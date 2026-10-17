package ru.aiforge.arsenal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.entity.ArsEntity;
import ru.aiforge.arsenal.entity.FpvDrone;
import ru.aiforge.arsenal.entity.IcbmEntity;
import ru.aiforge.arsenal.entity.MissileEntity;
import ru.aiforge.arsenal.entity.ReconDrone;
import ru.aiforge.arsenal.entity.ShahedDrone;
import ru.aiforge.arsenal.registry.ArsenalRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * Engine loops for drones and missiles: manual distance fall-off (audible far beyond 16 blocks, the Shahed "moped"
 * is heard long before it is seen), Doppler shift from the radial velocity; FPV analog video static in the session.
 */
public final class ArsSounds {
    private static final Map<Integer, Loop> LOOPS = new HashMap<>();
    private static Loop staticLoop;

    private ArsSounds() {
    }

    static void tick(Minecraft mc) {
        if (mc.level == null) return;
        if (mc.level.getGameTime() % 10 == 0) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof ArsEntity a)) continue;
                Loop l = LOOPS.get(e.getId());
                SoundEvent want = want(a);
                if (want == null) continue;
                if (l == null || l.isStopped() || l.ev != want) {
                    if (l != null && !l.isStopped() && l.ev == want) continue;
                    if (l != null) l.kill();
                    Loop n = new Loop(a, want);
                    LOOPS.put(e.getId(), n);
                    mc.getSoundManager().play(n);
                }
            }
            LOOPS.entrySet().removeIf(en -> en.getValue().isStopped());
        }
        Entity s = ArsClient.sessionEntity();
        if (s instanceof FpvDrone f && f.link() < 0.7f) {
            if (staticLoop == null || staticLoop.isStopped()) {
                staticLoop = new Loop(f, ArsenalRegistry.SND_STATIC.get());
                staticLoop.isStatic = true;
                mc.getSoundManager().play(staticLoop);
            }
        } else if (staticLoop != null) {
            staticLoop.kill();
            staticLoop = null;
        }
    }

    private static SoundEvent want(ArsEntity a) {
        Kind k = a.kind();
        if (a instanceof ShahedDrone s) {
            if (s.boosterAttached()) return ArsenalRegistry.SND_BOOSTER.get();
            if (!s.engineRunning()) return null;
            return k == Kind.S238 ? ArsenalRegistry.SND_JET.get() : ArsenalRegistry.SND_SHAHED.get();
        }
        if (a instanceof ReconDrone r) return r.state() == ReconDrone.PARKED ? null : ArsenalRegistry.SND_TURBOPROP.get();
        if (a instanceof FpvDrone f) return f.state() == FpvDrone.IDLE ? null : ArsenalRegistry.SND_FPV.get();
        if (a instanceof MissileEntity m) {
            if (m.booster() || k == Kind.HELLFIRE) return ArsenalRegistry.SND_BOOSTER.get();
            return m.engine() ? ArsenalRegistry.SND_JET.get() : null;
        }
        if (a instanceof IcbmEntity i) return i.state() <= IcbmEntity.BOOST && i.tickCount > 25 ? ArsenalRegistry.SND_BOOSTER.get() : null;
        return null;
    }

    private static float range(ArsEntity a, SoundEvent ev) {
        if (ev == ArsenalRegistry.SND_BOOSTER.get()) return a instanceof IcbmEntity ? 900f : 320f;
        if (ev == ArsenalRegistry.SND_SHAHED.get()) return 260f;
        if (ev == ArsenalRegistry.SND_JET.get()) return 300f;
        if (ev == ArsenalRegistry.SND_TURBOPROP.get()) return 200f;
        if (ev == ArsenalRegistry.SND_FPV.get()) return 70f;
        return 100f;
    }

    static final class Loop extends AbstractTickableSoundInstance {
        final ArsEntity e;
        final SoundEvent ev;
        boolean isStatic;

        Loop(ArsEntity e, SoundEvent ev) {
            super(ev, SoundSource.NEUTRAL, RandomSource.create());
            this.e = e;
            this.ev = ev;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.001f;
            this.x = e.getX();
            this.y = e.getY();
            this.z = e.getZ();
        }

        void kill() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (e.isRemoved()) {
                stop();
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
            if (isStatic) {
                x = ear.x;
                y = ear.y;
                z = ear.z;
                volume = e instanceof FpvDrone f ? Mth.clamp((0.7f - f.link()) * 1.4f, 0f, 0.9f) : 0f;
                return;
            }
            x = e.getX();
            y = e.getY();
            z = e.getZ();
            Vec3 d = ear.subtract(x, y, z);
            double dist = d.length();
            float r = range(e, ev);
            boolean inside = ArsClient.sessionEntity() == e;
            float v = inside ? 0.5f : (float) Mth.clamp(1 - dist / r, 0, 1);
            v = v * v;
            if (e instanceof FpvDrone f) v *= 0.4f + f.throttleSynced();
            volume = Mth.clamp(v * 1.4f, 0.0001f, 1.5f);
            double vr = dist < 1 ? 0 : e.getDeltaMovement().dot(d) / dist * 20.0;
            double c = 343.0 * ArsEntity.scale();
            float dop = (float) Mth.clamp(c / Math.max(c * 0.4, c - vr), 0.7, 1.5);
            float base = e instanceof FpvDrone f ? 0.8f + 0.5f * f.throttleSynced() : 1f;
            pitch = inside ? base : base * dop;
        }
    }
}
