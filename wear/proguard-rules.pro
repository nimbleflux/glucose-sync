-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.nimbleflux.glucosesync.**$$serializer { *; }
-keepclassmembers class com.nimbleflux.glucosesync.** { *** Companion; }
-keepclasseswithmembers class com.nimbleflux.glucosesync.** { kotlinx.serialization.KSerializer serializer(...); }

-dontwarn kotlinx.serialization.**
