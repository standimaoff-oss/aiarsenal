package ru.aiforge.bm21grad.fx;

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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.network.PacketDistributor;
import ru.aiforge.bm21grad.Bm21Grad;
import ru.aiforge.bm21grad.Config;
import ru.aiforge.bm21grad.ammo.RocketType;
import ru.aiforge.bm21grad.entity.SmokeScreenEntity;
import ru.aiforge.bm21grad.entity.SubmunitionEntity;
import ru.aiforge.bm21grad.network.FxPacket;
import ru.aiforge.bm21grad.network.ModNetwork;
import ru.aiforge.bm21grad.registry.ModRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Server-side warhead effects for every rocket type. All work is bounded (fixed ray/sample counts). */
public final class ImpactHandler {
    public static final int SUBMUNITIONS = 45;
    /** statistics for tests / status command */
    public static int detonations;

    private ImpactHandler() {
    }

    private static Level.ExplosionInteraction blocks() {
        return Config.BLOCK_DAMAGE.get() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
    }

    private static float scale() {
        return Config.EXPLOSION_SCALE.get().floatValue();
    }

    public static void detonate(ServerLevel sl, Vec3 p, RocketType type, Entity direct, Entity owner, Vec3 vel) {
        detonations++;
        DamageSource src = sl.damageSources().explosion(direct, owner);
        float s = scale();
        switch (type) {
            case HE_FRAG -> {
                sl.explode(direct, src, null, p.x, p.y, p.z, type.blastRadius * s, false, blocks());
                fragments(sl, p, type.fragments, 24, type.fragmentDamage, direct, owner);
                vehicles(sl, p, 6.4);                  // r21: 9M22U, 6.4 kg HE + fragments
                scorch(sl, p, 5 * s);
                shatter(sl, p, 14 * s);
                fx(sl, p, FxPacket.HE, type.blastRadius * s);
            }
            case HE_BLAST -> {
                sl.explode(direct, src, null, p.x, p.y, p.z, type.blastRadius * s, false, blocks());
                fragments(sl, p, type.fragments, 16, type.fragmentDamage, direct, owner);
                vehicles(sl, p, 21.0);                 // r21: 9M28F, high-explosive blast warhead
                debris(sl, p, 12, s);
                scorch(sl, p, 7 * s);
                shatter(sl, p, 22 * s);
                fx(sl, p, FxPacket.BLAST, type.blastRadius * s);
            }
            case INCENDIARY -> {
                sl.explode(direct, src, null, p.x, p.y, p.z, type.blastRadius * s, true, blocks());
                thermite(sl, p, 34, 8 * s);
                vehicles(sl, p, 1.8);
                for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(7 * s)))
                    le.setSecondsOnFire(10);
                scorch(sl, p, 6 * s);
                fx(sl, p, FxPacket.INCENDIARY, 4 * s);
            }
            case CLUSTER -> clusterBurst(sl, p.add(0, 3, 0), vel, direct, owner);
            case SMOKE -> {
                sl.explode(direct, src, null, p.x, p.y, p.z, type.blastRadius, false, Level.ExplosionInteraction.NONE);
                sl.addFreshEntity(SmokeScreenEntity.create(sl, p.x, p.y, p.z));
                fx(sl, p, FxPacket.SMOKE, 2);
            }
            default -> {
            }
        }
        Bm21Grad.LOGGER.debug("Impact {} at {}", type, BlockPos.containing(p));
    }

    /**
     * r21: the warhead's blast and fragments against vehicles go through the RealWar HE model (the vanilla explosion
     * alone barely scratched a truck): soft-skinned vehicles near the burst are wrecked, armour only loses tracks / optics.
     */
    static void vehicles(ServerLevel sl, Vec3 p, double tntKg) {
        double r = 7.0 * Math.cbrt(tntKg) + 8.0;
        for (Entity e : sl.getEntities((Entity) null, new AABB(p, p).inflate(r), x -> x instanceof ru.aiforge.realwar.damage.Damageable && x.isAlive())) {
            ((ru.aiforge.realwar.damage.Damageable) e).blastNear(p, tntKg, e.position().distanceTo(p));
        }
    }

    /** 9M218 opens over the target: small ejection charge + 45 bomblets in a ~30 block ellipse. */
    public static void clusterBurst(ServerLevel sl, Vec3 p, Vec3 vel, Entity direct, Entity owner) {
        detonations++;
        sl.explode(direct, sl.damageSources().explosion(direct, owner), null, p.x, p.y, p.z, 0.8f, false, Level.ExplosionInteraction.NONE);
        sl.playSound(null, p.x, p.y, p.z, ModRegistry.SND_SUBMUNITION.get(), SoundSource.HOSTILE, 8f, 1f);
        fx(sl, p, FxPacket.CLUSTER, 1.5f);
        RandomSource r = sl.random;
        Vec3 base = vel.scale(0.35);
        boolean ticking = sl.isPositionEntityTicking(BlockPos.containing(p));
        for (int i = 0; i < SUBMUNITIONS; i++) {
            double a = r.nextDouble() * Math.PI * 2, m = 0.15 + r.nextDouble() * 0.45;
            Vec3 v = base.add(Math.cos(a) * m, -0.1 - r.nextDouble() * 0.2, Math.sin(a) * m);
            if (ticking) sl.addFreshEntity(SubmunitionEntity.create(sl, p, v, owner));
            else GhostManager.scheduleBomblet(sl, p, v, owner, 10 + r.nextInt(30));
        }
    }

    public static void bomblet(ServerLevel sl, Vec3 p, Entity direct, Entity owner) {
        detonations++;
        DamageSource src = sl.damageSources().explosion(direct, owner);
        sl.explode(direct, src, null, p.x, p.y, p.z, 1.7f * scale(), false, blocks());
        fragments(sl, p, 10, 8, 6f, direct, owner);
        vehicles(sl, p, 0.25);                         // 9N210 bomblet
        fx(sl, p, FxPacket.BOMBLET, 1.7f);
    }

    // ------------------------------------------------------------------ building blocks
    private static void fx(ServerLevel sl, Vec3 p, int kind, float power) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(p.x, p.y, p.z, 1200, sl.dimension())),
                new FxPacket(p.x, p.y, p.z, kind, power));
    }

    /** Casts fragment rays; damage per entity is accumulated and applied once (bypassing the i-frames of the blast). */
    public static void fragments(ServerLevel sl, Vec3 p, int rays, double len, float dmg, Entity direct, Entity owner) {
        if (rays <= 0) return;
        RandomSource r = sl.random;
        List<Entity> near = sl.getEntities(direct, new AABB(p, p).inflate(len), e -> e instanceof LivingEntity || e instanceof ru.aiforge.bm21grad.entity.Bm21Entity);
        Map<Entity, Float> acc = new HashMap<>();
        for (int i = 0; i < rays; i++) {
            double y = r.nextDouble() * 1.2 - 0.25;
            double a = r.nextDouble() * Math.PI * 2, h = Math.sqrt(Math.max(0, 1 - y * y));
            Vec3 dir = new Vec3(Math.cos(a) * h, y, Math.sin(a) * h);
            Vec3 end = p.add(dir.scale(len));
            BlockHitResult bh = sl.clip(new ClipContext(p, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, direct));
            if (bh.getType() == HitResult.Type.BLOCK) {
                end = bh.getLocation();
                BlockState st = sl.getBlockState(bh.getBlockPos());
                if (Config.BLOCK_DAMAGE.get() && (st.is(Tags.Blocks.GLASS) || st.is(Tags.Blocks.GLASS_PANES) || st.is(BlockTags.LEAVES)))
                    sl.destroyBlock(bh.getBlockPos(), false);
            }
            for (Entity e : near) {
                if (e.getBoundingBox().inflate(0.2).clip(p, end).isPresent()) {
                    double d = e.position().distanceTo(p);
                    float f = (float) Math.max(0.15, 1 - d / len);
                    acc.merge(e, dmg * f, Float::sum);
                }
            }
        }
        DamageSource src = sl.damageSources().explosion(direct, owner);
        acc.forEach((e, d) -> {
            if (e instanceof LivingEntity le) le.invulnerableTime = 0;
            e.hurt(src, Math.min(d, 60f));
        });
    }

    /** Burnt ground, stripped vegetation and snow around the crater. */
    public static void scorch(ServerLevel sl, Vec3 p, double radius) {
        if (!Config.BLOCK_DAMAGE.get()) return;
        RandomSource r = sl.random;
        int n = (int) (radius * radius * 1.6);
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * radius;
            int x = Mth.floor(p.x + Math.cos(a) * d), z = Mth.floor(p.z + Math.sin(a) * d);
            if (!sl.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int top = sl.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            if (Math.abs(top - p.y) > radius + 2) continue;
            BlockPos bp = new BlockPos(x, top, z);
            BlockState st = sl.getBlockState(bp);
            if (st.is(BlockTags.REPLACEABLE) || st.is(BlockTags.FLOWERS) || st.is(BlockTags.SNOW) && !st.isCollisionShapeFullBlock(sl, bp)) {
                sl.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
                bp = bp.below();
                st = sl.getBlockState(bp);
            }
            double edge = d / radius;
            if (st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.PODZOL) || st.is(Blocks.MYCELIUM) || st.is(Blocks.DIRT_PATH) || st.is(Blocks.SNOW_BLOCK)) {
                sl.setBlock(bp, (r.nextDouble() < 0.7 - edge * 0.4 ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState(), 3);
            } else if (st.is(Blocks.DIRT) && r.nextDouble() < 0.5) {
                sl.setBlock(bp, Blocks.COARSE_DIRT.defaultBlockState(), 3);
            } else if ((st.is(Blocks.STONE) || st.is(Blocks.COBBLESTONE) || st.is(Blocks.SAND)) && edge < 0.5 && r.nextDouble() < 0.3) {
                sl.setBlock(bp, (st.is(Blocks.SAND) ? Blocks.GRAVEL : Blocks.COBBLESTONE).defaultBlockState(), 3);
            } else if (st.is(BlockTags.LEAVES) && r.nextDouble() < 0.5) {
                sl.destroyBlock(bp, false);
            }
        }
    }

    /** Blast wave breaks glass well beyond the crater. */
    public static void shatter(ServerLevel sl, Vec3 p, double radius) {
        if (!Config.BLOCK_DAMAGE.get()) return;
        RandomSource r = sl.random;
        int broken = 0;
        for (int i = 0; i < 900; i++) {
            double u = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, d = Math.cbrt(r.nextDouble()) * radius;
            double h = Math.sqrt(1 - u * u);
            BlockPos bp = BlockPos.containing(p.x + Math.cos(a) * h * d, p.y + u * d * 0.6, p.z + Math.sin(a) * h * d);
            if (!sl.hasChunkAt(bp)) continue;
            BlockState st = sl.getBlockState(bp);
            if (st.is(Tags.Blocks.GLASS) || st.is(Tags.Blocks.GLASS_PANES)) {
                sl.destroyBlock(bp, false);
                if (++broken > 64) break;
            }
        }
        if (broken > 0) sl.playSound(null, p.x, p.y, p.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 3f, 0.8f);
    }

    /** Throws crater material as falling blocks (they land and pile up like real ejecta). */
    public static void debris(ServerLevel sl, Vec3 p, int count, float s) {
        if (!Config.BLOCK_DAMAGE.get() || !Config.DEBRIS.get()) return;
        RandomSource r = sl.random;
        int made = 0;
        for (int i = 0; i < count * 6 && made < count; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 3 + r.nextDouble() * 3 * s;
            BlockPos bp = BlockPos.containing(p.x + Math.cos(a) * d, p.y - 1 - r.nextInt(3), p.z + Math.sin(a) * d);
            if (!sl.hasChunkAt(bp)) continue;
            BlockState st = sl.getBlockState(bp);
            if (st.isAir() || st.hasBlockEntity() || st.getDestroySpeed(sl, bp) < 0 || st.getDestroySpeed(sl, bp) > 3.5f
                    || !st.getFluidState().isEmpty() || !sl.getBlockState(bp.above()).isAir()) continue;
            FallingBlockEntity fb = FallingBlockEntity.fall(sl, bp, st);
            fb.dropItem = false;
            fb.time = 1;
            fb.setDeltaMovement(Math.cos(a) * (0.25 + r.nextDouble() * 0.35), 0.55 + r.nextDouble() * 0.5, Math.sin(a) * (0.25 + r.nextDouble() * 0.35));
            fb.hurtMarked = true;
            made++;
        }
    }

    /** Thermite elements: scattered fires on top surfaces. */
    public static void thermite(ServerLevel sl, Vec3 p, int count, double radius) {
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
