package com.gathin.tvhomephone.sports;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.gathin.tvhomephone.home.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * The squad as the family's picture lays it out: the player in the middle large, on his tall panel with his photo
 * rising above it, his number on a coin and his name under it, his line written up the side of the card; two smaller,
 * dimmer cards either side (three on a wide screen). Swipe it left or right: it follows the finger and snaps to a card,
 * a fling carrying it on a few. Tap a neighbour to bring it to the middle; tap the middle card to open the player's
 * page. Left and right keys move it, Enter opens. Only the cards near the middle are drawn and ask for their photos
 * (the neighbours' ahead of time); a frame only moves and scales views, never makes any.
 */
final class SquadCarousel extends ViewGroup {
    interface Listener {
        /** The middle card was tapped (or Enter pressed): its player's page. */
        void open(int index);

        /** The carousel came to rest on a card. */
        void settled(int index);
    }

    private final List<SquadCard> cards = new ArrayList<>();
    private final TextView label;
    private List<TeamData.Player> players = new ArrayList<>();
    private Listener listener;
    private int club = ClubLook.NAVY, ground = ClubLook.ground(ClubLook.NAVY);
    /** The card at the middle, continuously (2.5: half way between the third and the fourth). */
    private float pos, raw;
    private int focus = -1, shownLabel = -1;
    /** The panel's width in the middle (px), and the gaps between the slots. */
    private float p, s1, s2, s3;
    private final ValueAnimator settle = ValueAnimator.ofFloat(0f, 1f);
    private float from, to;
    private int target;
    private VelocityTracker velocity;
    private float downX, downY, lastX;
    private boolean dragging;
    private int dragStart;
    private final int slop, minFling, maxFling;
    /** The order the cards are drawn in: the farthest first, the middle one last (on top). */
    private int[] order = new int[0];
    private final float[] keys = new float[64];

    SquadCarousel(Context c) {
        super(c);
        setClipChildren(false);
        setFocusable(true);
        setChildrenDrawingOrderEnabled(true);
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity();
        maxFling = vc.getScaledMaximumFlingVelocity();
        label = new TextView(c);
        label.setTypeface(Type.outfit(c, 600));
        label.setTextColor(0xE6FFFFFF);
        label.setLetterSpacing(0.28f);
        label.setIncludeFontPadding(false);
        label.setSingleLine(true);
        label.setRotation(-90f);
        label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(label);
        settle.setInterpolator(new DecelerateInterpolator(1.6f));
        settle.addUpdateListener(a -> {
            pos = from + (to - from) * a.getAnimatedFraction();
            raw = pos;
            arrange();
        });
        settle.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationStart(Animator a) {
                cancelled = false;
            }

            @Override public void onAnimationCancel(Animator a) {
                cancelled = true;
            }

            @Override public void onAnimationEnd(Animator a) {
                if (cancelled) return;
                pos = to;
                raw = pos;
                arrive(target);
            }
        });
    }

    void setListener(Listener l) {
        listener = l;
    }

    /** The club's colours for the cards. */
    void setColours(int club, int ground) {
        this.club = club;
        this.ground = ground;
        for (SquadCard c : cards) c.setColours(club, ground);
    }

    /** The club the cards are of: its competition (for its league's own pictures), its names and its crest. */
    private String[] clubInfo = {"", "", "", ""};

    void setClub(String slug, String name, String shortName, String crest) {
        clubInfo = new String[]{slug, name, shortName, crest};
        for (SquadCard c : cards) c.setClub(slug, name, shortName, crest);
    }

    /** The players shown, with the one at middle; the cards are made again (at most a squad's worth). */
    void setPlayers(List<TeamData.Player> list, int middle) {
        settle.cancel();
        players = new ArrayList<>(list);
        while (cards.size() > players.size()) removeView(cards.remove(cards.size() - 1));
        while (cards.size() < players.size()) {
            SquadCard card = new SquadCard(getContext());
            card.setColours(club, ground);
            card.setClub(clubInfo[0], clubInfo[1], clubInfo[2], clubInfo[3]);
            int at = cards.size();
            card.setOnClickListener(v -> tapped(cards.indexOf((SquadCard) v)));
            cards.add(card);
            addView(card, at);
        }
        for (int i = 0; i < players.size(); i++) {
            SquadCard card = cards.get(i);
            card.bind(players.get(i));
            if (p > 0) card.setPanelWidth(p);
        }
        order = new int[players.size() + 1];
        for (int i = 0; i < order.length; i++) order[i] = i;
        pos = raw = players.isEmpty() ? 0 : Math.max(0, Math.min(players.size() - 1, middle));
        focus = -1;
        shownLabel = -1;
        describe();
        arrive(Math.round(pos));
        requestLayout();
    }

    int count() {
        return players.size();
    }

    int focus() {
        return CarouselMath.nearest(pos, players.size());
    }

    // ---- size and place ---------------------------------------------------------------------------------------------

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        if (MeasureSpec.getMode(hSpec) == MeasureSpec.UNSPECIFIED) h = Math.round(w * 1.1f);
        setMeasuredDimension(w, h);
        float density = getResources().getDisplayMetrics().density;
        float wDp = w / density;
        // The middle panel: as tall as the stage allows, and on a narrow screen most of its width (the neighbours
        // peeping in at the sides); on a wide one about a quarter of it, with two or three cards either side.
        float share = wDp < 600 ? 0.56f : wDp < 1000 ? 0.30f : 0.25f;
        float byHeight = h / 2.12f, byWidth = w * share;
        float np = Math.max(80 * density, Math.min(byHeight, byWidth));
        if (Math.abs(np - p) > 0.5f) {
            p = np;
            for (SquadCard c : cards) c.setPanelWidth(p);
        }
        s1 = 1.18f * p;
        s2 = 0.76f * p;
        s3 = 0.6f * p;
        for (SquadCard c : cards) {
            c.measure(MeasureSpec.makeMeasureSpec(Math.round(c.cardWidth()), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.round(c.cardHeight()), MeasureSpec.EXACTLY));
        }
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(9 * density, 0.068f * p));
        label.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (SquadCard c : cards) c.layout(0, 0, c.getMeasuredWidth(), c.getMeasuredHeight());
        label.layout(0, 0, label.getMeasuredWidth(), label.getMeasuredHeight());
        arrange();
    }

    /** Where the middle card's panel is centred, top to bottom. */
    private float middleY() {
        float cardH = 2.0f * p + 0.06f * p;
        float top = Math.max(0, (getHeight() - cardH) / 2f);
        return top + 0.40f * p + 0.66f * p;
    }

    /** Every card's place, size and look for the position now, and the line's label beside the middle one. */
    private void arrange() {
        int n = cards.size();
        if (n == 0 || p <= 0) {
            label.setAlpha(0);
            return;
        }
        float cx = getWidth() / 2f, cy = middleY();
        for (int i = 0; i < n; i++) {
            SquadCard c = cards.get(i);
            float d = i - pos;
            float a = CarouselMath.alpha(d);
            if (a <= 0.01f) {
                if (c.getVisibility() != INVISIBLE) c.setVisibility(INVISIBLE);
                keys[Math.min(i, keys.length - 1)] = 99f;
                continue;
            }
            if (c.getVisibility() != VISIBLE) c.setVisibility(VISIBLE);
            float s = CarouselMath.scale(d);
            c.setPivotX(c.panelCentreX());
            c.setPivotY(c.panelCentreY());
            c.setScaleX(s);
            c.setScaleY(s);
            c.setTranslationX(cx + CarouselMath.offset(d, s1, s2, s3) - c.panelCentreX());
            c.setTranslationY(cy - c.panelCentreY() + 0.03f * p * Math.min(1f, Math.abs(d)));
            c.setAlpha(a);
            c.morph(CarouselMath.focus(d));
            if (i < keys.length) keys[i] = Math.abs(d);
        }
        sortOrder(n);
        // The line's label, up the side of the middle card; fading as the cards move between slots.
        int near = CarouselMath.nearest(pos, n);
        if (near != shownLabel) {
            shownLabel = near;
            label.setText(PlayerFacts.lineLabel(players.get(near).line));
            label.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            label.layout(0, 0, label.getMeasuredWidth(), label.getMeasuredHeight());
        }
        float lw = label.getMeasuredWidth(), lh = label.getMeasuredHeight();
        float lx = cx + 0.5f * p + 0.19f * p + lh / 2f, ly = cy + 0.05f * p;
        label.setPivotX(lw / 2f);
        label.setPivotY(lh / 2f);
        label.setTranslationX(lx - lw / 2f);
        label.setTranslationY(ly - lh / 2f);
        label.setAlpha(Math.max(0f, 1f - 2.5f * Math.abs(pos - near)));
        invalidate();
    }

    /** The drawing order by distance from the middle, farthest first; the label last. */
    private void sortOrder(int n) {
        if (order.length != n + 1) order = new int[n + 1];
        for (int i = 0; i < n; i++) order[i] = i;
        // a small insertion sort, every frame without allocating
        for (int i = 1; i < n; i++) {
            int x = order[i];
            float kx = key(x);
            int j = i - 1;
            while (j >= 0 && key(order[j]) < kx) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = x;
        }
        order[n] = n;
    }

    private float key(int i) {
        return i < keys.length ? keys[i] : 99f;
    }

    @Override protected int getChildDrawingOrder(int childCount, int drawingPosition) {
        // The children are the cards in their order, then the label (each card is added before it).
        if (order.length != childCount || drawingPosition >= order.length) return drawingPosition;
        return order[drawingPosition];
    }

    // ---- moving ------------------------------------------------------------------------------------------------------

    /** Brings card i to the middle, gently. */
    void goTo(int i) {
        if (players.isEmpty()) return;
        int t = Math.max(0, Math.min(players.size() - 1, i));
        settle.cancel();
        from = pos;
        to = t;
        target = t;
        long ms = CarouselMath.settleMs(to - from);
        if (ms <= 0) {
            pos = raw = t;
            arrange();
            arrive(t);
            return;
        }
        settle.setDuration(ms);
        settle.start();
    }

    /** Puts card i in the middle at once. */
    void jump(int i) {
        settle.cancel();
        pos = raw = Math.max(0, Math.min(players.size() - 1, i));
        arrange();
        arrive(Math.round(pos));
    }

    private void arrive(int i) {
        if (players.isEmpty()) return;
        int n = players.size();
        // The photos of the cards that show, and of the next ones either side, ahead of time.
        for (int k = Math.max(0, i - 3); k <= Math.min(n - 1, i + 3); k++) cards.get(k).request();
        if (i != focus) {
            focus = i;
            describe();
            if (listener != null) listener.settled(i);
        }
    }

    private void tapped(int i) {
        switch (CarouselMath.tap(i, pos, settle.isRunning() || dragging, players.size())) {
            case CarouselMath.OPEN:
                if (listener != null) listener.open(i);
                break;
            case CarouselMath.MOVE:
                goTo(i);
                break;
            default:
                break;
        }
    }

    /** What each card says to a screen reader: who, his number and line, and what a tap does. */
    private void describe() {
        int middle = CarouselMath.nearest(pos, players.size());
        for (int i = 0; i < cards.size(); i++) {
            TeamData.Player pl = players.get(i);
            String what = pl.name + (pl.number.isEmpty() ? "" : ", number " + pl.number)
                    + (PlayerFacts.lineOne(pl.line).isEmpty() ? "" : ", " + PlayerFacts.lineOne(pl.line).toLowerCase(java.util.Locale.ROOT));
            cards.get(i).setContentDescription(what + (i == middle ? ". Opens his page" : ". Brings him to the middle"));
        }
    }

    // ---- touch --------------------------------------------------------------------------------------------------------

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        track(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                dragging = false;
                if (settle.isRunning()) {
                    // caught while moving: held where it is, and dragged on from there
                    settle.cancel();
                    startDrag();
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && wantsDrag(e)) {
                    startDrag();
                    lastX = e.getX();
                }
                return dragging;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) release(e.getActionMasked() == MotionEvent.ACTION_CANCEL);
                return false;
            default:
                return dragging;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        track(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                if (settle.isRunning()) {
                    settle.cancel();
                    startDrag();
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && wantsDrag(e)) {
                    startDrag();
                    lastX = e.getX();
                }
                if (dragging) {
                    float dx = e.getX() - lastX;
                    lastX = e.getX();
                    raw -= dx / Math.max(1f, s1);
                    pos = CarouselMath.rubber(raw, players.size());
                    arrange();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) release(false);
                else goTo(CarouselMath.nearest(pos, players.size()));
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) release(true);
                else goTo(CarouselMath.nearest(pos, players.size()));
                return true;
            default:
                return true;
        }
    }

    private boolean wantsDrag(MotionEvent e) {
        float dx = Math.abs(e.getX() - downX), dy = Math.abs(e.getY() - downY);
        return dx > slop && dx > dy * 1.2f;
    }

    private void startDrag() {
        dragging = true;
        dragStart = CarouselMath.nearest(pos, players.size());
        raw = pos;
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
    }

    private void track(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (velocity != null) velocity.clear();
        }
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(e);
    }

    private void release(boolean cancelled) {
        dragging = false;
        float v = 0;
        if (velocity != null && !cancelled) {
            velocity.computeCurrentVelocity(1000, maxFling);
            v = -velocity.getXVelocity() / Math.max(1f, s1);
        }
        goTo(CarouselMath.flingTarget(pos, v, dragStart, minFling / Math.max(1f, s1) * 3f, players.size()));
    }

    @Override protected void onDetachedFromWindow() {
        settle.cancel();
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
        super.onDetachedFromWindow();
    }

    // ---- keys and screen readers ------------------------------------------------------------------------------------

    @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
        int at = CarouselMath.nearest(pos, players.size());
        if (CarouselMath.opens(keyCode) && !players.isEmpty()) {
            if (listener != null) listener.open(at);
            return true;
        }
        int t = CarouselMath.keyTarget(at, keyCode, players.size());
        if (t >= 0 && t != at) {
            goTo(t);
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        int at = CarouselMath.nearest(pos, players.size());
        if (at > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        if (at < players.size() - 1) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.setScrollable(players.size() > 1);
    }

    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        int at = CarouselMath.nearest(pos, players.size());
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD && at < players.size() - 1) {
            goTo(at + 1);
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD && at > 0) {
            goTo(at - 1);
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }

    @Override public CharSequence getAccessibilityClassName() {
        return "android.widget.HorizontalScrollView";
    }

    /** The cards (for the previews and the tests' look at it). */
    List<SquadCard> cards() {
        return cards;
    }

    View labelView() {
        return label;
    }
}
