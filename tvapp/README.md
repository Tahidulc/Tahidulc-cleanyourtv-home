# Homefield TV

An IPTV app for Android TV only, in the look of Sky Q's interface: the blue screens, the menu down the left with the
time and a mini TV, the TV guide, the channel banner over live TV and the playback bar. It plays the family's own
playlists (an M3U address or an Xtream Codes login): their live channels and guide, and their own films and TV shows,
browsed by the playlist's categories. It ships with no playlists or sources of its own, and films and series come only
from the playlists added to it. The app's name and wordmark are Homefield; it carries no Sky logo or name.

## Screens

- **Home:** the menu (Home, TV guide, Movies, TV shows, Sports, Search, Settings), the time and a mini TV playing the
  last channel; a large picture of what is highlighted, then rows: On now, Continue watching, New films, New TV shows,
  Sport on now and favourite channels.
- **TV guide:** categories down the left (All channels, Entertainment, Sport, News, Movies, Kids, Documentaries, Music,
  Favourites, then the playlist's own groups), the highlighted programme's details at the top, the channels with their
  numbers and logos, a now line. Left and Right move through time, Up and Down through the channels, OK watches, holding
  OK makes a channel a favourite, the number keys go to a channel.
- **Live TV:** full screen with the channel banner (the channel between up and down arrows, the day and time, now and
  next). Up and Down look along the channels, OK changes to one, the channel keys change at once, Back hides the banner.
  A stream that drops is tried on the same channel in another playlist, then again.
- **Movies and TV shows:** each of the playlist's categories as a row of posters, the newest first, the highlighted
  title's picture and story above them.
- **A title:** its picture, rating, running time, age rating, story and cast; Play or Resume; a series' seasons and
  episodes; more like it.
- **Player:** the playback bar. OK pauses and plays, Left and Right go back and on (faster when held), Back keeps the
  place, and a series goes on to the next episode.
- **Search:** channels, films and TV shows, typed on the on-screen keyboard.
- **Settings:** the playlists (all of them together or one, their expiry dates), adding an M3U playlist or an Xtream
  Codes login, reading them again, removing everything, and the channel logos: on black tiles (each logo trimmed and set
  in the middle of a black tile, a dark one turned light) or as the playlist gives them.

## Getting a playlist on to a TV

On the TV: Settings, then Add an Xtream Codes login (or Add an M3U playlist), typed on the on-screen keyboard. A playlist
is kept only when it gives live channels.

From a computer with ADB connected to the TV (the setup entry point needs a permission only the ADB shell has):

```
adb shell content insert --uri content://com.gathin.homefieldtv.setup/xtream \
  --bind name:s:Home --bind server:s:http://host:port --bind user:s:NAME --bind pass:s:PASSWORD
adb shell content insert --uri content://com.gathin.homefieldtv.setup/m3u --bind name:s:Home --bind url:s:'http://…'
adb shell content query --uri content://com.gathin.homefieldtv.setup/playlists
adb shell content delete --uri content://com.gathin.homefieldtv.setup/playlists/p1
```

The login stays in the app's private storage on that TV. It is never logged, and the app shows the password only as dots.

## Building

JDK 17, Android SDK 35. In this folder:

```
./gradlew --no-daemon assembleRelease testReleaseUnitTest lintRelease
```

The APK is `app/build/outputs/apk/release/app-release.apk`, signed with the debug key for sideloading.

## Versions

| Version | What changed |
|---|---|
| 1.0 | The first version: Home, the TV guide, live TV with the channel banner, Movies and TV shows from the playlists, title pages with seasons and episodes, the player with the playback bar, Search and Settings. |
| 1.1 | Channel logos on black tiles, drawn as the TV launcher and the phone draw them: in the guide (the channel column and the large logo at the top), the channel banner's cards, Home's channel cards and its large picture, and Search. Each logo from the playlist is trimmed and set in the middle of a black tile; a white JPEG box round it is taken away and a mostly dark logo is turned light. Settings, Channel logos: on black tiles, or as the playlist gives them. |
