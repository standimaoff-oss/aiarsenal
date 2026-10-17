package ru.aiforge.arsenal.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.aiforge.arsenal.AiArsenal;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.entity.FpvDrone;
import ru.aiforge.arsenal.entity.IcbmEntity;
import ru.aiforge.arsenal.entity.LauncherEntity;
import ru.aiforge.arsenal.entity.MissileEntity;
import ru.aiforge.arsenal.entity.NukeEntity;
import ru.aiforge.arsenal.entity.ReconDrone;
import ru.aiforge.arsenal.entity.ShahedDrone;
import ru.aiforge.arsenal.item.AmmoItem;
import ru.aiforge.arsenal.item.DetonatorItem;
import ru.aiforge.arsenal.item.GogglesItem;
import ru.aiforge.arsenal.item.TabletItem;
import ru.aiforge.arsenal.item.UnitItem;
import ru.aiforge.tubombers.aircraft.BombType;

import java.util.EnumMap;
import java.util.Map;

/** Drones, missiles, nuclear weapons: entity types, items, sounds and the creative tab. */
public final class ArsenalRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AiArsenal.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AiArsenal.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, AiArsenal.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AiArsenal.MODID);

    // ------------------------------------------------------------------ entities
    public static final RegistryObject<EntityType<ReconDrone>> RECON = entity("recon_drone", ReconDrone::new, 2f, 1f, 32);
    public static final RegistryObject<EntityType<ShahedDrone>> SHAHED = entity("shahed_drone", ShahedDrone::new, 1.6f, 0.6f, 24);
    public static final RegistryObject<EntityType<FpvDrone>> FPV = entity("fpv_drone", FpvDrone::new, 0.5f, 0.25f, 16);
    public static final RegistryObject<EntityType<MissileEntity>> MISSILE = entity("missile", MissileEntity::new, 0.8f, 0.6f, 32);
    public static final RegistryObject<EntityType<IcbmEntity>> ICBM = entity("icbm", IcbmEntity::new, 2f, 2f, 64);
    public static final RegistryObject<EntityType<NukeEntity>> NUKE = entity("nuke", NukeEntity::new, 1.5f, 1.5f, 24);
    public static final RegistryObject<EntityType<LauncherEntity>> LAUNCHER = entity("launcher", LauncherEntity::new, 3f, 3f, 16);

    private static <T extends Entity> RegistryObject<EntityType<T>> entity(String name, EntityType.EntityFactory<T> f, float w, float h, int range) {
        return ENTITIES.register(name, () -> EntityType.Builder.of(f, MobCategory.MISC).sized(w, h).clientTrackingRange(range)
                .updateInterval(1).fireImmune().noSummon().build(AiArsenal.MODID + ":" + name));
    }

    // ------------------------------------------------------------------ items
    public static final Map<Kind, RegistryObject<Item>> UNITS = new EnumMap<>(Kind.class);

    static {
        for (Kind k : new Kind[]{Kind.MQ9, Kind.ORION, Kind.LIUTYI, Kind.FPV7, Kind.FPV10, Kind.RAIL, Kind.CRUISE_LAUNCHER, Kind.YARS_TEL})
            UNITS.put(k, ITEMS.register(k.id, () -> new UnitItem(k, new Item.Properties().stacksTo(k.cat == Kind.Cat.FPV ? 4 : 1)
                    .rarity(k == Kind.YARS_TEL ? Rarity.EPIC : Rarity.RARE))));
        for (Kind k : new Kind[]{Kind.S136, Kind.S131, Kind.S238, Kind.KALIBR, Kind.TOMAHAWK, Kind.YARS})
            UNITS.put(k, ITEMS.register(k.id, () -> new AmmoItem(k, new Item.Properties().stacksTo(k == Kind.YARS ? 1 : 4)
                    .rarity(k == Kind.YARS ? Rarity.EPIC : Rarity.UNCOMMON))));
    }

    public static final RegistryObject<Item> TABLET = ITEMS.register("tablet", () -> new TabletItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> GOGGLES = ITEMS.register("fpv_goggles", () -> new GogglesItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> DETONATOR = ITEMS.register("detonator", () -> new DetonatorItem(new Item.Properties().stacksTo(1)
            .rarity(Rarity.EPIC)));

    /** Item for any kind (arsenal units, or the bomber stores registered by the Tu module). */
    public static Item item(Kind k) {
        RegistryObject<Item> r = UNITS.get(k);
        if (r != null) return r.get();
        for (BombType b : BombType.values())
            if (b.arsenal() == k) return ru.aiforge.tubombers.registry.ModRegistry.bombItem(b);
        return Items.AIR;
    }

    // ------------------------------------------------------------------ sounds
    public static final RegistryObject<SoundEvent> SND_SHAHED = sound("shahed_engine");
    public static final RegistryObject<SoundEvent> SND_JET = sound("jet_loop");
    public static final RegistryObject<SoundEvent> SND_TURBOPROP = sound("turboprop_loop");
    public static final RegistryObject<SoundEvent> SND_FPV = sound("fpv_motor");
    public static final RegistryObject<SoundEvent> SND_BOOSTER = sound("booster");
    public static final RegistryObject<SoundEvent> SND_LAUNCH_RAIL = sound("launch_rail");
    public static final RegistryObject<SoundEvent> SND_LAUNCH_COLD = sound("launch_cold");
    public static final RegistryObject<SoundEvent> SND_NUKE = sound("nuke_blast");
    public static final RegistryObject<SoundEvent> SND_NUKE_FAR = sound("nuke_far");
    public static final RegistryObject<SoundEvent> SND_GEIGER = sound("geiger");
    public static final RegistryObject<SoundEvent> SND_BEEP = sound("tablet_beep");
    public static final RegistryObject<SoundEvent> SND_DETONATOR = sound("detonator");
    public static final RegistryObject<SoundEvent> SND_STATIC = sound("fpv_static");
    public static final RegistryObject<SoundEvent> SND_SIREN = sound("siren");

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(AiArsenal.MODID, name)));
    }

    // creative tabs: one shared set in ru.aiforge.realwar.registry.ModTabs (Техника / Дроны / Ядерное оружие / Ракеты / Боеприпасы ...)

    private ArsenalRegistry() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        ENTITIES.register(bus);
        SOUNDS.register(bus);
        TABS.register(bus);
    }
}
