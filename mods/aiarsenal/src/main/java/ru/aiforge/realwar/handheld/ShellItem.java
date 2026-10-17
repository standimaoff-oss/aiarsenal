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

/** r24: loose 12/70 buckshot shell (Remington 870 tube is loaded one shell at a time). Rendered from shell_12.glb. */
public class ShellItem extends Item {
    private final String tip;

    public ShellItem(Properties p) {
        this(p, "tooltip.aiarsenal.shell_12");
    }

    /** r26: also the 5-round stripper clips (Mosin 91/30, Kar98k) */
    public ShellItem(Properties p, String tip) {
        super(p.stacksTo(64));
        this.tip = tip;
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(this.tip).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> c) {
        c.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return ru.aiforge.realwar.client.render.GunRenderer.item();
            }
        });
    }
}
