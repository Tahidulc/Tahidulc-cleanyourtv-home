package com.gathin.homefieldtv.engine;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The previews' made-up playlist: a dozen and a half channels with a guide around now, and films and series from the
 * mock-ups' week (nfx/data.json and its pictures under the folder given). Previews only; none of it ships.
 */
public final class PreviewData {
    private PreviewData() {}

    private static final String[][] CHANNELS = {
            {"2076", "BBC One HD", "UK | Entertainment", "101"},
            {"2006", "BBC Two HD", "UK | Entertainment", "102"},
            {"6000", "ITV1 HD", "UK | Entertainment", "103"},
            {"1621", "Channel 4 HD", "UK | Entertainment", "104"},
            {"1800", "Channel 5 HD", "UK | Entertainment", "105"},
            {"1412", "Sky Atlantic HD", "UK | Entertainment", "108"},
            {"", "E4", "UK | Entertainment", "137"},
            {"", "Film4", "UK | Movies", "315"},
            {"4002", "Sky Sports Main Event UHD", "UK | Sports", "401"},
            {"4003", "Sky Sports Premier League", "UK | Sports", "402"},
            {"4004", "Sky Sports F1 UHD", "UK | Sports", "406"},
            {"4005", "TNT Sports 1", "UK | Sports", "411"},
            {"1404", "Sky News HD", "UK | News", "501"},
            {"2011", "BBC News HD", "UK | News", "503"},
            {"", "Discovery", "UK | Documentaries", "521"},
            {"", "CBeebies", "UK | Kids", "601"},
    };

    private static final Map<String, String[][]> SHOWS = new HashMap<>();

    static {
        SHOWS.put("BBC One HD", new String[][]{
                {"The One Show", "Live magazine programme with the stories that matter, presented from London."},
                {"EastEnders", "Linda tries to keep the peace at the Vic, but an old face has other ideas."},
                {"Strictly: It Takes Two", "Janette Manrara looks back at the weekend's routines with the couples."},
                {"Death in Paradise", "A guest is found dead at a beach wedding, and every guest has a reason to lie."},
                {"BBC News at Ten", "The latest national and international news, with reports from BBC correspondents."}});
        SHOWS.put("BBC Two HD", new String[][]{
                {"University Challenge", "The quiz for students returns with two new teams."},
                {"Only Connect", "Victoria Coren Mitchell hosts the quiz where knowledge will only take you so far."},
                {"Gardeners' World", "Monty Don plants spring bulbs and keeps the borders going into the autumn."},
                {"Newsnight", "The day's news, discussed and debated."}});
        SHOWS.put("ITV1 HD", new String[][]{
                {"Emmerdale", "Cain makes a decision that could change everything for the Dingles."},
                {"Coronation Street", "Roy is worried when the café's takings go missing."},
                {"I'm a Celebrity", "The campmates face the first trial of the series."},
                {"ITV News at Ten", "The latest news from around the country and the world."}});
        SHOWS.put("Channel 4 HD", new String[][]{
                {"Gogglebox", "Britain's favourite armchair critics share their views on the week's TV."},
                {"The Great British Bake Off", "Biscuit week: the bakers make a showstopper that has to stand up."},
                {"Hollyoaks", "Tony makes a confession, and the village takes sides."},
                {"Grand Designs", "Kevin McCloud follows a couple building a house into a hillside."}});
        SHOWS.put("Channel 5 HD", new String[][]{
                {"The Yorkshire Vet", "Peter Wright treats a donkey with a mystery limp."},
                {"Milkshake!", "Fun and games for young viewers."},
                {"Bargain Loving Brits in the Sun", "Families on the Costa del Sol make the most of the sunshine."}});
        SHOWS.put("Sky Atlantic HD", new String[][]{
                {"Game of Thrones", "The houses of Westeros fight for the Iron Throne."},
                {"The Sopranos", "Tony struggles to balance his family and his other family."},
                {"Succession", "The Roy siblings jostle for their father's approval."},
                {"The Last of Us", "Joel and Ellie cross a ruined America."}});
        SHOWS.put("E4", new String[][]{
                {"The Big Bang Theory", "Sheldon refuses to let Leonard move out."},
                {"Young Sheldon", "Sheldon starts a new term at college."},
                {"Married at First Sight UK", "The couples move in together."}});
        SHOWS.put("Film4", new String[][]{
                {"Paddington 2", "Paddington takes on odd jobs to buy the perfect present for his aunt."},
                {"The Martian", "An astronaut stranded on Mars has to survive until a rescue."},
                {"Heat", "A detective hunts a crew of master thieves across Los Angeles."}});
        SHOWS.put("Sky Sports Main Event UHD", new String[][]{
                {"Live: Everton v Coventry City", "Live coverage from the Hill Dickinson Stadium."},
                {"Friday Night Football", "The best of the build-up and the reaction."},
                {"The Debate", "The pundits pick apart the weekend's big talking points."}});
        SHOWS.put("Sky Sports Premier League", new String[][]{
                {"Premier League Today", "The latest news, views and team news from around the league."},
                {"Goals of the Season", "The finest strikes of the campaign so far."},
                {"The Football Show", "News and reaction, live."}});
        SHOWS.put("Sky Sports F1 UHD", new String[][]{
                {"Live: Brazilian GP Practice 2", "Second practice from Interlagos."},
                {"The F1 Show", "The week's news from the paddock."},
                {"Ted's Notebook", "Ted Kravitz looks back over the day in the pit lane."}});
        SHOWS.put("TNT Sports 1", new String[][]{
                {"Live: UEFA Europa League", "Live coverage of tonight's Europa League action."},
                {"Champions League Goals", "Every goal from the week's games."},
                {"WWE Smackdown", "The superstars of Smackdown in action."}});
        SHOWS.put("Sky News HD", new String[][]{
                {"The Politics Hub", "The day's political news, with interviews and analysis."},
                {"Sky News Tonight", "The day's top stories, and what they mean."},
                {"The Take", "Analysis of the day's news."}});
        SHOWS.put("BBC News HD", new String[][]{
                {"BBC News", "The latest national and international news."},
                {"The Context", "Christian Fraser looks at the stories behind the headlines."},
                {"Newsday", "News from around the world."}});
        SHOWS.put("Discovery", new String[][]{
                {"Gold Rush", "The miners race to beat the Klondike winter."},
                {"Wheeler Dealers", "Mike and Marc rescue a classic sports car."}});
        SHOWS.put("CBeebies", new String[][]{
                {"Hey Duggee", "The Squirrels earn their storytelling badge."},
                {"Bluey", "Bluey and Bingo turn the living room into a market."},
                {"Bedtime Story", "A story to settle down with before bed."}});
    }

    /** The made-up playlist loaded into the app, as if just read from a provider. */
    public static void load(Context c, String root) throws Exception {
        Sources.clear(c);
        Sources.put(c, "p0", "Home", new Source(Source.XTREAM, null, null, "http://provider.example:8080", "family", "preview"));
        long now = System.currentTimeMillis();
        List<Channel> chans = new ArrayList<>();
        Map<String, Guide.Schedule> guide = new HashMap<>();
        long half = 30 * 60_000L;
        int[][] lens = {{2, 1, 1, 2}, {1, 1, 2, 1}, {1, 2, 1, 2}, {2, 2, 1, 1}, {1, 1, 1, 2}};
        for (int i = 0; i < CHANNELS.length; i++) {
            String[] ch = CHANNELS[i];
            String logo = ch[0].isEmpty() ? "" : "file:" + new File(root, "skin-preview/logos/" + ch[0] + ".png").getPath();
            Channel channel = new Channel("x:" + (1000 + i), ch[1], ch[2], logo, "http://provider.example/live/" + i + ".ts", "", null, null,
                    Integer.parseInt(ch[3]));
            chans.add(channel);
            String[][] shows = SHOWS.get(ch[1]);
            Guide.Builder b = new Guide.Builder();
            int[] len = lens[i % lens.length];
            long at = now - now % half - half - (i % 3) * 15 * 60_000L;
            for (int k = 0; at < now + 12 * 3600_000L; k++) {
                long stop = at + len[k % len.length] * half;
                String[] s = shows[k % shows.length];
                b.add(at, stop, s[0], s[1]);
                at = stop;
            }
            guide.put(channel.key, b.build());
        }
        Library lib = Library.get(c);
        lib.useForPreview(chans, guide);
        if (!lib.isFavourite(chans.get(0))) lib.toggleFavourite(chans.get(0));
        if (!lib.isFavourite(chans.get(11))) lib.toggleFavourite(chans.get(11));
        lib.watched(chans.get(0));

        JSONObject d = new JSONObject(new String(Files.readAllBytes(new File(root, "nfx/data.json").toPath()), StandardCharsets.UTF_8));
        List<String> rows = new ArrayList<>();
        Map<String, Catalog.Details> pages = new HashMap<>();
        rows.add("c\tm\t1\tNew releases");
        rows.add("c\tm\t2\tPopular films");
        rows.add("c\tm\t3\tFamily films");
        rows.add("c\tm\t4\tHorror and thrillers");
        rows.add("c\ts\t1\tDrama and crime");
        rows.add("c\ts\t2\tKids' TV");
        Set<String> seen = new HashSet<>();
        String[][] filmLists = {{"new_films", "1"}, {"top_films", "2"}, {"kids_films", "3"}, {"because", "4"}};
        long added = now / 1000;
        for (String[] l : filmLists) {
            JSONArray a = d.getJSONArray(l[0]);
            for (int i = 0; i < a.length(); i++) {
                JSONObject f = a.getJSONObject(i);
                String id = String.valueOf(f.getInt("id"));
                if (!seen.add("m" + id)) continue;
                String year = f.optString("year");
                long when = l[1].equals("1") ? added - i * 3600L : added - 86_400L * 30 - i * 3600L;
                // As providers name their copies: some say their picture and sound.
                String[] tags = {" 4K", " 1080p", "", " 4K DV Atmos", " 720p", " 1080p Atmos"};
                rows.add("m\t" + id + "\t" + f.getString("name") + " (" + year + ")" + tags[seen.size() % tags.length] + "\t" + l[1] + "\t"
                        + pic(root, f.optString("poster")) + "\t" + f.optDouble("vote", 0) + "\t" + when * 1000L + "\tmkv\t" + year + "\t\t\t");
                JSONObject yt = f.optJSONObject("yt");
                pages.put("p0|m|" + id, Catalog.previewDetails(f.optString("overview"), genres(f), "", "", f.optString("date"),
                        pic(root, f.optString("bd")), String.valueOf(f.optDouble("vote", 0)), f.optString("cert"), f.optInt("runtime"), null,
                        yt == null ? "" : yt.optString("key")));
            }
        }
        String[][] showLists = {{"top_tv", "1"}, {"kids_tv", "2"}};
        JSONObject hero = d.getJSONObject("hero_tv");
        for (String[] l : showLists) {
            JSONArray a = d.getJSONArray(l[0]);
            for (int i = 0; i < a.length(); i++) {
                JSONObject s = a.getJSONObject(i);
                String id = String.valueOf(s.getInt("id"));
                if (!seen.add("s" + id)) continue;
                rows.add("s\t" + id + "\t" + s.getString("name") + "\t" + l[1] + "\t" + pic(root, s.optString("poster")) + "\t"
                        + s.optDouble("vote", 0) + "\t" + (added - i * 7200L) + "\t" + s.optString("year") + "\t"
                        + s.optString("overview").replace('\t', ' ').replace('\n', ' ') + "\t" + pic(root, s.optString("bd")));
                int seasons = Math.max(1, Math.min(3, s.optInt("seasons", 1)));
                List<String[]> eps = new ArrayList<>();
                String cast = "";
                for (int season = 1; season <= seasons; season++) {
                    boolean real = id.equals(String.valueOf(hero.getInt("id"))) && season == hero.optInt("season");
                    if (real) {
                        JSONArray he = hero.getJSONArray("episodes");
                        for (int k = 0; k < he.length(); k++) {
                            JSONObject e = he.getJSONObject(k);
                            eps.add(new String[]{id + "-" + season + "-" + e.getInt("n"), String.valueOf(season), String.valueOf(e.getInt("n")),
                                    e.optString("name"), e.optString("overview"), pic(root, e.optString("still")), String.valueOf(e.optInt("runtime")),
                                    e.optString("date")});
                        }
                        JSONArray people = hero.optJSONArray("cast");
                        List<String> names = new ArrayList<>();
                        for (int k = 0; people != null && k < people.length() && k < 4; k++) names.add(people.getJSONObject(k).optString("name"));
                        cast = String.join(", ", names);
                    } else {
                        for (int k = 1; k <= 6; k++) {
                            File still = new File(root, "skin-preview/img/e" + id + "-" + season + "-" + k + ".png");
                            eps.add(new String[]{id + "-" + season + "-" + k, String.valueOf(season), String.valueOf(k), "", "",
                                    still.exists() ? "file:" + still.getPath() : "", String.valueOf(s.optInt("runtime") > 0 ? s.optInt("runtime") : 45)});
                        }
                    }
                }
                JSONObject yt = s.optJSONObject("yt");
                pages.put("p0|s|" + id, Catalog.previewDetails(s.optString("overview"), genres(s), cast, "", s.optString("year"),
                        pic(root, s.optString("bd")), String.valueOf(s.optDouble("vote", 0)), s.optString("cert"), 0, eps.toArray(new String[0][]),
                        yt == null ? "" : yt.optString("key")));
            }
        }
        Catalog.get(c).useForPreview(rows, pages);

        // Where two things were left, for Continue watching.
        Resume.clear(c);
        JSONObject f = d.getJSONArray("new_films").getJSONObject(0);
        Resume.save(c, new Resume.Point("p0|m|" + f.getInt("id"), "", f.getString("name"), "", pic(root, f.optString("bd")),
                52 * 60_000L, f.optInt("runtime", 120) * 60_000L, now - 3600_000L));
        JSONObject e3 = hero.getJSONArray("episodes").getJSONObject(2);
        Resume.save(c, new Resume.Point("p0|s|" + hero.getInt("id"), hero.getInt("id") + "-" + hero.optInt("season") + "-3", hero.getString("name"),
                "S" + hero.optInt("season") + " E3  ·  " + e3.optString("name"), pic(root, e3.optString("still")), 21 * 60_000L,
                e3.optInt("runtime", 50) * 60_000L, now));
    }

    private static String pic(String root, String name) {
        if (name == null || name.isEmpty()) return "";
        File f = new File(root, "skin-preview/img/" + name + ".png");
        return f.exists() ? "file:" + f.getPath() : "";
    }

    private static String genres(JSONObject o) {
        JSONArray g = o.optJSONArray("genres");
        List<String> out = new ArrayList<>();
        for (int i = 0; g != null && i < g.length() && i < 2; i++) out.add(g.optString(i));
        return String.join(", ", out);
    }
}
