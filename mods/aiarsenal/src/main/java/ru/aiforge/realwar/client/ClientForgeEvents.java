/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.model.EntityModel
 *  net.minecraft.client.model.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.item.Item
 *  net.minecraftforge.api.distmarker.Dist
 *  net.minecraftforge.client.event.ClientPlayerNetworkEvent$LoggingOut
 *  net.minecraftforge.client.event.InputEvent$InteractionKeyMappingTriggered
 *  net.minecraftforge.client.event.RenderGuiOverlayEvent$Pre
 *  net.minecraftforge.client.event.RenderHandEvent
 *  net.minecraftforge.client.event.RenderLevelStageEvent
 *  net.minecraftforge.client.event.RenderLevelStageEvent$Stage
 *  net.minecraftforge.client.event.RenderLivingEvent$Pre
 *  net.minecraftforge.client.event.ViewportEvent$ComputeCameraAngles
 *  net.minecraftforge.client.event.ViewportEvent$ComputeFov
 *  net.minecraftforge.client.gui.overlay.VanillaGuiOverlay
 *  net.minecraftforge.event.TickEvent$ClientTickEvent
 *  net.minecraftforge.event.TickEvent$Phase
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber
 */
package ru.aiforge.realwar.client;

import ru.aiforge.realwar.client.ClientRadiation;
import ru.aiforge.realwar.client.VehicleClient;
import ru.aiforge.realwar.client.fx.ClientBlastManager;
import ru.aiforge.realwar.client.fx.TrailFx;
import ru.aiforge.realwar.client.particle.FxSystem;
import ru.aiforge.realwar.client.sight.MapCache;
import ru.aiforge.realwar.client.sight.SightClient;
import ru.aiforge.realwar.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="aiarsenal", value={Dist.CLIENT})
public final class ClientForgeEvents {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void resetWeaponArmScale(net.minecraftforge.client.event.RenderPlayerEvent.Pre e) {
        e.getRenderer().getModel().rightArm.yScale = 1f;
        e.getRenderer().getModel().leftArm.yScale = 1f;
        e.getRenderer().getModel().rightSleeve.yScale = 1f;
        e.getRenderer().getModel().leftSleeve.yScale = 1f;
        var m = e.getRenderer().getModel(); var p = e.getEntity();
        // Player skin jacket/trouser overlays can otherwise protrude through the garment.
        if (p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem() instanceof ru.aiforge.realwar.item.UniformItem) {
            m.jacket.visible = m.rightSleeve.visible = m.leftSleeve.visible = false;
        }
        if (p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).getItem() instanceof ru.aiforge.realwar.item.UniformItem
                || p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).getItem() instanceof ru.aiforge.realwar.item.UniformItem)
            m.rightPants.visible = m.leftPants.visible = false;
        if (p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem() instanceof ru.aiforge.realwar.item.UniformItem)
            m.hat.visible = false;
    }

    private ClientForgeEvents() {
    }

    /** r20: number keys 1..9 select the vehicle's weapon slot - consumed BEFORE vanilla changes the hotbar slot. */
    @SubscribeEvent
    public static void clientTickStart(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        net.minecraft.world.entity.Entity v = mc.player.getVehicle();
        while (VehicleClient.KEY_HINTS.consumeClick()) {
            if (v != null) ActionBar.hidden = !ActionBar.hidden;
        }
        if (!(v instanceof ru.aiforge.realwar.vehicle.VehicleEntity || v instanceof ru.aiforge.realwar.craft.CraftEntity
                || v instanceof ru.aiforge.realwar.aircraft.AircraftEntity)) return;
        for (int i = 0; i < 9; i++) {
            while (mc.options.keyHotbarSlots[i].consumeClick()) {
                if (mc.screen == null) ru.aiforge.realwar.network.ModNetwork.CHANNEL.sendToServer(new ru.aiforge.realwar.network.SelectWeaponPacket(v.getId(), i));
            }
        }
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ClientBlastManager.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        ClientBlastManager.tick(mc.level);
        TrailFx.tick(mc.level);
        ru.aiforge.realwar.client.fx.TrackMarks.tick();
        if (!ru.aiforge.realwar.client.craft.CraftClient.clientTick(mc)) VehicleClient.clientTick(mc);
        SightClient.tick(mc);
        ru.aiforge.realwar.client.sight.Optics.tick(mc);
        MapCache.tick(mc);
        ClientRadiation.tick(mc);
    }

    /** r15: crews of tanks, trucks and launchers sit INSIDE - no player model sticking out of the hull. Open mounts
     *  (Flak) and aircraft / boat crews stay visible. */
    @SubscribeEvent
    public static void hideCrewInside(RenderLivingEvent.Pre<?, ?> e) {
        if (e.getEntity() instanceof AbstractClientPlayer pl && pl.getVehicle() instanceof ru.aiforge.realwar.vehicle.LauncherVehicle) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void hideSkinUnderSuit(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity livingEntity = e.getEntity();
        if (!(livingEntity instanceof AbstractClientPlayer)) {
            return;
        }
        AbstractClientPlayer pl = (AbstractClientPlayer)livingEntity;
        EntityModel entityModel = e.getRenderer().getModel();
        if (!(entityModel instanceof PlayerModel)) {
            return;
        }
        PlayerModel m = (PlayerModel)entityModel;
        if (pl.getItemBySlot(EquipmentSlot.HEAD).is((Item)ModItems.HAZMAT_HELMET.get())) {
            m.head.visible = false;
            m.hat.visible = false;
        }
        if (pl.getItemBySlot(EquipmentSlot.CHEST).is((Item)ModItems.HAZMAT_CHESTPLATE.get())) {
            m.body.visible = false;
            m.jacket.visible = false;
            m.leftArm.visible = false;
            m.rightArm.visible = false;
            m.leftSleeve.visible = false;
            m.rightSleeve.visible = false;
        }
        if (pl.getItemBySlot(EquipmentSlot.LEGS).is((Item)ModItems.HAZMAT_LEGGINGS.get()) && pl.getItemBySlot(EquipmentSlot.FEET).is((Item)ModItems.HAZMAT_BOOTS.get())) {
            m.leftLeg.visible = false;
            m.rightLeg.visible = false;
            m.leftPants.visible = false;
            m.rightPants.visible = false;
        }
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        SightClient.onFov(e);
        ru.aiforge.realwar.client.craft.CraftClient.onFov(e);
    }

    @SubscribeEvent
    public static void hideCrosshair(RenderGuiOverlayEvent.Pre e) {
        Minecraft mcO = Minecraft.getInstance();
        if (mcO.player != null && (mcO.player.getVehicle() instanceof ru.aiforge.realwar.vehicle.VehicleEntity || mcO.player.getVehicle() instanceof ru.aiforge.realwar.craft.CraftEntity) && ru.aiforge.realwar.RWConfig.CLIENT.vehicleHud.get()) {
            // r15: War Thunder HUD - no hotbar / hearts / food / xp over the vehicle card and slot bar (health is on the card)
            net.minecraft.resources.ResourceLocation id = e.getOverlay().id();
            if (id.equals(VanillaGuiOverlay.HOTBAR.id()) || id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                    || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id()) || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id()) || id.equals(VanillaGuiOverlay.AIR_LEVEL.id())
                    || id.equals(VanillaGuiOverlay.MOUNT_HEALTH.id()) || id.equals(VanillaGuiOverlay.JUMP_BAR.id()) || id.equals(VanillaGuiOverlay.ITEM_NAME.id())) {
                e.setCanceled(true);
                return;
            }
        }
        if ((SightClient.active() || ImpactReticle.active() || GunClient.hideCrosshair() || ru.aiforge.realwar.client.craft.CraftClient.riding() != null) && e.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void uniformArm(net.minecraftforge.client.event.RenderArmEvent e) {
        ru.aiforge.realwar.client.render.UniformLayer.onRenderArm(e);
    }

    @SubscribeEvent
    public static void hideHand(RenderHandEvent e) {
        Minecraft mcH = Minecraft.getInstance();
        if (SightClient.active() || ru.aiforge.realwar.client.craft.CraftClient.hideHand()
                || mcH.player != null && mcH.player.getVehicle() instanceof ru.aiforge.realwar.vehicle.LauncherVehicle) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        ClientBlastManager.applyShake(e);
        VehicleClient.onCamera(e);
        ru.aiforge.realwar.client.craft.CraftClient.onCamera(e);
    }

    @SubscribeEvent
    public static void renderLevel(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            FxSystem.render(e.getPoseStack(), e.getCamera(), e.getPartialTick(), e.getFrustum());
            ru.aiforge.realwar.client.sight.Optics.renderHeat(e.getPoseStack(), e.getCamera(), e.getPartialTick());   // r21: thermal signatures
        }
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            TrenchPreview.render(e);                                         // r13: two-point trench x-ray preview
            ru.aiforge.realwar.client.fx.TrackMarks.render(e.getPoseStack(), e.getCamera());   // r21: mud / snow ruts
        }
    }

    @SubscribeEvent
    public static void interaction(InputEvent.InteractionKeyMappingTriggered e) {
        if (GunClient.onInteraction(e)) return;
        if (!ru.aiforge.realwar.client.craft.CraftClient.onInteraction(e)) VehicleClient.onInteraction(e);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientBlastManager.clear();
        ClientRadiation.reset();
        ru.aiforge.realwar.client.fx.TrackMarks.clear();
        VehicleClient.reset();
        ru.aiforge.realwar.client.craft.CraftFx.reset();
        SightClient.off();
        MapCache.clear();
        WtHud.clearMap();
    }
}

