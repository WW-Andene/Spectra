# Spectra — R8 / ProGuard rules.
#
# proguard-android-optimize.txt covers most defaults; this file adds
# project-specific overrides.

# ── Strip debug logging in release ───────────────────────────────
# Keeps Log.w/Log.e (warnings + errors) so release crashes still leave
# a useful trail; strips Log.v/Log.d/Log.i which are dev-only spam.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ── kotlinx.serialization ────────────────────────────────────────
# The serializer is generated at compile time and looked up reflectively
# at runtime via the kotlinx.serialization.* infrastructure. Keep the
# generated `Companion` of every @Serializable class so the lookup works
# after R8 renames everything else.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-keep,includedescriptorclasses class com.andene.spectra.**$$serializer { *; }
-keepclassmembers class com.andene.spectra.** {
    *** Companion;
}
-keepclasseswithmembers class com.andene.spectra.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── CameraX uses reflection internally ───────────────────────────
-keep class androidx.camera.** { *; }
-keep class androidx.camera.core.impl.** { *; }

# ── Navigation Component ─────────────────────────────────────────
-keep class * extends androidx.navigation.Navigator
