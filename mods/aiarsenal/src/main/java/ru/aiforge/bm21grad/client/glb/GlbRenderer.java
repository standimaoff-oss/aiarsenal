package ru.aiforge.bm21grad.client.glb;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import ru.aiforge.bm21grad.Bm21Grad;
import ru.aiforge.bm21grad.glb.GlbModel;
import ru.aiforge.bm21grad.glb.GlbPose;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side renderer for a {@link GlbModel}: uploads embedded images as dynamic textures (PNG via STB,
 * JPEG via ImageIO), then draws every visible mesh node of a {@link GlbPose} as triangles with
 * per-vertex colour (baseColorFactor x COLOR_0 x tint), normals, UVs, lightmap and overlay.
 * Supports skinned meshes (CPU skinning, 4 influences) and emissive materials (full-bright).
 */
public final class GlbRenderer {
    private static final Map<String, GlbRenderer> CACHE = new HashMap<>();
    private static ResourceLocation whiteTex;

    public final GlbModel model;
    private final ResourceLocation[] imageTex;
    /** Optional per-material colour override (e.g. rocket type band). */
    public interface MaterialTint {
        float[] tint(int materialIndex, GlbModel.Material material);
    }

    private GlbRenderer(GlbModel model) {
        this(model, true);
    }

    private GlbRenderer(GlbModel model, boolean upload) {
        this.model = model;
        imageTex = new ResourceLocation[upload ? model.images.size() : 0];
        for (int i = 0; i < imageTex.length; i++) {
            GlbModel.Image im = model.images.get(i);
            try {
                NativeImage ni = decode(im);
                DynamicTexture dt = new DynamicTexture(ni);
                dt.setFilter(true, false); // linear, photo-like textures
                ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(Bm21Grad.MODID, "glb/" + model.name + "/" + i);
                Minecraft.getInstance().getTextureManager().register(rl, dt);
                imageTex[i] = rl;
            } catch (Exception e) {
                Bm21Grad.LOGGER.error("GLB {}: cannot decode image {} ({})", model.name, im.name, im.mime, e);
                imageTex[i] = white();
            }
        }
        Bm21Grad.LOGGER.info("GLB {}: uploaded {} textures", model.name, imageTex.length);
    }

    public static GlbRenderer of(GlbModel model) {
        return CACHE.computeIfAbsent(model.name, n -> new GlbRenderer(model));
    }

    private static ResourceLocation white() {
        if (whiteTex == null) {
            NativeImage ni = new NativeImage(4, 4, false);
            for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) ni.setPixelRGBA(x, y, 0xFFFFFFFF);
            whiteTex = ResourceLocation.fromNamespaceAndPath(Bm21Grad.MODID, "glb/white");
            Minecraft.getInstance().getTextureManager().register(whiteTex, new DynamicTexture(ni));
        }
        return whiteTex;
    }

    private static NativeImage decode(GlbModel.Image im) throws Exception {
        boolean png = im.data.length > 8 && (im.data[0] & 0xFF) == 0x89 && im.data[1] == 'P';
        if (png) {
            // NativeImage.read(byte[]) copies onto the 64 KB LWJGL MemoryStack -> OOM for real textures; use a heap-off buffer
            java.nio.ByteBuffer buf = org.lwjgl.system.MemoryUtil.memAlloc(im.data.length);
            try {
                buf.put(im.data).flip();
                return NativeImage.read(buf);
            } catch (Exception ignored) {
                // fall through to ImageIO (e.g. 16-bit PNG)
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(buf);
            }
        }
        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(im.data));
        if (bi == null) throw new IllegalArgumentException("unsupported image format " + im.mime);
        NativeImage ni = new NativeImage(bi.getWidth(), bi.getHeight(), false);
        for (int y = 0; y < bi.getHeight(); y++)
            for (int x = 0; x < bi.getWidth(); x++) {
                int argb = bi.getRGB(x, y);
                int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
                ni.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r); // NativeImage is ABGR
            }
        return ni;
    }

    public ResourceLocation textureFor(GlbModel.Material m) {
        if (m == null || m.baseColorTexture < 0 || m.baseColorTexture >= model.textures.size()) return white();
        int img = model.textures.get(m.baseColorTexture);
        return img >= 0 && img < imageTex.length ? imageTex[img] : white();
    }

    private final Matrix4f tmpM = new Matrix4f();
    private final Matrix3f tmpN = new Matrix3f();
    private final Vector4f tmpP = new Vector4f();
    private final Vector3f tmpV = new Vector3f();

    // r5 performance: draw order grouped by (pass, texture) -> one draw call per texture instead of one per primitive
    // switch; triangle pairs merged into real quads (vanilla QUADS mode, ~45 % fewer vertices); node matrices cached.
    private int[] orderNode, orderPrim;
    private int[][] quadIdx;          // per mesh-primitive flat index -> quad index list
    private int[] primBase;           // mesh -> first flat primitive index
    private Matrix4f[] nodeM;
    private Matrix3f[] nodeN;
    private final Matrix3f tmpN2 = new Matrix3f();

    private void buildOrder(GlbRenderer src) {
        int nm = model.meshes.size();
        primBase = new int[nm];
        int flat = 0;
        for (int mi = 0; mi < nm; mi++) {
            primBase[mi] = flat;
            flat += model.meshes.get(mi).primitives.size();
        }
        quadIdx = new int[flat][];
        java.util.List<long[]> entries = new java.util.ArrayList<>();
        java.util.Map<ResourceLocation, Integer> texOrder = new java.util.HashMap<>();
        for (int ni = 0; ni < model.nodes.size(); ni++) {
            GlbModel.Node node = model.nodes.get(ni);
            if (node.mesh < 0) continue;
            java.util.List<GlbModel.Primitive> prims = model.meshes.get(node.mesh).primitives;
            for (int pi = 0; pi < prims.size(); pi++) {
                GlbModel.Primitive pr = prims.get(pi);
                GlbModel.Material mat = pr.material >= 0 ? model.materials.get(pr.material) : null;
                int pass = mat != null && mat.alphaMode == GlbModel.AlphaMode.BLEND ? 1 : 0;
                GlbModel.Material tm = mat;
                if (lodMatMap != null && pr.material >= 0 && lodMatMap[pr.material] >= 0) tm = src.model.materials.get(lodMatMap[pr.material]);
                if (tm != null && tm.alphaMode == GlbModel.AlphaMode.BLEND) pass = 1;
                int t = texOrder.computeIfAbsent(src.textureFor(tm), k -> texOrder.size());
                entries.add(new long[]{pass * 1_000_000L + t * 1000L, ni, pi, entries.size()});
            }
        }
        entries.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[3], b[3]));
        orderNode = new int[entries.size()];
        orderPrim = new int[entries.size()];
        for (int k = 0; k < entries.size(); k++) {
            orderNode[k] = (int) entries.get(k)[1];
            orderPrim[k] = (int) entries.get(k)[2];
        }
        nodeM = new Matrix4f[model.nodes.size()];
        nodeN = new Matrix3f[model.nodes.size()];
        for (int i = 0; i < nodeM.length; i++) {
            nodeM[i] = new Matrix4f();
            nodeN[i] = new Matrix3f();
        }
    }

    private int[] quads(int mesh, int pi, GlbModel.Primitive pr) {
        int f = primBase[mesh] + pi;
        if (quadIdx[f] == null) quadIdx[f] = quadIndices(pr.indices);
        return quadIdx[f];
    }

    /** Triangle list -> quad list: pairs (x,y,z)(z,w,x) become quad (x,y,z,w), the rest degenerate quads. Lossless. */
    static int[] quadIndices(int[] idx) {
        int[] out = new int[(idx.length / 3) * 4];
        int o = 0, i = 0;
        while (i + 2 < idx.length) {
            int a = idx[i], b = idx[i + 1], c = idx[i + 2];
            boolean merged = false;
            if (i + 5 < idx.length) {
                int[] t1 = {a, b, c};
                int[] t2 = {idx[i + 3], idx[i + 4], idx[i + 5]};
                outer:
                for (int r1 = 0; r1 < 3; r1++) {
                    int x = t1[r1], y = t1[(r1 + 1) % 3], z = t1[(r1 + 2) % 3];
                    for (int r2 = 0; r2 < 3; r2++) {
                        if (t2[r2] == z && t2[(r2 + 2) % 3] == x) {
                            int w = t2[(r2 + 1) % 3];
                            if (w == y) break outer;
                            out[o++] = x; out[o++] = y; out[o++] = z; out[o++] = w;
                            merged = true;
                            break outer;
                        }
                    }
                }
            }
            if (merged) {
                i += 6;
            } else {
                out[o++] = a; out[o++] = b; out[o++] = c; out[o++] = c;
                i += 3;
            }
        }
        return java.util.Arrays.copyOf(out, o);
    }

    /**
     * Draws the posed model. The pose must have {@link GlbPose#computeGlobals()} called.
     *
     * @param tint global RGBA multiplier (1,1,1,1 = none)
     */
    public void render(GlbPose pose, PoseStack ps, MultiBufferSource buffers, int light, int overlay,
                       float[] tint, MaterialTint matTint, boolean emissiveOn) {
        GlbRenderer lod = lod();
        if (lod != null && (shaderShadowPass() || effectiveDistance(ps.last().pose()) > lodDist)) {
            lod.draw(pose, lodMap, this, ps, buffers, light, overlay, tint, matTint, emissiveOn);
        } else {
            draw(pose, null, this, ps, buffers, light, overlay, tint, matTint, emissiveOn);
        }
    }

    // ---------------------------------------------------------------- distance LOD (r5)
    /** Effective distance (blocks at default FOV) beyond which {@code <name>_lod1.glb} is drawn. */
    public static double LOD_DISTANCE = 26.0;
    private boolean lodChecked;
    private double lodDist = LOD_DISTANCE;
    private GlbRenderer lodR;
    private int[] lodMap;          // lod node -> full node
    private int[] lodMatMap;       // lod material -> full material (by name), -1 = own

    private GlbRenderer lod() {
        if (lodChecked) return lodR;
        lodChecked = true;
        for (GlbModel.Node n : model.nodes) if (n.skin >= 0) return null; // CPU-skinned models: no LOD
        String path = "/assets/" + Bm21Grad.MODID + "/glb/" + model.name + "_lod1.glb";
        try (java.io.InputStream in = GlbRenderer.class.getResourceAsStream(path)) {
            if (in == null) return null;
            GlbModel lm = GlbModel.load(model.name + "_lod1", in);
            int[] map = new int[lm.nodes.size()];
            for (int i = 0; i < map.length; i++) {
                Integer k = model.nodeByName.get(lm.nodes.get(i).name);
                map[i] = k == null ? -1 : k;
                if (map[i] < 0 && lm.nodes.get(i).mesh >= 0) {
                    Bm21Grad.LOGGER.warn("GLB {}: LOD node {} has no match - LOD disabled", model.name, lm.nodes.get(i).name);
                    return null;
                }
            }
            GlbRenderer r = new GlbRenderer(lm, false);
            r.lodMatMap = new int[lm.materials.size()];
            for (int i = 0; i < lm.materials.size(); i++) {
                r.lodMatMap[i] = -1;
                for (int j = 0; j < model.materials.size(); j++) {
                    if (model.materials.get(j).name.equals(lm.materials.get(i).name)) {
                        r.lodMatMap[i] = j;
                        break;
                    }
                }
            }
            lodMap = map;
            lodR = r;
            // heavy models switch earlier (~1/sqrt(tris)): 26 blocks at 22k triangles, >= 12 blocks
            lodDist = Math.max(12.0, Math.min(LOD_DISTANCE, LOD_DISTANCE * Math.sqrt(22000.0 / Math.max(1, model.triangleCount))));
            Bm21Grad.LOGGER.info("GLB {}: LOD1 loaded ({} nodes)", model.name, lm.nodes.size());
        } catch (Exception e) {
            Bm21Grad.LOGGER.warn("GLB {}: LOD not loaded ({})", model.name, e.toString());
        }
        return lodR;
    }

    private static double effectiveDistance(Matrix4f pose) {
        double d = Math.sqrt(pose.m30() * pose.m30() + pose.m31() * pose.m31() + pose.m32() * pose.m32());
        try {
            float m11 = com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix().m11();
            double k = m11 * Math.tan(Math.toRadians(Minecraft.getInstance().options.fov().get()) / 2.0);
            if (k > 1.05) d /= k;
        } catch (Throwable ignored) {
            // no projection (GUI) -> raw distance
        }
        return d;
    }

    // Iris / Oculus shadow pass: LOD is always enough for the shadow map (reflection, no dependency).
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

    /** Draws this model's geometry with the pose of {@code src} (nodes via {@code map}, materials by name). */
    private void draw(GlbPose pose, int[] map, GlbRenderer src, PoseStack ps, MultiBufferSource buffers, int light, int overlay,
                      float[] tint, MaterialTint matTint, boolean emissiveOn) {
        if (orderNode == null) buildOrder(src);
        PoseStack.Pose last = ps.last();
        java.util.Arrays.fill(nodeDone(), false);
        RenderType curType = null;
        VertexConsumer vc = null;
        for (int k = 0; k < orderNode.length; k++) {
            int ni = orderNode[k];
            int wi = map == null ? ni : map[ni];
            if (wi < 0 || !pose.effectivelyVisible(wi)) continue;
            GlbModel.Node node = model.nodes.get(ni);
            GlbModel.Primitive pr = model.meshes.get(node.mesh).primitives.get(orderPrim[k]);
            Matrix4f[] skinMats = node.skin >= 0 && map == null ? skinMatrices(pose, node) : null;
            if (skinMats == null && !done[ni]) {
                done[ni] = true;
                nodeM[ni].set(last.pose()).mul(pose.global[wi]);
                pose.global[wi].normal(tmpN2);
                nodeN[ni].set(last.normal()).mul(tmpN2);
            }
            int matIdx = pr.material;
            GlbModel.Material mat;
            if (lodMatMap != null && matIdx >= 0 && lodMatMap[matIdx] >= 0) {
                matIdx = lodMatMap[matIdx];
                mat = src.model.materials.get(matIdx);
            } else {
                mat = matIdx >= 0 ? model.materials.get(matIdx) : null;
            }
            ResourceLocation tex = src.textureFor(mat);
            RenderType rt = (mat != null && mat.alphaMode == GlbModel.AlphaMode.BLEND)
                    ? GlbRenderTypes.translucent(tex) : GlbRenderTypes.cutout(tex, true);
            if (rt != curType) {
                vc = buffers.getBuffer(rt);
                curType = rt;
            }
            float[] base = mat != null ? mat.baseColor : new float[]{1, 1, 1, 1};
            float[] mt = matTint != null ? matTint.tint(matIdx, mat) : null;
            float cr = base[0] * tint[0], cg = base[1] * tint[1], cb = base[2] * tint[2], ca = base[3] * tint[3];
            if (mt != null) {
                cr = mt[0] * tint[0];
                cg = mt[1] * tint[1];
                cb = mt[2] * tint[2];
            }
            boolean em = mat != null && mat.isEmissive();
            int li = em && emissiveOn ? LightTexture.FULL_BRIGHT : light;
            if (em && !emissiveOn) {
                cr *= 0.45f;
                cg *= 0.45f;
                cb *= 0.45f;
            }
            drawPrimitive(pr, quads(node.mesh, orderPrim[k], pr), vc, nodeM[ni], nodeN[ni], skinMats, cr, cg, cb, ca, li, overlay, last);
        }
    }

    private boolean[] done;

    private boolean[] nodeDone() {
        if (done == null || done.length != model.nodes.size()) done = new boolean[model.nodes.size()];
        return done;
    }

    private void drawPrimitive(GlbModel.Primitive pr, int[] idx, VertexConsumer vc, Matrix4f m, Matrix3f nm, Matrix4f[] skin,
                               float cr, float cg, float cb, float ca, int light, int overlay, PoseStack.Pose last) {
        float[] pos = pr.pos, nrm = pr.nrm, uv = pr.uv, col = pr.col;
        Matrix4f sm = skin != null ? new Matrix4f() : null;
        final float m00 = m.m00(), m01 = m.m01(), m02 = m.m02(), m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
        final float m20 = m.m20(), m21 = m.m21(), m22 = m.m22(), m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
        final float n00 = nm.m00(), n01 = nm.m01(), n02 = nm.m02(), n10 = nm.m10(), n11 = nm.m11(), n12 = nm.m12();
        final float n20 = nm.m20(), n21 = nm.m21(), n22 = nm.m22();
        for (int k = 0; k < idx.length; k++) {
            int v = idx[k];
            float px = pos[v * 3], py = pos[v * 3 + 1], pz = pos[v * 3 + 2];
            float nx = nrm[v * 3], ny = nrm[v * 3 + 1], nz = nrm[v * 3 + 2];
            float ox, oy, oz, tx, ty, tz;
            if (skin != null) {
                sm.zero();
                for (int j = 0; j < 4; j++) {
                    float w = pr.weights[v * 4 + j];
                    if (w <= 0) continue;
                    int ji = pr.joints[v * 4 + j];
                    if (ji < skin.length) addScaled(sm, skin[ji], w);
                }
                tmpP.set(px, py, pz, 1).mul(sm).mul(last.pose());
                tmpV.set(nx, ny, nz);
                sm.transformDirection(tmpV);
                tmpV.mul(last.normal()).normalize();
                ox = tmpP.x; oy = tmpP.y; oz = tmpP.z;
                tx = tmpV.x; ty = tmpV.y; tz = tmpV.z;
            } else {
                ox = m00 * px + m10 * py + m20 * pz + m30;
                oy = m01 * px + m11 * py + m21 * pz + m31;
                oz = m02 * px + m12 * py + m22 * pz + m32;
                tx = n00 * nx + n10 * ny + n20 * nz;
                ty = n01 * nx + n11 * ny + n21 * nz;
                tz = n02 * nx + n12 * ny + n22 * nz;
                float len = tx * tx + ty * ty + tz * tz;
                if (len > 1e-12f) {
                    float inv = (float) (1.0 / Math.sqrt(len));
                    tx *= inv; ty *= inv; tz *= inv;
                }
            }
            float r = cr, g = cg, b = cb, a = ca;
            if (col != null) {
                r *= col[v * 4];
                g *= col[v * 4 + 1];
                b *= col[v * 4 + 2];
                a *= col[v * 4 + 3];
            }
            float u = uv != null ? uv[v * 2] : 0, t = uv != null ? uv[v * 2 + 1] : 0;
            vc.vertex(ox, oy, oz, r, g, b, a, u, t, overlay, light, tx, ty, tz);
        }
    }

    private static void addScaled(Matrix4f acc, Matrix4f m, float w) {
        acc.m00(acc.m00() + m.m00() * w).m01(acc.m01() + m.m01() * w).m02(acc.m02() + m.m02() * w).m03(acc.m03() + m.m03() * w)
                .m10(acc.m10() + m.m10() * w).m11(acc.m11() + m.m11() * w).m12(acc.m12() + m.m12() * w).m13(acc.m13() + m.m13() * w)
                .m20(acc.m20() + m.m20() * w).m21(acc.m21() + m.m21() * w).m22(acc.m22() + m.m22() * w).m23(acc.m23() + m.m23() * w)
                .m30(acc.m30() + m.m30() * w).m31(acc.m31() + m.m31() * w).m32(acc.m32() + m.m32() * w).m33(acc.m33() + m.m33() * w);
    }

    /** jointMatrix = globalJoint * inverseBind (skinned vertices are in model space; the mesh node transform is ignored per spec). */
    private Matrix4f[] skinMatrices(GlbPose pose, GlbModel.Node node) {
        GlbModel.Skin skin = model.skins.get(node.skin);
        Matrix4f[] out = new Matrix4f[skin.joints.length];
        for (int j = 0; j < out.length; j++) out[j] = new Matrix4f(pose.global[skin.joints[j]]).mul(skin.inverseBind[j]);
        return out;
    }
}
