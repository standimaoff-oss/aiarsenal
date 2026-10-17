package ru.aiforge.arsenal.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import ru.aiforge.arsenal.control.Controllable;
import ru.aiforge.arsenal.control.RemoteControl;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.core.Nodes;
import ru.aiforge.arsenal.core.Warheads;
import ru.aiforge.arsenal.item.GogglesItem;
import ru.aiforge.arsenal.registry.ArsenalRegistry;

import javax.annotation.Nullable;

/**
 * FPV kamikaze quadcopter flown through analog video goggles in angle mode: W/S pitch, A/D roll, mouse yaw,
 * Space/Shift throttle. 7" frame with a PG-7VL shaped charge or 10" frame with a TM-62M mine (heavier: lower thrust
 * to weight). Impact fuze + manual detonation; the analog link degrades with range and without line of sight.
 */
public class FpvDrone extends ArsEntity implements Controllable {
    public static final int IDLE = 0, FLYING = 1, FAILSAFE = 2;
    /** World gravity for the quad (b/t^2), tuned with drag for ~120 km/h (7") top speed in world units. */
    private static final double G = 0.03;
    private int keys;
    private float yawIn;
    private int battery = 7200;
    private double throttle;
    private int lastInput = -100;

    public FpvDrone(EntityType<?> type, Level level) {
        super(type, level);
    }

    public double twr() {
        return kind() == Kind.FPV10 ? 1.9 : 2.4;
    }

    public double range() {
        return kind() == Kind.FPV10 ? 1400 : 1000;
    }

    /** Link quality 0..1 (synced). */
    public float link() {
        return f1();
    }

    public float throttleSynced() {
        return f2();
    }

    public int batteryTicks() {
        return battery;
    }

    @Override
    protected boolean keepsChunks() {
        return state() != IDLE;
    }

    @Override
    public void control(ServerPlayer op, int keys, float camYaw, float camPitch, float zoom) {
        this.keys = keys;
        this.yawIn = camYaw;
        this.lastInput = age;
        if ((keys & K_DETONATE) != 0 && level() instanceof ServerLevel sl) detonate(sl, null);
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        boolean linked = operator != null && age - lastInput < 10;
        Vec3 opPos = operator == null ? null : RemoteControl.operatorPos(sl.getServer(), operator);
        // ---- link budget
        if (opPos != null && age % 5 == 0) {
            double d = opPos.distanceTo(position());
            double q = 1 - Mth.clamp((d - range() * 0.55) / (range() * 0.55), 0, 1);
            HitResult los = sl.clip(new ClipContext(opPos, position(), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this));
            if (los.getType() != HitResult.Type.MISS && los.getLocation().distanceTo(position()) > 2) q *= 0.55;
            setF1((float) (f1() * 0.5 + q * 0.5));
        } else if (opPos == null) setF1(0f);
        if (state() == FLYING && (!linked || link() < 0.06f || battery <= 0)) {
            setState(FAILSAFE);
            if (operator != null) RemoteControl.lost(sl.getServer(), operator, battery <= 0 ? "msg.aiarsenal.battery_empty" : "msg.aiarsenal.signal_lost");
        }
        // ---- inputs -> attitude / throttle
        int k = state() == FLYING ? keys : 0;
        if (state() == IDLE && linked && (keys & K_UP) != 0) {
            setState(FLYING);
            throttle = 0.3;
        }
        if (state() == FLYING) {
            if ((k & K_UP) != 0) throttle = Math.min(1, throttle + 0.035);
            if ((k & K_DOWN) != 0) throttle = Math.max(0, throttle - 0.035);
            battery -= 1 + (int) Math.round(throttle * 1.5);
            setYRot(yawIn);
        } else if (state() == FAILSAFE) throttle = 0;
        setF2((float) throttle);
        float maxTilt = (k & K_BOOST) != 0 ? 70f : 50f;
        float wantP = (k & K_FWD) != 0 ? -maxTilt : (k & K_BACK) != 0 ? maxTilt * 0.7f : 0f;
        float wantR = (k & K_RIGHT) != 0 ? maxTilt * 0.8f : (k & K_LEFT) != 0 ? -maxTilt * 0.8f : 0f;
        setPitch(approach(pitch(), wantP, 7f));
        setRoll(approach(roll(), wantR, 7f));
        // ---- physics
        Vec3 v = getDeltaMovement();
        if (state() == IDLE) {
            v = new Vec3(0, onGroundish(sl) ? 0 : Math.max(-0.6, v.y - G), 0);
        } else {
            Vec3 up = Nodes.toWorld(new Vector3f(0, 1, 0), getYRot(), pitch(), roll());
            double thrust = throttle * twr() * G;
            v = v.add(up.scale(thrust)).add(0, -G, 0);
            double sp = v.length();
            double c = kind() == Kind.FPV10 ? 0.07 : 0.05;
            v = v.subtract(v.scale(c * sp));
        }
        setDeltaMovement(v);
        if (state() != IDLE && age % 3 == 0 && throttle > 0.2)
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() - 0.2, getZ(), 0, 0, -0.2, 0, 0.05);
        HitResult h = sweep(v, state() != IDLE);
        if (h != null) {
            double sp = v.length();
            if (h instanceof EntityHitResult eh && sp > 0.12) {
                detonate(sl, eh.getEntity());
                return;
            }
            if (sp > 0.32) {
                detonate(sl, null);
                return;
            }
            // soft landing
            setDeltaMovement(Vec3.ZERO);
            if (state() == FAILSAFE) setState(IDLE);
        }
    }

    private boolean onGroundish(ServerLevel sl) {
        return sl.getBlockState(net.minecraft.core.BlockPos.containing(getX(), getY() - 0.05, getZ())).blocksMotion();
    }

    private static float approach(float cur, float want, float step) {
        return cur + Mth.clamp(want - cur, -step, step);
    }

    public void detonate(ServerLevel sl, @Nullable Entity hit) {
        if (isRemoved()) return;
        Vec3 p = position();
        discard();
        Warheads.detonate(sl, p, kind(), this, ownerEntity(), hit);
    }

    @Override
    public void released() {
        if (state() == FLYING) setState(FAILSAFE);
        operator = null;
    }

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (!level().isClientSide && p instanceof ServerPlayer sp) {
            if (s.getItem() instanceof GogglesItem) {
                GogglesItem.link(s, this);
                owner = sp.getUUID();
                RemoteControl.start(sp, this);
            } else if (sp.isShiftKeyDown() && state() == IDLE) {
                onPickedUp(sp);
            } else {
                sp.displayClientMessage(Component.translatable("msg.aiarsenal.use_goggles"), true);
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
        if (level() instanceof ServerLevel sl) detonate(sl, null);
    }

    @Override
    protected void readMore(CompoundTag t) {
        battery = t.contains("Battery") ? t.getInt("Battery") : 7200;
        if (state() == FLYING) setState(FAILSAFE);
    }

    @Override
    protected void saveMore(CompoundTag t) {
        t.putInt("Battery", battery);
    }
}
