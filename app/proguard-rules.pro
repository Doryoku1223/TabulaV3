# Add project specific ProGuard rules here.
# Tabula V3 ProGuard Rules

# Keep Coil classes
-keep class coil.** { *; }
-dontwarn coil.**

# Keep Compose classes
-keep class androidx.compose.** { *; }

# Keep data models
-keep class com.tabula.v3.data.model.** { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
