/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.Level
 */
package ru.aiforge.realwar.item;

import ru.aiforge.realwar.blast.RadiationManager;
import ru.aiforge.realwar.item.TooltipItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PotassiumIodideItem
extends TooltipItem {
    public static final double DOSE_REDUCTION = 40.0;

    public PotassiumIodideItem(Item.Properties props) {
        super(props);
    }

    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player) {
            Player p = (Player)entity;
            RadiationManager.setDose(p, RadiationManager.getDose(p) - 40.0);
        }
        return super.finishUsingItem(stack, level, entity);
    }
}

