package ru.aiforge.arsenal.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.core.Warheads;
import ru.aiforge.arsenal.item.TabletItem;
import ru.aiforge.arsenal.registry.ArsenalRegistry;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Loitering munitions: Shahed-136 / -131 (piston pusher), Shahed-238 (turbojet) launched from the rail with a RATO
 * booster; An-196 Liutyi takes off from the ground on its fixed tricycle gear. Cruise at low altitude following the
 * terrain with a slight weave (pre-programmed route), then a steep terminal dive onto the coordinates.
 */
public class ShahedDrone extends ArsEntity {
    public static final int GROUND = 0, BOOST = 1, CRUISE = 2, DIVE = 3, TAKEOFF = 4, FALLING = 5;
    public static final int B_BOOSTER = 1;
    private int phaseT;
    private double speed;
    private int fuel;
    private float seed;

    public ShahedDrone(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static ShahedDrone create(ServerLevel sl, Kind k, Vec3 pos, float yaw, float pitch, @Nullable BlockPos target, @Nullable UUID owner,
                                     int state) {
        ShahedDrone d = new ShahedDrone(ArsenalRegistry.SHAHED.get(), sl);
        d.setKind(k);
        d.setPos(pos);
        d.setYRot(yaw);
        d.setPitch(pitch);
        d.setTarget(target);
        d.owner = owner;
        d.setState(state);
        d.fuel = k == Kind.S238 ? 18000 : 30000;
        d.seed = sl.random.nextFloat() * 100f;
        if (state == BOOST) d.setAux(B_BOOSTER);
        sl.addFreshEntity(d);
        return d;
    }

    public boolean boosterAttached() {
        return (aux() & B_BOOSTER) != 0;
    }

    public boolean engineRunning() {
        return state() != GROUND && state() != FALLING && fuel > 0;
    }

    public void launch(@Nullable BlockPos target) {
        if (state() != GROUND) return;
        setTarget(target);
        setState(kind() == Kind.LIUTYI ? TAKEOFF : BOOST);
        if (state() == BOOST) setAux(B_BOOSTER);
        phaseT = 0;
    }

    @Override
    protected boolean keepsChunks() {
        return state() != GROUND;
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        phaseT++;
        Kind k = kind();
        double vc = bpt(k.speed);
        double g = 9.81 * scale() / 400.0;
        Vec3 v = getDeltaMovement();
        Vec3 d = v.lengthSqr() > 1e-6 ? v.normalize() : dir(getYRot(), pitch());
        float yaw0 = getYRot();
        if (engineRunning()) fuel--;
        BlockPos tgt = target();
        Vec3 aim = tgt == null ? null : Vec3.atCenterOf(tgt);
        switch (state()) {
            case GROUND -> {
                v = Vec3.ZERO;
                if (!level().getBlockState(BlockPos.containing(getX(), getY() - 0.2, getZ())).blocksMotion()) v = new Vec3(0, -0.3, 0);
            }
            case TAKEOFF -> {
                speed = Math.min(vc, speed + vc / 160.0);
                int gr = ground(sl, getX(), getZ());
                boolean rotate = speed > vc * 0.7;
                float p = rotate ? Math.min(12f, pitch() + 0.6f) : 0f;
                setPitch(p);
                v = dir(getYRot(), p).scale(speed);
                if (!rotate) v = new Vec3(v.x, getY() > gr + 0.05 ? -0.2 : 0, v.z);
                if (getY() > gr + 15) {
                    setState(CRUISE);
                    phaseT = 0;
                }
            }
            case BOOST -> {
                speed = Math.min(vc * 0.95, speed + vc / 25.0);
                v = d.scale(speed);
                Vec3 back = position().subtract(d.scale(1.6));
                sl.sendParticles(ParticleTypes.FLAME, back.x, back.y, back.z, 3, 0.05, 0.05, 0.05, 0.01);
                sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), back.x, back.y, back.z, 4, 0.15, 0.15, 0.15, 0.02);
                if (phaseT >= 45) {
                    setAux(0);
                    setState(CRUISE);
                    phaseT = 0;
                    sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), back.x, back.y, back.z, 15, 0.3, 0.3, 0.3, 0.05);
                }
            }
            case CRUISE -> {
                if (fuel <= 0) {
                    setState(FALLING);
                    break;
                }
                speed += (vc - speed) * 0.02;
                Vec3 dh = new Vec3(d.x, 0, d.z);
                dh = dh.lengthSqr() < 1e-6 ? dir(getYRot(), 0) : dh.normalize();
                double agl = k == Kind.S238 ? 70 : 50;
                if (aim != null) {
                    double hd = Math.hypot(aim.x - getX(), aim.z - getZ());
                    if (hd < (getY() - aim.y) * 1.1 + 25) {
                        setState(DIVE);
                        phaseT = 0;
                    }
                    double weave = Math.sin(age * 0.008 + seed) * Math.toRadians(hd > 300 ? 12 : 0);
                    Vec3 to = new Vec3(aim.x - getX(), 0, aim.z - getZ()).normalize();
                    to = to.yRot((float) weave);
                    dh = MissileEntity.turn(dh, to, Math.toRadians(k == Kind.S238 ? 1.5 : 2.5));
                }
                int gr = groundAhead(sl, position(), dh, Math.min(160, speed * 50), 8);
                double vz = Mth.clamp((gr + agl - getY()) * 0.03, -0.25, 0.3);
                d = MissileEntity.turn(d, dh.add(0, vz, 0), Math.toRadians(3));
                v = d.scale(speed);
            }
            case DIVE -> {
                if (aim == null) {
                    setState(CRUISE);
                    break;
                }
                speed = Math.min(vc * 1.6, speed + g * 0.6);
                d = MissileEntity.turn(d, aim.subtract(position()), Math.toRadians(7));
                v = d.scale(speed);
            }
            case FALLING -> {
                v = v.add(0, -g * 2, 0).scale(0.99);
                setRoll(roll() + 9f);
                if (age % 2 == 0) sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), getX(), getY(), getZ(), 1, 0.1, 0.1, 0.1, 0.01);
            }
            default -> {
            }
        }
        setDeltaMovement(v);
        if (v.lengthSqr() > 1e-4 && state() != GROUND && state() != TAKEOFF) {
            setYRot(yawOf(v));
            setPitch(pitchOf(v));
        }
        if (state() != FALLING) {
            float dy = Mth.wrapDegrees(getYRot() - yaw0);
            setRoll(Mth.clamp(roll() * 0.85f - dy * 12f * 0.15f, -50f, 50f));
        }
        if (state() == GROUND) {
            setPos(getX(), getY() + v.y, getZ());
            return;
        }
        HitResult h = sweep(v, state() != TAKEOFF);
        if (h != null) {
            if (state() == TAKEOFF && h.getType() == HitResult.Type.BLOCK && h.getLocation().y <= getY() + 0.6) {
                // rolling on the runway: stay on top of the ground
                setPos(getX() + v.x, ground(sl, getX() + v.x, getZ() + v.z), getZ() + v.z);
                return;
            }
            detonate(sl, h instanceof EntityHitResult eh ? eh.getEntity() : null);
        }
    }

    public void detonate(ServerLevel sl, @Nullable net.minecraft.world.entity.Entity hit) {
        if (isRemoved()) return;
        Vec3 p = position();
        discard();
        Warheads.detonate(sl, p, kind(), this, ownerEntity(), hit);
    }

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (!level().isClientSide && p instanceof ServerPlayer sp && state() == GROUND) {
            if (s.getItem() instanceof TabletItem) {
                BlockPos t = TabletItem.target(s);
                if (t == null) {
                    sp.displayClientMessage(Component.translatable("msg.aiarsenal.a_no_target"), true);
                } else {
                    owner = sp.getUUID();
                    launch(t);
                    sp.displayClientMessage(Component.translatable("msg.aiarsenal.launched", Component.translatable(kind().translationKey()),
                            t.getX(), t.getZ()), true);
                }
            } else {
                sp.displayClientMessage(Component.translatable("msg.aiarsenal.use_tablet"), true);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    protected void onPickedUp(ServerPlayer p) {
        if (state() == GROUND) p.getInventory().add(new ItemStack(ArsenalRegistry.item(kind())));
        discard();
    }

    @Override
    protected void onDestroyed(DamageSource src) {
        if (!(level() instanceof ServerLevel sl)) return;
        if (state() == GROUND || sl.random.nextFloat() < 0.5f) detonate(sl, null);
        else setState(FALLING);
    }

    @Override
    protected void readMore(CompoundTag t) {
        phaseT = t.getInt("PhaseT");
        speed = t.getDouble("Speed");
        fuel = t.getInt("Fuel");
        seed = t.getFloat("Seed");
    }

    @Override
    protected void saveMore(CompoundTag t) {
        t.putInt("PhaseT", phaseT);
        t.putDouble("Speed", speed);
        t.putInt("Fuel", fuel);
        t.putFloat("Seed", seed);
    }

    /** Exhaust smoke / launch blast (Real War particle system), client only. */
    @Override
    protected void clientTick() {
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> ru.aiforge.arsenal.client.ArsTrails.shahed(this));
    }
}
