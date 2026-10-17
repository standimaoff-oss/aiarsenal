package ru.aiforge.realwar.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.*;

@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class ComfortTests {
    @GameTest(template="arena",batch="comfort",timeoutTicks=70)
    public static void smoke_remains_without_blindness(GameTestHelper h) {
        var pig=h.spawn(EntityType.PIG,new BlockPos(5,2,5));pig.setNoAi(true);pig.setInvulnerable(true);
        var smoke=ru.aiforge.bm21grad.entity.SmokeScreenEntity.create(h.getLevel(),pig.getX(),pig.getY(),pig.getZ());
        h.getLevel().addFreshEntity(smoke);
        h.onEachTick(()->h.getLevel().resetEmptyTime());
        h.runAfterDelay(40,()->{
            h.assertTrue(!smoke.isRemoved() && smoke.radius()>1,"visual smoke must remain");
            h.assertTrue(!pig.hasEffect(MobEffects.BLINDNESS),"smoke applied blindness");
            smoke.discard();pig.discard();h.succeed();
        });
    }
    @GameTest(template="arena",batch="comfort")
    public static void radiation_harms_without_nausea(GameTestHelper h) {
        var pig=h.spawn(EntityType.PIG,new BlockPos(5,2,5));float before=pig.getHealth();
        new ru.aiforge.realwar.effect.RadiationSicknessEffect().applyEffectTick(pig,3);
        h.assertTrue(!pig.hasEffect(MobEffects.CONFUSION),"radiation applied nausea");
        h.assertTrue(pig.hasEffect(MobEffects.WEAKNESS),"radiation gameplay was removed instead of nausea");
        h.assertTrue(pig.getHealth()<before,"radiation damage must remain");pig.discard();h.succeed();
    }
}
