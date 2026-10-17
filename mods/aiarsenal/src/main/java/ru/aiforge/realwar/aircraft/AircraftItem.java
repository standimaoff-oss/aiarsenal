/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.core.Vec3i
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.item.context.UseOnContext
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.phys.Vec3
 */
package ru.aiforge.realwar.aircraft;

import ru.aiforge.realwar.aircraft.AircraftEntity;
import ru.aiforge.realwar.item.TooltipItem;
import ru.aiforge.realwar.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class AircraftItem
extends TooltipItem {
    public AircraftItem(Item.Properties props) {
        super(props.stacksTo(1));
    }

    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        ServerLevel sl = (ServerLevel)level;
        BlockPos pos = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        Vec3 at = face == Direction.UP ? new Vec3(ctx.getClickLocation().x, (double)pos.getY() + 1.0, ctx.getClickLocation().z) : Vec3.atBottomCenterOf((Vec3i)pos.relative(face));
        AircraftEntity e = (AircraftEntity)((EntityType)ModEntities.TU95.get()).create((Level)sl);
        if (e == null) {
            return InteractionResult.FAIL;
        }
        float yaw = ctx.getPlayer() == null ? 0.0f : (float)Math.round(ctx.getPlayer().getYRot() / 5.0f) * 5.0f;
        e.moveTo(at.x, at.y, at.z, yaw, 0.0f);
        sl.addFreshEntity((Entity)e);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
            ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}

