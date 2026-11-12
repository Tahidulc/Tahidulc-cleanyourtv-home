package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The launcher's update on the screen (Updater): a card at the foot of the screen in the Control Centre's navy glass,
 * over whatever is on and never taking the remote, saying what is being installed (4.73 → 4.74), with a bar filling as
 * the new version is put together and installed and a line on what it is doing. The same card says when there is
 * nothing new, when the update is in, or what went wrong.
 */
final class UpdateCard {
    private UpdateCard() { }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static FrameLayout win;
    private static TextView title, versions, line;
    private static ImageView icon;
    private static Bar bar;
    private static ValueAnimator moving;
    private static final Runnable gone = UpdateCard::hide;

    /** The bar: a pale track with the part done filled in white, both with round ends. */
    private static final class Bar extends View {
        float share;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), done = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Bar(Context c) {
            super(c);
            track.setColor(0x33FFFFFF);
            done.setColor(Color.WHITE);
        }

        @Override protected void onDraw(Canvas cv) {
            float h = getHeight(), w = getWidth(), rad = h / 2f;
            r.set(0, 0, w, h);
            cv.drawRoundRect(r, rad, rad, track);
            float s = Math.max(0f, Math.min(1f, share));
            if (s <= 0f) return;
            r.set(0, 0, Math.max(h, w * s), h);
            cv.drawRoundRect(r, rad, rad, done);
        }
    }

    private static int px(Context c, float v) {
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        return Math.round(v * Math.min(dm.widthPixels, dm.heightPixels) / 1080f);
    }

    /** Whether it can be shown over other apps here (the launcher's pop-ups need the same). */
    static boolean can(Context c) {
        return Settings.canDrawOverlays(c);
    }

    /** The card up, saying what is installed now and what it becomes, its bar empty. */
    static void updating(Context c, String from, String to) {
        if (!up(c)) return;
        MAIN.removeCallbacks(gone);
        title.setText("Updating TV Home");
        versions.setText(from + "  →  " + to);
        versions.setVisibility(View.VISIBLE);
        bar.setVisibility(View.VISIBLE);
        icon.setImageResource(R.drawable.ic_update);
        to(0f, 0L);
        line.setText("Getting ready");
    }

    /** How far it has got (0 to 1) and what it is doing, the bar moving there over ms (0: at once), slowing as it goes. */
    static void progress(float share, String what, long ms) {
        if (win == null) return;
        line.setText(what);
        to(share, ms);
    }

    /** A word on its own (nothing new, the update in, what went wrong) for ms; done shows the bar full and a tick. */
    static void say(Context c, String head, String what, boolean done, long ms) {
        if (!up(c)) return;
        MAIN.removeCallbacks(gone);
        title.setText(head);
        line.setText(what);
        versions.setVisibility(View.GONE);
        bar.setVisibility(done ? View.VISIBLE : View.GONE);
        icon.setImageResource(done ? R.drawable.ic_update_done : R.drawable.ic_update);
        if (done) to(1f, 300L);
        MAIN.postDelayed(gone, ms);
    }

    static boolean showing() {
        return win != null;
    }

    private static void to(float share, long ms) {
        if (bar == null) return;
        if (moving != null) moving.cancel();
        if (ms <= 0) {
            bar.share = share;
            bar.invalidate();
            return;
        }
        moving = ValueAnimator.ofFloat(bar.share, share);
        moving.setDuration(ms);
        moving.setInterpolator(new DecelerateInterpolator(ms > 2_000L ? 2.5f : 1.2f));
        Bar b = bar;
        moving.addUpdateListener(a -> {
            b.share = (float) a.getAnimatedValue();
            b.invalidate();
        });
        moving.start();
    }

    /** The card's window, made the first time (false: it cannot be shown here). */
    private static boolean up(Context ctx) {
        if (win != null) return true;
        Context c = ctx.getApplicationContext();
        if (!can(c)) {
            android.util.Log.i(Updater.TAG, "no card: the launcher may not show itself over other apps here");
            return false;
        }
        Typeface heavy = c.getResources().getFont(R.font.outfit_extrabold), semi = c.getResources().getFont(R.font.inter_semibold),
                regular = c.getResources().getFont(R.font.inter_regular);
        FrameLayout frame = new FrameLayout(c);
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xF21A2296, 0xF2121980, 0xF20B0F5A});
        g.setCornerRadius(px(c, 36));
        g.setStroke(Math.max(1, px(c, 1.5f)), 0x40FFFFFF);
        card.setBackground(g);
        card.setPadding(px(c, 34), px(c, 26), px(c, 36), px(c, 28));
        icon = new ImageView(c);
        icon.setColorFilter(Color.WHITE);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(px(c, 54), px(c, 54));
        il.rightMargin = px(c, 28);
        card.addView(icon, il);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        card.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.BOTTOM);
        title = text(c, heavy, 30, Color.WHITE);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        versions = text(c, semi, 20, 0xB3FFFFFF);
        head.addView(versions, new LinearLayout.LayoutParams(-2, -2));
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));
        bar = new Bar(c);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, px(c, 10));
        bl.topMargin = px(c, 16);
        col.addView(bar, bl);
        line = text(c, regular, 19, 0xD9FFFFFF);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
        ll.topMargin = px(c, 12);
        col.addView(line, ll);
        frame.addView(card, new FrameLayout.LayoutParams(-1, -2));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(px(c, 780), -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = px(c, 72);
        lp.setTitle("TV Home update");
        try {
            c.getSystemService(WindowManager.class).addView(frame, lp);
        } catch (RuntimeException e) {
            android.util.Log.i(Updater.TAG, "no card: " + e);
            title = versions = line = null;
            icon = null;
            bar = null;
            return false;
        }
        win = frame;
        card.setAlpha(0f);
        card.setTranslationY(px(c, 40));
        card.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(Fluid.EASE).start();
        return true;
    }

    private static TextView text(Context c, Typeface face, float size, int colour) {
        TextView t = new TextView(c);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(c, size));
        t.setTextColor(colour);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Gone, sliding down. */
    static void hide() {
        FrameLayout w = win;
        if (w == null) return;
        win = null;
        if (moving != null) moving.cancel();
        moving = null;
        View card = w.getChildCount() > 0 ? w.getChildAt(0) : w;
        card.animate().alpha(0f).translationY(card.getHeight() * 0.3f).setDuration(220).withEndAction(() -> {
            try {
                w.getContext().getSystemService(WindowManager.class).removeView(w);
            } catch (RuntimeException ignored) {
                // already gone
            }
        }).start();
        title = versions = line = null;
        icon = null;
        bar = null;
    }
}
