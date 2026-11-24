package com.gathin.homefieldtv.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * An answer read whole into memory only up to a limit, and made the right size at once when the server says how long it
 * is (Content-Length): a ByteArrayOutputStream that grows by doubling holds up to twice an answer while it grows, and
 * 1.x ran out of memory in one. Anything big (a playlist, a guide, a film list, an update) is read as a stream or
 * written to a file instead, never through here.
 */
public final class Bounded {
    private Bounded() {}

    /** Answers bigger than this are not read whole (ESPN's longest match summary is under 3 MB). */
    public static final int TEXT_MAX = 8 << 20;

    /** The bytes of in, at most max; an IOException past it (its message never the address). length: Content-Length, or -1. */
    public static byte[] bytes(InputStream in, long length, int max) throws IOException {
        Buffer b = new Buffer(length > 0 && length <= max ? (int) length : 32 * 1024);
        byte[] buf = new byte[16 * 1024];
        for (int n; (n = in.read(buf)) > 0; ) {
            if (b.size() + n > max) throw new IOException("The answer was too big to read");
            b.write(buf, 0, n);
        }
        return b.exact();
    }

    /** As bytes, as UTF-8 text. */
    public static String text(InputStream in, long length, int max) throws IOException {
        Buffer b = new Buffer(length > 0 && length <= max ? (int) length : 32 * 1024);
        byte[] buf = new byte[16 * 1024];
        for (int n; (n = in.read(buf)) > 0; ) {
            if (b.size() + n > max) throw new IOException("The answer was too big to read");
            b.write(buf, 0, n);
        }
        return b.toString(StandardCharsets.UTF_8.name());
    }

    /** Its own bytes handed back without a second copy when it was sized right. */
    private static final class Buffer extends ByteArrayOutputStream {
        Buffer(int size) {
            super(Math.max(32, size));
        }

        byte[] exact() {
            return count == buf.length ? buf : toByteArray();
        }
    }
}
