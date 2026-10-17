package ru.aiforge.realwar.client.craft;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Method;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Vector3f;
import ru.aiforge.realwar.client.AircraftClient;
import ru.aiforge.realwar.client.VehicleClient;
import ru.aiforge.realwar.craft.BoatEntity;
import ru.aiforge.realwar.craft.CraftControlPacket;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.craft.CraftStatePacket;
import ru.aiforge.realwar.craft.HeliEntity;
import ru.aiforge.realwar.craft.PlaneEntity;
import ru.aiforge.realwar.handheld.AtMissileEntity;
import ru.aiforge.realwar.network.ModNetwork;
import ru.aiforge.realwar.registry.ModSounds;

/**
 * Client control of helicopters / boats / submarine.
 *
 * <p>Helicopter pilot: the MOUSE is the cyclic. Every frame the look change produced by the mouse is taken out of
 * the player's rotation, accumulated as a stick rate command and the view is locked back onto the airframe
 * (camera rolls with the helicopter). Hold Left Alt for free look. V toggles the stabilisation ("hover") mode in
 * which roll is locked level and mouse X yaws the helicopter.
 */
public final class CraftClient {
    public static final KeyMapping KEY_FREELOOK = new KeyMapping("key.aiarsenal.rw.freelook", InputConstants.Type.KEYSYM, 342, "key.categories.aiarsenal_rw");

    private static float accX, accY;
    private static float lastYaw = Float.NaN, lastPitch;
    private static float freeYaw, freePitch;
    public static boolean zoom;
    private static boolean fireWas;
    public static int missileWarn;
    private static int ridingTicks;
    private static Method cameraMove;
    /** Last mouse stick command (deg/tick) for the HUD stick indicator. */
    public static float stickX, stickY;

    private CraftClient() {}

    public static void install() {
        CraftEntity.localPilot = c -> {
            LocalPlayer p = Minecraft.getInstance().player;
            return p != null && p.getVehicle() == c && c.seatOf(p) == 0;
        };
        CraftEntity.clientInput = CraftClient::input;
        CraftEntity.statePublisher = c -> ModNetwork.CHANNEL.sendToServer(new CraftStatePacket(c.getId(), c.getX(), c.getY(), c.getZ(),
                (float) c.vel.x, (float) c.vel.y, (float) c.vel.z, c.getYRot(), c.pitch, c.roll, c.aux, c.aux2, c.aux3, c.impact));
        CraftEntity.clientFx = CraftFx::tick;
        CraftEntity.clientEvent = CraftFx::event;
        CraftEntity.clientOrdnanceFx = CraftFx::ordnance;
        CraftEntity.snapHandler = m -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.getEntity(m.id()) instanceof CraftEntity c) c.snapTo(m.x(), m.y(), m.z());
        };
    }

    public static CraftEntity riding() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.getVehicle() instanceof CraftEntity c ? c : null;
    }

    private static float[] input(CraftEntity c) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        float[] in = new float[8];
        stickX = accX;
        stickY = accY;
        if (p == null || mc.screen != null) {
            accX = accY = 0;
            return in;
        }
        in[0] = Math.signum(p.input.forwardImpulse);
        in[1] = -Math.signum(p.input.leftImpulse);
        in[2] = Mth.clamp(accX, -30f, 30f);
        in[3] = Mth.clamp(accY, -30f, 30f);
        in[4] = mc.options.keyJump.isDown() ? 1 : 0;
        in[5] = AircraftClient.KEY_PITCH_DOWN.isDown() ? 1 : 0;
        accX = accY = 0;
        return in;
    }

    /** @return true while riding a craft (the ground-vehicle client then stays out of the way). */
    public static boolean clientTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        CraftEntity c = riding();
        if (c == null) {
            lastYaw = Float.NaN;
            zoom = false;
            freeYaw = freePitch = 0;
            missileWarn = 0;
            ridingTicks = 0;
            return false;
        }
        ridingTicks++;
        int actions = 0;
        while (VehicleClient.KEY_ENGINE.consumeClick()) actions |= CraftControlPacket.A_ENGINE;
        while (VehicleClient.KEY_LOCK.consumeClick()) actions |= CraftControlPacket.A_WEAPON;
        while (VehicleClient.KEY_SEAT.consumeClick()) actions |= CraftControlPacket.A_SEAT;
        boolean plane = c instanceof PlaneEntity;
        // planes: V gear, N flaps, R bomb load; helicopters: V hover mode, U gear
        while (AircraftClient.KEY_GEAR.consumeClick()) actions |= plane ? CraftControlPacket.A_GEAR : CraftControlPacket.A_MODE;
        while (AircraftClient.KEY_BAY.consumeClick()) actions |= plane ? 0 : CraftControlPacket.A_GEAR;
        if (plane) {
            while (AircraftClient.KEY_FLAPS.consumeClick()) actions |= CraftControlPacket.A_MODE;
            while (VehicleClient.KEY_RELOAD.consumeClick()) actions |= CraftControlPacket.A_LOADOUT;
        }
        while (AircraftClient.KEY_BRAKE.consumeClick()) actions |= CraftControlPacket.A_FLARE;
        while (VehicleClient.KEY_SIGHT.consumeClick()) {
            zoom = !zoom;
            if (c.kind.isSub()) actions |= CraftControlPacket.A_PERISCOPE;
        }
        while (VehicleClient.KEY_RELOAD.consumeClick() || VehicleClient.KEY_MAP.consumeClick() || AircraftClient.KEY_FLAPS.consumeClick()
                || AircraftClient.KEY_BOMBSEL.consumeClick() || AircraftClient.KEY_RUDDER_L.consumeClick() || AircraftClient.KEY_RUDDER_R.consumeClick()) {
        }
        boolean fire = mc.screen == null && mc.options.keyAttack.isDown() && ridingTicks > 5;
        if (actions != 0 || fire || fireWas) ModNetwork.CHANNEL.sendToServer(new CraftControlPacket(c.getId(), fire, actions));
        fireWas = fire;

        // missile approach warning (IR missiles locked on this helicopter / boat)
        if (missileWarn > 0) missileWarn--;
        if (c.tickCount % 4 == 0) {
            for (AtMissileEntity m : mc.level.getEntitiesOfClass(AtMissileEntity.class, c.getBoundingBox().inflate(600))) {
                if (m.targetId() == c.getId()) { missileWarn = 12; break; }
            }
        }
        if (missileWarn > 0 && c.tickCount % 8 == 0)
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.MISSILE_WARN.get(), 1.0f, 0.8f));
        if (c instanceof BoatEntity b && c.kind.isSub() && b.subFrac > 0.97f && c.tickCount % 200 == 37)
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SONAR_PING.get(), 1.0f, 0.35f));
        return true;
    }

    /** Laser range along the operator's line of sight (HUD), metres or -1. */
    public static double laserRange(Entity op, double max) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = op.getEyePosition(), end = eye.add(op.getLookAngle().scale(max));
        BlockHitResult h = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, op));
        return h.getType() == HitResult.Type.MISS ? -1 : h.getLocation().distanceTo(eye);
    }

    public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        CraftEntity c = riding();
        if (p == null || c == null) {
            lastYaw = Float.NaN;
            return;
        }
        float pt = (float) e.getPartialTick();
        int seat = c.seatOf(p);
        float hy = Mth.rotLerp(pt, c.yRotO, c.getYRot());
        float hp = c.pitchLerp(pt), hr = c.rollLerp(pt);
        boolean first = mc.options.getCameraType().isFirstPerson();
        boolean plane = c instanceof PlaneEntity;
        if ((c instanceof HeliEntity || plane) && seat == 0 && mc.screen == null) {
            if (!Float.isNaN(lastYaw)) {
                float dy = Mth.wrapDegrees(p.getYRot() - lastYaw), dp = p.getXRot() - lastPitch;
                if (KEY_FREELOOK.isDown()) {
                    freeYaw = Mth.clamp(freeYaw + dy, -170f, 170f);
                    freePitch = Mth.clamp(freePitch + dp, -80f, 80f);
                } else {
                    accX += dy;
                    accY -= dp;
                    freeYaw *= 0.8f;
                    freePitch *= 0.8f;
                }
            }
            float camYaw = hy + freeYaw;
            float camPitch = Mth.clamp(first ? -hp + freePitch : (plane ? 8f - hp * 0.5f : 14f) + freePitch, -90f, 90f);
            p.setYRot(camYaw);
            p.yRotO = camYaw;
            p.setXRot(camPitch);
            p.xRotO = camPitch;
            p.setYHeadRot(camYaw);
            p.yHeadRotO = camYaw;
            lastYaw = camYaw;
            lastPitch = camPitch;
            e.setYaw(camYaw);
            e.setPitch(camPitch);
            if (first) e.setRoll(hr * (1f - Math.min(1f, Math.abs(freeYaw) / 90f)));
        } else {
            lastYaw = Float.NaN;
            if (first) e.setRoll(hr * (c instanceof HeliEntity || plane ? 1f : 0.8f));
        }
        if (!first) pushCameraBack(e, mc, c);
    }

    private static void pushCameraBack(ViewportEvent.ComputeCameraAngles e, Minecraft mc, CraftEntity c) {
        Camera cam = e.getCamera();
        double vanilla = cam.getPosition().distanceTo(mc.player.getEyePosition((float) e.getPartialTick()));
        if (vanilla < 3.7) return;
        boolean bs = c.kind == ru.aiforge.realwar.craft.CraftKind.BISMARCK;
        double want = bs ? 120 : c.kind.isSub() ? 55 : c.kind.isPlane() ? Math.max(14.0, c.kind.plane.length() * 1.25) : c.kind.isHeli() ? 19 : 17;
        double extra = want - vanilla;
        if (extra <= 0.1) return;
        Vector3f look = cam.getLookVector();
        Vec3 back = new Vec3(-look.x(), -look.y(), -look.z());
        Vec3 pos = cam.getPosition();
        BlockHitResult hit = mc.level.clip(new ClipContext(pos, pos.add(back.scale(extra + 0.4)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.MISS) extra = Math.max(0, hit.getLocation().distanceTo(pos) - 0.4);
        try {
            if (cameraMove == null) {
                for (String n : new String[]{"m_90568_", "move"}) {
                    try {
                        cameraMove = ObfuscationReflectionHelper.findMethod(Camera.class, n, double.class, double.class, double.class);
                        break;
                    } catch (Throwable ignored) {
                    }
                }
            }
            cameraMove.invoke(cam, -extra, Math.min(extra * 0.2, bs ? 25.0 : c.kind.isSub() ? 9.0 : 3.0), 0.0);
        } catch (Throwable t) {
            cameraMove = null;
        }
    }

    public static void onFov(ViewportEvent.ComputeFov e) {
        if (!zoom || riding() == null || !e.usedConfiguredFov()) return;
        CraftEntity c = riding();
        double z = c.kind == ru.aiforge.realwar.craft.CraftKind.BISMARCK ? 6.0 : c.kind.isSub() ? 3.0 : 4.0;
        e.setFOV(Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(e.getFOV()) / 2) / z)));
    }

    /** LMB / RMB inside a craft never break blocks or use items. */
    public static boolean onInteraction(InputEvent.InteractionKeyMappingTriggered e) {
        if (riding() == null) return false;
        if (e.isAttack() || e.isUseItem()) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
        return true;
    }

    public static boolean hideHand() {
        return riding() != null;
    }

    public static float freeLookYaw() {
        return freeYaw;
    }

    static AABB around(Entity e, double r) {
        return e.getBoundingBox().inflate(r);
    }
}
