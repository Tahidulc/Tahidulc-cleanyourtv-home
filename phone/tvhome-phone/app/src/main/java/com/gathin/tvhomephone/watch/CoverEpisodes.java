package com.gathin.tvhomephone.watch;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.glass.Glass;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A series' seasons and episodes on its cover page, as the reference's phone has them: a frosted chip naming the season
 * ("Season 9 ⌄") that opens the list of seasons, then each episode as a card: its still under the glass rim, "1. Its
 * name", its length and the day it aired, and two lines of what happens (side by side on a wide screen). The one to
 * watch next is marked Up next, with how far in when it was left part-way. One the family's playlists don't have is
 * dimmed and says so when tapped; the rest play from the playlists. Held, a card shows all of what happens.
 */
final class CoverEpisodes {
    /** What the page does for it. */
    interface Host {
        /** Whether the family's playlists have that episode: null while they haven't been looked in yet. */
        Boolean has(int season, int number);

        /** Plays it, from the playlists only. */
        void play(int season, int number);

        /** Says the playlists don't have it. */
        void missing(int season, int number);

        /** Opens a menu of choices from the anchor (the list of seasons). */
        void choose(View anchor, List<CoverMenu.Entry> entries);
    }

    /** A long season shows this many (or to a little past the one up next) until asked for all. */
    private static final int FIRST = 30;

    final LinearLayout view;
    private final Activity a;
    private final Title t;
    private final Host host;
    private final List<int[]> seasons;
    private final TextView chip;
    private final LinearLayout list;
    private final boolean across;
    private final int side;
    private final List<Card> cards = new ArrayList<>();
    private int shown = -1, loads, upSeason = 1, upEpisode = 1;
    private float upProgress;

    /** One episode's card, and what changes on it. */
    private static final class Card {
        final int season, number;
        final boolean aired;
        final LocalDate air;
        final String name;
        final View box, still, words;
        final TextView badge;
        final CoverViews.Line line;

        Card(int season, int number, boolean aired, LocalDate air, String name, View box, View still, View words, TextView badge,
             CoverViews.Line line) {
            this.season = season;
            this.number = number;
            this.aired = aired;
            this.air = air;
            this.name = name;
            this.box = box;
            this.still = still;
            this.words = words;
            this.badge = badge;
            this.line = line;
        }
    }

    /**
     * The seasons and episodes, opening on the season of the episode up next (else season 1, else the first there is);
     * null when TMDB lists no seasons. across: the cards' still and words side by side (a wide screen).
     */
    static CoverEpisodes make(Activity a, Title t, JSONObject details, Host host, boolean across, int side, int upSeason,
                              int upEpisode, float upProgress) {
        List<int[]> seasons = CoverText.seasons(details);
        if (seasons.isEmpty()) return null;
        CoverEpisodes e = new CoverEpisodes(a, t, seasons, host, across, side);
        e.upSeason = upSeason;
        e.upEpisode = upEpisode;
        e.upProgress = upProgress;
        e.load(CoverText.opening(seasons, upSeason));
        return e;
    }

    private CoverEpisodes(Activity a, Title t, List<int[]> seasons, Host host, boolean across, int side) {
        this.a = a;
        this.t = t;
        this.seasons = seasons;
        this.host = host;
        this.across = across;
        this.side = side;
        view = new LinearLayout(a);
        view.setOrientation(LinearLayout.VERTICAL);
        chip = CoverViews.chip(a, CoverText.season(seasons.get(0)[0]));
        chip.setOnClickListener(v -> pickSeason());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.setMargins(side, Glass.dp(a, 28), side, Glass.dp(a, 18));
        view.addView(chip, cp);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        view.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /** The episode up next (and how far into it, 0 to 1): its card marked. */
    void setUpNext(int season, int episode, float progress) {
        upSeason = season;
        upEpisode = episode;
        upProgress = progress;
        paintUpNext();
    }

    /** The playlists looked in: the episodes they don't have dimmed (and those not out yet). */
    void mark() {
        for (Card c : cards) {
            boolean dim = !c.aired || Boolean.FALSE.equals(host.has(c.season, c.number));
            float al = dim ? 0.42f : 1f;
            c.still.setAlpha(al);
            c.words.setAlpha(al);
        }
    }

    private void pickSeason() {
        List<CoverMenu.Entry> items = new ArrayList<>();
        for (int[] s : seasons) {
            int n = s[0];
            String note = s[1] > 0 ? s[1] + (s[1] == 1 ? " episode" : " episodes") : null;
            items.add(new CoverMenu.Entry(CoverText.season(n), note, null, n == shown, () -> {
                if (n != shown) load(n);
            }));
        }
        host.choose(chip, items);
    }

    /** A season's episodes, read from TMDB off the main thread; the chip names it at once. */
    private void load(int n) {
        shown = n;
        chip.setText(CoverText.season(n));
        chip.setContentDescription(CoverText.season(n) + ", choose another season");
        cards.clear();
        list.removeAllViews();
        list.addView(note("Loading the episodes…"));
        int gen = ++loads;
        Context app = a.getApplicationContext();
        App.io.execute(() -> {
            JSONObject got = null;
            try {
                got = Tmdb.get(app, "tv/" + t.id + "/season/" + n);
            } catch (IOException ignored) {
                // said on the page
            }
            JSONObject season = got;
            App.main.post(() -> {
                if (a.isFinishing() || a.isDestroyed() || gen != loads) return;
                draw(n, season);
            });
        });
    }

    private TextView note(String words) {
        TextView w = Glass.text(a, words, 14, Glass.FAINT, Glass.body(a));
        w.setPadding(side, Glass.dp(a, 6), side, Glass.dp(a, 10));
        return w;
    }

    private void draw(int n, JSONObject season) {
        list.removeAllViews();
        cards.clear();
        JSONArray eps = season == null ? null : season.optJSONArray("episodes");
        if (eps == null || eps.length() == 0) {
            TextView none = note(season == null ? "Couldn't load " + CoverText.season(n) + " right now. Tap to try again."
                    : "No episodes listed for " + CoverText.season(n) + " yet.");
            if (season == null) none.setOnClickListener(v -> load(n));
            list.addView(none);
            return;
        }
        LocalDate today = LocalDate.now();
        int count = eps.length(), up = -1;
        for (int i = 0; i < count && n == upSeason; i++) {
            JSONObject e = eps.optJSONObject(i);
            if (e != null && e.optInt("episode_number") == upEpisode) up = i;
        }
        int show = count <= FIRST + 6 ? count : Math.min(count, Math.max(FIRST, up + 6));
        List<View> fresh = new ArrayList<>();
        for (int i = 0; i < show; i++) {
            JSONObject e = eps.optJSONObject(i);
            if (e == null) continue;
            View v = card(e, n, today);
            list.addView(v, cardPlace());
            if (fresh.size() < 6) fresh.add(v);
        }
        if (show < count) {
            TextView all = CoverViews.chip(a, "Show all " + count + " episodes");
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ap.gravity = Gravity.CENTER_HORIZONTAL;
            ap.setMargins(side, Glass.dp(a, 2), side, Glass.dp(a, 8));
            list.addView(all, ap);
            int from = show;
            all.setOnClickListener(v -> {
                list.removeView(all);
                for (int i = from; i < count; i++) {
                    JSONObject e = eps.optJSONObject(i);
                    if (e != null) list.addView(card(e, n, today), cardPlace());
                }
                mark();
                paintUpNext();
            });
        }
        mark();
        paintUpNext();
        CoverViews.rise(fresh);
    }

    private LinearLayout.LayoutParams cardPlace() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(side, 0, side, Glass.dp(a, across ? 20 : 26));
        return lp;
    }

    private View card(JSONObject e, int season, LocalDate today) {
        int n = e.optInt("episode_number");
        String name = CoverText.episode(n, e.optString("name", ""));
        LocalDate air = CoverText.day(e.optString("air_date", ""));
        boolean aired = air != null && !air.isAfter(today);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(across ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        // The still: a widescreen picture under the glass rim, the series' own colours until it comes.
        CoverViews.Wide still = new CoverViews.Wide(a);
        float r = Glass.dp(a, 14);
        still.setBackground(Glass.placeholder(t.title + " " + season, r));
        Glass.round(still, r);
        still.setForeground(Glass.rim(a, 14));
        ImageView pic = new ImageView(a);
        pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
        pic.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        still.addView(pic, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        String url = Tmdb.image(e.optString("still_path", null), "w500");
        try {
            // At its own size, cropped as it is drawn: a long season's stills stay small in memory.
            if (url != null) Glide.with(pic).load(url).dontTransform().into(pic);
        } catch (RuntimeException ignored) {
            // the page went away
        }
        TextView badge = Glass.text(a, "Up next", 11, CoverViews.INK, Glass.bodySemi(a));
        badge.setBackground(CoverViews.pill(a, CoverViews.WHITE));
        badge.setPadding(Glass.dp(a, 9), Glass.dp(a, 4), Glass.dp(a, 9), Glass.dp(a, 4));
        badge.setVisibility(View.GONE);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        bp.setMargins(Glass.dp(a, 10), Glass.dp(a, 10), 0, 0);
        still.addView(badge, bp);
        CoverViews.Line line = new CoverViews.Line(a);
        line.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, Glass.dp(a, 4), Gravity.BOTTOM);
        lp.setMargins(Glass.dp(a, 12), 0, Glass.dp(a, 12), Glass.dp(a, 10));
        still.addView(line, lp);
        box.addView(still, across ? new LinearLayout.LayoutParams(Glass.dp(a, 300), LinearLayout.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // The words: its number and name, its length and day, a little of what happens.
        LinearLayout words = new LinearLayout(a);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView title = Glass.text(a, name, 16, Glass.TEXT, Glass.heading(a, 500));
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(title);
        String info = info(e, air, aired);
        if (!info.isEmpty()) {
            TextView i = Glass.text(a, info, 13, Glass.FAINT, Glass.body(a));
            i.setSingleLine(true);
            i.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ip.topMargin = Glass.dp(a, 5);
            words.addView(i, ip);
        }
        String about = e.optString("overview", "").trim();
        if (!about.isEmpty()) {
            TextView o = Glass.text(a, about, 14, Glass.MUTED, Glass.body(a));
            o.setMaxLines(across ? 3 : 2);
            o.setEllipsize(TextUtils.TruncateAt.END);
            o.setLineSpacing(0, 1.2f);
            LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            op.topMargin = Glass.dp(a, 7);
            words.addView(o, op);
        }
        LinearLayout.LayoutParams wp;
        if (across) {
            wp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            wp.leftMargin = Glass.dp(a, 18);
            wp.gravity = Gravity.CENTER_VERTICAL;
        } else {
            wp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            wp.topMargin = Glass.dp(a, 12);
        }
        box.addView(words, wp);

        Card card = new Card(season, n, aired, air, name, box, still, words, badge, line);
        box.setClickable(true);
        box.setFocusable(true);
        box.setOnClickListener(v -> tapped(card));
        box.setOnLongClickListener(v -> {
            more(card, about, info);
            return true;
        });
        CoverViews.press(box, 0.98f);
        describe(card, false);
        cards.add(card);
        return box;
    }

    /** "22mins • Sep 26, 2002"; "Out Dec 16, 2026" for one still to come. */
    private static String info(JSONObject e, LocalDate air, boolean aired) {
        List<String> parts = new ArrayList<>();
        String len = CoverText.length(e.optInt("runtime"));
        if (!len.isEmpty()) parts.add(len);
        if (air == null) parts.add("Date to be announced");
        else parts.add(aired ? CoverText.date(air) : "Out " + CoverText.date(air));
        return String.join(CoverText.DOT, parts);
    }

    private void tapped(Card c) {
        if (!c.aired) {
            Toast.makeText(a, c.air == null ? "Not out yet" : "Out " + CoverText.date(c.air), Toast.LENGTH_SHORT).show();
            return;
        }
        if (Boolean.FALSE.equals(host.has(c.season, c.number))) host.missing(c.season, c.number);
        else host.play(c.season, c.number);
    }

    /** All of what happens in it, with Play when the playlists may have it. */
    private void more(Card c, String about, String info) {
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(a)
                .setTitle((c.season > 0 ? "S" + c.season + " " : "") + "E" + c.number + " · " + c.name.replaceFirst("^\\d+\\. ", ""))
                .setMessage(about.isEmpty() ? info : about + "\n\n" + info);
        if (c.aired && !Boolean.FALSE.equals(host.has(c.season, c.number))) {
            b.setPositiveButton("Play", (d, w) -> host.play(c.season, c.number));
        }
        b.setNegativeButton("Close", null).show();
    }

    private void paintUpNext() {
        for (Card c : cards) {
            boolean up = c.season == upSeason && c.number == upEpisode;
            c.badge.setVisibility(up ? View.VISIBLE : View.GONE);
            boolean part = up && upProgress > 0;
            c.line.setVisibility(part ? View.VISIBLE : View.GONE);
            if (part) c.line.set(upProgress);
            describe(c, up);
        }
    }

    private static void describe(Card c, boolean up) {
        c.box.setContentDescription((up ? "Up next: " : "") + c.name + (c.aired ? "" : ", not out yet"));
    }
}
