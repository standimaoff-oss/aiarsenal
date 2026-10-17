/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.util.Mth
 *  net.minecraftforge.client.event.ViewportEvent$ComputeCameraAngles
 */
package ru.aiforge.realwar.client.fx;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.RealWar;
import ru.aiforge.realwar.client.fx.ConvFx;
import ru.aiforge.realwar.client.fx.NukeFx;
import ru.aiforge.realwar.client.particle.FxSystem;
import ru.aiforge.realwar.network.ConventionalEffectPacket;
import ru.aiforge.realwar.network.NukeEffectPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.ViewportEvent;

public final class ClientBlastManager {
    private static final List<NukeFx> NUKES = new ArrayList<NukeFx>();
    private static final List<ConvFx> CONVS = new ArrayList<ConvFx>();
    private static final List<Later> LATER = new ArrayList<Later>();
    private static final List<Shake> SHAKES = new ArrayList<Shake>();
    private static float flashPeak;
    private static long flashStart;

    private ClientBlastManager() {
    }

    public static void startNuke(NukeEffectPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        NUKES.add(new NukeFx(m, mc));
    }

    public static void startConv(ConventionalEffectPacket m) {
        CONVS.add(new ConvFx(m));
    }

    public static void later(int delayTicks, Runnable r) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0L : mc.level.getGameTime();
        LATER.add(new Later(now + (long)Math.max(0, delayTicks), r));
    }

    public static void clear() {
        NUKES.clear();
        CONVS.clear();
        LATER.clear();
        SHAKES.clear();
        flashPeak = 0.0f;
        FxSystem.clear();
    }

    public static void tick(ClientLevel level) {
        Minecraft mc = Minecraft.getInstance();
        Iterator<NukeFx> it = NUKES.iterator();
        while (it.hasNext()) {
            try {
                if (!it.next().tick(level, mc)) continue;
                it.remove();
            }
            catch (Throwable t) {
                RealWar.LOGGER.error("\u041e\u0448\u0438\u0431\u043a\u0430 \u0432 \u043a\u043b\u0438\u0435\u043d\u0442\u0441\u043a\u043e\u043c \u044d\u0444\u0444\u0435\u043a\u0442\u0435 \u044f\u0434\u0435\u0440\u043d\u043e\u0433\u043e \u0432\u0437\u0440\u044b\u0432\u0430 \u2014 \u044d\u0444\u0444\u0435\u043a\u0442 \u043e\u0442\u043c\u0435\u043d\u0451\u043d", t);
                it.remove();
            }
        }
        Iterator<ConvFx> it2 = CONVS.iterator();
        while (it2.hasNext()) {
            try {
                if (!it2.next().tick(level, mc)) continue;
                it2.remove();
            }
            catch (Throwable t) {
                RealWar.LOGGER.error("\u041e\u0448\u0438\u0431\u043a\u0430 \u0432 \u043a\u043b\u0438\u0435\u043d\u0442\u0441\u043a\u043e\u043c \u044d\u0444\u0444\u0435\u043a\u0442\u0435 \u0432\u0437\u0440\u044b\u0432\u0430 \u2014 \u044d\u0444\u0444\u0435\u043a\u0442 \u043e\u0442\u043c\u0435\u043d\u0451\u043d", t);
                it2.remove();
            }
        }
        FxSystem.tick();
        long now = level.getGameTime();
        for (int i = 0; i < LATER.size(); ++i) {
            Later l = LATER.get(i);
            if (l.at() > now) continue;
            LATER.remove(i--);
            try {
                l.run().run();
                continue;
            }
            catch (Throwable t) {
                RealWar.LOGGER.error("\u041e\u0448\u0438\u0431\u043a\u0430 \u0432 \u043e\u0442\u043b\u043e\u0436\u0435\u043d\u043d\u043e\u043c \u043a\u043b\u0438\u0435\u043d\u0442\u0441\u043a\u043e\u043c \u0441\u043e\u0431\u044b\u0442\u0438\u0438 \u0432\u0437\u0440\u044b\u0432\u0430", t);
            }
        }
        SHAKES.removeIf(s -> (float)(now - s.start()) > s.dur() * 5.0f);
        if (flashPeak > 0.0f && now - flashStart > 600L) {
            flashPeak = 0.0f;
        }
    }

    public static void flash(ClientLevel level, float peak) {
        if (!((Boolean)RWConfig.CLIENT.flashEffect.get()).booleanValue()) {
            return;
        }
        long now = level.getGameTime();
        float cur = ClientBlastManager.flashAlpha(0.0f);
        if (peak >= cur) {
            flashPeak = peak;
            flashStart = now;
        }
    }

    public static float flashAlpha(float partial) {
        if (flashPeak <= 0.0f) {
            return 0.0f;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0.0f;
        }
        float t = (float)(mc.level.getGameTime() - flashStart) + partial;
        if (t < 0.0f) {
            return 0.0f;
        }
        if (t < 4.0f) {
            return flashPeak;
        }
        float dt = t - 4.0f;
        float a = flashPeak * (0.65f * (float)Math.exp(-dt / (28.0f * flashPeak + 8.0f)) + 0.35f * (float)Math.exp(-dt / (90.0f * flashPeak + 20.0f)));
        return a < 0.004f ? 0.0f : Mth.clamp((float)a, (float)0.0f, (float)1.0f);
    }

    public static void shake(ClientLevel level, float ampDeg, float durTicks) {
        if (!((Boolean)RWConfig.CLIENT.cameraShake.get()).booleanValue()) {
            return;
        }
        SHAKES.add(new Shake(level.getGameTime(), ampDeg, durTicks));
    }

    public static void applyShake(ViewportEvent.ComputeCameraAngles e) {
        if (SHAKES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float t = (float)mc.level.getGameTime() + (float)e.getPartialTick();
        float total = 0.0f;
        for (Shake s : SHAKES) {
            float dt = t - (float)s.start();
            if (dt < 0.0f) continue;
            total += s.amp() * (float)Math.exp(-dt / s.dur());
        }
        if (total < 0.01f) {
            return;
        }
        float n1 = Mth.sin((float)(t * 2.3f + 1.1f)) * 0.6f + Mth.sin((float)(t * 6.7f)) * 0.4f;
        float n2 = Mth.sin((float)(t * 3.1f + 4.0f)) * 0.6f + Mth.sin((float)(t * 8.9f + 2.0f)) * 0.4f;
        float n3 = Mth.sin((float)(t * 1.7f + 2.5f)) * 0.6f + Mth.sin((float)(t * 5.3f + 0.7f)) * 0.4f;
        e.setPitch(e.getPitch() + total * n1);
        e.setYaw(e.getYaw() + total * 0.6f * n2);
        e.setRoll(e.getRoll() + total * 0.8f * n3);
    }

    public static int activeNukes() {
        return NUKES.size();
    }

    private record Later(long at, Runnable run) {
    }

    private record Shake(long start, float amp, float dur) {
    }
}

