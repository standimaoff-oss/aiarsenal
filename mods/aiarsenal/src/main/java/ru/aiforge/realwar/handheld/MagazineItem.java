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

/** Rifle magazine (6L31 for the AK-12, STANAG for the M4A1). A stack without NBT is full; partial ones keep "Rounds". */
public class MagazineItem extends Item {
    public final GunKind kind;

    public MagazineItem(GunKind kind, Properties p) {
        super(p.stacksTo(8));
        this.kind = kind;
    }

    public static int rounds(ItemStack s) {
        if (!(s.getItem() instanceof MagazineItem m)) return 0;
        return s.hasTag() && s.getTag().contains("Rounds") ? s.getTag().getInt("Rounds") : m.kind.cap;
    }

    public static ItemStack withRounds(Item mag, int n) {
        ItemStack s = new ItemStack(mag);
        if (mag instanceof MagazineItem m && n < m.kind.cap) s.getOrCreateTag().putInt("Rounds", Math.max(0, n));
        return s;
    }

    @Override
    public boolean isBarVisible(ItemStack s) {
        return rounds(s) < this.kind.cap;
    }

    @Override
    public int getBarWidth(ItemStack s) {
        return Math.round(13f * rounds(s) / this.kind.cap);
    }

    @Override
    public int getBarColor(ItemStack s) {
        return rounds(s) == 0 ? 0xFF4040 : 0xE0C060;
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.aiarsenal.mag.rounds", rounds(st), this.kind.cap).withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("tooltip.aiarsenal.mag." + this.kind.id).withStyle(ChatFormatting.DARK_GRAY));
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
