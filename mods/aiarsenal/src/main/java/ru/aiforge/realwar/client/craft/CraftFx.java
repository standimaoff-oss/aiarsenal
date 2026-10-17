package ru.aiforge.realwar.client.craft;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.client.fx.TrailFx;
import ru.aiforge.realwar.client.particle.FxParticle;
import ru.aiforge.realwar.client.particle.FxSystem;
import ru.aiforge.realwar.client.particle.Sprites;
import ru.aiforge.realwar.craft.BoatEntity;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.craft.CraftKind;
import ru.aiforge.realwar.craft.CraftOrdnance;
import ru.aiforge.realwar.craft.CraftWeapon;
import ru.aiforge.realwar.craft.HeliEntity;
import ru.aiforge.realwar.registry.ModSounds;

/** Rotor downwash, bow spray / wake, bubbles, flares, missile trails, muzzle flashes and engine loops. */
public final class CraftFx {
    private static final Map<Integer, Loop[]> LOOPS = new HashMap<>();
    private static final Random R = new Random();

    private CraftFx() {}

    public static void tick(CraftEntity c) {
        ClientLevel level = (ClientLevel) c.level();
        sounds(c);
        if (c instanceof HeliEntity h) heli(level, h);
        else if (c instanceof ru.aiforge.realwar.craft.PlaneEntity p) plane(level, p);
        else if (c instanceof BoatEntity b) boat(level, b);
    }

    // ------------------------------------------------------------------------------------- helicopter

    private static void heli(ClientLevel level, HeliEntity h) {
        float om = h.aux;
        CraftKind.Heli k = h.kind.heli;
        if (om > 0.45f) {
            Vec3 hub = h.localToWorld(0, k.hubY(), 0);
            BlockHitResult hit = level.clip(new ClipContext(hub, hub.add(0, -22, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, h));
            if (hit.getType() != HitResult.Type.MISS) {
                double d = hub.y - hit.getLocation().y;
                double inten = om * om * h.aux2 * Math.max(0, 1 - d / 22.0);
                int n = (int) (inten * 9 * Math.min(1.5, TrailFx.q()));
                boolean water = level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER) || level.getFluidState(hit.getBlockPos().above()).is(FluidTags.WATER);
                BlockState ground = level.getBlockState(hit.getBlockPos());
                for (int i = 0; i < n; i++) {
                    double a = R.nextDouble() * Math.PI * 2, rr = k.rotorR() * (0.35 + 0.75 * R.nextDouble());
                    double x = hit.getLocation().x + Math.cos(a) * rr, z = hit.getLocation().z + Math.sin(a) * rr, y = hit.getLocation().y + 0.15;
                    double vx = Math.cos(a) * (0.35 + R.nextDouble() * 0.4), vz = Math.sin(a) * (0.35 + R.nextDouble() * 0.4);
                    if (water) {
                        level.addParticle(ParticleTypes.CLOUD, x, y + 0.2, z, vx * 0.6, 0.05 + R.nextDouble() * 0.08, vz * 0.6);
                        level.addParticle(ParticleTypes.SPLASH, x, y + 0.1, z, vx, 0.2, vz);
                    } else if (!ground.isAir()) {
                        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, y, z, vx * 2, 0.1, vz * 2);
                        if (R.nextInt(3) == 0) level.addParticle(ParticleTypes.POOF, x, y + 0.2, z, vx * 0.5, 0.03, vz * 0.5);
                    }
                }
            }
        }
        if (h.engineOn() && R.nextInt(3) == 0) {
            for (int s = -1; s <= 1; s += 2) {
                Vec3 ex = h.localToWorld(s * 0.95, 2.55, h.kind == CraftKind.AH64 ? -0.4 : -0.9);
                Vec3 back = h.localToWorld(0,0,-1).subtract(h.localToWorld(0,0,0)).scale(.1);
                level.addParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.EXHAUST.get(), ex.x, ex.y, ex.z, back.x, back.y, back.z);
            }
        }
        if (h.hp() < h.kind.hp * 0.3f) {
            Vec3 ex = h.localToWorld(0, 2.5, -0.8);
            level.addParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), ex.x, ex.y, ex.z, 0, 0.12, 0);
            if (R.nextInt(3) == 0) level.addParticle(ParticleTypes.FLAME, ex.x, ex.y, ex.z, 0, 0.05, 0);
        }
    }

    // ------------------------------------------------------------------------------------- aeroplanes

    private static void plane(ClientLevel level, ru.aiforge.realwar.craft.PlaneEntity p) {
        CraftKind.Plane k = p.spec();
        int n = k.engines();
        double sp = p.vel.length();
        Vec3[] bas = p.basis();
        for (int i = 0; i < n; i++) {
            float[] e = engineAt(p, i);
            if (k.jet()) {
                // r12 jet: afterburner sparks + heat haze from the nozzles, jet blast lifts dust on the ground
                Vec3 nz = p.localToWorld(e[0], e[1], e[2]);
                Vec3 back = bas[0].scale(-0.6);
                if (p.abVis > 0.5f && R.nextInt(2) == 0)
                    level.addParticle(ParticleTypes.FLAME, nz.x, nz.y, nz.z, back.x * 0.5, back.y * 0.5, back.z * 0.5);
                if (p.engineOn() && p.aux > 0.3f && R.nextInt(3) == 0)
                    level.addParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.EXHAUST.get(), nz.x, nz.y, nz.z, back.x, back.y, back.z);
                if (p.aux > 0.5f && p.agl < 6 && R.nextInt(2) == 0) {
                    Vec3 g = p.localToWorld(e[0], 0.1, e[2] - 4.0);
                    BlockState st = level.getBlockState(below(g));
                    if (!st.isAir()) level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st), g.x + R.nextGaussian(), g.y, g.z + R.nextGaussian(), back.x, 0.15, back.z);
                }
                continue;
            }
            // exhaust stubs: dark puffs on start-up / at low rpm, thin haze at power
            if (p.engineOn() && R.nextInt(p.aux < 0.35f ? 1 : 4) == 0) {
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 ex = p.localToWorld(e[0] + s * (n == 1 ? 0.42 : 0.55), e[1] + 0.05, e[2] - (n == 1 ? 1.0 : 1.3));
                    level.addParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.EXHAUST.get(), ex.x, ex.y, ex.z,
                            -bas[0].x * 0.1 + bas[2].x * s * 0.05, 0.02, -bas[0].z * 0.1 + bas[2].z * s * 0.05);
                }
            }
            // prop wash over the ground / water while taxiing and on the take-off run
            if (p.aux > 0.5f && p.agl < 4 && R.nextInt(2) == 0) {
                Vec3 g = p.localToWorld(e[0], 0.1, e[2] - 4.0);
                BlockPos bp = below(g);
                BlockState st = level.getBlockState(bp);
                boolean water = level.getFluidState(bp).is(FluidTags.WATER) || level.getFluidState(bp.above()).is(FluidTags.WATER);
                Vec3 v = bas[0].scale(-0.5 - R.nextDouble() * 0.4);
                if (water) level.addParticle(ParticleTypes.SPLASH, g.x, g.y, g.z, v.x, 0.2, v.z);
                else if (!st.isAir()) level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st), g.x + R.nextGaussian(), bp.getY() + 1.1, g.z + R.nextGaussian(), v.x, 0.12, v.z);
            }
            if (p.hp() < p.kind.hp * 0.3f && i == p.getId() % n) {
                Vec3 ex = p.localToWorld(e[0], e[1] + 0.3, e[2] - 1.5);
                level.addParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), ex.x, ex.y, ex.z, 0, 0.08, 0);
                if (R.nextInt(2) == 0) level.addParticle(ParticleTypes.FLAME, ex.x, ex.y, ex.z, 0, 0.03, 0);
            }
        }
        // wingtip vortices at high load
        if (p.isLocalPilot() && p.gForce > 4.0f && sp > 70 && R.nextInt(2) == 0) {
            for (int s = -1; s <= 1; s += 2) {
                Vec3 tip = p.localToWorld(s * k.span() * 0.48, p.pivotY(), -0.6);
                level.addParticle(ParticleTypes.CLOUD, tip.x, tip.y, tip.z, 0, 0, 0);
            }
        }
    }

    /** Engine (propeller hub) positions in the model frame: x, y, z. */
    static float[] engineAt(ru.aiforge.realwar.craft.PlaneEntity p, int i) {
        return switch (p.kind) {
            case HE111 -> new float[]{i == 0 ? 3.15f : -3.15f, 2.30f, 3.80f};
            case PE8 -> new float[]{(i < 2 ? 1 : -1) * (i % 2 == 0 ? 4.75f : 10.1f), i % 2 == 0 ? 2.62f : 2.92f, i % 2 == 0 ? 6.0f : 5.4f};
            case YAK3 -> new float[]{0f, 1.62f, 2.05f};
            case SU57 -> new float[]{i == 0 ? 1.22f : -1.22f, 1.55f, -9.9f};
            default -> new float[]{0f, 1.70f, 2.25f};
        };
    }

    // ------------------------------------------------------------------------------------- boats & sub

    private static void boat(ClientLevel level, BoatEntity b) {
        if (!b.onWater) return;
        CraftKind.Water w = b.kind.water;
        double sp = b.vel.length();
        Vec3[] bas = b.basis();
        Vec3 f = bas[0], r = bas[2];
        boolean sub = b.kind.isSub();
        boolean under = sub && b.subFrac > 0.97f;
        double q = Math.min(1.5, TrailFx.q());
        if (under) {
            if (Math.abs(b.output()) > 0.05 && R.nextInt(2) == 0) {
                Vec3 p = b.localToWorld(0, w.axisY(), -w.length() / 2 - 0.5);
                for (int i = 0; i < 3; i++) level.addParticle(ParticleTypes.BUBBLE, p.x + R.nextGaussian(), p.y + R.nextGaussian(), p.z + R.nextGaussian(), 0, 0.05, 0);
            }
            return;
        }
        double wl = sub ? w.draft() : w.draft() * 0.9;
        if (sp > 2.5) {
            int n = (int) (Math.min(sp, 25) / 4 * q);
            for (int i = 0; i < n; i++) {
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 p = b.localToWorld(s * w.beam() * 0.42, wl, w.length() * (sub ? 0.40 : 0.22));
                    Vec3 v = r.scale(-s * (0.15 + R.nextDouble() * 0.2)).add(0, 0.12 + R.nextDouble() * 0.15, 0).add(f.scale(sp * 0.01));
                    level.addParticle(ParticleTypes.SPLASH, p.x, p.y, p.z, v.x, v.y, v.z);
                    if (R.nextInt(3) == 0) level.addParticle(ParticleTypes.CLOUD, p.x, p.y + 0.2, p.z, v.x * 0.4, 0.02, v.z * 0.4);
                }
            }
            // wake / rooster tail
            Vec3 st = b.localToWorld(0, wl, -w.length() / 2);
            int m = (int) (Math.min(sp, 25) / 3 * q);
            for (int i = 0; i < m; i++) {
                double spread = w.beam() * 0.4;
                Vec3 v = f.scale(-0.15 - R.nextDouble() * 0.1).add(0, w.jets() ? 0.2 + R.nextDouble() * 0.25 : 0.03, 0);
                level.addParticle(ParticleTypes.CLOUD, st.x + r.x * R.nextGaussian() * spread, st.y + 0.1, st.z + r.z * R.nextGaussian() * spread, v.x, v.y, v.z);
            }
        }
        if (b.engineOn() && !sub && R.nextInt(4) == 0) {
            Vec3 ex = b.localToWorld(0.6, w.hullH() + 0.4, -w.length() * 0.35);
            level.addParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), ex.x, ex.y, ex.z, 0, 0.03, 0);
        }
        if (sub && b.engineOn() && R.nextInt(3) == 0) {
            Vec3 ex = b.localToWorld(0, 13.2, 7.0);
            level.addParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), ex.x, ex.y, ex.z, 0, 0.05, 0);
        }
    }

    // ------------------------------------------------------------------------------------- weapons

    public static void event(CraftEntity c, int code) {
        if (c instanceof ru.aiforge.realwar.craft.BattleshipEntity bs) {
            BattleshipFx.event(bs, code);
            return;
        }
        ClientLevel level = (ClientLevel) c.level();
        int li = code / 8, mi = code % 8;
        if (li >= c.kind.loads.length) return;
        CraftKind.Load l = c.kind.loads[li];
        float[] mz = l.muzzles()[mi % l.muzzles().length];
        Vec3 m = c.localToWorld(mz[0], mz[1], mz[2]);
        Vec3 dir = l.turret() ? c.dirFromAngles(c.gunYaw(), c.gunPitch()) : c.basis()[0];
        if (l.turret()) m = m.add(dir.scale(l.weapon().type == CraftWeapon.Type.CANNON ? 1.4 : 1.0));
        switch (l.weapon().type) {
            case CANNON, MG -> TrailFx.muzzleFlash(level, m, dir);
            case ROCKET, ATGM_BEAM, ATGM_SAL -> {
                TrailFx.muzzleFlash(level, m, dir);
                for (int i = 0; i < 5; i++) {
                    FxParticle s = new FxParticle(level, m.x - dir.x * i * 0.6, m.y - dir.y * i * 0.6, m.z - dir.z * i * 0.6, Sprites.smoke(R.nextInt(4)), 40, false, 0.8f);
                    s.velocity(-dir.x * 0.15 + R.nextGaussian() * 0.03, 0.02, -dir.z * 0.15 + R.nextGaussian() * 0.03);
                    s.motion = (pp, age) -> {
                        float ff = age / 40f;
                        pp.translate(pp.getDeltaX(), pp.getDeltaY(), pp.getDeltaZ());
                        pp.scaleVelocity(0.9);
                        pp.size = 0.8f + 2.4f * ff;
                        pp.color(0.85f, 0.85f, 0.85f);
                        pp.alpha(0.5f * (1 - ff));
                    };
                    FxSystem.add(s);
                }
            }
            case TORPEDO -> {
                for (int i = 0; i < 30; i++) level.addParticle(ParticleTypes.BUBBLE, m.x + R.nextGaussian() * 0.5, m.y + R.nextGaussian() * 0.5, m.z + R.nextGaussian() * 0.5, dir.x * 0.5, 0.1, dir.z * 0.5);
            }
            default -> {}
        }
    }

    public static void ordnance(CraftOrdnance o) {
        ClientLevel level = (ClientLevel) o.level();
        Vec3 a = new Vec3(o.xo, o.yo, o.zo), b = o.position();
        if (o.tickCount < 2) return;
        switch (o.type()) {
            case VIKHR, HELLFIRE -> {
                if (o.tickCount < 70) TrailFx.trail(level, o.getId(), a, b, 0.42, 0.9, true, false);
            }
            case S8, HYDRA -> {
                if (o.tickCount < o.type().burn + 4) TrailFx.trail(level, o.getId(), a, b, 0.38, 0.7, true, false);
            }
            case KALIBR -> {
                if (level.getFluidState(o.blockPosition()).is(FluidTags.WATER)) {
                    if (R.nextInt(2) == 0) level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, b.x, b.y, b.z, 0, 0.3, 0);
                } else {
                    TrailFx.trail(level, o.getId(), a, b, 0.5, 1.1, true, false);
                }
            }
            case TORPEDO -> {
                if (level.getFluidState(o.blockPosition()).is(FluidTags.WATER) && R.nextInt(2) == 0)
                    level.addParticle(ParticleTypes.BUBBLE, b.x + R.nextGaussian() * 0.2, b.y, b.z + R.nextGaussian() * 0.2, 0, 0.06, 0);
            }
            case FLARE -> {
                if (o.tickCount > CraftOrdnance.Type.FLARE.life) return;
                FxParticle core = new FxParticle(level, b.x, b.y, b.z, Sprites.flare(), 2, true, 2.2f + R.nextFloat());
                core.fullBright = true;
                core.motion = (pp, age) -> {
                    pp.color(1.0f, 0.95f, 0.8f);
                    pp.alpha(1.0f - age / 2.0f);
                };
                FxSystem.add(core);
                if (!TrailFx.crowded()) {
                    FxParticle s = new FxParticle(level, b.x, b.y, b.z, Sprites.smoke(R.nextInt(4)), 50, false, 0.6f);
                    s.velocity(0, 0.01, 0);
                    s.motion = (pp, age) -> {
                        float ff = age / 50f;
                        pp.translate(pp.getDeltaX(), pp.getDeltaY(), pp.getDeltaZ());
                        pp.size = 0.6f + 2.2f * ff;
                        pp.color(0.92f, 0.92f, 0.92f);
                        pp.alpha(0.55f * (1 - ff));
                    };
                    FxSystem.add(s);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------- sounds

    private static void sounds(CraftEntity c) {
        Loop[] l = LOOPS.get(c.getId());
        boolean running = c instanceof HeliEntity || c instanceof ru.aiforge.realwar.craft.PlaneEntity ? c.aux > 0.03f : c.engineOn();
        if (l != null && (l[0].isStopped() || l[0].c != c)) {
            LOOPS.remove(c.getId());
            l = null;
        }
        if (l == null && running && !c.isRemoved()) {
            Minecraft mc = Minecraft.getInstance();
            if (c instanceof HeliEntity) {
                l = new Loop[]{new Loop(c, ModSounds.ROTOR.get(), 0), new Loop(c, ModSounds.ROTOR_TURBINE.get(), 1)};
            } else if (c instanceof ru.aiforge.realwar.craft.PlaneEntity) {
                l = c.kind.plane.jet()
                        ? new Loop[]{new Loop(c, ru.aiforge.arsenal.registry.ArsenalRegistry.SND_JET.get(), 5), new Loop(c, ru.aiforge.tubombers.registry.ModRegistry.SND_AFTERBURNER.get(), 6), new Loop(c, ModSounds.AIR_RUSH.get(), 4)}
                        : new Loop[]{new Loop(c, ModSounds.PROP_PISTON.get(), 3), new Loop(c, ModSounds.AIR_RUSH.get(), 4)};
            } else {
                l = new Loop[]{new Loop(c, ModSounds.BOAT_ENGINE.get(), 2)};
            }
            for (Loop x : l) mc.getSoundManager().play(x);
            LOOPS.put(c.getId(), l);
        }
    }

    public static void reset() {
        LOOPS.clear();
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        final CraftEntity c;
        final int kind;

        Loop(CraftEntity c, SoundEvent e, int kind) {
            super(e, SoundSource.NEUTRAL, RandomSource.create());
            this.c = c;
            this.kind = kind;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.x = c.getX();
            this.y = c.getY();
            this.z = c.getZ();
        }

        @Override
        public void tick() {
            boolean running = this.c instanceof HeliEntity || this.c instanceof ru.aiforge.realwar.craft.PlaneEntity ? this.c.aux > 0.02f : this.c.engineOn();
            if (this.c.isRemoved() || !running) {
                this.stop();
                return;
            }
            this.x = this.c.getX();
            this.y = this.c.getY() + 2;
            this.z = this.c.getZ();
            float a = Mth.clamp(this.c.aux, 0f, 1.1f);
            switch (this.kind) {
                case 0 -> {   // blade slap: volume = audible range / 16
                    this.volume = 0.3f + 4.5f * a * a * (0.6f + 0.4f * this.c.aux2);
                    this.pitch = 0.5f + 0.5f * a;
                }
                case 1 -> {   // turbines
                    this.volume = this.c.engineOn() ? 0.4f + 1.8f * a : 0.05f;
                    this.pitch = 0.6f + 0.8f * a;
                }
                case 3 -> {   // piston engines: big radial/V12 drone, pitch follows rpm
                    int n = this.c.kind.plane.engines();
                    this.volume = (0.5f + 3.2f * a) * (n > 1 ? 1.5f : 1f);
                    this.pitch = (n > 2 ? 0.48f : n > 1 ? 0.52f : 0.6f) + 0.55f * a;
                }
                case 5 -> {   // turbofan whine + roar
                    this.volume = 0.6f + 4.0f * a;
                    this.pitch = 0.55f + 0.75f * a;
                }
                case 6 -> {   // afterburner rumble
                    float ab = this.c instanceof ru.aiforge.realwar.craft.PlaneEntity pe ? pe.abVis : 0f;
                    this.volume = 0.01f + 5.0f * ab;
                    this.pitch = 0.8f + 0.1f * ab;
                }
                case 4 -> {   // slipstream / wind over the airframe
                    float v = (float) Math.min(1.0, this.c.vel.length() / 160.0);
                    this.volume = 0.05f + 2.2f * v * v;
                    this.pitch = 0.7f + 0.6f * v;
                }
                default -> {
                    float out = this.c instanceof BoatEntity b ? Math.abs(b.output()) : 0f;
                    boolean sub = this.c.kind.isSub();
                    boolean under = sub && this.c instanceof BoatEntity b2 && b2.subFrac > 0.97f;
                    this.volume = under ? 0.25f + 0.4f * out : 0.6f + 2.0f * out;
                    this.pitch = sub ? 0.45f + 0.3f * out : 0.55f + 0.75f * out;
                }
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getVehicle() == this.c && mc.options.getCameraType() == CameraType.FIRST_PERSON) this.volume *= 0.5f;
        }
    }

    static BlockPos below(Vec3 p) {
        return BlockPos.containing(p.x, p.y - 1, p.z);
    }
}
