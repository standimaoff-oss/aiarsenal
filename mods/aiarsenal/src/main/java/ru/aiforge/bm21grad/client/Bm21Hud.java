package ru.aiforge.bm21grad.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import ru.aiforge.bm21grad.ammo.RocketType;
import ru.aiforge.bm21grad.ballistics.Ballistics;
import ru.aiforge.bm21grad.entity.Bm21Entity;

/** Crew HUD shown while riding a BM-21: status panel, gunner sight, and the control hints bar at the bottom. */
public final class Bm21Hud {
    private Bm21Hud() {
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof Bm21Entity v)) return;
        Font f = mc.font;
        boolean gunner = v.isGunnerMode() && !v.isDestroyed();
        statusPanel(g, f, v, gunner);
        if (gunner) sight(g, f, v, w, h);
        int barH = helpBar(g, f, mc, gunner, w, h);
        // push hearts/food (survival) above the hints bar
        gui.leftHeight = Math.max(gui.leftHeight, barH + 4);
        gui.rightHeight = Math.max(gui.rightHeight, barH + 4);
    }

    private static void statusPanel(GuiGraphics g, Font f, Bm21Entity v, boolean gunner) {
        int x = 6, y = 6, lh = 11;
        g.fill(x - 3, y - 3, x + 236, y + lh * 7 + 2, 0x90000000);
        g.drawString(f, Component.translatable("hud.aiarsenal.title").withStyle(ChatFormatting.GOLD), x, y, 0xFFFFFF);
        Component mode = Component.translatable(gunner ? "hud.aiarsenal.mode_gunner" : "hud.aiarsenal.mode_drive");
        g.drawString(f, mode, x + 84, y, gunner ? 0xFF7050 : 0x70D0FF);
        int hp = Math.round(v.getHealth());
        g.drawString(f, "HP " + hp, x + 196, y, hp > 150 ? 0x55FF55 : hp > 60 ? 0xFFFF55 : 0xFF5555);
        y += lh;
        RocketType ft = v.fireType();
        g.drawString(f, Component.translatable("hud.aiarsenal.tubes", v.loadedCount(), Bm21Entity.TUBES,
                Component.translatable(ft.translationKey()), v.rackTotal()), x, y, 0xE0E0E0);
        y += lh;
        int phase = v.getMissionPhase();
        Component ph = Component.translatable("hud.aiarsenal.phase." + phase);
        g.drawString(f, Component.translatable("hud.aiarsenal.mode", v.getFireMode(), ph), x, y, phase == Bm21Entity.PH_FIRING ? 0xFF6040 : 0xE0E0E0);
        y += lh;
        g.drawString(f, Component.translatable("hud.aiarsenal.laying", String.format("%.1f", v.getTurret()), String.format("%.1f", v.getElevation()),
                Component.translatable(v.hasState(Bm21Entity.ST_SHIELDS) ? "hud.aiarsenal.combat" : "hud.aiarsenal.travel")), x, y, 0xE0E0E0);
        y += lh;
        if (v.getTarget().isPresent()) {
            var t = v.getTarget().get();
            double d = v.horizontalDistanceTo(t);
            String az = Ballistics.mils(Ballistics.yawTo(v.launcherPivot(), t.getX() + 0.5, t.getZ() + 0.5));
            g.drawString(f, Component.translatable("hud.aiarsenal.target", t.getX(), t.getY(), t.getZ(), (int) d, az), x, y, 0x80FFFF);
        } else g.drawString(f, Component.translatable("hud.aiarsenal.bm21_no_target"), x, y, 0x909090);
        y += lh;
        if (v.hasState(Bm21Entity.ST_RELOADING)) {
            int tube = v.getLoadingTube();
            float p = v.getLoadingProgress();
            g.drawString(f, Component.translatable("hud.aiarsenal.reloading", tube + 1), x, y, 0xFFD060);
            g.fill(x + 120, y + 2, x + 120 + 100, y + 8, 0xFF303030);
            g.fill(x + 120, y + 2, x + 120 + (int) (100 * p), y + 8, 0xFFFFC040);
        } else {
            float sp = Math.abs(v.displaySpeed()) * 20f * 3.6f;
            g.drawString(f, Component.translatable("hud.aiarsenal.bm21_speed", Math.round(sp)), x, y, 0xC0C0C0);
        }
        y += lh;
        g.drawString(f, Component.translatable("hud.aiarsenal.help_hint"), x, y, 0x808080);
    }

    /** Panoramic-sight style reticle with a mil scale plus the fire-control readout for the point under it. */
    private static void sight(GuiGraphics g, Font f, Bm21Entity v, int w, int h) {
        int cx = w / 2, cy = h / 2;
        int col = GunnerControl.blockReason == null && GunnerControl.aimPoint != null ? 0xFF40FF40 : 0xFFFF5040;
        int gap = 5, len = 26;
        g.fill(cx - gap - len, cy, cx - gap, cy + 1, col);
        g.fill(cx + gap + 1, cy, cx + gap + 1 + len, cy + 1, col);
        g.fill(cx, cy + gap + 1, cx + 1, cy + gap + 1 + len, col);
        g.fill(cx, cy - gap - 10, cx + 1, cy - gap, col);
        for (int i = 1; i <= 4; i++) { // mil ticks every 10 px
            int o = gap + i * 6;
            g.fill(cx - o, cy - 2, cx - o + 1, cy + 3, col);
            g.fill(cx + o, cy - 2, cx + o + 1, cy + 3, col);
            g.fill(cx - 2, cy + o, cx + 3, cy + o + 1, col);
        }
        g.fill(cx, cy, cx + 1, cy + 1, 0xFFFFFFFF);
        // readout under the reticle
        int x = 6, y = 6 + 11 * 7 + 8, lh = 10;
        g.fill(x - 3, y - 3, x + 200, y + lh * 5 + 1, 0x80000000);
        g.drawString(f, Component.translatable(GunnerControl.direct ? "hud.aiarsenal.g_direct" : "hud.aiarsenal.g_indirect"), x, y, 0xFFE0A0);
        y += lh;
        var piv = v.launcherPivot();
        if (GunnerControl.aimPoint != null) {
            String az = Ballistics.mils(Ballistics.yawTo(piv, GunnerControl.aimPoint.x, GunnerControl.aimPoint.z));
            g.drawString(f, Component.translatable("hud.aiarsenal.g_range", (int) GunnerControl.range, az,
                    String.format("%.1f", GunnerControl.cmdElev)), x, y, 0xE0E0E0);
            y += lh;
            var s = GunnerControl.solution;
            String tof = s != null && s.ok() ? String.format("%.1f", s.flightTicks() / 20.0) : "—";
            g.drawString(f, Component.translatable("hud.aiarsenal.g_tof", tof, String.format("%.1f", v.getTurret()),
                    String.format("%.1f", v.getElevation())), x, y, 0xC0C0C0);
        } else {
            g.drawString(f, Component.translatable("msg.aiarsenal.no_aim"), x, y, 0xFF6060);
            y += lh;
        }
        y += lh;
        if (GunnerControl.blockReason != null) {
            var rl = f.split(Component.translatable("msg.aiarsenal.reason." + GunnerControl.blockReason), 194);
            for (int i = 0; i < rl.size(); i++) {
                if (i > 0) {
                    y += lh;
                    g.fill(x - 3, y - 1, x + 200, y + lh - 1, 0x80000000);
                }
                g.drawString(f, rl.get(i), x, y, 0xFF6050);
            }
        } else {
            boolean laid = Math.abs(Mth.wrapDegrees(GunnerControl.cmdTurret - v.getTurret())) < 0.5f
                    && Math.abs(GunnerControl.cmdElev - v.getElevation()) < 0.4f && v.shieldProgress >= 1f;
            g.drawString(f, Component.translatable(laid ? "hud.aiarsenal.g_laid" : "hud.aiarsenal.g_laying"), x, y, laid ? 0x60FF60 : 0xFFD060);
        }
        y += lh;
        if (GunnerControl.zoom) g.drawString(f, Component.translatable("hud.aiarsenal.g_zoom"), x, y, 0xA0D0FF);
    }

    private static Component k(KeyMapping km) {
        return km.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.YELLOW);
    }

    /** Two lines of control hints just above the hotbar, depending on the crew mode. */
    private static int helpBar(GuiGraphics g, Font f, Minecraft mc, boolean gunner, int w, int h) {
        var o = mc.options;
        Component l1, l2;
        if (gunner) {
            l1 = Component.translatable("help.aiarsenal.gun1");
            l2 = Component.translatable("help.aiarsenal.gun2", k(ClientSetup.KEY_FIRE), k(ClientSetup.KEY_FCS),
                    k(ClientSetup.KEY_RELOAD), k(ClientSetup.KEY_MODE));
        } else {
            l1 = Component.translatable("help.aiarsenal.drive1", k(o.keyUp), k(o.keyDown), k(o.keyLeft), k(o.keyRight),
                    k(o.keyShift), k(ClientSetup.KEY_LIGHTS), k(ClientSetup.KEY_CAMERA));
            l2 = Component.translatable("help.aiarsenal.drive2", k(ClientSetup.KEY_MODE), k(ClientSetup.KEY_FCS),
                    k(ClientSetup.KEY_SHIELDS), k(ClientSetup.KEY_RELOAD), k(ClientSetup.KEY_FIRE));
        }
        int maxW = w - 12;
        var lines = new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        lines.addAll(f.split(l1, maxW));
        int firstCount = lines.size();
        lines.addAll(f.split(l2, maxW));
        int bw = 0;
        for (var l : lines) bw = Math.max(bw, f.width(l));
        bw += 10;
        int y = h - 2 - lines.size() * 10;
        g.fill(w / 2 - bw / 2, y - 3, w / 2 + bw / 2, y + lines.size() * 10 + 1, 0x90000000);
        for (int i = 0; i < lines.size(); i++) {
            var l = lines.get(i);
            g.drawString(f, l, w / 2 - f.width(l) / 2, y + i * 10, i < firstCount ? (gunner ? 0xFFB0A0 : 0xB0E0FF) : 0xE0E0E0);
        }
        return lines.size() * 10 + 3;
    }
}
