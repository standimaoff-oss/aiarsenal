package ru.aiforge.realwar.client;

import com.mojang.math.Axis;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import ru.aiforge.realwar.craft.BattleshipEntity;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.craft.CraftKind;
import ru.aiforge.realwar.craft.CraftOrdnance;
import ru.aiforge.realwar.craft.CraftWeapon;
import ru.aiforge.realwar.craft.PlaneEntity;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.ordnance.BulletManager;
import ru.aiforge.realwar.vehicle.VehicleKind;
import ru.aiforge.realwar.vehicle.GunVehicle;
import ru.aiforge.realwar.vehicle.MlrsEntity;

/**
 * Reply 11 (r15: one small War Thunder style marker per active barrel): predicted impact point of the ACTIVE weapon of whatever the player drives (tanks, artillery, MLRS,
 * helicopters, boats, Bismarck, aeroplanes - guns, MGs, rockets and bombs), drawn as an animated red reticle in first
 * and third person.
 *
 * <p>Every client tick the projectile is flown forward with exactly the server's integrator (shells/rockets:
 * {@link Ballistics.Flight} with the Froude scale; bullets: BulletManager's per-tick drag + gravity; bombs: the
 * AirBombEntity integrator; helicopter rockets: CraftOrdnance boost/sustain) until it meets a block or water in loaded
 * chunks. The world point is projected with the camera matrices captured during level rendering.
 */
@Mod.EventBusSubscriber(modid = "aiarsenal", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ImpactReticle {
    private static final int RED = 0xFFFF3434, RED_D = 0xB0FF3434, RED_F = 0x60FF3434, SHADE = 0x90000000, AMBER = 0xFFFFB020;

    /** Prediction (client tick): one impact point per active barrel (twin guns, flak, wing guns, rocket pods). */
    private static final int MAXP = 4;
    private static Entity anchor;
    private static final Vec3[] OFF = new Vec3[MAXP], OFF_O = new Vec3[MAXP];
    private static int count;
    private static final java.util.List<Vec3> PTS = new java.util.ArrayList<>();
    private static double range, tof;
    private static boolean hit, guided, valid, reloading, empty, first;
    private static String tag = "";
    /** r20 (msg 20): colour of each marker = chance to penetrate what the shot will hit (green sure, yellow maybe,
     *  red practically impossible, white = no vehicle on the trajectory). Nothing else shows it. */
    public static final int C_NONE = 0xFFE8E8E8, C_SURE = 0xFF3CF04A, C_MAYBE = 0xFFFFD21E, C_NO = 0xFFFF3A2A;
    private static final int[] COL = new int[MAXP];
    private static final java.util.List<Integer> PCOL = new java.util.ArrayList<>();
    private static int pending = C_NONE;
    /** Vehicles / craft near the shooter, gathered once per prediction. */
    private static final java.util.List<Entity> TARGETS = new java.util.ArrayList<>();
    /** Ammunition of the trajectory being simulated (null = rifle-calibre bullets). */
    private static ru.aiforge.realwar.damage.Armour.Ammo curAmmo;
    /** Camera capture (render thread). */
    private static final Matrix4f PROJ = new Matrix4f();
    private static Vec3 camPos = Vec3.ZERO;
    private static boolean captured;
    private static float readyAnim;

    private ImpactReticle() {}

    public static boolean active() {
        return valid && anchor != null && !anchor.isRemoved();
    }

    // ------------------------------------------------------------------------------------------ prediction

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        boolean was = valid;
        valid = false;
        if (p == null || mc.level == null || p.getVehicle() == null) {
            anchor = null;
            return;
        }
        Entity v = p.getVehicle();
        try {
            PTS.clear();
            PCOL.clear();
            pending = C_NONE;
            first = true;
            gatherTargets(mc.level, v);
            predict(mc.level, p, v);
            if (!PTS.isEmpty()) {
                boolean keep = was && anchor == v && count == Math.min(MAXP, PTS.size());
                count = Math.min(MAXP, PTS.size());
                for (int i = 0; i < count; i++) {
                    Vec3 off = PTS.get(i).subtract(v.position());
                    OFF_O[i] = keep && OFF[i] != null ? OFF[i] : off;
                    OFF[i] = off;
                    COL[i] = i < PCOL.size() ? PCOL.get(i) : C_NONE;
                }
                anchor = v;
                valid = true;
            }
        } catch (RuntimeException ex) {
            valid = false;
        }
        if (!valid) anchor = null;
    }

    private static void add(@Nullable Vec3 at) {
        if (at != null && PTS.size() < MAXP) {
            PTS.add(at);
            PCOL.add(pending);
        }
        pending = C_NONE;
        first = false;
    }

    // ------------------------------------------------------------------------------------------ r20 penetration colour

    private static void gatherTargets(Level lv, Entity self) {
        TARGETS.clear();
        Entity root = self.getRootVehicle();
        for (Entity e : lv.getEntities(self, self.getBoundingBox().inflate(900.0), e -> e instanceof ru.aiforge.realwar.vehicle.VehicleEntity
                || e instanceof CraftEntity || e instanceof ru.aiforge.realwar.aircraft.AircraftEntity)) {
            if (e == root || e.isRemoved()) continue;
            TARGETS.add(e);
            if (TARGETS.size() >= 48) break;
        }
    }

    /** First target box crossed by the segment a-b: {entity, point}, or null. */
    @Nullable
    private static Object[] entityClip(Vec3 a, Vec3 b) {
        if (TARGETS.isEmpty()) return null;
        Object[] best = null;
        double bd = Double.MAX_VALUE;
        net.minecraft.world.phys.AABB sweep = new net.minecraft.world.phys.AABB(a, b).inflate(0.5);
        for (Entity e : TARGETS) {
            net.minecraft.world.phys.AABB bb = e.getBoundingBox();
            java.util.Optional<Vec3> h;
            if (e instanceof ru.aiforge.realwar.vehicle.VehicleEntity v) {
                // r20: the real oriented hull / turret boxes, same test as the server's shells
                if (!bb.inflate(v.spec.length() * 0.5 + 2.0).intersects(sweep)) continue;
                h = java.util.Optional.ofNullable(ru.aiforge.realwar.damage.VehicleHit.clip(v, a, b));
            } else {
                if (!bb.intersects(sweep)) continue;
                h = bb.clip(a, b);
                if (h.isEmpty() && bb.contains(a)) h = java.util.Optional.of(a);
            }
            if (h.isPresent()) {
                double d = h.get().distanceToSqr(a);
                if (d < bd) {
                    bd = d;
                    best = new Object[]{e, h.get()};
                }
            }
        }
        return best;
    }

    /** Colour for a hit on e at world point p, flying along dir at speed (m/s) with the current ammunition. */
    private static final boolean DEBUG = Boolean.getBoolean("aiarsenal.reticleDebug");
    private static long dbgT;

    private static int judge(Entity e, Vec3 p, Vec3 dir, double speed) {
        if (!(e instanceof ru.aiforge.realwar.vehicle.VehicleEntity v)) return C_SURE;      // aircraft, boats: no armour model
        if (!ru.aiforge.realwar.damage.Armour.armoured(v.kind)) return C_SURE;
        ru.aiforge.realwar.damage.Armour.Ammo a = curAmmo;
        if (a == null) return C_NO;                                                         // rifle calibre vs armour
        try {
            ru.aiforge.realwar.damage.VehicleDamage.Geo g = ru.aiforge.realwar.damage.VehicleDamage.geo(v);
            org.joml.Matrix4d inv = new org.joml.Matrix4d(v.bodyFrame()).invert();
            org.joml.Vector3d lp = inv.transformPosition(new org.joml.Vector3d(p.x, p.y, p.z));
            org.joml.Vector3d ld = inv.transformDirection(new org.joml.Vector3d(dir.x, dir.y, dir.z));
            if (ld.lengthSquared() < 1e-9) return C_NONE;
            ld.normalize();
            if (a.type() == ru.aiforge.realwar.damage.Armour.Type.EFP) ld.set(0, -1, 0);
            float[] ang = ru.aiforge.realwar.damage.VehicleDamage.turretAngles(v);
            boolean tg = v.dmg.broken(ru.aiforge.realwar.damage.DamageState.TURRET_GONE);
            ru.aiforge.realwar.damage.Armour.Result best = ru.aiforge.realwar.damage.Armour.solve(g, v.kind, tg, ang, lp, ld, a, speed, 0.999);
            ru.aiforge.realwar.damage.Armour.Result worst = ru.aiforge.realwar.damage.Armour.solve(g, v.kind, tg, ang, lp, ld, a, speed, 0.0);
            if (DEBUG && System.currentTimeMillis() - dbgT > 2000) {
                dbgT = System.currentTimeMillis();
                ru.aiforge.realwar.RealWar.LOGGER.info("reticle judge {} ammo {} pen0 {} speed {} lp {} ld {} best {} worst {}", v.kind, a.name(), a.pen0(), speed, lp, ld, best, worst);
            }
            if (best.result() == ru.aiforge.realwar.damage.Armour.MODULE) return C_MAYBE;
            if (worst.result() == ru.aiforge.realwar.damage.Armour.PEN && worst.pen() >= 1.12f * worst.eff()) return C_SURE;
            if (best.result() != ru.aiforge.realwar.damage.Armour.PEN
                    && (best.result() == ru.aiforge.realwar.damage.Armour.RICOCHET || best.pen() < 0.9f * best.eff())) return C_NO;
            return C_MAYBE;
        } catch (RuntimeException ex) {
            return C_NONE;
        }
    }

    private static void predict(Level lv, LocalPlayer p, Entity v) {
        reloading = false;
        empty = false;
        guided = false;
        if (v instanceof CraftEntity c) {
            int seat = c.seatOf(p);
            if (seat < 0) return;
            int li = c.weaponFor(seat);
            if (li < 0) return;
            CraftKind.Load l = c.kind.loads[li];
            CraftWeapon w = l.weapon();
            empty = c.ammo(li) <= 0;
            tag = net.minecraft.network.chat.Component.translatable(w.langKey()).getString();
            if (c instanceof BattleshipEntity bs) {
                add(battleship(lv, bs, w, p));
                return;
            }
            if (w.guided()) {
                guided = true;
                curAmmo = ru.aiforge.realwar.damage.Armour.Ammo.ofCraft(w == CraftWeapon.VIKHR ? "VIKHR" : w == CraftWeapon.HELLFIRE ? "HELLFIRE" : w.name(), "", 8.0);
                add(lookPoint(lv, p, 3000));
                return;
            }
            double sc = c instanceof PlaneEntity pl ? PlaneEntity.scaleCfg() : 1.0;
            Vec3 inherit = c.vel.scale(CraftEntity.DT * sc);
            Vec3 dir = l.turret() ? c.dirFromAngles(c.gunYawC, c.gunPitchC) : c.basis()[0];
            if (w.type == CraftWeapon.Type.BOMB) {
                int idx = Mth.clamp(c.ammo(li) - 1, 0, l.muzzles().length - 1);
                float[] b = l.muzzles()[idx];
                add(bomb(lv, c.localToWorld(b[0], b[1] - 0.4, b[2]), inherit.add(0, -0.05, 0), c));
                return;
            }
            // aeroplane fixed guns fire every muzzle together; helicopter / boat mounts alternate - both are shown
            int n = Math.min(MAXP, l.muzzles().length);
            for (int i = 0; i < n; i++) {
                float[] m0 = l.muzzles()[i];
                Vec3 m = c.localToWorld(m0[0], m0[1], m0[2]);
                switch (w.type) {
                    case CANNON -> {
                        Ballistics.Profile prof = switch (w) {
                            case GUN_2A42 -> Ballistics.Profile.A42_HE;
                            case GUN_M230 -> Ballistics.Profile.M230_HE;
                            case SHVAK -> Ballistics.Profile.SHVAK_HE;
                            case MGFF -> Ballistics.Profile.MGFF_HE;
                            default -> Ballistics.Profile.MG151_HE;
                        };
                        if (l.turret()) m = m.add(dir.scale(1.4));
                        add(shell(lv, prof, c instanceof PlaneEntity ? m.add(inherit) : m, dir, c));
                    }
                    case MG -> {
                        double sp = w == CraftWeapon.MG_KPV ? 32.0 : w == CraftWeapon.MG131 ? 29.0 : w == CraftWeapon.UBS ? 30.0 : 28.0;
                        if (l.turret()) m = m.add(dir);
                        add(bullet(lv, m, dir.scale(sp).add(inherit), c));
                    }
                    case ROCKET -> add(craftRocket(lv, w == CraftWeapon.S8 ? CraftOrdnance.Type.S8 : CraftOrdnance.Type.HYDRA, m, dir, inherit, c));
                    default -> {
                        return;
                    }
                }
            }
            return;
        }
        if (v instanceof GunVehicle gv) {
            tag = "";
            reloading = gv.reloadTube() >= 0;
            int mode = gv.mode();
            if (mode == 3) {
                int ct = gv.cfg.coax30() ? 1 : gv.spec.tubes().length - 1;
                add(bullet(lv, gv.tubeWorld(ct), gv.launchDir().scale(12.0), gv));
                return;
            }
            if (mode == 2 && (gv.kind == ru.aiforge.realwar.vehicle.VehicleKind.BRADLEY || gv.kind.isSpaag())) {
                guided = true;                       // TOW / radar-guided SPAAG SAM
                tag = gv.kind == ru.aiforge.realwar.vehicle.VehicleKind.BRADLEY ? "TOW" : "SAM";
                empty = gv.thirdRounds() <= 0;
                curAmmo = ru.aiforge.realwar.damage.Armour.Ammo.of(gv.cfg.third());
                Entity rTgt = gv.kind.isSpaag() ? gv.flakTarget() : null;
                add(rTgt != null ? rTgt.getBoundingBox().getCenter() : lookPoint(lv, p, 3750));
                return;
            }
            if (mode == 2 && gv.cfg.third() != null) {
                boolean rear = gv.isRatte();
                Vec3 m = rear ? gv.secMuzzle() : gv.tubeWorld(1);
                Vec3 d = rear ? gv.secDir() : gv.launchDir();
                add(shell(lv, gv.cfg.third(), m.add(d.scale(0.2)), d, gv));
                return;
            }
            empty = gv.loadedOfType(gv.loadType()) == 0 && !reloading;   // r28: the selected round type, not the whole rack
            if (gv.kind == VehicleKind.MARK_I) {
                for (int tube = 0; tube < 2; ++tube) {
                    Vec3 ds = gv.sponsonDir(tube);
                    add(shell(lv, gv.profile(), gv.tubeWorld(tube).add(ds.scale(0.2)), ds, gv));
                }
                return;
            }
            Vec3 d = gv.launchDir();
            add(shell(lv, gv.profile(), gv.tubeWorld(0).add(d.scale(0.2)), d, gv));
            if (gv.twinSalvo()) add(shell(lv, gv.profile(), gv.tubeWorld(1).add(d.scale(0.2)), d, gv));
            return;
        }
        if (v instanceof ru.aiforge.realwar.vehicle.FlakEntity fl) {
            tag = "";
            empty = fl.ammo() <= 0;
            Vec3 d = fl.aimDir(fl.az(), fl.el());
            for (int b = 0; b < 2; b++) add(bullet(lv, fl.barrelMuzzle(b, fl.az(), fl.el()), d.scale(ru.aiforge.realwar.vehicle.FlakEntity.MUZZLE_SPEED), fl));
            return;
        }
        if (v instanceof MlrsEntity ml) {
            tag = "";
            int k = Math.max(0, ml.firstLoaded());
            empty = ml.firstLoaded() < 0;
            Vec3 d = ml.launchDir();
            add(shell(lv, ml.profile(), ml.muzzleWorld(k).add(d.scale(0.6)), d, ml));
        }
    }

    @Nullable
    private static Vec3 battleship(Level lv, BattleshipEntity bs, CraftWeapon w, LocalPlayer p) {
        int from, to;
        Ballistics.Profile prof;
        if (w == CraftWeapon.SK38_AP || w == CraftWeapon.SK38_HE) {
            from = 0;
            to = BattleshipEntity.N_MAIN;
            prof = bs.mainProfile();
        } else if (w == CraftWeapon.SK15) {
            from = BattleshipEntity.I_SEC;
            to = BattleshipEntity.I_SEC + BattleshipEntity.N_SEC;
            prof = Ballistics.Profile.SK15_HE;
        } else {
            return null;
        }
        // the mount pointing closest to where the director (player) looks
        Vec3 look = p.getLookAngle();
        int best = from;
        double bd = -2;
        for (int i = from; i < to; i++) {
            Vec3 dl = BattleshipEntity.localDir(bs.az[i], bs.el[i]);
            Vec3 d = bs.localDirToWorld(dl.x, dl.y, dl.z);
            double dot = new Vec3(d.x, 0, d.z).normalize().dot(new Vec3(look.x, 0, look.z).normalize());
            if (dot > bd) {
                bd = dot;
                best = i;
            }
        }
        Vec3 ml = BattleshipEntity.muzzleLocal(best, 0, bs.az[best], bs.el[best], 0);
        Vec3 dl = BattleshipEntity.localDir(bs.az[best], bs.el[best]);
        Vec3 m = bs.localToWorld(ml.x, ml.y, ml.z);
        Vec3 d = bs.localDirToWorld(dl.x, dl.y, dl.z);
        return shell(lv, prof, m.add(d.scale(0.5)), d, bs);
    }

    private static Vec3 lookPoint(Level lv, LocalPlayer p, double max) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(max));
        BlockHitResult h = lv.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
        boolean ht = h.getType() != HitResult.Type.MISS;
        Vec3 at = ht ? h.getLocation() : end;
        Object[] eh = entityClip(eye, at);
        if (eh != null) {
            at = (Vec3) eh[1];
            ht = true;
            pending = judge((Entity) eh[0], at, p.getLookAngle(), 300.0);
        }
        if (first) {
            hit = ht;
            range = at.distanceTo(eye);
            tof = 0;
        }
        return at;
    }

    @Nullable
    private static BlockHitResult seg(Level lv, Vec3 a, Vec3 b, Entity ignore) {
        BlockHitResult h = lv.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, ignore));
        return h.getType() == HitResult.Type.MISS ? null : h;
    }

    private static Vec3 done(Vec3 origin, Vec3 at, double ticks, boolean wasHit) {
        if (first) {
            hit = wasHit;
            range = at.distanceTo(origin);
            tof = ticks / 20.0;
        }
        return at;
    }

    /** Shells and gun-launched / MLRS rockets: Ballistics.Flight, K-scaled, substeps per tick like RocketEntity. */
    private static Vec3 shell(Level lv, Ballistics.Profile prof, Vec3 m, Vec3 dir, Entity ignore) {
        double k = Ballistics.scaleK();
        int n = Ballistics.substeps();
        Vec3 d = dir.normalize();
        Ballistics.Flight f = new Ballistics.Flight(prof, d.x, d.y, d.z);
        Vec3 prev = m;
        int punched = 0;
        // r20: no target within ~800 m (sky, far horizon) -> the marker stays on the flight path, so it follows the
        // gun up into the sky instead of collapsing onto a far-away ground point near the horizon
        Vec3 sky = null;
        double skyD = 800.0 / k;
        int maxSub = Math.min(2400 * n, (int) (160.0 / Ballistics.dtStep()) + 1);
        for (int i = 1; i <= maxSub; i++) {
            f.step(Ballistics.dtStep(), 0, 0, 0);
            Vec3 cur = new Vec3(m.x + f.x / k, m.y + f.y / k, m.z + f.z / k);
            if (sky == null && cur.distanceTo(m) >= skyD) sky = cur;
            if (!lv.hasChunkAt(BlockPos.containing(cur))) return done(m, sky != null ? sky : prev, (double) i / n, false);
            BlockHitResult h = seg(lv, prev, cur, ignore);
            Object[] eh = entityClip(prev, cur);
            if (eh != null && (h == null || ((Vec3) eh[1]).distanceToSqr(prev) <= h.getLocation().distanceToSqr(prev))) {
                curAmmo = ru.aiforge.realwar.damage.Armour.Ammo.of(prof);
                pending = judge((Entity) eh[0], (Vec3) eh[1], cur.subtract(prev), f.speed());
                return done(m, (Vec3) eh[1], (double) i / n, true);
            }
            while (h != null && prof.kinetic() && punched < 6 && h.getType() == HitResult.Type.BLOCK
                    && ru.aiforge.realwar.ordnance.ShellEntity.punchable(lv.getBlockState(h.getBlockPos()))) {
                ++punched;                           // the server punches this block and keeps flying
                BlockPos bp = h.getBlockPos();
                Vec3 sd = cur.subtract(prev).normalize();
                Vec3 q = h.getLocation();
                while (BlockPos.containing(q).equals(bp) && q.distanceToSqr(cur) > 1e-4) q = q.add(sd.scale(0.05));
                h = q.distanceToSqr(cur) > 1e-4 ? seg(lv, q, cur, ignore) : null;
            }
            if (h != null) return done(m, h.getLocation(), (double) i / n, true);
            if (cur.y < lv.getMinBuildHeight()) return done(m, sky != null ? sky : cur, (double) i / n, false);
            prev = cur;
        }
        return done(m, sky != null ? sky : prev, (double) maxSub / n, false);
    }

    /** BulletManager: v *= 0.985, vy -= 0.02 per tick, expires after lifeTicks. */
    private static Vec3 bullet(Level lv, Vec3 m, Vec3 v0, Entity ignore) {
        Vec3 v = v0, prev = m;
        int life = BulletManager.lifeTicks();
        for (int t = 1; t <= life; t++) {
            v = new Vec3(v.x * 0.985, v.y * 0.985 - 0.02, v.z * 0.985);
            Vec3 cur = prev.add(v);
            if (!lv.hasChunkAt(BlockPos.containing(cur))) return done(m, prev, t, false);
            BlockHitResult h = seg(lv, prev, cur, ignore);
            Object[] eh = entityClip(prev, cur);
            if (eh != null && (h == null || ((Vec3) eh[1]).distanceToSqr(prev) <= h.getLocation().distanceToSqr(prev))) {
                curAmmo = null;
                pending = judge((Entity) eh[0], (Vec3) eh[1], v, 800.0);
                return done(m, (Vec3) eh[1], t, true);
            }
            if (h != null) return done(m, h.getLocation(), t, true);
            prev = cur;
        }
        return done(m, prev, life, false);
    }

    /** AirBombEntity: g 0.0245 b/t^2, quadratic drag. */
    private static Vec3 bomb(Level lv, Vec3 m, Vec3 v0, Entity ignore) {
        Vec3 v = v0, prev = m;
        for (int t = 1; t <= 1600; t++) {
            v = v.add(0, -0.0245, 0);
            v = v.scale(1.0 - 4.0E-4 * v.length());
            Vec3 cur = prev.add(v);
            if (!lv.hasChunkAt(BlockPos.containing(cur))) return done(m, prev, t, false);
            BlockHitResult h = seg(lv, prev, cur, ignore);
            if (h != null) return done(m, h.getLocation(), t, true);
            if (cur.y < lv.getMinBuildHeight()) return done(m, cur, t, false);
            prev = cur;
        }
        return done(m, prev, 1600, false);
    }

    /** CraftOrdnance S-8 / Hydra: boost, sustain to max speed, then decay; gravity scaled per type. */
    private static Vec3 craftRocket(Level lv, CraftOrdnance.Type t, Vec3 m, Vec3 dir, Vec3 inheritPerTick, Entity ignore) {
        double dt = 0.05;
        Vec3 v = dir.normalize().scale(t.boost).add(inheritPerTick.scale(20.0)), prev = m;
        for (int i = 1; i <= t.life; i++) {
            double sp = v.length();
            Vec3 d = sp > 1e-6 ? v.scale(1 / sp) : dir;
            if (i - 1 < t.burn) sp = Math.min(t.speed, sp + (t.speed - t.boost) / t.burn);
            else sp *= 0.996;
            v = d.scale(sp).add(0, -9.81 * dt * t.gravity, 0);
            Vec3 cur = prev.add(v.scale(dt));
            if (!lv.hasChunkAt(BlockPos.containing(cur))) return done(m, prev, i, false);
            BlockHitResult h = seg(lv, prev, cur, ignore);
            Object[] eh = entityClip(prev, cur);
            if (eh != null && (h == null || ((Vec3) eh[1]).distanceToSqr(prev) <= h.getLocation().distanceToSqr(prev))) {
                curAmmo = ru.aiforge.realwar.damage.Armour.Ammo.ofCraft(t.name(), "", 1.0);
                pending = judge((Entity) eh[0], (Vec3) eh[1], v, v.length());
                return done(m, (Vec3) eh[1], i, true);
            }
            if (h != null) return done(m, h.getLocation(), i, true);
            prev = cur;
        }
        return done(m, prev, t.life, false);
    }

    // ------------------------------------------------------------------------------------------ render

    /** r17: the camera roll set by vehicles / blast shake (applied to the view after yaw / pitch). */
    private static float camRoll;

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onCameraAngles(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles e) {
        camRoll = e.getRoll();
    }

    /**
     * r17 fix: the marker used to be projected with the pose matrix handed to RenderLevelStageEvent, which came out
     * mirrored left-right (barrel turned right -> marker on the left). Now the view basis is taken straight from the
     * camera: forward, up and right = forward x up (MC axes: facing north the right hand is east), then the camera roll,
     * then the perspective scale of the real projection matrix (m00 / m11 = FOV and aspect, zoom included).
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        PROJ.set(e.getProjectionMatrix());
        net.minecraft.client.Camera c = e.getCamera();
        FWD.set(c.getLookVector());
        UP.set(c.getUpVector());
        FWD.cross(UP, RIGHT);
        RIGHT.normalize();
        camPos = c.getPosition();
        captured = true;
    }

    private static final org.joml.Vector3f FWD = new org.joml.Vector3f(), UP = new org.joml.Vector3f(), RIGHT = new org.joml.Vector3f();

    /** World point -> GUI coordinates, or null when behind the camera. */
    @Nullable
    static float[] project(Vec3 w, int gw, int gh) {
        float dx = (float) (w.x - camPos.x), dy = (float) (w.y - camPos.y), dz = (float) (w.z - camPos.z);
        float depth = dx * FWD.x + dy * FWD.y + dz * FWD.z;
        if (depth <= 0.05f) return null;
        float x = dx * RIGHT.x + dy * RIGHT.y + dz * RIGHT.z;
        float y = dx * UP.x + dy * UP.y + dz * UP.z;
        if (camRoll != 0f) {
            // the view is turned by roll about the look axis (PoseStack ZP(roll) before the camera rotation)
            float r = (float) Math.toRadians(camRoll), cs = Mth.cos(r), sn = Mth.sin(r);
            float xr = x * cs - y * sn, yr = x * sn + y * cs;
            x = xr;
            y = yr;
        }
        float nx = x / depth * PROJ.m00(), ny = y / depth * PROJ.m11();
        return new float[]{(nx * 0.5f + 0.5f) * gw, (1f - (ny * 0.5f + 0.5f)) * gh};
    }

    /** War Thunder style: a small red circle with a centre dot and four short ticks per barrel; distance in small
     *  print next to the first marker; amber quarter arcs while reloading; grey when out of ammo. */
    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || !captured || mc.options.hideGui || count == 0) return;
        Entity a = anchor;
        Vec3 base = new Vec3(Mth.lerp(pt, a.xo, a.getX()), Mth.lerp(pt, a.yo, a.getY()), Mth.lerp(pt, a.zo, a.getZ()));
        float time = (a.tickCount + pt) / 20f;
        float lx = -1, ly = -1;
        int col0 = C_NONE;
        for (int i = 0; i < count; i++) {
            if (OFF[i] == null) continue;
            int col = empty ? 0xFFA0A0A0 : COL[i];
            if (i == 0) col0 = col;
            Vec3 off = OFF_O[i] == null ? OFF[i] : OFF_O[i].lerp(OFF[i], pt);
            float[] s = project(base.add(off), w, h);
            if (s == null) continue;
            boolean onScreen = s[0] > 6 && s[0] < w - 6 && s[1] > 6 && s[1] < h - 6;
            float x = Mth.clamp(s[0], 8, w - 8), y = Mth.clamp(s[1], 8, h - 8);
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(0.5f, 0.5f, 1f);           // half-pixel strokes
            if (!onScreen) {
                float ang = (float) Math.toDegrees(Math.atan2(s[1] - h / 2f, s[0] - w / 2f));
                g.pose().mulPose(Axis.ZP.rotationDegrees(ang));
                for (int k = 0; k < 6; k++) g.fill(-k * 2 - 2, -k, -k * 2, k + 1, col);
                g.pose().popPose();
                continue;
            }
            arc(g, 0, 0, 10, 4, 0, 360, SHADE & 0x50FFFFFF, 10);        // faint shadow for bright backgrounds
            arc(g, 0, 0, 10, 2, 0, 360, col, 8);                        // r = 5 GUI px
            g.fill(-1, -1, 1, 1, col);                                  // centre dot
            g.fill(-17, -1, -12, 1, col);                               // ticks
            g.fill(12, -1, 17, 1, col);
            g.fill(-1, 12, 1, 17, col);
            g.fill(-1, -17, 1, -12, col);
            if (reloading) arc(g, 0, 0, 14, 2, time * 240f, 70, AMBER, 6);
            g.pose().popPose();
            if (lx < 0) {
                lx = x;
                ly = y;
            }
        }
        if (lx >= 0) {
            Font f = mc.font;
            String l1 = guided || hit ? String.format("%.0f", range) : String.format(">%.0f", range);
            if (empty) l1 = "--";
            g.pose().pushPose();
            g.pose().translate(lx + 10, ly - 3, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(f, l1, 0, 0, col0, true);
            g.pose().popPose();
        }
        // boresight cue (tiny cross at screen centre) when the impact point is away from it
        int cx = w / 2, cy = h / 2;
        if (lx >= 0 && (Math.abs(cx - lx) > 10 || Math.abs(cy - ly) > 10)) {
            g.fill(cx - 4, cy, cx - 1, cy + 1, 0xB0E8E8E8);
            g.fill(cx + 2, cy, cx + 5, cy + 1, 0xB0E8E8E8);
            g.fill(cx, cy - 4, cx + 1, cy - 1, 0xB0E8E8E8);
            g.fill(cx, cy + 2, cx + 1, cy + 5, 0xB0E8E8E8);
        }
    }

    /** Arc of small quads (in the current pose units): centre, radius, stroke, start/sweep (deg), colour, step (deg). */
    private static void arc(GuiGraphics g, float cx, float cy, float r, float th, float start, float sweep, int col, float step) {
        int n = Math.max(1, (int) Math.ceil(sweep / step));
        float seg = (float) (Math.toRadians(sweep / n) * r) + 1.2f;
        for (int i = 0; i < n; i++) {
            float a = start + sweep * (i + 0.5f) / n;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(a));
            g.pose().translate(r, 0, 0);
            int hs = Math.max(1, Math.round(seg / 2));
            int ht = Math.max(1, Math.round(th / 2));
            g.fill(-ht, -hs, ht, hs, col);
            g.pose().popPose();
        }
    }
}
