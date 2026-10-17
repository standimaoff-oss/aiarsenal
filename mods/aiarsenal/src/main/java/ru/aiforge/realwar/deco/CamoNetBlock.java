package ru.aiforge.realwar.deco;

import net.minecraft.world.level.block.IronBarsBlock;

/** r16: camouflage screen hung between poles - pane connection logic (links to other screens, panes, walls and solid faces).
 *  The multipart blockstate draws a pole only at ends / corners / isolated blocks and a net half towards every link. */
public class CamoNetBlock extends IronBarsBlock {
    public CamoNetBlock(Properties props) {
        super(props);
    }
}
