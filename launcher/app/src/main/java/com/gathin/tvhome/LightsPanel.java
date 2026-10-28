package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * The lights button's panel, on the same silver as the top bar: for each room (config.xml lights_rooms, or a theme's
 * "lights_rooms") On, Off and a brightness slider (Left/Right in steps of 10%), each sent to the TV's Google Assistant
 * as a typed request ("set the living room lights to 60%"), the same as saying it. Lights the Assistant controls
 * through Google Home work this way even when Google TV's own Home panel does not list them (Tuya / Smart Life).
 * The last button opens that panel.
 */
final class LightsPanel {
    private static final String ASSISTANT = "com.google.android.katniss";
    private static final String TRAMPOLINE = "com.google.android.apps.tvsearch.app.launch.trampoline.SearchActivityTrampoline";
    private static final int INK = 0xFF1A1D24;

    private LightsPanel() { }

    static void show(Context ctx, View anchor, List<String> rooms, Intent smartHome) {
        float d = ctx.getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(silver(d));
        int pad = Math.round(20 * d);
        panel.setPadding(pad, Math.round(16 * d), pad, Math.round(18 * d));
        PopupWindow popup = new PopupWindow(panel, Math.round(380 * d), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(0));
        popup.setElevation(16 * d);
        View first = null;
        for (String room : rooms) {
            // Header: the bulb and the room.
            LinearLayout head = new LinearLayout(ctx);
            head.setGravity(Gravity.CENTER_VERTICAL);
            ImageView bulb = new ImageView(ctx);
            bulb.setImageResource(R.drawable.ic_bulb);
            bulb.setColorFilter(0xFFF5B800);
            head.addView(bulb, new LinearLayout.LayoutParams(Math.round(22 * d), Math.round(22 * d)));
            TextView name = text(ctx, capitalise(room) + " lights", 16f, INK, true);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.leftMargin = Math.round(10 * d);
            head.addView(name, nlp);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hlp.topMargin = first == null ? 0 : Math.round(18 * d);
            panel.addView(head, hlp);

            // On and Off, side by side across the panel.
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView on = button(ctx, "On", d);
            on.setOnClickListener(v -> send(ctx, panel, v, "turn on the " + room + " lights"));
            TextView off = button(ctx, "Off", d);
            off.setOnClickListener(v -> send(ctx, panel, v, "turn off the " + room + " lights"));
            LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, Math.round(44 * d), 1f);
            row.addView(on, half);
            LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, Math.round(44 * d), 1f);
            half2.leftMargin = Math.round(12 * d);
            row.addView(off, half2);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = Math.round(14 * d);
            panel.addView(row, rlp);

            // Brightness: the label and value, then the slider.
            LinearLayout label = new LinearLayout(ctx);
            label.setGravity(Gravity.CENTER_VERTICAL);
            TextView bl = text(ctx, "Brightness", 13f, 0xB31A1D24, false);
            label.addView(bl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView value = text(ctx, "50%", 14f, INK, true);
            label.addView(value);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = Math.round(16 * d);
            panel.addView(label, llp);
            Slider slider = new Slider(ctx, d, 50, level -> value.setText(level + "%"),
                    level -> send(ctx, panel, null, String.format(Locale.ROOT, "set the %s lights to %d%%", room, level)));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(36 * d));
            slp.topMargin = Math.round(6 * d);
            panel.addView(slider, slp);
            if (first == null) first = on;
        }
        if (smartHome != null) {
            TextView home = button(ctx, "Open Google Home", d);
            home.setOnClickListener(v -> {
                popup.dismiss();
                try { ctx.startActivity(smartHome); } catch (Exception ignored) { }
            });
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(40 * d));
            hlp.topMargin = Math.round(18 * d);
            panel.addView(home, hlp);
            if (first == null) first = home;
        }
        popup.showAsDropDown(anchor, -Math.round(300 * d), Math.round(12 * d), Gravity.START);
        if (first != null) first.requestFocus();
    }

    /**
     * The request, with a theme's "quiet_assistant": done out of sight behind a still of the screen and a spinner on
     * the button (AppSearchService.quietAsk), so Google's own screen never shows; otherwise asked the plain way.
     */
    private static void send(Context ctx, View panel, View pressed, String query) {
        if (Config.themeFlag(ctx, "quiet_assistant", false) && ctx instanceof android.app.Activity
                && AppSearchService.quietAsk((android.app.Activity) ctx, panel, pressed, query, () -> ask(ctx, query))) return;
        ask(ctx, query);
    }

    /** Hands the request to Google Assistant as text, the way a spoken one arrives. */
    static void ask(Context ctx, String query) {
        Intent i = new Intent("android.search.action.GLOBAL_SEARCH").setClassName(ASSISTANT, TRAMPOLINE)
                .putExtra("query", query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { ctx.startActivity(i); } catch (Exception ignored) { }
    }

    /** The panel's silver: brushed light metal, brighter at the top, with a white rim. */
    private static LayerDrawable silver(float d) {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFBFCFE, 0xFFE6EAEF, 0xFFD3D9E1});
        base.setCornerRadius(22 * d);
        GradientDrawable rim = new GradientDrawable();
        rim.setCornerRadius(22 * d);
        rim.setStroke(Math.max(1, Math.round(1.5f * d)), 0xFFFFFFFF);
        return new LayerDrawable(new android.graphics.drawable.Drawable[]{base, rim});
    }

    /** A white key on the silver: dark when focused, so the focus is plain to see. */
    private static TextView button(Context ctx, String label, float d) {
        TextView b = text(ctx, label, 15f, INK, true);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(keyBackground(d));
        b.setTextColor(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{Color.WHITE, INK}));
        return b;
    }

    private static android.graphics.drawable.StateListDrawable keyBackground(float d) {
        GradientDrawable idle = new GradientDrawable();
        idle.setColor(0xFFFFFFFF);
        idle.setCornerRadius(14 * d);
        idle.setStroke(Math.max(1, Math.round(1 * d)), 0x331A1D24);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(INK);
        focused.setCornerRadius(14 * d);
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, idle);
        return s;
    }

    private static TextView text(Context ctx, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(ctx.getResources().getFont(bold ? R.font.outfit_medium : R.font.outfit_regular));
        t.setSingleLine(true);
        return t;
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    interface Level { void on(int level); }

    /**
     * A brightness slider for the remote: Left and Right move it 10% at a time (10% at the bottom, 100% at the top),
     * the value shows as it moves, and the request goes out once it has stood still for a moment, so walking from 20%
     * to 80% sends one request, not six.
     */
    private static final class Slider extends View {
        private static final long SETTLE_MS = 900L;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private final Level onMove, onSettle;
        private final Handler main = new Handler(Looper.getMainLooper());
        private int level;
        private final Runnable send = () -> onSettleNow();

        Slider(Context c, float d, int start, Level onMove, Level onSettle) {
            super(c);
            this.d = d;
            this.level = start;
            this.onMove = onMove;
            this.onSettle = onSettle;
            setFocusable(true);
        }

        private void onSettleNow() {
            onSettle.on(level);
        }

        @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int next = Math.max(10, Math.min(100, level + (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 10 : -10)));
                if (next != level) {
                    level = next;
                    onMove.on(level);
                    invalidate();
                    main.removeCallbacks(send);
                    main.postDelayed(send, SETTLE_MS);
                }
                return true;   // never leaves the slider sideways
            }
            if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) && event.getRepeatCount() == 0) {
                main.removeCallbacks(send);
                onSettleNow();
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        @Override protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previous) {
            super.onFocusChanged(focused, direction, previous);
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float knob = (isFocused() ? 13f : 11f) * d;
            float trackH = 8 * d, left = knob, right = w - knob, cy = h / 2f;
            float x = left + (right - left) * (level - 10) / 90f;
            // The track, the filled part up to the knob, the knob.
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x331A1D24);
            c.drawRoundRect(new RectF(left, cy - trackH / 2, right, cy + trackH / 2), trackH / 2, trackH / 2, paint);
            paint.setColor(0xFFF5B800);
            c.drawRoundRect(new RectF(left, cy - trackH / 2, x, cy + trackH / 2), trackH / 2, trackH / 2, paint);
            paint.setColor(0x401A1D24);
            c.drawCircle(x, cy + d, knob, paint);
            paint.setColor(Color.WHITE);
            c.drawCircle(x, cy, knob, paint);
            if (isFocused()) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f * d);
                paint.setColor(INK);
                c.drawCircle(x, cy, knob, paint);
            }
        }
    }
}
