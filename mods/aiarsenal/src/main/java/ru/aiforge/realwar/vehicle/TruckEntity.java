package ru.aiforge.realwar.vehicle;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import ru.aiforge.realwar.glb.Pose;

/**
 * Reply 12: unarmed wheeled transport (Ural-4320). Uses the common wheeled-vehicle physics, steering, wheel spin and the
 * cab gauges of VehicleEntity; seat 0 drives, all other seats are passengers (cab + troop benches under the tent).
 */
public class TruckEntity extends LauncherVehicle {
    public TruckEntity(EntityType<?> type, Level level, VehicleKind kind) {
        super(type, level, kind);
    }

    @Override
    protected Item ammoItem() {
        return Items.AIR;
    }

    @Override
    protected float[] aimTarget() {
        return null;
    }

    @Override
    protected void serverLaunch() {
    }

    @Override
    protected boolean mayReload() {
        return false;
    }

    @Override
    protected void launcherPose(Pose pose, float pt) {
    }
}
