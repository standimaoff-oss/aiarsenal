package ru.aiforge.arsenal.migration;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.MissingMappingsEvent;

/** Explicit old-save migration, NOT hidden registrations. Backup the world before upgrading.
 * Old ore becomes its host rock; inventory raw/ingot becomes a mundane iron equivalent. */
@Mod.EventBusSubscriber(modid = "aiarsenal")
public final class RemovedOres {
    private RemovedOres() {}
    @SubscribeEvent public static void missing(MissingMappingsEvent event) {
        for (var mapping : event.getMappings(Registries.BLOCK, "aiarsenal")) {
            switch (mapping.getKey().getPath()) {
                case "uranium_ore" -> mapping.remap(Blocks.STONE);
                case "deepslate_uranium_ore" -> mapping.remap(Blocks.DEEPSLATE);
            }
        }
        for (var mapping : event.getMappings(Registries.ITEM, "aiarsenal")) {
            switch (mapping.getKey().getPath()) {
                case "uranium_ore" -> mapping.remap(Items.STONE);
                case "deepslate_uranium_ore" -> mapping.remap(Items.DEEPSLATE);
                case "raw_uranium" -> mapping.remap(Items.IRON_NUGGET);
                case "uranium_ingot" -> mapping.remap(Items.IRON_INGOT);
            }
        }
    }
}
