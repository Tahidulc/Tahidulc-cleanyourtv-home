package com.gathin.homefieldtv.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * Settings → Guide → Guide time shift, as the guide is read: the guide's window and each catch-up channel's earliest
 * programme moved into the guide's own (wrong) time, then every programme moved by the shift into the real time, so
 * the guide, its now and next, catch-up and reminders all go by the real time.
 */
final class GuideShift {
    private GuideShift() {}

    /** Pure: a schedule with every programme moved by ms (the same schedule for none). */
    static Guide.Schedule by(Guide.Schedule s, long ms) {
        if (s == null || ms == 0) return s;
        int n = s.start.length;
        long[] a = new long[n], b = new long[n];
        for (int i = 0; i < n; i++) {
            a[i] = s.start[i] + ms;
            b[i] = s.stop[i] + ms;
        }
        return s.at(a, b);
    }

    /** Pure: catch-up's earliest times (by channel key) in the guide's own time: moved back by the shift. */
    static Map<String, Long> since(Map<String, Long> since, long ms) {
        if (since == null || ms == 0) return since;
        Map<String, Long> out = new HashMap<>(since.size() * 2);
        for (Map.Entry<String, Long> e : since.entrySet()) out.put(e.getKey(), e.getValue() - ms);
        return out;
    }
}
