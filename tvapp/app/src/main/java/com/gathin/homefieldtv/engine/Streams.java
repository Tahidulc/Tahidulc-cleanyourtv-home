package com.gathin.homefieldtv.engine;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A stream for Media3, as the live player and the film player both open it: the playlist's own user agent and
 * referrer, redirects followed across http and https, HLS when the address says so (or when a first try says it is).
 * A live channel's MPEG-TS goes through LiveHttp, which connects a dropped stream again itself; its HLS, the archive and
 * films through Media3's own HTTP. Every one of them one stream at a time (StreamGate). The address is never logged: it
 * can hold the login.
 */
@OptIn(markerClass = UnstableApi.class)
public final class Streams {
    private Streams() {}

    /** What a stream is for: a live channel, a programme from the archive, a film or an episode. */
    public static final int LIVE = 0, ARCHIVE = 1, FILM = 2;

    public static boolean looksHls(String url) {
        return url != null && url.toLowerCase(Locale.ROOT).contains(".m3u8");
    }

    /** A film's or an episode's stream (VodActivity). */
    public static MediaSource source(String url, String userAgent, String referrer, boolean asHls) {
        return source(url, userAgent, referrer, asHls, FILM);
    }

    public static MediaSource source(String url, String userAgent, String referrer, boolean asHls, int kind) {
        String agent = userAgent == null || userAgent.isEmpty() ? Fetch.AGENT : userAgent;
        int session = StreamGate.newSession();
        boolean live = kind == LIVE;
        DataSource.Factory f;
        if (live && !asHls) {
            f = new LiveHttp.Factory(agent, referrer, session);
        } else {
            // A live HLS stream's playlists and segments: a dead connection let go sooner than a film's (8 s, not 20).
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(live ? 8_000 : 15_000)
                    .setReadTimeoutMs(live ? 8_000 : 20_000)
                    .setUserAgent(agent);
            if (referrer != null && !referrer.isEmpty()) {
                Map<String, String> headers = new HashMap<>();
                headers.put("Referer", referrer);
                http.setDefaultRequestProperties(headers);
            }
            f = new Gated.Factory(http, session, live);
        }
        MediaItem.Builder item = new MediaItem.Builder().setUri(url);
        if (asHls) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        DefaultMediaSourceFactory media = new DefaultMediaSourceFactory(f);
        if (live) media.setLoadErrorHandlingPolicy(new LiveRetries(!asHls));
        return media.createMediaSource(item.build());
    }

    /**
     * How a live stream's failed loads are tried again. Its MPEG-TS has been connected again by LiveHttp for
     * LiveHttp.AGAIN_FOR_MS already: the player is told at once (LiveTv's tries, another copy). Its HLS: quickly, 100,
     * 300, 700 ms then every 1.5 s (Media3's own waits a second more each time, up to five), and a segment or playlist
     * not found or refused a few times too (a live one can be a moment early), before the player is told.
     */
    public static final class LiveRetries extends DefaultLoadErrorHandlingPolicy {
        private final boolean progressive;

        public LiveRetries(boolean progressive) {
            super(progressive ? 1 : 8);
            this.progressive = progressive;
        }

        @Override public long getRetryDelayMsFor(@NonNull LoadErrorHandlingPolicy.LoadErrorInfo info) {
            return delay(progressive, info.exception, info.errorCount, super.getRetryDelayMsFor(info));
        }

        /** Pure: the wait before the errorCount'th try again (C.TIME_UNSET: none; Media3's own answer given). */
        static long delay(boolean progressive, IOException e, int errorCount, long media3) {
            if (progressive) return C.TIME_UNSET;
            if (media3 == C.TIME_UNSET) {
                int code = StreamStats.http(e);
                boolean early = code == 403 || code == 404 || code == 410 || code >= 500;
                return early && errorCount <= 3 ? 300L * errorCount : C.TIME_UNSET;
            }
            long[] waits = {100, 300, 700, 1_500};
            return waits[Math.max(0, Math.min(waits.length - 1, errorCount - 1))];
        }
    }

    /**
     * Media3's HTTP for one stream, through StreamGate (one stream at a time: another stream's connections closed or
     * waited for first; this stream's own playlists and segments don't wait for each other), its bytes counted for the
     * play log, and a live stream's loading thread favoured as LiveHttp's is.
     */
    static final class Gated implements DataSource {
        static final class Factory implements DataSource.Factory {
            private final DataSource.Factory upstream;
            private final int session;
            private final boolean live;

            Factory(DataSource.Factory upstream, int session, boolean live) {
                this.upstream = upstream;
                this.session = session;
                this.live = live;
            }

            @NonNull @Override public DataSource createDataSource() {
                return new Gated(upstream.createDataSource(), session, live);
            }
        }

        private final DataSource upstream;
        private final int session;
        private final boolean live;
        private StreamGate.Entry entry;

        Gated(DataSource upstream, int session, boolean live) {
            this.upstream = upstream;
            this.session = session;
            this.live = live;
        }

        @Override public void addTransferListener(@NonNull TransferListener l) {
            upstream.addTransferListener(l);
        }

        @Override public long open(@NonNull DataSpec spec) throws IOException {
            if (live) LiveHttp.favour();
            if (entry == null) {
                try {
                    entry = StreamGate.enter(session, null);
                } catch (InterruptedException e) {
                    if (!(e instanceof StreamGate.Superseded)) Thread.currentThread().interrupt();
                    throw HttpDataSource.HttpDataSourceException.createForIOException(new InterruptedIOException(), spec,
                            HttpDataSource.HttpDataSourceException.TYPE_OPEN);
                }
            }
            try {
                return upstream.open(spec);
            } catch (IOException | RuntimeException e) {
                leave();
                throw e;
            }
        }

        @Override public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
            int n = upstream.read(buffer, offset, length);
            if (n > 0) StreamStats.bytes(n);
            return n;
        }

        @Nullable @Override public Uri getUri() {
            return upstream.getUri();
        }

        @NonNull @Override public Map<String, List<String>> getResponseHeaders() {
            return upstream.getResponseHeaders();
        }

        @Override public void close() throws IOException {
            try {
                upstream.close();
            } finally {
                leave();
            }
        }

        private void leave() {
            StreamGate.leave(entry);
            entry = null;
        }
    }
}
