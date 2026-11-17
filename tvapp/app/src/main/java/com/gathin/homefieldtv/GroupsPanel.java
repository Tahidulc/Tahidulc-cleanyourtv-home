package com.gathin.homefieldtv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Exec;

import java.util.ArrayList;
import java.util.List;

/**
 * The groups column beside the menu (the closed rail, or the open drawer): 504 px of the ground, each playlist's name as
 * a heading (30 px, medium, 56 px in, with a chevron that folds or opens it) and its groups under it (ROW size, 80 px
 * in), 77 px a row. Lists such as Movies' can start with bold entries of their own (My list, History: bold).
 *
 * The remote's row is in the focus pill (32 px in from both sides: 440 wide) and always sits at the same height,
 * FOCUS_Y (502), the list sliding under it (SCROLL_MS); the group on screen keeps the selected pill while the remote is
 * elsewhere (setActive(false)). Moving onto a group tells the host at once (focused): the guide switches group as the
 * remote moves, with no OK. OK on a heading folds or opens it; OK on a group is chosen. Left and Right go to the host
 * (edge). It slides in from the left (slideIn, 150 ms) and follows the menu's edge: set x to Rail.W, or Rail.DRAWER
 * while the drawer is open.
 *
 * <pre>
 *   set(entries, focus)            the column's rows (Entry.heading / Entry.group / Entry.bold), the remote on focus
 *   choose(tag)                    the group on screen (its selected pill), the remote put on it too
 *   mark(tag)                      the group on screen only (the remote and the list's slide left as they are)
 *   key(code) / ok()               the remote
 *   lift(lo, hi) / drop() / cancelLift()   Move group (MoveMode): the remote's row lifted, Up and Down moving it among
 *                                  rows lo..hi (the host not told), put down (the rows' tags in their new order) or back
 *   focusIndex() / count() / entry(i) / focusedRow(out)   the rows as shown, and the remote's row on the screen
 * </pre>
 */
final class GroupsPanel extends View {
    static final float W = 504f, ROW = 77f, FOCUS_Y = 502f, TOP = 38f, HEAD_X = 56f, GROUP_X = 80f, INSET = Focus.INSET_DRAWER;
    static final long SCROLL_MS = 200, SLIDE_MS = 150;

    /** A row: a playlist's heading (folds its groups), a group, or a bold entry of the list's own. */
    static final class Entry {
        final String name;
        final Object tag;
        final int kind;
        boolean folded;

        private Entry(String name, Object tag, int kind) {
            this.name = name == null ? "" : name;
            this.tag = tag;
            this.kind = kind;
        }

        static Entry heading(String name, Object tag) {
            return new Entry(name, tag, HEADING);
        }

        static Entry group(String name, Object tag) {
            return new Entry(name, tag, GROUP);
        }

        static Entry bold(String name, Object tag) {
            return new Entry(name, tag, BOLD);
        }

        /** A list's own entry in the regular weight, 64 px in (PLAIN_X), with no heading over it: My list's lists. */
        static Entry plain(String name, Object tag) {
            return new Entry(name, tag, PLAIN);
        }
    }

    static final int GROUP = 0, HEADING = 1, BOLD = 2;
    /** A plain entry (Entry.plain) and its text's x in the column, as My list's are drawn (DEVICE.md 160: x 176). */
    static final int PLAIN = 3;
    static final float PLAIN_X = 64f;

    interface Host {
        /** The remote moved onto a row (a group: show it now). */
        void focused(GroupsPanel panel, Entry entry);

        /** OK on a group or a bold entry. */
        void chosen(GroupsPanel panel, Entry entry);

        /** Left or Right (Ui.LEFT, Ui.RIGHT), or past the ends: true when the screen took it. */
        boolean edge(GroupsPanel panel, int dir);
    }

    /** The column's left edge (design pixels): beside the rail, or the drawer. */
    float x = Rail.W;
    /** The column's own colour (0: the ground, as beside the menu; the player's lists give theirs). */
    int background;
    private final List<Entry> all = new ArrayList<>(), shown = new ArrayList<>();
    private int focus;
    private Object current;
    private boolean active = true;
    private Host host;
    private float scroll, scrollFrom, scrollTo;
    private long scrollAt;
    private ValueAnimator slide;

    private final Paint back = new Paint(), chevron = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint headP = Type.paint(30f, Type.MEDIUM, Palette.TEXT1), groupP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT1);
    private final TextPaint boldP = Type.paint(Type.ROW, Type.BOLD, Palette.TEXT1);
    private final RectF r = new RectF();

    GroupsPanel(Context c) {
        super(c);
        chevron.setStyle(Paint.Style.STROKE);
        chevron.setStrokeCap(Paint.Cap.ROUND);
        chevron.setStrokeJoin(Paint.Join.ROUND);
    }

    void setHost(Host h) {
        host = h;
    }

    void set(List<Entry> entries, int focusAt) {
        all.clear();
        all.addAll(entries);
        rebuild();
        focus = Math.max(0, Math.min(focusAt, shown.size() - 1));
        settle(true);
        invalidate();
    }

    /** The group on screen: its selected pill, and the remote on it. */
    void choose(Object tag) {
        current = tag;
        for (int i = 0; i < shown.size(); i++) {
            if (tag != null && tag.equals(shown.get(i).tag)) {
                focus = i;
                settle(true);
                break;
            }
        }
        invalidate();
    }

    /** The group on screen (its selected pill), the remote left where it is and the list's slide left running. */
    void mark(Object tag) {
        current = tag;
        invalidate();
    }

    Entry focused() {
        return focus >= 0 && focus < shown.size() ? shown.get(focus) : null;
    }

    void setActive(boolean a) {
        active = a;
        invalidate();
    }

    boolean active() {
        return active;
    }

    /** In from the left, fading up (150 ms). */
    void slideIn() {
        if (slide != null) slide.cancel();
        if (Exec.inline) {
            setTranslationX(0f);
            setAlpha(1f);
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(SLIDE_MS);
        a.setInterpolator(Fade.EASE);
        a.addUpdateListener(v -> {
            float k = (Float) v.getAnimatedValue();
            setTranslationX(-Ui.f(40) * (1f - k));
            setAlpha(k);
        });
        slide = a;
        a.start();
    }

    /** The rows shown: a folded heading's groups are left out. */
    private void rebuild() {
        shown.clear();
        boolean hide = false;
        for (Entry e : all) {
            if (e.kind == HEADING) {
                hide = e.folded;
                shown.add(e);
            } else if (e.kind == BOLD || !hide) {
                shown.add(e);
            }
        }
    }

    /** Pure: the scroll that puts row k at FOCUS_Y (negative near the top: the list starts lower). */
    static float scrollFor(int k) {
        return TOP + k * ROW - FOCUS_Y;
    }

    private void settle(boolean now) {
        float target = scrollFor(focus);
        if (now || Exec.inline) {
            scroll = scrollFrom = scrollTo = target;
            scrollAt = 0;
        } else if (target != scrollTo) {
            scrollFrom = scroll;
            scrollTo = target;
            scrollAt = SystemClock.uptimeMillis();
        }
    }

    boolean key(int code) {
        if (liftFrom >= 0) return liftKey(code);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                int n = focus + (code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                if (n < 0 || n >= shown.size()) return host == null || host.edge(this, code == KeyEvent.KEYCODE_DPAD_UP ? Ui.UP : Ui.DOWN);
                focus = n;
                settle(false);
                invalidate();
                if (host != null) host.focused(this, shown.get(n));
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return host != null && host.edge(this, Ui.LEFT);
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return host != null && host.edge(this, Ui.RIGHT);
            default:
                return false;
        }
    }

    // ---- a row lifted and moved (MoveMode: Move group) --------------------------------------------------------------

    /** While a row is lifted: where it was, the rows it may move among, and the rows as they were. */
    private int liftFrom = -1, liftLo, liftHi;
    private final List<Entry> liftAll = new ArrayList<>();

    /** The remote's row lifted, to move among rows lo..hi (as shown) with Up and Down; the host isn't told of them. */
    void lift(int lo, int hi) {
        if (focus < lo || focus > hi) return;
        liftFrom = focus;
        liftLo = lo;
        liftHi = hi;
        liftAll.clear();
        liftAll.addAll(all);
        invalidate();
    }

    boolean lifted() {
        return liftFrom >= 0;
    }

    /** Whether the lifted row is somewhere else now. */
    boolean liftMoved() {
        return liftFrom >= 0 && liftFrom != focus;
    }

    private boolean liftKey(int code) {
        if (code != KeyEvent.KEYCODE_DPAD_UP && code != KeyEvent.KEYCODE_DPAD_DOWN) return true;
        int n = focus + (code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
        if (n < liftLo || n > liftHi) return true;
        Entry a = shown.get(focus), b = shown.get(n);
        shown.set(focus, b);
        shown.set(n, a);
        int ia = all.indexOf(a), ib = all.indexOf(b);
        if (ia >= 0 && ib >= 0) {
            all.set(ia, b);
            all.set(ib, a);
        }
        focus = n;
        settle(false);
        invalidate();
        return true;
    }

    /** The lifted row put down where it is: the rows lo..hi's tags in their order now. */
    List<Object> drop() {
        List<Object> out = new ArrayList<>();
        if (liftFrom < 0) return out;
        for (int i = liftLo; i <= liftHi && i < shown.size(); i++) out.add(shown.get(i).tag);
        liftFrom = -1;
        liftAll.clear();
        invalidate();
        return out;
    }

    /** The lifted row put back where it was. */
    void cancelLift() {
        if (liftFrom < 0) return;
        all.clear();
        all.addAll(liftAll);
        liftAll.clear();
        rebuild();
        focus = Math.max(0, Math.min(liftFrom, shown.size() - 1));
        liftFrom = -1;
        settle(false);
        invalidate();
    }

    /** The remote's row's place among the rows shown, how many there are, and a row. */
    int focusIndex() {
        return focus;
    }

    int count() {
        return shown.size();
    }

    Entry entry(int i) {
        return i >= 0 && i < shown.size() ? shown.get(i) : null;
    }

    /** The remote's row on the screen (screen pixels, the column's slide included), for a menu to leave undimmed. */
    RectF focusedRow(RectF out) {
        float top = TOP + focus * ROW - scroll, dx = getTranslationX();
        out.set(Ui.f(x + INSET) + dx, Ui.f(top), Ui.f(x + W - INSET) + dx, Ui.f(top + ROW));
        return out;
    }

    void ok() {
        Entry e = focused();
        if (e == null) return;
        if (e.kind == HEADING) {
            e.folded = !e.folded;
            rebuild();
            focus = Math.max(0, shown.indexOf(e));
            settle(false);
            invalidate();
            return;
        }
        if (host != null) host.chosen(this, e);
    }

    @Override protected void onDraw(Canvas c) {
        long t = SystemClock.uptimeMillis();
        boolean moving = false;
        if (scrollAt != 0) {
            float k = Math.min(1f, (t - scrollAt) / (float) SCROLL_MS);
            scroll = scrollFrom + (scrollTo - scrollFrom) * Fade.EASE.getInterpolation(k);
            if (k >= 1f) scrollAt = 0;
            else moving = true;
        }
        float x0 = Ui.f(x), x1 = Ui.f(x + W);
        back.setColor(background != 0 ? background : Palette.GROUND);
        c.drawRect(x0, 0, x1, getHeight(), back);
        c.save();
        c.clipRect(x0, 0, x1, getHeight());
        for (int i = 0; i < shown.size(); i++) {
            float top = TOP + i * ROW - scroll;
            if (top > 1080f || top + ROW < 0f) continue;
            Entry e = shown.get(i);
            float cy = Ui.f(top + ROW / 2f);
            r.set(x0 + Ui.f(INSET), Ui.f(top), x1 - Ui.f(INSET), Ui.f(top + ROW));
            if (i == focus && active) Focus.pill(c, r);
            else if (current != null && current.equals(e.tag) && e.kind != HEADING) Focus.selected(c, r);
            if (e.kind == HEADING) {
                headP.setColor(Palette.TEXT1);
                c.drawText(Ui.fit(e.name, headP, Ui.f(W - HEAD_X - 96)), x0 + Ui.f(HEAD_X), Type.centred(headP, cy), headP);
                chevron.setColor(Palette.TEXT2);
                chevron.setStrokeWidth(Math.max(1f, Ui.f(3f)));
                Ui.chevron(c, x1 - Ui.f(68), cy, Ui.f(10), e.folded ? Ui.DOWN : Ui.UP, chevron);
            } else {
                TextPaint p = e.kind == BOLD ? boldP : groupP;
                p.setColor(Palette.TEXT1);
                float tx = e.kind == BOLD ? HEAD_X : e.kind == PLAIN ? PLAIN_X : GROUP_X;
                c.drawText(Ui.fit(e.name, p, Ui.f(W - tx - (i == focus && liftFrom >= 0 ? 96 : 48))), x0 + Ui.f(tx), Type.centred(p, cy), p);
            }
            // Lifted (Move group): raised, its edge in the accent, ▲▼ at its right.
            if (i == focus && liftFrom >= 0) MoveMode.drawLifted(c, r, x1 - Ui.f(68), cy);
        }
        c.restore();
        if (moving) postInvalidateOnAnimation();
    }
}
