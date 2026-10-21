package ru.aiforge.arsenal.volumetric.client;

import com.mojang.brigadier.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.arsenal.AiArsenal;
import ru.aiforge.arsenal.volumetric.client.render.VolumeGlRenderer;
import ru.aiforge.arsenal.volumetric.client.sim.VolumetricFxManager;

/** One bounded, client-only adapter for all Arsenal visual emitters. Damage, radiation,
 * ballistics and server explosions remain owned by the existing gameplay systems. */
@Mod.EventBusSubscriber(modid = "aiarsenal", value = Dist.CLIENT)
public final class VolumetricEffects {
    private static ClientLevel world;
    private VolumetricEffects() {}

    private static void checkWorld() {
        ClientLevel next = Minecraft.getInstance().level;
        if (world != next) {
            VolumetricFxManager.clear();
            VolumeGlRenderer.reset();
            world = next;
        }
    }

    public static boolean active() {
        checkWorld();
        return world != null && VolumeGlRenderer.available();
    }

    public static boolean smoke(double x, double y, double z, float radius, float seconds) {
        if (!active()) return false;
        VolumetricFxManager.smokePuff(new Vec3(x, y, z), radius, seconds, 290f);
        return true;
    }

    public static boolean profile(double x,double y,double z,double vx,double vy,double vz,int profile) {
        if (!active()) return false;
        VolumetricFxManager.arsenalSmoke(new Vec3(x,y,z),new Vec3(vx,vy,vz),profile);
        if(Boolean.getBoolean("aiarsenal.visualQa")) AiArsenal.LOGGER.info("FX EMITTER profile={} pos={},{},{}",profile,x,y,z);
        return true;
    }

    public static boolean blast(double x, double y, double z, float radius, long seed) {
        if (!active() || !Float.isFinite(radius)) return false;
        VolumetricFxManager.arsenalBlast(new Vec3(x, y, z), Math.max(1f, Math.min(radius, 30f)), seed);
        if(Boolean.getBoolean("aiarsenal.visualQa")) AiArsenal.LOGGER.info("FX ARSENAL BLAST pos={},{},{} radius={}",x,y,z,radius);
        return true;
    }

    public static boolean nuclear(double x, double y, double z, float radius, long seed) {
        if (!active() || !Float.isFinite(radius)) return false;
        VolumetricFxManager.addNuke(new Vec3(x, y, z), Math.max(4f, Math.min(radius, 180f)), 120f, seed, 0);
        return true;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        checkWorld();
        if (world == null) return;
        VolumetricFxManager.tick(world);   // simulation never stops; only the GL pass may be in its retry window
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        checkWorld();
        VolumeGlRenderer.render(event);
    }

    /** Client-only, non-destructive QA commands. Also useful for checking GPU compatibility.
     * Does not spawn a bomb or modify terrain; real explosion packets are tested separately. */
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        var root = Commands.literal("arsenal_fx");
        for (String effect : new String[]{"smoke", "blast", "nuke", "trail", "clear", "status", "budget", "unpack", "off", "on"}) {
            if (effect.equals("unpack") && !Boolean.getBoolean("aiarsenal.visualQa")) continue;
            root.then(Commands.literal(effect).executes(context -> {
                String status = runVisualQa(effect);
                context.getSource().sendSuccess(() -> Component.literal(status), false);
                return Command.SINGLE_SUCCESS;
            }));
        }
        event.getDispatcher().register(root);
    }

    /** Shared by the local command and the explicitly opt-in automation bridge. */
    public static String runVisualQa(String effect) {
        if (effect.startsWith("view_")) return ru.aiforge.realwar.client.render.WeaponViewQa.run(effect);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "No player";
        Vec3 p = mc.player.position().add(mc.player.getLookAngle().scale(effect.equals("nuke") ? 100 : 18));
        if (effect.equals("off") || effect.equals("on")) {
            ru.aiforge.arsenal.volumetric.config.VolPyroClientConfig.ENABLE_VOLUMETRIC_RENDERER.set(effect.equals("on"));
            VolumetricFxManager.clear();
        }
        if (effect.equals("unpack")) VolumeGlRenderer.verifyPixelUnpackForQa();
        if (effect.equals("budget")) {
            for (int i = 0; i < 200; i++) {
                VolumetricFxManager.smokePuff(p.add(i, 0, 0), 1, 5, 290);
                VolumetricFxManager.recordRocket(i, p, new Vec3(0.5, 1, 0));
                VolumetricFxManager.recordRocket(i, p.add(1, 1, 0), new Vec3(0.5, 1, 0));
            }
            int volumes = VolumetricFxManager.volumeCount(), plumes = VolumetricFxManager.plumeCount();
            if (volumes > ru.aiforge.arsenal.volumetric.config.VolPyroClientConfig.MAX_ACTIVE_VOLUMES.get() + 8
                    || plumes > ru.aiforge.arsenal.volumetric.config.VolPyroClientConfig.MAX_PLUME_SEGMENTS.get())
                throw new IllegalStateException("Volumetric budget exceeded");
            AiArsenal.LOGGER.info("FX BUDGET PASS volumes={} plumes={}", volumes, plumes);
            VolumetricFxManager.clear();
        }
        if (effect.equals("clear")) VolumetricFxManager.clear();
        else if (effect.equals("smoke")) smoke(p.x, p.y + 1, p.z, 4, 15);
        else if (effect.equals("blast")) blast(p.x, p.y, p.z, 6, 27);
        else if (effect.equals("nuke")) nuclear(p.x, p.y, p.z, 20, 27);
        else if (effect.equals("trail") && active()) {
            for (int i = 0; i < 20; i++)
                VolumetricFxManager.recordRocket(-27, p.add(i * 0.5, i * 0.7, 0), new Vec3(0.5, 0.7, 0), 1);
        }
        String target = mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                ? net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(hit.getEntity().getType()).toString()
                : mc.hitResult == null ? "none" : mc.hitResult.getType().toString();
        String status = String.format(java.util.Locale.ROOT,
            "Arsenal VolPyro: active=%s volumes=%d plumes=%d frames=%d draw=%.2fms tick=%.3fms renderer=%s world=%s target=%s safeParticles=%d safeCloudFrames=%d",
            active(), VolumetricFxManager.volumeCount(), VolumetricFxManager.plumeCount(),
            VolumeGlRenderer.frameCounter(), VolumeGlRenderer.lastDrawNanos() / 1e6,
            VolumetricFxManager.lastTickMs(), VolumeGlRenderer.rendererName(), mc.level.dimension().location(), target,
            ru.aiforge.realwar.client.particle.FxSystem.shaderSafeParticles(),
            ru.aiforge.arsenal.client.NukeFx.shaderSafeFrames());
        AiArsenal.LOGGER.info("FX QA {}: {}", effect, status);
        return status;
    }

}
