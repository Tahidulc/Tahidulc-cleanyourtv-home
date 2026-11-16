package com.gathin.tvhomephone.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.live.FanFeed;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Home's fans' feed: one frosted card of what fans of the starred clubs are posting now, newest on top. Each post shows
 * its author's initials on a colour of their own (no profile pictures are read), their name, how long ago, the words
 * (four lines at most) and where it is from, as words ("Bluesky", "Reddit · r/reddevils"). Posts that come later drop
 * in at the top one at a time, a couple of seconds apart, the others sliding down to make room; a "2 new" tag shows
 * while they come. A tap opens the post on its own site.
 */
final class SocialCard {
    interface OnPost {
        void open(FanFeed.Item p);
    }

    /** Posts on the card at once. */
    static final int MAX = 5;
    /** Between one waiting post dropping in and the next. */
    private static final long DRIP_MS = 2_600L;
    /** The sources' words, each in a colour of its own (words only: no logos). */
    static final int BLUESKY = 0xFF7DB9FF, REDDIT = 0xFFFF8A5C;
    /** The initials' colours: quiet, by the author. */
    private static final int[] FACES = {0xFF5B4BB7, 0xFF2F7D6D, 0xFFB5542F, 0xFF3F6CB4, 0xFF8C3F78, 0xFF6F7A2E,
            0xFF2E6F98, 0xFF9A4A4A};

    private final Context c;
    private final OnPost tap;
    /** The card, for its section. */
    final LinearLayout view;
    private final LinearLayout list;
    private final TextView about, fresh;
    private final View dot;
    private final List<Row> rows = new ArrayList<>();
    /** Posts that came after the card filled, oldest first, dropping in one at a time. */
    private final ArrayDeque<FanFeed.Item> waiting = new ArrayDeque<>();
    private final Runnable drip = this::dropNext;
    private final Runnable freshAway;
    private boolean dripping;

    /** One post on the card. */
    private final class Row {
        final FanFeed.Item item;
        final LinearLayout view;
        final View line;
        final TextView when;

        Row(FanFeed.Item p) {
            item = p;
            view = new LinearLayout(c);
            view.setOrientation(LinearLayout.VERTICAL);
            line = new View(c);
            line.setBackgroundColor(0x1AFFFFFF);
            view.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, Glass.dp(c, 0.7f))));

            LinearLayout body = new LinearLayout(c);
            body.setOrientation(LinearLayout.HORIZONTAL);
            body.setPadding(0, Glass.dp(c, 12), 0, Glass.dp(c, 12));
            TextView face = Glass.text(c, initials(p.name), 13, 0xFFFFFFFF, Type.interSemi(c));
            face.setGravity(Gravity.CENTER);
            GradientDrawable round = new GradientDrawable();
            round.setShape(GradientDrawable.OVAL);
            round.setColor(face(p.handle.isEmpty() ? p.name : p.handle));
            face.setBackground(round);
            face.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int s = Glass.dp(c, 36);
            body.addView(face, new LinearLayout.LayoutParams(s, s));

            LinearLayout words = new LinearLayout(c);
            words.setOrientation(LinearLayout.VERTICAL);
            LinearLayout top = new LinearLayout(c);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = Glass.text(c, p.name, 14, Glass.TEXT, Type.interSemi(c));
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            top.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            when = Glass.text(c, FanFeed.ago(p.at, System.currentTimeMillis()), 12, Glass.FAINT, Glass.body(c));
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-2, -2);
            wp.leftMargin = Glass.dp(c, 8);
            top.addView(when, wp);
            words.addView(top, new LinearLayout.LayoutParams(-1, -2));
            TextView text = Glass.text(c, p.text, 14, Glass.SOFT, Glass.body(c));
            text.setMaxLines(4);
            text.setEllipsize(TextUtils.TruncateAt.END);
            text.setLineSpacing(Glass.dp(c, 3), 1f);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
            tp.topMargin = Glass.dp(c, 5);
            words.addView(text, tp);
            TextView from = Glass.text(c, from(p), 12, Glass.FAINT, Glass.body(c));
            from.setSingleLine(true);
            from.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
            fp.topMargin = Glass.dp(c, 7);
            words.addView(from, fp);
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1f);
            wl.leftMargin = Glass.dp(c, 12);
            body.addView(words, wl);
            view.addView(body, new LinearLayout.LayoutParams(-1, -2));

            view.setContentDescription(p.name + " on " + p.source + (p.where.isEmpty() ? "" : ", " + p.where) + ": " + p.text);
            if (!p.link.isEmpty()) {
                view.setOnClickListener(v -> tap.open(p));
                GradientDrawable mask = Art.round(0xFFFFFFFF, Glass.dp(c, 12));
                view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x24FFFFFF), null, mask));
            }
        }
    }

    SocialCard(Context c, OnPost tap) {
        this.c = c;
        this.tap = tap;
        view = new LinearLayout(c);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackground(Glass.card(c, 20));
        Glass.round(view, Glass.dp(c, 20));
        int side = Glass.dp(c, 16);
        view.setPadding(side, Glass.dp(c, 14), side, Glass.dp(c, 4));

        // The clubs it is about, with a dot that pulses as a post drops in, and the "2 new" tag while they come.
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        dot = new View(c);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Art.SPORT);
        dot.setBackground(d);
        int ds = Glass.dp(c, 7);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ds, ds);
        dp.rightMargin = Glass.dp(c, 8);
        head.addView(dot, dp);
        about = Glass.text(c, "", 11, Glass.MUTED, Type.interSemi(c));
        about.setLetterSpacing(0.1f);
        about.setSingleLine(true);
        about.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(about, new LinearLayout.LayoutParams(0, -2, 1f));
        fresh = Glass.text(c, "", 11, Glass.TEXT, Type.interSemi(c));
        fresh.setPadding(Glass.dp(c, 9), Glass.dp(c, 4), Glass.dp(c, 9), Glass.dp(c, 4));
        fresh.setBackground(Art.round(0x2EFFFFFF, Glass.dp(c, 11)));
        fresh.setAlpha(0f);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2);
        np.leftMargin = Glass.dp(c, 8);
        head.addView(fresh, np);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.bottomMargin = Glass.dp(c, 6);
        view.addView(head, hp);

        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        view.addView(list, new LinearLayout.LayoutParams(-1, -2));

        TextView foot = Glass.text(c, "Filtered for the family · tap a post to open it", 11, Glass.FAINT, Glass.body(c));
        foot.setPadding(0, Glass.dp(c, 2), 0, Glass.dp(c, 10));
        view.addView(foot, new LinearLayout.LayoutParams(-1, -2));
        freshAway = () -> fresh.animate().alpha(0f).setStartDelay(0).setDuration(400).start();
    }

    /**
     * The posts, newest first, and the clubs they are about. The first ever drop in together, one after another; after
     * that, only posts newer than the top one are taken, and they drop in at the top one at a time. None: the card
     * empties (its section is hidden).
     */
    void set(List<FanFeed.Item> items, List<String> clubs) {
        about.setText(clubs == null || clubs.isEmpty() ? "FANS RIGHT NOW"
                : String.join(" · ", clubs).toUpperCase(Locale.UK));
        if (items == null || items.isEmpty()) {
            view.removeCallbacks(drip);
            dripping = false;
            waiting.clear();
            rows.clear();
            list.removeAllViews();
            return;
        }
        if (rows.isEmpty()) {
            fill(items);
            return;
        }
        Set<String> known = new HashSet<>();
        for (Row r : rows) known.add(r.item.key());
        for (FanFeed.Item p : waiting) known.add(p.key());
        long top = newest();
        List<FanFeed.Item> come = later(items, known, top);
        if (come.isEmpty()) return;
        // Oldest first, so the newest drops in last and ends on top; no more than the card holds.
        for (int i = Math.min(come.size(), MAX) - 1; i >= 0; i--) waiting.addLast(come.get(i));
        while (waiting.size() > MAX) waiting.pollFirst();
        showFresh(waiting.size());
        if (!dripping) {
            dripping = true;
            view.post(drip);
        }
    }

    /** How long ago each was, again (the clock's tick). */
    void tick(long now) {
        for (Row r : rows) r.when.setText(FanFeed.ago(r.item.at, now));
    }

    // ---- dropping in ------------------------------------------------------------------------------------------------

    /** The first posts: each drops in a little after the one above it. */
    private void fill(List<FanFeed.Item> items) {
        list.removeAllViews();
        rows.clear();
        for (int i = 0; i < items.size() && i < MAX; i++) {
            Row r = new Row(items.get(i));
            rows.add(r);
            list.addView(r.view, new LinearLayout.LayoutParams(-1, -2));
            drop(r.view, i * 90L);
        }
        lines();
    }

    /** One waiting post in at the top; the others slide down; the bottom one goes when the card is full. */
    private void dropNext() {
        FanFeed.Item p = waiting.pollFirst();
        if (p == null) {
            dripping = false;
            view.removeCallbacks(freshAway);
            view.postDelayed(freshAway, 2_500L);
            return;
        }
        Map<Row, Integer> before = new HashMap<>();
        boolean laid = list.isLaidOut();
        for (Row r : rows) before.put(r, r.view.getTop());
        Row row = new Row(p);
        rows.add(0, row);
        list.addView(row.view, 0, new LinearLayout.LayoutParams(-1, -2));
        while (rows.size() > MAX) {
            Row gone = rows.remove(rows.size() - 1);
            list.removeView(gone.view);
        }
        lines();
        if (laid) {
            // Once laid out again: each one that was there starts where it was and slides to its new place.
            list.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    list.getViewTreeObserver().removeOnPreDrawListener(this);
                    for (Row r : rows) {
                        Integer was = before.get(r);
                        if (was == null) continue;
                        float dy = was - r.view.getTop();
                        if (dy == 0f) continue;
                        r.view.setTranslationY(dy);
                        r.view.animate().translationY(0f).setStartDelay(0).setDuration(460)
                                .setInterpolator(new DecelerateInterpolator(2f)).start();
                    }
                    return true;
                }
            });
        }
        drop(row.view, 0);
        pulse();
        if (waiting.isEmpty()) {
            dripping = false;
            view.removeCallbacks(freshAway);
            view.postDelayed(freshAway, 3_000L);
        } else {
            view.postDelayed(drip, DRIP_MS);
        }
    }

    /** A post dropping into place: from a little above, fading in, settling with a small bounce. */
    private void drop(View v, long delay) {
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(-Glass.dp(c, 28));
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(520)
                .setInterpolator(new OvershootInterpolator(1.2f)).start();
    }

    /** The dot swells and settles, twice. */
    private void pulse() {
        dot.animate().cancel();
        dot.setScaleX(1f);
        dot.setScaleY(1f);
        dot.animate().scaleX(1.9f).scaleY(1.9f).setStartDelay(0).setDuration(220).setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> dot.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(320)
                        .setInterpolator(new OvershootInterpolator(2f)).start()).start();
    }

    private void showFresh(int n) {
        if (n <= 0) return;
        view.removeCallbacks(freshAway);
        fresh.setText(n == 1 ? "1 new" : n + " new");
        fresh.animate().cancel();
        fresh.animate().alpha(1f).setStartDelay(0).setDuration(240).start();
    }

    /** A line above each post but the first. */
    private void lines() {
        for (int i = 0; i < rows.size(); i++) rows.get(i).line.setVisibility(i == 0 ? View.GONE : View.VISIBLE);
    }

    private long newest() {
        long top = 0;
        for (Row r : rows) top = Math.max(top, r.item.at);
        for (FanFeed.Item p : waiting) top = Math.max(top, p.at);
        return top;
    }

    // ---- pure ---------------------------------------------------------------------------------------------------------

    /**
     * Pure: the posts to drop in, newest first: not on the card (or waiting) already, and newer than the newest there
     * (older ones would belong lower down, under posts already read).
     */
    static List<FanFeed.Item> later(List<FanFeed.Item> items, Set<String> known, long newest) {
        List<FanFeed.Item> out = new ArrayList<>();
        for (FanFeed.Item p : items) if (!known.contains(p.key()) && p.at > newest) out.add(p);
        return out;
    }

    /** Pure: an author's initials: the first letters of a name's first two words ("Red Devil Dan" RD), else its first. */
    static String initials(String name) {
        String n = name == null ? "" : name.trim();
        if (n.startsWith("u/")) n = n.substring(2);
        if (n.startsWith("@")) n = n.substring(1);
        StringBuilder b = new StringBuilder();
        String[] words = n.split("\\s+");
        for (String w : words) {
            int i = 0;
            while (i < w.length() && !Character.isLetterOrDigit(w.codePointAt(i))) i += Character.charCount(w.codePointAt(i));
            if (i >= w.length()) continue;
            b.appendCodePoint(Character.toUpperCase(w.codePointAt(i)));
            if (b.codePointCount(0, b.length()) >= 2 || words.length == 1) break;
        }
        return b.length() == 0 ? "?" : b.toString();
    }

    /** Pure: an author's colour (the same author, the same colour). */
    static int face(String who) {
        return FACES[Math.floorMod(who == null ? 0 : who.toLowerCase(Locale.ROOT).hashCode(), FACES.length)];
    }

    /** Where it is from, in words, the source's in its colour: "Bluesky · @fan.bsky.social", "Reddit · r/reddevils". */
    static CharSequence from(FanFeed.Item p) {
        SpannableStringBuilder b = new SpannableStringBuilder(p.source);
        b.setSpan(new ForegroundColorSpan(p.bluesky() ? BLUESKY : REDDIT), 0, p.source.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        String more = p.bluesky() ? p.handle : p.where;
        if (!more.isEmpty()) b.append("  ·  ").append(more);
        return b;
    }
}
