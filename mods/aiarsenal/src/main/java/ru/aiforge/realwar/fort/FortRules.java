package ru.aiforge.realwar.fort;

import net.minecraft.world.entity.Entity;

/** Shared rules for obstacles. */
public final class FortRules {
    private FortRules() {
    }

    /** Wheeled/tracked Real War vehicles and the arsenal launch trucks crush wire obstacles. */
    public static boolean crushesObstacles(Entity e) {
        return e instanceof ru.aiforge.realwar.vehicle.VehicleEntity || e instanceof ru.aiforge.arsenal.entity.LauncherEntity;
    }
}
