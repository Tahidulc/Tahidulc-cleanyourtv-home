package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Bitmap;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The scoreboard over the picture's reads (ScoreOverlay, the scoreboard over other apps): one game read again, the
 * followed clubs' games on now, and crests and logos as pictures. Off the main thread.
 */
public final class LiveGames {
    private LiveGames() {}

    /** How old a scoreboard read may be for the overlay: it reads every 15 seconds. */
    private static final long FRESH_MS = 10_000L;

    /** The game read again from its competition's scoreboard (its kick-off's day, then today); null when not found. */
    public static Game again(Game g) {
        if (g == null || g.id == null || g.slug == null) return null;
        LocalDate day = g.kickoff > 0 ? Instant.ofEpochMilli(g.kickoff).atZone(ZoneId.systemDefault()).toLocalDate() : LocalDate.now();
        for (LocalDate d : new LocalDate[]{day, LocalDate.now()}) {
            for (Game x : Espn.scoreboard(g.slug, d, FRESH_MS)) if (g.id.equals(x.id)) return x;
            if (d.equals(LocalDate.now())) break;
        }
        return null;
    }

    /** The starred clubs' games on now, in the competitions switched on; the first is the one to show. */
    public static List<Game> followed(Context c) {
        List<Game> out = new ArrayList<>();
        Set<String> teams = Clubs.starred(c);
        if (teams.isEmpty()) return out;
        for (String slug : Watcher.leagues(c, teams)) {
            if (!Competitions.on(c, slug)) continue;
            for (Game g : Espn.scoreboard(slug, LocalDate.now(), FRESH_MS)) {
                if (g.state == Game.LIVE && g.involves(teams)) out.add(g);
            }
        }
        return out;
    }

    /** A crest or logo as a picture px square (cached); null when it can't be had. */
    public static Bitmap picture(Context c, String url, int px) {
        return NotifCard.logo(c, url, px, px);
    }

    /** A game's crests' addresses: ESPN's own when the scoreboard gave none. */
    public static String homeCrest(Game g) {
        return g.homeLogo != null ? g.homeLogo : Espn.crest(g.homeId);
    }

    public static String awayCrest(Game g) {
        return g.awayLogo != null ? g.awayLogo : Espn.crest(g.awayId);
    }
}
