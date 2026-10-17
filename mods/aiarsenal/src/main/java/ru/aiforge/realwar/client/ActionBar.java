package ru.aiforge.realwar.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import ru.aiforge.realwar.aircraft.AircraftEntity;
import ru.aiforge.realwar.client.craft.CraftClient;
import ru.aiforge.realwar.craft.BoatEntity;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.craft.CraftKind;
import ru.aiforge.realwar.craft.CraftWeapon;
import ru.aiforge.realwar.craft.HeliEntity;
import ru.aiforge.realwar.craft.PlaneEntity;
import ru.aiforge.realwar.vehicle.FlakEntity;
import ru.aiforge.realwar.vehicle.GunVehicle;
import ru.aiforge.realwar.vehicle.MlrsEntity;
import ru.aiforge.realwar.vehicle.SamLauncherEntity;
import ru.aiforge.realwar.vehicle.VehicleEntity;
import ru.aiforge.realwar.vehicle.YarsVehicle;

/**
 * r20 bottom action bar (War Thunder style): one row of small "[key] action" chips above the weapon slots, listing
 * ONLY the special functions the current vehicle really has - smoke grenades, map, sight, hover / manoeuvre mode,
 * gear, flaps, flares, bomb load, engine, seat... Obvious controls (WASD, mouse aim, LMB fire, Shift exit, number
 * keys - those are printed on the slots) are never listed. A chip lights green while its mode is on and shows a
 * count where it matters (smoke grenades, flares). [J] hides / shows the bar.
 */
public final class ActionBar {
    public static boolean hidden;

    /** r20 (msg 20): a square tile like the weapon slots - key in the top strip, icon, optional count. No text. */
    public record Chip(String key, int icon, int count, int state) {
        public static final int NORMAL = 0, ACTIVE = 1, DIM = 2, WARN = 3;
    }

    /** Icon cells of textures/gui/actions.png (tools/blender/action_icons_r20.py). */
    public static final int I_SMOKE = 0, I_SIGHT = 1, I_MAP = 2, I_MAP_ARTY = 3, I_AUTO = 4, I_RELOAD = 5, I_RADAR = 6, I_LOCK = 7,
            I_ENGINE = 8, I_SEAT = 9, I_HOVER = 10, I_FLARES = 11, I_GEAR = 12, I_ZOOM = 13, I_LOOK = 14, I_MANEUVER = 15,
            I_FLAPS = 16, I_LOADOUT = 17, I_BLOW = 18, I_FLOOD = 19, I_DEPTH = 20, I_PERISCOPE = 21, I_BRAKE = 22, I_BAY = 23,
            I_NOSE_DOWN = 24, I_RUDDER = 25, I_PERSON = 26, I_HIDDEN = 27, I_YARS = 28, I_TELEGRAPH = 29, I_ENGINES = 30, I_OPTICS = 31;
    private static final net.minecraft.resources.ResourceLocation SHEET = new net.minecraft.resources.ResourceLocation("aiarsenal", "textures/gui/actions.png");
    public static final int TW = 20, TH = 26, GAP = 2;

    private ActionBar() {
    }

    static String key(KeyMapping k) {
        com.mojang.blaze3d.platform.InputConstants.Key in = k.getKey();
        if (in.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM) {
            switch (in.getValue()) {      // compact WT-style names for the long modifier keys
                case 340 -> { return "LSHIFT"; }
                case 344 -> { return "RSHIFT"; }
                case 341 -> { return "LCTRL"; }
                case 345 -> { return "RCTRL"; }
                case 342 -> { return "LALT"; }
                case 346 -> { return "RALT"; }
                case 32 -> { return "SPACE"; }
                case 258 -> { return "TAB"; }
                case 280 -> { return "CAPS"; }
                default -> { }
            }
        }
        String s = k.getTranslatedKeyMessage().getString().toUpperCase();
        return s.length() > 8 ? s.substring(0, 8) : s;
    }

    private static String tr(String k, Object... a) {
        return Component.translatable("hud.aiarsenal.rw.act." + k, a).getString();
    }

    private static Chip c(KeyMapping k, int icon, int st) {
        return new Chip(key(k), icon, -1, st);
    }

    private static Chip c(KeyMapping k, int icon, int count, int st) {
        return new Chip(key(k), icon, count, st);
    }

    // ===================================================================================== content

    /** r22: small arms - R reload (lit while reloading), B fire selector (lit on automatic) */
    public static List<Chip> gun(ru.aiforge.realwar.handheld.GunKind k, boolean auto, boolean reloading) {
        List<Chip> l = new java.util.ArrayList<>();
        l.add(c(VehicleClient.KEY_RELOAD, I_RELOAD, reloading ? Chip.ACTIVE : Chip.NORMAL));
        if (k.selector()) l.add(c(AircraftClient.KEY_BRAKE, I_AUTO, auto ? Chip.ACTIVE : Chip.NORMAL));
        else if (k == ru.aiforge.realwar.handheld.GunKind.RPG7) l.add(c(AircraftClient.KEY_BRAKE, I_SIGHT, auto ? Chip.ACTIVE : Chip.NORMAL));   // r23: PGO-7V3 on/off
        return l;
    }

    public static List<Chip> vehicle(Minecraft mc, VehicleEntity v, int seat) {
        List<Chip> l = new ArrayList<>();
        boolean driver = seat == 0;
        if (v instanceof GunVehicle gv && seat == gv.operatorSeat()) {
            int sm = GunVehicle.smokeMax(gv.kind);
            if (sm > 0) l.add(c(AircraftClient.KEY_BRAKE, I_SMOKE, gv.smoke(), gv.smoke() > 0 ? Chip.NORMAL : Chip.DIM));
            l.add(c(VehicleClient.KEY_SIGHT, I_SIGHT, ru.aiforge.realwar.client.sight.SightClient.active() ? Chip.ACTIVE : Chip.NORMAL));
            if (ru.aiforge.realwar.client.sight.SightClient.active() && ru.aiforge.realwar.client.sight.Optics.channels(gv.kind) > 0)
                l.add(c(VehicleClient.KEY_OPTICS, I_OPTICS, ru.aiforge.realwar.client.sight.Optics.active() > 0 ? Chip.ACTIVE : Chip.NORMAL));
            l.add(c(VehicleClient.KEY_MAP, gv.kind.artillery() ? I_MAP_ARTY : I_MAP, Chip.NORMAL));
            if (gv.kind.artillery()) l.add(c(VehicleClient.KEY_LOCK, I_AUTO, gv.autoAim() ? Chip.ACTIVE : Chip.NORMAL));
            l.add(c(VehicleClient.KEY_RELOAD, I_RELOAD, Chip.NORMAL));
        } else if (v instanceof MlrsEntity && driver) {
            if (ru.aiforge.realwar.client.sight.SightClient.active() && ru.aiforge.realwar.client.sight.Optics.channels(v.kind) > 0)
                l.add(c(VehicleClient.KEY_OPTICS, I_OPTICS, ru.aiforge.realwar.client.sight.Optics.active() > 0 ? Chip.ACTIVE : Chip.NORMAL));
            l.add(c(VehicleClient.KEY_SIGHT, I_SIGHT, Chip.NORMAL));
            l.add(c(VehicleClient.KEY_MAP, I_MAP_ARTY, Chip.NORMAL));
            l.add(c(VehicleClient.KEY_RELOAD, I_RELOAD, Chip.NORMAL));
        } else if (v instanceof SamLauncherEntity && driver) {
            if (ru.aiforge.realwar.client.sight.SightClient.active() && ru.aiforge.realwar.client.sight.Optics.channels(v.kind) > 0)
                l.add(c(VehicleClient.KEY_OPTICS, I_OPTICS, ru.aiforge.realwar.client.sight.Optics.active() > 0 ? Chip.ACTIVE : Chip.NORMAL));
            l.add(c(VehicleClient.KEY_MAP, I_RADAR, Chip.NORMAL));
            l.add(c(VehicleClient.KEY_LOCK, I_LOCK, Chip.NORMAL));
            l.add(c(VehicleClient.KEY_SIGHT, I_SIGHT, Chip.NORMAL));
            l.add(c(VehicleClient.KEY_RELOAD, I_RELOAD, Chip.NORMAL));
        } else if (v instanceof YarsVehicle && driver) {
            l.add(c(VehicleClient.KEY_MAP, I_MAP_ARTY, Chip.NORMAL));
            l.add(c(VehicleClient.KEY_LOCK, I_YARS, Chip.NORMAL));
            l.add(c(VehicleClient.KEY_RELOAD, I_RELOAD, Chip.NORMAL));
        } else if (v instanceof FlakEntity) {
            l.add(c(VehicleClient.KEY_RELOAD, I_RELOAD, Chip.NORMAL));
        } else if (v.kind.isRadar() && driver) {
            l.add(c(VehicleClient.KEY_MAP, I_RADAR, Chip.NORMAL));
        }
        if (v.kind.drivable() && driver) l.add(c(VehicleClient.KEY_ENGINE, I_ENGINE, v.engineOn() ? Chip.ACTIVE : Chip.WARN));
        if (v.spec.seatCount() > 1) l.add(c(VehicleClient.KEY_SEAT, I_SEAT, Chip.NORMAL));
        return l;
    }

    public static List<Chip> craft(Minecraft mc, CraftEntity cr, int seat) {
        List<Chip> l = new ArrayList<>();
        boolean flares = false;
        int flareN = 0;
        for (int i = 0; i < cr.kind.loads.length; i++) {
            if (cr.kind.loads[i].weapon() == CraftWeapon.FLARES) {
                flares = true;
                flareN += cr.ammo(i);
            }
        }
        if (cr instanceof HeliEntity) {
            if (seat == 0) {
                l.add(c(AircraftClient.KEY_GEAR, I_HOVER, cr.flag(CraftEntity.FLAG_STAB) ? Chip.ACTIVE : Chip.NORMAL));
                if (flares) l.add(c(AircraftClient.KEY_BRAKE, I_FLARES, flareN, flareN > 0 ? Chip.NORMAL : Chip.DIM));
                if (cr.kind == CraftKind.KA52) l.add(c(AircraftClient.KEY_BAY, I_GEAR, cr.flag(CraftEntity.FLAG_GEAR) ? Chip.ACTIVE : Chip.NORMAL));
                l.add(c(VehicleClient.KEY_SIGHT, I_ZOOM, CraftClient.zoom ? Chip.ACTIVE : Chip.NORMAL));
                l.add(c(CraftClient.KEY_FREELOOK, I_LOOK, Chip.NORMAL));
                l.add(c(VehicleClient.KEY_ENGINE, I_ENGINES, cr.engineOn() ? Chip.ACTIVE : Chip.WARN));
            } else if (flares) {
                l.add(c(AircraftClient.KEY_BRAKE, I_FLARES, flareN, flareN > 0 ? Chip.NORMAL : Chip.DIM));
            }
        } else if (cr instanceof PlaneEntity pl) {
            if (seat == 0) {
                boolean jet = cr.kind.plane.jet();
                l.add(c(AircraftClient.KEY_GEAR, I_GEAR, pl.gearDown() ? Chip.ACTIVE : Chip.NORMAL));
                l.add(c(AircraftClient.KEY_FLAPS, jet ? I_MANEUVER : I_FLAPS, cr.flag(CraftEntity.FLAG_STAB) ? Chip.ACTIVE : Chip.NORMAL));
                if (flares) l.add(c(AircraftClient.KEY_BRAKE, I_FLARES, flareN, flareN > 0 ? Chip.NORMAL : Chip.DIM));
                if (!pl.airborne()) l.add(c(VehicleClient.KEY_RELOAD, I_LOADOUT, Chip.NORMAL));
                l.add(c(CraftClient.KEY_FREELOOK, I_LOOK, Chip.NORMAL));
                l.add(c(VehicleClient.KEY_ENGINE, I_ENGINE, cr.engineOn() ? Chip.ACTIVE : Chip.WARN));
            }
        } else if (cr.kind.isSub()) {
            if (seat == 0) {
                l.add(c(mc.options.keyJump, I_BLOW, Chip.NORMAL));
                l.add(c(AircraftClient.KEY_PITCH_DOWN, I_FLOOD, Chip.NORMAL));
                l.add(c(AircraftClient.KEY_GEAR, I_DEPTH, cr.flag(CraftEntity.FLAG_STAB) ? Chip.ACTIVE : Chip.NORMAL));
                l.add(c(VehicleClient.KEY_SIGHT, I_PERISCOPE, cr.flag(CraftEntity.FLAG_PERISCOPE) ? Chip.ACTIVE : Chip.NORMAL));
                l.add(c(VehicleClient.KEY_ENGINE, I_ENGINES, cr.engineOn() ? Chip.ACTIVE : Chip.WARN));
            }
        } else if (cr instanceof BoatEntity) {
            if (seat == 0 || cr.kind != CraftKind.BISMARCK) l.add(c(VehicleClient.KEY_SIGHT, I_SIGHT, CraftClient.zoom ? Chip.ACTIVE : Chip.NORMAL));
            if (seat == 0) l.add(c(VehicleClient.KEY_ENGINE, cr.kind == CraftKind.BISMARCK ? I_TELEGRAPH : I_ENGINES, cr.engineOn() ? Chip.ACTIVE : Chip.WARN));
        }
        l.add(c(VehicleClient.KEY_SEAT, I_SEAT, Chip.NORMAL));
        return l;
    }

    public static List<Chip> aircraft(Minecraft mc, AircraftEntity a, int seat) {
        List<Chip> l = new ArrayList<>();
        if (seat < 0) {
            l.add(c(VehicleClient.KEY_SEAT, I_PERSON, Chip.NORMAL));
            return l;
        }
        if (seat == 0 || seat == 1) {
            l.add(c(AircraftClient.KEY_GEAR, I_GEAR, a.gearDown() ? Chip.ACTIVE : Chip.NORMAL));
            l.add(c(AircraftClient.KEY_FLAPS, I_FLAPS, a.flaps(), a.flaps() > 0 ? Chip.ACTIVE : Chip.NORMAL));
            l.add(c(AircraftClient.KEY_BRAKE, I_BRAKE, Chip.NORMAL));
            l.add(c(AircraftClient.KEY_PITCH_DOWN, I_NOSE_DOWN, Chip.NORMAL));
            l.add(new Chip(key(AircraftClient.KEY_RUDDER_L) + "/" + key(AircraftClient.KEY_RUDDER_R), I_RUDDER, -1, Chip.NORMAL));
        }
        l.add(c(AircraftClient.KEY_BAY, I_BAY, a.bayOpen() ? Chip.ACTIVE : Chip.NORMAL));
        l.add(c(VehicleClient.KEY_ENGINE, I_ENGINES, a.engineOn() ? Chip.ACTIVE : Chip.WARN));
        l.add(c(VehicleClient.KEY_SEAT, I_PERSON, Chip.NORMAL));
        return l;
    }

    // ===================================================================================== drawing

    /** Width of a tile row (GUI px). */
    public static int rowW(int n) {
        return n <= 0 ? 0 : n * TW + (n - 1) * GAP;
    }

    /** r20 (msg 20): square tiles in the weapon-slot row - [key] strip on top, icon, count corner, coloured frame for
     *  on / warning / unavailable. [J] collapses them to one dim tile. */
    public static void drawTiles(GuiGraphics g, Font f, List<Chip> chips, int x0, int y0) {
        for (int i = 0; i < chips.size(); i++) {
            Chip ch = chips.get(i);
            int x = x0 + i * (TW + GAP);
            g.fill(x, y0, x + TW, y0 + 6, 0xB0303438);
            g.fill(x, y0 + 6, x + TW, y0 + TH, ch.state == Chip.ACTIVE ? 0xA0183A1C : 0x9A1A1E22);
            // key: digits / letters at 0.6, long names (LALT, SPACE) shrink to fit the 20 px strip
            float ks = Math.min(0.6f, (TW - 2) / (float) Math.max(1, f.width(ch.key)));
            g.pose().pushPose();
            g.pose().translate(x + TW / 2f - f.width(ch.key) * ks / 2f, y0 + 3f - 4.5f * ks, 0);
            g.pose().scale(ks, ks, 1f);
            g.drawString(f, ch.key, 0, 0, ch.state == Chip.DIM ? 0xFF8A8E8A : 0xFFE8E8E0, false);
            g.pose().popPose();
            int col = switch (ch.state) {
                case Chip.ACTIVE -> 0xFF7CFF7C;
                case Chip.DIM -> 0xFF62666A;
                case Chip.WARN -> 0xFFFFB040;
                default -> 0xFFD8DCD8;
            };
            icon(g, ch.icon, x + 3, y0 + 8, 14, col);
            if (ch.count >= 0) {
                String c = Integer.toString(ch.count);
                g.pose().pushPose();
                g.pose().translate(x + TW - 1 - f.width(c) * 0.55f, y0 + 21f, 0);
                g.pose().scale(0.55f, 0.55f, 1f);
                g.drawString(f, c, 0, 0, ch.count == 0 ? 0xFFFF5A5A : 0xFFFFFFFF, true);
                g.pose().popPose();
            }
            if (ch.state == Chip.ACTIVE || ch.state == Chip.WARN) {
                int fc = ch.state == Chip.ACTIVE ? 0xFF5AD85A : 0xFFE89A30;
                g.fill(x - 1, y0 - 1, x + TW + 1, y0, fc);
                g.fill(x - 1, y0 + TH, x + TW + 1, y0 + TH + 1, fc);
                g.fill(x - 1, y0, x, y0 + TH, fc);
                g.fill(x + TW, y0, x + TW + 1, y0 + TH, fc);
            }
        }
    }

    /** One 32 px cell of the icon sheet drawn at size s, tinted. */
    public static void icon(GuiGraphics g, int idx, int x, int y, int s, int argb) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.setColor(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, ((argb >>> 24) & 255) / 255f);
        g.blit(SHEET, x, y, s, s, (idx % 8) * 32, (idx / 8) * 32, 32, 32, 256, 128);
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** What is actually drawn: the chips, or (J pressed) a single dim tile that brings them back. */
    public static List<Chip> visible(List<Chip> chips) {
        if (chips.isEmpty() || !hidden) return chips;
        List<Chip> l = new ArrayList<>();
        l.add(new Chip(key(VehicleClient.KEY_HINTS), I_HIDDEN, -1, Chip.DIM));
        return l;
    }
}
