/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.particles.BlockParticleOption
 *  net.minecraft.core.particles.ParticleOptions
 *  net.minecraft.core.particles.ParticleTypes
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.ClipContext
 *  net.minecraft.world.level.ClipContext$Block
 *  net.minecraft.world.level.ClipContext$Fluid
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.levelgen.Heightmap$Types
 *  net.minecraft.world.phys.HitResult$Type
 *  net.minecraft.world.phys.Vec3
 */
package ru.aiforge.realwar.client.fx;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.blast.BlastPhysics;
import ru.aiforge.realwar.blast.NuclearProfile;
import ru.aiforge.realwar.client.fx.ClientBlastManager;
import ru.aiforge.realwar.client.particle.FxParticle;
import ru.aiforge.realwar.client.particle.FxSystem;
import ru.aiforge.realwar.client.particle.Sprites;
import ru.aiforge.realwar.fx.CloudModel;
import ru.aiforge.realwar.network.NukeEffectPacket;
import ru.aiforge.realwar.registry.ModSounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class NukeFx {
    private final NukeEffectPacket m;
    private final NuclearProfile prof;
    private final CloudModel cloud;
    private final double bx;
    private final double by;
    private final double bz;
    private final double heightBlocks;
    private final Random rnd;
    private final double[] out = new double[4];
    private final float[] col = new float[3];
    private final List<Timed> events = new ArrayList<Timed>();
    private final float q;
    private final int endTick;
    private int tick;
    private boolean spawned;

    public NukeFx(NukeEffectPacket m, Minecraft mc) {
        this.m = m;
        this.bx = m.x();
        this.by = m.y();
        this.bz = m.z();
        this.rnd = new Random(m.seed());
        this.prof = NuclearProfile.create(m.yieldKt(), m.surface(), m.metersPerBlock(), m.maxR());
        this.heightBlocks = m.surface() ? 0.0 : Math.max(0.0, this.by - (double)m.groundY());
        double maxTop = mc.level != null ? (double)(mc.level.getMaxBuildHeight() - 2) : 318.0;
        this.cloud = new CloudModel(m.yieldKt(), this.by, m.groundY(), m.surface(), maxTop);
        this.q = ((Double)RWConfig.CLIENT.particleQuality.get()).floatValue();
        this.endTick = this.prof.arrival[this.prof.maxR] + 60;
        this.planPlayerEvents(mc);
    }

    private double playerDistance(Minecraft mc) {
        return mc.player == null ? 0.0 : mc.player.getEyePosition().distanceTo(new Vec3(this.bx, this.by, this.bz));
    }

    private void planPlayerEvents(Minecraft mc) {
        LocalPlayer pl = mc.player;
        if (pl == null || mc.level == null) {
            return;
        }
        Vec3 burst = new Vec3(this.bx, this.by, this.bz);
        Vec3 eye = pl.getEyePosition();
        double dist = eye.distanceTo(burst);
        double fluence = BlastPhysics.thermalFluence(this.m.yieldKt(), dist * (double)this.m.metersPerBlock());
        float peak = (float)CloudModel.clamp(fluence / 0.6, 0.0, 1.0);
        if (peak > 0.02f && ((Boolean)RWConfig.CLIENT.flashEffect.get()).booleanValue()) {
            Vec3 toB = burst.subtract(eye).normalize();
            double look = pl.getLookAngle().dot(toB);
            boolean los = mc.level.clip(new ClipContext(eye, burst, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, (Entity)pl)).getType() == HitResult.Type.MISS;
            ClientBlastManager.flash(mc.level, peak *= (float)(0.3 + 0.7 * Math.max(0.0, look)) * (los ? 1.0f : 0.35f));
        }
        double arr = dist <= (double)this.prof.maxR ? this.prof.arrivalTicksAt(dist) : (double)this.prof.arrival[this.prof.maxR] + (dist - (double)this.prof.maxR) * (double)this.m.metersPerBlock() / 343.0 * 20.0;
        int at = (int)arr;
        float d = (float)dist;
        this.events.add(new Timed(at, () -> this.playBlast(mc, d)));
        this.events.add(new Timed(at + 10, () -> this.playRumble(mc, d)));
        if (dist < (double)this.prof.maxR * 1.3) {
            double p = this.prof.pressureAtBlocks(dist);
            float amp = (float)CloudModel.clamp(Math.sqrt(p) * 0.22, 0.4, 8.0);
            float dur = (float)(40.0 + 20.0 * Math.log10(1.0 + p));
            this.events.add(new Timed(at, () -> ClientBlastManager.shake(mc.level, amp, dur)));
        }
    }

    private void playAt(Minecraft mc, SoundEvent s, float vol, float pitch, double dist) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        Vec3 pp = mc.player.position();
        Vec3 dir = new Vec3(this.bx, this.by, this.bz).subtract(pp);
        double len = dir.length();
        Vec3 pos = len < 6.0 ? new Vec3(this.bx, this.by, this.bz) : pp.add(dir.scale(6.0 / len));
        mc.level.playLocalSound(pos.x, pos.y, pos.z, s, SoundSource.AMBIENT, vol, pitch, false);
    }

    private void playBlast(Minecraft mc, float dist) {
        if ((double)dist < (double)this.prof.maxR * 0.55) {
            this.playAt(mc, (SoundEvent)ModSounds.NUKE_BLAST.get(), 1.0f, 1.0f, dist);
            if ((double)dist < (double)this.prof.maxR * 0.35) {
                this.playAt(mc, (SoundEvent)ModSounds.SHOCK_CRACK.get(), 1.0f, 0.9f, dist);
            }
        } else if ((double)dist < (double)this.prof.maxR * 1.5) {
            this.playAt(mc, (SoundEvent)ModSounds.NUKE_BLAST.get(), 0.75f, 0.85f, dist);
        } else {
            this.playAt(mc, (SoundEvent)ModSounds.NUKE_FAR.get(), 0.9f, 1.0f, dist);
        }
    }

    private void playRumble(Minecraft mc, float dist) {
        float vol = (float)CloudModel.clamp(1.2 - (double)dist / ((double)this.prof.maxR * 3.0), 0.35, 1.0);
        this.playAt(mc, (SoundEvent)ModSounds.NUKE_RUMBLE.get(), vol, 1.0f, dist);
    }

    public boolean tick(ClientLevel level, Minecraft mc) {
        if (!this.spawned) {
            this.spawned = true;
            if (this.playerDistance(mc) <= (Double)RWConfig.CLIENT.maxEffectDistance.get()) {
                float visualRadius = Math.max(6f, (float)this.cloud.fireballMax);
                if (!ru.aiforge.arsenal.volumetric.client.VolumetricEffects.nuclear(this.bx, this.by, this.bz, visualRadius, this.m.seed()))
                    this.spawnCloud(level);
            }
        }
        Iterator<Timed> it = this.events.iterator();
        while (it.hasNext()) {
            Timed e = it.next();
            if (e.tick() > this.tick) continue;
            it.remove();
            e.run().run();
        }
        if (!ru.aiforge.arsenal.volumetric.client.VolumetricEffects.active() && this.playerDistance(mc) <= (Double)RWConfig.CLIENT.maxEffectDistance.get()) {
            this.shockFront(level);
        }
        ++this.tick;
        return this.tick > this.endTick && this.events.isEmpty();
    }

    private void add(FxParticle p) {
        FxSystem.add(p);
    }

    private static float fade(int age, int life, int fadeInTicks, float outFrac) {
        float in = Math.min(1.0f, (float)age / (float)Math.max(1, fadeInTicks));
        float start = (float)life * (1.0f - outFrac);
        float out = (float)age > start ? 1.0f - ((float)age - start) / ((float)life * outFrac) : 1.0f;
        return Math.max(0.0f, in * out);
    }

    private void mix(FxParticle p, double heat, float shade, float sr, float sg, float sb) {
        CloudModel.hotColor(heat, this.col);
        double k = CloudModel.smooth(0.05, 0.5, 1.0 - heat);
        p.color((float)((double)this.col[0] * (1.0 - k) + (double)(sr * shade) * k), (float)((double)this.col[1] * (1.0 - k) + (double)(sg * shade) * k), (float)((double)this.col[2] * (1.0 - k) + (double)(sb * shade) * k));
    }

    private void spawnCloud(ClientLevel level) {
        double baseY = this.m.groundY();
        double H = this.cloud.height;
        boolean surface = this.m.surface();
        FxParticle p = new FxParticle(level, this.bx, this.by, this.bz, Sprites.glow(), 80, true, 1.0f);
        float big = (float)(this.cloud.fireballMax * 5.5);
        p.motion = (pp, age) -> {
            float f = (float)age / 80.0f;
            pp.size = big * (0.45f + 0.55f * (float)Math.sqrt(f));
            pp.color(1.0f, 0.93f, 0.75f);
            pp.alpha((float)Math.pow(1.0 - (double)f, 2.2) * 0.95f);
        };
        this.add(p);
        FxParticle c = new FxParticle(level, this.bx, this.by, this.bz, Sprites.flare(), 40, true, 1.0f);
        float rays = (float)(this.cloud.fireballMax * 9.0);
        c.motion = (pp, age) -> {
            float f = (float)age / 40.0f;
            pp.size = rays * (0.3f + 0.7f * f);
            pp.color(1.0f, 0.95f, 0.85f);
            pp.alpha((1.0f - f) * 0.9f);
        };
        this.add(c);
        int nFire = (int)(260.0f * this.q);
        for (int i = 0; i < nFire; ++i) {
            double u = this.rnd.nextDouble() * 2.0 - 1.0;
            double a = this.rnd.nextDouble() * Math.PI * 2.0;
            double sn = Math.sqrt(1.0 - u * u);
            double dx = sn * Math.cos(a);
            double dy = u;
            double dz = sn * Math.sin(a);
            double rho = Math.cbrt(this.rnd.nextDouble());
            float base = (float)(this.cloud.fireballMax * (0.45 + 0.45 * this.rnd.nextDouble()));
            int life = 20 * (13 + this.rnd.nextInt(7));
            FxParticle p2 = new FxParticle(level, this.bx, this.by, this.bz, Sprites.glow(), life, true, base);
            p2.motion = (pp, age) -> {
                double s = (double)age / 20.0;
                this.cloud.fireballPos(s, dx, dy, dz, rho, this.out);
                pp.setPosition(this.bx + this.out[0], this.out[1], this.bz + this.out[2]);
                double heat = CloudModel.clamp(this.cloud.heat(s) * (1.15 - 0.45 * rho), 0.0, 1.0);
                CloudModel.hotColor(heat, this.col);
                pp.color(this.col[0], this.col[1], this.col[2]);
                double rel = this.cloud.fireballRadius(s) / this.cloud.fireballMax;
                pp.alpha((float)(Math.min(1.0, rel * 1.4) * (0.22 + 0.4 * heat)) * NukeFx.fade(age, life, 2, 0.25f));
                pp.size = base * (float)(0.35 + 0.65 * Math.min(1.0, s / 1.5));
            };
            this.add(p2);
        }
        int nFireSmoke = (int)(120.0f * this.q);
        int i = 0;
        while (i < nFireSmoke) {
            double u = this.rnd.nextDouble() * 2.0 - 1.0;
            double a = this.rnd.nextDouble() * Math.PI * 2.0;
            double sn = Math.sqrt(1.0 - u * u);
            double dx = sn * Math.cos(a);
            double dy = u;
            double dz = sn * Math.sin(a);
            double rho = 0.6 + 0.6 * this.rnd.nextDouble();
            float base = (float)(this.cloud.fireballMax * (0.35 + 0.3 * this.rnd.nextDouble()));
            int life = 20 * (16 + this.rnd.nextInt(8));
            int idx = i++;
            FxParticle p3 = new FxParticle(level, this.bx, this.by, this.bz, Sprites.smoke(idx), life, false, base);
            p3.motion = (pp, age) -> {
                double s = (double)age / 20.0;
                this.cloud.fireballPos(s, dx, dy, dz, rho, this.out);
                pp.setPosition(this.bx + this.out[0], this.out[1], this.bz + this.out[2]);
                double heat = CloudModel.clamp(this.cloud.heat(s) * 0.85, 0.0, 1.0);
                this.mix(pp, heat, 0.6f, 0.22f, 0.19f, 0.17f);
                pp.fullBright = heat > 0.3;
                double rel = this.cloud.fireballRadius(s) / this.cloud.fireballMax;
                pp.alpha((float)Math.min(1.0, rel * 1.6 + 0.15) * 0.5f * NukeFx.fade(age, life, 3, 0.3f));
                pp.size = base * (float)(0.5 + 0.5 * Math.min(1.0, s / 2.0));
            };
            this.add(p3);
        }
        this.spawnCap(level, (int)(1100.0f * this.q), CapKind.BODY);
        this.spawnCap(level, (int)(900.0f * this.q), CapKind.SKIN);
        this.spawnCap(level, (int)(240.0f * this.q), CapKind.GLOW);
        this.spawnStem(level, (int)(620.0f * this.q), false);
        this.spawnStem(level, (int)(110.0f * this.q), true);
        int nSkirt = (int)((float)(surface ? 600 : 200) * this.q);
        float hs = (float)(H / 118.0);
        int i2 = 0;
        while (i2 < nSkirt) {
            double theta = this.rnd.nextDouble() * Math.PI * 2.0;
            double frac = this.rnd.nextDouble();
            double h = this.rnd.nextDouble() * 3.0;
            float base = (float)((2.5 + this.rnd.nextDouble() * 4.0) * Math.max(0.6, (double)hs));
            float shade = 0.8f + 0.4f * this.rnd.nextFloat();
            int life = 20 * (16 + this.rnd.nextInt(16));
            int idx = i2++;
            FxParticle p4 = new FxParticle(level, this.bx, baseY + 1.0, this.bz, Sprites.smoke(idx), life, false, base);
            p4.motion = (pp, age) -> {
                double s = (double)age / 20.0;
                this.cloud.skirtPos(s, theta, frac, h, this.out);
                pp.setPosition(this.bx + this.out[0], this.out[1], this.bz + this.out[2]);
                pp.color(0.5f * shade, 0.43f * shade, 0.35f * shade);
                pp.alpha(0.5f * NukeFx.fade(age, life, 6, 0.6f) * (surface ? 1.0f : 0.6f));
                pp.size = base * (0.6f + 0.4f * Math.min(1.0f, (float)s / 4.0f));
            };
            this.add(p4);
        }
    }

    private void spawnCap(ClientLevel level, int n, CapKind kind) {
        boolean glow = kind == CapKind.GLOW;
        for (int i = 0; i < n; ++i) {
            double theta = this.rnd.nextDouble() * Math.PI * 2.0;
            double rho = switch (kind) {
                default -> throw new IncompatibleClassChangeError();
                case BODY -> Math.sqrt(this.rnd.nextDouble()) * 0.88;
                case SKIN -> 0.84 + 0.26 * this.rnd.nextDouble();
                case GLOW -> Math.sqrt(this.rnd.nextDouble()) * 0.75;
            };
            double phi0 = this.rnd.nextDouble() * Math.PI * 2.0;
            double d = this.cloud.capMinorMax;
            float base = (float)(d * (switch (kind) {
                default -> throw new IncompatibleClassChangeError();
                case BODY -> 0.45 + 0.5 * this.rnd.nextDouble();
                case SKIN -> 0.16 + 0.26 * this.rnd.nextDouble();
                case GLOW -> 0.45 + 0.4 * this.rnd.nextDouble();
            }));
            float shade = (kind == CapKind.SKIN ? 1.0f : 0.85f) + 0.3f * this.rnd.nextFloat();
            int life = glow ? 20 * (22 + this.rnd.nextInt(14)) : 20 * (100 + this.rnd.nextInt(50));
            int idx = i;
            FxParticle p = new FxParticle(level, this.bx, this.by, this.bz, glow ? Sprites.glow() : Sprites.smoke(idx), life, glow, base);
            p.spin = (this.rnd.nextFloat() - 0.5f) * 0.01f;
            p.motion = (pp, age) -> {
                double s = (double)age / 20.0;
                this.cloud.capPos(s, theta, rho, phi0, this.out);
                pp.setPosition(this.bx + this.out[0], this.out[1], this.bz + this.out[2]);
                double heat = CloudModel.clamp(this.cloud.heat(s) * (1.35 - 0.95 * Math.min(rho, 1.0)), 0.0, 1.0);
                float grow = (float)(0.25 + 0.75 * CloudModel.smooth(0.0, 8.0, s));
                if (glow) {
                    CloudModel.hotColor(heat, this.col);
                    pp.color(this.col[0], this.col[1], this.col[2]);
                    pp.alpha((float)(Math.pow(heat, 1.3) * 0.5) * NukeFx.fade(age, life, 6, 0.3f));
                    pp.size = base * grow * (float)(0.7 + 0.3 * heat);
                } else {
                    double lit = 0.8 + 0.22 * Math.sin(this.out[3]);
                    float cool = (float)CloudModel.smooth(8.0, 75.0, s);
                    float sr = 0.56f + 0.14f * cool;
                    float sg = 0.5f + 0.2f * cool;
                    float sb = 0.45f + 0.27f * cool;
                    this.mix(pp, heat, (float)((double)shade * lit), sr, sg, sb);
                    pp.fullBright = heat > 0.22;
                    pp.alpha((kind == CapKind.SKIN ? 0.5f : 0.6f) * NukeFx.fade(age, life, 12, 0.28f));
                    pp.size = base * grow;
                }
            };
            this.add(p);
        }
    }

    private void spawnStem(ClientLevel level, int n, boolean glow) {
        double H = this.cloud.height;
        boolean surface = this.m.surface();
        for (int i = 0; i < n; ++i) {
            double u = Math.pow(this.rnd.nextDouble(), 0.9);
            double theta = this.rnd.nextDouble() * Math.PI * 2.0;
            double jit = this.rnd.nextDouble();
            double swirl = this.rnd.nextDouble() * Math.PI * 2.0;
            float base = (float)(this.cloud.stemR1 * (0.8 + 0.7 * this.rnd.nextDouble()));
            int life = glow ? 20 * (14 + this.rnd.nextInt(8)) : 20 * (90 + this.rnd.nextInt(50));
            int idx = i;
            FxParticle p = new FxParticle(level, this.bx, this.m.groundY(), this.bz, glow ? Sprites.glow() : Sprites.smoke(idx), life, glow, base);
            p.motion = (pp, age) -> {
                double s = (double)age / 20.0;
                this.cloud.stemPos(s, u, theta, jit, swirl, this.out);
                pp.setPosition(this.bx + this.out[0], this.out[1], this.bz + this.out[2]);
                double len = this.cloud.stemTopY(s) - (double)this.m.groundY();
                float vis = (float)CloudModel.smooth(0.03 * H, 0.22 * H, len);
                if (!surface) {
                    vis *= (float)CloudModel.smooth(3.0, 9.0, s);
                }
                double heat = CloudModel.clamp(this.cloud.heat(s) * (0.2 + 0.9 * u) * (surface ? 1.0 : 0.4), 0.0, 1.0);
                if (glow) {
                    CloudModel.hotColor(heat, this.col);
                    pp.color(this.col[0], this.col[1], this.col[2]);
                    pp.alpha((float)(Math.pow(heat, 1.4) * 0.4) * vis * NukeFx.fade(age, life, 6, 0.3f));
                    pp.size = base * 0.8f;
                } else {
                    float k = (float)u;
                    this.mix(pp, heat, 1.0f, 0.55f - 0.06f * k, 0.47f - 0.01f * k, 0.38f + 0.07f * k);
                    pp.fullBright = heat > 0.22;
                    pp.alpha(0.55f * vis * NukeFx.fade(age, life, 10, 0.3f));
                    pp.size = base * (0.5f + 0.5f * Math.min(1.0f, (float)s / 4.0f));
                }
            };
            this.add(p);
        }
    }

    private void shockFront(ClientLevel level) {
        double rh;
        double a;
        int front = this.prof.frontAt(this.tick);
        if (front < 2 || front > this.prof.maxR) {
            return;
        }
        float strength = (float)CloudModel.clamp((double)this.prof.pressure[front] / 200.0, 0.15, 1.0);
        if (this.tick < 50) {
            int n = (int)(22.0f * this.q);
            for (int i = 0; i < n; ++i) {
                double u = this.m.surface() ? this.rnd.nextDouble() : this.rnd.nextDouble() * 2.0 - 1.0;
                a = this.rnd.nextDouble() * Math.PI * 2.0;
                double sn = Math.sqrt(1.0 - u * u);
                double x = this.bx + (double)front * sn * Math.cos(a);
                double y = this.by + (double)front * u;
                double z = this.bz + (double)front * sn * Math.sin(a);
                int life = 6 + this.rnd.nextInt(4);
                FxParticle p = new FxParticle(level, x, y, z, Sprites.smoke(i), life, false, (float)(1.5 + (double)front * 0.12));
                p.fullBright = true;
                p.motion = (pp, age) -> {
                    pp.color(0.93f, 0.95f, 1.0f);
                    pp.alpha(0.2f * (1.0f - (float)age / (float)life));
                };
                this.add(p);
            }
        }
        if ((rh = Math.sqrt(Math.max(0.0, (double)front * (double)front - this.heightBlocks * this.heightBlocks))) < 2.0) {
            return;
        }
        int n = Math.min(60, (int)(rh * 0.9 * (double)this.q) + 4);
        for (int i = 0; i < n; ++i) {
            int iz;
            a = this.rnd.nextDouble() * Math.PI * 2.0;
            double x = this.bx + rh * Math.cos(a);
            double z = this.bz + rh * Math.sin(a);
            int ix = Mth.floor((double)x);
            if (!level.hasChunk(ix >> 4, (iz = Mth.floor((double)z)) >> 4)) continue;
            int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz);
            BlockPos below = new BlockPos(ix, gy - 1, iz);
            BlockState st = level.getBlockState(below);
            int rgb = st.isAir() ? 8417376 : st.getMapColor((BlockGetter)level, (BlockPos)below).col;
            float cr = (float)(rgb >> 16 & 0xFF) / 255.0f * 0.7f + 0.15f;
            float cg = (float)(rgb >> 8 & 0xFF) / 255.0f * 0.7f + 0.13f;
            float cb = (float)(rgb & 0xFF) / 255.0f * 0.7f + 0.11f;
            int life = 20 + this.rnd.nextInt(25);
            double vy = 0.1 + 0.25 * (double)strength * this.rnd.nextDouble();
            float base = 1.6f + 2.4f * strength;
            int idx = i;
            FxParticle p = new FxParticle(level, x, (double)gy + 0.4, z, Sprites.smoke(idx), life, false, base);
            p.motion = (pp, age) -> {
                pp.translate(0.0, vy * Math.max(0.0, 1.0 - (double)age / (double)life), 0.0);
                pp.color(cr, cg, cb);
                pp.alpha(0.42f * strength * NukeFx.fade(age, life, 2, 0.7f));
                pp.size = base * (1.0f + 1.6f * (float)age / (float)life);
            };
            this.add(p);
            if (st.isAir() || !(this.rnd.nextFloat() < 0.3f * strength)) continue;
            double ang = this.rnd.nextDouble() * Math.PI * 2.0;
            level.addParticle((ParticleOptions)new BlockParticleOption(ParticleTypes.BLOCK, st), x, (double)gy + 0.3, z, Math.cos(ang) * 0.25, 0.4 + 0.5 * (double)strength * this.rnd.nextDouble(), Math.sin(ang) * 0.25);
        }
    }

    private record Timed(int tick, Runnable run) {
    }

    private static enum CapKind {
        BODY,
        SKIN,
        GLOW;

    }
}

