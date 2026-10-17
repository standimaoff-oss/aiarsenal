/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.KeyMapping
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.Options
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.entity.Entity
 *  net.minecraftforge.client.gui.overlay.ForgeGui
 */
package ru.aiforge.realwar.client;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.aircraft.AircraftEntity;
import ru.aiforge.realwar.client.AircraftClient;
import ru.aiforge.realwar.client.VehicleClient;
import ru.aiforge.realwar.vehicle.FlakEntity;
import ru.aiforge.realwar.vehicle.LauncherVehicle;
import ru.aiforge.realwar.vehicle.MlrsEntity;
import ru.aiforge.realwar.vehicle.SamLauncherEntity;
import ru.aiforge.realwar.vehicle.VehicleEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.gui.overlay.ForgeGui;

public final class VehicleHud {
    private static final int PANEL = -1609559024;
    private static final int EDGE = -10852784;
    private static final int TEXT = -2562872;
    private static final int DIM = -7694720;
    private static final int GREEN = -8586148;
    private static final int AMBER = -20480;
    private static final int RED = -46534;

    private VehicleHud() {
    }

    /** r15: War Thunder style ground-vehicle HUD (compass strip, round vehicle card, slot bar, grid minimap) and a
     *  collapsible control panel ([J]; shown for 8 s after mounting). */
    private static int mountedId = -1;
    private static long mountedAt;
    public static boolean hintsOpen;

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || !((Boolean)RWConfig.CLIENT.vehicleHud.get()).booleanValue()) {
            return;
        }
        Font f = mc.font;
        Entity entity = p.getVehicle();
        if (entity == null) {
            mountedId = -1;
            return;
        }
        long now = mc.level.getGameTime();
        if (entity.getId() != mountedId) {
            mountedId = entity.getId();
            mountedAt = now;
        }
        boolean showHints = hintsOpen || now - mountedAt < 160;
        if (entity instanceof AircraftEntity) {
            AircraftEntity a = (AircraftEntity)entity;
            g.pose().pushPose();
            g.pose().translate(4, h * 0.2f - 4, 0);
            g.pose().scale(0.8f, 0.8f, 1f);
            VehicleHud.aircraft(g, f, a, w, h);
            g.pose().popPose();
            int seatA = a.seatOf((Entity)p);
            int top = h;
            if (seatA >= 0) {
                // r20: bomb types as number-key slots (1 RDS-1, 2 FAB-5000, 3 FAB-1500, 4 FAB-500)
                int[] cnt = a.counts();
                String[] nm = new String[]{"\u0420\u0414\u0421-1", "\u0424\u0410\u0411-5000", "\u0424\u0410\u0411-1500", "\u0424\u0410\u0411-500"};
                java.util.List<WtHud.Slot> sl = new java.util.ArrayList<>();
                for (int i = 0; i < 4; i++) sl.add(new WtHud.Slot(0, cnt[i], cnt[i], i == a.bombSel(), 0f, nm[i]));
                top = WtHud.drawSlots(g, f, sl, nm[a.bombSel()] + " \u00d7" + cnt[a.bombSel()], 0xFFE8E8E0, w, h, ActionBar.aircraft(mc, a, seatA), Math.max(140, w - 2 * 150));
            } else {
                WtHud.drawSlots(g, f, java.util.List.of(), "", 0, w, h, ActionBar.aircraft(mc, a, seatA), Math.max(140, w - 2 * 150));
            }
            return;
        }
        if (!(entity instanceof VehicleEntity)) {
            return;
        }
        VehicleEntity v = (VehicleEntity)entity;
        int seat = v.seatOf((Entity)p);
        WtHud.compass(g, f, mc, v, w, pt);
        WtHud.card(g, f, mc, v, w, h, pt);
        int mapS = WtHud.minimap(g, f, mc, v, w, h, pt);
        // r20 (msg 20): special actions = square tiles in the weapon-slot row
        int slotTop = WtHud.slots(g, f, v, seat, w, h, ActionBar.vehicle(mc, v, seat), Math.max(120, w - 2 * (mapS + 14)));
        if (v instanceof ru.aiforge.realwar.vehicle.YarsVehicle yv) {
            VehicleHud.yarsStatus(g, f, yv, w, h);
        }
        if (v instanceof ru.aiforge.realwar.vehicle.GunVehicle gv && seat == gv.operatorSeat() && gv.isRatte()) {
            g.pose().pushPose();
            g.pose().translate(0, 14, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            VehicleHud.ratteStatus(g, f, gv, w, h);
            g.pose().popPose();
        }
        int barTop = slotTop >= h ? h - 6 : slotTop;
        if (!v.engineOn() && v.kind.drivable() && now / 10L % 2L == 0L) {
            WtHud.small(g, f, VehicleHud.tr("hud.aiarsenal.rw.engine_off"), w / 2, barTop - 8, 0xFFFF5A5A, true);
        }
    }

    /** Ratte (r9): rear 12.8 cm turret bearing + Flak 38 battery state, top-left. */
    private static void ratteStatus(GuiGraphics g, Font f, ru.aiforge.realwar.vehicle.GunVehicle gv, int w, int h) {
        net.minecraft.world.entity.Entity t = gv.flakTarget();
        String a = VehicleHud.tr("hud.aiarsenal.rw.ratte.sec") + " " + Math.round(net.minecraft.util.Mth.wrapDegrees(gv.secAz())) + "\u00b0 / "
                + String.format("%.1f", gv.secEl()) + "\u00b0" + (ru.aiforge.realwar.vehicle.GunVehicle.secClear(gv.secAz()) ? "" : "  " + VehicleHud.tr("hud.aiarsenal.rw.out_of_sector"));
        String b = VehicleHud.tr("hud.aiarsenal.rw.ratte.flak") + " " + gv.flakRounds() + "/" + ru.aiforge.realwar.vehicle.GunVehicle.FLAK_MAX + "  \u00b7  "
                + (t == null ? VehicleHud.tr("hud.aiarsenal.rw.ratte.no_air") : VehicleHud.tr("hud.aiarsenal.rw.ratte.engaging") + " " + t.getDisplayName().getString()
                + " " + Math.round(t.distanceTo(gv)) + " m");
        int tw = Math.max(f.width(a), f.width(b)) + 12;
        VehicleHud.box(g, 6, 6, tw, 26);
        g.drawString(f, a, 12, 9, -1, true);
        g.drawString(f, b, 12, 20, t == null ? -5592406 : -20480, true);
    }

    /** Launch-officer status strip above the speedometer: stage, TPK %, target range. */
    private static void yarsStatus(GuiGraphics g, Font f, ru.aiforge.realwar.vehicle.YarsVehicle y, int w, int h) {
        int stg = y.stage();
        String s = VehicleHud.tr("gui.aiarsenal.rw.yars.st" + stg);
        if (stg == 4) s = s + " " + (int)(y.erectFrac() * 100.0f) + "%";
        net.minecraft.world.phys.Vec3 t = y.mapTarget();
        if (t != null) s = s + "  \u00b7  " + VehicleHud.tr("hud.aiarsenal.rw.range") + " " + String.format("%.1f", Math.hypot(t.x - y.getX(), t.z - y.getZ()) * ru.aiforge.realwar.ordnance.Ballistics.scaleK() / 1000.0) + " " + VehicleHud.tr("hud.aiarsenal.rw.km");
        if (y.armed()) s = s + "  \u00b7  " + VehicleHud.tr("gui.aiarsenal.rw.yars.armed");
        int tw = f.width(s) + 12;
        int x = w / 2 - tw / 2;
        int yy = h - 66;
        VehicleHud.box(g, x, yy, tw, 14);
        g.drawString(f, s, x + 6, yy + 3, stg == 5 ? -8586148 : (stg <= 1 ? -46534 : -20480), true);
    }

    private static String tr(String k) {
        return Component.translatable((String)k).getString();
    }

    private static String key(KeyMapping k) {
        return k.getTranslatedKeyMessage().getString().toUpperCase();
    }

    private static void box(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, -1609559024);
        g.fill(x, y, x + w, y + 1, -10852784);
        g.fill(x, y + h - 1, x + w, y + h, -10852784);
        g.fill(x, y, x + 1, y + h, -10852784);
        g.fill(x + w - 1, y, x + w, y + h, -10852784);
    }

    /*
     * Unable to fully structure code
     */
    private static List<G> vehicleGuide(Minecraft mc, VehicleEntity v, int seat) {
        Options o = mc.options;
        ArrayList<G> l = new ArrayList<G>();
        // operator seat: seat 1 on multi-seat launchers, otherwise the driver seat 0 (hand-restored, CFR could not structure it)
        boolean operator = seat == 0;   // r9: driver = gunner = commander
        if (v.kind.drivable() && seat == 0) {
            l.add(new G(VehicleHud.key(o.keyUp) + "/" + VehicleHud.key(o.keyDown), "hud.aiarsenal.rw.g.throttle"));
            l.add(new G(VehicleHud.key(o.keyLeft) + "/" + VehicleHud.key(o.keyRight), "hud.aiarsenal.rw.g.steer"));
            l.add(new G(VehicleHud.key(VehicleClient.KEY_ENGINE), "hud.aiarsenal.rw.g.engine"));
        }
        if (v.kind.isRadar()) {
            if (seat >= 0 && operator) {
                l.add(new G(VehicleHud.key(VehicleClient.KEY_MAP), "hud.aiarsenal.rw.g.radar_open"));
            }
        } else if (v instanceof ru.aiforge.realwar.vehicle.GunVehicle gv && operator) {
            l.add(new G(net.minecraft.client.resources.language.I18n.get("hud.aiarsenal.rw.g.mouse"), "hud.aiarsenal.rw.g.aim"));
            l.add(new G(VehicleHud.key(o.keyAttack), gv.cfg.coax30() ? "hud.aiarsenal.rw.g.gun_fire_coax" : "hud.aiarsenal.rw.g.gun_fire"));
            if (gv.kind.artillery()) {
                l.add(new G(VehicleHud.key(VehicleClient.KEY_MAP), "hud.aiarsenal.rw.g.map_arty"));
                l.add(new G(VehicleHud.key(VehicleClient.KEY_LOCK), "hud.aiarsenal.rw.g.gun_auto"));
            } else {
                l.add(new G(VehicleHud.key(VehicleClient.KEY_LOCK), "hud.aiarsenal.rw.g.gun_sel"));
            }
            l.add(new G(VehicleHud.key(o.keyUse) + "/" + VehicleHud.key(VehicleClient.KEY_SIGHT), "hud.aiarsenal.rw.g.sight"));
            l.add(new G(VehicleHud.key(VehicleClient.KEY_RELOAD), "hud.aiarsenal.rw.g.reload"));
        } else if (v instanceof MlrsEntity && operator) {
            l.add(new G(VehicleHud.key(o.keyAttack), "hud.aiarsenal.rw.g.launch"));
            l.add(new G(VehicleHud.key(VehicleClient.KEY_RELOAD), "hud.aiarsenal.rw.g.reload"));
            l.add(new G(VehicleHud.key(VehicleClient.KEY_SIGHT), "hud.aiarsenal.rw.g.sight"));
            l.add(new G(VehicleHud.key(VehicleClient.KEY_MAP), "hud.aiarsenal.rw.g.map_arty"));
        } else if (v instanceof SamLauncherEntity && operator) {
            l.add(new G(VehicleHud.key(VehicleClient.KEY_MAP), "hud.aiarsenal.rw.g.radar_fire"));
            l.add(new G(VehicleHud.key(VehicleClient.KEY_LOCK) + "/" + VehicleHud.key(o.keyAttack), "hud.aiarsenal.rw.g.sam_manual"));
            l.add(new G(VehicleHud.key(VehicleClient.KEY_SIGHT), "hud.aiarsenal.rw.g.sight"));
            l.add(new G(VehicleHud.key(VehicleClient.KEY_RELOAD), "hud.aiarsenal.rw.g.reload"));
        } else if (v instanceof ru.aiforge.realwar.vehicle.YarsVehicle && operator) {
            l.add(new G(VehicleHud.key(VehicleClient.KEY_MAP), "hud.aiarsenal.rw.g.yars_map"));
            l.add(new G(VehicleHud.key(VehicleClient.KEY_LOCK), "hud.aiarsenal.rw.g.yars_lock"));
            l.add(new G(VehicleHud.key(o.keyAttack), "hud.aiarsenal.rw.g.yars_launch"));
            l.add(new G(VehicleHud.key(VehicleClient.KEY_RELOAD), "hud.aiarsenal.rw.g.reload"));
        } else if (v instanceof FlakEntity) {
            l.add(new G(VehicleHud.key(o.keyAttack), "hud.aiarsenal.rw.g.fire"));
            l.add(new G(VehicleHud.key(VehicleClient.KEY_RELOAD), "hud.aiarsenal.rw.g.reload"));
        }
        if (v.spec.seatCount() > 1) {
            l.add(new G(VehicleHud.key(VehicleClient.KEY_SEAT), "hud.aiarsenal.rw.g.seat"));
        }
        l.add(new G(VehicleHud.key(o.keyShift), "hud.aiarsenal.rw.g.exit"));
        return l;
    }

    private static List<G> aircraftGuide(Minecraft mc, AircraftEntity a, int seat) {
        boolean flight;
        Options o = mc.options;
        ArrayList<G> l = new ArrayList<G>();
        if (seat < 0) {
            l.add(new G(VehicleHud.key(o.keyUp) + VehicleHud.key(o.keyLeft) + VehicleHud.key(o.keyDown) + VehicleHud.key(o.keyRight), "hud.aiarsenal.rw.g.walk"));
            l.add(new G(VehicleHud.key(VehicleClient.KEY_SEAT), "hud.aiarsenal.rw.g.sit_door"));
            return l;
        }
        boolean bl = flight = seat == 0 || seat == 1;
        if (flight) {
            l.add(new G(VehicleHud.key(o.keyUp) + "/" + VehicleHud.key(o.keyDown), "hud.aiarsenal.rw.g.a_throttle"));
            l.add(new G(VehicleHud.key(o.keyLeft) + "/" + VehicleHud.key(o.keyRight), "hud.aiarsenal.rw.g.a_roll"));
            l.add(new G(VehicleHud.key(o.keyJump) + "/" + VehicleHud.key(AircraftClient.KEY_PITCH_DOWN), "hud.aiarsenal.rw.g.a_pitch"));
            l.add(new G(VehicleHud.key(AircraftClient.KEY_RUDDER_L) + "/" + VehicleHud.key(AircraftClient.KEY_RUDDER_R), "hud.aiarsenal.rw.g.a_rudder"));
            l.add(new G(VehicleHud.key(AircraftClient.KEY_BRAKE), "hud.aiarsenal.rw.g.a_brake"));
        }
        l.add(new G(VehicleHud.key(AircraftClient.KEY_GEAR) + " " + VehicleHud.key(AircraftClient.KEY_FLAPS) + " " + VehicleHud.key(AircraftClient.KEY_BAY), "hud.aiarsenal.rw.g.a_mech"));
        l.add(new G(VehicleHud.key(VehicleClient.KEY_ENGINE), "hud.aiarsenal.rw.g.engine"));
        l.add(new G(VehicleHud.key(o.keyAttack) + " / " + VehicleHud.key(AircraftClient.KEY_BOMBSEL), "hud.aiarsenal.rw.g.a_drop"));
        l.add(new G(VehicleHud.key(VehicleClient.KEY_SEAT), "hud.aiarsenal.rw.g.a_stand"));
        return l;
    }

    /** Compact control panel (scale 0.66) bottom-right above the minimap; collapsed to a single "[J] controls" line. */
    private static void hints(GuiGraphics g, Font f, List<G> lines, boolean open, int w, int h, int bottom) {
        if (lines.isEmpty()) return;
        float sc = 0.66f;
        if (!open) {
            String t = VehicleHud.key(VehicleClient.KEY_HINTS) + " \u2014 " + VehicleHud.tr("hud.aiarsenal.rw.g.title");
            g.pose().pushPose();
            g.pose().translate(w - 6 - f.width(t) * sc, h - bottom - 7, 0);
            g.pose().scale(sc, sc, 1f);
            g.drawString(f, t, 0, 0, 0xB0D8D8D0, true);
            g.pose().popPose();
            return;
        }
        int kw = 0, aw = 0;
        for (G e : lines) {
            kw = Math.max(kw, f.width(e.keys));
            aw = Math.max(aw, f.width(VehicleHud.tr(e.action)));
        }
        int pw = kw + aw + 16, ph = lines.size() * 10 + 6;
        g.pose().pushPose();
        g.pose().translate(w - 6 - pw * sc, h - bottom - ph * sc, 0);
        g.pose().scale(sc, sc, 1f);
        g.fill(0, 0, pw, ph, 0x80101418);
        int yy = 4;
        for (G e : lines) {
            g.drawString(f, e.keys, 5, yy, 0xFFFFC040, true);
            g.drawString(f, VehicleHud.tr(e.action), 5 + kw + 6, yy, 0xFFE0E0D8, true);
            yy += 10;
        }
        g.pose().popPose();
    }

    private static void aircraft(GuiGraphics g, Font f, AircraftEntity a, int w, int h) {
        int pw = 190;
        int ph = 54;
        int x = 6;
        int y = h - ph - 6;
        VehicleHud.box(g, x, y, pw, ph);
        double kmh = (double)a.speed() * 3.6;
        g.pose().pushPose();
        g.pose().translate((float)(x + 8), (float)(y + 7), 0.0f);
        g.pose().scale(2.6f, 2.6f, 1.0f);
        g.drawString(f, String.format("%3d", Math.round(kmh)), 0, 0, -2562872, true);
        g.pose().popPose();
        g.drawString(f, VehicleHud.tr("hud.aiarsenal.rw.kmh"), x + 64, y + 22, -7694720, false);
        g.drawString(f, VehicleHud.tr("hud.aiarsenal.rw.alt") + " " + Math.round(a.altitudeAboveGround()) + " \u043c", x + 100, y + 7, -2562872, true);
        g.drawString(f, VehicleHud.tr("hud.aiarsenal.rw.thr") + " " + Math.round(a.throttle() * 100.0f) + "%", x + 100, y + 19, -2562872, true);
        String st = (a.gearDown() ? VehicleHud.tr("hud.aiarsenal.rw.gear_dn") : VehicleHud.tr("hud.aiarsenal.rw.gear_up")) + "  " + VehicleHud.tr("hud.aiarsenal.rw.flaps") + " " + a.flaps() + "  " + VehicleHud.tr(a.bayOpen() ? "hud.aiarsenal.rw.bay_open" : "hud.aiarsenal.rw.bay_closed");
        g.drawString(f, st, x + 8, y + 32, a.gearDown() && a.speed() > 45.0f ? -20480 : -8586148, true);
        int[] c = a.counts();
        String[] nm = new String[]{"\u0420\u0414\u0421", "\u0424\u0410\u0411-5000", "\u0424\u0410\u0411-1500", "\u0424\u0410\u0411-500"};
        int sel = a.bombSel();
        String b = nm[sel] + " \u00d7" + c[sel];
        g.drawString(f, VehicleHud.tr("hud.aiarsenal.rw.bomb") + ": " + b, x + 8, y + 43, c[sel] > 0 ? -2562872 : -7694720, true);
        if (!a.engineOn() && Minecraft.getInstance().level.getGameTime() / 10L % 2L == 0L) {
            g.drawCenteredString(f, VehicleHud.tr("hud.aiarsenal.rw.engine_off"), w / 2, y - 12, -46534);
        }
        if (a.onGround() && a.speed() < 1.0f) {
            return;
        }
        if (a.altitudeAboveGround() < 60.0 && !a.gearDown() && a.speed() < 70.0f && Minecraft.getInstance().level.getGameTime() / 6L % 2L == 0L) {
            g.drawCenteredString(f, VehicleHud.tr("hud.aiarsenal.rw.gear_warn"), w / 2, y - 24, -46534);
        }
    }

    private record G(String keys, String action) {
    }
}

