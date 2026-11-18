package com.gathin.homefieldtv.sports;

import android.content.Context;
import android.util.Base64;

import com.gathin.broadcast.LaLigaPhotos;
import com.gathin.broadcast.PlPhotos;
import com.gathin.homefieldtv.engine.Exec;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The leagues' own pictures of their players for the club page's squad and the player's page, as the phone takes them
 * (its LeaguePhotos, phone 5.29), so the TV and the phone pick the same picture: the Premier League's (head to waist in
 * the club's kit, see-through round him: PlPhotos, its fantasy game's list matched by club and name) for a Premier
 * League club, La Liga's (LaLigaPhotos: from his page on laliga.com, framed as the Premier League's) for a La Liga
 * club. The club's names asked with are the phone's: ESPN's full name, then its short one. Looked up once a player, as
 * his card first asks for its picture, off the main thread; the answer kept on the player ("" for none) and La Liga's
 * picture kept as a file in the cache. In tests and previews (Exec.inline) nothing is fetched: GIVEN's pictures, else none.
 */
public final class LeaguePhotos {
    private LeaguePhotos() {}

    public static final int NONE = 0, PREMIER = 1, LA_LIGA = 2;
    /** FotMob's ids for the two leagues (a club met in another competition: its main league, Squad.fotmobLeague). */
    static final int FOTMOB_PREMIER = 47, FOTMOB_LA_LIGA = 87;
    /** Where La Liga's pictures are kept for the squad (their addresses carry it, so Squad.kind knows their kind). */
    public static final String LA_LIGA_DIR = "laliga-squad";

    /** Previews only: a player's league picture by his name (nothing is fetched while Exec.inline). Empty in the app. */
    public static final Map<String, String> GIVEN = new HashMap<>();

    /**
     * Pure: whose own pictures a club's players have: by the competition it was met in (ESPN's slug, as the phone), or
     * by FotMob's main league for it (a Premier League club opened from a Champions League game).
     */
    public static int league(String slug, int fotmobLeague) {
        if ("eng.1".equals(slug) || fotmobLeague == FOTMOB_PREMIER) return PREMIER;
        if (LaLigaPhotos.isLaLiga(slug, null, null) || fotmobLeague == FOTMOB_LA_LIGA) return LA_LIGA;
        return NONE;
    }

    /** Whether a player's league picture is still to be looked up. */
    public static boolean pending(int league, Squad.Person p) {
        return p != null && league != NONE && p.line != Squad.STAFF && p.league == null;
    }

    /** Those waiting on a player's lookup, by the player (one lookup however many cards ask). */
    private static final Map<Squad.Person, List<Runnable>> ASKING = new IdentityHashMap<>();
    /** La Liga's pages a club may read in a session (LaLigaPhotos.Budget), by club. */
    private static final Map<String, LaLigaPhotos.Budget> BUDGETS = new HashMap<>();

    /**
     * Looks the player's league picture up (off the main thread); then() on the main thread once it is known. club and
     * shortName: the club's full and short names (ESPN's displayName and shortDisplayName, as the phone asks with).
     */
    public static void lookUp(Context c, int league, String club, String shortName, Squad.Person p, Runnable then) {
        if (league == NONE || p.league != null || p.line == Squad.STAFF) {
            then.run();
            return;
        }
        if (Exec.inline) {
            String given = GIVEN.get(p.name);
            p.league = given == null ? "" : given;
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
        Exec.io(() -> {
            String got;
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
            if (done != null) Exec.main(() -> {
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

    /** La Liga's picture (a data: address) kept as a file in the cache, its address for Pics ("file:"); null when none. */
    private static String file(File cache, String data, Squad.Person p) {
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
            return "file:" + f.getPath();
        } catch (Exception e) {
            return null;
        }
    }
}
