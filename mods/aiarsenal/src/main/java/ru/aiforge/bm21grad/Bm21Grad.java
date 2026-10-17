package ru.aiforge.bm21grad;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import ru.aiforge.bm21grad.network.ModNetwork;
import ru.aiforge.bm21grad.registry.ModRegistry;

/** BM-21 "Grad" MLRS with a GLB (glTF 2.0) model/animation engine. */
public class Bm21Grad {
    public static final String MODID = "aiarsenal";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** called from the single @Mod entry point {@code ru.aiforge.arsenal.AiArsenal} */
    public static void init(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        ModRegistry.register(bus);
        context.registerConfig(ModConfig.Type.SERVER, Config.SPEC, MODID + "-bm21-server.toml");
        bus.addListener(Bm21Grad::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(ModNetwork::register);
        LOGGER.info("BM-21 Grad loaded: {} rocket types", ru.aiforge.bm21grad.ammo.RocketType.values().length - 1);
    }
}
