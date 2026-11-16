package com.gathin.tvhomephone.cast;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import com.gathin.tvhomephone.SyncStore;
import com.gathin.tvhomephone.live.Channel;
import com.gathin.tvhomephone.sync.Tvs;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What is being cast, and where: one device at a time, the channel on it, and how it is going (connecting, playing,
 * failed). Live TV's cast sheet starts it, a channel chosen while casting goes to the device instead of the phone, and
 * the "Casting to …" bar stops it. Main thread. The stream's address is never logged.
 */
public final class Casting {
    private Casting() {}

    public static final int CONNECTING = 1, PLAYING = 2, FAILED = 3;

    /** A channel to cast: the channel, its names for a TV's own player, what's on now and its logo for the device's screen. */
    public static final class What {
        public final Channel channel;
        final List<String> names;
        final String programme, logo;

        public What(Channel channel, List<String> names, String programme, String logo) {
            this.channel = channel;
            this.names = names;
            this.programme = programme;
            this.logo = logo;
        }
    }

    public interface Listener {
        void castChanged();
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private static CastDevice device;
    private static What what;
    private static int state;
    private static String message = "";
    /** Each send counted, so an answer to an earlier one doesn't overwrite a later one's. */
    private static int sends;

    public static boolean active() { return device != null; }

    public static CastDevice device() { return device; }

    /** The channel on the device (null before one is sent). */
    public static Channel channel() { return what == null ? null : what.channel; }

    public static int state() { return state; }

    /** What went wrong, or "" while all is well. */
    public static String message() { return message; }

    public static void listen(Listener l) {
        if (!LISTENERS.contains(l)) LISTENERS.add(l);
    }

    public static void unlisten(Listener l) {
        LISTENERS.remove(l);
    }

    private static void changed() {
        for (Listener l : LISTENERS) l.castChanged();
    }

    /** Casting to a device starts: the channel (when there is one yet) sent to it. */
    public static void start(Context c, CastDevice d, What w) {
        if (device != null && !device.id.equals(d.id)) leave(c);
        device = d;
        what = null;
        state = CONNECTING;
        message = "";
        changed();
        if (w != null) send(c, w);
    }

    /** Another channel to the device being cast to. */
    public static void change(Context c, What w) {
        if (device == null || w == null) return;
        send(c, w);
    }

    /** Casting stops: the device stops playing where it can, and the phone's relay closes. */
    public static void stop(Context c) {
        if (device == null) return;
        leave(c);
        device = null;
        what = null;
        state = 0;
        message = "";
        sends++;
        changed();
    }

    private static void leave(Context c) {
        CastDevice d = device;
        if (d == null) return;
        switch (d.kind) {
            case CastDevice.CHROMECAST:
                Chromecast.stop(c);
                break;
            case CastDevice.DLNA:
                IO.execute(() -> {
                    try {
                        Dlna.stop(d.control);
                    } catch (IOException ignored) {
                        // it plays on, or it's gone
                    }
                });
                break;
            case CastDevice.HOMEFIELD:
                IO.execute(() -> HomefieldLink.stop(d));
                break;
            default:
                // A paired TV's own player plays on: it is the TV's now.
                break;
        }
        HlsRelay.stopAll();
    }

    private static void send(Context c, What w) {
        CastDevice d = device;
        Context app = c.getApplicationContext();
        what = w;
        state = CONNECTING;
        message = "";
        int mine = ++sends;
        changed();
        Done done = (ok, why) -> MAIN.post(() -> {
            if (mine != sends || device != d) return;
            state = ok ? PLAYING : FAILED;
            message = ok ? "" : why == null ? "" : why;
            changed();
        });
        Channel ch = w.channel;
        switch (d.kind) {
            case CastDevice.HOMEFIELD:
                IO.execute(() -> {
                    try {
                        HomefieldLink.play(d, ch, SyncStore.device());
                        done.done(true, null);
                    } catch (IOException e) {
                        done.done(false, "not found".equals(e.getMessage())
                                ? d.name + " doesn't have " + ch.name + " in its playlists"
                                : "Couldn't reach Homefield TV on " + d.name);
                    }
                });
                break;
            case CastDevice.TV_HOME:
                Tvs.tuneOnTv(app, d.name, w.names, (ok, err) -> done.done(Boolean.TRUE.equals(ok), err));
                break;
            case CastDevice.CHROMECAST: {
                String hls = HlsRelay.hlsFor(ch.url);
                IO.execute(() -> {
                    String url;
                    try {
                        url = HlsRelay.on(wifiAddress(app)).publish(hls != null ? hls : ch.url, ch.url, ch.userAgent, ch.referrer, true);
                    } catch (IOException e) {
                        done.done(false, "The phone needs to be on the Wi-Fi to cast");
                        return;
                    }
                    MAIN.post(() -> Chromecast.play(app, d, url, true, ch.name, w.programme, w.logo,
                            (ok, why) -> done.done(ok, why)));
                });
                break;
            }
            case CastDevice.DLNA:
                IO.execute(() -> {
                    try {
                        boolean hls = ch.url.toLowerCase(java.util.Locale.ROOT).contains(".m3u8");
                        String url = ch.url;
                        // Sets can't send the channel's own User-Agent or Referrer: the phone asks for them.
                        if (!ch.userAgent.isEmpty() || !ch.referrer.isEmpty()) {
                            url = HlsRelay.on(wifiAddress(app)).publish(hls ? ch.url : null, ch.url, ch.userAgent, ch.referrer, hls);
                        }
                        Dlna.play(d.control, url, ch.name, hls ? "application/vnd.apple.mpegurl" : "video/mp2t");
                        done.done(true, null);
                    } catch (IOException e) {
                        done.done(false, d.name + " didn't take the channel");
                    }
                });
                break;
            default:
                done.done(false, null);
        }
    }

    private interface Done {
        void done(boolean ok, String why);
    }

    /** The phone's own address on the Wi-Fi (the relay listens there only). */
    static InetAddress wifiAddress(Context c) throws IOException {
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        if (cm != null) {
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps == null || !(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) continue;
                LinkProperties lp = cm.getLinkProperties(n);
                if (lp == null) continue;
                for (LinkAddress a : lp.getLinkAddresses()) {
                    if (a.getAddress() instanceof Inet4Address && !a.getAddress().isLoopbackAddress()) return a.getAddress();
                }
            }
        }
        throw new IOException("no Wi-Fi");
    }

    /** The paired TVs' addresses, where Homefield TV is looked for at its usual port too. */
    static List<String> pairedHosts(Context c) {
        List<String> out = new java.util.ArrayList<>();
        for (org.json.JSONObject t : Tvs.list(c)) {
            String host = t.optString("host");
            if (!host.isEmpty() && !out.contains(host)) out.add(host);
        }
        return out;
    }
}
