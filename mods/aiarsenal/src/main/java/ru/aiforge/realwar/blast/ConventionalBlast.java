/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.longs.LongOpenHashSet
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.BlockPos$MutableBlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.core.Position
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.tags.BlockTags
 *  net.minecraft.util.Mth
 *  net.minecraft.util.RandomSource
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 */
package ru.aiforge.realwar.blast;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.blast.BlastManager;
import ru.aiforge.realwar.blast.BlastPhysics;
import ru.aiforge.realwar.blast.BlastTask;
import ru.aiforge.realwar.blast.BlastUtil;
import ru.aiforge.realwar.blast.ModDamage;
import ru.aiforge.realwar.network.ConventionalEffectPacket;
import ru.aiforge.realwar.network.ModNetwork;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ConventionalBlast
implements BlastTask {
    private static final double GOLDEN = Math.PI * (3.0 - Math.sqrt(5.0));
    private final ServerLevel level;
    private final Vec3 c;
    private final double massKg;
    private final boolean surface;
    private final int rMax;
    private final double[] pStep;
    private final int nRays;
    private final double rc;
    private final double dur;
    private final double phi0;
    private final double frag;
    private final RandomSource rnd;
    private final LongOpenHashSet destroyed = new LongOpenHashSet();
    private boolean started;
    private int rayIdx;
    private long[] apply;
    private int applyIdx;

    private ConventionalBlast(ServerLevel level, Vec3 pos, double massKg, double frag) {
        this.level = level;
        this.c = pos;
        this.massKg = massKg;
        this.frag = frag;
        BlockPos bp = BlockPos.containing((Position)pos);
        BlockPos below = bp.below();
        this.surface = level.getBlockState(below).isFaceSturdy((BlockGetter)level, below, Direction.UP) || level.getBlockState(bp).isSolid();
        double range6 = BlastPhysics.rangeFor(6.0, massKg, this.surface);
        this.rMax = (int)Math.max(3.0, Math.min((double)((Integer)RWConfig.COMMON.conventionalMaxRadius.get()).intValue(), Math.ceil(range6)));
        this.pStep = new double[this.rMax + 2];
        for (int i = 0; i < this.pStep.length; ++i) {
            this.pStep[i] = BlastPhysics.overpressure(Math.max(0.3, (double)i), massKg, this.surface);
        }
        this.nRays = Math.min(14000, Math.max(300, (int)(Math.PI * (double)this.rMax * (double)this.rMax)));
        this.rc = 0.65 * Math.cbrt(massKg) * (Double)RWConfig.COMMON.craterScale.get();
        this.dur = BlastPhysics.durationFactor(massKg);
        this.rnd = RandomSource.create((long)level.random.nextLong());
        this.phi0 = this.rnd.nextDouble() * Math.PI * 2.0;
    }

    public static void explode(ServerLevel level, Vec3 pos, double massKg, double frag) {
        ConventionalBlast b = new ConventionalBlast(level, pos, massKg, frag);
        double radius = 160.0 + 40.0 * Math.cbrt(massKg);
        ModNetwork.sendNear(level, pos.x, pos.y, pos.z, radius, new ConventionalEffectPacket(pos.x, pos.y, pos.z, (float)massKg, b.surface, level.random.nextLong()));
        BlastManager.add(level, b);
    }

    @Override
    public String describe() {
        return String.format("Conventional %.0f kg rays=%d/%d destroyed=%d", this.massKg, this.rayIdx, this.nRays, this.destroyed.size());
    }

    @Override
    public boolean tick(ServerLevel lvl, long deadline) {
        if (!this.started) {
            this.started = true;
            if (((Boolean)RWConfig.COMMON.entityDamage.get()).booleanValue()) {
                this.hurtEntities();
            }
        }
        if (!((Boolean)RWConfig.COMMON.destroyBlocks.get()).booleanValue()) {
            return true;
        }
        if (this.rayIdx < this.nRays) {
            while (this.rayIdx < this.nRays) {
                this.castRay(this.rayIdx++);
                if ((this.rayIdx & 0x3F) != 0 || System.nanoTime() <= deadline) continue;
                return false;
            }
        }
        if (this.apply == null) {
            this.apply = this.destroyed.toLongArray();
            Arrays.sort(this.apply);
        }
        while (this.applyIdx < this.apply.length) {
            BlockPos p;
            BlockState s;
            if ((s = BlastUtil.stateOrNull(this.level, p = BlockPos.of((long)this.apply[this.applyIdx++]))) != null && !s.isAir()) {
                BlastUtil.destroy(this.level, p, s, false);
            }
            if ((this.applyIdx & 0xFF) != 0 || System.nanoTime() <= deadline) continue;
            return false;
        }
        return true;
    }

    private void castRay(int k) {
        double yy = 1.0 - 2.0 * ((double)k + 0.5) / (double)this.nRays;
        double rr = Math.sqrt(Math.max(0.0, 1.0 - yy * yy));
        double phi = (double)k * GOLDEN + this.phi0;
        double dx = Math.cos(phi) * rr;
        double dy = yy;
        double dz = Math.sin(phi) * rr;
        double trans = 0.92 + 0.16 * this.rnd.nextDouble();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        long last = Long.MIN_VALUE;
        for (int i = 1; i <= this.rMax; ++i) {
            double p;
            mp.set(this.c.x + dx * (double)i, this.c.y + dy * (double)i, this.c.z + dz * (double)i);
            long key = mp.asLong();
            if (key == last) continue;
            last = key;
            BlockState s = BlastUtil.stateOrNull(this.level, (BlockPos)mp);
            if (s == null || (p = this.pStep[i] * trans) < 1.5) break;
            if (s.isAir()) continue;
            if (BlastUtil.isUnbreakable(s)) break;
            if (BlastUtil.isNaturalGround(s)) {
                double lim = this.rc * (0.8 + 0.4 * BlastUtil.hash01(mp.getX(), mp.getZ(), (long)this.massKg)) * (s.is(BlockTags.BASE_STONE_OVERWORLD) ? 0.75 : 1.0);
                if (!((double)i <= lim)) break;
                this.destroyed.add(key);
                trans *= 0.9;
                continue;
            }
            double t = BlastPhysics.blockThresholdKPa(s.getBlock().getExplosionResistance(), this.dur);
            if (!(p >= t)) break;
            this.destroyed.add(key);
            trans *= Math.max(0.35, 1.0 - 0.6 * t / p);
        }
    }

    private void hurtEntities() {
        double range = Math.max((double)this.rMax, this.frag > 0.0 ? this.fragRadius() : 0.0);
        AABB box = new AABB(this.c, this.c).inflate(range);
        List list = this.level.getEntities((Entity)null, box, e -> !e.isSpectator() && !(e instanceof ru.aiforge.realwar.damage.DebrisEntity d && d.tickCount < 4));
        double fr = this.fragRadius();
        for (Entity e2 : (Iterable<Entity>)(Iterable<?>)(list)) {
            double[] tr;
            double pe;
            double dist;
            block5: {
                double kb;
                Vec3 dir;
                block6: {
                    Vec3 pos = e2.position().add(0.0, (double)e2.getBbHeight() * 0.5, 0.0);
                    dist = pos.distanceTo(this.c);
                    if (dist > range) continue;
                    pe = BlastPhysics.overpressure(Math.max(0.3, dist), this.massKg, this.surface);
                    tr = BlastUtil.trace(this.level, this.c, pos, 1.0);
                    dir = dist < 0.001 ? new Vec3(0.0, 1.0, 0.0) : pos.subtract(this.c).scale(1.0 / dist);
                    kb = Math.min(3.5, Math.sqrt(Math.max(0.0, pe *= Math.exp(-tr[0] / 18.0))) / 7.0);
                    if (!(kb > 0.05)) break block5;
                    if (!(e2 instanceof Player)) break block6;
                    Player p = (Player)e2;
                    if (p.getAbilities().flying) break block5;
                }
                e2.setDeltaMovement(e2.getDeltaMovement().add(dir.x * kb, Math.min(1.4, kb * 0.5 + 0.2), dir.z * kb));
                e2.hurtMarked = true;
            }
            if (e2 instanceof ru.aiforge.realwar.damage.Damageable dm && !(e2 instanceof LivingEntity)) {
                dm.blastNear(this.c, this.massKg, dist);
            }
            if (!(e2 instanceof LivingEntity)) continue;
            LivingEntity le = (LivingEntity)e2;
            float dmg = BlastPhysics.blastDamage(pe);
            if (dmg > 0.0f) {
                le.hurt(ModDamage.source((Level)this.level, ModDamage.BLAST), dmg);
            }
            if (!(this.frag > 0.0) || !(dist < fr)) continue;
            double vis = Math.exp(-tr[0] / 3.0);
            float fd = (float)(16.0 * Mth.square((double)(1.0 - dist / fr)) * vis * this.frag);
            if (!(fd >= 0.5f)) continue;
            le.invulnerableTime = 0;
            le.hurt(ModDamage.source((Level)this.level, ModDamage.FRAGMENTS), fd);
        }
    }

    private double fragRadius() {
        return 18.0 * Math.cbrt(this.massKg) * this.frag;
    }
}

