package ru.aiforge.realwar.craft;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Entity types and items of the helicopters / boats / submarine (reply 6). */
public final class CraftRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "aiarsenal");
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "aiarsenal");

    public static final RegistryObject<EntityType<HeliEntity>> KA52 = heli(CraftKind.KA52);
    public static final RegistryObject<EntityType<HeliEntity>> AH64 = heli(CraftKind.AH64);
    public static final RegistryObject<EntityType<BoatEntity>> RAPTOR = boat(CraftKind.RAPTOR);
    public static final RegistryObject<EntityType<BoatEntity>> CB90 = boat(CraftKind.CB90);
    public static final RegistryObject<EntityType<BoatEntity>> SUB636 = boat(CraftKind.SUB636);
    public static final RegistryObject<EntityType<BattleshipEntity>> BISMARCK = ENTITIES.register(CraftKind.BISMARCK.id,
            () -> EntityType.Builder.<BattleshipEntity>of((t, l) -> new BattleshipEntity(t, l, CraftKind.BISMARCK), MobCategory.MISC)
                    .sized(CraftKind.BISMARCK.width, CraftKind.BISMARCK.height).clientTrackingRange(32).updateInterval(1).fireImmune()
                    .build(CraftKind.BISMARCK.id));
    public static final RegistryObject<EntityType<AradoEntity>> ARADO = ENTITIES.register("ar196",
            () -> EntityType.Builder.<AradoEntity>of(AradoEntity::new, MobCategory.MISC).sized(4.0f, 2.2f)
                    .clientTrackingRange(32).updateInterval(1).fireImmune().build("ar196"));
    public static final RegistryObject<EntityType<PlaneEntity>> BF109 = plane(CraftKind.BF109);
    public static final RegistryObject<EntityType<PlaneEntity>> YAK3 = plane(CraftKind.YAK3);
    public static final RegistryObject<EntityType<PlaneEntity>> HE111 = plane(CraftKind.HE111);
    public static final RegistryObject<EntityType<PlaneEntity>> PE8 = plane(CraftKind.PE8);
    public static final RegistryObject<EntityType<PlaneEntity>> SU57 = plane(CraftKind.SU57);
    public static final RegistryObject<EntityType<CraftOrdnance>> ORDNANCE = ENTITIES.register("craft_ordnance",
            () -> EntityType.Builder.<CraftOrdnance>of(CraftOrdnance::new, MobCategory.MISC).sized(0.3f, 0.3f)
                    .clientTrackingRange(16).updateInterval(1).fireImmune().build("craft_ordnance"));

    private static final Map<CraftKind, RegistryObject<Item>> ITEM_BY_KIND = new EnumMap<>(CraftKind.class);
    public static final RegistryObject<Item> KA52_ITEM = item(CraftKind.KA52, "ka52_vehicle");
    public static final RegistryObject<Item> AH64_ITEM = item(CraftKind.AH64, "ah64_vehicle");
    public static final RegistryObject<Item> RAPTOR_ITEM = item(CraftKind.RAPTOR, "raptor_boat");
    public static final RegistryObject<Item> CB90_ITEM = item(CraftKind.CB90, "cb90_boat");
    public static final RegistryObject<Item> SUB636_ITEM = item(CraftKind.SUB636, "sub636_vehicle");
    public static final RegistryObject<Item> BISMARCK_ITEM = item(CraftKind.BISMARCK, "bismarck_ship");
    public static final RegistryObject<Item> BF109_ITEM = item(CraftKind.BF109, "bf109_plane");
    public static final RegistryObject<Item> YAK3_ITEM = item(CraftKind.YAK3, "yak3_plane");
    public static final RegistryObject<Item> HE111_ITEM = item(CraftKind.HE111, "he111_plane");
    public static final RegistryObject<Item> PE8_ITEM = item(CraftKind.PE8, "pe8_plane");
    public static final RegistryObject<Item> SU57_ITEM = item(CraftKind.SU57, "su57_plane");
    public static final RegistryObject<Item> SERVICE_KIT = ITEMS.register("craft_service_kit",
            () -> new CraftServiceKitItem(new Item.Properties().stacksTo(16)));

    private CraftRegistry() {}

    private static RegistryObject<EntityType<HeliEntity>> heli(CraftKind k) {
        return ENTITIES.register(k.id, () -> EntityType.Builder.<HeliEntity>of((t, l) -> new HeliEntity(t, l, k), MobCategory.MISC)
                .sized(k.width, k.height).clientTrackingRange(20).updateInterval(1).fireImmune().build(k.id));
    }

    private static RegistryObject<EntityType<PlaneEntity>> plane(CraftKind k) {
        return ENTITIES.register(k.id, () -> EntityType.Builder.<PlaneEntity>of((t, l) -> new PlaneEntity(t, l, k), MobCategory.MISC)
                .sized(k.width, k.height).clientTrackingRange(k.plane.engines() >= 2 ? 32 : 24).updateInterval(1).fireImmune().build(k.id));
    }

    private static RegistryObject<EntityType<BoatEntity>> boat(CraftKind k) {
        return ENTITIES.register(k.id, () -> EntityType.Builder.<BoatEntity>of((t, l) -> new BoatEntity(t, l, k), MobCategory.MISC)
                .sized(k.width, k.height).clientTrackingRange(k.isSub() ? 24 : 16).updateInterval(1).fireImmune().build(k.id));
    }

    private static RegistryObject<Item> item(CraftKind k, String name) {
        RegistryObject<Item> r = ITEMS.register(name, () -> new CraftItem(k, new Item.Properties().stacksTo(1)));
        ITEM_BY_KIND.put(k, r);
        return r;
    }

    public static RegistryObject<Item> item(CraftKind k) {
        return ITEM_BY_KIND.get(k);
    }

    public static EntityType<? extends CraftEntity> type(CraftKind k) {
        return switch (k) {
            case KA52 -> KA52.get();
            case AH64 -> AH64.get();
            case RAPTOR -> RAPTOR.get();
            case CB90 -> CB90.get();
            case SUB636 -> SUB636.get();
            case BISMARCK -> BISMARCK.get();
            case BF109 -> BF109.get();
            case YAK3 -> YAK3.get();
            case HE111 -> HE111.get();
            case PE8 -> PE8.get();
            case SU57 -> SU57.get();
        };
    }

    /** Spawn a fresh, fully armed craft. */
    public static CraftEntity spawn(net.minecraft.server.level.ServerLevel sl, CraftKind k, double x, double y, double z, float yaw) {
        CraftEntity e = type(k).create(sl);
        if (e == null) return null;
        e.initFresh();
        e.moveTo(x, y, z, yaw, 0f);
        sl.addFreshEntity(e);
        return e;
    }
}
