/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.network.chat.Component
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.phys.Vec3
 *  net.minecraftforge.client.gui.overlay.ForgeGui
 */
package ru.aiforge.realwar.client.sight;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.client.sight.SightClient;
import ru.aiforge.realwar.ordnance.AirTargets;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.vehicle.LauncherVehicle;
import ru.aiforge.realwar.vehicle.MlrsEntity;
import ru.aiforge.realwar.vehicle.SamLauncherEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;

public final class SightOverlay {
    private static final int C = -8586148;
    private static final int AMB = -20480;
    private static final int RED = -46534;
    private static final int WHITE = -1511208;
    private static Ballistics.Profile ladderFor;
    private static final List<double[]> LADDER;
    private static long tgtKey;
    private static double tgtEl;
    private static boolean tgtOut;
    private static final Map<Long, Double> RC;

    private SightOverlay() {
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Entity entity;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!SightClient.active() || p == null || mc.options.hideGui || !((entity = p.getVehicle()) instanceof LauncherVehicle)) {
            return;
        }
        LauncherVehicle v = (LauncherVehicle)entity;
        Optics.post(pt);                                             // r21: NV / thermal picture under the reticle
        Font f = mc.font;
        float fov = SightClient.fovDeg;
        double fpx = (double)h / 2.0 / Math.tan(Math.toRadians(fov) / 2.0);
        float yawV = p.getViewYRot(pt);
        float elV = -p.getViewXRot(pt);
        int cx = w / 2;
        int cy = h / 2;
        int R = (int)((double)h * 0.47);
        SightOverlay.mask(g, w, h, cx, cy, R);
        SightOverlay.reticle(g, cx, cy, R, fpx);
        if (v instanceof MlrsEntity) {
            MlrsEntity m = (MlrsEntity)v;
            SightOverlay.arty(g, f, m, p, pt, w, h, cx, cy, R, fpx, yawV, elV);
        } else if (v instanceof SamLauncherEntity) {
            SamLauncherEntity s = (SamLauncherEntity)v;
            SightOverlay.sam(g, f, s, p, pt, w, h, cx, cy, R, fpx, yawV, elV);
        }
        String z = "\u00d7" + (int)SightClient.zoom(v);
        // r20: zoom readout in the upper left of the lens (the right side carries the MLRS range scale) (the bottom row belongs to the action tiles); no key text line
        g.drawString(f, z, cx - (int)(R * 0.62) - f.width(z) / 2, cy - (int)(R * 0.62) - 4, -1511208, true);
    }

    private static void mask(GuiGraphics g, int w, int h, int cx, int cy, int R) {
        for (int y = 0; y < h; ++y) {
            int dy = y - cy;
            if (Math.abs(dy) >= R) {
                g.fill(0, y, w, y + 1, -16777216);
                continue;
            }
            int hw = (int)Math.sqrt((double)R * (double)R - (double)dy * (double)dy);
            g.fill(0, y, cx - hw, y + 1, -16777216);
            g.fill(cx + hw, y, w, y + 1, -16777216);
        }
        int steps = 360;
        for (int i = 0; i < steps; ++i) {
            double a = (double)i * Math.PI * 2.0 / (double)steps;
            int x = cx + (int)Math.round(Math.cos(a) * (double)(R - 1));
            int y = cy + (int)Math.round(Math.sin(a) * (double)(R - 1));
            g.fill(x, y, x + 1, y + 1, -13617104);
        }
    }

    private static void reticle(GuiGraphics g, int cx, int cy, int R, double fpx) {
        int step;
        int col = -1072687088;
        int gap = 10;
        g.fill(cx - R + 2, cy, cx - gap, cy + 1, col);
        g.fill(cx + gap, cy, cx + R - 2, cy + 1, col);
        g.fill(cx, cy - R + 2, cx + 1, cy - gap, col);
        g.fill(cx, cy + gap, cx + 1, cy + R - 2, col);
        double pxPerMil = fpx * Math.tan(Math.toRadians(0.06));
        int mil = step = pxPerMil * 10.0 < 7.0 ? 50 : 10;
        while ((double)mil * pxPerMil < (double)(R - 6)) {
            int o = (int)Math.round((double)mil * pxPerMil);
            int len = mil % 50 == 0 ? 5 : 3;
            g.fill(cx + o, cy - len, cx + o + 1, cy + len + 1, col);
            g.fill(cx - o, cy - len, cx - o + 1, cy + len + 1, col);
            g.fill(cx - len, cy + o, cx + len + 1, cy + o + 1, col);
            g.fill(cx - len, cy - o, cx + len + 1, cy - o + 1, col);
            mil += step;
        }
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, -46534);
    }

    private static void ladder(MlrsEntity m) {
        double step;
        if (ladderFor == m.profile()) {
            return;
        }
        ladderFor = m.profile();
        LADDER.clear();
        double maxR = 0.0;
        for (double e = (double)m.kind.elMin; e <= (double)m.kind.elMax; e += 2.5) {
            maxR = Math.max(maxR, Ballistics.range(m.profile(), e, 0.0));
        }
        for (double r = step = maxR > 40000.0 ? 5000.0 : (maxR > 12000.0 ? 2000.0 : 1000.0); r < maxR * 0.98; r += step) {
            double el = Ballistics.solveElevation(m.profile(), r, 0.0, m.kind.elMin, m.kind.elMax);
            if (Double.isNaN(el)) continue;
            LADDER.add(new double[]{r / 1000.0, el});
        }
    }

    private static void arty(GuiGraphics g, Font f, MlrsEntity m, LocalPlayer p, float pt, int w, int h, int cx, int cy, int R, double fpx, float yawV, float elV) {
        float compass;
        SightOverlay.ladder(m);
        for (double[] l : LADDER) {
            int y = cy - (int)Math.round(Math.tan(Math.toRadians(l[1] - (double)elV)) * fpx);
            if (Math.abs(y - cy) > R - 12) continue;
            int x0 = cx + R / 2;
            g.fill(x0, y, x0 + 14, y + 1, -535816176);
            g.drawString(f, String.valueOf((int)l[0]), x0 + 18, y - 4, -15722481, false);
        }
        int yc = cy - (int)Math.round(Math.tan(Math.toRadians(m.el() - elV)) * fpx);
        double curKm = SightOverlay.rangeCached(m.profile(), Math.max(1.0f, -p.getViewXRot(pt))) / 1000.0;
        Vec3 d = m.launchDir();
        float gy = LauncherVehicle.yawOf(d);
        float ge = (float)Math.toDegrees(Math.asin(Mth.clamp((double)d.y, (double)-1.0, (double)1.0)));
        int gxp = cx + (int)Math.round(Math.tan(Math.toRadians(Mth.wrapDegrees((float)(gy - yawV)))) * fpx);
        int gyp = cy - (int)Math.round(Math.tan(Math.toRadians(ge - elV)) * fpx);
        SightOverlay.ring(g, gxp, gyp, 7, -14714160);
        Vec3 t = m.mapTarget();
        String st = null;
        int stCol = -1511208;
        if (t != null) {
            int col;
            Vec3 from = m.muzzleWorld(Math.max(0, m.firstLoaded()));
            double dx = t.x - from.x;
            double dz = t.z - from.z;
            double k = Ballistics.scaleK();
            double rng = Math.sqrt(dx * dx + dz * dz) * k;
            double dy = (t.y - from.y) * k;
            long key = Double.doubleToLongBits(rng) * 31L + Double.doubleToLongBits(dy);
            if (key != tgtKey) {
                tgtKey = key;
                tgtEl = Ballistics.solveElevation(m.profile(), rng, dy, m.kind.elMin, m.kind.elMax);
                tgtOut = Double.isNaN(tgtEl);
            }
            float ty = (float)Math.toDegrees(Math.atan2(-dx, dz));
            double te = tgtOut ? (double)m.kind.elMax : tgtEl;
            double dAz = Mth.wrapDegrees((float)(ty - yawV));
            int tx = cx + (int)Math.round(Math.tan(Math.toRadians(Mth.clamp((double)dAz, (double)-80.0, (double)80.0))) * fpx);
            int tyy = cy - (int)Math.round(Math.tan(Math.toRadians(Mth.clamp((double)(te - (double)elV), (double)-80.0, (double)80.0))) * fpx);
            int n = col = tgtOut ? -46534 : -20480;
            if (Math.hypot(tx - cx, tyy - cy) < (double)(R - 10)) {
                SightOverlay.diamond(g, tx, tyy, 9, col);
            } else {
                double a = Math.atan2(tyy - cy, tx - cx);
                SightOverlay.diamond(g, cx + (int)(Math.cos(a) * (double)(R - 14)), cy + (int)(Math.sin(a) * (double)(R - 14)), 5, col);
            }
            st = Component.translatable((String)"hud.aiarsenal.rw.target").getString() + " " + String.format("%.1f", rng / 1000.0) + " " + Component.translatable((String)"hud.aiarsenal.rw.km").getString() + "   \u0394" + Component.translatable((String)"hud.aiarsenal.rw.az").getString() + " " + String.format("%+.1f\u00b0", dAz) + (String)(tgtOut ? "   " + Component.translatable((String)"hud.aiarsenal.rw.out_of_range").getString() : "");
            int n2 = stCol = tgtOut ? -46534 : -20480;
        }
        if ((compass = Mth.wrapDegrees((float)(yawV + 180.0f))) < 0.0f) {
            compass += 360.0f;
        }
        String top = Component.translatable((String)"hud.aiarsenal.rw.az").getString() + " " + String.format("%05.1f\u00b0", Float.valueOf(compass)) + "   " + Component.translatable((String)"hud.aiarsenal.rw.el").getString() + " " + String.format("%04.1f\u00b0", Float.valueOf(elV)) + "   " + Component.translatable((String)"hud.aiarsenal.rw.range").getString() + " " + String.format("%.1f", curKm) + " " + Component.translatable((String)"hud.aiarsenal.rw.km").getString();
        g.drawCenteredString(f, top, cx, cy - R + 8, -8586148);
        if (st != null) {
            g.drawCenteredString(f, st, cx, cy + R - 22, stCol);
        }
        if (m.autoAim()) {
            g.drawCenteredString(f, (Component)Component.translatable((String)"hud.aiarsenal.rw.auto_aim"), cx, cy - R + 20, -20480);
        }
    }

    private static double rangeCached(Ballistics.Profile pr, double el) {
        long key = (long)pr.ordinal() * 100000L + Math.round(el * 4.0);
        return RC.computeIfAbsent(key, k -> Ballistics.range(pr, (double)Math.round(el * 4.0) / 4.0, 0.0));
    }

    private static void sam(GuiGraphics g, Font f, SamLauncherEntity s, LocalPlayer p, float pt, int w, int h, int cx, int cy, int R, double fpx, float yawV, float elV) {
        float compass;
        Vec3 eye = p.getEyePosition(pt);
        double rr = ((Integer)RWConfig.COMMON.radarRange.get()).intValue();
        for (Entity e : s.level().getEntities((Entity)s, s.getBoundingBox().inflate(rr), AirTargets::isAirTarget)) {
            boolean lock;
            int y;
            int x;
            Vec3 rel = e.getPosition(pt).add(0.0, (double)e.getBbHeight() * 0.5, 0.0).subtract(eye);
            double dAz = Mth.wrapDegrees((double)(Math.toDegrees(Math.atan2(-rel.x, rel.z)) - (double)yawV));
            double dEl = Math.toDegrees(Math.atan2(rel.y, Math.hypot(rel.x, rel.z))) - (double)elV;
            if (Math.abs(dAz) > 80.0 || Math.abs(dEl) > 80.0 || Math.hypot((x = cx + (int)Math.round(Math.tan(Math.toRadians(dAz)) * fpx)) - cx, (y = cy - (int)Math.round(Math.tan(Math.toRadians(dEl)) * fpx)) - cy) > (double)(R - 8)) continue;
            boolean bl = lock = e.getId() == s.lockId();
            int col = lock ? (s.locked() ? -46534 : -20480) : -8586148;
            int b = lock ? 11 : 8;
            SightOverlay.bracket(g, x, y, b, col);
            String lab = (int)rel.length() + " \u043c  h" + (int)(e.getY() - s.getY());
            g.drawString(f, lab, x + b + 3, y - 4, col, true);
            if (!lock || s.locked()) continue;
            g.drawString(f, 100 * s.lockProgress() / 40 + "%", x - b, y + b + 3, -20480, true);
        }
        Vec3 t = s.mapTarget();
        if (t != null) {
            int y;
            Vec3 rel = t.subtract(eye);
            double dAz = Mth.wrapDegrees((double)(Math.toDegrees(Math.atan2(-rel.x, rel.z)) - (double)yawV));
            double dEl = Math.toDegrees(Math.atan2(rel.y, Math.hypot(rel.x, rel.z))) - (double)elV;
            int x = cx + (int)Math.round(Math.tan(Math.toRadians(Mth.clamp((double)dAz, (double)-80.0, (double)80.0))) * fpx);
            if (Math.hypot(x - cx, (y = cy - (int)Math.round(Math.tan(Math.toRadians(Mth.clamp((double)dEl, (double)-80.0, (double)80.0))) * fpx)) - cy) < (double)(R - 10)) {
                SightOverlay.diamond(g, x, y, 10, -20480);
            }
            g.drawCenteredString(f, Component.translatable((String)"hud.aiarsenal.rw.cue").getString() + " " + (int)rel.length() + " \u043c", cx, cy + R - 22, -20480);
        }
        if ((compass = Mth.wrapDegrees((float)(yawV + 180.0f))) < 0.0f) {
            compass += 360.0f;
        }
        g.drawCenteredString(f, Component.translatable((String)"hud.aiarsenal.rw.az").getString() + " " + String.format("%05.1f\u00b0", Float.valueOf(compass)) + "   " + Component.translatable((String)"hud.aiarsenal.rw.el").getString() + " " + String.format("%04.1f\u00b0", Float.valueOf(elV)), cx, cy - R + 8, -8586148);
    }

    private static void diamond(GuiGraphics g, int x, int y, int r, int col) {
        for (int i = 0; i <= r; ++i) {
            g.fill(x + i, y + r - i, x + i + 1, y + r - i + 1, col);
            g.fill(x - i, y + r - i, x - i + 1, y + r - i + 1, col);
            g.fill(x + i, y - r + i, x + i + 1, y - r + i + 1, col);
            g.fill(x - i, y - r + i, x - i + 1, y - r + i + 1, col);
        }
    }

    private static void bracket(GuiGraphics g, int x, int y, int r, int col) {
        int l = Math.max(3, r / 2);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int px = x + sx * r;
                int py = y + sy * r;
                g.fill(Math.min(px, px - sx * l), py, Math.max(px, px - sx * l) + 1, py + 1, col);
                g.fill(px, Math.min(py, py - sy * l), px + 1, Math.max(py, py - sy * l) + 1, col);
            }
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int col) {
        for (int i = 0; i < 48; ++i) {
            double a = (double)i * Math.PI * 2.0 / 48.0;
            int x = cx + (int)Math.round(Math.cos(a) * (double)r);
            int y = cy + (int)Math.round(Math.sin(a) * (double)r);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }

    static {
        LADDER = new ArrayList<double[]>();
        tgtKey = Long.MIN_VALUE;
        RC = new HashMap<Long, Double>();
    }
}

