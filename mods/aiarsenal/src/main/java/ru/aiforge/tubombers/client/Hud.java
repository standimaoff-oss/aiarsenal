package ru.aiforge.tubombers.client;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import ru.aiforge.tubombers.aircraft.AircraftData;
import ru.aiforge.tubombers.aircraft.FlightModel;
import ru.aiforge.tubombers.entity.BomberEntity;
import ru.aiforge.tubombers.entity.BomberPart;
import ru.aiforge.tubombers.entity.Crew;
import ru.aiforge.tubombers.registry.ModRegistry;

import java.util.ArrayList;
import java.util.List;

/** Flight instruments, systems, bombing panel, warnings and the context control hints at the bottom of the screen. */
public final class Hud {
    private static final int GREEN = 0x55FF66, AMBER = 0xFFC040, RED = 0xFF4A3A, WHITE = 0xF0F0F0, GREY = 0xA0A0A0, CYAN = 0x66DDFF;
    private static int warnTicks;
    private static String warning;

    private Hud() {
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("hud.aiarsenal." + key, args).getString();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        BomberEntity b = ClientEvents.vehicle();
        warning = null;
        if (b == null || mc.player == null) return;
        AircraftData.Seat s = b.seatOf(mc.player);
        if (s == null || !s.canFly()) return;
        FlightModel.State st = b.st;
        boolean ground = b.onGroundState();
        float agl = agl(b);
        double vs = st.vel.y;
        if (b.hasFlag(BomberEntity.F_STALL)) warning = tr("w_stall");
        else if (!ground && agl < 90 && vs < -12) warning = tr("w_pullup");
        else if (!ground && agl < 45 && b.gear > 0.5f && vs < -1) warning = tr("w_gear");
        else if (st.vel.length() / 340 > (b.kind.swingWing ? 2.05 : 0.86)) warning = tr("w_overspeed");
        else if (b.health() < b.kind.health * 0.3f) warning = tr("w_damage");
        if (warning != null && ++warnTicks % 12 == 1)
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModRegistry.SND_WARNING.get(), 1f, 0.6f));
        if (warning == null) warnTicks = 0;
    }

    private static float agl(BomberEntity b) {
        Minecraft mc = Minecraft.getInstance();
        double g = new ru.aiforge.tubombers.fx.LevelEnv(mc.level).ground(b.getX(), b.getY(), b.getZ());
        return g > -1e8 ? (float) (b.getY() - g) : 999;
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Font f = mc.font;
        BomberEntity b = ClientEvents.vehicle();
        if (b == null) {
            // looking at a parked bomber: boarding hint
            if (mc.hitResult instanceof EntityHitResult eh && (eh.getEntity() instanceof BomberPart || eh.getEntity() instanceof BomberEntity)) {
                hints(g, f, w, h, List.of(tr("h_board"), tr("h_load_item"), tr("h_pickup")));
            }
            return;
        }
        Crew c = b.crewOf(mc.player);
        AircraftData.Seat s = b.seatOf(mc.player);
        List<String> hint = new ArrayList<>();
        String title = Component.translatable(b.kind.displayKey()).getString();
        if (s == null) {
            String where = c != null && c.mode == Crew.LADDER ? tr("on_ladder") : tr("in_cabin");
            g.drawString(f, title + " — " + where, 6, 6, WHITE);
            basicData(g, f, b, 6, 18);
            if (c != null && c.mode == Crew.LADDER) {
                hint.add(tr("h_ladder"));
                hint.add(b.hasFlag(BomberEntity.F_DOOR) ? tr("h_exit") : tr("h_bail"));
            } else {
                AircraftData.Seat near = ClientCrew.seatInReach(b, c);
                hint.add(tr("h_walk"));
                if (near != null) hint.add(tr("h_sit", Component.translatable("seat.aiarsenal." + near.role().toLowerCase()).getString()));
                else hint.add(tr("h_find_seat"));
            }
        } else {
            String role = Component.translatable("seat.aiarsenal." + s.role().toLowerCase()).getString();
            g.drawString(f, title + " — " + role, 6, 6, WHITE);
            if (s.canFly()) {
                flightPanel(g, f, b, w, h);
                bombPanel(g, f, b, w, h);
                hint.add(tr("h_fly1"));
                hint.add(tr("h_fly2") + (b.kind.swingWing ? "  " + tr("h_sweep") : ""));
                hint.add(tr("h_fly3"));
            } else if (s.bombardier()) {
                basicData(g, f, b, 6, 18);
                bombPanel(g, f, b, w, h);
                if (ClientEvents.sight) sightOverlay(g, f, b, w, h);
                hint.add(tr("h_nav1"));
                hint.add(tr("h_nav2"));
            } else {
                basicData(g, f, b, 6, 18);
                hint.add(tr("h_stand"));
            }
        }
        if (warning != null && (warnTicks / 6) % 2 == 0) {
            int ww = f.width(warning);
            g.fill(w / 2 - ww / 2 - 6, h / 2 - 46, w / 2 + ww / 2 + 6, h / 2 - 32, 0xA0400000);
            g.drawString(f, warning, w / 2 - ww / 2, h / 2 - 43, RED);
        }
        hints(g, f, w, h, hint);
    }

    private static void hints(GuiGraphics g, Font f, int w, int h, List<String> lines) {
        if (!ClientEvents.hints && ClientEvents.vehicle() != null) {
            String s = tr("h_toggle");
            g.drawString(f, s, w / 2 - f.width(s) / 2, h - 62, GREY);
            return;
        }
        int y = h - 50 - lines.size() * 11;
        for (String l : lines) {
            int lw = f.width(l);
            g.fill(w / 2 - lw / 2 - 4, y - 2, w / 2 + lw / 2 + 4, y + 9, 0x90000000);
            g.drawString(f, l, w / 2 - lw / 2, y, 0xFFE8E0A0);
            y += 11;
        }
    }

    private static void basicData(GuiGraphics g, Font f, BomberEntity b, int x, int y) {
        FlightModel.State st = b.st;
        double v = st.vel.length();
        g.drawString(f, tr("speed", Math.round(v * 3.6)), x, y, GREY);
        g.drawString(f, tr("alt", Math.round((b.getY() - FlightModel.SEA_LEVEL) / b.speedScale())), x, y + 10, GREY);
        g.drawString(f, tr("hdg", Math.round((st.yawDeg() + 540) % 360)), x, y + 20, GREY);
    }

    private static void flightPanel(GuiGraphics g, Font f, BomberEntity b, int w, int h) {
        FlightModel.State st = b.st;
        double V = st.vel.length();
        float sigma = (float) (FlightModel.density(b.getY(), b.speedScale()) / 1.225);
        float ias = (float) (V * Math.sqrt(sigma)) * 3.6f;
        float altM = (float) ((b.getY() - FlightModel.SEA_LEVEL) / b.speedScale());
        float agl = agl(b) / b.speedScale();
        float vs = (float) st.vel.y;
        float pitch = st.pitchDeg(), roll = st.rollDeg(), hdg = (st.yawDeg() + 540) % 360;
        int x = 6, y = 20;
        g.fill(2, 16, 132, 132, 0x70000000);
        g.drawString(f, tr("ias", Math.round(ias)), x, y, WHITE);
        g.drawString(f, String.format("M %.2f", V / 340.0), x + 84, y, CYAN);
        g.drawString(f, tr("alt", Math.round(altM)), x, y + 11, WHITE);
        g.drawString(f, tr("ra", agl > 900 ? "---" : String.valueOf(Math.round(agl))), x, y + 22, agl < 60 ? AMBER : GREY);
        g.drawString(f, tr("vs", String.format("%+.1f", vs)), x, y + 33, vs < -10 ? AMBER : GREY);
        g.drawString(f, tr("hdg", Math.round(hdg)), x, y + 44, WHITE);
        g.drawString(f, String.format("θ %+.1f°  γ %+.1f°", pitch, roll), x, y + 55, GREY);
        float thr = b.throttle(), act = b.thrust();
        String ab = b.kind.swingWing && act > 1.01f ? " " + tr("ab") : "";
        g.drawString(f, tr("thr", Math.round(Math.min(thr, 1f) * 100), Math.round(Math.min(act, 1f) * 100)) + ab, x, y + 66,
                b.hasFlag(BomberEntity.F_ENGINES) ? (act > 1.01f ? AMBER : WHITE) : RED);
        g.drawString(f, b.hasFlag(BomberEntity.F_ENGINES) ? tr("eng_on") : tr("eng_off"), x, y + 77, b.hasFlag(BomberEntity.F_ENGINES) ? GREEN : RED);
        g.drawString(f, tr("hp", Math.round(b.health() / b.kind.health * 100)), x + 64, y + 77, b.health() < b.kind.health * 0.5f ? RED : GREY);
        g.drawString(f, String.format("α %.1f°  n %.2f", Math.toDegrees(st.alpha), st.gLoad), x, y + 88, b.hasFlag(BomberEntity.F_STALL) ? RED : GREY);
        g.drawString(f, String.format("%s %d", tr("ground_lbl"), b.onGroundState() ? 1 : 0), x, y + 99, GREY);
        // systems (right column)
        int rx = w - 128, ry = 20;
        g.fill(rx - 4, 16, w - 2, 98, 0x70000000);
        String gear = b.gear < 0.02f ? tr("gear_down") : b.gear > 0.98f ? tr("gear_up") : tr("gear_transit");
        g.drawString(f, gear, rx, ry, b.gear < 0.02f ? GREEN : b.gear > 0.98f ? GREY : AMBER);
        g.drawString(f, tr("flaps", Math.round(b.flaps * (b.kind.swingWing ? 40 : 35))), rx, ry + 11, b.flaps > 0.01f ? AMBER : GREY);
        g.drawString(f, b.bay > 0.98f ? tr("bay_open") : b.bay < 0.02f ? tr("bay_closed") : tr("bay_moving"), rx, ry + 22, b.bay > 0.02f ? AMBER : GREY);
        if (b.kind.swingWing) g.drawString(f, tr("sweep", Math.round(20 + 45 * b.sweep)), rx, ry + 33, CYAN);
        g.drawString(f, b.hasFlag(BomberEntity.F_BRAKES) ? tr("brakes_on") : tr("brakes_off"), rx, ry + 44, b.hasFlag(BomberEntity.F_BRAKES) ? AMBER : GREY);
        g.drawString(f, b.hasFlag(BomberEntity.F_LIGHTS) ? tr("lights_on") : tr("lights_off"), rx, ry + 55, GREY);
        g.drawString(f, b.hasFlag(BomberEntity.F_DOOR) ? tr("door_open") : tr("door_closed"), rx, ry + 66, b.hasFlag(BomberEntity.F_DOOR) ? AMBER : GREY);
        // attitude indicator: horizon line and pitch ladder around the screen centre
        int cx = w / 2, cy = h / 2;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-roll));
        float pxPerDeg = 3.0f;
        for (int p = -30; p <= 30; p += 10) {
            int yy = Math.round((pitch - p) * pxPerDeg);
            if (Math.abs(yy) > 70) continue;
            int half = p == 0 ? 60 : 22;
            int col = p == 0 ? 0xC055FF66 : 0x9055FF66;
            g.fill(-half, yy, -8, yy + 1, col);
            g.fill(8, yy, half, yy + 1, col);
            if (p != 0) g.drawString(Minecraft.getInstance().font, String.valueOf(p), half + 3, yy - 4, 0x55FF66, false);
        }
        g.pose().popPose();
        g.fill(cx - 14, cy, cx - 4, cy + 1, 0xFFFFE070);
        g.fill(cx + 4, cy, cx + 14, cy + 1, 0xFFFFE070);
        // flight path marker (velocity vector)
        if (V > 20) {
            float a = (float) Math.toDegrees(st.alpha), be = (float) Math.toDegrees(st.beta);
            int fx = cx + Math.round(be * pxPerDeg), fy = cy + Math.round(a * pxPerDeg);
            g.fill(fx - 3, fy - 3, fx + 4, fy - 2, 0xFF55FF66);
            g.fill(fx - 3, fy + 3, fx + 4, fy + 4, 0xFF55FF66);
            g.fill(fx - 3, fy - 3, fx - 2, fy + 4, 0xFF55FF66);
            g.fill(fx + 3, fy - 3, fx + 4, fy + 4, 0xFF55FF66);
        }
        // heading tape
        String ht = String.format("%03d", Math.round(hdg) % 360);
        g.fill(cx - 18, 4, cx + 18, 16, 0x90000000);
        g.drawString(f, ht, cx - f.width(ht) / 2, 6, WHITE);
    }

    private static void bombPanel(GuiGraphics g, Font f, BomberEntity b, int w, int h) {
        int x = w - 128, y = 104;
        g.fill(x - 4, y - 4, w - 2, y + 72, 0x70000000);
        int n = b.bombCount();
        g.drawString(f, tr("bombs", b.bombType().displayName(), n), x, y, n > 0 ? WHITE : GREY);
        String mode = switch (b.releaseMode()) {
            case BomberEntity.MODE_SERIES -> tr("mode_series", b.seriesLength());
            case BomberEntity.MODE_SALVO -> tr("mode_salvo");
            default -> tr("mode_single");
        };
        g.drawString(f, mode, x, y + 11, GREY);
        if (ClientEvents.ccip != null)
            g.drawString(f, tr("fall", String.format("%.1f", ClientEvents.ccipTime / b.speedScale())), x, y + 22, GREY);
        double[] s = ClientEvents.ccrp;
        if (b.target().isPresent()) {
            if (s != null) {
                double ttr = s[3];
                g.drawString(f, tr("ccrp_dist", Math.round(Math.max(0, s[0] - s[1]) / b.speedScale())), x, y + 33, CYAN);
                g.drawString(f, tr("ccrp_cross", String.format("%+.0f", s[2] / b.speedScale())), x, y + 44, Math.abs(s[2]) < 6 ? GREEN : AMBER);
                g.drawString(f, ttr > 0 ? tr("ccrp_ttr", String.format("%.1f", ttr)) : tr("ccrp_passed"), x, y + 55, ttr > 0 && ttr < 5 ? AMBER : GREY);
                // steering cue: arrow towards the target track
                int cx = w / 2, cy = h / 2 + 30;
                int off = (int) Math.max(-60, Math.min(60, -s[2] * 2));
                g.fill(cx + off - 1, cy - 8, cx + off + 1, cy + 8, 0xFF66DDFF);
                g.fill(cx - 60, cy, cx + 60, cy + 1, 0x6066DDFF);
            } else g.drawString(f, tr("ccrp_none"), x, y + 33, GREY);
        } else g.drawString(f, tr("no_target"), x, y + 33, GREY);
        if (!b.bayReady() && n > 0) g.drawString(f, tr("open_bay"), x, y + 66, AMBER);
    }

    private static void sightOverlay(GuiGraphics g, Font f, BomberEntity b, int w, int h) {
        int cx = w / 2, cy = h / 2;
        int col = 0xC0FFE070;
        g.fill(cx - 90, cy, cx - 6, cy + 1, col);
        g.fill(cx + 6, cy, cx + 90, cy + 1, col);
        g.fill(cx, cy - 90, cx + 1, cy - 6, col);
        g.fill(cx, cy + 6, cx + 1, cy + 90, col);
        for (int i = -4; i <= 4; i++) {
            if (i == 0) continue;
            g.fill(cx - 3, cy + i * 20, cx + 4, cy + i * 20 + 1, col);
            g.fill(cx + i * 20, cy - 3, cx + i * 20 + 1, cy + 4, col);
        }
        String z = tr("zoom", ClientEvents.zoom == 1 ? 2 : ClientEvents.zoom == 2 ? 4 : ClientEvents.zoom == 3 ? 8 : 14);
        g.drawString(f, z, cx + 96, cy - 4, 0xFFE070);
        String pitch = tr("sight_angle", Math.round(Minecraft.getInstance().player.getXRot()));
        g.drawString(f, pitch, cx + 96, cy + 8, 0xFFE070);
    }
}
