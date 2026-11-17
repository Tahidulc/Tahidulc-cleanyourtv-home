package com.gathin.homefieldtv.captions;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The TV's own speech recognition, on the TV (Android 13 and later, where the TV has it on the device), given
 * the channel's sound instead of the microphone (EXTRA_AUDIO_SOURCE, a pipe) for one long session in segments. Nothing
 * leaves the TV: it is the on-device recogniser only, never the online one.
 */
@RequiresApi(33)
final class OnDeviceEngine implements CaptionEngine {
    /** Whether this TV can do it: on-device recognition there (the caller has checked for Android 13). */
    static boolean available(Context c) {
        try {
            return SpeechRecognizer.isOnDeviceRecognitionAvailable(c);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BlockingQueue<short[]> queue = new ArrayBlockingQueue<>(40);
    private volatile boolean running;
    private volatile OutputStream sink;
    private SpeechRecognizer rec;
    private ParcelFileDescriptor[] pipe;
    private Listener listener;
    private Thread writer;
    /** Sessions that ended badly one after another (reset by words heard), and when this one began. */
    private int troubles;
    private long startedAt;

    OnDeviceEngine(Context c) {
        app = c.getApplicationContext();
    }

    @Override public void start(Listener l) {
        listener = l;
        running = true;
        writer = new Thread(this::write, "captions-pipe");
        writer.setDaemon(true);
        writer.start();
        main.post(this::begin);
    }

    @Override public void feed(short[] pcm, int n) {
        if (running) queue.offer(n == pcm.length ? pcm : java.util.Arrays.copyOf(pcm, n));
    }

    @Override public void stop() {
        running = false;
        main.removeCallbacksAndMessages(null);
        main.post(this::end);
        if (writer != null) writer.interrupt();
        queue.clear();
    }

    private void write() {
        while (running) {
            short[] b;
            try {
                b = queue.poll(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return;
            }
            OutputStream o = sink;
            if (b == null || o == null) continue;
            try {
                o.write(Pcm.bytes(b, b.length));
            } catch (IOException ignored) {
                // the session ended while this was on its way: the next one takes the rest
            }
        }
    }

    private void begin() {
        if (!running) return;
        end();
        try {
            pipe = ParcelFileDescriptor.createPipe();
            rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(app);
        } catch (IOException | RuntimeException e) {
            fail("The TV's speech recognition isn't there", true);
            return;
        }
        rec.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle b) { }

            @Override public void onBeginningOfSpeech() { }

            @Override public void onRmsChanged(float v) { }

            @Override public void onBufferReceived(byte[] b) { }

            @Override public void onEndOfSpeech() { }

            @Override public void onError(int error) {
                if (!running) return;
                switch (error) {
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
                    case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
                    case SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT:
                        fail("The TV's own speech recognition can't do this", true);
                        return;
                    case SpeechRecognizer.ERROR_NO_MATCH:
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        // Nothing said for a while (music, a quiet scene): listen again, no harm done.
                        main.postDelayed(OnDeviceEngine.this::begin, 300);
                        return;
                    default:
                        again();
                }
            }

            @Override public void onResults(Bundle b) {
                String t = first(b);
                if (!t.isEmpty()) said(t);
                again();
            }

            @Override public void onPartialResults(Bundle b) {
                String t = first(b);
                if (!t.isEmpty() && listener != null) listener.partial(t);
            }

            @Override public void onSegmentResults(Bundle b) {
                String t = first(b);
                if (!t.isEmpty()) said(t);
            }

            @Override public void onEndOfSegmentedSession() {
                again();
            }

            @Override public void onEvent(int type, Bundle b) { }
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, Pcm.RATE)
                .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                .putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY);
        sink = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);
        startedAt = android.os.SystemClock.elapsedRealtime();
        try {
            rec.startListening(i);
        } catch (RuntimeException e) {
            fail("The TV's speech recognition didn't start", true);
        }
    }

    private void said(String t) {
        troubles = 0;
        if (listener != null) listener.line(t);
    }

    /** The session ended: another, sooner the first times, later after more (one that lasted counts for nothing). */
    private void again() {
        if (!running) return;
        if (android.os.SystemClock.elapsedRealtime() - startedAt > 20_000) troubles = 0;
        if (++troubles > 6) {
            fail("The TV's speech recognition keeps stopping", true);
            return;
        }
        main.postDelayed(this::begin, Math.min(5_000, 250L * troubles * troubles));
    }

    private void fail(String why, boolean tryOther) {
        end();
        running = false;
        if (listener != null) listener.failed(why, tryOther);
    }

    private void end() {
        OutputStream o = sink;
        sink = null;
        if (o != null) {
            try {
                o.close();
            } catch (IOException ignored) {
                // closed
            }
        }
        if (pipe != null) {
            try {
                pipe[0].close();
            } catch (IOException ignored) {
                // closed
            }
            pipe = null;
        }
        if (rec != null) {
            try {
                rec.cancel();
                rec.destroy();
            } catch (RuntimeException ignored) {
                // gone already
            }
            rec = null;
        }
    }

    private static String first(Bundle b) {
        if (b == null) return "";
        ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return r == null || r.isEmpty() || r.get(0) == null ? "" : r.get(0).trim();
    }
}
