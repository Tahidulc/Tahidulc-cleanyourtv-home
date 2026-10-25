package com.gathin.tvhome;

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
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Goal alerts over another app (config.xml goal_watch_apps, TiviMate): started when Home opens one of those apps,
 * stopped when Home comes back. Reads the scores every POLL_MS and shows each new goal DELAY_MS after it was seen,
 * as a small glass box in the top-right corner for SHOW_MS. The box never takes focus or keys. Needs "display over
 * other apps" (appops SYSTEM_ALERT_WINDOW); without it the service stops at once.
 */
public class GoalWatchService extends Service {
    private static final long POLL_MS = 20_000L;
    private static final long DELAY_MS = 60_000L;
    private static final long SHOW_MS = 8_000L;
    private static final long MAX_RUN_MS = 4 * 60 * 60_000L;
    private static final String CHANNEL = "goal_watch";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Set<String> seen = new HashSet<>();
    private boolean primed;
    private long startedAt;
    private View shown;
    /** The overlay window, added once (empty, so 0 by 0) when the service starts; alerts only fill it. */
    private android.widget.FrameLayout host;
    /** Alerts waiting their turn: {first line, score line, home crest url, away crest url}. */
    private final java.util.ArrayDeque<Object[]> queue = new java.util.ArrayDeque<>();
    private final Runnable poll = this::poll;
    /** The newest read of each game, so an alert shown a minute later carries the score as it is by then. */
    private final java.util.Map<String, ScoresTicker.Game> latest = new java.util.HashMap<>();
    /** Each game's state at the last read (0 live, 1 upcoming, 2 finished), to see the moment one finishes. */
    private final java.util.Map<String, Integer> lastState = new java.util.HashMap<>();

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
        if (intent != null && intent.hasExtra("test_goal")) {
            // adb only (the service needs DUMP from outside): one sample alert now, to check the look over an app.
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.league = "PL"; g.home = "LIV"; g.away = "BHA"; g.homeScore = "2"; g.awayScore = "1";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/364.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/331.png";
            enqueue(g, new String[]{"test", "Mohamed Salah", "67'", "home", ""}, false);
        }
        if (intent != null && intent.hasExtra("test_ft")) {
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.league = "PL"; g.home = "MAN"; g.away = "BOU"; g.homeScore = "3"; g.awayScore = "1";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/360.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/349.png";
            enqueue(g, new String[]{"test-ft", "", "", "", "ft"}, false);
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
        return START_NOT_STICKY;
    }

    private void poll() {
        if (System.currentTimeMillis() - startedAt > MAX_RUN_MS) { stopSelf(); return; }
        // Only the competitions worth interrupting a programme for (goal_alert_leagues).
        String[] leagues = getResources().getStringArray(R.array.goal_alert_leagues);
        io.execute(() -> {
            List<ScoresTicker.Game> games = ScoresTicker.load(leagues);
            main.post(() -> {
                boolean live = false;
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

    /** Fetches both crests (cached on disk by the scores line), then queues the alert. */
    private void enqueue(ScoresTicker.Game g, String[] goal, boolean red) {
        int px = Math.round(30 * getResources().getDisplayMetrics().density);
        io.execute(() -> {
            android.graphics.Bitmap home = g.homeLogo == null ? null : ScoresTicker.cachedLogo(this, g.homeLogo, px);
            android.graphics.Bitmap away = g.awayLogo == null ? null : ScoresTicker.cachedLogo(this, g.awayLogo, px);
            main.post(() -> {
                queue.add(new Object[]{headline(goal, red), g, home, away});
                showNext();
            });
        });
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

    /** The alert's window: added for one alert at a fixed size (no resize while showing), removed after it. */
    private void ensureHost() {
        if (host != null) return;
        float d = getResources().getDisplayMetrics().density;
        host = new android.widget.FrameLayout(this);
        // A fixed size, set once: a window that changes size over a playing video is reallocated and re-planned
        // by the display, which shows as a hitch; this one only ever changes what it draws.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(Math.round(420 * d), Math.round(40 * d),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.x = Math.round(32 * d);
        lp.y = Math.round(30 * d);
        try {
            getSystemService(WindowManager.class).addView(host, lp);
        } catch (Exception e) {
            host = null;
        }
    }

    /** "Mohamed Salah" to "Salah", "Virgil van Dijk" to "van Dijk": everything after the first name. */
    static String surname(String name) {
        String n = name == null ? "" : name.trim();
        int space = n.indexOf(' ');
        return space > 0 ? n.substring(space + 1) : n;
    }

    private void showNext() {
        if (shown != null || queue.isEmpty()) return;
        Object[] next = queue.poll();
        ScoresTicker.Game g = (ScoresTicker.Game) next[1];
        ScoresTicker.Game now = g.id == null ? null : latest.get(g.id);
        if (now != null) g = now;
        float d = getResources().getDisplayMetrics().density;
        // One slim line in the Home screen's frosted pill style: a faint dark base so white text reads over a bright
        // pitch, the pills' white glass and rim on top, and the same soft sheen along the top edge.
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int h = Math.round(28 * d);
        GradientDrawable base = new GradientDrawable();
        // Silver: a light metallic glass, brighter at the top like brushed aluminium, with dark text on it.
        base.setColors(new int[]{0xF2E9ECF1, 0xF2B9BFC8});
        base.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        base.setCornerRadius(h / 2f);
        android.graphics.drawable.LayerDrawable glass = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{base, getDrawable(R.drawable.glass_box).mutate()});
        box.setBackground(glass);
        box.setClipToOutline(true);
        int pad = Math.round(12 * d);
        box.setPadding(pad, 0, pad, 0);
        int crestPx = Math.round(14 * d);
        box.addView(text((CharSequence) next[0], 10.5f, 0xFF1A1D24));
        // Crests stand in for the team names: crest, score, crest.
        box.addView(crest((android.graphics.Bitmap) next[2], d), sized(crestPx, Math.round(10 * d)));
        SpannableStringBuilder line = new SpannableStringBuilder(g.homeScore + "–" + g.awayScore);
        line.setSpan(new StyleSpan(Typeface.BOLD), 0, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        box.addView(text(line, 10.5f, 0xFF1A1D24), gap(Math.round(6 * d)));
        box.addView(crest((android.graphics.Bitmap) next[3], d), sized(crestPx, Math.round(6 * d)));
        box.addView(text(g.league, 9f, 0xA61A1D24), gap(Math.round(8 * d)));
        box.setMinimumHeight(h);

        ensureHost();
        if (host == null) return;
        host.addView(box, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, h, Gravity.BOTTOM | Gravity.START));
        shown = box;
        // A small glass dot pops in at the corner, the pill then stretches out to the right with its round ends kept
        // (an animated rounded outline, clipped in hardware), and only then does the text fade in.
        final float radius = h / 2f;
        final float[] visible = {h};
        box.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                // Anchored at the left: the pill grows out to the right from the dot.
                int right = Math.min(v.getWidth(), Math.round(visible[0]));
                o.setRoundRect(0, 0, right, v.getHeight(), radius);
            }
        });
        box.setClipToOutline(true);
        for (int c = 0; c < box.getChildCount(); c++) box.getChildAt(c).setAlpha(0f);
        box.setAlpha(0f);
        box.setScaleX(0.3f);
        box.setScaleY(0.3f);
        box.post(() -> {
            box.setPivotX(radius);
            box.setPivotY(box.getHeight() / 2f);
            box.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).withEndAction(() -> {
                android.animation.ValueAnimator grow = android.animation.ValueAnimator.ofFloat(h, box.getWidth());
                grow.setDuration(850);
                grow.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
                grow.addUpdateListener(a -> { visible[0] = (float) a.getAnimatedValue(); box.invalidateOutline(); });
                grow.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator a) {
                        for (int c = 0; c < box.getChildCount(); c++) {
                            box.getChildAt(c).animate().alpha(1f).setDuration(300).setStartDelay(c * 40L).start();
                        }
                        box.postDelayed(() -> box.setLayerType(View.LAYER_TYPE_NONE, null), 600);
                    }
                });
                grow.start();
            }).start();
        });
        main.postDelayed(() -> {
            box.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            box.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(450)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(() -> {
                host.removeView(box);
                // Remove the window itself too: even an empty one over the video can show as a faint box
                // where the TV processes the picture differently under it.
                try { getSystemService(WindowManager.class).removeView(host); } catch (Exception ignored) { }
                host = null;
                shown = null;
                showNext();
            }).start();
        }, SHOW_MS + 1_400);
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
