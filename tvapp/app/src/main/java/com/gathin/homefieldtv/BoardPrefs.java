package com.gathin.homefieldtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;

/**
 * The scoreboard's settings in Homefield TV's player, as the launcher keeps them for its board over TiviMate: on or off,
 * its look (By competition unless picked), its size and its corner, the swishes; and the board's own lettering. Kept on
 * this TV.
 */
final class BoardPrefs {
    private BoardPrefs() {}

    private static final String PREFS = "scoreboard";

    /**
     * Where the scoreboard and the cover over the channel's logo sit, and how big, as the launcher's BoardPlace keeps them:
     * fractions of the screen, the board by its left, top and height (its width follows its words), the cover by its
     * left, top, width and height. Kept a channel at a time (one channel's broadcaster puts its own board and logo where
     * it does), a channel with none of its own taking the last set on any, else the standard places, where most
     * broadcasters put theirs: the scoreboard top left, over their own, and the cover top right, over their logo.
     */
    static final class Place {
        float bx, by, bh, lx, ly, lw, lh;

        static final float MIN_H = 0.03f, MAX_H = 0.12f, MIN_W = 0.03f, MAX_W = 0.3f, MIN_LH = 0.02f, MAX_LH = 0.2f;

        static Place standard() {
            Place p = new Place();
            p.bx = 0.045f;
            p.by = 0.045f;
            p.bh = 0.045f;
            p.lx = 0.855f;
            p.ly = 0.045f;
            p.lw = 0.1f;
            p.lh = 0.065f;
            return p;
        }

        /** Pure: as kept, "bx,by,bh,lx,ly,lw,lh"; null for words that aren't one. */
        static Place parse(String s) {
            if (s == null) return null;
            String[] f = s.split(",");
            if (f.length != 7) return null;
            try {
                Place p = new Place();
                p.bx = Float.parseFloat(f[0]);
                p.by = Float.parseFloat(f[1]);
                p.bh = Float.parseFloat(f[2]);
                p.lx = Float.parseFloat(f[3]);
                p.ly = Float.parseFloat(f[4]);
                p.lw = Float.parseFloat(f[5]);
                p.lh = Float.parseFloat(f[6]);
                return p;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        String words() {
            return bx + "," + by + "," + bh + "," + lx + "," + ly + "," + lw + "," + lh;
        }

        /** Pure: the board moved by (dx, dy), kept on the screen (aspect: its width over its height as shares of the screen's). */
        void moveBoard(float dx, float dy, float aspect) {
            bx = clamp(bx + dx, 0, Math.max(0, 1 - bh * aspect));
            by = clamp(by + dy, 0, Math.max(0, 1 - bh));
        }

        /** Pure: the board's height (and so its width) times k, its top left kept, kept on the screen. */
        void sizeBoard(float k, float aspect) {
            bh = clamp(bh * k, MIN_H, MAX_H);
            moveBoard(0, 0, aspect);
        }

        /** Pure: the cover moved by (dx, dy), kept on the screen. */
        void moveLogo(float dx, float dy) {
            lx = clamp(lx + dx, 0, Math.max(0, 1 - lw));
            ly = clamp(ly + dy, 0, Math.max(0, 1 - lh));
        }

        /** Pure: the cover's size times k (both ways), its top left kept, kept on the screen. */
        void sizeLogo(float k) {
            lw = clamp(lw * k, MIN_W, MAX_W);
            lh = clamp(lh * k, MIN_LH, MAX_LH);
            moveLogo(0, 0);
        }

        static float clamp(float v, float lo, float hi) {
            return Math.max(lo, Math.min(hi, v));
        }
    }

    /** A channel's places: its own, else the last set on any channel, else the standard ones. */
    static Place place(Context c, String channelKey) {
        SharedPreferences p = prefs(c);
        Place own = Place.parse(p.getString("place." + (channelKey == null ? "" : channelKey), null));
        if (own != null) return own;
        Place last = Place.parse(p.getString("place.last", null));
        return last != null ? last : Place.standard();
    }

    static void setPlace(Context c, String channelKey, Place pl) {
        SharedPreferences.Editor e = prefs(c).edit().putString("place.last", pl.words());
        if (channelKey != null && !channelKey.isEmpty()) e.putString("place." + channelKey, pl.words());
        e.apply();
    }

    /** Back to the standard places for this channel (and the default for the rest). */
    static void resetPlace(Context c, String channelKey) {
        SharedPreferences.Editor e = prefs(c).edit().remove("place.last");
        if (channelKey != null) e.remove("place." + channelKey);
        e.apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean on(Context c) {
        return prefs(c).getBoolean("on", true);
    }

    static void setOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("on", on).apply();
    }

    /** The look (BoardBug.Style), By competition unless picked. */
    static BoardBug.Style style(Context c) {
        String s = prefs(c).getString("style", BoardBug.Style.COMPETITION.name());
        BoardBug.Style st = BoardBug.Style.of(s);
        // The 3D kits need the shirts' pictures the launcher makes; the TV app draws the board's own looks.
        return st == BoardBug.Style.KITS ? BoardBug.Style.COMPETITION : st;
    }

    static void setStyle(Context c, BoardBug.Style s) {
        prefs(c).edit().putString("style", s.name()).apply();
    }

    /** The looks offered, in the launcher's order (all but the 3D kits). */
    static BoardBug.Style[] looks() {
        java.util.List<BoardBug.Style> out = new java.util.ArrayList<>();
        out.add(BoardBug.Style.COMPETITION);
        for (BoardBug.Style s : BoardBug.Style.values()) if (s != BoardBug.Style.KITS && s != BoardBug.Style.COMPETITION) out.add(s);
        return out.toArray(new BoardBug.Style[0]);
    }

    /** The line-ups' tab swish as anything slides out beside the board: on unless switched off. */
    static boolean swishOn(Context c) {
        return prefs(c).getBoolean("swish", true);
    }

    static void setSwishOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("swish", on).apply();
    }

    /** The family's swish as a goal's new score comes out on the board: on unless switched off. */
    static boolean scoreSwishOn(Context c) {
        return prefs(c).getBoolean("score_swish", true);
    }

    static void setScoreSwishOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("score_swish", on).apply();
    }

    /**
     * Pure: whether a Lab sound is heard: only while what it is for is up on the screen (and not faded away), and only
     * with the swish switched on (the launcher's BoardLab.heard).
     */
    static boolean heard(boolean up, float alpha, boolean swishOn) {
        return up && alpha >= 0.05f && swishOn;
    }

    private static final java.util.Map<Integer, Typeface> outfits = new java.util.HashMap<>();

    /**
     * Outfit at a weight for the board's words, as the launcher's ScoreCard.outfit: each weight its own resource with the
     * weight set in it (asking the variable font file for a weight draws its thin default instance).
     */
    static Typeface outfit(Context c, int weight) {
        int w = weight >= 800 ? 800 : weight >= 700 ? 700 : weight >= 600 ? 600 : 500;
        Typeface t = outfits.get(w);
        if (t != null) return t;
        int res = w == 800 ? R.font.outfit_extrabold : w == 700 ? R.font.outfit_bold : w == 600 ? R.font.outfit_semibold : R.font.outfit_medium;
        try {
            t = c.getResources().getFont(res);
        } catch (RuntimeException e) {
            t = Typeface.create(Dark.outfit, w >= 700 ? Typeface.BOLD : Typeface.NORMAL);
        }
        outfits.put(w, t);
        return t;
    }

    /** A milestone's gold and a booking's yellow (the launcher's Milestones), for the alerts beside the board. */
    static final int GOLD = 0xFFFFC21A, YELLOW = 0xFFFFD21F;

    /**
     * Pure: a turn from one page to the next at t (0 to 1), as the launcher's BoardStats.turnParts: {the last one's way
     * gone (0 to 1), its strength, this one's way still to come (1 to 0), its strength}: the last gone before this one
     * shows, so the two never overlap.
     */
    static float[] turnParts(float t) {
        if (t >= 1f) return new float[]{1f, 0f, 0f, 1f};
        if (t < 0.5f) {
            float p = t / 0.5f;
            return new float[]{p, 1f - p, 1f, 0f};
        }
        float p = (t - 0.5f) / 0.5f;
        return new float[]{1f, 0f, 1f - p, p};
    }

    // ---- the launcher's other switches for the board ----------------------------------------------------------------

    /** The alerts sliding out beside the board (goals, cards, subs, VAR, added time, half and full time): on unless off. */
    static boolean dropsOn(Context c) {
        return prefs(c).getBoolean("drops", true);
    }

    static void setDropsOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("drops", on).apply();
    }

    /** VAR explained: a check out of the board as it starts, then the decision and its why. On unless off. */
    static boolean varExplainedOn(Context c) {
        return prefs(c).getBoolean("var", true);
    }

    static void setVarExplainedOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("var", on).apply();
    }

    /** The kick-off pop-up (and the second half's, and after a goal): on unless off. */
    static boolean popupOn(Context c) {
        return prefs(c).getBoolean("popup", true);
    }

    static void setPopupOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("popup", on).apply();
    }

    /** The classic line-ups before kick-off: on unless off. */
    static boolean lineupsOn(Context c) {
        return prefs(c).getBoolean("lineups", true);
    }

    static void setLineupsOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("lineups", on).apply();
    }

    /** The cover over the channel's own logo while the board is up: on unless off. */
    static boolean logoOn(Context c) {
        return prefs(c).getBoolean("logo", true);
    }

    static void setLogoOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("logo", on).apply();
    }

    /** The board's clock as set at a kick-off on the screen (BoardClock.anchorWords), the last game's; null for none. */
    static String anchor(Context c) {
        return prefs(c).getString("anchor", null);
    }

    static void setAnchor(Context c, String words) {
        prefs(c).edit().putString("anchor", words).apply();
    }

    /** The goal sound over the board's goal animation (Sfx.GOALS), the family's swoosh unless another is picked. */
    static String goalSound(Context c) {
        return prefs(c).getString("goal_sound", com.gathin.broadcast.Sfx.GOAL_DEFAULT);
    }

    static void setGoalSound(Context c, String key) {
        prefs(c).edit().putString("goal_sound", key).apply();
    }
}
