/*
 * Decompiled with CFR 0.152.
 */
package ru.aiforge.realwar.client;

import ru.aiforge.realwar.client.ClientRadiation;
import ru.aiforge.realwar.client.fx.ClientBlastManager;
import ru.aiforge.realwar.client.fx.TrailFx;
import ru.aiforge.realwar.network.ConventionalEffectPacket;
import ru.aiforge.realwar.network.NukeEffectPacket;
import ru.aiforge.realwar.network.RadiationSyncPacket;
import ru.aiforge.realwar.network.TracerPacket;

public final class ClientPackets {
    private ClientPackets() {
    }

    public static void nuke(NukeEffectPacket m) {
        ClientBlastManager.startNuke(m);
    }

    public static void conventional(ConventionalEffectPacket m) {
        ClientBlastManager.startConv(m);
    }

    public static void tracer(TracerPacket m) {
        TrailFx.addTracer(m);
    }

    public static void radiation(RadiationSyncPacket m) {
        ClientRadiation.set(m.rate(), m.dose());
    }
}

