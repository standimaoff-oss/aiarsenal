/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.food.FoodProperties$Builder
 *  net.minecraft.world.item.ArmorItem$Type
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.level.block.Block
 *  net.minecraftforge.registries.DeferredRegister
 *  net.minecraftforge.registries.ForgeRegistries
 *  net.minecraftforge.registries.IForgeRegistry
 *  net.minecraftforge.registries.RegistryObject
 */
package ru.aiforge.realwar.registry;

import ru.aiforge.realwar.aircraft.AirBombItem;
import ru.aiforge.realwar.aircraft.AirBombType;
import ru.aiforge.realwar.aircraft.AircraftItem;
import ru.aiforge.realwar.block.BombBlock;
import ru.aiforge.realwar.item.ArmingPlugItem;
import ru.aiforge.realwar.item.BombItem;
import ru.aiforge.realwar.item.DesignatorItem;
import ru.aiforge.realwar.item.GeigerCounterItem;
import ru.aiforge.realwar.item.HazmatArmorItem;
import ru.aiforge.realwar.item.PotassiumIodideItem;
import ru.aiforge.realwar.item.RemoteDetonatorItem;
import ru.aiforge.realwar.item.TooltipBlockItem;
import ru.aiforge.realwar.item.TooltipItem;
import ru.aiforge.realwar.item.VehicleItem;
import ru.aiforge.realwar.registry.ModBlocks;
import ru.aiforge.realwar.vehicle.VehicleKind;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create((IForgeRegistry)ForgeRegistries.ITEMS, (String)"aiarsenal");
    public static final RegistryObject<Item> LITTLE_BOY = ITEMS.register("little_boy", () -> new BombItem((BombBlock)((ModBlocks.LITTLE_BOY.get())), new Item.Properties().stacksTo(1).fireResistant()));
    public static final RegistryObject<Item> RDS_4 = ITEMS.register("rds_4", () -> new BombItem((BombBlock)((ModBlocks.RDS_4.get())), new Item.Properties().stacksTo(1).fireResistant()));
    public static final RegistryObject<Item> ARMING_PLUG = ITEMS.register("arming_plug", () -> new ArmingPlugItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> REMOTE_DETONATOR = ITEMS.register("remote_detonator", () -> new RemoteDetonatorItem(new Item.Properties().stacksTo(1)));
    // ---- shoulder-fired weapons
    // r22: small arms rebuilt from drawings (AK-12 6P70, M4A1 + CompM4, RPG-7V2 + PGO-7V3)
    public static final RegistryObject<Item> RPG7 = ITEMS.register("rpg7", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.RPG7, new Item.Properties()));
    public static final RegistryObject<Item> AK12 = ITEMS.register("ak12", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.AK12, new Item.Properties()));
    public static final RegistryObject<Item> M4A1 = ITEMS.register("m4a1", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.M4A1, new Item.Properties()));
    public static final RegistryObject<Item> MAG_AK12 = ITEMS.register("mag_ak12", () -> new ru.aiforge.realwar.handheld.MagazineItem(ru.aiforge.realwar.handheld.GunKind.AK12, new Item.Properties()));
    public static final RegistryObject<Item> MAG_STANAG = ITEMS.register("mag_stanag", () -> new ru.aiforge.realwar.handheld.MagazineItem(ru.aiforge.realwar.handheld.GunKind.M4A1, new Item.Properties()));
    // r23: ORSIS T-5000M (.338 LM) and PKP Pecheneg
    public static final RegistryObject<Item> T5000 = ITEMS.register("t5000", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.T5000, new Item.Properties()));
    public static final RegistryObject<Item> PKP = ITEMS.register("pkp", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.PKP, new Item.Properties()));
    public static final RegistryObject<Item> MAG_T5000 = ITEMS.register("mag_t5000", () -> new ru.aiforge.realwar.handheld.MagazineItem(ru.aiforge.realwar.handheld.GunKind.T5000, new Item.Properties()));
    public static final RegistryObject<Item> BOX_PKP = ITEMS.register("box_pkp", () -> new ru.aiforge.realwar.handheld.MagazineItem(ru.aiforge.realwar.handheld.GunKind.PKP, new Item.Properties()));
    // r24: Saiga-12K isp.030 and Remington 870 Police Magnum (12 ga)
    public static final RegistryObject<Item> SAIGA12 = ITEMS.register("saiga12", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.SAIGA12, new Item.Properties()));
    public static final RegistryObject<Item> R870 = ITEMS.register("r870", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.R870, new Item.Properties()));
    public static final RegistryObject<Item> MAG_SAIGA12 = ITEMS.register("mag_saiga12", () -> new ru.aiforge.realwar.handheld.MagazineItem(ru.aiforge.realwar.handheld.GunKind.SAIGA12, new Item.Properties()));
    public static final RegistryObject<Item> SHELL_12 = ITEMS.register("shell_12", () -> new ru.aiforge.realwar.handheld.ShellItem(new Item.Properties()));
    public static final RegistryObject<Item> IGLA = ITEMS.register("igla", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.IGLA, new Item.Properties()));
    public static final RegistryObject<Item> JAVELIN = ITEMS.register("javelin", () -> new ru.aiforge.realwar.handheld.LauncherWeaponItem(ru.aiforge.realwar.handheld.HandheldKind.JAVELIN, new Item.Properties()));
    public static final RegistryObject<Item> PG7V = ITEMS.register("pg7v", () -> new ru.aiforge.realwar.handheld.HandheldAmmoItem(ru.aiforge.realwar.handheld.HandheldKind.RPG7, new Item.Properties()));
    public static final RegistryObject<Item> IGLA_MISSILE = ITEMS.register("igla_missile", () -> new ru.aiforge.realwar.handheld.HandheldAmmoItem(ru.aiforge.realwar.handheld.HandheldKind.IGLA, new Item.Properties()));
    public static final RegistryObject<Item> STINGER = ITEMS.register("stinger", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.STINGER, new Item.Properties()));
    public static final RegistryObject<Item> STINGER_MISSILE = ITEMS.register("stinger_missile", () -> new ru.aiforge.realwar.handheld.HandheldAmmoItem(ru.aiforge.realwar.handheld.HandheldKind.STINGER, new Item.Properties()));
    // r26 WW2 small arms
    public static final RegistryObject<Item> PPSH41 = ITEMS.register("ppsh41", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.PPSH41, new Item.Properties()));
    public static final RegistryObject<Item> MUSKET_1777 = ITEMS.register("musket_1777", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.MUSKET_1777, new Item.Properties()));
    public static final RegistryObject<Item> MUSKET_1808 = ITEMS.register("musket_1808", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.MUSKET_1808, new Item.Properties()));
    public static final RegistryObject<Item> PISTOL_AN13 = ITEMS.register("pistol_an13", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.PISTOL_AN13, new Item.Properties()));
    public static final RegistryObject<Item> PISTOL_1809 = ITEMS.register("pistol_1809", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.PISTOL_1809, new Item.Properties()));
    public static final RegistryObject<Item> PAPER_CARTRIDGE = ITEMS.register("paper_cartridge", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PISTOL_CARTRIDGE = ITEMS.register("pistol_cartridge", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CHARGE_FR6 = ITEMS.register("charge_fr6", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CHARGE_RI12 = ITEMS.register("charge_ri12", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CANNONBALL = ITEMS.register("cannonball", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SABRE_BRIQUET = ITEMS.register("sabre_briquet", ru.aiforge.realwar.napoleonic.SabreItem::new);
    public static final RegistryObject<Item> SABRE_1809 = ITEMS.register("sabre_1809", ru.aiforge.realwar.napoleonic.SabreItem::new);
    public static final RegistryObject<Item> CANNON_FR6 = ITEMS.register("cannon_fr6", () -> new ru.aiforge.realwar.napoleonic.CannonItem(true));
    public static final RegistryObject<Item> CANNON_RI12 = ITEMS.register("cannon_ri12", () -> new ru.aiforge.realwar.napoleonic.CannonItem(false));
    public static final RegistryObject<Item> MAG_PPSH = ITEMS.register("mag_ppsh", () -> new ru.aiforge.realwar.handheld.MagazineItem(ru.aiforge.realwar.handheld.GunKind.PPSH41, new Item.Properties()));
    public static final RegistryObject<Item> MP40 = ITEMS.register("mp40", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.MP40, new Item.Properties()));
    public static final RegistryObject<Item> MAG_MP40 = ITEMS.register("mag_mp40", () -> new ru.aiforge.realwar.handheld.MagazineItem(ru.aiforge.realwar.handheld.GunKind.MP40, new Item.Properties()));
    public static final RegistryObject<Item> MOSIN = ITEMS.register("mosin", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.MOSIN, new Item.Properties()));
    public static final RegistryObject<Item> CLIP_MOSIN = ITEMS.register("clip_mosin", () -> new ru.aiforge.realwar.handheld.ShellItem(new Item.Properties(), "tooltip.aiarsenal.clip_mosin"));
    public static final RegistryObject<Item> K98K = ITEMS.register("k98k", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.K98K, new Item.Properties()));
    public static final RegistryObject<Item> CLIP_K98 = ITEMS.register("clip_k98", () -> new ru.aiforge.realwar.handheld.ShellItem(new Item.Properties(), "tooltip.aiarsenal.clip_k98"));
    // WWI: Gewehr 98 uses the same five-round 7.92x57 Mauser stripper clip as the later Kar98k.
    public static final RegistryObject<Item> GEWEHR98 = ITEMS.register("gewehr98", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.GEWEHR98, new Item.Properties()));
    public static final RegistryObject<Item> JAVELIN_MISSILE = ITEMS.register("javelin_missile", () -> new ru.aiforge.realwar.handheld.HandheldAmmoItem(ru.aiforge.realwar.handheld.HandheldKind.JAVELIN, new Item.Properties()));
    public static final RegistryObject<Item> GEIGER_COUNTER = ITEMS.register("geiger_counter", () -> new GeigerCounterItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> POTASSIUM_IODIDE = ITEMS.register("potassium_iodide", () -> new PotassiumIodideItem(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder().nutrition(0).saturationMod(0.0f).alwaysEat().build())));
    // Stage 1c: separate scoped variants, fixed-magazine C96 and detachable-magazine TT-33.
    public static final RegistryObject<Item> MOSIN_PU = ITEMS.register("mosin_pu", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.MOSIN_PU, new Item.Properties()));
    public static final RegistryObject<Item> K98K_ZF39 = ITEMS.register("k98k_zf39", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.K98K_ZF39, new Item.Properties()));
    public static final RegistryObject<Item> C96 = ITEMS.register("c96", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.C96, new Item.Properties()));
    public static final RegistryObject<Item> TT33 = ITEMS.register("tt33", () -> new ru.aiforge.realwar.handheld.FirearmItem(ru.aiforge.realwar.handheld.GunKind.TT33, new Item.Properties()));
    public static final RegistryObject<Item> ROUND_762X54R = ITEMS.register("round_762x54r", () -> new ru.aiforge.realwar.handheld.ShellItem(new Item.Properties(), "tooltip.aiarsenal.round_762x54r"));
    public static final RegistryObject<Item> ROUND_792X57 = ITEMS.register("round_792x57", () -> new ru.aiforge.realwar.handheld.ShellItem(new Item.Properties(), "tooltip.aiarsenal.round_792x57"));
    public static final RegistryObject<Item> CLIP_C96 = ITEMS.register("clip_c96", () -> new ru.aiforge.realwar.handheld.ShellItem(new Item.Properties(), "tooltip.aiarsenal.clip_c96"));
    public static final RegistryObject<Item> MAG_TT33 = ITEMS.register("mag_tt33", () -> new ru.aiforge.realwar.handheld.MagazineItem(ru.aiforge.realwar.handheld.GunKind.TT33, new Item.Properties()));
    public static final RegistryObject<Item> HAZMAT_HELMET = ITEMS.register("hazmat_helmet", () -> new HazmatArmorItem(ArmorItem.Type.HELMET, new Item.Properties()));
    public static final RegistryObject<Item> HAZMAT_CHESTPLATE = ITEMS.register("hazmat_chestplate", () -> new HazmatArmorItem(ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final RegistryObject<Item> HAZMAT_LEGGINGS = ITEMS.register("hazmat_leggings", () -> new HazmatArmorItem(ArmorItem.Type.LEGGINGS, new Item.Properties()));
    public static final RegistryObject<Item> HAZMAT_BOOTS = ITEMS.register("hazmat_boots", () -> new HazmatArmorItem(ArmorItem.Type.BOOTS, new Item.Properties()));
    public static final RegistryObject<Item> LB_CASING = ModItems.item("lb_casing", 0.0, 1);
    public static final RegistryObject<Item> RDS_CORE = ModItems.item("rds_core", 80.0, 1);
    public static final RegistryObject<Item> RDS_CASING = ModItems.item("rds_casing", 0.0, 1);
    public static final RegistryObject<Item> BAROMETRIC_FUZE = ModItems.item("barometric_fuze", 0.0, 16);
    public static final RegistryObject<Item> CORDITE = ModItems.item("cordite");
    public static final RegistryObject<Item> BRIDGEWIRE_DETONATOR = ModItems.item("bridgewire_detonator", 0.0, 16);
    public static final RegistryObject<Item> STEEL_INGOT = ModItems.item("steel_ingot");
    public static final RegistryObject<Item> STEEL_PLATE = ModItems.item("steel_plate");
    public static final RegistryObject<Item> STEEL_TUBE = ModItems.item("steel_tube");
    public static final RegistryObject<Item> RUBBERIZED_FABRIC = ModItems.item("rubberized_fabric");
    public static final RegistryObject<Item> SANDBAGS = ModItems.blockItem("sandbags", ModBlocks.SANDBAGS);
    public static final RegistryObject<Item> BARBED_WIRE = ModItems.blockItem("barbed_wire", ModBlocks.BARBED_WIRE);
    public static final RegistryObject<Item> CONCERTINA_WIRE = ModItems.blockItem("concertina_wire", ModBlocks.CONCERTINA_WIRE);
    public static final RegistryObject<Item> CZECH_HEDGEHOG = ModItems.blockItem("czech_hedgehog", ModBlocks.CZECH_HEDGEHOG);
    public static final RegistryObject<Item> DRAGON_TEETH = ModItems.blockItem("dragon_teeth", ModBlocks.DRAGON_TEETH);
    public static final RegistryObject<Item> CONCRETE_BARRIER = ModItems.blockItem("concrete_barrier", ModBlocks.CONCRETE_BARRIER);
    public static final RegistryObject<Item> CAMO_NET = ModItems.blockItem("camo_net", ModBlocks.CAMO_NET);
    // >>> r12 field blocks (tools/gen_field_r12.py)
    public static final RegistryObject<Item> TRENCH_WALL = ModItems.blockItem("trench_wall", ModBlocks.TRENCH_WALL);
    public static final RegistryObject<Item> TRENCH_WALL_TIN = ModItems.blockItem("trench_wall_tin", ModBlocks.TRENCH_WALL_TIN);
    public static final RegistryObject<Item> DUCKBOARD = ModItems.blockItem("duckboard", ModBlocks.DUCKBOARD);
    public static final RegistryObject<Item> FIRING_STEP = ModItems.blockItem("firing_step", ModBlocks.FIRING_STEP);
    public static final RegistryObject<Item> FUEL_DRUM_OLIVE = ModItems.blockItem("fuel_drum_olive", ModBlocks.FUEL_DRUM_OLIVE);
    public static final RegistryObject<Item> FUEL_DRUM_RED = ModItems.blockItem("fuel_drum_red", ModBlocks.FUEL_DRUM_RED);
    public static final RegistryObject<Item> FUEL_DRUM_BLUE = ModItems.blockItem("fuel_drum_blue", ModBlocks.FUEL_DRUM_BLUE);
    public static final RegistryObject<Item> EGOZA = ModItems.blockItem("egoza", ModBlocks.EGOZA);
    public static final RegistryObject<Item> BRUNO_SPIRAL = ModItems.blockItem("bruno_spiral", ModBlocks.BRUNO_SPIRAL);
    public static final RegistryObject<Item> MINE_PMN2 = ModItems.blockItem("mine_pmn2", ModBlocks.MINE_PMN2);
    public static final RegistryObject<Item> MINE_TM62 = ModItems.blockItem("mine_tm62", ModBlocks.MINE_TM62);
    public static final RegistryObject<Item> MINE_MON50 = ModItems.blockItem("mine_mon50", ModBlocks.MINE_MON50);
    public static final RegistryObject<Item> MINE_OZM72 = ModItems.blockItem("mine_ozm72", ModBlocks.MINE_OZM72);
    public static final RegistryObject<Item> TRIPWIRE_F1 = ModItems.blockItem("tripwire_f1", ModBlocks.TRIPWIRE_F1);
    public static final RegistryObject<Item> BEAR_TRAP = ModItems.blockItem("bear_trap", ModBlocks.BEAR_TRAP);
    public static final RegistryObject<Item> WARNING_SIGN_MINES = ModItems.blockItem("warning_sign_mines", ModBlocks.WARNING_SIGN_MINES);
    public static final RegistryObject<Item> WARNING_SIGN_RADIATION = ModItems.blockItem("warning_sign_radiation", ModBlocks.WARNING_SIGN_RADIATION);
    public static final RegistryObject<Item> WARNING_SIGN_VOLTAGE = ModItems.blockItem("warning_sign_voltage", ModBlocks.WARNING_SIGN_VOLTAGE);
    public static final RegistryObject<Item> WARNING_SIGN_BIOHAZARD = ModItems.blockItem("warning_sign_biohazard", ModBlocks.WARNING_SIGN_BIOHAZARD);
    public static final RegistryObject<Item> WARNING_SIGN_CHECKPOINT = ModItems.blockItem("warning_sign_checkpoint", ModBlocks.WARNING_SIGN_CHECKPOINT);
    public static final RegistryObject<Item> WARNING_SIGN_NOENTRY = ModItems.blockItem("warning_sign_noentry", ModBlocks.WARNING_SIGN_NOENTRY);
    public static final RegistryObject<Item> ENTRENCHING_TOOL = ITEMS.register("entrenching_tool", () -> new ru.aiforge.realwar.field.EntrenchingToolItem(new Item.Properties()));
    // <<< r12 field blocks
    // >>> r14 deco (tools/gen_deco_r14.py)
    public static final RegistryObject<Item> FLAGPOLE = ModItems.blockItem("flagpole", ModBlocks.FLAGPOLE);
    public static final RegistryObject<Item> FLAG_RUSSIA = ModItems.blockItem("flag_russia", ModBlocks.FLAG_RUSSIA);
    public static final RegistryObject<Item> FLAG_USSR = ModItems.blockItem("flag_ussr", ModBlocks.FLAG_USSR);
    public static final RegistryObject<Item> FLAG_GERMANY = ModItems.blockItem("flag_germany", ModBlocks.FLAG_GERMANY);
    public static final RegistryObject<Item> FLAG_USA = ModItems.blockItem("flag_usa", ModBlocks.FLAG_USA);
    public static final RegistryObject<Item> FLAG_UK = ModItems.blockItem("flag_uk", ModBlocks.FLAG_UK);
    public static final RegistryObject<Item> FLAG_FRANCE = ModItems.blockItem("flag_france", ModBlocks.FLAG_FRANCE);
    public static final RegistryObject<Item> FLAG_CHINA = ModItems.blockItem("flag_china", ModBlocks.FLAG_CHINA);
    public static final RegistryObject<Item> FLAG_JAPAN = ModItems.blockItem("flag_japan", ModBlocks.FLAG_JAPAN);
    public static final RegistryObject<Item> FLAG_BALKENKREUZ = ModItems.blockItem("flag_balkenkreuz", ModBlocks.FLAG_BALKENKREUZ);
    public static final RegistryObject<Item> FLAG_VDV = ModItems.blockItem("flag_vdv", ModBlocks.FLAG_VDV);
    public static final RegistryObject<Item> FLAG_VMF = ModItems.blockItem("flag_vmf", ModBlocks.FLAG_VMF);
    public static final RegistryObject<Item> FLAG_VKS = ModItems.blockItem("flag_vks", ModBlocks.FLAG_VKS);
    public static final RegistryObject<Item> FLAG_NATO = ModItems.blockItem("flag_nato", ModBlocks.FLAG_NATO);
    public static final RegistryObject<Item> FLAG_UN = ModItems.blockItem("flag_un", ModBlocks.FLAG_UN);
    public static final RegistryObject<Item> FLAG_WHITE = ModItems.blockItem("flag_white", ModBlocks.FLAG_WHITE);
    public static final RegistryObject<Item> FLAG_VICTORY = ModItems.blockItem("flag_victory", ModBlocks.FLAG_VICTORY);
    public static final RegistryObject<Item> LANTERN_HURRICANE = ModItems.blockItem("lantern_hurricane", ModBlocks.LANTERN_HURRICANE);
    public static final RegistryObject<Item> LAMP_BULKHEAD = ModItems.blockItem("lamp_bulkhead", ModBlocks.LAMP_BULKHEAD);
    public static final RegistryObject<Item> LAMP_STREET = ModItems.blockItem("lamp_street", ModBlocks.LAMP_STREET);
    public static final RegistryObject<Item> LAMP_FLOOD = ModItems.blockItem("lamp_flood", ModBlocks.LAMP_FLOOD);
    public static final RegistryObject<Item> BEACON_RED = ModItems.blockItem("beacon_red", ModBlocks.BEACON_RED);
    public static final RegistryObject<Item> BEACON_ROTATING = ModItems.blockItem("beacon_rotating", ModBlocks.BEACON_ROTATING);
    public static final RegistryObject<Item> SEARCHLIGHT = ModItems.blockItem("searchlight", ModBlocks.SEARCHLIGHT);
    public static final RegistryObject<Item> ARMOR_PLATE = ModItems.blockItem("armor_plate", ModBlocks.ARMOR_PLATE);
    public static final RegistryObject<Item> ARMOR_PLATE_HAZARD = ModItems.blockItem("armor_plate_hazard", ModBlocks.ARMOR_PLATE_HAZARD);
    public static final RegistryObject<Item> ARMOR_PLATE_STAR_RED = ModItems.blockItem("armor_plate_star_red", ModBlocks.ARMOR_PLATE_STAR_RED);
    public static final RegistryObject<Item> ARMOR_PLATE_BALKENKREUZ = ModItems.blockItem("armor_plate_balkenkreuz", ModBlocks.ARMOR_PLATE_BALKENKREUZ);
    public static final RegistryObject<Item> ARMOR_PLATE_US_STAR = ModItems.blockItem("armor_plate_us_star", ModBlocks.ARMOR_PLATE_US_STAR);
    public static final RegistryObject<Item> ARMOR_PLATE_ROUNDEL = ModItems.blockItem("armor_plate_roundel", ModBlocks.ARMOR_PLATE_ROUNDEL);
    public static final RegistryObject<Item> ARMOR_PLATE_VDV = ModItems.blockItem("armor_plate_vdv", ModBlocks.ARMOR_PLATE_VDV);
    public static final RegistryObject<Item> ARMOR_PLATE_VMF = ModItems.blockItem("armor_plate_vmf", ModBlocks.ARMOR_PLATE_VMF);
    // <<< r14 deco
    // >>> r16 doors (tools/gen_doors_r16.py)
    public static final RegistryObject<Item> DOOR_STEEL = ModItems.blockItem("door_steel", ModBlocks.DOOR_STEEL);
    public static final RegistryObject<Item> DOOR_BUNKER = ModItems.blockItem("door_bunker", ModBlocks.DOOR_BUNKER);
    public static final RegistryObject<Item> DOOR_BARS = ModItems.blockItem("door_bars", ModBlocks.DOOR_BARS);
    public static final RegistryObject<Item> DOOR_WOOD_ARMY = ModItems.blockItem("door_wood_army", ModBlocks.DOOR_WOOD_ARMY);
    public static final RegistryObject<Item> DOOR_LAB = ModItems.blockItem("door_lab", ModBlocks.DOOR_LAB);
    public static final RegistryObject<Item> HATCH_STEEL = ModItems.blockItem("hatch_steel", ModBlocks.HATCH_STEEL);
    public static final RegistryObject<Item> HATCH_BUNKER = ModItems.blockItem("hatch_bunker", ModBlocks.HATCH_BUNKER);
    public static final RegistryObject<Item> HATCH_GRATE = ModItems.blockItem("hatch_grate", ModBlocks.HATCH_GRATE);
    public static final RegistryObject<Item> GATE_SLIDING = ModItems.blockItem("gate_sliding", ModBlocks.GATE_SLIDING);
    public static final RegistryObject<Item> GATE_SWING = ModItems.blockItem("gate_swing", ModBlocks.GATE_SWING);
    public static final RegistryObject<Item> GATE_BOOM = ModItems.blockItem("gate_boom", ModBlocks.GATE_BOOM);
    public static final RegistryObject<Item> GATE_BLAST = ModItems.blockItem("gate_blast", ModBlocks.GATE_BLAST);
    public static final RegistryObject<Item> CAMO_SCREEN_FOREST = ModItems.blockItem("camo_screen_forest", ModBlocks.CAMO_SCREEN_FOREST);
    public static final RegistryObject<Item> CAMO_CANOPY_FOREST = ModItems.blockItem("camo_canopy_forest", ModBlocks.CAMO_CANOPY_FOREST);
    public static final RegistryObject<Item> CAMO_BUSH_FOREST = ModItems.blockItem("camo_bush_forest", ModBlocks.CAMO_BUSH_FOREST);
    public static final RegistryObject<Item> CAMO_SCREEN_DESERT = ModItems.blockItem("camo_screen_desert", ModBlocks.CAMO_SCREEN_DESERT);
    public static final RegistryObject<Item> CAMO_CANOPY_DESERT = ModItems.blockItem("camo_canopy_desert", ModBlocks.CAMO_CANOPY_DESERT);
    public static final RegistryObject<Item> CAMO_BUSH_DRY = ModItems.blockItem("camo_bush_dry", ModBlocks.CAMO_BUSH_DRY);
    public static final RegistryObject<Item> CAMO_SCREEN_WINTER = ModItems.blockItem("camo_screen_winter", ModBlocks.CAMO_SCREEN_WINTER);
    public static final RegistryObject<Item> CAMO_CANOPY_WINTER = ModItems.blockItem("camo_canopy_winter", ModBlocks.CAMO_CANOPY_WINTER);
    public static final RegistryObject<Item> CAMO_BUSH_SNOW = ModItems.blockItem("camo_bush_snow", ModBlocks.CAMO_BUSH_SNOW);
    // <<< r16 doors
    // >>> r17 (tools/gen_wrecks_r17.py)
    public static final RegistryObject<Item> WRECK_TANK_HULL = ModItems.blockItem("wreck_tank_hull", ModBlocks.WRECK_TANK_HULL);
    public static final RegistryObject<Item> WRECK_TANK_TURRET = ModItems.blockItem("wreck_tank_turret", ModBlocks.WRECK_TANK_TURRET);
    public static final RegistryObject<Item> WRECK_TANK_TRACK = ModItems.blockItem("wreck_tank_track", ModBlocks.WRECK_TANK_TRACK);
    public static final RegistryObject<Item> WRECK_HELI_BODY = ModItems.blockItem("wreck_heli_body", ModBlocks.WRECK_HELI_BODY);
    public static final RegistryObject<Item> WRECK_HELI_TAIL = ModItems.blockItem("wreck_heli_tail", ModBlocks.WRECK_HELI_TAIL);
    public static final RegistryObject<Item> WRECK_HELI_ROTOR = ModItems.blockItem("wreck_heli_rotor", ModBlocks.WRECK_HELI_ROTOR);
    public static final RegistryObject<Item> WRECK_JET_BODY = ModItems.blockItem("wreck_jet_body", ModBlocks.WRECK_JET_BODY);
    public static final RegistryObject<Item> WRECK_JET_WING = ModItems.blockItem("wreck_jet_wing", ModBlocks.WRECK_JET_WING);
    public static final RegistryObject<Item> WRECK_JET_TAIL = ModItems.blockItem("wreck_jet_tail", ModBlocks.WRECK_JET_TAIL);
    public static final RegistryObject<Item> CRATE_AMMO = ModItems.blockItem("crate_ammo", ModBlocks.CRATE_AMMO);
    public static final RegistryObject<Item> CRATE_MEDICAL = ModItems.blockItem("crate_medical", ModBlocks.CRATE_MEDICAL);
    public static final RegistryObject<Item> CRATE_MILITARY = ModItems.blockItem("crate_military", ModBlocks.CRATE_MILITARY);
    public static final RegistryObject<Item> CRATE_WEAPONS = ModItems.blockItem("crate_weapons", ModBlocks.CRATE_WEAPONS);
    public static final RegistryObject<Item> CRATE_SECRET = ModItems.blockItem("crate_secret", ModBlocks.CRATE_SECRET);
    public static final RegistryObject<Item> TRAP_PUNJI = ModItems.blockItem("trap_punji", ModBlocks.TRAP_PUNJI);
    public static final RegistryObject<Item> TRAP_SPIKE_STRIP = ModItems.blockItem("trap_spike_strip", ModBlocks.TRAP_SPIKE_STRIP);
    public static final RegistryObject<Item> TRAP_FLARE = ModItems.blockItem("trap_flare", ModBlocks.TRAP_FLARE);
    public static final RegistryObject<Item> TRAP_IED = ModItems.blockItem("trap_ied", ModBlocks.TRAP_IED);
    public static final RegistryObject<Item> TRAP_ELECTRIC_FENCE = ModItems.blockItem("trap_electric_fence", ModBlocks.TRAP_ELECTRIC_FENCE);
    public static final RegistryObject<Item> RATION_IRP = ITEMS.register("ration_irp", () -> new ru.aiforge.realwar.item.TooltipItem(new Item.Properties().stacksTo(16).food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(10).saturationMod(0.8f).build())));
    public static final RegistryObject<Item> BANDAGE = ITEMS.register("bandage", () -> new ru.aiforge.realwar.field.MedicalItem(false, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> FIRST_AID_KIT = ITEMS.register("first_aid_kit", () -> new ru.aiforge.realwar.field.MedicalItem(true, new Item.Properties().stacksTo(8)));
    // <<< r17
    public static final RegistryObject<Item> STEEL_BLOCK = ModItems.blockItem("steel_block", ModBlocks.STEEL_BLOCK);
    public static final RegistryObject<Item> REINFORCED_CONCRETE = ModItems.blockItem("reinforced_concrete", ModBlocks.REINFORCED_CONCRETE);
    public static final RegistryObject<Item> SCORCHED_EARTH = ModItems.blockItem("scorched_earth", ModBlocks.SCORCHED_EARTH);
    public static final RegistryObject<Item> TRINITITE = ModItems.blockItem("trinitite", ModBlocks.TRINITITE);
    public static final RegistryObject<Item> GRAD_VEHICLE = ITEMS.register("grad_vehicle", () -> new VehicleItem(VehicleKind.GRAD, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SMERCH_VEHICLE = ITEMS.register("smerch_vehicle", () -> new VehicleItem(VehicleKind.SMERCH, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> S400_VEHICLE = ITEMS.register("s400_vehicle", () -> new VehicleItem(VehicleKind.S400, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> RADAR_MOBILE_VEHICLE = ITEMS.register("radar_mobile_vehicle", () -> new VehicleItem(VehicleKind.RADAR_MOBILE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> RADAR_POST_VEHICLE = ITEMS.register("radar_post_vehicle", () -> new VehicleItem(VehicleKind.RADAR_POST, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ZU23_VEHICLE = ITEMS.register("zu23_vehicle", () -> new VehicleItem(VehicleKind.ZU23, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> T90M_VEHICLE = ITEMS.register("t90m_vehicle", () -> new VehicleItem(VehicleKind.T90M, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ABRAMS_VEHICLE = ITEMS.register("abrams_vehicle", () -> new VehicleItem(VehicleKind.ABRAMS, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BMP3_VEHICLE = ITEMS.register("bmp3_vehicle", () -> new VehicleItem(VehicleKind.BMP3, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MSTA_VEHICLE = ITEMS.register("msta_vehicle", () -> new VehicleItem(VehicleKind.MSTA, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> D30_VEHICLE = ITEMS.register("d30_vehicle", () -> new VehicleItem(VehicleKind.D30, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> M777_VEHICLE = ITEMS.register("m777_vehicle", () -> new VehicleItem(VehicleKind.M777, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PZ2_VEHICLE = ITEMS.register("pz2_vehicle", () -> new VehicleItem(VehicleKind.PZ2, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PZ4_VEHICLE = ITEMS.register("pz4_vehicle", () -> new VehicleItem(VehicleKind.PZ4, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TIGER_VEHICLE = ITEMS.register("tiger_vehicle", () -> new VehicleItem(VehicleKind.TIGER, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> STURMTIGER_VEHICLE = ITEMS.register("sturmtiger_vehicle", () -> new VehicleItem(VehicleKind.STURMTIGER, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MAUS_VEHICLE = ITEMS.register("maus_vehicle", () -> new VehicleItem(VehicleKind.MAUS, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> RATTE_VEHICLE = ITEMS.register("ratte_vehicle", () -> new VehicleItem(VehicleKind.RATTE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> T34_VEHICLE = ITEMS.register("t34_vehicle", () -> new VehicleItem(VehicleKind.T34, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> KV2_VEHICLE = ITEMS.register("kv2_vehicle", () -> new VehicleItem(VehicleKind.KV2, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> IS2_VEHICLE = ITEMS.register("is2_vehicle", () -> new VehicleItem(VehicleKind.IS2, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> KV5_VEHICLE = ITEMS.register("kv5_vehicle", () -> new VehicleItem(VehicleKind.KV5, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BM13_VEHICLE = ITEMS.register("bm13_vehicle", () -> new VehicleItem(VehicleKind.BM13, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BRADLEY_VEHICLE = ITEMS.register("bradley_vehicle", () -> new VehicleItem(VehicleKind.BRADLEY, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TOS1A_VEHICLE = ITEMS.register("tos1a_vehicle", () -> new VehicleItem(VehicleKind.TOS1A, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> URAL_VEHICLE = ITEMS.register("ural_vehicle", () -> new VehicleItem(VehicleKind.URAL, new Item.Properties().stacksTo(1)));
    // WWI Mark I item is appended with the model and double-sponson simulation.
    public static final RegistryObject<Item> MARK_I_VEHICLE = ITEMS.register("mark_i_vehicle", () -> new VehicleItem(VehicleKind.MARK_I, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SHELLS_20 = ModItems.item("shells_20", 0.0, 16);
    public static final RegistryObject<Item> SHELLS_57 = ModItems.item("shells_57", 0.0, 8);
    public static final RegistryObject<Item> SHELLS_75 = ModItems.item("shells_75", 0.0, 16);
    public static final RegistryObject<Item> SHELLS_88 = ModItems.item("shells_88", 0.0, 16);
    public static final RegistryObject<Item> SHELLS_85 = ModItems.item("shells_85", 0.0, 16);
    public static final RegistryObject<Item> SHELLS_107 = ModItems.item("shells_107", 0.0, 16);
    public static final RegistryObject<Item> SHELLS_128 = ModItems.item("shells_128", 0.0, 8);
    public static final RegistryObject<Item> SHELLS_280 = ModItems.item("shells_280", 0.0, 4);
    public static final RegistryObject<Item> ROCKET_380 = ModItems.item("rocket_380", 0.0, 2);
    public static final RegistryObject<Item> ROCKET_M13 = ModItems.item("rocket_m13", 0.0, 16);
    public static final RegistryObject<Item> SHELLS_125 = ModItems.item("shells_125", 0.0, 16);
    public static final RegistryObject<Item> SHELLS_120 = ModItems.item("shells_120", 0.0, 16);
    public static final RegistryObject<Item> SHELLS_100 = ModItems.item("shells_100", 0.0, 16);
    public static final RegistryObject<Item> SHELLS_122 = ModItems.item("shells_122", 0.0, 16);
    public static final RegistryObject<Item> SHELLS_152 = ModItems.item("shells_152", 0.0, 8);
    public static final RegistryObject<Item> SHELLS_155 = ModItems.item("shells_155", 0.0, 8);
    public static final RegistryObject<Item> SHELLS_25 = ModItems.item("shells_25", 0.0, 16);
    public static final RegistryObject<Item> ROCKET_220 = ModItems.item("rocket_220", 0.0, 4);
    public static final RegistryObject<Item> GRAD_ROCKET = ModItems.item("grad_rocket", 0.0, 16);
    public static final RegistryObject<Item> SMERCH_ROCKET = ModItems.item("smerch_rocket", 0.0, 4);
    public static final RegistryObject<Item> SAM_48N6 = ModItems.item("sam_48n6", 0.0, 2);
    public static final RegistryObject<Item> FLAK_AMMO_BOX = ModItems.item("flak_ammo_box", 0.0, 8);
    public static final RegistryObject<Item> PROPELLANT_GRAIN = ModItems.item("propellant_grain", 0.0, 64);
    public static final RegistryObject<Item> ROCKET_MOTOR_122 = ModItems.item("rocket_motor_122", 0.0, 16);
    public static final RegistryObject<Item> ROCKET_MOTOR_300 = ModItems.item("rocket_motor_300", 0.0, 8);
    public static final RegistryObject<Item> WARHEAD_122 = ModItems.item("warhead_122", 0.0, 16);
    public static final RegistryObject<Item> WARHEAD_300 = ModItems.item("warhead_300", 0.0, 8);
    public static final RegistryObject<Item> CONTACT_FUZE = ModItems.item("contact_fuze", 0.0, 64);
    public static final RegistryObject<Item> PROXIMITY_FUZE = ModItems.item("proximity_fuze", 0.0, 64);
    public static final RegistryObject<Item> RADAR_SEEKER = ModItems.item("radar_seeker", 0.0, 8);
    public static final RegistryObject<Item> GUIDANCE_UNIT = ModItems.item("guidance_unit", 0.0, 8);
    public static final RegistryObject<Item> SAM_MOTOR = ModItems.item("sam_motor", 0.0, 4);
    public static final RegistryObject<Item> SAM_WARHEAD = ModItems.item("sam_warhead", 0.0, 4);
    public static final RegistryObject<Item> DIESEL_ENGINE = ModItems.item("diesel_engine", 0.0, 4);
    public static final RegistryObject<Item> TRUCK_WHEEL = ModItems.item("truck_wheel", 0.0, 64);
    public static final RegistryObject<Item> HYDRAULIC_DRIVE = ModItems.item("hydraulic_drive", 0.0, 64);
    public static final RegistryObject<Item> CHASSIS_6X6 = ModItems.item("chassis_6x6", 0.0, 1);
    public static final RegistryObject<Item> CHASSIS_8X8 = ModItems.item("chassis_8x8", 0.0, 1);
    public static final RegistryObject<Item> ARMORED_CAB = ModItems.item("armored_cab", 0.0, 4);
    public static final RegistryObject<Item> LAUNCHER_PACKAGE_122 = ModItems.item("launcher_package_122", 0.0, 2);
    public static final RegistryObject<Item> LAUNCHER_PACKAGE_300 = ModItems.item("launcher_package_300", 0.0, 2);
    public static final RegistryObject<Item> SAM_LAUNCHER_FRAME = ModItems.item("sam_launcher_frame", 0.0, 2);
    public static final RegistryObject<Item> GUN_MOUNT_23 = ModItems.item("gun_mount_23", 0.0, 2);
    public static final RegistryObject<Item> GUN_BARRELS_23 = ModItems.item("gun_barrels_23", 0.0, 4);
    public static final RegistryObject<Item> RADIO = ITEMS.register("radio", () -> new ru.aiforge.realwar.item.RadioItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ARTILLERY_DESIGNATOR = ITEMS.register("artillery_designator", () -> new DesignatorItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TU95_VEHICLE = ITEMS.register("tu95_vehicle", () -> new AircraftItem(new Item.Properties()));
    private static final Map<AirBombType, RegistryObject<Item>> AIR_BOMBS = new EnumMap<AirBombType, RegistryObject<Item>>(AirBombType.class);

    private ModItems() {
    }

    private static RegistryObject<Item> item(String name, double radioactivity, int stack) {
        return ITEMS.register(name, () -> new TooltipItem(new Item.Properties().stacksTo(stack), radioactivity));
    }

    private static RegistryObject<Item> item(String name) {
        return ModItems.item(name, 0.0, 64);
    }

    private static RegistryObject<Item> blockItem(String name, RegistryObject<Block> block) {
        return ITEMS.register(name, () -> new TooltipBlockItem((Block)block.get(), new Item.Properties()));
    }

    public static RegistryObject<Item> airBomb(AirBombType t) {
        return AIR_BOMBS.get(t);
    }

    public static RegistryObject<Item> vehicleItem(VehicleKind k) {
        return switch (k) {
            default -> throw new IncompatibleClassChangeError();
            case GRAD -> GRAD_VEHICLE;
            case SMERCH -> SMERCH_VEHICLE;
            case S400 -> S400_VEHICLE;
            case ZU23 -> ZU23_VEHICLE;
            case RADAR_MOBILE -> RADAR_MOBILE_VEHICLE;
            case RADAR_POST -> RADAR_POST_VEHICLE;
            case YARS -> ru.aiforge.arsenal.registry.ArsenalRegistry.UNITS.get(ru.aiforge.arsenal.core.Kind.YARS_TEL);
            case T90M -> T90M_VEHICLE;
            case ABRAMS -> ABRAMS_VEHICLE;
            case BMP3 -> BMP3_VEHICLE;
            case MSTA -> MSTA_VEHICLE;
            case D30 -> D30_VEHICLE;
            case M777 -> M777_VEHICLE;
            case PZ2 -> PZ2_VEHICLE;
            case PZ4 -> PZ4_VEHICLE;
            case TIGER -> TIGER_VEHICLE;
            case STURMTIGER -> STURMTIGER_VEHICLE;
            case MAUS -> MAUS_VEHICLE;
            case RATTE -> RATTE_VEHICLE;
            case T34 -> T34_VEHICLE;
            case KV2 -> KV2_VEHICLE;
            case IS2 -> IS2_VEHICLE;
            case KV5 -> KV5_VEHICLE;
            case BM13 -> BM13_VEHICLE;
            case BRADLEY -> BRADLEY_VEHICLE;
            case TOS1A -> TOS1A_VEHICLE;
            case URAL -> URAL_VEHICLE;
            case MARK_I -> MARK_I_VEHICLE;
        };
    }

    static {
        for (AirBombType t : AirBombType.values()) {
            AIR_BOMBS.put(t, ITEMS.<Item>register(t.id, () -> new AirBombItem(t, new Item.Properties())));
        }
    }

    /** r15: 10 uniform sets x 4 pieces, ids uniform_<set>_<head|chest|legs|feet>. */
    public static final java.util.Map<String, RegistryObject<Item>> UNIFORMS = new java.util.LinkedHashMap<>();

    static {
        for (String set : ru.aiforge.realwar.item.UniformItem.SETS) {
            for (ArmorItem.Type t : ArmorItem.Type.values()) {
                String id = "uniform_" + set + "_" + ru.aiforge.realwar.item.UniformItem.slotName(t);
                UNIFORMS.put(id, ITEMS.register(id, () -> new ru.aiforge.realwar.item.UniformItem(set, t, new Item.Properties())));
            }
        }
    }
}
