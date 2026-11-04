package com.gathin.tvhomephone.home;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
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
 * broadcast bug) to swipe between today's games, your clubs and live ones first; your clubs' games today with where
 * they are on (a link into Live TV when the playlist has the channel); the latest alerts; then the watching cards.
 */
public class HomeFragment extends Fragment {
    private TextView date, clock, weather, count;
    private RecyclerView bugs;
    private final List<Game> shown = new ArrayList<>();
    private LinearLayout today, latest;
    private SwipeRefreshLayout swipe;
    private boolean visible;
    private int gen;

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
        new PagerSnapHelper().attachToRecyclerView(bugs);
        bugs.setAdapter(new Bugs());
        bugs.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView r, int s) {
                if (s == RecyclerView.SCROLL_STATE_IDLE) counter();
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
        App.main.post(tick);
        App.main.post(reread);
    }

    @Override public void onPause() {
        visible = false;
        App.main.removeCallbacks(tick);
        App.main.removeCallbacks(reread);
        super.onPause();
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
        shown.clear();
        shown.addAll(bugList);
        bugs.getAdapter().notifyDataSetChanged();
        bugs.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
        counter();
        today.removeAllViews();
        if (mine.isEmpty()) Ui.empty(today, "No games for your clubs today.");
        List<View> cards = new ArrayList<>();
        for (int i = 0; i < mine.size(); i++) {
            Game g = mine.get(i);
            LinearLayout card = GameRow.build(c, g, true);
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
        int at = lm == null ? 0 : Math.max(0, lm.findFirstCompletelyVisibleItemPosition());
        count.setText((at + 1) + " of " + shown.size() + "  ·  swipe for more");
    }

    /** The scores box, one game a page. */
    private final class Bugs extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            ScoreBug b = new ScoreBug(parent.getContext());
            b.setLayoutParams(new RecyclerView.LayoutParams(parent.getWidth() > 0 ? parent.getWidth() : ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerView.ViewHolder(b) {};
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            Game g = shown.get(pos);
            ((ScoreBug) h.itemView).show(g);
            h.itemView.setOnClickListener(v -> v.getContext().startActivity(GameActivity.intent(v.getContext(), g)));
        }

        @Override public int getItemCount() { return shown.size(); }
    }
}
