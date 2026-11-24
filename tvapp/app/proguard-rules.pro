# The live overlays' page (overlays/assets/broadcast) calls the bridge of com.gathin.broadcast.BroadcastView by name.
-keepclassmembers class com.gathin.broadcast.** {
    @android.webkit.JavascriptInterface <methods>;
}
# Media3 makes the FFmpeg audio renderer by name (DefaultRenderersFactory, extension renderers on).
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer { <init>(...); }

# Vosk's speech recognition is reached through JNA, by name; JNA's desktop parts aren't on Android.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**

# The memory log names the screen on top and each screen's frame times (MemLog, FrameStats) by the screen's class.
-keepnames class * extends com.gathin.homefieldtv.Screen
