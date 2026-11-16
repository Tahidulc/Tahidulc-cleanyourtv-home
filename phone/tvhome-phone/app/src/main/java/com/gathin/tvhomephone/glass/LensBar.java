package com.gathin.tvhomephone.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * A bar of liquid glass with a lens on what is chosen, as the family asked: slide a finger along it and the lens lifts,
 * swells and follows, stretching with the speed of the finger, magnifying what passes under it, with a tick at each
 * stop; let go and it springs onto the nearest, which is then picked. A tap springs it straight there. Two shapes: the
 * tabs along the foot of the app (an icon over a word, equal stops), and a pill (icon buttons that act, and words to
 * choose between, as Watch's Discover, Movies and TV shows). Each stop is also a plain view for TalkBack.
 */
public class LensBar extends BlurBehind {
    public static final int TABS = 0, PILL = 1;

    public interface OnPick {
        void picked(int id);
    }

    private static final class Item {
        int id, icon = -1;
        String text;
        Runnable action;
        Icons glyph;
        float l, r, textW;
        View target;

        boolean choosable() {
            return action == null;
        }

        float centre() {
            return (l + r) / 2f;
        }
    }

    private final int mode;
    private final List<Item> items = new ArrayList<>();
    private final GlassDrawable lens;
    private final Paint words = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final int slop, pad;
    private int chosen = -1, under = -1, pressed = -1;
    private OnPick onPick;
    // The lens: its centre and width, each on a spring; lift is how far it has risen under a finger.
    private float lensX = -1, lensV, lensTarget, lensW = -1, lensWV, lensWTarget, lift, liftV, liftTarget, stretch;
    private boolean dragging, running, laid;
    private float downX, downY;
    private VelocityTracker vt;
    private long last;
    private final Runnable frame = this::step;

    public LensBar(Context c, int mode) {
        super(c, mode == TABS ? Glass.BAR_DP / 2f : 22, mode == TABS ? GlassDrawable.BAR : GlassDrawable.CARD);
        this.mode = mode;
        lens = new GlassDrawable(c, mode == TABS ? 26 : 18, GlassDrawable.LENS);
        words.setTextAlign(Paint.Align.CENTER);
        words.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, mode == TABS ? 11f : 14.5f,
                c.getResources().getDisplayMetrics()));
        Typeface face = mode == TABS ? Glass.bodySemi(c) : Glass.heading(c, 500);
        words.setTypeface(face);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        pad = Glass.dp(c, mode == TABS ? 6 : 4);
        setClipChildren(false);
    }

    /** A tab: its id (the menu's), its icon (Icons) and its word. */
    public LensBar tab(int id, int icon, String text) {
        return add(id, icon, text, null);
    }

    /** A pill's word to choose, with its id. */
    public LensBar segment(int id, String text) {
        return add(id, -1, text, null);
    }

    /** A pill's icon button (search, settings): it acts, it is never chosen. */
    public LensBar action(int icon, String label, Runnable run) {
        return add(View.NO_ID, icon, label, run);
    }

    private LensBar add(int id, int icon, String text, Runnable run) {
        Item it = new Item();
        it.id = id;
        it.icon = icon;
        it.text = text;
        it.action = run;
        if (icon >= 0) it.glyph = new Icons(getContext(), icon, Glass.MUTED);
        it.textW = text == null ? 0 : words.measureText(text);
        View t = new View(getContext());
        t.setContentDescription(text);
        t.setFocusable(true);
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        int index = items.size();
        t.setOnClickListener(v -> tap(index));
        it.target = t;
        addView(t, new LayoutParams(0, 0));
        items.add(it);
        laid = false;
        requestLayout();
        return this;
    }

    public void setOnPick(OnPick p) {
        onPick = p;
    }

    /** The chosen item's id, or NO_ID. */
    public int selected() {
        return chosen < 0 ? View.NO_ID : items.get(chosen).id;
    }

    /** Chooses the item with this id (without telling OnPick): the lens springs there, or jumps when not animated. */
    public void select(int id, boolean animate) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == id && items.get(i).choosable()) {
                choose(i, animate);
                return;
            }
        }
    }

    private void choose(int i, boolean animate) {
        chosen = i;
        for (int k = 0; k < items.size(); k++) items.get(k).target.setSelected(k == i);
        if (!laid) return;
        Item it = items.get(i);
        lensTarget = it.centre();
        lensWTarget = lensWidth(it);
        if (!animate || lensX < 0) {
            lensX = lensTarget;
            lensW = lensWTarget;
            lensV = lensWV = 0;
            invalidate();
        } else {
            run();
        }
    }

    private float lensWidth(Item it) {
        return Math.max(0, it.r - it.l - (mode == TABS ? Glass.dp(getContext(), 4) : 0));
    }

    @Override protected void onMeasure(int w, int h) {
        int height = Glass.dp(getContext(), mode == TABS ? Glass.BAR_DP : 46);
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        place(r - l);
        for (Item it : items) it.target.layout(Math.round(it.l), 0, Math.round(it.r), b - t);
        laid = !items.isEmpty() && r > l;
        if (laid && chosen >= 0 && (lensX < 0 || changed)) {
            Item it = items.get(chosen);
            lensX = lensTarget = it.centre();
            lensW = lensWTarget = lensWidth(it);
        }
    }

    /** Where each item sits: tabs share the width; a pill's icons are square and its words share what is left. */
    private void place(int width) {
        if (items.isEmpty()) return;
        float inner = width - pad * 2f;
        if (mode == TABS) {
            float w = inner / items.size();
            for (int i = 0; i < items.size(); i++) {
                items.get(i).l = pad + i * w;
                items.get(i).r = pad + (i + 1) * w;
            }
            return;
        }
        float icon = Glass.dp(getContext(), 42), padH = Glass.dp(getContext(), 14);
        int texts = 0;
        float textSum = 0;
        for (Item it : items) {
            if (it.action == null || it.icon < 0) {
                texts++;
                textSum += it.textW + padH * 2;
            }
        }
        float icons = 0;
        for (Item it : items) if (it.action != null && it.icon >= 0) icons += icon;
        float spare = Math.max(0, inner - icons - textSum);
        float x = pad;
        for (Item it : items) {
            float w = it.action != null && it.icon >= 0 ? icon : it.textW + padH * 2 + (texts == 0 ? 0 : spare / texts);
            if (inner - icons < textSum && it.action == null) w = (inner - icons) * (it.textW + padH * 2) / textSum;
            it.l = x;
            it.r = x + w;
            x += w;
        }
    }

    // ---- touch: a tap, or a finger sliding the lens --------------------------------------------------------------------

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        return true;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (items.isEmpty() || !laid) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                dragging = false;
                pressed = at(e.getX());
                under = pressed;
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                // A finger on the chosen one: the lens rises, ready to be slid.
                if (pressed == chosen) {
                    liftTarget = 1f;
                    run();
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (vt != null) vt.addMovement(e);
                if (!dragging && Math.abs(e.getX() - downX) > slop && Math.abs(e.getX() - downX) > Math.abs(e.getY() - downY)) {
                    dragging = true;
                    liftTarget = 1f;
                }
                if (dragging) {
                    follow(e.getX());
                    run();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (vt != null) {
                    vt.addMovement(e);
                    vt.computeCurrentVelocity(1000);
                }
                float v = vt == null ? 0 : vt.getXVelocity();
                if (vt != null) {
                    vt.recycle();
                    vt = null;
                }
                liftTarget = 0f;
                if (dragging) {
                    dragging = false;
                    // A flick carries on a little in its direction.
                    int to = nearestChoosable(clampX(lensTarget + v * 0.06f));
                    pick(to);
                } else {
                    int at = at(e.getX());
                    if (at >= 0) tap(at);
                }
                pressed = -1;
                under = -1;
                run();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (vt != null) {
                    vt.recycle();
                    vt = null;
                }
                dragging = false;
                liftTarget = 0f;
                pressed = -1;
                under = -1;
                if (chosen >= 0) choose(chosen, true);
                run();
                return true;
            default:
                return true;
        }
    }

    private void follow(float x) {
        lensTarget = clampX(x);
        int over = nearestChoosable(lensTarget);
        if (over >= 0) lensWTarget = lensWidth(items.get(over));
        if (over != under && over >= 0) {
            under = over;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    private float clampX(float x) {
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (Item it : items) {
            if (!it.choosable()) continue;
            lo = Math.min(lo, it.centre());
            hi = Math.max(hi, it.centre());
        }
        if (lo > hi) return x;
        return Math.max(lo, Math.min(hi, x));
    }

    private int at(float x) {
        for (int i = 0; i < items.size(); i++) if (x >= items.get(i).l && x < items.get(i).r) return i;
        return x < pad ? 0 : items.size() - 1;
    }

    private int nearestChoosable(float x) {
        int best = -1;
        float d = Float.MAX_VALUE;
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).choosable()) continue;
            float dd = Math.abs(items.get(i).centre() - x);
            if (dd < d) {
                d = dd;
                best = i;
            }
        }
        return best;
    }

    private void tap(int i) {
        if (i < 0 || i >= items.size()) return;
        Item it = items.get(i);
        if (it.action != null) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            it.action.run();
            return;
        }
        pick(i);
    }

    private void pick(int i) {
        if (i < 0) return;
        boolean changed = i != chosen;
        choose(i, true);
        if (changed) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (onPick != null) onPick.picked(items.get(i).id);
        }
    }

    // ---- the springs ---------------------------------------------------------------------------------------------------

    private void run() {
        if (running) return;
        running = true;
        last = 0;
        postOnAnimation(frame);
    }

    private void step() {
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 1f / 60f : Math.min(0.032f, (now - last) / 1000f);
        last = now;
        // Quick and close under the finger; a little bounce when let go. Stepped in small slices, so a slow frame (a
        // phone busy for a moment) can't throw the springs off.
        float k = dragging ? 1100f : 420f, zeta = dragging ? 0.95f : 0.62f;
        float c = 2f * (float) Math.sqrt(k) * zeta;
        float kl = 320f, cl = 2f * (float) Math.sqrt(kl) * 0.7f;
        int slices = Math.max(1, (int) Math.ceil(dt / 0.004f));
        float h = dt / slices;
        for (int i = 0; i < slices; i++) {
            lensV += (-k * (lensX - lensTarget) - c * lensV) * h;
            lensX += lensV * h;
            lensWV += (-k * (lensW - lensWTarget) - c * lensWV) * h;
            lensW += lensWV * h;
            liftV += (-kl * (lift - liftTarget) - cl * liftV) * h;
            lift += liftV * h;
        }
        if (!Float.isFinite(lensX) || !Float.isFinite(lensW) || !Float.isFinite(lift)
                || !Float.isFinite(lensV) || !Float.isFinite(lensWV) || !Float.isFinite(liftV)) {
            lensX = lensTarget;
            lensW = lensWTarget;
            lift = liftTarget;
            lensV = lensWV = liftV = 0;
        }
        float slot = items.isEmpty() ? 1 : Math.max(1, getWidth() / (float) items.size());
        stretch = Math.min(0.34f, Math.abs(lensV) / (slot * 10f));
        invalidate();
        boolean still = !dragging && Math.abs(lensX - lensTarget) < 0.4f && Math.abs(lensV) < 4f
                && Math.abs(lensW - lensWTarget) < 0.4f && Math.abs(lensWV) < 4f
                && Math.abs(lift - liftTarget) < 0.004f && Math.abs(liftV) < 0.02f;
        if (still) {
            lensX = lensTarget;
            lensW = lensWTarget;
            lift = liftTarget;
            lensV = lensWV = liftV = 0;
            stretch = 0;
            running = false;
            return;
        }
        postOnAnimation(frame);
    }

    // ---- drawing -------------------------------------------------------------------------------------------------------

    @Override protected void dispatchDraw(Canvas cv) {
        super.dispatchDraw(cv);
        if (items.isEmpty() || !laid) return;
        int h = getHeight();
        float lx = lensX < 0 ? -1 : lensX;
        if (chosen >= 0 && lx >= 0) {
            float base = lensW > 0 ? lensW : lensWidth(items.get(chosen));
            float w = base * (1f + stretch + lift * 0.1f);
            float lh = (h - pad * 2f) * (1f - stretch * 0.22f + lift * 0.08f);
            lh = Math.min(lh, h - Glass.dp(getContext(), 2));
            lens.setLift(Math.min(1f, lift + (dragging ? 0.25f : 0f)));
            lens.setBounds(Math.round(lx - w / 2f), Math.round(h / 2f - lh / 2f), Math.round(lx + w / 2f), Math.round(h / 2f + lh / 2f));
            lens.draw(cv);
        }
        float slot = Math.max(1, (getWidth() - pad * 2f) / items.size());
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            float cx = it.centre();
            // How near the lens is: 1 right under it.
            float d = lx < 0 ? 9f : (cx - lx) / slot;
            float near = it.choosable() ? (float) Math.exp(-d * d * 5f) : 0f;
            boolean on = i == chosen || i == pressed;
            float mag = 1f + lift * 0.2f * near;
            int colour = blend(Glass.MUTED, Glass.TEXT, Math.max(on ? 1f : 0f, near * (lift > 0.05f ? 1f : (i == chosen ? 1f : 0f))));
            int save = cv.save();
            cv.scale(mag, mag, cx, h / 2f);
            if (mode == TABS) {
                int s = Glass.dp(getContext(), 24);
                float top = h / 2f - Glass.dp(getContext(), 19);
                if (it.glyph != null) {
                    it.glyph.setColour(colour);
                    it.glyph.setBounds(Math.round(cx - s / 2f), Math.round(top), Math.round(cx + s / 2f), Math.round(top + s));
                    it.glyph.draw(cv);
                }
                words.setColor(colour);
                cv.drawText(it.text == null ? "" : it.text, cx, h / 2f + Glass.dp(getContext(), 19), words);
            } else if (it.action != null && it.icon >= 0) {
                int s = Glass.dp(getContext(), 21);
                it.glyph.setColour(i == pressed ? Glass.TEXT : Glass.SOFT);
                it.glyph.setBounds(Math.round(cx - s / 2f), Math.round(h / 2f - s / 2f), Math.round(cx + s / 2f), Math.round(h / 2f + s / 2f));
                it.glyph.draw(cv);
            } else {
                words.setColor(i == chosen ? Glass.TEXT : blend(Glass.MUTED, Glass.TEXT, near * lift));
                words.setFakeBoldText(i == chosen);
                Paint.FontMetrics fm = words.getFontMetrics();
                cv.drawText(it.text == null ? "" : it.text, cx, h / 2f - (fm.ascent + fm.descent) / 2f, words);
                words.setFakeBoldText(false);
            }
            cv.restoreToCount(save);
        }
    }

    /** Pure: a colour part of the way (0 to 1) from a to b. */
    static int blend(int a, int b, float t) {
        float f = Math.max(0f, Math.min(1f, t));
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(aa + (ba - aa) * f) << 24) | (Math.round(ar + (br - ar) * f) << 16)
                | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
    }
}
