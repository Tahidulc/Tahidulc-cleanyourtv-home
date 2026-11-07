package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * UHF's TV guide for the UHF style: the back and more buttons either side of the title, a strip across the top for the
 * focused programme (its channel's tile, the channel, the title, the times, two lines about it), the channel groups as
 * chips, "Today" with the times along a ruler and the now marker, then a row for each channel: its logo on its colour
 * (a round catch-up mark on the ones with catch-up) over the start of its programmes, which are tinted in the
 * channel's colour, the part of what is on now that has gone a lighter shade up to now, the focused one white.
 */
final class SkinUhfGuide {
    private static final String[] GROUPS = {"All channels", "Entertainment", "Sport", "News"};
    private static final String[] GROUP_ICONS = {"apps", "shows", "sports", "news"};
    private static final long HALF = 30 * 60_000L, WINDOW = 2 * 60 * 60_000L;
    private static final int LEFT = SkinUhf.LEFT, RIGHT = SkinUhf.RIGHT;
    /** The channel tiles' width, where the time axis starts, the rows' height and gap, where the first row is, how many show. */
    private static final int TILE_W = 150, AXIS = LEFT + TILE_W + 8, ROW_H = 84, ROW_GAP = 10, GRID_Y = 442, ROWS = 6;
    private static final float PX = (RIGHT - AXIS) / 120f;
    private static final int BLOCK = 0xFF141417, FOCUS = 0xFFECECF0;

    private final SkinUhf s;
    private final SkinHost host;
    private FrameLayout view, gridBox, rulerBox;
    private LinearLayout chipsRow;
    private final List<View> chipViews = new ArrayList<>();
    private FrameLayout infoTile;
    private TextView infoChannel, infoTitle, infoTimes, infoText;
    private LinearLayout infoChannelRow;
    private View nowLine, nowMark;

    private final List<String[]> all;
    private List<String[]> list = new ArrayList<>();
    private final Map<String, List<GuideV2.Show>> shows = new HashMap<>();
    private int group, chip, chan, top;
    private long at, windowStart, now, readAt;
    private boolean onChips, closed, focusedHere;
    /** What the rows were last drawn for: drawn again only when it changes, else only the focus moves. */
    private String drawnFor = "";
    private final List<Block> blocks = new ArrayList<>();

    private static final class Block {
        int row;
        GuideV2.Show show;
        View view;
        TextView line, title;
        int colour;
        boolean first;
    }

    SkinUhfGuide(SkinUhf s) {
        this.s = s;
        this.host = s.host;
        all = host.channels() == null ? new ArrayList<>() : host.channels();
    }

    // ---- building ---------------------------------------------------------------------------------------------------

    View build() {
        view = new FrameLayout(s.c);
        view.setClipChildren(false);
        now = System.currentTimeMillis();
        at = now;
        windowStart = now - now % HALF - HALF;
        // The header: back, the title, more.
        view.addView(roundIcon("back"), lp(LEFT, 36, 56, 56));
        TextView title = s.text(34, host.display(), SkinUhf.TEXT);
        title.setText("TV Guide");
        title.setGravity(Gravity.CENTER);
        view.addView(title, lp(LEFT, 42, RIGHT - LEFT, 44));
        view.addView(roundIcon("more"), lp(RIGHT - 56, 36, 56, 56));
        buildInfo();
        buildChips();
        rulerBox = new FrameLayout(s.c);
        view.addView(rulerBox, lp(0, 380, 1920, 56));
        nowLine = new View(s.c);
        nowLine.setBackgroundColor(0x33FFFFFF);
        view.addView(nowLine, lp(0, 430, 2, GRID_Y + ROWS * (ROW_H + ROW_GAP) - 430));
        gridBox = new FrameLayout(s.c);
        gridBox.setClipChildren(false);
        view.addView(gridBox, lp(0, GRID_Y, 1920, ROWS * (ROW_H + ROW_GAP)));
        filter();
        read();
        draw();
        return view;
    }

    private FrameLayout.LayoutParams lp(float x, float y, float w, float h) {
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(s.px(w), s.px(h));
        l.leftMargin = s.px(x);
        l.topMargin = s.px(y);
        return l;
    }

    private View roundIcon(String icon) {
        FrameLayout b = new FrameLayout(s.c);
        b.setBackground(s.circle(SkinUhf.GLASS_HI));
        ImageView ic = new ImageView(s.c);
        ic.setImageDrawable(SkinIcons.get(icon, SkinUhf.TEXT, 2.2f));
        b.addView(ic, new FrameLayout.LayoutParams(s.px(26), s.px(26), Gravity.CENTER));
        return b;
    }

    private void buildInfo() {
        infoTile = new FrameLayout(s.c);
        view.addView(infoTile, lp(LEFT, 118, 300, 168));
        LinearLayout col = new LinearLayout(s.c);
        col.setOrientation(LinearLayout.VERTICAL);
        infoChannelRow = new LinearLayout(s.c);
        infoChannelRow.setGravity(Gravity.CENTER_VERTICAL);
        infoChannel = s.text(19, host.semi(), SkinUhf.TEXT2);
        infoChannelRow.addView(infoChannel);
        col.addView(infoChannelRow);
        infoTitle = s.text(40, host.display(), SkinUhf.TEXT);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = s.px(10);
        col.addView(infoTitle, tl);
        infoTimes = s.text(21, host.regular(), SkinUhf.TEXT);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ml.topMargin = s.px(10);
        col.addView(infoTimes, ml);
        infoText = s.text(19, host.regular(), SkinUhf.TEXT2);
        infoText.setSingleLine(false);
        infoText.setMaxLines(2);
        infoText.setLineSpacing(s.px(6), 1f);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.topMargin = s.px(10);
        col.addView(infoText, dl);
        view.addView(col, lp(LEFT + 330, 120, RIGHT - LEFT - 330, 170));
        View rule = new View(s.c);
        rule.setBackgroundColor(0x1AFFFFFF);
        view.addView(rule, lp(LEFT, 302, RIGHT - LEFT, 1));
    }

    private void buildChips() {
        chipsRow = new LinearLayout(s.c);
        chipsRow.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < GROUPS.length; i++) {
            LinearLayout c = new LinearLayout(s.c);
            c.setGravity(Gravity.CENTER_VERTICAL);
            c.setPadding(s.px(20), 0, s.px(24), 0);
            ImageView ic = new ImageView(s.c);
            ic.setImageDrawable(SkinIcons.get(GROUP_ICONS[i], SkinUhf.TEXT, 2f));
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(s.px(22), s.px(22));
            il.rightMargin = s.px(10);
            c.addView(ic, il);
            TextView t = s.text(20, host.semi(), SkinUhf.TEXT);
            t.setText(GROUPS[i]);
            c.addView(t);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, s.px(50));
            cl.rightMargin = s.px(14);
            chipsRow.addView(c, cl);
            chipViews.add(c);
            final int g = i;
            c.setOnClickListener(v -> { chip = g; choose(g); onChips = false; draw(); });
        }
        view.addView(chipsRow, lp(LEFT, 316, RIGHT - LEFT, 50));
    }

    // ---- data -------------------------------------------------------------------------------------------------------

    private void filter() {
        list = new ArrayList<>();
        for (String[] ch : all) {
            String g = ch.length > 2 && ch[2] != null ? ch[2] : "";
            boolean in = group == 0 || group == 1 && g.equalsIgnoreCase("Entertainment") || group == 2 && g.contains("Sport")
                    || group == 3 && g.equalsIgnoreCase("News");
            if (in) list.add(ch);
        }
    }

    /** Sky's schedules for every channel, twenty at a time: the rows fill in as each lot comes. */
    private void read() {
        readAt = System.currentTimeMillis();
        List<String[]> want = new ArrayList<>(all);
        long from = now;
        for (int i = 0; i < want.size(); i += 20) {
            List<String[]> lot = new ArrayList<>(want.subList(i, Math.min(want.size(), i + 20)));
            host.io().execute(() -> {
                Map<String, List<GuideV2.Show>> got;
                try {
                    got = host.schedules(lot, from);
                } catch (Exception e) {
                    got = null;
                }
                Map<String, List<GuideV2.Show>> done = got;
                host.main().post(() -> {
                    if (closed || done == null) return;
                    shows.putAll(done);
                    drawnFor = "";
                    draw();
                });
            });
        }
    }

    void refresh() {
        now = System.currentTimeMillis();
        if (now - readAt > 10 * 60_000L) read();
        drawnFor = "";
        draw();
    }

    void close() {
        closed = true;
    }

    private List<GuideV2.Show> showsOf(int row) {
        if (row < 0 || row >= list.size()) return null;
        return shows.get(list.get(row)[0]);
    }

    /** The programme on row at `at` (else the next one after it); null when the row has no schedule yet. */
    private GuideV2.Show current(int row) {
        List<GuideV2.Show> l = showsOf(row);
        if (l == null || l.isEmpty()) return null;
        for (GuideV2.Show sh : l) if (sh.start <= at && sh.end > at) return sh;
        for (GuideV2.Show sh : l) if (sh.start > at) return sh;
        return l.get(l.size() - 1);
    }

    private void choose(int g) {
        if (g == group) return;
        group = g;
        filter();
        chan = 0;
        top = 0;
        at = Math.max(now, windowStart);
        drawnFor = "";
    }

    // ---- keys -------------------------------------------------------------------------------------------------------

    /** False when the move leaves the guide (Left from its start: the rail). */
    boolean move(int dx, int dy) {
        if (onChips) {
            if (dx < 0 && chip == 0) return false;
            if (dx != 0) {
                chip = Math.max(0, Math.min(GROUPS.length - 1, chip + dx));
                choose(chip);
            } else if (dy > 0 && !list.isEmpty()) {
                onChips = false;
            }
            draw();
            return true;
        }
        if (dy != 0) {
            int n = chan + dy;
            if (n < 0) { onChips = true; chip = group; draw(); return true; }
            if (n >= list.size()) return true;
            GuideV2.Show cur = current(chan);
            if (cur != null) at = Math.max(at, Math.max(cur.start, windowStart));
            chan = n;
            if (chan < top) top = chan;
            if (chan >= top + ROWS) top = chan - ROWS + 1;
            draw();
            return true;
        }
        GuideV2.Show cur = current(chan);
        if (dx > 0) {
            at = cur != null ? Math.max(cur.end, at + 60_000L) : at + HALF;
            while (at >= windowStart + WINDOW - HALF / 2) windowStart += HALF;
            draw();
            return true;
        }
        long earliest = now - now % HALF - HALF;
        if (cur == null || cur.start <= Math.max(windowStart, earliest)) {
            if (windowStart > earliest) {
                windowStart -= HALF;
                at = Math.max(windowStart, at - HALF);
                draw();
                return true;
            }
            return false;
        }
        at = Math.max(earliest, cur.start - 60_000L);
        while (at < windowStart && windowStart > earliest) windowStart -= HALF;
        draw();
        return true;
    }

    void ok() {
        if (onChips) {
            choose(chip);
            onChips = false;
            draw();
            return;
        }
        if (chan < list.size()) host.tune(list.get(chan));
    }

    /** Back leaves the guide for the rail (handled by the style). */
    boolean back() {
        return false;
    }

    void paint(boolean here) {
        focusedHere = here;
        draw();
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    private void draw() {
        if (view == null) return;
        // The chips: the chosen group lighter, the focused one white.
        for (int i = 0; i < chipViews.size(); i++) {
            LinearLayout c = (LinearLayout) chipViews.get(i);
            boolean f = focusedHere && onChips && i == chip, sel = i == group;
            GradientDrawable g = s.fill(25, f ? 0xFFFFFFFF : sel ? 0x38FFFFFF : 0x14FFFFFF);
            if (!f) g.setStroke(Math.max(1, s.px(1)), 0x1FFFFFFF);
            c.setBackground(g);
            ((SkinIcons.Icon) ((ImageView) c.getChildAt(0)).getDrawable()).colour(f ? SkinUhf.DARK : SkinUhf.TEXT);
            ((TextView) c.getChildAt(1)).setTextColor(f ? SkinUhf.DARK : SkinUhf.TEXT);
        }
        String want = group + "|" + top + "|" + windowStart + "|" + shows.size() + "|" + list.size();
        if (!want.equals(drawnFor)) {
            drawnFor = want;
            drawRuler();
            drawRows();
        }
        focus();
        info();
    }

    private float xOf(long t) {
        return AXIS + (t - windowStart) / 60_000f * PX;
    }

    private void drawRuler() {
        rulerBox.removeAllViews();
        LinearLayout today = new LinearLayout(s.c);
        today.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = s.text(30, host.display(), SkinUhf.TEXT);
        t.setText("Today");
        today.addView(t);
        ImageView chev = new ImageView(s.c);
        chev.setImageDrawable(SkinIcons.get("chevdown", SkinUhf.TEXT, 2.6f));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(s.px(16), s.px(16));
        cl.leftMargin = s.px(12);
        today.addView(chev, cl);
        rulerBox.addView(today, lp(LEFT, 4, 160, 44));
        for (long m = windowStart; m <= windowStart + WINDOW - HALF; m += HALF) {
            TextView l = s.text(20, host.regular(), SkinUhf.TEXT2);
            l.setText(Skin.hm(m));
            rulerBox.addView(l, lp(xOf(m) + 6, 14, 120, 28));
        }
        float nx = xOf(now);
        boolean inView = now >= windowStart && now < windowStart + WINDOW;
        if (inView) {
            nowMark = new View(s.c);
            nowMark.setBackground(new Down(0xFFFFFFFF));
            rulerBox.addView(nowMark, lp(nx - 8, 18, 16, 12));
        }
        nowLine.setVisibility(inView && !list.isEmpty() ? View.VISIBLE : View.INVISIBLE);
        FrameLayout.LayoutParams nl = (FrameLayout.LayoutParams) nowLine.getLayoutParams();
        nl.leftMargin = s.px(nx);
        int shown = Math.min(ROWS, Math.max(0, list.size() - top));
        nl.height = s.px(GRID_Y - 430 + shown * (ROW_H + ROW_GAP) - ROW_GAP);
        nowLine.setLayoutParams(nl);
        // UHF's two round buttons at the ruler's end: the order of the channels, and the guide full screen.
        rulerBox.addView(roundIcon("sort"), lp(RIGHT - 52 - 64, 0, 52, 52));
        rulerBox.addView(roundIcon("expand"), lp(RIGHT - 52, 0, 52, 52));
    }

    private void drawRows() {
        gridBox.removeAllViews();
        blocks.clear();
        long end = windowStart + WINDOW;
        if (list.isEmpty()) {
            TextView none = s.text(22, host.regular(), SkinUhf.TEXT2);
            none.setText(all.isEmpty() ? "No channels in the guide yet: they come from the playlist's guide channels."
                    : "No " + GROUPS[group].toLowerCase(Locale.ROOT) + " channels in the guide.");
            gridBox.addView(none, lp(LEFT, 20, RIGHT - LEFT, 40));
            return;
        }
        for (int r = top; r < Math.min(list.size(), top + ROWS); r++) {
            float y = (r - top) * (ROW_H + ROW_GAP);
            String[] ch = list.get(r);
            int colour = SkinUhf.channelColour(ch[1]);
            List<GuideV2.Show> l = showsOf(r);
            boolean firstInView = true;
            if (l == null || l.isEmpty()) {
                Block b = block(r, null, colour, true, y, LEFT + 40, RIGHT);
                b.title.setText(l == null ? "Loading the guide…" : "No listings");
                b.title.setTextColor(SkinUhf.TEXT2);
            } else {
                for (GuideV2.Show sh : l) {
                    if (sh.end <= windowStart || sh.start >= end) continue;
                    float x0 = sh.start <= windowStart ? LEFT + 40 : xOf(sh.start);
                    float x1 = Math.min(RIGHT, xOf(Math.min(sh.end, end))) - 6;
                    if (x1 - x0 < 8) continue;
                    block(r, sh, colour, firstInView, y, x0, x1);
                    firstInView = false;
                }
            }
            tile(ch, colour, y);
        }
    }

    /** One programme's block (or a row's "loading" block when show is null). */
    private Block block(int row, GuideV2.Show sh, int colour, boolean first, float y, float x0, float x1) {
        Block b = new Block();
        b.row = row;
        b.show = sh;
        b.colour = colour;
        b.first = first;
        LinearLayout v = new LinearLayout(s.c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        float textX = Math.max(x0 + 18, first && x0 < AXIS ? AXIS + 16 : x0 + 18);
        v.setPadding(s.px(textX - x0), 0, s.px(14), 0);
        b.line = s.text(15, host.regular(), 0x8CFFFFFF);
        b.title = s.text(21, host.regular(), SkinUhf.TEXT);
        String[] ch = list.get(row);
        // A programme that shows only a sliver (one nearly over at the grid's left edge, under the channel's tile, or a
        // five-minute filler) keeps its block but no words, which would only be cut to a letter and dots ("C…"); the
        // top of the guide names it when it is picked.
        boolean words = x1 - textX - 14 >= 64;
        if (sh != null && !words) {
            v.addView(b.title);
        } else if (sh != null) {
            if (first) {
                String q = quality(ch[1]);
                android.text.SpannableStringBuilder line = new android.text.SpannableStringBuilder(bare(ch[1]).toUpperCase(Locale.ROOT));
                if (q != null) {
                    line.append("  ");
                    int st = line.length();
                    line.append(q);
                    line.setSpan(new Chip(s.px(1.2f), s.px(4), s.px(5)), st, line.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                line.append("  ").append(Skin.hm(sh.start));
                b.line.setText(line);
            } else {
                b.line.setText(Skin.hm(sh.start));
            }
            b.title.setText(sh.title);
            v.addView(b.line);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tl.topMargin = s.px(5);
            v.addView(b.title, tl);
        } else {
            v.addView(b.title);
        }
        b.view = v;
        gridBox.addView(v, lp(x0, y, x1 - x0, ROW_H));
        blocks.add(b);
        return b;
    }

    /** A channel's logo tile at the start of its row: its colour, its logo (else its name), the catch-up mark. */
    private void tile(String[] ch, int colour, float y) {
        FrameLayout t = channelTile(ch, colour, 14, 112, 44);
        if (catchUp(ch[1])) {
            FrameLayout mark = new FrameLayout(s.c);
            mark.setBackground(s.circle(0x8C000000));
            ImageView ic = new ImageView(s.c);
            ic.setImageDrawable(SkinIcons.get("catchup", SkinUhf.TEXT, 2.4f));
            mark.addView(ic, new FrameLayout.LayoutParams(s.px(18), s.px(18), Gravity.CENTER));
            FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(s.px(28), s.px(28), Gravity.END);
            ml.topMargin = s.px(6);
            ml.rightMargin = s.px(6);
            t.addView(mark, ml);
        }
        gridBox.addView(t, lp(LEFT, y, TILE_W, ROW_H));
    }

    /** A tile in the channel's colour with its logo (white on clear) or its name. */
    private FrameLayout channelTile(String[] ch, int colour, float radius, float logoW, float logoH) {
        FrameLayout t = new FrameLayout(s.c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{colour, SkinUhf.mix(colour, 0xFF000000, 0.35f)});
        g.setCornerRadius(s.px(radius));
        t.setBackground(g);
        TextView name = s.text(logoH > 50 ? 40 : 22, host.display(), SkinUhf.TEXT);
        name.setText(SkinUhf.shortName(ch[1]));
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(false);
        name.setMaxLines(2);
        t.addView(name, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        String url = host.channelLogo(ch[0]);
        if (url != null) {
            ImageView logo = new ImageView(s.c);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setVisibility(View.INVISIBLE);
            t.addView(logo, new FrameLayout.LayoutParams(s.px(logoW), s.px(logoH), Gravity.CENTER));
            host.image(logo, url, s.px(logoW * 1.5f), () -> { logo.setVisibility(View.VISIBLE); name.setVisibility(View.GONE); });
        }
        return t;
    }

    /** The focused block white, the rest in their channel's tint (what is on now lighter up to now). */
    private void focus() {
        GuideV2.Show cur = onChips ? null : current(chan);
        for (Block b : blocks) {
            boolean f = focusedHere && !onChips && b.row == chan && (b.show == cur || cur == null && b.show == null);
            boolean on = b.show != null && b.show.start <= now && b.show.end > now;
            float nowX = on ? xOf(now) : -1;
            FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) b.view.getLayoutParams();
            float left = l.leftMargin / (float) s.px(1);
            b.view.setBackground(new Tint(s.px(12), f ? FOCUS : SkinUhf.mix(b.colour, BLOCK, 0.7f),
                    f || !on ? 0 : SkinUhf.mix(b.colour, BLOCK, 0.48f), f || !on ? 0 : s.px(nowX - left)));
            b.title.setTextColor(f ? SkinUhf.DARK : b.show == null ? SkinUhf.TEXT2 : SkinUhf.TEXT);
            b.line.setTextColor(f ? 0x8C000000 : 0x8CFFFFFF);
        }
    }

    /** The strip across the top: the focused programme (or the focused chip's channels). */
    private void info() {
        infoTile.removeAllViews();
        if (list.isEmpty()) {
            infoChannel.setText(GROUPS[group].toUpperCase(Locale.ROOT));
            infoTitle.setText("No channels");
            infoTimes.setText("");
            infoText.setText("");
            return;
        }
        int row = Math.min(chan, list.size() - 1);
        String[] ch = list.get(row);
        int colour = SkinUhf.channelColour(ch[1]);
        FrameLayout t = channelTile(ch, colour, 22, 210, 72);
        infoTile.addView(t, SkinUhf.full());
        FrameLayout badge = new FrameLayout(s.c);
        badge.setBackground(s.fill(10, SkinUhf.RED));
        ImageView bi = new ImageView(s.c);
        bi.setImageDrawable(SkinIcons.get("live", SkinUhf.TEXT, 2.2f));
        badge.addView(bi, new FrameLayout.LayoutParams(s.px(20), s.px(20), Gravity.CENTER));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(s.px(34), s.px(34), Gravity.END);
        bl.topMargin = s.px(12);
        bl.rightMargin = s.px(12);
        infoTile.addView(badge, bl);
        GuideV2.Show sh = current(row);
        String q = quality(ch[1]);
        android.text.SpannableStringBuilder line = new android.text.SpannableStringBuilder(bare(ch[1]).toUpperCase(Locale.ROOT));
        if (q != null) {
            line.append("  ");
            int st = line.length();
            line.append(q);
            line.setSpan(new Chip(s.px(1.2f), s.px(4), s.px(5)), st, line.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        infoChannel.setText(line);
        if (sh == null) {
            infoTitle.setText(showsOf(row) == null ? "Loading the guide…" : "No listings");
            infoTimes.setText("");
            infoText.setText("");
            return;
        }
        infoTitle.setText(sh.title);
        boolean on = sh.start <= now && sh.end > now;
        android.text.SpannableStringBuilder times = new android.text.SpannableStringBuilder(Skin.hm(sh.start) + " — " + Skin.hm(sh.end));
        if (on) {
            int st = times.length();
            times.append("  ·  ").append(String.valueOf(Math.max(1, (sh.end - now) / 60_000L))).append(" min left");
            times.setSpan(new android.text.style.ForegroundColorSpan(SkinUhf.TEXT2), st, times.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (sh.start > now) {
            int st = times.length();
            times.append("  ·  in ").append(span(sh.start - now));
            times.setSpan(new android.text.style.ForegroundColorSpan(SkinUhf.TEXT2), st, times.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        GuideV2.Show next = null;
        List<GuideV2.Show> l = showsOf(row);
        for (int i = 0; l != null && i < l.size() - 1; i++) if (l.get(i) == sh) next = l.get(i + 1);
        if (next != null) {
            int st = times.length();
            times.append("  ·  Next ").append(Skin.hm(next.start)).append(" ").append(next.title);
            times.setSpan(new android.text.style.ForegroundColorSpan(SkinUhf.TEXT3), st, times.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        infoTimes.setText(times);
        infoText.setText(sh.text == null ? "" : sh.text);
        if (on) {
            FrameLayout bar = new FrameLayout(s.c);
            bar.setBackground(s.fill(4, 0x4DFFFFFF));
            View in = new View(s.c);
            in.setBackground(s.fill(4, SkinUhf.TEXT));
            float p = (now - sh.start) / (float) Math.max(1, sh.end - sh.start);
            bar.addView(in, new FrameLayout.LayoutParams(Math.max(s.px(8), Math.round(s.px(256) * Math.max(0, Math.min(1, p)))), ViewGroup.LayoutParams.MATCH_PARENT));
            FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(s.px(256), s.px(8), Gravity.BOTTOM);
            pl.leftMargin = s.px(22);
            pl.bottomMargin = s.px(18);
            infoTile.addView(bar, pl);
        }
    }

    private static String span(long ms) {
        long m = Math.max(1, ms / 60_000L);
        return m >= 60 ? m / 60 + "h " + (m % 60 > 0 ? m % 60 + "m" : "") : m + " min";
    }

    /** "Sky Sports Main Event UHD" → "Sky Sports Main Event". */
    static String bare(String name) {
        return name == null ? "" : name.replaceAll("(?i)\\s+(uhd|fhd|hd|4k)$", "").trim();
    }

    /** The picture's mark from the channel's name: 4K, FHD or HD; null for none. */
    static String quality(String name) {
        String n = name == null ? "" : name.toUpperCase(Locale.ROOT);
        if (n.matches(".*\\b(UHD|4K)$")) return "4K";
        if (n.endsWith(" FHD")) return "FHD";
        if (n.endsWith(" HD")) return "HD";
        return null;
    }

    /** Channels that can be watched again on their own catch-up service in the UK (BBC iPlayer, ITVX, Channel 4, My5, Sky). */
    static boolean catchUp(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.contains("news") || n.contains("sport")) return false;
        return n.matches(".*\\b(bbc|cbbc|cbeebies|itv|channel 4|e4|more4|film4|channel 5|5usa|5star|sky atlantic|sky showcase|sky max|sky witness|sky comedy|sky crime|dave|gold|alibi|eden|yesterday|drama|w)\\b.*");
    }

    // ---- drawables --------------------------------------------------------------------------------------------------

    /** A programme's block: rounded, one colour, with what has gone of a live one a lighter shade up to `split`. */
    private static final class Tint extends Drawable {
        private final float r;
        private final int base, done;
        private final float split;
        private final Paint pb = new Paint(Paint.ANTI_ALIAS_FLAG), pd = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final Path shape = new Path();

        Tint(float r, int base, int done, float split) {
            this.r = r;
            this.base = base;
            this.done = done;
            this.split = split;
            pb.setColor(base);
            pd.setColor(done);
        }

        @Override protected void onBoundsChange(Rect b) {
            box.set(b);
            shape.reset();
            shape.addRoundRect(box, r, r, Path.Direction.CW);
        }

        @Override public void draw(Canvas canvas) {
            canvas.drawRoundRect(box, r, r, pb);
            if (done != 0 && split > 0) {
                int save = canvas.save();
                canvas.clipPath(shape);
                canvas.drawRect(box.left, box.top, Math.min(box.right, box.left + split), box.bottom, pd);
                canvas.restoreToCount(save);
            }
        }

        @Override public void setAlpha(int alpha) {
        }

        @Override public void setColorFilter(ColorFilter cf) {
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** The now marker: a small white triangle pointing down. */
    private static final class Down extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Down(int colour) {
            p.setColor(colour);
        }

        @Override protected void onBoundsChange(Rect b) {
            path.reset();
            path.moveTo(b.left, b.top);
            path.lineTo(b.right, b.top);
            path.lineTo(b.exactCenterX(), b.bottom);
            path.close();
        }

        @Override public void draw(Canvas canvas) {
            canvas.drawPath(path, p);
        }

        @Override public void setAlpha(int alpha) {
            p.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter cf) {
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** "HD" in a thin rounded outline, in the text's own colour (a span, so it sits in the line). */
    private static final class Chip extends android.text.style.ReplacementSpan {
        private final float stroke, radius, pad;

        Chip(float stroke, float radius, float pad) {
            this.stroke = stroke;
            this.radius = radius;
            this.pad = pad;
        }

        @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            Paint p = new Paint(paint);
            p.setTextSize(paint.getTextSize() * 0.78f);
            return Math.round(p.measureText(text, start, end) + 2 * pad);
        }

        @Override public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            Paint p = new Paint(paint);
            p.setTextSize(paint.getTextSize() * 0.78f);
            float w = p.measureText(text, start, end) + 2 * pad;
            Paint.FontMetrics fm = paint.getFontMetrics();
            float h = (fm.descent - fm.ascent) * 0.82f;
            float cy = y + (fm.ascent + fm.descent) / 2f;
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(stroke);
            line.setColor(paint.getColor());
            canvas.drawRoundRect(new RectF(x + stroke / 2, cy - h / 2, x + w - stroke / 2, cy + h / 2), radius, radius, line);
            Paint.FontMetrics sm = p.getFontMetrics();
            canvas.drawText(text, start, end, x + pad, cy - (sm.ascent + sm.descent) / 2f, p);
        }
    }
}
