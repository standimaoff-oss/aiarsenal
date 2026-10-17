package ru.aiforge.realwar.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.aiforge.realwar.damage.VehicleDamage;

/**
 * r20 S->C (to the shooter only): War Thunder style hit camera of one shot. Everything in the hull frame of the
 * target (x left, y up, z forward): entry point, flight direction, plate normal; the client re-generates the spall
 * cone from the seed with the same {@code Armour.spall} as the server.
 */
public record HitCamPacket(int target, int kind, int flags, float az, float el, int result, int zone, float nominal, float eff, float angle,
                           float pen, float[] entry, float[] dir, float[] normal, String ammo, int ammoType, float dist, float tEnd,
                           long seed, int spallN, float spallAng, int[] mods, int[] states, int crewHit, int crewDead) {
    public static final int F_TRACKED = 1, F_TURRET = 2, F_TURRET_GONE = 4, F_VIA_TRACK = 8, F_OVERMATCH = 16, F_CREW = 32, F_DESTROYED = 64;

    public static HitCamPacket of(VehicleDamage.Cam c) {
        int f = (c.tracked() ? F_TRACKED : 0) | (c.turret() ? F_TURRET : 0) | (c.turretGone() ? F_TURRET_GONE : 0)
                | (c.res().viaTrack() ? F_VIA_TRACK : 0) | (c.res().overmatch() ? F_OVERMATCH : 0) | (c.crew() ? F_CREW : 0)
                | (c.destroyed() ? F_DESTROYED : 0);
        return new HitCamPacket(c.target(), c.kind().ordinal(), f, c.az(), c.el(), c.res().result(), c.res().zone(), c.res().nominal(),
                c.res().eff(), c.res().angle(), c.res().pen(),
                new float[]{(float) c.res().entry().x, (float) c.res().entry().y, (float) c.res().entry().z},
                new float[]{(float) c.dir().x, (float) c.dir().y, (float) c.dir().z},
                new float[]{(float) c.res().normal().x, (float) c.res().normal().y, (float) c.res().normal().z},
                c.ammo().name(), c.ammo().type().ordinal(), c.dist(), c.tEnd(), c.seed(), c.spallN(), c.spallAng(), c.mods(), c.states(),
                c.crewHit(), c.crewDead());
    }

    private static void v3(FriendlyByteBuf b, float[] v) {
        b.writeFloat(v[0]);
        b.writeFloat(v[1]);
        b.writeFloat(v[2]);
    }

    private static float[] r3(FriendlyByteBuf b) {
        float x = b.readFloat();
        float y = b.readFloat();
        float z = b.readFloat();
        return new float[]{x, y, z};
    }

    public static void encode(HitCamPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.target);
        b.writeVarInt(m.kind);
        b.writeVarInt(m.flags);
        b.writeFloat(m.az);
        b.writeFloat(m.el);
        b.writeByte(m.result);
        b.writeByte(m.zone);
        b.writeFloat(m.nominal);
        b.writeFloat(m.eff);
        b.writeFloat(m.angle);
        b.writeFloat(m.pen);
        v3(b, m.entry);
        v3(b, m.dir);
        v3(b, m.normal);
        b.writeUtf(m.ammo, 64);
        b.writeByte(m.ammoType);
        b.writeFloat(m.dist);
        b.writeFloat(m.tEnd);
        b.writeLong(m.seed);
        b.writeByte(m.spallN);
        b.writeFloat(m.spallAng);
        b.writeByte(m.mods.length);
        for (int i = 0; i < m.mods.length; i++) {
            b.writeByte(m.mods[i]);
            b.writeByte(m.states[i]);
        }
        b.writeByte(m.crewHit);
        b.writeByte(m.crewDead);
    }

    public static HitCamPacket decode(FriendlyByteBuf b) {
        int target = b.readVarInt();
        int kind = b.readVarInt();
        int flags = b.readVarInt();
        float az = b.readFloat();
        float el = b.readFloat();
        int result = b.readByte();
        int zone = b.readByte();
        float nominal = b.readFloat();
        float eff = b.readFloat();
        float angle = b.readFloat();
        float pen = b.readFloat();
        float[] entry = r3(b);
        float[] dir = r3(b);
        float[] normal = r3(b);
        String ammo = b.readUtf(64);
        int ammoType = b.readByte();
        float dist = b.readFloat();
        float tEnd = b.readFloat();
        long seed = b.readLong();
        int spallN = b.readByte();
        float spallAng = b.readFloat();
        int n = b.readByte();
        int[] mods = new int[n], states = new int[n];
        for (int i = 0; i < n; i++) {
            mods[i] = b.readByte();
            states[i] = b.readByte();
        }
        int crewHit = b.readUnsignedByte();
        int crewDead = b.readUnsignedByte();
        return new HitCamPacket(target, kind, flags, az, el, result, zone, nominal, eff, angle, pen, entry, dir, normal, ammo, ammoType, dist, tEnd,
                seed, spallN, spallAng, mods, states, crewHit, crewDead);
    }

    public static void handle(HitCamPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.aiforge.realwar.client.HitCam.receive(m)));
        ctx.get().setPacketHandled(true);
    }
}
