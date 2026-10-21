package ru.aiforge.realwar.client.fx;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import ru.aiforge.realwar.client.particle.FxParticle;
import ru.aiforge.realwar.client.particle.FxSystem;
import ru.aiforge.realwar.client.particle.Sprites;
import ru.aiforge.realwar.handheld.GunKind;

/**
 * Muzzle blast of small arms, drawn for <em>everybody</em>: the shooter's own client and, through
 * {@code PlayerGunStatePacket}, the players watching him.
 *
 * <p>Built only from the mod's own billboard particles, which fall back to the vanilla particle engine whenever a
 * shaderpack owns the render targets (see {@code ShaderPackCompatibility}) — so Photon/Oculus, Iris and vanilla all
 * draw the same flash: additive core, fast sparks, then a cordite cloud that rolls forward, expands and drifts up.
 * Black powder (flintlocks) gets a bigger, whiter, longer-lived cloud than smokeless nitro powder.</p>
 */
public final class MuzzleFx {
    private static final Random R = new Random();

    private MuzzleFx() {
    }

    /** Muzzle blast for a remote shooter; coordinates are camera relative. */
    public static void remote(Entity shooter, float mx, float my, float mz, Vector3f dir, GunKind k, int seq) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        if (shooter == mc.player) return;                       // own flash is part of the first-person view model
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        blast(level, new Vec3(cam.x + mx, cam.y + my, cam.z + mz), new Vec3(dir.x, dir.y, dir.z), k, seq);
    }

    /** Muzzle blast at a world point (also used for the local player's third-person view and for AI soldiers). */
    public static void blast(ClientLevel level, Vec3 muzzle, Vec3 dir, GunKind k, int seq) {
        if (level == null || muzzle == null || dir == null) return;
        Vec3 d = dir.normalize();
        boolean black = k != null && k.flintlock();
        boolean big = k != null && (k.launcher() || k.manual());
        float scale = k == null ? 1f : k.launcher() ? 2.6f : big ? 1.35f : 1f;
        Random r = new Random(seq * 0x9E3779B1L + 17L);

        // --- additive core: a short-lived star, then two petals around the bore
        int cores = black ? 5 : 3;
        for (int i = 0; i < cores; i++) {
            Vec3 at = muzzle.add(d.scale(0.04 + 0.05 * i)).add(jitter(r, 0.012));
            FxParticle f = new FxParticle(level, at.x, at.y, at.z, Sprites.flare(), 2 + i,
                    true, (0.10f + 0.05f * i) * scale);
            f.fullBright = true;
            f.spin = (r.nextFloat() - 0.5f) * 0.6f;
            float base = (0.11f + 0.05f * i) * scale;
            int life = 2 + i;
            f.motion = (p, age) -> {
                float t = age / (float) life;
                p.color(1.0f, 0.88f - 0.25f * t, 0.62f - 0.4f * t);
                p.alpha(1.0f - t * t);
                p.size = base * (1.0f + 1.6f * t);
            };
            FxSystem.add(f);
        }

        // --- burning powder grains thrown out of the bore
        int sparks = black ? 10 : 5;
        for (int i = 0; i < sparks; i++) {
            Vec3 at = muzzle.add(d.scale(0.08)).add(jitter(r, 0.02));
            FxParticle s = new FxParticle(level, at.x, at.y, at.z, Sprites.glow(), 5 + r.nextInt(5),
                    true, 0.022f * scale);
            s.fullBright = true;
            Vec3 v = d.scale(0.35 + r.nextDouble() * 0.5).add(jitter(r, 0.22));
            s.velocity(v.x, v.y + 0.02, v.z);
            int life = 5 + r.nextInt(5);
            s.motion = (p, age) -> {
                float t = age / (float) life;
                p.translate(p.getDeltaX(), p.getDeltaY(), p.getDeltaZ());
                p.scaleVelocity(0.86);
                p.alpha(1.0f - t);
                p.color(1.0f, 0.72f, 0.32f);
            };
            FxSystem.add(s);
        }

        // --- the smoke itself is a raymarched volume (mandatory, also under shaderpacks)
        ru.aiforge.arsenal.volumetric.client.VolumetricEffects.smoke(
                muzzle.x + d.x * 0.25, muzzle.y + d.y * 0.25, muzzle.z + d.z * 0.25,
                (black ? 1.5f : 0.8f) * scale, black ? 7f : 3.5f);

        // --- cordite / black-powder cloud: quick forward roll, then slow buoyant drift
        int puffs = black ? 7 : 3;
        float puffSize = (black ? 0.34f : 0.16f) * scale;
        int puffLife = black ? 44 : 26;
        for (int i = 0; i < puffs; i++) {
            Vec3 at = muzzle.add(d.scale(0.05 + r.nextDouble() * 0.18 * scale)).add(jitter(r, 0.03 * scale));
            FxParticle c = new FxParticle(level, at.x, at.y, at.z, Sprites.smoke(r.nextInt(4)), puffLife + r.nextInt(10),
                    false, puffSize * (0.8f + r.nextFloat() * 0.5f));
            c.velocity(d.x * 0.16 + jitter(r, 0.05).x, d.y * 0.16 + 0.012 + jitter(r, 0.04).y,
                    d.z * 0.16 + jitter(r, 0.05).z);
            c.roll(r.nextFloat() * 6.28f);
            c.spin = (r.nextFloat() - 0.5f) * 0.05f;
            float base = c.size;
            int life = puffLife;
            double seed = r.nextDouble() * 6.28;
            c.motion = (p, age) -> {
                float t = age / (float) life;
                // turbulent rise: the puff spreads, slows and climbs as it cools
                p.translate(p.getDeltaX() + Math.sin(seed + age * 0.4) * 0.002,
                        p.getDeltaY() + 0.004 * (0.4 + t), p.getDeltaZ() + Math.cos(seed + age * 0.35) * 0.002);
                p.scaleVelocity(0.93);
                p.size = base * (1.0f + 2.6f * t);
                float grey = black ? 0.86f : 0.66f;
                p.color(grey, grey * 0.98f, grey * 0.95f);
                p.alpha((black ? 0.5f : 0.34f) * (1.0f - t * t));
            };
            FxSystem.add(c);
        }
    }

    private static Vec3 jitter(Random r, double scale) {
        return new Vec3(r.nextGaussian() * scale, r.nextGaussian() * scale, r.nextGaussian() * scale);
    }
}
