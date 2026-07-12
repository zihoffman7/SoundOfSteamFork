package com.finchy.pipeorgans.content.swell;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

/**
 * Fallback renderer for the swell shutter, used only when Flywheel visualization is
 * unavailable. Draws the same four pivoting, non-stretched material slats as
 * {@link SwellShutterVisual}, using the cropped quads from
 * {@link SwellShutterSlatModels}.
 */
public class SwellShutterRenderer extends SafeBlockEntityRenderer<SwellShutterBlockEntity> {

    private static final int   SLAT_COUNT = SwellShutterSlatModels.SLAT_COUNT;
    private static final float SLAT_STEP  = 4f / 16f;
    private static final float SLAT_HALF  = 2f / 16f;

    public SwellShutterRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(SwellShutterBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        Level level = be.getLevel();
        if (VisualizationManager.supportsVisualization(level)) return; // Flywheel handles it

        BlockState material = be.getMaterial();
        if (material == null) material = be.getBlockState();

        BlockState state = be.getBlockState();
        float facingYaw = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() * Mth.DEG_TO_RAD
                : 0f;
        float angle = be.getAngle(partialTicks) * Mth.DEG_TO_RAD;

        List<BakedQuad>[] perSlat = SwellShutterSlatModels.buildQuads(material);
        VertexConsumer vc = buffer.getBuffer(RenderType.cutout());

        for (int i = 0; i < SLAT_COUNT; i++) {
            float centerX = SLAT_STEP * i + SLAT_HALF;

            ms.pushPose();
            ms.translate(0.5f, 0.5f, 0.5f);
            ms.mulPose(Axis.YP.rotation(facingYaw));
            ms.translate(-0.5f, -0.5f, -0.5f);

            ms.translate(centerX, 0f, 0.5f);
            ms.mulPose(Axis.YP.rotation(angle));
            ms.translate(-centerX, 0f, -0.5f);

            PoseStack.Pose pose = ms.last();
            for (BakedQuad quad : perSlat[i]) {
                vc.putBulkData(pose, quad, 1f, 1f, 1f, light, overlay);
            }
            ms.popPose();
        }
    }
}
