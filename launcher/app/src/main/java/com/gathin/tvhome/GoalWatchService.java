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
    private final java.util.ArrayDeque<CharSequence> queue = new java.util.ArrayDeque<>();
    private final Runnable poll = this::poll;

    /** Home opened this package: watch for goals while it is in front, if it is one of goal_watch_apps. */
    static void onAppOpened(Context ctx, String pkg) {
        if (pkg == null || !Arrays.asList(ctx.getResources().getStringArray(R.array.goal_watch_apps)).contains(pkg)) return;
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
            queue.add(line(g, new String[]{"test", "Mohamed Salah", "67'", "home", ""}));
            showNext();
        }
        if (startedAt == 0) {
            startedAt = System.currentTimeMillis();
            main.post(poll);
        }
        return START_NOT_STICKY;
    }

    private void poll() {
        if (System.currentTimeMillis() - startedAt > MAX_RUN_MS) { stopSelf(); return; }
        String[] leagues = getResources().getStringArray(R.array.score_leagues);
        io.execute(() -> {
            List<ScoresTicker.Game> games = ScoresTicker.load(leagues);
            main.post(() -> {
                boolean live = false;
                for (ScoresTicker.Game g : games) {
                    live |= g.state == 0;
                    for (String[] goal : g.goals) {
                        if (seen.add(goal[0]) && primed) {
                            CharSequence text = line(g, goal);
                            main.postDelayed(() -> { queue.add(text); showNext(); }, DELAY_MS);
                        }
                    }
                }
                if (!games.isEmpty()) primed = true;
                main.postDelayed(poll, live ? POLL_MS : 5 * POLL_MS);
            });
        });
    }

    private static CharSequence line(ScoresTicker.Game g, String[] goal) {
        SpannableStringBuilder s = new SpannableStringBuilder("⚽ GOAL   ");
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(0xFF4ADE80), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int at = s.length();
        s.append(goal[1]);
        s.setSpan(new StyleSpan(Typeface.BOLD), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.append("  ").append(goal[2]);
        if (!goal[4].isEmpty()) s.append(goal[4].equals("pen") ? " (pen)" : " (og)");
        at = s.length();
        s.append("\n").append(g.league).append("   ").append(g.home).append("  ").append(g.homeScore).append("–")
                .append(g.awayScore).append("  ").append(g.away);
        s.setSpan(new ForegroundColorSpan(0xCCFFFFFF), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private void showNext() {
        if (shown != null || queue.isEmpty()) return;
        float density = getResources().getDisplayMetrics().density;
        TextView t = new TextView(this);
        t.setText(queue.poll());
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        t.setLineSpacing(4 * density, 1f);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xEB143D2A, 0xEB0E2A1D});
        bg.setCornerRadius(16 * density);
        bg.setStroke(Math.round(1.5f * density), 0xFF4ADE80);
        t.setBackground(bg);
        int pad = Math.round(16 * density);
        t.setPadding(pad, Math.round(10 * density), pad, Math.round(10 * density));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = Math.round(32 * density);
        lp.y = Math.round(28 * density);
        WindowManager wm = getSystemService(WindowManager.class);
        try {
            wm.addView(t, lp);
        } catch (Exception e) {
            return;
        }
        shown = t;
        t.setAlpha(0f);
        t.setTranslationY(-20 * density);
        t.animate().alpha(1f).translationY(0f).setDuration(300).start();
        main.postDelayed(() -> t.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            try { wm.removeView(t); } catch (Exception ignored) { }
            shown = null;
            showNext();
        }).start(), SHOW_MS);
    }

    @Override public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        if (shown != null) {
            try { getSystemService(WindowManager.class).removeView(shown); } catch (Exception ignored) { }
            shown = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
