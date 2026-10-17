package ru.aiforge.arsenal.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import ru.aiforge.arsenal.control.Controllable;
import ru.aiforge.arsenal.control.RemoteControl;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.core.Nodes;
import ru.aiforge.arsenal.item.TabletItem;
import ru.aiforge.arsenal.registry.ArsenalRegistry;
import ru.aiforge.tubombers.fx.ImpactHandler;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * MALE reconnaissance / strike drone (MQ-9 Reaper with 8 AGM-114 Hellfire, Orion with 4 KAB-50). Flown from the
 * tablet like the real ground control station: the autopilot loiters around an orbit point the operator moves
 * (WASD relative to the camera), altitude Space/Shift, the EO/IR ball (mouse) designates targets (LMB), F releases a
 * weapon on the designation, G orbits the designation, H returns home and lands on the take-off strip.
 */
public class ReconDrone extends ArsEntity implements Controllable {
    public static final int PARKED = 0, TAKEOFF = 1, FLY = 2, RTB = 3, APPROACH = 4, ROLLOUT = 5, CRASH = 6;
    private Vec3 home = Vec3.ZERO;
    private float homeYaw;
    private Vec3 center;
    private double agl = 90;
    private double speed;
    private int keys, prevKeys;
    private float camYaw, camPitch;
    @Nullable
    private UUID designatedEntity;
    private int fireCooldown;

    public ReconDrone(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static ReconDrone create(ServerLevel sl, Kind k, Vec3 pos, float yaw, @Nullable UUID owner) {
        ReconDrone d = new ReconDrone(ArsenalRegistry.RECON.get(), sl);
        d.setKind(k);
        d.setPos(pos);
        d.setYRot(yaw);
        d.home = pos;
        d.homeYaw = yaw;
        d.owner = owner;
        d.setAux(d.maxAmmo());
        sl.addFreshEntity(d);
        return d;
    }

    public int maxAmmo() {
        return kind() == Kind.MQ9 ? 8 : 4;
    }

    public int ammo() {
        return aux();
    }

    public double radius() {
        return kind() == Kind.MQ9 ? 90 : 70;
    }

    /** Gear retraction 0 (down) .. 1 (up), synced. */
    public float gear() {
        return f2();
    }

    public float camYaw() {
        return camYaw;
    }

    public double altitudeTarget() {
        return agl;
    }

    @Override
    protected boolean keepsChunks() {
        return state() != PARKED;
    }

    @Override
    public void control(ServerPlayer op, int keys, float camYaw, float camPitch, float zoom) {
        this.prevKeys = this.keys;
        this.keys = keys;
        this.camYaw = camYaw;
        this.camPitch = camPitch;
        int pressed = keys & ~prevKeys;
        if (state() == PARKED && (pressed & K_UP) != 0) {
            setState(TAKEOFF);
            speed = 0;
            op.displayClientMessage(Component.translatable("msg.aiarsenal.takeoff"), true);
        }
        if ((pressed & K_ORBIT) != 0 && target() != null && state() >= FLY && state() <= APPROACH) {
            center = Vec3.atCenterOf(target());
            setState(FLY);
            op.displayClientMessage(Component.translatable("msg.aiarsenal.orbit_target"), true);
        }
        if ((pressed & K_HOME) != 0 && state() >= FLY && state() <= APPROACH) {
            setState(state() == FLY ? RTB : FLY);
            if (state() == FLY) center = position();
            op.displayClientMessage(Component.translatable(state() == RTB ? "msg.aiarsenal.rtb" : "msg.aiarsenal.rtb_cancel"), true);
        }
        if ((pressed & K_FIRE) != 0) fire(op);
    }

    @Override
    public void designate(ServerPlayer op, Vec3 point, @Nullable Entity hit) {
        setTarget(BlockPos.containing(point));
        designatedEntity = hit == null ? null : hit.getUUID();
        TabletItem.storeTarget(op, BlockPos.containing(point));
        level().playSound(null, op.getX(), op.getY(), op.getZ(), ArsenalRegistry.SND_BEEP.get(), SoundSource.PLAYERS, 0.6f, 1f);
    }

    private void fire(ServerPlayer op) {
        if (!(level() instanceof ServerLevel sl) || state() < FLY || state() > APPROACH) return;
        if (fireCooldown > 0) return;
        BlockPos t = target();
        if (t == null) {
            op.displayClientMessage(Component.translatable("msg.aiarsenal.a_no_target"), true);
            return;
        }
        if (ammo() <= 0) {
            op.displayClientMessage(Component.translatable("msg.aiarsenal.a_no_ammo"), true);
            return;
        }
        double range = kind() == Kind.MQ9 ? 450 : 550;
        double d = Vec3.atCenterOf(t).distanceTo(position());
        if (d > range) {
            op.displayClientMessage(Component.translatable("msg.aiarsenal.out_of_range", (int) d, (int) range), true);
            return;
        }
        int fired = maxAmmo() - ammo();
        // release order: outer left, outer right, inner left, inner right (store nodes 1,3,0,2)
        int store = new int[]{1, 3, 0, 2}[Math.min(3, kind() == Kind.MQ9 ? fired / 2 : fired)];
        float side = store < 2 ? 1f : -1f;
        float span = (store % 2 == 1 ? 4.6f : 2.9f) * (kind() == Kind.MQ9 ? 1f : 0.8f);
        Vec3 off = Nodes.toWorld(new Vector3f(side * span, -0.7f, 0.2f), getYRot(), pitch(), roll());
        Vec3 pos = position().add(off);
        Kind w = kind() == Kind.MQ9 ? Kind.HELLFIRE : Kind.KAB50;
        Vec3 vel = getDeltaMovement().add(0, -0.15, 0);
        MissileEntity.create(sl, w, pos, vel, t, designatedEntity, op.getUUID(), MissileEntity.CRUISE);
        setAux(ammo() - 1);
        fireCooldown = 20;
        sl.playSound(null, pos.x, pos.y, pos.z, w == Kind.HELLFIRE ? ArsenalRegistry.SND_LAUNCH_RAIL.get()
                : ru.aiforge.tubombers.registry.ModRegistry.SND_RELEASE.get(), SoundSource.NEUTRAL, 3f, w == Kind.HELLFIRE ? 1.6f : 1.3f);
        op.displayClientMessage(Component.translatable("msg.aiarsenal.weapon_away", Component.translatable(w.translationKey()), ammo()), true);
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        if (fireCooldown > 0) fireCooldown--;
        double vc = bpt(kind().speed);
        Vec3 v = getDeltaMovement();
        float yaw0 = getYRot();
        boolean controlled = operator != null;
        int k = controlled ? keys : 0;
        switch (state()) {
            case PARKED -> {
                v = Vec3.ZERO;
                setF2(0);
                int gr = ground(sl, getX(), getZ());
                if (getY() > gr + 0.05) setPos(getX(), Math.max(gr, getY() - 0.4), getZ());
            }
            case TAKEOFF -> {
                speed = Math.min(vc, speed + vc / 120.0);
                int gr = ground(sl, getX(), getZ());
                boolean rot = speed > 0.75 * vc;
                setPitch(rot ? Math.min(8f, pitch() + 0.4f) : 0f);
                v = dir(getYRot(), pitch()).scale(speed);
                if (!rot) v = new Vec3(v.x, 0, v.z);
                double h = getY() - gr;
                if (h > 8) setF2(Math.min(1f, f2() + 0.03f));
                if (h > 22) {
                    setState(FLY);
                    if (center == null) center = home.add(dir(homeYaw, 0).scale(radius() * 1.6));
                }
            }
            case FLY, RTB -> {
                speed += (vc - speed) * 0.02;
                if (state() == RTB) {
                    Vec3 a = approachStart();
                    center = a;
                    if (Math.hypot(a.x - getX(), a.z - getZ()) < 18) setState(APPROACH);
                }
                if (center == null) center = position();
                // operator moves the orbit point relative to the camera heading
                if (state() == FLY && (k & (K_FWD | K_BACK | K_LEFT | K_RIGHT)) != 0) {
                    double mv = (k & K_BOOST) != 0 ? 4.0 : 1.5;
                    Vec3 f = dir(camYaw, 0), r = new Vec3(-f.z, 0, f.x);
                    if ((k & K_FWD) != 0) center = center.add(f.scale(mv));
                    if ((k & K_BACK) != 0) center = center.subtract(f.scale(mv));
                    if ((k & K_RIGHT) != 0) center = center.add(r.scale(mv));     // r = right of the camera heading
                    if ((k & K_LEFT) != 0) center = center.subtract(r.scale(mv));
                }
                if ((k & K_UP) != 0) agl = Math.min(400, agl + 1.5);
                if ((k & K_DOWN) != 0) agl = Math.max(25, agl - 1.5);
                Vec3 rel = new Vec3(getX() - center.x, 0, getZ() - center.z);
                double dist = rel.length();
                Vec3 dh;
                if (state() == RTB || dist < 1) dh = new Vec3(center.x - getX(), 0, center.z - getZ());
                else {
                    Vec3 n = rel.scale(-1 / dist), t = new Vec3(-rel.z / dist, 0, rel.x / dist);
                    dh = t.add(n.scale(Mth.clamp((dist - radius()) / radius() * 2.0, -1.5, 3.0)));
                }
                dh = dh.normalize();
                Vec3 cur = dir(getYRot(), 0);
                Vec3 nh = MissileEntity.turn(cur, dh, Math.toRadians(3));
                int gr = groundAhead(sl, position(), nh, 90, 10);
                double yDes = gr + (state() == RTB ? Math.min(agl, 60) : agl);
                double vz = Mth.clamp((yDes - getY()) * 0.02, -0.3, 0.25) * vc / 1.5;
                v = nh.scale(speed).add(0, vz, 0);
                setF2(Math.min(1f, f2() + 0.03f));
            }
            case APPROACH -> {
                speed += (0.7 * vc - speed) * 0.05;
                Vec3 hd = dir(homeYaw, 0);
                double along = (home.x - getX()) * hd.x + (home.z - getZ()) * hd.z;   // remaining distance to touchdown
                double cross = (home.x - getX()) * hd.z - (home.z - getZ()) * hd.x;
                Vec3 want = hd.add(new Vec3(hd.z, 0, -hd.x).scale(Mth.clamp(cross * 0.05, -0.5, 0.5))).normalize();
                Vec3 nh = MissileEntity.turn(dir(getYRot(), 0), want, Math.toRadians(3));
                double yDes = home.y + Math.max(0, along) * Math.tan(Math.toRadians(5));
                double vz = Mth.clamp((yDes - getY()) * 0.1, -0.4, 0.1);
                v = nh.scale(speed).add(0, vz, 0);
                setF2(Math.max(0f, f2() - 0.03f));
                int gr = ground(sl, getX(), getZ());
                if (getY() <= gr + 0.3 && along < 60) {
                    setState(ROLLOUT);
                    setPos(getX(), gr, getZ());
                }
                if (along < -150) setState(RTB);   // missed: go around
            }
            case ROLLOUT -> {
                speed = Math.max(0, speed - vc / 90.0);
                v = dir(getYRot(), 0).scale(speed);
                setPitch(0);
                if (speed <= 0.01) {
                    setState(PARKED);
                    home = position();
                    homeYaw = getYRot();
                    setAux(maxAmmo());   // re-armed on the ground
                }
            }
            case CRASH -> {
                v = v.add(0, -0.05, 0).scale(0.99);
                setRoll(roll() + 6f);
                if (age % 2 == 0)
                    sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), getX(), getY(), getZ(), 2, 0.3, 0.3, 0.3, 0.01);
            }
            default -> {
            }
        }
        setDeltaMovement(v);
        if (v.horizontalDistanceSqr() > 1e-4 && state() != PARKED) {
            setYRot(yawOf(v));
            if (state() >= FLY && state() != ROLLOUT) setPitch(pitchOf(v));
        }
        if (state() != CRASH) {
            float dy = Mth.wrapDegrees(getYRot() - yaw0);
            setRoll(Mth.clamp(roll() * 0.9f - dy * 9f * 0.1f, -35f, 35f));
        }
        if (state() == PARKED) return;
        if (state() == TAKEOFF || state() == ROLLOUT) {
            int gr = ground(sl, getX() + v.x, getZ() + v.z);
            setPos(getX() + v.x, Math.max(gr, getY() + v.y), getZ() + v.z);
            return;
        }
        HitResult h = sweep(v, state() == CRASH);
        if (h != null) {
            if (state() == APPROACH && h.getLocation().y < getY() + 1) {
                setState(ROLLOUT);
                return;
            }
            crash(sl);
        }
    }

    private Vec3 approachStart() {
        return home.subtract(dir(homeYaw, 0).scale(160)).add(0, 0, 0);
    }

    private void crash(ServerLevel sl) {
        Vec3 p = position();
        if (operator != null) RemoteControl.lost(sl.getServer(), operator, "msg.aiarsenal.drone_lost");
        discard();
        ImpactHandler.crash(sl, p, kind() == Kind.MQ9 ? 3.5f : 3f, this);
        ImpactHandler.fire(sl, p, 6, 4);
    }

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (!level().isClientSide && p instanceof ServerPlayer sp) {
            if (s.getItem() instanceof TabletItem) {
                TabletItem.link(s, this);
                owner = sp.getUUID();
                RemoteControl.start(sp, this);
            } else if (sp.isShiftKeyDown() && state() == PARKED) {
                onPickedUp(sp);
            } else {
                sp.displayClientMessage(Component.translatable("msg.aiarsenal.use_tablet"), true);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    protected void onPickedUp(ServerPlayer p) {
        p.getInventory().add(new ItemStack(ArsenalRegistry.item(kind())));
        discard();
    }

    @Override
    protected void onDestroyed(DamageSource src) {
        if (!(level() instanceof ServerLevel sl)) return;
        if (state() == PARKED || state() == TAKEOFF || state() == ROLLOUT) crash(sl);
        else {
            setState(CRASH);
            if (operator != null) RemoteControl.lost(sl.getServer(), operator, "msg.aiarsenal.drone_hit");
        }
    }

    @Override
    protected void readMore(CompoundTag t) {
        home = new Vec3(t.getDouble("HX"), t.getDouble("HY"), t.getDouble("HZ"));
        homeYaw = t.getFloat("HYaw");
        if (t.contains("CX")) center = new Vec3(t.getDouble("CX"), t.getDouble("CY"), t.getDouble("CZ"));
        agl = t.contains("Agl") ? t.getDouble("Agl") : 90;
        speed = t.getDouble("Speed");
        setF2(t.getFloat("Gear"));
    }

    @Override
    protected void saveMore(CompoundTag t) {
        t.putDouble("HX", home.x);
        t.putDouble("HY", home.y);
        t.putDouble("HZ", home.z);
        t.putFloat("HYaw", homeYaw);
        if (center != null) {
            t.putDouble("CX", center.x);
            t.putDouble("CY", center.y);
            t.putDouble("CZ", center.z);
        }
        t.putDouble("Agl", agl);
        t.putDouble("Speed", speed);
        t.putFloat("Gear", f2());
    }

    @Override
    public void released() {
        operator = null;
        keys = 0;
    }
}
