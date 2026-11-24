package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;

/**
 * 07's card: a rounded picture that lifts toward you, tilts with the remote and catches the light (the mock-ups' .card:
 * focused, scale 1.12, rotateX 5° and rotateY -7° seen from 1200 px, its picture drifting under its frame, a sheen of
 * light sweeping across it, a deep soft shadow, a hairline of light round it; its caption dropping 22 px and turning
 * white). How far lifted is a Lift07's value, so it follows 07's springy ease on Glide. One painter serves a row of
 * cards of one size; nothing is made a frame once each picture has been drawn once (Shaders). In Motion's Lite tier a
 * card lifts and grows the same but without the tilt in perspective, the sheen or the drift (the graphics chip's quick
 * path: a plain scale, no gradient over it, the picture still under its frame).
 *
 * <pre>
 *   Card07 cards = new Card07(Look07.R_CARD);                    // R_POSTER for posters; with the view
 *   Bitmap pic = Pics.get(url, cards.picW(w), cards.picH(h), Pics.CROP, redraw);   // read 14 px larger all round
 *   cards.draw(c, x, y, w, h, lift.of(item), pic, marks);         // marks: Card07.Marks (kept, filled per card) or null
 *   cards.caption(c, x, y + h, w, lift.of(item), title, sub);     // its words under it, dropping as it lifts
 *
 *   cards.begin(c, x, y, w, h, up, pic);  ...draw in (0, 0, w, h)...;  cards.end(c);   // your own marks on it
 * </pre>
 *
 * x, y, w, h are screen pixels. Without a picture the frame is the card's own colour (Look07.CARD_FRAME): draw a
 * stand-in between begin and end (PicTile, Logos), or leave it.
 */
final class Card07 {
    /** What sits on a card's picture: LIVE (its words: "LIVE · 75'", or "" for plain LIVE), a time (with a bell), a
     *  quality chip, NEW, TorBox's cloud, how far through, a rating. One kept by the view, cleared and filled for each
     *  card (with words kept by the caller: nothing made a frame). */
    static final class Marks {
        boolean live, bell, torbox;
        String liveText = "", time = "", quality = "", badge = "", rating = "";
        /** How far through (0 to 1), or below 0 for none. */
        float progress = -1f;

        Marks clear() {
            live = bell = torbox = false;
            liveText = time = quality = badge = rating = "";
            progress = -1f;
            return this;
        }
    }

    private final float radius;
    private final Shaders shaders = new Shaders();
    private final Matrix m = new Matrix();
    private final float[] tilt = new float[9];
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG),
            edge = new Paint(Paint.ANTI_ALIAS_FLAG), cloud = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final TextPaint titleP, subP;
    private final TextFit titles, subs;
    private float sheenW = -1f, sheenH = -1f;
    private float lifted;

    /** radius: design px (Look07.R_CARD for wide cards, R_POSTER for posters). */
    Card07(float radius) {
        this.radius = radius;
        frame.setColor(Look07.CARD_FRAME);
        edge.setStyle(Paint.Style.STROKE);
        cloud.setColor(0xFF1FAE5B);
        titleP = Type.paint(Type.CAPTION, Type.SEMIBOLD, Look07.L2);
        subP = Type.paint(Type.CAPTION_SUB, Type.MEDIUM, Look07.L3);
        titles = new TextFit(titleP, 256);
        subs = new TextFit(subP, 256);
        Pills.prepare();
        Shadows.prepare(Look07.SH_CARD, radius);
        Shadows.prepare(Look07.SH_CARD_UP, radius);
    }

    /** The size to read a card's picture at (screen px): its width with the drift's room all round. */
    int picW(float w) {
        return Math.round(w + 2 * Ui.f(Look07.CARD_BLEED));
    }

    int picH(float h) {
        return Math.round(h + 2 * Ui.f(Look07.CARD_BLEED));
    }

    /** A whole card: begin, its marks, end. */
    void draw(Canvas c, float x, float y, float w, float h, float up, Bitmap pic, Marks marks) {
        begin(c, x, y, w, h, up, pic);
        if (marks != null) marks(c, w, h, marks);
        end(c);
    }

    /**
     * The card's shadow, its tilt, its picture (drifting) and sheen; then the canvas is the card's own (0, 0, w, h),
     * moved a little as the marks on it move: draw them, then end(c).
     */
    void begin(Canvas c, float x, float y, float w, float h, float up, Bitmap pic) {
        float k = Math.max(-0.2f, Math.min(1.2f, up)), rad = Ui.f(radius);
        lifted = k;
        float grow = 1f + (Look07.CARD_SCALE - 1f) * k;
        // The shadow: resting, then deep and soft as it lifts (under the grown card).
        r.set(x + w / 2f - w * grow / 2f, y + h / 2f - h * grow / 2f, x + w / 2f + w * grow / 2f, y + h / 2f + h * grow / 2f);
        float kk = Math.max(0f, Math.min(1f, k));
        // The card is opaque (its frame under its picture): the resting shadow's hidden middle is left out.
        Shadows.under(c, r, rad * grow, Look07.SH_CARD, 1f - kk);
        Shadows.draw(c, r, rad * grow, Look07.SH_CARD_UP, kk);
        boolean lite = Motion.lite();
        c.save();
        c.translate(x, y);
        if (k != 0f && lite) {
            // Lite: lifted toward you without the tilt (a plain scale: the chip's quick path, words and edges crisp).
            c.scale(grow, grow, w / 2f, h / 2f);
        } else if (k != 0f) {
            // Lifted toward you, tilted 5° about its width and -7° about its height, seen from 1200 px.
            tilt(k, Ui.f(Look07.CARD_PERSPECTIVE), tilt);
            m.setValues(tilt);
            m.preScale(grow, grow);
            m.preTranslate(-w / 2f, -h / 2f);
            m.postTranslate(w / 2f, h / 2f);
            c.concat(m);
        }
        r.set(0, 0, w, h);
        if (pic != null) {
            // The picture, 14 px larger all round, drifting (-10, -4) under its still frame as it lifts.
            Paint p = shaders.paint(pic, w, h, Ui.f(Look07.CARD_BLEED));
            float dx = lite ? 0f : Ui.f(Look07.CARD_DRIFT_X) * k, dy = lite ? 0f : Ui.f(Look07.CARD_DRIFT_Y) * k;
            c.save();
            c.translate(dx, dy);
            c.drawRoundRect(-dx, -dy, w - dx, h - dy, rad, rad, p);
            c.restore();
        } else {
            c.drawRoundRect(r, rad, rad, frame);
        }
        if (kk > 0.004f && !lite) {
            // The sheen: light at 115°, sweeping from -30 % to 8 % of the width as it fades in.
            if (w != sheenW || h != sheenH) {
                sheenW = w;
                sheenH = h;
                double a = Math.toRadians(115 - 90);
                float len = (float) (Math.abs(w * Math.cos(a)) + Math.abs(h * Math.sin(a)));
                float cx = w / 2f, cy = h / 2f, ux = (float) Math.cos(a) * len / 2f, uy = (float) Math.sin(a) * len / 2f;
                sheen.setShader(new LinearGradient(cx - ux, cy - uy, cx + ux, cy + uy, new int[]{0x00FFFFFF, 0x42FFFFFF, 0x00FFFFFF},
                        new float[]{0.20f, 0.42f, 0.62f}, Shader.TileMode.CLAMP));
            }
            float sx = w * (-0.30f + 0.38f * kk);
            sheen.setAlpha(Math.round(255 * kk));
            c.save();
            c.translate(sx, 0);
            c.drawRoundRect(-sx, 0, w - sx, h, rad, rad, sheen);
            c.restore();
        }
        if (kk > 0.004f) {
            // A hairline of light round it.
            edge.setStrokeWidth(Math.max(1f, Ui.f(1f)));
            edge.setColor(Palette.alpha(Look07.EDGE, Math.round((Look07.EDGE >>> 24) * kk)));
            c.drawRoundRect(r, rad, rad, edge);
        }
        // The marks drift (7, -3) the other way (Full).
        if (!lite) c.translate(Ui.f(Look07.MARKS_DRIFT_X) * k, Ui.f(Look07.MARKS_DRIFT_Y) * k);
    }

    void end(Canvas c) {
        c.restore();
    }

    /** The mock-ups' marks on a card's picture, in its own coordinates (begin's). */
    void marks(Canvas c, float w, float h, Marks mk) {
        if (mk.live) Pills.liveOn(c, Ui.f(14f), h - Ui.f(16f) - Ui.f(15f), mk.liveText);
        if (!mk.time.isEmpty()) Pills.time(c, w - Ui.f(14f) - Pills.timeWidth(mk.time, mk.bell), Ui.f(14f) + Ui.f(15f), mk.time, mk.bell);
        if (!mk.quality.isEmpty()) Pills.quality(c, w - Ui.f(10f), Ui.f(10f), mk.quality);
        if (!mk.badge.isEmpty()) Pills.badge(c, Ui.f(10f), Ui.f(10f), mk.badge);
        if (mk.torbox) {
            float d = Ui.f(34f), cx = Ui.f(10f) + d / 2f, cy = h - Ui.f(10f) - d / 2f;
            c.drawCircle(cx, cy, d / 2f, cloud);
            Icons07.draw(c, Icons07.CLOUD, cx, cy, Ui.f(22f), Look07.L1);
        }
        if (!mk.rating.isEmpty()) Pills.rating(c, Ui.f(10f), h - Ui.f(12f) - Ui.f(13f), mk.rating);
        if (mk.progress >= 0f) Pills.progressOn(c, Ui.f(16f), h - Ui.f(9f) - Ui.f(5f), w - Ui.f(32f), mk.progress);
    }

    /** Its words under it (16 px below): the name (23 px, white as it lifts) and a quieter line; both drop 22 px lifted. */
    void caption(Canvas c, float x, float bottom, float w, float up, String title, String sub) {
        float k = Math.max(0f, Math.min(1.2f, up));
        float drop = Ui.f(Look07.CAPTION_DROP) * k, pad = Ui.f(4f), room = w - pad * 2;
        float top = bottom + Ui.f(Look07.CAPTION_GAP) + drop;
        if (title != null && !title.isEmpty()) {
            titleP.setColor(Theme.mix(Look07.L2, Look07.L1, Math.min(1f, k)) | 0xFF000000);
            titleP.setAlpha(Math.round(0xBD + (0xFF - 0xBD) * Math.min(1f, k)));
            titles.line(c, title, x + pad, top + titleP.getTextSize() * 0.93f, room);
        }
        if (sub != null && !sub.isEmpty()) {
            float y = top + titleP.getTextSize() * 1.2f + Ui.f(4f) + subP.getTextSize() * 0.93f;
            subs.line(c, sub, x + pad, y, room);
        }
    }

    /**
     * Pure: the card's tilt as a 3 x 3 matrix (out, Matrix.setValues' order) about its middle, as CSS draws
     * perspective(p) rotateX(5°·k) rotateY(-7°·k) (y down, z toward you): its top a little away, its right edge a little
     * toward you. Worked out here rather than with android.graphics.Camera, so it is the same everywhere.
     */
    static void tilt(float k, float p, float[] out) {
        double ax = Math.toRadians(Look07.CARD_TILT_X * k), ay = Math.toRadians(Look07.CARD_TILT_Y * k);
        float cx = (float) Math.cos(ax), sx = (float) Math.sin(ax), cy = (float) Math.cos(ay), sy = (float) Math.sin(ay);
        // (x, y, 0) turned by rotateX(ax) after rotateY(ay): x' = cy x, y' = sx sy x + cx y, z' = -cx sy x + sx y;
        // seen from p: (x', y') / (1 - z' / p).
        out[0] = cy;
        out[1] = 0f;
        out[2] = 0f;
        out[3] = sx * sy;
        out[4] = cx;
        out[5] = 0f;
        out[6] = cx * sy / p;
        out[7] = -sx / p;
        out[8] = 1f;
    }

    /** How lifted the card begun last was (for a caller drawing more on it). */
    float lifted() {
        return lifted;
    }
}
