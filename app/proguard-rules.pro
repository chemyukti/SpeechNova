# SpeechNova — R8 configuration.
#
# This file used to keep almost everything: `-keep class android.** { *; }`,
# `androidx.**`, `kotlin.**` and the whole app package. Blanket keeps like that
# don't make a build safer, they switch R8 off — nothing can be renamed, nothing
# can be removed, and nothing can be inlined across a kept boundary. That is
# exactly what Play reported: shrinking, obfuscation and optimization all
# disabled at once.
#
# The libraries this app uses (AndroidX, Compose, Play Services, ML Kit) all
# ship their own consumer ProGuard rules, which R8 applies automatically. They
# do not need keeping by hand, and keeping them by hand is what broke this.
#
# The app itself uses no reflection, no Gson/Moshi/Retrofit models and no
# Class.forName, so it needs no keep rules of its own either.

# Crash reports stay readable: line numbers are preserved and the original
# source file name is hidden behind a placeholder.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin coroutines' internal debugging probes are looked up reflectively by the
# runtime; without this the stack traces get worse, and R8 warns.
-dontwarn kotlinx.coroutines.**
