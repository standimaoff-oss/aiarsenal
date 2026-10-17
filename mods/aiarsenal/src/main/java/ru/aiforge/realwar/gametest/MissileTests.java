/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.authlib.GameProfile
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Vec3i
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.gametest.framework.GameTest
 *  net.minecraft.gametest.framework.GameTestHelper
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.crafting.RecipeManager
 *  net.minecraft.world.level.ItemLike
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.levelgen.Heightmap$Types
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 *  net.minecraftforge.common.util.FakePlayer
 *  net.minecraftforge.gametest.GameTestHolder
 *  net.minecraftforge.gametest.PrefixGameTestTemplate
 */
package ru.aiforge.realwar.gametest;

import ru.aiforge.realwar.RealWar;

import com.mojang.authlib.GameProfile;
import ru.aiforge.realwar.missile.LaunchComputerBlock;
import ru.aiforge.realwar.missile.LaunchComputerBlockEntity;
import ru.aiforge.realwar.missile.LaunchPadBlock;
import ru.aiforge.realwar.missile.LaunchPadBlockEntity;
import ru.aiforge.realwar.missile.LaunchPadPartBlock;
import ru.aiforge.realwar.missile.MissileEntity;
import ru.aiforge.realwar.missile.MissileItem;
import ru.aiforge.realwar.missile.MissileType;
import ru.aiforge.realwar.missile.SirenBlock;
import ru.aiforge.realwar.missile.SirenBlockEntity;
import ru.aiforge.realwar.missile.Warheads;
import ru.aiforge.realwar.registry.ModS3;
import ru.aiforge.realwar.registry.ModTabs;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(value="aiarsenal")
@PrefixGameTestTemplate(value=false)
public final class MissileTests {
    private static final String T = "arena";
    private static final String B = "rw_s3";

    private MissileTests() {
    }

    private static ServerPlayer player(GameTestHelper h, BlockPos near) {
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "rw-s3"));
        p.moveTo((double)near.getX() + 0.5, (double)near.getY(), (double)near.getZ() + 0.5);
        return p;
    }

    private static int flatten(GameTestHelper h, BlockPos c, int half) {
        ServerLevel level = h.getLevel();
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        for (int dx = -half; dx <= half; ++dx) {
            for (int dz = -half; dz <= half; ++dz) {
                for (int y = top - 1; y <= top + 40; ++y) {
                    level.setBlock(new BlockPos(c.getX() + dx, y, c.getZ() + dz), y == top - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return top;
    }

    private static List<MissileEntity> missiles(ServerLevel l, Vec3 c, double r) {
        return l.getEntitiesOfClass(MissileEntity.class, new AABB(c, c).inflate(r, 4000.0, r));
    }

    @GameTest(template="arena", batch="rw_mirv_remote", timeoutTicks=1000000)
    public static void mirv_children_finish_outside_player_chunks(GameTestHelper h) {
        ServerLevel level=h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(4,4,4)).offset(12000,0,12000);
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,origin.getX(),origin.getZ());
        Vec3 start=new Vec3(origin.getX()+.5,y+5,origin.getZ()+.5);
        Vec3 target=start.add(3100,-5,0);
        // GameTestServer.runAllTasks() is unthrottled: 1800 test ticks can be
        // only 7 seconds, not 90. Async generation must get a bounded wall-clock
        // window, without forcing the route or synchronously generating it.
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(90);
        int tasksBefore=ru.aiforge.realwar.blast.BlastManager.completedBlasts;
        long blocksBefore=ru.aiforge.realwar.blast.BlastManager.totalBlocksChanged;
        MissileEntity parent=MissileEntity.launch(level,MissileType.ICBM_MIRV,start,target);
        java.util.Set<MissileEntity> children=new java.util.HashSet<>();
        class Probe {
            void tick() {
                // Keep an empty dimension awake, not its route chunks forced.
                level.resetEmptyTime();
                for (net.minecraft.world.entity.Entity entity:level.getAllEntities())
                    if(entity instanceof MissileEntity m && m.type()==MissileType.ICBM_MIRV && m.mode()==1
                            && Math.abs(m.getZ()-origin.getZ())<300 && m.getX()>origin.getX()) children.add(m);
                boolean finished=parent.isRemoved() && children.size()==8
                        && children.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved)
                        && ru.aiforge.realwar.blast.BlastManager.completedBlasts>=tasksBefore+8
                        && ru.aiforge.realwar.blast.BlastManager.active(level)==0;
                if (finished) {
                    // Unload != impact. Require actual destruction and discard,
                    // not merely disappearing from the visible entity lookup.
                    for(var child:children)h.assertTrue(child.getRemovalReason()==net.minecraft.world.entity.Entity.RemovalReason.DISCARDED,
                            "MIRV child unloaded instead of impacting");
                    h.assertTrue(ru.aiforge.realwar.blast.BlastManager.totalBlocksChanged>blocksBefore,"no destructive impacts");
                    h.succeed();return;
                }
                if(System.nanoTime()>=deadline) {
                    String state="parent age="+parent.tickCount+" pos="+parent.position()+" "+ru.aiforge.realwar.missile.MissileChunkLeases.diagnostics(parent);
                    for(var child:children)if(!child.isRemoved())state+="; child age="+child.tickCount+" pos="+child.position()+" "+ru.aiforge.realwar.missile.MissileChunkLeases.diagnostics(child);
                    h.fail("MIRV did not complete eight impacts within 90 real seconds: "+state);return;
                }
                // One pending callback, not a million preallocated scheduled tasks.
                h.runAfterDelay(1,this::tick);
            }
        }
        h.runAfterDelay(1,new Probe()::tick);
    }

    @GameTest(template="arena", batch="rw_s3")
    public static void cluster_payload_exact_even_with_late_ticks(GameTestHelper h) {
        for (int payload : new int[]{1, 14, MissileType.CRUISE_CLUSTER.warheads, 90, 1000}) {
            int total = 0;
            for (int tick = 1; tick <= 50; tick++) {
                int count = Warheads.clusterReleases(tick, payload);
                h.assertTrue(count >= 0, "negative release count");
                if (tick > 45) h.assertTrue(count == 0, "extra submunitions after release window");
                total += count;
            }
            h.assertTrue(total == payload, "payload changed: " + payload + " -> " + total);
        }
        h.assertTrue(Warheads.clusterReleases(0, 48) == 0 && Warheads.clusterReleases(-1, 48) == 0,
                "submunitions before release starts");
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_s3")
    public static void missile_items_and_types(GameTestHelper h) {
        h.assertTrue(MissileType.values().length == 14, "\u0442\u0438\u043f\u043e\u0432 \u0440\u0430\u043a\u0435\u0442 " + MissileType.values().length);
        for (MissileType t : MissileType.values()) {
            h.assertTrue(ModS3.missileItem(t).get() instanceof MissileItem, "\u043d\u0435\u0442 \u043f\u0440\u0435\u0434\u043c\u0435\u0442\u0430 " + String.valueOf(t));
            h.assertTrue(t.cls.minRange < t.cls.maxRange, "\u0434\u0438\u0430\u043f\u0430\u0437\u043e\u043d " + String.valueOf(t));
        }
        h.assertTrue(MissileType.ICBM_HE.padLevel() == 3 && MissileType.TACTICAL_HE.padLevel() == 1 && MissileType.OPERATIONAL_HE.padLevel() == 2, "\u0443\u0440\u043e\u0432\u043d\u0438 \u043f\u043b\u043e\u0449\u0430\u0434\u043e\u043a");
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_s3")
    public static void s3_recipes_loaded(GameTestHelper h) {
        RecipeManager rm = h.getLevel().getRecipeManager();
        for (MissileType missileType : MissileType.values()) {
            h.assertTrue(rm.byKey(new ResourceLocation("aiarsenal", missileType.itemName())).isPresent(), "\u0440\u0435\u0446\u0435\u043f\u0442 \u043d\u0435 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d: " + missileType.itemName());
        }
        for (String string : new String[]{"launch_pad_1", "launch_pad_2", "launch_pad_3", "launch_computer", "siren"}) {
            h.assertTrue(rm.byKey(new ResourceLocation("aiarsenal", string)).isPresent(), "\u0440\u0435\u0446\u0435\u043f\u0442 \u043d\u0435 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d: " + string);
        }
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_s3", timeoutTicks=600)
    public static void ballistic_missile_hits_target(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos a = h.absolutePos(new BlockPos(2, 2, 2));
        int top = MissileTests.flatten(h, a, 3);
        BlockPos tgt = a.offset(260, 0, 0);
        for (int cx = (a.getX() >> 4) - 1; cx <= (tgt.getX() >> 4) + 1; ++cx) {
            l.setChunkForced(cx, a.getZ() >> 4, true);
        }
        int ttop = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tgt.getX(), tgt.getZ());
        Vec3 t = new Vec3((double)tgt.getX() + 0.5, (double)ttop, (double)tgt.getZ() + 0.5);
        MissileEntity m = MissileEntity.launch(l, MissileType.TACTICAL_HE, new Vec3((double)a.getX() + 0.5, (double)top + 1.3, (double)a.getZ() + 0.5), t);
        int[] peak = new int[]{0};
        h.onEachTick(() -> {
            if (m.isAlive()) {
                peak[0] = Math.max(peak[0], (int)m.getY());
            }
        });
        h.runAfterDelay(200L, () -> {
            h.assertTrue(!m.isAlive(), "\u0440\u0430\u043a\u0435\u0442\u0430 \u043d\u0435 \u0434\u043e\u043b\u0435\u0442\u0435\u043b\u0430 (y=" + m.getY() + ", x=" + m.getX() + ")");
            h.assertTrue(peak[0] > top + 20, "\u0430\u043f\u043e\u0433\u0435\u0439 \u0441\u043b\u0438\u0448\u043a\u043e\u043c \u043d\u0438\u0437\u043a\u0438\u0439: " + (peak[0] - top));
            h.succeed();
        });
    }

    @GameTest(template="arena", batch="rw_s3", timeoutTicks=400)
    public static void mirv_separates_into_warheads(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos a = h.absolutePos(new BlockPos(2, 2, 2));
        int top = MissileTests.flatten(h, a, 3);
        Vec3 t = new Vec3((double)a.getX() + 400.5, (double)top, (double)a.getZ() + 0.5);
        for (int cx = (a.getX() >> 4) - 1; cx <= (a.getX() + 400 >> 4) + 1; ++cx) {
            l.setChunkForced(cx, a.getZ() >> 4, true);
        }
        MissileEntity m = MissileEntity.launch(l, MissileType.TACTICAL_MIRV, new Vec3((double)a.getX() + 0.5, (double)top + 1.3, (double)a.getZ() + 0.5), t);
        h.runAfterDelay(100L, () -> {
            Vec3 c = new Vec3((double)(a.getX() + 200), (double)top, (double)a.getZ());
            long rv = MissileTests.missiles(l, c, 600.0).stream().filter(e -> e.mode() == 1).count();
            h.assertTrue(rv == 3L, "\u0431\u043e\u0435\u0433\u043e\u043b\u043e\u0432\u043e\u043a " + rv + " (\u043e\u0436\u0438\u0434\u0430\u043b\u043e\u0441\u044c 3); \u043e\u0441\u043d\u043e\u0432\u043d\u0430\u044f \u0436\u0438\u0432\u0430=" + m.isAlive() + " u");
            h.succeed();
        });
    }

    @GameTest(template="arena", batch="rw_s3", timeoutTicks=600)
    public static void pad_loads_then_launches(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos rel = new BlockPos(4, 2, 4);
        BlockPos pos = h.absolutePos(rel);
        int top = MissileTests.flatten(h, pos, 4);
        BlockPos p = pos = new BlockPos(pos.getX(), top, pos.getZ());
        l.setBlock(p, ((LaunchPadBlock)(ModS3.PAD_1.get())).defaultBlockState(), 3);
        LaunchPadBlockEntity be = (LaunchPadBlockEntity)l.getBlockEntity(p);
        h.assertTrue(be != null, "\u043d\u0435\u0442 \u0431\u043b\u043e\u043a-\u0441\u0443\u0449\u043d\u043e\u0441\u0442\u0438 \u043f\u043b\u043e\u0449\u0430\u0434\u043a\u0438");
        ServerPlayer pl = MissileTests.player(h, p.offset(2, 0, 0));
        pl.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack((ItemLike)ModS3.missileItem(MissileType.TACTICAL_HE).get()));
        be.interact((Player)pl, InteractionHand.MAIN_HAND);
        h.assertTrue(be.state() == LaunchPadBlockEntity.State.LOADING, "\u043f\u043e\u0433\u0440\u0443\u0437\u043a\u0430 \u043d\u0435 \u043d\u0430\u0447\u0430\u043b\u0430\u0441\u044c: " + String.valueOf(be.state()));
        be.interact((Player)pl, InteractionHand.MAIN_HAND);
        h.runAfterDelay(155L, () -> {
            h.assertTrue(be.ready(), "\u043f\u043b\u043e\u0449\u0430\u0434\u043a\u0430 \u043d\u0435 \u0433\u043e\u0442\u043e\u0432\u0430: " + String.valueOf(be.state()));
            Vec3 tg = new Vec3((double)p.getX() + 300.5, (double)top, (double)p.getZ() + 0.5);
            h.assertTrue(be.scheduleLaunch(tg, 0), "\u043d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0437\u0430\u043f\u043b\u0430\u043d\u0438\u0440\u043e\u0432\u0430\u0442\u044c \u043f\u0443\u0441\u043a");
        });
        h.runAfterDelay(222L, () -> {
            h.assertTrue(be.state() == LaunchPadBlockEntity.State.LAUNCH, "\u043d\u0435\u0442 \u043f\u0443\u0441\u043a\u0430: " + String.valueOf(be.state()));
            h.assertTrue(!MissileTests.missiles(l, Vec3.atCenterOf((Vec3i)p), 300.0).isEmpty(), "\u0440\u0430\u043a\u0435\u0442\u0430 \u043d\u0435 \u043f\u043e\u044f\u0432\u0438\u043b\u0430\u0441\u044c");
            h.succeed();
        });
    }

    @GameTest(template="arena", batch="rw_s3")
    public static void big_pad_rejects_nothing_small_pad_rejects_big_missile(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos p = h.absolutePos(new BlockPos(3, 2, 3));
        int top = MissileTests.flatten(h, p, 4);
        p = new BlockPos(p.getX(), top, p.getZ());
        l.setBlock(p, ((LaunchPadBlock)(ModS3.PAD_1.get())).defaultBlockState(), 3);
        LaunchPadBlockEntity be = (LaunchPadBlockEntity)l.getBlockEntity(p);
        ServerPlayer pl = MissileTests.player(h, p.offset(2, 0, 0));
        pl.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack((ItemLike)ModS3.missileItem(MissileType.ICBM_HE).get()));
        be.interact((Player)pl, InteractionHand.MAIN_HAND);
        h.assertTrue(be.state() == LaunchPadBlockEntity.State.EMPTY && be.kind() == null, "\u041c\u0411\u0420 \u043d\u0435 \u0434\u043e\u043b\u0436\u043d\u0430 \u043b\u043e\u0436\u0438\u0442\u044c\u0441\u044f \u043d\u0430 1\u00d71");
        h.assertTrue(!be.canAccept(MissileType.OPERATIONAL_HE), "\u043e\u043f\u0435\u0440\u0430\u0442\u0438\u0432\u043d\u0430\u044f \u043d\u0435 \u0434\u043e\u043b\u0436\u043d\u0430 \u043b\u043e\u0436\u0438\u0442\u044c\u0441\u044f \u043d\u0430 1\u00d71");
        h.assertTrue(be.canAccept(MissileType.CRUISE_HE), "\u043a\u0440\u044b\u043b\u0430\u0442\u0430\u044f \u0434\u043e\u043b\u0436\u043d\u0430 \u043b\u043e\u0436\u0438\u0442\u044c\u0441\u044f \u043d\u0430 1\u00d71");
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_s3")
    public static void multiblock_pad_builds_and_breaks(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos p = h.absolutePos(new BlockPos(3, 2, 3));
        int top = MissileTests.flatten(h, p, 5);
        p = new BlockPos(p.getX(), top, p.getZ());
        LaunchPadBlock b = (LaunchPadBlock)(ModS3.PAD_3.get());
        h.assertTrue(b.canFit((Level)l, p), "3\u00d73 \u0434\u043e\u043b\u0436\u043d\u043e \u043f\u043e\u043c\u0435\u0449\u0430\u0442\u044c\u0441\u044f");
        l.setBlock(p, b.defaultBlockState(), 3);
        b.setPlacedBy((Level)l, p, b.defaultBlockState(), null, new ItemStack((ItemLike)b));
        int parts = 0;
        for (int dx = 0; dx < 3; ++dx) {
            for (int dz = 0; dz < 3; ++dz) {
                if (!l.getBlockState(p.offset(dx, 0, dz)).is((Block)ModS3.PAD_PART.get())) continue;
                ++parts;
            }
        }
        h.assertTrue(parts == 8, "\u0447\u0430\u0441\u0442\u0435\u0439 \u043f\u043b\u043e\u0449\u0430\u0434\u043a\u0438 " + parts);
        l.setBlock(p.above(), Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(LaunchPadPartBlock.findMaster((Level)l, p.offset(2, 0, 2), 3) != null, "\u043c\u0430\u0441\u0442\u0435\u0440 \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d \u0438\u0437 \u0434\u0430\u043b\u044c\u043d\u0435\u0433\u043e \u0443\u0433\u043b\u0430");
        l.destroyBlock(p.offset(2, 0, 1), false);
        h.assertTrue(l.getBlockState(p).isAir(), "\u043c\u0430\u0441\u0442\u0435\u0440 \u043d\u0435 \u0438\u0441\u0447\u0435\u0437 \u043f\u043e\u0441\u043b\u0435 \u0440\u0430\u0437\u0440\u0443\u0448\u0435\u043d\u0438\u044f \u0447\u0430\u0441\u0442\u0438");
        int left = 0;
        for (int dx = 0; dx < 3; ++dx) {
            for (int dz = 0; dz < 3; ++dz) {
                if (!l.getBlockState(p.offset(dx, 0, dz)).is((Block)ModS3.PAD_PART.get())) continue;
                ++left;
            }
        }
        h.assertTrue(left == 0, "\u043e\u0441\u0442\u0430\u043b\u0438\u0441\u044c \u0447\u0430\u0441\u0442\u0438: " + left);
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_s3", timeoutTicks=900)
    public static void computer_salvo(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(4, 2, 4));
        int top = MissileTests.flatten(h, c, 14);
        BlockPos comp = new BlockPos(c.getX(), top, c.getZ());
        l.setBlock(comp, ((LaunchComputerBlock)(ModS3.COMPUTER.get())).defaultBlockState(), 3);
        BlockPos[] pads = new BlockPos[]{comp.offset(5, 0, 0), comp.offset(8, 0, 0)};
        ServerPlayer pl = MissileTests.player(h, comp.offset(1, 0, 0));
        for (BlockPos p : pads) {
            l.setBlock(p, ((LaunchPadBlock)(ModS3.PAD_1.get())).defaultBlockState(), 3);
            LaunchPadBlockEntity be = (LaunchPadBlockEntity)l.getBlockEntity(p);
            pl.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack((ItemLike)ModS3.missileItem(MissileType.TACTICAL_HE).get()));
            be.interact((Player)pl, InteractionHand.MAIN_HAND);
        }
        LaunchComputerBlockEntity ce = (LaunchComputerBlockEntity)l.getBlockEntity(comp);
        h.runAfterDelay(180L, () -> {
            h.assertTrue(ce.pads.stream().filter(pi -> pi.pos().equals(pads[0]) || pi.pos().equals(pads[1])).count() == 2L, "\u043f\u0443\u043b\u044c\u0442 \u0432\u0438\u0434\u0438\u0442 \u043f\u043b\u043e\u0449\u0430\u0434\u043e\u043a: " + ce.pads.size());
            ce.handle(pl, 7, 1, 0);
            h.assertTrue(ce.selected.contains(pads[0].asLong()) && ce.selected.contains(pads[1].asLong()), "\u0432\u044b\u0431\u0440\u0430\u043d\u043e: " + ce.selected.size());
            ce.handle(pl, 1, comp.getX() + 420, comp.getZ());
            ce.handle(pl, 2, 8, 0);
            ce.handle(pl, 4, 0, 0);
            h.assertTrue(ce.countdown < 0, "\u0431\u0435\u0437 \u0432\u0437\u0432\u043e\u0434\u0430 \u043f\u0443\u0441\u043a \u043d\u0435 \u0434\u043e\u043b\u0436\u0435\u043d \u043d\u0430\u0447\u0438\u043d\u0430\u0442\u044c\u0441\u044f");
            ce.handle(pl, 3, 0, 0);
            ce.handle(pl, 4, 0, 0);
            h.assertTrue(ce.countdown > 0, "\u043e\u0442\u0441\u0447\u0451\u0442 \u043d\u0435 \u043d\u0430\u0447\u0430\u043b\u0441\u044f");
        });
        h.runAfterDelay(500L, () -> {
            int launched = 0;
            for (BlockPos p : pads) {
                if (((LaunchPadBlockEntity)l.getBlockEntity(p)).state() != LaunchPadBlockEntity.State.LAUNCH && ((LaunchPadBlockEntity)l.getBlockEntity(p)).state() != LaunchPadBlockEntity.State.EMPTY) continue;
                ++launched;
            }
            h.assertTrue(launched == 2, "\u0443\u0448\u043b\u043e \u0440\u0430\u043a\u0435\u0442: " + launched);
            h.succeed();
        });
    }

    @GameTest(template="arena", batch="rw_s3")
    public static void penetrator_digs_shaft(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos p = h.absolutePos(new BlockPos(3, 2, 3));
        int top = MissileTests.flatten(h, p, 4);
        for (int y = top - 30; y < top; ++y) {
            for (int dx = -2; dx <= 2; ++dx) {
                for (int dz = -2; dz <= 2; ++dz) {
                    l.setBlock(new BlockPos(p.getX() + dx, y, p.getZ() + dz), Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
        Vec3 at = Warheads.dig(l, new Vec3((double)p.getX() + 0.5, (double)top, (double)p.getZ() + 0.5));
        h.assertTrue(at.y < (double)(top - 10), "\u0448\u0430\u0445\u0442\u0430 \u0441\u043b\u0438\u0448\u043a\u043e\u043c \u043c\u0435\u043b\u043a\u0430\u044f: " + ((double)top - at.y));
        h.assertTrue(l.getBlockState(new BlockPos(p.getX(), top - 8, p.getZ())).isAir(), "\u0432 \u0448\u0430\u0445\u0442\u0435 \u043e\u0441\u0442\u0430\u043b\u0441\u044f \u043a\u0430\u043c\u0435\u043d\u044c");
        h.succeed();
    }

    @GameTest(template="arena", batch="rw_s3")
    public static void siren_alarm_activates(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos p = h.absolutePos(new BlockPos(3, 2, 3));
        int top = MissileTests.flatten(h, p, 3);
        p = new BlockPos(p.getX(), top, p.getZ());
        SirenBlock b = (SirenBlock)(ModS3.SIREN.get());
        l.setBlock(p, b.defaultBlockState(), 3);
        b.setPlacedBy((Level)l, p, b.defaultBlockState(), null, new ItemStack((ItemLike)b));
        h.assertTrue(l.getBlockState(p.above()).is((Block)b), "\u0432\u0435\u0440\u0445\u043d\u044f\u044f \u043f\u043e\u043b\u043e\u0432\u0438\u043d\u0430 \u0441\u0438\u0440\u0435\u043d\u044b \u043d\u0435 \u043f\u043e\u0441\u0442\u0430\u0432\u043b\u0435\u043d\u0430");
        SirenBlockEntity be = (SirenBlockEntity)l.getBlockEntity(p);
        SirenBlockEntity.alarmAround(l, p, 20.0, 100);
        BlockPos pp = p;
        h.runAfterDelay(3L, () -> {
            h.assertTrue(be.active(), "\u0441\u0438\u0440\u0435\u043d\u0430 \u043d\u0435 \u0432\u043a\u043b\u044e\u0447\u0438\u043b\u0430\u0441\u044c");
            SirenBlockEntity.alarmAround(l, pp, 20.0, 0);
        });
        h.runAfterDelay(8L, () -> {
            h.assertTrue(!be.active(), "\u0441\u0438\u0440\u0435\u043d\u0430 \u043d\u0435 \u0432\u044b\u043a\u043b\u044e\u0447\u0438\u043b\u0430\u0441\u044c");
            h.succeed();
        });
    }

    @GameTest(template="arena", batch="rw_s3", timeoutTicks=20)
    public static void every_item_has_creative_tab(GameTestHelper h) {
        ArrayList<String> lost = new ArrayList<String>();
        for (ResourceLocation k : BuiltInRegistries.ITEM.keySet()) {
            if (!RealWar.ownsItem(k) || ModTabs.tabOf(k.getPath()) != null) continue;
            lost.add(k.getPath());
        }
        h.assertTrue(lost.isEmpty(), "\u043f\u0440\u0435\u0434\u043c\u0435\u0442\u044b \u0431\u0435\u0437 \u0432\u043a\u043b\u0430\u0434\u043a\u0438: " + String.valueOf(lost));
        Set<String> extra = ModTabs.mappedNames();
        for (ResourceLocation k : BuiltInRegistries.ITEM.keySet()) {
            extra.remove(k.getPath());
        }
        h.assertTrue(extra.isEmpty(), "\u0432\u043e \u0432\u043a\u043b\u0430\u0434\u043a\u0430\u0445 \u043d\u0435\u0441\u0443\u0449\u0435\u0441\u0442\u0432\u0443\u044e\u0449\u0438\u0435 \u043f\u0440\u0435\u0434\u043c\u0435\u0442\u044b: " + String.valueOf(extra));
        h.succeed();
    }
}

