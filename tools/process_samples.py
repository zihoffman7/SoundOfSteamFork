#!/usr/bin/env python3
"""
Hauptwerk / GrandOrgue F# sample processor.

For every WAV file that:
  - is inside an A0 directory
  - has 'f#' (case-insensitive) in its filename

Do:
  1. Read the smpl chunk to get sustain loop start/end (sample frames).
  2. Crop the audio to [loop_start, loop_end] inclusive.
  3. Pitch-shift to the correct equal-temperament F# frequency for the MIDI
     note encoded in the filename (e.g. '054-f#.wav' → MIDI 54).
     The smpl unity note tells us the recorded pitch; the filename MIDI number
     tells us the target pitch.
  4. Convert to mono, resample to 48000 Hz.
  5. Normalize peak to -12 dBFS.
  6. Export as OGG Vorbis (16-bit mono), mirroring the directory hierarchy.

Requirements:
    pip install pydub numpy scipy
    (optional, better pitch quality): pip install librosa

ffmpeg must be on PATH (pydub uses it for OGG encoding).
"""

import os
import re
import math
import wave
import struct
import pathlib
import argparse
import numpy as np

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
_HERE = pathlib.Path(__file__).parent

DEFAULT_INPUT_DIR  = str(_HERE / "Szczecinek_GrandOrgue" / "Data - Szczecinek")
DEFAULT_OUTPUT_DIR = str(_HERE / "output")
TARGET_SAMPLE_RATE = 48000
TARGET_DB          = -12.0      # dBFS peak normalization
# ---------------------------------------------------------------------------


def midi_to_freq(midi_note: int) -> float:
    """Equal-temperament frequency for a MIDI note number (A4=69=440 Hz)."""
    return 440.0 * (2.0 ** ((midi_note - 69) / 12.0))


def read_smpl_loop(filepath: str):
    """
    Parse the WAV smpl chunk and return (unity_note, loop_start, loop_end).
    loop_start/end are in sample frames (inclusive).
    Returns (None, None, None) if the chunk is absent or has no loops.

    smpl chunk layout (little-endian uint32):
      offset  0: manufacturer
      offset  4: product
      offset  8: sample_period
      offset 12: midi_unity_note
      offset 16: midi_pitch_fraction
      offset 20: smpte_format
      offset 24: smpte_offset
      offset 28: num_sample_loops
      offset 32: sampler_data
      Then num_sample_loops × 24-byte loop records:
        +0: cue_point_id
        +4: type  (0 = forward sustain)
        +8: start (sample frame)
       +12: end   (sample frame, inclusive)
       +16: fraction
       +20: play_count
    """
    try:
        with open(filepath, "rb") as f:
            if f.read(4) != b"RIFF":
                return None, None, None
            f.read(4)  # file size
            if f.read(4) != b"WAVE":
                return None, None, None
            while True:
                hdr = f.read(8)
                if len(hdr) < 8:
                    break
                chunk_id   = hdr[:4].decode("ascii", errors="replace")
                chunk_size = struct.unpack_from("<I", hdr, 4)[0]
                if chunk_id == "smpl":
                    data = f.read(chunk_size)
                    if len(data) < 36:
                        return None, None, None
                    unity     = struct.unpack_from("<I", data, 12)[0]
                    num_loops = struct.unpack_from("<I", data, 28)[0]
                    if num_loops == 0:
                        return unity, None, None
                    loop_start = struct.unpack_from("<I", data, 36 + 8)[0]
                    loop_end   = struct.unpack_from("<I", data, 36 + 12)[0]
                    return unity, loop_start, loop_end
                else:
                    f.seek(chunk_size + (chunk_size % 2), 1)
    except OSError:
        pass
    return None, None, None


def read_wav_as_float32(filepath: str):
    """
    Read WAV → float32 mono numpy array + sample rate.
    Handles 16-bit, 24-bit PCM and 32-bit float WAVs.
    Multi-channel is mixed to mono.
    """
    with wave.open(filepath, "rb") as wf:
        n_ch   = wf.getnchannels()
        sw     = wf.getsampwidth()
        sr     = wf.getframerate()
        nf     = wf.getnframes()
        raw    = wf.readframes(nf)

    if sw == 2:
        data = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
    elif sw == 3:
        arr = np.frombuffer(raw, dtype=np.uint8).reshape(-1, 3)
        s32 = (arr[:, 0].astype(np.int32)
               | (arr[:, 1].astype(np.int32) << 8)
               | (arr[:, 2].astype(np.int32) << 16))
        s32[s32 >= (1 << 23)] -= (1 << 24)
        data = s32.astype(np.float32) / 8388608.0
    elif sw == 4:
        data = np.frombuffer(raw, dtype=np.float32).copy()
    else:
        raise ValueError(f"Unsupported sample width: {sw} bytes")

    if n_ch > 1:
        data = data.reshape(-1, n_ch).mean(axis=1)

    return data.astype(np.float32), sr


def resample(data: np.ndarray, orig_sr: int, target_sr: int) -> np.ndarray:
    """Resample using scipy (polyphase), fallback to numpy linear interp."""
    if orig_sr == target_sr:
        return data
    try:
        from scipy.signal import resample_poly
        from math import gcd
        g = gcd(orig_sr, target_sr)
        return resample_poly(data, target_sr // g, orig_sr // g).astype(np.float32)
    except ImportError:
        new_len = int(len(data) * target_sr / orig_sr)
        x_old = np.linspace(0, 1, len(data))
        x_new = np.linspace(0, 1, new_len)
        return np.interp(x_new, x_old, data).astype(np.float32)


def detect_pitch(data: np.ndarray, sample_rate: int,
                 expected_midi: int) -> float:
    """
    Detect fundamental frequency using librosa's pyin algorithm.
    pyin is accurate to sub-cent level on sustained tones.
    Falls back to autocorrelation if librosa unavailable.
    """
    target_freq = midi_to_freq(expected_midi)

    try:
        import librosa
        # Search ±150 cents around target
        fmin = target_freq * (2 ** (-1.5 / 12))
        fmax = target_freq * (2 ** (1.5 / 12))

        # Use a stable middle chunk of the loop (up to 1s)
        chunk_len = min(len(data), sample_rate)
        start = (len(data) - chunk_len) // 2
        chunk = data[start : start + chunk_len].astype(np.float32)

        f0, voiced_flag, voiced_probs = librosa.pyin(
            chunk,
            fmin=fmin,
            fmax=fmax,
            sr=sample_rate,
            fill_na=None,
        )
        # Take median of voiced frames only
        voiced = f0[voiced_flag]
        if len(voiced) > 0:
            return float(np.median(voiced))
    except Exception:
        pass

    # Fallback: parabolic interpolation on autocorrelation
    freq_lo = target_freq * (2 ** (-1.5 / 12))
    freq_hi = target_freq * (2 ** (1.5 / 12))
    lag_lo  = int(sample_rate / freq_hi)
    lag_hi  = int(sample_rate / freq_lo)

    chunk_len = min(len(data), sample_rate // 2)
    start = (len(data) - chunk_len) // 2
    chunk = data[start : start + chunk_len]

    corr = np.correlate(chunk, chunk, mode='full')
    corr = corr[len(corr) // 2:]
    corr /= (corr[0] + 1e-10)

    if lag_hi >= len(corr) or lag_lo < 1:
        return target_freq

    region = corr[lag_lo : lag_hi + 1]
    if len(region) < 3:
        return target_freq

    best = int(np.argmax(region))
    best_lag = lag_lo + best

    # Parabolic interpolation for sub-sample accuracy
    if 0 < best < len(region) - 1:
        alpha = region[best - 1]
        beta  = region[best]
        gamma = region[best + 1]
        denom = alpha - 2 * beta + gamma
        if abs(denom) > 1e-10:
            offset = 0.5 * (alpha - gamma) / denom
            best_lag = lag_lo + best + offset

    return sample_rate / best_lag


def pitch_shift(data: np.ndarray, ratio: float, sample_rate: int) -> np.ndarray:
    """
    Pitch-shift audio by the given ratio (>1 = up, <1 = down).
    Uses librosa if available (high quality, time-preserving).
    Falls back to resample-based shifting (tempo also changes slightly).
    """
    if abs(ratio - 1.0) < 1e-6:
        return data
    try:
        import librosa
        n_steps = 12.0 * math.log2(ratio)
        return librosa.effects.pitch_shift(
            data.astype(np.float32), sr=sample_rate, n_steps=n_steps
        ).astype(np.float32)
    except ImportError:
        pass
    # Naive fallback
    inter_sr = int(sample_rate / ratio)
    stretched = resample(data, sample_rate, inter_sr)
    return resample(stretched, inter_sr, sample_rate)
    """
    Pitch-shift audio by the given ratio (>1 = up, <1 = down).
    Uses librosa if available (high quality, time-preserving).
    Falls back to resample-based shifting (tempo also changes slightly).
    """
    if abs(ratio - 1.0) < 1e-6:
        return data
    try:
        import librosa
        n_steps = 12.0 * math.log2(ratio)
        return librosa.effects.pitch_shift(
            data.astype(np.float32), sr=sample_rate, n_steps=n_steps
        ).astype(np.float32)
    except ImportError:
        pass
    # Naive fallback
    inter_sr = int(sample_rate / ratio)
    stretched = resample(data, sample_rate, inter_sr)
    return resample(stretched, inter_sr, sample_rate)


def normalize_to_db(data: np.ndarray, target_db: float) -> np.ndarray:
    peak = np.max(np.abs(data))
    if peak < 1e-9:
        return data
    return (data * (10.0 ** (target_db / 20.0) / peak)).astype(np.float32)


def export_ogg(samples: np.ndarray, sample_rate: int, output_path: str) -> None:
    """
    Export float32 mono as OGG Vorbis using oggenc (from vorbis-tools).
    Writes a temporary WAV then encodes with oggenc for proper mono vorbis output.
    Requires: brew install vorbis-tools
    """
    import subprocess, tempfile

    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    int16 = np.clip(samples, -1.0, 1.0)
    int16 = (int16 * 32767.0).astype(np.int16)

    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        tmp_path = tmp.name
        import wave as wavemod
        with wavemod.open(tmp_path, "wb") as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)
            wf.setframerate(sample_rate)
            wf.writeframes(int16.tobytes())

    try:
        cmd = [
            "oggenc",
            "-q", "6",          # vorbis quality 6 (~192 kbps)
            "-o", output_path,
            tmp_path,
        ]
        result = subprocess.run(cmd, capture_output=True)
        if result.returncode != 0:
            raise RuntimeError(f"oggenc failed: {result.stderr.decode()}")
    finally:
        os.unlink(tmp_path)


def process_file(wav_path: str, out_path: str, midi_note: int,
                 verbose: bool = True) -> bool:
    if verbose:
        print(f"  {wav_path}")

    # 1. Read smpl loop points
    unity_note, loop_start, loop_end = read_smpl_loop(wav_path)

    if loop_start is None:
        print(f"    WARNING: no smpl loop — skipping")
        return False
    if verbose:
        print(f"    loop: {loop_start}–{loop_end}  unity={unity_note}")

    # 2. Read audio
    try:
        data, sr = read_wav_as_float32(wav_path)
    except Exception as e:
        print(f"    ERROR reading audio: {e}")
        return False

    # 3. Crop to loop bounds, snapping start to nearest zero-crossing
    loop_end = min(loop_end, len(data) - 1)
    if loop_start >= loop_end:
        print(f"    WARNING: invalid loop bounds — skipping")
        return False

    # Snap loop_start forward to the nearest zero-crossing (sign change)
    # to avoid a click at the beginning. Search up to 2048 samples ahead.
    search_limit = min(loop_start + 2048, loop_end)
    snap_start = loop_start
    for i in range(loop_start, search_limit - 1):
        if data[i] * data[i + 1] <= 0:
            snap_start = i
            break

    data = data[snap_start : loop_end + 1]

    # 4. Pitch-shift to correct equal-temperament F#
    #    Detect actual pitch from audio — more reliable than smpl unity note.
    target_freq  = midi_to_freq(midi_note)
    detected_freq = detect_pitch(data, sr, midi_note)
    ratio = target_freq / detected_freq
    if verbose:
        print(f"    pitch: detected {detected_freq:.4f} Hz "
              f"→ target {target_freq:.4f} Hz, ratio={ratio:.6f}")
    data = pitch_shift(data, ratio, sr)

    # 5. Resample to 48 kHz
    data = resample(data, sr, TARGET_SAMPLE_RATE)

    # 6. Normalize to -12 dBFS
    data = normalize_to_db(data, TARGET_DB)

    # 7. Export as OGG
    try:
        export_ogg(data, TARGET_SAMPLE_RATE, out_path)
    except Exception as e:
        print(f"    ERROR exporting: {e}")
        return False

    if verbose:
        print(f"    → {out_path}")
    return True


def main():
    parser = argparse.ArgumentParser(
        description="Process organ F# samples: crop to loop, tune, normalize, export OGG.")
    parser.add_argument("--input",  default=DEFAULT_INPUT_DIR,
                        help="Root sample directory (default: %(default)s)")
    parser.add_argument("--output", default=DEFAULT_OUTPUT_DIR,
                        help="Output directory (default: %(default)s)")
    parser.add_argument("--quiet",  action="store_true")
    args = parser.parse_args()

    input_root  = pathlib.Path(args.input).resolve()
    output_root = pathlib.Path(args.output).resolve()
    verbose     = not args.quiet

    print(f"Input:  {input_root}")
    print(f"Output: {output_root}")
    print()

    # Match files like "054-f#.wav" inside any A0 directory
    fsharp_re = re.compile(r"^(\d+)-f#\.wav$", re.IGNORECASE)

    # MIDI note → tier name (sorted ascending = deep to superhigh)
    TIER_ORDER = ["deep", "low", "medium", "high", "superhigh"]

    ok_count = err_count = skip_count = 0

    # First pass: collect all midi notes per stop to assign tiers by rank
    stop_notes: dict = {}  # grandparent_name -> sorted list of midi notes
    for wav_path in sorted(input_root.rglob("*.wav")):
        if re.match(r'^R\d+$', wav_path.parent.name, re.IGNORECASE):
            continue
        m = fsharp_re.match(wav_path.name)
        if not m:
            continue
        grandparent = wav_path.parent.parent.name
        midi_note = int(m.group(1))
        stop_notes.setdefault(grandparent, set()).add(midi_note)
    # Sort each stop's notes
    stop_tiers: dict = {}  # grandparent -> {midi_note: tier_name}
    for stop, notes in stop_notes.items():
        sorted_notes = sorted(notes)
        n = len(sorted_notes)
        # Assign tiers from the top of TIER_ORDER so the highest note = superhigh
        # Use the last n tiers
        tiers = TIER_ORDER[-n:]
        stop_tiers[stop] = {note: tier for note, tier in zip(sorted_notes, tiers)}

    for wav_path in sorted(input_root.rglob("*.wav")):
        # Must be one level below the stop directory (i.e. the attack/sustain folder).
        # Skip release layers (R0, R1, R2, R3...) — accept anything else (A0, ae, etc.)
        parent_name = wav_path.parent.name
        if re.match(r'^R\d+$', parent_name, re.IGNORECASE):
            continue
        m = fsharp_re.match(wav_path.name)
        if not m:
            continue

        midi_note   = int(m.group(1))
        prefix      = wav_path.parent.name                # e.g. "ae" from aeoline/ae/
        grandparent = wav_path.parent.parent.name         # e.g. "aeoline"
        tier        = stop_tiers[grandparent][midi_note]  # e.g. "medium"
        out_name    = f"{prefix}_{tier}.ogg"              # e.g. "ae_medium.ogg"

        rel_dir  = wav_path.parent.parent.relative_to(input_root)
        out_path = output_root / rel_dir / out_name

        if out_path.exists():
            if verbose:
                print(f"  SKIP (exists): {out_path.name}")
            skip_count += 1
            continue

        ok = process_file(str(wav_path), str(out_path), midi_note, verbose)
        if ok:
            ok_count += 1
        else:
            err_count += 1

    print()
    print(f"Done.  {ok_count} exported,  {err_count} errors,  "
          f"{skip_count} skipped (already existed).")


if __name__ == "__main__":
    main()
