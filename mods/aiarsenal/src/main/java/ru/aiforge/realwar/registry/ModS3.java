/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.EntityType$Builder
 *  net.minecraft.world.entity.MobCategory
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.SoundType
 *  net.minecraft.world.level.block.entity.BlockEntityType
 *  net.minecraft.world.level.block.entity.BlockEntityType$Builder
 *  net.minecraft.world.level.block.state.BlockBehaviour$Properties
 *  net.minecraft.world.level.material.MapColor
 *  net.minecraft.world.level.material.PushReaction
 *  net.minecraftforge.registries.DeferredRegister
 *  net.minecraftforge.registries.ForgeRegistries
 *  net.minecraftforge.registries.IForgeRegistry
 *  net.minecraftforge.registries.RegistryObject
 */
package ru.aiforge.realwar.registry;

import ru.aiforge.realwar.missile.GlbBlockItem;
import ru.aiforge.realwar.missile.LaunchComputerBlock;
import ru.aiforge.realwar.missile.LaunchComputerBlockEntity;
import ru.aiforge.realwar.missile.LaunchPadBlock;
import ru.aiforge.realwar.missile.LaunchPadBlockEntity;
import ru.aiforge.realwar.missile.LaunchPadItem;
import ru.aiforge.realwar.missile.LaunchPadPartBlock;
import ru.aiforge.realwar.missile.MissileEntity;
import ru.aiforge.realwar.missile.MissileItem;
import ru.aiforge.realwar.missile.MissileType;
import ru.aiforge.realwar.missile.SirenBlock;
import ru.aiforge.realwar.missile.SirenBlockEntity;
import ru.aiforge.realwar.registry.ModSounds;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

public final class ModS3 {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create((IForgeRegistry)ForgeRegistries.BLOCKS, (String)"aiarsenal");
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create((IForgeRegistry)ForgeRegistries.ITEMS, (String)"aiarsenal");
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create((IForgeRegistry)ForgeRegistries.BLOCK_ENTITY_TYPES, (String)"aiarsenal");
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create((IForgeRegistry)ForgeRegistries.ENTITY_TYPES, (String)"aiarsenal");
    public static final RegistryObject<LaunchPadBlock> PAD_1 = BLOCKS.register("launch_pad_1", () -> new LaunchPadBlock(1, ModS3.concrete()));
    public static final RegistryObject<LaunchPadBlock> PAD_2 = BLOCKS.register("launch_pad_2", () -> new LaunchPadBlock(2, ModS3.concrete()));
    public static final RegistryObject<LaunchPadBlock> PAD_3 = BLOCKS.register("launch_pad_3", () -> new LaunchPadBlock(3, ModS3.concrete()));
    public static final RegistryObject<LaunchPadPartBlock> PAD_PART = BLOCKS.register("launch_pad_part", () -> new LaunchPadPartBlock(ModS3.concrete().noLootTable()));
    public static final RegistryObject<LaunchComputerBlock> COMPUTER = BLOCKS.register("launch_computer", () -> new LaunchComputerBlock(ModS3.metal(4.0f)));
    public static final RegistryObject<SirenBlock> SIREN = BLOCKS.register("siren", () -> new SirenBlock(ModS3.metal(3.0f)));
    public static final RegistryObject<Item> PAD_1_ITEM = ITEMS.register("launch_pad_1", () -> new LaunchPadItem((LaunchPadBlock)((PAD_1.get())), new Item.Properties()));
    public static final RegistryObject<Item> PAD_2_ITEM = ITEMS.register("launch_pad_2", () -> new LaunchPadItem((LaunchPadBlock)((PAD_2.get())), new Item.Properties()));
    public static final RegistryObject<Item> PAD_3_ITEM = ITEMS.register("launch_pad_3", () -> new LaunchPadItem((LaunchPadBlock)((PAD_3.get())), new Item.Properties()));
    public static final RegistryObject<Item> COMPUTER_ITEM = ITEMS.register("launch_computer", () -> new GlbBlockItem((Block)COMPUTER.get(), new Item.Properties()));
    public static final RegistryObject<Item> SIREN_ITEM = ITEMS.register("siren", () -> new GlbBlockItem((Block)SIREN.get(), new Item.Properties()));
    private static final Map<MissileType, RegistryObject<Item>> MISSILES = new EnumMap<MissileType, RegistryObject<Item>>(MissileType.class);
    public static final RegistryObject<BlockEntityType<LaunchPadBlockEntity>> PAD_BE;
    public static final RegistryObject<BlockEntityType<LaunchComputerBlockEntity>> COMPUTER_BE;
    public static final RegistryObject<BlockEntityType<SirenBlockEntity>> SIREN_BE;
    public static final RegistryObject<EntityType<MissileEntity>> MISSILE;
    public static final RegistryObject<SoundEvent> SIREN_WAIL;

    private ModS3() {
    }

    private static BlockBehaviour.Properties metal(float hard) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(hard, 40.0f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK);
    }

    private static BlockBehaviour.Properties concrete() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(8.0f, 60.0f).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK);
    }

    public static RegistryObject<Item> missileItem(MissileType t) {
        return MISSILES.get(t);
    }

    static {
        for (MissileType t : MissileType.values()) {
            MISSILES.put(t, ITEMS.<Item>register(t.itemName(), () -> new MissileItem(t, new Item.Properties())));
        }
        PAD_BE = BLOCK_ENTITIES.register("launch_pad", () -> BlockEntityType.Builder.of(LaunchPadBlockEntity::new, (Block[])new Block[]{(Block)PAD_1.get(), (Block)PAD_2.get(), (Block)PAD_3.get()}).build(null));
        COMPUTER_BE = BLOCK_ENTITIES.register("launch_computer", () -> BlockEntityType.Builder.of(LaunchComputerBlockEntity::new, (Block[])new Block[]{(Block)COMPUTER.get()}).build(null));
        SIREN_BE = BLOCK_ENTITIES.register("siren", () -> BlockEntityType.Builder.of(SirenBlockEntity::new, (Block[])new Block[]{(Block)SIREN.get()}).build(null));
        MISSILE = ENTITIES.register("rw_missile", () -> EntityType.Builder.of(MissileEntity::new, (MobCategory)MobCategory.MISC).sized(0.6f, 0.6f).clientTrackingRange(64).updateInterval(1).fireImmune().noSummon().build("missile"));
        SIREN_WAIL = ModSounds.SIREN_WAIL;
    }
}

