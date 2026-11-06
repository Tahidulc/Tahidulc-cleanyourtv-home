package com.gathin.homefieldtv.engine;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A stream for Media3, as the live player and the film player both open it: the playlist's own user agent and
 * referrer, redirects followed across http and https, HLS when the address says so (or when a first try says it is).
 * The address is never logged: it can hold the login.
 */
@OptIn(markerClass = UnstableApi.class)
public final class Streams {
    private Streams() {}

    public static boolean looksHls(String url) {
        return url != null && url.toLowerCase(Locale.ROOT).contains(".m3u8");
    }

    public static MediaSource source(String url, String userAgent, String referrer, boolean asHls) {
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
                .setUserAgent(userAgent == null || userAgent.isEmpty() ? Fetch.AGENT : userAgent);
        if (referrer != null && !referrer.isEmpty()) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", referrer);
            http.setDefaultRequestProperties(headers);
        }
        MediaItem.Builder item = new MediaItem.Builder().setUri(url);
        if (asHls) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        return new DefaultMediaSourceFactory(http).createMediaSource(item.build());
    }
}
