package ru.aiforge.tubombers.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.aiforge.tubombers.TuBombers;
import ru.aiforge.tubombers.aircraft.AircraftType;
import ru.aiforge.tubombers.aircraft.BombType;
import ru.aiforge.tubombers.entity.BombEntity;
import ru.aiforge.tubombers.entity.BomberEntity;
import ru.aiforge.tubombers.item.AircraftItem;
import ru.aiforge.tubombers.item.BombItem;

import java.util.EnumMap;
import java.util.Map;

/** All DeferredRegisters of the mod. */
public final class ModRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TuBombers.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TuBombers.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, TuBombers.MODID);
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, TuBombers.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TuBombers.MODID);

    public static final RegistryObject<net.minecraft.core.particles.SimpleParticleType> P_SMOKE_WALL = ru.aiforge.bm21grad.registry.ModRegistry.P_SMOKE_WALL; // shared with the BM-21 registry
    public static final RegistryObject<net.minecraft.core.particles.SimpleParticleType> P_FIREBALL = ru.aiforge.bm21grad.registry.ModRegistry.P_FIREBALL; // shared with the BM-21 registry
    public static final RegistryObject<net.minecraft.core.particles.SimpleParticleType> P_BLAST_SMOKE = ru.aiforge.bm21grad.registry.ModRegistry.P_BLAST_SMOKE; // shared with the BM-21 registry

    // ------------------------------------------------------------------ entities
    public static final RegistryObject<EntityType<BomberEntity>> TU95 = ENTITIES.register("tu95ms", () ->
            EntityType.Builder.<BomberEntity>of(BomberEntity::new, MobCategory.MISC)
                    .sized(3.0f, 3.0f).clientTrackingRange(32).updateInterval(1).fireImmune()
                    .build(TuBombers.MODID + ":tu95ms"));
    public static final RegistryObject<EntityType<BomberEntity>> TU160 = ENTITIES.register("tu160", () ->
            EntityType.Builder.<BomberEntity>of(BomberEntity::new, MobCategory.MISC)
                    .sized(3.0f, 3.0f).clientTrackingRange(32).updateInterval(1).fireImmune()
                    .build(TuBombers.MODID + ":tu160"));
    public static final RegistryObject<EntityType<BombEntity>> BOMB = ENTITIES.register("bomb", () ->
            EntityType.Builder.<BombEntity>of(BombEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f).clientTrackingRange(24).updateInterval(2).fireImmune().noSummon()
                    .build(TuBombers.MODID + ":bomb"));

    // ------------------------------------------------------------------ items
    public static final RegistryObject<Item> TU95_ITEM = ITEMS.register("tu95ms", () ->
            new AircraftItem(AircraftType.TU95, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> TU160_ITEM = ITEMS.register("tu160", () ->
            new AircraftItem(AircraftType.TU160, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final Map<BombType, RegistryObject<Item>> BOMBS = new EnumMap<>(BombType.class);

    static {
        for (BombType t : BombType.values())
            BOMBS.put(t, ITEMS.register(t.id, () -> new BombItem(t, new Item.Properties()
                    .stacksTo(t.nuclear() ? 1 : t.missile() ? 6 : t == BombType.FAB1500 ? 4 : 16)
                    .rarity(t.nuclear() ? Rarity.EPIC : t.missile() ? Rarity.RARE : Rarity.COMMON))));
    }

    public static Item bombItem(BombType t) {
        RegistryObject<Item> r = BOMBS.get(t);
        return r == null ? null : r.get();
    }

    // ------------------------------------------------------------------ sounds
    public static final RegistryObject<SoundEvent> SND_TU95_ENGINE = sound("tu95_engine");
    public static final RegistryObject<SoundEvent> SND_TU160_ENGINE = sound("tu160_engine");
    public static final RegistryObject<SoundEvent> SND_AFTERBURNER = sound("afterburner");
    public static final RegistryObject<SoundEvent> SND_TU95_CABIN = sound("tu95_cabin");
    public static final RegistryObject<SoundEvent> SND_TU160_CABIN = sound("tu160_cabin");
    public static final RegistryObject<SoundEvent> SND_WIND = sound("wind");
    public static final RegistryObject<SoundEvent> SND_GEAR = sound("gear");
    public static final RegistryObject<SoundEvent> SND_BAY = sound("bay_doors");
    public static final RegistryObject<SoundEvent> SND_HYDRAULICS = ru.aiforge.bm21grad.registry.ModRegistry.SND_HYDRAULICS;
    public static final RegistryObject<SoundEvent> SND_RELEASE = sound("bomb_release");
    public static final RegistryObject<SoundEvent> SND_WHISTLE = sound("bomb_whistle");
    public static final RegistryObject<SoundEvent> SND_WARNING = sound("warning");
    public static final RegistryObject<SoundEvent> SND_EXPLOSION_NEAR = ru.aiforge.bm21grad.registry.ModRegistry.SND_EXPLOSION_NEAR;
    public static final RegistryObject<SoundEvent> SND_EXPLOSION_FAR = ru.aiforge.bm21grad.registry.ModRegistry.SND_EXPLOSION_FAR;

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(TuBombers.MODID, name)));
    }

    // creative tabs: one shared set in ru.aiforge.realwar.registry.ModTabs (Техника / Дроны / Ядерное оружие / Ракеты / Боеприпасы ...)

    private ModRegistry() {
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        ITEMS.register(bus);
        SOUNDS.register(bus);
        TABS.register(bus);
    }
}
