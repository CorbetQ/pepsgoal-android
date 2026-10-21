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
# Code de l'application gardé tel quel (sérialisation, récepteurs, widget) : R8 optimise surtout Compose et les bibliothèques
-keep class fr.pepsgoal.** { *; }
-dontwarn javax.annotation.**
# MediaPipe LLM Inference (code natif appelé par JNI) et protobuf
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
-dontwarn com.google.auto.value.**
