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
 * granted). The token is only ever taken from the TV itself (DeliveryLink checks the caller is on loopback). Nothing
 * else is served here.
 */
public final class SyncTokenProvider extends ContentProvider {
    static final String AUTHORITY = "com.gathin.tvhome.sync";

    @Override public boolean onCreate() {
        return true;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!"token".equals(method)) return null;
        Context c = getContext();
        if (c == null || !sameSigner(c.getPackageManager(), Binder.getCallingUid())) throw new SecurityException("not one of the family's own apps");
        Bundle b = new Bundle();
        b.putString("token", SyncHub.appToken(c));
        b.putInt("port", DeliveryLink.PORT);
        return b;
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
