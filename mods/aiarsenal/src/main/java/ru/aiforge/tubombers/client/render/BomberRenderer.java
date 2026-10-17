package ru.aiforge.tubombers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.aiforge.tubombers.aircraft.BombType;
import ru.aiforge.tubombers.client.ClientEvents;
import ru.aiforge.tubombers.client.glb.GlbRenderer;
import ru.aiforge.tubombers.entity.BomberEntity;
import ru.aiforge.tubombers.glb.GlbModel;
import ru.aiforge.tubombers.glb.GlbModels;
import ru.aiforge.tubombers.glb.GlbPose;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a Tu-95MS / Tu-160 from its GLB: all control surfaces and mechanisation are posed from the synced state
 * (gear, flaps/slats, bay doors, wing sweep, entry hatch, stick/rudder deflection), propellers spin (blur disc at
 * speed), afterburner flames appear above military power, and the loaded bombs hang at their bay stations.
 */
public class BomberRenderer extends EntityRenderer<BomberEntity> {
    private static final float[] WHITE = {1, 1, 1, 1};
    private final Map<String, GlbPose> poses = new HashMap<>();
    private final java.util.Map<String, GlbPose> bombPoses = new java.util.HashMap<>();
    private final Map<String, int[]> propNodes = new HashMap<>();

    public BomberRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(BomberEntity e, Frustum f, double x, double y, double z) {
        return e.shouldRenderAtSqrDistance(e.distanceToSqr(x, y, z)) && f.isVisible(e.getBoundingBoxForCulling());
    }

    private static float lerp(float pt, float a, float b) {
        return a + (b - a) * pt;
    }

    public static void poseAircraft(GlbPose pose, BomberEntity e, float pt) {
        pose.reset();
        float gear = lerp(pt, e.gearO, e.gear), bay = lerp(pt, e.bayO, e.bay), flaps = lerp(pt, e.flapsO, e.flaps);
        float sweep = lerp(pt, e.sweepO, e.sweep), door = lerp(pt, e.doorO, e.door);
        pose.apply("gear", gear * 2f, false, 1f);
        pose.apply("bay_doors", bay, false, 1f);
        pose.apply("flaps", flaps, false, 1f);
        pose.apply("slats", flaps, false, 1f);
        pose.apply("sweep", sweep * 2f, false, 1f);
        pose.apply("hatch", door, false, 1f);
        float p = e.control(0), r = e.control(1), y = e.control(2);
        pose.apply("elevator", (19f + (p > 0 ? p * 29f : p * 19f)) / 24f, false, 1f);
        pose.apply("aileron", (24f + r * 24f) / 24f, false, 1f);
        pose.apply("rudder", (24f + y * 24f) / 24f, false, 1f);
        pose.apply("turret", 0f, false, 1f);
        // propellers: NK-12 at 735 rpm aliases at 20 tps, so blades turn at a slow visual rate and a blur disc fades in
        float rate = e.propAngle - e.propAngleO;
        float vis = lerp(pt, e.propAngleO, e.propAngle) * 0.13f;
        pose.apply("props", vis - (float) Math.floor(vis), true, 1f);
        GlbModel m = pose.model;
        boolean disc = rate > 0.3f;
        for (int i = 1; i <= 4; i++) {
            int d = m.node("PropDisc_" + i + "_geo");
            if (d >= 0) pose.setVisible(d, disc);
            int fl = m.node("AB_Flame_" + i + "_geo");
            if (fl >= 0) pose.setVisible(fl, e.hasFlag(BomberEntity.F_ENGINES) && e.thrust() > 1.02f);
        }
        boolean ladder = door > 0.5f && e.onGroundState();
        for (String n : new String[]{"Ladder_F_geo", "Ladder_R_geo"}) {
            int li = m.node(n);
            if (li >= 0) pose.setVisible(li, ladder);
        }
        pose.computeGlobals();
    }

    @Override
    public void render(BomberEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buf, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        if (ClientEvents.sight && ClientEvents.vehicle() == e) return; // looking through the bombsight: no own airframe
        GlbModel m = GlbModels.get(e.kind.glb);
        GlbPose pose = poses.computeIfAbsent(e.kind.glb, k -> new GlbPose(m));
        poseAircraft(pose, e, pt);
        Quaternionf q = new Quaternionf(e.qPrev).slerp(e.qCur, pt);
        int light = LevelRenderer.getLightColor(e.level(), BlockPos.containing(e.getX(), e.getY() + 6, e.getZ()));
        ps.pushPose();
        ps.mulPose(q);
        boolean lightsOn = e.hasFlag(BomberEntity.F_LIGHTS) && !e.isDestroyed();
        float[] tint = e.isDestroyed() ? new float[]{0.35f, 0.33f, 0.31f, 1f} : WHITE;
        GlbRenderer.of(m).render(pose, ps, buf, light, OverlayTexture.NO_OVERLAY, tint, null, lightsOn);
        renderBombs(e, pt, ps, buf, light, mc);
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buf, packedLight);
    }

    private void renderBombs(BomberEntity e, float pt, PoseStack ps, MultiBufferSource buf, int light, Minecraft mc) {
        int n = e.bombCount();
        if (n <= 0) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        boolean near = cam.distanceToSqr(e.position()) < 70 * 70;
        if (!near && e.bay < 0.05f) return;
        BombType t = e.bombType();
        List<Vector3f> st = e.data.stations.get(t);
        if (st == null || st.isEmpty()) return;
        GlbModel bm = GlbModels.get(t.glb());
        GlbPose bombPose = bombPoses.computeIfAbsent(t.glb(), k -> new GlbPose(bm));
        bombPose.reset();
        ru.aiforge.arsenal.core.Nodes.isolate(bombPose, t.node);
        if (t == BombType.AN602) bombPose.setVisible(bm.node("AN602_Chute"), false);
        bombPose.computeGlobals();
        int cap = e.kind.capacity[t.ordinal()];
        int first = Math.max(0, cap - n);
        GlbRenderer r = GlbRenderer.of(bm);
        for (int i = first; i < Math.min(cap, st.size()); i++) {
            Vector3f p = st.get(i);
            ps.pushPose();
            ps.translate(p.x, p.y, p.z);
            r.render(bombPose, ps, buf, light, OverlayTexture.NO_OVERLAY, WHITE, null, false);
            ps.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(BomberEntity e) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    }
}
