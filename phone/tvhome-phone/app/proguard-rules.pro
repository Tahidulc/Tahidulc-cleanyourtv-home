# Glide's generated module and its reflection.
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-dontwarn com.bumptech.glide.**

# Media3 makes the FFmpeg audio renderer by name (DefaultRenderersFactory, extension renderers on).
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer { <init>(...); }

# The live overlays' page (overlays/assets/broadcast) calls the bridge of com.gathin.broadcast.BroadcastView by name.
-keepclassmembers class com.gathin.broadcast.** {
    @android.webkit.JavascriptInterface <methods>;
}

# Cast reads its options class by name (the manifest's meta-data).
-keep class com.gathin.tvhomephone.cast.CastSetup { *; }

# Vosk's speech recognition is reached through JNA, by name; JNA's desktop parts aren't on Android.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
