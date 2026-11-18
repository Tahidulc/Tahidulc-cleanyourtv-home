package com.gathin.homefieldtv;

/** Scratch: allocation probes (removed before committing). */
final class DebugAlloc {
    static java.util.function.LongSupplier now;
    static final java.util.Map<String, long[]> SUMS = new java.util.TreeMap<>();
    static long t;

    static void mark() {
        if (now != null) t = now.getAsLong();
    }

    static void add(String name) {
        if (now == null) return;
        long n = now.getAsLong();
        long[] s = SUMS.computeIfAbsent(name, k -> new long[2]);
        s[0] += n - t;
        s[1]++;
        t = now.getAsLong();
    }
}
