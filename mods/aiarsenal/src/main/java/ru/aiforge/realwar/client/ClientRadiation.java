/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.network.chat.Component
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.util.Mth
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraftforge.client.gui.overlay.ForgeGui
 */
package ru.aiforge.realwar.client;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;

public final class ClientRadiation {
    private static float rate;
    private static float dose;
    private static long lastSync;

    private ClientRadiation() {
    }

    public static void set(float r, float d) {
        rate = r;
        dose = d;
        Minecraft mc = Minecraft.getInstance();
        lastSync = mc.level == null ? 0L : mc.level.getGameTime();
    }

    public static void reset() {
        rate = 0.0f;
        dose = 0.0f;
        lastSync = Long.MIN_VALUE;
    }

    private static boolean hasCounter(Player p) {
        for (ItemStack s : p.getInventory().items) {
            if (!s.is((Item)ModItems.GEIGER_COUNTER.get())) continue;
            return true;
        }
        return p.getOffhandItem().is((Item)ModItems.GEIGER_COUNTER.get());
    }

    private static boolean holdingCounter(Player p) {
        return p.getItemInHand(InteractionHand.MAIN_HAND).is((Item)ModItems.GEIGER_COUNTER.get()) || p.getItemInHand(InteractionHand.OFF_HAND).is((Item)ModItems.GEIGER_COUNTER.get());
    }

    private static boolean fresh(Minecraft mc) {
        return mc.level != null && mc.level.getGameTime() - lastSync < 80L;
    }

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !ClientRadiation.hasCounter((Player)p)) {
            return;
        }
        float r = ClientRadiation.fresh(mc) ? rate : 0.0f;
        double perSecond = 0.6 + 2.2 * Math.sqrt(Math.max(0.0f, r)) + 0.25 * (double)Math.min(r, 400.0f);
        perSecond = Math.min(perSecond, 45.0);
        if (mc.level.random.nextDouble() < perSecond / 20.0) {
            float pitch = 0.9f + mc.level.random.nextFloat() * 0.25f;
            mc.level.playLocalSound(p.getX(), p.getEyeY(), p.getZ(), (SoundEvent)ModSounds.GEIGER_CLICK.get(), SoundSource.PLAYERS, 0.35f, pitch, false);
        }
    }

    public static void renderHud(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || !((Boolean)RWConfig.CLIENT.geigerHud.get()).booleanValue() || !ClientRadiation.holdingCounter((Player)p)) {
            return;
        }
        float r = ClientRadiation.fresh(mc) ? rate : 0.0f;
        int pw = 126;
        int ph = 34;
        int x = w - pw - 8;
        int y = h - 66;
        g.fill(x - 1, y - 1, x + pw + 1, y + ph + 1, -14934252);
        g.fill(x, y, x + pw, y + ph, -1072688632);
        double lg = r <= 0.01f ? 0.0 : Mth.clamp((double)((Math.log10(r) + 2.0) / 6.0), (double)0.0, (double)1.0);
        int bar = (int)(lg * (double)(pw - 8));
        int color = lg < 0.35 ? -11153334 : (lg < 0.6 ? -2047936 : (lg < 0.8 ? -2066392 : -2084816));
        g.fill(x + 4, y + 24, x + pw - 4, y + 29, -14012894);
        g.fill(x + 4, y + 24, x + 4 + Math.max(1, bar), y + 29, color);
        g.drawString(mc.font, (Component)Component.translatable((String)"hud.aiarsenal.rw.rate", (Object[])new Object[]{String.format("%.2f", Float.valueOf(r))}), x + 4, y + 4, color, false);
        g.drawString(mc.font, (Component)Component.translatable((String)"hud.aiarsenal.rw.dose", (Object[])new Object[]{String.format("%.0f", Float.valueOf(dose))}), x + 4, y + 14, -3618632, false);
    }

    static {
        lastSync = Long.MIN_VALUE;
    }
}

