package com.gathin.tvhome;

/**
 * The geometry shared by lite_home's baked background (HomeV2.rebake) and the rounded posters (PagesV2.RoundPic): a
 * picture centre-cropped into a box exactly as ImageView's CENTER_CROP does it (scaled to cover, centred, the offset
 * rounded to whole pixels), so a baked picture lines up with the view it stands in for. Plain Java, unit tested.
 */
final class BakedHero {
    private BakedHero() {
    }

    /** {scale, dx, dy} for a bw by bh picture cropped into a vw by vh box. */
    static float[] centreCrop(int bw, int bh, int vw, int vh) {
        if (bw <= 0 || bh <= 0 || vw <= 0 || vh <= 0) return new float[]{1f, 0f, 0f};
        float scale, dx = 0f, dy = 0f;
        if ((long) bw * vh > (long) vw * bh) {
            scale = vh / (float) bh;
            dx = (vw - bw * scale) * 0.5f;
        } else {
            scale = vw / (float) bw;
            dy = (vh - bh * scale) * 0.5f;
        }
        return new float[]{scale, Math.round(dx), Math.round(dy)};
    }

    /**
     * What of a bw by bh picture the 1400 by 720 hero view shows with centre-crop, and the size it is kept at (no wider
     * than viewPx, the view's width on this screen, and never enlarged): {x, y, width, height} of the part shown, then
     * {kept width, kept height}. HomeV2 keeps only that (it kept the whole picture as fetched, up to twice the view's
     * width, and copied it at full size: two to four times the memory a hero needs).
     */
    static float[] shownPart(int bw, int bh, int viewPx) {
        float view = 1400f / 720f, visW = Math.max(1, bw), visH = Math.max(1, bh), x0 = 0, y0 = 0;
        if (visW / visH > view) { visW = visH * view; x0 = (Math.max(1, bw) - visW) / 2f; } else { visH = visW / view; y0 = (Math.max(1, bh) - visH) / 2f; }
        float k = Math.min(1f, Math.max(1, viewPx) / visW);
        return new float[]{x0, y0, visW, visH, Math.max(1, Math.round(visW * k)), Math.max(1, Math.round(visH * k))};
    }
}
