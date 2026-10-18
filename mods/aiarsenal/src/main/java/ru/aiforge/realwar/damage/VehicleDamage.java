package ru.aiforge.realwar.damage;

import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import ru.aiforge.realwar.blast.ConventionalBlast;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.vehicle.GunVehicle;
import ru.aiforge.realwar.vehicle.LauncherVehicle;
import ru.aiforge.realwar.vehicle.VehicleEntity;
import ru.aiforge.realwar.vehicle.VehicleKind;

import static ru.aiforge.realwar.damage.DamageState.*;

/**
 * r18 War Thunder style module damage of ground vehicles. Modules live in the hull frame (x left, y up, z forward):
 * tracks along the sides, engine (rear, Bradley front, trucks: cab), turret ring / traverse drive, gun barrel,
 * ammunition (T-90 / BMP carousel under the turret, Abrams bustle with blow-out panels, WW2 sponson racks, TOS pack).
 * AP rounds march through the modules along the shot line; HE damages what is inside its splash radius.
 */
public final class VehicleDamage {
    public static final int CAROUSEL = 0, BUSTLE = 1, SPONSON = 2, PACK = 3;
    private static final double[][] MARK_I_TRACK_PATH = {{0.13, -2.45}, {0.82, -3.28}, {2.28, -3.28},
            {2.28, 3.28}, {0.82, 3.28}, {0.13, 2.45}}; // (height, forward) from tools/wwi/build_mark_i.py
    private static final double MARK_I_TRACK_X = 1.50;
    /** Half width of a track shoe and the radius the belt is tested with (build_mark_i.py: 0.25 m wide shoes). */
    private static final double MARK_I_TRACK_R = 0.17;
    /** r28: the closed belt as 12 capsules (one per side and path edge) so a shot meets it with an exact
     *  ray/capsule solution instead of a 0.05 m march through {@link #zone}. */
    private static final ru.aiforge.realwar.damage.Armour.Tube[] MARK_I_BELT = markIBelt();

    private static ru.aiforge.realwar.damage.Armour.Tube[] markIBelt() {
        java.util.List<ru.aiforge.realwar.damage.Armour.Tube> out = new java.util.ArrayList<>();
        for (double side : new double[]{-MARK_I_TRACK_X, MARK_I_TRACK_X}) {
            for (int i = 0; i < MARK_I_TRACK_PATH.length; ++i) {
                double[] a = MARK_I_TRACK_PATH[i], b = MARK_I_TRACK_PATH[(i + 1) % MARK_I_TRACK_PATH.length];
                double dy = b[0] - a[0], dz = b[1] - a[1];
                double len = Math.sqrt(dy * dy + dz * dz);
                if (len < 1e-6) continue;
                out.add(new ru.aiforge.realwar.damage.Armour.Tube(
                        new org.joml.Vector3d(side, a[0], a[1]),
                        new org.joml.Vector3d(0, dy / len, dz / len), len, MARK_I_TRACK_R, 0.0));
            }
        }
        return out.toArray(new ru.aiforge.realwar.damage.Armour.Tube[0]);
    }

    /** r28: entry distance of the hull-frame ray o + t*d (t in 0..maxT) into the Mark I link belt, or -1. */
    public static double markITrackHit(org.joml.Vector3d o, org.joml.Vector3d d, double maxT) {
        double best = -1;
        for (ru.aiforge.realwar.damage.Armour.Tube t : MARK_I_BELT) {
            double tt = ru.aiforge.realwar.damage.Armour.tubeHit(o, d, t, maxT);
            if (tt >= 0 && (best < 0 || tt < best)) best = tt;
        }
        return best;
    }

    private VehicleDamage() {
    }

    // ------------------------------------------------------------------------------------------ geometry

    /** {turret tonnes (0 = no turret toss), kg of propellant+HE per stowed round, blow-out panels, ammo stowage}. */
    static float[] params(VehicleKind k) {
        return switch (k) {
            case T90M -> new float[]{11, 9, 0, CAROUSEL};
            case ABRAMS -> new float[]{20, 9, 1, BUSTLE};
            case BMP3 -> new float[]{3, 3, 0, CAROUSEL};
            case BRADLEY -> new float[]{3, 1.2f, 0, CAROUSEL};
            case MSTA -> new float[]{15, 25, 0, BUSTLE};
            case PZ2 -> new float[]{1.5f, 0.5f, 0, SPONSON};
            case PZ4 -> new float[]{4, 4, 0, SPONSON};
            case TIGER -> new float[]{11, 6, 0, SPONSON};
            case T34 -> new float[]{4, 4, 0, SPONSON};
            case KV2 -> new float[]{12, 20, 0, SPONSON};
            case IS2 -> new float[]{8, 15, 0, SPONSON};
            case KV5 -> new float[]{20, 10, 0, SPONSON};
            case MAUS -> new float[]{50, 12, 0, SPONSON};
            case RATTE -> new float[]{0, 40, 0, SPONSON};
            case STURMTIGER -> new float[]{0, 60, 0, SPONSON};
            case TOS1A -> new float[]{12, 30, 0, PACK};
            case MARK_I -> new float[]{0, 2.72f, 0, SPONSON};
            default -> new float[]{0, 5, 0, CAROUSEL};
        };
    }

    public record Geo(float cx, float cy, float cz, float hx, float hy, float hz, float trackW, boolean tracked, boolean turret,
               float[] yp, float[] pp, float tube, int ammo, boolean engineFront, float rgTop) {}

    public static Geo geo(VehicleEntity v) {
        boolean tracked = v instanceof GunVehicle gv && gv.cfg.tracked();
        boolean turret = v instanceof LauncherVehicle && v.spec.yawPivot() != null && v.kind != VehicleKind.MARK_I;
        return geo(v.kind, tracked, turret);
    }

    /** r20: entity-free form (client hit camera, gametests). */
    public static Geo geo(VehicleKind k, boolean tracked, boolean turret) {
        float[] body = null;
        for (int i = 0; i < k.spec.partKinds().length; i++) if ("body".equals(k.spec.partKinds()[i])) body = k.spec.parts()[i];
        float cx = 0, cy, cz = 0, hx, hy, hz;
        if (body != null) {
            cx = body[0]; cy = body[1]; cz = body[2]; hx = body[3]; hy = body[4]; hz = body[5];
        } else {
            hx = k.spec.aabbW() * 0.5f; hy = k.spec.height() * 0.4f; cy = hy; hz = k.spec.length() * 0.45f;
        }
        float tw = Mth.clamp(hx * 0.32f, 0.35f, 1.2f);
        if (k == VehicleKind.RATTE) tw = hx * 0.4f;
        turret = turret && k.spec.yawPivot() != null && k != VehicleKind.MARK_I;
        boolean front = k == VehicleKind.BRADLEY || !tracked;
        // top of the running gear: only a hit below it really passes through track links (the Mark I belt wraps the
        // whole side, so its links stand in front of the hull side only in the bottom run)
        float rgTop = tracked ? (k == VehicleKind.MARK_I ? 0.35f : 0.9f) : 0.0f;
        return new Geo(cx, cy, cz, hx, hy, hz, tw, tracked, turret, k.spec.yawPivot(), k.spec.pitchPivot(), k.tubeLen,
                (int) params(k)[3], front, rgTop);
    }

    static float scale(VehicleEntity v) {
        return (float) Mth.clamp(Math.sqrt(v.kind.hp / 600.0), 0.55, 2.2);
    }

    static float maxHp(VehicleEntity v, int m) {
        float s = scale(v);
        return switch (m) {
            case TRACK_L, TRACK_R -> 40 * s;
            case ENGINE -> 55 * s;
            case BARREL -> 35 * s;
            case TURRET -> 45 * s;
            default -> 50 * s;
        };
    }

    /** Hull-frame turret angles (stabilised gun drawn on a tilted hull). */
    public static float[] turretAngles(VehicleEntity v) {
        if (!(v instanceof LauncherVehicle lv)) return new float[]{0, 0};
        if (v.kind == VehicleKind.MARK_I) {
            // hand-laid sponson guns; no stabiliser, and each gun lives inside its own traverse sector
            return new float[]{v.kind.sponsonAz(0, lv.az()), v.kind.sponsonEl(lv.el())};
        }
        return v.stabilised(lv.az(), lv.el(), v.bodyPitch, v.bodyRoll);
    }

    /** Module at a hull-frame point, or -1. */
    static int zone(Geo g, VehicleEntity v, double x, double y, double z, float[] ang) {
        return zone(g, v.kind, v.dmg.broken(TURRET_GONE), x, y, z, ang);
    }

    /** r20: entity-free form (armour model, client hit camera). */
    public static int zone(Geo g, VehicleKind kind, boolean turretGone, double x, double y, double z, float[] ang) {
        // r28: the barrel test is the same finite-cylinder collider the hit resolution and the client reticle use,
        // with the real tube radius of each gun instead of a 0.18 / 0.24 m one-size-fits-all cylinder.
        for (ru.aiforge.realwar.damage.Armour.Tube tube : ru.aiforge.realwar.damage.Armour.tubes(g, kind, turretGone, ang)) {
            if (tube.contains(x, y, z)) return BARREL;
        }
        if (kind == VehicleKind.MARK_I) {
            if (Math.abs(Math.abs(x) - MARK_I_TRACK_X) < MARK_I_TRACK_R && nearMarkITrack(y, z)) return x > 0 ? TRACK_L : TRACK_R;
            if (Math.abs(Math.abs(x) - 1.41) < 0.42 && y > 0.80 && y < 1.66 && Math.abs(z) < 0.87) return AMMO;
        }
        if (g.turret && !turretGone) {
            double dx = x - g.yp[0], dz = z - g.yp[2];
            double r2 = dx * dx + dz * dz;
            if (r2 < 0.85 * 0.85 && y > g.yp[1] - 0.25 && y < g.yp[1] + 0.45) return TURRET;
            if (g.ammo == BUSTLE && y > g.yp[1] + 0.05 && y < g.yp[1] + 0.8) {
                // r20: the bustle rack turns with the turret (turret-local coordinates)
                double a = Math.toRadians(ang[0]), sa = Math.sin(a), ca = Math.cos(a);
                double lx = dx * ca - dz * sa, lz = (dx * sa + dz * ca) * kind.fireAxisZ();
                if (lz < -1.2 && lz > -2.7 && Math.abs(lx) < 1.1) return AMMO;
            }
            if (g.ammo == PACK && r2 < 1.7 * 1.7 && y > g.yp[1] + 0.15 && y < g.yp[1] + 2.2) return AMMO;
        }
        boolean inHull = Math.abs(x - g.cx) < g.hx + 0.15 && y > 0 && y < g.cy + g.hy + 0.1 && Math.abs(z - g.cz) < g.hz + 0.25;
        if (!inHull) return -1;
        // r31: per-component modules from the spec part boxes (fuel tanks ignite, the turret ring jams the drive)
        for (int i = 0; i < kind.spec.partKinds().length; i++) {
            String pk = kind.spec.partKinds()[i];
            if (!"fuel".equals(pk) && !"ring".equals(pk)) continue;
            float[] b = kind.spec.parts()[i];
            if (Math.abs(x - b[0]) < b[3] && Math.abs(y - b[1]) < b[4] && Math.abs(z - b[2]) < b[5]) {
                return "fuel".equals(pk) ? FIRE : TURRET;
            }
        }
        if (g.turret && g.ammo == CAROUSEL) {
            double dx = x - g.yp[0], dz = z - g.yp[2];
            if (dx * dx + dz * dz < 1.0 && y > g.cy - g.hy && y < g.cy) return AMMO;
        }
        if (g.tracked && g.ammo == SPONSON && Math.abs(z - (g.yp != null ? g.yp[2] : g.cz)) < 1.6) {
            double ax = Math.abs(x - g.cx);
            if (ax > g.hx - g.trackW - 0.55 && ax < g.hx - g.trackW + 0.05 && y > g.cy - 0.1 && y < g.cy + g.hy) return AMMO;
        }
        if (g.tracked && Math.abs(x - g.cx) > g.hx - g.trackW && y < g.cy + g.hy * 0.2) return x > g.cx ? TRACK_L : TRACK_R;
        boolean eng = g.engineFront ? z > g.cz + g.hz * 0.35 : z < g.cz - g.hz * 0.36;
        if (kind.drivable() && eng && Math.abs(x - g.cx) < g.hx - (g.tracked ? g.trackW : 0.1f) && y > g.cy - g.hy) return ENGINE;
        return -1;
    }

    private static boolean nearMarkITrack(double y, double z) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < MARK_I_TRACK_PATH.length; ++i) {
            double[] a = MARK_I_TRACK_PATH[i], b = MARK_I_TRACK_PATH[(i + 1) % MARK_I_TRACK_PATH.length];
            double dy = b[0] - a[0], dz = b[1] - a[1];
            double den = dy * dy + dz * dz;
            double t = den < 1e-9 ? 0.0 : ((y - a[0]) * dy + (z - a[1]) * dz) / den;
            t = Mth.clamp(t, 0.0, 1.0);
            double ey = y - (a[0] + dy * t), ez = z - (a[1] + dz * t);
            best = Math.min(best, ey * ey + ez * ez);
        }
        return best < 0.17 * 0.17;
    }

    // ------------------------------------------------------------------------------------------ hits

    /** Server: a hit on a living vehicle (called from VehicleEntity.hurt before the hull HP is reduced).
     *  @return multiplier of the hull HP damage (r20: ricochet / non-penetration barely scratch the hull) */
    public static float onHurt(VehicleEntity v, DamageSource src, float amount) {
        if (!(v.level() instanceof ServerLevel sl) || v.dmg.dead()) return 1f;
        HitContext.Hit h = HitContext.get();
        String id = src.getMsgId();
        HitContext.Shot blastShot = null;
        HitContext.Shot prevShot = null;
        if (h == null) {
            // r31: a registered blast centre (ground burst under the belly, burst in the turret ring gap) beats
            // the random hull point: the splash and the crew sphere then come from the real direction
            HitContext.Blast bl = HitContext.takeBlast();
            if (bl != null && bl.pos().distanceToSqr(v.position()) < 400.0) {
                h = new HitContext.Hit(bl.pos(), v.position().subtract(bl.pos()).normalize(), HitContext.Kind.HE, bl.tnt());
                prevShot = HitContext.shot();
                HitContext.setShot(bl.shot());
                blastShot = bl.shot();
            } else {
                // no context: explosions and ordnance hit a random point of the hull
                if (!(id.contains("ordnance") || id.contains("blast") || src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION))) return 1f;
                RandomSource r = sl.random;
                Vec3 c = v.localToWorld((r.nextDouble() - 0.5) * v.spec.aabbW(), v.spec.height() * (0.2 + 0.6 * r.nextDouble()), (r.nextDouble() - 0.5) * v.spec.length() * 0.8);
                h = new HitContext.Hit(c, Vec3.ZERO, HitContext.Kind.HE, amount / 8.0);
            }
        }
        if (h.kind() == HitContext.Kind.BULLET) return 1f;            // armour stops rifle-calibre fire
        Geo g = geo(v);
        Matrix4d inv = new Matrix4d(v.bodyFrame()).invert();
        Vector3d p = inv.transformPosition(new Vector3d(h.pos().x, h.pos().y, h.pos().z));
        Vector3d d = inv.transformDirection(new Vector3d(h.dir().x, h.dir().y, h.dir().z));
        float[] ang = turretAngles(v);
        HitContext.Shot shot = HitContext.shot();
        float mul = 1f;
        if (shot != null && d.lengthSquared() > 1e-6) {
            d.normalize();
            mul = armoured(sl, v, g, p, d, amount, ang, shot);
        } else if (h.kind() == HitContext.Kind.AP && d.lengthSquared() > 1e-6) {
            d.normalize();
            ap(sl, v, g, p, d, amount, ang);
        } else {
            double tnt = Math.max(0.2, h.power());
            double R = 0.7 + 0.35 * Math.cbrt(tnt);
            boolean soft = !Armour.armoured(v.kind);
            splash(sl, v, g, p, R, amount, soft ? 1.0 : tnt >= 8 ? 0.5 : 0.15, ang);
            Crew.sphere(sl, v, g, ang[0], p, (soft ? 1.0 : 0.5) + 0.5 * Math.cbrt(tnt), soft ? 120f : 35f);
        }
        if (mul > 0.1f) addHole(v, g, p, d, h);
        v.dmg.dirtyNow = true;
        if (blastShot != null) HitContext.setShot(prevShot);
        return mul;
    }

    // ------------------------------------------------------------------------------------------ r20 armour + hit camera

    /** Hit camera data captured during onHurt; sent by {@link #flushCam} once the hull HP is known. */
    public record Cam(int target, int shooter, VehicleKind kind, boolean tracked, boolean turret, boolean turretGone, float az, float el,
                      Armour.Result res, Armour.Ammo ammo, float dist, float tEnd, long seed, int spallN, float spallAng,
                      Vector3d dir, int[] mods, int[] states, int crewHit, int crewDead, boolean destroyed) {
        public Cam dead(boolean d) {
            return new Cam(target, shooter, kind, tracked, turret, turretGone, az, el, res, ammo, dist, tEnd, seed, spallN, spallAng, dir, mods, states, crewHit, crewDead, d);
        }

        public boolean crew() {
            return this.crewHit != 0;
        }
    }

    /** Last resolved hit (server thread; read by flushCam and the gametests). */
    @Nullable
    public static Cam lastCam;
    @Nullable
    private static Cam pendingCam;

    /** Module ids reported on the hit camera (+ CREW pseudo module). */
    public static final int[] CAM_MODS = {ENGINE, TRACK_L, TRACK_R, BARREL, TURRET, AMMO, FIRE, TURRET_GONE};
    public static final int CREW = 30;

    private static float armoured(ServerLevel sl, VehicleEntity v, Geo g, Vector3d p, Vector3d d, float amount, float[] ang, HitContext.Shot shot) {
        DamageState s = v.dmg;
        int[] lv0 = new int[CAM_MODS.length];
        float[] md0 = new float[CAM_MODS.length];
        for (int i = 0; i < CAM_MODS.length; i++) {
            lv0[i] = s.level(CAM_MODS[i]);
            md0[i] = s.mdmg[CAM_MODS[i]];
        }
        Crew.resetLog();
        boolean soft = !Armour.armoured(v.kind);
        boolean tg = s.broken(TURRET_GONE);
        Armour.Ammo a = shot.ammo();
        Vector3d dir = new Vector3d(d);
        if (a.type() == Armour.Type.EFP) dir.set(0, -1, 0);           // TOW-2B: the EFPs fire down over the target
        Armour.Result r = Armour.solve(g, v.kind, tg, ang, p, dir, a, shot.speed(), sl.random.nextDouble());
        if (r.viaTrack()) module(sl, v, p.x > g.cx ? TRACK_L : TRACK_R, amount * 0.6f);
        long seed = sl.random.nextLong();
        int spN = 0;
        float spA = 0;
        double tEnd = 0;
        float mul;
        switch (r.result()) {
            case Armour.RICOCHET -> {
                mul = 0.04f;
                sl.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 2.5f, 1.6f);
            }
            case Armour.MODULE -> {
                boolean trackOnly = r.module() == TRACK_L || r.module() == TRACK_R;
                mul = trackOnly ? 0.0f : 0.15f;
                module(sl, v, r.module() >= 0 ? r.module() : BARREL, amount * (trackOnly ? 0.6f : 0.8f));
            }
            case Armour.NO_PEN -> {
                if (a.type() == Armour.Type.HE) {
                    // r21: HE that does not get through: the blast scars the plate, wrecks tracks / barrel / optics,
                    // and a big charge on a not-much-thicker plate still concusses the men behind it
                    double tnt = Math.max(0.05, a.fillerKg());
                    double ratio = Mth.clamp(r.pen() / Math.max(1.0, r.eff()), 0.0, 1.0);
                    mul = (float) (heHull(tnt) * (0.06 + 0.12 * ratio) / Math.max(1f, amount));
                    splash(sl, v, g, r.entry(), 0.8 + 0.45 * Math.cbrt(tnt), (float) heMod(tnt), 0.0, ang);   // tracks / barrel only
                    if (ratio > 0.6 && tnt >= 1.0) {
                        Crew.sphere(sl, v, g, ang[0], r.entry(), 0.6 + 0.4 * Math.cbrt(tnt), (float) (70.0 * (ratio - 0.6) / 0.4 * Math.min(1.0, tnt / 4.0)));
                    } else if ((r.zone() == Armour.HATCH || r.zone() == Armour.VIEW) && tnt >= 0.2) {
                        // r31: a burst right over a lid or a visor block concusses the men behind the weak component
                        Crew.sphere(sl, v, g, ang[0], r.entry(), 0.6 + 0.4 * Math.cbrt(tnt), (float) (60.0 * Math.min(1.0, tnt)));
                    }
                    s.lastHeTick = v.tickCount;
                } else {
                    mul = 0.12f;
                }
            }
            default -> {   // PEN
                mul = 1f;
                double ex = Math.min(1.0, r.excess());
                if (a.type() == Armour.Type.HE) {
                    // r21: HE inside (soft skin or thin plate): blast + fragments wreck everything around the burst,
                    // the hull takes the full blast (trucks, launchers, radars are blown apart by a rocket / shell)
                    double tnt = Math.max(0.05, a.fillerKg());
                    Vector3d c = new Vector3d(r.entry()).add(new Vector3d(dir).mul(0.35));
                    splash(sl, v, g, c, 0.9 + 0.5 * Math.cbrt(tnt), (float) (heMod(tnt) * 1.3), 1.0, ang);
                    Crew.sphere(sl, v, g, ang[0], c, 1.1 + 0.7 * Math.cbrt(tnt), 190f);
                    if (soft) Crew.fragments(sl, v, Math.min(0.9, 0.25 + 0.25 * Math.cbrt(tnt)), 60f);
                    mul = (float) (heHull(tnt) * (soft ? 1.0 : 0.55) / Math.max(1f, amount));
                    tEnd = 0.35;
                    s.lastHeTick = v.tickCount;
                } else {
                    // r21: kinetic / shaped charge: damage only along the shell / jet path and by its spall; the hull
                    // integrity drops much less than from HE (an APFSDS overpenetrates a truck)
                    mul = soft ? 0.3f : (float) (0.45 + 0.25 * ex);
                    float budget = (float) (amount * (0.6 + 0.4 * ex));
                    tEnd = apFrom(sl, v, g, r.entry(), dir, budget, ang, tg);
                    // r31: a round through the middle of the crew compartment throws deadlier spall; the engine
                    // block in the shell path absorbs fragments and shields the men behind it
                    double boost = Armour.centreBoost(g, r.entry(), dir, tEnd);
                    boolean eng = Armour.crossesEngine(g, v.kind, r.entry(), dir, tEnd, ang);
                    spN = Armour.spallCount(a.type(), ex);
                    if (eng) spN = Math.max(2, spN / 2);
                    spA = Armour.spallAngle(a.type(), ex);
                    Vector3d st = new Vector3d(r.entry()).add(new Vector3d(dir).mul(0.08));
                    float per = (float) (amount * 0.30 / Math.max(1, spN) * (1.0 + ex));
                    float perCrew = (float) Mth.clamp((22.0 + 40.0 * ex) * Math.max(0.5, Math.min(1.4, a.calMm() / 100.0)), 12.0, 75.0);
                    perCrew = (float) (perCrew * boost * (eng ? 0.5 : 1.0));
                    for (Vector3d sd : Armour.spall(seed, dir, spA, spN)) {
                        double L = Armour.travel(g, v.kind, tg, ang, st, sd, 4.5);
                        boolean[] done = new boolean[N];
                        for (double t = 0.05; t < L; t += 0.07) {
                            int m = zone(g, v, st.x + sd.x * t, st.y + sd.y * t, st.z + sd.z * t, ang);
                            if (m < 0 || done[m] || m == BARREL || m == TRACK_L || m == TRACK_R) continue;
                            done[m] = true;
                            module(sl, v, m, per);
                        }
                        Crew.segment(sl, v, g, ang[0], st, new Vector3d(st).add(new Vector3d(sd).mul(L)), 0.02, perCrew);
                    }
                    if (a.type() == Armour.Type.APHE && a.fillerKg() > 0) {
                        // the burster goes off ~0.6 m behind the plate (delay fuze)
                        double f = a.fillerKg();
                        Vector3d c = new Vector3d(r.entry()).add(new Vector3d(dir).mul(Math.min(0.6, tEnd)));
                        splash(sl, v, g, c, 0.9 + 0.9 * Math.cbrt(f), (float) (25 + 220 * Math.min(1.0, f)), 1.0, ang);
                        Crew.sphere(sl, v, g, ang[0], c, 1.0 + 1.1 * Math.cbrt(f), 200f);
                        sl.sendParticles(ParticleTypes.EXPLOSION, v.getX(), v.getY() + g.cy, v.getZ(), 1, 0.3, 0.3, 0.3, 0);
                    }
                }
            }
        }
        if (Crew.knockedOut(v)) knockOut(sl, v);
        // ---- what changed
        java.util.List<int[]> ch = new java.util.ArrayList<>();
        for (int i = 0; i < CAM_MODS.length; i++) {
            int m = CAM_MODS[i];
            int l = s.level(m);
            if (l > lv0[i] || s.mdmg[m] > md0[i] + 0.5f) ch.add(new int[]{m, Math.max(1, l)});
        }
        int[] mods = new int[ch.size()], sts = new int[ch.size()];
        for (int i = 0; i < ch.size(); i++) {
            mods[i] = ch.get(i)[0];
            sts[i] = ch.get(i)[1];
        }
        float dist = shot.origin() != null && shot.origin() != Vec3.ZERO ? (float) (shot.origin().distanceTo(v.position()) * ru.aiforge.realwar.ordnance.Ballistics.scaleK()) : 0f;
        Cam cam = new Cam(v.getId(), shot.shooter(), v.kind, g.tracked, g.turret, tg, ang[0], ang[1], r, a, dist, (float) tEnd, seed, spN, spA,
                dir, mods, sts, Crew.logHit, Crew.logDead, false);
        lastCam = cam;
        pendingCam = cam;
        return mul;
    }

    /** Called by VehicleEntity.hurt after the hull HP was applied: sends the hit camera to the shooter. */
    public static void flushCam(VehicleEntity v) {
        Cam c = pendingCam;
        pendingCam = null;
        if (c == null || c.target() != v.getId() || !(v.level() instanceof ServerLevel sl)) return;
        c = c.dead(v.dmg.dead() || v.hp() <= 0f);
        lastCam = c;
        net.minecraft.server.level.ServerPlayer sp = shooterPlayer(sl, c.shooter());
        if (sp != null) {
            ru.aiforge.realwar.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                    ru.aiforge.realwar.network.HitCamPacket.of(c));
        }
    }

    @Nullable
    static net.minecraft.server.level.ServerPlayer shooterPlayer(ServerLevel sl, int id) {
        if (id < 0) return null;
        Entity e = sl.getEntity(id);
        if (e instanceof net.minecraft.server.level.ServerPlayer sp) return sp;
        if (e == null) return null;
        if (e.getControllingPassenger() instanceof net.minecraft.server.level.ServerPlayer sp) return sp;
        for (Entity q : e.getPassengers()) if (q instanceof net.minecraft.server.level.ServerPlayer sp) return sp;
        return null;
    }

    /** Penetrated shell inside the vehicle: modules along the line from the entry point; returns the length travelled. */
    private static double apFrom(ServerLevel sl, VehicleEntity v, Geo g, Vector3d e, Vector3d d, float amount, float[] ang, boolean tg) {
        double budget = amount;
        boolean[] done = new boolean[N];
        double t = 0.02;
        double L = Armour.travel(g, v.kind, tg, ang, e, d, 9.0);
        for (; t < L && budget > 3; t += 0.07) {
            int m = zone(g, v, e.x + d.x * t, e.y + d.y * t, e.z + d.z * t, ang);
            if (m < 0 || done[m]) continue;
            done[m] = true;
            module(sl, v, m, (float) budget);
            budget *= (m == TRACK_L || m == TRACK_R || m == BARREL) ? 0.8 : 0.6;
        }
        double end = Math.min(t, L);
        Crew.segment(sl, v, g, ang[0], e, new Vector3d(e).add(new Vector3d(d).mul(end)), 0.05, (float) Math.max(60.0, Math.min(130.0, 60.0 + amount * 0.5)));
        if (t > 0.3) sl.sendParticles(ParticleTypes.LAVA, v.getX(), v.getY() + g.cy, v.getZ(), 6, 0.4, 0.3, 0.4, 0);
        return end;
    }

    private static void ap(ServerLevel sl, VehicleEntity v, Geo g, Vector3d p0, Vector3d d, float amount, float[] ang) {
        double budget = amount;
        boolean[] done = new boolean[N];
        boolean inside = false;
        for (double t = -4.0; t < 9.0 && budget > 3; t += 0.07) {
            double x = p0.x + d.x * t, y = p0.y + d.y * t, z = p0.z + d.z * t;
            if (Math.abs(x - g.cx) < g.hx - g.trackW && y > g.cy - g.hy && y < g.cy + g.hy && Math.abs(z - g.cz) < g.hz) inside = true;
            int m = zone(g, v, x, y, z, ang);
            if (m < 0 || done[m]) continue;
            done[m] = true;
            module(sl, v, m, (float) budget);
            budget *= (m == TRACK_L || m == TRACK_R || m == BARREL) ? 0.8 : 0.6;
        }
        if (inside) {
            Crew.segment(sl, v, g, ang[0], new Vector3d(p0).sub(new Vector3d(d).mul(4.0)), new Vector3d(p0).add(new Vector3d(d).mul(9.0)), 0.05, 100f);
            sl.sendParticles(ParticleTypes.LAVA, v.getX(), v.getY() + g.cy, v.getZ(), 6, 0.4, 0.3, 0.4, 0);
        }
    }

    private static final double[][] DIRS;

    static {
        java.util.List<double[]> l = new java.util.ArrayList<>();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            if (x == 0 && y == 0 && z == 0) continue;
            double n = Math.sqrt(x * x + y * y + z * z);
            l.add(new double[]{x / n, y / n, z / n});
        }
        DIRS = l.toArray(new double[0][]);
    }

    /** Highest proximity factor (1 at the centre .. 0 at R) of every module around a point. */
    static float[] sample(Geo g, VehicleEntity v, Vector3d c, double R, float[] ang) {
        float[] best = new float[N];
        int m0 = zone(g, v, c.x, c.y, c.z, ang);
        if (m0 >= 0) best[m0] = 1f;
        for (double f : new double[]{0.22, 0.5, 0.85}) {
            for (double[] dd : DIRS) {
                int m = zone(g, v, c.x + dd[0] * R * f, c.y + dd[1] * R * f, c.z + dd[2] * R * f, ang);
                if (m >= 0) best[m] = Math.max(best[m], (float) (1.0 - f));
            }
        }
        return best;
    }

    private static boolean external(int m) {
        return m == TRACK_L || m == TRACK_R || m == BARREL || m == ROTOR || m == TAIL_ROTOR || m == WING_L || m == WING_R || m == TAIL;
    }

    private static void splash(ServerLevel sl, VehicleEntity v, Geo g, Vector3d c, double R, float amount, double internal, float[] ang) {
        float[] best = sample(g, v, c, R, ang);
        for (int m = 0; m < N; m++) {
            if (best[m] <= 0) continue;
            module(sl, v, m, (float) (amount * best[m] * (external(m) ? 1.0 : internal)));
        }
    }

    /** r21: hull damage of a contact HE burst of tnt kg (soft vehicles take it whole): 90 * tnt^0.75. */
    public static double heHull(double tnt) {
        return 90.0 * Math.pow(Math.max(0.01, tnt), 0.75);
    }

    /** r21: module damage near an HE burst. */
    static double heMod(double tnt) {
        return 12.0 + 30.0 * Math.pow(Math.max(0.01, tnt), 0.6);
    }

    /** Distance from a hull-frame point to the hull box (0 inside). */
    static double boxDist(Geo g, Vector3d c) {
        double dx = Math.max(0, Math.abs(c.x - g.cx) - g.hx), dz = Math.max(0, Math.abs(c.z - g.cz) - g.hz);
        double y0 = Math.max(0.1, g.cy - g.hy), y1 = g.cy + g.hy;
        double dy = c.y < y0 ? y0 - c.y : c.y > y1 ? c.y - y1 : 0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * r21: near miss of a conventional blast. Tracks / barrel as before; plus the blast wave on the hull (soft
     * vehicles: trucks, launchers, radars are wrecked within ~1.6 cbrt(tnt) m) and the fragments (soft skin: holes,
     * engine, wounded / killed crew within ~7 cbrt(tnt) m; armour: only a big charge close by concusses the crew).
     */
    public static void blastNear(VehicleEntity v, Vec3 centre, double tnt, double dist) {
        if (!(v.level() instanceof ServerLevel sl) || v.dmg.dead() && v.hp() <= 0f) return;   // r21: knocked-out hulls still take blast
        boolean soft = !Armour.armoured(v.kind);
        double Rb = 1.6 * Math.cbrt(tnt), Rf = soft ? 7.0 * Math.cbrt(tnt) : 0.0;
        double R = 1.0 + 0.6 * Math.cbrt(tnt);
        if (dist > Math.max(R, Math.max(Rb, Rf)) + v.spec.length()) return;
        Geo g = geo(v);
        Vector3d c = new Matrix4d(v.bodyFrame()).invert().transformPosition(new Vector3d(centre.x, centre.y, centre.z));
        float[] ang = turretAngles(v);
        float[] best = sample(g, v, c, R, ang);
        double base = 25.0 * Math.cbrt(tnt);
        for (int m : new int[]{TRACK_L, TRACK_R, BARREL}) if (best[m] > 0) module(sl, v, m, (float) (base * best[m]));
        if (v.dmg.lastHeTick == v.tickCount) return;            // this burst was a direct hit, resolved by armoured()
        double ds = boxDist(g, c);
        double hull = 0;
        if (ds < Rb) hull += heHull(tnt) * Math.pow(1.0 - ds / Rb, 2) * (soft ? 0.9 : 0.12);
        if (soft && ds < Rf) {
            double k = Math.pow(1.0 - ds / Rf, 2);
            hull += 14.0 * Math.sqrt(tnt) * k;
            Crew.fragments(sl, v, Math.min(0.85, 0.9 * k), (float) (25.0 + 30.0 * k));
            if (sl.random.nextDouble() < k) module(sl, v, ENGINE, (float) (60.0 * k + 20.0));
        } else if (!soft && tnt >= 50 && ds < Rb * 0.25) {
            Crew.fragments(sl, v, 0.5 * (1.0 - ds / (Rb * 0.25)), 60f);
        }
        if (Crew.knockedOut(v)) knockOut(sl, v);
        if (hull >= 1.0) v.blastHull((float) hull);
        v.dmg.dirtyNow = true;
    }

    /** r21: the crew is knocked out (too few men left): the vehicle stops for good, the hull is not burning. */
    public static void knockOut(ServerLevel sl, VehicleEntity v) {
        DamageState s = v.dmg;
        if (s.dead()) return;
        s.setBroken(DEAD, true);
        s.deadTicks = 0;
        s.repairMod = -1;
        s.swapTo = s.swapFrom = -1;
        v.setEngine(false);
        v.speedMs = 0;
        notify(v, "hud.aiarsenal.rw.crew.ko");
        v.ejectPassengers();
        Vec3 c = v.position().add(0, v.spec.height() * 0.7, 0);
        sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), c.x, c.y, c.z, 20, 0.6, 0.4, 0.6, 0.02);
        if (pendingCam != null && pendingCam.target() == v.getId()) pendingCam = pendingCam.dead(true);
    }

    // ------------------------------------------------------------------------------------------ module damage

    static void module(ServerLevel sl, VehicleEntity v, int m, float d) {
        DamageState s = v.dmg;
        if (d <= 0.5f || s.broken(m) || s.dead()) return;
        if (m == AMMO) {
            ammoHit(sl, v, d);
            return;
        }
        float max = maxHp(v, m);
        s.mdmg[m] += d;
        if (s.mdmg[m] >= max) {
            s.setBroken(m, true);
            s.setHurt(m, false);
            if (s.repairMod == m) s.repairMod = -1;
            broken(sl, v, m);
        } else if (s.mdmg[m] >= max * 0.35 && !s.hurtBit(m)) {
            s.setHurt(m, true);
            notifyMod(v, "hurt", m);
        }
    }

    private static void broken(ServerLevel sl, VehicleEntity v, int m) {
        Vec3 c = v.position();
        notifyMod(v, "broken", m);
        switch (m) {
            case TRACK_L, TRACK_R -> {
                v.speedMs = 0;
                spawnTrack(sl, v, m == TRACK_L ? "L" : "R");
                sl.playSound(null, c.x, c.y, c.z, SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 2.5f, 0.6f);
                sl.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 1.5f, 0.5f);
            }
            case ENGINE -> {
                sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.NEUTRAL, 2f, 0.5f);
                if (sl.random.nextFloat() < 0.4f) ignite(v, 160);
            }
            case BARREL -> {
                Vec3 p = v.localToWorld(0, v.spec.height(), v.spec.length() * 0.35);
                sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), p.x, p.y, p.z, 12, 0.3, 0.2, 0.3, 0.03);
                sl.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_BREAK, SoundSource.NEUTRAL, 2f, 0.6f);
            }
            case TURRET -> sl.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_BREAK, SoundSource.NEUTRAL, 2f, 0.8f);
            default -> {
            }
        }
    }

    static int rounds(VehicleEntity v) {
        return v instanceof LauncherVehicle lv ? lv.loadedCount() : 0;
    }

    private static void ammoHit(ServerLevel sl, VehicleEntity v, float d) {
        DamageState s = v.dmg;
        int n = rounds(v);
        int full = Math.max(1, v.kind.magazine);
        if (n <= 0) {
            s.setHurt(AMMO, true);
            if (sl.random.nextFloat() < 0.3f) ignite(v, 120);
            return;
        }
        float fill = Mth.clamp(n / (float) full, 0f, 1f);
        float chance = Mth.clamp(d / 70f, 0.15f, 0.95f) * (0.3f + 0.7f * fill);
        if (sl.random.nextFloat() < chance) {
            detonate(sl, v);
        } else {
            s.setHurt(AMMO, true);
            if (sl.random.nextFloat() < 0.5f) ignite(v, 160);
        }
    }

    public static void ignite(VehicleEntity v, int ticks) {
        DamageState s = v.dmg;
        if (!s.burning()) notify(v, "hud.aiarsenal.rw.dmg.fire");
        s.setBroken(FIRE, true);
        s.fireTicks = Math.max(s.fireTicks, ticks);
    }

    /** Ammunition rack detonation: blow-out (Abrams) or catastrophic - turret thrown by the gas pressure of the rounds. */
    public static void detonate(ServerLevel sl, VehicleEntity v) {
        DamageState s = v.dmg;
        float[] pr = params(v.kind);
        Geo g = geo(v);
        int n = rounds(v);
        float yp1 = g.yp != null ? g.yp[1] : g.cy;
        Vec3 c = v.localToWorld(g.yp != null ? g.yp[0] : 0, yp1, g.yp != null ? g.yp[2] : 0);
        if (pr[2] > 0.5f) {
            // blow-out panels: the bustle vents upwards, the crew compartment survives; the stowed rounds are lost
            Vec3 b = v.localToWorld(0, yp1 + 0.6, (g.yp != null ? g.yp[2] : 0) - 2.0);
            sl.sendParticles(ParticleTypes.FLAME, b.x, b.y, b.z, 80, 0.5, 0.2, 0.5, 0.35);
            sl.sendParticles(ParticleTypes.LAVA, b.x, b.y, b.z, 20, 0.5, 0.3, 0.5, 0);
            sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), b.x, b.y + 1, b.z, 40, 0.6, 1.5, 0.6, 0.08);
            sl.playSound(null, b.x, b.y, b.z, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 4f, 1.2f);
            if (v instanceof LauncherVehicle lv) lv.setLoadedMask(0L);
            s.setBroken(AMMO, true);
            ignite(v, 140);
            notify(v, "hud.aiarsenal.rw.dmg.blowout");
            return;
        }
        double tnt = Math.min(45.0, 4.0 + n * pr[1] * 0.3);
        kill(sl, v, false);
        s.setBroken(AMMO, true);
        if (v instanceof LauncherVehicle lv) lv.setLoadedMask(0L);
        ConventionalBlast.explode(sl, c, tnt, 0.5);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        if (pr[0] > 0 && g.turret && n > 0 && !s.broken(TURRET_GONE)) tossTurret(sl, v, g, pr, n);
    }

    private static void tossTurret(ServerLevel sl, VehicleEntity v, Geo g, float[] pr, int n) {
        RandomSource r = sl.random;
        double up = Mth.clamp(3.0 + 2.6 * Math.sqrt(n * pr[1] / pr[0]), 4.0, 24.0) * (0.85 + 0.3 * r.nextDouble());
        float[] ang = turretAngles(v);
        String pose = "launcher_yaw:y:" + (float) Math.toRadians(ang[0]) + ";launcher_pitch:x:" + (float) Math.toRadians(-v.kind.fireAxisZ() * ang[1]);
        float[] pivot = {g.yp[0], g.yp[1] + 0.35f, g.yp[2]};
        Vec3 pw = v.localToWorld(pivot[0], pivot[1], pivot[2]);
        double hd = r.nextDouble() * Math.PI * 2, hs = 2.2 + Math.abs(r.nextGaussian()) * 2.0;     // lands beside the hull, not back on it
        Vec3 vel = new Vec3(Math.cos(hd) * hs, up, Math.sin(hd) * hs);
        Vec3 spin = new Vec3(r.nextGaussian() * 1.8, r.nextGaussian() * 2.5, r.nextGaussian() * 1.8);
        float half = Mth.clamp(v.spec.aabbW() * 0.38f, 0.6f, 3f);
        DebrisEntity.spawn(sl, v.spec.model().toString(), "launcher_yaw", DebrisEntity.RIGID, pw, vel, pivot,
                new float[]{v.getYRot(), v.bodyPitch, v.bodyRoll, v.bodyDy}, pose, spin, half, 2400, 500, v.getId());
        v.dmg.setBroken(TURRET_GONE, true);
        sl.playSound(null, pw.x, pw.y, pw.z, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 6f, 0.5f);
    }

    private static void spawnTrack(ServerLevel sl, VehicleEntity v, String side) {
        int i = "L".equals(side) ? 0 : 1;
        Entity old = v.dmg.trackDebris[i] >= 0 ? sl.getEntity(v.dmg.trackDebris[i]) : null;
        if (old != null) old.discard();
        Vec3 p = v.position().add(0, v.bodyDy, 0);
        DebrisEntity d = DebrisEntity.spawn(sl, v.spec.model().toString(), "re:.*_link_" + side + "_\\d+", DebrisEntity.TRACK, p, Vec3.ZERO,
                new float[]{0, 0, 0}, new float[]{v.getYRot(), v.bodyPitch, v.bodyRoll, 0f}, side, Vec3.ZERO, 0.5f, 2400, 0, v.getId());
        v.dmg.trackDebris[i] = d.getId();
    }

    /** Hull destroyed: crew bails out / is hurt, the hull burns and stays as a dark wreck for 90 s. */
    public static void kill(ServerLevel sl, VehicleEntity v, boolean cookOff) {
        DamageState s = v.dmg;
        if (s.dead()) return;
        s.setBroken(DEAD, true);
        s.deadTicks = 0;
        s.repairMod = -1;
        Crew.killAll(v);
        ignite(v, 1200);
        v.setHp(0f);
        v.setEngine(false);
        v.speedMs = 0;
        for (Entity e : v.getPassengers()) {
            e.invulnerableTime = 0;
            e.hurt(sl.damageSources().generic(), params(v.kind)[2] > 0.5f ? 4f : 10f);
        }
        v.ejectPassengers();
        Vec3 c = v.position().add(0, v.spec.height() * 0.5, 0);
        sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, 0.8, 0.5, 0.8, 0);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 3f, 0.8f);
        if (cookOff && rounds(v) > 0 && sl.random.nextFloat() < 0.5f) detonate(sl, v);
    }

    public static boolean handlesDeath(VehicleEntity v) {
        return v instanceof GunVehicle gv && gv.cfg.tracked();
    }

    // ------------------------------------------------------------------------------------------ tick

    private static final int[] REPAIR_ORDER = {TRACK_L, TRACK_R, ENGINE, TURRET, BARREL, AMMO};

    static int repairTicks(int m, boolean brokenNotHurt) {
        int t = switch (m) {
            case TRACK_L, TRACK_R -> 300;
            case ENGINE -> 400;
            case BARREL -> 500;
            case TURRET -> 240;
            default -> 200;
        };
        return brokenNotHurt ? t : t / 2;
    }

    public static void tick(VehicleEntity v) {
        if (!(v.level() instanceof ServerLevel sl)) return;
        DamageState s = v.dmg;
        if (s.dead()) {
            s.deadTicks++;
            if (s.deadTicks == 1200) {
                s.setBroken(FIRE, false);
                s.fireTicks = 0;
            }
            if (s.deadTicks >= 1800) {
                Vec3 c = v.position().add(0, 1, 0);
                for (int i = 0; i < 4 + sl.random.nextInt(4); i++) {
                    net.minecraft.world.entity.item.ItemEntity it = new net.minecraft.world.entity.item.ItemEntity(sl, c.x, c.y, c.z, new ItemStack(ModItems.STEEL_PLATE.get()));
                    sl.addFreshEntity(it);
                }
                for (int i = 0; i < 2; i++) {
                    Entity e = s.trackDebris[i] >= 0 ? sl.getEntity(s.trackDebris[i]) : null;
                    if (e != null) e.discard();
                }
                v.discard();
            }
        } else {
            if (s.burning()) {
                s.fireTicks--;
                float nh = v.hp() - v.kind.hp * 0.0012f;
                if (nh <= 0f && handlesDeath(v)) {
                    kill(sl, v, true);
                    return;
                }
                v.setHp(Math.max(1f, nh));
                if (rounds(v) > 0 && handlesDeath(v) && v.tickCount % 20 == 0 && sl.random.nextFloat() < 0.05f) {
                    detonate(sl, v);
                    return;
                }
                if (s.fireTicks <= 0) {
                    s.setBroken(FIRE, false);
                    sl.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 1.5f, 0.8f);
                    notify(v, "hud.aiarsenal.rw.dmg.fire_out");
                }
            }
            Crew.tick(sl, v);
            if (!s.dead()) repair(sl, v, s);
        }
        if (s.dirtyNow || (s.dirty && v.tickCount % 10 == 0)) v.publishDamage();
    }

    private static void repair(ServerLevel sl, VehicleEntity v, DamageState s) {
        if (s.burning() || Math.abs(v.speedMs) > 0.3) return;
        if (s.repairMod < 0) {
            for (int m : REPAIR_ORDER) {
                if (s.broken(m) && m != AMMO || s.hurtBit(m)) {
                    s.repairMod = m;
                    s.repairTotal = s.repairTicks = repairTicks(m, s.broken(m));
                    s.dirtyNow = true;
                    break;
                }
            }
            if (s.repairMod < 0) return;
        }
        if (--s.repairTicks % 20 == 0) {
            s.dirty = true;
            sl.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.35f, 1.4f + sl.random.nextFloat() * 0.3f);
        }
        if (s.repairTicks <= 0) {
            int m = s.repairMod;
            s.setBroken(m, false);
            s.setHurt(m, false);
            s.mdmg[m] = 0;
            s.repairMod = -1;
            if (m == TRACK_L || m == TRACK_R) {
                int i = m == TRACK_L ? 0 : 1;
                Entity e = s.trackDebris[i] >= 0 ? sl.getEntity(s.trackDebris[i]) : null;
                if (e != null) e.discard();
                s.trackDebris[i] = -1;
            }
            notifyMod(v, "repaired", m);
            sl.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.NEUTRAL, 1f, 1f);
        }
    }

    // ------------------------------------------------------------------------------------------ queries

    public static boolean canDrive(VehicleEntity v) {
        DamageState s = v.dmg;
        return !s.dead() && !s.broken(ENGINE) && !s.broken(TRACK_L) && !s.broken(TRACK_R) && Crew.ok(v, Crew.DRIVER);
    }

    public static double powerFactor(VehicleEntity v) {
        return v.dmg.hurtBit(ENGINE) ? 0.5 : 1.0;
    }

    public static boolean canTraverse(VehicleEntity v) {
        return !v.dmg.dead() && !v.dmg.broken(TURRET) && !v.dmg.broken(TURRET_GONE) && Crew.ok(v, Crew.GUNNER);
    }

    public static boolean canFireMain(VehicleEntity v) {
        return !v.dmg.dead() && !v.dmg.broken(BARREL) && !v.dmg.broken(TURRET_GONE) && Crew.ok(v, Crew.GUNNER);
    }

    /** r21: reload speed factor (loader out and not yet replaced: half speed). */
    public static boolean slowReload(VehicleEntity v) {
        return !Crew.ok(v, Crew.LOADER);
    }

    // ------------------------------------------------------------------------------------------ holes / util

    private static void addHole(VehicleEntity v, Geo g, Vector3d p, Vector3d d, HitContext.Hit h) {
        if (h.kind() == HitContext.Kind.BLAST || h.kind() == HitContext.Kind.PROX) return;
        // entry point on the hull box along the shot line (or the nearest face for HE)
        double[] lo = {g.cx - g.hx, Math.max(0.15, g.cy - g.hy), g.cz - g.hz}, hi = {g.cx + g.hx, g.cy + g.hy, g.cz + g.hz};
        double[] o = {p.x, p.y, p.z}, dir = {d.x, d.y, d.z};
        double tIn = -1e9, tOut = 1e9;
        int face = -1;
        boolean ok = d.lengthSquared() > 1e-6;
        for (int a = 0; a < 3 && ok; a++) {
            if (Math.abs(dir[a]) < 1e-9) {
                if (o[a] < lo[a] || o[a] > hi[a]) ok = false;
                continue;
            }
            double t1 = (lo[a] - o[a]) / dir[a], t2 = (hi[a] - o[a]) / dir[a];
            double tn = Math.min(t1, t2), tf = Math.max(t1, t2);
            if (tn > tIn) {
                tIn = tn;
                face = a * 2 + (t1 < t2 ? 0 : 1);
            }
            tOut = Math.min(tOut, tf);
        }
        double[] e = new double[3];
        if (ok && tIn <= tOut && face >= 0) {
            for (int a = 0; a < 3; a++) e[a] = o[a] + dir[a] * tIn;
        } else {
            // nearest face to the point
            double best = 1e9;
            for (int a = 0; a < 3; a++) {
                for (int sgn = 0; sgn < 2; sgn++) {
                    double dd = Math.abs(o[a] - (sgn == 0 ? lo[a] : hi[a]));
                    if (dd < best && !(a == 1 && sgn == 0)) {
                        best = dd;
                        face = a * 2 + sgn;
                    }
                }
            }
            for (int a = 0; a < 3; a++) e[a] = Mth.clamp(o[a], lo[a], hi[a]);
            e[face / 2] = face % 2 == 0 ? lo[face / 2] : hi[face / 2];
        }
        if (face == 2) return;                                     // belly
        float[] n = new float[3];
        n[face / 2] = face % 2 == 0 ? -1 : 1;
        float r = h.kind() == HitContext.Kind.AP ? 0.09f : (float) Mth.clamp(0.1 + 0.07 * Math.cbrt(h.power()), 0.1, 0.35);
        float off = 0.02f;
        DamageState.Hole hole = new DamageState.Hole((float) e[0] + n[0] * off, (float) e[1] + n[1] * off, (float) e[2] + n[2] * off, n[0], n[1], n[2], r, 0);
        v.dmg.holes.add(hole);
        while (v.dmg.holes.size() > 14) v.dmg.holes.remove(0);
    }

    /** Display name of a module ("left track", "turret B", "15 cm mount 3"...). */
    public static Component modName(Entity v, int m) {
        if (m >= GUN0 && m < GUN0 + N_GUNS) {
            int i = m - GUN0;
            if (v instanceof ru.aiforge.realwar.craft.BattleshipEntity) {
                if (i < 4) return Component.translatable("hud.aiarsenal.rw.mod.main", String.valueOf((char) ('A' + i)));
                return Component.translatable("hud.aiarsenal.rw.mod.sec", i - 3);
            }
            return Component.translatable("hud.aiarsenal.rw.mod.gun");
        }
        return Component.translatable("hud.aiarsenal.rw.mod." + m);
    }

    public static void notifyMod(Entity v, String what, int m) {
        Component c = Component.translatable("hud.aiarsenal.rw.dmg." + what, modName(v, m));
        for (Entity e : v.getPassengers()) if (e instanceof Player p) p.displayClientMessage(c, true);
    }

    public static void notify(Entity v, String key) {
        for (Entity e : v.getPassengers()) if (e instanceof Player p) p.displayClientMessage(Component.translatable(key), true);
    }
}
