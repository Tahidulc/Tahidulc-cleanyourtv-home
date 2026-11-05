package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.gathin.tvhomephone.Net;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.SyncStore;
import com.gathin.tvhomephone.sports.Clubs;
import com.gathin.tvhomephone.sports.Espn;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.LiveClock;
import com.gathin.tvhomephone.sports.Weather;
import com.gathin.tvhomephone.watch.ComingUp;
import com.gathin.tvhomephone.watch.Lists;
import com.gathin.tvhomephone.watch.Title;
import com.gathin.tvhomephone.watch.Tmdb;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Broadcast mode's break: while the channel shows its adverts, the picture shrinks into a small live window in the
 * corner (sound off) and Homefield fills the screen with what is for this family, from the TV and the phone: what's on
 * the TV to carry on watching, new episodes and what is coming up, the list, the reminders set on the TVs, the other
 * games on now, the headlines, a trailer with its sound, fun facts (Bangladesh first, as the TVs' Ambient mode has
 * them), a quiz question (its answer a few seconds on), the day's prayer times from the family's mosque with the next
 * one counting down, and today: the date, the Islamic date, the weather and sunset. A card every nine seconds or so (the
 * trailer longer); Settings says which kinds. "Back to the channel" ends it, and it ends by itself when the channel's
 * logo is back (BreakWatch).
 */
@SuppressLint("ViewConstructor")
final class BreakScreen extends FrameLayout {
    interface Host {
        /** The channel's sound off (on) or back. */
        void mute(boolean on);

        /** "Back to the channel" tapped. */
        void back();
    }

    /** What the break shows, read off the main thread. */
    static final class Content {
        final List<Title> onTv = new ArrayList<>(), fresh = new ArrayList<>(), coming = new ArrayList<>(), list = new ArrayList<>();
        final List<String[]> reminders = new ArrayList<>(), news = new ArrayList<>();
        final List<Game> elsewhere = new ArrayList<>();
        String trailerKey, trailerTitle;
        Object[] weather;
        /** The day's prayers, {name, begins, jamā'ah or ""} (Sunrise among them); whose times, the Islamic date. */
        final List<String[]> prayers = new ArrayList<>();
        String prayerSource, hijri;
        /** In Ramadan: {"Iftar" or "Sehri ends", "HH:mm"}. */
        String[] ramadan;
    }

    /** The kept content let go (what the break shows was changed in Settings). */
    static void forget() {
        cached = null;
    }

    private static final long CARD_MS = 9_000L, TRAILER_MS = 40_000L, FACT_MS = 10_000L, QUIZ_MS = 14_000L, ANSWER_MS = 7_000L;
    private static Content cached;
    private static long cachedAt;

    /** The break's content (kept ten minutes between breaks). Off the main thread. */
    static Content load(Context c, Game watching) {
        if (cached != null && System.currentTimeMillis() - cachedAt < 10 * 60_000L) return cached;
        Content k = new Content();
        if (ScoreBoard.breakShows(c, "tv")) {
            try { k.onTv.addAll(cap(Lists.resume(c), 6)); } catch (RuntimeException ignored) { }
            try { k.fresh.addAll(cap(Lists.yourShows(c, true), 6)); } catch (RuntimeException ignored) { }
            try { k.list.addAll(cap(Lists.myList(c), 6)); } catch (RuntimeException ignored) { }
            try { k.coming.addAll(cap(ComingUp.load(c, 10), 6)); } catch (Exception ignored) { }
        }
        // The reminders set on the TVs (and here): the ones still to come in the next day.
        long now = System.currentTimeMillis();
        List<JSONObject> rs = new ArrayList<>(SyncStore.list(c, Reminders.TYPE));
        rs.sort((a, b) -> Long.compare(a.optLong("at"), b.optLong("at")));
        for (JSONObject r : rs) {
            if (!ScoreBoard.breakShows(c, "reminders")) break;
            long at = r.optLong("at");
            if (at < now - 5 * 60_000L || at > now + 36 * 3_600_000L) continue;
            k.reminders.add(new String[]{r.optString("title"), when(at), r.optString("channel")});
            if (k.reminders.size() >= 5) break;
        }
        // The headlines: BBC News's and BBC Sport's first three.
        for (String[] f : BreakingNews.FEEDS) {
            if (!ScoreBoard.breakShows(c, "news")) break;
            try {
                List<BreakingNews.Story> st = BreakingNews.parse(Net.get(f[0]));
                for (int i = 0; i < Math.min(3, st.size()); i++) k.news.add(new String[]{f[1], st.get(i).title});
            } catch (Exception ignored) {
                // that feed another time
            }
        }
        // The other games on now, the followed clubs' first.
        if (ScoreBoard.breakShows(c, "games")) try {
            Set<String> teams = Clubs.starred(c);
            List<Game> mine = com.gathin.tvhomephone.sports.LiveGames.followed(c), rest = new ArrayList<>();
            for (Game g : mine) if (watching == null || !g.id.equals(watching.id)) k.elsewhere.add(g);
            for (String[] l : Espn.LEAGUES) {
                if (k.elsewhere.size() + rest.size() >= 8) break;
                for (Game g : Espn.scoreboard(l[0], LocalDate.now(), 60_000L)) {
                    if (g.state != Game.LIVE || g.involves(teams) || (watching != null && g.id.equals(watching.id))) continue;
                    rest.add(g);
                }
            }
            k.elsewhere.addAll(rest);
            while (k.elsewhere.size() > 6) k.elsewhere.remove(k.elsewhere.size() - 1);
        } catch (RuntimeException ignored) {
            // none
        }
        if (ScoreBoard.breakShows(c, "today")) k.weather = Weather.now();
        if (ScoreBoard.breakShows(c, "trailer")) findTrailer(c, k);
        // The prayer times: the mosque's timetable (read once a day, kept), else worked out for Manchester.
        if (ScoreBoard.breakShows(c, "prayer") || ScoreBoard.breakShows(c, "today")) {
            try {
                com.gathin.tvhomephone.Prayer.refresh(c);
                LocalDate d = LocalDate.now();
                java.time.LocalDateTime rise = com.gathin.tvhomephone.Prayer.sunrise(c, d);
                for (com.gathin.tvhomephone.Prayer.Slot x : com.gathin.tvhomephone.Prayer.slots(c, d)) {
                    k.prayers.add(new String[]{x.label(), com.gathin.tvhomephone.Prayer.hm(x.begins), com.gathin.tvhomephone.Prayer.hm(x.jamaah)});
                    if ("Fajr".equals(x.name) && rise != null) k.prayers.add(new String[]{"Sunrise", com.gathin.tvhomephone.Prayer.hm(rise), ""});
                }
                k.prayerSource = com.gathin.tvhomephone.Prayer.source(c);
                k.hijri = com.gathin.tvhomephone.Prayer.hijri(c);
                k.ramadan = com.gathin.tvhomephone.Prayer.ramadan(c);
            } catch (RuntimeException ignored) {
                // no times this break
            }
        }
        cached = k;
        cachedAt = now;
        return k;
    }

    /** A trailer: for something on the list or coming up first, else what's trending this week. */
    private static void findTrailer(Context c, Content k) {
        if (!Tmdb.ready(c)) return;
        List<Title> pool = new ArrayList<>(k.coming);
        pool.addAll(k.list);
        pool.addAll(k.fresh);
        try {
            if (pool.isEmpty()) pool.addAll(cap(Tmdb.list(c, "trending/movie/week", false, 1), 5));
        } catch (Exception ignored) {
            // none
        }
        for (Title t : pool) {
            if (t.id <= 0) continue;
            try {
                JSONArray vs = Tmdb.get(c, (t.tv ? "tv/" : "movie/") + t.id + "/videos").optJSONArray("results");
                for (int i = 0; vs != null && i < vs.length(); i++) {
                    JSONObject v = vs.getJSONObject(i);
                    if ("YouTube".equals(v.optString("site")) && "Trailer".equals(v.optString("type"))) {
                        k.trailerKey = v.optString("key");
                        k.trailerTitle = t.title;
                        return;
                    }
                }
            } catch (Exception ignored) {
                // the next
            }
        }
    }

    private static <T> List<T> cap(List<T> l, int n) {
        return l.size() <= n ? l : new ArrayList<>(l.subList(0, n));
    }

    private static String when(long at) {
        java.time.ZonedDateTime z = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault());
        String day = z.toLocalDate().equals(LocalDate.now()) ? "Today" : z.toLocalDate().equals(LocalDate.now().plusDays(1)) ? "Tomorrow"
                : z.format(DateTimeFormatter.ofPattern("EEE", Locale.UK));
        return day + " " + z.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    // ---- the screen ----------------------------------------------------------------------------------------------

    private final Video video;
    private final Host host;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Paint ground = new Paint();
    private final RectF hole = new RectF();
    private final FrameLayout stage;
    private final LinearLayout dots;
    private final List<View> cards = new ArrayList<>();
    private final List<Long> lengths = new ArrayList<>();
    /** What a card does each time it comes up (a countdown put right, a quiz's answer held back). */
    private final java.util.Map<View, Runnable> onShow = new java.util.HashMap<>();
    private int at = -1;
    private float oldScaleX = 1f, oldScaleY = 1f;
    private WebView trailer;
    private View trailerCard;
    private String trailerHtml;
    private final float dp;

    private final Runnable turn = this::next;

    BreakScreen(Context c, Video video, Host host) {
        super(c);
        this.video = video;
        this.host = host;
        dp = c.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setClickable(true);
        setAlpha(0f);
        boolean upright = c.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;

        // The heading, top left: Homefield's tile, BREAK, why.
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(c);
        mark.setImageResource(R.drawable.ic_app);
        mark.setClipToOutline(true);
        mark.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 9 * dp);
            }
        });
        head.addView(mark, new LinearLayout.LayoutParams(px(36), px(36)));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(px(12), 0, 0, 0);
        TextView t = Ui.text(c, "HOMEFIELD BREAK", 15, 0xFFFFFFFF, true);
        t.setLetterSpacing(0.14f);
        words.addView(t);
        words.addView(Ui.text(c, "The channel's on a break: back to the match as soon as it's on", 12, 0xFFB8BED6, false));
        head.addView(words);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        hp.leftMargin = px(24);
        hp.topMargin = px(20);
        addView(head, hp);

        // The cards, under the heading, beside (or, upright, under) the small live window.
        stage = new FrameLayout(c);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1, -1);
        sp.leftMargin = px(24);
        sp.topMargin = upright ? 0 : px(84);
        sp.bottomMargin = px(64);
        sp.rightMargin = px(24);
        addView(stage, sp);

        // Along the foot: where it is in the cards, and back to the channel.
        LinearLayout foot = new LinearLayout(c);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        dots = new LinearLayout(c);
        foot.addView(dots, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView back = Ui.text(c, "Back to the channel", 14, 0xFF0A0D33, true);
        back.setGravity(Gravity.CENTER);
        back.setPadding(px(18), px(9), px(18), px(9));
        back.setBackground(Ui.round(c, 0xFFFFFFFF, 20));
        back.setOnClickListener(v -> host.back());
        foot.addView(back);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        fp.leftMargin = fp.rightMargin = px(24);
        fp.bottomMargin = px(16);
        addView(foot, fp);
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    /** It comes up: the picture shrinks to its corner, the sound goes, the cards begin. */
    void show(Content k) {
        int w = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        int ht = getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels;
        boolean upright = ht > w;
        // The live window: the picture itself (not the bars round it), top right, half the width upright, else a third.
        RectF pic = new RectF();
        video.picture(pic);
        if (pic.width() < 8 || pic.height() < 8) pic.set(0, 0, w, ht);
        float s = Math.min(0.6f, (upright ? 0.5f : 0.3f) * w / pic.width());
        float right = w - px(20), top = upright ? px(84) : px(20);
        float pivotX = (right - pic.right * s) / (1 - s), pivotY = (top - pic.top * s) / (1 - s);
        oldScaleX = video.getScaleX();
        oldScaleY = video.getScaleY();
        video.setPivotX(pivotX);
        video.setPivotY(pivotY);
        video.animate().scaleX(s).scaleY(s).setDuration(450).start();
        hole.set(pivotX + (pic.left - pivotX) * s, top, right, pivotY + (pic.bottom - pivotY) * s);
        if (upright) {
            FrameLayout.LayoutParams sp = (FrameLayout.LayoutParams) stage.getLayoutParams();
            sp.topMargin = Math.round(hole.bottom + px(16));
            stage.setLayoutParams(sp);
        } else {
            FrameLayout.LayoutParams sp = (FrameLayout.LayoutParams) stage.getLayoutParams();
            sp.rightMargin = Math.round(w - hole.left + px(20));
            stage.setLayoutParams(sp);
        }
        TextView live = Ui.text(getContext(), "● LIVE · back here when the match is", 11, 0xFFDDE3FF, true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        lp.leftMargin = Math.round(hole.left);
        lp.topMargin = Math.round(hole.bottom + px(6));
        addView(live, lp);
        host.mute(true);
        build(k);
        animate().alpha(1f).setDuration(350).start();
        next();
    }

    /** It goes: the picture grows back, the sound returns. */
    void end(Runnable done) {
        h.removeCallbacksAndMessages(null);
        if (trailer != null) {
            trailer.destroy();
            trailer = null;
        }
        video.animate().scaleX(oldScaleX).scaleY(oldScaleY).setDuration(400).start();
        host.mute(false);
        animate().alpha(0f).setDuration(300).withEndAction(done).start();
    }

    @Override protected void onDraw(Canvas cv) {
        // A navy-to-violet ground everywhere but the live window.
        if (ground.getShader() == null || getHeight() == 0) {
            ground.setShader(new LinearGradient(0, 0, getWidth(), getHeight(), new int[]{0xFF070A1E, 0xFF15103A, 0xFF2A0F2E},
                    null, Shader.TileMode.CLAMP));
        }
        cv.save();
        if (!hole.isEmpty()) cv.clipOutRect(hole);
        cv.drawRect(0, 0, getWidth(), getHeight(), ground);
        cv.restore();
    }

    // ---- the cards -------------------------------------------------------------------------------------------------

    private void build(Content k) {
        Context c = getContext();
        boolean tv = ScoreBoard.breakShows(c, "tv"), facts = ScoreBoard.breakShows(c, "facts"), quiz = ScoreBoard.breakShows(c, "quiz");
        // Fresh facts and questions each break (the deck goes on from the last one's).
        List<String[]> fs = facts ? Facts.next(3) : new ArrayList<>();
        List<String[]> qs = quiz ? Facts.questions(2) : new ArrayList<>();
        if (tv && !k.onTv.isEmpty()) card(posters(c, "CARRY ON WATCHING ON YOUR TV", 0xFF8FC3FF, k.onTv), CARD_MS);
        if (!fs.isEmpty()) card(fact(c, fs.get(0)), FACT_MS);
        if (tv && !k.fresh.isEmpty()) card(posters(c, "NEW EPISODES FOR YOU", 0xFF3DDC84, k.fresh), CARD_MS);
        if (ScoreBoard.breakShows(c, "prayer") && k.prayers.size() >= 5) card(prayers(c, k), 11_000L);
        if (ScoreBoard.breakShows(c, "trailer") && k.trailerKey != null) card(trailerCard(c, k), TRAILER_MS);
        if (!qs.isEmpty()) card(quiz(c, qs.get(0)), QUIZ_MS);
        if (ScoreBoard.breakShows(c, "reminders") && !k.reminders.isEmpty()) card(rows(c, "YOUR REMINDERS", 0xFFFFC93C, k.reminders), CARD_MS);
        if (ScoreBoard.breakShows(c, "games") && !k.elsewhere.isEmpty()) card(games(c, k.elsewhere), CARD_MS);
        if (fs.size() > 1) card(fact(c, fs.get(1)), FACT_MS);
        if (tv && !k.coming.isEmpty()) card(posters(c, "COMING UP", 0xFFFF8A7A, k.coming), CARD_MS);
        if (ScoreBoard.breakShows(c, "news") && !k.news.isEmpty()) card(news(c, k.news), CARD_MS);
        if (qs.size() > 1) card(quiz(c, qs.get(1)), QUIZ_MS);
        if (tv && !k.list.isEmpty()) card(posters(c, "FROM YOUR LIST", 0xFFC8A2FF, k.list), CARD_MS);
        if (ScoreBoard.breakShows(c, "today")) card(today(c, k), 8_000L);
        if (fs.size() > 2) card(fact(c, fs.get(2)), FACT_MS);
        if (cards.isEmpty()) card(heading(c, "BACK SOON", 0xFFFFFFFF, "Nothing else to show: the match is back in a moment."), CARD_MS);
        for (int i = 0; i < cards.size(); i++) {
            View d = new View(c);
            d.setBackground(Ui.round(c, 0x55FFFFFF, 4));
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(px(8), px(8));
            dp.rightMargin = px(6);
            dots.addView(d, dp);
        }
    }

    private void card(View v, long ms) {
        v.setAlpha(0f);
        v.setVisibility(GONE);
        stage.addView(v, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL));
        cards.add(v);
        lengths.add(ms);
    }

    private void next() {
        h.removeCallbacks(turn);
        if (cards.isEmpty()) return;
        if (at >= 0) {
            View old = cards.get(at);
            // The trailer stops as its card goes (it plays only while seen).
            if (old == trailerCard && trailer != null) trailer.loadUrl("about:blank");
            old.animate().alpha(0f).translationX(-px(24)).setDuration(260).withEndAction(() -> old.setVisibility(GONE)).start();
            dots.getChildAt(at).setBackground(Ui.round(getContext(), 0x55FFFFFF, 4));
        }
        at = (at + 1) % cards.size();
        View v = cards.get(at);
        if (v == trailerCard && trailer != null) trailer.loadDataWithBaseURL("https://homefield.app/", trailerHtml, "text/html", "utf-8", null);
        Runnable shown = onShow.get(v);
        if (shown != null) shown.run();
        v.setVisibility(VISIBLE);
        v.setTranslationX(px(24));
        v.animate().alpha(1f).translationX(0).setDuration(320).setStartDelay(120).start();
        dots.getChildAt(at).setBackground(Ui.round(getContext(), 0xFFFFFFFF, 4));
        h.postDelayed(turn, lengths.get(at));
    }

    private LinearLayout heading(Context c, String label, int colour, String line) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView l = Ui.text(c, label, 12, colour, true);
        l.setLetterSpacing(0.14f);
        col.addView(l);
        if (line != null) {
            TextView t = Ui.text(c, line, 20, 0xFFFFFFFF, true);
            t.setPadding(0, px(6), 0, 0);
            col.addView(t);
        }
        return col;
    }

    /** A row of posters, each with its name and its line (the episode, the day, the channel). */
    private View posters(Context c, String label, int colour, List<Title> titles) {
        LinearLayout col = heading(c, label, colour, null);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(c);
        hs.addView(row);
        for (Title t : titles) {
            LinearLayout one = new LinearLayout(c);
            one.setOrientation(LinearLayout.VERTICAL);
            ImageView p = new ImageView(c);
            p.setScaleType(ImageView.ScaleType.CENTER_CROP);
            p.setBackground(Ui.round(c, 0x22FFFFFF, 10));
            p.setClipToOutline(true);
            p.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 10 * dp);
                }
            });
            if (t.poster != null && !t.poster.isEmpty()) {
                com.gathin.tvhomephone.sports.Ui.load(p, t.poster.startsWith("http") ? t.poster : Tmdb.image(t.poster, "w342"));
            }
            one.addView(p, new LinearLayout.LayoutParams(px(104), px(156)));
            if (t.progress >= 0) {
                ProgressBar bar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
                bar.setMax(100);
                bar.setProgress(Math.round(t.progress * 100));
                bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFE0213A));
                one.addView(bar, new LinearLayout.LayoutParams(px(104), px(4)));
            }
            TextView name = Ui.text(c, t.title, 13, 0xFFFFFFFF, true);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(0, px(6), 0, 0);
            one.addView(name, new LinearLayout.LayoutParams(px(104), -2));
            String under = t.badge != null ? t.badge : t.foot;
            if (under != null) {
                TextView u = Ui.text(c, under, 11, 0xFFB8BED6, false);
                u.setMaxLines(1);
                u.setEllipsize(TextUtils.TruncateAt.END);
                one.addView(u, new LinearLayout.LayoutParams(px(104), -2));
            }
            LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-2, -2);
            op.rightMargin = px(12);
            row.addView(one, op);
        }
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = px(12);
        col.addView(hs, hp);
        return col;
    }

    /** Lines: {words, when, where}. */
    private View rows(Context c, String label, int colour, List<String[]> lines) {
        LinearLayout col = heading(c, label, colour, null);
        for (String[] l : lines) {
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, px(10), 0, 0);
            TextView when = Ui.text(c, l[1], 13, colour, true);
            when.setFontFeatureSettings("tnum");
            r.addView(when, new LinearLayout.LayoutParams(px(118), -2));
            TextView what = Ui.text(c, l[0] + (l[2] == null || l[2].isEmpty() ? "" : "  ·  " + l[2]), 16, 0xFFFFFFFF, false);
            what.setMaxLines(1);
            what.setEllipsize(TextUtils.TruncateAt.END);
            r.addView(what, new LinearLayout.LayoutParams(0, -2, 1f));
            col.addView(r);
        }
        return col;
    }

    private View games(Context c, List<Game> games) {
        LinearLayout col = heading(c, "ELSEWHERE NOW", 0xFF3DDC84, null);
        for (Game g : games) {
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, px(9), 0, 0);
            TextView clock = Ui.text(c, "", 13, 0xFF3DDC84, true);
            clock.setFontFeatureSettings("tnum");
            LiveClock.bind(clock, g, w -> w);
            r.addView(clock, new LinearLayout.LayoutParams(px(84), -2));
            TextView line = Ui.text(c, g.homeName + "  " + g.score() + "  " + g.awayName, 16, 0xFFFFFFFF, true);
            line.setMaxLines(1);
            line.setEllipsize(TextUtils.TruncateAt.END);
            r.addView(line, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView comp = Ui.text(c, g.league == null ? "" : g.league, 11, 0xFFB8BED6, true);
            r.addView(comp);
            col.addView(r);
        }
        return col;
    }

    private View news(Context c, List<String[]> stories) {
        LinearLayout col = heading(c, "THE HEADLINES", 0xFFFFC21A, null);
        for (String[] s : stories) {
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, px(9), 0, 0);
            TextView src = Ui.text(c, s[0].toUpperCase(Locale.UK).replace("BBC ", ""), 10, 0xFF0A0D33, true);
            src.setBackground(Ui.round(c, s[0].contains("Sport") ? 0xFFFFC21A : 0xFFFF6B6B, 4));
            src.setPadding(px(6), px(1), px(6), px(1));
            r.addView(src);
            TextView t = Ui.text(c, s[1], 15, 0xFFFFFFFF, false);
            t.setMaxLines(1);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(px(10), 0, 0, 0);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            col.addView(r);
        }
        return col;
    }

    /** A fun fact: whose (Bangladesh's with its flag, the world's, football's) and the fact, big. */
    private View fact(Context c, String[] f) {
        boolean bd = Facts.BANGLADESH.equals(f[0]), ball = Facts.FOOTBALL.equals(f[0]);
        int colour = bd ? 0xFF3DDC84 : ball ? 0xFF8FC3FF : 0xFFFFC93C;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (bd) {
            top.addView(new Flag(c), new LinearLayout.LayoutParams(px(30), px(18)));
        } else {
            TextView mark = Ui.text(c, ball ? "\u26BD" : "\uD83D\uDCA1", 16, 0xFFFFFFFF, false);
            top.addView(mark);
        }
        TextView l = Ui.text(c, bd ? "FUN FACT \u00B7 BANGLADESH" : ball ? "FUN FACT \u00B7 FOOTBALL" : "DID YOU KNOW?", 12, colour, true);
        l.setLetterSpacing(0.14f);
        l.setPadding(px(10), 0, 0, 0);
        top.addView(l);
        col.addView(top);
        TextView t = Ui.text(c, f[1], 23, 0xFFFFFFFF, true);
        t.setLineSpacing(0, 1.12f);
        t.setPadding(0, px(12), 0, 0);
        col.addView(t);
        // A line in its colour under it.
        View bar = new View(c);
        bar.setBackground(Ui.round(c, colour, 2));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(px(56), px(4));
        bp.topMargin = px(14);
        col.addView(bar, bp);
        return col;
    }

    /** A quiz question: the answer held back for a few seconds, the time left drawn as a bar, then shown. */
    private View quiz(Context c, String[] q) {
        LinearLayout col = heading(c, "QUIZ TIME", 0xFFFF7AC8, null);
        TextView t = Ui.text(c, q[0], 23, 0xFFFFFFFF, true);
        t.setLineSpacing(0, 1.12f);
        t.setPadding(0, px(10), 0, 0);
        col.addView(t);
        ProgressBar left = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        left.setMax(1000);
        left.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFFF7AC8));
        left.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33FFFFFF));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(220), px(4));
        lp.topMargin = px(16);
        col.addView(left, lp);
        TextView hint = Ui.text(c, "", 13, 0xFFB8BED6, false);
        hint.setPadding(0, px(8), 0, 0);
        col.addView(hint);
        TextView answer = Ui.text(c, q[1], 22, 0xFF0A0D33, true);
        answer.setPadding(px(16), px(9), px(16), px(9));
        answer.setBackground(Ui.round(c, 0xFF3DDC84, 12));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, -2);
        ap.topMargin = px(10);
        col.addView(answer, ap);
        onShow.put(col, () -> {
            answer.animate().cancel();
            answer.setAlpha(0f);
            answer.setScaleX(0.85f);
            answer.setScaleY(0.85f);
            left.setVisibility(VISIBLE);
            hint.setText("Think you know? The answer's coming\u2026");
            // The time left, drawn down in small steps till the answer.
            long from = android.os.SystemClock.uptimeMillis();
            Runnable tick = new Runnable() {
                @Override public void run() {
                    long gone = android.os.SystemClock.uptimeMillis() - from;
                    left.setProgress((int) Math.max(0, 1000 - gone * 1000 / ANSWER_MS));
                    if (gone < ANSWER_MS) h.postDelayed(this, 50);
                }
            };
            tick.run();
            h.postDelayed(() -> {
                left.setVisibility(INVISIBLE);
                hint.setText("The answer:");
                answer.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
            }, ANSWER_MS);
        });
        return col;
    }

    /**
     * The day's prayer times: whose, the Islamic date, then each prayer's beginning with the jamā'ah under it, the next
     * one lit with its countdown (put right each time the card comes up). In Ramadan, iftar or sehri first.
     */
    private View prayers(Context c, Content k) {
        String who = k.prayerSource == null ? "" : k.prayerSource.toUpperCase(Locale.UK);
        LinearLayout col = heading(c, "PRAYER TIMES \u00B7 " + who, 0xFF5EE0C8, null);
        String sub = k.hijri;
        if (k.ramadan != null) sub = (sub == null ? "" : sub + "  \u00B7  ") + "Ramadan: " + k.ramadan[0] + " " + k.ramadan[1];
        if (sub != null) {
            TextView h2 = Ui.text(c, sub, 13, 0xFFB8BED6, false);
            h2.setPadding(0, px(4), 0, 0);
            col.addView(h2);
        }
        LinearLayout row = new LinearLayout(c);
        row.setPadding(0, px(14), 0, 0);
        List<LinearLayout> cells = new ArrayList<>();
        boolean jamaah = false;
        for (String[] p : k.prayers) {
            LinearLayout one = new LinearLayout(c);
            one.setOrientation(LinearLayout.VERTICAL);
            one.setGravity(Gravity.CENTER_HORIZONTAL);
            one.setPadding(px(4), px(10), px(4), px(10));
            boolean rise = "Sunrise".equals(p[0]);
            TextView name = Ui.text(c, p[0], 12, rise ? 0xFF8A90AA : 0xFFDDE3FF, true);
            name.setMaxLines(1);
            one.addView(name);
            TextView at = Ui.text(c, p[1], 19, rise ? 0xFF8A90AA : 0xFFFFFFFF, true);
            at.setFontFeatureSettings("tnum");
            one.addView(at);
            TextView jam = Ui.text(c, p[2].isEmpty() ? " " : p[2], 12, 0xFF5EE0C8, false);
            jam.setFontFeatureSettings("tnum");
            jam.setMaxLines(1);
            one.addView(jam);
            if (!p[2].isEmpty()) jamaah = true;
            one.setTag(p[0]);
            cells.add(one);
            row.addView(one, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        col.addView(row, new LinearLayout.LayoutParams(-1, -2));
        if (jamaah) {
            TextView key = Ui.text(c, "When each prayer's time begins, the mosque's jam\u0101'ah under it", 11, 0xFF8A90AA, false);
            key.setPadding(0, px(4), 0, 0);
            col.addView(key);
        }
        TextView next = Ui.text(c, "", 15, 0xFF5EE0C8, true);
        next.setPadding(0, px(12), 0, 0);
        col.addView(next);
        onShow.put(col, () -> {
            com.gathin.tvhomephone.Prayer.Slot n = com.gathin.tvhomephone.Prayer.next(c);
            String line = com.gathin.tvhomephone.Prayer.nextLine(c);
            next.setText(line == null ? "" : "Next: " + line);
            for (LinearLayout one : cells) {
                boolean lit = n != null && n.label().equals(one.getTag()) && n.begins.toLocalDate().equals(LocalDate.now());
                one.setBackground(lit ? Ui.round(c, 0x335EE0C8, 12) : null);
            }
        });
        return col;
    }

    /** Today: the date, the Islamic date, the weather and sunset (Maghrib). */
    private View today(Context c, Content k) {
        LinearLayout col = heading(c, "TODAY", 0xFF8FC3FF, LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)));
        ((TextView) col.getChildAt(1)).setTextSize(26);
        if (k.hijri != null) {
            TextView h2 = Ui.text(c, k.hijri, 15, 0xFF5EE0C8, true);
            h2.setPadding(0, px(4), 0, 0);
            col.addView(h2);
        }
        if (k.weather != null) {
            int[] chance = (int[]) k.weather[2];
            int most = 0;
            for (int x : chance) most = Math.max(most, x);
            TextView w = Ui.text(c, k.weather[0] + "\u00B0  " + k.weather[1]
                    + (most >= 40 ? "  \u00B7  " + most + "% chance of rain in the next hours" : "  \u00B7  dry for now"), 17, 0xFFFFFFFF, false);
            w.setPadding(0, px(12), 0, 0);
            col.addView(w);
        }
        for (String[] p : k.prayers) {
            if (!"Maghrib".equals(p[0])) continue;
            TextView sun = Ui.text(c, "Sunset (Maghrib) " + p[1], 15, 0xFFFFC93C, false);
            sun.setPadding(0, px(6), 0, 0);
            col.addView(sun);
        }
        return col;
    }

    /** Bangladesh's flag: a green field, the red disc a little towards the pole. */
    private static final class Flag extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Flag(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), ht = getHeight();
            p.setColor(0xFF006A4E);
            cv.drawRoundRect(0, 0, w, ht, ht * 0.12f, ht * 0.12f, p);
            p.setColor(0xFFF42A41);
            cv.drawCircle(w * 0.45f, ht / 2f, ht * 0.4f, p);
        }
    }

    /** The trailer, playing with its sound (the channel's is off), its name over it. */
    @SuppressLint("SetJavaScriptEnabled")
    private View trailerCard(Context c, Content k) {
        LinearLayout col = heading(c, "TRAILER · " + (k.trailerTitle == null ? "" : k.trailerTitle.toUpperCase(Locale.UK)), 0xFFFF6B6B, null);
        trailer = new WebView(c);
        trailer.setBackgroundColor(0xFF000000);
        trailer.getSettings().setJavaScriptEnabled(true);
        trailer.getSettings().setMediaPlaybackRequiresUserGesture(false);
        trailerHtml = "<html><body style='margin:0;background:#000'><iframe style='border:0;width:100%;height:100%' "
                + "src='https://www.youtube-nocookie.com/embed/" + k.trailerKey + "?autoplay=1&controls=0&rel=0&playsinline=1&modestbranding=1' "
                + "allow='autoplay; encrypted-media'></iframe></body></html>";
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, px(200));
        wp.topMargin = px(10);
        col.addView(trailer, wp);
        trailerCard = col;
        return col;
    }
}
