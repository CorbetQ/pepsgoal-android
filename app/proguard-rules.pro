# kotlinx.serialization : les classes @Serializable gardent leurs sérialiseurs
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class fr.pepsgoal.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class fr.pepsgoal.**$$serializer { *; }
-keepclassmembers class fr.pepsgoal.** { *** Companion; }
# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
