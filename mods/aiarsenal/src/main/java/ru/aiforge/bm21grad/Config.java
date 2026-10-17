package ru.aiforge.bm21grad;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-side gameplay configuration ({@code serverconfig/bm21grad-server.toml}). */
public final class Config {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue RANGE_SCALE;
    public static final ForgeConfigSpec.IntValue SALVO_INTERVAL;
    public static final ForgeConfigSpec.IntValue RELOAD_TICKS;
    public static final ForgeConfigSpec.BooleanValue BLOCK_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue EXPLOSION_SCALE;
    public static final ForgeConfigSpec.DoubleValue DISPERSION;
    public static final ForgeConfigSpec.DoubleValue VEHICLE_HEALTH;
    public static final ForgeConfigSpec.BooleanValue CREATIVE_FREE_AMMO;
    public static final ForgeConfigSpec.BooleanValue DEBRIS;
    public static final ForgeConfigSpec.DoubleValue MAX_SPEED;
    public static final ForgeConfigSpec.IntValue MAX_GHOST_TICKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("ballistics");
        RANGE_SCALE = b.comment("Range multiplier. 1.0 gives ~760 blocks max range (game-scaled 20.4 km).")
                .defineInRange("rangeScale", 1.0, 0.1, 10.0);
        DISPERSION = b.comment("Circular dispersion as a fraction of range (real Grad: ~1%).")
                .defineInRange("dispersion", 0.012, 0.0, 0.2);
        MAX_GHOST_TICKS = b.comment("Max lifetime (ticks) of projectiles simulated outside loaded chunks.")
                .defineInRange("maxGhostTicks", 2400, 200, 12000);
        b.pop();
        b.push("launcher");
        SALVO_INTERVAL = b.comment("Ticks between rockets in a salvo (real: 0.5 s = 10 ticks, 40 rockets in 20 s).")
                .defineInRange("salvoIntervalTicks", 10, 1, 200);
        RELOAD_TICKS = b.comment("Ticks to load one tube (real crew: ~7 min for 40 tubes = 210 ticks per tube).")
                .defineInRange("reloadTicksPerTube", 40, 1, 2000);
        CREATIVE_FREE_AMMO = b.comment("Creative players reload with HE-FRAG rockets without consuming items.")
                .define("creativeFreeAmmo", true);
        VEHICLE_HEALTH = b.defineInRange("vehicleHealth", 300.0, 1.0, 100000.0);
        MAX_SPEED = b.comment("Max road speed in blocks/tick (75 km/h = 1.04 b/t).")
                .defineInRange("maxSpeed", 0.9, 0.05, 3.0);
        b.pop();
        b.push("effects");
        BLOCK_DAMAGE = b.comment("Warheads break blocks.").define("blockDamage", true);
        EXPLOSION_SCALE = b.defineInRange("explosionScale", 1.0, 0.1, 4.0);
        DEBRIS = b.comment("Explosions throw falling-block debris.").define("debris", true);
        b.pop();
        SPEC = b.build();
    }

    private Config() {
    }
}
