/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.NativeImage
 *  com.mojang.logging.LogUtils
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.texture.AbstractTexture
 *  net.minecraft.client.renderer.texture.DynamicTexture
 *  net.minecraft.resources.ResourceLocation
 *  org.slf4j.Logger
 */
package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import ru.aiforge.realwar.glb.GlbModel;
import ru.aiforge.realwar.glb.Pose;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class GlbAssets {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, Loaded> CACHE = new HashMap<ResourceLocation, Loaded>();
    private static final Loaded FAILED = new Loaded(null, new ResourceLocation[0]);

    private GlbAssets() {
    }

    public static Loaded get(ResourceLocation path) {
        Loaded l = CACHE.computeIfAbsent(path, GlbAssets::load);
        return l == FAILED ? null : l;
    }

    /** PNG through NativeImage; anything else (JPEG from Blender exports) through ImageIO. */
    static NativeImage decode(byte[] data) throws java.io.IOException {
        boolean png = data.length > 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P';
        if (png) {
            try {
                return NativeImage.read((InputStream)new ByteArrayInputStream(data));
            } catch (Exception ignored) {
                // 16-bit PNG etc. -> ImageIO
            }
        }
        java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(new ByteArrayInputStream(data));
        if (bi == null) {
            throw new java.io.IOException("unsupported texture format");
        }
        NativeImage ni = new NativeImage(bi.getWidth(), bi.getHeight(), false);
        for (int y = 0; y < bi.getHeight(); ++y) {
            for (int x = 0; x < bi.getWidth(); ++x) {
                int argb = bi.getRGB(x, y);
                ni.setPixelRGBA(x, y, (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16));
            }
        }
        return ni;
    }

    private static Loaded load(ResourceLocation path) {
        Minecraft mc = Minecraft.getInstance();
        try (InputStream in = mc.getResourceManager().open(path)) {
            GlbModel model = GlbModel.read(in);
            ResourceLocation[] tex = new ResourceLocation[model.images.size()];
            for (int i = 0; i < tex.length; ++i) {
                NativeImage img = GlbAssets.decode(model.images.get(i));
                ResourceLocation id = new ResourceLocation("aiarsenal", "glb/" + path.getPath().replaceAll("[^a-z0-9_./-]", "_").replace('/', '_') + "_" + i);
                mc.getTextureManager().register(id, (AbstractTexture)new DynamicTexture(img));
                tex[i] = id;
            }
            LOGGER.info("[realwar] GLB {}: {} \u0432\u0435\u0440\u0448\u0438\u043d, {} \u0442\u0440\u0435\u0443\u0433\u043e\u043b\u044c\u043d\u0438\u043a\u043e\u0432, {} \u0443\u0437\u043b\u043e\u0432, {} \u0430\u043d\u0438\u043c\u0430\u0446\u0438\u0439", new Object[]{path, model.totalVertices(), model.totalTriangles(), model.nodes.size(), model.animations.size()});
            Loaded l = new Loaded(model, tex);
            GlbAssets.loadLod(mc, path, l);
            return l;
        }
        catch (Exception e) {
            LOGGER.error("[realwar] \u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044c GLB {}: {}", path, e.toString());
            return FAILED;
        }
    }

    /** Optional {@code <name>_lod1.glb}: Blender-decimated copy, textures come from the full model (see GlbRenderer). */
    private static void loadLod(Minecraft mc, ResourceLocation path, Loaded full) {
        String p = path.getPath();
        if (!p.endsWith(".glb")) return;
        ResourceLocation lp = new ResourceLocation(path.getNamespace(), p.substring(0, p.length() - 4) + "_lod1.glb");
        if (mc.getResourceManager().getResource(lp).isEmpty()) return;
        try (InputStream in = mc.getResourceManager().open(lp)) {
            GlbModel lm = GlbModel.read(in);
            int[] map = new int[lm.nodes.size()];
            int missing = 0;
            for (int i = 0; i < map.length; ++i) {
                map[i] = full.model.nodeIndex(lm.nodes.get(i).name);
                if (map[i] < 0 && lm.nodes.get(i).mesh >= 0) ++missing;
            }
            if (missing > 0) {
                LOGGER.warn("[realwar] LOD {}: {} mesh nodes have no match in the full model - LOD disabled", lp, missing);
                return;
            }
            full.lod = new Loaded(lm, new ResourceLocation[0]);
            full.lodMap = map;
            LOGGER.info("[realwar] LOD {}: {} -> {} tris", lp, full.model.totalTriangles(), lm.totalTriangles());
        } catch (Exception e) {
            LOGGER.warn("[realwar] LOD {} not loaded: {}", lp, e.toString());
        }
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (Loaded l : CACHE.values()) {
            for (ResourceLocation t : l.textures) {
                mc.getTextureManager().release(t);
            }
        }
        CACHE.clear();
    }

    public static final class Loaded {
        public final GlbModel model;
        public final ResourceLocation[] textures;
        /** Baked render data (GlbRenderer), LOD1 model and its node map (lod node -> full model node). */
        Object baked;
        public Loaded lod;
        public int[] lodMap;
        double lodDist;
        private Pose staticPose;
        private Pose scratch;

        Loaded(GlbModel model, ResourceLocation[] textures) {
            this.model = model;
            this.textures = textures;
        }

        /** Override the automatic LOD switch distance (blocks) - very large models (battleship) need a much longer one. */
        public double lodDist() {
            return this.lodDist;
        }

        public void setLodDistance(double d) {
            this.lodDist = d;
        }

        public Pose scratch() {
            if (this.scratch == null) {
                this.scratch = new Pose(this.model);
            }
            this.scratch.reset();
            return this.scratch;
        }

        public Pose staticPose() {
            if (this.staticPose == null) {
                this.staticPose = new Pose(this.model);
                this.staticPose.compute();
            }
            return this.staticPose;
        }
    }
}

