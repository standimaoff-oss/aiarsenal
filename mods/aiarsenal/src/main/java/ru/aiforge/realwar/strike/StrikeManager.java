package ru.aiforge.realwar.strike;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.entity.ArsEntity;
import ru.aiforge.arsenal.entity.NukeEntity;
import ru.aiforge.arsenal.nuke.ChargeRegistry;
import ru.aiforge.arsenal.nuke.NuclearBlast;
import ru.aiforge.realwar.RealWar;
import ru.aiforge.realwar.aircraft.AirBombEntity;
import ru.aiforge.realwar.aircraft.AirBombType;
import ru.aiforge.realwar.blast.ConventionalBlast;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.ordnance.ShellEntity;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.registry.ModSounds;

/**
 * Server side of the radio (reply 19): plans and flies artillery / air strikes.
 * <p>
 * Aircraft and bombs are simulated "virtually" along analytic / integrated paths so a strike works even where no chunk is loaded;
 * a {@link StrikePlaneEntity} is shown only while its position is entity-ticking, and a virtual bomb becomes a real
 * {@link AirBombEntity} / {@link NukeEntity} as soon as it enters an entity-ticking chunk. The target area is held loaded by
 * a refreshed, non-persistent chunk ticket. Release points come from integrating exactly the physics of the real bomb entities.
 */
@Mod.EventBusSubscriber(modid = "aiarsenal")
public final class StrikeManager {
    public static final double MAX_RANGE = 3000.0;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("aiarsenal_strike", Comparator.comparingLong(ChunkPos::toLong), 600);
    private static final Map<ResourceKey<Level>, List<Strike>> ACTIVE = new HashMap<>();
    /** nuclear drop marker for {@link Drop#type} */
    static final int NUKE = -1;

    private StrikeManager() {
    }

    // ------------------------------------------------------------------------------------------------------------- request

    /**
     * Validates and schedules a strike. Returns null on success, otherwise a translation key of the refusal.
     * @param yawDeg approach heading (vanilla yaw: direction the aircraft fly / the shells travel)
     */
    public static String request(ServerPlayer p, StrikeType t, double x, double z, float yawDeg, int fuze) {
        ServerLevel sl = p.serverLevel();
        if (Math.abs(x) > 29_999_000 || Math.abs(z) > 29_999_000) return "msg.aiarsenal.radio.bad_target";
        double dx = x - p.getX(), dz = z - p.getZ();
        if (dx * dx + dz * dz > MAX_RANGE * MAX_RANGE) return "msg.aiarsenal.radio.too_far";
        ItemStack radio = radioIn(p);
        if (radio.isEmpty()) return "msg.aiarsenal.radio.no_radio";
        long now = sl.getGameTime();
        boolean creative = p.getAbilities().instabuild;
        CompoundTag cd = radio.getOrCreateTagElement("Cooldown");
        long ready = cd.getLong(t.key());
        if (!creative && ready > now) {
            p.displayClientMessage(Component.translatable("msg.aiarsenal.radio.cooldown", (ready - now + 19) / 20), true);
            return "";
        }
        for (Strike s : ACTIVE.getOrDefault(sl.dimension(), List.of()))
            if (s.owner.equals(p.getUUID()) && s.type == t) return "msg.aiarsenal.radio.busy";
        if (t == StrikeType.NUCLEAR && !creative) {
            Item rds = ForgeRegistries.ITEMS.getValue(new ResourceLocation("aiarsenal", "rds1"));
            int slot = -1;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++)
                if (rds != null && p.getInventory().getItem(i).is(rds)) {
                    slot = i;
                    break;
                }
            if (slot < 0) return "msg.aiarsenal.radio.need_rds1";
            p.getInventory().getItem(slot).shrink(1);
        }
        Strike s = plan(sl, p.getUUID(), t, x, z, yawDeg, fuze);
        cd.putLong(t.key(), now + t.cooldownS * 20L);
        int eta = (s.firstImpact - (int) (now - s.t0)) / 20;
        p.displayClientMessage(Component.translatable("msg.aiarsenal.radio.ack." + t.key(), (int) x, (int) z, Math.max(1, eta)), false);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.LOCK_DONE.get(), SoundSource.PLAYERS, 0.7f, 0.8f);
        RealWar.LOGGER.info("radio strike {} by {} at {} {} eta {} s", t, p.getGameProfile().getName(), (int) x, (int) z, eta);
        return null;
    }

    public static ItemStack radioIn(Player p) {
        if (p.getMainHandItem().getItem() instanceof ru.aiforge.realwar.item.RadioItem) return p.getMainHandItem();
        if (p.getOffhandItem().getItem() instanceof ru.aiforge.realwar.item.RadioItem) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /** Builds the timeline. Public for game tests (no player, no cooldown). */
    public static Strike plan(ServerLevel sl, UUID owner, StrikeType t, double x, double z, float yawDeg, int fuze) {
        Strike s = new Strike(owner, t, sl.getGameTime());
        s.fuze = fuze;
        s.center = new ChunkPos(BlockPos.containing(x, 0, z));
        s.ticket(sl);
        sl.getChunk(s.center.x, s.center.z);                // force-generate so the ground height is real
        double ty = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        s.target = new Vec3(x, ty, z);
        double yr = Math.toRadians(yawDeg);
        Vec3 h = new Vec3(-Math.sin(yr), 0, Math.cos(yr));     // flight direction
        Vec3 lat = new Vec3(-h.z, 0, h.x);
        s.heading = h;
        RandomSource rnd = sl.random;
        if (t == StrikeType.ARTILLERY) {
            // battery ~15 km behind the caller: 3 salvos x 6 rounds, descent 55 deg, shots 4 s apart, first impact 12 s after the call
            double el = Math.toRadians(55);
            Vec3 d = new Vec3(h.x * Math.cos(el), -Math.sin(el), h.z * Math.cos(el));
            for (int salvo = 0; salvo < 3; salvo++) {
                double sig = salvo == 0 ? 18 : 11;          // first salvo is the adjustment round, then fire for effect
                for (int g = 0; g < 6; g++) {
                    Vec3 hit = new Vec3(x + rnd.nextGaussian() * sig, ty, z + rnd.nextGaussian() * sig);
                    int tick = 240 + salvo * 80 + g * 2 + rnd.nextInt(4);
                    s.shells.add(new Shell(tick, hit.subtract(d.scale(90)), d));
                }
            }
            s.firstImpact = 242;
            s.end = 240 + 3 * 80 + 60;
            ACTIVE.computeIfAbsent(sl.dimension(), k -> new ArrayList<>()).add(s);
            return s;
        }
        double vB = t.speedMs / 20.0;
        double alt = t.altM;
        boolean nuke = t == StrikeType.NUCLEAR;
        double stop = nuke && fuze == ChargeRegistry.FUZE_AIR ? NuclearBlast.radius(Kind.RDS1.yieldKt()) * 0.45 : 0.0;
        stop = Math.min(90.0, stop);
        double[] th = throwOf(nuke ? NUKE : t.bomb().ordinal(), alt, vB, stop);
        double D = th[0];
        int n = t.planes, m = t.bombsPerPlane;
        double preRun = 2400.0;
        int lastRelease = 0, firstRelease = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double li = (i - (n - 1) / 2.0) * t.lateralM;
            double stagger = Math.abs(i - (n - 1) / 2.0) * 45.0;  // shallow V: wingmen 45 m behind per position
            double altI = alt + (i % 2 == 0 ? 0 : 8);
            double s0 = -(D + preRun + stagger);
            Flight f = new Flight(t.plane, sl.getGameTime());
            f.origin = new Vec3(x, ty + altI, z).add(lat.scale(li)).add(h.scale(s0));
            f.vel = h.scale(vB);
            f.load = nuke ? -1 : t.bomb().ordinal();
            f.stores = m;
            double[] dthI = altI == alt ? th : throwOf(nuke ? NUKE : t.bomb().ordinal(), altI, vB, stop);
            for (int k = 0; k < m; k++) {
                double a = (k - (m - 1) / 2.0) * t.spacingM;
                int tick = (int) Math.round((a - dthI[0] - s0) / vB);
                f.releases.add(tick);
                lastRelease = Math.max(lastRelease, tick);
                firstRelease = Math.min(firstRelease, tick);
            }
            f.climbAt = f.releases.get(f.releases.size() - 1) + 30;
            f.endTick = (int) ((-s0 + 2600.0) / vB);
            s.flights.add(f);
        }
        s.firstImpact = firstRelease + (int) th[1];
        s.end = 0;
        for (Flight f : s.flights) s.end = Math.max(s.end, f.endTick);
        s.end = Math.max(s.end, lastRelease + (int) th[1] + 100);
        ACTIVE.computeIfAbsent(sl.dimension(), k -> new ArrayList<>()).add(s);
        return s;
    }

    /**
     * Forward throw (m) and fall time (ticks) of a store released level at {@code alt} m above the target at {@code vB} blocks/tick,
     * integrated with the very same step as the real entity. {@code stop}: height above ground at which the fall ends (air burst).
     */
    public static double[] throwOf(int type, double alt, double vB, double stop) {
        Vec3 p = new Vec3(0, alt, 0), v = new Vec3(vB, 0, 0);
        int n = 0;
        while (p.y > stop && n < 4000) {
            v = step(type, v);
            p = p.add(v);
            n++;
        }
        return new double[]{p.x, n};
    }

    static Vec3 step(int type, Vec3 v) {
        if (type == NUKE) {
            double g = 9.81 * ArsEntity.scale() / 400.0;
            double drag = g / Math.pow(ArsEntity.bpt(260), 2);
            double sp = v.length();
            return v.add(0, -g, 0).subtract(v.scale(drag * sp));
        }
        Vec3 w = v.add(0.0, -AirBombEntity.G, 0.0);
        return w.scale(1.0 - 4.0E-4 * w.length());
    }

    // ------------------------------------------------------------------------------------------------------------- tick

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.level instanceof ServerLevel sl)) return;
        List<Strike> list = ACTIVE.get(sl.dimension());
        if (list == null || list.isEmpty()) return;
        for (Iterator<Strike> it = list.iterator(); it.hasNext(); ) {
            Strike s = it.next();
            try {
                if (s.tick(sl)) {
                    s.close(sl);
                    it.remove();
                }
            } catch (RuntimeException e) {
                RealWar.LOGGER.error("strike tick failed", e);
                s.close(sl);
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        ACTIVE.clear();
    }

    public static int activeCount(ServerLevel sl) {
        return ACTIVE.getOrDefault(sl.dimension(), List.of()).size();
    }

    public static List<Strike> active(ServerLevel sl) {
        return ACTIVE.getOrDefault(sl.dimension(), List.of());
    }

    // ------------------------------------------------------------------------------------------------------------- model

    record Shell(int tick, Vec3 from, Vec3 dir) {
    }

    static final class Drop {
        Vec3 pos, vel;
        final int type, fuze;
        int age;

        Drop(Vec3 pos, Vec3 vel, int type, int fuze) {
            this.pos = pos;
            this.vel = vel;
            this.type = type;
            this.fuze = fuze;
        }
    }

    public static final class Flight {
        final StrikePlaneEntity.Kind kind;
        final long t0;
        Vec3 origin, vel;
        int load, stores, climbAt, endTick, released;
        final List<Integer> releases = new ArrayList<>();
        StrikePlaneEntity ent;

        Flight(StrikePlaneEntity.Kind kind, long t0) {
            this.kind = kind;
            this.t0 = t0;
        }

        /** position at tick {@code k} after the call: straight and level, then a gentle climb-out (vy ramps to 0.2 vB). */
        Vec3 pos(int k) {
            Vec3 p = origin.add(vel.scale(k));
            if (k > climbAt) {
                double u = k - climbAt, ramp = 80.0, vy = 0.2 * vel.length();
                double dy = u < ramp ? 0.5 * vy * u * u / ramp : 0.5 * vy * ramp + vy * (u - ramp);
                p = p.add(0, dy, 0);
            }
            return p;
        }

        Vec3 velAt(int k) {
            return pos(k + 1).subtract(pos(k));
        }

        public StrikePlaneEntity entity() {
            return ent;
        }
    }

    public static final class Strike {
        public final UUID owner;
        public final StrikeType type;
        public final long t0;
        public Vec3 target, heading;
        public int fuze, firstImpact, end;
        ChunkPos center;
        public final List<Flight> flights = new ArrayList<>();
        final List<Shell> shells = new ArrayList<>();
        final List<Drop> drops = new ArrayList<>();
        /** real bombs spawned by this strike; parked back into {@link #drops} if their chunk stops ticking */
        final List<AirBombEntity> live = new ArrayList<>();
        boolean announcedRelease;
        public int realBombs, realNukes, shellsFired;

        Strike(UUID owner, StrikeType type, long t0) {
            this.owner = owner;
            this.type = type;
            this.t0 = t0;
        }

        void ticket(ServerLevel sl) {
            // level 33-5 = 28 at the centre -> entity ticking within 3 chunks (7 x 7 chunks, 112 m)
            sl.getChunkSource().addRegionTicket(TICKET, center, 5, center);
        }

        void close(ServerLevel sl) {
            sl.getChunkSource().removeRegionTicket(TICKET, center, 5, center);
            for (Flight f : flights)
                if (f.ent != null && !f.ent.isRemoved()) f.ent.discard();
        }

        /** @return true when finished */
        boolean tick(ServerLevel sl) {
            int k = (int) (sl.getGameTime() - t0);
            if (k % 200 == 0) ticket(sl);
            // ---- artillery
            for (Shell sh : shells) {
                if (sh.tick - 34 == k) {
                    Vec3 hit = sh.from.add(sh.dir.scale(90));
                    sl.playSound(null, hit.x, hit.y + 6, hit.z, ModSounds.SHELL_WHISTLE.get(), SoundSource.HOSTILE, 5.0f, 0.9f + sl.random.nextFloat() * 0.2f);
                }
                if (sh.tick == k) {
                    ShellEntity.fire(sl, Ballistics.Profile.MSTA_HE, sh.from, sh.dir, -1);
                    shellsFired++;
                }
            }
            if (type == StrikeType.ARTILLERY && k == 180) say(sl, "msg.aiarsenal.radio.shot");
            // ---- aircraft
            for (Flight f : flights) {
                if (k > f.endTick) {
                    if (f.ent != null && !f.ent.isRemoved()) f.ent.discard();
                    f.ent = null;
                    continue;
                }
                Vec3 p = f.pos(k), v = f.velAt(k);
                while (f.released < f.releases.size() && f.releases.get(f.released) <= k) {
                    Vec3 rel = p.add(0, -2.0, 0);
                    drops.add(new Drop(rel, f.vel, f.load >= 0 ? f.load : NUKE, fuze));
                    f.released++;
                    if (!announcedRelease) {
                        announcedRelease = true;
                        say(sl, type == StrikeType.NUCLEAR ? "msg.aiarsenal.radio.release_nuke" : "msg.aiarsenal.radio.release");
                    }
                }
                // aircraft are pure visuals driven from here, so they are shown wherever entities are loaded (whole view
                // distance, also border chunks that do not tick) - not only inside the simulation distance
                boolean visible = p.y > sl.getMinBuildHeight() && p.y < sl.getMaxBuildHeight() + 512
                        && sl.areEntitiesLoaded(ChunkPos.asLong(Mth.floor(p.x) >> 4, Mth.floor(p.z) >> 4));
                if (visible) {
                    if (f.ent == null || f.ent.isRemoved()) {
                        f.ent = new StrikePlaneEntity(ModEntities.STRIKE_PLANE.get(), sl);
                        f.ent.setKind(f.kind);
                        f.ent.setPos(p.x, p.y, p.z);
                        f.ent.setVel(v);
                        f.ent.setLoad(f.load, f.stores - f.released);
                        f.ent.touched = sl.getGameTime();
                        sl.addFreshEntity(f.ent);
                    }
                    StrikePlaneEntity e = f.ent;
                    e.setPos(p.x, p.y, p.z);
                    e.setVel(v);
                    e.setLoad(f.load, f.stores - f.released);
                    boolean doors = !f.releases.isEmpty() && k > f.releases.get(0) - 70 && k < f.releases.get(f.releases.size() - 1) + 40;
                    e.setFlag(StrikePlaneEntity.F_DOORS, doors);
                    e.setFlag(StrikePlaneEntity.F_AB, k > f.climbAt);
                    e.touched = sl.getGameTime();
                } else if (f.ent != null) {
                    if (!f.ent.isRemoved()) f.ent.discard();
                    f.ent = null;
                }
            }
            // ---- real bombs whose chunk stopped entity-ticking (caller moved away): they would hang frozen in the air,
            // so they go back to the virtual integrator and are resolved like any other virtual store
            for (Iterator<AirBombEntity> it = live.iterator(); it.hasNext(); ) {
                AirBombEntity b = it.next();
                if (b.isRemoved()) {
                    it.remove();
                } else if (!sl.isPositionEntityTicking(b.blockPosition())) {
                    Drop d = new Drop(b.position(), b.getDeltaMovement(), b.bombType().ordinal(), fuze);
                    d.age = AirBombEntity.ARM_TICKS;
                    drops.add(d);
                    b.discard();
                    it.remove();
                }
            }
            // ---- stores in flight
            for (Iterator<Drop> it = drops.iterator(); it.hasNext(); ) {
                Drop d = it.next();
                BlockPos bp = BlockPos.containing(d.pos);
                boolean ticking = sl.isPositionEntityTicking(bp);
                if (ticking) {
                    if (d.type == NUKE) {
                        NukeEntity.create(sl, Kind.RDS1, d.pos, d.vel, ArsEntity.yawOf(d.vel), d.fuze, owner, true);
                        realNukes++;
                    } else {
                        AirBombEntity b = AirBombEntity.drop(sl, d.pos, d.vel, AirBombType.byOrdinal(d.type));
                        b.armAt = Math.max(2, AirBombEntity.ARM_TICKS - d.age);
                        sl.addFreshEntity(b);
                        live.add(b);
                        realBombs++;
                    }
                    it.remove();
                    continue;
                }
                d.vel = step(d.type, d.vel);
                d.pos = d.pos.add(d.vel);
                d.age++;
                if (d.pos.y < target.y - 2 || d.age > 1200) {
                    // never reached a loaded chunk (ticket refused): resolve in place
                    if (d.type == NUKE) NukeEntity.create(sl, Kind.RDS1, new Vec3(d.pos.x, target.y + 1, d.pos.z), Vec3.ZERO, 0f, d.fuze, owner, true);
                    else {
                        AirBombType bt = AirBombType.byOrdinal(d.type);
                        ConventionalBlast.explode(sl, new Vec3(d.pos.x, target.y + 0.3, d.pos.z), bt.tntKg, bt.frag);
                    }
                    it.remove();
                }
            }
            if (k == end - 40 && !flights.isEmpty()) say(sl, "msg.aiarsenal.radio.rtb");
            return k >= end && drops.isEmpty() && live.isEmpty();
        }

        private void say(ServerLevel sl, String key) {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(owner);
            if (p != null) p.displayClientMessage(Component.translatable(key), false);
        }
    }
}
