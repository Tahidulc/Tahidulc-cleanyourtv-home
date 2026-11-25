package com.gathin.broadcast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The public schedule behind Sky's UK TV guide, read the same way by Homefield TV (SkyGuide) and the phone app (SkyPics)
 * for programme pictures, pure: Sky's short channel names spelt out, its list of channels as name → number (the family's
 * region first, then HD), the programme on at a time (its title preferred) and its picture's address. Only the channel's
 * number and the day are asked for: no key, nothing about the family. The apps fetch; nothing here does.
 */
public final class SkyListing {
    private SkyListing() {}

    public static final String SERVICES = "https://awk.epgsky.com/hawk/linear/services/4101/1";
    public static final String SCHEDULE = "https://awk.epgsky.com/hawk/linear/schedule/%s/%s";
    /** A programme's picture at a width (640 for the TV's cells and cards; 1280 for a phone's whole panel). */
    public static final String PICTURE = "https://images.metadata.sky.com/pd-image/%s/16-9/%d";

    /** The picture's address for a programme's id at a width. */
    public static String picture(String uuid, int width) {
        return uuid == null || uuid.isEmpty() ? "" : String.format(Locale.ROOT, PICTURE, uuid, width);
    }

    /** Sky's short names spelt out ("SkySpMainEvHD" → "Sky Sports Main Event HD", "U&Dave" → "Dave"). */
    public static String skyName(String t) {
        if (t == null) return "";
        String s = t.trim();
        if (s.startsWith("U&")) s = s.substring(2);
        for (String[] p : SPELL) s = s.replace(p[0], p[1]);
        // "NickelodeonHD", "Sky HistoryHD": the HD on its own.
        s = GLUED_HD.matcher(s).replaceAll(" HD");
        // BBC One's and Two's regions ("BBC One Lon HD", "BBC One NW HD"): the channel the same everywhere.
        if (s.startsWith("BBC One") || s.startsWith("BBC Two") || s.startsWith("ITV1")) s = REGION.matcher(s).replaceAll(" ");
        return s.replaceAll("\\s+", " ").trim();
    }

    private static final String[][] SPELL = {
            {"SkySpMainEv", "Sky Sports Main Event"}, {"SkySp PL", "Sky Sports Premier League"},
            {"SkySp F'ball", "Sky Sports Football"}, {"SkySpBoxOff", "Sky Sports Box Office"}, {"SkySp", "Sky Sports "},
            {"TNTSBoxOff", "TNT Sports Box Office"}, {"TNTSports", "TNT Sports "}, {"ComedyXtra", "Comedy Central Extra"},
            {"ComedyCent", "Comedy Central "}, {"SkyDocmntrs", "Sky Documentaries "}, {"SkyPremiere", "Sky Premiere "},
            {"Disc.", "Discovery "}, {"Animal Plnt", "Animal Planet"}, {"Food Netwrk", "Food Network"},
            {"Cartoon Netwrk", "Cartoon Network"}, {"Cartoon Net ", "Cartoon Network "}, {"Crime+Inv", "Crime and Investigation"},
            {"Premier 1", "Premier Sports 1"}, {"Premier 2", "Premier Sports 2"}, {"NatGeoWild", "Nat Geo Wild"},
            {"BBCScotland", "BBC Scotland"}};
    private static final Pattern GLUED_HD = Pattern.compile("(?<=[a-z0-9])HD\\b");
    private static final Pattern REGION = Pattern.compile("\\s(?:Lon|NE|NW|Yks|Y&L|WM|EMid|East|SE|Wst|Sth|SW|CI|Scot|Wal|NI)(?=\\s|$)");

    /** A channel's key (the apps' Names.key) as both sides are compared: "junior", "geographic", "national" short. */
    public static String keyOf(String namesKey) {
        return namesKey == null ? "" : namesKey.replace("junior", "jr").replace("geographic", "geo").replace("national", "nat");
    }

    /**
     * Sky's list of channels as key → its number (key: the app's channel key for a name, then keyOf): the North West's
     * BBC One first, then an HD copy, adult channels left out.
     */
    public static Map<String, String> services(String json, Function<String, String> key) {
        Map<String, String> out = new HashMap<>();
        Map<String, Integer> rank = new HashMap<>();
        try {
            JSONArray a = new JSONObject(json).optJSONArray("services");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject s = a.optJSONObject(i);
                if (s == null || s.optString("sid").isEmpty() || s.optBoolean("adult")) continue;
                String raw = s.optString("t");
                String k = keyOf(key.apply(skyName(raw)));
                if (k.isEmpty()) continue;
                // The family's region (Manchester) first, then HD, then the list's own order.
                int r = (raw.contains(" NW ") || raw.endsWith(" NW") ? 4 : 0) + ("HD".equals(s.optString("sf")) ? 2 : 0);
                Integer had = rank.get(k);
                if (had == null || r > had) {
                    out.put(k, s.optString("sid"));
                    rank.put(k, r);
                }
            }
        } catch (JSONException e) {
            // none
        }
        return out;
    }

    /** A day's schedule ({"schedule": [{"sid", "events"}]}) for a channel's number: its events, empty for none. */
    public static JSONArray events(String json, String sid) {
        try {
            JSONArray sch = new JSONObject(json).optJSONArray("schedule");
            for (int i = 0; sch != null && i < sch.length(); i++) {
                JSONObject one = sch.optJSONObject(i);
                if (one != null && sid.equals(one.optString("sid")) && one.optJSONArray("events") != null) return one.optJSONArray("events");
            }
        } catch (JSONException e) {
            // none
        }
        return new JSONArray();
    }

    /**
     * The programme on at a time (start seconds "st", length "d"), its title preferred among several; one starting
     * within five minutes after the time when none is on at it. Its "programmeuuid", or "".
     */
    public static String uuidAt(JSONArray events, long at, String title) {
        if (events == null) return "";
        long t = at / 1000L;
        String want = squash(title);
        List<JSONObject> on = new ArrayList<>();
        JSONObject soon = null;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            long st = e.optLong("st"), en = st + e.optLong("d");
            if (st <= t && t < en) on.add(e);
            else if (st > t && st - t <= 300 && soon == null) soon = e;
        }
        JSONObject pick = null;
        for (JSONObject e : on) {
            String name = squash(e.optString("t"));
            if (!want.isEmpty() && !name.isEmpty() && (name.contains(want) || want.contains(name))) {
                pick = e;
                break;
            }
        }
        if (pick == null && !on.isEmpty()) pick = on.get(0);
        if (pick == null) pick = soon;
        return pick == null ? "" : pick.optString("programmeuuid", "");
    }

    /**
     * A day's events as a listing (Catalogues.Listing): each programme's picture ids, episode first, then its season's and
     * its series' ("programme;season;series", "" where Sky gives none).
     */
    public static Catalogues.Listing listing(JSONArray events) {
        int n = events == null ? 0 : events.length();
        long[] a = new long[n], b = new long[n];
        String[] t = new String[n], p = new String[n];
        int k = 0;
        for (int i = 0; i < n; i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            a[k] = e.optLong("st") * 1000L;
            b[k] = a[k] + e.optLong("d") * 1000L;
            t[k] = e.optString("t");
            p[k] = e.optString("programmeuuid", "") + ";" + e.optString("seasonuuid", "") + ";" + e.optString("seriesuuid", "");
            k++;
        }
        return new Catalogues.Listing(java.util.Arrays.copyOf(a, k), java.util.Arrays.copyOf(b, k), java.util.Arrays.copyOf(t, k),
                java.util.Arrays.copyOf(p, k));
    }

    /** A listing's picture ids ("programme;season;series") as Sky's picture addresses at a width, the episode's first. */
    public static String[] pictures(String ids, int width) {
        String[] part = ids == null ? new String[0] : ids.split(";", -1);
        List<String> out = new ArrayList<>(3);
        for (String id : part) {
            String u = picture(id, width);
            if (!u.isEmpty() && !out.contains(u)) out.add(u);
        }
        return out.toArray(new String[0]);
    }

    /** When to look in Sky's guide for a programme from start to stop: now for the one on now, else a little into it. */
    public static long lookAt(long start, long stop, long now) {
        return start <= now && now < stop ? now : start + Math.min(10 * 60_000L, (stop - start) / 2);
    }

    private static String squash(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') b.append(c);
        }
        return b.toString();
    }
}
