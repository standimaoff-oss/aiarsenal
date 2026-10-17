package ru.aiforge.arsenal.volumetric.client.render;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Preserve actual GL state. All mutations inside the pass use raw GL as well,
 * so Minecraft's cached RenderSystem state stays consistent with the restored state. */
final class GlStateSnapshot implements AutoCloseable {
    private final PixelUnpackState unpack = new PixelUnpackState();
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int buffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(4);
    private final FloatBuffer clear = BufferUtils.createFloatBuffer(4);
    private final int texture0, texture1;

    GlStateSnapshot() {
        PixelUnpackState.tightlyPackedClientMemory();
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clear);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static void enabled(int capability, boolean value) {
        if (value) GL11.glEnable(capability); else GL11.glDisable(capability);
    }

    @Override public void close() {
        unpack.restore();
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        GL20.glUseProgram(program);
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture0);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture1);
        GL13.glActiveTexture(active);
        enabled(GL11.GL_DEPTH_TEST, depth);
        enabled(GL11.GL_CULL_FACE, cull);
        enabled(GL11.GL_BLEND, blend);
        enabled(GL11.GL_SCISSOR_TEST, scissor);
        GL11.glDepthMask(depthMask);
        GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
        GL11.glClearColor(clear.get(0), clear.get(1), clear.get(2), clear.get(3));
    }
}
