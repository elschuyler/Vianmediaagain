# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line numbers for LogKeeper/LogCatcher while hiding source filenames
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# FFmpeg Kit
-keep class com.arthenica.** { *; }
-dontwarn com.arthenica.**

# Fresco / SoLoader reflection (VideoEditorScreen / FFmpegService animated WebP decode)
-keep class com.facebook.soloader.** { *; }
-keep class com.facebook.animated.webp.** { *; }
-keep class com.facebook.imagepipeline.animated.** { *; }
-keep class com.facebook.common.references.** { *; }
-dontwarn com.facebook.soloader.**
-dontwarn com.facebook.infer.annotation.**
-dontwarn javax.annotation.**

# ExoPlayer / Media3 & Jellyfin FFmpeg Audio Decoder
-keep class androidx.media3.** { *; }
-keep class org.jellyfin.media3.** { *; }
-dontwarn androidx.media3.**
-dontwarn org.jellyfin.media3.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep enum classes
-keepclassmembers enum * { *; }

# Keep Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
