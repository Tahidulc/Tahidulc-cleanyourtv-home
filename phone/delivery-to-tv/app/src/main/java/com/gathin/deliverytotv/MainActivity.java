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
 * delivery apps whose order updates go to them.
 */
public class MainActivity extends Activity {
    private static final String TYPE = "_tvhome._tcp.";

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout root, access, found, paired;
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
        note("Your delivery apps' order updates, as pop-ups on your TVs at home. Only the apps ticked below are read; "
                + "nothing leaves your home network.");

        heading("1  ·  Notification access");
        access = section();

        heading("2  ·  Your TVs");
        note("Your phone and the TVs must be on the same Wi-Fi.");
        Button find = button("Find TVs", v -> discover());
        root.addView(find);
        found = section();
        paired = section();

        heading("3  ·  Delivery apps");
        appList();

        heading("Try it");
        root.addView(button("Send a test to the TVs", v -> test()));
        note("On Honor phones: Settings › Battery › App launch › Delivery to TV › Manage manually, with all three "
                + "switches on, so the phone does not stop it.");
    }

    @Override protected void onResume() {
        super.onResume();
        showAccess();
        showPaired();
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
        discovery = new NsdManager.DiscoveryListener() {
            @Override public void onServiceFound(NsdServiceInfo info) {
                nsd.resolveService(info, new NsdManager.ResolveListener() {
                    @Override public void onResolveFailed(NsdServiceInfo i, int e) { }
                    @Override public void onServiceResolved(NsdServiceInfo i) {
                        main.post(() -> showFound(i));
                    }
                });
            }
            @Override public void onDiscoveryStarted(String t) { }
            @Override public void onDiscoveryStopped(String t) { }
            @Override public void onServiceLost(NsdServiceInfo i) { }
            @Override public void onStartDiscoveryFailed(String t, int e) { main.post(() -> toast("Could not search the network")); }
            @Override public void onStopDiscoveryFailed(String t, int e) { }
        };
        nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, discovery);
        main.postDelayed(this::stopDiscovery, 20_000);
    }

    private void stopDiscovery() {
        if (discovery != null) {
            try { nsd.stopServiceDiscovery(discovery); } catch (Exception ignored) { }
            discovery = null;
        }
        if (lock != null && lock.isHeld()) lock.release();
    }

    private void showFound(NsdServiceInfo i) {
        if (i.getHost() == null) return;
        String host = i.getHost().getHostAddress(), name = i.getServiceName();
        for (int k = 0; k < found.getChildCount(); k++) if (name.equals(found.getChildAt(k).getTag())) return;
        if (found.getChildCount() == 1 && !(found.getChildAt(0) instanceof LinearLayout)) found.removeAllViews();
        LinearLayout row = row(name);
        row.setTag(name);
        row.addView(button("Pair", v -> pair(name, host, i.getPort())));
        found.addView(row);
    }

    /** Ask the TV for a code, which it shows; the code typed here gets this phone its token. */
    private void pair(String name, String host, int port) {
        new Thread(() -> {
            try {
                Relay.post(host, port, "/pair", new JSONObject().put("phone", Build.MODEL), null);
                main.post(() -> askCode(name, host, port));
            } catch (Exception e) {
                main.post(() -> toast("Could not reach " + name + ": " + e.getMessage()));
            }
        }).start();
    }

    private void askCode(String name, String host, int port) {
        EditText code = new EditText(this);
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        code.setHint("Code on the TV");
        new AlertDialog.Builder(this)
                .setTitle("Pair with " + name)
                .setMessage("Type the four-digit code showing on the TV.")
                .setView(code)
                .setPositiveButton("Pair", (d, w) -> confirm(name, host, port, code.getText().toString().trim()))
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
                main.post(() -> toast("Not paired: " + e.getMessage()));
            }
        }).start();
    }

    private void showPaired() {
        paired.removeAllViews();
        for (JSONObject tv : Relay.tvs(this)) {
            String name = tv.optString("name");
            LinearLayout row = row("✓  " + name);
            row.addView(button("Remove", v -> {
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
            });
            root.addView(box);
        }
    }

    // ---- test ------------------------------------------------------------------------------------

    private void test() {
        if (Relay.tvs(this).isEmpty()) { toast("Pair a TV first"); return; }
        String pkg = Relay.apps(this).contains("com.ubercab.eats") ? "com.ubercab.eats" : getPackageName();
        String[][] steps = {{"1", "Your order is being prepared", ""}, {"2", "Your courier is on the way", "19:42"},
                {"4", "Your order has been delivered. Enjoy!", ""}};
        for (int i = 0; i < steps.length; i++) {
            String[] s = steps[i];
            main.postDelayed(() -> {
                try {
                    Relay.send(this, new JSONObject().put("app", Relay.label(this, pkg)).put("package", pkg)
                            .put("stage", Integer.parseInt(s[0])).put("status", s[1]).put("eta", s[2])
                            .put("icon", Relay.icon(this, pkg)));
                } catch (Exception ignored) {
                }
            }, i * 12_000L);
        }
        toast("Sending three test updates, 12 seconds apart");
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

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
