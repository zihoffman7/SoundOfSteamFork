package com.finchy.pipeorgans.content.swell;

import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Objects;

/**
 * Swell Shutter — a copycat louver panel that forms part of a swell box wall.
 *
 * OPENNESS 0 = fully closed, 15 = fully open. The value is written by the
 * SwellControlBlockEntity when it receives a redstone-link signal, and drives the
 * pivot angle of the Flywheel-rendered slats (see SwellShutterVisual / Renderer).
 *
 * Being a copycat, the slats are textured with whatever block is placed inside it.
 */
public class SwellShutterBlock extends CopycatBlock {

    public static final DirectionProperty FACING   = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty   OPENNESS = IntegerProperty.create("openness", 0, 15);

    // Slats are 4px wide and pivot on their vertical axis, so their maximum sweep
    // along the facing axis is ~4px, centred on the block. The hitbox reflects that
    // rather than occupying the full block.
    private static final VoxelShape SHAPE_Z = Block.box(0, 0, 6, 16, 16, 10); // facing N/S
    private static final VoxelShape SHAPE_X = Block.box(6, 0, 0, 10, 16, 16); // facing E/W

    public SwellShutterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING,   Direction.NORTH)
                .setValue(OPENNESS, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, OPENNESS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return Objects.requireNonNull(super.getStateForPlacement(ctx))
                .setValue(FACING,   ctx.getHorizontalDirection().getOpposite())
                .setValue(OPENNESS, 0);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }

    // -----------------------------------------------------------------------
    // Copycat wiring
    // -----------------------------------------------------------------------

    @Override
    public boolean canConnectTexturesToward(BlockAndTintGetter level, BlockPos pos,
                                            BlockPos other, BlockState state) {
        return true;
    }

    @Override
    public Class<com.simibubi.create.content.decoration.copycat.CopycatBlockEntity> getBlockEntityClass() {
        return com.simibubi.create.content.decoration.copycat.CopycatBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends com.simibubi.create.content.decoration.copycat.CopycatBlockEntity> getBlockEntityType() {
        return com.finchy.pipeorgans.init.AllBlockEntities.SWELL_SHUTTER_BE.get();
    }

    /**
     * CopycatBlock returns a null ticker; we need the BE to tick (on both sides)
     * so the slat angle animates toward the OPENNESS target every tick.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <S extends net.minecraft.world.level.block.entity.BlockEntity>
    net.minecraft.world.level.block.entity.BlockEntityTicker<S> getTicker(
            Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<S> type) {
        if (type == getBlockEntityType()) {
            return (lvl, pos, st, be) -> ((SwellShutterBlockEntity) be).tick();
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Swell-control triggers (same as the swell box)
    // -----------------------------------------------------------------------

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                        BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) SwellBoxBlock.triggerNearbyControls(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide)
            SwellBoxBlock.triggerNearbyControls(level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        // Only rescan when a neighbouring block was BROKEN (now air). Placing a block
        // on the shutter does nothing.
        if (!level.isClientSide && level.getBlockState(neighborPos).isAir())
            SwellBoxBlock.triggerNearbyControls(level, pos);
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
}
