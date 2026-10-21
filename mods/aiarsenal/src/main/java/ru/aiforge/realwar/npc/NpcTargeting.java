package ru.aiforge.realwar.npc;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.vehicle.VehicleEntity;

/** Target picking for the AI armies: soldiers first, then crewed vehicles and aircraft, never own faction. */
public final class NpcTargeting {
    private NpcTargeting() {
    }

    /** Faction of an entity: the soldier's own army, or the army of the crew riding a vehicle / aircraft. */
    @Nullable
    public static Faction factionOf(Entity e) {
        if (e instanceof SoldierEntity s) return s.faction();
        if (e instanceof VehicleEntity || e instanceof CraftEntity) {
            for (Entity p : e.getPassengers()) {
                if (p instanceof SoldierEntity s) return s.faction();
            }
        }
        return null;
    }

    public static boolean crewedByPlayer(Entity e) {
        for (Entity p : e.getPassengers()) {
            if (p instanceof Player pl && !pl.isSpectator()) return true;
        }
        return false;
    }

    /** Is this entity a valid target for the given army? */
    public static boolean hostile(Faction f, Entity e) {
        if (e == null || !e.isAlive() || e.isSpectator()) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        if (e instanceof SoldierEntity s) return s.faction() != f;
        Faction cf = factionOf(e);
        if (cf != null) return cf != f;
        // an uncrewed vehicle is not a target; a player-driven one is
        return (e instanceof VehicleEntity || e instanceof CraftEntity) && crewedByPlayer(e);
    }

    /** Nearest hostile, split into infantry (priority) and hard targets. */
    @Nullable
    public static Entity find(ServerLevel sl, Vec3 from, Faction f, double infantryRange, double hardRange) {
        AABB box = new AABB(from, from).inflate(Math.max(infantryRange, hardRange));
        // a passenger is not a target of his own: the crewed hull is the target, not the man inside it
        // hulls are not LivingEntity, so this list only ever holds infantry: a passenger is hit through his hull
        List<LivingEntity> soft = sl.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.getVehicle() == null && hostile(f, e));
        Entity best = null;
        double bd = infantryRange * infantryRange;
        for (LivingEntity e : soft) {
            double d = e.position().distanceToSqr(from);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best != null) return best;
        List<Entity> hard = sl.getEntitiesOfClass(Entity.class, box, e -> (e instanceof VehicleEntity || e instanceof CraftEntity)
                && hostile(f, e));
        bd = hardRange * hardRange;
        for (Entity e : hard) {
            double d = e.position().distanceToSqr(from);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }
}
