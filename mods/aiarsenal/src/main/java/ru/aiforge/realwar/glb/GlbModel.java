/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  org.joml.Matrix4f
 *  org.joml.Quaternionf
 *  org.joml.Vector3f
 */
package ru.aiforge.realwar.glb;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class GlbModel {
    public final List<Node> nodes = new ArrayList<Node>();
    public final List<Mesh> meshes = new ArrayList<Mesh>();
    public final List<Material> materials = new ArrayList<Material>();
    public final List<byte[]> images = new ArrayList<byte[]>();
    public final List<Animation> animations = new ArrayList<Animation>();
    public final List<Integer> roots = new ArrayList<Integer>();
    private final Map<String, Integer> nodeByName = new HashMap<String, Integer>();

    private GlbModel() {
    }

    public int nodeIndex(String name) {
        Integer i = this.nodeByName.get(name);
        return i == null ? -1 : i;
    }

    public Animation animation(String name) {
        for (Animation a : this.animations) {
            if (!a.name.equals(name)) continue;
            return a;
        }
        return null;
    }

    public int totalVertices() {
        int n = 0;
        for (Mesh m : this.meshes) {
            for (Primitive p : m.prims) {
                n += p.vertexCount();
            }
        }
        return n;
    }

    public int totalTriangles() {
        int n = 0;
        for (Mesh m : this.meshes) {
            for (Primitive p : m.prims) {
                n += p.triangleCount();
            }
        }
        return n;
    }

    public static GlbModel read(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        in.transferTo(bos);
        return GlbModel.parse(bos.toByteArray());
    }

    public static GlbModel parse(byte[] data) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data.length < 20) {
            throw new IOException("GLB \u0441\u043b\u0438\u0448\u043a\u043e\u043c \u043a\u043e\u0440\u043e\u0442\u043a\u0438\u0439");
        }
        if (bb.getInt(0) != 1179937895) {
            throw new IOException("\u041d\u0435\u0442 \u0441\u0438\u0433\u043d\u0430\u0442\u0443\u0440\u044b glTF");
        }
        int version = bb.getInt(4);
        if (version != 2) {
            throw new IOException("\u041f\u043e\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u0435\u0442\u0441\u044f \u0442\u043e\u043b\u044c\u043a\u043e glTF 2.0, \u0430 \u043d\u0435 " + version);
        }
        int total = bb.getInt(8);
        if (total > data.length) {
            throw new IOException("\u0414\u043b\u0438\u043d\u0430 \u0432 \u0437\u0430\u0433\u043e\u043b\u043e\u0432\u043a\u0435 \u0431\u043e\u043b\u044c\u0448\u0435 \u0444\u0430\u0439\u043b\u0430");
        }
        int pos = 12;
        String json = null;
        byte[] bin = null;
        while (pos + 8 <= total) {
            int len = bb.getInt(pos);
            int type = bb.getInt(pos + 4);
            if (len < 0 || (pos += 8) + len > data.length) {
                throw new IOException("\u041f\u043e\u0432\u0440\u0435\u0436\u0434\u0451\u043d\u043d\u044b\u0439 \u0447\u0430\u043d\u043a GLB");
            }
            if (type == 1313821514) {
                json = new String(data, pos, len, StandardCharsets.UTF_8);
            } else if (type == 5130562 && bin == null) {
                bin = new byte[len];
                System.arraycopy(data, pos, bin, 0, len);
            }
            pos += len + 3 & 0xFFFFFFFC;
        }
        if (json == null) {
            throw new IOException("\u041d\u0435\u0442 JSON-\u0447\u0430\u043d\u043a\u0430");
        }
        JsonObject root = (JsonObject)new Gson().fromJson(json, JsonObject.class);
        return new Builder(root, bin).build();
    }

    public static final class Animation {
        public String name = "";
        public float duration;
        public final List<Channel> channels = new ArrayList<Channel>();
    }

    public static final class Mesh {
        public final String name;
        public final List<Primitive> prims = new ArrayList<Primitive>();

        Mesh(String name) {
            this.name = name;
        }
    }

    public static final class Primitive {
        public final float[] pos;
        public final float[] nrm;
        public final float[] uv;
        public final int[] idx;
        public final int material;
        public float[] col;

        Primitive(float[] pos, float[] nrm, float[] uv, int[] idx, int material) {
            this.pos = pos;
            this.nrm = nrm;
            this.uv = uv;
            this.idx = idx;
            this.material = material;
        }

        public int vertexCount() {
            return this.pos.length / 3;
        }

        public int triangleCount() {
            return this.idx.length / 3;
        }
    }

    private static final class Builder {
        final JsonObject root;
        final byte[] bin;
        final GlbModel m = new GlbModel();

        Builder(JsonObject root, byte[] bin) {
            this.root = root;
            this.bin = bin;
        }

        JsonArray arr(String key) {
            return this.root.has(key) ? this.root.getAsJsonArray(key) : new JsonArray();
        }

        GlbModel build() throws IOException {
            JsonObject asset = this.root.getAsJsonObject("asset");
            if (asset == null || !"2.0".equals(asset.get("version").getAsString())) {
                throw new IOException("asset.version != 2.0");
            }
            this.readImages();
            this.readMaterials();
            this.readMeshes();
            this.readNodes();
            this.readAnimations();
            this.readScene();
            return this.m;
        }

        ByteBuffer viewBuffer(int view) throws IOException {
            JsonObject bv = this.arr("bufferViews").get(view).getAsJsonObject();
            int buffer = bv.get("buffer").getAsInt();
            if (buffer != 0 || this.bin == null) {
                throw new IOException("\u041f\u043e\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u0435\u0442\u0441\u044f \u0442\u043e\u043b\u044c\u043a\u043e \u0432\u0441\u0442\u0440\u043e\u0435\u043d\u043d\u044b\u0439 \u0431\u0443\u0444\u0435\u0440 GLB");
            }
            int off = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
            int len = bv.get("byteLength").getAsInt();
            if (off < 0 || len < 0 || off + len > this.bin.length) {
                throw new IOException("bufferView \u0432\u044b\u0445\u043e\u0434\u0438\u0442 \u0437\u0430 \u043f\u0440\u0435\u0434\u0435\u043b\u044b \u0431\u0443\u0444\u0435\u0440\u0430");
            }
            return ByteBuffer.wrap(this.bin, off, len).slice().order(ByteOrder.LITTLE_ENDIAN);
        }

        static int comps(String type) throws IOException {
            return switch (type) {
                case "SCALAR" -> 1;
                case "VEC2" -> 2;
                case "VEC3" -> 3;
                case "VEC4" -> 4;
                case "MAT4" -> 16;
                default -> throw new IOException("\u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u044b\u0439 \u0442\u0438\u043f accessor: " + type);
            };
        }

        static int compSize(int ct) throws IOException {
            return switch (ct) {
                case 5120, 5121 -> 1;
                case 5122, 5123 -> 2;
                case 5125, 5126 -> 4;
                default -> throw new IOException("\u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u044b\u0439 componentType: " + ct);
            };
        }

        float[] readFloats(int accessor) throws IOException {
            JsonObject a = this.arr("accessors").get(accessor).getAsJsonObject();
            if (a.has("sparse")) {
                throw new IOException("sparse accessor \u043d\u0435 \u043f\u043e\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u0435\u0442\u0441\u044f");
            }
            int count = a.get("count").getAsInt();
            int nc = Builder.comps(a.get("type").getAsString());
            int ct = a.get("componentType").getAsInt();
            boolean norm = a.has("normalized") && a.get("normalized").getAsBoolean();
            float[] out = new float[count * nc];
            if (!a.has("bufferView")) {
                return out;
            }
            JsonObject bv = this.arr("bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();
            ByteBuffer b = this.viewBuffer(a.get("bufferView").getAsInt());
            int aoff = a.has("byteOffset") ? a.get("byteOffset").getAsInt() : 0;
            int cs = Builder.compSize(ct);
            int stride = bv.has("byteStride") ? bv.get("byteStride").getAsInt() : cs * nc;
            for (int i = 0; i < count; ++i) {
                for (int c = 0; c < nc; ++c) {
                    int p = aoff + i * stride + c * cs;
                    if (p + cs > b.limit()) {
                        throw new IOException("accessor \u0432\u044b\u0445\u043e\u0434\u0438\u0442 \u0437\u0430 \u043f\u0440\u0435\u0434\u0435\u043b\u044b bufferView");
                    }
                    out[i * nc + c] = switch (ct) {
                        case 5126 -> b.getFloat(p);
                        case 5123 -> {
                            int u = b.getShort(p) & 0xFFFF;
                            yield norm ? (float)u / 65535.0f : (float)u;
                        }
                        case 5121 -> {
                            int u = b.get(p) & 0xFF;
                            yield norm ? (float)u / 255.0f : (float)u;
                        }
                        case 5125 -> (long)b.getInt(p) & 0xFFFFFFFFL;
                        case 5122 -> {
                            short s = b.getShort(p);
                            yield norm ? Math.max((float)s / 32767.0f, -1.0f) : (float)s;
                        }
                        case 5120 -> {
                            short s = b.get(p);
                            yield norm ? Math.max((float)s / 127.0f, -1.0f) : (float)s;
                        }
                        default -> throw new IOException("componentType " + ct);
                    };
                }
            }
            return out;
        }

        int[] readIndices(int accessor) throws IOException {
            JsonObject a = this.arr("accessors").get(accessor).getAsJsonObject();
            int count = a.get("count").getAsInt();
            int ct = a.get("componentType").getAsInt();
            int[] out = new int[count];
            JsonObject bv = this.arr("bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();
            ByteBuffer b = this.viewBuffer(a.get("bufferView").getAsInt());
            int aoff = a.has("byteOffset") ? a.get("byteOffset").getAsInt() : 0;
            int cs = Builder.compSize(ct);
            int stride = bv.has("byteStride") ? bv.get("byteStride").getAsInt() : cs;
            for (int i = 0; i < count; ++i) {
                int p = aoff + i * stride;
                if (p + cs > b.limit()) {
                    throw new IOException("\u0438\u043d\u0434\u0435\u043a\u0441\u044b \u0432\u044b\u0445\u043e\u0434\u044f\u0442 \u0437\u0430 \u043f\u0440\u0435\u0434\u0435\u043b\u044b bufferView");
                }
                out[i] = switch (ct) {
                    case 5121 -> b.get(p) & 0xFF;
                    case 5123 -> b.getShort(p) & 0xFFFF;
                    case 5125 -> b.getInt(p);
                    default -> throw new IOException("\u041d\u0435\u0434\u043e\u043f\u0443\u0441\u0442\u0438\u043c\u044b\u0439 \u0442\u0438\u043f \u0438\u043d\u0434\u0435\u043a\u0441\u043e\u0432 " + ct);
                };
            }
            return out;
        }

        void readImages() throws IOException {
            for (JsonElement e : this.arr("images")) {
                JsonObject im = e.getAsJsonObject();
                if (im.has("bufferView")) {
                    ByteBuffer b = this.viewBuffer(im.get("bufferView").getAsInt());
                    byte[] bytes = new byte[b.remaining()];
                    b.get(bytes);
                    this.m.images.add(bytes);
                    continue;
                }
                if (im.has("uri") && im.get("uri").getAsString().startsWith("data:")) {
                    String uri = im.get("uri").getAsString();
                    this.m.images.add(Base64.getDecoder().decode(uri.substring(uri.indexOf(44) + 1)));
                    continue;
                }
                throw new IOException("\u0412\u043d\u0435\u0448\u043d\u0438\u0435 \u0442\u0435\u043a\u0441\u0442\u0443\u0440\u044b \u043d\u0435 \u043f\u043e\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u044e\u0442\u0441\u044f \u2014 \u0432\u0441\u0442\u0440\u0430\u0438\u0432\u0430\u0439\u0442\u0435 PNG \u0432 GLB");
            }
        }

        void readMaterials() {
            for (JsonElement e : this.arr("materials")) {
                JsonObject pbr;
                JsonObject mo = e.getAsJsonObject();
                Material mat = new Material();
                if (mo.has("name")) {
                    mat.name = mo.get("name").getAsString();
                }
                mat.doubleSided = mo.has("doubleSided") && mo.get("doubleSided").getAsBoolean();
                mat.alphaTest = mo.has("alphaMode") && "MASK".equals(mo.get("alphaMode").getAsString());
                mat.blend = mo.has("alphaMode") && "BLEND".equals(mo.get("alphaMode").getAsString());
                if (mo.has("emissiveFactor")) {
                    JsonArray ef = mo.getAsJsonArray("emissiveFactor");
                    float es = 0f;
                    for (int i = 0; i < ef.size(); ++i) es += ef.get(i).getAsFloat();
                    mat.emissive = es > 0.05f;
                }
                JsonObject jsonObject = pbr = mo.has("pbrMetallicRoughness") ? mo.getAsJsonObject("pbrMetallicRoughness") : null;
                if (pbr != null) {
                    if (pbr.has("baseColorFactor")) {
                        JsonArray c = pbr.getAsJsonArray("baseColorFactor");
                        for (int i = 0; i < 4; ++i) {
                            mat.color[i] = c.get(i).getAsFloat();
                        }
                    }
                    if (pbr.has("baseColorTexture")) {
                        int tex = pbr.getAsJsonObject("baseColorTexture").get("index").getAsInt();
                        JsonObject t = this.arr("textures").get(tex).getAsJsonObject();
                        if (t.has("source")) {
                            mat.image = t.get("source").getAsInt();
                        }
                    }
                }
                this.m.materials.add(mat);
            }
        }

        void readMeshes() throws IOException {
            for (JsonElement e : this.arr("meshes")) {
                JsonObject mo = e.getAsJsonObject();
                Mesh mesh = new Mesh(mo.has("name") ? mo.get("name").getAsString() : "");
                for (JsonElement pe : mo.getAsJsonArray("primitives")) {
                    float[] c;
                    int[] idx;
                    float[] uv;
                    int mode;
                    JsonObject po = pe.getAsJsonObject();
                    int n = mode = po.has("mode") ? po.get("mode").getAsInt() : 4;
                    if (mode != 4) {
                        throw new IOException("\u041f\u043e\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u044e\u0442\u0441\u044f \u0442\u043e\u043b\u044c\u043a\u043e \u0442\u0440\u0435\u0443\u0433\u043e\u043b\u044c\u043d\u0438\u043a\u0438 (mode=4)");
                    }
                    JsonObject at = po.getAsJsonObject("attributes");
                    float[] pos = this.readFloats(at.get("POSITION").getAsInt());
                    int vc = pos.length / 3;
                    float[] fArray = uv = at.has("TEXCOORD_0") ? this.readFloats(at.get("TEXCOORD_0").getAsInt()) : new float[vc * 2];
                    if (po.has("indices")) {
                        idx = this.readIndices(po.get("indices").getAsInt());
                    } else {
                        idx = new int[vc];
                        for (int i = 0; i < vc; ++i) {
                            idx[i] = i;
                        }
                    }
                    if (idx.length % 3 != 0) {
                        throw new IOException("\u0427\u0438\u0441\u043b\u043e \u0438\u043d\u0434\u0435\u043a\u0441\u043e\u0432 \u043d\u0435 \u043a\u0440\u0430\u0442\u043d\u043e 3");
                    }
                    for (int i : idx) {
                        if (i >= 0 && i < vc) continue;
                        throw new IOException("\u0418\u043d\u0434\u0435\u043a\u0441 \u0432\u0435\u0440\u0448\u0438\u043d\u044b \u0432\u043d\u0435 \u0434\u0438\u0430\u043f\u0430\u0437\u043e\u043d\u0430");
                    }
                    float[] nrm = at.has("NORMAL") ? this.readFloats(at.get("NORMAL").getAsInt()) : Builder.computeNormals(pos, idx);
                    int material = po.has("material") ? po.get("material").getAsInt() : -1;
                    Primitive prim = new Primitive(pos, nrm, uv, idx, material);
                    if (at.has("COLOR_0")) {
                        c = this.readFloats(at.get("COLOR_0").getAsInt());
                        if (c.length == vc * 3) {
                            prim.col = c;
                        } else if (c.length == vc * 4) {
                            // Blender exports RGBA (unsigned short, normalized): keep RGB
                            float[] c3 = new float[vc * 3];
                            for (int q = 0; q < vc; ++q) {
                                c3[q * 3] = c[q * 4];
                                c3[q * 3 + 1] = c[q * 4 + 1];
                                c3[q * 3 + 2] = c[q * 4 + 2];
                            }
                            prim.col = c3;
                        }
                    }
                    mesh.prims.add(prim);
                }
                this.m.meshes.add(mesh);
            }
        }

        static float[] computeNormals(float[] pos, int[] idx) {
            int i;
            float[] n = new float[pos.length];
            for (i = 0; i < idx.length; i += 3) {
                int a = idx[i] * 3;
                int b = idx[i + 1] * 3;
                int c = idx[i + 2] * 3;
                float ux = pos[b] - pos[a];
                float uy = pos[b + 1] - pos[a + 1];
                float uz = pos[b + 2] - pos[a + 2];
                float vx = pos[c] - pos[a];
                float vy = pos[c + 1] - pos[a + 1];
                float vz = pos[c + 2] - pos[a + 2];
                float nx = uy * vz - uz * vy;
                float ny = uz * vx - ux * vz;
                float nz = ux * vy - uy * vx;
                int[] nArray = new int[]{a, b, c};
                int n2 = nArray.length;
                for (int j = 0; j < n2; ++j) {
                    int k;
                    int n3 = k = nArray[j];
                    n[n3] = n[n3] + nx;
                    int n4 = k + 1;
                    n[n4] = n[n4] + ny;
                    int n5 = k + 2;
                    n[n5] = n[n5] + nz;
                }
            }
            for (i = 0; i < n.length; i += 3) {
                float l = (float)Math.sqrt(n[i] * n[i] + n[i + 1] * n[i + 1] + n[i + 2] * n[i + 2]);
                if (l > 1.0E-9f) {
                    int n6 = i;
                    n[n6] = n[n6] / l;
                    int n7 = i + 1;
                    n[n7] = n[n7] / l;
                    int n8 = i + 2;
                    n[n8] = n[n8] / l;
                    continue;
                }
                n[i + 1] = 1.0f;
            }
            return n;
        }

        void readNodes() {
            int i;
            JsonArray na = this.arr("nodes");
            for (i = 0; i < na.size(); ++i) {
                JsonObject no = na.get(i).getAsJsonObject();
                Node n = new Node();
                JsonArray a;
                if (no.has("name")) {
                    n.name = no.get("name").getAsString();
                }
                if (no.has("mesh")) {
                    n.mesh = no.get("mesh").getAsInt();
                }
                if (no.has("matrix")) {
                    a = no.getAsJsonArray("matrix");
                    float[] f = new float[16];
                    for (int k = 0; k < 16; ++k) {
                        f[k] = a.get(k).getAsFloat();
                    }
                    Matrix4f mat = new Matrix4f().set(f);
                    mat.getTranslation(n.t);
                    mat.getUnnormalizedRotation(n.r);
                    mat.getScale(n.s);
                } else {
                    if (no.has("translation")) {
                        a = no.getAsJsonArray("translation");
                        n.t.set(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
                    }
                    if (no.has("rotation")) {
                        a = no.getAsJsonArray("rotation");
                        n.r.set(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsFloat()).normalize();
                    }
                    if (no.has("scale")) {
                        a = no.getAsJsonArray("scale");
                        n.s.set(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
                    }
                }
                if (no.has("children")) {
                    for (JsonElement c : no.getAsJsonArray("children")) {
                        n.children.add(c.getAsInt());
                    }
                }
                this.m.nodes.add(n);
                if (n.name.isEmpty()) continue;
                this.m.nodeByName.putIfAbsent(n.name, i);
            }
            for (i = 0; i < this.m.nodes.size(); ++i) {
                for (int c : this.m.nodes.get((int)i).children) {
                    this.m.nodes.get((int)c).parent = i;
                }
            }
        }

        void readAnimations() throws IOException {
            for (JsonElement e : this.arr("animations")) {
                JsonObject ao = e.getAsJsonObject();
                Animation anim = new Animation();
                if (ao.has("name")) {
                    anim.name = ao.get("name").getAsString();
                }
                JsonArray samplers = ao.getAsJsonArray("samplers");
                for (JsonElement ce : ao.getAsJsonArray("channels")) {
                    JsonObject co = ce.getAsJsonObject();
                    JsonObject target = co.getAsJsonObject("target");
                    if (!target.has("node")) continue;
                    JsonObject so = samplers.get(co.get("sampler").getAsInt()).getAsJsonObject();
                    Channel ch = new Channel();
                    ch.node = target.get("node").getAsInt();
                    ch.path = target.get("path").getAsString();
                    String interp = so.has("interpolation") ? so.get("interpolation").getAsString() : "LINEAR";
                    ch.step = interp.equals("STEP");
                    ch.times = this.readFloats(so.get("input").getAsInt());
                    float[] vals = this.readFloats(so.get("output").getAsInt());
                    if (interp.equals("CUBICSPLINE")) {
                        int per = ch.path.equals("rotation") ? 4 : 3;
                        float[] v = new float[ch.times.length * per];
                        for (int i = 0; i < ch.times.length; ++i) {
                            System.arraycopy(vals, (i * 3 + 1) * per, v, i * per, per);
                        }
                        vals = v;
                    }
                    ch.values = vals;
                    if (ch.times.length > 0) {
                        anim.duration = Math.max(anim.duration, ch.times[ch.times.length - 1]);
                    }
                    anim.channels.add(ch);
                }
                this.m.animations.add(anim);
            }
        }

        void readScene() {
            JsonArray scenes = this.arr("scenes");
            if (scenes.size() > 0) {
                int s = this.root.has("scene") ? this.root.get("scene").getAsInt() : 0;
                for (JsonElement n : scenes.get(s).getAsJsonObject().getAsJsonArray("nodes")) {
                    this.m.roots.add(n.getAsInt());
                }
            } else {
                for (int i = 0; i < this.m.nodes.size(); ++i) {
                    if (this.m.nodes.get((int)i).parent >= 0) continue;
                    this.m.roots.add(i);
                }
            }
        }
    }

    public static final class Channel {
        public int node;
        public String path;
        public boolean step;
        public float[] times;
        public float[] values;
    }

    public static final class Material {
        public String name = "";
        public int image = -1;
        public final float[] color = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        public boolean doubleSided;
        public boolean alphaTest;
        public boolean blend;
        /** r12: emissiveFactor > 0 -> drawn full-bright (screens, lamps, afterburner, reticles). */
        public boolean emissive;
    }

    public static final class Node {
        public String name = "";
        public int parent = -1;
        public int mesh = -1;
        public final List<Integer> children = new ArrayList<Integer>();
        public final Vector3f t = new Vector3f();
        public final Quaternionf r = new Quaternionf();
        public final Vector3f s = new Vector3f(1.0f, 1.0f, 1.0f);
    }
}

