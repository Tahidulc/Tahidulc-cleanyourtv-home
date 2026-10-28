package com.gathin.tvhome;

import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Goal alerts over another app (config.xml goal_watch_apps, TiviMate): started when Home opens one of those apps,
 * stopped when Home comes back. Reads the scores every POLL_MS and shows each new goal DELAY_MS after it was seen,
 * as a small glass box in the bottom-left corner for SHOW_MS. The box never takes focus or keys. Needs "display over
 * other apps" (appops SYSTEM_ALERT_WINDOW); without it the service stops at once.
 * <p>
 * A theme's config.json can add more: "f1_live_alerts" (qualifying, the sprint and the race as they run),
 * "news_alerts" (new headlines from config.xml news_alert_feeds), "frost_alerts" (a lighter, grained silver),
 * "team_alerts" (every game of the goal_alert_teams clubs), "incident_alerts" (penalties missed or saved, VAR),
 * "kickoff_reminders" (15 minutes before a goal_alert_leagues game), "tennis_alerts", "ufc_alerts", "boxing_alerts"
 * (BBC Sport's boxing headlines: ESPN has no boxing) and "alerts_everywhere" (Home and the screensaver too: the
 * service then runs for as long as the TV is on, and reads nothing while the screen is off).
 */
public class GoalWatchService extends Service {
    private static final long POLL_MS = 20_000L;
    private static final long DELAY_MS = 60_000L;
    private static final long SHOW_MS = 8_000L;
    private static final long NEWS_MS = 5 * 60_000L;
    private static final long REMIND_MS = 15 * 60_000L;
    /** The tennis feeds are large (all of a week's draws), so they are read less often than the others. */
    private static final long TENNIS_MS = 10 * 60_000L;
    private static final String BOXING_FEED = "https://feeds.bbci.co.uk/sport/boxing/rss.xml|BOXING";
    private static final long MAX_RUN_MS = 4 * 60 * 60_000L;
    private static final String CHANNEL = "goal_watch";
    /** The alerts' size: a pill PILL_DP tall, TEXT_SP text, CREST_DP crests and logos fetched at LOGO_DP. */
    private static final int PILL_DP = 32, CREST_DP = 18, LOGO_DP = 48;
    private static final float TEXT_SP = 12.5f, SMALL_SP = 10f;
    private static final long WEATHER_MS = 15 * 60_000L;

    /**
     * A short sound for each kind of alert (a theme's "alert_sounds"), quiet, and played as a UI sound without asking
     * for audio focus, so whatever is playing carries on at its own volume.
     */
    private android.media.SoundPool sounds;
    private final Map<String, Integer> soundIds = new HashMap<>();
    private static final float SOUND_VOLUME = 0.35f;

    private void loadSounds() {
        sounds = new android.media.SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
                new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        int[] raw = {R.raw.alert_goal, R.raw.alert_red, R.raw.alert_fulltime, R.raw.alert_incident, R.raw.alert_f1,
                R.raw.alert_news, R.raw.alert_weather, R.raw.alert_tennis, R.raw.alert_fight, R.raw.alert_reminder};
        String[] names = {"goal", "red", "fulltime", "incident", "f1", "news", "weather", "tennis", "fight", "reminder"};
        for (int i = 0; i < raw.length; i++) soundIds.put(names[i], sounds.load(this, raw[i], 1));
    }

    private void play(String sound) {
        Integer id = sound == null || sounds == null ? null : soundIds.get(sound);
        if (id != null) sounds.play(id, SOUND_VOLUME, SOUND_VOLUME, 1, 0, 1f);
    }

    /** The screensaver is on (its own clock sits bottom-left, where the alerts go). */
    private boolean dreaming;
    private final android.content.BroadcastReceiver dreams = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            dreaming = Intent.ACTION_DREAMING_STARTED.equals(i.getAction());
            releaseHeld();
        }
    };

    /** Something started or stopped playing: held line-ups come out once nothing plays. */
    private final android.media.AudioManager.AudioPlaybackCallback playback = new android.media.AudioManager.AudioPlaybackCallback() {
        @Override public void onPlaybackConfigChanged(List<android.media.AudioPlaybackConfiguration> configs) {
            // The stream reads as active for a moment after a pause.
            main.postDelayed(GoalWatchService.this::releaseHeld, 1_500);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        android.content.IntentFilter f = new android.content.IntentFilter(Intent.ACTION_DREAMING_STARTED);
        f.addAction(Intent.ACTION_DREAMING_STOPPED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(dreams, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(dreams, f);
        android.media.AudioManager am = getSystemService(android.media.AudioManager.class);
        if (am != null) am.registerAudioPlaybackCallback(playback, main);
        deliveries = new DeliveryLink(this, new DeliveryLink.Sink() {
            @Override public void pairing(String phone, String code) { main.post(() -> showPairing(phone, code)); }
            @Override public void delivery(JSONObject update) { main.post(() -> showDelivery(update)); }
            @Override public void paired() { main.post(() -> { if (pairingClose != null) pairingClose.run(); }); }
        });
        deliveries.start();
    }

    // ---- delivery updates from the phone (DeliveryLink) ----------------------------------------------------

    private DeliveryLink deliveries;

    /** The code a phone asks to pair with, for the person to type there: ahead of any other alert waiting. */
    private void showPairing(String phone, String code) {
        float d = getResources().getDisplayMetrics().density;
        queue.removeIf(e -> e.length == 2 && e[0] instanceof DeliveryCard.Card && ((DeliveryCard.Card) e[0]).pairing);
        queue.removeIf(e -> e.length == 2 && e[0] instanceof DeliveryCard.Card && ((DeliveryCard.Card) e[0]).pairing);
        queue.addFirst(new Object[]{DeliveryCard.pairing(this, phone, code, glass(20 * d)), "reminder"});
        if (pairingClose != null) pairingClose.run();   // an older code on screen gives way to the new one
        if (pairingClose != null) pairingClose.run();   // an older code on screen gives way to the new one
        showNext();
    }

    /**
     * A delivery app's update, as a card in that app's colours: its logo, the restaurant, the stage with a look of
     * its own, the bar of stages and what was ordered (DeliveryCard).
     */
    private void showDelivery(JSONObject u) {
        String kind = u.optString("kind");
        if ("car".equals(kind)) { showCar(u); return; }
        if ("home_eta".equals(kind)) {
            if (Config.themeFlag(this, "home_eta", false)) {
                queue.addFirst(new Object[]{DeliveryCard.homeEta(this, u, glass(24 * getResources().getDisplayMetrics().density)), "reminder"});
                showNext();
            }
            return;
        }
        if (!kind.isEmpty() && !"delivery".equals(kind)) return;   // something newer than this TV knows
        float d = getResources().getDisplayMetrics().density;
        // Ahead of the sports alerts waiting: an order's news is now or never.
        queue.addFirst(new Object[]{DeliveryCard.food(this, u, glass(24 * d)), u.optInt("stage") == 4 ? "fulltime" : "reminder"});
        showNext();
    }

    /**
     * The car app's notification (Tesla), passed on by the phone: a card with the car, the Tesla mark and the battery,
     * over any app and on Home.
     */
    private void showCar(JSONObject u) {
        String title = u.optString("title", "").trim(), status = u.optString("status", "").trim();
        if (title.isEmpty() && status.isEmpty()) return;
        float d = getResources().getDisplayMetrics().density;
        queue.addFirst(new Object[]{DeliveryCard.car(this, u, glass(24 * d)), "reminder"});
        showNext();
    }

    /** Closes the pairing code's card once the phone has typed it in. */
    private Runnable pairingClose;

    /** A card (DeliveryCard) rises into place, plays its reveal, stays a while and fades. */
    private void showCard(DeliveryCard.Card card, String sound) {
        float d = getResources().getDisplayMetrics().density;
        ensureHost(card.widthDp, card.heightDp);
        if (host == null) { showNext(); return; }
        View panel = card.view;
        host.addView(panel, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START));
        shown = panel;
        play(sound);
        Runnable[] closing = new Runnable[1];
        LinearLayout box = (LinearLayout) panel;
        final float[] visible = {Float.MAX_VALUE};
        Runnable close = () -> {
            main.removeCallbacks(closing[0]);
            if (pairingClose == closing[0]) pairingClose = null;
            if (shown != panel) return;
            // Closed as a goal alert closes: the words fade, the pill draws back to a dot, the dot goes.
            visible[0] = box.getWidth();
            outline(box, box.getHeight(), visible);
            closePill(box, box.getHeight(), visible);
        };
        closing[0] = close;
        if (card.pairing) pairingClose = close;
        if (calm()) {
            card.reveal.accept(stillOnly() ? DeliveryCard.STILL : DeliveryCard.CALM);
            main.postDelayed(close, card.showMs);
            return;
        }
        // Opened as a goal alert opens: a glass dot pops in at the corner, the pill stretches out to the right with
        // its round ends kept, then the words fade in, and the card's own reveal (its bar filling) plays.
        for (int c = 0; c < box.getChildCount(); c++) box.getChildAt(c).setAlpha(0f);
        box.setAlpha(0f);
        box.setScaleX(0.3f);
        box.setScaleY(0.3f);
        box.post(() -> {
            int h = box.getHeight();
            visible[0] = h;
            outline(box, h, visible);
            box.setPivotX(h / 2f);
            box.setPivotY(h / 2f);
            box.setLayerType(animLayer(), null);
            box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).withEndAction(() -> {
                ((RevealBox) box).setFrozen(true);
                android.animation.ValueAnimator grow = android.animation.ValueAnimator.ofFloat(h, box.getWidth());
                grow.setDuration(850);
                grow.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
                grow.addUpdateListener(a -> { visible[0] = (float) a.getAnimatedValue(); box.invalidateOutline(); box.invalidate(); });
                grow.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator a) {
                        ((RevealBox) box).setFrozen(false);
                        visible[0] = Float.MAX_VALUE;
                        box.invalidateOutline();
                        for (int c = 0; c < box.getChildCount(); c++) {
                            box.getChildAt(c).animate().alpha(1f).setDuration(300).setStartDelay(c * 40L).start();
                        }
                        box.postDelayed(() -> {
                            box.setLayerType(View.LAYER_TYPE_NONE, null);
                            card.reveal.accept(DeliveryCard.MOVE);
                        }, 450);
                    }
                });
                grow.start();
            }).start();
        });
        main.postDelayed(close, card.showMs);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    /**
     * Polls, downloads and decodes run at background priority, so they never take a core from the app being watched
     * (its player and its UI) on a quad-core TV.
     */
    private static final java.util.concurrent.ThreadFactory LOW = r -> new Thread(() -> {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        r.run();
    }, "alerts");
    private final ExecutorService io = Executors.newSingleThreadExecutor(LOW);
    private final Set<String> seen = new HashSet<>();
    private boolean primed;
    private long startedAt;
    private View shown;
    /** The overlay window, added for one alert at a time and removed after it. */
    private android.widget.FrameLayout host;
    /**
     * Alerts waiting their turn: a goal {first line, game, home crest, away crest}, a ready line {parts} or a race
     * result {race}.
     */
    private final java.util.ArrayDeque<Object[]> queue = new java.util.ArrayDeque<>();
    private final Runnable poll = this::poll;
    /** The newest read of each game, so an alert shown a minute later carries the score as it is by then. */
    private final Map<String, ScoresTicker.Game> latest = new HashMap<>();
    /** Each game's state at the last read (0 live, 1 upcoming, 2 finished), to see the moment one finishes. */
    private final Map<String, Integer> lastState = new HashMap<>();
    /** The F1 race's state at the last read, to see the moment it finishes. */
    private String f1Id, f1State;

    /** The theme's extras, read when the service starts. */
    private boolean f1Live, newsAlerts, frost;
    /** Each F1 session's state, leader and ten-lap mark at the last read. */
    private final Map<String, String> f1States = new HashMap<>(), f1Leaders = new HashMap<>();
    private final Map<String, Integer> f1Laps = new HashMap<>();
    private boolean f1Primed, newsPrimed;
    private long newsAt;
    private final Set<String> seenNews = new HashSet<>();
    /** Weather pop-ups (a theme's "weather_alerts"): warnings already shown, the last rain alert, the last morning. */
    private boolean weatherAlerts;
    private final Set<String> seenWarnings = new HashSet<>();
    private long weatherAt, rainAlertAt;
    private String morningShown = "";
    private boolean teamAlerts, incidentAlerts, kickoffReminders, tennisAlerts, ufcAlerts, boxingAlerts, everywhere;
    /** Games already reminded of, and games whose incidents have been read once (the first read only learns). */
    private final Set<String> reminded = new HashSet<>(), incidentGames = new HashSet<>();
    /** Each tennis match's and UFC fight's state at the last read. */
    private final Map<String, String> otherStates = new HashMap<>();
    private boolean tennisPrimed, ufcPrimed, ufcLive;
    private long tennisAt, ufcAt;
    private int polls;

    /** A picture in a ready line, fetched (from the logo cache) before the line is queued; wide for a wordmark. */
    private static final class Pic {
        final String url;
        final boolean wide;
        Pic(String url) { this(url, false); }
        Pic(String url, boolean wide) { this.url = url; this.wide = wide; }
    }

    /** A player on a line-up page: his photo (or null) and shirt number. */
    private static final class Face {
        final android.graphics.Bitmap photo;
        final String number;
        Face(android.graphics.Bitmap photo, String number) { this.photo = photo; this.number = number; }
    }

    /** A channel logo to fetch, shown on a dark capsule; ChipImg once fetched. */
    private static final class Chip {
        final String url, name;
        Chip(String url, String name) { this.url = url; this.name = name; }
    }

    private static final class ChipImg {
        final android.graphics.Bitmap bitmap;
        ChipImg(android.graphics.Bitmap bitmap) { this.bitmap = bitmap; }
    }

    /** A fetched Pic. */
    private static final class Img {
        final android.graphics.Bitmap bitmap;
        final boolean wide;
        Img(android.graphics.Bitmap bitmap, boolean wide) { this.bitmap = bitmap; this.wide = wide; }
    }

    /** A goal's score: the side that scored ("home" or "away", or null for none) has its number roll up to it. */
    private static final class Score {
        final int home, away;
        final String side;
        Score(int home, int away, String side) { this.home = home; this.away = away; this.side = side; }
    }

    /** The dim tag at the end of a line, like the competition. */
    private static final class Small {
        final CharSequence text;
        Small(CharSequence text) { this.text = text; }
    }

    /** Home opened this package: watch for goals while it is in front, if it is one of goal_watch_apps. */
    static void onAppOpened(Context ctx, String pkg) {
        List<String> apps = Arrays.asList(ctx.getResources().getStringArray(R.array.goal_watch_apps));
        if (pkg == null || pkg.equals(ctx.getPackageName()) || !(apps.contains("*") || apps.contains(pkg))) return;
        if (!Settings.canDrawOverlays(ctx)) return;
        try {
            Intent i = new Intent(ctx, GoalWatchService.class);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i); else ctx.startService(i);
        } catch (Exception ignored) {
        }
    }

    /** Home is in front and the alerts are wanted everywhere (alerts_everywhere): keep them running. */
    static void startEverywhere(Context ctx) {
        if (!Settings.canDrawOverlays(ctx)) return;
        try {
            Intent i = new Intent(ctx, GoalWatchService.class).putExtra("everywhere", true);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i); else ctx.startService(i);
        } catch (Exception ignored) {
        }
    }

    /** Home is back in front: no more alerts over other apps. */
    static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, GoalWatchService.class));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Goal alerts", NotificationManager.IMPORTANCE_MIN));
        }
        Notification n = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_mic)
                .setContentTitle("Goal alerts").setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(7, n);
        if (startedAt == 0) {
            f1Live = Config.themeFlag(this, "f1_live_alerts", false);
            newsAlerts = Config.themeFlag(this, "news_alerts", false);
            frost = Config.themeFlag(this, "frost_alerts", false);
            stillOverApps = Config.themeFlag(this, "still_over_apps", false);
            animateOverApps = Config.themeFlag(this, "animate_over_apps", false);
            liteOverApps = Config.themeFlag(this, "lite_over_apps", false);
            teamAlerts = Config.themeFlag(this, "team_alerts", false);
            incidentAlerts = Config.themeFlag(this, "incident_alerts", false);
            kickoffReminders = Config.themeFlag(this, "kickoff_reminders", false);
            tennisAlerts = Config.themeFlag(this, "tennis_alerts", false);
            ufcAlerts = Config.themeFlag(this, "ufc_alerts", false);
            boxingAlerts = Config.themeFlag(this, "boxing_alerts", false);
            everywhere = Config.themeFlag(this, "alerts_everywhere", false);
            weatherAlerts = Config.themeFlag(this, "weather_alerts", false);
            if (Config.themeFlag(this, "alert_sounds", false)) loadSounds();
        }
        if (intent != null && intent.hasExtra("dump_ui")) AppSearchService.dump();
        if (intent != null && intent.hasExtra("test_tune")) AppSearchService.tune(this, getString(R.string.scores_open_package), intent.getStringExtra("test_tune"));
        if (intent != null && intent.hasExtra("test_search")) {
            AppSearchService.search(this, getString(R.string.trending_package), intent.getStringExtra("test_search"),
                    intent.getBooleanExtra("tv", false));
        }
        if (intent != null && intent.hasExtra("test_more")) testMore(intent.getStringExtra("test_more"));
        if (intent != null && intent.hasExtra("test_goal")) {
            // adb only (the service needs DUMP from outside): one sample alert now, to check the look over an app.
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.league = "PL"; g.home = "LIV"; g.away = "BHA"; g.homeScore = "2"; g.awayScore = "1";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/364.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/331.png";
            // Live, on Sky Sports Premier League, so its "Watch on" step shows too.
            g.state = 0;
            g.slug = "eng.1";
            g.broadcasts.add("Sky Sports Premier League");
            enqueue(g, new String[]{"test", "Mohamed Salah", "67'", "home", ""}, false);
        }
        if (intent != null && intent.hasExtra("test_ft")) {
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.league = "PL"; g.home = "MAN"; g.away = "BOU"; g.homeScore = "3"; g.awayScore = "1";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/360.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/349.png";
            enqueue(g, new String[]{"test-ft", "", "", "", "ft"}, false);
        }
        if (intent != null && intent.hasExtra("test_f1")) {
            io.execute(() -> {
                F1Results.Race r = F1Results.load(intent.getStringExtra("test_f1"));
                if (r != null) main.post(() -> { queue.add(new Object[]{r}); showNext(); });
            });
        }
        if (intent != null && intent.hasExtra("test_f1_live")) {
            // The live session alerts, from a finished weekend's real order ("yyyyMMdd" of its race day).
            io.execute(() -> {
                F1Results.Weekend w = F1Results.weekend(intent.getStringExtra("test_f1_live"));
                if (w == null) return;
                main.post(() -> {
                    for (F1Results.Session s : w.sessions) {
                        if ("Qual".equals(s.abbr)) enqueueParts(sessionOver(w, s), "f1");
                    }
                    for (F1Results.Session s : w.sessions) {
                        if (!"Race".equals(s.abbr)) continue;
                        enqueueParts(sessionStart(w, s), "f1", true);
                        if (s.top.size() > 1) enqueueParts(leads(s, 1, 23), "f1");
                        enqueueParts(lapUpdate(s, 30), "f1");
                    }
                });
            });
        }
        if (intent != null && intent.hasExtra("test_news")) {
            io.execute(() -> {
                List<String[]> items = readNews(true, boxingAlerts);
                main.post(() -> {
                    Set<String> labels = new HashSet<>();
                    for (String[] item : items) if (labels.add(item[0])) enqueueParts(newsLine(item), newsSound(item));
                });
            });
        }
        if (intent != null && intent.hasExtra("test_red")) {
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.league = "UCL"; g.home = "ARS"; g.away = "PSG"; g.homeScore = "1"; g.awayScore = "1";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/359.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/160.png";
            enqueue(g, new String[]{"test-red", "Declan Rice", "81'", "home", ""}, true);
        }
        if (startedAt == 0) {
            startedAt = System.currentTimeMillis();
            main.post(poll);
        }
        // Home has come up: line-ups held over a video can be shown now.
        main.post(this::releaseHeld);
        return START_NOT_STICKY;
    }

    private void poll() {
        if (!everywhere && System.currentTimeMillis() - startedAt > MAX_RUN_MS) { stopSelf(); return; }
        android.os.PowerManager power = getSystemService(android.os.PowerManager.class);
        if (power != null && !power.isInteractive()) {
            // Screen off: read nothing, look again in a minute (the screensaver counts as on).
            main.postDelayed(poll, 60_000L);
            return;
        }
        long now = System.currentTimeMillis();
        // Only the competitions worth interrupting a programme for (goal_alert_leagues), and with team_alerts the
        // leagues and cups the goal_alert_teams clubs play in, for those clubs' games only.
        String[] alertLeagues = getResources().getStringArray(R.array.goal_alert_leagues);
        Set<String> alertTags = new HashSet<>();
        for (String l : alertLeagues) alertTags.add(l.contains("|") ? l.split("\\|", 2)[1].trim() : l.trim());
        Set<String> clubs = new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.goal_alert_teams)));
        List<String> all = new ArrayList<>(Arrays.asList(alertLeagues));
        if (teamAlerts) all.addAll(Arrays.asList(getResources().getStringArray(R.array.team_alert_leagues)));
        String[] leagues = all.toArray(new String[0]);
        boolean incidentsNow = incidentAlerts && polls++ % 3 == 0;
        boolean tennisNow = tennisAlerts && now - tennisAt >= TENNIS_MS;
        boolean ufcNow = ufcAlerts && now - ufcAt >= (ufcLive ? 2 * 60_000L : 15 * 60_000L);
        if (tennisNow) tennisAt = now;
        boolean weatherNow = weatherAlerts && now - weatherAt >= WEATHER_MS;
        if (weatherNow) weatherAt = now;
        String lat = getString(R.string.weather_latitude), lon = getString(R.string.weather_longitude);
        String warnFeed = getString(R.string.weather_warnings_feed), warnArea = getString(R.string.weather_warnings_area);
        if (ufcNow) ufcAt = now;
        // The live F1 alerts include the result panel, so the race alone is read only without them.
        boolean f1 = getResources().getBoolean(R.bool.f1_results_popup) && !f1Live;
        boolean news = (newsAlerts || boxingAlerts) && now - newsAt >= NEWS_MS;
        if (news) newsAt = now;
        io.execute(() -> {
            // One entry per game: a club's Champions League game is in both lists.
            Map<String, ScoresTicker.Game> byId = new java.util.LinkedHashMap<>();
            for (ScoresTicker.Game g : ScoresTicker.load(leagues)) {
                boolean wanted = alertTags.contains(g.league) || clubs.contains(g.homeId) || clubs.contains(g.awayId);
                if (wanted && g.id != null && !byId.containsKey(g.id)) byId.put(g.id, g);
            }
            List<ScoresTicker.Game> games = new ArrayList<>(byId.values());
            Map<String, List<String[]>> incidents = new HashMap<>();
            if (incidentsNow) for (ScoresTicker.Game g : games) if (g.state == 0) incidents.put(g.id, ScoresTicker.incidents(g));
            List<MoreSports.Result> tennis = tennisNow ? MoreSports.tennis() : null;
            List<MoreSports.Result> ufc = ufcNow ? MoreSports.ufc() : null;
            List<String[]> warnings = weatherNow && !warnFeed.isEmpty() ? WeatherAlerts.warnings(warnFeed, warnArea) : null;
            WeatherAlerts.Outlook outlook = weatherNow && !lat.isEmpty() ? WeatherAlerts.outlook(lat, lon) : null;
            F1Results.Race race = f1 ? F1Results.load(null) : null;
            F1Results.Weekend weekend = f1Live ? F1Results.weekend(null) : null;
            List<String[]> headlines = news ? readNews(newsAlerts, boxingAlerts) : null;
            boolean f1On = false;
            for (F1Results.Session s : weekend == null ? new ArrayList<F1Results.Session>() : weekend.sessions) {
                f1On |= "in".equals(s.state);
            }
            final boolean racing = f1On;
            main.post(() -> {
                if (race != null) {
                    boolean finished = race.id.equals(f1Id) && "in".equals(f1State) && "post".equals(race.state);
                    f1Id = race.id;
                    f1State = race.state;
                    if (finished) main.postDelayed(() -> { queue.add(new Object[]{race}); showNext(); }, DELAY_MS);
                }
                if (weekend != null) f1Updates(weekend);
                if (headlines != null) newsUpdates(headlines);
                if (warnings != null) for (String[] w : warnings) if (seenWarnings.add(w[0])) enqueueParts(warningLine(w), "weather");
                if (outlook != null) weatherUpdates(outlook);
                if (tennis != null) { otherUpdates(tennis, tennisPrimed); if (!tennis.isEmpty()) tennisPrimed = true; }
                if (ufc != null) {
                    ufcLive = false;
                    for (MoreSports.Result r : ufc) ufcLive |= "in".equals(r.state);
                    otherUpdates(ufc, ufcPrimed);
                    if (!ufc.isEmpty()) ufcPrimed = true;
                }
                for (ScoresTicker.Game g : games) {
                    if (kickoffReminders && alertTags.contains(g.league) && g.state == 1 && g.kickoff - now > 0
                            && g.kickoff - now <= REMIND_MS && reminded.add(g.id)) {
                        enqueueReminder(g);
                    }
                    List<String[]> found = incidents.get(g.id);
                    if (found == null) continue;
                    boolean first = incidentGames.add(g.id);
                    for (String[] inc : found) {
                        if (seen.add(inc[0]) && !first) main.postDelayed(() -> enqueueFootball(g, incidentHead(inc), "incident"), DELAY_MS);
                    }
                }
            });
            main.post(() -> {
                boolean live = racing;
                for (ScoresTicker.Game g : games) if (g.id != null) latest.put(g.id, g);
                for (ScoresTicker.Game g : games) {
                    live |= g.state == 0;
                    Integer before = g.id == null ? null : lastState.put(g.id, g.state);
                    if (primed && before != null && before == 0 && g.state == 2) {
                        main.postDelayed(() -> enqueue(g, new String[]{"FT|" + g.id, "", "", "", "ft"}, false), DELAY_MS);
                    }
                    for (String[] goal : g.goals) {
                        if (seen.add(goal[0]) && primed) main.postDelayed(() -> enqueue(g, goal, false), DELAY_MS);
                    }
                    for (String[] card : g.reds) {
                        if (seen.add(card[0]) && primed) main.postDelayed(() -> enqueue(g, card, true), DELAY_MS);
                    }
                }
                if (!games.isEmpty()) primed = true;
                main.postDelayed(poll, live ? POLL_MS : 5 * POLL_MS);
            });
        });
    }

    // ---- F1 ----------------------------------------------------------------------------------------

    /**
     * Compares the weekend with the last read: a session starting, a new leader, every ten laps of a race, and the
     * end (the race's top ten, else the top three). The first read only learns where things stand.
     */
    private void f1Updates(F1Results.Weekend w) {
        for (F1Results.Session s : w.sessions) {
            if (!s.counts()) continue;
            String before = f1States.put(s.id, s.state);
            String leader = s.top.isEmpty() ? null : s.top.get(0);
            String lastLeader = leader == null ? f1Leaders.get(s.id) : f1Leaders.put(s.id, leader);
            Integer lastMark = f1Laps.put(s.id, s.lap / 10);
            if (!f1Primed || before == null) continue;
            if ("pre".equals(before) && "in".equals(s.state)) {
                enqueueParts(sessionStart(w, s), "f1", true);
            } else if ("in".equals(before) && "post".equals(s.state)) {
                if ("Race".equals(s.abbr)) {
                    F1Results.Race r = new F1Results.Race();
                    r.id = s.id; r.name = w.name; r.state = s.state;
                    r.top.addAll(s.top);
                    r.logos.addAll(s.logos);
                    queue.add(new Object[]{r});
                    showNext();
                } else {
                    enqueueParts(sessionOver(w, s), "f1");
                }
            } else if ("in".equals(s.state)) {
                if (leader != null && lastLeader != null && !leader.equals(lastLeader)) enqueueParts(leads(s, 0, s.lap), "f1", true);
                if (s.isRace() && lastMark != null && s.lap / 10 > lastMark && s.lap > 0) enqueueParts(lapUpdate(s, s.lap), "f1", true);
            }
        }
        if (!w.sessions.isEmpty()) f1Primed = true;
    }

    /** "[F1] 🚦 LIGHTS OUT · United States GP" for a race, "⏱ QUALIFYING · ..." for the others. */
    private static List<Object> sessionStart(F1Results.Weekend w, F1Results.Session s) {
        List<Object> parts = f1Parts();
        parts.add(bold((s.isRace() ? "🚦  LIGHTS OUT" : "⏱  " + s.label) + (s.isRace() && !"Race".equals(s.abbr) ? " · SPRINT" : "")));
        parts.add(8);
        parts.add(w.name.replace("Grand Prix", "GP"));
        return parts;
    }

    /** "🏎 [logo] ANT leads · LAP 23"; in qualifying "⏱ [logo] ANT goes P1". */
    private static List<Object> leads(F1Results.Session s, int index, int lap) {
        List<Object> parts = f1Parts();
        parts.add(bold(s.isRace() ? "🏎" : "⏱"));
        parts.add(8);
        parts.add(new Pic(s.logos.get(index)));
        parts.add(4);
        parts.add(bold(F1Results.code(s.top.get(index))));
        parts.add(4);
        parts.add(s.isRace() ? "leads" : "goes P1");
        if (s.isRace() && lap > 0) { parts.add(8); parts.add(new Small("LAP " + lap)); }
        else { parts.add(8); parts.add(new Small(s.label)); }
        return parts;
    }

    /** "LAP 30 · 1 [logo] ANT  2 [logo] VER  3 [logo] HAM". */
    private static List<Object> lapUpdate(F1Results.Session s, int lap) {
        List<Object> parts = f1Parts();
        parts.add(bold("🏎  LAP " + lap));
        podium(parts, s);
        parts.add(8);
        parts.add(new Small(s.label));
        return parts;
    }

    /** "🏁 POLE · 1 ANT 2 VER 3 HAM" after qualifying; the sprint and sprint qualifying likewise. */
    private static List<Object> sessionOver(F1Results.Weekend w, F1Results.Session s) {
        List<Object> parts = f1Parts();
        String what = "Qual".equals(s.abbr) ? "POLE" : "SS".equals(s.abbr) ? "SPRINT POLE" : "SR".equals(s.abbr) ? "SPRINT" : s.label;
        parts.add(bold("🏁  " + what));
        podium(parts, s);
        parts.add(8);
        parts.add(new Small(w.name.replace("Grand Prix", "GP")));
        return parts;
    }

    /** Every F1 line opens with the F1 logo. */
    private static List<Object> f1Parts() {
        List<Object> parts = new ArrayList<>();
        parts.add(new Pic(F1Results.F1_LOGO, true));
        parts.add(8);
        return parts;
    }

    /** The top three: position, team logo, code. */
    private static void podium(List<Object> parts, F1Results.Session s) {
        int[] medal = {0xFFB8860B, 0xFF6B7280, 0xFF9A5B2E};
        for (int i = 0; i < Math.min(3, s.top.size()); i++) {
            parts.add(i == 0 ? 10 : 12);
            SpannableStringBuilder pos = new SpannableStringBuilder(String.valueOf(i + 1));
            pos.setSpan(new StyleSpan(Typeface.BOLD), 0, pos.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            pos.setSpan(new ForegroundColorSpan(medal[i]), 0, pos.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            parts.add(pos);
            parts.add(4);
            parts.add(new Pic(s.logos.get(i)));
            parts.add(3);
            parts.add(F1Results.code(s.top.get(i)));
        }
    }

    // ---- news --------------------------------------------------------------------------------------

    /** The newest few stories of each news_alert_feeds feed (and boxing's): {label, headline, link}. Off the main thread. */
    private List<String[]> readNews(boolean news, boolean boxing) {
        List<String[]> out = new ArrayList<>();
        List<String> feeds = new ArrayList<>();
        if (news) feeds.addAll(Arrays.asList(getResources().getStringArray(R.array.news_alert_feeds)));
        if (boxing) feeds.add(BOXING_FEED);
        for (String entry : feeds) {
            String[] parts = entry.split("\\|", 2);
            boolean fight = entry.equals(BOXING_FEED);
            // Breaking news only: the local feed is left out, and only a feed's lead story counts.
            if (!fight && parts[0].contains("/manchester/")) continue;
            List<String[]> stories = NewsPanel.read(parts[0].trim());
            for (int i = 0; i < Math.min(fight ? 5 : 1, stories.size()); i++) {
                String[] st = stories.get(i);
                // Every lead story is learnt (so a restart does not replay it); only a breaking one pops up.
                out.add(new String[]{parts.length > 1 ? parts[1].trim() : "NEWS", st[0], st[2] == null ? st[0] : st[2],
                        fight || breaking(st) ? "pop" : ""});
            }
        }
        return out;
    }

    /**
     * Breaking news: a feed's new lead story, published within the last 45 minutes, that is news rather than a
     * feature (a quiz, a video, a live page's daily round-up, "in pictures").
     */
    private static boolean breaking(String[] st) {
        String t = st[0].toLowerCase(java.util.Locale.ROOT);
        for (String f : new String[]{"watch:", "quiz", "in pictures", "listen:", "podcast", "week in", "your questions",
                "what we know", "the papers", "newspaper headlines", "how to ", "explained", "analysis:", "opinion"}) {
            if (t.startsWith(f) || t.contains(" " + f)) return false;
        }
        if (st.length < 4 || st[3] == null) return false;
        try {
            long at = java.time.ZonedDateTime.parse(st[3], java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli();
            return System.currentTimeMillis() - at < 45 * 60_000L;
        } catch (Exception e) {
            return false;
        }
    }

    /** A story new to the top five of its feed pops up; at most one per feed each read, the first read only learns. */
    private void newsUpdates(List<String[]> items) {
        Set<String> shownFeeds = new HashSet<>();
        for (String[] item : items) {
            if (seenNews.add(item[2]) && newsPrimed && "pop".equals(item[3]) && shownFeeds.add(item[0])) {
                List<Object> line = newsLine(item);
                if (!"BOXING".equals(item[0])) { line.add(1, 8); line.add(2, bold("BREAKING")); }
                enqueueParts(line, newsSound(item));
            }
        }
        if (!items.isEmpty()) newsPrimed = true;
    }

    private static String newsSound(String[] item) {
        return "BOXING".equals(item[0]) ? "fight" : "news";
    }

    private static String otherSound(MoreSports.Result r) {
        return "UFC".equals(r.tour) ? "fight" : "tennis";
    }

    /** The BBC's blocks with NEWS or SPORT, then the headline; boxing's with a glove. */
    private List<Object> newsLine(String[] item) {
        float d = getResources().getDisplayMetrics().density;
        List<Object> parts = new ArrayList<>();
        boolean sport = item[0].contains("SPORT") || "BOXING".equals(item[0]);
        parts.add(Badges.bbc(d, sport ? "SPORT" : item[0].replace("BBC ", ""), sport ? 0xFF8A6100 : 0xFFB80000));
        if ("BOXING".equals(item[0])) { parts.add(8); parts.add(bold("🥊  BOXING")); }
        parts.add(8);
        parts.add(item[1]);
        return parts;
    }

    // ---- weather -----------------------------------------------------------------------------------

    /** Rain on its way (at most every three hours), and the day's forecast once each morning. */
    private void weatherUpdates(WeatherAlerts.Outlook o) {
        long now = System.currentTimeMillis();
        String place = getString(R.string.weather_place);
        if (o.rainFrom > 0 && now - rainAlertAt > 3 * 60 * 60_000L) {
            rainAlertAt = now;
            List<Object> parts = new ArrayList<>();
            parts.add(weatherIcon(61));
            parts.add(8);
            parts.add(bold("Rain from about " + java.time.Instant.ofEpochMilli(o.rainFrom).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))));
            parts.add(8);
            parts.add(new Small(place.toUpperCase(java.util.Locale.getDefault())));
            enqueueParts(parts, "weather");
        }
        java.time.LocalDateTime local = java.time.LocalDateTime.now();
        String today = local.toLocalDate().toString();
        if (!today.equals(morningShown) && local.getHour() >= 6 && local.getHour() < 11) {
            morningShown = today;
            enqueueParts(forecastLine(o, place), "weather");
        }
    }

    /** "[icon] Today 12° / 6°  Showers  ☂ 70%  MANCHESTER". */
    private List<Object> forecastLine(WeatherAlerts.Outlook o, String place) {
        List<Object> parts = new ArrayList<>();
        parts.add(weatherIcon(o.code));
        parts.add(8);
        parts.add(bold("Today  " + o.high + "° / " + o.low + "°"));
        parts.add(8);
        parts.add(WeatherLine.describe(o.code));
        if (o.rainChance >= 0) { parts.add(8); parts.add("☂ " + o.rainChance + "%"); }
        parts.add(8);
        parts.add(new Small(place.toUpperCase(java.util.Locale.getDefault())));
        return parts;
    }

    /** The weather line's own icon, as a picture in the pill. */
    private android.graphics.drawable.Drawable weatherIcon(int code) {
        // The light-background version: its own colours, with grey clouds that read on the silver glass.
        android.graphics.drawable.Drawable d = getDrawable(WeatherLine.lightIcon(code, true));
        return d == null ? null : d.mutate();
    }

    /** "[Met Office icon] YELLOW WIND WARNING  Greater Manchester  from 0300 Tue 27 Oct to 2359 Tue 27 Oct". */
    private List<Object> warningLine(String[] w) {
        String title = w[1];
        String colour = title.contains(" ") ? title.substring(0, title.indexOf(' ')) : "";
        int of = title.indexOf("warning of "), aff = title.indexOf(" affecting");
        String kind = of >= 0 && aff > of ? title.substring(of + "warning of ".length(), aff) : "";
        String head = (colour + " " + kind + " warning").trim().toUpperCase(java.util.Locale.ROOT);
        SpannableStringBuilder s = new SpannableStringBuilder("⚠️  " + head);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int tint = colour.equalsIgnoreCase("red") ? 0xFFC81E1E : colour.equalsIgnoreCase("amber") ? 0xFFC2410C : 0xFF8A6100;
        s.setSpan(new ForegroundColorSpan(tint), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        List<Object> parts = new ArrayList<>();
        if (w[3] != null) { parts.add(new Pic(w[3])); parts.add(8); }
        parts.add(s);
        parts.add(8);
        parts.add(getString(R.string.weather_warnings_area));
        int valid = w[2].indexOf("valid ");
        if (valid >= 0) { parts.add(8); parts.add(new Small(w[2].substring(valid + "valid ".length()))); }
        return parts;
    }

    // ---- football extras, tennis and UFC -----------------------------------------------------------

    /** "⏰ 15 MIN · crest v crest · 15:00 · league". */
    private static List<Object> kickoffLine(ScoresTicker.Game g) {
        List<Object> parts = new ArrayList<>();
        long mins = Math.max(1, Math.round((g.kickoff - System.currentTimeMillis()) / 60_000.0));
        parts.add(bold("⏰  KICK-OFF IN " + mins + " MIN"));
        parts.add(10);
        parts.add(new Pic(g.homeLogo));
        parts.add(4);
        parts.add(bold(g.home));
        parts.add(6);
        parts.add("v");
        parts.add(6);
        parts.add(new Pic(g.awayLogo));
        parts.add(4);
        parts.add(bold(g.away));
        parts.add(8);
        parts.add(new Pic(lightLogo(g.leagueLogo)));
        return parts;
    }

    /** "❌ Pen missed · Salah 67'" before the game's crests and score. */
    private static CharSequence incidentHead(String[] inc) {
        SpannableStringBuilder s = new SpannableStringBuilder(inc[1]);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (!inc[2].isEmpty()) {
            s.append("  ");
            int at = s.length();
            s.append(surname(inc[2]));
            s.setSpan(new StyleSpan(Typeface.BOLD), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (!inc[3].isEmpty()) s.append("  ").append(inc[3]);
        return s;
    }

    /** ESPN's league logos come in a white version for dark backgrounds; the alerts' glass is light. */
    private static String lightLogo(String url) {
        return url == null ? null : url.replace("/500-dark/", "/500/");
    }

    /** A match or fight that ended (any state to post) or a final or main event that began (pre to in). */
    private void otherUpdates(List<MoreSports.Result> results, boolean primedYet) {
        for (MoreSports.Result r : results) {
            String before = otherStates.put(r.id, r.state);
            if (!primedYet || before == null || before.equals(r.state)) continue;
            if ("post".equals(r.state)) enqueueParts(resultLine(r), otherSound(r));
            else if ("pre".equals(before) && "in".equals(r.state) && (r.mainEvent || "Final".equals(r.round))) {
                enqueueParts(startLine(r), otherSound(r));
            }
        }
    }

    /** The tour's mark: the UFC logo, or ATP / WTA on their colours. */
    private Object mark(MoreSports.Result r) {
        float d = getResources().getDisplayMetrics().density;
        if ("UFC".equals(r.tour)) return new Pic(MoreSports.UFC_LOGO, true);
        return "WTA".equals(r.tour) ? Badges.tile(d, "WTA", 0xFF4B2683, 0xFFFFFFFF) : Badges.tile(d, "ATP", 0xFF0B2A5B, 0xFFFFFFFF);
    }

    /** "ATP 🎾 [flag] Sinner bt [flag] Alcaraz 6-4 7-6 · SF"; "UFC [flag] Steele def. [flag] Gugnon KO/TKO · R1 3:54". */
    private List<Object> resultLine(MoreSports.Result r) {
        boolean ufc = "UFC".equals(r.tour);
        List<Object> parts = new ArrayList<>();
        parts.add(mark(r));
        parts.add(8);
        parts.add(bold(ufc ? "🥊" : "🎾"));
        parts.add(8);
        parts.add(new Pic(r.winnerFlag));
        parts.add(4);
        parts.add(bold(r.winner));
        parts.add(5);
        parts.add(ufc ? "def." : "bt");
        parts.add(5);
        parts.add(new Pic(r.loserFlag));
        parts.add(4);
        parts.add(r.loser);
        parts.add(8);
        parts.add(bold(r.score));
        parts.add(8);
        parts.add(new Small(ufc ? (r.mainEvent ? "MAIN EVENT" : r.round.toUpperCase(java.util.Locale.ROOT)) : roundTag(r.round) + " · " + r.event));
        return parts;
    }

    /** "ATP 🎾 FINAL under way · [flag] Sinner v [flag] Alcaraz"; UFC's main event likewise. */
    private List<Object> startLine(MoreSports.Result r) {
        boolean ufc = "UFC".equals(r.tour);
        List<Object> parts = new ArrayList<>();
        parts.add(mark(r));
        parts.add(8);
        parts.add(bold(ufc ? "🥊  MAIN EVENT" : "🎾  FINAL"));
        parts.add(5);
        parts.add("under way");
        parts.add(8);
        parts.add(new Pic(r.winnerFlag));
        parts.add(4);
        parts.add(bold(r.winner));
        parts.add(5);
        parts.add("v");
        parts.add(5);
        parts.add(new Pic(r.loserFlag));
        parts.add(4);
        parts.add(bold(r.loser));
        parts.add(8);
        parts.add(new Small(r.event));
        return parts;
    }

    private static String roundTag(String round) {
        switch (round) {
            case "Quarterfinal": return "QF";
            case "Semifinal": return "SF";
            case "Final": return "FINAL";
            default: return "R16";
        }
    }

    /** adb only: sample alerts from real data ("team", "incident", "kickoff", "tennis", "ufc", "boxing" or "all"). */
    private void testMore(String what) {
        boolean all = "all".equals(what);
        if (all || "team".equals(what)) {
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.league = "LALIGA"; g.home = "BAR"; g.away = "RMA"; g.homeScore = "1"; g.awayScore = "0";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/83.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/86.png";
            g.leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500-dark/15.png";
            enqueue(g, new String[]{"test-team", "Lamine Yamal", "34'", "home", ""}, false);
        }
        if (all || "incident".equals(what)) {
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.league = "UCL"; g.home = "PSG"; g.away = "MUN"; g.homeScore = "2"; g.awayScore = "2";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/160.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/132.png";
            g.leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500-dark/2.png";
            enqueueFootball(g, incidentHead(new String[]{"t", "🧤  Pen saved", "Manuel Neuer", "88'"}), "incident");
        }
        if (all || "weather".equals(what)) {
            String lat = getString(R.string.weather_latitude), lon = getString(R.string.weather_longitude);
            io.execute(() -> {
                WeatherAlerts.Outlook o = WeatherAlerts.outlook(lat, lon);
                main.post(() -> {
                    if (o != null) enqueueParts(forecastLine(o, getString(R.string.weather_place)), "weather");
                    List<Object> rain = new ArrayList<>();
                    rain.add(weatherIcon(61)); rain.add(8); rain.add(bold("Rain from about 15:30")); rain.add(8);
                    rain.add(new Small(getString(R.string.weather_place).toUpperCase(java.util.Locale.getDefault())));
                    enqueueParts(rain, "weather");
                    enqueueParts(warningLine(new String[]{"t", "Yellow warning of wind affecting North West England",
                            "Yellow warning of wind affecting North West England: Greater Manchester valid from 0300 Tue 27 Oct to 2359 Tue 27 Oct",
                            "https://data.consumer-digital.api.metoffice.gov.uk/v1/warnings/rss/image/yellow-wind.png"}), "weather");
                });
            });
        }
        if (all || "kickoff".equals(what)) {
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.league = "PL"; g.home = "ARS"; g.away = "CHE";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/359.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/363.png";
            g.leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500-dark/23.png";
            g.kickoff = System.currentTimeMillis() + REMIND_MS;
            g.id = "test";
            // The guide part of the test looks up a real listing: Liverpool v Arsenal, 1 November 2026, 16:30 UK.
            g.home = "LIV"; g.away = "ARS"; g.homeName = "Liverpool"; g.awayName = "Arsenal";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/364.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/359.png";
            g.tvAt = 1_793_554_200_000L;
            enqueueReminder(g);
        }
        io.execute(() -> {
            List<MoreSports.Result> tennis = all || "tennis".equals(what) ? MoreSports.tennis() : new ArrayList<>();
            List<MoreSports.Result> ufc = all || "ufc".equals(what) ? MoreSports.ufc() : new ArrayList<>();
            List<String[]> boxing = all || "boxing".equals(what) ? readNews(false, true) : new ArrayList<>();
            main.post(() -> {
                for (MoreSports.Result r : tennis) if ("post".equals(r.state) && "Final".equals(r.round)) { enqueueParts(resultLine(r), otherSound(r)); break; }
                // The latest finished main event: next weekend's card has no result yet.
                for (MoreSports.Result r : ufc) if (r.mainEvent && "post".equals(r.state)) { enqueueParts(startLine(r), otherSound(r)); enqueueParts(resultLine(r), otherSound(r)); break; }
                if (!boxing.isEmpty()) enqueueParts(newsLine(boxing.get(0)), "fight");
            });
        });
    }

    // ---- the alert ---------------------------------------------------------------------------------

    private void enqueue(ScoresTicker.Game g, String[] goal, boolean red) {
        boolean scored = !red && !"ft".equals(goal[4]) && ("home".equals(goal[3]) || "away".equals(goal[3]));
        enqueueFootball(g, headline(goal, red), red ? "red" : "ft".equals(goal[4]) ? "fulltime" : "goal", scored ? goal[3] : null);
    }

    private void enqueueFootball(ScoresTicker.Game g, CharSequence head, String sound) {
        enqueueFootball(g, head, sound, null);
    }

    /**
     * Fetches both crests and the competition's logo (cached on disk by the scores line), then queues the alert;
     * scoredBy is the side whose goal it is, for the score's roll.
     */
    private void enqueueFootball(ScoresTicker.Game g, CharSequence head, String sound, String scoredBy) {
        int px = Math.round(LOGO_DP * getResources().getDisplayMetrics().density);
        io.execute(() -> {
            android.graphics.Bitmap home = g.homeLogo == null ? null : ScoresTicker.cachedLogo(this, g.homeLogo, px);
            android.graphics.Bitmap away = g.awayLogo == null ? null : ScoresTicker.cachedLogo(this, g.awayLogo, px);
            String light = lightLogo(g.leagueLogo);
            android.graphics.Bitmap league = light == null ? null : ScoresTicker.cachedLogo(this, light, px);
            // While the game is on: where to watch it, for the pill's second step.
            Object[] watch = g.state == 0 ? watchOn(TvGuide.watch(getResources().getStringArray(R.array.football_tv_channels), g)) : null;
            main.post(() -> {
                queue.add(new Object[]{head, g, home, away, league, sound, scoredBy, watch});
                showNext();
            });
        });
    }

    /**
     * The kick-off reminder, with the two line-ups as its second page when they are out: the pill shows first, then
     * flips up to both starting elevens with each player's picture.
     */
    private void enqueueReminder(ScoresTicker.Game g) {
        List<Object> parts = kickoffLine(g);
        int px = Math.round(LOGO_DP * getResources().getDisplayMetrics().density);
        String[] channels = getResources().getStringArray(R.array.football_tv_channels);
        io.execute(() -> {
            ScoresTicker.Side[] sides = "test".equals(g.id) ? testSides(g) : ScoresTicker.lineups(g);
            // "Live on" and the channel, when a British channel lists the game.
            TvGuide.Channel tv = TvGuide.orAbroad(TvGuide.find(channels, g.homeName, g.awayName, g.tvAt != 0 ? g.tvAt : g.kickoff), g);
            // The last page, after the line-ups: the two clubs, "LIVE ON" and the channel.
            List<Object> live = null;
            if (tv != null) {
                live = new ArrayList<>();
                live.add(new Pic(g.homeLogo)); live.add(4); live.add(bold(g.home)); live.add(6); live.add("v"); live.add(6);
                live.add(new Pic(g.awayLogo)); live.add(4); live.add(bold(g.away)); live.add(14);
                live.add(bold("📺  LIVE ON")); live.add(8); live.add(new Chip(tv.logo, tv.name));
            }
            List<Object> ready = resolve(parts, px);
            List<Object> livePage = live == null ? null : resolve(live, px);
            // Every picture at once, and no more than fifteen seconds for all of them: on a slow connection a player
            // whose photo has not come shows his shirt number instead.
            Map<String, android.graphics.Bitmap> pictures = new java.util.concurrent.ConcurrentHashMap<>();
            if (sides != null) {
                List<String> urls = new ArrayList<>();
                for (ScoresTicker.Side side : sides) {
                    if (side.crest != null) urls.add(side.crest);
                    for (String[] p : side.players) if (p[1] != null && !urls.contains(p[1])) urls.add(p[1]);
                }
                ExecutorService pool = Executors.newFixedThreadPool(6, LOW);
                List<java.util.concurrent.Callable<Void>> jobs = new ArrayList<>();
                for (String url : urls) jobs.add(() -> {
                    android.graphics.Bitmap b = ScoresTicker.cachedLogo(this, url, px);
                    if (b != null) pictures.put(url, b);
                    return null;
                });
                // ESPN has photos for only some players: the rest from TheSportsDB, filed under the same key.
                for (ScoresTicker.Side side : sides) for (String[] p : side.players) {
                    if (p[1] == null || p.length < 5) continue;
                    jobs.add(() -> {
                        if (pictures.containsKey(p[1])) return null;
                        android.graphics.Bitmap b = PlayerPhotos.find(this, p[4], side.name, px);
                        if (b != null) pictures.putIfAbsent(p[1], b);
                        return null;
                    });
                }
                try { pool.invokeAll(jobs, 15, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                pool.shutdownNow();
            }
            main.post(() -> {
                queue.add(new Object[]{ready, "reminder", sides, pictures, livePage});
                showNext();
            });
        });
    }

    /** adb test only: the elevens of the last Premier League game, so the page can be seen on any day. */
    private static ScoresTicker.Side[] testSides(ScoresTicker.Game g) {
        ScoresTicker.Game last = ScoresTicker.lastFinished("eng.1", "PL", 7);
        return last == null ? null : ScoresTicker.lineups(last);
    }

    /** The line's pictures fetched: Pics to Imgs, Chips to channel logos drawn for the light pill. Off the main thread. */
    private List<Object> resolve(List<Object> parts, int px) {
        List<Object> ready = new ArrayList<>();
        for (Object o : parts) {
            if (o instanceof Chip) {
                android.graphics.Bitmap logo = ((Chip) o).url == null ? null : ScoresTicker.cachedLogo(this, ((Chip) o).url, px * 4);
                ready.add(logo != null ? new ChipImg(inked(logo)) : bold(((Chip) o).name));
            } else if (o instanceof Pic) {
                Pic pic = (Pic) o;
                ready.add(new Img(pic.url == null ? null : ScoresTicker.cachedLogo(this, pic.url, px * (pic.wide ? 2 : 1)), pic.wide));
            } else ready.add(o);
        }
        return ready;
    }

    /**
     * A channel logo made for dark screens, for the light pill: its white and grey lettering that stands on the
     * transparent background drawn in the pill's dark ink; lettering inside a coloured box (Sky's blue and red) stays
     * white, and the colours and transparent parts stay as they are. A white shape counts as standing on the
     * background when any of its pixels touches a transparent one.
     */
    private static android.graphics.Bitmap inked(android.graphics.Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        boolean[] light = new boolean[px.length];
        for (int i = 0; i < px.length; i++) {
            int c = px[i], a = c >>> 24;
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            light[i] = a > 40 && max - min < 40 && max > 120;
        }
        boolean[] seen = new boolean[px.length];
        int[] stack = new int[px.length];
        for (int start = 0; start < px.length; start++) {
            if (!light[start] || seen[start]) continue;
            // One white shape, and whether it borders the transparent background.
            int top = 0, count = 0;
            boolean onBackground = false;
            stack[top++] = start;
            seen[start] = true;
            int[] members = new int[64];
            while (top > 0) {
                int i = stack[--top];
                if (count == members.length) members = java.util.Arrays.copyOf(members, count * 2);
                members[count++] = i;
                int x = i % w, y = i / w;
                int[] around = {x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, y > 0 ? i - w : -1, y < h - 1 ? i + w : -1};
                for (int n : around) {
                    if (n < 0) { onBackground = true; continue; }
                    if ((px[n] >>> 24) <= 40) onBackground = true;
                    else if (light[n] && !seen[n]) { seen[n] = true; stack[top++] = n; }
                }
            }
            if (!onBackground) continue;
            for (int k = 0; k < count; k++) px[members[k]] = (px[members[k]] & 0xFF000000) | 0x1A1D24;
        }
        return android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888);
    }

    /** Fetches the line's pictures, then queues it with its sound. */
    private void enqueueParts(List<Object> parts, String sound) {
        enqueueParts(parts, sound, false);
    }

    /** As enqueueParts; f1Live adds the "Watch on" step (Sky Sports F1 and the rest) after the line. */
    private void enqueueParts(List<Object> parts, String sound, boolean f1Live) {
        int px = Math.round(LOGO_DP * getResources().getDisplayMetrics().density);
        io.execute(() -> {
            Object[] watch = null;
            if (f1Live) {
                ScoresTicker.Game f1 = new ScoresTicker.Game();
                f1.leagueLogo = F1Results.F1_LOGO;
                watch = watchOn(TvGuide.watch(new String[0], f1));
            }
            final Object[] watching = watch;
            List<Object> ready = new ArrayList<>();
            for (Object o : parts) {
                if (o instanceof Pic) {
                    Pic pic = (Pic) o;
                    ready.add(new Img(pic.url == null ? null : ScoresTicker.cachedLogo(this, pic.url, px * (pic.wide ? 2 : 1)), pic.wide));
                }
                else ready.add(o);
            }
            main.post(() -> {
                queue.add(watching == null ? new Object[]{ready, sound} : new Object[]{ready, sound, watching});
                showNext();
            });
        });
    }

    /** {names, logo} for the "Watch on" step, the channel's logo fetched; null without anywhere to watch. */
    private Object[] watchOn(TvGuide.Watch w) {
        if (w == null || w.names.isEmpty()) return null;
        int px = Math.round(96 * getResources().getDisplayMetrics().density);
        android.graphics.Bitmap logo = w.logo == null ? null : ScoresTicker.cachedLogo(this, w.logo, px);
        return new Object[]{w.names, logo};
    }

    private static CharSequence bold(String text) {
        SpannableStringBuilder s = new SpannableStringBuilder(text);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private static CharSequence headline(String[] goal, boolean red) {
        if ("ft".equals(goal[4])) {
            SpannableStringBuilder ft = new SpannableStringBuilder("🏁  FT");
            ft.setSpan(new StyleSpan(Typeface.BOLD), 0, ft.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return ft;
        }
        SpannableStringBuilder s = new SpannableStringBuilder(red ? "🟥  " : "⚽  ");
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(red ? 0xFFC81E1E : 0xFF15803D), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int at = s.length();
        s.append(surname(goal[1]));
        s.setSpan(new StyleSpan(Typeface.BOLD), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.append("  ").append(goal[2]);
        if (!goal[4].isEmpty()) s.append(goal[4].equals("pen") ? " (pen)" : " (og)");
        return s;
    }

    private void ensureHost(int widthDp, int heightDp) {
        if (host != null) return;
        float d = getResources().getDisplayMetrics().density;
        boolean software = calm() || lite();
        host = new android.widget.FrameLayout(software ? new SoftwareWindow(this) : this);
        // A fixed size, set once: a window that changes size over a playing video is reallocated and re-planned
        // by the display, which shows as a hitch; this one only ever changes what it draws.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(Math.round(widthDp * d), Math.round(heightDp * d),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Not FLAG_NOT_TOUCHABLE: Android 12+ caps such an overlay at 80% opacity, which let the picture
                // through the glass. A TV has no touch screen, and not focusable keeps every key with the app.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | (software ? 0 : WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED),
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.x = Math.round(32 * d);
        // Above the screensaver's clock and caption while it runs.
        lp.y = Math.round((dreaming ? 92 : 30) * d);
        try {
            getSystemService(WindowManager.class).addView(host, lp);
        } catch (Exception e) {
            host = null;
        }
    }

    /**
     * Over another app the alert is drawn by the CPU. A window the graphics chip draws gets compressed buffers that
     * the TCL's spare display planes cannot show, so the TV composes the app's layer and the alert on the GPU while
     * it is up, and the video underneath misses frames whenever that starts and stops. A plain buffer goes on a plane
     * of its own, and the app keeps its hardware composition. Android picks software drawing for a window with no
     * parent from the application's hardware-acceleration flag, read through the root view's context: this one says no.
     */
    private static final class SoftwareWindow extends android.content.ContextWrapper {
        private final android.content.pm.ApplicationInfo info;

        SoftwareWindow(android.content.Context base) {
            super(base);
            info = new android.content.pm.ApplicationInfo(base.getApplicationInfo());
            info.flags &= ~android.content.pm.ApplicationInfo.FLAG_HARDWARE_ACCELERATED;
        }

        @Override public android.content.pm.ApplicationInfo getApplicationInfo() { return info; }
    }

    /** "Mohamed Salah" to "Salah", "Virgil van Dijk" to "van Dijk": everything after the first name. */
    static String surname(String name) {
        String n = name == null ? "" : name.trim();
        int space = n.indexOf(' ');
        return space > 0 ? n.substring(space + 1) : n;
    }

    /**
     * The alerts' glass. Silver: a light metallic gradient, brighter at the top like brushed aluminium, under the
     * Home pills' rim and sheen. Frosted (frost_alerts): paler and milkier, a fine grain like etched glass, a bright
     * rim and a soft highlight over the top half.
     */
    private android.graphics.drawable.Drawable glass(float radius) {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                frost ? new int[]{0xFFFBFCFE, 0xFFE6EAEF, 0xFFD3D9E1} : new int[]{0xFFE9ECF1, 0xFFB9BFC8});
        base.setCornerRadius(radius);
        if (!frost) {
            return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                    base, getDrawable(R.drawable.glass_box).mutate()});
        }
        float d = getResources().getDisplayMetrics().density;
        float[] radii = new float[8];
        Arrays.fill(radii, radius);
        android.graphics.drawable.ShapeDrawable grain = new android.graphics.drawable.ShapeDrawable(
                new android.graphics.drawable.shapes.RoundRectShape(radii, null, null));
        grain.getPaint().setShader(new android.graphics.BitmapShader(grain(),
                android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT));
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x80FFFFFF, 0x1AFFFFFF, 0x00FFFFFF, 0x00FFFFFF});
        sheen.setCornerRadius(radius);
        GradientDrawable rim = new GradientDrawable();
        rim.setCornerRadius(radius);
        rim.setStroke(Math.max(1, Math.round(1 * d)), 0xF2FFFFFF);
        GradientDrawable edge = new GradientDrawable();
        edge.setCornerRadius(radius);
        edge.setStroke(Math.max(1, Math.round(0.5f * d)), 0x33707884);
        return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                base, grain, sheen, edge, rim});
    }

    private static android.graphics.Bitmap grainTile;

    /** A 64 px tile of faint light and dark specks, the frosted look's grain. */
    private static android.graphics.Bitmap grain() {
        if (grainTile != null) return grainTile;
        int size = 64;
        int[] px = new int[size * size];
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < px.length; i++) {
            int a = r.nextInt(18);
            px[i] = r.nextBoolean() ? (a << 24) | 0xFFFFFF : ((a / 2) << 24) | 0x404650;
        }
        grainTile = android.graphics.Bitmap.createBitmap(px, size, size, android.graphics.Bitmap.Config.ARGB_8888);
        return grainTile;
    }

    @SuppressWarnings("unchecked")
    private void showNext() {
        if (shown != null || queue.isEmpty()) return;
        Object[] next = queue.poll();
        if (next.length == 1 && next[0] instanceof F1Results.Race) {
            showRace((F1Results.Race) next[0]);
            return;
        }
        if (next.length == 2 && next[0] instanceof DeliveryCard.Card) {
            showCard((DeliveryCard.Card) next[0], (String) next[1]);
            return;
        }
        List<Object> parts;
        boolean wide;
        String sound;
        ScoresTicker.Side[] sides = null;
        Map<String, android.graphics.Bitmap> pictures = null;
        List<Object> livePage = null;
        Object[] watch = null;
        if (next.length == 2 || next.length == 3 || next.length == 5) {
            parts = (List<Object>) next[0];
            sound = (String) next[1];
            wide = true;
            if (next.length == 3) watch = (Object[]) next[2];
            if (next.length == 5) {
                sides = (ScoresTicker.Side[]) next[2];
                pictures = (Map<String, android.graphics.Bitmap>) next[3];
                livePage = (List<Object>) next[4];
                if (stillOnly() && (sides != null || livePage != null)) {
                    // Over a playing video: one still pill, the kick-off and the channel together; the line-ups
                    // wait for a pause or Home, under the two clubs and "LINE-UPS".
                    if (sides != null) {
                        List<Object> head = new ArrayList<>(livePage != null && livePage.size() > 9 ? livePage.subList(0, 9) : parts);
                        head.add(14);
                        head.add(bold("📋  LINE-UPS"));
                        held = new Object[]{head, sides, pictures, System.currentTimeMillis()};
                    }
                    if (livePage != null && livePage.size() >= 3) {
                        parts = new ArrayList<>(parts);
                        parts.add(14);
                        parts.addAll(livePage.subList(livePage.size() - 3, livePage.size()));
                    }
                    sides = null;
                    livePage = null;
                }
            }
        } else {
            sound = (String) next[5];
            ScoresTicker.Game g = (ScoresTicker.Game) next[1];
            ScoresTicker.Game now = g.id == null ? null : latest.get(g.id);
            if (now != null) g = now;
            // Crests stand in for the team names: crest, score, crest.
            Object score;
            String side = next.length > 6 ? (String) next[6] : null;
            int hs = parse(g.homeScore), as = parse(g.awayScore);
            if (side != null && hs >= 0 && as >= 0) {
                score = new Score(hs, as, side);
            } else {
                SpannableStringBuilder plain = new SpannableStringBuilder(g.homeScore + "–" + g.awayScore);
                plain.setSpan(new StyleSpan(Typeface.BOLD), 0, plain.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                score = plain;
            }
            // The competition's logo closes the line; its short name when ESPN has no logo.
            Object league = next.length > 4 && next[4] != null ? next[4] : new Small(g.league);
            parts = Arrays.asList(next[0], 10, next[2], 6, score, 6, next[3], 8, league);
            wide = false;
            if (next.length > 7) watch = (Object[]) next[7];
        }
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = pillBox(parts, d);
        int h = Math.round(PILL_DP * d);

        // With line-ups to show, the window is sized for their page from the start: it never changes size.
        // Line-up pages are pills too: the same window as any wide line.
        ensureHost(sides != null || livePage != null || wide ? 940 : 520, 46);
        if (host == null) return;
        host.addView(box, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, h, Gravity.BOTTOM | Gravity.START));
        shown = box;
        play(sound);
        // A goal's number rolls up once the pill has opened and its words are in (at once when it does not animate).
        if (scoreRoll != null) {
            Runnable roll = scoreRoll;
            scoreRoll = null;
            main.postDelayed(roll, calm() ? 0 : 1_700);
        }
        // A small glass dot pops in at the corner, the pill then stretches out to the right with its round ends kept
        // (an animated rounded outline, clipped in hardware), and only then does the text fade in.
        final float radius = h / 2f;
        final float[] visible = {h};
        ((RevealBox) box).visible = visible;
        box.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                // Anchored at the left: the pill grows out to the right from the dot.
                int right = Math.min(v.getWidth(), Math.round(visible[0]));
                o.setRoundRect(0, 0, right, v.getHeight(), radius);
            }
        });
        box.setClipToOutline(true);
        if (calm()) {
            // Over a playing app: no animation at all (see calm()), the pill is simply there.
            visible[0] = Float.MAX_VALUE;
        } else {
        for (int c = 0; c < box.getChildCount(); c++) box.getChildAt(c).setAlpha(0f);
        box.setAlpha(0f);
        box.setScaleX(0.3f);
        box.setScaleY(0.3f);
        box.post(() -> {
            box.setPivotX(radius);
            box.setPivotY(box.getHeight() / 2f);
            box.setLayerType(animLayer(), null);
            box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).withEndAction(() -> {
                ((RevealBox) box).setFrozen(true);
                android.animation.ValueAnimator grow = android.animation.ValueAnimator.ofFloat(h, box.getWidth());
                grow.setDuration(850);
                grow.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
                grow.addUpdateListener(a -> { visible[0] = (float) a.getAnimatedValue(); box.invalidateOutline(); box.invalidate(); });
                grow.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator a) {
                        ((RevealBox) box).setFrozen(false);
                        for (int c = 0; c < box.getChildCount(); c++) {
                            box.getChildAt(c).animate().alpha(1f).setDuration(300).setStartDelay(c * 40L).start();
                        }
                        box.postDelayed(() -> box.setLayerType(View.LAYER_TYPE_NONE, null), 600);
                    }
                });
                grow.start();
            }).start();
        });
        }
        if (sides != null || livePage != null) {
            // Then the line-ups, a page at a time flipping up in the same pill (each side's goalkeeper, defence,
            // midfield and attack), and last which channel has it.
            List<List<Object>> pages = sides != null ? lineupPages(sides, pictures) : new ArrayList<>();
            if (livePage != null) pages.add(livePage);
            if (sides != null) {
                List<Object> head = parts;
                if (livePage != null && livePage.size() > 9) {
                    head = new ArrayList<>(livePage.subList(0, 9));
                    head.add(14);
                    head.add(bold("📋  LINE-UPS"));
                }
                showingLineup = new Object[]{head, sides, pictures, System.currentTimeMillis()};
            }
            main.postDelayed(() -> flipPage(box, pages, 0, h), 6_500);
            return;
        }
        if (watch != null && !stillOnly()) {
            Object[] w = watch;
            main.postDelayed(() -> watchStep(box, h, w), SHOW_MS + 400);
            return;
        }
        main.postDelayed(() -> closePill(box, h, visible), SHOW_MS + 1_400);
    }

    // ---- "Watch on": the alert's second step, OK to watch it in TiviMate --------------------------------------

    private static final long WATCH_MS = 9_000L;
    private Runnable watchClose;

    /**
     * The alert flips to "WATCH ON" and the channel's logo, and for a few seconds OK on the remote opens TiviMate on
     * that channel (the first of w's names the playlist has), the steps hidden under the helper's cover. The window
     * takes the keys only while this step shows: OK watches, any other key closes it.
     */
    @SuppressWarnings("unchecked")
    private void watchStep(LinearLayout old, int h, Object[] w) {
        if (host == null || shown != old) return;
        List<String> names = (List<String>) w[0];
        android.graphics.Bitmap logo = (android.graphics.Bitmap) w[1];
        List<Object> parts = new ArrayList<>();
        parts.add(bold("▶  WATCH ON"));
        parts.add(10);
        parts.add(logo != null ? new ChipImg(logo) : bold(names.get(0)));
        parts.add(12);
        parts.add(new Small("OK to watch"));
        float d = getResources().getDisplayMetrics().density;
        LinearLayout page = pillBox(parts, d);
        host.addView(page, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, h, Gravity.BOTTOM | Gravity.START));
        shown = page;
        // A pop as it comes up, so the change catches the eye.
        if (calm()) {
            host.removeView(old);
        } else {
            page.setTranslationY(h);
            page.setAlpha(0f);
            old.setLayerType(animLayer(), null);
            old.animate().translationY(-h).alpha(0f).setDuration(380).withEndAction(() -> host.removeView(old)).start();
            page.animate().translationY(0f).alpha(1f).setDuration(380).withEndAction(() -> {
                page.setPivotX(h / 2f);
                page.setPivotY(h / 2f);
                page.animate().scaleX(1.06f).scaleY(1.06f).setDuration(140).withEndAction(() ->
                        page.animate().scaleX(1f).scaleY(1f).setDuration(220)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start()).start();
            }).start();
        }
        takeKeys(true);
        host.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return true;
            boolean ok = keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER
                    || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == android.view.KeyEvent.KEYCODE_BUTTON_A;
            endWatch(page, h);
            if (ok) AppSearchService.tune(this, getString(R.string.scores_open_package), names, logo);
            return true;
        });
        watchClose = () -> endWatch(page, h);
        main.postDelayed(watchClose, WATCH_MS);
    }

    /** The "Watch on" step over: the keys back with the app, and the pill closes. */
    private void endWatch(LinearLayout page, int h) {
        if (watchClose != null) main.removeCallbacks(watchClose);
        watchClose = null;
        if (host == null || shown != page) return;
        host.setOnKeyListener(null);
        takeKeys(false);
        final float[] full = {page.getWidth()};
        outline(page, h, full);
        closePill(page, h, full);
    }

    /** The alert's window takes the remote's keys (for "Watch on"), or leaves them with the app again. */
    private void takeKeys(boolean take) {
        if (host == null) return;
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) host.getLayoutParams();
        if (take) lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        else lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try {
            getSystemService(WindowManager.class).updateViewLayout(host, lp);
        } catch (Exception ignored) {
            return;
        }
        host.setFocusable(take);
        host.setFocusableInTouchMode(take);
        if (take) host.requestFocus();
    }

    private static final long PAGE_MS = 3_200L;

    /** Home is in front (MainActivity says so in onResume and onPause). */
    static volatile boolean homeInFront;

    /**
     * Over another app the alerts do not animate. Their window makes the TV compose the app's own layer and the alert
     * on the graphics chip instead of in hardware, for every frame that changes; on a slower set (the TCL) the steady
     * stream of animation frames over a 4K picture showed as stutter in the video underneath. A still alert costs that
     * once when it appears and once when it goes. Home and the screensaver keep the animations.
     */
    private boolean calm() {
        return !animateOverApps && !homeInFront && !dreaming;
    }

    /** animate_over_apps (theme): the full animations over other apps too, at the cost of the odd video frame. */
    private boolean animateOverApps;

    /**
     * lite_over_apps (theme): over another app the alerts still animate, but lightly: their window is always drawn by
     * the CPU (a plain buffer on a display plane of its own, so the video keeps its hardware composition), and each
     * alert is drawn once into a cached picture that only slides and fades, instead of the glass being redrawn for
     * every frame of a stretch and grow. For a TV (the TCL) whose video stuttered under the full animations.
     */
    private boolean liteOverApps;

    private boolean lite() {
        return liteOverApps && !homeInFront && !dreaming;
    }

    /** The layer an animating alert is drawn into: a cached picture (software in a CPU window, else the GPU's). */
    private int animLayer() {
        return lite() ? View.LAYER_TYPE_SOFTWARE : View.LAYER_TYPE_HARDWARE;
    }

    /** still_over_apps (theme): turn off pop-up page changes over a playing video. */
    private boolean stillOverApps;
    /** A kick-off reminder's line-ups put off while a video played: {header, sides, pictures, when}. */
    private Object[] held, showingLineup;
    private static final long HELD_MS = 3 * 60 * 60_000L;

    /**
     * Over a playing video on the TCL every change in an alert's window makes the TV compose the whole screen on the
     * graphics chip, and the video loses frames each time; one still pill costs nothing measurable. With
     * still_over_apps an alert there is a single still pill, and paged line-ups wait.
     */
    private boolean stillOnly() {
        if (!stillOverApps || !calm()) return false;
        android.media.AudioManager am = getSystemService(android.media.AudioManager.class);
        return am != null && am.isMusicActive();
    }

    /** The held line-ups, once nothing is playing or Home or the screensaver is up; a stale one is dropped. */
    private void releaseHeld() {
        if (held == null || stillOnly()) return;
        Object[] h = held;
        held = null;
        if (System.currentTimeMillis() - (Long) h[3] > HELD_MS) return;
        queue.add(new Object[]{h[0], "reminder", h[1], h[2], null});
        showNext();
    }

    /** The alert's window gone, and the next one in the queue. */
    private void removeHost() {
        if (host != null) {
            host.removeAllViews();
            try { getSystemService(WindowManager.class).removeView(host); } catch (Exception ignored) { }
        }
        host = null;
        shown = null;
        showNext();
    }

    /** The next line-up page slides up into the pill's place; after the last, the pill closes as usual. */
    private void flipPage(LinearLayout old, List<List<Object>> pages, int index, int h) {
        if (host == null) return;
        if (stillOnly() && showingLineup != null) {
            // Play was pressed while the pages turned: they stop, and the line-ups wait again.
            held = showingLineup;
            showingLineup = null;
            removeHost();
            return;
        }
        if (index >= pages.size()) {
            showingLineup = null;
            final float[] full = {old.getWidth()};
            outline(old, h, full);
            closePill(old, h, full);
            return;
        }
        float d = getResources().getDisplayMetrics().density;
        LinearLayout page = pillBox(pages.get(index), d);
        host.addView(page, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, h, Gravity.BOTTOM | Gravity.START));
        shown = page;
        if (calm()) {
            host.removeView(old);
            main.postDelayed(() -> flipPage(page, pages, index + 1, h), PAGE_MS);
            return;
        }
        page.setTranslationY(h);
        page.setAlpha(0f);
        old.setLayerType(animLayer(), null);
        old.animate().translationY(-h).alpha(0f).setDuration(380).withEndAction(() -> host.removeView(old)).start();
        page.animate().translationY(0f).alpha(1f).setDuration(380).start();
        main.postDelayed(() -> flipPage(page, pages, index + 1, h), PAGE_MS);
    }

    /** The pages: for each side, its crest and name, the line ("DEFENCE"), then each player's picture and name. */
    private List<List<Object>> lineupPages(ScoresTicker.Side[] sides, Map<String, android.graphics.Bitmap> pics) {
        List<List<Object>> pages = new ArrayList<>();
        String[] names = {"GOALKEEPER", "DEFENCE", "MIDFIELD", "ATTACK"};
        for (ScoresTicker.Side side : sides) {
            List<List<String[]>> groups = new ArrayList<>();
            for (int i = 0; i < 4; i++) groups.add(new ArrayList<>());
            for (String[] p : side.players) groups.get(line(p.length > 3 ? p[3] : "")).add(p);
            for (int i = 0; i < 4; i++) {
                if (groups.get(i).isEmpty()) continue;
                List<Object> parts = new ArrayList<>();
                parts.add(new Img(pics.get(side.crest), false));
                parts.add(6);
                parts.add(bold(side.abbr));
                parts.add(8);
                parts.add(new Small(names[i] + (i == 0 && side.formation != null && !side.formation.isEmpty() ? "  ·  " + side.formation : "")));
                for (String[] p : groups.get(i)) {
                    parts.add(12);
                    parts.add(new Face(pics.get(p[1]), p[2]));
                    parts.add(5);
                    parts.add(p[0]);
                }
                pages.add(parts);
            }
        }
        return pages;
    }

    /** ESPN's position ("G", "CD-L", "RB", "DM", "AM", "CF", "LW") to 0 keeper, 1 defence, 2 midfield, 3 attack. */
    private static int line(String pos) {
        String p = pos.toUpperCase(java.util.Locale.ROOT);
        if (p.startsWith("G")) return 0;
        if (p.contains("M")) return 2;
        if (p.contains("D") || p.endsWith("B") || p.contains("WB")) return 1;
        return 3;
    }

    /** A player's round photo, or his shirt number on the same disc when there is none. */
    private View face(Face f, int size, float d) {
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(0xFFC9D0D9);
        if (f.photo != null) {
            android.widget.ImageView v = crest(round(f.photo), d);
            v.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            v.setBackground(disc);
            v.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View view, android.graphics.Outline o) { o.setOval(0, 0, view.getWidth(), view.getHeight()); }
            });
            v.setClipToOutline(true);
            return v;
        }
        TextView n = text(bold(f.number), 9.5f, 0xFF1A1D24);
        n.setGravity(Gravity.CENTER);
        n.setBackground(disc);
        return n;
    }

    /** The photo cut to a disc over the face's grey, in the bitmap itself: a window drawn in software does not clip. */
    private static android.graphics.Bitmap round(android.graphics.Bitmap src) {
        int size = Math.min(src.getWidth(), src.getHeight());
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFC9D0D9);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setShader(new android.graphics.BitmapShader(src, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP));
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setTranslate(-(src.getWidth() - size) / 2f, -(src.getHeight() - size) / 2f);
        p.getShader().setLocalMatrix(m);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        return out;
    }

    private void outline(LinearLayout box, int h, float[] visible) {
        final float radius = h / 2f;
        if (box instanceof RevealBox) ((RevealBox) box).visible = visible;
        box.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, Math.min(v.getWidth(), Math.round(visible[0])), v.getHeight(), radius);
            }
        });
        box.setClipToOutline(true);
    }

    /**
     * Closing is the opening backwards: the pill draws back in to the dot with the text going as it passes (the
     * shrinking outline clips it from the right while it fades), then the dot pops away.
     */
    private void closePill(LinearLayout box, int h, float[] visible) {
        if (calm()) {
            removeHost();
            return;
        }
        {
            box.setLayerType(animLayer(), null);
            for (int c = 0; c < box.getChildCount(); c++) {
                box.getChildAt(c).animate().alpha(0f).setStartDelay(0).setDuration(lite() ? 300 : 600).start();
            }
            // Drawn by the CPU, the words fade first and the pill then draws back as one picture, not redrawn each frame.
            box.postDelayed(() -> {
                if (lite() && box instanceof RevealBox) ((RevealBox) box).setFrozen(true);
                android.animation.ValueAnimator shrink = android.animation.ValueAnimator.ofFloat(visible[0], h);
                shrink.setDuration(700);
                shrink.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                shrink.addUpdateListener(a -> { visible[0] = (float) a.getAnimatedValue(); box.invalidateOutline(); box.invalidate(); });
                shrink.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator a) {
                        box.animate().alpha(0f).scaleX(0.3f).scaleY(0.3f).setDuration(260)
                                .setInterpolator(new android.view.animation.AnticipateInterpolator(1.5f)).withEndAction(() -> {
                            host.removeView(box);
                            // Remove the window itself too: even an empty one over the video can show as a faint
                            // box where the TV processes the picture differently under it.
                            try { getSystemService(WindowManager.class).removeView(host); } catch (Exception ignored) { }
                            host = null;
                            shown = null;
                            showNext();
                        }).start();
                    }
                });
                shrink.start();
            }, lite() ? 320 : 0);
        }
    }

    /** One slim line in the Home screen's frosted pill style, dark text on the silver glass, from its parts. */
    private LinearLayout pillBox(List<Object> parts, float d) {
        LinearLayout box = new RevealBox(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int h = Math.round(PILL_DP * d);
        box.setBackground(glass(h / 2f));
        int pad = Math.round(14 * d);
        box.setPadding(pad, 0, pad, 0);
        int crestPx = Math.round(CREST_DP * d);
        int gap = 0;
        for (Object part : parts) {
            if (part instanceof Integer) { gap = Math.round((Integer) part * d); continue; }
            if (part == null || part instanceof android.graphics.Bitmap) {
                box.addView(crest((android.graphics.Bitmap) part, d), sized(crestPx, gap));
            } else if (part instanceof Img) {
                Img img = (Img) part;
                LinearLayout.LayoutParams lp = sized(crestPx, gap);
                if (img.wide) lp.width = crestPx * 2;
                box.addView(crest(img.bitmap, d), lp);
            } else if (part instanceof android.graphics.drawable.Drawable) {
                android.graphics.drawable.Drawable mark = (android.graphics.drawable.Drawable) part;
                android.widget.ImageView v = new android.widget.ImageView(this);
                v.setImageDrawable(mark);
                // Drawn marks and vector icons scale cleanly: fit them to the crests' height.
                int mh = Math.max(1, mark.getIntrinsicHeight());
                int th = Math.round(crestPx * 0.95f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.max(1, mark.getIntrinsicWidth() * th / mh), th);
                lp.leftMargin = gap;
                box.addView(v, lp);
            } else if (part instanceof Face) {
                box.addView(face((Face) part, crestPx, d), sized(crestPx, gap));
            } else if (part instanceof ChipImg) {
                // A channel's logo straight on the glass, its lettering already in the pill's ink (inked).
                android.graphics.Bitmap logo = ((ChipImg) part).bitmap;
                android.widget.ImageView v = new android.widget.ImageView(this);
                v.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                v.setImageBitmap(logo);
                int ch = crestPx;
                int cw = Math.min(Math.round(180 * d), Math.round(ch * logo.getWidth() / (float) Math.max(1, logo.getHeight())));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cw, ch);
                lp.leftMargin = gap;
                box.addView(v, lp);
            } else if (part instanceof Score) {
                box.addView(scoreView((Score) part, d), gap(gap));
            } else if (part instanceof Small) {
                box.addView(text(((Small) part).text, SMALL_SP, 0xB31A1D24), gap(gap));
            } else {
                TextView t = text((CharSequence) part, TEXT_SP, 0xFF1A1D24);
                t.setMaxWidth(Math.round(700 * d));   // a long headline ends in an ellipsis
                t.setEllipsize(TextUtils.TruncateAt.END);
                box.addView(t, gap(gap));
            }
            gap = 0;
        }
        box.setMinimumHeight(h);
        return box;
    }

    private static int parse(String n) {
        try { return Integer.parseInt(n.trim()); } catch (Exception e) { return -1; }
    }

    /** The roll of the score pillBox last built, run by showNext once the pill is up. */
    private Runnable scoreRoll;

    /**
     * "1–0" as three views; the scoring side's number sits in a window that starts on the score before the goal and
     * rolls the old number up and out and the new one up into place, which then flashes green and settles.
     */
    private View scoreView(Score sc, float d) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        boolean home = "home".equals(sc.side);
        View[] rollers = new View[2];
        for (int i = 0; i < 2; i++) {
            int now = i == 0 ? sc.home : sc.away;
            boolean scorer = (i == 0) == home;
            if (i == 1) row.addView(text(bold("–"), TEXT_SP, 0xFF1A1D24));
            if (!scorer) { row.addView(text(bold(String.valueOf(now)), TEXT_SP, 0xFF1A1D24)); continue; }
            android.widget.FrameLayout window = new android.widget.FrameLayout(this);
            window.setClipChildren(true);
            TextView was = text(bold(String.valueOf(Math.max(0, now - 1))), TEXT_SP, 0xFF1A1D24);
            TextView is = text(bold(String.valueOf(now)), TEXT_SP, 0xFF16A34A);
            was.setGravity(Gravity.CENTER);
            is.setGravity(Gravity.CENTER);
            window.addView(was, new android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            window.addView(is, new android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            is.setAlpha(0f);
            row.addView(window, new LinearLayout.LayoutParams(-2, Math.round(22 * d)));
            rollers[0] = was;
            rollers[1] = is;
        }
        if (rollers[0] != null) {
            TextView was = (TextView) rollers[0], is = (TextView) rollers[1];
            scoreRoll = () -> {
                float h = 22 * d;
                if (calm()) {
                    was.setAlpha(0f);
                    is.setAlpha(1f);
                    is.setTextColor(0xFF1A1D24);
                    return;
                }
                is.setTranslationY(h);
                is.setAlpha(1f);
                was.animate().translationY(-h).alpha(0f).setDuration(420)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f)).start();
                is.animate().translationY(0f).setDuration(520).setStartDelay(80)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).withEndAction(() -> {
                    is.setPivotY(is.getHeight() / 2f);
                    is.animate().scaleX(1.3f).scaleY(1.3f).setDuration(160).withEndAction(() ->
                            is.animate().scaleX(1f).scaleY(1f).setDuration(220).withEndAction(() -> {
                                // Back to the pill's ink after a moment in green.
                                main.postDelayed(() -> is.setTextColor(0xFF1A1D24), 900);
                            }).start()).start();
                }).start();
            };
        }
        return row;
    }

    /**
     * The race result: the Grand Prix's name, then the top ten in two rows of five, podium numbers in gold, silver
     * and bronze, on the same glass as the football alerts. It stays a little longer than they do.
     */
    private void showRace(F1Results.Race race) {
        if (shown != null || race.top.isEmpty()) { showNext(); return; }
        float d = getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(glass(16 * d));
        int pad = Math.round(14 * d);
        panel.setPadding(pad, Math.round(9 * d), pad, Math.round(10 * d));
        SpannableStringBuilder head = new SpannableStringBuilder("🏁  " + race.name.toUpperCase() + "  ·  RESULT");
        head.setSpan(new StyleSpan(Typeface.BOLD), 0, head.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // The F1 logo leads the heading, as on the other F1 alerts.
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.ImageView f1Logo = crest(null, d);
        f1Logo.setTag(F1Results.F1_LOGO);
        LinearLayout.LayoutParams llp = sized(Math.round(CREST_DP * d), 0);
        llp.width = Math.round(CREST_DP * 2 * d);
        heading.addView(f1Logo, llp);
        heading.addView(text(head, TEXT_SP, 0xFF1A1D24), gap(Math.round(8 * d)));
        panel.addView(heading);
        int[] medal = {0xFFB8860B, 0xFF6B7280, 0xFF9A5B2E};
        List<android.widget.ImageView> logoViews = new ArrayList<>();
        logoViews.add(f1Logo);
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            for (int i = row * 5; i < Math.min(race.top.size(), row * 5 + 5); i++) {
                // Position, the team's logo, the surname.
                SpannableStringBuilder pos = new SpannableStringBuilder(String.valueOf(i + 1));
                pos.setSpan(new StyleSpan(Typeface.BOLD), 0, pos.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                line.addView(text(pos, TEXT_SP, i < 3 ? medal[i] : 0xA61A1D24), gap(i == row * 5 ? 0 : Math.round(16 * d)));
                android.widget.ImageView logo = crest(null, d);
                logo.setTag(i < race.logos.size() ? race.logos.get(i) : null);
                line.addView(logo, sized(Math.round(CREST_DP * d), Math.round(5 * d)));
                logoViews.add(logo);
                line.addView(text(race.top.get(i), TEXT_SP, 0xFF1A1D24), gap(Math.round(5 * d)));
            }
            if (line.getChildCount() == 0) continue;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Math.round(5 * d);
            panel.addView(line, lp);
        }
        int px = Math.round(LOGO_DP * d);
        for (android.widget.ImageView v : logoViews) {
            String url = (String) v.getTag();
            if (url == null) continue;
            int size = F1Results.F1_LOGO.equals(url) ? px * 2 : px;
            io.execute(() -> {
                android.graphics.Bitmap b = ScoresTicker.cachedLogo(this, url, size);
                if (b != null) main.post(() -> v.setImageBitmap(b));
            });
        }
        ensureHost(840, 116);
        if (host == null) return;
        host.addView(panel, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START));
        shown = panel;
        play("f1");
        if (calm()) {
            main.postDelayed(this::removeHost, 15_000);
            return;
        }
        panel.setAlpha(0f);
        panel.setTranslationY(14 * d);
        panel.setLayerType(animLayer(), null);
        panel.animate().alpha(1f).translationY(0f).setDuration(600)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                .withEndAction(() -> panel.setLayerType(View.LAYER_TYPE_NONE, null)).start();
        main.postDelayed(() -> {
            panel.setLayerType(animLayer(), null);
            panel.animate().alpha(0f).setDuration(450).withEndAction(() -> {
                host.removeView(panel);
                try { getSystemService(WindowManager.class).removeView(host); } catch (Exception ignored) { }
                host = null;
                shown = null;
                showNext();
            }).start();
        }, 15_000);
    }

    private TextView text(CharSequence s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setSingleLine(true);
        return t;
    }

    private static LinearLayout.LayoutParams sized(int size, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.leftMargin = left;
        return lp;
    }

    private static LinearLayout.LayoutParams gap(int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = left;
        return lp;
    }

    private android.widget.ImageView crest(android.graphics.Bitmap b, float d) {
        android.widget.ImageView v = new android.widget.ImageView(this);
        v.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        if (b != null) v.setImageBitmap(b);
        v.setLayoutParams(new LinearLayout.LayoutParams(Math.round(18 * d), Math.round(18 * d)));
        return v;
    }

    @Override public void onDestroy() {
        if (deliveries != null) deliveries.stop();
        try { unregisterReceiver(dreams); } catch (Exception ignored) { }
        android.media.AudioManager am = getSystemService(android.media.AudioManager.class);
        if (am != null) am.unregisterAudioPlaybackCallback(playback);
        if (sounds != null) sounds.release();
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        if (host != null) {
            try { getSystemService(WindowManager.class).removeView(host); } catch (Exception ignored) { }
            host = null;
            shown = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
