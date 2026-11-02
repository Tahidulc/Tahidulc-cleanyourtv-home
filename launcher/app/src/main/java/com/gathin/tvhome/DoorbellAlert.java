package com.gathin.tvhome;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The doorbell, over whatever is on. CloudEdge (Meari's app, which many video doorbells use), signed in on this TV,
 * gets the doorbell's ring as a notification; WatchHistory, the launcher's notification listener, passes CloudEdge's
 * here, and a ring shows as a card in the top right corner: the visitor's snapshot when the ring carries one (else a
 * bell on amber), DOORBELL and the time, "Someone's at the door" and the doorbell's name, OK to answer (CloudEdge's own
 * ring screen, through the notification's tap) and Back to close. A ding-dong plays once, whatever is on ducking under
 * it. It stays 30 seconds; a second ring while it shows refreshes it without ringing again. Never with the screen off
 * (it does not wake the TV), nor while CloudEdge is in front. CloudEdge's other news (motion, battery, sharing) is not
 * a ring and shows nothing.
 */
final class DoorbellAlert {
    static final String CLOUDEDGE = "com.cloudedge.smarteye";
    private static final String TAG = "Doorbell";
    private static final long SHOW_MS = 30_000L;
    private static final int AMBER = 0xFFFFB020;
    /** What a ring says; a call-style notification (CloudEdge's incoming visitor) is one whatever it says. */
    private static final Pattern RING = Pattern.compile(
            "(?i).*(ring|doorbell|door bell|visitor|at the door|at your door|someone is|calling|incoming|answer|bell).*");
    private static final Pattern NOT_RING = Pattern.compile(
            "(?i).*(offline|online|battery|low power|charg|firmware|upgrade|update|shared|share|invitation|log ?in|password|"
                    + "storage|sd card|cloud plan|subscription|expir|motion|moving|human|person detected|pet).*");

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static FrameLayout host;
    private static long shownAt;
    /** The latest ring: {picture or null, when, the doorbell's name}; null before the first. */
    private static volatile Object[] last;
    private static final Runnable close = () -> { close(true); pipClose(); };
    /** The live view's window in the corner (doorbell_pip, on a TV with windows): its task, -1 for none. */
    private static int pipTask = -1;
    private static Context pipCtx;

    private static void pipClose() {
        if (pipTask >= 0 && pipCtx != null) Windowed.close(pipCtx, pipTask);
        pipTask = -1;
    }

    private DoorbellAlert() { }

    static Object[] lastRing() {
        return last;
    }

    static boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo(app(c), 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** The doorbell's app: CloudEdge, or the theme's "doorbell_app" for another of Meari's (the same rings). */
    static String app(Context c) {
        return Config.themeString(c, "doorbell_app", CLOUDEDGE).trim();
    }

    /** A notification from the doorbell's app (or the adb stand-in): a ring shows the card. */
    static void posted(Context c, StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (n == null) return;
        String title = words(n.extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = words(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (text.isEmpty()) text = words(n.extras.getCharSequence(Notification.EXTRA_TEXT));
        String all = title + " " + text;
        boolean call = Notification.CATEGORY_CALL.equals(n.category) || n.fullScreenIntent != null;
        boolean ring = call || (RING.matcher(all).matches() && !NOT_RING.matcher(all).matches());
        Log.i(TAG, (ring ? "ring" : "not a ring") + (call ? " (call)" : "") + ": " + all);
        if (!ring) return;
        long when = n.when > 0 ? n.when : sbn.getPostTime();
        if (System.currentTimeMillis() - when > 2 * 60_000L) return;
        Bitmap pic = CameraAlert.picture(c, n);
        String name = title.isEmpty() || title.toLowerCase(Locale.ROOT).contains("cloudedge") || RING.matcher(title).matches()
                ? "Front door" : title;
        Object[] before = last;
        if (pic == null && before != null && when - (Long) before[1] < 90_000L) pic = (Bitmap) before[0];
        last = new Object[]{pic, when, name};
        PowerManager pm = c.getSystemService(PowerManager.class);
        if (pm != null && !pm.isInteractive()) return;
        if (app(c).equals(AppSearchService.front())) return;
        PendingIntent tap = n.contentIntent != null ? n.contentIntent : n.fullScreenIntent;
        Bitmap shot = pic;
        main.post(() -> show(c.getApplicationContext(), name, shot, tap, when));
    }

    private static String words(CharSequence s) {
        return s == null ? "" : s.toString().replaceAll("\\s+", " ").trim();
    }

    // ---- the card ------------------------------------------------------------------------------------------------

    private static void show(Context c, String name, Bitmap pic, PendingIntent tap, long when) {
        // Rung again while the card shows: the card afresh with the new picture, the ding-dong not again.
        boolean again = host != null && System.currentTimeMillis() - shownAt < 45_000L;
        close(false);
        shownAt = System.currentTimeMillis();
        float s = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        int w = Math.round(560 * s), ph = Math.round(w * 9f / 16f), band = Math.round(150 * s), m = Math.round(24 * s);
        float corner = 24 * s;
        Typeface semi = c.getResources().getFont(R.font.inter_semibold), heavy = c.getResources().getFont(R.font.outfit_extrabold);

        FrameLayout win = new FrameLayout(c);
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xF7161C33, 0xF70B0F1E});
        glass.setCornerRadius(corner);
        glass.setStroke(Math.max(1, Math.round(2 * s)), 0x8CFFB020);
        card.setBackground(glass);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), corner);
            }
        });
        card.setClipToOutline(true);
        card.setElevation(16 * s);

        // The picture: the visitor, or a bell on the doorbell's amber with rings going out from it.
        FrameLayout picture = new FrameLayout(c);
        View bell = null;
        if (pic != null) {
            ImageView shot = new ImageView(c);
            shot.setScaleType(ImageView.ScaleType.CENTER_CROP);
            shot.setImageBitmap(pic);
            picture.addView(shot, new FrameLayout.LayoutParams(-1, -1));
        } else {
            picture.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFFFC14D, 0xFFE07B00, 0xFF8A3B00}));
            for (int k = 0; k < 2; k++) {
                View ring = new View(c);
                GradientDrawable rg = new GradientDrawable();
                rg.setShape(GradientDrawable.OVAL);
                rg.setStroke(Math.max(2, Math.round(4 * s)), 0x99FFFFFF);
                ring.setBackground(rg);
                int rs = Math.round(150 * s);
                picture.addView(ring, new FrameLayout.LayoutParams(rs, rs, Gravity.CENTER));
                ring.setAlpha(0f);
                pulse(ring, k * 700L);
            }
            TextView b = text(c, "🔔", 96 * s, Color.WHITE, null);
            b.setShadowLayer(14 * s, 0, 4 * s, 0x66000000);
            picture.addView(b, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            bell = b;
        }
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x8C000000, 0x00000000, 0x00000000}));
        picture.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout tagRow = new LinearLayout(c);
        tagRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView tag = text(c, "DOORBELL", 15 * s, 0xFF1A1100, semi);
        tag.setLetterSpacing(0.14f);
        tag.setPadding(Math.round(12 * s), Math.round(4 * s), Math.round(12 * s), Math.round(4 * s));
        GradientDrawable tg = new GradientDrawable();
        tg.setColor(AMBER);
        tg.setCornerRadius(8 * s);
        tag.setBackground(tg);
        tagRow.addView(tag);
        TextView time = text(c, new SimpleDateFormat("HH:mm", Locale.UK).format(new Date(when)), 17 * s, Color.WHITE, semi);
        time.setShadowLayer(6 * s, 0, 1, 0xAA000000);
        LinearLayout.LayoutParams tml = new LinearLayout.LayoutParams(-2, -2);
        tml.leftMargin = Math.round(12 * s);
        tagRow.addView(time, tml);
        FrameLayout.LayoutParams trl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        trl.leftMargin = trl.topMargin = Math.round(18 * s);
        picture.addView(tagRow, trl);
        card.addView(picture, new LinearLayout.LayoutParams(w, ph));

        // Under it: a bell that swings, who is there, and the keys.
        LinearLayout foot = new LinearLayout(c);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setPadding(Math.round(24 * s), Math.round(18 * s), Math.round(24 * s), Math.round(16 * s));
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = text(c, "🔔", 30 * s, Color.WHITE, null);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x33FFB020);
        bg.setStroke(Math.max(1, Math.round(2 * s)), AMBER);
        badge.setBackground(bg);
        int bs = Math.round(60 * s);
        head.addView(badge, new LinearLayout.LayoutParams(bs, bs));
        LinearLayout says = new LinearLayout(c);
        says.setOrientation(LinearLayout.VERTICAL);
        says.addView(text(c, "Someone's at the door", 30 * s, Color.WHITE, heavy));
        says.addView(text(c, name + "  ·  just now", 18 * s, 0xBFFFFFFF, semi));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, -2, 1f);
        sl.leftMargin = Math.round(18 * s);
        head.addView(says, sl);
        foot.addView(head);
        LinearLayout keys = new LinearLayout(c);
        keys.setGravity(Gravity.CENTER_VERTICAL);
        String remote = Config.themeString(c, "ok_button", "tcl");
        ImageView ok = new ImageView(c);
        int ks = Math.round(30 * s);
        ok.setImageBitmap(GoalWatchService.okButton(remote, ks / 22f, true));
        keys.addView(ok, new LinearLayout.LayoutParams(ks, ks));
        TextView okWords = text(c, "Answer", 18 * s, Color.WHITE, semi);
        LinearLayout.LayoutParams ow = new LinearLayout.LayoutParams(-2, -2);
        ow.leftMargin = Math.round(10 * s);
        ow.rightMargin = Math.round(28 * s);
        keys.addView(okWords, ow);
        ImageView back = new ImageView(c);
        back.setImageBitmap(GoalWatchService.backButton(remote, ks));
        back.setTag(PopupMotion.BACK_KEY);
        keys.addView(back, new LinearLayout.LayoutParams(ks, ks));
        TextView backWords = text(c, "Close", 18 * s, 0xBFFFFFFF, semi);
        LinearLayout.LayoutParams bw = new LinearLayout.LayoutParams(-2, -2);
        bw.leftMargin = Math.round(10 * s);
        keys.addView(backWords, bw);
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(-1, -2);
        kl.topMargin = Math.round(14 * s);
        foot.addView(keys, kl);
        card.addView(foot, new LinearLayout.LayoutParams(w, band));
        // The time left: an amber line along the foot, running down.
        View left = new View(c);
        left.setBackgroundColor(AMBER);
        left.setPivotX(0);
        card.addView(left, new LinearLayout.LayoutParams(w, Math.max(2, Math.round(4 * s))));

        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2);
        cl.leftMargin = cl.topMargin = cl.rightMargin = cl.bottomMargin = m;
        win.addView(card, cl);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2, -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Focusable, so OK and Back come here while it shows; whatever is on keeps playing underneath.
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = Math.max(0, Math.round(44 * s) - m);
        lp.y = Math.max(0, Math.round(80 * s) - m);
        lp.setTitle("DoorbellAlert");
        try {
            c.getSystemService(WindowManager.class).addView(win, lp);
        } catch (Exception e) {
            Log.w(TAG, "no overlay: " + e);
            return;
        }
        host = win;
        win.setFocusable(true);
        win.setFocusableInTouchMode(true);
        win.requestFocus();
        final boolean[] down = {false};
        win.setOnKeyListener((v, code, e) -> {
            // Acted on as the key comes up, and only for a press that began here.
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (!down[0] && code == KeyEvent.KEYCODE_BACK) PopupMotion.press(win);
                down[0] = true;
                return code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
            }
            if (!down[0]) return false;
            down[0] = false;
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                close(false);
                // Already live in the corner: that window grows to the whole screen; else the ring's own screen.
                if (pipTask >= 0) { Windowed.full(c, pipTask); pipTask = -1; } else answer(c, tap);
                return true;
            }
            if (code == KeyEvent.KEYCODE_BACK) { closeByBack(); return true; }
            return false;
        });

        // In: dropped in from above with a little bounce, the bell swinging, the time running down.
        card.setAlpha(0f);
        card.setTranslationY(-60 * s);
        card.animate().alpha(1f).translationY(0).setDuration(420).setInterpolator(new OvershootInterpolator(1.3f)).withLayer().start();
        swing(badge, 3);
        if (bell != null) swing(bell, 3);
        left.animate().scaleX(0f).setDuration(SHOW_MS).setInterpolator(new LinearInterpolator()).start();
        main.removeCallbacks(close);
        main.postDelayed(close, SHOW_MS);
        if (!again && Config.themeFlag(c, "doorbell_chime", true)) chime(c);
        // The live view in a window in the bottom right corner while the card shows, what is on going on behind it.
        if (!again && tap != null && Config.themeFlag(c, "doorbell_pip", false) && Windowed.supported(c)) {
            pipCtx = c;
            Windowed.openTap(c, tap, app(c), Windowed.rect(c, 1920 - 24 - 640, 1080 - 24 - 360, 1920 - 24, 1080 - 24), id -> {
                pipTask = id;
                AlertLog.note(c, "doorbell: " + (id >= 0 ? "live view in the corner" : "live view did not open in a window"));
            });
        }
    }

    private static TextView text(Context c, String s, float px, int colour, Typeface face) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(colour);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        if (face != null) t.setTypeface(face);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** A bell swinging from its top, a few times, settling. */
    private static void swing(View v, int times) {
        v.post(() -> {
            v.setPivotX(v.getWidth() / 2f);
            v.setPivotY(v.getHeight() * 0.12f);
            android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofFloat(v, View.ROTATION,
                    0f, -18f, 15f, -11f, 8f, -4f, 2f, 0f);
            a.setDuration(1100);
            a.setRepeatCount(times - 1);
            a.setStartDelay(250);
            a.start();
            v.setTag(R.id.v2_state, a);
        });
    }

    /** A ring going out from the bell: grown and faded, again and again while the card shows. */
    private static void pulse(View ring, long delay) {
        android.animation.AnimatorSet set = new android.animation.AnimatorSet();
        android.animation.ObjectAnimator sx = android.animation.ObjectAnimator.ofFloat(ring, View.SCALE_X, 0.6f, 2.2f);
        android.animation.ObjectAnimator sy = android.animation.ObjectAnimator.ofFloat(ring, View.SCALE_Y, 0.6f, 2.2f);
        android.animation.ObjectAnimator al = android.animation.ObjectAnimator.ofFloat(ring, View.ALPHA, 0.9f, 0f);
        for (android.animation.ObjectAnimator o : new android.animation.ObjectAnimator[]{sx, sy, al}) {
            o.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            o.setDuration(1400);
        }
        set.playTogether(sx, sy, al);
        set.setStartDelay(delay);
        set.start();
        ring.setTag(set);
    }

    /**
     * Ding-dong: two soft bell tones (E then C, each with a little of its octave and twelfth, dying away), played once
     * at the TV's own volume, whatever is on ducking under it.
     */
    private static void chime(Context c) {
        tones(c, new double[]{659.25, 523.25}, 0.55f);
    }

    /**
     * Soft bell tones one after another (step seconds apart), each dying away, at the TV's own volume with whatever is
     * on ducking under them: the doorbell's ding-dong, and a message's three rising notes (TvLink).
     */
    static void tones(Context c, double[] notes, float step) {
        new Thread(() -> {
            AudioManager am = c.getSystemService(AudioManager.class);
            AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            AudioFocusRequest focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(aa).build();
            if (am != null) am.requestAudioFocus(focus);
            int rate = 44100, len = Math.round(rate * (step * (notes.length - 1) + 1.35f));
            double[] mix = new double[len];
            for (int k = 0; k < notes.length; k++) {
                int start = Math.round(rate * step * k);
                for (int i = start; i < len; i++) {
                    double t = (i - start) / (double) rate;
                    double env = Math.exp(-t * 2.6) * Math.min(1.0, t * 300);
                    double f = notes[k];
                    mix[i] += env * (Math.sin(2 * Math.PI * f * t) + 0.32 * Math.sin(4 * Math.PI * f * t) + 0.1 * Math.sin(6 * Math.PI * f * t));
                }
            }
            short[] pcm = new short[len];
            for (int i = 0; i < len; i++) pcm[i] = (short) Math.max(-32767, Math.min(32767, mix[i] * 0.3 * 32767 / 1.42));
            AudioTrack track = null;
            try {
                track = new AudioTrack.Builder().setAudioAttributes(aa)
                        .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(len * 2).build();
                track.write(pcm, 0, len);
                track.play();
                Thread.sleep(len * 1000L / rate + 100);
            } catch (Exception e) {
                Log.w(TAG, "chime: " + e);
            } finally {
                if (track != null) track.release();
                if (am != null) am.abandonAudioFocusRequest(focus);
            }
        }, "doorbell-chime").start();
    }

    /** OK: CloudEdge's own screen for the ring (the notification's tap), else CloudEdge as it was. */
    static void answer(Context c, PendingIntent tap) {
        if (tap != null) {
            try {
                android.app.ActivityOptions o = android.app.ActivityOptions.makeBasic();
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    o.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                }
                tap.send(c, 0, null, null, null, null, o.toBundle());
                return;
            } catch (Exception e) {
                Log.w(TAG, "tap: " + e);
            }
        }
        open(c);
    }

    /** CloudEdge opened (its phone launcher entry: it has no TV one). */
    static void open(Context c) {
        android.content.pm.PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(app(c));
        if (i == null) i = pm.getLaunchIntentForPackage(app(c));
        if (i == null) return;
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    /** Back while the card shows (the key helper, before any app): the card closes. True when it took the key. */
    static boolean key(KeyEvent e) {
        if (host == null || e.getKeyCode() != KeyEvent.KEYCODE_BACK) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) main.post(() -> PopupMotion.press(host));
        else if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main.post(DoorbellAlert::closeByBack);
        return true;
    }

    private static void closeByBack() {
        main.removeCallbacks(close);
        pipClose();
        FrameLayout v = host;
        host = null;
        if (v == null) return;
        v.setOnKeyListener(null);
        stop(v);
        notFocusable(v);
        PopupMotion.backOut(v, v.getWidth(), 0f, () -> remove(v));
    }

    private static void close(boolean fade) {
        main.removeCallbacks(close);
        FrameLayout v = host;
        host = null;
        if (v == null) return;
        v.setOnKeyListener(null);
        stop(v);
        if (!fade) { remove(v); return; }
        notFocusable(v);
        v.animate().alpha(0f).translationY(-v.getHeight() * 0.08f).setDuration(240).withLayer().withEndAction(() -> remove(v)).start();
    }

    private static void notFocusable(View v) {
        try {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getLayoutParams();
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            v.getContext().getSystemService(WindowManager.class).updateViewLayout(v, lp);
        } catch (Exception ignored) {
        }
    }

    private static void remove(View v) {
        try { v.getContext().getSystemService(WindowManager.class).removeView(v); } catch (Exception ignored) { }
    }

    /** The rings and swings stopped with the card. */
    private static void stop(View v) {
        if (v.getTag() instanceof android.animation.Animator) ((android.animation.Animator) v.getTag()).cancel();
        Object a = v.getTag(R.id.v2_state);
        if (a instanceof android.animation.Animator) ((android.animation.Animator) a).cancel();
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) stop(g.getChildAt(i));
        }
    }
}
