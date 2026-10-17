package ru.aiforge.realwar.client.craft;

import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import ru.aiforge.realwar.craft.BoatEntity;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.craft.CraftKind;
import ru.aiforge.realwar.craft.CraftWeapon;
import ru.aiforge.realwar.craft.HeliEntity;
import ru.aiforge.realwar.craft.PlaneEntity;

/** Helicopter HUD (horizon, speed / altitude / vertical speed, rotor, warnings), boat & submarine panels, hints. */
public final class CraftHud {
    /** r15: scale of the side panels (War Thunder sized text) */
    private static final float PANEL = 0.66f;
    private static final int GREEN = 0xFF40FF60, GREEN_D = 0x9040FF60, RED = 0xFFFF4040, AMBER = 0xFFFFC020, WHITE = 0xFFE0E0E0;

    private CraftHud() {}

    private static String tr(String k, Object... a) {
        return Component.translatable("hud.aiarsenal.craft." + k, a).getString();
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || !(p.getVehicle() instanceof CraftEntity c)) return;
        Font f = mc.font;
        int seat = c.seatOf(p);
        // hints wrap to the screen width and stack up from the hotbar; every side panel is laid out above them
        // r15: compact War Thunder style - hints collapsed behind [J] (open for 8 s after boarding), weapons as a slot bar
        long now = mc.level.getGameTime();
        if (c.getId() != boardedId) {
            boardedId = c.getId();
            boardedAt = now;
        }
        bot = h - 8;
        if (c instanceof HeliEntity heli) {
            if (seat == 0) heliPilot(g, f, heli, w, h, pt);
            else heliGunner(g, f, heli, w, h);
        } else if (c instanceof PlaneEntity pl) {
            if (seat == 0) planePilot(g, f, pl, w, h, pt);
        } else if (c instanceof BoatEntity b) {
            boat(g, f, b, seat, w, h);
            if (b instanceof ru.aiforge.realwar.craft.BattleshipEntity bs && seat == 0) BattleshipHud.render(g, f, bs, w, h, bot);
        }
        int slotTop = weapons(g, f, c, seat, w, h, ru.aiforge.realwar.client.ActionBar.craft(mc, c, seat));
        ru.aiforge.realwar.client.DamageHud.craftPanel(g, f, c, w, h);
        float php = p.getHealth();
        ru.aiforge.realwar.client.WtHud.small(g, f, "\u2665 " + Math.round(php), w / 2f, h - 4 - 7, php < 6 ? 0xFFFF5A5A : 0xFFFF8080, true);
    }

    private static int boardedId = -1;
    private static long boardedAt;

    /** Bottom edge available to the side panels (above the hint block). */
    private static int bot;

    // --------------------------------------------------------------------------------------- helicopter

    private static void heliPilot(GuiGraphics g, Font f, HeliEntity c, int w, int h, float pt) {
        int cx = w / 2, cy = h / 2;
        float pitch = c.pitchLerp(pt), roll = c.rollLerp(pt);
        double ppd = h / 70.0;                         // pixels per degree (~70 deg vertical FOV)
        boolean first = Minecraft.getInstance().options.getCameraType().isFirstPerson();
        // boresight
        g.fill(cx - 14, cy, cx - 5, cy + 1, GREEN);
        g.fill(cx + 5, cy, cx + 14, cy + 1, GREEN);
        g.fill(cx, cy - 9, cx + 1, cy - 4, GREEN);
        // pitch ladder + horizon, rolled
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(first ? -roll : -roll));
        for (int deg = -30; deg <= 30; deg += 10) {
            int y = (int) ((pitch - deg) * ppd);
            if (Math.abs(y) > h / 2 - 30) continue;
            int half = deg == 0 ? 90 : 34;
            int gap = deg == 0 ? 24 : 16;
            int col = deg == 0 ? GREEN : GREEN_D;
            g.fill(-half, y, -gap, y + 1, col);
            g.fill(gap, y, half, y + 1, col);
            if (deg != 0) {
                g.fill(-half, y, -half + 1, y + (deg > 0 ? 4 : -4), col);
                g.fill(half - 1, y, half, y + (deg > 0 ? 4 : -4), col);
                g.drawString(f, String.valueOf(Math.abs(deg)), half + 3, y - 3, col, false);
            }
        }
        g.pose().popPose();
        // bank scale
        g.drawCenteredString(f, String.format("%+.0f°", roll), cx, cy + 42, Math.abs(roll) > 45 ? AMBER : GREEN_D);
        // speed (left), altitude (right)
        double vh = Math.sqrt(c.vel.x * c.vel.x + c.vel.z * c.vel.z);
        int lx = cx - 150, rx = cx + 110;
        box(g, f, lx, cy - 6, String.format("%3.0f", vh * 3.6), GREEN);
        g.drawString(f, tr("kmh"), lx, cy + 8, GREEN_D, false);
        box(g, f, rx, cy - 6, String.format("%4.0f", c.agl), c.agl < 15 && c.vel.y < -5 ? RED : GREEN);
        g.drawString(f, tr("agl"), rx, cy + 8, GREEN_D, false);
        g.drawString(f, tr("asl", (int) c.getY()), rx, cy + 20, GREEN_D, false);
        int vsCol = c.vel.y < -8 ? AMBER : GREEN;
        g.drawString(f, String.format("Vy %+.1f", c.vel.y), rx, cy - 22, vsCol, false);
        // vertical speed bar
        int vsBar = (int) Mth.clamp(c.vel.y * 4, -50, 50);
        g.fill(rx + 46, cy, rx + 49, cy - vsBar, vsCol);
        g.fill(rx + 44, cy, rx + 51, cy + 1, GREEN_D);
        // heading tape
        float hdg = Mth.wrapDegrees(c.getYRot() + 180f);
        if (hdg < 0) hdg += 360;
        g.drawCenteredString(f, String.format("%03.0f°", hdg), cx, 12, GREEN);
        for (int d = -40; d <= 40; d += 10) {
            float mark = (float) (Math.floor(hdg / 10f) * 10 + d);
            int x = (int) (cx + (mark - hdg) * 3);
            if (Math.abs(x - cx) > 125) continue;
            g.fill(x, 24, x + 1, 28, GREEN_D);
            int m = Math.floorMod((int) mark, 360);
            String lbl = m == 0 ? "N" : m == 90 ? "E" : m == 180 ? "S" : m == 270 ? "W" : (m % 30 == 0 ? String.valueOf(m / 10) : null);
            if (lbl != null) g.drawCenteredString(f, lbl, x, 30, GREEN_D);
        }
        // collective + rotor + engine (left bottom)
        // r15: systems panel at War Thunder scale, anchored bottom-left (clear of the speed / altitude tapes)
        g.pose().pushPose();
        g.pose().translate(6, bot - 84 * PANEL, 0);
        g.pose().scale(PANEL, PANEL, 1f);
        int bx = 0, by = 0;
        bar(g, f, bx, by, tr("collective"), c.aux2, GREEN);
        bar(g, f, bx, by + 12, tr("rotor"), Math.min(1f, c.aux), c.aux < 0.88f && c.agl > 2 ? RED : GREEN);
        bar(g, f, bx, by + 24, tr("fuel"), c.fuel(), c.fuel() < 0.15f ? AMBER : GREEN);
        bar(g, f, bx, by + 36, tr("hull"), c.hp() / c.kind.hp, c.hp() < c.kind.hp * 0.3f ? RED : GREEN);
        g.drawString(f, tr(c.engineOn() ? "engine_on" : "engine_off"), bx, by + 50, c.engineOn() ? GREEN : AMBER, true);
        g.drawString(f, tr(c.flag(CraftEntity.FLAG_STAB) ? "mode_stab" : "mode_manual"), bx, by + 62, c.flag(CraftEntity.FLAG_STAB) ? 0xFF60C0FF : GREEN, true);
        if (c.kind == CraftKind.KA52) g.drawString(f, tr(c.flag(CraftEntity.FLAG_GEAR) ? "gear_down" : "gear_up"), bx, by + 74, GREEN_D, true);
        g.drawString(f, String.format("G %.1f", c.gForce), bx + 70, by + 74, Math.abs(c.gForce) > 2.5f ? AMBER : GREEN_D, true);
        // stick indicator (mouse cyclic)
        int sx = 16, sy = by - 24;   // stick indicator sits above the left panel
        g.fill(sx - 16, sy - 16, sx + 16, sy + 16, 0x50000000);
        g.fill(sx - 16, sy, sx + 16, sy + 1, 0x60FFFFFF);
        g.fill(sx, sy - 16, sx + 1, sy + 16, 0x60FFFFFF);
        int kx = (int) Mth.clamp(c.rateP / c.kind.heli.rollRate() * 15, -15, 15), ky = (int) Mth.clamp(-c.rateQ / c.kind.heli.pitchRate() * 15, -15, 15);
        g.fill(sx + kx - 2, sy + ky - 2, sx + kx + 2, sy + ky + 2, GREEN);
        g.pose().popPose();
        // warnings
        int wy = cy - 70;
        boolean blink = (c.tickCount / 5) % 2 == 0;
        if (CraftClient.missileWarn > 0 && blink) { g.drawCenteredString(f, tr("warn_missile"), cx, wy, RED); wy -= 11; }
        if (c.warnVrs > 0.2f) { g.drawCenteredString(f, tr("warn_vrs"), cx, wy, blink ? RED : AMBER); wy -= 11; }
        if (c.warnStall > 0.05f) { g.drawCenteredString(f, tr("warn_stall"), cx, wy, blink ? RED : AMBER); wy -= 11; }
        if (c.aux < 0.88f && c.agl > 3) { g.drawCenteredString(f, tr("warn_rotor"), cx, wy, RED); wy -= 11; }
        if (Math.abs(roll) > 60) { g.drawCenteredString(f, tr("warn_bank"), cx, wy, AMBER); wy -= 11; }
        if (c.agl < 20 && c.vel.y < -8 && blink) { g.drawCenteredString(f, tr("warn_terrain"), cx, wy, RED); wy -= 11; }
        if (c.fuel() < 0.1f) { g.drawCenteredString(f, tr("warn_fuel"), cx, wy, AMBER); wy -= 11; }
        if (!c.engineOn() && c.aux > 0.3f && c.agl > 3) { g.drawCenteredString(f, tr("warn_autorot"), cx, wy, AMBER); }
    }

    private static void heliGunner(GuiGraphics g, Font f, HeliEntity c, int w, int h) {
        int cx = w / 2, cy = h / 2;
        int r = 22;
        g.fill(cx - r, cy, cx - 6, cy + 1, GREEN);
        g.fill(cx + 6, cy, cx + r, cy + 1, GREEN);
        g.fill(cx, cy - r, cx + 1, cy - 6, GREEN);
        g.fill(cx, cy + 6, cx + 1, cy + r, GREEN);
        Entity op = Minecraft.getInstance().player;
        double rng = CraftClient.laserRange(op, 1500);
        g.drawCenteredString(f, rng < 0 ? tr("range_none") : tr("range", (int) rng), cx, cy + r + 6, GREEN);
        double vh = Math.sqrt(c.vel.x * c.vel.x + c.vel.z * c.vel.z);
        g.drawString(f, String.format("%3.0f %s   %4.0f %s", vh * 3.6, tr("kmh"), c.agl, tr("agl")), 8, bot - 12, GREEN_D, true);
        if (CraftClient.zoom) g.drawCenteredString(f, tr("zoom", 4), cx, cy - r - 12, GREEN_D);
        if (CraftClient.missileWarn > 0 && (c.tickCount / 5) % 2 == 0) g.drawCenteredString(f, tr("warn_missile"), cx, cy - 70, RED);
    }

    // --------------------------------------------------------------------------------------- aeroplanes

    private static void planePilot(GuiGraphics g, Font f, PlaneEntity c, int w, int h, float pt) {
        int cx = w / 2, cy = h / 2;
        float pitch = c.pitchLerp(pt), roll = c.rollLerp(pt);
        double ppd = h / 70.0;
        boolean first = Minecraft.getInstance().options.getCameraType().isFirstPerson();
        CraftKind.Plane p = c.spec();
        // flight path marker (velocity vector) + pitch ladder
        if (first && c.vel.length() > 8) {
            int fy = (int) (c.aoa * ppd), fx = (int) (-c.slip * ppd);
            int mx = cx + fx, my = cy + fy;
            g.fill(mx - 4, my - 4, mx + 4, my - 3, GREEN);
            g.fill(mx - 4, my + 3, mx + 4, my + 4, GREEN);
            g.fill(mx - 4, my - 4, mx - 3, my + 4, GREEN);
            g.fill(mx + 3, my - 4, mx + 4, my + 4, GREEN);
            g.fill(mx - 11, my, mx - 4, my + 1, GREEN);
            g.fill(mx + 4, my, mx + 11, my + 1, GREEN);
            g.fill(mx, my - 9, mx + 1, my - 4, GREEN);
        }
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-roll));
        for (int deg = -60; deg <= 60; deg += 10) {
            int y = (int) ((pitch - deg) * ppd);
            if (Math.abs(y) > h / 2 - 40) continue;
            int half = deg == 0 ? 110 : 36, gap = deg == 0 ? 26 : 18;
            int col = deg == 0 ? GREEN : GREEN_D;
            if (deg < 0) {
                for (int x = gap; x < half; x += 6) {
                    g.fill(-x - 3, y, -x, y + 1, col);
                    g.fill(x, y, x + 3, y + 1, col);
                }
            } else {
                g.fill(-half, y, -gap, y + 1, col);
                g.fill(gap, y, half, y + 1, col);
            }
            if (deg != 0) g.drawString(f, String.valueOf(Math.abs(deg)), half + 3, y - 3, col, false);
        }
        g.pose().popPose();
        // speed (left), altitude (right), with tapes
        double V = c.vel.length();
        int lx = cx - Math.min(160, (int) (w * 0.30)), rx = cx + Math.min(120, (int) (w * 0.22));
        boolean slow = V < c.vStall * 1.15 && !c.contactOrGround();
        box(g, f, lx, cy - 6, String.format("%3.0f", V * 3.6), slow ? RED : GREEN);
        g.drawString(f, tr("kmh"), lx, cy + 8, GREEN_D, false);
        g.drawString(f, tr("vstall", (int) (c.vStall * 3.6)), lx, cy + 20, GREEN_D, false);
        for (int k = -3; k <= 3; k++) {
            int y = cy + k * 14 - (int) ((V * 3.6) % 20 * 0.7);
            if (Math.abs(y - cy) < 9) continue;
            g.fill(lx + 28, y, lx + 33, y + 1, GREEN_D);
        }
        box(g, f, rx, cy - 6, String.format("%4.0f", c.agl), c.agl < 30 && c.vel.y < -6 ? RED : GREEN);
        g.drawString(f, tr("agl"), rx, cy + 8, GREEN_D, false);
        g.drawString(f, tr("asl", (int) c.getY()), rx, cy + 20, GREEN_D, false);
        int vsCol = c.vel.y < -12 ? AMBER : GREEN;
        g.drawString(f, String.format("Vy %+.1f", c.vel.y), rx, cy - 22, vsCol, false);
        int vsBar = (int) Mth.clamp(c.vel.y * 2.5, -50, 50);
        g.fill(rx + 46, cy, rx + 49, cy - vsBar, vsCol);
        g.fill(rx + 44, cy, rx + 51, cy + 1, GREEN_D);
        // heading tape
        float hdg = Mth.wrapDegrees(c.getYRot() + 180f);
        if (hdg < 0) hdg += 360;
        g.drawCenteredString(f, String.format("%03.0f°", hdg), cx, 12, GREEN);
        for (int d = -40; d <= 40; d += 10) {
            float mark = (float) (Math.floor(hdg / 10f) * 10 + d);
            int x = (int) (cx + (mark - hdg) * 3);
            if (Math.abs(x - cx) > 125) continue;
            g.fill(x, 24, x + 1, 28, GREEN_D);
            int m = Math.floorMod((int) mark, 360);
            String lbl = m == 0 ? "N" : m == 90 ? "E" : m == 180 ? "S" : m == 270 ? "W" : (m % 30 == 0 ? String.valueOf(m / 10) : null);
            if (lbl != null) g.drawCenteredString(f, lbl, x, 30, GREEN_D);
        }
        // slip ball
        int sbx = cx, sby = cy + 58;
        g.fill(sbx - 22, sby, sbx + 22, sby + 6, 0x50000000);
        g.fill(sbx - 4, sby, sbx - 3, sby + 6, 0x80FFFFFF);
        g.fill(sbx + 3, sby, sbx + 4, sby + 6, 0x80FFFFFF);
        int ball = (int) Mth.clamp(c.slip * 1.5f, -19, 19);
        g.fill(sbx + ball - 2, sby + 1, sbx + ball + 2, sby + 5, WHITE);
        // engine / systems panel
        g.pose().pushPose();
        g.pose().translate(6, bot - 98 * PANEL, 0);
        g.pose().scale(PANEL, PANEL, 1f);
        int bx = 0, by = 0;
        bar(g, f, bx, by, tr("throttle_p"), c.throttle(), GREEN);
        bar(g, f, bx, by + 12, tr("rpm"), Math.min(1f, c.rpm()), c.engineOn() ? GREEN : AMBER);
        bar(g, f, bx, by + 24, tr("fuel"), c.fuel(), c.fuel() < 0.15f ? AMBER : GREEN);
        bar(g, f, bx, by + 36, tr("hull"), c.hp() / c.kind.hp, c.hp() < c.kind.hp * 0.3f ? RED : GREEN);
        g.drawString(f, tr(c.engineOn() ? (p.engines() > 1 ? "engine_on" : "engine1_on") : (p.engines() > 1 ? "engine_off" : "engine1_off")), bx, by + 50, c.engineOn() ? GREEN : AMBER, true);
        float gp = c.gearPos;
        String gear = gp > 0.99f ? tr("gear_down") : gp < 0.01f ? tr("gear_up") : tr("gear_moving");
        g.drawString(f, gear, bx, by + 62, gp > 0.99f ? GREEN : gp < 0.01f ? GREEN_D : AMBER, true);
        if (p.jet()) {
            g.drawString(f, tr(c.flapsDown() ? "mnv_on" : "mnv_off"), bx + 80, by + 62, c.flapsDown() ? 0xFFFF6040 : GREEN_D, true);
            if (c.abVis > 0.3f) g.drawString(f, tr("afterburner"), bx + 80, by + 50, 0xFFFFA030, true);
            g.drawString(f, String.format("M %.2f", c.vel.length() / 340.0), bx + 140, by + 74, GREEN_D, true);
        } else
        g.drawString(f, tr(c.flapPos > 0.5f ? "flaps_down" : "flaps_up"), bx + 80, by + 62, c.flapPos > 0.5f ? 0xFF60C0FF : GREEN_D, true);
        g.drawString(f, String.format("G %.1f   AoA %.0f°", c.gForce, c.aoa), bx, by + 74, Math.abs(c.gForce) > p.maxG() * 0.85f ? AMBER : GREEN_D, true);
        if (p.turretCount() > 0) {
            int busy = 0;
            for (int i = 0; i < p.turretCount(); i++) if (Math.abs(Mth.wrapDegrees(c.turAz[i] - p.turrets()[i][3])) > 3f || Math.abs(c.turEl[i]) > 3f) busy++;
            g.drawString(f, tr("gunners", busy, p.turretCount()), bx, by + 86, busy > 0 ? AMBER : GREEN_D, true);
            if (c.bayPos > 0.02f) g.drawString(f, tr(c.bayPos > 0.85f ? "bay_open" : "bay_moving"), bx + 100, by + 86, c.bayPos > 0.85f ? GREEN : AMBER, true);
        }
        // stick indicator
        int sx = 16, sy = by - 24;
        g.fill(sx - 16, sy - 16, sx + 16, sy + 16, 0x50000000);
        g.fill(sx - 16, sy, sx + 16, sy + 1, 0x60FFFFFF);
        g.fill(sx, sy - 16, sx + 1, sy + 16, 0x60FFFFFF);
        int kx = (int) Mth.clamp(c.rateP / p.rollRate() * 15, -15, 15), ky = (int) Mth.clamp(-c.rateQ / p.pitchRate() * 15, -15, 15);
        g.fill(sx + kx - 2, sy + ky - 2, sx + kx + 2, sy + ky + 2, GREEN);
        g.pose().popPose();
        // warnings
        int wy = cy - 70;
        boolean blink = (c.tickCount / 5) % 2 == 0;
        if (CraftClient.missileWarn > 0 && blink) { g.drawCenteredString(f, tr("warn_missile"), cx, wy, RED); wy -= 11; }
        if (c.warnStall > 0.05f && !c.contactOrGround()) { g.drawCenteredString(f, tr("warn_stall_plane"), cx, wy, blink ? RED : AMBER); wy -= 11; }
        if (c.gForce > p.maxG() * 0.9f) { g.drawCenteredString(f, tr("warn_g"), cx, wy, AMBER); wy -= 11; }
        if (c.agl < 40 && c.vel.y < -10 && blink) { g.drawCenteredString(f, tr("warn_terrain"), cx, wy, RED); wy -= 11; }
        if (c.agl < 25 && c.gearPos < 0.99f && V < c.vStall * 1.6 && c.vel.y < -1 && blink) { g.drawCenteredString(f, tr("warn_gear"), cx, wy, RED); wy -= 11; }
        if (c.fuel() < 0.1f) { g.drawCenteredString(f, tr("warn_fuel"), cx, wy, AMBER); wy -= 11; }
        if (V > p.vmax() * 1.45f) { g.drawCenteredString(f, tr("warn_vne"), cx, wy, RED); }
    }

    // --------------------------------------------------------------------------------------- boats

    private static void boat(GuiGraphics g, Font f, BoatEntity b, int seat, int w, int h) {
        int cx = w / 2, cy = h / 2;
        boolean sub = b.kind.isSub();
        if (seat != 0 || CraftClient.zoom) {
            g.fill(cx - 12, cy, cx - 4, cy + 1, GREEN);
            g.fill(cx + 4, cy, cx + 12, cy + 1, GREEN);
            g.fill(cx, cy + 4, cx + 1, cy + 12, GREEN);
        }
        double sp = Math.sqrt(b.vel.x * b.vel.x + b.vel.z * b.vel.z);
        int bx = 8, by = bot - 84;
        g.drawString(f, tr("knots", String.format("%.1f", sp * 1.944), String.format("%.0f", sp * 3.6)), bx, by, GREEN, true);
        float hdg = Mth.wrapDegrees(b.getYRot() + 180f);
        if (hdg < 0) hdg += 360;
        g.drawString(f, tr("heading", String.format("%03.0f", hdg)), bx, by + 12, GREEN_D, true);
        float lever = b.throttle();
        String tel = lever < -0.05f ? tr("tel_astern") : lever < 0.05f ? tr("tel_stop") : lever < 0.4f ? tr("tel_slow") : lever < 0.75f ? tr("tel_half") : tr("tel_full");
        bar(g, f, bx, by + 24, tr("throttle"), Math.abs(lever), lever < 0 ? AMBER : GREEN);
        g.drawString(f, tel, bx + 148, by + 24, WHITE, true);
        bar(g, f, bx, by + 36, tr("fuel"), b.fuel(), b.fuel() < 0.15f ? AMBER : GREEN);
        bar(g, f, bx, by + 48, tr("hull"), b.hp() / b.kind.hp, b.hp() < b.kind.hp * 0.3f ? RED : GREEN);
        g.drawString(f, tr(b.engineOn() ? "engine_on" : "engine_off"), bx, by + 62, b.engineOn() ? GREEN : AMBER, true);
        // rudder indicator
        int rx = bx + 60, ry = by + 76;
        g.fill(rx - 30, ry, rx + 30, ry + 2, 0x80FFFFFF);
        int k = (int) (b.rudderVis * 28);
        g.fill(rx + k - 1, ry - 3, rx + k + 2, ry + 5, GREEN);
        g.drawString(f, tr("rudder"), bx, ry - 2, GREEN_D, true);
        if (sub) {
            int sx = w - 150, sy = 40;
            g.drawString(f, tr("depth", String.format("%.0f", b.depth)), sx, sy, b.depth > 200 ? AMBER : 0xFF60C0FF, true);
            bar(g, f, sx, sy + 12, tr("ballast"), Math.min(1f, b.ballast()), 0xFF60C0FF);
            bar(g, f, sx, sy + 24, tr("battery"), b.battery(), b.battery() < 0.15f ? RED : GREEN);
            g.drawString(f, tr(b.subFrac > 0.97f ? "submerged" : "surfaced"), sx, sy + 38, WHITE, true);
            if (b.flag(CraftEntity.FLAG_STAB)) g.drawString(f, tr("depth_hold"), sx, sy + 50, 0xFF60C0FF, true);
            if (b.flag(CraftEntity.FLAG_PERISCOPE)) g.drawString(f, tr("periscope"), sx, sy + 62, GREEN, true);
            if (b.subFrac > 0.97f && b.battery() <= 0.001f) g.drawCenteredString(f, tr("warn_battery"), cx, cy - 60, RED);
        } else if (b.kind != CraftKind.BISMARCK && sp > b.kind.water.vHump() * 1.1) {
            g.drawString(f, tr("planing"), bx + 148, by + 12, 0xFF60C0FF, true);
        }
        if (CraftClient.missileWarn > 0 && (b.tickCount / 5) % 2 == 0) g.drawCenteredString(f, tr("warn_missile"), cx, cy - 70, RED);
    }

    // --------------------------------------------------------------------------------------- weapons / hints

    /** r15: usable weapons as War Thunder slots (number = order, icon by type, rounds left); returns the bar top. */
    private static int weapons(GuiGraphics g, Font f, CraftEntity c, int seat, int w, int h, java.util.List<ru.aiforge.realwar.client.ActionBar.Chip> acts) {
        int sel = c.weaponFor(seat);
        List<ru.aiforge.realwar.client.WtHud.Slot> sl = new ArrayList<>();
        String info = "";
        for (int i = 0; i < c.kind.loads.length; i++) {
            CraftKind.Load l = c.kind.loads[i];
            if (!(l.weapon() == CraftWeapon.FLARES || c.canUse(seat, i))) continue;
            int icon = switch (l.weapon().type) {
                case MG, FLAK -> 2;
                case CANNON, NAVAL, BOMB -> 0;
                case FLARE -> 1;
                default -> 3;
            };
            String name = Component.translatable(l.weapon().langKey()).getString();
            sl.add(new ru.aiforge.realwar.client.WtHud.Slot(icon, c.ammo(i), l.full(), i == sel, 0f, name));
            if (i == sel) info = name + "  " + c.ammo(i) + "/" + l.full();
        }
        // r20 (msg 20): the special actions (hover, gear, flares...) are square tiles in the same row
        return ru.aiforge.realwar.client.WtHud.drawSlots(g, f, sl, info, WHITE, w, h - 8, acts, Math.max(140, w - 2 * 150));
    }

    private static List<FormattedCharSequence> hintLines(Font f, CraftEntity c, int seat, int w) {
        String l1, l2;
        if (c instanceof HeliEntity) {
            if (seat == 0) {
                l1 = tr(c.flag(CraftEntity.FLAG_STAB) ? "hint_heli1_stab" : "hint_heli1");
                l2 = tr(c.kind == CraftKind.KA52 ? "hint_heli2_ka" : "hint_heli2");
            } else {                       // r9: the pilot runs every weapon, other seats ride along
                l1 = tr("hint_crew");
                l2 = "";
            }
        } else if (c instanceof PlaneEntity) {
            if (seat == 0) {
                l1 = tr("hint_plane1");
                l2 = tr(c.kind.plane.jet() ? "hint_plane2_jet" : c.kind.plane.turretCount() > 0 ? "hint_plane2_bomber" : "hint_plane2");
            } else {
                l1 = tr("hint_crew_plane");
                l2 = "";
            }
        } else if (c.kind == CraftKind.BISMARCK) {
            l1 = tr(seat == 0 ? "hint_bs1" : "hint_crew");
            l2 = seat == 0 ? tr("hint_bs2") : "";
        } else if (c.kind.isSub()) {
            l1 = tr(seat == 0 ? "hint_sub1" : "hint_crew");
            l2 = tr("hint_sub2");
        } else {
            l1 = tr(seat == 0 ? "hint_boat1" : "hint_crew");
            l2 = seat == 0 ? tr("hint_boat2") : "";
        }
        List<FormattedCharSequence> out = new ArrayList<>();
        int maxW = Math.max(120, w - 16);
        for (String s : new String[]{l1, l2}) if (!s.isEmpty()) out.addAll(f.split(Component.literal(s), maxW));
        return out;
    }

    private static void box(GuiGraphics g, Font f, int x, int y, String s, int col) {
        int tw = f.width(s);
        g.fill(x - 3, y - 3, x + tw + 3, y + 10, 0x60000000);
        g.fill(x - 3, y - 3, x + tw + 3, y - 2, col);
        g.fill(x - 3, y + 9, x + tw + 3, y + 10, col);
        g.drawString(f, s, x, y, col, false);
    }

    private static void bar(GuiGraphics g, Font f, int x, int y, String label, float v, int col) {
        g.drawString(f, label, x, y, col, true);
        int bx = x + 62, bw = 50;
        g.fill(bx, y + 1, bx + bw, y + 7, 0x80000000);
        g.fill(bx, y + 1, bx + (int) (bw * Mth.clamp(v, 0, 1)), y + 7, col);
        g.drawString(f, String.format("%d%%", Math.round(v * 100)), bx + bw + 4, y, col, true);
    }
}
