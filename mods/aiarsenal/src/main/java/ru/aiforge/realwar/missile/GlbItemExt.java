/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
 *  net.minecraftforge.client.extensions.common.IClientItemExtensions
 */
package ru.aiforge.realwar.missile;

import ru.aiforge.realwar.client.render.GlbItemRenderer;
import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public final class GlbItemExt {
    private GlbItemExt() {
    }

    public static void apply(Consumer<IClientItemExtensions> c) {
        c.accept(new IClientItemExtensions(){

            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return GlbItemRenderer.instance();
            }
        });
    }
}

