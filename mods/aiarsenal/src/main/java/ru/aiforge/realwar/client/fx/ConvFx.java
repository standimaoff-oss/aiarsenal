/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.particles.BlockParticleOption
 *  net.minecraft.core.particles.ParticleOptions
 *  net.minecraft.core.particles.ParticleTypes
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.phys.Vec3
 */
package ru.aiforge.realwar.client.fx;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.blast.BlastPhysics;
import ru.aiforge.realwar.client.fx.ClientBlastManager;
import ru.aiforge.realwar.client.particle.FxParticle;
import ru.aiforge.realwar.client.particle.FxSystem;
import ru.aiforge.realwar.client.particle.Sprites;
import ru.aiforge.realwar.fx.CloudModel;
import ru.aiforge.realwar.network.ConventionalEffectPacket;
import ru.aiforge.realwar.registry.ModSounds;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class ConvFx {
    private final ConventionalEffectPacket m;
    private final Random rnd;
    private boolean done;

    public ConvFx(ConventionalEffectPacket m) {
        this.m = m;
        this.rnd = new Random(m.seed());
    }

    private void add(FxParticle p) {
        FxSystem.add(p);
    }

    public boolean tick(ClientLevel level, Minecraft mc) {
        double dist;
        if (this.done) {
            return true;
        }
        this.done = true;
        Vec3 c = new Vec3(this.m.x(), this.m.y(), this.m.z());
        double d = dist = mc.player == null ? 0.0 : mc.player.getEyePosition().distanceTo(c);
        if (dist <= (Double)RWConfig.CLIENT.maxEffectDistance.get()) {
            this.spawn(level, c);
        }
        this.schedule(level, mc, c, dist);
        return true;
    }

    private void spawn(ClientLevel level, Vec3 c) {
        if (ru.aiforge.arsenal.volumetric.client.VolumetricEffects.blast(c.x, c.y, c.z, (float)Math.cbrt(this.m.massKg()), this.m.seed())) return;
        double a;
        double sp;
        float q = ((Double)RWConfig.CLIENT.particleQuality.get()).floatValue();
        double r = Math.cbrt(this.m.massKg());
        double x = c.x;
        double y = c.y;
        double z = c.z;
        FxParticle core = new FxParticle(level, x, y, z, Sprites.glow(), 10, true, (float)(r * 3.2));
        float coreSize = (float)(r * 3.4);
        core.motion = (p, age) -> {
            float f = (float)age / 10.0f;
            p.size = coreSize * (0.5f + 0.5f * f);
            p.color(1.0f, 0.85f, 0.55f);
            p.alpha((1.0f - f) * 0.9f);
        };
        this.add(core);
        int nFire = (int)(Math.max(16.0, Math.min(90.0, 26.0 * r / 2.0)) * (double)q);
        float[] col = new float[3];
        for (int i = 0; i < nFire; ++i) {
            double u = this.rnd.nextDouble() * 2.0 - 1.0;
            double a2 = this.rnd.nextDouble() * Math.PI * 2.0;
            double sn = Math.sqrt(1.0 - u * u);
            sp = (0.08 + 0.14 * this.rnd.nextDouble()) * Math.max(0.6, r / 4.0);
            double vx = sn * Math.cos(a2) * sp;
            double vy = u * sp + 0.05;
            double vz = sn * Math.sin(a2) * sp;
            int life = 10 + this.rnd.nextInt(14);
            float base = (float)(r * (0.5 + 0.5 * this.rnd.nextDouble()));
            FxParticle p2 = new FxParticle(level, x, y, z, Sprites.glow(), life, true, base);
            p2.velocity(vx, vy, vz);
            p2.drag = 0.86f;
            p2.motion = (pp, age) -> {
                float f = (float)age / (float)life;
                pp.translate(pp.getDeltaX(), pp.getDeltaY(), pp.getDeltaZ());
                pp.scaleVelocity(0.86);
                CloudModel.hotColor(1.0 - (double)f * 0.85, col);
                pp.color(col[0], col[1], col[2]);
                pp.alpha(0.6f * (1.0f - f));
                pp.size = base * (0.6f + 0.6f * f);
            };
            this.add(p2);
        }
        // r27: much denser, bigger and longer-lived smoke - the fallback had to read as a real column.
        int nSmoke = (int)(Math.max(30.0, Math.min(180.0, 44.0 * r / 2.0)) * (double)q);
        for (int i = 0; i < nSmoke; ++i) {
            double a3 = this.rnd.nextDouble() * Math.PI * 2.0;
            double rad = this.rnd.nextDouble() * r * 0.6;
            double vx = Math.cos(a3) * 0.03 * r / 3.0;
            double vz = Math.sin(a3) * 0.03 * r / 3.0;
            double vy = (0.12 + 0.35 * this.rnd.nextDouble()) * Math.max(0.7, r / 5.0);
            int life = 70 + this.rnd.nextInt(120);
            float base = (float)(r * (0.7 + 0.9 * this.rnd.nextDouble()));
            float shade = 0.13f + 0.25f * this.rnd.nextFloat();
            FxParticle p3 = new FxParticle(level, x + Math.cos(a3) * rad, y, z + Math.sin(a3) * rad, Sprites.smoke(i), life, false, base);
            p3.velocity(vx, vy, vz);
            p3.motion = (pp, age) -> {
                float f = (float)age / (float)life;
                pp.translate(pp.getDeltaX(), pp.getDeltaY(), pp.getDeltaZ());
                pp.scaleVelocity(0.96);
                float warm = Math.max(0.0f, 1.0f - f * 4.0f);
                pp.color(shade + 0.55f * warm, shade + 0.25f * warm, shade);
                pp.alpha(0.75f * (f < 0.1f ? f / 0.1f : 1.0f) * (1.0f - f * f));
                pp.size = base * (0.6f + 1.5f * (float)Math.sqrt(f));
                pp.fullBright = warm > 0.3f;
            };
            this.add(p3);
        }
        BlockPos bp = BlockPos.containing((double)x, (double)(y - 0.5), (double)z);
        BlockState below = level.getBlockState(bp);
        for (int dy = 0; dy < 3 && below.isAir(); ++dy) {
            below = level.getBlockState(bp.below(dy + 1));
        }
        if (!below.isAir()) {
            int nDebris = (int)(Math.min(120.0, 10.0 + 6.0 * r) * (double)q);
            for (int i = 0; i < nDebris; ++i) {
                a = this.rnd.nextDouble() * Math.PI * 2.0;
                sp = 0.15 + 0.35 * this.rnd.nextDouble() * Math.max(0.8, r / 5.0);
                level.addParticle((ParticleOptions)new BlockParticleOption(ParticleTypes.BLOCK, below), x, y + 0.2, z, Math.cos(a) * sp, 0.25 + 0.7 * this.rnd.nextDouble() * Math.max(0.8, r / 6.0), Math.sin(a) * sp);
            }
        }
        if (this.m.surface()) {
            int nRing = (int)(Math.min(60.0, 12.0 + 2.5 * r) * (double)q);
            for (int i = 0; i < nRing; ++i) {
                a = (double)i * (Math.PI * 2 / (double)nRing) + this.rnd.nextDouble() * 0.2;
                sp = 0.22 * Math.max(0.8, r / 4.0);
                int life = 20 + this.rnd.nextInt(14);
                float base = (float)(0.8 + r * 0.3);
                FxParticle p4 = new FxParticle(level, x, y + 0.3, z, Sprites.smoke(i), life, false, base);
                p4.velocity(Math.cos(a) * sp, 0.02, Math.sin(a) * sp);
                p4.motion = (pp, age) -> {
                    float f = (float)age / (float)life;
                    pp.translate(pp.getDeltaX(), pp.getDeltaY(), pp.getDeltaZ());
                    pp.scaleVelocity(0.93);
                    pp.color(0.55f, 0.48f, 0.4f);
                    pp.alpha(0.4f * (1.0f - f));
                    pp.size = base * (1.0f + 1.8f * f);
                };
                this.add(p4);
            }
        }
        // r27: a lingering smoke wall - slowly spreading low cloud that stays for several seconds.
        int nWall = (int)(Math.min(48.0, 10.0 + 4.0 * r) * (double)q);
        for (int i = 0; i < nWall; ++i) {
            double aw = (double)i * (Math.PI * 2 / (double)nWall) + this.rnd.nextDouble() * 0.4;
            double rw = r * (0.3 + 0.5 * this.rnd.nextDouble());
            double sw = 0.012 + 0.02 * this.rnd.nextDouble();
            int life = 160 + this.rnd.nextInt(110);
            float base = (float)(r * (0.8 + 0.7 * this.rnd.nextDouble()));
            FxParticle p5 = new FxParticle(level, x + Math.cos(aw) * rw, y + 0.2, z + Math.sin(aw) * rw, Sprites.smoke(i), life, false, base);
            p5.velocity(Math.cos(aw) * sw, 0.006 + 0.008 * this.rnd.nextDouble(), Math.sin(aw) * sw);
            p5.motion = (pp, age) -> {
                float f = (float)age / (float)life;
                pp.translate(pp.getDeltaX(), pp.getDeltaY(), pp.getDeltaZ());
                pp.scaleVelocity(0.985);
                float in = f < 0.08f ? f / 0.08f : 1.0f;
                double tf = Math.max(0.0, Math.min(1.0, (f - 0.6) / 0.4));
                float out = (float) (1.0 - tf * tf * (3.0 - 2.0 * tf));
                pp.color(0.3f, 0.3f, 0.31f);
                pp.alpha(0.55f * in * out);
                pp.size = base * (0.7f + 1.3f * (float)Math.sqrt(f));
            };
            this.add(p5);
        }
    }

    private void schedule(ClientLevel level, Minecraft mc, Vec3 c, double dist) {
        if (mc.player == null) {
            return;
        }
        double r = Math.cbrt(this.m.massKg());
        double range = 90.0 + 60.0 * r;
        if (dist > range * 1.6) {
            return;
        }
        int delay = (int)(dist / 343.0 * 20.0);
        float vol = (float)CloudModel.clamp(1.3 - dist / range, 0.15, 1.0);
        SoundEvent s = this.m.massKg() < 40.0f ? (SoundEvent)ModSounds.BLAST_SMALL.get() : (this.m.massKg() < 400.0f ? (SoundEvent)ModSounds.BLAST_MEDIUM.get() : (SoundEvent)ModSounds.BLAST_LARGE.get());
        float pitch = (float)(1.05 - 0.06 * Math.log10(Math.max(1.0, (double)this.m.massKg())));
        ClientBlastManager.later(delay, () -> {
            if (mc.player == null || mc.level == null) {
                return;
            }
            Vec3 pp = mc.player.position();
            Vec3 dir = c.subtract(pp);
            double len = dir.length();
            Vec3 pos = len < 6.0 ? c : pp.add(dir.scale(6.0 / len));
            mc.level.playLocalSound(pos.x, pos.y, pos.z, s, SoundSource.AMBIENT, vol, pitch, false);
            double p = BlastPhysics.overpressure(Math.max(1.0, dist), this.m.massKg(), this.m.surface());
            if (p > 3.0) {
                ClientBlastManager.shake(mc.level, (float)CloudModel.clamp(Math.sqrt(p) * 0.25, 0.2, 5.0), 18.0f + (float)r);
            }
        });
        double fluence = BlastPhysics.thermalFluence((double)this.m.massKg() / 1000000.0 * 0.3, Math.max(1.0, dist));
        if (fluence > 0.6 && ((Boolean)RWConfig.CLIENT.flashEffect.get()).booleanValue()) {
            ClientBlastManager.flash(level, (float)CloudModel.clamp(fluence / 4.0, 0.0, 0.5));
        }
    }
}

