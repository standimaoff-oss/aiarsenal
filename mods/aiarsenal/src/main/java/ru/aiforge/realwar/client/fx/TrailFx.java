/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Position
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.level.ClipContext
 *  net.minecraft.world.level.ClipContext$Block
 *  net.minecraft.world.level.ClipContext$Fluid
 *  net.minecraft.world.phys.BlockHitResult
 *  net.minecraft.world.phys.HitResult$Type
 *  net.minecraft.world.phys.Vec3
 */
package ru.aiforge.realwar.client.fx;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.client.particle.FxParticle;
import ru.aiforge.realwar.client.particle.FxSystem;
import ru.aiforge.realwar.client.particle.Sprites;
import ru.aiforge.realwar.missile.MissileEntity;
import ru.aiforge.realwar.missile.MissileType;
import ru.aiforge.realwar.network.TracerPacket;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.ordnance.RocketEntity;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class TrailFx {
    private static final Random R = new Random();
    private static final int PARTICLE_CAP = 9000;
    private static final List<Tracer> TRACERS = new ArrayList<Tracer>();

    private TrailFx() {
    }

    public static float q() {
        return ((Double)RWConfig.CLIENT.trailQuality.get()).floatValue();
    }

    public static boolean crowded() {
        return FxSystem.count() > 9000;
    }

    public static void rocketTick(RocketEntity e) {
        if (e.tickCount < 1) return;
        if (ru.aiforge.arsenal.volumetric.client.VolumetricEffects.active()) {
            ru.aiforge.arsenal.volumetric.client.sim.VolumetricFxManager.recordRocket(e.getId(), e.position(), e.isBurning() ? e.getDeltaMovement() : Vec3.ZERO, 1f);
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || e.tickCount < 1) {
            return;
        }
        Ballistics.Profile p = e.profile();
        double s = p == Ballistics.Profile.GRAD ? 1.0 : (p == Ballistics.Profile.SMERCH ? 1.9 : (p == Ballistics.Profile.M13 ? 0.9 : 2.7));
        Vec3 a = new Vec3(e.xo, e.yo, e.zo);
        Vec3 b = e.position();
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.001 || len > 120.0) {
            return;
        }
        boolean burn = e.isBurning();
        Vec3 dir = d.scale(1.0 / len);
        if (e.tickCount < 3 && burn) {
            return;
        }
        double spacing = burn ? 1.5 * Math.sqrt(s) / Math.min((double)TrailFx.q(), 1.6) : 6.0 / Math.min((double)TrailFx.q(), 1.5);
        int n = Math.max(1, (int)Math.ceil(len / spacing));
        if (TrailFx.crowded() && R.nextInt(3) != 0) {
            n = 1;
        }
        for (int i = 0; i < n; ++i) {
            double f = ((double)i + R.nextDouble()) / (double)n;
            Vec3 pos = a.add(d.scale(f)).subtract(dir.scale(p == Ballistics.Profile.SAM_48N6 ? 3.4 : (p == Ballistics.Profile.SMERCH ? 3.6 : 1.6)));
            if (burn) {
                TrailFx.smoke(level, pos, dir, s, false);
                TrailFx.flame(level, pos, dir, s);
                continue;
            }
            TrailFx.smoke(level, pos, dir, s, true);
        }
        if (burn) {
            Vec3 tail = b.subtract(dir.scale(p == Ballistics.Profile.SAM_48N6 ? 3.8 : (p == Ballistics.Profile.SMERCH ? 4.0 : 1.9)));
            FxParticle core = new FxParticle(level, tail.x, tail.y, tail.z, Sprites.flare(), 2, true, (float)(2.6 * s));
            core.fullBright = true;
            core.motion = (pp, age) -> {
                pp.color(1.0f, 0.82f, 0.5f);
                pp.alpha(0.95f * (1.0f - (float)age / 2.0f));
            };
            FxSystem.add(core);
        }
    }

    public static void missileTick(MissileEntity e) {
        if (e.tickCount < 2 || e.mode() == 2) return;
        if (ru.aiforge.arsenal.volumetric.client.VolumetricEffects.active()) {
            ru.aiforge.arsenal.volumetric.client.sim.VolumetricFxManager.recordRocket(e.getId(), e.position(), e.getDeltaMovement(), 1.5f);
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || e.tickCount < 2 || e.mode() == 2) {
            return;
        }
        Vec3 a = new Vec3(e.xo, e.yo, e.zo);
        Vec3 b = e.position();
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.001 || len > 160.0) {
            return;
        }
        Vec3 dir = d.scale(1.0 / len);
        MissileType.Cls c = e.type().cls;
        boolean rv = e.mode() == 1;
        double s = rv ? 0.6 : c.trail;
        boolean burn = e.isBurning();
        if (!burn && !rv) {
            return;
        }
        double spacing = (rv ? 2.5 : 1.5 * Math.sqrt(s)) / Math.min((double)TrailFx.q(), 1.6);
        int n = Math.max(1, (int)Math.ceil(len / spacing));
        n = Math.min(n, 8);
        if (TrailFx.crowded() && R.nextInt(3) != 0) {
            n = 1;
        }
        double tailOff = rv ? 0.3 : c.length * 0.5 + 0.2;
        for (int i = 0; i < n; ++i) {
            double f = ((double)i + R.nextDouble()) / (double)n;
            Vec3 pos = a.add(d.scale(f)).subtract(dir.scale(tailOff));
            if (rv) {
                TrailFx.smooth(level, pos, dir, s);
                if (R.nextInt(2) != 0) continue;
                TrailFx.flame(level, pos, dir, s);
                continue;
            }
            TrailFx.smoke(level, pos, dir, s, false);
            TrailFx.flame(level, pos, dir, s);
        }
        if (burn && !rv) {
            Vec3 tail = b.subtract(dir.scale(tailOff + 0.4));
            FxParticle core = new FxParticle(level, tail.x, tail.y, tail.z, Sprites.flare(), 2, true, (float)(2.4 * s));
            core.fullBright = true;
            core.motion = (pp, age) -> {
                pp.color(1.0f, 0.82f, 0.5f);
                pp.alpha(0.95f * (1.0f - (float)age / 2.0f));
            };
            FxSystem.add(core);
        }
    }

    /**
     * Generic exhaust trail between two positions of any moving body (used by the AI Arsenal missiles, ICBM stages and
     * drone boosters): smoke puffs + flame, optional bright core. {@code s} is the plume scale (1 = tactical missile),
     * {@code tailOff} the distance from the body origin back to the nozzle, {@code plasma} = re-entry glow instead of smoke.
     */
    public static void trail(ClientLevel level, int entityId, Vec3 a, Vec3 b, double s, double tailOff, boolean core, boolean plasma) {
        Vec3 delta = b.subtract(a);
        if (!plasma && ru.aiforge.arsenal.volumetric.client.VolumetricEffects.active()) {
            if (delta.lengthSqr() > 0.0001 && delta.lengthSqr() < 25600)
                ru.aiforge.arsenal.volumetric.client.sim.VolumetricFxManager.recordRocket(entityId, b.subtract(delta.normalize().scale(tailOff)), delta, (float)s);
            return;
        }
        trail(level, a, b, s, tailOff, core, plasma);
    }

    public static void trail(ClientLevel level, Vec3 a, Vec3 b, double s, double tailOff, boolean core, boolean plasma) {
        if (level == null) {
            return;
        }
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.001 || len > 160.0) {
            return;
        }
        Vec3 dir = d.scale(1.0 / len);
        double spacing = (plasma ? 2.5 : 1.5 * Math.sqrt(s)) / Math.min((double)TrailFx.q(), 1.6);
        int n = Math.min(8, Math.max(1, (int)Math.ceil(len / spacing)));
        if (TrailFx.crowded() && R.nextInt(3) != 0) {
            n = 1;
        }
        for (int i = 0; i < n; ++i) {
            double f = ((double)i + R.nextDouble()) / (double)n;
            Vec3 pos = a.add(d.scale(f)).subtract(dir.scale(tailOff));
            if (plasma) {
                TrailFx.smooth(level, pos, dir, s);
                if (R.nextInt(2) == 0) {
                    TrailFx.flame(level, pos, dir, s);
                }
                continue;
            }
            TrailFx.smoke(level, pos, dir, s, false);
            TrailFx.flame(level, pos, dir, s);
        }
        if (core) {
            Vec3 tail = b.subtract(dir.scale(tailOff + 0.4));
            FxParticle c = new FxParticle(level, tail.x, tail.y, tail.z, Sprites.flare(), 2, true, (float)(2.4 * s));
            c.fullBright = true;
            c.motion = (pp, age) -> {
                pp.color(1.0f, 0.82f, 0.5f);
                pp.alpha(0.95f * (1.0f - (float)age / 2.0f));
            };
            FxSystem.add(c);
        }
    }

    /** Thin light-grey contrail of a turbofan / piston engine (cruise missiles and strike drones in cruise flight). */
    public static void engineWisp(ClientLevel level, Vec3 nozzle, Vec3 dir, double s) {
        if (level == null || R.nextFloat() > 0.6f * TrailFx.q()) {
            return;
        }
        TrailFx.smoke(level, nozzle, dir, 0.35 * s, true);
    }

    private static void smooth(ClientLevel level, Vec3 pos, Vec3 dir, double s) {
        TrailFx.smoke(level, pos, dir, s, true);
    }

    public static void padLaunch(ClientLevel level, Vec3 top, int padLevel, int tick) {
        double s = 1.0 + 0.9 * (double)padLevel;
        float qq = Math.min(TrailFx.q(), 1.6f);
        if (tick < 40) {
            int nf = (int)(3.0f * qq) + 1;
            for (int i = 0; i < nf; ++i) {
                Vec3 p = top.add((R.nextDouble() - 0.5) * 0.5 * (double)padLevel, 0.2 + R.nextDouble() * 0.5, (R.nextDouble() - 0.5) * 0.5 * (double)padLevel);
                TrailFx.flame(level, p, new Vec3(0.0, -1.0, 0.0), s * 1.1);
            }
            FxParticle core = new FxParticle(level, top.x, top.y + 0.4, top.z, Sprites.flare(), 2, true, (float)(3.2 * s));
            core.fullBright = true;
            core.motion = (pp, age) -> {
                pp.color(1.0f, 0.8f, 0.45f);
                pp.alpha(0.9f * (1.0f - (float)age / 2.0f));
            };
            FxSystem.add(core);
        }
        int ns = (int)((double)((float)(2 + padLevel * 2) * qq) * (tick < 30 ? 1.0 : 0.5));
        for (int i = 0; i < ns; ++i) {
            double a = R.nextDouble() * Math.PI * 2.0;
            double sp = (0.25 + R.nextDouble() * 0.55) * (0.8 + 0.3 * (double)padLevel);
            Vec3 v = new Vec3(Math.cos(a) * sp, 0.02 + R.nextDouble() * 0.05, Math.sin(a) * sp);
            FxParticle p = TrailFx.launchSmoke(level, top.add(Math.cos(a) * (double)padLevel * 0.5, 0.1, Math.sin(a) * (double)padLevel * 0.5), v, s, 0.92f);
            if (R.nextInt(3) != 0) continue;
            p.color(0.6f, 0.53f, 0.43f);
        }
        if (tick < 50 && R.nextInt(2) == 0) {
            Vec3 v = new Vec3((R.nextDouble() - 0.5) * 0.05, 0.12 + R.nextDouble() * 0.1, (R.nextDouble() - 0.5) * 0.05);
            TrailFx.launchSmoke(level, top.add(0.0, 0.5, 0.0), v, s * 1.3, 0.92f);
        }
    }

    static void smoke(ClientLevel level, Vec3 pos, Vec3 dir, double s, boolean thin) {
        if (TrailFx.crowded() && R.nextBoolean()) {
            return;
        }
        int life = (int)((double)(thin ? 50 : 90 + R.nextInt(60)) * (0.8 + 0.3 * Math.min(s, 2.0)));
        float size0 = (float)((thin ? 0.35 : 0.9) * s);
        float size1 = (float)((thin ? 1.5 : 3.6 + R.nextDouble() * 1.2) * s);
        float a0 = thin ? 0.22f : 0.62f;
        float shade = 0.8f + 0.18f * R.nextFloat();
        FxParticle p = new FxParticle(level, pos.x, pos.y, pos.z, Sprites.smoke(R.nextInt(4)), life, false, size0);
        double vx = (R.nextDouble() - 0.5) * 0.04 - dir.x * 0.02;
        double vy = (R.nextDouble() - 0.3) * 0.03;
        double vz = (R.nextDouble() - 0.5) * 0.04 - dir.z * 0.02;
        p.spin = (R.nextFloat() - 0.5f) * 0.05f;
        p.velocity(vx, vy, vz);
        p.motion = (pp, age) -> {
            float f = (float)age / (float)life;
            pp.translate(pp.getDeltaX(), pp.getDeltaY() + 0.0035, pp.getDeltaZ());
            pp.scaleVelocity(0.97);
            float grow = 1.0f - (1.0f - f) * (1.0f - f);
            pp.size = size0 + (size1 - size0) * grow;
            float warm = Math.max(0.0f, 1.0f - (float)age / 7.0f);
            pp.color(Math.min(1.0f, shade + 0.18f * warm), Math.min(1.0f, shade + 0.08f * warm), shade - 0.1f * warm);
            float fadeIn = Math.min(1.0f, (float)age / 2.5f);
            pp.alpha(a0 * fadeIn * (1.0f - f) * (1.0f - f * 0.3f));
        };
        FxSystem.add(p);
    }

    static void flame(ClientLevel level, Vec3 pos, Vec3 dir, double s) {
        int life = 4 + R.nextInt(3);
        float size = (float)(1.5 * s * (0.8 + 0.4 * R.nextDouble()));
        FxParticle p = new FxParticle(level, pos.x, pos.y, pos.z, Sprites.glow(), life, true, size);
        p.fullBright = true;
        p.motion = (pp, age) -> {
            float f = (float)age / (float)life;
            pp.size = size * (1.0f - 0.65f * f);
            pp.color(1.0f, 0.78f - 0.45f * f, 0.38f - 0.28f * f);
            pp.alpha(0.85f * (1.0f - f));
        };
        FxSystem.add(p);
    }

    public static void launch(ClientLevel level, Vec3 muzzle, Vec3 dir, int big, double tubeLen) {
        double s = big == 0 ? 1.0 : (big == 1 ? 1.8 : 2.4);
        float qq = Math.min(TrailFx.q(), 1.6f);
        FxParticle flash = new FxParticle(level, muzzle.x, muzzle.y, muzzle.z, Sprites.flare(), 6, true, (float)(4.0 * s));
        flash.fullBright = true;
        flash.motion = (pp, age) -> {
            pp.color(1.0f, 0.85f, 0.55f);
            pp.alpha(1.0f - (float)age / 6.0f);
            pp.size = (float)(4.0 * s * (double)(1.0f - 0.4f * (float)age / 6.0f));
        };
        FxSystem.add(flash);
        Vec3 breech = muzzle.subtract(dir.scale(tubeLen));
        Vec3 back = dir.scale(-1.0);
        int nFire = (int)((float)(big == 2 ? 6 : 16) * qq);
        for (int i = 0; i < nFire; ++i) {
            Vec3 v = back.scale(0.5 + R.nextDouble() * 1.1 * s * 0.6).add((R.nextDouble() - 0.5) * 0.25, (R.nextDouble() - 0.5) * 0.25, (R.nextDouble() - 0.5) * 0.25);
            int life = 6 + R.nextInt(6);
            float size = (float)(1.3 * s * (0.6 + 0.6 * R.nextDouble()));
            FxParticle p = new FxParticle(level, breech.x, breech.y, breech.z, Sprites.glow(), life, true, size);
            p.fullBright = true;
            p.velocity(v.x, v.y, v.z);
            p.motion = (pp, age) -> {
                float f = (float)age / (float)life;
                pp.translate(pp.getDeltaX(), pp.getDeltaY(), pp.getDeltaZ());
                pp.scaleVelocity(0.82);
                pp.size = size * (0.8f + 0.5f * f);
                pp.color(1.0f, 0.74f - 0.4f * f, 0.32f - 0.25f * f);
                pp.alpha(0.8f * (1.0f - f));
            };
            FxSystem.add(p);
        }
        int nSmoke = (int)((double)(big == 2 ? 26 : 22) * 0.45 * (double)qq * (1.0 + (double)big * 0.4));
        for (int i = 0; i < nSmoke; ++i) {
            Vec3 o = R.nextBoolean() ? muzzle : breech;
            Vec3 v = back.scale(R.nextDouble() * 0.5 * s).add((R.nextDouble() - 0.5) * 0.18, R.nextDouble() * 0.08, (R.nextDouble() - 0.5) * 0.18);
            TrailFx.launchSmoke(level, o, v, s, 0.92f);
        }
        Vec3 g = TrailFx.groundBelow(level, breech);
        if (g != null) {
            int nDust = (int)((double)(big == 2 ? 26 : 16) * 0.5 * (double)qq * (1.0 + (double)big * 0.5));
            for (int i = 0; i < nDust; ++i) {
                double a = R.nextDouble() * Math.PI * 2.0;
                double sp = (0.25 + R.nextDouble() * 0.5) * (0.8 + 0.5 * (double)big);
                Vec3 v = new Vec3(Math.cos(a) * sp, 0.02 + R.nextDouble() * 0.06, Math.sin(a) * sp).add(back.x * 0.3, 0.0, back.z * 0.3);
                FxParticle p = TrailFx.launchSmoke(level, g.add(0.0, 0.3, 0.0), v, s * 1.1, 0.0f);
                p.color(0.62f, 0.54f, 0.43f);
            }
        }
    }

    private static FxParticle launchSmoke(ClientLevel level, Vec3 o, Vec3 v, double s, float shadeHint) {
        int life = 70 + R.nextInt(70);
        float size0 = (float)(1.0 * s);
        float size1 = (float)((3.0 + R.nextDouble() * 1.6) * s);
        float shade = 0.62f + 0.3f * R.nextFloat();
        boolean dust = shadeHint == 0.0f;
        FxParticle p = new FxParticle(level, o.x, o.y, o.z, Sprites.smoke(R.nextInt(4)), life, false, size0);
        p.velocity(v.x, v.y, v.z);
        p.spin = (R.nextFloat() - 0.5f) * 0.05f;
        p.motion = (pp, age) -> {
            float f = (float)age / (float)life;
            pp.translate(pp.getDeltaX(), pp.getDeltaY(), pp.getDeltaZ());
            pp.scaleVelocity(0.93);
            pp.translate(0.0, dust ? 0.004 : 0.008, 0.0);
            float grow = 1.0f - (1.0f - f) * (1.0f - f);
            pp.size = size0 + (size1 - size0) * grow;
            if (!dust) {
                pp.color(shade, shade, shade - 0.04f);
            }
            pp.alpha(0.55f * Math.min(1.0f, (float)age / 2.0f) * (1.0f - f) * (1.0f - f * 0.2f));
        };
        FxSystem.add(p);
        return p;
    }

    private static Vec3 groundBelow(ClientLevel level, Vec3 from) {
        BlockHitResult h = level.clip(new ClipContext(from, from.add(0.0, -7.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity)Minecraft.getInstance().player));
        return h.getType() == HitResult.Type.MISS ? null : h.getLocation();
    }

    public static void muzzleFlash(ClientLevel level, Vec3 m, Vec3 dir) {
        FxParticle f = new FxParticle(level, m.x + dir.x * 0.3, m.y + dir.y * 0.3, m.z + dir.z * 0.3, Sprites.flare(), 2, true, 1.3f + R.nextFloat() * 0.5f);
        f.fullBright = true;
        f.motion = (pp, age) -> {
            pp.color(1.0f, 0.8f, 0.45f);
            pp.alpha(1.0f - (float)age / 2.0f);
        };
        FxSystem.add(f);
        if (R.nextInt(4) == 0 && !TrailFx.crowded()) {
            FxParticle s = new FxParticle(level, m.x, m.y, m.z, Sprites.smoke(R.nextInt(4)), 30, false, 0.4f);
            s.velocity(dir.x * 0.1, dir.y * 0.1 + 0.01, dir.z * 0.1);
            s.motion = (pp, age) -> {
                float ff = (float)age / 30.0f;
                pp.translate(pp.getDeltaX(), pp.getDeltaY() + 0.004, pp.getDeltaZ());
                pp.scaleVelocity(0.92);
                pp.size = 0.4f + 1.4f * ff;
                pp.color(0.7f, 0.7f, 0.7f);
                pp.alpha(0.35f * (1.0f - ff));
            };
            FxSystem.add(s);
        }
    }

    /** Gun discharge: flash, muzzle-brake side jets, forward smoke cloud and a ground dust ring (big = 120-155 mm). */
    public static void gunBlast(ClientLevel level, Vec3 m, Vec3 dir, boolean big, boolean brake) {
        float s = big ? 1.0f : 0.6f;
        FxParticle f = new FxParticle(level, m.x + dir.x * 0.6, m.y + dir.y * 0.6, m.z + dir.z * 0.6, Sprites.flare(), 3, true, 3.2f * s);
        f.fullBright = true;
        f.motion = (pp, age) -> {
            pp.color(1.0f, 0.75f, 0.4f);
            pp.alpha(1.0f - (float)age / 3.0f);
        };
        FxSystem.add(f);
        Vec3 side = new Vec3(-dir.z, 0.0, dir.x).normalize();
        int n = crowded() ? 6 : (big ? 22 : 12);
        for (int i = 0; i < n; ++i) {
            boolean jet = brake && i % 3 == 0;
            Vec3 v = jet ? side.scale((i % 2 == 0 ? 1 : -1) * (0.25 + R.nextDouble() * 0.15)).add(dir.scale(0.05))
                         : dir.scale(0.15 + R.nextDouble() * 0.35 * s).add(R.nextGaussian() * 0.06, R.nextGaussian() * 0.04 + 0.02, R.nextGaussian() * 0.06);
            int life = 40 + R.nextInt(50);
            FxParticle p = new FxParticle(level, m.x, m.y, m.z, Sprites.smoke(R.nextInt(4)), life, false, 0.6f * s);
            p.velocity(v.x, v.y, v.z);
            float g = 0.62f + R.nextFloat() * 0.12f;
            p.motion = (pp, age) -> {
                float ff = (float)age / (float)life;
                pp.translate(pp.getDeltaX(), pp.getDeltaY() + 0.003, pp.getDeltaZ());
                pp.scaleVelocity(0.86);
                pp.size = (0.6f + 3.2f * ff) * s * (big ? 1.4f : 1.0f);
                pp.color(g, g, g * 0.97f);
                pp.alpha(0.55f * (1.0f - ff));
            };
            FxSystem.add(p);
        }
        // ground dust kicked up under the muzzle
        double gy = m.y - 1.6;
        for (int i = 0; i < (big ? 10 : 5); ++i) {
            double a = R.nextDouble() * Math.PI * 2.0;
            int life = 50 + R.nextInt(30);
            FxParticle d = new FxParticle(level, m.x + Math.cos(a) * 1.5, gy, m.z + Math.sin(a) * 1.5, Sprites.smoke(R.nextInt(4)), life, false, 0.8f);
            d.velocity(Math.cos(a) * 0.12 + dir.x * 0.1, 0.02, Math.sin(a) * 0.12 + dir.z * 0.1);
            d.motion = (pp, age) -> {
                float ff = (float)age / (float)life;
                pp.translate(pp.getDeltaX(), pp.getDeltaY(), pp.getDeltaZ());
                pp.scaleVelocity(0.9);
                pp.size = 0.8f + 2.5f * ff;
                pp.color(0.62f, 0.55f, 0.43f);
                pp.alpha(0.4f * (1.0f - ff));
            };
            FxSystem.add(d);
        }
    }

    /** Tracer streak behind a gun projectile (tank rounds glow, howitzer shells leave a faint heat shimmer only). */
    public static void shellTick(ru.aiforge.realwar.ordnance.ShellEntity e) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || e.tickCount < 1) {
            return;
        }
        Vec3 a = new Vec3(e.xo, e.yo, e.zo);
        Vec3 b = e.position();
        double len = b.distanceTo(a);
        if (len < 0.01 || len > 200.0) {
            return;
        }
        boolean art = e.profile().tntKg >= 3.5;
        if (art && e.tickCount > 6) {
            return;
        }
        int n = Math.min(12, (int)Math.ceil(len / 0.8));
        for (int i = 0; i < n; ++i) {
            double t = (double)i / (double)n;
            FxParticle f = new FxParticle(level, a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t, Sprites.flare(), 2, true, art ? 0.25f : 0.35f);
            f.fullBright = true;
            f.motion = (pp, age) -> {
                pp.color(1.0f, 0.55f, 0.2f);
                pp.alpha(0.9f - 0.4f * age);
            };
            FxSystem.add(f);
        }
    }

    public static void addTracer(TracerPacket m) {
        Tracer t = new Tracer();
        t.pos = new Vec3(m.x(), m.y(), m.z());
        t.vel = new Vec3(m.vx(), m.vy(), m.vz());
        t.life = m.life();
        if (TRACERS.size() < 400) {
            TRACERS.add(t);
        }
    }

    public static void clear() {
        TRACERS.clear();
    }

    public static void tick(ClientLevel level) {
        Iterator<Tracer> it = TRACERS.iterator();
        while (it.hasNext()) {
            BlockHitResult h;
            Tracer t = it.next();
            Vec3 from = t.pos;
            t.vel = new Vec3(t.vel.x * 0.985, t.vel.y * 0.985 - 0.02, t.vel.z * 0.985);
            Vec3 to = from.add(t.vel);
            if (level.hasChunkAt(BlockPos.containing((Position)to)) && (h = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null))).getType() != HitResult.Type.MISS) {
                it.remove();
                continue;
            }
            int n = 4;
            for (int i = 0; i < n; ++i) {
                Vec3 p = from.add(t.vel.scale(((double)i + 0.5) / (double)n));
                FxParticle g = new FxParticle(level, p.x, p.y, p.z, Sprites.glow(), 4, true, 0.55f);
                g.fullBright = true;
                g.motion = (pp, age) -> {
                    float f = (float)age / 4.0f;
                    pp.color(1.0f, 0.55f - 0.25f * f, 0.15f);
                    pp.alpha(0.95f * (1.0f - f));
                    pp.size = 0.55f * (1.0f - 0.5f * f);
                };
                FxSystem.add(g);
            }
            FxParticle c = new FxParticle(level, to.x, to.y, to.z, Sprites.flare(), 2, true, 0.8f);
            c.fullBright = true;
            c.motion = (pp, age) -> {
                pp.color(1.0f, 0.92f, 0.7f);
                pp.alpha(1.0f - (float)age / 2.0f);
            };
            FxSystem.add(c);
            t.pos = to;
            if (--t.life > 0) continue;
            it.remove();
        }
    }

    private static final class Tracer {
        Vec3 pos;
        Vec3 vel;
        int life;

        private Tracer() {
        }
    }
}

