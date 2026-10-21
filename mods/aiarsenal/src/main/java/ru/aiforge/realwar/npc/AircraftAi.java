package ru.aiforge.realwar.npc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.craft.PlaneEntity;

/**
 * Pilots of the AI armies: proper take-off runs and climbs for aeroplanes, collective/VRS-aware hovering for
 * helicopters, terrain separation, banked turns, and strafing runs on hostile soldiers, vehicles and aircraft.
 *
 * <p>Inputs are the same {@code throttle,steer,mouseX,mouseY,up,down} array a player's keyboard and mouse produce
 * (see {@link CraftEntity#inputLayout()}), so the flight models stay untouched. Helicopters are flown with the
 * stabilisation (SAS) mode on - the pilots hold a hover and command a vertical speed instead of hand-flying the
 * collective, which is exactly what the mode is for.</p>
 */
final class AircraftAi {
    private static final int CRUISE_CLEARANCE = 26;

    private AircraftAi() {
    }

    static void fly(CraftEntity c, SoldierEntity s) {
        if (!(c.level() instanceof ServerLevel sl) || !c.kind.isAir()) return;
        if (!c.engineOn() && c.fuel() > 0.01f) c.setEngine(true);
        if (--c.aiThink <= 0) {
            c.aiThink = 20;
            think(c, s, sl);
        }
        Entity target = c.aiTargetId < 0 ? null : sl.getEntity(c.aiTargetId);
        if (target != null && (!target.isAlive() || target.distanceTo(c) > 700.0 || !NpcTargeting.hostile(s.faction(), target))) {
            target = null;
            c.aiTargetId = -1;
        }
        Vec3 wp = waypoint(c, s, target);
        if (c.kind.isHeli()) heli(c, s, target, wp, sl);
        else plane(c, s, target, wp, sl);
        evade(c, s, sl);
        trigger(c, target);
    }

    /**
     * Under AA fire or with a missile out: pop flares and weave — gently. A hard break bleeds the speed
     * a fixed wing needs to stay airborne and throws the helicopter into the ground, so the jink is a
     * shallow, rolling S-turn that keeps the aircraft flying while it spoils the gunner's lead.
     */
    private static void evade(CraftEntity c, SoldierEntity s, ServerLevel sl) {
        if (!threatened(c, s, sl)) return;
        if (c.tickCount % 24 == 0) c.fireFlares();
        float[] in = c.forcedInput();
        if (in == null || in.length < 4) return;
        double wave = Math.sin(c.tickCount * 0.22);
        in[2] = Mth.clamp(in[2] + (float) wave * 0.38f, -1f, 1f);        // yaw/roll weave
        in[3] = Mth.clamp(in[3] + (float) Math.cos(c.tickCount * 0.22) * 0.10f, -0.6f, 0.6f);
        if (in.length > 4) in[4] = Math.max(in[4], 0.25f);               // helicopters also climb out of the cone
        c.setForcedInput(in);
    }

    private static boolean threatened(CraftEntity c, SoldierEntity s, ServerLevel sl) {
        // a launched missile homing on us
        for (ru.aiforge.realwar.handheld.AtMissileEntity m : sl.getEntitiesOfClass(
                ru.aiforge.realwar.handheld.AtMissileEntity.class, c.getBoundingBox().inflate(170.0))) {
            Entity owner = m.getOwner();
            if (owner == null || owner == c) continue;
            if (NpcTargeting.hostile(s.faction(), owner)) return true;
        }
        // a crewed flak gun or an enemy aircraft close enough to matter
        for (Entity e : sl.getEntitiesOfClass(Entity.class, c.getBoundingBox().inflate(320.0),
                e -> e != c && (e instanceof ru.aiforge.realwar.vehicle.FlakEntity
                        || e instanceof CraftEntity) && NpcTargeting.hostile(s.faction(), e))) {
            return true;
        }
        return false;
    }

    private static void think(CraftEntity c, SoldierEntity s, ServerLevel sl) {
        boolean air = c.kind.isAir();
        Entity found = NpcTargeting.find(sl, c.position(), s.faction(), air ? 480.0 : 200.0, air ? 520.0 : 220.0);
        if (found != null && found.getVehicle() == c) found = null;
        c.aiTargetId = found == null ? -1 : found.getId();
    }

    /** Where the pilot is heading: the squad, or an orbit/attack point around the target. */
    private static Vec3 waypoint(CraftEntity c, SoldierEntity s, Entity target) {
        if (target != null) {
            Vec3 tp = target.position();
            Vec3 flat = new Vec3(c.getX() - tp.x, 0, c.getZ() - tp.z);
            if (flat.lengthSqr() < 1.0) flat = new Vec3(1, 0, 0);
            flat = flat.normalize();
            double orbit = c.kind.isHeli() ? 70.0 : 220.0;
            return tp.add(flat.x * orbit, 22.0, flat.z * orbit);
        }
        Vec3 leader = s.squadAnchor();
        if (leader == null || leader.equals(Vec3.ZERO)) leader = c.position();
        double r = c.kind.isHeli() ? 55.0 : 120.0;
        return new Vec3(leader.x + Math.cos(c.tickCount * 0.004) * r, 0, leader.z + Math.sin(c.tickCount * 0.004) * r);
    }

    // ------------------------------------------------------------------ helicopter

    private static void heli(CraftEntity c, SoldierEntity s, Entity target, Vec3 wp, ServerLevel sl) {
        if (!c.flag(CraftEntity.FLAG_STAB)) c.setFlag(CraftEntity.FLAG_STAB, true);
        if (!c.flag(CraftEntity.FLAG_GEAR)) c.setFlag(CraftEntity.FLAG_GEAR, true);

        int gy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(wp.x), Mth.floor(wp.z));
        boolean nearSquad = s.squadAnchor() != null && s.squadAnchor().distanceTo(c.position()) < 42.0;
        double wantY = Math.max(gy + CRUISE_CLEARANCE, c.getY());
        if (nearSquad) wantY = Math.max(wantY, c.getY() + 8.0);           // rotor wash: never hover on the squad
        if (target != null) wantY = Math.max(wantY, target.getY() + 18.0);
        if (c.getY() < gy + 12 && c.vel.y < 0) wantY = Math.max(wantY, gy + 30);

        double dy = wantY - c.getY();
        float up = (float) Mth.clamp(dy * 0.22 - c.vel.y * 0.10, -0.75, 0.8);

        double dx = wp.x - c.getX();
        double dz = wp.z - c.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yawWant = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float yawErr = Mth.wrapDegrees(yawWant - c.getYRot());
        float yaw = Mth.clamp(yawErr / 14.0f, -1f, 1f);

        double speedWant = dist > 120 ? 26.0 : dist > 45 ? 18.0 : 6.0;
        Vec3 hv = new Vec3(c.vel.x, 0, c.vel.z);
        double speed = hv.length();
        // nose down to accelerate, nose up to bleed speed (positive pitch = nose up in this model)
        float pitch = (float) Mth.clamp((speed - speedWant) * 0.05, -0.55, 0.35);
        if (Math.abs(yawErr) > 70) pitch *= 0.4f;

        c.setForcedInput(up, 0f, yaw, pitch, 0f, 0f);
    }

    // ------------------------------------------------------------------ aeroplane

    private static void plane(CraftEntity c, SoldierEntity s, Entity target, Vec3 wp, ServerLevel sl) {
        if (!(c instanceof PlaneEntity p)) return;      // e.g. a float-plane class with its own model

        double speed = c.vel.length();
        boolean onGround = p.contactOrGround() && speed < p.vStall * 1.35;
        int gy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(wp.x), Mth.floor(wp.z));
        int gyHere = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(c.getX()), Mth.floor(c.getZ()));
        int ahead = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(c.getX() + c.vel.x * 10), Mth.floor(c.getZ() + c.vel.z * 10));

        // --- take-off run: full throttle, wings level, then rotate at 1.18 x stall speed
        if (onGround) {
            if (!p.gearDown()) c.setFlag(CraftEntity.FLAG_GEAR, true);
            float roll = -c.roll * 0.08f;
            float rotate = speed > p.vStall * 1.18 ? 0.30f : 0f;      // rotate just above the stall speed
            c.setForcedInput(1.0f, 0f, roll, rotate, 0f, 0f);
            return;
        }

        // --- altitude: cruise above terrain, hard floor over rising ground ahead
        double wantY = Math.max(gy + CRUISE_CLEARANCE + 12, Math.max(gyHere, ahead) + CRUISE_CLEARANCE);
        if (target != null) wantY = Math.max(wantY, target.getY() + 30.0);
        double dy = wantY - c.getY();
        float pitch = (float) Mth.clamp(dy * 0.02 - c.vel.y * 0.09, -0.45, 0.55);
        if (c.getY() - Math.max(gyHere, ahead) < 14) pitch = Math.max(pitch, 0.6f);
        if (p.vStall > speed * 1.05) pitch = Math.min(pitch, -0.35f);

        // --- heading: banked turn; the flight model turns through the lift vector
        double dx = wp.x - c.getX();
        double dz = wp.z - c.getZ();
        float yawWant = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float yawErr = Mth.wrapDegrees(yawWant - c.getYRot());
        float wantRoll = (float) Mth.clamp(yawErr * 1.3, -55.0, 55.0);   // bank into the turn (positive roll = right)
        if (target != null && Math.abs(yawErr) < 45 && c.distanceTo(target) < 420) {
            // attack run: roll wings level on the final stretch so the guns and bombs bear
            wantRoll *= 0.35f;
        }
        float roll = (float) Mth.clamp((wantRoll - c.roll) / 9.0, -1.0, 1.0);
        float rudder = Mth.clamp(yawErr / 90.0f, -0.6f, 0.6f);
        c.setForcedInput(1.0f, rudder, roll, pitch, 0f, 0f);
    }

    /** Frees the controls when the pilot dies: no stale forced input on a crewless aircraft. */
    static void release(CraftEntity c) {
        c.setForcedInput(0f, 0f, 0f, 0f, 0f, 0f);
        c.aiFire(false);
        c.aiTargetId = -1;
    }

    // ------------------------------------------------------------------ weapons

    private static void trigger(CraftEntity c, Entity target) {
        if (target == null) {
            c.aiFire(false);
            return;
        }
        Vec3 f = c.basis()[0];
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(c.position());
        double dist = to.length();
        boolean aligned = f.dot(to.normalize()) > 0.94;
        boolean range = dist < (c.kind.isHeli() ? 330.0 : 520.0);
        c.aiFire(aligned && range);
    }
}
