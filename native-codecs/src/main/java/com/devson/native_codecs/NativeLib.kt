package com.devson.native_codecs

class NativeLib {

    /**
     * A native method that is implemented by the 'native_codecs' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'native_codecs' library on application startup.
        init {
            System.loadLibrary("native_codecs")
        }
    }
}