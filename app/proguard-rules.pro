# Add project specific ProGuard rules here.

# ============================================
# 🔒 CRITICAL: Keep Android/Compose classes
# ============================================

# Keep all classes with native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep classes that have custom constructors (Activity, Service, etc.)
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# Keep android.os.Parcelable classes
-keep interface android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep all Parcelable subclasses
-keepclassmembers class * implements android.os.Parcelable {
    static android.os.Parcelable$Creator CREATOR;
}

# Keep all enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep R (resources) classes
-keep class **.R$* {
    public static <fields>;
}

# ============================================
# 🔒 CRITICAL: Keep Compose classes
# ============================================

-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# Keep Compose runtime
-keep class androidx.compose.runtime.** { *; }
-keep interface androidx.compose.runtime.** { *; }

# Keep Compose foundation
-keep class androidx.compose.foundation.** { *; }
-keep interface androidx.compose.foundation.** { *; }

# Keep Compose Material3
-keep class androidx.compose.material3.** { *; }
-keep interface androidx.compose.material3.** { *; }

# Keep Compose UI
-keep class androidx.compose.ui.** { *; }
-keep interface androidx.compose.ui.** { *; }

# ============================================
# 🔒 CRITICAL: Keep AndroidX classes
# ============================================

-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-keep class androidx.core.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class androidx.** { *; }

# ============================================
# 🔒 CRITICAL: Keep Google Play Services
# ============================================

-keep class com.google.android.gms.** { *; }
-keep interface com.google.android.gms.** { *; }
-keep class com.google.android.ads.** { *; }
-keepclassmembers class com.google.** { *; }

# Keep AdMob classes
-keep class com.google.android.gms.ads.** { *; }
-keep interface com.google.android.gms.ads.** { *; }
-keepclassmembers class com.google.android.gms.ads.** { *; }

# ============================================
# 🔒 CRITICAL: Keep MLKit Translation
# ============================================

-keep class com.google.mlkit.nl.translate.** { *; }
-keep interface com.google.mlkit.nl.translate.** { *; }
-keep class com.google.mlkit.common.** { *; }
-keep interface com.google.mlkit.common.** { *; }
-keepclassmembers class com.google.mlkit.** { *; }

# ============================================
# 🔒 CRITICAL: Keep Android OS classes
# ============================================

-keep class android.** { *; }
-keep interface android.** { *; }
-keep class android.speech.** { *; }
-keep class android.speech.tts.** { *; }
-keep interface android.speech.** { *; }

# ============================================
# 🔒 CRITICAL: Keep Your App Classes
# ============================================

-keep class com.parashmani.speechnova.** { *; }
-keep interface com.parashmani.speechnova.** { *; }
-keepclassmembers class com.parashmani.speechnova.** { *; }

# Keep MainActivity
-keep class com.parashmani.speechnova.MainActivity { *; }
-keepclassmembers class com.parashmani.speechnova.MainActivity { *; }

# ============================================
# 🔒 Kotlin support
# ============================================

-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep interface kotlinx.** { *; }
-keepclassmembers class kotlin.** { *; }

# Keep annotations
-keep class java.lang.annotation.* { *; }
-keep class kotlin.annotation.* { *; }

# ============================================
# Optimizations (safe)
# ============================================

-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================
# Important: Don't remove these
# ============================================

# Keep everything in your package just to be safe for initial release
-keep class com.parashmani.** { *; }
-keepclassmembers class com.parashmani.** { *; }

# For debugging
-keepattributes InnerClasses
-keepattributes EnclosingMethod