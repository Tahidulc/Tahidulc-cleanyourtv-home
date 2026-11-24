package com.gathin.homefieldtv.engine;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A catalogue file read a line at a time with where each line starts in the file, so that a part of it can be read
 * again later straight from there (an M3U series' episodes, read when its page opens: never all of them in memory). Lines
 * are UTF-8, ended by "\n" (a "\r" before it is left out).
 */
final class Rows implements Closeable {
    private final InputStream in;
    private final byte[] buf = new byte[64 * 1024];
    private int pos, lim;
    private byte[] line = new byte[512];
    /** Where the file has been read to, and the line last given's start and end. */
    private long read, start, end;
    private final long stop;

    Rows(File f) throws IOException {
        this(f, 0, Long.MAX_VALUE);
    }

    /** The lines from byte from (a line's start) up to byte to. */
    Rows(File f, long from, long to) throws IOException {
        FileInputStream s = new FileInputStream(f);
        if (from > 0) s.getChannel().position(from);
        in = s;
        read = from;
        stop = to;
    }

    /** The next line, or null at the end. */
    String next() throws IOException {
        if (read >= stop) return null;
        start = read;
        int n = 0;
        while (true) {
            if (pos == lim) {
                lim = in.read(buf, 0, buf.length);
                pos = 0;
                if (lim <= 0) {
                    lim = 0;
                    if (n == 0) return null;
                    break;
                }
            }
            byte b = buf[pos++];
            read++;
            if (b == '\n') break;
            if (n == line.length) {
                byte[] bigger = new byte[line.length * 2];
                System.arraycopy(line, 0, bigger, 0, n);
                line = bigger;
            }
            line[n++] = b;
        }
        end = read;
        if (n > 0 && line[n - 1] == '\r') n--;
        return new String(line, 0, n, StandardCharsets.UTF_8);
    }

    /** Where the line last given starts in the file. */
    long start() {
        return start;
    }

    /** Where it ends (the next line's start). */
    long end() {
        return end;
    }

    @Override public void close() throws IOException {
        in.close();
    }
}
