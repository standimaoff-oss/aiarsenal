package ru.aiforge.tubombers.fx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.network.PacketDistributor;
import ru.aiforge.tubombers.Config;
import ru.aiforge.tubombers.TuBombers;
import ru.aiforge.tubombers.aircraft.BombType;
import ru.aiforge.tubombers.entity.BomberEntity;
import ru.aiforge.tubombers.network.FxPacket;
import ru.aiforge.tubombers.network.ModNetwork;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Server-side detonation effects. All work is bounded (fixed sample counts, crater volume ~ r^3). */
public final class ImpactHandler {
    /** statistics for tests / status command */
    public static int detonations;
    public static Vec3 lastImpact;
    public static int lastCraterBlocks;

    private ImpactHandler() {
    }

    private static boolean blocks() {
        return Config.BLOCK_DAMAGE.get();
    }

    private static float scale() {
        return Config.EXPLOSION_SCALE.get().floatValue();
    }

    /** r21: blast + fragments of the charge against vehicles through the RealWar HE model. */
    static void vehicles(ServerLevel sl, Vec3 p, double tntKg) {
        if (tntKg <= 0) return;
        double r = 7.0 * Math.cbrt(tntKg) + 8.0;
        for (Entity e : sl.getEntities((Entity) null, new AABB(p, p).inflate(r), x -> x instanceof ru.aiforge.realwar.damage.Damageable && x.isAlive())) {
            ((ru.aiforge.realwar.damage.Damageable) e).blastNear(p, tntKg, e.position().distanceTo(p));
        }
    }

    /** High-explosive bomb: crater, blast wave, fragments, ejecta, scorched ground, broken glass. */
    public static void bomb(ServerLevel sl, Vec3 p, BombType t, Entity direct, Entity owner, Vec3 vel) {
        detonations++;
        lastImpact = p;
        float s = scale();
        DamageSource src = sl.damageSources().explosion(direct, owner);
        lastCraterBlocks = blocks() ? crater(sl, p, t.craterRadius * s, t.craterRadius * s * 0.5f) : 0;
        sl.explode(direct, src, null, p.x, p.y, p.z, t.blastPower * s, false,
                blocks() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        blast(sl, p, t.damageRadius * s, t.maxDamage, direct, owner);
        vehicles(sl, p, t.explosive);                  // r21: HE model against vehicles
        fragments(sl, p, 40 + (int) (t.explosive / 6), t.damageRadius * 1.6 * s, t.maxDamage * 0.25f, direct, owner);
        debris(sl, p, (int) (t.craterRadius * 3), t.craterRadius * s);
        scorch(sl, p, t.craterRadius * 1.6 * s);
        shatter(sl, p, t.damageRadius * 1.8 * s);
        int kind = t == BombType.FAB1500 ? FxPacket.HEAVY : (t == BombType.FAB500 ? FxPacket.MEDIUM : FxPacket.LIGHT);
        fx(sl, p, kind, t.craterRadius * s);
        TuBombers.LOGGER.debug("Bomb {} at {}", t, BlockPos.containing(p));
    }

    /** Aircraft crash: fuel fire and secondary explosions. */
    /**
     * Generic HE warhead (drones / cruise missiles): crater, explosion, overpressure, fragments, scorch, glass.
     *
     * @param fxKind FxPacket LIGHT/MEDIUM/HEAVY
     */
    public static void warhead(ServerLevel sl, Vec3 p, float power, float craterR, double radius, float maxDmg, int frags, int fxKind,
                               Entity direct, Entity owner) {
        detonations++;
        lastImpact = p;
        DamageSource src = sl.damageSources().explosion(direct, owner);
        boolean b = blocks() && ru.aiforge.arsenal.ArsenalConfig.HE_BLOCK_DAMAGE.get();
        lastCraterBlocks = b && craterR > 0.5f ? crater(sl, p, craterR, craterR * 0.5f) : 0;
        sl.explode(direct, src, null, p.x, p.y, p.z, power, false, b ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        blast(sl, p, radius, maxDmg, direct, owner);
        vehicles(sl, p, Math.pow(Math.max(0.5f, power) / 1.6, 3));   // explosion power ~ 1.6 cbrt(TNT kg)
        if (frags > 0) fragments(sl, p, frags, radius * 1.5, maxDmg * 0.3f, direct, owner);
        if (craterR > 0.5f) {
            debris(sl, p, (int) (craterR * 3), craterR);
            scorch(sl, p, craterR * 1.6);
        }
        shatter(sl, p, radius * 1.6);
        fx(sl, p, fxKind, Math.max(1f, craterR));
    }

    public static void crash(ServerLevel sl, Vec3 p, float power, Entity direct) {
        detonations++;
        sl.explode(direct, sl.damageSources().explosion(direct, null), null, p.x, p.y, p.z, power * scale(), Config.BLOCK_DAMAGE.get(),
                blocks() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        fire(sl, p, 20, 9);
        fx(sl, p, FxPacket.CRASH, power);
    }

    // ------------------------------------------------------------------ building blocks
    public static void fx(ServerLevel sl, Vec3 p, int kind, float power) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(p.x, p.y, p.z, 1500, sl.dimension())),
                new FxPacket(p.x, p.y, p.z, kind, power));
    }

    /**
     * Bowl-shaped crater (paraboloid of revolution), radius r and depth d; blocks harder than obsidian survive,
     * block entities keep their contents (they are dropped).
     */
    public static int crater(ServerLevel sl, Vec3 p, float r, float d) {
        RandomSource rnd = sl.random;
        int removed = 0;
        int R = Mth.ceil(r) + 1;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        double cy = p.y + d * 0.25; // the charge is buried a little by the impact
        for (int dx = -R; dx <= R; dx++)
            for (int dz = -R; dz <= R; dz++) {
                double h2 = (dx * dx + dz * dz) / (double) (r * r);
                if (h2 > 1.15) continue;
                double depth = d * (1 - h2) + (rnd.nextDouble() - 0.5) * 0.6;
                int top = Mth.floor(cy + 2.5 * (1 - h2) + 1);
                int bottom = Mth.floor(cy - depth);
                for (int y = top; y >= bottom; y--) {
                    mp.set(Mth.floor(p.x) + dx, y, Mth.floor(p.z) + dz);
                    if (!sl.hasChunkAt(mp)) continue;
                    BlockState st = sl.getBlockState(mp);
                    if (st.isAir()) continue;
                    float res = st.getBlock().getExplosionResistance();
                    if (st.getDestroySpeed(sl, mp) < 0 || res >= 1200) continue;
                    if (h2 > 1.0 && rnd.nextFloat() < 0.6f) continue; // ragged edge
                    if (st.hasBlockEntity()) sl.destroyBlock(mp, true);
                    else sl.setBlock(mp, (y < sl.getSeaLevel() && !st.getFluidState().isEmpty()) ? st : Blocks.AIR.defaultBlockState(), 2 | 16);
                    removed++;
                }
                // throw some of the soil onto the rim
                if (h2 > 0.85 && h2 < 1.25 && rnd.nextFloat() < 0.35f) {
                    int x = Mth.floor(p.x) + dx, z = Mth.floor(p.z) + dz;
                    for (int y = Mth.floor(cy) + 3; y > Mth.floor(cy) - 4; y--) {
                        mp.set(x, y, z);
                        if (sl.getBlockState(mp).isAir() && !sl.getBlockState(mp.below()).isAir()) {
                            sl.setBlock(mp, (rnd.nextBoolean() ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState(), 3);
                            break;
                        }
                    }
                }
            }
        return removed;
    }

    /** Overpressure: damage with distance falloff and line-of-sight, knockback outwards. */
    public static void blast(ServerLevel sl, Vec3 p, double radius, float maxDmg, Entity direct, Entity owner) {
        DamageSource src = sl.damageSources().explosion(direct, owner);
        List<Entity> list = sl.getEntities(direct, new AABB(p, p).inflate(radius), e -> e instanceof LivingEntity || e instanceof BomberEntity);
        for (Entity e : list) {
            double dist = e.position().add(0, e.getBbHeight() / 2, 0).distanceTo(p);
            if (dist > radius) continue;
            double f = Math.pow(1 - dist / radius, 1.6);
            boolean los = sl.clip(new ClipContext(p, e.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, direct)).getType() == HitResult.Type.MISS;
            if (!los) f *= 0.35;
            if (e instanceof LivingEntity le) le.invulnerableTime = 0;
            e.hurt(src, (float) (maxDmg * f));
            if (!(e instanceof BomberEntity)) {
                Vec3 push = e.position().subtract(p).normalize().scale(2.2 * f);
                e.push(push.x, Math.max(0.3 * f, push.y * 0.5 + 0.4 * f), push.z);
                e.hurtMarked = true;
            }
        }
    }

    /** Casts fragment rays; damage per entity is accumulated and applied once. */
    public static void fragments(ServerLevel sl, Vec3 p, int rays, double len, float dmg, Entity direct, Entity owner) {
        RandomSource r = sl.random;
        List<Entity> near = sl.getEntities(direct, new AABB(p, p).inflate(len), e -> e instanceof LivingEntity);
        Map<Entity, Float> acc = new HashMap<>();
        for (int i = 0; i < rays; i++) {
            double y = r.nextDouble() * 1.1 - 0.15;
            double a = r.nextDouble() * Math.PI * 2, h = Math.sqrt(Math.max(0, 1 - y * y));
            Vec3 dir = new Vec3(Math.cos(a) * h, y, Math.sin(a) * h);
            Vec3 end = p.add(dir.scale(len));
            BlockHitResult bh = sl.clip(new ClipContext(p.add(0, 0.5, 0), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, direct));
            if (bh.getType() == HitResult.Type.BLOCK) {
                end = bh.getLocation();
                BlockState st = sl.getBlockState(bh.getBlockPos());
                if (blocks() && (st.is(Tags.Blocks.GLASS) || st.is(Tags.Blocks.GLASS_PANES) || st.is(BlockTags.LEAVES)))
                    sl.destroyBlock(bh.getBlockPos(), false);
            }
            for (Entity e : near) {
                if (e.getBoundingBox().inflate(0.2).clip(p, end).isPresent()) {
                    double d = e.position().distanceTo(p);
                    acc.merge(e, dmg * (float) Math.max(0.15, 1 - d / len), Float::sum);
                }
            }
        }
        DamageSource src = sl.damageSources().explosion(direct, owner);
        acc.forEach((e, d) -> {
            if (e instanceof LivingEntity le) le.invulnerableTime = 0;
            e.hurt(src, Math.min(d, 80f));
        });
    }

    /** Burnt ground and stripped vegetation around the crater. */
    public static void scorch(ServerLevel sl, Vec3 p, double radius) {
        if (!blocks()) return;
        RandomSource r = sl.random;
        int n = (int) (radius * radius * 1.4);
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * radius;
            int x = Mth.floor(p.x + Math.cos(a) * d), z = Mth.floor(p.z + Math.sin(a) * d);
            for (int y = Mth.floor(p.y) + 4; y > Mth.floor(p.y) - 6; y--) {
                bp.set(x, y, z);
                if (!sl.hasChunkAt(bp)) break;
                BlockState st = sl.getBlockState(bp);
                if (st.isAir()) continue;
                if (st.is(BlockTags.REPLACEABLE) || st.is(BlockTags.FLOWERS)) {
                    sl.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
                    continue;
                }
                double edge = d / radius;
                if (st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.PODZOL) || st.is(Blocks.MYCELIUM) || st.is(Blocks.DIRT_PATH) || st.is(Blocks.SNOW_BLOCK))
                    sl.setBlock(bp, (r.nextDouble() < 0.75 - edge * 0.4 ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState(), 3);
                else if ((st.is(Blocks.STONE) || st.is(Blocks.SAND)) && edge < 0.5 && r.nextDouble() < 0.3)
                    sl.setBlock(bp, (st.is(Blocks.SAND) ? Blocks.GRAVEL : Blocks.COBBLESTONE).defaultBlockState(), 3);
                else if (st.is(BlockTags.LEAVES) && r.nextDouble() < 0.6) {
                    sl.destroyBlock(bp, false);
                    continue;
                }
                break;
            }
        }
    }

    /** Blast wave breaks glass well beyond the crater. */
    public static void shatter(ServerLevel sl, Vec3 p, double radius) {
        if (!blocks()) return;
        RandomSource r = sl.random;
        int broken = 0;
        for (int i = 0; i < 1200; i++) {
            double u = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, d = Math.cbrt(r.nextDouble()) * radius;
            double h = Math.sqrt(1 - u * u);
            BlockPos bp = BlockPos.containing(p.x + Math.cos(a) * h * d, p.y + u * d * 0.6, p.z + Math.sin(a) * h * d);
            if (!sl.hasChunkAt(bp)) continue;
            BlockState st = sl.getBlockState(bp);
            if (st.is(Tags.Blocks.GLASS) || st.is(Tags.Blocks.GLASS_PANES)) {
                sl.destroyBlock(bp, false);
                if (++broken > 96) break;
            }
        }
        if (broken > 0) sl.playSound(null, p.x, p.y, p.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 4f, 0.8f);
    }

    /** Throws crater material as falling blocks (ejecta). */
    public static void debris(ServerLevel sl, Vec3 p, int count, float r) {
        if (!blocks() || !Config.DEBRIS.get()) return;
        RandomSource rnd = sl.random;
        int made = 0;
        for (int i = 0; i < count * 6 && made < count; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = r * (0.8 + rnd.nextDouble() * 0.6);
            BlockPos bp = BlockPos.containing(p.x + Math.cos(a) * d, p.y - 1 - rnd.nextInt(3), p.z + Math.sin(a) * d);
            if (!sl.hasChunkAt(bp)) continue;
            BlockState st = sl.getBlockState(bp);
            if (st.isAir() || st.hasBlockEntity() || st.getDestroySpeed(sl, bp) < 0 || st.getDestroySpeed(sl, bp) > 3.5f
                    || !st.getFluidState().isEmpty() || !sl.getBlockState(bp.above()).isAir()) continue;
            FallingBlockEntity fb = FallingBlockEntity.fall(sl, bp, st);
            fb.dropItem = false;
            fb.time = 1;
            double sp = 0.3 + rnd.nextDouble() * 0.4 * (r / 4);
            fb.setDeltaMovement(Math.cos(a) * sp, 0.6 + rnd.nextDouble() * 0.6 * (r / 4), Math.sin(a) * sp);
            fb.hurtMarked = true;
            made++;
        }
    }

    /** Burning fuel. */
    public static void fire(ServerLevel sl, Vec3 p, int count, double radius) {
        RandomSource r = sl.random;
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * radius;
            int x = Mth.floor(p.x + Math.cos(a) * d), z = Mth.floor(p.z + Math.sin(a) * d);
            for (int dy = 4; dy >= -6; dy--) {
                BlockPos bp = new BlockPos(x, Mth.floor(p.y) + dy, z);
                if (!sl.hasChunkAt(bp)) break;
                if (sl.getBlockState(bp).isAir() && !sl.getBlockState(bp.below()).isAir()) {
                    if (BaseFireBlock.canBePlacedAt(sl, bp, Direction.UP)) sl.setBlock(bp, BaseFireBlock.getState(sl, bp), 11);
                    break;
                }
            }
        }
    }
}
