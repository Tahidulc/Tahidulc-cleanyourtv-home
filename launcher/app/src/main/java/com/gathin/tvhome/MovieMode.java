package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Movie mode (the Control Centre's switch, off until turned on): before a film opened from Home, about ten minutes of
 * Whitebrook Road Cinema, as a night at the pictures goes (the family's reference: a big cinema's own intro). The
 * curtains part and the welcome puts tonight's film on the marquee; adverts for things nobody can buy; the family's
 * teams' next games, sold as a big night; three trailers of what is new on this TV's apps, each after a film leader's
 * countdown, with cheeky lines between; a word from the management; then, the lights out, the intro: words spaced wide,
 * one line at a time, the name, and the film. The room's lights (Tuya, when set up) dim as it starts and go out for the
 * intro; Cinema mode then brings them back up as its own (paused, stopped or the credits). Back twice skips the lot: the
 * lights out and the film at once. Drawn by its own page (assets: cinema/preshow.html), the trailers playing in Home's own
 * YouTube player under it; a film only (a series opens as it always has).
 */
final class MovieMode {
    private static final String FILE = "moviemode";
    /** A trailer is cut short after this long, so the whole thing stays about ten minutes. */
    static final long TRAILER_MOST_MS = 150_000L;
    /** Back twice within this long skips to the film. */
    static final long SKIP_MS = 1_600L;
    /** The lights' level while the pre-show plays (per cent). */
    static final int DIM = 30;
    private static final ExecutorService LIGHTS = Executors.newSingleThreadExecutor();

    // ---- the switch -----------------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean on(Context c) {
        return prefs(c).getBoolean("on", false);
    }

    static void toggle(Context c) {
        prefs(c).edit().putBoolean("on", !on(c)).apply();
    }

    static String words(Context c) {
        return on(c) ? "On: ten minutes of trailers first" : "Off";
    }

    /** Whether a film about to open from Home gets the pre-show first. */
    static boolean wants(Context c, PagesV2.Title t) {
        return t != null && !t.tv && on(c) && shown == null;
    }

    // ---- the programme (pure, for the tests) ------------------------------------------------------------------------

    /** Adverts for things nobody can buy: {product, line, line, price, small print, picture, colour, colour}. */
    static final String[][] ADS = {
        {"Whitebrook Road Popcorn", "Lovingly microwaved for exactly 2 minutes 40.", "Hand-shaken by whoever's nearest.", "FREE*",
            "*if you make it yourself. Crumbs not refundable.", "🍿", "#ff4d6d", "#ffb347"},
        {"Sofa Supreme", "Now with 40% more cushion", "and 100% more lost coins.", "£0.00", "Remote not included. It's down the side.",
            "🛋️", "#3a7bd5", "#00d2ff"},
        {"The Pause Button", "For snack runs, toilet trips", "and \"wait, who's that?\" moments.", "FREE WITH EVERY FILM",
            "Use responsibly. Never during the best bit.", "⏸️", "#7f53ac", "#647dee"},
        {"Blanket Fort Deluxe", "Planning permission", "not required.", "3 CUSHIONS", "Pets may claim it first.", "🏰", "#11998e", "#38ef7d"},
        {"Midnight Fridge Raid", "The snack you weren't going to have.", "Now in family size.", "PRICELESS",
            "Not available after brushing your teeth.", "🍫", "#8e2de2", "#ff6a88"},
        {"Remote Finder Pro", "It's under the cushion.", "It's always under the cushion.", "£0.00",
            "Batteries sold separately. Batteries always flat.", "🔍", "#f7971e", "#ffd200"},
        {"Hot Choc Express", "Delivered from the kitchen.", "By you. During the trailers.", "2 MARSHMALLOWS",
            "Spills are part of the experience.", "☕", "#c94b4b", "#f09819"},
    };

    /** Cheeky lines between the trailers: {line, its punchline, picture}. */
    static final String[][] BETWEEN = {
        {"That trailer was rated S", "for Snacks.", "🍿"},
        {"Please keep your feet off the coffee table.", "Unless it's comfy. Then fine.", "🦶"},
        {"Fun fact: nobody in this room has ever", "finished the popcorn before the film starts.", "🍿"},
        {"Coming soon to this sofa:", "the argument about what to watch next.", "🙃"},
        {"If you need the toilet,", "now is the time. We'll wait. (We won't.)", "🚽"},
    };

    /**
     * Pure: the parts in order, for up to three trailers: the welcome, two adverts, the teams' games (when there are
     * any), each trailer after its card with a cheeky line or an advert between them, the word from the management, and
     * the intro. A slot whose trailer is not ready when it comes is passed over, with its card.
     */
    static List<String> order(boolean sport) {
        List<String> o = new ArrayList<>();
        Collections.addAll(o, "welcome", "ad", "ad");
        if (sport) o.add("sport");
        Collections.addAll(o, "card", "trailer", "between", "card", "trailer", "ad", "card", "trailer", "phones", "intro");
        return o;
    }

    /** Pure: when a game kicks off, as a cinema's poster says it ("TONIGHT 8:00PM", "TOMORROW 3:00PM", "SAT 12:30PM"). */
    static String when(long kickoff, long now, ZoneId zone) {
        ZonedDateTime k = Instant.ofEpochMilli(kickoff).atZone(zone);
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        String time = k.format(DateTimeFormatter.ofPattern("h:mma", Locale.UK)).toUpperCase(Locale.UK);
        LocalDate d = k.toLocalDate();
        if (d.equals(today)) return (k.getHour() >= 17 ? "TONIGHT " : "TODAY ") + time;
        if (d.equals(today.plusDays(1))) return "TOMORROW " + time;
        return k.format(DateTimeFormatter.ofPattern("EEE", Locale.UK)).toUpperCase(Locale.UK) + " " + time;
    }

    // ---- showing --------------------------------------------------------------------------------------------------

    private static MovieMode shown;

    static boolean isOpen() {
        return shown != null;
    }

    static boolean key(KeyEvent e) {
        return shown != null && shown.onKey(e);
    }

    /** Taken down (Home went into the background): no film, the lights as they were. */
    static void close() {
        if (shown != null) shown.end(false);
    }

    /** The pre-show for film, then open (the film in its app). */
    static void start(HomeV2 h, PagesV2.Title film, Runnable open) {
        close();
        MovieMode m = new MovieMode(h, film, open);
        shown = m;
        m.build();
    }

    private final HomeV2 h;
    private final Context c;
    private final PagesV2.Title film;
    private final Runnable open;
    private final Random random = new Random();
    private FrameLayout overlay, frame;
    private WebView web;
    private List<String> plan;
    private int at = -1, ad, between, trailer;
    private boolean ended, ready;
    /** All three trailers looked for (how many there are is known). */
    private volatile boolean prepared;
    private long lastBack;
    /** Pictures handed over before the page was ready: {key, data: URL}, given to it once it is. */
    private final List<String[]> early = new ArrayList<>();
    /** The trailers made ready (off the main thread): each {"id", "title", "label", "sub", "backdrop" key}. */
    private final List<JSONObject> trailers = Collections.synchronizedList(new ArrayList<>());
    /** The teams' next games, made ready: null until looked at, empty for none. */
    private volatile JSONArray games;
    /** The lights dimmed: {light, level before}. */
    private final List<Object[]> lit = new ArrayList<>();
    private final Runnable cutTrailer = this::trailerOver;
    private final List<String[]> ads = new ArrayList<>(), betweens = new ArrayList<>();

    private MovieMode(HomeV2 h, PagesV2.Title film, Runnable open) {
        this.h = h;
        c = h.a;
        this.film = film;
        this.open = open;
        Collections.addAll(ads, ADS);
        Collections.shuffle(ads, random);
        Collections.addAll(betweens, BETWEEN);
        Collections.shuffle(betweens, random);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void build() {
        overlay = new FrameLayout(c);
        overlay.setBackgroundColor(Color.BLACK);
        overlay.setClickable(true);
        overlay.setKeepScreenOn(true);
        h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        frame = new FrameLayout(c);
        frame.setBackgroundColor(Color.BLACK);
        overlay.addView(frame, new FrameLayout.LayoutParams(h.px(1920), h.px(1080)));
        web = new WebView(c);
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setFocusable(false);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setSupportZoom(false);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(), "AndroidCinema");
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                if (web == null) return;
                JSONObject cfg = new JSONObject();
                try {
                    // a slower TV draws the page smaller, scaled up to the screen
                    cfg.put("res", h.lite ? 0.667 : 1.0);
                } catch (Exception ignored) {
                    // drawn at full size
                }
                v.evaluateJavascript("CINEMA.init(" + cfg + ")", null);
            }
        });
        overlay.addView(web, new FrameLayout.LayoutParams(-1, -1));
        web.loadUrl("file:///android_asset/cinema/preshow.html");
        plan = order(true);
        lightsDim();
        prepare();
        AlertLog.note(c, "movie mode: the pre-show for " + film.title);
    }

    /** What the page says (its own thread): each onto the main one. */
    private final class Bridge {
        @JavascriptInterface public void ready() {
            h.main.post(() -> {
                if (ended || ready) return;
                ready = true;
                for (String[] i : early) give(i[0], i[1]);
                early.clear();
                next();
            });
        }
        @JavascriptInterface public void done(String kind) {
            h.main.post(() -> { if (!ended) next(); });
        }
        @JavascriptInterface public void lights(String level) {
            h.main.post(() -> { if (!ended && "off".equals(level)) lightsOff(); });
        }
        @JavascriptInterface public void log(String s) {
            android.util.Log.i("MovieMode", s);
        }
    }

    // ---- the parts in turn ----------------------------------------------------------------------------------------

    private void next() {
        if (ended || web == null) return;
        while (++at < plan.size()) {
            String part = plan.get(at);
            JSONObject p = part(part);
            if (p == null) continue;
            if ("trailer".equals(part)) playTrailer(p);
            else web.evaluateJavascript("CINEMA.play(" + p + ")", null);
            return;
        }
        end(true);
    }

    /** A part's words and pictures; null to pass it over (no games, a trailer not ready). */
    private JSONObject part(String kind) {
        JSONObject p = new JSONObject();
        try {
            p.put("kind", kind);
            switch (kind) {
                case "welcome":
                case "intro":
                    p.put("film", new JSONObject().put("title", film.title));
                    return p;
                case "ad": {
                    String[] a = ads.get(ad++ % ads.size());
                    return p.put("product", a[0]).put("line1", a[1]).put("line2", a[2]).put("price", a[3]).put("small", a[4])
                            .put("emoji", a[5]).put("bg1", a[6]).put("bg2", a[7]);
                }
                case "between": {
                    String[] b = betweens.get(between++ % betweens.size());
                    return p.put("line1", b[0]).put("line2", b[1]).put("emoji", b[2]);
                }
                case "sport": {
                    JSONArray g = games;
                    if (g == null || g.length() == 0) return null;
                    return p.put("fixtures", g);
                }
                case "card": {
                    // the next trailer that is ready, its card first; none ready: the card and its trailer passed over
                    if (trailer >= trailers.size()) { at++; return null; }
                    JSONObject t = trailers.get(trailer);
                    return p.put("label", t.optString("label")).put("title", t.optString("title")).put("sub", t.optString("sub"))
                            .put("backdrop", t.optString("backdrop")).put("n", trailer + 1).put("of", of());
                }
                case "trailer": {
                    if (trailer >= trailers.size()) return null;
                    JSONObject t = trailers.get(trailer++);
                    return new JSONObject(t.toString()).put("kind", "trailer").put("n", trailer).put("of", of());
                }
                default:
                    return p;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** How many trailers tonight: three while they are still being looked for. */
    private int of() {
        return prepared ? Math.min(3, trailers.size()) : 3;
    }

    /** The trailer full screen in Home's YouTube player, the page see-through over it with its marks. */
    private void playTrailer(JSONObject t) {
        web.evaluateJavascript("CINEMA.trailer(" + t + ")", null);
        h.main.removeCallbacks(cutTrailer);
        h.main.postDelayed(cutTrailer, TRAILER_MOST_MS);
        h.players.film(frame, t.optString("id"), new TilePlayer.Film() {
            @Override public void state(int s) {
                if (s == 0) h.main.post(MovieMode.this::trailerOver);   // it ended
            }
            @Override public void time(double atS, double length) { }
            @Override public void failed(int code) {
                h.main.post(MovieMode.this::trailerOver);
            }
            @Override public void gone() {
                h.main.post(MovieMode.this::trailerOver);
            }
        });
    }

    private void trailerOver() {
        if (ended) return;
        h.main.removeCallbacks(cutTrailer);
        // only once for each trailer (its end, a refusal and the cut can all come)
        if (at < 0 || at >= plan.size() || !"trailer".equals(plan.get(at))) return;
        h.players.filmOff();
        next();
    }

    // ---- what it shows, got ready while it plays ------------------------------------------------------------------

    private void prepare() {
        String key = h.tmdbKey;
        h.io.execute(() -> {
            // the teams' next games, crests on the page
            JSONArray out = new JSONArray();
            try {
                for (ScoresTicker.Game g : nextGames()) {
                    String hk = "c" + out.length() + "h", ak = "c" + out.length() + "a";
                    image(hk, ScoresTicker.cachedLogo(c, g.homeLogo, 160), false);
                    image(ak, ScoresTicker.cachedLogo(c, g.awayLogo, 160), false);
                    out.put(new JSONObject().put("home", g.homeName != null ? g.homeName : g.home)
                            .put("away", g.awayName != null ? g.awayName : g.away).put("homeCrest", hk).put("awayCrest", ak)
                            .put("when", when(g.kickoff, System.currentTimeMillis(), ZoneId.systemDefault()))
                            .put("channel", g.broadcasts.isEmpty() ? "" : g.broadcasts.get(0)).put("comp", g.league == null ? "" : g.league));
                }
            } catch (Exception ignored) {
                // no games part
            }
            games = out;
            if (key == null || key.isEmpty()) { prepared = true; return; }
            // three trailers of what is new on this TV's apps (not tonight's film)
            try {
                for (PagesV2.Title t : TrailerReel.titles(h, TrailerReel.ALL)) {
                    if (ended || trailers.size() >= 3) break;
                    if (t.id == film.id) continue;
                    String ids = t.youtube != null ? t.youtube : PagesV2.trailer(key, t);
                    t.youtube = ids;
                    if (ids == null || ids.isEmpty()) continue;
                    String label = "NEW TO YOUR SOFA";
                    try {
                        JSONObject d = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/" + (t.tv ? "tv/" : "movie/") + t.id
                                + "?language=en-GB&append_to_response=watch/providers&api_key=" + key));
                        String[] w = FilmNight.where(h, YourShows.gb(d));
                        if (w != null && w[1] != null && w[2] != null) label = "NEW ON " + w[2].toUpperCase(Locale.UK);
                    } catch (Exception ignored) {
                        // the plain label
                    }
                    String bk = "b" + trailers.size();
                    if (t.backdrop != null) image(bk, ScoresTicker.cachedLogo(c, t.backdrop, 1280), true);
                    trailers.add(new JSONObject().put("id", ids.split(",")[0]).put("title", t.title).put("label", label)
                            .put("sub", (t.tv ? "Series" : "Film") + (t.year != null && !t.year.isEmpty() ? " · " + t.year : ""))
                            .put("backdrop", bk));
                }
            } catch (Exception ignored) {
                // fewer trailers
            }
            prepared = true;
        });
    }

    /** The family's teams' next games, today and the three days after: the first three still to kick off. */
    private List<ScoresTicker.Game> nextGames() {
        List<ScoresTicker.Game> out = new ArrayList<>();
        Set<String> slugs = GoalWatchService.alertSlugs(c);
        slugs.addAll(Players.leagues(c));
        if (slugs.isEmpty()) return out;
        String[] leagues = slugs.toArray(new String[0]);
        long now = System.currentTimeMillis();
        LocalDate d = LocalDate.now(ZoneId.systemDefault());
        for (int i = 0; i < 4 && out.size() < 3 && !ended; i++) {
            try {
                for (ScoresTicker.Game g : ScoresTicker.load(leagues, d.plusDays(i))) {
                    if (g.id == null || g.kickoff <= now || !GoalWatchService.followsAnywhere(c, g)) continue;
                    boolean had = false;
                    for (ScoresTicker.Game o : out) had |= g.id.equals(o.id);
                    if (!had) out.add(g);
                }
            } catch (RuntimeException ignored) {
                // that day as far as it was read
            }
        }
        out.sort((a, b) -> Long.compare(a.kickoff, b.kickoff));
        return out.size() > 3 ? new ArrayList<>(out.subList(0, 3)) : out;
    }

    /** A picture onto the page under its key (a data: URL: the page reads nothing from the web or the files). */
    private void image(String key, Bitmap b, boolean photo) {
        if (b == null) return;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(photo ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG, photo ? 82 : 100, out);
        String url = "data:image/" + (photo ? "jpeg" : "png") + ";base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        h.main.post(() -> {
            if (web == null) return;
            if (ready) give(key, url);
            else early.add(new String[]{key, url});
        });
    }

    private void give(String key, String url) {
        if (web != null) web.evaluateJavascript("CINEMA.image(" + JSONObject.quote(key) + "," + JSONObject.quote(url) + ")", null);
    }

    // ---- the lights -----------------------------------------------------------------------------------------------

    private void lightsDim() {
        if (!TuyaCloud.configured(c)) return;
        LIGHTS.execute(() -> {
            try {
                List<TuyaCloud.Light> all = TuyaCloud.lights(c);
                synchronized (lit) {
                    for (TuyaCloud.Light l : all) {
                        if (!l.online || !l.on) continue;
                        lit.add(new Object[]{l, l.level});
                        if (l.brightCode != null && (l.level < 0 || l.level > DIM)) TuyaCloud.level(c, l, DIM);
                    }
                }
                AlertLog.note(c, "movie mode: " + lit.size() + " light(s) down to " + DIM + "% for the pre-show");
            } catch (Exception e) {
                AlertLog.note(c, "movie mode: the lights could not be read (" + e.getMessage() + ")");
            }
        });
    }

    private boolean out;

    private void lightsOff() {
        if (out) return;
        out = true;
        LIGHTS.execute(() -> {
            synchronized (lit) {
                for (Object[] s : lit) TuyaCloud.power(c, (TuyaCloud.Light) s[0], false);
            }
        });
    }

    /** Taken down without the film: the lights as they were. */
    private void lightsBack() {
        LIGHTS.execute(() -> {
            synchronized (lit) {
                for (Object[] s : lit) {
                    int before = (Integer) s[1];
                    TuyaCloud.level(c, (TuyaCloud.Light) s[0], before < 0 ? 100 : before);
                }
                lit.clear();
            }
        });
    }

    // ---- keys and the end -----------------------------------------------------------------------------------------

    private boolean onKey(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
                long now = SystemClock.uptimeMillis();
                if (now - lastBack < SKIP_MS) {
                    // twice: straight to the film, the lights out as for the intro
                    lightsOff();
                    end(true);
                } else {
                    lastBack = now;
                    if (web != null) web.evaluateJavascript("CINEMA.hint()", null);
                }
            }
            return true;
        }
        // the remote's own: nothing in the pre-show moves (the volume and the like go on to the TV)
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_DPAD_UP
                || code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    /** Over: the page and the player gone; with the film (film), the lights handed to Cinema mode and the film opened. */
    private void end(boolean withFilm) {
        if (ended) return;
        ended = true;
        if (shown == this) shown = null;
        h.main.removeCallbacks(cutTrailer);
        h.players.filmOff();
        if (web != null) {
            WebView w = web;
            web = null;
            w.evaluateJavascript("CINEMA.stop()", null);
            w.removeJavascriptInterface("AndroidCinema");
            if (w.getParent() instanceof ViewGroup) ((ViewGroup) w.getParent()).removeView(w);
            w.destroy();
        }
        FrameLayout o = overlay;
        overlay = null;
        if (o != null && o.getParent() instanceof ViewGroup) ((ViewGroup) o.getParent()).removeView(o);
        if (withFilm) {
            // the lights to Cinema mode once the dimming and the switching off are through (Back twice can come first)
            LIGHTS.execute(() -> {
                List<Object[]> copy;
                synchronized (lit) { copy = new ArrayList<>(lit); }
                h.main.post(() -> Cinema.adopt(c, copy));
            });
            AlertLog.note(c, "movie mode: on to " + film.title);
            open.run();
        } else {
            lightsBack();
        }
        h.paint();
    }
}
