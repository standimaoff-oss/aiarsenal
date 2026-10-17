package ru.aiforge.realwar.handheld;

import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** PG-7VL round / Igla missile / Javelin missile (rendered from its Blender GLB). */
public class HandheldAmmoItem extends Item {
    public final HandheldKind kind;

    public HandheldAmmoItem(HandheldKind kind, Properties p) {
        super(p.stacksTo(kind == HandheldKind.RPG7 ? 8 : 4));
        this.kind = kind;
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.aiarsenal." + this.kind.ammo).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> c) {
        c.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return ru.aiforge.realwar.client.render.GlbItemRenderer.instance();
            }
        });
    }
}
