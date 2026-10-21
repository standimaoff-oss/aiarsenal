package ru.aiforge.realwar.client;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import ru.aiforge.realwar.network.PlayerGunStatePacket;

/**
 * Client table of the gun state of <em>other</em> players and AI soldiers, fed by {@link PlayerGunStatePacket}.
 *
 * <p>Everything is keyed by entity id and carries the millisecond stamp of the packet, so the render thread never
 * waits for a tick: a shot that arrives mid-frame already flashes in that frame. Entries are dropped when the entity
 * leaves the level or goes quiet. Deliberately free of gameplay state — this is presentation only.</p>
 */
public final class GunAnimClient {
    /** a shot is drawn this long after the packet (seconds) */
    private static final float SHOT_FLASH = 0.09f;
    /** a pose entry without updates is forgotten after this long (ms) */
    private static final long STALE_MS = 20_000L;

    private static final Map<Integer, Entry> ENTRIES = new ConcurrentHashMap<>();

    private static final class Entry {
        int flags;
        int reloadTicks;
        long stamp = System.currentTimeMillis();
        long reloadStamp;
        long shotStamp;
        int shotSeq;
    }

    private GunAnimClient() {
    }

    // ------------------------------------------------------------------ network

    public static void accept(int entity, int flags, int ticks) {
        Entry e = ENTRIES.computeIfAbsent(entity, k -> new Entry());
        long now = System.currentTimeMillis();
        int before = e.flags;
        if ((flags & PlayerGunStatePacket.SHOT) != 0) {
            e.shotStamp = now;
            e.shotSeq++;
        }
        if ((flags & PlayerGunStatePacket.RELOAD) != 0 && (before & PlayerGunStatePacket.RELOAD) == 0) {
            e.reloadStamp = now;
            e.reloadTicks = Math.max(1, ticks);
        }
        if ((flags & PlayerGunStatePacket.RELOAD) == 0) e.reloadTicks = 0;
        e.flags = flags & ~(PlayerGunStatePacket.SHOT | PlayerGunStatePacket.DRY);
        e.stamp = now;
        e.flags |= (flags & PlayerGunStatePacket.DRY);
    }

    public static void clear() {
        ENTRIES.clear();
    }

    /** Called once per client tick: forget entities that are gone or silent. */
    public static void tick() {
        if (ENTRIES.isEmpty()) return;
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        Iterator<Map.Entry<Integer, Entry>> it = ENTRIES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Entry> en = it.next();
            Entry v = en.getValue();
            Entity e = mc.level == null ? null : mc.level.getEntity(en.getKey());
            if (e == null || e.isRemoved() || now - v.stamp > STALE_MS) it.remove();
        }
    }

    // ------------------------------------------------------------------ queries (render thread)

    public static boolean known(Entity e) {
        return e != null && ENTRIES.containsKey(e.getId());
    }

    /** 1 while aiming, easing to 0 when the packet said otherwise. */
    public static float aim(Entity e, float partial) {
        Entry s = e == null ? null : ENTRIES.get(e.getId());
        return s != null && (s.flags & PlayerGunStatePacket.AIM) != 0 ? 1f : 0f;
    }

    public static boolean reloading(Entity e) {
        Entry s = e == null ? null : ENTRIES.get(e.getId());
        return s != null && (s.flags & PlayerGunStatePacket.RELOAD) != 0 && secondsSince(s.reloadStamp) * 20f < s.reloadTicks;
    }

    public static boolean reloadEmpty(Entity e) {
        Entry s = e == null ? null : ENTRIES.get(e.getId());
        return s != null && (s.flags & PlayerGunStatePacket.RELOAD_EMPTY) != 0;
    }

    /** seconds since the reload started, -1 when he is not reloading */
    public static float reloadTime(Entity e) {
        Entry s = e == null ? null : ENTRIES.get(e.getId());
        return s != null && reloading(e) ? secondsSince(s.reloadStamp) : -1f;
    }

    /** seconds since the last shot, -1 when there was none recently */
    public static float shotAge(Entity e) {
        Entry s = e == null ? null : ENTRIES.get(e.getId());
        if (s == null || s.shotStamp == 0) return -1f;
        float age = secondsSince(s.shotStamp);
        return age > SHOT_FLASH ? -1f : age;
    }

    public static int shotSeq(Entity e) {
        Entry s = e == null ? null : ENTRIES.get(e.getId());
        return s == null ? 0 : s.shotSeq;
    }

    private static float secondsSince(long stamp) {
        return (System.currentTimeMillis() - stamp) / 1000f;
    }
}
