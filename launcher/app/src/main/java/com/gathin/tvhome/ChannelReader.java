package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The words on part of the picture, read on the phone (Google's text reader, which works offline): the channel's
 * scoreboard for its clock and score (ScreenText), F1's tower for its lap, a tennis board's rows word by word. One read
 * at a time; the answer comes on the main thread.
 *
 * <p>The text reader is native code with its models and working memory of its own, outside what Java's memory tidy
 * sees. It is let go a minute after its last read (made again for the next one, which takes a moment), on a memory
 * warning (MemoryWatch), and made afresh every READS_EACH reads, so whatever the native side keeps between reads never
 * piles up over a long match read every second and a bit (the tennis board).
 */
final class ChannelReader {
    interface Got {
        /** The lines read, top to bottom, each with its box in the bitmap; empty when nothing was read. */
        void lines(List<String> lines, List<Rect> boxes);

        /**
         * The same read word by word (each with its box in the bitmap), after lines: for a reader that wants each word
         * where it is (the tennis board's, SportCover). Nothing by default.
         */
        default void elements(List<String> words, List<Rect> boxes) { }
    }

    /** Let go this long after its last read. */
    static final long IDLE_MS = 60_000L;
    /** Made afresh after this many reads. */
    static final int READS_EACH = 400;

    private TextRecognizer reader;
    private volatile boolean busy;
    private int readsSince;
    private long lastReadAt;

    /** Every reader made, for memory warnings and the self-check. */
    private static final List<WeakReference<ChannelReader>> ALL = new ArrayList<>();
    /** Reads asked for since the start (the self-check counts them a minute at a time). */
    static volatile long reads;
    private static Handler main;

    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    ChannelReader() {
        synchronized (ALL) {
            for (Iterator<WeakReference<ChannelReader>> it = ALL.iterator(); it.hasNext(); ) if (it.next().get() == null) it.remove();
            ALL.add(new WeakReference<>(this));
        }
    }

    private final Runnable idle = this::closeIfIdle;

    /** Reads the bitmap; false when a read is already under way (got is not called then). */
    boolean read(Bitmap b, Got got) {
        if (busy || b == null || broken) return false;
        try {
            if (reader != null && readsSince >= READS_EACH) close();
            if (reader == null) {
                reader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                readsSince = 0;
            }
            busy = true;
            readsSince++;
            reads++;
            lastReadAt = SystemClock.uptimeMillis();
            main().removeCallbacks(idle);
            main().postDelayed(idle, IDLE_MS);
            reader.process(InputImage.fromBitmap(b, 0))
                    .addOnSuccessListener(t -> {
                        busy = false;
                        List<String> lines = new ArrayList<>(), words = new ArrayList<>();
                        List<Rect> boxes = new ArrayList<>(), wordBoxes = new ArrayList<>();
                        for (Text.TextBlock block : t.getTextBlocks()) {
                            for (Text.Line line : block.getLines()) {
                                lines.add(line.getText());
                                boxes.add(line.getBoundingBox() == null ? new Rect() : line.getBoundingBox());
                                for (Text.Element e : line.getElements()) {
                                    if (e.getBoundingBox() == null) continue;
                                    words.add(e.getText());
                                    wordBoxes.add(e.getBoundingBox());
                                }
                            }
                        }
                        got.lines(lines, boxes);
                        got.elements(words, wordBoxes);
                    })
                    .addOnFailureListener(e -> {
                        busy = false;
                        got.lines(new ArrayList<>(), new ArrayList<>());
                        got.elements(new ArrayList<>(), new ArrayList<>());
                    });
            return true;
        } catch (RuntimeException e) {
            busy = false;
            return false;
        } catch (Throwable e) {
            // The phone can't run the reader at all (its own library, or no memory for it): not tried again this time,
            // and the board keeps its own time instead. Never the app's end.
            broken = true;
            busy = false;
            return false;
        }
    }

    /** The reader couldn't run on this phone this time (see read). */
    private static volatile boolean broken;

    /** Let go when no read is under way and none came for IDLE_MS (main thread). */
    private void closeIfIdle() {
        if (busy) {
            main().postDelayed(idle, 5_000L);
            return;
        }
        if (SystemClock.uptimeMillis() - lastReadAt >= IDLE_MS - 1_000L) close();
    }

    /** Whether its native reader is held now. */
    boolean open() {
        return reader != null;
    }

    void close() {
        if (main != null) main.removeCallbacks(idle);
        if (reader != null) {
            try {
                reader.close();
            } catch (RuntimeException ignored) {
                // closed already
            }
            reader = null;
        }
    }

    /** A memory warning: every reader not reading now let go (main thread). */
    static void closeIdle() {
        for (ChannelReader r : all()) if (!r.busy) r.close();
    }

    /** Native readers held now, for the self-check. */
    static int openCount() {
        int n = 0;
        for (ChannelReader r : all()) if (r.open()) n++;
        return n;
    }

    private static List<ChannelReader> all() {
        List<ChannelReader> out = new ArrayList<>();
        synchronized (ALL) {
            for (Iterator<WeakReference<ChannelReader>> it = ALL.iterator(); it.hasNext(); ) {
                ChannelReader r = it.next().get();
                if (r == null) it.remove();
                else out.add(r);
            }
        }
        return out;
    }
}
