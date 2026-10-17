package ru.aiforge.arsenal.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.joml.Vector3f;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.core.Nodes;
import ru.aiforge.arsenal.core.Warheads;
import ru.aiforge.arsenal.item.AmmoItem;
import ru.aiforge.arsenal.item.TabletItem;
import ru.aiforge.arsenal.registry.ArsenalRegistry;

import javax.annotation.Nullable;

/**
 * Ground launchers: Shahed launch rail (one drone, RATO launch at 15 degrees), 4-tube cruise missile launcher
 * (Kalibr / Tomahawk; elevates to 45 degrees, opens the tube covers) and the Yars TEL (erects the canister to vertical,
 * blows the cap and cold-launches the ICBM). Target X/Z comes from the GUI or the tablet.
 */
public class LauncherEntity extends ArsEntity {
    public static final int A_TARGET = 0, A_LAUNCH = 1, A_SALVO = 2, A_ERECT = 3, A_UNLOAD = 4;
    private int queue, queueTimer;
    private boolean wantErect;

    public LauncherEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static LauncherEntity create(ServerLevel sl, Kind k, Vec3 pos, float yaw) {
        LauncherEntity e = new LauncherEntity(ArsenalRegistry.LAUNCHER.get(), sl);
        e.setKind(k);
        e.setPos(pos);
        e.setYRot(yaw);
        sl.addFreshEntity(e);
        return e;
    }

    // ------------------------------------------------------------------ loaded ammunition (synced in aux)
    public int loadedMask() {
        return aux() & 0xFF;
    }

    public int loadedCount() {
        return Integer.bitCount(loadedMask());
    }

    @Nullable
    public Kind ammoKind() {
        return loadedMask() == 0 ? null : Kind.byIndex(aux() >> 8);
    }

    private void setLoaded(int mask, @Nullable Kind k) {
        setAux((mask & 0xFF) | ((k == null ? 0 : k.ordinal()) << 8));
    }

    public boolean accepts(Kind ammo) {
        return switch (kind()) {
            case RAIL -> ammo == Kind.S136 || ammo == Kind.S131 || ammo == Kind.S238;
            case CRUISE_LAUNCHER -> ammo == Kind.KALIBR || ammo == Kind.TOMAHAWK;
            case YARS_TEL -> ammo == Kind.YARS;
            default -> false;
        };
    }

    /** Erection progress 0..1 (synced). */
    public float erect() {
        return f1();
    }

    /** Cruise launcher: tube covers open 0..1; TEL: 1 after the canister cap was blown. */
    public float lids() {
        return f2();
    }

    public boolean needsErect() {
        return kind() != Kind.RAIL;
    }

    public boolean ready() {
        return !needsErect() || (erect() >= 1f && (kind() != Kind.CRUISE_LAUNCHER || lids() >= 1f));
    }

    public int queued() {
        return queue;
    }

    // ------------------------------------------------------------------ actions
    public void action(ServerPlayer p, int action, int x, int z) {
        switch (action) {
            case A_TARGET -> {
                setTarget(new BlockPos(x, ground(level(), x, z), z));
                p.displayClientMessage(Component.translatable("msg.aiarsenal.a_target_set", x, z), true);
            }
            case A_LAUNCH, A_SALVO -> {
                if (target() == null) {
                    p.displayClientMessage(Component.translatable("msg.aiarsenal.a_no_target"), true);
                    return;
                }
                if (loadedCount() == 0) {
                    p.displayClientMessage(Component.translatable("msg.aiarsenal.a_empty"), true);
                    return;
                }
                owner = p.getUUID();
                queue = action == A_SALVO ? loadedCount() : 1;
                wantErect = true;
                p.displayClientMessage(Component.translatable(ready() ? "msg.aiarsenal.launching" : "msg.aiarsenal.preparing"), true);
            }
            case A_ERECT -> {
                if (needsErect()) wantErect = !wantErect;
            }
            case A_UNLOAD -> {
                Kind k = ammoKind();
                if (k != null && queue == 0) {
                    for (int i = 0; i < loadedCount(); i++) p.getInventory().add(new ItemStack(ArsenalRegistry.item(k)));
                    setLoaded(0, null);
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected boolean keepsChunks() {
        return queue > 0;
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        setDeltaMovement(Vec3.ZERO);
        if (age % 10 == 0 && !level().getBlockState(BlockPos.containing(getX(), getY() - 0.1, getZ())).blocksMotion())
            setPos(getX(), Math.max(ground(sl, getX(), getZ()), getY() - 1), getZ());
        if (needsErect()) {
            float rate = kind() == Kind.YARS_TEL ? 1f / 100f : 1f / 50f;
            boolean up = wantErect;
            float e = erect();
            if (up && e < 1f) {
                if (e == 0f) sl.playSound(null, getX(), getY(), getZ(), ru.aiforge.bm21grad.registry.ModRegistry.SND_HYDRAULICS.get(), SoundSource.NEUTRAL, 3f, 0.8f);
                setF1(Math.min(1f, e + rate));
            } else if (!up && e > 0f && (kind() != Kind.CRUISE_LAUNCHER || lids() <= 0f)) setF1(Math.max(0f, e - rate));
            if (kind() == Kind.CRUISE_LAUNCHER) {
                if (up && erect() >= 1f) setF2(Math.min(1f, lids() + 0.04f));
                else if (!up) setF2(Math.max(0f, lids() - 0.04f));
            }
            if (kind() == Kind.YARS_TEL && !up && erect() <= 0f && loadedCount() > 0) setF2(0f);
        }
        if (queueTimer > 0) queueTimer--;
        if (queue > 0 && ready() && queueTimer == 0) {
            if (loadedCount() == 0 || target() == null) queue = 0;
            else {
                fireOne(sl);
                queue--;
                queueTimer = kind() == Kind.CRUISE_LAUNCHER ? 40 : 20;
            }
        }
    }

    private void fireOne(ServerLevel sl) {
        Kind ammo = ammoKind();
        int mask = loadedMask();
        BlockPos t = target();
        if (ammo == null || t == null) return;
        switch (kind()) {
            case RAIL -> {
                Vec3 seat = position().add(Nodes.toWorld(Nodes.node(Kind.RAIL, "RAIL_Seat", null, 0, null), getYRot(), 0, 0));
                ShahedDrone.create(sl, ammo, seat.add(0, 0.35, 0), getYRot(), 15f, t, owner, ShahedDrone.BOOST)
                        .setDeltaMovement(dir(getYRot(), 15f).scale(0.2));
                setLoaded(0, null);
                sl.playSound(null, seat.x, seat.y, seat.z, ArsenalRegistry.SND_LAUNCH_RAIL.get(), SoundSource.NEUTRAL, 6f, 1f);
            }
            case CRUISE_LAUNCHER -> {
                int i = 31 - Integer.numberOfLeadingZeros(mask);
                Vector3f ax = new Vector3f();
                Vector3f tube = Nodes.node(Kind.CRUISE_LAUNCHER, "LAUNCHER_Tube" + i, "launcher_erect", 2f, ax);
                Vec3 axis = Nodes.toWorld(ax, getYRot(), 0, 0).normalize();
                Vec3 mouth = position().add(Nodes.toWorld(tube, getYRot(), 0, 0)).add(axis.scale(4.5));
                MissileEntity.create(sl, ammo, mouth, axis.scale(0.6), t, null, owner, MissileEntity.BOOST);
                setLoaded(mask & ~(1 << i), ammo);
                sl.playSound(null, mouth.x, mouth.y, mouth.z, ArsenalRegistry.SND_BOOSTER.get(), SoundSource.NEUTRAL, 10f, 1f);
                sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), mouth.x, mouth.y, mouth.z, 30, 1.5, 1.0, 1.5, 0.05);
            }
            case YARS_TEL -> {
                Vector3f ax = new Vector3f();
                Vector3f m = Nodes.node(Kind.YARS_TEL, "YARSTEL_Missile", "yarstel_erect", 4f, ax);
                Vec3 pos = position().add(Nodes.toWorld(m, getYRot(), 0, 0));
                setF2(1f);     // cap blown off
                Vec3 aim = Vec3.atCenterOf(t);
                IcbmEntity.launch(sl, pos.subtract(0, 1.0, 0), aim, owner);
                setLoaded(0, null);
                Vec3 top = pos.add(0, 11.5, 0);
                sl.sendParticles(ParticleTypes.EXPLOSION, top.x, top.y, top.z, 2, 0.5, 0.5, 0.5, 0);
                sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), pos.x, getY() + 1, pos.z, 60, 4, 1, 4, 0.03);
            }
            default -> {
            }
        }
    }

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (level().isClientSide) {
            if (!(s.getItem() instanceof AmmoItem) && !(s.getItem() instanceof TabletItem) && !p.isShiftKeyDown())
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.aiforge.arsenal.client.ArsClient.openLauncher(this));
            return InteractionResult.SUCCESS;
        }
        ServerPlayer sp = (ServerPlayer) p;
        if (s.getItem() instanceof AmmoItem ai) {
            Kind k = ai.kind;
            if (!accepts(k)) {
                sp.displayClientMessage(Component.translatable("msg.aiarsenal.wrong_ammo"), true);
                return InteractionResult.FAIL;
            }
            Kind cur = ammoKind();
            if (cur != null && cur != k) {
                sp.displayClientMessage(Component.translatable("msg.aiarsenal.mixed_ammo"), true);
                return InteractionResult.FAIL;
            }
            int mask = loadedMask();
            for (int i = 0; i < kind().slots; i++)
                if ((mask & (1 << i)) == 0) {
                    setLoaded(mask | (1 << i), k);
                    if (!sp.getAbilities().instabuild) s.shrink(1);
                    sl().playSound(null, getX(), getY(), getZ(), ru.aiforge.bm21grad.registry.ModRegistry.SND_RELOAD.get(), SoundSource.NEUTRAL, 1.5f, 0.8f);
                    sp.displayClientMessage(Component.translatable("msg.aiarsenal.a_loaded", Component.translatable(k.translationKey()),
                            loadedCount(), kind().slots), true);
                    return InteractionResult.CONSUME;
                }
            sp.displayClientMessage(Component.translatable("msg.aiarsenal.a_full"), true);
            return InteractionResult.FAIL;
        }
        if (s.getItem() instanceof TabletItem) {
            BlockPos t = TabletItem.target(s);
            if (t == null) sp.displayClientMessage(Component.translatable("msg.aiarsenal.a_no_target"), true);
            else action(sp, A_TARGET, t.getX(), t.getZ());
            return InteractionResult.CONSUME;
        }
        if (sp.isShiftKeyDown() && queue == 0) {
            onPickedUp(sp);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.CONSUME;
    }

    private ServerLevel sl() {
        return (ServerLevel) level();
    }

    @Override
    protected void onPickedUp(ServerPlayer p) {
        Kind k = ammoKind();
        if (k != null) for (int i = 0; i < loadedCount(); i++) p.getInventory().add(new ItemStack(ArsenalRegistry.item(k)));
        p.getInventory().add(new ItemStack(ArsenalRegistry.item(kind())));
        discard();
    }

    @Override
    protected void onDestroyed(DamageSource src) {
        if (level() instanceof ServerLevel sl) {
            Kind k = ammoKind();
            Vec3 p = position().add(0, 1, 0);
            discard();
            if (k != null && k != Kind.YARS) Warheads.detonate(sl, p, k, this, null, null);
            else ru.aiforge.tubombers.fx.ImpactHandler.crash(sl, p, 4f, this);
            if (k == Kind.YARS) ru.aiforge.tubombers.fx.ImpactHandler.fire(sl, p, 20, 8);
        }
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        return super.hurt(src, src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) ? amount : amount * 0.3f);
    }

    @Override
    protected void readMore(CompoundTag t) {
        setF1(t.getFloat("Erect"));
        setF2(t.getFloat("Lids"));
        wantErect = t.getBoolean("WantErect");
        queue = t.getInt("Queue");
    }

    @Override
    protected void saveMore(CompoundTag t) {
        t.putFloat("Erect", f1());
        t.putFloat("Lids", f2());
        t.putBoolean("WantErect", wantErect);
        t.putInt("Queue", queue);
    }
}
