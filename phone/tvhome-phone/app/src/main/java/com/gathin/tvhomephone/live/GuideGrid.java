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
 * The TV guide's grid, dark (GuideLook): the day big at the top left and the times across with a white mark at now;
 * each channel a row of rounded cards, its logo on a tile in the logo's own colour over the row's start (a star on a
 * favourite), and its programmes as rounded blocks each in a deep colour of its own, what has been shown a shade
 * darker, the channel's name with its picture's tag and each start time over the titles. One RecyclerView of rows,
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
        st.tileW = colW;
        rowH = Ui.dp(c, wide ? 70 : 62);
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity();
        maxFling = vc.getScaledMaximumFlingVelocity();
        scroller = new OverScroller(c);
        setBackgroundColor(GuideLook.GROUND);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        // The head: the day ("Today") big at the left, the times across with a white mark at now.
        FrameLayout head = new FrameLayout(c);
        ruler = new Ruler(c);
        head.addView(ruler, new FrameLayout.LayoutParams(-1, -1));
        corner = Ui.text(c, "", wide ? 26 : 24, Ui.TEXT, true);
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

    /** The width the time runs across: the whole row (the logo's tile lies over its start). */
    private float viewport() {
        return getWidth() - 2f * margin;
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

    @Override protected void onDetachedFromWindow() {
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
        final Paint block = fill(0), past = fill(GuideLook.PAST), chosen = fill(0x2EFFFFFF), ring = fill(Ui.TEXT),
                badge = fill(GuideLook.SMALL), nowMark = fill(Ui.TEXT);
        final Paint title, titleInk, small, smallName, badgeText, ruler, rulerNow;
        final RectF rect = new RectF(), tag = new RectF();
        final android.graphics.Path tri = new android.graphics.Path();
        final char[] buf = new char[24];
        final float gap, radius, pad, minText, one, ringW, badgePad, badgeR;

        State(Context c, boolean wide) {
            // Rounded blocks a little apart, each in a deep colour of its own, as cards in a row.
            gap = Ui.dp(c, 3);
            radius = Ui.dp(c, 11);
            one = Math.max(1, Ui.dp(c, 1));
            ringW = Ui.dp(c, 2);
            pad = Ui.dp(c, 12);
            minText = Ui.dp(c, 18);
            badgePad = Ui.dp(c, 4);
            badgeR = Ui.dp(c, 4);
            Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            title = text(c, wide ? 18 : 17, Ui.TEXT, Typeface.DEFAULT);
            titleInk = text(c, wide ? 18 : 17, Ui.TEXT, medium);
            small = text(c, wide ? 14 : 13, GuideLook.SMALL, Typeface.DEFAULT);
            smallName = text(c, wide ? 14 : 13, GuideLook.SMALL, medium);
            badgeText = text(c, wide ? 10 : 9.5f, GuideLook.SMALL, medium);
            ruler = text(c, wide ? 18 : 17, GuideLook.RULER, Typeface.DEFAULT);
            rulerNow = text(c, wide ? 18 : 17, Ui.TEXT, Typeface.DEFAULT);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(ringW);
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

        Blocks(Context c, State st) {
            super(c);
            this.st = st;
            touch = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(@NonNull MotionEvent e) { return true; }

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
            topY = h * 0.40f;
            titleY = h * 0.78f;
        }

        private void tapAt(float x) {
            if (ch == null || listener == null || st.tl == null) return;
            long t = st.tl.time(st.offset + x);
            int i = schedule == null ? -1 : schedule.at(t);
            listener.onProgramme(ch, i);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent e) {
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
                st.rect.set(0, 0, w, h);
                st.block.setColor(GuideLook.blockColour(name));
                cv.drawRoundRect(st.rect, st.radius, st.radius, st.block);
                if (mine && st.selStart == 0) {
                    cv.drawRoundRect(st.rect, st.radius, st.radius, st.chosen);
                    inset(st.rect);
                    cv.drawRoundRect(st.rect, st.radius, st.radius, st.ring);
                }
                writing(cv, st.tileW + st.pad, w - st.pad, -1, noGuide, 0);
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
            float left = l + st.gap, right = r - st.gap;
            if (right - left < 1 || right < 0 || left > w) return;
            long now = st.now;
            boolean on = s.start[i] <= now && now < s.stop[i];
            boolean sel = mine && s.start[i] == st.selStart;
            st.rect.set(left, 0, right, h);
            st.block.setColor(GuideLook.blockColour(s.title[i]));
            cv.drawRoundRect(st.rect, st.radius, st.radius, st.block);
            if (on) {
                // What has been shown already, a shade darker.
                float px = Math.min(st.tl.x(now) - st.offset, right);
                if (px > left) {
                    cv.save();
                    cv.clipRect(left, 0, px, h);
                    cv.drawRoundRect(st.rect, st.radius, st.radius, st.past);
                    cv.restore();
                }
            }
            if (sel) {
                cv.drawRoundRect(st.rect, st.radius, st.radius, st.chosen);
                inset(st.rect);
                cv.drawRoundRect(st.rect, st.radius, st.radius, st.ring);
            }
            // The writing starts after the logo's tile and stays in sight while the block runs on under it.
            float tx = Math.max(left, st.tileW) + st.pad, end = right - st.pad;
            if (end - tx < st.minText) return;
            writing(cv, tx, end, s.start[i], s.title[i].isEmpty() ? "—" : s.title[i], sel ? 1 : 0);
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

    /** The times across the top in grey, and now as a white mark and its time. */
    private final class Ruler extends View {
        private final float markW, markH, gapAfter, room;

        Ruler(Context c) {
            super(c);
            markW = Ui.dp(c, 9);
            markH = Ui.dp(c, 7);
            gapAfter = Ui.dp(c, 3);
            room = Ui.dp(c, 64);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(@NonNull Canvas cv) {
            Timeline tl = st.tl;
            if (tl == null || Float.isNaN(st.offset)) return;
            float w = getWidth(), h = getHeight(), off = st.offset, base = h * 0.66f;
            // Nothing under the day's name: the times start after the logos' column.
            float from = margin + colW + Ui.dp(getContext(), 24);
            float nx = margin + tl.x(st.now) - off;
            boolean nowSeen = nx >= from - markW && nx <= w;
            long tL = tl.time(off - room), tR = tl.time(off + w);
            for (long t = Timeline.ceilHalfHour(tL, st.tz); t <= tR; t += Timeline.HALF_HOUR) {
                float x = margin + tl.x(t) - off;
                if (x < from) continue;
                if (nowSeen && Math.abs(x - nx) < room * 1.2f) continue;
                int len = st.clock(t, 0);
                cv.drawText(st.buf, 0, len, x, base, st.ruler);
            }
            if (nowSeen) {
                float mx = Math.max(from - markW, nx - markW / 2);
                st.tri.reset();
                st.tri.moveTo(mx, base - st.rulerNow.getTextSize() * 0.62f);
                st.tri.lineTo(mx + markW, base - st.rulerNow.getTextSize() * 0.62f);
                st.tri.lineTo(mx + markW / 2, base - st.rulerNow.getTextSize() * 0.62f + markH);
                st.tri.close();
                cv.drawPath(st.tri, st.nowMark);
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

    /** Each logo's tile colour once worked out, by the logo's address. */
    private static final android.util.LruCache<String, Integer> tiles = new android.util.LruCache<>(600);

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
            item.setPadding(margin, 0, margin, Ui.dp(c, 8));
            blocks = new Blocks(c, st);
            blocks.noGuide = c.getString(R.string.live_no_guide_info);
            item.addView(blocks, new FrameLayout.LayoutParams(-1, -1));
            // The logo's tile over the row's start, in the logo's colour.
            tile = new FrameLayout(c);
            ground = new android.graphics.drawable.GradientDrawable();
            ground.setCornerRadius(st.radius);
            tile.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), ground, null));
            tile.setClipToOutline(true);
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
            item.addView(tile, new FrameLayout.LayoutParams(colW, -1));
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
            Integer known = url.isEmpty() ? null : tiles.get(url);
            ground.setColor(known != null ? known : url.isEmpty() ? GuideLook.nameColour(c.name) : GuideLook.PILL_ON);
            if (url.isEmpty()) {
                Glide.with(logo).clear(logo);
                initials.setVisibility(View.VISIBLE);
            } else {
                initials.setVisibility(View.INVISIBLE);
                Glide.with(logo).load(url).listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                    @Override public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model,
                                                          @NonNull com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean first) {
                        if (ch == c) {
                            initials.setVisibility(View.VISIBLE);
                            ground.setColor(GuideLook.nameColour(c.name));
                        }
                        return false;
                    }

                    @Override public boolean onResourceReady(@NonNull android.graphics.drawable.Drawable d, @NonNull Object model,
                                                             com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                             @NonNull com.bumptech.glide.load.DataSource source, boolean first) {
                        if (ch == c && tiles.get(url) == null) {
                            int colour = tileOf(d);
                            tiles.put(url, colour);
                            ground.setColor(colour);
                        }
                        return false;
                    }
                }).into(logo);
            }
            marks();
            Guide.Schedule s = blocks.schedule;
            int at = s == null ? -1 : s.at(st.now);
            tile.setContentDescription(c.name + (at >= 0 ? ", " + s.title[at] : ""));
            blocks.setContentDescription(at >= 0 ? s.title[at] + ", " + Ui.range(s.start[at], s.stop[at]) : blocks.noGuide);
            blocks.invalidate();
        }

        /** The favourite's star, and the selection drawn again. */
        void marks() {
            if (ch == null) return;
            star.setVisibility(Picks.isFavourite(itemView.getContext(), ch.key) ? View.VISIBLE : View.GONE);
            blocks.invalidate();
        }
    }

    /** The tile's colour for a logo as loaded: from a small copy of its pixels (GuideLook.tile). */
    private static int tileOf(android.graphics.drawable.Drawable d) {
        try {
            int s = 24;
            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            android.graphics.Rect was = new android.graphics.Rect(d.getBounds());
            d.setBounds(0, 0, s, s);
            d.draw(cv);
            d.setBounds(was);
            int[] px = new int[s * s];
            b.getPixels(px, 0, s, 0, 0, s, s);
            b.recycle();
            return GuideLook.tile(px, s, s);
        } catch (RuntimeException e) {
            return GuideLook.LIGHT_GREY;
        }
    }
}
