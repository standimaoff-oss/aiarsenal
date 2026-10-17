/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.InputConstants$Type
 *  net.minecraft.client.KeyMapping
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.item.ItemStack
 *  net.minecraftforge.client.event.InputEvent$InteractionKeyMappingTriggered
 */
package ru.aiforge.realwar.client;

import com.mojang.blaze3d.platform.InputConstants;
import ru.aiforge.realwar.aircraft.AircraftControlPacket;
import ru.aiforge.realwar.aircraft.AircraftEntity;
import ru.aiforge.realwar.aircraft.AircraftInteractPacket;
import ru.aiforge.realwar.aircraft.Tu95Spec;
import ru.aiforge.realwar.client.VehicleClient;
import ru.aiforge.realwar.network.ModNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.InputEvent;

public final class AircraftClient {
    public static final KeyMapping KEY_GEAR = new KeyMapping("key.aiarsenal.rw.gear", InputConstants.Type.KEYSYM, 86, "key.categories.aiarsenal_rw");
    public static final KeyMapping KEY_FLAPS = new KeyMapping("key.aiarsenal.rw.flaps", InputConstants.Type.KEYSYM, 78, "key.categories.aiarsenal_rw");
    public static final KeyMapping KEY_BAY = new KeyMapping("key.aiarsenal.rw.bay", InputConstants.Type.KEYSYM, 85, "key.categories.aiarsenal_rw");
    public static final KeyMapping KEY_BOMBSEL = new KeyMapping("key.aiarsenal.rw.bombsel", InputConstants.Type.KEYSYM, 89, "key.categories.aiarsenal_rw");
    public static final KeyMapping KEY_BRAKE = new KeyMapping("key.aiarsenal.rw.brake", InputConstants.Type.KEYSYM, 66, "key.categories.aiarsenal_rw");
    public static final KeyMapping KEY_PITCH_DOWN = new KeyMapping("key.aiarsenal.rw.pitch_down", InputConstants.Type.KEYSYM, 67, "key.categories.aiarsenal_rw");
    public static final KeyMapping KEY_RUDDER_L = new KeyMapping("key.aiarsenal.rw.rudder_l", InputConstants.Type.KEYSYM, 81, "key.categories.aiarsenal_rw");
    public static final KeyMapping KEY_RUDDER_R = new KeyMapping("key.aiarsenal.rw.rudder_r", InputConstants.Type.KEYSYM, 69, "key.categories.aiarsenal_rw");
    private static final float[] LOCAL = new float[3];
    private static int localId = -1;
    private static boolean useArmed = true;

    private AircraftClient() {
    }

    public static void install() {
        AircraftEntity.localWalk = e -> (float[])(e.getId() == localId ? LOCAL : null);
    }

    public static AircraftEntity riding() {
        AircraftEntity a;
        Entity entity;
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && (entity = p.getVehicle()) instanceof AircraftEntity ? (a = (AircraftEntity)entity) : null;
    }

    public static boolean clientTick(Minecraft mc) {
        boolean brake;
        float pitch;
        float roll;
        int seat;
        Entity entity;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !((entity = p.getVehicle()) instanceof AircraftEntity)) {
            localId = -1;
            return false;
        }
        AircraftEntity a = (AircraftEntity)entity;
        float[] srv = a.walkers.get(p.getUUID());
        if (a.getId() != localId) {
            localId = a.getId();
            AircraftClient.LOCAL[0] = srv != null ? srv[0] : 0.95f;
            AircraftClient.LOCAL[1] = srv != null ? srv[1] : -5.2f;
            AircraftClient.LOCAL[2] = -1.0f;
        }
        if (srv != null) {
            AircraftClient.LOCAL[2] = srv[2];
        }
        if ((seat = (int)LOCAL[2]) >= 0) {
            AircraftClient.LOCAL[0] = Tu95Spec.SEATS[seat][0];
            AircraftClient.LOCAL[1] = Tu95Spec.SEATS[seat][1];
        } else if (mc.screen == null) {
            AircraftClient.walk(p, a);
        }
        boolean pilot = seat == 0 || seat == 1 && a.seatHolder(0) == null;
        boolean canFly = pilot && mc.screen == null;
        float thr = canFly ? p.input.forwardImpulse : 0.0f;
        float f = roll = canFly ? -p.input.leftImpulse : 0.0f;
        pitch = canFly ? (p.input.jumping ? 1.0f : 0.0f) - (KEY_PITCH_DOWN.isDown() ? 1.0f : 0.0f) : 0.0f;
        float f2 = pitch;
        float rudder = canFly ? (KEY_RUDDER_R.isDown() ? 1.0f : 0.0f) - (KEY_RUDDER_L.isDown() ? 1.0f : 0.0f) : 0.0f;
        int actions = 0;
        if (VehicleClient.KEY_SEAT.consumeClick()) {
            actions |= 1;
        }
        if (VehicleClient.KEY_ENGINE.consumeClick()) {
            actions |= 2;
        }
        if (KEY_GEAR.consumeClick()) {
            actions |= 4;
        }
        if (KEY_FLAPS.consumeClick()) {
            actions |= 8;
        }
        if (KEY_BAY.consumeClick()) {
            actions |= 0x10;
        }
        if (KEY_BOMBSEL.consumeClick()) {
            actions |= 0x20;
        }
        while (VehicleClient.KEY_LOCK.consumeClick() || VehicleClient.KEY_SIGHT.consumeClick()) {
        }
        boolean fire = mc.screen == null && mc.options.keyAttack.isDown() && mc.mouseHandler.isMouseGrabbed();
        boolean bl = brake = canFly && KEY_BRAKE.isDown();
        if (mc.screen == null) {
            ModNetwork.CHANNEL.sendToServer(AircraftControlPacket.of(thr, roll, pitch, rudder, fire, brake, actions, LOCAL[0], LOCAL[1]));
        }
        return true;
    }

    private static void walk(LocalPlayer p, AircraftEntity a) {
        double nz;
        double sp;
        double nx;
        double mz;
        float fw = p.input.forwardImpulse;
        float lf = p.input.leftImpulse;
        if (fw == 0.0f && lf == 0.0f) {
            return;
        }
        double th = Math.toRadians(p.getYRot() - a.getYRot());
        double mx = (double)fw * -Math.sin(th) + (double)lf * Math.cos(th);
        double len = Math.hypot(mx, mz = (double)fw * Math.cos(th) + (double)lf * Math.sin(th));
        if (len > 1.0) {
            mx /= len;
            mz /= len;
        }
        if (AircraftEntity.walkable(nx = (double)LOCAL[0] + mx * (sp = p.isSprinting() ? 0.27 : 0.21), nz = (double)LOCAL[1] + mz * sp)) {
            AircraftClient.LOCAL[0] = (float)nx;
            AircraftClient.LOCAL[1] = (float)nz;
        } else if (AircraftEntity.walkable(nx, LOCAL[1])) {
            AircraftClient.LOCAL[0] = (float)nx;
        } else if (AircraftEntity.walkable(LOCAL[0], nz)) {
            AircraftClient.LOCAL[1] = (float)nz;
        }
    }

    public static boolean onInteraction(InputEvent.InteractionKeyMappingTriggered e, Minecraft mc, LocalPlayer p) {
        if (p.getVehicle() instanceof AircraftEntity) {
            if (e.isAttack() || e.isUseItem()) {
                e.setCanceled(true);
                e.setSwingHand(false);
            }
            return true;
        }
        if (!e.isUseItem()) {
            return false;
        }
        ItemStack held = p.getMainHandItem();
        boolean bomb = AircraftEntity.kindOfItem(held.getItem()) >= 0;
        for (AircraftEntity a : mc.level.getEntitiesOfClass(AircraftEntity.class, p.getBoundingBox().inflate(30.0))) {
            boolean unload;
            double lz;
            double s;
            double yaw = Math.toRadians(a.getYRot());
            double rx = p.getX() - a.getX();
            double rz = p.getZ() - a.getZ();
            double c = Math.cos(yaw);
            double lx = rx * c + rz * (s = Math.sin(yaw));
            boolean atDoor = Math.hypot(lx - (double)Tu95Spec.DOOR_POS[0] - 0.9, (lz = -rx * s + rz * c) - (double)Tu95Spec.DOOR_POS[2]) < 3.0 && Math.abs(p.getY() - a.getY()) < 3.5;
            boolean underBay = Math.abs(lx) < 4.0 && lz > -4.2 && lz < 9.7 && p.getY() - a.getY() < 3.0;
            boolean bl = unload = held.isEmpty() && p.isShiftKeyDown();
            if (underBay && (bomb || unload)) {
                ModNetwork.CHANNEL.sendToServer(new AircraftInteractPacket(a.getId(), true));
                e.setCanceled(true);
                e.setSwingHand(true);
                return true;
            }
            if (!atDoor || bomb) continue;
            ModNetwork.CHANNEL.sendToServer(new AircraftInteractPacket(a.getId(), false));
            e.setCanceled(true);
            e.setSwingHand(true);
            return true;
        }
        return false;
    }
}

