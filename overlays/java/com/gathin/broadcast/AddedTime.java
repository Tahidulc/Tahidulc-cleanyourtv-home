package com.gathin.broadcast;

/**
 * Added time, one way for every look on the TV and the phone (the nine first looks, the 3D kits, the Scoreboard Lab's
 * five, every competition's own and the four retro boards): once the minutes added are put up, the ADDED TIME pop-out
 * comes out of the board ("+4 minutes", with the swish where swishes are on); while they run, either that panel stays
 * out tucked under the board's end ("Added time stays out") and the board keeps no +N tab of its own, or the +N tab
 * stands at the board's end. The tab is drawn by the board, beside whatever look it is in, along the look's own main row
 * (a competition's look with a tab over or under its row: beside the row), at the foot of a tall look, else across its
 * middle; a look that cannot write the minutes inside beside its clock (the competitions' and the Lab's, drawn by
 * CompBug) always has the tab, however narrow it is kept, so no look ever loses them.
 *
 * <p>Pure: both boards ask here.
 */
public final class AddedTime {
    private AddedTime() { }

    /**
     * Added time's one red, on every look of both boards: Card's middle as it turns red, every look's clock in added time,
     * the +N tab, the ADDED TIME pop-out's chip and the competitions' and the Lab's looks (CompBug). One colour from one
     * place, so the board and what comes out of it are the same red (they were four reds: the family saw two on Card).
     */
    public static final int RED = 0xFFD7192F;

    /** A look keeps room for itself beside the tab: its own part at least this many of its heights (Card's more). */
    public static final float LEAST = 2f, LEAST_CARD = 2.6f;

    /** Pure: the minutes added in the board's own tab: none while their panel stands out beside it (it says them). */
    public static String boardAdded(String added, boolean panelStands) {
        return panelStands || added == null ? "" : added;
    }

    /**
     * Pure: whether the minutes added stay out beside the board now: the game in play on the board (not half time nor full
     * time), still the half they came in.
     */
    public static boolean stands(boolean inPlay, boolean halftime, boolean live, int period, int standingPeriod) {
        return inPlay && !halftime && live && period == standingPeriod;
    }

    /**
     * Pure: whether the minutes added stand in their tab beside the board (else inside it, by its clock): there are some,
     * and either the look cannot write them inside (drawsInside false: a competition's or the Lab's look) or its own part
     * keeps at least least of its heights beside the tab.
     */
    public static boolean outside(boolean drawsInside, int own, int room, int h, float least) {
        if (room <= 0) return false;
        return !drawsInside || own >= least * h;
    }

    /** Pure: whether a look can write the minutes inside, beside its clock: every look but CompBug's (kind not null). */
    public static boolean drawsInside(CompBug.Kind kind) {
        return kind == null;
    }

    /**
     * Pure: the tab's band at board height h, {top, height}: beside a competition look's main row (CompBug.bar: its row's
     * middle, 0.8 of the row; a tall one with no row of its own, beside its foot row), at the foot of a tall look (half of
     * it), else across the middle (0.8 of it).
     */
    public static float[] band(CompBug.Kind kind, boolean tall, float h) {
        if (kind != null) {
            float[] row = CompBug.bar(kind, h);
            if (tall(kind.tall) && row[0] <= 0.01f && row[1] >= h - 0.01f) {
                float unit = h / kind.tall;
                row = new float[]{h - unit, h};
            }
            float rh = Math.max(1f, row[1] - row[0]), th = rh * 0.8f;
            return new float[]{row[0] + (rh - th) / 2f, th};
        }
        if (tall) return new float[]{h * 0.5f, h * 0.5f};
        return new float[]{h * 0.1f, h * 0.8f};
    }

    /** Pure: a look is drawn tall (the tab on its foot): more than 1.2 of a row high. */
    public static boolean tall(float rows) {
        return rows > 1.2f;
    }
}
