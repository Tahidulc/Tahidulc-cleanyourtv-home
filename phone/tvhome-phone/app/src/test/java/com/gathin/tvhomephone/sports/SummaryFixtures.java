package com.gathin.tvhomephone.sports;

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** ESPN summaries kept from real games (trimmed to the fields read): AEK Athens 2–3 Real Madrid, Dortmund v Betis before kick-off. */
final class SummaryFixtures {
    private SummaryFixtures() {}

    static Espn.Summary read(String name, String id) throws Exception {
        try (InputStream in = SummaryFixtures.class.getClassLoader().getResourceAsStream(name)) {
            return Espn.summary(new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)), id);
        }
    }

    static Espn.Summary aek() throws Exception {
        return read("espn_summary_aek_rma.json", "401915383");
    }

    static Espn.Summary dortmund() throws Exception {
        return read("espn_summary_bvb_betis.json", "401915304");
    }
}
