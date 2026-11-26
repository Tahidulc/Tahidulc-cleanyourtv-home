package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.PicSources;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * Which of TMDB's logos a card's mark is made from: the family's language first, then English, then one with no words,
 * then any other; PNGs only; the first three of that language in TMDB's order (the card takes the first light one);
 * kept on the TV with the extras.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LogoChoiceTest {
    private static JSONObject logo(String lang, String path) throws Exception {
        JSONObject o = new JSONObject().put("file_path", path);
        if (lang == null) o.put("iso_639_1", JSONObject.NULL);
        else o.put("iso_639_1", lang);
        return o;
    }

    private static JSONObject images(JSONObject... logos) throws Exception {
        JSONArray a = new JSONArray();
        for (JSONObject o : logos) a.put(o);
        return new JSONObject().put("logos", a);
    }

    @Test public void theFamilysLanguageThenEnglishThenNoWordsThenAnyPngsOnly() throws Exception {
        JSONObject im = images(logo("de", "/de1.png"), logo("en", "/en1.svg"), logo("en", "/en1.png"), logo(null, "/none.png"),
                logo("fr", "/fr1.png"), logo("fr", "/fr2.png"), logo("en", "/en2.png"));
        assertEquals(List.of(Tmdb.image("/fr1.png", "w500"), Tmdb.image("/fr2.png", "w500")), Tmdb.logosOf(im, "fr"));
        assertEquals(List.of(Tmdb.image("/en1.png", "w500"), Tmdb.image("/en2.png", "w500")), Tmdb.logosOf(im, "en"));
        assertEquals("no Spanish one: English", List.of(Tmdb.image("/en1.png", "w500"), Tmdb.image("/en2.png", "w500")),
                Tmdb.logosOf(im, "es"));
        JSONObject wordless = images(logo("de", "/de1.png"), logo(null, "/none.png"));
        assertEquals(List.of(Tmdb.image("/none.png", "w500")), Tmdb.logosOf(wordless, "en"));
        assertEquals("then any", List.of(Tmdb.image("/de1.png", "w500")), Tmdb.logosOf(images(logo("de", "/de1.png")), "en"));
        assertTrue(Tmdb.logosOf(images(logo("en", "/en.svg")), "en").isEmpty());
        // At most three, TMDB's order kept.
        JSONObject many = images(logo("en", "/a.png"), logo("en", "/b.png"), logo("en", "/c.png"), logo("en", "/d.png"));
        assertEquals(Tmdb.MARKS, Tmdb.logosOf(many, "en").size());
        assertEquals(Tmdb.image("/a.png", "w500"), Tmdb.logoOf(many, "en"));
    }

    @Test public void theMarkIsAChainOfThemKeptWithTheExtras() throws Exception {
        JSONObject j = new JSONObject().put("images", images(logo("en", "/dark.png"), logo("en", "/light.png")));
        Tmdb.Extras x = Tmdb.extrasOf(j, false, "en");
        assertEquals(Tmdb.image("/dark.png", "w500"), x.logo);
        assertTrue(PicSources.isChain(x.mark));
        assertEquals(List.of(Tmdb.image("/dark.png", "w500"), Tmdb.image("/light.png", "w500")), PicSources.parts(x.mark));
        Tmdb.Extras kept = Tmdb.fromKept(Tmdb.kept(x));
        assertEquals(x.mark, kept.mark);
        assertEquals(x.logo, kept.logo);
        // One logo: the mark is it; none: nothing.
        assertEquals(Tmdb.image("/one.png", "w500"), Tmdb.extrasOf(new JSONObject().put("images", images(logo("en", "/one.png"))), false, "en").mark);
        assertEquals("", Tmdb.extrasOf(new JSONObject(), false, "en").mark);
    }
}
