package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Surprise me's spin, for TV shows and Movies alike: the five cards thrown like dice. The old ones are swept away, the
 * new ones come tumbling in from the left, and each keeps tumbling a quarter turn at a time (now on its side, now end
 * over end), a hop with each turn and a new face each time it is edge on: a poster, or a die's face with its spots.
 * They roll on for as long as the pick takes to find, then slow, the outer ones landing first and the middle one last,
 * on the pick, with a squash as it lands, the ring popping round it and a few sparks. A shadow under the middle card
 * shrinks as it hops. Landed, the pick is held up to the light (a 3D tilt card with a moving glow): it leans slowly
 * this way and that, as if a hand passed over it, a soft light gliding across it, the ring leaning with it.
 */
final class DiceRoll {
    private final HomeV2 h;
    private final FrameLayout stage;
    private final ImageView[] cards;
    private final View ring, shadow;
    /** One design pixel (the layout's 1920 wide) on this screen. */
    private final float unit;
    private final float[] baseAlpha = new float[5], baseRot = new float[5];
    /** Turns each card still has to make once the pick is known (-1: rolling on until it is). */
    private final int[] left = new int[5];
    private final List<Animator> running = new ArrayList<>();
    private final Random random = new Random();
    private List<Bitmap> faces = new ArrayList<>();
    private Bitmap[] finals;
    private Runnable done;
    private int gen, turn;
    private long startedAt;
    /** The six faces of a die, drawn once. */
    private static Bitmap[] dice;
    /** The light gliding over the pick once it has landed. */
    private final Glare glare = new Glare();
    /** Turns to the landing, by card: the outer ones soonest, the middle one last. */
    private static final int[] LANDING = {2, 3, 5, 3, 2};

    DiceRoll(HomeV2 h, FrameLayout stage, ImageView[] cards, View ring) {
        this.h = h;
        this.stage = stage;
        this.cards = cards;
        this.ring = ring;
        unit = h.px(1000) / 1000f;
        float camera = h.a.getResources().getDisplayMetrics().density * 7000;
        for (int i = 0; i < 5; i++) {
            baseAlpha[i] = cards[i].getAlpha();
            baseRot[i] = cards[i].getRotationY();
            cards[i].setCameraDistance(camera);
        }
        ring.setCameraDistance(camera);
        // The middle card's shadow on the "table", behind the cards.
        FrameLayout.LayoutParams mid = (FrameLayout.LayoutParams) cards[2].getLayoutParams();
        shadow = new View(h.a);
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[]{0x8C000000, 0x00000000});
        g.setGradientRadius(mid.width * 0.42f);
        shadow.setBackground(g);
        int sw = Math.round(mid.width * 0.9f), sh = h.px(40);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(sw, sh);
        sp.leftMargin = mid.leftMargin + (mid.width - sw) / 2;
        sp.topMargin = mid.topMargin + mid.height - sh / 2;
        shadow.setScaleY(0.5f);
        stage.addView(shadow, stage.indexOfChild(cards[0]), sp);
    }

    /** The throw: the cards swept off, then tumbling in and on, showing these posters and dice until land(). */
    void start(List<Bitmap> showing) {
        int my = ++gen;
        stopAll();
        startedAt = System.currentTimeMillis();
        finals = null;
        done = null;
        faces = new ArrayList<>();
        if (showing != null) for (Bitmap b : showing) if (b != null) faces.add(b);
        ring.animate().cancel();
        ring.animate().alpha(0f).setStartDelay(0).setDuration(140).start();
        // The last pick's tilt and light stopped (stopAll cancelled its loop): flat again for the throw.
        ring.setRotationX(0);
        ring.setRotationY(baseRot[2]);
        ring.setScaleX(1f);
        ring.setScaleY(1f);
        cards[2].setForeground(null);
        for (int i = 0; i < 5; i++) {
            ImageView v = cards[i];
            int k = i;
            left[i] = -1;
            v.animate().cancel();
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            v.setRotationX(0);
            v.setRotationY(baseRot[i]);
            v.setTranslationY(0);
            v.setScaleX(1f);
            v.setScaleY(1f);
            v.setPivotY(v.getHeight() / 2f);
            // Swept away: up and off to the right, the right-hand card first.
            boolean had = v.getDrawable() != null && v.getAlpha() > 0.05f;
            v.animate().translationX(had ? h.px(260) : v.getTranslationX()).translationY(had ? -h.px(70) : 0)
                    .rotation(had ? 18 : 0).alpha(0f).setStartDelay(had ? (4 - i) * 30L : 0).setDuration(had ? 170 : 1)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(() -> throwIn(my, k)).start();
        }
    }

    /** Back in from the left, sliding to a stop over the first few turns, and tumbling all the while. */
    private void throwIn(int my, int i) {
        if (my != gen) return;
        ImageView v = cards[i];
        v.setRotation(0);
        v.setTranslationY(0);
        v.setImageBitmap(nextFace());
        float from = -h.px(620 + 90 * (4 - i));
        v.setTranslationX(from);
        ObjectAnimator x = ObjectAnimator.ofFloat(v, View.TRANSLATION_X, from, 0f);
        x.setDuration(950 + i * 40L);
        x.setInterpolator(new DecelerateInterpolator(2.2f));
        ObjectAnimator a = ObjectAnimator.ofFloat(v, View.ALPHA, 0f, baseAlpha[i]);
        a.setDuration(280);
        keep(x);
        keep(a);
        x.start();
        a.start();
        v.postDelayed(() -> hop(my, i), (4 - i) * 25L);
    }

    /** The pick is known: the cards' last faces (the middle one the pick); done runs as the middle one lands. */
    void land(Bitmap[] last, Runnable then) {
        int my = gen;
        finals = last;
        done = then;
        List<Bitmap> now = new ArrayList<>();
        for (Bitmap b : last) if (b != null) now.add(b);
        if (!now.isEmpty()) faces = now;
        // At least a second and a half of rolling, even when the pick was ready at once.
        long wait = Math.max(0, 1500 - (System.currentTimeMillis() - startedAt));
        stage.postDelayed(() -> {
            if (my != gen) return;
            for (int i = 0; i < 5; i++) left[i] = LANDING[i];
        }, wait);
    }

    /** Stops everything (the page closing). */
    void stop() {
        gen++;
        stopAll();
    }

    private void stopAll() {
        for (Animator a : new ArrayList<>(running)) a.cancel();
        running.clear();
    }

    private void keep(Animator a) {
        running.add(a);
        a.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                running.remove(animation);
            }
        });
    }

    /**
     * One quarter turn with a hop: up while it turns edge on, the new face as it is edge on, down as it turns flat.
     * Quick while rolling on, slower and lower with each of the last turns.
     */
    private void hop(int my, int i) {
        if (my != gen) return;
        ImageView v = cards[i];
        int l = left[i];
        boolean mid = i == 2;
        long d = l < 0 ? 230 : new long[]{0, 560, 430, 340, 280, 250}[Math.min(5, l)];
        float up = (l < 0 ? (mid ? 52 : 30) : (mid ? 10 + 9 * l : 6 + 7 * l)) * unit;
        boolean onSide = random.nextInt(3) > 0;
        float dir = random.nextBoolean() ? 1 : -1;
        float base = baseRot[i];
        boolean last = l == 1;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(d);
        a.setInterpolator(new LinearInterpolator());
        final boolean[] swapped = {false};
        a.addUpdateListener(an -> {
            float t = (float) an.getAnimatedValue();
            float turn = t < 0.5f ? 90 * dir * (t * 2) : -90 * dir * (1 - (t - 0.5f) * 2);
            if (t >= 0.5f && !swapped[0]) {
                swapped[0] = true;
                Bitmap next = last && finals != null ? finals[i] : nextFace();
                if (next != null) v.setImageBitmap(next); else v.setImageDrawable(null);
            }
            if (onSide) { v.setRotationY(base + turn); v.setRotationX(0); }
            else { v.setRotationX(turn); v.setRotationY(base); }
            float lift = up * 4 * t * (1 - t);
            v.setTranslationY(-lift);
            if (mid) {
                float k = lift / (unit * 52);
                shadow.setTranslationX(v.getTranslationX());
                shadow.setScaleX(1f - 0.45f * k);
                shadow.setAlpha(Math.max(0f, Math.min(1f, v.getAlpha())) * (1f - 0.55f * k));
            }
        });
        a.addListener(new AnimatorListenerAdapter() {
            boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }
            @Override public void onAnimationEnd(Animator animation) {
                if (cancelled || my != gen) return;
                v.setRotationX(0);
                v.setRotationY(base);
                v.setTranslationY(0);
                if (left[i] > 0) left[i]--;
                if (left[i] == 0) settle(my, i); else hop(my, i);
            }
        });
        keep(a);
        a.start();
    }

    /** Landed: a squash on the table and back, with an overshoot; the middle one brings the ring, the sparks and done. */
    private void settle(int my, int i) {
        ImageView v = cards[i];
        v.setPivotY(v.getHeight());
        v.animate().scaleX(1.07f).scaleY(0.9f).setStartDelay(0).setDuration(80).setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(420)
                        .setInterpolator(new OvershootInterpolator(3.2f)).withEndAction(() -> {
                            v.setPivotY(v.getHeight() / 2f);
                            v.setLayerType(View.LAYER_TYPE_NONE, null);
                            if (i == 2) tilt(my);
                        }).start()).start();
        if (i != 2) return;
        shadow.animate().scaleX(1f).alpha(1f).translationX(0).setDuration(200).start();
        ring.setScaleX(1.12f);
        ring.setScaleY(1.12f);
        ring.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(60).setDuration(380)
                .setInterpolator(new OvershootInterpolator(2.4f)).start();
        sparks(v);
        Runnable r = done;
        done = null;
        if (r != null && my == gen) r.run();
    }

    /**
     * The pick held up to the light: a slow, endless sway, as if a hand moved over it, the card leaning towards the
     * hand (up to 9 degrees across, 7 up and down) and a little larger, a soft light where the hand is; the ring leans
     * with it. Eases in over the first second; the next throw or the page closing stops it.
     */
    private void tilt(int my) {
        if (my != gen) return;
        ImageView v = cards[2];
        v.setPivotX(v.getWidth() / 2f);
        v.setPivotY(v.getHeight() / 2f);
        ring.setPivotX(ring.getWidth() / 2f);
        ring.setPivotY(ring.getHeight() / 2f);
        glare.set(0.5f, 0.5f, 0f);
        v.setForeground(glare);
        float base = baseRot[2];
        long from = System.currentTimeMillis();
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(7200);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(an -> {
            if (my != gen) { an.cancel(); return; }
            double turn = 2 * Math.PI * (float) an.getAnimatedValue();
            float in = Math.min(1f, (System.currentTimeMillis() - from) / 1200f);
            in = in * in * (3 - 2 * in);
            // Where the "hand" is over the card: round and across, a figure of eight up and down.
            float x = (float) (0.5 + 0.38 * Math.cos(turn)), y = (float) (0.5 + 0.3 * Math.sin(2 * turn));
            float ry = (x - 0.5f) * 18f * in, rx = -(y - 0.5f) * 14f * in, grow = 1f + 0.035f * in;
            for (View w : new View[]{v, ring}) {
                w.setRotationY(base + ry);
                w.setRotationX(rx);
                w.setScaleX(grow);
                w.setScaleY(grow);
            }
            glare.set(x, y, in);
        });
        keep(a);
        a.start();
    }

    /** The light on the pick: a soft white spot where the hand is, fading out to nothing; it turns with the card. */
    private static final class Glare extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float x = 0.5f, y = 0.5f, strength;

        void set(float x, float y, float strength) {
            this.x = x;
            this.y = y;
            this.strength = strength;
            invalidateSelf();
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            if (strength <= 0.01f || b.isEmpty()) return;
            float cx = b.left + x * b.width(), cy = b.top + y * b.height(), r = Math.max(b.width(), b.height()) * 0.8f;
            int a0 = Math.round(105 * strength), a1 = Math.round(34 * strength);
            paint.setShader(new RadialGradient(cx, cy, r, new int[]{a0 << 24 | 0xFFFFFF, a1 << 24 | 0xFFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.38f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(b, paint);
        }

        @Override public void setAlpha(int alpha) {
        }

        @Override public void setColorFilter(ColorFilter cf) {
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** A few sparks thrown out from the middle card's foot as it lands. */
    private void sparks(ImageView v) {
        FrameLayout.LayoutParams mid = (FrameLayout.LayoutParams) v.getLayoutParams();
        int[] colours = {0xFFFFFFFF, 0xFFFFD04D, 0xFFFFFFFF, 0xFF9DB4FF};
        for (int k = 0; k < 14; k++) {
            View s = new View(h.a);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(colours[k % colours.length]);
            s.setBackground(g);
            int size = h.px(5 + random.nextInt(6));
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(size, size);
            boolean right = k % 2 == 0;
            p.leftMargin = mid.leftMargin + (right ? mid.width - h.px(18) : h.px(18)) - size / 2;
            p.topMargin = mid.topMargin + mid.height - h.px(10) - size / 2;
            stage.addView(s, p);
            double angle = Math.toRadians((right ? -10 : -170) + (right ? -1 : 1) * random.nextInt(70));
            float dist = h.px(70 + random.nextInt(110));
            s.animate().translationX((float) Math.cos(angle) * dist).translationY((float) Math.sin(angle) * dist)
                    .scaleX(0.3f).scaleY(0.3f).alpha(0f).setDuration(520 + random.nextInt(260))
                    .setInterpolator(new DecelerateInterpolator(1.6f)).withEndAction(() -> stage.removeView(s)).start();
        }
    }

    /** The next face while rolling: a poster, then a die's face, and so on (only dice before there are posters). */
    private Bitmap nextFace() {
        turn++;
        if (faces.isEmpty() || turn % 2 == 0) return die(1 + random.nextInt(6));
        return faces.get(random.nextInt(faces.size()));
    }

    /** A die's face on a card: white with navy spots (the one red), on the page's blue, lit from behind. */
    static Bitmap die(int pips) {
        if (dice == null) dice = new Bitmap[6];
        if (dice[pips - 1] != null) return dice[pips - 1];
        int w = 240, hh = 360;
        Bitmap b = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w * 0.6f, hh, 0xFF4B3BE0, 0xFF111762, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, hh, p);
        p.setShader(new RadialGradient(w / 2f, hh / 2f, w * 0.75f, 0x59FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawCircle(w / 2f, hh / 2f, w * 0.75f, p);
        p.setShader(null);
        float s = w * 0.64f, l = (w - s) / 2f, t = (hh - s) / 2f;
        Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
        face.setShadowLayer(s * 0.07f, 0, s * 0.05f, 0x73000000);
        face.setShader(new LinearGradient(0, t, 0, t + s, 0xFFFFFFFF, 0xFFDDE2F4, Shader.TileMode.CLAMP));
        cv.drawRoundRect(l, t, l + s, t + s, s * 0.2f, s * 0.2f, face);
        float[] at = {0.24f, 0.5f, 0.76f};
        int[][] spots = {{4}, {0, 8}, {0, 4, 8}, {0, 2, 6, 8}, {0, 2, 4, 6, 8}, {0, 2, 3, 5, 6, 8}};
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(pips == 1 ? 0xFFE63946 : 0xFF141A5C);
        float r = s * (pips == 1 ? 0.13f : 0.085f);
        for (int k : spots[pips - 1]) cv.drawCircle(l + s * at[k % 3], t + s * at[k / 3], r, dot);
        dice[pips - 1] = b;
        return b;
    }

    // ---- where Play opens it: the marks after "on" ----------------------------------------------------------------------

    /**
     * After "on" in the spin's details: Debrid Stream's mark first (when it is on the TV), then the streaming services
     * that carry the title in the UK (TMDB's subscription, free and with-ads lists), those with an app on this TV first,
     * two at most, each with the launcher's own logo, a thin rule between them. False when there is none to show.
     */
    static boolean marks(HomeV2 h, LinearLayout into, JSONObject gb) {
        into.removeAllViews();
        List<Object> list = new ArrayList<>();
        if (ReleaseAlerts.installed(h.a, ReleaseAlerts.DEBRID)) {
            Bitmap debrid = ReleaseAlerts.debridLogo(h.a);
            if (debrid != null) list.add(debrid);
        }
        List<Integer> mine = new ArrayList<>(), others = new ArrayList<>();
        for (String kind : new String[]{"flatrate", "free", "ads"}) {
            JSONArray arr = gb == null ? null : gb.optJSONArray(kind);
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject pr = arr.optJSONObject(i);
                String id = pr == null ? null : String.valueOf(pr.optInt("provider_id"));
                int logo = HomeV2.serviceLogo(id);
                if (logo == 0 || mine.contains(logo) || others.contains(logo)) continue;
                String app = HomeV2.serviceApp(id);
                if (app != null && ReleaseAlerts.installed(h.a, app)) mine.add(logo); else others.add(logo);
            }
        }
        mine.addAll(others);
        for (int k = 0; k < Math.min(2, mine.size()); k++) list.add(mine.get(k));
        for (int k = 0; k < list.size(); k++) {
            if (k > 0) {
                View rule = new View(h.a);
                rule.setBackgroundColor(0x59FFFFFF);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(Math.max(1, h.px(1.5f)), h.px(24));
                rp.leftMargin = rp.rightMargin = h.px(12);
                into.addView(rule, rp);
            }
            ImageView m = new ImageView(h.a);
            m.setAdjustViewBounds(true);
            m.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Object o = list.get(k);
            boolean debrid = o instanceof Bitmap;
            int tall = 38;
            if (debrid) m.setImageBitmap((Bitmap) o);
            else {
                // Each logo about as weighty as the others: a long wordmark (Netflix) lower, a squarer one (Disney+,
                // Sky) taller.
                android.graphics.drawable.Drawable d = h.a.getDrawable((Integer) o);
                m.setImageDrawable(d);
                float aspect = d == null || d.getIntrinsicHeight() <= 0 ? 3f : d.getIntrinsicWidth() / (float) d.getIntrinsicHeight();
                tall = Math.max(22, Math.min(34, Math.round(95 / aspect)));
            }
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-2, h.px(tall));
            if (k == 0) mp.leftMargin = h.px(10);
            into.addView(m, mp);
        }
        into.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        return !list.isEmpty();
    }
}
