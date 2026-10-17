package ru.aiforge.realwar.damage;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.craft.BattleshipEntity;
import ru.aiforge.realwar.craft.BoatEntity;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.craft.CraftKind;
import ru.aiforge.realwar.craft.HeliEntity;
import ru.aiforge.realwar.craft.PlaneEntity;

import static ru.aiforge.realwar.damage.DamageState.*;

/**
 * r18 module damage of aircraft and ships.
 * Helicopters: main rotor (blades fly off, no lift), tail rotor (uncontrolled yaw), tail boom, engine, fire.
 * Aeroplanes: left / right wing (shot off: half the lift and a violent roll to that side), tail (pitch / yaw authority),
 * engine (smoke, thrust x0.55, dead), fire. Ships: gun mounts (barrel blown off, turret jammed), engine room, fire and
 * hull holes - holes under the waterline flood the hull (the flood water mass pulls the ship down and lists it towards
 * the holes) until damage control patches them and the pumps empty the compartments.
 */
public final class CraftDamage {
    private CraftDamage() {
    }

    // ------------------------------------------------------------------------------------------ geometry

    /** Wing split plane (|x|), aileron hinge (x, y, z), fuselage half width, engine nacelle |x| list. */
    record PlaneGeo(float xcut, float ax, float ay, float az, float fus, float[] nacelles, float chord) {}

    static PlaneGeo plane(CraftKind k) {
        return switch (k) {
            case BF109 -> new PlaneGeo(2.6f, 3.62f, 1.59f, -0.61f, 0.55f, new float[0], 1.5f);
            case YAK3 -> new PlaneGeo(2.55f, 3.46f, 1.50f, -0.85f, 0.55f, new float[0], 1.6f);
            case HE111 -> new PlaneGeo(5.6f, 8.14f, 2.60f, -1.11f, 1.0f, new float[]{3.15f}, 3.0f);
            case PE8 -> new PlaneGeo(10.5f, 14.73f, 3.16f, -1.08f, 1.4f, new float[]{4.75f, 10.1f}, 3.6f);
            case SU57 -> new PlaneGeo(2.1f, 5.72f, 1.83f, -5.25f, 1.3f, new float[0], 5.0f);
            default -> new PlaneGeo(2.0f, 3.5f, 1.5f, -0.6f, 0.6f, new float[0], 1.5f);
        };
    }

    static float scale(CraftEntity c) {
        if (c.kind.isPlane()) return (float) Mth.clamp(Math.sqrt(c.kind.hp / 150.0), 0.7, 3.2);
        if (c.kind.isHeli()) return (float) Mth.clamp(Math.sqrt(c.kind.hp / 300.0), 0.7, 2.0);
        return (float) Mth.clamp(Math.sqrt(c.kind.hp / 300.0), 0.7, 6.5);
    }

    static float maxHp(CraftEntity c, int m) {
        float s = scale(c);
        if (c.kind.isPlane()) return switch (m) {
            case WING_L, WING_R -> 50 * s;
            case TAIL -> 45 * s;
            default -> 35 * s;
        };
        if (c.kind.isHeli()) return switch (m) {
            case ROTOR -> 55 * s;
            case TAIL_ROTOR -> 25 * s;
            case TAIL -> 60 * s;
            default -> 45 * s;
        };
        if (c instanceof BattleshipEntity) {
            if (m == ENGINE) return 900;
            return m - GUN0 < BattleshipEntity.N_MAIN ? 170 : 80;
        }
        return m == ENGINE ? 40 * s : 35 * s;
    }

    public static Vec3 enginePoint(CraftEntity c) {
        CraftKind k = c.kind;
        if (k.isHeli()) return new Vec3(0, k.heli.hubY() - 0.9, -0.6);
        if (k.isPlane()) {
            PlaneGeo g = plane(k);
            if (g.nacelles.length > 0) return new Vec3(g.nacelles[0], g.ay, g.az + g.chord);
            if (k.plane.jet()) return new Vec3(0, g.ay, -0.42 * k.plane.length());
            return new Vec3(0, g.ay + 0.2, 0.33 * k.plane.length());
        }
        if (c instanceof BattleshipEntity) return new Vec3(0, 28, -3);
        CraftKind.Water w = k.water;
        return new Vec3(0, w.hullH() * 0.95, -0.3 * w.length());
    }

    public static Vec3 firePoint(CraftEntity c) {
        if (c instanceof BattleshipEntity) return new Vec3(0, 21, 12);
        if (c.kind.water != null) return new Vec3(0, c.kind.water.hullH(), 0.1 * c.kind.water.length());
        return enginePoint(c);
    }

    public static Vec3 wingRoot(CraftEntity c, boolean left) {
        PlaneGeo g = plane(c.kind);
        return new Vec3(left ? g.xcut : -g.xcut, g.ay, g.az + g.chord * 0.5);
    }

    static Vec3 wingPivot(CraftEntity c, boolean left) {
        PlaneGeo g = plane(c.kind);
        float x = (g.xcut + c.kind.plane.span() * 0.5f) * 0.5f;
        return new Vec3(left ? x : -x, g.ay, g.az + g.chord * 0.45);
    }

    /** Mount position (model frame) of gun module i, or null. */
    @Nullable
    public static Vec3 gunPoint(CraftEntity c, int i) {
        if (c instanceof BattleshipEntity) {
            if (i < BattleshipEntity.N_MAIN) {
                BattleshipEntity.Mount m = BattleshipEntity.M[i];
                return new Vec3(m.x(), m.y() + 2.5, m.z());
            }
            if (i - BattleshipEntity.N_MAIN < BattleshipEntity.N_SEC) {
                BattleshipEntity.Mount m = BattleshipEntity.M[BattleshipEntity.I_SEC + i - BattleshipEntity.N_MAIN];
                return new Vec3(m.x(), m.y() + 1.2, m.z());
            }
            return null;
        }
        if (i == 0 && c.kind == CraftKind.CB90) return new Vec3(0, 3.0, -0.6);
        if (i == 0 && c.kind == CraftKind.RAPTOR) return new Vec3(0, 3.4, 3.6);
        return null;
    }

    static int gunCount(CraftEntity c) {
        if (c instanceof BattleshipEntity) return BattleshipEntity.N_MAIN + BattleshipEntity.N_SEC;
        return c.kind == CraftKind.CB90 || c.kind == CraftKind.RAPTOR ? 1 : 0;
    }

    static Vec3 toLocal(CraftEntity c, Vec3 w) {
        Vec3[] b = c.basis();
        double py = c.pivotY();
        Vec3 d = w.subtract(c.getX(), c.getY() + py, c.getZ());
        return new Vec3(-d.dot(b[2]), d.dot(b[1]) + py, d.dot(b[0]));
    }

    static Vec3 dirLocal(CraftEntity c, Vec3 d) {
        Vec3[] b = c.basis();
        return new Vec3(-d.dot(b[2]), d.dot(b[1]), d.dot(b[0]));
    }

    /** Module at a model-frame point, or -1. */
    static int zone(CraftEntity c, double x, double y, double z) {
        CraftKind k = c.kind;
        DamageState s = c.dmg;
        if (k.isPlane()) {
            PlaneGeo g = plane(k);
            double L = k.plane.length(), hs = k.plane.span() * 0.5;
            if (Math.abs(x) > hs + 0.3 || Math.abs(z) > L * 0.6 || Math.abs(y - g.ay) > 4) return -1;
            if (z < -0.3 * L && Math.abs(x) < Math.max(hs * 0.3, 2.6) && !(k.plane.jet() && z > -0.36 * L && Math.abs(x) < 1.6)) return TAIL;
            if (Math.abs(x) > g.fus) {
                for (float nx : g.nacelles) if (Math.abs(Math.abs(x) - nx) < 0.9 && z > g.az - 0.5) return ENGINE;
                if (z > g.az - 0.6 && z < g.az + g.chord + 0.8 && Math.abs(y - g.ay) < 1.0) {
                    boolean left = x > 0;
                    if (s.broken(left ? WING_L : WING_R) && Math.abs(x) > g.xcut) return -1;
                    return left ? WING_L : WING_R;
                }
                return -1;
            }
            if (g.nacelles.length == 0) {
                if (k.plane.jet() ? z < -0.05 * L && z > -0.4 * L : z > 0.2 * L) return ENGINE;
            }
            return -1;
        }
        if (k.isHeli()) {
            CraftKind.Heli h = k.heli;
            if (y > h.hubY() - 0.45 && x * x + z * z < h.rotorR() * h.rotorR() && y < Math.max(h.hubY(), h.hub2Y()) + 0.6) return s.broken(ROTOR) ? -1 : ROTOR;
            if (!h.coaxial() && h.tailR() > 0) {
                double dx = x - 0.45, dy = y - 3.0, dz = z - h.tailZ();
                if (dx * dx + dy * dy + dz * dz < (h.tailR() + 0.3) * (h.tailR() + 0.3)) return s.broken(TAIL_ROTOR) ? -1 : TAIL_ROTOR;
            }
            if (z < -0.6 * h.rotorR() && Math.abs(x) < 1.0) return TAIL;
            if (Math.abs(x) < 1.4 && y > h.hubY() - 2.0 && y < h.hubY() - 0.35 && z > -2.5 && z < 1.2) return ENGINE;
            return -1;
        }
        // ships: gun mounts first, then the engine room
        for (int i = 0; i < gunCount(c); i++) {
            Vec3 gp = gunPoint(c, i);
            if (gp == null) continue;
            double r = c instanceof BattleshipEntity ? (i < BattleshipEntity.N_MAIN ? 6.5 : 3.0) : 1.4;
            if (gp.distanceToSqr(x, y, z) < r * r) return GUN0 + i;
        }
        CraftKind.Water w = k.water;
        if (c instanceof BattleshipEntity) {
            if (z > -60 && z < -5 && Math.abs(x) < 12 && y > 1 && y < 10) return ENGINE;
        } else if (z < -0.22 * w.length() && z > -0.48 * w.length() && Math.abs(x) < w.beam() * 0.5 && y < w.hullH() * 0.8) {
            return ENGINE;
        }
        return -1;
    }

    /** Representative module points for proximity bursts. */
    static double[][] points(CraftEntity c) {
        CraftKind k = c.kind;
        if (k.isPlane()) {
            Vec3 l = wingPivot(c, true), r = wingPivot(c, false), e = enginePoint(c);
            PlaneGeo g = plane(k);
            return new double[][]{{WING_L, l.x, l.y, l.z}, {WING_R, r.x, r.y, r.z}, {TAIL, 0, g.ay + 0.5, -0.42 * k.plane.length()}, {ENGINE, e.x, e.y, e.z}};
        }
        if (k.isHeli()) {
            CraftKind.Heli h = k.heli;
            java.util.List<double[]> l = new java.util.ArrayList<>();
            l.add(new double[]{ROTOR, 0, h.hubY(), 0});
            l.add(new double[]{ROTOR, h.rotorR() * 0.6, h.hubY(), 0});
            l.add(new double[]{ROTOR, -h.rotorR() * 0.6, h.hubY(), 0});
            l.add(new double[]{ENGINE, 0, h.hubY() - 0.9, -0.6});
            l.add(new double[]{TAIL, 0, 2.0, -0.8 * h.rotorR()});
            if (!h.coaxial() && h.tailR() > 0) l.add(new double[]{TAIL_ROTOR, 0.45, 3.0, h.tailZ()});
            return l.toArray(new double[0][]);
        }
        return new double[0][];
    }

    // ------------------------------------------------------------------------------------------ hits

    public static void onHurt(CraftEntity c, DamageSource src, float amount) {
        if (!(c.level() instanceof ServerLevel sl) || c.dmg.dead()) return;
        HitContext.Hit h = HitContext.get();
        String id = src.getMsgId();
        RandomSource rnd = sl.random;
        if (h == null) {
            boolean bullet = id.contains("bullet");
            if (!bullet && !(id.contains("ordnance") || id.contains("blast") || src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION))) return;
            Vec3 cpt = c.localToWorld((rnd.nextDouble() - 0.5) * c.kind.width, c.kind.height * (0.2 + 0.6 * rnd.nextDouble()), (rnd.nextDouble() - 0.5) * c.kind.width * 1.5);
            h = bullet ? new HitContext.Hit(cpt, Vec3.ZERO, HitContext.Kind.BULLET, amount)
                    : new HitContext.Hit(cpt, Vec3.ZERO, HitContext.Kind.HE, amount / 8.0);
        }
        Vec3 p = toLocal(c, h.pos());
        Vec3 d = h.dir().lengthSqr() > 1e-6 ? dirLocal(c, h.dir()).normalize() : Vec3.ZERO;
        boolean air = c.kind.isAir();
        switch (h.kind()) {
            case BULLET -> {
                if (!air && !(c.kind.water != null && c.kind.water.length() < 30)) return;
                int m = march(c, p, d, 1)[0];
                if (m < 0 && air && rnd.nextFloat() < 0.35f) {
                    int[] pool = c.kind.isPlane() ? new int[]{p.x > 0 ? WING_L : WING_R, ENGINE, TAIL} : new int[]{ENGINE, TAIL, ROTOR};
                    m = pool[rnd.nextInt(pool.length)];
                }
                if (m >= 0) module(sl, c, m, amount * 0.4f);
                if (m < 0 || rnd.nextFloat() < 0.04f) pilotHit(sl, c, 2f);
            }
            case AP -> {
                double budget = Math.max(amount, h.power());
                int[] ms = march(c, p, d, 2);
                for (int m : ms) {
                    if (m < 0) continue;
                    module(sl, c, m, (float) budget);
                    budget *= 0.6;
                }
            }
            case HE -> {
                double tnt = Math.max(0.2, h.power());
                double R = 0.8 + 0.4 * Math.cbrt(tnt);
                splash(sl, c, p, R, amount);
            }
            case PROX -> prox(sl, c, p, h.power());
            case BLAST -> {
            }
        }
        if (!air && (h.kind() == HitContext.Kind.AP || h.kind() == HitContext.Kind.HE)) addHole(c, p, d, h);
        if (!air && (h.kind() == HitContext.Kind.HE && h.power() > 3 || h.kind() == HitContext.Kind.AP) && rnd.nextFloat() < 0.18f) ignite(c, 400);
        c.dmg.dirtyNow = true;
    }

    /** Modules along a shot line through the craft (first n distinct). */
    static int[] march(CraftEntity c, Vec3 p, Vec3 d, int n) {
        int[] out = new int[n];
        java.util.Arrays.fill(out, -1);
        if (d.lengthSqr() < 1e-6) {
            out[0] = zone(c, p.x, p.y, p.z);
            return out;
        }
        int k = 0;
        double span = c.kind.water != null ? 40 : 14;
        for (double t = -span * 0.5; t < span && k < n; t += 0.1) {
            int m = zone(c, p.x + d.x * t, p.y + d.y * t, p.z + d.z * t);
            if (m < 0) continue;
            boolean dup = false;
            for (int j = 0; j < k; j++) if (out[j] == m) dup = true;
            if (!dup) out[k++] = m;
        }
        return out;
    }

    private static void splash(ServerLevel sl, CraftEntity c, Vec3 p, double R, float amount) {
        float[] best = new float[N];
        int m0 = zone(c, p.x, p.y, p.z);
        if (m0 >= 0) best[m0] = 1f;
        for (double f : new double[]{0.25, 0.55, 0.85}) {
            for (int a = 0; a < 26; a++) {
                double th = a * 2.399963, yy = 1 - 2 * (a + 0.5) / 26.0, rr = Math.sqrt(1 - yy * yy);
                double x = p.x + Math.cos(th) * rr * R * f, y = p.y + yy * R * f, z = p.z + Math.sin(th) * rr * R * f;
                int m = zone(c, x, y, z);
                if (m >= 0) best[m] = Math.max(best[m], (float) (1 - f));
            }
        }
        for (int m = 0; m < N; m++) if (best[m] > 0) module(sl, c, m, amount * best[m]);
    }

    private static void prox(ServerLevel sl, CraftEntity c, Vec3 p, double power) {
        for (double[] q : points(c)) {
            double dd = p.distanceTo(new Vec3(q[1], q[2], q[3]));
            double f = Math.max(0, 1 - dd / 9.0);
            if (f > 0) module(sl, c, (int) q[0], (float) (power * f * f * 0.9));
        }
        if (p.length() < 6) pilotHit(sl, c, (float) (power * 0.03));
    }

    /** Near miss of a conventional blast: rotor blades / wings / tail of aircraft, mounts of small boats. */
    public static void blastNear(CraftEntity c, Vec3 centre, double tnt, double dist) {
        if (!(c.level() instanceof ServerLevel sl) || c.dmg.dead()) return;
        double R = 1.5 + 1.0 * Math.cbrt(tnt);
        Vec3 p = toLocal(c, centre);
        double base = 20.0 * Math.cbrt(tnt);
        for (double[] q : points(c)) {
            double dd = p.distanceTo(new Vec3(q[1], q[2], q[3]));
            double f = Math.max(0, 1 - dd / R);
            if (f > 0) module(sl, c, (int) q[0], (float) (base * f));
        }
    }

    private static void pilotHit(ServerLevel sl, CraftEntity c, float d) {
        if (d < 0.5f) return;
        for (Entity e : c.getPassengers()) {
            if (sl.random.nextFloat() < 0.3f) {
                e.invulnerableTime = 0;
                e.hurt(sl.damageSources().generic(), d);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ modules

    static void module(ServerLevel sl, CraftEntity c, int m, float d) {
        DamageState s = c.dmg;
        if (d <= 0.3f || s.broken(m) || s.dead()) return;
        float max = maxHp(c, m);
        s.mdmg[m] += d;
        if (s.mdmg[m] >= max) {
            s.setBroken(m, true);
            s.setHurt(m, false);
            if (s.repairMod == m) s.repairMod = -1;
            broken(sl, c, m);
        } else if (s.mdmg[m] >= max * 0.35 && !s.hurtBit(m)) {
            s.setHurt(m, true);
            VehicleDamage.notifyMod(c, "hurt", m);
        }
    }

    private static float[] frame(CraftEntity c) {
        return new float[]{c.getYRot(), c.pitch, c.roll, 0f};
    }

    private static String model(CraftEntity c) {
        return "aiarsenal:" + c.kind.model;
    }

    private static Vec3 velMs(CraftEntity c) {
        return c.getDeltaMovement().scale(20.0);
    }

    private static void broken(ServerLevel sl, CraftEntity c, int m) {
        RandomSource r = sl.random;
        Vec3 pos = c.position();
        VehicleDamage.notifyMod(c, "broken", m);
        Vec3[] b = c.basis();
        switch (m) {
            case WING_L, WING_R -> {
                boolean left = m == WING_L;
                String sd = left ? "L" : "R";
                Vec3 pv = wingPivot(c, left);
                Vec3 out = b[2].scale(left ? -1 : 1);                    // basis[2] = right
                Vec3 vel = velMs(c).scale(0.85).add(out.scale(4)).add(0, 2, 0);
                Vec3 spin = b[0].scale(left ? -4 : 4).add(b[1].scale(r.nextGaussian() * 1.5));
                float half = Mth.clamp((c.kind.plane.span() * 0.5f - plane(c.kind).xcut) * 0.35f, 0.6f, 4f);
                DebrisEntity.spawn(sl, model(c), "wing_" + sd + ",ail_" + sd, DebrisEntity.RIGID, c.localToWorld(pv.x, pv.y, pv.z), vel,
                        new float[]{(float) pv.x, (float) pv.y, (float) pv.z}, frame(c), "", spin, half, 1200, 200, c.getId());
                sl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ANVIL_BREAK, SoundSource.NEUTRAL, 4f, 0.5f);
                Vec3 root = c.localToWorld(wingRoot(c, left).x, wingRoot(c, left).y, wingRoot(c, left).z);
                sl.sendParticles(ParticleTypes.EXPLOSION, root.x, root.y, root.z, 1, 0, 0, 0, 0);
                if (r.nextFloat() < 0.5f) ignite(c, 300);
            }
            case ROTOR -> {
                HeliEntity h = (HeliEntity) c;
                CraftKind.Heli hk = c.kind.heli;
                boolean ka = c.kind == CraftKind.KA52;
                float rot = (float) Math.toRadians(c.rotorAngle);
                String nodes = ka ? "rotor_upper,rotor_lower" : "rotor_main";
                String pose = ka ? "rotor_upper:y:" + rot + ";rotor_lower:y:" + (-rot) : "rotor_main:y:" + rot;
                float hy = ka ? (hk.hubY() + hk.hub2Y()) * 0.5f : hk.hubY();
                Vec3 vel = velMs(c).add(b[1].scale(5)).add(r.nextGaussian() * 4, 2, r.nextGaussian() * 4);
                Vec3 spin = b[1].scale(26).add(r.nextGaussian(), 0, r.nextGaussian());
                DebrisEntity.spawn(sl, model(c), nodes, DebrisEntity.ROTOR, c.localToWorld(0, hy, 0), vel, new float[]{0, hy, 0}, frame(c), pose, spin,
                        Mth.clamp(hk.rotorR() * 0.45f, 1f, 4f), 1200, 0, c.getId());
                sl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL, 4f, 0.4f);
                sl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ANVIL_BREAK, SoundSource.NEUTRAL, 4f, 0.6f);
                h.aux = 0;
            }
            case TAIL_ROTOR -> {
                CraftKind.Heli hk = c.kind.heli;
                Vec3 tp = new Vec3(0.45, 3.0, hk.tailZ());
                Vec3 vel = velMs(c).add(b[2].scale(-3)).add(0, 2, 0);
                DebrisEntity.spawn(sl, model(c), "rotor_tail", DebrisEntity.ROTOR, c.localToWorld(tp.x, tp.y, tp.z), vel,
                        new float[]{(float) tp.x, (float) tp.y, (float) tp.z}, frame(c), "", b[2].scale(30), 0.8f, 900, 0, c.getId());
                sl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL, 3f, 0.6f);
            }
            case ENGINE -> {
                sl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.NEUTRAL, 3f, 0.5f);
                if (r.nextFloat() < (c.kind.isAir() ? 0.45f : 0.3f)) ignite(c, c.kind.isAir() ? 400 : 500);
            }
            case TAIL -> sl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ANVIL_BREAK, SoundSource.NEUTRAL, 3f, 0.7f);
            default -> {
                if (m >= GUN0 && m < GUN0 + N_GUNS) gunLost(sl, c, m - GUN0);
            }
        }
    }

    private static void gunLost(ServerLevel sl, CraftEntity c, int i) {
        RandomSource r = sl.random;
        Vec3 vel = new Vec3(r.nextGaussian() * 3, 9 + r.nextDouble() * 5, r.nextGaussian() * 3).add(velMs(c));
        Vec3 spin = new Vec3(r.nextGaussian() * 1.2, r.nextGaussian() * 0.6, r.nextGaussian() * 1.2);
        if (c instanceof BattleshipEntity bs) {
            int mi = i < BattleshipEntity.N_MAIN ? i : BattleshipEntity.I_SEC + i - BattleshipEntity.N_MAIN;
            BattleshipEntity.Mount m = BattleshipEntity.M[mi];
            float az = bs.az[mi], el = bs.el[mi];
            String pose = m.tag() + "_yaw:y:" + (float) Math.toRadians(az - m.rest()) + ";" + m.tag() + "_pitch:x:" + (float) Math.toRadians(m.aft() ? el : -el);
            if (i < BattleshipEntity.N_MAIN) {
                int g = r.nextInt(2);
                Vec3 pv = BattleshipEntity.muzzleLocal(mi, g, az, el, m.len() * 0.5);
                DebrisEntity.spawn(sl, model(c), m.tag() + "_g" + g, DebrisEntity.RIGID, c.localToWorld(pv.x, pv.y, pv.z), vel,
                        new float[]{(float) pv.x, (float) pv.y, (float) pv.z}, frame(c), pose, spin, 1.6f, 1200, 300, c.getId());
                c.dmg.lost |= 1 << (i * 2 + g);
            } else {
                Vec3 pv = new Vec3(m.px(), m.py(), m.pz());
                DebrisEntity.spawn(sl, model(c), m.tag() + "_pitch", DebrisEntity.RIGID, c.localToWorld(pv.x, pv.y, pv.z), vel,
                        new float[]{(float) pv.x, (float) pv.y, (float) pv.z}, frame(c), pose, spin, 1.2f, 1200, 300, c.getId());
                c.dmg.lost |= 1 << (8 + i - BattleshipEntity.N_MAIN);
            }
        } else {
            Vec3 pv = gunPoint(c, 0);
            if (pv == null) return;
            String pose = "turret_yaw:y:" + (float) Math.toRadians(c.gunYaw()) + ";turret_pitch:x:" + (float) -Math.toRadians(c.gunPitch());
            DebrisEntity.spawn(sl, model(c), "turret_pitch", DebrisEntity.RIGID, c.localToWorld(pv.x, pv.y, pv.z), vel.scale(0.6),
                    new float[]{(float) pv.x, (float) pv.y, (float) pv.z}, frame(c), pose, spin, 0.6f, 900, 200, c.getId());
            c.dmg.lost |= 1;
        }
        Vec3 w = c.localToWorld(gunPoint(c, i).x, gunPoint(c, i).y, gunPoint(c, i).z);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, w.x, w.y, w.z, 1, 0, 0, 0, 0);
        sl.playSound(null, w.x, w.y, w.z, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 8f, 0.6f);
    }

    public static void ignite(CraftEntity c, int ticks) {
        DamageState s = c.dmg;
        if (!s.burning()) VehicleDamage.notify(c, "hud.aiarsenal.rw.dmg.fire");
        s.setBroken(FIRE, true);
        s.fireTicks = Math.max(s.fireTicks, ticks);
    }

    private static void addHole(CraftEntity c, Vec3 p, Vec3 d, HitContext.Hit h) {
        CraftKind.Water w = c.kind.water;
        if (w == null) return;
        double hb = w.beam() * 0.5, hl = w.length() * 0.5;
        // walk back along the shot line to the hull side (beam tapers towards bow and stern)
        Vec3 e = p;
        if (d.lengthSqr() > 1e-6) {
            for (double t = -hb * 2.5; t < hb * 2.5; t += 0.1) {
                Vec3 q = p.add(d.scale(t));
                if (insideHull(w, q)) {
                    e = q;
                    break;
                }
            }
        }
        double z = Mth.clamp(e.z, -hl * 0.95, hl * 0.95);
        double bz = beamAt(w, z);
        double y = Mth.clamp(e.y, 0.3, w.hullH() - 0.2);
        boolean deck = e.y > w.hullH() - 0.6 && Math.abs(e.x) < bz - 0.6;
        float r = h.kind() == HitContext.Kind.AP ? (float) Mth.clamp(0.12 + h.power() / 700.0, 0.12, 0.6)
                : (float) Mth.clamp(0.12 + 0.11 * Math.cbrt(h.power()), 0.12, 1.4);
        if (w.length() < 30) r = Math.min(r, 0.35f);
        int patch = c instanceof BattleshipEntity ? 600 : 300;
        DamageState.Hole hole;
        if (deck) {
            hole = new DamageState.Hole((float) e.x, w.hullH() + 0.03f, (float) z, 0, 1, 0, r, 0);
        } else {
            float side = e.x >= 0 ? 1f : -1f;
            // outward normal of the tapered side (dx/dz of the beam curve)
            double dz = 0.5;
            double slope = (beamAt(w, z + dz) - beamAt(w, z - dz)) / (2 * dz);
            Vec3 n = new Vec3(side, 0, -slope).normalize();
            hole = new DamageState.Hole((float) (side * (bz + 0.04)), (float) y, (float) z, (float) n.x, 0, (float) n.z, r, patch);
        }
        DamageState s = c.dmg;
        s.holes.add(hole);
        while (s.holes.size() > 24) {
            int rm = 0;
            for (int i = 0; i < s.holes.size(); i++) if (s.holes.get(i).open == 0) { rm = i; break; }
            s.holes.remove(rm);
        }
    }

    static double beamAt(CraftKind.Water w, double z) {
        double u = Math.min(1.0, Math.abs(2 * z / w.length()));
        return w.beam() * 0.5 * Math.sqrt(Math.max(0.05, 1 - Math.pow(u, z > 0 ? 2.2 : 4.0)));
    }

    static boolean insideHull(CraftKind.Water w, Vec3 q) {
        return Math.abs(q.z) < w.length() * 0.5 && q.y > 0 && q.y < w.hullH() && Math.abs(q.x) < beamAt(w, q.z);
    }

    // ------------------------------------------------------------------------------------------ death

    public static boolean handlesDeath(CraftEntity c) {
        return c.kind.water != null || (c.kind.isAir() && !c.onGround() && c.agl > 3);
    }

    public static void kill(CraftEntity c) {
        if (!(c.level() instanceof ServerLevel sl)) return;
        DamageState s = c.dmg;
        if (s.dead()) return;
        s.setBroken(DEAD, true);
        s.deadTicks = 0;
        s.repairMod = -1;
        ignite(c, c.kind.water != null ? 2400 : 1200);
        c.setEngine(false);
        Vec3 p = c.localToWorld(0, c.kind.height * 0.5, 0);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        sl.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 6f, 0.6f);
        VehicleDamage.notify(c, "hud.aiarsenal.rw.dmg.dead");
    }

    // ------------------------------------------------------------------------------------------ tick

    public static void tick(CraftEntity c) {
        if (!(c.level() instanceof ServerLevel sl)) return;
        DamageState s = c.dmg;
        boolean ship = c.kind.water != null;
        if (s.dead()) {
            s.deadTicks++;
            if (c.kind.isAir()) {
                if (c.onGround() || c.isInWater() || s.deadTicks > 700) {
                    c.destroyNow();
                    return;
                }
            } else if (ship) {
                Vec3 deck = c.localToWorld(0, c.kind.water.hullH(), 0);
                boolean awash = c.level().getFluidState(BlockPos.containing(deck)).is(FluidTags.WATER);
                if (awash) {
                    c.ejectPassengers();
                    s.sunkTicks++;
                }
                if (s.sunkTicks > 600 || s.deadTicks > 3600) {
                    c.discard();
                    return;
                }
            }
        }
        if (s.burning()) {
            if (--s.fireTicks <= 0) {
                s.setBroken(FIRE, false);
                sl.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 1.5f, 0.8f);
                if (!s.dead()) VehicleDamage.notify(c, "hud.aiarsenal.rw.dmg.fire_out");
            } else if (!s.dead() && c.tickCount % 5 == 0) {
                float drain = c.kind.hp * (ship ? 0.0004f : 0.004f);
                if (c.hp() - drain <= 0) c.damage(drain);
                else c.setHp(c.hp() - drain);
                if (s.dead() || c.isRemoved()) return;
            }
        }
        if (ship) flood(sl, c, s);
        if (!s.dead()) repair(sl, c, s, ship);
        if (s.dirtyNow || (s.dirty && c.tickCount % 10 == 0)) c.publishDamage();
    }

    private static void flood(ServerLevel sl, CraftEntity c, DamageState s) {
        CraftKind.Water w = c.kind.water;
        double mass = w.mass();
        double k = Math.max(1.0, mass / 4.0e6);
        double in = 0, sx = 0, sz = 0;
        boolean anyUnder = false;
        for (DamageState.Hole h : s.holes) {
            if (h.open <= 0 || h.ny > 0.5f) continue;
            Vec3 wp = c.localToWorld(h.x - h.nx * 0.3, h.y, h.z - h.nz * 0.3);
            if (!c.level().getFluidState(BlockPos.containing(wp)).is(FluidTags.WATER)) continue;
            anyUnder = true;
            double head = Math.max(0.3, w.draft() - h.y + 0.3);
            double q = Math.PI * h.r * h.r * Math.sqrt(2 * 9.81 * head) * 1000.0 * k;
            in += q;
            sx += q * h.x;
            sz += q * h.z;
        }
        if (s.dead()) {
            in += mass * 0.012;
            sz += mass * 0.012 * (c.getId() % 2 == 0 ? 0.25 : -0.25) * w.length();
        }
        float old = s.flood;
        if (in > 0) {
            s.flood = (float) Math.min(mass * 1.6, s.flood + in * 0.05);
            float fx = (float) (sx / in), fz = (float) (sz / in);
            s.floodX += (fx - s.floodX) * 0.05f;
            s.floodZ += (fz - s.floodZ) * 0.05f;
        } else if (s.flood > 0) {
            s.flood = (float) Math.max(0, s.flood - Math.max(mass * 0.004, 400) * 0.05);
        }
        if (Math.abs(s.flood - old) > 1e-4 * mass || (old > 0) != (s.flood > 0)) s.dirty = true;
        // a living ship whose deck stays under water for 10 s founders
        if (!s.dead() && s.flood > 0) {
            Vec3 deck = c.localToWorld(0, w.hullH(), 0);
            if (c.level().getFluidState(BlockPos.containing(deck)).is(FluidTags.WATER) && !c.kind.isSub()) {
                if (++s.sunkTicks > 200) kill(c);
            } else {
                s.sunkTicks = 0;
            }
        }
        if (anyUnder && c.tickCount % 40 == 0) {
            sl.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.BUCKET_FILL, SoundSource.NEUTRAL, 1.5f, 0.6f);
        }
    }

    private static void repair(ServerLevel sl, CraftEntity c, DamageState s, boolean ship) {
        if (!ship) {
            boolean still = c.onGround() && c.getDeltaMovement().horizontalDistanceSqr() < 0.0025;
            if (!still || s.burning()) return;
        }
        // holes first (those under water before the others)
        if (ship && s.repairMod < 0) {
            DamageState.Hole best = null;
            for (DamageState.Hole h : s.holes) {
                if (h.open <= 0) continue;
                if (best == null || h.y < best.y) best = h;
            }
            if (best != null) {
                if (--best.open % 20 == 0) {
                    s.dirty = true;
                    if (best.open % 60 == 0) sl.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6f, 1.2f);
                }
                if (best.open <= 0) {
                    s.dirtyNow = true;
                    VehicleDamage.notify(c, "hud.aiarsenal.rw.dmg.hole_patched");
                }
                return;
            }
        }
        if (s.repairMod < 0) {
            int[] order = c.kind.isPlane() ? new int[]{ENGINE, TAIL, WING_L, WING_R}
                    : c.kind.isHeli() ? new int[]{ROTOR, TAIL_ROTOR, TAIL, ENGINE}
                    : new int[]{ENGINE, GUN0, GUN0 + 1, GUN0 + 2, GUN0 + 3, GUN0 + 4, GUN0 + 5, GUN0 + 6, GUN0 + 7, GUN0 + 8, GUN0 + 9};
            for (int m : order) {
                if (s.broken(m) || s.hurtBit(m)) {
                    s.repairMod = m;
                    int t = switch (m) {
                        case WING_L, WING_R -> 900;
                        case ROTOR -> 800;
                        case TAIL_ROTOR -> 400;
                        case TAIL -> 600;
                        case ENGINE -> ship ? 600 : 500;
                        default -> 800;
                    };
                    s.repairTotal = s.repairTicks = s.broken(m) ? t : t / 2;
                    s.dirtyNow = true;
                    break;
                }
            }
            if (s.repairMod < 0) return;
        }
        if (--s.repairTicks % 20 == 0) s.dirty = true;
        if (s.repairTicks <= 0) {
            int m = s.repairMod;
            s.setBroken(m, false);
            s.setHurt(m, false);
            s.mdmg[m] = 0;
            s.repairMod = -1;
            if (m >= GUN0) {
                int i = m - GUN0;
                if (c instanceof BattleshipEntity) {
                    if (i < BattleshipEntity.N_MAIN) s.lost &= ~(3 << (i * 2));
                    else s.lost &= ~(1 << (8 + i - BattleshipEntity.N_MAIN));
                } else {
                    s.lost = 0;
                }
            }
            VehicleDamage.notifyMod(c, "repaired", m);
            sl.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.NEUTRAL, 1f, 1f);
        }
    }

    // ------------------------------------------------------------------------------------------ physics queries

    public static boolean engineWorks(CraftEntity c) {
        DamageState s = c.dmg;
        if (s.dead()) return false;
        if (!s.broken(ENGINE)) return true;
        return c.kind.isPlane() && c.kind.plane.engines() > 1;          // multi-engine: the others keep running
    }

    public static double thrustFactor(CraftEntity c) {
        DamageState s = c.dmg;
        if (s.dead()) return 0;
        double f = 1.0;
        if (s.broken(ENGINE)) f = c.kind.isPlane() && c.kind.plane.engines() > 1 ? 1.0 - 1.0 / c.kind.plane.engines() - 0.1 : 0;
        else if (s.hurtBit(ENGINE)) f = 0.55;
        return Math.max(0, f);
    }

    public static double liftFactor(CraftEntity c) {
        DamageState s = c.dmg;
        int lost = (s.broken(WING_L) ? 1 : 0) + (s.broken(WING_R) ? 1 : 0);
        return lost == 0 ? 1.0 : lost == 1 ? 0.5 : 0.08;
    }

    /** Aeroplane roll command with a wing shot off: the remaining wing rolls the aircraft towards the lost side. */
    public static double planeRoll(CraftEntity c, double pT, double eff) {
        DamageState s = c.dmg;
        boolean l = s.broken(WING_L), r = s.broken(WING_R);
        if (l == r) return l ? pT * 0.2 : pT;
        double push = 120.0 * Mth.clamp(eff * 2.0, 0.35, 1.0);
        return pT * 0.3 + (l ? -push : push);
    }

    public static double pitchAuth(CraftEntity c) {
        return c.dmg.broken(TAIL) ? 0.3 : c.dmg.hurtBit(TAIL) ? 0.75 : 1.0;
    }

    public static double yawAuth(CraftEntity c) {
        return c.dmg.broken(TAIL) ? 0.2 : 1.0;
    }

    public static double rotorLift(CraftEntity c) {
        DamageState s = c.dmg;
        if (s.broken(ROTOR)) return 0;
        return s.hurtBit(ROTOR) ? 0.85 : 1.0;
    }

    /** Helicopter yaw command: lost tail rotor = the main rotor torque spins the fuselage. */
    public static double heliYaw(CraftEntity c, double rT, double omega2) {
        DamageState s = c.dmg;
        if (s.broken(TAIL_ROTOR) && !c.kind.heli.coaxial()) return rT * 0.15 + 95.0 * omega2;
        if (s.broken(TAIL)) return rT * 0.4;
        return rT;
    }

    public static double boatThrust(CraftEntity c) {
        DamageState s = c.dmg;
        if (s.dead() || s.broken(ENGINE)) return 0;
        return s.hurtBit(ENGINE) ? 0.5 : 1.0;
    }

    /** Battleship: mount i (main 0-3, secondary 4-9) out of action. */
    public static boolean mountOut(CraftEntity c, int mount) {
        DamageState s = c.dmg;
        if (s.dead()) return true;
        int gi = mount < BattleshipEntity.N_MAIN ? mount : mount < BattleshipEntity.N_MAIN + BattleshipEntity.N_SEC ? mount : -1;
        return gi >= 0 && s.broken(GUN0 + gi);
    }
}
