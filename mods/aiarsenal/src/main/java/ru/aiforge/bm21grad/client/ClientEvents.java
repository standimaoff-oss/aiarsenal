package ru.aiforge.bm21grad.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import ru.aiforge.bm21grad.Bm21Grad;
import ru.aiforge.bm21grad.client.gui.FireControlScreen;
import ru.aiforge.bm21grad.entity.Bm21Entity;
import ru.aiforge.bm21grad.network.ModNetwork;
import ru.aiforge.bm21grad.network.VehicleCommandPacket;

/** Client game-bus events: crew keys, per-vehicle animation ticks, delayed explosion sounds, camera. */
@Mod.EventBusSubscriber(modid = Bm21Grad.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final double[] CAMERA_DIST = {4, 10, 16};
    private static int cameraMode = 1;

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!mc.isPaused()) {
            ClientFx.tick();
            for (Entity en : mc.level.entitiesForRendering()) if (en instanceof Bm21Entity b) Bm21ClientState.of(b).tick(b);
        }
        Bm21Entity v = mc.player.getVehicle() instanceof Bm21Entity b ? b : null;
        if (v != null) GunnerControl.tick(mc, v);
        else GunnerControl.zoom = false;
        while (ClientSetup.KEY_MODE.consumeClick()) if (v != null) send(v, VehicleCommandPacket.GUNNER, 0);
        while (ClientSetup.KEY_FIRE.consumeClick()) {
            if (v == null) continue;
            if (v.isGunnerMode()) GunnerControl.fire(mc, v);
            else send(v, VehicleCommandPacket.FIRE, 0);
        }
        while (ClientSetup.KEY_RELOAD.consumeClick()) if (v != null) send(v, VehicleCommandPacket.RELOAD, 0);
        while (ClientSetup.KEY_SHIELDS.consumeClick()) if (v != null) send(v, VehicleCommandPacket.SHIELDS, 0);
        while (ClientSetup.KEY_LIGHTS.consumeClick()) if (v != null) send(v, VehicleCommandPacket.LIGHTS, 0);
        while (ClientSetup.KEY_CAMERA.consumeClick()) cameraMode = (cameraMode + 1) % CAMERA_DIST.length;
        while (ClientSetup.KEY_FCS.consumeClick()) if (v != null) mc.setScreen(new FireControlScreen(v));
    }

    public static void send(Bm21Entity v, int action, int value) {
        ModNetwork.CHANNEL.sendToServer(VehicleCommandPacket.of(v, action, value));
    }

    /** Face the direction of travel when climbing into the cab. */
    @SubscribeEvent
    public static void onMount(net.minecraftforge.event.entity.EntityMountEvent e) {
        if (e.isMounting() && e.getLevel().isClientSide && e.getEntityBeingMounted() instanceof Bm21Entity v
                && e.getEntityMounting() == Minecraft.getInstance().player) {
            e.getEntityMounting().setYRot(v.getYRot());
            e.getEntityMounting().setYHeadRot(v.getYRot());
            e.getEntityMounting().setXRot(8f);
        }
    }

    /** Crew sits inside the closed cab: hide riders of a BM-21 (their own first-person view is unaffected). */
    @SubscribeEvent
    public static void onRenderPlayer(net.minecraftforge.client.event.RenderPlayerEvent.Pre e) {
        if (e.getEntity().getVehicle() instanceof Bm21Entity) e.setCanceled(true);
    }

    /** Gunner mode: LMB fires at the reticle, RMB toggles the sight magnification. */
    @SubscribeEvent
    public static void onInteract(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (!GunnerControl.active(mc) || !(mc.player.getVehicle() instanceof Bm21Entity v)) return;
        if (e.isAttack()) {
            GunnerControl.fire(mc, v);
        } else if (e.isUseItem()) {
            GunnerControl.zoom = !GunnerControl.zoom;
        } else return;
        e.setSwingHand(false);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFov(net.minecraftforge.client.event.ComputeFovModifierEvent e) {
        if (GunnerControl.zoom && GunnerControl.active(Minecraft.getInstance())) e.setNewFovModifier(e.getFovModifier() * 0.25f);
    }

    /** No floating hand/item inside the cab or at the sight. */
    @SubscribeEvent
    public static void onHand(net.minecraftforge.client.event.RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof Bm21Entity) e.setCanceled(true);
    }

    /** The gunner reticle replaces the vanilla crosshair. */
    @SubscribeEvent
    public static void onOverlay(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof Bm21Entity)) return;
        var id = e.getOverlay().id();
        if (id.equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.id()) && GunnerControl.active(mc)) e.setCanceled(true);
        // in the cab the hotbar is useless; its place at the bottom is taken by the control hints
        if (id.equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.id())
                || id.equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.EXPERIENCE_BAR.id())
                || id.equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.ITEM_NAME.id())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        float s = ClientFx.shake();
        if (s > 0) {
            float t = (float) (e.getPartialTick() + (Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0));
            e.setPitch(e.getPitch() + (float) Math.sin(t * 2.9) * s * 0.5f);
            e.setYaw(e.getYaw() + (float) Math.cos(t * 3.7) * s * 0.35f);
            e.setRoll(e.getRoll() + (float) Math.sin(t * 5.3) * s * 0.4f);
        }
        Camera cam = e.getCamera();
        Entity ent = cam.getEntity();
        if (!cam.isDetached() && ent != null && ent.getVehicle() instanceof Bm21Entity gv && gv.isGunnerMode() && !gv.isDestroyed()) {
            cam.setPosition(GunnerControl.sightPos(gv, (float) e.getPartialTick()));
            return;
        }
        if (!cam.isDetached() || ent == null || !(ent.getVehicle() instanceof Bm21Entity)) return;
        double want = CAMERA_DIST[cameraMode];
        Vec3 eye = ent.getEyePosition((float) e.getPartialTick());
        Vec3 pos = cam.getPosition();
        double cur = pos.distanceTo(eye);
        if (cur < 3.9 || want <= cur) return; // vanilla distance was already clipped by a wall
        double extra = cam.getMaxZoom(want - cur);
        Vector3f look = cam.getLookVector();
        cam.setPosition(pos.subtract(look.x() * extra, look.y() * extra, look.z() * extra));
    }
}
