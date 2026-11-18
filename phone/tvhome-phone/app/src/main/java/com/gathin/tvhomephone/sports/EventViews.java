package com.gathin.tvhomephone.sports;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Outline;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.live.AlsoShowing;
import com.gathin.tvhomephone.live.LogoTiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The two parts a fixture's page and an event's page share: "Watch on your channels", every channel in the family's
 * playlists that shows it, each with its logo, its plain name, its 4K mark and whether it is on now (or when it starts),
 * a tap playing it; and "Highlights", a row of clips with their stills, the ESPN ones playing here, the rest opening in
 * YouTube or the browser.
 */
final class EventViews {
    private EventViews() {}

    /** Shown at first; the rest behind "Show all". */
    static final int FIRST = 6;

    /** The card of channels, all of them when all, else the first few and "Show all N". Empty: a line saying so. */
    static View channels(Activity a, List<AlsoShowing.Listed> rows, boolean all, Runnable more) {
        LinearLayout card = Ui.column(a);
        card.setBackground(Ui.glass(a, 18));
        int p = Ui.dp(a, 14);
        card.setPadding(p, Ui.dp(a, 12), p, Ui.dp(a, 8));
        LinearLayout head = Ui.row(a);
        TextView h = Ui.text(a, "WATCH ON YOUR CHANNELS", 12, a.getColor(R.color.text_soft), true);
        h.setLetterSpacing(0.12f);
        head.addView(h, Ui.weight(1));
        if (!rows.isEmpty()) head.addView(Ui.text(a, rows.size() == 1 ? "1 channel" : rows.size() + " channels", 12,
                a.getColor(R.color.text_dim), false));
        card.addView(head, Ui.margins(a, 0, 0, 0, 6));
        if (rows.isEmpty()) {
            TextView none = Ui.text(a, "None of your channels lists it yet. Pull down on Sports later, or look in Live TV.", 14,
                    a.getColor(R.color.text_dim), false);
            none.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 8));
            card.addView(none);
            return card;
        }
        long now = System.currentTimeMillis();
        int n = all ? rows.size() : Math.min(FIRST, rows.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) card.addView(rule(a));
            card.addView(row(a, rows.get(i), now));
        }
        if (rows.size() > FIRST && more != null) {
            TextView t = Ui.text(a, all ? "Show fewer" : "Show all " + rows.size() + " channels  ›", 13.5f, a.getColor(R.color.sports), true);
            t.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 8));
            t.setOnClickListener(v -> more.run());
            card.addView(t);
        }
        return card;
    }

    private static View rule(Context c) {
        View v = new View(c);
        v.setBackgroundColor(0x1AFFFFFF);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(c, 1) / 2)));
        return v;
    }

    /**
     * One channel: its logo on a tile in its own colour (or with nothing behind it, or on black: LogoTiles; initials on a
     * dark tile until it loads, and when it has none), its name, on now or when, and Watch.
     */
    private static View row(Activity a, AlsoShowing.Listed l, long now) {
        LinearLayout r = Ui.row(a);
        r.setPadding(0, Ui.dp(a, 9), 0, Ui.dp(a, 9));
        FrameLayout tile = new FrameLayout(a);
        tile.setBackground(Ui.fill(a, 0xFF0A0C22, 9));
        int pad = LogoTiles.pad(a, 6);
        tile.setPadding(pad, pad, pad, pad);
        ImageView logo = new ImageView(a);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(logo, new FrameLayout.LayoutParams(-1, -1));
        TextView initials = Ui.text(a, initials(l.name), 13, 0xFFFFFFFF, true);
        initials.setGravity(Gravity.CENTER);
        initials.setMaxLines(1);
        tile.addView(initials, new FrameLayout.LayoutParams(-1, -1));
        if (l.uhd) {
            TextView k = Ui.text(a, "4K", 9, 0xFFFFFFFF, true);
            k.setIncludeFontPadding(false);
            k.setPadding(Ui.dp(a, 4), Ui.dp(a, 1), Ui.dp(a, 4), Ui.dp(a, 1));
            k.setBackground(Ui.fill(a, 0xE6000000, 3));
            FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
            kp.setMargins(0, 0, -Ui.dp(a, 3), -Ui.dp(a, 3));
            tile.addView(k, kp);
            tile.setClipChildren(false);
            tile.setClipToPadding(false);
        }
        r.addView(tile, new LinearLayout.LayoutParams(Ui.dp(a, 70), Ui.dp(a, 40)));
        LinearLayout words = Ui.column(a);
        words.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 8), 0);
        TextView name = Ui.text(a, l.name, 15.5f, a.getColor(R.color.text), true);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(name);
        String when = l.live ? "● Live now" : when(l.start, now);
        String why = l.best && !l.live ? "Best match" : "";
        if (!l.under.isEmpty() && !l.under.equalsIgnoreCase(l.name)) why = why.isEmpty() ? l.under : why + " · " + l.under;
        TextView under = Ui.text(a, when + (why.isEmpty() ? "" : (when.isEmpty() ? "" : "  ·  ") + why), 12.5f,
                l.live ? a.getColor(R.color.live_green) : a.getColor(R.color.text_dim), l.live);
        under.setMaxLines(1);
        under.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(under);
        r.addView(words, Ui.weight(1));
        if (l.logo != null && !l.logo.isEmpty()) {
            // Logo's colour: the row (its name and when) in it too, a touch lighter than the tile, its words white.
            initials.setVisibility(View.GONE);
            int underInk = l.live ? a.getColor(R.color.live_green) : a.getColor(R.color.text_dim);
            LogoTiles.show(tile, 9, false, logo, l.logo, LogoTiles.ON_NAVY, cols -> {
                android.graphics.drawable.Drawable ground = LogoTiles.row(a, cols, false, 0xFFFFFFFF, 14);
                r.setBackground(ground);
                int side = ground == null ? 0 : Ui.dp(a, 8);
                r.setPadding(side, Ui.dp(a, 9), side, Ui.dp(a, 9));
                under.setTextColor(ground == null ? underInk : 0xE6FFFFFF);
            }, () -> {
                initials.setVisibility(View.VISIBLE);
                tile.setBackground(Ui.fill(a, 0xFF0A0C22, 9));
                r.setBackground(null);
                r.setPadding(0, Ui.dp(a, 9), 0, Ui.dp(a, 9));
                under.setTextColor(underInk);
            });
        }
        TextView watch = Ui.text(a, "▶  Watch", 13, l.live ? 0xFF06281A : 0xFF0A0D33, true);
        watch.setGravity(Gravity.CENTER);
        watch.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
        watch.setBackground(Ui.fill(a, l.live ? a.getColor(R.color.live_green) : 0xFFFFFFFF, 16));
        watch.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        r.addView(watch);
        r.setContentDescription("Watch on " + l.name + (l.live ? ", live now" : when.isEmpty() ? "" : ", " + when));
        r.setOnClickListener(v -> com.gathin.tvhomephone.live.PlayerActivity.start(a, l.ch.key, com.gathin.tvhomephone.live.Library.ALL));
        return r;
    }

    /** "From 19:45" today, "Sat 15:00" another day this week, "21 Nov 15:00" later; "" without a time. Pure but for the zone. */
    static String when(long start, long now) {
        if (start <= 0) return "";
        ZoneId z = ZoneId.systemDefault();
        ZonedDateTime at = Instant.ofEpochMilli(start).atZone(z);
        LocalDate today = Instant.ofEpochMilli(now).atZone(z).toLocalDate(), day = at.toLocalDate();
        String hm = at.format(DateTimeFormatter.ofPattern("HH:mm"));
        if (start <= now) return "Started " + hm;
        if (day.equals(today)) return "From " + hm;
        if (day.isBefore(today.plusDays(7))) return at.format(DateTimeFormatter.ofPattern("EEE", Locale.UK)) + " " + hm;
        return at.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)) + " " + hm;
    }

    private static String initials(String name) {
        StringBuilder b = new StringBuilder();
        for (String w : (name == null ? "" : name).split("\\s+")) if (!w.isEmpty() && b.length() < 3) b.append(Character.toUpperCase(w.charAt(0)));
        return b.toString();
    }

    // ---- highlights --------------------------------------------------------------------------------------------------

    /** "Highlights": a row of clips, each its still with a play mark, its length, its title and where it is from. */
    static View highlights(Activity a, List<Highlights.Clip> clips) {
        LinearLayout box = Ui.column(a);
        TextView h = Ui.text(a, "HIGHLIGHTS", 12, a.getColor(R.color.text_soft), true);
        h.setLetterSpacing(0.12f);
        box.addView(h, Ui.margins(a, 2, 0, 0, 8));
        HorizontalScrollView scroll = new HorizontalScrollView(a);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout row = Ui.row(a);
        row.setGravity(Gravity.TOP);
        for (int i = 0; i < clips.size(); i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 228), -2);
            if (i > 0) lp.leftMargin = Ui.dp(a, 12);
            row.addView(clip(a, clips.get(i)), lp);
        }
        scroll.addView(row);
        box.addView(scroll, Ui.matchWrap());
        return box;
    }

    private static View clip(Activity a, Highlights.Clip c) {
        LinearLayout col = Ui.column(a);
        FrameLayout pic = new FrameLayout(a);
        pic.setBackground(Ui.fill(a, 0xFF151A40, 14));
        pic.setClipToOutline(true);
        int r = Ui.dp(a, 14);
        pic.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        });
        ImageView still = new ImageView(a);
        still.setScaleType(ImageView.ScaleType.CENTER_CROP);
        still.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        pic.addView(still, new FrameLayout.LayoutParams(-1, -1));
        if (!c.still.isEmpty()) Ui.load(still, c.still, null, null);
        TextView play = Ui.text(a, "▶", 18, 0xFFFFFFFF, true);
        play.setGravity(Gravity.CENTER);
        play.setPadding(Ui.dp(a, 3), 0, 0, 0);
        play.setBackground(Ui.fill(a, 0x99000000, 22));
        pic.addView(play, new FrameLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44), Gravity.CENTER));
        if (c.seconds > 0) {
            TextView len = Ui.text(a, length(c.seconds), 11, 0xFFFFFFFF, true);
            len.setPadding(Ui.dp(a, 6), Ui.dp(a, 2), Ui.dp(a, 6), Ui.dp(a, 2));
            len.setBackground(Ui.fill(a, 0xCC000000, 6));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
            lp.setMargins(0, 0, Ui.dp(a, 8), Ui.dp(a, 8));
            pic.addView(len, lp);
        }
        col.addView(pic, new LinearLayout.LayoutParams(-1, Ui.dp(a, 128)));
        TextView title = Ui.text(a, c.title, 13.5f, a.getColor(R.color.text), true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(title, Ui.margins(a, 2, 8, 2, 0));
        TextView from = Ui.text(a, c.here() ? c.source + " · plays here" : c.source + "  ↗", 11.5f, a.getColor(R.color.text_dim), false);
        from.setMaxLines(1);
        from.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(from, Ui.margins(a, 2, 2, 2, 0));
        col.setContentDescription(c.title + ", " + (c.here() ? "plays here" : "opens " + c.source));
        col.setOnClickListener(v -> open(a, c));
        return col;
    }

    /** A clip played: ESPN's here, the rest by their page (the YouTube app takes YouTube's). */
    static void open(Activity a, Highlights.Clip c) {
        try {
            if (c.here()) a.startActivity(ClipPlayerActivity.intent(a, c.play, c.title));
            else if (!c.link.isEmpty()) a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(c.link)));
        } catch (RuntimeException ignored) {
            // nothing to open it with
        }
    }

    /** "1:58", "12:03": a clip's length. Pure. */
    static String length(int seconds) {
        int s = Math.max(0, seconds);
        return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }
}
