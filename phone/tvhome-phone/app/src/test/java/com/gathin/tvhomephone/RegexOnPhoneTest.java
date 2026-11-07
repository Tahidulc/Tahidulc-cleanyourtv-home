package com.gathin.tvhomephone;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * The app's regular expressions as a phone reads them. A phone's are ICU's, not desktop Java's, and the two differ:
 * a class opening "[:" with a ":]" anywhere later in the pattern is a named class to ICU ("[:alpha:]"), so a pattern
 * desktop Java compiles happily fails on the phone the moment its class loads (4.10's ScreenText: "[:.]" with
 * "(?![\\d:])" after it). These tests run on desktop Java, which cannot see that, so the app's own source is read for it.
 */
public class RegexOnPhoneTest {
    private static final Pattern CALL = Pattern.compile(
            "(?:Pattern\\.compile|\\.matches|\\.replaceAll|\\.replaceFirst|\\.split)\\(\\s*\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
    private static final Pattern NAMED = Pattern.compile("\\[:\\^?[A-Za-z_]+:\\]");

    /** Pure: where in a pattern a class opens "[:" that ICU would read as a named class; -1 for none. */
    static int icuTrap(String regex) {
        for (int i = 0; i < regex.length() - 1; i++) {
            char ch = regex.charAt(i);
            if (ch == '\\') {
                i++;
                continue;
            }
            if (ch != '[' || regex.charAt(i + 1) != ':') continue;
            Matcher m = NAMED.matcher(regex);
            if (m.find(i) && m.start() == i) continue;
            if (regex.indexOf(":]", i + 2) >= 0) return i;
        }
        return -1;
    }

    /** A Java string literal's text as the regex sees it (only the escapes that matter here). */
    private static String unescape(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                if (n == '\\' || n == '"' || n == '\'') b.append(n);
                else b.append('\\').append(n);
            } else {
                b.append(ch);
            }
        }
        return b.toString();
    }

    @Test
    public void theTrapIsSeen() {
        assertTrue(icuTrap("(\\d{1,3})\\s?[:.]\\s?([0-5]\\d)(?![\\d:])") >= 0);
        // Fine on a phone: the colon not first, a real named class, an escaped bracket, no ":]" after it.
        assertTrue(icuTrap("(\\d{1,3})\\s?[.:]\\s?([0-5]\\d)(?![\\d:])") < 0);
        assertTrue(icuTrap("[[:alpha:]]+") < 0);
        assertTrue(icuTrap("\\[:x(?![\\d:])") < 0);
        assertTrue(icuTrap("[:.]") < 0);
    }

    @Test
    public void everyPatternInTheAppCompilesOnAPhone() throws IOException {
        List<String> bad = new ArrayList<>();
        List<File> files = new ArrayList<>();
        collect(new File("src/main/java"), files);
        assertTrue("the app's source was not found from " + new File(".").getAbsolutePath(), !files.isEmpty());
        for (File f : files) {
            String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            Matcher m = CALL.matcher(src);
            while (m.find()) {
                String regex = unescape(m.group(1));
                if (icuTrap(regex) >= 0) {
                    int line = 1;
                    for (int i = 0; i < m.start(); i++) if (src.charAt(i) == '\n') line++;
                    bad.add(f.getName() + ":" + line + "  " + regex);
                }
            }
        }
        assertTrue("A class opening \"[:\" fails on a phone (write \"[.:]\", not \"[:.]\"): " + bad, bad.isEmpty());
    }

    private static void collect(File dir, List<File> out) {
        File[] all = dir.listFiles();
        if (all == null) return;
        for (File f : all) {
            if (f.isDirectory()) collect(f, out);
            else if (f.getName().endsWith(".java")) out.add(f);
        }
    }
}
