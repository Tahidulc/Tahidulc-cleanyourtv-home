package com.gathin.homefieldtv.prefs;

/**
 * Where the picture goes in the space it has, by its shape (Settings → Player → Picture, or the player's Display mode):
 * Normal (FIT) keeps it whole with bars, Full (FILL) stretches it, Zoom fills the space and cuts its edges, 16:9, 4:3 and
 * 2.35:1 show it as that shape (bars where the space differs), Zoom - 2.35:1 takes a wide film inside a 16:9 picture
 * up to the space's height (its sides cut off), Reduced is Normal at nine tenths. Pure.
 */
public final class Picture {
    private Picture() {}

    /**
     * {left, top, width, height} of the picture in the space (left, top, width, height), for a picture of videoW by
     * videoH (its pixels already made square); the whole space when its size isn't known yet.
     */
    public static int[] place(int mode, int videoW, int videoH, int left, int top, int width, int height) {
        if (width <= 0 || height <= 0) return new int[]{left, top, Math.max(0, width), Math.max(0, height)};
        if (mode == PlayerPrefs.REDUCED || mode == PlayerPrefs.ZOOM_235) {
            int[] fit = place(PlayerPrefs.FIT, videoW, videoH, left, top, width, height);
            float k;
            if (mode == PlayerPrefs.REDUCED) {
                k = 0.9f;
            } else {
                // The film's own band is the picture's width over 2.35: grown until it is the space's height.
                float band = fit[2] / 2.35f;
                k = band >= height ? 1f : height / band;
            }
            int w = Math.round(fit[2] * k), h = Math.round(fit[3] * k);
            return new int[]{left + (width - w) / 2, top + (height - h) / 2, w, h};
        }
        float va;
        switch (mode) {
            case PlayerPrefs.FILL:
                return new int[]{left, top, width, height};
            case PlayerPrefs.WIDE:
                va = 16f / 9f;
                break;
            case PlayerPrefs.STANDARD:
                va = 4f / 3f;
                break;
            case PlayerPrefs.CINEMA:
                va = 2.35f;
                break;
            default:
                if (videoW <= 0 || videoH <= 0) return new int[]{left, top, width, height};
                va = videoW / (float) videoH;
        }
        float ra = width / (float) height;
        if (Math.abs(va - ra) <= 0.03f) return new int[]{left, top, width, height};
        boolean cover = mode == PlayerPrefs.ZOOM;
        int w = width, h = height;
        // Fit: the picture inside the space; Zoom: the space inside the picture.
        if ((va > ra) != cover) h = Math.round(width / va);
        else w = Math.round(height * va);
        return new int[]{left + (width - w) / 2, top + (height - h) / 2, w, h};
    }
}
