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

/**
 * Builds the four louver-slat {@link Model}s for a swell shutter from a copycat
 * material. Each slat is a genuine 1px-thick box whose faces are cropped from the
 * material's own face quads, so the material texture is shown at natural scale (no
 * stretching) and the slats have visible thickness.
 */
public final class SwellShutterSlatModels {

    public static final int SLAT_COUNT = 4;

    private static final float FRONT_Z = 8.5f / 16f;
    private static final float BACK_Z  = 7.5f / 16f;

    private SwellShutterSlatModels() {}

    /** @return an array of 4 Flywheel models, one per slat (index 0 = leftmost). */
    public static Model[] build(BlockState material) {
        // Empty shutter: use the authored swell_shutter model/texture as the default look.
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

    /**
     * Build the 4 slats from the authored {@code block/swell_shutter} model (used when
     * no material is placed). The model already contains 4 slat elements with the
     * swell_shutter texture and correct UVs; we split its quads into 4 groups by their
     * X position so each slat can pivot independently.
     */
    private static Model[] buildDefaultSlats() {
        BakedModel model = AllPartialModels.SWELL_SHUTTER.get();
        List<BakedQuad>[] perSlat = defaultSlatQuads();
        Model[] models = new Model[SLAT_COUNT];
        for (int i = 0; i < SLAT_COUNT; i++) {
            models[i] = BakedModelBuilder.create(new SlatModel(model, perSlat[i])).build();
        }
        return models;
    }

    /** Split the authored swell_shutter model's quads into 4 slats by X position. */
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
            int idx = net.minecraft.util.Mth.clamp((int) (centroidX(q) * SLAT_COUNT), 0, SLAT_COUNT - 1);
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

    /** @return the cropped face quads forming each of the 4 slat boxes. */
    @SuppressWarnings("unchecked")
    public static List<BakedQuad>[] buildQuads(BlockState material) {
        // Empty shutter: use the authored swell_shutter model quads.
        if (com.simibubi.create.AllBlocks.COPYCAT_BASE.has(material)) {
            return defaultSlatQuads();
        }

        BakedModel matModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(material);
        RandomSource rand = RandomSource.create(42L);

        List<BakedQuad> south = matModel.getQuads(material, Direction.SOUTH, rand, ModelData.EMPTY, null);
        List<BakedQuad> north = matModel.getQuads(material, Direction.NORTH, rand, ModelData.EMPTY, null);
        List<BakedQuad> up    = matModel.getQuads(material, Direction.UP,    rand, ModelData.EMPTY, null);
        List<BakedQuad> down  = matModel.getQuads(material, Direction.DOWN,  rand, ModelData.EMPTY, null);
        List<BakedQuad> east  = matModel.getQuads(material, Direction.EAST,  rand, ModelData.EMPTY, null);
        List<BakedQuad> west  = matModel.getQuads(material, Direction.WEST,  rand, ModelData.EMPTY, null);

        List<BakedQuad>[] perSlat = new List[SLAT_COUNT];
        for (int i = 0; i < SLAT_COUNT; i++) {
            float frac0 = i / (float) SLAT_COUNT;
            float frac1 = (i + 1) / (float) SLAT_COUNT;
            float x0 = frac0, x1 = frac1;

            List<BakedQuad> quads = new ArrayList<>();
            // Wide faces (front / back): crop X, move to slat depth. U follows X, V follows Y.
            for (BakedQuad q : south) quads.add(cropAxis(q, Axis.X, frac0, frac1, Axis.Z, FRONT_Z));
            for (BakedQuad q : north) quads.add(cropAxis(q, Axis.X, frac0, frac1, Axis.Z, BACK_Z));
            // Top / bottom: crop X (U) and Z (V) to the thin box, keep Y.
            for (BakedQuad q : up)    quads.add(cropBox(q, frac0, frac1, BACK_Z, FRONT_Z, Axis.X, Axis.Z));
            for (BakedQuad q : down)  quads.add(cropBox(q, frac0, frac1, BACK_Z, FRONT_Z, Axis.X, Axis.Z));
            // Side edges (east / west): crop Z (U), keep Y, move X to slat edge.
            for (BakedQuad q : east)  quads.add(cropAxis(q, Axis.Z, BACK_Z, FRONT_Z, Axis.X, x1));
            for (BakedQuad q : west)  quads.add(cropAxis(q, Axis.Z, BACK_Z, FRONT_Z, Axis.X, x0));
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

    /**
     * Crop a face quad along {@code cropAxis} to the fractional sub-range
     * [frac0,frac1] of its extent (interpolating U, which is assumed to follow
     * cropAxis), and set {@code moveAxis} to the absolute value {@code moveTo}.
     */
    private static BakedQuad cropAxis(BakedQuad src, Axis cropAxis, float frac0, float frac1,
                                      Axis moveAxis, float moveTo) {
        int[] v = BakedQuadHelper.clone(src).getVertices();

        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float c = coord(BakedQuadHelper.getXYZ(v, i), cropAxis);
            min = Math.min(min, c);
            max = Math.max(max, c);
        }
        float mid = (min + max) * 0.5f;
        float uMin = 0, uMax = 0;
        boolean gotMin = false, gotMax = false;
        for (int i = 0; i < 4; i++) {
            float c = coord(BakedQuadHelper.getXYZ(v, i), cropAxis);
            if (c <= mid && !gotMin) { uMin = BakedQuadHelper.getU(v, i); gotMin = true; }
            if (c >  mid && !gotMax) { uMax = BakedQuadHelper.getU(v, i); gotMax = true; }
        }

        for (int i = 0; i < 4; i++) {
            Vec3 xyz = BakedQuadHelper.getXYZ(v, i);
            boolean isMin = coord(xyz, cropAxis) <= mid;
            float frac = isMin ? frac0 : frac1;
            float newC = min + (max - min) * frac;
            Vec3 moved = withCoord(withCoord(xyz, cropAxis, newC), moveAxis, moveTo);
            BakedQuadHelper.setXYZ(v, i, moved);
            BakedQuadHelper.setU(v, i, uMin + (uMax - uMin) * frac);
        }
        return BakedQuadHelper.cloneWithCustomGeometry(src, v);
    }

    /**
     * Crop a horizontal (up/down) face to the thin slat box: crop {@code uAxis}
     * (U follows it) to [frac0,frac1] of its extent, and crop {@code vAxis}
     * (V follows it) to the absolute range [vLo,vHi].
     */
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

        // fractional position of the absolute [vLo,vHi] within the face's v-extent
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

    /** Minimal baked model exposing the cropped slat quads as general (unculled) quads. */
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
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand,
                                        ModelData data, net.minecraft.client.renderer.RenderType renderType) {
            return side == null ? quads : List.of();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }
    }
}
