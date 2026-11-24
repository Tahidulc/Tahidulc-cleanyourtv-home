package com.gathin.tvhomephone.live;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.media.FaceDetector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Faces in a programme's picture, so they are seen whole and clear (after Homefield TV's Faces): found once per picture
 * as it is read (ProgPics, off the main thread: Android's own FaceDetector on a small 16-bit copy, nothing to fetch),
 * kept by its address as a box (shares of its width and height). A picture with no face found keeps FALLBACK, its upper
 * middle, and PicFill takes it as none (then there is nothing to keep whole).
 */
final class Faces {
    private Faces() {}

    /** No face found: the upper middle of the picture, where people usually are. */
    static final float[] FALLBACK = {0.30f, 0.12f, 0.70f, 0.58f};
    /** The width the picture is looked at (an even number, as FaceDetector wants), and the most faces looked for. */
    static final int LOOK_W = 320, MAX = 6;

    /** Each picture's box as found, by its address (a few hundred: a screen's worth and more). */
    private static final Map<String, float[]> FOUND = Collections.synchronizedMap(new LinkedHashMap<String, float[]>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, float[]> e) {
            return size() > 600;
        }
    });

    /** A picture's faces given straight in (previews and tests: no detector off a phone). */
    static void known(String url, float[] box) {
        FOUND.put(url, box);
    }

    /** The faces' box for a picture read from url: found once, then kept. Not the main thread. */
    static float[] of(String url, Bitmap src) {
        float[] had = FOUND.get(url);
        if (had != null) return had;
        float[] f = find(src);
        FOUND.put(url, f);
        return f;
    }

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
            // No detector here (some builds, the tests): none found.
            return FALLBACK;
        } finally {
            if (look != null && look != src) look.recycle();
        }
    }
}
