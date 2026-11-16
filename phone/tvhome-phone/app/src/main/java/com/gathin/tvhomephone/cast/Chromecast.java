package com.gathin.tvhomephone.cast;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;

import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.HlsSegmentFormat;
import com.google.android.gms.cast.HlsVideoSegmentFormat;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.images.WebImage;

import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Chromecasts and Google TVs (Cast), with Google's own player on them (the Default Media Receiver): found through the
 * phone's media routes, connected by choosing one, then given the channel as live HLS through the phone's relay
 * (HlsRelay). Needs Google Play services; without them there is simply no Chromecast section.
 */
final class Chromecast {
    private Chromecast() {}

    /** What happened to a cast asked for (on the main thread). */
    interface Result {
        void done(boolean ok, String why);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static CastContext cast;
    private static MediaRouter.Callback scanning;
    private static SessionManagerListener<CastSession> waiting;

    /** Whether Cast can work on this phone at all (Google Play services there and up to date). */
    static boolean possible(Context c) {
        try {
            return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(c) == ConnectionResult.SUCCESS;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static MediaRouteSelector selector() {
        return new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                .build();
    }

    /** Cast's own context, made once (it reads CastSetup); null where it can't be. Main thread. */
    private static void context(Context c, Consumer<CastContext> then) {
        if (cast != null) {
            then.accept(cast);
            return;
        }
        if (!possible(c)) {
            then.accept(null);
            return;
        }
        try {
            CastContext.getSharedInstance(c.getApplicationContext(), Executors.newSingleThreadExecutor())
                    .addOnSuccessListener(ctx -> MAIN.post(() -> {
                        cast = ctx;
                        then.accept(ctx);
                    }))
                    .addOnFailureListener(e -> MAIN.post(() -> then.accept(null)));
        } catch (RuntimeException e) {
            then.accept(null);
        }
    }

    /** Looks for Cast devices until stopScan; each reported once as it is seen (main thread). */
    static void scan(Context c, Consumer<CastDevice> found) {
        context(c, ctx -> {
            if (ctx == null) return;
            MediaRouter router = MediaRouter.getInstance(c.getApplicationContext());
            MediaRouteSelector sel = selector();
            java.util.Set<String> seen = new java.util.HashSet<>();
            MediaRouter.Callback cb = new MediaRouter.Callback() {
                @Override public void onRouteAdded(@NonNull MediaRouter r, @NonNull MediaRouter.RouteInfo route) { offer(route); }

                @Override public void onRouteChanged(@NonNull MediaRouter r, @NonNull MediaRouter.RouteInfo route) { offer(route); }

                private void offer(MediaRouter.RouteInfo route) {
                    if (route.isDefault() || !route.matchesSelector(sel) || !route.isEnabled()) return;
                    if (seen.add(route.getId())) found.accept(CastDevice.chromecast(route.getName(), route.getId()));
                }
            };
            stopScan(c);
            scanning = cb;
            router.addCallback(sel, cb, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY | MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
            for (MediaRouter.RouteInfo r : router.getRoutes()) cb.onRouteAdded(router, r);
        });
    }

    static void stopScan(Context c) {
        if (scanning == null) return;
        try {
            MediaRouter.getInstance(c.getApplicationContext()).removeCallback(scanning);
        } catch (RuntimeException ignored) {
            // gone
        }
        scanning = null;
    }

    /** Connects to the device (when not already) and plays the address there as a live channel. Main thread. */
    static void play(Context c, CastDevice d, String url, boolean hls, String title, String subtitle, String logo, Result r) {
        context(c, ctx -> {
            if (ctx == null) {
                r.done(false, "Cast isn't available on this phone");
                return;
            }
            SessionManager sessions = ctx.getSessionManager();
            CastSession now = sessions.getCurrentCastSession();
            if (now != null && now.isConnected() && d.route.equals(routeOf(c, now))) {
                load(now, url, hls, title, subtitle, logo, r);
                return;
            }
            MediaRouter router = MediaRouter.getInstance(c.getApplicationContext());
            MediaRouter.RouteInfo route = null;
            for (MediaRouter.RouteInfo ri : router.getRoutes()) if (ri.getId().equals(d.route)) route = ri;
            if (route == null) {
                r.done(false, d.name + " isn't on the Wi-Fi now");
                return;
            }
            forget(sessions);
            waiting = new SessionListener() {
                @Override public void onSessionStarted(@NonNull CastSession s, @NonNull String id) {
                    forget(sessions);
                    load(s, url, hls, title, subtitle, logo, r);
                }

                @Override public void onSessionResumed(@NonNull CastSession s, boolean wasSuspended) {
                    forget(sessions);
                    load(s, url, hls, title, subtitle, logo, r);
                }

                @Override public void onSessionStartFailed(@NonNull CastSession s, int error) {
                    forget(sessions);
                    r.done(false, "Couldn't connect to " + d.name);
                }
            };
            sessions.addSessionManagerListener(waiting, CastSession.class);
            router.selectRoute(route);
        });
    }

    private static String routeOf(Context c, CastSession s) {
        try {
            MediaRouter.RouteInfo selected = MediaRouter.getInstance(c.getApplicationContext()).getSelectedRoute();
            return selected == null ? "" : selected.getId();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static void forget(SessionManager sessions) {
        if (waiting == null) return;
        sessions.removeSessionManagerListener(waiting, CastSession.class);
        waiting = null;
    }

    private static void load(CastSession s, String url, boolean hls, String title, String subtitle, String logo, Result r) {
        RemoteMediaClient client = s.getRemoteMediaClient();
        if (client == null) {
            r.done(false, "The cast device isn't ready");
            return;
        }
        MediaMetadata meta = new MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC);
        meta.putString(MediaMetadata.KEY_TITLE, title);
        if (subtitle != null && !subtitle.isEmpty()) meta.putString(MediaMetadata.KEY_SUBTITLE, subtitle);
        if (logo != null && logo.startsWith("http")) meta.addImage(new WebImage(Uri.parse(logo)));
        MediaInfo.Builder info = new MediaInfo.Builder(url)
                .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                .setContentType(hls ? "application/x-mpegURL" : "video/mp2t")
                .setMetadata(meta);
        if (hls) {
            // An IPTV provider's HLS carries MPEG transport stream pieces, not the fragmented MP4 the player expects.
            info.setHlsSegmentFormat(HlsSegmentFormat.TS).setHlsVideoSegmentFormat(HlsVideoSegmentFormat.MPEG2_TS);
        }
        try {
            client.load(new MediaLoadRequestData.Builder().setMediaInfo(info.build()).setAutoplay(true).build())
                    .setResultCallback(res -> r.done(res.getStatus().isSuccess(),
                            res.getStatus().isSuccess() ? null : "The cast device couldn't play this channel"));
        } catch (RuntimeException e) {
            r.done(false, "The cast device couldn't play this channel");
        }
    }

    /** Stops playing and leaves the device (its own screen back). Main thread. */
    static void stop(Context c) {
        if (cast == null) return;
        try {
            CastSession s = cast.getSessionManager().getCurrentCastSession();
            if (s != null && s.getRemoteMediaClient() != null) s.getRemoteMediaClient().stop();
            cast.getSessionManager().endCurrentSession(true);
            MediaRouter.getInstance(c.getApplicationContext()).unselect(MediaRouter.UNSELECT_REASON_STOPPED);
        } catch (RuntimeException ignored) {
            // already gone
        }
    }

    /** A session listener with nothing to do but what is overridden. */
    private abstract static class SessionListener implements SessionManagerListener<CastSession> {
        @Override public void onSessionStarting(@NonNull CastSession s) { }

        @Override public void onSessionStarted(@NonNull CastSession s, @NonNull String id) { }

        @Override public void onSessionStartFailed(@NonNull CastSession s, int error) { }

        @Override public void onSessionEnding(@NonNull CastSession s) { }

        @Override public void onSessionEnded(@NonNull CastSession s, int error) { }

        @Override public void onSessionResuming(@NonNull CastSession s, @NonNull String id) { }

        @Override public void onSessionResumed(@NonNull CastSession s, boolean wasSuspended) { }

        @Override public void onSessionResumeFailed(@NonNull CastSession s, int error) { }

        @Override public void onSessionSuspended(@NonNull CastSession s, int reason) { }
    }
}
