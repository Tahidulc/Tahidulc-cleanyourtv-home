package com.gathin.homefieldtv.prefs;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.video.VideoFrameMetadataListener;

import java.util.Arrays;

/**
 * Match frame rate (Settings → Player): the TV's refresh rate set to the programme's frames a second while it is
 * watched full screen (50 Hz for a UK channel's 25 or 50, 23.976 or 24 Hz for a film), the TV's own again after. The
 * rate is the stream's when it says it, else read from its first frames.
 */
@OptIn(markerClass = UnstableApi.class)
public final class Afr {
    private Afr() {}

    /** How far a rate may be from a whole multiple of the frames a second (24 Hz for 23.976 when there is no 23.976). */
    static final double CLOSE = 0.0025;

    /**
     * Pure: which of the display's refresh rates shows fps frames a second best: the closest whole multiple (1 to 5
     * times) within 0.25%, the lowest multiple among equally close; -1 when none fits (or fps isn't known).
     */
    public static int pick(float fps, float[] rates) {
        if (fps <= 1f || rates == null) return -1;
        int best = -1, bestK = 99;
        double bestErr = Double.MAX_VALUE;
        for (int i = 0; i < rates.length; i++) {
            float r = rates[i];
            if (r <= 0f) continue;
            for (int k = 1; k <= 5; k++) {
                double err = Math.abs(r - k * fps) / (k * fps);
                if (err > CLOSE) continue;
                boolean closer = err < bestErr - 1e-6, asClose = Math.abs(err - bestErr) <= 1e-6;
                if (closer || (asClose && k < bestK)) {
                    best = i;
                    bestErr = err;
                    bestK = k;
                }
            }
        }
        return best;
    }

    /** Pure: frames a second from a run of frame times (µs): the middle gap's, rounded to the usual rates; 0 when unsure. */
    public static float fpsFrom(long[] timesUs) {
        if (timesUs == null || timesUs.length < 8) return 0f;
        long[] gaps = new long[timesUs.length - 1];
        for (int i = 1; i < timesUs.length; i++) gaps[i - 1] = timesUs[i] - timesUs[i - 1];
        Arrays.sort(gaps);
        long mid = gaps[gaps.length / 2];
        if (mid <= 0) return 0f;
        float fps = 1_000_000f / mid;
        float[] usual = {23.976f, 24f, 25f, 29.97f, 30f, 50f, 59.94f, 60f};
        for (float u : usual) if (Math.abs(fps - u) / u < 0.01f) return u;
        return 0f;
    }

    /** The TV's refresh rate set for fps (when Settings say so, and a mode fits). */
    public static void match(Activity a, float fps) {
        if (a == null || fps <= 1f || !PlayerPrefs.matchFrameRate(a)) return;
        try {
            Window w = a.getWindow();
            Display d = a.getWindowManager().getDefaultDisplay();
            Display.Mode cur = d.getMode();
            Display.Mode[] modes = d.getSupportedModes();
            int n = 0;
            Display.Mode[] same = new Display.Mode[modes.length];
            for (Display.Mode m : modes) {
                if (m.getPhysicalWidth() == cur.getPhysicalWidth() && m.getPhysicalHeight() == cur.getPhysicalHeight()) same[n++] = m;
            }
            float[] rates = new float[n];
            for (int i = 0; i < n; i++) rates[i] = same[i].getRefreshRate();
            int i = pick(fps, rates);
            if (i < 0) return;
            WindowManager.LayoutParams lp = w.getAttributes();
            if (lp.preferredDisplayModeId == same[i].getModeId() || (lp.preferredDisplayModeId == 0 && same[i].getModeId() == cur.getModeId())) return;
            lp.preferredDisplayModeId = same[i].getModeId();
            w.setAttributes(lp);
        } catch (RuntimeException ignored) {
            // the TV's own rate
        }
    }

    /** The TV's own refresh rate again (leaving full screen, or the player). */
    public static void reset(Activity a) {
        if (a == null) return;
        try {
            Window w = a.getWindow();
            WindowManager.LayoutParams lp = w.getAttributes();
            if (lp.preferredDisplayModeId == 0) return;
            lp.preferredDisplayModeId = 0;
            w.setAttributes(lp);
        } catch (RuntimeException ignored) {
            // nothing to undo
        }
    }

    /**
     * Reads a stream's frames a second: the format's when it says so, else from its first frames' times (on the player's
     * thread), then sets the TV's rate once on the main thread. A new one for each stream.
     */
    public static final class Probe implements VideoFrameMetadataListener {
        private final Activity a;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final long[] times = new long[24];
        private int n;
        private volatile boolean done;

        public Probe(Activity a) {
            this.a = a;
        }

        /** The format the player has now: its rate used at once when it says one. */
        public void format(Format f) {
            if (done || f == null || f.frameRate == Format.NO_VALUE || f.frameRate <= 1f) return;
            float fps = f.frameRate;
            done = true;
            main.post(() -> match(a, fps));
        }

        @Override public void onVideoFrameAboutToBeRendered(long presentationTimeUs, long releaseTimeNs, Format format, android.media.MediaFormat mediaFormat) {
            if (done) return;
            if (format != null && format.frameRate != Format.NO_VALUE && format.frameRate > 1f) {
                format(format);
                return;
            }
            if (n > 0 && presentationTimeUs <= times[n - 1]) {
                n = 0;
            }
            times[n++] = presentationTimeUs;
            if (n == times.length) {
                float fps = fpsFrom(times);
                n = 0;
                if (fps > 0f) {
                    done = true;
                    main.post(() -> match(a, fps));
                }
            }
        }
    }
}
