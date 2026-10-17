/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.CameraType
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.core.BlockPos
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.phys.Vec3
 *  net.minecraftforge.client.event.ViewportEvent$ComputeFov
 */
package ru.aiforge.realwar.client.sight;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.client.sight.MapScreen;
import ru.aiforge.realwar.network.ModNetwork;
import ru.aiforge.realwar.network.SetTargetPacket;
import ru.aiforge.realwar.ordnance.Ballistics;
import ru.aiforge.realwar.vehicle.LauncherVehicle;
import ru.aiforge.realwar.vehicle.MlrsEntity;
import ru.aiforge.realwar.vehicle.SamLauncherEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

public final class SightClient {
    public static final float[] ZOOM_ARTY = new float[]{1.0f, 3.0f, 6.0f};
    public static final float[] ZOOM_SAM = new float[]{1.0f, 2.0f, 4.0f};
    public static int zoomIdx;
    public static float fovDeg;
    private static double origSens;
    private static CameraType origCam;

    private SightClient() {
    }

    public static boolean active() {
        return zoomIdx > 0;
    }

    public static float zoom(LauncherVehicle v) {
        float[] z = v instanceof SamLauncherEntity ? ZOOM_SAM : ZOOM_ARTY;
        return z[Math.min(zoomIdx, z.length - 1)];
    }

    public static boolean isOperator(Minecraft mc, Object veh) {
        LauncherVehicle lv;
        return mc.player != null && veh instanceof LauncherVehicle && (lv = (LauncherVehicle)(veh)).seatOf((Entity)mc.player) == lv.operatorSeat();
    }

    public static void cycle(LauncherVehicle v) {
        Minecraft mc = Minecraft.getInstance();
        int n = (v instanceof SamLauncherEntity ? ZOOM_SAM : ZOOM_ARTY).length;
        SightClient.setZoom(mc, v, (zoomIdx + 1) % n);
    }

    private static void setZoom(Minecraft mc, LauncherVehicle v, int idx) {
        boolean was = SightClient.active();
        zoomIdx = idx;
        if (SightClient.active() && !was) {
            origCam = mc.options.getCameraType();
            origSens = (Double)mc.options.sensitivity().get();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        if (SightClient.active()) {
            mc.options.sensitivity().set(Math.min(1.0, origSens / ((double)SightClient.zoom(v) * 0.85)));
        } else if (was) {
            SightClient.restore(mc);
        }
    }

    private static void restore(Minecraft mc) {
        if (origSens >= 0.0) {
            mc.options.sensitivity().set(origSens);
        }
        if (origCam != null) {
            mc.options.setCameraType(origCam);
        }
        origSens = -1.0;
        origCam = null;
    }

    public static void off() {
        Minecraft mc = Minecraft.getInstance();
        if (SightClient.active()) {
            zoomIdx = 0;
            SightClient.restore(mc);
        }
    }

    public static void tick(Minecraft mc) {
        LauncherVehicle v;
        Entity entity;
        if (!SightClient.active()) {
            return;
        }
        if (mc.player == null || !((entity = mc.player.getVehicle()) instanceof LauncherVehicle) || !SightClient.isOperator(mc, (v = (LauncherVehicle)entity))) {
            SightClient.off();
        } else if (mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    public static void onFov(ViewportEvent.ComputeFov e) {
        Entity entity;
        if (!e.usedConfiguredFov()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double f = e.getFOV();
        if (SightClient.active() && mc.player != null && (entity = mc.player.getVehicle()) instanceof LauncherVehicle) {
            LauncherVehicle v = (LauncherVehicle)entity;
            f = Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(f) / 2.0) / (double)SightClient.zoom(v)));
            e.setFOV(f);
        }
        fovDeg = (float)f;
    }

    private static double[] ranges(LauncherVehicle v) {
        if (v instanceof ru.aiforge.realwar.vehicle.GunVehicle gv) {
            return gv.rangesBlocks();
        }
        if (v instanceof MlrsEntity) {
            MlrsEntity m = (MlrsEntity)v;
            double k = Ballistics.scaleK();
            double max = 0.0;
            for (double e = (double)v.kind.elMin; e <= (double)v.kind.elMax + 1.0E-6; e += 2.5) {
                max = Math.max(max, Ballistics.range(m.profile(), e, 0.0));
            }
            return new double[]{Ballistics.range(m.profile(), v.kind.elMin, 0.0) / k, max / k};
        }
        return new double[]{0.0, ((Integer)RWConfig.COMMON.radarRange.get()).intValue()};
    }

    public static void openMap(LauncherVehicle v) {
        Minecraft mc = Minecraft.getInstance();
        if (v instanceof ru.aiforge.realwar.vehicle.GunVehicle && !v.kind.artillery()) {
            return;   // tanks / IFV: direct fire only
        }
        if (v instanceof ru.aiforge.realwar.vehicle.YarsVehicle) {
            // strategic 5x5 km map + ground picture of linked radars; click = aim point, contact = tracked target, ПУСК
            Vec3 cur0 = v.mapTarget();
            BlockPos cp0 = cur0 == null ? null : BlockPos.containing((double)cur0.x, (double)cur0.y, (double)cur0.z);
            MapScreen.Params yp = new MapScreen.Params((Component)Component.translatable((String)"gui.aiarsenal.rw.map.title.yars"), v.position(), 0.0, 2500.0, false, cp0, Ballistics.scaleK() / 1000.0,
                    pos -> ModNetwork.CHANNEL.sendToServer(new SetTargetPacket(v.getId(), true, pos.getX(), pos.getY(), pos.getZ())),
                    () -> ModNetwork.CHANNEL.sendToServer(new SetTargetPacket(v.getId(), false, 0, 0, 0)), new MapScreen.Radar(v.getId(), true, true));
            mc.setScreen((Screen)new MapScreen(yp));
            return;
        }
        if (v instanceof SamLauncherEntity || v.kind.isRadar()) {
            boolean fire = v instanceof SamLauncherEntity;
            MapScreen.Params rp = new MapScreen.Params((Component)Component.translatable((String)"gui.aiarsenal.rw.map.title.radar"), v.position(), 0.0, ((Integer)RWConfig.COMMON.radarRange.get()).intValue(), false, null, Ballistics.scaleK() / 1000.0, pos -> {}, () -> {}, new MapScreen.Radar(v.getId(), fire));
            mc.setScreen((Screen)new MapScreen(rp));
            return;
        }
        double[] r = SightClient.ranges(v);
        Vec3 cur = v.mapTarget();
        BlockPos curPos = cur == null ? null : BlockPos.containing((double)cur.x, (double)cur.y, (double)cur.z);
        MapScreen.Params p = new MapScreen.Params((Component)Component.translatable((String)("gui.aiarsenal.rw.map.title." + (v instanceof SamLauncherEntity ? "sam" : "arty"))), v.position(), r[0], r[1], v.usesAltitude(), curPos, Ballistics.scaleK() / 1000.0, pos -> ModNetwork.CHANNEL.sendToServer(new SetTargetPacket(v.getId(), true, pos.getX(), pos.getY(), pos.getZ())), () -> ModNetwork.CHANNEL.sendToServer(new SetTargetPacket(v.getId(), false, 0, 0, 0)));
        mc.setScreen((Screen)new MapScreen(p));
    }

    static {
        fovDeg = 70.0f;
        origSens = -1.0;
    }
}

