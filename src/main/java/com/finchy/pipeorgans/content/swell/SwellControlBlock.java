package com.finchy.pipeorgans.content.swell;

import com.finchy.pipeorgans.init.AllBlockEntities;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import java.util.Objects;

public class SwellControlBlock extends Block implements IBE<SwellControlBlockEntity> {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public SwellControlBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return Objects.requireNonNull(super.getStateForPlacement(ctx))
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state
                : state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // -----------------------------------------------------------------------
    // Redstone input — read signal on placement and neighbor changes
    // -----------------------------------------------------------------------

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide)
            withBlockEntityDo(level, pos, be -> {
                be.onSignalChanged(level.getBestNeighborSignal(pos));
                be.forceRescan();
            });
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide)
            withBlockEntityDo(level, pos, be -> {
                // Redstone / signal update is cheap (no flood fill).
                be.onSignalChanged(level.getBestNeighborSignal(pos));
                // Only re-scan the geometry if a neighbouring block was BROKEN
                // (now air). Placing a block never triggers a rescan.
                if (level.getBlockState(neighborPos).isAir())
                    be.forceRescan();
            });
    }

    private void updateSignal(Level level, BlockPos pos) {
        withBlockEntityDo(level, pos, be -> be.onSignalChanged(level.getBestNeighborSignal(pos)));
    }

    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        if (hand == net.minecraft.world.InteractionHand.OFF_HAND) return net.minecraft.world.InteractionResult.PASS;
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        withBlockEntityDo(level, pos, be ->
                net.minecraftforge.network.NetworkHooks.openScreen(
                        (net.minecraft.server.level.ServerPlayer) player, be, be::sendToMenu));
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    // -----------------------------------------------------------------------
    // IBE
    // -----------------------------------------------------------------------

    @Override
    public Class<SwellControlBlockEntity> getBlockEntityClass() {
        return SwellControlBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SwellControlBlockEntity> getBlockEntityType() {
        return AllBlockEntities.SWELL_CONTROL_BE.get();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            withBlockEntityDo(level, pos, SwellControlBlockEntity::onRemoved);
            IBE.onRemove(state, level, pos, newState);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
