package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The Kids page's little shows, at the top of the page while the focus is on its tabs, and the family's own children as
 * 3D cartoons. Each show is a picture in the hero's place (the children's own scenes from the TV's theme folder,
 * kids_scene*.jpg: a jungle party, a football match, the beach at Cox's Bazar; and two the launcher draws: a space
 * adventure and a dance party) drifting slowly, with something going on over it: balloons rising, confetti falling,
 * the sea sparkling with gulls going over, stars twinkling and shooting, music notes and spotlights. In the space
 * adventure the children float about among the stars; at the dance party they dance. Down in the posters the picture
 * at the top is the title's again, and the children walk about on the line above the tabs: waddling along, hopping,
 * spinning round, wriggling with excitement and kicking a ball to one another. The children's pictures are the
 * family's own, kept on the TV only (the theme folder's kids_3d*.png, cut out; "sit" in the name for one who sits,
 * "ride" for one on a bouncy ride-on toy, who bounces along on it, rocks and bounces for joy),
 * never in the launcher or its repository; without them there are no children and no shows of theirs. Everything
 * keeps still a moment whenever a key is pressed, so the page moves without them costing a frame, and goes gentler on
 * a slower TV (lite_home).
 */
final class KidsPlay extends View {

    // ---- what the TV has -------------------------------------------------------------------------------------------

    /** The children's pictures (the theme folder's kids_3d*.png, in name order); empty: none. */
    static List<File> cast(Context c) {
        return files(c, "kids_3d", ".png");
    }

    /** The children's scenes (the theme folder's kids_scene*.jpg or .png, in name order). */
    static List<File> scenes(Context c) {
        List<File> out = files(c, "kids_scene", ".jpg");
        out.addAll(files(c, "kids_scene", ".png"));
        return out;
    }

    private static List<File> files(Context c, String start, String end) {
        File dir = Config.dir(c);
        File[] found = dir == null ? null : dir.listFiles((d, n) -> n.startsWith(start) && n.toLowerCase(Locale.ROOT).endsWith(end));
        List<File> out = new ArrayList<>();
        if (found != null) { Arrays.sort(found); out.addAll(Arrays.asList(found)); }
        return out;
    }

    /** One show: its picture (a file, or kidsact:space / kidsact:dance, drawn by KidsArt), its words, what goes on. */
    static final class Act {
        final String picture, title, line;
        final int effect;
        /** The children out on the page (floating or dancing), not only in the picture. */
        final boolean out;

        Act(String picture, String title, String line, int effect, boolean out) {
            this.picture = picture;
            this.title = title;
            this.line = line;
            this.effect = effect;
            this.out = out;
        }
    }

    static final int BALLOONS = 0, CONFETTI = 1, SEASIDE = 2, SPACE = 3, DANCE = 4;

    private static Act actFor(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        String pic = Uri.fromFile(f).toString();
        if (n.contains("jungle") || n.contains("party"))
            return new Act(pic, "Jungle party!", "Balloons, a birthday cake and a dinosaur friend", BALLOONS, false);
        if (n.contains("football"))
            return new Act(pic, "Match day!", "Three little footballers chasing the ball", CONFETTI, false);
        if (n.contains("beach") || n.contains("cox"))
            return new Act(pic, "Beach day at Cox's Bazar", "Sandcastles by one of the longest beaches in the world", SEASIDE, false);
        if (n.contains("space"))
            return new Act(pic, "Space adventure!", "Off to the stars and back", SPACE, false);
        return new Act(pic, "Playtime!", "Everything for kids, all in one place", CONFETTI, false);
    }

    // ---- the children -----------------------------------------------------------------------------------------------

    private static final int STAND = 0, WALK = 1, HOP = 2, SPIN = 3, WIGGLE = 4, KICK = 5, SCOOT = 6, RIDE = 7, ROCK = 8;
    /** Where the children are: on the line above the tabs, hidden in the picture, floating, dancing. */
    private static final int GROUND = 0, HIDDEN = 1, FLOAT = 2, DANCING = 3;

    private static final class Kid {
        Bitmap pic;
        boolean sits, rides;
        float x, lift, rot, sx = 1f, sy = 1f, alpha, face = 1f;
        float fy;            // floating: the height of its middle above the line
        int doing = STAND;
        float age, length;   // how long it has been doing it, and for how long
        float fromX, toX;
        float phase, step;
        long until;          // standing: when to do something next
    }

    private final HomeV2 h;
    private final float u;
    private final boolean lite;
    private final Random random = new Random();
    private final List<Kid> kids = new ArrayList<>();
    private final List<Act> acts = new ArrayList<>();
    private int act;
    private boolean showing;     // the shows (the focus on the tabs), not the posters
    private int where = GROUND;
    private boolean running;
    private long hushedUntil, last;
    private final Runnable next;
    private final Runnable turn = this::turn;
    private ValueAnimator drift;
    /** Home's two hero pictures (one fading in over the other), drifting together. */
    private final ImageView[] heroes;
    /** The dance party's spotlights and a shooting star's tail, made once. */
    private Shader[] beamShade;
    private Shader tail;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();

    /**
     * @param heroAgain asked for when the show changes: the page shows it at the top (KidsPlay.tile) if the focus is
     *                  still on its tabs
     */
    KidsPlay(HomeV2 h, List<File> cast, List<File> scenes, Runnable heroAgain) {
        super(h.a);
        this.h = h;
        u = h.px(1000) / 1000f;
        lite = h.lite;
        next = heroAgain;
        heroes = new ImageView[]{h.heroA, h.heroB};
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        for (File f : cast) {
            Kid k = new Kid();
            String name = f.getName().toLowerCase(Locale.ROOT);
            k.rides = name.contains("ride");
            k.sits = !k.rides && name.contains("sit");
            k.pic = read(f, Math.round((k.rides ? 214 : k.sits ? 172 : 232) * u));
            if (k.pic == null) continue;
            k.phase = random.nextFloat() * 6.28f;
            kids.add(k);
        }
        // When the children are here, the space adventure the launcher draws opens (unless there is a scene of it: the
        // children are out at once, floating in), then the children's own scenes with the dance party after the second.
        boolean spaceScene = false;
        for (File f : scenes) {
            Act a = actFor(f);
            if (a.effect == SPACE) spaceScene = true;
            acts.add(a);
        }
        if (!kids.isEmpty()) {
            if (!spaceScene) acts.add(0, new Act("kidsact:space", "Space adventure!", "Floating among the stars", SPACE, true));
            acts.add(Math.min(3, acts.size()), new Act("kidsact:dance", "Dance party!", "Everybody dance!", DANCE, true));
        }
        // Spread along the line, coming in one after another from the right.
        for (int i = 0; i < kids.size(); i++) {
            Kid k = kids.get(i);
            k.x = width() + 160 * u;
            k.toX = spot(i, kids.size());
            k.fromX = k.x;
            k.doing = WALK;
            k.alpha = 1f;
            k.age = -0.9f * i;   // a little later each
            k.length = 99f;
        }
        setLayerType(LAYER_TYPE_NONE, null);
    }

    /** A child's picture, at the height it is drawn. */
    private static Bitmap read(File f, int height) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (o.outHeight <= 0 || height <= 0) return null;
            int sample = 1;
            while (o.outHeight / (sample * 2) >= height) sample *= 2;
            BitmapFactory.Options read = new BitmapFactory.Options();
            read.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), read);
            if (b == null) return null;
            int w = Math.max(1, Math.round(b.getWidth() * height / (float) b.getHeight()));
            Bitmap s = Bitmap.createScaledBitmap(b, w, height, true);
            if (s != b) b.recycle();
            s.prepareToDraw();
            return s;
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    boolean hasShows() {
        return !acts.isEmpty();
    }

    /** The show's picture and words, for the top of the page. */
    HomeV2.Tile tile(String crumb) {
        Act a = acts.get(act % acts.size());
        HomeV2.Tile t = new HomeV2.Tile();
        t.title = a.title;
        t.heroTitle = a.title;
        t.heroImage = a.picture;
        t.heroTop = crumb;
        t.heroMeta = a.line;
        t.heroText = "Down for cartoons, films and YouTube";
        return t;
    }

    /** Which show is on (the page shows its picture once). */
    int act() {
        return act;
    }

    void start() {
        if (running) return;
        running = true;
        last = SystemClock.uptimeMillis();
        invalidate();
    }

    void stop() {
        running = false;
        removeCallbacks(turn);
        if (drift != null) drift.cancel();
        drift = null;
        // Home's own pictures, as they were.
        for (ImageView v : heroes) {
            v.animate().cancel();
            v.setScaleX(1f);
            v.setScaleY(1f);
            v.setTranslationX(0f);
            v.setTranslationY(0f);
        }
    }

    /** A key was pressed on the page: everything still a moment, so the page moves without them costing a frame. */
    void hush() {
        hushedUntil = SystemClock.uptimeMillis() + 1400;
        if (drift != null && drift.isRunning() && !drift.isPaused()) drift.pause();
    }

    /** The shows on (the focus on the tabs) or off (in the posters, the title's picture at the top). */
    void shows(boolean on) {
        if (on == showing) return;
        showing = on;
        removeCallbacks(turn);
        if (on) {
            postDelayed(turn, lite ? 20_000 : 16_000);
            startDrift();
        } else {
            if (drift != null) drift.cancel();
            drift = null;
            for (ImageView v : heroes)
                v.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f).setDuration(400).start();
        }
        place();
        invalidate();
    }

    /** The next show, when the page is in sight; the page puts its picture at the top. */
    private void turn() {
        if (!running || !showing) return;
        postDelayed(turn, lite ? 20_000 : 16_000);
        if (getWindowVisibility() != VISIBLE || acts.size() < 2) return;
        act = (act + 1) % acts.size();
        place();
        next.run();
        invalidate();
    }

    /** The picture at the top drifting slowly in and out (Home's two hero pictures together, so the fade matches). */
    private void startDrift() {
        if (drift != null) drift.cancel();
        drift = ValueAnimator.ofFloat(0f, 1f);
        drift.setDuration(lite ? 20_000 : 16_000);
        drift.setRepeatCount(ValueAnimator.INFINITE);
        drift.setRepeatMode(ValueAnimator.REVERSE);
        drift.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        float from = h.heroA.getScaleX() - 1f;
        drift.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            for (ImageView img : heroes) {
                img.setPivotX(img.getWidth() * 0.62f);
                img.setPivotY(img.getHeight() * 0.42f);
                float s = 1f + 0.065f * v;
                img.setScaleX(s);
                img.setScaleY(s);
                img.setTranslationX(-26 * u * v);
                img.setTranslationY(-8 * u * v);
            }
        });
        drift.setCurrentFraction(Math.max(0f, Math.min(1f, from / 0.065f)));
        drift.start();
    }

    /** Where the children go for what is on now. */
    private void place() {
        int was = where;
        Act a = acts.isEmpty() ? null : acts.get(act % acts.size());
        where = !showing || a == null ? GROUND : !a.out ? HIDDEN : a.effect == SPACE ? FLOAT : DANCING;
        if (where == was) return;
        for (int i = 0; i < kids.size(); i++) {
            Kid k = kids.get(i);
            k.age = 0;
            k.step = 0;
            // Floating, a child turns about its middle, else about its feet: the height carried over, no jump.
            float half = k.pic.getHeight() * 0.43f;
            if (was == FLOAT) k.lift = Math.max(0f, k.lift - half);
            if (where == FLOAT) k.lift += half;
            if (where == GROUND) {
                // Back on the line: those hidden in the picture pop up where they stood; those not yet in walk in.
                k.until = 0;
                k.length = 0.7f;
                if (k.x > width() - 40 * u) {
                    k.doing = WALK;
                    k.toX = spot(i, kids.size());
                    k.alpha = 1f;
                } else {
                    k.doing = was == HIDDEN ? HOP : STAND;
                    k.toX = k.x;
                    if (was == HIDDEN) k.alpha = 0.01f;
                }
            } else if (where == FLOAT) {
                k.fy = (i % 2 == 0 ? 250 : 150) * u + random.nextInt(40) * u;
                k.toX = (520 + i * 290 + random.nextInt(60)) * u;
            } else if (where == DANCING) {
                k.toX = spot(i, kids.size());
            }
        }
        ball.on = false;
    }

    /** A child's place along the line, the children spread out on the picture's side. */
    private float spot(int i, int n) {
        float left = 520 * u, right = 1240 * u;
        return n <= 1 ? (left + right) / 2 : left + (right - left) * i / (n - 1);
    }

    private float width() {
        return getWidth() > 0 ? getWidth() : 1400 * u;
    }

    private float ground() {
        return (getHeight() > 0 ? getHeight() : 516 * u) - 2 * u;
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (drift == null) return;
        if (visibility == VISIBLE) { if (drift.isPaused()) drift.resume(); } else if (drift.isRunning()) drift.pause();
    }

    // ---- the ball they kick to one another ------------------------------------------------------------------------

    private static final class Ball {
        boolean on;
        float x, y, fromX, toX, age, spin, rest, alpha;
        Kid to;
    }

    private final Ball ball = new Ball();

    // ---- what goes on over the picture ----------------------------------------------------------------------------

    private static final int P_BALLOON = 0, P_CONFETTI = 1, P_SPARK = 2, P_NOTE = 3, P_SHOOT = 4, P_GULL = 5;
    private static final int MAX = 64;
    private final int[] kind = new int[MAX], colour = new int[MAX];
    private final float[] px = new float[MAX], py = new float[MAX], vx = new float[MAX], vy = new float[MAX],
            age = new float[MAX], life = new float[MAX], size = new float[MAX], spin = new float[MAX], phase = new float[MAX];
    private int count;
    private float spawn, burst;

    private static final int[] BALLOON_COLOURS = {0xFFFF5E5B, 0xFFFFC93C, 0xFF3DDC84, 0xFF4CC9F0, 0xFFFF8AC2, 0xFF9B5CFF, 0xFFFF9F1C};
    private static final int[] CONFETTI_COLOURS = {0xFFE53935, 0xFFFFFFFF, 0xFFFFC93C, 0xFFE53935, 0xFF4CC9F0, 0xFFFF8AC2};
    private static final int[] NOTE_COLOURS = {0xFFFFC93C, 0xFFFF8AC2, 0xFF4CC9F0, 0xFF3DDC84, 0xFFFFFFFF};

    private int add(int k, float x, float y, float vxx, float vyy, float lifeS, float sz, int c) {
        if (count >= MAX) return -1;
        int i = count++;
        kind[i] = k;
        px[i] = x;
        py[i] = y;
        vx[i] = vxx;
        vy[i] = vyy;
        age[i] = 0;
        life[i] = lifeS;
        size[i] = sz;
        colour[i] = c;
        spin[i] = random.nextFloat() * 360f;
        phase[i] = random.nextFloat() * 6.28f;
        return i;
    }

    private float rnd(float a, float b) {
        return a + random.nextFloat() * (b - a);
    }

    /** New pieces for the show on, as many a second as it has (half on a slower TV). */
    private void spawn(float dt) {
        if (!showing || acts.isEmpty()) return;
        Act a = acts.get(act % acts.size());
        float W = width(), H = ground(), k = lite ? 0.5f : 1f;
        spawn += dt;
        burst += dt;
        switch (a.effect) {
            case BALLOONS:
                if (spawn > 0.75f / k) {
                    spawn = 0;
                    add(P_BALLOON, rnd(470, 1360) * u, H + 70 * u, 0, -rnd(70, 105) * u, 12f, rnd(30, 40) * u,
                            BALLOON_COLOURS[random.nextInt(BALLOON_COLOURS.length)]);
                }
                if (!lite && random.nextFloat() < dt * 3f) confetti(rnd(470, 1360) * u, -10 * u);
                break;
            case CONFETTI:
                if (burst > 3.4f) {
                    burst = 0;
                    int n = lite ? 14 : 28;
                    for (int i = 0; i < n; i++) confetti(rnd(460, 1380) * u, -rnd(10, 120) * u);
                }
                break;
            case SEASIDE:
                if (spawn > 0.2f / k) {
                    spawn = 0;
                    add(P_SPARK, rnd(400, 620) * u, rnd(250, 420) * u, 0, 0, rnd(0.6f, 1.1f), rnd(7, 13) * u, 0xFFFFFFFF);
                }
                if (burst > 5.5f) {
                    burst = 0;
                    float y = rnd(120, 210) * u;
                    add(P_GULL, W + 40 * u, y, -rnd(60, 85) * u, rnd(-4, 4) * u, 30f, rnd(16, 24) * u, 0xFFFFFFFF);
                    if (!lite && random.nextBoolean()) add(P_GULL, W + 110 * u, y + 30 * u, -rnd(60, 85) * u, 0, 30f, rnd(12, 18) * u, 0xFFFFFFFF);
                }
                break;
            case SPACE:
                if (spawn > 0.16f / k) {
                    spawn = 0;
                    add(P_SPARK, rnd(440, 1390) * u, rnd(15, 470) * u, 0, 0, rnd(0.8f, 1.5f), rnd(5, 12) * u, 0xFFFFFFFF);
                }
                if (burst > 3.6f) {
                    burst = 0;
                    add(P_SHOOT, rnd(900, 1400) * u, rnd(90, 190) * u, -760 * u, 300 * u, 1.3f, 150 * u, 0xFFFFFFFF);
                }
                break;
            case DANCE:
                if (spawn > 0.4f / k && !kids.isEmpty()) {
                    spawn = 0;
                    Kid kd = kids.get(random.nextInt(kids.size()));
                    add(P_NOTE, kd.x + rnd(-40, 40) * u, ground() - (kd.sits ? 190 : 250) * u, rnd(-12, 12) * u, -rnd(55, 80) * u, 2.6f,
                            rnd(26, 36) * u, NOTE_COLOURS[random.nextInt(NOTE_COLOURS.length)]);
                }
                if (!lite && random.nextFloat() < dt * 4f) confetti(rnd(470, 1380) * u, -10 * u);
                break;
            default:
                break;
        }
    }

    private void confetti(float x, float y) {
        add(P_CONFETTI, x, y, rnd(-40, 40) * u, rnd(140, 230) * u, 6f, rnd(7, 11) * u, CONFETTI_COLOURS[random.nextInt(CONFETTI_COLOURS.length)]);
    }

    private void moveParticles(float dt) {
        float H = ground();
        for (int i = 0; i < count; i++) {
            age[i] += dt;
            switch (kind[i]) {
                case P_BALLOON:
                    px[i] += (float) Math.sin(age[i] * 1.3f + phase[i]) * 18 * u * dt;
                    py[i] += vy[i] * dt;
                    if (py[i] < -120 * u) age[i] = life[i];
                    break;
                case P_CONFETTI:
                    px[i] += (vx[i] + (float) Math.sin(age[i] * 3f + phase[i]) * 30 * u) * dt;
                    py[i] += vy[i] * dt;
                    spin[i] += 260 * dt;
                    if (py[i] > H + 20 * u) age[i] = life[i];
                    break;
                case P_NOTE:
                    px[i] += (vx[i] + (float) Math.sin(age[i] * 2.4f + phase[i]) * 22 * u) * dt;
                    py[i] += vy[i] * dt;
                    break;
                default:
                    px[i] += vx[i] * dt;
                    py[i] += vy[i] * dt;
                    if (kind[i] == P_GULL && px[i] < -80 * u) age[i] = life[i];
            }
        }
        // The finished ones out (the last in each place).
        for (int i = count - 1; i >= 0; i--) {
            if (age[i] < life[i]) continue;
            int j = --count;
            kind[i] = kind[j]; colour[i] = colour[j]; px[i] = px[j]; py[i] = py[j]; vx[i] = vx[j]; vy[i] = vy[j];
            age[i] = age[j]; life[i] = life[j]; size[i] = size[j]; spin[i] = spin[j]; phase[i] = phase[j];
        }
    }

    // ---- each frame -------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.05f, (now - last) / 1000f);
        last = now;
        boolean hushed = now < hushedUntil;
        if (running && !hushed) {
            if (drift != null && drift.isPaused() && getWindowVisibility() == VISIBLE) drift.resume();
            spawn(dt);
            moveParticles(dt);
            moveKids(now, dt);
        }
        if (showing && !acts.isEmpty() && acts.get(act % acts.size()).effect == DANCE) beams(c, now / 1000f);
        drawParticles(c, true);
        for (Kid k : kids) drawKid(c, k);
        if (ball.on) drawBall(c);
        drawParticles(c, false);
        if (!running) return;
        if (hushed) postInvalidateDelayed(hushedUntil - now + 16);
        else if (busy()) { if (lite) postInvalidateDelayed(33); else postInvalidateOnAnimation(); }
        else postInvalidateDelayed(Math.max(60, nextChange() - now));
    }

    /** Anything moving (else the page is left alone until the next child does something). */
    private boolean busy() {
        if (count > 0 || ball.on || showing && where != GROUND) return true;
        if (showing && !acts.isEmpty()) return true;   // the show's pieces keep coming
        for (Kid k : kids) if (k.doing != STAND || k.alpha < 1f || Math.abs(k.lift) > 0.5f || Math.abs(k.rot) > 0.3f) return true;
        return !lite;   // breathing (not on a slower TV)
    }

    private long nextChange() {
        long soonest = Long.MAX_VALUE;
        for (Kid k : kids) soonest = Math.min(soonest, k.until);
        return soonest == Long.MAX_VALUE ? SystemClock.uptimeMillis() + 1000 : soonest;
    }

    private void moveKids(long now, float dt) {
        float t = now / 1000f;
        for (int i = 0; i < kids.size(); i++) {
            Kid k = kids.get(i);
            switch (where) {
                case HIDDEN:
                    k.alpha = Math.max(0f, k.alpha - dt * 2.5f);
                    settle(k, dt);
                    break;
                case FLOAT: {
                    // Weightless: drifting to their places among the stars, bobbing and turning slowly.
                    k.alpha = Math.min(1f, k.alpha + dt * 2f);
                    float ease = 1f - (float) Math.exp(-dt * 1.4f);
                    k.x += (k.toX + (float) Math.sin(t * 0.5f + k.phase) * 18 * u - k.x) * ease;
                    float wantLift = k.fy + (float) Math.sin(t * 0.9f + k.phase) * 26 * u;
                    k.lift += (wantLift - k.lift) * ease;
                    k.rot += ((float) Math.sin(t * 0.6f + k.phase) * 16f - k.rot) * ease;
                    k.sx += (1f - k.sx) * ease;
                    k.sy += (1f - k.sy) * ease;
                    break;
                }
                case DANCING:
                    dance(k, i, t, dt);
                    break;
                default:
                    walkAbout(k, i, now, dt, t);
            }
        }
        if (ball.on) moveBall(dt);
    }

    /** Lift, lean and squash eased back to standing. */
    private void settle(Kid k, float dt) {
        float e = 1f - (float) Math.exp(-dt * 8f);
        k.lift += (0 - k.lift) * e;
        k.rot += (0 - k.rot) * e;
        k.sx += (1 - k.sx) * e;
        k.sy += (1 - k.sy) * e;
    }

    private void dance(Kid k, int i, float t, float dt) {
        k.alpha = Math.min(1f, k.alpha + dt * 2f);
        // To its place on the floor first, with a quick shuffle.
        float d = k.toX - k.x;
        if (Math.abs(d) > 4 * u) {
            k.x += Math.signum(d) * Math.min(Math.abs(d), 260 * u * dt);
            k.face = Math.signum(d);
            k.rot = (float) Math.sin(t * 16f) * 5f;
            k.lift = Math.abs((float) Math.sin(t * 16f)) * 6 * u;
            return;
        }
        k.face = 1f;
        float beat = t * 2.1f;            // about 126 beats a minute
        int bar = (int) (beat / 4f) % 4;
        float b = beat - (float) Math.floor(beat);
        float s = (float) Math.sin(Math.PI * beat);
        if (k.rides) {
            // On the ride-on: bouncing to the beat, rocking as it lands.
            k.lift = Math.abs(s) * 24 * u;
            k.rot = (float) Math.sin(Math.PI * beat / 2f) * 7f;
            k.sy = 1f - 0.05f * (1f - Math.abs(s));
            k.sx = 2f - k.sy;
            return;
        }
        if (k.sits) {
            // The little one bounces and claps along.
            k.lift = Math.abs(s) * 14 * u;
            k.rot = (float) Math.sin(t * 14f) * 4f;
            k.sx = 1f + 0.03f * Math.abs(s);
            k.sy = 1f - 0.03f * Math.abs(s);
            return;
        }
        k.sx = 1f;
        switch (bar) {
            case 0:   // sway side to side
                k.rot = s * 11f;
                k.lift = Math.abs(s) * 10 * u;
                k.sy = 1f;
                break;
            case 1:   // a bounce on every beat, squashing as they land
                k.rot = 0;
                k.lift = (float) Math.sin(Math.PI * b) * 30 * u;
                k.sy = b < 0.15f || b > 0.85f ? 0.92f : 1.04f;
                k.sx = 2f - k.sy;
                break;
            case 2:   // lean and wiggle, one after another
                k.rot = (float) Math.sin(Math.PI * (beat + i * 0.33f)) * 14f;
                k.lift = 0;
                k.sy = 1f;
                break;
            default:  // a spin on the bar's last beat, hops before it
                float inBar = beat / 4f - (float) Math.floor(beat / 4f);
                if (inBar > 0.75f) {
                    float p = (inBar - 0.75f) / 0.25f;
                    k.sx = (float) Math.cos(2 * Math.PI * p);
                    k.lift = (float) Math.sin(Math.PI * p) * 46 * u;
                } else {
                    k.lift = (float) Math.sin(Math.PI * b) * 18 * u;
                }
                k.rot = 0;
                k.sy = 1f;
        }
    }

    /** On the line above the tabs: standing, then off doing something. */
    private void walkAbout(Kid k, int i, long now, float dt, float t) {
        k.alpha = Math.min(1f, k.alpha + dt * 3f);
        k.age += dt;
        if (k.age < 0) return;   // not in yet
        switch (k.doing) {
            case WALK: {
                // A waddle: rocking side to side, a little bob with each step.
                float speed = (k.sits ? 60 : 125) * u;
                float d = k.toX - k.x;
                k.face = d >= 0 ? 1f : -1f;
                k.step += dt * 6.28f * 2.3f;
                k.rot = (float) Math.sin(k.step) * 6f;
                k.lift = Math.abs((float) Math.sin(k.step)) * 7 * u;
                k.sx = 1f;
                k.sy = 1f;
                if (Math.abs(d) <= speed * dt) { k.x = k.toX; stand(k, now); } else k.x += Math.signum(d) * speed * dt;
                break;
            }
            case SCOOT: {
                // The little one shuffles along on their bottom, a bounce at a time.
                float d = k.toX - k.x;
                k.step += dt * 6.28f * 1.6f;
                float bob = Math.abs((float) Math.sin(k.step));
                k.lift = bob * 10 * u;
                k.rot = (float) Math.sin(k.step) * 4f;
                if (bob > 0.6f) k.x += Math.signum(d) * Math.min(Math.abs(d), 90 * u * dt);
                if (Math.abs(d) < 2 * u) stand(k, now);
                break;
            }
            case RIDE: {
                // Bouncing along on the ride-on toy: up and forward, squashing a little at each landing.
                float d = k.toX - k.x;
                k.face = d >= 0 ? 1f : -1f;
                k.step += dt * 6.28f * 1.9f;
                float bob = Math.abs((float) Math.sin(k.step));
                k.lift = bob * 24 * u;
                k.rot = (float) Math.sin(k.step) * 5f * k.face;
                k.sy = 1f - 0.06f * (1f - bob);
                k.sx = 2f - k.sy;
                float speed = 110 * u;
                if (Math.abs(d) <= speed * dt) { k.x = k.toX; stand(k, now); } else k.x += Math.signum(d) * speed * dt;
                break;
            }
            case ROCK: {
                // Rocking back and forth on it, as on a rocking horse.
                float p = Math.min(1f, k.age / k.length);
                k.rot = (float) Math.sin(k.age * 6.5f) * 9f * (float) Math.sin(Math.PI * p);
                k.lift = 0;
                if (p >= 1f) stand(k, now);
                break;
            }
            case HOP:
            case SPIN: {
                float p = Math.min(1f, k.age / k.length);
                float high = (k.doing == SPIN ? 92 : k.sits ? 36 : k.rides ? 54 : 70) * u;
                if (p < 0.15f) {           // crouch
                    float q = p / 0.15f;
                    k.sy = 1f - 0.12f * q;
                    k.sx = 1f + 0.08f * q;
                    k.lift = 0;
                } else if (p < 0.85f) {    // up and down, stretched
                    float q = (p - 0.15f) / 0.7f;
                    k.lift = (float) Math.sin(Math.PI * q) * high;
                    k.sy = 1.05f;
                    k.sx = k.doing == SPIN ? (float) Math.cos(2 * Math.PI * q) : 0.96f;
                } else {                   // land, squashed, and back up
                    float q = (p - 0.85f) / 0.15f;
                    k.lift = 0;
                    k.sy = 0.9f + 0.1f * q;
                    k.sx = 1.07f - 0.07f * q;
                }
                k.rot = 0;
                if (p >= 1f) stand(k, now);
                break;
            }
            case WIGGLE: {
                // Too excited to stand still (the little one clapping).
                float p = Math.min(1f, k.age / k.length);
                float env = (float) Math.sin(Math.PI * p);
                k.rot = (float) Math.sin(k.age * 19f) * 8f * env;
                k.lift = Math.abs((float) Math.sin(k.age * 9.5f)) * 9 * u * env;
                if (p >= 1f) stand(k, now);
                break;
            }
            case KICK: {
                float p = Math.min(1f, k.age / k.length);
                k.rot = -14f * k.face * (float) Math.sin(Math.PI * p);
                k.lift = (float) Math.sin(Math.PI * p) * 6 * u;
                if (p >= 1f) stand(k, now);
                break;
            }
            default:
                settle(k, dt);
                if (!lite) k.sy = 1f + 0.012f * (float) Math.sin(t * 2.4f + k.phase);
                if (now >= k.until && k.until > 0) choose(k, i, now);
                else if (k.until == 0) stand(k, now);
        }
    }

    private void stand(Kid k, long now) {
        k.doing = STAND;
        k.age = 0;
        k.step = 0;
        // Mostly standing about (longer on a slower TV): the page is not kept busy drawing them.
        k.until = now + (lite ? 4500 : 2800) + random.nextInt(lite ? 6000 : 4500);
    }

    private void choose(Kid k, int i, long now) {
        k.age = 0;
        int r = random.nextInt(100);
        Kid mate = mate(k);
        if (r < 16 && mate != null && !ball.on) {
            // A kick to one of the others, who jumps for joy when it arrives.
            k.doing = KICK;
            k.length = 0.45f;
            k.face = mate.x >= k.x ? 1f : -1f;
            ball.on = true;
            ball.fromX = k.x + k.face * 34 * u;
            ball.toX = mate.x - k.face * 34 * u;
            ball.x = ball.fromX;
            ball.y = 0;
            ball.age = -0.2f;
            ball.rest = 0;
            ball.alpha = 1f;
            ball.to = mate;
            return;
        }
        if (k.rides) {
            if (r < 45) {
                k.doing = RIDE;
                float to = k.x;
                for (int tries = 0; tries < 10; tries++) {
                    to = clampX(rnd(500, 1300) * u, k);
                    if (Math.abs(to - k.x) > 140 * u && clear(to, k)) break;
                }
                k.toX = to;
            } else if (r < 72) { k.doing = ROCK; k.length = 1.8f; }
            else { k.doing = HOP; k.length = 0.65f; }
            return;
        }
        if (k.sits) {
            if (r < 45) { k.doing = WIGGLE; k.length = 1.3f; }
            else if (r < 75) { k.doing = HOP; k.length = 0.6f; }
            else { k.doing = SCOOT; k.toX = clampX(k.x + rnd(-110, 110) * u, k); }
            return;
        }
        if (r < 52) {
            k.doing = WALK;
            float to = k.x;
            for (int tries = 0; tries < 10; tries++) {
                to = clampX(rnd(500, 1300) * u, k);
                if (Math.abs(to - k.x) > 140 * u && clear(to, k)) break;
            }
            k.toX = to;
        } else if (r < 68) { k.doing = HOP; k.length = 0.7f; }
        else if (r < 80) { k.doing = SPIN; k.length = 0.9f; }
        else { k.doing = WIGGLE; k.length = 1.2f; }
    }

    private Kid mate(Kid k) {
        List<Kid> ready = new ArrayList<>();
        for (Kid o : kids) if (o != k && o.doing == STAND && Math.abs(o.x - k.x) > 150 * u && Math.abs(o.x - k.x) < 700 * u) ready.add(o);
        return ready.isEmpty() ? null : ready.get(random.nextInt(ready.size()));
    }

    private float clampX(float x, Kid k) {
        return Math.max(500 * u, Math.min(1300 * u, x));
    }

    /** Somewhere no other child is standing or heading. */
    private boolean clear(float x, Kid k) {
        for (Kid o : kids) if (o != k && (Math.abs(o.x - x) < 150 * u || Math.abs(o.toX - x) < 150 * u)) return false;
        return true;
    }

    private void moveBall(float dt) {
        ball.age += dt;
        if (ball.age < 0) return;
        float flight = 1.05f;
        if (ball.age <= flight) {
            float p = ball.age / flight;
            ball.x = ball.fromX + (ball.toX - ball.fromX) * p;
            ball.y = (float) Math.sin(Math.PI * p) * 120 * u;
            ball.spin += dt * 540f * Math.signum(ball.toX - ball.fromX);
            if (p >= 1f && ball.to != null) {
                // Caught at the feet: the one it came to jumps for joy.
                Kid to = ball.to;
                to.doing = to.sits ? WIGGLE : HOP;
                to.age = 0;
                to.length = to.sits ? 1.2f : 0.7f;
                ball.to = null;
            }
        } else {
            // Two little bounces, a rest, then gone.
            float q = ball.age - flight;
            ball.y = q < 0.5f ? Math.abs((float) Math.sin(Math.PI * q / 0.25f)) * 18 * u * (1f - q * 2f) : 0;
            ball.rest += dt;
            if (ball.rest > 2.2f) ball.alpha = Math.max(0f, ball.alpha - dt * 2f);
            if (ball.alpha <= 0f) ball.on = false;
        }
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    private void drawKid(Canvas c, Kid k) {
        if (k.pic == null || k.alpha <= 0.01f) return;
        float g = ground(), w = k.pic.getWidth(), hgt = k.pic.getHeight();
        boolean floating = where == FLOAT;
        // A shadow on the ground, smaller and fainter the higher they are.
        if (!floating || k.lift < 120 * u) {
            float far = Math.min(1f, k.lift / (140 * u));
            fill.setColor(Color.argb(Math.round(70 * (1f - far) * k.alpha), 0, 0, 0));
            float half = w * (k.rides ? 0.44f : k.sits ? 0.42f : 0.36f) * (1f - 0.45f * far);
            oval.set(k.x - half, g - 7 * u, k.x + half, g + 7 * u);
            c.drawOval(oval, fill);
        }
        int save = c.save();
        if (floating) {
            // Turning about its middle.
            c.translate(k.x, g - k.lift);
            c.rotate(k.rot);
            c.scale(k.face * k.sx * 0.86f, k.sy * 0.86f);
            c.translate(-w / 2f, -hgt / 2f);
        } else {
            // Rocking and squashing about its feet.
            c.translate(k.x, g - k.lift);
            c.rotate(k.rot);
            c.scale(k.face * k.sx, k.sy);
            c.translate(-w / 2f, -hgt);
        }
        paint.setAlpha(Math.round(255 * k.alpha));
        c.drawBitmap(k.pic, 0, 0, paint);
        c.restoreToCount(save);
    }

    private void drawBall(Canvas c) {
        float r = 17 * u, g = ground();
        fill.setColor(Color.argb(Math.round(60 * ball.alpha), 0, 0, 0));
        oval.set(ball.x - r * 0.9f, g - 5 * u, ball.x + r * 0.9f, g + 5 * u);
        c.drawOval(oval, fill);
        int save = c.save();
        c.translate(ball.x, g - r - ball.y);
        c.rotate(ball.spin);
        int a = Math.round(255 * ball.alpha);
        fill.setColor(Color.argb(a, 255, 255, 255));
        c.drawCircle(0, 0, r, fill);
        fill.setColor(Color.argb(a, 25, 25, 30));
        pentagon(c, 0, 0, r * 0.36f);
        for (int i = 0; i < 5; i++) {
            double ang = Math.toRadians(-90 + 72 * i);
            pentagon(c, (float) Math.cos(ang) * r * 0.86f, (float) Math.sin(ang) * r * 0.86f, r * 0.26f);
        }
        line.setColor(Color.argb(Math.round(a * 0.5f), 40, 40, 50));
        line.setStrokeWidth(1.6f * u);
        c.drawCircle(0, 0, r, line);
        c.restoreToCount(save);
    }

    private void pentagon(Canvas c, float x, float y, float r) {
        path.reset();
        for (int i = 0; i < 5; i++) {
            double ang = Math.toRadians(-90 + 72 * i);
            float qx = x + (float) Math.cos(ang) * r, qy = y + (float) Math.sin(ang) * r;
            if (i == 0) path.moveTo(qx, qy); else path.lineTo(qx, qy);
        }
        path.close();
        c.drawPath(path, fill);
    }

    private static final float[] BEAM_AT = {640, 1180};

    /** The dance party's two coloured spotlights, sweeping. */
    private void beams(Canvas c, float t) {
        float H = ground();
        float[] at = BEAM_AT;
        if (beamShade == null) {
            int[] cols = {0x38FF5EC8, 0x384CC9F0};
            beamShade = new Shader[2];
            for (int i = 0; i < 2; i++) beamShade[i] = new LinearGradient(0, 0, 0, H, cols[i], cols[i] & 0x00FFFFFF, Shader.TileMode.CLAMP);
        }
        for (int i = 0; i < 2; i++) {
            float ang = (float) Math.sin(t * 0.8f + i * 2.1f) * 24f, x = at[i] * u;
            int save = c.save();
            c.rotate(ang, x, -10 * u);
            path.reset();
            path.moveTo(x - 14 * u, -10 * u);
            path.lineTo(x + 14 * u, -10 * u);
            path.lineTo(x + 150 * u, H);
            path.lineTo(x - 150 * u, H);
            path.close();
            fill.setShader(beamShade[i]);
            c.drawPath(path, fill);
            fill.setShader(null);
            c.restoreToCount(save);
        }
    }

    /** The pieces: balloons and gulls behind the children (behind = true), the rest in front. */
    private void drawParticles(Canvas c, boolean behind) {
        for (int i = 0; i < count; i++) {
            boolean back = kind[i] == P_BALLOON || kind[i] == P_GULL || kind[i] == P_SPARK || kind[i] == P_SHOOT;
            if (back != behind) continue;
            float x = px[i], y = py[i], s = size[i], p = age[i] / life[i];
            switch (kind[i]) {
                case P_BALLOON: {
                    // The string first, waving, then the balloon with its shine and knot.
                    line.setColor(0x99FFFFFF);
                    line.setStrokeWidth(1.6f * u);
                    path.reset();
                    path.moveTo(x, y + s * 1.18f);
                    float wv = (float) Math.sin(age[i] * 3f + phase[i]) * 8 * u;
                    path.cubicTo(x + wv, y + s * 1.7f, x - wv, y + s * 2.2f, x + wv * 0.5f, y + s * 2.8f);
                    c.drawPath(path, line);
                    fill.setColor(colour[i]);
                    oval.set(x - s, y - s * 1.15f, x + s, y + s * 1.15f);
                    c.drawOval(oval, fill);
                    path.reset();
                    path.moveTo(x, y + s * 1.08f);
                    path.lineTo(x - s * 0.16f, y + s * 1.3f);
                    path.lineTo(x + s * 0.16f, y + s * 1.3f);
                    path.close();
                    c.drawPath(path, fill);
                    fill.setColor(0x66FFFFFF);
                    oval.set(x - s * 0.58f, y - s * 0.82f, x - s * 0.18f, y - s * 0.22f);
                    c.drawOval(oval, fill);
                    break;
                }
                case P_CONFETTI: {
                    int save = c.save();
                    c.translate(x, y);
                    c.rotate(spin[i]);
                    c.scale((float) Math.cos(age[i] * 7f + phase[i]), 1f);
                    fill.setColor(colour[i]);
                    c.drawRect(-s / 2f, -s * 0.8f, s / 2f, s * 0.8f, fill);
                    c.restoreToCount(save);
                    break;
                }
                case P_SPARK: {
                    // A four-pointed twinkle, growing and fading.
                    float a = (float) Math.sin(Math.PI * p);
                    float r = s * (0.5f + 0.5f * a);
                    fill.setColor(Color.argb(Math.round(230 * a), 255, 255, 255));
                    path.reset();
                    path.moveTo(x, y - r * 1.6f);
                    path.quadTo(x, y, x + r * 1.6f, y);
                    path.quadTo(x, y, x, y + r * 1.6f);
                    path.quadTo(x, y, x - r * 1.6f, y);
                    path.quadTo(x, y, x, y - r * 1.6f);
                    c.drawPath(path, fill);
                    fill.setColor(Color.argb(Math.round(110 * a), 255, 255, 255));
                    c.drawCircle(x, y, r * 0.55f, fill);
                    break;
                }
                case P_SHOOT: {
                    float a = p < 0.15f ? p / 0.15f : 1f - (p - 0.15f) / 0.85f;
                    if (tail == null) tail = new LinearGradient(0, 0, -s, 0, 0xF0FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
                    int save = c.save();
                    c.translate(x, y);
                    c.rotate((float) Math.toDegrees(Math.atan2(vy[i], vx[i])));
                    line.setShader(tail);
                    line.setStrokeWidth(3f * u);
                    line.setAlpha(Math.round(255 * a));
                    c.drawLine(0, 0, -s, 0, line);
                    line.setShader(null);
                    line.setAlpha(255);
                    fill.setColor(Color.argb(Math.round(255 * a), 255, 255, 255));
                    c.drawCircle(0, 0, 3.2f * u, fill);
                    c.restoreToCount(save);
                    break;
                }
                case P_GULL: {
                    // A gull's two curved wings, flapping now and then.
                    float flap = (float) Math.sin(age[i] * 5f + phase[i]) * 0.35f;
                    line.setColor(0xE6FFFFFF);
                    line.setStrokeWidth(2.6f * u);
                    path.reset();
                    path.moveTo(x - s, y - s * (0.2f + flap));
                    path.quadTo(x - s * 0.45f, y - s * (0.55f + flap), x, y);
                    path.quadTo(x + s * 0.45f, y - s * (0.55f + flap), x + s, y - s * (0.2f + flap));
                    c.drawPath(path, line);
                    break;
                }
                case P_NOTE: {
                    float a = p < 0.2f ? p / 0.2f : 1f - (p - 0.2f) / 0.8f;
                    fill.setColor((colour[i] & 0x00FFFFFF) | (Math.round(255 * a) << 24));
                    fill.setTextSize(s);
                    fill.setTextAlign(Paint.Align.CENTER);
                    c.drawText((i & 1) == 0 ? "♪" : "♫", x, y, fill);
                    break;
                }
                default:
                    break;
            }
        }
    }
}
