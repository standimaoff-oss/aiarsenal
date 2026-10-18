package ru.aiforge.realwar.damage;

import org.joml.Vector3d;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.vehicle.VehicleKind;

/**
 * r20 War Thunder style armour and penetration model of the ground vehicles.
 * <ul>
 *   <li>Every vehicle has an armour table in mm: hull upper / lower front, side, rear, roof, floor and turret front,
 *   side, rear, roof, plus the plate slopes (degrees from the vertical). Modern composite / ERA protected faces are
 *   given as the published line-of-sight RHA equivalent against long-rod KE, with a separate multiplier against
 *   shaped charges (HEAT jets lose much more in composite than in steel).</li>
 *   <li>Every projectile has a type (APFSDS, APCR, AP, APHE, HEAT, HE, EFP), a muzzle penetration (mm RHA at 0
 *   degrees), calibre and filler. Kinetic penetration falls with the real impact speed of the flight model
 *   (de Marre: pen ~ v^1.43), HEAT / EFP do not depend on distance, HE penetration comes from the filler mass.</li>
 *   <li>Effective thickness = nominal / cos(impact angle to the plate normal) after normalisation (full-calibre
 *   AP turns ~5 degrees into the plate), overmatch (calibre &gt; 3x plate: the slope is ignored), tracks in front
 *   of the lower side / nose act as 20 mm of spaced armour. High impact angles ricochet with a type dependent
 *   probability window.</li>
 * </ul>
 * Pure maths on the hull-frame geometry of {@link VehicleDamage.Geo} (x left, y up, z forward), shared by the
 * server hit resolution, the client hit camera (spall rays are re-generated from the same seed) and the gametests.
 */
public final class Armour {
    public static final int HULL_UF = 0, HULL_LF = 1, HULL_S = 2, HULL_R = 3, HULL_T = 4, HULL_B = 5, TUR_F = 6, TUR_S = 7, TUR_R = 8, TUR_T = 9,
            TRACK = 10, GUN = 11, VIEW = 12, HATCH = 13, FUEL = 14, RING = 15;
    public static final String[] ZONE_KEYS = {"hull_uf", "hull_lf", "hull_side", "hull_rear", "hull_roof", "hull_floor",
            "tur_front", "tur_side", "tur_rear", "tur_roof", "track", "barrel", "vision", "hatch", "fuel", "ring"};
    public static final int RICOCHET = 0, NO_PEN = 1, PEN = 2, MODULE = 3;
    /** Tracks / road wheels in front of the plate (spaced armour, mm). */
    public static final float TRACK_MM = 20f;

    private Armour() {
    }

    // ============================================================================================ ammunition

    public enum Type {
        APFSDS, APCR, AP, APHE, HEAT, HE, EFP;

        public boolean kinetic() {
            return this == APFSDS || this == APCR || this == AP || this == APHE;
        }
    }

    /** name = designation shown on the hit camera, pen0 = mm RHA at 0 deg at the muzzle speed v0 (m/s). */
    public record Ammo(String name, Type type, double pen0, double v0, double calMm, double fillerKg) {
        /** Penetration (mm RHA, 0 deg) at the impact speed. */
        public double penAt(double speed) {
            if (this.type.kinetic() && this.v0 > 1 && speed > 1) return this.pen0 * Math.pow(Math.min(speed, this.v0 * 1.05) / this.v0, 1.43);
            return this.pen0;
        }

        public static Ammo of(Ballistics.Profile p) {
            double cal = p.d * 1000.0;
            return switch (p) {
                case T125_AP -> new Ammo(p.title, Type.APFSDS, 700, p.ejectSpeed, cal, 0);        // 3BM60 Svinets-2
                case T120_AP -> new Ammo(p.title, Type.APFSDS, 790, p.ejectSpeed, cal, 0);        // M829A4
                case QF6_AP -> new Ammo(p.title, Type.AP, 55, p.ejectSpeed, cal, 0);               // QF 6-pdr AP shot, Mark I-era armour
                case M242_AP -> new Ammo(p.title, Type.APFSDS, 110, p.ejectSpeed, cal, 0);        // M919 (DU rod)
                case KW20_AP -> new Ammo(p.title, Type.APCR, 52, p.ejectSpeed, cal, 0);           // 2 cm Pzgr 40
                case K75_AP -> new Ammo(p.title, Type.APHE, 140, p.ejectSpeed, cal, 0.018);       // KwK 40 L/48 Pzgr 39
                case K88_AP -> new Ammo(p.title, Type.APHE, 165, p.ejectSpeed, cal, 0.059);       // KwK 36 Pzgr 39
                case S85_AP -> new Ammo(p.title, Type.APHE, 142, p.ejectSpeed, cal, 0.048);       // ZiS-S-53 BR-365
                case K128_AP -> new Ammo(p.title, Type.APHE, 250, p.ejectSpeed, cal, 0.4);        // KwK 44 Pzgr 43
                case IS2_AP -> new Ammo(p.title, Type.APHE, 200, p.ejectSpeed, cal, 0.156);       // D-25T BR-471
                case SK38_AP -> new Ammo(p.title, Type.APHE, 740, p.ejectSpeed, cal, 23.4);       // 38 cm SK C/34 Psgr L/4.4
                case T120_HE -> new Ammo(p.title, Type.HEAT, 480, p.ejectSpeed, cal, p.tntKg);    // M830A1 HEAT-MP-T
                case TOW2B -> new Ammo(p.title, Type.EFP, 250, p.ejectSpeed, cal, p.tntKg);       // top-attack EFP pair
                default -> new Ammo(p.title, p.kinetic() ? Type.AP : Type.HE, p.kinetic() ? 0.5 * cal : hePen(p.tntKg), p.ejectSpeed, cal,
                        Math.max(0, p.tntKg));
            };
        }

        /** Helicopter / boat ordnance (CraftOrdnance.Type names). */
        public static Ammo ofCraft(String type, String title, double tntKg) {
            return switch (type) {
                case "VIKHR" -> new Ammo(title, Type.HEAT, 950, 0, 130, tntKg);    // 9M127 tandem HEAT
                case "HELLFIRE" -> new Ammo(title, Type.HEAT, 1000, 0, 178, tntKg); // AGM-114K tandem HEAT
                case "S8" -> new Ammo(title, Type.HEAT, 400, 0, 80, tntKg);         // S-8KOM HEAT-frag
                default -> new Ammo(title, Type.HE, hePen(tntKg), 0, 70, tntKg);
            };
        }
    }

    /** HE "penetration" (mm RHA, slope ignored) of a filler of tnt kg - fitted to War Thunder style values:
     *  30 mm HE-I 11 mm, 125 mm 3OF26 40 mm, 152 mm 3OF45 52 mm. */
    public static double hePen(double tntKg) {
        return 28.0 * Math.pow(Math.max(0.005, tntKg), 0.3);
    }

    // ============================================================================================ armour tables

    /** mm and slope (deg from vertical) per zone index HULL_UF..TUR_T; ce = multiplier of the composite faces
     *  (hull upper front, turret front) against HEAT jets. */
    public record Table(float[] mm, float[] ang, float ce) {
        public float mm(int zone) {
            return zone >= 0 && zone < 10 ? this.mm[zone] : 0f;
        }
    }

    private static Table t(int uf, int lf, int s, int r, int top, int bot, int tf, int ts, int tr, int tt,
                           int aUF, int aLF, int aS, int aR, int aTF, int aTS, int aTR, float ce) {
        return new Table(new float[]{uf, lf, s, r, top, bot, tf, ts, tr, tt}, new float[]{aUF, aLF, aS, aR, 0, 0, aTF, aTS, aTR, 0}, ce);
    }

    /** Published / commonly cited values (WT data sheets, Zaloga, Jentz, Tankograd). Composite faces = KE line of
     *  sight equivalent, so their slope entry is 0. */
    public static Table of(VehicleKind k) {
        return switch (k) {
            //                 UF   LF   S   R  top bot  TF   TS   TR  TT  aUF aLF aS aR aTF aTS aTR  ce
            case T90M -> t(550, 80, 80, 40, 40, 20, 700, 300, 70, 45, 0, 60, 0, 0, 0, 0, 0, 1.7f);
            case ABRAMS -> t(600, 120, 60, 40, 40, 25, 850, 320, 60, 50, 0, 55, 0, 0, 0, 0, 0, 1.5f);
            case BMP3 -> t(35, 30, 25, 15, 15, 10, 35, 20, 15, 15, 60, 40, 0, 0, 30, 0, 0, 1f);
            case BRADLEY -> t(35, 30, 40, 25, 25, 15, 40, 35, 25, 25, 60, 40, 0, 0, 30, 0, 0, 1.1f);
            case MSTA -> t(30, 20, 15, 15, 15, 15, 20, 15, 15, 15, 35, 30, 0, 0, 15, 0, 0, 1f);
            case TOS1A -> t(410, 80, 80, 40, 20, 20, 30, 20, 20, 15, 0, 60, 0, 0, 0, 0, 0, 1.3f);
            // British Mark I: about 12 mm at the front, 8 mm on the sides and rear, 6 mm roof / floor.
            case MARK_I -> t(12, 12, 8, 8, 6, 6, 0, 0, 0, 0, 10, 0, 0, 0, 0, 0, 0, 1f);
            case PZ2 -> t(35, 35, 15, 15, 10, 5, 30, 15, 15, 10, 10, 20, 0, 0, 10, 22, 22, 1f);
            case PZ4 -> t(80, 80, 30, 20, 12, 10, 50, 30, 30, 18, 10, 14, 0, 10, 10, 25, 15, 1f);
            case TIGER -> t(100, 100, 80, 80, 25, 25, 120, 80, 80, 25, 10, 24, 0, 8, 0, 0, 0, 1f);
            case STURMTIGER -> t(150, 100, 82, 82, 40, 25, 150, 82, 82, 40, 47, 24, 0, 10, 47, 20, 10, 1f);
            case MAUS -> t(200, 200, 180, 160, 50, 50, 240, 200, 200, 60, 55, 35, 0, 30, 0, 30, 15, 1f);
            case RATTE -> t(360, 300, 250, 250, 150, 150, 400, 250, 250, 150, 30, 30, 0, 0, 0, 20, 0, 1f);
            case T34 -> t(45, 45, 45, 45, 20, 20, 90, 75, 52, 20, 60, 53, 40, 47, 0, 20, 10, 1f);
            case KV2 -> t(75, 75, 75, 60, 30, 30, 75, 75, 75, 40, 30, 25, 0, 15, 0, 0, 0, 1f);
            case IS2 -> t(120, 100, 90, 60, 30, 20, 100, 90, 90, 30, 60, 30, 15, 49, 0, 20, 30, 1f);
            case KV5 -> t(180, 180, 150, 150, 40, 40, 180, 150, 150, 40, 30, 30, 0, 0, 0, 0, 0, 1f);
            case ZU23, D30, M777 -> t(6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 0, 0, 0, 0, 0, 0, 0, 1f);
            case RADAR_POST -> t(8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 0, 0, 0, 0, 0, 0, 0, 1f);
            default -> t(5, 5, 4, 4, 3, 4, 4, 4, 4, 4, 0, 0, 0, 0, 0, 0, 0, 1f);     // trucks: cab sheet steel
        };
    }

    /** True for vehicles with real armour (the hit camera shows the X-ray of every vehicle anyway). */
    public static boolean armoured(VehicleKind k) {
        return k == VehicleKind.MARK_I || of(k).mm[HULL_UF] >= 15;
    }

    // ============================================================================================ geometry

    public record Result(int result, int zone, float nominal, float eff, float angle, float pen, Vector3d entry, Vector3d normal,
                         boolean viaTrack, boolean overmatch, int module) {
        public double excess() {
            return (this.pen - this.eff) / Math.max(1.0, this.eff);
        }
    }

    /** Ray / box slab test in the box frame: {tEnter, axis (0 x, 1 y, 2 z), outward sign}; null = miss. */
    static double[] slab(double ox, double oy, double oz, double dx, double dy, double dz, double hx, double hy, double hz) {
        double t0 = -1e9, t1 = 1e9;
        int ax = -1;
        double sg = 0;
        double[] o = {ox, oy, oz}, d = {dx, dy, dz}, h = {hx, hy, hz};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-9) {
                if (Math.abs(o[i]) > h[i]) return null;
                continue;
            }
            double a = (-h[i] - o[i]) / d[i], b = (h[i] - o[i]) / d[i];
            double sa = -1;
            if (a > b) {
                double tt = a;
                a = b;
                b = tt;
                sa = 1;
            }
            if (a > t0) {
                t0 = a;
                ax = i;
                sg = sa;                 // entering through the -h face (sa -1) or the +h face (sa +1)
            }
            t1 = Math.min(t1, b);
            if (t0 > t1) return null;
        }
        if (t1 < 0 || ax < 0) return null;
        return new double[]{t0, ax, sg};
    }

    /** Turret box {cx, cy, cz, hx, hy, hz} in the turret frame at az = 0 (hull frame), or null. */
    public static float[] turretBox(VehicleDamage.Geo g, VehicleKind k) {
        if (!g.turret() || g.yp() == null) return null;
        String[] kinds = k.spec.partKinds();
        for (int i = 0; i < kinds.length; i++) {
            if ("door_gunner".equals(kinds[i])) {
                float[] b = k.spec.parts()[i];
                if (b.length >= 6 && b[3] > 0.5f && b[5] > 0.5f) return b;
            }
        }
        return new float[]{g.yp()[0], g.yp()[1] + 0.3f, g.yp()[2], 0.95f, 0.4f, 1.1f};
    }

    // ============================================================================================ gun tube collider

    /**
     * r28: the gun barrel as the finite cylinder it really is: {@code o} = trunnion, {@code dir} = aim direction,
     * {@code len} = tube length, {@code r} = outer radius of the tube, {@code t0} = the first metre of the axis that
     * sits inside the mantlet / sponson box (that region belongs to the turret plate, not to the barrel).
     * The Mark I has two of them (one per sponson), turreted vehicles one.
     */
    public record Tube(Vector3d o, Vector3d dir, double len, double r, double t0) {
        /** True when the hull-frame point is inside the tube (used by the module damage model). */
        public boolean contains(double x, double y, double z) {
            double wx = x - this.o.x, wy = y - this.o.y, wz = z - this.o.z;
            double ax = wx * this.dir.x + wy * this.dir.y + wz * this.dir.z;
            if (ax < this.t0 || ax > this.len) return false;
            double px = wx - this.dir.x * ax, py = wy - this.dir.y * ax, pz = wz - this.dir.z * ax;
            return px * px + py * py + pz * pz < this.r * this.r;
        }
    }

    /** Outer tube radius (m) of the real gun: bore/2 plus the wall, checked against published ordnance drawings.
     *  The old model used one 0.24 m (Mark I: 0.18 m) radius for every vehicle, i.e. a 48 cm thick virtual barrel
     *  that swallowed rounds aimed at the plate next to it. */
    public static double tubeRadius(VehicleKind k) {
        return switch (k) {
            case T90M -> 0.100;              // 125 mm 2A46M-4
            case ABRAMS -> 0.098;            // 120 mm M256
            case BMP3 -> 0.083;              // 100 mm 2A70
            case MSTA -> 0.112;              // 152 mm 2A64
            case D30 -> 0.092;               // 122 mm 2A18
            case M777 -> 0.113;              // 155 mm M777
            case PZ2 -> 0.033;               // 2 cm KwK 30
            case PZ4 -> 0.068;               // 7.5 cm KwK 40 L/48
            case TIGER -> 0.074;             // 8.8 cm KwK 36 L/56
            case STURMTIGER -> 0.215;        // 38 cm RW 61
            case MAUS -> 0.094;              // 12.8 cm KwK 44 L/55
            case RATTE -> 0.185;             // 28 cm SK C/34
            case T34 -> 0.068;               // 85 mm ZiS-S-53
            case KV2 -> 0.112;               // 152 mm M-10T
            case IS2 -> 0.093;               // 122 mm D-25T
            case KV5 -> 0.084;               // 107 mm ZiS-6
            case BRADLEY -> 0.032;           // 25 mm M242 Bushmaster
            case TOS1A -> 0.122;             // 220 mm thermobaric tube
            case MARK_I -> 0.049;            // QF 6-pdr Hotchkiss (57 mm, 8.6 kg barrel section)
            case ZU23 -> 0.028;              // 23 mm AZP-23
            default -> 0.060;
        };
    }

    /** The barrels of a vehicle in the hull frame (0, 1 or 2), or an empty array when the turret is gone. */
    public static Tube[] tubes(VehicleDamage.Geo g, VehicleKind k, boolean turretGone, float[] ang) {
        if (turretGone || g.tube() <= 0.5f) return new Tube[0];
        double az = Math.toRadians(ang[0]), el = Math.toRadians(ang[1]);
        double sa = Math.sin(az), ca = Math.cos(az), ce = Math.cos(el), se = Math.sin(el);
        double fz = k.fireAxisZ();
        Vector3d dir = new Vector3d(sa * ce * fz, se * fz, ca * ce * fz);
        if (dir.lengthSquared() < 1e-9) return new Tube[0];
        dir.normalize();
        double r = tubeRadius(k);
        // The collider ends at the real muzzle of the model (spec tube), never at the longer ballistic barrel length:
        // the Tiger's tubeLen is 4.93 m while its KwK 36 muzzle sits 3.93 m from the trunnion, and that extra metre of
        // invisible steel is what swallowed rounds in front of the turret.
        double len = g.tube();
        float[][] spec = k.spec.tubes();
        if (spec != null && spec.length > 0) {
            float[] q = spec[0];
            double model = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
            if (model > 0.5) len = Math.min(len, model + 0.10);
        }
        if (k == VehicleKind.MARK_I) {
            // the two QF 6-pdrs turn about their own sponson trunnions (tools/wwi/build_mark_i.py), each inside
            // its historical sector: the barrel collider follows the clamped gun, not the shared aim line
            Tube[] out = new Tube[2];
            for (int t = 0; t < 2; ++t) {
                double a2 = Math.toRadians(k.sponsonAz(t, ang[0])), e2 = Math.toRadians(k.sponsonEl(ang[1]));
                Vector3d d2 = new Vector3d(Math.sin(a2) * Math.cos(e2) * fz, Math.sin(e2) * fz,
                        Math.cos(a2) * Math.cos(e2) * fz).normalize();
                out[t] = new Tube(new Vector3d(t == 0 ? -1.41 : 1.41, 1.22, 0.0), d2, len, r, 0.65);
            }
            return out;
        }
        if (!g.turret() || g.yp() == null || g.pp() == null) return new Tube[0];
        Vector3d o = new Vector3d(g.yp()[0] + g.pp()[0] * ca + g.pp()[2] * sa,
                g.yp()[1] + g.pp()[1],
                g.yp()[2] - g.pp()[0] * sa + g.pp()[2] * ca);
        return new Tube[]{new Tube(o, dir, len, r, Math.min(0.9, len * 0.5))};
    }

    /**
     * Entry distance of the ray o+t*d (t in 0..maxT) into the tube, or -1. Exact solution of the quadratic for the
     * cylinder side plus the two flat caps: one square root per barrel instead of the 0.05 m march of 200
     * {@code zone()} calls the hit resolution used before.
     */
    public static double tubeHit(Vector3d ro, Vector3d rd, Tube t, double maxT) {
        double dw = ro.x - t.o.x, ey = ro.y - t.o.y, ez = ro.z - t.o.z;
        double wd = dw * t.dir.x + ey * t.dir.y + ez * t.dir.z;
        double rdAx = rd.x * t.dir.x + rd.y * t.dir.y + rd.z * t.dir.z;
        double ax = rd.x - t.dir.x * rdAx, ay = rd.y - t.dir.y * rdAx, az = rd.z - t.dir.z * rdAx;
        double bx = dw - t.dir.x * wd, by = ey - t.dir.y * wd, bz = ez - t.dir.z * wd;
        double A = ax * ax + ay * ay + az * az;
        double best = -1;
        if (A > 1e-12) {
            double B = 2 * (ax * bx + ay * by + az * bz);
            double C = bx * bx + by * by + bz * bz - t.r * t.r;
            double disc = B * B - 4 * A * C;
            if (disc >= 0) {
                double sq = Math.sqrt(disc);
                double r0 = (-B - sq) / (2 * A), r1 = (-B + sq) / (2 * A);
                for (double tt : new double[]{Math.min(r0, r1), Math.max(r0, r1)}) {
                    if (tt < 0 || tt > maxT) continue;
                    double along = wd + rdAx * tt;
                    if (along >= t.t0 && along <= t.len) return tt;      // ascending roots: the first hit wins
                }
            }
        }
        if (Math.abs(rdAx) > 1e-9) {
            for (double along : new double[]{t.t0, t.len}) {
                double tt = (along - wd) / rdAx;
                if (tt < 0 || tt > maxT) continue;
                double qx = bx + ax * tt, qy = by + ay * tt, qz = bz + az * tt;
                if (qx * qx + qy * qy + qz * qz <= t.r * t.r && (best < 0 || tt < best)) best = tt;
            }
        }
        return best;
    }

    /** Rotates a hull-frame vector by the turret azimuth (deg) about +y (same convention as VehicleDamage.zone). */
    static Vector3d rot(Vector3d v, double azDeg) {
        double a = Math.toRadians(azDeg), sa = Math.sin(a), ca = Math.cos(a);
        return new Vector3d(v.x * ca + v.z * sa, v.y, -v.x * sa + v.z * ca);
    }

    static Vector3d unrot(Vector3d v, double azDeg) {
        return rot(v, -azDeg);
    }

    /** Is the hull-frame point inside the fighting / engine compartment or the turret? */
    public static boolean inside(VehicleDamage.Geo g, VehicleKind k, boolean turretGone, float[] ang, double x, double y, double z) {
        double tw = g.tracked() ? g.trackW() : 0.1;
        if (Math.abs(x - g.cx()) < g.hx() - tw && y > g.cy() - g.hy() * 0.7 && y < g.cy() + g.hy() && Math.abs(z - g.cz()) < g.hz()) return true;
        if (k == VehicleKind.MARK_I) {
            String[] names = k.spec.partKinds();
            float[][] parts = k.spec.parts();
            for (int i = 0; i < names.length; ++i) {
                if (!names[i].startsWith("sponson_")) continue;
                float[] b = parts[i];
                if (Math.abs(x - b[0]) < b[3] && Math.abs(y - b[1]) < b[4] && Math.abs(z - b[2]) < b[5]) return true;
            }
        }
        float[] tb = turretGone ? null : turretBox(g, k);
        if (tb == null) return false;
        Vector3d q = unrot(new Vector3d(x - g.yp()[0], y, z - g.yp()[2]), ang[0]);
        double lx = q.x - (tb[0] - g.yp()[0]), lz = q.z - (tb[2] - g.yp()[2]);
        return Math.abs(lx) < tb[3] && Math.abs(y - tb[1]) < tb[4] && Math.abs(lz) < tb[5];
    }

    /**
     * Resolves which plate a shot meets and whether it goes through.
     *
     * @param p0    hull-frame impact point (on the collision box)
     * @param d     hull-frame unit flight direction
     * @param roll  uniform random 0..1 for the ricochet window
     */
    public static Result solve(VehicleDamage.Geo g, VehicleKind k, boolean turretGone, float[] ang, Vector3d p0, Vector3d d, Ammo a, double speed, double roll) {
        Table tb = of(k);
        double fz = k.fireAxisZ();
        Vector3d o = new Vector3d(p0).sub(new Vector3d(d).mul(8.0));
        // ---- hull box
        double[] hh = slab(o.x - g.cx(), o.y - g.cy(), o.z - g.cz(), d.x, d.y, d.z, g.hx(), g.hy(), g.hz());
        // ---- turret box (rotated with the turret)
        float[] tbx = turretGone ? null : turretBox(g, k);
        double[] th = null;
        Vector3d tCentre = null;
        if (tbx != null) {
            Vector3d off = rot(new Vector3d(tbx[0] - g.yp()[0], 0, tbx[2] - g.yp()[2]), ang[0]);
            tCentre = new Vector3d(g.yp()[0] + off.x, tbx[1], g.yp()[2] + off.z);
            Vector3d lo = unrot(new Vector3d(o).sub(tCentre), ang[0]);
            Vector3d ld = unrot(new Vector3d(d), ang[0]);
            th = slab(lo.x, lo.y, lo.z, ld.x, ld.y, ld.z, tbx[3], tbx[4], tbx[5]);
        }
        double tHit = 1e9;
        boolean turretFace = false;
        double[] face = null;
        if (hh != null && hh[0] >= -0.01) {
            tHit = hh[0];
            face = hh;
        }
        if (th != null && th[0] >= -0.01 && th[0] < tHit) {
            tHit = th[0];
            face = th;
            turretFace = true;
        }
        // Mark I Male has two external armoured sponsons, not a fictitious central turret.
        if (k == VehicleKind.MARK_I) {
            String[] names = k.spec.partKinds();
            float[][] parts = k.spec.parts();
            for (int i = 0; i < names.length; ++i) {
                if (!names[i].startsWith("sponson_")) continue;
                float[] b = parts[i];
                double[] sh = slab(o.x - b[0], o.y - b[1], o.z - b[2], d.x, d.y, d.z, b[3], b[4], b[5]);
                if (sh != null && sh[0] >= -0.01 && sh[0] < tHit) {
                    tHit = sh[0];
                    face = sh;
                    turretFace = false;
                }
            }
        }
        // External modules (barrels, plus Mark I track belts) are resolved before the armour plate behind them.
        // r28: exact ray/tube and ray/belt solutions instead of a 0.05 m march through zone() - up to 200 module
        // lookups per round, and the barrel is now the real calibre tube, so a round aimed at the plate next to the
        // gun is no longer swallowed by a 48 cm virtual cylinder. The client reticle, the server and the hit camera
        // call exactly this geometry.
        double limit = Math.min(tHit, 12.0);
        double tModule = -1;
        int hitModule = -1;
        boolean externalTrackHit = false;
        for (Tube tube : tubes(g, k, turretGone, ang)) {
            double tt = tubeHit(o, d, tube, limit);
            if (tt >= 0 && (tModule < 0 || tt < tModule)) {
                tModule = tt;
                hitModule = DamageState.BARREL;
            }
        }
        if (k == VehicleKind.MARK_I && VehicleDamage.markITrackHit(o, d, limit) >= 0) {
            externalTrackHit = true;   // the link belt takes module damage and adds spaced armour, but the shell continues
        }
        if (tModule >= 0) {
            Vector3d e = new Vector3d(o).add(new Vector3d(d).mul(tModule));
            return new Result(MODULE, GUN, 0, 0, 0, (float) a.penAt(speed), e, new Vector3d(d).negate(), false, false, hitModule);
        }
        if (face == null && k == VehicleKind.MARK_I && externalTrackHit) {
            // A hit on the exposed lower run can pass under the hull; damage the track only, never invent a hull plate.
            int trackModule = p0.x > g.cx() ? DamageState.TRACK_L : DamageState.TRACK_R;
            return new Result(MODULE, TRACK, 0, 0, 0, (float) a.penAt(speed), new Vector3d(p0),
                    new Vector3d(d).negate(), false, false, trackModule);
        }
        Vector3d entry;
        int axis;
        double sg;
        if (face == null) {
            // grazing hit on a fender / stowage outside the boxes: nearest hull face to the impact point
            // r20: the face the round is flying INTO (largest direction component), not the nearest one by position -
            // a low shot between the road wheels passes under the hull box and used to "ricochet off the floor" at 87 deg.
            entry = new Vector3d(p0);
            double ax0 = Math.abs(d.x), ay0 = Math.abs(d.y), az0 = Math.abs(d.z);
            if (az0 >= ax0 && az0 >= ay0) {
                axis = 2;
                sg = -Math.signum(d.z);
            } else if (ax0 >= ay0) {
                axis = 0;
                sg = -Math.signum(d.x);
            } else {
                axis = 1;
                sg = -Math.signum(d.y);
            }
        } else {
            entry = new Vector3d(o).add(new Vector3d(d).mul(tHit));
            axis = (int) face[1];
            sg = face[2];
        }
        if (sg == 0) sg = 1;
        int zone;
        Vector3d n;
        boolean viaTrack = externalTrackHit;
        if (turretFace) {
            double aTF = Math.toRadians(tb.ang[TUR_F]), aTS = Math.toRadians(tb.ang[TUR_S]), aTR = Math.toRadians(tb.ang[TUR_R]);
            Vector3d ln;
            if (axis == 2 && sg * fz > 0) {
                zone = TUR_F;
                ln = new Vector3d(0, Math.sin(aTF), Math.cos(aTF) * fz);
            } else if (axis == 2) {
                zone = TUR_R;
                ln = new Vector3d(0, Math.sin(aTR), -Math.cos(aTR) * fz);
            } else if (axis == 0) {
                zone = TUR_S;
                ln = new Vector3d(sg * Math.cos(aTS), Math.sin(aTS), 0);
            } else if (sg > 0) {
                zone = TUR_T;
                ln = new Vector3d(0, 1, 0);
            } else {
                zone = TUR_S;
                ln = new Vector3d(0, -1, 0);
            }
            n = rot(ln, ang[0]);
        } else {
            boolean low = entry.y < g.cy() + g.hy() * 0.2;
            if (axis == 2 && sg * fz > 0) {
                boolean upper = entry.y > g.cy();
                zone = upper ? HULL_UF : HULL_LF;
                double aa = Math.toRadians(tb.ang[zone]);
                n = new Vector3d(0, upper ? Math.sin(aa) : -Math.sin(aa), Math.cos(aa) * fz);
                if (g.tracked() && !upper && entry.y <= g.rgTop() && Math.abs(entry.x - g.cx()) > g.hx() - g.trackW()) viaTrack = true;
            } else if (axis == 2) {
                zone = HULL_R;
                double aa = Math.toRadians(tb.ang[HULL_R]);
                n = new Vector3d(0, Math.sin(aa), -Math.cos(aa) * fz);
            } else if (axis == 0) {
                zone = HULL_S;
                double aa = low ? 0 : Math.toRadians(tb.ang[HULL_S]);      // sponson / upper side plate slope
                n = new Vector3d(sg * Math.cos(aa), Math.sin(aa), 0);
                if (g.tracked() && entry.y <= g.rgTop()) viaTrack = true;   // r31: only the running-gear band
            } else if (sg > 0) {
                zone = HULL_T;
                n = new Vector3d(0, 1, 0);
            } else {
                zone = HULL_B;
                n = new Vector3d(0, -1, 0);
            }
        }
        // r31: weak components (driver vision slit, roof hatches, the turret ring gap, fuel tanks behind their plate)
        int comp = component(g, k, entry);
        if (comp >= 0) zone = comp;
        n.normalize();
        double cosA = Math.max(-1, Math.min(1, -d.dot(n)));
        if (cosA < 0) {             // leaving face (point blank inside a box): treat as perpendicular
            cosA = Math.abs(cosA);
        }
        double angle = Math.toDegrees(Math.acos(cosA));
        float nominal = switch (zone) {
            case VIEW -> Math.max(6f, tb.mm(HULL_UF) * 0.6f);   // the visor block is thinner than the plate around it
            case HATCH -> tb.mm(HULL_T);                        // lids are roof thickness, but a separate hit zone
            default -> tb.mm(zone < VIEW ? zone : HULL_S);
        };
        boolean overmatch = a.calMm() > 3.0 * nominal;
        double an = angle;
        switch (a.type()) {
            case AP, APHE -> an = Math.max(0, angle - 5);       // full-calibre nose bites into the plate
            case APCR -> an = Math.max(0, angle - 2);
            default -> {
            }
        }
        if (overmatch) an = Math.min(an, 30);                   // a shell 3x thicker than the plate ignores most of the slope
        double eff;
        if (a.type() == Type.HE) {
            eff = nominal;                                      // blast + fragments: slope ignored
        } else {
            eff = nominal / Math.max(0.12, Math.cos(Math.toRadians(an)));
            if ((a.type() == Type.HEAT) && (zone == HULL_UF || zone == TUR_F)) eff *= tb.ce();
        }
        if (viaTrack) eff += a.type() == Type.HEAT ? TRACK_MM * 4 : TRACK_MM;   // a HEAT jet forms early on the track links
        double pen = a.penAt(speed);
        // ---- ricochet window (deg of the normalised angle)
        double r0, r1;
        switch (a.type()) {
            case APFSDS -> { r0 = 78; r1 = 84; }
            case APCR -> { r0 = 55; r1 = 68; }
            case AP, APHE -> { r0 = 60; r1 = 72; }
            case HEAT -> { r0 = 80; r1 = 86; }            // fuze graze / jet deflection
            default -> { r0 = 999; r1 = 1000; }
        }
        double pr = overmatch ? 0 : Math.max(0, Math.min(1, (an - r0) / (r1 - r0)));
        int res;
        if (pr > 0 && roll < pr) res = RICOCHET;
        else res = pen >= eff ? PEN : NO_PEN;
        return new Result(res, zone, nominal, (float) eff, (float) angle, (float) pen, entry, n, viaTrack, overmatch, -1);
    }

    /** r31: the weak component (VIEW / HATCH / FUEL / RING) whose spec part box holds the entry point, else -1. */
    public static int component(VehicleDamage.Geo g, VehicleKind k, Vector3d e) {
        String[] kinds = k.spec.partKinds();
        float[][] parts = k.spec.parts();
        for (int i = 0; i < kinds.length; i++) {
            int zone = switch (kinds[i]) {
                case "vision" -> VIEW;
                case "hatch" -> HATCH;
                default -> -1;      // fuel / ring boxes lie inside the hull: they are module zones, see VehicleDamage.zone
            };
            if (zone < 0) continue;
            float[] b = parts[i];
            if (Math.abs(e.x - b[0]) < b[3] && Math.abs(e.y - b[1]) < b[4] && Math.abs(e.z - b[2]) < b[5]) return zone;
        }
        return -1;
    }

    /** Ricochet direction: the flight vector reflected off the plate and flattened a bit (energy loss). */
    public static Vector3d reflect(Vector3d d, Vector3d n) {
        Vector3d r = new Vector3d(d).sub(new Vector3d(n).mul(2 * d.dot(n)));
        return r.normalize();
    }

    // ============================================================================================ spall

    /** Number of spall rays and cone half angle (deg) behind a penetration. */
    public static int spallCount(Type t, double excess) {
        return switch (t) {
            case HEAT -> 4;
            case HE -> 0;
            case EFP -> 5;
            case APHE -> 6;
            default -> (int) Math.max(3, Math.min(12, Math.round(4 + 8 * Math.min(1, excess))));
        };
    }

    public static float spallAngle(Type t, double excess) {
        return switch (t) {
            case HEAT -> 8f;
            case EFP -> 14f;
            case APHE -> 30f;
            default -> (float) Math.max(10, Math.min(35, 12 + 23 * Math.min(1, excess)));
        };
    }

    /** Deterministic cone of spall directions around d (same on server and client). */
    public static Vector3d[] spall(long seed, Vector3d d, double halfDeg, int n) {
        java.util.Random r = new java.util.Random(seed);
        Vector3d u = Math.abs(d.y) < 0.9 ? new Vector3d(0, 1, 0) : new Vector3d(1, 0, 0);
        Vector3d a = new Vector3d(d).cross(u).normalize();
        Vector3d b = new Vector3d(d).cross(a).normalize();
        double c0 = Math.cos(Math.toRadians(halfDeg));
        Vector3d[] out = new Vector3d[n];
        for (int i = 0; i < n; i++) {
            double ct = 1 - r.nextDouble() * (1 - c0);
            double st = Math.sqrt(Math.max(0, 1 - ct * ct));
            double ph = (i + r.nextDouble() * 0.8) / n * Math.PI * 2;
            out[i] = new Vector3d(d).mul(ct).add(new Vector3d(a).mul(st * Math.cos(ph))).add(new Vector3d(b).mul(st * Math.sin(ph))).normalize();
        }
        return out;
    }

    /** r31: 1.0..1.8 - how close the shell path passes to the crew compartment centre (a centre hit spalls harder). */
    public static double centreBoost(VehicleDamage.Geo g, Vector3d entry, Vector3d dir, double tEnd) {
        Vector3d c = new Vector3d(g.cx(), g.cy(), g.cz());
        Vector3d w = new Vector3d(c).sub(entry);
        double t = Math.max(0.0, Math.min(tEnd, w.dot(dir)));
        double d = c.distance(entry.x + dir.x * t, entry.y + dir.y * t, entry.z + dir.z * t);
        return 1.0 + 0.8 * Math.max(0.0, Math.min(1.0, 1.0 - d / 1.5));
    }

    /** r31: true when the shell path crosses the engine compartment (the block absorbs fragments). */
    public static boolean crossesEngine(VehicleDamage.Geo g, VehicleKind k, Vector3d entry, Vector3d dir, double tEnd, float[] ang) {
        for (double t = 0.1; t < tEnd; t += 0.12) {
            if (VehicleDamage.zone(g, k, false, entry.x + dir.x * t, entry.y + dir.y * t, entry.z + dir.z * t, ang)
                    == DamageState.ENGINE) return true;
        }
        return false;
    }

    /** Length (m) a spall ray / the shell travels inside the vehicle before it hits the far wall. */
    public static double travel(VehicleDamage.Geo g, VehicleKind k, boolean turretGone, float[] ang, Vector3d start, Vector3d dir, double max) {
        double s = 0.05;
        for (; s < max; s += 0.06) {
            if (!inside(g, k, turretGone, ang, start.x + dir.x * s, start.y + dir.y * s, start.z + dir.z * s)) break;
        }
        return Math.min(s, max);
    }
}
