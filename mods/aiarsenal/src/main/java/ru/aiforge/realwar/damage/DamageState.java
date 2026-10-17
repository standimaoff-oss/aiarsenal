package ru.aiforge.realwar.damage;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

/**
 * r18 War Thunder style module damage of one vehicle / craft: broken and damaged ("hurt") module bits, accumulated
 * module damage, crew repair, fire, hull holes (+ flooding for ships). Server authoritative, synced to the clients as
 * one CompoundTag (see {@link #sync()} / {@link #readSync(CompoundTag)}).
 */
public final class DamageState {
    public static final int ENGINE = 0, TRACK_L = 1, TRACK_R = 2, BARREL = 3, TURRET = 4, AMMO = 5;
    public static final int ROTOR = 6, TAIL_ROTOR = 7, WING_L = 8, WING_R = 9, TAIL = 10;
    public static final int FIRE = 11, DEAD = 12, GUN0 = 13, N_GUNS = 10, TURRET_GONE = 23;
    public static final int N = 24;

    public int broken, hurt;
    /** Barrels / mounts shot away (battleship: bit t*2+g main, 8+s secondary). */
    public int lost;
    public final float[] mdmg = new float[N];
    public int repairMod = -1;
    public int repairTicks, repairTotal;
    public int fireTicks, deadTicks, sunkTicks;
    public float flood, floodX, floodZ;
    public final List<Hole> holes = new ArrayList<>();
    /** Server: the state changed and must be re-synced (bits immediately, continuous values throttled). */
    public boolean dirty, dirtyNow;
    /** Server only: track debris entity ids (left / right) removed when the track is repaired. */
    public final int[] trackDebris = {-1, -1};
    /** r21: crew health per seat (Crew.roles order), 0 = dead or seat left empty by a replacement. */
    public final float[] crew = {100f, 100f, 100f, 100f, 100f, 100f, 100f, 100f};
    /** r21: seats emptied because their man moved to a more important one. */
    public int crewMoved;
    /** r21: replacement in progress: seat swapFrom moves into swapTo. */
    public int swapTo = -1, swapFrom = -1, swapTicks, swapTotal;
    /** Server only: tick of the last direct HE hit (the same burst's near-miss pass is then skipped). */
    public int lastHeTick = -100;

    public static final class Hole {
        public float x, y, z, nx, ny, nz, r;
        /** Remaining damage-control ticks; 0 = patched (the scorched plate stays visible). */
        public int open;

        public Hole(float x, float y, float z, float nx, float ny, float nz, float r, int open) {
            this.x = x; this.y = y; this.z = z;
            this.nx = nx; this.ny = ny; this.nz = nz;
            this.r = r; this.open = open;
        }
    }

    public boolean broken(int m) {
        return (this.broken >>> m & 1) != 0;
    }

    public boolean hurtBit(int m) {
        return (this.hurt >>> m & 1) != 0;
    }

    public boolean dead() {
        return this.broken(DEAD);
    }

    public boolean burning() {
        return this.broken(FIRE);
    }

    public boolean any() {
        return this.broken != 0 || this.hurt != 0;
    }

    public void setBroken(int m, boolean v) {
        int nb = v ? this.broken | 1 << m : this.broken & ~(1 << m);
        if (nb != this.broken) {
            this.broken = nb;
            this.dirtyNow = true;
        }
    }

    public void setHurt(int m, boolean v) {
        int nh = v ? this.hurt | 1 << m : this.hurt & ~(1 << m);
        if (nh != this.hurt) {
            this.hurt = nh;
            this.dirtyNow = true;
        }
    }

    /** 0 = ok, 1 = damaged (yellow), 2 = destroyed (red). */
    public int level(int m) {
        return this.broken(m) ? 2 : this.hurtBit(m) ? 1 : 0;
    }

    public float repairFrac() {
        return this.repairMod < 0 || this.repairTotal <= 0 ? 0f : 1f - this.repairTicks / (float) this.repairTotal;
    }

    public int openHoles() {
        int n = 0;
        for (Hole h : this.holes) if (h.open > 0) n++;
        return n;
    }

    public void clearAll() {
        this.broken = this.hurt = this.lost = 0;
        java.util.Arrays.fill(this.mdmg, 0f);
        this.repairMod = -1;
        this.repairTicks = this.repairTotal = 0;
        this.fireTicks = this.deadTicks = this.sunkTicks = 0;
        this.flood = 0;
        for (Hole h : this.holes) h.open = 0;
        Crew.restore(this);
        this.dirtyNow = true;
    }

    public float swapFrac() {
        return this.swapTo < 0 || this.swapTotal <= 0 ? 0f : 1f - this.swapTicks / (float) this.swapTotal;
    }

    // ------------------------------------------------------------------------------------------ sync / save

    public CompoundTag sync() {
        CompoundTag t = new CompoundTag();
        t.putInt("b", this.broken);
        t.putInt("h", this.hurt);
        t.putInt("l", this.lost);
        t.putInt("rm", this.repairMod);
        t.putInt("rt", this.repairTicks);
        t.putInt("rT", this.repairTotal);
        t.putFloat("fl", this.flood);
        t.putFloat("fx", this.floodX);
        t.putFloat("fz", this.floodZ);
        int[] a = new int[this.holes.size() * 8];
        for (int i = 0; i < this.holes.size(); i++) {
            Hole h = this.holes.get(i);
            float[] f = {h.x, h.y, h.z, h.nx, h.ny, h.nz, h.r};
            for (int k = 0; k < 7; k++) a[i * 8 + k] = Float.floatToIntBits(f[k]);
            a[i * 8 + 7] = h.open;
        }
        t.putIntArray("ho", a);
        int[] cr = new int[Crew.MAX];
        for (int i = 0; i < Crew.MAX; i++) cr[i] = this.crew[i] <= 0 ? 0 : Math.max(1, Math.round(this.crew[i]));
        t.putIntArray("cr", cr);
        t.putInt("cm", this.crewMoved);
        t.putInt("st", this.swapTo);
        t.putInt("sf", this.swapFrom);
        t.putInt("sk", this.swapTicks);
        t.putInt("sK", this.swapTotal);
        return t;
    }

    public void readSync(CompoundTag t) {
        this.broken = t.getInt("b");
        this.hurt = t.getInt("h");
        this.lost = t.getInt("l");
        this.repairMod = t.contains("rm") ? t.getInt("rm") : -1;
        this.repairTicks = t.getInt("rt");
        this.repairTotal = t.getInt("rT");
        this.flood = t.getFloat("fl");
        this.floodX = t.getFloat("fx");
        this.floodZ = t.getFloat("fz");
        this.holes.clear();
        int[] a = t.getIntArray("ho");
        for (int i = 0; i + 7 < a.length; i += 8) {
            float[] f = new float[7];
            for (int k = 0; k < 7; k++) f[k] = Float.intBitsToFloat(a[i + k]);
            this.holes.add(new Hole(f[0], f[1], f[2], f[3], f[4], f[5], f[6], a[i + 7]));
        }
        if (t.contains("cr")) {
            int[] cr = t.getIntArray("cr");
            for (int i = 0; i < Math.min(Crew.MAX, cr.length); i++) this.crew[i] = cr[i];
            this.crewMoved = t.getInt("cm");
            this.swapTo = t.getInt("st");
            this.swapFrom = t.getInt("sf");
            this.swapTicks = t.getInt("sk");
            this.swapTotal = t.getInt("sK");
        }
    }

    public void save(CompoundTag tag) {
        CompoundTag t = this.sync();
        int[] md = new int[N];
        for (int i = 0; i < N; i++) md[i] = Float.floatToIntBits(this.mdmg[i]);
        t.putIntArray("md", md);
        t.putInt("ft", this.fireTicks);
        t.putInt("dt", this.deadTicks);
        tag.put("rwDamage", t);
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("rwDamage")) return;
        CompoundTag t = tag.getCompound("rwDamage");
        this.readSync(t);
        int[] md = t.getIntArray("md");
        for (int i = 0; i < Math.min(N, md.length); i++) this.mdmg[i] = Float.intBitsToFloat(md[i]);
        this.fireTicks = t.getInt("ft");
        this.deadTicks = t.getInt("dt");
        this.dirtyNow = true;
    }
}
