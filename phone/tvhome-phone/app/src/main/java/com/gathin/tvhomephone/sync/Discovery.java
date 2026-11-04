package com.gathin.tvhomephone.sync;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;

import com.gathin.tvhomephone.App;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Finding the TVs on the home network, as Delivery to TV does: their mDNS announcement ("_tvhome._tcp") and, for a TV
 * whose announcement doesn't reach the phone, every address on the Wi-Fi's /24 asked for /hello.
 */
public final class Discovery {
    private static final String TYPE = "_tvhome._tcp";
    private static final Map<String, Long> LOOKED = new HashMap<>();

    private Discovery() {}

    /** A TV found: its name and where it answers. */
    public interface Found {
        void found(String name, String host, int port);
    }

    /** Looks for about eight seconds; each TV reported once, on the main thread; done after. */
    public static void find(Context c, Found cb, Runnable done) {
        Context a = c.getApplicationContext();
        Set<String> seen = new HashSet<>();
        Found once = (name, host, port) -> App.main.post(() -> {
            if (seen.add(name)) cb.found(name, host, port);
        });
        NsdManager nsd = a.getSystemService(NsdManager.class);
        WifiManager wifi = a.getSystemService(WifiManager.class);
        WifiManager.MulticastLock lock = wifi == null ? null : wifi.createMulticastLock("tvhome-find");
        if (lock != null) lock.acquire();
        ArrayDeque<NsdServiceInfo> queue = new ArrayDeque<>();
        boolean[] busy = {false};
        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override public void onServiceFound(NsdServiceInfo info) {
                App.main.post(() -> {
                    queue.add(info);
                    resolveNext(nsd, queue, busy, once);
                });
            }
            @Override public void onDiscoveryStarted(String t) { }
            @Override public void onDiscoveryStopped(String t) { }
            @Override public void onServiceLost(NsdServiceInfo i) { }
            @Override public void onStartDiscoveryFailed(String t, int e) { }
            @Override public void onStopDiscoveryFailed(String t, int e) { }
        };
        boolean started = false;
        try {
            if (nsd != null) {
                nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, listener);
                started = true;
            }
        } catch (RuntimeException ignored) {
            // the scan below still runs
        }
        boolean nsdOn = started;
        App.io.execute(() -> {
            scan(a, null, once);
            App.main.postDelayed(() -> {
                if (nsdOn) {
                    try {
                        nsd.stopServiceDiscovery(listener);
                    } catch (RuntimeException ignored) {
                        // already stopped
                    }
                }
                if (lock != null && lock.isHeld()) lock.release();
                if (done != null) done.run();
            }, 1500);
        });
    }

    @SuppressWarnings("deprecation")
    private static void resolveNext(NsdManager nsd, ArrayDeque<NsdServiceInfo> queue, boolean[] busy, Found cb) {
        if (busy[0] || queue.isEmpty()) return;
        NsdServiceInfo info = queue.poll();
        busy[0] = true;
        nsd.resolveService(info, new NsdManager.ResolveListener() {
            int tries;
            @Override public void onResolveFailed(NsdServiceInfo i, int e) {
                if (++tries <= 3) App.main.postDelayed(() -> nsd.resolveService(info, this), 600);
                else App.main.post(() -> { busy[0] = false; resolveNext(nsd, queue, busy, cb); });
            }
            @Override public void onServiceResolved(NsdServiceInfo i) {
                String host = i.getHost() == null ? null : i.getHost().getHostAddress();
                int port = i.getPort();
                App.io.execute(() -> {
                    String name = host == null ? null : Link.hello(host, port, 3_000);
                    if (name != null) cb.found(name, host, port);
                });
                App.main.post(() -> { busy[0] = false; resolveNext(nsd, queue, busy, cb); });
            }
        });
    }

    /** The Wi-Fi's /24, every address asked for /hello (32 at a time, 400 ms each); want: only that TV, else all. */
    private static String scan(Context c, String want, Found cb) {
        String base = wifiBase(c);
        if (base == null) return null;
        String[] hit = {null};
        ExecutorService pool = Executors.newFixedThreadPool(32);
        for (int i = 1; i < 255; i++) {
            String host = base + i;
            pool.execute(() -> {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(host, Link.PORT), 400);
                } catch (Exception e) {
                    return;
                }
                String name = Link.hello(host, Link.PORT, 2_500);
                if (name == null) return;
                if (cb != null) cb.found(name, host, Link.PORT);
                if (name.equals(want)) synchronized (hit) { hit[0] = host; }
            });
        }
        pool.shutdown();
        try {
            pool.awaitTermination(8, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        synchronized (hit) {
            return hit[0];
        }
    }

    /** A paired TV not at its address: looked for by name (at most every ten minutes); its new address, or null. */
    static String relocate(Context c, String name, String old, int port) {
        synchronized (LOOKED) {
            Long last = LOOKED.get(name);
            if (name == null || name.isEmpty() || last != null && System.currentTimeMillis() - last < 10 * 60_000L) return null;
            LOOKED.put(name, System.currentTimeMillis());
        }
        String host = scan(c, name, null);
        return host == null || host.equals(old) ? null : host;
    }

    private static String wifiBase(Context c) {
        try {
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue;
                LinkProperties lp = cm.getLinkProperties(n);
                if (lp == null) continue;
                for (LinkAddress a : lp.getLinkAddresses()) {
                    if (a.getAddress() instanceof Inet4Address) {
                        String ip = a.getAddress().getHostAddress();
                        return ip.substring(0, ip.lastIndexOf('.') + 1);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // no Wi-Fi
        }
        return null;
    }
}
