package ru.aiforge.tubombers.entity;

import net.minecraft.network.FriendlyByteBuf;

/** Where one crew member is inside the aircraft. Coordinates are model space (x left, y up, z forward). */
public final class Crew {
    public static final int WALK = 0, LADDER = 1, SEAT = 2;

    public int mode = WALK;
    /** seat index for SEAT, ladder index for LADDER */
    public int index;
    public float x, y, z;
    // client interpolation for remote crew
    public float px, py, pz;
    public int sneakTicks;

    public Crew() {
    }

    public Crew(int mode, int index, float x, float y, float z) {
        set(mode, index, x, y, z);
        px = x;
        py = y;
        pz = z;
    }

    public void set(int mode, int index, float x, float y, float z) {
        this.mode = mode;
        this.index = index;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void write(FriendlyByteBuf b) {
        b.writeByte(mode);
        b.writeByte(index);
        b.writeFloat(x);
        b.writeFloat(y);
        b.writeFloat(z);
    }

    public static Crew read(FriendlyByteBuf b) {
        return new Crew(b.readByte(), b.readByte(), b.readFloat(), b.readFloat(), b.readFloat());
    }

    public String modeName() {
        return switch (mode) {
            case WALK -> "walk";
            case LADDER -> "ladder";
            default -> "seat";
        };
    }
}
