package com.roy.kotlinapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mono.MonoPackageManager

/**
 * Initializes Mono.
 *
 * An alternative to mono.MonoRuntimeProvider content provider that allows delayed initialization.
 */
class Mono {
    suspend fun initialize(context: Context) {
        val info = context.applicationInfo
        val apks = mutableListOf(info.sourceDir)
        info.splitPublicSourceDirs?.let {
            apks.addAll(it)
        }

        withContext(Dispatchers.IO) {
            MonoPackageManager.LoadApplication(context, info, apks.toTypedArray())
        }
    }
}
