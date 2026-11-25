package com.gathin.broadcast;

import java.util.Locale;

/**
 * Whether a picture a catalogue gave is really the programme's, the same in both apps, pure: not a placeholder (a known
 * placeholder's address, Sky's "Image unavailable" card, a picture nearly all one colour), not tiny, not the channel's
 * own logo. A picture that isn't is passed over for the next in its chain, never shown.
 *
 * <pre>
 *   PicVet.placeholderUrl(url)          by its address ("nologo.png", "placeholder", "default-image"...)
 *   PicVet.tiny(w, h)                   too small to be anyone's picture of a programme (under MIN_SIDE)
 *   PicVet.flat(px)                     a few hundred of its pixels: FLAT_SHARE or more of them one colour
 *   PicVet.logo(url, logoUrl)           the channel's own logo given as the programme's picture
 * </pre>
 */
public final class PicVet {
    private PicVet() {}

    /** A picture's short side under this is no programme's picture (a channel's badge, a tracking pixel). */
    public static final int MIN_SIDE = 90;
    /** This share of a picture's pixels within FLAT_TOLERANCE of one colour: a plain card, not a programme's picture. */
    public static final float FLAT_SHARE = 0.86f;
    public static final int FLAT_TOLERANCE = 26;

    private static final String[] PLACEHOLDERS = {"nologo", "no_logo", "no-logo", "placeholder", "place-holder", "default_image",
            "default-image", "defaultimage", "noimage", "no_image", "no-image", "image-unavailable", "imageunavailable", "missing.",
            "blank.", "spacer.", "/default.", "fallback.", "dummy."};

    /** Pure: whether an address is a known placeholder's. */
    public static boolean placeholderUrl(String url) {
        if (url == null || url.isEmpty()) return true;
        String u = url.toLowerCase(Locale.ROOT);
        for (String p : PLACEHOLDERS) if (u.contains(p)) return true;
        return false;
    }

    /** Pure: whether a picture w x h is too small to be a programme's. */
    public static boolean tiny(int w, int h) {
        return Math.min(w, h) < MIN_SIDE;
    }

    /** Pure: whether a programme's picture address is just the channel's own logo (the same address, its query aside). */
    public static boolean logo(String url, String logoUrl) {
        if (url == null || logoUrl == null || url.isEmpty() || logoUrl.isEmpty()) return false;
        return strip(url).equalsIgnoreCase(strip(logoUrl));
    }

    private static String strip(String u) {
        int q = u.indexOf('?');
        String s = q >= 0 ? u.substring(0, q) : u;
        return s.replaceFirst("^https?://", "");
    }

    /**
     * Pure: whether a picture is a plain card: of its pixels (ARGB, a small even sample of it), FLAT_SHARE or more within
     * FLAT_TOLERANCE of their commonest colour (Sky's "Image unavailable" is one blue with a line of words on it).
     */
    public static boolean flat(int[] px) {
        if (px == null || px.length == 0) return true;
        // The commonest colour, its channels rounded to 32 steps.
        java.util.Map<Integer, Integer> count = new java.util.HashMap<>();
        int top = 0, topN = 0;
        for (int c : px) {
            int k = ((c >> 19) & 0x1F) << 10 | ((c >> 11) & 0x1F) << 5 | ((c >> 3) & 0x1F);
            int n = count.merge(k, 1, Integer::sum);
            if (n > topN) {
                topN = n;
                top = c;
            }
        }
        int r0 = (top >> 16) & 0xFF, g0 = (top >> 8) & 0xFF, b0 = top & 0xFF, near = 0;
        for (int c : px) {
            if (Math.abs(((c >> 16) & 0xFF) - r0) <= FLAT_TOLERANCE && Math.abs(((c >> 8) & 0xFF) - g0) <= FLAT_TOLERANCE
                    && Math.abs((c & 0xFF) - b0) <= FLAT_TOLERANCE) near++;
        }
        return near >= FLAT_SHARE * px.length;
    }
}
