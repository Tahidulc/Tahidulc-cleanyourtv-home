# TV Home for Android phones and tablets

The launcher's own parts on a phone: Home with the scores box and weather, Watch (Discover, Movies and TV shows, a
title's page, the Episode map, Coming up, Because you watched, Film night, trailers), Sports (Match Centre, tables, F1,
This weekend), alerts as phone notifications with the Control Centre's switches, and Live TV for the family's own M3U
playlist or Xtream Codes login. It pairs with the TVs as Delivery to TV does and keeps My list, ratings, the Film
night plan, Coming up's mutes, reminders and starred clubs the same everywhere (SYNC.md).

Full screen, a tap brings up an info tile that suits what's on (the Full-screen TV Info Tiles canvas): Match night for a
game (the scores box with both crests and the competition's logo, the fixture, kick-off or the score, each club's
form or the game so far), Cinematic for a film, Card for a series' episode and Glass ribbon for the rest, each with
the channel's logo. The player's options can set one style for everything, or the guide panel as before.

Titles open in the licensed streaming app that carries them in the UK (or its store), on the phone or, with Play on TV,
on a TV. Live TV plays only the playlist you give it; the app ships with no channels or sources.

Build: JDK 17, Android SDK 34. `./gradlew assembleRelease testReleaseUnitTest lintRelease`; the APK is in
`app/build/outputs/apk/release/`, signed with the debug key for sideloading.
