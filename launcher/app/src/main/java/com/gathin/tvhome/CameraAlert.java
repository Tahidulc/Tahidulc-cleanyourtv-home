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
    /** The card's width; a snapshot fills it at 16:9, over the card's words. */
    private static final int WIDTH_DP = 360, WORDS_DP = 96;
    private static final float CORNER_DP = 12f;
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

    private static void show(Context c, String camera, String what, Bitmap pic, PendingIntent tap) {
        // The same alarm again (Tuya updates its notification as the snapshot arrives): shown afresh, picture and all.
        close(false);
        float d = c.getResources().getDisplayMetrics().density;
        int w = Math.round(WIDTH_DP * d);
        int picH = pic == null ? 0 : Math.round(w * 9f / 16f);
        int h = picH + Math.round(WORDS_DP * d);
        float corner = CORNER_DP * d;

        FrameLayout win = new FrameLayout(c);
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xF52A2C31, 0xF5141518});
        glass.setCornerRadius(corner);
        glass.setStroke(Math.max(1, Math.round(d)), 0x33FFFFFF);
        card.setBackground(glass);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), corner);
            }
        });
        card.setClipToOutline(true);

        List<View> pulses = new ArrayList<>();
        if (pic != null) {
            FrameLayout top = new FrameLayout(c);
            ImageView shot = new ImageView(c);
            shot.setScaleType(ImageView.ScaleType.CENTER_CROP);
            shot.setImageBitmap(pic);
            top.addView(shot, new FrameLayout.LayoutParams(-1, -1));
            // A shade at the picture's foot, so it meets the card's words softly.
            View shade = new View(c);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x66000000}));
            top.addView(shade, new FrameLayout.LayoutParams(-1, Math.round(48 * d), Gravity.BOTTOM));
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
            tl.leftMargin = tl.topMargin = Math.round(12 * d);
            top.addView(tag(c, d, pulses), tl);
            card.addView(top, new LinearLayout.LayoutParams(-1, picH));
        }

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(16 * d);
        row.setPadding(pad, 0, pad, 0);
        if (pic == null) {
            // No snapshot: the camera in a red disc that breathes, where the picture would be.
            FrameLayout disc = new FrameLayout(c);
            View ring = new View(c);
            GradientDrawable rg = new GradientDrawable();
            rg.setShape(GradientDrawable.OVAL);
            rg.setColor(0x40FF3B30);
            ring.setBackground(rg);
            disc.addView(ring, new FrameLayout.LayoutParams(-1, -1));
            pulses.add(ring);
            ImageView icon = new ImageView(c);
            icon.setImageResource(R.drawable.ic_camera);
            icon.setColorFilter(0xFFFFFFFF);
            GradientDrawable fill = new GradientDrawable();
            fill.setShape(GradientDrawable.OVAL);
            fill.setColor(RED);
            icon.setBackground(fill);
            int ip = Math.round(10 * d);
            icon.setPadding(ip, ip, ip, ip);
            int in = Math.round(46 * d);
            disc.addView(icon, new FrameLayout.LayoutParams(in, in, Gravity.CENTER));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Math.round(60 * d), Math.round(60 * d));
            dl.rightMargin = Math.round(14 * d);
            row.addView(disc, dl);
        }
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        if (pic == null) {
            LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, -2);
            gl.rightMargin = Math.round(8 * d);
            head.addView(tag(c, d, pulses), gl);
        }
        TextView name = text(c, camera, 17f, 0xFFFFFFFF, Typeface.create("sans-serif-medium", Typeface.BOLD));
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView time = text(c, android.text.format.DateFormat.getTimeFormat(c).format(new java.util.Date()), 13f, 0x99FFFFFF, Typeface.DEFAULT);
        head.addView(time, new LinearLayout.LayoutParams(-2, -2));
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));
        TextView line = text(c, what, 13.5f, 0xCCFFFFFF, Typeface.DEFAULT);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
        ll.topMargin = Math.round(2 * d);
        col.addView(line, ll);
        // The remote's own OK button drawn small: "[OK] View live", then "Back Close".
        LinearLayout hint = new LinearLayout(c);
        hint.setOrientation(LinearLayout.HORIZONTAL);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        String remote = Config.themeString(c, "ok_button", "");
        ImageView ok = new ImageView(c);
        ok.setImageBitmap(GoalWatchService.okButton(remote.isEmpty() ? "tcl" : remote, d, true));
        int ks = Math.round(20 * d);
        hint.addView(ok, new LinearLayout.LayoutParams(ks, ks));
        TextView live = text(c, "View live", 12.5f, 0xFFFFFFFF, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-2, -2);
        vl.leftMargin = Math.round(7 * d);
        hint.addView(live, vl);
        // "Back" as a key, outlined, then what it does.
        TextView back = text(c, "Back", 10.5f, 0xCCFFFFFF, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable key = new GradientDrawable();
        key.setCornerRadius(5 * d);
        key.setStroke(Math.max(1, Math.round(d)), 0x66FFFFFF);
        back.setBackground(key);
        back.setPadding(Math.round(7 * d), Math.round(3 * d), Math.round(7 * d), Math.round(3 * d));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.leftMargin = Math.round(18 * d);
        hint.addView(back, bl);
        TextView shut = text(c, "Close", 12.5f, 0x99FFFFFF, Typeface.DEFAULT);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.leftMargin = Math.round(7 * d);
        hint.addView(shut, sl);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = Math.round(8 * d);
        col.addView(hint, hl);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));
        win.addView(card, new FrameLayout.LayoutParams(-1, -1));

        // The time left, as a red line along the card's foot that runs down.
        View left = new View(c);
        left.setBackgroundColor(RED);
        left.setPivotX(0);
        win.addView(left, new FrameLayout.LayoutParams(-1, Math.round(3 * d), Gravity.BOTTOM));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Focusable, so OK and Back come here while it shows; the app keeps playing underneath.
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.x = Math.round(32 * d);
        lp.y = Math.round(32 * d);
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
        card.setTranslationX(w * 0.12f);
        card.animate().alpha(1f).translationX(0f).setDuration(320).setInterpolator(new DecelerateInterpolator(2f)).start();
        left.animate().scaleX(0f).setDuration(SHOW_MS).setInterpolator(new LinearInterpolator()).start();
        for (View p : pulses) {
            android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofFloat(p, View.ALPHA, 1f, 0.25f);
            a.setDuration(700);
            a.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            a.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            a.start();
            p.setTag(a);
        }
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
