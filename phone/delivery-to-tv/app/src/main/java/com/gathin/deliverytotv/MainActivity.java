package com.gathin.deliverytotv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Delivery to TV: let it read notifications, find and pair the TVs (each shows a code to type here), and tick the
 * delivery apps whose order updates go to them, and the car app (Tesla).
 */
public class MainActivity extends Activity {
    private static final String TYPE = "_tvhome._tcp.";

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout root, access, found, paired, reader;
    private NsdManager nsd;
    private NsdManager.DiscoveryListener discovery;
    private WifiManager.MulticastLock lock;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        nsd = getSystemService(NsdManager.class);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        heading("Delivery to TV");
        note("Your delivery apps' order updates and your Tesla's notifications, as pop-ups on your TVs at home. "
                + "Only the apps ticked below are read; "
                + "nothing leaves your home network.");

        heading("1  ·  Notification access");
        access = section();

        heading("2  ·  Your TVs");
        note("Your phone and the TVs must be on the same Wi-Fi.");
        Button find = button("Find TVs", v -> discover());
        root.addView(find);
        found = section();
        paired = section();

        heading("3  ·  Apps");
        appList();

        heading("4  ·  What you ordered (optional)");
        note("To show your order's items on the TV, Delivery to TV reads them off the delivery app's basket or order "
                + "screen while you have it open. Only the ticked apps' screens, only the item lines.");
        reader = section();

        heading("Try it");
        root.addView(button("Send a test to the TVs", v -> test()));
        root.addView(button("Send a Tesla test", v -> testCar()));
        note("On Honor phones: Settings › Battery › App launch › Delivery to TV › Manage manually, with all three "
                + "switches on, so the phone does not stop it.");
    }

    @Override protected void onResume() {
        super.onResume();
        showAccess();
        showPaired();
        showReader();
    }

    @Override protected void onDestroy() {
        stopDiscovery();
        super.onDestroy();
    }

    // ---- notification access -------------------------------------------------------------------

    private void showAccess() {
        access.removeAllViews();
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean on = enabled != null && enabled.contains(new ComponentName(this, DeliveryListener.class).flattenToString());
        TextView t = note(on ? "✓  On: order updates are read." : "Off: turn on Delivery to TV in the list that opens.");
        root.removeView(t);
        access.addView(t);
        if (!on) access.addView(button("Turn on notification access",
                v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
    }

    private void showReader() {
        reader.removeAllViews();
        String on = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean enabled = on != null && on.contains(new ComponentName(this, OrderReader.class).flattenToString());
        TextView t = note(enabled ? "✓  On: items are read from the order screens." : "Off. In the list that opens, tap "
                + "Delivery to TV · order contents and turn it on.");
        root.removeView(t);
        reader.addView(t);
        if (!enabled) reader.addView(button("Turn on order contents",
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
    }

    // ---- TVs: discovery and pairing --------------------------------------------------------------

    private void discover() {
        stopDiscovery();
        found.removeAllViews();
        TextView looking = note("Looking for TVs…");
        root.removeView(looking);
        found.addView(looking);
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifi != null) {
            lock = wifi.createMulticastLock("delivery-to-tv");
            lock.setReferenceCounted(false);
            lock.acquire();
        }
        resolving.clear();
        busy = false;
        discovery = new NsdManager.DiscoveryListener() {
            @Override public void onServiceFound(NsdServiceInfo info) {
                main.post(() -> { resolving.add(info); resolveNext(); });
            }
            @Override public void onDiscoveryStarted(String t) { }
            @Override public void onDiscoveryStopped(String t) { }
            @Override public void onServiceLost(NsdServiceInfo i) { }
            @Override public void onStartDiscoveryFailed(String t, int e) { }
            @Override public void onStopDiscoveryFailed(String t, int e) { }
        };
        try {
            nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, discovery);
        } catch (Exception e) {
            discovery = null;
        }
        // And straight through the home network, for a TV whose announcement does not reach this phone.
        scan();
        main.postDelayed(() -> {
            stopDiscovery();
            if (found.getChildCount() == 1 && !(found.getChildAt(0) instanceof LinearLayout)) {
                TextView none = note("No TVs found. Check the phone is on the same Wi-Fi as the TVs, and that a "
                        + "VPN on the phone is off, then try again.");
                root.removeView(none);
                found.removeAllViews();
                found.addView(none);
            }
        }, 15_000);
    }

    /** TVs to look up, one at a time: many phones can look up only one at once, and drop the rest. */
    private final java.util.ArrayDeque<NsdServiceInfo> resolving = new java.util.ArrayDeque<>();
    private boolean busy;

    private void resolveNext() {
        if (busy || resolving.isEmpty()) return;
        NsdServiceInfo info = resolving.poll();
        busy = true;
        nsd.resolveService(info, new NsdManager.ResolveListener() {
            int tries;
            @Override public void onResolveFailed(NsdServiceInfo i, int e) {
                // Busy with another lookup: try this one again shortly.
                if (++tries <= 3) main.postDelayed(() -> nsd.resolveService(info, this), 600);
                else main.post(() -> { busy = false; resolveNext(); });
            }
            @Override public void onServiceResolved(NsdServiceInfo i) {
                main.post(() -> {
                    if (i.getHost() != null) showFound(i.getServiceName(), i.getHost().getHostAddress(), i.getPort());
                    busy = false;
                    resolveNext();
                });
            }
        });
    }

    /** Every address on this phone's Wi-Fi (its /24), asked "which TV are you?" on the TVs' port. */
    private void scan() {
        String base = null;
        try {
            android.net.ConnectivityManager cm = getSystemService(android.net.ConnectivityManager.class);
            for (android.net.Network n : cm.getAllNetworks()) {
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps == null || !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) continue;
                android.net.LinkProperties lp = cm.getLinkProperties(n);
                if (lp == null) continue;
                for (android.net.LinkAddress a : lp.getLinkAddresses()) {
                    if (a.getAddress() instanceof java.net.Inet4Address) {
                        String ip = a.getAddress().getHostAddress();
                        base = ip.substring(0, ip.lastIndexOf('.') + 1);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (base == null) return;
        String prefix = base;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(32);
        for (int i = 1; i < 255; i++) {
            String host = prefix + i;
            pool.execute(() -> {
                try (java.net.Socket s = new java.net.Socket()) {
                    s.connect(new java.net.InetSocketAddress(host, PORT), 400);
                } catch (Exception e) {
                    return;
                }
                try {
                    JSONObject r = Relay.post(host, PORT, "/hello", new JSONObject(), null);
                    String name = r.optString("tv", host);
                    main.post(() -> showFound(name, host, PORT));
                } catch (Exception ignored) {
                }
            });
        }
        pool.shutdown();
    }

    private static final int PORT = 47820;

    private void stopDiscovery() {
        if (discovery != null) {
            try { nsd.stopServiceDiscovery(discovery); } catch (Exception ignored) { }
            discovery = null;
        }
        if (lock != null && lock.isHeld()) lock.release();
    }

    private void showFound(String name, String host, int port) {
        for (int k = 0; k < found.getChildCount(); k++) {
            Object tag = found.getChildAt(k).getTag();
            if (name.equals(tag) || host.equals(found.getChildAt(k).getContentDescription())) return;
        }
        if (found.getChildCount() == 1 && !(found.getChildAt(0) instanceof LinearLayout)) found.removeAllViews();
        LinearLayout row = row(name);
        row.setTag(name);
        row.setContentDescription(host);
        row.addView(button("Pair", v -> pair(name, host, port)));
        found.addView(row);
    }

    /** Ask the TV for a code, which it shows; the code typed here gets this phone its token. */
    private void pair(String name, String host, int port) {
        new Thread(() -> {
            try {
                Relay.post(host, port, "/pair", new JSONObject().put("phone", Build.MODEL), null);
                main.post(() -> askCode(name, host, port));
            } catch (Exception e) {
                main.post(() -> problem("Could not reach " + name, "The TV did not answer (" + e.getMessage() + "). Check "
                        + "the phone is on the same Wi-Fi as the TV and any VPN on the phone is off, then tap Find TVs again."));
            }
        }).start();
    }

    private void askCode(String name, String host, int port) {
        EditText code = new EditText(this);
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        code.setHint("Code on the TV");
        new AlertDialog.Builder(this)
                .setTitle("Pair with " + name)
                .setMessage("Type the four-digit code showing at the bottom left of the TV.")
                .setView(code)
                .setPositiveButton("Pair", (d, w) -> confirm(name, host, port, code.getText().toString().trim()))
                .setNeutralButton("Show code again", (d, w) -> pair(name, host, port))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirm(String name, String host, int port, String code) {
        new Thread(() -> {
            try {
                JSONObject reply = Relay.post(host, port, "/pair/confirm", new JSONObject().put("code", code), null);
                List<JSONObject> tvs = Relay.tvs(this);
                tvs.removeIf(t -> name.equals(t.optString("name")));
                tvs.add(new JSONObject().put("name", name).put("host", host).put("port", port).put("token", reply.getString("token")));
                Relay.saveTvs(this, tvs);
                main.post(() -> { toast("Paired with " + name); showPaired(); });
            } catch (Exception e) {
                main.post(() -> problem("Not paired", "wrong code".equals(e.getMessage())
                        ? "That code did not match, or it is over five minutes old. Tap Pair to get a new one."
                        : "The TV did not answer (" + e.getMessage() + ")."));
            }
        }).start();
    }

    private void showPaired() {
        paired.removeAllViews();
        for (JSONObject tv : Relay.tvs(this)) {
            String name = tv.optString("name");
            LinearLayout row = row("✓  " + name);
            row.addView(button("Remove", v -> {
                // The TV forgets this phone too, when it can be reached.
                new Thread(() -> {
                    try {
                        Relay.post(tv.getString("host"), tv.getInt("port"), "/unpair", new JSONObject(), tv.getString("token"));
                    } catch (Exception ignored) {
                    }
                }).start();
                List<JSONObject> tvs = Relay.tvs(this);
                tvs.removeIf(t -> name.equals(t.optString("name")));
                Relay.saveTvs(this, tvs);
                showPaired();
            }));
            paired.addView(row);
        }
    }

    // ---- apps ------------------------------------------------------------------------------------

    /** Every app with an icon, the ticked ones first, then by name. */
    private void appList() {
        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(launcher, 0);
        Set<String> chosen = Relay.apps(this);
        List<String[]> list = new ArrayList<>();
        for (ResolveInfo r : apps) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean dup = false;
            for (String[] e : list) dup |= e[0].equals(pkg);
            if (!dup) list.add(new String[]{pkg, String.valueOf(r.loadLabel(pm))});
        }
        Collections.sort(list, (a, b) -> {
            boolean ca = chosen.contains(a[0]), cb = chosen.contains(b[0]);
            return ca != cb ? (ca ? -1 : 1) : a[1].compareToIgnoreCase(b[1]);
        });
        for (String[] e : list) {
            CheckBox box = new CheckBox(this);
            box.setText(e[1]);
            box.setChecked(chosen.contains(e[0]));
            box.setOnCheckedChangeListener((b, on) -> {
                Set<String> now = Relay.apps(this);
                if (on) now.add(e[0]); else now.remove(e[0]);
                Relay.setApps(this, now);
                OrderReader.refresh();
            });
            root.addView(box);
        }
    }

    // ---- test ------------------------------------------------------------------------------------

    private void test() {
        if (Relay.tvs(this).isEmpty()) { toast("Pair a TV first"); return; }
        String pkg = Relay.apps(this).contains("com.ubercab.eats") ? "com.ubercab.eats" : getPackageName();
        String[][] steps = {{"1", "Nando's is preparing your order", "19:58"}, {"2", "Sam is on the way with your order", "19:58"},
                {"4", "Your order has been delivered. Enjoy!", ""}};
        for (int i = 0; i < steps.length; i++) {
            String[] s = steps[i];
            main.postDelayed(() -> {
                try {
                    Relay.send(this, new JSONObject().put("app", Relay.label(this, pkg)).put("package", pkg)
                            .put("stage", Integer.parseInt(s[0])).put("status", s[1]).put("eta", s[2])
                            .put("from", "Nando's (test)")
                            .put("items", new org.json.JSONArray(java.util.Arrays.asList("2× ¼ Chicken PERi-PERi",
                                    "1× Spicy Rice", "1× Garlic Bread", "1× Bottomless Coke Zero", "1× Macho Peas")))
                            .put("icon", Relay.icon(this, pkg)));
                } catch (Exception ignored) {
                }
            }, i * 12_000L);
        }
        toast("Sending three test updates, 12 seconds apart");
    }

    /** A charge going up (42%, then 61%), then complete at 80%, 10 seconds apart. */
    private void testCar() {
        if (Relay.tvs(this).isEmpty()) { toast("Pair a TV first"); return; }
        Object[][] steps = {{"Charging", "Charging at 11 kW · 2 hr 10 min to 80%", 42, -1},
                {"Charging", "Charging at 11 kW · 1 hr 5 min to 80%", 61, 42},
                {"Charging complete", "Your Model X has finished charging to 80%", 80, -1}};
        for (int i = 0; i < steps.length; i++) {
            Object[] s = steps[i];
            main.postDelayed(() -> {
                try {
                    Relay.send(this, new JSONObject().put("kind", "car").put("app", "Tesla").put("package", Relay.TESLA)
                            .put("title", s[0]).put("status", s[1]).put("battery", s[2]).put("from", s[3])
                            .put("target", 80).put("icon", Relay.icon(this, Relay.TESLA)));
                } catch (Exception ignored) {
                }
            }, i * 10_000L);
        }
        toast("Sending three Tesla tests, 10 seconds apart (they show on the TV's Home screen)");
    }

    // ---- views -----------------------------------------------------------------------------------

    private void heading(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(18), 0, dp(6));
        root.addView(t);
    }

    private TextView note(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        t.setAlpha(0.8f);
        t.setPadding(0, dp(2), 0, dp(6));
        root.addView(t);
        return t;
    }

    private LinearLayout section() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        root.addView(l);
        return l;
    }

    private LinearLayout row(String label) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.setPadding(0, dp(4), 0, dp(4));
        return r;
    }

    private Button button(String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(click);
        return b;
    }

    private void problem(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
