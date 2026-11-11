package com.gathin.tvhomephone.sync;

import android.content.Context;

import com.gathin.tvhomephone.App;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * The family's own intro video sent to each paired TV (DeliveryLink's /intro-video on the TV, port 47820), or taken off
 * them again: the file streamed as it is, with the TV's token. Over the home network only.
 */
public final class TvUpload {
    private TvUpload() {}

    /** Sent to every paired TV; done: what happened, in a line ("Sent to Living room", "Couldn't reach Bedroom"). */
    public static void introVideo(Context c, File video, String name, Tvs.Result<String> done) {
        Context a = c.getApplicationContext();
        App.io.execute(() -> {
            List<String> said = new ArrayList<>();
            List<JSONObject> tvs = Tvs.list(a);
            for (JSONObject tv : tvs) said.add(sendOne(a, tv, video, name));
            String line = tvs.isEmpty() ? "No TV paired yet: pair one in Settings, then choose the video again" : String.join(" · ", said);
            App.main.post(() -> done.done(line, null));
        });
    }

    /** The TVs back on the built-in film (their copy of the video deleted). */
    public static void clearIntroVideo(Context c) {
        Context a = c.getApplicationContext();
        App.io.execute(() -> {
            for (JSONObject tv : Tvs.list(a)) {
                try {
                    Syncer.call(a, tv, "/intro-video/clear", new JSONObject(), 6_000);
                } catch (Exception ignored) {
                    // that TV keeps its copy; its Scoreboard card can switch back too
                }
            }
        });
    }

    private static String sendOne(Context c, JSONObject tv, File video, String name) {
        String tvName = tv.optString("name", "the TV");
        String host = tv.optString("host");
        int port = tv.optInt("port", Link.PORT);
        try {
            int code;
            try {
                code = post(host, port, tv.optString("token"), video, name);
            } catch (IOException e) {
                String moved = Discovery.relocate(c, tvName, host, port);
                if (moved == null) throw e;
                tv.put("host", moved);
                Tvs.update(c, tv);
                code = post(moved, port, tv.optString("token"), video, name);
            }
            if (code == 200) return "Sent to " + tvName;
            if (code == 401) return "Pair with " + tvName + " again in Settings";
            if (code == 413) return "Too big for " + tvName;
            if (code == 404 || code == 405) return tvName + " needs launcher 4.61 or later";
            return tvName + " couldn't play that video";
        } catch (Exception e) {
            return "Couldn't reach " + tvName;
        }
    }

    private static int post(String host, int port, String token, File video, String name) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL("http://" + host + ":" + port + "/intro-video").openConnection();
        try {
            h.setConnectTimeout(6_000);
            h.setReadTimeout(120_000);
            h.setDoOutput(true);
            h.setRequestMethod("POST");
            h.setRequestProperty("Content-Type", "video/mp4");
            h.setRequestProperty("X-Token", token == null ? "" : token);
            h.setRequestProperty("X-Name", name == null ? "" : name.replaceAll("[\\r\\n]", " "));
            h.setFixedLengthStreamingMode(video.length());
            byte[] buf = new byte[64 * 1024];
            try (InputStream in = new FileInputStream(video); OutputStream out = h.getOutputStream()) {
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            }
            return h.getResponseCode();
        } finally {
            h.disconnect();
        }
    }
}
