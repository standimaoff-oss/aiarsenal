package ru.aiforge.realwar.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import ru.aiforge.realwar.damage.VehicleDamage;
import ru.aiforge.realwar.vehicle.GunVehicle;
import ru.aiforge.realwar.vehicle.Terrain;
import ru.aiforge.realwar.vehicle.VehicleEntity;

/**
 * r21: temporary track / tyre marks of the ground vehicles - dark mud ruts on wet soil in the rain, pressed grey ruts in
 * snow. Client only: every vehicle near the player lays a ribbon per track (or per wheel line) one tread period at a
 * time, laid on the block tops; marks fade out and disappear (mud 90 s, snow 150 s).
 */
public final class TrackMarks {
    private static final ResourceLocation TRACK = new ResourceLocation("aiarsenal", "textures/fx/track_tread.png");
    private static final ResourceLocation TYRE = new ResourceLocation("aiarsenal", "textures/fx/tyre_tread.png");
    private static final int MAX = 4096;
    private static final double STEP = 0.5;

    static final class Mark {
        final float[] p = new float[12];   // corners: prev-left, prev-right, cur-right, cur-left
        final int light;
        final long born;
        final int life;
        final int type;
        final boolean tyre;

        Mark(float[] p, int light, long born, int life, int type, boolean tyre) {
            System.arraycopy(p, 0, this.p, 0, 12);
            this.light = light;
            this.born = born;
            this.life = life;
            this.type = type;
            this.tyre = tyre;
        }
    }

    /** Last laid cross-section of every line of a vehicle. */
    static final class Lines {
        double[][] l, r;      // per line: last left / right corner (world)
        double lx, lz;        // last vehicle position
        long seen;
    }

    private static final ArrayDeque<Mark> MARKS = new ArrayDeque<>();
    private static final Map<Integer, Lines> LAST = new HashMap<>();

    private TrackMarks() {
    }

    public static void clear() {
        MARKS.clear();
        LAST.clear();
    }

    /** Lines of a vehicle: {x centre, half width} in the hull frame. */
    static double[][] lines(VehicleEntity v) {
        if (v instanceof GunVehicle gv && gv.cfg.tracked()) {
            VehicleDamage.Geo g = VehicleDamage.geo(v);
            double hw = g.trackW() * 0.5;
            return new double[][]{{g.cx() + g.hx() - hw, hw}, {g.cx() - g.hx() + hw, hw}};
        }
        float[][] w = v.spec.wheels();
        if (w == null || w.length == 0) return new double[0][];
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (float[] wh : w) {
            boolean dup = false;
            for (double[] o : out) if (Math.abs(o[0] - wh[0]) < 0.15) dup = true;
            if (!dup) out.add(new double[]{wh[0], Math.max(0.14, Math.min(0.3, wh[3] * 0.4))});
        }
        return out.toArray(new double[0][]);
    }

    /** Front-most / rear-most wheel z (the ribbon starts at the rear contact). */
    static double rearZ(VehicleEntity v) {
        if (v instanceof GunVehicle gv && gv.cfg.tracked()) {
            VehicleDamage.Geo g = VehicleDamage.geo(v);
            return g.cz() - g.hz() * 0.85;
        }
        double z = 0;
        for (float[] wh : v.spec.wheels()) z = Math.min(z, wh[2]);
        return z;
    }

    static double surfaceY(ClientLevel level, double x, double yRef, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int by = (int) Math.floor(yRef + 0.6); by > yRef - 2.5; by--) {
            BlockPos p = new BlockPos(bx, by, bz);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof SnowLayerBlock) return by + s.getValue(SnowLayerBlock.LAYERS) * 0.125;
            VoxelShape sh = s.getCollisionShape(level, p);
            if (!sh.isEmpty()) return by + sh.max(Direction.Axis.Y);
        }
        return yRef;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) return;
        long now = level.getGameTime();
        while (!MARKS.isEmpty() && now - MARKS.peekFirst().born > MARKS.peekFirst().life) MARKS.pollFirst();
        // cheap removal of expired marks deeper in the queue happens when they reach the head (lifetimes are close)
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof VehicleEntity v) || v.isRemoved() || e.distanceToSqr(mc.player) > 110 * 110) continue;
            Lines ln = LAST.get(v.getId());
            double[][] lines = lines(v);
            if (lines.length == 0) continue;
            if (ln == null || ln.l == null || ln.l.length != lines.length) {
                ln = new Lines();
                ln.l = new double[lines.length][];
                ln.r = new double[lines.length][];
                ln.lx = v.getX();
                ln.lz = v.getZ();
                LAST.put(v.getId(), ln);
            }
            ln.seen = now;
            double moved = Math.hypot(v.getX() - ln.lx, v.getZ() - ln.lz);
            if (moved < STEP) continue;
            ln.lx = v.getX();
            ln.lz = v.getZ();
            boolean grounded = v.onGround() || Math.abs(v.getDeltaMovement().y) < 0.2;
            int t = grounded && !v.isInWater() ? Terrain.under(v) : Terrain.FIRM;
            boolean tyre = !(v instanceof GunVehicle gv && gv.cfg.tracked());
            double rz = rearZ(v);
            for (int i = 0; i < lines.length; i++) {
                double x = lines[i][0], hw = lines[i][1];
                Vec3 a = v.localToWorld(x + hw, 0, rz), b = v.localToWorld(x - hw, 0, rz);
                double[] L = {a.x, surfaceY(level, a.x, v.getY(), a.z) + 0.03, a.z};
                double[] R = {b.x, surfaceY(level, b.x, v.getY(), b.z) + 0.03, b.z};
                int tt = t == Terrain.FIRM ? Terrain.FIRM : Terrain.at(level, (a.x + b.x) * 0.5, v.getY(), (a.z + b.z) * 0.5);
                if (tt != Terrain.FIRM && ln.l[i] != null && Math.hypot(L[0] - ln.l[i][0], L[2] - ln.l[i][2]) < STEP * 4) {
                    float[] p = {(float) ln.l[i][0], (float) ln.l[i][1], (float) ln.l[i][2], (float) ln.r[i][0], (float) ln.r[i][1], (float) ln.r[i][2],
                            (float) R[0], (float) R[1], (float) R[2], (float) L[0], (float) L[1], (float) L[2]};
                    int light = LevelRenderer.getLightColor(level, BlockPos.containing((a.x + b.x) * 0.5, L[1] + 0.5, (a.z + b.z) * 0.5));
                    MARKS.addLast(new Mark(p, light, now, tt == Terrain.SNOW ? 3000 : 1800, tt, tyre));
                    if (MARKS.size() > MAX) MARKS.pollFirst();
                }
                ln.l[i] = L;
                ln.r[i] = R;
            }
        }
        if (now % 200 == 0) {
            Iterator<Map.Entry<Integer, Lines>> it = LAST.entrySet().iterator();
            while (it.hasNext()) if (now - it.next().getValue().seen > 200) it.remove();
        }
    }

    public static void render(PoseStack ps, Camera cam) {
        Minecraft mc = Minecraft.getInstance();
        if (MARKS.isEmpty() || mc.level == null) return;
        long now = mc.level.getGameTime();
        Vec3 c = cam.getPosition();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        for (int pass = 0; pass < 2; pass++) {
            VertexConsumer vc = bs.getBuffer(RenderType.entityTranslucent(pass == 0 ? TRACK : TYRE));
            ps.pushPose();
            Matrix4f pm = ps.last().pose();
            Matrix3f nm = ps.last().normal();
            for (Mark m : MARKS) {
                if (m.tyre != (pass == 1)) continue;
                float age = (now - m.born) / (float) m.life;
                if (age >= 1f) continue;
                float al = (age > 0.7f ? (1f - age) / 0.3f : 1f) * (m.type == Terrain.SNOW ? 0.75f : 0.88f);
                float r, g, b;
                switch (m.type) {
                    case Terrain.SNOW -> { r = 0.58f; g = 0.63f; b = 0.70f; }
                    case Terrain.BOG -> { r = 0.20f; g = 0.15f; b = 0.10f; }
                    default -> { r = 0.27f; g = 0.19f; b = 0.11f; }
                }
                float[] p = m.p;
                float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
                for (int k = 0; k < 4; k++) {
                    vc.vertex(pm, (float) (p[k * 3] - c.x), (float) (p[k * 3 + 1] - c.y), (float) (p[k * 3 + 2] - c.z)).color(r, g, b, al)
                            .uv(uv[k][0], uv[k][1]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(m.light).normal(nm, 0f, 1f, 0f).endVertex();
                }
            }
            ps.popPose();
            bs.endBatch(RenderType.entityTranslucent(pass == 0 ? TRACK : TYRE));
        }
    }
}
