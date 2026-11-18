package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.broadcast.SportBoard;
import com.gathin.tvhomephone.glass.Glass;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The Sports page's pieces in the glass of Home: frosted cards, and the rows in them, each with every logo it can carry:
 * the competition's or the league's, both clubs' crests or teams' logos, players' flags and fighters' photos, and the
 * British channel it is on (Sky's guide logo). Pure layout: SportsScreen places them, SportsFragment gives the data.
 */
final class SportCards {
    private SportCards() {}

    static final int LIVE_RED = 0xFFE5484D, MINE = 0xFF3DDC84, GOLD = 0xFFFFC94D;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm"),
            DAY_HM = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK);

    // ---- basics -----------------------------------------------------------------------------------------------------

    /** A frosted card, its rows inside it. */
    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(Glass.card(c, 20));
        Glass.round(l, Glass.dp(c, 20));
        int p = Glass.dp(c, 14);
        l.setPadding(p, Glass.dp(c, 12), p, Glass.dp(c, 12));
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static TextView text(Context c, CharSequence s, float sp, int colour, Typeface face) {
        TextView t = Glass.text(c, s, sp, colour, face);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    static Typeface semi(Context c) {
        return Glass.bodySemi(c);
    }

    static Typeface body(Context c) {
        return Glass.body(c);
    }

    static Typeface bold(Context c) {
        return Glass.heading(c, 700);
    }

    /** A logo or crest fitted in a box of w by h dp; gone (its room kept) when it does not load. */
    static ImageView image(Context c, String url, int wDp, int hDp) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        v.setLayoutParams(new LinearLayout.LayoutParams(Glass.dp(c, wDp), Glass.dp(c, hDp)));
        if (url == null || url.isEmpty()) v.setVisibility(View.INVISIBLE);
        else Ui.load(v, url, null, () -> v.setVisibility(View.INVISIBLE));
        return v;
    }

    /** A round photo (a driver's, a fighter's) on a colour, its initials until (or unless) it loads. */
    static View photo(Context c, String url, String name, int colour, int sizeDp) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable round = new GradientDrawable();
        round.setShape(GradientDrawable.OVAL);
        round.setColor(colour);
        f.setBackground(round);
        f.setClipToOutline(true);
        f.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        TextView initials = Glass.text(c, F1.initials(name == null ? "" : name), sizeDp * 0.34f, 0xFFFFFFFF, bold(c));
        initials.setGravity(Gravity.CENTER);
        f.addView(initials, new FrameLayout.LayoutParams(-1, -1));
        if (url != null && !url.isEmpty()) {
            ImageView v = new ImageView(c);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(v, new FrameLayout.LayoutParams(-1, -1));
            Ui.load(v, url, () -> initials.setVisibility(View.GONE), () -> v.setVisibility(View.GONE));
        }
        f.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        f.setLayoutParams(new LinearLayout.LayoutParams(Glass.dp(c, sizeDp), Glass.dp(c, sizeDp)));
        return f;
    }

    /**
     * A channel's logo (Sky's guide logo, the playlist's), heightDp tall, in the line as it is (a soft halo behind one too
     * dark for the glass: LogoTiles); its name written out when it has none.
     */
    static View channel(Context c, String name, int heightDp) {
        String url = name == null ? null : ChannelLogos.known(c, name);
        if (url == null) {
            TextView t = text(c, name == null ? "" : name, 11, Glass.MUTED, semi(c));
            t.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
            return t;
        }
        ImageView v = new ImageView(c);
        v.setAdjustViewBounds(true);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setMaxWidth(Glass.dp(c, 118));
        v.setContentDescription(name);
        v.setLayoutParams(new LinearLayout.LayoutParams(-2, Glass.dp(c, heightDp)));
        TextView fallback = text(c, name, 11, Glass.MUTED, semi(c));
        com.gathin.tvhomephone.live.LogoTiles.loadBare(v, url, com.gathin.tvhomephone.live.LogoTiles.ON_NAVY, () -> {
            if (v.getParent() instanceof LinearLayout) {
                LinearLayout p = (LinearLayout) v.getParent();
                int at = p.indexOfChild(v);
                p.removeView(v);
                p.addView(fallback, at, new LinearLayout.LayoutParams(-2, -2));
            }
        });
        return v;
    }

    /** Up to max channels' logos side by side, the first first. */
    static LinearLayout channels(Context c, List<String> names, int heightDp, int max) {
        LinearLayout r = row(c);
        for (int i = 0; names != null && i < names.size() && i < max; i++) {
            View v = channel(c, names.get(i), heightDp);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
            if (i > 0) lp.leftMargin = Glass.dp(c, 8);
            r.addView(v, lp);
        }
        return r;
    }

    /** A soft ripple for a row that opens something. */
    static android.graphics.drawable.RippleDrawable touch(Context c) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadius(Glass.dp(c, 10));
        return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x24FFFFFF), null, mask);
    }

    /** A thin line between rows. */
    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(0x14FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, Glass.dp(c, 0.7f)));
        lp.topMargin = Glass.dp(c, 8);
        lp.bottomMargin = Glass.dp(c, 8);
        v.setLayoutParams(lp);
        return v;
    }

    /** A small pill: "● LIVE 71'", "FT", "Sun 16:30". */
    static TextView pill(Context c, String words, int fill, int ink) {
        TextView t = text(c, words, 11, ink, semi(c));
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(Glass.dp(c, 10));
        t.setBackground(d);
        t.setPadding(Glass.dp(c, 8), Glass.dp(c, 3), Glass.dp(c, 8), Glass.dp(c, 3));
        return t;
    }

    /** A sport's mark: its league's logo where it has one (the NFL's, F1's…), else its emoji. */
    static View sportMark(Context c, String sport, String title, int sizeDp) {
        String logo = OnTv.logo(sport, title);
        if (logo != null) return image(c, logo, sizeDp, sizeDp);
        TextView t = Glass.text(c, OnTv.emoji(sport), sizeDp * 0.62f, 0xFFFFFFFF, null);
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(new LinearLayout.LayoutParams(Glass.dp(c, sizeDp), Glass.dp(c, sizeDp)));
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return t;
    }

    /** "16:30" today, "Tue 19:30" another day (British time). */
    static String when(long ms) {
        if (ms <= 0) return "";
        ZonedDateTime t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());
        return t.toLocalDate().equals(LocalDate.now()) ? t.format(HM) : t.format(DAY_HM);
    }

    // ---- football ---------------------------------------------------------------------------------------------------

    /**
     * One game as a row: its state (FT, the minute in red, the kick-off), both clubs' crests and names with the score,
     * and the channel's logo while it is to come or on.
     */
    static View gameRow(Context c, Game g, String channel, boolean mine, View.OnClickListener tap) {
        LinearLayout r = row(c);
        r.setPadding(0, Glass.dp(c, 2), 0, Glass.dp(c, 2));
        String state = g.state == Game.LIVE ? (g.halftime ? "HT" : g.detail) : g.state == Game.POST ? "FT" : when(g.kickoff);
        TextView s = text(c, state, 12, g.state == Game.LIVE ? LIVE_RED : Glass.MUTED, semi(c));
        s.setMaxLines(2);
        r.addView(s, new LinearLayout.LayoutParams(Glass.dp(c, 50), -2));
        LinearLayout teams = column(c);
        teams.addView(side(c, g.homeLogo, g.homeName, g.state == Game.PRE ? "" : String.valueOf(g.homeScore),
                g.state == Game.POST && g.homeScore > g.awayScore, mine));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
        ap.topMargin = Glass.dp(c, 6);
        teams.addView(side(c, g.awayLogo, g.awayName, g.state == Game.PRE ? "" : String.valueOf(g.awayScore),
                g.state == Game.POST && g.awayScore > g.homeScore, mine), ap);
        r.addView(teams, new LinearLayout.LayoutParams(0, -2, 1f));
        if (channel != null && g.state != Game.POST) {
            View ch = channel(c, channel, 14);
            LinearLayout.LayoutParams cp = (LinearLayout.LayoutParams) ch.getLayoutParams();
            cp.leftMargin = Glass.dp(c, 10);
            r.addView(ch, cp);
        }
        r.setContentDescription(g.homeName + " " + (g.state == Game.PRE ? "v" : g.homeScore + ", " + g.awayScore) + " " + g.awayName
                + ", " + state + (channel == null ? "" : ", on " + channel));
        if (tap != null) {
            r.setOnClickListener(tap);
            r.setBackground(touch(c));
        }
        return r;
    }

    private static View side(Context c, String logo, String name, String score, boolean won, boolean mine) {
        LinearLayout r = row(c);
        ImageView crest = image(c, logo, 20, 20);
        r.addView(crest);
        TextView n = text(c, name == null ? "" : name, 15, won || mine ? Glass.TEXT : Glass.SOFT, won ? semi(c) : body(c));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1f);
        np.leftMargin = Glass.dp(c, 10);
        r.addView(n, np);
        if (!score.isEmpty()) r.addView(text(c, score, 15, Glass.TEXT, semi(c)));
        return r;
    }

    /** A competition's heading inside a card: its logo and its name, what it is on at the right (or nothing). */
    static View compHead(Context c, String logo, String name, String right) {
        LinearLayout r = row(c);
        r.setPadding(0, 0, 0, Glass.dp(c, 8));
        r.addView(image(c, logo, 22, 22));
        TextView n = text(c, name, 14, Glass.TEXT, semi(c));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1f);
        np.leftMargin = Glass.dp(c, 10);
        r.addView(n, np);
        if (right != null && !right.isEmpty()) r.addView(text(c, right, 12, Glass.MUTED, body(c)));
        return r;
    }

    // ---- other sports -----------------------------------------------------------------------------------------------

    /** A live sport listing: when (LIVE in red while on), the sport's mark, the event, and every channel's logo. */
    static View listingRow(Context c, OnTv.Listing l, long now, View.OnClickListener tap) {
        LinearLayout r = row(c);
        r.setPadding(0, Glass.dp(c, 4), 0, Glass.dp(c, 4));
        boolean on = l.on(now);
        TextView t = text(c, on ? "LIVE" : when(l.start), 12, on ? LIVE_RED : Glass.MUTED, semi(c));
        t.setMaxLines(2);
        r.addView(t, new LinearLayout.LayoutParams(Glass.dp(c, 50), -2));
        r.addView(sportMark(c, l.sport, l.title, 26));
        LinearLayout words = column(c);
        TextView title = Glass.text(c, l.title, 14, Glass.TEXT, semi(c));
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(title);
        LinearLayout chans = channels(c, l.channels, 13, 3);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        cp.topMargin = Glass.dp(c, 5);
        words.addView(chans, cp);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1f);
        wp.leftMargin = Glass.dp(c, 10);
        r.addView(words, wp);
        r.setContentDescription((on ? "Live now: " : when(l.start) + ": ") + l.title + ", on " + String.join(", ", l.channels));
        if (tap != null) {
            r.setOnClickListener(tap);
            r.setBackground(touch(c));
        }
        return r;
    }

    /** One NFL or NBA game: its state, both teams' logos and names with the score, the British channel's logo. */
    static View matchRow(Context c, MoreSports.Match m, View.OnClickListener tap) {
        LinearLayout r = row(c);
        r.setPadding(0, Glass.dp(c, 2), 0, Glass.dp(c, 2));
        String state = m.live() ? m.detail : m.over() ? "Final" : when(m.start);
        TextView s = text(c, state, 12, m.live() ? LIVE_RED : Glass.MUTED, semi(c));
        s.setMaxLines(2);
        r.addView(s, new LinearLayout.LayoutParams(Glass.dp(c, 50), -2));
        LinearLayout teams = column(c);
        teams.addView(side(c, m.awayLogo, m.away, m.live() || m.over() ? m.awayScore : "", m.awayWin, false));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = Glass.dp(c, 6);
        teams.addView(side(c, m.homeLogo, m.home, m.live() || m.over() ? m.homeScore : "", m.homeWin, false), hp);
        r.addView(teams, new LinearLayout.LayoutParams(0, -2, 1f));
        if (m.channel != null && !m.over()) {
            View ch = channel(c, m.channel, 14);
            LinearLayout.LayoutParams cp = (LinearLayout.LayoutParams) ch.getLayoutParams();
            cp.leftMargin = Glass.dp(c, 10);
            r.addView(ch, cp);
        }
        r.setContentDescription(m.away + " at " + m.home + ", " + state + (m.channel == null ? "" : ", on " + m.channel));
        if (tap != null) {
            r.setOnClickListener(tap);
            r.setBackground(touch(c));
        }
        return r;
    }

    /** One tennis match: its state, each player's flag and name with the games of each set, the winner bright. */
    static View tennisRow(Context c, SportBoard s) {
        LinearLayout r = row(c);
        r.setPadding(0, Glass.dp(c, 2), 0, Glass.dp(c, 2));
        String state = s.live() ? "LIVE" : s.over() ? (s.method.isEmpty() ? "Final" : s.method) : when(s.start);
        TextView t = text(c, state, 12, s.live() ? LIVE_RED : Glass.MUTED, semi(c));
        t.setMaxLines(2);
        r.addView(t, new LinearLayout.LayoutParams(Glass.dp(c, 50), -2));
        LinearLayout players = column(c);
        players.addView(player(c, s.a, s.b, !s.live() && !s.over()));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = Glass.dp(c, 6);
        players.addView(player(c, s.b, s.a, !s.live() && !s.over()), bp);
        r.addView(players, new LinearLayout.LayoutParams(0, -2, 1f));
        r.setContentDescription(s.a.name + " v " + s.b.name + ", " + state + (s.stage.isEmpty() ? "" : ", " + s.stage));
        return r;
    }

    private static View player(Context c, SportBoard.Side p, SportBoard.Side other, boolean before) {
        LinearLayout r = row(c);
        r.addView(image(c, p.flagUrl, 20, 14));
        String seed = p.seed == null || p.seed.isEmpty() ? "" : " (" + p.seed + ")";
        TextView n = text(c, p.name + seed, 14, p.winner ? Glass.TEXT : Glass.SOFT, p.winner ? semi(c) : body(c));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1f);
        np.leftMargin = Glass.dp(c, 10);
        r.addView(n, np);
        if (!before) {
            for (int i = 0; i < p.games.length; i++) {
                boolean took = i < other.games.length && p.games[i] > other.games[i];
                TextView g = text(c, String.valueOf(p.games[i]), 14, took ? Glass.TEXT : Glass.MUTED, took ? semi(c) : body(c));
                g.setGravity(Gravity.CENTER);
                r.addView(g, new LinearLayout.LayoutParams(Glass.dp(c, 20), -2));
            }
        }
        return r;
    }

    /** UFC's card: its logo and name, when, the main event's two fighters face to face, then the other bouts. */
    static View fightCard(Context c, List<SportBoard> card, String channel, View.OnClickListener tap) {
        LinearLayout box = card(c);
        SportBoard main = card.get(0);
        LinearLayout head = row(c);
        head.addView(image(c, SportFeeds_UFC_LOGO, 46, 24));
        LinearLayout words = column(c);
        words.addView(text(c, main.event, 15, Glass.TEXT, semi(c)));
        words.addView(text(c, (main.live() ? "Live now" : main.over() ? "Results" : dayAndTime(main.start)), 12, main.live() ? LIVE_RED : Glass.MUTED, body(c)));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1f);
        wp.leftMargin = Glass.dp(c, 10);
        head.addView(words, wp);
        if (channel != null) head.addView(channel(c, channel, 14));
        box.addView(head);
        // The main event, face to face.
        LinearLayout face = row(c);
        face.setPadding(0, Glass.dp(c, 14), 0, Glass.dp(c, 6));
        face.addView(fighter(c, main.a), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView vs = text(c, main.over() ? (main.method.isEmpty() ? "Result" : main.method) : "v", main.over() ? 12 : 18, Glass.MUTED, semi(c));
        vs.setGravity(Gravity.CENTER);
        face.addView(vs, new LinearLayout.LayoutParams(Glass.dp(c, 56), -2));
        face.addView(fighter(c, main.b), new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(face);
        if (!main.stage.isEmpty()) {
            TextView div = text(c, main.stage, 12, Glass.MUTED, body(c));
            div.setGravity(Gravity.CENTER);
            box.addView(div, new LinearLayout.LayoutParams(-1, -2));
        }
        for (int i = 1; i < card.size() && i < 6; i++) {
            SportBoard b = card.get(i);
            box.addView(divider(c));
            LinearLayout r = row(c);
            r.addView(image(c, b.a.flagUrl, 18, 12));
            TextView a = text(c, b.a.name, 13, b.a.winner ? Glass.TEXT : Glass.SOFT, b.a.winner ? semi(c) : body(c));
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, -2, 1f);
            ap.leftMargin = Glass.dp(c, 8);
            r.addView(a, ap);
            TextView v = text(c, "v", 12, Glass.FAINT, body(c));
            v.setGravity(Gravity.CENTER);
            r.addView(v, new LinearLayout.LayoutParams(Glass.dp(c, 22), -2));
            TextView bn = text(c, b.b.name, 13, b.b.winner ? Glass.TEXT : Glass.SOFT, b.b.winner ? semi(c) : body(c));
            bn.setGravity(Gravity.END);
            r.addView(bn, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(Glass.dp(c, 18), Glass.dp(c, 12));
            fp.leftMargin = Glass.dp(c, 8);
            r.addView(image(c, b.b.flagUrl, 18, 12), fp);
            box.addView(r);
        }
        if (tap != null) box.setOnClickListener(tap);
        box.setContentDescription(main.event + ": " + main.a.name + " v " + main.b.name);
        return box;
    }

    static final String SportFeeds_UFC_LOGO = com.gathin.broadcast.SportFeeds.UFC_LOGO;

    private static View fighter(Context c, SportBoard.Side s) {
        LinearLayout col = column(c);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        View p = photo(c, s.photoUrl, s.name, s.colour, 64);
        col.addView(p);
        TextView n = Glass.text(c, s.name, 14, s.winner ? GOLD : Glass.TEXT, semi(c));
        n.setGravity(Gravity.CENTER);
        n.setMaxLines(2);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = Glass.dp(c, 8);
        col.addView(n, np);
        LinearLayout line = row(c);
        line.setGravity(Gravity.CENTER);
        line.addView(image(c, s.flagUrl, 18, 12));
        if (s.record != null && !s.record.isEmpty()) {
            TextView rec = text(c, s.record, 12, Glass.MUTED, body(c));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
            rp.leftMargin = Glass.dp(c, 6);
            line.addView(rec, rp);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Glass.dp(c, 3);
        col.addView(line, lp);
        return col;
    }

    /** A golf tournament: its tour's logo and name, how far on, the channel; its leaders with their flags. */
    static View golfCard(Context c, MoreSports.Leaderboard b) {
        LinearLayout box = card(c);
        LinearLayout head = row(c);
        head.addView(image(c, b.logo, 34, 34));
        LinearLayout words = column(c);
        words.addView(text(c, b.event, 15, Glass.TEXT, semi(c)));
        String state = "in".equals(b.state) ? "Live" : "post".equals(b.state) ? "Final" : b.detail;
        words.addView(text(c, b.tour + (state.isEmpty() ? "" : " · " + state), 12, "in".equals(b.state) ? LIVE_RED : Glass.MUTED, body(c)));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1f);
        wp.leftMargin = Glass.dp(c, 10);
        head.addView(words, wp);
        if (b.channel != null) head.addView(channel(c, b.channel, 14));
        box.addView(head);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-1, -2);
        gap.topMargin = Glass.dp(c, 10);
        boolean first = true;
        for (String[] row : b.rows) {
            LinearLayout r = row(c);
            r.setPadding(0, Glass.dp(c, 4), 0, Glass.dp(c, 4));
            r.addView(text(c, row[0], 13, Glass.MUTED, semi(c)), new LinearLayout.LayoutParams(Glass.dp(c, 34), -2));
            r.addView(image(c, row[2], 20, 14));
            TextView n = text(c, row[1], 14, Glass.TEXT, body(c));
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1f);
            np.leftMargin = Glass.dp(c, 10);
            r.addView(n, np);
            TextView rounds = text(c, row[4], 11, Glass.FAINT, body(c));
            r.addView(rounds);
            TextView par = text(c, row[3], 14, row[3].startsWith("-") ? MINE : Glass.TEXT, semi(c));
            par.setGravity(Gravity.END);
            r.addView(par, new LinearLayout.LayoutParams(Glass.dp(c, 44), -2));
            box.addView(r, first ? gap : new LinearLayout.LayoutParams(-1, -2));
            first = false;
        }
        box.setContentDescription(b.event + ", " + b.tour);
        return box;
    }

    /** "Sat 21 Nov · 15:00": a day and a time. */
    static String dayAndTime(long ms) {
        if (ms <= 0) return "";
        return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.UK));
    }
}
