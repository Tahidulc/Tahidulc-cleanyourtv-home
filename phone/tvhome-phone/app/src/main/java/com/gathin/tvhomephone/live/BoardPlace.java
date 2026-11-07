package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Where the family put the scoreboard and Homefield's mark over the channel's logo on one channel, and how big, by
 * hand (the player's "Move and resize"): each as fractions of the picture, so the same place holds upright and on
 * its side, whatever the picture's zoom. The scoreboard keeps its own width for the height chosen; the mark its width
 * and height. Kept on this phone, a channel at a time; Reset gives the channel back to the automatic places.
 */
final class BoardPlace {
    /** The scoreboard: its left, top and height; bx below 0 while it isn't placed. */
    float bx = -1, by, bh;
    /** The mark over the logo: its left, top, width and height; lx below 0 while it isn't placed. */
    float lx = -1, ly, lw, lh;

    /** The smallest and largest a scoreboard's height may be, as a share of the picture's. */
    static final float MIN_H = 0.035f, MAX_H = 0.3f;
    /** The smallest and largest a mark's width may be. */
    static final float MIN_W = 0.04f, MAX_W = 0.45f;

    boolean board() {
        return bx >= 0;
    }

    boolean logo() {
        return lx >= 0;
    }

    /** The scoreboard moved to (x, y), kept on the picture with the width it has (w, a share of the picture). Pure. */
    void moveBoard(float x, float y, float w) {
        bx = clamp(x, 0, Math.max(0, 1 - w));
        by = clamp(y, 0, Math.max(0, 1 - bh));
    }

    /** The mark moved to (x, y), kept on the picture. Pure. */
    void moveLogo(float x, float y) {
        lx = clamp(x, 0, Math.max(0, 1 - lw));
        ly = clamp(y, 0, Math.max(0, 1 - lh));
    }

    /** The scoreboard's height times k, between MIN_H and MAX_H, its top kept on the picture. Pure. */
    void sizeBoard(float k) {
        bh = clamp(bh * k, MIN_H, MAX_H);
        by = clamp(by, 0, Math.max(0, 1 - bh));
    }

    /** The mark's size times k about its middle, its shape kept, kept on the picture. Pure. */
    void sizeLogo(float k) {
        float w = clamp(lw * k, MIN_W, MAX_W);
        float f = lw > 0 ? w / lw : 1;
        float h = Math.min(0.5f, lh * f);
        float cx = lx + lw / 2, cy = ly + lh / 2;
        lw = w;
        lh = h;
        moveLogo(cx - w / 2, cy - h / 2);
    }

    static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    String json() {
        try {
            return new JSONObject().put("bx", bx).put("by", by).put("bh", bh).put("lx", lx).put("ly", ly).put("lw", lw)
                    .put("lh", lh).toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    static BoardPlace from(String s) {
        BoardPlace p = new BoardPlace();
        if (s == null || s.isEmpty()) return p;
        try {
            JSONObject o = new JSONObject(s);
            p.bx = (float) o.optDouble("bx", -1);
            p.by = (float) o.optDouble("by", 0);
            p.bh = (float) o.optDouble("bh", 0);
            p.lx = (float) o.optDouble("lx", -1);
            p.ly = (float) o.optDouble("ly", 0);
            p.lw = (float) o.optDouble("lw", 0);
            p.lh = (float) o.optDouble("lh", 0);
        } catch (JSONException ignored) {
            // nothing placed
        }
        if (p.bh <= 0) p.bx = -1;
        if (p.lw <= 0 || p.lh <= 0) p.lx = -1;
        return p;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("board_places", Context.MODE_PRIVATE);
    }

    /** The channel's own places (none placed when it has none). */
    static BoardPlace get(Context c, String channel) {
        return from(channel == null ? null : prefs(c).getString(channel, null));
    }

    static void put(Context c, String channel, BoardPlace p) {
        if (channel == null) return;
        if (!p.board() && !p.logo()) prefs(c).edit().remove(channel).apply();
        else prefs(c).edit().putString(channel, p.json()).apply();
    }
}
