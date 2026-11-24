package com.gathin.homefieldtv.subs;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Typeface;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * The film player's subtitle files (made-up lines): SubRip and WebVTT read into their lines, sorted, their styles kept as
 * spans and every other tag dropped, the top of the picture where the file says, the lines on at a moment with the timing
 * moved, and old files' writing turned to UTF-8.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SubtitleFileTest {
    private static final String SRT = "1\r\n00:00:01,000 --> 00:00:03,500\r\nThe lighthouse keeper\r\nwaited.\r\n\r\n"
            + "2\r\n00:00:04,000 --> 00:00:06,000\r\n<i>Somewhere, a bell.</i>\r\n\r\n"
            + "3\r\n00:00:05,500 --> 00:00:07,000\r\n{\\an8}<b>[THUNDER]</b>\r\n\r\n";

    @Test public void readsSubRip() {
        SubtitleFile f = SubtitleFile.parse(SRT);
        assertEquals(3, f.size());
        assertEquals(1000, f.lines.get(0).start);
        assertEquals(3500, f.lines.get(0).end);
        assertEquals("The lighthouse keeper\nwaited.", f.lines.get(0).text.toString());
        assertEquals("Somewhere, a bell.", f.lines.get(1).text.toString());
        assertFalse(f.lines.get(1).top);
        // SubStation's {\an8} in a SubRip file: at the top of the picture.
        assertTrue(f.lines.get(2).top);
        assertEquals("[THUNDER]", f.lines.get(2).text.toString());
    }

    @Test public void keepsItalicsBoldUnderliningAndColours() {
        boolean[] top = {false};
        CharSequence t = SubtitleFile.styled("<i>Not</i> <b>now</b>, <u>Morwenna</u> <font color=\"#ffcc00\">please</font>", top);
        assertEquals("Not now, Morwenna please", t.toString());
        Spanned s = (Spanned) t;
        StyleSpan[] styles = s.getSpans(0, s.length(), StyleSpan.class);
        assertEquals(2, styles.length);
        assertEquals(Typeface.ITALIC, styles[0].getStyle());
        assertEquals(0, s.getSpanStart(styles[0]));
        assertEquals(3, s.getSpanEnd(styles[0]));
        assertEquals(Typeface.BOLD, styles[1].getStyle());
        UnderlineSpan[] under = s.getSpans(0, s.length(), UnderlineSpan.class);
        assertEquals(1, under.length);
        assertEquals("Morwenna", t.subSequence(s.getSpanStart(under[0]), s.getSpanEnd(under[0])).toString());
        ForegroundColorSpan[] colours = s.getSpans(0, s.length(), ForegroundColorSpan.class);
        assertEquals(1, colours.length);
        assertEquals(0xFFFFCC00, colours[0].getForegroundColor());
        assertEquals("please", t.subSequence(s.getSpanStart(colours[0]), s.getSpanEnd(colours[0])).toString());
    }

    @Test public void dropsOtherTagsAndWritesEntitiesOut() {
        boolean[] top = {false};
        assertEquals("Fish & chips <3", SubtitleFile.styled("<v Ada>Fish &amp; chips &lt;3", top).toString());
        assertEquals("Run!", SubtitleFile.styled("<00:00:01.200><c.yellow>Run!</c>", top).toString());
        assertEquals("one two", SubtitleFile.styled("{\\i1}one{\\i0} {\\pos(10,20)}two", top).toString());
        // A lone "<" that opens no tag stays.
        assertEquals("a < b", SubtitleFile.styled("a < b", top).toString());
        assertFalse(top[0]);
        Spanned yellow = (Spanned) SubtitleFile.styled("<c.yellow>Run!</c>", top);
        assertEquals(0xFFFFFF00, yellow.getSpans(0, yellow.length(), ForegroundColorSpan.class)[0].getForegroundColor());
        Spanned ass = (Spanned) SubtitleFile.styled("{\\i1}one{\\i0} two", top);
        StyleSpan[] it = ass.getSpans(0, ass.length(), StyleSpan.class);
        assertEquals(1, it.length);
        assertEquals(3, ass.getSpanEnd(it[0]));
    }

    @Test public void readsWebVtt() {
        String vtt = "WEBVTT - made up\nKind: captions\n\nNOTE a note\nthat goes on\n\nSTYLE\n::cue { color: red }\n\n"
                + "intro\n00:01.000 --> 00:02.500 line:0 align:start\nAt the top\n\n"
                + "00:00:03.000 --> 00:00:04.250 position:50%\nAt the foot\n\n"
                + "1:00:00.5 --> 1:00:02\nAn hour in\n";
        SubtitleFile f = SubtitleFile.parse(vtt);
        assertEquals(3, f.size());
        assertEquals(1000, f.lines.get(0).start);
        assertEquals(2500, f.lines.get(0).end);
        assertTrue(f.lines.get(0).top);
        assertEquals("At the top", f.lines.get(0).text.toString());
        assertEquals(3000, f.lines.get(1).start);
        assertFalse(f.lines.get(1).top);
        assertEquals(3_600_500, f.lines.get(2).start);
        assertEquals(3_602_000, f.lines.get(2).end);
    }

    @Test public void timesAsTheFilesWriteThem() {
        assertEquals(62_345, SubtitleFile.time("00:01:02,345"));
        assertEquals(62_345, SubtitleFile.time("00:01:02.345"));
        assertEquals(62_345, SubtitleFile.time("01:02.345"));
        assertEquals(3_723_500, SubtitleFile.time("1:02:03.5"));
        assertEquals(-1, SubtitleFile.time("soon"));
        assertEquals(-1, SubtitleFile.time("12"));
        assertTrue(SubtitleFile.vttTop("line:10%"));
        assertFalse(SubtitleFile.vttTop("line:-1"));
        assertFalse(SubtitleFile.vttTop("line:90%,end"));
    }

    @Test public void theLinesOnAtAMomentWithTheTimingMoved() {
        SubtitleFile f = SubtitleFile.parse(SRT);
        int[] on = new int[SubtitleFile.MAX_ON];
        assertEquals(0, f.at(500, 0, on));
        assertEquals(1, f.at(2000, 0, on));
        assertEquals(0, on[0]);
        // Two at once, in the order they start.
        assertEquals(2, f.at(5_700, 0, on));
        assertArrayEquals(new int[]{1, 2}, new int[]{on[0], on[1]});
        assertEquals(0, f.at(3_700, 0, on));
        // Half a second later (+500): what was on at 3.2 s is on at 3.7 s.
        assertEquals(1, f.at(3_700, 500, on));
        assertEquals(0, on[0]);
        // Half a second earlier (−500): the first line already starts at 0.6 s.
        assertEquals(1, f.at(600, -500, on));
        assertEquals(0, f.at(600, 0, on));
    }

    @Test public void outOfOrderBrokenAndEmptyBlocks() {
        String s = "2\n00:00:05,000 --> 00:00:06,000\nSecond\n\n1\n00:00:01,000 --> 00:00:02,000\nFirst\n\n"
                + "x\nnot a time --> nor this\nlost\n\n3\n00:00:07,000 --> 00:00:07,000\nNo length of its own\n\n"
                + "4\n00:00:09,000 --> 00:00:10,000\n<i></i>\n\n";
        SubtitleFile f = SubtitleFile.parse(s);
        assertEquals(3, f.size());
        assertEquals("First", f.lines.get(0).text.toString());
        assertEquals("Second", f.lines.get(1).text.toString());
        // A line that ends as it starts lasts two seconds.
        assertEquals(9_000, f.lines.get(2).end);
        assertTrue(SubtitleFile.parse("").isEmpty());
    }

    @Test public void oldWritingTurnedToUtf8() {
        String words = "1\n00:00:01,000 --> 00:00:02,000\nCafé crème\n";
        byte[] latin = words.getBytes(Charset.forName("windows-1252"));
        assertEquals(words, new String(SubtitleFile.utf8(latin, "fr"), StandardCharsets.UTF_8));
        String greek = "Καλημέρα";
        assertEquals(greek, new String(SubtitleFile.utf8(greek.getBytes(Charset.forName("windows-1253")), "el"), StandardCharsets.UTF_8));
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'h', 'i'};
        assertEquals("hi", new String(SubtitleFile.utf8(bom, "en"), StandardCharsets.UTF_8));
        assertEquals("Café", SubtitleFile.parse("1\n00:00:01,000 --> 00:00:02,000\nCafé\n".getBytes(StandardCharsets.UTF_16), "fr")
                .lines.get(0).text.toString());
    }

    @Test public void languagesByTheirCodes() {
        assertTrue(Langs.same("en", "eng"));
        assertTrue(Langs.same("pt-BR", "por"));
        assertTrue(Langs.same("de", "ger"));
        assertFalse(Langs.same("en", ""));
        assertEquals("English", Langs.name("eng"));
        assertEquals("Portuguese (Brazil)", Langs.name("pt-BR"));
        assertEquals("", Langs.name("und"));
    }
}
