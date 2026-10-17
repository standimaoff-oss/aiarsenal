package ru.aiforge.realwar.napoleonic;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import java.util.List;
import java.util.function.Consumer;
import ru.aiforge.realwar.registry.ModEntities;
public final class CannonItem extends Item {
    private final boolean french;
    public CannonItem(boolean french){super(new Properties().stacksTo(1));this.french=french;}
    @Override public InteractionResult useOn(UseOnContext c){
        Level l=c.getLevel();var pos=c.getClickedPos().relative(c.getClickedFace());
        var e=(french?ModEntities.CANNON_FR6:ModEntities.CANNON_RI12).get().create(l);
        if(e==null)return InteractionResult.FAIL;
        e.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,c.getRotation(),0);
        if(!l.noCollision(e))return InteractionResult.FAIL;
        if(!l.isClientSide){l.addFreshEntity(e);if(c.getPlayer()!=null && !c.getPlayer().getAbilities().instabuild)c.getItemInHand().shrink(1);}
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override public void appendHoverText(ItemStack s,Level l,List<Component> tip,TooltipFlag flag){tip.add(Component.translatable("tooltip.aiarsenal.powder_cannon"));}
    @Override public void initializeClient(Consumer<IClientItemExtensions> c){c.accept(new IClientItemExtensions(){public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){return ru.aiforge.realwar.client.render.GunRenderer.item();}});}
}
