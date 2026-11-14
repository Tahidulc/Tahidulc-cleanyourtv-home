package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;

import com.gathin.tvhomephone.Prayer;
import com.gathin.tvhomephone.R;

import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The logo's extras: the stream's marks from the player's tracks, Up next, prayer time, which comes first. */
public class LogoExtrasTest {

    @Test
    public void theSoundIsReadFromItsTrack() {
        assertEquals("Dolby Atmos", MediaTags.audio(MimeTypes.AUDIO_E_AC3_JOC, null, 6));
        // A playlist naming E-AC-3's Atmos by its codec, the player's type plain E-AC-3.
        assertEquals("Dolby Atmos", MediaTags.audio(MimeTypes.AUDIO_E_AC3, "ec+3", 6));
        assertEquals("Dolby Digital+ 5.1", MediaTags.audio(MimeTypes.AUDIO_E_AC3, "ec-3", 6));
        assertEquals("Dolby Digital 5.1", MediaTags.audio(MimeTypes.AUDIO_AC3, null, 6));
        assertEquals("DTS 5.1", MediaTags.audio(MimeTypes.AUDIO_DTS, null, 6));
        assertEquals("AAC Stereo", MediaTags.audio(MimeTypes.AUDIO_AAC, "mp4a.40.2", 2));
        assertEquals("Stereo", MediaTags.audio("audio/unknown", null, 2));
        assertEquals("", MediaTags.audio(null, null, 0));
    }

    @Test
    public void thePicturesHdrIsReadFromItsTrack() {
        assertEquals("Dolby Vision", MediaTags.hdr(MimeTypes.VIDEO_DOLBY_VISION, null, C.INDEX_UNSET));
        assertEquals("Dolby Vision", MediaTags.hdr(MimeTypes.VIDEO_H265, "dvh1.05.06", C.COLOR_TRANSFER_ST2084));
        assertEquals("Dolby Vision", MediaTags.hdr(MimeTypes.VIDEO_H265, "hvc1.2.4.L150,dvh1.08.06", C.COLOR_TRANSFER_ST2084));
        assertEquals("HDR10+", MediaTags.hdr(MimeTypes.VIDEO_H265, "hev1.2.4.L153.B0,hdr10+", C.COLOR_TRANSFER_ST2084));
        assertEquals("HDR10", MediaTags.hdr(MimeTypes.VIDEO_H265, "hvc1.2.4.L153.B0", C.COLOR_TRANSFER_ST2084));
        assertEquals("HLG", MediaTags.hdr(MimeTypes.VIDEO_H265, null, C.COLOR_TRANSFER_HLG));
        assertEquals("", MediaTags.hdr(MimeTypes.VIDEO_H264, "avc1.640028", C.COLOR_TRANSFER_SDR));
        assertEquals("4K", MediaTags.video(3840, 2160));
        assertEquals("1080p", MediaTags.video(1920, 1080));
    }

    private static List<Integer> logos(List<Marks.Mark> marks) {
        List<Integer> out = new ArrayList<>();
        for (Marks.Mark m : marks) out.add(m.logo);
        return out;
    }

    private static List<String> words(List<Marks.Mark> marks) {
        List<String> out = new ArrayList<>();
        for (Marks.Mark m : marks) out.add(m.words);
        return out;
    }

    @Test
    public void theLogoCarriesOnlyWhatTheStreamHas() {
        List<Marks.Mark> top = Marks.logo("4K", "HDR10+", "Dolby Atmos");
        assertEquals(Arrays.asList(R.drawable.fmt_4k, R.drawable.fmt_hdr10plus, R.drawable.fmt_dolby_atmos), logos(top));
        // HD pictures carry no picture mark; Dolby's other sound its logo and its surround channels.
        List<Marks.Mark> hd = Marks.logo("1080p", "", "Dolby Digital+ 5.1");
        assertEquals(Arrays.asList(R.drawable.fmt_dolby, 0), logos(hd));
        assertEquals("5.1", hd.get(1).words);
        assertEquals(Arrays.asList("4K", "Dolby Vision", "DTS", "5.1"), words(Marks.logo("4K", "Dolby Vision", "DTS 5.1")));
        assertEquals(Arrays.asList("HLG", "5.1"), words(Marks.logo("1080p", "HLG", "AAC 5.1")));
        assertEquals(Arrays.asList("Stereo"), words(Marks.logo("720p", "", "AAC Stereo")));
        assertTrue(Marks.logo("SD", "", "Mono").isEmpty());
        assertTrue(Marks.logo("", "", "").isEmpty());
        assertTrue(Marks.logo(null, null, null).isEmpty());
    }

    @Test
    public void upNextIsTheNextProgrammeFiveMinutesBefore() {
        long now = 1_000_000_000L;
        OverMatches.Programme on = new OverMatches.Programme("Live Boxing: Undercard", "", now - 3_600_000L, now + 240_000L);
        OverMatches.Programme next = new OverMatches.Programme("Live Boxing: Archer v Mendes", "", now + 240_000L, now + 7_200_000L);
        assertSame(next, LogoExtras.upNext(Arrays.asList(on, next), now, LogoExtras.AHEAD_MS));
        // Further off than five minutes, begun already, or untitled: none.
        assertNull(LogoExtras.upNext(Arrays.asList(on, next), now - 120_000L, LogoExtras.AHEAD_MS));
        assertNull(LogoExtras.upNext(Arrays.asList(on, next), now + 300_000L, LogoExtras.AHEAD_MS));
        assertNull(LogoExtras.upNext(Arrays.asList(new OverMatches.Programme(" ", "", now + 60_000L, now + 90_000L)), now, LogoExtras.AHEAD_MS));
        assertNull(LogoExtras.upNext(null, now, LogoExtras.AHEAD_MS));
        // "IN 5 MIN" from four and a half minutes; at least one.
        assertEquals(5, LogoExtras.minutesTo(now, now + 270_000L));
        assertEquals(5, LogoExtras.minutesTo(now, now + 300_000L));
        assertEquals(1, LogoExtras.minutesTo(now, now + 10_000L));
    }

    private static Prayer.Slot slot(String name, LocalDateTime begins) {
        Prayer.Slot s = new Prayer.Slot();
        s.name = name;
        s.begins = begins;
        return s;
    }

    @Test
    public void onePrayerReminderFiveMinutesBefore() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 14, 16, 7, 1);
        List<Prayer.Slot> day = Arrays.asList(slot("Asr", now.minusHours(2)), slot("Maghrib", now.plusMinutes(4).plusSeconds(59)),
                slot("Isha", now.plusHours(2)));
        assertEquals("Maghrib", LogoExtras.prayerDue(day, now, LogoExtras.AHEAD_MS).name);
        assertNull(LogoExtras.prayerDue(day, now.minusSeconds(3), LogoExtras.AHEAD_MS));
        assertNull(LogoExtras.prayerDue(day, now.plusMinutes(5), LogoExtras.AHEAD_MS));
        assertNull(LogoExtras.prayerDue(null, now, LogoExtras.AHEAD_MS));
    }

    @Test
    public void oneAtATimeUpNextFirst() {
        assertEquals(LogoExtras.NEXT, LogoExtras.pick(true, true, true));
        assertEquals(LogoExtras.PRAYER, LogoExtras.pick(false, true, true));
        assertEquals(LogoExtras.CLOCK, LogoExtras.pick(false, false, true));
        assertEquals(LogoExtras.NONE, LogoExtras.pick(false, false, false));
        // The clock every five minutes, its switch on.
        assertTrue(LogoExtras.clockDue(true, 300_000L, 0L));
        assertEquals(false, LogoExtras.clockDue(true, 299_999L, 0L));
        assertEquals(false, LogoExtras.clockDue(false, 900_000L, 0L));
        // The weather's picture by the app's weather words.
        assertEquals(LogoExtras.SKY_CLEAR, LogoExtras.skyOf("Clear"));
        assertEquals(LogoExtras.SKY_PARTLY, LogoExtras.skyOf("Partly cloudy"));
        assertEquals(LogoExtras.SKY_CLOUD, LogoExtras.skyOf("Cloudy"));
        assertEquals(LogoExtras.SKY_RAIN, LogoExtras.skyOf("Showers"));
        assertEquals(LogoExtras.SKY_RAIN, LogoExtras.skyOf("Drizzle"));
        assertEquals(LogoExtras.SKY_SNOW, LogoExtras.skyOf("Snow showers"));
        assertEquals(LogoExtras.SKY_THUNDER, LogoExtras.skyOf("Thunder"));
        assertEquals(LogoExtras.SKY_FOG, LogoExtras.skyOf("Fog"));
        assertEquals(LogoExtras.SKY_PARTLY, LogoExtras.skyOf(null));
    }
}
