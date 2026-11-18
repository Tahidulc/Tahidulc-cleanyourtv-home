package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.media.FaceDetector;

/**
 * Faces in a programme's picture, so the family "can see faces clearly": found once per picture as it is read (Pics,
 * off the main thread: Android's own FaceDetector on a small 16-bit copy, nothing new to fetch), kept with the picture
 * as a box (shares of its width and height), and each tile's crop worked out around them so they are whole, never cut
 * by an edge. A picture with no face found keeps its upper middle (FALLBACK).
 *
 * <pre>
 *   float[] box = Faces.find(bitmap)                      off the main thread: {left, top, right, bottom} (0-1)
 *   Faces.crop(sw, sh, aspect, box, bias, out)            pure: the crop window (source px) at an aspect, the faces
 *                                                         whole, their middle bias of the way across where it can be
 *   Faces.place(sw, sh, aspect, box, bias, w, h, out)     pure: where the faces land in a w x h picture so cropped
 * </pre>
 */
final class Faces {
    private Faces() {}

    /** No face found: the upper middle of the picture, where people usually are. */
    static final float[] FALLBACK = {0.30f, 0.12f, 0.70f, 0.58f};
    /** The width the picture is looked at (an even number, as FaceDetector wants), and the most faces looked for. */
    static final int LOOK_W = 320, MAX = 6;
    /** Room kept round the faces in a crop (a share of the window). */
    static final float MARGIN = 0.06f;

    /** The faces' box in a picture (shares of its size), or FALLBACK; any thread but the main one. */
    static float[] find(Bitmap src) {
        if (src == null || src.getWidth() < 16 || src.getHeight() < 16) return FALLBACK;
        Bitmap look = null;
        try {
            int w = Math.min(LOOK_W, src.getWidth()) & ~1, h = Math.max(2, Math.round(src.getHeight() * (w / (float) src.getWidth())));
            if (src.getConfig() == Bitmap.Config.RGB_565 && src.getWidth() == w && src.getHeight() == h) {
                look = src;
            } else {
                Bitmap scaled = Bitmap.createScaledBitmap(src, w, h, true);
                look = scaled.copy(Bitmap.Config.RGB_565, false);
                if (scaled != src) scaled.recycle();
            }
            FaceDetector.Face[] faces = new FaceDetector.Face[MAX];
            int n = new FaceDetector(look.getWidth(), look.getHeight(), MAX).findFaces(look, faces);
            float l = 1f, t = 1f, r = 0f, b = 0f;
            int kept = 0;
            PointF mid = new PointF();
            for (int i = 0; i < n; i++) {
                FaceDetector.Face f = faces[i];
                if (f == null || f.confidence() < 0.3f) continue;
                f.getMidPoint(mid);
                float e = f.eyesDistance();
                // A face round its eyes: its width, its forehead and hair, its chin.
                l = Math.min(l, (mid.x - 1.25f * e) / look.getWidth());
                r = Math.max(r, (mid.x + 1.25f * e) / look.getWidth());
                t = Math.min(t, (mid.y - 1.4f * e) / look.getHeight());
                b = Math.max(b, (mid.y + 2.0f * e) / look.getHeight());
                kept++;
            }
            if (kept == 0) return FALLBACK;
            return new float[]{Math.max(0f, l), Math.max(0f, t), Math.min(1f, r), Math.min(1f, b)};
        } catch (RuntimeException | LinkageError e) {
            // No detector here (some builds, the tests): the upper middle.
            return FALLBACK;
        } finally {
            if (look != null && look != src) look.recycle();
        }
    }

    /**
     * Pure: the largest window of the picture (sw x sh) at aspect (width / height) holding the faces' box (shares) whole
     * with a little room, its middle bias of the way across the window where the picture allows (0.5 centred; 0.62
     * leaves the left for words), a little above the middle down. out: source pixels.
     */
    static void crop(float sw, float sh, float aspect, float[] box, float bias, RectF out) {
        float[] f = box == null ? FALLBACK : box;
        float wc = sw, hc = sw / Math.max(0.01f, aspect);
        if (hc > sh) {
            hc = sh;
            wc = sh * aspect;
        }
        float fl = f[0] * sw, ft = f[1] * sh, fr = f[2] * sw, fb = f[3] * sh;
        float fx = (fl + fr) / 2f, fy = (ft + fb) / 2f, mx = MARGIN * wc, my = MARGIN * hc;
        float x = fx - bias * wc, y = fy - 0.42f * hc;
        // The faces whole, where the window can hold them.
        if (fr - fl + 2 * mx <= wc) {
            if (x > fl - mx) x = fl - mx;
            if (x + wc < fr + mx) x = fr + mx - wc;
        } else {
            x = fx - wc / 2f;
        }
        if (fb - ft + 2 * my <= hc) {
            if (y > ft - my) y = ft - my;
            if (y + hc < fb + my) y = fb + my - hc;
        } else {
            y = ft - my;
        }
        x = Math.max(0f, Math.min(sw - wc, x));
        y = Math.max(0f, Math.min(sh - hc, y));
        out.set(x, y, x + wc, y + hc);
    }

    /** Pure: where the faces land (out, px) in a w x h picture cropped from sw x sh as crop() does. */
    static void place(float sw, float sh, float aspect, float[] box, float bias, float w, float h, RectF out) {
        float[] f = box == null ? FALLBACK : box;
        crop(sw, sh, aspect, f, bias, out);
        float k = w / Math.max(1f, out.width()), x0 = out.left, y0 = out.top;
        out.set((f[0] * sw - x0) * k, (f[1] * sh - y0) * k, (f[2] * sw - x0) * k, (f[3] * sh - y0) * k);
        out.left = Math.max(0f, out.left);
        out.top = Math.max(0f, out.top);
        out.right = Math.min(w, out.right);
        out.bottom = Math.min(h, out.bottom);
    }
}
