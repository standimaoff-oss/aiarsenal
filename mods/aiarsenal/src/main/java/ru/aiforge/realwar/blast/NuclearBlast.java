/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.ints.IntOpenHashSet
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.BlockPos$MutableBlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.tags.BlockTags
 *  net.minecraft.util.Mth
 *  net.minecraft.util.RandomSource
 *  net.minecraft.world.effect.MobEffectInstance
 *  net.minecraft.world.effect.MobEffects
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.BaseFireBlock
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.chunk.LevelChunk
 *  net.minecraft.world.level.levelgen.Heightmap$Types
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 */
package ru.aiforge.realwar.blast;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.blast.BlastManager;
import ru.aiforge.realwar.blast.BlastPhysics;
import ru.aiforge.realwar.blast.BlastTask;
import ru.aiforge.realwar.blast.BlastUtil;
import ru.aiforge.realwar.blast.ColumnOrder;
import ru.aiforge.realwar.blast.ModDamage;
import ru.aiforge.realwar.blast.NuclearProfile;
import ru.aiforge.realwar.blast.RadiationManager;
import ru.aiforge.realwar.network.ModNetwork;
import ru.aiforge.realwar.network.NukeEffectPacket;
import ru.aiforge.realwar.registry.ModBlocks;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class NuclearBlast
implements BlastTask {
    /** Diagnostics for the gametests: crater lining blocks placed / columns carved / last crater radius + ground Y. */
    public static int dbgLining, dbgColumns, dbgCraterR, dbgGroundY, dbgFloorMin = Integer.MAX_VALUE;
    private final ServerLevel level;
    private final Vec3 burst;
    private final int gzX;
    private final int gzZ;
    private final int groundY;
    private final double yieldKt;
    private final boolean surface;
    private final NuclearProfile prof;
    private final double heightBlocks;
    private final int craterR;
    private final int timeMul;
    private final int craterDepth;
    private final int craterTicks;
    private final int lipR;
    private final long seed;
    private final double durFactor;
    private final ColumnOrder order;
    private final int shockTotal;
    private final int thermalCount;
    private final int craterCount;
    private final int craterStart;
    private final RandomSource rnd;
    private final IntOpenHashSet hit = new IntOpenHashSet();
    private int tick;
    private int shockIdx;
    private int thermalIdx;
    private int craterIdx;
    private int lipIdx;
    private boolean prompted;
    private final long startBlocks;

    private NuclearBlast(ServerLevel level, Vec3 burst, double yieldKt, boolean surface) {
        this.level = level;
        this.burst = burst;
        this.yieldKt = yieldKt;
        this.surface = surface;
        double mpb = (Double)RWConfig.COMMON.metersPerBlock.get();
        this.prof = NuclearProfile.create(yieldKt, surface, mpb, (Integer)RWConfig.COMMON.maxBlastRadius.get());
        this.gzX = Mth.floor((double)burst.x);
        this.gzZ = Mth.floor((double)burst.z);
        this.groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.gzX, this.gzZ);
        this.heightBlocks = surface ? 0.0 : Math.max(0.0, burst.y - (double)this.groundY);
        this.craterR = surface ? BlastPhysics.craterRadiusBlocks(yieldKt, mpb, (Double)RWConfig.COMMON.craterScale.get()) : 0;
        // gameplay crater (user request): shallow and irregular - ~4-6 blocks over most of the bowl, deepest (6-12)
        // in the middle, never anywhere near bedrock; the old 0.42*R bowl went 40-170 blocks down for Mt yields
        this.craterDepth = Mth.clamp(5 + this.craterR / 40, 5, 6);
        this.craterTicks = Math.min(30, 6 + this.craterR / 6);
        this.lipR = (int)Math.round((double)this.craterR * 1.3);
        this.durFactor = BlastPhysics.durationFactor(this.prof.wKg);
        // compress the shock-front timeline so the whole radius is processed within ~3.5 s (the hole used to keep
        // "growing" for half a minute after the flash)
        this.timeMul = Math.max(1, (int)Math.ceil(this.prof.arrival[this.prof.maxR] / 70.0));
        this.seed = level.random.nextLong();
        this.rnd = RandomSource.create((long)this.seed);
        this.order = ColumnOrder.get(this.prof.maxR);
        long shockLim = (long)this.prof.maxR * (long)this.prof.maxR - (long)(this.heightBlocks * this.heightBlocks);
        this.shockTotal = this.order.countWithin(shockLim);
        // fires only where the flash really ignites things (>= 20 cal/cm2) and never beyond ~0.55 of the blast radius
        double thermalR = Math.min(this.prof.thermalRadiusBlocks(20.0), this.prof.maxR * 0.55);
        this.thermalCount = this.order.countWithin((long)(thermalR * thermalR - this.heightBlocks * this.heightBlocks));
        this.craterStart = surface ? this.order.countWithin((long)this.craterR * (long)this.craterR) : 0;
        this.craterCount = surface ? this.order.countWithin((long)this.lipR * (long)this.lipR) : 0;
        this.startBlocks = BlastManager.totalBlocksChanged;
    }

    public static NuclearBlast start(ServerLevel level, Vec3 burstPoint, double yieldKt, boolean surface) {
        if (!((Boolean)RWConfig.COMMON.nuclearEnabled.get()).booleanValue()) {
            return null;
        }
        NuclearBlast nb = new NuclearBlast(level, burstPoint, yieldKt, surface);
        nb.announce();
        BlastManager.add(level, nb);
        return nb;
    }

    private void announce() {
        ModNetwork.sendToDimension(this.level, new NukeEffectPacket(this.burst.x, this.burst.y, this.burst.z, (float)this.yieldKt, this.surface, this.seed, (float)this.prof.metersPerBlock, this.prof.maxR, this.groundY, this.craterR));
    }

    public NuclearProfile profile() {
        return this.prof;
    }

    @Override
    public String describe() {
        return String.format("Nuclear %.1f kt %s @ %d,%d tick=%d shock=%d/%d thermal=%d/%d crater=%d/%d blocks=%d", this.yieldKt, this.surface ? "surface" : "air", this.gzX, this.gzZ, this.tick, this.shockIdx, this.shockTotal, this.thermalIdx, this.thermalCount, this.craterIdx, this.craterCount, BlastManager.totalBlocksChanged - this.startBlocks);
    }

    @Override
    public boolean tick(ServerLevel lvl, long deadline) {
        int front;
        if (!this.prompted) {
            this.prompted = true;
            this.promptEffects();
            if (((Boolean)RWConfig.COMMON.radiationEnabled.get()).booleanValue()) {
                RadiationManager.addPlume(this.level, this.burst, this.yieldKt, this.surface, this.prof.metersPerBlock);
            }
        }
        long span = Math.max(0L, deadline - System.nanoTime());
        boolean blocks = (Boolean)RWConfig.COMMON.destroyBlocks.get();
        if (blocks && this.surface) {
            this.runCrater(Math.min(deadline, System.nanoTime() + span * 3L / 10L));
        }
        if (blocks) {
            this.runShock(Math.min(deadline, System.nanoTime() + span * 5L / 10L));
        }
        if (((Boolean)RWConfig.COMMON.thermalIgnition.get()).booleanValue()) {
            this.runThermal(deadline);
        }
        if ((front = this.prof.frontAt(this.tick * this.timeMul)) >= 0 && ((Boolean)RWConfig.COMMON.entityDamage.get()).booleanValue()) {
            this.blastEntities(front);
        }
        if (blocks && this.surface && front >= Math.min(this.lipR + 3, this.prof.maxR) && this.shockIdx >= this.shockTotal) {
            this.runLip(deadline);
        }
        ++this.tick;
        boolean doneBlocks = !blocks || this.shockIdx >= this.shockTotal && (!this.surface || this.craterIdx >= this.craterStart && this.lipIdx >= this.craterCount - this.craterStart);
        boolean doneThermal = (Boolean)RWConfig.COMMON.thermalIgnition.get() == false || this.thermalIdx >= this.thermalCount;
        return this.tick * this.timeMul > this.prof.arrival[this.prof.maxR] + 2 && doneBlocks && doneThermal;
    }

    private void promptEffects() {
        if (!((Boolean)RWConfig.COMMON.entityDamage.get()).booleanValue()) {
            return;
        }
        double rb = this.prof.thermalRadiusBlocks(2.0);
        AABB box = new AABB(this.burst, this.burst).inflate(rb + 2.0);
        List list = this.level.getEntitiesOfClass(LivingEntity.class, box);
        for (LivingEntity e : (Iterable<LivingEntity>)(Iterable<?>)(list)) {
            Vec3 eye = e.getEyePosition();
            double distB = eye.distanceTo(this.burst);
            double q = BlastPhysics.thermalFluence(this.yieldKt, distB * this.prof.metersPerBlock);
            if (q < 2.0 || (q *= BlastUtil.thermalTransmission(this.level, this.burst, eye)) < 2.0) continue;
            if (!(q >= 3.0)) continue;
            e.setSecondsOnFire((int)Math.min(40.0, 2.0 + q));
            e.hurt(ModDamage.source((Level)this.level, ModDamage.NUCLEAR_THERMAL), (float)Math.min(1000.0, q * 1.6));
        }
    }

    private void blastEntities(int front) {
        double r = (double)front + 1.5;
        AABB box = new AABB(this.burst, this.burst).inflate(r);
        List list = this.level.getEntities((Entity)null, box, e -> !e.isSpectator() && !this.hit.contains(e.getId()));
        for (Entity e2 : (Iterable<Entity>)(Iterable<?>)(list)) {
            Vec3 pos = e2.position().add(0.0, (double)e2.getBbHeight() * 0.5, 0.0);
            double dist = pos.distanceTo(this.burst);
            if (dist > r) continue;
            this.hit.add(e2.getId());
            double p = this.prof.pressure[Math.min(this.prof.maxR, (int)dist)];
            double pe = p * BlastUtil.blastAttenuation(this.level, this.burst, pos);
            Vec3 dir = dist < 0.001 ? new Vec3(0.0, 1.0, 0.0) : pos.subtract(this.burst).scale(1.0 / dist);
            double kb = Math.min(4.0, Math.sqrt(pe) / 7.0);
            if (kb > 0.05) {
                e2.setDeltaMovement(e2.getDeltaMovement().add(dir.x * kb, Math.min(1.5, kb * 0.5 + 0.2), dir.z * kb));
                e2.hurtMarked = true;
            }
            if (!(e2 instanceof LivingEntity)) continue;
            LivingEntity le = (LivingEntity)e2;
            float dmg = BlastPhysics.blastDamage(pe);
            if (!(dmg > 0.0f)) continue;
            le.hurt(ModDamage.source((Level)this.level, ModDamage.NUCLEAR_BLAST), dmg);
        }
    }

    private void runThermal(long deadline) {
        int n = 0;
        while (this.thermalIdx < this.thermalCount) {
            this.processThermalColumn(this.order.dx[this.thermalIdx], this.order.dz[this.thermalIdx], this.order.d2[this.thermalIdx]);
            ++this.thermalIdx;
            if ((++n & 0xF) != 0 || System.nanoTime() <= deadline) continue;
            break;
        }
    }

    private void processThermalColumn(int dx, int dz, int d2) {
        int x = this.gzX + dx;
        int z = this.gzZ + dz;
        if (!this.level.hasChunk(x >> 4, z >> 4)) {
            return;
        }
        double slantB = Math.sqrt((double)d2 + this.heightBlocks * this.heightBlocks);
        double q = BlastPhysics.thermalFluence(this.yieldKt, slantB * this.prof.metersPerBlock);
        if (q < 8.0) {
            return;
        }
        int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        if (top < this.level.getMinBuildHeight()) {
            return;
        }
        Vec3 target = new Vec3((double)x + 0.5, (double)top + 1.0, (double)z + 0.5);
        double qe = q * BlastUtil.thermalTransmission(this.level, this.burst, target);
        if (qe < 8.0) {
            return;
        }
        BlockPos tp = new BlockPos(x, top, z);
        BlockState ts = this.level.getBlockState(tp);
        BlockPos above = tp.above();
        if (qe >= 20.0 && ts.isFlammable((BlockGetter)this.level, tp, Direction.UP)) {
            if ((double)this.rnd.nextFloat() < Mth.clamp((double)((qe - 20.0) / 80.0), (double)0.04, (double)0.35) && this.level.getBlockState(above).isAir()) {
                BlastUtil.set(this.level, above, BaseFireBlock.getState((BlockGetter)this.level, (BlockPos)above));
            }
        } else if (qe >= 30.0 && (ts.is(Blocks.GRASS_BLOCK) || ts.is(Blocks.PODZOL) || ts.is(Blocks.MYCELIUM) || ts.is(Blocks.DIRT_PATH) || ts.is(Blocks.MOSS_BLOCK) || ts.is(Blocks.ROOTED_DIRT))) {
            BlastUtil.set(this.level, tp, ((Block)ModBlocks.SCORCHED_EARTH.get()).defaultBlockState());
        } else if (qe >= 15.0 && (ts.is(Blocks.SNOW) || ts.is(Blocks.SNOW_BLOCK))) {
            BlastUtil.set(this.level, tp, Blocks.AIR.defaultBlockState());
        }
    }

    private void runShock(long deadline) {
        int front = this.prof.frontAt(this.tick * this.timeMul);
        if (front < 0) {
            return;
        }
        long lim = (long)(front + 1) * (long)(front + 1) - (long)(this.heightBlocks * this.heightBlocks);
        if (lim < 0L) {
            return;
        }
        int limit = (int)Math.min(lim, (long)this.order.maxD2());
        int n = 0;
        while (this.shockIdx < this.shockTotal && this.order.d2[this.shockIdx] <= limit) {
            this.processShockColumn(this.order.dx[this.shockIdx], this.order.dz[this.shockIdx], this.order.d2[this.shockIdx]);
            ++this.shockIdx;
            if ((++n & 0x1F) != 0 || System.nanoTime() <= deadline) continue;
            break;
        }
    }

    private void processShockColumn(int dx, int dz, int d2) {
        if (this.surface && (long)d2 <= (long)this.craterR * (long)this.craterR) {
            return;
        }
        int x = this.gzX + dx;
        int z = this.gzZ + dz;
        if (!this.level.hasChunk(x >> 4, z >> 4)) {
            return;
        }
        double slant = Math.sqrt((double)d2 + this.heightBlocks * this.heightBlocks);
        double p = this.prof.pressure[Math.min(this.prof.maxR, (int)slant)];
        if (p < 1.5) {
            return;
        }
        LevelChunk chunk = this.level.getChunk(x >> 4, z >> 4);
        int top = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos(x, top, z);
        double pc = p;
        int minY = this.level.getMinBuildHeight();
        int y = top;
        for (int scanned = 0; y >= minY && scanned < 96 && pc >= 1.5; ++scanned) {
            mp.setY(y);
            BlockState s = chunk.getBlockState((BlockPos)mp);
            if (s.isAir()) {
                --y;
                continue;
            }
            if (BlastUtil.isUnbreakable(s)) break;
            if (BlastUtil.isNaturalGround(s)) {
                this.scour(chunk, x, y, z, p);
                break;
            }
            double t = BlastPhysics.blockThresholdKPa(s.getBlock().getExplosionResistance(), this.durFactor);
            if (!(pc >= t)) break;
            BlastUtil.destroy(this.level, mp.immutable(), s, false);
            pc -= t * 0.5;
            --y;
        }
    }

    private void scour(LevelChunk chunk, int x, int y, int z, double p) {
        int depth = p >= 2500.0 ? 3 : (p >= 700.0 ? 2 : (p >= 160.0 ? 1 : 0));
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos(x, y, z);
        for (int k = 0; k < depth; ++k) {
            mp.setY(y - k);
            BlockState s = chunk.getBlockState((BlockPos)mp);
            if (s.isAir() || BlastUtil.isUnbreakable(s)) continue;
            if (!BlastUtil.isLooseGround(s) && !(p >= 2500.0)) break;
            BlastUtil.destroy(this.level, mp.immutable(), s, false);
        }
    }

    /** Bilinear value noise in [0,1] on a unit grid (cheap, deterministic per blast seed). */
    private static double smoothNoise(double x, double z, long seed) {
        int x0 = Mth.floor(x), z0 = Mth.floor(z);
        double fx = x - x0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = BlastUtil.hash01(x0, z0, seed), b = BlastUtil.hash01(x0 + 1, z0, seed);
        double c = BlastUtil.hash01(x0, z0 + 1, seed), d = BlastUtil.hash01(x0 + 1, z0 + 1, seed);
        return Mth.lerp(fz, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }

    private void runCrater(long deadline) {
        if (this.craterIdx >= this.craterStart) {
            return;
        }
        double frac = Math.min(1.0, ((double)this.tick + 1.0) / (double)this.craterTicks);
        double frontD = (double)this.craterR * frac;
        long limit = (long)(frontD * frontD);
        int n = 0;
        while (this.craterIdx < this.craterStart && (long)this.order.d2[this.craterIdx] <= limit) {
            this.carveColumn(this.order.dx[this.craterIdx], this.order.dz[this.craterIdx], this.order.d2[this.craterIdx]);
            ++this.craterIdx;
            if ((++n & 0xF) != 0 || System.nanoTime() <= deadline) continue;
            break;
        }
    }

    private void carveColumn(int dx, int dz, int d2) {
        int x = this.gzX + dx;
        int z = this.gzZ + dz;
        if (!this.level.hasChunk(x >> 4, z >> 4)) {
            return;
        }
        ++dbgColumns;
        dbgCraterR = this.craterR;
        dbgGroundY = this.groundY;
        double rel = Math.sqrt(d2) / (double)this.craterR;
        // irregular bowl: coarse lumps (8-block cells, smoothed) + fine grain; rim ~2, bulk 4-6, centre up to craterDepth
        double lump = smoothNoise(x / 8.0, z / 8.0, this.seed) - 0.5;
        double grain = BlastUtil.hash01(x, z, this.seed ^ 0x5DEECE66DL) - 0.5;
        double prof = 1.5 + 2.0 * (1.0 - rel) + (this.craterDepth - 3.5) * Math.max(0.0, 1.0 - rel * rel * 2.2);
        int depth = Math.min((int)Math.round(prof * (1.0 + 0.35 * lump) + grain * 1.2), this.craterDepth);
        if (rel > 0.92) depth = Math.min(depth, 1);
        int floorY = Math.max(this.groundY - 1 - Math.max(depth, 0), this.level.getMinBuildHeight() + 1);
        int top = Math.min(this.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, this.groundY + 200);
        int minY = this.level.getMinBuildHeight();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos(x, top, z);
        for (int y = top; y > floorY && y >= minY; --y) {
            mp.setY(y);
            BlockState s = this.level.getBlockState((BlockPos)mp);
            if (s.isAir()) continue;
            BlastUtil.destroy(this.level, mp.immutable(), s, false);
        }
        dbgFloorMin = Math.min(dbgFloorMin, floorY);
        if (floorY >= minY && depth > 0) {
            mp.setY(floorY);
            BlockState fs = this.level.getBlockState((BlockPos)mp);
            if (!fs.isAir() && BlastUtil.isNaturalGround(fs) && !BlastUtil.isUnbreakable(fs)) {
                BlockState lining = fs.is(BlockTags.SAND) || rel < 0.6 && this.rnd.nextFloat() < 0.35f ? ((Block)ModBlocks.TRINITITE.get()).defaultBlockState() : ((Block)ModBlocks.SCORCHED_EARTH.get()).defaultBlockState();
                if (BlastUtil.set(this.level, mp.immutable(), lining)) ++dbgLining;
            }
        }
    }

    private void runLip(long deadline) {
        int total = this.craterCount - this.craterStart;
        int n = 0;
        while (this.lipIdx < total) {
            int i = this.craterStart + this.lipIdx;
            this.lipColumn(this.order.dx[i], this.order.dz[i], this.order.d2[i]);
            ++this.lipIdx;
            if ((++n & 0x1F) != 0 || System.nanoTime() <= deadline) continue;
            break;
        }
    }

    private void lipColumn(int dx, int dz, int d2) {
        BlockPos p;
        int x = this.gzX + dx;
        int z = this.gzZ + dz;
        if (!this.level.hasChunk(x >> 4, z >> 4)) {
            return;
        }
        double rel = Math.sqrt(d2) / (double)this.craterR;
        double h = 3.0 * (1.0 - (rel - 1.0) / 0.3) * (0.5 + BlastUtil.hash01(x, z, this.seed ^ 0x55AAL));
        int hh = (int)Math.round(h);
        if (hh <= 0) {
            return;
        }
        int top = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockState base = this.level.getBlockState(new BlockPos(x, top, z));
        if (base.isAir() || !base.isSolid() || this.level.getFluidState(new BlockPos(x, top, z)).isSource()) {
            return;
        }
        for (int k = 1; k <= hh && this.level.getBlockState(p = new BlockPos(x, top + k, z)).isAir(); ++k) {
            float r = this.rnd.nextFloat();
            BlockState rubble = r < 0.45f ? Blocks.COARSE_DIRT.defaultBlockState() : (r < 0.75f ? Blocks.GRAVEL.defaultBlockState() : ((Block)ModBlocks.SCORCHED_EARTH.get()).defaultBlockState());
            BlastUtil.set(this.level, p, rubble);
        }
    }
}

