package ru.aiforge.bm21grad.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import ru.aiforge.bm21grad.Bm21Grad;
import ru.aiforge.bm21grad.client.render.Bm21Renderer;
import ru.aiforge.bm21grad.client.render.RocketRenderer;
import ru.aiforge.bm21grad.client.render.SubmunitionRenderer;
import ru.aiforge.bm21grad.registry.ModRegistry;

/** Client-only mod-bus registrations. */
@Mod.EventBusSubscriber(modid = Bm21Grad.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    public static final String CAT = "key.categories.aiarsenal";
    public static final KeyMapping KEY_FIRE = key("fire", GLFW.GLFW_KEY_G);
    public static final KeyMapping KEY_RELOAD = key("reload", GLFW.GLFW_KEY_R);
    public static final KeyMapping KEY_SHIELDS = key("shields", GLFW.GLFW_KEY_H);
    public static final KeyMapping KEY_FCS = key("fire_control", GLFW.GLFW_KEY_C);
    public static final KeyMapping KEY_LIGHTS = key("bm21_lights", GLFW.GLFW_KEY_K);
    public static final KeyMapping KEY_CAMERA = key("camera", GLFW.GLFW_KEY_V);
    public static final KeyMapping KEY_MODE = key("gunner", GLFW.GLFW_KEY_B);

    private ClientSetup() {
    }

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.aiarsenal." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CAT);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModRegistry.BM21.get(), Bm21Renderer::new);
        e.registerEntityRenderer(ModRegistry.ROCKET.get(), RocketRenderer::new);
        e.registerEntityRenderer(ModRegistry.SUBMUNITION.get(), SubmunitionRenderer::new);
        e.registerEntityRenderer(ModRegistry.SMOKE_SCREEN.get(), NoopRenderer::new);
    }

    @SubscribeEvent
    public static void particles(net.minecraftforge.client.event.RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ModRegistry.P_SMOKE_WALL.get(), sp -> new BigPuffParticle.Provider(sp, BigPuffParticle.Kind.SMOKE_WALL));
        e.registerSpriteSet(ModRegistry.P_FIREBALL.get(), sp -> new BigPuffParticle.Provider(sp, BigPuffParticle.Kind.FIREBALL));
        e.registerSpriteSet(ModRegistry.P_BLAST_SMOKE.get(), sp -> new BigPuffParticle.Provider(sp, BigPuffParticle.Kind.BLAST_SMOKE));
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        for (KeyMapping k : new KeyMapping[]{KEY_FIRE, KEY_RELOAD, KEY_SHIELDS, KEY_FCS, KEY_LIGHTS, KEY_CAMERA, KEY_MODE}) e.register(k);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "bm21_hud", Bm21Hud::render);
    }
}
