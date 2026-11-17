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

import com.gathin.homefieldtv.engine.Exec;

/**
 * The main menu down the left of every screen. Closed, it is the rail: 112 px of the panel's colour with Homefield's
 * mark at the top, then an icon (40 px, 36 px in) for each section at 96 px steps (Search, Home, the TV guide, Movies,
 * TV shows, Sports, Recordings, My list) and Settings at the foot. The section on screen is white, the others grey
 * (Palette.ICON_OFF).
 *
 * When the remote comes on to it (setActive(true)) it opens into the drawer: 440 px, Homefield's name at the top, each
 * icon's name beside it (ITEM size, 120 px in), the remote's item in the focus pill (32 px in from both sides, 80 high,
 * radius 12), and the section on screen in the selected pill while the remote is on another. It opens over the screen
 * in DRAWER_MS, its names fading in, with a soft shadow down its edge, and closes the same way, gliding on the display's
 * frame clock (Glide: opened and closed again quickly, it turns smoothly, never jumping back). Up and Down jump
 * between the items with no wrap-around; OK opens one (go); Right and Back close it (the screen's call). A newer
 * Homefield TV waiting puts the accent dot on Settings.
 *
 * The screens use it as before: new Rail(context, page), setActive, active(), key(code), focus, page, keep, go().
 */
final class Rail extends View {
    static final float W = 112f, DRAWER = 440f;
    static final long DRAWER_MS = 150;
    static final int HOME = 0, GUIDE = 1, FILMS = 2, SHOWS = 3, SPORTS = 4, SEARCH = 5, SETTINGS = 6, RECORDINGS = 7, MY_LIST = 8;
    /** The items from the top; Settings is at the foot. */
    static final int[] ORDER = {SEARCH, HOME, GUIDE, FILMS, SHOWS, SPORTS, RECORDINGS, MY_LIST, SETTINGS};
    static final String[] NAMES = {"Home", "TV guide", "Movies", "TV shows", "Sports", "Search", "Settings", "Recordings", "My list"};
    private static final int[] ICONS = {Dark.HOME, Dark.TV, Dark.CLAPPER, Dark.STACK, RoundButton.SPORTS, Dark.SEARCH, Dark.GEAR,
            Dark.RECORD, Dark.BOOKMARK};
    /** The first item's middle, the items' pitch, Settings' middle, an item's height, the icons' and names' places. */
    static final float FIRST = 224f, PITCH = 96f, FOOT = 1008f, ITEM_H = 80f, ICON = 40f, ICON_X = 56f, ICON_X_OPEN = 76f,
            LABEL_X = 120f, INSET = Focus.INSET_DRAWER;

    /** The section on screen (one of the ids above, or -1). */
    final int page;
    /** The item the remote is on (an id). */
    int focus;
    private boolean active;
    /** Home's menu: the remote comes back to the item it was on (every other screen's starts on its own). */
    boolean keep;
    private float open;
    /** How open it is, gliding (0 the rail, 1 the drawer; its unit the drawer's extra width). */
    private final Glide opening = new Glide(DRAWER_MS, DRAWER - W);

    private final Paint back = new Paint(), shadow = new Paint(), amber = Dark.fill(0xFFFFB547), ink = Dark.fill(0xFF1E1606);
    private final Paint dot = Dark.fill(Palette.ACCENT), dotEdge = Dark.line(Palette.PANEL, 3f);
    private final TextPaint label = Type.paint(Type.ITEM, Type.REGULAR, Palette.TEXT1);
    private final TextPaint name = Type.paint(36f, Type.MEDIUM, Palette.TEXT1), tv = Type.paint(Type.SMALL, Type.REGULAR, Palette.TEXT2);
    private final RectF r = new RectF();

    Rail(Context c, int page) {
        super(c);
        this.page = page;
        focus = page >= 0 ? page : HOME;
        shadow.setShader(new LinearGradient(0, 0, Ui.f(12), 0, Palette.SHADOW, 0, Shader.TileMode.CLAMP));
    }

    void setActive(boolean a) {
        boolean was = active;
        active = a;
        if (a && !keep) focus = page >= 0 ? page : HOME;
        if (a != was || Exec.inline) animateTo(a ? 1f : 0f);
        invalidate();
    }

    boolean active() {
        return active;
    }

    /** Up and Down along the items (no wrap-around); true for either, used or not. */
    boolean key(int code) {
        if (code != KeyEvent.KEYCODE_DPAD_UP && code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
        int i = indexOf(focus), n = i + (code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
        if (n >= 0 && n < ORDER.length) {
            focus = ORDER[n];
            invalidate();
        }
        return true;
    }

    /** Pure: an item's place from the top (its index in ORDER), or -1. */
    static int indexOf(int id) {
        for (int i = 0; i < ORDER.length; i++) if (ORDER[i] == id) return i;
        return -1;
    }

    /** Pure: where an item's middle is (design pixels). */
    static float y(int id) {
        return id == SETTINGS ? FOOT : FIRST + Math.max(0, indexOf(id)) * PITCH;
    }

    /** OK on an item: to its screen. */
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
            case RECORDINGS:
                tv.fromHome(new RecordingsScreen(tv));
                break;
            case MY_LIST:
                tv.fromHome(new MyListScreen(tv));
                break;
            default:
                tv.openSettings();
                break;
        }
    }

    private void animateTo(float to) {
        if (Exec.inline) opening.jump(to);
        else opening.to(to);
        open = opening.value();
        invalidate();
    }

    /** The frame's step of the drawer's glide (the same frame's time asked again moves nothing). */
    private boolean stepOpen() {
        boolean moving = opening.step();
        open = Math.max(0f, Math.min(1f, opening.value()));
        return moving;
    }

    /** Whether the drawer is still opening or closing. */
    boolean moving() {
        return opening.moving();
    }

    /** The menu's right edge now (screen pixels): 112 closed, 440 open. */
    float edge() {
        stepOpen();
        return Ui.f(W + (DRAWER - W) * open);
    }

    @Override protected void onDraw(Canvas c) {
        boolean moving = stepOpen();
        int h = getHeight();
        float right = edge();
        if (open > 0f) {
            // The drawer's soft edge over the screen.
            shadow.setAlpha(Math.round(255 * open));
            c.save();
            c.translate(right, 0);
            c.drawRect(0, 0, Ui.f(12), h, shadow);
            c.restore();
        }
        back.setColor(Palette.PANEL);
        c.drawRect(0, 0, right, h, back);

        // Homefield's mark; in the drawer, its name beside it.
        float s = Ui.f(48 + 8 * open), mx = Ui.f(32 + 16 * open), my = Ui.f(46 - 2 * open);
        Dark.mark(c, mx, my, s, amber, ink);
        if (open > 0.01f) {
            name.setColor(Palette.alpha(Palette.TEXT1, Math.round(((Palette.TEXT1 >>> 24) & 0xFF) * open)));
            tv.setColor(Palette.alpha(Palette.TEXT2, Math.round(((Palette.TEXT2 >>> 24) & 0xFF) * open)));
            c.save();
            c.clipRect(0, 0, right, h);
            c.drawText("Homefield", Ui.f(LABEL_X), Ui.f(84), name);
            c.drawText("TV", Ui.f(LABEL_X + 1), Ui.f(120), tv);
            c.restore();
        }

        float iconX = Ui.f(ICON_X + (ICON_X_OPEN - ICON_X) * open);
        for (int id : ORDER) {
            float cy = Ui.f(y(id));
            r.set(Ui.f(INSET), cy - Ui.f(ITEM_H / 2f), right - Ui.f(INSET), cy + Ui.f(ITEM_H / 2f));
            boolean here = active && id == focus;
            if (open > 0.01f && r.width() > Ui.f(ITEM_H)) {
                if (here) Focus.pill(c, r);
                else if (active && id == page) Focus.selected(c, r);
            }
            int colour = here || id == page ? Palette.TEXT1 : Palette.ICON_OFF;
            RoundButton.glyph(c, ICONS[id], iconX, cy, Ui.f(ICON), colour);
            if (open > 0.01f) {
                label.setColor(Palette.alpha(Palette.TEXT1, Math.round(((Palette.TEXT1 >>> 24) & 0xFF) * open)));
                c.save();
                c.clipRect(0, 0, right - Ui.f(INSET), h);
                c.drawText(NAMES[id], Ui.f(LABEL_X), Type.centred(label, cy), label);
                c.restore();
            }
        }
        // A newer Homefield TV waiting (Settings, Updates): a dot on Settings.
        if (Updates.waiting(getContext())) {
            float sx = iconX + Ui.f(17), sy = Ui.f(FOOT) - Ui.f(17);
            dotEdge.setColor(Palette.PANEL);
            dot.setColor(Palette.ACCENT);
            c.drawCircle(sx, sy, Ui.f(8), dotEdge);
            c.drawCircle(sx, sy, Ui.f(7), dot);
        }
        if (moving) postInvalidateOnAnimation();
    }
}
