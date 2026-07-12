package com.finchy.pipeorgans.content.swell;

import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Copycat block entity for the swell shutter.
 *
 * The louver slats pivot between fully closed (OPENNESS 0) and fully open
 * (OPENNESS 15). The target angle is derived from the OPENNESS blockstate value
 * (written by the SwellControlBlockEntity); the client-side {@link #angle} lerps
 * toward that target each tick so the movement is smooth rather than snapping.
 */
public class SwellShutterBlockEntity extends CopycatBlockEntity {

    /** Fully-open slat angle, in degrees. 0 = closed (facing the viewer). */
    public static final float MAX_ANGLE = 80f;
    /** How much of the remaining gap the angle closes each tick (0..1). */
    private static final float LERP_SPEED = 0.35f;

    private float angle;
    private float previousAngle;

    public SwellShutterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        float target = targetAngle();
        angle = target;
        previousAngle = target;
    }

    /** Target slat angle in degrees, derived from the OPENNESS blockstate value. */
    private float targetAngle() {
        int openness = getBlockState().hasProperty(SwellShutterBlock.OPENNESS)
                ? getBlockState().getValue(SwellShutterBlock.OPENNESS)
                : 0;
        return (openness / 15f) * MAX_ANGLE;
    }

    @Override
    public void tick() {
        super.tick();
        previousAngle = angle;
        angle = Mth.lerp(LERP_SPEED, angle, targetAngle());
    }

    /** Interpolated slat angle in degrees for smooth rendering. */
    public float getAngle(float partialTick) {
        return Mth.lerp(partialTick, previousAngle, angle);
    }
}
