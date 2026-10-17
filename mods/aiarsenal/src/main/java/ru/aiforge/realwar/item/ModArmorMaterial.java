/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundEvents
 *  net.minecraft.world.item.ArmorItem$Type
 *  net.minecraft.world.item.ArmorMaterial
 *  net.minecraft.world.item.crafting.Ingredient
 *  net.minecraft.world.level.ItemLike
 */
package ru.aiforge.realwar.item;

import ru.aiforge.realwar.registry.ModItems;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

public enum ModArmorMaterial implements ArmorMaterial
{
    HAZMAT("hazmat", 18, new int[]{1, 2, 3, 1}, 6, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0f, 0.0f),
    /** r15 uniforms: field kit (ballistic helmet, Ratnik / OCP) and service dress. {feet, legs, chest, head} */
    UNIFORM_FIELD("uniform_field", 14, new int[]{1, 2, 3, 2}, 12, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0f, 0.0f),
    UNIFORM_DRESS("uniform_dress", 10, new int[]{1, 1, 2, 1}, 15, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0f, 0.0f);

    private static final int[] BASE_DURABILITY;
    private final String name;
    private final int durabilityMultiplier;
    private final int[] defense;
    private final int enchantability;
    private final SoundEvent sound;
    private final float toughness;
    private final float knockbackResistance;

    private ModArmorMaterial(String name, int durabilityMultiplier, int[] defense, int enchantability, SoundEvent sound, float toughness, float kb) {
        this.name = name;
        this.durabilityMultiplier = durabilityMultiplier;
        this.defense = defense;
        this.enchantability = enchantability;
        this.sound = sound;
        this.toughness = toughness;
        this.knockbackResistance = kb;
    }

    public int getDurabilityForType(ArmorItem.Type type) {
        return BASE_DURABILITY[type.getSlot().getIndex()] * this.durabilityMultiplier;
    }

    public int getDefenseForType(ArmorItem.Type type) {
        return this.defense[type.getSlot().getIndex()];
    }

    public int getEnchantmentValue() {
        return this.enchantability;
    }

    public SoundEvent getEquipSound() {
        return this.sound;
    }

    public Ingredient getRepairIngredient() {
        if (this != HAZMAT) return Ingredient.of(net.minecraft.tags.ItemTags.WOOL);
        return Ingredient.of((ItemLike[])new ItemLike[]{(ItemLike)ModItems.RUBBERIZED_FABRIC.get()});
    }

    public String getName() {
        return "aiarsenal:" + this.name;
    }

    public float getToughness() {
        return this.toughness;
    }

    public float getKnockbackResistance() {
        return this.knockbackResistance;
    }

    static {
        BASE_DURABILITY = new int[]{13, 15, 16, 11};
    }
}

