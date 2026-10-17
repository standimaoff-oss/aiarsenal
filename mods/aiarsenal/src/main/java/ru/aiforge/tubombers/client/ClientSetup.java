package ru.aiforge.tubombers.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
import ru.aiforge.tubombers.TuBombers;
import ru.aiforge.tubombers.client.render.BombRenderer;
import ru.aiforge.tubombers.client.render.BomberRenderer;
import ru.aiforge.tubombers.entity.BombEntity;
import ru.aiforge.tubombers.entity.BomberEntity;
import ru.aiforge.tubombers.entity.Crew;
import ru.aiforge.tubombers.registry.ModRegistry;

/** Client-only mod-bus registrations. */
@Mod.EventBusSubscriber(modid = TuBombers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    public static final String CAT = "key.categories.aiarsenal";
    public static final KeyMapping KEY_SEAT = key("seat", GLFW.GLFW_KEY_R);
    public static final KeyMapping KEY_GEAR = key("gear", GLFW.GLFW_KEY_G);
    public static final KeyMapping KEY_FLAPS = key("flaps", GLFW.GLFW_KEY_N);
    public static final KeyMapping KEY_BAY = key("bay", GLFW.GLFW_KEY_B);
    public static final KeyMapping KEY_SWEEP = key("sweep_sight", GLFW.GLFW_KEY_V);
    public static final KeyMapping KEY_RUDDER_L = key("rudder_left", GLFW.GLFW_KEY_Z);
    public static final KeyMapping KEY_RUDDER_R = key("rudder_right", GLFW.GLFW_KEY_C);
    public static final KeyMapping KEY_THR_UP = key("throttle_up", GLFW.GLFW_KEY_UP);
    public static final KeyMapping KEY_THR_DOWN = key("throttle_down", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping KEY_BRAKES = key("brakes", GLFW.GLFW_KEY_X);
    public static final KeyMapping KEY_MODE = key("release_mode", GLFW.GLFW_KEY_M);
    public static final KeyMapping KEY_TARGET = key("target", GLFW.GLFW_KEY_Y);
    public static final KeyMapping KEY_LOAD = key("load", GLFW.GLFW_KEY_J);
    public static final KeyMapping KEY_LIGHTS = key("lights", GLFW.GLFW_KEY_K);
    public static final KeyMapping KEY_ENGINES = key("engines", GLFW.GLFW_KEY_I);
    public static final KeyMapping KEY_HINTS = key("hints", GLFW.GLFW_KEY_H);
    public static final KeyMapping[] ALL = {KEY_SEAT, KEY_GEAR, KEY_FLAPS, KEY_BAY, KEY_SWEEP, KEY_RUDDER_L, KEY_RUDDER_R, KEY_THR_UP, KEY_THR_DOWN,
            KEY_BRAKES, KEY_MODE, KEY_TARGET, KEY_LOAD, KEY_LIGHTS, KEY_ENGINES, KEY_HINTS};

    private ClientSetup() {
    }

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.aiarsenal." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CAT);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        BomberEntity.CLIENT = new BomberEntity.ClientHooks() {
            @Override
            public void walk(BomberEntity b, Crew c, Entity passenger) {
                ClientCrew.walk(b, c, passenger);
            }

            @Override
            public void tick(BomberEntity b) {
                BomberSounds.ensure(b);
                BomberExhaust.tick(b);
            }

            @Override
            public boolean isLocal(Entity e) {
                return e == Minecraft.getInstance().player;
            }
        };
        BombEntity.BombSounds.STARTER = BomberSounds::whistle;
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModRegistry.TU95.get(), BomberRenderer::new);
        e.registerEntityRenderer(ModRegistry.TU160.get(), BomberRenderer::new);
        e.registerEntityRenderer(ModRegistry.BOMB.get(), BombRenderer::new);
    }

    @SubscribeEvent
    public static void particles(net.minecraftforge.client.event.RegisterParticleProvidersEvent e) {
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        for (KeyMapping k : ALL) e.register(k);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "bomber_hud", Hud::render);
    }
}
