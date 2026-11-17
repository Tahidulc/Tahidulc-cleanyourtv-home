package com.gathin.tvhome;

import android.graphics.Bitmap;

/**
 * A picture cache held to a size in bytes (ByteLru): each picture counted by the memory its pixels take. What it lets go
 * is never recycled here, as a picture may still be on the screen; once nothing shows it, the memory comes back.
 * Registered for memory warnings and the self-check (MemoryWatch) by being one.
 */
final class BitmapCache extends ByteLru<String, Bitmap> {
    private static final Sizer<Bitmap> PIXELS = b -> b == null || b.isRecycled() ? 0 : b.getAllocationByteCount();

    BitmapCache(String name, long maxBytes) {
        super(name, maxBytes, PIXELS);
    }

    /** Up to share of what the app may use (its Java limit, a fair guide on these TVs), at most cap bytes. */
    static long share(int divisor, long cap) {
        return Math.min(Runtime.getRuntime().maxMemory() / Math.max(1, divisor), cap);
    }
}
