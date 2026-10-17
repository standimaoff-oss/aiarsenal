/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.Items
 *  net.minecraft.world.level.Level
 */
package ru.aiforge.realwar.radar;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.glb.Pose;
import ru.aiforge.realwar.radar.RadarNet;
import ru.aiforge.realwar.vehicle.LauncherVehicle;
import ru.aiforge.realwar.vehicle.VehicleKind;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class RadarVehicleEntity
extends LauncherVehicle
implements RadarNet.RadarEmitter {
    public float spin;
    public float spinO;
    public float spinRate;

    public RadarVehicleEntity(EntityType<?> type, Level level, VehicleKind kind) {
        super(type, level, kind);
    }

    @Override
    public boolean radarActive() {
        if (!this.isAlive()) {
            return false;
        }
        return this.kind == VehicleKind.RADAR_POST || this.jack() > 0.97f && Math.abs(this.speedMs) < 0.4;
    }

    @Override
    public double radarRange() {
        double r = ((Integer)RWConfig.COMMON.radarRange.get()).intValue();
        return this.kind == VehicleKind.RADAR_POST ? r : r * 0.75;
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
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.spinO = this.spin;
            float want = this.radarActive() ? (this.kind == VehicleKind.RADAR_POST ? 3.0f : 4.0f) : 0.0f;
            this.spinRate += (want - this.spinRate) * 0.03f;
            this.spin = (this.spin + this.spinRate) % 360.0f;
            if (this.spinRate < 0.01f && want == 0.0f) {
                this.spinRate = 0.0f;
            }
        }
    }

    @Override
    protected void launcherPose(Pose pose, float pt) {
        float a = this.spinO + (this.spin - this.spinO) * pt;
        if (this.spin < this.spinO) {
            a = this.spin;
        }
        pose.rotate("radar_yaw", (float)(-Math.toRadians(a)), 0.0f, 1.0f, 0.0f);
    }
}

