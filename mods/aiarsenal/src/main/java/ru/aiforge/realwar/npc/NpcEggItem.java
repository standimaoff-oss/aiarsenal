package ru.aiforge.realwar.npc;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/**
 * One spawn egg of one army in one period — either its rifleman ({@code profession == null})
 * or one of its trades: the army's own commander, assault trooper, medic, artilleryman,
 * tank crewman, AA gunner or pilot.
 */
public final class NpcEggItem extends net.minecraftforge.common.ForgeSpawnEggItem {
    private final Faction faction;
    private final Era era;
    @Nullable
    private final Profession profession;

    public NpcEggItem(Faction faction, Era era, @Nullable Profession profession, int bg, int hl,
                      Properties props) {
        super(() -> SoldierSpawner.typeFor(faction, era), bg, hl, props);
        this.faction = faction;
        this.era = era;
        this.profession = profession;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        BlockPos pos = ctx.getClickedPos();
        if (ctx.getClickedFace() != Direction.DOWN) pos = pos.relative(ctx.getClickedFace());
        Rank rank = this.profession == null ? Rank.PRIVATE : this.profession.rank;
        SoldierEntity s = SoldierSpawner.spawnOne(sl, new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5),
                this.faction, this.era, rank);
        if (s == null) return InteractionResult.FAIL;
        if (this.profession != null) s.setProfession(this.profession);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
            ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
