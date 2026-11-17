package com.gathin.homefieldtv.prefs;

import android.content.Context;

import androidx.media3.common.PlaybackException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Riding out a bad connection on the channel itself (Settings → Stream problems): the provider's HLS stream for a
 * channel its playlist gives as MPEG-TS (an Xtream /live/…/id.ts address), so the player can ride out a drop on its
 * segments and pick among its versions where it has several; kept only where it plays (a playlist whose HLS failed goes
 * back to MPEG-TS for a few days).
 */
public final class Adaptive {
    private Adaptive() {}

    private static final Pattern LIVE_TS = Pattern.compile("^(https?://[^?#]+/live/[^/?#]+/[^/?#]+/[^/?#]+)\\.ts(\\?.*)?$");
    private static final long FORGET_MS = 3L * 24 * 3600_000L;

    /** Pure: an Xtream live MPEG-TS address's HLS one (….ts → ….m3u8), or null for any other address. */
    public static String hlsFor(String url) {
        if (url == null) return null;
        Matcher m = LIVE_TS.matcher(url);
        if (!m.matches()) return null;
        return m.group(1) + ".m3u8" + (m.group(2) == null ? "" : m.group(2));
    }

    /** Whether a playlist's HLS stream failed lately (its MPEG-TS played instead for a few days). */
    public static boolean hlsFailed(Context c, String playlistId) {
        long at = PlayerPrefs.prefs(c).getLong("hls.failed." + playlistId, 0L);
        return at > 0 && System.currentTimeMillis() - at < FORGET_MS;
    }

    public static void markHlsFailed(Context c, String playlistId) {
        PlayerPrefs.prefs(c).edit().putLong("hls.failed." + playlistId, System.currentTimeMillis()).apply();
    }

    /**
     * Pure: whether a player's error says the provider doesn't offer the HLS form at all (refused, not found, not HLS)
     * rather than that the connection dropped, so its playlist goes back to MPEG-TS for a few days.
     */
    public static boolean notOffered(int errorCode) {
        switch (errorCode) {
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS:
            case PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND:
            case PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE:
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED:
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED:
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED:
                return true;
            default:
                return false;
        }
    }
}
