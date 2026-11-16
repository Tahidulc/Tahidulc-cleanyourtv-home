package com.gathin.tvhomephone.live;

import android.content.Context;

import java.util.regex.Pattern;

/**
 * The best picture and sound the family's own sources say they have of a title, for its poster's corner: from the
 * names the playlists give (a "4K", "UHD", "HDR", "Dolby Vision" or "Atmos" in them) and, with TorBox on, the names of
 * the account's own files. Kept in one int: the picture (0 none, 1 SD, 2 720p, 3 1080p, 4 4K), its HDR (0 none, 1 HDR,
 * 2 HLG, 3 HDR10, 4 HDR10+, 5 Dolby Vision) and Dolby Atmos. Nothing is guessed: a name that says nothing gives nothing.
 */
public final class SourceQuality {
    private SourceQuality() {}

    static final int SD = 1, HD720 = 2, FHD = 3, UHD = 4;
    static final int HDR = 1, HLG = 2, HDR10 = 3, HDR10_PLUS = 4, DOLBY_VISION = 5;
    static final int ATMOS = 1 << 6;

    /** Names that may say something; any other is passed by without the closer look. */
    private static final Pattern MAYBE = Pattern.compile("(?i)([0-9]{3,4}[pi]|4k|uhd|fhd|hdr|hlg|dolby|vision|dovi|dv|atmos|ddpa|hd|sd|dvd)");

    /** Pure: what a source's name says of its picture and sound. */
    public static int of(String name) {
        if (name == null || name.isEmpty() || !MAYBE.matcher(name).find()) return 0;
        int v;
        switch (SourceInfo.video(name)) {
            case "4K": v = UHD; break;
            case "1080p": v = FHD; break;
            case "720p": v = HD720; break;
            case "SD": v = SD; break;
            default: v = 0;
        }
        int h;
        switch (SourceInfo.hdr(name)) {
            case "Dolby Vision": h = DOLBY_VISION; break;
            case "HDR10+": h = HDR10_PLUS; break;
            case "HDR10": h = HDR10; break;
            case "HLG": h = HLG; break;
            case "HDR": h = HDR; break;
            default: h = 0;
        }
        boolean atmos = SourceInfo.audio(name).startsWith("Dolby Atmos");
        return v | h << 3 | (atmos ? ATMOS : 0);
    }

    /** Pure: the better of two, part by part (the sharpest picture, the best HDR, Atmos if either has it). */
    public static int best(int a, int b) {
        int v = Math.max(a & 7, b & 7), h = Math.max(a >> 3 & 7, b >> 3 & 7);
        return v | h << 3 | ((a | b) & ATMOS);
    }

    /** Pure: the picture's mark for a poster: "4K", "1080p", "720p"; "" for SD or none (nothing worth saying). */
    public static String picture(int q) {
        switch (q & 7) {
            case UHD: return "4K";
            case FHD: return "1080p";
            case HD720: return "720p";
            default: return "";
        }
    }

    /** Pure: the HDR: "Dolby Vision", "HDR10+", "HDR10", "HLG", "HDR", or "". */
    public static String hdr(int q) {
        switch (q >> 3 & 7) {
            case DOLBY_VISION: return "Dolby Vision";
            case HDR10_PLUS: return "HDR10+";
            case HDR10: return "HDR10";
            case HLG: return "HLG";
            case HDR: return "HDR";
            default: return "";
        }
    }

    public static boolean dolbyVision(int q) {
        return (q >> 3 & 7) == DOLBY_VISION;
    }

    public static boolean atmos(int q) {
        return (q & ATMOS) != 0;
    }

    /** Pure: what a screen reader says for it ("4K, Dolby Vision, Dolby Atmos"); "" for nothing. */
    public static String spoken(int q) {
        StringBuilder b = new StringBuilder();
        for (String s : new String[]{picture(q), hdr(q), atmos(q) ? "Dolby Atmos" : ""}) {
            if (s.isEmpty()) continue;
            if (b.length() > 0) b.append(", ");
            b.append(s);
        }
        return b.toString();
    }

    /**
     * Off the main thread: the best the playlists' names say of the title (from the catalogue given; null when it isn't
     * read yet) and, with TorBox on, its own files' names (from the library read last: nothing is read for this).
     */
    public static int of(Context c, Catalogue cat, int tmdb, boolean tv, String title, String year) {
        int q = cat == null ? 0 : cat.quality(tmdb, tv, title, null, year);
        try {
            q = best(q, TorBox.posterQuality(c, title, year, tv));
        } catch (RuntimeException ignored) {
            // the playlists' alone
        }
        return q;
    }
}
