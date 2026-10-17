package ru.aiforge.realwar.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;
import org.joml.Vector4f;
import ru.aiforge.realwar.glb.GlbModel;
import ru.aiforge.realwar.glb.Pose;

/**
 * Per-context placement of the shoulder launchers and their rounds, read from assets/aiarsenal/handheld_view.json
 * (resource packs can override it; reloaded with F3+T). Entry: [tx, ty, tz, yaw, pitch, roll, scale, centre(0/1)].
 * Keys: "<item>.<context>", "<item>.aim.<context>" (while aiming), "default.<context>".
 */
public final class HandheldView {
    private static Map<String, float[]> table;
    private static final Map<ResourceLocation, float[]> BOUNDS = new HashMap<>();
    private static final float[] FALLBACK = {0, 0, 0, 90, 0, 35, 0.6f, 1};

    private HandheldView() {
    }

    public static void clear() {
        table = null;
        BOUNDS.clear();
    }

    private static long overrideStamp = -1, lastCheck;

    /** r22: config/aiarsenal_view.json (optional) overrides the resource table; re-read when the file changes */
    private static void checkOverride() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 1000) return;
        lastCheck = now;
        java.nio.file.Path f = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("aiarsenal_view.json");
        long st = java.nio.file.Files.exists(f) ? f.toFile().lastModified() : 0;
        if (st != overrideStamp) {
            overrideStamp = st;
            table = null;
        }
    }

    /** raw entry of any length (e.g. "<gun>.fp" first-person placement), null if absent */
    public static float[] raw(String key) {
        checkOverride();
        return table().get(key);
    }

    private static Map<String, float[]> table() {
        if (table != null) return table;
        Map<String, float[]> m = new HashMap<>();
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation("aiarsenal", "handheld_view.json"));
            if (res.isPresent()) {
                try (Reader r = res.get().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                        if (!en.getValue().isJsonArray()) continue;
                        JsonArray a = en.getValue().getAsJsonArray();
                        float[] v = new float[Math.max(8, a.size())];
                        System.arraycopy(FALLBACK, 0, v, 0, 8);
                        for (int i = 0; i < a.size(); i++) v[i] = a.get(i).getAsFloat();
                        m.put(en.getKey(), v);
                    }
                }
            }
        } catch (Exception ex) {
            ru.aiforge.realwar.RealWar.LOGGER.warn("handheld_view.json: {}", ex.toString());
        }
        try {
            java.nio.file.Path f = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("aiarsenal_view.json");
            if (java.nio.file.Files.exists(f)) {
                try (Reader r = java.nio.file.Files.newBufferedReader(f)) {
                    JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                        if (!en.getValue().isJsonArray()) continue;
                        JsonArray a = en.getValue().getAsJsonArray();
                        float[] v = new float[Math.max(8, a.size())];
                        System.arraycopy(FALLBACK, 0, v, 0, 8);
                        for (int i = 0; i < a.size(); i++) v[i] = a.get(i).getAsFloat();
                        m.put(en.getKey(), v);
                    }
                }
            }
        } catch (Exception ex) {
            ru.aiforge.realwar.RealWar.LOGGER.warn("aiarsenal_view.json: {}", ex.toString());
        }
        table = m;
        return m;
    }

    static float[] get(String item, ItemDisplayContext ctx, boolean aiming) {
        checkOverride();
        String c = ctx.getSerializedName();
        Map<String, float[]> t = table();
        float[] v = aiming ? t.get(item + ".aim." + c) : null;
        if (v == null) v = t.get(item + "." + c);
        if (v == null) v = t.get("default." + c);
        return v != null ? v : FALLBACK;
    }

    /** Model-space AABB centre (x, y, z) and largest extent of a loaded GLB (cached). */
    static float[] bounds(ResourceLocation id, GlbAssets.Loaded lm) {
        return BOUNDS.computeIfAbsent(id, k -> {
            Pose p = lm.staticPose();
            float[] mn = {1e9f, 1e9f, 1e9f}, mx = {-1e9f, -1e9f, -1e9f};
            Vector4f q = new Vector4f();
            for (int i = 0; i < lm.model.nodes.size(); i++) {
                GlbModel.Node n = lm.model.nodes.get(i);
                if (n.mesh < 0) continue;
                for (GlbModel.Primitive pr : lm.model.meshes.get(n.mesh).prims) {
                    for (int j = 0; j + 2 < pr.pos.length; j += 3) {
                        q.set(pr.pos[j], pr.pos[j + 1], pr.pos[j + 2], 1f);
                        p.world[i].transform(q);
                        mn[0] = Math.min(mn[0], q.x); mn[1] = Math.min(mn[1], q.y); mn[2] = Math.min(mn[2], q.z);
                        mx[0] = Math.max(mx[0], q.x); mx[1] = Math.max(mx[1], q.y); mx[2] = Math.max(mx[2], q.z);
                    }
                }
            }
            float ext = Math.max(mx[0] - mn[0], Math.max(mx[1] - mn[1], mx[2] - mn[2]));
            return new float[]{(mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2, (mn[2] + mx[2]) / 2, Math.max(ext, 1e-3f)};
        });
    }

    /** Applies the placement: item-space centre, offset, yaw/pitch/roll (deg), scale; centre=1 also fits the longest side to 1. */
    static void apply(PoseStack ps, float[] v, float[] b) {
        ps.translate(0.5f + v[0], 0.5f + v[1], 0.5f + v[2]);
        ps.mulPose(Axis.YP.rotationDegrees(v[3]));
        ps.mulPose(Axis.XP.rotationDegrees(v[4]));
        ps.mulPose(Axis.ZP.rotationDegrees(v[5]));
        float s = v[6];
        if (v[7] > 0.5f) {
            s /= b[3];
            ps.scale(s, s, s);
            ps.translate(-b[0], -b[1], -b[2]);
        } else {
            ps.scale(s, s, s);
        }
    }
}
