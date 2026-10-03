# kotlinx.serialization keeps generated serializers reachable through reflection-free
# lookups on the companion; R8 needs these hints to not strip them.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class io.github.deeplow.nobstuner.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.deeplow.nobstuner.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.deeplow.nobstuner.**$$serializer { *; }

# Keep enum values used by kotlinx.serialization and Compose previews.
-keepclassmembers enum io.github.deeplow.nobstuner.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
