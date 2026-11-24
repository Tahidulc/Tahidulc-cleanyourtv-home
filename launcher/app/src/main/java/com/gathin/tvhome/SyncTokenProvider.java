package com.gathin.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/**
 * Hands Homefield TV on this TV its token for the link's /sync (phone/tvhome-phone/SYNC.md), so the two apps share the
 * family's playlists, favourites and the rest through this TV. call("token") answers only an app signed with this
 * app's own key: the caller's signing certificate is compared when it calls, which is what a signature permission
 * checks, without that permission's trap (one requested by an app installed before the app that defines it is never
 * granted). The token is only ever taken from the TV itself (DeliveryLink checks the caller is on loopback).
 * call("tmdb") answers the same apps with the theme's TMDB key (as /tmdb gives a paired phone). call("aikey") answers
 * Homefield TV alone (its package, signed with this app's key) with the family's Ask AI key as the phone's "Send key to
 * the TVs" left it here (AiKey's private preferences), so Homefield TV can take it when it has none of its own; the key is
 * never logged. Nothing else is served.
 */
public final class SyncTokenProvider extends ContentProvider {
    static final String AUTHORITY = "com.gathin.tvhome.sync";
    /** The one app the Ask AI key is handed to: Homefield TV, signed with this app's own key. */
    static final String HOMEFIELD_TV = "com.gathin.homefieldtv";

    @Override public boolean onCreate() {
        return true;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!"token".equals(method) && !"tmdb".equals(method) && !"aikey".equals(method)) return null;
        Context c = getContext();
        int uid = Binder.getCallingUid();
        if (c == null || !sameSigner(c.getPackageManager(), uid)) throw new SecurityException("not one of the family's own apps");
        Bundle b = new Bundle();
        if ("aikey".equals(method)) {
            // The family's Ask AI key, for Homefield TV only (its package and this app's signature, both checked): read from
            // AiKey's private preferences, handed over, never written to the log.
            b.putString("key", aiKeyFor(c.getPackageManager().getPackagesForUid(uid), true, com.gathin.broadcast.AiKey.get(c)));
            return b;
        }
        if ("tmdb".equals(method)) {
            // The theme's TMDB key, for Homefield TV's posters of titles only the family's TorBox has (as /tmdb gives a
            // paired phone); kept in its memory, never written out.
            b.putString("key", Config.themeString(c, "tmdb_key", "").trim());
            return b;
        }
        b.putString("token", SyncHub.appToken(c));
        b.putInt("port", DeliveryLink.PORT);
        return b;
    }

    /**
     * Pure: the Ask AI key handed to a caller (its packages, and whether it is signed with this app's key): the key ("" for
     * none here) to Homefield TV alone; a SecurityException for any other app, the family's own included.
     */
    static String aiKeyFor(String[] callerPackages, boolean sameSigner, String key) {
        if (!sameSigner || !isHomefieldTv(callerPackages)) throw new SecurityException("the Ask AI key is for Homefield TV only");
        return key == null ? "" : key.trim();
    }

    /** Pure: whether a caller's packages (by its uid) are Homefield TV's. */
    static boolean isHomefieldTv(String[] callerPackages) {
        if (callerPackages == null) return false;
        for (String p : callerPackages) if (HOMEFIELD_TV.equals(p)) return true;
        return false;
    }

    /** Whether the app calling (by its uid) is signed with the same certificate as this one. */
    static boolean sameSigner(PackageManager pm, int callingUid) {
        return pm != null && pm.checkSignatures(Process.myUid(), callingUid) == PackageManager.SIGNATURE_MATCH;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override public String getType(Uri uri) {
        return null;
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
