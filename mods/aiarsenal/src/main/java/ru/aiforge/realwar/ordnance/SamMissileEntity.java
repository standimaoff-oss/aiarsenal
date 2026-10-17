/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 */
package ru.aiforge.realwar.ordnance;

import ru.aiforge.realwar.blast.ModDamage;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.ordnance.RocketEntity;
import ru.aiforge.realwar.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SamMissileEntity
extends RocketEntity {
    public static final double N_GAIN = 4.0;
    public static final double MAX_G = 25.0;
    public static final double VERTICAL_PHASE = 3.0;
    public static final double FUZE_RADIUS = 5.5;
    private int targetId = -1;
    private double prevDist = Double.MAX_VALUE;
    private double lostAt = -1.0;

    public SamMissileEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
    }

    public static SamMissileEntity launch(ServerLevel level, Vec3 muzzle, Vec3 dir, int targetId, int sourceVehicleId) {
        SamMissileEntity e = new SamMissileEntity((EntityType<? extends RocketEntity>)((EntityType)ModEntities.SAM_MISSILE.get()), (Level)level);
        e.init(Ballistics.Profile.SAM_48N6, muzzle, dir, sourceVehicleId);
        e.targetId = targetId;
        level.addFreshEntity((Entity)e);
        return e;
    }

    public int targetId() {
        return this.targetId;
    }

    private Entity target() {
        if (this.targetId < 0) {
            return null;
        }
        Entity t = this.level().getEntity(this.targetId);
        return t != null && t.isAlive() ? t : null;
    }

    private static Vec3 center(Entity t) {
        AABB b = t.getBoundingBox();
        return b.getCenter();
    }

    @Override
    protected double maxFlightTime() {
        return 150.0;
    }

    @Override
    protected void guide(int sub, int subs, double[] out) {
        double lim;
        double am;
        out[2] = 0.0;
        out[1] = 0.0;
        out[0] = 0.0;
        double t = this.flight.t;
        if (t < 3.0) {
            return;
        }
        Entity tg = this.target();
        if (tg == null) {
            if (this.lostAt < 0.0) {
                this.lostAt = t;
            }
            return;
        }
        double k = Ballistics.scaleK();
        double tickReal = (double)Ballistics.substeps() * Ballistics.dtStep();
        Vec3 c = SamMissileEntity.center(tg);
        Vec3 dv = tg.getDeltaMovement();
        double f = (double)sub / (double)subs;
        double tx = (c.x + dv.x * f - this.origin.x) * k;
        double ty = (c.y + dv.y * f - this.origin.y) * k;
        double tz = (c.z + dv.z * f - this.origin.z) * k;
        double tvx = dv.x * k / tickReal;
        double tvy = dv.y * k / tickReal;
        double tvz = dv.z * k / tickReal;
        double rx = tx - this.flight.x;
        double ry = ty - this.flight.y;
        double rz = tz - this.flight.z;
        double r2 = rx * rx + ry * ry + rz * rz;
        if (r2 < 1.0) {
            return;
        }
        double vrx = tvx - this.flight.vx;
        double vry = tvy - this.flight.vy;
        double vrz = tvz - this.flight.vz;
        double ox = (ry * vrz - rz * vry) / r2;
        double oy = (rz * vrx - rx * vrz) / r2;
        double oz = (rx * vry - ry * vrx) / r2;
        double ax = 4.0 * (oy * this.flight.vz - oz * this.flight.vy);
        double ay = 4.0 * (oz * this.flight.vx - ox * this.flight.vz);
        double az = 4.0 * (ox * this.flight.vy - oy * this.flight.vx);
        if ((am = Math.sqrt(ax * ax + (ay += 9.80665) * ay + az * az)) > (lim = 245.16625)) {
            double s = lim / am;
            ax *= s;
            ay *= s;
            az *= s;
        }
        double eff = Math.min(1.0, Math.max(0.0, (this.flight.speed() - 60.0) / 140.0));
        out[0] = ax * eff;
        out[1] = ay * eff;
        out[2] = az * eff;
    }

    @Override
    protected boolean afterSubstep(int sub, int subs, Vec3 pos) {
        if (this.flight.t < 3.2) {
            return false;
        }
        Entity tg = this.target();
        if (tg == null) {
            return this.lostAt >= 0.0 && this.flight.t - this.lostAt > 6.0;
        }
        double d = pos.distanceTo(SamMissileEntity.center(tg));
        boolean close = d < 5.5;
        boolean passed = d > this.prevDist && this.prevDist < 14.0;
        this.prevDist = d;
        return close || passed;
    }

    @Override
    protected void impact(ServerLevel sl, Vec3 pos, Entity direct) {
        this.detonate(sl, pos, direct);
    }

    @Override
    protected void detonate(ServerLevel sl, Vec3 pos, Entity direct) {
        double d;
        if (this.exploded) {
            return;
        }
        Entity tg = this.target();
        if (tg != null && (d = pos.distanceTo(SamMissileEntity.center(tg))) < 14.0) {
            ru.aiforge.realwar.damage.HitContext.hurt(tg, ModDamage.source((Level)sl, ModDamage.ORDNANCE), (float)(220.0 * Math.max(0.0, 1.0 - d / 14.0)), pos, null, ru.aiforge.realwar.damage.HitContext.Kind.PROX, 220.0);
        }
        super.detonate(sl, pos, direct);
    }
}

