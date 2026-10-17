/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.nbt.ListTag
 *  net.minecraft.nbt.Tag
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.protocol.Packet
 *  net.minecraft.network.protocol.game.ClientGamePacketListener
 *  net.minecraft.network.syncher.EntityDataAccessor
 *  net.minecraft.network.syncher.EntityDataSerializer
 *  net.minecraft.network.syncher.EntityDataSerializers
 *  net.minecraft.network.syncher.SynchedEntityData
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.tags.DamageTypeTags
 *  net.minecraft.util.Mth
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.damagesource.DamageSource
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.Entity$MoveFunction
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.ItemLike
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.levelgen.Heightmap$Types
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 *  net.minecraftforge.network.NetworkHooks
 */
package ru.aiforge.realwar.aircraft;

import ru.aiforge.realwar.aircraft.AirBombEntity;
import ru.aiforge.realwar.aircraft.AirBombType;
import ru.aiforge.realwar.aircraft.BayLayout;
import ru.aiforge.realwar.aircraft.Tu95Spec;
import ru.aiforge.realwar.blast.ConventionalBlast;
import ru.aiforge.realwar.entity.FallingBombEntity;
import ru.aiforge.realwar.registry.BombType;
import ru.aiforge.realwar.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class AircraftEntity
extends Entity {
    public static final EntityDataAccessor<Float> THROTTLE = SynchedEntityData.defineId(AircraftEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(AircraftEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(AircraftEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(AircraftEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> HP = SynchedEntityData.defineId(AircraftEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> IN_ROLL = SynchedEntityData.defineId(AircraftEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> IN_PITCH = SynchedEntityData.defineId(AircraftEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> IN_RUDDER = SynchedEntityData.defineId(AircraftEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Boolean> GEAR = SynchedEntityData.defineId(AircraftEntity.class, (EntityDataSerializer)EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> BAY = SynchedEntityData.defineId(AircraftEntity.class, (EntityDataSerializer)EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> ENGINE = SynchedEntityData.defineId(AircraftEntity.class, (EntityDataSerializer)EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> ON_GROUND = SynchedEntityData.defineId(AircraftEntity.class, (EntityDataSerializer)EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> FLAPS = SynchedEntityData.defineId(AircraftEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> LOAD = SynchedEntityData.defineId(AircraftEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> BOMB_SEL = SynchedEntityData.defineId(AircraftEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    public static final EntityDataAccessor<CompoundTag> RIDERS = SynchedEntityData.defineId(AircraftEntity.class, (EntityDataSerializer)EntityDataSerializers.COMPOUND_TAG);
    public static final double G = 9.81;
    public static final double HP_MAX = 600.0;
    public static final double EMPTY_STALL = 15.0;
    public static final double V_MAX = 46.0;
    public static final double MAX_BANK = 60.0;
    public double gamma;
    public double speedBs;
    private double bank;
    private double pitchAtt;
    private float inThr;
    private float inRoll;
    private float inPitch;
    private float inRudder;
    private boolean brake;
    private int dropCooldown;
    private final UUID[] seatUuid = new UUID[Tu95Spec.SEATS.length];
    public final Map<UUID, float[]> walkers = new HashMap<UUID, float[]>();
    private int crashGrace = 40;
    public boolean allowDismount;
    private Vec3 lastPos = Vec3.ZERO;
    public float rollVis;
    public float rollVisO;
    public float pitchVis;
    public float pitchVisO;
    public float propAngle;
    public float propAngleO;
    public float gearAnim;
    public float gearAnimO = 1.0f;
    public float bayAnim;
    public float bayAnimO;
    public float doorAnim;
    public float doorAnimO;
    public float flapAnim;
    public float flapAnimO;
    public static Function<AircraftEntity, float[]> localWalk = e -> null;
    public static Consumer<AircraftEntity> clientHook = e -> {};
    static final boolean DEBUG = false;

    public AircraftEntity(EntityType<? extends AircraftEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    protected void defineSynchedData() {
        this.entityData.define(THROTTLE, Float.valueOf(0.0f));
        this.entityData.define(ROLL, Float.valueOf(0.0f));
        this.entityData.define(PITCH, Float.valueOf(0.0f));
        this.entityData.define(SPEED, Float.valueOf(0.0f));
        this.entityData.define(HP, Float.valueOf(600.0f));
        this.entityData.define(IN_ROLL, Float.valueOf(0.0f));
        this.entityData.define(IN_PITCH, Float.valueOf(0.0f));
        this.entityData.define(IN_RUDDER, Float.valueOf(0.0f));
        this.entityData.define(GEAR, true);
        this.entityData.define(BAY, false);
        this.entityData.define(ENGINE, false);
        this.entityData.define(ON_GROUND, true);
        this.entityData.define(FLAPS, 0);
        this.entityData.define(LOAD, 0);
        this.entityData.define(BOMB_SEL, 3);
        this.entityData.define(RIDERS, new CompoundTag());
    }

    public float throttle() {
        return ((Float)this.entityData.get(THROTTLE)).floatValue();
    }

    public float roll() {
        return ((Float)this.entityData.get(ROLL)).floatValue();
    }

    public float pitch() {
        return ((Float)this.entityData.get(PITCH)).floatValue();
    }

    public float speed() {
        return ((Float)this.entityData.get(SPEED)).floatValue();
    }

    public float hp() {
        return ((Float)this.entityData.get(HP)).floatValue();
    }

    public boolean gearDown() {
        return (Boolean)this.entityData.get(GEAR);
    }

    public boolean bayOpen() {
        return (Boolean)this.entityData.get(BAY);
    }

    public boolean engineOn() {
        return (Boolean)this.entityData.get(ENGINE);
    }

    public boolean onGround() {
        return (Boolean)this.entityData.get(ON_GROUND);
    }

    public int flaps() {
        return (Integer)this.entityData.get(FLAPS);
    }

    public int bombSel() {
        return (Integer)this.entityData.get(BOMB_SEL);
    }

    public int[] counts() {
        int l = (Integer)this.entityData.get(LOAD);
        return new int[]{l & 0xFF, l >> 8 & 0xFF, l >> 16 & 0xFF, l >> 24 & 0xFF};
    }

    public void setCounts(int[] c) {
        this.entityData.set(LOAD, (c[0] & 0xFF | (c[1] & 0xFF) << 8 | (c[2] & 0xFF) << 16 | (c[3] & 0xFF) << 24));
    }

    public String load(int kind) {
        int[] c = this.counts();
        String why = BayLayout.why(c, kind);
        if (why != null) {
            return why;
        }
        int n = kind;
        c[n] = c[n] + 1;
        this.setCounts(c);
        return null;
    }

    public boolean unload(int kind) {
        int[] c = this.counts();
        if (c[kind] <= 0) {
            return false;
        }
        int n = kind;
        c[n] = c[n] - 1;
        this.setCounts(c);
        return true;
    }

    public double stallSpeed() {
        double base = 15.0 * (this.flaps() == 0 ? 1.0 : (this.flaps() == 1 ? 0.9 : 0.78));
        return base * Math.sqrt(1.0 + 0.25 * (double)BayLayout.mass(this.counts()) / 20000.0);
    }

    public double altitudeAboveGround() {
        return this.getY() - this.groundLevel(this.getX(), this.getZ());
    }

    public Vec3 toWorld(double lx, double ly, double lz) {
        return this.toWorld(lx, ly, lz, this.pitch(), this.roll(), this.getYRot());
    }

    public Vec3 toWorld(double lx, double ly, double lz, double pitchDeg, double rollDeg, double yawDeg) {
        double y = ly - (double)3.9f;
        double cr = Math.cos(Math.toRadians(rollDeg));
        double sr = Math.sin(Math.toRadians(rollDeg));
        double x1 = lx * cr - y * sr;
        double y1 = lx * sr + y * cr;
        double cp = Math.cos(Math.toRadians(-pitchDeg));
        double sp = Math.sin(Math.toRadians(-pitchDeg));
        double y2 = y1 * cp - lz * sp;
        double z2 = y1 * sp + lz * cp;
        double c = Math.cos(Math.toRadians(yawDeg));
        double s = Math.sin(Math.toRadians(yawDeg));
        return new Vec3(this.getX() + x1 * c - z2 * s, this.getY() + (y2 += (double)3.9f), this.getZ() + x1 * s + z2 * c);
    }

    public Vec3 forward() {
        double yaw = Math.toRadians(this.getYRot());
        double p = Math.toRadians(this.gamma);
        return new Vec3(-Math.sin(yaw) * Math.cos(this.gamma), Math.sin(this.gamma), Math.cos(yaw) * Math.cos(this.gamma));
    }

    public static double floorY(double z) {
        double f = (z - -24.0) / 0.5;
        int i = Mth.clamp((int)((int)Math.floor(f)), (int)0, (int)(Tu95Spec.FLOOR_Y.length - 2));
        double t = Mth.clamp((double)(f - (double)i), (double)0.0, (double)1.0);
        return Mth.lerp((double)t, (double)Tu95Spec.FLOOR_Y[i], (double)Tu95Spec.FLOOR_Y[i + 1]);
    }

    public static double halfWidth(double z) {
        double f = (z - -24.0) / 0.5;
        int i = Mth.clamp((int)((int)Math.floor(f)), (int)0, (int)(Tu95Spec.HALF_W.length - 2));
        double t = Mth.clamp((double)(f - (double)i), (double)0.0, (double)1.0);
        return Mth.lerp((double)t, (double)Tu95Spec.HALF_W[i], (double)Tu95Spec.HALF_W[i + 1]);
    }

    public static boolean walkable(double x, double z) {
        if (z < -23.4 || z > 22.4) {
            return false;
        }
        double hw = AircraftEntity.halfWidth(z) - 0.06;
        if (Math.abs(x) > hw) {
            return false;
        }
        if (z > -3.25 && z < 8.75) {
            return x >= 0.86 && x <= hw;
        }
        return true;
    }

    public int nearestSeat(double x, double z) {
        int best = -1;
        double bd = 1.25;
        for (int i = 0; i < Tu95Spec.SEATS.length; ++i) {
            double d = Math.hypot(x - (double)Tu95Spec.SEATS[i][0], z - (double)Tu95Spec.SEATS[i][1]);
            if (!(d < bd)) continue;
            bd = d;
            best = i;
        }
        return best;
    }

    public int seatOf(Entity p) {
        if (this.level().isClientSide) {
            float[] w = this.walkers.get(p.getUUID());
            return w == null ? -1 : (int)w[2];
        }
        for (int i = 0; i < this.seatUuid.length; ++i) {
            if (!p.getUUID().equals(this.seatUuid[i])) continue;
            return i;
        }
        return -1;
    }

    public UUID seatHolder(int s) {
        if (this.level().isClientSide) {
            for (Map.Entry<UUID, float[]> e : this.walkers.entrySet()) {
                if ((int)e.getValue()[2] != s) continue;
                return e.getKey();
            }
            return null;
        }
        return this.seatUuid[s];
    }

    protected boolean canAddPassenger(Entity p) {
        return this.getPassengers().size() < 10;
    }

    protected void addPassenger(Entity p) {
        super.addPassenger(p);
        if (!this.level().isClientSide) {
            this.walkers.put(p.getUUID(), new float[]{0.95f, -5.2f, -1.0f});
            this.syncRiders();
        }
    }

    protected void removePassenger(Entity p) {
        super.removePassenger(p);
        if (!this.level().isClientSide) {
            for (int i = 0; i < this.seatUuid.length; ++i) {
                if (!p.getUUID().equals(this.seatUuid[i])) continue;
                this.seatUuid[i] = null;
            }
            this.walkers.remove(p.getUUID());
            this.syncRiders();
        } else {
            this.walkers.remove(p.getUUID());
        }
    }

    private void syncRiders() {
        CompoundTag t = new CompoundTag();
        ListTag l = new ListTag();
        for (Map.Entry<UUID, float[]> e : this.walkers.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.getKey());
            c.putFloat("x", e.getValue()[0]);
            c.putFloat("z", e.getValue()[1]);
            c.putInt("s", (int)e.getValue()[2]);
            l.add(c);
        }
        t.put("r", (Tag)l);
        if (!t.equals(this.entityData.get(RIDERS))) {
            this.entityData.set(RIDERS, t);
        }
    }

    private void readRiders() {
        CompoundTag t = (CompoundTag)this.entityData.get(RIDERS);
        HashMap<UUID, float[]> m = new HashMap<UUID, float[]>();
        for (Tag tg : t.getList("r", 10)) {
            CompoundTag c = (CompoundTag)tg;
            m.put(c.getUUID("id"), new float[]{c.getFloat("x"), c.getFloat("z"), c.getInt("s")});
        }
        this.walkers.clear();
        this.walkers.putAll(m);
    }

    public float[] walkOf(Entity p) {
        float[] l;
        if (this.level().isClientSide && (l = localWalk.apply(this)) != null && p == Minecraft.getInstance().player) {
            return l;
        }
        return this.walkers.get(p.getUUID());
    }

    protected void positionRider(Entity p, Entity.MoveFunction f) {
        if (!this.hasPassenger(p)) {
            return;
        }
        float[] w = this.walkOf(p);
        if (w == null) {
            w = new float[]{0.95f, -5.2f, -1.0f};
        }
        double y = AircraftEntity.floorY(w[1]) - (w[2] >= 0.0f ? 0.3 : 0.0);
        Vec3 v = this.toWorld(w[0], y, w[1]);
        f.accept(p, v.x, v.y, v.z);
    }

    public Vec3 getDismountLocationForPassenger(LivingEntity p) {
        Vec3 w = this.toWorld(2.8, 0.0, -5.2);
        return new Vec3(w.x, Math.max(this.getY(), this.groundLevel(w.x, w.z)), w.z);
    }

    public void leave(ServerPlayer p) {
        Vec3 w = this.getDismountLocationForPassenger((LivingEntity)p);
        this.allowDismount = true;
        p.stopRiding();
        this.allowDismount = false;
        p.teleportTo(w.x, w.y, w.z);
    }

    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return this.tryBoard(player, hand) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    public boolean tryBoard(Player player, InteractionHand hand) {
        if (player.isPassenger()) {
            return false;
        }
        if (!(player instanceof ServerPlayer)) {
            return false;
        }
        ServerPlayer sp = (ServerPlayer)player;
        if (!this.onGround() || this.speed() > 3.0f) {
            sp.displayClientMessage((Component)Component.translatable((String)"msg.aiarsenal.rw.tu95.moving"), true);
            return false;
        }
        Vec3 d = this.toWorld(Tu95Spec.DOOR_POS[0], 0.0, Tu95Spec.DOOR_POS[2]);
        Vec3 vec3 = new Vec3(d.x, player.getY(), d.z);
        if (player.position().distanceTo(vec3) > 3.6) {
            sp.displayClientMessage((Component)Component.translatable((String)"msg.aiarsenal.rw.tu95.door"), true);
            return false;
        }
        boolean ok = sp.startRiding((Entity)this, true);
        if (ok) {
            sp.displayClientMessage((Component)Component.translatable((String)"msg.aiarsenal.rw.tu95.board"), true);
        }
        return ok;
    }

    public boolean handleBay(ServerPlayer p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (!this.onGround() || this.speed() > 3.0f) {
            return false;
        }
        int kind = AircraftEntity.kindOfItem(st.getItem());
        if (kind < 0) {
            if (st.isEmpty() && p.isShiftKeyDown()) {
                int[] c = this.counts();
                for (int k = 0; k < 4; ++k) {
                    for (int i = 0; i < c[k]; ++i) {
                        this.giveBack(p, k);
                    }
                    c[k] = 0;
                }
                this.setCounts(c);
                p.displayClientMessage((Component)Component.translatable((String)"msg.aiarsenal.rw.tu95.unloaded"), true);
                return true;
            }
            return false;
        }
        int n = p.isShiftKeyDown() ? st.getCount() : 1;
        int done = 0;
        String why = null;
        for (int i = 0; i < n && (why = this.load(kind)) == null; ++i) {
            ++done;
        }
        if (done > 0 && !p.getAbilities().instabuild) {
            st.shrink(done);
        }
        if (done == 0) {
            p.displayClientMessage((Component)Component.translatable((String)("msg.aiarsenal.rw.tu95.full." + why)), true);
            return true;
        }
        int[] c = this.counts();
        p.displayClientMessage((Component)Component.translatable((String)"msg.aiarsenal.rw.tu95.loaded", (Object[])new Object[]{(double)BayLayout.mass(c) / 1000.0}), true);
        return true;
    }

    private void giveBack(ServerPlayer p, int kind) {
        Item it = AircraftEntity.itemOfKind(kind);
        ItemStack s = new ItemStack((ItemLike)it);
        if (!p.getInventory().add(s)) {
            p.drop(s, false);
        }
    }

    public static int kindOfItem(Item it) {
        if (it == ModItems.RDS_4.get()) {
            return 0;
        }
        if (it == ModItems.airBomb(AirBombType.FAB5000).get()) {
            return 1;
        }
        if (it == ModItems.airBomb(AirBombType.FAB1500).get()) {
            return 2;
        }
        if (it == ModItems.airBomb(AirBombType.FAB500).get()) {
            return 3;
        }
        return -1;
    }

    public static Item itemOfKind(int k) {
        return switch (k) {
            case 0 -> (Item)ModItems.RDS_4.get();
            case 1 -> (Item)ModItems.airBomb(AirBombType.FAB5000).get();
            case 2 -> (Item)ModItems.airBomb(AirBombType.FAB1500).get();
            default -> (Item)ModItems.airBomb(AirBombType.FAB500).get();
        };
    }

    public void handleControl(ServerPlayer p, float thr, float roll, float pitch, float rudder, boolean brakeHeld, boolean fire, int actions, float lx, float lz) {
        boolean canRelease;
        boolean flightCrew;
        double d;
        float[] w = this.walkers.get(p.getUUID());
        if (w == null) {
            return;
        }
        int seat = this.seatOf((Entity)p);
        if (seat < 0 && (d = Math.hypot(lx - w[0], lz - w[1])) < 4.0 && AircraftEntity.walkable(lx, lz)) {
            w[0] = lx;
            w[1] = lz;
        }
        boolean pilot = seat == 0 || seat == 1 && this.seatUuid[0] == null;
        boolean bl = flightCrew = pilot || seat == 3;
        if (pilot) {
            this.inThr = thr;
            this.inRoll = roll;
            this.inPitch = pitch;
            this.inRudder = rudder;
            this.brake = brakeHeld;
        }
        if ((actions & 1) != 0) {
            this.toggleSeat(p, w, seat);
        }
        if (flightCrew) {
            if ((actions & 2) != 0) {
                this.entityData.set(ENGINE, !this.engineOn());
            }
            if ((actions & 4) != 0) {
                this.toggleGear(p);
            }
            if ((actions & 8) != 0) {
                this.entityData.set(FLAPS, ((this.flaps() + 1) % 3));
            }
            if ((actions & 0x10) != 0) {
                this.entityData.set(BAY, !this.bayOpen());
            }
        }
        if ((actions & 0x20) != 0 && (flightCrew || seat == 2)) {
            this.cycleBomb();
        }
        boolean bl2 = canRelease = seat == 2 || pilot && this.seatUuid[2] == null;
        if (fire && canRelease) {
            this.release((Entity)p);
        }
        this.syncRiders();
    }

    private void toggleGear(ServerPlayer p) {
        if (this.onGround() && this.speed() > 1.0f) {
            p.displayClientMessage((Component)Component.translatable((String)"msg.aiarsenal.rw.tu95.gear_ground"), true);
            return;
        }
        this.entityData.set(GEAR, !this.gearDown());
    }

    private void toggleSeat(ServerPlayer p, float[] w, int seat) {
        ServerLevel sl;
        Level level;
        if (seat >= 0) {
            this.seatUuid[seat] = null;
            w[2] = -1.0f;
            this.inThr = 0.0f;
            if (seat == 0 || seat == 1) {
                this.inRudder = 0.0f;
                this.inPitch = 0.0f;
                this.inRoll = 0.0f;
                this.brake = false;
            }
            return;
        }
        if (Math.hypot((double)w[0] - 0.95, (double)w[1] + 5.2) < 1.5) {
            if (this.onGround() && this.speed() < 2.0f) {
                this.leave(p);
            } else {
                p.displayClientMessage((Component)Component.translatable((String)"msg.aiarsenal.rw.tu95.no_exit"), true);
            }
            return;
        }
        int s = this.nearestSeat(w[0], w[1]);
        if (s < 0) {
            p.displayClientMessage((Component)Component.translatable((String)"msg.aiarsenal.rw.tu95.no_seat"), true);
            return;
        }
        if (this.seatUuid[s] != null && (level = this.level()) instanceof ServerLevel && (sl = (ServerLevel)level).getPlayerByUUID(this.seatUuid[s]) != null) {
            p.displayClientMessage((Component)Component.translatable((String)"msg.aiarsenal.rw.seat_taken"), true);
            return;
        }
        this.seatUuid[s] = p.getUUID();
        w[0] = Tu95Spec.SEATS[s][0];
        w[1] = Tu95Spec.SEATS[s][1];
        w[2] = s;
        p.displayClientMessage((Component)Component.translatable((String)("msg.aiarsenal.rw.tu95.seat." + Tu95Spec.SEAT_ROLES[s])), true);
    }

    /** r20: number keys 1..4 pick the bomb type directly (RDS-1, FAB-5000, FAB-1500, FAB-500). */
    public void selectBomb(int k) {
        int[] c = this.counts();
        if (k >= 0 && k < 4 && c[k] > 0) this.entityData.set(BOMB_SEL, k);
    }

    private void cycleBomb() {
        int[] c = this.counts();
        int sel = this.bombSel();
        for (int i = 1; i <= 4; ++i) {
            int k = (sel + i) % 4;
            if (c[k] <= 0) continue;
            this.entityData.set(BOMB_SEL, k);
            return;
        }
    }

    public boolean release(Entity by) {
        int kind;
        Level level;
        if (this.dropCooldown > 0 || !((level = this.level()) instanceof ServerLevel)) {
            return false;
        }
        ServerLevel sl = (ServerLevel)level;
        if (!this.bayOpen()) {
            if (by instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer)by;
                sp.displayClientMessage((Component)Component.translatable((String)"msg.aiarsenal.rw.tu95.bay_closed"), true);
            }
            return false;
        }
        int[] c = this.counts();
        if (c[kind = this.bombSel()] <= 0) {
            kind = -1;
            for (int i = 0; i < 4; ++i) {
                if (c[i] <= 0) continue;
                kind = i;
                break;
            }
            if (kind < 0) {
                return false;
            }
            this.entityData.set(BOMB_SEL, kind);
        }
        List<BayLayout.Slot> slots = BayLayout.slots(c);
        BayLayout.Slot s = null;
        for (BayLayout.Slot x : slots) {
            if (x.kind() != kind) continue;
            s = x;
        }
        if (s == null) {
            return false;
        }
        Vec3 at = this.toWorld(s.x(), (double)s.y() - 0.1, s.z());
        Vec3 v = this.forward().scale(this.speedBs / 20.0).add(0.0, -0.05, 0.0);
        Direction face = Direction.fromYRot((double)this.getYRot());
        if (kind == 0) {
            sl.addFreshEntity((Entity)FallingBombEntity.drop((Level)sl, at, v, BombType.RDS_4, face));
        } else {
            AirBombType t = kind == 1 ? AirBombType.FAB5000 : (kind == 2 ? AirBombType.FAB1500 : AirBombType.FAB500);
            sl.addFreshEntity((Entity)AirBombEntity.drop((Level)sl, at, v, t));
        }
        int n = kind;
        c[n] = c[n] - 1;
        this.setCounts(c);
        this.dropCooldown = 6;
        return true;
    }

    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.clientTick();
            return;
        }
        this.serverTick(0.05);
    }

    public double groundLevel(double x, double z) {
        int h = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor((double)x), Mth.floor((double)z));
        return h;
    }

    private double gearGround() {
        double yaw = Math.toRadians(this.getYRot());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double lx = Math.cos(yaw);
        double lz = Math.sin(yaw);
        double g = this.groundLevel(this.getX(), this.getZ());
        g = Math.max(g, this.groundLevel(this.getX() + fx * 15.0, this.getZ() + fz * 15.0));
        for (int s : new int[]{-1, 1}) {
            g = Math.max(g, this.groundLevel(this.getX() + lx * 5.7 * (double)s - fx * 6.5, this.getZ() + lz * 5.7 * (double)s - fz * 6.5));
        }
        return g;
    }

    public void serverTick(double dt) {
        boolean slow;
        double thr;
        if (this.dropCooldown > 0) {
            --this.dropCooldown;
        }
        if (this.crashGrace > 0) {
            --this.crashGrace;
        }
        double V = this.speedBs;
        double vs = this.stallSpeed();
        double d = thr = this.engineOn() ? (double)this.throttle() : 0.0;
        if (this.inThr != 0.0f) {
            this.entityData.set(THROTTLE, Float.valueOf(Mth.clamp((float)(this.throttle() + this.inThr * 0.45f * (float)dt), (float)0.0f, (float)1.0f)));
        }
        if (this.engineOn() || !(this.throttle() > 0.0f) || V < 0.5) {
            // empty if block
        }
        boolean ground = this.onGround();
        double ySoil = this.gearGround();
        double yawRad = Math.toRadians(this.getYRot());
        boolean gearUp = !this.gearDown();
        double drag = 1.98E-4 * V * V;
        double thrust = thr * 0.34 * 9.81 * Math.max(0.0, 1.0 - V / 70.0);
        if (this.gamma > 0.0 && !ground) {
            thrust *= 1.0;
        }
        if (ground) {
            double fric = gearUp ? 5.395500000000001 * (double)(V > 0.1 ? 1 : 0) : (this.brake ? 0.3 : 0.018) * 9.81 * (double)(V > 0.05 ? 1 : 0);
            V = Math.max(0.0, V + (thrust - drag - fric) * dt);
            double steer = Mth.clamp((float)this.inRudder, (float)-1.0f, (float)1.0f);
            double rate = steer * (V < 25.0 ? Math.toRadians(14.0) * Math.min(1.0, V / 4.0) * (1.0 - V / 30.0) : 0.0);
            yawRad += rate * dt;
            this.bank += (0.0 - this.bank) * 0.4;
            double vr = vs * 1.07;
            double pitchWant = 0.0;
            if (V > vs * 0.65 && this.inPitch > 0.1f) {
                pitchWant = 10.0 * (double)this.inPitch;
            }
            this.pitchAtt += (pitchWant - this.pitchAtt) * 0.12;
            if (V > vr && this.inPitch > 0.15f && !gearUp) {
                this.gamma = Math.toRadians(1.5);
                this.setGrounded(false);
                ground = false;
            } else if (V > vr * 1.35 && !gearUp) {
                this.gamma = Math.toRadians(0.6);
                this.setGrounded(false);
                ground = false;
            }
            if (ground) {
                this.gamma = 0.0;
                if (gearUp && V > 2.0 && this.crashGrace == 0) {
                    this.damage(V * 0.5 * dt * 20.0);
                }
            }
        }
        if (!ground) {
            double c = Math.max(Math.cos(this.gamma), 0.05);
            double phi = Math.toRadians(this.bank);
            this.bank = (double)Math.abs(this.inRoll) > 0.05 ? (this.bank += (double)this.inRoll * 40.0 * dt) : (this.bank += (0.0 - this.bank) * 0.25 * dt);
            this.bank = Mth.clamp((double)this.bank, (double)-60.0, (double)60.0);
            phi = Math.toRadians(this.bank);
            double nmax = Math.max(0.05, V / vs * (V / vs));
            double n0 = Math.cos(this.gamma) / Math.max(Math.cos(phi), 0.3);
            double n = n0 + (this.inPitch > 0.0f ? (double)this.inPitch * 1.3 : (double)this.inPitch * 1.1);
            n = Mth.clamp((double)n, (double)-0.8, (double)nmax);
            double Vsafe = Math.max(V, 6.0);
            this.gamma += 9.81 / Vsafe * (n * Math.cos(phi) - Math.cos(this.gamma)) * dt;
            this.gamma = Mth.clamp((double)this.gamma, (double)-1.2, (double)1.2);
            yawRad += 9.81 * Math.tan(phi) / Vsafe * dt;
            yawRad += Math.toRadians(6.0) * (double)this.inRudder * dt * (double)(V > 8.0 ? 1 : 0);
            double ind = 2.0E-4 * (n * n - 1.0) * V * V * 0.15;
            V += (thrust - drag - Math.max(ind, 0.0) - 9.81 * Math.sin(this.gamma)) * dt;
            V = Mth.clamp((double)V, (double)0.0, (double)58.0);
            double alpha = Mth.clamp((double)(n * (vs / Math.max(V, 1.0)) * (vs / Math.max(V, 1.0))), (double)0.0, (double)1.2) * 13.0;
            this.pitchAtt = Math.toDegrees(this.gamma) + alpha - 2.0;
        }
        this.speedBs = V;
        double horiz = V * Math.cos(this.gamma) * dt;
        double vert = V * Math.sin(this.gamma) * dt;
        this.setYRot((float)Math.toDegrees(yawRad));
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double nx = this.getX() + fx * horiz;
        double nz = this.getZ() + fz * horiz;
        double ny = this.getY() + vert;
        Vec3 old = this.position();
        this.setPos(nx, ny, nz);
        double soil = this.gearGround();
        if (!ground) {
            if (this.getY() <= soil + 0.02) {
                boolean hard;
                double sink = -V * Math.sin(this.gamma);
                boolean bl = hard = sink > 3.5 || Math.abs(this.bank) > 12.0 || V > 47.4;
                if (this.crashGrace == 0 && (gearUp ? sink > 2.2 || V > 22.0 : hard)) {
                    this.crash();
                    return;
                }
                if (gearUp) {
                    this.damage(40.0);
                }
                this.setPos(this.getX(), soil, this.getZ());
                this.gamma = 0.0;
                this.setGrounded(true);
                this.bank = 0.0;
            }
        } else {
            this.setPos(this.getX(), soil, this.getZ());
            if (this.getY() - ySoil > 0.3) {
                this.setGrounded(false);
            }
        }
        if (this.crashGrace == 0 && V > 8.0 && this.obstacle()) {
            this.crash();
            return;
        }
        double roll = this.bank;
        if (ground || this.onGround()) {
            roll = 0.0;
        }
        this.entityData.set(ROLL, Float.valueOf((float)roll));
        this.entityData.set(PITCH, Float.valueOf((float)(this.onGround() ? this.pitchAtt : Mth.clamp((double)this.pitchAtt, (double)-60.0, (double)60.0))));
        this.entityData.set(SPEED, Float.valueOf((float)V));
        this.entityData.set(IN_ROLL, Float.valueOf(this.inRoll));
        this.entityData.set(IN_PITCH, Float.valueOf(this.inPitch));
        this.entityData.set(IN_RUDDER, Float.valueOf(this.inRudder));
        this.setXRot(0.0f);
        boolean bl = slow = V < 2.0 && this.onGround();
        if (this.getY() > 600.0) {
            this.setPos(this.getX(), 600.0, this.getZ());
        }
        this.lastPos = old;
    }

    private void setGrounded(boolean g) {
        this.entityData.set(ON_GROUND, g);
    }

    private boolean obstacle() {
        double[][] pts;
        double yaw = Math.toRadians(this.getYRot());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double lx = Math.cos(yaw);
        double lz = Math.sin(yaw);
        double sb = Math.sin(Math.toRadians(this.roll()));
        for (double[] p : pts = new double[][]{{0.0, 24.0, 3.9}, {-25.0, -2.0, 3.9 - 25.0 * sb}, {25.0, -2.0, 3.9 + 25.0 * sb}, {0.0, -24.0, 4.3}}) {
            double top;
            double wx = this.getX() + lx * p[0] + fx * p[1];
            double wz = this.getZ() + lz * p[0] + fz * p[1];
            if (!this.level().hasChunkAt(new BlockPos(Mth.floor((double)wx), 64, Mth.floor((double)wz))) || !((top = this.groundLevel(wx, wz)) > this.getY() + p[2] + 0.2)) continue;
            return true;
        }
        return false;
    }

    public void damage(double amount) {
        float h = this.hp() - (float)amount;
        this.entityData.set(HP, Float.valueOf(h));
        if (h <= 0.0f) {
            this.crash();
        }
    }

    public boolean hurt(DamageSource src, float amount) {
        if (this.level().isClientSide || this.isRemoved()) {
            return false;
        }
        if (src.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        Entity entity = src.getEntity();
        if (entity instanceof Player) {
            Player pl = (Player)entity;
            if (pl.getAbilities().instabuild && pl.isShiftKeyDown()) {
                this.removeAircraft(true);
                return true;
            }
        }
        this.damage((double)amount * (src.is(DamageTypeTags.IS_EXPLOSION) ? 1.0 : 0.25));
        return true;
    }

    public void crash() {
        ServerLevel sl;
        block7: {
            block6: {
                Level level = this.level();
                if (!(level instanceof ServerLevel)) break block6;
                sl = (ServerLevel)level;
                if (!this.isRemoved()) break block7;
            }
            return;
        }
        int[] c = this.counts();
        double mass = 1800.0 + 0.8 * ((double)c[1] * 2400.0 + (double)c[2] * 760.0 + (double)c[3] * 240.0);
        Vec3 p = this.position().add(0.0, 3.0, 0.0);
        for (Entity e : new ArrayList<>(this.getPassengers())) {
            if (e instanceof Player) {
                Player pl = (Player)e;
                if (!pl.getAbilities().instabuild) {
                    this.allowDismount = true;
                    e.stopRiding();
                    this.allowDismount = false;
                    e.hurt(sl.damageSources().generic(), 1000.0f);
                    continue;
                }
            }
            this.allowDismount = true;
            e.stopRiding();
            this.allowDismount = false;
        }
        this.discard();
        ConventionalBlast.explode(sl, p, mass, 0.8);
    }

    public void removeAircraft(boolean dropItem) {
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        int[] c = this.counts();
        for (int k = 0; k < 4; ++k) {
            for (int i = 0; i < c[k]; ++i) {
                this.spawnAtLocation(new ItemStack((ItemLike)AircraftEntity.itemOfKind(k)));
            }
        }
        if (dropItem) {
            this.spawnAtLocation(new ItemStack((ItemLike)ModItems.TU95_VEHICLE.get()));
        }
        for (Entity e : new ArrayList<>(this.getPassengers())) {
            this.allowDismount = true;
            e.stopRiding();
            this.allowDismount = false;
        }
        this.discard();
    }

    private void clientTick() {
        this.readRiders();
        this.rollVisO = this.rollVis;
        this.pitchVisO = this.pitchVis;
        this.propAngleO = this.propAngle;
        this.gearAnimO = this.gearAnim;
        this.bayAnimO = this.bayAnim;
        this.doorAnimO = this.doorAnim;
        this.flapAnimO = this.flapAnim;
        this.rollVis = Mth.lerp((float)0.5f, (float)this.rollVis, (float)this.roll());
        this.pitchVis = Mth.lerp((float)0.5f, (float)this.pitchVis, (float)this.pitch());
        float rpm = this.engineOn() ? 0.35f + 0.65f * this.throttle() : 0.0f;
        this.propAngle += rpm * 70.0f * 0.05f * 6.2832f / 3.0f;
        float gTarget = this.gearDown() ? 1.0f : 0.0f;
        this.gearAnim += Mth.clamp((float)(gTarget - this.gearAnim), (float)-0.04f, (float)0.04f);
        this.bayAnim += Mth.clamp((float)((this.bayOpen() ? 1.0f : 0.0f) - this.bayAnim), (float)-0.06f, (float)0.06f);
        boolean door = this.onGround() && this.speed() < 2.0f;
        this.doorAnim += Mth.clamp((float)((door ? 1.0f : 0.0f) - this.doorAnim), (float)-0.08f, (float)0.08f);
        this.flapAnim += Mth.clamp((float)((float)this.flaps() / 2.0f - this.flapAnim), (float)-0.03f, (float)0.03f);
        clientHook.accept(this);
    }

    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(30.0, 6.0, 30.0);
    }

    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 490000.0;
    }

    public boolean isPickable() {
        return !this.isRemoved();
    }

    public boolean canBeCollidedWith() {
        return false;
    }

    public boolean isPushable() {
        return false;
    }

    public boolean isAttackable() {
        return true;
    }

    public boolean isPushedByFluid() {
        return false;
    }

    public ItemStack getPickResult() {
        return new ItemStack((ItemLike)ModItems.TU95_VEHICLE.get());
    }

    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(HP, Float.valueOf(tag.contains("hp") ? tag.getFloat("hp") : 600.0f));
        this.entityData.set(LOAD, tag.getInt("load"));
        this.entityData.set(GEAR, !tag.contains("gear") || tag.getBoolean("gear"));
        this.speedBs = tag.getDouble("v");
        this.gamma = tag.getDouble("g");
        this.bank = tag.getDouble("bank");
        this.entityData.set(ON_GROUND, !tag.contains("ground") || tag.getBoolean("ground"));
        this.entityData.set(ENGINE, tag.getBoolean("eng"));
        this.entityData.set(THROTTLE, Float.valueOf(tag.getFloat("thr")));
    }

    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("hp", this.hp());
        tag.putInt("load", ((Integer)this.entityData.get(LOAD)).intValue());
        tag.putBoolean("gear", this.gearDown());
        tag.putDouble("v", this.speedBs);
        tag.putDouble("g", this.gamma);
        tag.putDouble("bank", this.bank);
        tag.putBoolean("ground", this.onGround());
        tag.putBoolean("eng", this.engineOn());
        tag.putFloat("thr", this.throttle());
    }

    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket((Entity)this);
    }

    public static final class Act {
        public static final int SEAT = 1;
        public static final int ENGINE = 2;
        public static final int GEAR = 4;
        public static final int FLAPS = 8;
        public static final int BAY = 16;
        public static final int BOMBSEL = 32;

        private Act() {
        }
    }
}

