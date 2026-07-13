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
    protected List<BakedQuad> getCroppedQuads(BlockState state, Direction side, RandomSource rand, BlockState material, ModelData modelData, RenderType renderType) {
        return List.of();
    }
}
