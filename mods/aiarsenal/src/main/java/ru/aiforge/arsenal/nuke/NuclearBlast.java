package ru.aiforge.arsenal.nuke;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import ru.aiforge.arsenal.AiArsenal;
import ru.aiforge.arsenal.ArsenalConfig;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.core.Tickets;
import ru.aiforge.arsenal.entity.ArsEntity;
import ru.aiforge.arsenal.network.ArsenalNetwork;
import ru.aiforge.arsenal.network.NukeFxPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Nuclear detonation. Radii scale with the cube root of the yield (Hopkinson-Cranz): R = 40 * (Y/22kt)^(1/3) blocks,
 * capped at 190 so the Tsar Bomba stays playable. The block damage runs as a wave: chunks are processed ordered by
 * distance, a configurable number of terrain columns per tick.
 * <ul>
 * <li>&lt;0.33R (ground burst only): crater, depth 0.12R, fused-soil lining (blackstone, basalt, magma, green glass)</li>
 * <li>overpressure: every block above the ground whose blast resistance is below S(d) = 12 (0.7R/d)^2 is removed</li>
 * <li>&lt;1.3R: vegetation burnt, grass to coarse dirt; leaves stripped to 1.9R; random fires 0.4R..1.5R</li>
 * </ul>
 * Air bursts (burst height &gt; 0.25R) leave no crater but reach further on the ground (Mach stem: slant distance).
 */
public final class NuclearBlast {
    private static final List<NuclearBlast> ACTIVE = new ArrayList<>();
    public static int detonations;
    @Nullable
    public static Vec3 last;
    public static int lastBlocks;
    /** GameTests: skip terrain processing. */
    public static boolean testNoBlocks;

    private final ServerLevel sl;
    private final Vec3 p;
    private final float r;
    private final int cx, cz, gy;
    private final float hb, craterR, craterD;
    private final List<long[]> chunks = new ArrayList<>();
    private int chunkIdx, colIdx;
    private int changed;
    private final RandomSource rnd = RandomSource.create();

    private NuclearBlast(ServerLevel sl, Vec3 p, float r) {
        this.sl = sl;
        this.p = p;
        this.r = r;
        this.cx = Mth.floor(p.x);
        this.cz = Mth.floor(p.z);
        this.gy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        this.hb = (float) Math.max(0, p.y - gy);
        float cf = Mth.clamp(1f - hb / (0.25f * r), 0f, 1f);
        this.craterR = 0.33f * r * cf;
        this.craterD = Math.min(0.12f * r * cf, 10f);   // shallow gameplay crater (never to bedrock)
        int reach = (int) Math.ceil(1.9f * r);
        int c0x = (cx - reach) >> 4, c1x = (cx + reach) >> 4, c0z = (cz - reach) >> 4, c1z = (cz + reach) >> 4;
        for (int x = c0x; x <= c1x; x++)
            for (int z = c0z; z <= c1z; z++) {
                double dx = Mth.clamp(cx, x * 16, x * 16 + 15) - cx, dz = Mth.clamp(cz, z * 16, z * 16 + 15) - cz;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= reach) chunks.add(new long[]{x, z, (long) (d * 100)});
            }
        chunks.sort((a, b) -> Long.compare(a[2], b[2]));
    }

    /** Damage radius in blocks for a yield in kilotons. */
    public static float radius(float kt) {
        float s = ArsenalConfig.NUKE_RADIUS_SCALE.get().floatValue();
        return Math.min(190f, 40f * (float) Math.cbrt(kt / 22f)) * s;
    }

    /** Detonates a nuclear warhead of {@code kind} at {@code p}. */
    public static void detonate(ServerLevel sl, Vec3 p, Kind kind, @Nullable Entity direct, @Nullable Entity owner) {
        float r = radius(kind.yieldKt());
        detonations++;
        last = p;
        int ground = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(p.x), Mth.floor(p.z));
        float hb = (float) Math.max(0, p.y - ground);
        AiArsenal.LOGGER.info("Nuclear detonation {} ({} kt) at {} burst height {} R={}", kind.id, kind.yieldKt(), BlockPos.containing(p), (int) hb, r);
        if (ArsenalConfig.REALWAR_NUKE.get() && !testNoBlocks) {
            // Real War blast model: ground burst below ~12 blocks (crater + fallout plume), air burst above
            boolean ground0 = hb < 12f;
            Vec3 burst = ground0 ? new Vec3(p.x, ground, p.z) : p;
            if (ru.aiforge.realwar.blast.NuclearBlast.start(sl, burst, kind.yieldKt(), ground0) != null) return;
        }
        ArsenalNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(sl::dimension),
                new NukeFxPacket(p.x, p.y, p.z, ground, r, kind.ordinal()));
        boolean surface = hb < 1.2f * r;
        if (surface) {
            effects(sl, p, r, hb, direct, owner);
            int secs = ArsenalConfig.FALLOUT_SECONDS.get();
            if (secs > 0 && hb < 0.6f * r)
                ChargeRegistry.get(sl.getServer()).addFallout(sl.dimension(), new Vec3(p.x, ground, p.z), 1.4f * r * (1f - hb / (0.6f * r) * 0.5f),
                        sl.getGameTime() + secs * 20L);
        }
        if (surface && !testNoBlocks && ArsenalConfig.NUKE_BLOCK_DAMAGE.get()) ACTIVE.add(new NuclearBlast(sl, p, r));
    }

    /** Overpressure, thermal pulse and knock-back on entities. */
    private static void effects(ServerLevel sl, Vec3 p, float r, float hb, @Nullable Entity direct, @Nullable Entity owner) {
        DamageSource blast = sl.damageSources().explosion(direct, owner);
        DamageSource heat = sl.damageSources().inFire();
        double reach = 1.6 * r;
        for (Entity e : sl.getEntities((Entity) null, new AABB(p, p).inflate(reach, reach + hb, reach), x -> x != direct)) {
            double d = e.position().distanceTo(p);
            if (d > reach) continue;
            if (e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof Projectile) {
                if (d < 0.8 * r) e.discard();
                continue;
            }
            if (e instanceof ArsEntity a) {
                if (d < 1.1 * r) a.hurt(blast, 1000f * (float) (1 - d / (1.1 * r)) + 5f);
                continue;
            }
            Vec3 away = e.position().subtract(p);
            double len = Math.max(0.1, away.length());
            if (d < 1.3 * r) {
                double k = (1.3 * r - d) / (1.3 * r);
                float dmg = d < 0.45 * r ? 5000f : (float) (300 * k * k);
                e.hurt(blast, dmg);
                e.push(away.x / len * 3 * k, 0.6 * k + 0.2, away.z / len * 3 * k);
                e.hurtMarked = true;
            }
            if (e instanceof LivingEntity le) {
                Vec3 eye = le.getEyePosition();
                HitResult h = sl.clip(new ClipContext(p, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
                boolean exposed = h.getType() == HitResult.Type.MISS || d < 0.4 * r;
                if (exposed) {
                    double k = 1 - d / reach;
                    le.setSecondsOnFire((int) (4 + 20 * k));
                    le.hurt(heat, (float) (40 * k * k));
                }
            }
        }
    }

    /** Server tick: advances every blast wave and applies fallout every second. */
    public static void tickAll(net.minecraft.server.MinecraftServer server) {
        int budget = ArsenalConfig.NUKE_COLUMNS_PER_TICK.get();
        for (Iterator<NuclearBlast> it = ACTIVE.iterator(); it.hasNext(); ) {
            NuclearBlast b = it.next();
            if (b.step(budget)) {
                lastBlocks = b.changed;
                AiArsenal.LOGGER.info("Nuclear blast finished: {} blocks changed", b.changed);
                it.remove();
            }
            break; // one wave at a time
        }
        if (server.getTickCount() % 20 == 0) fallout(server);
    }

    public static boolean busy() {
        return !ACTIVE.isEmpty();
    }

    private static void fallout(net.minecraft.server.MinecraftServer server) {
        ChargeRegistry reg = ChargeRegistry.get(server);
        if (reg.fallout.isEmpty()) return;
        long now = server.overworld().getGameTime();
        reg.fallout.removeIf(f -> f.until < now);
        for (ChargeRegistry.Fallout f : reg.fallout) {
            ServerLevel sl = server.getLevel(f.dim);
            if (sl == null) continue;
            for (ServerPlayer pl : sl.players()) {
                if (pl.isCreative() || pl.isSpectator()) continue;
                double d = Math.sqrt(Math.pow(pl.getX() - f.pos.x, 2) + Math.pow(pl.getZ() - f.pos.z, 2));
                if (d > f.radius) continue;
                double k = 1 - d / f.radius;
                applyDose(pl, k);
            }
            for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(f.pos, f.pos).inflate(f.radius, 64, f.radius),
                    x -> !(x instanceof Player))) {
                double d = Math.sqrt(Math.pow(le.getX() - f.pos.x, 2) + Math.pow(le.getZ() - f.pos.z, 2));
                if (d < f.radius) applyDose(le, 1 - d / f.radius);
            }
        }
    }

    private static void applyDose(LivingEntity le, double k) {
        le.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, k > 0.5 ? 2 : 0, false, false, true));
        if (k > 0.3) le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false, true));
        if (k > 0.55) le.addEffect(new MobEffectInstance(MobEffects.POISON, 50, 0, false, false, true));
        if (k > 0.85) le.addEffect(new MobEffectInstance(MobEffects.WITHER, 50, 0, false, false, true));
    }

    // ------------------------------------------------------------------ terrain wave
    /** @return true when finished */
    private boolean step(int budget) {
        int loads = 0;
        while (budget > 0 && chunkIdx < chunks.size()) {
            long[] c = chunks.get(chunkIdx);
            int chx = (int) c[0], chz = (int) c[1];
            if (colIdx == 0) {
                if (!sl.hasChunk(chx, chz)) {
                    if (loads >= 3) return false;
                    loads++;
                }
                Tickets.keepBlast(sl, chx * 16 + 8, chz * 16 + 8);
                sl.getChunk(chx, chz);
            }
            while (colIdx < 256 && budget > 0) {
                int x = chx * 16 + (colIdx & 15), z = chz * 16 + (colIdx >> 4);
                column(x, z);
                colIdx++;
                budget--;
            }
            if (colIdx >= 256) {
                colIdx = 0;
                chunkIdx++;
            }
        }
        return chunkIdx >= chunks.size();
    }

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private void set(BlockPos.MutableBlockPos bp, BlockState s) {
        sl.setBlock(bp, s, FLAGS);
        changed++;
    }

    private void column(int x, int z) {
        double dx = x + 0.5 - p.x, dz = z + 0.5 - p.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        double dd = Math.sqrt(d * d + 0.25 * hb * hb);   // effective (slant) distance
        if (dd > 1.9 * r) return;
        int top = sl.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        int yg = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int minY = sl.getMinBuildHeight();
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        double s = dd < 1 ? 5000 : 12.0 * Math.pow(0.7 * r / dd, 2);
        // ---- crater
        if (d < craterR) {
            double depth = craterD * (1 - (d / craterR) * (d / craterR)) + rnd.nextFloat() * 0.8;
            int bottom = (int) Math.floor(gy - 1 - depth);
            for (int y = top; y > bottom && y > minY; y--) {
                bp.set(x, y, z);
                BlockState bs = sl.getBlockState(bp);
                if (bs.isAir() || bs.getDestroySpeed(sl, bp) < 0) continue;
                set(bp, Blocks.AIR.defaultBlockState());
            }
            bp.set(x, bottom, z);
            BlockState bs = sl.getBlockState(bp);
            if (!bs.isAir() && bs.getDestroySpeed(sl, bp) >= 0) {
                float q = rnd.nextFloat();
                set(bp, q < 0.5f ? Blocks.BLACKSTONE.defaultBlockState() : q < 0.72f ? Blocks.BASALT.defaultBlockState()
                        : q < 0.85f ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.GREEN_STAINED_GLASS.defaultBlockState());
            }
            return;
        }
        // ---- crater rim: thrown-out soil
        if (craterR > 3 && d < craterR * 1.3) {
            int rim = (int) ((1 - (d - craterR) / (0.3 * craterR)) * craterD * 0.25);
            for (int k = 1; k <= rim; k++) {
                bp.set(x, yg + k, z);
                if (sl.getBlockState(bp).isAir()) set(bp, rnd.nextFloat() < 0.6f ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
            }
            top = Math.max(top, yg + rim);
            yg += rim;
        }
        // ---- objects above the ground: overpressure vs blast resistance
        for (int y = top; y > yg; y--) {
            bp.set(x, y, z);
            BlockState bs = sl.getBlockState(bp);
            if (bs.isAir()) continue;
            if (bs.getDestroySpeed(sl, bp) < 0) continue;
            boolean remove;
            if (bs.is(BlockTags.LEAVES)) remove = dd < 1.9 * r;
            else if (!bs.getFluidState().isEmpty() && bs.getFluidState().isSource()) remove = d < 0.5 * r && y > yg - 3;
            else remove = bs.getBlock().getExplosionResistance() < s * (0.6 + 0.7 * rnd.nextFloat());
            if (remove) set(bp, Blocks.AIR.defaultBlockState());
        }
        // ---- surface: scorch, evaporate shallow water, fires
        bp.set(x, yg, z);
        BlockState g = sl.getBlockState(bp);
        if (dd < 1.3 * r) {
            if (g.is(Blocks.GRASS_BLOCK) || g.is(Blocks.PODZOL) || g.is(Blocks.MYCELIUM) || g.is(Blocks.DIRT_PATH)) {
                if (dd < r || rnd.nextFloat() < 0.5f) set(bp, dd < 0.8 * r ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState());
            } else if ((g.is(Blocks.SAND) || g.is(Blocks.RED_SAND)) && dd < 0.45 * r && rnd.nextFloat() < 0.25f) {
                set(bp, Blocks.GLASS.defaultBlockState());
            } else if (g.is(Blocks.SNOW_BLOCK) || g.is(Blocks.ICE) || g.is(Blocks.PACKED_ICE)) {
                set(bp, dd < 0.8 * r ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState());
            }
            bp.set(x, yg + 1, z);
            BlockState above = sl.getBlockState(bp);
            if (above.is(Blocks.SNOW) || above.is(BlockTags.REPLACEABLE_BY_TREES) || above.is(BlockTags.FLOWERS) || above.is(Blocks.GRASS) || above.is(Blocks.TALL_GRASS)) {
                set(bp, Blocks.AIR.defaultBlockState());
                above = Blocks.AIR.defaultBlockState();
            }
            if (above.isAir() && dd > 0.4 * r && dd < 1.5 * r && rnd.nextFloat() < 0.035f) {
                BlockState fire = BaseFireBlock.getState(sl, bp);
                if (fire.canSurvive(sl, bp)) set(bp, fire);
            }
        }
    }
}
