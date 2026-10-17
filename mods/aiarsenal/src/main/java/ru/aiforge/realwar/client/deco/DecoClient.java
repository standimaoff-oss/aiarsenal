package ru.aiforge.realwar.client.deco;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.realwar.registry.ModBlockEntities;

/** r14: client registration for flags, beacons and the searchlight. */
@Mod.EventBusSubscriber(modid = "aiarsenal", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DecoClient {
    private DecoClient() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(ModBlockEntities.FLAG.get(), FlagRenderer::new);
        e.registerBlockEntityRenderer(ModBlockEntities.BEACON_LAMP.get(), BeaconLampRenderer::new);
        e.registerBlockEntityRenderer(ModBlockEntities.SEARCHLIGHT.get(), SearchlightRenderer::new);
        e.registerBlockEntityRenderer(ModBlockEntities.ANIM_DOOR.get(), AnimDoorRenderer::new);
        e.registerBlockEntityRenderer(ModBlockEntities.GATE.get(), GateRenderer::new);
        e.registerBlockEntityRenderer(ModBlockEntities.CRATE.get(), CrateRenderer::new);
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional e) {
        for (ResourceLocation rl : DecoModels.ALL) e.register(rl);
    }

    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((PreparableReloadListener) (ResourceManagerReloadListener) rm -> FlagRenderer.clear());
    }
}
