package ru.aiforge.bm21grad.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.aiforge.bm21grad.entity.Bm21Entity;
import ru.aiforge.bm21grad.glb.GlbPose;

import java.util.Map;
import java.util.WeakHashMap;

/** Per-vehicle client-only animation state (wheel roll, visual steering, terrain pitch, engine sound). */
public final class Bm21ClientState {
    private static final Map<Bm21Entity, Bm21ClientState> MAP = new WeakHashMap<>();

    public final GlbPose pose = new GlbPose(Bm21Entity.model());
    public double wheel, prevWheel;       // revolutions
    public float steerVis, prevSteerVis;  // -1..1, + = left
    public float pitch, prevPitch;        // degrees, + = nose up
    public float roll, prevRoll;          // degrees, + = left side down
    private Vec3 lastPos;
    private float lastYaw;
    private int lastRecoil = 100;
    private EngineSound engine;
    public int age;

    public static Bm21ClientState of(Bm21Entity e) {
        return MAP.computeIfAbsent(e, k -> new Bm21ClientState());
    }

    public void tick(Bm21Entity e) {
        age++;
        prevWheel = wheel;
        prevSteerVis = steerVis;
        prevPitch = pitch;
        prevRoll = roll;
        Vec3 p = e.position();
        double signed = 0;
        if (lastPos != null) {
            signed = p.subtract(lastPos).dot(e.forward());
            if (Math.abs(signed) < 3) wheel += signed / (2 * Math.PI * Bm21Entity.WHEEL_RADIUS);
        }
        lastPos = p;
        float target;
        if (e.isControlledByLocalInstance()) target = e.steer;
        else {
            float dyaw = Mth.wrapDegrees(e.getYRot() - lastYaw);
            target = Math.abs(signed) > 0.02 ? Mth.clamp((float) (-Math.toRadians(dyaw) * Bm21Entity.TURN_RADIUS / signed), -1, 1) : steerVis * 0.95f;
        }
        lastYaw = e.getYRot();
        steerVis += (target - steerVis) * 0.3f;

        Level lv = e.level();
        double hf = ground(lv, e.toWorld(0, 0, 2.45), e.getY());
        double hr = ground(lv, e.toWorld(0, 0, -1.8), e.getY());
        double hl = ground(lv, e.toWorld(1.0, 0, 0.3), e.getY());
        double hrr = ground(lv, e.toWorld(-1.0, 0, 0.3), e.getY());
        float tp = (float) Mth.clamp(Math.toDegrees(Math.atan2(hf - hr, 4.25)), -22, 22);
        float tr = (float) Mth.clamp(Math.toDegrees(Math.atan2(hrr - hl, 2.0)), -15, 15);
        if (!e.onGround() && Math.abs(e.getDeltaMovement().y) > 0.2) {
            tp = pitch;
            tr = roll;
        }
        pitch += (tp - pitch) * 0.25f;
        roll += (tr - roll) * 0.25f;

        Minecraft mc = Minecraft.getInstance();
        if (e.hasState(Bm21Entity.ST_ENGINE) && !e.isDestroyed() && (engine == null || engine.isStopped())) {
            engine = new EngineSound(e);
            mc.getSoundManager().play(engine);
        }
        if (e.recoilTicks < lastRecoil) onShot(e, mc);
        lastRecoil = e.recoilTicks;
    }

    private void onShot(Bm21Entity e, Minecraft mc) {
        if (mc.player == null) return;
        double d = mc.player.distanceTo(e);
        ClientFx.addShake((float) (2.2 / (1 + d / 10)));
        Level lv = e.level();
        for (int i = 0; i < 16; i++) {
            Vec3 q = e.toWorld((lv.random.nextDouble() - 0.5) * 7, 0.2, -2 - lv.random.nextDouble() * 6);
            lv.addParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), q.x, q.y, q.z, (lv.random.nextDouble() - 0.5) * 0.1, 0.02, (lv.random.nextDouble() - 0.5) * 0.1);
        }
    }

    /** Top of the highest collision shape in the column within [y-2.5, y+1.5]. */
    private static double ground(Level lv, Vec3 at, double y) {
        int x = Mth.floor(at.x), z = Mth.floor(at.z);
        for (int by = Mth.floor(y + 1.5); by >= Mth.floor(y - 2.5); by--) {
            BlockPos bp = new BlockPos(x, by, z);
            VoxelShape s = lv.getBlockState(bp).getCollisionShape(lv, bp);
            if (!s.isEmpty()) return by + s.max(net.minecraft.core.Direction.Axis.Y);
        }
        return y - 2.5;
    }
}
