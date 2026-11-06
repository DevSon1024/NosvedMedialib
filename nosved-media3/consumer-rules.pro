# ProGuard/R8 consumer rules for com.nosved.nosvedmedia
# Preserve public API facade, renderers, decoders, and preference models

-keep class com.nosved.nosvedmedia.NosvedMedia {
    *;
}

-keep class com.nosved.nosvedmedia.media3.** {
    *;
}

