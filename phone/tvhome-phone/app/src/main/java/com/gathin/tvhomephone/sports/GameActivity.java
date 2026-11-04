package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.SyncStore;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * One game, as the TV's Live Centre has it: the scores box, where it is on (a link into Live TV when the playlist has
 * the channel), Remind me before it starts, then the events, the stats and both line-ups. Read again every 30 seconds
 * while it is live.
 */
public class GameActivity extends AppCompatActivity {
    private static final String SLUG = "slug", ID = "id", GAME = "game";
    private Game game;
    private LinearLayout body;
    private ScoreBug bug;
    private TextView names, line;
    private LinearLayout watch;
    private boolean open;

    public static Intent intent(Context c, Game g) {
        return new Intent(c, GameActivity.class).putExtra(SLUG, g.slug).putExtra(ID, g.id).putExtra(GAME, g.json().toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            game = Game.from(new JSONObject(getIntent().getStringExtra(GAME)));
        } catch (JSONException | NullPointerException e) {
            finish();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.ground));
        LinearLayout col = Ui.column(this);
        int pad = Ui.dp(this, 18);
        col.setPadding(pad, pad, pad, pad);
        scroll.addView(col);
        LinearLayout top = Ui.row(this);
        TextView back = Ui.text(this, "‹", 30, getColor(R.color.text), false);
        back.setPadding(0, 0, Ui.dp(this, 14), 0);
        back.setOnClickListener(v -> finish());
        back.setContentDescription("Back");
        top.addView(back);
        top.addView(Ui.heading(this, game.leagueName == null || game.leagueName.isEmpty() ? game.league : game.leagueName, getColor(R.color.sports)));
        col.addView(top);
        bug = new ScoreBug(this);
        col.addView(bug, Ui.margins(this, 0, 6, 0, 8));
        names = Ui.text(this, "", 17, getColor(R.color.text), true);
        names.setGravity(Gravity.CENTER);
        col.addView(names);
        line = Ui.text(this, "", 14, getColor(R.color.text_soft), false);
        line.setGravity(Gravity.CENTER);
        line.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 6));
        col.addView(line);
        watch = Ui.column(this);
        watch.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(watch, Ui.matchWrap());
        body = Ui.column(this);
        col.addView(body, Ui.matchWrap());
        setContentView(scroll);
        paint();
        App.io.execute(() -> {
            String ch = Channels.best(this, game);
            App.main.post(() -> showWatch(ch));
        });
    }

    @Override protected void onResume() {
        super.onResume();
        open = true;
        refresh();
    }

    @Override protected void onPause() {
        open = false;
        super.onPause();
    }

    private void refresh() {
        App.io.execute(() -> {
            Game fresh = null;
            LocalDate day = java.time.Instant.ofEpochMilli(game.kickoff).atZone(ZoneId.systemDefault()).toLocalDate();
            for (Game g : Espn.scoreboard(game.slug, day, 15_000L)) if (g.id.equals(game.id)) fresh = g;
            Espn.Summary s = Espn.summary(game.slug, game.id, 20_000L);
            Game got = fresh;
            App.main.post(() -> {
                if (isFinishing()) return;
                if (got != null) game = got;
                paint();
                details(s);
                if (open && game.state == Game.LIVE) App.main.postDelayed(this::refresh, 30_000L);
            });
        });
    }

    private void paint() {
        bug.show(game);
        names.setText(game.homeName + "  v  " + game.awayName);
        String when = java.time.Instant.ofEpochMilli(game.kickoff).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm"));
        line.setText(when + (game.venue != null ? " · " + game.venue : ""));
    }

    private void showWatch(String channel) {
        watch.removeAllViews();
        if (channel != null) watch.addView(Ui.liveOn(this, channel));
        if (game.state == Game.PRE) {
            String key = "game:" + game.id;
            Button b = new Button(this);
            boolean set = SyncStore.get(this, "reminder", key) != null;
            b.setText(set ? R.string.sports_reminded : R.string.sports_remind_me);
            b.setOnClickListener(v -> {
                if (SyncStore.get(this, "reminder", key) != null) {
                    SyncStore.delete(this, "reminder", key);
                    b.setText(R.string.sports_remind_me);
                } else {
                    SyncStore.put(this, "reminder", key, reminder(game, channel));
                    b.setText(R.string.sports_reminded);
                }
            });
            watch.addView(b, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    /** The synced reminder for a game (SYNC.md "reminder"). */
    static JSONObject reminder(Game g, String channel) {
        JSONObject d = new JSONObject();
        try {
            d.put("title", g.homeName + " v " + g.awayName).put("at", g.kickoff).put("kind", "game").put("slug", g.slug)
                    .put("id", g.id).put("channel", channel == null ? "" : channel);
        } catch (JSONException ignored) {
            // plain fields
        }
        return d;
    }

    private void details(Espn.Summary s) {
        body.removeAllViews();
        if (!s.events.isEmpty()) {
            body.addView(Ui.heading(this, "Events", getColor(R.color.sports)));
            for (String[] e : s.events) {
                LinearLayout r = Ui.row(this);
                r.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
                TextView m = Ui.text(this, e[0], 14, getColor(R.color.text_soft), true);
                r.addView(m, new LinearLayout.LayoutParams(Ui.dp(this, 56), LinearLayout.LayoutParams.WRAP_CONTENT));
                String club = "home".equals(e[3]) ? game.home : "away".equals(e[3]) ? game.away : "";
                TextView w = Ui.text(this, icon(e[1]) + "  " + e[2] + (club.isEmpty() ? "" : "  ·  " + club), 15, getColor(R.color.text), false);
                r.addView(w, Ui.weight(1));
                body.addView(r);
            }
        }
        if (!s.stats.isEmpty()) {
            body.addView(Ui.heading(this, "Stats", getColor(R.color.sports)));
            for (String[] st : s.stats) body.addView(statRow(st));
        }
        if (!s.homeLineup.isEmpty() || !s.awayLineup.isEmpty()) {
            body.addView(Ui.heading(this, "Line-ups", getColor(R.color.sports)));
            LinearLayout two = Ui.row(this);
            two.setGravity(Gravity.TOP);
            two.addView(lineup(game.homeName, s.homeFormation, s.homeLineup), Ui.weight(1));
            two.addView(lineup(game.awayName, s.awayFormation, s.awayLineup), Ui.weight(1));
            body.addView(two);
        }
        if (body.getChildCount() == 0) Ui.empty(body, game.state == Game.PRE ? "Line-ups come about an hour before kick-off." : "Nothing yet.");
    }

    private static String icon(String what) {
        String w = what.toLowerCase(java.util.Locale.ROOT);
        if (w.contains("goal")) return "⚽";
        if (w.contains("red")) return "🟥";
        if (w.contains("yellow")) return "🟨";
        if (w.contains("sub")) return "🔁";
        if (w.contains("var")) return "📺";
        if (w.contains("penalty")) return "❌";
        return "•";
    }

    private View statRow(String[] st) {
        LinearLayout r = Ui.column(this);
        r.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        LinearLayout labels = Ui.row(this);
        labels.addView(Ui.text(this, st[1], 15, getColor(R.color.text), true), Ui.weight(1));
        TextView mid = Ui.text(this, st[0], 13, getColor(R.color.text_soft), false);
        mid.setGravity(Gravity.CENTER);
        labels.addView(mid, Ui.weight(2));
        TextView right = Ui.text(this, st[2], 15, getColor(R.color.text), true);
        right.setGravity(Gravity.END);
        labels.addView(right, Ui.weight(1));
        r.addView(labels);
        float h = number(st[1]), a = number(st[2]);
        if (h + a > 0) {
            LinearLayout bar = Ui.row(this);
            View hv = new View(this), av = new View(this);
            hv.setBackground(Ui.fill(this, getColor(R.color.bug_blue_top), 3));
            av.setBackground(Ui.fill(this, 0x66FFFFFF, 3));
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, Ui.dp(this, 6), h), ap = new LinearLayout.LayoutParams(0, Ui.dp(this, 6), a);
            hp.rightMargin = Ui.dp(this, 3);
            bar.addView(hv, hp);
            bar.addView(av, ap);
            r.addView(bar, Ui.margins(this, 0, 4, 0, 0));
        }
        return r;
    }

    private static float number(String s) {
        try {
            return Float.parseFloat(s.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private View lineup(String club, String formation, List<String[]> players) {
        LinearLayout c = Ui.column(this);
        c.setPadding(0, 0, Ui.dp(this, 8), 0);
        c.addView(Ui.text(this, club + (formation.isEmpty() ? "" : "  " + formation), 15, getColor(R.color.text), true));
        boolean bench = false;
        for (String[] p : players) {
            if (!"1".equals(p[3]) && !bench) {
                bench = true;
                TextView b = Ui.text(this, "Bench", 12, getColor(R.color.text_dim), true);
                b.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 2));
                c.addView(b);
            }
            c.addView(Ui.text(this, (p[0].isEmpty() ? "" : p[0] + "  ") + p[1] + (p[2].isEmpty() ? "" : "  " + p[2]), 14,
                    "1".equals(p[3]) ? getColor(R.color.text) : getColor(R.color.text_soft), false));
        }
        FrameLayout f = new FrameLayout(this);
        f.addView(c);
        return f;
    }
}
