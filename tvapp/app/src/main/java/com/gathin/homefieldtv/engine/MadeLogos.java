package com.gathin.homefieldtv.engine;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The channel logos the family makes on this TV (Change logo's Make my own and Ask AI: a LogoSpec LogoArt draws): each
 * kept as a see-through PNG in the app's own files ("logos-made", never in a backup), and chosen for its channel as
 * "made:" and the file's name ("made:m0123456789abcdef.png") in the channel's edits (ChannelEdits). Drawn from there as
 * a "file:" picture (Pics) wherever the channel's logo is drawn; one gone (a backup put back on another TV) is the
 * playlist's again. A new file a logo, so a picture already read is never the old one.
 */
public final class MadeLogos {
    private MadeLogos() {}

    public static final String PREFIX = "made:";
    static final String DIR = "logos-made";
    /** The pictures' height when kept (LogoArt draws them as wide as their layout needs: square, or 2.2 times as wide). */
    public static final int HEIGHT = 288;

    private static volatile File dir;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Each choice's address, made once (nothing made while the rows are drawn). */
    private static final Map<String, String> urls = new ConcurrentHashMap<>();

    /** Where they are kept (the app starting: LogoPack.init). */
    public static void init(Context c) {
        if (dir == null) dir = new File(c.getApplicationContext().getFilesDir(), DIR);
    }

    /** Pure: whether a channel's logo choice is one made on this TV. */
    public static boolean is(String choice) {
        return choice != null && choice.startsWith(PREFIX);
    }

    /** Pure: whether a made logo's file name is one this makes (nothing else is ever read or served). */
    public static boolean validName(String name) {
        return name != null && name.matches("m[0-9a-f]{16}\\.png");
    }

    /** Pure: a choice's file name, or null when it isn't a made logo's. */
    public static String nameOf(String choice) {
        if (!is(choice)) return null;
        String n = choice.substring(PREFIX.length());
        return validName(n) ? n : null;
    }

    /** A made logo's file (null: not one, or not set up yet). */
    public static File file(String choice) {
        String n = nameOf(choice);
        File d = dir;
        return n == null || d == null ? null : new File(d, n);
    }

    /** A made logo's file by its name (the cards' provider): null for a name this never makes. */
    public static File fileNamed(Context c, String name) {
        init(c);
        return validName(name) ? new File(dir, name) : null;
    }

    /** The picture's address for a choice ("file:…"), made once; null for one that isn't a made logo's. */
    public static String url(String choice) {
        String u = urls.get(choice);
        if (u != null) return u;
        File f = file(choice);
        if (f == null) return null;
        u = "file:" + f.getPath();
        urls.put(choice, u);
        return u;
    }

    /** Pure: a new file's name. */
    static String newName() {
        return String.format(Locale.ROOT, "m%016x.png", RANDOM.nextLong());
    }

    /**
     * A logo kept (written whole first, then put in place): the choice to keep for its channel. Off the main thread.
     */
    public static String save(Context c, Bitmap logo) throws IOException {
        init(c);
        File d = dir;
        if (!d.exists() && !d.mkdirs()) throw new IOException("no folder for made logos");
        String name = newName();
        File f = new File(d, name), tmp = new File(d, name + ".part");
        try (OutputStream o = new FileOutputStream(tmp)) {
            if (!logo.compress(Bitmap.CompressFormat.PNG, 100, o)) throw new IOException("not written");
        }
        if (!tmp.renameTo(f)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("not kept");
        }
        return PREFIX + name;
    }

    /** The made logos no channel uses any more let go of (their names in keep stay). Off the main thread. */
    public static int prune(Context c, Set<String> keep) {
        init(c);
        File[] all = dir.listFiles();
        if (all == null) return 0;
        int n = 0;
        for (File f : all) {
            String name = f.getName();
            if (keep.contains(PREFIX + name)) continue;
            if (validName(name) || name.endsWith(".part")) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                n++;
            }
        }
        return n;
    }
}
