package com.roy.kotlinapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class MainActivity : AppCompatActivity() {
    /**
     * Mono is initialized using the mono.MonoRuntimeProvider content provider declared in AndroidManifest.xml
     * Usage is the same as that in the output of the C# project.
     *   i.e. test/dot-net/DotNetLibrary/obj/debug/android/manifest/AndroidManifest.xml
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val logger = com.roy.dotnetlibrary.Logger()
        logger.log("C# log message")
    }
}
