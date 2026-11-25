package com.gathin.broadcast;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * An alert handed from the TV launcher to Homefield TV's scoreboard, to slide out of it as it slides out of the
 * launcher's board over TiviMate (BoardCover.take, BoardDrop): one source of alerts, the launcher's (its scores' service
 * reads the games the family follows, holds each goal a minute, and passes it through Settings › Notifications), and one
 * place they show, the board in front of the family. While Homefield TV plays a channel full screen with its scoreboard
 * up, the launcher offers each football alert to it first (Homefield TV's ChannelCardsProvider, call BOARD_ALERT, behind
 * the two apps' signature permission); taken, the launcher shows no card of its own (no doubles); not taken (the board
 * away, resting, covered by the guide or a menu, switched off), the launcher's card shows as it does over TiviMate then.
 *
 * <p>Homefield TV takes another game's alert at once (it slides out beside the board in its look: the chip, both crests
 * and the score, the scorer, the clubs' colours down its edge), and its own game's too when its board does not say that
 * kind itself (the ratings, the weather, the predictions); its own game's goals, cards, half and full time, kick-off,
 * line-ups and VAR it says itself, from its own reading of the game as the picture reaches it, so the launcher's is
 * taken and let go. ALERT_SWITCHES (the launcher's SyncTokenProvider) gives Homefield TV the family's switches for its
 * own game's alerts, so one switch in the launcher's Notifications controls both apps.
 *
 * <p>The words and colours as the launcher's board made them; the pictures (the player's, both crests, a page's)
 * as small PNGs, PICTURE_MAX across at most, so a hand-over stays far under Binder's limit.
 */
public final class BoardAlert {
    /** Homefield TV's provider and its method; the launcher's provider and its method for the switches. */
    public static final String HOMEFIELD_CARDS = "content://com.gathin.homefieldtv.cards", BOARD_ALERT = "board_alert",
            LAUNCHER_SYNC = "content://com.gathin.tvhome.sync", ALERT_SWITCHES = "alert_switches";
    /** The answer's keys: taken (no card from the launcher), and why, for the launcher's alert log. */
    public static final String TAKEN = "taken", WHY = "why";
    /** The switches' question: the game and the competition asked about. */
    public static final String GAME = "game", SLUG = "slug";
    /** The football kinds the launcher's Notifications has a switch for (NotifyKinds), as the switches name them. */
    public static final String[] KINDS = {"goal", "red", "ft", "kickoff", "ht", "var", "drama", "prediction", "table", "goalrush",
            "milestone"};
    /** The pictures' longest side, in pixels. */
    public static final int PICTURE_MAX = 256, PAGES_MAX = 4;

    public String gameId, kind, chip = "", title = "", line = "", line2 = "", key, what = "", tag;
    public int colour, homeColour, awayColour, side, tagColour, tagMark;
    public boolean lineups, own;
    public Bitmap photo, homeCrest, awayCrest;
    public String[][] pages;
    public Bitmap[] photos;

    // ---- the rules (pure) -----------------------------------------------------------------------------------------------

    /** What Homefield TV does with an alert offered: not taken (its board is not up to take it), let go (its own board says it), shown. */
    public static final int NOT_UP = 0, SAID = 1, SHOWN = 2;

    /**
     * Pure: what Homefield TV does with an alert of kind for game alertGame: its board up to take it (up), the board's own
     * game (boardGame; null: none), and, for an alert with pages (the ratings), whether its full time has little of its
     * minute left (ftLate: then the card shows instead, as the launcher's).
     */
    public static int decide(boolean up, String boardGame, String alertGame, String kind, boolean paged, boolean ftLate) {
        if (!up) return NOT_UP;
        if (paged && ftLate) return NOT_UP;
        if (boardGame != null && boardGame.equals(alertGame) && boardSaysIt(kind)) return SAID;
        return SHOWN;
    }

    /**
     * Pure: whether Homefield TV's board says this kind itself for its own game (from its own reading, as the picture
     * reaches it): goals (a late one or a comeback too), red cards, full time, half time, the kick-off and its line-ups,
     * VAR and penalties.
     */
    public static boolean boardSaysIt(String kind) {
        if (kind == null) return false;
        switch (kind) {
            case "goal": case "drama": case "red": case "ft": case "ht": case "kickoff": case "var": case "lineups": return true;
            default: return false;
        }
    }

    /**
     * Pure: the kind of an alert by its chip as the boards write it, for the launcher's switches: GOAL, RED CARD, FULL
     * TIME, HALF TIME, KICK-OFF, SECOND HALF and LINE-UPS (kick-off and line-ups), VAR; null for one with no switch of its
     * own (substitutions, the minutes added, a yellow card), which only the main switches hold.
     */
    public static String kindOfChip(String chip) {
        String c = chip == null ? "" : chip.trim().toUpperCase(java.util.Locale.ROOT);
        switch (c) {
            case "GOAL": return "goal";
            case "RED CARD": return "red";
            case "FULL TIME": return "ft";
            case "HALF TIME": return "ht";
            case "KICK-OFF": case "SECOND HALF": case "LINE-UPS": return "kickoff";
            case "VAR": case "PEN": case "PENALTY": return "var";
            default: return null;
        }
    }

    /**
     * Pure: whether an alert of kind may show by the launcher's switches as last read (switches: kind to on, each as the
     * launcher's own check answers it for the game; null when no launcher answered), else by the family's own defaults
     * (every kind on but Goal Rush and the live table, as both TVs have them). One with no switch of its own (null: a
     * substitution, the minutes added) always may, as the launcher's board's own over TiviMate.
     */
    public static boolean allowed(Map<String, Boolean> switches, String kind) {
        if (kind == null) return true;
        if (switches != null) {
            Boolean on = switches.get(kind);
            if (on != null) return on;
        }
        return byDefault(kind);
    }

    /** Pure: the family's defaults for a kind with no answer from the launcher: on, but Goal Rush and the live table. */
    public static boolean byDefault(String kind) {
        return !"goalrush".equals(kind) && !"table".equals(kind);
    }

    /** Pure: a picture's size made at most most across its longer side, its shape kept: {width, height}. */
    public static int[] fitted(int w, int h, int most) {
        if (w <= 0 || h <= 0) return new int[]{0, 0};
        int longer = Math.max(w, h);
        if (longer <= most) return new int[]{w, h};
        float k = most / (float) longer;
        return new int[]{Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k))};
    }

    // ---- across the two apps ------------------------------------------------------------------------------------------

    /** As handed over: the words, the colours and the pictures (small PNGs). */
    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putString("game", gameId);
        b.putString("kind", kind);
        b.putString("chip", chip);
        b.putString("title", title);
        b.putString("line", line);
        b.putString("line2", line2);
        b.putString("key", key);
        b.putString("what", what);
        b.putString("tag", tag);
        b.putInt("colour", colour);
        b.putInt("home_colour", homeColour);
        b.putInt("away_colour", awayColour);
        b.putInt("side", side);
        b.putInt("tag_colour", tagColour);
        b.putInt("tag_mark", tagMark);
        b.putBoolean("lineups", lineups);
        b.putBoolean("own", own);
        b.putByteArray("photo", png(photo));
        b.putByteArray("home_crest", png(homeCrest));
        b.putByteArray("away_crest", png(awayCrest));
        if (pages != null) {
            int n = Math.min(PAGES_MAX, pages.length);
            b.putInt("pages", n);
            for (int i = 0; i < n; i++) {
                String[] p = pages[i] == null ? new String[0] : pages[i];
                b.putStringArray("page" + i, p);
                if (photos != null && i < photos.length) b.putByteArray("page_photo" + i, png(photos[i]));
            }
        }
        return b;
    }

    /** One handed over (null when there is none, or it has no words to say). */
    public static BoardAlert from(Bundle b) {
        if (b == null) return null;
        BoardAlert a = new BoardAlert();
        a.gameId = b.getString("game");
        a.kind = b.getString("kind");
        a.chip = nz(b.getString("chip"));
        a.title = nz(b.getString("title"));
        a.line = nz(b.getString("line"));
        a.line2 = nz(b.getString("line2"));
        a.key = b.getString("key");
        a.what = nz(b.getString("what"));
        a.tag = b.getString("tag");
        a.colour = b.getInt("colour");
        a.homeColour = b.getInt("home_colour");
        a.awayColour = b.getInt("away_colour");
        a.side = b.getInt("side");
        a.tagColour = b.getInt("tag_colour");
        a.tagMark = b.getInt("tag_mark");
        a.lineups = b.getBoolean("lineups");
        a.own = b.getBoolean("own");
        a.photo = bitmap(b.getByteArray("photo"));
        a.homeCrest = bitmap(b.getByteArray("home_crest"));
        a.awayCrest = bitmap(b.getByteArray("away_crest"));
        int n = Math.min(PAGES_MAX, Math.max(0, b.getInt("pages")));
        if (n > 0) {
            a.pages = new String[n][];
            a.photos = new Bitmap[n];
            for (int i = 0; i < n; i++) {
                String[] p = b.getStringArray("page" + i);
                a.pages[i] = p == null ? new String[0] : p;
                a.photos[i] = bitmap(b.getByteArray("page_photo" + i));
            }
        }
        if (a.chip.isEmpty() && a.title.isEmpty() && a.line.isEmpty()) return null;
        return a;
    }

    /** The answer to an offer. */
    public static Bundle answer(boolean taken, String why) {
        Bundle b = new Bundle();
        b.putBoolean(TAKEN, taken);
        b.putString(WHY, why == null ? "" : why);
        return b;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** A picture as a small PNG (PICTURE_MAX across at most); null for none. */
    static byte[] png(Bitmap b) {
        if (b == null || b.isRecycled() || b.getWidth() <= 0 || b.getHeight() <= 0) return null;
        try {
            int[] wh = fitted(b.getWidth(), b.getHeight(), PICTURE_MAX);
            Bitmap s = wh[0] == b.getWidth() && wh[1] == b.getHeight() ? b : Bitmap.createScaledBitmap(b, wh[0], wh[1], true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            s.compress(Bitmap.CompressFormat.PNG, 100, out);
            return out.toByteArray();
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    static Bitmap bitmap(byte[] png) {
        if (png == null || png.length == 0) return null;
        try {
            return BitmapFactory.decodeByteArray(png, 0, png.length);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }
}
