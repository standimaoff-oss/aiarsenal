package ru.aiforge.arsenal.nuke;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.arsenal.core.Kind;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * World-wide list of armed nuclear charges (placed charges, falling bombs, ICBM warheads incl. virtual mid-course
 * flights) for the remote detonator, plus active fallout zones. Stored in the overworld data storage.
 */
public class ChargeRegistry extends SavedData {
    public static final String NAME = "aiarsenal_charges";
    public static final int FUZE_REMOTE = 0, FUZE_CONTACT = 1, FUZE_AIR = 2;

    public static final class Charge {
        public int id;
        public UUID entity;
        public ResourceKey<Level> dim;
        public Vec3 pos;
        public Kind kind;
        public int fuze;
        @Nullable
        public UUID owner;
        /** 0 placed / on ground, 1 falling, 2 in flight (ICBM), 3 virtual mid-course flight. */
        public int status;
        /** Virtual flight parameters (ICBM above the atmosphere, no entity / no chunk loading). */
        public Vec3 launch = Vec3.ZERO, aim = Vec3.ZERO;
        public double apex, total, time;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("Id", id);
            if (entity != null) t.putUUID("E", entity);
            t.putString("Dim", dim.location().toString());
            t.putDouble("X", pos.x);
            t.putDouble("Y", pos.y);
            t.putDouble("Z", pos.z);
            t.putString("Kind", kind.id);
            t.putInt("Fuze", fuze);
            if (owner != null) t.putUUID("O", owner);
            t.putInt("St", status);
            t.putDouble("LX", launch.x);
            t.putDouble("LY", launch.y);
            t.putDouble("LZ", launch.z);
            t.putDouble("AX", aim.x);
            t.putDouble("AY", aim.y);
            t.putDouble("AZ", aim.z);
            t.putDouble("Ap", apex);
            t.putDouble("Tt", total);
            t.putDouble("T", time);
            return t;
        }

        static Charge load(CompoundTag t) {
            Charge c = new Charge();
            c.id = t.getInt("Id");
            if (t.hasUUID("E")) c.entity = t.getUUID("E");
            c.dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(t.getString("Dim")));
            c.pos = new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"));
            c.kind = Kind.byId(t.getString("Kind"));
            if (c.kind == null) c.kind = Kind.RDS1;
            c.fuze = t.getInt("Fuze");
            if (t.hasUUID("O")) c.owner = t.getUUID("O");
            c.status = t.getInt("St");
            c.launch = new Vec3(t.getDouble("LX"), t.getDouble("LY"), t.getDouble("LZ"));
            c.aim = new Vec3(t.getDouble("AX"), t.getDouble("AY"), t.getDouble("AZ"));
            c.apex = t.getDouble("Ap");
            c.total = t.getDouble("Tt");
            c.time = t.getDouble("T");
            return c;
        }
    }

    public static final class Fallout {
        public ResourceKey<Level> dim;
        public Vec3 pos;
        public float radius;
        public long until;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Dim", dim.location().toString());
            t.putDouble("X", pos.x);
            t.putDouble("Y", pos.y);
            t.putDouble("Z", pos.z);
            t.putFloat("R", radius);
            t.putLong("U", until);
            return t;
        }

        static Fallout load(CompoundTag t) {
            Fallout f = new Fallout();
            f.dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(t.getString("Dim")));
            f.pos = new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"));
            f.radius = t.getFloat("R");
            f.until = t.getLong("U");
            return f;
        }
    }

    public final List<Charge> charges = new ArrayList<>();
    public final List<Fallout> fallout = new ArrayList<>();
    private int nextId = 1;

    public static ChargeRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ChargeRegistry::load, ChargeRegistry::new, NAME);
    }

    public Charge add(UUID entity, ResourceKey<Level> dim, Vec3 pos, Kind kind, int fuze, @Nullable UUID owner, int status) {
        Charge c = new Charge();
        c.id = nextId++;
        c.entity = entity;
        c.dim = dim;
        c.pos = pos;
        c.kind = kind;
        c.fuze = fuze;
        c.owner = owner;
        c.status = status;
        charges.add(c);
        setDirty();
        return c;
    }

    @Nullable
    public Charge byEntity(UUID e) {
        for (Charge c : charges) if (e.equals(c.entity)) return c;
        return null;
    }

    @Nullable
    public Charge byId(int id) {
        for (Charge c : charges) if (c.id == id) return c;
        return null;
    }

    public void remove(Charge c) {
        charges.remove(c);
        setDirty();
    }

    public void removeEntity(UUID e) {
        for (Iterator<Charge> it = charges.iterator(); it.hasNext(); ) if (e.equals(it.next().entity)) it.remove();
        setDirty();
    }

    public void update(UUID e, Vec3 pos, int status) {
        Charge c = byEntity(e);
        if (c != null) {
            c.pos = pos;
            c.status = status;
            setDirty();
        }
    }

    public void addFallout(ResourceKey<Level> dim, Vec3 pos, float radius, long until) {
        Fallout f = new Fallout();
        f.dim = dim;
        f.pos = pos;
        f.radius = radius;
        f.until = until;
        fallout.add(f);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag l = new ListTag();
        for (Charge c : charges) l.add(c.save());
        t.put("Charges", l);
        ListTag f = new ListTag();
        for (Fallout x : fallout) f.add(x.save());
        t.put("Fallout", f);
        t.putInt("Next", nextId);
        return t;
    }

    public static ChargeRegistry load(CompoundTag t) {
        ChargeRegistry r = new ChargeRegistry();
        for (Tag x : t.getList("Charges", Tag.TAG_COMPOUND)) r.charges.add(Charge.load((CompoundTag) x));
        for (Tag x : t.getList("Fallout", Tag.TAG_COMPOUND)) r.fallout.add(Fallout.load((CompoundTag) x));
        r.nextId = Math.max(1, t.getInt("Next"));
        return r;
    }

    /** For the gametests / detonator: the registered position block. */
    public static BlockPos block(Charge c) {
        return BlockPos.containing(c.pos);
    }
}
