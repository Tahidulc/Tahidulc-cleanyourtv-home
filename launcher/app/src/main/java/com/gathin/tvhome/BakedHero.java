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
}
