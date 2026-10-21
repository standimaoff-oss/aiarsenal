package ru.aiforge.realwar.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import ru.aiforge.realwar.registry.ModEntities;
import net.minecraft.world.phys.Vec3;

/**
 * One spawn egg per trade: a modern Russian-army soldier of the chosen profession
 * (commander, tank crew, assault, medic, artillery, AA gunner, pilot).
 * The five army eggs keep raising a plain rifleman of their nation and period.
 */
public final class ProfessionEggItem extends net.minecraftforge.common.ForgeSpawnEggItem {
    private final Profession profession;

    public ProfessionEggItem(Profession profession, int bg, int hl, Properties props) {
        super(() -> ModEntities.SOLDIER_RUSSIA.get(), bg, hl, props);
        this.profession = profession;
    }

    public Profession profession() {
        return this.profession;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        BlockPos pos = ctx.getClickedPos();
        if (ctx.getClickedFace() != Direction.DOWN) pos = pos.relative(ctx.getClickedFace());
        SoldierEntity s = SoldierSpawner.spawnOne(sl, new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5),
                Faction.RUSSIA, Era.MODERN, this.profession.rank);
        if (s == null) return InteractionResult.FAIL;
        s.setProfession(this.profession);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
            ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
