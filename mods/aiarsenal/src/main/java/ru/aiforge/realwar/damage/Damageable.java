package ru.aiforge.realwar.damage;

import net.minecraft.world.phys.Vec3;

/** r18: an entity with War Thunder style module damage. */
public interface Damageable {
    DamageState dmg();

    /** A near-miss blast (ConventionalBlast): module damage only (tracks, barrel, rotor blades, wings...). */
    default void blastNear(Vec3 centre, double tntKg, double dist) {
    }
}
