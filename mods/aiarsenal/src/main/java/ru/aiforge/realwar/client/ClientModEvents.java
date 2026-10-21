/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.KeyMapping
 *  net.minecraft.client.gui.screens.MenuScreens
 *  net.minecraft.client.model.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.client.renderer.entity.LivingEntityRenderer
 *  net.minecraft.client.renderer.entity.RenderLayerParent
 *  net.minecraft.client.renderer.entity.layers.RenderLayer
 *  net.minecraft.client.renderer.entity.player.PlayerRenderer
 *  net.minecraft.server.packs.resources.PreparableReloadListener
 *  net.minecraft.server.packs.resources.ResourceManagerReloadListener
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.inventory.MenuType
 *  net.minecraft.world.level.block.entity.BlockEntityType
 *  net.minecraftforge.api.distmarker.Dist
 *  net.minecraftforge.client.event.EntityRenderersEvent$AddLayers
 *  net.minecraftforge.client.event.EntityRenderersEvent$RegisterRenderers
 *  net.minecraftforge.client.event.RegisterClientReloadListenersEvent
 *  net.minecraftforge.client.event.RegisterGuiOverlaysEvent
 *  net.minecraftforge.client.event.RegisterKeyMappingsEvent
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber$Bus
 *  net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
 */
package ru.aiforge.realwar.client;

import ru.aiforge.realwar.client.AircraftClient;
import ru.aiforge.realwar.client.ClientRadiation;
import ru.aiforge.realwar.client.VehicleClient;
import ru.aiforge.realwar.client.VehicleHud;
import ru.aiforge.realwar.client.fx.ClientBlastManager;
import ru.aiforge.realwar.client.render.AirBombRenderer;
import ru.aiforge.realwar.client.render.AircraftRenderer;
import ru.aiforge.realwar.client.render.BombRenderer;
import ru.aiforge.realwar.client.render.FallingBombRenderer;
import ru.aiforge.realwar.client.render.GlbAssets;
import ru.aiforge.realwar.client.render.HazmatLayer;
import ru.aiforge.realwar.client.render.LaunchPadRenderer;
import ru.aiforge.realwar.client.render.MachineGlbRenderers;
import ru.aiforge.realwar.client.render.MissileRenderer;
import ru.aiforge.realwar.client.render.RocketRenderer;
import ru.aiforge.realwar.client.render.VehicleRenderer;
import ru.aiforge.realwar.client.sight.SightOverlay;
import ru.aiforge.realwar.registry.ModBlockEntities;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.registry.ModS3;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid="aiarsenal", bus=Mod.EventBusSubscriber.Bus.MOD, value={Dist.CLIENT})
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            VehicleClient.install();
            AircraftClient.install();
            ru.aiforge.realwar.client.craft.CraftClient.install();
        });
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.AddLayers e) {
        for (String skin : new String[]{"default", "slim"}) {
            LivingEntityRenderer r = e.getSkin(skin);
            if (!(r instanceof PlayerRenderer)) continue;
            PlayerRenderer pr = (PlayerRenderer)r;
            pr.addLayer((RenderLayer)new HazmatLayer((RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>)pr));
            pr.addLayer((RenderLayer)new ru.aiforge.realwar.client.render.UniformLayer((RenderLayerParent)pr));
        }
        // r15: uniforms also show on armour stands and humanoid zombies (display / NPC use)
        for (net.minecraft.world.entity.EntityType<?> t : new net.minecraft.world.entity.EntityType<?>[]{
                net.minecraft.world.entity.EntityType.ARMOR_STAND, net.minecraft.world.entity.EntityType.ZOMBIE,
                net.minecraft.world.entity.EntityType.HUSK, net.minecraft.world.entity.EntityType.DROWNED}) {
            Object r = e.getRenderer((net.minecraft.world.entity.EntityType)t);
            if (r instanceof LivingEntityRenderer lr && lr.getModel() instanceof net.minecraft.client.model.HumanoidModel)
                lr.addLayer(new ru.aiforge.realwar.client.render.UniformLayer((RenderLayerParent)lr));
        }
    }

    @SubscribeEvent
    public static void layerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(ru.aiforge.realwar.soldier.client.SoldierModel.LAYER,
                ru.aiforge.realwar.soldier.client.SoldierModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.CANNON_FR6.get(), ru.aiforge.realwar.client.render.PowderCannonRenderer::new);
        e.registerEntityRenderer(ModEntities.CANNON_RI12.get(), ru.aiforge.realwar.client.render.PowderCannonRenderer::new);
        e.registerEntityRenderer(ModEntities.CANNONBALL.get(), ru.aiforge.realwar.client.render.CannonballRenderer::new);
        e.registerBlockEntityRenderer((BlockEntityType)ModBlockEntities.BOMB.get(), BombRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.FALLING_BOMB.get(), FallingBombRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.AT_MISSILE.get(), ru.aiforge.realwar.client.render.AtMissileRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.GRAD.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.SMERCH.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.S400.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.ZU23.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.SOLDIER.get(), ru.aiforge.realwar.soldier.client.SoldierRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.RADAR_MOBILE.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.RADAR_POST.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.YARS.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.T90M.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.ABRAMS.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.BMP3.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.MSTA.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.D30.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.M777.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.PZ2.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.PZ4.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.TIGER.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.STURMTIGER.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.MAUS.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.RATTE.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.T34.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.KV2.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.IS2.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.KV5.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.BM13.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.BRADLEY.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.TOS1A.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.URAL.get(), VehicleRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.MARK_I.get(), VehicleRenderer::new);
        e.registerEntityRenderer(ModEntities.WWI_GRENADE.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        e.registerEntityRenderer(ru.aiforge.realwar.craft.CraftRegistry.KA52.get(), ru.aiforge.realwar.client.craft.CraftRenderer::new);
        e.registerEntityRenderer(ru.aiforge.realwar.craft.CraftRegistry.AH64.get(), ru.aiforge.realwar.client.craft.CraftRenderer::new);
        e.registerEntityRenderer(ru.aiforge.realwar.craft.CraftRegistry.RAPTOR.get(), ru.aiforge.realwar.client.craft.CraftRenderer::new);
        e.registerEntityRenderer(ru.aiforge.realwar.craft.CraftRegistry.CB90.get(), ru.aiforge.realwar.client.craft.CraftRenderer::new);
        e.registerEntityRenderer(ru.aiforge.realwar.craft.CraftRegistry.SUB636.get(), ru.aiforge.realwar.client.craft.CraftRenderer::new);
        e.registerEntityRenderer(ru.aiforge.realwar.craft.CraftRegistry.BISMARCK.get(), ru.aiforge.realwar.client.craft.CraftRenderer::new);
        e.registerEntityRenderer(ru.aiforge.realwar.craft.CraftRegistry.BF109.get(), ru.aiforge.realwar.client.craft.CraftRenderer::new);
        e.registerEntityRenderer(ru.aiforge.realwar.craft.CraftRegistry.YAK3.get(), ru.aiforge.realwar.client.craft.CraftRenderer::new);
        e.registerEntityRenderer(ru.aiforge.realwar.craft.CraftRegistry.HE111.get(), ru.aiforge.realwar.client.craft.CraftRenderer::new);
        e.registerEntityRenderer(ru.aiforge.realwar.craft.CraftRegistry.PE8.get(), ru.aiforge.realwar.client.craft.CraftRenderer::new);
        e.registerEntityRenderer(ru.aiforge.realwar.craft.CraftRegistry.SU57.get(), ru.aiforge.realwar.client.craft.CraftRenderer::new);
        e.registerEntityRenderer(ru.aiforge.realwar.craft.CraftRegistry.ARADO.get(), ru.aiforge.realwar.client.craft.AradoRenderer::new);
        e.registerEntityRenderer(ru.aiforge.realwar.craft.CraftRegistry.ORDNANCE.get(), ru.aiforge.realwar.client.craft.CraftOrdnanceRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.SHELL.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        e.registerEntityRenderer(ModEntities.DEBRIS.get(), ru.aiforge.realwar.client.render.DebrisRenderer::new);
        e.registerEntityRenderer(ModEntities.STRIKE_PLANE.get(), ru.aiforge.realwar.client.strike.StrikePlaneRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.ROCKET.get(), RocketRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.TU95.get(), AircraftRenderer::new);
        e.registerEntityRenderer((EntityType)ModEntities.AIR_BOMB.get(), AirBombRenderer::new);
        e.registerEntityRenderer((EntityType)ModS3.MISSILE.get(), MissileRenderer::new);
        e.registerBlockEntityRenderer((BlockEntityType)ModS3.PAD_BE.get(), LaunchPadRenderer::new);
        e.registerBlockEntityRenderer((BlockEntityType)ModS3.COMPUTER_BE.get(), MachineGlbRenderers.Computer::new);
        e.registerBlockEntityRenderer((BlockEntityType)ModS3.SIREN_BE.get(), MachineGlbRenderers.Siren::new);
        e.registerEntityRenderer((EntityType)ModEntities.SAM_MISSILE.get(), RocketRenderer::new);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("geiger", ClientRadiation::renderHud);
        e.registerAboveAll("sight", SightOverlay::render);
        e.registerAboveAll("vehicle", VehicleHud::render);
        e.registerAboveAll("craft", ru.aiforge.realwar.client.craft.CraftHud::render);
        e.registerAboveAll("impact", ImpactReticle::render);
        e.registerAboveAll("hitcam", HitCam::render);
        e.registerAboveAll("handheld", HandheldClient::render);
        e.registerAboveAll("gun", GunClient::render);
        e.registerAboveAll("flash", (gui, g, partial, w, h) -> {
            float a = ClientBlastManager.flashAlpha(partial);
            if (a <= 0.0f) {
                return;
            }
            int alpha = Math.min(255, Math.round(a * 255.0f));
            g.fill(0, 0, w, h, alpha << 24 | 0xFFFAF0);
        });
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(WeaponMotionClient.SLOW);
        e.register(VehicleClient.KEY_RELOAD);
        e.register(VehicleClient.KEY_LOCK);
        e.register(VehicleClient.KEY_ENGINE);
        e.register(VehicleClient.KEY_SEAT);
        e.register(VehicleClient.KEY_SIGHT);
        e.register(VehicleClient.KEY_OPTICS);
        e.register(VehicleClient.KEY_MAP);
        e.register(VehicleClient.KEY_HINTS);
        e.register(ru.aiforge.realwar.client.craft.CraftClient.KEY_FREELOOK);
        for (KeyMapping k : new KeyMapping[]{AircraftClient.KEY_GEAR, AircraftClient.KEY_FLAPS, AircraftClient.KEY_BAY, AircraftClient.KEY_BOMBSEL, AircraftClient.KEY_BRAKE, AircraftClient.KEY_PITCH_DOWN, AircraftClient.KEY_RUDDER_L, AircraftClient.KEY_RUDDER_R}) {
            e.register(k);
        }
    }

    @SubscribeEvent
    public static void reloadListeners(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((PreparableReloadListener)((ResourceManagerReloadListener)rm -> {
            GlbAssets.clear();
            ru.aiforge.realwar.client.render.HandheldView.clear();
            ru.aiforge.realwar.client.render.GunRenderer.clear();
        }));
    }
}

