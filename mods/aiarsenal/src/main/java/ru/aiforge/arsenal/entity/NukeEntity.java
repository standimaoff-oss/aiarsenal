package ru.aiforge.arsenal.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.nuke.ChargeRegistry;
import ru.aiforge.arsenal.nuke.NuclearBlast;

/**
 * Nuclear charge: either a free-falling bomb (dropped by a Tu-95 / Tu-160, AN602 under its 1600 m^2 parachute) or an
 * armed charge lying on the ground. Registered in {@link ChargeRegistry} so the remote detonator can fire it from any
 * distance; fuze modes REMOTE (only on command), CONTACT (on impact) and AIR (barometric, optimum burst height).
 */
public class NukeEntity extends ArsEntity {
    public static final int FALLING = 0, GROUND = 1;
    private boolean registered;
    /** Registry id (for messages). */
    public int charge;

    public NukeEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static NukeEntity create(ServerLevel sl, Kind k, Vec3 pos, Vec3 vel, float yaw, int fuze, java.util.UUID owner, boolean falling) {
        NukeEntity n = new NukeEntity(ru.aiforge.arsenal.registry.ArsenalRegistry.NUKE.get(), sl);
        n.setKind(k);
        n.setPos(pos);
        n.setDeltaMovement(vel);
        n.setYRot(yaw);
        n.setAux(fuze);
        n.owner = owner;
        n.setState(falling ? FALLING : GROUND);
        sl.addFreshEntity(n);
        n.charge = ChargeRegistry.get(sl.getServer()).add(n.getUUID(), sl.dimension(), pos, k, fuze, owner, falling ? 1 : 0).id;
        n.registered = true;
        return n;
    }

    public int fuze() {
        return aux();
    }

    /** Burst height above ground for the AIR fuze. */
    public float burstHeight() {
        return Math.min(90f, 0.45f * NuclearBlast.radius(kind().yieldKt()));
    }

    public boolean chuteOpen() {
        return kind() == Kind.AN602 && state() == FALLING && age > 30;
    }

    @Override
    protected boolean keepsChunks() {
        return state() == FALLING;
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        ChargeRegistry reg = ChargeRegistry.get(sl.getServer());
        ChargeRegistry.Charge c = reg.byEntity(getUUID());
        if (!registered) {
            if (c == null) c = reg.add(getUUID(), sl.dimension(), position(), kind(), fuze(), owner, state() == FALLING ? 1 : 0);
            registered = true;
        } else if (c == null) {
            discard();      // detonated remotely while unloaded / removed by command
            return;
        }
        if (c != null && c.fuze != fuze()) setAux(c.fuze);
        if (state() == FALLING) {
            Vec3 v = getDeltaMovement();
            double g = 9.81 * scale() / 400.0;
            double drag;
            if (chuteOpen()) drag = g / Math.pow(bpt(25.0), 2);          // terminal ~25 m/s under canopy
            else drag = g / Math.pow(bpt(kind() == Kind.AN602 ? 290 : 260), 2);
            double sp = v.length();
            v = v.add(0, -g, 0).subtract(v.scale(drag * sp));
            if (chuteOpen()) v = new Vec3(v.x * 0.97, v.y, v.z * 0.97);
            setDeltaMovement(v);
            if (sp > 1e-3) {
                setYRot(yawOf(v));
                setPitch(Math.max(-88f, pitchOf(v)));
            }
            if (fuze() == ChargeRegistry.FUZE_AIR) {
                int gnd = ground(sl, getX(), getZ());
                if (getY() - gnd <= burstHeight()) {
                    detonate(sl);
                    return;
                }
            }
            HitResult h = sweep(v, false);
            if (h != null) {
                if (fuze() == ChargeRegistry.FUZE_REMOTE) {
                    setState(GROUND);
                    setDeltaMovement(Vec3.ZERO);
                    setPitch(0);
                    sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), getX(), getY(), getZ(), 20, 1, 0.2, 1, 0.02);
                } else {
                    detonate(sl);
                    return;
                }
            }
        } else {
            setDeltaMovement(Vec3.ZERO);
            if (age % 20 == 0 && !level().getBlockState(blockPosition().below()).blocksMotion()
                    && level().getBlockState(blockPosition()).isAir()) {
                setPos(getX(), getY() - 0.5, getZ());
            }
        }
        if (age % 20 == 0) reg.update(getUUID(), position(), state() == FALLING ? 1 : 0);
    }

    public void detonate(ServerLevel sl) {
        if (isRemoved()) return;
        ChargeRegistry.get(sl.getServer()).removeEntity(getUUID());
        Vec3 p = position().add(0, 0.5, 0);
        discard();
        NuclearBlast.detonate(sl, p, kind(), this, ownerEntity());
    }

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (!level().isClientSide && p instanceof ServerPlayer sp) {
            ChargeRegistry.Charge c = ChargeRegistry.get(sp.server).byEntity(getUUID());
            if (sp.isShiftKeyDown() && sp.getAbilities().instabuild) {
                // creative: disarm and pick up
                ChargeRegistry.get(sp.server).removeEntity(getUUID());
                ru.aiforge.tubombers.aircraft.BombType bt = kind() == Kind.AN602 ? ru.aiforge.tubombers.aircraft.BombType.AN602
                        : ru.aiforge.tubombers.aircraft.BombType.RDS1;
                net.minecraft.world.item.Item it = ru.aiforge.tubombers.registry.ModRegistry.bombItem(bt);
                if (it != null) sp.getInventory().add(new net.minecraft.world.item.ItemStack(it));
                discard();
                sp.displayClientMessage(Component.translatable("msg.aiarsenal.charge_disarmed"), true);
            } else if (c != null) {
                sp.displayClientMessage(Component.translatable("msg.aiarsenal.charge_info", c.id,
                        Component.translatable(kind().translationKey()), Component.translatable("fuze.aiarsenal." + c.fuze)), true);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    protected void onDestroyed(DamageSource src) {
        // conventional damage cannot initiate a nuclear yield: the charge is wrecked (small HE detonation of the lenses)
        if (level() instanceof ServerLevel sl) {
            ChargeRegistry.get(sl.getServer()).removeEntity(getUUID());
            sl.explode(this, getX(), getY() + 0.5, getZ(), 4f, Level.ExplosionInteraction.NONE);
        }
        discard();
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        // a 4.7 t steel case shrugs off small arms
        return super.hurt(src, src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) ? amount : amount * 0.1f);
    }

    @Override
    protected void readMore(CompoundTag t) {
        registered = t.getBoolean("Registered");
    }

    @Override
    protected void saveMore(CompoundTag t) {
        t.putBoolean("Registered", registered);
    }
}
