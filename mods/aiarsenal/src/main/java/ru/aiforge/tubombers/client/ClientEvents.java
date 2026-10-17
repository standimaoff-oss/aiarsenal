package ru.aiforge.tubombers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import ru.aiforge.tubombers.TuBombers;
import ru.aiforge.tubombers.aircraft.AircraftData;
import ru.aiforge.tubombers.aircraft.FlightModel;
import ru.aiforge.tubombers.entity.BomberEntity;
import ru.aiforge.tubombers.entity.Crew;
import ru.aiforge.tubombers.fx.LevelEnv;
import ru.aiforge.tubombers.network.CrewActionPacket;
import ru.aiforge.tubombers.network.ModNetwork;
import ru.aiforge.tubombers.network.PilotInputPacket;

/** Crew controls, cockpit camera (rolls and pitches with the airframe), bombsight, CCIP/CCRP markers. */
@Mod.EventBusSubscriber(modid = TuBombers.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    public static boolean sight, hints = true;
    public static int zoom = 1;
    public static float inPitch, inRoll, inYaw, throttle;
    private static int throttleSyncedFor = -1;
    /** CCIP: predicted impact of the next bomb; CCRP solution {along, half, cross, ttr} */
    public static Vec3 ccip;
    public static double[] ccrp;
    public static float ccipTime;

    private ClientEvents() {
    }

    public static BomberEntity vehicle() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.getVehicle() instanceof BomberEntity b ? b : null;
    }

    public static AircraftData.Seat mySeat() {
        BomberEntity b = vehicle();
        return b == null ? null : b.seatOf(Minecraft.getInstance().player);
    }

    private static void send(BomberEntity b, int action, int arg) {
        ModNetwork.CHANNEL.sendToServer(new CrewActionPacket(b.getId(), action, arg));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientFx.tick();
        Hud.tick();
        if (mc.player == null || mc.level == null) return;
        BomberEntity b = vehicle();
        if (b == null) {
            sight = false;
            throttleSyncedFor = -1;
            ccip = null;
            ccrp = null;
            for (var k : ClientSetup.ALL) while (k.consumeClick()) ;
            return;
        }
        if (throttleSyncedFor != b.getId()) {
            throttle = b.throttle();
            throttleSyncedFor = b.getId();
        }
        Crew c = b.crewOf(mc.player);
        AircraftData.Seat s = b.seatOf(mc.player);
        while (ClientSetup.KEY_HINTS.consumeClick()) hints = !hints;
        while (ClientSetup.KEY_SEAT.consumeClick()) {
            if (s != null) send(b, BomberEntity.A_STAND, 0);
            else if (c != null && c.mode == Crew.WALK) send(b, BomberEntity.A_SIT, 0);
        }
        while (ClientSetup.KEY_LIGHTS.consumeClick()) send(b, BomberEntity.A_LIGHTS, 0);
        while (ClientSetup.KEY_LOAD.consumeClick()) send(b, BomberEntity.A_LOAD, 0);
        boolean fly = s != null && s.canFly();
        boolean bomb = s != null && (s.canFly() || s.bombardier());
        if (s == null || !s.bombardier()) sight = false;
        while (ClientSetup.KEY_GEAR.consumeClick()) if (fly) send(b, BomberEntity.A_GEAR, 0);
        while (ClientSetup.KEY_FLAPS.consumeClick()) if (fly) send(b, BomberEntity.A_FLAPS, mc.player.isShiftKeyDown() ? -1 : 1);
        while (ClientSetup.KEY_ENGINES.consumeClick()) if (fly) send(b, BomberEntity.A_ENGINES, 0);
        while (ClientSetup.KEY_BAY.consumeClick()) if (bomb) send(b, BomberEntity.A_BAY, 0);
        while (ClientSetup.KEY_MODE.consumeClick()) if (bomb) send(b, BomberEntity.A_MODE, 0);
        while (ClientSetup.KEY_SWEEP.consumeClick()) {
            if (fly && b.kind.swingWing) send(b, BomberEntity.A_SWEEP, mc.player.isShiftKeyDown() ? -1 : 1);
            else if (s != null && s.bombardier()) {
                sight = !sight;
                if (sight) mc.player.setXRot(80);
            }
        }
        while (ClientSetup.KEY_TARGET.consumeClick()) if (bomb) designate(b);
        while (mc.options.keyJump.consumeClick()) if (bomb) send(b, BomberEntity.A_RELEASE, 0);
        // continuous controls (keyboard -> smoothed stick)
        if (fly) {
            float tp = (mc.options.keyDown.isDown() ? 1 : 0) - (mc.options.keyUp.isDown() ? 1 : 0);
            float tr = (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0);
            float ty = (ClientSetup.KEY_RUDDER_R.isDown() ? 1 : 0) - (ClientSetup.KEY_RUDDER_L.isDown() ? 1 : 0);
            inPitch = ramp(inPitch, tp, 0.12f);
            inRoll = ramp(inRoll, tr, 0.15f);
            inYaw = ramp(inYaw, ty, 0.15f);
            float max = b.kind.maxThrottle();
            if (ClientSetup.KEY_THR_UP.isDown()) throttle = Math.min(max, throttle + 0.015f);
            if (ClientSetup.KEY_THR_DOWN.isDown()) throttle = Math.max(0, throttle - 0.015f);
        } else {
            inPitch = inRoll = inYaw = 0;
        }
        if (s != null) {
            boolean consent = bomb && mc.options.keyJump.isDown();
            ModNetwork.CHANNEL.sendToServer(new PilotInputPacket(b.getId(), inPitch, inRoll, inYaw, throttle,
                    fly && ClientSetup.KEY_BRAKES.isDown(), consent));
        }
        // fire-control solution for the HUD and the world markers
        if (bomb && b.bombCount() > 0) {
            LevelEnv env = new LevelEnv(mc.level);
            Vector3d start = b.st.toWorld(b.nextStation(), new Vector3d());
            double[] t = new double[1];
            Vector3d imp = FlightModel.predictImpact(start, new Vector3d(b.st.vel).add(0, -1.5, 0), b.bombType(), b.speedScale(), env, t);
            ccip = imp == null ? null : new Vec3(imp.x, imp.y, imp.z);
            ccipTime = (float) t[0];
            ccrp = b.target().map(tg -> b.ccrp(env, tg)).orElse(null);
        } else {
            ccip = null;
            ccrp = null;
        }
    }

    private static float ramp(float v, float target, float step) {
        return v + Mth.clamp(target - v, -step, step);
    }

    /** Designates the ground point under the view centre (bombsight or the pilot's look direction). */
    private static void designate(BomberEntity b) {
        Minecraft mc = Minecraft.getInstance();
        var cam = mc.gameRenderer.getMainCamera();
        Vec3 from = cam.getPosition();
        Vector3f look = cam.getLookVector();
        Vec3 to = from.add(look.x() * 3000, look.y() * 3000, look.z() * 3000);
        BlockHitResult hit = mc.level.clip(new ClipContext(from.add(look.x() * 12, look.y() * 12, look.z() * 12), to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, mc.player));
        if (hit.getType() == HitResult.Type.MISS) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.aiarsenal.no_ground"), true);
            return;
        }
        Vec3 p = hit.getLocation();
        ModNetwork.CHANNEL.sendToServer(new CrewActionPacket(b.getId(), BomberEntity.A_TARGET, 0, p.x, p.y, p.z));
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent e) {
        BomberEntity b = vehicle();
        AircraftData.Seat s = mySeat();
        if (b == null || s == null || Minecraft.getInstance().screen != null) return;
        if (sight) {
            zoom = Mth.clamp(zoom + (e.getScrollDelta() > 0 ? 1 : -1), 1, 4);
            e.setCanceled(true);
        } else if (s.canFly()) {
            throttle = Mth.clamp(throttle + (float) e.getScrollDelta() * 0.05f, 0, b.kind.maxThrottle());
            e.setCanceled(true);
        }
    }

    /** Camera follows the airframe attitude; third person sits far behind the 50 m aircraft; bombsight looks down. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        BomberEntity b = vehicle();
        if (b == null || mc.player == null) return;
        float pt = (float) e.getPartialTick();
        Quaternionf q = new Quaternionf(b.qPrev).slerp(b.qCur, pt);
        float bYaw = b.st.yawDeg();
        if (sight) {
            float yawOff = Mth.clamp(Mth.wrapDegrees(mc.player.getYRot() - bYaw), -25, 25);
            e.setYaw(bYaw + yawOff);
            e.setPitch(Mth.clamp(mc.player.getXRot(), 15, 90));
            e.setRoll(0);
            return;
        }
        float shake = ClientFx.shake();
        if (mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            float rel = Mth.wrapDegrees(mc.player.getViewYRot(pt) - Mth.rotLerp(pt, b.yRotO, b.getYRot()));
            Quaternionf look = new Quaternionf(q).rotateY((float) Math.toRadians(-rel)).rotateX((float) Math.toRadians(mc.player.getViewXRot(pt)));
            Vector3f eul = look.getEulerAnglesYXZ(new Vector3f());
            e.setYaw((float) -Math.toDegrees(eul.y));
            e.setPitch((float) Math.toDegrees(eul.x));
            e.setRoll((float) -Math.toDegrees(eul.z) + (shake > 0 ? (float) Math.sin(b.clientAge * 1.7) * shake * 0.3f : 0));
        } else {
            // chase camera: orbit the aircraft centre at 55 m
            Vector3f centre = q.transform(new Vector3f(0, 4.5f, -2f));
            Vec3 c = new Vec3(Mth.lerp(pt, b.xo, b.getX()) + centre.x, Mth.lerp(pt, b.yo, b.getY()) + centre.y, Mth.lerp(pt, b.zo, b.getZ()) + centre.z);
            float yaw = e.getYaw(), pitch = e.getPitch();
            boolean front = mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT;
            Vec3 dir = Vec3.directionFromRotation(pitch, yaw).scale(front ? 1 : -1);
            e.getCamera().setPosition(c.add(dir.scale(58)));
        }
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov e) {
        if (sight && vehicle() != null) e.setFOV(e.getFOV() / (zoom == 1 ? 2.0 : zoom == 2 ? 4.0 : zoom == 3 ? 8.0 : 14.0));
    }

    /** riders sit only in seats (walkers stand); everything of the own aircraft is hidden in the bombsight */
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> e) {
        if (e.getEntity().getVehicle() instanceof BomberEntity b) {
            if (sight && b == vehicle()) {
                e.setCanceled(true);
                return;
            }
            BomberEntity.RENDERING_RIDER = e.getEntity();
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> e) {
        BomberEntity.RENDERING_RIDER = null;
    }

    /** CCIP pipper and target marker drawn on the ground. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        BomberEntity b = vehicle();
        if (b == null || (ccip == null && b.target().isEmpty())) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        if (ccip != null) {
            float r = (float) Math.max(2.5, cam.distanceTo(ccip) * 0.012);
            ring(vc, m, ccip, r, 1f, 0.25f, 0.2f);
            ring(vc, m, ccip, r * 0.15f, 1f, 0.25f, 0.2f);
            line(vc, m, ccip.add(-r * 1.6, 0.2, 0), ccip.add(-r * 1.1, 0.2, 0), 1f, 0.25f, 0.2f);
            line(vc, m, ccip.add(r * 1.1, 0.2, 0), ccip.add(r * 1.6, 0.2, 0), 1f, 0.25f, 0.2f);
            line(vc, m, ccip.add(0, 0.2, -r * 1.6), ccip.add(0, 0.2, -r * 1.1), 1f, 0.25f, 0.2f);
            line(vc, m, ccip.add(0, 0.2, r * 1.1), ccip.add(0, 0.2, r * 1.6), 1f, 0.25f, 0.2f);
        }
        b.target().ifPresent(t -> {
            Vec3 tp = Vec3.atBottomCenterOf(t);
            float r = (float) Math.max(3, cam.distanceTo(tp) * 0.015);
            line(vc, m, tp.add(-r, 0.25, 0), tp.add(0, 0.25, r), 0.2f, 1f, 0.3f);
            line(vc, m, tp.add(0, 0.25, r), tp.add(r, 0.25, 0), 0.2f, 1f, 0.3f);
            line(vc, m, tp.add(r, 0.25, 0), tp.add(0, 0.25, -r), 0.2f, 1f, 0.3f);
            line(vc, m, tp.add(0, 0.25, -r), tp.add(-r, 0.25, 0), 0.2f, 1f, 0.3f);
            line(vc, m, tp, tp.add(0, r * 6, 0), 0.2f, 1f, 0.3f);
        });
        ps.popPose();
        buf.endBatch(RenderType.lines());
    }

    private static void ring(VertexConsumer vc, Matrix4f m, Vec3 c, float r, float cr, float cg, float cb) {
        int n = 32;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            line(vc, m, c.add(Math.cos(a0) * r, 0.2, Math.sin(a0) * r), c.add(Math.cos(a1) * r, 0.2, Math.sin(a1) * r), cr, cg, cb);
        }
    }

    private static void line(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float r, float g, float bl) {
        Vec3 n = b.subtract(a).normalize();
        vc.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(r, g, bl, 1f).normal((float) n.x, (float) n.y, (float) n.z).endVertex();
        vc.vertex(m, (float) b.x, (float) b.y, (float) b.z).color(r, g, bl, 1f).normal((float) n.x, (float) n.y, (float) n.z).endVertex();
    }

    /** keeps the player facing forward when boarding */
    @SubscribeEvent
    public static void onMount(net.minecraftforge.event.entity.EntityMountEvent e) {
        if (e.isMounting() && e.getLevel().isClientSide && e.getEntityBeingMounted() instanceof BomberEntity v
                && e.getEntityMounting() == Minecraft.getInstance().player) {
            e.getEntityMounting().setYRot(v.getYRot());
            e.getEntityMounting().setYHeadRot(v.getYRot());
        }
    }

    public static BlockPos dummy() {
        return BlockPos.ZERO;
    }
}
