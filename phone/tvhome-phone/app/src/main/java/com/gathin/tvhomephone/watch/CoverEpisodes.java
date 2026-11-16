package com.gathin.tvhomephone.watch;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
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
 * A series' seasons and episodes on its cover page, as the family's recording has them: the show's logo in the middle,
 * the seasons as tabs under it (the one in view in a white pill), then one row of every episode of every season, side
 * by side: each a widescreen still with its corners rounded, "S1 E8", its name, two lines of what happens, and "1h 7m,
 * 9 Oct 2022". As the row is scrolled the white pill slides to the season in view; a season's tab scrolls the row to its
 * first episode. The page opens on the episode up next, marked Up next (with how far in when it was left part-way).
 * One the family's playlists don't have is dimmed and says so when tapped; the rest play from the playlists. Held, a
 * card shows all of what happens. Each season is read from TMDB as it is needed (the one up next first); until then
 * its cards keep their place, quietly blank.
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
    }

    /** One episode's place in the row: what TMDB said of it once its season is read. */
    static final class Episode {
        final int season, number;
        boolean read;
        String name = "", about = "", still;
        LocalDate air;
        int minutes;

        Episode(int season, int number) {
            this.season = season;
            this.number = number;
        }
    }

    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private static final int INK = 0xFF101218, SOFT = 0xFF8E8E93;

    final LinearLayout view;
    private final Activity a;
    private final Title t;
    private final Host host;
    /** {season number, episodes} in order, the counts as the row has them now (a read season's own). */
    private final List<int[]> seasons;
    private final List<List<Episode>> bySeason = new ArrayList<>();
    private final List<Episode> all = new ArrayList<>();
    private final boolean[] asked;
    private final RecyclerView row;
    private final LinearLayoutManager lm;
    private final Cards cards;
    private final Tabs tabs;
    private final int cardW, side;
    private int upSeason = 1, upEpisode = 1;
    private float upProgress;
    /** The season tab chosen and being scrolled to: the pill stays on it until the row stops. */
    private int jumping = -1;
    private boolean placed;

    /**
     * The seasons and episodes, opening on the episode up next (else the first of season 1, else of the first season);
     * null when TMDB lists no seasons. logo: the show's logo (null: its name in words).
     */
    static CoverEpisodes make(Activity a, Title t, JSONObject details, Host host, boolean across, int side, int upSeason,
                              int upEpisode, float upProgress, String logo) {
        List<int[]> seasons = CoverText.seasons(details);
        if (seasons.isEmpty()) return null;
        CoverEpisodes e = new CoverEpisodes(a, t, seasons, host, across, side, logo);
        e.upSeason = upSeason;
        e.upEpisode = upEpisode;
        e.upProgress = upProgress;
        e.start();
        return e;
    }

    private CoverEpisodes(Activity a, Title t, List<int[]> seasons, Host host, boolean across, int side, String logo) {
        this.a = a;
        this.t = t;
        this.host = host;
        this.side = side;
        this.seasons = new ArrayList<>();
        for (int[] s : seasons) this.seasons.add(new int[]{s[0], Math.max(1, s[1])});
        asked = new boolean[seasons.size()];
        for (int[] s : this.seasons) {
            List<Episode> eps = new ArrayList<>();
            for (int n = 1; n <= s[1]; n++) eps.add(new Episode(s[0], n));
            bySeason.add(eps);
        }
        rebuildAll();
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        int screen = Math.round(dm.widthPixels / dm.density);
        cardW = Glass.dp(a, across ? 300 : Math.min(290, Math.round(screen * 0.68f)));

        view = new LinearLayout(a);
        view.setOrientation(LinearLayout.VERTICAL);

        // The show's logo in the middle (its name in words when it has none).
        View top;
        if (logo != null && !logo.isEmpty()) {
            ImageView mark = new ImageView(a);
            mark.setAdjustViewBounds(true);
            mark.setMaxHeight(Glass.dp(a, 64));
            mark.setMaxWidth(Math.round(dm.widthPixels * 0.62f));
            mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mark.setContentDescription(t.title);
            try {
                Glide.with(mark).load(logo).override(Math.round(dm.widthPixels * 0.62f), Glass.dp(a, 64)).fitCenter()
                        .format(DecodeFormat.PREFER_ARGB_8888).into(mark);
            } catch (RuntimeException ignored) {
                // the page went away
            }
            top = mark;
        } else {
            TextView name = Glass.text(a, t.title, 24, Glass.TEXT, Glass.heading(a, 700));
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            top = name;
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.gravity = Gravity.CENTER_HORIZONTAL;
        tp.setMargins(side, Glass.dp(a, 30), side, Glass.dp(a, 18));
        view.addView(top, tp);

        // The seasons' tabs, the one in view in the white pill.
        tabs = new Tabs(a, this.seasons, side, this::jump);
        view.addView(tabs.scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Every episode of every season, side by side.
        row = new RecyclerView(a);
        lm = new LinearLayoutManager(a, RecyclerView.HORIZONTAL, false);
        row.setLayoutManager(lm);
        row.setPadding(side, 0, side - Glass.dp(a, 14), 0);
        row.setClipToPadding(false);
        row.setOverScrollMode(View.OVER_SCROLL_NEVER);
        row.setHorizontalScrollBarEnabled(false);
        row.setItemAnimator(null);
        row.setNestedScrollingEnabled(false);
        cards = new Cards();
        row.setAdapter(cards);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.topMargin = Glass.dp(a, 16);
        rp.bottomMargin = Glass.dp(a, 8);
        view.addView(row, rp);
        row.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView r, int dx, int dy) {
                follow();
            }

            @Override public void onScrollStateChanged(@NonNull RecyclerView r, int state) {
                if (state != RecyclerView.SCROLL_STATE_IDLE) return;
                jumping = -1;
                follow();
            }
        });
    }

    /**
     * The season up next read first and the row placed on its episode; the two seasons either side of it just after;
     * the rest as the row comes near them (or their tab is chosen), so a long-running series isn't read all at once.
     */
    private void start() {
        int first = Math.max(0, indexOf(CoverText.opening(seasons, upSeason)));
        tabs.select(first, false);
        read(first, true);
        for (int d = 1; d <= 2; d++) {
            if (first + d < seasons.size()) read(first + d, false);
            if (first - d >= 0) read(first - d, false);
        }
        row.post(this::place);
    }

    private int indexOf(int season) {
        for (int i = 0; i < seasons.size(); i++) if (seasons.get(i)[0] == season) return i;
        return -1;
    }

    /** The row on the episode up next (else its season's first), once: what the family scrolls to after that is theirs. */
    private void place() {
        if (placed) return;
        int[] starts = starts(seasons);
        int s = indexOf(upSeason);
        int at;
        if (s >= 0) at = starts[s] + Math.max(0, Math.min(upEpisode, seasons.get(s)[1]) - 1);
        else at = starts[Math.max(0, indexOf(CoverText.opening(seasons, upSeason)))];
        lm.scrollToPositionWithOffset(Math.max(0, Math.min(at, all.size() - 1)), 0);
        placed = true;
    }

    /** A season's episodes from TMDB, off the main thread; its cards filled in when they come. */
    private void read(int index, boolean now) {
        if (asked[index]) return;
        asked[index] = true;
        int n = seasons.get(index)[0];
        Context app = a.getApplicationContext();
        Runnable job = () -> {
            JSONObject got = null;
            try {
                got = Tmdb.get(app, "tv/" + t.id + "/season/" + n);
            } catch (IOException ignored) {
                // its cards stay blank; tapped, they say so
            }
            JSONObject season = got;
            App.main.post(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                fill(index, season);
            });
        };
        if (now) App.io.execute(job);
        else row.postDelayed(() -> App.io.execute(job), 400L);
    }

    /** A season read: its cards say what TMDB says, the row keeping the card in view where it was. */
    private void fill(int index, JSONObject season) {
        JSONArray eps = season == null ? null : season.optJSONArray("episodes");
        if (eps == null || eps.length() == 0) return;
        Episode keep = firstInView();
        List<Episode> list = episodes(seasons.get(index)[0], eps);
        if (list.isEmpty()) return;
        boolean recount = list.size() != bySeason.get(index).size();
        bySeason.set(index, list);
        seasons.get(index)[1] = list.size();
        rebuildAll();
        if (recount) {
            cards.notifyDataSetChanged();
            if (keep != null && placed) {
                int at = find(keep.season, keep.number);
                if (at >= 0) lm.scrollToPositionWithOffset(at, 0);
            }
            if (!placed) row.post(this::place);
        } else {
            int[] starts = starts(seasons);
            cards.notifyItemRangeChanged(starts[index], list.size());
        }
    }

    private void rebuildAll() {
        all.clear();
        for (List<Episode> l : bySeason) all.addAll(l);
    }

    private int find(int season, int number) {
        for (int i = 0; i < all.size(); i++) {
            Episode e = all.get(i);
            if (e.season == season && e.number == number) return i;
        }
        return -1;
    }

    private Episode firstInView() {
        int p = lm.findFirstVisibleItemPosition();
        return p >= 0 && p < all.size() ? all.get(p) : null;
    }

    /** The pill on the season of the card most in view (on the chosen one while the row scrolls to it). */
    private void follow() {
        if (jumping >= 0) return;
        int p = lm.findFirstVisibleItemPosition();
        if (p < 0) return;
        View first = lm.findViewByPosition(p);
        // A first card more than half out of view gives the row's place to the next.
        if (first != null && first.getRight() - row.getPaddingLeft() < cardW / 2 && p + 1 < all.size()) p++;
        int s = seasonAt(starts(seasons), p);
        tabs.select(s, true);
        // A season read when the row comes near it, if not already.
        if (s + 1 < seasons.size()) read(s + 1, true);
        if (s > 0) read(s - 1, true);
    }

    /** A season's tab: the row slides to its first episode, the pill going straight to it. */
    private void jump(int index) {
        int target = starts(seasons)[index];
        jumping = index;
        tabs.select(index, true);
        read(index, true);
        int from = lm.findFirstVisibleItemPosition();
        // Far away: most of the way at once, the last few cards slid.
        if (from >= 0 && Math.abs(target - from) > 6) lm.scrollToPositionWithOffset(target + (target > from ? -4 : 4), 0);
        LinearSmoothScroller s = new LinearSmoothScroller(a) {
            @Override protected int getHorizontalSnapPreference() {
                return SNAP_TO_START;
            }
        };
        s.setTargetPosition(target);
        row.post(() -> lm.startSmoothScroll(s));
    }

    /** The episode up next (and how far into it, 0 to 1): its card marked. */
    void setUpNext(int season, int episode, float progress) {
        upSeason = season;
        upEpisode = episode;
        upProgress = progress;
        cards.notifyItemRangeChanged(0, all.size());
    }

    /** The playlists looked in: the episodes they don't have dimmed (and those not out yet). */
    void mark() {
        cards.notifyItemRangeChanged(0, all.size());
    }

    // ---- the cards -------------------------------------------------------------------------------------------------

    private static final class Holder extends RecyclerView.ViewHolder {
        final FrameLayout still;
        final ImageView picture;
        final TextView badge, code, name, about, facts;
        final CoverViews.Line line;

        Holder(View v, FrameLayout still, ImageView picture, TextView badge, CoverViews.Line line, TextView code, TextView name,
               TextView about, TextView facts) {
            super(v);
            this.still = still;
            this.picture = picture;
            this.badge = badge;
            this.line = line;
            this.code = code;
            this.name = name;
            this.about = about;
            this.facts = facts;
        }
    }

    private final class Cards extends RecyclerView.Adapter<Holder> {
        @Override public int getItemCount() {
            return all.size();
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context c = parent.getContext();
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Glass.dp(c, 14);
            box.setLayoutParams(lp);
            CoverViews.Wide still = new CoverViews.Wide(c);
            float r = Glass.dp(c, 10);
            Glass.round(still, r);
            ImageView pic = new ImageView(c);
            pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
            pic.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            still.addView(pic, new FrameLayout.LayoutParams(-1, -1));
            TextView badge = Glass.text(c, "Up next", 11, INK, Glass.bodySemi(c));
            badge.setBackground(CoverViews.pill(c, CoverViews.WHITE));
            badge.setPadding(Glass.dp(c, 9), Glass.dp(c, 4), Glass.dp(c, 9), Glass.dp(c, 4));
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
            bp.setMargins(Glass.dp(c, 10), Glass.dp(c, 10), 0, 0);
            still.addView(badge, bp);
            CoverViews.Line line = new CoverViews.Line(c);
            FrameLayout.LayoutParams lnp = new FrameLayout.LayoutParams(-1, Glass.dp(c, 4), Gravity.BOTTOM);
            lnp.setMargins(Glass.dp(c, 10), 0, Glass.dp(c, 10), Glass.dp(c, 9));
            still.addView(line, lnp);
            box.addView(still, new LinearLayout.LayoutParams(-1, -2));

            TextView code = Glass.text(c, "", 12.5f, SOFT, Glass.bodySemi(c));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.topMargin = Glass.dp(c, 13);
            box.addView(code, cp);
            TextView name = Glass.text(c, "", 15.5f, Glass.TEXT, Glass.heading(c, 600));
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
            np.topMargin = Glass.dp(c, 5);
            box.addView(name, np);
            TextView about = Glass.text(c, "", 13.5f, 0x9EFFFFFF, Glass.body(c));
            // Two lines kept whatever is said: every card the same height.
            about.setLines(2);
            about.setEllipsize(TextUtils.TruncateAt.END);
            about.setLineSpacing(0, 1.15f);
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
            ap.topMargin = Glass.dp(c, 6);
            box.addView(about, ap);
            TextView facts = Glass.text(c, "", 12.5f, SOFT, Glass.body(c));
            facts.setSingleLine(true);
            facts.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
            fp.topMargin = Glass.dp(c, 7);
            box.addView(facts, fp);
            box.setClickable(true);
            box.setFocusable(true);
            CoverViews.press(box, 0.98f);
            return new Holder(box, still, pic, badge, line, code, name, about, facts);
        }

        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            Episode e = all.get(position);
            Context c = h.itemView.getContext();
            LocalDate today = LocalDate.now();
            h.still.setBackground(Glass.placeholder(t.title + " " + e.season, Glass.dp(c, 10)));
            String url = e.still == null ? null : Tmdb.image(e.still, "w500");
            try {
                if (url != null) Glide.with(h.picture).load(url).override(cardW, cardW * 9 / 16).centerCrop()
                        .format(DecodeFormat.PREFER_RGB_565).into(h.picture);
                else {
                    Glide.with(h.picture).clear(h.picture);
                    h.picture.setImageDrawable(null);
                }
            } catch (RuntimeException ignored) {
                // the page went away
            }
            h.code.setText(code(e.season, e.number));
            h.name.setText(e.read ? (e.name.isEmpty() ? "Episode " + e.number : e.name) : " ");
            h.about.setText(e.about);
            h.facts.setText(e.read ? facts(e.minutes, e.air, today) : " ");
            boolean up = e.season == upSeason && e.number == upEpisode;
            h.badge.setVisibility(up ? View.VISIBLE : View.GONE);
            boolean part = up && upProgress > 0;
            h.line.setVisibility(part ? View.VISIBLE : View.GONE);
            if (part) h.line.set(upProgress);
            boolean aired = e.read && e.air != null && !e.air.isAfter(today);
            boolean dim = e.read && (!aired || Boolean.FALSE.equals(host.has(e.season, e.number)));
            float al = dim ? 0.42f : 1f;
            h.still.setAlpha(al);
            h.name.setAlpha(al);
            h.about.setAlpha(al);
            h.itemView.setContentDescription((up ? "Up next: " : "") + code(e.season, e.number) + ", " + h.name.getText()
                    + (e.read && !aired ? ", not out yet" : ""));
            h.itemView.setOnClickListener(v -> tapped(e, today));
            h.itemView.setOnLongClickListener(v -> {
                more(e, today);
                return true;
            });
        }
    }

    private void tapped(Episode e, LocalDate today) {
        if (!e.read) {
            Toast.makeText(a, "Still reading " + CoverText.season(e.season) + "…", Toast.LENGTH_SHORT).show();
            return;
        }
        if (e.air == null || e.air.isAfter(today)) {
            Toast.makeText(a, e.air == null ? "Not out yet" : "Out " + day(e.air), Toast.LENGTH_SHORT).show();
            return;
        }
        if (Boolean.FALSE.equals(host.has(e.season, e.number))) host.missing(e.season, e.number);
        else host.play(e.season, e.number);
    }

    /** All of what happens in it, with Play when the playlists may have it. */
    private void more(Episode e, LocalDate today) {
        if (!e.read) return;
        String info = facts(e.minutes, e.air, today);
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(a)
                .setTitle(code(e.season, e.number) + " · " + (e.name.isEmpty() ? "Episode " + e.number : e.name))
                .setMessage(e.about.isEmpty() ? info : e.about + "\n\n" + info);
        boolean aired = e.air != null && !e.air.isAfter(today);
        if (aired && !Boolean.FALSE.equals(host.has(e.season, e.number))) {
            b.setPositiveButton("Play", (d, w) -> host.play(e.season, e.number));
        }
        b.setNegativeButton("Close", null).show();
    }

    // ---- the seasons' tabs -------------------------------------------------------------------------------------------

    /** The seasons in a row of words, the chosen one's in a white pill that slides from one to the next. */
    private static final class Tabs {
        interface OnPick {
            void on(int index);
        }

        final HorizontalScrollView scroll;
        private final FrameLayout stage;
        private final View pill;
        private final LinearLayout words;
        private final List<TextView> tabs = new ArrayList<>();
        private int chosen = -1;
        private ValueAnimator move;

        Tabs(Context c, List<int[]> seasons, int side, OnPick pick) {
            scroll = new HorizontalScrollView(c);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            stage = new FrameLayout(c);
            // The pill's left edge in line with the cards' (the words inside it a little in), as the recording has it.
            stage.setPadding(side, 0, side, 0);
            pill = new View(c);
            GradientDrawable white = new GradientDrawable();
            white.setColor(0xFFFFFFFF);
            white.setCornerRadius(Glass.dp(c, 18));
            pill.setBackground(white);
            pill.setVisibility(View.INVISIBLE);
            stage.addView(pill, new FrameLayout.LayoutParams(0, Glass.dp(c, 34), Gravity.CENTER_VERTICAL | Gravity.START));
            words = new LinearLayout(c);
            words.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < seasons.size(); i++) {
                TextView w = Glass.text(c, CoverText.season(seasons.get(i)[0]), 15, 0xA6FFFFFF, Glass.heading(c, 600));
                w.setGravity(Gravity.CENTER);
                w.setPadding(Glass.dp(c, 14), 0, Glass.dp(c, 14), 0);
                w.setMinHeight(Glass.dp(c, 34));
                int index = i;
                w.setOnClickListener(v -> pick.on(index));
                tabs.add(w);
                words.addView(w, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Glass.dp(c, 34)));
            }
            stage.addView(words, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL));
            scroll.addView(stage, new FrameLayout.LayoutParams(-2, -2));
            // The pill set on the chosen tab once the tabs know their sizes.
            words.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (chosen >= 0 && (move == null || !move.isRunning())) snap(chosen);
            });
        }

        /** The pill on a season: sliding there, or there at once. */
        void select(int index, boolean animate) {
            if (index < 0 || index >= tabs.size()) return;
            if (index == chosen && (move == null || !move.isRunning())) return;
            int from = chosen;
            chosen = index;
            for (int i = 0; i < tabs.size(); i++) {
                TextView w = tabs.get(i);
                w.setContentDescription(w.getText() + (i == index ? ", showing" : ""));
            }
            TextView to = tabs.get(index);
            if (!animate || from < 0 || to.getWidth() == 0) {
                snap(index);
                return;
            }
            float x0 = pill.getTranslationX(), w0 = pill.getLayoutParams().width;
            float x1 = to.getLeft(), w1 = to.getWidth();
            TextView old = from >= 0 && from < tabs.size() ? tabs.get(from) : null;
            ArgbEvaluator colours = new ArgbEvaluator();
            if (move != null) move.cancel();
            move = ValueAnimator.ofFloat(0f, 1f);
            move.setDuration(320);
            move.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
            move.addUpdateListener(an -> {
                float f = (float) an.getAnimatedValue();
                pill.setTranslationX(x0 + (x1 - x0) * f);
                ViewGroup.LayoutParams lp = pill.getLayoutParams();
                lp.width = Math.round(w0 + (w1 - w0) * f);
                pill.setLayoutParams(lp);
                to.setTextColor((Integer) colours.evaluate(f, 0xA6FFFFFF, INK));
                if (old != null && old != to) old.setTextColor((Integer) colours.evaluate(f, INK, 0xA6FFFFFF));
            });
            move.start();
            pill.setVisibility(View.VISIBLE);
            keepInView(to);
        }

        private void snap(int index) {
            TextView to = tabs.get(index);
            if (to.getWidth() == 0) return;
            pill.setTranslationX(to.getLeft());
            ViewGroup.LayoutParams lp = pill.getLayoutParams();
            lp.width = to.getWidth();
            pill.setLayoutParams(lp);
            pill.setVisibility(View.VISIBLE);
            for (int i = 0; i < tabs.size(); i++) tabs.get(i).setTextColor(i == index ? INK : 0xA6FFFFFF);
            keepInView(to);
        }

        private void keepInView(TextView tab) {
            int left = tab.getLeft() + stage.getPaddingLeft(), right = left + tab.getWidth();
            int x = scroll.getScrollX(), w = scroll.getWidth();
            if (w == 0) return;
            if (left < x + stage.getPaddingLeft()) scroll.smoothScrollTo(Math.max(0, left - stage.getPaddingLeft()), 0);
            else if (right > x + w - stage.getPaddingRight()) scroll.smoothScrollTo(right - w + stage.getPaddingRight(), 0);
        }
    }

    // ---- pure helpers -----------------------------------------------------------------------------------------------

    /** Pure: where each season's first episode is in the row, from the seasons' counts. */
    static int[] starts(List<int[]> seasons) {
        int[] out = new int[seasons.size()];
        int at = 0;
        for (int i = 0; i < seasons.size(); i++) {
            out[i] = at;
            at += Math.max(0, seasons.get(i)[1]);
        }
        return out;
    }

    /** Pure: which season (its index) the card at a place in the row is in. */
    static int seasonAt(int[] starts, int position) {
        int s = 0;
        for (int i = 0; i < starts.length; i++) if (position >= starts[i]) s = i;
        return s;
    }

    /** Pure: "S1 E8". */
    static String code(int season, int number) {
        return (season > 0 ? "S" + season + " " : "") + "E" + number;
    }

    /** Pure: "1h 7m", "54m"; "" for none. */
    static String length(int minutes) {
        if (minutes <= 0) return "";
        return minutes < 60 ? minutes + "m" : minutes / 60 + "h " + minutes % 60 + "m";
    }

    /** Pure: "1h 7m, 9 Oct 2022"; "Out 16 Dec 2026" for one still to come; "Date to be announced" with no date. */
    static String facts(int minutes, LocalDate air, LocalDate today) {
        if (air == null) return "Date to be announced";
        if (today != null && air.isAfter(today)) return "Out " + day(air);
        String len = length(minutes);
        return len.isEmpty() ? day(air) : len + ", " + day(air);
    }

    /** Pure: "9 Oct 2022", "4 Sep 2022" (the months' short names fixed, whatever the phone's language data says). */
    static String day(LocalDate d) {
        return d.getDayOfMonth() + " " + MONTHS[d.getMonthValue() - 1] + " " + d.getYear();
    }

    /** Pure: a season's episodes from TMDB's season (in order, each once), read. */
    static List<Episode> episodes(int season, JSONArray eps) {
        List<Episode> out = new ArrayList<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; eps != null && i < eps.length(); i++) {
            JSONObject o = eps.optJSONObject(i);
            if (o == null || o.optInt("episode_number") <= 0 || !seen.add(o.optInt("episode_number"))) continue;
            Episode e = new Episode(season, o.optInt("episode_number"));
            e.read = true;
            e.name = o.optString("name", "").trim();
            e.about = o.optString("overview", "").trim();
            String still = o.optString("still_path", "");
            e.still = still.isEmpty() || still.equals("null") ? null : still;
            e.air = CoverText.day(o.optString("air_date", ""));
            e.minutes = o.optInt("runtime");
            out.add(e);
        }
        out.sort((x, y) -> Integer.compare(x.number, y.number));
        return out;
    }
}
