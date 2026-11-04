package com.gathin.tvhomephone.live;

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
 * A channel's stream for Media3, as the player and the guide's preview both open it: the playlist's own user agent and
 * referrer, redirects followed, HLS when the address says so (or when a first try says it is). The address is never
 * logged (it can hold the login).
 */
@OptIn(markerClass = UnstableApi.class)
final class Streams {
    private Streams() {}

    static boolean looksHls(Channel c) {
        return c.url.toLowerCase(Locale.ROOT).contains(".m3u8");
    }

    static MediaSource source(Channel c, boolean asHls) {
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
                .setUserAgent(c.userAgent.isEmpty() ? Fetch.AGENT : c.userAgent);
        if (!c.referrer.isEmpty()) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", c.referrer);
            http.setDefaultRequestProperties(headers);
        }
        MediaItem.Builder item = new MediaItem.Builder().setUri(c.url);
        if (asHls) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        return new DefaultMediaSourceFactory(http).createMediaSource(item.build());
    }
}
