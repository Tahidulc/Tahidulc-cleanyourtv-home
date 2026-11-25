package com.gathin.broadcast;

/**
 * The choice of goal animation on the scoreboard, the same values and words in all three apps (each keeps its own
 * preference, as it keeps its board's others): the animations the boards have had until now, each on its own, and the
 * new ones (GoalReveal): every club of the five leagues its own (ClubGoals), any other team the generic, or the generic
 * for every team. "As today" is the default: the board does what it did before the choice came (the GOAL band, then the
 * crest's burst as the score rolls up, then the net; the 3D kits look its gold GOAL). Pure Java.
 */
public final class GoalLooks {
    private GoalLooks() { }

    /** The values, as kept. */
    public static final String TODAY = "today", BAND = "band", FLIP = "flip", BURST = "burst", SHIRT = "shirt", CLUBS = "clubs",
            GENERIC = "generic";
    /** In the order offered. */
    public static final String[] ALL = {TODAY, BAND, FLIP, BURST, SHIRT, CLUBS, GENERIC};
    /** Nothing changes for the family until they choose. */
    public static final String DEFAULT = TODAY;

    private static final String[] NAMES = {"As today", "GOAL band", "Flip", "Burst", "3D shirt", "Club animations", "Generic"};
    private static final String[] NOTES = {
            "the GOAL band across the board, the crest bursting as the score rolls up, then the net",
            "the scorers' colour across the board with their crest and GOAL, the new score popping up",
            "the scorers' side washing in their colour, a shine, the new figure turning in, then the net",
            "the scorers' crest popping with rings in their colour as the score rolls up, then the net",
            "the GOAL band, and the scorer's shirt turning out of the board to his name and number",
            "each club of the five big leagues its own: its colours sweeping, its crest turning, what it is known by, GOAL!; "
                    + "any other team the generic",
            "every team the same: its colours sweeping, its crest turning, a football, GOAL!"};

    /** Pure: a kept value as one of ALL (anything else, or nothing kept, the default). */
    public static String of(String kept) {
        for (String k : ALL) if (k.equals(kept)) return k;
        return DEFAULT;
    }

    /** Pure: the choice step places on (1 the next, -1 the one before), round from the last to the first. */
    public static String next(String now, int step) {
        int i = indexOf(of(now));
        return ALL[Math.floorMod(i + (step < 0 ? -1 : 1), ALL.length)];
    }

    /** Pure: the choices an app offers, in order: all of them, or without the 3D shirt in an app that has none (Homefield TV). */
    public static String[] offered(boolean shirt) {
        if (shirt) return ALL.clone();
        String[] out = new String[ALL.length - 1];
        int n = 0;
        for (String k : ALL) if (!SHIRT.equals(k)) out[n++] = k;
        return out;
    }

    /** Pure: the choice step places on among those an app offers (round from the last to the first). */
    public static String next(String now, int step, boolean shirt) {
        String[] all = offered(shirt);
        String k = of(now);
        int i = 0;
        for (int j = 0; j < all.length; j++) if (all[j].equals(k)) i = j;
        if (!shirt && SHIRT.equals(k)) i = indexOf(BAND);
        return all[Math.floorMod(i + (step < 0 ? -1 : 1), all.length)];
    }

    private static int indexOf(String k) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(k)) return i;
        return 0;
    }

    /** Pure: its name as the menus show it. */
    public static String name(String key) {
        return NAMES[indexOf(of(key))];
    }

    /** Pure: what it does, in a few words. */
    public static String note(String key) {
        return NOTES[indexOf(of(key))];
    }

    /** Pure: one of the new animations (GoalReveal): each club's own, or the generic for all. */
    public static boolean reveal(String key) {
        String k = of(key);
        return CLUBS.equals(k) || GENERIC.equals(k);
    }

    /** Pure: the scorer's 3D shirt turns out of the board with it, whatever the shirt's own switch says. */
    public static boolean shirt(String key) {
        return SHIRT.equals(of(key));
    }

    /**
     * Pure: the board's goal set (BoardFx's) for a choice: "As today" the game's own set as the boards have it
     * (BoardFx.goalSet: the sequence); the GOAL band (and the 3D shirt's) BoardFx.GOAL_BAND, the flip and the burst theirs,
     * the new ones BoardFx.GOAL_REVEAL.
     */
    public static int set(String key, int todays) {
        switch (of(key)) {
            case BAND:
            case SHIRT:
                return BoardFx.GOAL_BAND;
            case FLIP:
                return BoardFx.GOAL_FLIP;
            case BURST:
                return BoardFx.GOAL_BURST;
            case CLUBS:
            case GENERIC:
                return BoardFx.GOAL_REVEAL;
            default:
                return todays;
        }
    }

    /**
     * Pure: the animation a goal plays under a choice: the club's own when Club animations is chosen and the club is one
     * of the 96 (by ESPN's id, else its names), else the generic in the team's own colour (the board's); null when the
     * choice is not one of the new ones.
     */
    public static ClubGoals.Club clubFor(String key, String espnId, int colour, String... names) {
        String k = of(key);
        if (!reveal(k)) return null;
        ClubGoals.Club c = CLUBS.equals(k) ? ClubGoals.find(espnId, names) : null;
        return c != null ? c : ClubGoals.generic(colour);
    }
}
