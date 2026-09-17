# R8 rules for release builds.
#
# Room, Compose and AndroidX ship their own consumer rules. The rules below cover
# the two places where this app relies on reflection or on generated code that R8
# cannot see from the call graph alone.

# kotlinx.serialization: protocol DTOs are serialized via generated $serializer
# companions reached through the SerializersModule, so keep the companion holders.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.silverphone.app.platform.transfer.** {
    *** Companion;
}
-keepclasseswithmembers class com.silverphone.app.platform.transfer.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class com.silverphone.app.**
-keepclassmembers class com.silverphone.app.** {
    static **$* *;
}
-keepclassmembers @kotlinx.serialization.Serializable class com.silverphone.app.** {
    *** Companion;
}

# The image cropper view is inflated from XML inside the library and is reached
# only through its public constructor.
-keep class com.canhub.cropper.** { *; }
