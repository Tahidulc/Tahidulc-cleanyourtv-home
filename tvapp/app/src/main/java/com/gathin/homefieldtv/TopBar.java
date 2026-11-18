package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;

import java.util.List;

/**
 * The bar across the top of Movies, TV shows and a title's page: Home, TV shows, Movies and Recently added on the left
 * (the section on screen in bold white), Homefield's mark and name in the middle, and Search, My list and the clock on
 * the right, over a soft dark edge that the page's own colour fills in as it is scrolled under the bar. The remote comes
 * up on to it from the page: Left and Right along its items, the one it is on in a white pill; OK goes there (go).
 */
final class TopBar extends View {
    static final int HOME = 0, SHOWS = 1, MOVIES = 2, RECENT = 3, SEARCH = 4, MY_LIST = 5, COUNT = 6;
    static final String[] WORDS = {"Home", "TV shows", "Movies", "Recently added", "Search", "My list"};
    /** The bar's height, its words' baseline, and the page's left edge (design pixels). */
    static final float H = 130f, BASE = 74f, LEFT = 130f;

    /** The section on screen (SHOWS or MOVIES; -1 for none), and the item the remote is on (-1: the page has it). */
    final int current;
    int focus = -1;
    private int backing;
    private float backed;

    private final TextPaint word, wordOn, ink, brand, clock;
    private final Paint scrim = new Paint(Paint.DITHER_FLAG), band = new Paint(), pill = Ui.fill(Cine.WHITE);
    private final Paint amber = Ui.fill(Cine.AMBER), amberInk = Ui.fill(Cine.AMBER_INK);
    private final float[] x = new float[COUNT], w = new float[COUNT];
    private final RectF r = new RectF();
    private float brandX, brandW, clockRight;
    private String time = "";
    private long timeFor = -1;

    TopBar(Context c, int current) {
        super(c);
        this.current = current;
        word = Cine.text(25f, Cine.inter(), Cine.SOFT);
        wordOn = Cine.text(25f, Cine.interSemi(), Cine.TEXT);
        ink = Cine.text(25f, Cine.interSemi(), Cine.INK);
        brand = Cine.text(33f, Cine.outfit(800), Cine.AMBER);
        brand.setLetterSpacing(0.09f);
        clock = Cine.text(25f, Cine.inter(), Cine.SOFT);
    }

    /** The page's colour filling the bar (0: only the dark edge; 1: solid, the page scrolled under it). */
    void setBacking(int colour, float amount) {
        if (colour == backing && Math.abs(amount - backed) < 0.004f) return;
        backing = colour;
        backed = amount;
        invalidate();
    }

    @Override protected void onSizeChanged(int wd, int ht, int ow, int oh) {
        scrim.setShader(new LinearGradient(0, 0, 0, Ui.f(H + 90f), new int[]{0xA6000000, 0x40000000, 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        float at = Ui.f(LEFT);
        for (int i = HOME; i <= RECENT; i++) {
            x[i] = at;
            w[i] = Math.max(word.measureText(WORDS[i]), wordOn.measureText(WORDS[i]));
            at += w[i] + Ui.f(46f);
        }
        clockRight = wd - Ui.f(LEFT);
        // From the right: the clock, My list, then Search's glass.
        float myW = word.measureText(WORDS[MY_LIST]);
        x[MY_LIST] = clockRight - Ui.f(150f) - myW;
        w[MY_LIST] = myW;
        w[SEARCH] = Ui.f(40f);
        x[SEARCH] = x[MY_LIST] - Ui.f(52f) - w[SEARCH];
        brandW = Ui.f(46f) + Ui.f(16f) + brand.measureText("HOMEFIELD");
        brandX = (wd - brandW) / 2f;
    }

    /** Left and Right along the items (true when it moved, or at an end). */
    boolean key(int code) {
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (focus > 0) focus--;
            invalidate();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (focus < COUNT - 1) focus++;
            invalidate();
            return true;
        }
        return false;
    }

    /** The remote on to the bar (at the section on screen), or off it (-1). */
    void enter(boolean on) {
        focus = on ? (current >= 0 ? current : HOME) : -1;
        invalidate();
    }

    /** OK on an item: where it goes (series: whether Recently added means TV shows). */
    static void go(TvActivity tv, int item, int from, boolean series) {
        switch (item) {
            case HOME:
                tv.home();
                break;
            case SHOWS:
                if (from != SHOWS || !(tv.top() instanceof BrowseScreen)) tv.fromHome(new BrowseScreen(tv, true));
                break;
            case MOVIES:
                if (from != MOVIES || !(tv.top() instanceof BrowseScreen)) tv.fromHome(new BrowseScreen(tv, false));
                break;
            case RECENT: {
                List<Catalog.Title> recent = Catalog.get(tv).recent(series, 120);
                tv.push(new ListScreen(tv, series, "Recently added", recent));
                break;
            }
            case SEARCH:
                tv.openSearch();
                break;
            default:
                tv.fromHome(new MyListScreen(tv));
                break;
        }
    }

    @Override protected void onDraw(Canvas c) {
        int wd = getWidth();
        c.drawRect(0, 0, wd, Ui.f(H + 90f), scrim);
        if (backed > 0.01f) {
            band.setColor(backing);
            band.setAlpha(Math.round(255 * Math.min(1f, backed)));
            c.drawRect(0, 0, wd, Ui.f(H), band);
        }
        float base = Ui.f(BASE), cy = base - Ui.f(9f);
        for (int i = HOME; i <= RECENT; i++) {
            boolean on = i == focus;
            if (on) {
                r.set(x[i] - Ui.f(20f), cy - Ui.f(27f), x[i] + w[i] + Ui.f(20f), cy + Ui.f(27f));
                c.drawRoundRect(r, Ui.f(27f), Ui.f(27f), pill);
            }
            c.drawText(WORDS[i], x[i], base, on ? ink : i == current ? wordOn : word);
        }
        // Homefield's mark and name.
        float s = Ui.f(46f);
        Dark.mark(c, brandX, cy - s / 2f, s, amber, amberInk);
        c.drawText("HOMEFIELD", brandX + s + Ui.f(16f), Cine.mid(brand, cy), brand);
        // Search's glass, My list, the clock.
        boolean on = focus == SEARCH;
        float sx = x[SEARCH] + w[SEARCH] / 2f;
        if (on) c.drawCircle(sx, cy, Ui.f(32f), pill);
        RoundButton.glyph(c, Dark.SEARCH, sx, cy, Ui.f(36f), on ? Cine.INK : Cine.SOFT);
        on = focus == MY_LIST;
        if (on) {
            r.set(x[MY_LIST] - Ui.f(20f), cy - Ui.f(27f), x[MY_LIST] + w[MY_LIST] + Ui.f(20f), cy + Ui.f(27f));
            c.drawRoundRect(r, Ui.f(27f), Ui.f(27f), pill);
        }
        c.drawText(WORDS[MY_LIST], x[MY_LIST], base, on ? ink : word);
        long minute = System.currentTimeMillis() / 60_000L;
        if (minute != timeFor) {
            timeFor = minute;
            time = Ui.cornerClock(System.currentTimeMillis());
        }
        if (!time.isEmpty()) c.drawText(time, clockRight - clock.measureText(time), base, clock);
    }
}
