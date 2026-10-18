package ru.aiforge.realwar.damage;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;
import ru.aiforge.realwar.vehicle.VehicleEntity;
import ru.aiforge.realwar.vehicle.VehicleKind;

/**
 * r21 War Thunder style crew of the ground vehicles. Every vehicle kind has its real crew roles (driver-mechanic,
 * gunner, commander, loader, radio operator / bow gunner) seated at hull-frame positions (shared by the server hit
 * resolution, the hit camera and the vehicle card). Members have 0..100 health in {@link DamageState#crew}; they are
 * hurt by the shell body, spall, the HE / APHE burst and fragments of soft-skinned vehicles.
 * <ul>
 *   <li>driver out -> the vehicle stops (no drive), gunner out -> no traverse / fire, loader out -> reload x2;</li>
 *   <li>a surviving member of a less important role moves into the empty seat after {@link #SWAP_TICKS}
 *   (priority driver > gunner > loader > commander > radio), his own seat stays empty;</li>
 *   <li>too few men left (less than half, or nobody) -> the vehicle is knocked out ("crew knocked out").</li>
 * </ul>
 */
public final class Crew {
    public static final int DRIVER = 0, GUNNER = 1, COMMANDER = 2, LOADER = 3, RADIO = 4;
    public static final String[] KEYS = {"driver", "gunner", "commander", "loader", "radio"};
    public static final int MAX = 8;
    public static final int SWAP_TICKS = 160;
    /** Replacement priority: index = importance (0 = most important). */
    private static final int[] PRIO = {0, 1, 3, 2, 4};
    private static final int[] NONE = {};

    private Crew() {
    }

    public static int[] roles(VehicleKind k) {
        return switch (k) {
            case T90M, BMP3, BRADLEY, TOS1A, PZ2, GEPARD -> new int[]{DRIVER, GUNNER, COMMANDER};
            case MARK_I -> new int[]{DRIVER, GUNNER, GUNNER, LOADER, LOADER, COMMANDER};
            case ABRAMS, IS2, MSTA, SHILKA, ZSU37 -> new int[]{DRIVER, GUNNER, COMMANDER, LOADER};
            case PZ4, TIGER, T34, STURMTIGER, KV5, WIRBELWIND -> new int[]{DRIVER, GUNNER, COMMANDER, LOADER, RADIO};
            case KV2, MAUS, RATTE -> new int[]{DRIVER, GUNNER, COMMANDER, LOADER, LOADER, RADIO};
            case GRAD, SMERCH, S400, RADAR_MOBILE, YARS, BM13 -> new int[]{DRIVER, GUNNER, COMMANDER};
            case URAL -> new int[]{DRIVER, COMMANDER};
            default -> NONE;              // towed guns, ZU-23, radar post: no crew model
        };
    }

    public static int count(VehicleKind k) {
        return roles(k).length;
    }

    public static boolean has(VehicleKind k, int role) {
        for (int r : roles(k)) if (r == role) return true;
        return false;
    }

    /** How many seats of the same role come before seat i (second loader...). */
    static int dup(int[] r, int i) {
        int n = 0;
        for (int j = 0; j < i; j++) if (r[j] == r[i]) n++;
        return n;
    }

    /** Crew sit in the turret (real turreted tanks / IFVs / SPGs; the TOS and the trucks' launchers are unmanned). */
    public static boolean turretCrew(VehicleKind k, VehicleDamage.Geo g) {
        return g.turret() && g.yp() != null && g.tracked() && k != VehicleKind.TOS1A;
    }

    public static boolean inTurret(VehicleKind k, VehicleDamage.Geo g, int seat) {
        int[] r = roles(k);
        if (seat >= r.length || !turretCrew(k, g)) return false;
        return r[seat] == GUNNER || r[seat] == COMMANDER || r[seat] == LOADER;
    }

    /** Hip point of seat i in the hull frame (x left, y up, z forward); az = turret angle. */
    public static Vector3d pos(VehicleKind k, VehicleDamage.Geo g, float az, int seat) {
        int[] r = roles(k);
        if (seat >= r.length) return new Vector3d(g.cx(), g.cy(), g.cz());
        int role = r[seat], dn = dup(r, seat);
        double cx = g.cx(), cy = g.cy(), cz = g.cz(), hx = g.hx(), hy = g.hy(), hz = g.hz();
        if (!g.tracked()) {
            // trucks: the cab seats of the spec (seat 0 driver, seat 1 co-driver), the third man between them
            float[][] s = k.spec.seats();
            if (s != null && s.length >= 2) {
                double y = Math.min(s[0][1], s[1][1]) - 0.15;
                return switch (role) {
                    case DRIVER -> new Vector3d(s[0][0], y, s[0][2]);
                    case GUNNER -> new Vector3d(s[1][0], y, s[1][2]);
                    default -> new Vector3d((s[0][0] + s[1][0]) * 0.5, y, (s[0][2] + s[1][2]) * 0.5 - 0.05);
                };
            }
        }
        double sc = Math.max(1.0, hy / 0.8) * 0.5 + 0.5;          // big hulls (Maus, Ratte) spread the crew a bit
        switch (role) {
            case DRIVER -> { return new Vector3d(cx + hx * 0.42, cy - hy * 0.25, cz + hz * 0.62); }
            case RADIO -> { return new Vector3d(cx - hx * 0.42, cy - hy * 0.25, cz + hz * 0.62); }
            default -> { }
        }
        if (!turretCrew(k, g)) {
            double[] q = switch (role) {
                case GUNNER -> new double[]{-0.45, 0.1, 0.1};
                case COMMANDER -> new double[]{0.45, 0.1, -0.5};
                default -> dn == 0 ? new double[]{-0.45, 0.1, -0.9} : new double[]{0.0, 0.1, -1.4};
            };
            return new Vector3d(cx + q[0] * sc, cy + q[1], cz + q[2] * sc);
        }
        double[] q = switch (role) {
            case GUNNER -> new double[]{0.45, -0.35, 0.10};
            case COMMANDER -> new double[]{0.45, -0.15, -0.40};
            default -> dn == 0 ? new double[]{-0.50, -0.40, 0.15} : new double[]{-0.45, -0.30, -0.55};
        };
        return turretPoint(g, az, q[0] * sc, g.yp()[1] + q[1], q[2] * sc * k.fireAxisZ());
    }

    /** Turret-local (x, absolute y, z) -> hull frame, rotated by the turret angle about the yaw pivot. */
    public static Vector3d turretPoint(VehicleDamage.Geo g, float az, double lx, double y, double lz) {
        double a = Math.toRadians(az), sa = Math.sin(a), ca = Math.cos(a);
        return new Vector3d(g.yp()[0] + lx * ca + lz * sa, y, g.yp()[2] - lx * sa + lz * ca);
    }

    /** Body sample points of a seated man (hip, chest, head) and their radii. */
    private static final double[] BODY_Y = {0.08, 0.38, 0.68}, BODY_R = {0.26, 0.26, 0.17};

    static double distSeg(Vector3d p, Vector3d a, Vector3d b) {
        double abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
        double l2 = abx * abx + aby * aby + abz * abz;
        double t = l2 < 1e-9 ? 0 : ((p.x - a.x) * abx + (p.y - a.y) * aby + (p.z - a.z) * abz) / l2;
        t = Math.max(0, Math.min(1, t));
        double dx = a.x + abx * t - p.x, dy = a.y + aby * t - p.y, dz = a.z + abz * t - p.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Clearance between the segment a-b (beam half width w) and the body of seat i; &lt; 0 = touching. */
    public static double clearSeg(VehicleKind k, VehicleDamage.Geo g, float az, int seat, Vector3d a, Vector3d b, double w) {
        Vector3d hip = pos(k, g, az, seat);
        double best = 1e9;
        for (int i = 0; i < 3; i++) {
            Vector3d p = new Vector3d(hip.x, hip.y + BODY_Y[i], hip.z);
            best = Math.min(best, distSeg(p, a, b) - BODY_R[i] - w);
        }
        return best;
    }

    /** Distance from a point to the body of seat i (0 inside). */
    public static double distPoint(VehicleKind k, VehicleDamage.Geo g, float az, int seat, Vector3d c) {
        Vector3d hip = pos(k, g, az, seat);
        double best = 1e9;
        for (int i = 0; i < 3; i++) {
            double d = c.distance(hip.x, hip.y + BODY_Y[i], hip.z) - BODY_R[i];
            best = Math.min(best, Math.max(0, d));
        }
        return best;
    }

    // ============================================================================================ server

    /** Crew hits of the hit being resolved (for the hit camera): bit per seat. */
    static int logHit, logDead;

    static void resetLog() {
        logHit = logDead = 0;
    }

    /** Hurts seat i; returns true if he died now. */
    static boolean hurt(ServerLevel sl, VehicleEntity v, int seat, float dmg) {
        DamageState s = v.dmg;
        int[] r = roles(v.kind);
        if (seat >= r.length || s.crew[seat] <= 0 || dmg < 0.5f || s.dead()) return false;
        s.crew[seat] = Math.max(0f, s.crew[seat] - dmg);
        logHit |= 1 << seat;
        s.dirtyNow = true;
        if (s.crew[seat] > 0) {
            notifyRole(v, "hurt", r[seat]);
            return false;
        }
        logDead |= 1 << seat;
        notifyRole(v, "dead", r[seat]);
        return true;
    }

    /** The shell body / a fragment flying a -> b (beam half width w): dmg to every man it touches. */
    static void segment(ServerLevel sl, VehicleEntity v, VehicleDamage.Geo g, float az, Vector3d a, Vector3d b, double w, float dmg) {
        int n = count(v.kind);
        for (int i = 0; i < n; i++) {
            double c = clearSeg(v.kind, g, az, i, a, b, w);
            if (c < 0) hurt(sl, v, i, dmg);
            else if (c < 0.12) hurt(sl, v, i, dmg * 0.35f);     // grazed
        }
    }

    /** A burst inside / at the vehicle: falls off linearly to zero at R. */
    static void sphere(ServerLevel sl, VehicleEntity v, VehicleDamage.Geo g, float az, Vector3d c, double R, float dmg) {
        int n = count(v.kind);
        for (int i = 0; i < n; i++) {
            double d = distPoint(v.kind, g, az, i, c);
            if (d < R) hurt(sl, v, i, (float) (dmg * Math.pow(1.0 - d / R, 1.2)));
        }
    }

    /** Fragments through a soft hull: every man is hit with probability p for dmg (+/- 40 %). */
    static void fragments(ServerLevel sl, VehicleEntity v, double p, float dmg) {
        int n = count(v.kind);
        for (int i = 0; i < n; i++) {
            if (sl.random.nextDouble() < p) hurt(sl, v, i, dmg * (0.6f + 0.8f * sl.random.nextFloat()));
        }
    }

    static void killAll(VehicleEntity v) {
        DamageState s = v.dmg;
        for (int i = 0; i < MAX; i++) s.crew[i] = 0f;
        s.swapTo = s.swapFrom = -1;
        s.dirtyNow = true;
    }

    public static int alive(VehicleEntity v) {
        int n = count(v.kind), a = 0;
        for (int i = 0; i < n; i++) if (v.dmg.crew[i] > 0) a++;
        return a;
    }

    /** True if the vehicle has no such role or someone alive sits in it. */
    public static boolean ok(VehicleEntity v, int role) {
        int[] r = roles(v.kind);
        boolean any = false;
        for (int i = 0; i < r.length; i++) {
            if (r[i] != role) continue;
            any = true;
            if (v.dmg.crew[i] > 0) return true;
        }
        return !any;
    }

    /** Knocked out by crew losses? */
    static boolean knockedOut(VehicleEntity v) {
        int n = count(v.kind);
        if (n == 0) return false;
        int a = alive(v);
        if (a == 0) return true;
        if (v.kind == VehicleKind.RATTE) return false;   // a crew of 20-40: the six modelled men are the key posts
        return a * 2 < n;
    }

    /** Server tick: replacements and the knock-out check. */
    static void tick(ServerLevel sl, VehicleEntity v) {
        DamageState s = v.dmg;
        int[] r = roles(v.kind);
        if (r.length == 0 || s.dead()) return;
        if (knockedOut(v)) {
            VehicleDamage.knockOut(sl, v);
            return;
        }
        if (s.swapTo >= 0) {
            if (s.swapTo >= r.length || s.swapFrom >= r.length || s.crew[s.swapFrom] <= 0 || s.crew[s.swapTo] > 0) {
                s.swapTo = s.swapFrom = -1;
                s.dirtyNow = true;
                return;
            }
            if (--s.swapTicks <= 0) {
                s.crew[s.swapTo] = s.crew[s.swapFrom];
                s.crew[s.swapFrom] = 0f;
                s.crewMoved |= 1 << s.swapFrom;
                s.crewMoved &= ~(1 << s.swapTo);
                notifyRole(v, "replaced", r[s.swapTo]);
                sl.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.NEUTRAL, 1f, 0.8f);
                s.swapTo = s.swapFrom = -1;
                s.dirtyNow = true;
            } else if (s.swapTicks % 10 == 0) {
                s.dirty = true;
            }
            return;
        }
        // the most important empty seat that someone less important can take
        int to = -1, from = -1;
        for (int i = 0; i < r.length; i++) {
            if (s.crew[i] > 0 || r[i] == RADIO) continue;
            if (to >= 0 && PRIO[r[i]] >= PRIO[r[to]]) continue;
            int best = -1;
            for (int j = 0; j < r.length; j++) {
                if (s.crew[j] <= 0 || PRIO[r[j]] <= PRIO[r[i]]) continue;
                if (best < 0 || PRIO[r[j]] > PRIO[r[best]]) best = j;
            }
            if (best >= 0) {
                to = i;
                from = best;
            }
        }
        if (to >= 0) {
            s.swapTo = to;
            s.swapFrom = from;
            s.swapTicks = s.swapTotal = SWAP_TICKS;
            s.dirtyNow = true;
            Component c = Component.translatable("hud.aiarsenal.rw.crew.moving", role(r[from]), role(r[to]));
            for (Entity e : v.getPassengers()) if (e instanceof Player p) p.displayClientMessage(c, true);
            return;
        }
        // the wounded patch themselves up slowly when nothing burns
        if (!s.burning() && v.tickCount % 40 == 0) {
            for (int i = 0; i < r.length; i++) {
                if (s.crew[i] > 0 && s.crew[i] < 100) {
                    s.crew[i] = Math.min(100f, s.crew[i] + 1f);
                    s.dirty = true;
                }
            }
        }
    }

    /** Fresh crew (repair kit / command). */
    public static void restore(DamageState s) {
        for (int i = 0; i < MAX; i++) s.crew[i] = 100f;
        s.crewMoved = 0;
        s.swapTo = s.swapFrom = -1;
        s.swapTicks = s.swapTotal = 0;
        s.dirtyNow = true;
    }

    public static Component role(int role) {
        return Component.translatable("hud.aiarsenal.rw.crew." + KEYS[Math.max(0, Math.min(KEYS.length - 1, role))]);
    }

    static void notifyRole(VehicleEntity v, String what, int role) {
        Component c = Component.translatable("hud.aiarsenal.rw.crew." + what, role(role));
        for (Entity e : v.getPassengers()) if (e instanceof Player p) p.displayClientMessage(c, true);
    }
}
