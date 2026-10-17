package ru.aiforge.arsenal.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import ru.aiforge.arsenal.AiArsenal;
import ru.aiforge.arsenal.control.Controllable;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.entity.ArsEntity;
import ru.aiforge.arsenal.entity.FpvDrone;
import ru.aiforge.arsenal.entity.LauncherEntity;
import ru.aiforge.arsenal.entity.ReconDrone;
import ru.aiforge.arsenal.network.ArsenalNetwork;
import ru.aiforge.arsenal.network.ChargeListPacket;
import ru.aiforge.arsenal.network.ControlPacket;
import ru.aiforge.arsenal.network.DesignatePacket;
import ru.aiforge.arsenal.network.SessionPacket;
import ru.aiforge.arsenal.registry.ArsenalRegistry;

import javax.annotation.Nullable;

/**
 * Client side of the arsenal: remote drone sessions (input capture, gimbal / FPV camera, zoom, designation), the
 * ground-station and FPV OSD overlays with control hints, launcher / detonator screens.
 */
@Mod.EventBusSubscriber(modid = AiArsenal.MODID, value = Dist.CLIENT)
public final class ArsClient {
    public static final String CAT = "key.categories.aiarsenal_drones";
    public static final KeyMapping KEY_EXIT = key("exit_control", GLFW.GLFW_KEY_BACKSPACE);
    public static final KeyMapping KEY_FIRE = key("drone_fire", GLFW.GLFW_KEY_F);
    public static final KeyMapping KEY_ORBIT = key("drone_orbit", GLFW.GLFW_KEY_G);
    public static final KeyMapping KEY_HOME = key("drone_home", GLFW.GLFW_KEY_H);
    public static final KeyMapping KEY_HUD = key("drone_hud", GLFW.GLFW_KEY_O);

    private static int sessionId = -1;
    private static boolean lmbPrev, firePrev;
    private static float zoom = 1f;
    private static int lostTicks;
    private static String lostReason = "";
    private static boolean hints = true;
    private static double lastFov = 70;
    @Nullable
    private static Vec3 lastRange;
    private static final RandomSource RND = RandomSource.create();
    private static int flightTicks;

    private ArsClient() {
    }

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.aiarsenal." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CAT);
    }

    // ------------------------------------------------------------------ mod bus
    @Mod.EventBusSubscriber(modid = AiArsenal.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(ArsenalRegistry.RECON.get(), ArsRenderer::new);
            e.registerEntityRenderer(ArsenalRegistry.SHAHED.get(), ArsRenderer::new);
            e.registerEntityRenderer(ArsenalRegistry.FPV.get(), ArsRenderer::new);
            e.registerEntityRenderer(ArsenalRegistry.MISSILE.get(), ArsRenderer::new);
            e.registerEntityRenderer(ArsenalRegistry.ICBM.get(), ArsRenderer::new);
            e.registerEntityRenderer(ArsenalRegistry.NUKE.get(), ArsRenderer::new);
            e.registerEntityRenderer(ArsenalRegistry.LAUNCHER.get(), ArsRenderer::new);
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            for (KeyMapping k : new KeyMapping[]{KEY_EXIT, KEY_FIRE, KEY_ORBIT, KEY_HOME, KEY_HUD}) e.register(k);
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("arsenal_hud", ArsClient::hud);
        }
    }

    // ------------------------------------------------------------------ session state
    @Nullable
    public static Entity sessionEntity() {
        Minecraft mc = Minecraft.getInstance();
        return sessionId < 0 || mc.level == null ? null : mc.level.getEntity(sessionId);
    }

    public static boolean inSession() {
        return sessionId >= 0;
    }

    public static float camYaw() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? 0 : p.getYRot();
    }

    public static float camPitch() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? 0 : p.getXRot();
    }

    private static int qaAckSession = -1;
    private static int qaReadyTicks;

    public static void session(SessionPacket pk) {
        Minecraft mc = Minecraft.getInstance();
        qaAckSession = -1;
        qaReadyTicks = 0;
        if (pk.start()) {
            sessionId = pk.entityId();
            zoom = 1f;
            flightTicks = 0;
            lostTicks = 0;
            Entity e = sessionEntity();
            if (mc.player != null) {
                if (e != null) mc.player.setYRot(e.getYRot());
                mc.player.setXRot(e instanceof ReconDrone ? 35f : 0f);
            }
        } else {
            Entity e = sessionEntity();
            sessionId = -1;
            if (mc.player != null) mc.setCameraEntity(mc.player);
            if (Boolean.getBoolean("aiarsenal.visualQa"))
                ru.aiforge.arsenal.AiArsenal.LOGGER.info("REMOTE CLIENT QA STOP");
            if (!pk.reason().isEmpty() && !pk.reason().endsWith("session_end")) {
                lostTicks = 40;
                lostReason = pk.reason();
            }
        }
    }

    public static void openLauncher(LauncherEntity l) {
        Minecraft.getInstance().setScreen(new LauncherScreen(l));
    }

    public static void charges(ChargeListPacket pk) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DetonatorScreen s) s.update(pk.entries());
        else if (pk.open()) mc.setScreen(new DetonatorScreen(pk.entries()));
    }

    // ------------------------------------------------------------------ input
    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (lostTicks > 0) lostTicks--;
        if (mc.level == null || mc.player == null) {
            sessionId = -1;
            return;
        }
        ArsSounds.tick(mc);
        NukeFx.tick(mc);
        while (KEY_HUD.consumeClick()) hints = !hints;
        if (!inSession()) {
            while (KEY_EXIT.consumeClick()) {
            }
            return;
        }
        Entity d = sessionEntity();
        if (d != null && mc.getCameraEntity() != d) mc.setCameraEntity(d);
        if (Boolean.getBoolean("aiarsenal.visualQa")) {
            boolean ready = d != null && mc.getCameraEntity() == d && mc.level.hasChunkAt(d.blockPosition());
            if (!ready) {
                if (qaAckSession != -1)
                    ru.aiforge.arsenal.AiArsenal.LOGGER.info("REMOTE CLIENT QA WAITING id={}", sessionId);
                qaAckSession = -1;
                qaReadyTicks = 0;
            } else if (++qaReadyTicks >= 20 && qaAckSession != d.getId()) {
                qaAckSession = d.getId();
                ru.aiforge.arsenal.AiArsenal.LOGGER.info("REMOTE CLIENT QA READY id={}", d.getId());
            }
        }
        if (d == null) return;
        flightTicks++;
        if (KEY_EXIT.consumeClick()) {
            ArsenalNetwork.CHANNEL.sendToServer(new ControlPacket(-1, 0, 0, 0, 1));
            return;
        }
        boolean gui = mc.screen != null;
        int k = 0;
        if (!gui) {
            if (mc.options.keyUp.isDown()) k |= Controllable.K_FWD;
            if (mc.options.keyDown.isDown()) k |= Controllable.K_BACK;
            if (mc.options.keyLeft.isDown()) k |= Controllable.K_LEFT;
            if (mc.options.keyRight.isDown()) k |= Controllable.K_RIGHT;
            if (mc.options.keyJump.isDown()) k |= Controllable.K_UP;
            if (mc.options.keyShift.isDown()) k |= Controllable.K_DOWN;
            if (mc.options.keySprint.isDown()) k |= Controllable.K_BOOST;
            if (KEY_ORBIT.isDown()) k |= Controllable.K_ORBIT;
            if (KEY_HOME.isDown()) k |= Controllable.K_HOME;
            boolean fire = KEY_FIRE.isDown();
            boolean lmb = mc.options.keyAttack.isDown();
            if (d instanceof FpvDrone) {
                if ((fire && !firePrev) || (lmb && !lmbPrev)) k |= Controllable.K_DETONATE;
            } else {
                if (fire) k |= Controllable.K_FIRE;
                if (lmb && !lmbPrev) designate(mc, d);
            }
            firePrev = fire;
            lmbPrev = lmb;
        }
        if (d instanceof FpvDrone) mc.player.setXRot(0);
        else mc.player.setXRot(Mth.clamp(mc.player.getXRot(), -5f, 90f));
        ArsenalNetwork.CHANNEL.sendToServer(new ControlPacket(d.getId(), k, mc.player.getYRot(), mc.player.getXRot(), zoom));
        // laser range finder under the crosshair (recon)
        if (d instanceof ReconDrone) {
            Vec3 from = mc.gameRenderer.getMainCamera().getPosition();
            Vec3 dir = Vec3.directionFromRotation(mc.player.getXRot(), mc.player.getYRot());
            BlockHitResult h = mc.level.clip(new ClipContext(from, from.add(dir.scale(2000)), ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, d));
            lastRange = h.getType() == HitResult.Type.MISS ? null : h.getLocation();
        }
    }

    private static void designate(Minecraft mc, Entity d) {
        Vec3 from = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 dir = Vec3.directionFromRotation(mc.player.getXRot(), mc.player.getYRot());
        Vec3 to = from.add(dir.scale(2000));
        BlockHitResult bh = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, d));
        Vec3 end = bh.getType() == HitResult.Type.MISS ? to : bh.getLocation();
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(mc.level, d, from, end, new AABB(from, end).inflate(2),
                x -> x != d && x != mc.player && x.isPickable() && !x.isSpectator());
        if (eh != null) {
            Vec3 p = eh.getEntity().position();
            ArsenalNetwork.CHANNEL.sendToServer(new DesignatePacket(d.getId(), p.x, p.y, p.z, eh.getEntity().getId()));
        } else if (bh.getType() != HitResult.Type.MISS) {
            ArsenalNetwork.CHANNEL.sendToServer(new DesignatePacket(d.getId(), end.x, end.y, end.z, -1));
        } else mc.player.displayClientMessage(Component.translatable("msg.aiarsenal.no_return"), true);
    }

    @SubscribeEvent
    public static void movement(MovementInputUpdateEvent e) {
        if (!inSession()) return;
        var in = e.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void interact(InputEvent.InteractionKeyMappingTriggered e) {
        if (!inSession()) return;
        e.setCanceled(true);
        e.setSwingHand(false);
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        if (!inSession()) return;
        e.setCanceled(true);
        if (sessionEntity() instanceof ReconDrone)
            zoom = Mth.clamp(zoom * (e.getScrollDelta() > 0 ? 1.25f : 0.8f), 1f, 40f);
    }

    @SubscribeEvent
    public static void overlayPre(RenderGuiOverlayEvent.Pre e) {
        if (!inSession() && lostTicks <= 0) return;
        var id = e.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.HOTBAR.id()) || id.equals(VanillaGuiOverlay.CROSSHAIR.id())
                || id.equals(VanillaGuiOverlay.ITEM_NAME.id()) || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id()))
            e.setCanceled(true);
    }

    // ------------------------------------------------------------------ camera
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        Entity d = sessionEntity();
        Minecraft mc = Minecraft.getInstance();
        if (d != null && mc.getCameraEntity() == d && mc.player != null) {
            float pt = (float) e.getPartialTick();
            if (d instanceof FpvDrone f) {
                float pitchUp = -Mth.lerp(pt, f.xRotO, f.getXRot());
                e.setYaw(mc.player.getViewYRot(pt));
                e.setPitch(-(pitchUp + 25f));
                e.setRoll(Mth.lerp(pt, f.rollO, f.roll()));
            } else {
                e.setYaw(mc.player.getViewYRot(pt));
                e.setPitch(mc.player.getViewXRot(pt));
                e.setRoll(0);
            }
        }
        NukeFx.shake(e);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        if (sessionEntity() instanceof ReconDrone && zoom > 1f) e.setFOV(e.getFOV() / zoom);
        else if (sessionEntity() instanceof FpvDrone) e.setFOV(Math.min(110, e.getFOV() * 1.25));
        lastFov = e.getFOV();
    }

    @SubscribeEvent
    public static void fovMod(ComputeFovModifierEvent e) {
        if (inSession()) e.setNewFovModifier(1f);
    }

    // ------------------------------------------------------------------ HUD
    private static final int GREEN = 0xFF7CFF7C, AMBER = 0xFFFFC040, RED = 0xFFFF4040, WHITE = 0xFFFFFFFF;

    static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        NukeFx.hud(g, w, h, pt);
        if (lostTicks > 0) {
            staticNoise(g, w, h, 1f);
            Component c = Component.translatable(lostReason.isEmpty() ? "hud.aiarsenal.no_signal" : lostReason);
            g.drawCenteredString(mc.font, c, w / 2, h / 2 - 4, RED);
            return;
        }
        Entity d = sessionEntity();
        if (d instanceof ReconDrone r) reconHud(g, mc, r, w, h, pt);
        else if (d instanceof FpvDrone f) fpvHud(g, mc, f, w, h);
        else if (inSession()) g.drawCenteredString(mc.font, Component.translatable("hud.aiarsenal.connecting"), w / 2, h / 2, AMBER);
        else heldItemHud(g, mc, w, h);
    }

    private static void heldItemHud(GuiGraphics g, Minecraft mc, int w, int h) {
        if (mc.player == null) return;
        var s = mc.player.getMainHandItem();
        if (s.getItem() instanceof ru.aiforge.arsenal.item.TabletItem) {
            var t = ru.aiforge.arsenal.item.TabletItem.target(s);
            String txt = t == null ? Component.translatable("hud.aiarsenal.tablet_no_target").getString()
                    : Component.translatable("hud.aiarsenal.tablet_target", t.getX(), t.getY(), t.getZ(),
                    (int) Math.sqrt(mc.player.blockPosition().distSqr(t))).getString();
            g.drawCenteredString(mc.font, txt, w / 2, h - 72, AMBER);
        }
    }

    private static String kmh(Entity e) {
        double v = e.getDeltaMovement().length() * 20 / ArsEntity.scale() * 3.6;
        return String.valueOf((int) v);
    }

    private static void reconHud(GuiGraphics g, Minecraft mc, ReconDrone r, int w, int h, float pt) {
        Font f = mc.font;
        int cx = w / 2, cy = h / 2;
        // reticle
        g.fill(cx - 30, cy, cx - 8, cy + 1, GREEN);
        g.fill(cx + 8, cy, cx + 30, cy + 1, GREEN);
        g.fill(cx, cy - 30, cx + 1, cy - 8, GREEN);
        g.fill(cx, cy + 8, cx + 1, cy + 30, GREEN);
        g.fill(cx, cy, cx + 1, cy + 1, GREEN);
        int bw = (int) (w * 0.32), bh = (int) (h * 0.32);
        for (int sx : new int[]{-1, 1})
            for (int sy : new int[]{-1, 1}) {
                int x = cx + sx * bw, y = cy + sy * bh;
                g.fill(Math.min(x, x - sx * 14), y, Math.max(x, x - sx * 14) + 1, y + 1, GREEN);
                g.fill(x, Math.min(y, y - sy * 14), x + 1, Math.max(y, y - sy * 14) + 1, GREEN);
            }
        int gr = ArsEntity.ground(r.level(), r.getX(), r.getZ());
        String[] st = {"PARKED", "TAKEOFF", "LOITER", "RTB", "APPROACH", "ROLLOUT", "CRASH"};
        String mode = st[Mth.clamp(r.state(), 0, st.length - 1)];
        int y = 6;
        g.drawString(f, Component.translatable(r.kind().translationKey()).getString() + "  " + mode, 6, y, GREEN);
        g.drawString(f, String.format("ALT %d m  AGL %d m  CMD %d", (int) (r.getY() - 63), (int) (r.getY() - gr), (int) r.altitudeTarget()), 6, y += 11, GREEN);
        g.drawString(f, String.format("SPD %s km/h  HDG %03d", kmh(r), (int) Mth.positiveModulo(r.getYRot() + 180f, 360f)), 6, y += 11, GREEN);
        g.drawString(f, String.format("POS %d %d", (int) r.getX(), (int) r.getZ()), 6, y += 11, GREEN);
        String w1 = r.kind() == Kind.MQ9 ? "AGM-114" : "KAB-50";
        g.drawString(f, w1 + " x" + r.ammo(), 6, y += 11, r.ammo() > 0 ? GREEN : RED);
        int rx = w - 6;
        float az = Mth.positiveModulo(camYaw() + 180f, 360f);
        String c1 = String.format("AZ %03d  EL %d", (int) az, (int) -camPitch());
        String c2 = String.format("EO  x%.1f  FOV %.1f", zoom, lastFov);
        g.drawString(f, c1, rx - f.width(c1), 6, GREEN);
        g.drawString(f, c2, rx - f.width(c2), 17, GREEN);
        if (lastRange != null) {
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            String c3 = String.format("LRF %d m  %d %d %d", (int) lastRange.distanceTo(cam), (int) lastRange.x, (int) lastRange.y, (int) lastRange.z);
            g.drawString(f, c3, rx - f.width(c3), 28, GREEN);
        } else g.drawString(f, "LRF ----", rx - f.width("LRF ----"), 28, GREEN);
        if (r.target() != null) {
            var t = r.target();
            Vec3 tp = Vec3.atCenterOf(t);
            String c4 = String.format("TGT %d %d %d  RNG %d m", t.getX(), t.getY(), t.getZ(), (int) tp.distanceTo(r.position()));
            g.drawString(f, c4, rx - f.width(c4), 39, RED);
            float[] sp = project(mc, tp, w, h);
            if (sp != null) {
                int x = (int) sp[0], yy = (int) sp[1];
                g.fill(x - 6, yy - 6, x + 6, yy - 5, RED);
                g.fill(x - 6, yy + 5, x + 6, yy + 6, RED);
                g.fill(x - 6, yy - 6, x - 5, yy + 6, RED);
                g.fill(x + 5, yy - 6, x + 6, yy + 6, RED);
            }
        }
        // time / link
        String clock = String.format("REC %02d:%02d:%02d", flightTicks / 72000, flightTicks / 1200 % 60, flightTicks / 20 % 60);
        g.drawString(f, clock, rx - f.width(clock), h - 40, RED);
        if (hints) hints(g, f, w, h, r.state() == ReconDrone.PARKED ? "hint.aiarsenal.recon_parked" : "hint.aiarsenal.recon");
    }

    private static void fpvHud(GuiGraphics g, Minecraft mc, FpvDrone d, int w, int h) {
        Font f = mc.font;
        float link = d.link();
        // analog video: scanlines + static that grows with a weak link
        for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, 0x14000000);
        if (link < 0.75f) staticNoise(g, w, h, Mth.clamp((0.75f - link) / 0.7f, 0f, 1f));
        int cx = w / 2, cy = h / 2;
        g.fill(cx - 5, cy, cx + 6, cy + 1, WHITE);
        g.fill(cx, cy - 5, cx + 1, cy + 6, WHITE);
        // artificial horizon
        float roll = (float) Math.toRadians(d.roll()), pitch = d.pitch() + 25f;
        int off = (int) (pitch * 3);
        int len = 60;
        int x0 = (int) (cx - Math.cos(roll) * len), y0 = (int) (cy + off - Math.sin(roll) * len);
        int x1 = (int) (cx + Math.cos(roll) * len), y1 = (int) (cy + off + Math.sin(roll) * len);
        line(g, x0, y0, x1, y1, 0xC0FFFFFF);
        int gr = ArsEntity.ground(d.level(), d.getX(), d.getZ());
        double batt = Mth.clamp(d.batteryTicks() / 7200.0, 0, 1);
        double volts = 19.8 + 5.4 * batt;
        int lq = (int) (link * 100);
        int t = flightTicks / 20;
        g.drawString(f, String.format("%.1fV", volts), 8, h - 30, batt < 0.25 ? RED : WHITE);
        g.drawString(f, String.format("%d:%02d", t / 60, t % 60), 8, h - 19, WHITE);
        g.drawString(f, "THR " + (int) (d.throttleSynced() * 100) + "%", 8, 8, WHITE);
        g.drawString(f, "LQ " + lq + "  RSSI -" + (int) (40 + (1 - link) * 60) + "dBm", 8, 19, lq < 30 ? RED : WHITE);
        String sp = kmh(d) + " km/h";
        g.drawString(f, sp, w - 8 - f.width(sp), 8, WHITE);
        String al = (int) (d.getY() - gr) + " m";
        g.drawString(f, al, w - 8 - f.width(al), 19, WHITE);
        String pay = d.kind() == Kind.FPV10 ? "TM-62M" : "PG-7VL";
        String arm = d.state() == FpvDrone.IDLE ? "DISARMED" : (d.state() == FpvDrone.FAILSAFE ? "FAILSAFE" : "ARMED " + pay);
        g.drawCenteredString(f, arm, cx, h - 44, d.state() == FpvDrone.FLYING ? RED : AMBER);
        if (hints) hints(g, f, w, h, "hint.aiarsenal.fpv");
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i += 2) {
            int x = x0 + (x1 - x0) * i / Math.max(1, n), y = y0 + (y1 - y0) * i / Math.max(1, n);
            g.fill(x, y, x + 2, y + 1, color);
        }
    }

    static void staticNoise(GuiGraphics g, int w, int h, float k) {
        int n = (int) (300 * k) + 20;
        for (int i = 0; i < n; i++) {
            int x = RND.nextInt(w), y = RND.nextInt(h);
            int c = 80 + RND.nextInt(175);
            int a = (int) (40 + 200 * k);
            g.fill(x, y, x + 1 + RND.nextInt(12), y + 1, (a << 24) | (c << 16) | (c << 8) | c);
        }
        if (k > 0.5f) {
            int lines = (int) (k * 6);
            for (int i = 0; i < lines; i++) {
                int y = RND.nextInt(h);
                g.fill(0, y, w, y + 1 + RND.nextInt(3), ((int) (90 * k) << 24) | 0xFFFFFF);
            }
            g.fill(0, 0, w, h, ((int) (120 * (k - 0.5f)) << 24) | 0x202020);
        }
    }

    private static void hints(GuiGraphics g, Font f, int w, int h, String key) {
        String s = Component.translatable(key).getString();
        String[] parts = s.split("\\|");
        int y = h - 12 - (parts.length - 1) * 10;
        for (String p : parts) {
            int tw = f.width(p);
            g.fill(w / 2 - tw / 2 - 3, y - 2, w / 2 + tw / 2 + 3, y + 9, 0x90000000);
            g.drawCenteredString(f, p, w / 2, y, 0xFFE0E0E0);
            y += 10;
        }
    }

    /** World point to screen (GUI-scaled) coordinates for the current camera, or null when behind. */
    @Nullable
    static float[] project(Minecraft mc, Vec3 p, int w, int h) {
        var cam = mc.gameRenderer.getMainCamera();
        Vec3 rel = p.subtract(cam.getPosition());
        Quaternionf inv = new Quaternionf(cam.rotation()).conjugate();
        Vector3f c = inv.transform(new Vector3f((float) rel.x, (float) rel.y, (float) rel.z));
        if (c.z <= 0.1f) return null;
        double f = (h / 2.0) / Math.tan(Math.toRadians(lastFov) / 2);
        return new float[]{(float) (w / 2.0 - c.x / c.z * f), (float) (h / 2.0 - c.y / c.z * f)};
    }
}
