package ru.aiforge.realwar.gametest;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.arsenal.nuke.ChargeRegistry;
import ru.aiforge.realwar.aircraft.AirBombEntity;
import ru.aiforge.realwar.aircraft.AirBombType;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.strike.StrikeManager;
import ru.aiforge.realwar.strike.StrikeType;

/** r19: radio fire support - release solution, virtual-to-real hand-off of bombs, artillery salvos. */
@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class StrikeTests {
    private StrikeTests() {
    }

    /** The forward throw is consistent: higher / faster releases throw further, the nuke air-burst stops above the ground. */
    @GameTest(template = "arena", batch = "rw_strike", timeoutTicks = 20)
    public static void release_solution_is_physical(GameTestHelper h) {
        int fab = AirBombType.FAB500.ordinal();
        double[] a = StrikeManager.throwOf(fab, 180, 8.0, 0);
        double[] b = StrikeManager.throwOf(fab, 250, 8.0, 0);
        double[] c = StrikeManager.throwOf(fab, 180, 6.0, 0);
        // vacuum: t = sqrt(2h/g) = 6.06 s -> 121 ticks, x = 970 m; drag shortens it
        h.assertTrue(a[1] > 110 && a[1] < 135, "FAB-500 fall time " + a[1]);
        h.assertTrue(a[0] > 600 && a[0] < 970, "FAB-500 throw " + a[0]);
        h.assertTrue(b[0] > a[0] && c[0] < a[0], "throw ordering " + a[0] + " " + b[0] + " " + c[0]);
        double[] n = StrikeManager.throwOf(-1, 320, 11.5, 90);
        h.assertTrue(n[1] > 60 && n[0] > 300, "nuke drop " + n[0] + " m / " + n[1] + " t");
        h.assertTrue(new ItemStack(ModItems.RADIO.get()).getItem() instanceof ru.aiforge.realwar.item.RadioItem, "radio item");
        h.succeed();
    }

    /** A FAB-1000 pair flies in from 3 km away through unloaded chunks; all 8 bombs become real entities over the target. */
    @GameTest(template = "arena", batch = "rw_strike_boom", timeoutTicks = 900)
    public static void fab1000_pair_hands_bombs_over_target(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(7, 1, 7));
        StrikeManager.Strike s = StrikeManager.plan(l, UUID.randomUUID(), StrikeType.FAB1000, c.getX() + 0.5, c.getZ() + 0.5, 90f, ChargeRegistry.FUZE_AIR);
        h.assertTrue(s.flights.size() == 2, "two Su-34");
        boolean[] seenPlane = {false};
        double[] closest = {1e9};
        h.onEachTick(() -> {
            for (StrikeManager.Flight f : s.flights)
                if (f.entity() != null && !f.entity().isRemoved()) seenPlane[0] = true;
            for (AirBombEntity b : l.getEntitiesOfClass(AirBombEntity.class, new net.minecraft.world.phys.AABB(Vec3.atCenterOf(c), Vec3.atCenterOf(c)).inflate(160)))
                if (b.getY() < c.getY() + 12) closest[0] = Math.min(closest[0], Math.hypot(b.getX() - c.getX(), b.getZ() - c.getZ()));
        });
        h.succeedWhen(() -> {
            h.assertTrue(s.realBombs == 8, "real bombs " + s.realBombs);
            h.assertTrue(seenPlane[0], "strike aircraft shown over the target");
            h.assertTrue(closest[0] < 80, "bombs fall on the target, closest " + closest[0]);
        });
    }

    /** The battery fires 3 salvos x 6 rounds. */
    @GameTest(template = "arena", batch = "rw_strike_boom", timeoutTicks = 700)
    public static void artillery_fires_three_salvos(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(7, 1, 7));
        StrikeManager.Strike s = StrikeManager.plan(l, UUID.randomUUID(), StrikeType.ARTILLERY, c.getX() + 0.5, c.getZ() + 0.5, 0f, 0);
        h.succeedWhen(() -> h.assertTrue(s.shellsFired == 18, "shells " + s.shellsFired));
    }
}
