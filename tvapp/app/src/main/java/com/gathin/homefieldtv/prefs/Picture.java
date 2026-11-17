package com.gathin.homefieldtv.prefs;

/**
 * Where the picture goes in the space it has, by its shape (Settings → Player → Picture, or the player's menu): Fit
 * keeps it whole with bars, Fill stretches it, Zoom fills the space and cuts its edges, 16:9 and 4:3 show it as that
 * shape (bars where the space differs). Pure.
 */
public final class Picture {
    private Picture() {}

    /**
     * {left, top, width, height} of the picture in the space (left, top, width, height), for a picture of videoW by
     * videoH (its pixels already made square); the whole space when its size isn't known yet.
     */
    public static int[] place(int mode, int videoW, int videoH, int left, int top, int width, int height) {
        if (width <= 0 || height <= 0) return new int[]{left, top, Math.max(0, width), Math.max(0, height)};
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
