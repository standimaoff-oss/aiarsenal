/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraftforge.common.ForgeConfigSpec
 *  net.minecraftforge.common.ForgeConfigSpec$BooleanValue
 *  net.minecraftforge.common.ForgeConfigSpec$Builder
 *  net.minecraftforge.common.ForgeConfigSpec$DoubleValue
 *  net.minecraftforge.common.ForgeConfigSpec$IntValue
 *  org.apache.commons.lang3.tuple.Pair
 */
package ru.aiforge.realwar;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class RWConfig {
    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    private RWConfig() {
    }

    static {
        Pair c = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = (Common)c.getLeft();
        COMMON_SPEC = (ForgeConfigSpec)c.getRight();
        Pair k = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = (Client)k.getLeft();
        CLIENT_SPEC = (ForgeConfigSpec)k.getRight();
    }

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue nuclearEnabled;
        public final ForgeConfigSpec.DoubleValue metersPerBlock;
        public final ForgeConfigSpec.IntValue maxBlastRadius;
        public final ForgeConfigSpec.DoubleValue craterScale;
        public final ForgeConfigSpec.BooleanValue destroyBlocks;
        public final ForgeConfigSpec.BooleanValue thermalIgnition;
        public final ForgeConfigSpec.BooleanValue entityDamage;
        public final ForgeConfigSpec.IntValue blastBudgetMs;
        public final ForgeConfigSpec.IntValue conventionalMaxRadius;
        public final ForgeConfigSpec.BooleanValue radiationEnabled;
        public final ForgeConfigSpec.IntValue radiationHourTicks;
        public final ForgeConfigSpec.DoubleValue falloutLengthScale;
        public final ForgeConfigSpec.BooleanValue radioactiveItems;
        public final ForgeConfigSpec.DoubleValue ordnanceScale;
        public final ForgeConfigSpec.IntValue radarRange;
        public final ForgeConfigSpec.IntValue radarLink;
        public final ForgeConfigSpec.BooleanValue rocketsDestroyBlocks;
        public final ForgeConfigSpec.BooleanValue vehicleDamage;
        public final ForgeConfigSpec.IntValue flakRange;
        public final ForgeConfigSpec.BooleanValue wreckSites;
        public final ForgeConfigSpec.BooleanValue supplyCaches;

        Common(ForgeConfigSpec.Builder b) {
            b.comment("\u042f\u0434\u0435\u0440\u043d\u044b\u0435 \u0432\u0437\u0440\u044b\u0432\u044b").push("nuclear");
            this.nuclearEnabled = b.comment("\u0420\u0430\u0437\u0440\u0435\u0448\u0438\u0442\u044c \u044f\u0434\u0435\u0440\u043d\u044b\u0435 \u0432\u0437\u0440\u044b\u0432\u044b \u0432\u043e\u043e\u0431\u0449\u0435").define("enabled", true);
            this.metersPerBlock = b.comment(new String[]{"\u041c\u0430\u0441\u0448\u0442\u0430\u0431 \u0441\u0436\u0430\u0442\u0438\u044f \u0440\u0430\u0441\u0441\u0442\u043e\u044f\u043d\u0438\u0439 \u0434\u043b\u044f \u042f\u0414\u0415\u0420\u041d\u042b\u0425 \u0432\u0437\u0440\u044b\u0432\u043e\u0432: \u0441\u043a\u043e\u043b\u044c\u043a\u043e \u0440\u0435\u0430\u043b\u044c\u043d\u044b\u0445 \u043c\u0435\u0442\u0440\u043e\u0432 \u0432 \u043e\u0434\u043d\u043e\u043c \u0431\u043b\u043e\u043a\u0435.", "17.5 => 5 psi (\u0440\u0430\u0437\u0440\u0443\u0448\u0435\u043d\u0438\u0435 \u043a\u0438\u0440\u043f\u0438\u0447\u043d\u044b\u0445 \u0434\u043e\u043c\u043e\u0432) \u0434\u043b\u044f \u0425\u0438\u0440\u043e\u0441\u0438\u043c\u044b 1750 \u043c \u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0441\u044f ~100 \u0431\u043b\u043e\u043a\u043e\u0432.", "\u041f\u043e\u0441\u0442\u0430\u0432\u044c\u0442\u0435 1.0 \u0434\u043b\u044f 1:1 (\u0440\u0430\u0434\u0438\u0443\u0441\u044b \u0432 \u043a\u0438\u043b\u043e\u043c\u0435\u0442\u0440\u044b, \u043d\u0443\u0436\u0435\u043d \u043c\u043e\u0449\u043d\u044b\u0439 \u0441\u0435\u0440\u0432\u0435\u0440 \u0438 \u0431\u043e\u043b\u044c\u0448\u0430\u044f \u0434\u0430\u043b\u044c\u043d\u043e\u0441\u0442\u044c \u043f\u0440\u043e\u0440\u0438\u0441\u043e\u0432\u043a\u0438)."}).defineInRange("metersPerBlock", 17.5, 1.0, 200.0);
            this.maxBlastRadius = b.comment("\u041c\u0430\u043a\u0441\u0438\u043c\u0430\u043b\u044c\u043d\u044b\u0439 \u0440\u0430\u0434\u0438\u0443\u0441 \u0443\u0434\u0430\u0440\u043d\u043e\u0439 \u0432\u043e\u043b\u043d\u044b \u0432 \u0431\u043b\u043e\u043a\u0430\u0445 (\u043e\u0433\u0440\u0430\u043d\u0438\u0447\u0438\u0442\u0435\u043b\u044c \u043d\u0430\u0433\u0440\u0443\u0437\u043a\u0438)").defineInRange("maxBlastRadius", 300, 32, 2000);
            this.craterScale = b.comment("\u041c\u043d\u043e\u0436\u0438\u0442\u0435\u043b\u044c \u0440\u0430\u0437\u043c\u0435\u0440\u0430 \u0432\u043e\u0440\u043e\u043d\u043a\u0438 \u043f\u0440\u0438 \u043d\u0430\u0437\u0435\u043c\u043d\u043e\u043c \u0432\u0437\u0440\u044b\u0432\u0435").defineInRange("craterScale", 1.0, 0.0, 5.0);
            this.destroyBlocks = b.comment("\u0412\u0437\u0440\u044b\u0432\u044b \u0440\u0430\u0437\u0440\u0443\u0448\u0430\u044e\u0442 \u0431\u043b\u043e\u043a\u0438").define("destroyBlocks", true);
            this.thermalIgnition = b.comment("\u0421\u0432\u0435\u0442\u043e\u0432\u043e\u0435 \u0438\u0437\u043b\u0443\u0447\u0435\u043d\u0438\u0435 \u043f\u043e\u0434\u0436\u0438\u0433\u0430\u0435\u0442 \u0433\u043e\u0440\u044e\u0447\u0438\u0435 \u0431\u043b\u043e\u043a\u0438 \u0438 \u0432\u044b\u0436\u0438\u0433\u0430\u0435\u0442 \u0437\u0435\u043c\u043b\u044e").define("thermalIgnition", true);
            this.entityDamage = b.comment("\u0412\u0437\u0440\u044b\u0432\u044b \u0438 \u0438\u0437\u043b\u0443\u0447\u0435\u043d\u0438\u0435 \u043d\u0430\u043d\u043e\u0441\u044f\u0442 \u0443\u0440\u043e\u043d \u0441\u0443\u0449\u0435\u0441\u0442\u0432\u0430\u043c").define("entityDamage", true);
            this.blastBudgetMs = b.comment("\u0411\u044e\u0434\u0436\u0435\u0442 \u0432\u0440\u0435\u043c\u0435\u043d\u0438 \u043d\u0430 \u043e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0443 \u0432\u0437\u0440\u044b\u0432\u043e\u0432 \u0437\u0430 \u0442\u0438\u043a (\u043c\u0441). \u041c\u0435\u043d\u044c\u0448\u0435 = \u043f\u043b\u0430\u0432\u043d\u0435\u0435 TPS, \u043d\u043e \u043c\u0435\u0434\u043b\u0435\u043d\u043d\u0435\u0435 \u0432\u043e\u043b\u043d\u0430").defineInRange("blastBudgetMs", 14, 1, 45);
            this.conventionalMaxRadius = b.comment("\u041c\u0430\u043a\u0441\u0438\u043c\u0430\u043b\u044c\u043d\u044b\u0439 \u0440\u0430\u0434\u0438\u0443\u0441 \u043e\u0431\u044b\u0447\u043d\u044b\u0445 (\u0444\u0443\u0433\u0430\u0441\u043d\u044b\u0445) \u0432\u0437\u0440\u044b\u0432\u043e\u0432, \u0431\u043b\u043e\u043a\u0438").defineInRange("conventionalMaxRadius", 64, 8, 256);
            b.pop();
            b.comment("\u0420\u0430\u0434\u0438\u0430\u0446\u0438\u044f \u0438 \u043e\u0441\u0430\u0434\u043a\u0438").push("radiation");
            this.radiationEnabled = b.define("enabled", true);
            this.radiationHourTicks = b.comment(new String[]{"\u0421\u043a\u043e\u043b\u044c\u043a\u043e \u0438\u0433\u0440\u043e\u0432\u044b\u0445 \u0442\u0438\u043a\u043e\u0432 \u0434\u043b\u0438\u0442\u0441\u044f \u00ab1 \u0447\u0430\u0441\u00bb \u0434\u043b\u044f \u0437\u0430\u043a\u043e\u043d\u0430 \u0441\u043f\u0430\u0434\u0430 \u0440\u0430\u0434\u0438\u0430\u0446\u0438\u0438 t^-1.2 (\u043f\u0440\u0430\u0432\u0438\u043b\u043e 7/10) \u0438 \u0434\u043b\u044f \u0434\u043e\u0437\u044b", "2400 \u0442\u0438\u043a\u043e\u0432 = 2 \u043c\u0438\u043d\u0443\u0442\u044b \u0440\u0435\u0430\u043b\u044c\u043d\u043e\u0433\u043e \u0432\u0440\u0435\u043c\u0435\u043d\u0438. \u041f\u043e\u0441\u0442\u0430\u0432\u044c\u0442\u0435 72000 \u0434\u043b\u044f 1:1."}).defineInRange("hourTicks", 2400, 200, 720000);
            this.falloutLengthScale = b.comment("\u041c\u043d\u043e\u0436\u0438\u0442\u0435\u043b\u044c \u0434\u043b\u0438\u043d\u044b \u0448\u043b\u0435\u0439\u0444\u0430 \u043e\u0441\u0430\u0434\u043a\u043e\u0432 (1.0 = \u043c\u0430\u0441\u0448\u0442\u0430\u0431\u0438\u0440\u043e\u0432\u0430\u043d\u043e \u043f\u043e metersPerBlock)").defineInRange("falloutLengthScale", 0.35, 0.0, 3.0);
            this.radioactiveItems = b.comment("\u0420\u0430\u0434\u0438\u043e\u0430\u043a\u0442\u0438\u0432\u043d\u044b\u0435 \u043f\u0440\u0435\u0434\u043c\u0435\u0442\u044b \u0432 \u0438\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u0435 \u043e\u0431\u043b\u0443\u0447\u0430\u044e\u0442 \u0432\u043b\u0430\u0434\u0435\u043b\u044c\u0446\u0430").define("radioactiveItems", true);
            b.pop();
            b.comment("\u0420\u0435\u0430\u043a\u0442\u0438\u0432\u043d\u044b\u0435 \u0441\u043d\u0430\u0440\u044f\u0434\u044b, \u0440\u0430\u043a\u0435\u0442\u044b \u041f\u0412\u041e \u0438 \u0442\u0435\u0445\u043d\u0438\u043a\u0430").push("ordnance");
            this.ordnanceScale = b.comment(new String[]{"\u041c\u0430\u0441\u0448\u0442\u0430\u0431 K \u043f\u043e \u0424\u0440\u0443\u0434\u0443 \u0434\u043b\u044f \u0440\u0430\u043a\u0435\u0442: \u0440\u0430\u0441\u0441\u0442\u043e\u044f\u043d\u0438\u044f \u0434\u0435\u043b\u044f\u0442\u0441\u044f \u043d\u0430 K, \u0441\u043a\u043e\u0440\u043e\u0441\u0442\u0438 \u0438 \u0432\u0440\u0435\u043c\u044f \u2014 \u043d\u0430 \u043a\u043e\u0440\u0435\u043d\u044c \u0438\u0437 K.", "K=25: \u00ab\u0413\u0440\u0430\u0434\u00bb \u043b\u0435\u0442\u0438\u0442 20 \u043a\u043c => ~816 \u0431\u043b\u043e\u043a\u043e\u0432 \u0437\u0430 ~14 \u0441, \u00ab\u0421\u043c\u0435\u0440\u0447\u00bb 70 \u043a\u043c => ~2800 \u0431\u043b\u043e\u043a\u043e\u0432. 1.0 \u2014 \u0447\u0435\u0441\u0442\u043d\u044b\u0439 1:1 (\u043d\u0443\u0436\u0435\u043d \u043e\u0433\u0440\u043e\u043c\u043d\u044b\u0439 \u043c\u0438\u0440)."}).defineInRange("scaleK", 25.0, 1.0, 100.0);
            this.radarRange = b.comment("\u0414\u0430\u043b\u044c\u043d\u043e\u0441\u0442\u044c \u043e\u0431\u043d\u0430\u0440\u0443\u0436\u0435\u043d\u0438\u044f \u0440\u0430\u0434\u0438\u043e\u043b\u043e\u043a\u0430\u0446\u0438\u043e\u043d\u043d\u043e\u0433\u043e \u043f\u043e\u0441\u0442\u0430, \u0431\u043b\u043e\u043a\u0438 (\u043f\u043e\u0434\u0432\u0438\u0436\u043d\u0430\u044f \u0420\u041b\u0421 \u2014 75%)").defineInRange("radarRange", 600, 50, 2500);
            this.radarLink = b.comment("\u0420\u0430\u0434\u0438\u0443\u0441, \u0432 \u043a\u043e\u0442\u043e\u0440\u043e\u043c \u0420\u041b\u0421 \u043f\u0435\u0440\u0435\u0434\u0430\u0451\u0442 \u043a\u0430\u0440\u0442\u0438\u043d\u0443 \u0432\u043e\u0437\u0434\u0443\u0448\u043d\u043e\u0439 \u043e\u0431\u0441\u0442\u0430\u043d\u043e\u0432\u043a\u0438 \u043d\u0430 \u0421-400, \u0431\u043b\u043e\u043a\u0438").defineInRange("radarLink", 250, 20, 1000);
            this.rocketsDestroyBlocks = b.comment("\u0420\u0430\u043a\u0435\u0442\u044b \u0438 \u0431\u043e\u0435\u043f\u0440\u0438\u043f\u0430\u0441\u044b \u041f\u0412\u041e \u0440\u0430\u0437\u0440\u0443\u0448\u0430\u044e\u0442 \u0431\u043b\u043e\u043a\u0438").define("rocketsDestroyBlocks", true);
            this.vehicleDamage = b.comment("\u0422\u0435\u0445\u043d\u0438\u043a\u0430 \u043f\u043e\u043b\u0443\u0447\u0430\u0435\u0442 \u0443\u0440\u043e\u043d \u043e\u0442 \u0432\u0437\u0440\u044b\u0432\u043e\u0432, \u043f\u0443\u043b\u044c \u0438 \u0440\u0430\u043a\u0435\u0442").define("vehicleDamage", true);
            this.flakRange = b.comment("\u042d\u0444\u0444\u0435\u043a\u0442\u0438\u0432\u043d\u0430\u044f \u0434\u0430\u043b\u044c\u043d\u043e\u0441\u0442\u044c \u0417\u0423-23-2 (\u0431\u043b\u043e\u043a\u0438)").defineInRange("flakRange", 170, 40, 600);
            b.pop();
            b.comment("Worldgen (r17)").push("worldgen");
            this.wreckSites = b.comment("Burnt-out vehicle wreck sites with loot crates (~1 per 160 chunks)").define("wreckSites", true);
            this.supplyCaches = b.comment("Sandbagged supply caches with loot crates (~1 per 120 chunks)").define("supplyCaches", true);
            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.DoubleValue particleQuality;
        public final ForgeConfigSpec.BooleanValue flashEffect;
        public final ForgeConfigSpec.BooleanValue cameraShake;
        public final ForgeConfigSpec.DoubleValue maxEffectDistance;
        public final ForgeConfigSpec.BooleanValue geigerHud;
        public final ForgeConfigSpec.DoubleValue trailQuality;
        public final ForgeConfigSpec.BooleanValue vehicleHud;

        Client(ForgeConfigSpec.Builder b) {
            b.comment("\u0412\u0438\u0437\u0443\u0430\u043b\u044c\u043d\u044b\u0435 \u044d\u0444\u0444\u0435\u043a\u0442\u044b").push("effects");
            this.particleQuality = b.comment("\u041c\u043d\u043e\u0436\u0438\u0442\u0435\u043b\u044c \u043a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u0430 \u0447\u0430\u0441\u0442\u0438\u0446 \u0433\u0440\u0438\u0431\u043e\u0432\u0438\u0434\u043d\u043e\u0433\u043e \u043e\u0431\u043b\u0430\u043a\u0430 (0.25 = \u0441\u043b\u0430\u0431\u044b\u0439 \u041f\u041a, 2.0 = \u043c\u0430\u043a\u0441\u0438\u043c\u0443\u043c)").defineInRange("particleQuality", 1.0, 0.1, 3.0);
            this.flashEffect = b.comment("\u041e\u0441\u043b\u0435\u043f\u043b\u044f\u044e\u0449\u0430\u044f \u0432\u0441\u043f\u044b\u0448\u043a\u0430").define("flash", true);
            this.cameraShake = b.comment("\u0422\u0440\u044f\u0441\u043a\u0430 \u043a\u0430\u043c\u0435\u0440\u044b \u043e\u0442 \u0443\u0434\u0430\u0440\u043d\u043e\u0439 \u0432\u043e\u043b\u043d\u044b").define("cameraShake", true);
            this.maxEffectDistance = b.comment("\u0414\u0430\u043b\u044c\u0448\u0435 \u044d\u0442\u043e\u0433\u043e \u0440\u0430\u0441\u0441\u0442\u043e\u044f\u043d\u0438\u044f (\u0431\u043b\u043e\u043a\u043e\u0432) \u044d\u0444\u0444\u0435\u043a\u0442\u044b \u0432\u0437\u0440\u044b\u0432\u0430 \u043d\u0435 \u043e\u0442\u043e\u0431\u0440\u0430\u0436\u0430\u044e\u0442\u0441\u044f").defineInRange("maxEffectDistance", 2500.0, 100.0, 10000.0);
            this.geigerHud = b.comment("\u041f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0442\u044c \u043f\u043e\u043a\u0430\u0437\u0430\u043d\u0438\u044f \u0441\u0447\u0451\u0442\u0447\u0438\u043a\u0430 \u0413\u0435\u0439\u0433\u0435\u0440\u0430 \u043d\u0430 \u044d\u043a\u0440\u0430\u043d\u0435").define("geigerHud", true);
            this.trailQuality = b.comment("\u041f\u043b\u043e\u0442\u043d\u043e\u0441\u0442\u044c \u0434\u044b\u043c\u043e\u0432\u043e\u0433\u043e \u0448\u043b\u0435\u0439\u0444\u0430 \u0440\u0430\u043a\u0435\u0442 (0.3 \u0441\u043b\u0430\u0431\u044b\u0439 \u041f\u041a, 2.0 \u043c\u0430\u043a\u0441\u0438\u043c\u0443\u043c)").defineInRange("trailQuality", 1.0, 0.1, 3.0);
            this.vehicleHud = b.comment("\u042d\u043a\u0440\u0430\u043d\u043d\u044b\u0435 \u043f\u0440\u0438\u0431\u043e\u0440\u044b \u0442\u0435\u0445\u043d\u0438\u043a\u0438 (\u0441\u043a\u043e\u0440\u043e\u0441\u0442\u044c, \u043f\u0435\u0440\u0435\u0434\u0430\u0447\u0430, \u043d\u0430\u0432\u043e\u0434\u043a\u0430)").define("vehicleHud", true);
            b.pop();
        }
    }
}

