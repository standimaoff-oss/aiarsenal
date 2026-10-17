package ru.aiforge.realwar.client.craft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.craft.BattleshipEntity;
import ru.aiforge.realwar.craft.CraftWeapon;
import ru.aiforge.realwar.ordnance.Ballistics;

/**
 * Bismarck fire-control panel (reply 10): range to the director aim point, state of every 38 cm turret
 * (bears / training / laid / loading), 15 cm battery readiness, flak, Arado, out-of-range warning.
 * The aim point is re-derived client-side with the same ray as the server (every 4 ticks).
 */
public final class BattleshipHud {
    private static final int GREEN = 0xFF40FF60, GREEN_D = 0xFF30C048, AMBER = 0xFFFFB020, RED = 0xFFFF4040, WHITE = 0xFFFFFFFF, GREY = 0xFF909090;
    private static Vec3 aim;
    private static int aimTick = -100;
    private static int aimShip = -1;

    private BattleshipHud() {}

    private static String tr(String k, Object... a) {
        return Component.translatable("hud.aiarsenal.craft." + k, a).getString();
    }

    public static void render(GuiGraphics g, Font f, BattleshipEntity b, int w, int h, int bot) {
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        if (b.tickCount - aimTick >= 4 || aimShip != b.getId() || aim == null) {
            aim = b.aimAlong(p.getEyePosition(), p.getLookAngle());
            aimTick = b.tickCount;
            aimShip = b.getId();
        }
        int cx = w / 2, cy = h / 2;
        // reticle
        g.fill(cx - 14, cy, cx - 4, cy + 1, GREEN);
        g.fill(cx + 4, cy, cx + 14, cy + 1, GREEN);
        g.fill(cx, cy + 4, cx + 1, cy + 14, GREEN);
        g.fill(cx, cy - 8, cx + 1, cy - 4, GREEN);
        double k = Ballistics.scaleK();
        Vec3 tl = b.toLocal(aim);
        double dBlocks = Math.hypot(aim.x - b.getX(), aim.z - b.getZ());
        Ballistics.Profile mp = b.mainProfile();
        // top-left block (the weapon list sits bottom-right, the helm panel bottom-left)
        int x = 10, y = 6;
        g.fill(x - 4, y - 4, x + 196, y + 116, 0x70000000);
        g.drawString(f, tr("bs_fc"), x, y, WHITE, true);
        g.drawString(f, tr("bs_range", String.format("%.1f", dBlocks * k / 1000.0), String.format("%.0f", dBlocks)), x, y + 11, GREEN, true);
        boolean anyOut = false;
        String[] names = {"Anton", "Bruno", "Caesar", "Dora"};
        for (int t = 0; t < BattleshipEntity.N_MAIN; t++) {
            BattleshipEntity.Mount m = BattleshipEntity.M[t];
            double dx = tl.x - m.px(), dz = tl.z - m.pz(), dy = tl.y - m.py();
            float a = (float) Math.toDegrees(Math.atan2(dx, dz));
            double e = BattleshipEntity.elevationFor(mp, m.elMax(), Math.hypot(dx, dz) * k, dy * k);
            boolean out = Double.isNaN(e);
            anyOut |= out;
            boolean arc = m.inArc(a);
            // the director solution is the server's (exact eye position / ray), synced as a bit mask
            boolean laid = (b.laidMask >> t & 1) != 0;
            int r0 = b.reload[t * 2], r1 = b.reload[t * 2 + 1];
            int loaded = (r0 == 0 ? 1 : 0) + (r1 == 0 ? 1 : 0);
            String st;
            int col;
            if (!arc) { st = tr("bs_noarc"); col = GREY; }
            else if (!laid) { st = tr("bs_training"); col = AMBER; }
            else { st = tr("bs_laid"); col = GREEN; }
            String rl = loaded == 2 ? "##" : loaded == 1 ? "#." : "..";
            int rmin = Math.min(r0, r1);
            String tail = loaded == 0 ? String.format(" %ds", (rmin + 19) / 20) : "";
            g.drawString(f, String.format("%-6s %5.1f\u00b0 %s ", names[t], b.el[t], rl) + st + tail, x, y + 24 + t * 10, col, true);
        }
        int secReady = 0;
        for (int i = 8; i < 20; i++) if (b.reload[i] == 0) secReady++;
        g.drawString(f, tr("bs_sec", secReady), x, y + 66, secReady > 0 ? GREEN_D : AMBER, true);
        g.drawString(f, tr("bs_aa"), x, y + 77, GREEN_D, true);
        int li = b.kind.loadIndex(CraftWeapon.ARADO);
        int hangar = li >= 0 ? b.ammo(li) : 0, air = b.aradosAirborne();
        g.drawString(f, tr("bs_arado", hangar, air), x, y + 88, air > 0 ? 0xFF60C0FF : WHITE, true);
        if (air > 0) g.drawString(f, tr("bs_spotting"), x, y + 99, 0xFF60C0FF, true);
        anyOut = (b.laidMask & (1 << 31)) != 0;
        if (anyOut) g.drawCenteredString(f, tr("bs_out"), cx, cy + 22, RED);
        if (CraftClient.zoom) {
            // stadia marks for the x6 sight
            for (int i = -4; i <= 4; i++) if (i != 0) g.fill(cx + i * 20, cy + 18, cx + i * 20 + 1, cy + (i % 2 == 0 ? 26 : 22), GREEN_D);
            g.drawCenteredString(f, tr("zoom", 6), cx, cy - 40, GREEN_D);
        }
    }
}
