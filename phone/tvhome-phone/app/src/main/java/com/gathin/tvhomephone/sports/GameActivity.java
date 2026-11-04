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
import java.util.ArrayList;
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
    private static final int TIMELINE = 0, LINEUPS = 1, STATS = 2;
    private static final String[] TAB_NAMES = {"Timeline", "Line-ups", "Stats"};

    private Game game;
    private Espn.Summary summary = new Espn.Summary();
    private String channel;
    private boolean channelKnown;
    private int tab = -1;
    private LinearLayout hero, tabs, body;
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

    /** A player's name as the line-ups shorten it ("Koita", "Vinícius Júnior"), else his surname. */
    private String shortName(String name) {
        for (List<String[]> side : java.util.Arrays.asList(summary.homeLineup, summary.awayLineup)) {
            for (String[] p : side) if (p[Espn.P_NAME].equals(name) && !p[Espn.P_SHORT].isEmpty()) return p[Espn.P_SHORT];
        }
        return surname(name);
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
        List<Integer> out = new ArrayList<>();
        if (!summary.events.isEmpty() || game.state != Game.PRE) out.add(TIMELINE);
        if (Pitch.has(summary.homeLineup, summary.awayLineup)) out.add(LINEUPS);
        if (!summary.stats.isEmpty() && game.state != Game.PRE) out.add(STATS);
        return out;
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
        if (tab == TIMELINE) timeline();
        else if (tab == LINEUPS) lineups();
        else if (tab == STATS) stats();
        else Ui.empty(body, game.state == Game.PRE ? "The line-ups come about an hour before kick-off." : "Nothing yet.");
    }

    // ---- the timeline -------------------------------------------------------------------------------------------

    private void timeline() {
        List<String[]> ev = summary.events;
        // The score after each goal, from the scoreboard's goals when they agree with the summary's.
        List<Integer> goalAt = new ArrayList<>();
        for (int i = 0; i < ev.size(); i++) if (Glyphs.scored(ev.get(i)[1])) goalAt.add(i);
        String[] after = new String[ev.size()];
        int h = 0, a = 0;
        for (int n = 0; n < goalAt.size(); n++) {
            int i = goalAt.get(n);
            String side = goalAt.size() == game.goals.size() ? game.goals.get(n)[3] : ev.get(i)[3];
            if ("home".equals(side)) h++; else a++;
            after[i] = h + "–" + a;
        }
        int htH = 0, htA = 0;
        if (summary.halfTimeAt >= 0) {
            for (int n = 0; n < goalAt.size() && goalAt.get(n) < summary.halfTimeAt; n++) {
                String side = goalAt.size() == game.goals.size() ? game.goals.get(n)[3] : ev.get(goalAt.get(n))[3];
                if ("home".equals(side)) htH++; else htA++;
            }
        }
        if (game.state != Game.PRE) body.addView(map(), Ui.margins(this, 0, 12, 0, 6));
        if (game.state == Game.POST) body.addView(divider("FULL TIME", game.homeScore + "–" + game.awayScore));
        else if (game.state == Game.LIVE) body.addView(divider(game.halftime ? "HALF TIME" : "LIVE · " + game.detail, game.homeScore + "–" + game.awayScore));
        if (ev.isEmpty() && game.state == Game.LIVE) {
            TextView none = Ui.text(this, "No goals or cards yet", 14, getColor(R.color.text_dim), false);
            none.setGravity(Gravity.CENTER);
            none.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            body.addView(none, Ui.matchWrap());
        }
        for (int i = ev.size() - 1; i >= 0; i--) {
            if (summary.halfTimeAt >= 0 && i == summary.halfTimeAt - 1 && !(game.state == Game.LIVE && game.halftime)) {
                body.addView(divider("HALF TIME", htH + "–" + htA));
            }
            body.addView(moment(ev.get(i), after[i]));
        }
        String ko = java.time.Instant.ofEpochMilli(game.kickoff).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));
        if (game.state != Game.PRE) body.addView(divider("KICK-OFF", ko));
    }

    /** A colour that shows on the grass: a green kit turned to white (or to its second colour). */
    private static int onGrass(int kit, String alt) {
        if (Glyphs.distance(kit, 0xFF1C6A44) >= 0.6 && Glyphs.luminance(kit) > 0.03) return kit;
        int a = Glyphs.color(alt, 0);
        return a != 0 && Glyphs.distance(a, 0xFF1C6A44) >= 0.6 && Glyphs.luminance(a) > 0.03 ? a : 0xFFF4F5FA;
    }

    /**
     * The game on a pitch: each side's shots and how many were on target above it, which way each attacks, the shots,
     * goals and cards where they happened, the key under it and the newest moment in words.
     */
    private View map() {
        int[] k = kits();
        int hc = onGrass(k[0], summary.homeKitAlt), ac = onGrass(k[1], summary.awayKitAlt);
        if (Glyphs.distance(hc, ac) < 0.5) ac = Glyphs.luminance(hc) > 0.5 ? 0xFF1A2466 : 0xFFF4F5FA;
        LinearLayout c = Ui.column(this);
        int hs = 0, hon = 0, as = 0, aon = 0;
        for (String[] m : summary.plays) {
            String kind = m[Espn.M_KIND];
            boolean shot = Espn.GOAL.equals(kind) || Espn.ON_TARGET.equals(kind) || Espn.OFF_TARGET.equals(kind) || Espn.BLOCKED.equals(kind);
            boolean on = Espn.GOAL.equals(kind) || Espn.ON_TARGET.equals(kind);
            if (!shot) continue;
            if ("home".equals(m[Espn.M_SIDE])) { hs++; if (on) hon++; } else { as++; if (on) aon++; }
        }
        LinearLayout top = Ui.row(this);
        top.addView(shots(game.homeLogo, hs, hon, true), Ui.weight(1));
        top.addView(shots(game.awayLogo, as, aon, false), Ui.weight(1));
        c.addView(top, Ui.margins(this, 2, 0, 2, 8));
        MatchMap pitch = new MatchMap(this);
        pitch.show(summary.plays, hc, ac, game.state == Game.LIVE);
        pitch.setContentDescription("Shots: " + game.homeName + " " + hs + ", " + hon + " on target; " + game.awayName + " " + as + ", "
                + aon + " on target");
        c.addView(pitch, Ui.matchWrap());
        LinearLayout ways = Ui.row(this);
        TextView hw = Ui.text(this, game.homeName + "  →", 12, getColor(R.color.text_dim), true);
        hw.setMaxLines(1);
        TextView aw = Ui.text(this, "←  " + game.awayName, 12, getColor(R.color.text_dim), true);
        aw.setMaxLines(1);
        aw.setGravity(Gravity.END);
        ways.addView(hw, Ui.weight(1));
        ways.addView(aw, Ui.weight(1));
        c.addView(ways, Ui.margins(this, 4, 6, 4, 0));
        LinearLayout key = Ui.row(this);
        key.setGravity(Gravity.CENTER);
        key.addView(keyItem(dot(0xFFFFFFFF, true), "On target"));
        key.addView(keyItem(dot(0xFFFFFFFF, false), "Off target"));
        key.addView(keyItem(Glyphs.drawable(this, Glyphs.GOAL, 13), "Goal"));
        key.addView(keyItem(Glyphs.drawable(this, Glyphs.YELLOW, 13), "Card"));
        c.addView(key, Ui.margins(this, 0, 10, 0, 0));
        if (!summary.plays.isEmpty()) {
            String[] last = summary.plays.get(summary.plays.size() - 1);
            String what;
            switch (last[Espn.M_KIND]) {
                case Espn.GOAL: what = "scores"; break;
                case Espn.ON_TARGET: what = "shot saved"; break;
                case Espn.OFF_TARGET: what = "shot off target"; break;
                case Espn.BLOCKED: what = "shot blocked"; break;
                case Espn.YELLOW: what = "yellow card"; break;
                default: what = "red card";
            }
            TextView latest = Ui.text(this, (game.state == Game.LIVE ? "LATEST  ·  " : "LAST  ·  ") + last[Espn.M_MINUTE] + "  "
                    + shortName(last[Espn.M_PLAYER]) + " " + what, 12.5f, getColor(R.color.text), true);
            latest.setMaxLines(1);
            latest.setEllipsize(TextUtils.TruncateAt.END);
            latest.setGravity(Gravity.CENTER);
            latest.setBackground(Ui.fill(this, 0x1AFFFFFF, 12));
            latest.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
            c.addView(latest, Ui.margins(this, 0, 10, 0, 0));
        }
        return c;
    }

    private View shots(String crest, int shots, int on, boolean home) {
        LinearLayout r = Ui.row(this);
        r.setGravity((home ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL);
        ImageView v = Ui.image(this, crest, 20);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        TextView t = Ui.text(this, shots + (shots == 1 ? " shot" : " shots") + " · " + on + " on target", 12.5f, getColor(R.color.text_soft), true);
        t.setMaxLines(1);
        t.setPadding(Ui.dp(this, 7), 0, Ui.dp(this, 7), 0);
        if (home) { r.addView(v); r.addView(t); } else { r.addView(t); r.addView(v); }
        return r;
    }

    private static Drawable dot(int color, boolean filled) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        if (filled) g.setColor(color); else g.setStroke(3, color);
        return g;
    }

    private View keyItem(Drawable icon, String words) {
        TextView t = Ui.text(this, words, 11.5f, getColor(R.color.text_dim), false);
        t.setMaxLines(1);
        int s = Ui.dp(this, icon instanceof GradientDrawable ? 9 : 13);
        icon.setBounds(0, 0, icon instanceof GradientDrawable ? s : icon.getIntrinsicWidth(), icon instanceof GradientDrawable ? s : icon.getIntrinsicHeight());
        t.setCompoundDrawables(icon, null, null, null);
        t.setCompoundDrawablePadding(Ui.dp(this, 5));
        t.setPadding(Ui.dp(this, 7), 0, Ui.dp(this, 7), 0);
        return t;
    }

    private View divider(String words, String score) {
        LinearLayout r = Ui.row(this);
        r.setGravity(Gravity.CENTER);
        r.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        View l = new View(this), rr = new View(this);
        l.setBackgroundColor(getColor(R.color.surface_line));
        rr.setBackgroundColor(getColor(R.color.surface_line));
        r.addView(l, new LinearLayout.LayoutParams(0, Ui.dp(this, 1), 1));
        TextView t = Ui.text(this, words + "   " + score, 11.5f, getColor(R.color.text_soft), true);
        t.setLetterSpacing(0.1f);
        t.setBackground(Ui.fill(this, 0x14FFFFFF, 12));
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.leftMargin = Ui.dp(this, 10);
        tp.rightMargin = Ui.dp(this, 10);
        r.addView(t, tp);
        r.addView(rr, new LinearLayout.LayoutParams(0, Ui.dp(this, 1), 1));
        return r;
    }

    /** One moment: its minute in the middle, the home side's on the left reading inwards, the away side's on the right. */
    private View moment(String[] e, String score) {
        boolean home = !"away".equals(e[3]);
        int glyph = Glyphs.of(e[1]);
        LinearLayout r = Ui.row(this);
        r.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
        LinearLayout content = Ui.row(this);
        content.setGravity((home ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
        LinearLayout words = Ui.column(this);
        words.setGravity(home ? Gravity.END : Gravity.START);
        String second = e.length > 5 ? e[5] : "";
        boolean sub = glyph == Glyphs.SUB;
        TextView main = Ui.text(this, shortName(e[2]), 14.5f, sub ? 0xFF8EF0B8 : getColor(R.color.text), true);
        main.setMaxLines(1);
        main.setEllipsize(TextUtils.TruncateAt.END);
        main.setGravity(home ? Gravity.END : Gravity.START);
        words.addView(main);
        String under;
        if (sub) under = second.isEmpty() ? "" : "Off: " + shortName(second);
        else if (score != null) under = score + (second.isEmpty() ? "" : " · Assist " + surname(second))
                + (e[1].toLowerCase(Locale.ROOT).contains("penalty") ? " · Penalty" : "");
        else if (glyph == Glyphs.VAR || glyph == Glyphs.MISS) under = e[1];
        else under = "";
        if (!under.isEmpty()) {
            TextView u = Ui.text(this, under, 12, sub ? 0xFFFF9EA2 : getColor(R.color.text_soft), score != null);
            u.setMaxLines(1);
            u.setEllipsize(TextUtils.TruncateAt.END);
            u.setGravity(home ? Gravity.END : Gravity.START);
            words.addView(u);
        }
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(Glyphs.drawable(this, glyph, 20));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(this, glyph == Glyphs.VAR ? 34 : 26), Ui.dp(this, 26));
        if (home) {
            content.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            ip.leftMargin = Ui.dp(this, 8);
            content.addView(icon, ip);
        } else {
            ip.rightMargin = Ui.dp(this, 8);
            content.addView(icon, ip);
            content.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        TextView minute = Ui.text(this, e[0], 12.5f, getColor(R.color.text), true);
        minute.setGravity(Gravity.CENTER);
        minute.setMaxLines(1);
        boolean goal = score != null;
        minute.setBackground(Ui.fill(this, goal ? 0x333DDC84 : 0x1AFFFFFF, 13));
        minute.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(Ui.dp(this, 52), ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.leftMargin = Ui.dp(this, 8);
        mp.rightMargin = Ui.dp(this, 8);
        View space = new View(this);
        if (home) {
            r.addView(content, Ui.weight(1));
            r.addView(minute, mp);
            r.addView(space, Ui.weight(1));
        } else {
            r.addView(space, Ui.weight(1));
            r.addView(minute, mp);
            r.addView(content, Ui.weight(1));
        }
        String club = home ? game.homeName : game.awayName;
        r.setContentDescription(e[0] + " " + e[1] + ", " + e[2] + (sub && !second.isEmpty() ? " on for " + second : "") + ", " + club
                + (score != null ? ", " + score : ""));
        r.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        return r;
    }

    // ---- the line-ups -------------------------------------------------------------------------------------------

    private void lineups() {
        int[] k = kits();
        body.addView(teamBar(true), Ui.margins(this, 0, 10, 0, 8));
        Pitch pitch = new Pitch(this);
        pitch.show(summary.homeLineup, summary.homeFormation, k[0], summary.awayLineup, summary.awayFormation, k[1]);
        pitch.setContentDescription(describe(game.homeName, summary.homeFormation, summary.homeLineup) + " "
                + describe(game.awayName, summary.awayFormation, summary.awayLineup));
        body.addView(pitch, Ui.matchWrap());
        body.addView(teamBar(false), Ui.margins(this, 0, 8, 0, 0));
        TextView subs = Ui.heading(this, "Substitutes", getColor(R.color.text_soft));
        subs.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 10));
        body.addView(subs);
        LinearLayout two = Ui.row(this);
        two.setGravity(Gravity.TOP);
        two.addView(bench(summary.homeLineup, k[0]), Ui.weight(1));
        View gap = new View(this);
        two.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 12), 1));
        two.addView(bench(summary.awayLineup, k[1]), Ui.weight(1));
        body.addView(two);
    }

    private String describe(String club, String formation, List<String[]> lineup) {
        StringBuilder b = new StringBuilder(club + (formation.isEmpty() ? "" : ", " + formation) + ":");
        for (List<Integer> row : Formation.rows(formation, lineup)) {
            for (int i : row) b.append(' ').append(lineup.get(i)[Espn.P_SHORT]).append(',');
        }
        return b.toString();
    }

    /** A side's crest, name and formation, above or under the pitch. */
    private View teamBar(boolean home) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        ImageView crest = Ui.image(this, home ? game.homeLogo : game.awayLogo, 24);
        crest.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        r.addView(crest);
        TextView name = Ui.text(this, home ? game.homeName : game.awayName, 15, getColor(R.color.text), true);
        name.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(name, Ui.weight(1));
        String f = home ? summary.homeFormation : summary.awayFormation;
        if (!f.isEmpty()) {
            TextView t = Ui.text(this, f, 12.5f, getColor(R.color.text), true);
            t.setBackground(Ui.fill(this, 0x24FFFFFF, 11));
            t.setPadding(Ui.dp(this, 10), Ui.dp(this, 3), Ui.dp(this, 10), Ui.dp(this, 3));
            r.addView(t);
        }
        return r;
    }

    /** A side's substitutes: those who came on first (when, and for whom), then the rest, each with his number. */
    private View bench(List<String[]> lineup, int kit) {
        LinearLayout c = Ui.column(this);
        List<String[]> on = new ArrayList<>(), rest = new ArrayList<>();
        for (String[] p : lineup) {
            if ("1".equals(p[Espn.P_STARTER])) continue;
            (p[Espn.P_ON].isEmpty() ? rest : on).add(p);
        }
        on.sort((x, y) -> Integer.compare(Game.minute(x[Espn.P_ON]), Game.minute(y[Espn.P_ON])));
        for (String[] p : on) c.addView(benchRow(p, kit, true));
        for (String[] p : rest) c.addView(benchRow(p, kit, false));
        return c;
    }

    private View benchRow(String[] p, int kit, boolean cameOn) {
        LinearLayout r = Ui.row(this);
        r.setGravity(Gravity.TOP);
        r.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
        TextView num = Ui.text(this, p[Espn.P_NUMBER], 11, Glyphs.ink(kit), true);
        num.setGravity(Gravity.CENTER);
        num.setMaxLines(1);
        num.setBackground(Ui.fill(this, kit, 12));
        if (!cameOn) num.setAlpha(0.75f);
        r.addView(num, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
        LinearLayout words = Ui.column(this);
        words.setPadding(Ui.dp(this, 9), 0, 0, 0);
        LinearLayout first = Ui.row(this);
        TextView name = Ui.text(this, p[Espn.P_SHORT], 13.5f, cameOn ? getColor(R.color.text) : getColor(R.color.text_soft), cameOn);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        first.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        int goals = Espn.parseInt(p[Espn.P_GOALS]);
        for (int g = 0; g < Math.min(goals, 3); g++) first.addView(glyph(Glyphs.GOAL));
        String cards = p[Espn.P_CARDS];
        if (!cards.isEmpty()) first.addView(glyph(cards.equals("YR") ? Glyphs.SECOND_YELLOW : cards.contains("R") ? Glyphs.RED : Glyphs.YELLOW));
        words.addView(first);
        if (cameOn) {
            String forWho = replaced(p[Espn.P_NAME]);
            String minute = "?".equals(p[Espn.P_ON]) ? "" : p[Espn.P_ON];
            TextView when = Ui.text(this, "▲ " + minute + (forWho.isEmpty() ? "" : "  for " + surname(forWho))
                    + (p[Espn.P_OFF].isEmpty() ? "" : "  ▼ " + p[Espn.P_OFF]), 11.5f, 0xFF8EF0B8, false);
            when.setMaxLines(1);
            when.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(when);
        }
        r.addView(words, Ui.weight(1));
        r.setContentDescription(p[Espn.P_NUMBER] + " " + p[Espn.P_NAME] + (cameOn ? ", came on " + p[Espn.P_ON] : ", unused"));
        r.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        return r;
    }

    private ImageView glyph(int kind) {
        ImageView v = new ImageView(this);
        v.setImageDrawable(Glyphs.drawable(this, kind, 13));
        v.setScaleType(ImageView.ScaleType.CENTER);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 18));
        lp.leftMargin = Ui.dp(this, 2);
        v.setLayoutParams(lp);
        return v;
    }

    /** Whom a substitute replaced, from the timeline's substitution ("" when not told). */
    private String replaced(String name) {
        for (String[] e : summary.events) {
            if (Glyphs.of(e[1]) == Glyphs.SUB && e[2].equals(name) && e.length > 5) return e[5];
        }
        return "";
    }

    // ---- the stats ----------------------------------------------------------------------------------------------

    private void stats() {
        int[] k = kits();
        int hc = Glyphs.onDark(k[0]), ac = Glyphs.onDark(k[1]);
        if (Glyphs.distance(hc, ac) < 0.4) ac = 0x99FFFFFF;
        LinearLayout key = Ui.row(this);
        key.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 6));
        key.addView(keyChip(game.homeName, hc, Gravity.START), Ui.weight(1));
        key.addView(keyChip(game.awayName, ac, Gravity.END), Ui.weight(1));
        body.addView(key);
        for (String[] st : summary.stats) body.addView(statRow(st, hc, ac));
    }

    private View keyChip(String name, int color, int gravity) {
        LinearLayout r = Ui.row(this);
        r.setGravity(gravity | Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(Ui.fill(this, color, 5));
        TextView t = Ui.text(this, name, 13, getColor(R.color.text_soft), true);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.dp(this, 10), Ui.dp(this, 10));
        if (gravity == Gravity.START) {
            dp.rightMargin = Ui.dp(this, 7);
            r.addView(dot, dp);
            r.addView(t);
        } else {
            dp.leftMargin = Ui.dp(this, 7);
            r.addView(t);
            r.addView(dot, dp);
        }
        return r;
    }

    private View statRow(String[] st, int hc, int ac) {
        LinearLayout r = Ui.column(this);
        r.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
        float h = number(st[1]), a = number(st[2]);
        LinearLayout labels = Ui.row(this);
        TextView left = Ui.text(this, st[1], 16, h >= a ? getColor(R.color.text) : getColor(R.color.text_soft), h >= a);
        labels.addView(left, Ui.weight(1));
        TextView mid = Ui.text(this, st[0], 13, getColor(R.color.text_soft), false);
        mid.setGravity(Gravity.CENTER);
        labels.addView(mid, Ui.weight(2));
        TextView right = Ui.text(this, st[2], 16, a >= h ? getColor(R.color.text) : getColor(R.color.text_soft), a >= h);
        right.setGravity(Gravity.END);
        labels.addView(right, Ui.weight(1));
        r.addView(labels);
        if (h + a > 0) {
            LinearLayout bar = Ui.row(this);
            View hv = new View(this), av = new View(this);
            hv.setBackground(Ui.fill(this, h >= a ? hc : mix(hc, 0xFF161A4A, 0.45f), 4));
            av.setBackground(Ui.fill(this, a >= h ? ac : mix(ac, 0xFF161A4A, 0.45f), 4));
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, Ui.dp(this, 8), Math.max(h, 0.0001f)),
                    ap = new LinearLayout.LayoutParams(0, Ui.dp(this, 8), Math.max(a, 0.0001f));
            hp.rightMargin = Ui.dp(this, 4);
            bar.addView(hv, hp);
            bar.addView(av, ap);
            r.addView(bar, Ui.margins(this, 0, 7, 0, 0));
        }
        r.setContentDescription(st[0] + ": " + game.homeName + " " + st[1] + ", " + game.awayName + " " + st[2]);
        r.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        return r;
    }

    private static float number(String s) {
        try {
            return Float.parseFloat(s.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
