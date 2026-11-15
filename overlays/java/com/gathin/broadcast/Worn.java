package com.gathin.broadcast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The kits the two sides wear in a game, the same on the TV and the phone. ESPN's line-ups name each side's kit for the
 * game ("uniform": home, away, third or fourth) with its colour and second colour, from when the line-ups are out (about
 * an hour before kick-off): Wales in their gold third kit in Oslo, Lazio in black at Napoli, Aston Villa in light blue at
 * Old Trafford. The scoreboards, their pop-outs, the shot map and the line-ups are coloured by them, not by the clubs'
 * own colours (ESPN's team colour, the same whichever kit is worn).
 *
 * <p>Until the line-ups are out, or where ESPN names no kit, the sides keep their clubs' colours, but for a clash: when
 * the away side's colour is too near the home side's, it takes its second colour (or a plain white or navy), as it
 * would change its shirt (pick).
 *
 * <p>Kept a game at a time in memory, the last forty, and as text the app keeps (save, restore), so a TV that restarts
 * mid-game still has them.
 */
public final class Worn {
    private Worn() { }

    /** One side's kit for the game: ESPN's name for it ("home", "away", "third", "fourth"), its colour and its second. */
    public static final class Kit {
        public final String type;
        public final int body, trim;

        public Kit(String type, int body, int trim) {
            this.type = type == null ? "" : type;
            this.body = body;
            this.trim = trim;
        }

        /** "Home kit", "Away kit", "Third kit", "Fourth kit" (or "Kit" for a name ESPN has not used before). */
        public String label() {
            switch (type) {
                case "home": return "Home kit";
                case "away": return "Away kit";
                case "third": return "Third kit";
                case "fourth": return "Fourth kit";
                default: return "Kit";
            }
        }
    }

    /** Pure: ESPN's colour ("e9af3a", "#E9AF3A") as an opaque colour; 0 for none. */
    public static int hex(String s) {
        if (s == null) return 0;
        String h = s.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() != 6) return 0;
        try {
            return 0xFF000000 | Integer.parseInt(h, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Pure: an opaque colour as ESPN writes one ("e9af3a"); "" for 0. */
    public static String hexOf(int c) {
        return c == 0 ? "" : String.format(java.util.Locale.ROOT, "%06x", c & 0xFFFFFF);
    }

    /**
     * Pure: {home, away} from a match summary's rosters (either null when ESPN names no kit for it). A side is the home
     * one by its "homeAway", else by its team's id against homeId, else by its place (the first home).
     */
    public static Kit[] of(JSONArray rosters, String homeId) {
        Kit[] out = new Kit[2];
        for (int i = 0; rosters != null && i < rosters.length(); i++) {
            JSONObject r = rosters.optJSONObject(i);
            if (r == null) continue;
            JSONObject u = r.optJSONObject("uniform");
            if (u == null) continue;
            int body = hex(u.optString("color", ""));
            if (body == 0) continue;
            String side = r.optString("homeAway", "");
            JSONObject team = r.optJSONObject("team");
            boolean home = "home".equals(side) || !"away".equals(side) && (homeId != null && !homeId.isEmpty() && team != null
                    ? homeId.equals(team.optString("id")) : i == 0);
            out[home ? 0 : 1] = new Kit(u.optString("type", ""), body, hex(u.optString("alternateColor", "")));
        }
        return out;
    }

    /** Pure: {home, away} from a match summary (its rosters); both null when it names no kits. */
    public static Kit[] of(JSONObject summary, String homeId) {
        return of(summary == null ? null : summary.optJSONArray("rosters"), homeId);
    }

    // ---- the games' kits --------------------------------------------------------------------------------------------

    private static final int KEEP = 40;
    private static final LinkedHashMap<String, Kit[]> games = new LinkedHashMap<String, Kit[]>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Kit[]> eldest) {
            return size() > KEEP;
        }
    };
    private static boolean changed;

    /** The kits named for a game (a side ESPN no longer names keeps the one it named); true when they changed. */
    public static synchronized boolean note(String gameId, Kit[] kits) {
        if (gameId == null || kits == null || kits.length < 2 || kits[0] == null && kits[1] == null) return false;
        Kit[] was = games.get(gameId);
        Kit h = kits[0] != null ? kits[0] : was == null ? null : was[0], a = kits[1] != null ? kits[1] : was == null ? null : was[1];
        boolean same = was != null && same(was[0], h) && same(was[1], a);
        if (same) return false;
        games.put(gameId, new Kit[]{h, a});
        changed = true;
        return true;
    }

    private static boolean same(Kit x, Kit y) {
        return x == null ? y == null : y != null && x.body == y.body && x.trim == y.trim && x.type.equals(y.type);
    }

    /** {home, away} named for the game, either null; null when none is known. */
    public static synchronized Kit[] get(String gameId) {
        Kit[] k = gameId == null ? null : games.get(gameId);
        return k == null ? null : k.clone();
    }

    /** Whether any game's kits changed since the last save. */
    public static synchronized boolean unsaved() {
        return changed;
    }

    /** Every game's kits as one line of text: "id=type,body,trim/type,body,trim;…" (a side not named left empty). */
    public static synchronized String save() {
        changed = false;
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Kit[]> e : games.entrySet()) {
            if (b.length() > 0) b.append(';');
            b.append(e.getKey().replace(";", "").replace("=", "")).append('=').append(text(e.getValue()[0])).append('/').append(text(e.getValue()[1]));
        }
        return b.toString();
    }

    private static String text(Kit k) {
        return k == null ? "" : k.type.replace(",", "") + "," + hexOf(k.body) + "," + hexOf(k.trim);
    }

    /** What save gave, back again (games already known keep what they have). */
    public static synchronized void restore(String saved) {
        if (saved == null || saved.isEmpty()) return;
        for (String game : saved.split(";")) {
            int eq = game.indexOf('=');
            if (eq <= 0) continue;
            String id = game.substring(0, eq);
            if (games.containsKey(id)) continue;
            String[] sides = game.substring(eq + 1).split("/", -1);
            Kit h = sides.length > 0 ? kit(sides[0]) : null, a = sides.length > 1 ? kit(sides[1]) : null;
            if (h != null || a != null) games.put(id, new Kit[]{h, a});
        }
    }

    private static Kit kit(String s) {
        String[] f = s.split(",", -1);
        if (f.length < 3) return null;
        int body = hex(f[1]);
        return body == 0 ? null : new Kit(f[0], body, hex(f[2]));
    }

    /** Tests only: nothing known. */
    public static synchronized void forget() {
        games.clear();
        changed = false;
    }

    // ---- the colours to show ----------------------------------------------------------------------------------------

    /** How near two colours may be before the away side changes (0 to 441, as the line-ups' kit rule). */
    static final int CLASH = 120;

    /** Pure: how far apart two colours are, straight across red, green and blue (0 to 441). */
    public static double distance(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF), dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF), db = (a & 0xFF) - (b & 0xFF);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    /**
     * Pure: each side's {colour, second colour} for the game, {homeFirst, homeSecond, awayFirst, awaySecond}: the kit it
     * wears when ESPN names one (its second colour, else the club's), else the club's colours; when neither side's kit
     * is named and the away club's colour clashes with the home one's, its second colour (else white, or navy against a
     * light home side). 0 stays 0 (unknown).
     */
    public static int[] pick(Kit[] kits, int homeClub, int homeAlt, int awayClub, int awayAlt) {
        Kit h = kits == null ? null : kits[0], a = kits == null ? null : kits[1];
        int hf = h != null ? h.body : homeClub, hs = h != null ? (h.trim != 0 ? h.trim : homeAlt) : homeAlt;
        int af = a != null ? a.body : awayClub, as = a != null ? (a.trim != 0 ? a.trim : awayAlt) : awayAlt;
        if (a == null && hf != 0 && af != 0 && distance(hf, af) < CLASH) {
            if (awayAlt != 0 && distance(hf, awayAlt) >= CLASH) {
                af = awayAlt;
                as = awayClub;
            } else {
                af = light(hf) ? 0xFF1A2466 : 0xFFF4F5FA;
                as = awayClub;
            }
        }
        return new int[]{hf, hs, af, as};
    }

    private static boolean light(int c) {
        return 0.299 * ((c >> 16) & 0xFF) + 0.587 * ((c >> 8) & 0xFF) + 0.114 * (c & 0xFF) > 150;
    }
}
