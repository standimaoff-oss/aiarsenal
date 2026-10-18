package ru.aiforge.realwar.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.client.sight.MapCache;
import ru.aiforge.realwar.aircraft.AircraftEntity;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.vehicle.FlakEntity;
import ru.aiforge.realwar.vehicle.GunVehicle;
import ru.aiforge.realwar.vehicle.LauncherVehicle;
import ru.aiforge.realwar.vehicle.VehicleEntity;

/**
 * Reply 15: War Thunder style ground-vehicle HUD, after the user's screenshots.
 * <ul>
 *   <li>top centre - compass strip (bearing ticks, hull diamond, gun tick);</li>
 *   <li>bottom left - round "vehicle card": top-down hull silhouette (hull always up) with the turret bearing, hull
 *   integrity arc, crew health, and the ПРД / ОБР / СКОР lines (gear / rpm / speed);</li>
 *   <li>bottom centre - weapon slot bar (slot number, icon, ready rounds, reload shade, selected frame) with a one-line
 *   info strip above it (round, loading, range, elevation);</li>
 *   <li>bottom right - grid minimap 7 x 7 (columns 1-7, rows b-g like War Thunder; 48-block cells, world aligned) drawn
 *   from the terrain colour cache, with the view cone, own marker and nearby vehicles.</li>
 * </ul>
 */
public final class WtHud {
    private static final int TXT = 0xFFE8E8E0, DIM = 0xFFA8ACA8, AMB = 0xFFFFB020, REDC = 0xFFFF5A5A;

    private WtHud() {
    }

    // ================================================================== helpers

    public static void small(GuiGraphics g, Font f, String s, float x, float y, int col, boolean centre) {
        g.pose().pushPose();
        g.pose().translate(centre ? x - f.width(s) * 0.375f : x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(f, s, 0, 0, col, true);
        g.pose().popPose();
    }

    private static void disc(GuiGraphics g, int cx, int cy, int r, int col) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, col);
        }
    }

    /** Ring arc in GUI units: radius, thickness, start (deg, 0 = up, clockwise), sweep. */
    private static void arc(GuiGraphics g, float cx, float cy, float r, float th, float start, float sweep, int col) {
        int n = Math.max(2, (int) (Math.abs(sweep) / 4f));
        float seg = (float) Math.toRadians(Math.abs(sweep) / n) * r + 0.8f;
        for (int i = 0; i < n; i++) {
            float a = start + sweep * (i + 0.5f) / n;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(a));
            g.pose().translate(0, -r, 0);
            g.pose().scale(0.25f, 0.25f, 1f);
            int hs = Math.max(1, Math.round(seg * 2)), ht = Math.max(1, Math.round(th * 2));
            g.fill(-hs, -ht, hs, ht, col);
            g.pose().popPose();
        }
    }

    private static String tr(String k) {
        return Component.translatable(k).getString();
    }

    private static float yawOf(Vec3 d) {
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    /** Turret bearing relative to the hull (deg, clockwise seen from above), NaN = no turret. */
    private static float turretRel(VehicleEntity v) {
        if (v instanceof LauncherVehicle lv && !(lv.spec.tubes() == null || lv.spec.tubes().length == 0)) {
            return Mth.wrapDegrees(yawOf(lv.launchDir(lv.az(), 0)) - yawOf(lv.launchDir(0, 0)));
        }
        if (v instanceof FlakEntity fl) return Mth.wrapDegrees(yawOf(fl.aimDir(fl.az(), 0)) - yawOf(fl.aimDir(0, 0)));
        return Float.NaN;
    }

    private static float hullYaw(VehicleEntity v) {
        if (v instanceof LauncherVehicle lv) return yawOf(lv.launchDir(0, 0));
        return v.getYRot();
    }

    // ================================================================== compass

    static void compass(GuiGraphics g, Font f, Minecraft mc, VehicleEntity v, int w, float pt) {
        float cam = mc.gameRenderer.getMainCamera().getYRot();
        float brg = Mth.positiveModulo(cam + 180f, 360f);
        int cx = w / 2, y = 14, half = 84;
        float ppd = 2.0f;                                     // pixels per degree
        g.fill(cx - half, y, cx + half, y + 3, 0x90000000);
        g.fill(cx - half, y + 3, cx + half, y + 4, 0x50000000);
        int d0 = (int) Math.floor((brg - half / ppd) / 5f) * 5;
        for (int d = d0; d <= brg + half / ppd; d += 5) {
            float x = cx + (d - brg) * ppd;
            if (x < cx - half + 1 || x > cx + half - 1) continue;
            int dd = Math.floorMod(d, 360);
            boolean major = dd % 15 == 0;
            g.fill((int) x, y - (major ? 3 : 1), (int) x + 1, y, 0xC0FFFFFF);
            if (major) {
                String s = switch (dd) {
                    case 0 -> "\u0421";
                    case 90 -> "\u0412";
                    case 180 -> "\u042e";
                    case 270 -> "\u0417";
                    default -> Integer.toString(dd);
                };
                small(g, f, s, x + 0.5f, y - 10, dd % 90 == 0 ? 0xFFFFFFFF : 0xD8E0E0E0, true);
            }
        }
        // hull (blue diamond) and gun (red bar) bearings
        float hb = Mth.positiveModulo(hullYaw(v) + 180f, 360f);
        float dh = Mth.wrapDegrees(hb - brg);
        if (Math.abs(dh) * ppd < half - 3) {
            int x = (int) (cx + dh * ppd);
            g.pose().pushPose();
            g.pose().translate(x + 0.5f, y + 8.5f, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(45));
            g.fill(-2, -2, 2, 2, 0xFF3070FF);
            g.pose().popPose();
        }
        float tr = turretRel(v);
        if (!Float.isNaN(tr)) {
            float gb = Mth.positiveModulo(hb + tr, 360f);
            float dg = Mth.wrapDegrees(gb - brg);
            int x0 = (int) (cx + Mth.clamp(Math.min(0, dg) * ppd, -half, half)), x1 = (int) (cx + Mth.clamp(Math.max(0, dg) * ppd, -half, half));
            g.fill(Math.min(x0, x1), y, Math.max(x0, x1) + 1, y + 3, 0xC0E03030);
        }
        g.fill(cx - 2, y + 4, cx + 3, y + 5, 0xFFFFFFFF);         // centre caret
        g.fill(cx - 1, y + 5, cx + 2, y + 6, 0xFFFFFFFF);
        g.fill(cx, y + 6, cx + 1, y + 7, 0xFFFFFFFF);
    }

    // ================================================================== vehicle card

    private static final float[] C_HULL = {0.54f, 0.57f, 0.58f}, C_TUR = {0.76f, 0.79f, 0.80f}, C_BAR = {0.82f, 0.84f, 0.85f},
            C_TRK = {0.30f, 0.32f, 0.33f}, C_WHL = {0.24f, 0.26f, 0.27f}, C_YEL = {0.98f, 0.74f, 0.16f}, C_RED = {0.95f, 0.18f, 0.12f},
            C_RED2 = {0.55f, 0.08f, 0.06f}, C_FIRE = {0.98f, 0.45f, 0.10f};

    private static float[] crewCol(ru.aiforge.realwar.damage.DamageState s, int i) {
        float hp = s.crew[Math.min(i, s.crew.length - 1)];
        if (hp <= 0) return (s.crewMoved >> i & 1) != 0 ? new float[]{0.25f, 0.27f, 0.28f} : (System.currentTimeMillis() / 300 % 2 == 0 ? C_RED : C_RED2);
        return hp < 60 ? C_YEL : new float[]{0.62f, 0.86f, 0.55f};
    }

    private static float[] dmgCol(ru.aiforge.realwar.damage.DamageState s, int m, float[] base) {
        int l = s.level(m);
        if (l >= 2) return (System.currentTimeMillis() / 300 % 2 == 0) ? C_RED : C_RED2;
        return l == 1 ? C_YEL : base;
    }

    /**
     * r20 (msg 20): the bottom-left card shows the vehicle's own GLB from straight above (hull up = forward), the turret
     * at its real bearing and elevation: hull / turret / barrel / tracks / wheels in greys, damaged parts yellow,
     * destroyed parts blinking red (a thrown track stays drawn in red), internal modules (engine, ammo, turret drive)
     * appear over the hull at their true places only when damaged. Burning = orange flicker, dead = dark.
     */
    private static boolean cardModel(GuiGraphics g, Minecraft mc, VehicleEntity v, int cx, int cy, int R, float pt) {
        ru.aiforge.realwar.client.render.GlbAssets.Loaded lm = ru.aiforge.realwar.client.render.GlbAssets.get(v.spec.model());
        if (lm == null) return false;
        ru.aiforge.realwar.damage.DamageState s = v.dmg;
        ru.aiforge.realwar.glb.Pose pose = lm.scratch();
        v.applyPose(pose, pt);
        ru.aiforge.realwar.client.render.DamageRender.vehicle(v, pose);
        int[] cat = ru.aiforge.realwar.client.render.ModuleProps.categories(lm.model);
        for (int i = 0; i < cat.length; i++) {
            int c = cat[i];
            if (c == ru.aiforge.realwar.client.render.ModuleProps.C_INTERIOR) pose.hideIdx(i, true);
            else if (c == ru.aiforge.realwar.client.render.ModuleProps.C_TRACK_L || c == ru.aiforge.realwar.client.render.ModuleProps.C_TRACK_R) pose.hideIdx(i, false);
        }
        pose.compute();
        boolean fire = s.burning() && System.currentTimeMillis() / 250 % 2 == 0;
        float[][] col = new float[7][];
        col[0] = fire ? C_FIRE : C_HULL;
        col[1] = s.level(ru.aiforge.realwar.damage.DamageState.TURRET) > 0 ? dmgCol(s, ru.aiforge.realwar.damage.DamageState.TURRET, C_TUR) : (fire ? C_FIRE : C_TUR);
        col[2] = dmgCol(s, ru.aiforge.realwar.damage.DamageState.BARREL, C_BAR);
        col[3] = dmgCol(s, ru.aiforge.realwar.damage.DamageState.TRACK_L, C_TRK);
        col[4] = dmgCol(s, ru.aiforge.realwar.damage.DamageState.TRACK_R, C_TRK);
        col[5] = C_WHL;
        col[6] = C_HULL;
        float[] tint = new float[cat.length * 4];
        for (int i = 0; i < cat.length; i++) {
            float[] c = col[cat[i]];
            tint[i * 4] = c[0];
            tint[i * 4 + 1] = c[1];
            tint[i * 4 + 2] = c[2];
            tint[i * 4 + 3] = 1f;
        }
        ru.aiforge.realwar.damage.VehicleDamage.Geo ge = ru.aiforge.realwar.damage.VehicleDamage.geo(v);
        float ext = Math.max(v.spec.length() + (ge.turret() ? ge.tube() * 0.7f : 0f), v.spec.aabbW() * 1.25f);
        float S = R * 2f * 0.80f / Math.max(1.5f, ext);
        com.mojang.blaze3d.vertex.PoseStack ps = g.pose();
        net.minecraft.client.renderer.MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        g.flush();
        g.enableScissor(cx - R + 1, cy - R + 1, cx + R - 1, cy + R - 1);
        ps.pushPose();
        ps.translate(cx, cy, 100);
        ps.mulPoseMatrix(new org.joml.Matrix4f(-S, 0, 0, 0, 0, 0, S, 0, 0, -S, 0, 0, 0, 0, 0, 1));
        ps.translate(-ge.cx(), -(ge.cy() + ge.hy() + 3f), -ge.cz());
        com.mojang.blaze3d.platform.Lighting.setupForFlatItems();
        ru.aiforge.realwar.client.render.GlbRenderer.FLAT = true;
        ru.aiforge.realwar.client.render.GlbRenderer.NODE_TINT = tint;
        ru.aiforge.realwar.client.render.GlbRenderer.TINT = s.dead() ? 0.35f : 1f;
        try {
            ru.aiforge.realwar.client.render.GlbRenderer.render(lm, pose, ps, bs, 0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            bs.endBatch();
            // damaged / thrown tracks are usually under the fenders: draw them once more over the roof
            boolean tl = s.level(ru.aiforge.realwar.damage.DamageState.TRACK_L) > 0, trk = s.level(ru.aiforge.realwar.damage.DamageState.TRACK_R) > 0;
            if (tl || trk) {
                for (int i = 0; i < cat.length; i++) {
                    boolean keep = (tl && cat[i] == ru.aiforge.realwar.client.render.ModuleProps.C_TRACK_L) || (trk && cat[i] == ru.aiforge.realwar.client.render.ModuleProps.C_TRACK_R);
                    if (!keep) tint[i * 4 + 3] = -1f;
                }
                ps.pushPose();
                ps.translate(0, 8, 0);
                ru.aiforge.realwar.client.render.GlbRenderer.render(lm, pose, ps, bs, 0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                bs.endBatch();
                ps.popPose();
            }
            // internal modules, only when damaged, lifted over the roof so they show through it
            ru.aiforge.realwar.client.render.GlbRenderer.NODE_TINT = null;
            ps.translate(0, 12, 0);
            final ru.aiforge.realwar.damage.DamageState st = s;
            float az = ru.aiforge.realwar.damage.VehicleDamage.turretAngles(v)[0];
            final int CR = ru.aiforge.realwar.client.render.ModuleProps.CREW;
            ru.aiforge.realwar.client.render.ModuleProps.draw(ps, bs, v.kind, ge, az,
                    id -> id >= CR || st.level(id) > 0,
                    id -> ru.aiforge.realwar.client.render.GlbRenderer.FLAT_RGB = id >= CR ? crewCol(st, id - CR) : dmgCol(st, id, C_YEL));
            bs.endBatch();
        } catch (Throwable ex) {
            ru.aiforge.realwar.RealWar.LOGGER.warn("vehicle card render failed: {}", ex.toString());
        } finally {
            ru.aiforge.realwar.client.render.GlbRenderer.FLAT = false;
            ru.aiforge.realwar.client.render.GlbRenderer.NODE_TINT = null;
            ru.aiforge.realwar.client.render.GlbRenderer.TINT = 1f;
            ru.aiforge.realwar.client.render.GlbRenderer.FLAT_RGB = new float[]{0.62f, 0.64f, 0.66f};
            com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
            ps.popPose();
            g.disableScissor();
        }
        return true;
    }

    static void card(GuiGraphics g, Font f, Minecraft mc, VehicleEntity v, int w, int h, float pt) {
        int R = 40, cx = 6 + R, cy = h - 6 - R;
        disc(g, cx, cy, R, 0x70141C22);
        arc(g, cx, cy, R - 0.5f, 1, 0, 360, 0x60A8B8C0);
        arc(g, cx, cy, R - 12, 0.5f, 0, 360, 0x30A8B8C0);
        // hull integrity: arc on the upper right, green -> amber -> red
        float hpF = Mth.clamp(v.hp() / Math.max(1f, v.kind.hp), 0f, 1f);
        int hc = hpF > 0.6f ? 0xFF7CD050 : hpF > 0.3f ? AMB : REDC;
        arc(g, cx, cy, R + 2.5f, 1.2f, 20, 70, 0x60000000);
        arc(g, cx, cy, R + 2.5f, 1.2f, 20, 70 * hpF, hc);
        // r20 (msg 20): the real vehicle model seen from straight above, damaged parts painted on it
        int nCrew = ru.aiforge.realwar.damage.Crew.count(v.kind);
        if (nCrew > 0) crewRow(g, f, v, 8, cy - R - 13);
        int ly = cy - R - (nCrew > 0 ? 25 : 12);
        if (cardModel(g, mc, v, cx, cy, R, pt)) {
            DamageHud.list(g, f, v, v.dmg, 8, ly, false);
        } else {
            silhouette(g, f, v, cx, cy, R);
        }
        // crew health (heart + hp) top-left of the circle
        float php0 = mc.player.getHealth();
        cardRest(g, f, mc, v, w, h, pt, R, cx, cy, php0);
    }

    /** r21: War Thunder style crew strip over the card: one tile per seat with the role letter; health bar,
     *  wounded amber, dead red, seat left empty grey, the seat being taken over blinks with the progress. */
    private static void crewRow(GuiGraphics g, Font f, VehicleEntity v, int x, int y) {
        ru.aiforge.realwar.damage.DamageState s = v.dmg;
        int[] r = ru.aiforge.realwar.damage.Crew.roles(v.kind);
        boolean blink = System.currentTimeMillis() / 250 % 2 == 0;
        for (int i = 0; i < r.length; i++) {
            int x0 = x + i * 12;
            float hp = s.crew[i];
            boolean moved = (s.crewMoved >> i & 1) != 0;
            int bg = hp <= 0 ? (moved ? 0xC0303438 : (blink && !s.dead() ? 0xE0C02018 : 0xE0701010)) : hp < 60 ? 0xE0A07010 : 0xC0283830;
            if (s.swapTo == i && blink) bg = 0xE0207030;
            g.fill(x0 - 1, y - 1, x0 + 11, y + 11, 0x90000000);
            g.fill(x0, y, x0 + 10, y + 10, bg);
            if (hp > 0) {
                int bw = Math.max(1, Math.round(10 * Math.min(1f, hp / 100f)));
                g.fill(x0, y + 9, x0 + bw, y + 10, hp < 60 ? 0xFFFFC040 : 0xFF7CD050);
            }
            if (s.swapTo == i) g.fill(x0, y + 9, x0 + Math.round(10 * s.swapFrac()), y + 10, 0xFF60FF80);
            String letter = tr("hud.aiarsenal.rw.crew.short." + ru.aiforge.realwar.damage.Crew.KEYS[r[i]]);
            small(g, f, letter, x0 + 5 - f.width(letter) * 0.375f, y + 1.5f, hp <= 0 && moved ? 0xFF808890 : 0xFFFFFFFF, false);
        }
    }

    private static void silhouette(GuiGraphics g, Font f, VehicleEntity v, int cx, int cy, int R) {
        // silhouette (hull up)
        float len = Math.max(2f, v.spec.length()), wid = Math.max(1f, v.spec.aabbW());
        float L = 46f, W = Mth.clamp(L * wid / len, 14f, 30f);
        int hl = (int) (L / 2), hw = (int) (W / 2);
        boolean tracked = v instanceof GunVehicle gv && gv.cfg.tracked();
        int hull = 0xFF8E989C, dark = 0xFF4A5256, lite = 0xFFC4CCD0;
        g.fill(cx - hw - 1, cy - hl - 1, cx + hw + 1, cy + hl + 1, 0xFF20262A);
        g.fill(cx - hw, cy - hl, cx + hw, cy + hl, hull);
        if (tracked) {
            g.fill(cx - hw, cy - hl, cx - hw + 4, cy + hl, dark);
            g.fill(cx + hw - 4, cy - hl, cx + hw, cy + hl, dark);
            for (int yy = cy - hl + 1; yy < cy + hl; yy += 3) {
                g.fill(cx - hw, yy, cx - hw + 4, yy + 1, 0xFF2E3438);
                g.fill(cx + hw - 4, yy, cx + hw, yy + 1, 0xFF2E3438);
            }
        } else {
            for (float[] wh : v.spec.wheels()) {
                int wy = (int) (cy - wh[2] / len * L);
                int sx = wh[0] >= 0 ? -1 : 1;           // +x = left side of the vehicle
                int x0 = sx < 0 ? cx - hw - 2 : cx + hw - 1;
                g.fill(x0, wy - 2, x0 + 3, wy + 2, 0xFF262C30);
            }
        }
        g.fill(cx - hw + 5, cy - hl + 2, cx + hw - 5, cy - hl + 4, lite);   // glacis line = front
        float tr = turretRel(v);
        if (!Float.isNaN(tr)) {
            float pz = 0f;
            if (v instanceof GunVehicle gv) {
                Vec3 pv = gv.pivotWorld().subtract(gv.position());
                Vec3 fw = gv.launchDir(0, 0);
                pz = (float) (pv.x * fw.x + pv.z * fw.z);
            }
            int tx = cx, ty = (int) (cy - Mth.clamp(pz / len * L, -hl + 6, hl - 6));
            g.pose().pushPose();
            g.pose().translate(tx + 0.5f, ty + 0.5f, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(tr));
            int tw = Math.max(5, (int) (W * 0.34f));
            g.fill(-1, -(int) (L * 0.62f), 2, 0, 0xFFE8ECEE);                 // barrel
            g.pose().popPose();
            disc(g, tx, ty, tw + 1, 0xFF20262A);
            disc(g, tx, ty, tw, lite);
            disc(g, tx, ty, Math.max(1, tw - 3), 0xFFA8B0B4);
        }
        DamageHud.vehicleCard(g, f, v, cx, cy, hw, hl, R, tracked);
    }

    private static void cardRest(GuiGraphics g, Font f, Minecraft mc, VehicleEntity v, int w, int h, float pt, int R, int cx, int cy, float php) {
        small(g, f, "\u2665 " + Math.round(php), 8, cy - R + 2, php < 6 ? REDC : 0xFFFF8080, false);
        // gear / rpm / speed
        double kmh = Math.abs(v.speedMs) * 3.6;
        float vmax = Math.max(10f, v.kind.vmaxKmh);
        String gear;
        int rpm;
        if (!v.engineOn()) {
            gear = "N";
            rpm = 0;
        } else if (v.speedMs < -0.3) {
            gear = "R";
            rpm = 900 + (int) (Math.min(1, kmh / (vmax * 0.25)) * 1400);
        } else if (kmh < 0.6) {
            gear = "N";
            rpm = 800 + (int) (Math.abs(v.throttle()) * 500);
        } else {
            double gx = kmh / vmax * 6.0;
            int gi = Mth.clamp((int) Math.ceil(gx), 1, 6);
            gear = Integer.toString(gi);
            rpm = 1100 + (int) (Mth.clamp(gx - (gi - 1), 0, 1) * 1500);
        }
        rpm = rpm / 50 * 50;
        int ty0 = h - 31;
        small(g, f, tr("hud.aiarsenal.rw.wt.gear") + " " + gear, 8, ty0, TXT, false);
        small(g, f, tr("hud.aiarsenal.rw.wt.rpm") + " " + rpm, 8, ty0 + 8, TXT, false);
        small(g, f, tr("hud.aiarsenal.rw.wt.spd") + " " + Math.round(kmh) + " " + tr("hud.aiarsenal.rw.kmh"), 8, ty0 + 16, v.speedMs < -0.3 ? AMB : TXT, false);
    }

    // ================================================================== slot bar

    public record Slot(int icon, int count, int max, boolean sel, float reload, String name) {
    }

    // client-side breech cycle: the server fireCd is not synced, so a drop of the loaded count starts a local timer
    private static int cycId = -1, cycLast = -1;
    private static long cycT0 = Long.MIN_VALUE;

    /** @return 1 right after a main-gun shot falling to 0 when the gun can fire again */
    static float gunCycle(GunVehicle gv) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return 0f;
        long now = mc.level.getGameTime();
        int n = gv.loadedCount();
        if (gv.getId() != cycId) {
            cycId = gv.getId();
            cycLast = n;
            cycT0 = Long.MIN_VALUE;
        } else if (n < cycLast) {
            cycT0 = now;
            cycLast = n;
        } else if (n != cycLast) {
            cycLast = n;
        }
        if (cycT0 == Long.MIN_VALUE) return 0f;
        float len = Math.max(1, gv.kind.fireInterval);
        float pt = mc.getFrameTime();
        float k = 1f - (now - cycT0 + pt) / len;
        return Math.max(0f, Math.min(1f, k));
    }

    /** @return top y of the bar (h if nothing was drawn) */
    static int slots(GuiGraphics g, Font f, VehicleEntity v, int seat, int w, int h, java.util.List<ActionBar.Chip> acts, int maxW) {
        java.util.List<Slot> sl = new java.util.ArrayList<>();
        String info = "";
        int infoCol = TXT;
        if (v instanceof GunVehicle gv && seat == gv.operatorSeat()) {
            int[] modes = gv.cfg.modes();
            int cur = gv.mode();
            float rl = gv.reloadTube() >= 0 ? 1f - gv.reloadTicks() / (float) Math.max(1, gv.kind.reloadTicks) : 0f;
            float cyc = gunCycle(gv);
            boolean cycling = rl <= 0f && cyc > 0f;
            if (cycling) rl = cyc;
            for (int m : modes) {
                Slot s = switch (m) {
                    // r28: each slot shows the rounds of ITS OWN type that the loader actually seated. The rack used
                    // to be one typeless counter shown twice, so firing one HE round also emptied the AP slot.
                    case 0 -> new Slot(gv.cfg.primary().kinetic() ? 1 : 0, gv.loadedOfType(0), gv.kind.magazine, cur == 0, rl,
                            tr("hud.aiarsenal.rw.gun.round." + gv.cfg.primary().name().toLowerCase(java.util.Locale.ROOT)));
                    case 1 -> new Slot(gv.cfg.secondary().kinetic() ? 1 : 0, gv.loadedOfType(1), gv.kind.magazine, cur == 1, rl,
                            tr("hud.aiarsenal.rw.gun.round." + gv.cfg.secondary().name().toLowerCase(java.util.Locale.ROOT)));
                    case 2 -> new Slot(gv.kind == ru.aiforge.realwar.vehicle.VehicleKind.BRADLEY ? 3 : 0, gv.thirdRounds(), gv.thirdMax(), cur == 2, 0f,
                            gv.kind == ru.aiforge.realwar.vehicle.VehicleKind.BRADLEY ? "TOW" : tr("hud.aiarsenal.rw.gun.round." + gv.cfg.third().name().toLowerCase(java.util.Locale.ROOT)));
                    default -> new Slot(gv.cfg.coax30() ? 0 : 2, gv.coaxRounds(), GunVehicle.COAX_MAX, cur == 3, 0f,
                            tr("hud.aiarsenal.rw.gun.round." + (gv.cfg.coax30() ? "coax30" : "coax_mg")));
                };
                sl.add(s);
            }
            StringBuilder b = new StringBuilder(tr("hud.aiarsenal.rw.gun.round." + gv.roundKey()));
            if ((gv.reloadTube() >= 0 || cycling) && !gv.coaxSelected() && !gv.thirdSelected()) {
                b.append("  \u00b7  ").append(tr("hud.aiarsenal.rw.gun.loading")).append(" ").append(Math.round((1f - rl) * 100)).append("%");
                infoCol = AMB;
            } else if (!gv.coaxSelected() && !gv.thirdSelected() && gv.loadedOfType(gv.loadType()) == 0) {
                b.append("  \u00b7  ").append(tr(gv.loadedCount() == 0 ? "hud.aiarsenal.rw.no_ammo" : "hud.aiarsenal.rw.gun.wrong_round"));
                infoCol = REDC;
            }
            float r = gv.lase();
            if (r > 0f) {
                double m = r * Ballistics.scaleK();
                b.append("  \u00b7  ").append(m >= 10000 ? String.format("%.1f %s", m / 1000, tr("hud.aiarsenal.rw.km")) : String.format("%d m", Math.round(m / 10.0) * 10));
            }
            if (gv.autoAim()) b.append("  \u00b7  ").append(tr("hud.aiarsenal.rw.gun.auto"));
            b.append("  \u00b7  ").append(String.format("%+.1f\u00b0", gv.el()));
            info = b.toString();
        } else if (v instanceof FlakEntity fl) {
            sl.add(new Slot(2, fl.ammo(), 100, true, 0f, ""));
        } else if (v instanceof LauncherVehicle lv && !(v instanceof GunVehicle) && lv.kind.magazine > 0) {
            float rl = lv.reloadTube() >= 0 ? 1f - lv.reloadTicks() / (float) Math.max(1, lv.kind.reloadTicks) : 0f;
            sl.add(new Slot(3, lv.loadedCount(), lv.kind.magazine, true, rl, ""));
        }
        return drawSlots(g, f, sl, info, infoCol, w, h, acts, maxW);
    }

    public static int drawSlots(GuiGraphics g, Font f, java.util.List<Slot> sl, String info, int infoCol, int w, int h) {
        return drawSlots(g, f, sl, info, infoCol, w, h, java.util.List.of(), w);
    }

    /**
     * Slot bar bottom-centre + optional info strip above it (also used by CraftHud). r20 (msg 20): the special
     * actions are square tiles in the same row, right of the weapon slots (the whole row centred); if the row would
     * exceed {@code maxW} they wrap into rows above.
     */
    public static int drawSlots(GuiGraphics g, Font f, java.util.List<Slot> sl, String info, int infoCol, int w, int h,
                                java.util.List<ActionBar.Chip> acts0, int maxW) {
        java.util.List<ActionBar.Chip> acts = ActionBar.visible(acts0);
        if (sl.isEmpty() && acts.isEmpty()) return h;
        int cw = 20, gap = 2, n = sl.size();
        int tw = n == 0 ? 0 : n * cw + (n - 1) * gap;
        int aw = ActionBar.rowW(acts.size());
        int sep = tw > 0 && aw > 0 ? 6 : 0;
        // r22 (msg 22): the action tiles ALWAYS sit in the same bottom row right after the ammo slots - never a second
        // row; they are drawn smaller (bottom-aligned with the slots) and shrink further until the row fits in maxW.
        float ts = aw > 0 ? Math.max(0.5f, Math.min(0.8f, (maxW - tw - sep) / (float) aw)) : 1f;
        int awS = Math.round(aw * ts);
        int total = tw + sep + awS;
        int x0 = w / 2 - total / 2, y0 = h - 6 - 26;
        int top = y0;
        if (aw > 0) {
            g.pose().pushPose();
            g.pose().translate(x0 + tw + sep, y0 + 26 - ActionBar.TH * ts, 0);
            g.pose().scale(ts, ts, 1f);
            ActionBar.drawTiles(g, f, acts, 0, 0);
            g.pose().popPose();
        }
        for (int i = 0; i < n; i++) {
            Slot s = sl.get(i);
            int x = x0 + i * (cw + gap);
            g.fill(x, y0, x + cw, y0 + 6, 0xB0303438);                      // key strip
            g.fill(x, y0 + 6, x + cw, y0 + 26, 0x9A1A1E22);
            small(g, f, Integer.toString(i + 1), x + cw / 2f, y0 - 0.2f, s.sel ? 0xFFFFFFFF : DIM, true);
            icon(g, s.icon, x + cw / 2, y0 + 15, s.count > 0 ? 0xFFD8DCD8 : 0xFF6A6E6A);
            if (s.sel && s.reload > 0f) reloadPie(g, x, y0 + 6, cw, 20, s.reload);
            String c = Integer.toString(s.count);
            g.pose().pushPose();
            g.pose().translate(x + cw - 1 - f.width(c) * 0.6f, y0 + 20.5f, 0);
            g.pose().scale(0.6f, 0.6f, 1f);
            g.drawString(f, c, 0, 0, s.count == 0 ? REDC : 0xFFFFFFFF, true);
            g.pose().popPose();
            if (s.sel) {
                int col = 0xFFF0F0F0;
                g.fill(x - 1, y0 - 1, x + cw + 1, y0, col);
                g.fill(x - 1, y0 + 26, x + cw + 1, y0 + 27, col);
                g.fill(x - 1, y0, x, y0 + 26, col);
                g.fill(x + cw, y0, x + cw + 1, y0 + 26, col);
            }
        }
        if (!info.isEmpty() && n > 0) {
            float iw = f.width(info) * 0.75f;
            // r20: centred over the slots but kept inside the free band between the tank card and the map
            float lo = w / 2f - maxW / 2f + 2 + iw / 2 + 3, hi = w / 2f + maxW / 2f - 2 - iw / 2 - 3;
            float ic = lo > hi ? lo : Math.max(lo, Math.min(hi, x0 + tw / 2f));   // r22g: too wide -> left-aligned in the band, never clipped
            g.fill((int) (ic - iw / 2 - 3), top - 11, (int) (ic + iw / 2 + 3), top - 3, 0x80101418);
            small(g, f, info, ic, top - 10, infoCol, true);
            return top - 12;
        }
        return top - 2;
    }

    /**
     * r22 (msg 22): reload of the selected round - the slot is greyed and the grey sector recedes clockwise from
     * 12 o'clock as the reload advances (remaining = 1 just after the shot, 0 = loaded).
     */
    static void reloadPie(GuiGraphics g, int x, int y, int bw, int bh, float remaining) {
        float done = 1f - Math.max(0f, Math.min(1f, remaining));
        float cx = x + bw / 2f, cy = y + bh / 2f;
        for (int yy = 0; yy < bh; yy++) {
            int run = -1;
            for (int xx = 0; xx <= bw; xx++) {
                boolean grey = false;
                if (xx < bw) {
                    double dx = x + xx + 0.5 - cx, dy = y + yy + 0.5 - cy;
                    double ang = Math.atan2(dx, -dy) / (2 * Math.PI);        // 0 at 12 o'clock, clockwise
                    if (ang < 0) ang += 1;
                    grey = ang >= done;
                }
                if (grey && run < 0) run = xx;
                if (!grey && run >= 0) {
                    g.fill(x + run, y + yy, x + xx, y + yy + 1, 0xA8606468);
                    run = -1;
                }
            }
        }
        // hand of the "clock"
        double a = done * 2 * Math.PI;
        for (int i = 0; i < 9; i++) {
            int px = Math.round((float) (cx + Math.sin(a) * i)), py = Math.round((float) (cy - Math.cos(a) * i));
            g.fill(px, py, px + 1, py + 1, 0xE0FFFFFF);
        }
    }

    /** 0 HE shell, 1 APFSDS dart, 2 MG belt, 3 missile / rocket. Centred at (x, y), about 14 px tall. */
    private static void icon(GuiGraphics g, int kind, int x, int y, int col) {
        switch (kind) {
            case 1 -> {
                g.fill(x - 1, y - 7, x + 1, y + 6, col);
                g.fill(x - 3, y - 3, x + 3, y - 1, col);
                g.fill(x - 3, y + 4, x + 3, y + 6, col);
                g.fill(x - 2, y + 1, x + 2, y + 3, 0xFF9A9E9A);
            }
            case 2 -> {
                for (int i = -2; i <= 2; i++) {
                    int xx = x + i * 3;
                    g.fill(xx - 1, y - 4 + Math.abs(i), xx + 1, y + 5, col);
                    g.fill(xx - 1, y - 6 + Math.abs(i), xx + 1, y - 4 + Math.abs(i), 0xFFC8A040);
                }
                g.fill(x - 8, y + 3, x + 8, y + 4, 0xFF5A5E5A);
            }
            case 3 -> {
                g.fill(x - 1, y - 7, x + 2, y + 6, col);
                g.fill(x, y - 8, x + 1, y - 7, col);
                g.fill(x - 3, y + 3, x + 4, y + 6, col);
                g.fill(x - 2, y - 3, x + 3, y - 2, col);
            }
            default -> {
                g.fill(x - 2, y - 4, x + 3, y + 6, col);
                g.fill(x - 1, y - 6, x + 2, y - 4, col);
                g.fill(x, y - 7, x + 1, y - 6, col);
                g.fill(x - 2, y + 2, x + 3, y + 3, 0xFFC08A40);
            }
        }
    }

    // ================================================================== minimap

    private static final int CELLS = 7, CELL_B = 48, TILE_B = CELLS * CELL_B, TEX = 112;
    private static final ResourceLocation MAP_RL = new ResourceLocation("aiarsenal", "dynamic/wt_minimap");
    private static DynamicTexture tex;
    private static int mx0 = Integer.MIN_VALUE, mz0;
    private static long mapStamp = -1000;
    private static ClientLevel mapLevel;

    public static void clearMap() {
        mx0 = Integer.MIN_VALUE;
        mapLevel = null;
    }

    /** @return the map size (GUI px) so the hint panel can sit above it */
    static int minimap(GuiGraphics g, Font f, Minecraft mc, VehicleEntity v, int w, int h, float pt) {
        int S = 98;
        int x0 = w - S - 6, y0 = h - S - 6;
        ClientLevel lv = mc.level;
        double px = Mth.lerp(pt, v.xo, v.getX()), pz = Mth.lerp(pt, v.zo, v.getZ());
        boolean moved = false;
        if (mapLevel != lv || mx0 == Integer.MIN_VALUE || px < mx0 + CELL_B || px > mx0 + TILE_B - CELL_B
                || pz < mz0 + CELL_B || pz > mz0 + TILE_B - CELL_B) {
            mx0 = (int) Math.round((px - TILE_B / 2.0) / CELL_B) * CELL_B;
            mz0 = (int) Math.round((pz - TILE_B / 2.0) / CELL_B) * CELL_B;
            mapLevel = lv;
            moved = true;
        }
        long now = lv.getGameTime();
        if (tex == null) {
            tex = new DynamicTexture(TEX, TEX, true);
            mc.getTextureManager().register(MAP_RL, tex);
            moved = true;
        }
        if (moved || now - mapStamp > 40 || now < mapStamp) {
            paint(lv);
            mapStamp = now;
        }
        g.fill(x0 - 1, y0 - 1, x0 + S + 1, y0 + S + 1, 0xC0101010);
        g.blit(MAP_RL, x0, y0, S, S, 0f, 0f, TEX, TEX, TEX, TEX);
        float cs = S / (float) CELLS;
        for (int i = 1; i < CELLS; i++) {
            int gx = x0 + Math.round(i * cs), gy = y0 + Math.round(i * cs);
            g.fill(gx, y0, gx + 1, y0 + S, 0x38000000);
            g.fill(x0, gy, x0 + S, gy + 1, 0x38000000);
        }
        for (int i = 0; i < CELLS; i++) {
            mapLabel(g, f, Integer.toString(i + 1), x0 + i * cs + cs / 2f, y0 + 1.5f);
            if (i > 0) mapLabel(g, f, String.valueOf((char) ('a' + i)), x0 + 4f, y0 + i * cs + cs / 2f - 2.5f);
        }
        float sc = S / (float) TILE_B;
        g.enableScissor(x0, y0, x0 + S, y0 + S);
        // other vehicles nearby (light blue)
        AABB box = new AABB(mx0, lv.getMinBuildHeight(), mz0, mx0 + TILE_B, lv.getMaxBuildHeight(), mz0 + TILE_B);
        for (Entity e : lv.getEntities((Entity) null, box, e -> e != v && (e instanceof VehicleEntity || e instanceof CraftEntity || e instanceof AircraftEntity))) {
            int ex = (int) (x0 + (e.getX() - mx0) * sc), ez = (int) (y0 + (e.getZ() - mz0) * sc);
            g.fill(ex - 2, ez - 1, ex + 2, ez + 2, 0xFF1C2C60);
            g.fill(ex - 1, ez, ex + 1, ez + 1, 0xFF4C8CFF);
        }
        float mxp = (float) (x0 + (px - mx0) * sc), mzp = (float) (y0 + (pz - mz0) * sc);
        float cam = mc.gameRenderer.getMainCamera().getYRot();
        // view cone: light wedge, 70 deg, ~38 % of the map
        float coneL = S * 0.38f;
        for (float a = -35f; a <= 35f; a += 2.5f) {
            g.pose().pushPose();
            g.pose().translate(mxp, mzp, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(cam + 180f + a));
            g.pose().scale(0.5f, 0.5f, 1f);
            g.fill(-1, -(int) (coneL * 2), 1, 0, 0x16FFF6D8);
            g.pose().popPose();
        }
        // own marker: arrow along the hull
        g.pose().pushPose();
        g.pose().translate(mxp, mzp, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(hullYaw(v) + 180f));
        g.pose().scale(0.5f, 0.5f, 1f);
        int ac = 0xFFFFE070;
        g.fill(-1, -7, 1, -5, ac);
        g.fill(-2, -5, 2, -3, ac);
        g.fill(-3, -3, 3, -1, ac);
        g.fill(-4, -1, 4, 1, ac);
        g.fill(-5, 1, 5, 3, ac);
        g.fill(-5, 3, -2, 5, ac);
        g.fill(2, 3, 5, 5, ac);
        g.pose().popPose();
        g.disableScissor();
        return S;
    }

    private static void mapLabel(GuiGraphics g, Font f, String s, float x, float y) {
        g.pose().pushPose();
        g.pose().translate(x - f.width(s) * 0.3f, y, 0);
        g.pose().scale(0.6f, 0.6f, 1f);
        g.drawString(f, s, 0, 0, 0xFF1A1A14, false);
        g.pose().popPose();
    }

    /** Terrain colours from MapCache, toned like the War Thunder paper map (sepia, light). */
    private static void paint(ClientLevel lv) {
        NativeImage im = tex.getPixels();
        if (im == null) return;
        double bpp = TILE_B / (double) TEX;
        for (int z = 0; z < TEX; z++) {
            for (int x = 0; x < TEX; x++) {
                int wx = mx0 + (int) (x * bpp + bpp / 2), wz = mz0 + (int) (z * bpp + bpp / 2);
                MapCache.Tile t = MapCache.tile(lv, wx >> 4, wz >> 4);
                int r, gg, b;
                if (t == null) {
                    r = 150; gg = 140; b = 112;
                } else {
                    int c = t.rgb[(wz & 15) * 16 + (wx & 15)];
                    int cr = c >> 16 & 255, cg = c >> 8 & 255, cb = c & 255;
                    float lum = (cr * 0.3f + cg * 0.59f + cb * 0.11f) / 255f;
                    boolean water = cb > cr + 20 && cb > cg;
                    float sr = 120 + lum * 120, sg = 112 + lum * 108, sb = 78 + lum * 80;   // paper sepia
                    float k = water ? 0.55f : 0.30f;                                         // keep a hint of real colour
                    r = (int) (sr * (1 - k) + cr * k);
                    gg = (int) (sg * (1 - k) + cg * k);
                    b = (int) (sb * (1 - k) + cb * k);
                }
                im.setPixelRGBA(x, z, 0xFF000000 | Mth.clamp(b, 0, 255) << 16 | Mth.clamp(gg, 0, 255) << 8 | Mth.clamp(r, 0, 255));
            }
        }
        tex.upload();
    }
}
