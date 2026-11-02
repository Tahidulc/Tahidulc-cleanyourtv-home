package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The family's little ones on the Kids page, drawn as cartoons: each walks along the line above the tabs, stops,
 * blinks, waves at the room, now and then hops, turns round and walks back, and in time walks off the right edge for
 * the next one to come in, so every one of them has a turn. Their pictures are the family's own, kept on the TV only
 * (the theme folder's kids_buddy*.png, each a strip of 14 frames: eight steps of a walk, standing, a blink, four of a
 * wave), never in the launcher itself; with no such files there is no one. Only those on the page are read (their
 * pictures at the size they are drawn). They keep still a moment whenever a key is pressed, so the page stays smooth to
 * move around, and go at a gentler pace on a slower TV (lite_home).
 */
final class KidsBuddy extends View {
    private static final int FRAMES = 14, WALK = 0, STAND = 8, BLINK = 9, WAVE = 10;

    /** The pictures there are (the theme folder's kids_buddy*.png, in name order); empty: no one. */
    static List<File> cast(Context c) {
        File dir = Config.dir(c);
        File[] found = dir == null ? null : dir.listFiles((d, n) -> n.startsWith("kids_buddy") && n.endsWith(".png"));
        List<File> out = new ArrayList<>();
        if (found != null) { Arrays.sort(found); out.addAll(Arrays.asList(found)); }
        return out;
    }

    /** Which of the cast are on the page now, shared by everyone on it. */
    static final class Stage {
        final List<File> cast;
        final Set<Integer> on = new HashSet<>();
        int next;

        Stage(List<File> cast, int start) {
            this.cast = cast;
            next = start;
        }

        /** The next of the cast not already on the page. */
        int pick() {
            for (int i = 0; i < cast.size(); i++) {
                int k = (next + i) % cast.size();
                if (!on.contains(k)) { next = (k + 1) % cast.size(); on.add(k); return k; }
            }
            return -1;
        }
    }

    private final Stage stage;
    private int who = -1;
    private Bitmap sheet;
    private final float unit, minX, maxX, offX;
    private final boolean lite;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF(), ground = new RectF();

    /** 0 standing, 1 walking, 2 waving, 3 hopping, 4 leaving (walking off for the next to come in). */
    private int mode, frame = STAND, face = -1, step, waves;
    private float x, targetX, hop;
    private long hushedUntil, nextChange, turnEnds;
    private boolean running;

    /**
     * @param unit  screen pixels to a design pixel
     * @param minX  how far left he may walk (screen px, his left edge), maxX how far right
     * @param delay how long after the page opens he walks in (they come in one after another)
     */
    KidsBuddy(Context c, Stage stage, float unit, float minX, float maxX, boolean lite, long delay) {
        super(c);
        this.stage = stage;
        this.unit = unit;
        this.minX = minX;
        this.maxX = maxX;
        this.lite = lite;
        offX = maxX + 300 * unit;
        shadow.setColor(0x40000000);
        x = offX;
        setTranslationX(x);
        mode = 0;
        nextChange = SystemClock.uptimeMillis() + delay;
        comeIn();
    }

    void start() {
        if (running) return;
        running = true;
        main.post(tick);
    }

    void stop() {
        running = false;
        main.removeCallbacksAndMessages(null);
        if (who >= 0) stage.on.remove(who);
    }

    /** A key was pressed on the page: still a moment, so the page moves without him costing a frame. */
    void hush() {
        hushedUntil = SystemClock.uptimeMillis() + 1400;
    }

    /** The next of the cast, read at the size he is drawn, walking in from the right. */
    private void comeIn() {
        int was = who;
        who = stage.pick();
        if (was >= 0) stage.on.remove(was);
        sheet = who < 0 ? null : read(stage.cast.get(who));
        x = offX;
        setTranslationX(x);
        face = -1;
        targetX = minX + random.nextFloat() * (maxX - minX);
        turnEnds = SystemClock.uptimeMillis() + (lite ? 70_000 : 50_000) + random.nextInt(30_000);
        invalidate();
    }

    private Bitmap read(File f) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (b == null || b.getWidth() < FRAMES) return null;
            // Kept at the size he is drawn, not the file's: six of them at full size were more than a TV should hold.
            int fw = Math.max(1, Math.round(164 * unit)), fh = Math.max(1, Math.round(214 * unit));
            if (b.getWidth() != fw * FRAMES || b.getHeight() != fh) {
                Bitmap s = Bitmap.createScaledBitmap(b, fw * FRAMES, fh, true);
                if (s != b) b.recycle();
                b = s;
            }
            return b;
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = SystemClock.uptimeMillis();
            if (now >= hushedUntil && sheet != null) step(now);
            main.postDelayed(this, lite ? 125 : 90);
        }
    };

    private void step(long now) {
        int was = frame;
        float wasX = x, wasHop = hop;
        switch (mode) {
            case 1:
            case 4: {   // walking (or leaving), a waddle
                float speed = (lite ? 15f : 11f) * unit;
                float to = mode == 4 ? offX : targetX;
                float d = to - x;
                if (Math.abs(d) <= speed) {
                    x = to;
                    if (mode == 4) { comeIn(); mode = 1; break; }
                    standFor(now);
                    break;
                }
                face = d > 0 ? 1 : -1;
                x += Math.signum(d) * speed;
                step = (step + 1) % 8;
                frame = WALK + step;
                break;
            }
            case 2: {   // waving, a few times
                step = (step + 1) % 4;
                frame = WAVE + step;
                if (step == 3 && ++waves >= 3) standFor(now);
                break;
            }
            case 3: {   // a little hop on the spot
                step++;
                hop = (float) Math.sin(Math.PI * Math.min(1f, step / 6f)) * 34f * unit;
                frame = STAND;
                if (step >= 6) { hop = 0; standFor(now); }
                break;
            }
            default: {  // standing (or waiting to come in): blinking now and then, until something next
                frame = frame == BLINK ? STAND : random.nextInt(lite ? 18 : 26) == 0 ? BLINK : STAND;
                if (now >= nextChange) next(now);
            }
        }
        if (x != wasX) setTranslationX(x);
        if (hop != wasHop) setTranslationY(-hop);
        if (frame != was || x != wasX) invalidate();
    }

    private void standFor(long now) {
        mode = 0;
        step = 0;
        frame = STAND;
        // Mostly standing still (on a slower TV longer still): the page is not kept busy drawing them.
        nextChange = now + (lite ? 7000 : 4500) + random.nextInt(lite ? 7000 : 5000);
    }

    private void next(long now) {
        step = 0;
        if (x >= offX - 1) { mode = 1; return; }   // his first walk in
        // His turn over (when others are waiting): off he goes, and the next one comes in.
        if (now >= turnEnds && stage.cast.size() > stage.on.size()) { mode = 4; return; }
        int r = random.nextInt(10);
        if (r < 5) {
            mode = 1;
            float to;
            int tries = 0;
            do { to = minX + random.nextFloat() * (maxX - minX); } while (Math.abs(to - x) < 140 * unit && ++tries < 8);
            targetX = to;
        } else if (r < 8) {
            mode = 2;
            waves = 0;
            face = 1;
        } else {
            mode = 3;
        }
    }

    @Override protected void onDraw(Canvas c) {
        if (sheet == null) return;
        float w = getWidth(), h = getHeight();
        int fw = sheet.getWidth() / FRAMES, fh = sheet.getHeight();
        // A shadow on the ground, smaller as he hops.
        float k = 1f - Math.min(0.5f, hop / (80f * unit));
        ground.set(w * (0.5f - 0.3f * k), h * 0.955f - 7 * unit, w * (0.5f + 0.3f * k), h * 0.955f + 7 * unit);
        int save = c.save();
        c.translate(0, hop);
        c.drawOval(ground, shadow);
        c.restoreToCount(save);
        src.set(frame * fw, 0, frame * fw + fw, fh);
        dst.set(0, 0, w, h);
        save = c.save();
        if (face < 0) c.scale(-1, 1, w / 2f, h / 2f);
        c.drawBitmap(sheet, src, dst, paint);
        c.restoreToCount(save);
    }
}
