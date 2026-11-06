# ProGuard/R8 consumer rules for com.nosved.native-codecs
# Preserve JNI bridge entry points and methods invoked via JNI

-keep class com.nosved.native_codecs.NativeLib** {
    *;
}

-keepclasseswithmembernames class * {
    native <methods>;
}

