package ru.aiforge.arsenal;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import ru.aiforge.arsenal.network.ArsenalNetwork;
import ru.aiforge.arsenal.registry.ArsenalRegistry;

/**
 * AI Arsenal: one mod with the BM-21 "Grad" MLRS, the Tu-95MS / Tu-160 strategic bombers, reconnaissance and
 * strike drones, cruise missiles and nuclear weapons with remote detonation, plus the merged Real War content
 * (missiles and launch pads, radars, S-400, ZU-23, Smerch, the nuclear fuel chain, radiation).
 */
@Mod(AiArsenal.MODID)
public class AiArsenal {
    public static final String MODID = "aiarsenal";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AiArsenal(FMLJavaModLoadingContext context) {
        ru.aiforge.bm21grad.Bm21Grad.init(context);
        ru.aiforge.tubombers.TuBombers.init(context);
        ru.aiforge.realwar.RealWar.init(context);
        IEventBus bus = context.getModEventBus();
        ArsenalRegistry.register(bus);
        ru.aiforge.arsenal.volumetric.VolumetricParticles.TYPES.register(bus);
        context.registerConfig(ModConfig.Type.CLIENT, ru.aiforge.arsenal.volumetric.config.VolPyroClientConfig.SPEC, "aiarsenal-volumetric-client.toml");
        context.registerConfig(ModConfig.Type.SERVER, ArsenalConfig.SPEC, MODID + "-arsenal-server.toml");
        bus.addListener(AiArsenal::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(ArsenalNetwork::register);
        LOGGER.info("AI Arsenal loaded");
    }
}
