package ru.aiforge.realwar.deck;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import ru.aiforge.bm21grad.entity.Bm21Entity;
import ru.aiforge.realwar.aircraft.AircraftEntity;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.vehicle.VehicleEntity;
import ru.aiforge.realwar.vehicle.LauncherVehicle;
import ru.aiforge.realwar.vehicle.GunVehicle;
import ru.aiforge.realwar.deck.DeckShapes.Deck;

/**
 * Walkable vehicles (reply 7, collision rewritten in reply 9): every vehicle with a {@link DeckShapes} entry has a
 * solid, multi-level shape made of merged boxes (generated from its GLB) that follows the hull's position and
 * heading; turret boxes additionally rotate with the turret. Entities stand on the highest box top within a step,
 * are pushed out of taller boxes (walls), stopped by box undersides (ceilings - e.g. inside the Ratte) and carried
 * along with the vehicle (and with the turret, if they stand on it).
 * <p>
 * Deliberately no PartEntities / Mixins: half the projectile hit-tests in the mod do not filter part entities, so a
 * vehicle would detonate its own ordnance inside its own boxes, and vanilla entity collision only knows one AABB.
 * Instead the local player is corrected on its client (movement is client-authoritative) and mobs / items on the
 * server, after vanilla movement.
 */
@Mod.EventBusSubscriber(modid = "aiarsenal")
public final class Decks {
    private static final double STEP = 0.6;
    /** Host pose at the previous tick per side: x, y, z, yaw(deg), az turret 1, az turret 2 (deg). */
    private static final Map<Entity, double[]> PREV_SERVER = new WeakHashMap<>();
    private static final Map<Entity, Entity> ON_SERVER = new WeakHashMap<>();   // rider entity -> host
    private static final Map<Entity, Integer> GRP_SERVER = new WeakHashMap<>();
    private static Field aboveGround;
    private static boolean fieldFailed;

    private Decks() {
    }

    /** Collision shape of a host entity, null if the entity is not walkable. */
    @Nullable
    public static Deck shape(Entity e) {
        String m = null;
        if (e instanceof VehicleEntity v) m = v.spec.model().getPath();
        else if (e instanceof CraftEntity c) m = c.kind.model;
        else if (e instanceof AircraftEntity) m = "models/glb/tu95.glb";
        else if (e instanceof Bm21Entity) m = "glb/bm21.glb";
        else if (e instanceof ru.aiforge.tubombers.entity.BomberEntity) {
            String id = net.minecraft.world.entity.EntityType.getKey(e.getType()).getPath();
            m = id.contains("160") ? "glb/tu160.glb" : "glb/tu95ms.glb";
        }
        return m == null ? null : DeckShapes.get(m);
    }

    /**
     * Host pose: {x, y, z, yaw, pitch, roll, pivotY, angle of group 1, angle of group 2, ...} (degrees).
     * r11: pitch (+ nose up) and roll (+ right side down) about (0, pivotY, 0) - the boxes tilt with the model,
     * exactly like the renderers (Y(-yaw) X(-pitch) Z(roll)).
     */
    public static double[] pose(Entity host) {
        double a1 = 0, a2 = 0, pitch = 0, roll = 0, piv = 0, y = host.getY();
        if (host instanceof LauncherVehicle lv) a1 = lv.az();
        if (host instanceof GunVehicle gv) a2 = gv.secAz() - 180.0;   // same as the renderer: rest pose (az 180) faces aft
        if (host instanceof VehicleEntity v) {
            pitch = v.bodyPitch;
            roll = v.bodyRoll;
            y += v.bodyDy;
        } else if (host instanceof CraftEntity c) {
            pitch = c.pitch;
            roll = c.roll;
            piv = c.pivotY();
        }
        if (host instanceof ru.aiforge.realwar.craft.BattleshipEntity bs) {
            // r10: 4 main + 6 secondary turrets are separate walkable groups 1..10
            double[] r = new double[A0 + 11];
            r[0] = host.getX(); r[1] = y; r[2] = host.getZ(); r[3] = host.getYRot(); r[4] = pitch; r[5] = roll; r[6] = piv;
            for (int g = 1; g <= 10; g++) r[A0 + g] = bs.deckGroupAngle(g);
            return r;
        }
        return new double[]{host.getX(), y, host.getZ(), host.getYRot(), pitch, roll, piv, a1, a2};
    }

    /** Index base of the group angles in a pose: group g (>= 1) at p[A0 + g]. */
    static final int A0 = 6;

    /** Rotation (deg) of group g in pose p; 0 for groups the host does not animate. */
    static double ang(double[] p, int g) {
        return g > 0 && A0 + g < p.length ? p[A0 + g] : 0.0;
    }

    /** World -> host-local 3-D (model space; glTF +X left, +Y up from the origin, +Z forward). */
    static double[] toLocal3(double[] p, double x, double y, double z) {
        double dx = x - p[0], dy = y - p[1] - p[6], dz = z - p[2];
        double a = Math.toRadians(p[3]), c = Math.cos(a), s = Math.sin(a);
        double x1 = dx * c + dz * s, z1 = -dx * s + dz * c;          // Ry(yaw)
        double y1 = dy;
        if (p[4] != 0 || p[5] != 0) {
            double pa = Math.toRadians(p[4]), cp = Math.cos(pa), sp = Math.sin(pa);
            double y2 = y1 * cp - z1 * sp, z2 = y1 * sp + z1 * cp;     // Rx(pitch)
            double ra = Math.toRadians(-p[5]), cr = Math.cos(ra), sr = Math.sin(ra);
            double x3 = x1 * cr - y2 * sr, y3 = x1 * sr + y2 * cr;     // Rz(-roll)
            return new double[]{x3, y3 + p[6], z2};
        }
        return new double[]{x1, y1 + p[6], z1};
    }

    /** Host-local 3-D -> world. */
    static double[] toWorld3(double[] p, double lx, double ly, double lz) {
        double x = lx, y = ly - p[6], z = lz;
        if (p[4] != 0 || p[5] != 0) {
            double ra = Math.toRadians(p[5]), cr = Math.cos(ra), sr = Math.sin(ra);
            double x1 = x * cr - y * sr, y1 = x * sr + y * cr;         // Rz(roll)
            double pa = Math.toRadians(-p[4]), cp = Math.cos(pa), sp = Math.sin(pa);
            double y2 = y1 * cp - z * sp, z2 = y1 * sp + z * cp;       // Rx(-pitch)
            x = x1; y = y2; z = z2;
        }
        double a = Math.toRadians(p[3]), c = Math.cos(a), s = Math.sin(a);
        return new double[]{p[0] + x * c - z * s, p[1] + p[6] + y, p[2] + x * s + z * c};   // Ry(-yaw)
    }

    /** World up expressed in the host-local frame. */
    static double[] up(double[] p) {
        if (p[4] == 0 && p[5] == 0) return new double[]{0, 1, 0};
        double pa = Math.toRadians(p[4]), ra = Math.toRadians(p[5]);
        return new double[]{Math.cos(pa) * Math.sin(ra), Math.cos(pa) * Math.cos(ra), Math.sin(pa)};
    }

    /** World -> host-local (x, z) at height y (kept for the 2-D callers). */
    static double[] toLocal(double[] p, double x, double z) {
        double[] l = toLocal3(p, x, p[1], z);
        return new double[]{l[0], l[2]};
    }

    /** Per-group rotation tables {cos, sin} of the group angles. */
    private static double[] groupTrig(Deck d, double[] p) {
        int n = Math.max(3, d.pivot.length / 2);
        double[] t = new double[n * 2];
        for (int g = 0; g < n; g++) {
            double a = Math.toRadians(ang(p, g));
            t[g * 2] = Math.cos(a);
            t[g * 2 + 1] = Math.sin(a);
        }
        return t;
    }

    /** Model-space point -> rest frame of group g (undo the turret's rotation about its pivot). */
    static double[] toGroup(Deck d, double[] p, int g, double mx, double mz) {
        if (g == 0 || g * 2 + 1 >= d.pivot.length) return new double[]{mx, mz};
        double a = Math.toRadians(ang(p, g));
        double px = d.pivot[g * 2], pz = d.pivot[g * 2 + 1], dx = mx - px, dz = mz - pz;
        double c = Math.cos(a), s = Math.sin(a);
        return new double[]{px + dx * c - dz * s, pz + dx * s + dz * c};
    }

    static double[] fromGroup(Deck d, double[] p, int g, double tx, double tz) {
        if (g == 0 || g * 2 + 1 >= d.pivot.length) return new double[]{tx, tz};
        double a = Math.toRadians(ang(p, g));
        double px = d.pivot[g * 2], pz = d.pivot[g * 2 + 1], dx = tx - px, dz = tz - pz;
        double c = Math.cos(a), s = Math.sin(a);
        return new double[]{px + dx * c + dz * s, pz - dx * s + dz * c};
    }

    /** Local (x, z) -> frames of all groups: {x0,z0,x1,z1,...}. */
    static double[] frames(Deck d, double[] p, double lx, double lz, double[] trig) {
        int n = trig.length / 2;
        double[] r = new double[n * 2];
        r[0] = lx; r[1] = lz;
        for (int g = 1; g < n; g++) {
            if (g * 2 + 1 >= d.pivot.length) { r[g * 2] = lx; r[g * 2 + 1] = lz; continue; }
            double px = d.pivot[g * 2], pz = d.pivot[g * 2 + 1], dx = lx - px, dz = lz - pz;
            double c = trig[g * 2], s = trig[g * 2 + 1];
            r[g * 2] = px + dx * c - dz * s;
            r[g * 2 + 1] = pz + dx * s + dz * c;
        }
        return r;
    }

    /** Group-frame point at local height ly -> world (x, y, z). */
    static double[] groupToWorld3(Deck d, double[] p, int g, double tx, double ly, double tz) {
        double[] m = fromGroup(d, p, g, tx, tz);
        return toWorld3(p, m[0], ly, m[1]);
    }

    private static boolean inBox(float[] b, int i, double gx, double gz, double m) {
        return gx >= b[i + 1] - m && gx <= b[i + 4] + m && gz >= b[i + 3] - m && gz <= b[i + 6] + m;
    }

    private static double gX(Deck d, double[] trig, int g, double lx, double lz) {
        if (g == 0 || g * 2 + 1 >= d.pivot.length) return lx;
        double px = d.pivot[g * 2], pz = d.pivot[g * 2 + 1];
        return px + (lx - px) * trig[g * 2] - (lz - pz) * trig[g * 2 + 1];
    }

    private static double gZ(Deck d, double[] trig, int g, double lx, double lz) {
        if (g == 0 || g * 2 + 1 >= d.pivot.length) return lz;
        double px = d.pivot[g * 2], pz = d.pivot[g * 2 + 1];
        return pz + (lx - px) * trig[g * 2 + 1] + (lz - pz) * trig[g * 2];
    }

    /**
     * Highest box top under the entity that it can stand on from feetY (within a step above, or up to `below`
     * blocks below): {top (world Y), group} or null. On a tilted host the world vertical through the feet is
     * intersected with each (tilted) box top plane.
     */
    @Nullable
    public static double[] surface(Deck d, double[] p, Entity e, double feetY, double below) {
        double[] L = toLocal3(p, e.getX(), feetY, e.getZ());
        double[] u = up(p);
        double[] trig = groupTrig(d, p);
        double m = e.getBbWidth() * 0.3;
        float[] b = d.b;
        double best = Double.NaN;
        int bg = 0;
        double iu = 1.0 / Math.max(0.2, u[1]);
        for (int i = 0; i < b.length; i += 7) {
            double t = (b[i + 5] - L[1]) * iu;                      // world-vertical distance feet -> top plane
            if (t > STEP || t < -below) continue;
            int g = (int) b[i];
            double hx = L[0] + t * u[0], hz = L[2] + t * u[2];
            if (!inBox(b, i, gX(d, trig, g, hx, hz), gZ(d, trig, g, hx, hz), m)) continue;
            double top = feetY + t;
            if (Double.isNaN(best) || top > best) { best = top; bg = g; }
        }
        return Double.isNaN(best) ? null : new double[]{best, bg};
    }

    /**
     * Horizontal push-out from boxes that are more than a step above the feet and overlap the body vertically
     * (walls): the deepest shallow (<= 0.6 m) overlap is resolved along its axis of least penetration. {dx,dz} or null.
     */
    @Nullable
    public static double[] wall(Deck d, double[] p, Entity e, double feetY) {
        double[] L = toLocal3(p, e.getX(), feetY, e.getZ());
        double[] trig = groupTrig(d, p);
        double[] f = frames(d, p, L[0], L[2], trig);
        double hw = e.getBbWidth() * 0.5, feet = L[1], head = feet + e.getBbHeight();
        float[] b = d.b;
        double bestPen = 0, px = 0, pz = 0;
        int bg = 0;
        for (int i = 0; i < b.length; i += 7) {
            double top = b[i + 5], bot = b[i + 2];
            if (top <= feet + STEP || bot >= head - 0.05) continue;
            int g = (int) b[i];
            double lx = f[g * 2], lz = f[g * 2 + 1];
            double inL = lx + hw - b[i + 1], inR = b[i + 4] - (lx - hw);
            double inB = lz + hw - b[i + 3], inF = b[i + 6] - (lz - hw);
            if (inL <= 0 || inR <= 0 || inB <= 0 || inF <= 0) continue;
            double penX = Math.min(inL, inR), penZ = Math.min(inB, inF);
            double pen = Math.min(penX, penZ);
            if (pen > 0.6 || pen <= bestPen) continue;
            bestPen = pen;
            bg = g;
            if (penX < penZ) { px = inL < inR ? -inL : inR; pz = 0; }
            else { px = 0; pz = inB < inF ? -inB : inF; }
        }
        if (bestPen <= 0) return null;
        // push vector in the group frame -> world (rotation only, horizontal part)
        double[] f0 = groupToWorld3(d, p, bg, f[bg * 2], feet, f[bg * 2 + 1]);
        double[] f1 = groupToWorld3(d, p, bg, f[bg * 2] + px, feet, f[bg * 2 + 1] + pz);
        return new double[]{f1[0] - f0[0], f1[2] - f0[2]};
    }

    /** Lowest box underside above the head region (feet+0.5 .. head+reach) over the footprint, world Y or NaN. */
    public static double ceiling(Deck d, double[] p, Entity e, double feetY, double reach) {
        double[] L = toLocal3(p, e.getX(), feetY, e.getZ());
        double[] u = up(p);
        double[] trig = groupTrig(d, p);
        double m = e.getBbWidth() * 0.3;
        double iu = 1.0 / Math.max(0.2, u[1]);
        float[] b = d.b;
        double best = Double.NaN;
        for (int i = 0; i < b.length; i += 7) {
            double t = (b[i + 2] - L[1]) * iu;
            if (t < 0.5 || t > e.getBbHeight() + reach) continue;
            int g = (int) b[i];
            double hx = L[0] + t * u[0], hz = L[2] + t * u[2];
            if (!inBox(b, i, gX(d, trig, g, hx, hz), gZ(d, trig, g, hx, hz), m)) continue;
            double bot = feetY + t;
            if (Double.isNaN(best) || bot < best) best = bot;
        }
        return best;
    }

    /** True if no box occupies the column [y, y+h) (host-local heights) at world point (wx, y, wz). */
    static boolean free(Deck d, double[] p, double wx, double wy, double wz, double h, double m) {
        double[] L = toLocal3(p, wx, wy, wz);
        double[] trig = groupTrig(d, p);
        double[] f = frames(d, p, L[0], L[2], trig);
        float[] b = d.b;
        for (int i = 0; i < b.length; i += 7) {
            int g = (int) b[i];
            if (!inBox(b, i, f[g * 2], f[g * 2 + 1], m)) continue;
            if (b[i + 5] > L[1] + 0.01 && b[i + 2] < L[1] + h) return false;
        }
        return true;
    }

    /**
     * Climbing onto armour/hulls (jump key against the side): the lowest box next to the body whose top is
     * 0.6..2.8 above the feet and has head room on top pulls the entity up onto it. Returns true if climbed.
     */
    public static boolean climb(Deck d, double[] p, Entity e) {
        double[] L = toLocal3(p, e.getX(), e.getY(), e.getZ());
        double feet = L[1];
        double[] trig = groupTrig(d, p);
        double[] f = frames(d, p, L[0], L[2], trig);
        double reach = e.getBbWidth() * 0.5 + 0.45;
        float[] b = d.b;
        double bestTop = Double.NaN;
        double[] tw = null;
        for (int i = 0; i < b.length; i += 7) {
            double top = b[i + 5];
            if (top <= feet + STEP || top > feet + 2.8) continue;
            if (b[i + 2] > feet + 1.6) continue;                            // overhang above the head, not a ledge
            int g = (int) b[i];
            double lx = f[g * 2], lz = f[g * 2 + 1];
            double cx = Mth.clamp(lx, b[i + 1], b[i + 4]), cz = Mth.clamp(lz, b[i + 3], b[i + 6]);
            if (Math.hypot(cx - lx, cz - lz) > reach) continue;
            if (!Double.isNaN(bestTop) && top >= bestTop) continue;
            // stand 0.35 m inside the box edge (or at its centre if it is narrow)
            double ix = b[i + 4] - b[i + 1] > 0.7 ? Mth.clamp(lx, b[i + 1] + 0.35, b[i + 4] - 0.35) : (b[i + 1] + b[i + 4]) * 0.5;
            double iz = b[i + 6] - b[i + 3] > 0.7 ? Mth.clamp(lz, b[i + 3] + 0.35, b[i + 6] - 0.35) : (b[i + 3] + b[i + 6]) * 0.5;
            double[] w = groupToWorld3(d, p, g, ix, top, iz);
            if (!free(d, p, w[0], w[1] + 0.02, w[2], e.getBbHeight(), -0.05)) continue;
            bestTop = top; tw = w;
        }
        if (tw == null) return false;
        e.setPos(tw[0], tw[1], tw[2]);
        e.setDeltaMovement(0, 0, 0);
        e.setOnGround(true);
        e.resetFallDistance();
        return true;
    }

    /** Carry a point from the host's previous pose to its current pose (translation, yaw / pitch / roll, turret yaw). */
    public static double[] carry(Deck d, double[] prev, double[] cur, int g, double x, double y, double z) {
        double[] l = toLocal3(prev, x, y, z);
        double[] t = toGroup(d, prev, g, l[0], l[2]);
        return groupToWorld3(d, cur, g, t[0], l[1], t[1]);
    }

    /**
     * One collision step for an entity relative to one host: carry (if it stood on this host last tick, on group
     * stoodGroup), push out of walls, stop at ceilings, then hold it on the surface. Returns the group it now
     * stands on, or -1.
     */
    public static int step(Entity e, Entity host, Deck d, @Nullable double[] prev, double[] cur, int stoodGroup) {
        if (prev != null && prev.length != cur.length) prev = null;
        if (stoodGroup >= 0 && prev != null) {
            double[] n = carry(d, prev, cur, stoodGroup, e.getX(), e.getY(), e.getZ());
            double dyaw = Mth.wrapDegrees(cur[3] - prev[3]) + (stoodGroup > 0 ? Mth.wrapDegrees(ang(cur, stoodGroup) - ang(prev, stoodGroup)) : 0);
            e.setPos(n[0], n[1], n[2]);
            if (dyaw != 0) {
                e.setYRot(e.getYRot() + (float) dyaw);
                e.setYHeadRot(e.getYHeadRot() + (float) dyaw);
                if (e instanceof LivingEntity le) le.yBodyRot += (float) dyaw;
            }
        }
        double feet = e.getY();
        for (int k = 0; k < 2; k++) {
            double[] w = wall(d, cur, e, feet);
            if (w == null) break;
            e.setPos(e.getX() + w[0], e.getY(), e.getZ() + w[1]);
        }
        Vec3 v = e.getDeltaMovement();
        if (v.y > 0) {
            double c = ceiling(d, cur, e, feet, v.y + 0.05);
            if (!Double.isNaN(c) && feet + e.getBbHeight() > c - 0.02 - v.y) {
                e.setPos(e.getX(), Math.max(feet - 0.0, c - e.getBbHeight() - 0.02), e.getZ());
                e.setDeltaMovement(v.x, 0, v.z);
                v = e.getDeltaMovement();
                feet = e.getY();
            }
        }
        if (v.y > 0.05) return -1;                                       // jumping off / climbing
        double[] s = surface(d, cur, e, feet, stoodGroup >= 0 ? 0.9 : 0.45 + Math.max(0, -v.y));
        if (s == null) return -1;
        e.setPos(e.getX(), s[0], e.getZ());
        e.setDeltaMovement(v.x, 0, v.z);
        e.setOnGround(true);
        e.resetFallDistance();
        return (int) s[1];
    }

    public static List<Entity> hostsNear(net.minecraft.world.level.Level level, AABB box) {
        return level.getEntities((Entity) null, box, h -> !h.isRemoved() && shape(h) != null);
    }

    /** Server: mobs and dropped items on decks; players get the flying-kick counter reset (they move on the client). */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.level instanceof ServerLevel sl)) return;
        for (Entity host : sl.getAllEntities()) {
            Deck d;
            if (host.isRemoved() || (d = shape(host)) == null) continue;
            double[] cur = pose(host);
            double[] prev = PREV_SERVER.put(host, cur);
            AABB box = new AABB(host.position(), host.position()).inflate(d.radius + 2, Math.max(16, d.top + 4), d.radius + 2);
            for (Entity e : sl.getEntities(host, box, x -> x.isAlive() && !x.isPassenger() && !x.isVehicle() && x != host
                    && (x instanceof LivingEntity || x instanceof ItemEntity) && shape(x) == null && !x.isSpectator())) {
                if (e instanceof ServerPlayer sp) {
                    if (surface(d, cur, sp, sp.getY(), 0.6) != null) resetFlyKick(sp);
                    continue;
                }
                if (e instanceof Player) continue;
                boolean was = ON_SERVER.get(e) == host;
                int g = step(e, host, d, prev, cur, was ? GRP_SERVER.getOrDefault(e, 0) : -1);
                if (g >= 0) {
                    ON_SERVER.put(e, host);
                    GRP_SERVER.put(e, g);
                    e.hurtMarked = true;
                } else if (was) {
                    ON_SERVER.remove(e);
                }
            }
        }
    }

    /** Dedicated servers kick players "flying" for 80 ticks with only air below; a vehicle deck is solid ground. */
    private static void resetFlyKick(ServerPlayer sp) {
        if (fieldFailed || sp.connection == null) return;
        try {
            if (aboveGround == null) {
                aboveGround = ObfuscationReflectionHelper.findField(net.minecraft.server.network.ServerGamePacketListenerImpl.class, "f_9737_");
            }
            aboveGround.setInt(sp.connection, 0);
        } catch (Throwable t) {
            fieldFailed = true;
            org.slf4j.LoggerFactory.getLogger("aiarsenal").warn("deck: cannot reset aboveGroundTickCount: {}", t.toString());
        }
    }
}
