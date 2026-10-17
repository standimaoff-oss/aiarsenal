package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import ru.aiforge.realwar.glb.GlbModel;
import ru.aiforge.realwar.glb.Pose;

/**
 * Fast GLB renderer for the Real War models (r5 performance rewrite).
 * <ul>
 *   <li><b>Baked quads.</b> At first use every primitive is converted once into a flat vertex array. Consecutive glTF
 *   triangles that share an edge (Blender triangulates quads as (a,b,c)(c,d,a)) are merged back into one real quad,
 *   the rest become degenerate quads. Lossless (same two triangles) and ~45 % fewer vertices than 4 per triangle;
 *   vanilla QUADS render types stay, so shader packs see ordinary entity geometry.</li>
 *   <li><b>One draw call per texture.</b> Primitives are grouped by render type (texture + blend) and opaque types
 *   come first; previously every material switch flushed the buffer (hundreds of draw calls per tank).</li>
 *   <li><b>No per-vertex allocation.</b> Position / normal are transformed inline and sent through the bulk
 *   {@code VertexConsumer.vertex(...)} fast path (the old chained calls allocated two vectors per vertex).</li>
 *   <li><b>Distance LOD.</b> {@code <model>_lod1.glb} (Blender-decimated) is used beyond {@link #LOD_DISTANCE}
 *   blocks (corrected for zoom). Its nodes take their world matrices from the full model's pose by node name and
 *   its materials take the full model's textures by material name.</li>
 * </ul>
 */
public final class GlbRenderer {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final GlbModel.Material DEFAULT_MAT = new GlbModel.Material();
    /** Effective distance (blocks, at the default FOV) beyond which the LOD1 model is drawn. */
    public static double LOD_DISTANCE = 26.0;
    /** r18: colour multiplier for burnt-out wrecks / scorched debris (1 = normal). Set around a render call. */
    public static float TINT = 1f;
    /** r20 hit camera: draw every primitive as flat translucent grey "X-ray" (alpha &gt; 0 enables). */
    public static float XRAY = 0f;
    public static float XRAY_GREY = 0.62f;
    /** r20 (msg 20): per-node colour, 4 floats (r g b a) per node of the FULL model, used by the XRAY and FLAT modes;
     *  a &lt;= 0 keeps the default. In XRAY mode a multiplies the alpha (hit modules more opaque). */
    public static float[] NODE_TINT;
    /** r20: colour of untinted XRAY geometry (null = XRAY_GREY) - the hit camera's module props. */
    public static float[] XRAY_RGB;
    /** r20: opaque flat colour render (top-down damage card): every node in its NODE_TINT colour or FLAT_RGB. */
    public static boolean FLAT;
    public static float[] FLAT_RGB = {0.62f, 0.64f, 0.66f};
    private static float cR, cG, cB, cA;
    /** Floats per baked vertex: x y z nx ny nz u v r g b a */
    private static final int STRIDE = 12;

    private GlbRenderer() {
    }

    // ------------------------------------------------------------------ baked data
    static final class Item {
        final int node;
        final float[] v;
        final int count;
        boolean bright;

        Item(int node, float[] v, int count) {
            this.node = node;
            this.v = v;
            this.count = count;
        }
    }

    static final class Batch {
        final RenderType type;
        final boolean blend;
        final List<Item> list = new ArrayList<>();
        Item[] items;

        Batch(RenderType type, boolean blend) {
            this.type = type;
            this.blend = blend;
        }
    }

    private record NodeDraw(RenderType type, Item item) {}
    static final class Baked {
        NodeDraw[][] byNode;
        Batch[] batches;
        Matrix4f[] m4;
        Matrix3f[] m3;
        int[] stamp;
        int frame;
        int vertices;
    }

    private static boolean isBlend(GlbModel.Material mt) {
        return mt.blend || mt.color[3] < 0.99f;
    }

    /** Material + texture used for a primitive of {@code lm}; LOD models borrow them from {@code src} by name. */
    private static Baked bake(GlbAssets.Loaded lm, GlbAssets.Loaded src) {
        GlbModel m = lm.model;
        Map<String, Integer> srcByName = new HashMap<>();
        if (src != lm) {
            for (int i = 0; i < src.model.materials.size(); ++i) srcByName.putIfAbsent(src.model.materials.get(i).name, i);
        }
        Map<RenderType, Batch> map = new LinkedHashMap<>();
        int verts = 0;
        for (int ni = 0; ni < m.nodes.size(); ++ni) {
            GlbModel.Node node = m.nodes.get(ni);
            if (node.mesh < 0) continue;
            for (GlbModel.Primitive p : m.meshes.get(node.mesh).prims) {
                GlbModel.Material mat = DEFAULT_MAT;
                ResourceLocation tex = WHITE;
                if (p.material >= 0 && p.material < m.materials.size()) {
                    mat = m.materials.get(p.material);
                    if (src != lm) {
                        Integer si = srcByName.get(mat.name);
                        if (si != null) {
                            mat = src.model.materials.get(si);
                            tex = mat.image >= 0 && mat.image < src.textures.length ? src.textures[mat.image] : WHITE;
                        }
                    } else {
                        tex = mat.image >= 0 && mat.image < lm.textures.length ? lm.textures[mat.image] : WHITE;
                    }
                }
                boolean blend = isBlend(mat);
                RenderType rt = blend ? RenderType.entityTranslucentCull(tex) : RenderType.entityCutoutNoCull(tex);
                Batch b = map.computeIfAbsent(rt, k -> new Batch(k, blend));
                Item it = bakePrim(ni, p, mat);
                if (it.count > 0) {
                    b.list.add(it);
                    verts += it.count;
                }
            }
        }
        List<Batch> order = new ArrayList<>();
        for (Batch b : map.values()) if (!b.blend) order.add(b);
        for (Batch b : map.values()) if (b.blend) order.add(b);
        Baked bk = new Baked();
        bk.batches = order.toArray(new Batch[0]);
        for (Batch b : bk.batches) b.items = b.list.toArray(new Item[0]);
        int n = m.nodes.size();
        bk.m4 = new Matrix4f[n];
        bk.m3 = new Matrix3f[n];
        bk.stamp = new int[n];
        for (int i = 0; i < n; ++i) {
            bk.m4[i] = new Matrix4f();
            bk.m3[i] = new Matrix3f();
        }
        // Build once, owned by the loaded asset: O(primitives), released with the asset on F3+T.
        // Preserve opaque/translucent batch order exactly; do not copy vertex arrays.
        List<List<NodeDraw>> nodes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) nodes.add(new ArrayList<>());
        for (Batch batch : bk.batches) for (Item item : batch.items)
            nodes.get(item.node).add(new NodeDraw(batch.type, item));
        bk.byNode = new NodeDraw[n][];
        for (int i = 0; i < n; i++) bk.byNode[i] = nodes.get(i).toArray(new NodeDraw[0]);
        bk.vertices = verts;
        return bk;
    }

    /** Triangle list -> quad list (4 indices per quad). */
    static int[] quadIndices(int[] idx) {
        int[] out = new int[(idx.length / 3) * 4];
        int o = 0;
        int i = 0;
        while (i + 2 < idx.length) {
            int a = idx[i], b = idx[i + 1], c = idx[i + 2];
            boolean merged = false;
            if (i + 5 < idx.length) {
                int d = idx[i + 3], e = idx[i + 4], f = idx[i + 5];
                int[] t1 = {a, b, c};
                int[] t2 = {d, e, f};
                outer:
                for (int r1 = 0; r1 < 3; ++r1) {
                    int x = t1[r1], y = t1[(r1 + 1) % 3], z = t1[(r1 + 2) % 3];
                    for (int r2 = 0; r2 < 3; ++r2) {
                        // quad (x,y,z,w) is drawn as triangles (x,y,z) + (z,w,x)
                        if (t2[r2] == z && t2[(r2 + 2) % 3] == x) {
                            int w = t2[(r2 + 1) % 3];
                            if (w == y) break outer;
                            out[o++] = x;
                            out[o++] = y;
                            out[o++] = z;
                            out[o++] = w;
                            merged = true;
                            break outer;
                        }
                    }
                }
            }
            if (merged) {
                i += 6;
            } else {
                out[o++] = a;
                out[o++] = b;
                out[o++] = c;
                out[o++] = c;
                i += 3;
            }
        }
        return java.util.Arrays.copyOf(out, o);
    }

    private static Item bakePrim(int node, GlbModel.Primitive p, GlbModel.Material mat) {
        int[] q = quadIndices(p.idx);
        float[] v = new float[q.length * STRIDE];
        float r = mat.color[0], g = mat.color[1], b = mat.color[2], a = mat.color[3];
        for (int k = 0; k < q.length; ++k) {
            int vi = q[k];
            int o = k * STRIDE;
            v[o] = p.pos[vi * 3];
            v[o + 1] = p.pos[vi * 3 + 1];
            v[o + 2] = p.pos[vi * 3 + 2];
            v[o + 3] = p.nrm[vi * 3];
            v[o + 4] = p.nrm[vi * 3 + 1];
            v[o + 5] = p.nrm[vi * 3 + 2];
            v[o + 6] = p.uv != null ? p.uv[vi * 2] : 0f;
            v[o + 7] = p.uv != null ? p.uv[vi * 2 + 1] : 0f;
            float cr = r, cg = g, cb = b;
            if (p.col != null) {
                cr *= p.col[vi * 3];
                cg *= p.col[vi * 3 + 1];
                cb *= p.col[vi * 3 + 2];
            }
            v[o + 8] = cr;
            v[o + 9] = cg;
            v[o + 10] = cb;
            v[o + 11] = a;
        }
        Item it = new Item(node, v, q.length);
        it.bright = mat.emissive;
        return it;
    }

    private static Baked baked(GlbAssets.Loaded lm, GlbAssets.Loaded src) {
        if (lm.baked == null) lm.baked = bake(lm, src);
        return (Baked) lm.baked;
    }

    // ------------------------------------------------------------------ rendering
    /** Camera distance of the current pose origin, corrected for zoom (sights, spyglass). */
    private static double effectiveDistance(Matrix4f pose) {
        double d = Math.sqrt(pose.m30() * pose.m30() + pose.m31() * pose.m31() + pose.m32() * pose.m32());
        try {
            float m11 = RenderSystem.getProjectionMatrix().m11();
            double fov = Minecraft.getInstance().options.fov().get();
            double k = m11 * Math.tan(Math.toRadians(fov) / 2.0);
            if (k > 1.05) d /= k;
        } catch (Throwable ignored) {
            // no projection yet (item/GUI render) -> raw distance
        }
        return d;
    }

    /**
     * Heavy models switch earlier: 26 blocks for a ~22k-triangle tank, down to 12 blocks for the 98k Yars TEL
     * (switch distance ~ 1/sqrt(triangles), i.e. roughly constant triangles per screen pixel).
     */
    static double lodDistance(GlbAssets.Loaded lm) {
        if (lm.lodDist <= 0) {
            double tris = Math.max(1, lm.model.totalTriangles());
            lm.lodDist = Math.max(12.0, Math.min(LOD_DISTANCE, LOD_DISTANCE * Math.sqrt(22000.0 / tris)));
        }
        return lm.lodDist;
    }

    // Iris / Oculus shadow pass (shader packs draw every entity a second time into the shadow map): the LOD model is
    // always good enough for a shadow. Looked up by reflection - no compile/runtime dependency on the shader mod.
    private static java.lang.reflect.Method irisShadow;
    private static Object irisApi;
    private static boolean irisChecked;

    static boolean shaderShadowPass() {
        if (!irisChecked) {
            irisChecked = true;
            try {
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = c.getMethod("getInstance").invoke(null);
                irisShadow = c.getMethod("isRenderingShadowPass");
            } catch (Throwable ignored) {
                irisShadow = null;
            }
        }
        if (irisShadow == null) return false;
        try {
            return (Boolean) irisShadow.invoke(irisApi);
        } catch (Throwable t) {
            irisShadow = null;
            return false;
        }
    }

    public static void render(GlbAssets.Loaded lm, Pose pose, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        PoseStack.Pose last = ps.last();
        GlbAssets.Loaded use = lm;
        int[] map = null;
        if (lm.lod != null && (shaderShadowPass() || effectiveDistance(last.pose()) > lodDistance(lm))) {
            use = lm.lod;
            map = lm.lodMap;
        }
        Baked bk = baked(use, lm);
        int frame = ++bk.frame;
        Matrix4f base = last.pose();
        Matrix3f baseN = last.normal();
        Matrix3f tmpN = new Matrix3f();
        VertexConsumer xray = XRAY > 0f ? buffers.getBuffer(RenderType.entityTranslucentCull(WHITE))
                : FLAT ? buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE)) : null;
        for (Batch b : bk.batches) {
            VertexConsumer vc = xray;
            for (Item it : b.items) {
                int wi = map == null ? it.node : map[it.node];
                if (wi < 0 || pose.isHidden(wi)) continue;
                if (xray != null && NODE_TINT != null && wi * 4 + 3 < NODE_TINT.length && NODE_TINT[wi * 4 + 3] < 0f) continue;   // r20: skip node
                if (bk.stamp[it.node] != frame) {
                    bk.stamp[it.node] = frame;
                    Matrix4f nw = pose.world[wi];
                    bk.m4[it.node].set(base).mul(nw);
                    nw.normal(tmpN);
                    bk.m3[it.node].set(baseN).mul(tmpN);
                }
                if (vc == null) vc = buffers.getBuffer(b.type);
                if (xray != null) colour(wi);
                emit(vc, it, bk.m4[it.node], bk.m3[it.node], it.bright ? 0xF000F0 : light, overlay);
            }
        }
    }

    /** Draws one node's geometry with the current pose stack (used by item / part renderers). */
    public static void renderNode(GlbAssets.Loaded lm, int ni, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (ni < 0 || ni >= lm.model.nodes.size() || lm.model.nodes.get(ni).mesh < 0) return;
        Baked bk = baked(lm, lm);
        PoseStack.Pose last = ps.last();
        boolean special = XRAY > 0f || FLAT;
        if (special) colour(-1);
        if (qaScan) {
            // Opt-in A/B reference path; unreachable on normal clients.
            for (Batch batch : bk.batches) for (Item it : batch.items) {
                if (it.node == ni) emitNode(batch.type, it, last, buffers, light, overlay);
            }
        } else {
            for (NodeDraw draw : bk.byNode[ni]) emitNode(draw.type, draw.item, last, buffers, light, overlay);
        }
    }

    private static void emitNode(RenderType type, Item it, PoseStack.Pose last, MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer vc = XRAY > 0f ? buffers.getBuffer(RenderType.entityTranslucentCull(WHITE))
                : FLAT ? buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE)) : buffers.getBuffer(type);
        emit(vc, it, last.pose(), last.normal(), light, overlay);
    }

    private static boolean qaScan;
    public static void qaReference(boolean enabled) {
        if (!Boolean.getBoolean("aiarsenal.visualQa")) throw new IllegalStateException("QA disabled");
        qaScan = enabled;
    }

    /** Exact identity/order equivalence on actual baked models, not estimates of triangles or FPS. */
    public static String qaIndex() {
        if (!Boolean.getBoolean("aiarsenal.visualQa")) throw new IllegalStateException("QA disabled");
        long oldVisits = 0, newVisits = 0;
        int assets = 0;
        for (String asset : new String[]{"historical_uniforms", "uniforms", "tt33", "ppsh41", "t90m"}) {
            var lm = GlbAssets.get(new ResourceLocation("aiarsenal", "models/glb/" + asset + ".glb"));
            if (lm == null) throw new IllegalStateException("missing benchmark model " + asset);
            Baked bk = baked(lm, lm); assets++;
            for (int node = 0; node < bk.byNode.length; node++) {
                int index = 0;
                for (Batch batch : bk.batches) for (Item it : batch.items) {
                    oldVisits++;
                    if (it.node != node) continue;
                    NodeDraw draw = bk.byNode[node][index++];
                    if (draw.item != it || draw.type != batch.type) throw new IllegalStateException("node index changed draw order");
                }
                if (index != bk.byNode[node].length) throw new IllegalStateException("lost primitive");
                newVisits += index;
            }
        }
        if (newVisits <= 0 || oldVisits <= newVisits) throw new IllegalStateException("no reduction");
        return "INDEX PASS assets=" + assets + " scanVisits=" + oldVisits + " indexedVisits=" + newVisits;
    }

    /** XRAY / FLAT colour of world node wi (-1 = untinted). */
    private static void colour(int wi) {
        float[] t = NODE_TINT;
        boolean tinted = t != null && wi >= 0 && wi * 4 + 3 < t.length && t[wi * 4 + 3] > 0f;
        if (FLAT && XRAY <= 0f) {
            float[] c = tinted ? new float[]{t[wi * 4], t[wi * 4 + 1], t[wi * 4 + 2]} : FLAT_RGB;
            cR = c[0] * TINT;
            cG = c[1] * TINT;
            cB = c[2] * TINT;
            cA = 1f;
            return;
        }
        if (tinted) {
            cR = t[wi * 4];
            cG = t[wi * 4 + 1];
            cB = t[wi * 4 + 2];
            cA = Math.min(1f, XRAY * t[wi * 4 + 3]);
        } else if (XRAY_RGB != null) {
            cR = XRAY_RGB[0];
            cG = XRAY_RGB[1];
            cB = XRAY_RGB[2];
            cA = XRAY;
        } else {
            cR = XRAY_GREY;
            cG = XRAY_GREY;
            cB = XRAY_GREY * 1.04f;
            cA = XRAY;
        }
    }

    private static void emit(VertexConsumer vc, Item it, Matrix4f m, Matrix3f n, int light, int overlay) {
        final float m00 = m.m00(), m01 = m.m01(), m02 = m.m02();
        final float m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
        final float m20 = m.m20(), m21 = m.m21(), m22 = m.m22();
        final float m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
        final float n00 = n.m00(), n01 = n.m01(), n02 = n.m02();
        final float n10 = n.m10(), n11 = n.m11(), n12 = n.m12();
        final float n20 = n.m20(), n21 = n.m21(), n22 = n.m22();
        final float[] v = it.v;
        final int end = it.count * STRIDE;
        for (int o = 0; o < end; o += STRIDE) {
            float x = v[o], y = v[o + 1], z = v[o + 2];
            float nx = v[o + 3], ny = v[o + 4], nz = v[o + 5];
            float tx = n00 * nx + n10 * ny + n20 * nz;
            float ty = n01 * nx + n11 * ny + n21 * nz;
            float tz = n02 * nx + n12 * ny + n22 * nz;
            float len = tx * tx + ty * ty + tz * tz;
            if (len > 1e-12f) {
                float inv = (float) (1.0 / Math.sqrt(len));
                tx *= inv;
                ty *= inv;
                tz *= inv;
            }
            if (XRAY > 0f || FLAT) {
                vc.vertex(m00 * x + m10 * y + m20 * z + m30, m01 * x + m11 * y + m21 * z + m31, m02 * x + m12 * y + m22 * z + m32,
                        cR, cG, cB, cA, 0f, 0f, overlay, light, tx, ty, tz);
                continue;
            }
            vc.vertex(m00 * x + m10 * y + m20 * z + m30, m01 * x + m11 * y + m21 * z + m31, m02 * x + m12 * y + m22 * z + m32,
                    v[o + 8] * TINT, v[o + 9] * TINT, v[o + 10] * TINT, v[o + 11], v[o + 6], v[o + 7], overlay, light, tx, ty, tz);
        }
    }
}
