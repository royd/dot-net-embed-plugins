package com.roy.plugins

import org.gradle.api.Named
import org.gradle.api.provider.Property

abstract class DotNetBuildConfig(private val buildConfig: String) : Named {
    override fun getName(): String {
        return buildConfig
    }

    abstract val msBuildConfig: Property<String>
    fun msBuildConfig(config: String) {
        msBuildConfig.set(config)
    }
}