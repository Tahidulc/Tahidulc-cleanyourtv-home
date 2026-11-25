package com.gathin.homefieldtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;

/**
 * The scoreboard's settings in Homefield TV's player, as the launcher keeps them for its board over TiviMate: the board
 * and the logo's cover on or off, its look (By competition unless picked), its size and its corner, the swishes; and the
 * board's own lettering. Kept on this TV. All of them are set in the scoreboard and logo menu over the player (hold OK
 * while a channel plays full screen: BoardMenu); Settings has no page for them.
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

    /** Back to the standard places on every channel (the menu's Back to the standard places, Every channel). */
    static void resetAllPlaces(Context c) {
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit();
        for (String k : p.getAll().keySet()) if (k.startsWith("place.")) e.remove(k);
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

    /**
     * The menu's "Scoreboard" switch, as the launcher's over TiviMate (BoardCover.boardShown): on, the board over a match;
     * off, the board, what slides out beside it and its pop-ups (the strap, the kick-off's, the line-ups before kick-off)
     * away until it is switched on again, the logo's cover staying as its own switch says, and the game followed
     * underneath (its clock and score go on, so it comes back right). The phone's Scoreboard switch sets it too.
     */
    static boolean on(Context c) {
        return switched(c).getBoolean("on", true);
    }

    static void setOn(Context c, boolean on) {
        switched(c).edit().putBoolean("on", on).apply();
    }

    /** Set once the three switches kept before (Scoreboard, Logo cover, Logo cover only) became the menu's two (Nov 2026). */
    private static final String TWO_SWITCHES = "two_switches";

    /** The settings, with the three switches kept before made the two the menu has (once). */
    private static SharedPreferences switched(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(TWO_SWITCHES, false)) {
            boolean[] two = twoSwitches(p.getBoolean("on", true), p.getBoolean("logo", true), p.getBoolean("logo_only", false));
            p.edit().putBoolean("on", two[0]).putBoolean("logo", two[1]).remove("logo_only").putBoolean(TWO_SWITCHES, true).apply();
        }
        return p;
    }

    /**
     * Pure: the menu's two switches {Scoreboard, Logo's cover} for the three kept before, showing what they showed: Logo
     * cover only, the cover alone; the scoreboard off, nothing; else the board, with the cover if it was on.
     */
    static boolean[] twoSwitches(boolean on, boolean logo, boolean logoOnly) {
        if (logoOnly) return new boolean[]{false, true};
        if (!on) return new boolean[]{false, false};
        return new boolean[]{true, logo};
    }

    /** The look (BoardBug.Style), By competition unless picked. */
    static BoardBug.Style style(Context c) {
        String s = prefs(c).getString("style", BoardBug.Style.COMPETITION.name());
        BoardBug.Style st = BoardBug.Style.of(s);
        // The 3D kits need the shirts' pictures the launcher makes; the TV app draws the board's own looks.
        return st == BoardBug.Style.KITS ? BoardBug.Style.COMPETITION : st;
    }

    /** A look chosen: one of My scoreboards in use is let go (the look as chosen shows), as the launcher's. */
    static void setStyle(Context c, BoardBug.Style s) {
        prefs(c).edit().putString("style", s.name()).remove(CUSTOM).apply();
    }

    private static final String CUSTOM = "custom";

    /** The family's own board in use (My scoreboards: its id), or null for the look as chosen. Kept on this TV. */
    static String customId(Context c) {
        String id = prefs(c).getString(CUSTOM, null);
        return id == null || id.isEmpty() ? null : id;
    }

    /** One of My scoreboards in use (its id), or null to go back to the look as chosen. */
    static void setCustom(Context c, String id) {
        if (id == null || id.isEmpty()) prefs(c).edit().remove(CUSTOM).apply();
        else prefs(c).edit().putString(CUSTOM, id).apply();
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

    /**
     * The kick-off pop-up's style (and the restart's after a goal, and the second half's): "strap" (in my scoreboard's look,
     * whichever look the board is in, folding into it where it is: a new install's), "classic" (crests, trophy and teams)
     * or "off", as on the phone and the launcher. Once, a pop-up that was on (the classic, the only one here before) was
     * switched to the strap, as the family asked (followBoard); off stays off.
     */
    static String popupStyle(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(FOLLOWS_BOARD, false)) {
            p.edit().putString("popup_style", followBoard(p.getString("popup_style", null), p.getBoolean("popup", true)))
                    .putBoolean(FOLLOWS_BOARD, true).apply();
        }
        String s = p.getString("popup_style", null);
        return s != null ? s : "strap";
    }

    static void setPopupStyle(Context c, String style) {
        prefs(c).edit().putString("popup_style", style).apply();
    }

    /** The kick-off pop-up (and the second half's, and after a goal), in either style: on unless off. */
    static boolean popupOn(Context c) {
        return !"off".equals(popupStyle(c));
    }

    /** Set once the pop-up was switched to follow the board (Nov 2026). */
    private static final String FOLLOWS_BOARD = "popup_follows_board";

    /**
     * Pure: the pop-up's style after the one switch to the board's look: the strap for one that was on (the old switch,
     * or the classic); off stays off; the strap stays.
     */
    static String followBoard(String style, boolean wasOn) {
        if ("off".equals(style)) return "off";
        if (style == null) return wasOn ? "strap" : "off";
        return "strap";
    }

    /** The pop-up's styles, in the order OK and the arrows step through them. */
    static final String[] POPUPS = {"strap", "classic", "off"};

    /** Pure: the style step away from style (1 the next, -1 the one before), round the three. */
    static String nextPopup(String style, int step) {
        int i = 0;
        for (int k = 0; k < POPUPS.length; k++) if (POPUPS[k].equals(style)) i = k;
        return POPUPS[((i + step) % POPUPS.length + POPUPS.length) % POPUPS.length];
    }

    /** Pure: a style in words, as the phone's and the launcher's settings say it. */
    static String popupWords(String style) {
        return "strap".equals(style) ? "In my scoreboard's look" : "classic".equals(style) ? "Classic" : "Off";
    }

    /** The classic line-ups before kick-off: on unless off. */
    static boolean lineupsOn(Context c) {
        return prefs(c).getBoolean("lineups", true);
    }

    static void setLineupsOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("lineups", on).apply();
    }

    /**
     * The menu's "Logo's cover" switch (the launcher's logoOn): the cover over the channel's own logo while a match is on,
     * with the scoreboard or without it (the scoreboard off and the cover on is what "Logo cover only" was). On unless off.
     */
    static boolean logoOn(Context c) {
        return switched(c).getBoolean("logo", true);
    }

    static void setLogoOn(Context c, boolean on) {
        switched(c).edit().putBoolean("logo", on).apply();
    }

    // ---- the logo's look (the launcher's logo looks over TiviMate, CoverLooks) ----------------------------------------

    /**
     * The cover's look as it came: the channel's own logo on a plate in the scoreboard's look (CoverArt), what the cover
     * has always been here, so what the family had stays as it was until they pick another.
     */
    static final String CHANNEL_LOGO = "CHANNEL";

    /**
     * The looks the menu offers, in its order: the channel's own logo first (as it came), then the launcher's over TiviMate
     * that both apps draw the same (CoverLooks: Glass, Mark only, Plain, both Broadcasts, the competition's logo, the
     * Scoreboard Lab's five), as the launcher's Scoreboard card orders them.
     */
    static String[] logoLooks() {
        com.gathin.broadcast.CoverLooks.Look[] all = com.gathin.broadcast.CoverLooks.Look.values();
        String[] out = new String[all.length + 1];
        out[0] = CHANNEL_LOGO;
        for (int i = 0; i < all.length; i++) out[i + 1] = all[i].name();
        return out;
    }

    /** The look chosen for the cover over the channel's logo: CHANNEL_LOGO, or a CoverLooks.Look's name. Kept on this TV. */
    static String logoLook(Context c) {
        return logoLookOf(prefs(c).getString("logo_look", CHANNEL_LOGO));
    }

    static void setLogoLook(Context c, String look) {
        prefs(c).edit().putString("logo_look", logoLookOf(look)).apply();
    }

    /** Pure: a kept look as one the menu offers (anything else, or none: the channel's own logo). */
    static String logoLookOf(String kept) {
        return com.gathin.broadcast.CoverLooks.Look.find(kept) != null ? kept : CHANNEL_LOGO;
    }

    /** Pure: the look step away from now (1 the next, -1 the one before), round the menu's list. */
    static String nextLogoLook(String now, int step) {
        String[] all = logoLooks();
        int at = 0;
        for (int i = 0; i < all.length; i++) if (all[i].equals(logoLookOf(now))) at = i;
        return all[Math.floorMod(at + step, all.length)];
    }

    /** Pure: a look in words, as the launcher's Scoreboard card names it. */
    static String logoLookWords(String look) {
        com.gathin.broadcast.CoverLooks.Look l = com.gathin.broadcast.CoverLooks.Look.find(look);
        return l == null ? "The channel's own logo" : l.label;
    }

    /**
     * The picture tag on the cover (Broadcast's box, the Lab's logos), as the launcher's: null for Auto, from the channel's
     * name (HD, UHD, 4K, with HDR or not), "" for none, or one set (CoverLooks.TAGS). Kept on this TV.
     */
    static String logoTag(Context c) {
        String t = prefs(c).getString("logo_tag", "AUTO");
        return tagOf(t);
    }

    static void setLogoTag(Context c, String tag) {
        prefs(c).edit().putString("logo_tag", tag == null ? "AUTO" : tag).apply();
    }

    /** Pure: a kept tag as a choice (AUTO, or anything not one of CoverLooks.TAGS: null for Auto). */
    static String tagOf(String kept) {
        if (kept == null || "AUTO".equals(kept)) return null;
        for (String t : com.gathin.broadcast.CoverLooks.TAGS) if (t != null && t.equals(kept)) return t;
        return null;
    }

    /** Whitebrook Road styles: which of the 54 (BrandStyles, 0 to COUNT - 1), as the launcher's Back a style and Next style. */
    static int logoStyle(Context c) {
        return com.gathin.broadcast.BrandStyles.clamp(prefs(c).getInt("logo_style", 0));
    }

    static void setLogoStyle(Context c, int n) {
        prefs(c).edit().putInt("logo_style", com.gathin.broadcast.BrandStyles.clamp(n)).apply();
    }

    /** Pure: the style step away from n, round the 54. */
    static int nextStyle(int n, int step) {
        return Math.floorMod(com.gathin.broadcast.BrandStyles.clamp(n) + step, com.gathin.broadcast.BrandStyles.COUNT);
    }

    /** What the Sports wordmark says is on ("" for Auto, from the game: premier league, football, f1; else SportsMark.WHATS). */
    static String logoWhat(Context c) {
        String w = prefs(c).getString("logo_what", "");
        for (String k : com.gathin.broadcast.SportsMark.WHATS) if (k.equals(w)) return w;
        return "";
    }

    static void setLogoWhat(Context c, String what) {
        prefs(c).edit().putString("logo_what", what == null ? "" : what).apply();
    }

    /** Pure: the next of the wordmark's choices (Auto first), step on or back, round. */
    static String nextWhat(String now, int step) {
        String[] all = com.gathin.broadcast.SportsMark.WHATS;
        int at = 0;
        for (int i = 0; i < all.length; i++) if (all[i].equals(now == null ? "" : now)) at = i;
        return all[Math.floorMod(at + step, all.length)];
    }

    /** A general channel (the family's name alone, no SPORTS), else a sports one (Whitebrook Road Sports). */
    static boolean logoNameOnly(Context c) {
        return prefs(c).getBoolean("logo_name_only", false);
    }

    static void setLogoNameOnly(Context c, boolean on) {
        prefs(c).edit().putBoolean("logo_name_only", on).apply();
    }

    /** The looks drawn whole's background (CoverLooks.GROUNDS: plate, frosted, tempered, smoked glass, transparent). */
    static int logoGround(Context c) {
        int g = prefs(c).getInt("logo_ground", com.gathin.broadcast.CoverLooks.PLATE);
        for (int k : com.gathin.broadcast.CoverLooks.GROUNDS) if (k == g) return g;
        return com.gathin.broadcast.CoverLooks.PLATE;
    }

    static void setLogoGround(Context c, int g) {
        prefs(c).edit().putInt("logo_ground", g).apply();
    }

    /** Pure: the next background, step on or back, round the five in the launcher's order. */
    static int nextGround(int g, int step) {
        int[] all = com.gathin.broadcast.CoverLooks.GROUNDS;
        int at = 0;
        for (int i = 0; i < all.length; i++) if (all[i] == g) at = i;
        return all[Math.floorMod(at + step, all.length)];
    }

    /** The competition's logo beside every look (the Premier League's, the Champions League's): on unless off. */
    static boolean logoComp(Context c) {
        return prefs(c).getBoolean("logo_comp", true);
    }

    static void setLogoComp(Context c, boolean on) {
        prefs(c).edit().putBoolean("logo_comp", on).apply();
    }

    /** LIVE along the cover's foot while the game is live: on unless off. */
    static boolean logoLive(Context c) {
        return prefs(c).getBoolean("logo_live", true);
    }

    static void setLogoLive(Context c, boolean on) {
        prefs(c).edit().putBoolean("logo_live", on).apply();
    }

    /** A channel's own logo choice, as the launcher's This channel's logo: null as the rest, LEAVE, CHANNEL_LOGO or a look. */
    static final String LEAVE = "LEAVE";

    static String channelLogo(Context c, String channelKey) {
        String k = channelKey == null ? "" : channelKey;
        if (k.isEmpty()) return null;
        String v = prefs(c).getString("logo_here." + k, null);
        return v == null || !(LEAVE.equals(v) || CHANNEL_LOGO.equals(v) || com.gathin.broadcast.CoverLooks.Look.find(v) != null) ? null : v;
    }

    static void setChannelLogo(Context c, String channelKey, String look) {
        if (channelKey == null || channelKey.isEmpty()) return;
        if (look == null) prefs(c).edit().remove("logo_here." + channelKey).apply();
        else prefs(c).edit().putString("logo_here." + channelKey, look).apply();
    }

    /** Pure: the channel's own choice step on or back: as the rest (null), leave the channel's own, then each look, round. */
    static String nextChannelLogo(String now, int step) {
        String[] looks = logoLooks();
        String[] all = new String[looks.length + 2];
        all[0] = null;
        all[1] = LEAVE;
        System.arraycopy(looks, 0, all, 2, looks.length);
        int at = 0;
        for (int i = 0; i < all.length; i++) if (java.util.Objects.equals(all[i], now)) at = i;
        return all[Math.floorMod(at + step, all.length)];
    }

    /** Pure: a channel's own choice in words (general: the look the rest have). */
    static String channelLogoWords(String own, String general) {
        if (own == null) return "As the rest (" + logoLookWords(general) + ")";
        if (LEAVE.equals(own)) return "Leave the channel's own";
        return logoLookWords(own);
    }

    /** The look the cover is drawn in on this channel: its own, else the general one (LEAVE: no cover on it). */
    static String logoLookHere(Context c, String channelKey) {
        String own = channelLogo(c, channelKey);
        return own != null ? own : logoLook(c);
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

    /**
     * The board's goal animation (GoalLooks: as today unless the family chooses another: the GOAL band, the flip, the
     * burst, each club's own, the generic; Homefield TV has no 3D shirt), from the hold-OK menu.
     */
    static String goalLook(Context c) {
        String k = com.gathin.broadcast.GoalLooks.of(prefs(c).getString("goal_look", com.gathin.broadcast.GoalLooks.DEFAULT));
        return com.gathin.broadcast.GoalLooks.SHIRT.equals(k) ? com.gathin.broadcast.GoalLooks.DEFAULT : k;
    }

    static void setGoalLook(Context c, String key) {
        prefs(c).edit().putString("goal_look", com.gathin.broadcast.GoalLooks.of(key)).apply();
    }
}
