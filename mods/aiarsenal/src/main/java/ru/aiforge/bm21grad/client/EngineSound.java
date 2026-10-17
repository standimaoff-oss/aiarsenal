package ru.aiforge.bm21grad.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import ru.aiforge.bm21grad.entity.Bm21Entity;
import ru.aiforge.bm21grad.registry.ModRegistry;

/** Looping ZIL-375 V8 sound following the vehicle; pitch/volume follow road speed. */
public class EngineSound extends AbstractTickableSoundInstance {
    private final Bm21Entity vehicle;
    private float rpm;

    public EngineSound(Bm21Entity v) {
        super(ModRegistry.SND_ENGINE.get(), SoundSource.NEUTRAL, RandomSource.create());
        this.vehicle = v;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = v.getX();
        this.y = v.getY() + 1;
        this.z = v.getZ();
    }

    @Override
    public void tick() {
        if (vehicle.isRemoved() || vehicle.isDestroyed() || !vehicle.hasState(Bm21Entity.ST_ENGINE)) {
            volume -= 0.1f;
            if (volume <= 0) stop();
            return;
        }
        float sp = Math.abs(vehicle.isControlledByLocalInstance() ? vehicle.speed : vehicle.getSyncedSpeed());
        rpm += (Math.min(1f, sp / 0.9f) - rpm) * 0.1f;
        x = vehicle.getX();
        y = vehicle.getY() + 1;
        z = vehicle.getZ();
        volume = 0.55f + 0.65f * rpm;
        pitch = 0.75f + 0.65f * rpm;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
