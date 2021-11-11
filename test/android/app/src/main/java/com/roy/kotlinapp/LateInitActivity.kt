package com.roy.kotlinapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class LateInitActivity : AppCompatActivity() {
    /**
     * Mono is initialized manually.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_late_init)

        MainScope().launch {
            Mono().initialize(this@LateInitActivity)

            val logger = com.roy.dotnetlibrary.Logger()
            logger.log("C# log message")
        }
    }
}