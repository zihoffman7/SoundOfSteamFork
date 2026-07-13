package com.finchy.pipeorgans.content.swell;

import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public class SwellBoxBlock extends CopycatBlock {

    public SwellBoxBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canConnectTexturesToward(BlockAndTintGetter level, BlockPos pos, BlockPos other, BlockState state) {
        return true;
    }

    @Override
    public Class<CopycatBlockEntity> getBlockEntityClass() {
        return CopycatBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CopycatBlockEntity> getBlockEntityType() {
        return com.finchy.pipeorgans.init.AllBlockEntities.SWELL_BOX_BE.get();
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        // Rescan when neighbouring block broken
        if (!level.isClientSide && level.getBlockState(neighborPos).isAir())
            triggerNearbyControls(level, pos);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) triggerNearbyControls(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide)
            triggerNearbyControls(level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // Safety bound on wall search
    private static final int MAX_WALL_SEARCH = SwellControlBlockEntity.DEFAULT_CAP;

    // BFS through connected swell box/shutter blocks to find all
    public static void triggerNearbyControls(Level level, BlockPos origin) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        visited.add(origin);
        queue.add(origin);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos n = pos.relative(dir);
                if (!level.isLoaded(n) || !visited.add(n)) continue;
                if (level.getBlockEntity(n) instanceof SwellControlBlockEntity ctrl) {
                    ctrl.forceRescan();
                    // Don't continue BFS through the control itself
                } else if (level.getBlockState(n).getBlock() instanceof SwellBoxBlock
                        || level.getBlockState(n).getBlock() instanceof SwellShutterBlock) {
                    if (visited.size() < MAX_WALL_SEARCH) queue.add(n);
                }
            }
        }
    }
}
