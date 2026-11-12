package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.Bitmap;

import com.gathin.tvhomephone.Net;
import com.gathin.tvhomephone.sports.Competitions;
import com.gathin.tvhomephone.sports.Espn;
import com.gathin.tvhomephone.sports.F1;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.LiveGames;
import com.gathin.tvhomephone.sports.OnAir;
import com.gathin.tvhomephone.sports.PlayerPhotos;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Everything the pre-match ad-break screen shows (AdBreakView), read off the main thread from what the app already
 * reads: ESPN's summary for the game, the competition's table, each club's games in this competition, today's games,
 * F1's weekend, BBC Sport's club news, the players' photos, the last meeting's video and the fans' posts. A part that
 * can't be read is left empty, and its widget is not shown: nothing is made up.
 */
final class AdBreakData {
    private AdBreakData() {}

    static final class Content {
        Game game;
        Espn.Summary summary;
        /** The competition's logo (its white one for the navy band where ESPN has one). */
        String compLogo = "", compLogoOnNavy = "";
        String homeForm = "", awayForm = "";
        /** The two clubs' rows in the table (Espn.table's), the rows around each, and where they would be (AdBreak.ifFinished). */
        String[] homeRow, awayRow;
        List<String[]> homeAround = new ArrayList<>(), awayAround = new ArrayList<>();
        int[][] ifFinished;
        /** Each club's next three in this competition: {opponent id, name, "H"/"A", date, opponent's place or ""}. */
        List<String[]> homeNext = new ArrayList<>(), awayNext = new ArrayList<>();
        /** Players to watch: {"home"/"away", name, what (ESPN's words), photo (Bitmap or null)}. */
        List<Object[]> watch = new ArrayList<>();
        /** Games: kicking off with this one, on now elsewhere, later today. */
        List<Game> alsoNow = new ArrayList<>(), elsewhere = new ArrayList<>(), later = new ArrayList<>();
        /** The playlist channel for each game kicking off with this one (null where none), in alsoNow's order. */
        List<Channel> alsoChannels = new ArrayList<>();
        /** F1 today: {session, "HH:mm"}. */
        List<String[]> f1Today = new ArrayList<>();
        /** The clubs' news (BBC Sport): {"home"/"away", headline}. */
        List<String[]> headlines = new ArrayList<>();
        /** The last meeting: Summary.meetings' row, and its video {mp4, still, headline} or null. */
        String[] lastMeeting;
        String[] clip;
        /** The elevens, when they are out (the line-up card). */
        BoardLineups.Data lineups;
        List<FanPosts.Post> posts = new ArrayList<>();
    }

    /** All of it, for this game. Off the main thread. */
    static Content load(Context c, Game g) {
        Content k = new Content();
        k.game = g;
        if (g == null || g.slug == null) return k;
        String slug = g.slug;
        k.compLogo = safe(Competitions.logo(g));
        k.compLogoOnNavy = k.compLogo.contains("/500/") ? k.compLogo.replace("/500/", "/500-dark/") : k.compLogo;
        Espn.Summary sum = Espn.summary(slug, g.id, 60_000L);
        k.summary = sum;
        // Form: this competition only, from each club's own games in it, oldest first.
        List<String[]> hs = Espn.schedule(slug, g.homeId, false), as = Espn.schedule(slug, g.awayId, false);
        k.homeForm = AdBreak.form(hs, g.homeId, 5);
        k.awayForm = AdBreak.form(as, g.awayId, 5);
        // The table: each club's row, the rows round it, where they would be if it finished now.
        List<String[]> table = Espn.table(slug);
        List<String[]> grp = AdBreak.group(table, g.homeId);
        for (String[] r : grp) {
            if (r[AdBreak.T_ID].equals(g.homeId)) k.homeRow = r;
            if (r[AdBreak.T_ID].equals(g.awayId)) k.awayRow = r;
        }
        if (k.homeRow != null) k.homeAround = AdBreak.around(grp, g.homeId, 4);
        if (k.awayRow != null) k.awayAround = AdBreak.around(grp, g.awayId, 4);
        if (k.homeRow != null && k.awayRow != null) k.ifFinished = AdBreak.ifFinished(grp, g.homeId, g.awayId);
        // Coming up: each club's next three in this competition (after this one), with the opponent's place.
        long now = System.currentTimeMillis();
        for (int side = 0; side < 2; side++) {
            String id = side == 0 ? g.homeId : g.awayId;
            List<String[]> next = new ArrayList<>();
            for (String[] f : AdBreak.fixtures(Espn.schedule(slug, id, true), id, now, 4)) {
                if (g.awayId.equals(f[0]) && side == 0 || g.homeId.equals(f[0]) && side == 1) continue;   // this game
                String place = "";
                for (String[] r : table) if (r[AdBreak.T_ID].equals(f[0])) place = r[AdBreak.T_RANK];
                next.add(new String[]{f[0], f[1], f[2], f[3], place});
                if (next.size() >= 3) break;
            }
            if (side == 0) k.homeNext = next;
            else k.awayNext = next;
        }
        // Players to watch: each side's leading scorer in the competition (ESPN's leaders), with his photo.
        for (String side : new String[]{"home", "away"}) {
            for (String[] l : sum.leaders) {
                if (!l[0].equals(side) || !l[1].toLowerCase(Locale.ROOT).contains("goal")) continue;
                String club = "home".equals(side) ? g.homeName : g.awayName;
                Bitmap photo = null;
                try {
                    photo = PlayerPhotos.get(c, l[2], club, null);
                } catch (RuntimeException ignored) {
                    // the figure instead
                }
                k.watch.add(new Object[]{side, l[2], l[3], photo});
                break;
            }
        }
        // Today's other games: kicking off with this one, on now, later on.
        try {
            for (Game x : OnAir.today(60_000L)) {
                if (x.id == null || x.id.equals(g.id)) continue;
                if (x.state == Game.LIVE) k.elsewhere.add(x);
                else if (x.state == Game.PRE && Math.abs(x.kickoff - g.kickoff) < 60_000L) k.alsoNow.add(x);
                else if (x.state == Game.PRE && x.kickoff > g.kickoff && sameDay(x.kickoff, g.kickoff)) k.later.add(x);
            }
            k.later.sort((a, b) -> Long.compare(a.kickoff, b.kickoff));
            while (k.alsoNow.size() > 3) k.alsoNow.remove(k.alsoNow.size() - 1);
            while (k.elsewhere.size() > 3) k.elsewhere.remove(k.elsewhere.size() - 1);
            while (k.later.size() > 3) k.later.remove(k.later.size() - 1);
            for (Game x : k.alsoNow) {
                Channel ch = null;
                try {
                    ch = AlsoShowing.best(c, x);
                } catch (RuntimeException ignored) {
                    // not in the playlist
                }
                k.alsoChannels.add(ch);
            }
        } catch (RuntimeException ignored) {
            // none this time
        }
        try {
            F1.Weekend w = F1.next();
            if (w != null) {
                for (Object[] s : w.sessions) {
                    long at = (Long) s[1];
                    if (at > now && sameDay(at, now)) k.f1Today.add(new String[]{(String) s[0], hm(at)});
                }
                if (w.race > now && sameDay(w.race, now)) k.f1Today.add(new String[]{"Race", hm(w.race)});
            }
        } catch (RuntimeException ignored) {
            // no F1 today
        }
        // The clubs' news: BBC Sport's feed for each club.
        for (int side = 0; side < 2; side++) {
            String full = side == 0 ? g.homeFull : g.awayFull;
            String feed = bbcFeed(full != null && !full.isEmpty() ? full : side == 0 ? g.homeName : g.awayName);
            if (feed == null) continue;
            try {
                List<BreakingNews.Story> st = BreakingNews.parse(Net.get(feed));
                for (int i = 0; i < Math.min(2, st.size()); i++) k.headlines.add(new String[]{side == 0 ? "home" : "away", st.get(i).title});
            } catch (Exception ignored) {
                // that club's news another time
            }
        }
        // The last meeting, and its video where ESPN has one.
        if (!sum.meetings.isEmpty()) {
            k.lastMeeting = sum.meetings.get(0);
            if (k.lastMeeting.length > 6 && !k.lastMeeting[6].isEmpty()) k.clip = Espn.video(slug, k.lastMeeting[6]);
        }
        // The elevens, when they are out.
        if (!sum.homeLineup.isEmpty() && !sum.awayLineup.isEmpty()) {
            try {
                int px = Math.round(28 * c.getResources().getDisplayMetrics().density * 2);
                k.lineups = ScoreBoard.lineupsData(c, g, sum, px);
            } catch (RuntimeException ignored) {
                // no line-up card
            }
        }
        k.posts = FanPosts.load(g, 12);
        return k;
    }

    /** BBC Sport's feed for a club ("Manchester United" → manchester-united), or null without a name. */
    static String bbcFeed(String club) {
        if (club == null || club.trim().isEmpty()) return null;
        String s = club.toLowerCase(Locale.ROOT).replace("&", "and").replace("afc ", "").replace(" fc", "").trim();
        s = s.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return "https://feeds.bbci.co.uk/sport/football/teams/" + s + "/rss.xml";
    }

    private static boolean sameDay(long a, long b) {
        ZoneId z = ZoneId.systemDefault();
        LocalDate x = Instant.ofEpochMilli(a).atZone(z).toLocalDate(), y = Instant.ofEpochMilli(b).atZone(z).toLocalDate();
        return x.equals(y);
    }

    static String hm(long at) {
        return java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.UK).format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** The crests' addresses for a game's sides. */
    static String homeCrest(Game g) {
        return safe(LiveGames.homeCrest(g));
    }

    static String awayCrest(Game g) {
        return safe(LiveGames.awayCrest(g));
    }
}
