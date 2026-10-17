package ru.aiforge.arsenal.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.core.Tickets;
import ru.aiforge.arsenal.nuke.ChargeRegistry;
import ru.aiforge.arsenal.nuke.NuclearBlast;
import ru.aiforge.arsenal.registry.ArsenalRegistry;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * RS-24 Yars ICBM (game-scaled ballistic flight). Cold launch from the TEL canister (gas generator ejects the missile,
 * first stage ignites above the canister), three solid stages with separation, then the post-boost vehicle. Above
 * the atmosphere the flight continues "virtually" in the {@link ChargeRegistry} (no entity, no chunk loading across
 * thousands of blocks); near the target the re-entry vehicle is spawned again and falls with a plasma trail.
 * Fuze: AIR (optimum burst height), CONTACT or REMOTE; the detonator can fire it at any moment of the flight.
 */
public class IcbmEntity extends ArsEntity {
    public static final int EJECT = 0, BOOST = 1, BUS = 2, REENTRY = 3;
    /** aux bits: stages already separated */
    public static final int B_S1 = 1, B_S2 = 2, B_S3 = 4;
    private Vec3 launch = Vec3.ZERO, aim = Vec3.ZERO;
    private double apex, total, time;
    private boolean registered;

    public IcbmEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    /** Launches from {@code pos} (missile centre in the erected canister) toward {@code target}. */
    public static IcbmEntity launch(ServerLevel sl, Vec3 pos, Vec3 target, @Nullable UUID owner) {
        IcbmEntity e = new IcbmEntity(ArsenalRegistry.ICBM.get(), sl);
        e.setKind(Kind.YARS);
        e.setPos(pos);
        e.setPitch(90);
        e.owner = owner;
        e.aim = target;
        e.setState(EJECT);
        e.setAux(0);
        sl.addFreshEntity(e);
        ChargeRegistry.get(sl.getServer()).add(e.getUUID(), sl.dimension(), pos, Kind.YARS, ChargeRegistry.FUZE_AIR, owner, 2);
        e.registered = true;
        sl.playSound(null, pos.x, pos.y, pos.z, ArsenalRegistry.SND_LAUNCH_COLD.get(), SoundSource.NEUTRAL, 12f, 1f);
        return e;
    }

    public static Vec3 path(Vec3 l, Vec3 t, double apex, double s) {
        Vec3 c = l.add(t).scale(0.5).add(0, 2 * apex, 0);
        double a = (1 - s) * (1 - s), b = 2 * (1 - s) * s, d = s * s;
        return l.scale(a).add(c.scale(b)).add(t.scale(d));
    }

    public static double progress(double time, double total) {
        return Math.pow(Mth.clamp(time / total, 0, 1), 1.15);
    }

    /** Progress at which the re-entry vehicle re-appears as an entity (at most ~500 blocks before the target). */
    public static double resumeS(Vec3 l, Vec3 t) {
        double d = Math.hypot(t.x - l.x, t.z - l.z);
        return Math.max(0.8, 1 - 500 / Math.max(1, d));
    }

    public static double burstHeight() {
        return Math.min(90, 0.45 * NuclearBlast.radius(Kind.YARS.yieldKt()));
    }

    @Override
    protected boolean keepsChunks() {
        return true;
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        ChargeRegistry reg = ChargeRegistry.get(sl.getServer());
        ChargeRegistry.Charge c = reg.byEntity(getUUID());
        if (!registered) {
            if (c == null) c = reg.add(getUUID(), sl.dimension(), position(), Kind.YARS, ChargeRegistry.FUZE_AIR, owner, 2);
            registered = true;
        } else if (c == null) {
            discard();
            return;
        }
        int fuze = c == null ? ChargeRegistry.FUZE_AIR : c.fuze;
        Vec3 old = position();
        switch (state()) {
            case EJECT -> {
                // gas generator throws the missile ~25 blocks up out of the canister, then the first stage lights
                double t = age;
                double vy = Math.max(0.15, 1.1 - t * 0.035);
                setPos(getX(), getY() + vy, getZ());
                setDeltaMovement(0, vy, 0);
                if (age == 26) sl.playSound(null, getX(), getY(), getZ(), ArsenalRegistry.SND_BOOSTER.get(), SoundSource.NEUTRAL, 16f, 0.7f);
                if (age >= 26) flame(sl, 6);
                if (age >= 34) {
                    launch = position();
                    double dist = Math.hypot(aim.x - launch.x, aim.z - launch.z);
                    apex = Mth.clamp(dist * 0.22, 300, 4000);
                    total = Mth.clamp(dist / 25.0, 400, 2400);
                    time = 0;
                    aim = new Vec3(aim.x, ground(sl, aim.x, aim.z) + (fuze == ChargeRegistry.FUZE_AIR ? burstHeight() : 0) - 2, aim.z);
                    setState(BOOST);
                }
            }
            case BOOST, BUS, REENTRY -> {
                time += 1;
                double s = progress(time, total);
                Vec3 p = path(launch, aim, apex, s);
                // pitch program: the first part of the boost stays steep (gravity turn)
                if (state() == BOOST && s < 0.03) p = new Vec3(Mth.lerp(s / 0.03, launch.x, p.x), p.y, Mth.lerp(s / 0.03, launch.z, p.z));
                Vec3 v = p.subtract(old);
                setDeltaMovement(v);
                if (state() == BOOST) {
                    flame(sl, 4);
                    if (s > 0.05 && (aux() & B_S1) == 0) stage(sl, B_S1);
                    if (s > 0.11 && (aux() & B_S2) == 0) stage(sl, B_S2);
                    if (s > 0.18 && (aux() & B_S3) == 0) {
                        stage(sl, B_S3);
                        setState(BUS);
                    }
                }
                double rs = resumeS(launch, aim);
                if (state() == BUS && s > 0.25 && s < rs - 0.03 && getY() > launch.y + 300 && c != null) {
                    // leave the world: continue as a virtual flight
                    c.status = 3;
                    c.launch = launch;
                    c.aim = aim;
                    c.apex = apex;
                    c.total = total;
                    c.time = time;
                    c.pos = p;
                    c.entity = null;
                    reg.setDirty();
                    discard();
                    return;
                }
                if (state() == BUS && s >= rs) setState(REENTRY);
                if (state() == REENTRY) {
                    if (age % 10 == 0) Tickets.keep(sl, aim, 3);
                    sl.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 4, 0.3, 0.3, 0.3, 0.02);
                    sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), getX(), getY(), getZ(), 2, 0.4, 0.4, 0.4, 0.0);
                    if (fuze == ChargeRegistry.FUZE_AIR && getY() - ground(sl, getX(), getZ()) <= burstHeight()) {
                        detonate(sl);
                        return;
                    }
                }
                if (v.lengthSqr() > 1e-6) {
                    setYRot(yawOf(v));
                    setPitch(pitchOf(v));
                }
                setPos(old);
                HitResult h = sweep(v, false);
                if (h != null || s >= 1.0) {
                    if (fuze == ChargeRegistry.FUZE_REMOTE && state() == REENTRY) {
                        // REMOTE fuze: the RV buries itself, the charge stays armed
                        NukeEntity.create(sl, Kind.YARS, position(), Vec3.ZERO, getYRot(), ChargeRegistry.FUZE_REMOTE, owner, false);
                        reg.removeEntity(getUUID());
                        discard();
                    } else detonate(sl);
                    return;
                }
            }
            default -> {
            }
        }
        if (age % 20 == 0 && c != null) {
            c.pos = position();
            reg.setDirty();
        }
    }

    private void stage(ServerLevel sl, int bit) {
        setAux(aux() | bit);
        sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 20, 1, 1, 1, 0.05);
    }

    private void flame(ServerLevel sl, int n) {
        Vec3 back = position().subtract(dir(getYRot(), pitch()).scale(9));
        sl.sendParticles(ParticleTypes.FLAME, back.x, back.y, back.z, n, 0.3, 0.3, 0.3, 0.05);
        sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), back.x, back.y, back.z, n, 0.6, 0.6, 0.6, 0.02);
    }

    public void detonate(ServerLevel sl) {
        if (isRemoved()) return;
        ChargeRegistry.get(sl.getServer()).removeEntity(getUUID());
        Vec3 p = position();
        discard();
        NuclearBlast.detonate(sl, p, Kind.YARS, this, ownerEntity());
    }

    /** Re-creates the re-entry vehicle from a virtual flight. */
    public static IcbmEntity resume(ServerLevel sl, ChargeRegistry.Charge c) {
        IcbmEntity e = new IcbmEntity(ArsenalRegistry.ICBM.get(), sl);
        e.setKind(Kind.YARS);
        e.launch = c.launch;
        e.aim = c.aim;
        e.apex = c.apex;
        e.total = c.total;
        e.time = c.time;
        e.owner = c.owner;
        e.setAux(B_S1 | B_S2 | B_S3);
        e.setState(REENTRY);
        Vec3 p = path(c.launch, c.aim, c.apex, progress(c.time, c.total));
        e.setPos(p);
        e.registered = true;
        sl.addFreshEntity(e);
        c.entity = e.getUUID();
        c.status = 2;
        return e;
    }

    @Override
    protected void onDestroyed(DamageSource src) {
        if (level() instanceof ServerLevel sl) {
            ChargeRegistry.get(sl.getServer()).removeEntity(getUUID());
            ru.aiforge.tubombers.fx.ImpactHandler.crash(sl, position(), 5f, this);
        }
        discard();
    }

    @Override
    protected void readMore(CompoundTag t) {
        launch = new Vec3(t.getDouble("LX"), t.getDouble("LY"), t.getDouble("LZ"));
        aim = new Vec3(t.getDouble("AX"), t.getDouble("AY"), t.getDouble("AZ"));
        apex = t.getDouble("Apex");
        total = t.getDouble("Total");
        time = t.getDouble("Time");
        registered = t.getBoolean("Registered");
    }

    @Override
    protected void saveMore(CompoundTag t) {
        t.putDouble("LX", launch.x);
        t.putDouble("LY", launch.y);
        t.putDouble("LZ", launch.z);
        t.putDouble("AX", aim.x);
        t.putDouble("AY", aim.y);
        t.putDouble("AZ", aim.z);
        t.putDouble("Apex", apex);
        t.putDouble("Total", total);
        t.putDouble("Time", time);
        t.putBoolean("Registered", registered);
    }

    /** Exhaust smoke / launch blast (Real War particle system), client only. */
    @Override
    protected void clientTick() {
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> ru.aiforge.arsenal.client.ArsTrails.icbm(this));
    }
}
