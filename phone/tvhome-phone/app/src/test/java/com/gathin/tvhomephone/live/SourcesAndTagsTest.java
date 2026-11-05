package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;

import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Films and series: how their sources are named and picked, and the picture and sound marks on the information bars. */
public class SourcesAndTagsTest {
    @Test public void aSourceIsNamedByItsPlaylistPictureAndFile() {
        assertEquals("Main · 4K · MKV", Vod.label("Main", "EN - The Matrix (1999) 4K", "http://a/movie/u/p/1.mkv"));
        assertEquals("Backup · HD · MP4", Vod.label("Backup", "The Matrix HD", "http://b/1.mp4?token=x"));
        assertEquals("Main", Vod.label("Main", "The Matrix", "x:movie:12"));
        assertEquals("1080p", Vod.quality("The Matrix 1080p"));
        assertEquals("", Vod.quality("Hdtv Nights"));
        assertEquals("", Vod.extension("http://a.example/live/stream"));
    }

    @Test public void theChosenSourcePlaysFirstAndTheRestStayBackups() {
        List<String[]> plays = Arrays.asList(new String[]{"a"}, new String[]{"b"}, new String[]{"c"});
        List<String[]> out = VodPlayerActivity.chosenFirst(plays, 2);
        assertEquals("c", out.get(0)[0]);
        assertEquals("a", out.get(1)[0]);
        assertEquals("b", out.get(2)[0]);
        assertEquals("a", VodPlayerActivity.chosenFirst(plays, 0).get(0)[0]);
    }

    @Test public void thePictureMarks() {
        assertEquals("4K", MediaTags.video(3840, 2160));
        assertEquals("4K", MediaTags.video(3840, 1600));
        assertEquals("1080p", MediaTags.video(1920, 800));
        assertEquals("1080p", MediaTags.video(1080, 1920));
        assertEquals("720p", MediaTags.video(1280, 720));
        assertEquals("SD", MediaTags.video(720, 576));
        assertEquals("", MediaTags.video(0, 0));
        Format hdr10 = new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).setWidth(3840).setHeight(2160)
                .setColorInfo(new ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_ST2084).build()).build();
        assertEquals("HDR10", MediaTags.hdr(hdr10));
        Format hlg = new Format.Builder().setColorInfo(new ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_HLG).build()).build();
        assertEquals("HLG", MediaTags.hdr(hlg));
        assertEquals("Dolby Vision", MediaTags.hdr(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION).build()));
        assertEquals("", MediaTags.hdr(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()));
    }

    @Test public void theSoundMarks() {
        assertEquals("Dolby Digital 5.1", MediaTags.audio(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3).setChannelCount(6).build()));
        assertEquals("Dolby Atmos", MediaTags.audio(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3_JOC).setChannelCount(6).build()));
        assertEquals("AAC Stereo", MediaTags.audio(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setChannelCount(2).build()));
        assertEquals("7.1", MediaTags.audio(new Format.Builder().setSampleMimeType("audio/x-unknown").setChannelCount(8).build()));
        assertEquals("", MediaTags.audio(null));
    }
}
