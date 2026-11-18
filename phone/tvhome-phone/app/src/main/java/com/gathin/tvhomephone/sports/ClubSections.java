package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.home.Type;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The club page's sections under the squad (as 5.18 had them, now on the club's own ground): the club's form and its
 * last and next games; the season (ESPN's record: points, games, goal difference, won, drawn, lost, goals, clean
 * sheets, home and away, the biggest win and the heaviest defeat); FotMob's figures with the club's place in the league
 * for each; and its leading players. Two columns on a wide screen.
 */
final class ClubSections {
    /** The form discs' colours: won, drawn, lost. */
    static final int WIN = 0xFF2BB673, DRAW = 0xFF8C93AD, LOSS = 0xFFE5484D;
    private static final int TEXT = 0xFFFFFFFF, SOFT = 0xD9FFFFFF, DIM = 0x99FFFFFF;

    private final Context c;
    private final float d;
    private final Typeface head, semi, body;
    private boolean allBoards;

    ClubSections(Context c) {
        this.c = c;
        d = c.getResources().getDisplayMetrics().density;
        head = Type.outfit(c, 700);
        semi = Type.outfit(c, 600);
        body = Type.inter(c);
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    /** Fills box with the sections for the page as read so far (fotmobRead: FotMob's part has come, or will not). */
    void fill(LinearLayout box, TeamData data, boolean fotmobRead, boolean wide) {
        box.removeAllViews();
        View club = club(data), season = season(data), figures = figures(data, fotmobRead), leaders = leaders(data, box, fotmobRead, wide);
        if (wide && (club != null || season != null) && figures != null) {
            LinearLayout two = row();
            two.setGravity(Gravity.TOP);
            LinearLayout a = column();
            if (club != null) a.addView(club, gapBelow());
            if (season != null) a.addView(season, gapBelow());
            two.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            bp.leftMargin = dp(14);
            LinearLayout b = column();
            b.addView(figures, gapBelow());
            two.addView(b, bp);
            box.addView(two, matchWrap());
        } else {
            for (View v : new View[]{club, season, figures}) if (v != null) box.addView(v, gapBelow());
        }
        if (leaders != null) box.addView(leaders, matchWrap());
    }

    private LinearLayout.LayoutParams gapBelow() {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.bottomMargin = dp(14);
        return lp;
    }

    // ---- the club: form, last and next ----------------------------------------------------------------------------

    private View club(TeamData data) {
        String form = TeamData.form(data.results, 5), formFrom = Espn.name(data.slug);
        if (form.isEmpty() && !data.formEverywhere.isEmpty()) {
            form = data.formEverywhere.substring(Math.max(0, data.formEverywhere.length() - 5));
            formFrom = "All competitions";
        }
        if (form.isEmpty() && data.last == null && data.next == null && data.venue.isEmpty()) return null;
        LinearLayout card = glassCard();
        card.addView(cardHead("THE CLUB", data.venue));
        if (!form.isEmpty()) {
            LinearLayout fr = row();
            fr.addView(kicker("FORM"), new LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT));
            StringBuilder said = new StringBuilder("Form, oldest first:");
            for (char r : form.toCharArray()) {
                LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(dp(26), dp(26));
                dp.rightMargin = dp(6);
                fr.addView(disc(r, 26), dp);
                said.append(' ').append(r == 'W' ? "won" : r == 'L' ? "lost" : "drawn");
            }
            TextView from = text(formFrom, 11.5f, DIM, body);
            from.setGravity(Gravity.END);
            from.setMaxLines(1);
            from.setEllipsize(TextUtils.TruncateAt.END);
            fr.addView(from, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            fr.setContentDescription(said + " (" + formFrom + ")");
            card.addView(fr);
        }
        if (data.last != null || data.next != null) {
            LinearLayout two = row();
            two.setGravity(Gravity.TOP);
            if (data.last != null) two.addView(gameBox("LAST", data.last), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (data.last != null && data.next != null) two.addView(new View(c), new LinearLayout.LayoutParams(dp(10), 1));
            if (data.next != null) two.addView(gameBox("NEXT", data.next), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams lp = matchWrap();
            lp.topMargin = dp(form.isEmpty() ? 0 : 14);
            card.addView(two, lp);
        }
        return card;
    }

    /** The last game or the next: the other club's crest and name, the score or the day, the competition. */
    private View gameBox(String label, TeamData.Fixture f) {
        LinearLayout b = column();
        b.setBackground(Ui.fill(c, 0x1FFFFFFF, 16));
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        b.addView(kicker(label));
        LinearLayout r = row();
        ImageView crest = new ImageView(c);
        crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
        crest.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        r.addView(crest, new LinearLayout.LayoutParams(dp(26), dp(26)));
        Ui.load(crest, f.otherLogo, null, () -> crest.setVisibility(View.GONE));
        TextView o = text(f.other, 14.5f, TEXT, semi);
        o.setMaxLines(1);
        o.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        op.leftMargin = dp(8);
        r.addView(o, op);
        b.addView(r, top(6));
        LinearLayout dl = row();
        String where = f.home ? "Home" : "Away";
        String said;
        if (f.played) {
            char res = f.result();
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(dp(18), dp(18));
            dp.rightMargin = dp(7);
            dl.addView(disc(res, 18), dp);
            dl.addView(text(f.scored + "–" + f.conceded, 13.5f, SOFT, semi));
            said = (res == 'W' ? "Won " : res == 'L' ? "Lost " : "Drew ") + f.scored + "–" + f.conceded;
        } else {
            String when = f.when > 0 ? Instant.ofEpochMilli(f.when).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.UK)) : "";
            TextView w = text(when.isEmpty() ? "To be arranged" : when, 13.5f, SOFT, semi);
            w.setMaxLines(1);
            w.setEllipsize(TextUtils.TruncateAt.END);
            dl.addView(w, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            said = when;
        }
        b.addView(dl, top(6));
        TextView comp = text(f.comp.isEmpty() ? where : where + " · " + f.comp, 11.5f, DIM, body);
        comp.setMaxLines(1);
        comp.setEllipsize(TextUtils.TruncateAt.END);
        b.addView(comp, top(3));
        b.setContentDescription((label.equals("LAST") ? "Last game: " : "Next game: ") + f.other + ", " + where.toLowerCase(Locale.ROOT)
                + ", " + said + (f.comp.isEmpty() ? "" : ", " + f.comp));
        return b;
    }

    // ---- the season ---------------------------------------------------------------------------------------------------

    private View season(TeamData data) {
        TeamData.Record a = data.all;
        if (!a.has()) return null;
        LinearLayout card = glassCard();
        card.addView(cardHead("THE SEASON", Espn.name(data.slug)));
        LinearLayout tiles = row();
        tiles.addView(tile(data.rank > 0 ? TeamData.ordinal(data.rank) : "–", "Position"), weight());
        tiles.addView(tile(String.valueOf(a.points()), "Points"), spaced());
        tiles.addView(tile(String.valueOf(a.played), "Played"), spaced());
        tiles.addView(tile(signed(a.difference()), "Goal diff."), spaced());
        card.addView(tiles, top(4));
        card.addView(recordBar(a), top(14));
        LinearLayout goals = row();
        goals.setGravity(Gravity.TOP);
        goals.addView(figure(String.valueOf(a.scored), "Scored", perGame(a.scored, a.played)), weight());
        goals.addView(figure(String.valueOf(a.conceded), "Conceded", perGame(a.conceded, a.played)), weight());
        if (!data.results.isEmpty()) {
            int blanks = TeamData.blanks(data.results);
            goals.addView(figure(String.valueOf(TeamData.cleanSheets(data.results)), "Clean sheets",
                    blanks == 0 ? "Scored in every game" : blanks + (blanks == 1 ? " game" : " games") + " without a goal"), weight());
        }
        card.addView(goals, top(16));
        if (data.home.has() || data.away.has()) card.addView(splits(data), top(16));
        TeamData.Fixture best = TeamData.biggest(data.results, true), worst = TeamData.biggest(data.results, false);
        if (best != null || worst != null) {
            LinearLayout extremes = column();
            if (best != null) extremes.addView(line("Biggest win", best));
            if (worst != null) extremes.addView(line("Heaviest defeat", worst));
            card.addView(extremes, top(12));
        }
        return card;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams lp = weight();
        lp.leftMargin = dp(8);
        return lp;
    }

    private View tile(String value, String label) {
        LinearLayout t = column();
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setBackground(Ui.fill(c, 0x17FFFFFF, 14));
        t.setPadding(dp(4), dp(10), dp(4), dp(9));
        TextView v = text(value, 21, TEXT, head);
        v.setGravity(Gravity.CENTER);
        v.setMaxLines(1);
        t.addView(v);
        TextView l = text(label, 11.5f, DIM, body);
        l.setGravity(Gravity.CENTER);
        l.setMaxLines(1);
        t.addView(l, top(3));
        t.setContentDescription(label + ": " + value);
        return t;
    }

    private View recordBar(TeamData.Record a) {
        LinearLayout box = column();
        LinearLayout counts = row();
        counts.addView(count(a.won, "Won", WIN), weight());
        counts.addView(count(a.drawn, "Drawn", DRAW), weight());
        TextView lost = count(a.lost, "Lost", LOSS);
        lost.setGravity(Gravity.END);
        counts.addView(lost, weight());
        box.addView(counts);
        LinearLayout bar = row();
        int[] parts = {a.won, a.drawn, a.lost};
        int[] colours = {WIN, DRAW, LOSS};
        for (int i = 0; i < 3; i++) {
            if (parts[i] <= 0) continue;
            View v = new View(c);
            v.setBackground(Ui.fill(c, colours[i], 3));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(6), parts[i]);
            lp.rightMargin = dp(2);
            bar.addView(v, lp);
        }
        box.addView(bar, top(6));
        box.setContentDescription("Won " + a.won + ", drawn " + a.drawn + ", lost " + a.lost);
        return box;
    }

    private TextView count(int n, String label, int colour) {
        TextView t = text(n + "  " + label, 13, colour, semi);
        t.setMaxLines(1);
        return t;
    }

    private View figure(String value, String label, String under) {
        LinearLayout f = column();
        f.addView(text(value, 23, TEXT, head));
        f.addView(text(label, 12.5f, SOFT, semi), top(2));
        if (under != null && !under.isEmpty()) {
            TextView u = text(under, 11.5f, DIM, body);
            u.setMaxLines(2);
            f.addView(u, top(2));
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

    private View splits(TeamData data) {
        LinearLayout t = column();
        t.addView(splitRow(new String[]{"", "P", "W", "D", "L", "F", "A", "Pts"}, true));
        if (data.home.has()) t.addView(splitRow(cells("Home", data.home), false));
        if (data.away.has()) t.addView(splitRow(cells("Away", data.away), false));
        return t;
    }

    private static String[] cells(String name, TeamData.Record r) {
        return new String[]{name, String.valueOf(r.played), String.valueOf(r.won), String.valueOf(r.drawn), String.valueOf(r.lost),
                String.valueOf(r.scored), String.valueOf(r.conceded), String.valueOf(r.points())};
    }

    private View splitRow(String[] cells, boolean isHead) {
        LinearLayout r = row();
        r.setPadding(0, dp(5), 0, dp(5));
        int ink = isHead ? DIM : SOFT;
        r.addView(text(cells[0], 13.5f, isHead ? ink : TEXT, isHead ? body : semi), weight());
        for (int i = 1; i < cells.length; i++) {
            boolean pts = i == cells.length - 1;
            TextView v = text(cells[i], 13, pts && !isHead ? TEXT : ink, pts || isHead ? semi : body);
            v.setGravity(Gravity.END);
            v.setMaxLines(1);
            r.addView(v, new LinearLayout.LayoutParams(dp(pts ? 36 : 28), ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (!isHead) {
            r.setContentDescription(cells[0] + ": played " + cells[1] + ", won " + cells[2] + ", drawn " + cells[3] + ", lost "
                    + cells[4] + ", scored " + cells[5] + ", conceded " + cells[6] + ", " + cells[7] + " points");
        }
        return r;
    }

    private View line(String label, TeamData.Fixture f) {
        LinearLayout r = row();
        r.setPadding(0, dp(4), 0, dp(4));
        r.addView(text(label, 13, DIM, body), weight());
        TextView v = text(f.scored + "–" + f.conceded + " v " + f.other + (f.home ? " (H)" : " (A)"), 13, TEXT, semi);
        v.setMaxLines(1);
        v.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(v);
        return r;
    }

    // ---- FotMob's figures ---------------------------------------------------------------------------------------------

    private View figures(TeamData data, boolean fotmobRead) {
        if (data.figures.isEmpty()) {
            if (fotmobRead) return null;
            LinearLayout card = glassCard();
            card.addView(text("Reading the season's figures…", 14, DIM, body));
            return card;
        }
        LinearLayout card = glassCard();
        card.addView(cardHead("SEASON FIGURES", "Place in the league"));
        boolean first = true;
        for (Map.Entry<String, List<TeamData.Figure>> s : TeamData.sections(data.figures).entrySet()) {
            TextView h = text(s.getKey().toUpperCase(Locale.ROOT), 11.5f, DIM, semi);
            h.setLetterSpacing(0.1f);
            LinearLayout.LayoutParams lp = matchWrap();
            lp.topMargin = dp(first ? 2 : 14);
            lp.bottomMargin = dp(2);
            card.addView(h, lp);
            first = false;
            for (TeamData.Figure f : s.getValue()) card.addView(figureRow(f));
        }
        TextView src = text("Figures from FotMob", 11, DIM, body);
        src.setGravity(Gravity.END);
        card.addView(src, top(8));
        return card;
    }

    private View figureRow(TeamData.Figure f) {
        LinearLayout r = row();
        r.setPadding(0, dp(6), 0, dp(6));
        TextView n = text(f.name, 14, SOFT, body);
        n.setMaxLines(1);
        n.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(n, weight());
        TextView v = text(f.value, 14.5f, TEXT, semi);
        v.setMaxLines(1);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.leftMargin = dp(8);
        r.addView(v, vp);
        TextView rank = text(f.rank > 0 ? TeamData.ordinal(f.rank) : "", 11.5f, f.rank > 0 && f.rank <= 3 ? 0xFF8BF0BC : SOFT, semi);
        rank.setGravity(Gravity.CENTER);
        rank.setMaxLines(1);
        if (f.rank > 0) rank.setBackground(Ui.fill(c, f.rank <= 3 ? 0x333DDC84 : 0x1AFFFFFF, 10));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(46), dp(22));
        rp.leftMargin = dp(10);
        r.addView(rank, rp);
        r.setContentDescription(f.name + ": " + f.value + (f.rank > 0 ? ", " + TeamData.ordinal(f.rank) + " in the league" : ""));
        return r;
    }

    // ---- the leading players --------------------------------------------------------------------------------------

    private View leaders(TeamData data, LinearLayout box, boolean fotmobRead, boolean wide) {
        List<TeamData.Board> shown = TeamData.shown(data.boards, allBoards);
        if (shown.isEmpty()) return null;
        LinearLayout out = column();
        TextView h = text("LEADING PLAYERS", 13, SOFT, semi);
        h.setLetterSpacing(0.12f);
        h.setPadding(0, dp(4), 0, dp(8));
        out.addView(h);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        LinearLayout r = row();
        r.setGravity(Gravity.TOP);
        for (TeamData.Board b : shown) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(10);
            r.addView(boardCard(b), lp);
        }
        hs.addView(r);
        out.addView(hs, matchWrap());
        if (data.boards.size() > TeamData.shown(data.boards, false).size()) {
            TextView more = text(allBoards ? "Fewer lists" : "All " + data.boards.size() + " lists ›", 13.5f, SOFT, semi);
            more.setPadding(0, dp(10), 0, dp(2));
            more.setOnClickListener(v -> {
                allBoards = !allBoards;
                fill(box, data, fotmobRead, wide);
            });
            out.addView(more);
        }
        return out;
    }

    private View boardCard(TeamData.Board b) {
        LinearLayout card = column();
        card.setBackground(Ui.glass(c, 18));
        int p = dp(12);
        card.setPadding(p, p, p, dp(10));
        TextView t = text(b.name.toUpperCase(Locale.ROOT), 11.5f, SOFT, semi);
        t.setLetterSpacing(0.08f);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(t);
        StringBuilder said = new StringBuilder(b.name).append(':');
        for (int i = 0; i < b.top.size(); i++) {
            TeamData.Leader l = b.top.get(i);
            boolean lead = i == 0;
            LinearLayout r = row();
            r.setPadding(0, dp(lead ? 10 : 7), 0, 0);
            int size = lead ? 40 : 30;
            r.addView(face(l.photo, l.name, size), new LinearLayout.LayoutParams(dp(size), dp(size)));
            TextView n = text(l.name, lead ? 14 : 13, TEXT, lead ? semi : body);
            n.setMaxLines(1);
            n.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np = weight();
            np.leftMargin = dp(9);
            r.addView(n, np);
            TextView v = text(l.value, lead ? 16 : 13.5f, TEXT, head);
            v.setMaxLines(1);
            LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            vp.leftMargin = dp(6);
            r.addView(v, vp);
            card.addView(r);
            said.append(' ').append(l.name).append(' ').append(l.value).append(i + 1 < b.top.size() ? ';' : '.');
        }
        card.setContentDescription(said);
        return card;
    }

    // ---- small parts --------------------------------------------------------------------------------------------------

    /** A picture in a disc, the initials under it until it comes (and when there is none). */
    private View face(String url, String name, int sizeDp) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(Ui.fill(c, 0x33FFFFFF, sizeDp / 2f));
        TextView ini = text(TeamData.initials(name), Math.max(10, sizeDp * 0.34f), 0xFFE4E6F5, semi);
        ini.setGravity(Gravity.CENTER);
        ini.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        f.addView(ini, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (url != null && !url.isEmpty()) {
            ImageView img = new ImageView(c);
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

    private TextView disc(char r, int sizeDp) {
        TextView t = text(String.valueOf(r), sizeDp >= 24 ? 12 : 10, Color.WHITE, semi);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.fill(c, r == 'W' ? WIN : r == 'L' ? LOSS : DRAW, sizeDp / 2f));
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return t;
    }

    private TextView kicker(String words) {
        TextView t = text(words, 11, DIM, semi);
        t.setLetterSpacing(0.12f);
        return t;
    }

    private LinearLayout glassCard() {
        LinearLayout card = column();
        card.setBackground(Ui.glass(c, 18));
        int p = dp(14);
        card.setPadding(p, dp(12), p, dp(12));
        return card;
    }

    private View cardHead(String title, String right) {
        LinearLayout r = row();
        TextView t = text(title, 12, SOFT, semi);
        t.setLetterSpacing(0.12f);
        r.addView(t, weight());
        if (right != null && !right.isEmpty()) {
            TextView x = text(right, 12, DIM, body);
            x.setMaxLines(1);
            x.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f);
            x.setGravity(Gravity.END);
            r.addView(x, xp);
        }
        r.setPadding(0, 0, 0, dp(10));
        return r;
    }

    private TextView text(String s, float sp, int colour, Typeface face) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setTypeface(face);
        return t;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams top(int dpTop) {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(dpTop);
        return lp;
    }
}
