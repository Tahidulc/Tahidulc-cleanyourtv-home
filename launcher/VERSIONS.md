# Launcher versions

Each change to the launcher is a new version: `versionName` and `versionCode` in `app/build.gradle` go up by one
(2.50 is versionCode 250), the commit is tagged (`v2.50`), and a line goes here with what changed and which TVs
have it.

| Version | What changed | On |
|---|---|---|
| 2.49 | Starting point for version numbers. Live Centre: one scoreboard bar with the competition's logo, club crests, a blue score box and a clock counting minutes and seconds; stats and facts folding out of it (goalscorers with pictures, player of the match, formations, win chance before kick-off, detailed stats, form, table); goals, substitutions and line-ups folding out as they happen; goal videos for every LaLiga game; official highlights at full time, played inside it. Alerts: every logo and photo from its full-size original, larger crests and F1 team logos, Mercedes' star lifted on black, the full-time card naming its winner. Pop-up mode: OK reaches the app on the TCL. | TCL |
| 2.50 | Channel tuning from Home tiles and the TV guide: the helper is connected again before TiviMate opens (without it TiviMate only opened on the last channel); a tune gives up after 30 seconds instead of minutes, closes TiviMate's search if it is still up and says "Couldn't find …" rather than lifting onto another channel; the search is closed sooner after a pick. New, off unless a TV's theme sets `"channel_app": "kleva"`: channels tuned through Kleva TV's search (one card per channel), TiviMate when Kleva TV hasn't got it. Each tune's time is logged. | Not on a TV yet |
