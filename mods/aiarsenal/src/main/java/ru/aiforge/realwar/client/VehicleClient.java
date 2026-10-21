/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.InputConstants$Type
 *  net.minecraft.client.Camera
 *  net.minecraft.client.CameraType
 *  net.minecraft.client.KeyMapping
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
 *  net.minecraft.client.resources.sounds.SoundInstance
 *  net.minecraft.core.BlockPos
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.util.Mth
 *  net.minecraft.util.RandomSource
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.level.ClipContext
 *  net.minecraft.world.level.ClipContext$Block
 *  net.minecraft.world.level.ClipContext$Fluid
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.BlockHitResult
 *  net.minecraft.world.phys.HitResult$Type
 *  net.minecraft.world.phys.Vec3
 *  net.minecraftforge.client.event.InputEvent$InteractionKeyMappingTriggered
 *  net.minecraftforge.client.event.ViewportEvent$ComputeCameraAngles
 *  net.minecraftforge.fml.util.ObfuscationReflectionHelper
 *  org.joml.Vector3f
 */
package ru.aiforge.realwar.client;

import com.mojang.blaze3d.platform.InputConstants;
import ru.aiforge.realwar.RealWar;
import ru.aiforge.realwar.client.AircraftClient;
import ru.aiforge.realwar.client.fx.TrailFx;
import ru.aiforge.realwar.client.particle.FxParticle;
import ru.aiforge.realwar.client.particle.FxSystem;
import ru.aiforge.realwar.client.particle.Sprites;
import ru.aiforge.realwar.client.screen.ComputerScreen;
import ru.aiforge.realwar.client.sight.SightClient;
import ru.aiforge.realwar.missile.LaunchComputerBlock;
import ru.aiforge.realwar.missile.LaunchPadBlock;
import ru.aiforge.realwar.missile.LaunchPadBlockEntity;
import ru.aiforge.realwar.missile.MissileEntity;
import ru.aiforge.realwar.network.ModNetwork;
import ru.aiforge.realwar.network.VehicleControlPacket;
import ru.aiforge.realwar.network.VehicleInteractPacket;
import ru.aiforge.realwar.network.VehicleStatePacket;
import ru.aiforge.realwar.ordnance.RocketEntity;
import ru.aiforge.realwar.registry.ModSounds;
import ru.aiforge.realwar.vehicle.FlakEntity;
import ru.aiforge.realwar.vehicle.LauncherVehicle;
import ru.aiforge.realwar.vehicle.VehicleEntity;
import ru.aiforge.realwar.vehicle.VehicleKind;
import ru.aiforge.realwar.vehicle.VehicleSpec;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Vector3f;

public final class VehicleClient {
    public static final KeyMapping KEY_RELOAD = new KeyMapping("key.aiarsenal.rw.reload", InputConstants.Type.KEYSYM, 82, "key.categories.aiarsenal_rw");
    public static final KeyMapping KEY_LOCK = new KeyMapping("key.aiarsenal.rw.lock", InputConstants.Type.KEYSYM, 71, "key.categories.aiarsenal_rw");
    public static final KeyMapping KEY_ENGINE = new KeyMapping("key.aiarsenal.rw.engine", InputConstants.Type.KEYSYM, 72, "key.categories.aiarsenal_rw");
    public static final KeyMapping KEY_SEAT = new KeyMapping("key.aiarsenal.rw.seat", InputConstants.Type.KEYSYM, 88, "key.categories.aiarsenal_rw");
    public static final KeyMapping KEY_OPTICS = new KeyMapping("key.aiarsenal.rw.optics", InputConstants.Type.KEYSYM, 75, "key.categories.aiarsenal_rw");   // r21: day / NV / thermal
    public static final KeyMapping KEY_SIGHT = new KeyMapping("key.aiarsenal.rw.sight", InputConstants.Type.KEYSYM, 90, "key.categories.aiarsenal_rw");
    public static final KeyMapping KEY_MAP = new KeyMapping("key.aiarsenal.rw.map", InputConstants.Type.KEYSYM, 77, "key.categories.aiarsenal_rw");
    /** r15: show / hide the compact control panel of the War Thunder style HUD. */
    public static final KeyMapping KEY_HINTS = new KeyMapping("key.aiarsenal.rw.hints", InputConstants.Type.KEYSYM, 74, "key.categories.aiarsenal_rw");
    private static final Map<Integer, EngineSound> ENGINES = new HashMap<Integer, EngineSound>();
    private static Method cameraMove;
    public static int ridingTicks;
    private static boolean useArmed;
    private static int lastVehicleId;

    private VehicleClient() {
    }

    public static void install() {
        VehicleEntity.clientInput = VehicleClient::input;
        VehicleEntity.clientHook = VehicleClient::entityTick;
        VehicleEntity.localDriver = v -> Minecraft.getInstance().player != null && v.getControllingPassenger() == Minecraft.getInstance().player;
        VehicleEntity.statePublisher = v -> ModNetwork.CHANNEL.sendToServer(new VehicleStatePacket(v.getId(), v.getX(), v.getY(), v.getZ(), v.getYRot(), (float)v.speedMs, v.steerVis));
        MissileEntity.clientHook = TrailFx::missileTick;
        LaunchPadBlockEntity.clientHook = be -> {
            ClientLevel cl;
            int t;
            Level patt3533$temp;
            if (be.state() == LaunchPadBlockEntity.State.LAUNCH && (patt3533$temp = be.getLevel()) instanceof ClientLevel && (t = (int)((cl = (ClientLevel)patt3533$temp).getGameTime() - be.stateStart())) >= 0 && t < 60) {
                double h = LaunchPadBlock.HEIGHT[be.padLevel()];
                BlockPos p = be.getBlockPos();
                TrailFx.padLaunch(cl, new Vec3((double)p.getX() + (double)be.padLevel() / 2.0, (double)p.getY() + h + 0.22, (double)p.getZ() + (double)be.padLevel() / 2.0), be.padLevel(), t);
            }
        };
        LaunchComputerBlock.openScreen = p -> Minecraft.getInstance().setScreen((Screen)new ComputerScreen((BlockPos)p));
        RocketEntity.clientHook = e -> {
            if (e instanceof ru.aiforge.realwar.ordnance.ShellEntity sh) {
                TrailFx.shellTick(sh);
                ShellWhistle.tick(sh);
                return;
            }
            TrailFx.rocketTick(e);
            RocketSound.tick(e);
        };
    }

    private static float[] input(VehicleEntity v) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || v.seatOf((Entity)p) != 0) {
            return new float[]{0.0f, 0.0f, 1.0f};
        }
        float fw = p.input.forwardImpulse;
        float st = p.input.leftImpulse;
        return new float[]{fw, st, p.input.jumping ? 1.0f : 0.0f};
    }

    public static void clientTick(Minecraft mc) {
        LauncherVehicle lv;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            return;
        }
        if (AircraftClient.clientTick(mc)) {
            return;
        }
        Entity entity = p.getVehicle();
        if (!(entity instanceof VehicleEntity)) {
            ridingTicks = 0;
            lastVehicleId = -1;
            while (KEY_ENGINE.consumeClick() || KEY_LOCK.consumeClick() || KEY_SEAT.consumeClick() || KEY_SIGHT.consumeClick() || KEY_MAP.consumeClick()) {
            }
            return;
        }
        VehicleEntity v = (VehicleEntity)entity;
        if (v.getId() != lastVehicleId) {
            lastVehicleId = v.getId();
            ridingTicks = 0;
        }
        ++ridingTicks;
        if (!mc.options.keyUse.isDown()) {
            useArmed = true;
        }
        int seat = v.seatOf((Entity)p);
        if (v instanceof LauncherVehicle && SightClient.isOperator(mc, (lv = (LauncherVehicle)v))) {
            while (KEY_SIGHT.consumeClick()) {
                SightClient.cycle(lv);
            }
            while (KEY_OPTICS.consumeClick()) {
                ru.aiforge.realwar.client.sight.Optics.cycle();
            }
            if (mc.screen == null) {
                while (KEY_MAP.consumeClick()) {
                    SightClient.openMap(lv);
                }
            }
        } else {
            while (KEY_SIGHT.consumeClick() || KEY_MAP.consumeClick() || KEY_OPTICS.consumeClick()) {
            }
        }
        int actions = 0;
        if (KEY_ENGINE.consumeClick()) {
            actions |= 1;
        }
        if (KEY_LOCK.consumeClick()) {
            actions |= 2;
        }
        if (KEY_SEAT.consumeClick()) {
            actions |= 4;
        }
        while (AircraftClient.KEY_BRAKE.consumeClick()) {
            actions |= 8;                      // r20: smoke grenades
        }
        boolean fire = mc.screen == null && mc.options.keyAttack.isDown() && mc.mouseHandler.isMouseGrabbed();
        boolean reload = mc.screen == null && KEY_RELOAD.isDown();
        float thr = seat == 0 && v.kind.drivable() ? p.input.forwardImpulse : 0.0f;
        float steer = seat == 0 && v.kind.drivable() ? p.input.leftImpulse : 0.0f;
        ModNetwork.CHANNEL.sendToServer(VehicleControlPacket.of(thr, steer, fire, reload, actions));
    }

    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            return;
        }
        if (AircraftClient.onInteraction(e, mc, p)) {
            return;
        }
        if (p.getVehicle() instanceof VehicleEntity) {
            if (e.isAttack() || e.isUseItem()) {
                LauncherVehicle lv;
                Entity entity;
                if (e.isUseItem() && useArmed && (entity = p.getVehicle()) instanceof LauncherVehicle && SightClient.isOperator(mc, (lv = (LauncherVehicle)entity))) {
                    useArmed = false;
                    SightClient.cycle(lv);
                }
                e.setCanceled(true);
                e.setSwingHand(false);
            }
            return;
        }
        if (!e.isUseItem() || p.isSecondaryUseActive() && !p.getMainHandItem().isEmpty()) {
            return;
        }
        double reach = 6.5;
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double blockDist = Double.MAX_VALUE;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            blockDist = mc.hitResult.getLocation().distanceTo(eye);
        }
        VehicleEntity best = null;
        int bestPart = -1;
        double bestT = Math.min(reach, blockDist);
        AABB box = p.getBoundingBox().inflate(16.0);
        for (VehicleEntity v : mc.level.getEntitiesOfClass(VehicleEntity.class, box)) {
            VehicleSpec s = v.spec;
            double yaw = Math.toRadians(v.getYRot());
            double c = Math.cos(yaw);
            double sn = Math.sin(yaw);
            Vec3 rel = eye.subtract(v.position());
            Vec3 o = new Vec3(rel.x * c + rel.z * sn, rel.y, -rel.x * sn + rel.z * c);
            Vec3 d = new Vec3(look.x * c + look.z * sn, look.y, -look.x * sn + look.z * c);
            for (int i = 0; i < s.parts().length; ++i) {
                float[] b = s.parts()[i];
                double t = VehicleClient.rayBox(o, d, b[0] - b[3], b[1] - b[4], b[2] - b[5], b[0] + b[3], b[1] + b[4], b[2] + b[5]);
                if (!(t >= 0.0) || !(t < bestT)) continue;
                bestT = t;
                best = v;
                bestPart = i;
            }
        }
        if (best != null) {
            ModNetwork.CHANNEL.sendToServer(new VehicleInteractPacket(best.getId(), bestPart));
            e.setCanceled(true);
            e.setSwingHand(true);
        }
    }

    private static double rayBox(Vec3 o, Vec3 d, double x0, double y0, double z0, double x1, double y1, double z1) {
        double tmin = 0.0;
        double tmax = Double.MAX_VALUE;
        double[] os = new double[]{o.x, o.y, o.z};
        double[] ds = new double[]{d.x, d.y, d.z};
        double[] lo = new double[]{x0, y0, z0};
        double[] hi = new double[]{x1, y1, z1};
        for (int i = 0; i < 3; ++i) {
            if (Math.abs(ds[i]) < 1.0E-9) {
                if (!(os[i] < lo[i]) && !(os[i] > hi[i])) continue;
                return -1.0;
            }
            double t1 = (lo[i] - os[i]) / ds[i];
            double t2 = (hi[i] - os[i]) / ds[i];
            if (t1 > t2) {
                double t = t1;
                t1 = t2;
                t2 = t;
            }
            if (!((tmin = Math.max(tmin, t1)) > (tmax = Math.min(tmax, t2)))) continue;
            return -1.0;
        }
        return tmin;
    }

    private static boolean cameraWarned;

    /** r15: first person in a tank = the gunner's sight beside the gun: the camera sits on the mantlet just above the
     *  bore (turret + gun pitch frame), so the view swings with the turret and nothing of the crew is visible. */
    private static void gunnerSight(ViewportEvent.ComputeCameraAngles e, ru.aiforge.realwar.vehicle.GunVehicle gv) {
        double[] sl = gv.sightLocal();
        org.joml.Vector3d t = gv.pitchFrame(gv.az(), gv.el()).transformPosition(new org.joml.Vector3d(sl[0], sl[1], sl[2]));
        double pt = e.getPartialTick();
        Vec3 target = new Vec3(t.x + Mth.lerp(pt, gv.xo, gv.getX()) - gv.getX(), t.y + Mth.lerp(pt, gv.yo, gv.getY()) - gv.getY(),
                t.z + Mth.lerp(pt, gv.zo, gv.getZ()) - gv.getZ());
        Camera cam = e.getCamera();
        Vec3 d = target.subtract(cam.getPosition());
        Vector3f fw = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
        camMove(cam, d.x * fw.x() + d.y * fw.y() + d.z * fw.z(), d.x * up.x() + d.y * up.y() + d.z * up.z(),
                d.x * left.x() + d.y * left.y() + d.z * left.z());
    }

    /** r20: MLRS sight (Grad, Smerch, BM-13) - from the panorama beside the tube pack, not from the driver in the cab. */
    private static void launcherSight(ViewportEvent.ComputeCameraAngles e, ru.aiforge.realwar.vehicle.MlrsEntity lv) {
        double[] sl = lv.launcherSightLocal();
        if (sl == null) return;
        org.joml.Vector3d t = lv.pitchFrame(lv.az(), lv.el()).transformPosition(new org.joml.Vector3d(sl[0], sl[1], sl[2]));
        double pt = e.getPartialTick();
        Vec3 target = new Vec3(t.x + Mth.lerp(pt, lv.xo, lv.getX()) - lv.getX(), t.y + Mth.lerp(pt, lv.yo, lv.getY()) - lv.getY(),
                t.z + Mth.lerp(pt, lv.zo, lv.getZ()) - lv.getZ());
        Camera cam = e.getCamera();
        Vec3 d = target.subtract(cam.getPosition());
        Vector3f fw = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
        camMove(cam, d.x * fw.x() + d.y * fw.y() + d.z * fw.z(), d.x * up.x() + d.y * up.y() + d.z * up.z(),
                d.x * left.x() + d.y * left.y() + d.z * left.z());
    }

    private static void camMove(Camera cam, double fwd, double up, double left) {
        try {
            if (cameraMove == null) {
                for (String n : new String[]{"m_90568_", "move"}) {
                    try {
                        cameraMove = ObfuscationReflectionHelper.findMethod(Camera.class, n, Double.TYPE, Double.TYPE, Double.TYPE);
                        break;
                    } catch (Throwable ignored) {
                    }
                }
            }
            cameraMove.invoke(cam, fwd, up, left);
        } catch (Throwable t) {
            if (!cameraWarned) RealWar.LOGGER.error("camera move", t);
            cameraWarned = true;
        }
    }

    public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        Entity entity;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !((entity = mc.player.getVehicle()) instanceof VehicleEntity)) {
            return;
        }
        VehicleEntity v = (VehicleEntity)entity;
        if (mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            if (v instanceof ru.aiforge.realwar.vehicle.GunVehicle gv) gunnerSight(e, gv);
            else if (v instanceof ru.aiforge.realwar.vehicle.MlrsEntity ml && SightClient.active()) launcherSight(e, ml);
            return;
        }
        Camera cam = e.getCamera();
        double vanilla = cam.getPosition().distanceTo(mc.player.getEyePosition((float)e.getPartialTick()));
        if (vanilla < 3.7) {
            return;
        }
        double want = Math.max(6.0, (double)v.spec.length() * 1.05 + 4.0);   // T-90 ~14 m, Yars ~28 m behind
        double extra = want - vanilla;
        if (extra <= 0.1) {
            return;
        }
        Vector3f look = cam.getLookVector();
        Vec3 back = new Vec3((double)(-look.x()), (double)(-look.y()), (double)(-look.z()));
        Vec3 pos = cam.getPosition();
        BlockHitResult hit = mc.level.clip(new ClipContext(pos, pos.add(back.scale(extra + 0.4)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, (Entity)mc.player));
        if (hit.getType() != HitResult.Type.MISS) {
            extra = Math.max(0.0, hit.getLocation().distanceTo(pos) - 0.4);
        }
        try {
            if (cameraMove == null) {
                // SRG name first: in a normal (obfuscated) game "move" does not exist - the lookup failed every frame
                // and the 3rd-person camera never backed away from big vehicles (only the dev client worked)
                for (String n : new String[]{"m_90568_", "move"}) {
                    try {
                        cameraMove = ObfuscationReflectionHelper.findMethod(Camera.class, n, Double.TYPE, Double.TYPE, Double.TYPE);
                        break;
                    } catch (Throwable ignored) {
                    }
                }
            }
            cameraMove.invoke(cam, -extra, Math.min(extra * 0.22, 2.5), 0.0);
        }
        catch (Throwable t) {
            if (!cameraWarned) RealWar.LOGGER.error("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u043e\u0442\u043e\u0434\u0432\u0438\u043d\u0443\u0442\u044c \u043a\u0430\u043c\u0435\u0440\u0443", t);
            cameraWarned = true;
            cameraMove = null;
        }
    }

    private static void entityTick(VehicleEntity v) {
        FlakEntity f;
        LauncherVehicle lv;
        int k;
        EngineSound s;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        if (v.engineOn() && v.kind.drivable() && ((s = ENGINES.get(v.getId())) == null || s.isStopped())) {
            s = new EngineSound(v);
            ENGINES.put(v.getId(), s);
            mc.getSoundManager().play((SoundInstance)s);
        }
        if (v.kind.drivable() && v.engineOn() && v.tickCount % 4 == 0) {
            VehicleClient.exhaust(level, v);
        }
        if (v instanceof ru.aiforge.realwar.vehicle.GunVehicle gv) {
            int fx = gv.consumeFx();
            if (fx >= 0 && gv.perTube()) {
                // r12 TOS-1A: rocket launch flash + smoke from the tube that fired
                TrailFx.launch(level, gv.tubeWorld(fx), gv.launchDir(), 1, gv.kind.tubeLen);
            } else if (fx >= 0) {
                boolean big = gv.kind != VehicleKind.BMP3 && gv.kind != VehicleKind.BRADLEY;
                TrailFx.gunBlast(level, gv.tubeWorld(0), gv.launchDir(), big, gv.kind.artillery());
                if (gv.twinSalvo()) TrailFx.gunBlast(level, gv.tubeWorld(1), gv.launchDir(), big, gv.kind.artillery());
            }
            if (gv.coaxFiring() && gv.tickCount % 3 == 0) {
                TrailFx.muzzleFlash(level, gv.tubeWorld(gv.cfg.coax30() ? 1 : gv.spec.tubes().length - 1), gv.launchDir());
            }
        } else if (v instanceof LauncherVehicle && (k = (lv = (LauncherVehicle)v).consumeFx()) >= 0) {
            Vec3 m = lv.muzzleWorld(k);
            Vec3 d = lv.launchDir();
            int big = lv.kind.isSam() ? 2 : (lv.kind == VehicleKind.SMERCH || lv.kind == VehicleKind.YARS ? 1 : 0);
            double len = lv.kind.isSam() ? 0.0 : (double)lv.kind.tubeLen;
            TrailFx.launch(level, m, d, big, lv.kind.isSam() ? 2.0 : len);
        }
        if (v instanceof FlakEntity && (f = (FlakEntity)v).firing()) {
            for (int b = 0; b < 2; ++b) {
                if ((f.tickCount + b * 3) % 6 == 0) continue;
                TrailFx.muzzleFlash(level, f.barrelMuzzle(b, f.az(), f.el()), f.aimDir(f.az(), f.el()));
            }
        }
    }

    private static void exhaust(ClientLevel level, VehicleEntity v) {
        Vec3 p = v.localToWorld(-1.05, 0.75, (double)v.spec.length() * 0.18);
        float load = Math.abs(v.throttle());
        FxParticle s = new FxParticle(level, p.x, p.y, p.z, Sprites.smoke(v.tickCount), 26 + (int)(14.0f * load), false, 0.3f);
        float a = 0.18f + 0.22f * load;
        int life = s.getLifetime();
        s.velocity((level.random.nextDouble() - 0.5) * 0.02, 0.03, (level.random.nextDouble() - 0.5) * 0.02);
        s.motion = (pp, age) -> {
            float f = (float)age / (float)life;
            pp.translate(pp.getDeltaX(), pp.getDeltaY(), pp.getDeltaZ());
            pp.size = 0.3f + 1.1f * f;
            pp.color(0.25f, 0.25f, 0.27f);
            pp.alpha(a * (1.0f - f));
        };
        FxSystem.add(s);
    }

    public static void reset() {
        ENGINES.clear();
        ridingTicks = 0;
        lastVehicleId = -1;
        TrailFx.clear();
    }

    static {
        useArmed = true;
        lastVehicleId = -1;
    }

    private static final class EngineSound
    extends AbstractTickableSoundInstance {
        private final VehicleEntity v;

        EngineSound(VehicleEntity v) {
            super((SoundEvent)ModSounds.ENGINE_IDLE.get(), SoundSource.NEUTRAL, RandomSource.create());
            this.v = v;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.6f;
            this.x = v.getX();
            this.y = v.getY();
            this.z = v.getZ();
        }

        public void tick() {
            if (this.v.isRemoved() || !this.v.engineOn()) {
                this.stop();
                return;
            }
            this.x = this.v.getX();
            this.y = this.v.getY() + 1.0;
            this.z = this.v.getZ();
            float r = Mth.clamp((float)((this.v.rpm - 600.0f) / 2600.0f), (float)0.0f, (float)1.0f);
            this.volume = 1.3f + 0.9f * r;
            this.pitch = 0.62f + 1.0f * r;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getVehicle() == this.v && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
                this.volume *= 0.55f;
            }
        }
    }

    /** Incoming-shell whistle: plays once for the local player when a descending shell is ~4 s out. */
    public static final class ShellWhistle {
        private static final java.util.Set<Integer> PLAYED = new java.util.HashSet<Integer>();

        private ShellWhistle() {
        }

        static void tick(ru.aiforge.realwar.ordnance.ShellEntity e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || e.tickCount < 20 || PLAYED.contains(e.getId())) {
                return;
            }
            Vec3 v = e.position().subtract(e.xo, e.yo, e.zo);
            if (v.y >= 0.0) {
                return;
            }
            double d = e.position().distanceTo(mc.player.position());
            double sp = Math.max(0.5, v.length());
            if (d / sp < 80.0 && d < 260.0) {
                PLAYED.add(e.getId());
                if (PLAYED.size() > 256) PLAYED.clear();
                mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), (SoundEvent)ModSounds.SHELL_WHISTLE.get(), SoundSource.NEUTRAL, 6.0f, 0.95f + mc.level.random.nextFloat() * 0.1f, false);
            }
        }
    }

    public static final class RocketSound {
        private RocketSound() {
        }

        static void tick(RocketEntity e) {
            if (e.tickCount % 12 != 3) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), (SoundEvent)ModSounds.ROCKET_WHOOSH.get(), SoundSource.NEUTRAL, e.isBurning() ? 5.0f : 2.0f, 0.9f + mc.level.random.nextFloat() * 0.2f, false);
        }
    }
}

