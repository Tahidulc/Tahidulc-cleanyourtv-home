package com.gathin.homefieldtv.engine;

import java.util.List;
import java.util.Map;

/**
 * The catalogue's look-ups kept small (the memory diet at 200,000 titles), each a few plain arrays instead of a map entry
 * or a string of its own a title:
 * <ul>
 *   <li>{@link Keys}: a title by its key (its copies' keys too, to the title shown), an open table of the titles' own key
 *   strings and the titles, a third bigger than the titles; the family's own TorBox titles over it in a small map;</li>
 *   <li>{@link Names}: every title's name as search reads it ("the long night 2"), all in one string with where each
 *   starts, searched in place.</li>
 * </ul>
 */
final class TitleIndex {
    private TitleIndex() {}

    /** A title by its key. */
    abstract static class Keys {
        abstract Catalog.Title get(String key);

        /** As a map says (tests, previews and the small catalogues). */
        static Keys of(Map<String, Catalog.Title> map) {
            return new Keys() {
                @Override Catalog.Title get(String key) {
                    return key == null ? null : map.get(key);
                }
            };
        }

        /** own's titles first, then base's (the family's own TorBox titles over the playlists'). */
        static Keys over(Keys base, Map<String, Catalog.Title> own) {
            return new Keys() {
                @Override Catalog.Title get(String key) {
                    if (key == null) return null;
                    Catalog.Title t = own.get(key);
                    return t != null ? t : base.get(key);
                }
            };
        }
    }

    /** The open table: every key put (a later put of the same key replaces its title, as a map does), then read. */
    static final class Table extends Keys {
        private final String[] keys;
        private final Catalog.Title[] titles;
        private int size;

        Table(int expected) {
            int cap = Math.max(16, expected + expected / 3 + 1);
            keys = new String[cap];
            titles = new Catalog.Title[cap];
        }

        void put(String key, Catalog.Title t) {
            if (key == null) return;
            if (size + 1 >= keys.length) throw new IllegalStateException("table full");
            int i = Math.floorMod(key.hashCode() * 0x9E3779B9, keys.length);
            while (keys[i] != null) {
                if (keys[i].equals(key)) {
                    titles[i] = t;
                    return;
                }
                if (++i == keys.length) i = 0;
            }
            keys[i] = key;
            titles[i] = t;
            size++;
        }

        @Override Catalog.Title get(String key) {
            if (key == null) return null;
            int i = Math.floorMod(key.hashCode() * 0x9E3779B9, keys.length);
            for (String k; (k = keys[i]) != null; ) {
                if (k.equals(key)) return titles[i];
                if (++i == keys.length) i = 0;
            }
            return null;
        }

        int size() {
            return size;
        }
    }

    /** Every title's name as search reads it, in one string, searched in place (no string made a title or a search). */
    static final class Names {
        private final String all;
        /** Where each name starts in all; the last one more, its end. */
        private final int[] at;

        private Names(String all, int[] at) {
            this.all = all;
            this.at = at;
        }

        static Names of(List<Catalog.Title> list) {
            StringBuilder b = new StringBuilder(list.size() * 24);
            int[] at = new int[list.size() + 1];
            for (int i = 0; i < list.size(); i++) {
                at[i] = b.length();
                b.append(Catalog.searchable(list.get(i).name));
            }
            at[list.size()] = b.length();
            return new Names(b.toString(), at);
        }

        /** These names, then more's (the family's own TorBox titles added at the end of the list). */
        Names plus(List<Catalog.Title> more) {
            if (more.isEmpty()) return this;
            Names m = of(more);
            int n = at.length - 1, k = m.at.length - 1, len = all.length();
            int[] a = new int[n + k + 1];
            System.arraycopy(at, 0, a, 0, n);
            for (int i = 0; i <= k; i++) a[n + i] = len + m.at[i];
            return new Names(all + m.all, a);
        }

        int size() {
            return at.length - 1;
        }

        /** The i-th name starts with q. */
        boolean startsWith(int i, String q) {
            int s = at[i];
            return at[i + 1] - s >= q.length() && all.regionMatches(s, q, 0, q.length());
        }

        /** The i-th name has q in it somewhere. */
        boolean contains(int i, String q) {
            int s = at[i], last = at[i + 1] - q.length();
            for (int p = s; p <= last; p++) if (all.regionMatches(p, q, 0, q.length())) return true;
            return false;
        }

        /** Tests: the i-th name. */
        String name(int i) {
            return all.substring(at[i], at[i + 1]);
        }
    }
}
