package com.finchy.pipeorgans.content.swell;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.copycat.CopycatModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;

import java.util.List;

public class SwellBoxModel extends CopycatModel {

    public SwellBoxModel(BakedModel template) {
        super(template);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    private static boolean hasMaterial(ModelData modelData) {
        return !AllBlocks.COPYCAT_BASE.has(getMaterial(modelData));
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData modelData) {
        if (!hasMaterial(modelData)) {
            return originalModel.getRenderTypes(state, rand, ModelData.EMPTY);
        }
        BlockState material = getMaterial(modelData);
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(material)
                .getRenderTypes(material, rand, ModelData.EMPTY);
    }

    @Override
    protected List<BakedQuad> getCroppedQuads(BlockState state, Direction side,
                                               RandomSource rand, BlockState material,
                                               ModelData modelData, RenderType renderType) {
        if (AllBlocks.COPYCAT_BASE.has(material)) {
            return originalModel.getQuads(state, side, rand, ModelData.EMPTY, renderType);
        }
        return getModelOf(material).getQuads(material, side, rand, ModelData.EMPTY, renderType);
    }
}
