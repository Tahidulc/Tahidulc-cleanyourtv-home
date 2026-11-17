package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.MimeTypes;
import androidx.media3.common.text.Cue;

import org.junit.Test;

public class LiveSubsTest {
    @Test public void aChannelsOwnSubtitlesArePlainlyItsOwn() {
        // DVB subtitles and subtitle playlists (read as they come in, their kind kept in codecs) count.
        assertTrue(LiveSubs.plainlyOwn(MimeTypes.APPLICATION_MEDIA3_CUES, MimeTypes.APPLICATION_DVBSUBS, null, null));
        assertTrue(LiveSubs.plainlyOwn(MimeTypes.APPLICATION_DVBSUBS, null, "eng", null));
        assertTrue(LiveSubs.plainlyOwn(MimeTypes.TEXT_VTT, null, null, null));
        // Captions count only when the stream names them.
        assertTrue(LiveSubs.plainlyOwn(MimeTypes.APPLICATION_CEA608, null, "en", null));
        assertTrue(LiveSubs.plainlyOwn(MimeTypes.APPLICATION_CEA708, null, null, "English CC"));
    }

    @Test public void aBareCaptionsTrackDoesNot() {
        // HLS and MPEG-TS streams are given one whether they carry captions or not.
        assertFalse(LiveSubs.plainlyOwn(MimeTypes.APPLICATION_CEA608, null, null, null));
        assertFalse(LiveSubs.plainlyOwn(MimeTypes.APPLICATION_CEA608, null, "und", "  "));
        assertFalse(LiveSubs.plainlyOwn(MimeTypes.APPLICATION_CEA708, null, "", ""));
        // The same, read as it comes in.
        assertFalse(LiveSubs.plainlyOwn(MimeTypes.APPLICATION_MEDIA3_CUES, MimeTypes.APPLICATION_CEA608, null, null));
    }

    @Test public void theQuestionNamesBothSizes() {
        String ask = LiveSubs.askLine().replace(' ', ' ');
        assertTrue(ask, ask.contains("about 40 MB"));
        assertTrue(ask, ask.contains("about 70 MB on the TV"));
        assertTrue(ask, ask.contains("never leaves it"));
    }

    @Test public void theChoicesAreOffFromTheChannelAndAutomatic() {
        assertEquals(3, LiveSubs.MODES.length);
        assertEquals("Off", LiveSubs.MODES[LiveSubs.OFF]);
        assertEquals("From the channel", LiveSubs.MODES[LiveSubs.CHANNEL]);
        assertEquals("Automatic", LiveSubs.MODES[LiveSubs.AUTO]);
    }

    @Test public void wordsGoAtTheTopOnlyWhenTheChannelPutsThemThere() {
        assertFalse(ChannelSubs.atTop(Cue.TYPE_UNSET, Cue.DIMEN_UNSET));
        assertTrue(ChannelSubs.atTop(Cue.LINE_TYPE_FRACTION, 0.1f));
        assertFalse(ChannelSubs.atTop(Cue.LINE_TYPE_FRACTION, 0.85f));
        // Line numbers: from the top when 0 or more, from the foot when less.
        assertTrue(ChannelSubs.atTop(Cue.LINE_TYPE_NUMBER, 1f));
        assertFalse(ChannelSubs.atTop(Cue.LINE_TYPE_NUMBER, 12f));
        assertFalse(ChannelSubs.atTop(Cue.LINE_TYPE_NUMBER, -2f));
    }
}
