package com.gathin.tvhomephone.live;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;

import java.util.Locale;

/**
 * What a stream itself says of its picture and sound, for the information bars: its resolution ("4K", "1080p",
 * "720p", "SD"), its HDR ("Dolby Vision", "HDR10", "HLG"), and its sound ("Dolby Atmos", "Dolby Digital 5.1", "AAC
 * Stereo"). Read from the formats the player chose, so a 4K name on an HD stream says HD. Pure.
 */
@OptIn(markerClass = UnstableApi.class)
public final class MediaTags {
    private MediaTags() {}

    /** The picture's resolution by its size (either way round), or "" when not known yet. */
    public static String video(int width, int height) {
        int w = Math.max(width, height), h = Math.min(width, height);
        if (w <= 0 || h <= 0) return "";
        if (w >= 3200 || h >= 1800) return "4K";
        if (w >= 1700 || h >= 1000) return "1080p";
        if (w >= 1200 || h >= 700) return "720p";
        return "SD";
    }

    public static String video(Format f) {
        return f == null ? "" : video(f.width, f.height);
    }

    /** The picture's HDR: Dolby Vision, HDR10 (PQ, with HDR10+ when the codec says so), HLG; "" for none. */
    public static String hdr(Format f) {
        if (f == null) return "";
        ColorInfo ci = f.colorInfo;
        return hdr(f.sampleMimeType, f.codecs, ci == null ? C.INDEX_UNSET : ci.colorTransfer);
    }

    /**
     * Pure: the HDR by the picture's type (Dolby Vision's own, or a Dolby Vision codec: dvh1, dvhe, dav1, dva1, dvav),
     * its codec's words (HDR10+ where a codec string says so) and its transfer (PQ: HDR10, HLG); "" for none.
     */
    static String hdr(String mime, String codecs, int transfer) {
        String m = mime == null ? "" : mime, cs = codecs == null ? "" : codecs.toLowerCase(Locale.ROOT).trim();
        if (MimeTypes.VIDEO_DOLBY_VISION.equals(m) || cs.startsWith("dvh") || cs.startsWith("dva") || cs.startsWith("dav1")
                || cs.contains(",dvh") || cs.contains(",dva")) return "Dolby Vision";
        if (cs.contains("hdr10+") || cs.contains("hdr10plus")) return "HDR10+";
        if (transfer == C.COLOR_TRANSFER_ST2084) return "HDR10";
        if (transfer == C.COLOR_TRANSFER_HLG) return "HLG";
        return "";
    }

    /** The sound: its kind and its channels ("Dolby Digital 5.1", "AAC Stereo", "Dolby Atmos"), or "". */
    public static String audio(Format f) {
        if (f == null) return "";
        return audio(f.sampleMimeType, f.codecs, f.channelCount);
    }

    /**
     * Pure: the sound by its type, its codec's words and its channels: E-AC-3 with joint object coding (its own type,
     * or the "ec+3" codec a stream's playlist names it by) is Dolby Atmos.
     */
    static String audio(String mime, String codecs, int channelCount) {
        String cs = codecs == null ? "" : codecs.toLowerCase(Locale.ROOT);
        String kind = cs.contains("ec+3") || cs.contains("ec-3.joc") ? "Dolby Atmos" : codec(mime);
        String ch = channels(channelCount);
        if ("Dolby Atmos".equals(kind)) return kind;
        if (kind.isEmpty()) return ch;
        return ch.isEmpty() ? kind : kind + " " + ch;
    }

    static String codec(String mime) {
        if (mime == null) return "";
        switch (mime) {
            case MimeTypes.AUDIO_E_AC3_JOC: return "Dolby Atmos";
            case MimeTypes.AUDIO_E_AC3: return "Dolby Digital+";
            case MimeTypes.AUDIO_AC3: return "Dolby Digital";
            case MimeTypes.AUDIO_AC4: return "Dolby AC-4";
            case MimeTypes.AUDIO_TRUEHD: return "Dolby TrueHD";
            case MimeTypes.AUDIO_DTS: case MimeTypes.AUDIO_DTS_EXPRESS: return "DTS";
            case MimeTypes.AUDIO_DTS_HD: return "DTS-HD";
            case MimeTypes.AUDIO_DTS_X: return "DTS:X";
            case MimeTypes.AUDIO_AAC: return "AAC";
            case MimeTypes.AUDIO_MPEG: case MimeTypes.AUDIO_MPEG_L2: return "MPEG";
            case MimeTypes.AUDIO_OPUS: return "Opus";
            case MimeTypes.AUDIO_FLAC: return "FLAC";
            default: return "";
        }
    }

    static String channels(int n) {
        switch (n) {
            case 1: return "Mono";
            case 2: return "Stereo";
            case 6: return "5.1";
            case 8: return "7.1";
            default: return n > 2 ? n + " ch" : "";
        }
    }
}
