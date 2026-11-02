package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * One search for everything (launcher v2), from the magnifier in Home's top bar or the remote's Search key. On the
 * left the words, typed on an on-screen keyboard (or a keyboard remote) or said to the microphone, and the last
 * searches; on the right, as they are found, a top result and rows: on TV today and tomorrow (Sky's guide, live first),
 * the next games of a club or country searched for (ESPN, with the channel from Sky's guide, UHD first), films and
 * series in the apps on this TV (TMDB, where they stream in Britain), BBC iPlayer, and YouTube's own search.
 * OK on a programme on now tunes it; on one later, or a game, sets a reminder (a game on now opens the Live Centre on
 * it); on a title, opens it in its app. Back closes it.
 */
final class SearchPanel {
    static final int REQ_VOICE = 4711;
    private static final String YOUTUBE = "com.google.android.youtube.tv", IPLAYER = "bbc.iplayer.android";
    private static final int TOP = 0, TV = 1, GAMES = 2, TITLES = 3, PLAYER = 4, WEB = 5;
    private static final int NAVY = 0xFF10157E, YELLOW = 0xFFFFD04D;

    private final HomeV2 h;
    private final Context c;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Typeface heavy, semi, regular;
    private boolean open;
    private FrameLayout overlay, list;
    private LinearLayout results;
    private TextView field, status;
    private final StringBuilder query = new StringBuilder();
    /** The keyboard's rows (row 0 the microphone, then the letters and digits, the space row, the last searches). */
    private final List<List<View>> keyRows = new ArrayList<>();
    private final List<List<Runnable>> keyActs = new ArrayList<>();
    private LinearLayout recentRow;
    private int kr = 1, kc = 0;
    private boolean inResults;
    private int sec, item;
    private final List<Section> sections = new ArrayList<>();
    private int gen;
    private final Runnable search = this::runSearch;

    private static final class Item {
        String title, sub, when, image, logo, crestHome, crestAway;
        int service;
        /** When it starts (a programme, a game); for the top result. */
        long startMs = Long.MAX_VALUE;
        boolean live, poster;
        Runnable ok;
        String okWord;
        /** True when OK leaves Home (a channel, an app): the panel closes behind it. */
        boolean leaves = true;
        TextView whenView;
    }

    private static final class Section {
        String head;
        int type;
        List<Item> items = new ArrayList<>();
        LinearLayout row;
        List<View> views = new ArrayList<>();
        View block;
    }

    SearchPanel(HomeV2 home) {
        h = home;
        c = home.a;
        heavy = c.getResources().getFont(R.font.outfit_extrabold);
        semi = c.getResources().getFont(R.font.inter_semibold);
        regular = c.getResources().getFont(R.font.inter_regular);
    }

    boolean isOpen() {
        return open;
    }

    // ---- open and close -------------------------------------------------------------------------------------------

    void show() {
        if (open) return;
        open = true;
        overlay = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF10157E, 0xFF1B279F, 0xFF2838C0});
        overlay.setBackground(bg);
        overlay.setClickable(true);
        buildLeft();
        list = new FrameLayout(c);
        list.setClipChildren(true);
        results = new LinearLayout(c);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setClipChildren(false);
        list.addView(results, new FrameLayout.LayoutParams(-1, -2));
        place(overlay, list, 720, 134, 1140, 926);
        status = h.text(22, regular, 0xB3FFFFFF);
        status.setText("Type a programme, a team, a film or a series");
        place(overlay, status, 726, 92, 1100, 34);
        h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(220).start();
        kr = 1;
        kc = 0;
        inResults = false;
        paint();
        // Sky's guide read ahead, so the first search finds the TV at once.
        io.execute(() -> guideEvents());
    }

    void hide() {
        if (!open) return;
        open = false;
        main.removeCallbacks(search);
        gen++;
        View o = overlay;
        overlay = null;
        o.animate().alpha(0f).setDuration(180).withEndAction(() -> h.root.removeView(o)).start();
        h.paint();
    }

    /** What the microphone heard (MainActivity's answer from Android's speech recogniser). */
    void heard(String words) {
        if (!open || words == null || words.trim().isEmpty()) return;
        query.setLength(0);
        query.append(words.trim().toLowerCase(Locale.UK));
        field.setText(query);
        runSearch();
    }

    // ---- the left: field, keyboard, last searches ------------------------------------------------------------------

    private void buildLeft() {
        keyRows.clear();
        keyActs.clear();
        LinearLayout box = new LinearLayout(c);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(h.px(26), 0, h.px(12), 0);
        box.setBackground(h.fill(39, Color.WHITE));
        ImageView glass = new ImageView(c);
        glass.setImageResource(R.drawable.ic_search);
        glass.setColorFilter(NAVY);
        box.addView(glass, new LinearLayout.LayoutParams(h.px(30), h.px(30)));
        field = h.text(34, semi, NAVY);
        field.setSingleLine(true);
        field.setHint("Search");
        field.setHintTextColor(0x8010157E);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, -2, 1f);
        fp.leftMargin = h.px(14);
        box.addView(field, fp);
        TextView mic = new TextView(c);
        mic.setBackground(h.fill(28, NAVY));
        mic.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_mic, 0, 0, 0);
        mic.setGravity(Gravity.CENTER);
        mic.setPadding(h.px(14), 0, 0, 0);
        mic.setContentDescription("Search by voice");
        box.addView(mic, new LinearLayout.LayoutParams(h.px(56), h.px(56)));
        place(overlay, box, 96, 74, 560, 78);
        keyRows.add(Collections.singletonList(mic));
        keyActs.add(Collections.singletonList(this::voice));

        String keys = "abcdefghijklmnopqrstuvwxyz1234567890";
        for (int r = 0; r < 6; r++) {
            List<View> row = new ArrayList<>();
            List<Runnable> acts = new ArrayList<>();
            for (int k = 0; k < 7 && r * 7 + k < keys.length(); k++) {
                char ch = keys.charAt(r * 7 + k);
                TextView key = keyView(String.valueOf(ch), 26);
                place(overlay, key, 96 + k * 80, 180 + r * 68, 70, 58);
                row.add(key);
                acts.add(() -> type(String.valueOf(ch)));
            }
            keyRows.add(row);
            keyActs.add(acts);
        }
        String[] words = {"Space", "Delete", "Clear"};
        Runnable[] doWhat = {() -> type(" "), this::delete, this::clear};
        List<View> srow = new ArrayList<>();
        List<Runnable> sacts = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            TextView key = keyView(words[k], 20);
            place(overlay, key, 96 + k * 190, 594, 180, 58);
            srow.add(key);
            sacts.add(doWhat[k]);
        }
        keyRows.add(srow);
        keyActs.add(sacts);

        TextView rl = h.text(15, semi, 0xB3FFFFFF);
        rl.setText("RECENT");
        rl.setLetterSpacing(0.14f);
        place(overlay, rl, 96, 680, 560, 24);
        recentRow = new LinearLayout(c);
        recentRow.setOrientation(LinearLayout.VERTICAL);
        place(overlay, recentRow, 96, 712, 560, 320);
        List<View> rrow = new ArrayList<>();
        List<Runnable> racts = new ArrayList<>();
        for (String r : recents()) {
            TextView chip = h.text(19, regular, Color.WHITE);
            chip.setText(r);
            chip.setSingleLine(true);
            chip.setPadding(h.px(18), h.px(9), h.px(18), h.px(9));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
            cp.bottomMargin = h.px(10);
            recentRow.addView(chip, cp);
            rrow.add(chip);
            racts.add(() -> setQuery(r));
        }
        if (!rrow.isEmpty()) {
            keyRows.add(rrow);
            keyActs.add(racts);
        }
    }

    private TextView keyView(String label, float size) {
        TextView key = h.text(size, semi, Color.WHITE);
        key.setText(label);
        key.setGravity(Gravity.CENTER);
        return key;
    }

    private void place(FrameLayout parent, View v, int x, int y, int w, int hgt) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(h.px(w), h.px(hgt));
        p.leftMargin = h.px(x);
        p.topMargin = h.px(y);
        parent.addView(v, p);
    }

    private void type(String s) {
        if (query.length() >= 40) return;
        query.append(s);
        changed();
    }

    private void delete() {
        if (query.length() > 0) query.setLength(query.length() - 1);
        changed();
    }

    private void clear() {
        query.setLength(0);
        changed();
    }

    private void setQuery(String q) {
        query.setLength(0);
        query.append(q);
        field.setText(query);
        runSearch();
    }

    private void changed() {
        field.setText(query);
        main.removeCallbacks(search);
        if (query.toString().trim().length() >= 2) main.postDelayed(search, 600);
        else {
            gen++;
            showSections(new ArrayList<>());
            status.setText("Type a programme, a team, a film or a series");
        }
    }

    private void voice() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search");
        try {
            h.a.startActivityForResult(i, REQ_VOICE);
        } catch (RuntimeException e) {
            status.setText("Voice search isn't available on this TV");
        }
    }

    private List<String> recents() {
        String all = prefs().getString("recent", "");
        List<String> out = new ArrayList<>();
        for (String s : all.split("\n")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    private void remember(String q) {
        q = q.trim().toLowerCase(Locale.UK);
        if (q.length() < 2) return;
        List<String> all = recents();
        all.remove(q);
        all.add(0, q);
        while (all.size() > 6) all.remove(all.size() - 1);
        prefs().edit().putString("recent", String.join("\n", all)).apply();
    }

    private SharedPreferences prefs() {
        return c.getSharedPreferences("search", Context.MODE_PRIVATE);
    }

    // ---- searching -----------------------------------------------------------------------------------------------------

    private void runSearch() {
        String q = query.toString().trim();
        if (q.length() < 2) return;
        int my = ++gen;
        status.setText("Searching for “" + q + "”…");
        io.execute(() -> {
            Future<List<Item>> tv = pool.submit(() -> tv(q));
            Future<List<Item>> games = pool.submit(() -> games(q));
            Future<List<Item>> titles = pool.submit(() -> titles(q));
            Future<List<Item>> player = pool.submit(() -> iplayer(q));
            List<Section> found = new ArrayList<>();
            List<Item> t = get(tv), g = get(games), f = get(titles), p = get(player);
            // The top result: a programme on now named for it, else a game in the next day and a half, else the first
            // programme, title or iPlayer find.
            Item top = null;
            String nq = norm(q);
            for (Item i : t) if (i.live && norm(i.title).contains(nq)) { top = i; break; }
            long soon = System.currentTimeMillis() + 36 * 3600_000L;
            if (top == null) for (Item i : g) if (i.live || i.startMs < soon) { top = i; break; }
            if (top == null && !t.isEmpty()) top = t.get(0);
            if (top == null && !f.isEmpty()) top = f.get(0);
            if (top == null && !p.isEmpty()) top = p.get(0);
            if (top != null) {
                t.remove(top);
                g.remove(top);
                f.remove(top);
                p.remove(top);
                found.add(section("TOP RESULT", TOP, Collections.singletonList(top)));
            }
            if (!t.isEmpty()) found.add(section("ON TV", TV, t));
            if (!g.isEmpty()) found.add(section("NEXT GAMES", GAMES, g));
            if (!f.isEmpty()) found.add(section("FILMS & SERIES IN YOUR APPS", TITLES, f));
            if (!p.isEmpty()) found.add(section("BBC iPLAYER", PLAYER, p));
            if (h.installed(YOUTUBE)) {
                Item y = new Item();
                y.title = "Search YouTube for “" + q + "”";
                y.sub = "Opens YouTube's own search";
                y.ok = () -> h.searchTitle(YOUTUBE, q, false, 0, null);
                found.add(section("YOUTUBE", WEB, Collections.singletonList(y)));
            }
            main.post(() -> {
                if (my != gen || !open) return;
                showSections(found);
                status.setText(found.isEmpty() || found.size() == 1 && found.get(0).type == WEB ? "Nothing found for “" + q + "”" : "");
            });
        });
    }

    private static <T> List<T> get(Future<List<T>> f) {
        try {
            return new ArrayList<>(f.get(20, TimeUnit.SECONDS));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static Section section(String head, int type, List<Item> items) {
        Section s = new Section();
        s.head = head;
        s.type = type;
        s.items = new ArrayList<>(items);
        return s;
    }

    static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.UK).replaceAll("[^a-z0-9]+", " ").trim();
    }

    // -- on TV: Sky's guide, today and tomorrow, for the TV guide's channels --

    private static final Map<String, List<Object[]>> days = new HashMap<>();
    private static final Map<String, Long> daysAt = new HashMap<>();

    /** Every programme on the guide's channels today and tomorrow: {sid, title, synopsis, start, end, programme id}. */
    private List<Object[]> guideEvents() {
        List<Object[]> out = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        List<String[]> chans = HomeV2.guideChannels(c);
        for (int d = 0; d < 2; d++) {
            String date = today.plusDays(d).format(DateTimeFormatter.BASIC_ISO_DATE);
            List<Object[]> got;
            synchronized (days) {
                Long at = daysAt.get(date);
                got = at != null && System.currentTimeMillis() - at < 30 * 60_000L ? days.get(date) : null;
            }
            if (got == null) {
                got = new ArrayList<>();
                // Sky's guide answers for at most 20 channels a time.
                for (int from = 0; from < chans.size(); from += 20) {
                    StringBuilder ids = new StringBuilder();
                    for (int k = from; k < Math.min(chans.size(), from + 20); k++) ids.append(ids.length() > 0 ? "," : "").append(chans.get(k)[0]);
                    try {
                        JSONArray sched = new JSONObject(ScoresTicker.get("https://awk.epgsky.com/hawk/linear/schedule/" + date + "/" + ids))
                                .optJSONArray("schedule");
                        for (int i = 0; sched != null && i < sched.length(); i++) {
                            JSONObject s = sched.getJSONObject(i);
                            JSONArray ev = s.optJSONArray("events");
                            for (int k = 0; ev != null && k < ev.length(); k++) {
                                JSONObject e = ev.getJSONObject(k);
                                long st = e.optLong("st") * 1000L;
                                got.add(new Object[]{s.optString("sid"), e.optString("t"), e.optString("sy"), st, st + e.optLong("d") * 1000L,
                                        e.optString("programmeuuid")});
                            }
                        }
                    } catch (Exception ignored) {
                        // that block of channels missing this time
                    }
                }
                synchronized (days) {
                    days.put(date, got);
                    daysAt.put(date, System.currentTimeMillis());
                }
            }
            out.addAll(got);
        }
        return out;
    }

    private List<Item> tv(String q) {
        String nq = norm(q);
        long now = System.currentTimeMillis();
        Map<String, String[]> chans = new HashMap<>();
        for (String[] ch : HomeV2.guideChannels(c)) chans.put(ch[0], ch);
        List<Object[]> hits = new ArrayList<>();
        for (Object[] e : guideEvents()) {
            long end = (Long) e[4];
            if (end <= now) continue;
            String title = norm((String) e[1]);
            int score = title.equals(nq) ? 4 : (" " + title).contains(" " + nq) ? 3 : title.contains(nq) ? 2
                    : (" " + norm((String) e[2])).contains(" " + nq) ? 1 : 0;
            if (score > 0 && chans.containsKey((String) e[0])) hits.add(new Object[]{e, score});
        }
        hits.sort((x, y) -> {
            Object[] a = (Object[]) x[0], b = (Object[]) y[0];
            boolean la = (Long) a[3] <= now, lb = (Long) b[3] <= now;
            if (la != lb) return la ? -1 : 1;
            if ((Integer) x[1] != (Integer) y[1]) return (Integer) y[1] - (Integer) x[1];
            return Long.compare((Long) a[3], (Long) b[3]);
        });
        List<Item> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm");
        for (Object[] hit : hits) {
            Object[] e = (Object[]) hit[0];
            String title = (String) e[1];
            long st = (Long) e[3], en = (Long) e[4];
            if (!seen.add(norm(title) + "|" + (st / 60_000L))) continue;
            String[] ch = chans.get((String) e[0]);
            Item i = new Item();
            i.title = title;
            i.sub = ch[1];
            i.live = st <= now;
            i.startMs = st;
            i.when = i.live ? "On now · until " + Instant.ofEpochMilli(en).atZone(ZoneId.systemDefault()).format(hm) : day(st);
            String id = (String) e[5];
            i.image = id == null || id.isEmpty() ? null : "https://images.metadata.sky.com/pd-image/" + id + "/16-9/640";
            i.logo = ScoresTicker.skyLogo(ch[0], ch[1]);
            String name = ch[1];
            if (i.live) {
                i.okWord = "Watch now";
                i.ok = () -> h.tune(Collections.singletonList(name), i.logo);
            } else {
                i.okWord = "Remind me";
                i.leaves = false;
                i.ok = () -> {
                    boolean set = Reminders.toggle(c, title, st, name, i.logo);
                    if (i.whenView != null) i.whenView.setText(set ? "Reminder set · " + day(st) : day(st));
                };
            }
            out.add(i);
            if (out.size() >= 8) break;
        }
        return out;
    }

    /** "Today 19:00", "Tomorrow 20:00", "Sun 22 Nov 16:30". */
    private static String day(long at) {
        ZonedDateTime t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault());
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        String time = t.format(DateTimeFormatter.ofPattern("HH:mm"));
        if (t.toLocalDate().equals(today)) return "Today " + time;
        if (t.toLocalDate().equals(today.plusDays(1))) return "Tomorrow " + time;
        return t.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)) + " " + time;
    }

    // -- games: a club or country's next games, from ESPN --

    /** {id, name, short name, league slug, crest}, every club in the score bar's leagues, and England. */
    private static final List<String[]> teams = new ArrayList<>();
    private static final Map<String, String> CUPS = new HashMap<>();
    static {
        CUPS.put("eng", "eng.fa,eng.league_cup");
        CUPS.put("esp", "esp.copa_del_rey");
        CUPS.put("ita", "ita.coppa_italia");
        CUPS.put("ger", "ger.dfb_pokal");
        CUPS.put("fra", "fra.coupe_de_france");
        CUPS.put("sco", "sco.tennents,sco.cis");
    }

    private void loadTeams() {
        synchronized (teams) {
            if (!teams.isEmpty()) return;
            Set<String> ids = new HashSet<>();
            for (String l : c.getResources().getStringArray(R.array.score_leagues)) {
                String slug = l.split("\\|", 2)[0].trim();
                if (slug.startsWith("uefa.") || slug.endsWith(".fa") || slug.endsWith("_cup")) continue;
                try {
                    JSONObject root = new JSONObject(ScoresTicker.get("https://site.api.espn.com/apis/site/v2/sports/soccer/" + slug + "/teams"));
                    JSONArray ts = root.getJSONArray("sports").getJSONObject(0).getJSONArray("leagues").getJSONObject(0).getJSONArray("teams");
                    for (int i = 0; i < ts.length(); i++) {
                        JSONObject t = ts.getJSONObject(i).getJSONObject("team");
                        if (!ids.add(t.optString("id"))) continue;
                        JSONArray logos = t.optJSONArray("logos");
                        String crest = logos != null && logos.length() > 0 ? logos.getJSONObject(0).optString("href") : null;
                        teams.add(new String[]{t.optString("id"), t.optString("displayName"), t.optString("shortDisplayName"), slug, crest});
                    }
                } catch (Exception ignored) {
                    // that league's clubs missing this time
                }
            }
            teams.add(new String[]{"448", "England", "England", "fifa.friendly", "https://a.espncdn.com/i/teamlogos/countries/500/eng.png"});
        }
    }

    /** ESPN's kick-off, "2026-11-08T14:00Z" (no seconds) or a full ISO time; 0 when unreadable. */
    static long espnTime(String d) {
        if (d == null || d.isEmpty()) return 0;
        try {
            return Instant.parse(d).toEpochMilli();
        } catch (RuntimeException e) {
            try {
                return OffsetDateTime.parse(d, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmX")).toInstant().toEpochMilli();
            } catch (RuntimeException e2) {
                return 0;
            }
        }
    }

    private List<Item> games(String q) {
        loadTeams();
        String nq = norm(q);
        List<String[]> best = new ArrayList<>();
        int bestScore = 0;
        synchronized (teams) {
            for (String[] t : teams) {
                String n = norm(t[1]), s = norm(t[2]);
                int score = n.equals(nq) || s.equals(nq) ? 3 : n.startsWith(nq) || s.startsWith(nq) ? 2 : (" " + n).contains(" " + nq) ? 1 : 0;
                if (score == 0 || nq.length() < 3 && score < 2) continue;
                if (score > bestScore) { best.clear(); bestScore = score; }
                if (score == bestScore && best.size() < 2) best.add(t);
            }
        }
        long now = System.currentTimeMillis();
        String[] channels = c.getResources().getStringArray(R.array.football_tv_channels);
        Map<String, String> names = new HashMap<>();
        for (String l : c.getResources().getStringArray(R.array.score_leagues)) {
            String[] p = l.split("\\|", 2);
            names.put(p[0].trim(), p.length > 1 ? p[1].trim() : p[0].trim());
        }
        names.put("fifa.friendly", "FRIENDLY");
        names.put("uefa.nations", "NATIONS LEAGUE");
        names.put("fifa.worldq.uefa", "WC QUALIFIER");
        names.put("uefa.euroq", "EURO QUALIFIER");
        Map<String, Object[]> events = new LinkedHashMap<>();
        for (String[] t : best) {
            List<String> slugs = new ArrayList<>();
            if ("448".equals(t[0])) {
                Collections.addAll(slugs, "fifa.friendly", "uefa.nations", "fifa.worldq.uefa", "uefa.euroq");
            } else {
                slugs.add(t[3]);
                Collections.addAll(slugs, "uefa.champions", "uefa.europa", "uefa.europa.conf");
                String cups = CUPS.get(t[3].split("\\.")[0]);
                if (cups != null) Collections.addAll(slugs, cups.split(","));
            }
            for (String slug : slugs) {
                try {
                    JSONObject root = new JSONObject(ScoresTicker.get("https://site.api.espn.com/apis/site/v2/sports/soccer/" + slug
                            + "/teams/" + t[0] + "/schedule?fixture=true"));
                    JSONArray ev = root.optJSONArray("events");
                    for (int i = 0; ev != null && i < ev.length(); i++) {
                        JSONObject e = ev.getJSONObject(i);
                        long at = espnTime(e.optString("date"));
                        if (at == 0) continue;
                        if (at < now - 2 * 3600_000L) continue;
                        events.put(e.optString("id"), new Object[]{e, slug, at});
                    }
                } catch (Exception ignored) {
                    // that competition missing this time
                }
            }
        }
        List<Object[]> sorted = new ArrayList<>(events.values());
        sorted.sort((x, y) -> Long.compare((Long) x[2], (Long) y[2]));
        List<Item> out = new ArrayList<>();
        for (Object[] row : sorted) {
            if (out.size() >= 4) break;
            try {
                JSONObject e = (JSONObject) row[0];
                String slug = (String) row[1];
                long at = (Long) row[2];
                JSONObject comp = e.getJSONArray("competitions").getJSONObject(0);
                JSONArray cs = comp.getJSONArray("competitors");
                String home = null, away = null, hc = null, ac = null;
                for (int k = 0; k < cs.length(); k++) {
                    JSONObject x = cs.getJSONObject(k);
                    JSONObject team = x.getJSONObject("team");
                    JSONArray logos = team.optJSONArray("logos");
                    String crest = logos != null && logos.length() > 0 ? logos.getJSONObject(0).optString("href") : null;
                    if ("home".equals(x.optString("homeAway"))) { home = team.optString("displayName"); hc = crest; }
                    else { away = team.optString("displayName"); ac = crest; }
                }
                if (home == null || away == null) continue;
                Item i = new Item();
                i.title = home + " v " + away;
                JSONObject venue = comp.optJSONObject("venue");
                i.sub = names.getOrDefault(slug, slug.toUpperCase(Locale.UK)) + (venue != null && !venue.optString("fullName").isEmpty() ? " · " + venue.optString("fullName") : "");
                i.startMs = at;
                i.live = at <= now;
                i.when = i.live ? "On now" : day(at);
                i.crestHome = hc;
                i.crestAway = ac;
                Object[] show = TvGuide.show(channels, home, away, at);
                String channel = null;
                if (show != null) {
                    TvGuide.Channel up = TvGuide.uhd(c, new TvGuide.Channel((String) show[0], (String) show[1]));
                    channel = up.name;
                    i.logo = up.logo;
                    i.when += " · " + channel;
                }
                String id = e.optString("id"), title = i.title, ch = channel, logo = i.logo;
                if (i.live || at - now < 15 * 60_000L) {
                    i.okWord = "Live Centre";
                    i.ok = () -> LiveCentre.open(c, id);
                } else {
                    i.okWord = "Remind me";
                    i.leaves = false;
                    i.ok = () -> {
                        boolean set = Reminders.toggle(c, title, at, ch, logo);
                        if (i.whenView != null) i.whenView.setText((set ? "Reminder set · " : "") + day(at) + (ch != null ? " · " + ch : ""));
                    };
                }
                out.add(i);
            } catch (Exception ignored) {
                // a game without its teams: left out
            }
        }
        return out;
    }

    // -- films and series: TMDB, in the apps on this TV that show them in Britain --

    private List<Item> titles(String q) {
        String key = h.tmdbKey;
        if (key == null || key.isEmpty()) return new ArrayList<>();
        List<Item> out = new ArrayList<>();
        try {
            JSONArray res = new JSONObject(ScoresTicker.get("https://api.themoviedb.org/3/search/multi?api_key=" + key
                    + "&language=en-GB&region=GB&include_adult=false&query=" + URLEncoder.encode(q, "UTF-8"))).optJSONArray("results");
            for (int i = 0; res != null && i < res.length() && out.size() < 8 && i < 14; i++) {
                JSONObject r = res.getJSONObject(i);
                String type = r.optString("media_type");
                if (!"movie".equals(type) && !"tv".equals(type)) continue;
                String poster = r.optString("poster_path", "");
                if (poster.isEmpty() || "null".equals(poster)) continue;
                int id = r.optInt("id");
                JSONObject gb = new JSONObject(ScoresTicker.get("https://api.themoviedb.org/3/" + type + "/" + id + "/watch/providers?api_key=" + key))
                        .optJSONObject("results");
                gb = gb == null ? null : gb.optJSONObject("GB");
                JSONArray flat = gb == null ? null : gb.optJSONArray("flatrate");
                String pkg = null, provider = null;
                for (int k = 0; flat != null && k < flat.length() && pkg == null; k++) {
                    String pid = String.valueOf(flat.getJSONObject(k).optInt("provider_id"));
                    String app = HomeV2.serviceApp(pid);
                    if (app != null && h.installed(app)) { pkg = app; provider = pid; }
                }
                if (pkg == null) continue;
                boolean tv = "tv".equals(type);
                Item it = new Item();
                it.title = tv ? r.optString("name") : r.optString("title");
                String date = r.optString(tv ? "first_air_date" : "release_date", "");
                it.sub = (date.length() >= 4 ? date.substring(0, 4) + " · " : "") + (tv ? "Series" : "Film");
                it.image = "https://image.tmdb.org/t/p/w342" + poster;
                it.poster = true;
                it.service = HomeV2.serviceLogo(provider);
                String backdrop = r.optString("backdrop_path", "");
                String art = backdrop.isEmpty() || "null".equals(backdrop) ? it.image : "https://image.tmdb.org/t/p/w1280" + backdrop;
                String app = pkg, name = it.title;
                it.okWord = "Open";
                it.ok = () -> h.searchTitle(app, name, tv, id, art);
                out.add(it);
            }
        } catch (Exception ignored) {
            // TMDB unreachable: no titles this time
        }
        return out;
    }

    // -- BBC iPlayer: its own search --

    private List<Item> iplayer(String q) {
        if (!h.installed(IPLAYER)) return new ArrayList<>();
        List<Item> out = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(ScoresTicker.get("https://ibl.api.bbci.co.uk/ibl/v1/new-search?rights=tv&lang=en&availability=available&q="
                    + URLEncoder.encode(q, "UTF-8")));
            JSONArray res = root.optJSONObject("new_search") == null ? null : root.getJSONObject("new_search").optJSONArray("results");
            for (int i = 0; res != null && i < res.length() && out.size() < 6; i++) {
                JSONObject r = res.getJSONObject(i);
                Item it = new Item();
                it.title = r.optString("title");
                JSONObject syn = r.optJSONObject("synopses");
                it.sub = syn == null ? "" : syn.optString("small");
                JSONObject img = r.optJSONObject("images");
                String std = img == null ? "" : img.optString("standard");
                it.image = std.isEmpty() ? null : std.replace("{recipe}", "640x360");
                String name = it.title, art = it.image;
                it.okWord = "Open in iPlayer";
                it.ok = () -> h.searchTitle(IPLAYER, name, true, 0, art);
                out.add(it);
            }
        } catch (Exception ignored) {
            // iPlayer unreachable: none this time
        }
        return out;
    }

    // ---- showing the results -----------------------------------------------------------------------------------------

    private void showSections(List<Section> found) {
        sections.clear();
        results.removeAllViews();
        results.setTranslationY(0);
        for (Section s : found) {
            LinearLayout block = new LinearLayout(c);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setClipChildren(false);
            TextView head = h.text(16, semi, Color.WHITE);
            head.setText(s.head);
            head.setLetterSpacing(0.14f);
            block.addView(head);
            FrameLayout clip = new FrameLayout(c);
            clip.setClipChildren(true);
            s.row = new LinearLayout(c);
            s.row.setClipChildren(false);
            s.row.setPadding(h.px(6), h.px(10), h.px(6), h.px(10));
            for (Item i : s.items) {
                View card = card(s.type, i);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) card.getLayoutParams();
                lp.rightMargin = h.px(16);
                s.row.addView(card, lp);
                s.views.add(card);
            }
            clip.addView(s.row, new FrameLayout.LayoutParams(-2, -2));
            block.addView(clip, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
            bp.bottomMargin = h.px(18);
            results.addView(block, bp);
            s.block = block;
            sections.add(s);
        }
        sec = 0;
        item = 0;
        if (sections.isEmpty()) inResults = false;
        paint();
    }

    private View card(int type, Item i) {
        switch (type) {
            case TOP: return topCard(i);
            case TITLES: return posterCard(i);
            case PLAYER: return wideCard(i, 320, 180);
            case WEB: return webCard(i);
            default: return rowCard(i, type == GAMES);
        }
    }

    private View topCard(Item i) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(h.fill(24, 0xFF0D1160));
        f.setClipToOutline(true);
        f.setOutlineProvider(h.round(24));
        ImageView pic = new ImageView(c);
        pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(h.px(i.poster ? 220 : 520), -1, Gravity.END);
        f.addView(pic, pp);
        load(pic, i.image, h.px(640), false);
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF0D1160, 0xFF0D1160, 0x000D1160}));
        f.addView(shade, new FrameLayout.LayoutParams(h.px(i.poster ? 920 : 720), -1));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(h.px(28), h.px(22), h.px(28), h.px(20));
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = h.text(14, semi, NAVY);
        badge.setText(i.live ? "ON NOW" : "TOP RESULT");
        badge.setLetterSpacing(0.12f);
        badge.setPadding(h.px(10), h.px(4), h.px(10), h.px(4));
        badge.setBackground(h.fill(6, i.live ? 0xFFE8112D : YELLOW));
        if (i.live) badge.setTextColor(Color.WHITE);
        top.addView(badge);
        addLogo(top, i, 26, 12);
        col.addView(top);
        LinearLayout teams = new LinearLayout(c);
        teams.setGravity(Gravity.CENTER_VERTICAL);
        if (i.crestHome != null) teams.addView(crest(i.crestHome, 56), new LinearLayout.LayoutParams(h.px(56), h.px(56)));
        TextView title = h.text(40, heavy, Color.WHITE);
        title.setText(i.title);
        title.setMaxLines(2);
        title.getPaint().setTextSkewX(i.crestHome != null ? -0.1f : 0f);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = i.crestHome != null ? h.px(14) : 0;
        tl.rightMargin = h.px(14);
        teams.addView(title, tl);
        if (i.crestAway != null) teams.addView(crest(i.crestAway, 56), new LinearLayout.LayoutParams(h.px(56), h.px(56)));
        LinearLayout.LayoutParams tm = new LinearLayout.LayoutParams(h.px(i.poster ? 820 : 640), -2);
        tm.topMargin = h.px(12);
        col.addView(teams, tm);
        TextView sub = h.text(19, regular, 0xE6FFFFFF);
        sub.setText(join(i.when, i.sub));
        sub.setMaxLines(1);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(h.px(i.poster ? 820 : 640), -2);
        sp.topMargin = h.px(10);
        col.addView(sub, sp);
        i.whenView = sub;
        TextView act = h.text(18, semi, NAVY);
        act.setText("OK  " + (i.okWord == null ? "Open" : i.okWord));
        act.setPadding(h.px(18), h.px(8), h.px(20), h.px(8));
        act.setBackground(h.fill(22, Color.WHITE));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, -2);
        ap.topMargin = h.px(14);
        col.addView(act, ap);
        f.addView(col, new FrameLayout.LayoutParams(-1, -1));
        f.setLayoutParams(new LinearLayout.LayoutParams(h.px(1110), h.px(250)));
        return f;
    }

    private View rowCard(Item i, boolean game) {
        LinearLayout f = new LinearLayout(c);
        f.setBackground(h.fill(18, 0x14FFFFFF));
        f.setClipToOutline(true);
        f.setOutlineProvider(h.round(18));
        if (!game) {
            ImageView pic = new ImageView(c);
            pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(pic, new LinearLayout.LayoutParams(h.px(240), -1));
            load(pic, i.image, h.px(480), false);
        }
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(h.px(16), h.px(12), h.px(16), h.px(10));
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (i.live) {
            TextView l = h.text(12, semi, Color.WHITE);
            l.setText("LIVE");
            l.setPadding(h.px(7), h.px(2), h.px(7), h.px(2));
            l.setBackground(h.fill(5, 0xFFE8112D));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = h.px(10);
            top.addView(l, lp);
        }
        if (game) {
            TextView comp = h.text(14, semi, 0xCCFFFFFF);
            comp.setText(i.sub);
            comp.setSingleLine(true);
            top.addView(comp, new LinearLayout.LayoutParams(0, -2, 1f));
        } else addLogo(top, i, 20, 0);
        col.addView(top);
        LinearLayout mid = new LinearLayout(c);
        mid.setGravity(Gravity.CENTER_VERTICAL);
        if (game && i.crestHome != null) mid.addView(crest(i.crestHome, 34), new LinearLayout.LayoutParams(h.px(34), h.px(34)));
        TextView title = h.text(20, semi, Color.WHITE);
        title.setText(i.title);
        title.setMaxLines(2);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = game && i.crestHome != null ? h.px(10) : 0;
        tl.rightMargin = game ? h.px(10) : 0;
        mid.addView(title, tl);
        if (game && i.crestAway != null) mid.addView(crest(i.crestAway, 34), new LinearLayout.LayoutParams(h.px(34), h.px(34)));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.topMargin = h.px(6);
        col.addView(mid, mp);
        TextView when = h.text(15, regular, 0xCCFFFFFF);
        when.setText(i.when);
        when.setSingleLine(true);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, -2);
        wp.topMargin = h.px(6);
        col.addView(when, wp);
        i.whenView = when;
        if (game) addLogo(col, i, 18, 0);
        f.addView(col, new LinearLayout.LayoutParams(-1, -1));
        f.setLayoutParams(new LinearLayout.LayoutParams(h.px(game ? 420 : 540), h.px(game ? 150 : 136)));
        return f;
    }

    private View posterCard(Item i) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(h.fill(14, 0xFF0B0F55));
        f.setClipToOutline(true);
        f.setOutlineProvider(h.round(14));
        ImageView pic = new ImageView(c);
        pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
        f.addView(pic, new FrameLayout.LayoutParams(-1, -1));
        load(pic, i.image, h.px(400), false);
        if (i.service != 0) {
            ImageView svc = new ImageView(c);
            svc.setImageResource(i.service);
            svc.setScaleType(ImageView.ScaleType.FIT_START);
            svc.setPadding(h.px(8), h.px(6), h.px(8), h.px(6));
            svc.setBackground(h.fill(8, 0xB3000000));
            FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(h.px(110), h.px(34), Gravity.TOP | Gravity.START);
            sp.leftMargin = h.px(8);
            sp.topMargin = h.px(8);
            f.addView(svc, sp);
        }
        TextView title = h.text(16, semi, Color.WHITE);
        title.setText(i.title);
        title.setMaxLines(2);
        title.setShadowLayer(h.px(8), 0, h.px(2), 0xCC000000);
        title.setPadding(h.px(10), h.px(30), h.px(10), h.px(10));
        title.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0xD9000000}));
        f.addView(title, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        f.setLayoutParams(new LinearLayout.LayoutParams(h.px(190), h.px(285)));
        return f;
    }

    private View wideCard(Item i, int w, int hgt) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(h.fill(14, 0xFF0B0F55));
        f.setClipToOutline(true);
        f.setOutlineProvider(h.round(14));
        ImageView pic = new ImageView(c);
        pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
        f.addView(pic, new FrameLayout.LayoutParams(-1, -1));
        load(pic, i.image, h.px(w * 2), false);
        TextView title = h.text(17, semi, Color.WHITE);
        title.setText(i.title);
        title.setMaxLines(2);
        title.setPadding(h.px(12), h.px(30), h.px(12), h.px(10));
        title.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0xD9000000}));
        f.addView(title, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        f.setLayoutParams(new LinearLayout.LayoutParams(h.px(w), h.px(hgt)));
        return f;
    }

    private View webCard(Item i) {
        LinearLayout f = new LinearLayout(c);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setPadding(h.px(18), 0, h.px(22), 0);
        f.setBackground(h.fill(18, 0x14FFFFFF));
        ImageView icon = new ImageView(c);
        try {
            icon.setImageDrawable(c.getPackageManager().getApplicationIcon(YOUTUBE));
        } catch (Exception ignored) {
            // no icon
        }
        f.addView(icon, new LinearLayout.LayoutParams(h.px(48), h.px(48)));
        TextView t = h.text(20, semi, Color.WHITE);
        t.setText(i.title);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.leftMargin = h.px(16);
        f.addView(t, tp);
        f.setLayoutParams(new LinearLayout.LayoutParams(-2, h.px(84)));
        return f;
    }

    private void addLogo(LinearLayout into, Item i, int tall, int left) {
        if (i.logo == null) return;
        ImageView logo = new ImageView(c);
        logo.setAdjustViewBounds(true);
        logo.setMaxHeight(h.px(tall));
        logo.setMaxWidth(h.px(300));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, h.px(tall));
        lp.leftMargin = h.px(left);
        into.addView(logo, lp);
        load(logo, i.logo, h.px(300), true);
    }

    private ImageView crest(String url, int size) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        load(v, url, h.px(size) * 2, false);
        return v;
    }

    /** A picture fetched off the main thread (the launcher's cache), into its view when it arrives. */
    private void load(ImageView v, String url, int size, boolean trim) {
        if (url == null) return;
        int my = gen;
        pool.execute(() -> {
            Bitmap b = ScoresTicker.cachedLogo(c, url, size);
            if (b != null && trim) b = HomeV2.trim(b);
            Bitmap got = b;
            main.post(() -> { if (got != null && my == gen) v.setImageBitmap(got); });
        });
    }

    private static String join(String a, String b) {
        if (a == null || a.isEmpty()) return b == null ? "" : b;
        return b == null || b.isEmpty() ? a : a + "  ·  " + b;
    }

    // ---- the remote --------------------------------------------------------------------------------------------------

    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP) hide();
            return true;
        }
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) press();
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        // A keyboard remote, or the remote's own digits: typed straight in.
        if (code == KeyEvent.KEYCODE_DEL) { delete(); return true; }
        int ch = e.getUnicodeChar();
        if (ch > 0 && (Character.isLetterOrDigit(ch) || ch == ' ')) { type(String.valueOf((char) Character.toLowerCase(ch))); return true; }
        move(code);
        return true;
    }

    private void move(int code) {
        if (!inResults) {
            int cols = keyRows.get(kr).size();
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && kc > 0) kc--;
            else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (kc < cols - 1) kc++;
                else if (!sections.isEmpty()) { inResults = true; item = Math.min(item, sections.get(sec).items.size() - 1); }
            } else if (code == KeyEvent.KEYCODE_DPAD_UP && kr > 0) {
                kc = scale(kc, cols, keyRows.get(kr - 1).size());
                kr--;
            } else if (code == KeyEvent.KEYCODE_DPAD_DOWN && kr < keyRows.size() - 1) {
                kc = scale(kc, cols, keyRows.get(kr + 1).size());
                kr++;
            }
        } else {
            Section s = sections.get(sec);
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (item > 0) item--;
                else inResults = false;
            } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && item < s.items.size() - 1) item++;
            else if (code == KeyEvent.KEYCODE_DPAD_UP && sec > 0) { sec--; item = Math.min(item, sections.get(sec).items.size() - 1); }
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN && sec < sections.size() - 1) { sec++; item = Math.min(item, sections.get(sec).items.size() - 1); }
        }
        paint();
    }

    private static int scale(int col, int from, int to) {
        if (from <= 1 || to <= 1) return 0;
        return Math.round(col * (to - 1f) / (from - 1f));
    }

    private void press() {
        if (!inResults) {
            keyActs.get(kr).get(kc).run();
            paint();
            return;
        }
        Item i = sections.get(sec).items.get(item);
        remember(query.toString());
        if (i.ok == null) return;
        if (i.leaves) hide();
        i.ok.run();
    }

    /** The focus drawn: a lit key, or a lit card (its row moved along and the results up to keep it in view). */
    private void paint() {
        if (!open) return;
        for (int r = 0; r < keyRows.size(); r++) {
            for (int k = 0; k < keyRows.get(r).size(); k++) {
                View v = keyRows.get(r).get(k);
                boolean lit = !inResults && r == kr && k == kc;
                if (r == 0) {
                    v.setBackground(h.fill(28, lit ? 0xFFE8112D : NAVY));
                    continue;
                }
                GradientDrawable bg = h.fill(14, lit ? Color.WHITE : 0x1AFFFFFF);
                bg.setStroke(Math.max(1, h.px(1)), lit ? Color.WHITE : 0x24FFFFFF);
                v.setBackground(bg);
                if (v instanceof TextView) ((TextView) v).setTextColor(lit ? NAVY : Color.WHITE);
            }
        }
        for (int s = 0; s < sections.size(); s++) {
            Section sc = sections.get(s);
            for (int k = 0; k < sc.views.size(); k++) {
                View v = sc.views.get(k);
                boolean lit = inResults && s == sec && k == item;
                if (v instanceof FrameLayout) ((FrameLayout) v).setForeground(lit ? h.ring(sc.type == TOP ? 24 : 16) : null);
                else v.setForeground(lit ? h.ring(18) : null);
                v.animate().scaleX(lit ? 1.04f : 1f).scaleY(lit ? 1.04f : 1f).setDuration(140).start();
            }
        }
        if (inResults && sec < sections.size()) {
            Section sc = sections.get(sec);
            // Along the row: the card in view; down the page: the row near the top.
            View v = sc.views.get(item);
            float left = v.getLeft(), right = v.getRight(), shown = h.px(1120), now = -sc.row.getTranslationX();
            float to = now;
            if (right - now > shown) to = right - shown + h.px(12);
            if (left - now < 0) to = Math.max(0, left - h.px(6));
            sc.row.animate().translationX(-to).setDuration(200).start();
            float top = sc.block.getTop();
            results.animate().translationY(-Math.max(0, top - h.px(20))).setDuration(220).start();
        } else if (!inResults) {
            results.animate().translationY(0).setDuration(220).start();
        }
    }
}
