package ru.aiforge.bm21grad.registry;

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
import ru.aiforge.bm21grad.Bm21Grad;
import ru.aiforge.bm21grad.ammo.RocketType;
import ru.aiforge.bm21grad.entity.Bm21Entity;
import ru.aiforge.bm21grad.entity.RocketEntity;
import ru.aiforge.bm21grad.entity.SmokeScreenEntity;
import ru.aiforge.bm21grad.entity.SubmunitionEntity;
import ru.aiforge.bm21grad.item.Bm21SpawnerItem;
import ru.aiforge.bm21grad.item.RangefinderItem;
import ru.aiforge.bm21grad.item.RocketItem;

import java.util.EnumMap;
import java.util.Map;

/** All DeferredRegisters of the mod. */
public final class ModRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Bm21Grad.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Bm21Grad.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Bm21Grad.MODID);
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Bm21Grad.MODID);
    /** Large billboard puffs (3-6 blocks) for smoke screens, fireballs and blast smoke - vanilla particles are too small for 122 mm effects. */
    public static final RegistryObject<net.minecraft.core.particles.SimpleParticleType> P_SMOKE_WALL = PARTICLES.register("smoke_wall", () -> new net.minecraft.core.particles.SimpleParticleType(true));
    public static final RegistryObject<net.minecraft.core.particles.SimpleParticleType> P_FIREBALL = PARTICLES.register("fireball", () -> new net.minecraft.core.particles.SimpleParticleType(true));
    public static final RegistryObject<net.minecraft.core.particles.SimpleParticleType> P_BLAST_SMOKE = PARTICLES.register("blast_smoke", () -> new net.minecraft.core.particles.SimpleParticleType(true));
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Bm21Grad.MODID);

    // ------------------------------------------------------------------ entities
    public static final RegistryObject<EntityType<Bm21Entity>> BM21 = ENTITIES.register("bm21", () ->
            EntityType.Builder.<Bm21Entity>of(Bm21Entity::new, MobCategory.MISC)
                    .sized(2.9f, 3.0f).clientTrackingRange(16).updateInterval(1).fireImmune()
                    .build(Bm21Grad.MODID + ":bm21"));
    public static final RegistryObject<EntityType<RocketEntity>> ROCKET = ENTITIES.register("rocket", () ->
            EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f).clientTrackingRange(32).updateInterval(1).fireImmune()
                    .build(Bm21Grad.MODID + ":rocket"));
    public static final RegistryObject<EntityType<SubmunitionEntity>> SUBMUNITION = ENTITIES.register("submunition", () ->
            EntityType.Builder.<SubmunitionEntity>of(SubmunitionEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).clientTrackingRange(16).updateInterval(2)
                    .build(Bm21Grad.MODID + ":submunition"));
    public static final RegistryObject<EntityType<SmokeScreenEntity>> SMOKE_SCREEN = ENTITIES.register("smoke_screen", () ->
            EntityType.Builder.<SmokeScreenEntity>of(SmokeScreenEntity::new, MobCategory.MISC)
                    .sized(1f, 1f).clientTrackingRange(16).updateInterval(20).noSummon()
                    .build(Bm21Grad.MODID + ":smoke_screen"));

    // ------------------------------------------------------------------ items
    public static final RegistryObject<Item> BM21_ITEM = ITEMS.register("bm21", () ->
            new Bm21SpawnerItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> RANGEFINDER = ITEMS.register("rangefinder", () ->
            new RangefinderItem(new Item.Properties().stacksTo(1)));
    public static final Map<RocketType, RegistryObject<Item>> ROCKETS = new EnumMap<>(RocketType.class);

    static {
        for (RocketType t : RocketType.values()) {
            if (t == RocketType.NONE) continue;
            ROCKETS.put(t, ITEMS.register(t.itemName(), () -> new RocketItem(t, new Item.Properties().stacksTo(16))));
        }
    }

    // ------------------------------------------------------------------ sounds
    public static final RegistryObject<SoundEvent> SND_LAUNCH = sound("launch");
    public static final RegistryObject<SoundEvent> SND_EXPLOSION_NEAR = sound("explosion_near");
    public static final RegistryObject<SoundEvent> SND_EXPLOSION_FAR = sound("explosion_far");
    public static final RegistryObject<SoundEvent> SND_ENGINE = sound("engine");
    public static final RegistryObject<SoundEvent> SND_RELOAD = sound("reload");
    public static final RegistryObject<SoundEvent> SND_HYDRAULICS = sound("hydraulics");
    public static final RegistryObject<SoundEvent> SND_INCOMING = sound("incoming");
    public static final RegistryObject<SoundEvent> SND_SUBMUNITION = sound("submunition");
    public static final RegistryObject<SoundEvent> SND_ROCKET_FLIGHT = sound("rocket_flight");

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Bm21Grad.MODID, name)));
    }

    // ------------------------------------------------------------------ tab
    // creative tabs: one shared set in ru.aiforge.realwar.registry.ModTabs (Техника / Дроны / Ядерное оружие / Ракеты / Боеприпасы ...)

    private ModRegistry() {
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        ITEMS.register(bus);
        SOUNDS.register(bus);
        PARTICLES.register(bus);
        TABS.register(bus);
    }
}
