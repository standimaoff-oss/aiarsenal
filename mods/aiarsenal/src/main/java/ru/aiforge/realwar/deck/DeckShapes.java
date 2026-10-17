package ru.aiforge.realwar.deck;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/**
 * Walkable collision shapes (reply 9): merged axis-aligned boxes per vehicle model, generated from the GLBs by
 * tools/deck/gen_boxes.py (voxelised, closed cavities filled, greedy-merged) and shipped as
 * assets/aiarsenal/deck/&lt;model&gt;.deck. Boxes are in model space (glTF: +X left, +Y up, +Z forward, metres) and
 * belong to a rotating group: 0 = hull, 1 = main turret (rotates by az about its pivot), 2 = secondary turret.
 */
public final class DeckShapes {
    public static final DeckShapes.Deck NONE = new Deck(0, new float[0], new float[6], 0);
    private static final Map<String, Deck> CACHE = new ConcurrentHashMap<>();

    private DeckShapes() {
    }

    /** One model's collision: {@code b} holds 7 floats per box (group, x0, y0, z0, x1, y1, z1). */
    public static final class Deck {
        public final float res;
        public final float[] b;
        /** Pivot (x, z) of each group 0..n in model space. */
        public final float[] pivot;
        /** Horizontal radius of the whole shape around the origin. */
        public final float radius;
        /** Highest box top above the origin (m). */
        public final float top;

        Deck(float res, float[] b, float[] pivot, float radius) {
            this.res = res;
            this.b = b;
            this.pivot = pivot;
            this.radius = radius;
            float t = 0;
            for (int i = 0; i + 6 < b.length; i += 7) t = Math.max(t, b[i + 5]);
            this.top = t;
        }

        public int count() {
            return b.length / 7;
        }
    }

    /** @param model resource path inside assets/aiarsenal, e.g. "models/glb/t90m.glb"; null if no shape is shipped */
    @Nullable
    public static Deck get(String model) {
        Deck d = CACHE.computeIfAbsent(model, DeckShapes::load);
        return d == NONE ? null : d;
    }

    private static Deck load(String model) {
        String name = model.substring(model.lastIndexOf('/') + 1).replace(".glb", "");
        try (InputStream in = DeckShapes.class.getResourceAsStream("/assets/aiarsenal/deck/" + name + ".deck")) {
            if (in == null) return NONE;
            DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(in));
            if (d.readInt() != 2) return NONE;
            float res = d.readFloat();
            int ng = d.readInt();
            int[] gi = new int[ng];
            float[] gp = new float[ng * 2];
            int maxG = 2;
            for (int i = 0; i < ng; i++) {
                gi[i] = d.readInt();
                gp[i * 2] = d.readFloat();
                gp[i * 2 + 1] = d.readFloat();
                if (gi[i] >= 0 && gi[i] < 64) maxG = Math.max(maxG, gi[i]);
            }
            // r10: any number of rotating groups (battleship: 4 main + 6 secondary turrets)
            float[] piv = new float[(maxG + 1) * 2];
            for (int i = 0; i < ng; i++) if (gi[i] >= 0 && gi[i] <= maxG) { piv[gi[i] * 2] = gp[i * 2]; piv[gi[i] * 2 + 1] = gp[i * 2 + 1]; }
            int n = d.readInt();
            float[] b = new float[n * 7];
            float r = 0;
            for (int i = 0; i < n; i++) {
                b[i * 7] = d.readByte();
                for (int k = 1; k < 7; k++) b[i * 7 + k] = d.readFloat();
                r = Math.max(r, Math.max(Math.max(Math.abs(b[i * 7 + 1]), Math.abs(b[i * 7 + 4])), Math.max(Math.abs(b[i * 7 + 3]), Math.abs(b[i * 7 + 6]))));
            }
            return new Deck(res, b, piv, r * 1.42f);
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger("aiarsenal").warn("deck shape {} unreadable: {}", model, ex.toString());
            return NONE;
        }
    }
}
