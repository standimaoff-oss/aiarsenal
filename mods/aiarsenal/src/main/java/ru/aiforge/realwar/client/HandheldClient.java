package ru.aiforge.realwar.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.realwar.handheld.HandheldKind;
import ru.aiforge.realwar.handheld.LauncherWeaponItem;

/** Shoulder-launcher aiming: optic zoom (Igla x2, Javelin CLU x4) and the seeker/lock HUD with control hints. */
@Mod.EventBusSubscriber(modid = "aiarsenal", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HandheldClient {
    private HandheldClient() {
    }

    @SubscribeEvent
    public static void fov(ComputeFovModifierEvent e) {
        Player p = e.getPlayer();
        if (p.isUsingItem() && p.getUseItem().getItem() instanceof LauncherWeaponItem w && w.kind.guided()) {
            e.setNewFovModifier(e.getNewFovModifier() * (w.kind == HandheldKind.JAVELIN ? 0.25f : 0.5f));
        }
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.options.hideGui) return;
        ItemStack st = p.getMainHandItem();
        if (!(st.getItem() instanceof LauncherWeaponItem lw)) return;
        Font f = mc.font;
        HandheldKind k = lw.kind;
        int cx = w / 2, cy = h / 2;
        boolean aiming = p.isUsingItem() && p.getUseItem() == st;
        if (k.guided() && aiming) {
            int lock = LauncherWeaponItem.lock(st);
            int col = lock >= 100 ? 0xFFFF3030 : (lock > 0 ? 0xFFFFC020 : 0xFF40FF60);
            // seeker field-of-view brackets (shrink while locking)
            int r = (int) (h * (k == HandheldKind.JAVELIN ? 0.16 : 0.11) * (1.0 - 0.45 * lock / 100.0));
            int L = Math.max(6, r / 3);
            g.fill(cx - r, cy - r, cx - r + L, cy - r + 1, col);
            g.fill(cx - r, cy - r, cx - r + 1, cy - r + L, col);
            g.fill(cx + r - L, cy - r, cx + r, cy - r + 1, col);
            g.fill(cx + r - 1, cy - r, cx + r, cy - r + L, col);
            g.fill(cx - r, cy + r - 1, cx - r + L, cy + r, col);
            g.fill(cx - r, cy + r - L, cx - r + 1, cy + r, col);
            g.fill(cx + r - L, cy + r - 1, cx + r, cy + r, col);
            g.fill(cx + r - 1, cy + r - L, cx + r, cy + r, col);
            if (k == HandheldKind.JAVELIN) {
                // CLU reticle: long crosshair lines
                g.fill(cx - w / 3, cy, cx - r - 4, cy + 1, 0x8040FF60);
                g.fill(cx + r + 4, cy, cx + w / 3, cy + 1, 0x8040FF60);
                g.fill(cx, cy - h / 3, cx + 1, cy - r - 4, 0x8040FF60);
                g.fill(cx, cy + r + 4, cx + 1, cy + h / 3, 0x8040FF60);
            }
            String state;
            if (lock >= 100) state = Component.translatable("hud.aiarsenal.hh.locked").getString();
            else if (lock > 0) state = Component.translatable("hud.aiarsenal.hh.locking", lock).getString();
            else state = Component.translatable("hud.aiarsenal.hh.search").getString();
            g.drawCenteredString(f, state, cx, cy + r + 6, col);
            int tid = LauncherWeaponItem.target(st);
            Entity t = tid >= 0 ? p.level().getEntity(tid) : null;
            String info = null;
            if (t != null) info = t.getDisplayName().getString() + "  " + String.format("%.0f", t.distanceTo(p)) + " m";
            else if (tid == -1 && lock > 0 && st.hasTag()) {
                double d = Math.sqrt(p.distanceToSqr(st.getTag().getDouble("PtX"), st.getTag().getDouble("PtY"), st.getTag().getDouble("PtZ")));
                info = Component.translatable("hud.aiarsenal.hh.point").getString() + "  " + String.format("%.0f", d) + " m";
            }
            if (info != null) g.drawCenteredString(f, info, cx, cy - r - 12, col);
            if (k == HandheldKind.JAVELIN) g.drawString(f, "TOP", cx + r + 6, cy - r, 0xFF40FF60, true);
        }
        // control hints at the bottom (same style as the vehicle HUD guide)
        String ammo = Component.translatable("item.aiarsenal." + k.ammo).getString();
        String hint;
        if (k == HandheldKind.RPG7) {
            hint = Component.translatable(LauncherWeaponItem.loaded(st) ? "hud.aiarsenal.hh.rpg_fire" : "hud.aiarsenal.hh.rpg_reload", ammo).getString();
        } else {
            hint = Component.translatable("hud.aiarsenal.hh.guided_hint", ammo).getString();
        }
        int tw = f.width(hint) + 10;
        int y = h - 59;
        g.fill(cx - tw / 2, y - 3, cx + tw / 2, y + 10, 0x90000000);
        g.drawCenteredString(f, hint, cx, y, 0xFFE0E0E0);
        if (k == HandheldKind.RPG7 && aiming) {
            int used = p.getTicksUsingItem();
            int bw = 80;
            float fr = Math.min(1f, used / 44f);
            g.fill(cx - bw / 2, cy + 14, cx + bw / 2, cy + 18, 0x80000000);
            g.fill(cx - bw / 2, cy + 14, cx - bw / 2 + (int) (bw * fr), cy + 18, 0xFFFFC020);
        }
    }
}
