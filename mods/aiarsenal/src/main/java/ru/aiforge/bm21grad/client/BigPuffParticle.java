package ru.aiforge.bm21grad.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Large soft billboard puff. Three flavours share one sprite set (procedurally generated textures, tools/make_particles.py):
 * SMOKE_WALL - white-grey, 3.5-6 blocks, 12-16 s, almost static (smoke screen);
 * FIREBALL   - yellow -> orange -> dark red, fullbright, 0.6-1 s, fast expansion;
 * BLAST_SMOKE- dark grey-brown, rises and spreads, 4-7 s.
 */
public class BigPuffParticle extends TextureSheetParticle {
    public enum Kind { SMOKE_WALL, FIREBALL, BLAST_SMOKE }

    private final Kind kind;
    private final SpriteSet sprites;
    private final float size0, size1;
    private final float spin;

    protected BigPuffParticle(ClientLevel lv, double x, double y, double z, double vx, double vy, double vz, Kind kind, SpriteSet sprites) {
        super(lv, x, y, z);
        this.kind = kind;
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = false;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        this.spin = (random.nextFloat() - 0.5f) * 0.02f;
        switch (kind) {
            case SMOKE_WALL -> {
                lifetime = 240 + random.nextInt(80);
                size0 = 2.0f + random.nextFloat();
                size1 = 3.5f + random.nextFloat() * 2.5f;
                float g = 0.78f + random.nextFloat() * 0.16f;
                setColor(g, g, g * 0.97f);
                friction = 0.96f;
                gravity = -0.0005f;
            }
            case FIREBALL -> {
                lifetime = 12 + random.nextInt(9);
                size0 = 0.6f + random.nextFloat() * 0.6f;
                size1 = 2.2f + random.nextFloat() * 1.6f;
                setColor(1f, 0.95f, 0.7f);
                friction = 0.82f;
                gravity = -0.01f;
            }
            default -> {
                lifetime = 80 + random.nextInt(60);
                size0 = 1.2f + random.nextFloat();
                size1 = 3.5f + random.nextFloat() * 2.5f;
                float g = 0.12f + random.nextFloat() * 0.14f;
                setColor(g * 1.05f, g, g * 0.92f);
                friction = 0.93f;
                gravity = -0.004f;
            }
        }
        alpha = kind == Kind.FIREBALL ? 0.95f : 0.0f;
        quadSize = size0;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        oRoll = roll;
        roll += spin;
        float t = (float) age / lifetime;
        quadSize = Mth.lerp(1f - (1f - t) * (1f - t), size0, size1);
        switch (kind) {
            case SMOKE_WALL -> alpha = Math.min(1f, age / 25f) * (t > 0.7f ? (1f - t) / 0.3f : 1f) * 0.85f;
            case FIREBALL -> {
                // white-yellow core cools to orange, then to smoky dark red
                float r = 1f, g = Mth.lerp(t, 0.95f, 0.25f), b = Mth.lerp(Math.min(1f, t * 2f), 0.7f, 0.05f);
                if (t > 0.65f) { float k = (t - 0.65f) / 0.35f; r = Mth.lerp(k, 1f, 0.35f); g *= 1f - 0.6f * k; }
                setColor(r, g, b);
                alpha = t < 0.6f ? 0.95f : 0.95f * (1f - (t - 0.6f) / 0.4f);
            }
            default -> alpha = Math.min(1f, age / 6f) * (1f - t) * 0.9f;
        }
        setSpriteFromAge(sprites);
    }

    @Override
    protected int getLightColor(float pt) {
        return kind == Kind.FIREBALL ? 0xF000F0 : super.getLightColor(pt);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites, Kind kind) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel lv, double x, double y, double z, double vx, double vy, double vz) {
            if (kind != Kind.FIREBALL && ru.aiforge.arsenal.volumetric.client.VolumetricEffects.smoke(x, y, z, 3f, 8f)) return null;
            return new BigPuffParticle(lv, x, y, z, vx, vy, vz, kind, sprites);
        }
    }
}
