package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProgrammeSearchTest {
    private static final long NOW = 100_000;

    private static Channel ch(String name) {
        return new Channel(name, name, "", "", "http://x", "", "", "", 0);
    }

    private static List<String> titles(List<ProgrammeSearch.Hit> hits) {
        List<String> out = new ArrayList<>();
        for (ProgrammeSearch.Hit h : hits) out.add(h.title() + "@" + h.channel.name);
        return out;
    }

    private static ProgrammeSearch.Index index() {
        Guide.Builder one = new Guide.Builder(), two = new Guide.Builder(), three = new Guide.Builder();
        one.add(90_000, 110_000, "The News at Six", "The day's headlines.");     // on now, a word starts with it
        one.add(110_000, 130_000, "News", "");                                    // later, exactly
        one.add(50_000, 60_000, "News", "");                                      // over already
        two.add(95_000, 105_000, "Newsround", "");                                // on now, starts with it
        two.add(150_000, 160_000, "Cooking", "With the latest news from the kitchen."); // in the description only
        two.add(105_000, 115_000, "Renewsal", "");                                // inside a word
        three.add(120_000, 130_000, "Café Société", "");                          // accents
        three.add(80_000, 101_000, "NEWS", "");                                   // on now, exactly (any case)
        List<Channel> chans = Arrays.asList(ch("One"), ch("Two"), ch("Three"));
        return new ProgrammeSearch.Index(chans, new Guide.Schedule[]{one.build(), two.build(), three.build()});
    }

    @Test public void rankedExactThenWordThenInsideThenDescription() {
        List<String> got = titles(ProgrammeSearch.search(index(), "news", NOW, 20));
        assertEquals(Arrays.asList(
                "NEWS@Three",              // exact, on now
                "News@One",                // exact, later
                "The News at Six@One",     // a word starts with it, on now (starts sooner)
                "Newsround@Two",           // starts with it, on now
                "Renewsal@Two",            // inside a word
                "Cooking@Two"), got);      // the description
    }

    @Test public void endedProgrammesAndShortSearchesAreLeftOut() {
        assertEquals(5, ProgrammeSearch.search(index(), "news", NOW, 5).size());
        assertTrue(ProgrammeSearch.search(index(), "n", NOW, 20).isEmpty());
        assertTrue(ProgrammeSearch.search(index(), "  ", NOW, 20).isEmpty());
        // Later on, what has finished by then has gone from the results too.
        List<String> later = titles(ProgrammeSearch.search(index(), "news", 125_000, 20));
        assertEquals(Arrays.asList("News@One", "Cooking@Two"), later);
    }

    @Test public void limitKeepsTheBest() {
        List<String> got = titles(ProgrammeSearch.search(index(), "news", NOW, 2));
        assertEquals(Arrays.asList("NEWS@Three", "News@One"), got);
    }

    @Test public void accentsAndCaseDoNotMatter() {
        assertEquals(Arrays.asList("Café Société@Three"), titles(ProgrammeSearch.search(index(), "cafe soc", NOW, 20)));
        assertEquals(ProgrammeSearch.EXACT, ProgrammeSearch.titleRank(ProgrammeSearch.fold("  The  Chase "), "the chase"));
        assertEquals(ProgrammeSearch.WORD, ProgrammeSearch.titleRank("the chase", "chase"));
        assertEquals(ProgrammeSearch.INSIDE, ProgrammeSearch.titleRank("purchase", "chase"));
        assertEquals(-1, ProgrammeSearch.titleRank("the chase", "news"));
        assertTrue(ProgrammeSearch.inDescription("Breaking NEWS tonight", "news"));
        assertFalse(ProgrammeSearch.inDescription("Breaking NEWS tonight", "ne"));
    }

    @Test public void indexSpan() {
        ProgrammeSearch.Index ix = index();
        assertEquals(50_000, ix.from);
        assertEquals(160_000, ix.to);
        assertEquals(8, ix.size());
        assertEquals(0, ProgrammeSearch.Index.EMPTY.size());
    }
}
