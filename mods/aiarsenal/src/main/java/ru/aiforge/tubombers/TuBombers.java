package ru.aiforge.tubombers;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import ru.aiforge.tubombers.network.ModNetwork;
import ru.aiforge.tubombers.registry.ModRegistry;

/** Tu-95MS and Tu-160 strategic bombers: GLB models, walkable crew compartments, flight model, bombs. */
public class TuBombers {
    public static final String MODID = "aiarsenal";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** called from the single @Mod entry point {@code ru.aiforge.arsenal.AiArsenal} */
    public static void init(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        ModRegistry.register(bus);
        context.registerConfig(ModConfig.Type.SERVER, Config.SPEC, MODID + "-aviation-server.toml");
        bus.addListener(TuBombers::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(ModNetwork::register);
        LOGGER.info("Tu Bombers loaded");
    }
}
