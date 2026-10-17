/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 *  net.minecraftforge.registries.ForgeRegistries
 */
package ru.aiforge.realwar.radar;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.aircraft.AircraftEntity;
import ru.aiforge.realwar.missile.MissileEntity;
import ru.aiforge.realwar.ordnance.AirTargets;
import ru.aiforge.realwar.ordnance.RocketEntity;
import ru.aiforge.realwar.ordnance.SamMissileEntity;
import ru.aiforge.realwar.vehicle.LauncherVehicle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

public final class RadarNet {
    public static final byte T_AIRCRAFT = 0;
    public static final byte T_MISSILE = 1;
    public static final byte T_DRONE = 2;
    public static final byte T_OTHER = 3;
    /** Ground target (vehicles / launchers / players on the ground) — only reported to the Yars launch officer. */
    public static final byte T_GROUND = 4;

    private RadarNet() {
    }

    public static byte typeOf(Entity e) {
        if (RadarNet.groundTarget(e)) {
            return T_GROUND;
        }
        if (e instanceof AircraftEntity) {
            return 0;
        }
        if (e instanceof MissileEntity || e instanceof SamMissileEntity || (e instanceof RocketEntity && !(e instanceof ru.aiforge.realwar.ordnance.ShellEntity))) {
            return 1;
        }
        String id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).getPath();
        if (id.contains("drone") || id.equals("shahed") || id.equals("fpv")) {
            return 2;
        }
        return 3;
    }

    /** Ground radar picture for strategic launchers: ground vehicles, arsenal launchers and surface players. */
    public static boolean groundTarget(Entity e) {
        if (!e.isAlive() || e.isSpectator()) return false;
        if (e instanceof ru.aiforge.realwar.vehicle.VehicleEntity) return true;
        if (e instanceof ru.aiforge.arsenal.entity.LauncherEntity) return true;
        return e instanceof net.minecraft.world.entity.player.Player pl && !pl.isCreative() && pl.onGround() && pl.getVehicle() == null;
    }

    public static boolean detectable(Entity e) {
        return AirTargets.isAirTarget(e);
    }

    public static List<Entity> scan(Level level, Vec3 c, double r, Entity self) {
        ArrayList<Entity> out = new ArrayList<Entity>(level.getEntities(self, new AABB(c, c).inflate(r, r, r), RadarNet::detectable));
        out.removeIf(e -> e.distanceToSqr(c) > r * r || self != null && e.getVehicle() == self || e == self);
        return out;
    }

    public static List<Entity> scanGround(Level level, Vec3 c, double r, Entity self) {
        ArrayList<Entity> out = new ArrayList<Entity>(level.getEntities(self, new AABB(c, c).inflate(r, 256.0, r), RadarNet::groundTarget));
        out.removeIf(e -> e.distanceToSqr(c) > r * r || self != null && (e.getVehicle() == self || e == self));
        return out;
    }

    public static List<Entity> emittersNear(Level level, Vec3 c) {
        double r = ((Integer)RWConfig.COMMON.radarLink.get()).intValue();
        ArrayList<Entity> out = new ArrayList<Entity>(level.getEntities((Entity)null, new AABB(c, c).inflate(r, r, r), e -> {
            if (!(e instanceof RadarEmitter)) return false;
            RadarEmitter re = (RadarEmitter)e;
            if (!e.isAlive()) return false;
            if (!re.radarActive()) return false;
            return true;
        }));
        out.removeIf(e -> e.distanceToSqr(c) > r * r);
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c)));
        return out;
    }

    public static boolean linked(LauncherVehicle v) {
        return !RadarNet.emittersNear(v.level(), v.position()).isEmpty();
    }

    public static List<Entity> sources(LauncherVehicle v) {
        RadarEmitter re;
        ArrayList<Entity> src = new ArrayList<Entity>();
        if (v instanceof RadarEmitter && (re = (RadarEmitter)(v)).radarActive()) {
            src.add(v);
        }
        for (Entity e : RadarNet.emittersNear(v.level(), v.position())) {
            if (e == v) continue;
            src.add(e);
        }
        return src;
    }

    public static List<Entity> picture(LauncherVehicle v) {
        HashSet<Integer> seen = new HashSet<Integer>();
        ArrayList<Entity> out = new ArrayList<Entity>();
        boolean ground = v instanceof ru.aiforge.realwar.vehicle.YarsVehicle;
        for (Entity s : RadarNet.sources(v)) {
            double r = ((RadarEmitter)s).radarRange();
            for (Entity t : ground ? RadarNet.scanGround(v.level(), s.position(), r, s) : RadarNet.scan(v.level(), s.position(), r, s)) {
                if (!seen.add(t.getId())) continue;
                out.add(t);
            }
        }
        Vec3 c = v.position();
        out.removeIf(e -> e == v || e.getVehicle() == v);
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c)));
        return out;
    }

    public static List<Contact> contacts(LauncherVehicle v) {
        ArrayList<Contact> out = new ArrayList<Contact>();
        for (Entity e : RadarNet.picture(v)) {
            Vec3 d = e.getDeltaMovement();
            double vx = d.x;
            double vy = d.y;
            double vz = d.z;
            if (e instanceof AircraftEntity) {
                AircraftEntity a = (AircraftEntity)e;
                double s = (double)a.speed() / 20.0;
                Vec3 f = a.getLookAngle();
                vx = f.x * s;
                vz = f.z * s;
                vy = f.y * s;
            }
            out.add(new Contact(e.getId(), RadarNet.typeOf(e), e.getX(), e.getY(), e.getZ(), (float)vx, (float)vy, (float)vz));
        }
        return out;
    }

    public static boolean isPlayerRiderOf(Player p, Entity v) {
        return p.getVehicle() == v;
    }

    public static interface RadarEmitter {
        public boolean radarActive();

        public double radarRange();
    }

    public record Contact(int id, byte type, double x, double y, double z, float vx, float vy, float vz) {
    }
}

