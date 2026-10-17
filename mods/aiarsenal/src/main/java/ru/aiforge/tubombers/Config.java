package ru.aiforge.tubombers;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-side gameplay configuration ({@code serverconfig/tubombers-server.toml}). */
public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue SPEED_SCALE;
    public static final ForgeConfigSpec.BooleanValue BLOCK_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue EXPLOSION_SCALE;
    public static final ForgeConfigSpec.BooleanValue DEBRIS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("flight");
        SPEED_SCALE = b.comment("World distance covered per real metre of flight. The flight model is computed in real units",
                        "(800 km/h cruise); 0.5 moves the aircraft at half speed over the map so terrain can load in time.",
                        "1.0 = true scale (needs a pre-generated world).")
                .defineInRange("speedScale", 0.5, 0.1, 1.0);
        b.pop();
        b.push("effects");
        BLOCK_DAMAGE = b.comment("Bombs and crashes break blocks (craters).").define("blockDamage", true);
        EXPLOSION_SCALE = b.defineInRange("explosionScale", 1.0, 0.1, 3.0);
        DEBRIS = b.comment("Explosions throw falling-block ejecta.").define("debris", true);
        b.pop();
        SPEC = b.build();
    }

    private Config() {
    }
}
