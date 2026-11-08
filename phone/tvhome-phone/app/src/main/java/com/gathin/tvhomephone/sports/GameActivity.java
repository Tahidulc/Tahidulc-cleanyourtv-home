package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import java.util.Locale;

/**
 * One game: a header card with both crests, the score (or the kick-off time), the clock, the scorers, where it is on
 * and Remind me; then three tabs: the timeline (the game on a pitch, each shot, goal and card where it happened, then
 * its moments newest first, the home side's on the left and the away side's on the right), both line-ups on a pitch
 * with the substitutes under it, and the stats in each side's colours. Read again every 30 seconds while it is live.
 */
public class GameActivity extends AppCompatActivity {
    private static final String SLUG = "slug", ID = "id", GAME = "game";
    private static final int TIMELINE = GameTabs.TIMELINE;
    private static final String[] TAB_NAMES = GameTabs.NAMES;

    private Game game;
    private Espn.Summary summary = new Espn.Summary();
    private String channel;
    private boolean channelKnown;
    private int tab = -1;
    private LinearLayout hero, tabs, body;
    private boolean open;
    /** The tabs' pages (GameTabs, shared with the player's match centre). */
    private GameTabs parts;

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
        parts = new GameTabs(this, () -> !isFinishing());
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.ground));
        scroll.setFillViewport(true);
        LinearLayout col = Ui.column(this);
        int side = Math.max(Ui.dp(this, 14), (getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 620)) / 2);
        col.setPadding(side, Ui.dp(this, 8), side, Ui.dp(this, 28));
        scroll.addView(col);
        LinearLayout top = Ui.row(this);
        TextView back = Ui.text(this, "‹", 30, getColor(R.color.text), false);
        back.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 14), 0);
        back.setOnClickListener(v -> finish());
        back.setContentDescription("Back");
        top.addView(back);
        top.addView(Ui.compHeading(this, game.leagueName == null || game.leagueName.isEmpty() ? game.league : game.leagueName,
                Competitions.logo(game), getColor(R.color.text_soft)), Ui.weight(1));
        col.addView(top);
        hero = Ui.column(this);
        col.addView(hero, Ui.matchWrap());
        tabs = Ui.row(this);
        col.addView(tabs, Ui.margins(this, 0, 16, 0, 4));
        body = Ui.column(this);
        col.addView(body, Ui.matchWrap());
        setContentView(scroll);
        paint();
        App.io.execute(() -> {
            String ch = Channels.best(this, game);
            App.main.post(() -> {
                channel = ch;
                channelKnown = true;
                if (!isFinishing()) paintHero();
            });
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
            if (game.state != Game.PRE) com.gathin.tvhomephone.live.FotMob.addTo(s.stats, game.id, game.homeName, game.awayName, game.kickoff);
            Game got = fresh;
            App.main.post(() -> {
                if (isFinishing()) return;
                if (got != null) game = got;
                summary = s;
                paint();
                if (open && game.state == Game.LIVE) App.main.postDelayed(this::refresh, 30_000L);
            });
        });
    }

    private void paint() {
        paintHero();
        paintTabs();
        paintBody();
    }

    // ---- colours ------------------------------------------------------------------------------------------------

    /** {home, away}: the kits worn, told apart. */
    private int[] kits() {
        return Glyphs.kits(summary.homeKit, summary.homeKitAlt, game.homeColor, summary.awayKit, summary.awayKitAlt, game.awayColor);
    }

    private static int mix(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    // ---- the header card ----------------------------------------------------------------------------------------

    private void paintHero() {
        hero.removeAllViews();
        int[] k = kits();
        int navy = 0xFF161A4A;
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{mix(navy, Glyphs.onDark(k[0]), 0.34f), navy, mix(navy, Glyphs.onDark(k[1]), 0.34f)});
        bg.setCornerRadius(Ui.dp(this, 24));
        bg.setStroke(Ui.dp(this, 1), getColor(R.color.surface_line));
        LinearLayout card = Ui.column(this);
        card.setBackground(bg);
        int p = Ui.dp(this, 14);
        card.setPadding(p, Ui.dp(this, 18), p, Ui.dp(this, 16));
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        row.addView(club(true), Ui.weight(1));
        row.addView(middle(), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.05f));
        row.addView(club(false), Ui.weight(1));
        card.addView(row);
        if (game.state == Game.PRE && (!game.homeForm.isEmpty() || !game.awayForm.isEmpty())) card.addView(form(), Ui.margins(this, 0, 14, 0, 0));
        String when = java.time.Instant.ofEpochMilli(game.kickoff).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm"));
        String place = game.venue == null || game.venue.isEmpty() ? when : game.state == Game.PRE ? game.venue : when + " · " + game.venue;
        TextView venue = Ui.text(this, place, 12.5f, getColor(R.color.text_dim), false);
        venue.setGravity(Gravity.CENTER);
        card.addView(venue, Ui.margins(this, 0, 14, 0, 0));
        LinearLayout actions = Ui.column(this);
        actions.setGravity(Gravity.CENTER_HORIZONTAL);
        if (channel != null) {
            View on = Ui.liveOn(this, channel);
            on.setBackground(Ui.fill(this, 0x24FFFFFF, 18));
            on.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
            actions.addView(on, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (game.state != Game.POST && channelKnown) {
            TextView also = Ui.text(this, getString(R.string.tile_also_showing) + "  ›", 13.5f, getColor(R.color.text), true);
            also.setGravity(Gravity.CENTER);
            also.setBackground(Ui.fill(this, 0x1FFFFFFF, 16));
            also.setPadding(Ui.dp(this, 14), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7));
            also.setOnClickListener(v -> com.gathin.tvhomephone.live.AlsoShowing.open(this, game, null,
                    ch -> com.gathin.tvhomephone.live.PlayerActivity.start(this, ch.key, com.gathin.tvhomephone.live.Library.ALL)));
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ap.topMargin = Ui.dp(this, 8);
            actions.addView(also, ap);
        }
        if (game.state == Game.PRE && channelKnown) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 42));
            lp.topMargin = Ui.dp(this, 10);
            actions.addView(remind(), lp);
        }
        if (actions.getChildCount() > 0) card.addView(actions, Ui.margins(this, 0, 12, 0, 0));
        hero.addView(card, Ui.matchWrap());
    }

    /** A side: its crest on a white disc, its name, and its scorers and red cards under it. */
    private View club(boolean home) {
        LinearLayout c = Ui.column(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout disc = new FrameLayout(this);
        disc.setBackground(Ui.fill(this, 0xFFFFFFFF, 34));
        disc.setElevation(Ui.dp(this, 3));
        ImageView crest = Ui.image(this, home ? game.homeLogo : game.awayLogo, 44);
        crest.setContentDescription((home ? game.homeName : game.awayName) + " crest");
        disc.addView(crest, new FrameLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44), Gravity.CENTER));
        c.addView(disc, new LinearLayout.LayoutParams(Ui.dp(this, 68), Ui.dp(this, 68)));
        TextView name = Ui.text(this, home ? game.homeName : game.awayName, 15, getColor(R.color.text), true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(name, Ui.margins(this, 0, 9, 0, 0));
        String side = home ? "home" : "away";
        StringBuilder scorers = new StringBuilder();
        for (String[] g : game.goals) {
            if (!side.equals(g[3])) continue;
            if (scorers.length() > 0) scorers.append('\n');
            scorers.append(surname(g[1])).append(' ').append(g[2]).append("pen".equals(g[4]) ? " (pen)" : "og".equals(g[4]) ? " (og)" : "");
        }
        if (scorers.length() > 0) c.addView(marked(scorers.toString(), Glyphs.GOAL), Ui.margins(this, 0, 6, 0, 0));
        StringBuilder reds = new StringBuilder();
        for (String[] r : game.reds) {
            if (!side.equals(r[3])) continue;
            if (reds.length() > 0) reds.append('\n');
            reds.append(surname(r[1])).append(' ').append(r[2]);
        }
        if (reds.length() > 0) c.addView(marked(reds.toString(), Glyphs.RED), Ui.margins(this, 0, 4, 0, 0));
        return c;
    }

    private TextView marked(String words, int glyph) {
        TextView t = Ui.text(this, words, 12, getColor(R.color.text_soft), false);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(Ui.dp(this, 2), 1f);
        Drawable d = Glyphs.drawable(this, glyph, 12);
        d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        t.setCompoundDrawables(d, null, null, null);
        t.setCompoundDrawablePadding(Ui.dp(this, 5));
        return t;
    }

    private static String surname(String name) {
        if (name == null) return "";
        int sp = name.lastIndexOf(' ');
        return sp > 0 && name.length() - sp > 3 ? name.substring(sp + 1) : name;
    }

    /** The score (or the kick-off time) and the clock under it in its pill. */
    private View middle() {
        LinearLayout m = Ui.column(this);
        m.setGravity(Gravity.CENTER_HORIZONTAL);
        String big = game.state == Game.PRE ? java.time.Instant.ofEpochMilli(game.kickoff).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm")) : game.homeScore + " – " + game.awayScore;
        TextView score = Ui.text(this, big, game.state == Game.PRE ? 32 : 40, getColor(R.color.text), true);
        score.setGravity(Gravity.CENTER);
        score.setIncludeFontPadding(false);
        score.setMaxLines(1);
        m.addView(score, Ui.margins(this, 0, 12, 0, 0));
        String pill;
        int fill, ink;
        if (game.state == Game.LIVE && game.halftime) {
            pill = "HALF TIME";
            fill = Glyphs.AMBER;
            ink = 0xFF2A1A00;
        } else if (game.state == Game.LIVE) {
            pill = "●  LIVE  " + game.detail;
            fill = getColor(R.color.live_green);
            ink = 0xFF06281A;
        } else if (game.state == Game.POST) {
            pill = "FULL TIME";
            fill = 0x2EFFFFFF;
            ink = Color.WHITE;
        } else {
            pill = java.time.Instant.ofEpochMilli(game.kickoff).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("EEE d MMM")).toUpperCase(Locale.ROOT);
            fill = 0x2EFFFFFF;
            ink = Color.WHITE;
        }
        TextView t = Ui.text(this, pill, 11, ink, true);
        t.setLetterSpacing(0.08f);
        t.setMaxLines(1);
        if (game.state == Game.LIVE && !game.halftime) {
            // The clock in minutes and seconds, going on while the page is open.
            t.setFontFeatureSettings("tnum");
            LiveClock.bind(t, game, w -> "●  LIVE  " + w);
        }
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.fill(this, fill, 12));
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        m.addView(t, lp);
        return m;
    }

    /** Each side's last five results as coloured discs, oldest first, "FORM" between them. */
    private View form() {
        LinearLayout r = Ui.row(this);
        r.setGravity(Gravity.CENTER);
        r.addView(formDiscs(game.homeForm, Gravity.END), Ui.weight(1));
        TextView label = Ui.text(this, "FORM", 10, getColor(R.color.text_dim), true);
        label.setLetterSpacing(0.14f);
        label.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        r.addView(label);
        r.addView(formDiscs(game.awayForm, Gravity.START), Ui.weight(1));
        r.setContentDescription("Form. " + game.homeName + ": " + spell(game.homeForm) + ". " + game.awayName + ": " + spell(game.awayForm));
        return r;
    }

    private static String spell(String form) {
        StringBuilder b = new StringBuilder();
        for (char ch : form.toCharArray()) b.append(ch == 'W' ? "won " : ch == 'D' ? "drew " : "lost ");
        return b.toString().trim();
    }

    private View formDiscs(String form, int gravity) {
        LinearLayout r = Ui.row(this);
        r.setGravity(gravity | Gravity.CENTER_VERTICAL);
        r.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        for (char ch : form.toCharArray()) {
            int fill = ch == 'W' ? 0xFF2FB36B : ch == 'D' ? 0xFF6B7194 : 0xFFD9434A;
            TextView d = Ui.text(this, String.valueOf(ch), 10, Color.WHITE, true);
            d.setGravity(Gravity.CENTER);
            d.setBackground(Ui.fill(this, fill, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20));
            lp.leftMargin = Ui.dp(this, 2);
            lp.rightMargin = Ui.dp(this, 2);
            r.addView(d, lp);
        }
        return r;
    }

    /** Remind me: a pill with a bell; green once set. */
    private View remind() {
        String key = "game:" + game.id;
        TextView b = Ui.text(this, "", 14, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 20), 0);
        b.setCompoundDrawablePadding(Ui.dp(this, 8));
        b.setClickable(true);
        b.setFocusable(true);
        Runnable show = () -> {
            boolean set = SyncStore.get(this, "reminder", key) != null;
            b.setText(set ? R.string.sports_reminded : R.string.sports_remind_me);
            int ink = set ? 0xFF06281A : Color.WHITE;
            b.setTextColor(ink);
            GradientDrawable bg = Ui.fill(this, set ? getColor(R.color.live_green) : 0x1FFFFFFF, 21);
            if (!set) bg.setStroke(Ui.dp(this, 1), 0x4DFFFFFF);
            b.setBackground(bg);
            Drawable bell = getDrawable(R.drawable.live_ic_bell);
            if (bell != null) {
                bell = bell.mutate();
                bell.setTint(ink);
                int s = Ui.dp(this, 18);
                bell.setBounds(0, 0, s, s);
            }
            b.setCompoundDrawables(bell, null, null, null);
        };
        show.run();
        b.setOnClickListener(v -> {
            if (SyncStore.get(this, "reminder", key) != null) SyncStore.delete(this, "reminder", key);
            else SyncStore.put(this, "reminder", key, reminder(game, channel));
            show.run();
        });
        return b;
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

    // ---- the tabs -----------------------------------------------------------------------------------------------

    private List<Integer> available() {
        parts.set(game, summary);
        return parts.available();
    }

    private void paintTabs() {
        tabs.removeAllViews();
        List<Integer> have = available();
        if (!have.contains(tab)) tab = have.isEmpty() ? -1 : have.contains(TIMELINE) && game.state != Game.PRE ? TIMELINE : have.get(0);
        if (have.size() < 2) {
            tabs.setVisibility(View.GONE);
            return;
        }
        tabs.setVisibility(View.VISIBLE);
        tabs.setBackground(Ui.fill(this, 0x1AFFFFFF, 22));
        int pad = Ui.dp(this, 4);
        tabs.setPadding(pad, pad, pad, pad);
        for (int t : have) {
            boolean on = t == tab;
            TextView v = Ui.text(this, TAB_NAMES[t], 14, on ? 0xFF10164A : getColor(R.color.text_soft), true);
            v.setGravity(Gravity.CENTER);
            v.setMaxLines(1);
            if (on) v.setBackground(Ui.fill(this, Color.WHITE, 18));
            v.setOnClickListener(x -> {
                tab = t;
                paintTabs();
                paintBody();
            });
            v.setSelected(on);
            tabs.addView(v, new LinearLayout.LayoutParams(0, Ui.dp(this, 38), 1));
        }
    }

    private void paintBody() {
        body.removeAllViews();
        parts.set(game, summary);
        if (tab >= 0) parts.paint(tab, body);
        else Ui.empty(body, game.state == Game.PRE ? "The line-ups come about an hour before kick-off." : "Nothing yet.");
    }

}
