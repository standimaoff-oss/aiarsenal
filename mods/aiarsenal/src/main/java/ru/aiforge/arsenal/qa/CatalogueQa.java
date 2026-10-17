package ru.aiforge.arsenal.qa;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.realwar.handheld.*;
import ru.aiforge.realwar.blast.BlastManager;

/** Destructive diagnostics ONLY on an explicitly opted-in disposable server.
 * Never registered in normal games. Uses live registry entries and production item/
 * bomb paths; does not substitute visual-only explosion packets for detonations. */
@Mod.EventBusSubscriber(modid="aiarsenal")
public final class CatalogueQa {
    private static Entity bomb;
    private static Pig witness;
    private static long blocksBefore;
    private static int completedBefore, tuBefore, arsenalBefore;
    private CatalogueQa() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean("aiarsenal.stressQa")) return;
        var root = Commands.literal("arsenal_stress").requires(s -> s.hasPermission(2));
        root.then(Commands.literal("catalogue").executes(c -> {
            JsonArray items = new JsonArray();
            int gunVehicleCases=0;
            for (var id : BuiltInRegistries.ITEM.keySet().stream().filter(i -> i.getNamespace().equals("aiarsenal")).sorted().toList()) {
                Item item = BuiltInRegistries.ITEM.get(id);
                JsonObject row = new JsonObject();
                row.addProperty("id", id.toString()); row.addProperty("class", item.getClass().getName());
                row.addProperty("armor", item instanceof ArmorItem); row.addProperty("block", item instanceof BlockItem);
                if (item instanceof ArmorItem armor) row.addProperty("armorSlot", 100 + armor.getEquipmentSlot().getIndex());
                if (item instanceof BlockItem block) row.addProperty("placedBlock", BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString());
                row.addProperty("consumable", item.isEdible() || item instanceof ru.aiforge.realwar.field.MedicalItem);
                row.addProperty("melee", item instanceof SwordItem);
                if (item instanceof ru.aiforge.realwar.item.VehicleItem) {
                    for (var k : ru.aiforge.realwar.vehicle.VehicleKind.values()) if (k.isGun() && id.getPath().equals(k.spec.id()+"_vehicle")) {
                        gunVehicleCases++;
                        row.addProperty("gunVehicle",true); row.addProperty("drivable",k.drivable());
                        row.addProperty("vehicleAmmo",BuiltInRegistries.ITEM.getKey(ru.aiforge.realwar.vehicle.GunVehicle.cfg(k).ammo().get()).toString());
                    }
                }
                if (item instanceof FirearmItem gun) {
                    var k = gun.kind;
                    row.addProperty("ammo", "aiarsenal:" + k.ammo); row.addProperty("capacity", k.cap);
                    row.addProperty("reloadTicks", k.reloadEmpty); row.addProperty("incremental", k.incrementalReload());
                    row.addProperty("manpads", k.manpads());
                }
                ItemStack stack = new ItemStack(item);
                stack.getOrCreateTag().putString("CatalogueQa", id.toString());
                row.addProperty("nbtRoundtrip", ItemStack.isSameItemSameTags(stack, ItemStack.of(stack.save(new CompoundTag()))));
                items.add(row);
            }
            long expectedVehicles=java.util.Arrays.stream(ru.aiforge.realwar.vehicle.VehicleKind.values()).filter(ru.aiforge.realwar.vehicle.VehicleKind::isGun).count();
            if(gunVehicleCases!=expectedVehicles) throw new IllegalStateException("unmapped gun vehicle items: "+gunVehicleCases+"/"+expectedVehicles);
            JsonArray bombs = new JsonArray();
            for (var t : ru.aiforge.realwar.aircraft.AirBombType.values()) bombs.add("air:" + t.name());
            for (var t : ru.aiforge.tubombers.aircraft.BombType.values()) if (t.arsenal() == null) bombs.add("tu:" + t.name());
            for (var t : ru.aiforge.realwar.registry.BombType.values()) bombs.add("rw:" + t.name());
            for (var t : ru.aiforge.arsenal.core.Kind.values()) if (t.cat == ru.aiforge.arsenal.core.Kind.Cat.NUKE) bombs.add("ars:" + t.name());
            JsonObject out = new JsonObject(); out.add("items", items); out.add("freeFallBombs", bombs);
            try {
                Files.createDirectories(Path.of("logs"));
                Files.writeString(Path.of("logs/arsenal-catalogue.json"), new GsonBuilder().setPrettyPrinting().create().toJson(out));
            } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
            c.getSource().sendSuccess(() -> Component.literal("CATALOGUE items=" + items.size() + " freeFallBombs=" + bombs.size()), false);
            return items.size();
        }));
        root.then(Commands.literal("use").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            ItemStack stack = p.getMainHandItem();
            if (stack.getItem().isEdible() || stack.getItem() instanceof ru.aiforge.realwar.field.MedicalItem) {
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                p.setHealth(8); p.getFoodData().setFoodLevel(6);
            }
            // Live ServerPlayer, network and inventory, not a FakePlayer. Generic smoke
            // coverage only: PASS/FAIL here is an interaction result, not proof of a feature.
            var air = p.gameMode.useItem(p, p.level(), stack, InteractionHand.MAIN_HAND);
            BlockPos at = p.blockPosition().offset(0,-1,2);
            var block = p.gameMode.useItemOn(p, p.serverLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(at).add(0,.5,0), Direction.UP, at, false));
            if (block == net.minecraft.world.InteractionResult.FAIL && p.getMainHandItem().getItem() instanceof BlockItem) {
                // A wall-mounted street lamp correctly rejects the floor. Supply a
                // wall, then exercise placement again; never set the tested block directly.
                BlockPos wall=p.blockPosition().offset(0,0,3);
                p.serverLevel().setBlock(wall,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                block=p.gameMode.useItemOn(p,p.serverLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(wall).add(0,0,-.5),Direction.NORTH,wall,false));
            }
            String outcome="USE air=" + air + " block=" + block + " using=" + p.isUsingItem();
            c.getSource().sendSuccess(() -> Component.literal(outcome), false);
            return 1;
        }));
        root.then(Commands.literal("playerstatus").executes(c -> {
            var p = c.getSource().getPlayerOrException();
            JsonObject o = new JsonObject();
            o.addProperty("item", BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString());
            o.addProperty("count",p.getMainHandItem().getCount()); o.addProperty("health",p.getHealth());
            o.addProperty("using",p.isUsingItem());
            JsonArray effects=new JsonArray();
            for(var effect:p.getActiveEffects()) effects.add(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect()).toString());
            o.add("effects",effects);
            c.getSource().sendSuccess(() -> Component.literal(o.toString()),false);
            return 1;
        }));
        root.then(Commands.literal("mount").executes(c -> {
            var p=c.getSource().getPlayerOrException();
            var candidates=p.serverLevel().getEntitiesOfClass(ru.aiforge.realwar.vehicle.GunVehicle.class,p.getBoundingBox().inflate(64));
            if(candidates.size()!=1) throw new IllegalStateException("expected exactly one placed gun vehicle, got "+candidates.size());
            var v=candidates.get(0);
            if(!v.enterSeat(p,v.operatorSeat())) throw new IllegalStateException("cannot enter operator seat");
            c.getSource().sendSuccess(() -> Component.literal("MOUNTED "+v.kind),false); return 1;
        }));
        root.then(Commands.literal("vehiclestatus").executes(c -> {
            var p=c.getSource().getPlayerOrException();
            if(!(p.getVehicle() instanceof ru.aiforge.realwar.vehicle.GunVehicle v)) throw new IllegalStateException("not mounted");
            JsonObject o=new JsonObject();
            o.addProperty("age",v.tickCount);o.addProperty("reloadTicks",v.reloadTicks());
            o.addProperty("reloadTube",v.reloadTube());o.addProperty("reloadRequired",v.kind.reloadTicks);
            o.addProperty("reloadInputFresh",v.reloadHeld(v.operatorSeat()));
            o.addProperty("reloadInputGrace",v.reloadInputInGrace(v.operatorSeat()));
            o.addProperty("inputAge",v.controlInputAge(v.operatorSeat()));
            o.addProperty("loaded",v.loadedCount());o.addProperty("fired",v.firedSeq());o.addProperty("engine",v.engineOn());
            o.addProperty("x",v.getX());o.addProperty("z",v.getZ());o.addProperty("hp",v.hp());
            var supply=ru.aiforge.realwar.vehicle.GunVehicle.cfg(v.kind).ammo().get();
            int count=0;for(var stack:p.getInventory().items) if(stack.is(supply)) count+=stack.getCount();
            o.addProperty("supply",count);
            c.getSource().sendSuccess(() -> Component.literal(o.toString()),false);return 1;
        }));
        root.then(Commands.literal("drop").then(Commands.argument("type", StringArgumentType.greedyString()).executes(c -> {
            var level = c.getSource().getLevel();
            if (BlastManager.active(level) != 0 || ru.aiforge.arsenal.nuke.NuclearBlast.busy()) throw new IllegalStateException("previous blast still active");
            Vec3 at = c.getSource().getPosition().add(0,45,25);
            String[] key = StringArgumentType.getString(c,"type").split(":");
            Entity next = switch(key[0]) {
                case "air" -> ru.aiforge.realwar.aircraft.AirBombEntity.drop(level, at, Vec3.ZERO,
                        ru.aiforge.realwar.aircraft.AirBombType.valueOf(key[1]));
                case "rw" -> ru.aiforge.realwar.entity.FallingBombEntity.drop(level, at, Vec3.ZERO,
                        ru.aiforge.realwar.registry.BombType.valueOf(key[1]), Direction.NORTH);
                case "tu" -> {
                    var owner = ru.aiforge.tubombers.registry.ModRegistry.TU95.get().create(level);
                    yield ru.aiforge.tubombers.entity.BombEntity.create(level, ru.aiforge.tubombers.aircraft.BombType.valueOf(key[1]),
                            new org.joml.Vector3d(at.x,at.y,at.z), new org.joml.Vector3d(), owner);
                }
                case "ars" -> ru.aiforge.arsenal.entity.NukeEntity.create(level, ru.aiforge.arsenal.core.Kind.valueOf(key[1]),
                        at, Vec3.ZERO, 0, ru.aiforge.arsenal.nuke.ChargeRegistry.FUZE_CONTACT, null, true);
                default -> throw new IllegalArgumentException("unknown bomb family");
            };
            if (!key[0].equals("ars") && !level.addFreshEntity(next)) throw new IllegalStateException("bomb spawn refused");
            bomb = next; blocksBefore = BlastManager.totalBlocksChanged;
            completedBefore = BlastManager.completedBlasts;
            tuBefore = ru.aiforge.tubombers.fx.ImpactHandler.detonations;
            arsenalBefore = ru.aiforge.arsenal.nuke.NuclearBlast.detonations;
            if (witness != null) witness.discard();
            witness = EntityType.PIG.create(level);
            witness.setNoAi(true); witness.setPersistenceRequired();
            witness.setPos(at.x + 3, at.y - 45, at.z);
            level.addFreshEntity(witness);
            c.getSource().sendSuccess(() -> Component.literal("DROPPED " + key[0] + ":" + key[1] + " uuid=" + next.getUUID()), false);
            return 1;
        })));
        root.then(Commands.literal("bombstatus").executes(c -> {
            JsonObject o = new JsonObject();
            o.addProperty("removed", bomb != null && bomb.isRemoved());
            o.addProperty("removalReason", bomb == null || bomb.getRemovalReason() == null ? "none" : bomb.getRemovalReason().name());
            o.addProperty("witnessHealth", witness == null ? -1 : witness.getHealth());
            o.addProperty("witnessDead", witness != null && witness.isDeadOrDying());
            o.addProperty("activeBlasts", BlastManager.active(c.getSource().getLevel()));
            o.addProperty("legacyBusy", ru.aiforge.arsenal.nuke.NuclearBlast.busy());
            o.addProperty("changedBlocks", BlastManager.totalBlocksChanged - blocksBefore);
            o.addProperty("completedTasks", BlastManager.completedBlasts-completedBefore);
            o.addProperty("tuDetonations",ru.aiforge.tubombers.fx.ImpactHandler.detonations-tuBefore);
            o.addProperty("arsenalDetonations",ru.aiforge.arsenal.nuke.NuclearBlast.detonations-arsenalBefore);
            c.getSource().sendSuccess(() -> Component.literal(o.toString()), false);
            return 1;
        }));
        for (boolean verify : new boolean[]{false, true}) {
            root.then(Commands.literal(verify ? "archivecheck" : "archive").executes(c -> {
                var level = c.getSource().getLevel();
                var ids = BuiltInRegistries.ITEM.keySet().stream().filter(i -> i.getNamespace().equals("aiarsenal")).sorted().toList();
                for (int i=0; i<ids.size(); i++) {
                    BlockPos pos = new BlockPos(5120 + i/27, 80, 5120);
                    level.getChunkAt(pos);
                    if (!verify && i%27==0) level.setBlock(pos, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState(), 3);
                    if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel))
                        throw new IllegalStateException("missing saved barrel " + pos);
                    ItemStack expected = new ItemStack(BuiltInRegistries.ITEM.get(ids.get(i)));
                    expected.getOrCreateTag().putString("SavedRegistryId", ids.get(i).toString());
                    if (!verify) { barrel.setItem(i%27,expected); barrel.setChanged(); }
                    else if (!ItemStack.isSameItemSameTags(expected,barrel.getItem(i%27)) || barrel.getItem(i%27).getCount()!=1)
                        throw new IllegalStateException("world save/reopen mismatch: " + ids.get(i));
                }
                if (verify && ru.aiforge.arsenal.stability.ChunkTagSnapshot.copies() == 0)
                    throw new IllegalStateException("installed chunk upgrade snapshot hook never ran after world reopen");
                if (verify) ru.aiforge.arsenal.AiArsenal.LOGGER.info("CHUNK SNAPSHOT QA PASS copies={}", ru.aiforge.arsenal.stability.ChunkTagSnapshot.copies());
                c.getSource().sendSuccess(() -> Component.literal((verify ? "ARCHIVE VERIFIED " : "ARCHIVE SAVED ") + ids.size()), false);
                return ids.size();
            }));
        }
        event.getDispatcher().register(root);
    }
}
