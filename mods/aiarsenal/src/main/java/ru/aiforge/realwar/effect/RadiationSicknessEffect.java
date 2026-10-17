/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.effect.MobEffect
 *  net.minecraft.world.effect.MobEffectCategory
 *  net.minecraft.world.effect.MobEffectInstance
 *  net.minecraft.world.effect.MobEffects
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.player.Player
 */
package ru.aiforge.realwar.effect;

import ru.aiforge.realwar.blast.ModDamage;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class RadiationSicknessEffect
extends MobEffect {
    public RadiationSicknessEffect() {
        super(MobEffectCategory.HARMFUL, 8190976);
    }

    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 40 == 0;
    }

    public void applyEffectTick(LivingEntity e, int amp) {
        if (e.level().isClientSide) {
            return;
        }
        if (e instanceof Player) {
            Player p = (Player)e;
            p.causeFoodExhaustion(0.6f * (float)(amp + 1));
        }
        if (amp >= 1) {
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, amp >= 2 ? 1 : 0, true, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0, true, false, false));
        }
        if (amp == 2 && e.getHealth() > 6.0f) {
            e.hurt(ModDamage.source(e.level(), ModDamage.RADIATION), 1.0f);
        }
        if (amp >= 3) {
            e.hurt(ModDamage.source(e.level(), ModDamage.RADIATION), 1.5f);
        }
    }
}

