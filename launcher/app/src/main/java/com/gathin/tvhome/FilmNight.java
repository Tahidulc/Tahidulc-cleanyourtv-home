package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Film night (the theme's "film_night", on unless set false): a film planned from its panel for a start time tonight
 * (FilmNightPanel), kept in the app's preferences so a restart keeps it. Ten minutes before, a reminder: the Sky-style
 * bar out of the scores box on Home, a card in the corner over an app (ScoreCard), OK opening the film in the
 * streaming app on this TV that carries it in the UK (else the store that sells it; never anything else). While it
 * plays, when asked: the room's lights dim as it starts (Cinema mode, with Tuya's lights), and pop-ups wait until ten
 * minutes after it ends or its credits come (NotifyPrefs), the starred clubs' goals still showing.
 */
final class FilmNight {
    private FilmNight() { }

    private static final String FILE = "filmnight";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    static final int PINK = 0xFFD81B73, PINK_DEEP = 0xFF8E0E4A;

    /** One planned night. */
    static final class Plan {
        int id;
        String title = "", poster, backdrop;
        int runtime;
        /** Its start, as a time in milliseconds. */
        long start;
        boolean dim, remind = true, quiet = true, told;
        /** The app on this TV that streams it (or the store that sells it), and TMDB's provider id for its logo. */
        String app, provider, service;
        /** When the credits were seen (Cinema), 0 until then. */
        long credits;

        long end() {
            return start + runtime * 60_000L;
        }

        JSONObject json() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id).put("title", title).put("poster", poster).put("backdrop", backdrop).put("runtime", runtime)
                        .put("start", start).put("dim", dim).put("remind", remind).put("quiet", quiet).put("told", told)
                        .put("app", app).put("provider", provider).put("service", service).put("credits", credits);
            } catch (Exception ignored) {
                // a value it cannot take is left out
            }
            return o;
        }

        static Plan of(JSONObject o) {
            Plan p = new Plan();
            p.id = o.optInt("id");
            p.title = o.optString("title", "");
            p.poster = o.optString("poster", null);
            p.backdrop = o.optString("backdrop", null);
            p.runtime = o.optInt("runtime", 120);
            p.start = o.optLong("start");
            p.dim = o.optBoolean("dim");
            p.remind = o.optBoolean("remind", true);
            p.quiet = o.optBoolean("quiet", true);
            p.told = o.optBoolean("told");
            p.app = o.optString("app", null);
            p.provider = o.optString("provider", null);
            p.service = o.optString("service", null);
            p.credits = o.optLong("credits");
            if ("null".equals(p.app)) p.app = null;
            if ("null".equals(p.provider)) p.provider = null;
            if ("null".equals(p.service)) p.service = null;
            return p;
        }
    }

    /** The plan in memory (read once from the preferences); loaded false until then. */
    private static Plan plan;
    private static boolean loaded;

    static boolean on(Context c) {
        return Config.themeFlag(c, "film_night", true);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** The night planned, or null. */
    static synchronized Plan get(Context c) {
        if (!loaded) {
            loaded = true;
            String s = prefs(c).getString("plan", null);
            try {
                plan = s == null ? null : Plan.of(new JSONObject(s));
            } catch (Exception e) {
                plan = null;
            }
        }
        return plan;
    }

    /** The night for this film, or null when none is planned for it. */
    static Plan forFilm(Context c, int id) {
        Plan p = get(c);
        return p != null && p.id == id && id != 0 ? p : null;
    }

    static synchronized void set(Context c, Plan p) {
        plan = p;
        loaded = true;
        if (p == null) prefs(c).edit().remove("plan").apply();
        else prefs(c).edit().putString("plan", p.json().toString()).apply();
        AlertLog.note(c, p == null ? "film night: cancelled" : "film night: " + p.title + " at " + clock(p.start)
                + (p.dim ? ", lights" : "") + (p.remind ? ", reminder" : "") + (p.quiet ? ", quiet pop-ups" : ""));
    }

    static String clock(long ms) {
        return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm", Locale.UK));
    }

    static LocalDateTime local(long ms) {
        return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    static long millis(LocalDateTime t) {
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** Whether pop-ups wait now for a film night (NotifyPrefs asks as each one comes up). */
    static boolean quietNow(Context c) {
        Plan p = get(c);
        return p != null && p.quiet && on(c) && FilmNightPlan.quiet(System.currentTimeMillis(), p.start, p.end(), p.credits);
    }

    /** Until when, for the alert log and Today's alerts. */
    static String quietWords(Context c) {
        Plan p = get(c);
        return p == null ? "" : "Quiet for film night until " + clock(FilmNightPlan.quietUntil(p.start, p.end(), p.credits));
    }

    /** Cinema mode is wanted for tonight's film: from half an hour before the start to half an hour after the end. */
    static boolean dimNow(Context c) {
        Plan p = get(c);
        if (p == null || !p.dim || !on(c)) return false;
        long now = System.currentTimeMillis();
        return now >= p.start - 30 * 60_000L && now <= p.end() + 30 * 60_000L;
    }

    /** Cinema mode saw a film's credits: the quiet ends ten minutes after (when this is tonight's film night). */
    static void credits(Context c) {
        Plan p = get(c);
        long now = System.currentTimeMillis();
        if (p == null || p.credits > 0 || now < p.start + 20 * 60_000L || now > p.end() + 30 * 60_000L) return;
        p.credits = now;
        set(c, p);
    }

    /**
     * Every minute (the alerts' service, and Home's clock): the reminder when it is due, and a night long over let go.
     */
    static void tick(Context c) {
        Plan p = get(c);
        if (p == null) return;
        long now = System.currentTimeMillis();
        if (FilmNightPlan.over(now, p.start, p.end(), p.credits)) { set(c, null); return; }
        if (!on(c) || !p.remind || !FilmNightPlan.remindDue(now, p.start, p.told)) return;
        synchronized (FilmNight.class) {
            if (p.told) return;
            p.told = true;
        }
        set(c, p);
        Context app = c.getApplicationContext();
        IO.execute(() -> {
            ScoreCard.Data d = card(app, p);
            MAIN.post(() -> ScoreCard.show(app, d));
        });
    }

    /**
     * The reminder: the pink band with the film's own logo, FILM NIGHT, "Verity starts at 20:00", how long until, the
     * lights, the end and bed; OK to start it now in its app. Off the main thread (the logo is fetched).
     */
    static ScoreCard.Data card(Context c, Plan p) {
        ScoreCard.Data d = new ScoreCard.Data();
        d.kind = "filmnight";
        d.notify = "filmnight";
        d.band = PINK;
        d.bandDeep = PINK_DEEP;
        d.labelColour = 0xFFB3135F;
        d.label = "FILM NIGHT";
        d.wideMark = true;
        d.showMs = 20_000L;
        Drawable icon = c.getDrawable(R.drawable.ic_v2_movies);
        if (icon != null) { icon = icon.mutate(); icon.setTint(0xFFFFFFFF); d.labelIcon = icon; }
        String key = Config.themeString(c, "tmdb_key", "").trim();
        if (p.id > 0 && !key.isEmpty()) d.leagueLogo = logo(c, key, p.id);
        d.headline = p.title + " starts at " + clock(p.start);
        long mins = Math.max(0, Math.round((p.start - System.currentTimeMillis()) / 60_000.0));
        long spare = FilmNightPlan.spare(local(p.start), p.runtime, FitsTonight.bedMinutes(c));
        StringBuilder line = new StringBuilder(mins <= 0 ? "Now" : "In " + mins + (mins == 1 ? " minute" : " minutes"));
        if (p.dim && TuyaCloud.configured(c)) line.append(" · the lights dim as it starts");
        line.append(" · ends ").append(clock(p.end())).append(", ").append(FilmNightPlan.bedWords(spare));
        d.line = line.toString();
        if (p.app != null) {
            d.okWords = "to start now";
            d.channel = p.service != null ? p.service : "Start now";
            int logo = HomeV2.serviceLogo(p.provider);
            if (logo != 0) {
                try {
                    d.channelLogo = android.graphics.BitmapFactory.decodeResource(c.getResources(), logo);
                } catch (RuntimeException | OutOfMemoryError ignored) {
                    // its name instead
                }
            }
            Bitmap art = p.backdrop == null ? null : ScoresTicker.cachedLogo(c, p.backdrop, 640);
            d.watch = () -> start(c, p, art);
        }
        return d;
    }

    /** The film in its app: the streaming app on this TV (or the store), through its search, as the panels open them. */
    static void start(Context c, Plan p, Bitmap art) {
        if (p.app == null) return;
        AppSearchService.search(c, p.app, p.title, false, art, null);
        GoalWatchService.onAppOpened(c, p.app);
    }

    private static Bitmap logo(Context c, String key, int id) {
        try {
            JSONArray logos = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/movie/" + id
                    + "/images?include_image_language=en,null&api_key=" + key)).optJSONArray("logos");
            for (int i = 0; logos != null && i < logos.length(); i++) {
                JSONObject l = logos.getJSONObject(i);
                if (!l.optString("file_path").endsWith(".png")) continue;
                Bitmap b = ScoresTicker.cachedLogo(c, "https://image.tmdb.org/t/p/w300" + l.optString("file_path"), 200);
                if (b != null) return HomeV2.trim(b);
            }
        } catch (Exception ignored) {
            // the band plain, its icon in the label
        }
        return null;
    }

    /**
     * Where a film streams on this TV (UK providers, gb): {app, TMDB provider id, the service's name}; a store when no
     * streaming app has it ({app, null, its name}); null when neither. Never anything but those apps.
     */
    static String[] where(HomeV2 h, JSONObject gb) {
        JSONArray flat = gb == null ? null : gb.optJSONArray("flatrate");
        for (int i = 0; flat != null && i < flat.length(); i++) {
            JSONObject o = flat.optJSONObject(i);
            String id = o == null ? null : String.valueOf(o.optInt("provider_id"));
            String app = id == null ? null : HomeV2.serviceApp(id);
            if (app != null && h.installed(app)) return new String[]{app, id, o.optString("provider_name", null)};
        }
        String store = HomeV2.storeFor(h, gb);
        if (store == null) return null;
        return new String[]{store, null, store.contains("amazon") ? "Prime Video" : store.contains("apple") ? "Apple TV"
                : store.contains("youtube") ? "YouTube" : "Google TV"};
    }

    /** adb only (--es test_filmnight auto): the reminder now, for tonight's plan or, with none, a sample one. */
    static void test(Context c, Intent intent) {
        if (intent == null || !intent.hasExtra("test_filmnight")) return;
        Plan had = get(c);
        Plan p;
        if (had != null) {
            p = Plan.of(had.json());
        } else {
            p = new Plan();
            p.title = "Verity";
            p.runtime = 117;
            p.start = millis(FilmNightPlan.choices(LocalDateTime.now()).get(0));
            p.dim = true;
            String app = null, provider = null;
            for (String id : new String[]{"8", "9", "337", "350", "38", "39", "531"}) {
                String a = HomeV2.serviceApp(id);
                if (a != null && ReleaseAlerts.installed(c, a)) { app = a; provider = id; break; }
            }
            p.app = app;
            p.provider = provider;
            p.service = provider == null ? null : "8".equals(provider) ? "Netflix" : "9".equals(provider) ? "Prime Video"
                    : "337".equals(provider) ? "Disney+" : "350".equals(provider) ? "Apple TV" : "38".equals(provider) ? "BBC iPlayer"
                    : "39".equals(provider) ? "NOW" : "Paramount+";
        }
        Context app = c.getApplicationContext();
        IO.execute(() -> {
            ScoreCard.Data d = card(app, p);
            MAIN.post(() -> ScoreCard.show(app, d));
        });
    }
}
