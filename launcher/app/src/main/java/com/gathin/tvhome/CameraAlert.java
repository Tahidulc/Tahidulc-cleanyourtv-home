package com.gathin.tvhome;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Motion at a camera, over whatever is on. The Tuya Smart app on this TV gets the camera's alarms (motion, a person)
 * as notifications; WatchHistory, already the launcher's notification listener, passes Tuya's here, and an alarm shows
 * as a card in the bottom corner: the snapshot the alert carries (when it carries one), a red MOTION tag, the camera's
 * name, what Tuya says and the time. OK opens the camera's live view in Tuya Smart (AppSearchService.camera); Back or
 * any other key closes it, and so do 20 seconds. Never while the screen is off (it does not wake the TV), and not while
 * Tuya Smart is in front, where the camera is already on screen.
 *
 * Which cameras: the theme's "cameras" list, by name as Tuya Smart calls them ("Front Right"); without one, any
 * Tuya alarm. The first in the list is the one the top bar's camera button opens.
 */
final class CameraAlert {
    static final String TUYA = "com.tuya.smart";
    private static final String TAG = "Camera";
    private static final long SHOW_MS = 20_000L;
    /** The card's width; it is 16:9, the snapshot filling it under its words. */
    private static final int WIDTH_DP = 264;
    private static final float CORNER_DP = 14f;
    private static final int RED = 0xFFFF3B30;
    /** What an alarm says (motion, a person, the doorbell); the rest from Tuya (offline, battery, updates) is not one. */
    private static final Pattern ALARM = Pattern.compile(
            "(?i).*(motion|moving|movement|move|person|people|human|body|someone|detect|alarm|intru|visitor|doorbell|"
                    + "ring|face|pet|vehicle|car |package|parcel|sound|noise|cry|linger|stay).*");
    private static final Pattern NOT_ALARM = Pattern.compile(
            "(?i).*(offline|online|battery|firmware|upgrade|update available|shared|invitation|log ?in|password).*");

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static FrameLayout host;
    private static final Runnable close = () -> close(true);

    private CameraAlert() { }

    /** The cameras this TV alerts for and opens, as Tuya Smart names them: the theme's "cameras" (none: any). */
    static List<String> cameras(Context c) {
        return Config.themeStrings(c, "cameras", new ArrayList<>());
    }

    /** A notification from Tuya Smart: an alarm from one of the cameras is shown; anything else is left alone. */
    static void posted(Context c, StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return;
        if ((n.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE
                | Notification.FLAG_GROUP_SUMMARY)) != 0) return;
        String title = words(n.extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = words(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (text.isEmpty()) text = words(n.extras.getCharSequence(Notification.EXTRA_TEXT));
        Bitmap pic = picture(c, n);
        // Each Tuya notification in the log (adb logcat -s Camera), to see what its alarms say.
        Log.i(TAG, "tuya: title=" + title + " | text=" + text + " | picture=" + (pic != null)
                + " | channel=" + n.getChannelId());
        String all = title + " " + text;
        if (NOT_ALARM.matcher(all).matches() || (!ALARM.matcher(all).matches() && pic == null)) return;
        String camera = null;
        List<String> mine = cameras(c);
        for (String name : mine) {
            if (all.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) { camera = name; break; }
        }
        if (camera == null && !mine.isEmpty()) return;   // another camera's
        if (camera == null) camera = title.isEmpty() ? "Camera" : title;
        // An alarm from a while ago (delivered as the TV woke from standby) is old news.
        long when = n.when > 0 ? n.when : sbn.getPostTime();
        if (System.currentTimeMillis() - when > 2 * 60_000L) return;
        PowerManager pm = c.getSystemService(PowerManager.class);
        if (pm != null && !pm.isInteractive()) return;
        if (TUYA.equals(AppSearchService.front())) return;
        // What Tuya says, without the camera's name it opens with ("Front Right: Motion detected").
        String what = text.isEmpty() ? title : text;
        if (what.toLowerCase(Locale.ROOT).startsWith(camera.toLowerCase(Locale.ROOT))) {
            what = what.substring(camera.length()).replaceFirst("^[\\s:\\-\u2013\u2014,]+", "");
        }
        if (what.isEmpty() || what.equalsIgnoreCase(camera)) what = "Motion detected";
        final String name = camera, line = what;
        main.post(() -> show(c.getApplicationContext(), name, line, pic, n.contentIntent));
    }

    private static String words(CharSequence s) {
        return s == null ? "" : s.toString().replaceAll("\\s+", " ").trim();
    }

    /**
     * The alarm's snapshot: its big picture, or a large icon shaped like a photo (wider than tall and not small; a
     * square one is only Tuya's logo).
     */
    private static Bitmap picture(Context c, Notification n) {
        Object p = n.extras.get(Notification.EXTRA_PICTURE);
        if (p instanceof Bitmap) return (Bitmap) p;
        if (p instanceof Icon) return bitmap(((Icon) p).loadDrawable(c));
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            Object icon = n.extras.get(Notification.EXTRA_PICTURE_ICON);
            if (icon instanceof Icon) return bitmap(((Icon) icon).loadDrawable(c));
        }
        Icon large = n.getLargeIcon();
        Drawable d = large == null ? null : large.loadDrawable(c);
        if (d != null && d.getIntrinsicWidth() >= 160 && d.getIntrinsicWidth() >= d.getIntrinsicHeight() * 1.2f) return bitmap(d);
        return null;
    }

    private static Bitmap bitmap(Drawable d) {
        if (d == null) return null;
        if (d instanceof BitmapDrawable) return ((BitmapDrawable) d).getBitmap();
        int w = d.getIntrinsicWidth(), h = d.getIntrinsicHeight();
        if (w <= 0 || h <= 0) return null;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, w, h);
        d.draw(new Canvas(b));
        return b;
    }

    // ---- the card --------------------------------------------------------------------------------------------

    /**
     * The card: a small live-camera tile in the bottom right corner, the alarm's snapshot filling it (16:9), as a camera
     * app's picture-in-picture would. A red MOTION tag and the time across its top; the camera's name and what Tuya says
     * over a shade at its foot, with a white "OK Live" pill; a scan line that sweeps down the picture, a red rim that
     * breathes, a flash as it lands and a red line along its foot that runs down for the 20 seconds it stays. Without a
     * snapshot, the camera in sonar rings on dark glass.
     */
    private static void show(Context c, String camera, String what, Bitmap pic, PendingIntent tap) {
        // The same alarm again (Tuya updates its notification as the snapshot arrives): shown afresh, picture and all.
        close(false);
        float d = c.getResources().getDisplayMetrics().density;
        int w = Math.round(WIDTH_DP * d);
        int h = Math.round(w * 9f / 16f);
        float corner = CORNER_DP * d;
        List<View> pulses = new ArrayList<>();
        List<android.animation.Animator> loops = new ArrayList<>();

        FrameLayout win = new FrameLayout(c);
        FrameLayout card = new FrameLayout(c);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1E2A2E, 0xFF0B0F12});
        glass.setCornerRadius(corner);
        card.setBackground(glass);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), corner);
            }
        });
        card.setClipToOutline(true);
        card.setElevation(10 * d);

        if (pic != null) {
            ImageView shot = new ImageView(c);
            shot.setScaleType(ImageView.ScaleType.CENTER_CROP);
            shot.setImageBitmap(pic);
            card.addView(shot, new FrameLayout.LayoutParams(-1, -1));
        } else {
            // No snapshot: the camera, faint, in rings that spread out from it like sonar.
            int ring = Math.round(h * 0.62f);
            for (int i = 0; i < 2; i++) {
                View r = new View(c);
                GradientDrawable rg = new GradientDrawable();
                rg.setShape(GradientDrawable.OVAL);
                rg.setStroke(Math.round(2 * d), 0xCCFF3B30);
                r.setBackground(rg);
                card.addView(r, new FrameLayout.LayoutParams(ring, ring, Gravity.CENTER));
                android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofPropertyValuesHolder(r,
                        android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 0.35f, 1.5f),
                        android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.35f, 1.5f),
                        android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 0.9f, 0f));
                a.setDuration(1800);
                a.setStartDelay(i * 900L);
                a.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                loops.add(a);
            }
            ImageView icon = new ImageView(c);
            icon.setImageResource(R.drawable.ic_camera);
            icon.setColorFilter(0xE6FFFFFF);
            int is = Math.round(h * 0.26f);
            FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(is, is, Gravity.CENTER);
            il.bottomMargin = Math.round(10 * d);
            card.addView(icon, il);
        }
        // The scan line: a soft bright band that sweeps down the picture, again and again.
        View scan = new View(c);
        scan.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00FFFFFF, 0x2EFFFFFF, 0x00FFFFFF}));
        int band = Math.round(26 * d);
        card.addView(scan, new FrameLayout.LayoutParams(-1, band, Gravity.TOP));
        android.animation.ObjectAnimator sweep = android.animation.ObjectAnimator.ofFloat(scan, View.TRANSLATION_Y, -band, h);
        sweep.setDuration(2600);
        sweep.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        sweep.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        loops.add(sweep);
        // Shades top and foot, for the words over the picture.
        View top = new View(c);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x8C000000, 0x00000000}));
        card.addView(top, new FrameLayout.LayoutParams(-1, Math.round(54 * d), Gravity.TOP));
        View foot = new View(c);
        foot.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xF0000000, 0x99000000, 0x00000000}));
        card.addView(foot, new FrameLayout.LayoutParams(-1, Math.round(h * 0.58f), Gravity.BOTTOM));

        int pad = Math.round(11 * d);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tl.leftMargin = tl.topMargin = pad;
        card.addView(tag(c, d, pulses), tl);
        TextView time = text(c, android.text.format.DateFormat.getTimeFormat(c).format(new java.util.Date()), 12f, 0xF2FFFFFF,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        time.setShadowLayer(4 * d, 0, d, 0xCC000000);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        ml.rightMargin = pad;
        ml.topMargin = pad + Math.round(2 * d);
        card.addView(time, ml);

        // The foot: name and what Tuya says on the left, "OK Live" on the right.
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(c, camera, 15f, 0xFFFFFFFF, Typeface.create("sans-serif-medium", Typeface.BOLD));
        name.setShadowLayer(6 * d, 0, d, 0xCC000000);
        col.addView(name, new LinearLayout.LayoutParams(-1, -2));
        TextView line = text(c, what, 11.5f, 0xD9FFFFFF, Typeface.DEFAULT);
        line.setShadowLayer(4 * d, 0, d, 0xCC000000);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
        ll.topMargin = Math.round(3 * d);
        col.addView(line, ll);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout pill = new LinearLayout(c);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable pg = new GradientDrawable();
        pg.setColor(0xF2FFFFFF);
        pg.setCornerRadius(20 * d);
        pill.setBackground(pg);
        pill.setPadding(Math.round(4 * d), Math.round(4 * d), Math.round(10 * d), Math.round(4 * d));
        String remote = Config.themeString(c, "ok_button", "");
        ImageView ok = new ImageView(c);
        ok.setImageBitmap(GoalWatchService.okButton(remote.isEmpty() ? "tcl" : remote, d, false));
        int ks = Math.round(18 * d);
        pill.addView(ok, new LinearLayout.LayoutParams(ks, ks));
        TextView live = text(c, "Live", 12f, 0xFF111214, Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-2, -2);
        vl.leftMargin = Math.round(5 * d);
        pill.addView(live, vl);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2);
        pl.leftMargin = Math.round(8 * d);
        row.addView(pill, pl);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        rl.leftMargin = rl.rightMargin = pad;
        rl.bottomMargin = pad + Math.round(2 * d);
        card.addView(row, rl);

        // The time left: a red line along the foot that runs down.
        View left = new View(c);
        left.setBackgroundColor(RED);
        left.setPivotX(0);
        card.addView(left, new FrameLayout.LayoutParams(-1, Math.round(3 * d), Gravity.BOTTOM));
        // The rim: red, breathing, like a camera that is recording.
        View rim = new View(c);
        GradientDrawable rd = new GradientDrawable();
        rd.setCornerRadius(corner);
        rd.setStroke(Math.round(2 * d), RED);
        rim.setBackground(rd);
        card.addView(rim, new FrameLayout.LayoutParams(-1, -1));
        pulses.add(rim);
        // A flash as it lands, like a shutter.
        View flash = new View(c);
        flash.setBackgroundColor(0xFFFFFFFF);
        flash.setAlpha(0.6f);
        card.addView(flash, new FrameLayout.LayoutParams(-1, -1));

        // Room around the card for its shadow.
        int m = Math.round(8 * d);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(w, h);
        cl.leftMargin = cl.topMargin = cl.rightMargin = cl.bottomMargin = m;
        win.addView(card, cl);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w + 2 * m, h + 2 * m,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Focusable, so OK and Back come here while it shows; the app keeps playing underneath.
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.x = Math.round(20 * d);
        lp.y = Math.round(20 * d);
        lp.setTitle("CameraAlert");
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
            // Acted on as the key comes up, and only for a press that began here: a key held as the card appeared
            // belongs to the app.
            if (e.getAction() == KeyEvent.ACTION_DOWN) { down[0] = true; return true; }
            if (e.getAction() != KeyEvent.ACTION_UP || !down[0]) return true;
            boolean open = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
            close(true);
            if (open) live(c, camera, tap);
            return true;
        });

        card.setAlpha(0f);
        card.setTranslationY(h * 0.18f);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(380)
                .setInterpolator(new DecelerateInterpolator(2.2f)).start();
        flash.animate().alpha(0f).setStartDelay(160).setDuration(420).start();
        left.animate().scaleX(0f).setDuration(SHOW_MS).setInterpolator(new LinearInterpolator()).start();
        for (View p : pulses) {
            android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofFloat(p, View.ALPHA, 1f, 0.25f);
            a.setDuration(700);
            a.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            a.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            loops.add(a);
            p.setTag(a);
        }
        for (android.animation.Animator a : loops) a.start();
        win.setTag(loops);
        main.removeCallbacks(close);
        main.postDelayed(close, SHOW_MS);
    }

    /** The red "MOTION" tag, with a dot that breathes. */
    private static View tag(Context c, float d, List<View> pulses) {
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.HORIZONTAL);
        t.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(RED);
        bg.setCornerRadius(4 * d);
        t.setBackground(bg);
        t.setPadding(Math.round(7 * d), Math.round(3 * d), Math.round(8 * d), Math.round(3 * d));
        View dot = new View(c);
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor(0xFFFFFFFF);
        dot.setBackground(dg);
        int ds = Math.round(6 * d);
        t.addView(dot, new LinearLayout.LayoutParams(ds, ds));
        pulses.add(dot);
        TextView word = text(c, "MOTION", 10.5f, 0xFFFFFFFF, Typeface.DEFAULT_BOLD);
        word.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
        wl.leftMargin = Math.round(5 * d);
        t.addView(word, wl);
        return t;
    }

    private static TextView text(Context c, String s, float sp, int colour, Typeface face) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(colour);
        v.setTypeface(face);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setIncludeFontPadding(false);
        return v;
    }

    /** The card away, the keys back with the app: faded out, or at once when another takes its place. */
    private static void close(boolean fade) {
        main.removeCallbacks(close);
        FrameLayout v = host;
        host = null;
        if (v == null) return;
        v.setOnKeyListener(null);
        stopPulses(v);
        Runnable gone = () -> {
            try { v.getContext().getSystemService(WindowManager.class).removeView(v); } catch (Exception ignored) { }
        };
        if (!fade || v.getChildCount() == 0) { gone.run(); return; }
        // Not focusable while it fades, so the next key already goes to the app.
        try {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getLayoutParams();
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            v.getContext().getSystemService(WindowManager.class).updateViewLayout(v, lp);
        } catch (Exception ignored) { }
        v.animate().alpha(0f).translationX(v.getWidth() * 0.08f).setDuration(220).withEndAction(gone).start();
    }

    private static void stopPulses(View v) {
        if (v.getTag() instanceof android.animation.Animator) ((android.animation.Animator) v.getTag()).cancel();
        if (v.getTag() instanceof List) {
            for (Object a : (List<?>) v.getTag()) if (a instanceof android.animation.Animator) ((android.animation.Animator) a).cancel();
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) stopPulses(g.getChildAt(i));
        }
    }

    /**
     * The camera's live view: Tuya Smart opened and the camera picked by the helper; without the helper, the
     * alarm's own tap (Tuya's message for it), or Tuya Smart as it was.
     */
    static void live(Context c, String camera, PendingIntent tap) {
        if (AppSearchService.camera(c, camera)) return;
        if (tap != null) {
            try { tap.send(); return; } catch (PendingIntent.CanceledException ignored) { }
        }
        Intent open = c.getPackageManager().getLaunchIntentForPackage(TUYA);
        if (open == null) return;
        try { c.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
    }
}
