package com.gathin.tvhome;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Control Centre's Follow players: the players followed, each with a switch (off: no longer followed once the card
 * closes) and their club's next game; Follow a player lists the starred clubs, and a club its squad, each player with a
 * switch. Back goes back a page, then closes. Clubs and squads are read off the main thread (SportsData).
 */
final class PlayersPanel extends SportPanel {
    private int page;
    /** The starred clubs (as read), the club opened, and its squad. */
    private final List<SportsData.Team> clubs = new ArrayList<>();
    private SportsData.Team club;
    private final List<SportsData.Player> squad = new ArrayList<>();
    /** Each followed player's club, read for its next game. */
    private final Map<String, SportsData.Team> teams = new HashMap<>();
    /** The players followed as the card opened: those switched off stay listed until it closes. */
    private final List<Players.Followed> shown = new ArrayList<>();
    private boolean loading;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, H:mm", Locale.UK);

    PlayersPanel(HomeV2 home) {
        super(home);
    }

    @Override protected int[] ground() {
        return new int[]{0xFF2A2F7A, 0xFF181C55, 0xFF0D1030};
    }

    @Override protected void opened() {
        page = 0;
        shown.clear();
        shown.addAll(Players.list(c));
        readTeams();
    }

    @Override protected boolean back() {
        if (page == 0) return false;
        page--;
        line = 0;
        col = 0;
        redraw();
        return true;
    }

    /** States line for the Control Centre's tile. */
    static String describe(android.content.Context c) {
        int n = Players.list(c).size();
        return n == 0 ? "Goals from any player you choose" : n == 1 ? "1 player" : n + " players";
    }

    private void readTeams() {
        List<String> ids = new ArrayList<>();
        for (Players.Followed f : shown) if (!f.teamId.isEmpty() && !ids.contains(f.teamId)) ids.add(f.teamId);
        if (ids.isEmpty()) return;
        h.io.execute(() -> {
            Map<String, SportsData.Team> got = new HashMap<>();
            for (String id : ids) {
                SportsData.Team t = SportsData.team(c, id);
                if (t != null) got.put(id, t);
            }
            h.root.post(() -> {
                teams.putAll(got);
                if (open && page == 0) redraw();
            });
        });
    }

    @Override protected void build() {
        if (page == 0) followed();
        else if (page == 1) clubs();
        else squad();
    }

    private void followed() {
        title("Players you follow", "OK to switch  ·  Back to close");
        Row add = row("Follow a player", false);
        add.words = () -> "From your clubs' squads";
        add.ok = () -> {
            page = 1;
            line = 0;
            col = 0;
            readClubs();
            redraw();
        };
        add(add);
        if (shown.isEmpty()) {
            words("Their goals pop up wherever they play, in any game the TV follows the scores of, the card saying it's a"
                    + " player you follow.", 18, 0xCCFFFFFF);
            return;
        }
        heading("FOLLOWING");
        List<Row> rows = new ArrayList<>();
        for (Players.Followed f : shown) {
            Row r = row(f.name, true);
            r.on = () -> Players.follows(c, f.id);
            r.words = () -> next(f);
            r.ok = () -> Players.unfollow(c, f.id, !Players.follows(c, f.id), f);
            rows.add(r);
        }
        addAll(rows);
    }

    private String next(Players.Followed f) {
        SportsData.Team t = teams.get(f.teamId);
        if (t == null || t.nextAt <= 0) return f.team;
        return f.team + " · next " + WHEN.format(Instant.ofEpochMilli(t.nextAt).atZone(ZoneId.systemDefault()))
                + (t.nextName.isEmpty() ? "" : " · " + t.nextName);
    }

    private void readClubs() {
        if (!clubs.isEmpty() || loading) return;
        loading = true;
        String[] ids = SyncHub.starredClubs(c);
        h.io.execute(() -> {
            List<SportsData.Team> got = new ArrayList<>();
            for (String id : ids) {
                SportsData.Team t = SportsData.team(c, id);
                if (t != null && !t.league.isEmpty()) got.add(t);
            }
            h.root.post(() -> {
                loading = false;
                clubs.clear();
                clubs.addAll(got);
                if (open && page == 1) redraw();
            });
        });
    }

    private void clubs() {
        title("Follow a player", "OK to open a squad  ·  Back to go back");
        if (clubs.isEmpty()) {
            words(loading ? "Reading your clubs…" : "Your clubs couldn't be read: try again in a moment.", 20, 0xCCFFFFFF);
            return;
        }
        heading("YOUR CLUBS");
        List<Row> rows = new ArrayList<>();
        for (SportsData.Team t : clubs) {
            Row r = row(t.name, false);
            r.words = () -> t.standing.isEmpty() ? "Its squad" : t.standing;
            r.ok = () -> openClub(t);
            rows.add(r);
        }
        addAll(rows);
    }

    private void openClub(SportsData.Team t) {
        club = t;
        squad.clear();
        page = 2;
        line = 0;
        col = 0;
        loading = true;
        redraw();
        h.io.execute(() -> {
            List<SportsData.Player> got = SportsData.squad(c, t.league, t.id);
            got.sort((a, b) -> Integer.compare(order(a.position), order(b.position)));
            h.root.post(() -> {
                loading = false;
                if (club != t) return;
                squad.addAll(got);
                if (open && page == 2) redraw();
            });
        });
    }

    /** Pure: goalkeepers first, then defenders, midfielders and forwards. */
    static int order(String position) {
        if (position == null || position.isEmpty()) return 9;
        switch (position.charAt(0)) {
            case 'G': return 0;
            case 'D': return 1;
            case 'M': return 2;
            case 'F': return 3;
            default: return 4;
        }
    }

    private void squad() {
        title(club == null ? "Squad" : club.name, "OK to follow  ·  Back to go back");
        if (squad.isEmpty()) {
            words(loading ? "Reading the squad…" : "The squad couldn't be read: try again in a moment.", 20, 0xCCFFFFFF);
            return;
        }
        List<Row> rows = new ArrayList<>();
        for (SportsData.Player p : squad) {
            Row r = row((p.number.isEmpty() ? "" : p.number + "  ·  ") + p.name, true);
            r.on = () -> Players.follows(c, p.id);
            r.words = () -> position(p.position);
            r.ok = () -> Players.set(c, p, club, !Players.follows(c, p.id));
            rows.add(r);
        }
        addAll(rows);
    }

    /** Pure: a position in words ("G" a goalkeeper). */
    static String position(String p) {
        if (p == null || p.isEmpty()) return "";
        switch (p.charAt(0)) {
            case 'G': return "Goalkeeper";
            case 'D': return "Defender";
            case 'M': return "Midfielder";
            case 'F': return "Forward";
            default: return p;
        }
    }
}
