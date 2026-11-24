package com.gathin.homefieldtv.engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Settings, About, If something goes wrong: a small file of what went wrong in Homefield TV (the last few problems), so the
 * family can see it on the screen and take a photo of it to send. Four kinds: a crash (the app closed), out of memory, a
 * freeze of the main thread (the remote got no answer for some seconds) and trouble in the background (a reading or a
 * download failed and the app carried on). Each note keeps when, its kind, the thread and where in the app's own code it
 * happened (class, method and line), and the kind of error and what caused it: never an error's message, which can hold a
 * playlist's address or login.
 *
 * <pre>
 *   1764000000000	oom	pool-2-thread-1	Espn.summary:212 &lt; BoardLayer.lambda$summary$5:477	OutOfMemoryError		0
 *   1764000100000	freeze	main	GuideGrid.onDraw:310	-		7
 * </pre>
 *
 * Kept in the app's private files; Clear these notes empties it. The start-up line ("Homefield TV had a problem last
 * time") shows once for a crash, out of memory or a freeze since the notes were last looked at.
 */
public final class Problems {
    private Problems() {}

    public static final String CRASH = "crash", OOM = "oom", FREEZE = "freeze", BACKGROUND = "background";
    /** The notes kept: the newest last. */
    static final int KEEP = 12;
    /** The same trouble in the background noted once a minute at most (a reading that fails every 20 seconds). */
    static final long QUIET_MS = 60_000L;
    private static final String NAME = "problems.txt";

    private static volatile File file;
    private static volatile String lastKey = "";
    private static volatile long lastAt;
    /** Since the app started: out of memory, freezes and background trouble (the memory log's problems=). */
    private static final java.util.concurrent.atomic.AtomicInteger OOMS = new java.util.concurrent.atomic.AtomicInteger(),
            FREEZES = new java.util.concurrent.atomic.AtomicInteger(), TROUBLES = new java.util.concurrent.atomic.AtomicInteger();

    /** The memory log's figures: "oom:0,freeze:1,bg:3" since the app started. */
    public static String sinceStart() {
        return "oom:" + OOMS.get() + ",freeze:" + FREEZES.get() + ",bg:" + TROUBLES.get();
    }

    /** One problem as noted. */
    public static final class Note {
        public final long at;
        public final String kind, thread, where, error, cause;
        /** A freeze's seconds (0 for the others). */
        public final int seconds;

        Note(long at, String kind, String thread, String where, String error, String cause, int seconds) {
            this.at = at;
            this.kind = kind;
            this.thread = thread;
            this.where = where;
            this.error = error;
            this.cause = cause;
            this.seconds = seconds;
        }

        /** Its first line, as Settings shows it: "Crash in GuideGrid.onDraw:310", "Froze for 7 s in …". */
        public String headline() {
            String in = where.isEmpty() ? "" : " in " + where;
            switch (kind) {
                case CRASH: return "Crash in " + (where.isEmpty() ? thread : where);
                case OOM: return "Out of memory in " + (where.isEmpty() ? thread : where);
                case FREEZE: return "Froze for " + seconds + " s" + in;
                default: return "Trouble in the background (the app carried on)" + in;
            }
        }

        /** Its second line: the error's kind, and what caused it ("Caused by …"), "" when there is nothing to add. */
        public String detail() {
            StringBuilder b = new StringBuilder();
            if (!error.isEmpty() && !"-".equals(error)) b.append(error);
            if (!cause.isEmpty()) b.append(b.length() > 0 ? " · " : "").append("Caused by ").append(cause);
            return b.toString();
        }

        String row() {
            return at + "\t" + kind + "\t" + clean(thread) + "\t" + clean(where) + "\t" + clean(error) + "\t" + clean(cause) + "\t" + seconds;
        }

        static Note parse(String line) {
            String[] c = line.split("\t", -1);
            if (c.length < 7) return null;
            try {
                return new Note(Long.parseLong(c[0]), c[1], c[2], c[3], c[4], c[5], Integer.parseInt(c[6]));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /** Where the notes go: the app's private files folder (set once, as the app starts). */
    public static void init(File filesDir) {
        if (filesDir != null) file = new File(filesDir, NAME);
    }

    /** The notes, oldest first (empty when none, or none yet). */
    public static List<Note> notes() {
        File f = file;
        if (f == null || !f.isFile()) return Collections.emptyList();
        List<Note> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            for (String l; (l = r.readLine()) != null; ) {
                Note n = Note.parse(l);
                if (n != null) out.add(n);
            }
        } catch (IOException | RuntimeException ignored) {
            // none to show
        }
        return out;
    }

    /** The newest note, or null. */
    public static Note last() {
        List<Note> n = notes();
        return n.isEmpty() ? null : n.get(n.size() - 1);
    }

    /** Clear these notes. */
    public static synchronized void clear() {
        File f = file;
        //noinspection ResultOfMethodCallIgnored
        if (f != null) f.delete();
        File seen = seenFile();
        //noinspection ResultOfMethodCallIgnored
        if (seen != null) seen.delete();
    }

    /**
     * The start-up line, once: the newest crash, out of memory or freeze since the last time it was shown (null: none). A
     * trouble in the background the app carried on through is not told at start-up.
     */
    public static synchronized Note takeUntold() {
        Note n = null;
        for (Note x : notes()) if (!BACKGROUND.equals(x.kind)) n = x;
        if (n == null) return null;
        File seen = seenFile();
        long told = 0;
        if (seen != null && seen.isFile()) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(seen), StandardCharsets.US_ASCII))) {
                String l = r.readLine();
                told = l == null ? 0 : Long.parseLong(l.trim());
            } catch (IOException | RuntimeException ignored) {
                told = 0;
            }
        }
        if (n.at <= told) return null;
        if (seen != null) {
            try (Writer w = new OutputStreamWriter(new FileOutputStream(seen), StandardCharsets.US_ASCII)) {
                w.write(String.valueOf(n.at));
            } catch (IOException ignored) {
                // told again next time
            }
        }
        return n;
    }

    private static File seenFile() {
        File f = file;
        return f == null ? null : new File(f.getParentFile(), NAME + ".told");
    }

    // ---- noting --------------------------------------------------------------------------------------------------------

    /** The app closed on an error the main thread had (written before Android ends it). */
    public static void crash(Thread t, Throwable e) {
        note(e instanceof OutOfMemoryError ? OOM : CRASH, t, e, 0, null);
    }

    /** Trouble off the main thread that the app carried on through: out of memory, or any other error. */
    public static void background(Thread t, Throwable e) {
        note(isOom(e) ? OOM : BACKGROUND, t, e, 0, null);
    }

    /**
     * The main thread hasn't answered since at (wall clock) for seconds, last seen at stack: noted at once, then its note
     * made longer as it goes on (the same at).
     */
    public static synchronized void froze(long at, int seconds, StackTraceElement[] stack) {
        File f = file;
        if (f == null) return;
        String where = where(stack);
        List<Note> all = notes();
        Note last = all.isEmpty() ? null : all.get(all.size() - 1);
        if (last != null && FREEZE.equals(last.kind) && last.at == at) {
            replaceLast(f, new Note(at, FREEZE, "main", where.isEmpty() ? last.where : where, "-", "", Math.max(seconds, last.seconds)));
            return;
        }
        FREEZES.incrementAndGet();
        append(f, new Note(at, FREEZE, "main", where, "-", "", seconds));
    }

    private static void replaceLast(File f, Note n) {
        List<Note> all = new ArrayList<>(notes());
        if (!all.isEmpty()) all.remove(all.size() - 1);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        for (Note x : all) append(f, x);
        append(f, n);
    }

    /** Whether e is, or was caused by, running out of memory. */
    public static boolean isOom(Throwable e) {
        for (int i = 0; e != null && i < 8; i++, e = e.getCause()) if (e instanceof OutOfMemoryError) return true;
        return false;
    }

    private static synchronized void note(String kind, Thread t, Throwable e, int seconds, StackTraceElement[] stack) {
        File f = file;
        if (f == null) return;
        long now = System.currentTimeMillis();
        String where = where(e != null ? e.getStackTrace() : stack);
        // Out of memory where it was found short: a JVM fills in no frames for one it made itself.
        if (where.isEmpty() && e != null) where = where(cause(e).isEmpty() ? null : rootOf(e).getStackTrace());
        String error = e == null ? "-" : e.getClass().getSimpleName();
        String cause = cause(e);
        String thread = t == null ? "main" : threadName(t);
        if (OOM.equals(kind)) OOMS.incrementAndGet();
        else if (BACKGROUND.equals(kind)) TROUBLES.incrementAndGet();
        // A reading that fails every few seconds noted once a minute, not a hundred times.
        String key = kind + "|" + where + "|" + error;
        if (!CRASH.equals(kind) && key.equals(lastKey) && now - lastAt < QUIET_MS) return;
        lastKey = key;
        lastAt = now;
        append(f, new Note(now, kind, thread, where, error, cause, seconds));
    }

    /** Pure: a note added, the oldest let go past KEEP. */
    static void append(File f, Note n) {
        List<String> rows = new ArrayList<>();
        if (f.isFile() && f.length() < 64 * 1024) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                for (String l; (l = r.readLine()) != null; ) if (!l.isEmpty()) rows.add(l);
            } catch (IOException ignored) {
                rows.clear();
            }
        }
        rows.add(n.row());
        while (rows.size() > KEEP) rows.remove(0);
        File tmp = new File(f.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            for (String r : rows) {
                w.write(r);
                w.write('\n');
            }
        } catch (IOException e) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }

    /**
     * Pure: where it happened, as the app's own code says it: the first two frames in Homefield's own classes (the shared
     * scoreboard's too), "Class.method:line &lt; Class.method:line"; else the first frame at all.
     */
    static String where(StackTraceElement[] stack) {
        if (stack == null || stack.length == 0) return "";
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (StackTraceElement s : stack) {
            String c = s.getClassName();
            if (!c.startsWith("com.gathin.")) continue;
            if (n > 0) b.append(" < ");
            b.append(frame(s));
            if (++n == 2) break;
        }
        return n > 0 ? b.toString() : frame(stack[0]);
    }

    private static String frame(StackTraceElement s) {
        String c = s.getClassName();
        int dot = c.lastIndexOf('.');
        String simple = dot >= 0 ? c.substring(dot + 1) : c;
        return simple + "." + s.getMethodName() + (s.getLineNumber() > 0 ? ":" + s.getLineNumber() : "");
    }

    private static Throwable rootOf(Throwable e) {
        Throwable root = e;
        for (int i = 0; i < 8 && root.getCause() != null && root.getCause() != root; i++) root = root.getCause();
        return root;
    }

    /** Pure: the deepest cause's kind and where it was (never its message), "" when the error had none. */
    static String cause(Throwable e) {
        if (e == null) return "";
        Throwable root = e;
        for (int i = 0; i < 8 && root.getCause() != null && root.getCause() != root; i++) root = root.getCause();
        if (root == e) return "";
        String w = where(root.getStackTrace());
        return root.getClass().getSimpleName() + (w.isEmpty() ? "" : " in " + w);
    }

    /** A thread's name with its numbers kept (pool-2-thread-1, pics-0), never anything else. */
    private static String threadName(Thread t) {
        String n = t.getName();
        return n == null ? "?" : n.length() > 40 ? n.substring(0, 40) : n;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ');
    }

    /** Previews and tests: notes written to this file (null: none). */
    public static void useFile(File f) {
        file = f;
        lastKey = "";
        lastAt = 0;
    }

    /** Pure, for the tests: a note in the file's own words. */
    static Note noteOf(long at, String kind, String where, int seconds) {
        return new Note(at, kind, "main", where, "-", "", seconds);
    }

    /** A time for a note, as Settings says it: "Tue 24 Nov at 20:31". */
    public static String when(long at) {
        java.time.ZonedDateTime t = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault());
        return t.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.UK) + " " + t.getDayOfMonth() + " "
                + t.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.UK)
                + String.format(Locale.UK, " at %02d:%02d", t.getHour(), t.getMinute());
    }
}
