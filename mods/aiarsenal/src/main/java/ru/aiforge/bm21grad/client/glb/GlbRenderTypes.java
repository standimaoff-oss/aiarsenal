package ru.aiforge.bm21grad.client.glb;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Entity render types for GLB models (QUADS: triangle pairs are merged into quads by GlbRenderer, r5).
 * Extends RenderType only to reach the protected render-state shards.
 */
public final class GlbRenderTypes extends RenderType {
    private GlbRenderTypes(String n, VertexFormat f, VertexFormat.Mode m, int b, boolean c, boolean s, Runnable a, Runnable r) {
        super(n, f, m, b, c, s, a, r);
    }

    private static final Function<ResourceLocation, RenderType> CUTOUT = Util.memoize(tex -> create("bm21grad_glb_cutout",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1 << 18, true, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                    .setTextureState(new TextureStateShard(tex, false, false))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true)));

    private static final Function<ResourceLocation, RenderType> CUTOUT_CULL = Util.memoize(tex -> create("bm21grad_glb_cutout_cull",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1 << 18, true, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                    .setTextureState(new TextureStateShard(tex, false, false))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true)));

    private static final Function<ResourceLocation, RenderType> TRANSLUCENT = Util.memoize(tex -> create("bm21grad_glb_translucent",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1 << 16, true, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(tex, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE) // glass must not hide particles/entities drawn after it
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true)));

    public static RenderType cutout(ResourceLocation tex, boolean doubleSided) {
        return doubleSided ? CUTOUT.apply(tex) : CUTOUT_CULL.apply(tex);
    }

    public static RenderType translucent(ResourceLocation tex) {
        return TRANSLUCENT.apply(tex);
    }
}
