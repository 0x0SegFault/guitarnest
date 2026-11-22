# kotlinx.serialization ships its own consumer rules; keep our models' generated serializers explicitly too
# (tab format, AI requests and answers, practice log).
-keep,includedescriptorclasses class app.guitarnest.**$$serializer { *; }
-keepclassmembers class app.guitarnest.** {
    *** Companion;
}
-keepclasseswithmembers class app.guitarnest.** {
    kotlinx.serialization.KSerializer serializer(...);
}
