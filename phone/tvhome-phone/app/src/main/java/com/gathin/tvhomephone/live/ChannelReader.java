package com.gathin.tvhomephone.live;

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
 * scoreboard for its clock and score (ScreenText), F1's tower for its lap. One read at a time; the answer comes on
 * the main thread.
 */
final class ChannelReader {
    interface Got {
        /** The lines read, top to bottom, each with its box in the bitmap; empty when nothing was read. */
        void lines(List<String> lines, List<Rect> boxes);
    }

    private TextRecognizer reader;
    private volatile boolean busy;

    /** Reads the bitmap; false when a read is already under way (got is not called then). */
    boolean read(Bitmap b, Got got) {
        if (busy || b == null) return false;
        try {
            if (reader == null) reader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            busy = true;
            reader.process(InputImage.fromBitmap(b, 0))
                    .addOnSuccessListener(t -> {
                        busy = false;
                        List<String> lines = new ArrayList<>();
                        List<Rect> boxes = new ArrayList<>();
                        for (Text.TextBlock block : t.getTextBlocks()) {
                            for (Text.Line line : block.getLines()) {
                                lines.add(line.getText());
                                boxes.add(line.getBoundingBox() == null ? new Rect() : line.getBoundingBox());
                            }
                        }
                        got.lines(lines, boxes);
                    })
                    .addOnFailureListener(e -> {
                        busy = false;
                        got.lines(new ArrayList<>(), new ArrayList<>());
                    });
            return true;
        } catch (RuntimeException e) {
            busy = false;
            return false;
        }
    }

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
