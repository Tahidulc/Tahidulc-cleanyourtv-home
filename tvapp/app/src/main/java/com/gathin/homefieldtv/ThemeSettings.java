package com.gathin.homefieldtv;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings → Colour theme: the primary colours, then the accents, as rows with each colour's disc
 * (RowList.Row.swatch) under the section names "Primary colour" and "Accent colour", the pair in use ticked. OK on a
 * disc keeps it with the other half of the pair as it was (Theme.save); Settings then has the screens made again in
 * it. The sections' row shows the pair in use under its name: "Indigo • Blue".
 */
final class ThemeSettings {
    private ThemeSettings() {}

    static final String PRIMARY = "p:", ACCENT = "a:";

    /** The page's rows: a section name, the primaries, a section name, the accents. */
    static List<RowList.Row> rows() {
        List<RowList.Row> rows = new ArrayList<>();
        rows.add(RowList.Row.header("Primary colour"));
        for (int i = 0; i < Theme.PRIMARIES.length; i++) {
            Theme.Primary p = Theme.PRIMARIES[i];
            rows.add(RowList.Row.swatch(PRIMARY + i, p.name, p.swatch, i == Theme.primary()));
        }
        rows.add(RowList.Row.header("Accent colour"));
        for (int i = 0; i < Theme.ACCENTS.length; i++) {
            Theme.Accent a = Theme.ACCENTS[i];
            rows.add(RowList.Row.swatch(ACCENT + i, a.name, a.colour, i == Theme.accent()));
        }
        rows.add(RowList.Row.note("The accent colours where the remote is, what is playing, switches and section names."));
        return rows;
    }

    /** Where the remote starts on the page: the primary in use. */
    static int focus(List<RowList.Row> rows) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).checked) return i;
        return 1;
    }

    /** Pure: the pair a row picks, from the pair in use: {primary, accent}; null for a row that picks nothing. */
    static int[] pick(Object tag, int primary, int accent) {
        if (!(tag instanceof String)) return null;
        String t = (String) tag;
        try {
            if (t.startsWith(PRIMARY)) return new int[]{Integer.parseInt(t.substring(PRIMARY.length())), accent};
            if (t.startsWith(ACCENT)) return new int[]{primary, Integer.parseInt(t.substring(ACCENT.length()))};
        } catch (NumberFormatException ignored) {
            // not a colour's row
        }
        return null;
    }

    /** OK on a disc: true when that makes a new pair, which is kept (the screens are to be made again). */
    static boolean chosen(Context c, RowList.Row row) {
        int[] pair = pick(row.tag, Theme.primary(), Theme.accent());
        if (pair == null || pair[0] == Theme.primary() && pair[1] == Theme.accent()) return false;
        Theme.save(c, pair[0], pair[1]);
        return true;
    }
}
