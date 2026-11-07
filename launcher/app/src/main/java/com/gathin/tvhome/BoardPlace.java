package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * Where the scoreboard over the channel's own (BoardCover) and the cover over the channel's logo sit, and how big:
 * each as fractions of the screen, so a place holds whatever the TV's resolution. The scoreboard by its left, top and
 * height (its width follows from its height, as its letters and crests need it); the cover by its left, top, width
 * and height. Kept a channel at a time in "board_places", set by Move and resize: a channel with no places of its own
 * takes the last ones set on any channel (one broadcaster's channels share a layout), else the standard ones, where
 * most broadcasters put theirs: the scoreboard top left, the channel's logo top right. The scoreboard's width can be
 * kept apart from its height (Move and resize's Height): it then fills that box. The sums are pure, for the tests.
 */
final class BoardPlace {
    /** The scoreboard: its left, top and height, and its width when kept apart from its height (0: as its words need). */
    float bx, by, bh, bw;
    /** The cover over the logo: its left, top, width and height. */
    float lx, ly, lw, lh;

    /** The smallest and largest scoreboard, by its height as a share of the screen's, and by its width kept. */
    static final float MIN_H = 0.03f, MAX_H = 0.12f, MIN_BW = 0.06f, MAX_BW = 0.6f;
    /** The smallest and largest cover over the logo, by its width and by its height. */
    static final float MIN_W = 0.03f, MAX_W = 0.3f, MIN_LH = 0.02f, MAX_LH = 0.2f;

    /** The standard places: the scoreboard top left and the cover top right, where most broadcasters put theirs. */
    static BoardPlace standard() {
        BoardPlace p = new BoardPlace();
        p.bx = 0.045f;
        p.by = 0.045f;
        p.bh = 0.045f;
        p.lx = 0.855f;
        p.ly = 0.045f;
        p.lw = 0.1f;
        p.lh = 0.065f;
        return p;
    }

    BoardPlace copy() {
        BoardPlace p = new BoardPlace();
        p.bx = bx;
        p.by = by;
        p.bh = bh;
        p.bw = bw;
        p.lx = lx;
        p.ly = ly;
        p.lw = lw;
        p.lh = lh;
        return p;
    }

    /**
     * The scoreboard moved by (dx, dy), kept on the screen; aspect is its width for its height as shares of the screen's
     * (its width in pixels over its height in pixels, times the screen's height over its width). Pure.
     */
    void moveBoard(float dx, float dy, float aspect) {
        bx = clamp(bx + dx, 0, Math.max(0, 1 - bh * aspect));
        by = clamp(by + dy, 0, Math.max(0, 1 - bh));
    }

    /** The scoreboard's height (and so its width) times k, its top left corner kept, kept on the screen. Pure. */
    void sizeBoard(float k, float aspect) {
        float was = bh;
        bh = clamp(bh * k, MIN_H, MAX_H);
        if (bw > 0) bw = clamp(bw * bh / was, MIN_BW, MAX_BW);
        moveBoard(0, 0, bw > 0 ? bw / bh : aspect);
    }

    /**
     * Move and resize's Height: the scoreboard's height times k, its width kept as it is (width, a share of the
     * screen's, when it had none of its own yet), so it is drawn taller or flatter to fill that box. Pure.
     */
    void heightBoard(float k, float width) {
        if (bw <= 0) bw = clamp(width > 0 ? width : bh * 9f * 1080f / 1920f, MIN_BW, MAX_BW);
        bh = clamp(bh * k, MIN_H, MAX_H);
        moveBoard(0, 0, bw / bh);
    }

    /** The cover moved by (dx, dy), kept on the screen. Pure. */
    void moveLogo(float dx, float dy) {
        lx = clamp(lx + dx, 0, Math.max(0, 1 - lw));
        ly = clamp(ly + dy, 0, Math.max(0, 1 - lh));
    }

    /** The cover's width times kw and its height times kh, about its middle, kept on the screen. Pure. */
    void sizeLogo(float kw, float kh) {
        float w = clamp(lw * kw, MIN_W, MAX_W), h = clamp(lh * kh, MIN_LH, MAX_LH);
        float cx = lx + lw / 2, cy = ly + lh / 2;
        lw = w;
        lh = h;
        lx = cx - w / 2;
        ly = cy - h / 2;
        moveLogo(0, 0);
    }

    static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** "bx,by,bh,lx,ly,lw,lh,bw". Pure. */
    String encode() {
        return String.format(Locale.ROOT, "%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f", bx, by, bh, lx, ly, lw, lh, bw);
    }

    /** The places written by encode (seven numbers before the board's width was kept, eight since), each between 0 and 1; else null. Pure. */
    static BoardPlace decode(String s) {
        if (s == null) return null;
        String[] f = s.split(",");
        if (f.length != 7 && f.length != 8) return null;
        float[] v = new float[f.length];
        try {
            for (int i = 0; i < f.length; i++) v[i] = Float.parseFloat(f[i].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        for (float x : v) if (Float.isNaN(x) || x < 0 || x > 1) return null;
        BoardPlace p = new BoardPlace();
        p.bx = v[0];
        p.by = v[1];
        p.bh = clamp(v[2], MIN_H, MAX_H);
        p.lx = v[3];
        p.ly = v[4];
        p.lw = clamp(v[5], MIN_W, MAX_W);
        p.lh = clamp(v[6], MIN_LH, MAX_LH);
        p.bw = v.length > 7 && v[7] > 0 ? clamp(v[7], MIN_BW, MAX_BW) : 0;
        return p;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("board_places", Context.MODE_PRIVATE);
    }

    /** Pure: a channel's key, its name without its picture's tag ("c.skysportspremierleague"); "c." when not known. */
    static String key(String channel) {
        return "c." + InStep.base(channel);
    }

    /** The channel's own places, else the last set on any channel, else the standard ones. */
    static BoardPlace get(Context c, String channel) {
        SharedPreferences p = prefs(c);
        BoardPlace own = decode(p.getString(key(channel), null));
        if (own != null) return own;
        BoardPlace last = decode(p.getString("last", null));
        return last != null ? last : standard();
    }

    /** Kept for the channel, and for every channel with no places of its own. */
    static void put(Context c, String channel, BoardPlace b) {
        String s = b.encode();
        prefs(c).edit().putString(key(channel), s).putString("last", s).apply();
    }

    /** The channel (and every channel with no places of its own) back to the standard places. */
    static void reset(Context c, String channel) {
        put(c, channel, standard());
    }
}
