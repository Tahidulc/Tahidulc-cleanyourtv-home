package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.OverScroller;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.R;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * The TV guide's grid, in Homefield TV 2.0's 07 look made for a finger (TvLook): on the TV section's softly lit ground
 * (TvGround), the day in amber at the top left and the times across with an amber dot at now; each channel a row of
 * rounded cards, its logo on a raised tile in the logo's own colour at the row's start (or as it is, or on a black tile:
 * LogoTiles; a star on a favourite), and its programmes as cards after it, each filled by the programme's own picture
 * as Homefield TV's are (ProgArt finds it, ProgPics makes it at the card's size with its faces whole, PicDraw draws it
 * with a soft shade under the words only; a card too small for its faces in the picture's own colour; with no picture,
 * the channel's logo on its colour; frosted glass while one is looked for), what's on now brighter (the rest a little
 * dimmer) with a white line for how far in it is, the one chosen ringed in white and lifted, one pressed lifting under
 * the finger; the channel's name with its picture's tag and each start time over the titles, in Inter. One RecyclerView of rows,
 * each row one view that draws its blocks from a shared horizontal offset, so twenty thousand channels and a day of
 * guide stay smooth: no view per programme. A sideways drag or fling moves every row and the ruler together; up and
 * down is the RecyclerView's own scrolling. A tap on a logo plays the channel; a long press on it, or on a programme,
 * opens the channel's menu (favourites among it).
 */
@SuppressLint("ViewConstructor")
final class GuideGrid extends FrameLayout {
    /** What a touch in the grid asks for. */
    interface Listener {
        /** A programme's block (index into the channel's schedule; -1 where the guide has nothing). */
        void onProgramme(Channel ch, int index);

        /** The channel's own cell: play it. */
        void onChannel(Channel ch);

        /** The star beside the logo. */
        void onFavourite(Channel ch);

        /** A long press on the channel or one of its programmes: the channel's menu. */
        void onMenu(Channel ch);

        /** The day at the grid's left edge changed (for the day button's label). */
        void onDay(long leftTime);
    }

    private static final Object SELECTION = new Object();

    private final Library lib;
    private final State st;
    final RecyclerView rows;
    final TextView corner;
    private final Ruler ruler;
    private final Rows adapter = new Rows();
    private final OverScroller scroller;
    private final int slop, minFling, maxFling, colW, rowH, margin;
    /** The logos' tiles start this far in from the screen's left edge. */
    private final int tileIn;
    private final boolean wide;
    private VelocityTracker tracker;
    private float downX, downY, lastX;
    private boolean dragging, vertical;
    private long pending = -1, shownDay = Long.MIN_VALUE;
    private List<Channel> channels = new ArrayList<>();
    private Listener listener;
    private final Runnable fling = new Runnable() {
        @Override public void run() {
            if (scroller.computeScrollOffset()) {
                setOffset(scroller.getCurrX());
                postOnAnimation(this);
            }
        }
    };

    GuideGrid(Context c, Library lib, boolean wide) {
        super(c);
        this.lib = lib;
        this.wide = wide;
        st = new State(c, wide);
        margin = Ui.dp(c, 12);
        colW = Ui.dp(c, wide ? 120 : 88);
        tileIn = Ui.dp(c, 6);
        st.tileW = colW;
        rowH = Ui.dp(c, wide ? 70 : 62);
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity();
        maxFling = vc.getScaledMaximumFlingVelocity();
        scroller = new OverScroller(c);
        // No ground of its own: the TV section's (TvGround) shows through the glass.

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        // The head: the day ("Today") big at the left, the times across with a white mark at now.
        FrameLayout head = new FrameLayout(c);
        ruler = new Ruler(c);
        head.addView(ruler, new FrameLayout.LayoutParams(-1, -1));
        // The day in amber, as 07 has its date over the guide.
        corner = TvLook.text(c, "", wide ? 22 : 20, TvLook.AMBER, true);
        corner.setGravity(Gravity.CENTER_VERTICAL);
        corner.setPadding(margin, 0, Ui.dp(c, 8), 0);
        corner.setBackground(Ui.touch(c, false, 0, 12));
        corner.setContentDescription(c.getString(R.string.live_pick_day));
        corner.setSingleLine(true);
        head.addView(corner, new FrameLayout.LayoutParams(colW + margin + Ui.dp(c, 30), -1));
        col.addView(head, new LinearLayout.LayoutParams(-1, Ui.dp(c, 52)));

        rows = new RecyclerView(c);
        rows.setLayoutManager(new LinearLayoutManager(c));
        rows.setHasFixedSize(true);
        rows.setItemAnimator(null);
        rows.setAdapter(adapter);
        rows.setClipToPadding(false);
        rows.setPadding(0, Ui.dp(c, 2), 0, Ui.dp(c, 16));
        col.addView(rows, new LinearLayout.LayoutParams(-1, 0, 1f));
        addView(col, new LayoutParams(-1, -1));
    }

    void setListener(Listener l) { listener = l; }

    /** The channels down the side (the rows keep their place when it is the same list again). */
    @SuppressLint("NotifyDataSetChanged")
    void setChannels(List<Channel> list) {
        channels = list;
        adapter.notifyDataSetChanged();
    }

    List<Channel> channels() { return channels; }

    /** The time across: kept at the same moment on the left when there was one already. */
    void setTimeline(Timeline tl) {
        long left = st.tl == null ? -1 : st.tl.time(st.offset);
        st.tl = tl;
        st.now = System.currentTimeMillis();
        if (left >= 0) {
            st.offset = Float.NaN;
            scrollToTime(left, false);
        } else {
            st.offset = 0;
        }
        invalidateAll();
    }

    Timeline timeline() { return st.tl; }

    long leftTime() { return st.tl == null || Float.isNaN(st.offset) ? st.now : st.tl.time(st.offset); }

    /** Moves so that t is at the left edge (smoothly when animate). */
    void scrollToTime(long t, boolean animate) {
        if (st.tl == null) return;
        if (viewport() <= 0) {
            pending = t;
            return;
        }
        float target = st.tl.clamp(st.tl.x(t), viewport());
        scroller.forceFinished(true);
        removeCallbacks(fling);
        if (animate && !Float.isNaN(st.offset)) {
            scroller.startScroll(Math.round(st.offset), 0, Math.round(target - st.offset), 0, 450);
            postOnAnimation(fling);
        } else {
            setOffset(target);
        }
    }

    /** Pure: the time at the left edge that puts now just after the logos' column (into, px from the edge). */
    static long leftForNow(long now, float pxPerMs, float into) {
        return now - (long) (into / Math.max(1e-9f, pxPerMs));
    }

    /** Now just after the logos' column, with room before it for what has been shown of each programme. */
    void scrollToNow(boolean animate) {
        if (st.tl == null) return;
        float perMs = (st.tl.x(st.now + 3_600_000L) - st.tl.x(st.now)) / 3_600_000f;
        scrollToTime(leftForNow(System.currentTimeMillis(), perMs, colW + Ui.dp(getContext(), 64)), animate);
    }

    /** The programme picked out in light blue (start 0: the channel only). */
    void select(String key, long start) {
        boolean channelChanged = key == null ? st.selKey != null : !key.equals(st.selKey);
        st.selKey = key;
        st.selStart = start;
        if (channelChanged) adapter.notifyItemRangeChanged(0, adapter.getItemCount(), SELECTION);
        invalidateAll();
    }

    /** The minute moved on, or the favourites changed: the now line and the stars again. */
    void tick() {
        st.now = System.currentTimeMillis();
        invalidateAll();
    }

    void favouritesChanged() {
        adapter.notifyItemRangeChanged(0, adapter.getItemCount(), SELECTION);
    }

    /**
     * The width the time runs across: the whole row, edge to edge of the screen (the logo's tile lies over its start;
     * the programmes run on past the right edge, as the screen is filled).
     */
    private float viewport() {
        return getWidth();
    }

    private void setOffset(float o) {
        if (st.tl == null) return;
        float v = st.tl.clamp(o, Math.max(1, viewport()));
        if (v == st.offset) return;
        st.offset = v;
        invalidateAll();
        long left = st.tl.time(v);
        long day = Math.floorDiv(left + st.tz.getOffset(left), Timeline.DAY);
        if (day != shownDay) {
            shownDay = day;
            if (listener != null) listener.onDay(left);
        }
    }

    private void invalidateAll() {
        ruler.invalidate();
        for (int i = 0; i < rows.getChildCount(); i++) {
            RecyclerView.ViewHolder h = rows.getChildViewHolder(rows.getChildAt(i));
            if (h instanceof Row) ((Row) h).blocks.invalidate();
        }
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (pending >= 0 && viewport() > 0) {
            long t = pending;
            pending = -1;
            post(() -> scrollToTime(t, false));
        } else if (st.tl != null && !Float.isNaN(st.offset)) {
            setOffset(st.offset);
        }
    }

    // ---- sideways scrolling ----------------------------------------------------------------------------------

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                dragging = false;
                vertical = false;
                if (tracker == null) tracker = VelocityTracker.obtain();
                else tracker.clear();
                tracker.addMovement(e);
                if (!scroller.isFinished()) {
                    // A touch stops a fling (and isn't a tap on a programme).
                    scroller.forceFinished(true);
                    removeCallbacks(fling);
                    dragging = true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (tracker != null) tracker.addMovement(e);
                if (startsDrag(e)) return true;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                break;
            default:
                break;
        }
        return false;
    }

    /** A drag that has gone sideways further than up or down: the grid's from here on. */
    private boolean startsDrag(MotionEvent e) {
        if (dragging) return true;
        if (vertical) return false;
        float dx = e.getX() - downX, dy = e.getY() - downY;
        if (Math.abs(dy) > slop && Math.abs(dy) >= Math.abs(dx)) {
            vertical = true;
            return false;
        }
        if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
            dragging = true;
            lastX = e.getX();
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (tracker == null) tracker = VelocityTracker.obtain();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                vertical = false;
                tracker.clear();
                tracker.addMovement(e);
                return true;
            case MotionEvent.ACTION_MOVE:
                tracker.addMovement(e);
                if (!dragging && !startsDrag(e)) return true;
                if (dragging) {
                    float dx = e.getX() - lastX;
                    lastX = e.getX();
                    setOffset((Float.isNaN(st.offset) ? 0 : st.offset) - dx);
                }
                return true;
            case MotionEvent.ACTION_UP:
                tracker.addMovement(e);
                if (dragging && st.tl != null) {
                    tracker.computeCurrentVelocity(1000, maxFling);
                    float vx = tracker.getXVelocity();
                    if (Math.abs(vx) > minFling) {
                        int max = Math.round(Math.max(0, st.tl.width() - viewport()));
                        scroller.fling(Math.round(st.offset), 0, Math.round(-vx), 0, 0, max, 0, 0);
                        postOnAnimation(fling);
                    }
                }
                dragging = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
            default:
                return true;
        }
    }

    /** Every row drawn again as programmes' pictures are found (ProgArt). */
    private final Runnable artFound = this::invalidateAll;

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ProgArt.listen(artFound);
    }

    @Override protected void onDetachedFromWindow() {
        ProgArt.unlisten(artFound);
        removeCallbacks(fling);
        if (tracker != null) {
            tracker.recycle();
            tracker = null;
        }
        super.onDetachedFromWindow();
    }

    // ---- drawing ---------------------------------------------------------------------------------------------

    /** What every row and the ruler draw from: the time across, the offset, now, the selection, and the paints. */
    static final class State {
        Timeline tl;
        float offset;
        long now = System.currentTimeMillis();
        String selKey;
        long selStart;
        /** The logo's tile over each row's start: the writing starts after it. */
        float tileW;
        final TimeZone tz = TimeZone.getDefault();
        final Paint block = fill(TvLook.GLASS), onNow = fill(TvLook.GLASS_ON), chosen = fill(0x24FFFFFF), ring = fill(Ui.TEXT),
                badge = fill(TvLook.L3), nowMark = fill(TvLook.AMBER), rim = fill(TvLook.RIM), hair = fill(TvLook.TOP),
                track = fill(TvLook.TRACK), done = fill(TvLook.L1), pressed = fill(0x1AFFFFFF);
        final Paint title, titleInk, small, smallName, badgeText, ruler, rulerNow;
        final RectF rect = new RectF(), tag = new RectF(), bar = new RectF(), logoBox = new RectF();
        final char[] buf = new char[24];
        final float gap, radius, pad, minText, one, ringW, badgePad, badgeR, lineH, lineIn, lift, dot;
        /** The programmes' pictures: drawn (PicDraw), the plain and dimming washes, the logo's paint. */
        final PicDraw art;
        final Paint plain = fill(0), wash = fill(0), logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        /** A card narrower than picMin takes no picture (its colour); none is made wider than cap (the screen). */
        final float picMin, cap;

        State(Context c, boolean wide) {
            art = new PicDraw(c);
            picMin = Ui.dp(c, 40);
            cap = Math.max(c.getResources().getDisplayMetrics().widthPixels, c.getResources().getDisplayMetrics().heightPixels);
            // Rounded blocks a little apart, each in a deep colour of its own, as cards in a row.
            gap = Ui.dp(c, 3);
            radius = Ui.dp(c, 11);
            one = Math.max(1, Ui.dp(c, 1));
            ringW = Ui.dp(c, 2);
            lineH = Math.max(2, Ui.dp(c, 3));
            lineIn = Ui.dp(c, 10);
            lift = Ui.dp(c, 1.5f);
            dot = Ui.dp(c, 3.5f);
            pad = Ui.dp(c, 12);
            // A card cut too narrow by the column or the edge keeps no writing (no lone "N…").
            minText = Ui.dp(c, 34);
            badgePad = Ui.dp(c, 4);
            badgeR = Ui.dp(c, 4);
            // Inter: the titles semibold in white, the times and names regular in 07's softer greys.
            Typeface regular = TvLook.inter(c), semi = TvLook.semi(c);
            title = text(c, wide ? 16.5f : 15.5f, Ui.TEXT, semi);
            titleInk = text(c, wide ? 16.5f : 15.5f, Ui.TEXT, semi);
            small = text(c, wide ? 13 : 12, TvLook.L3, regular);
            smallName = text(c, wide ? 13 : 12, TvLook.L2, semi);
            badgeText = text(c, wide ? 9.5f : 9f, TvLook.L2, semi);
            ruler = text(c, wide ? 15 : 14, TvLook.L3, regular);
            rulerNow = text(c, wide ? 15 : 14, Ui.TEXT, semi);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(ringW);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(one);
            hair.setStyle(Paint.Style.STROKE);
            hair.setStrokeWidth(one);
            badge.setStyle(Paint.Style.STROKE);
            badge.setStrokeWidth(one);
        }

        private static Paint fill(int color) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(color);
            return p;
        }

        private static Paint text(Context c, float sp, int color, Typeface tf) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, c.getResources().getDisplayMetrics()));
            p.setColor(color);
            p.setTypeface(tf);
            return p;
        }

        /** "09:30" for t into buf at at (the 24-hour clock); the index after it. No allocation (it runs while drawing). */
        int clock(long t, int at) {
            long mins = Math.floorMod(Math.floorDiv(t + tz.getOffset(t), 60_000L), 1440L);
            int h = (int) (mins / 60), m = (int) (mins % 60);
            buf[at++] = (char) ('0' + h / 10);
            buf[at++] = (char) ('0' + h % 10);
            buf[at++] = ':';
            buf[at++] = (char) ('0' + m / 10);
            buf[at++] = (char) ('0' + m % 10);
            return at;
        }

        /** Writes s from x on the baseline y, cut with an ellipsis to fit max; the width written. */
        float fit(Canvas cv, String s, float x, float y, float max, Paint p) {
            int n = s.length();
            if (n == 0 || max <= 0) return 0;
            int fits = p.breakText(s, true, max, null);
            if (fits >= n) {
                cv.drawText(s, x, y, p);
                return p.measureText(s);
            }
            float ell = p.measureText("…");
            int cut = p.breakText(s, true, Math.max(0, max - ell), null);
            if (cut <= 0) return 0;
            cv.drawText(s, 0, cut, x, y, p);
            float w = p.measureText(s, 0, cut);
            cv.drawText("…", x + w, y, p);
            return w + ell;
        }
    }

    /** One channel's programmes along its row, the logo's tile lying over its start. */
    @SuppressLint("ViewConstructor")
    static final class Blocks extends View {
        private final State st;
        private final GestureDetector touch;
        Guide.Schedule schedule;
        String key = "";
        String noGuide = "";
        /** The channel's name as written over the first title, and its picture's tag (HD, 4K…). */
        String name = "", quality = "";
        Channel ch;
        Listener listener;
        private float topY, titleY, tapX = -1;
        /** Where a finger is pressing (the card under it lifts), or below 0. */
        private float pressX = -1;
        /** Drawn again as a picture is made (ProgPics). */
        private final Runnable redraw = this::invalidate;

        Blocks(Context c, State st) {
            super(c);
            this.st = st;
            touch = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(@NonNull MotionEvent e) { return true; }

                @Override public void onShowPress(@NonNull MotionEvent e) {
                    press(e.getX());
                }

                @Override public boolean onSingleTapUp(@NonNull MotionEvent e) {
                    tapX = e.getX();
                    performClick();
                    return true;
                }

                @Override public void onLongPress(@NonNull MotionEvent e) {
                    if (ch != null && listener != null) {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        listener.onMenu(ch);
                    }
                }
            });
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            topY = h * 0.37f;
            titleY = h * 0.71f;
        }

        /** The card under a finger lifts (x below 0: none). */
        private void press(float x) {
            if (x == pressX) return;
            pressX = x;
            invalidate();
        }

        private void tapAt(float x) {
            if (ch == null || listener == null || st.tl == null) return;
            long t = st.tl.time(st.offset + x);
            int i = schedule == null ? -1 : schedule.at(t);
            listener.onProgramme(ch, i);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) press(-1);
            return touch.onTouchEvent(e) || super.onTouchEvent(e);
        }

        /** A tap where it landed; TalkBack's double tap (no touch) as a tap on what is on now. */
        @Override public boolean performClick() {
            super.performClick();
            float x = tapX >= 0 || st.tl == null ? tapX : st.tl.x(st.now) - st.offset;
            tapX = -1;
            tapAt(x);
            return true;
        }

        @Override protected void onDraw(@NonNull Canvas cv) {
            Timeline tl = st.tl;
            if (tl == null || Float.isNaN(st.offset)) return;
            float w = getWidth(), h = getHeight(), off = st.offset;
            long tL = tl.time(off), tR = tl.time(off + w);
            boolean mine = key.equals(st.selKey);
            Guide.Schedule s = schedule;
            named = false;
            if (s == null || s.start.length == 0) {
                // No guide: one card of glass after the logo's tile.
                boolean sel = mine && st.selStart == 0;
                card(cv, st.tileW + st.gap, w, h, false, sel, pressX >= 0);
                writing(cv, st.tileW + st.gap + st.pad, w - st.pad, -1, noGuide, 0);
                return;
            }
            int n = s.start.length;
            for (int i = Timeline.first(s, tL); i < n && s.start[i] < tR; i++) {
                float l = tl.x(s.start[i]) - off, r = tl.x(s.stop[i]) - off;
                if (r <= 0) continue;
                block(cv, s, i, l, r, h, w, mine);
            }
        }

        /** Whether this row's name has been written yet (over its first title in sight). */
        private boolean named;

        private void inset(RectF r) {
            float k = st.ringW / 2;
            r.inset(k, k);
        }

        private void block(Canvas cv, Guide.Schedule s, int i, float l, float r, float h, float w, boolean mine) {
            // The cards start after the logo's tile (one that began earlier is cut there, as 07's grid has it).
            float left = Math.max(l + st.gap, st.tileW + st.gap), right = r - st.gap;
            if (right - left < 1 || right < 0 || left > w) return;
            long now = st.now;
            boolean on = s.start[i] <= now && now < s.stop[i];
            boolean sel = mine && s.start[i] == st.selStart;
            boolean pressed = pressX >= 0 && pressX >= l && pressX < r;
            // The programme's picture filling the card (Homefield TV's cells), the words over a shade at its left; the
            // glass card as before while it is looked for or read.
            float tx = left + st.pad, end = right - st.pad;
            String title = s.title[i].isEmpty() ? "—" : s.title[i];
            float wordsEnd = end - tx >= st.minText ? words(tx, end, s.start[i], title) + st.pad * 0.6f : left;
            float k = sel || pressed ? 0 : st.lift;
            st.rect.set(left + k, k, right - k, h - k);
            boolean past = s.stop[i] <= now;
            if (picture(cv, s, i, r - l - 2 * st.gap, h, on, sel, past, wordsEnd)) edges(cv, sel, pressed);
            else card(cv, left, right, h, on, sel, pressed);
            if (on) {
                // How far in it is: a white line on a soft track along the card's foot.
                float y = h - k - st.lineIn / 2 - st.lineH;
                float x0 = left + k + st.lineIn, x1 = right - k - st.lineIn;
                if (x1 - x0 > st.lineIn) {
                    float r2 = st.lineH / 2;
                    st.bar.set(x0, y, x1, y + st.lineH);
                    cv.drawRoundRect(st.bar, r2, r2, st.track);
                    st.bar.right = x0 + (x1 - x0) * TvLook.progress(s.start[i], s.stop[i], now);
                    if (st.bar.right - x0 > 1) cv.drawRoundRect(st.bar, r2, r2, st.done);
                }
            }
            // The writing starts inside the card and stays in sight while the card runs on.
            if (end - tx < st.minText) return;
            writing(cv, tx, end, s.start[i], title, sel ? 1 : 0);
        }

        /**
         * The programme's picture filling st.rect (full: the whole programme's card width, the size it is made at),
         * dimmer when it isn't on (more so once shown; the one chosen as on), the shade under the words; with none, the
         * channel's own picture: its logo on its colour. False while it is looked for or read (the glass card then).
         */
        private boolean picture(Canvas cv, Guide.Schedule s, int i, float full, float h, boolean on, boolean sel, boolean past,
                                float wordsEnd) {
            Context c = getContext();
            RectF rc = st.rect;
            String url = ProgArt.of(c, ch, s, i);
            if (url == null) return false;
            if (url.isEmpty()) return logoCard(cv, on, sel, past, wordsEnd);
            if (rc.width() < st.picMin) {
                // Too small for a picture: its own colour, clean (once it has been read for a larger cell).
                int col = ProgPics.colourOf(url);
                if (col == 0) return false;
                plain(cv, col, on, sel, past);
                return true;
            }
            int fw = Math.round(Math.min(st.cap, Math.max(rc.width(), full))), fh = Math.round(h);
            ProgPics.Made m = ProgPics.get(c, url, fw, fh, ProgPics.FILL, redraw);
            if (m == null) return false;
            if (m == ProgPics.NONE) return logoCard(cv, on, sel, past, wordsEnd);
            if (m.b != null && st.art.place(rc, m, wordsEnd, st.pad)) {
                st.art.fill(cv, rc, st.radius, m);
            } else if (!st.art.ground(cv, rc, st.radius, m)) {
                // Its faces would be cut at this size, or under the words: its own colours, never half a face.
                plain(cv, m.colour, on, sel, past);
                return true;
            }
            wash(cv, on, sel, past);
            st.art.shade(cv, rc, st.radius, wordsEnd, on);
            return true;
        }

        /** A card in one colour (a picture's own, too small for it). */
        private void plain(Canvas cv, int colour, boolean on, boolean sel, boolean past) {
            st.plain.setColor(colour | 0xFF000000);
            cv.drawRoundRect(st.rect, st.radius, st.radius, st.plain);
            wash(cv, on, sel, past);
        }

        /** Dimmer when it isn't on now (or chosen), more so once it has been shown: what's on keeps the brighter look. */
        private void wash(Canvas cv, boolean on, boolean sel, boolean past) {
            if (on || sel) return;
            st.wash.setColor(past ? 0x7A000000 : 0x40000000);
            cv.drawRoundRect(st.rect, st.radius, st.radius, st.wash);
        }

        /**
         * The channel's own picture for a programme with none: the card in its logo's colour (as the logo's tile, Behind
         * logos), the logo after the words where there is room; false with Behind logos set to None (the glass then).
         */
        private boolean logoCard(Canvas cv, boolean on, boolean sel, boolean past, float wordsEnd) {
            if (ch == null) return false;
            Context c = getContext();
            String logo = ch.logo;
            int top, foot;
            if (logo.isEmpty()) {
                top = foot = GuideLook.nameColour(ch.name);
            } else {
                int[] cols = LogoTiles.now(c, logo);
                top = cols[0];
                foot = cols[1];
            }
            if (top == 0 && foot == 0) return false;
            RectF rc = st.rect;
            st.plain.setColor(top | 0xFF000000);
            cv.drawRoundRect(rc, st.radius, st.radius, st.plain);
            if (foot != top) {
                // Its foot in the tile's lower colour, softly: the tile's gradient without a shader per card.
                st.wash.setColor(foot & 0x00FFFFFF | 0x80000000);
                float mid = rc.top + rc.height() * 0.55f;
                cv.save();
                cv.clipRect(rc.left, mid, rc.right, rc.bottom);
                cv.drawRoundRect(rc, st.radius, st.radius, st.wash);
                cv.restore();
            }
            float lh = rc.height() * 0.5f, lw = lh * 2.2f, free = rc.right - Math.max(rc.left, wordsEnd) - st.pad;
            if (!logo.isEmpty() && free >= lw + st.pad) {
                ProgPics.Made m = ProgPics.get(c, logo, Math.round(lw), Math.round(lh), ProgPics.LOGO, redraw);
                if (m != null && m.b != null) {
                    float bw = m.b.getWidth(), bh = m.b.getHeight();
                    float cx = Math.max(rc.left, wordsEnd) + free / 2f + st.pad / 2f, cy = rc.centerY();
                    st.logoBox.set(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f);
                    st.logoPaint.setAlpha(on || sel ? 235 : 190);
                    cv.drawBitmap(m.b, null, st.logoBox, st.logoPaint);
                }
            }
            wash(cv, on, sel, past);
            return true;
        }

        /** Over a picture: pressed, its wash; its rim and the bright hairline along its top; chosen, the white ring. */
        private void edges(Canvas cv, boolean sel, boolean pressed) {
            float rad = st.radius;
            if (pressed) cv.drawRoundRect(st.rect, rad, rad, st.pressed);
            float half = st.one / 2;
            st.rect.inset(half, half);
            cv.drawRoundRect(st.rect, rad, rad, st.rim);
            if (st.rect.width() > rad * 2) cv.drawLine(st.rect.left + rad, st.rect.top, st.rect.right - rad, st.rect.top, st.hair);
            st.rect.inset(-half, -half);
            if (sel) {
                inset(st.rect);
                cv.drawRoundRect(st.rect, rad, rad, st.ring);
                st.rect.inset(-st.ringW / 2, -st.ringW / 2);
            }
        }

        /** Where the block's words end (as writing() lays them out), to shade under them. Nothing made. */
        private float words(float tx, float end, long start, String title) {
            float x = tx, max = end - tx;
            if (!named) {
                float wn = Math.min(st.smallName.measureText(name), max * 0.7f);
                x += wn;
                if (!quality.isEmpty() && wn > 0) {
                    float bw = st.badgeText.measureText(quality) + 2 * st.badgePad, bx = x + st.badgePad * 1.5f;
                    if (bx + bw < end) x = bx + bw;
                }
                x += st.pad * 0.8f;
            }
            if (start >= 0) {
                int len = st.clock(start, 0);
                float tw = st.small.measureText(st.buf, 0, len);
                if (x + tw <= end) x += tw;
            }
            float t = tx + Math.min(max, st.title.measureText(title));
            return Math.min(end, Math.max(x, t));
        }

        /**
         * A card of frosted glass from left to right: what's on now a little brighter, its rim and the bright hairline
         * along its top; chosen (ringed in white) or pressed, it lifts to fill its place (at rest it sits a little in).
         */
        private void card(Canvas cv, float left, float right, float h, boolean on, boolean sel, boolean pressed) {
            float k = sel || pressed ? 0 : st.lift;
            st.rect.set(left + k, k, right - k, h - k);
            float rad = st.radius;
            cv.drawRoundRect(st.rect, rad, rad, on ? st.onNow : st.block);
            if (pressed) cv.drawRoundRect(st.rect, rad, rad, st.pressed);
            if (sel) cv.drawRoundRect(st.rect, rad, rad, st.chosen);
            float half = st.one / 2;
            st.rect.inset(half, half);
            cv.drawRoundRect(st.rect, rad, rad, st.rim);
            if (st.rect.width() > rad * 2) cv.drawLine(st.rect.left + rad, st.rect.top, st.rect.right - rad, st.rect.top, st.hair);
            st.rect.inset(-half, -half);
            if (sel) {
                inset(st.rect);
                cv.drawRoundRect(st.rect, rad, rad, st.ring);
            }
        }

        /**
         * The block's two lines: over the title the channel's name with its tag (the first block in sight only) and
         * the start time (start below 0: none), the title under them.
         */
        private void writing(Canvas cv, float tx, float end, long start, String title, int ink) {
            float x = tx, max = end - tx;
            if (max < st.minText) return;
            if (!named) {
                named = true;
                float wn = st.fit(cv, name, x, topY, max * 0.7f, st.smallName);
                x += wn;
                if (!quality.isEmpty() && wn > 0) {
                    float tw = st.badgeText.measureText(quality), bw = tw + 2 * st.badgePad;
                    float bx = x + st.badgePad * 1.5f;
                    if (bx + bw < end) {
                        float th = st.badgeText.getTextSize();
                        st.tag.set(bx, topY - th - st.badgePad * 0.6f, bx + bw, topY + st.badgePad * 0.6f);
                        cv.drawRoundRect(st.tag, st.badgeR, st.badgeR, st.badge);
                        cv.drawText(quality, bx + st.badgePad, topY - st.badgePad * 0.2f, st.badgeText);
                        x = bx + bw;
                    }
                }
                x += st.pad * 0.8f;
            }
            if (start >= 0) {
                int len = st.clock(start, 0);
                if (x + st.small.measureText(st.buf, 0, len) <= end) cv.drawText(st.buf, 0, len, x, topY, st.small);
            }
            st.fit(cv, title, tx, titleY, max, ink == 1 ? st.titleInk : st.title);
        }
    }

    /** The times across the top in grey, and now as an amber dot and its time in white. */
    private final class Ruler extends View {
        private final float markW, gapAfter, room;

        Ruler(Context c) {
            super(c);
            markW = Ui.dp(c, 9);
            gapAfter = Ui.dp(c, 3);
            room = Ui.dp(c, 64);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(@NonNull Canvas cv) {
            Timeline tl = st.tl;
            if (tl == null || Float.isNaN(st.offset)) return;
            float w = getWidth(), h = getHeight(), off = st.offset, base = h * 0.66f;
            // Nothing under the day's name: the times start after the logos' column (the rows start at the screen's edge).
            float from = colW + Ui.dp(getContext(), 24);
            float nx = tl.x(st.now) - off;
            boolean nowSeen = nx >= from - markW && nx <= w;
            long tL = tl.time(off - room), tR = tl.time(off + w);
            for (long t = Timeline.ceilHalfHour(tL, st.tz); t <= tR; t += Timeline.HALF_HOUR) {
                float x = tl.x(t) - off;
                if (x < from) continue;
                if (nowSeen && Math.abs(x - nx) < room * 1.2f) continue;
                int len = st.clock(t, 0);
                cv.drawText(st.buf, 0, len, x, base, st.ruler);
            }
            if (nowSeen) {
                // Now: 07's amber dot under the time, glowing a little, and the time in white beside it.
                float mx = Math.max(from - markW, nx - markW / 2), cx = mx + markW / 2, cy = h - st.dot * 2.2f;
                st.nowMark.setAlpha(0x40);
                cv.drawCircle(cx, cy, st.dot * 2.1f, st.nowMark);
                st.nowMark.setAlpha(0xFF);
                cv.drawCircle(cx, cy, st.dot, st.nowMark);
                int len = st.clock(st.now, 0);
                cv.drawText(st.buf, 0, len, mx + markW + gapAfter, base, st.rulerNow);
            }
        }
    }

    // ---- the rows ----------------------------------------------------------------------------------------------

    private final class Rows extends RecyclerView.Adapter<Row> {
        @Override public int getItemCount() { return channels.size(); }

        @NonNull @Override public Row onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new Row(parent.getContext());
        }

        @Override public void onBindViewHolder(@NonNull Row h, int position) {
            h.bind(channels.get(position));
        }

        @Override public void onBindViewHolder(@NonNull Row h, int position, @NonNull List<Object> payloads) {
            if (payloads.contains(SELECTION) && h.ch == channels.get(position)) h.marks();
            else onBindViewHolder(h, position);
        }
    }

    private final class Row extends RecyclerView.ViewHolder {
        final FrameLayout tile;
        final TextView initials;
        final ImageView logo, star;
        final Blocks blocks;
        final android.graphics.drawable.GradientDrawable ground;
        Channel ch;

        Row(Context c) {
            super(new FrameLayout(c));
            FrameLayout item = (FrameLayout) itemView;
            item.setLayoutParams(new RecyclerView.LayoutParams(-1, rowH));
            // No inset at either side: the logos' column starts at the screen's left edge (5.16) and the programmes run to
            // its right edge (5.15), with no black strip at either.
            item.setPadding(0, 0, 0, Ui.dp(c, 8));
            blocks = new Blocks(c, st);
            blocks.noGuide = c.getString(R.string.live_no_guide_info);
            item.addView(blocks, new FrameLayout.LayoutParams(-1, -1));
            // The logo's tile at the row's start, raised a little off the ground (07's colour tile: the logo's own colour,
            // a soft gradient with its light top edge), or as it is, or black (LogoTiles).
            tile = new FrameLayout(c);
            ground = new android.graphics.drawable.GradientDrawable();
            ground.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM);
            ground.setCornerRadius(st.radius);
            tile.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), ground, null));
            tile.setClipToOutline(true);
            tile.setElevation(Ui.dp(c, 3));
            tile.setForeground(new TvLook.Pane(c, st.radius / c.getResources().getDisplayMetrics().density, 0, 0, true));
            initials = Ui.text(c, "", wide ? 18 : 16, Ui.TEXT, true);
            initials.setGravity(Gravity.CENTER);
            tile.addView(initials, new FrameLayout.LayoutParams(-1, -1));
            logo = new ImageView(c);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int lp = Ui.dp(c, 8);
            logo.setPadding(lp, lp, lp, lp);
            tile.addView(logo, new FrameLayout.LayoutParams(-1, -1));
            // A favourite: a small star at the tile's top right.
            star = new ImageView(c);
            star.setImageResource(R.drawable.live_ic_star);
            star.setImageTintList(ColorStateList.valueOf(Ui.STAR));
            star.setBackground(Ui.round(c, 0x99000000, 10));
            int sp = Ui.dp(c, 3);
            star.setPadding(sp, sp, sp, sp);
            star.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams stp = new FrameLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20), Gravity.TOP | Gravity.END);
            stp.topMargin = stp.rightMargin = Ui.dp(c, 4);
            tile.addView(star, stp);
            tile.setOnClickListener(v -> {
                if (ch != null && listener != null) listener.onChannel(ch);
            });
            tile.setOnLongClickListener(v -> {
                if (ch != null && listener != null) {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    listener.onMenu(ch);
                }
                return true;
            });
            // In from the screen's edge by a little, so its corners and its shadow show on the ground.
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(colW - tileIn, -1);
            tp.leftMargin = tileIn;
            item.addView(tile, tp);
        }

        void bind(Channel c) {
            ch = c;
            blocks.ch = c;
            blocks.key = c.key;
            blocks.listener = listener;
            blocks.schedule = lib.schedule(c.key);
            blocks.name = GuideLook.shortName(c.name);
            blocks.quality = GuideLook.quality(c.name);
            initials.setText(Ui.initials(c.name));
            String url = c.logo;
            Context ctx = itemView.getContext();
            if (url.isEmpty()) {
                // No logo: its initials on a tile in a colour of its own.
                paint(GuideLook.nameColour(c.name), GuideLook.nameColour(c.name));
                Glide.with(logo).clear(logo);
                initials.setVisibility(View.VISIBLE);
            } else {
                // Behind logos (LogoTiles): the tile in the logo's own colour (the default, worked out once per logo); the
                // logo as it is on the guide's own black (None: the programmes run on under the column, so it is the
                // guide's ground, not a tile), a soft halo behind a dark one; or a black tile.
                int lp = Ui.dp(ctx, 8);
                logo.setPadding(lp, lp, lp, lp);
                initials.setVisibility(View.INVISIBLE);
                LogoTiles.load(logo, url, GuideLook.GROUND, cols -> {
                    if (ch != c) return;
                    // None: the logo as it is on a faint glass tile (the ground is no longer black).
                    if (cols[0] == 0 && cols[1] == 0) paint(0x1FFFFFFF, 0x0FFFFFFF);
                    else paint(cols[0], cols[1]);
                }, () -> {
                    if (ch != c) return;
                    initials.setVisibility(View.VISIBLE);
                    paint(GuideLook.nameColour(c.name), GuideLook.nameColour(c.name));
                });
            }
            marks();
            Guide.Schedule s = blocks.schedule;
            int at = s == null ? -1 : s.at(st.now);
            tile.setContentDescription(c.name + (at >= 0 ? ", " + s.title[at] : ""));
            blocks.setContentDescription(at >= 0 ? s.title[at] + ", " + Ui.range(s.start[at], s.stop[at]) : blocks.noGuide);
            blocks.invalidate();
        }

        /** The tile's colours, top to foot (always a gradient: a solid colour after one would keep the gradient's paint). */
        private void paint(int top, int foot) {
            ground.setColors(new int[]{top, foot});
        }

        /** The favourite's star, and the selection drawn again. */
        void marks() {
            if (ch == null) return;
            star.setVisibility(Picks.isFavourite(itemView.getContext(), ch.key) ? View.VISIBLE : View.GONE);
            blocks.invalidate();
        }
    }

}
