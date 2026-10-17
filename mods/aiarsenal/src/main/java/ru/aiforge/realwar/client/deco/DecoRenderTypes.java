package ru.aiforge.realwar.client.deco;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Additive, unculled, no-depth-write glow (beams, halos). Same shader + blending as lightning. */
public final class DecoRenderTypes extends RenderType {
    private DecoRenderTypes(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean a, boolean b, Runnable c, Runnable d) {
        super(n, f, m, s, a, b, c, d);
    }

    public static final RenderType GLOW = create("aiarsenal_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 8192, false, true,
            CompositeState.builder().setShaderState(RENDERTYPE_LIGHTNING_SHADER).setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).setOutputState(TRANSLUCENT_TARGET).createCompositeState(false));
}
