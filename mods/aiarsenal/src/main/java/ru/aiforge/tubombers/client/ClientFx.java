package ru.aiforge.tubombers.client;

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
import ru.aiforge.tubombers.network.FxPacket;
import ru.aiforge.tubombers.registry.ModRegistry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client explosion effects: the sound arrives with the speed of sound (343 m/s = 17.15 blocks/tick),
 * near/far variants, camera shake with distance falloff, ejecta of the actual ground block, fireball,
 * dust column and shock ring scaled with the bomb.
 */
public final class ClientFx {
    private static final List<Pending> PENDING = new ArrayList<>();
    private static float shake;

    private record Pending(int[] ticks, SoundEvent sound, double x, double y, double z, float volume, float pitch, float shake) {
    }

    private ClientFx() {
    }

    public static void addShake(float s) {
        shake = Math.min(8f, shake + s);
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
        float power = p.power();
        SoundEvent snd = dist < 140 ? ModRegistry.SND_EXPLOSION_NEAR.get() : ModRegistry.SND_EXPLOSION_FAR.get();
        float vol = (float) Math.min(1.0, (power / 4.0) * 90.0 / (dist + 30.0));
        float pitch = 0.95f - power * 0.03f + lv.random.nextFloat() * 0.12f - (float) Math.min(0.25, dist / 2500.0);
        float sh = (float) (power * 1.4 / (1 + dist / 18.0));
        if (vol > 0.01f) PENDING.add(new Pending(new int[]{delay}, snd, p.x(), p.y(), p.z(), vol, Math.max(0.5f, pitch), sh));
        if (dist < mc.options.getEffectiveRenderDistance() * 16 + 64) particles(lv, p);
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
            Vec3 dir = new Vec3(pd.x, pd.y, pd.z).subtract(cam);
            Vec3 at = dir.lengthSqr() > 1e-4 ? cam.add(dir.normalize().scale(Math.min(8, dir.length()))) : cam;
            mc.getSoundManager().play(new SimpleSoundInstance(pd.sound.getLocation(), SoundSource.HOSTILE, pd.volume, pd.pitch,
                    RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false));
            addShake(pd.shake);
        }
    }

    private static void particles(ClientLevel lv, FxPacket p) {
        if (ru.aiforge.arsenal.volumetric.client.VolumetricEffects.blast(p.x(), p.y(), p.z(), p.power(), lv.getGameTime())) return;
        RandomSource r = lv.random;
        double x = p.x(), y = p.y(), z = p.z();
        float pw = p.power();
        BlockState ground = lv.getBlockState(BlockPos.containing(x, y - 1, z));
        if (ground.isAir()) ground = lv.getBlockState(BlockPos.containing(x, y - 2, z));
        if (ground.isAir() || !ground.getFluidState().isEmpty()) ground = Blocks.DIRT.defaultBlockState();
        ParticleOptions dirt = new BlockParticleOption(ParticleTypes.BLOCK, ground);
        boolean crash = p.kind() == FxPacket.CRASH;
        float k = switch (p.kind()) {
            case FxPacket.LIGHT -> 1f;
            case FxPacket.MEDIUM -> 1.5f;
            case FxPacket.HEAVY -> 2.6f;
            default -> 1.8f;
        };
        lv.addAlwaysVisibleParticle(ParticleTypes.FLASH, true, x, y + 1, z, 0, 0, 0);
        lv.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION_EMITTER, true, x, y + 0.5, z, 0, 0, 0);
        // ejecta of the real ground material
        for (int i = 0; i < 90 * k; i++)
            lv.addAlwaysVisibleParticle(dirt, true, x + r.nextGaussian() * pw * 0.2, y + 0.3, z + r.nextGaussian() * pw * 0.2,
                    r.nextGaussian() * 0.4 * k, 0.5 + r.nextDouble() * 1.1 * k, r.nextGaussian() * 0.4 * k);
        // dust column
        for (int i = 0; i < 34 * k; i++)
            lv.addAlwaysVisibleParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), true, x + r.nextGaussian() * pw * 0.35, y + r.nextDouble() * 2 * k,
                    z + r.nextGaussian() * pw * 0.35, r.nextGaussian() * 0.03, 0.08 + r.nextDouble() * 0.14 * k, r.nextGaussian() * 0.03);
        // shock ring along the ground
        for (int i = 0; i < 60; i++) {
            double a = i / 60.0 * Math.PI * 2;
            lv.addAlwaysVisibleParticle(ParticleTypes.POOF, true, x, y + 0.3, z, Math.cos(a) * 0.9 * k, 0.02, Math.sin(a) * 0.9 * k);
        }
        for (int i = 0; i < 30 * k; i++)
            lv.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, x, y + 0.5, z, 0, 0, 0);
        // fireball + rising blast smoke (large procedural billboards)
        float rad = 1.2f * pw / 4.0f * k;
        for (int i = 0; i < 26 * k; i++) {
            double u = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
            double vx = Math.cos(a) * s, vy = Math.abs(u) * 0.7 + 0.2, vz = Math.sin(a) * s;
            double sp = 0.15 + r.nextDouble() * 0.3 * k;
            lv.addAlwaysVisibleParticle(ModRegistry.P_FIREBALL.get(), true, x + vx * rad * 0.4, y + 0.8 + vy * rad * 0.4, z + vz * rad * 0.4,
                    vx * sp, vy * sp, vz * sp);
        }
        for (int i = 0; i < 18 * k; i++)
            lv.addAlwaysVisibleParticle(ModRegistry.P_BLAST_SMOKE.get(), true, x + r.nextGaussian() * rad * 0.5, y + 1 + r.nextDouble() * rad * 1.5,
                    z + r.nextGaussian() * rad * 0.5, r.nextGaussian() * 0.06, 0.1 + r.nextDouble() * 0.14, r.nextGaussian() * 0.06);
        for (int i = 0; i < 40 * k; i++)
            lv.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, x, y + 0.5, z, r.nextGaussian() * 0.4, 0.3 + r.nextDouble() * 0.6, r.nextGaussian() * 0.4);
        if (crash) {
            for (int i = 0; i < 60; i++)
                lv.addAlwaysVisibleParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), true, x + r.nextGaussian() * 4, y + 1 + r.nextDouble() * 3, z + r.nextGaussian() * 4,
                        0, 0.12, 0);
        }
    }
}
