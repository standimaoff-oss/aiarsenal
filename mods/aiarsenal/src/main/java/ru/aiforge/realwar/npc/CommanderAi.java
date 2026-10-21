package ru.aiforge.realwar.npc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The commander of a squad / regiment — one per army and era, raised as the leader of any real formation.
 *
 * <p>1812: he walks at marching pace ahead of the colours and the men dress into two ranks behind him
 * (see the formation code in {@link SoldierEntity}); he halts at musket range and lets the line fire.
 * Modern war is harsher: he stays fifty-plus metres behind the contact, moves by bounds between the squad
 * and the enemy, and only shoots when the fight reaches him. In a vehicle he rides as the commander:
 * he does not drive — he scans, picks the target and hands it to his gunner ({@code VehicleAi}).</p>
 */
final class CommanderAi {
    private CommanderAi() {
    }

    /** @return true when the commander handled this tick himself (no enemy close enough to fight personally) */
    static boolean tick(SoldierEntity s) {
        if (!(s.level() instanceof ServerLevel sl)) return false;

        if (s.isPassenger()) {
            Entity v = s.getVehicle();
            if (v instanceof ru.aiforge.realwar.vehicle.VehicleEntity veh && s.tickCount % 20 == 0) {
                VehicleAi.commanderScan(veh, s);      // eyes out: choose the target, the crew does the rest
            }
            return true;
        }

        if (s.tickCount % 20 == 0) scan(s, sl);
        LivingEntity target = s.getTarget();
        boolean napoleonic = s.era() == Era.NAPOLEONIC;

        if (target != null && target.isAlive()) {
            double d = s.distanceTo(target);
            double standOff = napoleonic ? 40.0 : 58.0;
            if (d > standOff + 8.0) {
                moveToward(s, target.position(), napoleonic ? 0.85 : 1.15);
            } else if (d < standOff - 10.0) {
                Vec3 away = s.position().subtract(target.position());
                if (away.lengthSqr() > 0.01) {
                    away = away.normalize().scale(6.0);
                    moveToward(s, s.position().add(away), 1.0);
                }
            } else {
                s.getNavigation().stop();
                s.setSprinting(false);
            }
            s.getLookControl().setLookAt(target, 25f, 25f);
            return d > 22.0;      // far from the fight: command only; close in: he fights too
        }

        march(s);
        return true;
    }

    private static void scan(SoldierEntity s, ServerLevel sl) {
        LivingEntity current = s.getTarget();
        if (current != null && current.isAlive() && s.distanceToSqr(current) < 100.0 * 100.0) return;
        Entity found = NpcTargeting.find(sl, s.position(), s.faction(), 100.0, 100.0);
        s.setTarget(found instanceof LivingEntity le ? le : null);
    }

    /**
     * No contact: the march. In 1812 a slow, dressed advance around the anchor (the men form up behind him);
     * in later wars a patrol bound from cover-ish point to point at walking pace, never sprinting.
     */
    private static void march(SoldierEntity s) {
        Vec3 anchor = s.squadAnchor();
        if (anchor == null || anchor.equals(Vec3.ZERO)) anchor = s.position();
        int leg = (s.tickCount / 360) % 4;
        double a = leg * Math.PI / 2.0 + 0.6;
        double r = s.era() == Era.NAPOLEONIC ? 14.0 : 20.0;
        Vec3 wp = anchor.add(Math.cos(a) * r, 0, Math.sin(a) * r);
        double d = NpcGear.horizontalDistance(s, wp);
        if (d > 2.5) {
            double speed = s.era() == Era.NAPOLEONIC ? 0.78 : 1.0;   // marching pace: the line must keep up
            if (s.tickCount % 10 == 0) s.getNavigation().moveTo(wp.x, wp.y, wp.z, speed);
            s.setSprinting(false);
        } else {
            s.getNavigation().stop();
        }
    }

    private static void moveToward(SoldierEntity s, Vec3 p, double speed) {
        if (s.tickCount % 10 != 0) return;
        s.getNavigation().moveTo(p.x, p.y, p.z, speed);
    }
}
