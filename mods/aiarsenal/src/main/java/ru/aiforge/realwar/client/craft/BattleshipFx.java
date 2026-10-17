package ru.aiforge.realwar.client.craft;

import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.client.fx.TrailFx;
import ru.aiforge.realwar.client.particle.FxParticle;
import ru.aiforge.realwar.client.particle.FxSystem;
import ru.aiforge.realwar.client.particle.Sprites;
import ru.aiforge.realwar.craft.BattleshipEntity;

/** Client effects of the Bismarck's guns (reply 10): 38 cm / 15 cm discharge, flak flashes, catapult launch. */
public final class BattleshipFx {
    private static final Random R = new Random();

    private BattleshipFx() {}

    /** @return true when the code belonged to the battleship. */
    public static boolean event(BattleshipEntity b, int code) {
        ClientLevel level = (ClientLevel) b.level();
        if (code >= BattleshipEntity.EV_MAIN && code < BattleshipEntity.EV_SEC) {
            int t = (code - BattleshipEntity.EV_MAIN) / 2, g = code % 2;
            gun(level, b, t, g, true);
            b.recoil[t * 2 + g] = 1f;
            return true;
        }
        if (code >= BattleshipEntity.EV_SEC && code < BattleshipEntity.EV_H) {
            int k = code - BattleshipEntity.EV_SEC, s = k / 2, g = k % 2;
            gun(level, b, BattleshipEntity.I_SEC + s, g, false);
            b.recoil[8 + s * 2 + g] = 1f;
            return true;
        }
        if (code >= BattleshipEntity.EV_H && code < BattleshipEntity.EV_F) {
            int i = BattleshipEntity.I_H + code - BattleshipEntity.EV_H;
            for (int g = 0; g < BattleshipEntity.M[i].guns().length; g++) flak(level, b, i, g, 1.6f);
            return true;
        }
        if (code >= BattleshipEntity.EV_F && code < BattleshipEntity.EV_F + 8) {
            int i = BattleshipEntity.I_F + code - BattleshipEntity.EV_F;
            flak(level, b, i, R.nextInt(2), 0.8f);
            return true;
        }
        if (code == BattleshipEntity.EV_CAT) {
            Vec3 c = b.localToWorld(0, 21.6, -10);
            for (int i = 0; i < 18; i++) puff(level, c.add(R.nextGaussian() * 2, R.nextGaussian() * 0.4, R.nextGaussian() * 2), 1.5f, 40, 0.8f);
            return true;
        }
        return false;
    }

    private static Vec3 dirOf(BattleshipEntity b, int i) {
        Vec3 l = BattleshipEntity.localDir(b.az[i], b.el[i]);
        return b.localDirToWorld(l.x, l.y, l.z);
    }

    private static void gun(ClientLevel level, BattleshipEntity b, int i, int g, boolean main) {
        Vec3 ml = BattleshipEntity.muzzleLocal(i, g, b.az[i], b.el[i], 0);
        Vec3 m = b.localToWorld(ml.x, ml.y, ml.z);
        Vec3 dir = dirOf(b, i);
        float s = main ? 3.4f : 1.6f;
        // fireball: a chain of flashes along the line of fire
        for (int k = 0; k < (main ? 6 : 3); k++) {
            Vec3 p = m.add(dir.scale(k * s * 1.4));
            FxParticle f = new FxParticle(level, p.x, p.y, p.z, Sprites.flare(), 4, true, s * (2.2f - k * 0.25f));
            f.fullBright = true;
            f.motion = (pp, age) -> {
                pp.color(1.0f, 0.72f, 0.35f);
                pp.alpha(1.0f - age / 4.0f);
            };
            FxSystem.add(f);
        }
        TrailFx.muzzleFlash(level, m, dir);
        // brown cordite cloud rolling forward, then drifting
        int n = main ? 22 : 8;
        for (int k = 0; k < n; k++) {
            double d = R.nextDouble() * s * 6;
            Vec3 p = m.add(dir.scale(d)).add(R.nextGaussian() * s * 0.6, R.nextGaussian() * s * 0.4, R.nextGaussian() * s * 0.6);
            FxParticle c = new FxParticle(level, p.x, p.y, p.z, Sprites.smoke(R.nextInt(4)), main ? 140 : 70, false, s);
            double sp = (1 - d / (s * 6)) * (main ? 0.8 : 0.4);
            c.velocity(dir.x * sp, dir.y * sp + 0.02, dir.z * sp);
            int life = main ? 140 : 70;
            float base = s;
            c.motion = (pp, age) -> {
                float ff = age / (float) life;
                pp.translate(pp.getDeltaX(), pp.getDeltaY() + 0.01, pp.getDeltaZ());
                pp.scaleVelocity(0.9);
                pp.size = base * (1.0f + 3.5f * ff);
                pp.color(0.62f, 0.57f, 0.5f);
                pp.alpha(0.55f * (1 - ff));
            };
            FxSystem.add(c);
        }
        if (main) {
            // blast pressure flattening the sea beside the hull
            for (int k = 0; k < 14; k++) {
                Vec3 p = m.add(dir.scale(4 + R.nextDouble() * 12));
                level.addParticle(ParticleTypes.SPLASH, p.x + R.nextGaussian() * 3, b.getY() + b.kind.water.draft() + 0.3, p.z + R.nextGaussian() * 3, 0, 0.3, 0);
            }
        }
    }

    private static void flak(ClientLevel level, BattleshipEntity b, int i, int g, float size) {
        Vec3 ml = BattleshipEntity.muzzleLocal(i, g, b.az[i], b.el[i], 0);
        Vec3 m = b.localToWorld(ml.x, ml.y, ml.z);
        Vec3 dir = dirOf(b, i);
        FxParticle f = new FxParticle(level, m.x + dir.x * 0.5, m.y + dir.y * 0.5, m.z + dir.z * 0.5, Sprites.flare(), 2, true, size);
        f.fullBright = true;
        f.motion = (pp, age) -> {
            pp.color(1.0f, 0.8f, 0.45f);
            pp.alpha(1.0f - age / 2.0f);
        };
        FxSystem.add(f);
        if (R.nextInt(3) == 0) puff(level, m, size * 0.6f, 30, 0.4f);
    }

    private static void puff(ClientLevel level, Vec3 p, float size, int life, float alpha) {
        FxParticle c = new FxParticle(level, p.x, p.y, p.z, Sprites.smoke(R.nextInt(4)), life, false, size);
        c.velocity(R.nextGaussian() * 0.03, 0.03, R.nextGaussian() * 0.03);
        c.motion = (pp, age) -> {
            float ff = age / (float) life;
            pp.translate(pp.getDeltaX(), pp.getDeltaY(), pp.getDeltaZ());
            pp.scaleVelocity(0.93);
            pp.size = size * (1 + 2.5f * ff);
            pp.color(0.8f, 0.8f, 0.8f);
            pp.alpha(alpha * (1 - ff));
        };
        FxSystem.add(c);
    }
}
