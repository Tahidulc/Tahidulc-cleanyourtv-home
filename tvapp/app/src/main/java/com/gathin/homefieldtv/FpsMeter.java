package com.gathin.homefieldtv;

import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.video.VideoFrameMetadataListener;

/**
 * The picture's frames a second, for the player's chips ("50 FPS") and Settings → Player → Match frame rate: the
 * stream's own rate when it says it (Format.frameRate), else measured from the frames the player renders: the middle
 * gap between the presentation times of a run of WINDOW frames (a few seconds), taken to the usual rate it is within
 * 1.5 % of (23.976, 24, 25, 29.97, 30, 50, 59.94, 60), and only once two runs in a row agree. Anything else is not known:
 * nothing is shown rather than a wrong rate.
 *
 * <p>It is the player's one frame listener, called on the player's own thread for every frame: it keeps the times in
 * arrays made once and posts to the main thread only once a stream, when the rate is known. reset() for each new stream.
 */
@OptIn(markerClass = UnstableApi.class)
final class FpsMeter implements VideoFrameMetadataListener {
    interface Listener {
        /**
         * On the main thread: the stream started as stream (the reset's number) runs at precise frames a second; sure once
         * the stream said it or two runs agreed (the chip), else a first run's guess (only to set the TV's refresh rate
         * early, Settings → Player → Match frame rate).
         */
        void measured(int stream, float precise, boolean sure);
    }

    /** The frames a run is measured over. */
    static final int WINDOW = 48;
    private static final float[] USUAL = {23.976f, 24f, 25f, 29.97f, 30f, 50f, 59.94f, 60f};

    private final Listener out;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final long[] times = new long[WINDOW], gaps = new long[WINDOW - 1];
    /** The stream measured (reset counts them); the player's thread's own copy of it. */
    private volatile int stream;
    private int seen = -1, n;
    private float last;
    private boolean done, guessed;

    FpsMeter(Listener out) {
        this.out = out;
    }

    /** A new stream: measured afresh. Main thread; returns the stream's number. */
    int reset() {
        return ++stream;
    }

    int stream() {
        return stream;
    }

    @Override public void onVideoFrameAboutToBeRendered(long presentationTimeUs, long releaseTimeNs, @NonNull Format format,
                                                         @Nullable MediaFormat mediaFormat) {
        int s = stream;
        if (s != seen) {
            seen = s;
            n = 0;
            last = 0f;
            done = false;
            guessed = false;
        }
        if (done) return;
        float said = usual(format.frameRate);
        if (said > 0f) {
            publish(s, said, true);
            return;
        }
        // A jump back (a seek, the stream starting again): the run starts over.
        if (n > 0 && presentationTimeUs <= times[n - 1]) n = 0;
        times[n++] = presentationTimeUs;
        if (n < WINDOW) return;
        float f = rate(times, gaps);
        n = 0;
        if (f > 0f && f == last) {
            publish(s, f, true);
        } else {
            last = f;
            if (f > 0f && !guessed) {
                guessed = true;
                main.post(() -> out.measured(s, f, false));
            }
        }
    }

    private void publish(int s, float fps, boolean sure) {
        done = true;
        main.post(() -> out.measured(s, fps, sure));
    }

    /** Pure: the usual rate f is within 1.5 % of (23.976 and 29.97 kept as they are), or 0 for none. */
    static float usual(float f) {
        if (!(f > 1f) || Float.isInfinite(f)) return 0f;
        float best = 0f, err = 0.015f;
        for (float u : USUAL) {
            float e = Math.abs(f - u) / u;
            if (e < err) {
                err = e;
                best = u;
            }
        }
        return best;
    }

    /** Pure: the whole frames a second a chip shows for a rate: 23.976 as 24, 29.97 as 30, 59.94 as 60; 0 unknown. */
    static int chip(float f) {
        float u = usual(f);
        return u > 0f ? Math.round(u) : 0;
    }

    /**
     * Pure: the rate of a run of presentation times (µs, rising): the usual rate of their middle gap (worked out in gaps,
     * which it sorts), or 0 when unsure.
     */
    static float rate(long[] times, long[] gaps) {
        int k = 0;
        for (int i = 1; i < times.length && k < gaps.length; i++) gaps[k++] = times[i] - times[i - 1];
        java.util.Arrays.sort(gaps, 0, k);
        long mid = k == 0 ? 0 : gaps[k / 2];
        return mid <= 0 ? 0f : usual(1_000_000f / mid);
    }
}
