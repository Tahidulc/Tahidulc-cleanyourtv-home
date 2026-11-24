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

    /**
     * The name a reminder's channel shows under now (a name given to it since, or its channel's without the quality, one
     * row per channel), else the name it had when the reminder was set.
     */
    static String channelName(Context c, Reminders.Reminder r) {
        com.gathin.homefieldtv.engine.Channel ch = com.gathin.homefieldtv.engine.Library.get(c).byKey(r.key);
        return ch == null ? r.channel : com.gathin.homefieldtv.prefs.ChannelEdits.name(ch);
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
                .setContentText(channelName(app, r) + "  ·  " + LiveText.hm(r.start))
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
            lp.x = Ui.px(70);
            lp.y = Ui.px(60);
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

    /**
     * The card itself in the 07 look (the approved still 29): drawn here, used by the window and by ReminderActivity. A
     * frosted dark card 660 wide, rounded 40 (the plain dark material: it may be over another app): the channel's logo
     * on its tile (in its own colour: Logos), Homefield's mark and name with "Starts in 1 min" by a bell in amber, the
     * title (36 px), the channel, its number and times, two lines about the programme (from the guide, where the app
     * has it), the programme's picture faint at the top right (fitted round its faces), and the keys as buttons: OK
     * Watch now (white) and BACK Not now; a thin amber bar along its foot runs out as the card's time does.
     */
    static final class Face extends View {
        interface Answer {
            void ok();

            void close();
        }

        /** Its window (design px): the card at its tallest; a card with nothing to say about the programme is shorter. */
        static final float W = 660f, H = 310f;
        private final Reminders.Reminder r;
        private final Answer answer;
        private final long shownAt = android.os.SystemClock.uptimeMillis();
        private final Frost frost = new Frost(Look07.MAT_DARK);
        private final Paint amber = new Paint(Paint.ANTI_ALIAS_FLAG), amberInk = new Paint(Paint.ANTI_ALIAS_FLAG),
                fill = new Paint(Paint.ANTI_ALIAS_FLAG), bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final TextPaint kickP = Type.paint(19f, Type.BOLD, Look07.AMBER), brandP = Type.paint(19f, Type.BOLD, Look07.L2),
                titleP = Type.paint(36f, Type.BOLD, Look07.L1), subP = Type.paint(19f, Type.REGULAR, Look07.L2),
                descP = Type.paint(19f, Type.REGULAR, Look07.L2), keyP = Type.paint(14f, Type.EXTRABOLD, Look07.INK),
                buttonP = Type.paint(22f, Type.SEMIBOLD, Look07.INK), nameP = Type.paint(26f, Type.BOLD, Look07.L1);
        private final RectF rect = new RectF();
        private final Runnable redraw = this::invalidate;
        /** What the guide says of the programme (its channel, schedule and index), read once; the words cut to fit once. */
        private String sub = "", desc = "", pic, titleFit, subFit, name = "";
        private com.gathin.homefieldtv.engine.Channel ch;
        private com.gathin.homefieldtv.engine.Guide.Schedule sched;
        private int index = -1;
        private android.text.StaticLayout descL;
        private boolean read;

        Face(Context c, Reminders.Reminder r, Answer answer) {
            super(c);
            this.r = r;
            this.answer = answer;
            setFocusable(true);
            setFocusableInTouchMode(true);
            amber.setColor(Look07.AMBER);
            amberInk.setColor(Look07.AMBER_INK);
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

        /** The programme's line, what it is about and its picture, from the guide where the app has it (once). */
        private void read() {
            if (read) return;
            read = true;
            com.gathin.homefieldtv.engine.Library lib = com.gathin.homefieldtv.engine.Library.get(getContext());
            ch = lib.byKey(r.key);
            com.gathin.homefieldtv.engine.Guide.Schedule s = ch == null ? null : lib.schedule(ch.key);
            int i = s == null ? -1 : s.at(r.start + 1);
            int n = ch == null ? 0 : lib.number(ch);
            String times = r.end > r.start ? LiveText.hm(r.start) + " – " + LiveText.hm(r.end) : LiveText.hm(r.start);
            name = ch == null ? r.channel : com.gathin.homefieldtv.prefs.ChannelEdits.name(ch);
            sub = (n > 0 ? n + " " : "") + name + "  ·  " + times;
            if (i >= 0 && s.start[i] == r.start) {
                sched = s;
                index = i;
                desc = s.desc[i] == null ? "" : s.desc[i].trim();
                // Asked for once (drawn again when it is known).
                pic = com.gathin.homefieldtv.engine.Exec.inline ? ProgrammePictures.peek(ch, s, i) : ProgrammePictures.get(getContext(), ch, s, i, redraw);
            }
        }

        /** Where the buttons go (design px): under the lines about the programme, as many as there are (two at most). */
        private float buttonsTop() {
            int lines = descL == null ? 0 : descL.getLineCount();
            return 128f + (lines == 0 ? 0f : 14f + 26.6f * lines);
        }

        @Override protected void onDraw(Canvas c) {
            read();
            // Its picture asked for: looked at again as it comes.
            if (pic == null && index >= 0) pic = ProgrammePictures.peek(ch, sched, index);
            float w = getWidth(), pad = Ui.f(28f);
            if (!desc.isEmpty() && descL == null && w > 0) descL = InfoCard.lines(desc, descP, Math.round(w - pad * 2f), 2, 1.4f / 1.21f);
            float y = buttonsTop(), h = Ui.f(y + 22f + 62f + 30f);
            rect.set(0, 0, w, h);
            frost.draw(c, rect, Ui.f(Look07.R_MENU), 1f);
            // The programme's picture, faint at the top right.
            if (pic != null && !pic.isEmpty()) {
                android.graphics.Bitmap b = Pics.get(pic, Ui.px(360f), Ui.px(220f), Pics.PANEL, redraw);
                if (b != null) {
                    c.save();
                    c.clipRect(0, 0, w, h);
                    rect.set(w - Ui.f(360f), 0, w, Ui.f(220f));
                    bmp.setAlpha(0x57);
                    c.drawBitmap(b, null, rect, bmp);
                    c.restore();
                }
            }
            // The channel's logo on its tile (its name when there is no logo).
            float lx = pad, ly = Ui.f(26f + 8f), lw = Ui.f(110f), lh = Ui.f(72f);
            rect.set(lx, ly, lx + lw, ly + lh);
            if (!Logos.draw(c, r.logo, rect, redraw)) {
                String n = Ui.fit(name, nameP, lw - Ui.f(16f));
                c.drawText(n, rect.centerX() - nameP.measureText(n) / 2f, Type.centred(nameP, rect.centerY()), nameP);
            }
            // Homefield · the bell and "Starts in 1 min"; the title; the channel and its time.
            float x0 = lx + lw + Ui.f(18f), room = w - x0 - pad, ky = Ui.f(45f);
            float ms = Ui.f(24f);
            Dark.mark(c, x0, ky - Ui.f(19f), ms, amber, amberInk);
            float kx = x0 + ms + Ui.f(10f);
            c.drawText("Homefield", kx, ky, brandP);
            kx += brandP.measureText("Homefield") + Ui.f(10f);
            c.drawText("·", kx, ky, kickP);
            kx += kickP.measureText("·") + Ui.f(10f);
            Icons07.draw(c, Icons07.BELL, kx + Ui.f(9f), ky - Ui.f(7f), Ui.f(18f), Look07.AMBER);
            kx += Ui.f(18f + 10f);
            c.drawText(when(System.currentTimeMillis(), r.start), kx, ky, kickP);
            if (titleFit == null) {
                titleFit = Ui.fit(r.title, titleP, room);
                subFit = Ui.fit(sub, subP, room);
            }
            c.drawText(titleFit, x0, Ui.f(87f), titleP);
            c.drawText(subFit, x0, Ui.f(118f), subP);
            // What it is about, two lines at most.
            if (descL != null) Ui.draw(c, descL, pad, Ui.f(128f + 14f));
            // The keys as buttons: OK Watch now, BACK Not now.
            float cy = Ui.f(y + 22f + 31f), bx = pad;
            bx += button(c, bx, cy, "OK", "Watch now", true) + Ui.f(14f);
            button(c, bx, cy, "BACK", "Not now", false);
            // The card's time running out, along its foot.
            float k = 1f - Math.min(1f, (android.os.SystemClock.uptimeMillis() - shownAt) / (float) SHOW_MS);
            fill.setColor(0x1FFFFFFF);
            c.save();
            rect.set(0, 0, w, h);
            c.clipRect(0, h - Ui.f(6f), w, h);
            c.drawRoundRect(rect, Ui.f(Look07.R_MENU), Ui.f(Look07.R_MENU), fill);
            c.clipRect(0, h - Ui.f(6f), w * k, h);
            fill.setColor(Look07.AMBER);
            c.drawRoundRect(rect, Ui.f(Look07.R_MENU), Ui.f(Look07.R_MENU), fill);
            c.restore();
            if (k > 0f && !com.gathin.homefieldtv.engine.Exec.inline) postInvalidateDelayed(250);
        }

        /** A key's button: its key in a chip, then what it does; the first white (OK), the other on glass. */
        private float button(Canvas c, float x, float cy, String key, String what, boolean lit) {
            float h = Ui.f(62f), kw = Math.max(Ui.f(40f), keyP.measureText(key) + Ui.f(16f)), kh = Ui.f(30f);
            float w = Ui.f(16f) + kw + Ui.f(14f) + buttonP.measureText(what) + Ui.f(30f);
            rect.set(x, cy - h / 2f, x + w, cy + h / 2f);
            fill.setColor(lit ? Look07.L1 : Look07.BUTTON);
            c.drawRoundRect(rect, h / 2f, h / 2f, fill);
            rect.set(x + Ui.f(16f), cy - kh / 2f, x + Ui.f(16f) + kw, cy + kh / 2f);
            fill.setColor(lit ? 0x1F000000 : 0x2EFFFFFF);
            c.drawRoundRect(rect, Ui.f(9f), Ui.f(9f), fill);
            keyP.setColor(lit ? Look07.INK : Look07.L1);
            c.drawText(key, rect.centerX() - keyP.measureText(key) / 2f, Type.centred(keyP, cy), keyP);
            buttonP.setColor(lit ? Look07.INK : Look07.L1);
            c.drawText(what, rect.right + Ui.f(14f), Type.centred(buttonP, cy), buttonP);
            return w;
        }
    }
}
