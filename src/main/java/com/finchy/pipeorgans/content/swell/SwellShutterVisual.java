package com.finchy.pipeorgans.content.swell;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class SwellShutterVisual extends AbstractBlockEntityVisual<SwellShutterBlockEntity>
        implements SimpleDynamicVisual {

    private static final int   SLAT_COUNT = SwellShutterSlatModels.SLAT_COUNT;
    private static final float SLAT_STEP  = 4f / 16f;
    private static final float SLAT_HALF  = 2f / 16f;

    private final TransformedInstance[] slats = new TransformedInstance[SLAT_COUNT];

    @Nullable
    private BlockState slatMaterial;

    private final float facingYaw;

    public SwellShutterVisual(VisualizationContext context, SwellShutterBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        this.facingYaw = blockState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? blockState.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() * Mth.DEG_TO_RAD
                : 0f;
        buildSlats();
        animate(partialTick);
    }

    private BlockState currentMaterial() {
        BlockState mat = blockEntity.getMaterial();
        return mat != null ? mat : blockEntity.getBlockState();
    }

    private void buildSlats() {
        slatMaterial = currentMaterial();
        Model[] models = SwellShutterSlatModels.build(slatMaterial);
        for (int i = 0; i < SLAT_COUNT; i++) {
            if (slats[i] != null) slats[i].delete();
            slats[i] = instancerProvider()
                    .instancer(InstanceTypes.TRANSFORMED, models[i])
                    .createInstance();
        }
    }

    private void animate(float partialTick) {
        float angle = blockEntity.getAngle(partialTick) * Mth.DEG_TO_RAD;
        int light = computePackedLight();

        Vec3i origin = renderOrigin();
        float bx = blockEntity.getBlockPos().getX() - origin.getX();
        float by = blockEntity.getBlockPos().getY() - origin.getY();
        float bz = blockEntity.getBlockPos().getZ() - origin.getZ();

        for (int i = 0; i < SLAT_COUNT; i++) {
            float centerX = SLAT_STEP * i + SLAT_HALF; // 0.125, 0.375, 0.625, 0.875

            slats[i].setIdentityTransform()
                    .translate(bx, by, bz)
                    // orient the whole block to its FACING (baseline = SOUTH)
                    .translate(0.5f, 0.5f, 0.5f)
                    .rotateY(facingYaw)
                    .translate(-0.5f, -0.5f, -0.5f)
                    // pivot 
                    .translate(centerX, 0f, 0.5f)
                    .rotateY(angle)
                    .translate(-centerX, 0f, -0.5f)
                    .setChanged();

            slats[i].light = light;
        }
    }

    @Override
    public void beginFrame(Context ctx) {
        if (!currentMaterial().equals(slatMaterial)) buildSlats();
        animate(ctx.partialTick());
    }

    @Override
    public void update(float partialTick) {
        if (!currentMaterial().equals(slatMaterial)) buildSlats();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(slats);
    }

    @Override
    protected void _delete() {
        for (TransformedInstance slat : slats) {
            if (slat != null) slat.delete();
        }
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        for (TransformedInstance slat : slats) consumer.accept(slat);
    }
}
