package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A club's own page, opened from a table's row or a game's crest. A header in the club's colour: its crest and name,
 * the competition's logo and its place in it, its last five (W, D, L discs), its last game and its next. The season:
 * ESPN's record (points, games, goal difference, won, drawn, lost), goals for and against, clean sheets, home and away,
 * the biggest win and the heaviest defeat. FotMob's figures with the club's place in the league for each (xG, possession,
 * shots, passing, defending, cards…), its leading players (scorers, assists, ratings and the rest, with their pictures),
 * and the squad by position: every player's picture (or initials), number, country's flag, age and season.
 */
public class TeamActivity extends AppCompatActivity {
    private static final String SLUG = "slug", ID = "id", NAME = "name", LOGO = "logo";
    /** The form discs' colours: won, drawn, lost. */
    static final int WIN = 0xFF2BB673, DRAW = 0xFF8C93AD, LOSS = 0xFFE5484D;

    private TeamData data;
    private boolean fotmobRead, allBoards;
    private LinearLayout hero, season, figures, leaders, squad;

    public static Intent intent(Context c, String slug, String id, String name, String logo) {
        return new Intent(c, TeamActivity.class).putExtra(SLUG, slug).putExtra(ID, id).putExtra(NAME, name).putExtra(LOGO, logo);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent in = getIntent();
        String slug = in.getStringExtra(SLUG), id = in.getStringExtra(ID);
        if (slug == null || id == null || id.isEmpty()) {
            finish();
            return;
        }
        String name = in.getStringExtra(NAME), logo = in.getStringExtra(LOGO);
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
        top.addView(Ui.compHeading(this, Espn.name(slug), Competitions.logo(slug), getColor(R.color.text_soft)), Ui.weight(1));
        col.addView(top);
        hero = Ui.column(this);
        col.addView(hero, Ui.matchWrap());
        season = Ui.column(this);
        col.addView(season, Ui.margins(this, 0, 14, 0, 0));
        figures = Ui.column(this);
        col.addView(figures, Ui.margins(this, 0, 14, 0, 0));
        leaders = Ui.column(this);
        col.addView(leaders, Ui.margins(this, 0, 4, 0, 0));
        squad = Ui.column(this);
        col.addView(squad, Ui.margins(this, 0, 4, 0, 0));
        setContentView(scroll);

        TeamData kept = TeamData.kept(slug, id);
        if (kept != null) {
            data = kept;
            fotmobRead = true;
            paint();
            return;
        }
        data = new TeamData();
        data.slug = slug;
        data.id = id;
        data.name = name == null ? "" : name;
        data.shortName = data.name;
        data.logo = logo == null ? "" : logo;
        paint();
        Context app = getApplicationContext();
        App.io.execute(() -> {
            TeamData espn;
            try {
                espn = TeamData.espn(slug, id, name, logo);
            } catch (RuntimeException e) {
                espn = null;
            }
            TeamData first = espn;
            if (first != null) {
                App.main.post(() -> {
                    if (isFinishing() || data.fotmob) return;
                    data = first;
                    paint();
                });
            }
            TeamData full;
            try {
                full = first == null ? null : TeamData.withFotMob(app, first);
            } catch (RuntimeException e) {
                full = null;
            }
            TeamData got = full;
            App.main.post(() -> {
                if (isFinishing()) return;
                fotmobRead = true;
                if (got != null) data = got;
                paint();
            });
        });
    }

    private void paint() {
        paintHero();
        paintSeason();
        paintFigures();
        paintLeaders();
        paintSquad();
    }

    // ---- the header -------------------------------------------------------------------------------------------------

    private void paintHero() {
        hero.removeAllViews();
        int team = TeamData.colour(data.color, 0xFF2A3070);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{blend(team, 0xFF161A4A, 0.5f), 0xFF1B1F52, 0xFF161A4A});
        bg.setCornerRadius(Ui.dp(this, 24));
        bg.setStroke(Ui.dp(this, 1), getColor(R.color.surface_line));
        LinearLayout card = Ui.column(this);
        card.setBackground(bg);
        int p = Ui.dp(this, 16);
        card.setPadding(p, Ui.dp(this, 18), p, Ui.dp(this, 16));

        LinearLayout top = Ui.row(this);
        FrameLayout disc = new FrameLayout(this);
        disc.setBackground(Ui.fill(this, 0xFFFFFFFF, 38));
        disc.setElevation(Ui.dp(this, 3));
        ImageView crest = new ImageView(this);
        crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
        crest.setContentDescription(data.name + " crest");
        disc.addView(crest, new FrameLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52), Gravity.CENTER));
        Ui.load(crest, data.logo, null, null);
        top.addView(disc, new LinearLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 76)));
        LinearLayout words = Ui.column(this);
        TextView n = Ui.text(this, data.name, 22, getColor(R.color.text), true);
        n.setMaxLines(2);
        n.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(n);
        LinearLayout place = Ui.row(this);
        String compLogo = Competitions.logo(data.slug);
        if (compLogo != null && !compLogo.isEmpty()) {
            ImageView comp = new ImageView(this);
            comp.setScaleType(ImageView.ScaleType.FIT_CENTER);
            comp.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18));
            cp.rightMargin = Ui.dp(this, 7);
            place.addView(comp, cp);
            Ui.load(comp, compLogo, null, () -> comp.setVisibility(View.GONE));
        }
        TextView st = Ui.text(this, standing(), 13.5f, getColor(R.color.text_soft), false);
        st.setMaxLines(2);
        place.addView(st, Ui.weight(1));
        words.addView(place, Ui.margins(this, 0, 6, 0, 0));
        if (!data.venue.isEmpty()) {
            TextView ground = Ui.text(this, data.venue, 12, getColor(R.color.text_dim), false);
            ground.setMaxLines(1);
            ground.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(ground, Ui.margins(this, 0, 4, 0, 0));
        }
        LinearLayout.LayoutParams wp = Ui.weight(1);
        wp.leftMargin = Ui.dp(this, 14);
        top.addView(words, wp);
        card.addView(top);

        String form = TeamData.form(data.results, 5), formFrom = Espn.name(data.slug);
        if (form.isEmpty() && !data.formEverywhere.isEmpty()) {
            form = data.formEverywhere.substring(Math.max(0, data.formEverywhere.length() - 5));
            formFrom = "All competitions";
        }
        if (!form.isEmpty()) {
            LinearLayout fr = Ui.row(this);
            fr.addView(kicker("FORM"), new LinearLayout.LayoutParams(Ui.dp(this, 52), ViewGroup.LayoutParams.WRAP_CONTENT));
            StringBuilder said = new StringBuilder("Form, oldest first:");
            for (char r : form.toCharArray()) {
                TextView d = disc(r, 26);
                LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26));
                dp.rightMargin = Ui.dp(this, 6);
                fr.addView(d, dp);
                said.append(' ').append(r == 'W' ? "won" : r == 'L' ? "lost" : "drawn");
            }
            TextView from = Ui.text(this, formFrom, 11.5f, getColor(R.color.text_dim), false);
            from.setGravity(Gravity.END);
            from.setMaxLines(1);
            from.setEllipsize(TextUtils.TruncateAt.END);
            fr.addView(from, Ui.weight(1));
            fr.setContentDescription(said + " (" + formFrom + ")");
            card.addView(fr, Ui.margins(this, 0, 16, 0, 0));
        }
        if (data.last != null || data.next != null) {
            LinearLayout two = Ui.row(this);
            two.setGravity(Gravity.TOP);
            if (data.last != null) two.addView(gameBox("LAST", data.last), Ui.weight(1));
            if (data.last != null && data.next != null) two.addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));
            if (data.next != null) two.addView(gameBox("NEXT", data.next), Ui.weight(1));
            card.addView(two, Ui.margins(this, 0, 14, 0, 0));
        }
        hero.addView(card, Ui.matchWrap());
    }

    /** "4th in Lakeside League" as ESPN says it, else its place counted, else the competition. */
    private String standing() {
        if (!data.standing.isEmpty()) return data.standing;
        if (data.rank > 0) return TeamData.ordinal(data.rank) + " in the " + Espn.name(data.slug);
        return Espn.name(data.slug);
    }

    /** The last game or the next: the other club's crest and name, the score or the day, the competition. */
    private View gameBox(String label, TeamData.Fixture f) {
        LinearLayout b = Ui.column(this);
        b.setBackground(Ui.fill(this, 0x1FFFFFFF, 16));
        b.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        b.addView(kicker(label));
        LinearLayout r = Ui.row(this);
        ImageView crest = new ImageView(this);
        crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
        crest.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        r.addView(crest, new LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26)));
        Ui.load(crest, f.otherLogo, null, () -> crest.setVisibility(View.GONE));
        TextView o = Ui.text(this, f.other, 14.5f, getColor(R.color.text), true);
        o.setMaxLines(1);
        o.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams op = Ui.weight(1);
        op.leftMargin = Ui.dp(this, 8);
        r.addView(o, op);
        b.addView(r, Ui.margins(this, 0, 6, 0, 0));
        LinearLayout d = Ui.row(this);
        String where = f.home ? "Home" : "Away";
        String said;
        if (f.played) {
            char res = f.result();
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18));
            dp.rightMargin = Ui.dp(this, 7);
            d.addView(disc(res, 18), dp);
            d.addView(Ui.text(this, f.scored + "–" + f.conceded, 13.5f, getColor(R.color.text_soft), true));
            said = (res == 'W' ? "Won " : res == 'L' ? "Lost " : "Drew ") + f.scored + "–" + f.conceded;
        } else {
            String when = f.when > 0 ? Instant.ofEpochMilli(f.when).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.UK)) : "";
            TextView w = Ui.text(this, when.isEmpty() ? "To be arranged" : when, 13.5f, getColor(R.color.text_soft), true);
            w.setMaxLines(1);
            w.setEllipsize(TextUtils.TruncateAt.END);
            d.addView(w, Ui.weight(1));
            said = when;
        }
        b.addView(d, Ui.margins(this, 0, 6, 0, 0));
        // Home or away, and the competition, under it.
        TextView comp = Ui.text(this, f.comp.isEmpty() ? where : where + " · " + f.comp, 11.5f, getColor(R.color.text_dim), false);
        comp.setMaxLines(1);
        comp.setEllipsize(TextUtils.TruncateAt.END);
        b.addView(comp, Ui.margins(this, 0, 3, 0, 0));
        b.setContentDescription((label.equals("LAST") ? "Last game: " : "Next game: ") + f.other + ", " + where.toLowerCase(Locale.ROOT)
                + ", " + said + (f.comp.isEmpty() ? "" : ", " + f.comp));
        return b;
    }

    // ---- the season ---------------------------------------------------------------------------------------------------

    private void paintSeason() {
        season.removeAllViews();
        TeamData.Record a = data.all;
        if (!a.has()) {
            season.setVisibility(View.GONE);
            return;
        }
        season.setVisibility(View.VISIBLE);
        LinearLayout card = glassCard();
        card.addView(cardHead("THE SEASON", Espn.name(data.slug)));
        LinearLayout tiles = Ui.row(this);
        tiles.addView(tile(data.rank > 0 ? TeamData.ordinal(data.rank) : "–", "Position"), Ui.weight(1));
        tiles.addView(tile(String.valueOf(a.points()), "Points"), spaced());
        tiles.addView(tile(String.valueOf(a.played), "Played"), spaced());
        tiles.addView(tile(signed(a.difference()), "Goal diff."), spaced());
        card.addView(tiles, Ui.margins(this, 0, 4, 0, 0));
        card.addView(recordBar(a), Ui.margins(this, 0, 14, 0, 0));
        LinearLayout goals = Ui.row(this);
        goals.setGravity(Gravity.TOP);
        goals.addView(figure(String.valueOf(a.scored), "Scored", perGame(a.scored, a.played)), Ui.weight(1));
        goals.addView(figure(String.valueOf(a.conceded), "Conceded", perGame(a.conceded, a.played)), Ui.weight(1));
        if (!data.results.isEmpty()) {
            int blanks = TeamData.blanks(data.results);
            goals.addView(figure(String.valueOf(TeamData.cleanSheets(data.results)), "Clean sheets",
                    blanks == 0 ? "Scored in every game" : blanks + (blanks == 1 ? " game" : " games") + " without a goal"), Ui.weight(1));
        }
        card.addView(goals, Ui.margins(this, 0, 16, 0, 0));
        if (data.home.has() || data.away.has()) card.addView(splits(), Ui.margins(this, 0, 16, 0, 0));
        TeamData.Fixture best = TeamData.biggest(data.results, true), worst = TeamData.biggest(data.results, false);
        if (best != null || worst != null) {
            LinearLayout extremes = Ui.column(this);
            if (best != null) extremes.addView(line("Biggest win", best));
            if (worst != null) extremes.addView(line("Heaviest defeat", worst));
            card.addView(extremes, Ui.margins(this, 0, 12, 0, 0));
        }
        season.addView(card, Ui.matchWrap());
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams lp = Ui.weight(1);
        lp.leftMargin = Ui.dp(this, 8);
        return lp;
    }

    /** One of the season's numbers on a tile: "18th / Position". */
    private View tile(String value, String label) {
        LinearLayout t = Ui.column(this);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setBackground(Ui.fill(this, 0x14FFFFFF, 14));
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 9));
        TextView v = Ui.text(this, value, 20, getColor(R.color.text), true);
        v.setGravity(Gravity.CENTER);
        v.setMaxLines(1);
        t.addView(v);
        TextView l = Ui.text(this, label, 11.5f, getColor(R.color.text_dim), false);
        l.setGravity(Gravity.CENTER);
        l.setMaxLines(1);
        t.addView(l);
        t.setContentDescription(label + ": " + value);
        return t;
    }

    /** Won, drawn and lost: a bar in three colours as long as each, and the three counts over it. */
    private View recordBar(TeamData.Record a) {
        LinearLayout box = Ui.column(this);
        LinearLayout counts = Ui.row(this);
        counts.addView(count(a.won, "Won", WIN), Ui.weight(1));
        counts.addView(count(a.drawn, "Drawn", DRAW), Ui.weight(1));
        TextView lost = count(a.lost, "Lost", LOSS);
        lost.setGravity(Gravity.END);
        counts.addView(lost, Ui.weight(1));
        box.addView(counts);
        LinearLayout bar = Ui.row(this);
        int[] parts = {a.won, a.drawn, a.lost};
        int[] colours = {WIN, DRAW, LOSS};
        for (int i = 0; i < 3; i++) {
            if (parts[i] <= 0) continue;
            View v = new View(this);
            v.setBackground(Ui.fill(this, colours[i], 3));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 6), parts[i]);
            lp.rightMargin = Ui.dp(this, 2);
            bar.addView(v, lp);
        }
        box.addView(bar, Ui.margins(this, 0, 6, 0, 0));
        box.setContentDescription("Won " + a.won + ", drawn " + a.drawn + ", lost " + a.lost);
        return box;
    }

    private TextView count(int n, String label, int colour) {
        TextView t = Ui.text(this, n + "  " + label, 13, colour, true);
        t.setMaxLines(1);
        return t;
    }

    /** A number with its name and a line under it: "14 / Scored / 1.4 a game". */
    private View figure(String value, String label, String under) {
        LinearLayout f = Ui.column(this);
        f.addView(Ui.text(this, value, 22, getColor(R.color.text), true));
        f.addView(Ui.text(this, label, 12.5f, getColor(R.color.text_soft), true));
        if (under != null && !under.isEmpty()) {
            TextView u = Ui.text(this, under, 11.5f, getColor(R.color.text_dim), false);
            u.setMaxLines(2);
            f.addView(u);
        }
        f.setContentDescription(label + " " + value + (under == null ? "" : ", " + under));
        return f;
    }

    private static String perGame(int goals, int played) {
        return played <= 0 ? "" : String.format(Locale.UK, "%.1f a game", goals / (double) played);
    }

    private static String signed(int n) {
        return n > 0 ? "+" + n : n < 0 ? "−" + (-n) : "0";
    }

    /** Home and away side by side: played, won, drawn, lost, for, against, points. */
    private View splits() {
        LinearLayout t = Ui.column(this);
        String[] head = {"", "P", "W", "D", "L", "F", "A", "Pts"};
        t.addView(splitRow(head, true));
        if (data.home.has()) t.addView(splitRow(cells("Home", data.home), false));
        if (data.away.has()) t.addView(splitRow(cells("Away", data.away), false));
        return t;
    }

    private static String[] cells(String name, TeamData.Record r) {
        return new String[]{name, String.valueOf(r.played), String.valueOf(r.won), String.valueOf(r.drawn), String.valueOf(r.lost),
                String.valueOf(r.scored), String.valueOf(r.conceded), String.valueOf(r.points())};
    }

    private View splitRow(String[] cells, boolean head) {
        LinearLayout r = Ui.row(this);
        r.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
        int ink = head ? getColor(R.color.text_dim) : getColor(R.color.text_soft);
        TextView name = Ui.text(this, cells[0], 13.5f, head ? ink : getColor(R.color.text), !head);
        r.addView(name, Ui.weight(1));
        for (int i = 1; i < cells.length; i++) {
            boolean pts = i == cells.length - 1;
            TextView v = Ui.text(this, cells[i], 13, pts && !head ? getColor(R.color.text) : ink, pts || head);
            v.setGravity(Gravity.END);
            v.setMaxLines(1);
            r.addView(v, new LinearLayout.LayoutParams(Ui.dp(this, pts ? 36 : 28), ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (!head) {
            r.setContentDescription(cells[0] + ": played " + cells[1] + ", won " + cells[2] + ", drawn " + cells[3] + ", lost "
                    + cells[4] + ", scored " + cells[5] + ", conceded " + cells[6] + ", " + cells[7] + " points");
        }
        return r;
    }

    private View line(String label, TeamData.Fixture f) {
        LinearLayout r = Ui.row(this);
        r.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        r.addView(Ui.text(this, label, 13, getColor(R.color.text_dim), false), Ui.weight(1));
        TextView v = Ui.text(this, f.scored + "–" + f.conceded + " v " + f.other + (f.home ? " (H)" : " (A)"), 13, getColor(R.color.text), true);
        v.setMaxLines(1);
        v.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(v);
        return r;
    }

    // ---- FotMob's figures -----------------------------------------------------------------------------------------

    private void paintFigures() {
        figures.removeAllViews();
        if (data.figures.isEmpty()) {
            if (fotmobRead) {
                figures.setVisibility(View.GONE);
                return;
            }
            figures.setVisibility(View.VISIBLE);
            LinearLayout card = glassCard();
            card.addView(Ui.text(this, "Reading the season's figures and squad…", 14, getColor(R.color.text_dim), false));
            figures.addView(card, Ui.matchWrap());
            return;
        }
        figures.setVisibility(View.VISIBLE);
        LinearLayout card = glassCard();
        card.addView(cardHead("SEASON FIGURES", "Place in the league"));
        boolean first = true;
        for (Map.Entry<String, List<TeamData.Figure>> s : TeamData.sections(data.figures).entrySet()) {
            TextView h = Ui.text(this, s.getKey().toUpperCase(Locale.ROOT), 11.5f, getColor(R.color.text_dim), true);
            h.setLetterSpacing(0.1f);
            card.addView(h, Ui.margins(this, 0, first ? 2 : 14, 0, 2));
            first = false;
            for (TeamData.Figure f : s.getValue()) card.addView(figureRow(f));
        }
        TextView src = Ui.text(this, "Figures from FotMob", 11, getColor(R.color.text_dim), false);
        src.setGravity(Gravity.END);
        card.addView(src, Ui.margins(this, 0, 8, 0, 0));
        figures.addView(card, Ui.matchWrap());
    }

    private View figureRow(TeamData.Figure f) {
        LinearLayout r = Ui.row(this);
        r.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        TextView n = Ui.text(this, f.name, 14, getColor(R.color.text_soft), false);
        n.setMaxLines(1);
        n.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(n, Ui.weight(1));
        TextView v = Ui.text(this, f.value, 14.5f, getColor(R.color.text), true);
        v.setMaxLines(1);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.leftMargin = Ui.dp(this, 8);
        r.addView(v, vp);
        TextView rank = Ui.text(this, f.rank > 0 ? TeamData.ordinal(f.rank) : "", 11.5f, f.rank > 0 && f.rank <= 3 ? 0xFF8BF0BC : getColor(R.color.text_soft), true);
        rank.setGravity(Gravity.CENTER);
        rank.setMaxLines(1);
        if (f.rank > 0) rank.setBackground(Ui.fill(this, f.rank <= 3 ? 0x333DDC84 : 0x1AFFFFFF, 10));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 22));
        rp.leftMargin = Ui.dp(this, 10);
        r.addView(rank, rp);
        r.setContentDescription(f.name + ": " + f.value + (f.rank > 0 ? ", " + TeamData.ordinal(f.rank) + " in the league" : ""));
        return r;
    }

    // ---- the leading players --------------------------------------------------------------------------------------

    private void paintLeaders() {
        leaders.removeAllViews();
        List<TeamData.Board> shown = TeamData.shown(data.boards, allBoards);
        if (shown.isEmpty()) {
            leaders.setVisibility(View.GONE);
            return;
        }
        leaders.setVisibility(View.VISIBLE);
        leaders.addView(Ui.heading(this, "Leading players", getColor(R.color.text_soft)));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        for (TeamData.Board b : shown) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 200), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 10);
            row.addView(boardCard(b), lp);
        }
        hs.addView(row);
        leaders.addView(hs, Ui.matchWrap());
        if (data.boards.size() > TeamData.shown(data.boards, false).size()) {
            TextView more = Ui.text(this, allBoards ? "Fewer lists" : "All " + data.boards.size() + " lists ›", 13.5f, getColor(R.color.text_soft), true);
            more.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
            more.setOnClickListener(v -> {
                allBoards = !allBoards;
                paintLeaders();
            });
            leaders.addView(more);
        }
    }

    private View boardCard(TeamData.Board b) {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.glass(this, 18));
        int p = Ui.dp(this, 12);
        card.setPadding(p, p, p, Ui.dp(this, 10));
        TextView t = Ui.text(this, b.name.toUpperCase(Locale.ROOT), 11.5f, getColor(R.color.text_soft), true);
        t.setLetterSpacing(0.08f);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(t);
        StringBuilder said = new StringBuilder(b.name).append(':');
        for (int i = 0; i < b.top.size(); i++) {
            TeamData.Leader l = b.top.get(i);
            boolean lead = i == 0;
            LinearLayout r = Ui.row(this);
            r.setPadding(0, Ui.dp(this, lead ? 10 : 7), 0, 0);
            int size = lead ? 40 : 30;
            r.addView(face(l.photo, l.name, size), new LinearLayout.LayoutParams(Ui.dp(this, size), Ui.dp(this, size)));
            TextView n = Ui.text(this, l.name, lead ? 14 : 13, getColor(R.color.text), lead);
            n.setMaxLines(1);
            n.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np = Ui.weight(1);
            np.leftMargin = Ui.dp(this, 9);
            r.addView(n, np);
            TextView v = Ui.text(this, l.value, lead ? 16 : 13.5f, getColor(R.color.text), true);
            v.setMaxLines(1);
            LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            vp.leftMargin = Ui.dp(this, 6);
            r.addView(v, vp);
            card.addView(r);
            said.append(' ').append(l.name).append(' ').append(l.value).append(i + 1 < b.top.size() ? ';' : '.');
        }
        card.setContentDescription(said);
        return card;
    }

    // ---- the squad ------------------------------------------------------------------------------------------------

    private void paintSquad() {
        squad.removeAllViews();
        if (data.squad.isEmpty()) {
            squad.setVisibility(View.GONE);
            return;
        }
        squad.setVisibility(View.VISIBLE);
        int players = 0;
        for (TeamData.Group g : data.squad) if (!g.title.equals("Manager")) players += g.players.size();
        squad.addView(Ui.heading(this, "Squad · " + players + " players", getColor(R.color.text_soft)));
        LinearLayout card = glassCard();
        boolean first = true;
        for (TeamData.Group g : data.squad) {
            boolean boss = g.title.equals("Manager");
            TextView h = Ui.text(this, (boss ? g.title : g.title + " · " + g.players.size()).toUpperCase(Locale.ROOT), 11.5f,
                    getColor(R.color.text_dim), true);
            h.setLetterSpacing(0.1f);
            card.addView(h, Ui.margins(this, 0, first ? 2 : 16, 0, 4));
            first = false;
            for (TeamData.Player p : g.players) card.addView(playerRow(p, boss));
        }
        TextView src = Ui.text(this, "Squad from " + (data.squadFrom.isEmpty() ? "ESPN" : data.squadFrom), 11, getColor(R.color.text_dim), false);
        src.setGravity(Gravity.END);
        card.addView(src, Ui.margins(this, 0, 8, 0, 0));
        squad.addView(card, Ui.matchWrap());
    }

    private View playerRow(TeamData.Player p, boolean boss) {
        LinearLayout r = Ui.row(this);
        r.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
        FrameLayout pic = new FrameLayout(this);
        pic.addView(face(p.photo, p.name, 46), new FrameLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46)));
        if (!p.number.isEmpty()) {
            TextView num = Ui.text(this, p.number, 10, Color.WHITE, true);
            num.setGravity(Gravity.CENTER);
            num.setIncludeFontPadding(false);
            GradientDrawable chip = new GradientDrawable();
            chip.setColor(0xFF0D0F2C);
            chip.setCornerRadius(Ui.dp(this, 9));
            chip.setStroke(Ui.dp(this, 1), 0x66FFFFFF);
            num.setBackground(chip);
            pic.addView(num, new FrameLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 18), Gravity.BOTTOM | Gravity.END));
        }
        r.addView(pic, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 48)));
        LinearLayout words = Ui.column(this);
        TextView n = Ui.text(this, p.name, 15, getColor(R.color.text), true);
        n.setMaxLines(1);
        n.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(n);
        LinearLayout meta = Ui.row(this);
        if (!p.flag.isEmpty()) {
            ImageView flag = new ImageView(this);
            flag.setScaleType(ImageView.ScaleType.FIT_CENTER);
            flag.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 14));
            fp.rightMargin = Ui.dp(this, 6);
            meta.addView(flag, fp);
            Ui.load(flag, p.flag, null, () -> flag.setVisibility(View.GONE));
        }
        String about = join(p.country, p.age > 0 ? p.age + " yrs" : "");
        TextView m = Ui.text(this, about, 12.5f, getColor(R.color.text_dim), false);
        m.setMaxLines(1);
        m.setEllipsize(TextUtils.TruncateAt.END);
        meta.addView(m, Ui.weight(1));
        words.addView(meta, Ui.margins(this, 0, 2, 0, 0));
        String seasonLine = boss ? "" : seasonOf(p);
        if (!seasonLine.isEmpty()) {
            TextView s = Ui.text(this, seasonLine, 12, getColor(R.color.text_soft), false);
            s.setMaxLines(1);
            s.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(s, Ui.margins(this, 0, 2, 0, 0));
        }
        if (!p.injury.isEmpty()) {
            TextView hurt = Ui.text(this, "✚ " + TeamData.injuryLine(p.injury), 12, LOSS, true);
            hurt.setMaxLines(1);
            hurt.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(hurt, Ui.margins(this, 0, 2, 0, 0));
        }
        LinearLayout.LayoutParams wp = Ui.weight(1);
        wp.leftMargin = Ui.dp(this, 10);
        r.addView(words, wp);
        if (boss) {
            r.addView(Ui.text(this, "Manager", 12, getColor(R.color.text_dim), false));
        } else if (p.rating > 0) {
            TextView rating = Ui.text(this, String.format(Locale.UK, "%.1f", p.rating), 12.5f, Color.WHITE, true);
            rating.setGravity(Gravity.CENTER);
            rating.setBackground(Ui.fill(this, ratingColour(p.rating), 8));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 22));
            rp.leftMargin = Ui.dp(this, 8);
            r.addView(rating, rp);
        }
        r.setContentDescription(p.name + (p.number.isEmpty() ? "" : ", number " + p.number) + (about.isEmpty() ? "" : ", " + about)
                + (seasonLine.isEmpty() ? "" : ", " + seasonLine) + (p.injury.isEmpty() ? "" : ", " + TeamData.injuryLine(p.injury))
                + (p.rating > 0 ? String.format(Locale.UK, ", rated %.1f", p.rating) : ""));
        return r;
    }

    /** A player's season in words: "4 goals · 2 assists · 3 yellow". */
    private static String seasonOf(TeamData.Player p) {
        List<String> parts = new ArrayList<>();
        if (p.goals > 0) parts.add(p.goals + (p.goals == 1 ? " goal" : " goals"));
        if (p.assists > 0) parts.add(p.assists + (p.assists == 1 ? " assist" : " assists"));
        if (p.yellow > 0) parts.add(p.yellow + " yellow");
        if (p.red > 0) parts.add(p.red + " red");
        return TextUtils.join(" · ", parts);
    }

    /** FotMob's colours for a rating: blue from 8, green from 7, amber from 6, else red. */
    static int ratingColour(double r) {
        return r >= 8 ? 0xFF1E88E5 : r >= 7 ? 0xFF22A565 : r >= 6 ? 0xFFE09A12 : 0xFFD8434B;
    }

    private static String join(String a, String b) {
        return a.isEmpty() ? b : b.isEmpty() ? a : a + " · " + b;
    }

    // ---- small parts ------------------------------------------------------------------------------------------------

    /** A picture in a disc, the initials under it until it comes (and when there is none). */
    private View face(String url, String name, int sizeDp) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(Ui.fill(this, 0xFF2E3578, sizeDp / 2f));
        TextView ini = Ui.text(this, TeamData.initials(name), Math.max(10, sizeDp * 0.34f), 0xFFE4E6F5, true);
        ini.setGravity(Gravity.CENTER);
        ini.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        f.addView(ini, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (url != null && !url.isEmpty()) {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            f.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            Ui.load(img, url, () -> ini.setVisibility(View.INVISIBLE), () -> img.setVisibility(View.GONE));
        }
        f.setClipToOutline(true);
        f.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        return f;
    }

    /** A result as a disc in its colour: W, D or L. */
    private TextView disc(char r, int sizeDp) {
        TextView t = Ui.text(this, String.valueOf(r), sizeDp >= 24 ? 12 : 10, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setBackground(Ui.fill(this, r == 'W' ? WIN : r == 'L' ? LOSS : DRAW, sizeDp / 2f));
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return t;
    }

    private TextView kicker(String words) {
        TextView t = Ui.text(this, words, 11, getColor(R.color.text_dim), true);
        t.setLetterSpacing(0.12f);
        return t;
    }

    private LinearLayout glassCard() {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.glass(this, 18));
        int p = Ui.dp(this, 14);
        card.setPadding(p, Ui.dp(this, 12), p, Ui.dp(this, 12));
        return card;
    }

    private View cardHead(String title, String right) {
        LinearLayout r = Ui.row(this);
        TextView t = Ui.text(this, title, 12, getColor(R.color.text_soft), true);
        t.setLetterSpacing(0.12f);
        r.addView(t, Ui.weight(1));
        if (right != null && !right.isEmpty()) {
            TextView x = Ui.text(this, right, 12, getColor(R.color.text_dim), false);
            x.setMaxLines(1);
            x.setEllipsize(TextUtils.TruncateAt.END);
            r.addView(x);
        }
        r.setPadding(0, 0, 0, Ui.dp(this, 10));
        return r;
    }

    /** Pure: a colour mixed with another, t of the way to the second. */
    static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }
}
