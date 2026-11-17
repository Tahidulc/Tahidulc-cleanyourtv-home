package com.gathin.tvhome;

import android.content.ComponentCallbacks2;

import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MemoryWatchTest {
    @Test
    @SuppressWarnings("deprecation")
    public void cachesLetGoByHowBadItIs() {
        assertEquals(1f, MemoryWatch.trimShare(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN), 0f);
        assertEquals(0.75f, MemoryWatch.trimShare(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE), 0f);
        assertEquals(0.5f, MemoryWatch.trimShare(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW), 0f);
        assertEquals(0f, MemoryWatch.trimShare(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL), 0f);
        assertEquals(0f, MemoryWatch.trimShare(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND), 0f);
        assertEquals(0f, MemoryWatch.trimShare(ComponentCallbacks2.TRIM_MEMORY_COMPLETE), 0f);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void selfTrimWhenNativeMemoryRunsAwayButNotTooOften() {
        long big = MemoryWatch.SELF_TRIM_BYTES, small = 200L * 1024 * 1024;
        assertEquals(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, MemoryWatch.selfTrimLevel(big, false, Long.MAX_VALUE));
        assertEquals(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, MemoryWatch.selfTrimLevel(small, true, Long.MAX_VALUE));
        assertEquals(0, MemoryWatch.selfTrimLevel(small, false, Long.MAX_VALUE));
        assertEquals("at most every ten minutes", 0, MemoryWatch.selfTrimLevel(big, true, MemoryWatch.SELF_TRIM_EVERY_MS - 1));
        assertEquals(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, MemoryWatch.selfTrimLevel(big, true, MemoryWatch.SELF_TRIM_EVERY_MS));
    }

    @Test
    public void procStatusRead() {
        String status = "Name:\tcom.gathin.tvhome\nVmRSS:\t  519168 kB\nVmSwap:\t 1228800 kB\nThreads:\t123\n";
        assertEquals(519168, MemoryWatch.statusKb(status, "VmRSS:"));
        assertEquals(1228800, MemoryWatch.statusKb(status, "VmSwap:"));
        assertEquals(123, MemoryWatch.statusKb(status, "Threads:"));
        assertEquals(-1, MemoryWatch.statusKb(status, "VmPTE:"));
        assertEquals(-1, MemoryWatch.statusKb(null, "VmRSS:"));
    }

    @Test
    public void aLineHasEveryFigureAndNothingPersonal() {
        MemoryWatch.Stats s = new MemoryWatch.Stats();
        s.time = LocalDateTime.of(2026, 11, 17, 15, 17, 0);
        s.upMin = 387;
        s.nativeKb = 1_539_000;
        s.nativeSizeKb = 1_600_000;
        s.nativeDeltaKb = 6_012;
        s.javaKb = 34_000;
        s.javaMaxKb = 512_000;
        s.rssKb = 507_000;
        s.swapKb = 1_200_000;
        s.threads = 123;
        s.availMb = 190;
        s.low = true;
        s.cachesKb = 48_000;
        s.caches = 14;
        s.big = "home:30000K,guide:8000K";
        s.fonts = 12;
        s.webViews = 1;
        s.gpu = 2;
        s.shots = 120;
        s.parts = 180;
        s.ocr = 3;
        s.ocrOpen = 1;
        s.front = "homefield";
        String l = MemoryWatch.line(s);
        assertTrue(l, l.startsWith("t=2026-11-17T15:17:00 up=387m nat=1539000K/1600000K(+6012K) java=34000K/512000K"));
        for (String key : new String[]{" rss=507000K", " swap=1200000K", " thr=123", " avail=190M", " low=1", " caches=48000K/14",
                " big=home:30000K,guide:8000K", " fonts=12", " web=1", " intro=0", " gpu=2", " shots=120", " parts=180", " ocr=3",
                " ocrOpen=1", " front=homefield", " board=0"}) {
            assertTrue(key + " in " + l, l.contains(key));
        }
        assertFalse(l.contains("\n"));
        s.nativeDeltaKb = -12;
        assertTrue(MemoryWatch.line(s).contains("(-12K)"));
    }

    @Test
    public void appsNamedOnlyByKind() {
        assertEquals("tivimate", MemoryWatch.kind("ar.tvplayer.tv"));
        assertEquals("homefield", MemoryWatch.kind("com.gathin.homefieldtv"));
        assertEquals("kleva", MemoryWatch.kind("com.plaintextdev.klevaTv"));
        assertEquals("other", MemoryWatch.kind("com.netflix.ninja"));
        assertEquals("?", MemoryWatch.kind(null));
    }

    @Test
    public void theFileKeepsTheLastDayUnderItsHeader() {
        List<String> lines = new ArrayList<>();
        lines.add(MemoryWatch.HEADER);
        for (int i = 0; i < 2_000; i++) lines.add("t=" + i);
        List<String> kept = MemoryWatch.keep(lines, MemoryWatch.KEEP_LINES);
        assertEquals(MemoryWatch.KEEP_LINES + 1, kept.size());
        assertEquals(MemoryWatch.HEADER, kept.get(0));
        assertEquals("t=500", kept.get(1));
        assertEquals("t=1999", kept.get(kept.size() - 1));
        // a day of minutes fits
        assertTrue(MemoryWatch.KEEP_LINES >= 24 * 60);
        // a short file is kept whole, the header once
        List<String> few = MemoryWatch.keep(java.util.Arrays.asList(MemoryWatch.HEADER, "a", "", "b"), 10);
        assertEquals(java.util.Arrays.asList(MemoryWatch.HEADER, "a", "b"), few);
    }

    @Test
    public void eventsAreOneLine() {
        String e = MemoryWatch.event("trim", "level=15 freed=1024K");
        assertTrue(e, e.startsWith("t="));
        assertTrue(e, e.endsWith(" event=trim level=15 freed=1024K"));
    }
}
