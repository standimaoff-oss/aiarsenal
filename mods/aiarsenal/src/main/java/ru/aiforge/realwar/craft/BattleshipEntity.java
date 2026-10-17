package ru.aiforge.realwar.craft;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.blast.ModDamage;
import ru.aiforge.realwar.ordnance.AirTargets;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.ordnance.BulletManager;
import ru.aiforge.realwar.ordnance.ShellEntity;
import ru.aiforge.realwar.registry.ModSounds;

/**
 * Schlachtschiff Bismarck (reply 10). Hull physics are the shared {@link BoatEntity} column model; this class adds the
 * fire control of 38 independently trained mounts.
 * <ul>
 *   <li><b>Director:</b> the helmsman's line of sight is the target designation. The aim point is the first block /
 *   water surface along the sight within the loaded world (&lt;= 600 blocks), otherwise the sight line's intersection
 *   with the sea surface (out to the maximum range of the 38 cm guns), above the horizon = maximum range.</li>
 *   <li><b>38 cm / 15 cm:</b> every turret computes its own bearing to the aim point, is clamped to its firing arc
 *   (A/B: 145 deg either side of the bow, C/D: of the stern, wing 15 cm turrets: their own beam) and slews at the real
 *   training / elevating speed. Elevation is solved from the projectile's drag ballistics (range tables built once from
 *   {@link Ballistics#range}). LMB fires a half-salvo: one gun of every turret that bears and is laid; reload 26 s
 *   per 38 cm gun, 7.5 s per 15 cm gun.</li>
 *   <li><b>Flak:</b> 10.5 cm (time-fuzed bursts at the predicted point), 3.7 cm and 2 cm (tracer streams) engage air
 *   targets automatically while somebody is at the helm, each mount within its own arc and range.</li>
 *   <li><b>Arado Ar 196:</b> launched from the catapult, spots the fall of shot (dispersion halved while it is over the
 *   target area), attacks hostile mobs there with 2 x 20 mm and 2 x SC 50, lands next to the ship and is craned back
 *   aboard.</li>
 * </ul>
 */
public class BattleshipEntity extends BoatEntity {
    public enum MK { MAIN, SEC, H, F, L, Q }

    /**
     * One mount in model space (glTF: +X left/port, +Y up, +Z forward; metres from the keel amidships).
     * yaw pivot (x,y,z); pitch pivot at rest (px,py,pz); guns = lateral offsets of the barrels in the node frame;
     * len = barrel length ahead of the pitch pivot; aft = mesh built facing aft (node rotation = az - 180).
     */
    public record Mount(String tag, MK kind, double x, double y, double z, double px, double py, double pz, double[] guns,
                        double len, boolean aft, float arcC, float arcH, float elMin, float elMax, float slewAz, float slewEl) {
        public float rest() { return this.aft ? 180f : 0f; }

        public float clampAz(float az) {
            float d = Mth.clamp(Mth.wrapDegrees(az - this.arcC), -this.arcH, this.arcH);
            return Mth.wrapDegrees(this.arcC + d);
        }

        public boolean inArc(float az) {
            return Math.abs(Mth.wrapDegrees(az - this.arcC)) <= this.arcH + 0.01f;
        }
    }

    public static final Mount[] M;
    public static final int N_MAIN = 4, N_SEC = 6, I_SEC = 4, I_H = 10, I_F = 18, I_L = 26, I_Q = 36;
    /** FX event codes: 0-7 main guns, 8-19 15 cm guns, 20-27 10.5 cm, 28-35 3.7 cm, 50 catapult. */
    public static final int EV_MAIN = 0, EV_SEC = 8, EV_H = 20, EV_F = 28, EV_CAT = 50;
    public static final int RELOAD_MAIN = 520, RELOAD_SEC = 150;
    public static final double RANGE_H = 400, RANGE_F = 250, RANGE_L = 150;

    static {
        List<Mount> l = new ArrayList<>();
        double[] g38 = {1.9, -1.9}, g38a = {-1.9, 1.9};
        l.add(new Mount("tA", MK.MAIN, 0, 17.0, 70.3, 0, 19.7, 74.6, g38, 15.75, false, 0f, 145f, -5.5f, 30f, 0.27f, 0.3f));
        l.add(new Mount("tB", MK.MAIN, 0, 19.3, 51.8, 0, 22.0, 56.1, g38, 15.75, false, 0f, 145f, -5.5f, 30f, 0.27f, 0.3f));
        l.add(new Mount("tC", MK.MAIN, 0, 19.2, -57.5, 0, 21.9, -61.8, g38a, 15.75, true, 180f, 145f, -5.5f, 30f, 0.27f, 0.3f));
        l.add(new Mount("tD", MK.MAIN, 0, 17.1, -76.0, 0, 19.8, -80.3, g38a, 15.75, true, 180f, 145f, -5.5f, 30f, 0.27f, 0.3f));
        double[][] sec = {{14.7, 15.75, -22.5, -24.8, 1}, {-14.7, 15.75, -22.5, -24.8, 1}, {14.7, 15.75, 7.0, 9.3, 0},
                {-14.7, 15.75, 7.0, 9.3, 0}, {10.3, 20.0, 27.5, 29.8, 0}, {-10.3, 20.0, 27.5, 29.8, 0}};
        for (int i = 0; i < sec.length; i++) {
            double[] s = sec[i];
            boolean aft = s[4] > 0;
            l.add(new Mount("s" + i, MK.SEC, s[0], s[1], s[2], s[0], s[1] + 1.6, s[3], aft ? new double[]{-0.95, 0.95} : new double[]{0.95, -0.95},
                    7.3, aft, s[0] > 0 ? 90f : -90f, 90f, -10f, 40f, 0.45f, 0.4f));
        }
        double[][] h = {{10, -33.5}, {-10, -33.5}, {11, -17.5}, {-11, -17.5}, {11, -1.0}, {-11, -1.0}, {11.3, 19.0}, {-11.3, 19.0}};
        for (int i = 0; i < h.length; i++)
            l.add(new Mount("h" + i, MK.H, h[i][0], 19.6, h[i][1], h[i][0], 21.15, h[i][1] + 0.3, new double[]{0.33, -0.33}, 6.0, false,
                    h[i][0] > 0 ? 90f : -90f, 100f, -8f, 80f, 0.6f, 0.5f));
        double[][] f = {{7.4, 19.6, -46.9}, {-7.4, 19.6, -46.9}, {5.0, 22.4, -37.0}, {-5.0, 22.4, -37.0}, {14.6, 19.6, -5.5}, {-14.6, 19.6, -5.5},
                {3.0, 26.6, 21.0}, {-3.0, 26.6, 21.0}};
        for (int i = 0; i < f.length; i++)
            l.add(new Mount("f" + i, MK.F, f[i][0], f[i][1], f[i][2], f[i][0], f[i][1] + 1.15, f[i][2] + 0.1, new double[]{0.2, -0.2}, 2.75, false,
                    f[i][0] > 0 ? 90f : -90f, 105f, -10f, 85f, 1.25f, 1.0f));
        double[][] lp = {{11.6, -28.5}, {-11.6, -28.5}, {11.8, -12.5}, {-11.8, -12.5}, {9.5, 13.0}, {-9.5, 13.0}, {7.0, 36.0}, {-7.0, 36.0},
                {4.0, 39.5}, {-4.0, 39.5}};
        for (int i = 0; i < lp.length; i++)
            l.add(new Mount("l" + i, MK.L, lp[i][0], 19.6, lp[i][1], lp[i][0], 20.55, lp[i][1] + 0.1, new double[]{0.0}, 1.8, false,
                    lp[i][0] > 0 ? 90f : -90f, 115f, -10f, 88f, 2.0f, 1.6f));
        for (int i = 0; i < 2; i++) {
            double x = i == 0 ? 6.1 : -6.1;
            l.add(new Mount("q" + i, MK.Q, x, 30.4, 19.0, x, 31.4, 19.0, new double[]{0.28, -0.28}, 1.9, false,
                    x > 0 ? 90f : -90f, 125f, -10f, 90f, 2.0f, 1.6f));
        }
        M = l.toArray(new Mount[0]);
    }

    private static final EntityDataAccessor<CompoundTag> MOUNTS = SynchedEntityData.defineId(BattleshipEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Integer> AIR = SynchedEntityData.defineId(BattleshipEntity.class, EntityDataSerializers.INT);

    /** Current mount angles (deg): server = authoritative, client = interpolated towards the synced values. */
    public final float[] az = new float[M.length], el = new float[M.length];
    public final float[] azO = new float[M.length], elO = new float[M.length];
    /** Gun reload timers (ticks): 0-7 main (turret*2+gun), 8-19 secondary. */
    public final int[] reload = new int[20];
    public final float[] recoil = new float[20];
    private final int[] nextGun = new int[N_MAIN + N_SEC];
    private final int[] mcd = new int[M.length];
    private final int[] aaTarget = new int[M.length];
    private final List<double[]> bursts = new ArrayList<>();
    private List<Entity> airContacts = new ArrayList<>();
    private int aaScan;
    private int catapultCd;
    /** Director state (server): aim point and whether it lies beyond the main battery's reach. */
    @Nullable public Vec3 aimPoint;
    /** Gametests: director target used when nobody is on the bridge (also wakes the flak). */
    @Nullable public Vec3 testAim;
    public boolean outOfRange;
    /** Client: server fire-control state - bit i = mount i laid on the aim point, bit 31 = beyond max range. */
    public int laidMask;
    private float[] wantAz = new float[N_MAIN + N_SEC], wantEl = new float[N_MAIN + N_SEC];
    private boolean[] wantOk = new boolean[N_MAIN + N_SEC];

    public BattleshipEntity(EntityType<? extends BattleshipEntity> type, Level level, CraftKind kind) {
        super(type, level, kind);
        for (int i = 0; i < M.length; i++) { this.az[i] = this.azO[i] = M[i].rest(); this.aaTarget[i] = -1; }
        this.setMaxUpStep(0.0f);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(MOUNTS, new CompoundTag());
        this.entityData.define(AIR, 0);
    }

    public int aradosAirborne() { return this.entityData.get(AIR); }

    void setAirborne(int n) { this.entityData.set(AIR, Math.max(0, n)); }

    /** Deck collision group rotation (deg) for the walkable decks: groups 1-4 main turrets, 5-10 secondary turrets. */
    public double deckGroupAngle(int g) {
        if (g < 1 || g > N_MAIN + N_SEC) return 0;
        int i = g - 1;
        return this.az[i] - M[i].rest();
    }

    // ------------------------------------------------------------------------------------------ geometry

    /** World -> model-local (glTF: x left, y up, z forward) relative to the keel origin. */
    public Vec3 toLocal(Vec3 w) {
        Vec3[] b = this.basis();
        Vec3 v = w.subtract(this.position());
        return new Vec3(-v.dot(b[2]), v.dot(b[1]), v.dot(b[0]));
    }

    public Vec3 localDirToWorld(double dx, double dy, double dz) {
        Vec3[] b = this.basis();
        return b[0].scale(dz).add(b[1].scale(dy)).add(b[2].scale(-dx)).normalize();
    }

    /** Local barrel direction for az (+left) / el. */
    public static Vec3 localDir(float azDeg, float elDeg) {
        double a = Math.toRadians(azDeg), e = Math.toRadians(elDeg);
        return new Vec3(Math.cos(e) * Math.sin(a), Math.sin(e), Math.cos(e) * Math.cos(a));
    }

    /** Model-local muzzle of gun gi of mount i at the given angles. */
    public static Vec3 muzzleLocal(int i, int gi, float azDeg, float elDeg, double back) {
        Mount m = M[i];
        double yaw = Math.toRadians(azDeg - m.rest());
        double pit = Math.toRadians(m.aft ? elDeg : -elDeg);
        double sign = m.aft ? -1 : 1;
        double gx = m.guns[Math.min(gi, m.guns.length - 1)];
        // in the pitch frame: barrel end
        double bx = gx, by = 0, bz = sign * (m.len - back);
        double ry = by * Math.cos(pit) - bz * Math.sin(pit), rz = by * Math.sin(pit) + bz * Math.cos(pit);
        // pitch pivot offset from the yaw pivot (node frame at rest), then the barrel
        double nx = (m.px - m.x) + bx, ny = (m.py - m.y) + ry, nz = (m.pz - m.z) + rz;
        double c = Math.cos(yaw), s = Math.sin(yaw);
        return new Vec3(m.x + nx * c + nz * s, m.y + ny, m.z - nx * s + nz * c);
    }

    // ------------------------------------------------------------------------------------------ ballistics

    private static final Map<Ballistics.Profile, double[]> TABLES = new EnumMap<>(Ballistics.Profile.class);

    /** Range (physical m) at sea level for elevations 0, 0.1, ... elMax deg. */
    static synchronized double[] table(Ballistics.Profile p, float elMax) {
        return TABLES.computeIfAbsent(p, k -> {
            int n = (int) Math.round(elMax * 10) + 1;
            double[] r = new double[n];
            for (int i = 0; i < n; i++) r[i] = Ballistics.range(k, i * 0.1, 0.0);
            for (int i = 1; i < n; i++) r[i] = Math.max(r[i], r[i - 1]);
            return r;
        });
    }

    public static double maxRange(Ballistics.Profile p, float elMax) {
        double[] t = table(p, elMax);
        return t[t.length - 1];
    }

    /**
     * Gun elevation (deg) to hit a point dPhys metres away horizontally and dyPhys above the muzzle; NaN beyond range.
     * Sea-level range table + the line-of-sight angle (exact at long range, where dy is negligible, and at short range,
     * where the trajectory is flat).
     */
    public static double elevationFor(Ballistics.Profile p, float elMax, double dPhys, double dyPhys) {
        double[] t = table(p, elMax);
        if (dPhys > t[t.length - 1]) return Double.NaN;
        double e0 = 0;
        for (int i = 1; i < t.length; i++) {
            if (t[i] >= dPhys) {
                double k = (dPhys - t[i - 1]) / Math.max(1e-6, t[i] - t[i - 1]);
                e0 = (i - 1 + Mth.clamp(k, 0, 1)) * 0.1;
                break;
            }
        }
        return e0 + Math.toDegrees(Math.atan2(dyPhys, Math.max(1.0, dPhys)));
    }

    public Ballistics.Profile mainProfile() {
        int li = this.weaponFor(0);
        return li >= 0 && this.kind.loads[li].weapon() == CraftWeapon.SK38_HE ? Ballistics.Profile.SK38_HE : Ballistics.Profile.SK38_AP;
    }

    /** Max reach of the main battery in blocks (Froude-scaled world, see RWConfig scaleK). */
    public static double mainReachBlocks() {
        return maxRange(Ballistics.Profile.SK38_AP, 30f) / Ballistics.scaleK();
    }

    /**
     * Director aim point along an observer's line of sight (both sides: the server lays the guns, the HUD shows the
     * same range): blocks / water in loaded chunks first, then the sea surface, then maximum range.
     */
    public Vec3 aimAlong(Vec3 eye, Vec3 look) {
        Level lv = this.level();
        double loaded = 0;
        for (double d = 8; d <= 600; d += 8) {
            Vec3 p = eye.add(look.scale(d));
            if (!lv.hasChunkAt(BlockPos.containing(p))) break;
            loaded = d;
        }
        if (loaded > 30) {
            // start the ray clear of our own superstructure (blocks only, the ship itself is an entity)
            BlockHitResult h = lv.clip(new ClipContext(eye, eye.add(look.scale(loaded)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
            if (h.getType() != HitResult.Type.MISS && h.getLocation().distanceTo(eye) > 12) return h.getLocation();
        }
        double waterY = this.getY() + this.kind.water.draft();
        double reach = mainReachBlocks();
        Vec3 hz = new Vec3(look.x, 0, look.z);
        if (hz.lengthSqr() < 1e-6) hz = this.basis()[0];
        hz = hz.normalize();
        if (look.y < -1e-4) {
            double t = (waterY - eye.y) / look.y;
            Vec3 p = eye.add(look.scale(t));
            double hd = Math.hypot(p.x - eye.x, p.z - eye.z);
            if (hd <= reach) return p;
        }
        return new Vec3(eye.x + hz.x * reach * 1.02, waterY, eye.z + hz.z * reach * 1.02);
    }

    // ------------------------------------------------------------------------------------------ tick

    @Override
    public void tick() {
        System.arraycopy(this.az, 0, this.azO, 0, M.length);
        System.arraycopy(this.el, 0, this.elO, 0, M.length);
        super.tick();
        for (int i = 0; i < this.recoil.length; i++) this.recoil[i] = Math.max(0, this.recoil[i] - 0.06f);
        if (this.level().isClientSide) {
            CompoundTag t = this.entityData.get(MOUNTS);
            int[] a = t.getIntArray("a");
            if (a.length == M.length) {
                for (int i = 0; i < M.length; i++) {
                    float ta = (short) (a[i] >> 16) / 10f, te = (short) (a[i] & 0xFFFF) / 10f;
                    this.az[i] += Mth.wrapDegrees(ta - this.az[i]) * 0.6f;
                    this.el[i] += (te - this.el[i]) * 0.6f;
                }
            }
            int[] r = t.getIntArray("r");
            if (r.length == this.reload.length) System.arraycopy(r, 0, this.reload, 0, r.length);
            this.laidMask = t.getInt("l");
        }
    }

    @Override
    protected void updateTurretAim() {
        // replaced by updateBatteries (the base single-turret aim does not apply)
    }

    @Override
    protected void serverTick() {
        super.serverTick();
        if (!(this.level() instanceof ServerLevel sl)) return;
        for (int i = 0; i < this.reload.length; i++) if (this.reload[i] > 0) this.reload[i]--;
        for (int i = 0; i < M.length; i++) if (this.mcd[i] > 0) this.mcd[i]--;
        if (this.catapultCd > 0) this.catapultCd--;
        Player op = this.pilot();
        this.updateBatteries(op);
        if (op != null || this.testAim != null) this.updateAA(sl, op);
        else for (int i = I_H; i < M.length; i++) this.slew(i, M[i].rest(), 0f);
        this.tickBursts(sl);
        this.publishMounts();
    }

    private void publishMounts() {
        int[] a = new int[M.length];
        for (int i = 0; i < M.length; i++) a[i] = (((short) Math.round(Mth.wrapDegrees(this.az[i]) * 10)) << 16) | (((short) Math.round(this.el[i] * 10)) & 0xFFFF);
        int lm = this.outOfRange ? 1 << 31 : 0;
        for (int i = 0; i < N_MAIN + N_SEC; i++) if (this.laid(i)) lm |= 1 << i;
        CompoundTag old = this.entityData.get(MOUNTS);
        int[] oa = old.getIntArray("a"), or = old.getIntArray("r");
        if (java.util.Arrays.equals(oa, a) && java.util.Arrays.equals(or, this.reload) && old.getInt("l") == lm && this.tickCount % 20 != 0) return;
        CompoundTag t = new CompoundTag();
        t.putIntArray("a", a);
        t.putIntArray("r", this.reload.clone());
        t.putInt("l", lm);
        this.entityData.set(MOUNTS, t);
    }

    /** Slew mount i towards (az, el) inside its arc at its training / elevating rate. Returns the remaining error. */
    private float slew(int i, float tAz, float tEl) {
        Mount m = M[i];
        if (i < N_MAIN + N_SEC && ru.aiforge.realwar.damage.CraftDamage.mountOut(this, i)) {
            // r18: knocked-out mount - jammed in train, the remaining barrel droops
            this.el[i] = this.el[i] + Mth.clamp(m.elMin - this.el[i], -m.slewEl, m.slewEl);
            return 999f;
        }
        float cur = Mth.wrapDegrees(this.az[i] - m.arcC);
        float tgt = Mth.clamp(Mth.wrapDegrees(tAz - m.arcC), -m.arcH, m.arcH);
        float nd = cur + Mth.clamp(tgt - cur, -m.slewAz, m.slewAz);
        this.az[i] = Mth.wrapDegrees(m.arcC + nd);
        float te = Mth.clamp(tEl, m.elMin, m.elMax);
        this.el[i] = this.el[i] + Mth.clamp(te - this.el[i], -m.slewEl, m.slewEl);
        return Math.max(Math.abs(Mth.wrapDegrees(tAz - this.az[i])), Math.abs(tEl - this.el[i]));
    }

    private void updateBatteries(@Nullable Player op) {
        if (op == null && this.testAim == null) {
            for (int i = 0; i < N_MAIN + N_SEC; i++) { this.slew(i, M[i].rest(), 0f); this.wantOk[i] = false; }
            this.aimPoint = null;
            return;
        }
        if (op == null) this.aimPoint = this.testAim;
        else if (this.tickCount % 4 == 0 || this.aimPoint == null) this.aimPoint = this.aimAlong(op.getEyePosition(), op.getLookAngle());
        Vec3 tl = this.toLocal(this.aimPoint);
        double k = Ballistics.scaleK();
        Ballistics.Profile mp = this.mainProfile();
        boolean anyOut = false;
        for (int i = 0; i < N_MAIN + N_SEC; i++) {
            Mount m = M[i];
            double dx = tl.x - m.px, dz = tl.z - m.pz, dy = tl.y - m.py;
            float a = (float) Math.toDegrees(Math.atan2(dx, dz));
            double d = Math.sqrt(dx * dx + dz * dz);
            double e = m.kind == MK.MAIN ? elevationFor(mp, m.elMax, d * k, dy * k) : elevationFor(Ballistics.Profile.SK15_HE, m.elMax, d * k, dy * k);
            boolean out = Double.isNaN(e);
            if (m.kind == MK.MAIN && out) anyOut = true;
            float te = out ? m.elMax : (float) e;
            this.wantAz[i] = a;
            this.wantEl[i] = te;
            this.wantOk[i] = !out && m.inArc(a) && te >= m.elMin - 0.01f && te <= m.elMax;
            this.slew(i, a, te);
        }
        this.outOfRange = anyOut;
    }

    /** True if mount i (main / secondary) bears on the aim point and is laid within tolerance. */
    public boolean laid(int i) {
        if (i >= N_MAIN + N_SEC || !this.wantOk[i] || ru.aiforge.realwar.damage.CraftDamage.mountOut(this, i)) return false;
        return Math.abs(Mth.wrapDegrees(this.wantAz[i] - this.az[i])) < 0.8f && Math.abs(this.wantEl[i] - this.el[i]) < 0.5f;
    }

    /** Client / HUD: is turret i's synced lay usable (approximate - server decides). */
    public boolean spotted() {
        if (this.aimPoint == null) return false;
        for (AradoEntity a : this.level().getEntitiesOfClass(AradoEntity.class, new AABB(this.aimPoint, this.aimPoint).inflate(500, 400, 500)))
            if (a.ownerId() == this.getId() && a.airborne()) return true;
        return false;
    }

    // ------------------------------------------------------------------------------------------ firing (helm)

    @Override
    public boolean canUse(int seat, int load) {
        if (seat != 0) return false;
        CraftWeapon w = this.kind.loads[load].weapon();
        return w == CraftWeapon.SK38_AP || w == CraftWeapon.SK38_HE || w == CraftWeapon.SK15 || w == CraftWeapon.ARADO;
    }

    @Override
    protected void tryFire(int seat) {
        if (seat != 0) return;
        int li = this.weaponFor(0);
        if (li < 0 || this.cooldown[li] > 0) return;
        CraftWeapon w = this.kind.loads[li].weapon();
        boolean auto = w == CraftWeapon.SK15;
        if (!auto && this.fireLatch[seat]) return;
        this.fireLatch[seat] = true;
        this.cooldown[li] = w.interval;
        if (!(this.level() instanceof ServerLevel sl)) return;
        int fired = switch (w) {
            case SK38_AP, SK38_HE -> this.fireMain(sl, li);
            case SK15 -> this.fireSecondary(sl, li);
            case ARADO -> this.launchArado(sl, li) ? 1 : 0;
            default -> 0;
        };
        if (fired == 0 && !auto) this.level().playSound(null, this.getX(), this.getY() + 30, this.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.NEUTRAL, 1f, 0.6f);
    }

    /** r11: full salvo - BOTH guns of every 38 cm turret that bears, is laid and loaded fire together. */
    public int fireMain(ServerLevel sl, int li) {
        int[] ammo = this.ammoArray();
        Ballistics.Profile p = this.kind.loads[li].weapon() == CraftWeapon.SK38_HE ? Ballistics.Profile.SK38_HE : Ballistics.Profile.SK38_AP;
        double disp = this.spotted() ? 0.5 : 1.0;
        int fired = 0;
        for (int t = 0; t < N_MAIN && ammo[li] > 0; t++) {
            if (!this.laid(t)) continue;
            for (int g = 0; g < 2 && ammo[li] > 0; g++) {
                if (this.reload[t * 2 + g] > 0) continue;
                this.reload[t * 2 + g] = RELOAD_MAIN;
                this.recoil[t * 2 + g] = 1f;
                this.shoot(sl, t, g, p, 0.0035 * disp, 0.0022 * disp);
                ammo[li]--;
                fired++;
                sl.broadcastEntityEvent(this, (byte) (160 + EV_MAIN + t * 2 + g));
            }
        }
        if (fired > 0) {
            this.setAmmoArray(ammo);
            this.level().playSound(null, this.getX(), this.getY() + 22, this.getZ(), ModSounds.CANNON_HOWITZER.get(), SoundSource.NEUTRAL, 24f, 0.45f);
            this.level().playSound(null, this.getX(), this.getY() + 22, this.getZ(), ModSounds.BLAST_LARGE.get(), SoundSource.NEUTRAL, 12f, 0.6f);
            // recoil heel: a broadside rolls the ship a fraction of a degree
            this.rateP += (float) (fired * 0.12 * Math.signum(Mth.wrapDegrees(this.az[0])));
        }
        return fired;
    }

    public int fireSecondary(ServerLevel sl, int li) {
        int[] ammo = this.ammoArray();
        double disp = this.spotted() ? 0.5 : 1.0;
        int fired = 0;
        for (int s = 0; s < N_SEC && ammo[li] > 0; s++) {
            int i = I_SEC + s;
            if (!this.laid(i)) continue;
            int r0 = N_MAIN * 2 + s * 2;
            for (int g = 0; g < 2 && ammo[li] > 0; g++) {      // r11: both 15 cm barrels together
                if (this.reload[r0 + g] > 0) continue;
                this.reload[r0 + g] = RELOAD_SEC;
                this.recoil[r0 + g] = 1f;
                this.shoot(sl, i, g, Ballistics.Profile.SK15_HE, 0.004 * disp, 0.003 * disp);
                ammo[li]--;
                fired++;
                sl.broadcastEntityEvent(this, (byte) (160 + EV_SEC + s * 2 + g));
            }
        }
        if (fired > 0) {
            this.setAmmoArray(ammo);
            this.level().playSound(null, this.getX(), this.getY() + 18, this.getZ(), ModSounds.CANNON_HOWITZER.get(), SoundSource.NEUTRAL, 12f, 0.75f);
        }
        return fired;
    }

    private void shoot(ServerLevel sl, int i, int g, Ballistics.Profile p, double sAz, double sEl) {
        float a = this.az[i] + (float) Math.toDegrees(this.random.nextGaussian() * sAz);
        float e = this.el[i] + (float) Math.toDegrees(this.random.nextGaussian() * sEl);
        Vec3 ml = muzzleLocal(i, g, this.az[i], this.el[i], 0);
        Vec3 dl = localDir(a, e);
        Vec3 m = this.localToWorld(ml.x, ml.y, ml.z);
        Vec3 d = this.localDirToWorld(dl.x, dl.y, dl.z);
        ShellEntity.fire(sl, p, m.add(d.scale(0.5)), d, this.getId());
    }

    // ------------------------------------------------------------------------------------------ Arado

    public boolean launchArado(ServerLevel sl, int li) {
        int[] ammo = this.ammoArray();
        if (ammo[li] <= 0 || this.aradosAirborne() > 0 || this.catapultCd > 0) return false;
        // the double-ended catapult trains to the side nearer the target (aim point), launching into the wind of its own run
        float side = this.aimPoint != null && this.toLocal(this.aimPoint).x < 0 ? -1f : 1f;
        Vec3 p = this.localToWorld(side * 6.0, 21.2, -10.0);
        Vec3 dir = this.localDirToWorld(side, 0, 0);
        AradoEntity a = AradoEntity.launch(sl, this, p, dir);
        if (a == null) return false;
        ammo[li]--;
        this.setAmmoArray(ammo);
        this.setAirborne(this.aradosAirborne() + 1);
        this.catapultCd = 200;
        sl.broadcastEntityEvent(this, (byte) (160 + EV_CAT));
        this.level().playSound(null, p.x, p.y, p.z, ModSounds.HYDRAULIC.get(), SoundSource.NEUTRAL, 4f, 0.6f);
        this.level().playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 2.5f, 1.6f);
        return true;
    }

    /** Called by a landed Arado alongside: craned back aboard, refuelled and rearmed. */
    void recoverArado() {
        int li = this.kind.loadIndex(CraftWeapon.ARADO);
        int[] ammo = this.ammoArray();
        ammo[li] = Math.min(this.kind.loads[li].full(), ammo[li] + 1);
        this.setAmmoArray(ammo);
        this.setAirborne(this.aradosAirborne() - 1);
    }

    void aradoLost() {
        this.setAirborne(this.aradosAirborne() - 1);
    }

    // ------------------------------------------------------------------------------------------ flak

    private boolean ownAir(Entity e) {
        return e instanceof AradoEntity a && a.ownerId() == this.getId();
    }

    private void updateAA(ServerLevel sl, Player op) {
        Vec3 c = this.localToWorld(0, 25, 0);
        if (--this.aaScan <= 0) {
            this.aaScan = 10;
            this.airContacts = sl.getEntities(this, new AABB(c, c).inflate(RANGE_H + 20), e -> e != op && e.getVehicle() != this
                    && !e.hasPassenger(op) && !this.ownAir(e) && AirTargets.isAirTarget(e) && e.getY() > c.y - 10);
        }
        int[] ammo = this.ammoArray();
        int liH = this.kind.loadIndex(CraftWeapon.FLAK105), liF = this.kind.loadIndex(CraftWeapon.FLAK37), liL = this.kind.loadIndex(CraftWeapon.FLAK20);
        boolean changed = false;
        boolean soundH = false, soundF = false, soundL = false;
        for (int i = I_H; i < M.length; i++) {
            Mount m = M[i];
            double range = m.kind == MK.H ? RANGE_H : m.kind == MK.F ? RANGE_F : RANGE_L;
            int li = m.kind == MK.H ? liH : m.kind == MK.F ? liF : liL;
            Vec3 base = this.localToWorld(m.px, m.py, m.pz);
            Entity best = null;
            double bd = range * range;
            float bAz = 0, bEl = 0;
            Vec3 bLead = null;
            for (Entity e : this.airContacts) {
                if (!e.isAlive()) continue;
                double dd = e.distanceToSqr(base);
                if (dd > bd || dd < 9) continue;
                Vec3 lead = m.kind == MK.H ? leadPoint(base, e, 6.0) : leadPoint(base, e, 10.0);
                Vec3 ll = this.toLocal(lead);
                double dx = ll.x - m.px, dy = ll.y - m.py, dz = ll.z - m.pz;
                float a = (float) Math.toDegrees(Math.atan2(dx, dz));
                float ev = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                if (!m.inArc(a) || ev < 3f || ev > m.elMax) continue;
                best = e; bd = dd; bAz = a; bEl = ev; bLead = lead;
            }
            if (best == null) {
                this.slew(i, m.rest(), 0f);
                this.aaTarget[i] = -1;
                continue;
            }
            this.aaTarget[i] = best.getId();
            float err = this.slew(i, bAz, bEl);
            if (err > 2.5f || this.mcd[i] > 0 || ammo[li] <= 0) continue;
            // r11: twin mounts (10.5 cm SK C/33, 3.7 cm SK C/30) fire both barrels together, at the per-barrel rate
            boolean twin = (m.kind == MK.H || m.kind == MK.F) && m.guns.length >= 2;
            int g0 = twin ? 0 : (this.tickCount + i) % m.guns.length;
            for (int g = g0; g < (twin ? m.guns.length : g0 + 1) && ammo[li] > 0; g++) {
            Vec3 ml = muzzleLocal(i, g, this.az[i], this.el[i], 0);
            Vec3 mw = this.localToWorld(ml.x, ml.y, ml.z);
            Vec3 dl = localDir(this.az[i], this.el[i]);
            Vec3 dw = this.localDirToWorld(dl.x, dl.y, dl.z);
            switch (m.kind) {
                case H -> {
                    this.mcd[i] = twin ? 40 : 20;                    // twin, ~15 rds/min per barrel
                    double dist = bLead.distanceTo(mw);
                    double sig = 0.025 * dist + 1.5;
                    Vec3 bp = bLead.add(this.random.nextGaussian() * sig, this.random.nextGaussian() * sig * 0.6, this.random.nextGaussian() * sig);
                    this.bursts.add(new double[]{bp.x, bp.y, bp.z, this.tickCount + Math.max(2, dist / 6.0)});
                    sl.broadcastEntityEvent(this, (byte) (160 + EV_H + (i - I_H)));
                    soundH = true;
                }
                case F -> {
                    this.mcd[i] = twin ? 20 : 10;                    // twin SK C/30, ~30 rds/min per barrel
                    Vec3 dd = dw.add(this.random.nextGaussian() * 0.004, this.random.nextGaussian() * 0.004, this.random.nextGaussian() * 0.004).normalize();
                    BulletManager.spawn(sl, mw.add(dd.scale(0.3)), dd.scale(10.0), this.getId(), true);
                    sl.broadcastEntityEvent(this, (byte) (160 + EV_F + (i - I_F)));
                    soundF = true;
                }
                default -> {
                    this.mcd[i] = m.kind == MK.Q ? 2 : 4;            // Flak 38 ~ 420 rds/min practical 220; Vierling 4 barrels
                    Vec3 dd = dw.add(this.random.nextGaussian() * 0.005, this.random.nextGaussian() * 0.005, this.random.nextGaussian() * 0.005).normalize();
                    BulletManager.spawn(sl, mw.add(dd.scale(0.3)), dd.scale(10.0), this.getId(), (this.tickCount + i) % 3 == 0);
                    soundL = true;
                }
            }
            ammo[li]--;
            changed = true;
            }
        }
        if (changed) this.setAmmoArray(ammo);
        if (soundH) sl.playSound(null, c.x, c.y, c.z, ModSounds.CANNON_TANK.get(), SoundSource.NEUTRAL, 8f, 0.85f + this.random.nextFloat() * 0.1f);
        if (soundF && this.tickCount % 2 == 0) sl.playSound(null, c.x, c.y, c.z, ModSounds.CRAFT_CANNON.get(), SoundSource.NEUTRAL, 6f, 0.7f);
        if (soundL && this.tickCount % 3 == 0) sl.playSound(null, c.x, c.y, c.z, ModSounds.FLAK_BURST.get(), SoundSource.NEUTRAL, 6f, 1.2f);
    }

    /** Lead point for a projectile of v blocks/tick (two iterations on the target's current velocity). */
    static Vec3 leadPoint(Vec3 from, Entity tg, double v) {
        Vec3 vel = tg.getDeltaMovement();
        Vec3 p = tg.getBoundingBox().getCenter();
        double t = p.distanceTo(from) / v;
        for (int i = 0; i < 2; i++) t = p.add(vel.scale(t)).distanceTo(from) / v;
        return p.add(vel.scale(t));
    }

    private void tickBursts(ServerLevel sl) {
        if (this.bursts.isEmpty()) return;
        for (int k = this.bursts.size() - 1; k >= 0; k--) {
            double[] b = this.bursts.get(k);
            if (b[3] > this.tickCount) continue;
            this.bursts.remove(k);
            Vec3 p = new Vec3(b[0], b[1], b[2]);
            if (!sl.hasChunkAt(BlockPos.containing(p))) continue;
            sl.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), p.x, p.y, p.z, 10, 0.6, 0.6, 0.6, 0.01);
            sl.playSound(null, p.x, p.y, p.z, ModSounds.FLAK_BURST.get(), SoundSource.NEUTRAL, 6f, 0.8f);
            for (Entity e : sl.getEntities(this, new AABB(p, p).inflate(7.0), e -> e.isAlive() && !this.ownAir(e) && e.getVehicle() != this)) {
                double d = e.getBoundingBox().getCenter().distanceTo(p);
                if (d > 7.0) continue;
                e.invulnerableTime = 0;
                ru.aiforge.realwar.damage.HitContext.hurt(e, ModDamage.source(sl, ModDamage.ORDNANCE), (float) (6 + 30 * (1 - d / 7.0)), p, null, ru.aiforge.realwar.damage.HitContext.Kind.PROX, 40.0);
            }
        }
    }

    /** Server: entity id each flak mount engages (-1 none) - tests / HUD. */
    public int aaTargetOf(int i) {
        return this.aaTarget[i];
    }

    public int aaEngaged() {
        int n = 0;
        for (int i = I_H; i < M.length; i++) if (this.aaTarget[i] >= 0) n++;
        return n;
    }

    // ------------------------------------------------------------------------------------------ misc

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity p) {
        int seat = Math.max(0, this.seatOf(p));
        float[] s = this.kind.seats[seat];
        // step off onto the open bridge deck beside the seat
        Vec3 w = this.localToWorld(s[0] * 0.5, s[1] - 0.85 + 0.05, s[2] - 1.6);
        return new Vec3(w.x, w.y + 0.1, w.z);
    }

    @Override
    protected double cookOffKg() {
        return 2000;
    }

    @Override
    public void service(float repair) {
        super.service(repair);
        java.util.Arrays.fill(this.reload, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setAirborne(0);                       // aircraft in flight are not saved with the ship
        if (tag.contains("mt_az")) {
            int[] a = tag.getIntArray("mt_az"), e = tag.getIntArray("mt_el");
            if (a.length == M.length && e.length == M.length)
                for (int i = 0; i < M.length; i++) { this.az[i] = a[i] / 10f; this.el[i] = e[i] / 10f; }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // an Arado in flight is not saved: book it back into the hangar so a reload never loses aircraft
        int li = this.kind.loadIndex(CraftWeapon.ARADO);
        int[] am = this.ammoArray();
        am[li] = Math.min(this.kind.loads[li].full(), am[li] + this.aradosAirborne());
        tag.putIntArray("ammo", am);
        int[] a = new int[M.length], e = new int[M.length];
        for (int i = 0; i < M.length; i++) { a[i] = Math.round(this.az[i] * 10); e[i] = Math.round(this.el[i] * 10); }
        tag.putIntArray("mt_az", a);
        tag.putIntArray("mt_el", e);
    }
}
