package com.gathin.broadcast;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * La Liga's own cut-out pictures of its players (head to waist, in the kit) for the classic line-ups of a La Liga game,
 * as PlPhotos has the Premier League's: each player's public page on laliga.com (found by the name La Liga knows him
 * by) lists his pictures. The page must name the same player and, when it says, the same club. The picture is cut
 * square to the Premier League's framing (head at the top, a third of the width across), so the pages draw it as
 * they draw a Premier League one. Kept a month per player (a miss three days); at most PAGE_BUDGET pages a game (70: both elevens and their second tries).
 * Only the public pages and pictures are read. Never the main thread.
 */
public final class LaLigaPhotos {
    private LaLigaPhotos() { }

    static final String PAGE = "https://www.laliga.com/en-GB/player/";
    /** Who is asking, as Grounds says it to Wikimedia: nothing about the family. */
    private static final String UA = "TVHomeLauncher/1.0 (TV launcher; player pictures)";
    static final long KEEP_MS = 30L * 24 * 3600_000L, MISS_MS = 3L * 24 * 3600_000L;
    /** Pages fetched at most for one game's line-ups (the rest, and the subs past it, are tried at the next game). */
    static final int PAGE_BUDGET = 70;   // both elevens with their second tries (25 left players out on a first game)
    /** The square cut: its side as a share of the picture's height, and the room left above the head (of the side). */
    static final float SIDE = 0.713f, HEAD_ROOM = 0.012f;
    /** The cut's size, as the Premier League's pictures come. */
    static final int OUT = 500;
    /** A few at a time: pages, and the big pictures in memory. */
    private static final Semaphore PAGES = new Semaphore(3), DECODE = new Semaphore(2);

    /** One game's allowance of pages, shared by its players. */
    public static final class Budget {
        private final AtomicInteger left;

        public Budget() {
            this(PAGE_BUDGET);
        }

        Budget(int pages) {
            left = new AtomicInteger(pages);
        }

        boolean take() {
            return left.getAndDecrement() > 0;
        }
    }

    // ---- the competition --------------------------------------------------------------------------------------------

    /**
     * Pure: whether a game is La Liga's (the first division): ESPN's "esp.1", FotMob's league 87, or the name ("LaLiga",
     * "LALIGA EA SPORTS", "Primera División"; not LaLiga 2 / Hypermotion), from the app's game or FotMob's details.
     */
    public static boolean isLaLiga(String compId, String compName, JSONObject details) {
        String id = compId == null ? "" : compId.trim().toLowerCase(Locale.ROOT);
        if (id.equals("esp.1") || id.equals("87")) return true;
        if (laLigaName(compName)) return true;
        JSONObject g = details == null ? null : details.optJSONObject("general");
        if (g == null) return false;
        return g.optInt("leagueId") == 87 || g.optInt("parentLeagueId") == 87 || laLigaName(g.optString("leagueName"));
    }

    private static boolean laLigaName(String name) {
        String n = PlPhotos.fold(name);
        if (!(n.contains("laliga") || n.contains("primeradivision"))) return false;
        for (String no : new String[]{"laliga2", "hypermotion", "segunda", "smartbank", "women", "femenin"}) {
            if (n.contains(no)) return false;
        }
        return true;
    }

    // ---- the names ----------------------------------------------------------------------------------------------------

    /** Pure: a name as La Liga writes it in its addresses: plain lower case, accents off, hyphens for spaces ("Julián Álvarez": "julian-alvarez"). */
    static String slug(String s) {
        String t = PlPhotos.plain(s).replace("'", "").replace("’", "");
        return t.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    /**
     * Pure: the addresses to try for a FotMob player, likeliest first: the full name ("lamine-yamal"), the surname
     * ("oblak", "ter-stegen"), for a surname of two words its first and the first name with it ("gonzalez",
     * "pedro-gonzalez"), then the first name alone ("giuliano") for one La Liga knows by it. A one-word name is tried once.
     */
    static List<String> slugs(String name, String last) {
        String full = name == null || name.trim().isEmpty() ? (last == null ? "" : last.trim()) : name.trim();
        String l = last == null || last.trim().isEmpty() ? lastWord(full) : last.trim();
        String first = firstOf(full, l);
        Set<String> out = new LinkedHashSet<>();
        out.add(slug(full));
        out.add(slug(l));
        String[] lw = l.split("[\\s-]+");
        if (lw.length >= 2 && !PARTICLES.contains(PlPhotos.fold(lw[0]))) {
            out.add(slug(lw[0]));
            if (!first.isEmpty()) out.add(slug(first.split("\\s+")[0] + " " + lw[0]));
        }
        String[] fw = first.isEmpty() ? new String[0] : first.split("\\s+");
        if (fw.length == 1 && PlPhotos.fold(fw[0]).length() >= 4) out.add(slug(fw[0]));
        out.remove("");
        List<String> list = new ArrayList<>(out);
        return list.size() > 4 ? list.subList(0, 4) : list;
    }

    /** A surname's small first words ("ter Stegen", "de Jong", "Van de Beek"): not a surname of two on their own. */
    private static final Set<String> PARTICLES = new LinkedHashSet<>(Arrays.asList("de", "da", "di", "do", "del", "der", "dos", "das",
            "du", "van", "von", "ter", "ten", "le", "la", "el", "al", "mac", "mc", "st", "san"));

    private static String lastWord(String s) {
        String[] w = s.trim().split("\\s+");
        return w.length == 0 ? s : w[w.length - 1];
    }

    /** Pure: the first name: the full name less the surname at its end ("Lamine Yamal", "Yamal": "Lamine"; "Koke": ""). */
    static String firstOf(String full, String last) {
        String f = full == null ? "" : full.trim(), l = last == null ? "" : last.trim();
        if (!l.isEmpty() && f.length() > l.length() && f.toLowerCase(Locale.ROOT).endsWith(l.toLowerCase(Locale.ROOT))) {
            return f.substring(0, f.length() - l.length()).trim();
        }
        if (!l.isEmpty() && PlPhotos.fold(f).equals(PlPhotos.fold(l))) return "";
        String[] w = f.split("\\s+");
        if (w.length < 2) return "";
        return String.join(" ", Arrays.copyOf(w, w.length - 1));
    }

    /** Pure: a name's words in plain letters ("Marc-André ter Stegen": [marc, andre, ter, stegen]). */
    static List<String> words(String s) {
        List<String> out = new ArrayList<>();
        for (String w : PlPhotos.plain(s).split("[^a-z0-9]+")) if (!w.isEmpty()) out.add(w);
        return out;
    }

    /**
     * Pure: whether a page's names (its full name, the name he is known by, his first and last names) are the FotMob
     * player's: all his surname's words among them (accents aside; "Yamal" in "Lamine Yamal Nasraoui Ebana", "Koke" by
     * the name he is known by), and when FotMob gives a first name, its start in one of them ("Alex": "Alejandro").
     */
    static boolean samePlayer(List<String> pageNames, String name, String last) {
        List<String> page = new ArrayList<>();
        for (String n : pageNames) page.addAll(words(n));
        if (page.isEmpty()) return false;
        String full = name == null || name.trim().isEmpty() ? (last == null ? "" : last) : name;
        String l = last == null || last.trim().isEmpty() ? lastWord(full) : last;
        List<String> lw = words(l);
        if (lw.isEmpty()) return false;
        String joined = " " + String.join(" ", page) + " ";
        boolean surname = joined.contains(" " + String.join(" ", lw) + " ") || page.contains(String.join("", lw));
        if (!surname) {
            // the words apart, in another order or with others between: every word of the surname of two letters or more
            surname = true;
            for (String w : lw) if (w.length() >= 2 && !page.contains(w)) surname = false;
        }
        if (!surname) return false;
        List<String> fw = words(firstOf(full, l));
        if (fw.isEmpty() || fw.get(0).length() < 2) return true;
        String start = fw.get(0).substring(0, Math.min(3, fw.get(0).length()));
        for (String w : page) if (w.contains(start)) return true;
        return false;
    }

    /** Words that say nothing of which club it is ("Club Atlético de Madrid SAD", "Fútbol Club Barcelona", "C.A. Osasuna"). */
    private static final Set<String> CLUB_WORDS = new LinkedHashSet<>(Arrays.asList("club", "cf", "fc", "sad", "de", "del", "la",
            "el", "rcd", "rc", "cd", "ud", "sd", "ca", "c", "a", "d", "futbol", "football", "balompie", "deportivo", "the"));

    /** Pure: a club's name with the words that say nothing taken out, run together ("Atlético de Madrid": "atleticomadrid"). */
    static String clubCore(String club) {
        StringBuilder b = new StringBuilder();
        for (String w : words(club)) if (!CLUB_WORDS.contains(w)) b.append(w);
        return b.toString();
    }

    /**
     * Pure: whether one of a page's club names (its squad's team: full name, the name it goes by, its address) is one of
     * ours (the app's name, FotMob's): the same once the empty words are out, or one the start of the other ("Real
     * Betis" and "Real Betis Balompié", "Celta Vigo" and "RC Celta"), never only a city ("Barcelona" is not Espanyol's
     * "RCD Espanyol de Barcelona").
     */
    static boolean sameClub(List<String> pageClubs, String[] ours) {
        for (String p : pageClubs) {
            String a = clubCore(p);
            if (a.length() < 4) continue;
            for (String o : ours) {
                String b = clubCore(o);
                if (b.length() < 4) continue;
                if (a.equals(b) || a.startsWith(b) || b.startsWith(a)) return true;
            }
        }
        return false;
    }

    // ---- the page ----------------------------------------------------------------------------------------------------

    /** One club the page lists the player in: its names and his cut-outs there (the bigger one first). */
    static final class Squad {
        final List<String> clubs = new ArrayList<>();
        final List<String> cutOuts = new ArrayList<>();
    }

    /** What a player's page says: his names and his clubs. */
    static final class Page {
        final List<String> names = new ArrayList<>();
        final List<Squad> squads = new ArrayList<>();
    }

    private static final Pattern NEXT = Pattern.compile("<script[^>]*id=\"__NEXT_DATA__\"[^>]*>");
    private static final Pattern META = Pattern.compile("<meta[^>]*property=\"og:(title|site_name)\"[^>]*content=\"([^\"]*)\"");
    private static final Pattern TITLE = Pattern.compile("<title>([^<]+)</title>");
    private static final Pattern H1 = Pattern.compile("<h1[^>]*>([^<]+)</h1>");
    private static final Pattern ASSET = Pattern.compile("https://assets\\.laliga\\.com/squad/[A-Za-z0-9_/.-]+?_(00[13])_000\\.png");
    private static final Pattern TEAM = Pattern.compile("\"team\":\\{\"id\":\\d+,\"slug\":\"([^\"]*)\",\"name\":\"([^\"]*)\",\"nickname\":\"([^\"]*)\"");
    private static final Pattern SIZE = Pattern.compile("/(\\d+)x(\\d+)/");
    private static final Pattern ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);");

    /**
     * Pure: a player page read: from the data the page is built from (its __NEXT_DATA__: props.pageProps.player, with
     * name, nickname, firstname, lastname and squads [{team {slug, name, nickname}, photos {"001": {"1024x1113": url}}}]),
     * else from its title, headline and the picture addresses in it.
     */
    static Page parse(String html) {
        Page p = new Page();
        if (html == null) return p;
        JSONObject player = nextData(html);
        if (player != null) {
            for (String k : new String[]{"nickname", "name", "firstname", "lastname"}) {
                String v = player.optString(k, "").trim();
                if (!v.isEmpty() && !p.names.contains(v)) p.names.add(v);
            }
            JSONArray sq = player.optJSONArray("squads");
            for (int i = 0; sq != null && i < sq.length(); i++) {
                JSONObject s = sq.optJSONObject(i);
                if (s == null) continue;
                Squad q = new Squad();
                JSONObject team = s.optJSONObject("team");
                if (team != null) {
                    for (String k : new String[]{"nickname", "name", "slug"}) {
                        String v = team.optString(k, "").trim();
                        if (!v.isEmpty()) q.clubs.add(v);
                    }
                }
                JSONObject photos = s.optJSONObject("photos");
                if (photos != null) {
                    for (String kind : new String[]{"001", "003"}) {
                        String u = best(photos.optJSONObject(kind));
                        if (u != null) q.cutOuts.add(u);
                    }
                }
                p.squads.add(q);
            }
        }
        if (p.names.isEmpty()) {
            Matcher m = META.matcher(html);
            while (m.find()) addName(p, m.group(2));
            m = TITLE.matcher(html);
            if (m.find()) addName(p, m.group(1));
            m = H1.matcher(html);
            if (m.find()) addName(p, m.group(1));
        }
        boolean any = false;
        for (Squad q : p.squads) any |= !q.cutOuts.isEmpty();
        if (!any) {
            Squad q = new Squad();
            Matcher t = TEAM.matcher(html);
            while (t.find()) for (int g = 1; g <= 3; g++) if (!t.group(g).isEmpty() && !q.clubs.contains(t.group(g))) q.clubs.add(t.group(g));
            String[] pick = new String[2];
            int[] width = new int[2];
            Matcher a = ASSET.matcher(html);
            while (a.find()) {
                int k = "001".equals(a.group(1)) ? 0 : 1;
                int w = widthOf(a.group());
                if (pick[k] == null || better(w, width[k])) { pick[k] = a.group(); width[k] = w; }
            }
            for (String u : pick) if (u != null) q.cutOuts.add(u);
            if (!q.cutOuts.isEmpty()) { p.squads.clear(); p.squads.add(q); }
        }
        return p;
    }

    private static void addName(Page p, String raw) {
        String v = unescape(raw).replaceFirst("\\s*\\|.*$", "").replaceFirst("(?i)\\s+LALIGA\\b.*$", "").trim();
        if (!v.isEmpty() && !p.names.contains(v)) p.names.add(v);
    }

    /** Pure: the few HTML escapes names carry ("&#x27;", "&amp;"). */
    static String unescape(String s) {
        Matcher m = ENTITY.matcher(s);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            int c;
            try {
                c = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
            } catch (NumberFormatException e) {
                c = '?';
            }
            m.appendReplacement(b, Matcher.quoteReplacement(new String(Character.toChars(c))));
        }
        m.appendTail(b);
        return b.toString().replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    private static JSONObject nextData(String html) {
        Matcher m = NEXT.matcher(html);
        if (!m.find()) return null;
        int end = html.indexOf("</script>", m.end());
        if (end < 0) return null;
        try {
            JSONObject props = new JSONObject(html.substring(m.end(), end)).optJSONObject("props");
            JSONObject pp = props == null ? null : props.optJSONObject("pageProps");
            return pp == null ? null : pp.optJSONObject("player");
        } catch (Exception e) {
            return null;
        }
    }

    /** Pure: of one picture's sizes ({"1024x1113": url, ...}), the PNG 1024 across, else the widest under it, else the narrowest over. */
    static String best(JSONObject sizes) {
        if (sizes == null) return null;
        String pick = null;
        int pw = 0;
        for (Iterator<String> it = sizes.keys(); it.hasNext(); ) {
            String k = it.next(), u = sizes.optString(k, "");
            if (!u.startsWith("https://") || !u.toLowerCase(Locale.ROOT).endsWith(".png")) continue;
            int w;
            try {
                w = Integer.parseInt(k.substring(0, Math.max(0, k.indexOf('x'))));
            } catch (NumberFormatException e) {
                continue;
            }
            if (pick == null || better(w, pw)) { pick = u; pw = w; }
        }
        return pick;
    }

    /** Pure: whether a width w is better than the one had: 1024 best, then the widest below, then the narrowest above. */
    private static boolean better(int w, int had) {
        if (had == 1024) return false;
        if (w == 1024) return true;
        if (w < 1024) return had > 1024 || w > had;
        return had > 1024 && w < had;
    }

    private static int widthOf(String url) {
        Matcher m = SIZE.matcher(url);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** Pure: the page's squad at one of our clubs, or the only one when the page names no club; null when it names others. */
    static Squad squadFor(Page p, String[] clubs) {
        Squad fallback = null;
        for (Squad q : p.squads) {
            if (q.clubs.isEmpty()) { if (fallback == null) fallback = q; continue; }
            if (sameClub(q.clubs, clubs)) return q;
        }
        return fallback;
    }

    // ---- the picture ---------------------------------------------------------------------------------------------------

    /**
     * Pure: the head's top row and its middle across, in a cut-out's top rows (ARGB, w wide, rows of an h tall picture):
     * the first row with a few solid pixels, and the middle of the solid run of the rows a little under it (12 % of
     * the height, the head and the top of the neck); null when the rows are all see-through.
     */
    static int[] head(int[] argb, int w, int rows, int h) {
        int top = -1;
        for (int y = 0; y < rows && top < 0; y++) {
            int n = 0;
            for (int x = 0; x < w && n < 3; x++) if ((argb[y * w + x] >>> 24) > 64) n++;
            if (n >= 3) top = y;
        }
        if (top < 0) return null;
        int band = Math.max(1, Math.round(h * 0.12f)), k = 0;
        int[] mids = new int[band];
        for (int y = top; y < Math.min(rows, top + band); y++) {
            int lo = -1, hi = -1;
            for (int x = 0; x < w; x++) {
                if ((argb[y * w + x] >>> 24) > 64) { if (lo < 0) lo = x; hi = x; }
            }
            if (lo >= 0) mids[k++] = (lo + hi) / 2;
        }
        int[] m = Arrays.copyOf(mids, k);
        Arrays.sort(m);
        return new int[]{top, k == 0 ? w / 2 : m[k / 2]};
    }

    /**
     * Pure: the square to cut from a La Liga cut-out (w x h, the head's top row and middle given) so it sits as a Premier
     * League one does: {x, y, side}. La Liga's show the player to the waist with room at the sides (the head about a
     * quarter of the width across); the Premier League's are closer (a third), the head 1 % from the top and in the
     * middle. The side is 71 % of the height, the same scale for the 1024 x 1113 and the 1024 x 1024 ones. Parts of
     * the square outside the picture are left see-through.
     */
    static int[] cut(int w, int h, int top, int mid) {
        int side = Math.round(h * SIDE);
        return new int[]{mid - side / 2, top - Math.round(side * HEAD_ROOM), side};
    }

    /** The cut-out cut square to the Premier League's framing, OUT across; null when no head is found. */
    static Bitmap frame(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight(), rows = Math.min(h, Math.round(h * 0.4f));
        int[] px = new int[w * rows];
        src.getPixels(px, 0, w, 0, 0, w, rows);
        int[] hd = head(px, w, rows, h);
        if (hd == null) return null;
        int[] r = cut(w, h, hd[0], hd[1]);
        int sx0 = Math.max(0, r[0]), sy0 = Math.max(0, r[1]), sx1 = Math.min(w, r[0] + r[2]), sy1 = Math.min(h, r[1] + r[2]);
        if (sx1 <= sx0 || sy1 <= sy0) return null;
        float k = OUT / (float) r[2];
        Bitmap out = Bitmap.createBitmap(OUT, OUT, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, new Rect(sx0, sy0, sx1, sy1),
                new RectF((sx0 - r[0]) * k, (sy0 - r[1]) * k, (sx1 - r[0]) * k, (sy1 - r[1]) * k),
                new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        return out;
    }

    /**
     * The player's cut-out as the line-ups take it (a data: URL, WebP with its see-through, as the Premier League's are
     * made), or null. From the cache while kept, else his page (the addresses of slugs() in turn, within the game's
     * budget) and its picture. A miss is kept only when every page was answered (not for a failed or skipped fetch).
     */
    public static String photo(String name, String last, String[] clubs, File cacheDir, Budget budget) {
        String full = name == null || name.trim().isEmpty() ? (last == null ? "" : last) : name;
        String key = PlPhotos.fold(full);
        if (key.isEmpty()) return null;
        String club = clubs == null || clubs.length == 0 ? "" : clubCore(clubs[0]);
        key = key + "-" + club;
        if (key.length() > 90) key = key.substring(0, 90);
        File dir = cacheDir == null ? null : new File(cacheDir, "laliga2");
        File hit = dir == null ? null : new File(dir, key + ".webp"), miss = dir == null ? null : new File(dir, key + ".none");
        long now = System.currentTimeMillis();
        try {
            if (hit != null && hit.isFile() && now - hit.lastModified() < KEEP_MS) {
                return "data:image/webp;base64," + Base64.encodeToString(Files.readAllBytes(hit.toPath()), Base64.NO_WRAP);
            }
            if (miss != null && miss.isFile() && now - miss.lastModified() < MISS_MS) return null;
        } catch (Exception ignored) { }
        boolean sure = true;
        for (String s : slugs(name, last)) {
            if (budget != null && !budget.take()) { sure = false; break; }
            String html;
            try {
                html = page(s);
            } catch (Exception e) {
                sure = false;   // the site slow or down: tried again at the next game
                break;
            }
            if (html == null) continue;   // no player by that name
            Page p = parse(html);
            if (!samePlayer(p.names, name, last)) continue;
            Squad q = squadFor(p, clubs == null ? new String[0] : clubs);
            if (q == null) continue;   // a namesake at another club
            for (String u : q.cutOuts) {
                try {
                    byte[] webp = picture(u);
                    if (webp == null) continue;
                    if (dir != null && (dir.isDirectory() || dir.mkdirs())) write(hit, webp);
                    return "data:image/webp;base64," + Base64.encodeToString(webp, Base64.NO_WRAP);
                } catch (Exception e) {
                    sure = false;
                }
            }
            break;   // the right page, without a cut-out to use: no other will have one
        }
        if (sure && dir != null && (dir.isDirectory() || dir.mkdirs())) {
            try {
                write(miss, new byte[0]);
            } catch (IOException ignored) { }
        }
        return null;
    }

    /** The page at La Liga's address for a slug, or null when there is none (404); an IOException when it could not be had. */
    private static String page(String slug) throws IOException, InterruptedException {
        PAGES.acquire();
        try {
            return new String(Net.get(PAGE + slug, UA, 10_000, 20_000, 3 << 20), StandardCharsets.UTF_8);
        } catch (Net.HttpError e) {
            if (e.code == 404 || e.code == 410) return null;
            throw e;
        } finally {
            PAGES.release();
        }
    }

    /**
     * Whether a cut-out is La Liga's stand-in for a player not yet photographed: a black figure in the kit (its head and
     * shoulders all but black). Such a player keeps FotMob's face on the drawn body instead.
     */
    static boolean silhouette(Bitmap bm) {
        int w = bm.getWidth(), h = (int) (bm.getHeight() * 0.4), step = Math.max(1, Math.min(w, h) / 48);
        int[] px = new int[w];
        long sum = 0, n = 0;
        for (int y = 0; y < h; y += step) {
            bm.getPixels(px, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x += step) {
                int c = px[x];
                if ((c >>> 24) < 200) continue;
                sum += ((c >> 16) & 255) * 299 + ((c >> 8) & 255) * 587 + (c & 255) * 114;
                n++;
            }
        }
        return silhouette(n, sum);
    }

    /** Pure: n opaque samples whose summed luma (×1000) says the figure's top is all but black. */
    static boolean silhouette(long n, long lumaSum) {
        return n >= 50 && lumaSum / (n * 1000.0) < 28;
    }

    /** A cut-out fetched, checked (a real one, see-through round the player) and framed, as WebP; null when it is not one. */
    private static byte[] picture(String url) throws IOException, InterruptedException {
        byte[] b = Net.get(url, UA, 10_000, 20_000, 8 << 20);
        DECODE.acquire();
        try {
            Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length);
            if (bm == null) return null;
            try {
                if (!PlPhotos.cutOut(bm) || silhouette(bm)) return null;
                Bitmap f = frame(bm);
                if (f == null) return null;
                byte[] out = BroadcastData.webp(f);
                f.recycle();
                return out;
            } finally {
                bm.recycle();
            }
        } finally {
            DECODE.release();
        }
    }

    private static void write(File f, byte[] b) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(b);
        }
    }
}
