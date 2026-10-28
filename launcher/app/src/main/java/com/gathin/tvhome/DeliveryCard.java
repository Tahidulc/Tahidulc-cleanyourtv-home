package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * The cards the phone's updates become (GoalWatchService shows them): a food order in its app's colours, with the
 * app's logo, the restaurant, a look of its own for each stage and what was ordered; and the car (Tesla), with the
 * Tesla mark, a studio picture of the car and its battery.
 */
final class DeliveryCard {
    static final int INK = 0xFF1A1D24, DIM = 0xA61A1D24;
    static final int MOVE = 0, CALM = 1, STILL = 2;

    private DeliveryCard() {
    }

    /**
     * A card ready to show: its view, and what plays once it is up (the stage bar filling, the battery, the order's
     * items turning up in its place): MOVE animates; CALM (over another app) shows each change at once; STILL (over
     * a playing video) changes nothing after it appears, so the picture under it is never disturbed.
     */
    static final class Card {
        final View view;
        final java.util.function.Consumer<Integer> reveal;
        final int widthDp, heightDp;
        long showMs = 14_000L;
        boolean pairing;
        Card(View view, java.util.function.Consumer<Integer> reveal, int widthDp, int heightDp) {
            this.view = view;
            this.reveal = reveal;
            this.widthDp = widthDp;
            this.heightDp = heightDp;
        }
    }

    // ---- pairing ---------------------------------------------------------------------------------------------------

    /** The code a phone asks to pair with, large, until it is typed in: "📱 Pair HONOR  ·  CODE 9 7 9 6". */
    static Card pairing(Context c, String phone, String code, Drawable glass) {
        float d = c.getResources().getDisplayMetrics().density;
        LinearLayout card = new RevealBox(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(glass);
        card.setPadding(px(d, 18), px(d, 12), px(d, 22), px(d, 12));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView head = text(c, "📱  PAIR " + phone.toUpperCase(Locale.ROOT), 10.5f, DIM, true);
        head.setLetterSpacing(0.1f);
        col.addView(head);
        col.addView(text(c, "Type this code on your phone", 13f, INK, false), margins(d, 4, 0));
        card.addView(col);
        TextView digits = text(c, code.replace("", " ").trim(), 30f, INK, true);
        digits.setLetterSpacing(0.12f);
        card.addView(digits, margins(d, 0, 22));
        Card out = new Card(card, mode -> { }, 470, 90);
        out.showMs = 5 * 60_000L;   // until it is typed in (GoalWatchService closes it then), or it runs out
        out.pairing = true;
        return out;
    }

    // ---- food ------------------------------------------------------------------------------------------------------

    /** Each stage's own look: its sign and headline. */
    private static final String[] STAGE_SIGN = {"🧾", "👨‍🍳", "🛵", "📍", "✅"};
    private static final String[] STAGE_HEAD = {"Order placed", "Being prepared", "On the way", "Almost there", "Delivered"};

    /** The app's brand colour: its own for the apps we know, else the strongest colour in its icon. */
    static int brand(String pkg, Bitmap icon) {
        if (pkg != null) {
            if (pkg.startsWith("com.ubercab.eats")) return 0xFF06C167;
            if (pkg.startsWith("com.justeat")) return 0xFFFF8000;
            if (pkg.startsWith("com.deliveroo")) return 0xFF00CCBC;
        }
        if (icon == null) return 0xFF3A7BFF;
        int best = 0xFF3A7BFF;
        float score = -1;
        float[] hsv = new float[3];
        int step = Math.max(1, icon.getWidth() / 24);
        for (int y = 0; y < icon.getHeight(); y += step) {
            for (int x = 0; x < icon.getWidth(); x += step) {
                int c = icon.getPixel(x, y);
                if (Color.alpha(c) < 200) continue;
                Color.colorToHSV(c, hsv);
                float sc = hsv[1] * Math.min(1f, hsv[2] * 1.4f);
                if (sc > score) { score = sc; best = c | 0xFF000000; }
            }
        }
        return score < 0.25f ? 0xFF1A1D24 : best;
    }

    /**
     * An order's update, about the height of a goal alert: the app's logo, where it is from and the app's own words,
     * then the bar of stages in the app's colour with the stage and when it arrives. What was ordered takes the
     * card's place for a few seconds after.
     */
    static Card food(Context c, JSONObject u, Drawable glass) {
        float d = c.getResources().getDisplayMetrics().density;
        int stage = Math.max(0, Math.min(4, u.optInt("stage", 0)));
        String pkg = u.optString("package", "");
        Bitmap icon = decode(u.optString("icon", ""));
        int brand = brand(pkg, icon);
        String app = u.optString("app", "Delivery"), from = u.optString("from", "").trim();
        String status = u.optString("status", "").trim(), eta = u.optString("eta", "").trim();

        LinearLayout card = pill(c, d, glass);
        card.addView(logo(c, d, icon, brand), new LinearLayout.LayoutParams(px(d, 34), px(d, 34)));

        // The update: "Nando's  ·  Nando's is preparing your order", then the stages.
        LinearLayout news = new LinearLayout(c);
        news.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = row(c);
        TextView where = text(c, from.isEmpty() ? app : from, 13f, INK, true);
        where.setMaxWidth(px(d, 200));
        top.addView(where);
        String words = status.isEmpty() || status.equalsIgnoreCase(from) ? STAGE_HEAD[stage] : status;
        TextView said = text(c, "·  " + words, 12f, DIM, false);
        said.setMaxWidth(px(d, 280));
        top.addView(said, margins(d, 0, 6));
        news.addView(top, margins(d, 0, 0));
        LinearLayout bottom = row(c);
        StageBar bar = new StageBar(c, brand, stage);
        bottom.addView(bar, new LinearLayout.LayoutParams(px(d, 120), px(d, 11)));
        String tag = STAGE_SIGN[stage] + " " + STAGE_HEAD[stage].toUpperCase(Locale.ROOT)
                + (eta.isEmpty() || stage == 4 ? "" : "  ·  ARRIVING " + eta.toUpperCase(Locale.ROOT));
        TextView stageTag = text(c, tag, 9.5f, readable(brand), true);
        stageTag.setLetterSpacing(0.06f);
        bottom.addView(stageTag, margins(d, 0, 8));
        news.addView(bottom, margins(d, 3, 0));

        // What was ordered, when the phone saw it: "2× ¼ Chicken  ·  1× Spicy Rice  ·  …".
        JSONArray items = u.optJSONArray("items");
        LinearLayout order = null;
        if (items != null && items.length() > 0) {
            order = new LinearLayout(c);
            order.setOrientation(LinearLayout.VERTICAL);
            StringBuilder list = new StringBuilder();
            for (int i = 0; i < items.length(); i++) list.append(i == 0 ? "" : "   ·   ").append(items.optString(i));
            TextView line = text(c, list, 12.5f, INK, false);
            line.setMaxWidth(px(d, 470));
            order.addView(line, margins(d, 0, 0));   // its own width, not the line under it
            String head = "🧾 YOUR ORDER  ·  " + items.length() + (items.length() == 1 ? " ITEM" : " ITEMS")
                    + (from.isEmpty() ? "" : "  ·  " + from.toUpperCase(Locale.ROOT));
            TextView sub = text(c, head, 9.5f, readable(brand), true);
            sub.setLetterSpacing(0.06f);
            order.addView(sub, margins(d, 3, 0));
        }
        card.addView(news, margins(d, 0, 10));
        LinearLayout orderPage = order;
        java.util.function.Consumer<Integer> reveal = mode -> {
            bar.fill(mode != MOVE);
            if (orderPage != null && mode != STILL) flip(card, news, orderPage, mode == CALM, 6_000L, px(d, 10));
        };
        return new Card(card, reveal, 620, 64);
    }

    /**
     * After a while, one face of a card gives way to the other in its place (the card taking the new face's width):
     * the old rolls up and out, the new rolls up in; at once when the card keeps still.
     */
    private static void flip(LinearLayout card, View from, View to, boolean calm, long after, int left) {
        card.postDelayed(() -> {
            if (from.getParent() != card) return;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = left;
            to.setVisibility(View.VISIBLE);
            if (calm) {
                int at = card.indexOfChild(from);
                card.removeView(from);
                card.addView(to, at, lp);
                return;
            }
            float h = from.getHeight();
            from.animate().alpha(0f).translationY(-h * 0.6f).setDuration(260).withEndAction(() -> {
                int at = card.indexOfChild(from);
                card.removeView(from);
                to.setAlpha(0f);
                to.setTranslationY(h * 0.6f);
                card.addView(to, at, lp);
                to.animate().alpha(1f).translationY(0f).setDuration(300).start();
            }).start();
        }, after);
    }

    /** The bar of five stages: a dot each, joined, filled up to the stage in the brand colour. */
    private static final class StageBar extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int colour, stage;
        private float progress;

        StageBar(Context c, int colour, int stage) {
            super(c);
            this.colour = colour;
            this.stage = stage;
            progress = Math.max(0, stage - 1);   // it fills from the last stage to this one
        }

        void fill(boolean calm) {
            if (calm) { progress = stage; invalidate(); return; }
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(progress, stage);
            a.setDuration(900);
            a.setStartDelay(200);
            a.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
            a.addUpdateListener(v -> { progress = (float) v.getAnimatedValue(); invalidate(); });
            a.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            float h = getHeight(), w = getWidth(), cy = h / 2f, r = h * 0.22f, x0 = h / 2f, x1 = w - h / 2f;
            paint.setColor(0x4D1A1D24);
            canvas.drawRoundRect(new RectF(x0, cy - r, x1, cy + r), r, r, paint);
            float to = x0 + (x1 - x0) * progress / 4f;
            paint.setColor(colour);
            canvas.drawRoundRect(new RectF(x0, cy - r, Math.max(x0 + 2 * r, to), cy + r), r, r, paint);
            for (int i = 0; i <= 4; i++) {
                float x = x0 + (x1 - x0) * i / 4f;
                boolean reached = i <= progress + 0.01f;
                paint.setColor(reached ? colour : 0x801A1D24);
                float dot = i == stage ? h / 2f - 0.5f : h * 0.26f;
                canvas.drawCircle(x, cy, dot, paint);
                if (i == stage) {
                    paint.setColor(Color.WHITE);
                    canvas.drawCircle(x, cy, dot * 0.42f, paint);
                }
            }
        }
    }

    // ---- the car ---------------------------------------------------------------------------------------------------

    /** The Tesla app's own colours: its near-black, white, the grey of its details, its green and blue. */
    static final int TESLA_BG = 0xFF151515, TESLA_TEXT = 0xFFFFFFFF, TESLA_GREY = 0xFF9A9A9A, TESLA_TRACK = 0xFF2E2E2E,
            TESLA_GREEN = 0xFF31C553, TESLA_BLUE = 0xFF3E6AE1;

    /** A pill in the Tesla app's style: its near-black, a faint rim, the pills' round ends. */
    private static LinearLayout teslaPill(Context c, float d) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(TESLA_BG);
        bg.setCornerRadius(px(d, 24));
        bg.setStroke(Math.max(1, Math.round(d)), 0x26FFFFFF);
        LinearLayout card = pill(c, d, bg);
        card.setPadding(px(d, 7), px(d, 4), px(d, 14), px(d, 4));
        card.setMinimumHeight(px(d, 36));
        return card;
    }

    /** The car's picture, then its name ("Batmobile") with the battery and miles beside it, as the app's header has. */
    private static LinearLayout teslaHead(Context c, float d, LinearLayout card, int battery, int miles, boolean estimated) {
        Bitmap art = carArt(c);
        ImageView car = new ImageView(c);
        car.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (art != null) car.setImageBitmap(art);
        else car.setImageResource(R.drawable.tesla_t);
        card.addView(car, new LinearLayout.LayoutParams(art != null ? px(d, 50) : px(d, 18), px(d, art != null ? 28 : 18)));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = row(c);
        // The Tesla "T" in its red, leading the car's name, as the brand's mark.
        ImageView t = new ImageView(c);
        t.setImageResource(R.drawable.tesla_t);
        head.addView(t, new LinearLayout.LayoutParams(px(d, 10), px(d, 10)));
        String name = Config.themeString(c, "tesla_name", Config.themeString(c, "tesla_model", "Model X"));
        head.addView(text(c, name, 11.5f, TESLA_TEXT, true), margins(d, 0, 5));
        if (battery >= 0) {
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(px(d, 15), px(d, 7));
            clp.leftMargin = px(d, 8);
            head.addView(new Cell(c, battery), clp);
            head.addView(text(c, (estimated ? "≈ " : "") + miles + " mi", 10f, TESLA_GREY, true), margins(d, 0, 4));
        }
        col.addView(head, margins(d, 0, 0));
        card.addView(col, margins(d, 0, 7));
        return col;
    }

    /**
     * The car's news in the Tesla app's style, the size of the other pills: the car, its name with the battery and
     * miles, what happened, and the charge in the app's green, filling up towards the limit while it charges.
     */
    static Card car(Context c, JSONObject u, Drawable glass) {
        float d = c.getResources().getDisplayMetrics().density;
        String title = u.optString("title", "").trim(), status = u.optString("status", "").trim();
        int battery = u.optInt("battery", -1), from = u.optInt("from", -1), target = u.optInt("target", -1);
        int miles = u.optInt("miles", -1);
        boolean estimated = miles < 0;
        if (battery >= 0 && miles < 0) {
            int full = parseInt(Config.themeString(c, "tesla_range_miles", "330"), 330);
            miles = Math.round(full * battery / 100f);
        }
        String all = (title + " " + status).toLowerCase(Locale.ROOT);
        boolean charging = all.contains("charging") && !all.contains("complete") && !all.contains("stopped")
                && !all.contains("interrupted") && !all.contains("finished");

        LinearLayout card = teslaPill(c, d);
        LinearLayout col = teslaHead(c, d, card, battery, miles, estimated);
        // Two slim lines: "T Batmobile ▭ ≈ 201 mi · Charging", then the charge and the details.
        LinearLayout head = (LinearLayout) col.getChildAt(0);
        TextView what = text(c, "·  " + (title.isEmpty() ? "Tesla" : title), 11f, TESLA_TEXT, false);
        what.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        what.setMaxWidth(px(d, 200));
        head.addView(what, margins(d, 0, 6));
        LinearLayout second = row(c);
        java.util.function.Consumer<Integer> reveal = mode -> { };
        if (battery >= 0) {
            Battery b = new Battery(c, battery, from >= 0 && from < battery ? from : charging ? Math.max(0, battery - 10) : 0,
                    target, charging);
            second.addView(b, new LinearLayout.LayoutParams(px(d, 100), px(d, 6)));
            TextView l = text(c, battery + "%" + (charging && target > battery ? " · limit " + target + "%" : ""), 9.5f,
                    charging ? TESLA_GREEN : TESLA_GREY, true);
            second.addView(l, margins(d, 0, 7));
            reveal = mode -> b.fill(mode != MOVE);
        }
        if (!status.isEmpty() && !status.equalsIgnoreCase(title)) {
            TextView st = text(c, (battery >= 0 ? "·  " : "") + status, 9.5f, TESLA_GREY, false);
            st.setMaxWidth(px(d, 260));
            second.addView(st, margins(d, 0, battery >= 0 ? 6 : 0));
        }
        if (second.getChildCount() > 0) col.addView(second, margins(d, 3, 0));
        return new Card(card, reveal, 600, 56);
    }

    /** The app's small battery: a grey cell filled to its level. */
    private static final class Cell extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int level;

        Cell(Context c, int level) {
            super(c);
            this.level = Math.max(0, Math.min(100, level));
        }

        @Override protected void onDraw(Canvas canvas) {
            float h = getHeight(), w = getWidth(), tip = h * 0.22f, body = w - tip - 1, r = h * 0.28f;
            paint.setColor(0xFF4A4A4A);
            canvas.drawRoundRect(new RectF(0, 0, body, h), r, r, paint);
            canvas.drawRoundRect(new RectF(w - tip, h * 0.3f, w, h * 0.7f), tip / 2, tip / 2, paint);
            paint.setColor(level < 20 ? 0xFFFF453A : 0xFFB0B0B0);
            float in = h * 0.18f;
            canvas.drawRoundRect(new RectF(in, in, in + Math.max(r, (body - 2 * in) * level / 100f), h - in), r * 0.6f, r * 0.6f, paint);
        }
    }

    /**
     * On the way home in the car (the phone's own estimate from where it is), in the Tesla app's style: the car,
     * "Heading home", the minutes and miles left, and a bar of the trip so far in the app's blue.
     */
    static Card homeEta(Context c, JSONObject u, Drawable glass) {
        float d = c.getResources().getDisplayMetrics().density;
        int minutes = u.optInt("minutes", -1);
        double miles = u.optDouble("miles", -1);
        float done = (float) Math.max(0, Math.min(1, u.optDouble("progress", 0)));
        boolean arriving = u.optBoolean("arriving");

        LinearLayout card = teslaPill(c, d);
        LinearLayout col = teslaHead(c, d, card, -1, 0, false);
        // Two slim lines: "T Batmobile · Heading home", then the trip, the minutes and miles, and when.
        LinearLayout head = (LinearLayout) col.getChildAt(0);
        TextView what = text(c, "·  " + (arriving ? "Almost home" : "Heading home"), 11f, TESLA_TEXT, false);
        what.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        head.addView(what, margins(d, 0, 6));
        LinearLayout trip = row(c);
        Trip bar = new Trip(c, done);
        trip.addView(bar, new LinearLayout.LayoutParams(px(d, 100), px(d, 8)));
        String left = arriving ? "pulling in now"
                : (minutes >= 0 ? minutes + " min" : "") + (miles >= 0 ? (minutes >= 0 ? " · " : "")
                + String.format(Locale.UK, miles < 10 ? "%.1f mi" : "%.0f mi", miles) : "");
        if (minutes >= 0 && !arriving) left += " · home by " + java.time.LocalTime.now().plusMinutes(minutes)
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        if (!left.isEmpty()) trip.addView(text(c, left, 9.5f, TESLA_GREY, true), margins(d, 0, 7));
        col.addView(trip, margins(d, 3, 0));
        return new Card(card, mode -> bar.fill(mode != MOVE), 600, 56);
    }

    /** The trip as a bar: a slim track, filled in Tesla red as far as the car has come, with a dot for the car. */
    private static final class Trip extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float done;
        private float shown;

        Trip(Context c, float done) {
            super(c);
            this.done = done;
            shown = Math.max(0, done - 0.1f);
        }

        void fill(boolean calm) {
            if (calm) { shown = done; invalidate(); return; }
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(shown, done);
            a.setDuration(900);
            a.addUpdateListener(v -> { shown = (float) v.getAnimatedValue(); invalidate(); });
            a.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            float h = getHeight(), w = getWidth(), cy = h / 2f, r = h * 0.2f, x0 = h / 2f, x1 = w - h / 2f;
            paint.setColor(TESLA_TRACK);
            canvas.drawRoundRect(new RectF(x0, cy - r, x1, cy + r), r, r, paint);
            float x = x0 + (x1 - x0) * shown;
            paint.setColor(TESLA_BLUE);
            canvas.drawRoundRect(new RectF(x0, cy - r, Math.max(x0 + 2 * r, x), cy + r), r, r, paint);
            canvas.drawCircle(x, cy, h / 2f - 0.5f, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, cy, h * 0.2f, paint);
            paint.setColor(0xFF8A8A8A);
            canvas.drawCircle(x1, cy, h * 0.3f, paint);   // home
        }
    }

    /**
     * The charge, as the Tesla app draws it: a slim dark track filling in its green (red under 20%); while it
     * charges it fills up from where it was, and a mark shows the charge limit.
     */
    private static final class Battery extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int level, from, target;
        private final boolean charging;
        private float shown;

        Battery(Context c, int level, int from, int target, boolean charging) {
            super(c);
            this.level = Math.max(0, Math.min(100, level));
            this.from = Math.max(0, Math.min(this.level, from));
            this.target = target;
            this.charging = charging;
            shown = this.from;
        }

        void fill(boolean calm) {
            if (calm) { shown = level; invalidate(); return; }
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(from, level);
            a.setDuration(charging ? 1600 : 1000);
            a.setStartDelay(200);
            a.setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f));
            a.addUpdateListener(v -> { shown = (float) v.getAnimatedValue(); invalidate(); });
            a.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            // The app's charge bar: a slim dark track filled in its green (red when low), a mark at the limit.
            float h = getHeight(), w = getWidth(), r = h / 2f;
            paint.setColor(TESLA_TRACK);
            canvas.drawRoundRect(new RectF(0, 0, w, h), r, r, paint);
            paint.setColor(level < 20 ? 0xFFE82127 : TESLA_GREEN);
            float x = Math.max(h, w * shown / 100f);
            if (shown > 0) canvas.drawRoundRect(new RectF(0, 0, x, h), r, r, paint);
            if (charging && target > 0 && target <= 100) {
                paint.setColor(0xFF6A6A6A);
                float tx = w * target / 100f;
                canvas.drawRect(tx - 1, 0, tx + 1, h, paint);
            }
        }
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    /** A card the shape of the alerts' pills: a rounded glass line, its parts centred. */
    private static LinearLayout pill(Context c, float d, Drawable glass) {
        LinearLayout card = new RevealBox(c);   // opens and closes as the goal alerts do
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(glass);
        card.setPadding(px(d, 8), px(d, 7), px(d, 18), px(d, 7));
        card.setMinimumHeight(px(d, 48));
        return card;
    }

    private static ImageView logo(Context c, float d, Bitmap icon, int brand) {
        ImageView logo = new ImageView(c);
        if (icon != null) logo.setImageBitmap(round(icon, 0.26f));
        else logo.setBackground(roundRect(brand, px(d, 9)));
        return logo;
    }

    private static LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    // ---- the car's picture -----------------------------------------------------------------------------------------

    private static Bitmap carArt;

    /**
     * The car's picture, cut out on its own like the Tesla app's: a Model X ("Tesla Model X 100D Solid Black (1)" by
     * Damian B Oh, CC BY-SA 4.0, Wikimedia Commons; background removed). Tesla's own renders refuse the TV. Other
     * models (a theme's "tesla_model") get the Tesla mark instead.
     */
    static Bitmap carArt(Context c) {
        if (carArt != null) return carArt;
        // The owner's own car, as the Tesla app draws it (tesla_car.png beside the theme on this TV, never in the repo).
        java.io.File own = Config.dir(c) == null ? null : new java.io.File(Config.dir(c), "tesla_car.png");
        if (own != null && own.length() > 0) {
            carArt = BitmapFactory.decodeFile(own.getPath());
            if (carArt != null) return carArt;
        }
        if (!Config.themeString(c, "tesla_model", "Model X").toLowerCase(Locale.ROOT).contains("x")) return null;
        carArt = trim(BitmapFactory.decodeResource(c.getResources(), R.drawable.car_model_x));
        return carArt;
    }

    /** The picture cut to the car itself: the empty, transparent margins around it removed. */
    private static Bitmap trim(Bitmap b) {
        if (b == null) return null;
        int w = b.getWidth(), h = b.getHeight(), left = w, right = -1, top = h, bottom = -1;
        int[] row = new int[w];
        for (int y = 0; y < h; y += 2) {
            b.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x += 2) {
                if ((row[x] >>> 24) > 16) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }
        if (right <= left || bottom <= top) return b;
        Bitmap out = Bitmap.createBitmap(b, left, top, right - left + 1, bottom - top + 1);
        float scale = Math.min(1f, 600f / out.getWidth());
        return scale < 1f ? Bitmap.createScaledBitmap(out, Math.round(out.getWidth() * scale), Math.round(out.getHeight() * scale), true) : out;
    }

    // ---- small helpers ---------------------------------------------------------------------------------------------

    static Bitmap decode(String base64) {
        try {
            byte[] png = Base64.decode(base64, Base64.DEFAULT);
            return png.length > 0 ? BitmapFactory.decodeByteArray(png, 0, png.length) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Bitmap round(Bitmap b, float corner) {
        int s = Math.min(b.getWidth(), b.getHeight());
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.BitmapShader(b, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(0, 0, s, s), s * corner, s * corner, p);
        return out;
    }

    private static GradientDrawable roundRect(int colour, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(colour);
        g.setCornerRadius(radius);
        return g;
    }

    /** The brand colour dark enough to read on the light glass (Uber's green and Just Eat's orange deepened). */
    private static int readable(int colour) {
        float[] hsv = new float[3];
        Color.colorToHSV(colour, hsv);
        hsv[2] = Math.min(hsv[2], 0.62f);
        return Color.HSVToColor(hsv);
    }

    private static TextView text(Context c, CharSequence s, float sp, int colour, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(colour);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return t;
    }

    private static LinearLayout.LayoutParams margins(float d, int top, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(d, top);
        lp.leftMargin = px(d, left);
        return lp;
    }

    private static int px(float d, float dp) {
        return Math.round(dp * d);
    }
}
