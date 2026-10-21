package ru.aiforge.realwar.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.realwar.handheld.AtMissileEntity;
import ru.aiforge.realwar.handheld.FirearmItem;
import ru.aiforge.realwar.handheld.Firearms;
import ru.aiforge.realwar.handheld.GunKind;
import ru.aiforge.realwar.handheld.MagazineItem;
import ru.aiforge.realwar.registry.ModItems;

/** Reply 22-24: AK-12 / M4A1 / RPG-7V2 / T-5000 / PKP / Saiga-12K / Remington 870 server logic. */
@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class SmallArmsTests {
    private SmallArmsTests() {
    }

    private static FakePlayer shooter(GameTestHelper h, ItemStack gun, double x, double y, double z) {
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "rw-rifle"));
        p.moveTo(x, y, z, 0f, 0f);                                     // yaw 0 = looking +Z
        p.setItemInHand(InteractionHand.MAIN_HAND, gun);
        return p;
    }

    @GameTest(template = "arena", batch = "rw_small", timeoutTicks = 120)
    public static void ak12_burst_hits_and_counts_rounds(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(2, 1, 2));
        for (int dz = 0; dz < 14; dz++) for (int dy = 0; dy < 4; dy++) level.setBlock(o.offset(0, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        ItemStack gun = new ItemStack(ModItems.AK12.get());
        FirearmItem.setAmmo(gun, 30);
        FakePlayer p = shooter(h, gun, o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        Husk t = EntityType.HUSK.create(level);
        t.moveTo(o.getX() + 0.5, o.getY(), o.getZ() + 10.5, 180f, 0f);
        t.setNoAi(true);
        level.addFreshEntity(t);
        float hp0 = t.getHealth();
        for (int i = 0; i < 5; i++) Firearms.fire(p, gun, GunKind.AK12, true);
        h.assertTrue(FirearmItem.ammo(gun) == 25, "5 shots should leave 25 rounds, got " + FirearmItem.ammo(gun));
        h.assertTrue(Firearms.bullets(level) >= 1, "bullets in flight");
        h.runAfterDelay(6, () -> {
            h.assertTrue(!t.isAlive() || t.getHealth() < hp0, "aimed AK burst at 10 m must hit the husk (hp " + t.getHealth() + ")");
            t.discard();
            h.succeed();
        });
    }

    @GameTest(template = "arena", batch = "rw_small")
    public static void reload_takes_fullest_mag_and_keeps_chambered(GameTestHelper h) {
        BlockPos o = h.absolutePos(new BlockPos(2, 1, 2));
        ItemStack gun = new ItemStack(ModItems.M4A1.get());
        FirearmItem.setAmmo(gun, 5);
        FakePlayer p = shooter(h, gun, o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        p.getInventory().add(MagazineItem.withRounds(ModItems.MAG_STANAG.get(), 12));
        p.getInventory().add(new ItemStack(ModItems.MAG_STANAG.get()));         // full (no NBT)
        Firearms.finishReload(p, gun, GunKind.M4A1);
        h.assertTrue(FirearmItem.ammo(gun) == 31, "tactical reload = 30 + 1 chambered, got " + FirearmItem.ammo(gun));
        int partial = -1, mags = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.getItem() instanceof MagazineItem) {
                mags += s.getCount();
                if (MagazineItem.rounds(s) == 4) partial = 4;
            }
        }
        h.assertTrue(mags == 2, "the 12-round mag stays, the old one comes back: " + mags);
        h.assertTrue(partial == 4, "old magazine returned with 4 rounds (5 - chambered)");
        ItemStack empty = new ItemStack(ModItems.AK12.get());
        FakePlayer q = shooter(h, empty, o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        Firearms.finishReload(q, empty, GunKind.AK12);
        h.assertTrue(FirearmItem.ammo(empty) == 0, "no magazine -> nothing loaded");
        h.succeed();
    }

    @GameTest(template = "arena", batch = "rw_small", timeoutTicks = 60)
    public static void rpg7_launches_pg7vl_and_needs_reload(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(2, 2, 2));
        ItemStack rpg = new ItemStack(ModItems.RPG7.get());
        FirearmItem.setAmmo(rpg, 1);
        FakePlayer p = shooter(h, rpg, o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        p.setXRot(-30f);
        Firearms.fire(p, rpg, GunKind.RPG7, true);
        h.assertTrue(FirearmItem.ammo(rpg) == 0, "RPG-7 holds one grenade");
        var m = level.getEntitiesOfClass(AtMissileEntity.class, new AABB(o).inflate(8));
        h.assertTrue(!m.isEmpty(), "PG-7VL launched");
        p.getInventory().add(new ItemStack(ModItems.PG7V.get(), 2));
        Firearms.finishReload(p, rpg, GunKind.RPG7);
        int left = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(ModItems.PG7V.get())) left += s.getCount();
        h.assertTrue(FirearmItem.ammo(rpg) == 1 && left == 1, "reload consumes one PG-7VL");
        m.forEach(net.minecraft.world.entity.Entity::discard);
        h.succeed();
    }

    // ------------------------------------------------------------------ r23

    @GameTest(template = "arena", batch = "rw_small", timeoutTicks = 120)
    public static void t5000_one_shot_and_bolt_reload(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(2, 1, 2));
        for (int dz = 0; dz < 14; dz++) for (int dy = 0; dy < 4; dy++) level.setBlock(o.offset(0, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        ItemStack gun = new ItemStack(ModItems.T5000.get());
        FirearmItem.setAmmo(gun, 2);
        FakePlayer p = shooter(h, gun, o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        Husk t = EntityType.HUSK.create(level);
        t.moveTo(o.getX() + 0.5, o.getY(), o.getZ() + 12.5, 180f, 0f);
        t.setNoAi(true);
        level.addFreshEntity(t);
        float hp0 = t.getHealth();
        Firearms.fire(p, gun, GunKind.T5000, true);
        h.assertTrue(FirearmItem.ammo(gun) == 1, ".338 LM: one round per shot");
        h.assertTrue(GunKind.T5000.boltAction() && !GunKind.T5000.selector() && GunKind.T5000.maxLoaded(true) == 6, "bolt action, 5 + 1");
        p.getInventory().add(new ItemStack(ModItems.MAG_T5000.get()));
        Firearms.finishReload(p, gun, GunKind.T5000);
        h.assertTrue(FirearmItem.ammo(gun) == 6, "tactical reload 5 + 1 chambered, got " + FirearmItem.ammo(gun));
        h.runAfterDelay(6, () -> {
            h.assertTrue(!t.isAlive() || t.getHealth() <= hp0 - 20f, ".338 LM at 12 m must (nearly) kill the husk: hp " + t.getHealth());
            t.discard();
            h.succeed();
        });
    }

    @GameTest(template = "arena", batch = "rw_small")
    public static void pkp_belt_box_reload_open_bolt(GameTestHelper h) {
        BlockPos o = h.absolutePos(new BlockPos(2, 1, 2));
        ItemStack gun = new ItemStack(ModItems.PKP.get());
        FirearmItem.setAmmo(gun, 10);
        FakePlayer p = shooter(h, gun, o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        p.getInventory().add(new ItemStack(ModItems.BOX_PKP.get()));
        Firearms.finishReload(p, gun, GunKind.PKP);
        h.assertTrue(FirearmItem.ammo(gun) == 100, "open bolt: a new box = 100 (no chambered extra), got " + FirearmItem.ammo(gun));
        int back = -1;
        for (ItemStack s : p.getInventory().items) if (s.getItem() instanceof MagazineItem) back = MagazineItem.rounds(s);
        h.assertTrue(back == 10, "the old box keeps its 10-round belt: " + back);
        h.assertTrue(GunKind.PKP.auto && !GunKind.PKP.selector(), "PKP is full-auto only");
        for (int i = 0; i < 3; i++) Firearms.fire(p, gun, GunKind.PKP, false);
        h.assertTrue(FirearmItem.ammo(gun) == 97, "3 rounds fired");
        h.succeed();
    }

    // ------------------------------------------------------------------ r24

    @GameTest(template = "arena", batch = "rw_small", timeoutTicks = 120)
    public static void saiga12_buckshot_pattern_hits(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(2, 1, 2));
        for (int dz = 0; dz < 10; dz++) for (int dy = 0; dy < 4; dy++) level.setBlock(o.offset(0, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        ItemStack gun = new ItemStack(ModItems.SAIGA12.get());
        FirearmItem.setAmmo(gun, 8);
        FakePlayer p = shooter(h, gun, o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        Husk t = EntityType.HUSK.create(level);
        t.moveTo(o.getX() + 0.5, o.getY(), o.getZ() + 6.5, 180f, 0f);
        t.setNoAi(true);
        level.addFreshEntity(t);
        float hp0 = t.getHealth();
        int b0 = Firearms.bullets(level);
        Firearms.fire(p, gun, GunKind.SAIGA12, true);
        h.assertTrue(FirearmItem.ammo(gun) == 7, "one shell per shot");
        h.assertTrue(Firearms.bullets(level) - b0 == 9, "00 buckshot = 9 pellets, got " + (Firearms.bullets(level) - b0));
        h.assertTrue(GunKind.SAIGA12.maxLoaded(true) == 9 && !GunKind.SAIGA12.auto, "semi-auto, 8 + 1");
        h.runAfterDelay(6, () -> {
            h.assertTrue(!t.isAlive() || t.getHealth() <= hp0 - 12f, "buckshot at 6 m must hit hard: hp " + t.getHealth());
            t.discard();
            h.succeed();
        });
    }

    @GameTest(template = "arena", batch = "rw_small")
    public static void r870_tube_loads_shell_by_shell(GameTestHelper h) {
        BlockPos o = h.absolutePos(new BlockPos(2, 1, 2));
        ItemStack gun = new ItemStack(ModItems.R870.get());
        FakePlayer p = shooter(h, gun, o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        p.getInventory().add(new ItemStack(ModItems.SHELL_12.get(), 7));
        for (int i = 0; i < 7; i++) Firearms.finishReload(p, gun, GunKind.R870);
        int left = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(ModItems.SHELL_12.get())) left += s.getCount();
        h.assertTrue(FirearmItem.ammo(gun) == 5, "4 in the tube + 1 chambered, got " + FirearmItem.ammo(gun));
        h.assertTrue(left == 2, "only 5 shells used (7 tries), left " + left);
        h.assertTrue(GunKind.R870.pumpAction() && GunKind.R870.tube() && GunKind.R870.semiCooldown() >= 12, "pump action, tube fed");
        h.succeed();
    }

    // ------------------------------------------------------------------ r26 WW2

    @GameTest(template = "arena", batch = "rw_small")
    public static void ppsh_mp40_open_bolt_magazines(GameTestHelper h) {
        BlockPos o = h.absolutePos(new BlockPos(2, 1, 2));
        ItemStack gun = new ItemStack(ModItems.PPSH41.get());
        FirearmItem.setAmmo(gun, 10);
        FakePlayer p = shooter(h, gun, o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        p.getInventory().add(new ItemStack(ModItems.MAG_PPSH.get()));
        Firearms.finishReload(p, gun, GunKind.PPSH41);
        h.assertTrue(FirearmItem.ammo(gun) == 71, "open bolt: a full drum = 71, nothing chambered, got " + FirearmItem.ammo(gun));
        int back = -1;
        for (ItemStack s : p.getInventory().items) if (s.is(ModItems.MAG_PPSH.get())) back = MagazineItem.rounds(s);
        h.assertTrue(back == 10, "the old drum comes back with its 10 rounds, got " + back);
        h.assertTrue(GunKind.PPSH41.selector() && !GunKind.MP40.selector() && GunKind.MP40.auto, "PPSh single/auto, MP 40 auto only");
        h.assertTrue(!GunKind.PPSH41.closedBolt() && !GunKind.MP40.closedBolt() && GunKind.MP40.maxLoaded(true) == 32, "open bolt SMGs");
        h.succeed();
    }

    @GameTest(template = "arena", batch = "rw_small")
    public static void mosin_clip_loads_only_when_empty(GameTestHelper h) {
        BlockPos o = h.absolutePos(new BlockPos(2, 1, 2));
        ItemStack gun = new ItemStack(ModItems.MOSIN.get());
        FirearmItem.setAmmo(gun, 2);
        FakePlayer p = shooter(h, gun, o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        p.getInventory().add(new ItemStack(ModItems.CLIP_MOSIN.get(), 2));
        h.assertTrue(!Firearms.reloadAllowed(p, gun, GunKind.MOSIN), "no clip into a magazine that still has rounds");
        FirearmItem.setAmmo(gun, 0);
        h.assertTrue(Firearms.reloadAllowed(p, gun, GunKind.MOSIN), "empty magazine takes a clip");
        Firearms.finishReload(p, gun, GunKind.MOSIN);
        int left = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(ModItems.CLIP_MOSIN.get())) left += s.getCount();
        h.assertTrue(FirearmItem.ammo(gun) == 5, "one clip = 5 rounds, got " + FirearmItem.ammo(gun));
        h.assertTrue(left == 1, "the clip is used up, left " + left);
        h.assertTrue(GunKind.MOSIN.boltAction() && GunKind.K98K.manual() && GunKind.K98K.clipFed() && GunKind.K98K.maxLoaded(true) == 5,
                "bolt action, 5 rounds, no +1");
        h.succeed();
    }

    @GameTest(template = "arena", batch = "rw_small", timeoutTicks = 120)
    public static void k98k_rifle_round_hits_hard(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(2, 1, 2));
        for (int dz = 0; dz < 14; dz++) for (int dy = 0; dy < 4; dy++) level.setBlock(o.offset(0, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        ItemStack gun = new ItemStack(ModItems.K98K.get());
        FirearmItem.setAmmo(gun, 5);
        FakePlayer p = shooter(h, gun, o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        Husk t = EntityType.HUSK.create(level);
        t.moveTo(o.getX() + 0.5, o.getY(), o.getZ() + 12.5, 180f, 0f);
        t.setNoAi(true);
        level.addFreshEntity(t);
        float hp0 = t.getHealth();
        Firearms.fire(p, gun, GunKind.K98K, true);
        h.assertTrue(FirearmItem.ammo(gun) == 4, "one round per shot");
        h.runAfterDelay(6, () -> {
            h.assertTrue(!t.isAlive() || t.getHealth() <= hp0 - 12f, "7.92x57 at 12 m must hit hard: hp " + t.getHealth());
            t.discard();
            h.succeed();
        });
    }
    @GameTest(template = "arena", batch = "rw_small")
    public static void scoped_rifles_single_round_topup(GameTestHelper h) {
        for (GunKind k : new GunKind[]{GunKind.MOSIN_PU, GunKind.K98K_ZF39}) {
            ItemStack gun = new ItemStack(k == GunKind.MOSIN_PU ? ModItems.MOSIN_PU.get() : ModItems.K98K_ZF39.get());
            FakePlayer p = shooter(h, gun, 0, 0, 0);
            FirearmItem.setAmmo(gun, 3);
            p.getInventory().add(new ItemStack(Firearms.ammoItem(k), 2));
            h.assertTrue(Firearms.reloadAllowed(p, gun, k), "partial scoped magazine must allow topping up");
            Firearms.finishReload(p, gun, k);
            h.assertTrue(FirearmItem.ammo(gun) == 4, "one cycle must insert one round, not a clip");
            Firearms.finishReload(p, gun, k);
            h.assertTrue(FirearmItem.ammo(gun) == 5, "internal magazine capacity is five total");
            h.assertTrue(!Firearms.reloadAllowed(p, gun, k), "no sixth round");
            h.assertTrue(k.scope() && k.boltAction() && !k.clipFed(), "scoped bolt action with no stripper clip");
            h.assertTrue(p.getInventory().countItem(Firearms.ammoItem(k)) == 0, "two loose rounds consumed");
        }
        h.succeed();
    }

    @GameTest(template = "arena", batch = "rw_small")
    public static void c96_fixed_magazine_clip(GameTestHelper h) {
        ItemStack gun = new ItemStack(ModItems.C96.get());
        FakePlayer p = shooter(h, gun, 0, 0, 0);
        p.getInventory().add(new ItemStack(ModItems.CLIP_C96.get(), 2));
        FirearmItem.setAmmo(gun, 4);
        h.assertTrue(!Firearms.reloadAllowed(p, gun, GunKind.C96), "clip requires an empty C96");
        Firearms.finishReload(p, gun, GunKind.C96);
        h.assertTrue(FirearmItem.ammo(gun) == 4, "cannot refill partly loaded C96 through public helper");
        FirearmItem.setAmmo(gun, 0);
        Firearms.finishReload(p, gun, GunKind.C96);
        h.assertTrue(FirearmItem.ammo(gun) == 10 && p.getInventory().countItem(ModItems.CLIP_C96.get()) == 1, "one clip = ten rounds");
        h.assertTrue(!GunKind.C96.manual() && !GunKind.C96.auto && GunKind.C96.maxLoaded(true) == 10, "C96 is semi-auto, not a bolt rifle or 10+1");
        h.succeed();
    }

    @GameTest(template = "arena", batch = "rw_small")
    public static void tt33_tactical_magazine_conserves_rounds(GameTestHelper h) {
        ItemStack gun = new ItemStack(ModItems.TT33.get());
        FakePlayer p = shooter(h, gun, 0, 0, 0);
        FirearmItem.setAmmo(gun, 3);
        p.getInventory().add(MagazineItem.withRounds(ModItems.MAG_TT33.get(), 8));
        Firearms.finishReload(p, gun, GunKind.TT33);
        int returned = 0;
        for (ItemStack stack : p.getInventory().items) if (stack.is(ModItems.MAG_TT33.get())) returned += MagazineItem.rounds(stack) * stack.getCount();
        h.assertTrue(FirearmItem.ammo(gun) == 9 && returned == 2, "TT: 8+1 chambered, two old magazine rounds returned");
        h.assertTrue(!GunKind.TT33.auto && !GunKind.TT33.manual(), "semi-automatic pistol");
        h.succeed();
    }

    @GameTest(template = "arena", batch = "rw_small", timeoutTicks = 120)
    public static void stage1c_all_four_fire_projectiles(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(2, 1, 2));
        for (int dz = 0; dz < 14; dz++) for (int dy = 0; dy < 4; dy++) level.setBlock(o.offset(0, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        Husk target = EntityType.HUSK.create(level);
        target.moveTo(o.getX() + 0.5, o.getY(), o.getZ() + 10.5, 0, 0);
        target.setNoAi(true); level.addFreshEntity(target);
        float hp = target.getHealth();
        for (var item : new net.minecraft.world.item.Item[]{ModItems.MOSIN_PU.get(), ModItems.K98K_ZF39.get(), ModItems.C96.get(), ModItems.TT33.get()}) {
            ItemStack gun = new ItemStack(item); GunKind k = ((FirearmItem)item).kind;
            FirearmItem.setAmmo(gun, k.cap);
            FakePlayer p = shooter(h, gun, o.getX()+0.5, o.getY(), o.getZ()+0.5);
            Firearms.fire(p, gun, k, true);
            h.assertTrue(FirearmItem.ammo(gun) == k.cap-1, "one round consumed: " + k.id);
        }
        h.assertTrue(Firearms.bullets(level) >= 4, "four real projectile paths");
        h.runAfterDelay(6, () -> { h.assertTrue(!target.isAlive() || target.getHealth() < hp, "projectiles hit target"); target.discard(); h.succeed(); });
    }

    @GameTest(template = "arena", batch = "rw_small")
    public static void stage1c_crafting_is_unambiguous(GameTestHelper h) {
        var level = h.getLevel();
        var manager = level.getRecipeManager();
        var player = shooter(h, ItemStack.EMPTY, 0, 0, 0);
        for (String name : new String[]{"mosin_pu", "k98k_zf39", "c96", "tt33",
                "round_762x54r", "round_792x57", "clip_c96", "clip_k98", "mag_tt33"}) {
            var id = new net.minecraft.resources.ResourceLocation("aiarsenal", name);
            var recipe = manager.byKey(id).orElseThrow();
            var grid = new net.minecraft.world.inventory.TransientCraftingContainer(
                    new net.minecraft.world.inventory.CraftingMenu(0, player.getInventory()), 3, 3);
            for (int i = 0; i < recipe.getIngredients().size(); i++) {
                grid.setItem(i, recipe.getIngredients().get(i).getItems()[0].copy());
            }
            var matches = manager.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)
                    .stream().filter(candidate -> candidate.matches(grid, level)).toList();
            h.assertTrue(matches.size() == 1, "ambiguous crafting for " + name + ": " + matches.size());
            h.assertTrue(matches.get(0).getId().equals(id), "wrong recipe selected for " + name);
            var output = matches.get(0).assemble(grid, level.registryAccess());
            h.assertTrue(output.is(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id)),
                    "wrong output for " + name);
        }
        var tooltip = new java.util.ArrayList<net.minecraft.network.chat.Component>();
        ModItems.C96.get().appendHoverText(new ItemStack(ModItems.C96.get()), level, tooltip,
                net.minecraft.world.item.TooltipFlag.NORMAL);
        h.assertTrue(tooltip.stream().anyMatch(line -> line.getContents() instanceof
                net.minecraft.network.chat.contents.TranslatableContents key &&
                key.getKey().equals("tooltip.aiarsenal.gun.keys_c96")), "C96 is not a manual bolt rifle");
        h.succeed();
    }

    @GameTest(template = "arena", batch = "rw_small")
    public static void era_catalog_is_complete_and_separates_ammunition(GameTestHelper h) {
        var tabs = ru.aiforge.realwar.registry.ModTabs.SECTIONS;
        var all = new java.util.HashSet<net.minecraft.world.item.Item>();
        for (var section : tabs) {
            var items = ru.aiforge.realwar.registry.ModTabs.content(section);
            h.assertTrue(new java.util.HashSet<>(items).size() == items.size(), "duplicate in " + section);
            all.addAll(items);
        }
        for (var key : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) {
            if (key.getNamespace().equals("aiarsenal")) h.assertTrue(
                    all.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key)), "unreachable item: " + key);
        }
        h.assertTrue(ru.aiforge.realwar.registry.ModTabs.content("ww2_weapons").contains(ModItems.C96.get()), "C96 WWII");
        h.assertTrue(!ru.aiforge.realwar.registry.ModTabs.content("ww2_weapons").contains(ModItems.CLIP_C96.get()), "ammo in separate tab");
        h.assertTrue(ru.aiforge.realwar.registry.ModTabs.content("ww2_ammo").contains(ModItems.CLIP_C96.get()), "C96 clip reachable");
        h.assertTrue(!ru.aiforge.realwar.registry.ModTabs.content("ww2_weapons").contains(ModItems.AK12.get()), "no modern guns in WWII");
        h.assertTrue(ru.aiforge.realwar.registry.ModTabs.content("ww2_clothing").size() == 40, "ten genuine WWII sets");
        h.assertTrue(ru.aiforge.realwar.registry.ModTabs.content("napoleonic_clothing").size() == 24, "six 1812 sets");
        h.assertTrue(!ru.aiforge.realwar.registry.ModTabs.content("ww2_clothing").contains(
            ModItems.UNIFORMS.get("uniform_su_tanker_head").get()), "Cold War tanker remains modern");
        for (String era : new String[]{"ww2_ammo", "modern_ammo"}) {
            h.assertTrue(ru.aiforge.realwar.registry.ModTabs.content(era).contains(ModItems.SHELLS_122.get()), "shared 122mm " + era);
            h.assertTrue(ru.aiforge.realwar.registry.ModTabs.content(era).contains(ModItems.SHELLS_152.get()), "shared 152mm " + era);
        }
        h.succeed();
    }

}
