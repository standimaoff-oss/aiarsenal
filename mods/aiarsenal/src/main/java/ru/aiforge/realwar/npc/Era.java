package ru.aiforge.realwar.npc;

/** Historical eras the NPC armies are raised for. */
public enum Era {
    NAPOLEONIC("napoleonic", 1812),
    WW2("ww2", 1943),
    MODERN("modern", 2023);

    public final String id;
    public final int year;

    Era(String id, int year) {
        this.id = id;
        this.year = year;
    }

    public String langKey() {
        return "npc.aiarsenal.era." + this.id;
    }

    public static Era byId(String id) {
        for (Era e : values()) {
            if (e.id.equalsIgnoreCase(id)) return e;
        }
        return null;
    }
}
