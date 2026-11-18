package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;

import com.gathin.homefieldtv.sports.Squad;

/**
 * A player's (or a manager's) real picture on 07's card, the family's change: never a drawn shirt, body or silhouette.
 * The best picture there is (Squad.photos, the phone's order): the league's own cut-out (the Premier League's or La
 * Liga's, head to waist in the club's kit) or ESPN's head and shoulders rises out of the card's top, as the mock-ups'
 * people do, kept within the card's sides, its foot fading into the glass so his name reads under it; a face alone
 * (FotMob's) is a portrait inside the card, filling its top and cut to its shape, its neck fading away; with no picture
 * at all, the club's crest large on the card and his initials under it.
 *
 * <p>The card's own units (screen px at the card's full size; a smaller card is drawn scaled): its width w, its glass
 * panel from top to top + h, rounded by radius. One PlayerArt per card size; a Pic per person, kept by the page, holds the
 * picture's shader (made when the picture comes, never a frame). Nothing here fetches: the page asks Pics for the
 * picture at width(kind) by height(kind), and draws it with draw().
 */
final class PlayerArt {
    /** The card: its width, its panel's top and height, its corners (screen px at full size). */
    final float w, top, h, radius;
    /** The rising cut-outs' heads come up this far over the panel's top (a share of the card's width), as the mock-ups'. */
    static final float RISE = 0.13f;
    /** How wide each kind of picture is drawn (shares of the card's width), and where its head's top is in it. */
    static final float LEAGUE_W = 1.5f, LEAGUE_CROWN = 0.012f, SHOULDERS_W = 1.7f, SHOULDERS_CROWN = 0.10f, ESPN_ASPECT = 436f / 600f;
    private final Path rising = new Path(), panel = new Path();
    private final Paint crestP = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint initialsP;
    private final RectF r = new RectF();

    PlayerArt(float w, float top, float h, float radius) {
        this.w = w;
        this.top = top;
        this.h = h;
        this.radius = radius;
        // The rising pictures show above the panel within the card's sides, and over the panel down to its rounded foot.
        rising.addRect(0, top - w * 2f, w, top + radius, Path.Direction.CW);
        rising.addRoundRect(new RectF(0, top, w, top + h), radius, radius, Path.Direction.CW);
        panel.addRoundRect(new RectF(0, top, w, top + h), radius, radius, Path.Direction.CW);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Math.max(1f, w * 0.008f));
        ring.setColor(0x47FFFFFF);
        initialsP = Cine.text(1f, Type.face(Type.EXTRABOLD, 60f), 0xFFFFFFFF);
        initialsP.setTextSize(w * 0.2f);
        initialsP.setLetterSpacing(0.02f);
        initialsP.setShadowLayer(Math.max(1f, w * 0.02f), 0, w * 0.006f, 0x66000000);
    }

    /** One person's picture as drawn: the addresses to try, the one drawn, and its shader. */
    static final class Pic {
        final Squad.Person person;
        /** The real pictures of him, best first (Squad.photos), worked out again when his league's one is known. */
        String[] urls;
        String initials;
        private Bitmap made;
        private int madeKind = -1;
        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

        Pic(Squad.Person p) {
            person = p;
            urls = Squad.photos(p);
            initials = Squad.initials(p.name);
        }

        /** His league's picture arrived (or none did): the pictures to try again. */
        void again() {
            urls = Squad.photos(person);
        }

        /** The picture to draw now: the first not failed (null while waiting for the league's, or none at all). */
        String url() {
            for (String u : urls) if (!Pics.failed(u)) return u;
            return null;
        }
    }

    /** The size (screen px) to read a picture of a kind at, for this card (Pics.get's w and h). */
    int width(int kind) {
        return kind == Squad.LEAGUE ? Math.round(w * LEAGUE_W) : kind == Squad.SHOULDERS ? Math.round(w * SHOULDERS_W) : Math.round(w);
    }

    int height(int kind) {
        return kind == Squad.LEAGUE ? Math.round(w * LEAGUE_W) : kind == Squad.SHOULDERS ? Math.round(w * SHOULDERS_W * ESPN_ASPECT) : Math.round(w);
    }

    /**
     * Where a picture of a kind sits on the card (out: left, top, right, bottom, then the fade's top and foot), in the
     * card's units: the rising ones with the head RISE over the panel; a face filling the panel's top.
     */
    void place(int kind, float aspect, float[] out) {
        float pw, ph, py;
        switch (kind) {
            case Squad.LEAGUE:
                pw = w * LEAGUE_W;
                ph = pw / Math.max(0.2f, aspect);
                py = top - w * RISE - ph * LEAGUE_CROWN;
                out[4] = top + h * 0.56f;
                out[5] = top + h * 0.90f;
                break;
            case Squad.SHOULDERS:
                pw = w * SHOULDERS_W;
                ph = pw / Math.max(0.2f, aspect);
                py = top - w * RISE - ph * SHOULDERS_CROWN;
                out[4] = Math.min(top + h * 0.56f, py + ph * 0.66f);
                out[5] = Math.min(top + h * 0.90f, py + ph * 0.98f);
                break;
            default:
                pw = w;
                ph = pw / Math.max(0.2f, aspect);
                py = top;
                out[4] = py + ph * 0.62f;
                out[5] = py + ph * 0.97f;
                break;
        }
        out[0] = (w - pw) / 2f;
        out[1] = py;
        out[2] = out[0] + pw;
        out[3] = py + ph;
    }

    private final float[] at = new float[6];
    private final Matrix m = new Matrix();

    /**
     * His picture b (of its kind) on the card, at an opacity: its shader made the first time this picture is drawn (the
     * fade, the place), then each frame one path filled. The canvas is the card's own (scaled for a smaller card).
     */
    void draw(Canvas c, Pic pic, Bitmap b, int kind, float alpha) {
        if (b == null || alpha <= 0.004f) return;
        if (pic.made != b || pic.madeKind != kind) {
            pic.made = b;
            pic.madeKind = kind;
            place(kind, b.getWidth() / (float) Math.max(1, b.getHeight()), at);
            BitmapShader bs = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            m.setScale((at[2] - at[0]) / b.getWidth(), (at[3] - at[1]) / b.getHeight());
            m.postTranslate(at[0], at[1]);
            bs.setLocalMatrix(m);
            LinearGradient fade = new LinearGradient(0, at[4], 0, at[5], 0xFF000000, 0x00000000, Shader.TileMode.CLAMP);
            pic.paint.setShader(new ComposeShader(bs, fade, PorterDuff.Mode.DST_IN));
        }
        pic.paint.setAlpha(Math.round(255 * Math.min(1f, alpha)));
        c.drawPath(kind == Squad.LEAGUE || kind == Squad.SHOULDERS ? rising : panel, pic.paint);
    }

    /**
     * No picture of him at all: the club's crest large on the card (crest null while it loads: a soft ring in its place)
     * and his initials under it. Never a drawn person.
     */
    void mark(Canvas c, Bitmap crest, String initials, float alpha) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        if (a <= 0) return;
        float side = w * 0.5f, cx = w / 2f, ct = top + w * 0.1f;
        if (crest != null && crest.getWidth() > 0 && crest.getHeight() > 0) {
            float k = Math.min(side / crest.getWidth(), side / crest.getHeight());
            float bw = crest.getWidth() * k, bh = crest.getHeight() * k;
            r.set(cx - bw / 2f, ct + (side - bh) / 2f, cx + bw / 2f, ct + (side + bh) / 2f);
            crestP.setAlpha(a);
            c.drawBitmap(crest, null, r, crestP);
        } else {
            ring.setAlpha(Math.round(a * 0.28f));
            c.drawCircle(cx, ct + side / 2f, side * 0.42f, ring);
        }
        if (initials != null && !initials.isEmpty()) {
            initialsP.setAlpha(Math.round(a * 0.9f));
            c.drawText(initials, cx - initialsP.measureText(initials) / 2f, ct + side + w * 0.24f, initialsP);
        }
    }

    /** The crest's size to read it at (screen px), for mark(). */
    int crestSize() {
        return Math.max(1, Math.round(w * 0.5f));
    }
}
