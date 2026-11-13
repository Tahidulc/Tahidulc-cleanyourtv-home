package com.gathin.tvhome;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
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
 * The SWIPTV style's TV guide: the focused programme large at the top with its picture in the corner, the channels
 * down the left and three hours of programmes across, a line at now. OK puts TiviMate on the channel.
 */
final class SkinSwipGuide {
    private static final int ROWS = 7, ROW_H = 88, GRID_Y = 440, LINE_X = 260;
    private static final long HALF = 30 * 60_000L, WINDOW = 180 * 60_000L;
    private static final float PX = 7.9f;

    private final SkinSwip s;
    private final SkinHost host;
    final FrameLayout view;
    private final TextView title, line, chip, desc, next, hint, pipLabel, empty;
    private final FrameLayout pipBox, rowsBox;
    private final Skin.Pic pip;
    private final View nowLine, nowDot;
    private final FrameLayout times;
    private View pipPanel;
    private View pipBar;

    private List<String[]> channels = new ArrayList<>();
    private final Map<String, List<GuideV2.Show>> shows = new HashMap<>();
    private long readAt;
    private int chan, top = -1, drawnTop = -2;
    private long ws, drawnWs, at;
    private int version, drawnVersion = -1;
    private GuideV2.Show sel;
    private final Map<GuideV2.Show, FrameLayout> blocks = new HashMap<>();
    private final Runnable minute = new Runnable() {
        @Override public void run() {
            if (!s.live()) return;
            placeNow();
            s.host.main().postDelayed(this, 60_000L);
        }
    };

    SkinSwipGuide(SkinSwip s) {
        this.s = s;
        host = s.host;
        view = new FrameLayout(s.c);
        view.setClipChildren(false);
        title = s.text(44, host.display(), SkinSwip.TEXT);
        s.add(view, title, 0, 40, 1150, 56);
        LinearLayout tl = new LinearLayout(s.c);
        tl.setOrientation(LinearLayout.HORIZONTAL);
        tl.setGravity(Gravity.CENTER_VERTICAL);
        chip = s.text(13, host.semi(), SkinSwip.TEXT2);
        chip.setLetterSpacing(0.04f);
        chip.setBackground(s.fill(5, SkinSwip.PANEL3));
        chip.setPadding(s.px(6), s.px(4), s.px(6), s.px(3));
        tl.addView(chip);
        line = s.text(20, host.regular(), SkinSwip.TEXT2);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
        ll.leftMargin = s.px(10);
        tl.addView(line, ll);
        s.add(view, tl, 0, 112, 1150, 30);
        desc = s.text(21, host.regular(), SkinSwip.TEXT2);
        desc.setSingleLine(false);
        desc.setMaxLines(3);
        desc.setLineSpacing(s.px(9), 1f);
        s.add(view, desc, 0, 158, 1100, 110);
        LinearLayout hl = new LinearLayout(s.c);
        hl.setOrientation(LinearLayout.HORIZONTAL);
        hl.setGravity(Gravity.CENTER_VERTICAL);
        hint = s.text(19, host.semi(), 0xFF111111);
        hint.setBackground(s.fill(26, SkinSwip.TEXT));
        hint.setPadding(s.px(22), s.px(13), s.px(24), s.px(13));
        hint.setCompoundDrawablePadding(s.px(10));
        hl.addView(hint);
        next = s.text(19, host.regular(), SkinSwip.TEXT3);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
        nl.leftMargin = s.px(22);
        hl.addView(next, nl);
        s.add(view, hl, 0, 300, 1150, 56);
        // The programme's picture in the corner (Sky's), or the channel's colour.
        pipBox = new FrameLayout(s.c);
        pipBox.setElevation(s.px(16));
        pipBox.setBackground(s.fill(18, 0xFF000000));
        pip = new Skin.Pic(s.c, s.px(18), 0x00000000);
        pip.alignY(0.3f);
        pipBox.addView(pip, new FrameLayout.LayoutParams(-1, -1));
        View sh = new View(s.c);
        sh.setBackground(s.shade(18, 0x00000000, 0xB3000000));
        pipBox.addView(sh, new FrameLayout.LayoutParams(-1, -1));
        pipLabel = s.text(15, host.semi(), 0xFFFFFFFF);
        pipLabel.setShadowLayer(s.px(6), 0, 0, 0xFF000000);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        pl.leftMargin = s.px(16);
        pl.bottomMargin = s.px(14);
        pipBox.addView(pipLabel, pl);
        s.add(view, pipBox, 1688 - 480, 40, 480, 270);
        // The grid: the times along the top, the channels down the left.
        times = new FrameLayout(s.c);
        s.add(view, times, 0, GRID_Y, 1688, 40);
        TextView today = s.text(20, host.semi(), SkinSwip.TEXT);
        today.setText("Today");
        s.add(times, today, 0, 6, 200, 28);
        nowLine = new View(s.c);
        nowLine.setBackgroundColor(SkinSwip.ORANGE);
        s.add(view, nowLine, 0, GRID_Y + 34, 2, 1080 - GRID_Y - 34);
        nowDot = new View(s.c);
        nowDot.setBackground(s.fill(6, SkinSwip.ORANGE));
        s.add(view, nowDot, 0, GRID_Y + 28, 12, 12);
        rowsBox = new FrameLayout(s.c);
        rowsBox.setClipChildren(false);
        s.add(view, rowsBox, 0, GRID_Y + 40, 1688, ROWS * ROW_H);
        empty = s.text(22, host.regular(), SkinSwip.TEXT2);
        empty.setSingleLine(false);
        empty.setVisibility(View.GONE);
        s.add(view, empty, 0, GRID_Y + 60, 1100, 120);
    }

    /** The page on screen: the channels, and what is on them read again when it has been a while. */
    void shown() {
        channels = host.channels() == null ? new ArrayList<>() : host.channels();
        long now = System.currentTimeMillis();
        if (top < 0) {
            top = 0;
            chan = 0;
            ws = now - now % HALF;
            at = now;
        }
        if (now - readAt > 15 * 60_000L) read();
        s.host.main().removeCallbacks(minute);
        s.host.main().post(minute);
        if (channels.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            empty.setText("No channels yet. The guide fills from your TiviMate playlist's channels.");
        } else empty.setVisibility(View.GONE);
    }

    void destroy() {
        s.host.main().removeCallbacks(minute);
    }

    /** Sky's schedules, twenty channels at a time, from the ones in view first. */
    private void read() {
        readAt = System.currentTimeMillis();
        List<String[]> order = new ArrayList<>();
        for (int i = top; i < channels.size(); i++) order.add(channels.get(i));
        for (int i = 0; i < Math.min(top, channels.size()); i++) order.add(channels.get(i));
        long from = readAt;
        host.io().execute(() -> {
            for (int i = 0; i < order.size(); i += 20) {
                Map<String, List<GuideV2.Show>> got;
                try {
                    got = host.schedules(order.subList(i, Math.min(order.size(), i + 20)), from);
                } catch (Exception e) {
                    got = new HashMap<>();
                }
                Map<String, List<GuideV2.Show>> done = got;
                host.main().post(() -> {
                    if (!s.live()) return;
                    shows.putAll(done);
                    version++;
                    if (sel == null) sel = showAt(chan, at);
                    if (s.page == SkinSwip.GUIDE) paint(s.zone == SkinSwip.Z_GUIDE);
                });
            }
        });
    }

    private List<GuideV2.Show> listFor(int ch) {
        if (ch < 0 || ch >= channels.size()) return null;
        return shows.get(channels.get(ch)[0]);
    }

    private GuideV2.Show showAt(int ch, long t) {
        List<GuideV2.Show> l = listFor(ch);
        if (l == null) return null;
        GuideV2.Show after = null;
        for (GuideV2.Show sh : l) {
            if (sh.start <= t && sh.end > t) return sh;
            if (sh.start > t && after == null) after = sh;
        }
        return after;
    }

    boolean move(int dx, int dy) {
        if (channels.isEmpty()) return dx >= 0;
        if (dy != 0) {
            int n = chan + dy;
            if (n < 0 || n >= channels.size()) return true;
            chan = n;
            if (chan < top) top = chan;
            if (chan >= top + ROWS) top = chan - ROWS + 1;
            sel = showAt(chan, Math.max(at, ws));
            return true;
        }
        List<GuideV2.Show> l = listFor(chan);
        if (l == null || sel == null) return dx > 0;
        int i = l.indexOf(sel);
        long floor = System.currentTimeMillis() - System.currentTimeMillis() % HALF;
        if (dx > 0) {
            if (i < 0 || i + 1 >= l.size()) return true;
            sel = l.get(i + 1);
            at = sel.start + 1;
            while (sel.start >= ws + WINDOW - HALF) ws += HALF;
            return true;
        }
        if (i <= 0) return false;
        GuideV2.Show prev = l.get(i - 1);
        if (prev.end <= floor) return false;
        sel = prev;
        at = Math.max(prev.start, floor) + 1;
        while (sel.start < ws && ws > floor) ws -= HALF;
        return true;
    }

    void ok() {
        if (chan >= 0 && chan < channels.size()) host.tune(channels.get(chan));
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    void paint(boolean focus) {
        if (channels.isEmpty()) return;
        if (top != drawnTop || ws != drawnWs || version != drawnVersion) draw();
        for (Map.Entry<GuideV2.Show, FrameLayout> e : blocks.entrySet()) {
            boolean f = focus && e.getKey() == sel;
            boolean on = e.getKey().start <= System.currentTimeMillis() && e.getKey().end > System.currentTimeMillis();
            FrameLayout b = e.getValue();
            b.setBackground(s.fill(12, f ? SkinSwip.ORANGE : on ? SkinSwip.PANEL2 : SkinSwip.PANEL));
            LinearLayout col = (LinearLayout) b.getChildAt(0);
            ((TextView) col.getChildAt(0)).setTextColor(f ? 0xD9FFFFFF : SkinSwip.TEXT3);
            ((TextView) col.getChildAt(1)).setTextColor(f ? 0xFFFFFFFF : SkinSwip.TEXT);
            b.animate().scaleX(f ? 1.03f : 1f).scaleY(f ? 1.03f : 1f).setDuration(host.lite() ? 0 : 180).start();
            b.setElevation(f ? s.px(10) : 0);
        }
        info();
    }

    private void draw() {
        drawnTop = top;
        drawnWs = ws;
        drawnVersion = version;
        blocks.clear();
        rowsBox.removeAllViews();
        // The times along the top, half an hour apart.
        for (int i = times.getChildCount() - 1; i >= 1; i--) times.removeViewAt(i);
        for (int m = 0; m <= 150; m += 30) {
            TextView t = s.text(18, host.semi(), SkinSwip.TEXT2);
            t.setText(Skin.hm(ws + m * 60_000L));
            s.add(times, t, LINE_X + m * PX, 8, 120, 26);
        }
        placeNow();
        long we = ws + WINDOW;
        for (int r = 0; r < ROWS && top + r < channels.size(); r++) {
            String[] ch = channels.get(top + r);
            int y = r * ROW_H;
            // The channel: its logo on its colour, and its name.
            LinearLayout cell = new LinearLayout(s.c);
            cell.setOrientation(LinearLayout.HORIZONTAL);
            cell.setGravity(Gravity.CENTER_VERTICAL);
            cell.setPadding(s.px(16), 0, s.px(12), 0);
            cell.setBackground(s.fill(12, top + r == chan ? SkinSwip.PANEL2 : SkinSwip.PANEL));
            FrameLayout tile = new FrameLayout(s.c);
            // On a black tile (ChannelTiles), else on the channel's colour.
            boolean black = ChannelTiles.on(s.c);
            int ground = black ? ChannelTiles.GROUND : SkinSwip.channelColour(ch[1]);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{ground, black ? ground : darker(ground)});
            g.setCornerRadius(s.px(10));
            tile.setBackground(g);
            String logo = black ? ChannelTiles.url(host.channelLogo(ch[0])) : host.channelLogo(ch[0]);
            if (logo != null) {
                ImageView lv = new ImageView(s.c);
                lv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
                lp.setMargins(s.px(5), s.px(8), s.px(5), s.px(8));
                tile.addView(lv, lp);
                host.image(lv, logo, s.px(120));
            } else {
                TextView t = s.text(14, host.display(), 0xFFFFFFFF);
                t.setGravity(Gravity.CENTER);
                t.setText(SkinSwip.monogram(SkinSwip.shortName(ch[1])));
                tile.addView(t, new FrameLayout.LayoutParams(-1, -1));
            }
            cell.addView(tile, new LinearLayout.LayoutParams(s.px(52), s.px(52)));
            TextView name = s.text(17, host.semi(), SkinSwip.TEXT);
            name.setSingleLine(false);
            name.setMaxLines(2);
            name.setText(ch[1]);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
            nl.leftMargin = s.px(14);
            cell.addView(name, nl);
            s.add(rowsBox, cell, 0, y + 6, 236, 76);
            // Its programmes in the window.
            List<GuideV2.Show> l = shows.get(ch[0]);
            if (l == null) {
                TextView wait = s.text(17, host.regular(), SkinSwip.TEXT3);
                wait.setText(readAt > 0 ? "Reading the guide…" : "");
                s.add(rowsBox, wait, LINE_X + 16, y + 34, 600, 24);
                continue;
            }
            for (GuideV2.Show sh : l) {
                if (sh.end <= ws || sh.start >= we) continue;
                long a = Math.max(sh.start, ws), b = Math.min(sh.end, we);
                float x = LINE_X + (a - ws) / 60_000f * PX;
                float w = (b - a) / 60_000f * PX - 8;
                if (w < 24) continue;
                FrameLayout block = new FrameLayout(s.c);
                block.setBackground(s.fill(12, SkinSwip.PANEL));
                block.setPivotX(0);
                LinearLayout col = new LinearLayout(s.c);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setPadding(s.px(w > 80 ? 16 : 8), s.px(11), s.px(10), 0);
                TextView tm = s.text(15, host.regular(), SkinSwip.TEXT3);
                tm.setText(Skin.hm(sh.start) + " – " + Skin.hm(sh.end));
                col.addView(tm);
                TextView tt = s.text(19, host.semi(), SkinSwip.TEXT);
                tt.setText(sh.title);
                LinearLayout.LayoutParams ttl = new LinearLayout.LayoutParams(-1, -2);
                ttl.topMargin = s.px(5);
                col.addView(tt, ttl);
                block.addView(col, new FrameLayout.LayoutParams(-1, -1));
                s.add(rowsBox, block, x, y + 6, w, 76);
                blocks.put(sh, block);
            }
        }
    }

    private static int darker(int c) {
        return 0xFF000000 | (((c >> 16 & 0xFF) * 55 / 100) << 16) | (((c >> 8 & 0xFF) * 55 / 100) << 8) | ((c & 0xFF) * 55 / 100);
    }

    private void placeNow() {
        long now = System.currentTimeMillis();
        float x = LINE_X + (now - ws) / 60_000f * PX;
        boolean in = now >= ws && now < ws + WINDOW;
        nowLine.setVisibility(in ? View.VISIBLE : View.GONE);
        nowDot.setVisibility(in ? View.VISIBLE : View.GONE);
        nowLine.setTranslationX(s.px(x));
        nowDot.setTranslationX(s.px(x - 5));
    }

    /** The top: the focused programme, its channel and times, what is next, its picture. */
    private void info() {
        if (chan >= channels.size()) return;
        String[] ch = channels.get(chan);
        long now = System.currentTimeMillis();
        chip.setText(SkinSwip.shortName(ch[1]).toUpperCase(Locale.ROOT));
        GuideV2.Show sh = sel;
        if (sh == null) {
            title.setText(ch[1]);
            line.setText(shows.containsKey(ch[0]) ? "Nothing listed" : "Reading the guide…");
            desc.setText("");
            next.setText("");
            hint.setText("OK  Watch " + SkinSwip.shortName(ch[1]));
            showPip(null, ch);
            return;
        }
        boolean on = sh.start <= now && sh.end > now;
        title.setText(sh.title);
        String t = ch[1] + " · " + Skin.hm(sh.start) + " – " + Skin.hm(sh.end);
        if (on) t += " · " + Math.max(1, (sh.end - now) / 60_000L) + " min left";
        else if (sh.start > now) t += " · in " + span((sh.start - now) / 60_000L);
        line.setText(t);
        desc.setText(sh.text == null ? "" : sh.text);
        hint.setText(on ? "OK  Watch live" : "OK  Watch " + SkinSwip.shortName(ch[1]) + " now");
        List<GuideV2.Show> l = shows.get(ch[0]);
        int i = l == null ? -1 : l.indexOf(sh);
        GuideV2.Show nx = l != null && i >= 0 && i + 1 < l.size() ? l.get(i + 1) : null;
        next.setText(nx == null ? "" : "Next on " + SkinSwip.shortName(ch[1]) + ": " + Skin.hm(nx.start) + " " + nx.title);
        showPip(sh, ch);
    }

    private static String span(long min) {
        return min >= 60 ? min / 60 + " h " + (min % 60 > 0 ? min % 60 + " min" : "") : min + " min";
    }

    private Object pipFor;

    private void showPip(GuideV2.Show sh, String[] ch) {
        Object key = sh != null ? sh : ch;
        if (key == pipFor) return;
        pipFor = key;
        if (pipPanel != null) { pipBox.removeView(pipPanel); pipPanel = null; }
        if (pipBar != null) { pipBox.removeView(pipBar); pipBar = null; }
        long now = System.currentTimeMillis();
        if (sh != null && sh.picture != null) {
            // The channel's panel until this programme's picture comes (at once when it is cached), not the last
            // programme's picture under the new label; it stays if the picture never comes.
            pip.setImageBitmap(null);
            View panel = s.channelPanel(ch[1], 18, 90, false);
            pipPanel = panel;
            pipBox.addView(panel, 1, new FrameLayout.LayoutParams(-1, -1));
            host.image(pip, sh.picture, s.px(480), () -> {
                if (pipPanel != panel) return;
                pipBox.removeView(panel);
                pipPanel = null;
            });
        } else {
            pip.setImageBitmap(null);
            pipPanel = s.channelPanel(ch[1], 18, 90, false);
            pipBox.addView(pipPanel, 1, new FrameLayout.LayoutParams(-1, -1));
        }
        boolean on = sh != null && sh.start <= now && sh.end > now;
        if (on) {
            pipBar = s.bar((now - sh.start) / (float) Math.max(1, sh.end - sh.start));
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-1, s.px(5), Gravity.BOTTOM);
            bl.leftMargin = s.px(16);
            bl.rightMargin = s.px(16);
            bl.bottomMargin = s.px(44);
            pipBox.addView(pipBar, bl);
        }
        pipLabel.setText(on ? "Live on " + SkinSwip.shortName(ch[1]) : sh != null ? "Later on " + SkinSwip.shortName(ch[1]) : SkinSwip.shortName(ch[1]));
    }
}
