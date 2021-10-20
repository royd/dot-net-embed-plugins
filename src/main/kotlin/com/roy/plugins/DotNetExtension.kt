package com.roy.plugins

import groovy.lang.Closure
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Property

abstract class DotNetExtension(var buildTypes: NamedDomainObjectContainer<DotNetBuildConfig>) {
    abstract val projectFile: Property<String>
    fun projectFile(file: String) {
        projectFile.set(file)
    }

    abstract val solutionFile: Property<String>
    fun solutionFile(file: String) {
        solutionFile.set(file)
    }

    fun buildTypes(closure: Closure<DotNetBuildConfig>) {
        buildTypes.configure(closure)
    }

    abstract val pluginLogLevel: Property<LogLevel>
    fun pluginLogLevel(level: LogLevel) {
        pluginLogLevel.set(level)
    }

    abstract val msBuildLogLevel: Property<LogLevel>
    fun msBuildLogLevel(level: LogLevel) {
        msBuildLogLevel.set(level)
    }
}