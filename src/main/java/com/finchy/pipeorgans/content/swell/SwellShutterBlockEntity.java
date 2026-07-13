package com.finchy.pipeorgans.content.swell;

import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SwellShutterBlockEntity extends CopycatBlockEntity {

    public static final float MAX_ANGLE = 80f;
    // How much  remaining gap angle closes each tick
    private static final float LERP_SPEED = 0.35f;

    private float angle;
    private float previousAngle;

    public SwellShutterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        float target = targetAngle();
        angle = target;
        previousAngle = target;
    }

    // Target slat angle (degrees)
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

    // For smooth rendering
    public float getAngle(float partialTick) {
        return Mth.lerp(partialTick, previousAngle, angle);
    }
}
