/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.network.chat.Component
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.world.item.CreativeModeTab
 *  net.minecraft.world.item.CreativeModeTab$Output
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.ItemLike
 *  net.minecraftforge.registries.DeferredRegister
 *  net.minecraftforge.registries.RegistryObject
 */
package ru.aiforge.realwar.registry;

import ru.aiforge.realwar.missile.MissileType;
import ru.aiforge.realwar.registry.ModBlocks;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.registry.ModS3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create((ResourceKey)Registries.CREATIVE_MODE_TAB, (String)"aiarsenal");
    private static final Map<String, Cat> MAP = new java.util.LinkedHashMap<String, Cat>();

    private ModTabs() {
    }

    private static void put(Cat c, String ... names) {
        for (String n : names) {
            MAP.put(n, c);
        }
    }

    public static Cat tabOf(String path) {
        return MAP.get(path);
    }

    public static Set<String> mappedNames() {
        return new HashSet<String>(MAP.keySet());
    }

    public static final java.util.List<String> SECTIONS = java.util.List.of(
            "wwi_weapons", "wwi_vehicles", "wwi_ammo",
            "ww2_weapons", "ww2_vehicles", "ww2_clothing", "ww2_ammo",
            "modern_weapons", "modern_vehicles", "modern_clothing", "modern_ammo",
            "napoleonic_weapons", "napoleonic_vehicles", "napoleonic_clothing", "napoleonic_ammo",
            "common_parts", "common_equipment", "common_building",
            "ai_units");
    private static final Set<String> WW2 = Set.of(
            "ppsh41", "mp40", "mosin", "k98k", "mosin_pu", "k98k_zf39", "c96", "tt33",
            "mag_ppsh", "mag_mp40", "clip_mosin", "clip_k98", "round_762x54r", "round_792x57", "clip_c96", "mag_tt33",
            "pz2_vehicle", "pz4_vehicle", "tiger_vehicle", "sturmtiger_vehicle", "maus_vehicle", "ratte_vehicle",
            "t34_vehicle", "kv2_vehicle", "is2_vehicle", "kv5_vehicle", "bm13_vehicle", "bismarck_ship",
            "bf109_plane", "yak3_plane", "he111_plane", "pe8_plane", "little_boy",
            "shells_20", "shells_75", "shells_88", "shells_85", "shells_107", "shells_128", "shells_280",
            "rocket_380", "rocket_m13", "sc250", "sc1000", "fab250sv", "fab1000", "fab5000");
    private static final Set<String> NAPOLEONIC = Set.of("musket_1777", "musket_1808", "pistol_an13", "pistol_1809", "sabre_briquet", "sabre_1809", "cannon_fr6", "cannon_ri12", "paper_cartridge", "pistol_cartridge", "charge_fr6", "charge_ri12", "cannonball");
    private static final Set<String> WWI = Set.of("mark_i_vehicle", "gewehr98", "shells_57", "mills_bomb", "stielhandgranate", "wex", "wwi_fuel");
    private static final Set<String> WWI_SHARED_AMMO = Set.of("clip_k98"); // same 5-round 7.92x57 Mauser clip feeds Gewehr 98 and Kar98k
    private static final Set<String> SHARED_AMMO = Set.of("shells_122", "shells_152"); // IS-2/KV-2 AND D-30/Msta
    private static final Set<String> NUCLEAR_PARTS = Set.of("lb_casing", "rds_core", "rds_casing", "cordite", "bridgewire_detonator");

    /** Primary section; a genuinely shared vehicle calibre can also appear in the other era. */
    public static String sectionOf(String name) {
        Cat c = MAP.get(name);
        if (c == null) return null;
        if (c == Cat.UNITS) return "ai_units";   // r27: all summon eggs live in one section
        if (NUCLEAR_PARTS.contains(name) || c == Cat.PARTS || c == Cat.INDUSTRY) return "common_parts";
        if (c == Cat.BUILDING || c == Cat.FORTIFICATION) return "common_building";
        if (name.equals("craft_service_kit")) return "common_equipment";
        if (c == Cat.PROTECTION) return name.startsWith("hazmat_") ? "modern_clothing" : "common_equipment";
        if (c == Cat.UNIFORMS && name.startsWith("uniform_nap_")) return "napoleonic_clothing";
        if (c == Cat.UNIFORMS && name.startsWith("uniform_ww2_")) return "ww2_clothing";
        String era = NAPOLEONIC.contains(name) ? "napoleonic_" : WWI.contains(name) ? "wwi_" : WW2.contains(name) ? "ww2_" : "modern_";
        if (c == Cat.UNIFORMS) return era + "clothing";
        if (c == Cat.VEHICLES || c == Cat.DRONES && !Set.of("tablet", "fpv_goggles").contains(name)) return era + "vehicles";
        if (c == Cat.AMMO || c == Cat.NUCLEAR && Set.of("yars", "rds1", "an602", "little_boy", "rds_4").contains(name)
                || c == Cat.MISSILES && (name.startsWith("missile_") || Set.of("kh101", "kh55", "kalibr", "tomahawk").contains(name)))
            return era + "ammo";
        return era + "weapons";
    }

    public static java.util.List<Item> content(String section) {
        java.util.List<Item> result = new ArrayList<>();
        for (String name : MAP.keySet()) {
            if (!section.equals(sectionOf(name))
                    && !(section.equals("ww2_ammo") && SHARED_AMMO.contains(name))
                    && !(section.equals("wwi_ammo") && WWI_SHARED_AMMO.contains(name))) continue;
            ResourceLocation key = new ResourceLocation("aiarsenal", name);
            if (BuiltInRegistries.ITEM.containsKey(key)) result.add(BuiltInRegistries.ITEM.get(key));
        }
        return java.util.List.copyOf(result);
    }

    private static void section(String id, String icon) {
        TABS.register(id, () -> {
            var builder = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aiarsenal." + id))
                    .icon(() -> new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("aiarsenal", icon))))
                    .displayItems((params, out) -> content(id).forEach(out::accept));
            // Forge otherwise sorts our IDs alphabetically, leaving WWII weapons on a separate page.
            int index = SECTIONS.indexOf(id);
            if (index > 0) builder.withTabsBefore(new ResourceLocation("aiarsenal", SECTIONS.get(index - 1)));
            return builder.build();
        });
    }

    static {
        // one set of sections for the whole mod (Real War + Tu bombers + BM-21 + arsenal)
        ModTabs.put(Cat.VEHICLES, "bm21", "grad_vehicle", "smerch_vehicle", "s400_vehicle", "zu23_vehicle", "t90m_vehicle", "abrams_vehicle", "bmp3_vehicle", "msta_vehicle", "d30_vehicle", "m777_vehicle", "pz2_vehicle", "pz4_vehicle", "tiger_vehicle", "sturmtiger_vehicle", "maus_vehicle", "ratte_vehicle", "t34_vehicle", "kv2_vehicle", "is2_vehicle", "kv5_vehicle", "bm13_vehicle", "bradley_vehicle", "tos1a_vehicle", "ural_vehicle", "radar_mobile_vehicle", "radar_post_vehicle", "yars_tel", "cruise_launcher", "shahed_rail", "tu95ms", "tu160", "tu95_vehicle", "ka52_vehicle", "ah64_vehicle", "raptor_boat", "cb90_boat", "sub636_vehicle", "bismarck_ship", "bf109_plane", "yak3_plane", "he111_plane", "pe8_plane", "su57_plane");
        ModTabs.put(Cat.DRONES, "mq9", "orion", "shahed136", "shahed131", "shahed238", "liutyi", "fpv7", "fpv10", "tablet", "fpv_goggles");
        ModTabs.put(Cat.NUCLEAR, "yars", "rds1", "an602", "detonator", "little_boy", "rds_4", "arming_plug", "remote_detonator", "barometric_fuze", "lb_casing", "rds_core", "rds_casing", "cordite", "bridgewire_detonator");
        ModTabs.put(Cat.MISSILES, "kh101", "kh55", "kalibr", "tomahawk", "missile_tactical_he", "missile_tactical_kinetic", "missile_tactical_mirv", "missile_tactical_thermobaric", "missile_operational_he", "missile_operational_kinetic", "missile_operational_mirv", "missile_operational_penetrator", "missile_cruise_he", "missile_cruise_cluster", "missile_icbm_he", "missile_icbm_kinetic", "missile_icbm_mirv", "missile_icbm_mirv_heavy", "launch_pad_1", "launch_pad_2", "launch_pad_3", "launch_computer", "siren");
        ModTabs.put(Cat.AMMO, "rocket_9m22u", "rocket_9m28f", "rocket_9m22s", "rocket_9m218", "rocket_9m43", "grad_rocket", "smerch_rocket", "sam_48n6", "flak_ammo_box", "shells_125", "shells_120", "shells_100", "shells_122", "shells_152", "shells_155", "shells_20", "shells_75", "shells_88", "shells_85", "shells_107", "shells_128", "shells_280", "rocket_380", "rocket_m13", "shells_25", "rocket_220", "fab250", "fab500", "fab1500", "fab500m54", "fab1500m54", "fab5000", "sc250", "sc1000", "fab250sv", "fab1000", "craft_service_kit");
        ModTabs.put(Cat.PARTS, "propellant_grain", "rocket_motor_122", "rocket_motor_300", "warhead_122", "warhead_300", "contact_fuze", "proximity_fuze", "radar_seeker", "guidance_unit", "sam_motor", "sam_warhead", "diesel_engine", "truck_wheel", "hydraulic_drive", "chassis_6x6", "chassis_8x8", "armored_cab", "launcher_package_122", "launcher_package_300", "sam_launcher_frame", "gun_mount_23", "gun_barrels_23");
        ModTabs.put(Cat.INDUSTRY, "steel_ingot", "steel_plate", "steel_tube", "rubberized_fabric", "scorched_earth", "trinitite");
        ModTabs.put(Cat.PROTECTION, "rangefinder", "artillery_designator", "radio", "geiger_counter", "potassium_iodide", "hazmat_helmet", "hazmat_chestplate", "hazmat_leggings", "hazmat_boots");
        ModTabs.put(Cat.FORTIFICATION, "sandbags", "barbed_wire", "concertina_wire", "czech_hedgehog", "dragon_teeth", "concrete_barrier", "camo_net", "reinforced_concrete", "steel_block", "trench_wall", "trench_wall_tin", "duckboard", "firing_step", "fuel_drum_olive", "fuel_drum_red", "fuel_drum_blue", "egoza", "bruno_spiral", "mine_pmn2", "mine_tm62", "mine_mon50", "mine_ozm72", "tripwire_f1", "bear_trap", "warning_sign_mines", "warning_sign_radiation", "warning_sign_voltage", "warning_sign_biohazard", "warning_sign_checkpoint", "warning_sign_noentry", "entrenching_tool");
        ModTabs.put(Cat.BUILDING, "flagpole", "flag_russia", "flag_ussr", "flag_germany", "flag_usa", "flag_uk", "flag_france", "flag_china", "flag_japan", "flag_balkenkreuz", "flag_vdv", "flag_vmf", "flag_vks", "flag_nato", "flag_un", "flag_white", "flag_victory", "lantern_hurricane", "lamp_bulkhead", "lamp_street", "lamp_flood", "beacon_red", "beacon_rotating", "searchlight", "armor_plate", "armor_plate_hazard", "armor_plate_star_red", "armor_plate_balkenkreuz", "armor_plate_us_star", "armor_plate_roundel", "armor_plate_vdv", "armor_plate_vmf");
        // >>> r16 doors
        ModTabs.put(Cat.BUILDING, "door_steel", "door_bunker", "door_bars", "door_wood_army", "door_lab", "hatch_steel", "hatch_bunker", "hatch_grate", "gate_sliding", "gate_swing", "gate_boom", "gate_blast", "camo_screen_forest", "camo_canopy_forest", "camo_bush_forest", "camo_screen_desert", "camo_canopy_desert", "camo_bush_dry", "camo_screen_winter", "camo_canopy_winter", "camo_bush_snow");
        // <<< r16 doors
        // >>> r17
        ModTabs.put(Cat.BUILDING, "wreck_tank_hull", "wreck_tank_turret", "wreck_tank_track", "wreck_heli_body", "wreck_heli_tail", "wreck_heli_rotor", "wreck_jet_body", "wreck_jet_wing", "wreck_jet_tail", "crate_ammo", "crate_medical", "crate_military", "crate_weapons", "crate_secret");
        ModTabs.put(Cat.FORTIFICATION, "trap_punji", "trap_spike_strip", "trap_flare", "trap_ied", "trap_electric_fence");
        ModTabs.put(Cat.PROTECTION, "ration_irp", "bandage", "first_aid_kit");
        // <<< r17
        ModTabs.put(Cat.WEAPONS, "ak12", "mag_ak12", "m4a1", "mag_stanag", "t5000", "mag_t5000", "pkp", "box_pkp", "saiga12", "mag_saiga12", "r870", "shell_12", "rpg7", "pg7v", "igla", "igla_missile", "stinger", "stinger_missile", "javelin", "javelin_missile", "ppsh41", "mag_ppsh", "mp40", "mag_mp40", "mosin", "clip_mosin", "k98k", "clip_k98");
        ModTabs.put(Cat.WEAPONS, "mosin_pu", "k98k_zf39", "c96", "tt33", "round_762x54r", "round_792x57", "clip_c96", "mag_tt33");
        for (String set : ru.aiforge.realwar.item.UniformItem.SETS)
            for (String sl : new String[]{"head", "chest", "legs", "feet"}) ModTabs.put(Cat.UNIFORMS, "uniform_" + set + "_" + sl);
        // WWI vertical slice: one tank, one infantry rifle, and its 57 mm tank ammunition.
        put(Cat.VEHICLES, "mark_i_vehicle");
        put(Cat.WEAPONS, "gewehr98", "mills_bomb", "stielhandgranate", "wex");
        put(Cat.AMMO, "wwi_fuel");
        put(Cat.AMMO, "shells_57");
        for (var gun : ru.aiforge.realwar.handheld.GunKind.values()) put(Cat.AMMO, gun.ammo);
        put(Cat.AMMO, "javelin_missile", "charge_fr6", "charge_ri12", "cannonball");
        put(Cat.WEAPONS, "musket_1777", "musket_1808", "pistol_an13", "pistol_1809", "sabre_briquet", "sabre_1809");
        put(Cat.VEHICLES, "cannon_fr6", "cannon_ri12");
        section("wwi_weapons", "gewehr98");
        section("wwi_vehicles", "mark_i_vehicle");
        section("wwi_ammo", "shells_57");
        section("ww2_weapons", "mosin_pu");
        section("ww2_vehicles", "t34_vehicle");
        // Genuine 1943–45 garments; do not classify the old Cold War tank uniform as WWII.
        section("ww2_clothing", "uniform_ww2_su_infantry_head");
        section("ww2_ammo", "mag_ppsh");
        section("modern_weapons", "ak12");
        section("modern_vehicles", "t90m_vehicle");
        section("modern_clothing", "uniform_ru_private_head");
        section("modern_ammo", "mag_ak12");
        section("napoleonic_weapons", "musket_1777");
        section("napoleonic_vehicles", "cannon_fr6");
        section("napoleonic_ammo", "paper_cartridge");
        section("napoleonic_clothing", "uniform_nap_fr_infantry_head");
        section("common_parts", "steel_plate");
        section("common_equipment", "first_aid_kit");
        section("common_building", "sandbags");
        // r27: every summon egg in a single section.
        put(Cat.UNITS, "soldier_egg_commander", "soldier_egg_tanker", "soldier_egg_assault", "soldier_egg_medic");
        section("ai_units", "soldier_egg_commander");

    }

    public static enum Cat {
        NUCLEAR,
        MISSILES,
        VEHICLES,
        DRONES,
        AMMO,
        PARTS,
        INDUSTRY,
        PROTECTION,
        FORTIFICATION,
        WEAPONS,
        BUILDING,
        UNIFORMS,
        UNITS;

    }
}

