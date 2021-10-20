package com.roy.plugins

import com.android.build.gradle.AppExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class DotNetAppPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.extensions.getByType(AppExtension::class.java).aaptOptions.noCompress("dll", "dll.config", "pdb", "mdb", "mj", "jm", "environment")
  }
}