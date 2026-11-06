package com.gathin.homefieldtv.engine;

import android.content.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * The family's own playlist: an M3U address (and, if they have one, its guide's), or an Xtream Codes login. Kept in
 * this app's private storage on this TV only: never sent anywhere but its own server, never logged, never shown in full
 * (the password stays masked).
 */
public final class Source {
    public static final String M3U = "m3u", XTREAM = "xtream";
    private static final String PREFS = "live_source";

    public final String type, m3uUrl, guideUrl, server, user, pass;

    public Source(String type, String m3uUrl, String guideUrl, String server, String user, String pass) {
        this.type = type == null ? "" : type;
        this.m3uUrl = clean(m3uUrl);
        this.guideUrl = clean(guideUrl);
        this.server = clean(server);
        this.user = user == null ? "" : user.trim();
        this.pass = pass == null ? "" : pass;
    }

    public boolean usable() {
        if (M3U.equals(type)) return !m3uUrl.isEmpty();
        if (XTREAM.equals(type)) return !server.isEmpty() && !user.isEmpty() && !pass.isEmpty();
        return false;
    }

    /** A short fingerprint of the source, for the cached channels to know they belong to it. */
    String fingerprint() {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] h = d.digest((type + "\n" + m3uUrl + "\n" + guideUrl + "\n" + server + "\n" + user + "\n" + pass).getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 8; i++) b.append(String.format("%02x", h[i]));
            return b.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString((type + m3uUrl + server + user).hashCode());
        }
    }

    /** What Settings shows: the kind, and the address's host (never the login). */
    public String describe() {
        String host = host(M3U.equals(type) ? m3uUrl : server);
        return (M3U.equals(type) ? "M3U playlist" : "Xtream Codes") + (host.isEmpty() ? "" : " · " + host);
    }

    static String host(String url) {
        String u = url == null ? "" : url;
        int s = u.indexOf("://");
        if (s >= 0) u = u.substring(s + 3);
        int at = u.lastIndexOf('@', u.indexOf('/') < 0 ? u.length() : u.indexOf('/'));
        if (at >= 0) u = u.substring(at + 1);
        int end = u.length();
        for (char c : new char[]{'/', '?', '#'}) {
            int i = u.indexOf(c);
            if (i >= 0) end = Math.min(end, i);
        }
        return u.substring(0, end);
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    /** The first playlist in use (Sources), or null when none is set up. */
    public static Source load(Context c) {
        List<Sources.Entry> in = Sources.inUse(c);
        return in.isEmpty() ? null : in.get(0).source;
    }
}
