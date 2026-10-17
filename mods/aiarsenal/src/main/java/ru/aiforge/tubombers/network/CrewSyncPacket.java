package ru.aiforge.tubombers.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.aiforge.tubombers.entity.Crew;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Server -> client: crew table of one aircraft. {@code forced} = entity id whose own entry must be adopted (or -1). */
public record CrewSyncPacket(int vehicle, Map<Integer, Crew> crew, int forced) {
    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(vehicle);
        b.writeVarInt(forced + 1);
        b.writeVarInt(crew.size());
        crew.forEach((id, c) -> {
            b.writeVarInt(id);
            c.write(b);
        });
    }

    public static CrewSyncPacket decode(FriendlyByteBuf b) {
        int v = b.readVarInt(), f = b.readVarInt() - 1, n = b.readVarInt();
        Map<Integer, Crew> m = new LinkedHashMap<>();
        for (int i = 0; i < n && i < 64; i++) m.put(b.readVarInt(), Crew.read(b));
        return new CrewSyncPacket(v, m, f);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.aiforge.tubombers.client.ClientCrew.onSync(this));
        ctx.get().setPacketHandled(true);
    }
}
