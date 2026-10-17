package ru.aiforge.tubombers.glb;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Side-independent glTF 2.0 binary (.glb) model: node hierarchy, meshes (all primitives, indexed or not),
 * PBR materials, embedded images, skins and keyframe animations (LINEAR / STEP / CUBICSPLINE,
 * translation / rotation / scale). No client classes here - usable on a dedicated server.
 */
public final class GlbModel {
    public static final int MAGIC = 0x46546C67; // "glTF"

    public final String name;
    public final List<Node> nodes = new ArrayList<>();
    public final List<Mesh> meshes = new ArrayList<>();
    public final List<Material> materials = new ArrayList<>();
    public final List<Image> images = new ArrayList<>();
    /** texture index -> image index */
    public final List<Integer> textures = new ArrayList<>();
    public final List<Skin> skins = new ArrayList<>();
    public final List<Animation> animations = new ArrayList<>();
    public final Map<String, Integer> nodeByName = new HashMap<>();
    public final Map<String, Animation> animByName = new HashMap<>();
    public int[] roots = new int[0];
    public int triangleCount;

    // ------------------------------------------------------------------ data classes
    public static final class Node {
        public String name;
        public int index;
        public int parent = -1;
        public int[] children = new int[0];
        public int mesh = -1;
        public int skin = -1;
        public final Vector3f t = new Vector3f();
        public final Quaternionf r = new Quaternionf();
        public final Vector3f s = new Vector3f(1, 1, 1);
        public Matrix4f matrix; // if the node uses a raw matrix instead of TRS
    }

    public static final class Mesh {
        public String name;
        public final List<Primitive> primitives = new ArrayList<>();
    }

    public static final class Primitive {
        public int material = -1;
        public int vertexCount;
        public float[] pos;      // xyz
        public float[] nrm;      // xyz (may be null -> flat normals generated)
        public float[] uv;       // uv (may be null)
        public float[] col;      // rgba (may be null)
        public int[] joints;     // 4 per vertex (skinned)
        public float[] weights;  // 4 per vertex
        public int[] indices;    // triangles
    }

    public enum AlphaMode {OPAQUE, MASK, BLEND}

    public static final class Material {
        public String name = "";
        public final float[] baseColor = {1, 1, 1, 1};
        public int baseColorTexture = -1;
        public float[] emissive = {0, 0, 0};
        public AlphaMode alphaMode = AlphaMode.OPAQUE;
        public float alphaCutoff = 0.5f;
        public boolean doubleSided;
        public float metallic = 1, roughness = 1;

        public boolean isEmissive() {
            return emissive[0] + emissive[1] + emissive[2] > 0.05f;
        }
    }

    public static final class Image {
        public String name;
        public String mime;
        public byte[] data;
    }

    public static final class Skin {
        public String name;
        public int[] joints;
        public Matrix4f[] inverseBind;
        public int skeleton = -1;
    }

    public enum Path {TRANSLATION, ROTATION, SCALE, WEIGHTS}

    public enum Interp {LINEAR, STEP, CUBICSPLINE}

    public static final class Channel {
        public int node;
        public Path path;
        public float[] times;
        public float[] values; // flat; CUBICSPLINE stores in/value/out triplets
        public Interp interp;
        public int comps;
    }

    public static final class Animation {
        public String name;
        public final List<Channel> channels = new ArrayList<>();
        public float duration;
    }

    private GlbModel(String name) {
        this.name = name;
    }

    // ------------------------------------------------------------------ loading
    public static GlbModel load(String name, InputStream in) throws IOException {
        byte[] all = readAll(in);
        ByteBuffer bb = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        if (bb.getInt(0) != MAGIC) throw new IOException("not a GLB: " + name);
        int version = bb.getInt(4);
        if (version != 2) throw new IOException("unsupported glTF version " + version);
        int off = 12;
        String json = null;
        ByteBuffer bin = null;
        while (off + 8 <= all.length) {
            int len = bb.getInt(off);
            int type = bb.getInt(off + 4);
            if (type == 0x4E4F534A) { // JSON
                json = new String(all, off + 8, len, StandardCharsets.UTF_8);
            } else if (type == 0x004E4942) { // BIN
                bin = ByteBuffer.wrap(all, off + 8, len).slice().order(ByteOrder.LITTLE_ENDIAN);
            }
            off += 8 + len;
        }
        if (json == null) throw new IOException("GLB without JSON chunk: " + name);
        GlbModel m = new GlbModel(name);
        m.parse(JsonParser.parseString(json).getAsJsonObject(), bin);
        return m;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 20);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private JsonObject root;
    private ByteBuffer bin;

    private void parse(JsonObject j, ByteBuffer bin) throws IOException {
        this.root = j;
        this.bin = bin;
        // images / textures
        for (JsonElement e : arr(j, "images")) {
            JsonObject o = e.getAsJsonObject();
            Image im = new Image();
            im.name = str(o, "name", "image" + images.size());
            im.mime = str(o, "mimeType", "image/png");
            if (o.has("bufferView")) {
                im.data = bufferViewBytes(o.get("bufferView").getAsInt());
            } else if (o.has("uri") && o.get("uri").getAsString().startsWith("data:")) {
                String uri = o.get("uri").getAsString();
                im.data = java.util.Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
            }
            images.add(im);
        }
        for (JsonElement e : arr(j, "textures")) {
            JsonObject o = e.getAsJsonObject();
            textures.add(o.has("source") ? o.get("source").getAsInt() : -1);
        }
        // materials
        for (JsonElement e : arr(j, "materials")) {
            JsonObject o = e.getAsJsonObject();
            Material mt = new Material();
            mt.name = str(o, "name", "material" + materials.size());
            if (o.has("pbrMetallicRoughness")) {
                JsonObject p = o.getAsJsonObject("pbrMetallicRoughness");
                if (p.has("baseColorFactor")) {
                    JsonArray a = p.getAsJsonArray("baseColorFactor");
                    for (int i = 0; i < 4; i++) mt.baseColor[i] = a.get(i).getAsFloat();
                }
                if (p.has("baseColorTexture")) mt.baseColorTexture = p.getAsJsonObject("baseColorTexture").get("index").getAsInt();
                if (p.has("metallicFactor")) mt.metallic = p.get("metallicFactor").getAsFloat();
                if (p.has("roughnessFactor")) mt.roughness = p.get("roughnessFactor").getAsFloat();
            }
            if (o.has("emissiveFactor")) {
                JsonArray a = o.getAsJsonArray("emissiveFactor");
                mt.emissive = new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
            }
            if (o.has("alphaMode")) mt.alphaMode = AlphaMode.valueOf(o.get("alphaMode").getAsString());
            if (o.has("alphaCutoff")) mt.alphaCutoff = o.get("alphaCutoff").getAsFloat();
            mt.doubleSided = o.has("doubleSided") && o.get("doubleSided").getAsBoolean();
            materials.add(mt);
        }
        // meshes
        for (JsonElement e : arr(j, "meshes")) {
            JsonObject o = e.getAsJsonObject();
            Mesh mesh = new Mesh();
            mesh.name = str(o, "name", "mesh" + meshes.size());
            for (JsonElement pe : arr(o, "primitives")) {
                JsonObject po = pe.getAsJsonObject();
                int mode = po.has("mode") ? po.get("mode").getAsInt() : 4;
                if (mode != 4 && mode != 5 && mode != 6) continue; // only triangle modes are renderable
                JsonObject at = po.getAsJsonObject("attributes");
                if (!at.has("POSITION")) continue;
                Primitive pr = new Primitive();
                pr.material = po.has("material") ? po.get("material").getAsInt() : -1;
                pr.pos = readFloats(at.get("POSITION").getAsInt());
                pr.vertexCount = pr.pos.length / 3;
                if (at.has("NORMAL")) pr.nrm = readFloats(at.get("NORMAL").getAsInt());
                if (at.has("TEXCOORD_0")) pr.uv = readFloats(at.get("TEXCOORD_0").getAsInt());
                if (at.has("COLOR_0")) {
                    int acc = at.get("COLOR_0").getAsInt();
                    float[] c = readFloats(acc);
                    if (numComps(acc) == 3) { // expand RGB -> RGBA
                        float[] c4 = new float[pr.vertexCount * 4];
                        for (int i = 0; i < pr.vertexCount; i++) {
                            c4[i * 4] = c[i * 3];
                            c4[i * 4 + 1] = c[i * 3 + 1];
                            c4[i * 4 + 2] = c[i * 3 + 2];
                            c4[i * 4 + 3] = 1f;
                        }
                        c = c4;
                    }
                    pr.col = c;
                }
                if (at.has("JOINTS_0") && at.has("WEIGHTS_0")) {
                    float[] jf = readFloatsRaw(at.get("JOINTS_0").getAsInt());
                    pr.joints = new int[jf.length];
                    for (int i = 0; i < jf.length; i++) pr.joints[i] = (int) jf[i];
                    pr.weights = readFloats(at.get("WEIGHTS_0").getAsInt());
                }
                int[] idx;
                if (po.has("indices")) {
                    float[] f = readFloatsRaw(po.get("indices").getAsInt());
                    idx = new int[f.length];
                    for (int i = 0; i < f.length; i++) idx[i] = (int) f[i];
                } else {
                    idx = new int[pr.vertexCount];
                    for (int i = 0; i < idx.length; i++) idx[i] = i;
                }
                pr.indices = triangulate(idx, mode);
                if (pr.nrm == null) pr.nrm = flatNormals(pr.pos, pr.indices, pr.vertexCount);
                triangleCount += pr.indices.length / 3;
                mesh.primitives.add(pr);
            }
            meshes.add(mesh);
        }
        // nodes
        JsonArray jn = arr(j, "nodes");
        for (int i = 0; i < jn.size(); i++) {
            JsonObject o = jn.get(i).getAsJsonObject();
            Node n = new Node();
            n.index = i;
            n.name = str(o, "name", "node" + i);
            if (o.has("mesh")) n.mesh = o.get("mesh").getAsInt();
            if (o.has("skin")) n.skin = o.get("skin").getAsInt();
            if (o.has("children")) {
                JsonArray c = o.getAsJsonArray("children");
                n.children = new int[c.size()];
                for (int k = 0; k < c.size(); k++) n.children[k] = c.get(k).getAsInt();
            }
            if (o.has("matrix")) {
                JsonArray a = o.getAsJsonArray("matrix");
                float[] f = new float[16];
                for (int k = 0; k < 16; k++) f[k] = a.get(k).getAsFloat();
                n.matrix = new Matrix4f().set(f); // column-major, same as glTF
                n.matrix.getTranslation(n.t);
                n.matrix.getUnnormalizedRotation(n.r);
                n.matrix.getScale(n.s);
            }
            if (o.has("translation")) {
                JsonArray a = o.getAsJsonArray("translation");
                n.t.set(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
            }
            if (o.has("rotation")) {
                JsonArray a = o.getAsJsonArray("rotation");
                n.r.set(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsFloat());
            }
            if (o.has("scale")) {
                JsonArray a = o.getAsJsonArray("scale");
                n.s.set(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
            }
            nodes.add(n);
            nodeByName.putIfAbsent(n.name, i);
        }
        for (Node n : nodes) for (int c : n.children) nodes.get(c).parent = n.index;
        // roots from the default scene (fallback: parentless nodes)
        List<Integer> rl = new ArrayList<>();
        if (j.has("scenes")) {
            int sc = j.has("scene") ? j.get("scene").getAsInt() : 0;
            for (JsonElement e : arr(j.getAsJsonArray("scenes").get(sc).getAsJsonObject(), "nodes")) rl.add(e.getAsInt());
        }
        if (rl.isEmpty()) for (Node n : nodes) if (n.parent < 0) rl.add(n.index);
        roots = rl.stream().mapToInt(Integer::intValue).toArray();
        // skins
        for (JsonElement e : arr(j, "skins")) {
            JsonObject o = e.getAsJsonObject();
            Skin s = new Skin();
            s.name = str(o, "name", "skin" + skins.size());
            JsonArray ja = o.getAsJsonArray("joints");
            s.joints = new int[ja.size()];
            for (int k = 0; k < ja.size(); k++) s.joints[k] = ja.get(k).getAsInt();
            s.inverseBind = new Matrix4f[s.joints.length];
            if (o.has("inverseBindMatrices")) {
                float[] f = readFloats(o.get("inverseBindMatrices").getAsInt());
                for (int k = 0; k < s.joints.length; k++) {
                    float[] m16 = new float[16];
                    System.arraycopy(f, k * 16, m16, 0, 16);
                    s.inverseBind[k] = new Matrix4f().set(m16);
                }
            } else for (int k = 0; k < s.joints.length; k++) s.inverseBind[k] = new Matrix4f();
            if (o.has("skeleton")) s.skeleton = o.get("skeleton").getAsInt();
            skins.add(s);
        }
        // animations
        for (JsonElement e : arr(j, "animations")) {
            JsonObject o = e.getAsJsonObject();
            Animation an = new Animation();
            an.name = str(o, "name", "anim" + animations.size());
            JsonArray samplers = o.getAsJsonArray("samplers");
            for (JsonElement ce : arr(o, "channels")) {
                JsonObject co = ce.getAsJsonObject();
                JsonObject tg = co.getAsJsonObject("target");
                if (!tg.has("node")) continue;
                String p = tg.get("path").getAsString();
                Channel ch = new Channel();
                ch.node = tg.get("node").getAsInt();
                ch.path = switch (p) {
                    case "translation" -> Path.TRANSLATION;
                    case "rotation" -> Path.ROTATION;
                    case "scale" -> Path.SCALE;
                    default -> Path.WEIGHTS;
                };
                if (ch.path == Path.WEIGHTS) continue; // morph targets are not supported by the renderer
                JsonObject so = samplers.get(co.get("sampler").getAsInt()).getAsJsonObject();
                ch.times = readFloats(so.get("input").getAsInt());
                ch.values = readFloats(so.get("output").getAsInt());
                ch.interp = Interp.valueOf(str(so, "interpolation", "LINEAR"));
                ch.comps = ch.path == Path.ROTATION ? 4 : 3;
                if (ch.times.length > 0) an.duration = Math.max(an.duration, ch.times[ch.times.length - 1]);
                an.channels.add(ch);
            }
            animations.add(an);
            animByName.putIfAbsent(an.name, an);
        }
        this.root = null;
        this.bin = null;
    }

    // ------------------------------------------------------------------ accessors
    private static final int BYTE = 5120, UBYTE = 5121, SHORT = 5122, USHORT = 5123, UINT = 5125, FLOAT = 5126;

    private int numComps(int accessor) {
        return comps(root.getAsJsonArray("accessors").get(accessor).getAsJsonObject().get("type").getAsString());
    }

    private static int comps(String type) {
        return switch (type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4", "MAT2" -> 4;
            case "MAT3" -> 9;
            case "MAT4" -> 16;
            default -> 1;
        };
    }

    private static int compSize(int ct) {
        return switch (ct) {
            case BYTE, UBYTE -> 1;
            case SHORT, USHORT -> 2;
            default -> 4;
        };
    }

    /** Reads an accessor as floats applying 'normalized' conversion. */
    private float[] readFloats(int accessor) throws IOException {
        return readAccessor(accessor, true);
    }

    /** Reads integer data (indices / joints) without normalisation. */
    private float[] readFloatsRaw(int accessor) throws IOException {
        return readAccessor(accessor, false);
    }

    private float[] readAccessor(int accessor, boolean allowNorm) throws IOException {
        JsonObject a = root.getAsJsonArray("accessors").get(accessor).getAsJsonObject();
        int count = a.get("count").getAsInt();
        int ct = a.get("componentType").getAsInt();
        int nc = comps(a.get("type").getAsString());
        boolean norm = allowNorm && a.has("normalized") && a.get("normalized").getAsBoolean();
        float[] out = new float[count * nc];
        if (a.has("bufferView")) {
            JsonObject bv = root.getAsJsonArray("bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();
            int base = (bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0) + (a.has("byteOffset") ? a.get("byteOffset").getAsInt() : 0);
            int elem = compSize(ct) * nc;
            int stride = bv.has("byteStride") ? bv.get("byteStride").getAsInt() : elem;
            if (bin == null) throw new IOException("accessor references missing BIN chunk");
            for (int i = 0; i < count; i++) {
                int p = base + i * stride;
                for (int c = 0; c < nc; c++) out[i * nc + c] = readComp(p + c * compSize(ct), ct, norm);
            }
        }
        if (a.has("sparse")) {
            JsonObject sp = a.getAsJsonObject("sparse");
            int sc = sp.get("count").getAsInt();
            JsonObject si = sp.getAsJsonObject("indices"), sv = sp.getAsJsonObject("values");
            JsonObject ibv = root.getAsJsonArray("bufferViews").get(si.get("bufferView").getAsInt()).getAsJsonObject();
            JsonObject vbv = root.getAsJsonArray("bufferViews").get(sv.get("bufferView").getAsInt()).getAsJsonObject();
            int ict = si.get("componentType").getAsInt();
            int ib = (ibv.has("byteOffset") ? ibv.get("byteOffset").getAsInt() : 0) + (si.has("byteOffset") ? si.get("byteOffset").getAsInt() : 0);
            int vb = (vbv.has("byteOffset") ? vbv.get("byteOffset").getAsInt() : 0) + (sv.has("byteOffset") ? sv.get("byteOffset").getAsInt() : 0);
            for (int k = 0; k < sc; k++) {
                int target = (int) readComp(ib + k * compSize(ict), ict, false);
                for (int c = 0; c < nc; c++) out[target * nc + c] = readComp(vb + (k * nc + c) * compSize(ct), ct, norm);
            }
        }
        return out;
    }

    private float readComp(int p, int ct, boolean norm) {
        switch (ct) {
            case FLOAT:
                return bin.getFloat(p);
            case UBYTE: {
                int v = bin.get(p) & 0xFF;
                return norm ? v / 255f : v;
            }
            case BYTE: {
                int v = bin.get(p);
                return norm ? Math.max(v / 127f, -1f) : v;
            }
            case USHORT: {
                int v = bin.getShort(p) & 0xFFFF;
                return norm ? v / 65535f : v;
            }
            case SHORT: {
                int v = bin.getShort(p);
                return norm ? Math.max(v / 32767f, -1f) : v;
            }
            case UINT:
                return (float) (bin.getInt(p) & 0xFFFFFFFFL);
            default:
                return 0;
        }
    }

    private byte[] bufferViewBytes(int view) {
        JsonObject bv = root.getAsJsonArray("bufferViews").get(view).getAsJsonObject();
        int off = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
        int len = bv.get("byteLength").getAsInt();
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) out[i] = bin.get(off + i);
        return out;
    }

    private static int[] triangulate(int[] idx, int mode) {
        if (mode == 4) return idx;
        List<Integer> t = new ArrayList<>();
        if (mode == 5) { // strip
            for (int i = 0; i + 2 < idx.length; i++) {
                if ((i & 1) == 0) Collections.addAll(t, idx[i], idx[i + 1], idx[i + 2]);
                else Collections.addAll(t, idx[i + 1], idx[i], idx[i + 2]);
            }
        } else { // fan
            for (int i = 1; i + 1 < idx.length; i++) Collections.addAll(t, idx[0], idx[i], idx[i + 1]);
        }
        return t.stream().mapToInt(Integer::intValue).toArray();
    }

    private static float[] flatNormals(float[] pos, int[] idx, int vc) {
        float[] n = new float[vc * 3];
        Vector3f a = new Vector3f(), b = new Vector3f(), c = new Vector3f();
        for (int i = 0; i + 2 < idx.length; i += 3) {
            int i0 = idx[i], i1 = idx[i + 1], i2 = idx[i + 2];
            a.set(pos[i1 * 3] - pos[i0 * 3], pos[i1 * 3 + 1] - pos[i0 * 3 + 1], pos[i1 * 3 + 2] - pos[i0 * 3 + 2]);
            b.set(pos[i2 * 3] - pos[i0 * 3], pos[i2 * 3 + 1] - pos[i0 * 3 + 1], pos[i2 * 3 + 2] - pos[i0 * 3 + 2]);
            a.cross(b, c);
            for (int v : new int[]{i0, i1, i2}) {
                n[v * 3] += c.x;
                n[v * 3 + 1] += c.y;
                n[v * 3 + 2] += c.z;
            }
        }
        for (int v = 0; v < vc; v++) {
            float l = (float) Math.sqrt(n[v * 3] * n[v * 3] + n[v * 3 + 1] * n[v * 3 + 1] + n[v * 3 + 2] * n[v * 3 + 2]);
            if (l > 1e-8f) {
                n[v * 3] /= l;
                n[v * 3 + 1] /= l;
                n[v * 3 + 2] /= l;
            } else n[v * 3 + 1] = 1;
        }
        return n;
    }

    private static JsonArray arr(JsonObject o, String key) {
        return o != null && o.has(key) ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) ? o.get(key).getAsString() : def;
    }

    // ------------------------------------------------------------------ helpers
    public int node(String name) {
        Integer i = nodeByName.get(name);
        return i == null ? -1 : i;
    }

    public Animation anim(String name) {
        return animByName.get(name);
    }

    @Override
    public String toString() {
        return "GlbModel[" + name + ": nodes=" + nodes.size() + ", meshes=" + meshes.size() + ", tris=" + triangleCount
                + ", materials=" + materials.size() + ", images=" + images.size() + ", skins=" + skins.size()
                + ", animations=" + animations.size() + "]";
    }
}
