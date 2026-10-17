package ru.aiforge.arsenal.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/**
 * Temporary chunk tickets that keep long-range drones, missiles and blast processing ticking far away from any
 * player (vanilla ticket mechanism, same as ender pearls / portals; expires automatically).
 */
public final class Tickets {
    public static final TicketType<ChunkPos> FLIGHT = TicketType.create("aiarsenal_flight", Comparator.comparingLong(ChunkPos::toLong), 60);
    public static final TicketType<ChunkPos> BLAST = TicketType.create("aiarsenal_blast", Comparator.comparingLong(ChunkPos::toLong), 100);

    private Tickets() {
    }

    /** Keeps the chunk at {@code p} (and a ring around it) entity-ticking for ~3 s; call every few ticks. */
    public static void keep(ServerLevel sl, Vec3 p, int radius) {
        ChunkPos cp = new ChunkPos((int) Math.floor(p.x) >> 4, (int) Math.floor(p.z) >> 4);
        sl.getChunkSource().addRegionTicket(FLIGHT, cp, radius, cp);
    }

    /**
     * Keeps a corridor along the flight path entity-ticking: a ticket of radius 3 makes a 3x3 chunk core entity-ticking,
     * tickets are spaced <= 40 blocks along the next ~3 s of flight so a fast missile never enters a frozen chunk.
     */
    public static void keepPath(ServerLevel sl, Vec3 p, Vec3 v) {
        keep(sl, p, 3);
        double sp = v.length();
        if (sp < 0.05) return;
        double horizon = Math.min(sp * 60, 480);          // blocks ahead
        int n = Math.min(12, (int) Math.ceil(horizon / 40));
        Vec3 d = v.normalize();
        for (int i = 1; i <= n; i++) keep(sl, p.add(d.scale(horizon * i / n)), 3);
    }

    public static void keepBlast(ServerLevel sl, int x, int z) {
        ChunkPos cp = new ChunkPos(x >> 4, z >> 4);
        sl.getChunkSource().addRegionTicket(BLAST, cp, 1, cp);
    }
}
