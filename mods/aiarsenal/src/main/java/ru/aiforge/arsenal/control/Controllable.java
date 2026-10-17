package ru.aiforge.arsenal.control;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** A unit that can be flown / operated remotely (tablet or FPV goggles). */
public interface Controllable {
    int K_FWD = 1, K_BACK = 2, K_LEFT = 4, K_RIGHT = 8, K_UP = 16, K_DOWN = 32, K_FIRE = 64, K_ORBIT = 128, K_HOME = 256, K_BOOST = 512,
            K_DETONATE = 1024;

    /** Per-tick input: key bits, camera yaw / pitch (world, degrees) and zoom (fov multiplier). */
    void control(ServerPlayer op, int keys, float camYaw, float camPitch, float zoom);

    /** Designation from the operator's camera crosshair. */
    default void designate(ServerPlayer op, Vec3 point, @Nullable net.minecraft.world.entity.Entity hit) {
    }

    /** Session ended (operator left / was hurt). */
    default void released() {
    }
}
