package com.gathin.homefieldtv.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.homefieldtv.engine.Channel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Settings → Parental control: a PIN of four numbers, and the channels and categories it locks. A locked channel asks for
 * the PIN before it plays (PinScreen), or is left out of every list until the PIN is entered (Hide them); Settings can
 * be locked too. Once the PIN is right, everything locked stays open until Homefield TV is left (or four hours, at
 * most). Without a PIN nothing is locked. The PIN is kept only as a salted hash; five wrong tries in a row wait 30
 * seconds before the next. Kept on this TV (and in its backups).
 */
public final class Parental {
    private Parental() {}

    static final String PREFS = "parental", PIN = "pin", GROUPS = "groups", CHANNELS = "channels", HIDE = "hide", SETTINGS = "settings";
    public static final int LENGTH = 4;
    /** Locked channels: asked for the PIN when chosen, or hidden until it is entered. */
    public static final String[] MODE_NAMES = {"Ask for the PIN", "Hide them"};
    public static final String[] MODE_ABOUT = {
            "They show in the lists; the PIN is asked before one plays",
            "Left out of every list, the guide and search until the PIN is entered in Settings"};
    static final long OPEN_MS = 4 * 3600_000L;
    static final int TRIES = 5;
    static final long WAIT_MS = 30_000L;

    private static volatile Set<String> groups, channels;
    private static volatile Boolean pinSet, hideMode;
    private static volatile long openUntil;
    private static int wrong;
    private static long waitUntil;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** What is kept, read again on next use (after a backup is restored, with a PIN of its own); locked again. */
    public static synchronized void forget() {
        groups = null;
        channels = null;
        pinSet = null;
        hideMode = null;
        openUntil = 0;
        wrong = 0;
        waitUntil = 0;
        Shown.changed();
    }

    private static synchronized void load(Context c) {
        if (groups != null && channels != null && pinSet != null && hideMode != null) return;
        SharedPreferences p = prefs(c);
        groups = Collections.unmodifiableSet(new HashSet<>(p.getStringSet(GROUPS, Collections.emptySet())));
        channels = Collections.unmodifiableSet(new HashSet<>(p.getStringSet(CHANNELS, Collections.emptySet())));
        pinSet = !p.getString(PIN, "").isEmpty();
        hideMode = p.getBoolean(HIDE, false);
    }

    // ---- the PIN --------------------------------------------------------------------------------------------------

    public static boolean hasPin(Context c) {
        Boolean b = pinSet;
        if (b == null) {
            load(c);
            b = pinSet;
        }
        return b;
    }

    /** Whether a PIN is four numbers. */
    public static boolean valid(String pin) {
        if (pin == null || pin.length() != LENGTH) return false;
        for (int i = 0; i < pin.length(); i++) if (pin.charAt(i) < '0' || pin.charAt(i) > '9') return false;
        return true;
    }

    /** A new PIN kept (as a salted hash); false when it isn't four numbers. Open now, as it was just typed. */
    public static synchronized boolean setPin(Context c, String pin) {
        if (!valid(pin)) return false;
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        String salt = hex(b);
        prefs(c).edit().putString(PIN, salt + ":" + hash(salt, pin)).apply();
        pinSet = true;
        unlock();
        return true;
    }

    /** Parental control off: the PIN gone (the locks are kept for the next PIN, and lock nothing without one). */
    public static synchronized void clearPin(Context c) {
        prefs(c).edit().remove(PIN).apply();
        pinSet = false;
        Shown.changed();
    }

    /** Whether the PIN is right; five wrong in a row and none is tried for 30 seconds (waitMs). */
    public static synchronized boolean check(Context c, String pin) {
        long now = System.currentTimeMillis();
        if (now < waitUntil || !valid(pin)) return false;
        String kept = prefs(c).getString(PIN, "");
        int colon = kept.indexOf(':');
        boolean right = colon > 0 && MessageDigest.isEqual(kept.substring(colon + 1).getBytes(StandardCharsets.US_ASCII),
                hash(kept.substring(0, colon), pin).getBytes(StandardCharsets.US_ASCII));
        if (right) {
            wrong = 0;
            return true;
        }
        if (++wrong >= TRIES) {
            wrong = 0;
            waitUntil = now + WAIT_MS;
        }
        return false;
    }

    /** How long before the PIN can be tried again (0: now). */
    public static synchronized long waitMs() {
        return Math.max(0, waitUntil - System.currentTimeMillis());
    }

    // ---- open until Homefield TV is left ----------------------------------------------------------------------------

    /** Whether the PIN was entered since Homefield TV was last left (and within four hours). */
    public static boolean open() {
        return System.currentTimeMillis() < openUntil;
    }

    public static void unlock() {
        openUntil = System.currentTimeMillis() + OPEN_MS;
        Shown.changed();
    }

    /** Homefield TV left: locked again. */
    public static void relock() {
        if (openUntil == 0) return;
        openUntil = 0;
        Shown.changed();
    }

    // ---- what is locked -------------------------------------------------------------------------------------------

    public static Set<String> lockedGroups(Context c) {
        Set<String> g = groups;
        if (g == null) {
            load(c);
            g = groups;
        }
        return g;
    }

    public static Set<String> lockedChannels(Context c) {
        Set<String> k = channels;
        if (k == null) {
            load(c);
            k = channels;
        }
        return k;
    }

    public static synchronized void lockGroup(Context c, String group, boolean on) {
        Set<String> s = new HashSet<>(lockedGroups(c));
        if (on) s.add(group);
        else s.remove(group);
        prefs(c).edit().putStringSet(GROUPS, s).apply();
        groups = Collections.unmodifiableSet(s);
        Shown.changed();
    }

    public static synchronized void lockChannel(Context c, String key, boolean on) {
        Set<String> s = new HashSet<>(lockedChannels(c));
        if (on) s.add(key);
        else s.remove(key);
        prefs(c).edit().putStringSet(CHANNELS, s).apply();
        channels = Collections.unmodifiableSet(s);
        Shown.changed();
    }

    /** Whether a channel is locked: a PIN is set and the channel, or its category, is locked. */
    public static boolean locked(Context c, Channel ch) {
        if (ch == null || !hasPin(c)) return false;
        return lockedChannels(c).contains(ch.key) || lockedGroups(c).contains(ch.group);
    }

    /** Whether a channel needs the PIN before it plays now. */
    public static boolean blocks(Context c, Channel ch) {
        return !open() && locked(c, ch);
    }

    /** Locked channels hidden (Hide them) rather than asking. */
    public static boolean hides(Context c) {
        Boolean b = hideMode;
        if (b == null) {
            load(c);
            b = hideMode;
        }
        return b;
    }

    public static synchronized void setHides(Context c, boolean hide) {
        prefs(c).edit().putBoolean(HIDE, hide).apply();
        hideMode = hide;
        Shown.changed();
    }

    /** Settings locked too (asked for the PIN before it opens). */
    public static boolean locksSettings(Context c) {
        return prefs(c).getBoolean(SETTINGS, false);
    }

    public static void setLocksSettings(Context c, boolean on) {
        prefs(c).edit().putBoolean(SETTINGS, on).apply();
    }

    /** Whether Settings needs the PIN before it opens now. */
    public static boolean settingsBlocked(Context c) {
        return hasPin(c) && locksSettings(c) && !open();
    }

    // ---- pure -----------------------------------------------------------------------------------------------------

    /** Pure: the PIN's hash with its salt (SHA-256, many rounds), as hex. */
    static String hash(String salt, String pin) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] h = (salt + "|" + pin).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 20_000; i++) {
                d.reset();
                d.update(h);
                d.update(salt.getBytes(StandardCharsets.UTF_8));
                h = d.digest();
            }
            return hex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) s.append(String.format(Locale.ROOT, "%02x", x & 0xFF));
        return s.toString();
    }
}
