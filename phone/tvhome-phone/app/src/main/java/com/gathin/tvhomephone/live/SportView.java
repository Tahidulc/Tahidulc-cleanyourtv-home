package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import com.gathin.broadcast.SportBoard;
import com.gathin.broadcast.SportBug;

/**
 * The tennis, boxing or UFC board drawn in the football board's look (SportBug, shared with the TV), in the football
 * board's lettering: it opens out from its left edge as the football board does, its tab rolls down out of its foot, and
 * a moment (a set, the match, a round, the winner) sweeps across it as a band.
 */
final class SportView extends View {
    static final long OPEN_MS = 420L, TAB_MS = 320L, BAND_MS = 3_500L;

    private final SportBug bug;
    private SportBug.Skin skin = SportBug.skin("CLASSIC", null);
    private SportBoard board;
    private Bitmap logo;
    /** Its rows' height, pixels. */
    private float u = 30f;
    private boolean tab;
    private long openAt = -1, tabAt = -1, bandAt = -1;
    private String bandWords;
    private int bandColour;
    private int wantW, wantH;
    /** At least this wide (over the channel's own board: as wide as it), pixels. */
    private int minW;

    SportView(Context c) {
        super(c);
        // The football board's own lettering (OverlayBug's), so the two read alike.
        bug = new SportBug(c, Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        setWillNotDraw(false);
    }

    /** What it shows: the look, the board, the logo (UFC's; null: the board draws a ball or a glove), rows u high, the tab. */
    void show(SportBug.Skin s, SportBoard b, Bitmap lg, float rows, boolean withTab) {
        skin = s == null ? SportBug.skin("CLASSIC", null) : s;
        board = b;
        logo = lg;
        u = Math.max(8f, rows);
        if (withTab && !tab) tabAt = SystemClock.uptimeMillis();
        tab = withTab;
        int[] z = size();
        if (z[0] != wantW || z[1] != wantH) {
            wantW = z[0];
            wantH = z[1];
            requestLayout();
        }
        invalidate();
    }

    /** Its size for what it shows: {width, height}, the tab's room under it included; at least minWidth wide. */
    int[] size() {
        if (board == null) return new int[]{0, 0};
        float[] z = bug.size(skin, board, u);
        return new int[]{Math.max(minW, (int) Math.ceil(z[0])), (int) Math.ceil(z[1])};
    }

    /** At least this wide from now (0: its own width): it draws to fill it. */
    void minWidth(int px) {
        int w = Math.max(0, px);
        if (w == minW) return;
        minW = w;
        int[] z = size();
        if (z[0] != wantW || z[1] != wantH) {
            wantW = z[0];
            wantH = z[1];
            requestLayout();
        }
        invalidate();
    }

    /** Opening out from its left edge, now. */
    void open() {
        openAt = SystemClock.uptimeMillis();
        invalidate();
    }

    /** A moment's band across it, now: its words and the side's colour (0: the look's own). */
    void band(String words, int colour) {
        bandWords = words;
        bandColour = colour;
        bandAt = SystemClock.uptimeMillis();
        invalidate();
    }

    boolean tab() {
        return tab;
    }

    SportBoard board() {
        return board;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int[] z = size();
        setMeasuredDimension(Math.max(1, z[0]), Math.max(1, z[1]));
    }

    @Override protected void onDraw(Canvas cv) {
        if (board == null) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        long now = SystemClock.uptimeMillis();
        boolean moving = false;
        float bodyH = Math.max(1f, h - SportBug.tabHeight(u) - u * 0.08f);
        // Opening: the body wipes out from its left edge.
        float f = openAt < 0 ? 1f : Math.min(1f, (now - openAt) / (float) OPEN_MS);
        if (f < 1f) moving = true;
        float shown = w * (f * f * (3f - 2f * f));
        cv.save();
        cv.clipRect(0, 0, shown, bodyH + 1);
        bug.draw(cv, skin, board, logo, w, h, u, false);
        cv.restore();
        // The tab rolls down out of the board's foot once the board is open.
        if (tab && f >= 1f) {
            float t = tabAt < 0 ? 1f : Math.min(1f, (now - Math.max(tabAt, openAt < 0 ? 0 : openAt + OPEN_MS)) / (float) TAB_MS);
            t = Math.max(0f, t);
            if (t < 1f) moving = true;
            cv.save();
            cv.clipRect(0, bodyH, w, bodyH + (h - bodyH) * t);
            bug.draw(cv, skin, board, logo, w, h, u, true);
            cv.restore();
        }
        if (bandAt >= 0 && bandWords != null) {
            float t = (now - bandAt) / (float) BAND_MS;
            if (t >= 1f) {
                bandAt = -1;
            } else {
                bug.band(cv, skin, board, bandWords, bandColour, w, u, Math.max(0.001f, t));
                moving = true;
            }
        }
        if (moving) postInvalidateOnAnimation();
    }
}
