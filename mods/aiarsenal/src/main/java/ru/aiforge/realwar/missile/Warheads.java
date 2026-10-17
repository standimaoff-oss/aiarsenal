/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.BlockPos$MutableBlockPos
 *  net.minecraft.core.particles.ParticleOptions
 *  net.minecraft.core.particles.ParticleTypes
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.util.Mth
 *  net.minecraft.util.RandomSource
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.levelgen.Heightmap$Types
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 */
package ru.aiforge.realwar.missile;

import ru.aiforge.realwar.blast.ConventionalBlast;
import ru.aiforge.realwar.missile.MissileType;
import ru.aiforge.realwar.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Warheads {
    private Warheads() {
    }

    public static void thermobaric(ServerLevel sl, Vec3 pos, double massKg) {
        double r = Math.cbrt(massKg) * 4.4;
        ConventionalBlast.explode(sl, pos, massKg * 0.75, 0.0);
        RandomSource rnd = sl.random;
        sl.playSound(null, pos.x, pos.y, pos.z, (SoundEvent)ModSounds.BLAST_LARGE.get(), SoundSource.HOSTILE, 8.0f, 0.6f);
        sl.sendParticles((ParticleOptions)ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 2.0, pos.z, 3, r * 0.15, 1.0, r * 0.15, 0.0);
        sl.sendParticles((ParticleOptions)ParticleTypes.FLAME, pos.x, pos.y + 2.0, pos.z, 400, r * 0.35, r * 0.2, r * 0.35, 0.12);
        for (Entity e : sl.getEntities((Entity)null, new AABB(pos, pos).inflate(r), en -> en instanceof LivingEntity)) {
            double d = e.position().distanceTo(pos);
            if (d > r) continue;
            double f = 1.0 - d / r;
            e.setSecondsOnFire((int)(4.0 + 12.0 * f));
            e.hurt(sl.damageSources().explosion(null, null), (float)(8.0 + 60.0 * f * f));
        }
        int n = (int)Math.min(900.0, r * r * 0.9);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; ++i) {
            int z;
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double rr = Math.sqrt(rnd.nextDouble()) * r * 0.85;
            int x = Mth.floor((double)(pos.x + Math.cos(a) * rr));
            if (!sl.hasChunk(x >> 4, (z = Mth.floor((double)(pos.z + Math.sin(a) * rr))) >> 4)) continue;
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            mp.set(x, y, z);
            BlockState below = sl.getBlockState(mp.below());
            if (!sl.getBlockState((BlockPos)mp).isAir() || !below.isSolidRender((BlockGetter)sl, mp.below()) || rnd.nextInt(3) != 0) continue;
            sl.setBlockAndUpdate((BlockPos)mp, Blocks.FIRE.defaultBlockState());
        }
    }

    public static Vec3 dig(ServerLevel sl, Vec3 pos) {
        int depth;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int bx = Mth.floor((double)pos.x);
        int bz = Mth.floor((double)pos.z);
        int y = Mth.floor((double)pos.y);
        for (depth = 0; depth < 20; ++depth) {
            boolean stop = false;
            for (int dx = -1; dx <= 0; ++dx) {
                for (int dz = -1; dz <= 0; ++dz) {
                    mp.set(bx + dx, y - depth, bz + dz);
                    BlockState s = sl.getBlockState((BlockPos)mp);
                    if (s.isAir()) continue;
                    if (s.getDestroySpeed((BlockGetter)sl, (BlockPos)mp) < 0.0f || s.getDestroySpeed((BlockGetter)sl, (BlockPos)mp) > 40.0f) {
                        stop = true;
                        continue;
                    }
                    sl.setBlock((BlockPos)mp, Blocks.AIR.defaultBlockState(), 2);
                }
            }
            if (stop) break;
        }
        sl.sendParticles((ParticleOptions)ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), pos.x, pos.y + 1.0, pos.z, 30, 0.6, 0.6, 0.6, 0.1);
        sl.playSound(null, pos.x, pos.y, pos.z, (SoundEvent)ModSounds.BLAST_SMALL.get(), SoundSource.HOSTILE, 4.0f, 0.6f);
        return new Vec3(pos.x, (double)(y - Math.max(1, depth - 1)) + 1.5, pos.z);
    }

    /** Spread the declared payload over the release window without rounding it
     * into one extra submunition per late hidden tick (48 previously became 50). */
    public static int clusterReleases(int tick, int payload) {
        if (tick <= 0 || tick > 45 || payload <= 0) return 0;
        return (int)((long)tick * payload / 45 - (long)(tick - 1) * payload / 45);
    }

    public static void clusterTick(ServerLevel sl, MissileType type, Vec3 center, int tick, double bombKg) {
        RandomSource rnd = sl.random;
        if (tick == 1) {
            sl.playSound(null, center.x, center.y, center.z, (SoundEvent)ModSounds.BLAST_SMALL.get(), SoundSource.HOSTILE, 3.0f, 1.4f);
            sl.sendParticles((ParticleOptions)ParticleTypes.FIREWORK, center.x, center.y + 30.0, center.z, 40, 3.0, 3.0, 3.0, 0.15);
        }
        int per = clusterReleases(tick, type.warheads);
        for (int i = 0; i < per; ++i) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(rnd.nextDouble()) * type.footprint;
            double x = center.x + Math.cos(a) * r;
            double z = center.z + Math.sin(a) * r;
            if (!sl.hasChunk(Mth.floor((double)x) >> 4, Mth.floor((double)z) >> 4)) continue;
            double y = (double)sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor((double)x), Mth.floor((double)z)) + 0.3;
            sl.sendParticles((ParticleOptions)ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), x, y + 14.0, z, 3, 0.1, 6.0, 0.1, 0.0);
            ConventionalBlast.explode(sl, new Vec3(x, y, z), bombKg, 1.3);
        }
    }

    public static void backblast(ServerLevel sl, Vec3 pad, int level) {
        double r = 4.0 + 4.5 * (double)level;
        for (Entity e : sl.getEntities((Entity)null, new AABB(pad.add(0.0, -1.0, 0.0), pad.add(0.0, (double)(10 + 4 * level), 0.0)).inflate(r, 0.0, r), en -> en instanceof LivingEntity)) {
            double d = e.position().distanceTo(pad);
            double f = Math.max(0.0, 1.0 - d / (r * 1.4));
            if (f <= 0.0) continue;
            e.setSecondsOnFire((int)(3.0 + 6.0 * f));
            e.hurt(sl.damageSources().inFire(), (float)(4.0 + 26.0 * f));
        }
    }

    public static boolean isLevel(Level l) {
        return l != null;
    }
}

