package ru.aiforge.realwar.worldgen;

import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** r17: worldgen features (placed through data/aiarsenal/worldgen + forge/biome_modifier/add_r17_sites.json). */
public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, "aiarsenal");
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> WRECK_SITE = FEATURES.register("wreck_site",
            () -> new WreckSiteFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> SUPPLY_CACHE = FEATURES.register("supply_cache",
            () -> new SupplyCacheFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {
    }
}
