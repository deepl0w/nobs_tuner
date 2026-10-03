# kotlinx.serialization keeps generated serializers reachable through reflection-free
# lookups on the companion; R8 needs these hints to not strip them.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class io.github.deeplow.stringtune.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.deeplow.stringtune.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.deeplow.stringtune.**$$serializer { *; }

# Keep enum values used by kotlinx.serialization and Compose previews.
-keepclassmembers enum io.github.deeplow.stringtune.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
