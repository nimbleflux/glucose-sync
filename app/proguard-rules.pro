-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.nimbleflux.medtrumwatch.**$$serializer { *; }
-keepclassmembers class com.nimbleflux.medtrumwatch.** { *** Companion; }
-keepclasseswithmembers class com.nimbleflux.medtrumwatch.** { kotlinx.serialization.KSerializer serializer(...); }

-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn kotlinx.serialization.**
