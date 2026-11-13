package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * The words on part of the picture, read on the phone (Google's text reader, which works offline): the channel's
 * scoreboard for its clock and score (ScreenText), F1's tower for its lap, a tennis board's rows word by word. One read
 * at a time; the answer comes on the main thread.
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

    private TextRecognizer reader;
    private volatile boolean busy;

    /** Reads the bitmap; false when a read is already under way (got is not called then). */
    boolean read(Bitmap b, Got got) {
        if (busy || b == null || broken) return false;
        try {
            if (reader == null) reader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            busy = true;
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

    void close() {
        if (reader != null) {
            try {
                reader.close();
            } catch (RuntimeException ignored) {
                // closed already
            }
            reader = null;
        }
    }
}
