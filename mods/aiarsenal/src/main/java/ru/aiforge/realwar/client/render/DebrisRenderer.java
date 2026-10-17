package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.aiforge.realwar.damage.DebrisEntity;
import ru.aiforge.realwar.glb.GlbModel;
import ru.aiforge.realwar.glb.Pose;
import ru.aiforge.realwar.vehicle.GunVehicle;

/**
 * r18: renders a {@link DebrisEntity} from its parent's GLB - only the listed node subtrees, with the parent's pose at
 * the moment of the hit. TRACK mode: the broken track runs off the wheels link by link and lies flat behind the hull.
 */
public class DebrisRenderer extends EntityRenderer<DebrisEntity> {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");

    public DebrisRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.6f;
    }

    private record Override(int node, char axis, float v) {}

    private static final class Cache {
        GlbModel model;
        boolean[] mask;
        List<Override> pose = new ArrayList<>();
        int[] links = new int[0];
    }

    private static Cache build(GlbModel m, CompoundTag s) {
        Cache c = new Cache();
        c.model = m;
        c.mask = new boolean[m.nodes.size()];
        for (String n : s.getString("n").split(",")) {
            n = n.trim();
            if (n.isEmpty()) continue;
            if (n.startsWith("re:")) {
                int[] idx = DamageRender.matching(m, n.substring(3));
                for (int i : idx) mark(m, i, c.mask);
                if (s.getInt("mode") == DebrisEntity.TRACK) c.links = idx;
            } else {
                mark(m, m.nodeIndex(n), c.mask);
            }
        }
        for (String o : s.getString("pose").split(";")) {
            String[] f = o.split(":");
            if (f.length != 3) continue;
            try {
                c.pose.add(new Override(m.nodeIndex(f[0]), f[1].charAt(0), Float.parseFloat(f[2])));
            } catch (NumberFormatException ignored) {
            }
        }
        return c;
    }

    private static void mark(GlbModel m, int i, boolean[] mask) {
        if (i < 0) return;
        mask[i] = true;
        for (int ch : m.nodes.get(i).children) mark(m, ch, mask);
    }

    @java.lang.Override
    public void render(DebrisEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        CompoundTag s = e.spec();
        String model = s.getString("m");
        if (model.isEmpty()) return;
        GlbAssets.Loaded lm = GlbAssets.get(new ResourceLocation(model));
        if (lm == null) return;
        Cache c = e.renderCache instanceof Cache cc && cc.model == lm.model ? cc : null;
        if (c == null) {
            c = build(lm.model, s);
            e.renderCache = c;
        }
        int mode = s.getInt("mode");
        Pose pose = lm.scratch();
        for (Override o : c.pose) {
            if (o.node < 0) continue;
            switch (o.axis) {
                case 'x' -> pose.rotateIdx(o.node, o.v, 1, 0, 0);
                case 'y' -> pose.rotateIdx(o.node, o.v, 0, 1, 0);
                case 'z' -> pose.rotateIdx(o.node, o.v, 0, 0, 1);
                default -> {
                }
            }
        }
        if (mode == DebrisEntity.TRACK) {
            String side = s.getString("pose").isEmpty() ? "L" : s.getString("pose");
            if (Float.isNaN(e.trackPhase)) {
                Entity p = e.level().getEntity(s.getInt("parent"));
                if (p instanceof GunVehicle gv && gv.cfg.tracked()) {
                    e.trackPhase = "L".equals(side) ? gv.trackL : gv.trackR;
                    e.trackPitch = gv.cfg.pitch();
                } else {
                    e.trackPhase = 0f;
                }
            }
            GlbModel.Animation a = lm.model.animation("track_" + side);
            if (a != null && e.trackPitch > 0) {
                float f = e.trackPhase / e.trackPitch;
                pose.sample(a, (f - (float) Math.floor(f)) * a.duration * 0.9999f);
            }
        }
        pose.restrictTo(c.mask);
        pose.compute();
        if (mode == DebrisEntity.TRACK) unroll(c.links, pose, e.tickCount + pt);
        ps.pushPose();
        if (mode == DebrisEntity.TRACK) {
            ps.translate(0, s.getFloat("dy"), 0);
        } else {
            ps.translate(0, e.getBbHeight() * 0.5f, 0);
            ps.mulPose(new Quaternionf(e.qO).slerp(e.q, pt));
        }
        ps.mulPose(DebrisEntity.frame(s));
        ps.translate(-s.getFloat("px"), -s.getFloat("py"), -s.getFloat("pz"));
        GlbRenderer.TINT = mode == DebrisEntity.TRACK ? 0.9f : s.getInt("burn") > 0 ? 0.45f : 0.85f;
        GlbRenderer.render(lm, pose, ps, buf, light, OverlayTexture.NO_OVERLAY);
        GlbRenderer.TINT = 1f;
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    /**
     * The track comes off at the bottom-front link: every following link (around the belt) runs off the wheels one by
     * one along an arc and comes to rest flat on the ground behind it, in the bottom run's orientation.
     */
    private static void unroll(int[] links, Pose pose, float age) {
        int n = links.length;
        if (n < 4) return;
        Vector3f[] p = new Vector3f[n];
        Quaternionf[] r = new Quaternionf[n];
        float minY = Float.MAX_VALUE;
        for (int k = 0; k < n; k++) {
            Matrix4f w = pose.world[links[k]];
            p[k] = w.getTranslation(new Vector3f());
            r[k] = w.getNormalizedRotation(new Quaternionf());
            minY = Math.min(minY, p[k].y);
        }
        int s = 0;
        float bestZ = -Float.MAX_VALUE;
        for (int k = 0; k < n; k++) {
            if (p[k].y < minY + 0.05f && p[k].z > bestZ) {
                bestZ = p[k].z;
                s = k;
            }
        }
        int a = (s + 1) % n, b = (s - 1 + n) % n;
        int dir = p[a].z < p[b].z ? 1 : -1;
        float pitch = Math.max(0.05f, p[s].distance(p[(s + dir + n) % n]));
        Vector3f tgt = new Vector3f();
        Vector3f pos = new Vector3f();
        Quaternionf q = new Quaternionf();
        for (int k = 1; k < n; k++) {
            int i = Math.floorMod(s + dir * k, n);
            float e = Mth.clamp((age - k * 0.5f) / 7f, 0f, 1f);
            e = e * e * (3 - 2 * e);
            if (e <= 0f) continue;
            tgt.set(p[s].x, p[s].y, p[s].z - k * pitch);
            p[i].lerp(tgt, e, pos);
            pos.y += (float) Math.sin(Math.PI * e) * 0.25f;
            r[i].slerp(r[s], e, q);
            pose.world[links[i]].translationRotateScale(pos, q, 1f);
        }
    }

    @java.lang.Override
    public ResourceLocation getTextureLocation(DebrisEntity e) {
        return WHITE;
    }
}
