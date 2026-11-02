package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.LinkAddress;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The TVs in the house talking to each other, over the delivery link each one already runs (DeliveryLink, port
 * 47820, home network and tailnet only):
 *
 * <ul>
 *   <li>Messages: a few words ("Dinner's ready") from Home's Control Centre, or from a phone's browser on the TV's own
 *   page (http://the-tv:47820/), pop up on the other TV (or every TV) with three soft rising notes.</li>
 *   <li>Send to the other TV: what is playing here goes on there (a YouTube video at the same moment, a title on a
 *   streaming app found in that app, TiviMate opened for its channel to be picked) and pauses here.</li>
 * </ul>
 *
 * <p>The other TVs are found by asking every address on the home network for /hello (as the phone app does), and
 * remembered. A message is short text shown as text, at most one every two seconds.
 */
final class TvLink {
    private TvLink() { }

    /** A message's three notes: C, E and G, an octave up, one after another. */
    static final double[] TONE = {1046.5, 1318.51, 1567.98};
    static final int MAX_TEXT = 160;
    private static final String YOUTUBE = "com.google.android.youtube.tv", TIVIMATE = "ar.tvplayer.tv";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static long lastMessage;

    static final String[] PRESETS = {"Dinner's ready 🍛", "Food's ready, come down please", "Bedtime in 10 minutes",
            "Time to switch off now", "Can you come downstairs?", "Phone call for you 📞", "Someone's at the door 🚪",
            "Turn it down a little please", "Time for prayer 🕌", "Goodnight ❤️"};

    // ---- the other TVs ------------------------------------------------------------------------------------------

    /** This TV's name, as the others and the phone see it. */
    static String myName(Context c) {
        String n = android.provider.Settings.Global.getString(c.getContentResolver(), "device_name");
        return n == null || n.trim().isEmpty() ? Build.MODEL : n.trim();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("tv_link", Context.MODE_PRIVATE);
    }

    /** The other TVs found before: {name, address}. */
    static List<String[]> known(Context c) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("peers", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new String[]{o.getString("name"), o.getString("host")});
            }
        } catch (Exception ignored) {
            // none yet
        }
        return out;
    }

    private static void remember(Context c, List<String[]> peers) {
        JSONArray a = new JSONArray();
        try {
            for (String[] p : peers) a.put(new JSONObject().put("name", p[0]).put("host", p[1]));
        } catch (Exception ignored) {
            return;
        }
        prefs(c).edit().putString("peers", a.toString()).apply();
    }

    /**
     * The other TVs on the home network now (off the main thread, about two seconds): every address beside this TV's
     * own asked for /hello on the link's port at once; the ones that answer, by name. Remembered for next time; the
     * ones found before are kept when the network cannot be read.
     */
    static List<String[]> find(Context c) {
        List<String> mine = new ArrayList<>();
        String base = null;
        try {
            android.net.ConnectivityManager cm = c.getSystemService(android.net.ConnectivityManager.class);
            android.net.LinkProperties lp = cm == null ? null : cm.getLinkProperties(cm.getActiveNetwork());
            for (LinkAddress la : lp == null ? Collections.<LinkAddress>emptyList() : lp.getLinkAddresses()) {
                InetAddress a = la.getAddress();
                if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                mine.add(a.getHostAddress());
                if (base == null && la.getPrefixLength() >= 16) base = a.getHostAddress().substring(0, a.getHostAddress().lastIndexOf('.') + 1);
            }
        } catch (Exception ignored) {
            // no network: the ones found before
        }
        if (base == null) return known(c);
        String me = myName(c);
        List<String[]> found = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(48);
        for (int i = 1; i < 255; i++) {
            String host = base + i;
            if (mine.contains(host)) continue;
            pool.execute(() -> {
                String[] r = http(host, "POST", "/hello", "{}", 350, 1500);
                if (r == null || !"200".equals(r[0])) return;
                try {
                    String name = new JSONObject(r[1]).optString("tv");
                    if (!name.isEmpty() && !name.equals(me)) found.add(new String[]{name, host});
                } catch (Exception ignored) {
                    // not one of ours
                }
            });
        }
        pool.shutdown();
        try { pool.awaitTermination(6, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        List<String[]> out = new ArrayList<>(found);
        out.sort((x, y) -> x[0].compareToIgnoreCase(y[0]));
        if (!out.isEmpty()) remember(c, out);
        return out.isEmpty() ? known(c) : out;
    }

    /**
     * A small HTTP request on the home network, by hand (the TV's apps may not send plain HTTP through Android's own
     * client): {status, body}, or null when nothing answered.
     */
    static String[] http(String host, String method, String path, String body, int connectMs, int readMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, DeliveryLink.PORT), connectMs);
            s.setSoTimeout(readMs);
            byte[] b = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            OutputStream out = s.getOutputStream();
            out.write((method + " " + path + " HTTP/1.1\r\nHost: " + host + "\r\nContent-Type: application/json\r\nContent-Length: "
                    + b.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(b);
            out.flush();
            InputStream in = s.getInputStream();
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0 && all.size() < 64 * 1024; ) all.write(buf, 0, n);
            String text = all.toString("UTF-8");
            int sp = text.indexOf(' '), end = text.indexOf("\r\n\r\n");
            if (sp < 0) return null;
            return new String[]{text.substring(sp + 1, Math.min(text.length(), sp + 4)), end < 0 ? "" : text.substring(end + 4)};
        } catch (Exception e) {
            return null;
        }
    }

    // ---- messages -----------------------------------------------------------------------------------------------

    /** A message received (DeliveryLink): false when it is empty, or too soon after the last one. */
    static synchronized boolean accept(JSONObject m) {
        String text = m.optString("text").trim();
        long now = SystemClock.uptimeMillis();
        if (text.isEmpty() || now - lastMessage < 2_000L) return false;
        lastMessage = now;
        return true;
    }

    /** A message's words, cut to length and to one paragraph. */
    static String clean(String text) {
        String t = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT - 1) + "…" : t;
    }

    /** Sends text to these TVs (off the main thread): the names it reached. */
    static List<String> message(Context c, List<String[]> to, String text) {
        List<String> reached = new ArrayList<>();
        String body;
        try {
            body = new JSONObject().put("text", clean(text)).put("from", myName(c)).toString();
        } catch (Exception e) {
            return reached;
        }
        for (String[] peer : to) {
            String[] r = http(peer[1], "POST", "/message", body, 1500, 4000);
            if (r != null && "200".equals(r[0])) reached.add(peer[0]);
        }
        return reached;
    }

    /**
     * From the phone's page: the message shown here, and sent on to the other TVs it names ("all": every one found).
     * Off the main thread.
     */
    static void forward(Context c, JSONObject m) {
        JSONArray to = m.optJSONArray("to");
        if (to == null || to.length() == 0) return;
        boolean all = false;
        List<String> names = new ArrayList<>();
        for (int i = 0; i < to.length(); i++) {
            String n = to.optString(i);
            if ("all".equals(n)) all = true; else names.add(n);
        }
        List<String[]> peers = new ArrayList<>();
        for (String[] p : known(c)) if (all || names.contains(p[0])) peers.add(p);
        if (peers.isEmpty()) return;
        String from = m.optString("from", "Phone");
        try {
            String body = new JSONObject().put("text", clean(m.optString("text"))).put("from", from).toString();
            for (String[] p : peers) http(p[1], "POST", "/message", body, 1500, 4000);
        } catch (Exception ignored) {
            // a message lost is not worth more than that
        }
    }

    /** The page a phone's browser opens on the TV (http://the-tv:47820/): a box, the quick messages, the TVs to send to. */
    static String page(Context c) {
        StringBuilder tvs = new StringBuilder();
        String me = myName(c);
        tvs.append("<label><input type=checkbox name=to value=\"here\" checked> ").append(TextUtils.htmlEncode(me)).append("</label>");
        for (String[] p : known(c)) {
            tvs.append("<label><input type=checkbox name=to value=\"").append(TextUtils.htmlEncode(p[0])).append("\" checked> ")
                    .append(TextUtils.htmlEncode(p[0])).append("</label>");
        }
        StringBuilder quick = new StringBuilder();
        for (String p : PRESETS) quick.append("<button class=q>").append(TextUtils.htmlEncode(p)).append("</button>");
        return "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">"
                + "<title>Message the TV</title><style>"
                + ":root{color-scheme:light dark;--bg:#f4f5fb;--card:#fff;--ink:#0a0d33;--soft:#5a5f86;--accent:#10157e}"
                + "@media (prefers-color-scheme:dark){:root{--bg:#060928;--card:#141a52;--ink:#fff;--soft:#b8bce0;--accent:#ffd04d}}"
                + "body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.4 system-ui,sans-serif}"
                + "main{max-width:520px;margin:0 auto;padding:20px 16px}h1{font-size:22px;margin:4px 0 14px}"
                + "textarea{width:100%;box-sizing:border-box;min-height:84px;font:inherit;padding:12px;border-radius:14px;border:1px solid #8888;background:var(--card);color:var(--ink)}"
                + ".row{display:flex;flex-wrap:wrap;gap:8px;margin:12px 0}label{background:var(--card);padding:8px 12px;border-radius:12px}"
                + "button{font:inherit;border:0;border-radius:12px;padding:10px 14px;background:var(--card);color:var(--ink)}"
                + "#send{width:100%;background:var(--accent);color:var(--bg);font-weight:700;padding:14px;margin-top:6px}"
                + "#note{color:var(--soft);min-height:22px;margin-top:10px}</style></head><body><main>"
                + "<h1>Message the TV</h1><textarea id=t maxlength=" + MAX_TEXT + " placeholder=\"Dinner's ready\"></textarea>"
                + "<div class=row>" + tvs + "</div><button id=send>Send</button><div id=note></div>"
                + "<h1 style=\"font-size:17px;margin-top:22px\">Quick messages</h1><div class=row>" + quick + "</div>"
                + "<script>"
                + "const t=document.getElementById('t'),note=document.getElementById('note');"
                + "async function send(text){if(!text.trim()){note.textContent='Type a message first';return}"
                + "const to=[...document.querySelectorAll('input[name=to]:checked')].map(x=>x.value);"
                + "if(!to.length){note.textContent='Pick a TV';return}"
                + "note.textContent='Sending…';"
                + "try{const r=await fetch('/message',{method:'POST',headers:{'Content-Type':'application/json'},"
                + "body:JSON.stringify({text:text,from:'Phone',here:to.includes('here'),to:to.filter(x=>x!=='here')})});"
                + "note.textContent=r.ok?'Sent':'Not sent (wait a moment and try again)'}catch(e){note.textContent='The TV did not answer'}}"
                + "document.getElementById('send').onclick=()=>send(t.value);"
                + "document.querySelectorAll('.q').forEach(b=>b.onclick=()=>send(b.textContent));"
                + "</script></main></body></html>";
    }

    // ---- what is playing, and sending it over -------------------------------------------------------------------

    /** What is playing (or paused) here now: {package, title, subtitle, position ms, media id, controller}; null for nothing. */
    static Object[] playing(Context c) {
        List<MediaController> list;
        try {
            MediaSessionManager m = c.getSystemService(MediaSessionManager.class);
            list = m.getActiveSessions(new ComponentName(c, WatchHistory.class));
        } catch (Exception e) {
            return null;   // not allowed to read the media sessions
        }
        MediaController best = null;
        for (MediaController mc : list) {
            String pkg = mc.getPackageName();
            if (pkg.contains("com.gathin.tvhome") || pkg.contains("spotify") || pkg.contains("music") || pkg.contains("bluetooth")) continue;
            PlaybackState st = mc.getPlaybackState();
            if (st == null || mc.getMetadata() == null) continue;
            if (best == null || st.getState() == PlaybackState.STATE_PLAYING) best = mc;
            if (st.getState() == PlaybackState.STATE_PLAYING) break;
        }
        if (best == null) return null;
        MediaMetadata md = best.getMetadata();
        PlaybackState st = best.getPlaybackState();
        String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (title == null || title.trim().isEmpty()) title = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String sub = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        if (sub == null) sub = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        long pos = st.getPosition();
        if (st.getState() == PlaybackState.STATE_PLAYING && st.getLastPositionUpdateTime() > 0) {
            pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
        }
        String id = md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        return new Object[]{best.getPackageName(), title == null ? "" : title.trim(), sub == null ? "" : sub.trim(), Math.max(0, pos),
                id == null ? "" : id, best};
    }

    /** What the Control Centre's tile says: what would go over ("Bluey on YouTube"), else what it does. */
    static String describe(Context c) {
        Object[] p = playing(c);
        if (p == null) return "Nothing playing";
        String t = (String) p[1];
        String app = appName(c, (String) p[0]);
        return t.isEmpty() ? app : t + " · " + app;
    }

    private static String appName(Context c, String pkg) {
        try {
            return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    /** A YouTube video's id: eleven letters, digits, - and _. */
    private static boolean videoId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{11}");
    }

    /**
     * What goes over: YouTube's video by its id at the moment it is at (by its title when YouTube gives no id), a
     * title on a streaming app by its name in that app, TiviMate opened (its channel picked there).
     */
    static JSONObject sendable(Context c, Object[] p) throws Exception {
        String pkg = (String) p[0], title = (String) p[1], id = (String) p[4];
        long pos = (Long) p[3];
        JSONObject j = new JSONObject().put("from", myName(c)).put("pkg", pkg).put("title", title).put("at", pos / 1000);
        if (pkg.startsWith(YOUTUBE) || pkg.startsWith("com.google.android.youtube")) {
            j.put("kind", "youtube");
            if (videoId(id)) j.put("video", id);
        } else if (TIVIMATE.equals(pkg)) {
            j.put("kind", "app");
        } else {
            Object[] known = ViewingLog.identify(c, pkg, title, (String) p[2]);
            j.put("kind", "title").put("name", known[1] == null ? title : (String) known[1])
                    .put("tmdb", known[2] == null ? 0 : (Integer) known[2]).put("tv", known[3] != null && (Boolean) known[3]);
        }
        return j;
    }

    /** Sends what is playing to that TV and pauses it here; a note on screen either way. Called on the main thread. */
    static void sendPlaying(HomeV2 h, String[] peer) {
        Context c = h.a;
        IO.execute(() -> {
            Object[] p = playing(c);
            String note;
            if (p == null) {
                note = "Nothing is playing to send";
            } else {
                String[] r;
                try {
                    r = http(peer[1], "POST", "/send", sendable(c, p).toString(), 1500, 6000);
                } catch (Exception e) {
                    r = null;
                }
                if (r != null && "200".equals(r[0])) {
                    try { ((MediaController) p[5]).getTransportControls().pause(); } catch (Exception ignored) { }
                    note = "On " + peer[0] + " now";
                } else if (r != null && "409".equals(r[0])) {
                    note = peer[0] + " is off: turn it on first";
                } else {
                    note = peer[0] + " didn't answer";
                }
            }
            String n = note;
            MAIN.post(() -> Toast.makeText(c, n, Toast.LENGTH_LONG).show());
        });
    }

    /** Something sent from the other TV, put on here (DeliveryLink's /send, on the main thread). */
    static void openSent(Context c, JSONObject j) {
        String kind = j.optString("kind"), pkg = j.optString("pkg");
        AlertLog.note(c, "sent from " + j.optString("from") + ": " + kind + " " + j.optString("title"));
        Intent i;
        if ("youtube".equals(kind)) {
            String video = j.optString("video");
            Uri u = videoId(video) ? Uri.parse("https://www.youtube.com/watch?v=" + video + "&t=" + Math.max(0, j.optLong("at")) + "s")
                    : Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(j.optString("title")));
            i = new Intent(Intent.ACTION_VIEW, u).setPackage(YOUTUBE);
        } else if ("title".equals(kind) && !pkg.isEmpty()) {
            // The title in the same app: its own search where it has one (Debrid Stream's page and Play as Home does).
            String name = j.optString("name", j.optString("title"));
            if (installed(c, pkg)) {
                boolean tv = j.optBoolean("tv");
                Runnable open = () -> AppSearchService.search(c, pkg, name, tv);
                // The helper off (a TCL turns it off now and then): switched back on first, given a moment to start.
                if (!AppSearchService.running()) { AppSearchService.ensure(c); MAIN.postDelayed(open, 1600); } else open.run();
                return;
            }
            i = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        } else {
            i = pkg.isEmpty() ? null : c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (i == null && !pkg.isEmpty()) i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        }
        if (i == null) return;
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            AlertLog.note(c, "could not open what was sent: " + e);
        }
    }

    private static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---- the panel: Home's Control Centre ------------------------------------------------------------------------

    private static TvLink.Panel panel;

    static boolean isOpen() {
        return panel != null;
    }

    static boolean key(KeyEvent e) {
        return panel != null && panel.onKey(e);
    }

    static void close() {
        if (panel != null) panel.hide();
    }

    /** The panel: which TV (Left and Right), then the quick messages, or for sending, Send. */
    static void open(HomeV2 h, boolean sending) {
        close();
        panel = new Panel(h, sending);
    }

    private static final class Panel {
        final HomeV2 h;
        final boolean sending;
        final FrameLayout overlay;
        final LinearLayout box, tvRow;
        final TextView status;
        final List<TextView> lines = new ArrayList<>();
        final List<TextView> tvChips = new ArrayList<>();
        List<String[]> peers = new ArrayList<>();
        final Typeface heavy, semi, regular;
        int at = 0, tv = 0;
        boolean busy;

        Panel(HomeV2 h, boolean sending) {
            this.h = h;
            this.sending = sending;
            Context c = h.a;
            heavy = c.getResources().getFont(R.font.outfit_extrabold);
            semi = c.getResources().getFont(R.font.inter_semibold);
            regular = c.getResources().getFont(R.font.inter_regular);
            overlay = new FrameLayout(c);
            overlay.setBackgroundColor(0x99000000);
            overlay.setClickable(true);
            box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(h.px(40), h.px(32), h.px(40), h.px(32));
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF10157E, 0xFF1B279F});
            bg.setCornerRadius(h.px(28));
            bg.setStroke(h.px(1), 0x40FFFFFF);
            box.setBackground(bg);
            TextView crumb = h.text(15, semi, 0xFFFFD04D);
            crumb.setText(sending ? "SEND TO THE OTHER TV" : "MESSAGE THE OTHER TV");
            crumb.setLetterSpacing(0.22f);
            box.addView(crumb);
            TextView title = h.text(34, heavy, Color.WHITE);
            if (sending) {
                Object[] p = playing(c);
                title.setText(p == null ? "Nothing is playing" : ((String) p[1]).isEmpty() ? appName(c, (String) p[0]) : (String) p[1]);
            } else {
                title.setText("Pop a message up on the other TV");
            }
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
            tlp.topMargin = h.px(6);
            box.addView(title, tlp);
            tvRow = new LinearLayout(c);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, h.px(56));
            rlp.topMargin = h.px(18);
            box.addView(tvRow, rlp);
            status = h.text(20, regular, 0xCCFFFFFF);
            status.setText("Looking for the other TVs…");
            box.addView(status, new LinearLayout.LayoutParams(-1, h.px(34)));
            String[] options = sending ? new String[]{"Send it over (it pauses here)"} : PRESETS;
            for (String o : options) {
                TextView line = h.text(sending ? 26 : 24, semi, Color.WHITE);
                line.setText(o);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(h.px(20), 0, h.px(20), 0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, h.px(sending ? 64 : 52));
                lp.topMargin = h.px(4);
                box.addView(line, lp);
                lines.add(line);
            }
            if (!sending) {
                TextView hint = h.text(18, regular, 0x99FFFFFF);
                hint.setText("Your own words: open http://" + "the-tv" + ":" + DeliveryLink.PORT + " on your phone");
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = h.px(12);
                box.addView(hint, lp);
                IO.execute(() -> {
                    String ip = ownAddress(c);
                    if (ip != null) MAIN.post(() -> hint.setText("Your own words: open http://" + ip + ":" + DeliveryLink.PORT + " on your phone"));
                });
            }
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(h.px(820), -2, Gravity.CENTER);
            overlay.addView(box, blp);
            h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(200).start();
            setPeers(known(c), false);
            IO.execute(() -> {
                List<String[]> found = find(c);
                MAIN.post(() -> { if (panel == this) setPeers(found, true); });
            });
            paint();
        }

        void setPeers(List<String[]> list, boolean final_) {
            String keep = tv < peers.size() ? peers.get(tv)[0] : null;
            peers = new ArrayList<>(list);
            if (peers.size() > 1 && !sending) peers.add(new String[]{"Every TV", "*"});
            tvRow.removeAllViews();
            tvChips.clear();
            tv = 0;
            for (int i = 0; i < peers.size(); i++) {
                if (peers.get(i)[0].equals(keep)) tv = i;
                TextView chip = h.text(20, semi, Color.WHITE);
                chip.setText(peers.get(i)[0]);
                chip.setGravity(Gravity.CENTER);
                chip.setPadding(h.px(20), 0, h.px(20), 0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, h.px(46));
                lp.rightMargin = h.px(10);
                tvRow.addView(chip, lp);
                tvChips.add(chip);
            }
            if (!busy) {
                status.setText(peers.isEmpty() ? (final_ ? "No other TV found on the home network" : "Looking for the other TVs…")
                        : peers.size() > 1 ? "Left and Right choose the TV" : "");
            }
            paint();
        }

        void paint() {
            for (int i = 0; i < tvChips.size(); i++) {
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(h.px(23));
                g.setColor(i == tv ? 0xFFFFFFFF : 0x22FFFFFF);
                tvChips.get(i).setBackground(g);
                tvChips.get(i).setTextColor(i == tv ? 0xFF10157E : Color.WHITE);
            }
            for (int i = 0; i < lines.size(); i++) {
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(h.px(14));
                g.setColor(i == at ? 0x33FFFFFF : 0x00000000);
                if (i == at) g.setStroke(h.px(2), 0x99FFFFFF);
                lines.get(i).setBackground(g);
            }
        }

        void hide() {
            if (panel == this) panel = null;
            overlay.animate().alpha(0f).setDuration(160).withEndAction(() -> h.root.removeView(overlay)).start();
            h.paint();
        }

        boolean onKey(KeyEvent e) {
            int code = e.getKeyCode();
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                if (e.getAction() == KeyEvent.ACTION_UP) hide();
                return true;
            }
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
            if (ok) {
                if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) go();
                return true;
            }
            if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && tv > 0) tv--;
            else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && tv < peers.size() - 1) tv++;
            else if (code == KeyEvent.KEYCODE_DPAD_UP && at > 0) at--;
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN && at < lines.size() - 1) at++;
            paint();
            return true;
        }

        void go() {
            if (busy || peers.isEmpty()) return;
            String[] peer = peers.get(tv);
            Context c = h.a;
            if (sending) {
                hide();
                sendPlaying(h, peer);
                return;
            }
            busy = true;
            String text = lines.get(at).getText().toString();
            List<String[]> to = new ArrayList<>();
            if ("*".equals(peer[1])) { for (String[] p : peers) if (!"*".equals(p[1])) to.add(p); } else to.add(peer);
            status.setText("Sending…");
            IO.execute(() -> {
                List<String> reached = message(c, to, text);
                MAIN.post(() -> {
                    busy = false;
                    if (panel != this) return;
                    if (reached.isEmpty()) {
                        status.setText(to.size() == 1 ? peer[0] + " didn't answer (is it on?)" : "No TV answered");
                    } else {
                        hide();
                        Toast.makeText(c, "Sent to " + TextUtils.join(" and ", reached), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }
    }

    /** This TV's address on the home network, for the phone's page. */
    static String ownAddress(Context c) {
        try {
            android.net.ConnectivityManager cm = c.getSystemService(android.net.ConnectivityManager.class);
            android.net.LinkProperties lp = cm.getLinkProperties(cm.getActiveNetwork());
            for (LinkAddress la : lp.getLinkAddresses()) {
                if (la.getAddress() instanceof Inet4Address && !la.getAddress().isLoopbackAddress()) return la.getAddress().getHostAddress();
            }
        } catch (Exception ignored) {
            // not on a network
        }
        return null;
    }
}
