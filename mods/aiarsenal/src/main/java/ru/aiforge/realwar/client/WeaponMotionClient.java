package ru.aiforge.realwar.client;

import java.util.WeakHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import ru.aiforge.realwar.animation.WeaponMotion;
import ru.aiforge.realwar.handheld.FirearmItem;

/** Vanilla movement/stance packets drive remote poses; no local ADS state is borrowed for another player. */
@Mod.EventBusSubscriber(modid="aiarsenal", value=Dist.CLIENT)
public final class WeaponMotionClient {
    public static final KeyMapping SLOW = new KeyMapping("key.aiarsenal.slow_walk", GLFW.GLFW_KEY_LEFT_ALT, "key.categories.movement");
    private static final WeakHashMap<Player, Track> STATES = new WeakHashMap<>();
    private static Object level;
    private static final class Track {
        final WeaponMotion motion = new WeaponMotion();
        final ru.aiforge.realwar.animation.MovementSpeedWindow remoteSpeed = new ru.aiforge.realwar.animation.MovementSpeedWindow();
        double measuredSpeed;
        double x, z;
        Object weapon;
        Track(Player p) { x = p.getX(); z = p.getZ(); weapon = p.getMainHandItem().getItem(); }
    }
    private WeaponMotionClient() {}
    @SubscribeEvent
    public static void input(MovementInputUpdateEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || e.getEntity().isPassenger() || !(e.getEntity().getMainHandItem().getItem() instanceof FirearmItem)) return;
        if (SLOW.isDown()) {
            e.getInput().forwardImpulse *= .4f;
            e.getInput().leftImpulse *= .4f;
            e.getEntity().setSprinting(false);
        }
    }
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (level != mc.level) { STATES.clear(); level = mc.level; }
        if (mc.level == null || mc.isPaused()) return;
        STATES.keySet().removeIf(p -> p.isRemoved() || p.level() != mc.level
                || !(p.getMainHandItem().getItem() instanceof FirearmItem));
        for (Player p : mc.level.players()) {
            if (!(p.getMainHandItem().getItem() instanceof FirearmItem) || p.isPassenger()) { STATES.remove(p); continue; }
            Track t = STATES.get(p);
            if (t == null || t.weapon != p.getMainHandItem().getItem()) { t = new Track(p); STATES.put(p, t); }
            double speed = Math.hypot(p.getX() - t.x, p.getZ() - t.z);
            t.x = p.getX(); t.z = p.getZ();
            t.measuredSpeed = p == mc.player ? (speed > 1 ? 0 : speed) : t.remoteSpeed.tick(speed);
            t.motion.tick(t.measuredSpeed, p.isSprinting(), p.isCrouching(), p.onGround());
        }
    }
    public static WeaponMotion.Frame frame(LivingEntity e, float pt, float ads, boolean reload, boolean pistol) {
        Track t = STATES.get(e);
        return t == null ? WeaponMotion.Frame.ZERO : t.motion.sample(pt, ads, reload, pistol);
    }
    public static String status(Player p) {
        Track t = STATES.get(p);
        return p.getGameProfile().getName() + ":" + (t == null ? "NONE" : t.motion.mode())
                + ":speed=" + (t == null ? 0 : t.measuredSpeed) + ":x=" + p.getX() + ":z=" + p.getZ()
                + ":sprint=" + p.isSprinting() + ":crouch=" + p.isCrouching() + ":ground=" + p.onGround();
    }
}
