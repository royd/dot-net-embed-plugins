package com.roy.plugins

import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.ide.common.symbols.parseManifest
import groovy.lang.Closure
import org.gradle.api.*
import org.gradle.api.tasks.Exec
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import java.io.File

class DotNetLibraryPlugin : Plugin<Project> {
    companion object {
        const val dotNetGroup = "dotNet"
        const val monoFrameworkPath = "/Library/Frameworks/Mono.framework/Versions/Current"
        const val xamarinAndroidPath = "/Library/Frameworks/Xamarin.Android.framework/Versions/Current"
        const val buildTasksDllFile = "$xamarinAndroidPath/lib/xamarin.android/xbuild/Xamarin/Android/Xamarin.Android.Build.Tasks.dll"
        const val monoDisPath = "$monoFrameworkPath/bin/monodis"
        const val msBuildExecutable = "$monoFrameworkPath/Commands/msbuild"
    }

    class AndroidTaskNames {
        val clean = "clean"
        fun compileJava(buildType: String) = "compile${buildType.capitalize()}JavaWithJavac"
        fun compileKotlin(buildType: String) = "compile${buildType.capitalize()}Kotlin"
        fun mergeProguardFiles(buildType: String) = "merge${buildType.capitalize()}GeneratedProguardFiles"
    }

    class DotNetTaskNames {
        val clean = "cleanDotNet"
        fun compile(buildConfig: String): String = "compile${buildConfig.capitalize()}DotNet"
        fun unpackProguardFile(buildConfig: String): String = "unpack${buildConfig.capitalize()}DotNetProguardFile"
        fun unzipApk(buildConfig: String): String = "unzipApk${buildConfig.capitalize()}"
    }

    class DotNetFiles(val dotNetProjectDir: Provider<File>) {
        private val _binDir = "bin"
        private val _binConfigDir = "bin/%s"

        private val _objDir = "obj"
        private val _objConfigDir = "obj/%s"
        private val _javaSrcDir = "obj/%s/android/src"
        private val _manifestFile = "obj/%s/android/manifest/AndroidManifest.xml"
        private val _buildTasksDir = "obj/%s/disassembled/Xamarin.Android.Build.Tasks"
        private val _proguardFile = "obj/%s/disassembled/Xamarin.Android.Build.Tasks/proguard_xamarin.cfg"
        private val _apkFile = "obj/%s/android/bin/%s.apk"
        private val _unzippedApkDir = "obj/%s/android/bin/apk"
        private val _unzippedAssembliesDir = "obj/%s/android/bin/apk/assemblies"
        private val _unzippedJniLibsDir = "obj/%s/android/bin/apk/lib"

        val binDir: Provider<File> = dotNetProjectDir.map { it.resolve(_binDir) }
        val objDir: Provider<File> = dotNetProjectDir.map { it.resolve(_objDir) }

        private fun map(path: String, vararg args:String): Provider<File> = dotNetProjectDir.map {
            it.resolve(path.format(*args))
        }

        fun binConfigDir(buildConfig: String) = map(_binConfigDir, buildConfig)

        fun objConfigDir(buildConfig: String) = map(_objConfigDir, buildConfig)

        fun buildTasksDir(buildConfig: String) = map(_buildTasksDir, buildConfig)

        fun manifestFile(buildConfig: String) = map(_manifestFile, buildConfig)

        fun proguardFile(buildConfig: String) = map(_proguardFile, buildConfig)

        fun javaSrcDir(buildConfig: String) = map(_javaSrcDir, buildConfig)

        fun apkFile(buildConfig: String, packageName: String) = map(_apkFile, buildConfig, packageName)

        fun unzippedApkDir(buildConfig: String) = map(_unzippedApkDir, buildConfig)

        fun unzippedAssembliesDir(buildConfig: String) = map(_unzippedAssembliesDir, buildConfig)

        fun unzippedJniLibsDir(buildConfig: String) = map(_unzippedJniLibsDir, buildConfig)
    }

    override fun apply(target: Project) {
        val dotNetExtension = target.extensions.create("dotNet", DotNetExtension::class.java, target.container(DotNetBuildConfig::class.java)).apply {
            pluginLogLevel.convention(LogLevel.INFO)
            msBuildLogLevel.convention(LogLevel.INFO)
        }

        val baseDir = dotNetExtension.projectFile.map { target.file(it).parentFile }
        val dotNetFiles = DotNetFiles(baseDir)

        val dotNetTaskNames = DotNetTaskNames()
        val androidTaskNames = AndroidTaskNames()

        target.tasks.register(dotNetTaskNames.clean, Delete::class.java) {
            it.group = dotNetGroup
            it.delete(
                dotNetFiles.binDir.get(),
                dotNetFiles.objDir.get()
            )
        }

        dotNetExtension.buildTypes.all { buildType ->
            val androidBuildTypeName = buildType.name

            // Disassemble Xamarin.Android.Build.Tasks.dll to get the default Xamarin proguard file
            target.tasks.register(dotNetTaskNames.unpackProguardFile(androidBuildTypeName), Exec::class.java) { execTask ->
                val dir = dotNetFiles.buildTasksDir(androidBuildTypeName).get()
                execTask.apply {
                    // Use anonymous class instead of lambda or task will never be up to date
                    doFirst(object : Action<Task> {
                        override fun execute(t: Task) {
                            dir.deleteRecursively()
                            dir.mkdirs()
                        }
                    })
                    group = dotNetGroup
                    commandLine(monoDisPath, "--mresources", buildTasksDllFile)
                    workingDir(dir)
                    inputs.file(buildTasksDllFile)
                    outputs.file(dotNetFiles.proguardFile(androidBuildTypeName))
                }
            }

            val compileDotNetProvider = target.tasks.register(dotNetTaskNames.compile(androidBuildTypeName), Exec::class.java) { execTask ->
                val solutionFile = target.file(dotNetExtension.solutionFile)
                if (!solutionFile.exists()) {
                    throw GradleException("Solution file does not exist: ${solutionFile.absolutePath}")
                }

                val csProjFile = target.file(dotNetExtension.projectFile)
                if (!csProjFile.exists()) {
                    throw GradleException("Project file does not exist: ${csProjFile.absolutePath}")
                }

                val dotNetProjectDir = dotNetFiles.dotNetProjectDir.get()
                val msBuildVerbosity = getMsBuildVerbosity(dotNetExtension.msBuildLogLevel.get())
                val msBuildArgs = listOf(dotNetProjectDir.absolutePath,
                    "-property:Configuration=${dotNetExtension.buildTypes.getByName(androidBuildTypeName).msBuildConfig.get()}",
                    "-target:restore,BuildApk",
                    "-verbosity:$msBuildVerbosity")

                val originalPathEnvironment = System.getenv("PATH")
                val commandsPath = "$monoFrameworkPath/Commands"
                val path = "$originalPathEnvironment:$commandsPath"

                val outputDirs = listOf(
                    dotNetFiles.binConfigDir(androidBuildTypeName).get(),
                    dotNetFiles.objConfigDir(androidBuildTypeName).get(),
                )

                val pluginLogLevel = dotNetExtension.pluginLogLevel.get()
                if (target.logger.isEnabled(pluginLogLevel)) {
                    val builder = StringBuilder()
                    builder.appendln("DotNet $androidBuildTypeName output directories:")
                    outputDirs.forEach {
                        builder.append("  ")
                        builder.appendln(it.absolutePath)
                    }
                    target.logger.log(pluginLogLevel, builder.toString())
                }

                val solutionDir = solutionFile.parentFile
                val inputFiles = target.fileTree(solutionDir).apply {
                    exclude(
                        "**/bin/**",
                        "**/obj/**",
                        "**/.*/**",
                        "**/.*"
                    )
                }

                if (target.logger.isEnabled(pluginLogLevel)) {
                    val builder = StringBuilder()
                    builder.appendln("DotNet $androidBuildTypeName input files:")
                    inputFiles.forEach {
                        builder.append("  ")
                        builder.appendln(it.absolutePath)
                    }
                    target.logger.log(pluginLogLevel, builder.toString())
                }

                execTask.apply {
                    group = dotNetGroup
                    workingDir = dotNetProjectDir
                    executable = msBuildExecutable
                    args = msBuildArgs
                    environment("PATH", path) // Workaround for /Library/Frameworks/Xamarin.Android.framework/Versions/Current/bin/generator: line 6: exec: mono: not found
                    inputs.files(inputFiles)
                    outputs.dirs(outputDirs)
                }
            }

            target.tasks.register(dotNetTaskNames.unzipApk(androidBuildTypeName), Copy::class.java) { copyTask ->
                val manifestFile = dotNetFiles.manifestFile(androidBuildTypeName).get()
                val packageName = parseManifest(manifestFile).`package`
                val apkFile = dotNetFiles.apkFile(androidBuildTypeName, packageName)

                copyTask.apply {
                    group = dotNetGroup
                    from(target.zipTree(apkFile))
                    into(dotNetFiles.unzippedApkDir(androidBuildTypeName))
                    dependsOn(compileDotNetProvider)
                }
            }
        }

        target.afterEvaluate {
            val cleanDotNet = target.tasks.named(dotNetTaskNames.clean)
            target.tasks.named(androidTaskNames.clean).dependsOn(cleanDotNet)

            val androidExtension = target.extensions.getByType(AppExtension::class.java)
            androidExtension.buildTypes.all { buildType ->
                val androidBuildTypeName = buildType.name

                androidExtension.sourceSets.getByName(androidBuildTypeName).apply {
                    java.srcDir(dotNetFiles.javaSrcDir(androidBuildTypeName))
                    resources.srcDir(dotNetFiles.unzippedAssembliesDir(androidBuildTypeName))
                    jniLibs.srcDir(dotNetFiles.unzippedJniLibsDir(androidBuildTypeName))             // Native .so libs
                }

                buildType.proguardFile(dotNetFiles.proguardFile(androidBuildTypeName))

                val unpackDotNetProguardFile = target.tasks.named(dotNetTaskNames.unpackProguardFile(androidBuildTypeName))
                val unzipApkProvider = target.tasks.named(dotNetTaskNames.unzipApk(androidBuildTypeName))

                target.tasks.named(androidTaskNames.mergeProguardFiles(androidBuildTypeName)).dependsOn(unpackDotNetProguardFile)
                target.tasks.named(androidTaskNames.compileJava(androidBuildTypeName)).dependsOn(unzipApkProvider)
                target.tasks.named(androidTaskNames.compileKotlin(androidBuildTypeName)).dependsOn(unzipApkProvider)
            }
        }
    }

    private fun getMsBuildVerbosity(level: LogLevel): String {
        val verbosity = mapOf(
            LogLevel.DEBUG to "diagnostic",
            LogLevel.INFO to "detailed",
            LogLevel.LIFECYCLE to "normal",
            LogLevel.WARN to "minimal",
            LogLevel.QUIET to "quiet",
            LogLevel.ERROR to "quiet"
        )

        return verbosity[level] ?: throw GradleException("No msbuild log level mapping for ${level.name}.")
    }
}
