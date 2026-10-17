package ru.aiforge.realwar.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.damage.DamageState;
import ru.aiforge.realwar.damage.VehicleDamage;
import ru.aiforge.realwar.vehicle.VehicleEntity;

import static ru.aiforge.realwar.damage.DamageState.*;

/** r18: War Thunder style module damage read-out - tinted silhouette parts, damaged module list, repair timer. */
public final class DamageHud {
    public static final int YEL = 0xFFE8B030, RED = 0xFFE04030, GRN = 0xFF7CD050, TXT = 0xFFD8DCD0;

    private DamageHud() {
    }

    static int col(DamageState s, int m) {
        int l = s.level(m);
        if (l == 2) return (System.currentTimeMillis() / 300 % 2 == 0) ? RED : 0xFF901818;
        return l == 1 ? YEL : 0;
    }

    /** Overlays on the WtHud hull card silhouette (hull up = front). */
    public static void vehicleCard(GuiGraphics g, Font f, VehicleEntity v, int cx, int cy, int hw, int hl, int R, boolean tracked) {
        DamageState s = v.dmg;
        if (tracked) {
            int cl = col(s, TRACK_L), cr = col(s, TRACK_R);
            // +x = vehicle left = screen left
            if (cl != 0) g.fill(cx - hw, cy - hl, cx - hw + 4, cy + hl, cl);
            if (cr != 0) g.fill(cx + hw - 4, cy - hl, cx + hw, cy + hl, cr);
        }
        int ce = col(s, ENGINE);
        boolean front = v.kind == ru.aiforge.realwar.vehicle.VehicleKind.BRADLEY || !tracked;
        if (ce != 0) {
            if (front) g.fill(cx - hw + 5, cy - hl + 5, cx + hw - 5, cy - hl + 11, ce);
            else g.fill(cx - hw + 5, cy + hl - 9, cx + hw - 5, cy + hl - 3, ce);
        }
        if (s.burning() && System.currentTimeMillis() / 250 % 2 == 0) g.fill(cx - hw, cy - hl, cx + hw, cy + hl, 0x60FF6010);
        if (s.dead()) g.fill(cx - hw, cy - hl, cx + hw, cy + hl, 0xA0101010);
        list(g, f, v, s, 8, cy - R - 12, false);
    }

    /** Module list (bottom-up from y) + repair progress. */
    static void list(GuiGraphics g, Font f, net.minecraft.world.entity.Entity e, DamageState s, int x, int y, boolean right) {
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        if (s.dead()) rows.add(new Object[]{Component.translatable("hud.aiarsenal.rw.dmg.state.dead").getString(), RED});
        if (s.burning() && !s.dead()) rows.add(new Object[]{Component.translatable("hud.aiarsenal.rw.dmg.state.fire").getString(), RED});
        for (int m = 0; m < N; m++) {
            if (m == FIRE || m == DEAD || m == TURRET_GONE) continue;
            int l = s.level(m);
            if (l == 0) continue;
            rows.add(new Object[]{VehicleDamage.modName(e, m).getString(), l == 2 ? RED : YEL});
        }
        int open = s.openHoles();
        if (open > 0) rows.add(new Object[]{Component.translatable("hud.aiarsenal.rw.dmg.state.holes", open).getString(), YEL});
        if (s.flood > 1f && e instanceof CraftEntity c && c.kind.water != null) {
            int pct = Math.round(100f * s.flood / c.kind.water.mass());
            rows.add(new Object[]{Component.translatable("hud.aiarsenal.rw.dmg.state.flood", pct).getString(), pct > 25 ? RED : YEL});
        }
        if (s.swapTo >= 0 && e instanceof ru.aiforge.realwar.vehicle.VehicleEntity ve) {
            int[] r = ru.aiforge.realwar.damage.Crew.roles(ve.kind);
            if (s.swapTo < r.length && s.swapFrom >= 0 && s.swapFrom < r.length) {
                rows.add(new Object[]{Component.translatable("hud.aiarsenal.rw.crew.swap", ru.aiforge.realwar.damage.Crew.role(r[s.swapFrom]),
                        ru.aiforge.realwar.damage.Crew.role(r[s.swapTo]), (s.swapTicks + 19) / 20).getString(), GRN});
            }
        }
        if (s.repairMod >= 0) {
            int sec = (s.repairTicks + 19) / 20;
            rows.add(new Object[]{Component.translatable("hud.aiarsenal.rw.dmg.state.repair", VehicleDamage.modName(e, s.repairMod), sec).getString(), GRN});
        }
        if (rows.isEmpty()) return;
        float sc = 0.75f;
        int yy = y;
        for (int i = rows.size() - 1; i >= 0; i--) {
            String t = (String) rows.get(i)[0];
            int c = (Integer) rows.get(i)[1];
            float tw = f.width(t) * sc;
            float xx = right ? x - tw : x;
            g.fill((int) xx - 2, yy - 1, (int) (xx + tw) + 2, yy + 7, 0x70000000);
            WtHud.small(g, f, t, xx, yy, c, false);
            yy -= 9;
        }
        if (s.repairMod >= 0) {
            float fr = s.repairFrac();
            int bx = right ? x - 60 : x, by = y + 8;
            g.fill(bx, by, bx + 60, by + 2, 0x80000000);
            g.fill(bx, by, bx + (int) (60 * fr), by + 2, GRN);
        }
    }

    /** Aircraft / ships: list on the right edge, above the bottom panels. */
    public static void craftPanel(GuiGraphics g, Font f, CraftEntity c, int w, int h) {
        DamageState s = c.dmg;
        if (!s.any() && s.openHoles() == 0 && s.flood <= 1f && s.repairMod < 0) return;
        list(g, f, c, s, w - 8, h / 2, true);
    }

    static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
