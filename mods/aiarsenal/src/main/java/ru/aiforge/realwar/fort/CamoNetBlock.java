package ru.aiforge.realwar.fort;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Camouflage net canopy: hangs in the air (place it over vehicles/positions), see-through, no collision, lets light pass. */
public class CamoNetBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 12, 0, 16, 16, 16);

    public CamoNetBlock(Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState st, BlockGetter lv, BlockPos p, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState st, BlockGetter lv, BlockPos p, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getVisualShape(BlockState st, BlockGetter lv, BlockPos p, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState st, BlockGetter lv, BlockPos p) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState st, BlockGetter lv, BlockPos p) {
        return 1.0f;
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, @javax.annotation.Nullable BlockGetter lv, java.util.List<net.minecraft.network.chat.Component> tip, net.minecraft.world.item.TooltipFlag flag) {
        tip.add(net.minecraft.network.chat.Component.translatable("tooltip.aiarsenal." + net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(this).getPath()).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
