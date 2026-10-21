package ru.aiforge.realwar.client;

import net.minecraft.world.phys.Vec3;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.realwar.handheld.FirearmItem;
import ru.aiforge.realwar.handheld.Firearms;
import ru.aiforge.realwar.handheld.GunKind;
import ru.aiforge.realwar.handheld.MagazineItem;
import ru.aiforge.realwar.network.GunActionPacket;
import ru.aiforge.realwar.network.GunFxPacket;
import ru.aiforge.realwar.network.ModNetwork;

/**
 * r22 small arms, client: LMB trigger / RMB aim (ADS) / R reload / B fire selector, recoil with partial recovery,
 * ADS zoom, the hip crosshair, the ammo panel and the optic overlays (Aimpoint CompM4 red dot, PGO-7V3 reticle).
 * r23: RPG-7 aims over its iron sights like the AK-12 (B switches to the PGO-7V3); T-5000 5-25x riflescope (mouse wheel)
 * with the mouse sensitivity scaled to the magnification; PKP over its iron sights.
 */
@Mod.EventBusSubscriber(modid = "aiarsenal", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GunClient {
    private static float ads, adsPrev;
    private static GunKind lastHeld;
    private static boolean trig, aim, rPrev, bPrev;
    private static long shotN = Long.MIN_VALUE / 2, lastFrameN, reloadN;
    private static float pendP, pendY, debt, debtY;
    private static boolean reloading, reloadEmpty;
    private static int shotCount;
    private static boolean rpgOptic;
    private static int zoomIdx = 1;
    private static final int[] ZOOM = {5, 8, 12, 18, 25};
    private static Double sensSaved;
    private static int toneT;
    private static final java.util.Random RND = new java.util.Random();

    private GunClient() {
    }

    // ------------------------------------------------------------------ state for the renderer

    public static GunKind held() {
        Player p = Minecraft.getInstance().player;
        if (p == null) return null;
        return p.getMainHandItem().getItem() instanceof FirearmItem g ? g.kind : null;
    }

    public static float adsLerp(float pt) {
        float a = adsPrev + (ads - adsPrev) * pt;
        return a * a * (3 - 2 * a);
    }

    /** gun model replaced by the optic overlay (M4 Aimpoint, T-5000 riflescope, RPG PGO-7V3 when selected with B);
     *  the AK-12, PKP and the RPG-7 (default) aim over their iron sights with the 3D model */
    public static boolean scoped() {
        GunKind k = held();
        boolean optic = k == GunKind.M4A1 || k != null && k.scope() || k == GunKind.RPG7 && rpgOptic;
        return optic && Minecraft.getInstance().options.getCameraType().isFirstPerson() && !reloading && adsLerp(Minecraft.getInstance().getFrameTime()) > 0.85f;
    }

    /** RPG-7: PGO-7V3 selected (B) instead of the iron sights */
    public static boolean rpgOptic() {
        return rpgOptic;
    }

    /** current riflescope magnification (T-5000) */
    public static int zoom() {
        return ZOOM[Math.max(0, Math.min(ZOOM.length - 1, zoomIdx))];
    }

    /** ADS field-of-view factor of the held weapon */
    static float adsFactor(GunKind k) {
        if (k == GunKind.T5000) return 1f / zoom();
        if (k == GunKind.RPG7) return rpgOptic ? k.adsFov : 0.80f;
        return k.adsFov;
    }

    /** mouse wheel while looking through the T-5000 scope = magnification 5/8/12/18/25x */
    public static boolean onScroll(double delta) {
        if (held() != GunKind.T5000 || !scoped() || delta == 0) return false;
        zoomIdx = Math.max(0, Math.min(ZOOM.length - 1, zoomIdx + (delta > 0 ? 1 : -1)));
        return true;
    }

    /** seconds since the last shot */
    public static float sinceShot() {
        return (System.nanoTime() - shotN) / 1e9f;
    }

    public static int shotCount() {
        return shotCount;
    }

    public static boolean reloading() {
        return reloading;
    }

    public static boolean reloadEmpty() {
        return reloadEmpty;
    }

    public static float reloadTime() {
        return (System.nanoTime() - reloadN) / 1e9f;
    }

    public static boolean hideCrosshair() {
        Minecraft mc = Minecraft.getInstance();
        return held() != null && mc.player != null && mc.player.getVehicle() == null;
    }

    // ------------------------------------------------------------------ input

    private static void send(int a) {
        if (Minecraft.getInstance().getConnection() != null) ModNetwork.CHANNEL.sendToServer(new GunActionPacket(a));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (e.phase == TickEvent.Phase.START) {
            if (p != null && held() != null && p.getVehicle() == null) {
                while (mc.options.keyAttack.consumeClick()) {
                }
                while (mc.options.keyUse.consumeClick()) {
                }
            }
            return;
        }
        adsPrev = ads;
        GunKind k = held();
        if (k != lastHeld) {
            if (trig) send(Firearms.TRIGGER_UP);
            if (aim) send(Firearms.AIM_OFF);
            trig = aim = false;
            ads = adsPrev = 0f;
            reloading = false;
            shotN = Long.MIN_VALUE / 2;
            pendP = pendY = debt = debtY = 0f;
            lastHeld = k;
        }
        boolean ok = p != null && k != null && mc.screen == null && p.getVehicle() == null && !p.isSpectator();
        if (!ok) {
            if (trig) send(Firearms.TRIGGER_UP);
            if (aim) send(Firearms.AIM_OFF);
            trig = aim = false;
            ads = Math.max(0f, ads - 0.25f);
            if (k == null) reloading = false;
            return;
        }
        boolean t = mc.options.keyAttack.isDown();
        if (t != trig) {
            trig = t;
            send(t ? Firearms.TRIGGER_DOWN : Firearms.TRIGGER_UP);
        }
        boolean a = mc.options.keyUse.isDown();
        if (a != aim) {
            aim = a;
            send(a ? Firearms.AIM_ON : Firearms.AIM_OFF);
        }
        float target = aim && !reloading && !(k.boltAction() && sinceShot() < 1.25f) && !p.isSprinting() ? 1f : 0f;
        float sp = k.launcher() ? 0.14f : 0.24f;
        ads += Math.max(-sp, Math.min(sp, target - ads));
        boolean r = VehicleClient.KEY_RELOAD.isDown(), rc = false, bc = false;
        while (VehicleClient.KEY_RELOAD.consumeClick()) rc = true;           // short taps are caught too
        if (rc || r && !rPrev) send(Firearms.RELOAD);
        rPrev = r;
        boolean b = AircraftClient.KEY_BRAKE.isDown();
        while (AircraftClient.KEY_BRAKE.consumeClick()) bc = true;
        if ((bc || b && !bPrev) && k.selector()) send(Firearms.MODE);
        else if ((bc || b && !bPrev) && k == GunKind.RPG7) {
            rpgOptic = !rpgOptic;                                            // r23: PGO-7V3 <-> iron sights
            p.playSound(ru.aiforge.realwar.registry.ModSounds.SELECTOR.get(), 0.5f, 0.8f);
        }
        bPrev = b;
        if (k.manpads() && aim && ads > 0.5f && !reloading && FirearmItem.ammo(p.getMainHandItem()) > 0) {
            // r25 seeker tone: silent while cooling, slow growl searching, faster while locking, high and steady when locked
            ItemStack st = p.getMainHandItem();
            int lk = Firearms.lock(st), cl = Firearms.cool(st);
            int period = cl < 100 ? 0 : lk >= 100 ? 3 : lk > 0 ? 6 : 14;
            if (period > 0 && ++toneT % period == 0)
                p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), ru.aiforge.realwar.registry.ModSounds.LOCK_TONE.get(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.35f, lk >= 100 ? 1.6f : 0.8f + lk / 200f, false);
        }
        if (reloading && reloadTime() > (reloadEmpty ? k.reloadEmpty : k.reload) / 20f + 1.0f) reloading = false;
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        if (onScroll(e.getScrollDelta())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void loggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) {
        restoreSensitivity();
    }

    /** cancels vanilla attack / use / pick while a firearm is in hand */
    public static boolean onInteraction(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (held() == null || mc.player == null || mc.player.getVehicle() != null) return false;
        e.setCanceled(true);
        e.setSwingHand(false);
        return true;
    }

    public static void onFx(int fx, int ammo) {
        GunKind k = held();
        switch (fx) {
            case GunFxPacket.SHOT -> {
                shotN = System.nanoTime();
                shotCount++;
                localMuzzleBlast(k);
                if (k != null && k.boltAction()) ads = adsPrev = 0f;
                if (k != null) {
                    float m = (ads > 0.5f ? 0.65f : 1f);
                    Player p = Minecraft.getInstance().player;
                    if (p != null && p.isCrouching()) m *= 0.75f;
                    pendP += k.recoilPitch * m * (0.85f + RND.nextFloat() * 0.3f);
                    pendY += k.recoilYaw * m * (RND.nextFloat() * 2f - 0.8f);    // slight right drift like a real AK / AR
                }
            }
            case GunFxPacket.RELOAD, GunFxPacket.RELOAD_EMPTY -> {
                reloading = true;
                ads = adsPrev = 0f; // never show an enlarged hip/reload model through residual scope zoom
                reloadEmpty = fx == GunFxPacket.RELOAD_EMPTY;
                reloadN = System.nanoTime();
                if (Boolean.getBoolean("aiarsenal.visualQa") && k != null)
                    ru.aiforge.arsenal.AiArsenal.LOGGER.info("WEAPON QA reload-start {}", k.id);
            }
            case GunFxPacket.LOADED, GunFxPacket.CANCEL -> reloading = false;
            default -> {
            }
        }
    }

    /** The shooter's own muzzle blast: sparks and the cordite cloud that drifts into his view. */
    private static void localMuzzleBlast(GunKind k) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.multiplayer.ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        var look = mc.player.getViewVector(1f);
        var eye = mc.player.getEyePosition();
        float reach = k == null ? 0.95f : k.flintlock() && !k.pistol() ? 1.35f : k.pistol() ? 0.6f : 0.95f;
        var at = eye.add(look.scale(reach)).add(0, -0.10, 0);
        ru.aiforge.realwar.client.fx.MuzzleFx.blast(level, at, look, k, shotCount);
    }

    /** smooth recoil: the kick is applied over ~40 ms, 75 % of it recovers once the burst ends */
    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrameN) / 1e9f);
        lastFrameN = now;
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        if (pendP > 1e-4f || Math.abs(pendY) > 1e-4f) {
            float f = Math.min(1f, dt * 28f);
            float ap = pendP * f, ay = pendY * f;
            pendP -= ap;
            pendY -= ay;
            p.setXRot(p.getXRot() - ap);
            p.xRotO -= ap;
            p.setYRot(p.getYRot() + ay);
            p.yRotO += ay;
            debt += ap * 0.75f;
            debtY += ay * 0.5f;
        }
        if (sinceShot() > 0.14f && (debt > 1e-3f || Math.abs(debtY) > 1e-3f)) {
            float f = Math.min(1f, dt * 7f);
            float rp = debt * f, ry = debtY * f;
            debt -= rp;
            debtY -= ry;
            p.setXRot(Math.min(90f, p.getXRot() + rp));
            p.xRotO += rp;
            p.setYRot(p.getYRot() - ry);
            p.yRotO -= ry;
        }
        if (held() == null) {
            debt = debtY = pendP = pendY = 0f;
        }
        sensitivity();
    }

    /**
     * r23: high-power optics (T-5000 5-25x, PGO-7V3 4x) turn the view as many times slower as they magnify, like a real
     * scope; the option value is restored as soon as the scope is left (never saved scaled).
     */
    private static void sensitivity() {
        Minecraft mc = Minecraft.getInstance();
        GunKind k = held();
        boolean on = k != null && mc.screen == null && mc.player != null && mc.player.getVehicle() == null && scoped() && adsFactor(k) < 0.6f;
        var opt = mc.options.sensitivity();
        if (on) {
            if (sensSaved == null) sensSaved = opt.get();
            double d = sensSaved * 0.6 + 0.2;                                  // vanilla: turn rate ~ d^3
            double d2 = d * Math.cbrt(adsFactor(k));
            double want = Math.max(0.0, Math.min(1.0, (d2 - 0.2) / 0.6));
            if (Math.abs(opt.get() - want) > 1e-6) opt.set(want);
        } else if (sensSaved != null) {
            opt.set(sensSaved);
            sensSaved = null;
        }
    }

    /** restores the option if the player leaves the world while scoped */
    public static void restoreSensitivity() {
        if (sensSaved != null) {
            Minecraft.getInstance().options.sensitivity().set(sensSaved);
            sensSaved = null;
        }
    }

    @SubscribeEvent
    public static void fov(ComputeFovModifierEvent e) {
        if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;
        GunKind k = held();
        if (k == null) return;
        float a = adsLerp(Minecraft.getInstance().getFrameTime());
        float base = e.getNewFovModifier();
        if (a > 0f) base = Math.min(base, 1f);                           // no sprint widening while aiming
        e.setNewFovModifier(base * (1f + (adsFactor(k) - 1f) * a));
    }

    // ------------------------------------------------------------------ HUD

    @SubscribeEvent
    public static void scopeOverlays(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre e) {
        // Iron-sight ADS needs the same clean view as an optical sight; restore chat outside ADS.
        var mc = Minecraft.getInstance();
        if (held() == null || !mc.options.getCameraType().isFirstPerson() || adsLerp(mc.getFrameTime()) < 0.4f) return;
        var id = e.getOverlay().id();
        if (id.equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CHAT_PANEL.id())
                || id.equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.ITEM_NAME.id())) e.setCanceled(true);
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        GunKind k = held();
        if (p == null || k == null || mc.options.hideGui || p.getVehicle() != null) return;
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400); // above chat depth, including the compact ammo panel
        float a = adsLerp(pt);
        if (scoped()) {
            if (k == GunKind.RPG7) pgo7(g, mc.font, w, h, mc.options.fov().get() * k.adsFov);
            else if (k.singleLoad()) historicalScope(g, mc.font, w, h, k);
            else if (k == GunKind.T5000) riflescope(g, mc.font, w, h, mc.options.fov().get() * adsFactor(k));
            else aimpoint(g, w, h);
        } else if (!mc.options.getCameraType().isFirstPerson()) {
            hipCross(g, w, h, k, p, 1f);
        } else if (a < 0.4f) {
            hipCross(g, w, h, k, p, 1f - a / 0.4f);
        }
        if (k.manpads() && a > 0.5f && !reloading && FirearmItem.ammo(p.getMainHandItem()) > 0) seeker(g, mc.font, p, k, w, h, pt);
        panel(g, mc.font, p, k, w, h);
        g.flush();
        g.pose().popPose();
    }

    private static void hipCross(GuiGraphics g, int w, int h, GunKind k, Player p, float alpha) {
        int cx = w / 2, cy = h / 2;
        double moving = p.getDeltaMovement().horizontalDistance() > 0.08 ? 2.2 : 1.0;
        float fovV = Minecraft.getInstance().options.fov().get();
        float ppd = h / fovV;
        int gap = Math.max(2, Math.round((float) Math.toDegrees(k.hipSpread * moving) * ppd));
        gap += Math.round(Math.max(0f, 0.18f - sinceShot()) * 30f);
        int col = ((int) (alpha * 0xD0) << 24) | 0xFFFFFF;
        int sh = ((int) (alpha * 0x70) << 24);
        int L = 4;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int x0 = cx + d[0] * gap, y0 = cy + d[1] * gap;
            int x1 = cx + d[0] * (gap + L), y1 = cy + d[1] * (gap + L);
            g.fill(Math.min(x0, x1) - (d[0] == 0 ? 1 : 0) + 1, Math.min(y0, y1) - (d[1] == 0 ? 1 : 0) + 1,
                    Math.max(x0, x1) + (d[0] == 0 ? 1 : 0) + 1, Math.max(y0, y1) + (d[1] == 0 ? 1 : 0) + 1, sh);
            g.fill(Math.min(x0, x1) - (d[0] == 0 ? 0 : 0), Math.min(y0, y1), Math.max(x0, x1) + (d[0] == 0 ? 1 : 0), Math.max(y0, y1) + (d[1] == 0 ? 1 : 0), col);
        }
        g.fill(cx, cy, cx + 1, cy + 1, col);
    }

    /**
     * bottom-left War Thunder slot bar (same look as the vehicle HUD): the ammo slot with the rounds counter and the
     * clockwise reload pie, then the R / B action tiles in the same row; info strip (weapon, mode, spare) above it.
     */
    private static void panel(GuiGraphics g, Font f, Player p, GunKind k, int w, int h) {
        ItemStack st = p.getMainHandItem();
        int ammo = FirearmItem.ammo(st);
        int spare = 0;
        net.minecraft.world.item.Item want = Firearms.ammoItem(k);
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(want)) spare += k.looseAmmo() ? s.getCount() * k.roundsPerItem() : (MagazineItem.rounds(s) > 0 ? s.getCount() : 0);
        }
        boolean auto = k.auto && (!k.selector() || FirearmItem.mode(st) == 1);
        float rem = 0f;
        if (reloading) {
            float tot = (reloadEmpty ? k.reloadEmpty : k.reload) / 20f;
            rem = Math.max(0.001f, 1f - reloadTime() / tot);
        }
        String name = Component.translatable("hud.aiarsenal.gun.name." + k.id).getString();
        java.util.List<WtHud.Slot> sl = java.util.List.of(new WtHud.Slot(k.launcher() ? 3 : 2, ammo, k.cap, true, rem, name));
        StringBuilder b = new StringBuilder(name).append("  \u00b7  ");
        if (k.manpads()) b.append(Component.translatable("hud.aiarsenal.gun.m." + k.id).getString());
        else if (k.launcher()) b.append(Component.translatable("hud.aiarsenal.gun.pg7vl").getString()).append("  \u00b7  ")
                .append(Component.translatable(rpgOptic ? "hud.aiarsenal.gun.pgo" : "hud.aiarsenal.gun.irons").getString());
        else if (k.singleLoad()) b.append(k.fixedMagnification()).append("×");
        else if (k.scope()) b.append(zoom()).append("\u00d7");
        else if (k.boltAction()) b.append(Component.translatable("hud.aiarsenal.gun.bolt").getString());
        else b.append(Component.translatable(auto ? "hud.aiarsenal.gun.auto" : "hud.aiarsenal.gun.semi").getString());
        b.append("  \u00b7  +").append(spare);
        int col = 0xFFE8E8E8;
        if (reloading) {
            b.append("  \u00b7  ").append(Component.translatable("hud.aiarsenal.gun.reloading").getString());
            col = 0xFFFFC040;
        } else if (ammo == 0) {
            b.append("  \u00b7  ").append(Component.translatable("hud.aiarsenal.gun.empty").getString());
            col = 0xFFFF6050;
        }
        int band = Math.max(80, w / 2 - 91 - 6);                   // free space left of the hotbar
        g.pose().pushPose();
        g.pose().translate(band / 2f + 3 - w / 2f, 0, 0);
        WtHud.drawSlots(g, f, sl, b.toString(), col, w, h, ActionBar.gun(k, k.launcher() ? rpgOptic : auto, reloading), band);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ r25 MANPADS seeker overlay

    /**
     * Seeker field of view (circle of the real cone), its state under it (cooling / search / locking / locked) and a
     * small diamond on the tracked target with its range; colours like the WT crosshair (green / amber / red).
     */
    private static void seeker(GuiGraphics g, Font f, Player p, GunKind k, int w, int h, float pt) {
        ItemStack st = p.getMainHandItem();
        int lk = Firearms.lock(st), cl = Firearms.cool(st);
        float fovV = (float) (Minecraft.getInstance().options.fov().get() * adsFactor(k));
        float ppd = h / fovV;
        int cx = w / 2, cy = h / 2;
        int r = Math.max(6, Math.round(k.missile().cone * ppd));
        int col = cl < 100 ? 0xB0B0B0B0 : lk >= 100 ? 0xFFFF3030 : lk > 0 ? 0xFFFFC020 : 0xD040FF60;
        int n = Math.max(24, r * 2);
        for (int i = 0; i < n; i++) {
            double t = Math.PI * 2 * i / n;
            int x = cx + (int) Math.round(Math.cos(t) * r), y = cy + (int) Math.round(Math.sin(t) * r);
            g.fill(x, y, x + 1, y + 1, col);
        }
        String state = cl < 100 ? Component.translatable("hud.aiarsenal.gun.cooling", cl).getString()
                : lk >= 100 ? Component.translatable("hud.aiarsenal.gun.locked").getString()
                : lk > 0 ? Component.translatable("hud.aiarsenal.hh.locking", lk).getString()
                : Component.translatable("hud.aiarsenal.hh.search").getString();
        g.pose().pushPose();
        g.pose().translate(cx, cy + r + 5, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawCenteredString(f, state, 0, 0, col);
        g.pose().popPose();
        int tid = Firearms.target(st);
        net.minecraft.world.entity.Entity t = tid != Integer.MIN_VALUE ? p.level().getEntity(tid) : null;
        if (t == null) return;
        Vec3 eye = p.getEyePosition(pt);
        Vec3 to = t.getBoundingBox().getCenter().subtract(eye);
        double dist = to.length();
        to = to.normalize();
        Vec3 fwd = p.getViewVector(pt);
        Vec3 right = fwd.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-6) return;
        right = right.normalize();
        Vec3 up = right.cross(fwd).normalize();
        double z = to.dot(fwd);
        if (z < 0.05) return;
        float sx = cx + (float) Math.toDegrees(Math.atan(to.dot(right) / z)) * ppd;
        float sy = cy - (float) Math.toDegrees(Math.atan(to.dot(up) / z)) * ppd;
        int x = Math.round(sx), y = Math.round(sy), d = 5;
        for (int i = 0; i <= d; i++) {
            g.fill(x - d + i, y - i, x - d + i + 1, y - i + 1, col);
            g.fill(x + d - i, y - i, x + d - i + 1, y - i + 1, col);
            g.fill(x - d + i, y + i, x - d + i + 1, y + i + 1, col);
            g.fill(x + d - i, y + i, x + d - i + 1, y + i + 1, col);
        }
        g.pose().pushPose();
        g.pose().translate(x + d + 3, y - 3, 0);
        g.pose().scale(0.7f, 0.7f, 1f);
        g.drawString(f, Math.round(dist) + " m", 0, 0, col, true);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ optics

    private static void ring(GuiGraphics g, int cx, int cy, int r, int w, int h, int col) {
        // everything outside the circle of radius r is filled (scanline mask)
        g.fill(0, 0, w, Math.max(0, cy - r), col);
        g.fill(0, Math.min(h, cy + r), w, h, col);
        for (int y = Math.max(0, cy - r); y < Math.min(h, cy + r); y++) {
            double dy = y + 0.5 - cy;
            int half = (int) Math.round(Math.sqrt(Math.max(0, r * (double) r - dy * dy)));
            g.fill(0, y, cx - half, y + 1, col);
            g.fill(cx + half, y, w, y + 1, col);
        }
    }

    /** PU three-post reticle and ZF39 German No.1 pointed post; no modern mil-dot tree. */
    private static void historicalScope(GuiGraphics g, Font f, int w, int h, GunKind k) {
        int cx = w / 2, cy = h / 2, r = Math.round(h * 0.45f), ink = 0xFF090909;
        ring(g, cx, cy, r, w, h, 0xFF080808);
        int gap = Math.max(8, r / 7), thick = Math.max(2, r / 42);
        g.fill(cx - r, cy - thick, cx - gap, cy + thick, ink);
        g.fill(cx + gap, cy - thick, cx + r, cy + thick, ink);
        if (k == GunKind.MOSIN_PU) {
            g.fill(cx - 1, cy, cx + 1, cy + gap, ink);
            g.fill(cx - thick, cy + gap, cx + thick, cy + r, ink);
            g.fill(cx - gap, cy, cx + gap, cy + 1, ink);
        } else {
            for (int y = 0; y < gap; y++) {
                int half = Math.max(0, thick * y / gap);
                g.fill(cx - half, cy + y, cx + half + 1, cy + y + 1, ink);
            }
            g.fill(cx - thick, cy + gap, cx + thick + 1, cy + r, ink);
        }
        g.drawString(f, k == GunKind.MOSIN_PU ? "PU 3.5×" : "ZF39 4×", cx - 24, h - 14, 0xFFC8C2AF, false);
    }

    /** Aimpoint CompM4: 30 mm tube seen at the eye, 2 MOA red dot with a soft halo */
    private static void aimpoint(GuiGraphics g, int w, int h) {
        int cx = w / 2, cy = h / 2;
        int r = Math.round(h * 0.40f);
        ring(g, cx, cy, r + 10, w, h, 0xF0101010);
        for (int i = 0; i < 10; i++) {                                   // tube wall gradient
            int rr = r + i;
            int a = 0x50 + i * 0x14;
            for (int y = cy - rr; y <= cy + rr; y++) {
                double dy = y + 0.5 - cy;
                int half = (int) Math.round(Math.sqrt(Math.max(0, rr * (double) rr - dy * dy)));
                int half2 = (int) Math.round(Math.sqrt(Math.max(0, (rr - 1) * (double) (rr - 1) - dy * dy)));
                if (half > half2) {
                    g.fill(cx - half, y, cx - half2, y + 1, (a << 24) | 0x181818);
                    g.fill(cx + half2, y, cx + half, y + 1, (a << 24) | 0x181818);
                } else if (Math.abs(dy) >= rr - 1) {
                    g.fill(cx - half, y, cx + half, y + 1, (a << 24) | 0x181818);
                }
            }
        }
        g.fill(0, 0, w, h, 0x0A40A0FF);                                  // faint lens tint
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0x40FF2010);
        g.fill(cx - 1, cy - 2, cx + 2, cy + 3, 0x70FF2010);
        g.fill(cx - 2, cy - 1, cx + 3, cy + 2, 0x70FF2010);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFF3020);
        g.fill(cx, cy, cx + 1, cy + 1, 0xFFFFC0B0);
    }

    /**
     * T-5000 riflescope (5-25x56, first focal plane MRAD reticle): black eyepiece ring with a thin bright edge, fine
     * cross with 1-mil dots / half-mil hashes numbered every 2 mil, thick posts outside 10 mil, tree below for holdovers.
     * The reticle is scaled with the magnification (FFP), so a mil always covers the same angle (0.0573 deg).
     */
    private static void riflescope(GuiGraphics g, Font f, int w, int h, float fovV) {
        int cx = w / 2, cy = h / 2;
        int r = Math.round(h * 0.46f);
        ring(g, cx, cy, r, w, h, 0xFF040404);
        for (int i = 1; i <= 3; i++) {                                    // soft vignette at the eyepiece edge
            int rr = r - i * 2;
            int a = 0x60 - i * 0x18;
            for (int y = cy - rr; y <= cy + rr; y++) {
                double dy = y + 0.5 - cy;
                int half = (int) Math.round(Math.sqrt(Math.max(0, rr * (double) rr - dy * dy)));
                int half2 = (int) Math.round(Math.sqrt(Math.max(0, (rr - 2) * (double) (rr - 2) - dy * dy)));
                if (half > half2) {
                    g.fill(cx - half, y, cx - half2, y + 1, (a << 24));
                    g.fill(cx + half2, y, cx + half, y + 1, (a << 24));
                }
            }
        }
        float ppm = h / fovV * 0.0573f;                                     // pixels per milliradian
        int ink = 0xFF0A0A0A;
        int thick = Math.round(10 * ppm);
        g.fill(cx - r, cy - 1, cx - thick, cy + 1, ink);                  // heavy posts
        g.fill(cx + thick, cy - 1, cx + r, cy + 1, ink);
        g.fill(cx - 1, cy + thick, cx + 1, cy + r, ink);
        g.fill(cx - 1, cy - r, cx + 1, cy - thick, ink);
        g.fill(cx - thick, cy, cx + thick, cy + 1, ink);                  // fine cross
        g.fill(cx, cy - thick, cx + 1, cy + thick, ink);
        for (int m = 1; m <= 10; m++) {
            int d = Math.round(m * ppm);
            if (d < 3) continue;
            int t = m % 2 == 0 ? 3 : 2;
            g.fill(cx + d, cy - t, cx + d + 1, cy + t + 1, ink);
            g.fill(cx - d, cy - t, cx - d + 1, cy + t + 1, ink);
            g.fill(cx - t, cy - d, cx + t + 1, cy - d + 1, ink);
            g.fill(cx - t, cy + d, cx + t + 1, cy + d + 1, ink);
            int hd = Math.round((m - 0.5f) * ppm);
            if (ppm > 6) {
                g.fill(cx + hd, cy - 1, cx + hd + 1, cy + 2, ink);
                g.fill(cx - hd, cy - 1, cx - hd + 1, cy + 2, ink);
                g.fill(cx - 1, cy + hd, cx + 2, cy + hd + 1, ink);
            }
            if (m % 2 == 0 && ppm > 4 && m < 10) {
                String s = Integer.toString(m);
                g.pose().pushPose();
                g.pose().translate(cx + d - f.width(s) * 0.3f, cy + 5, 0);
                g.pose().scale(0.6f, 0.6f, 1f);
                g.drawString(f, s, 0, 0, ink, false);
                g.pose().popPose();
                g.pose().pushPose();
                g.pose().translate(cx + 6, cy + d - 2, 0);
                g.pose().scale(0.6f, 0.6f, 1f);
                g.drawString(f, s, 0, 0, ink, false);
                g.pose().popPose();
            }
        }
        for (int m = 2; m <= 8; m += 2) {                                 // holdover tree: wind dots every 1 mil
            int y = cy + Math.round(m * ppm);
            int n = m / 2 + 1;
            for (int j = 1; j <= n; j++) {
                int x = Math.round(j * ppm);
                if (x < 3) continue;
                g.fill(cx + x, y, cx + x + 1, y + 1, ink);
                g.fill(cx - x, y, cx - x + 1, y + 1, ink);
            }
        }
        g.fill(cx, cy, cx + 1, cy + 1, 0xFFE02010);                      // illuminated centre dot
        String z = zoom() + "\u00d7";
        g.drawString(f, z, cx + r - 6 - f.width(z), cy + r - 14, 0xFF909090, false);
    }

    /** PG-7VL drop angle (deg) at a distance, from the same flight model as AtMissileEntity (4.6 m/tick, -0.010 after tick 6) */
    static float rpgDrop(double dist) {
        double x = 0, y = 0, vx = 1.2, vy = 0;
        for (int t = 1; t < 200; t++) {
            double sp = Math.sqrt(vx * vx + vy * vy);
            if (sp < 4.6) sp = Math.min(4.6, sp + 0.8);
            double l = Math.sqrt(vx * vx + vy * vy);
            vx = vx / l * sp;
            vy = vy / l * sp;
            if (t > 6) vy -= 0.010;
            double nx = x + vx, ny = y + vy;
            if (nx >= dist) {
                double fr = (dist - x) / (nx - x);
                double yy = y + (ny - y) * fr;
                return (float) Math.toDegrees(Math.atan2(-yy, dist));
            }
            x = nx;
            y = ny;
        }
        return 0f;
    }

    /**
     * PGO-7V3 reticle (4x, 13 deg field): top "+" = aim point (centre of screen), range chevrons 2..5 (x100 m, PG-7VL column "Л"
     * on the right, "М" on the left), lead ticks 1..5 each side per range row, stadiametric rangefinder for a 2.7 m target 2..10.
     */
    private static void pgo7(GuiGraphics g, Font f, int w, int h, float fovV) {
        int cx = w / 2, cy = h / 2;
        float ppd = h / fovV;
        int r = Math.round(Math.min(h * 0.47f, 6.5f * ppd));
        ring(g, cx, cy, r, w, h, 0xFF050505);
        g.fill(0, 0, w, h, 0x10308040);
        int ink = 0xFF101010, red = 0xFFD02818;
        // aim cross "+"
        g.fill(cx - 5, cy, cx + 6, cy + 1, ink);
        g.fill(cx, cy - 5, cx + 1, cy + 6, ink);
        // range rows: chevrons with lead ticks (1 tick = target at 15 km/h crossing; ~1 mil spacing scaled to 0.15 deg)
        float lead = 0.30f * ppd;
        int[] rng = {200, 300, 400, 500};
        for (int i = 0; i < rng.length; i++) {
            int y = cy + Math.round(rpgDrop(rng[i]) * ppd);
            g.fill(cx - 3, y, cx + 4, y + 1, ink);                       // chevron
            g.fill(cx - 2, y - 1, cx - 1, y, ink);
            g.fill(cx + 1, y - 1, cx + 2, y, ink);
            g.fill(cx, y - 2, cx + 1, y - 1, ink);
            for (int j = 1; j <= 5; j++) {
                int dx = Math.round(j * lead) + 4;
                int tl = j == 5 ? 3 : 2;
                g.fill(cx - dx, y - tl + 1, cx - dx + 1, y + 1, ink);
                g.fill(cx + dx, y - tl + 1, cx + dx + 1, y + 1, ink);
            }
            String lab = String.valueOf(rng[i] / 100);
            g.drawString(f, lab, cx - Math.round(5 * lead) - 8 - f.width(lab), y - 3, ink, false);
            g.drawString(f, lab, cx + Math.round(5 * lead) + 9, y - 3, ink, false);
        }
        int yTop = cy + Math.round(rpgDrop(200) * ppd);
        g.drawString(f, "\u041c", cx - Math.round(5 * lead) - 22, yTop - 14, ink, false);
        g.drawString(f, "\u041b", cx + Math.round(5 * lead) + 16, yTop - 14, ink, false);
        // stadiametric rangefinder: a 2.7 m tall tank between the baseline and the curve
        int yb = cy - Math.round(r * 0.08f);   // upper-left field: clear of the range rows and the aim cross
        int x0 = cx - Math.round(r * 0.86f), x1 = cx - Math.round(r * 0.30f);
        g.fill(x0, yb, x1, yb + 1, ink);
        int[] st = {200, 300, 400, 500, 600, 800, 1000};
        int prevX = -1, prevY = -1;
        for (int i = 0; i < st.length; i++) {
            float hh = (float) Math.toDegrees(Math.atan2(2.7, st[i])) * ppd;
            int x = x0 + Math.round((x1 - x0) * i / (float) (st.length - 1));
            int y = yb - Math.round(hh);
            g.fill(x, y, x + 1, yb, 0x80101010);
            if (prevX >= 0) line(g, prevX, prevY, x, y, ink);
            prevX = x;
            prevY = y;
            String lab = String.valueOf(st[i] / 100);
            if (i % 3 == 0 || i == st.length - 1) g.drawString(f, lab, x - f.width(lab) / 2, yb + 3, ink, false);
        }
        g.drawString(f, "\u0420", cx + Math.round(r * 0.55f), cy + Math.round(r * 0.62f), red, false);
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++) {
            int x = x0 + Math.round((x1 - x0) * i / (float) Math.max(1, n));
            int y = y0 + Math.round((y1 - y0) * i / (float) Math.max(1, n));
            g.fill(x, y, x + 1, y + 1, col);
        }
    }
}
