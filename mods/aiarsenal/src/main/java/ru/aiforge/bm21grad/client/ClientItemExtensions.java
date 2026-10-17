package ru.aiforge.bm21grad.client;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import ru.aiforge.bm21grad.client.render.GlbItemRenderer;

/** Items rendered from the GLB models (vehicle and rockets). */
public final class ClientItemExtensions implements IClientItemExtensions {
    public static final ClientItemExtensions INSTANCE = new ClientItemExtensions();
    private GlbItemRenderer renderer;

    private ClientItemExtensions() {
    }

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (renderer == null) renderer = new GlbItemRenderer();
        return renderer;
    }
}
