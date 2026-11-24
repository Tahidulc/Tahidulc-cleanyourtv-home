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
 * A reminder's panel, a minute before the programme: over whatever is on the TV, another app too, at the top right in
 * the 07 look, on the programme's own picture (Face): the channel's logo on its tile, the countdown, the programme's
 * title, its time and channel, the reminder's message, and Watch (OK), Remind again in 5 min and Dismiss (Back). It waits
 * a second and a half before it takes the remote (a key already on its way doesn't answer it); it goes by itself after
 * about 30 seconds. Shown in a window over other apps where Android lets Homefield TV (Display over other apps, asked
 * for once); otherwise as a notification that asks to come up full screen (ReminderActivity), Android permitting.
 */
final class ReminderCard {
    private ReminderCard() {}

    static final long SHOW_MS = 30_000L, HOLD_MS = 1_500L;
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

    /** Homefield TV while it is the app on screen (TvActivity's onResume to onPause): the panel goes inside it. */
    static java.lang.ref.WeakReference<TvActivity> front;

    /** The panel for a reminder now: inside Homefield TV when it is on screen, else over other apps, else the notification. */
    static void show(Context c, Reminders.Reminder r) {
        TvActivity in = front == null ? null : front.get();
        if (in != null && !in.isFinishing()) {
            in.push(new ReminderScreen(in, r));
            return;
        }
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

    /** Remind again in 5 min: the reminder kept, its card coming again then. */
    static void again(Context c, Reminders.Reminder r) {
        Context app = c.getApplicationContext();
        Reminders.add(app, r.againAt(System.currentTimeMillis() + Reminders.AGAIN_MS));
        ReminderAlarm.schedule(app);
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
            lp = new WindowManager.LayoutParams(Ui.px(Face.W), face.heightPx(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = Ui.px(64);
            lp.y = Ui.px(56);
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

        @Override public void again() {
            close();
            ReminderCard.again(app, r);
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
     * The reminder's panel in the 07 look (3.4: the family asked for "the panel with the programme background with a reminder
     * message"): 800 by 450, rounded 40, the programme's own picture filling it (Pics.FILL_CARD: its faces whole, toward
     * the right, clear of the words; else the channel's logo large on its own colour, never blank), a deep shade from its
     * left and its foot for the words: Homefield's mark with a bell and "Reminder", the countdown in a pill at the top
     * right ("Starts in 2:45", then "Starting now"), the channel's logo on its tile, the title, its time and channel, the
     * reminder's message, and three buttons: Watch (OK), Remind again in 5 min, Dismiss (BACK); Left and Right move between
     * them, the one the remote is on white. A thin amber line along its foot runs out as its time does. Used by the window
     * over other apps (and over Homefield TV itself) and by ReminderActivity. Nothing made while it draws but its words
     * once a second.
     *
     * <p>Its words are never cut short (3.9, "the reminders was cut off"): the title takes up to three lines where it needs
     * them, the message two, and the panel grows taller for them (heightPx, which its window and its places take), its buttons
     * below them; only a title longer than three lines, or a message longer than two,
     * ends in an ellipsis.
     */
    static final class Face extends View {
        interface Answer {
            void ok();

            void close();

            /** Remind again in 5 min. */
            void again();
        }

        /** Its window (design px): H with one line of title and one of message, taller for more of either. */
        static final float W = 800f, H = 450f;
        /** Where its words stand (design px): the title's first baseline, the time and channel's line below its last, the
         *  message's first baseline below that, the buttons' middle below the message's last, the panel's foot below
         *  them; the title's room (of the width) left of the picture's faces; its lines' spacing and the message's. */
        static final float TITLE_Y = 250f, SUB_GAP = 36f, MSG_GAP = 38f, BUTTONS_GAP = 60f, FOOT_GAP = 66f, PAD = 36f,
                TITLE_ROOM = 0.62f, TITLE_SPACING = 0.95f, MSG_SPACING = 1.2f;
        static final int TITLE_LINES = 3, MSG_LINES = 2;
        static final int WATCH = 0, AGAIN = 1, DISMISS = 2;
        static final String[] BUTTONS = {"Watch", "Remind again in 5 min", "Dismiss"}, KEYS = {"OK", "", "BACK"};
        private final Reminders.Reminder r;
        private final Answer answer;
        private final long shownAt = android.os.SystemClock.uptimeMillis();
        private final Paint amber = new Paint(Paint.ANTI_ALIAS_FLAG), amberInk = new Paint(Paint.ANTI_ALIAS_FLAG),
                fill = new Paint(Paint.ANTI_ALIAS_FLAG), bmp = new Paint(Paint.FILTER_BITMAP_FLAG), shade = new Paint(),
                foot = new Paint(), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint kickP = Type.paint(20f, Type.BOLD, Look07.AMBER), brandP = Type.paint(20f, Type.BOLD, Look07.L1),
                titleP = Type.paint(44f, Type.BOLD, Look07.L1), subP = Type.paint(21f, Type.SEMIBOLD, Look07.L2),
                msgP = Type.paint(21f, Type.REGULAR, Look07.L1), keyP = Type.paint(14f, Type.EXTRABOLD, Look07.INK),
                buttonP = Type.paint(21f, Type.SEMIBOLD, Look07.INK), nameP = Type.paint(26f, Type.BOLD, Look07.L1),
                pillP = Type.paint(20f, Type.BOLD, Look07.INK);
        private final RectF rect = new RectF();
        private final android.graphics.Path clip = new android.graphics.Path();
        private final Runnable redraw = this::invalidate;
        /** The button the remote is on. */
        int focus = WATCH;
        /** What the guide says of the programme (its channel, schedule and index), read once; the words cut to fit once. */
        private String sub = "", msg = "", pic, subFit = "", name = "", countdown = "";
        /** A logo not there (yet): the channel's name on its tile, in up to two lines, centred. */
        private android.text.StaticLayout standIn;
        /** Its words laid out for a width (once a width): the title's and the message's lines, where each stands. */
        private android.text.StaticLayout titleL, msgL;
        private float titleTop, subBase, msgTop, buttonsY, tall;
        private long countdownFor = -1;
        private com.gathin.homefieldtv.engine.Channel ch;
        private com.gathin.homefieldtv.engine.Guide.Schedule sched;
        private int index = -1;
        private boolean read;
        private float laidW = -1f;

        Face(Context c, Reminders.Reminder r, Answer answer) {
            super(c);
            this.r = r;
            this.answer = answer;
            setFocusable(true);
            setFocusableInTouchMode(true);
            amber.setColor(Look07.AMBER);
            amberInk.setColor(Look07.AMBER_INK);
            edge.setStyle(Paint.Style.STROKE);
            edge.setColor(0x33FFFFFF);
        }

        @Override public boolean dispatchKeyEvent(KeyEvent e) {
            int code = e.getKeyCode();
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_BUTTON_A:
                    if (e.getAction() == KeyEvent.ACTION_UP) press(focus);
                    return true;
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                case KeyEvent.KEYCODE_BUTTON_B:
                    if (e.getAction() == KeyEvent.ACTION_UP) answer.close();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (e.getAction() == KeyEvent.ACTION_DOWN) {
                        focus = Math.max(WATCH, Math.min(DISMISS, focus + (code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1)));
                        invalidate();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    return true;
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                case KeyEvent.KEYCODE_VOLUME_MUTE:
                    return false;
                default:
                    // Any other key: the panel makes way.
                    if (e.getAction() == KeyEvent.ACTION_DOWN) answer.close();
                    return true;
            }
        }

        /** One of its buttons pressed. */
        void press(int which) {
            if (which == WATCH) answer.ok();
            else if (which == AGAIN) answer.again();
            else answer.close();
        }

        /**
         * Its words laid out for width w (screen px), once a width: the title in up to three lines in its room, the time and
         * channel's line, the message in up to two lines across, the buttons under them; and the height they need.
         */
        private void lay(float w) {
            if (w == laidW && titleL != null) return;
            laidW = w;
            read();
            float pad = Ui.f(PAD), across = w - pad * 2f;
            String title = r.title == null || r.title.isEmpty() ? "Your programme" : r.title;
            titleL = InfoCard.lines(title, titleP, Math.round(w * TITLE_ROOM), TITLE_LINES, TITLE_SPACING);
            msgL = InfoCard.lines(msg, msgP, Math.round(across), MSG_LINES, MSG_SPACING);
            subFit = Ui.fit(sub, subP, across);
            // A logo not there (yet): the channel's name on its tile, in two lines and smaller rather than cut (26 px
            // down to 15, then cut).
            int tile = Math.round(Ui.f(120f - 16f));
            float full = nameP.getTextSize();
            for (float size = 26f; ; size -= 1f) {
                nameP.setTextSize(full * size / 26f);
                standIn = android.text.StaticLayout.Builder.obtain(name, 0, name.length(), nameP, tile)
                        .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 0.95f)
                        .setIncludePad(false).setMaxLines(2).setEllipsize(android.text.TextUtils.TruncateAt.END).build();
                if (size <= 15f || fits(standIn, tile)) break;
            }
            titleTop = Ui.f(TITLE_Y) - titleL.getLineBaseline(0);
            subBase = titleTop + titleL.getLineBaseline(titleL.getLineCount() - 1) + Ui.f(SUB_GAP);
            msgTop = subBase + Ui.f(MSG_GAP) - msgL.getLineBaseline(0);
            buttonsY = msgTop + msgL.getLineBaseline(msgL.getLineCount() - 1) + Ui.f(BUTTONS_GAP);
            tall = buttonsY + Ui.f(FOOT_GAP);
        }

        /** Pure enough: a laid-out block whole in its width (no line cut, none wider). */
        private static boolean fits(android.text.StaticLayout l, int width) {
            for (int i = 0; i < l.getLineCount(); i++) {
                if (l.getEllipsisCount(i) > 0 || l.getLineWidth(i) > width + 0.5f) return false;
            }
            return true;
        }

        /** Its height (screen px) for its words at its width: Face.H, taller for more lines of title or message. */
        int heightPx() {
            lay(Ui.f(W));
            return Math.round(tall);
        }

        /** How many lines its title and its message take (for the tests). */
        int titleLines() {
            lay(Ui.f(W));
            return titleL.getLineCount();
        }

        int messageLines() {
            lay(Ui.f(W));
            return msgL.getLineCount();
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
            sub = times + "  ·  " + (n > 0 ? n + " " : "") + name;
            msg = message(r.title, name, r.start, System.currentTimeMillis());
            if (i >= 0 && s.start[i] == r.start) {
                sched = s;
                index = i;
                // Asked for once (drawn again when it is known): Sky's, else one found wider (ProgrammePictures).
                pic = com.gathin.homefieldtv.engine.Exec.inline ? ProgrammePictures.peek(ch, s, i) : ProgrammePictures.get(getContext(), ch, s, i, redraw);
            } else {
                pic = "";
            }
        }

        /** Pure: the reminder's message: "Your reminder: The Long Tide starts at 20:00 on Harbour TV", or that it has. */
        static String message(String title, String channel, long start, long now) {
            String t = title == null || title.isEmpty() ? "Your programme" : title;
            if (start - now <= 30_000L) return "Your reminder: " + t + " is starting now on " + channel;
            return "Your reminder: " + t + " starts at " + LiveText.hm(start) + " on " + channel;
        }

        /** Pure: the countdown in its pill: "Starts in 2:45", "Starts in 12 min", "Starting now". */
        static String countdown(long left) {
            if (left <= 30_000L) return "Starting now";
            if (left >= 10 * 60_000L) return "Starts in " + (left + 30_000L) / 60_000L + " min";
            long s = (left + 999) / 1000;
            return String.format(java.util.Locale.UK, "Starts in %d:%02d", s / 60, s % 60);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            float rad = Ui.f(Look07.R_MENU);
            clip.reset();
            clip.addRoundRect(new RectF(0, 0, w, h), rad, rad, android.graphics.Path.Direction.CW);
            // The words' shade from the left and from the foot (made once a size, never a frame).
            shade.setShader(new android.graphics.LinearGradient(0, 0, w * 0.68f, 0, new int[]{0xF20B0B0F, 0xB30B0B0F, 0x000B0B0F},
                    new float[]{0f, 0.55f, 1f}, android.graphics.Shader.TileMode.CLAMP));
            foot.setShader(new android.graphics.LinearGradient(0, h * 0.42f, 0, h, 0x000B0B0F, 0xF00B0B0F,
                    android.graphics.Shader.TileMode.CLAMP));
        }

        @Override protected void onDraw(Canvas c) {
            read();
            if (pic == null && index >= 0) pic = ProgrammePictures.peek(ch, sched, index);
            float w = getWidth(), h = getHeight(), pad = Ui.f(PAD);
            if (w <= 0 || h <= 0) return;
            lay(w);
            c.save();
            c.clipPath(clip);
            // The programme's picture filling it; else the channel's logo large on its own colour (never blank).
            android.graphics.Bitmap b = pic != null && !pic.isEmpty() ? Pics.get(pic, Math.round(w), Math.round(h), Pics.FILL_CARD, redraw) : null;
            if (b != null) {
                rect.set(0, 0, w, h);
                c.drawBitmap(b, null, rect, bmp);
            } else {
                int col = Logos.colour(r.logo);
                fill.setColor(col == 0 ? 0xFF1E2430 : col);
                c.drawRect(0, 0, w, h, fill);
                rect.set(w * 0.52f, h * 0.16f, w - pad, h * 0.62f);
                Logos.draw(c, r.logo, rect, redraw);
            }
            c.drawRect(0, 0, w, h, shade);
            c.drawRect(0, 0, w, h, foot);
            c.restore();
            edge.setStrokeWidth(Math.max(1f, Ui.f(1.5f)));
            rect.set(0, 0, w, h);
            c.drawRoundRect(rect, Ui.f(Look07.R_MENU), Ui.f(Look07.R_MENU), edge);

            // Top: Homefield's mark, the bell and "Reminder"; the countdown in a pill at the right.
            float ky = Ui.f(58f), ms = Ui.f(26f);
            Dark.mark(c, pad, ky - Ui.f(20f), ms, amber, amberInk);
            float kx = pad + ms + Ui.f(12f);
            c.drawText("Homefield", kx, ky, brandP);
            kx += brandP.measureText("Homefield") + Ui.f(12f);
            Icons07.draw(c, Icons07.BELL, kx + Ui.f(10f), ky - Ui.f(7f), Ui.f(20f), Look07.AMBER);
            kx += Ui.f(20f + 10f);
            c.drawText("Reminder", kx, ky, kickP);
            long left = r.start - System.currentTimeMillis(), second = left / 1000;
            if (second != countdownFor) {
                countdownFor = second;
                countdown = countdown(left);
            }
            float pw = pillP.measureText(countdown) + Ui.f(36f), ph = Ui.f(44f);
            rect.set(w - pad - pw, ky - Ui.f(30f), w - pad, ky - Ui.f(30f) + ph);
            fill.setColor(Look07.AMBER);
            c.drawRoundRect(rect, ph / 2f, ph / 2f, fill);
            c.drawText(countdown, rect.centerX() - pillP.measureText(countdown) / 2f, Type.centred(pillP, rect.centerY()), pillP);

            // The channel's logo on its tile, the title (more lines where it needs them), its time and channel, the message.
            float ly = Ui.f(118f), lw = Ui.f(120f), lh = Ui.f(74f);
            rect.set(pad, ly, pad + lw, ly + lh);
            if (!Logos.draw(c, r.logo, rect, redraw)) {
                Ui.draw(c, standIn, rect.centerX() - standIn.getWidth() / 2f, rect.centerY() - standIn.getHeight() / 2f);
            }
            Ui.draw(c, titleL, pad, titleTop);
            c.drawText(subFit, pad, subBase, subP);
            Ui.draw(c, msgL, pad, msgTop);

            // The buttons under the words: Watch (OK), Remind again in 5 min, Dismiss (BACK).
            float cy = buttonsY, bx = pad;
            for (int i = 0; i < BUTTONS.length; i++) bx += button(c, bx, cy, KEYS[i], BUTTONS[i], i == focus) + Ui.f(14f);

            // Its time running out, along its foot.
            float k = 1f - Math.min(1f, (android.os.SystemClock.uptimeMillis() - shownAt) / (float) SHOW_MS);
            fill.setColor(0x1FFFFFFF);
            c.save();
            c.clipPath(clip);
            c.drawRect(0, h - Ui.f(6f), w, h, fill);
            fill.setColor(Look07.AMBER);
            c.drawRect(0, h - Ui.f(6f), w * k, h, fill);
            c.restore();
            if (k > 0f && !com.gathin.homefieldtv.engine.Exec.inline) postInvalidateDelayed(250);
        }

        /** A button: its key in a chip (none for Remind again), then what it does; the remote's white, the others on glass. */
        private float button(Canvas c, float x, float cy, String key, String what, boolean lit) {
            float h = Ui.f(58f), kw = key.isEmpty() ? 0f : Math.max(Ui.f(40f), keyP.measureText(key) + Ui.f(16f)), kh = Ui.f(28f);
            float w = Ui.f(18f) + (kw > 0 ? kw + Ui.f(12f) : 0f) + buttonP.measureText(what) + Ui.f(24f);
            rect.set(x, cy - h / 2f, x + w, cy + h / 2f);
            fill.setColor(lit ? Look07.L1 : 0x40FFFFFF);
            c.drawRoundRect(rect, h / 2f, h / 2f, fill);
            float tx = x + Ui.f(18f);
            if (kw > 0) {
                rect.set(tx, cy - kh / 2f, tx + kw, cy + kh / 2f);
                fill.setColor(lit ? 0x1F000000 : 0x2EFFFFFF);
                c.drawRoundRect(rect, Ui.f(9f), Ui.f(9f), fill);
                keyP.setColor(lit ? Look07.INK : Look07.L1);
                c.drawText(key, rect.centerX() - keyP.measureText(key) / 2f, Type.centred(keyP, cy), keyP);
                tx = rect.right + Ui.f(12f);
            }
            buttonP.setColor(lit ? Look07.INK : Look07.L1);
            c.drawText(what, tx, Type.centred(buttonP, cy), buttonP);
            return w;
        }
    }
}
