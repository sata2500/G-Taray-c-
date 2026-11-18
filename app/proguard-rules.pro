# GeckoView ve Mozilla bileşenleri kendi consumer kurallarını getirir.
# Uygulamaya özel kurallar buraya eklenir.

# NewPipe Extractor ve JavaScript motoru (Rhino) yansıma kullanır.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
-dontwarn org.mozilla.javascript.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn org.jspecify.annotations.**
