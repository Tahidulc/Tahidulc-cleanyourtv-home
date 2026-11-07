package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.Events;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.Notifier;

/**
 * The scoreboard over other apps (YouTube, another player, anything): the followed clubs' game on now, else an F1
 * session, with the goals, the other sport and breaking news dropping from it (ScoreBoard). Drag it where it suits;
 * tap it for the next game on; hold it to put it away. Runs while switched on, with Android's "Display over other
 * apps", as a quiet notification with Stop; keeps out of the way while Homefield's own player shows its scoreboard.
 * The other app's picture runs behind by what the setting says (0 until set), so its clock and score match that
 * screen too.
 */
public final class FloatService extends Service implements ScoreBoard.Source {
    private static final int ID = 4102;
    private static final String STOP = "stop";
    private final Handler h = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private FrameLayout win;
    private WindowManager.LayoutParams lp;
    private ScoreBoard board;
    private BurnGuard guard;

    /** Every second: out of the way while Homefield's own player has its scoreboard up. */
    private final Runnable keepClear = new Runnable() {
        @Override public void run() {
            h.removeCallbacks(this);
            if (win == null) return;
            win.setVisibility(ScoreOverlay.up ? View.GONE : View.VISIBLE);
            h.postDelayed(this, 1000);
        }
    };

    public static boolean on(Context c) {
        return ScoreBoard.floating(c);
    }

    /** Switched on (Android's permission asked for first when it isn't given) or off. */
    public static void toggle(Activity a) {
        if (on(a)) {
            ScoreBoard.setFloating(a, false);
            a.stopService(new Intent(a, FloatService.class));
            Toast.makeText(a, R.string.live4_float_stopped, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Settings.canDrawOverlays(a)) {
            Toast.makeText(a, R.string.live4_float_allow, Toast.LENGTH_LONG).show();
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + a.getPackageName())));
            } catch (RuntimeException ignored) {
                // no such screen on this phone
            }
            return;
        }
        ScoreBoard.setFloating(a, true);
        start(a);
        Toast.makeText(a, R.string.live4_float_started, Toast.LENGTH_LONG).show();
    }

    /** Started when switched on and allowed (the app opening, the switch); false when Android said no. */
    public static boolean start(Context c) {
        if (!on(c) || !Settings.canDrawOverlays(c)) return false;
        try {
            ContextCompat.startForegroundService(c, new Intent(c, FloatService.class));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            ScoreBoard.setFloating(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            PendingIntent stop = PendingIntent.getService(this, 4103, new Intent(this, FloatService.class).setAction(STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            android.app.Notification n = new NotificationCompat.Builder(this, Notifier.LIVE)
                    .setSmallIcon(R.drawable.sports_ic_ball)
                    .setContentTitle(getString(R.string.live4_float_title))
                    .setContentText(getString(R.string.live4_float_line))
                    .setOngoing(true)
                    .setSilent(true)
                    .setColor(getColor(R.color.sports))
                    .addAction(0, getString(R.string.live4_float_stop_short), stop)
                    .build();
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(ID, n);
        } catch (RuntimeException e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (win == null) show();
        return START_STICKY;
    }

    private SharedPreferences place() {
        return getSharedPreferences("score_overlay", MODE_PRIVATE);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void show() {
        wm = getSystemService(WindowManager.class);
        float d = getResources().getDisplayMetrics().density;
        board = new ScoreBoard(this, this);
        win = new FrameLayout(this);
        // Room round it for the burn-in guard's moves.
        int pad = Math.round(3 * d);
        win.setPadding(pad, pad, pad, pad);
        win.addView(board.view, new FrameLayout.LayoutParams(-2, -2));
        guard = new BurnGuard(this, 2f, new View[]{board.view}, new BurnGuard.Dims[]{board.bug});
        board.changed = guard::poke;
        lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = place().getInt("x", Math.round(16 * d));
        lp.y = place().getInt("y", Math.round(40 * d));
        lp.setTitle("Scoreboard");
        // Dragged by the scoreboard itself; a tap and a hold still reach it when it isn't moved.
        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        float[] start = new float[4];
        boolean[] moving = {false};
        board.bug.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = e.getRawX();
                    start[1] = e.getRawY();
                    start[2] = lp.x;
                    start[3] = lp.y;
                    moving[0] = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - start[0], dy = e.getRawY() - start[1];
                    if (!moving[0] && Math.hypot(dx, dy) > slop) {
                        moving[0] = true;
                        v.cancelLongPress();
                        v.setPressed(false);
                    }
                    if (moving[0]) {
                        lp.x = Math.round(start[2] + dx);
                        lp.y = Math.round(start[3] + dy);
                        try {
                            wm.updateViewLayout(win, lp);
                        } catch (RuntimeException ignored) {
                            // gone meanwhile
                        }
                    }
                    return moving[0];
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (moving[0]) {
                        place().edit().putInt("x", lp.x).putInt("y", lp.y).apply();
                        moving[0] = false;
                        roomNow();
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        });
        try {
            wm.addView(win, lp);
        } catch (RuntimeException e) {
            win = null;
            stopSelf();
            return;
        }
        board.start();
        guard.start();
        h.post(keepClear);
        roomNow();
        board.bug.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) h.post(this::roomNow);
        });
    }

    /**
     * The room on the board's right for what slides out of it, over the other apps: always out to its right (the window
     * grows that way and stays put), nothing above it, as far as the screen's edge.
     */
    private void roomNow() {
        if (board == null || lp == null) return;
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int bw = board.bug.getWidth() > 0 ? board.bug.getWidth() : board.bug.natural(Math.round(30 * dm.density));
        int pad = win == null ? 0 : win.getPaddingLeft() + win.getPaddingRight();
        board.room(0, Math.max(0, dm.widthPixels - lp.x - bw - pad), 0, Math.max(0, dm.heightPixels - lp.y));
    }

    @Override public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (guard != null) guard.stop();
        if (board != null) board.stop();
        if (win != null) {
            try {
                wm.removeView(win);
            } catch (RuntimeException ignored) {
                // already gone
            }
            win = null;
        }
        super.onDestroy();
    }

    // ---- ScoreBoard.Source ---------------------------------------------------------------------------------------

    @Override public Channel channel() {
        return null;
    }

    @Override public Game game() {
        return null;
    }

    @Override public long behindMs(boolean channelGame) {
        return ScoreBoard.otherDelay(this) * 1000L;
    }

    @Override public void tapped() {
        if (board != null) board.cycle();
    }

    @Override public void held() {
        ScoreBoard.setFloating(this, false);
        Toast.makeText(this, R.string.live4_float_stopped, Toast.LENGTH_SHORT).show();
        stopSelf();
    }

    @Override public void alertTapped(Events.Alert a) {
        if (a == null || a.game == null) return;
        Context app = getApplicationContext();
        App.io.execute(() -> {
            Channel ch = AlsoShowing.best(app, a.game);
            if (ch != null) App.main.post(() -> {
                try {
                    startActivity(PlayerActivity.intent(app, ch.key));
                } catch (RuntimeException ignored) {
                    // not let in from here
                }
            });
        });
    }
}
