package ru.aiforge.arsenal;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server config for drones, cruise missiles and nuclear weapons. */
public final class ArsenalConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue NUKE_BLOCK_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue NUKE_RADIUS_SCALE;
    public static final ForgeConfigSpec.IntValue NUKE_COLUMNS_PER_TICK;
    public static final ForgeConfigSpec.IntValue FALLOUT_SECONDS;
    public static final ForgeConfigSpec.BooleanValue HE_BLOCK_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue SPEED_SCALE;
    public static final ForgeConfigSpec.BooleanValue REALWAR_NUKE;
    public static final ForgeConfigSpec.BooleanValue REALWAR_HE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("nuclear");
        NUKE_BLOCK_DAMAGE = b.comment("Nuclear detonations destroy blocks").define("blockDamage", true);
        NUKE_RADIUS_SCALE = b.comment("Multiplier for all nuclear damage radii (1.0 = RDS-1 ~40 blocks, AN602 ~170)")
                .defineInRange("radiusScale", 1.0, 0.1, 3.0);
        NUKE_COLUMNS_PER_TICK = b.comment("Terrain columns processed per tick by the blast wave (performance)")
                .defineInRange("columnsPerTick", 1800, 100, 20000);
        REALWAR_NUKE = b.comment("Use the Real War nuclear blast (staged shock wave, crater, thermal pulse, mushroom cloud, radiation plume)").define("realWarBlast", true);
        FALLOUT_SECONDS = b.comment("Duration of the radioactive fallout zone").defineInRange("falloutSeconds", 300, 0, 3600);
        b.pop();
        b.push("conventional");
        HE_BLOCK_DAMAGE = b.comment("Drone / cruise missile warheads destroy blocks").define("blockDamage", true);
        REALWAR_HE = b.comment("Use the Real War conventional blast (ray-cast blast wave, fragments, fireball FX) for drone and missile warheads").define("realWarBlast", true);
        SPEED_SCALE = b.comment("World speed of drones and missiles relative to real speed (distances in MC are short)")
                .defineInRange("speedScale", 0.5, 0.1, 1.0);
        b.pop();
        SPEC = b.build();
    }

    private ArsenalConfig() {
    }
}
