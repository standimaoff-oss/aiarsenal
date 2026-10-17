package ru.aiforge.realwar.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.realwar.craft.AradoEntity;
import ru.aiforge.realwar.craft.BattleshipEntity;
import ru.aiforge.realwar.craft.CraftRegistry;
import ru.aiforge.realwar.craft.CraftWeapon;
import ru.aiforge.realwar.ordnance.AirTargets;
import ru.aiforge.realwar.ordnance.ShellEntity;

/**
 * Reply 10: battleship Bismarck - one scenario in one 1:1 basin (the ship is 251 m long): floats at its draft,
 * flak engages an aircraft, the 38 cm battery lays itself and lands a half-salvo near a point 260 m away,
 * the Arado is catapulted, flies and is craned back, a mob stands on the deck, the ship gets under way.
 */
@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class BattleshipTests {
    private BattleshipTests() {}

    private static int basin(GameTestHelper h, BlockPos o, int x0, int x1, int z0, int z1, int depth) {
        ServerLevel l = h.getLevel();
        for (int z = z0 - 16; z <= z1 + 16; z += 16) for (int x = x0 - 16; x <= x1 + 16; x += 16) TestChunks.force(l, Vec3.atCenterOf(o.offset(x, 0, z)));
        int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, o.getX(), o.getZ());
        BlockState water = Blocks.WATER.defaultBlockState(), stone = Blocks.STONE.defaultBlockState();
        int floor = Math.max(top - depth - 1, l.getMinBuildHeight());
        int surf = floor + depth + 1;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int x = x0 - 1; x <= x1 + 1; x++)
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean wall = x < x0 || x > x1 || z < z0 || z > z1;
                for (int y = floor; y < surf; y++) l.setBlock(mp.set(o.getX() + x, y, o.getZ() + z), y == floor || wall ? stone : water, 2);
            }
        return surf;
    }

    @GameTest(template = "arena", batch = "rw_bismarck", timeoutTicks = 2000)
    public static void bismarck_floats_fights_launches_and_steams(GameTestHelper h) {
        ServerLevel sl = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO).offset(4800, 0, 0);
        int surf = basin(h, o, -22, 22, -150, 150, 13);
        for (int z = 150; z <= 300; z += 16) TestChunks.force(sl, Vec3.atCenterOf(o.offset(0, 0, z)));
        BattleshipEntity b = CraftRegistry.BISMARCK.get().create(sl);
        b.moveTo(o.getX() + 0.5, surf - 9.3, o.getZ() + 0.5, 0f, 0f);
        b.initFresh();
        sl.addFreshEntity(b);
        TestChunks.follow(b);
        int gy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, o.getX(), o.getZ() + 260);
        Vec3 target = new Vec3(o.getX() + 0.5, gy, o.getZ() + 260.5);
        int[] tick = {0};
        int[] stage = {0};
        int[] mark = {0};
        double[] y0 = new double[3];
        List<ShellEntity> shells = new ArrayList<>();
        List<Double> miss = new ArrayList<>();
        AradoEntity[] enemy = new AradoEntity[1];
        AradoEntity[] ours = new AradoEntity[1];
        Zombie[] z = new Zombie[1];
        h.onEachTick(() -> {
            int t = ++tick[0];
            if (!b.isAlive()) h.fail("линкор исчез на этапе " + stage[0]);
            switch (stage[0]) {
                case 0 -> {
                    if (t < 60) return;
                    double draft = surf - b.getY();
                    h.assertTrue(draft > 7.0 && draft < 11.5, "осадка " + String.format("%.2f", draft) + " (нужно ~9.3)");
                    // stage 1: an aircraft 120 m abeam, 60 m up - the flak must take it on
                    AradoEntity a = CraftRegistry.ARADO.get().create(sl);
                    a.moveTo(o.getX() + 120.5, surf + 60, o.getZ() + 0.5, 0f, 0f);
                    sl.addFreshEntity(a);
                    TestChunks.follow(a);
                    enemy[0] = a;
                    b.testAim = target;
                    stage[0] = 1;
                    mark[0] = t;
                }
                case 1 -> {
                    AradoEntity a = enemy[0];
                    h.assertTrue(AirTargets.isAirTarget(a) || a.isRemoved(), "Арадо в воздухе должен быть воздушной целью");
                    if (a.isRemoved() || a.hp() < 40f) {
                        a.discard();
                        stage[0] = 2;
                        mark[0] = t;
                    } else if (t - mark[0] > 400) {
                        h.fail("зенитки не попали по самолёту за 20 с (в работе " + b.aaEngaged() + ")");
                    }
                }
                case 2 -> {
                    // 38 cm full salvo (r11: both barrels) at a point 260 blocks ahead (Anton + Bruno bear, Caesar / Dora do not)
                    if (!(b.laid(0) && b.laid(1))) {
                        if (t - mark[0] > 300) h.fail("башни ГК не навелись: A " + b.az[0] + "/" + b.el[0] + " B " + b.az[1] + "/" + b.el[1]);
                        return;
                    }
                    h.assertTrue(!b.laid(2) && !b.laid(3), "кормовые башни не могут стрелять прямо по носу");
                    int n = b.fireMain(sl, b.kind.loadIndex(CraftWeapon.SK38_HE));
                    h.assertTrue(n == 4, "r11 salvo: 2 turrets x 2 guns = 4 shots, got " + n);
                    shells.addAll(sl.getEntitiesOfClass(ShellEntity.class, b.getBoundingBox().inflate(160, 60, 160)));
                    h.assertTrue(shells.size() >= 4, "shells: " + shells.size());
                    for (ShellEntity s : shells) TestChunks.follow(s);
                    stage[0] = 3;
                    mark[0] = t;
                }
                case 3 -> {
                    for (ShellEntity s : shells) if (s.isRemoved() && s.getPersistentData().getBoolean("rw_seen") == false) {
                        s.getPersistentData().putBoolean("rw_seen", true);
                        miss.add(Math.hypot(s.getX() - target.x, s.getZ() - target.z));
                    }
                    if (miss.size() >= 2) {
                        double best = Math.min(miss.get(0), miss.get(1));
                        h.assertTrue(best < 30, "промах ГК: " + String.format("%.1f / %.1f", miss.get(0), miss.get(1)) + " блоков");
                        h.assertTrue(b.reload[0] > 0 && b.reload[1] > 0 && b.reload[2] > 0 && b.reload[3] > 0, "both barrels of A and B must be reloading");
                        int li = b.kind.loadIndex(CraftWeapon.ARADO);
                        h.assertTrue(b.launchArado(sl, li), "катапульта не запустила Арадо");
                        h.assertTrue(b.ammo(li) == 3 && b.aradosAirborne() == 1, "учёт Арадо: " + b.ammo(li) + " / " + b.aradosAirborne());
                        List<AradoEntity> ar = sl.getEntitiesOfClass(AradoEntity.class, b.getBoundingBox().inflate(60, 40, 60), a -> a.ownerId() == b.getId());
                        h.assertTrue(ar.size() == 1, "свой Арадо не найден");
                        ours[0] = ar.get(0);
                        TestChunks.follow(ours[0]);
                        y0[0] = ours[0].getY();
                        stage[0] = 4;
                        mark[0] = t;
                    } else if (t - mark[0] > 600) {
                        h.fail("снаряды не упали за 30 с: " + miss.size());
                    }
                }
                case 4 -> {
                    AradoEntity a = ours[0];
                    if (t - mark[0] == 100) {
                        h.assertTrue(a.isAlive() && a.airborne(), "Арадо разбился после взлёта");
                        h.assertTrue(a.getY() > y0[0] + 3, "Арадо не набирает высоту: " + String.format("%.1f -> %.1f", y0[0], a.getY()));
                        h.assertTrue(a.speed() > 30, "скорость Арадо " + a.speed());
                        // sent home: alights beside the ship, taxis to the crane, is hoisted in
                        a.moveTo(b.getX() + 40, a.getY(), b.getZ() - 10, a.getYRot(), 0f);
                        a.forceState(AradoEntity.S_WATER);
                    }
                    if (t - mark[0] > 100 && a.isRemoved()) {
                        int li = b.kind.loadIndex(CraftWeapon.ARADO);
                        h.assertTrue(b.ammo(li) == 4 && b.aradosAirborne() == 0, "Арадо не поднят на борт: " + b.ammo(li) + " / " + b.aradosAirborne());
                        // a mob on the forecastle deck
                        Zombie zo = EntityType.ZOMBIE.create(sl);
                        Vec3 p = b.localToWorld(0, 19, 95);
                        zo.moveTo(p.x, p.y, p.z, 0f, 0f);
                        zo.setNoAi(true);
                        // NoAI mobs do not fall: put it straight onto the forecastle surface under the spawn point
                        double[] sf = ru.aiforge.realwar.deck.Decks.surface(ru.aiforge.realwar.deck.Decks.shape(b), ru.aiforge.realwar.deck.Decks.pose(b), zo, zo.getY(), 8.0);
                        if (sf != null) zo.setPos(zo.getX(), sf[0], zo.getZ());
                        sl.addFreshEntity(zo);
                        z[0] = zo;
                        stage[0] = 5;
                        mark[0] = t;
                    } else if (t - mark[0] > 400) {
                        h.fail("Арадо так и не подняли на борт");
                    }
                }
                case 5 -> {
                    Zombie zo = z[0];
                    if (t - mark[0] == 40) {
                        double rel = zo.getY() - b.getY();
                        h.assertTrue(rel > 14.0 && rel < 18.5, "зомби не стоит на баке: высота над килем " + String.format("%.2f", rel));
                        y0[1] = b.getZ();
                        y0[2] = zo.getZ();
                        b.testAim = null;
                        b.setEngine(true);
                        b.setForcedInput(1f);
                    }
                    if (t - mark[0] == 340) {
                        double moved = b.getZ() - y0[1];
                        h.assertTrue(moved > 2.0, "линкор не дал ход: " + String.format("%.2f", moved));
                        h.assertTrue(b.getY() > surf - 12, "линкор тонет");
                        double carried = zo.getZ() - y0[2];
                        h.assertTrue(Math.abs(carried - moved) < 1.5 && zo.getY() - b.getY() > 14.0, "deck carry: " + String.format("%.2f vs %.2f rem=%b alive=%b rel=%.2f surf=%b pitch=%.3f roll=%.3f", carried, moved, zo.isRemoved(), zo.isAlive(), zo.getY() - b.getY(), ru.aiforge.realwar.deck.Decks.surface(ru.aiforge.realwar.deck.Decks.shape(b), ru.aiforge.realwar.deck.Decks.pose(b), zo, zo.getY(), 0.6) != null, b.pitch, b.roll));
                        zo.discard();
                        b.discard();
                        h.succeed();
                    }
                }
                default -> { }
            }
        });
    }
}
