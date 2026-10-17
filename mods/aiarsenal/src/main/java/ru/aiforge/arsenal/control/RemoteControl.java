package ru.aiforge.arsenal.control;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import ru.aiforge.arsenal.entity.ArsEntity;
import ru.aiforge.arsenal.network.ArsenalNetwork;
import ru.aiforge.arsenal.network.SessionPacket;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remote operation of drones (tablet / FPV goggles). The operator's view is moved into the drone with the vanilla
 * spectator camera ({@link ServerPlayer#setCamera}): the server then streams chunks around the drone at any range.
 * The player is switched to spectator for the session (invulnerable, no collisions), an operator dummy (armor stand
 * wearing the player's head and armour) stays at the control position. Game mode, position and dimension are saved
 * in the player's persistent data and restored on exit, when the drone is lost, on death or on the next login.
 */
public final class RemoteControl {
    private static final String TAG = "aiarsenal_rc";
    public static final String DUMMY_TAG = "aiarsenal_operator";

    public record Session(UUID player, UUID drone, int droneId, UUID dummy, Vec3 pos, ResourceKey<Level> dim) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private RemoteControl() {
    }

    @Nullable
    public static Session session(UUID player) {
        return SESSIONS.get(player);
    }

    public static boolean inSession(ServerPlayer p) {
        return SESSIONS.containsKey(p.getUUID());
    }

    public static void start(ServerPlayer p, ArsEntity drone) {
        if (inSession(p)) stop(p, null);
        if (drone.operator != null && !drone.operator.equals(p.getUUID())) {
            p.displayClientMessage(Component.translatable("msg.aiarsenal.a_busy"), true);
            return;
        }
        ServerLevel sl = p.serverLevel();
        CompoundTag t = new CompoundTag();
        t.putInt("Mode", p.gameMode.getGameModeForPlayer().getId());
        t.putDouble("X", p.getX());
        t.putDouble("Y", p.getY());
        t.putDouble("Z", p.getZ());
        t.putFloat("Yaw", p.getYRot());
        t.putFloat("Pitch", p.getXRot());
        t.putString("Dim", sl.dimension().location().toString());
        // operator dummy
        ArmorStand st = new ArmorStand(sl, p.getX(), p.getY(), p.getZ());
        st.setYRot(p.getYRot());
        st.setYBodyRot(p.getYRot());
        st.setYHeadRot(p.getYRot());
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.getOrCreateTag().putString("SkullOwner", p.getGameProfile().getName());
        st.setItemSlot(EquipmentSlot.HEAD, head);
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            st.setItemSlot(s, p.getItemBySlot(s).copy());
        st.setItemSlot(EquipmentSlot.MAINHAND, p.getMainHandItem().copy());
        st.setCustomName(p.getDisplayName());
        st.setCustomNameVisible(true);
        st.addTag(DUMMY_TAG);
        CompoundTag stTag = new CompoundTag();
        st.saveWithoutId(stTag);
        stTag.putBoolean("ShowArms", true);
        stTag.putBoolean("NoBasePlate", true);
        st.load(stTag);
        t.putUUID("Dummy", st.getUUID());
        p.getPersistentData().put(TAG, t);
        // Register before the join event so a newly created operator is not
        // mistaken for an orphan loaded from a previous/finished session.
        SESSIONS.put(p.getUUID(), new Session(p.getUUID(), drone.getUUID(), drone.getId(), st.getUUID(), p.position(), sl.dimension()));
        if (!sl.addFreshEntity(st)) {
            SESSIONS.remove(p.getUUID());
            p.getPersistentData().remove(TAG);
            return;
        }
        p.setGameMode(GameType.SPECTATOR);
        drone.operator = p.getUUID();
        p.setCamera(drone);
        ArsenalNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new SessionPacket(drone.getId(), true, ""));
    }

    /** Ends the session and restores the player (reason: translation key or null). */
    public static void stop(ServerPlayer p, @Nullable String reason) {
        if (Boolean.getBoolean("aiarsenal.stressQa"))
            ru.aiforge.arsenal.AiArsenal.LOGGER.info("REMOTE QA stop reason={} camera={} shifting={}",
                    reason, p.getCamera().getId(), p.isShiftKeyDown());
        Session s = SESSIONS.remove(p.getUUID());
        MinecraftServer server = p.server;
        if (s != null) {
            ServerLevel dl = server.getLevel(s.dim());
            Entity d = dl == null ? null : dl.getEntity(s.drone());
            if (d == null) for (ServerLevel l : server.getAllLevels()) if ((d = l.getEntity(s.drone())) != null) break;
            if (d instanceof ArsEntity a) {
                a.operator = null;
                if (a instanceof Controllable c) c.released();
            }
        }
        p.setCamera(p);
        restore(p);
        ArsenalNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new SessionPacket(-1, false, reason == null ? "" : reason));
        if (reason != null) p.displayClientMessage(Component.translatable(reason), true);
    }

    /** Restores game mode / position from the persistent backup (also used on login after a crash). */
    public static void restore(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(TAG)) return;
        CompoundTag t = root.getCompound(TAG);
        root.remove(TAG);
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(t.getString("Dim")));
        ServerLevel sl = p.server.getLevel(dim);
        if (sl == null) sl = p.server.overworld();
        if (t.hasUUID("Dummy")) {
            Entity st = sl.getEntity(t.getUUID("Dummy"));
            if (st != null) st.discard();
        }
        p.teleportTo(sl, t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"), t.getFloat("Yaw"), t.getFloat("Pitch"));
        p.setGameMode(GameType.byId(t.getInt("Mode")));
    }

    /** Drone lost (crashed, destroyed, link lost): ends the operator's session after a short delay for the static. */
    public static void lost(MinecraftServer server, UUID player, String reason) {
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (p != null) stop(p, reason);
    }

    /** Position of the operator (dummy) for link budget calculations. */
    @Nullable
    public static Vec3 operatorPos(MinecraftServer server, UUID player) {
        Session s = SESSIONS.get(player);
        return s == null ? null : s.pos().add(0, 1.6, 0);
    }

    /** Server tick: validates sessions (camera still on the drone, drone alive). */
    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        for (Session s : SESSIONS.values().toArray(new Session[0])) {
            ServerPlayer p = server.getPlayerList().getPlayer(s.player());
            if (p == null) {
                SESSIONS.remove(s.player());
                continue;
            }
            Entity cam = p.getCamera();
            if (cam == p || cam == null || !cam.isAlive() || !cam.getUUID().equals(s.drone())) stop(p, "msg.aiarsenal.session_end");
        }
    }

    /** Input from the client: forwarded to the controlled drone. */
    public static void input(ServerPlayer p, int entityId, int keys, float yaw, float pitch, float zoom) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.droneId() != entityId) return;
        Entity cam = p.getCamera();
        if (cam instanceof Controllable c && cam.getId() == entityId) c.control(p, keys, yaw, pitch, zoom);
    }

    public static void designate(ServerPlayer p, int entityId, Vec3 point, int hitId) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.droneId() != entityId) return;
        Entity cam = p.getCamera();
        if (cam instanceof Controllable c && cam.getId() == entityId) {
            Entity hit = hitId >= 0 ? cam.level().getEntity(hitId) : null;
            c.designate(p, point, hit);
        }
    }

    /** Also checked when an operator's previously unloaded chunk returns. */
    public static boolean activeDummy(UUID id) {
        return SESSIONS.values().stream().anyMatch(s -> s.dummy().equals(id));
    }

    public static boolean isDummy(Entity e) {
        return e instanceof ArmorStand && e.getTags().contains(DUMMY_TAG);
    }

    /** The operator dummy was attacked: the operator is pulled back (and takes the hit). */
    @Nullable
    public static ServerPlayer ownerOfDummy(MinecraftServer server, Entity dummy) {
        for (Session s : SESSIONS.values())
            if (s.dummy().equals(dummy.getUUID())) return server.getPlayerList().getPlayer(s.player());
        return null;
    }
}
