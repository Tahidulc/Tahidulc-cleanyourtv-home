package com.gathin.broadcast;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A photo of the ground a game is played at, for the line-ups' background: a freely licensed picture from Wikimedia
 * Commons (CC0, CC BY, CC BY-SA or public domain), preferring views from the stands over the pitch, with its credit to
 * show. The ground is looked up on Wikidata first (by its name, else through Wikipedia's search with the city), so a
 * ground known by a sponsor's or a new name ("Riyadh Air Metropolitano") is found under the name Commons files it by:
 * its Commons category, its main image, its other and old names. Then the name's own category, the name, and the name
 * without its leading sponsor's words. At most MAX_CALLS requests a ground. Kept two months per ground (a miss three
 * days). The page grades it for the game's time of day.
 */
public final class Grounds {
    private Grounds() { }

    /** Wikimedia asks clients to say who they are; nothing about the family is sent. */
    private static final String UA = "TVHomeLauncher/1.0 (TV launcher; stadium backgrounds)";
    private static final String IMAGE_INFO = "&prop=imageinfo&iiprop=url%7Csize%7Cmime%7Cextmetadata&iiurlwidth=1920"
            + "&iiextmetadatafilter=Artist%7CLicenseShortName";
    private static final String API = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search"
            + "&gsrnamespace=6&gsrlimit=40" + IMAGE_INFO + "&gsrsearch=";
    /** One file's details, as the search gives them (for the ground's main image on Wikidata). */
    private static final String FILE = "https://commons.wikimedia.org/w/api.php?action=query&format=json" + IMAGE_INFO + "&titles=";
    private static final String WD_SEARCH = "https://www.wikidata.org/w/api.php?action=wbsearchentities&format=json&language=en"
            + "&uselang=en&type=item&limit=7&search=";
    private static final String WD_GET = "https://www.wikidata.org/w/api.php?action=wbgetentities&format=json"
            + "&props=labels%7Caliases%7Cdescriptions%7Cclaims&languages=en%7Ces%7Cde%7Cfr%7Cit%7Cpt%7Cnl&ids=";
    private static final String WP_SEARCH = "https://en.wikipedia.org/w/api.php?action=query&format=json&generator=search"
            + "&gsrnamespace=0&gsrlimit=3&prop=pageprops&ppprop=wikibase_item&redirects=1&gsrsearch=";
    private static final long KEEP_MS = 60L * 24 * 3600_000L, MISS_MS = 3L * 24 * 3600_000L;
    /** Requests to Wikimedia for one ground at most: Wikidata's (up to 3), Commons' searches and the photo itself. */
    static final int MAX_CALLS = 8;
    /**
     * The miss marker's version: a ground the old search (its name alone) missed is looked for again. A photo found
     * either way is still good, so hits keep their names.
     */
    private static final String MISS = ".v2.none";

    /** {photo: data URL (JPEG), credit: "Photo: …"} for the ground, or null (io thread; cached). city: its town, or "". */
    public static JSONObject ground(String stadium, String city, File cacheDir) {
        if (stadium == null || stadium.trim().length() < 3 || cacheDir == null) return null;
        String name = stadium.trim(), town = city == null ? "" : city.trim();
        File dir = new File(cacheDir, "grounds");
        dir.mkdirs();
        String key = Integer.toHexString(name.toLowerCase(Locale.ROOT).hashCode());
        File jpg = new File(dir, key + ".jpg"), txt = new File(dir, key + ".txt"), miss = new File(dir, key + MISS);
        File old = new File(dir, key + ".none");
        if (old.exists()) old.delete();
        long now = System.currentTimeMillis();
        try {
            if (jpg.exists() && txt.exists() && now - jpg.lastModified() < KEEP_MS) return made(read(jpg), new String(read(txt), StandardCharsets.UTF_8));
            if (miss.exists() && now - miss.lastModified() < MISS_MS) return null;
        } catch (Exception e) {
            return null;
        }
        Calls calls = new Calls(MAX_CALLS);
        Entity ent = null;
        try {
            ent = entity(name, town, calls);
        } catch (Exception e) {
            calls.failed = true;   // Wikidata busy or away: Commons by the name alone, as before
        }
        JSONObject main = null;
        if (ent != null && !ent.image.isEmpty() && calls.take()) {
            try {
                main = pick(fetch(FILE + URLEncoder.encode("File:" + ent.image, "UTF-8")), name, true);
            } catch (Exception e) {
                calls.failed = true;
            }
        }
        boolean first = true;
        for (String[] q : queries(name, town, ent)) {
            if (calls.left() < 2) break;   // one kept for the photo
            calls.take();
            JSONObject best;
            try {
                best = pick(fetch(API + URLEncoder.encode(q[0], "UTF-8")), q[1], "cat".equals(q[2]));
            } catch (Exception e) {
                calls.failed = true;   // most likely asked to slow down: no more this time, and no miss kept
                break;
            }
            // the ground's main image goes against its own category's best
            if (first && main != null && (best == null || main.optInt("score") > best.optInt("score"))) best = main;
            first = false;
            if (best == null) continue;
            try {
                calls.take();
                byte[] jpeg = shrink(get(best.getString("url"), 8 << 20));
                if (jpeg == null) continue;
                String credit = best.getString("credit");
                write(jpg, jpeg);
                write(txt, credit.getBytes(StandardCharsets.UTF_8));
                return made(jpeg, credit);
            } catch (Exception e) {
                calls.failed = true;
            }
        }
        try {
            if (!calls.failed) write(miss, new byte[0]);
        } catch (IOException ignored) { }
        return null;   // no photo: the drawn ground, as before
    }

    /** The requests left for one ground, and whether one failed (then no miss is kept). */
    static final class Calls {
        private int left;
        boolean failed;

        Calls(int max) {
            left = max;
        }

        boolean take() {
            if (left <= 0) return false;
            left--;
            return true;
        }

        int left() {
            return left;
        }
    }

    // ---- Wikidata ------------------------------------------------------------------------------------------------------

    /** A ground as Wikidata has it: its id, Commons category (P373), main image (P18), and its names (label, aliases). */
    static final class Entity {
        String id = "", category = "", image = "";
        final List<String> names = new ArrayList<>();
    }

    /**
     * The ground on Wikidata, or null: its name searched there (labels and aliases, so an old or sponsor's name is
     * found when Wikidata lists it), else Wikipedia's search for the name and the city (an article mentioning the new
     * name); then the first of those that is a stadium. Three requests at most.
     */
    static Entity entity(String name, String city, Calls calls) throws IOException, JSONException {
        List<String> ids = new ArrayList<>();
        boolean sure = false;
        if (calls.take()) {
            JSONObject a = fetch(WD_SEARCH + URLEncoder.encode(name, "UTF-8"));
            for (String[] hit : searchHits(a)) {
                if (stadiumWords(hit[1])) { if (!sure) ids.clear(); sure = true; ids.add(hit[0]); }
                else if (!sure && hit[1].isEmpty() && ids.size() < 3) ids.add(hit[0]);
            }
        }
        if (!sure && calls.take()) {
            String q = name + " " + (city.isEmpty() ? "stadium" : city);
            for (String id : wikiItems(fetch(WP_SEARCH + URLEncoder.encode(q, "UTF-8")))) if (!ids.contains(id)) ids.add(id);
        }
        if (ids.isEmpty() || !calls.take()) return null;
        List<String> some = ids.subList(0, Math.min(6, ids.size()));
        return chosen(fetch(WD_GET + URLEncoder.encode(String.join("|", some), "UTF-8")), some);
    }

    /** Pure: Wikidata's search answer as [id, description] pairs, in its order. */
    static List<String[]> searchHits(JSONObject answer) {
        List<String[]> out = new ArrayList<>();
        JSONArray s = answer == null ? null : answer.optJSONArray("search");
        for (int i = 0; s != null && i < s.length(); i++) {
            JSONObject h = s.optJSONObject(i);
            if (h == null || !h.optString("id").startsWith("Q")) continue;
            out.add(new String[]{h.optString("id"), h.optString("description", "")});
        }
        return out;
    }

    /** Pure: the Wikidata ids of Wikipedia's search answer (pageprops.wikibase_item), in the search's order. */
    static List<String> wikiItems(JSONObject answer) {
        JSONObject q = answer == null ? null : answer.optJSONObject("query");
        JSONObject pages = q == null ? null : q.optJSONObject("pages");
        List<JSONObject> found = new ArrayList<>();
        JSONArray names = pages == null ? null : pages.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            JSONObject p = pages.optJSONObject(names.optString(i));
            JSONObject pp = p == null ? null : p.optJSONObject("pageprops");
            if (pp != null && pp.optString("wikibase_item").startsWith("Q")) found.add(p);
        }
        found.sort((a, b) -> Integer.compare(a.optInt("index", 99), b.optInt("index", 99)));
        List<String> out = new ArrayList<>();
        for (JSONObject p : found) out.add(p.optJSONObject("pageprops").optString("wikibase_item"));
        return out;
    }

    /** Wikidata's classes for a ground: stadium, association football stadium, sports venue, arena. */
    private static final Set<String> STADIUMS = new HashSet<>(Arrays.asList("Q483110", "Q1154710", "Q1076486", "Q641226"));

    /**
     * Pure: whether a description says a ground ("football stadium in Madrid, Spain", "stadium, home of Everton Football
     * Club"), not first a station, club or the like ("metro station by the stadium", "football club whose ground is").
     */
    static boolean stadiumWords(String description) {
        String d = " " + (description == null ? "" : description.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9à-ÿ]+", " ")) + " ";
        int ground = first(d, "stadium", "arena", "ground", "venue", "estadio", "stadion", "stade", "stadio", "estadi", "ballpark");
        int other = first(d, "station", "club", "team", "season", "album", "song", "film", "airline", "company", "street", "district");
        return ground >= 0 && (other < 0 || ground < other);
    }

    /** Where the first of some words comes in a spaced text (" a b c "), or -1. */
    private static int first(String d, String... words) {
        int at = -1;
        for (String k : words) {
            int i = d.indexOf(" " + k + " ");
            if (i >= 0 && (at < 0 || i < at)) at = i;
        }
        return at;
    }

    /**
     * Pure: of Wikidata's entities answer (wbgetentities), the first of ids that is a ground (an instance of a stadium
     * class, or so described), with its Commons category, main image and names (English label first, then the other
     * labels and the aliases, each once); null when none is.
     */
    static Entity chosen(JSONObject answer, List<String> ids) {
        JSONObject all = answer == null ? null : answer.optJSONObject("entities");
        for (String id : ids) {
            JSONObject e = all == null ? null : all.optJSONObject(id);
            if (e == null || !ground(e)) continue;
            Entity out = new Entity();
            out.id = id;
            out.category = claim(e, "P373");
            out.image = claim(e, "P18");
            Set<String> seen = new HashSet<>();
            JSONObject labels = e.optJSONObject("labels");
            addValue(out, seen, labels == null ? null : labels.optJSONObject("en"));
            JSONObject aliases = e.optJSONObject("aliases");
            JSONArray en = aliases == null ? null : aliases.optJSONArray("en");
            for (int i = 0; en != null && i < en.length(); i++) addValue(out, seen, en.optJSONObject(i));
            JSONArray ln = labels == null ? null : labels.names();
            for (int i = 0; ln != null && i < ln.length(); i++) addValue(out, seen, labels.optJSONObject(ln.optString(i)));
            JSONArray an = aliases == null ? null : aliases.names();
            for (int i = 0; an != null && i < an.length(); i++) {
                JSONArray list = aliases.optJSONArray(an.optString(i));
                for (int k = 0; list != null && k < list.length(); k++) addValue(out, seen, list.optJSONObject(k));
            }
            return out;
        }
        return null;
    }

    private static void addValue(Entity e, Set<String> seen, JSONObject term) {
        String v = term == null ? "" : term.optString("value", "").trim();
        if (v.length() >= 3 && seen.add(v.toLowerCase(Locale.ROOT))) e.names.add(v);
    }

    private static boolean ground(JSONObject e) {
        JSONObject claims = e.optJSONObject("claims");
        JSONArray p31 = claims == null ? null : claims.optJSONArray("P31");
        for (int i = 0; p31 != null && i < p31.length(); i++) {
            JSONObject v = value(p31.optJSONObject(i));
            if (v != null && STADIUMS.contains(v.optString("id"))) return true;
        }
        JSONObject d = e.optJSONObject("descriptions");
        JSONArray dn = d == null ? null : d.names();
        for (int i = 0; dn != null && i < dn.length(); i++) {
            JSONObject t = d.optJSONObject(dn.optString(i));
            if (t != null && stadiumWords(t.optString("value"))) return true;
        }
        return false;
    }

    /** A statement's value object (an item's {"id": ...}), or null. */
    private static JSONObject value(JSONObject statement) {
        JSONObject snak = statement == null ? null : statement.optJSONObject("mainsnak");
        JSONObject dv = snak == null ? null : snak.optJSONObject("datavalue");
        return dv == null ? null : dv.optJSONObject("value");
    }

    /** The first value of a string property (P373's category name, P18's file name), "" when none. */
    private static String claim(JSONObject e, String prop) {
        JSONObject claims = e.optJSONObject("claims");
        JSONArray c = claims == null ? null : claims.optJSONArray(prop);
        for (int i = 0; c != null && i < c.length(); i++) {
            JSONObject snak = c.optJSONObject(i) == null ? null : c.optJSONObject(i).optJSONObject("mainsnak");
            JSONObject dv = snak == null ? null : snak.optJSONObject("datavalue");
            String v = dv == null ? "" : dv.optString("value", "").trim();
            if (!v.isEmpty() && !v.startsWith("{")) return v;
        }
        return "";
    }

    // ---- what to search Commons for ------------------------------------------------------------------------------------

    /** Words that name what a ground is, not which: never a ground's name alone. */
    private static final Set<String> GENERIC = new HashSet<>(Arrays.asList("stadium", "stadion", "estadio", "estadi", "stadio",
            "stade", "arena", "park", "ground", "field", "the", "de", "del", "la", "el", "of", "municipal", "community", "nuevo", "new"));

    /**
     * Pure: the name less its leading words (a sponsor's), shortest first, where something that names a ground is left:
     * "Riyadh Air Metropolitano": "Metropolitano", "Air Metropolitano"; "Spotify Camp Nou": "Camp Nou";
     * "Estadio Abanca-Balaídos": "Balaídos", "Abanca Balaídos"; none for "Emirates Stadium" or "Gtech Community
     * Stadium", where only words for any ground would be left.
     */
    static List<String> variants(String name) {
        List<String> out = new ArrayList<>();
        String[] w = name == null ? new String[0] : name.trim().split("[\\s-]+");
        for (int k = w.length - 1; k >= 1; k--) {
            boolean named = false;
            for (int i = k; i < w.length; i++) {
                String f = PlPhotos.fold(w[i]);
                if (f.length() >= 4 && !GENERIC.contains(f)) named = true;
            }
            if (named) out.add(String.join(" ", Arrays.copyOfRange(w, k, w.length)));
        }
        return out;
    }

    /**
     * Pure: Commons' searches for a ground, in turn: each {search, the name a file's title must carry outside a
     * category, "cat" for a category or "text"}. With Wikidata's entity: its category; its first other name (English
     * label first; those sharing the name's own words before the rest), as a title (a category holds only its own
     * files, not its subcategories'); the name's own category and the name; a second other name. Without: the name's
     * category and the name (the search as it was), then the name less its sponsor's words, as a title (two at most:
     * a guessed category could be anything's). Last, either way, the name with the city.
     */
    static List<String[]> queries(String name, String city, Entity e) {
        List<String[]> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (e != null) {
            List<String> core = variants(name), named = new ArrayList<>(), others = new ArrayList<>();
            for (String n : e.names) {
                if (n.equalsIgnoreCase(name)) continue;
                boolean shares = false;
                for (String v : core) if (PlPhotos.fold(n).contains(PlPhotos.fold(v))) shares = true;
                (shares ? named : others).add(n);
            }
            named.addAll(others);
            if (!e.category.isEmpty()) add(out, seen, cat(e.category), e.category, "cat");
            if (!named.isEmpty()) add(out, seen, text(named.get(0)), named.get(0), "text");
            add(out, seen, cat(name), name, "cat");
            add(out, seen, text(name), name, "text");
            if (named.size() > 1) add(out, seen, text(named.get(1)), named.get(1), "text");
        } else {
            add(out, seen, cat(name), name, "cat");
            add(out, seen, text(name), name, "text");
            int k = 0;
            for (String v : variants(name)) {
                if (k >= 2) break;
                if (add(out, seen, text(v), v, "text")) k++;
            }
        }
        if (city != null && !city.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(city.toLowerCase(Locale.ROOT))) {
            add(out, seen, "\"" + name + "\" " + city + " filetype:bitmap", name, "text");
        }
        return out;
    }

    private static String text(String name) {
        return "\"" + name + "\" stadium filetype:bitmap";
    }

    private static String cat(String name) {
        return "incategory:\"" + name + "\" filetype:bitmap";
    }

    private static boolean add(List<String[]> out, Set<String> seen, String search, String ground, String kind) {
        if (!seen.add(search.toLowerCase(Locale.ROOT))) return false;
        out.add(new String[]{search, ground, kind});
        return true;
    }

    private static JSONObject made(byte[] jpeg, String credit) throws JSONException {
        return new JSONObject().put("photo", "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)).put("credit", credit);
    }

    /** The best photo in a Commons answer {url, credit, score}, or null. inCategory: the ground's own category (or image). */
    static JSONObject pick(JSONObject answer, String stadium, boolean inCategory) throws JSONException {
        JSONObject pages = answer == null ? null : answer.optJSONObject("query");
        pages = pages == null ? null : pages.optJSONObject("pages");
        if (pages == null) return null;
        JSONObject best = null;
        int bestScore = Integer.MIN_VALUE;
        JSONArray names = pages.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            JSONObject p = pages.getJSONObject(names.getString(i));
            JSONArray infos = p.optJSONArray("imageinfo");
            JSONObject ii = infos == null ? null : infos.optJSONObject(0);
            if (ii == null) continue;
            JSONObject md = ii.optJSONObject("extmetadata");
            String licence = md == null ? "" : value(md, "LicenseShortName");
            String title = p.optString("title");
            int s = score(title, ii.optInt("width"), ii.optInt("height"), ii.optString("mime"), licence, stadium, inCategory);
            if (s > bestScore) {
                String url = ii.optString("thumburl", ii.optString("url"));
                if (url.isEmpty()) continue;
                bestScore = s;
                String who = md == null ? "" : value(md, "Artist").replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
                if (who.length() > 40) who = who.substring(0, 40).trim() + "…";
                best = new JSONObject().put("url", url).put("credit", "Photo: " + (who.isEmpty() ? "" : who + ", ") + licence + " (Wikimedia Commons)")
                        .put("score", s);
            }
        }
        return bestScore < 0 ? null : best;
    }

    private static String value(JSONObject md, String k) {
        JSONObject o = md.optJSONObject(k);
        return o == null ? "" : o.optString("value", "").trim();
    }

    /**
     * Pure: how good a Commons file is as the line-ups' background (below 0: not to be used). A wide photo, at least
     * 1400 pixels across, freely licensed, of the ground from the stands or during a game; not its outside, a sign, a
     * statue or a map. Outside its own category the title must name the ground.
     */
    static int score(String title, int w, int h, String mime, String licence, String stadium, boolean inCategory) {
        if (title == null || h <= 0) return -1000;
        if (!"image/jpeg".equals(mime) || w < 1400) return -1000;
        double r = (double) w / h;
        if (r < 1.3 || r > 2.6) return -1000;
        String l = licence == null ? "" : licence.toLowerCase(Locale.ROOT);
        if (!(l.startsWith("cc0") || l.startsWith("cc by") || l.contains("public domain") || l.equals("pd"))) return -1000;
        // words as words ("sign" not in "design"): the title with its punctuation as spaces, a space each side
        String t = " " + title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9à-ÿ]+", " ") + " ";
        String ground = " " + stadium.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9à-ÿ]+", " ").trim() + " ";
        if (!inCategory && !t.contains(ground)) return -1000;
        int s = 0;
        for (String k : new String[]{"interior", "inside", "pitch", "panorama", "from the", "v", "vs", "match", "kick off", "crowd"}) {
            if (t.contains(" " + k + " ")) s += 3;
        }
        if (t.contains(" stand ") || t.contains(" end ")) s += 1;
        for (String k : new String[]{"facade", "façade", "exterior", "outside", "aerial", "statue", "sign", "station", "gate", "entrance",
                "plaque", "logo", "map", "museum", "shop", "ticket", "construction", "demolition", "car park"}) {
            if (t.contains(" " + k + " ")) s -= 4;
        }
        return s + Math.min(3, w / 1500);
    }

    /** At most 1920 across, JPEG (about 300 KB): what the page is given. */
    private static byte[] shrink(byte[] b) {
        if (b == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(b, 0, b.length, o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= 1920) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length, d);
        if (bm == null) return null;
        if (bm.getWidth() > 1920) {
            Bitmap s = Bitmap.createScaledBitmap(bm, 1920, Math.round(bm.getHeight() * 1920f / bm.getWidth()), true);
            bm.recycle();
            bm = s;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 82, out);
        bm.recycle();
        return out.toByteArray();
    }

    private static JSONObject fetch(String url) throws IOException, JSONException {
        return new JSONObject(new String(get(url, 2 << 20), StandardCharsets.UTF_8));
    }

    private static byte[] get(String url, int max) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setRequestProperty("User-Agent", UA);
        try (java.io.InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > max) throw new IOException("too big");
            }
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    private static byte[] read(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static void write(File f, byte[] b) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(b);
        }
    }
}
