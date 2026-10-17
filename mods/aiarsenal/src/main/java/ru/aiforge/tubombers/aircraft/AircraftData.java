package ru.aiforge.tubombers.aircraft;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.aiforge.tubombers.TuBombers;
import ru.aiforge.tubombers.glb.GlbModel;
import ru.aiforge.tubombers.glb.GlbModels;
import ru.aiforge.tubombers.glb.GlbPose;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Gameplay data derived from the GLB itself (both logical sides): walk zones, ladders, exits and seats are
 * marker nodes authored in Blender (WALK_*, LADDER_*, EXIT_*, SEAT_*); gear contact points and hull
 * collision points come from the actual mesh vertices; bomb stations are packed into the bay volumes.
 */
public final class AircraftData {
    private static final Map<AircraftType, AircraftData> CACHE = new EnumMap<>(AircraftType.class);

    /** Axis aligned box in model space. */
    public record Box(String name, float x0, float y0, float z0, float x1, float y1, float z1) {
        public boolean containsXZ(double x, double z, double m) {
            return x >= x0 - m && x <= x1 + m && z >= z0 - m && z <= z1 + m;
        }

        public float floor() {
            return y0;
        }

        public float cx() {
            return (x0 + x1) / 2;
        }

        public float cz() {
            return (z0 + z1) / 2;
        }
    }

    public record Seat(int index, String role, Vector3f pos, float yaw) {
        public boolean pilot() {
            return role.equals("PILOT");
        }

        public boolean canFly() {
            return role.equals("PILOT") || role.equals("COPILOT");
        }

        public boolean bombardier() {
            return role.equals("NAV");
        }
    }

    public record Ladder(Box box, Vector3f exit) {
    }

    /** A collision probe: a vertex of a mesh node, in that node's local space (moves with the animation). */
    public record HullPoint(int node, Vector3f local) {
    }

    public final AircraftType type;
    public final GlbModel model;
    public final List<Box> walk = new ArrayList<>();
    public final List<Ladder> ladders = new ArrayList<>();
    public final List<Seat> seats = new ArrayList<>();
    public final List<HullPoint> hull = new ArrayList<>();
    public final Vector3f mainGearL = new Vector3f(), mainGearR = new Vector3f(), noseGear = new Vector3f();
    public final Map<BombType, List<Vector3f>> stations = new EnumMap<>(BombType.class);
    public float wheelBase, tailLimitDeg;
    public static final float CELL = 5f;
    /** hitbox parts: {x, yBottom, z, height[, width]} in model space */
    public final List<float[]> parts = new ArrayList<>();

    public static synchronized AircraftData get(AircraftType t) {
        return CACHE.computeIfAbsent(t, AircraftData::new);
    }

    private AircraftData(AircraftType type) {
        this.type = type;
        this.model = GlbModels.get(type.glb);
        GlbPose pose = new GlbPose(model).reset().computeGlobals();
        Vector3f tmp = new Vector3f();
        Map<String, Vector3f> exits = new java.util.HashMap<>();
        List<Box> ladderBoxes = new ArrayList<>();
        for (GlbModel.Node n : model.nodes) {
            String nm = n.name == null ? "" : n.name;
            Matrix4f g = pose.global[n.index];
            if (nm.startsWith("WALK_") || nm.startsWith("LADDER_")) {
                Vector3f c = g.getTranslation(new Vector3f());
                Vector3f h = g.getScale(new Vector3f());
                Box b = new Box(nm, c.x - h.x, c.y - h.y, c.z - h.z, c.x + h.x, c.y + h.y, c.z + h.z);
                if (nm.startsWith("WALK_")) walk.add(b);
                else ladderBoxes.add(b);
            } else if (nm.startsWith("EXIT_")) {
                exits.put(nm.substring(5), g.getTranslation(new Vector3f()));
            } else if (nm.startsWith("SEAT_")) {
                Vector3f p = g.getTranslation(new Vector3f());
                Vector3f f = g.transformDirection(new Vector3f(0, 0, 1)).normalize();
                float yaw = (float) Math.toDegrees(Math.atan2(-f.x, f.z));
                seats.add(new Seat(0, nm.substring(5), p, yaw));
            }
        }
        // stable order: flight crew first
        List<String> order = List.of("PILOT", "COPILOT", "NAV", "OPERATOR", "ENG", "RADIO", "OBSERVER", "GUNNER");
        seats.sort((a, b) -> Integer.compare(idx(order, a.role), idx(order, b.role)));
        for (int i = 0; i < seats.size(); i++) {
            Seat s = seats.get(i);
            seats.set(i, new Seat(i, s.role, s.pos, s.yaw));
        }
        for (Box b : ladderBoxes) {
            Vector3f ex = exits.getOrDefault(b.name.substring(7), new Vector3f(b.cx(), 0, b.cz()));
            ladders.add(new Ladder(b, ex));
        }
        // gear contacts and hull probes from the mesh vertices
        for (GlbModel.Node n : model.nodes) {
            if (n.mesh < 0 || n.name == null) continue;
            String nm = n.name;
            Matrix4f g = pose.global[n.index];
            if (nm.startsWith("Bogie_L") || nm.startsWith("Bogie_R") || nm.startsWith("Gear_N")) {
                Vector3f low = lowest(model.meshes.get(n.mesh), g);
                if (nm.startsWith("Bogie_L")) mainGearL.set(low);
                else if (nm.startsWith("Bogie_R")) mainGearR.set(low);
                else noseGear.set(low);
                continue;
            }
            if (nm.startsWith("Int") || nm.startsWith("AB_Flame") || nm.startsWith("Gear") || nm.startsWith("Bogie")
                    || nm.contains("Door") || nm.startsWith("Ladder") || nm.startsWith("Hatch")) continue;
            addExtremes(n.index, model.meshes.get(n.mesh), g);
        }
        wheelBase = noseGear.z - (mainGearL.z + mainGearR.z) / 2;
        // geometric tail-strike angle: rotation about the main axle until the first hull probe touches the ground plane
        float mz = (mainGearL.z + mainGearR.z) / 2, my = Math.min(mainGearL.y, mainGearR.y);
        float lim = type.tailStrikeDeg;
        for (HullPoint h : hull) {
            Vector3f p = pose.global[h.node].transformPosition(new Vector3f(h.local));
            if (p.z < mz - 2) lim = Math.min(lim, (float) Math.toDegrees(Math.atan2(p.y - my, mz - p.z)) - 0.6f);
        }
        tailLimitDeg = Math.max(4f, lim);
        // hitbox parts: occupied cells of the airframe in a 5 m grid
        java.util.Map<Long, float[]> cells = new java.util.HashMap<>();
        for (GlbModel.Node n : model.nodes) {
            if (n.mesh < 0 || n.name == null || n.name.startsWith("Int") || n.name.startsWith("AB_") || n.name.startsWith("Prop")) continue;
            Matrix4f g = pose.global[n.index];
            for (GlbModel.Primitive p : model.meshes.get(n.mesh).primitives)
                for (int i = 0; i < p.vertexCount; i += 3) {
                    g.transformPosition(tmp.set(p.pos[i * 3], p.pos[i * 3 + 1], p.pos[i * 3 + 2]));
                    if (tmp.y < 0.8f) continue;
                    int cx = Math.round(tmp.x / CELL), cz = Math.round(tmp.z / CELL);
                    float[] c = cells.computeIfAbsent(((long) cx << 32) ^ (cz & 0xffffffffL), k -> new float[]{1e9f, -1e9f, 0, 0});
                    c[0] = Math.min(c[0], tmp.y);
                    c[1] = Math.max(c[1], tmp.y);
                    c[2] = cx * CELL;
                    c[3] = cz * CELL;
                }
        }
        for (float[] c : cells.values()) parts.add(new float[]{c[2], c[0], c[3], Math.max(1.2f, c[1] - c[0])});
        for (Ladder l : ladders) parts.add(new float[]{l.exit.x, 0, l.exit.z, 2.5f, 1.2f});
        for (BombType bt : BombType.values()) stations.put(bt, pack(bt));
        TuBombers.LOGGER.info("{}: {} walk zones, {} ladders, {} seats, {} hull probes, wheelbase {} m, stations {}",
                type.id, walk.size(), ladders.size(), seats.size(), hull.size(), wheelBase,
                stations.values().stream().map(List::size).toList());
    }

    private static int idx(List<String> l, String s) {
        int i = l.indexOf(s);
        return i < 0 ? 99 : i;
    }

    private static Vector3f lowest(GlbModel.Mesh mesh, Matrix4f g) {
        Vector3f best = new Vector3f(0, 1e9f, 0), v = new Vector3f();
        for (GlbModel.Primitive p : mesh.primitives)
            for (int i = 0; i < p.vertexCount; i++) {
                g.transformPosition(v.set(p.pos[i * 3], p.pos[i * 3 + 1], p.pos[i * 3 + 2]));
                if (v.y < best.y) best.set(v);
            }
        return best;
    }

    private void addExtremes(int node, GlbModel.Mesh mesh, Matrix4f g) {
        // 6 axis extremes + lowest points in 4 slices along z: enough to catch nose, tail, wingtips, fin, belly, nacelles
        int[] bi = new int[10];
        float[] bv = {1e9f, -1e9f, 1e9f, -1e9f, 1e9f, -1e9f, 1e9f, 1e9f, 1e9f, 1e9f};
        GlbModel.Primitive[] best = new GlbModel.Primitive[10];
        float zmin = 1e9f, zmax = -1e9f;
        Vector3f v = new Vector3f();
        for (GlbModel.Primitive p : mesh.primitives)
            for (int i = 0; i < p.vertexCount; i++) {
                g.transformPosition(v.set(p.pos[i * 3], p.pos[i * 3 + 1], p.pos[i * 3 + 2]));
                zmin = Math.min(zmin, v.z);
                zmax = Math.max(zmax, v.z);
            }
        for (GlbModel.Primitive p : mesh.primitives)
            for (int i = 0; i < p.vertexCount; i++) {
                g.transformPosition(v.set(p.pos[i * 3], p.pos[i * 3 + 1], p.pos[i * 3 + 2]));
                float[] val = {v.x, v.x, v.y, v.y, v.z, v.z};
                for (int k = 0; k < 6; k++) {
                    boolean better = (k % 2 == 0) ? val[k] < bv[k] : val[k] > bv[k];
                    if (better) {
                        bv[k] = val[k];
                        bi[k] = i;
                        best[k] = p;
                    }
                }
                int slice = (int) Math.min(3, Math.max(0, (v.z - zmin) / Math.max(0.01f, zmax - zmin) * 4));
                if (v.y < bv[6 + slice]) {
                    bv[6 + slice] = v.y;
                    bi[6 + slice] = i;
                    best[6 + slice] = p;
                }
            }
        for (int k = 0; k < 10; k++) {
            if (best[k] == null) continue;
            GlbModel.Primitive p = best[k];
            Vector3f loc = new Vector3f(p.pos[bi[k] * 3], p.pos[bi[k] * 3 + 1], p.pos[bi[k] * 3 + 2]);
            boolean dup = false;
            for (HullPoint h : hull) if (h.node == node && h.local.distanceSquared(loc) < 0.25f) dup = true;
            if (!dup) hull.add(new HullPoint(node, loc));
        }
    }

    /** Packs bombs of one type into the bays: lowest tier first (released first), alternating fore/aft. */
    private List<Vector3f> pack(BombType bt) {
        List<Vector3f> out = new ArrayList<>();
        int cap = type.capacity[bt.ordinal()];
        if (bt.missile()) {
            // MKU-6-5 rotary launcher: 6 stations on a drum per bay; the lowest station is released first, the drum
            // then indexes 60 degrees (bays alternate)
            for (int k = 0; k < 6; k++)
                for (AircraftType.Bay b : type.bays) {
                    if (out.size() >= cap) break;
                    double a = Math.PI / 2 + k * Math.PI / 3;            // start at the bottom (-y)
                    float rr = 0.55f, cy = (b.yBottom() + b.yTop()) / 2f;
                    out.add(new Vector3f((float) (Math.cos(a) * rr), cy - (float) (Math.sin(a) * rr), (b.z0() + b.z1()) / 2f));
                }
            return out;
        }
        if (bt.nuclear()) {
            // one free-fall bomb per bay on the centre-line; the AN602 (2.1 m) protrudes below the fuselage like on the Tu-95V
            for (AircraftType.Bay b : type.bays) {
                if (out.size() >= cap) break;
                out.add(new Vector3f(0, b.yBottom() + bt.diameter / 2f - (bt == BombType.AN602 ? 0.45f : 0f), (b.z0() + b.z1()) / 2f));
            }
            return out;
        }
        int perBay = (cap + type.bays.length - 1) / type.bays.length;
        List<List<Vector3f>> perBayLists = new ArrayList<>();
        for (AircraftType.Bay b : type.bays) {
            float gapX = bt.diameter + 0.10f, gapY = bt.diameter + 0.12f, gapZ = bt.length + 0.18f;
            int nx = Math.max(1, (int) ((2 * b.xHalf()) / gapX));
            int ny = Math.max(1, (int) ((b.yTop() - b.yBottom()) / gapY));
            int nz = Math.max(1, (int) ((b.z1() - b.z0()) / gapZ));
            List<Vector3f> l = new ArrayList<>();
            for (int iy = 0; iy < ny && l.size() < perBay; iy++) {
                float y = b.yBottom() + gapY * (iy + 0.5f);
                // fill each tier symmetric about the bay centre to keep the CG centred
                List<Integer> zs = new ArrayList<>();
                for (int k = 0; k < nz; k++) zs.add(k);
                zs.sort((a, c) -> Float.compare(Math.abs(a - (nz - 1) / 2f), Math.abs(c - (nz - 1) / 2f)));
                for (int iz : zs)
                    for (int ix = 0; ix < nx && l.size() < perBay; ix++) {
                        float x = -gapX * (nx - 1) / 2f + gapX * ix;
                        float z = (b.z0() + b.z1()) / 2 + gapZ * (iz - (nz - 1) / 2f);
                        l.add(new Vector3f(x, y, z));
                    }
            }
            perBayLists.add(l);
        }
        // interleave the bays so a series alternates between them like the real release sequence
        for (int i = 0; out.size() < cap; i++) {
            boolean any = false;
            for (List<Vector3f> l : perBayLists)
                if (i < l.size() && out.size() < cap) {
                    out.add(l.get(i));
                    any = true;
                }
            if (!any) break;
        }
        return out;
    }

    public Seat seat(String role) {
        for (Seat s : seats) if (s.role.equals(role)) return s;
        return null;
    }

    /** Floor height of the walk zone containing (x, z) whose floor is closest to y, or NaN. */
    public float floorAt(double x, double y, double z, double margin) {
        float best = Float.NaN;
        double bd = 1e9;
        for (Box b : walk)
            if (b.containsXZ(x, z, margin)) {
                double d = Math.abs(b.y0 - y);
                if (d < bd) {
                    bd = d;
                    best = b.y0;
                }
            }
        return best;
    }

    public Ladder ladderAt(double x, double z, double margin) {
        for (Ladder l : ladders) if (l.box.containsXZ(x, z, margin)) return l;
        return null;
    }
}
