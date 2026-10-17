package ru.aiforge.bm21grad.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.bm21grad.network.FxPacket;
import ru.aiforge.bm21grad.registry.ModRegistry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client explosion effects: the sound arrives with the speed of sound (343 m/s = 17.15 blocks/tick),
 * near/far variants, camera shake with distance falloff, ejecta of the actual ground block,
 * smoke column and shock ring.
 */
public final class ClientFx {
    private static final List<Pending> PENDING = new ArrayList<>();
    private static float shake;

    private record Pending(int[] ticks, SoundEvent sound, double x, double y, double z, float volume, float pitch, float shake) {
    }

    private ClientFx() {
    }

    public static void addShake(float s) {
        shake = Math.min(7f, shake + s);
    }

    public static float shake() {
        return shake;
    }

    public static void explosion(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double dist = cam.distanceTo(new Vec3(p.x(), p.y(), p.z()));
        int delay = (int) (dist / 17.15);
        boolean small = p.kind() == FxPacket.BOMBLET || p.kind() == FxPacket.CLUSTER;
        float power = p.power();
        SoundEvent snd = dist < 110 ? ModRegistry.SND_EXPLOSION_NEAR.get() : ModRegistry.SND_EXPLOSION_FAR.get();
        float vol = (float) Math.min(1.0, (power / 4.0) * 60.0 / (dist + 30.0)) * (small ? 0.6f : 1f);
        float pitch = (small ? 1.25f : 0.9f) + lv.random.nextFloat() * 0.15f - (float) Math.min(0.25, dist / 2000.0);
        float sh = (float) (power * 1.6 / (1 + dist / 14.0));
        if (vol > 0.01f) PENDING.add(new Pending(new int[]{delay}, snd, p.x(), p.y(), p.z(), vol, pitch, sh));
        if (dist < mc.options.getEffectiveRenderDistance() * 16 + 32) particles(lv, p, dist);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        shake *= 0.86f;
        if (shake < 0.01f) shake = 0;
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending pd = it.next();
            if (--pd.ticks[0] > 0) continue;
            it.remove();
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            // play from the direction of the blast but at a fixed short distance, volume computed above
            Vec3 dir = new Vec3(pd.x, pd.y, pd.z).subtract(cam);
            Vec3 at = dir.lengthSqr() > 1e-4 ? cam.add(dir.normalize().scale(Math.min(8, dir.length()))) : cam;
            mc.getSoundManager().play(new SimpleSoundInstance(pd.sound.getLocation(), SoundSource.HOSTILE, pd.volume, pd.pitch,
                    RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false));
            addShake(pd.shake);
        }
    }

    private static void particles(ClientLevel lv, FxPacket p, double dist) {
        if (ru.aiforge.arsenal.volumetric.client.VolumetricEffects.active()) {
            if (p.kind() == FxPacket.SMOKE) ru.aiforge.arsenal.volumetric.client.VolumetricEffects.smoke(p.x(), p.y(), p.z(), 5, 15);
            else ru.aiforge.arsenal.volumetric.client.VolumetricEffects.blast(p.x(), p.y(), p.z(), p.power(), lv.getGameTime());
            return;
        }
        RandomSource r = lv.random;
        double x = p.x(), y = p.y(), z = p.z();
        float pw = p.power();
        BlockState ground = lv.getBlockState(BlockPos.containing(x, y - 1, z));
        if (ground.isAir()) ground = lv.getBlockState(BlockPos.containing(x, y - 2, z));
        if (ground.isAir() || !ground.getFluidState().isEmpty()) ground = Blocks.DIRT.defaultBlockState();
        ParticleOptions dirt = new BlockParticleOption(ParticleTypes.BLOCK, ground);
        switch (p.kind()) {
            case FxPacket.HE, FxPacket.BLAST -> {
                int k = p.kind() == FxPacket.BLAST ? 2 : 1;
                lv.addAlwaysVisibleParticle(ParticleTypes.FLASH, true, x, y + 1, z, 0, 0, 0);
                lv.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION_EMITTER, true, x, y + 0.5, z, 0, 0, 0);
                for (int i = 0; i < 70 * k; i++)
                    lv.addAlwaysVisibleParticle(dirt, true, x + r.nextGaussian() * 0.8, y + 0.3, z + r.nextGaussian() * 0.8,
                            r.nextGaussian() * 0.35, 0.4 + r.nextDouble() * 0.9, r.nextGaussian() * 0.35);
                for (int i = 0; i < 26 * k; i++)
                    lv.addAlwaysVisibleParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), true, x + r.nextGaussian() * pw * 0.35, y + r.nextDouble() * 2, z + r.nextGaussian() * pw * 0.35,
                            r.nextGaussian() * 0.02, 0.06 + r.nextDouble() * 0.1, r.nextGaussian() * 0.02);
                for (int i = 0; i < 40; i++) {
                    double a = i / 40.0 * Math.PI * 2;
                    lv.addAlwaysVisibleParticle(ParticleTypes.POOF, true, x, y + 0.3, z, Math.cos(a) * 0.75 * k, 0.02, Math.sin(a) * 0.75 * k);
                }
                for (int i = 0; i < 24; i++)
                    lv.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, x, y + 0.5, z, 0, 0, 0);
                // fireball + rising blast smoke: large procedural billboards (BigPuffParticle)
                float rad = 1.1f * pw / 4.2f * k;
                for (int i = 0; i < 26 * k; i++) {
                    double u = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
                    double vx = Math.cos(a) * s, vy = Math.abs(u) * 0.7 + 0.2, vz = Math.sin(a) * s;
                    double sp = 0.15 + r.nextDouble() * 0.25 * k;
                    lv.addAlwaysVisibleParticle(ModRegistry.P_FIREBALL.get(), true, x + vx * rad * 0.4, y + 0.8 + vy * rad * 0.4, z + vz * rad * 0.4,
                            vx * sp, vy * sp, vz * sp);
                }
                for (int i = 0; i < 16 * k; i++)
                    lv.addAlwaysVisibleParticle(ModRegistry.P_BLAST_SMOKE.get(), true, x + r.nextGaussian() * rad * 0.5, y + 1 + r.nextDouble() * rad, z + r.nextGaussian() * rad * 0.5,
                            r.nextGaussian() * 0.06, 0.08 + r.nextDouble() * 0.12, r.nextGaussian() * 0.06);
                for (int i = 0; i < 30 * k; i++)
                    lv.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, x, y + 0.5, z, r.nextGaussian() * 0.35, 0.3 + r.nextDouble() * 0.5, r.nextGaussian() * 0.35);
            }
            case FxPacket.INCENDIARY -> {
                lv.addAlwaysVisibleParticle(ParticleTypes.FLASH, true, x, y + 1, z, 0, 0, 0);
                for (int i = 0; i < 14; i++)
                    lv.addAlwaysVisibleParticle(ModRegistry.P_FIREBALL.get(), true, x + r.nextGaussian() * 1.5, y + 0.6, z + r.nextGaussian() * 1.5,
                            r.nextGaussian() * 0.12, 0.05 + r.nextDouble() * 0.15, r.nextGaussian() * 0.12);
                lv.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION, true, x, y + 0.5, z, 0, 0, 0);
                for (int i = 0; i < 90; i++) {
                    double a = r.nextDouble() * Math.PI * 2, s = 0.2 + r.nextDouble() * 0.6;
                    lv.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, x, y + 0.5, z, Math.cos(a) * s, 0.2 + r.nextDouble() * 0.5, Math.sin(a) * s);
                }
                for (int i = 0; i < 40; i++)
                    lv.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, x + r.nextGaussian() * 3, y + 0.5, z + r.nextGaussian() * 3, 0, 0, 0);
                for (int i = 0; i < 20; i++)
                    lv.addAlwaysVisibleParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), true, x + r.nextGaussian() * 2, y + 1, z + r.nextGaussian() * 2, 0, 0.1, 0);
            }
            case FxPacket.SMOKE -> {
                for (int i = 0; i < 30; i++)
                    lv.addAlwaysVisibleParticle(ModRegistry.P_SMOKE_WALL.get(), true, x + r.nextGaussian() * 1.5, y + 0.8 + r.nextDouble() * 1.5, z + r.nextGaussian() * 1.5,
                            r.nextGaussian() * 0.25, 0.02 + r.nextDouble() * 0.05, r.nextGaussian() * 0.25);
                for (int i = 0; i < 50; i++)
                    lv.addAlwaysVisibleParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), true, x + r.nextGaussian() * 2, y + 0.5, z + r.nextGaussian() * 2,
                            r.nextGaussian() * 0.08, 0.05 + r.nextDouble() * 0.05, r.nextGaussian() * 0.08);
            }
            case FxPacket.CLUSTER -> {
                lv.addAlwaysVisibleParticle(ParticleTypes.FLASH, true, x, y, z, 0, 0, 0);
                for (int i = 0; i < 6; i++)
                    lv.addAlwaysVisibleParticle(ModRegistry.P_FIREBALL.get(), true, x, y, z, r.nextGaussian() * 0.15, r.nextGaussian() * 0.1, r.nextGaussian() * 0.15);
                for (int i = 0; i < 8; i++)
                    lv.addAlwaysVisibleParticle(ModRegistry.P_BLAST_SMOKE.get(), true, x + r.nextGaussian(), y, z + r.nextGaussian(), r.nextGaussian() * 0.05, 0.02, r.nextGaussian() * 0.05);
                for (int i = 0; i < 30; i++)
                    lv.addAlwaysVisibleParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), true, x + r.nextGaussian(), y, z + r.nextGaussian(),
                            r.nextGaussian() * 0.15, r.nextGaussian() * 0.05, r.nextGaussian() * 0.15);
            }
            case FxPacket.BOMBLET -> {
                lv.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION, true, x, y + 0.3, z, 0, 0, 0);
                for (int i = 0; i < 3; i++)
                    lv.addAlwaysVisibleParticle(ModRegistry.P_FIREBALL.get(), true, x, y + 0.5, z, r.nextGaussian() * 0.08, 0.06, r.nextGaussian() * 0.08);
                lv.addAlwaysVisibleParticle(ModRegistry.P_BLAST_SMOKE.get(), true, x, y + 0.8, z, 0, 0.05, 0);
                for (int i = 0; i < 16; i++)
                    lv.addAlwaysVisibleParticle(dirt, true, x, y + 0.2, z, r.nextGaussian() * 0.25, 0.3 + r.nextDouble() * 0.5, r.nextGaussian() * 0.25);
                for (int i = 0; i < 5; i++)
                    lv.addAlwaysVisibleParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), true, x + r.nextGaussian() * 0.6, y + 0.4, z + r.nextGaussian() * 0.6, 0, 0.05, 0);
            }
            default -> {
            }
        }
    }
}
