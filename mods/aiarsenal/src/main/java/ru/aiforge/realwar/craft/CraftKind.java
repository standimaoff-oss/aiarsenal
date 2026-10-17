package ru.aiforge.realwar.craft;

/**
 * Helicopters, boats and the submarine. All lengths are metres (= blocks), masses kg, speeds m/s.
 * Model/seat frame: +X left, +Y up, +Z forward, origin on the ground/keel under the centre of mass.
 */
public enum CraftKind {
    KA52("ka52", Cat.HELI, 3.2f, 3.4f, 300f, "models/glb/ka52.glb",
            new float[][]{{0.45f, 1.30f, 3.55f}, {-0.45f, 1.30f, 3.55f}},
            new Heli(10400, 1.45f, 83f, 7.25f, 3.65f, 4.75f, 5.1f, 22f, 30f, 85f, 50f, 60f, true, 8f, 1490f, 760f, 0f, 0f),
            null,
            new Load(CraftWeapon.GUN_2A42, 460, -1, new float[][]{{-1.05f, 1.12f, 3.35f}}, null, 2.5f, -37f, 3.5f),
            new Load(CraftWeapon.VIKHR, 12, 1, new float[][]{{2.78f, 1.25f, 1.10f}, {-2.78f, 1.25f, 1.10f}}, "atgm_", 0f, 0f, 0f),
            new Load(CraftWeapon.S8, 40, -1, new float[][]{{1.70f, 1.12f, 1.30f}, {-1.70f, 1.12f, 1.30f}}, null, 0f, 0f, 0f),
            new Load(CraftWeapon.FLARES, 64, -1, new float[][]{{3.55f, 1.55f, -0.4f}, {-3.55f, 1.55f, -0.4f}}, null, 0f, 0f, 0f)),

    AH64("ah64", Cat.HELI, 3.2f, 3.6f, 270f, "models/glb/ah64.glb",
            new float[][]{{0f, 1.95f, 2.15f}, {0f, 1.35f, 3.55f}},
            new Heli(8000, 1.45f, 81f, 7.3f, 3.85f, 0f, 5.6f, 20f, 28f, 90f, 50f, 70f, false, 8f, 1150f, 620f, 1.4f, -8.25f),
            null,
            new Load(CraftWeapon.GUN_M230, 1200, -1, new float[][]{{0f, 0.42f, 4.30f}}, null, 110f, -60f, 11f),
            new Load(CraftWeapon.HELLFIRE, 8, 1, new float[][]{{2.38f, 1.45f, 0.95f}, {-2.38f, 1.45f, 0.95f}}, "atgm_", 0f, 0f, 0f),
            new Load(CraftWeapon.HYDRA, 38, -1, new float[][]{{1.55f, 1.40f, 1.05f}, {-1.55f, 1.40f, 1.05f}}, null, 0f, 0f, 0f),
            new Load(CraftWeapon.FLARES, 60, -1, new float[][]{{0.9f, 1.3f, -2.6f}, {-0.9f, 1.3f, -2.6f}}, null, 0f, 0f, 0f)),

    RAPTOR("raptor", Cat.BOAT, 4.0f, 2.6f, 300f, "models/glb/raptor.glb",
            new float[][]{{0.55f, 2.05f, 0.9f}, {-0.55f, 2.05f, 0.9f}, {0.75f, 1.35f, -2.6f}, {-0.75f, 1.35f, -2.6f},
                    {0.75f, 1.35f, -4.1f}, {-0.75f, 1.35f, -4.1f}},
            null,
            new Water(23000, 16.9f, 4.0f, 0.9f, 2.3f, 0.75f, 62000f, 1.7e6f, 62f, 330f, 7.5f, 9000f, 22f, false, 0f, 0f, 0.95f, 0f),
            new Load(CraftWeapon.MG_KPV, 900, 1, new float[][]{{0f, 3.25f, 5.0f}}, null, 180f, -12f, 60f)),

    CB90("cb90", Cat.BOAT, 3.8f, 2.4f, 280f, "models/glb/cb90.glb",
            new float[][]{{0.55f, 2.25f, -2.3f}, {0f, 3.10f, -0.75f}, {0.7f, 1.20f, 1.6f}, {-0.7f, 1.20f, 1.6f},
                    {0.7f, 1.20f, 3.0f}, {-0.7f, 1.20f, 3.0f}},
            null,
            new Water(20500, 15.9f, 3.8f, 0.8f, 2.1f, 0.65f, 56000f, 1.25e6f, 75f, 300f, 7.0f, 8000f, 34f, true, 0f, 0f, 0.85f, 0f),
            new Load(CraftWeapon.MG_M2, 1000, 1, new float[][]{{0f, 3.70f, 0.55f}}, null, 180f, -12f, 60f)),

    SUB636("sub636", Cat.SUB, 9.9f, 9.9f, 1600f, "models/glb/sub636.glb",
            new float[][]{{0.45f, 14.0f, 9.6f}, {-0.45f, 14.0f, 9.6f}, {0f, 14.0f, 8.5f}},
            null,
            new Water(2_350_000, 73.8f, 9.9f, 6.2f, 9.9f, 0f, 420_000f, 4.1e6f, 7400f, 0f, 0f, 260_000f, 2.2f, false,
                    1_600_000f, 10.3f, 4.2f, 4.95f),
            new Load(CraftWeapon.TORPEDO, 18, -1, new float[][]{{1.1f, 5.6f, 36.6f}, {-1.1f, 5.6f, 36.6f}, {1.6f, 4.3f, 36.3f},
                    {-1.6f, 4.3f, 36.3f}, {1.1f, 3.0f, 35.9f}, {-1.1f, 3.0f, 35.9f}}, null, 0f, 0f, 0f),
            new Load(CraftWeapon.KALIBR, 4, -1, new float[][]{{1.1f, 5.6f, 36.6f}, {-1.1f, 5.6f, 36.6f}, {1.6f, 4.3f, 36.3f},
                    {-1.6f, 4.3f, 36.3f}}, null, 0f, 0f, 0f)),

    /**
     * Schlachtschiff Bismarck, May 1941 (reply 10): 251 m o.a. / 241.6 m wl, 36 m beam, 9.3 m draught at 50,300 t,
     * 3 shafts / 150,170 shp (110 MW) for 30 kn. Origin at the keel amidships. Seats: helm on the open bridge, two
     * bridge-wing lookouts. The batteries (8 x 38 cm, 12 x 15 cm, 16 x 10.5 cm, 16 x 3.7 cm, 2 cm singles + 2 Vierlinge,
     * 4 x Ar 196) are aimed and fired by BattleshipEntity; muzzles here are placeholders for the shared code paths.
     */
    BISMARCK("bismarck", Cat.BOAT, 34.0f, 15.2f, 12000f, "models/glb/bismarck.glb",
            new float[][]{{0f, 31.25f, 22.4f}, {6.2f, 31.25f, 20.6f}, {-6.2f, 31.25f, 20.6f}},
            null,
            new Water(5.03e7f, 241.6f, 36.0f, 9.3f, 15.1f, 2.0f, 5.0e6f, 1.10e8f, 16600f, 0f, 60f, 1.2e6f, 1.6f, false,
                    0f, 0f, 9.0f, 0f),
            new Load(CraftWeapon.SK38_AP, 472, 0, new float[][]{{0f, 20f, 70f}}, null, 0f, 0f, 0f),
            new Load(CraftWeapon.SK38_HE, 468, 0, new float[][]{{0f, 20f, 70f}}, null, 0f, 0f, 0f),
            new Load(CraftWeapon.SK15, 1800, 0, new float[][]{{14.7f, 17.4f, 9.3f}}, null, 0f, 0f, 0f),
            new Load(CraftWeapon.FLAK105, 6400, 0, new float[][]{{11f, 21f, 0f}}, null, 0f, 0f, 0f),
            new Load(CraftWeapon.FLAK37, 32000, 0, new float[][]{{7.4f, 20.8f, -46.8f}}, null, 0f, 0f, 0f),
            new Load(CraftWeapon.FLAK20, 40000, 0, new float[][]{{11.6f, 20.6f, -28.4f}}, null, 0f, 0f, 0f),
            new Load(CraftWeapon.ARADO, 4, 0, new float[][]{{0f, 21.2f, -10f}}, null, 0f, 0f, 0f)),

    // ------------------------------------------------------------------ WW2 aircraft (reply 11), real dimensions
    /** Messerschmitt Bf 109 G-6: DB 605A 1475 PS, 3150 kg, span 9.92 m, MG 151/20 + 2 x MG 131. */
    BF109("bf109", Cat.PLANE, 3.4f, 2.6f, 150f, "models/glb/bf109.glb",
            new float[][]{{0f, 1.53f, -0.98f}},
            null, null,
            new Plane(3150, 300, 330, 16.05f, 9.92f, 9.02f, 1.42f, 15.5f, 1.085e6f, 1, 1.50f, 147f, 105f, 34f, 22f, 7.0f,
                    12f, 1, false, 0.035f, null),
            new Load(CraftWeapon.MG151, 150, 0, new float[][]{{0f, 1.70f, 3.10f}}, null, 0f, 0f, 0f),
            new Load(CraftWeapon.MG131, 600, 0, new float[][]{{0.16f, 2.08f, 2.30f}, {-0.16f, 2.08f, 2.30f}}, null, 0f, 0f, 0f)),
    /** Yakovlev Yak-3 (VK-105PF2 1290 hp): 2692 kg, span 9.20 m, ShVAK + 2 x UBS. */
    YAK3("yak3", Cat.PLANE, 3.2f, 2.5f, 135f, "models/glb/yak3.glb",
            new float[][]{{0f, 1.50f, -0.95f}},
            null, null,
            new Plane(2692, 280, 300, 14.85f, 9.20f, 8.50f, 1.48f, 16f, 0.962e6f, 1, 1.43f, 157f, 125f, 38f, 24f, 7.5f,
                    11f, -1, true, 0.032f, null),
            new Load(CraftWeapon.SHVAK, 120, 0, new float[][]{{0f, 1.62f, 2.95f}}, null, 0f, 0f, 0f),
            new Load(CraftWeapon.UBS, 300, 0, new float[][]{{0.13f, 1.98f, 2.15f}, {-0.13f, 1.98f, 2.15f}}, null, 0f, 0f, 0f)),
    /** Heinkel He 111 H-6: 2 x Jumo 211F-1, 14 t loaded, span 22.6 m, 2000 kg bombs, 5 crew. */
    HE111("he111", Cat.PLANE, 6.0f, 4.2f, 900f, "models/glb/he111.glb",
            new float[][]{{0.32f, 2.40f, 2.85f}, {-0.30f, 2.30f, 3.70f}, {0f, 2.75f, -2.55f}, {0.25f, 2.25f, -0.95f}, {-0.25f, 2.25f, -1.6f}},
            null, null,
            new Plane(11000, 1650, 680, 87.6f, 22.6f, 16.4f, 1.50f, 15f, 1.972e6f, 2, 1.75f, 101f, 42f, 15f, 10f, 3.8f,
                    12f, 0, false, 0.030f, new float[][]{
                    {0.08f, 2.52f, 4.42f, 0f, -40f, 40f, -35f, 30f, 0f, 0.85f},
                    {0f, 3.50f, -2.75f, 180f, 115f, 245f, -5f, 65f, 1f, 1.10f},
                    {0f, 1.45f, -1.18f, 180f, 135f, 225f, -50f, 3f, 0f, 0.85f}}),
            new Load(CraftWeapon.MGFF, 180, 0, new float[][]{{0f, 1.52f, 2.40f}}, null, 0f, 0f, 0f),
            new Load(CraftWeapon.SC250, 8, 0, new float[][]{{0.33f, 2.48f, 0.45f}, {-0.33f, 2.48f, 0.45f}, {0.33f, 2.48f, -0.17f}, {-0.33f, 2.48f, -0.17f}, {0.33f, 2.48f, -0.79f}, {-0.33f, 2.48f, -0.79f}, {0.33f, 2.48f, -1.41f}, {-0.33f, 2.48f, -1.41f}}, "bomb_", 0f, 0f, 0f),
            new Load(CraftWeapon.SC1000, 2, 0, new float[][]{{0.45f, 1.36f, -0.5f}, {-0.45f, 1.36f, -0.5f}}, "bombx_", 0f, 0f, 0f),
            new Load(CraftWeapon.GUNNERS, 3000, 9, new float[][]{{0f, 2.5f, 0f}}, null, 0f, 0f, 0f)),
    /** Petlyakov Pe-8 (ANT-42), 4 x AM-35A: 27 t, span 39.13 m, FAB-250 / FAB-1000 / FAB-5000NG, 11 crew. */
    PE8("pe8", Cat.PLANE, 8.0f, 5.0f, 1500f, "models/glb/pe8.glb",
            new float[][]{{0.45f, 3.75f, 4.80f}, {-0.45f, 3.75f, 4.80f}, {0f, 2.95f, 7.0f}, {0f, 3.45f, -1.9f}, {0f, 3.10f, -12.6f}},
            null, null,
            new Plane(23000, 4500, 1350, 188.7f, 39.13f, 23.59f, 1.42f, 15f, 3.97e6f, 4, 2.05f, 97f, 28f, 10f, 7f, 3.0f,
                    10f, 0, false, 0.031f, new float[][]{
                    {0f, 3.30f, 8.15f, 0f, -55f, 55f, -40f, 45f, 0f, 0.90f},
                    {0f, 4.62f, -1.0f, 180f, 0f, 360f, -5f, 75f, 2f, 1.60f},
                    {0f, 3.60f, -14.45f, 180f, 120f, 240f, -40f, 45f, 2f, 1.40f},
                    {4.75f, 2.66f, -7.0f, 180f, 145f, 215f, -45f, 8f, 1f, 1.20f},
                    {-4.75f, 2.66f, -7.0f, 180f, 145f, 215f, -45f, 8f, 1f, 1.20f}}),
            new Load(CraftWeapon.FAB250W, 16, 0, new float[][]{{0.42f, 2.35f, 1.95f}, {-0.42f, 2.35f, 1.95f}, {0.42f, 2.80f, 1.95f}, {-0.42f, 2.80f, 1.95f}, {0.42f, 2.35f, 0.65f}, {-0.42f, 2.35f, 0.65f}, {0.42f, 2.80f, 0.65f}, {-0.42f, 2.80f, 0.65f}, {0.42f, 2.35f, -0.65f}, {-0.42f, 2.35f, -0.65f}, {0.42f, 2.80f, -0.65f}, {-0.42f, 2.80f, -0.65f}, {0.42f, 2.35f, -1.95f}, {-0.42f, 2.35f, -1.95f}, {0.42f, 2.80f, -1.95f}, {-0.42f, 2.80f, -1.95f}}, "bomb_", 0f, 0f, 0f),
            new Load(CraftWeapon.FAB1000W, 4, 0, new float[][]{{0.45f, 2.5f, 1.20f}, {-0.45f, 2.5f, 1.20f}, {0.45f, 2.5f, -1.50f}, {-0.45f, 2.5f, -1.50f}}, "bombk_", 0f, 0f, 0f),
            new Load(CraftWeapon.FAB5000W, 1, 0, new float[][]{{0f, 2.3f, -0.2f}}, "bombf_", 0f, 0f, 0f),
            new Load(CraftWeapon.GUNNERS, 6000, 9, new float[][]{{0f, 3.5f, 0f}}, null, 0f, 0f, 0f)),

    // ------------------------------------------------------------------ jet (reply 12)
    /**
     * Sukhoi Su-57 (T-50): 2 x AL-41F1 (2 x 88 kN dry / 147 kN reheat), 18.5 t empty + 10.3 t fuel, span 14.1 m, length
     * 20.1 m, thrust vectoring. propR = 0 marks a jet: power = total DRY thrust in N. Top speed limited to 300 m/s
     * (1080 km/h) for the world's chunk loading.
     * Stations: P1 +-3.0 m (S-8 pod / FAB-500 / Kh-38), P2 +-4.3 m (S-8 pod / Kh-38), P3 +-5.5 m (Kh-38), 2 tandem main bays.
     */
    SU57("su57", Cat.PLANE, 5.5f, 2.6f, 420f, "models/glb/su57.glb",
            new float[][]{{0f, 2.18f, 5.30f}},
            null, null,
            new Plane(18600, 10300, 9000, 78.8f, 14.1f, 20.1f, 1.60f, 32f, 1.76e5f, 2, 0f, 300f, 270f, 28f, 18f, 9.0f,
                    0f, 0, false, 0.0f, null),
            new Load(CraftWeapon.GSH301, 150, 0, new float[][]{{-0.80f, 2.22f, 5.50f}}, null, 0f, 0f, 0f),
            new Load(CraftWeapon.S8, 80, 0, new float[][]{{3.0f, 1.35f, 0.30f}, {-3.0f, 1.35f, 0.30f}, {4.3f, 1.30f, -1.0f}, {-4.3f, 1.30f, -1.0f}}, null, 0f, 0f, 0f),
            new Load(CraftWeapon.KH38, 6, 0, new float[][]{{4.3f, 1.42f, -2.4f}, {-4.3f, 1.42f, -2.4f}, {5.5f, 1.42f, -3.5f}, {-5.5f, 1.42f, -3.5f}, {3.0f, 1.47f, -1.3f}, {-3.0f, 1.47f, -1.3f}}, "kh38_", 0f, 0f, 0f),
            new Load(CraftWeapon.FAB500S, 6, 0, new float[][]{{0.33f, 2.05f, -0.4f}, {-0.33f, 2.05f, -0.4f}, {0.33f, 2.05f, -4.2f}, {-0.33f, 2.05f, -4.2f}, {3.0f, 1.83f, -1.5f}, {-3.0f, 1.83f, -1.5f}}, "fab_", 0f, 0f, 0f),
            new Load(CraftWeapon.FLARES, 96, 0, new float[][]{{0.55f, 1.55f, -7.6f}, {-0.55f, 1.55f, -7.6f}}, null, 0f, 0f, 0f));

    public enum Cat { HELI, BOAT, SUB, PLANE }

    /**
     * Fixed-wing aircraft (reply 11). Real units; the world runs at the Tu bombers' SPEED_SCALE.
     * mass = empty + crew + oil (fuel and bombs are added at run time), fuel kg, burn kg/h at full throttle,
     * area m^2, span / length m, clMax (clean), stallDeg (AoA of clMax), power W (all engines), propR m,
     * vmax = sea-level max speed m/s (sets the zero-lift drag), roll/pitch/yaw = max rates deg/s,
     * maxG = structural load limit, tailDeg = ground attitude on the tail wheel, gearFold +1 outward / -1 inward /
     * 0 aft into the nacelles, tailRetract, cd0Flaps = drag added by landing flaps,
     * turrets: {x, y, z, restAz, azLo, azHi, elLo, elHi, calibre 0 rifle / 1 heavy / 2 cannon, barrelLength}.
     */
    public record Plane(float mass, float fuel, float burn, float area, float span, float length, float clMax,
                        float stallDeg, float power, int engines, float propR, float vmax, float rollRate,
                        float pitchRate, float yawRate, float maxG, float tailDeg, int gearFold, boolean tailRetract,
                        float cd0Flaps, float[][] turrets) {
        public int turretCount() {
            return turrets == null ? 0 : turrets.length;
        }

        /** Jet: propR 0, power = total dry thrust (N), afterburner x1.67 above 86 % throttle. */
        public boolean jet() {
            return propR <= 0f;
        }
    }

    /**
     * Helicopter parameters.
     * thrustRatio = max rotor thrust / weight at full collective & nominal RPM (sea level);
     * cda* = drag area (Cd * A, m^2) along body forward / side / vertical;
     * rates in deg/s; spool = engine spool-up seconds; fuel kg; burn kg/h; tailR/tailZ = tail rotor radius / station.
     */
    public record Heli(float mass, float thrustRatio, float vne, float rotorR, float hubY, float hub2Y,
                       float cdaF, float cdaS, float cdaT, float rollRate, float pitchRate, float yawRate,
                       boolean coaxial, float spool, float fuel, float burn, float tailR, float tailZ) {}

    /**
     * Displacement hull parameters.
     * thrust = static bollard thrust N, power = shaft W (thrust = min(static, eta*P/v)),
     * kPlane/kHump/vHump = axial drag coefficient (N per (m/s)^2) planing / hump bump / hump speed,
     * kLat = lateral drag coefficient, turn = max yaw rate deg/s, jets = waterjets (turn at rest, sharp turns),
     * ballast = ballast-tank water mass kg (submarine), vSub = submerged design speed,
     * comY = centre of mass height above keel, axisY = hull axis height (sub) or 0.
     */
    public record Water(float mass, float length, float beam, float draft, float hullH, float bowRise,
                        float thrust, float power, float kPlane, float kHump, float vHump, float kLat, float turn,
                        boolean jets, float ballast, float vSub, float comY, float axisY) {}

    /**
     * A weapon station: weapon, full load, seat allowed to fire (-1 = any crew, 1 = gunner; the pilot may fire it
     * when flying alone), muzzle points (cycled), node prefix hidden as rounds are expended,
     * turret limits: azimuth +-az, elevation min/max (0,0,0 = fixed boresight along the nose).
     */
    public record Load(CraftWeapon weapon, int full, int seat, float[][] muzzles, String nodePrefix,
                       float az, float elMin, float elMax) {
        public boolean turret() {
            return az > 0f || elMin != 0f || elMax != 0f;
        }
    }

    public final String id;
    public final Cat cat;
    public final float width, height, hp;
    public final String model;
    public final float[][] seats;
    public final Heli heli;
    public final Water water;
    public final Plane plane;
    public final Load[] loads;

    CraftKind(String id, Cat cat, float width, float height, float hp, String model, float[][] seats, Heli heli,
              Water water, Load... loads) {
        this(id, cat, width, height, hp, model, seats, heli, water, null, loads);
    }

    CraftKind(String id, Cat cat, float width, float height, float hp, String model, float[][] seats, Heli heli,
              Water water, Plane plane, Load... loads) {
        this.plane = plane;
        this.id = id;
        this.cat = cat;
        this.width = width;
        this.height = height;
        this.hp = hp;
        this.model = model;
        this.seats = seats;
        this.heli = heli;
        this.water = water;
        this.loads = loads;
    }

    public boolean isHeli() {
        return cat == Cat.HELI;
    }

    public boolean isPlane() {
        return cat == Cat.PLANE;
    }

    /** Helicopters and aeroplanes: land-based, no buoyancy, never collidable as a solid. */
    public boolean isAir() {
        return cat == Cat.HELI || cat == Cat.PLANE;
    }

    /** Longest horizontal extent (m): culling / tracking. */
    public float extent() {
        return heli != null ? heli.rotorR() * 2 : plane != null ? Math.max(plane.span(), plane.length()) : water.length();
    }

    public boolean isSub() {
        return cat == Cat.SUB;
    }

    public float massKg() {
        return heli != null ? heli.mass() : plane != null ? plane.mass() : water.mass();
    }

    public int loadIndex(CraftWeapon w) {
        for (int i = 0; i < loads.length; i++) if (loads[i].weapon() == w) return i;
        return -1;
    }

    public String langKey() {
        return "entity.aiarsenal." + id;
    }

    public static CraftKind byId(String id) {
        for (CraftKind k : values()) if (k.id.equals(id)) return k;
        return null;
    }
}
