package ru.aiforge.realwar.item;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Reply 15: uniforms by army and rank (shoulder boards are clothing, not blocks). The vanilla armour layer draws a
 * blank texture; the real garment is the Blender model models/glb/uniforms.glb drawn by client/render/UniformLayer
 * (nodes "&lt;set&gt;.&lt;piece&gt;[.&lt;detail&gt;]").
 */
public class UniformItem extends ArmorItem {
    /** RF: private, sergeant, captain, colonel, colonel general; US Army: PV2, SGT, CPT, COL, GEN; r19: Soviet Afghanistan +
     *  tank crew, US Vietnam + Desert Storm, Russian VDV + VKS pilot (all field kit). */
    private static final String[] MODERN_SETS = {"ru_private", "ru_sergeant", "ru_captain", "ru_colonel", "ru_general",
            "us_private", "us_sergeant", "us_captain", "us_colonel", "us_general",
            // r19: eras and branches
            "su_afghan", "su_tanker", "us_vietnam", "us_desert", "ru_vdv", "ru_pilot"};
    public static final String[] SETS = java.util.stream.Stream.concat(
            java.util.Arrays.stream(MODERN_SETS), java.util.Arrays.stream(HistoricalUniforms.SETS)).toArray(String[]::new);
    public final String set;

    public UniformItem(String set, ArmorItem.Type type, Properties props) {
        super(field(set) ? ModArmorMaterial.UNIFORM_FIELD : ModArmorMaterial.UNIFORM_DRESS, type, props);
        this.set = set;
    }

    /** Field kit = everything below colonel. */
    public static boolean field(String set) {
        return !(set.endsWith("colonel") || set.endsWith("general"));
    }

    public static String slotName(ArmorItem.Type t) {
        return switch (t) {
            case HELMET -> "head";
            case CHESTPLATE -> "chest";
            case LEGGINGS -> "legs";
            default -> "feet";
        };
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return "aiarsenal:textures/models/armor/hazmat_blank.png"; // the garment itself is drawn by UniformLayer
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.aiarsenal.uniform." + this.set).withStyle(ChatFormatting.GRAY));
    }
}
