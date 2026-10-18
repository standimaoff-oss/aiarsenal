package ru.aiforge.realwar.vehicle;

import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import javax.annotation.Nullable;
import ru.aiforge.realwar.glb.GlbModel;
import ru.aiforge.realwar.glb.Pose;
import ru.aiforge.realwar.item.DesignatorItem;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.ordnance.BulletManager;
import ru.aiforge.realwar.ordnance.ShellEntity;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.registry.ModSounds;

/**
 * Tube-artillery and tank-gun vehicles: T-90M, M1A2 Abrams, BMP-3 (tracked, driver + gunner), 2S19 Msta-S (tracked SPG)
 * and the towed D-30 / M777 howitzers (gunner only, not drivable).
 * <ul>
 *   <li><b>Tracks</b> — skid steering: the hull pivots on the spot, turn rate falls with speed. Road wheels, sprocket,
 *   idler and return rollers of each side spin with that side's belt travel; the belt itself is the glTF clip
 *   {@code track_L/track_R} (one link pitch of travel) scrubbed by distance.</li>
 *   <li><b>Fire control</b> — the turret follows the gunner's view (traverse / elevation rate limited, 360 degrees for
 *   turrets). A laser rangefinder lases the point under the crosshair and the ballistic computer adds the
 *   superelevation for the selected round (Ballistics.solveElevation, low-angle solution).</li>
 *   <li><b>Artillery</b> (2S19, D-30, M777) — map target / designator auto-laying (same as the BM-21), [LOCK] toggles it.
 *   Tanks: [LOCK] switches the round (HE-FRAG / APFSDS, BMP-3: 100 mm / 30 mm coax).</li>
 *   <li>Ready rounds = LauncherVehicle magazine (reload from the ammo item, R or right click at the rear).</li>
 * </ul>
 */
public class GunVehicle extends LauncherVehicle {
    protected static final EntityDataAccessor<Integer> SEL = SynchedEntityData.defineId(GunVehicle.class, EntityDataSerializers.INT);
    /** r20: smoke grenades left in the dischargers. */
    protected static final EntityDataAccessor<Integer> SMOKE = SynchedEntityData.defineId(GunVehicle.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> COAX = SynchedEntityData.defineId(GunVehicle.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Float> LASE = SynchedEntityData.defineId(GunVehicle.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Boolean> COAX_FIRING = SynchedEntityData.defineId(GunVehicle.class, EntityDataSerializers.BOOLEAN);
    public static final int COAX_MAX = 500;
    public static final double LASER_RANGE = 450.0;

    /** Per-vehicle gun + running-gear data. pitch = belt link pitch of the GLB (printed by the Blender script). */
    public record Cfg(Ballistics.Profile primary, Ballistics.Profile secondary, boolean coax30, float recoil,
                      float trackHalf, float rRoad, float rSpk, float rIdl, float rRr, int nRoad, int nRr, float pitch,
                      float pivotDegS, float travRate, float elevRate, Supplier<Item> ammo,
                      Ballistics.Profile third, boolean coaxMg, boolean alternate) {
        /** pre-r8 vehicles: no third gun, no rifle-calibre MG, single barrel. */
        public Cfg(Ballistics.Profile primary, Ballistics.Profile secondary, boolean coax30, float recoil,
                   float trackHalf, float rRoad, float rSpk, float rIdl, float rRr, int nRoad, int nRr, float pitch,
                   float pivotDegS, float travRate, float elevRate, Supplier<Item> ammo) {
            this(primary, secondary, coax30, recoil, trackHalf, rRoad, rSpk, rIdl, rRr, nRoad, nRr, pitch, pivotDegS, travRate, elevRate, ammo,
                    null, false, false);
        }

        public boolean tracked() {
            return this.nRoad > 0;
        }

        public boolean hasCoax() {
            return this.coax30 || this.coaxMg;
        }

        /** Selectable weapon modes in [LOCK] order: 0 primary, 1 secondary round, 2 second gun, 3 coaxial MG / 30 mm. */
        public int[] modes() {
            int[] m = new int[4];
            int n = 0;
            m[n++] = 0;
            if (this.secondary != null) m[n++] = 1;
            if (this.third != null) m[n++] = 2;
            if (this.hasCoax()) m[n++] = 3;
            return java.util.Arrays.copyOf(m, n);
        }
    }

    // tube index of the second gun (Maus 7.5 cm) and of the coaxial MG (always the last tube)
    private int coaxTube() {
        return this.cfg.coax30 ? 1 : this.spec.tubes().length - 1;
    }

    /** Current weapon mode (see Cfg.modes()). */
    public int mode() {
        int[] m = this.cfg.modes();
        return m[Math.floorMod(this.sel(), m.length)];
    }

    public static final int THIRD_MAX = 40;
    /** Belt link pitch of the r12 GLBs (printed by tools/rwveh/bradley.py / tos1a.py as TRACK_PITCH). */
    public static final float BRADLEY_PITCH = 0.15157f;
    public static final float TOS_PITCH = 0.13646f;
    /** TOS-1A elevation ram end points relative to the launcher trunnion, glTF {y up, z forward}: B on the turntable,
     *  A under the pack (must match tools/rwveh/tos1a.py RAM_B / RAM_A). */
    static final double[] RAM_B = {-0.05, 1.6};
    static final double[] RAM_A = {0.05, 2.4};

    /** Ready rounds of the second weapon: Bradley = 7 TOW (2 in the launcher + 5 stowed), others 40. */
    public int thirdMax() {
        return this.kind == VehicleKind.BRADLEY ? 7 : THIRD_MAX;
    }

    /** TOS-1A: every rocket leaves its own tube (24 muzzles in the spec); guns: one barrel. */
    public boolean perTube() {
        return this.kind == VehicleKind.TOS1A;
    }
    protected static final EntityDataAccessor<Integer> THIRD = SynchedEntityData.defineId(GunVehicle.class, EntityDataSerializers.INT);
    private int thirdCd;

    public static Cfg cfg(VehicleKind k) {
        return switch (k) {
            case T90M -> new Cfg(Ballistics.Profile.T125_HE, Ballistics.Profile.T125_AP, false, 0.30f,
                    1.395f, 0.375f, 0.33f, 0.28f, 0.115f, 6, 3, 0.13646f, 32.0f, 2.0f, 0.9f, () -> ModItems.SHELLS_125.get());
            case ABRAMS -> new Cfg(Ballistics.Profile.T120_HE, Ballistics.Profile.T120_AP, false, 0.30f,
                    1.43f, 0.32f, 0.33f, 0.32f, 0.11f, 7, 2, 0.15418f, 30.0f, 2.2f, 1.0f, () -> ModItems.SHELLS_120.get());
            case BMP3 -> new Cfg(Ballistics.Profile.B100_HE, null, true, 0.22f,
                    1.30f, 0.27f, 0.25f, 0.25f, 0.10f, 6, 3, 0.12029f, 40.0f, 2.4f, 1.4f, () -> ModItems.SHELLS_100.get());
            case MSTA -> new Cfg(Ballistics.Profile.MSTA_HE, null, false, 0.55f,
                    1.45f, 0.335f, 0.33f, 0.30f, 0.11f, 6, 5, 0.13635f, 26.0f, 1.0f, 0.6f, () -> ModItems.SHELLS_152.get());
            case D30 -> new Cfg(Ballistics.Profile.D30_HE, null, false, 0.6f,
                    0f, 0f, 0f, 0f, 0f, 0, 0, 1f, 0f, 0.6f, 0.5f, () -> ModItems.SHELLS_122.get());
            case M777 -> new Cfg(Ballistics.Profile.M777_HE, null, false, 0.9f,
                    0f, 0f, 0f, 0f, 0f, 0, 0, 1f, 0f, 0.5f, 0.5f, () -> ModItems.SHELLS_155.get());
            // ---- WW2 (reply 8). Running gear radii / link pitch = Blender scripts (TRACK_PITCH); traverse deg/tick from the real drives.
            case PZ2 -> new Cfg(Ballistics.Profile.KW20_HE, Ballistics.Profile.KW20_AP, false, 0.08f,
                    0.99f, 0.275f, 0.27f, 0.24f, 0.08f, 5, 4, 0.08999f, 26.0f, 0.75f, 0.35f, () -> ModItems.SHELLS_20.get(), null, true, false);
            case PZ4 -> new Cfg(Ballistics.Profile.K75_HE, Ballistics.Profile.K75_AP, false, 0.40f,
                    1.24f, 0.235f, 0.33f, 0.27f, 0.11f, 8, 4, 0.11970f, 18.0f, 0.70f, 0.30f, () -> ModItems.SHELLS_75.get(), null, true, false);
            case TIGER -> new Cfg(Ballistics.Profile.K88_HE, Ballistics.Profile.K88_AP, false, 0.58f,
                    1.411f, 0.40f, 0.42f, 0.30f, 0.1f, 8, 0, 0.13006f, 20.0f, 0.30f, 0.25f, () -> ModItems.SHELLS_88.get(), null, true, false);
            case STURMTIGER -> new Cfg(Ballistics.Profile.RW61, null, false, 0.0f,
                    1.411f, 0.40f, 0.42f, 0.30f, 0.1f, 8, 0, 0.13006f, 18.0f, 0.25f, 0.25f, () -> ModItems.ROCKET_380.get(), null, false, false);
            case MAUS -> new Cfg(Ballistics.Profile.K128_HE, Ballistics.Profile.K128_AP, false, 0.60f,
                    1.175f, 0.275f, 0.45f, 0.42f, 0.1f, 12, 0, 0.16043f, 8.0f, 0.35f, 0.20f, () -> ModItems.SHELLS_128.get(),
                    Ballistics.Profile.K75S_HE, true, false);
            // r9: third gun = the rear 12.8 cm Pak 44 turret (own pivot, aims independently at the crosshair point)
            case RATTE -> new Cfg(Ballistics.Profile.SK280_HE, null, false, 1.10f,
                    4.90f, 1.00f, 1.40f, 1.30f, 0.1f, 33, 0, 1.00723f, 4.0f, 0.20f, 0.15f, () -> ModItems.SHELLS_280.get(),
                    Ballistics.Profile.K128_HE, true, true);
            case T34 -> new Cfg(Ballistics.Profile.S85_HE, Ballistics.Profile.S85_AP, false, 0.32f,
                    1.25f, 0.415f, 0.33f, 0.30f, 0.1f, 5, 0, 0.17213f, 24.0f, 1.20f, 0.35f, () -> ModItems.SHELLS_85.get(), null, true, false);
            case KV2 -> new Cfg(Ballistics.Profile.KV2_HE, null, false, 0.50f,
                    1.31f, 0.30f, 0.33f, 0.32f, 0.12f, 6, 3, 0.16014f, 14.0f, 0.30f, 0.20f, () -> ModItems.SHELLS_152.get(), null, true, false);
            case IS2 -> new Cfg(Ballistics.Profile.IS2_HE, Ballistics.Profile.IS2_AP, false, 0.50f,
                    1.21f, 0.275f, 0.32f, 0.30f, 0.12f, 6, 3, 0.16023f, 18.0f, 0.80f, 0.25f, () -> ModItems.SHELLS_122.get(), null, true, false);
            case KV5 -> new Cfg(Ballistics.Profile.S107_HE, null, false, 0.50f,
                    1.60f, 0.33f, 0.38f, 0.36f, 0.13f, 8, 4, 0.17942f, 10.0f, 0.50f, 0.25f, () -> ModItems.SHELLS_107.get(), null, true, false);
            // ---- reply 12. Bradley: M242 dual feed (HEI-T / APFSDS-T), TOW-2B as the "second gun", M240C coax.
            case BRADLEY -> new Cfg(Ballistics.Profile.M242_HE, Ballistics.Profile.M242_AP, false, 0.06f,
                    1.36f, 0.305f, 0.30f, 0.27f, 0.10f, 6, 3, BRADLEY_PITCH, 34.0f, 3.0f, 1.6f, () -> ModItems.SHELLS_25.get(),
                    Ballistics.Profile.TOW2B, true, false);
            // TOS-1A: T-72A running gear (same as the T-90M), 24 x 220 mm thermobaric rockets
            case TOS1A -> new Cfg(Ballistics.Profile.TOS220, null, false, 0.0f,
                    1.395f, 0.375f, 0.33f, 0.28f, 0.115f, 6, 3, TOS_PITCH, 30.0f, 0.6f, 0.5f, () -> ModItems.ROCKET_220.get(), null, false, false);
            // WWI Mark I Male: two independently loaded QF 6-pdr sponsons; a single trigger fires every loaded mount.
            case MARK_I -> new Cfg(Ballistics.Profile.QF6_HE, Ballistics.Profile.QF6_AP, false, 0.14f,
                    1.50f, 0.305f, 0.385f, 0.385f, 0.115f, 6, 3, 0.29533399f, 45.0f, 0.45f, 0.35f,
                    () -> ModItems.SHELLS_57.get(), null, false, false);
            default -> throw new IllegalArgumentException("not a gun vehicle: " + k);
        };
    }

    public final Cfg cfg;

    // ---- Ratte (r9): independent rear 12.8 cm turret + automatic 2 cm Flak 38 air defence
    protected static final EntityDataAccessor<Float> SEC_AZ = SynchedEntityData.defineId(GunVehicle.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Float> SEC_EL = SynchedEntityData.defineId(GunVehicle.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Integer> FLAK = SynchedEntityData.defineId(GunVehicle.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> FLAK_TGT = SynchedEntityData.defineId(GunVehicle.class, EntityDataSerializers.INT);
    public static final int FLAK_MAX = 1600;                      // 8 guns x 200 ready rounds (20-round magazines)
    public static final double FLAK_RANGE = 150.0;               // blocks
    /** Rear turret: yaw pivot in model space (glTF), trunnion height, trunnion -> muzzle; rest = facing aft. */
    public static final double[] SEC_PIVOT = {0.0, 7.0, -11.0};
    public static final double SEC_TRUN_Y = 8.45, SEC_TRUN_D = 1.55, SEC_BARREL = 6.6;
    public static final float SEC_EL_MIN = -8.0f, SEC_EL_MAX = 40.0f, SEC_DEAD = 50.0f;   // the main turret blocks +-50 deg about the bow
    /** Flak 38 mounts: model-space pivot (x, y, z) of each pedestal's cradle. */
    public static final double[][] FLAK_POS = {{5.55, 7.15, 11.8}, {-5.55, 7.15, 11.8}, {5.55, 7.15, -5.9}, {-5.55, 7.15, -5.9},
            {5.55, 7.15, -9.6}, {-5.55, 7.15, -9.6}, {2.3, 7.15, -14.5}, {-2.3, 7.15, -14.5}};
    @Nullable
    private Vec3 aimPoint;
    private int flakCd, flakScan;
    public float secVis, secVisO;
    // client visuals: belt travel of each side (metres)
    public float trackL, trackR, trackLO, trackRO;
    private float[] cachedSolution;
    private int solveTick = -100;
    private float[] cachedDirect;
    private int directTick = -100;
    private boolean outOfSector;
    private boolean outOfRange;
    private int coaxCd;

    public GunVehicle(EntityType<?> type, Level level, VehicleKind kind) {
        super(type, level, kind);
        this.cfg = cfg(kind);
        if (kind == VehicleKind.BRADLEY) this.entityData.set(THIRD, 7);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SEL, 0);
        this.entityData.define(SMOKE, -1);     // -1 = full (kind is not set yet while the data is defined)
        this.entityData.define(COAX, COAX_MAX);
        this.entityData.define(LASE, -1.0f);
        this.entityData.define(COAX_FIRING, false);
        this.entityData.define(THIRD, THIRD_MAX);
        this.entityData.define(SEC_AZ, 180.0f);
        this.entityData.define(SEC_EL, 0.0f);
        this.entityData.define(FLAK, FLAK_MAX);
        this.entityData.define(FLAK_TGT, -1);
    }

    public boolean isRatte() {
        return this.kind == VehicleKind.RATTE;
    }

    /** Rear turret yaw relative to the hull (deg, 0 = bow, 180 = aft); other vehicles: the main turret. */
    public float secAz() {
        return this.isRatte() ? this.entityData.get(SEC_AZ) : this.az();
    }

    public float secEl() {
        return this.entityData.get(SEC_EL);
    }

    public int flakRounds() {
        return this.entityData.get(FLAK);
    }

    @Nullable
    public Entity flakTarget() {
        int id = this.entityData.get(FLAK_TGT);
        return id < 0 ? null : this.level().getEntity(id);
    }

    /** Model-space point -> world. */
    public Vec3 modelToWorld(double x, double y, double z) {
        Vector3d v = this.bodyFrame().transformPosition(new Vector3d(x, y, z));
        return new Vec3(v.x, v.y, v.z);
    }

    /** Rear turret frame (virtual forward-facing barrel along +Z after az/el). */
    private Matrix4d secFrame(double az, double el) {
        Vector3d b = this.bodyFrame().transformPosition(new Vector3d(SEC_PIVOT[0], SEC_TRUN_Y, SEC_PIVOT[2]));
        return new Matrix4d().translate(b.x, b.y, b.z).rotateY(-Math.toRadians(this.getYRot())).rotateY(Math.toRadians(az)).translate(0, 0, SEC_TRUN_D).rotateX(-Math.toRadians(el));
    }

    public Vec3 secMuzzle() {
        Vector3d v = this.secFrame(this.secAz(), this.secEl()).transformPosition(new Vector3d(0, 0, SEC_BARREL));
        return new Vec3(v.x, v.y, v.z);
    }

    public Vec3 secDir() {
        Vector3d v = this.secFrame(this.secAz(), this.secEl()).transformDirection(new Vector3d(0, 0, 1));
        return new Vec3(v.x, v.y, v.z).normalize();
    }

    /** True if the rear turret may fire at hull-relative azimuth az (the main turret blocks the bow sector). */
    public static boolean secClear(float az) {
        return Math.abs(Mth.wrapDegrees(az)) >= SEC_DEAD;
    }

    // ------------------------------------------------------------------ state
    public int sel() {
        return this.entityData.get(SEL);
    }

    public boolean coaxSelected() {
        return this.mode() == 3;
    }

    public boolean thirdSelected() {
        return this.mode() == 2;
    }

    public Ballistics.Profile profile() {
        return switch (this.mode()) {
            case 1 -> this.cfg.secondary;
            case 2 -> this.cfg.third;
            default -> this.cfg.primary;
        };
    }

    public int thirdRounds() {
        return this.entityData.get(THIRD);
    }

    public int coaxRounds() {
        return this.entityData.get(COAX);
    }

    public boolean coaxFiring() {
        return this.entityData.get(COAX_FIRING);
    }

    /** Last laser range in blocks (-1 = no return). */
    public float lase() {
        return this.entityData.get(LASE);
    }

    public boolean outOfRange() {
        return this.outOfRange;
    }

    /** {min, max} range in blocks of the current round over the elevation limits (flat ground). */
    public double[] rangesBlocks() {
        double k = Ballistics.scaleK();
        double max = 0.0;
        for (double e = Math.max(this.kind.elMin, 0.5); e <= this.kind.elMax + 1.0E-6; e += 2.5) {
            max = Math.max(max, Ballistics.range(this.profile(), e, 0.0));
        }
        return new double[]{Ballistics.range(this.profile(), Math.max(this.kind.elMin, 0.5), 0.0) / k, max / k};
    }

    @Override
    protected Item ammoItem() {
        return this.cfg.ammo.get();
    }

    // all main-gun rounds leave the same barrel (the magazine bits are just ready rounds)
    @Override
    public float[] muzzleLocal(int k) {
        if (this.kind == VehicleKind.MARK_I) return new float[]{k == 0 ? -1.41f : 1.41f, 1.22f, 0.0f};
        if (this.perTube()) return this.spec.tubes()[Mth.clamp(k, 0, this.spec.tubes().length - 1)];
        return this.spec.tubes()[this.cfg.alternate ? Math.floorMod(k, 2) : 0];   // Ratte: twin 28 cm (r11: fired together as a salvo)
    }

    /** r11: twin main gun fired as a salvo (both barrels per trigger pull). */
    public boolean twinSalvo() {
        return this.cfg.alternate;
    }

    public Vec3 tubeWorld(int tube) {
        if (this.kind == VehicleKind.MARK_I) {
            // Each cannon pivots about its own sponson trunnion; only its muzzle follows the shared aim line.
            Matrix4d localAim = new Matrix4d().rotateY(Math.toRadians(this.az())).rotateX(-Math.toRadians(this.el()));
            Vector3d muzzle = localAim.transformPosition(new Vector3d(0.0, 0.0, this.kind.tubeLen));
            muzzle.add(tube == 0 ? -1.41 : 1.41, 1.22, 0.0);
            Vector3d world = this.bodyFrame().transformPosition(muzzle);
            return new Vec3(world.x, world.y, world.z);
        }
        float[] q = this.spec.tubes()[Mth.clamp(tube, 0, this.spec.tubes().length - 1)];
        Vector3d v = this.pitchFrame(this.az(), this.el()).transformPosition(new Vector3d(q[0], q[1], q[2]));
        return new Vec3(v.x, v.y, v.z);
    }

    public Vec3 pivotWorld() {
        Vector3d v = this.pitchFrame(this.az(), this.el()).transformPosition(new Vector3d(0, 0, 0));
        return new Vec3(v.x, v.y, v.z);
    }

    // ------------------------------------------------------------------ map target: artillery only
    @Override
    public void setMapTarget(ServerPlayer p, net.minecraft.core.BlockPos pos) {
        if (!this.kind.artillery()) {
            return;
        }
        super.setMapTarget(p, pos);
    }

    @Override
    protected void onTargetAssigned(ServerPlayer p) {
        this.solveTick = -100;
        super.onTargetAssigned(p);
    }

    @Override
    protected void onLock(ServerPlayer p, int seat) {
        if (seat != this.operatorSeat()) {
            return;
        }
        if (this.kind.artillery()) {
            if (this.autoAim()) {
                this.entityData.set(AUTO, false);
                p.displayClientMessage(Component.translatable("hud.aiarsenal.rw.gun.direct"), true);
                return;
            }
            if (this.mapTarget() != null || DesignatorItem.findTarget(p) != null) {
                this.entityData.set(AUTO, true);
                this.solveTick = -100;
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.LOCK_DONE.get(), SoundSource.NEUTRAL, 0.8f, 1.0f);
            } else {
                p.displayClientMessage(Component.translatable("hud.aiarsenal.rw.gun.no_target"), true);
            }
            return;
        }
        int nm = this.cfg.modes().length;
        if (nm < 2) {
            return;
        }
        int n = (Math.floorMod(this.sel(), nm) + 1) % nm;
        this.entityData.set(SEL, n);
        this.rememberMainMode(this.mode());
        this.directTick = -100;
        p.displayClientMessage(Component.translatable("hud.aiarsenal.rw.gun.sel." + this.roundKey()), true);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.RELOAD_CLANK.get(), SoundSource.NEUTRAL, 0.7f, 1.3f);
    }

    /** r20: smoke grenade dischargers of the real vehicles (0 = none). T-34-85: two MDSh smoke drums on the stern. */
    public static int smokeMax(VehicleKind k) {
        return switch (k) {
            case T90M, ABRAMS -> 12;     // 902G Shtora 2 x 6 / M250 2 x 6
            case BRADLEY -> 8;           // M257 2 x 4
            case BMP3, MSTA -> 6;        // 902V Tucha
            case PZ4, TIGER -> 6;        // NbK 39, 3 per turret side
            case T34 -> 2;               // MDSh drums
            default -> 0;
        };
    }

    public int smoke() {
        int v = this.entityData.get(SMOKE);
        return v < 0 ? smokeMax(this.kind) : v;
    }

    private int smokeCooldown;

    /** Fires one salvo (6 grenades, or the rest) in the gun direction; the T-34 drops a smoke drum behind. */
    public void fireSmoke(ServerPlayer p) {
        if (this.seatOf(p) != this.operatorSeat() || !(this.level() instanceof ServerLevel sl)) return;
        int left = this.smoke();
        if (left <= 0) {
            p.displayClientMessage(Component.translatable("hud.aiarsenal.rw.smoke.empty"), true);
            return;
        }
        if (this.tickCount < this.smokeCooldown) return;
        this.smokeCooldown = this.tickCount + 40;
        if (this.kind == VehicleKind.T34) {
            Vec3 b = this.localToWorld(0, 1.0, -this.spec.length() * 0.55);
            SmokeScreen.add(sl, b, 5.0, SmokeScreen.LIFE);
            this.entityData.set(SMOKE, left - 1);
        } else {
            int n = Math.min(6, left);
            Vec3 f = this.launchDir(this.az(), 0);
            Vec3 h = new Vec3(f.x, 0, f.z);
            if (h.lengthSqr() < 1e-4) h = new Vec3(0, 0, 1);
            h = h.normalize();
            Vec3 c = this.position().add(0, 1.6, 0);
            for (int i = 0; i < n; i++) {
                double a = Math.toRadians((i - (n - 1) / 2.0) * (70.0 / Math.max(1, n - 1)));
                Vec3 d = new Vec3(h.x * Math.cos(a) - h.z * Math.sin(a), 0, h.x * Math.sin(a) + h.z * Math.cos(a));
                Vec3 q = c.add(d.scale(16 + sl.random.nextDouble() * 4));
                int gy = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(q.x), (int) Math.floor(q.z));
                SmokeScreen.add(sl, new Vec3(q.x, Math.max(gy + 1.5, q.y - 1.0), q.z), 4.5, SmokeScreen.LIFE);
            }
            this.entityData.set(SMOKE, left - n);
        }
        sl.playSound(null, this.getX(), this.getY(), this.getZ(), net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 2.5f, 0.6f);
        p.displayClientMessage(Component.translatable("hud.aiarsenal.rw.smoke.fired", this.smoke()), true);
    }

    /** r20: weapon / round chosen directly with the number keys (slot = position on the HUD slot bar). */
    public void selectSlot(ServerPlayer p, int slot) {
        if (this.seatOf(p) != this.operatorSeat()) return;
        int nm = this.cfg.modes().length;
        if (slot < 0 || slot >= nm || slot == Math.floorMod(this.sel(), nm)) return;
        this.entityData.set(SEL, slot);
        this.rememberMainMode(this.mode());
        this.directTick = -100;
        p.displayClientMessage(Component.translatable("hud.aiarsenal.rw.gun.sel." + this.roundKey()), true);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.RELOAD_CLANK.get(), SoundSource.NEUTRAL, 0.7f, 1.3f);
    }

    /** r28: the round type the loader seats next = the selected main-gun round. The coaxial MG and the second gun
     *  have their own counters, so switching to them keeps loading the last selected main-gun round. */
    @Override
    public int loadType() {
        int m = this.mode();
        return m <= 1 ? m : this.lastMainMode;
    }

    private int lastMainMode = 0;

    private void rememberMainMode(int m) {
        if (m <= 1) {
            this.lastMainMode = m;
        }
    }

    /** lang key suffix of the selected round. */
    public String roundKey() {
        if (this.coaxSelected()) return this.cfg.coax30 ? "coax30" : "coax_mg";
        return this.profile().name().toLowerCase(java.util.Locale.ROOT);
    }

    // ------------------------------------------------------------------ fire control
    @Override
    protected float[] aimTarget() {
        ServerPlayer op = this.operator();
        if (op == null) {
            return null;
        }
        float[] t;
        if (this.autoAim()) {
            t = this.solveDesignated(op);
            if (t == null) {
                this.entityData.set(AUTO, false);
                return null;
            }
        } else {
            t = this.directFire(op);
        }
        this.outOfSector = this.kind.traverse < 179.9f && Math.abs(Mth.wrapDegrees(t[0])) > this.kind.traverse + 0.5f;
        return t;
    }

    /** r20: gunner sight in the pitch frame (x, y, z): on the barrel in front of the turret face, beside a launcher
     *  pack, between twin guns. Shared by the client camera (VehicleClient) and the server's laser ranging. */
    public double[] sightLocal() {
        float[] q = this.spec.tubes()[0];
        double sz = q[2] != 0 ? Math.signum(q[2]) : this.kind.fireAxisZ();
        // in front of the turret face (ERA wedges, roof sights and hatches stay behind the camera), riding on the barrel
        double z = sz * Mth.clamp(0.45 * Math.abs(q[2]), 1.2, 3.2);
        double qx = q[0], qy = q[1] + 0.30;
        float[][] tb = this.spec.tubes();
        if (this.perTube()) {
            // r20: launcher pack (TOS-1A) - the sight sits on the LEFT side of the pack (PG-1M style), never inside
            // the tubes: 0.65 m outside the outermost tube, at mid height, near the trunnions (checked against the GLB)
            float mx = 0f, y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
            for (float[] k : tb) {
                mx = Math.max(mx, Math.abs(k[0]));
                y0 = Math.min(y0, k[1]);
                y1 = Math.max(y1, k[1]);
            }
            qx = mx + 0.65;
            qy = (y0 + y1) * 0.5 + 0.15;
            z = sz * 0.6;
        } else if (tb.length >= 2 && Math.abs(q[0]) > 0.4 && Math.abs(tb[1][0] + q[0]) < 0.1 && Math.abs(tb[1][2] - q[2]) < 0.5) {
            qx = 0.0;   // r20: twin guns (Ratte 28 cm): between the barrels instead of inside the left one
        }
        return new double[]{qx, qy, z};
    }

    /** r20: gunner sight position in the world. */
    public Vec3 sightWorld() {
        double[] s = this.sightLocal();
        org.joml.Vector3d t = this.pitchFrame(this.az(), this.el()).transformPosition(new org.joml.Vector3d(s[0], s[1], s[2]));
        return new Vec3(t.x, t.y, t.z);
    }

    private float[] directFire(ServerPlayer op) {
        if (this.tickCount - this.directTick < 3 && this.cachedDirect != null) {
            // keep the lased solution but follow fast mouse movement in azimuth immediately
            return this.cachedDirect;
        }
        this.directTick = this.tickCount;
        Level lv = this.level();
        // r20: range from the gunner sight (where the client camera is), not from the seated body inside the hull -
        // otherwise the parallax put the round under the crosshair's target (onto the tracks / the ground behind)
        Vec3 eye = this.sightWorld();
        Vec3 look = op.getLookAngle();
        Vec3 end = eye.add(look.scale(LASER_RANGE));
        Vec3 hit = null;
        BlockHitResult bh = lv.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, op));
        if (bh.getType() != HitResult.Type.MISS) {
            hit = bh.getLocation();
        }
        Vec3 eEnd = hit != null ? hit : eye.add(look.scale(250.0));
        EntityHitResult eh = ru.aiforge.realwar.damage.VehicleHit.first(lv, op, eye, eEnd, 0.3,
                e -> e != this && e.getVehicle() != this && e.isAlive() && !e.isSpectator() && (e instanceof LivingEntity || e instanceof VehicleEntity || e.isPickable()));
        if (eh != null) {
            hit = eh.getLocation();
        }
        float az;
        float el;
        this.aimPoint = hit != null ? hit : eye.add(look.scale(300.0));
        if (hit != null) {
            Vec3 piv = this.pivotWorld();
            double dx = hit.x - piv.x;
            double dz = hit.z - piv.z;
            double k = Ballistics.scaleK();
            double sol = Ballistics.solveElevation(this.coaxSelected() ? Ballistics.Profile.T125_AP : this.profile(),
                    Math.sqrt(dx * dx + dz * dz) * k, (hit.y - piv.y) * k, this.kind.elMin, Math.min(this.kind.elMax, 45.0f));
            az = this.azRelFromWorldYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
            el = Double.isNaN(sol) ? -op.getXRot() : (float) sol;
            this.entityData.set(LASE, (float) hit.distanceTo(piv));
        } else {
            az = this.azRelFromWorldYaw(op.getYRot());
            el = -op.getXRot();
            this.entityData.set(LASE, -1.0f);
        }
        this.cachedDirect = new float[]{az, el};
        return this.cachedDirect;
    }

    private float[] solveDesignated(ServerPlayer op) {
        if (this.tickCount - this.solveTick < 10 && this.cachedSolution != null) {
            return this.cachedSolution;
        }
        Vec3 tg = this.mapTarget() != null ? this.mapTarget() : DesignatorItem.findTarget(op);
        if (tg == null) {
            return null;
        }
        this.solveTick = this.tickCount;
        Vec3 from = this.pivotWorld();
        double dx = tg.x - from.x;
        double dz = tg.z - from.z;
        double k = Ballistics.scaleK();
        double rangeM = Math.sqrt(dx * dx + dz * dz) * k;
        double dyM = (tg.y - from.y) * k;
        double el = Ballistics.solveElevation(this.profile(), rangeM, dyM, Math.max(this.kind.elMin, 0.0f), this.kind.elMax);
        this.outOfRange = Double.isNaN(el);
        if (this.outOfRange) {
            el = Ballistics.range(this.profile(), 45.0, dyM) < rangeM ? (double) (this.kind.elMax - 20.0f) : (double) Math.max(this.kind.elMin, 0.0f);
        }
        this.entityData.set(LASE, (float) Math.sqrt(dx * dx + dz * dz));
        this.cachedSolution = new float[]{this.azRelFromWorldYaw((float) Math.toDegrees(Math.atan2(-dx, dz))), (float) el};
        return this.cachedSolution;
    }

    @Override
    protected void updateAim() {
        float[] t = this.aimTarget();
        if (t == null) {
            return;
        }
        float az = this.az();
        float el = this.el();
        boolean full = this.kind.traverse >= 179.9f;
        if (this.dmg.dead()) return;
        float nAz = az + Mth.clamp(Mth.wrapDegrees(t[0] - az), -this.cfg.travRate, this.cfg.travRate);
        if (!ru.aiforge.realwar.damage.VehicleDamage.canTraverse(this)) nAz = az;   // r18: turret ring drive knocked out
        nAz = full ? Mth.wrapDegrees(nAz) : Mth.clamp(nAz, -this.kind.traverse, this.kind.traverse);
        float nEl = Mth.clamp(el + Mth.clamp(t[1] - el, -this.cfg.elevRate, this.cfg.elevRate), this.minEl(nAz), this.kind.elMax);
        if (Math.abs(Mth.wrapDegrees(nAz - az)) > 0.01f || Math.abs(nEl - el) > 0.01f) {
            this.entityData.set(AZ, nAz);
            this.entityData.set(EL, nEl);
            if (this.tickCount % 14 == 0) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.HYDRAULIC.get(), SoundSource.NEUTRAL, 0.5f, this.kind.drivable() ? 1.1f : 0.7f);
            }
        }
    }

    /** Lowest elevation at a turret bearing: Ratte 28 cm barrels must clear the bridge (bow) and the rear turret (stern). */
    public float minEl(float az) {
        if (!this.isRatte()) return this.kind.elMin;
        float a = Math.abs(Mth.wrapDegrees(az));
        if (a < 20.0f) return Math.max(this.kind.elMin, 0.0f);
        if (a > 160.0f) return Math.max(this.kind.elMin, 7.0f);
        return this.kind.elMin;
    }

    @Override
    protected boolean aimSettled(float[] t) {
        if (t == null) {
            return true;
        }
        float tol = this.kind.artillery() ? 0.6f : 0.4f;
        float tAz = this.kind.traverse >= 179.9f ? t[0] : Mth.clamp(t[0], -this.kind.traverse, this.kind.traverse);
        return Math.abs(Mth.wrapDegrees(tAz - this.az())) < tol
                && Math.abs(Mth.clamp(t[1], this.kind.elMin, this.kind.elMax) - this.el()) < tol;
    }

    @Override
    protected boolean mayReload() {
        return this.fireCd <= Math.max(0, this.kind.fireInterval - 30) && (this.kind.tracked() || Math.abs(this.speedMs) < 0.4);
    }

    /** Ratte rear turret: follows the operator's aim point with its own traverse (independent of the main turret). */
    private void updateSecondary() {
        if (this.aimPoint == null) return;
        Vec3 piv = this.modelToWorld(SEC_PIVOT[0], SEC_TRUN_Y, SEC_PIVOT[2]);
        double dx = this.aimPoint.x - piv.x, dz = this.aimPoint.z - piv.z;
        float tAz = this.azRelFromWorldYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        double k = Ballistics.scaleK();
        double sol = Ballistics.solveElevation(this.cfg.third, Math.sqrt(dx * dx + dz * dz) * k, (this.aimPoint.y - piv.y) * k, SEC_EL_MIN, SEC_EL_MAX);
        float tEl = Double.isNaN(sol) ? 10.0f : (float) sol;
        if (!secClear(tAz)) tAz = Math.copySign(SEC_DEAD, Mth.wrapDegrees(tAz));          // park at the edge of the dead sector
        float az = this.secAz(), el = this.secEl();
        // traverse the long way round if the short way crosses the bow dead sector
        float d = Mth.wrapDegrees(tAz - az);
        float mid = Mth.wrapDegrees(az + d * 0.5f);
        if (!secClear(mid) && Math.abs(d) > 2 * SEC_DEAD) d = d > 0 ? d - 360.0f : d + 360.0f;
        float nAz = Mth.wrapDegrees(az + Mth.clamp(d, -0.9f, 0.9f));
        if (!secClear(nAz)) nAz = az;
        float nEl = Mth.clamp(el + Mth.clamp(tEl - el, -0.5f, 0.5f), SEC_EL_MIN, SEC_EL_MAX);
        if (nAz != az) this.entityData.set(SEC_AZ, nAz);
        if (nEl != el) this.entityData.set(SEC_EL, nEl);
    }

    /** Ratte Flak 38 battery: picks the nearest air target and fires lead-corrected bursts from every mount that bears. */
    private void updateFlak(ServerLevel sl, @Nullable ServerPlayer op) {
        if (op != null && this.reloadHeld(this.operatorSeat()) && this.flakRounds() < FLAK_MAX && this.tickCount % 4 == 0) {
            this.entityData.set(FLAK, Math.min(FLAK_MAX, this.flakRounds() + 20));
        }
        Entity tg = this.flakTarget();
        if (op == null) {
            if (tg != null) this.entityData.set(FLAK_TGT, -1);
            return;
        }
        Vec3 c = this.modelToWorld(0, 8.0, 0);
        if (--this.flakScan <= 0 || tg == null || !tg.isAlive() || tg.distanceToSqr(c) > FLAK_RANGE * FLAK_RANGE || !ru.aiforge.realwar.ordnance.AirTargets.isAirTarget(tg)) {
            this.flakScan = 10;
            Entity best = null;
            double bd = FLAK_RANGE * FLAK_RANGE;
            for (Entity e : sl.getEntities(this, new AABB(c, c).inflate(FLAK_RANGE), e -> e != op && e.getVehicle() != this && !e.hasPassenger(op)
                    && ru.aiforge.realwar.ordnance.AirTargets.isAirTarget(e) && e.getY() > c.y + 4.0)) {
                double dd = e.distanceToSqr(c);
                if (dd > 20 * 20 && dd < bd) { bd = dd; best = e; }
            }
            int id = best == null ? -1 : best.getId();
            if (id != this.entityData.get(FLAK_TGT)) this.entityData.set(FLAK_TGT, id);
            tg = best;
        }
        if (tg == null || this.flakRounds() <= 0 || --this.flakCd > 0) return;
        this.flakCd = 3;                                         // Flak 38: ~420-480 rds/min per gun
        int fired = 0;
        for (double[] m : FLAK_POS) {
            Vec3 mp = this.modelToWorld(m[0], m[1], m[2]);
            Vec3 lead = flakLead(mp, tg);
            Vec3 dir = lead.subtract(mp);
            if (dir.y < dir.horizontalDistance() * 0.05) continue;    // too low: would hit the own deck
            dir = dir.normalize();
            Vec3 muz = mp.add(dir.scale(2.3));
            double sig = 0.004;
            Vec3 dd = dir.add(sl.random.nextGaussian() * sig, sl.random.nextGaussian() * sig, sl.random.nextGaussian() * sig).normalize();
            BulletManager.spawn(sl, muz, dd.scale(10.0), this.getId(), (this.tickCount + fired) % 3 == 0);
            fired++;
        }
        if (fired > 0) {
            this.entityData.set(FLAK, Math.max(0, this.flakRounds() - fired));
            sl.playSound(null, c.x, c.y, c.z, ModSounds.FLAK_BURST.get(), SoundSource.NEUTRAL, 7.0f, 1.05f + sl.random.nextFloat() * 0.1f);
        }
    }

    /** Lead point for a 10 blocks/tick bullet (two iterations). */
    public static Vec3 flakLead(Vec3 from, Entity tg) {
        Vec3 v = tg.getDeltaMovement();
        Vec3 p = tg.getBoundingBox().getCenter();
        double t = p.distanceTo(from) / 10.0;
        for (int i = 0; i < 2; i++) t = p.add(v.scale(t)).distanceTo(from) / 10.0;
        return p.add(v.scale(t));
    }

    @Override
    protected void serverTick() {
        super.serverTick();
        if (this.isRatte() && this.level() instanceof ServerLevel rsl) {
            if (this.operator() != null) this.updateSecondary();
            this.updateFlak(rsl, this.operator());
        }
        if (this.coaxCd > 0) --this.coaxCd;
        if (this.thirdCd > 0) --this.thirdCd;
        boolean coaxNow = false;
        ServerPlayer op = this.operator();
        if (op != null && !this.dmg.dead() && this.coaxSelected() && this.fireHeld(this.operatorSeat()) && this.level() instanceof ServerLevel sl) {
            coaxNow = this.fireCoax(sl);
        }
        if (coaxNow != this.coaxFiring()) {
            this.entityData.set(COAX_FIRING, coaxNow);
        }
        // the 30 mm belt is topped up together with main-gun reloading (both from the same ammo racks)
        if (this.reloadHeld(this.operatorSeat()) && op != null && this.smoke() < smokeMax(this.kind) && this.tickCount % 40 == 0 && Math.abs(this.speedMs) < 0.5) {
            this.entityData.set(SMOKE, this.smoke() + 1);      // r20: the crew reloads the grenade dischargers
        }
        if (this.cfg.hasCoax() && this.reloadHeld(this.operatorSeat()) && this.coaxRounds() < COAX_MAX && this.tickCount % 4 == 0 && op != null) {
            this.entityData.set(COAX, Math.min(COAX_MAX, this.coaxRounds() + 10));
        }
        if (this.cfg.third != null && this.reloadHeld(this.operatorSeat()) && this.thirdRounds() < this.thirdMax() && this.tickCount % 20 == 0 && op != null) {
            this.entityData.set(THIRD, this.thirdRounds() + 1);
        }
    }

    private boolean fireCoax(ServerLevel sl) {
        if (this.coaxRounds() <= 0) {
            this.notifyOp("hud.aiarsenal.rw.no_ammo");
            return false;
        }
        if (this.coaxCd > 0) {
            return true;
        }
        this.coaxCd = this.cfg.coax30 ? 3 : 2;    // 2A72 ~350-400 rds/min; MG 34 / DT / MG 151 ~600 (rate of the ammo belt in game ticks)
        Vec3 m = this.tubeWorld(this.coaxTube());
        Vec3 d = this.launchDir();
        double sig = 0.0035;
        Vec3 dir = d.add(sl.random.nextGaussian() * sig, sl.random.nextGaussian() * sig, sl.random.nextGaussian() * sig).normalize();
        BulletManager.spawn(sl, m, dir.scale(12.0), this.getId(), this.coaxRounds() % 3 == 0);
        this.entityData.set(COAX, this.coaxRounds() - 1);
        if (this.cfg.coax30) {
            sl.playSound(null, m.x, m.y, m.z, ModSounds.FLAK_BURST.get(), SoundSource.NEUTRAL, 6.0f, 0.75f + sl.random.nextFloat() * 0.1f);
        } else {
            sl.playSound(null, m.x, m.y, m.z, ModSounds.CRAFT_MG.get(), SoundSource.NEUTRAL, 4.0f, 1.0f + sl.random.nextFloat() * 0.1f);
        }
        return true;
    }

    @Override
    protected void serverLaunch() {
        ServerPlayer op = this.operator();
        if (op == null || this.coaxSelected() || !this.fireHeld(this.operatorSeat()) || !(this.level() instanceof ServerLevel sl)) {
            return;
        }
        if (this.dmg.dead()) return;
        if (this.thirdSelected()) {
            this.fireThird(sl);
            return;
        }
        if (this.fireCd > 0) {
            return;
        }
        if (!ru.aiforge.realwar.damage.VehicleDamage.canFireMain(this)) {
            if (this.tickCount % 20 == 0) this.notifyOp("hud.aiarsenal.rw.dmg.no_gun");
            return;
        }
        if (this.reloadTube() >= 0) {
            this.notifyOp("hud.aiarsenal.rw.reloading");
            return;
        }
        if (this.loadedCount() == 0) {
            this.notifyOp("hud.aiarsenal.rw.no_ammo");
            return;
        }
        int want = this.loadType();
        if (this.firstLoadedOfType(want) < 0) {
            // r28: the breech holds the other round. Before this the shot simply used whatever sat in the rack, so a
            // player who selected AP fired HE (and the counter of BOTH round types dropped).
            if (this.tickCount % 20 == 0) {
                this.notifyOp("hud.aiarsenal.rw.gun.wrong_round");
            }
            return;
        }
        if (this.outOfSector) {
            this.notifyOp("hud.aiarsenal.rw.out_of_sector");
            return;
        }
        if (this.autoAim() && !this.aimSettled(this.aimTarget())) {
            return;
        }
        Ballistics.Profile prof = this.profile();
        int k = this.firstLoadedOfType(want);
        Vec3 dir = this.launchDir();
        Vec3 muzzle;
        if (this.perTube()) {
            muzzle = this.tubeWorld(k);
            // r12 TOS-1A: unguided 220 mm rocket (motor + smoke trail), salvo pattern of the real launcher
            double rs = 0.006;
            Vec3 rd = dir.add(sl.random.nextGaussian() * rs, sl.random.nextGaussian() * rs * 0.6, sl.random.nextGaussian() * rs).normalize();
            ru.aiforge.realwar.ordnance.RocketEntity.launch(sl, prof, muzzle.add(rd.scale(0.6)), rd, this.getId());
            this.markFired(k);
            this.fireCd = this.kind.fireInterval;
            sl.playSound(null, muzzle.x, muzzle.y, muzzle.z, ModSounds.ROCKET_LAUNCH_LARGE.get(), SoundSource.NEUTRAL, 16.0f, 0.75f + sl.random.nextFloat() * 0.1f);
            this.rocketBackblast(sl, muzzle, rd);
            return;
        }
        double sig = this.kind.artillery() ? 0.0016 : (this.kind.ww2() ? 0.0007 : 0.0004);
        Vec3 d = dir.add(sl.random.nextGaussian() * sig, sl.random.nextGaussian() * sig * 0.6, sl.random.nextGaussian() * sig).normalize();
        if (this.kind == VehicleKind.MARK_I) {
            int second = -1;
            for (int slot = 0; slot < this.kind.magazine; ++slot) {
                if (slot != k && this.isLoaded(slot) && this.slotType(slot) == want) { second = slot; break; }
            }
            Vec3 m0 = this.tubeWorld(k);
            ShellEntity.fire(sl, prof, m0.add(d.scale(0.2)), d, this.getId());
            if (second >= 0) {
                Vec3 d2 = dir.add(sl.random.nextGaussian() * sig, sl.random.nextGaussian() * sig * 0.6, sl.random.nextGaussian() * sig).normalize();
                Vec3 m1 = this.tubeWorld(second);
                ShellEntity.fire(sl, prof, m1.add(d2.scale(0.2)), d2, this.getId());
                this.markFiredPair(k, second);
                muzzle = m0.add(m1).scale(0.5);
            } else {
                this.markFired(k);
                muzzle = m0;
            }
        } else {
            muzzle = this.tubeWorld(0);
            ShellEntity.fire(sl, prof, muzzle.add(d.scale(0.2)), d, this.getId());
            this.markFired(k);
            if (this.cfg.alternate) {
                // r11: twin mount (Ratte 2 x 28 cm SK C/34) - both barrels fire as one salvo, then both reload
                int k2 = this.firstLoadedOfType(want);
                if (k2 >= 0) {
                    Vec3 m2 = this.tubeWorld(1);
                    Vec3 d2 = dir.add(sl.random.nextGaussian() * sig, sl.random.nextGaussian() * sig * 0.6, sl.random.nextGaussian() * sig).normalize();
                    ShellEntity.fire(sl, prof, m2.add(d2.scale(0.2)), d2, this.getId());
                    this.markFired(k2);
                    muzzle = muzzle.add(m2).scale(0.5);
                }
            }
        }
        this.fireCd = this.kind.fireInterval;
        boolean big = this.kind.artillery() || this.kind == VehicleKind.T90M || this.kind == VehicleKind.ABRAMS || this.kind == VehicleKind.TIGER
                || this.kind == VehicleKind.MAUS || this.kind == VehicleKind.RATTE || this.kind == VehicleKind.KV2 || this.kind == VehicleKind.IS2
                || this.kind == VehicleKind.KV5;
        if (this.kind == VehicleKind.STURMTIGER) {
            sl.playSound(null, muzzle.x, muzzle.y, muzzle.z, ModSounds.ROCKET_LAUNCH_LARGE.get(), SoundSource.NEUTRAL, 18.0f, 0.7f);
        }
        if (this.kind == VehicleKind.BRADLEY) {
            // M242 Bushmaster chain gun: autocannon report, no tank-gun overpressure
            sl.playSound(null, muzzle.x, muzzle.y, muzzle.z, ModSounds.FLAK_BURST.get(), SoundSource.NEUTRAL, 7.0f, 0.85f + sl.random.nextFloat() * 0.08f);
            return;
        }
        sl.playSound(null, muzzle.x, muzzle.y, muzzle.z, (this.kind.artillery() ? ModSounds.CANNON_HOWITZER : ModSounds.CANNON_TANK).get(),
                SoundSource.NEUTRAL, big ? 20.0f : 10.0f, (this.kind == VehicleKind.BMP3 ? 1.25f : 0.95f) + sl.random.nextFloat() * 0.1f);
        if (this.kind == VehicleKind.MARK_I && this.firedTube() == -2) {
            this.muzzleBlast(sl, this.tubeWorld(0), dir);
            this.muzzleBlast(sl, this.tubeWorld(1), dir);
        } else this.muzzleBlast(sl, muzzle, d);
    }

    /** Maus: the coaxial 7.5 cm KwK 44 L/36.5 - own ready rounds (refilled with [R] from the racks) and own loader cycle. */
    private void fireThird(ServerLevel sl) {
        if (this.thirdCd > 0) {
            return;
        }
        if (this.thirdRounds() <= 0) {
            this.notifyOp("hud.aiarsenal.rw.no_ammo");
            return;
        }
        boolean rear = this.isRatte();
        if (rear ? !secClear(this.secAz()) : this.outOfSector) {
            this.notifyOp("hud.aiarsenal.rw.out_of_sector");
            return;
        }
        Vec3 muzzle = rear ? this.secMuzzle() : this.tubeWorld(1);
        Vec3 dir = rear ? this.secDir() : this.launchDir();
        if (this.kind == VehicleKind.BRADLEY) {
            // r12: BGM-71 TOW-2B, SACLOS - the missile rides the gunner's line of sight (see AtMissileEntity.saclos)
            ServerPlayer op = this.operator();
            ru.aiforge.realwar.handheld.AtMissileEntity.launch(sl, op != null ? op : this, ru.aiforge.realwar.handheld.HandheldKind.TOW,
                    muzzle.add(dir.scale(0.4)), dir, -1, null);
            this.entityData.set(THIRD, this.thirdRounds() - 1);
            this.thirdCd = 40;
            sl.playSound(null, muzzle.x, muzzle.y, muzzle.z, ModSounds.ROCKET_LAUNCH_SMALL.get(), SoundSource.NEUTRAL, 8.0f, 1.2f);
            return;
        }
        double sig = 0.0008;
        Vec3 d = dir.add(sl.random.nextGaussian() * sig, sl.random.nextGaussian() * sig * 0.6, sl.random.nextGaussian() * sig).normalize();
        ShellEntity.fire(sl, this.cfg.third, muzzle.add(d.scale(0.2)), d, this.getId());
        this.entityData.set(THIRD, this.thirdRounds() - 1);
        this.thirdCd = rear ? 120 : 100;    // ~10-12 rds/min (12.8 cm: two-piece ammunition, ~5-6 rds/min with a rested crew)
        sl.playSound(null, muzzle.x, muzzle.y, muzzle.z, ModSounds.CANNON_TANK.get(), SoundSource.NEUTRAL, 10.0f, 1.15f + sl.random.nextFloat() * 0.1f);
    }

    /** TOS-1A: rocket exhaust behind the launcher - burns / knocks back anybody standing in the cone. */
    private void rocketBackblast(ServerLevel sl, Vec3 muzzle, Vec3 dir) {
        Vec3 breech = muzzle.subtract(dir.scale(this.kind.tubeLen));
        double len = this.kind.backblast;
        Vec3 end = breech.subtract(dir.scale(len));
        for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(breech, end).inflate(3.0))) {
            if (le.getVehicle() == this) continue;
            Vec3 rel = le.position().subtract(breech);
            double along = -rel.dot(dir);
            if (along < 0 || along > len) continue;
            double off = rel.add(dir.scale(along)).length();
            if (off > 1.5 + along * 0.15) continue;
            le.hurt(sl.damageSources().inFire(), (float) (8.0 * (1.0 - along / len) + 1.0));
            le.setSecondsOnFire(3);
            le.push(-dir.x * 0.6, 0.2, -dir.z * 0.6);
        }
        for (int i = 0; i < 6; i++) {
            Vec3 q = breech.subtract(dir.scale(i * 0.9));
            sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), q.x, q.y, q.z, 3, 0.4, 0.3, 0.4, 0.02);
        }
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, breech.x, breech.y, breech.z, 8, 0.2, 0.2, 0.2, 0.05);
    }

    /** Overpressure in front of the muzzle: knocks back / hurts unprotected people standing there. */
    private void muzzleBlast(ServerLevel sl, Vec3 muzzle, Vec3 d) {
        double r = this.kind == VehicleKind.RATTE ? 12.0 : (this.kind.artillery() ? 4.0 : 3.0);
        for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(muzzle, muzzle).inflate(r))) {
            if (le.getVehicle() == this) continue;
            Vec3 rel = le.position().subtract(muzzle);
            double dist = rel.length();
            if (dist > r) continue;
            le.hurt(sl.damageSources().explosion(null, null), (float) (4.0 * (1.0 - dist / r)));
            le.push(d.x * 0.8, 0.25, d.z * 0.8);
        }
    }

    // ------------------------------------------------------------------ tracked physics (skid steer)
    private static final double TRACK_ROLL = 0.045;

    /** The 35 m Ratte (2 m road wheels, 3 belts per side) crosses 2-block steps; everything else the usual 1 block. */
    @Override
    public float maxUpStep() {
        return this.isRatte() ? 2.1f : super.maxUpStep();
    }

    @Override
    public void physics(float throttleIn, float steerIn, boolean handbrake) {
        if (!this.cfg.tracked()) {
            this.speedMs = 0.0;
            this.vy = this.onGround() ? -0.05 : (this.vy - 0.08) * 0.98;
            this.move(MoverType.SELF, new Vec3(0.0, this.vy, 0.0));
            if (this.verticalCollision) this.vy = 0.0;
            return;
        }
        double mass = this.kind.massKg;
        int ground = this.tickCount % 10 == 0 || this.groundCache < 0 ? (this.groundCache = Terrain.under(this)) : this.groundCache;   // r21 mud / snow
        boolean trk = this.cfg.tracked();
        double vmax = this.kind.vmaxKmh / 3.6 * Terrain.vmaxMul(ground, trk);
        boolean run = this.engineOn() && this.fuel() > 0.0f && this.canDrive() && ru.aiforge.realwar.damage.VehicleDamage.canDrive(this);
        float thr = run ? throttleIn : 0.0f;
        double v = this.speedMs;
        double F = 0.0;
        double power = this.kind.powerKw * 1000.0 * 0.78 * ru.aiforge.realwar.damage.VehicleDamage.powerFactor(this);
        // r10: maxAccel is the NET acceleration on flat ground - the track rolling resistance is added on top (before, the
        // Maus had 0.45 - 0.44 = 0.01 m/s2 left and the Ratte could not move at all)
        double fmax = mass * (this.kind.maxAccel + TRACK_ROLL * 9.81);
        if (thr > 0.01f) {
            F = v >= -0.3 ? Math.min(fmax, power / Math.max(Math.abs(v), 1.5)) * thr : mass * 6.0 * thr;
        } else if (thr < -0.01f) {
            F = v > 0.5 ? -mass * 6.0 * (-thr) : -Math.min(fmax * 0.6, power / Math.max(Math.abs(v), 2.0)) * (-thr);
        }
        // steering input slews the skid (track speed difference)
        this.steerVis += Mth.clamp((run ? steerIn : 0.0f) - this.steerVis, -0.15f, 0.15f);
        double skid = Math.abs(this.steerVis);
        double roll = TRACK_ROLL * mass * 9.81 * Terrain.rollMul(ground, trk);   // tracks: much higher rolling resistance than tyres
        double aero = 3.0 * v * v;
        double resist = roll + aero + skid * 0.10 * mass * 9.81 * Math.min(1.0, Math.abs(v))
                + (thr == 0.0f ? 0.05 * mass * 9.81 * Math.min(1.0, Math.abs(v)) : 0.0);
        if (handbrake || !run && thr == 0.0f) {
            resist += mass * 6.0 * Math.min(1.0, Math.abs(v));
        }
        if (Math.abs(v) < 0.05 && Math.abs(F) < resist) {
            v = 0.0;
        } else {
            double a = (F - Math.signum(v == 0.0 ? F : v) * resist) / mass;
            double nv = v + a * 0.05;
            if (v != 0.0 && Math.signum(nv) != Math.signum(v) && Math.abs(F) < resist + 1.0E-6) nv = 0.0;
            v = nv;
        }
        v = Mth.clamp(v, -4.5, vmax);
        if (this.isInWater()) v *= 0.9;
        double frac = Math.min(1.0, Math.abs(v) / vmax);
        double rate = this.cfg.pivotDegS * (1.0 - 0.6 * frac);
        double dir = v < -0.3 ? -1.0 : 1.0;                        // car-like steering when reversing
        this.setYRot(this.getYRot() - (float) (this.steerVis * rate * dir * 0.05));
        Vec3 fwd = this.forward();
        this.vy = this.onGround() ? -0.05 : (this.vy - 0.08) * 0.98;
        Vec3 before = this.position();
        this.move(MoverType.SELF, new Vec3(fwd.x * v * 0.05, this.vy, fwd.z * v * 0.05));
        if (this.verticalCollision) this.vy = 0.0;
        double got = this.position().subtract(before).horizontalDistance();
        double want = Math.abs(v * 0.05);
        if (want > 0.02 && got < want * 0.4) {
            if (Math.abs(v) > 7.0 && !this.level().isClientSide) this.hurtByCrash(Math.abs(v) * 0.5);
            v *= 0.3;
        }
        this.speedMs = v;
        float heat = run ? 0.45f + 0.35f * Math.max(Math.abs(thr), (float) skid * 0.6f) : 0.15f;
        this.temp += (heat - this.temp) * 0.003f;
    }

    // ------------------------------------------------------------------ client: visuals
    @Override
    public void tick() {
        if (this.level().isClientSide) {
            float d = this.az() - this.azVis;         // unwrap so the turret never spins the long way round at +-180
            if (Math.abs(d) > 180.0f) {
                float sh = 360.0f * Math.signum(d);
                this.azVis += sh;
                this.azVisO += sh;
            }
        }
        super.tick();
        if (this.level().isClientSide && this.isRatte()) {
            this.secVisO = this.secVis;
            float d = Mth.wrapDegrees(this.secAz() - this.secVis);
            this.secVis += d;
        }
        if (this.level().isClientSide && this.cfg.tracked()) {
            this.trackLO = this.trackL;
            this.trackRO = this.trackR;
            Vec3 f = this.forward();
            double ds = (this.getX() - this.xo) * f.x + (this.getZ() - this.zo) * f.z;
            double dyaw = Math.toRadians(Mth.wrapDegrees(this.getYRot() - this.yRotO));
            this.trackL += (float) (ds + dyaw * this.cfg.trackHalf);
            this.trackR += (float) (ds - dyaw * this.cfg.trackHalf);
        }
    }

    @Override
    protected void launcherPose(Pose pose, float pt) {
        pose.translate("barrel", 0.0f, 0.0f, -this.cfg.recoil * Math.min(1.0f, this.kick * 1.15f));
        if (this.kind == VehicleKind.MARK_I) pose.translate("barrel_left", 0.0f, 0.0f, -this.cfg.recoil * Math.min(1.0f, this.kick * 1.15f));
        if (this.perTube()) {
            for (int k = 0; k < this.kind.magazine; k++) pose.hide("rkt_" + k, !this.isLoaded(k) && k != this.reloadTube());
        }
        if (this.perTube()) {
            // two telescopic elevation rams (3 stages) between the turntable (B) and the pack (A): angle + stroke
            float e0 = Mth.lerp(pt, this.elVisO, this.elVis);
            float e = this.stabilised(Mth.lerp(pt, this.azVisO, this.azVis), e0, this.pitchLerp(pt), this.rollLerp(pt))[1];
            double er = Math.toRadians(e);
            double ay = RAM_A[0] * Math.cos(er) + RAM_A[1] * Math.sin(er), az = RAM_A[1] * Math.cos(er) - RAM_A[0] * Math.sin(er);
            double dy = ay - RAM_B[0], dz = az - RAM_B[1];
            double phi = Math.atan2(dy, dz) - Math.atan2(RAM_A[0] - RAM_B[0], RAM_A[1] - RAM_B[1]);
            double l0 = Math.hypot(RAM_A[0] - RAM_B[0], RAM_A[1] - RAM_B[1]);
            double ext = Math.hypot(dy, dz) - l0;
            float uy = (float) ((RAM_A[0] - RAM_B[0]) / l0), uz = (float) ((RAM_A[1] - RAM_B[1]) / l0);
            for (String sd : new String[]{"L", "R"}) {
                pose.rotate("ram_" + sd, (float) -phi, 1.0f, 0.0f, 0.0f);
                pose.translate("ram1_" + sd, 0.0f, (float) (uy * ext * 0.5), (float) (uz * ext * 0.5));
                pose.translate("ram2_" + sd, 0.0f, (float) (uy * ext * 0.5), (float) (uz * ext * 0.5));   // child of ram1
            }
        }
        if (this.kind == VehicleKind.BRADLEY) {
            pose.hide("tow_l", this.thirdRounds() < 1);
            pose.hide("tow_r", this.thirdRounds() < 2);
        }
        if (this.kind == VehicleKind.RATTE) {
            // r9: the rear 12.8 cm turret has its own director (modelled facing aft = az 180)
            float[] ss = this.stabilised(Mth.lerp(pt, this.secVisO, this.secVis), this.secEl(), this.pitchLerp(pt), this.rollLerp(pt));
            pose.rotate("sec_yaw", (float) Math.toRadians(ss[0] - 180.0f), 0.0f, 1.0f, 0.0f);
            pose.rotate("sec_pitch", (float) Math.toRadians(ss[1]), 1.0f, 0.0f, 0.0f);
            // Flak 38 mounts track the battery's target (each from its own pedestal), idle at 10 deg elevation
            Entity tg = this.flakTarget();
            for (int i = 0; i < FLAK_POS.length; i++) {
                float fa = 0.0f, fe = 10.0f;
                if (tg != null) {
                    double[] m = FLAK_POS[i];
                    Vec3 mp = this.modelToWorld(m[0], m[1], m[2]);
                    Vec3 tp = flakLead(mp, tg);
                    Vec3 d = tp.subtract(mp);
                    fa = this.azRelFromWorldYaw((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
                    fe = (float) Mth.clamp(Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())), -5.0, 87.0);
                }
                pose.rotate("flak_" + i, (float) Math.toRadians(fa), 0.0f, 1.0f, 0.0f);
                pose.rotate("flak_" + i + "_p", (float) -Math.toRadians(fe), 1.0f, 0.0f, 0.0f);
            }
        }
        if (!this.cfg.tracked()) {
            return;
        }
        float sl = Mth.lerp(pt, this.trackLO, this.trackL);
        float sr = Mth.lerp(pt, this.trackRO, this.trackR);
        Cfg c = this.cfg;
        for (int i = 0; i < c.nRoad; ++i) {
            pose.rotate("rw_L_" + i, -sl / c.rRoad, 1.0f, 0.0f, 0.0f);
            pose.rotate("rw_R_" + i, -sr / c.rRoad, 1.0f, 0.0f, 0.0f);
        }
        for (int i = 0; i < c.nRr; ++i) {
            pose.rotate("rr_L_" + i, -sl / c.rRr, 1.0f, 0.0f, 0.0f);
            pose.rotate("rr_R_" + i, -sr / c.rRr, 1.0f, 0.0f, 0.0f);
        }
        pose.rotate("spk_L", -sl / c.rSpk, 1.0f, 0.0f, 0.0f);
        pose.rotate("spk_R", -sr / c.rSpk, 1.0f, 0.0f, 0.0f);
        pose.rotate("idl_L", -sl / c.rIdl, 1.0f, 0.0f, 0.0f);
        pose.rotate("idl_R", -sr / c.rIdl, 1.0f, 0.0f, 0.0f);
        if (this.kind == VehicleKind.RATTE) {      // three belts per side
            for (String b : new String[]{"b1", "b2"}) {
                pose.rotate("spk" + b + "_L", -sl / c.rSpk, 1.0f, 0.0f, 0.0f);
                pose.rotate("spk" + b + "_R", -sr / c.rSpk, 1.0f, 0.0f, 0.0f);
                pose.rotate("idl" + b + "_L", -sl / c.rIdl, 1.0f, 0.0f, 0.0f);
                pose.rotate("idl" + b + "_R", -sr / c.rIdl, 1.0f, 0.0f, 0.0f);
            }
        }
        GlbModel m = pose.model();
        GlbModel.Animation a = m.animation("track_L");
        if (a != null) pose.sample(a, frac(sl / c.pitch) * a.duration * 0.9999f);
        GlbModel.Animation b = m.animation("track_R");
        if (b != null) pose.sample(b, frac(sr / c.pitch) * b.duration * 0.9999f);
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    // ------------------------------------------------------------------ save
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(SEL, tag.getInt("sel"));
        this.entityData.set(COAX, tag.contains("coax") ? tag.getInt("coax") : COAX_MAX);
        this.entityData.set(THIRD, tag.contains("third") ? tag.getInt("third") : this.thirdMax());
        this.entityData.set(FLAK, tag.contains("flak") ? tag.getInt("flak") : FLAK_MAX);
        this.entityData.set(SMOKE, tag.contains("smoke") ? tag.getInt("smoke") : -1);
        if (tag.contains("secAz")) this.entityData.set(SEC_AZ, tag.getFloat("secAz"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("sel", this.sel());
        tag.putInt("smoke", this.smoke());
        tag.putInt("coax", this.coaxRounds());
        tag.putInt("third", this.thirdRounds());
        tag.putInt("flak", this.flakRounds());
        tag.putFloat("secAz", this.secAz());
    }
}
