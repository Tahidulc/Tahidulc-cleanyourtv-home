package com.gathin.tvhomephone.captions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** The automatic subtitles' pure parts: the sound made 16 kHz mono, the words kept readable, the model unpacked safely. */
public class CaptionsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void stereoAt48kBecomesMonoAt16kInTenthsOfASecond() {
        Pcm.Resampler r = new Pcm.Resampler();
        r.configure(48_000, 2, false);
        List<short[]> out = new ArrayList<>();
        ByteBuffer b = ByteBuffer.allocate(48_000 * 2 * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 48_000; i++) {
            short v = (short) Math.round(10_000 * Math.sin(2 * Math.PI * 440 * i / 48_000.0));
            b.putShort(v).putShort(v);
        }
        b.flip();
        r.feed(b, (pcm, n) -> out.add(pcm));
        assertEquals(10, out.size());
        int peak = 0;
        for (short[] chunk : out) {
            assertEquals(Pcm.CHUNK, chunk.length);
            for (short s : chunk) peak = Math.max(peak, Math.abs(s));
        }
        // A 440 Hz tone comes through at nearly its loudness (the averaging barely touches it).
        assertTrue("peak " + peak, peak > 9_000 && peak <= 10_000);
    }

    @Test public void surroundSoundIsHeardMostlyFromTheCentre() {
        Pcm.Resampler r = new Pcm.Resampler();
        r.configure(16_000, 6, true);
        short[][] got = new short[1][];
        ByteBuffer b = ByteBuffer.allocate(Pcm.CHUNK * 6 * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < Pcm.CHUNK; i++) {
            // left, right, centre (the voices), LFE, back left, back right
            b.putFloat(0f).putFloat(0f).putFloat(0.5f).putFloat(1f).putFloat(1f).putFloat(1f);
        }
        b.flip();
        r.feed(b, (pcm, n) -> got[0] = pcm);
        assertEquals(Math.round(0.25f * 32767), got[0][10]);
        assertEquals(0, b.remaining());
        byte[] le = Pcm.bytes(new short[]{0x1234, -2}, 2);
        assertEquals(0x34, le[0] & 0xFF);
        assertEquals(0x12, le[1] & 0xFF);
        assertEquals(0xFE, le[2] & 0xFF);
        assertEquals(0xFF, le[3] & 0xFF);
    }

    @Test public void theWordsStayReadableAndRollOn() {
        CaptionText t = new CaptionText();
        t.partial("and i think   he's");
        assertEquals("And I think he's", t.shown());
        t.line("and i think he's through on goal");
        t.partial("i'm sure");
        assertEquals("And I think he's through on goal I'm sure", t.shown());
        t.line("");
        assertEquals("And I think he's through on goal", t.shown());
        for (int i = 0; i < 40; i++) t.line("the ball goes out for a throw in number " + i);
        String s = t.shown();
        assertTrue(s.length() <= CaptionText.KEEP);
        assertTrue(s, s.endsWith("number 39"));
        assertFalse(s, s.startsWith(" "));
        t.clear();
        assertEquals("", t.shown());
        assertEquals("hello there", VoskEngine.field("{\"text\" : \"hello there\"}", "text"));
        assertEquals("", VoskEngine.field("{\"partial\" : \"\"}", "partial"));
        assertEquals("", VoskEngine.field("nonsense", "text"));
    }

    @Test public void theModelUnpacksIntoItsFolderOnly() throws IOException {
        File into = tmp.newFolder("model");
        byte[] ok = zip("m/am/final.mdl", "m/conf/mfcc.conf");
        VoskModel.unzip(new ByteArrayInputStream(ok), into);
        assertTrue(VoskModel.valid(new File(into, "m")));
        assertFalse(VoskModel.valid(into));
        File other = tmp.newFolder("other");
        try {
            VoskModel.unzip(new ByteArrayInputStream(zip("../escape.txt")), other);
            fail("an entry outside the folder was written");
        } catch (IOException expected) {
            assertFalse(new File(other.getParentFile(), "escape.txt").exists());
        }
    }

    private static byte[] zip(String... names) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            for (String n : names) {
                z.putNextEntry(new ZipEntry(n));
                z.write(("x " + n).getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return b.toByteArray();
    }
}
