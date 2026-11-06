package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.widget.ImageView;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * What a Home style (Skin) needs from the launcher: its rows, pictures, guide and actions. SkinFeeds is the real one,
 * over launcher v2's engine; a preview builds its own with fixed rows, so a style can be drawn without a TV.
 */
interface SkinHost {
    Context context();

    /** Design pixels (the 1920 x 1080 screen the styles are drawn for) to the screen's. */
    int px(float design);

    Typeface regular();

    Typeface semi();

    /** Inter Display, semi-bold: the large headings. */
    Typeface display();

    Handler main();

    Executor io();

    /** A picture into v, about width pixels wide (kept in memory); call on the main thread, it is read off it. */
    void image(ImageView v, String url, int width);

    /** As image, then `then` on the main thread once the picture is in (not called when it could not be read). */
    void image(ImageView v, String url, int width, Runnable then);

    /** One of the launcher's rows (Skin.ROW_* or "movies#3" for a page's tab), read now: call off the main thread. */
    List<Skin.Item> row(String which) throws Exception;

    /** The heading a row has on this TV ("Because you watched Lupin"), or null when it has none to give. */
    String rowName(String which);

    /** A section page's tabs, for a library's categories: "movies", "shows", "kids", "discover". */
    List<String> tabs(String page);

    /** The guide's channels, in the playlist's order: {sid, name, group, logo, stream id}. */
    List<String[]> channels();

    /** What is on the channels from `from` on (Sky's guide), by sid, each list in time order; off the main thread. */
    Map<String, List<GuideV2.Show>> schedules(List<String[]> channels, long from);

    /** A channel's logo picture (Sky's, white on clear), by sid. */
    String channelLogo(String sid);

    /** OK on an item: what the launcher does for it (resume it in its app, tune the channel, open the app, the panel). */
    void ok(Skin.Item it);

    /** OK held on an item: the title's panel (its cast, seasons, My list), or the item's own long press. */
    void details(Skin.Item it);

    /** TiviMate on this channel ({sid, name, ...}). */
    void tune(String[] channel);

    /** A panel of the launcher's: OPEN_CONTROL_CENTRE, OPEN_SEARCH, OPEN_STYLES, OPEN_NOTIFICATIONS, OPEN_WEATHER. */
    void open(String what);

    /** The weather now, short ("9° · Cloudy"); "" before it is known. */
    String weather();

    /** The ticker's lines, {tag, words}: live games and what starts soon, then the headlines. Off the main thread. */
    List<String[]> ticker();

    /** The family's bedtime as minutes after midnight (Fits tonight's), for "ends before bed". */
    int bedMinutes();

    /**
     * The TV's graphics chip is a slow one (the theme's lite_home, the TCL): big redraws rarer and shorter, a picture
     * behind everything changing once the focus rests rather than while it moves, quick fades.
     */
    boolean lite();
}
