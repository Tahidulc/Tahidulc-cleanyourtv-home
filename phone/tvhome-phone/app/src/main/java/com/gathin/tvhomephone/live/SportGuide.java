package com.gathin.tvhomephone.live;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.Clubs;
import com.gathin.tvhomephone.sports.Competitions;
import com.gathin.tvhomephone.sports.Espn;
import com.gathin.tvhomephone.sports.F1;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.OnAir;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Live TV's Sport choice: what is live or still to come today in sport, on the family's own channels. Today's games in
 * the competitions the app reads, each with the playlist's channel most sure to show it (an event channel named for it,
 * a channel whose guide has it, a broadcaster's channel: AlsoShowing's order) and how many more show it, grouped by
 * competition, live ones first; the F1 weekend's sessions today on the playlist's F1 channel. The clubs the family
 * follows are marked Your club. Tap one to watch it. A game none of the channels show is left out (counted at the foot).
 */
public final class SportGuide extends ScrollView {
    /** Its chip's group, beside Live TV's Favourites, All and Recent. */
    public static final String GROUP = "⚽";
    static final String ALL = "all", LIVE = "live", FOOTBALL = "football", F1_KIND = "f1";

    /** Where a pick goes (Live TV's own play: the guide's picture, or full screen). */
    public interface Pick {
        void play(Channel ch);
    }

    /** One line: a game (or an F1 session), its channel and the words under it. */
    static final class Row {
        String kind = FOOTBALL, slug = "", compName = "", compLogo, title = "", under = "", minute = "";
        Game game;
        Channel ch;
        boolean live, mine;
        long when;
    }

    private final LinearLayout col, chips, list;
    private final Pick pick;
    private String filter = ALL;
    private List<Row> rows = new ArrayList<>();
    private int missing, gen;
    private boolean loading;

    public SportGuide(Context c, Pick pick) {
        super(c);
        this.pick = pick;
        setFillViewport(true);
        col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 24));
        HorizontalScrollView cs = new HorizontalScrollView(c);
        cs.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(c);
        chips.setPadding(Ui.dp(c, 12), Ui.dp(c, 2), Ui.dp(c, 12), Ui.dp(c, 8));
        cs.addView(chips);
        col.addView(cs);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);
        addView(col);
    }

    /** Today's games and their channels read again (off the main thread), then shown. */
    public void load() {
        Context app = getContext().getApplicationContext();
        int my = ++gen;
        if (rows.isEmpty()) {
            loading = true;
            show();
        }
        App.io.execute(() -> {
            List<Row> got = new ArrayList<>();
            int[] none = {0};
            try {
                got = read(app, none);
            } catch (RuntimeException ignored) {
                // what there is
            }
            List<Row> r = got;
            App.main.post(() -> {
                if (my != gen) return;
                rows = r;
                missing = none[0];
                loading = false;
                show();
            });
        });
    }

    /** Rows given as they are (the previews). */
    void set(List<Row> r, int missing) {
        rows = r;
        this.missing = missing;
        loading = false;
        show();
    }

    /** The rows for today: games with a channel to watch them on, then today's F1 sessions. Off the main thread. */
    static List<Row> read(Context c, int[] missing) {
        long now = System.currentTimeMillis();
        long dayEnd = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 2 * 3600_000L;
        Set<String> teams = Clubs.starred(c);
        Library lib = Library.get(c);
        List<Row> out = new ArrayList<>();
        for (Game g : OnAir.today(60_000L)) {
            if (!wanted(g, now, dayEnd)) continue;
            List<AlsoShowing.Row> on = AlsoShowing.rows(c, g, null);
            Channel best = null;
            int more = 0;
            boolean uhd = false;
            for (AlsoShowing.Row a : on) {
                if (a.ch == null) continue;
                if (best == null) best = a.ch;
                else {
                    more++;
                    uhd |= a.uhd && !Names.uhd(best.name);
                }
            }
            if (best == null) {
                missing[0]++;
                continue;
            }
            Row r = new Row();
            r.game = g;
            r.ch = best;
            r.slug = g.slug == null ? "" : g.slug;
            r.compName = Espn.name(r.slug);
            if (r.compName == null || r.compName.isEmpty()) r.compName = g.leagueName == null ? "" : g.leagueName;
            r.compLogo = Competitions.logo(g);
            r.live = g.state == Game.LIVE;
            r.mine = g.involves(teams);
            r.when = g.kickoff;
            r.title = g.homeName + (g.state == Game.PRE ? " v " : " " + g.score() + " ") + g.awayName;
            r.minute = r.live ? (g.detail == null || g.detail.isEmpty() ? "LIVE" : g.detail) : clock(g.kickoff);
            r.under = channelLine(best.name, uhd, uhd ? more - 1 : more);
            out.add(r);
        }
        // F1: the weekend's sessions today, on the playlist's F1 channel.
        if (Competitions.on(c, Competitions.F1)) {
            F1.Weekend w = F1.next();
            Channel f1 = null;
            if (w != null) {
                Channel[] t = lib.tiers("Sky Sports F1");
                f1 = t[0] != null ? t[0] : t[1];
            }
            for (int i = 0; w != null && f1 != null && i < w.sessions.size(); i++) {
                Object[] s = w.sessions.get(i);
                long at = (Long) s[1];
                if (at + 3 * 3600_000L < now || at > dayEnd) continue;
                Row r = new Row();
                r.kind = F1_KIND;
                r.slug = Competitions.F1;
                r.compName = "Formula 1";
                r.compLogo = Competitions.F1_LOGO;
                r.ch = f1;
                r.when = at;
                r.live = at <= now && now < at + ("Race".equals(s[0]) ? 2 * 3600_000L : 3600_000L);
                r.title = (w.name == null ? "" : w.name + " · ") + s[0];
                r.minute = r.live ? "LIVE" : clock(at);
                r.under = f1.name;
                out.add(r);
            }
        }
        return order(out);
    }

    /** A game still to see today: live, or kicking off before the day is out. Pure. */
    static boolean wanted(Game g, long now, long dayEnd) {
        if (g == null || g.state == Game.POST) return false;
        return g.state == Game.LIVE || (g.kickoff > now - 3 * 3600_000L && g.kickoff < dayEnd);
    }

    /** "Sports 1 HD · also in 4K and on 2 more", "Sports 1 HD · also on 1 more", "Sports 1 HD". Pure. */
    static String channelLine(String best, boolean uhd, int more) {
        StringBuilder b = new StringBuilder(best == null ? "" : best);
        if (uhd || more > 0) {
            b.append(" · also ");
            if (uhd) b.append("in 4K");
            if (uhd && more > 0) b.append(" and ");
            if (more > 0) b.append("on ").append(more).append(" more");
        }
        return b.toString();
    }

    /**
     * By competition (in the order the app reads them, F1 last), a competition with the family's club or a live game
     * first; in each, the family's clubs, then what is live, then by kick-off. Pure.
     */
    static List<Row> order(List<Row> rows) {
        Map<String, List<Row>> by = new LinkedHashMap<>();
        List<Row> sorted = new ArrayList<>(rows);
        sorted.sort((a, b) -> Integer.compare(rank(a.slug), rank(b.slug)));
        for (Row r : sorted) {
            List<Row> l = by.get(r.slug);
            if (l == null) by.put(r.slug, l = new ArrayList<>());
            l.add(r);
        }
        List<List<Row>> groups = new ArrayList<>(by.values());
        for (List<Row> l : groups) {
            l.sort((a, b) -> a.mine != b.mine ? (a.mine ? -1 : 1) : a.live != b.live ? (a.live ? -1 : 1) : Long.compare(a.when, b.when));
        }
        // Stable: the app's order, except that a competition with the family's club or a game on now comes first.
        List<List<Row>> first = new ArrayList<>(), rest = new ArrayList<>();
        for (List<Row> l : groups) (l.get(0).mine || l.get(0).live ? first : rest).add(l);
        List<Row> out = new ArrayList<>();
        for (List<Row> l : first) out.addAll(l);
        for (List<Row> l : rest) out.addAll(l);
        return out;
    }

    private static int rank(String slug) {
        if (Competitions.F1.equals(slug)) return Espn.LEAGUES.length + 1;
        for (int i = 0; i < Espn.LEAGUES.length; i++) if (Espn.LEAGUES[i][0].equals(slug)) return i;
        return Espn.LEAGUES.length;
    }

    /** The rows the chosen sub-chip lets through. Pure. */
    static List<Row> filtered(List<Row> rows, String filter) {
        List<Row> out = new ArrayList<>();
        for (Row r : rows) {
            if (LIVE.equals(filter) && !r.live) continue;
            if (FOOTBALL.equals(filter) && !FOOTBALL.equals(r.kind)) continue;
            if (F1_KIND.equals(filter) && !F1_KIND.equals(r.kind)) continue;
            out.add(r);
        }
        return out;
    }

    private static String clock(long ms) {
        return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm", Locale.UK));
    }

    // ---- the screen ---------------------------------------------------------------------------------------------

    private void show() {
        Context c = getContext();
        chips.removeAllViews();
        int live = filtered(rows, LIVE).size(), f1 = filtered(rows, F1_KIND).size();
        chip(c, c.getString(R.string.f1_sport_all), ALL, true);
        chip(c, c.getString(R.string.f1_sport_live, live), LIVE, live > 0);
        chip(c, c.getString(R.string.f1_sport_football), FOOTBALL, filtered(rows, FOOTBALL).size() > 0);
        chip(c, c.getString(R.string.f1_sport_f1), F1_KIND, f1 > 0);
        list.removeAllViews();
        List<Row> shown = filtered(rows, filter);
        if (shown.isEmpty()) {
            TextView t = Ui.text(c, c.getString(loading ? R.string.f1_sport_loading : R.string.f1_sport_none), 15, Ui.SOFT, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(c, 32), Ui.dp(c, 48), Ui.dp(c, 32), 0);
            list.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        String at = null;
        for (Row r : shown) {
            if (!r.slug.equals(at)) {
                at = r.slug;
                list.addView(heading(c, r));
            }
            list.addView(row(c, r));
        }
        if (missing > 0 && !shown.isEmpty() && !F1_KIND.equals(filter)) {
            TextView t = Ui.text(c, c.getResources().getQuantityString(R.plurals.f1_sport_missing, missing, missing), 12, Ui.G_TIME, false);
            t.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), 0);
            list.addView(t);
        }
    }

    private void chip(Context c, String label, String key, boolean any) {
        if (!any && !ALL.equals(key)) return;
        TextView t = Ui.guideChip(c, label, key.equals(filter));
        t.setOnClickListener(v -> {
            filter = key;
            show();
            scrollTo(0, 0);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(c, 6);
        chips.addView(t, lp);
    }

    private View heading(Context c, Row r) {
        LinearLayout h = new LinearLayout(c);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 6));
        ImageView logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (r.compLogo != null) com.gathin.tvhomephone.sports.Ui.load(logo, r.compLogo);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22));
        lp.rightMargin = Ui.dp(c, 8);
        h.addView(logo, lp);
        TextView t = Ui.text(c, r.compName.toUpperCase(Locale.UK), 13, Ui.TEXT, true);
        t.setLetterSpacing(0.06f);
        h.addView(t);
        return h;
    }

    private View row(Context c, Row r) {
        LinearLayout v = new LinearLayout(c);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setMinimumHeight(Ui.dp(c, 64));
        int p = Ui.dp(c, 12);
        v.setPadding(p, Ui.dp(c, 8), p, Ui.dp(c, 8));
        v.setBackground(Ui.touch(c, false, r.mine ? 0x2EFFFFFF : 0x00000000, 12));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, -2);
        vp.leftMargin = vp.rightMargin = Ui.dp(c, 8);
        vp.bottomMargin = Ui.dp(c, 4);
        v.setLayoutParams(vp);

        LinearLayout when = new LinearLayout(c);
        when.setOrientation(LinearLayout.VERTICAL);
        when.setGravity(Gravity.CENTER_HORIZONTAL);
        if (r.live) {
            TextView badge = Ui.text(c, "LIVE", 10, Ui.NAVY_INK, true);
            badge.setBackground(Ui.round(c, Ui.GREEN, 4));
            badge.setPadding(Ui.dp(c, 6), Ui.dp(c, 1), Ui.dp(c, 6), Ui.dp(c, 1));
            when.addView(badge, new LinearLayout.LayoutParams(-2, -2));
            if (!"LIVE".equals(r.minute)) {
                TextView m = Ui.text(c, r.minute, 12, Ui.TEXT, true);
                m.setFontFeatureSettings("tnum");
                m.setPadding(0, Ui.dp(c, 3), 0, 0);
                when.addView(m, new LinearLayout.LayoutParams(-2, -2));
            }
        } else {
            TextView m = Ui.text(c, r.minute, 13, Ui.TEXT, true);
            m.setFontFeatureSettings("tnum");
            when.addView(m, new LinearLayout.LayoutParams(-2, -2));
        }
        v.addView(when, new LinearLayout.LayoutParams(Ui.dp(c, 52), -2));

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (r.game != null) top.addView(crest(c, r.game.homeLogo != null ? r.game.homeLogo : Espn.crest(r.game.homeId)));
        TextView title = Ui.text(c, r.title, 15, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        // The away crest at the end of the line, in line with the others above and below.
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.leftMargin = tp.rightMargin = Ui.dp(c, r.game != null ? 6 : 0);
        top.addView(title, tp);
        if (r.game != null) top.addView(crest(c, r.game.awayLogo != null ? r.game.awayLogo : Espn.crest(r.game.awayId)));
        mid.addView(top);
        TextView under = Ui.text(c, r.under, 12, Ui.G_TIME, false);
        under.setSingleLine(true);
        under.setEllipsize(TextUtils.TruncateAt.END);
        under.setPadding(0, Ui.dp(c, 3), 0, 0);
        mid.addView(under);
        v.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));

        if (r.mine) {
            TextView tag = Ui.text(c, c.getString(R.string.f1_your_club), 11, Ui.NAVY_INK, true);
            tag.setBackground(Ui.round(c, Ui.STAR, 6));
            tag.setPadding(Ui.dp(c, 7), Ui.dp(c, 2), Ui.dp(c, 7), Ui.dp(c, 2));
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, -2);
            gp.leftMargin = Ui.dp(c, 8);
            v.addView(tag, gp);
        }
        v.setContentDescription(r.title + ", " + (r.live ? "live, " + r.minute : r.minute) + ", " + r.under
                + (r.mine ? ", " + c.getString(R.string.f1_your_club) : ""));
        v.setOnClickListener(x -> {
            if (r.ch != null && pick != null) pick.play(r.ch);
        });
        return v;
    }

    private static ImageView crest(Context c, String url) {
        ImageView i = new ImageView(c);
        i.setScaleType(ImageView.ScaleType.FIT_CENTER);
        i.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (url != null) com.gathin.tvhomephone.sports.Ui.load(i, url);
        i.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20)));
        return i;
    }
}
