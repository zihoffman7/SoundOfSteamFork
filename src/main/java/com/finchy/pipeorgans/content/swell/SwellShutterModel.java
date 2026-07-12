package com.finchy.pipeorgans.content.swell;

import com.simibubi.create.content.decoration.copycat.CopycatModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;

import java.util.List;

/**
 * The swell shutter's static blockstate model renders nothing — the louver slats
 * are drawn entirely by {@link SwellShutterVisual} (Flywheel) or the fallback
 * {@link SwellShutterRenderer}. This wrapper exists only so the copycat material
 * is carried in the model data (for particles, occlusion, etc.).
 */
public class SwellShutterModel extends CopycatModel {

    public SwellShutterModel(BakedModel template) {
        super(template);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData modelData) {
        return ChunkRenderTypeSet.none();
    }

    @Override
    protected List<BakedQuad> getCroppedQuads(BlockState state, Direction side,
                                               RandomSource rand, BlockState material,
                                               ModelData modelData, RenderType renderType) {
        return List.of();
    }
}
