package com.gathin.homefieldtv;

import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import com.gathin.homefieldtv.engine.Exec;

/**
 * 07's frosted materials, faked: never a blur while drawing. A frosted panel is the page's own pre-blurred picture
 * (Ground, the one on screen) drawn inside its rounded shape, under the material's tinted translucent fill, with a line
 * of light along its top and a soft shadow under it, as the mock-ups' .glass, .mat and .mat-dark. Where there is no
 * blurred picture behind (over the video, or a screen with no Ground) it is the plain dark material: the same fill made
 * solid over the 07 ground, so what is under it never shows through.
 *
 * <pre>
 *   Frost menu = new Frost(Look07.MAT_DARK);                       one per panel, made with its view
 *   menu.draw(c, rect, Ui.f(Look07.R_MENU), 1f)                    the panel (screen px), its opacity last
 *   menu.sheet(c, x, top, w, getHeight(), Ui.f(Look07.R_SHEET), 1f)   a bottom sheet from x, w wide, risen to top
 *                                                                  (screen px), rounded above only
 *   Frost.Rise rise = new Frost.Rise();  rise.to(true)             its rise on Glide; each frame rise.step(), then
 *                                                                  Ui.f(rise.top(sheetH)) is its top
 *   Frost.glass(c, rect, radius, 1f)                               the glass of the tab bar and the pills (no blur)
 * </pre>
 *
 * The material's fill is a gradient made when the panel's height changes (not each frame). In Motion's Lite tier, while
 * the ground shows its picture's colour (the remote moving), a panel has that colour behind its fill rather than a new
 * picture each move.
 */
final class Frost {
    /** The mock-ups' materials: Look07.GLASS, MAT or MAT_DARK. */
    final int kind;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), behind = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG),
            solid = new Paint(Paint.ANTI_ALIAS_FLAG), light = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
            tinted = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float madeFor = -1f;
    private final RectF r = new RectF();
    private final float[] shadow;

    Frost(int kind) {
        this.kind = Math.max(Look07.GLASS, Math.min(Look07.MAT_DARK, kind));
        light.setStyle(Paint.Style.STROKE);
        ring.setStyle(Paint.Style.STROKE);
        shadow = kind == Look07.GLASS ? Look07.SH_GLASS : Look07.SH_PANEL;
        Shadows.prepare(shadow, kind == Look07.GLASS ? Look07.R_BAR : Look07.R_MENU);
    }

    private float lightFor = -1f;

    /** The fill's gradient for a panel this tall (screen px), made when it changes (glass's never: it is one colour). */
    private void made(float h) {
        if (lightFor != Ui.s) {
            lightFor = Ui.s;
            // The light along the top, fading down its first 48 px (the corners' curve with it).
            light.setShader(new LinearGradient(0, 0, 0, Ui.f(48f), Look07.MAT_HIGHLIGHT[kind], 0, Shader.TileMode.CLAMP));
            if (kind == Look07.GLASS) {
                fill.setShader(null);
                fill.setColor(Look07.MAT_TOP[kind]);
            }
        }
        if (kind == Look07.GLASS || h == madeFor) return;
        madeFor = h;
        int top = Look07.MAT_TOP[kind], foot = Look07.MAT_FOOT[kind];
        fill.setShader(new LinearGradient(0, 0, 0, Math.max(1f, h), top, foot, Shader.TileMode.CLAMP));
        // The plain dark material: the same, solid over the ground.
        solid.setShader(new LinearGradient(0, 0, 0, Math.max(1f, h), Palette.over(Look07.GROUND, top), Palette.over(Look07.GROUND, foot),
                Shader.TileMode.CLAMP));
    }

    /** The frosted panel r (screen px) rounded by radius, at an opacity (0 to 1: it fading in). */
    void draw(Canvas c, RectF r, float radius, float alpha) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        if (a <= 0) return;
        // A panel (not glass) is opaque once whole: its shadow's middle, hidden under it, is left out.
        if (kind != Look07.GLASS && a >= 255) Shadows.under(c, r, radius, shadow, alpha);
        else Shadows.draw(c, r, radius, shadow, alpha);
        c.save();
        c.translate(r.left, r.top);
        made(r.height());
        this.r.set(0, 0, r.width(), r.height());
        Ground g = Ground.current();
        boolean onGround = g != null && kind != Look07.GLASS && g.showing();
        // Lite while the remote moves: the ground's colour behind the fill (nothing sampled; the picture once it rests).
        int tint = onGround ? g.tintBehind() : 0;
        BitmapShader under = onGround && tint == 0 ? g.shader() : null;
        if (tint != 0) {
            tinted.setColor(tint);
            tinted.setAlpha(a);
            c.drawRoundRect(this.r, radius, radius, tinted);
            fill.setAlpha(a);
            c.drawRoundRect(this.r, radius, radius, fill);
        } else if (under != null) {
            // The ground's blurred picture, where it lies on the screen (the canvas is at the panel's corner).
            c.save();
            c.translate(-r.left, -r.top);
            behind.setShader(under);
            behind.setAlpha(a);
            c.drawRoundRect(r, radius, radius, behind);
            c.restore();
            fill.setAlpha(a);
            c.drawRoundRect(this.r, radius, radius, fill);
        } else if (kind == Look07.GLASS) {
            int g0 = Look07.MAT_TOP[Look07.GLASS];
            fill.setColor(Palette.alpha(g0, (g0 >>> 24) * a / 255));
            c.drawRoundRect(this.r, radius, radius, fill);
        } else {
            solid.setAlpha(a);
            c.drawRoundRect(this.r, radius, radius, solid);
        }
        edges(c, radius, a);
        c.restore();
    }

    /** Its light along the top (inset 0 1px 0) and, glass, its hairline ring; the canvas at the panel's corner. */
    private void edges(Canvas c, float radius, int a) {
        float sw = Math.max(1f, Ui.f(1.5f));
        light.setStrokeWidth(sw);
        light.setAlpha(a);
        r.inset(sw / 2f, sw / 2f);
        c.drawRoundRect(r, radius, radius, light);
        int ringColour = Look07.MAT_RING[kind];
        if (ringColour != 0) {
            ring.setStrokeWidth(Math.max(1f, Ui.f(1f)));
            ring.setColor(Palette.alpha(ringColour, (ringColour >>> 24) * a / 255));
            c.drawRoundRect(r, radius, radius, ring);
        }
        r.inset(-sw / 2f, -sw / 2f);
    }

    /**
     * A bottom sheet: the panel risen from the screen's foot to top (screen px), w wide from x, rounded above only
     * (its foot runs off the screen), at an opacity.
     */
    void sheet(Canvas c, float x, float top, float w, float screenH, float radius, float alpha) {
        r.set(x, top, x + w, screenH + radius);
        // Drawn as a panel whose foot is past the screen's edge: only its top corners show.
        RectF box = SHEET;
        box.set(r);
        draw(c, box, radius, alpha);
    }

    private static final RectF SHEET = new RectF();

    // ---- glass (the tab bar, the pills): no blur, a light translucent fill --------------------------------------------

    private static final Frost GLASS = new Frost(Look07.GLASS);

    /** The mock-ups' .glass in r (screen px): white at 12 %, its top lit, a hairline ring, a soft shadow. */
    static void glass(Canvas c, RectF r, float radius, float alpha) {
        GLASS.draw(c, r, radius, alpha);
    }

    /**
     * A sheet's place as it rises (Glide: the panels' motion): from the screen's foot (hidden) to top. Kept with the view:
     * new Rise() and rise.to(shown) on open and close; rise.step() each frame (true: draw again); rise.top(heightDesign)
     * where its top is now (design px).
     */
    static final class Rise {
        private final Glide g = new Glide(Look07.LIFT_MS, 100f).springy(0.92f, 0.8f);

        void to(boolean shown) {
            if (Exec.inline) g.jump(shown ? 1f : 0f);
            else g.to(shown ? 1f : 0f);
        }

        /** True while it still moves (draw again next frame). */
        boolean step() {
            return g.step();
        }

        /** How far up it is (0 hidden, 1 risen). */
        float shown() {
            return g.value();
        }

        /** Its top (design px) for a sheet h tall on a 1080 screen. */
        float top(float h) {
            return 1080f - h * g.value();
        }
    }
}
