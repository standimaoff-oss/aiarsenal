/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.entity.BlockEntityType
 *  net.minecraft.world.level.block.entity.BlockEntityType$Builder
 *  net.minecraftforge.registries.DeferredRegister
 *  net.minecraftforge.registries.ForgeRegistries
 *  net.minecraftforge.registries.IForgeRegistry
 *  net.minecraftforge.registries.RegistryObject
 */
package ru.aiforge.realwar.registry;

import ru.aiforge.realwar.blockentity.BombBlockEntity;
import ru.aiforge.realwar.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create((IForgeRegistry)ForgeRegistries.BLOCK_ENTITY_TYPES, (String)"aiarsenal");
    public static final RegistryObject<BlockEntityType<BombBlockEntity>> BOMB = BLOCK_ENTITIES.register("bomb", () -> BlockEntityType.Builder.of(BombBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.LITTLE_BOY.get(), (Block)ModBlocks.RDS_4.get()}).build(null));
    // r14 deco
    public static final RegistryObject<BlockEntityType<ru.aiforge.realwar.deco.FlagBlockEntity>> FLAG = BLOCK_ENTITIES.register("flag", () -> BlockEntityType.Builder.of(ru.aiforge.realwar.deco.FlagBlockEntity::new, new Block[]{ModBlocks.FLAG_RUSSIA.get(), ModBlocks.FLAG_USSR.get(), ModBlocks.FLAG_GERMANY.get(), ModBlocks.FLAG_USA.get(), ModBlocks.FLAG_UK.get(), ModBlocks.FLAG_FRANCE.get(), ModBlocks.FLAG_CHINA.get(), ModBlocks.FLAG_JAPAN.get(), ModBlocks.FLAG_BALKENKREUZ.get(), ModBlocks.FLAG_VDV.get(), ModBlocks.FLAG_VMF.get(), ModBlocks.FLAG_VKS.get(), ModBlocks.FLAG_NATO.get(), ModBlocks.FLAG_UN.get(), ModBlocks.FLAG_WHITE.get(), ModBlocks.FLAG_VICTORY.get()}).build(null));
    public static final RegistryObject<BlockEntityType<ru.aiforge.realwar.deco.BeaconLampBlockEntity>> BEACON_LAMP = BLOCK_ENTITIES.register("beacon_lamp", () -> BlockEntityType.Builder.of(ru.aiforge.realwar.deco.BeaconLampBlockEntity::new, new Block[]{ModBlocks.BEACON_RED.get(), ModBlocks.BEACON_ROTATING.get()}).build(null));
    public static final RegistryObject<BlockEntityType<ru.aiforge.realwar.deco.SearchlightBlockEntity>> SEARCHLIGHT = BLOCK_ENTITIES.register("searchlight", () -> BlockEntityType.Builder.of(ru.aiforge.realwar.deco.SearchlightBlockEntity::new, new Block[]{ModBlocks.SEARCHLIGHT.get()}).build(null));
    // >>> r16 doors (tools/gen_doors_r16.py)
    public static final RegistryObject<BlockEntityType<ru.aiforge.realwar.deco.AnimDoorBlockEntity>> ANIM_DOOR = BLOCK_ENTITIES.register("anim_door", () -> BlockEntityType.Builder.of(ru.aiforge.realwar.deco.AnimDoorBlockEntity::new, new Block[]{ModBlocks.DOOR_STEEL.get(), ModBlocks.DOOR_BUNKER.get(), ModBlocks.DOOR_BARS.get(), ModBlocks.DOOR_WOOD_ARMY.get(), ModBlocks.DOOR_LAB.get(), ModBlocks.HATCH_STEEL.get(), ModBlocks.HATCH_BUNKER.get(), ModBlocks.HATCH_GRATE.get()}).build(null));
    public static final RegistryObject<BlockEntityType<ru.aiforge.realwar.deco.GateBlockEntity>> GATE = BLOCK_ENTITIES.register("gate", () -> BlockEntityType.Builder.of(ru.aiforge.realwar.deco.GateBlockEntity::new, new Block[]{ModBlocks.GATE_SLIDING.get(), ModBlocks.GATE_SWING.get(), ModBlocks.GATE_BOOM.get(), ModBlocks.GATE_BLAST.get()}).build(null));
    // <<< r16 doors
    // >>> r17 (tools/gen_wrecks_r17.py)
    public static final RegistryObject<BlockEntityType<ru.aiforge.realwar.deco.WreckBlockEntity>> WRECK = BLOCK_ENTITIES.register("wreck", () -> BlockEntityType.Builder.of(ru.aiforge.realwar.deco.WreckBlockEntity::new, new Block[]{ModBlocks.WRECK_TANK_HULL.get(), ModBlocks.WRECK_TANK_TURRET.get(), ModBlocks.WRECK_TANK_TRACK.get(), ModBlocks.WRECK_HELI_BODY.get(), ModBlocks.WRECK_HELI_TAIL.get(), ModBlocks.WRECK_HELI_ROTOR.get(), ModBlocks.WRECK_JET_BODY.get(), ModBlocks.WRECK_JET_WING.get(), ModBlocks.WRECK_JET_TAIL.get()}).build(null));
    public static final RegistryObject<BlockEntityType<ru.aiforge.realwar.deco.CrateBlockEntity>> CRATE = BLOCK_ENTITIES.register("crate", () -> BlockEntityType.Builder.of(ru.aiforge.realwar.deco.CrateBlockEntity::new, new Block[]{ModBlocks.CRATE_AMMO.get(), ModBlocks.CRATE_MEDICAL.get(), ModBlocks.CRATE_MILITARY.get(), ModBlocks.CRATE_WEAPONS.get(), ModBlocks.CRATE_SECRET.get()}).build(null));
    // <<< r17

    private ModBlockEntities() {
    }
}

