/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.logging.LogUtils
 *  net.minecraftforge.eventbus.api.IEventBus
 *  net.minecraftforge.fml.common.Mod
 *  net.minecraftforge.fml.config.IConfigSpec
 *  net.minecraftforge.fml.config.ModConfig$Type
 *  net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
 *  net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
 *  org.slf4j.Logger
 */
package ru.aiforge.realwar;

import com.mojang.logging.LogUtils;
import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.network.ModNetwork;
import ru.aiforge.realwar.registry.ModBlockEntities;
import ru.aiforge.realwar.registry.ModBlocks;
import ru.aiforge.realwar.registry.ModEffects;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.registry.ModS3;
import ru.aiforge.realwar.registry.ModSounds;
import ru.aiforge.realwar.registry.ModTabs;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

public class RealWar {
    public static final String MODID = "aiarsenal";   // merged into AI Arsenal (was "realwar")
    public static final Logger LOGGER = LogUtils.getLogger();

    private RealWar() {
    }

    /** Called from {@link ru.aiforge.arsenal.AiArsenal}: registers the Real War content inside the aiarsenal mod. */
    public static void init(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        ModS3.BLOCKS.register(bus);
        ModS3.ITEMS.register(bus);
        ModS3.BLOCK_ENTITIES.register(bus);
        ModS3.ENTITIES.register(bus);
        ModBlockEntities.BLOCK_ENTITIES.register(bus);
        ModEntities.ENTITIES.register(bus);
        ru.aiforge.realwar.craft.CraftRegistry.ENTITIES.register(bus);
        ru.aiforge.realwar.craft.CraftRegistry.ITEMS.register(bus);
        ModSounds.SOUNDS.register(bus);
        ModEffects.EFFECTS.register(bus);
        ModTabs.TABS.register(bus);
        ru.aiforge.realwar.worldgen.ModFeatures.FEATURES.register(bus);
        context.registerConfig(ModConfig.Type.COMMON, (IConfigSpec)RWConfig.COMMON_SPEC, "aiarsenal-realwar-common.toml");
        context.registerConfig(ModConfig.Type.CLIENT, (IConfigSpec)RWConfig.CLIENT_SPEC, "aiarsenal-realwar-client.toml");
        bus.addListener(RealWar::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    /** True for items registered by the Real War part of the merged mod (they share the "aiarsenal" namespace now). */
    public static boolean ownsItem(net.minecraft.resources.ResourceLocation id) {
        if (!MODID.equals(id.getNamespace())) {
            return false;
        }
        for (net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.Item> r : ru.aiforge.realwar.registry.ModItems.ITEMS.getEntries()) {
            if (r.getId().equals(id)) return true;
        }
        for (net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.Item> r : ru.aiforge.realwar.registry.ModS3.ITEMS.getEntries()) {
            if (r.getId().equals(id)) return true;
        }
        return false;
    }
}
