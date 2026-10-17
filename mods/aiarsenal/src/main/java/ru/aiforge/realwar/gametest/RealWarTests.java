/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.CommandDispatcher
 *  com.mojang.brigadier.exceptions.CommandSyntaxException
 *  io.netty.buffer.Unpooled
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.core.Registry
 *  net.minecraft.core.RegistryAccess
 *  net.minecraft.core.Vec3i
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.gametest.framework.AfterBatch
 *  net.minecraft.gametest.framework.BeforeBatch
 *  net.minecraft.gametest.framework.GameTest
 *  net.minecraft.gametest.framework.GameTestHelper
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.damagesource.DamageType
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.animal.Pig
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 *  net.minecraft.world.item.crafting.Ingredient
 *  net.minecraft.world.item.crafting.Recipe
 *  net.minecraft.world.item.crafting.RecipeManager
 *  net.minecraft.world.item.crafting.RecipeSerializer
 *  net.minecraft.world.level.ItemLike
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.block.state.properties.Property
 *  net.minecraft.world.level.levelgen.Heightmap$Types
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 *  net.minecraftforge.gametest.GameTestHolder
 *  net.minecraftforge.gametest.PrefixGameTestTemplate
 *  net.minecraftforge.registries.ForgeRegistries
 *  net.minecraftforge.registries.RegistryObject
 *  org.joml.Vector3f
 */
package ru.aiforge.realwar.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.RealWar;
import ru.aiforge.realwar.blast.BlastManager;
import ru.aiforge.realwar.blast.BlastPhysics;
import ru.aiforge.realwar.blast.ConventionalBlast;
import ru.aiforge.realwar.blast.ModDamage;
import ru.aiforge.realwar.blast.NuclearBlast;
import ru.aiforge.realwar.blast.NuclearProfile;
import ru.aiforge.realwar.blast.RadiationManager;
import ru.aiforge.realwar.block.BombBlock;
import ru.aiforge.realwar.blockentity.BombBlockEntity;
import ru.aiforge.realwar.entity.FallingBombEntity;
import ru.aiforge.realwar.glb.GlbModel;
import ru.aiforge.realwar.glb.Pose;
import ru.aiforge.realwar.registry.BombType;
import ru.aiforge.realwar.registry.ModBlocks;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.registry.ModSounds;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector3f;

@GameTestHolder(value="aiarsenal")
@PrefixGameTestTemplate(value=false)
public final class RealWarTests {
    private static final String T = "arena";
    private static final int FLOOR = 1;
    private static final double[] SAVED = new double[3];

    private RealWarTests() {
    }

    private static ResourceLocation rl(String p) {
        return new ResourceLocation("aiarsenal", p);
    }

    private static String column(GameTestHelper h, int rx, int rz) {
        StringBuilder sb = new StringBuilder("\u043a\u043e\u043b\u043e\u043d\u043a\u0430(" + rx + "," + rz + ") y=-3..3: ");
        for (int y = -3; y <= 3; ++y) {
            sb.append(ForgeRegistries.BLOCKS.getKey(h.getLevel().getBlockState(h.absolutePos(new BlockPos(rx, y, rz))).getBlock()).getPath()).append(' ');
        }
        ServerLevel l = h.getLevel();
        BlockPos a = h.absolutePos(new BlockPos(rx, 0, rz));
        sb.append("| \u0432\u044b\u0441\u043e\u0442\u0430 MB_NL=").append(l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, a.getX(), a.getZ()) - a.getY());
        sb.append(" WS=").append(l.getHeight(Heightmap.Types.WORLD_SURFACE, a.getX(), a.getZ()) - a.getY());
        return sb.toString();
    }

    @GameTest(template="arena", batch="rw_basic")
    public static void arena_template_layout(GameTestHelper h) {
        h.assertBlock(new BlockPos(15, 1, 15), b -> b == Blocks.STONE, "\u043f\u043e\u043b \u0430\u0440\u0435\u043d\u044b \u0434\u043e\u043b\u0436\u0435\u043d \u0431\u044b\u0442\u044c \u043a\u0430\u043c\u0435\u043d\u043d\u044b\u043c \u043d\u0430 y=1");
        h.assertBlock(new BlockPos(0, 1, 30), b -> b == Blocks.STONE, "\u0443\u0433\u043e\u043b \u043f\u043e\u043b\u0430 \u0430\u0440\u0435\u043d\u044b");
        h.assertBlock(new BlockPos(15, 2, 15), b -> b == Blocks.AIR, "\u043d\u0430\u0434 \u043f\u043e\u043b\u043e\u043c \u0434\u043e\u043b\u0436\u0435\u043d \u0431\u044b\u0442\u044c \u0432\u043e\u0437\u0434\u0443\u0445");
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_basic")
    public static void registries_complete(GameTestHelper h) {
        int items = 0;
        for (RegistryObject it : ModItems.ITEMS.getEntries()) {
            h.assertTrue(it.isPresent() && !((Item)it.get()).getDescriptionId().isEmpty(), "\u043f\u0440\u0435\u0434\u043c\u0435\u0442 \u043d\u0435 \u0437\u0430\u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0438\u0440\u043e\u0432\u0430\u043d: " + String.valueOf(it.getId()));
            ++items;
        }
        for (RegistryObject b : ModBlocks.BLOCKS.getEntries()) {
            h.assertTrue(b.isPresent(), "\u0431\u043b\u043e\u043a \u043d\u0435 \u0437\u0430\u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0438\u0440\u043e\u0432\u0430\u043d: " + String.valueOf(b.getId()));
        }
        for (RegistryObject s : ModSounds.SOUNDS.getEntries()) {
            h.assertTrue(s.isPresent(), "\u0437\u0432\u0443\u043a \u043d\u0435 \u0437\u0430\u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0438\u0440\u043e\u0432\u0430\u043d: " + String.valueOf(s.getId()));
        }
        h.assertTrue(items >= 50, "\u0441\u043b\u0438\u0448\u043a\u043e\u043c \u043c\u0430\u043b\u043e \u043f\u0440\u0435\u0434\u043c\u0435\u0442\u043e\u0432: " + items);
        h.assertTrue(ForgeRegistries.BLOCK_ENTITY_TYPES.containsKey(RealWarTests.rl("bomb")), "\u043d\u0435\u0442 BlockEntity bomb");
        h.assertFalse(ForgeRegistries.BLOCK_ENTITY_TYPES.containsKey(RealWarTests.rl("machine")), "r21: machine block entity must be gone");
        h.assertTrue(ForgeRegistries.ENTITY_TYPES.containsKey(RealWarTests.rl("falling_bomb")), "\u043d\u0435\u0442 \u0441\u0443\u0449\u043d\u043e\u0441\u0442\u0438 falling_bomb");
        h.assertFalse(ForgeRegistries.MENU_TYPES.containsKey(RealWarTests.rl("machine")), "r21: machine menu must be gone");
        h.assertTrue(ForgeRegistries.MOB_EFFECTS.containsKey(RealWarTests.rl("radiation_sickness")), "\u043d\u0435\u0442 \u044d\u0444\u0444\u0435\u043a\u0442\u0430 \u043b\u0443\u0447\u0435\u0432\u043e\u0439 \u0431\u043e\u043b\u0435\u0437\u043d\u0438");
        Registry dt = h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        for (ResourceKey<DamageType> k : List.of(ModDamage.NUCLEAR_BLAST, ModDamage.NUCLEAR_THERMAL, ModDamage.BLAST, ModDamage.FRAGMENTS, ModDamage.RADIATION)) {
            h.assertTrue(dt.containsKey(k), "\u043d\u0435\u0442 \u0442\u0438\u043f\u0430 \u0443\u0440\u043e\u043d\u0430 " + String.valueOf(k.location()));
        }
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_basic")
    public static void nuke_recipes_loaded(GameTestHelper h) {
        RecipeManager rm = h.getLevel().getRecipeManager();
        h.assertTrue(rm.byKey(RealWarTests.rl("little_boy")).isPresent(), "\u043d\u0435\u0442 \u0440\u0435\u0446\u0435\u043f\u0442\u0430 Little Boy");
        h.assertTrue(rm.byKey(RealWarTests.rl("rds_4")).isPresent(), "\u043d\u0435\u0442 \u0440\u0435\u0446\u0435\u043f\u0442\u0430 \u0420\u0414\u0421-4");
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_basic")
    public static void craft_chain_reaches_both_bombs(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        RecipeManager rm = level.getRecipeManager();
        RegistryAccess ra = level.registryAccess();
        HashSet<ResourceLocation> have = new HashSet<ResourceLocation>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Recipe r : rm.getRecipes()) {
                ResourceLocation outId;
                ItemStack out = r.getResultItem(ra);
                if (out.isEmpty() || (outId = ForgeRegistries.ITEMS.getKey(out.getItem())) == null || !outId.getNamespace().equals("aiarsenal") || have.contains(outId)) continue;
                boolean ok = true;
                for (Ingredient ing : (Iterable<Ingredient>)(Iterable<?>)(r.getIngredients())) {
                    if (ing.isEmpty()) continue;
                    boolean avail = false;
                    for (ItemStack s : ing.getItems()) {
                        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
                        if (id == null || id.getNamespace().equals("aiarsenal") && !have.contains(id)) continue;
                        avail = true;
                        break;
                    }
                    if (avail) continue;
                    ok = false;
                    break;
                }
                if (!ok) continue;
                have.add(outId);
                changed = true;
            }
        }
        for (String need : List.of("steel_ingot", "steel_plate", "steel_tube", "lb_casing", "barometric_fuze", "little_boy", "rds_core", "bridgewire_detonator", "rds_casing", "rds_4", "rds1", "an602", "geiger_counter", "remote_detonator", "arming_plug", "hazmat_chestplate")) {
            h.assertTrue(have.contains(RealWarTests.rl(need)), "\u043f\u0440\u0435\u0434\u043c\u0435\u0442 \u043d\u0435\u0434\u043e\u0441\u0442\u0438\u0436\u0438\u043c \u043f\u043e \u0440\u0435\u0446\u0435\u043f\u0442\u0430\u043c: " + need);
        }
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_basic")
    public static void nuke_workbench_only(GameTestHelper h) {
        RecipeManager rm = h.getLevel().getRecipeManager();
        RegistryAccess ra = h.getLevel().registryAccess();
        int n = 0;
        for (net.minecraft.world.item.crafting.Recipe<?> r : rm.getRecipes()) {
            h.assertTrue(!r.getId().getNamespace().equals("aiarsenal") || !net.minecraftforge.registries.ForgeRegistries.RECIPE_TYPES.getKey(r.getType()).getNamespace().equals("aiarsenal"),
                    "machine recipe left: " + r.getId());
            String out = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(r.getResultItem(ra).getItem()));
            if (out.equals("aiarsenal:rds_4") || out.equals("aiarsenal:little_boy") || out.equals("aiarsenal:rds1") || out.equals("aiarsenal:an602")) {
                h.assertTrue(r.getType() == net.minecraft.world.item.crafting.RecipeType.CRAFTING, "nuke not a workbench recipe: " + r.getId());
                boolean core = false;
                for (Ingredient in : r.getIngredients()) if (!in.isEmpty() && in.getItems()[0].is(ModItems.RDS_CORE.get())) core = true;
                h.assertTrue(core, "nuke without the core: " + r.getId());
                ++n;
            }
        }
        h.assertTrue(n == 4, "nuke recipes: " + n);
        h.succeed();
    }



    @GameTest(template="arena", batch="rw_basic")
    public static void physics_matches_hiroshima(GameTestHelper h) {
        double r5 = BlastPhysics.rangeFor(34.5, 1.5E7, false);
        double r10 = BlastPhysics.rangeFor(69.0, 1.5E7, false);
        h.assertTrue(r5 > 1600.0 && r5 < 1900.0, "5 psi \u0434\u043b\u044f 15 \u043a\u0442 \u0434\u043e\u043b\u0436\u043d\u043e \u0431\u044b\u0442\u044c \u22481750 \u043c, \u0430 \u043d\u0435 " + r5);
        h.assertTrue(r10 > 900.0 && r10 < 1150.0, "10 psi \u0434\u043b\u044f 15 \u043a\u0442 \u0434\u043e\u043b\u0436\u043d\u043e \u0431\u044b\u0442\u044c \u22481000 \u043c, \u0430 \u043d\u0435 " + r10);
        double fb = BlastPhysics.fireballRadiusM(15.0);
        h.assertTrue(fb > 80.0 && fb < 160.0, "\u0440\u0430\u0434\u0438\u0443\u0441 \u043e\u0433\u043d\u0435\u043d\u043d\u043e\u0433\u043e \u0448\u0430\u0440\u0430 \u0425\u0438\u0440\u043e\u0441\u0438\u043c\u044b \u2248110 \u043c, \u0430 \u043d\u0435 " + fb);
        double q1 = BlastPhysics.thermalFluence(15.0, 1000.0);
        h.assertTrue(q1 > 20.0 && q1 < 80.0, "\u0442\u0435\u043f\u043b\u043e\u0432\u043e\u0439 \u0438\u043c\u043f\u0443\u043b\u044c\u0441 \u043d\u0430 1 \u043a\u043c: " + q1);
        double rds5 = BlastPhysics.rangeFor(34.5, 2.8E7, false);
        h.assertTrue(rds5 > r5, "28 \u043a\u0442 \u0434\u043e\u043b\u0436\u043d\u044b \u0431\u0438\u0442\u044c \u0434\u0430\u043b\u044c\u0448\u0435 15 \u043a\u0442");
        NuclearProfile p = NuclearProfile.create(15.0, false, 17.5, 300);
        for (int i = 2; i <= p.maxR; ++i) {
            h.assertTrue(p.arrival[i] >= p.arrival[i - 1], "\u0432\u0440\u0435\u043c\u044f \u043f\u0440\u0438\u0445\u043e\u0434\u0430 \u0444\u0440\u043e\u043d\u0442\u0430 \u0443\u0431\u044b\u0432\u0430\u0435\u0442 \u043d\u0430 " + i);
            h.assertTrue((double)p.pressure[i] <= (double)p.pressure[i - 1] + 0.001, "\u0434\u0430\u0432\u043b\u0435\u043d\u0438\u0435 \u0440\u0430\u0441\u0442\u0451\u0442 \u0441 \u0440\u0430\u0441\u0441\u0442\u043e\u044f\u043d\u0438\u0435\u043c \u043d\u0430 " + i);
        }
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_basic", timeoutTicks=200)
    public static void conventional_blast_effects(GameTestHelper h) {
        Vec3 c = h.absoluteVec(new Vec3(15.5, 2.5, 15.5));
        h.setBlock(new BlockPos(17, 2, 15), Blocks.OAK_PLANKS);
        h.setBlock(new BlockPos(13, 2, 15), Blocks.OBSIDIAN);
        h.setBlock(new BlockPos(15, 2, 28), Blocks.STONE);
        Pig near = (Pig)h.spawn(EntityType.PIG, new BlockPos(15, 2, 18));
        Pig far = (Pig)h.spawn(EntityType.PIG, new BlockPos(15, 2, 2));
        ConventionalBlast.explode(h.getLevel(), c, 5.0, 0.0);
        h.runAfterDelay(20L, () -> {
            h.assertBlock(new BlockPos(17, 2, 15), b -> b == Blocks.AIR, "\u0434\u043e\u0441\u043a\u0438 \u0432 2 \u0431\u043b\u043e\u043a\u0430\u0445 \u043e\u0442 5 \u043a\u0433 \u0434\u043e\u043b\u0436\u043d\u044b \u0431\u044b\u0442\u044c \u0440\u0430\u0437\u0440\u0443\u0448\u0435\u043d\u044b");
            h.assertBlock(new BlockPos(13, 2, 15), b -> b == Blocks.OBSIDIAN, "\u043e\u0431\u0441\u0438\u0434\u0438\u0430\u043d \u0432 2 \u0431\u043b\u043e\u043a\u0430\u0445 \u0434\u043e\u043b\u0436\u0435\u043d \u0432\u044b\u0441\u0442\u043e\u044f\u0442\u044c");
            h.assertBlock(new BlockPos(15, 2, 28), b -> b == Blocks.STONE, "\u043a\u0430\u043c\u0435\u043d\u044c \u0432 13 \u0431\u043b\u043e\u043a\u0430\u0445 \u043d\u0435 \u0434\u043e\u043b\u0436\u0435\u043d \u0440\u0430\u0437\u0440\u0443\u0448\u0430\u0442\u044c\u0441\u044f");
            h.assertTrue(near.isDeadOrDying() || near.getHealth() < near.getMaxHealth(), "\u0441\u0432\u0438\u043d\u044c\u044f \u0432 3 \u0431\u043b\u043e\u043a\u0430\u0445 \u043d\u0435 \u043f\u043e\u0441\u0442\u0440\u0430\u0434\u0430\u043b\u0430");
            h.assertTrue(far.isAlive() && far.getHealth() >= far.getMaxHealth() * 0.5f, "\u0441\u0432\u0438\u043d\u044c\u044f \u0432 13 \u0431\u043b\u043e\u043a\u0430\u0445 \u043d\u0435 \u0434\u043e\u043b\u0436\u043d\u0430 \u043f\u043e\u0433\u0438\u0431\u043d\u0443\u0442\u044c \u043e\u0442 5 \u043a\u0433: " + far.getHealth());
            h.succeed();
        });
    }

    /**
     * The GameTest world is a void below the 1-block arena floor, so a surface burst has no natural ground to line the
     * crater with. Lays a dirt bed (only where there is air) under the pad: radius r, from top-2 down to top-depth.
     */
    private static String dbgInfo(ServerLevel level, BlockPos c, int top, long t0) {
        StringBuilder sb = new StringBuilder(" [L=" + NuclearBlast.dbgLining + " C=" + NuclearBlast.dbgColumns + " R=" + NuclearBlast.dbgCraterR + " G=" + NuclearBlast.dbgGroundY + " F=" + NuclearBlast.dbgFloorMin + " top=" + top + " dt=" + (level.getGameTime() - t0));
        for (int dx : new int[]{0, 6, 12, 18, 24}) {
            int y = top + 2;
            while (y > top - 20 && level.getBlockState(new BlockPos(c.getX() + dx, y, c.getZ())).isAir()) --y;
            sb.append(" x").append(dx).append(':').append(y - top).append('=').append(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(level.getBlockState(new BlockPos(c.getX() + dx, y, c.getZ())).getBlock()).getPath());
        }
        return sb.append(']').toString();
    }

    private static void groundBed(ServerLevel level, BlockPos c, int top, int r, int depth) {
        // The GameTest world is void under the arenas and neighbouring tests may leave water around:
        // build a sealed natural-ground bed so the crater is carved into real soil, not into fluid.
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int rr = r + 6;
        for (int dx = -rr; dx <= rr; ++dx) {
            for (int dz = -rr; dz <= rr; ++dz) {
                int d2 = dx * dx + dz * dz;
                if (d2 > rr * rr) continue;
                for (int y = top - depth - 1; y <= top + 3; ++y) {
                    mp.set(c.getX() + dx, y, c.getZ() + dz);
                    BlockState st = level.getBlockState(mp);
                    boolean empty = st.isAir() || !st.getFluidState().isEmpty();
                    if (y <= top - 2) {
                        if (empty) level.setBlock(mp, (d2 > r * r || y == top - depth - 1) ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 2);
                    } else if (y == top - 1) {
                        if (empty) level.setBlock(mp, Blocks.STONE.defaultBlockState(), 2);
                    } else if (d2 > (r + 2) * (r + 2) && y <= top + 1) {
                        level.setBlock(mp, Blocks.STONE.defaultBlockState(), 2);
                    } else if (!st.getFluidState().isEmpty()) {
                        level.setBlock(mp, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static int surfacePad(GameTestHelper h, int rx, int rz, int half) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(rx, 0, rz));
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        for (int dx = -half; dx <= half; ++dx) {
            for (int dz = -half; dz <= half; ++dz) {
                for (int y = top - 1; y <= top + 10; ++y) {
                    level.setBlock(new BlockPos(c.getX() + dx, y, c.getZ() + dz), y == top - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return top;
    }

    private static void nukeSetup() {
        RealWarTests.SAVED[0] = ((Integer)RWConfig.COMMON.maxBlastRadius.get()).intValue();
        RealWarTests.SAVED[1] = (Boolean)RWConfig.COMMON.nuclearEnabled.get() != false ? 1.0 : 0.0;
        RWConfig.COMMON.maxBlastRadius.set(32);
        RWConfig.COMMON.nuclearEnabled.set(true);
    }

    private static void nukeTeardown() {
        RWConfig.COMMON.maxBlastRadius.set(((int)SAVED[0]));
        RWConfig.COMMON.nuclearEnabled.set(SAVED[1] > 0.0);
        RWConfig.COMMON_SPEC.save();
    }

    @BeforeBatch(batch="rw_nuke_small")
    public static void nukeSmallSetup(ServerLevel level) {
        RealWarTests.nukeSetup();
    }

    @AfterBatch(batch="rw_nuke_small")
    public static void nukeSmallTeardown(ServerLevel level) {
        RealWarTests.nukeTeardown();
    }

    @BeforeBatch(batch="rw_nuke_lb")
    public static void nukeLbSetup(ServerLevel level) {
        RealWarTests.nukeSetup();
    }

    @AfterBatch(batch="rw_nuke_lb")
    public static void nukeLbTeardown(ServerLevel level) {
        RealWarTests.nukeTeardown();
    }

    @BeforeBatch(batch="rw_nuke_air")
    public static void nukeAirSetup(ServerLevel level) {
        RealWarTests.nukeSetup();
    }

    @AfterBatch(batch="rw_nuke_air")
    public static void nukeAirTeardown(ServerLevel level) {
        RealWarTests.nukeTeardown();
    }

    @GameTest(template="arena", batch="rw_nuke_small", timeoutTicks=400)
    public static void nuclear_surface_burst_small(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = RealWarTests.surfacePad(h, 15, 15, 6);
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        BlockPos center = new BlockPos(c.getX(), top - 1, c.getZ());
        h.assertTrue(level.getBlockState(center).is(Blocks.STONE), "\u043f\u043b\u043e\u0449\u0430\u0434\u043a\u0430 \u043d\u0435 \u043f\u043e\u0441\u0442\u0440\u043e\u0435\u043d\u0430");
        Vec3 gz = new Vec3((double)c.getX() + 0.5, (double)top + 0.5, (double)c.getZ() + 0.5);
        long changed0 = BlastManager.totalBlocksChanged;
        NuclearBlast nb = NuclearBlast.start(level, gz, 0.05, true);
        h.assertTrue(nb != null, "NuclearBlast.start \u0432\u0435\u0440\u043d\u0443\u043b null \u043f\u0440\u0438 \u0432\u043a\u043b\u044e\u0447\u0451\u043d\u043d\u044b\u0445 \u044f\u0434\u0435\u0440\u043d\u044b\u0445 \u0432\u0437\u0440\u044b\u0432\u0430\u0445");
        h.succeedWhen(() -> {
            h.assertTrue(BlastManager.active(level) == 0, "\u0432\u0437\u0440\u044b\u0432 \u0435\u0449\u0451 \u043d\u0435 \u0437\u0430\u0432\u0435\u0440\u0448\u0451\u043d: " + String.valueOf(BlastManager.describe(level)));
            h.assertTrue(BlastManager.totalBlocksChanged - changed0 > 30L, "\u0432\u0437\u0440\u044b\u0432 \u043f\u043e\u0447\u0442\u0438 \u043d\u0435 \u0438\u0437\u043c\u0435\u043d\u0438\u043b \u0431\u043b\u043e\u043a\u0438: " + (BlastManager.totalBlocksChanged - changed0));
            BlockState s = level.getBlockState(center);
            h.assertTrue(s.isAir(), "\u0432 \u044d\u043f\u0438\u0446\u0435\u043d\u0442\u0440\u0435 \u0434\u043e\u043b\u0436\u043d\u0430 \u0431\u044b\u0442\u044c \u0432\u043e\u0440\u043e\u043d\u043a\u0430, \u0430 \u0442\u0430\u043c " + String.valueOf(s));
            h.assertTrue(!RadiationManager.data((ServerLevel)level).plumes.isEmpty(), "\u043d\u0435\u0442 \u0440\u0430\u0434\u0438\u043e\u0430\u043a\u0442\u0438\u0432\u043d\u043e\u0433\u043e \u0448\u043b\u0435\u0439\u0444\u0430");
            double rate = RadiationManager.sampleRate(level, gz.x, gz.z);
            h.assertTrue(rate > 50.0, "\u0432 \u044d\u043f\u0438\u0446\u0435\u043d\u0442\u0440\u0435 \u043c\u043e\u0449\u043d\u043e\u0441\u0442\u044c \u0434\u043e\u0437\u044b \u0441\u043b\u0438\u0448\u043a\u043e\u043c \u043c\u0430\u043b\u0430: " + rate);
        });
    }

    @GameTest(template="arena", batch="rw_nuke_lb", timeoutTicks=600)
    public static void little_boy_block_detonation(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = RealWarTests.surfacePad(h, 15, 15, 10);
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        RealWarTests.groundBed(level, c, top, 30, 16);
        NuclearBlast.dbgLining = 0; NuclearBlast.dbgColumns = 0; NuclearBlast.dbgFloorMin = Integer.MAX_VALUE;
        final long t0 = level.getGameTime();
        Direction f = Direction.EAST;
        BlockPos origin = new BlockPos(c.getX() - 1, top, c.getZ());
        BlockState base = (BlockState)((Block)ModBlocks.LITTLE_BOY.get()).defaultBlockState().setValue((Property)BombBlock.FACING, f);
        for (int i = 0; i < 3; ++i) {
            level.setBlock(origin.relative(f, i), (BlockState)base.setValue((Property)BombBlock.PART, Integer.valueOf(i)), 3);
        }
        BlockEntity be = level.getBlockEntity(origin);
        h.assertTrue(be instanceof BombBlockEntity, "\u0443 \u0447\u0430\u0441\u0442\u0438 0 \u043d\u0435\u0442 BlockEntity");
        ((BombBlockEntity)be).setArmed(true);
        BlockState st = level.getBlockState(origin);
        BombBlock bomb = (BombBlock)st.getBlock();
        h.assertTrue(bomb.detonate(level, origin, st), "detonate \u0432\u0435\u0440\u043d\u0443\u043b false \u043f\u0440\u0438 \u0432\u043a\u043b\u044e\u0447\u0451\u043d\u043d\u044b\u0445 \u044f\u0434\u0435\u0440\u043d\u044b\u0445 \u0432\u0437\u0440\u044b\u0432\u0430\u0445");
        h.assertTrue(level.getBlockState(origin).isAir(), "\u043f\u043e\u0441\u043b\u0435 \u043f\u043e\u0434\u0440\u044b\u0432\u0430 \u0431\u043b\u043e\u043a \u0431\u043e\u043c\u0431\u044b \u043e\u0441\u0442\u0430\u043b\u0441\u044f");
        BlockPos center = new BlockPos(c.getX(), top - 1, c.getZ());
        h.succeedWhen(() -> {
            h.assertTrue(BlastManager.active(level) == 0, "\u0432\u0437\u0440\u044b\u0432 Little Boy \u0435\u0449\u0451 \u0438\u0434\u0451\u0442: " + String.valueOf(BlastManager.describe(level)));
            h.assertTrue(level.getBlockState(center).isAir(), "\u0432\u043e\u0440\u043e\u043d\u043a\u0438 \u043f\u043e\u0434 Little Boy \u043d\u0435\u0442: " + String.valueOf(level.getBlockState(center)));
            int lining = 0;
            for (int dx = -26; dx <= 26; ++dx) {
                for (int dz = -26; dz <= 26; ++dz) {
                    if (dx * dx + dz * dz > 676) continue;
                    for (int y = top - 16; y <= top - 2; ++y) {
                        BlockState s = level.getBlockState(new BlockPos(c.getX() + dx, y, c.getZ() + dz));
                        if (!s.is((Block)ModBlocks.SCORCHED_EARTH.get()) && !s.is((Block)ModBlocks.TRINITITE.get())) continue;
                        ++lining;
                    }
                }
            }
            h.assertTrue(lining >= 50, "\u0432 \u0432\u043e\u0440\u043e\u043d\u043a\u0435 \u0434\u043e\u043b\u0436\u043d\u0430 \u0431\u044b\u0442\u044c \u043e\u043f\u043b\u0430\u0432\u043b\u0435\u043d\u043d\u0430\u044f \u0432\u044b\u0441\u0442\u0438\u043b\u043a\u0430 (\u0442\u0440\u0438\u043d\u0438\u0442\u0438\u0442/\u0432\u044b\u0436\u0436\u0435\u043d\u043d\u0430\u044f \u0437\u0435\u043c\u043b\u044f), \u043d\u0430\u0439\u0434\u0435\u043d\u043e: " + lining + dbgInfo(level, c, top, t0));
            // r7: shallow gameplay crater - deepest point at most 7 blocks (~4-6 bowl) below ground zero, never to the void/bedrock
            h.assertTrue(NuclearBlast.dbgGroundY - NuclearBlast.dbgFloorMin <= 7, "crater too deep: " + (NuclearBlast.dbgGroundY - NuclearBlast.dbgFloorMin) + dbgInfo(level, c, top, t0));
            h.assertTrue(RadiationManager.sampleRate(level, c.getX(), c.getZ()) > 100.0, "\u043d\u0435\u0442 \u043e\u0441\u0430\u0434\u043a\u043e\u0432 \u0443 \u044d\u043f\u0438\u0446\u0435\u043d\u0442\u0440\u0430");
        });
    }

    @GameTest(template="arena", batch="rw_nuke_air", timeoutTicks=500)
    public static void armed_falling_bomb_airburst(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = RealWarTests.surfacePad(h, 15, 15, 4);
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15));
        FallingBombEntity bomb = FallingBombEntity.create((Level)level, (double)c.getX() + 0.5, top + 60, (double)c.getZ() + 0.5, BombType.RDS_4, true, Direction.NORTH);
        level.addFreshEntity((Entity)bomb);
        h.succeedWhen(() -> {
            h.assertTrue(bomb.isRemoved(), "\u0431\u043e\u043c\u0431\u0430 \u0435\u0449\u0451 \u043d\u0435 \u0441\u0440\u0430\u0431\u043e\u0442\u0430\u043b\u0430 (\u0432\u044b\u0441\u043e\u0442\u0430 \u043d\u0430\u0434 \u0437\u0435\u043c\u043b\u0451\u0439 " + (bomb.getY() - (double)top) + ")");
            double above = bomb.getY() - (double)top;
            h.assertTrue(above >= 30.0 && above <= 35.5, "\u0432\u0437\u0440\u044b\u0432\u0430\u0442\u0435\u043b\u044c \u0441\u0440\u0430\u0431\u043e\u0442\u0430\u043b \u043d\u0430 \u0432\u044b\u0441\u043e\u0442\u0435 " + above + " \u0431\u043b\u043e\u043a\u043e\u0432, \u043e\u0436\u0438\u0434\u0430\u043b\u043e\u0441\u044c \u224834");
            h.assertTrue(BlastManager.active(level) == 0, "\u0432\u043e\u0437\u0434\u0443\u0448\u043d\u044b\u0439 \u0432\u0437\u0440\u044b\u0432 \u0435\u0449\u0451 \u0438\u0434\u0451\u0442: " + String.valueOf(BlastManager.describe(level)));
        });
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @GameTest(template="arena", batch="rw_nuke_disabled", timeoutTicks=60)
    public static void nuclear_disabled_by_config(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        boolean prev = (Boolean)RWConfig.COMMON.nuclearEnabled.get();
        try {
            RWConfig.COMMON.nuclearEnabled.set(false);
            RWConfig.COMMON.nuclearEnabled.set(false);
            h.assertTrue(NuclearBlast.start(level, h.absoluteVec(new Vec3(5.0, 2.0, 5.0)), 0.05, true) == null, "\u0432\u0437\u0440\u044b\u0432 \u0437\u0430\u043f\u0443\u0441\u0442\u0438\u043b\u0441\u044f \u043f\u0440\u0438 nuclearEnabled=false");
            Direction f = Direction.SOUTH;
            BlockPos o = new BlockPos(20, 2, 10);
            RealWarTests.placeBomb(h, (BombBlock)(ModBlocks.RDS_4.get()), o, f);
            BlockPos origin = h.absolutePos(o);
            BlockState st = level.getBlockState(origin);
            RWConfig.COMMON.nuclearEnabled.set(false);
            h.assertTrue(!((BombBlock)st.getBlock()).detonate(level, origin, st), "detonate \u0441\u0440\u0430\u0431\u043e\u0442\u0430\u043b \u043f\u0440\u0438 nuclearEnabled=false");
            h.assertTrue(level.getBlockState(origin).getBlock() instanceof BombBlock, "\u0431\u043e\u043c\u0431\u0430 \u0438\u0441\u0447\u0435\u0437\u043b\u0430, \u0445\u043e\u0442\u044f \u0432\u0437\u0440\u044b\u0432 \u0437\u0430\u043f\u0440\u0435\u0449\u0451\u043d");
        }
        finally {
            RWConfig.COMMON.nuclearEnabled.set(prev);
            RWConfig.COMMON_SPEC.save();
        }
        h.succeed();
    }

    private static void placeBomb(GameTestHelper h, BombBlock block, BlockPos originRel, Direction facing) {
        BlockState base = (BlockState)block.defaultBlockState().setValue((Property)BombBlock.FACING, facing);
        for (int i = 0; i < 3; ++i) {
            h.setBlock(originRel.relative(facing, i), (BlockState)base.setValue((Property)BombBlock.PART, Integer.valueOf(i)));
        }
    }

    @GameTest(template="arena", batch="rw_basic", timeoutTicks=100)
    public static void bomb_multipart_structure(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Direction f = Direction.EAST;
        BlockPos o = new BlockPos(5, 2, 5);
        RealWarTests.placeBomb(h, (BombBlock)(ModBlocks.LITTLE_BOY.get()), o, f);
        h.runAfterDelay(6L, () -> {
            for (int i = 0; i < 3; ++i) {
                BlockPos p = h.absolutePos(o.relative(f, i));
                BlockState s = level.getBlockState(p);
                h.assertTrue(s.getBlock() instanceof BombBlock, "\u0447\u0430\u0441\u0442\u044c " + i + " \u043f\u0440\u043e\u043f\u0430\u043b\u0430 \u0441\u0430\u043c\u0430");
                h.assertTrue(BombBlock.origin(p, s).equals(h.absolutePos(o)), "origin() \u0441\u0447\u0438\u0442\u0430\u0435\u0442 \u043d\u0435\u0432\u0435\u0440\u043d\u043e \u0434\u043b\u044f \u0447\u0430\u0441\u0442\u0438 " + i);
                h.assertTrue(level.getBlockEntity(p) instanceof BombBlockEntity == (i == 0), "BlockEntity \u0434\u043e\u043b\u0436\u0435\u043d \u0431\u044b\u0442\u044c \u0442\u043e\u043b\u044c\u043a\u043e \u0443 \u0447\u0430\u0441\u0442\u0438 0 (\u0447\u0430\u0441\u0442\u044c " + i + ")");
            }
            h.setBlock(o.relative(f, 1), Blocks.AIR);
            h.runAfterDelay(4L, () -> {
                for (int i = 0; i < 3; ++i) {
                    h.assertBlock(o.relative(f, i), b -> b == Blocks.AIR, "\u043e\u0441\u0442\u0430\u0442\u043a\u0438 \u0431\u043e\u043c\u0431\u044b \u043f\u043e\u0441\u043b\u0435 \u0443\u0434\u0430\u043b\u0435\u043d\u0438\u044f \u0441\u0440\u0435\u0434\u043d\u0435\u0439 \u0447\u0430\u0441\u0442\u0438");
                }
                h.succeed();
            });
        });
    }

    @GameTest(template="arena", batch="rw_basic", timeoutTicks=100)
    public static void unsupported_bomb_becomes_falling_entity(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Direction f = Direction.EAST;
        BlockPos o = new BlockPos(10, 7, 10);
        RealWarTests.placeBomb(h, (BombBlock)(ModBlocks.RDS_4.get()), o, f);
        h.succeedWhen(() -> {
            List list = level.getEntitiesOfClass(FallingBombEntity.class, new AABB(Vec3.atLowerCornerOf((Vec3i)h.absolutePos(new BlockPos(0, 0, 0))), Vec3.atLowerCornerOf((Vec3i)h.absolutePos(new BlockPos(31, 30, 31)))));
            boolean landed = level.getBlockState(h.absolutePos(new BlockPos(10, 2, 10))).getBlock() instanceof BombBlock || level.getBlockState(h.absolutePos(new BlockPos(11, 2, 10))).getBlock() instanceof BombBlock;
            h.assertTrue(!list.isEmpty() || landed, "\u0431\u043e\u043c\u0431\u0430 \u0431\u0435\u0437 \u043e\u043f\u043e\u0440\u044b \u043d\u0435 \u0441\u0442\u0430\u043b\u0430 \u043f\u0430\u0434\u0430\u044e\u0449\u0435\u0439 \u0441\u0443\u0449\u043d\u043e\u0441\u0442\u044c\u044e \u0438 \u043d\u0435 \u043f\u0440\u0438\u0437\u0435\u043c\u043b\u0438\u043b\u0430\u0441\u044c");
            h.assertTrue(landed, "\u0431\u043e\u043c\u0431\u0430 \u0435\u0449\u0451 \u043f\u0430\u0434\u0430\u0435\u0442");
        });
    }

    @GameTest(template="arena", batch="rw_basic")
    public static void radiation_dose_and_protection(GameTestHelper h) {
        Player p = h.makeMockPlayer();
        RadiationManager.setDose(p, 0.0);
        RadiationManager.addDose(p, 120.0);
        h.assertTrue(Math.abs(RadiationManager.getDose(p) - 120.0) < 1.0E-6, "\u0434\u043e\u0437\u0430 \u043d\u0435 \u0441\u043e\u0445\u0440\u0430\u043d\u044f\u0435\u0442\u0441\u044f");
        ItemStack ki = new ItemStack((ItemLike)ModItems.POTASSIUM_IODIDE.get());
        ki.getItem().finishUsingItem(ki, (Level)h.getLevel(), (LivingEntity)p);
        h.assertTrue(Math.abs(RadiationManager.getDose(p) - 80.0) < 1.0E-6, "\u0439\u043e\u0434\u0438\u0434 \u043a\u0430\u043b\u0438\u044f \u0434\u043e\u043b\u0436\u0435\u043d \u0441\u043d\u0438\u0436\u0430\u0442\u044c \u0434\u043e\u0437\u0443 \u043d\u0430 40 \u0431\u044d\u0440: " + RadiationManager.getDose(p));
        h.assertTrue(RadiationManager.protectionFactor(p) == 1.0, "\u0431\u0435\u0437 \u043a\u043e\u0441\u0442\u044e\u043c\u0430 \u043c\u043d\u043e\u0436\u0438\u0442\u0435\u043b\u044c \u0434\u043e\u043b\u0436\u0435\u043d \u0431\u044b\u0442\u044c 1");
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack((ItemLike)ModItems.HAZMAT_HELMET.get()));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack((ItemLike)ModItems.HAZMAT_CHESTPLATE.get()));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack((ItemLike)ModItems.HAZMAT_LEGGINGS.get()));
        double three = RadiationManager.protectionFactor(p);
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack((ItemLike)ModItems.HAZMAT_BOOTS.get()));
        double four = RadiationManager.protectionFactor(p);
        h.assertTrue(three > four && four < 0.1, "\u043f\u043e\u043b\u043d\u044b\u0439 \u043a\u043e\u043c\u043f\u043b\u0435\u043a\u0442 \u0434\u043e\u043b\u0436\u0435\u043d \u0437\u0430\u0449\u0438\u0449\u0430\u0442\u044c \u0441\u0438\u043b\u044c\u043d\u0435\u0435: " + three + " / " + four);
        ServerLevel level = h.getLevel();
        Vec3 c = h.absoluteVec(new Vec3(15.5, 2.0, 15.5));
        RadiationManager.data((ServerLevel)level).plumes.clear();
        RadiationManager.addPlume(level, c, 15.0, true, 17.5);
        double at0 = RadiationManager.sampleRate(level, c.x, c.z);
        double at40 = RadiationManager.sampleRate(level, c.x + 40.0, c.z);
        h.assertTrue(at0 > 500.0, "\u0441\u043b\u0438\u0448\u043a\u043e\u043c \u0441\u043b\u0430\u0431\u0430\u044f \u0440\u0430\u0434\u0438\u0430\u0446\u0438\u044f \u0432 \u044d\u043f\u0438\u0446\u0435\u043d\u0442\u0440\u0435: " + at0);
        h.assertTrue(at0 > at40, "\u0440\u0430\u0434\u0438\u0430\u0446\u0438\u044f \u043d\u0435 \u043f\u0430\u0434\u0430\u0435\u0442 \u0441 \u0440\u0430\u0441\u0441\u0442\u043e\u044f\u043d\u0438\u0435\u043c");
        RadiationManager.data((ServerLevel)level).plumes.clear();
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_basic")
    public static void glb_models_valid(GameTestHelper h) {
        double[][] lenRange = new double[][]{{2.9, 3.1}, {2.5, 2.85}};
        int k = 0;
        for (BombType t : BombType.values()) {
            String path = "/assets/aiarsenal/models/glb/" + t.id + ".glb";
            try (InputStream in = RealWar.class.getResourceAsStream(path);){
                h.assertTrue(in != null, "\u043d\u0435\u0442 \u0440\u0435\u0441\u0443\u0440\u0441\u0430 " + path);
                GlbModel m = GlbModel.read(in);
                for (String node : List.of("bomb", "hull", "fins", "details", "cradle")) {
                    h.assertTrue(m.nodeIndex(node) >= 0, t.id + ": \u043d\u0435\u0442 \u0443\u0437\u043b\u0430 " + node);
                }
                h.assertTrue(m.images.size() == 1 && m.materials.size() == 1, t.id + ": \u043e\u0436\u0438\u0434\u0430\u043b\u0430\u0441\u044c 1 \u0442\u0435\u043a\u0441\u0442\u0443\u0440\u0430 \u0438 1 \u043c\u0430\u0442\u0435\u0440\u0438\u0430\u043b");
                h.assertTrue(m.totalTriangles() > 800 && m.totalTriangles() < 6000, t.id + ": \u0447\u0438\u0441\u043b\u043e \u0442\u0440\u0435\u0443\u0433\u043e\u043b\u044c\u043d\u0438\u043a\u043e\u0432 " + m.totalTriangles());
                Pose pose = new Pose(m);
                pose.compute();
                float minZ = Float.MAX_VALUE;
                float maxZ = -3.4028235E38f;
                float minY = Float.MAX_VALUE;
                float maxR = 0.0f;
                Vector3f v = new Vector3f();
                for (int ni = 0; ni < m.nodes.size(); ++ni) {
                    GlbModel.Node node = m.nodes.get(ni);
                    if (node.mesh < 0) continue;
                    for (GlbModel.Primitive p : m.meshes.get((int)node.mesh).prims) {
                        for (int i = 0; i < p.vertexCount(); ++i) {
                            pose.world[ni].transformPosition(p.pos[i * 3], p.pos[i * 3 + 1], p.pos[i * 3 + 2], v);
                            h.assertTrue(Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z), t.id + ": NaN \u0432 \u0432\u0435\u0440\u0448\u0438\u043d\u0430\u0445");
                            minZ = Math.min(minZ, v.z);
                            maxZ = Math.max(maxZ, v.z);
                            minY = Math.min(minY, v.y);
                            maxR = Math.max(maxR, Math.max(Math.abs(v.x), Math.abs(v.y)));
                            float nl = (float)Math.sqrt(p.nrm[i * 3] * p.nrm[i * 3] + p.nrm[i * 3 + 1] * p.nrm[i * 3 + 1] + p.nrm[i * 3 + 2] * p.nrm[i * 3 + 2]);
                            h.assertTrue(Math.abs(nl - 1.0f) < 0.02f, t.id + ": \u043d\u0435\u043d\u043e\u0440\u043c\u0438\u0440\u043e\u0432\u0430\u043d\u043d\u0430\u044f \u043d\u043e\u0440\u043c\u0430\u043b\u044c " + nl);
                            h.assertTrue(p.uv[i * 2] >= -1.0E-4f && p.uv[i * 2] <= 1.0001f && p.uv[i * 2 + 1] >= -1.0E-4f && p.uv[i * 2 + 1] <= 1.0001f, t.id + ": UV \u0432\u043d\u0435 [0,1]");
                        }
                    }
                }
                double len = maxZ - minZ;
                h.assertTrue(len >= lenRange[k][0] && len <= lenRange[k][1], t.id + ": \u0434\u043b\u0438\u043d\u0430 \u043c\u043e\u0434\u0435\u043b\u0438 " + len + " \u043c \u0432\u043d\u0435 \u0434\u0438\u0430\u043f\u0430\u0437\u043e\u043d\u0430 " + lenRange[k][0] + ".." + lenRange[k][1]);
                h.assertTrue(minY >= -0.5005f, t.id + ": \u043c\u043e\u0434\u0435\u043b\u044c \u0432\u044b\u0445\u043e\u0434\u0438\u0442 \u043d\u0438\u0436\u0435 \u043f\u043e\u043b\u0430 \u0431\u043b\u043e\u043a\u0430 (y=" + minY + ")");
                h.assertTrue(maxR <= 0.6f, t.id + ": \u043c\u043e\u0434\u0435\u043b\u044c \u0448\u0438\u0440\u0435 \u043a\u043b\u0435\u0442\u043a\u0438 \u0431\u043b\u043e\u043a\u0430: " + maxR);
            }
            catch (IOException e) {
                h.fail(t.id + ": \u043e\u0448\u0438\u0431\u043a\u0430 \u0447\u0442\u0435\u043d\u0438\u044f GLB: " + String.valueOf(e));
            }
            ++k;
        }
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_basic", timeoutTicks=100)
    public static void command_realwar(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        CommandDispatcher d = level.getServer().getCommands().getDispatcher();
        h.assertTrue(d.getRoot().getChild("realwar") != null, "\u043a\u043e\u043c\u0430\u043d\u0434\u0430 /realwar \u043d\u0435 \u0437\u0430\u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0438\u0440\u043e\u0432\u0430\u043d\u0430");
        CommandSourceStack src = level.getServer().createCommandSourceStack().withLevel(level).withPosition(h.absoluteVec(new Vec3(15.5, 1.0, 15.5)));
        try {
            h.assertTrue(d.execute("realwar blast 5", src) == 1, "realwar blast \u0432\u0435\u0440\u043d\u0443\u043b \u043d\u0435 1");
            h.assertTrue(d.execute("realwar status", src) >= 0, "realwar status");
            h.assertTrue(d.execute("realwar radiation clear", src) >= 0, "realwar radiation clear");
            h.assertTrue(!d.parse("realwar nuke little_boy air", src).getReader().canRead(), "realwar nuke little_boy air \u043d\u0435 \u0440\u0430\u0437\u043e\u0431\u0440\u0430\u043b\u0430\u0441\u044c");
            h.assertTrue(!d.parse("realwar nuke rds_4 surface 1 2 3", src).getReader().canRead(), "realwar nuke rds_4 surface <pos> \u043d\u0435 \u0440\u0430\u0437\u043e\u0431\u0440\u0430\u043b\u0430\u0441\u044c");
            h.assertTrue(!d.parse("realwar nuke 15 air", src).getReader().canRead(), "realwar nuke <\u043a\u0442> air \u043d\u0435 \u0440\u0430\u0437\u043e\u0431\u0440\u0430\u043b\u0430\u0441\u044c");
        }
        catch (CommandSyntaxException e) {
            h.fail("\u043e\u0448\u0438\u0431\u043a\u0430 \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0438\u044f \u043a\u043e\u043c\u0430\u043d\u0434\u044b: " + e.getMessage());
        }
        h.runAfterDelay(10L, () -> ((GameTestHelper)h).succeed());
    }
    @GameTest(template="arena", batch="rw_basic")
    public static void ores_removed_but_nukes_and_vehicle_ammo_retained(GameTestHelper h) {
        for (String id : List.of("uranium_ore", "deepslate_uranium_ore", "raw_uranium", "uranium_ingot")) {
            h.assertTrue(!ForgeRegistries.ITEMS.containsKey(rl(id)), "retired item still registered: " + id);
            h.assertTrue(!ForgeRegistries.BLOCKS.containsKey(rl(id)), "retired block still registered: " + id);
        }
        for (String id : List.of("little_boy", "rds_4", "rds1", "an602", "mq9", "shahed136", "fpv7", "sam_48n6", "grad_rocket", "smerch_rocket", "rocket_380", "rocket_m13", "rocket_220", "kalibr", "tomahawk", "kh101", "kh55", "yars"))
            h.assertTrue(ForgeRegistries.ITEMS.containsKey(rl(id)), "required nuke/drone/vehicle ammunition removed: " + id);
        for (var type : ru.aiforge.realwar.missile.MissileType.values())
            h.assertTrue(ForgeRegistries.ITEMS.containsKey(rl(type.itemName())), "launch-pad ammunition must remain: " + type);
        h.succeed();
    }

}

