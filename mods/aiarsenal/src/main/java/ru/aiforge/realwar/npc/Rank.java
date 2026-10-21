package ru.aiforge.realwar.npc;

/**
 * Squad ranks. {@code skill} is 0..1 and drives the aimed dispersion, the chance to pick a weak spot and the
 * reaction time: privates miss noticeably, sergeants and officers shoot well, dedicated crews and pilots are the
 * professionals that handle vehicles and aircraft.
 */
public enum Rank {
    PRIVATE("private", 0.58, 1),
    SERGEANT("sergeant", 0.78, 2),
    OFFICER("officer", 0.84, 3),
    CREW("crew", 0.90, 2),
    PILOT("pilot", 0.93, 1),
    /** Commands the squad / regiment; in a vehicle he picks targets instead of driving. */
    COMMANDER("commander", 0.88, 4);

    public final String id;
    public final double skill;
    /** 1 = rank and file, 2 = squad leader, 3 = officer. */
    public final int weight;

    Rank(String id, double skill, int weight) {
        this.id = id;
        this.skill = skill;
        this.weight = weight;
    }

    public boolean crew() {
        return this == CREW;
    }

    public boolean pilot() {
        return this == PILOT;
    }

    public String langKey() {
        return "npc.aiarsenal.rank." + this.id;
    }

    public static Rank byId(String id) {
        for (Rank r : values()) {
            if (r.id.equalsIgnoreCase(id)) return r;
        }
        return null;
    }
}
