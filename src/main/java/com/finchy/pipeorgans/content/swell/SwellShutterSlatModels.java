package com.finchy.pipeorgans.content.swell;

import com.simibubi.create.foundation.model.BakedQuadHelper;
import com.finchy.pipeorgans.init.AllPartialModels;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.model.baked.BakedModelBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

public final class SwellShutterSlatModels {

    public static final int SLAT_COUNT = 4;

    private static final float FRONT_Z = 8.5f / 16f;
    private static final float BACK_Z  = 7.5f / 16f;

    private SwellShutterSlatModels() {}

    public static Model[] build(BlockState material) {
        if (com.simibubi.create.AllBlocks.COPYCAT_BASE.has(material)) {
            return buildDefaultSlats();
        }

        BakedModel matModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(material);
        List<BakedQuad>[] perSlat = buildQuads(material);

        Model[] models = new Model[SLAT_COUNT];
        for (int i = 0; i < SLAT_COUNT; i++) {
            models[i] = BakedModelBuilder.create(new SlatModel(matModel, perSlat[i])).build();
        }
        return models;
    }

    private static Model[] buildDefaultSlats() {
        BakedModel model = AllPartialModels.SWELL_SHUTTER.get();
        List<BakedQuad>[] perSlat = defaultSlatQuads();
        Model[] models = new Model[SLAT_COUNT];
        for (int i = 0; i < SLAT_COUNT; i++) {
            models[i] = BakedModelBuilder.create(new SlatModel(model, perSlat[i])).build();
        }
        return models;
    }

    @SuppressWarnings("unchecked")
    private static List<BakedQuad>[] defaultSlatQuads() {
        BakedModel model = AllPartialModels.SWELL_SHUTTER.get();
        RandomSource rand = RandomSource.create(42L);

        List<BakedQuad> all = new ArrayList<>(model.getQuads(null, null, rand, ModelData.EMPTY, null));
        for (Direction d : Direction.values()) {
            all.addAll(model.getQuads(null, d, rand, ModelData.EMPTY, null));
        }

        List<BakedQuad>[] perSlat = new List[SLAT_COUNT];
        for (int i = 0; i < SLAT_COUNT; i++) perSlat[i] = new ArrayList<>();
        for (BakedQuad q : all) {
            float nudged = centroidX(q) - q.getDirection().getStepX() * 0.001f;
            int idx = net.minecraft.util.Mth.clamp((int) (nudged * SLAT_COUNT), 0, SLAT_COUNT - 1);
            perSlat[idx].add(q);
        }
        return perSlat;
    }

    private static float centroidX(BakedQuad q) {
        int[] v = q.getVertices();
        float sum = 0;
        for (int i = 0; i < 4; i++) sum += (float) BakedQuadHelper.getXYZ(v, i).x;
        return sum / 4f;
    }

    @SuppressWarnings("unchecked")
    public static List<BakedQuad>[] buildQuads(BlockState material) {
        if (com.simibubi.create.AllBlocks.COPYCAT_BASE.has(material)) {
            return defaultSlatQuads();
        }

        BakedModel matModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(material);
        RandomSource rand = RandomSource.create(42L);

        List<BakedQuad> south = matModel.getQuads(material, Direction.SOUTH, rand, ModelData.EMPTY, null);
        List<BakedQuad> north = matModel.getQuads(material, Direction.NORTH, rand, ModelData.EMPTY, null);
        List<BakedQuad> up  = matModel.getQuads(material, Direction.UP, rand, ModelData.EMPTY, null);
        List<BakedQuad> down = matModel.getQuads(material, Direction.DOWN, rand, ModelData.EMPTY, null);
        List<BakedQuad> east = matModel.getQuads(material, Direction.EAST, rand, ModelData.EMPTY, null);
        List<BakedQuad> west = matModel.getQuads(material, Direction.WEST, rand, ModelData.EMPTY, null);

        List<BakedQuad>[] perSlat = new List[SLAT_COUNT];
        for (int i = 0; i < SLAT_COUNT; i++) {
            float frac0 = i / (float) SLAT_COUNT;
            float frac1 = (i + 1) / (float) SLAT_COUNT;
            float x0 = frac0, x1 = frac1;

            List<BakedQuad> quads = new ArrayList<>();
            // Wide faces (front / back): crop X, move to slat depth.
            for (BakedQuad q : south) quads.add(cropAlong(q, Axis.X, frac0, frac1, Axis.Z, FRONT_Z));
            for (BakedQuad q : north) quads.add(cropAlong(q, Axis.X, frac0, frac1, Axis.Z, BACK_Z));
            // Top / bottom: crop X (U) and Z (V) to the thin box, keep Y.
            for (BakedQuad q : up)    quads.add(cropBox(q, frac0, frac1, BACK_Z, FRONT_Z, Axis.X, Axis.Z));
            for (BakedQuad q : down)  quads.add(cropBox(q, frac0, frac1, BACK_Z, FRONT_Z, Axis.X, Axis.Z));
            // Side edges (east / west): crop Z to the thin depth, move X to slat edge.
            for (BakedQuad q : east)  quads.add(cropAlong(q, Axis.Z, BACK_Z, FRONT_Z, Axis.X, x1));
            for (BakedQuad q : west)  quads.add(cropAlong(q, Axis.Z, BACK_Z, FRONT_Z, Axis.X, x0));
            perSlat[i] = quads;
        }
        return perSlat;
    }

    private enum Axis { X, Y, Z }

    private static float coord(Vec3 v, Axis a) {
        return (float) (a == Axis.X ? v.x : a == Axis.Y ? v.y : v.z);
    }

    private static Vec3 withCoord(Vec3 v, Axis a, float val) {
        return new Vec3(a == Axis.X ? val : v.x, a == Axis.Y ? val : v.y, a == Axis.Z ? val : v.z);
    }

    // Prevent textures from being smashed
    private static BakedQuad cropAlong(BakedQuad src, Axis cropAxis, float frac0, float frac1, Axis moveAxis, float moveTo) {
        int[] v = BakedQuadHelper.clone(src).getVertices();

        float cmin = Float.MAX_VALUE, cmax = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float c = coord(BakedQuadHelper.getXYZ(v, i), cropAxis);
            cmin = Math.min(cmin, c);
            cmax = Math.max(cmax, c);
        }
        float span = cmax - cmin;
        if (span < 1.0e-5f) span = 1f;
        float mid = (cmin + cmax) * 0.5f;

        // Perpendicular in-plane axis
        Axis perp = null;
        for (Axis a : Axis.values()) {
            if (a == cropAxis) continue;
            float amin = Float.MAX_VALUE, amax = -Float.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                float c = coord(BakedQuadHelper.getXYZ(v, i), a);
                amin = Math.min(amin, c);
                amax = Math.max(amax, c);
            }
            if (amax - amin > 1.0e-4f) { perp = a; break; }
        }

        // Gradient of U and V per unit of cropAxis
        float dUdc = 0f, dVdc = 0f;
        Vec3 p0 = BakedQuadHelper.getXYZ(v, 0);
        float perp0 = perp == null ? 0f : coord(p0, perp);
        float crop0 = coord(p0, cropAxis);
        for (int j = 1; j < 4; j++) {
            Vec3 pj = BakedQuadHelper.getXYZ(v, j);
            float cj = coord(pj, cropAxis);
            boolean samePerp = perp == null || Math.abs(coord(pj, perp) - perp0) < 1.0e-4f;
            if (samePerp && Math.abs(cj - crop0) > 1.0e-4f) {
                dUdc = (BakedQuadHelper.getU(v, j) - BakedQuadHelper.getU(v, 0)) / (cj - crop0);
                dVdc = (BakedQuadHelper.getV(v, j) - BakedQuadHelper.getV(v, 0)) / (cj - crop0);
                break;
            }
        }

        for (int i = 0; i < 4; i++) {
            Vec3 p = BakedQuadHelper.getXYZ(v, i);
            float oldC = coord(p, cropAxis);
            boolean isMin = oldC <= mid;
            float newC = cmin + span * (isMin ? frac0 : frac1);
            float delta = newC - oldC;
            Vec3 moved = withCoord(withCoord(p, cropAxis, newC), moveAxis, moveTo);
            BakedQuadHelper.setXYZ(v, i, moved);
            BakedQuadHelper.setU(v, i, BakedQuadHelper.getU(v, i) + dUdc * delta);
            BakedQuadHelper.setV(v, i, BakedQuadHelper.getV(v, i) + dVdc * delta);
        }
        return BakedQuadHelper.cloneWithCustomGeometry(src, v);
    }

    private static BakedQuad cropBox(BakedQuad src, float frac0, float frac1,
                                     float vLo, float vHi, Axis uAxis, Axis vAxis) {
        int[] v = BakedQuadHelper.clone(src).getVertices();

        float uMinPos = Float.MAX_VALUE, uMaxPos = -Float.MAX_VALUE;
        float vMinPos = Float.MAX_VALUE, vMaxPos = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            Vec3 p = BakedQuadHelper.getXYZ(v, i);
            uMinPos = Math.min(uMinPos, coord(p, uAxis));
            uMaxPos = Math.max(uMaxPos, coord(p, uAxis));
            vMinPos = Math.min(vMinPos, coord(p, vAxis));
            vMaxPos = Math.max(vMaxPos, coord(p, vAxis));
        }
        float uMid = (uMinPos + uMaxPos) * 0.5f;
        float vMid = (vMinPos + vMaxPos) * 0.5f;
        float texUMin = 0, texUMax = 0, texVMin = 0, texVMax = 0;
        boolean gu0 = false, gu1 = false, gv0 = false, gv1 = false;
        for (int i = 0; i < 4; i++) {
            Vec3 p = BakedQuadHelper.getXYZ(v, i);
            if (coord(p, uAxis) <= uMid && !gu0) { texUMin = BakedQuadHelper.getU(v, i); gu0 = true; }
            if (coord(p, uAxis) >  uMid && !gu1) { texUMax = BakedQuadHelper.getU(v, i); gu1 = true; }
            if (coord(p, vAxis) <= vMid && !gv0) { texVMin = BakedQuadHelper.getV(v, i); gv0 = true; }
            if (coord(p, vAxis) >  vMid && !gv1) { texVMax = BakedQuadHelper.getV(v, i); gv1 = true; }
        }

        float vf0 = (vLo - vMinPos) / (vMaxPos - vMinPos);
        float vf1 = (vHi - vMinPos) / (vMaxPos - vMinPos);

        for (int i = 0; i < 4; i++) {
            Vec3 p = BakedQuadHelper.getXYZ(v, i);
            boolean uIsMin = coord(p, uAxis) <= uMid;
            boolean vIsMin = coord(p, vAxis) <= vMid;

            float uFrac = uIsMin ? frac0 : frac1;
            float newU = uMinPos + (uMaxPos - uMinPos) * uFrac;
            float newV = vIsMin ? vLo : vHi;

            Vec3 moved = withCoord(withCoord(p, uAxis, newU), vAxis, newV);
            BakedQuadHelper.setXYZ(v, i, moved);
            BakedQuadHelper.setU(v, i, texUMin + (texUMax - texUMin) * uFrac);
            BakedQuadHelper.setV(v, i, texVMin + (texVMax - texVMin) * (vIsMin ? vf0 : vf1));
        }
        return BakedQuadHelper.cloneWithCustomGeometry(src, v);
    }

    private static final class SlatModel extends BakedModelWrapper<BakedModel> {
        private final List<BakedQuad> quads;

        SlatModel(BakedModel base, List<BakedQuad> quads) {
            super(base);
            this.quads = quads;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
            return side == null ? quads : List.of();
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, net.minecraft.client.renderer.RenderType renderType) {
            return side == null ? quads : List.of();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }
    }
}
