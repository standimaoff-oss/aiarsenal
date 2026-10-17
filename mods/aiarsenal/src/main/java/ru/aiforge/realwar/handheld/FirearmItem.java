package ru.aiforge.realwar.handheld;

import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * r22 firearm (AK-12 / M4A1 / RPG-7V2). Trigger, aim, reload and fire selector come from {@link ru.aiforge.realwar.network.GunActionPacket};
 * the server logic is in {@link Firearms}. NBT: Ammo (rounds incl. chambered), Mode (0 semi, 1 auto).
 */
public class FirearmItem extends Item {
    public final GunKind kind;

    public FirearmItem(GunKind kind, Properties p) {
        super(p.stacksTo(1));
        this.kind = kind;
    }

    public static int ammo(ItemStack s) {
        return s.hasTag() ? s.getTag().getInt("Ammo") : 0;
    }

    public static void setAmmo(ItemStack s, int n) {
        s.getOrCreateTag().putInt("Ammo", Math.max(0, n));
    }

    /** 0 = single, 1 = automatic (new rifles come on auto) */
    public static int mode(ItemStack s) {
        return s.hasTag() && s.getTag().contains("Mode") ? s.getTag().getInt("Mode") : 1;
    }

    public static void setMode(ItemStack s, int m) {
        s.getOrCreateTag().putInt("Mode", m);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        return InteractionResultHolder.fail(p.getItemInHand(hand));     // RMB = aim (client), never a vanilla "use"
    }

    @Override
    public boolean canAttackBlock(BlockState st, Level level, BlockPos pos, Player p) {
        return false;                                                    // LMB = trigger, never mines
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        return true;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack st) {
        return UseAnim.NONE;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack a, ItemStack b, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(a, b);
    }

    @Override
    public boolean isBarVisible(ItemStack s) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack s) {
        return Math.round(13f * Math.min(1f, ammo(s) / (float) this.kind.cap));
    }

    @Override
    public int getBarColor(ItemStack s) {
        return ammo(s) == 0 ? 0xFF4040 : 0xE0C060;
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.aiarsenal.gun." + this.kind.id).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.aiarsenal.gun.ammo", ammo(st), this.kind.cap,
                Component.translatable("item.aiarsenal." + this.kind.ammo)).withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable(this.kind.flintlock() ? "tooltip.aiarsenal.gun.keys_flintlock" : this.kind.manpads() ? "tooltip.aiarsenal.gun.keys_manpads" : this.kind.launcher() ? "tooltip.aiarsenal.gun.keys_rpg" : this.kind.singleLoad() ? "tooltip.aiarsenal.gun.keys_historical_scope" : this.kind.scope() ? "tooltip.aiarsenal.gun.keys_sniper"
                : this.kind == GunKind.C96 ? "tooltip.aiarsenal.gun.keys_c96" : this.kind.clipFed() ? "tooltip.aiarsenal.gun.keys_bolt"
                : this.kind.selector() ? "tooltip.aiarsenal.gun.keys" : this.kind.tube() ? "tooltip.aiarsenal.gun.keys_pump"
                : !this.kind.auto ? "tooltip.aiarsenal.gun.keys_semi" : "tooltip.aiarsenal.gun.keys_mg").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> c) {
        // Register during Item construction, BEFORE HumanoidModel builds its enum-switch lookup table.
        // Lazy creation on the first pistol render expands the enum too late and crashes poseRightArm.
        var pistolPose = ru.aiforge.realwar.client.render.PistolPose.HOLD;
        var longGunPose = ru.aiforge.realwar.client.render.PistolPose.LONG_GUN;
        c.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return ru.aiforge.realwar.client.render.GunRenderer.item();
            }

            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(LivingEntity e, InteractionHand hand, ItemStack st) {
                return kind.pistol() ? pistolPose
                        : longGunPose;
            }
        });
    }
}
