// Adapted from supplied VolPyro 1.5.6 (MIT), VolPyro Contributors. See THIRD_PARTY_NOTICES.md.
package ru.aiforge.arsenal.volumetric.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class VolPyroClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<QualityPreset> QUALITY_PRESET;
    public static final ForgeConfigSpec.IntValue RAYMARCH_STEPS;
    public static final ForgeConfigSpec.DoubleValue RESOLUTION_SCALE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VOLUMETRIC_RENDERER;

    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_VOLUMES, MAX_PLUME_SEGMENTS, MAX_RENDER_VOLUMES;

    private VolPyroClientConfig() {
    }

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("renderer");
        QUALITY_PRESET = builder.defineEnum("qualityPreset", QualityPreset.AUTO);
        RAYMARCH_STEPS = builder.defineInRange("raymarchSteps", 24, 12, 96);
        RESOLUTION_SCALE = builder.defineInRange("renderResolutionScale", 0.5, 0.25, 1.0);
        ENABLE_VOLUMETRIC_RENDERER = builder.define("enableVolumetricRenderer", true);
        MAX_ACTIVE_VOLUMES = builder.defineInRange("maxActiveVolumes", 32, 4, 64);
        MAX_PLUME_SEGMENTS = builder.defineInRange("maxPlumeSegments", 96, 8, 256);
        MAX_RENDER_VOLUMES = builder.defineInRange("maxRenderedVolumes", 24, 4, 64);
        builder.pop();
        SPEC = builder.build();
    }

    public static enum QualityPreset {
        AUTO,
        CI_SOFTWARE,
        MEDIUM,
        HIGH,
        ULTRA;

    }
}

