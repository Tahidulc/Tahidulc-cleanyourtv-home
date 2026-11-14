package com.gathin.tvhomephone.update;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.zip.GZIPInputStream;

/**
 * Pure: a new APK put together from the one installed and only what changed (made by launcher/update/mkpatch.py, the
 * launcher's own format, read the same way there). The patch, gzipped: "TVHP", its format (1), the new APK's length,
 * then steps until a 0: 1 copies a run of the old APK (where it starts, how long), 2 takes a run from the patch itself
 * (how long, then the bytes). Each step is checked against both files' lengths; the whole is checked against the new
 * APK's fingerprint by the caller (AppUpdate).
 */
final class UpdatePatch {
    private UpdatePatch() { }

    static final int MAGIC = 0x54564850;
    static final int FORMAT = 1;
    static final int END = 0, COPY = 1, ADD = 2;

    /** How far it has got: bytes of the new APK written of all of them. */
    interface Progress {
        void at(long done, long total);
    }

    /** The new APK's length, as the patch says (its first lines only). */
    static long length(InputStream patch) throws IOException {
        DataInputStream in = new DataInputStream(new GZIPInputStream(patch, 1 << 12));
        if (in.readInt() != MAGIC) throw new IOException("not an update");
        if (in.readInt() != FORMAT) throw new IOException("an update in another format");
        return in.readLong();
    }

    /** Writes the new APK to out: each run copied from old or taken from the patch, as it says. Its length, all written. */
    static long apply(File old, InputStream patch, OutputStream out, Progress progress) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(patch, 1 << 16), 1 << 16));
        if (in.readInt() != MAGIC) throw new IOException("not an update");
        int format = in.readInt();
        if (format != FORMAT) throw new IOException("an update in format " + format);
        long total = in.readLong(), done = 0;
        if (total <= 0) throw new IOException("an empty update");
        byte[] buf = new byte[1 << 16];
        try (RandomAccessFile ra = new RandomAccessFile(old, "r")) {
            long oldLength = ra.length();
            for (;;) {
                int step = in.readUnsignedByte();
                if (step == END) break;
                if (step == COPY) {
                    long from = in.readLong(), len = in.readLong();
                    if (from < 0 || len < 0 || from + len > oldLength) throw new IOException("a copy past the installed APK's end");
                    if (done + len > total) throw new IOException("longer than the update says");
                    ra.seek(from);
                    for (long left = len; left > 0; ) {
                        int n = ra.read(buf, 0, (int) Math.min(buf.length, left));
                        if (n < 0) throw new EOFException("the installed APK ended early");
                        out.write(buf, 0, n);
                        left -= n;
                        done += n;
                        if (progress != null) progress.at(done, total);
                    }
                } else if (step == ADD) {
                    long len = in.readLong();
                    if (len < 0 || done + len > total) throw new IOException("longer than the update says");
                    for (long left = len; left > 0; ) {
                        int n = (int) Math.min(buf.length, left);
                        in.readFully(buf, 0, n);
                        out.write(buf, 0, n);
                        left -= n;
                        done += n;
                        if (progress != null) progress.at(done, total);
                    }
                } else {
                    throw new IOException("an update step it does not know (" + step + ")");
                }
            }
        }
        if (done != total) throw new IOException("shorter than the update says");
        return done;
    }
}
