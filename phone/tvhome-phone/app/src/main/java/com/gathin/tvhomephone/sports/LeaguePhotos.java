package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.util.Base64;

import com.gathin.broadcast.LaLigaPhotos;
import com.gathin.broadcast.PlPhotos;
import com.gathin.tvhomephone.App;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The leagues' own pictures of their players for the club page's squad and the player's page, as the line-ups take
 * them: the Premier League's (head to waist in the club's kit, see-through round him: PlPhotos, its fantasy game's list
 * matched by club and name) for a Premier League club, La Liga's (LaLigaPhotos: from his page on laliga.com, framed as
 * the Premier League's) for a La Liga club. Looked up once a player, as his card first asks for its picture, off the
 * main thread; the answer kept on the player ("" for none) and La Liga's picture kept as a file.
 */
final class LeaguePhotos {
    private LeaguePhotos() {}

    static final int NONE = 0, PREMIER = 1, LA_LIGA = 2;
    /** Where La Liga's pictures are kept for the squad (their addresses carry it, so SquadFilter knows their kind). */
    static final String LA_LIGA_DIR = "laliga-squad";

    /** Pure: whose own pictures a club's players have, by its competition (ESPN's slug). */
    static int league(String slug) {
        if ("eng.1".equals(slug)) return PREMIER;
        if (LaLigaPhotos.isLaLiga(slug, null, null)) return LA_LIGA;
        return NONE;
    }

    /** Whether a player's league picture is still to be looked up. */
    static boolean pending(String slug, TeamData.Player p) {
        return p != null && league(slug) != NONE && p.league == null;
    }

    /** Those waiting on a player's lookup, by the player (one lookup however many cards ask). */
    private static final Map<TeamData.Player, List<Runnable>> ASKING = new IdentityHashMap<>();
    /** La Liga's pages a club may read in a session (LaLigaPhotos.Budget), by club. */
    private static final Map<String, LaLigaPhotos.Budget> BUDGETS = new HashMap<>();

    /** Looks the player's league picture up (off the main thread); then() on the main thread once it is known. */
    static void lookUp(Context c, String slug, String club, String shortName, TeamData.Player p, Runnable then) {
        int league = league(slug);
        if (league == NONE || p.league != null) {
            then.run();
            return;
        }
        // Previews only: the pictures are given, nothing is fetched.
        if (!Cutout.GIVEN.isEmpty()) {
            p.league = "";
            then.run();
            return;
        }
        synchronized (ASKING) {
            List<Runnable> waiting = ASKING.get(p);
            if (waiting != null) {
                waiting.add(then);
                return;
            }
            waiting = new ArrayList<>();
            waiting.add(then);
            ASKING.put(p, waiting);
        }
        File cache = c.getApplicationContext().getCacheDir();
        App.io.execute(() -> {
            String got = null;
            try {
                if (league == PREMIER) {
                    got = PlPhotos.url(cache, club, p.name, p.last);
                    if (got == null && shortName != null && !shortName.isEmpty() && !shortName.equals(club)) {
                        got = PlPhotos.url(cache, shortName, p.name, p.last);
                    }
                } else {
                    got = file(cache, LaLigaPhotos.photo(p.name, p.last, new String[]{club, shortName}, cache, budget(club)), p);
                }
            } catch (RuntimeException e) {
                got = null;
            }
            p.league = got == null ? "" : got;
            List<Runnable> done;
            synchronized (ASKING) {
                done = ASKING.remove(p);
            }
            if (done != null) App.main.post(() -> {
                for (Runnable r : done) r.run();
            });
        });
    }

    private static LaLigaPhotos.Budget budget(String club) {
        synchronized (BUDGETS) {
            LaLigaPhotos.Budget b = BUDGETS.get(club);
            if (b == null) {
                b = new LaLigaPhotos.Budget();
                BUDGETS.put(club, b);
            }
            return b;
        }
    }

    /** La Liga's picture (a data: address) kept as a file, its address; null when there is none. */
    private static String file(File cache, String data, TeamData.Player p) {
        if (data == null) return null;
        int comma = data.indexOf(',');
        if (!data.startsWith("data:") || comma < 0) return null;
        try {
            File dir = new File(cache, LA_LIGA_DIR);
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            String name = Integer.toHexString((p.name + "|" + p.last + "|" + data.length()).hashCode()) + ".webp";
            File f = new File(dir, name);
            if (!f.isFile()) {
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(Base64.decode(data.substring(comma + 1), Base64.DEFAULT));
                }
            }
            return android.net.Uri.fromFile(f).toString();
        } catch (Exception e) {
            return null;
        }
    }
}
