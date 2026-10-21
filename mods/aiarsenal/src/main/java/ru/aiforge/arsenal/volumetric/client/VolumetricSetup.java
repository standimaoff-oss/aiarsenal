package ru.aiforge.arsenal.volumetric.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.arsenal.volumetric.VolumetricParticles;
import ru.aiforge.arsenal.volumetric.client.render.VolumeGlRenderer;

@Mod.EventBusSubscriber(modid = "aiarsenal", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VolumetricSetup {
    private VolumetricSetup() {}

    @SubscribeEvent public static void particles(RegisterParticleProvidersEvent event) {
        var types=java.util.List.of(VolumetricParticles.MUZZLE,VolumetricParticles.POWDER,VolumetricParticles.CANNON,VolumetricParticles.EXHAUST,VolumetricParticles.IMPACT);
        for(int i=0;i<types.size();i++) {
            final int profile=i+1;
            // Volumetric only: if the volume pass is in its brief retry window the puff is dropped,
            // never replaced by a vanilla particle.
            event.registerSpecial(types.get(i).get(),(type,level,x,y,z,vx,vy,vz)->{
                VolumetricEffects.profile(x,y,z,vx,vy,vz,profile);
                return null;
            });
        }
        event.registerSpecial(VolumetricParticles.SMOKE.get(), (type, level, x, y, z, vx, vy, vz) -> {
            VolumetricEffects.smoke(x, y, z, 1.6f, 5f);
            return null;
        });
    }

    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            if (RenderSystem.isOnRenderThread()) VolumeGlRenderer.reset();
            else RenderSystem.recordRenderCall(VolumeGlRenderer::reset);
        });
    }
}
