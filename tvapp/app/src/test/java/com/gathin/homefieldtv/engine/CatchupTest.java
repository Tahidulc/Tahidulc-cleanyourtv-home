package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catch-up's addresses from made-up providers (every host here is invented): the Xtream Codes forms in the server's time
 * zone, each M3U kind and template field, what a playlist's attributes say, and how far back an archive goes.
 */
public class CatchupTest {
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final String XTREAM_TS = "http://box.example:8080/live/viewer/secret/101.ts";
    private static final String XTREAM_HLS = "http://box.example:8080/live/viewer/secret/101.m3u8";
    private static final long MIN = 60_000L, DAY = 86_400_000L;

    private static long utc(int y, int mo, int d, int h, int m, int s) {
        return ZonedDateTime.of(y, mo, d, h, m, s, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    /** 16 Nov 2026, 20:05 UTC (GMT in London), an hour long; asked for at 22:30. */
    private static final long START = utc(2026, 11, 16, 20, 5, 0), END = START + 60 * MIN, NOW = utc(2026, 11, 16, 22, 30, 0);
    private static final long S = START / 1000, E = END / 1000, N = NOW / 1000;

    // ---- what a playlist says ---------------------------------------------------------------------------------------

    @Test public void xtreamArchiveDays() {
        assertEquals(7, Catchup.xtreamDays("1", "7"));
        assertEquals(3, Catchup.xtreamDays(" 1 ", "3"));
        assertEquals(2, Catchup.xtreamDays("true", "2"));
        // Marked as kept but for no length said: a day.
        assertEquals(Catchup.DEFAULT_DAYS, Catchup.xtreamDays("1", ""));
        assertEquals(Catchup.DEFAULT_DAYS, Catchup.xtreamDays("1", null));
        assertEquals(31, Catchup.xtreamDays("1", "400"));
        assertEquals(0, Catchup.xtreamDays("0", "7"));
        assertEquals(0, Catchup.xtreamDays("", "7"));
        assertEquals(0, Catchup.xtreamDays(null, null));
    }

    @Test public void m3uKinds() {
        assertEquals(Catchup.DEFAULT, Catchup.kind("Default"));
        assertEquals(Catchup.APPEND, Catchup.kind("append"));
        assertEquals(Catchup.SHIFT, Catchup.kind("shift"));
        assertEquals(Catchup.SHIFT, Catchup.kind("timeshift"));
        assertEquals(Catchup.FLUSSONIC, Catchup.kind("flussonic"));
        assertEquals(Catchup.FLUSSONIC, Catchup.kind("flussonic-hls"));
        assertEquals(Catchup.FLUSSONIC, Catchup.kind("fs"));
        assertEquals(Catchup.XC, Catchup.kind(" XC "));
        assertEquals("", Catchup.kind("vod"));
        assertEquals("", Catchup.kind(null));
    }

    @Test public void m3uAttributes() {
        String live = "http://tv.example/hls/ch1.m3u8";
        assertArrayEquals(new String[]{"append", "3", "?utc={utc}&lutc={lutc}"},
                Catchup.m3u("append", "3", "?utc={utc}&lutc={lutc}", null, null, live));
        // A source with no kind: the whole address (default).
        assertArrayEquals(new String[]{"default", "1", "http://archive.example/ch1/{utc}.ts"},
                Catchup.m3u(null, null, "http://archive.example/ch1/{utc}.ts", null, null, live));
        // Days with no kind: an Xtream archive on an Xtream address, else shift.
        assertArrayEquals(new String[]{"xc", "5", ""}, Catchup.m3u(null, "5", null, null, null, XTREAM_TS));
        assertArrayEquals(new String[]{"shift", "5", ""}, Catchup.m3u(null, "5", null, null, null, live));
        // The older words for the days: timeshift, then tvg-rec.
        assertArrayEquals(new String[]{"xc", "7", ""}, Catchup.m3u(null, null, null, "7", null, XTREAM_TS));
        assertArrayEquals(new String[]{"shift", "2", ""}, Catchup.m3u(null, null, null, null, "2", live));
        assertArrayEquals(new String[]{"shift", "1", ""}, Catchup.m3u("shift", null, null, null, null, live));
        assertArrayEquals(new String[]{"flussonic", "31", ""}, Catchup.m3u("flussonic", "365", "", null, null, live));
        // None: said not to, a kind not known, default without its source, xc on an address that isn't Xtream's.
        assertNull(Catchup.m3u("vod", "7", null, null, null, live));
        assertNull(Catchup.m3u("disabled", null, "http://archive.example/{utc}", null, null, live));
        assertNull(Catchup.m3u("default", "3", "", null, null, live));
        assertNull(Catchup.m3u("xc", "3", null, null, null, live));
        assertNull(Catchup.m3u(null, null, null, null, null, live));
        assertNull(Catchup.m3u(null, "0", "", "0", "", live));
    }

    @Test public void archiveWindow() {
        assertTrue(Catchup.inArchive(1, NOW - 2 * 3600_000L, NOW));
        assertTrue(Catchup.inArchive(1, NOW - DAY, NOW));
        assertFalse(Catchup.inArchive(1, NOW - DAY - 1, NOW));
        assertTrue(Catchup.inArchive(7, NOW - 6 * DAY, NOW));
        // Not started yet, or no archive.
        assertFalse(Catchup.inArchive(3, NOW + MIN, NOW));
        assertFalse(Catchup.inArchive(0, NOW - MIN, NOW));
    }

    // ---- Xtream Codes -----------------------------------------------------------------------------------------------

    @Test public void xtreamParts() {
        assertArrayEquals(new String[]{"http://box.example:8080", "viewer", "secret", "101", "ts"}, Catchup.xtreamParts(XTREAM_TS));
        assertArrayEquals(new String[]{"https://box.example", "a%20b", "p", "7", "m3u8"},
                Catchup.xtreamParts("https://box.example/live/a%20b/p/7.m3u8"));
        // The older M3U form, without /live/ or an extension.
        assertArrayEquals(new String[]{"http://box.example:25461", "viewer", "secret", "42", ""},
                Catchup.xtreamParts("http://box.example:25461/viewer/secret/42"));
        assertNull(Catchup.xtreamParts("http://tv.example/hls/ch1.m3u8"));
        assertNull(Catchup.xtreamParts("http://box.example/live/viewer/secret/abc.ts"));
        assertNull(Catchup.xtreamParts(null));
    }

    @Test public void theServersTimeZone() {
        assertEquals(LONDON, Catchup.zone("Europe/London"));
        assertEquals(ZoneOffset.UTC, Catchup.zone(""));
        assertEquals(ZoneOffset.UTC, Catchup.zone(null));
        assertEquals(ZoneOffset.UTC, Catchup.zone("Not/AZone"));
        // An Xtream server reads the start in its own time, to the minute.
        assertEquals("2026-11-16:20-05", Catchup.xtreamStart(START, LONDON));
        assertEquals("2026-11-16:15-05", Catchup.xtreamStart(START, ZoneId.of("America/New_York")));
        assertEquals("2026-11-17:02-05", Catchup.xtreamStart(START, ZoneId.of("Asia/Dhaka")));
        assertEquals("2026-11-16:21-05", Catchup.xtreamStart(START, ZoneId.of("Europe/Amsterdam")));
        // British Summer Time.
        assertEquals("2026-07-01:13-00", Catchup.xtreamStart(utc(2026, 7, 1, 12, 0, 0), LONDON));
        assertEquals("2026-11-16:20-05", Catchup.xtreamStart(START + 59_000L, LONDON));
    }

    @Test public void minutesAskedFor() {
        assertEquals(60, Catchup.minutes(START, END));
        assertEquals(1, Catchup.minutes(START, START + MIN));
        assertEquals(2, Catchup.minutes(START, START + MIN + 1000));
        assertEquals(1, Catchup.minutes(START, START));
    }

    @Test public void xtreamAddresses() {
        assertEquals("http://box.example:8080/timeshift/viewer/secret/60/2026-11-16:20-05/101.ts",
                Catchup.xtream(XTREAM_TS, START, 60, LONDON));
        assertEquals("http://box.example:8080/timeshift/viewer/secret/60/2026-11-16:20-05/101.m3u8",
                Catchup.xtream(XTREAM_HLS, START, 60, LONDON));
        assertEquals("http://box.example:25461/timeshift/viewer/secret/90/2026-11-16:15-05/42.ts",
                Catchup.xtream("http://box.example:25461/viewer/secret/42", START, 90, ZoneId.of("America/New_York")));
        assertEquals("http://box.example:8080/streaming/timeshift.php?username=viewer&password=secret&stream=101"
                + "&start=2026-11-16:20-05&duration=60", Catchup.xtreamPhp(XTREAM_TS, START, 60, LONDON));
        assertNull(Catchup.xtream("http://tv.example/hls/ch1.m3u8", START, 60, LONDON));
        assertNull(Catchup.xtreamPhp("http://tv.example/hls/ch1.m3u8", START, 60, LONDON));
        // Through url(): the server's zone by name, the minutes from start to end.
        assertEquals("http://box.example:8080/timeshift/viewer/secret/60/2026-11-17:02-05/101.ts",
                Catchup.url(Catchup.XC, "", XTREAM_TS, "Asia/Dhaka", START, END, NOW));
        assertEquals("http://box.example:8080/timeshift/viewer/secret/60/2026-11-16:20-05/101.ts",
                Catchup.url(Catchup.XC, "", XTREAM_TS, "", START, END, NOW));
    }

    // ---- M3U templates ----------------------------------------------------------------------------------------------

    @Test public void startEndAndNow() {
        assertEquals(S + "|" + S + "|" + S, Catchup.fill("{utc}|${start}|{start}", START, END, NOW));
        assertEquals(E + "|" + E + "|" + E, Catchup.fill("{utcend}|${end}|{end}", START, END, NOW));
        assertEquals(N + "|" + N + "|" + N + "|" + N, Catchup.fill("{lutc}|${now}|{now}|${timestamp}", START, END, NOW));
    }

    @Test public void durationAndOffset() {
        assertEquals("3600|3600|60", Catchup.fill("{duration}|${duration}|{duration:60}", START, END, NOW));
        assertEquals((N - S) + "|" + (N - S) / 60, Catchup.fill("{offset}|{offset:60}", START, END, NOW));
        assertEquals(String.valueOf(N - S), Catchup.fill("${offset}", START, END, NOW));
    }

    @Test public void datesAndTimes() {
        assertEquals("2026-11-16 20:05:00", Catchup.fill("{Y}-{m}-{d} {H}:{M}:{S}", START, END, NOW));
        assertEquals("2026/11/16/20", Catchup.fill("{Y}/{m}/{d}/{H}", START, END, NOW));
        assertEquals("2026-11-16:20-05", Catchup.fill("{utc:Y-m-d:H-M}", START, END, NOW));
        assertEquals("20261116200500", Catchup.fill("${start:YmdHMS}", START, END, NOW));
        assertEquals("2026-11-16-21-05", Catchup.fill("{end:Y-m-d-H-M}", START, END, NOW));
        assertEquals("22:30", Catchup.fill("{lutc:H:M}", START, END, NOW));
    }

    @Test public void fieldsNotKnownStayAsTheyAre() {
        assertEquals("http://archive.example/{channel}/x", Catchup.fill("http://archive.example/{channel}/x", START, END, NOW));
        assertEquals("id=", Catchup.fill("id={catchup-id}", START, END, NOW));
        assertEquals("", Catchup.fill(null, START, END, NOW));
        // Characters a replacement would treat specially are kept.
        assertEquals("$1\\x" + S, Catchup.fill("$1\\x{utc}", START, END, NOW));
    }

    @Test public void eachKind() {
        String live = "http://tv.example/hls/ch1.m3u8";
        assertEquals("http://archive.example/ch1/" + S + "-3600.ts",
                Catchup.url(Catchup.DEFAULT, "http://archive.example/ch1/{utc}-{duration}.ts", live, "", START, END, NOW));
        assertEquals(live + "?utc=" + S + "&lutc=" + N, Catchup.url(Catchup.APPEND, "?utc={utc}&lutc={lutc}", live, "", START, END, NOW));
        // Appended to an address that already asks something: joined with &.
        assertEquals(live + "?token=abc&utc=" + S + "&lutc=" + N,
                Catchup.url(Catchup.APPEND, "?utc={utc}&lutc={lutc}", live + "?token=abc", "", START, END, NOW));
        assertEquals(live + "/" + S + "/3600", Catchup.url(Catchup.APPEND, "/{utc}/{duration}", live, "", START, END, NOW));
        assertEquals(live + "?utc=" + S + "&lutc=" + N, Catchup.url(Catchup.SHIFT, "", live, "", START, END, NOW));
        assertEquals(live + "?token=abc&utc=" + S + "&lutc=" + N, Catchup.url(Catchup.SHIFT, "", live + "?token=abc", "", START, END, NOW));
        // None: default with no source, append with nothing to add, a kind not known, no live address.
        assertNull(Catchup.url(Catchup.DEFAULT, "", live, "", START, END, NOW));
        assertNull(Catchup.url(Catchup.APPEND, "", live, "", START, END, NOW));
        assertNull(Catchup.url("vod", "", live, "", START, END, NOW));
        assertNull(Catchup.url(Catchup.SHIFT, "", "", "", START, END, NOW));
        assertNull(Catchup.url(null, "", live, "", START, END, NOW));
    }

    @Test public void flussonic() {
        assertEquals("http://fl.example/ch1/index-" + S + "-3600.m3u8?token=t",
                Catchup.url(Catchup.FLUSSONIC, "", "http://fl.example/ch1/index.m3u8?token=t", "", START, END, NOW));
        assertEquals("http://fl.example/ch1/video-" + S + "-3600.m3u8",
                Catchup.url(Catchup.FLUSSONIC, "", "http://fl.example/ch1/video.m3u8", "", START, END, NOW));
        assertEquals("http://fl.example/ch1/timeshift_abs-" + S + ".ts?token=t",
                Catchup.url(Catchup.FLUSSONIC, "", "http://fl.example/ch1/mpegts?token=t", "", START, END, NOW));
        assertEquals("http://fl.example/ch1/timeshift_abs-" + S + ".ts",
                Catchup.url(Catchup.FLUSSONIC, "", "http://fl.example/ch1", "", START, END, NOW));
        assertEquals("http://fl.example/ch1/timeshift_abs-" + S + ".ts?t=1",
                Catchup.url(Catchup.FLUSSONIC, "", "http://fl.example/ch1/?t=1", "", START, END, NOW));
    }

    // ---- a channel's archive ------------------------------------------------------------------------------------------

    private static Channel channel(String key, String url, String kind, String source, int days, String zone) {
        return new Channel(key, "Made Up One", "Made up", "", url, "made.up", "", "", 0, kind, source, days, zone);
    }

    @Test public void aChannelsArchive() {
        Channel none = channel("m:1", "http://tv.example/hls/ch1.m3u8", "", "", 0, "");
        Channel kept = channel("x:101", XTREAM_TS, Catchup.XC, "", 2, "Europe/London");
        assertNull(Catchup.source(none));
        assertNull(Catchup.url(none, START, END, NOW));
        assertFalse(Catchup.has(none, START, NOW));
        assertEquals(Long.MAX_VALUE, Catchup.earliest(none, NOW));
        assertSame(kept, Catchup.source(kept));
        assertTrue(Catchup.has(kept, START, NOW));
        assertFalse(Catchup.has(kept, NOW - 3 * DAY, NOW));
        assertEquals(NOW - 2 * DAY, Catchup.earliest(kept, NOW));
        assertEquals("http://box.example:8080/timeshift/viewer/secret/60/2026-11-16:20-05/101.ts", Catchup.url(kept, START, END, NOW));
        assertEquals("http://box.example:8080/streaming/timeshift.php?username=viewer&password=secret&stream=101"
                + "&start=2026-11-16:20-05&duration=60", Catchup.olderUrl(kept, START, END));
        // A kind with no days is no archive.
        assertEquals(0, channel("m:2", "http://tv.example/x", Catchup.SHIFT, "", 0, "").archiveDays);
        assertEquals(0, channel("m:3", "http://tv.example/x", "", "", 5, "").archiveDays);
    }

    @Test public void theArchiveOfTheSameChannelInAnotherPlaylist() {
        Channel main = channel("m:1", "http://tv.example/hls/ch1.m3u8", "", "", 0, "");
        Channel other = channel("p2:x:101", XTREAM_TS, Catchup.XC, "", 3, "");
        main.alternates.add(other);
        assertSame(other, Catchup.source(main));
        assertTrue(Catchup.has(main, START, NOW));
        assertEquals("http://box.example:8080/timeshift/viewer/secret/60/2026-11-16:20-05/101.ts", Catchup.url(main, START, END, NOW));
        assertNull(Catchup.olderUrl(channel("m:4", "http://tv.example/x", Catchup.SHIFT, "", 1, ""), START, END));
    }

    @Test public void countsAndSettingsLine() {
        Channel a = channel("x:1", XTREAM_TS, Catchup.XC, "", 7, "");
        Channel b = channel("x:2", XTREAM_TS, Catchup.XC, "", 3, "");
        Channel c = channel("m:3", "http://tv.example/x", "", "", 0, "");
        assertArrayEquals(new int[]{2, 7}, Catchup.count(Arrays.asList(a, b, c)));
        assertArrayEquals(new int[]{0, 0}, Catchup.count(Arrays.asList(c)));
        assertEquals("Catch-up: 2 channels keep programmes for up to 7 days", Catchup.summary(2, 7));
        assertEquals("Catch-up: 1 channel keeps programmes for up to 1 day", Catchup.summary(1, 1));
        assertEquals("Catch-up: 1,250 channels keep programmes for up to 3 days", Catchup.summary(1250, 3));
        assertEquals("Your provider keeps no catch-up for these channels", Catchup.summary(0, 0));
    }

    @Test public void whereToAskFrom() {
        long t = utc(2026, 11, 16, 20, 42, 37);
        // An Xtream server's archive starts on the minute; the others to the second.
        assertEquals(utc(2026, 11, 16, 20, 42, 0), Catchup.requestAt(Catchup.XC, t, t, -1));
        assertEquals(utc(2026, 11, 16, 20, 42, 37), Catchup.requestAt(Catchup.SHIFT, t + 400, t, -1));
        // Moving on never goes back: on to the next minute.
        long on = t + 10_000L;
        assertEquals(utc(2026, 11, 16, 20, 43, 0), Catchup.requestAt(Catchup.XC, on, t, 1));
        assertEquals(utc(2026, 11, 16, 20, 44, 0), Catchup.requestAt(Catchup.XC, utc(2026, 11, 16, 20, 44, 5), t, 1));
        assertEquals(60_000L, Catchup.step(Catchup.XC));
        assertEquals(1000L, Catchup.step(Catchup.DEFAULT));
        // How far to ask for: past the programme's end (or half an hour), at most four hours.
        assertEquals(END + 5 * MIN, Catchup.requestEnd(START, END));
        assertEquals(START + 35 * MIN, Catchup.requestEnd(START, START + 10 * MIN));
        assertEquals(START + 4 * 3600_000L, Catchup.requestEnd(START, START + 6 * 3600_000L));
    }

    @Test public void anXtreamM3uGivenItsArchiveByStreamId() {
        Channel a = new Channel("bbc|One", "One", "", "", "http://box.example:8080/viewer/secret/101", "", "", "", 0);
        Channel b = new Channel("x|Two", "Two", "", "", XTREAM_TS.replace("101", "202"), "", "", "", 0);
        Channel c = new Channel("y|Three", "Three", "", "", "http://tv.example/hls/three.m3u8", "", "", "", 0);
        Channel d = channel("z|Four", XTREAM_TS.replace("101", "404"), Catchup.XC, "", 2, "");
        Map<String, Integer> days = new HashMap<>();
        days.put("101", 5);
        days.put("303", 7);
        List<Channel> out = Catchup.withXtream(Arrays.asList(a, b, c, d), days, "Europe/London");
        assertEquals(Catchup.XC, out.get(0).catchup);
        assertEquals(5, out.get(0).archiveDays);
        assertEquals("Europe/London", out.get(0).zone);
        assertEquals("bbc|One", out.get(0).key);
        // Not in the archive, not Xtream's: as they were.
        assertSame(b, out.get(1));
        assertSame(c, out.get(2));
        // Its own catch-up kept, given the server's time zone.
        assertEquals(2, out.get(3).archiveDays);
        assertEquals("Europe/London", out.get(3).zone);
        // No archive known: the zone still goes on what the playlist said.
        List<Channel> none = Catchup.withXtream(Arrays.asList(a, d), null, "");
        assertSame(a, none.get(0));
        assertSame(d, none.get(1));
    }

    @Test public void aChannelUnderAnotherKeyKeepsItsArchive() {
        Channel kept = channel("x:101", XTREAM_TS, Catchup.XC, "", 2, "Europe/London");
        Channel copy = kept.keyed("p2:x:101");
        assertEquals("p2:x:101", copy.key);
        assertEquals(Catchup.XC, copy.catchup);
        assertEquals(2, copy.archiveDays);
        assertEquals("Europe/London", copy.zone);
        assertEquals(XTREAM_TS, copy.url);
    }
}
