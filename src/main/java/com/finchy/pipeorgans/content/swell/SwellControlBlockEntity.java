package com.finchy.pipeorgans.content.swell;

import com.finchy.pipeorgans.content.pipes.generic.GenericPipeBlockEntity;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class SwellControlBlockEntity extends SmartBlockEntity
        implements IHaveGoggleInformation, MenuProvider {

    public static final int DEFAULT_CAP = 4000;

    // Persistent
    private int     signalLevel = 0;
    private int     scanCap     = DEFAULT_CAP;
    private boolean goggles     = false;

    // Client-synced display state
    private int     shutterCount   = 0;
    private int     interiorVolume = 0;
    private boolean hasHoles       = false;
    private float   maxVolume      = 1.0f;
    private float   volumeFactor   = 1.0f;

    // Server-only runtime
    private final Set<BlockPos> trackedPipes = new HashSet<>();
    // Shutter positions found by the last flood fill, so a signal change can re-apply
    // openness without re-running the expensive scan.
    private final List<BlockPos> cachedShutters = new ArrayList<>();
    private boolean scanPending  = false; // structural change → needs a full flood fill
    private boolean applyPending = false; // signal change → only recompute + re-apply

    public SwellControlBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    // -----------------------------------------------------------------------
    // Triggers
    // -----------------------------------------------------------------------

    /**
     * Redstone input changed. This does NOT change the box geometry, so it only
     * schedules a cheap recompute of the volume factor + shutter openness — never a
     * full flood-fill scan. This is what keeps redstone-driven changes cheap.
     */
    public void onSignalChanged(int newSignal) {
        if (signalLevel == newSignal) return;
        signalLevel = newSignal;
        applyPending = true;
    }

    /** Schedule a full structural flood-fill (block broken, or swell block placed/removed). */
    public void forceRescan() {
        scanPending = true;
    }

    public int  getScanCap()        { return scanCap; }
    public void setScanCap(int cap) {
        scanCap = Math.max(20, cap);
        setChanged();
        scanPending = true;
    }

    // -----------------------------------------------------------------------
    // Tick
    // -----------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (scanPending) {
            scanPending = false;
            applyPending = false;
            runScan();               // full flood fill (structural change)
        } else if (applyPending) {
            applyPending = false;
            recomputeAndApply();     // cheap: reuse cached scan results
        }
    }

    // -----------------------------------------------------------------------
    // Flood fill — only SwellBoxBlock and SwellShutterBlock are walls
    // Everything else is traversed. Enclosed = fill terminates within cap.
    // -----------------------------------------------------------------------

    private void runScan() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        Set<BlockPos>   visited   = new HashSet<>();
        Queue<BlockPos> queue     = new ArrayDeque<>();
        List<BlockPos>  shutters  = new ArrayList<>();
        List<BlockPos>  pipes     = new ArrayList<>();
        int interiorCount = 0;

        // Seed from 6 faces, skip SwellBoxBlock/shutter (they are the wall)
        for (Direction dir : Direction.values()) {
            BlockPos n = worldPosition.relative(dir);
            if (!serverLevel.isLoaded(n)) continue;
            Block nb = serverLevel.getBlockState(n).getBlock();
            if (!(nb instanceof SwellBoxBlock) && !(nb instanceof SwellShutterBlock)
                    && visited.add(n)) queue.add(n);
        }

        boolean hitCap = false;

        while (!queue.isEmpty()) {
            if (visited.size() > scanCap) { hitCap = true; break; }
            BlockPos pos = queue.poll();
            if (!serverLevel.isLoaded(pos)) { hitCap = true; break; }

            BlockState state = serverLevel.getBlockState(pos);
            Block      block = state.getBlock();

            if (block instanceof SwellShutterBlock) {
                shutters.add(pos.immutable());
                continue; // wall boundary
            }
            if (block instanceof SwellBoxBlock) {
                continue; // wall boundary
            }

            // Everything else: traverse through
            BlockEntity be = serverLevel.getBlockEntity(pos);
            if (be instanceof GenericPipeBlockEntity) pipes.add(pos.immutable());
            interiorCount++;

            for (Direction dir : Direction.values()) {
                BlockPos n = pos.relative(dir);
                if (visited.add(n)) queue.add(n);
            }
        }

        boolean foundHole = hitCap;

        this.shutterCount   = shutters.size();
        this.interiorVolume = interiorCount;
        this.hasHoles       = foundHole;

        // Cache shutter positions so a signal change can re-apply openness cheaply.
        cachedShutters.clear();
        cachedShutters.addAll(shutters);

        // Diff the tracked pipe set: drop the factor from pipes that are no longer enclosed.
        Set<BlockPos> newPipeSet = new HashSet<>(pipes);
        for (BlockPos old : trackedPipes) {
            if (!newPipeSet.contains(old)) {
                BlockEntity be = serverLevel.getBlockEntity(old);
                if (be instanceof GenericPipeBlockEntity p) p.removeSwellFactor(worldPosition);
            }
        }
        trackedPipes.clear();
        trackedPipes.addAll(newPipeSet);

        // Compute the volume factor and push it to shutters + pipes.
        recomputeAndApply();
    }

    /**
     * Recompute the volume factor from the cached scan results (shutter count,
     * interior volume, enclosure) plus the current signal level, and apply it to the
     * cached shutters and tracked pipes. Cheap — does NOT flood fill — so it is safe
     * to run on every redstone signal change.
     */
    private void recomputeAndApply() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        float newMaxVol;
        float newFactor;
        if (hasHoles) {
            // Not enclosed — swell control has no effect, pipes play at full volume
            newMaxVol = 1.0f;
            newFactor = 1.0f;
        } else {
            // Enclosed — 1 shutter covers 64 interior blocks
            newMaxVol = interiorVolume > 0
                    ? Math.min(1.0f, (shutterCount * 64f) / interiorVolume)
                    : 1.0f;
            newFactor = (signalLevel / 15f) * newMaxVol;
        }
        this.maxVolume    = newMaxVol;
        this.volumeFactor = newFactor;

        // Apply shutter openness (only touches the few cached shutter blocks).
        for (BlockPos sp : cachedShutters) {
            BlockState s = serverLevel.getBlockState(sp);
            if (s.getBlock() instanceof SwellShutterBlock
                    && s.getValue(SwellShutterBlock.OPENNESS) != signalLevel) {
                serverLevel.setBlock(sp, s.setValue(SwellShutterBlock.OPENNESS, signalLevel),
                        Block.UPDATE_CLIENTS);
            }
        }

        // Push the factor to every tracked pipe.
        for (BlockPos pp : trackedPipes) {
            BlockEntity be = serverLevel.getBlockEntity(pp);
            if (be instanceof GenericPipeBlockEntity p) p.updateSwellFactor(worldPosition, newFactor);
        }

        notifyUpdate();
    }

    public void onRemoved() {
        if (level == null) return;
        for (BlockPos pp : trackedPipes) {
            BlockEntity be = level.getBlockEntity(pp);
            if (be instanceof GenericPipeBlockEntity p) p.removeSwellFactor(worldPosition);
        }
        trackedPipes.clear();
    }

    // -----------------------------------------------------------------------
    // Goggles
    // -----------------------------------------------------------------------

    public boolean hasGoggles()          { return goggles; }
    public void    setGoggles(boolean g) { goggles = g; }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        CreateLang.builder().text("Signal: "   + signalLevel)                        .forGoggles(tooltip);
        CreateLang.builder().text("Enclosed: " + (!hasHoles ? "yes" : "no"))         .forGoggles(tooltip);
        if (!hasHoles) {
            CreateLang.builder().text("Shutters: "  + shutterCount)                  .forGoggles(tooltip);
            CreateLang.builder().text("Interior: "  + interiorVolume + " blocks")    .forGoggles(tooltip);
            CreateLang.builder().text("Max Vol: "   + (int)(maxVolume * 100f) + "%") .forGoggles(tooltip);
        } else {
            CreateLang.builder().text("Max Vol: 100%")                               .forGoggles(tooltip);
        }
        CreateLang.builder().text("Volume: "   + (int)(volumeFactor * 100f) + "%")  .forGoggles(tooltip);
        return true;
    }

    // -----------------------------------------------------------------------
    // MenuProvider (scan cap GUI)
    // -----------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.pipeorgans.swell_control");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return SwellControlMenu.create(id, inv, this);
    }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putInt("Signal",  signalLevel);
        tag.putInt("ScanCap", scanCap);
        tag.putBoolean("Goggles", goggles);
        if (clientPacket) {
            tag.putInt("Shutters",     shutterCount);
            tag.putInt("Interior",     interiorVolume);
            tag.putBoolean("HasHoles", hasHoles);
            tag.putFloat("MaxVol",     maxVolume);
            tag.putFloat("VolFactor",  volumeFactor);
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        signalLevel = tag.getInt("Signal");
        scanCap     = tag.contains("ScanCap") ? Math.max(64, tag.getInt("ScanCap")) : DEFAULT_CAP;
        goggles     = tag.getBoolean("Goggles");
        if (clientPacket) {
            shutterCount   = tag.getInt("Shutters");
            interiorVolume = tag.getInt("Interior");
            hasHoles       = tag.getBoolean("HasHoles");
            maxVolume      = tag.getFloat("MaxVol");
            volumeFactor   = tag.getFloat("VolFactor");
        } else {
            scanPending = true;
        }
    }

    public int     getShutterCount() { return shutterCount; }
    public boolean getHasHoles()     { return hasHoles;     }
    public float   getMaxVolume()    { return maxVolume;    }
    public float   getVolumeFactor() { return volumeFactor; }
}
