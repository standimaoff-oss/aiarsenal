package ru.aiforge.realwar.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import ru.aiforge.realwar.handheld.*;

@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class CatalogueTests {
    @GameTest(template="arena", batch="catalogue")
    public static void stale_operator_dummy_rejected_when_chunk_returns(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new net.minecraft.core.BlockPos(2,2,2));
        var orphan=new net.minecraft.world.entity.decoration.ArmorStand(level,pos.getX(),pos.getY(),pos.getZ());
        orphan.addTag(ru.aiforge.arsenal.control.RemoteControl.DUMMY_TAG);
        h.assertTrue(!level.addFreshEntity(orphan),"orphan operator must not reappear after chunk load");
        h.assertTrue(level.getEntity(orphan.getUUID())==null,"orphan operator visible");
        var ordinary=new net.minecraft.world.entity.decoration.ArmorStand(level,pos.getX(),pos.getY(),pos.getZ());
        h.assertTrue(level.addFreshEntity(ordinary),"ordinary armor stands must be preserved");ordinary.discard();
        h.succeed();
    }

    @GameTest(template="arena", batch="catalogue")
    public static void real_chunk_upgrade_does_not_mutate_pending_write_nbt(GameTestHelper h) throws Exception {
        CompoundTag pending = new CompoundTag();
        pending.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        CompoundTag nested = new CompoundTag(); nested.putString("value", "saved");
        pending.put("Nested", nested);
        var path = java.nio.file.Files.createTempDirectory("arsenal-chunk-snapshot-");
        try (var storage = new net.minecraft.world.level.chunk.storage.ChunkStorage(path,
                net.minecraft.util.datafix.DataFixers.getDataFixer(), false)) {
            CompoundTag upgraded = storage.upgradeChunkTag(h.getLevel().dimension(), h.getLevel()::getDataStorage,
                    pending, java.util.Optional.empty());
            h.assertTrue(upgraded != pending, "real ChunkStorage upgrade must detach the pending IO tag (coremod active)");
            upgraded.getCompound("Nested").putString("value", "changed");
            h.assertTrue(pending.getCompound("Nested").getString("value").equals("saved"), "nested NBT must also be detached");
            h.assertTrue(!pending.contains("__context"), "datafix context must never leak into pending writes");
        }
        h.succeed();
    }

    @GameTest(template="arena", batch="catalogue")
    public static void every_registered_item_roundtrips_nbt(GameTestHelper h) {
        int count = 0;
        for (var id : BuiltInRegistries.ITEM.keySet()) if (id.getNamespace().equals("aiarsenal")) {
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            stack.getOrCreateTag().putString("CatalogueRegression", id.toString());
            var loaded = ItemStack.of(stack.save(new CompoundTag()));
            h.assertTrue(ItemStack.isSameItemSameTags(stack, loaded) && loaded.getCount() == 1, "item save/load: " + id);
            count++;
        }
        h.assertTrue(count > 100, "the full runtime registry must be populated");
        ru.aiforge.arsenal.AiArsenal.LOGGER.info("CATALOGUE NBT PASS items={}", count);
        h.succeed();
    }

    @GameTest(template="arena", batch="catalogue")
    public static void every_firearm_reload_consumption_and_no_free_ammo(GameTestHelper h) {
        int count = 0;
        for (var item : BuiltInRegistries.ITEM) if (item instanceof FirearmItem gun) {
            GunKind k = gun.kind;
            var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "catalogue-reload"));
            var stack = new ItemStack(gun);
            var supply = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("aiarsenal", k.ammo)), 3);
            p.getInventory().setItem(1, supply);
            h.assertTrue(Firearms.reloadAllowed(p, stack, k), k.id + " empty reload permitted");
            Firearms.finishReload(p, stack, k);
            int expected = k.launcher() || k.incrementalReload() || k.flintlock() ? 1 : k.cap;
            h.assertTrue(FirearmItem.ammo(stack) == expected, k.id + " actual reload result");
            h.assertTrue(supply.getCount() == 2, k.id + " consumes exactly one ammo item");
            var restored = ItemStack.of(stack.save(new CompoundTag()));
            h.assertTrue(FirearmItem.ammo(restored) == expected, k.id + " loaded rounds survive save");
            p.getInventory().clearContent();
            FirearmItem.setAmmo(stack,0);
            h.assertTrue(!Firearms.reloadAllowed(p,stack,k), k.id + " cannot reload without ammo");
            Firearms.finishReload(p,stack,k);
            h.assertTrue(FirearmItem.ammo(stack)==0, k.id + " no free ammunition");
            count++;
        }
        h.assertTrue(count == GunKind.values().length, "all firearm kinds covered");
        ru.aiforge.arsenal.AiArsenal.LOGGER.info("CATALOGUE RELOAD PASS firearms={}", count);
        h.succeed();
    }

    @GameTest(template="arena", batch="catalogue")
    public static void every_air_bomb_preserves_fuze_age_and_delay(GameTestHelper h) {
        for (var type : ru.aiforge.realwar.aircraft.AirBombType.values()) {
            var original = ru.aiforge.realwar.aircraft.AirBombEntity.drop(h.getLevel(), new net.minecraft.world.phys.Vec3(0,200,0), net.minecraft.world.phys.Vec3.ZERO, type);
            original.armAt=12; original.tickCount=35;
            CompoundTag saved=original.saveWithoutId(new CompoundTag());
            var restored=ru.aiforge.realwar.registry.ModEntities.AIR_BOMB.get().create(h.getLevel());
            restored.load(saved);
            h.assertTrue(restored.bombType()==type && restored.armAt==12 && restored.tickCount==35,
                    type.id + " loading a world must not turn an already armed falling bomb into a dud");
        }
        h.succeed();
    }
}
