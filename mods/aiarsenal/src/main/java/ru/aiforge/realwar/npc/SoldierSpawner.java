package ru.aiforge.realwar.npc;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.craft.CraftKind;
import ru.aiforge.realwar.craft.CraftRegistry;
import ru.aiforge.realwar.item.VehicleItem;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.vehicle.VehicleEntity;
import ru.aiforge.realwar.vehicle.VehicleKind;

/**
 * Raises AI squads: rank mix, squad wiring (leader + anchor), and the crews / pilots for the squad's own nation and
 * era. Two or three soldiers per nation are the default; a squad never mixes armies or periods.
 */
public final class SoldierSpawner {
    private SoldierSpawner() {
    }

    // ------------------------------------------------------------------ entity type -> army / period

    public static Faction factionOf(EntityType<?> t) {
        if (t == ModEntities.SOLDIER_NATO.get()) return Faction.NATO;
        if (t == ModEntities.SOLDIER_GERMANY.get()) return Faction.GERMANY;
        if (t == ModEntities.SOLDIER_FRANCE.get()) return Faction.FRANCE;
        return Faction.RUSSIA;
    }

    public static Era eraOf(EntityType<?> t) {
        if (t == ModEntities.SOLDIER_GERMANY.get()) return Era.WW2;
        if (t == ModEntities.SOLDIER_USSR.get()) return Era.WW2;
        if (t == ModEntities.SOLDIER_FRANCE.get()) return Era.NAPOLEONIC;
        return Era.MODERN;
    }

    public static EntityType<? extends SoldierEntity> typeFor(Faction f, Era era) {
        return switch (f) {
            case NATO -> ModEntities.SOLDIER_NATO.get();
            case GERMANY -> ModEntities.SOLDIER_GERMANY.get();
            case FRANCE -> ModEntities.SOLDIER_FRANCE.get();
            case RUSSIA -> era == Era.WW2 ? ModEntities.SOLDIER_USSR.get() : ModEntities.SOLDIER_RUSSIA.get();
        };
    }

    // ------------------------------------------------------------------ spawning

    @Nullable
    public static SoldierEntity spawnOne(ServerLevel sl, Vec3 at, Faction f, Era era, Rank rank) {
        EntityType<? extends SoldierEntity> type = typeFor(f, era);
        SoldierEntity s = type.create(sl);
        if (s == null) return null;
        Vec3 p = groundPoint(sl, at);
        s.moveTo(p.x, p.y, p.z, sl.random.nextFloat() * 360.0f, 0.0f);
        s.initWith(f, era, rank);
        sl.addFreshEntity(s);
        return s;
    }

    /**
     * A full squad: one leader (sergeant, or an officer for six and more), riflemen, plus a vehicle crew and a pilot
     * when the army has hardware for that period. All of them share a squad id and the leader as their anchor.
     */
    public static List<SoldierEntity> spawnSquad(ServerLevel sl, Vec3 at, Faction f, Era era, int count,
                                                 boolean withVehicle, boolean withAircraft) {
        if (!f.allows(era)) return List.of();
        int n = Mth.clamp(count, 1, 24);
        int squadId = Math.abs(sl.random.nextInt()) % 90000 + 1000;
        List<SoldierEntity> squad = new ArrayList<>();
        Rank leaderRank = n >= 6 ? Rank.OFFICER : Rank.SERGEANT;

        for (int i = 0; i < n; i++) {
            Rank rank = i == 0 ? leaderRank : Rank.PRIVATE;
            Vec3 p = at;
            if (i > 0) {
                double a = sl.random.nextDouble() * Math.PI * 2.0;
                double r = 2.0 + sl.random.nextDouble() * 4.0;
                p = at.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            }
            SoldierEntity s = spawnOne(sl, p, f, era, rank);
            if (s != null) squad.add(s);
        }
        if (squad.isEmpty()) return squad;

        SoldierEntity leader = squad.get(0);
        for (SoldierEntity s : squad) {
            s.setSquad(squadId, leader.getId(), leader.position());
        }

        int crewIdx = -1;
        int pilotIdx = -1;
        // 1812 armies have no motor vehicles, but they still need a gun crew for their field pieces
        if (squad.size() >= 3 && (era == Era.NAPOLEONIC || withVehicle && f.vehicles(era).length > 0)) crewIdx = 1;
        if (withAircraft && f.aircraft(era).length > 0 && squad.size() >= 4) pilotIdx = Math.min(2, squad.size() - 1);
        if (pilotIdx > 0 && pilotIdx == crewIdx) pilotIdx = Math.min(3, squad.size() - 1);

        if (crewIdx > 0) {
            SoldierEntity crew = squad.get(crewIdx);
            crew.initWith(f, era, Rank.CREW);
            VehicleEntity v = withVehicle ? spawnVehicle(sl, at.add(7, 0, 7), f, era) : null;
            if (v != null) {
                crew.setAssignedVehicle(v.getId());
            } else if (era == Era.NAPOLEONIC) {
                spawnCannon(sl, at.add(5, 0, 0), f);          // 1812: the gun comes with its crew instead
            }
        }
        if (pilotIdx > 0) {
            SoldierEntity pilot = squad.get(pilotIdx);
            pilot.initWith(f, era, Rank.PILOT);
            CraftEntity c = spawnAircraft(sl, at.add(-14, 0, 12), f, era);
            if (c != null) pilot.setAssignedCraft(c.getId());
        }
        return squad;
    }

    @Nullable
    public static VehicleEntity spawnVehicle(ServerLevel sl, Vec3 at, Faction f, Era era) {
        VehicleKind[] kinds = f.vehicles(era);
        if (kinds.length == 0) return null;
        VehicleKind kind = kinds[sl.random.nextInt(kinds.length)];
        Entity e = VehicleItem.typeOf(kind).create(sl);
        if (!(e instanceof VehicleEntity v)) return null;
        Vec3 p = groundPoint(sl, at);
        v.moveTo(p.x, p.y, p.z, sl.random.nextFloat() * 360.0f, 0.0f);
        if (!sl.noCollision(v, v.getBoundingBox())) v.moveTo(p.x, p.y + 1.0, p.z);
        if (v instanceof ru.aiforge.realwar.vehicle.LauncherVehicle lv && lv.loadedCount() == 0) {
            lv.fillMagazine();                 // an AI crew deploys with a full rack, like a player-built vehicle
        }
        sl.addFreshEntity(v);
        return v;
    }

    /** r22: a national powder gun for the 1812 armies (France: 6-pounder, Russia: 12-pounder). */
    @Nullable
    public static ru.aiforge.realwar.napoleonic.PowderCannonEntity spawnCannon(ServerLevel sl, Vec3 at, Faction f) {
        net.minecraft.world.entity.EntityType<ru.aiforge.realwar.napoleonic.PowderCannonEntity> type;
        if (f == Faction.FRANCE) type = ModEntities.CANNON_FR6.get();
        else if (f == Faction.RUSSIA) type = ModEntities.CANNON_RI12.get();
        else return null;
        ru.aiforge.realwar.napoleonic.PowderCannonEntity c = type.create(sl);
        if (c == null) return null;
        Vec3 p = groundPoint(sl, at);
        c.moveTo(p.x, p.y, p.z, sl.random.nextFloat() * 360.0f, 0.0f);
        sl.addFreshEntity(c);
        return c;
    }

    @Nullable
    public static CraftEntity spawnAircraft(ServerLevel sl, Vec3 at, Faction f, Era era) {
        CraftKind[] kinds = f.aircraft(era);
        if (kinds.length == 0) return null;
        CraftKind kind = kinds[sl.random.nextInt(kinds.length)];
        Entity e = CraftRegistry.type(kind).create(sl);
        if (!(e instanceof CraftEntity c)) return null;
        Vec3 p = groundPoint(sl, at);
        c.moveTo(p.x, p.y + 0.2, p.z, sl.random.nextFloat() * 360.0f, 0.0f);
        sl.addFreshEntity(c);
        return c;
    }

    public static int clear(ServerLevel sl, Vec3 at, double radius) {
        int n = 0;
        for (SoldierEntity s : sl.getEntitiesOfClass(SoldierEntity.class,
                new net.minecraft.world.phys.AABB(at, at).inflate(radius))) {
            s.discard();
            n++;
        }
        return n;
    }

    private static Vec3 groundPoint(ServerLevel sl, Vec3 at) {
        int x = Mth.floor(at.x);
        int z = Mth.floor(at.z);
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new Vec3(at.x, Math.max(y, at.y), at.z);
    }
}
