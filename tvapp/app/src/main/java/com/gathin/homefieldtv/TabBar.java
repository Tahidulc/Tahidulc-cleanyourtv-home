package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Exec;

/**
 * 07's shell, the app's main way around (in place of the rail): Homefield's amber mark and name at the top left, the
 * clock at the top right and, between them, the glass tab bar (the search glass, Home, Guide, Movies, TV Shows, Sports,
 * My List and the Settings cog, its amber dot while a newer Homefield TV waits) with the white pill under the tab of the
 * page on screen; on a screen reached from another, its "‹ back" line instead (where Back goes).
 *
 * <p>There is one, kept by TvActivity over the screens and under Settings and the menus (overlays). It follows the
 * screen on top: Screen.shell() says whether it is drawn, Screen.tab() which tab the screen is, Screen.backLine() its
 * back line. The remote comes up on to the bar when a page calls enter() (Up past its top, or Back at its root); then
 * Left and Right slide the pill along the tabs on Glide (07's springy lift, as the mock-ups' clip), the page coming up
 * under the bar once the remote rests on a tab (a page drawn in the shell: one still in its own look waits for OK and
 * takes the remote); Down or OK goes into that page (its fromTabs()); OK on the search glass
 * or the cog opens Search or Settings; Back goes to Home's tab, and from Home leaves the app; MENU goes into the page
 * with it. The app starts with the remote on the bar's Home tab.
 *
 * <pre>
 *   tv.tabs().enter()            a page sends the remote up to the bar (its own tab lit)
 *   tv.tabs().active()           whether the bar has the remote
 *   Screen.shell() / tab() / backLine() / fromTabs()   what a screen tells the bar, and the remote coming back down
 * </pre>
 */
final class TabBar extends View {
    static final int NONE = -1, SEARCH = 0, HOME = 1, GUIDE = 2, MOVIES = 3, SHOWS = 4, SPORTS = 5, MY_LIST = 6, SETTINGS = 7;
    static final int COUNT = 8;
    static final String[] NAMES = {"Search", "Home", "Guide", "Movies", "TV Shows", "Sports", "My List", "Settings"};
    /** How long the remote rests on a tab before its page comes up under the bar. */
    static final long REST_MS = 380;
    /** How long the shell takes to come and go, and a tab's words to turn dark or light. */
    static final long FADE_MS = 200, INK_MS = 300;

    private final TvActivity tv;
    /** The tab of the page on top (NONE: not a tab's page), where the remote is on the bar, whether it is. */
    private int page = HOME, focus = HOME;
    private boolean active;
    /** The shell drawn for the screen on top, its back line (null: the tabs), and how far it has faded in. */
    private boolean wanted;
    private String back;
    private float shown, shownFrom;
    private long shownAt;
    /** Set while the bar itself opens a page (so the page doesn't take the remote from it). */
    private boolean navigating;
    /** The tabs' places (screen px), measured with the screen. */
    private final float[] tabX = new float[COUNT], tabW = new float[COUNT], wordsW = new float[COUNT], inkW = new float[COUNT];
    /** How dark each tab's words are (1: on the pill), turning over INK_MS from where they were when the pill moved. */
    private final float[] ink = new float[COUNT], inkFrom = new float[COUNT];
    private long inkAt;
    private float barX, barW;
    /** The pill's middle and width, gliding on 07's lift (screen px), and how lifted it is (the remote on the bar). */
    private final Glide pillX = new Glide(Look07.LIFT_MS, 1f).springy(Look07.BOUNCE, Look07.BOUNCE_KICK),
            pillW = new Glide(Look07.LIFT_MS, 1f).springy(Look07.BOUNCE, Look07.BOUNCE_KICK),
            lift = new Glide(Look07.LIFT_MS, 100f).springy(Look07.BOUNCE, Look07.BOUNCE_KICK);
    private boolean placed;
    private final Runnable rest = this::rested;
    private String time = "";
    private long timeFor = -1;
    private float madeFor = -1f;

    private TextPaint tabP, tabInk, brandP, clockP;
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG), mark = new Paint(Paint.ANTI_ALIAS_FLAG),
            markInk = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    TabBar(TvActivity tv) {
        super(tv);
        this.tv = tv;
        dot.setColor(Look07.AMBER);
        mark.setColor(Look07.AMBER);
        markInk.setColor(Look07.AMBER_INK);
        paints();
        Shadows.prepare(Look07.SH_PILL, Look07.R_TAB);
        Shadows.prepare(Look07.SH_GLASS, Look07.R_BAR);
    }

    private void paints() {
        if (madeFor == Ui.s && tabP != null) return;
        madeFor = Ui.s;
        timeFor = -1;
        tabP = Type.paint(Type.TAB, Type.MEDIUM, Look07.L2);
        tabInk = Type.paint(Type.TAB, Type.SEMIBOLD, Look07.INK);
        brandP = Type.paint(Type.BRAND, Type.BOLD, Look07.L1);
        brandP.setLetterSpacing(-0.02f);
        clockP = Type.clock(Type.CLOCK, Type.SEMIBOLD, Look07.L1);
        measure();
    }

    /** The tabs laid out, centred across the screen (each as wide as its words in the darker weight, 26 px each side). */
    private void measure() {
        float x = 0f;
        for (int i = 0; i < COUNT; i++) {
            boolean icon = i == SEARCH || i == SETTINGS;
            wordsW[i] = icon ? 0f : tabP.measureText(NAMES[i]);
            inkW[i] = icon ? 0f : tabInk.measureText(NAMES[i]);
            tabW[i] = icon ? Ui.f(Look07.TAB_ICON_W) : Math.max(wordsW[i], inkW[i]) + Ui.f(Look07.TAB_PAD * 2);
            tabX[i] = x;
            x += tabW[i] + (i < COUNT - 1 ? Ui.f(Look07.TAB_GAP) : 0f);
        }
        barW = x + Ui.f(Look07.BAR_PAD * 2);
        barX = (Ui.f(1920f) - barW) / 2f;
        for (int i = 0; i < COUNT; i++) tabX[i] += barX + Ui.f(Look07.BAR_PAD);
        placed = false;
    }

    // ---- what the screens tell it ---------------------------------------------------------------------------------------

    /** A screen was opened (put on top): unless the bar itself opened it, the remote goes with it. */
    void pushed(Screen s) {
        if (!navigating && active) setActive(false);
        if (s != null && !s.overlay()) raise();
        follow(s);
    }

    /** The screen on top changed: the shell follows it (over an overlay, the screen under it). */
    void follow(Screen top) {
        Screen s = top;
        while (s != null && s.overlay() && tv.below(s) != null) s = tv.below(s);
        boolean want = s != null && s.shell();
        String line = s == null ? null : s.backLine();
        int tab = s == null ? NONE : s.tab();
        if (tab != NONE) page = tab;
        back = tab == NONE ? line : null;
        if (!active) focus = page;
        show(want || active);
        if (!active) slideTo(focus, false);
        invalidate();
    }

    /** Over the screens, under any overlay (Settings, the menus): TvActivity calls it after adding a page. */
    void raise() {
        bringToFront();
    }

    // ---- the remote -------------------------------------------------------------------------------------------------------

    boolean active() {
        return active;
    }

    /** The remote up on to the bar, its page's tab lit. */
    void enter() {
        if (active) return;
        focus = page >= 0 ? page : HOME;
        setActive(true);
        show(true);
        slideTo(focus, false);
        invalidate();
    }

    /** The remote back down into the page (it is told: fromTabs). */
    void leave() {
        if (!active) return;
        setActive(false);
        removeCallbacks(rest);
        Screen s = tv.top();
        follow(s);
        if (s != null) s.fromTabs();
    }

    private void setActive(boolean on) {
        active = on;
        if (Exec.inline) lift.jump(on ? 1f : 0f);
        else lift.to(on ? 1f : 0f);
        inkTurns();
        if (!on) removeCallbacks(rest);
    }

    /** The tabs' words start turning (dark on the pill, light off it) from where they are now. */
    private void inkTurns() {
        System.arraycopy(ink, 0, inkFrom, 0, COUNT);
        inkAt = SystemClock.uptimeMillis();
    }

    /** A key while the bar has the remote (true: used). */
    boolean key(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int n = focus + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                if (n >= 0 && n < COUNT) {
                    focus = n;
                    slideTo(n, true);
                    inkTurns();
                    removeCallbacks(rest);
                    if (pageOf(n)) postDelayed(rest, REST_MS);
                    invalidate();
                }
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_UP:
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                ok();
                return true;
            case KeyEvent.KEYCODE_MENU: {
                leave();
                Screen s = tv.top();
                return s != null && s.key(code, e);
            }
            default:
                return false;
        }
    }

    /** OK (or Down) on the bar: into the tab's page, opening it first; Search and Settings open. */
    void ok() {
        removeCallbacks(rest);
        int f = focus;
        if (f == SEARCH || f == SETTINGS) {
            setActive(false);
            go(f, null);
            return;
        }
        if (f != page) go(f, null);
        leave();
    }

    /** Back on the bar: Home's tab; on Home, the app goes to the background. */
    boolean back() {
        removeCallbacks(rest);
        if (page != HOME || focus != HOME) {
            focus = HOME;
            slideTo(HOME, true);
            inkTurns();
            if (page != HOME) go(HOME, null);
            invalidate();
            return true;
        }
        return false;
    }

    /**
     * The remote has rested on a tab: its page comes up under the bar when it is drawn in 07's shell (Screen.shell);
     * one still in its own look (its own rail or top bar) waits for OK or Down and then takes the remote, so the bar
     * never sits over it. Search and Settings wait for OK.
     */
    private void rested() {
        if (!active || focus == page || !pageOf(focus)) return;
        Screen s = focus == HOME ? null : pageFor(focus);
        if (focus == HOME || s != null && s.shell()) go(focus, s);
    }

    private static boolean pageOf(int tab) {
        return tab == HOME || tab == GUIDE || tab == MOVIES || tab == SHOWS || tab == SPORTS || tab == MY_LIST;
    }

    /** A tab's page, made but not opened (null for Home, Search and Settings). */
    private Screen pageFor(int tab) {
        switch (tab) {
            case GUIDE:
                return new GuideScreen(tv, null);
            case MOVIES:
                return new BrowseScreen(tv, false);
            case SHOWS:
                return new BrowseScreen(tv, true);
            case SPORTS:
                return new SportsScreen(tv);
            case MY_LIST:
                return new MyListScreen(tv);
            default:
                return null;
        }
    }

    /** A tab's screen (made already, or null to make it), the remote staying where it is. */
    private void go(int tab, Screen made) {
        navigating = true;
        try {
            if (tab == HOME) tv.home();
            else if (tab == SEARCH) tv.openSearch();
            else if (tab == SETTINGS) tv.openSettings();
            else tv.fromHome(made != null ? made : pageFor(tab));
        } finally {
            navigating = false;
        }
        if (tab != SEARCH && tab != SETTINGS) page = tab;
        follow(tv.top());
    }

    // ---- motion -----------------------------------------------------------------------------------------------------------

    private void slideTo(int tab, boolean glide) {
        if (tab < 0 || tab >= COUNT) return;
        float cx = tabX[tab] + tabW[tab] / 2f, w = tabW[tab];
        if (!glide || !placed || Exec.inline) {
            pillX.jump(cx);
            pillW.jump(w);
            placed = true;
        } else {
            pillX.to(cx);
            pillW.to(w);
        }
    }

    private void show(boolean want) {
        if (want == wanted) return;
        wanted = want;
        shownFrom = shown;
        shownAt = SystemClock.uptimeMillis();
        if (Exec.inline) shown = want ? 1f : 0f;
        invalidate();
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------------

    /**
     * Only the shell's own strip along the top (the bar, its shadow, the back line), so a frame of the pill sliding
     * redraws that strip and not the page under it (the TV redraws only what changed).
     */
    static final float HEIGHT = 200f;

    @Override protected void onMeasure(int ws, int hs) {
        super.onMeasure(ws, hs);
        setMeasuredDimension(getMeasuredWidth(), Math.min(getMeasuredHeight(), Ui.px(HEIGHT)));
    }

    @Override protected void onDraw(Canvas c) {
        paints();
        long t = SystemClock.uptimeMillis();
        boolean moving = false;
        if (!Exec.inline) {
            float k = Math.min(1f, (t - shownAt) / (float) FADE_MS);
            shown = shownFrom + ((wanted ? 1f : 0f) - shownFrom) * Fade.ease(k);
            moving = k < 1f;
        }
        if (shown <= 0.005f) {
            if (moving) postInvalidateOnAnimation();
            return;
        }
        int a = Math.round(255 * shown);
        moving |= pillX.step() | pillW.step() | lift.step();
        long minute = System.currentTimeMillis() / 60_000L;
        if (minute != timeFor) {
            timeFor = minute;
            time = Ui.cornerClock(System.currentTimeMillis());
            timeW = clockP.measureText(time);
        }
        boolean tabs = back == null || active;
        // The mark, the name, the clock and the bar's glass don't move as the pill slides: kept (Cached), drawn in one call,
        // faded with the shell as the node's own opacity (nothing recorded again as it fades).
        still.draw(c, getWidth(), getHeight(), null, Cached.key(minute, (tabs ? 1 : 2) + Math.round(Ui.s * 1000f) * 4L
                + (long) Math.round(barW) * 1_000_000L), paintStill, a / 255f, 0f);
        if (tabs) {
            moving |= drawTabs(c, a, t);
        } else {
            moving |= drawBack(c, a);
        }
        if (moving) postInvalidateOnAnimation();
    }

    private final Cached still = new Cached(false);
    private final Cached.Painter paintStill = this::paintStill;
    private float timeW;

    /** The shell's parts that stay still: Homefield's mark and name, the clock, and (the tabs) the bar's glass. */
    private void paintStill(Canvas c) {
        int a = still.alpha();
        float ms = Ui.f(Look07.MARK), mx = Ui.f(Look07.BRAND_X), my = Ui.f(Look07.BRAND_Y);
        mark.setAlpha(a);
        markInk.setAlpha(a);
        Dark.mark(c, mx, my, ms, mark, markInk);
        brandP.setAlpha(a);
        c.drawText("Homefield", mx + ms + Ui.f(Look07.BRAND_GAP), Type.centred(brandP, my + ms / 2f), brandP);
        if (!time.isEmpty()) {
            clockP.setAlpha(a);
            c.drawText(time, Ui.f(1920f - Look07.CLOCK_RIGHT) - timeW, Ui.f(Look07.CLOCK_TOP + 31f), clockP);
        }
        if (back == null || active) {
            float top = Ui.f(Look07.BAR_TOP), h = Ui.f(Look07.BAR_H);
            r.set(barX, top, barX + barW, top + h);
            Frost.glass(c, r, h / 2f, a / 255f);
        }
    }

    private boolean drawBack(Canvas c, int a) {
        // Faded with the shell only while it fades (an offscreen pass otherwise wasted on every frame).
        if (a < 255) c.saveLayerAlpha(0, 0, getWidth(), Ui.f(Look07.TOP), a);
        Pills.back(c, Ui.f(Look07.BACK_X), Ui.f(Look07.BACK_Y + 12f), back);
        if (a < 255) c.restore();
        return false;
    }

    private boolean drawTabs(Canvas c, int a, long t) {
        float top = Ui.f(Look07.BAR_TOP), h = Ui.f(Look07.BAR_H);
        // The pill: under the page's tab, or (the remote on the bar) under the remote's tab, lifted a little with its
        // shadow, sliding between tabs.
        float up = Math.max(0f, Math.min(1.1f, lift.value()));
        float th = Ui.f(Look07.TAB_H), cy = top + h / 2f;
        float grow = 1f + (Look07.PILL_GROW - 1f) * up, pw = pillW.value() * grow, ph = th * grow;
        r.set(pillX.value() - pw / 2f, cy - ph / 2f, pillX.value() + pw / 2f, cy + ph / 2f);
        if (up > 0.01f) Shadows.draw(c, r, ph / 2f, Look07.SH_PILL, Math.min(1f, up) * shown);
        pill.setColor(Theme.mix(Look07.FOCUS_FILL, Look07.PILL_FILL, Math.min(1f, up)));
        pill.setAlpha(Math.round(((Look07.FOCUS_FILL >>> 24) + ((Look07.PILL_FILL >>> 24) - (Look07.FOCUS_FILL >>> 24)) * Math.min(1f, up)) * shown));
        c.drawRoundRect(r, ph / 2f, ph / 2f, pill);
        // The tabs' words and icons: dark on the pill, light on the glass, turning as the pill comes and goes.
        boolean moving = false;
        float k = Exec.inline ? 1f : Math.min(1f, (t - inkAt) / (float) INK_MS), e = Fade.ease(k);
        for (int i = 0; i < COUNT; i++) {
            float want = i == focus ? 1f : 0f;
            ink[i] = k >= 1f ? want : inkFrom[i] + (want - inkFrom[i]) * e;
            if (ink[i] != want) moving = true;
            int colour = Theme.mix(Look07.L2 | 0xFF000000, Look07.INK, ink[i]);
            int alpha = Math.round((0xBD + (0xFF - 0xBD) * ink[i]) * shown);
            float cx = tabX[i] + tabW[i] / 2f;
            if (i == SEARCH || i == SETTINGS) {
                Icons07.draw(c, i == SEARCH ? Icons07.SEARCH : Icons07.COG, cx, cy, Ui.f(Look07.TAB_ICON), Palette.alpha(colour, alpha));
                if (i == SETTINGS && Updates.waiting(getContext())) {
                    dot.setAlpha(a);
                    c.drawCircle(tabX[i] + tabW[i] - Ui.f(Look07.DOT_IN) - Ui.f(Look07.DOT) / 2f,
                            cy - th / 2f + Ui.f(Look07.DOT_IN) + Ui.f(Look07.DOT) / 2f, Ui.f(Look07.DOT) / 2f, dot);
                }
            } else {
                boolean dark = ink[i] > 0.5f;
                TextPaint p = dark ? tabInk : tabP;
                p.setColor(Palette.alpha(colour, alpha));
                c.drawText(NAMES[i], cx - (dark ? inkW[i] : wordsW[i]) / 2f, Type.centred(p, cy), p);
            }
        }
        return moving;
    }

    // ---- what the tests and previews look at --------------------------------------------------------------------------------

    int page() {
        return page;
    }

    int focus() {
        return focus;
    }

    boolean drawn() {
        return wanted;
    }

    String backWords() {
        return back;
    }
}
