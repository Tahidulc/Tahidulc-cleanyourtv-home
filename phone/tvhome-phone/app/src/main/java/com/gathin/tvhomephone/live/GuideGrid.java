package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.InsetDrawable;
import android.text.TextUtils;
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
 * The TV guide's grid: the channels down the left (number, logo, name, a star on favourites), the half hours across the
 * top with a red line at now, and each channel's programmes as blocks along its row. One RecyclerView of rows, each row
 * one view that draws its blocks from a shared horizontal offset, so twenty thousand channels and a day of guide stay
 * smooth: no view per programme. A sideways drag or fling moves every row and the ruler together; up and down is the
 * RecyclerView's own scrolling.
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
    private final int slop, minFling, maxFling, colW, rowH;
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
        colW = Ui.dp(c, wide ? 184 : 124);
        rowH = Ui.dp(c, wide ? 72 : 64);
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity();
        maxFling = vc.getScaledMaximumFlingVelocity();
        scroller = new OverScroller(c);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(c);
        corner = Ui.text(c, "", 14, Ui.TEXT, true);
        corner.setGravity(Gravity.CENTER_VERTICAL);
        corner.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 8), 0);
        corner.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.live_ic_down, 0);
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(corner, ColorStateList.valueOf(Ui.G_SELECT));
        corner.setBackground(Ui.touch(c, false, Ui.G_CELL, 0));
        corner.setContentDescription(c.getString(R.string.live_pick_day));
        corner.setSingleLine(true);
        head.addView(corner, new LinearLayout.LayoutParams(colW, Ui.dp(c, 48)));
        ruler = new Ruler(c);
        head.addView(ruler, new LinearLayout.LayoutParams(0, Ui.dp(c, 48), 1f));
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));

        rows = new RecyclerView(c);
        rows.setLayoutManager(new LinearLayoutManager(c));
        rows.setHasFixedSize(true);
        rows.setItemAnimator(null);
        rows.setAdapter(adapter);
        rows.setClipToPadding(false);
        rows.setPadding(0, 0, 0, Ui.dp(c, 12));
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

    private float viewport() {
        return getWidth() - colW;
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
        final TimeZone tz = TimeZone.getDefault();
        final Paint blockFill = fill(Ui.G_BLOCK), nowFill = fill(Ui.G_NOW), pastFill = fill(Ui.G_PAST), selFill = fill(Ui.G_SELECT),
                nowLine = fill(Ui.RED), tick = fill(Ui.G_LINE);
        final Paint title, titleInk, time, timeInk, ruler, rulerDay, nowLabel;
        final RectF rect = new RectF();
        final char[] buf = new char[16];
        final float gap, radius, pad, minText, nowW, ellipsis, ellipsisInk;

        State(Context c, boolean wide) {
            gap = Ui.dp(c, 1.5f);
            radius = Ui.dp(c, 6);
            pad = Ui.dp(c, 10);
            minText = Ui.dp(c, 14);
            nowW = Ui.dp(c, 2);
            Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            title = text(c, wide ? 16 : 14.5f, Ui.TEXT, medium);
            titleInk = text(c, wide ? 16 : 14.5f, Ui.G_INK, Typeface.DEFAULT_BOLD);
            time = text(c, wide ? 13 : 12, Ui.G_TIME, Typeface.DEFAULT);
            timeInk = text(c, wide ? 13 : 12, 0xCC04113A, Typeface.DEFAULT);
            ruler = text(c, 13, Ui.SOFT, medium);
            rulerDay = text(c, 13, Ui.G_SELECT, Typeface.DEFAULT_BOLD);
            nowLabel = text(c, 11, Ui.TEXT, Typeface.DEFAULT_BOLD);
            ellipsis = title.measureText("…");
            ellipsisInk = titleInk.measureText("…");
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

        /** "HH:mm" for t into buf at at; the index after it. No allocation (it runs while drawing). */
        int clock(long t, int at) {
            long mins = Math.floorMod(Math.floorDiv(t + tz.getOffset(t), 60_000L), 1440L);
            int h = (int) (mins / 60), m = (int) (mins % 60);
            buf[at] = (char) ('0' + h / 10);
            buf[at + 1] = (char) ('0' + h % 10);
            buf[at + 2] = ':';
            buf[at + 3] = (char) ('0' + m / 10);
            buf[at + 4] = (char) ('0' + m % 10);
            return at + 5;
        }

        /** "HH:mm – HH:mm" into buf; its length. */
        int range(long a, long b) {
            int at = clock(a, 0);
            buf[at++] = ' ';
            buf[at++] = '–';
            buf[at++] = ' ';
            return clock(b, at);
        }

        /** Writes s from x on the baseline y, cut with an ellipsis to fit max. */
        void fit(Canvas cv, String s, float x, float y, float max, Paint p, float ell) {
            int n = s.length();
            if (n == 0 || max <= 0) return;
            int fits = p.breakText(s, true, max, null);
            if (fits >= n) {
                cv.drawText(s, x, y, p);
                return;
            }
            int cut = p.breakText(s, true, Math.max(0, max - ell), null);
            if (cut <= 0) return;
            cv.drawText(s, 0, cut, x, y, p);
            cv.drawText("…", x + p.measureText(s, 0, cut), y, p);
        }
    }

    /** One channel's programmes along its row. */
    @SuppressLint("ViewConstructor")
    static final class Blocks extends View {
        private final State st;
        private final GestureDetector touch;
        Guide.Schedule schedule;
        String key = "";
        String noGuide = "";
        Channel ch;
        Listener listener;
        private float titleY, timeY, tapX = -1;

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
            titleY = h * 0.45f;
            timeY = h * 0.76f;
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
            float top = st.gap, bottom = h - st.gap;
            long tL = tl.time(off), tR = tl.time(off + w);
            boolean mine = key.equals(st.selKey);
            Guide.Schedule s = schedule;
            if (s == null || s.start.length == 0) {
                float l = Math.max(-off, -st.radius), r = Math.min(tl.width() - off, w + st.radius);
                st.rect.set(l + st.gap, top, r - st.gap, bottom);
                boolean sel = mine && st.selStart == 0;
                cv.drawRoundRect(st.rect, st.radius, st.radius, sel ? st.selFill : st.blockFill);
                float tx = Math.max(st.rect.left, 0) + st.pad;
                st.fit(cv, noGuide, tx, h * 0.58f, w - tx - st.pad, sel ? st.timeInk : st.time, st.ellipsis);
            } else {
                int n = s.start.length;
                for (int i = Timeline.first(s, tL); i < n && s.start[i] < tR; i++) {
                    float l = tl.x(s.start[i]) - off, r = tl.x(s.stop[i]) - off;
                    if (r <= 0) continue;
                    block(cv, s, i, l, r, top, bottom, w, mine);
                }
            }
            float nx = tl.x(st.now) - off;
            if (nx >= -st.nowW && nx <= w + st.nowW) cv.drawRect(nx - st.nowW / 2, 0, nx + st.nowW / 2, h, st.nowLine);
        }

        private void block(Canvas cv, Guide.Schedule s, int i, float l, float r, float top, float bottom, float w, boolean mine) {
            float left = Math.max(l + st.gap, -st.radius), right = Math.min(r - st.gap, w + st.radius);
            if (right - left < 1) return;
            long now = st.now;
            boolean on = s.start[i] <= now && now < s.stop[i];
            boolean sel = mine && s.start[i] == st.selStart;
            st.rect.set(left, top, right, bottom);
            cv.drawRoundRect(st.rect, st.radius, st.radius, sel ? st.selFill : on ? st.nowFill : st.blockFill);
            if (on && !sel) {
                // What has been shown already, a shade darker.
                float px = Math.min(st.tl.x(now) - st.offset, right);
                if (px > left) {
                    cv.save();
                    cv.clipRect(left, top, px, bottom);
                    cv.drawRoundRect(st.rect, st.radius, st.radius, st.pastFill);
                    cv.restore();
                }
            }
            // The writing stays in view at the left edge while the block runs on under it.
            float tx = Math.max(left, 0) + st.pad, max = right - st.pad - tx;
            if (max < st.minText) return;
            String t = s.title[i].isEmpty() ? "—" : s.title[i];
            st.fit(cv, t, tx, titleY, max, sel ? st.titleInk : st.title, sel ? st.ellipsisInk : st.ellipsis);
            Paint tp = sel ? st.timeInk : st.time;
            int len = st.range(s.start[i], s.stop[i]);
            if (tp.measureText(st.buf, 0, len) <= max) cv.drawText(st.buf, 0, len, tx, timeY, tp);
            else if (tp.measureText(st.buf, 0, 5) <= max) cv.drawText(st.buf, 0, 5, tx, timeY, tp);
        }
    }

    /** The half hours across the top, the day's name at midnight, and now in red. */
    private final class Ruler extends View {
        private final float tickH, labelX, nowPad, nowH, one, back;

        Ruler(Context c) {
            super(c);
            setBackgroundColor(Ui.G_CELL);
            tickH = Ui.dp(c, 10);
            labelX = Ui.dp(c, 6);
            nowPad = Ui.dp(c, 6);
            nowH = Ui.dp(c, 18);
            one = Ui.dp(c, 1);
            back = Ui.dp(c, 60);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(@NonNull Canvas cv) {
            Timeline tl = st.tl;
            if (tl == null || Float.isNaN(st.offset)) return;
            float w = getWidth(), h = getHeight(), off = st.offset;
            long tL = tl.time(off - back), tR = tl.time(off + w);
            float base = h * 0.62f;
            for (long t = Timeline.ceilHalfHour(tL, st.tz); t <= tR; t += Timeline.HALF_HOUR) {
                float x = tl.x(t) - off;
                cv.drawRect(x, h - tickH, x + one, h, st.tick);
                long local = Math.floorMod(t + st.tz.getOffset(t), Timeline.DAY);
                if (local == 0) {
                    cv.drawText(Ui.day(getContext(), t), x + labelX, base, st.rulerDay);
                } else {
                    int len = st.clock(t, 0);
                    cv.drawText(st.buf, 0, len, x + labelX, base, st.ruler);
                }
            }
            cv.drawRect(0, h - 1, w, h, st.tick);
            float nx = tl.x(st.now) - off;
            if (nx >= -st.nowW && nx <= w + st.nowW) {
                int len = st.clock(st.now, 0);
                float tw = st.nowLabel.measureText(st.buf, 0, len) + 2 * nowPad;
                float left = Math.max(0, Math.min(w - tw, nx - tw / 2));
                st.rect.set(left, h - tickH - nowH - 2 * one, left + tw, h - tickH - 2 * one);
                cv.drawRoundRect(st.rect, nowH / 2, nowH / 2, st.nowLine);
                cv.drawText(st.buf, 0, len, left + nowPad, st.rect.bottom - nowH * 0.28f, st.nowLabel);
                cv.drawRect(nx - st.nowW / 2, h - tickH - 2 * one, nx + st.nowW / 2, h, st.nowLine);
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
        final LinearLayout cell;
        final TextView number, name, initials;
        final ImageView logo, star;
        final Blocks blocks;
        View sideCol;
        Channel ch;

        Row(Context c) {
            super(new LinearLayout(c));
            LinearLayout item = (LinearLayout) itemView;
            item.setLayoutParams(new RecyclerView.LayoutParams(-1, rowH));
            cell = new LinearLayout(c);
            cell.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout side = new LinearLayout(c);
            side.setOrientation(LinearLayout.VERTICAL);
            side.setGravity(Gravity.CENTER_HORIZONTAL);
            number = Ui.text(c, "", 12, Ui.G_TIME, true);
            number.setGravity(Gravity.CENTER);
            number.setSingleLine(true);
            side.addView(number, new LinearLayout.LayoutParams(-1, 0, 1f));
            star = new ImageView(c);
            star.setScaleType(ImageView.ScaleType.CENTER);
            star.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            side.addView(star, new LinearLayout.LayoutParams(-1, 0, 1.3f));
            // The whole column beside the logo is the star's to touch (the row's full height).
            side.setBackground(Ui.touch(c, false, 0, 0));
            side.setContentDescription(c.getString(R.string.live_favourite));
            side.setOnClickListener(v -> {
                if (ch != null && listener != null) listener.onFavourite(ch);
            });
            sideCol = side;
            cell.addView(side, new LinearLayout.LayoutParams(Ui.dp(c, 40), -1));
            LinearLayout id = new LinearLayout(c);
            id.setOrientation(LinearLayout.VERTICAL);
            id.setGravity(Gravity.CENTER_VERTICAL);
            id.setPadding(0, 0, Ui.dp(c, 8), 0);
            FrameLayout box = new FrameLayout(c);
            initials = Ui.text(c, "", 13, Ui.SOFT, true);
            initials.setGravity(Gravity.CENTER);
            initials.setBackground(Ui.round(c, Ui.GLASS, 8));
            box.addView(initials, new FrameLayout.LayoutParams(-1, -1));
            logo = new ImageView(c);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            box.addView(logo, new FrameLayout.LayoutParams(-1, -1));
            id.addView(box, new LinearLayout.LayoutParams(-1, Ui.dp(c, wide ? 34 : 28)));
            name = Ui.text(c, "", wide ? 13 : 11, Ui.SOFT, false);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
            np.topMargin = Ui.dp(c, 3);
            id.addView(name, np);
            cell.addView(id, new LinearLayout.LayoutParams(0, -1, 1f));
            cell.setOnClickListener(v -> {
                if (ch != null && listener != null) listener.onChannel(ch);
            });
            cell.setOnLongClickListener(v -> {
                if (ch != null && listener != null) {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    listener.onMenu(ch);
                }
                return true;
            });
            item.addView(cell, new LinearLayout.LayoutParams(colW, -1));
            blocks = new Blocks(c, st);
            blocks.noGuide = c.getString(R.string.live_no_guide_info);
            item.addView(blocks, new LinearLayout.LayoutParams(0, -1, 1f));
        }

        void bind(Channel c) {
            ch = c;
            blocks.ch = c;
            blocks.key = c.key;
            blocks.listener = listener;
            blocks.schedule = lib.schedule(c.key);
            int n = lib.number(c);
            number.setText(n > 0 ? String.valueOf(n) : "");
            name.setText(c.name);
            initials.setText(Ui.initials(c.name));
            if (c.logo.isEmpty()) {
                Glide.with(logo).clear(logo);
                initials.setVisibility(View.VISIBLE);
            } else {
                initials.setVisibility(View.INVISIBLE);
                Glide.with(logo).load(c.logo).into(logo);
            }
            marks();
            Guide.Schedule s = blocks.schedule;
            int at = s == null ? -1 : s.at(st.now);
            cell.setContentDescription(c.name + (at >= 0 ? ", " + s.title[at] : ""));
            blocks.setContentDescription(at >= 0 ? s.title[at] + ", " + Ui.range(s.start[at], s.stop[at]) : blocks.noGuide);
            blocks.invalidate();
        }

        /** The selection's lighter cell and the favourite's star. */
        void marks() {
            if (ch == null) return;
            Context c = itemView.getContext();
            boolean sel = ch.key.equals(st.selKey);
            cell.setBackground(new InsetDrawable(Ui.touch(c, false, sel ? Ui.G_NOW : Ui.G_CELL, 0), 0, 0, 0, Ui.dp(c, 1)));
            boolean fav = Picks.isFavourite(c, ch.key);
            star.setImageResource(fav ? R.drawable.live_ic_star : R.drawable.live_ic_star_outline);
            star.setImageTintList(ColorStateList.valueOf(fav ? Ui.STAR : 0x59FFFFFF));
            sideCol.setContentDescription(c.getString(fav ? R.string.live_fav_remove : R.string.live_fav_add));
            blocks.invalidate();
        }
    }
}
