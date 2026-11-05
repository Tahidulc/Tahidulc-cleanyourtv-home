package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSink;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The rewind: the live stream's last minutes kept on this phone as it plays (the bytes the player reads are written
 * to files of 20 seconds each, and the oldest let go), so a moment can be seen again: the replay plays the files from
 * there, as one stream, and goes back to live at their end. A channel's recording starts afresh on another channel; a
 * break in the stream starts a new stretch. Only a transport stream is kept (a playlist's own channels, and HLS parts
 * that are transport streams), not HLS's playlists or MP4 parts. Off (0), 5 or 10 minutes; never when the phone is
 * short of space. The files are in the app's cache, never shared, and deleted on the next channel.
 */
@OptIn(markerClass = UnstableApi.class)
final class Timeshift {
    static final String MINUTES = "rewind_minutes";
    static final int[] CHOICES = {0, 5, 10};
    private static final long CHUNK_MS = 20_000L;
    /** At most this much kept, and never with less than this much free. */
    private static final long MAX_BYTES = 1_200L << 20, MIN_FREE = 1_500L << 20;

    /** One file of the recording: its wall-clock span and size, and which unbroken stretch it is in. */
    static final class Chunk {
        final File file;
        final long start;
        final int stretch;
        volatile long end, bytes;

        Chunk(File file, long start, int stretch) {
            this.file = file;
            this.start = start;
            this.end = start;
            this.stretch = stretch;
        }
    }

    private static Timeshift instance;
    private final Context app;
    private final File dir;
    private final List<Chunk> chunks = new ArrayList<>();
    private String key = "";
    private int stretch, serial;
    private Chunk writing;
    private OutputStream out;
    private long total;

    private Timeshift(Context app) {
        this.app = app;
        this.dir = new File(app.getCacheDir(), "timeshift");
    }

    static synchronized Timeshift get(Context c) {
        if (instance == null) instance = new Timeshift(c.getApplicationContext());
        return instance;
    }

    private SharedPreferences prefs() {
        return app.getSharedPreferences("live_player", Context.MODE_PRIVATE);
    }

    int minutes() {
        return prefs().getInt(MINUTES, 10);
    }

    void setMinutes(int m) {
        prefs().edit().putInt(MINUTES, m).apply();
        if (m == 0) clear();
    }

    /** A stretch of the stream begins (the live stream opened): kept from now, after the same channel's earlier ones. */
    synchronized boolean begin(String streamKey) {
        closeWriting();
        if (minutes() == 0 || !roomy()) {
            clear();
            return false;
        }
        if (!streamKey.equals(key)) {
            clear();
            key = streamKey;
        }
        stretch++;
        return true;
    }

    /** The live stream stopped (a replay, another channel, the player closed): the open file is finished. */
    synchronized void pause() {
        closeWriting();
    }

    synchronized void clear() {
        closeWriting();
        for (Chunk c : chunks) //noinspection ResultOfMethodCallIgnored
            c.file.delete();
        chunks.clear();
        total = 0;
        key = "";
        File[] left = dir.listFiles();
        if (left != null) for (File f : left) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }

    private boolean roomy() {
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir.getUsableSpace() > MIN_FREE;
    }

    /** The sink the live stream's reads are copied into: transport-stream bytes only. */
    DataSink sink() {
        return new DataSink() {
            private boolean keep;

            @Override public void open(@NonNull DataSpec spec) {
                keep = keeps(spec.uri.getPath());
            }

            @Override public void write(@NonNull byte[] buffer, int offset, int length) {
                if (keep) append(buffer, offset, length);
            }

            @Override public void close() {}
        };
    }

    /** Pure: whether a read's address is of a transport stream worth keeping (not an HLS playlist, key or MP4 part). */
    static boolean keeps(String path) {
        String p = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return !(p.endsWith(".m3u8") || p.endsWith(".m3u") || p.endsWith(".mp4") || p.endsWith(".m4s") || p.endsWith(".key")
                || p.endsWith(".aac") || p.endsWith(".vtt"));
    }

    private synchronized void append(byte[] b, int off, int len) {
        if (key.isEmpty()) return;
        long now = System.currentTimeMillis();
        try {
            if (writing == null || writing.stretch != stretch || now - writing.start >= CHUNK_MS) roll(now);
            out.write(b, off, len);
            writing.bytes += len;
            writing.end = now;
            total += len;
            trim(now);
        } catch (IOException e) {
            closeWriting();
        }
    }

    private void roll(long now) throws IOException {
        closeWriting();
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File f = new File(dir, (serial++) + ".ts");
        writing = new Chunk(f, now, stretch);
        out = new BufferedOutputStream(new FileOutputStream(f), 1 << 16);
        chunks.add(writing);
    }

    private void closeWriting() {
        if (out != null) {
            try {
                out.close();
            } catch (IOException ignored) {
                // the file holds what was written
            }
        }
        out = null;
        writing = null;
    }

    private void trim(long now) {
        long keepFrom = now - minutes() * 60_000L;
        while (chunks.size() > 1 && (chunks.get(0).end < keepFrom || total > MAX_BYTES)) {
            Chunk c = chunks.remove(0);
            total -= c.bytes;
            //noinspection ResultOfMethodCallIgnored
            c.file.delete();
        }
    }

    /** Whether the recording of this stream reaches back to wall time t. */
    synchronized boolean covers(String streamKey, long t) {
        if (chunks.isEmpty() || streamKey == null || !streamKey.equals(key)) return false;
        return chunks.get(0).start <= t + 2000 && t <= chunks.get(chunks.size() - 1).end;
    }

    /** The earliest moment kept for this stream (0 when nothing). */
    synchronized long earliest(String streamKey) {
        return chunks.isEmpty() || !key.equals(streamKey) ? 0 : chunks.get(0).start;
    }

    /** What a replay plays: the files of one unbroken stretch, from a point in the first. */
    static final class Snapshot {
        final List<Chunk> chunks;
        final long[] sizes;
        final long skip, startWall, endWall;

        Snapshot(List<Chunk> chunks, long skip, long startWall) {
            this.chunks = chunks;
            this.sizes = new long[chunks.size()];
            for (int i = 0; i < sizes.length; i++) sizes[i] = chunks.get(i).bytes;
            this.skip = skip;
            this.startWall = startWall;
            this.endWall = chunks.isEmpty() ? startWall : chunks.get(chunks.size() - 1).end;
        }

        long length() {
            long n = -skip;
            for (long s : sizes) n += s;
            return Math.max(0, n);
        }
    }

    /** The recording from wall time t (in its stretch), to now; null when it holds nothing there. */
    synchronized Snapshot from(String streamKey, long t) {
        closeWriting();
        if (chunks.isEmpty() || !key.equals(streamKey)) return null;
        int at = -1;
        for (int i = 0; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            if (c.end >= t) {
                at = i;
                break;
            }
        }
        if (at < 0) return null;
        Chunk first = chunks.get(at);
        List<Chunk> run = new ArrayList<>();
        for (int i = at; i < chunks.size() && chunks.get(i).stretch == first.stretch; i++) run.add(chunks.get(i));
        long skip = offset(first.start, first.end, first.bytes, t);
        return new Snapshot(run, skip, Math.max(first.start, Math.min(t, first.end)));
    }

    /** Pure: where wall time t falls in a file spanning start..end of size bytes, as a byte offset (linear). */
    static long offset(long start, long end, long bytes, long t) {
        if (t <= start || end <= start) return 0;
        if (t >= end) return Math.max(0, bytes - 1);
        return (long) ((t - start) / (double) (end - start) * bytes);
    }

    /** The replay's source of bytes: the snapshot's files read one after another, as one stream. */
    static DataSource.Factory reader(Snapshot s) {
        return () -> new Reader(s);
    }

    private static final class Reader extends BaseDataSource {
        private final Snapshot s;
        private RandomAccessFile file;
        private int index;
        private long left;
        private Uri uri;

        Reader(Snapshot s) {
            super(false);
            this.s = s;
        }

        @Override public long open(@NonNull DataSpec spec) throws IOException {
            uri = spec.uri;
            transferInitializing(spec);
            long pos = spec.position + s.skip;
            index = 0;
            while (index < s.sizes.length && pos >= s.sizes[index]) {
                pos -= s.sizes[index];
                index++;
            }
            long total = s.length() - spec.position;
            if (total < 0) total = 0;
            left = spec.length == C.LENGTH_UNSET ? total : Math.min(total, spec.length);
            openAt(pos);
            transferStarted(spec);
            return left;
        }

        private void openAt(long pos) throws IOException {
            closeFile();
            if (index >= s.chunks.size()) return;
            file = new RandomAccessFile(s.chunks.get(index).file, "r");
            file.seek(pos);
        }

        @Override public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) return 0;
            if (left == 0) return C.RESULT_END_OF_INPUT;
            while (file != null) {
                int n = file.read(buffer, offset, (int) Math.min(length, left));
                if (n > 0) {
                    left -= n;
                    bytesTransferred(n);
                    return n;
                }
                index++;
                if (index >= s.chunks.size()) {
                    closeFile();
                    return C.RESULT_END_OF_INPUT;
                }
                openAt(0);
            }
            return C.RESULT_END_OF_INPUT;
        }

        @Nullable @Override public Uri getUri() {
            return uri;
        }

        @Override public void close() throws IOException {
            closeFile();
            if (uri != null) transferEnded();
            uri = null;
        }

        private void closeFile() throws IOException {
            if (file != null) file.close();
            file = null;
        }
    }
}
