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
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 */
package ru.aiforge.realwar.item;

import ru.aiforge.realwar.item.TooltipItem;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.vehicle.FlakEntity;
import ru.aiforge.realwar.vehicle.LauncherVehicle;
import ru.aiforge.realwar.vehicle.VehicleKind;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class VehicleItem
extends TooltipItem {
    private final VehicleKind kind;

    public VehicleItem(VehicleKind kind, Item.Properties props) {
        super(props);
        this.kind = kind;
    }

    public static EntityType<? extends Entity> typeOf(VehicleKind k) {
        return switch (k) {
            default -> throw new IncompatibleClassChangeError();
            case GRAD -> (EntityType)ModEntities.GRAD.get();
            case SMERCH -> (EntityType)ModEntities.SMERCH.get();
            case S400 -> (EntityType)ModEntities.S400.get();
            case ZU23 -> (EntityType)ModEntities.ZU23.get();
            case RADAR_MOBILE -> (EntityType)ModEntities.RADAR_MOBILE.get();
            case RADAR_POST -> (EntityType)ModEntities.RADAR_POST.get();
            case YARS -> (EntityType)ModEntities.YARS.get();
            case T90M -> (EntityType)ModEntities.T90M.get();
            case ABRAMS -> (EntityType)ModEntities.ABRAMS.get();
            case BMP3 -> (EntityType)ModEntities.BMP3.get();
            case MSTA -> (EntityType)ModEntities.MSTA.get();
            case D30 -> (EntityType)ModEntities.D30.get();
            case M777 -> (EntityType)ModEntities.M777.get();
            case PZ2 -> (EntityType)ModEntities.PZ2.get();
            case PZ4 -> (EntityType)ModEntities.PZ4.get();
            case TIGER -> (EntityType)ModEntities.TIGER.get();
            case STURMTIGER -> (EntityType)ModEntities.STURMTIGER.get();
            case MAUS -> (EntityType)ModEntities.MAUS.get();
            case RATTE -> (EntityType)ModEntities.RATTE.get();
            case T34 -> (EntityType)ModEntities.T34.get();
            case KV2 -> (EntityType)ModEntities.KV2.get();
            case IS2 -> (EntityType)ModEntities.IS2.get();
            case KV5 -> (EntityType)ModEntities.KV5.get();
            case BM13 -> (EntityType)ModEntities.BM13.get();
            case BRADLEY -> (EntityType)ModEntities.BRADLEY.get();
            case TOS1A -> (EntityType)ModEntities.TOS1A.get();
            case URAL -> (EntityType)ModEntities.URAL.get();
        };
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
        Entity e = VehicleItem.typeOf(this.kind).create((Level)sl);
        if (e == null) {
            return InteractionResult.FAIL;
        }
        float yaw = ctx.getPlayer() == null ? 0.0f : (float)Math.round(ctx.getPlayer().getYRot() / 5.0f) * 5.0f;
        e.moveTo(at.x, at.y, at.z, yaw, 0.0f);
        AABB box = e.getBoundingBox();
        if (!sl.noCollision(e, box)) {
            e.moveTo(at.x, at.y + 1.0, at.z, yaw, 0.0f);
            if (!sl.noCollision(e, e.getBoundingBox())) {
                return InteractionResult.FAIL;
            }
        }
        if (ctx.getPlayer() != null && ctx.getPlayer().getAbilities().instabuild) {
            if (e instanceof LauncherVehicle) {
                LauncherVehicle lv = (LauncherVehicle)e;
                lv.fillMagazine();
            }
            if (e instanceof FlakEntity) {
                FlakEntity fe = (FlakEntity)e;
                fe.setAmmo(100);
            }
        }
        sl.addFreshEntity(e);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
            ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}

