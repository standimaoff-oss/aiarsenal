/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 */
package ru.aiforge.realwar.client.sight;

import ru.aiforge.realwar.radar.RadarNet;
import ru.aiforge.realwar.radar.RadarReplyPacket;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class RadarClient {
    public static int vehId = -1;
    public static boolean linked;
    public static boolean self;
    public static List<float[]> sources;
    public static List<RadarNet.Contact> contacts;
    public static long stamp;

    private RadarClient() {
    }

    public static void accept(RadarReplyPacket m) {
        vehId = m.vehId();
        linked = m.linked();
        self = m.self();
        sources = m.sources();
        contacts = m.contacts();
        Minecraft mc = Minecraft.getInstance();
        stamp = mc.level == null ? 0L : mc.level.getGameTime();
    }

    public static void reset() {
        vehId = -1;
        linked = false;
        self = false;
        sources = List.of();
        contacts = List.of();
    }

    static {
        sources = List.of();
        contacts = List.of();
    }
}

