package ru.aiforge.realwar.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The GameTestServer has no players: only each arena's own chunks are force-loaded, so anything that flies past them
 * (missiles, bombers, bullets) stops ticking / cannot be found.  Tests use this to keep their flight paths loaded.
 */
@Mod.EventBusSubscriber(modid = "aiarsenal")
public final class TestChunks {
    private static final List<Entity> FOLLOW = new ArrayList<>();

    private TestChunks() {
    }

    public static void force(ServerLevel level, Vec3 p) {
        ChunkPos c = new ChunkPos(BlockPos.containing(p));
        level.setChunkForced(c.x, c.z, true);
    }

    /** Force-loads every chunk along a straight line (every 8 blocks). */
    public static void path(ServerLevel level, Vec3 a, Vec3 b) {
        int n = (int) Math.ceil(a.distanceTo(b) / 8.0);
        for (int i = 0; i <= n; ++i) force(level, a.lerp(b, i / (double) Math.max(1, n)));
    }

    /** Keeps the chunk under each entity (and the one it is about to enter) force-loaded while it lives. */
    public static void follow(Entity... es) {
        for (Entity e : es) {
            if (e != null) FOLLOW.add(e);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent ev) {
        if (ev.phase != TickEvent.Phase.START || FOLLOW.isEmpty() || !(ev.level instanceof ServerLevel sl)) return;
        FOLLOW.removeIf(Entity::isRemoved);   // LevelTickEvent fires per dimension: only drop dead entities
        for (Entity e : FOLLOW) {
            if (e.level() != sl) continue;
            // the whole corridor ~12 ticks ahead: a freshly forced chunk needs a few ticks to become entity-ticking
            path(sl, e.position(), e.position().add(e.getDeltaMovement().scale(12.0)));
        }
    }
}
