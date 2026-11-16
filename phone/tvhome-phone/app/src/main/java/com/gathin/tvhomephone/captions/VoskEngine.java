package com.gathin.tvhomephone.captions;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Speech to words with the offline English model (VoskModel), on its own thread: the channel's sound in, what is being
 * said (partial) and what was said (line) out. Everything stays on the device.
 */
final class VoskEngine implements CaptionEngine {
    private final File modelDir;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BlockingQueue<short[]> queue = new ArrayBlockingQueue<>(60);
    private volatile boolean running;
    private Thread thread;

    VoskEngine(File modelDir) {
        this.modelDir = modelDir;
    }

    @Override public void start(Listener l) {
        running = true;
        thread = new Thread(() -> run(l), "captions-vosk");
        thread.setDaemon(true);
        thread.start();
    }

    @Override public void feed(short[] pcm, int n) {
        if (running) queue.offer(n == pcm.length ? pcm : java.util.Arrays.copyOf(pcm, n));
    }

    @Override public void stop() {
        running = false;
        queue.clear();
        if (thread != null) thread.interrupt();
    }

    private void run(Listener l) {
        Model model = null;
        Recognizer rec = null;
        try {
            model = new Model(modelDir.getAbsolutePath());
            rec = new Recognizer(model, (float) Pcm.RATE);
            String shown = "";
            while (running) {
                short[] b = queue.poll(500, TimeUnit.MILLISECONDS);
                if (b == null) continue;
                if (rec.acceptWaveForm(b, b.length)) {
                    String t = field(rec.getResult(), "text");
                    shown = "";
                    if (!t.isEmpty()) main.post(() -> { if (running) l.line(t); });
                } else {
                    String p = field(rec.getPartialResult(), "partial");
                    if (!p.isEmpty() && !p.equals(shown)) {
                        shown = p;
                        main.post(() -> { if (running) l.partial(p); });
                    }
                }
            }
        } catch (InterruptedException e) {
            // stopped
        } catch (IOException | RuntimeException | UnsatisfiedLinkError e) {
            if (running) main.post(() -> l.failed("The speech model couldn't start", false));
        } finally {
            if (rec != null) rec.close();
            if (model != null) model.close();
        }
    }

    /** Pure: one field of Vosk's answer ({"text": "..."} or {"partial": "..."}), "" when it isn't there. */
    static String field(String json, String key) {
        try {
            return new JSONObject(json).optString(key, "").trim();
        } catch (JSONException | NullPointerException e) {
            return "";
        }
    }
}
