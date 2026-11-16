package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

/**
 * The slim rail down the left of the dark screens (Films, TV shows, a title's page, the TV guide): Homefield's mark
 * and the time at the top, then Home, the TV guide, Films, TV shows and Search as icons, the page it is on in amber,
 * Settings at the foot. When the remote comes on to it, the icon it is on sits in a white circle with its name beside
 * it; OK goes there.
 */
final class Rail extends View {
    static final float W = 112f;
    static final int HOME = 0, GUIDE = 1, FILMS = 2, SHOWS = 3, SPORTS = 4, SEARCH = 5, SETTINGS = 6;
    /** The icons in order; Sports' (-1) is a ball drawn by SportsIcon. */
    private static final int[] ICONS = {Dark.HOME, Dark.GUIDE, Dark.FILMS, Dark.SHOWS, -1, Dark.SEARCH, Dark.SETTINGS};
    private static final String[] NAMES = {"Home", "TV guide", "Films", "TV shows", "Sports", "Search", "Settings"};

    /** The page it is on (one of HOME…SETTINGS, or -1). */
    final int page;
    int focus;
    private boolean active;

    private final Paint back = Dark.fill(Dark.RAIL), edge = Dark.fill(Dark.LINE), amber = Dark.fill(Dark.ACCENT);
    private final Paint ink = Dark.fill(Dark.ACCENT_INK), on = Dark.fill(0xFFFFFFFF), tag = Dark.fill(0xF2FFFFFF);
    private final Paint stroke = Dark.line(Dark.MUTED, 2.6f), strokeDark = Dark.line(0xFF111114, 2.6f);
    private final Paint solid = Dark.fill(Dark.MUTED), solidDark = Dark.fill(0xFF111114);
    private final Paint strokeAmber = Dark.line(Dark.ACCENT, 2.6f), solidAmber = Dark.fill(Dark.ACCENT);
    private final TextPaint time = Dark.text(22, 500, Dark.SOFT), label = Dark.text(26, 600, 0xFF111114);

    Rail(Context c, int page) {
        super(c);
        this.page = page;
        focus = Math.max(0, page);
    }

    void setActive(boolean a) {
        active = a;
        if (a) focus = Math.max(0, page);
        invalidate();
    }

    boolean active() {
        return active;
    }

    /** Up and Down along the icons; true when used. */
    boolean key(int code) {
        if (code == KeyEvent.KEYCODE_DPAD_UP && focus > 0) focus--;
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN && focus < ICONS.length - 1) focus++;
        else return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
        invalidate();
        return true;
    }

    /** An icon: Dark's, or Sports' ball. */
    private static void icon(Canvas c, int i, float cx, float cy, float s, Paint stroke, Paint solid) {
        if (i == SPORTS) SportsIcon.draw(c, cx, cy, s, stroke, solid);
        else Dark.icon(c, ICONS[i], cx, cy, s, stroke, solid);
    }

    /** Where an icon's middle is (design pixels). */
    private static float y(int i) {
        return i == SETTINGS ? 1010f : 400f + i * 92f;
    }

    /** OK on the rail: to the page the remote is on. */
    static void go(TvActivity tv, int which, int from) {
        if (which == from && which != SEARCH) return;
        switch (which) {
            case HOME:
                tv.home();
                break;
            case GUIDE:
                tv.fromHome(new GuideScreen(tv, null));
                break;
            case FILMS:
                tv.fromHome(new BrowseScreen(tv, false));
                break;
            case SHOWS:
                tv.fromHome(new BrowseScreen(tv, true));
                break;
            case SPORTS:
                tv.fromHome(new SportsScreen(tv));
                break;
            case SEARCH:
                tv.openSearch();
                break;
            default:
                tv.openSettings();
                break;
        }
    }

    @Override protected void onDraw(Canvas c) {
        int h = getHeight();
        c.drawRect(0, 0, Ui.f(W), h, back);
        c.drawRect(Ui.f(W) - Math.max(1f, Ui.f(1.5f)), 0, Ui.f(W), h, edge);
        float s = Ui.f(52), x = (Ui.f(W) - s) / 2f;
        Dark.mark(c, x, Ui.f(46), s, amber, ink);
        String t = Ui.clock(System.currentTimeMillis());
        c.drawText(t, (Ui.f(W) - time.measureText(t)) / 2f, Ui.f(136), time);
        float cx = Ui.f(W / 2f);
        for (int i = 0; i < ICONS.length; i++) {
            float cy = Ui.f(y(i));
            boolean here = active && i == focus;
            if (here) {
                // The icon in a white circle, its name in a white tag beside it (over the page).
                float tw = label.measureText(NAMES[i]);
                c.drawRoundRect(cx - Ui.f(30), cy - Ui.f(30), cx + Ui.f(46) + tw + Ui.f(24), cy + Ui.f(30), Ui.f(30), Ui.f(30), tag);
                c.drawCircle(cx, cy, Ui.f(30), on);
                icon(c, i, cx, cy, Ui.f(30), strokeDark, solidDark);
                c.drawText(NAMES[i], cx + Ui.f(42), cy + Ui.f(9), label);
            } else if (i == page) {
                icon(c, i, cx, cy, Ui.f(30), strokeAmber, solidAmber);
            } else {
                icon(c, i, cx, cy, Ui.f(30), stroke, solid);
            }
        }
    }
}
