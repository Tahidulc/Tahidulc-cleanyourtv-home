package com.gathin.tvhomephone.home;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.AlertLog;
import com.gathin.tvhomephone.sports.Channels;
import com.gathin.tvhomephone.sports.Clubs;
import com.gathin.tvhomephone.sports.Competitions;
import com.gathin.tvhomephone.sports.Espn;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.GameActivity;
import com.gathin.tvhomephone.sports.GameRow;
import com.gathin.tvhomephone.sports.ScoreBug;
import com.gathin.tvhomephone.sports.Ui;
import com.gathin.tvhomephone.sports.Weather;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Home, as the TV's top bar and Home have it: the date, the time and Manchester's weather; the scores box (the
 * broadcast bug) to swipe between today's games, your clubs and live ones first, moving on by itself every 8 seconds
 * as the TV's does (held while a finger is on it); your clubs' games today, each under its competition's logo, with
 * where they are on (the channel's logo; a link into Live TV when the playlist has the channel); the latest alerts;
 * then the watching cards.
 */
public class HomeFragment extends Fragment {
    private TextView date, clock, weather, count;
    private RecyclerView bugs;
    /** The games of the clubs the family follows, marked Your club on the scores box. */
    private final java.util.Set<String> mineIds = new java.util.HashSet<>();
    private final List<Game> shown = new ArrayList<>();
    private LinearLayout today, latest;
    private SwipeRefreshLayout swipe;
    private boolean visible, touching;
    private int gen;
    private Pager pager;
    /** The scores box moves on to its next game every AUTO_MS, as the TV's does. */
    private static final long AUTO_MS = 8_000L;
    private static final int LOOPS = 1000;
    private final Runnable auto = this::advance;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            stamp();
            App.main.postDelayed(this, 15_000L);
        }
    };
    private final Runnable reread = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            load();
            App.main.postDelayed(this, 30_000L);
        }
    };

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        Context c = requireContext();
        swipe = new SwipeRefreshLayout(c);
        ScrollView scroll = new ScrollView(c);
        scroll.setBackgroundColor(c.getColor(R.color.ground));
        LinearLayout col = Ui.column(c);
        int pad = Ui.dp(c, 18);
        col.setPadding(pad, pad, pad, pad);
        scroll.addView(col);
        swipe.addView(scroll);
        swipe.setOnRefreshListener(this::load);

        LinearLayout top = Ui.row(c);
        LinearLayout left = Ui.column(c);
        date = Ui.text(c, "", 14, c.getColor(R.color.text_soft), true);
        date.setLetterSpacing(0.1f);
        left.addView(date);
        weather = Ui.text(c, "Manchester", 14, c.getColor(R.color.text_soft), false);
        left.addView(weather);
        top.addView(left, Ui.weight(1));
        clock = Ui.text(c, "", 44, c.getColor(R.color.text), false);
        clock.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        top.addView(clock);
        col.addView(top);

        bugs = new RecyclerView(c);
        bugs.setLayoutManager(new LinearLayoutManager(c, LinearLayoutManager.HORIZONTAL, false));
        pager = new Pager();
        pager.attachToRecyclerView(bugs);
        bugs.setAdapter(new Bugs());
        bugs.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView r, int s) {
                if (s == RecyclerView.SCROLL_STATE_IDLE) {
                    counter();
                    autoLater();
                } else if (s == RecyclerView.SCROLL_STATE_DRAGGING) {
                    App.main.removeCallbacks(auto);
                }
            }
        });
        // A finger on the box holds it; it moves on again AUTO_MS after it is let go.
        bugs.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override public boolean onInterceptTouchEvent(@NonNull RecyclerView r, @NonNull MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    touching = true;
                    App.main.removeCallbacks(auto);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    touching = false;
                    autoLater();
                }
                return false;
            }
        });
        col.addView(bugs, Ui.margins(c, 0, 16, 0, 4));
        count = Ui.text(c, "", 12, c.getColor(R.color.text_dim), false);
        count.setGravity(Gravity.CENTER);
        col.addView(count);

        col.addView(Ui.heading(c, "Your clubs today", c.getColor(R.color.sports)));
        today = Ui.column(c);
        col.addView(today, Ui.matchWrap());
        col.addView(Ui.heading(c, "Latest alerts", c.getColor(R.color.sports)));
        latest = Ui.column(c);
        col.addView(latest, Ui.matchWrap());
        col.addView(com.gathin.tvhomephone.watch.HomeWatch.build(this, col));
        stamp();
        return swipe;
    }

    @Override public void onResume() {
        super.onResume();
        visible = true;
        touching = false;
        App.main.post(tick);
        App.main.post(reread);
        autoLater();
    }

    @Override public void onPause() {
        visible = false;
        App.main.removeCallbacks(tick);
        App.main.removeCallbacks(reread);
        App.main.removeCallbacks(auto);
        super.onPause();
    }

    // ---- the scores box moving on by itself -----------------------------------------------------------------------

    /** The next game in AUTO_MS, while Home shows, nobody holds the box and it has more than one game. */
    private void autoLater() {
        App.main.removeCallbacks(auto);
        if (visible && !touching && shown.size() > 1) App.main.postDelayed(auto, AUTO_MS);
    }

    /** Slides the next game in from the right, as a swipe does (after the last, the first again). */
    private void advance() {
        if (!visible || touching || shown.size() < 2 || bugs == null || bugs.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
            autoLater();
            return;
        }
        LinearLayoutManager lm = (LinearLayoutManager) bugs.getLayoutManager();
        if (lm == null) return;
        int at = at(lm);
        RecyclerView.Adapter<?> a = bugs.getAdapter();
        if (a == null || at + 1 >= a.getItemCount()) {
            // The end of the long loop (some days in): back to the middle, then on.
            bugs.scrollToPosition(middle(at % shown.size()));
            autoLater();
            return;
        }
        RecyclerView.SmoothScroller slide = pager.slide(lm);
        if (slide == null) {
            bugs.smoothScrollToPosition(at + 1);
        } else {
            slide.setTargetPosition(at + 1);
            lm.startSmoothScroll(slide);
        }
        // The slide's end (idle) sets the next one; this one only if it never started.
        autoLater();
    }

    /** The page the box is on (an adapter position: the game is it modulo shown.size()). */
    private static int at(LinearLayoutManager lm) {
        int at = lm.findFirstCompletelyVisibleItemPosition();
        if (at < 0) at = lm.findFirstVisibleItemPosition();
        return Math.max(0, at);
    }

    /** The position of game i in the middle round of the loop, so the box can go either way from it. */
    private int middle(int i) {
        return shown.size() < 2 ? Math.max(0, i) : (LOOPS / 2) * shown.size() + i;
    }

    /** PagerSnapHelper's own settling slide, as a fling ends with, for the box's moves by itself. */
    private static final class Pager extends PagerSnapHelper {
        RecyclerView.SmoothScroller slide(RecyclerView.LayoutManager lm) {
            return createScroller(lm);
        }
    }

    private void stamp() {
        if (clock == null) return;
        clock.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        date.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM")).toUpperCase(java.util.Locale.ROOT));
    }

    private void load() {
        Context app = requireContext().getApplicationContext();
        int my = ++gen;
        App.io.execute(() -> {
            Set<String> teams = Clubs.starred(app);
            List<Game> all = new ArrayList<>(), mine = new ArrayList<>();
            for (String[] l : Espn.LEAGUES) {
                for (Game g : Espn.scoreboard(l[0], LocalDate.now(), 30_000L)) {
                    all.add(g);
                    if (g.involves(teams)) mine.add(g);
                }
            }
            List<Game> bugList = new ArrayList<>(mine);
            all.sort((a, b) -> a.state != b.state ? Integer.compare(a.state, b.state) : Long.compare(a.kickoff, b.kickoff));
            for (Game g : all) {
                if (bugList.size() >= 12) break;
                boolean big = "eng.1".equals(g.slug) || "uefa.champions".equals(g.slug);
                if (!bugList.contains(g) && big) bugList.add(g);
            }
            List<String> channels = new ArrayList<>();
            for (Game g : mine) channels.add(g.state == Game.POST ? null : Channels.best(app, g));
            Object[] w = Weather.now();
            App.main.post(() -> {
                if (my != gen || !isAdded()) return;
                swipe.setRefreshing(false);
                show(bugList, mine, channels, w);
            });
        });
    }

    private void show(List<Game> bugList, List<Game> mine, List<String> channels, Object[] w) {
        Context c = requireContext();
        if (w != null) weather.setText(w[0] + "°  " + w[1] + " · Manchester");
        int was = shown.size();
        LinearLayoutManager lm = (LinearLayoutManager) bugs.getLayoutManager();
        int on = was == 0 || lm == null ? 0 : at(lm) % was;
        shown.clear();
        shown.addAll(bugList);
        mineIds.clear();
        for (Game g : mine) if (g.id != null) mineIds.add(g.id);
        bugs.getAdapter().notifyDataSetChanged();
        // A new number of games: the same place in the list (or the first), in the loop's middle round.
        if (shown.size() != was) bugs.scrollToPosition(middle(Math.min(on, Math.max(0, shown.size() - 1))));
        bugs.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
        counter();
        autoLater();
        today.removeAllViews();
        if (mine.isEmpty()) Ui.empty(today, "No games for your clubs today.");
        List<View> cards = new ArrayList<>();
        for (int i = 0; i < mine.size(); i++) {
            Game g = mine.get(i);
            // Each with its competition's logo and name: the clubs' games today may be in several.
            LinearLayout card = GameRow.build(c, g, true, true);
            String ch = channels.get(i);
            if (ch != null) card.addView(Ui.liveOn(c, ch));
            cards.add(card);
        }
        GameRow.addAll(today, cards);
        latest.removeAllViews();
        JSONArray log = AlertLog.list(c);
        if (log.length() == 0) Ui.empty(latest, "Goals, cards and results for your clubs come here and to your notifications.");
        for (int i = 0; i < log.length() && i < 8; i++) {
            JSONObject a = log.optJSONObject(i);
            if (a == null) continue;
            LinearLayout card = Ui.card(c, null);
            LinearLayout band = Ui.row(c);
            // The competition's logo (its version for light grounds, on the silver band), F1's for an F1 alert.
            String kind = a.optString("kind");
            String logo = kind.startsWith("f1") ? Competitions.F1_LOGO : Competitions.lightLogo(a.optString("slug", null));
            if (logo != null) {
                android.widget.ImageView v = new android.widget.ImageView(c);
                v.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20));
                lp.rightMargin = Ui.dp(c, 8);
                band.addView(v, lp);
                Ui.load(v, logo, null, () -> v.setVisibility(View.GONE));
            }
            band.addView(Ui.text(c, a.optString("title"), 15, c.getColor(R.color.band_ink), true), Ui.weight(1));
            band.addView(Ui.text(c, java.time.Instant.ofEpochMilli(a.optLong("at")).atZone(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm")), 12, 0xFF33386A, false));
            band.setBackground(Ui.vertical(c.getColor(R.color.band_silver_top), c.getColor(R.color.band_silver_bottom)));
            band.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
            card.setPadding(0, 0, 0, Ui.dp(c, 10));
            card.addView(band, Ui.matchWrap());
            TextView t = Ui.text(c, a.optString("text"), 14, c.getColor(R.color.text_soft), false);
            t.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), 0);
            card.addView(t);
            card.setClipToOutline(true);
            latest.addView(card, Ui.margins(c, 0, 0, 0, 10));
        }
    }

    private void counter() {
        if (shown.size() < 2) { count.setText(""); return; }
        LinearLayoutManager lm = (LinearLayoutManager) bugs.getLayoutManager();
        int at = lm == null ? 0 : at(lm) % shown.size();
        count.setText((at + 1) + " of " + shown.size() + "  ·  swipe for more");
    }

    /**
     * The scores box, one game a page, round and round: with two games or more it holds LOOPS rounds of them (page p
     * is game p modulo their number), so moving on from the last slides the first in from the right.
     */
    private final class Bugs extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context c = parent.getContext();
            android.widget.FrameLayout box = new android.widget.FrameLayout(c);
            box.setLayoutParams(new RecyclerView.LayoutParams(parent.getWidth() > 0 ? parent.getWidth() : ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            ScoreBug b = new ScoreBug(c);
            box.addView(b, new android.widget.FrameLayout.LayoutParams(-1, -2));
            // A family's club's game: a small Your club tag on its top right corner.
            TextView tag = Ui.text(c, getString(R.string.f1_your_club).toUpperCase(java.util.Locale.UK), 9, 0xFF0A0D33, true);
            tag.setLetterSpacing(0.06f);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFFFFC93C);
            bg.setCornerRadii(new float[]{0, 0, Ui.dp(c, 6), Ui.dp(c, 6), 0, 0, Ui.dp(c, 6), Ui.dp(c, 6)});
            tag.setBackground(bg);
            tag.setPadding(Ui.dp(c, 6), Ui.dp(c, 1), Ui.dp(c, 6), Ui.dp(c, 2));
            tag.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            box.addView(tag, new android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
            return new RecyclerView.ViewHolder(box) {};
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            Game g = shown.get(pos % shown.size());
            android.widget.FrameLayout box = (android.widget.FrameLayout) h.itemView;
            ScoreBug b = (ScoreBug) box.getChildAt(0);
            b.show(g);
            boolean mine = g.id != null && mineIds.contains(g.id);
            box.getChildAt(1).setVisibility(mine ? View.VISIBLE : View.GONE);
            if (mine) b.setContentDescription(b.getContentDescription() + ", " + getString(R.string.f1_your_club));
            h.itemView.setOnClickListener(v -> v.getContext().startActivity(GameActivity.intent(v.getContext(), g)));
        }

        @Override public int getItemCount() { return shown.size() < 2 ? shown.size() : shown.size() * LOOPS; }
    }
}
