package ru.aiforge.realwar.damage;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import ru.aiforge.realwar.vehicle.VehicleEntity;

/**
 * r20: projectile hit-test against the REAL vehicle shape instead of the entity AABB. Minecraft entity boxes are
 * square (aabbW x aabbW), so on a 9.8 m Abrams only the middle 3.7 m could be hit - rounds aimed at the front or
 * the rear of a hull flew through it. Here a segment is tested against the oriented hull box, the running gear under
 * it and the turret box turned with the turret (the same boxes the armour model {@link Armour#solve} uses), in the
 * tilted body frame. Used by shells / rockets, the gun's laser rangefinder, the client impact reticle and
 * /realwar shot, so the crosshair, the server and the hit camera always agree.
 */
public final class VehicleHit {
    private VehicleHit() {
    }

    /** World entry point of segment a-b into the vehicle, or null. */
    @Nullable
    public static Vec3 clip(VehicleEntity v, Vec3 a, Vec3 b) {
        Matrix4d frame = v.bodyFrame();
        Matrix4d inv = new Matrix4d(frame).invert();
        Vector3d la = inv.transformPosition(new Vector3d(a.x, a.y, a.z));
        Vector3d lb = inv.transformPosition(new Vector3d(b.x, b.y, b.z));
        Vector3d d = new Vector3d(lb).sub(la);
        double len = d.length();
        if (len < 1e-9) return null;
        d.div(len);
        VehicleDamage.Geo g = VehicleDamage.geo(v);
        double best = Double.MAX_VALUE;
        // hull
        double[] h = Armour.slab(la.x - g.cx(), la.y - g.cy(), la.z - g.cz(), d.x, d.y, d.z, g.hx(), g.hy(), g.hz());
        if (h != null) best = Math.min(best, Math.max(0, h[0]));
        // running gear / wheels below the hull floor
        double floor = g.cy() - g.hy();
        if (floor > 0.05) {
            double gh = floor * 0.5;
            double[] r = Armour.slab(la.x - g.cx(), la.y - gh, la.z - g.cz(), d.x, d.y, d.z, g.hx(), gh, g.hz() * 0.96);
            if (r != null) best = Math.min(best, Math.max(0, r[0]));
        }
        // turret
        float[] tb = v.dmg.broken(DamageState.TURRET_GONE) ? null : Armour.turretBox(g, v.kind);
        if (tb != null) {
            float az = VehicleDamage.turretAngles(v)[0];
            Vector3d off = Armour.rot(new Vector3d(tb[0] - g.yp()[0], 0, tb[2] - g.yp()[2]), az);
            Vector3d tc = new Vector3d(g.yp()[0] + off.x, tb[1], g.yp()[2] + off.z);
            Vector3d lo = Armour.unrot(new Vector3d(la).sub(tc), az);
            Vector3d ld = Armour.unrot(new Vector3d(d), az);
            double[] t = Armour.slab(lo.x, lo.y, lo.z, ld.x, ld.y, ld.z, tb[3], tb[4], tb[5]);
            if (t != null) best = Math.min(best, Math.max(0, t[0]));
        }
        Vec3 res = null;
        if (best <= len) {
            Vector3d p = frame.transformPosition(new Vector3d(la).add(new Vector3d(d).mul(best)));
            res = new Vec3(p.x, p.y, p.z);
        }
        // union with the entity box: never lose a hit that registered before (trucks / launchers whose armour box is
        // only the cab or the body part); the oriented boxes add the bow, the stern and the turret
        java.util.Optional<Vec3> ab = v.getBoundingBox().clip(a, b);
        if (ab.isPresent() && (res == null || ab.get().distanceToSqr(a) < res.distanceToSqr(a))) res = ab.get();
        return res;
    }

    /**
     * First entity crossed by a-b: vehicles by their real shape, everything else by its AABB grown by pad.
     * Candidates are gathered with a margin of 20 m so long hulls (Ratte, Maus) are found from their ends.
     */
    @Nullable
    public static EntityHitResult first(Level lv, @Nullable Entity self, Vec3 a, Vec3 b, double pad, Predicate<Entity> filter) {
        AABB sweep = new AABB(a, b);
        EntityHitResult best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : lv.getEntities(self, sweep.inflate(20.0), filter)) {
            Vec3 hit;
            if (e instanceof VehicleEntity v) {
                if (!e.getBoundingBox().inflate(v.spec.length() * 0.5 + 2.0).intersects(sweep)) continue;
                hit = clip(v, a, b);
            } else {
                AABB bb = e.getBoundingBox().inflate(pad);
                if (!bb.intersects(sweep.inflate(0.01))) continue;
                hit = bb.clip(a, b).orElse(bb.contains(a) ? a : null);
            }
            if (hit == null) continue;
            double dd = hit.distanceToSqr(a);
            if (dd < bd) {
                bd = dd;
                best = new EntityHitResult(e, hit);
            }
        }
        return best;
    }
}
