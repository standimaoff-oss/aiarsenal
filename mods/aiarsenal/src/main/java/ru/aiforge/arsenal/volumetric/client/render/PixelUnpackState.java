package ru.aiforge.arsenal.volumetric.client.render;

import org.lwjgl.opengl.*;

/** Pixel upload state is GLOBAL, not part of a texture. NativeImage or another
 * renderer may leave a nonzero row length / skip offset. Uploading our compact
 * direct FloatBuffer with that state can read beyond its native allocation; a
 * Java try/catch cannot recover from a native driver access violation.
 * A bound unpack PBO additionally changes a client pointer into a buffer offset.
 */
final class PixelUnpackState {
    private static final int[] PARAMETERS = {GL11.GL_UNPACK_ALIGNMENT, GL11.GL_UNPACK_ROW_LENGTH,
            GL11.GL_UNPACK_SKIP_ROWS, GL11.GL_UNPACK_SKIP_PIXELS, GL11.GL_UNPACK_SWAP_BYTES,
            GL11.GL_UNPACK_LSB_FIRST, GL12.GL_UNPACK_IMAGE_HEIGHT, GL12.GL_UNPACK_SKIP_IMAGES};
    private final int[] values = new int[PARAMETERS.length];
    private final int pbo = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);

    PixelUnpackState() {
        for (int i=0;i<PARAMETERS.length;i++) values[i]=GL11.glGetInteger(PARAMETERS[i]);
    }

    static void tightlyPackedClientMemory() {
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
        for (int parameter:PARAMETERS) GL11.glPixelStorei(parameter,parameter==GL11.GL_UNPACK_ALIGNMENT ? 4 : 0);
    }

    static void assertTightlyPacked() {
        if (GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING)!=0) throw new IllegalStateException("unpack PBO still bound");
        for (int parameter:PARAMETERS) if (GL11.glGetInteger(parameter)!=(parameter==GL11.GL_UNPACK_ALIGNMENT ? 4 : 0))
            throw new IllegalStateException("unsafe pixel unpack state: " + parameter);
    }

    void restore() {
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,pbo);
        for(int i=0;i<PARAMETERS.length;i++) GL11.glPixelStorei(PARAMETERS[i],values[i]);
    }

    void assertRestored() {
        if(GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING)!=pbo) throw new IllegalStateException("unpack PBO not restored");
        for(int i=0;i<PARAMETERS.length;i++) if(GL11.glGetInteger(PARAMETERS[i])!=values[i])
            throw new IllegalStateException("pixel unpack state not restored: "+PARAMETERS[i]);
    }
}
