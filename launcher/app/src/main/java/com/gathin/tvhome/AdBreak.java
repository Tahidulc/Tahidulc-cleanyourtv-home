package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The ad-break screen before kick-off, over the channel in the player: when the game on the channel watched kicks off
 * within the hour and the channel's own logo goes (the adverts), a strip rises along the foot (BEFORE KICK-OFF, the
 * game, the kick-off counting down) with three cards in the line-up card's look taking turns through what is known
 * about the game, the line-up card itself going round on the right with the competition's table above it, and fans'
 * posts (Bluesky, Reddit: FanPosts) popping up on the left. All of it goes when the logo comes back, at kick-off, or
 * when the channel or the app changes. Only what is really known is shown: a widget with nothing behind it is left out.
 *
 * <p>The logo is told by its own learned shape (LogoShape), from pictures of its place of the player's window alone
 * (ScreenShots), about one a second while such a game's build-up is on; where such pictures cannot be had (before
 * Android 14) the screen never comes by itself. The remote stays with the player: Down goes into the cards (a white
 * ring on the card picked), Left and Right pick, Up and Down scroll it (it holds still while picked), OK does what
 * the widget offers (another game's channel, Match mode), Back hands the remote back. adb can bring it up over
 * anything (AdBreakReceiver) for ten minutes.
 */
final class AdBreak {
    private AdBreak() { }

    private static final String FILE = "ad_break", TAG = "AdBreak";
    /** The test's showing, at most. */
    static final long TEST_MS = 10 * 60_000L;
    /** Fan posts: read again this often while up; one more comes this often; at most this many at once. */
    static final long POSTS_EVERY_MS = 60_000L, POST_GAP_MS = 8_000L;
    static final int POSTS_SHOWN = 3;

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AdBreak");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private static Context app;
    private static float s = 1f;
    private static volatile List<ScoresTicker.Game> latest = new ArrayList<>();
    /** The game whose build-up is on the channel watched (the scores poll's word), its channels. */
    private static ScoresTicker.Game target;
    private static List<String> targetChannels = new ArrayList<>();
    private static boolean checking, looking, forced;
    private static long forcedUntil;
    private static final AdBreakLogic.Trigger trigger = new AdBreakLogic.Trigger();
    private static LogoShape.Here logoHere;
    private static String logoKey = "";

    // ---- the switch ----------------------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean on(Context c) {
        return prefs(c).getBoolean("on", true);
    }

    static void setOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("on", on).apply();
        if (!on) hide("switched off");
    }

    static boolean isShowing() {
        return strip != null;
    }

    // ---- which game, which channel (each scores poll) ------------------------------------------------------------------

    /** Each scores poll (main thread): the game whose build-up is on the channel watched, worked out off the main thread. */
    static void games(Context c, List<ScoresTicker.Game> games) {
        app = c.getApplicationContext();
        if (games != null) latest = new ArrayList<>(games);
        if (checking || games == null) return;
        long now = System.currentTimeMillis();
        List<ScoresTicker.Game> soon = new ArrayList<>();
        for (ScoresTicker.Game g : games) {
            if (g != null && g.id != null && g.state == 1 && g.kickoff > now && g.kickoff - now <= AdBreakLogic.WINDOW_MS) soon.add(g);
        }
        if (soon.isEmpty() || !on(app)) {
            target = null;
            if (!forced) hide("no game's build-up on");
            return;
        }
        checking = true;
        Context a = app;
        io.execute(() -> {
            ScoresTicker.Game found = null;
            List<String> names = new ArrayList<>();
            try {
                String[] channels = a.getResources().getStringArray(R.array.football_tv_channels);
                Map<String, List<String>> all = new HashMap<>(), own = new HashMap<>();
                for (ScoresTicker.Game g : soon) {
                    TvGuide.Watch w = TvGuide.watch(channels, g);
                    if (w == null || w.names.isEmpty()) continue;
                    all.put("game:" + g.id, w.names);
                    own.put("game:" + g.id, new ArrayList<>(w.own()));
                }
                OverlayPrompt.Found f = OverlayPrompt.watched(soon, MatchMode.game(a), InStep.tunedNames(a), own, all);
                if (f != null) {
                    found = f.game;
                    List<String> n = all.get("game:" + f.game.id);
                    if (n != null) names.addAll(n);
                }
            } catch (RuntimeException e) {
                // looked for again at the next poll
            }
            ScoresTicker.Game g = found;
            main.post(() -> {
                checking = false;
                boolean changed = g == null || target == null || !g.id.equals(target.id);
                target = g;
                targetChannels = names;
                if (g != null && changed) prefetch(g);
                if (g != null) startLooking();
            });
        });
    }

    // ---- the channel's logo, a look a second ----------------------------------------------------------------------------

    private static void startLooking() {
        if (looking) return;
        looking = true;
        main.post(lookRun);
    }

    private static final Runnable lookRun = AdBreak::look;

    private static void look() {
        Context c = app;
        ScoresTicker.Game g = target;
        long now = System.currentTimeMillis();
        if (forced) {
            if (now > forcedUntil) {
                forced = false;
                hide("the test's time is up");
            } else {
                main.postDelayed(lookRun, 1_000L);
                return;
            }
        }
        if (c == null || g == null) {
            looking = false;
            int a = trigger.step(now, true, false, false, 0, -1);
            if (a == AdBreakLogic.HIDE || isShowing()) hide("no game");
            return;
        }
        boolean front = BoardCover.playerInFront(c) && !GoalWatchService.homeInFront;
        List<String> tuned = InStep.tunedNames(c);
        boolean onChannel = !tuned.isEmpty() && InStep.onChannel(tuned.subList(0, 1), targetChannels);
        long toKick = g.kickoff - now;
        if (!on(c) || !front || !onChannel || toKick <= 0) {
            act(c, g, trigger.step(now, on(c), front, onChannel, toKick, -1));
            if (toKick <= 0) {
                looking = false;
                return;
            }
            main.postDelayed(lookRun, 2_000L);
            return;
        }
        if (!ScreenShots.available() || !ScreenShots.windowOnly()) {
            // No pictures of the player's window alone here: the screen never comes by itself.
            main.postDelayed(lookRun, 5_000L);
            return;
        }
        String channel = InStep.channel(c);
        BoardPlace place = BoardPlace.get(c, channel);
        int sw = 1920, sh = 1080;
        int bx = Math.round(place.lx * sw), by = Math.round(place.ly * sh), bw = Math.round(place.lw * sw), bh = Math.round(place.lh * sh);
        int m = LogoShape.MARGIN, mx = Math.max(m, Math.round(0.4f * bw)), my = Math.max(m, bh / 2 + m);
        int x0 = Math.max(0, bx - mx), y0 = Math.max(0, by - my), x1 = Math.min(sw, bx + bw + mx), y1 = Math.min(sh, by + bh + my);
        String key = ChannelOverlays.key(channel);
        if (logoHere == null || !logoHere.same(key, sw, sh, x0, y0, x1 - x0, y1 - y0, bx - x0, by - y0, bx - x0 + bw, by - y0 + bh)) {
            logoHere = new LogoShape.Here(new java.io.File(c.getFilesDir(), "adbreak_logos"), key, sw, sh, x0, y0, x1 - x0, y1 - y0,
                    bx - x0, by - y0, bx - x0 + bw, by - y0 + bh);
            if (!key.equals(logoKey)) trigger.set(isShowing());
            logoKey = key;
        }
        LogoShape.Here at = logoHere;
        RectF part = new RectF(x0 / (float) sw, y0 / (float) sh, x1 / (float) sw, y1 / (float) sh);
        boolean asked = ScreenShots.take(new RectF[]{part}, new int[][]{{at.w, at.h}}, (parts, when, own) -> {
            Bitmap got = parts[0];
            float presence = -1f;
            if (got != null && own && !ScreenShots.black(got)) {
                int[] px = new int[at.w * at.h];
                if (got.getWidth() == at.w && got.getHeight() == at.h) {
                    got.getPixels(px, 0, at.w, 0, 0, at.w, at.h);
                    // Learned from as it goes (the logo is up most of the time), then asked whether it is up now.
                    at.take(px, SystemClock.uptimeMillis());
                    presence = at.presence(px);
                }
            }
            if (got != null) got.recycle();
            ScoresTicker.Game now2 = target;
            if (now2 != null) act(c, now2, trigger.step(System.currentTimeMillis(), on(c), true, true, now2.kickoff - System.currentTimeMillis(), presence));
            main.postDelayed(lookRun, 1_000L);
        });
        if (!asked) main.postDelayed(lookRun, 400L);
    }

    private static void act(Context c, ScoresTicker.Game g, int what) {
        if (what == AdBreakLogic.SHOW) {
            android.util.Log.i(TAG, "the channel's logo gone: up");
            show(c, g);
        } else if (what == AdBreakLogic.HIDE) {
            android.util.Log.i(TAG, "away");
            hide("the logo back, or the game, channel or app changed");
        }
    }

    // ---- the test (adb) -------------------------------------------------------------------------------------------------

    /** adb: up now over anything for TEST_MS, for the game on the channel or else the soonest one to come. */
    static void test(Context c, boolean up) {
        app = c.getApplicationContext();
        if (!up) {
            forced = false;
            trigger.set(false);
            hide("asked over adb");
            return;
        }
        ScoresTicker.Game g = target;
        if (g == null) {
            long now = System.currentTimeMillis();
            for (ScoresTicker.Game x : latest) {
                if (x == null || x.id == null || x.state != 1 || x.kickoff <= now) continue;
                if (g == null || score(x) > score(g) || score(x) == score(g) && x.kickoff < g.kickoff) g = x;
            }
        }
        if (g == null) {
            android.util.Log.i(TAG, "test: no game to come in the scores");
            return;
        }
        forced = true;
        forcedUntil = System.currentTimeMillis() + TEST_MS;
        trigger.set(true);
        ScoresTicker.Game game = g;
        prefetch(game);
        show(app, game);
        if (!looking) {
            looking = true;
            main.postDelayed(lookRun, 1_000L);
        }
    }

    /** The test's pick: a Premier League game first, then any. */
    private static int score(ScoresTicker.Game g) {
        return "eng.1".equals(g.slug) ? 2 : 1;
    }

    // ---- what it shows: fetched once a game (off the main thread) ------------------------------------------------------

    /** Everything known about the game for the widgets. */
    static final class Pack {
        ScoresTicker.Game g;
        JSONObject summary;
        String homeForm = "", awayForm = "";
        List<AdBreakLogic.Row> table = new ArrayList<>();
        List<String[]> h2h = new ArrayList<>(), homeNext = new ArrayList<>(), awayNext = new ArrayList<>(), absent = new ArrayList<>();
        String referee;
        Bitmap mark;
        final Map<String, Bitmap> crests = new HashMap<>(), faces = new HashMap<>();
        String[][] leaders = new String[2][];
        String weatherHead, weatherLine;
        List<String> headlines = new ArrayList<>();
        Kits.Kit homeKit, awayKit;
        String homeKitName, awayKitName;
        String[] clip;
        Bitmap clipStill;
        List<Object[]> alsoNow = new ArrayList<>();
        LineupCard.Data lineups;
        long at;
    }

    private static Pack pack;
    private static String packFor = "";
    private static long packAt;

    /** The game's widgets fetched (once each ten minutes at most), so they are ready when the break comes. */
    private static void prefetch(ScoresTicker.Game g) {
        if (g == null) return;
        long now = System.currentTimeMillis();
        if (g.id.equals(packFor) && now - packAt < 10 * 60_000L) return;
        packFor = g.id;
        packAt = now;
        Context c = app;
        io.execute(() -> {
            Pack p = load(c, g);
            main.post(() -> {
                if (!g.id.equals(packFor)) return;
                pack = p;
                if (isShowing() && shownFor != null && shownFor.id.equals(g.id)) fill(c, p);
            });
        });
        fetchPosts(g);
    }

    private static String espn(String url) {
        try {
            return ScoresTicker.get(url);
        } catch (Exception e) {
            return null;
        }
    }

    static String crestUrl(String id) {
        return "https://a.espncdn.com/i/teamlogos/soccer/500/" + id + ".png";
    }

    private static Pack load(Context c, ScoresTicker.Game g) {
        Pack p = new Pack();
        p.g = g;
        p.at = System.currentTimeMillis();
        int crestPx = 96, facePx = 160;
        String base = "https://site.api.espn.com/apis/site/v2/sports/soccer/" + g.slug;
        try {
            String sum = espn(base + "/summary?event=" + g.id);
            if (sum != null) p.summary = new JSONObject(sum);
        } catch (Exception ignored) {
            // the widgets from it are left out
        }
        p.mark = GoalWatchService.bandMark(c, g, 96);
        // Form and fixtures, this competition only: each club's own list in it.
        for (int side = 0; side < 2; side++) {
            String id = side == 0 ? g.homeId : g.awayId;
            if (id == null) continue;
            try {
                String body = espn(base + "/teams/" + id + "/schedule");
                if (body == null) continue;
                JSONObject sched = new JSONObject(body);
                String form = AdBreakLogic.form(sched, id, g.kickoff, 5);
                List<String[]> next = AdBreakLogic.comingUp(sched, id, g.kickoff + 60_000L, 3);
                if (side == 0) { p.homeForm = form; p.homeNext = next; } else { p.awayForm = form; p.awayNext = next; }
            } catch (Exception ignored) {
                // no form for that club
            }
        }
        if (p.summary != null) {
            p.table = AdBreakLogic.table(p.summary);
            p.h2h = AdBreakLogic.headToHead(p.summary, g.homeId);
            p.referee = AdBreakLogic.referee(p.summary);
            p.absent = AdBreakLogic.absent(p.summary);
            p.leaders = leaders(p.summary, g);
            org.json.JSONObject news = p.summary.optJSONObject("news");
            org.json.JSONArray arts = news == null ? null : news.optJSONArray("articles");
            for (int i = 0; arts != null && i < arts.length() && p.headlines.size() < 3; i++) {
                String h = arts.optJSONObject(i) == null ? "" : arts.optJSONObject(i).optString("headline");
                if (!h.isEmpty() && FanFilter.ok(h)) p.headlines.add(FanFilter.clip(h, 70));
            }
        }
        // Every crest a widget names.
        List<String> ids = new ArrayList<>();
        for (AdBreakLogic.Row r : p.table) ids.add(r.id);
        for (List<String[]> l : java.util.Arrays.asList(p.homeNext, p.awayNext)) for (String[] n : l) ids.add(n[0]);
        if (g.homeId != null) ids.add(g.homeId);
        if (g.awayId != null) ids.add(g.awayId);
        for (String id : ids) {
            if (id == null || id.isEmpty() || p.crests.containsKey(id)) continue;
            Bitmap b = ScoresTicker.cachedLogo(c, crestUrl(id), crestPx);
            if (b != null) p.crests.put(id, b);
        }
        for (ScoresTicker.Game x : latest) {
            for (String[] e : new String[][]{{x.homeId, x.homeLogo}, {x.awayId, x.awayLogo}}) {
                if (e[0] == null || e[1] == null || p.crests.containsKey(e[0])) continue;
                Bitmap b = ScoresTicker.cachedLogo(c, e[1], crestPx);
                if (b != null) p.crests.put(e[0], b);
            }
        }
        for (String[] l : p.leaders) {
            if (l == null) continue;
            Bitmap face = PlayerPhotos.find(c, l[0], l[3], facePx);
            if (face != null) p.faces.put(l[0], face);
        }
        // The ground's weather now and to full time.
        String[] w = MatchExtras.weatherParts(c, g);
        if (w != null) {
            p.weatherHead = w[0];
            p.weatherLine = w[1];
        }
        // Their kits today, as the line-up card has them.
        try {
            Kits.Kit[] hk = Kits.of(c, g.homeName != null ? g.homeName : g.home), ak = Kits.of(c, g.awayName != null ? g.awayName : g.away);
            p.homeKit = Kits.wearing(hk, ak, false);
            p.awayKit = Kits.wearing(ak, hk, true);
            p.homeKitName = kitName(hk, p.homeKit);
            p.awayKitName = kitName(ak, p.awayKit);
        } catch (RuntimeException ignored) {
            // no kits
        }
        // The other games kicking off at the same time, with where they are on.
        for (ScoresTicker.Game x : latest) {
            if (x == null || x.id == null || x.id.equals(g.id) || x.state != 1 || Math.abs(x.kickoff - g.kickoff) > 60_000L) continue;
            Object[] where = GoalWatchService.watchOn(x);
            p.alsoNow.add(new Object[]{x, where});
            if (p.alsoNow.size() >= 3) break;
        }
        // The last meeting's goal, to play silently in its widget.
        if (!p.h2h.isEmpty()) {
            String[] last = p.h2h.get(p.h2h.size() - 1);
            for (String[] clip : GoalClips.clips(g.slug, last[3])) {
                if (!GoalClips.goalish(clip[1])) continue;
                p.clip = new String[]{clip[2], clip[1], last[0], last[2]};
                if (clip[3] != null && !clip[3].isEmpty()) p.clipStill = ScoresTicker.cachedLogo(c, clip[3], 360);
                break;
            }
        }
        p.lineups = GoalWatchService.breakLineups(g);
        return p;
    }

    private static String kitName(Kits.Kit[] kits, Kits.Kit wears) {
        if (kits == null || wears == null) return null;
        for (int i = 0; i < kits.length; i++) if (kits[i] == wears) return i == 0 ? "Home kit" : i == 1 ? "Away kit" : "Third kit";
        return null;
    }

    /** Each club's leading scorer in the competition: {name, "9 goals", club id, club name}; null where ESPN names none. */
    private static String[][] leaders(JSONObject summary, ScoresTicker.Game g) {
        String[][] out = new String[2][];
        org.json.JSONArray teams = summary.optJSONArray("leaders");
        for (int i = 0; teams != null && i < teams.length(); i++) {
            JSONObject t = teams.optJSONObject(i);
            JSONObject team = t == null ? null : t.optJSONObject("team");
            if (team == null) continue;
            String id = team.optString("id");
            int side = id.equals(g.homeId) ? 0 : id.equals(g.awayId) ? 1 : -1;
            if (side < 0) continue;
            org.json.JSONArray cats = t.optJSONArray("leaders");
            for (int k = 0; cats != null && k < cats.length(); k++) {
                JSONObject cat = cats.optJSONObject(k);
                if (cat == null || !cat.optString("name").toLowerCase(Locale.ROOT).contains("goal")) continue;
                org.json.JSONArray top = cat.optJSONArray("leaders");
                JSONObject first = top == null ? null : top.optJSONObject(0);
                JSONObject ath = first == null ? null : first.optJSONObject("athlete");
                if (ath == null) continue;
                String v = first.optString("displayValue");
                String what = v.isEmpty() ? cat.optString("displayName") : v + " " + ("1".equals(v) ? "goal" : "goals");
                out[side] = new String[]{ath.optString("displayName"), what, id, team.optString("displayName")};
                break;
            }
        }
        return out;
    }

    // ---- fan posts --------------------------------------------------------------------------------------------------------

    private static volatile List<FanPosts.Post> posts = new ArrayList<>();
    private static long postsAt;
    private static final java.util.Set<String> postsShown = new java.util.HashSet<>();
    private static final Map<String, Bitmap> avatars = new HashMap<>();

    private static void fetchPosts(ScoresTicker.Game g) {
        long now = System.currentTimeMillis();
        if (now - postsAt < POSTS_EVERY_MS) return;
        postsAt = now;
        io.execute(() -> {
            List<FanPosts.Post> got = FanPosts.fetch(g.homeName != null ? g.homeName : g.home, g.awayName != null ? g.awayName : g.away);
            for (FanPosts.Post p : got) {
                if (p.avatar == null || avatars.containsKey(p.avatar)) continue;
                Bitmap b = avatar(p.avatar);
                if (b != null) avatars.put(p.avatar, b);
            }
            android.util.Log.i(TAG, "fan posts: " + got.size());
            posts = got;
        });
    }

    /** A poster's picture, small, held in memory only (never kept on the TV). */
    private static Bitmap avatar(String url) {
        try {
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            con.setConnectTimeout(6_000);
            con.setReadTimeout(8_000);
            con.setRequestProperty("User-Agent", FanPosts.AGENT);
            try (java.io.InputStream in = con.getInputStream()) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2;
                Bitmap b = BitmapFactory.decodeStream(in, null, o);
                if (b == null) return null;
                Bitmap small = Bitmap.createScaledBitmap(b, 96, 96, true);
                if (small != b) b.recycle();
                return small;
            } finally {
                con.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ---- the screen ------------------------------------------------------------------------------------------------------

    private static FrameLayout strip, table, postsBox;
    private static ScoresTicker.Game shownFor;
    private static TextView countdown, hint;
    private static final Card[] cards = new Card[3];
    private static boolean inside, lineupOpened;
    private static int picked;

    private static int px(float v) {
        return Math.round(v * s);
    }

    private static WindowManager.LayoutParams params(int w, int h, int gravity, int x, int y) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = gravity;
        lp.x = x;
        lp.y = y;
        lp.setTitle("AdBreak");
        return lp;
    }

    private static void add(Context c, View v, WindowManager.LayoutParams lp) {
        try {
            c.getSystemService(WindowManager.class).addView(v, lp);
        } catch (RuntimeException e) {
            android.util.Log.w(TAG, "window refused: " + e.getClass().getSimpleName());
        }
    }

    private static void remove(View v) {
        if (v == null) return;
        try {
            v.getContext().getSystemService(WindowManager.class).removeView(v);
        } catch (RuntimeException ignored) {
            // gone already
        }
    }

    /** Up (main thread): the strip, the table, the line-up card, the posts. */
    static void show(Context ctx, ScoresTicker.Game g) {
        Context c = ctx.getApplicationContext();
        if (g == null) return;
        if (isShowing() && shownFor != null && shownFor.id.equals(g.id)) return;
        hideNow();
        app = c;
        shownFor = g;
        s = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        AlertLog.note(c, "ad-break screen up: " + AlertLog.game(g));
        // The strip along the foot.
        int sw = c.getResources().getDisplayMetrics().widthPixels, stripH = px(294);
        strip = new FrameLayout(c);
        GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xF5070912, 0xEB0B0F24, 0xEB0B0F24, 0x000B0F24});
        strip.setBackground(shade);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.BOTTOM);
        col.setPadding(px(60), 0, px(60), px(36));
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView before = text(c, "BEFORE KICK-OFF", 19.5f, 0xFFFF8A8A, Typeface.create("sans-serif", Typeface.BOLD));
        before.setLetterSpacing(0.1f);
        head.addView(before);
        String when = new java.text.SimpleDateFormat("HH:mm", Locale.UK).format(new java.util.Date(g.kickoff));
        TextView title = text(c, name(g, true) + " v " + name(g, false) + " · " + when, 24, Color.WHITE, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = px(21);
        head.addView(title, tl);
        hint = text(c, InputKeyService.running ? "Down: widgets" : "", 19.5f, 0xFFA9B1CC, Typeface.DEFAULT);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, -2, 1f);
        hl.leftMargin = px(21);
        head.addView(hint, hl);
        head.addView(text(c, "Kick-off in", 19.5f, 0xFFA9B1CC, Typeface.DEFAULT));
        countdown = text(c, "", 45, Color.WHITE, ScoreCard.outfit(c, 700));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.leftMargin = px(12);
        head.addView(countdown, cl);
        col.addView(head, new LinearLayout.LayoutParams(-1, px(60)));
        LinearLayout row = new LinearLayout(c);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, px(168));
        rl.topMargin = px(18);
        col.addView(row, rl);
        for (int i = 0; i < 3; i++) {
            cards[i] = new Card(c, i);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) l.leftMargin = px(24);
            row.addView(cards[i].root, l);
        }
        strip.addView(col, new FrameLayout.LayoutParams(-1, -1));
        add(c, strip, params(sw, stripH, Gravity.BOTTOM | Gravity.START, 0, 0));
        strip.setTranslationY(stripH);
        strip.animate().translationY(0).setDuration(420).setInterpolator(new DecelerateInterpolator(2f)).start();
        // The posts' column, above the strip on the left.
        postsBox = new FrameLayout(c);
        add(c, postsBox, params(px(705), px(430), Gravity.BOTTOM | Gravity.START, px(60), stripH + px(27)));
        inside = false;
        picked = 0;
        tick.run();
        Pack p = pack;
        if (p != null && p.g.id.equals(g.id)) fill(c, p);
        else prefetch(g);
        main.postDelayed(postRun, 3_000L);
    }

    /** The widgets filled in once the game's data is in. */
    private static void fill(Context c, Pack p) {
        if (!isShowing()) return;
        List<List<Widget>> lists = Widgets.build(c, p, s);
        for (int i = 0; i < 3; i++) cards[i].set(lists.get(i));
        // The table's card above the line-up card on the right, its size.
        remove(table);
        table = null;
        View t = Widgets.tableCard(c, p, s);
        if (t != null) {
            table = new FrameLayout(c);
            table.addView(t, new FrameLayout.LayoutParams(px(616), px(204)));
            add(c, table, params(px(616), px(204), Gravity.TOP | Gravity.END, px(60), px(42)));
            table.setAlpha(0f);
            table.setTranslationX(px(40));
            table.animate().alpha(1f).translationX(0).setDuration(340).setInterpolator(new DecelerateInterpolator(2f)).start();
        }
        if (p.lineups != null) {
            float k = s * 0.88f;
            int w = Math.round((700 + 20) * k);
            int x = c.getResources().getDisplayMetrics().widthPixels - px(60) - w + Math.round(10 * k);
            LineupCard.showForBreak(c, p.lineups, x, px(264) - Math.round(10 * k));
            lineupOpened = true;
        }
    }

    private static final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!isShowing() || shownFor == null) return;
            long left = Math.max(0, shownFor.kickoff - System.currentTimeMillis()) / 1000;
            countdown.setText(String.format(Locale.UK, "%d:%02d", left / 60, left % 60));
            main.postDelayed(this, 1_000L - System.currentTimeMillis() % 1000);
        }
    };

    /** Away (main thread). */
    static void hide(String why) {
        if (!isShowing()) return;
        if (app != null) AlertLog.note(app, "ad-break screen away: " + why);
        hideNow();
    }

    private static void hideNow() {
        main.removeCallbacks(tick);
        main.removeCallbacks(postRun);
        for (int i = 0; i < 3; i++) if (cards[i] != null) cards[i].stop();
        if (strip != null) {
            FrameLayout st = strip;
            st.animate().translationY(st.getHeight()).alpha(0f).setDuration(300).withEndAction(() -> remove(st)).start();
        }
        if (table != null) {
            FrameLayout t = table;
            t.animate().alpha(0f).translationX(px(40)).setDuration(260).withEndAction(() -> remove(t)).start();
        }
        if (postsBox != null) {
            FrameLayout pb = postsBox;
            pb.animate().alpha(0f).setDuration(260).withEndAction(() -> remove(pb)).start();
        }
        if (lineupOpened) LineupCard.close(true);
        lineupOpened = false;
        strip = null;
        table = null;
        postsBox = null;
        shownFor = null;
        inside = false;
    }

    // ---- the posts popping up ------------------------------------------------------------------------------------------

    private static final Runnable postRun = new Runnable() {
        @Override public void run() {
            if (!isShowing() || postsBox == null) return;
            ScoresTicker.Game g = shownFor;
            if (g != null) fetchPosts(g);
            FanPosts.Post next = null;
            for (FanPosts.Post p : posts) {
                if (!postsShown.contains(p.key)) {
                    next = p;
                    break;
                }
            }
            if (next != null) {
                postsShown.add(next.key);
                pop(postsBox.getContext(), next);
            }
            main.postDelayed(this, POST_GAP_MS);
        }
    };

    private static void pop(Context c, FanPosts.Post p) {
        LinearLayout card = new LinearLayout(c);
        card.setGravity(Gravity.TOP);
        card.setPadding(px(24), px(21), px(24), px(21));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE00B0F24);
        bg.setCornerRadius(px(14));
        bg.setStroke(Math.max(1, px(1.5f)), 0x24FFFFFF);
        card.setBackground(bg);
        card.addView(Widgets.ringed(c, avatars.get(p.avatar), initials(p.name), 0xFF2B3170, px(60)), new LinearLayout.LayoutParams(px(60), px(60)));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView n = text(c, p.name, 21, Color.WHITE, Typeface.create("sans-serif", Typeface.BOLD));
        top.addView(n, new LinearLayout.LayoutParams(-2, -2));
        TextView h = text(c, "  " + p.handle + " · " + FanPosts.ago(p.at, System.currentTimeMillis()), 18, 0xFFA9B1CC, Typeface.DEFAULT);
        top.addView(h, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView src = text(c, p.source, 16.5f, 0xFFC9CFE3, Typeface.create("sans-serif-condensed", Typeface.BOLD));
        src.setLetterSpacing(0.06f);
        src.setPadding(px(9), px(2), px(9), px(3));
        src.setBackground(fill(0x29FFFFFF, px(6)));
        top.addView(src);
        words.addView(top, new LinearLayout.LayoutParams(-1, -2));
        TextView body = text(c, p.text, 22, 0xFFEEF1FA, Typeface.DEFAULT);
        body.setSingleLine(false);
        body.setMaxLines(3);
        body.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = px(6);
        words.addView(body, bl);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1f);
        wl.leftMargin = px(18);
        card.addView(words, wl);
        // Newest at the foot: the others move up and fade; past three, the oldest goes.
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        postsBox.addView(card, fl);
        card.measure(View.MeasureSpec.makeMeasureSpec(px(705), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int hgt = card.getMeasuredHeight() + px(15);
        int count = postsBox.getChildCount();
        for (int i = 0; i < count - 1; i++) {
            View v = postsBox.getChildAt(i);
            int age = count - 1 - i;
            if (age >= POSTS_SHOWN) {
                v.animate().alpha(0f).translationYBy(-hgt).setDuration(300).withEndAction(() -> postsBox_remove(v)).start();
            } else {
                v.animate().translationYBy(-hgt).alpha(age == 1 ? 0.8f : 0.6f).setDuration(380).setInterpolator(new DecelerateInterpolator(2f)).start();
            }
        }
        card.setAlpha(0f);
        card.setTranslationX(-px(40));
        card.animate().alpha(1f).translationX(0).setDuration(420).setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    private static void postsBox_remove(View v) {
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    }

    static String initials(String name) {
        if (name == null || name.isEmpty()) return "";
        String[] w = name.replaceAll("[^A-Za-z ]", " ").trim().split("\\s+");
        StringBuilder b = new StringBuilder();
        for (String x : w) if (!x.isEmpty() && b.length() < 2) b.append(Character.toUpperCase(x.charAt(0)));
        return b.toString();
    }

    // ---- the remote ----------------------------------------------------------------------------------------------------

    /** Keys from the key helper: Down goes in; once in, the arrows, OK and Back are the cards'. */
    static boolean key(KeyEvent e) {
        if (!isShowing()) return false;
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (!inside) {
            if (code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            if (down && e.getRepeatCount() == 0) {
                inside = true;
                picked = firstCard();
                ring();
                if (hint != null) hint.setText("Left, Right: card · Up, Down: scroll · Back: out");
            }
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) {
                    int step = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                    for (int i = picked + step; i >= 0 && i < 3; i += step) {
                        if (cards[i] != null && cards[i].has()) {
                            picked = i;
                            break;
                        }
                    }
                    ring();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down && cards[picked] != null) cards[picked].turn(code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!down && cards[picked] != null) cards[picked].ok();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (!down) {
                    inside = false;
                    ring();
                    if (hint != null) hint.setText("Down: widgets");
                    if (forced) {
                        // The test: Back puts it away.
                        forced = false;
                        trigger.set(false);
                        hide("Back, on the test");
                    }
                }
                return true;
            default:
                return false;
        }
    }

    private static int firstCard() {
        for (int i = 0; i < 3; i++) if (cards[i] != null && cards[i].has()) return i;
        return 0;
    }

    private static void ring() {
        for (int i = 0; i < 3; i++) if (cards[i] != null) cards[i].ring(inside && i == picked);
    }

    // ---- one card of the three -----------------------------------------------------------------------------------------

    /** A widget: its band's mark and name, how its lines are made, how long it stays, and what OK does on it. */
    static final class Widget {
        final Bitmap mark;
        final String caption;
        final Widgets.Lines lines;
        final Runnable ok;
        final String clipUrl;
        final Bitmap clipStill;

        Widget(Bitmap mark, String caption, Widgets.Lines lines, Runnable ok, String clipUrl, Bitmap clipStill) {
            this.mark = mark;
            this.caption = caption;
            this.lines = lines;
            this.ok = ok;
            this.clipUrl = clipUrl;
            this.clipStill = clipStill;
        }
    }

    private static final class Card {
        final FrameLayout root, band, stage;
        final View wipe;
        final int index;
        List<Widget> list = new ArrayList<>();
        int at;
        MediaPlayer player;
        private final Runnable next = () -> turn(1);
        private boolean ringed;

        Card(Context c, int index) {
            this.index = index;
            root = new FrameLayout(c);
            float r = px(12);
            root.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
            });
            root.setClipToOutline(true);
            root.setBackgroundColor(0xFF0D1360);
            band = new FrameLayout(c);
            band.setBackground(SkyStyle.band(SkyStyle.NAVY, SkyStyle.NAVY_DEEP, px(18), px(3)));
            root.addView(band, new FrameLayout.LayoutParams(px(90), -1));
            stage = new FrameLayout(c);
            stage.setBackground(new SkyStyle.Streaks(0xFFF4F5FD, 0xFFC2C5EE, 0x8CFFFFFF, px(75), px(32)));
            stage.setClipChildren(true);
            FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-1, -1);
            sl.leftMargin = px(90);
            root.addView(stage, sl);
            wipe = new WipeView(c);
            wipe.setVisibility(View.GONE);
            stage.addView(wipe, new FrameLayout.LayoutParams(-1, -1));
            root.setVisibility(View.INVISIBLE);
        }

        boolean has() {
            return !list.isEmpty();
        }

        void set(List<Widget> widgets) {
            list = widgets == null ? new ArrayList<>() : widgets;
            root.setVisibility(list.isEmpty() ? View.INVISIBLE : View.VISIBLE);
            at = 0;
            if (list.isEmpty()) return;
            put(list.get(0));
            main.removeCallbacks(next);
            main.postDelayed(next, AdBreakLogic.firstTurn(index));
        }

        void ring(boolean on) {
            ringed = on;
            if (on) {
                GradientDrawable g = new GradientDrawable();
                g.setColor(0);
                g.setCornerRadius(px(12));
                g.setStroke(px(4.5f), Color.WHITE);
                root.setForeground(g);
                root.setElevation(px(18));
                root.animate().scaleX(1.03f).scaleY(1.03f).setDuration(160).start();
            } else {
                root.setForeground(null);
                root.setElevation(0);
                root.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
            }
        }

        /** The next (or the one before) behind the wipe; the picked card waits for the remote. */
        void turn(int step) {
            main.removeCallbacks(next);
            if (list.isEmpty() || !isShowing()) return;
            int to = AdBreakLogic.next(at, step, list.size());
            boolean held = inside && cards[picked] == this;
            if (list.size() > 1) {
                at = to;
                Widget w = list.get(to);
                int sw = stage.getWidth(), sh = stage.getHeight();
                float slant = sh * 0.42f;
                ((WipeView) wipe).span = sw + 2 * slant;
                ViewGroup.LayoutParams wl = wipe.getLayoutParams();
                if (wl.width != Math.round(sw + 2 * slant)) {
                    wl.width = Math.round(sw + 2 * slant);
                    wipe.setLayoutParams(wl);
                }
                wipe.setVisibility(View.VISIBLE);
                wipe.bringToFront();
                wipe.setTranslationX(-(sw + 2 * slant));
                boolean[] swapped = {false};
                wipe.animate().translationX(sw).setDuration(900).setInterpolator(new AccelerateDecelerateInterpolator())
                        .setUpdateListener(a -> {
                            if (swapped[0] || a.getAnimatedFraction() < 0.5f) return;
                            swapped[0] = true;
                            put(w);
                            wipe.bringToFront();
                        })
                        .withEndAction(() -> wipe.setVisibility(View.GONE)).start();
            }
            long stay = list.get(at).clipUrl != null ? 30_000L : AdBreakLogic.PERIOD_MS;
            if (!held) main.postDelayed(next, stay);
        }

        void ok() {
            if (list.isEmpty()) return;
            Widget w = list.get(at);
            if (w.ok != null) w.ok.run();
        }

        /** A widget in: its band, its lines coming in from the right one after another. */
        private void put(Widget w) {
            Context c = root.getContext();
            stopClip();
            band.removeAllViews();
            LinearLayout b = new LinearLayout(c);
            b.setOrientation(LinearLayout.VERTICAL);
            b.setGravity(Gravity.CENTER_HORIZONTAL);
            b.setPadding(0, px(18), 0, px(12));
            if (w.mark != null) {
                ImageView m = new ImageView(c);
                m.setImageBitmap(w.mark);
                m.setScaleType(ImageView.ScaleType.FIT_CENTER);
                b.addView(m, new LinearLayout.LayoutParams(px(33), px(33)));
            } else {
                b.addView(new HouseMark(c), new LinearLayout.LayoutParams(px(36), px(36)));
            }
            TextView cap = text(c, w.caption, 12.75f, 0xE6FFFFFF, Typeface.create("sans-serif-condensed", Typeface.BOLD));
            cap.setSingleLine(false);
            cap.setMaxLines(3);
            cap.setGravity(Gravity.CENTER);
            cap.setLetterSpacing(0.08f);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(px(84), -2);
            cl.topMargin = px(12);
            b.addView(cap, cl);
            View gap = new View(c);
            b.addView(gap, new LinearLayout.LayoutParams(1, 0, 1f));
            TextView pos = text(c, (at + 1) + " / " + list.size(), 13.5f, 0xB3FFFFFF, Typeface.create("sans-serif-condensed", Typeface.BOLD));
            b.addView(pos);
            band.addView(b, new FrameLayout.LayoutParams(-1, -1));
            for (int i = stage.getChildCount() - 1; i >= 0; i--) if (stage.getChildAt(i) != wipe) stage.removeViewAt(i);
            if (w.clipUrl != null) {
                stage.addView(clipView(c, w), 0, new FrameLayout.LayoutParams(-1, -1));
                return;
            }
            LinearLayout lines = new LinearLayout(c);
            lines.setOrientation(LinearLayout.VERTICAL);
            lines.setGravity(Gravity.CENTER_VERTICAL);
            lines.setPadding(px(18), px(12), px(18), px(12));
            List<View> made = w.lines.make(c);
            for (int i = 0; i < made.size(); i++) {
                View v = made.get(i);
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
                if (i > 0) l.topMargin = px(9);
                lines.addView(v, l);
                v.setAlpha(0f);
                v.setTranslationX(px(39));
                v.animate().alpha(1f).translationX(0).setStartDelay(120L + 70L * i).setDuration(420)
                        .setInterpolator(new DecelerateInterpolator(2.2f)).start();
            }
            stage.addView(lines, 0, new FrameLayout.LayoutParams(-1, -1));
        }

        /** The last meeting's goal at the card's full height, playing with no sound (no hold on the sound either). */
        private View clipView(Context c, Widget w) {
            LinearLayout row = new LinearLayout(c);
            FrameLayout box = new FrameLayout(c);
            box.setBackgroundColor(Color.BLACK);
            int ch = px(168), cw = Math.round(ch * 16f / 9f);
            ImageView still = new ImageView(c);
            still.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (w.clipStill != null) still.setImageBitmap(w.clipStill);
            box.addView(still, new FrameLayout.LayoutParams(-1, -1));
            TextureView video = new TextureView(c);
            box.addView(video, new FrameLayout.LayoutParams(-1, -1));
            video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st, int sw2, int sh2) {
                    try {
                        MediaPlayer mp = new MediaPlayer();
                        player = mp;
                        mp.setDataSource(w.clipUrl);
                        mp.setSurface(new android.view.Surface(st));
                        mp.setVolume(0f, 0f);
                        mp.setOnPreparedListener(m -> {
                            if (player != m) return;
                            m.setVolume(0f, 0f);
                            m.start();
                        });
                        // The decoder cannot start (two videos at once): its still stays.
                        mp.setOnErrorListener((m, what, extra) -> {
                            video.setVisibility(View.GONE);
                            return true;
                        });
                        mp.prepareAsync();
                    } catch (Exception e) {
                        video.setVisibility(View.GONE);
                    }
                }
                @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st, int a, int b2) { }
                @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st) { stopClip(); return true; }
                @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st) { }
            });
            row.addView(box, new LinearLayout.LayoutParams(cw, ch));
            LinearLayout side = new LinearLayout(c);
            side.setOrientation(LinearLayout.VERTICAL);
            side.setGravity(Gravity.CENTER_VERTICAL);
            side.setPadding(px(15), 0, px(12), 0);
            for (View v : w.lines.make(c)) {
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
                l.bottomMargin = px(8);
                side.addView(v, l);
            }
            row.addView(side, new LinearLayout.LayoutParams(0, -1, 1f));
            return row;
        }

        void stopClip() {
            MediaPlayer mp = player;
            player = null;
            if (mp != null) {
                try { mp.release(); } catch (RuntimeException ignored) { }
            }
        }

        void stop() {
            main.removeCallbacks(next);
            stopClip();
        }
    }

    /** The slanted navy panel, edged light blue and white, crossing a card as its widget changes (the line-up card's). */
    private static final class WipeView extends View {
        float span;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        WipeView(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas cv) {
            int h = getHeight();
            float slant = h * 0.42f, w = span > 0 ? span : getWidth(), body = w - slant, edge = px(10);
            panel(cv, 0, body, h, slant, 0xFF151A63);
            panel(cv, body - edge, body, h, slant, 0xFF3D4BD6);
            panel(cv, body - edge * 0.35f, body, h, slant, Color.WHITE);
            panel(cv, 0, edge, h, slant, 0xFF3D4BD6);
            panel(cv, 0, edge * 0.35f, h, slant, 0xE6FFFFFF);
        }

        private void panel(Canvas cv, float from, float to, int h, float slant, int colour) {
            Path path = new Path();
            path.moveTo(from, h);
            path.lineTo(to, h);
            path.lineTo(to + slant, 0);
            path.lineTo(from + slant, 0);
            path.close();
            p.setColor(colour);
            cv.drawPath(path, p);
        }

    }

    /** The house with its play mark (Homefield's own) for the widgets about this house and its day. */
    static final class HouseMark extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        HouseMark(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), u = w / 24f;
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * u);
            p.setStrokeJoin(Paint.Join.ROUND);
            Path h = new Path();
            h.moveTo(3 * u, 11 * u);
            h.lineTo(12 * u, 3 * u);
            h.lineTo(21 * u, 11 * u);
            h.lineTo(21 * u, 21 * u);
            h.lineTo(3 * u, 21 * u);
            h.close();
            cv.drawPath(h, p);
            p.setStyle(Paint.Style.FILL);
            Path t = new Path();
            t.moveTo(10 * u, 11 * u);
            t.lineTo(15.5f * u, 14.5f * u);
            t.lineTo(10 * u, 18 * u);
            t.close();
            cv.drawPath(t, p);
        }
    }

    // ---- small things ------------------------------------------------------------------------------------------------------

    static String name(ScoresTicker.Game g, boolean home) {
        String n = home ? (g.homeName != null ? g.homeName : g.home) : (g.awayName != null ? g.awayName : g.away);
        return n == null ? "" : n;
    }

    static TextView text(Context c, String t, float size1920, int colour, Typeface tf) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextColor(colour);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size1920 * s);
        v.setTypeface(tf);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setIncludeFontPadding(false);
        return v;
    }

    static GradientDrawable fill(int colour, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(colour);
        g.setCornerRadius(radius);
        return g;
    }

    static List<ScoresTicker.Game> latestGames() {
        return latest;
    }

    static float scale() {
        return s;
    }

    static Handler main() {
        return main;
    }
}
