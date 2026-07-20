package com.finchy.pipeorgans.content.swell;

import com.finchy.pipeorgans.content.pipes.generic.GenericPipeBlock;
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

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.*;

public class SwellControlBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, MenuProvider {

    public static final int DEFAULT_CAP = 5000;

    // Persistent
    private int signalLevel = 0;
    private int scanCap = DEFAULT_CAP;
    private boolean goggles = false;

    // Client-synced display state
    private int shutterCount = 0;
    private int interiorVolume = 0;
    private boolean hasHoles = false;
    private float maxVolume = 1.0f;
    private float volumeFactor = 1.0f;

    // Server-only runtime
    private final Set<BlockPos> trackedPipes = new HashSet<>();
    private final List<BlockPos> cachedShutters = new ArrayList<>();
    private boolean scanPending = false;
    private boolean applyPending = false;

    public SwellControlBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public void onSignalChanged(int newSignal) {
        if (signalLevel == newSignal) return;
        signalLevel = newSignal;
        applyPending = true;
    }

    public void forceRescan() {
        scanPending = true;
    }

    public int getScanCap() { return scanCap; }

    public void setScanCap(int cap) {
        scanCap = Math.max(20, cap);
        setChanged();
        scanPending = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (scanPending) {
            scanPending = false;
            applyPending = false;
            runScan(); // full flood fill (structural change)
        } else if (applyPending) {
            applyPending = false;
            recomputeAndApply(); // reuse cached scan results
        }
    }

    // Flood fill
    private void runScan() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Scan allocates almost nothing and never queries block entities in the loop
        LongOpenHashSet visited = new LongOpenHashSet();
        LongOpenHashSet interior = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        List<BlockPos> shutters = new ArrayList<>();
        List<BlockPos> pipes = new ArrayList<>();
        int interiorCount = 0;

        BlockPos.MutableBlockPos cur  = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();

        // Start from the block directly behind the control
        Direction facing = getBlockState().getValue(SwellControlBlock.FACING);
        next.setWithOffset(worldPosition, facing.getOpposite());
        if (serverLevel.isLoaded(next)) {
            Block nb = serverLevel.getBlockState(next).getBlock();
            if (!(nb instanceof SwellBoxBlock) && !(nb instanceof SwellShutterBlock)
                    && !(nb instanceof SwellControlBlock)) {
                long key = next.asLong();
                if (visited.add(key)) queue.enqueue(key);
            }
        }

        boolean hitCap = false;

        while (!queue.isEmpty()) {
            if (visited.size() > scanCap) { hitCap = true; break; }
            long key = queue.dequeueLong();
            cur.set(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
            if (!serverLevel.isLoaded(cur)) { hitCap = true; break; }

            BlockState state = serverLevel.getBlockState(cur);
            Block block = state.getBlock();

            if (block instanceof SwellShutterBlock) {
                // A misoriented shutter is not a valid wall
                Direction sf = state.getValue(SwellShutterBlock.FACING);
                next.setWithOffset(cur, sf);
                boolean seals = interior.contains(next.asLong());
                next.setWithOffset(cur, sf.getOpposite());
                seals |= interior.contains(next.asLong());
                if (seals) {
                    shutters.add(cur.immutable());
                    continue; // valid wall seal
                }
                // otherwise fall through and traverse it as a leak
            } else if (block instanceof SwellBoxBlock || block instanceof SwellControlBlock) {
                continue; // wall boundary
            }

            interior.add(key);
            if (block instanceof GenericPipeBlock) pipes.add(cur.immutable());
            interiorCount++;

            for (Direction dir : Direction.values()) {
                next.setWithOffset(cur, dir);
                long nkey = next.asLong();
                if (visited.add(nkey)) queue.enqueue(nkey);
            }
        }

        boolean foundHole = hitCap;

        // Include only wall shutters
        List<BlockPos> wallShutters = new ArrayList<>();
        for (BlockPos sp : shutters) {
            Direction exposedDir = null;
            for (Direction dir : Direction.values()) {
                next.setWithOffset(sp, dir);
                if (interior.contains(next.asLong())) continue; // faces the inside
                Block nb = serverLevel.getBlockState(next).getBlock();
                if (!(nb instanceof SwellBoxBlock) && !(nb instanceof SwellShutterBlock)
                        && !(nb instanceof SwellControlBlock)) {
                    exposedDir = dir; // faces the exterior
                    break;
                }
            }
            if (exposedDir == null) continue; // floating / interior shutter — ignore

            // Only count shutters whose louvers are oriented along the wall's outward axis
            Direction shutterFacing = serverLevel.getBlockState(sp).getValue(SwellShutterBlock.FACING);
            if (shutterFacing.getAxis() == exposedDir.getAxis()) {
                wallShutters.add(sp);
            }
        }

        this.shutterCount   = wallShutters.size();
        this.interiorVolume = interiorCount;
        this.hasHoles       = foundHole;

        // Cache the wall shutter positions so a signal change can re-apply
        cachedShutters.clear();
        cachedShutters.addAll(wallShutters);

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

    // Recompute volume factor from the cached scan results
    // Does NOT flood fill, runs on every redstone signal change.
    private void recomputeAndApply() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        float newMaxVol;
        float newFactor;
        if (hasHoles) {
            // Not enclosed
            newMaxVol = 1.0f;
            newFactor = 1.0f;
        } else {
            // Enclosed: 1 shutter covers 64 interior blocks
            newMaxVol = interiorVolume > 0
                    ? Math.min(1.0f, (shutterCount * 64f) / interiorVolume)
                    : 1.0f;
            newFactor = (signalLevel / 15f) * newMaxVol;
        }
        this.maxVolume = newMaxVol;
        this.volumeFactor = newFactor;

        // Apply shutter openness.
        for (BlockPos sp : cachedShutters) {
            BlockState s = serverLevel.getBlockState(sp);
            if (s.getBlock() instanceof SwellShutterBlock
                    && s.getValue(SwellShutterBlock.OPENNESS) != signalLevel) {
                serverLevel.setBlock(sp, s.setValue(SwellShutterBlock.OPENNESS, signalLevel),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }

        // Push the factor to every tracked pipe
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

    // Goggles
    public boolean hasGoggles() { return goggles; }
    public void setGoggles(boolean g) { goggles = g; }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        CreateLang.builder().text("Signal: " + signalLevel).forGoggles(tooltip);
        CreateLang.builder().text("Enclosed: " + (!hasHoles ? "yes" : "no")).forGoggles(tooltip);
        if (!hasHoles) {
            CreateLang.builder().text("Shutters: " + shutterCount).forGoggles(tooltip);
            CreateLang.builder().text("Interior: " + interiorVolume + " blocks").forGoggles(tooltip);
            CreateLang.builder().text("Max Vol: " + (int)(maxVolume * 100f) + "%").forGoggles(tooltip);
        } else {
            CreateLang.builder().text("Max Vol: 100%").forGoggles(tooltip);
        }
        CreateLang.builder().text("Volume: "  + (int)(volumeFactor * 100f) + "%").forGoggles(tooltip);
        return true;
    }

    // GUI
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.pipeorgans.swell_control");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return SwellControlMenu.create(id, inv, this);
    }

    // Persistence

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putInt("Signal", signalLevel);
        tag.putInt("ScanCap", scanCap);
        tag.putBoolean("Goggles", goggles);
        if (clientPacket) {
            tag.putInt("Shutters", shutterCount);
            tag.putInt("Interior", interiorVolume);
            tag.putBoolean("HasHoles", hasHoles);
            tag.putFloat("MaxVol", maxVolume);
            tag.putFloat("VolFactor", volumeFactor);
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        signalLevel = tag.getInt("Signal");
        scanCap = tag.contains("ScanCap") ? Math.max(100, tag.getInt("ScanCap")) : DEFAULT_CAP;
        goggles = tag.getBoolean("Goggles");
        if (clientPacket) {
            shutterCount = tag.getInt("Shutters");
            interiorVolume = tag.getInt("Interior");
            hasHoles = tag.getBoolean("HasHoles");
            maxVolume = tag.getFloat("MaxVol");
            volumeFactor = tag.getFloat("VolFactor");
        } else {
            scanPending = true;
        }
    }

    public int getShutterCount() { return shutterCount; }
    public boolean getHasHoles() { return hasHoles; }
    public float getMaxVolume() { return maxVolume; }
    public float getVolumeFactor() { return volumeFactor; }
}
