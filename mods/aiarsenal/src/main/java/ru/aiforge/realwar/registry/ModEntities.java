/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.EntityType$Builder
 *  net.minecraft.world.entity.EntityType$EntityFactory
 *  net.minecraft.world.entity.MobCategory
 *  net.minecraftforge.registries.DeferredRegister
 *  net.minecraftforge.registries.ForgeRegistries
 *  net.minecraftforge.registries.IForgeRegistry
 *  net.minecraftforge.registries.RegistryObject
 */
package ru.aiforge.realwar.registry;

import ru.aiforge.realwar.aircraft.AirBombEntity;
import ru.aiforge.realwar.aircraft.AircraftEntity;
import ru.aiforge.realwar.entity.FallingBombEntity;
import ru.aiforge.realwar.ordnance.RocketEntity;
import ru.aiforge.realwar.ordnance.SamMissileEntity;
import ru.aiforge.realwar.radar.RadarVehicleEntity;
import ru.aiforge.realwar.vehicle.FlakEntity;
import ru.aiforge.realwar.vehicle.MlrsEntity;
import ru.aiforge.realwar.vehicle.SamLauncherEntity;
import ru.aiforge.realwar.vehicle.VehicleKind;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create((IForgeRegistry)ForgeRegistries.ENTITY_TYPES, (String)"aiarsenal");
    public static final RegistryObject<EntityType<FallingBombEntity>> FALLING_BOMB = ENTITIES.register("falling_bomb", () -> EntityType.Builder.of(FallingBombEntity::new, (MobCategory)MobCategory.MISC).sized(0.8f, 0.8f).clientTrackingRange(16).updateInterval(1).fireImmune().build("falling_bomb"));
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> T90M = ModEntities.vehicle("t90m", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.T90M), VehicleKind.T90M);
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> ABRAMS = ModEntities.vehicle("abrams", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.ABRAMS), VehicleKind.ABRAMS);
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> BMP3 = ModEntities.vehicle("bmp3", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.BMP3), VehicleKind.BMP3);
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> MSTA = ModEntities.vehicle("msta", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.MSTA), VehicleKind.MSTA);
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> D30 = ModEntities.vehicle("d30", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.D30), VehicleKind.D30);
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> M777 = ModEntities.vehicle("m777", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.M777), VehicleKind.M777);
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> PZ2 = ModEntities.vehicle("pz2", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.PZ2), VehicleKind.PZ2);
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> PZ4 = ModEntities.vehicle("pz4", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.PZ4), VehicleKind.PZ4);
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> TIGER = ModEntities.vehicle("tiger", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.TIGER), VehicleKind.TIGER);
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> STURMTIGER = ModEntities.vehicle("sturmtiger", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.STURMTIGER), VehicleKind.STURMTIGER);
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> MAUS = ModEntities.vehicle("maus", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.MAUS), VehicleKind.MAUS);
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> RATTE = ModEntities.vehicle("ratte", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.RATTE), VehicleKind.RATTE);
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> T34 = ModEntities.vehicle("t34", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.T34), VehicleKind.T34);
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> KV2 = ModEntities.vehicle("kv2", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.KV2), VehicleKind.KV2);
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> IS2 = ModEntities.vehicle("is2", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.IS2), VehicleKind.IS2);
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> KV5 = ModEntities.vehicle("kv5", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.KV5), VehicleKind.KV5);
    public static final RegistryObject<EntityType<MlrsEntity>> BM13 = ModEntities.vehicle("bm13", (t, l) -> new MlrsEntity(t, l, VehicleKind.BM13), VehicleKind.BM13);
    // reply 12
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> BRADLEY = ModEntities.vehicle("bradley", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.BRADLEY), VehicleKind.BRADLEY);
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> TOS1A = ModEntities.vehicle("tos1a", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.TOS1A), VehicleKind.TOS1A);
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.TruckEntity>> URAL = ModEntities.vehicle("ural", (t, l) -> new ru.aiforge.realwar.vehicle.TruckEntity(t, l, VehicleKind.URAL), VehicleKind.URAL);
    // WWI Mark I: reuse the established tracked gun-vehicle simulation, with its own no-turret profile and twin sponsons.
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.GunVehicle>> MARK_I = ModEntities.vehicle("mark_i", (t, l) -> new ru.aiforge.realwar.vehicle.GunVehicle(t, l, VehicleKind.MARK_I), VehicleKind.MARK_I);
    public static final RegistryObject<EntityType<MlrsEntity>> GRAD = ModEntities.vehicle("grad", (t, l) -> new MlrsEntity(t, l, VehicleKind.GRAD), VehicleKind.GRAD);
    public static final RegistryObject<EntityType<MlrsEntity>> SMERCH = ModEntities.vehicle("smerch", (t, l) -> new MlrsEntity(t, l, VehicleKind.SMERCH), VehicleKind.SMERCH);
    public static final RegistryObject<EntityType<SamLauncherEntity>> S400 = ModEntities.vehicle("s400", (t, l) -> new SamLauncherEntity(t, l, VehicleKind.S400), VehicleKind.S400);
    public static final RegistryObject<EntityType<RadarVehicleEntity>> RADAR_MOBILE = ModEntities.vehicle("radar_mobile", (t, l) -> new RadarVehicleEntity(t, l, VehicleKind.RADAR_MOBILE), VehicleKind.RADAR_MOBILE);
    public static final RegistryObject<EntityType<RadarVehicleEntity>> RADAR_POST = ModEntities.vehicle("radar_post", (t, l) -> new RadarVehicleEntity(t, l, VehicleKind.RADAR_POST), VehicleKind.RADAR_POST);
    public static final RegistryObject<EntityType<ru.aiforge.realwar.vehicle.YarsVehicle>> YARS = ModEntities.vehicle("yars", (t, l) -> new ru.aiforge.realwar.vehicle.YarsVehicle(t, l), VehicleKind.YARS);
    public static final RegistryObject<EntityType<FlakEntity>> ZU23 = ModEntities.vehicle("zu23", (t, l) -> new FlakEntity(t, l), VehicleKind.ZU23);
    public static final RegistryObject<EntityType<RocketEntity>> ROCKET = ENTITIES.register("rw_rocket", () -> EntityType.Builder.of(RocketEntity::new, (MobCategory)MobCategory.MISC).sized(0.4f, 0.4f).clientTrackingRange(64).updateInterval(1).fireImmune().noSummon().build("rocket"));
    public static final RegistryObject<EntityType<ru.aiforge.realwar.damage.DebrisEntity>> DEBRIS = ENTITIES.register("rw_debris", () -> EntityType.Builder.<ru.aiforge.realwar.damage.DebrisEntity>of(ru.aiforge.realwar.damage.DebrisEntity::new, (MobCategory)MobCategory.MISC).sized(0.8f, 0.5f).clientTrackingRange(16).updateInterval(1).fireImmune().noSummon().build("rw_debris"));
    public static final RegistryObject<EntityType<ru.aiforge.realwar.ordnance.ShellEntity>> SHELL = ENTITIES.register("rw_shell", () -> EntityType.Builder.<ru.aiforge.realwar.ordnance.ShellEntity>of(ru.aiforge.realwar.ordnance.ShellEntity::new, (MobCategory)MobCategory.MISC).sized(0.25f, 0.25f).clientTrackingRange(64).updateInterval(1).fireImmune().noSummon().build("rw_shell"));
    public static final RegistryObject<EntityType<ru.aiforge.realwar.handheld.AtMissileEntity>> AT_MISSILE = ENTITIES.register("at_missile", () -> EntityType.Builder.<ru.aiforge.realwar.handheld.AtMissileEntity>of(ru.aiforge.realwar.handheld.AtMissileEntity::new, (MobCategory)MobCategory.MISC).sized(0.3f, 0.3f).clientTrackingRange(128).updateInterval(1).fireImmune().noSummon().build("at_missile"));
    public static final RegistryObject<EntityType<SamMissileEntity>> SAM_MISSILE = ENTITIES.register("sam_missile", () -> EntityType.Builder.of(SamMissileEntity::new, (MobCategory)MobCategory.MISC).sized(0.5f, 0.5f).clientTrackingRange(64).updateInterval(1).fireImmune().noSummon().build("sam_missile"));
    public static final RegistryObject<EntityType<AircraftEntity>> TU95 = ENTITIES.register("tu95", () -> EntityType.Builder.of(AircraftEntity::new, (MobCategory)MobCategory.MISC).sized(10.0f, 5.5f).clientTrackingRange(32).updateInterval(1).fireImmune().build("tu95"));
    public static final RegistryObject<EntityType<ru.aiforge.realwar.strike.StrikePlaneEntity>> STRIKE_PLANE = ENTITIES.register("strike_plane", () -> EntityType.Builder.<ru.aiforge.realwar.strike.StrikePlaneEntity>of(ru.aiforge.realwar.strike.StrikePlaneEntity::new, MobCategory.MISC).sized(4.0f, 2.0f).clientTrackingRange(48).updateInterval(2).fireImmune().noSummon().build("strike_plane"));
    public static final RegistryObject<EntityType<AirBombEntity>> AIR_BOMB = ENTITIES.register("air_bomb", () -> EntityType.Builder.of(AirBombEntity::new, (MobCategory)MobCategory.MISC).sized(0.6f, 0.6f).clientTrackingRange(32).updateInterval(1).fireImmune().noSummon().build("air_bomb"));

    public static final RegistryObject<EntityType<ru.aiforge.realwar.napoleonic.PowderCannonEntity>> CANNON_FR6 = ENTITIES.register("cannon_fr6", () -> EntityType.Builder.<ru.aiforge.realwar.napoleonic.PowderCannonEntity>of(ru.aiforge.realwar.napoleonic.PowderCannonEntity::new, MobCategory.MISC).sized(1.8f,1.5f).clientTrackingRange(48).updateInterval(2).build("cannon_fr6"));
    public static final RegistryObject<EntityType<ru.aiforge.realwar.napoleonic.PowderCannonEntity>> CANNON_RI12 = ENTITIES.register("cannon_ri12", () -> EntityType.Builder.<ru.aiforge.realwar.napoleonic.PowderCannonEntity>of(ru.aiforge.realwar.napoleonic.PowderCannonEntity::new, MobCategory.MISC).sized(1.9f,1.6f).clientTrackingRange(48).updateInterval(2).build("cannon_ri12"));
    public static final RegistryObject<EntityType<ru.aiforge.realwar.napoleonic.CannonballEntity>> CANNONBALL = ENTITIES.register("cannonball", () -> EntityType.Builder.<ru.aiforge.realwar.napoleonic.CannonballEntity>of(ru.aiforge.realwar.napoleonic.CannonballEntity::new, MobCategory.MISC).sized(.18f,.18f).clientTrackingRange(64).updateInterval(1).build("cannonball"));

    private ModEntities() {
    }

    private static <T extends Entity> RegistryObject<EntityType<T>> vehicle(String name, EntityType.EntityFactory<T> f, VehicleKind k) {
        return ENTITIES.register(name, () -> EntityType.Builder.of((EntityType.EntityFactory)f, (MobCategory)MobCategory.MISC).sized(k.spec.aabbW(), k.spec.aabbH()).clientTrackingRange(16).updateInterval(1).fireImmune().build(name));
    }
}

