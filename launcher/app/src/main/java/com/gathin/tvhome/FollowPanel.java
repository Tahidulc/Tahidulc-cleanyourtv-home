package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The Control Centre's Teams & competitions, as the phone's Settings has them (Following): Scoreboard over matches at
 * the top (Automatic brings the scoreboard up by itself), the starred clubs with their crests, the phone's and the
 * other TV's list too, each with a switch (switched off, they stay listed until the card closes), and a switch for
 * each competition with its logo. Add a club opens the leagues (and the national sides), a league its clubs A to Z,
 * each with a switch. Back goes back a page, then closes. Clubs, crests and logos are read off the main thread.
 */
final class FollowPanel extends SportPanel {
    private int page;
    /** The clubs on the first page: the starred ones as the card opened, and those starred since. */
    private final List<String> shownClubs = new ArrayList<>();
    /** The league opened from Add a club ({slug, name}), and its clubs. */
    private String[] league;
    private final List<SportsData.Team> clubs = new ArrayList<>();
    private boolean loading;
    /** The starred clubs' names have been looked for since the card opened (those still unknown say their id). */
    private boolean namesRead;
    /** The pictures read while the card is open, by crest address or "comp:" and the slug, and the rows waiting for one. */
    private final Map<String, Bitmap> pics = new HashMap<>();
    private final Map<String, List<Row>> waiting = new HashMap<>();

    FollowPanel(HomeV2 home) {
        super(home);
    }

    @Override protected int[] ground() {
        return new int[]{0xFF1C3C8C, 0xFF12265E, 0xFF0A1334};
    }

    @Override protected void opened() {
        page = 0;
        shownClubs.clear();
        shownClubs.addAll(Arrays.asList(Following.clubs(c)));
        readNames();
    }

    @Override void hide() {
        super.hide();
        pics.clear();
        waiting.clear();
    }

    @Override protected boolean back() {
        if (page == 0) return false;
        page--;
        line = 0;
        col = 0;
        redraw();
        return true;
    }

    /** The Control Centre tile's line. */
    static String describe(Context c) {
        return Following.describe(c);
    }

    @Override protected void build() {
        if (page == 0) following();
        else if (page == 1) leagues();
        else leagueClubs();
    }

    // ---- the first page: Scoreboard over matches, the clubs, the competitions ------------------------------------------

    private void following() {
        title("Teams & competitions", "OK to switch  ·  Back to close");
        // What a match of theirs being watched gets: the scoreboard by itself, the hold-OK button, or neither.
        Row matches = row(c.getString(R.string.board_over_matches), true);
        matches.on = () -> OverlayPrompt.on(c);
        matches.words = () -> {
            String m = OverlayPrompt.mode(c);
            return c.getString(OverlayPrompt.AUTO.equals(m) ? R.string.board_over_matches_auto
                    : OverlayPrompt.OFF.equals(m) ? R.string.board_over_matches_off : R.string.board_over_matches_ask);
        };
        matches.ok = () -> OverlayPrompt.setMode(c, OverlayPrompt.next(OverlayPrompt.mode(c)));
        Row add = row("Add a club", false);
        add.words = () -> "By league, or a national side";
        add.ok = () -> {
            page = 1;
            line = 0;
            col = 0;
            redraw();
        };
        add(matches, add);
        heading("YOUR CLUBS  ·  ON YOUR PHONE TOO");
        if (shownClubs.isEmpty()) {
            words("No clubs starred yet. Add a club: every game of theirs is followed, wherever they play.", 18, 0xCCFFFFFF);
        } else {
            List<Row> rows = new ArrayList<>();
            for (String id : shownClubs) {
                String[] k = Following.known(c, id);
                Row r = row(clubName(id, k), true, "⚽");
                r.on = () -> Following.starred(c, id);
                r.words = () -> Following.starred(c, id) ? Following.clubWords(c, id) : "Not starred  ·  OK to star again";
                r.ok = () -> Following.star(c, id, !Following.starred(c, id));
                rows.add(r);
                crest(r, k != null && !k[1].isEmpty() ? k[1] : Following.crestUrl(id));
            }
            addAll(rows);
        }
        heading("COMPETITIONS  ·  EVERY GAME");
        List<Row> rows = new ArrayList<>();
        for (String[] x : Following.COMPETITIONS) {
            Row r = row(x[2], true, Following.international(x[0]) ? "🌍" : "🏆");
            r.on = () -> Following.on(c, x[0]);
            r.words = () -> Following.words(c, x[0]);
            r.ok = () -> Following.set(c, x[0], !Following.on(c, x[0]));
            rows.add(r);
            logo(r, x[0]);
        }
        addAll(rows);
        words("A game of a club or competition switched on here gets the scoreboard when it's on the channel: by itself"
                + " on Automatic, else from the button or the Scoreboard tile.", 17, 0x99FFFFFF);
    }

    /** A starred club's name: as known, else as today's scores have it, else (looked for already) its id. */
    private String clubName(String id, String[] known) {
        if (known != null && !known[0].isEmpty()) return known[0];
        for (ScoresTicker.Game g : GoalWatchService.latestGames()) {
            if (id.equals(g.homeId) && g.homeName != null) return g.homeName;
            if (id.equals(g.awayId) && g.awayName != null) return g.awayName;
        }
        return namesRead ? "Club " + id : "Reading its name…";
    }

    /** The starred clubs nothing is known of yet (starred on the phone): their names, crests and leagues read. */
    private void readNames() {
        List<String> need = new ArrayList<>();
        for (String id : shownClubs) {
            String[] k = Following.known(c, id);
            if (k == null || k[0].isEmpty() || k[2].isEmpty()) need.add(id);
        }
        namesRead = need.isEmpty();
        if (need.isEmpty()) return;
        h.io.execute(() -> {
            for (String id : need) {
                SportsData.Team t = SportsData.team(c, id);
                if (t != null && !t.name.isEmpty()) Following.know(c, id, t.name, t.logo, t.league);
            }
            h.root.post(() -> {
                namesRead = true;
                if (open && page == 0) redraw();
            });
        });
    }

    // ---- Add a club: the leagues, then a league's clubs -------------------------------------------------------------

    private void leagues() {
        title("Add a club", "OK to open  ·  Back to go back");
        List<Row> rows = new ArrayList<>();
        for (String[] l : Following.LEAGUES) {
            boolean sides = Following.international(l[0]);
            Row r = row(l[1], false, sides ? "🌍" : "🏆");
            r.words = () -> sides ? "The World Cup's sides" : "Its clubs, A to Z";
            r.ok = () -> openLeague(l);
            rows.add(r);
            logo(r, l[0]);
        }
        addAll(rows);
    }

    private void openLeague(String[] l) {
        league = l;
        clubs.clear();
        page = 2;
        line = 0;
        col = 0;
        loading = true;
        redraw();
        h.io.execute(() -> {
            List<SportsData.Team> got = SportsData.clubs(c, l[0]);
            h.root.post(() -> {
                if (league != l) return;
                loading = false;
                clubs.clear();
                clubs.addAll(got);
                if (open && page == 2) redraw();
            });
        });
    }

    private void leagueClubs() {
        title(league == null ? "Clubs" : league[1], "OK to star  ·  Back to go back");
        if (clubs.isEmpty()) {
            words(loading ? "Reading its clubs…" : "Its clubs couldn't be read: try again in a moment.", 20, 0xCCFFFFFF);
            return;
        }
        String[] l = league;
        List<Row> rows = new ArrayList<>();
        for (SportsData.Team t : clubs) {
            Row r = row(t.name, true, "⚽");
            r.on = () -> Following.starred(c, t.id);
            r.words = () -> Following.starred(c, t.id) ? "Starred: every game of theirs followed" : "OK to star";
            r.ok = () -> {
                boolean on = !Following.starred(c, t.id);
                Following.know(c, t.id, t.name, t.logo, l == null ? "" : l[0]);
                Following.star(c, t.id, on);
                if (on && !shownClubs.contains(t.id)) shownClubs.add(t.id);
            };
            rows.add(r);
            crest(r, t.logo);
        }
        addAll(rows);
    }

    // ---- the pictures -----------------------------------------------------------------------------------------------

    private void crest(Row r, String url) {
        if (url == null || url.isEmpty()) return;
        int px = h.px(54);
        picture(r, url, () -> ScoreCard.onNavy(fit(ScoresTicker.cachedLogo(c, url, px), px * 2)));
    }

    private void logo(Row r, String slug) {
        int px = h.px(54);
        picture(r, "comp:" + slug, () -> fit(Following.logo(c, slug, px), px * 2));
    }

    /** A picture no bigger than size across or down (the cache keeps them large), so a long list stays light. */
    private static Bitmap fit(Bitmap b, int size) {
        if (b == null || b.getWidth() <= size && b.getHeight() <= size) return b;
        float k = size / (float) Math.max(b.getWidth(), b.getHeight());
        return Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * k)), Math.max(1, Math.round(b.getHeight() * k)), true);
    }

    /** The picture for key in the row's place: at once when it has been read, else read off the main thread first. */
    private void picture(Row r, String key, Supplier<Bitmap> read) {
        Bitmap have = pics.get(key);
        if (have != null) {
            picture(r, have);
            return;
        }
        List<Row> w = waiting.get(key);
        if (w != null) {
            w.add(r);
            return;
        }
        w = new ArrayList<>();
        w.add(r);
        waiting.put(key, w);
        h.io.execute(() -> {
            Bitmap b;
            try {
                b = read.get();
            } catch (RuntimeException e) {
                b = null;   // its mark stays
            }
            Bitmap got = b;
            h.root.post(() -> {
                List<Row> rows = waiting.remove(key);
                if (got == null || rows == null || !open) return;
                pics.put(key, got);
                for (Row x : rows) if (x.view.isAttachedToWindow()) picture(x, got);
            });
        });
    }
}
