# kotlinx.serialization ships its own consumer rules; keep our models' generated serializers explicitly too.
-keep,includedescriptorclasses class app.guitarnest.data.**$$serializer { *; }
-keepclassmembers class app.guitarnest.data.** {
    *** Companion;
}
-keepclasseswithmembers class app.guitarnest.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
