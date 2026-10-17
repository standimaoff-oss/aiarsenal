package ru.aiforge.tubombers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.tubombers.aircraft.BombType;
import ru.aiforge.tubombers.entity.BombEntity;
import ru.aiforge.tubombers.entity.BomberEntity;
import ru.aiforge.tubombers.registry.ModRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * Engine, cabin, afterburner and wind loops for bombers plus falling-bomb whistles. All loops use no vanilla
 * attenuation: volume falls off with distance manually (bombers are audible far beyond 16 blocks) and pitch carries
 * a Doppler shift computed from the radial velocity (speed of sound scaled to world speed).
 */
public final class BomberSounds {
    private static final int EXT = 0, CABIN = 1, AB = 2, WIND = 3;
    private static final Map<Integer, Loop[]> LOOPS = new HashMap<>();

    private BomberSounds() {
    }

    public static void ensure(BomberEntity b) {
        Minecraft mc = Minecraft.getInstance();
        Loop[] l = LOOPS.computeIfAbsent(b.getId(), k -> new Loop[4]);
        for (int i = 0; i < 4; i++) {
            if (l[i] == null || l[i].isStopped() || !mc.getSoundManager().isActive(l[i])) {
                if (l[i] != null && !l[i].isStopped() && l[i].age < 40) continue; // just queued, not playing yet
                if (i == AB && !b.kind.swingWing) continue;
                if (b.isRemoved()) continue;
                l[i] = new Loop(b, i);
                mc.getSoundManager().play(l[i]);
            }
        }
        LOOPS.entrySet().removeIf(en -> {
            Entity e = mc.level == null ? null : mc.level.getEntity(en.getKey());
            return e == null || e.isRemoved();
        });
    }

    private static boolean insideOf(BomberEntity b) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && b.crewOf(mc.player) != null;
    }

    /** Doppler factor for a source moving with world-velocity v (blocks/tick) relative to the listener. */
    private static float doppler(BomberEntity b, Vec3 src, Vec3 vel) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 ears = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 d = ears.subtract(src);
        double dist = d.length();
        if (dist < 1) return 1f;
        double vr = vel.dot(d) / dist * 20.0;                    // blocks/s toward listener
        double c = 343.0 * b.speedScale();
        return (float) Mth.clamp(c / Math.max(c * 0.3, c - vr), 0.6, 1.6);
    }

    static final class Loop extends AbstractTickableSoundInstance {
        final BomberEntity b;
        final int kind;
        int age;
        float vol, pit = 1f;

        Loop(BomberEntity b, int kind) {
            super(pick(b, kind), SoundSource.NEUTRAL, RandomSource.create());
            this.b = b;
            this.kind = kind;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.001f;
            this.x = b.getX();
            this.y = b.getY();
            this.z = b.getZ();
        }

        private static SoundEvent pick(BomberEntity b, int kind) {
            boolean t95 = !b.kind.swingWing;
            return switch (kind) {
                case CABIN -> (t95 ? ModRegistry.SND_TU95_CABIN : ModRegistry.SND_TU160_CABIN).get();
                case AB -> ModRegistry.SND_AFTERBURNER.get();
                case WIND -> ModRegistry.SND_WIND.get();
                default -> (t95 ? ModRegistry.SND_TU95_ENGINE : ModRegistry.SND_TU160_ENGINE).get();
            };
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            age++;
            if (b.isRemoved()) {
                stop();
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            Vec3 ears = mc.gameRenderer.getMainCamera().getPosition();
            boolean inside = insideOf(b);
            boolean engines = b.hasFlag(BomberEntity.F_ENGINES) && !b.isDestroyed();
            float thr = engines ? b.thrust() : 0f;
            float spool = engines ? Math.max(0.25f, Math.min(1f, thr)) : 0f;
            double ias = b.st.vel.length();
            Vec3 src = b.position().add(0, 3, 0);
            double dist = ears.distanceTo(src);
            float range = kind == WIND ? 120f : kind == AB ? 900f : 700f;
            float fall = (float) Math.max(0, 1 - dist / range);
            fall = fall * fall;
            float target;
            switch (kind) {
                case CABIN -> {
                    target = inside ? 0.25f + 0.55f * spool : 0f;
                    pit = 0.8f + 0.25f * spool;
                }
                case AB -> {
                    float ab = Math.max(0f, (thr - 1.0f) / Math.max(0.01f, b.kind.maxThrottle() - 1f));
                    target = ab * fall * (inside ? 0.18f : 1.6f);
                    pit = (0.9f + 0.15f * ab) * (inside ? 1f : doppler(b, src, b.getDeltaMovement()));
                }
                case WIND -> {
                    float w = (float) Mth.clamp((ias - 25) / 180.0, 0, 1);
                    target = w * (inside ? 0.45f : 0.9f * fall);
                    pit = 0.7f + 0.6f * w;
                }
                default -> {
                    target = spool * fall * (inside ? 0.2f : 2.0f);
                    float base = b.kind.swingWing ? 0.75f + 0.45f * Math.min(1f, thr) : 0.85f + 0.2f * Math.min(1f, thr);
                    pit = base * (inside ? 1f : doppler(b, src, b.getDeltaMovement()));
                }
            }
            vol += (target - vol) * 0.15f;
            this.volume = Math.max(0.0001f, vol);
            this.pitch = Mth.clamp(pit, 0.5f, 2f);
            // put the source close to the listener in the right direction so the engine keeps spatial panning
            Vec3 dir = src.subtract(ears);
            double len = dir.length();
            Vec3 p = inside || kind == CABIN ? ears : (len > 8 ? ears.add(dir.scale(8 / len)) : src);
            this.x = p.x;
            this.y = p.y;
            this.z = p.z;
        }
    }

    /** Whistle of a falling bomb; louder and higher as it nears the ground and the listener. */
    public static void whistle(BombEntity bomb) {
        Minecraft.getInstance().getSoundManager().play(new Whistle(bomb));
    }

    static final class Whistle extends AbstractTickableSoundInstance {
        final BombEntity bomb;
        int age;

        Whistle(BombEntity bomb) {
            super(ModRegistry.SND_WHISTLE.get(), SoundSource.NEUTRAL, RandomSource.create());
            this.bomb = bomb;
            this.looping = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.001f;
            this.x = bomb.getX();
            this.y = bomb.getY();
            this.z = bomb.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            age++;
            if (bomb.isRemoved() || age > 2400) {
                stop();
                return;
            }
            Vec3 ears = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 d = bomb.position().subtract(ears);
            double dist = d.length();
            double v = bomb.position().subtract(bomb.xo, bomb.yo, bomb.zo).length() * 20; // blocks/s
            float speedK = (float) Mth.clamp(v / 60.0, 0, 1);
            float near = (float) Math.max(0, 1 - dist / 260.0);
            float heavy = bomb.type() == BombType.FAB1500 ? 0.8f : bomb.type() == BombType.FAB500 ? 0.92f : 1.05f;
            this.volume = Math.max(0.0001f, speedK * near * near * 1.6f);
            this.pitch = Mth.clamp(heavy * (1.25f - 0.45f * (float) Math.min(1, dist / 200.0)), 0.5f, 2f);
            Vec3 p = dist > 8 ? ears.add(d.scale(8 / dist)) : bomb.position();
            this.x = p.x;
            this.y = p.y;
            this.z = p.z;
        }
    }
}
