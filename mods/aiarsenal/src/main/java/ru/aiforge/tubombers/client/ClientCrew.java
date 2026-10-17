package ru.aiforge.tubombers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import ru.aiforge.tubombers.aircraft.AircraftData;
import ru.aiforge.tubombers.entity.BomberEntity;
import ru.aiforge.tubombers.entity.Crew;
import ru.aiforge.tubombers.network.CrewActionPacket;
import ru.aiforge.tubombers.network.CrewMovePacket;
import ru.aiforge.tubombers.network.CrewSyncPacket;
import ru.aiforge.tubombers.network.ModNetwork;

import java.util.HashMap;
import java.util.Map;

/**
 * Walking inside the aircraft: the local player's own position (model space) is predicted on the client from the
 * normal movement keys, constrained to the walk zones authored in Blender, and reported to the server.
 */
public final class ClientCrew {
    public static final float WALK_SPEED = 0.115f, LADDER_SPEED = 0.085f;
    private static boolean lastSneak;
    private static int sendCooldown;

    private ClientCrew() {
    }

    public static void onSync(CrewSyncPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !(mc.level.getEntity(p.vehicle()) instanceof BomberEntity b)) return;
        int me = mc.player.getId();
        Crew mine = b.crew.get(me);
        Map<Integer, Crew> old = new HashMap<>(b.crew);
        b.crew.clear();
        p.crew().forEach((id, c) -> {
            if (id == me && mine != null && p.forced() != me && mine.mode != Crew.SEAT && c.mode != Crew.SEAT) {
                b.crew.put(id, mine); // our own walking position is client-authoritative
                return;
            }
            Crew o = old.get(id);
            if (o != null && o.mode == c.mode) {
                c.px = o.x;
                c.py = o.y;
                c.pz = o.z;
            }
            b.crew.put(id, c);
        });
    }

    /** Called from BomberEntity.positionRider for the local player every client tick. */
    public static void walk(BomberEntity b, Crew c, Entity e) {
        if (!(e instanceof LocalPlayer p)) return;
        c.px = c.x;
        c.py = c.y;
        c.pz = c.z;
        Input in = p.input;
        boolean sneak = in.shiftKeyDown;
        boolean sneakEdge = sneak && !lastSneak;
        lastSneak = sneak;
        AircraftData d = b.data;
        if (c.mode == Crew.SEAT) {
            if (sneakEdge && Minecraft.getInstance().screen == null)
                ModNetwork.CHANNEL.sendToServer(new CrewActionPacket(b.getId(), BomberEntity.A_STAND, 0));
            return;
        }
        int mode0 = c.mode, idx0 = c.index;
        float x0 = c.x, y0 = c.y, z0 = c.z;
        float rel = (float) Math.toRadians(Mth.wrapDegrees(p.getYRot() - b.getYRot()));
        float fwd = in.forwardImpulse, str = in.leftImpulse;
        float sp = sneak ? WALK_SPEED * 0.45f : WALK_SPEED * (p.isSprinting() ? 1.35f : 1f);
        float dx = (str * Mth.cos(rel) - fwd * Mth.sin(rel)) * sp;
        float dz = (fwd * Mth.cos(rel) + str * Mth.sin(rel)) * sp;
        boolean doorOpen = b.hasFlag(BomberEntity.F_DOOR);
        if (c.mode == Crew.WALK) {
            tryMove(d, c, dx, 0);
            tryMove(d, c, 0, dz);
            AircraftData.Ladder l = d.ladderAt(c.x, c.z, -0.08);
            if (l != null && Math.abs(l.box().y1() - c.y) < 0.3f) {
                c.mode = Crew.LADDER;
                c.index = d.ladders.indexOf(l);
                c.y = l.box().y1();
            }
        } else if (c.mode == Crew.LADDER) {
            AircraftData.Ladder l = d.ladders.get(Mth.clamp(c.index, 0, d.ladders.size() - 1));
            float topY = l.box().y1();
            float bottom = doorOpen ? 0f : topY - 1.4f;
            boolean atTop = c.y >= topY - 0.02f;
            float vy = 0;
            if (in.jumping || (fwd > 0 && !atTop)) vy = LADDER_SPEED;
            if (sneak) vy = -LADDER_SPEED;
            c.y = Mth.clamp(c.y + vy, bottom, topY);
            if (atTop && vy <= 0 && !sneak && (dx != 0 || dz != 0)) {
                float nx = c.x + dx, nz = c.z + dz;
                float floor = d.floorAt(nx, topY, nz, 0);
                if (!l.box().containsXZ(nx, nz, -0.08) && !Float.isNaN(floor) && Math.abs(floor - topY) < 0.6f) {
                    c.mode = Crew.WALK;
                    c.x = nx;
                    c.z = nz;
                    c.y = floor;
                } else if (l.box().containsXZ(nx, nz, 0)) {
                    c.x = nx;
                    c.z = nz;
                }
            } else if (!atTop) {
                // on the rungs: centred on the ladder
                c.x += (l.box().cx() - c.x) * 0.3f;
                c.z += (l.box().cz() - c.z) * 0.3f;
            }
            if (c.y <= bottom + 0.01f && sneak) {
                if (++c.sneakTicks == 8) {
                    int a = doorOpen ? BomberEntity.A_EXIT : BomberEntity.A_BAIL;
                    ModNetwork.CHANNEL.sendToServer(new CrewActionPacket(b.getId(), a, 0));
                }
            } else c.sneakTicks = 0;
        }
        boolean changed = c.mode != mode0 || c.index != idx0 || c.x != x0 || c.y != y0 || c.z != z0;
        if (changed || --sendCooldown <= 0) {
            sendCooldown = 20;
            ModNetwork.CHANNEL.sendToServer(new CrewMovePacket(b.getId(), c.mode, c.index, c.x, c.y, c.z));
        }
    }

    private static void tryMove(AircraftData d, Crew c, float dx, float dz) {
        if (dx == 0 && dz == 0) return;
        float nx = c.x + dx, nz = c.z + dz;
        float floor = d.floorAt(nx, c.y, nz, -0.12);
        if (!Float.isNaN(floor) && Math.abs(floor - c.y) <= 0.6f) {
            c.x = nx;
            c.z = nz;
            c.y = floor;
        } else if (d.ladderAt(nx, nz, 0) != null) {
            c.x = nx;
            c.z = nz;
        }
    }

    /** nearest free seat within reach, for the hint line */
    public static AircraftData.Seat seatInReach(BomberEntity b, Crew c) {
        if (c == null || c.mode != Crew.WALK) return null;
        for (AircraftData.Seat s : b.data.seats) {
            double d = (s.pos().x - c.x) * (s.pos().x - c.x) + (s.pos().z - c.z) * (s.pos().z - c.z);
            if (d < 1.6 * 1.6 && b.occupant(s.index()) == null && Math.abs(s.pos().y - 0.5 - c.y) < 1.2) return s;
        }
        return null;
    }
}
