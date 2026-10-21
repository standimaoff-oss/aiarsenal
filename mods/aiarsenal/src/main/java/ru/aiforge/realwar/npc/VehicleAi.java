package ru.aiforge.realwar.npc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.vehicle.GunVehicle;
import ru.aiforge.realwar.vehicle.LauncherVehicle;
import ru.aiforge.realwar.vehicle.VehicleEntity;

/**
 * Ground-vehicle behaviour of an AI crew.
 *
 * <p>The crew does one thing at a time, like a real one: it drives to where the squad (or the fight) is, stops before
 * shooting, lays the gun on a point that is close to - but never exactly - the target, and backs out if the hull is
 * wedged against terrain. The squad leash and the "own soldiers in front" check keep it from driving off alone or
 * over its own riflemen.</p>
 */
final class VehicleAi {
    /** The crew keeps the hull this close to the squad; further out it drives back on its own. */
    private static final double SQUAD_LEASH = 12.0;
    private static final double SQUAD_HARD_LEASH = 40.0;
    private static final double ENGAGE_MAX = 240.0;

    private VehicleAi() {
    }

    static void tick(VehicleEntity v, SoldierEntity s) {
        if (!(v.level() instanceof ServerLevel sl)) return;
        if (--v.aiThink <= 0) {
            v.aiThink = 10;
            think(v, s, sl);
        }
        Entity target = v.aiTargetId < 0 ? null : sl.getEntity(v.aiTargetId);
        if (target != null && (!target.isAlive() || target.distanceTo(v) > ENGAGE_MAX * 1.2 || !NpcTargeting.hostile(s.faction(), target))) {
            target = null;
            v.aiTargetId = -1;
        }

        Vec3 leader = s.squadAnchor();
        double squadDist = leader == null || leader.equals(Vec3.ZERO) ? 0 : leader.distanceTo(v.position());

        // --- knocked about but not knocked out: fall back and let the crew repair (module repair needs a halt)
        boolean battered = v.dmg.any() || v.hp() < v.kind.hp * 0.45f;
        boolean canRetreat = ru.aiforge.realwar.damage.VehicleDamage.canDrive(v);   // false: track/engine gone
        if (battered) {
            if (!canRetreat) {
                v.setAiInput(0f, 0f, true);                   // cannot move: repair where she stands
                return;
            }
            if (target != null) {
                Vec3 away = v.position().subtract(target.position());
                if (away.lengthSqr() > 0.01) {
                    Vec3 back = v.position().add(away.normalize().scale(35.0));
                    if (leader != null && !leader.equals(Vec3.ZERO)) {
                        back = back.add(leader.subtract(v.position()).scale(0.35));   // retire toward your own
                    }
                    driveToward(v, back);
                }
                return;
            }
            v.setAiInput(0f, 0f, true);                       // safe: halt and repair
            return;
        }

        Vec3 goal = null;
        if (leader != null && squadDist > SQUAD_LEASH) {
            goal = leader;                                    // never wander off from the squad
        } else if (target != null && target.distanceTo(v) > 105.0) {
            goal = target.position();                         // close the distance to the fight
        }

        float throttle = 0f;
        float steer = 0f;
        boolean brake = false;

        if (v.aiReverse > 0) {
            v.aiReverse--;
            throttle = -0.55f;
            steer = (v.tickCount % 40 < 20) ? 0.8f : -0.8f;
        } else if (goal != null) {
            double dx = goal.x - v.getX();
            double dz = goal.z - v.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float err = Mth.wrapDegrees(want - v.getYRot());
            if (Math.abs(err) > 110.0f) {
                brake = true;
                steer = Mth.clamp(-err / 30.0f, -1f, 1f);
            } else {
                steer = Mth.clamp(-err / 26.0f, -1f, 1f);
                throttle = Mth.clamp((float) (dist / 30.0), 0.22f, 0.8f);
                if (Math.abs(err) > 50.0f) throttle *= 0.3f;
            }
            if (blockedAhead(v, sl)) {
                if (Math.abs(v.speedMs) < 1.0) {
                    v.aiReverse = 30;
                } else {
                    brake = true;
                }
            }
        } else if (target != null) {
            float err = headingError(v, target.position());
            if (Math.abs(err) > 70.0f) {
                steer = Mth.clamp(-err / 30.0f, -1f, 1f);
                throttle = 0.2f;
            } else if (Math.abs(v.speedMs) > 0.35) {
                brake = true;                                 // a stationary hull is a stable gun platform
            }
        }

        if (squadDist > SQUAD_HARD_LEASH + 30.0) throttle = Math.max(throttle, 0.9f);
        if (ownSoldierAhead(v, sl)) {
            throttle = 0f;
            brake = true;                                     // never run over the squad
        }

        v.setAiInput(throttle, steer, brake);
    }

    /** A commander riding in the hull does the looking: same scan, handed to his gunner. */
    static void commanderScan(VehicleEntity v, SoldierEntity s) {
        if (v.level() instanceof ServerLevel sl) think(v, s, sl);
    }

    private static void think(VehicleEntity v, SoldierEntity s, ServerLevel sl) {
        Vec3 eye = v.position().add(0, v.spec.height() * 0.7, 0);
        // an AA crew lives for aircraft: nothing on the ground outranks a hostile plane
        Entity found = v.kind.isFlak() ? NpcTargeting.findAir(sl, v.position(), s.faction(), 420.0) : null;
        if (found == null) found = NpcTargeting.find(sl, eye, s.faction(),
                v instanceof GunVehicle ? 210.0 : 260.0, 260.0);
        if (found != null && found.getVehicle() == v) found = null;
        v.aiTargetId = found == null ? -1 : found.getId();
        if (found != null) {
            aim(v, s, sl, found);
        } else {
            // no target left: stop laying the gun and hold fire
            if (v instanceof GunVehicle g) {
                g.aiAim = null;
                g.aiTrigger = false;
            } else if (v instanceof LauncherVehicle l) {
                l.aiAim = null;
                l.aiTrigger = false;
            }
        }
    }

    /** Lays the gun on the target with a rank-scaled error: privates miss, sergeants mostly do not. */
    private static void aim(VehicleEntity v, SoldierEntity s, ServerLevel sl, Entity target) {
        double skill = s.rank().skill;
        Vec3 from;
        if (v instanceof GunVehicle g) {
            from = g.sightWorld();
            Vec3 p = NpcGear.vehicleAimPoint(from, target, skill, sl.random);
            // a gunner keeps re-ranging: the point drifts slightly every think step instead of snapping
            g.aiAim = p;
            g.aiSpread = (float) (1.4 + (1.0 - skill) * 8.0);
            g.aiTrigger = true;
        } else if (v instanceof LauncherVehicle l) {
            from = v.position().add(0, v.spec.height() * 0.6, 0);
            l.aiAim = NpcGear.vehicleAimPoint(from, target, skill * 0.8, sl.random);
            l.aiSpread = (float) (1.2 + (1.0 - skill) * 5.0);
            l.aiTrigger = true;
        }
    }

    /** Steers the hull at a point at cruising throttle (retreat driving; shooting continues via think()). */
    private static void driveToward(VehicleEntity v, Vec3 goal) {
        double dx = goal.x - v.getX();
        double dz = goal.z - v.getZ();
        float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float err = Mth.wrapDegrees(want - v.getYRot());
        float steer = Mth.clamp(-err / 26.0f, -1f, 1f);
        float throttle = Math.abs(err) > 110.0f ? 0f : 0.65f;
        if (Math.abs(err) > 50.0f) throttle *= 0.4f;
        boolean brake = Math.abs(err) > 110.0f;
        if (v.level() instanceof ServerLevel sl && ownSoldierAhead(v, sl)) {
            throttle = 0f;
            brake = true;
        }
        v.setAiInput(throttle, steer, brake);
    }

    private static float headingError(VehicleEntity v, Vec3 to) {
        float want = (float) Math.toDegrees(Math.atan2(-(to.x - v.getX()), to.z - v.getZ()));
        return Mth.wrapDegrees(want - v.getYRot());
    }

    /** Terrain step or wall directly in the driving direction. */
    private static boolean blockedAhead(VehicleEntity v, ServerLevel sl) {
        Vec3 f = v.forward();
        double probe = v.spec.length() * 0.5 + 6.0;
        double px = v.getX() + f.x * probe;
        double pz = v.getZ() + f.z * probe;
        int gy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(px), Mth.floor(pz));
        if (gy - v.getY() > 2.2) return true;
        Vec3 a = v.position().add(0, 1.8, 0);
        Vec3 b = a.add(f.scale(probe));
        HitResult r = sl.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, v));
        return r.getType() == HitResult.Type.BLOCK
                && ((net.minecraft.world.phys.BlockHitResult) r).getBlockPos().getY() > v.getY() + 1.3;
    }

    /** Any friendly rifleman in the strip ahead of the hull: stop instead of crushing him. */
    private static boolean ownSoldierAhead(VehicleEntity v, ServerLevel sl) {
        Vec3 f = v.forward();
        Vec3 c = v.position().add(f.scale(v.spec.length() * 0.5 + 3.0));
        for (SoldierEntity ally : sl.getEntitiesOfClass(SoldierEntity.class,
                new net.minecraft.world.phys.AABB(c.x - 4, c.y - 2, c.z - 4, c.x + 4, c.y + 2, c.z + 4),
                e -> e.isAlive() && e.getVehicle() == null)) {
            return true;
        }
        return false;
    }

    /** Called when the crew dismounts or the vehicle dies. */
    static void release(VehicleEntity v) {
        v.clearAiInput();
        v.aiTargetId = -1;
        if (v instanceof GunVehicle g) {
            g.aiAim = null;
            g.aiTrigger = false;
        } else if (v instanceof LauncherVehicle l) {
            l.aiAim = null;
            l.aiTrigger = false;
        }
    }
}
