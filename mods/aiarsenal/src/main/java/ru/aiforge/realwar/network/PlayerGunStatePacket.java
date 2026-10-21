package ru.aiforge.realwar.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server → every client that tracks the shooter: the third-person pose of a firearm.
 *
 * <p>Online this is what makes another player look like he is really handling the weapon: the arms are the vanilla
 * ones, and the gun model drawn by the mod follows aim, recoil, the bolt and the Blender reload animation of the
 * player we are looking at. Nothing here is server gameplay state, so a lost packet only costs one animation.</p>
 *
 * <p>{@code flags} is a bit set of {@link #AIM}, {@link #RELOAD}, {@link #RELOAD_EMPTY}, {@link #SHOT}, {@link #DRY};
 * {@code ticks} is the total reload time for {@code RELOAD} (and otherwise 0).</p>
 */
public record PlayerGunStatePacket(int entity, int flags, int ticks) {
    public static final int AIM = 1;
    public static final int RELOAD = 2;
    public static final int RELOAD_EMPTY = 4;
    public static final int SHOT = 8;
    public static final int DRY = 16;

    public static void encode(PlayerGunStatePacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.entity);
        b.writeByte(m.flags);
        b.writeVarInt(m.ticks);
    }

    public static PlayerGunStatePacket decode(FriendlyByteBuf b) {
        return new PlayerGunStatePacket(b.readVarInt(), b.readByte(), b.readVarInt());
    }

    public static void handle(PlayerGunStatePacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ru.aiforge.realwar.client.GunAnimClient.accept(m.entity, m.flags, m.ticks)));
        ctx.get().setPacketHandled(true);
    }
}
