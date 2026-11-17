package com.gathin.homefieldtv;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import com.gathin.homefieldtv.engine.Reminders;

import java.util.ArrayDeque;

/**
 * A reminder's card, a minute before the programme: over whatever is on the TV, another app too, at the top right in
 * Homefield's dark look: the channel's logo on its tile, "Starts in 1 min" (then "Starting now"), the programme's title,
 * the channel and its time, and what the keys do. It waits a second and a half before it takes the remote (a key already
 * on its way doesn't answer it); then OK opens Homefield TV on the channel and Back puts it away; it goes by itself after
 * about 20 seconds. Shown in a window over other apps where Android lets Homefield TV (Display over other apps, asked
 * for once); otherwise as a notification that asks to come up full screen (ReminderActivity), Android permitting.
 */
final class ReminderCard {
    private ReminderCard() {}

    static final long SHOW_MS = 20_000L, HOLD_MS = 1_500L;
    /** The channel to open, given to TvActivity (and the moment the card was answered). */
    static final String EXTRA_KEY = "reminder_key", EXTRA_AT = "reminder_at";
    private static final String CHANNEL = "reminders", PREFS = "reminders_ui";
    /** Cards due while one is up, shown after it. */
    private static final ArrayDeque<Reminders.Reminder> waiting = new ArrayDeque<>();
    /** The card up now: made on the application's context, never an activity's, and let go when it closes. */
    @SuppressLint("StaticFieldLeak")
    private static Window up;

    /** Pure: the card's line about the start: "Starts in 1 min", "Starting now". */
    static String when(long now, long start) {
        long left = start - now;
        if (left <= 30_000L) return "Starting now";
        return "Starts in " + Math.max(1L, (left + 30_000L) / 60_000L) + " min";
    }

    /** Whether Android lets Homefield TV show the card over other apps. */
    static boolean overApps(Context c) {
        return Settings.canDrawOverlays(c);
    }

    /** The card for a reminder now: over other apps, else the notification. */
    static void show(Context c, Reminders.Reminder r) {
        Context app = c.getApplicationContext();
        if (overApps(app)) {
            if (up != null) {
                waiting.add(r);
                return;
            }
            up = new Window(app, r);
            if (!up.open()) {
                up = null;
                notify(app, r);
            }
        } else {
            notify(app, r);
        }
    }

    /** OK on a card: Homefield TV opened on the channel. */
    static void watch(Context c, String key) {
        Intent i = new Intent(c, TvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_KEY, key).putExtra(EXTRA_AT, System.currentTimeMillis());
        try {
            c.startActivity(i);
        } catch (RuntimeException ignored) {
            // Android wouldn't open it from here: the card goes, the reminder was given.
        }
    }

    // ---- asked for once ---------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Whether to ask about showing over other apps now (not allowed yet, and not asked before). */
    static boolean shouldAsk(Context c) {
        return !overApps(c) && !prefs(c).getBoolean("asked", false);
    }

    static void asked(Context c) {
        prefs(c).edit().putBoolean("asked", true).apply();
    }

    /** The question, the first time a reminder is set. */
    static final String ASK = "So a reminder can pop up while you're watching another app, Android needs to let Homefield TV "
            + "show over other apps. OK opens Android's page for it: turn Homefield TV on there, then press Back.";

    /**
     * Android's page for showing over other apps, for Homefield TV; false when this TV has none (then reminders come as
     * a notification, which Android 13 and later ask to allow).
     */
    static boolean openSettings(Activity a) {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + a.getPackageName()));
        Intent any = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        for (Intent t : new Intent[]{i, any}) {
            try {
                if (t.resolveActivity(a.getPackageManager()) == null) continue;
                a.startActivity(t);
                return true;
            } catch (RuntimeException ignored) {
                // the next way, if there is one
            }
        }
        askNotifications(a);
        return false;
    }

    /** Android 13 and later: notifications allowed, for the card when it can't go over other apps. */
    static void askNotifications(Activity a) {
        if (Build.VERSION.SDK_INT < 33) return;
        if (a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        try {
            a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 41);
        } catch (RuntimeException ignored) {
            // asked another time, from Settings
        }
    }

    // ---- the notification, where the card can't go over other apps ---------------------------------------------

    private static void notify(Context app, Reminders.Reminder r) {
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 33
                && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH));
        int id = r.id().hashCode();
        Intent go = new Intent(app, TvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_KEY, r.key).putExtra(EXTRA_AT, System.currentTimeMillis());
        PendingIntent watch = PendingIntent.getActivity(app, id, go, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent full = PendingIntent.getActivity(app, id + 1, ReminderActivity.intent(app, r),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        long now = System.currentTimeMillis();
        Notification n = new Notification.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_reminder)
                .setContentTitle(when(now, r.start) + ": " + r.title)
                .setContentText(r.channel + "  ·  " + LiveText.hm(r.start))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(watch)
                .setFullScreenIntent(full, true)
                .setAutoCancel(true)
                .setTimeoutAfter(Reminders.LATE_MS + Reminders.AHEAD_MS)
                .build();
        nm.notify(CHANNEL, id, n);
    }

    // ---- the card over other apps ----------------------------------------------------------------------------

    /** The card in its own window over whatever is on the TV. */
    private static final class Window implements Face.Answer {
        private final Context app;
        private final Reminders.Reminder r;
        private final WindowManager wm;
        private final Face face;
        private final WindowManager.LayoutParams lp;
        private final Handler h = new Handler(Looper.getMainLooper());
        private boolean gone;

        Window(Context app, Reminders.Reminder r) {
            this.app = app;
            this.r = r;
            Looks.ready(app);
            wm = app.getSystemService(WindowManager.class);
            face = new Face(app, r, this);
            lp = new WindowManager.LayoutParams(Ui.px(Face.W), Ui.px(Face.H), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = Ui.px(56);
            lp.y = Ui.px(52);
            lp.setTitle("Homefield TV reminder");
        }

        boolean open() {
            if (wm == null) return false;
            try {
                wm.addView(face, lp);
            } catch (RuntimeException e) {
                return false;
            }
            face.setAlpha(0f);
            face.setTranslationX(Ui.f(60));
            face.animate().alpha(1f).translationX(0f).setDuration(260).setInterpolator(Fade.EASE).start();
            // A second and a half, then the remote is the card's.
            h.postDelayed(() -> {
                if (gone) return;
                lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                try {
                    wm.updateViewLayout(face, lp);
                    face.requestFocus();
                } catch (RuntimeException ignored) {
                    // the card stays, the keys stay with the app under it
                }
            }, HOLD_MS);
            // "Starting now" once it starts; gone after a while.
            long left = r.start - System.currentTimeMillis();
            if (left > 0) h.postDelayed(face::invalidate, left + 500);
            h.postDelayed(this::close, SHOW_MS);
            return true;
        }

        @Override public void ok() {
            close();
            watch(app, r.key);
        }

        @Override public void close() {
            if (gone) return;
            gone = true;
            h.removeCallbacksAndMessages(null);
            face.animate().alpha(0f).translationX(Ui.f(40)).setDuration(200).withEndAction(() -> {
                try {
                    wm.removeView(face);
                } catch (RuntimeException ignored) {
                    // already gone
                }
                up = null;
                Reminders.Reminder next = waiting.poll();
                if (next != null) show(app, next);
            }).start();
        }
    }

    /** The colours, the lettering and the pictures ready when the app wasn't open (the card came from the alarm). */
    static final class Looks {
        private Looks() {}

        static void ready(Context app) {
            Ui.init(app);
            Dark.init(app);
            Theme.apply(app);
            Pics.init(app);
        }
    }

    /** The card itself: drawn here, used by the window and by ReminderActivity. */
    static final class Face extends View {
        interface Answer {
            void ok();

            void close();
        }

        static final float W = 700f, H = 212f;
        private final Reminders.Reminder r;
        private final Answer answer;
        private final Paint card = Dark.fill(Dark.RAIL), edge = Dark.line(0x33FFFFFF, 1.5f), tile = Dark.fill(0xFF000000);
        private final Paint pill = Dark.fill(Dark.ACCENT), ink = Dark.fill(Dark.ACCENT_INK), line = Dark.fill(Dark.LINE);
        private final Paint chip = Dark.line(0x8CFFFFFF, 1.5f), bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final TextPaint pillP = Dark.text(22, 700, Dark.ACCENT_INK), titleP = Dark.text(32, 700, Dark.TEXT);
        private final TextPaint subP = Dark.text(24, 500, Dark.SOFT), nameP = Dark.text(26, 700, Dark.TEXT);
        private final TextPaint chipP = Dark.text(19, 700, Dark.SOFT), hintP = Dark.text(21, 500, Dark.MUTED);
        private final RectF rect = new RectF();
        private final Runnable redraw = this::invalidate;

        Face(Context c, Reminders.Reminder r, Answer answer) {
            super(c);
            this.r = r;
            this.answer = answer;
            setFocusable(true);
            setFocusableInTouchMode(true);
        }

        @Override public boolean dispatchKeyEvent(KeyEvent e) {
            int code = e.getKeyCode();
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_BUTTON_A:
                    if (e.getAction() == KeyEvent.ACTION_UP) answer.ok();
                    return true;
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                case KeyEvent.KEYCODE_BUTTON_B:
                    if (e.getAction() == KeyEvent.ACTION_UP) answer.close();
                    return true;
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                case KeyEvent.KEYCODE_VOLUME_MUTE:
                    return false;
                default:
                    // Any other key: the card makes way.
                    if (e.getAction() == KeyEvent.ACTION_DOWN) answer.close();
                    return true;
            }
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), pad = Ui.f(24);
            rect.set(0, 0, w, h);
            c.drawRoundRect(rect, Ui.f(24), Ui.f(24), card);
            rect.inset(Ui.f(0.75f), Ui.f(0.75f));
            c.drawRoundRect(rect, Ui.f(24), Ui.f(24), edge);

            // The channel's logo on its tile (its name when there is no logo).
            float lx = pad, ly = Ui.f(26), lw = Ui.f(192), lh = Ui.f(108);
            Bitmap logo = Pics.get(r.logo, Ui.px(192), Ui.px(108), Pics.tiles ? Pics.TILE : Pics.FIT, redraw);
            if (logo != null && Pics.tiles) {
                c.drawBitmap(logo, lx, ly, bmp);
            } else {
                rect.set(lx, ly, lx + lw, ly + lh);
                c.drawRoundRect(rect, Ui.f(14), Ui.f(14), tile);
                if (logo != null) {
                    c.drawBitmap(logo, lx + (lw - logo.getWidth()) / 2f, ly + (lh - logo.getHeight()) / 2f, bmp);
                } else {
                    String n = Ui.fit(r.channel, nameP, lw - Ui.f(20));
                    c.drawText(n, lx + (lw - nameP.measureText(n)) / 2f, ly + lh / 2f + Ui.f(9), nameP);
                }
            }

            // "Starts in 1 min", the bell in front, on the accent.
            float x0 = Ui.f(240), room = w - x0 - pad;
            String when = when(System.currentTimeMillis(), r.start);
            float pw = Ui.f(52) + pillP.measureText(when) + Ui.f(18), ph = Ui.f(40), py = Ui.f(24);
            rect.set(x0, py, x0 + pw, py + ph);
            c.drawRoundRect(rect, ph / 2f, ph / 2f, pill);
            Dark.bell(c, x0 + Ui.f(27), py + ph / 2f, Ui.f(11), ink);
            c.drawText(when, x0 + Ui.f(48), py + ph / 2f + Ui.f(8), pillP);

            c.drawText(Ui.fit(r.title, titleP, room), x0, Ui.f(106), titleP);
            c.drawText(Ui.fit(r.channel + "  ·  " + LiveText.hm(r.start), subP, room), x0, Ui.f(142), subP);

            // What the keys do.
            c.drawRect(pad, Ui.f(160), w - pad, Ui.f(161.5f), line);
            float kx = keyHint(c, "OK", "Watch", pad, Ui.f(192));
            keyHint(c, "Back", "Dismiss", kx + Ui.f(28), Ui.f(192));
        }

        private float keyHint(Canvas c, String key, String what, float x, float base) {
            float cw = chipP.measureText(key) + Ui.f(20), ch = Ui.f(28);
            rect.set(x, base - Ui.f(21), x + cw, base - Ui.f(21) + ch);
            c.drawRoundRect(rect, Ui.f(7), Ui.f(7), chip);
            c.drawText(key, x + Ui.f(10), base - Ui.f(1), chipP);
            float tx = x + cw + Ui.f(10);
            c.drawText(what, tx, base, hintP);
            return tx + hintP.measureText(what);
        }
    }
}
