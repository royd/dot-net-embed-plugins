package com.roy.plugins

import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.*
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuildTest {
    companion object {
        private val projectDir: File = Paths.get("test/android/app").toFile()
        private val dotNetProjectFile = File("test/dot-net/DotNetLibrary/DotNetLibrary.csproj")
        val dotNetFiles = DotNetLibraryPlugin.DotNetFiles(DefaultProvider { dotNetProjectFile.parentFile })
        val androidFiles = DotNetLibraryPlugin.AndroidFiles(projectDir)
        val androidTaskNames = DotNetLibraryPlugin.AndroidTaskNames()
        val dotNetTaskNames = DotNetLibraryPlugin.DotNetTaskNames()

        val testConfigs = listOf(
            "debug",
            "release",
        )
        val completedOutcomes = setOf(
            TaskOutcome.UP_TO_DATE,
            TaskOutcome.SUCCESS,
        )

        fun clean() {
            dotNetFiles.binDir.get().deleteRecursively()
            dotNetFiles.objDir.get().deleteRecursively()
        }

        fun cleanConfig(buildConfig: String) {
            dotNetFiles.binConfigDir(buildConfig).get().deleteRecursively()
            dotNetFiles.objConfigDir(buildConfig).get().deleteRecursively()
        }
    }

    @Test fun cleanRemovesDirs() {
        val binDir = dotNetFiles.binDir.get()
        if (!binDir.exists()) {
            binDir.mkdir()
        }

        val objDir = dotNetFiles.objDir.get()
        if (!objDir.exists()) {
            objDir.mkdir()
        }

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(":app:${androidTaskNames.clean}")
            .forwardOutput()
            .build()

        val clean = result.task(":app:${dotNetTaskNames.clean}")
        assertNotNull(clean)
        assertEquals(TaskOutcome.SUCCESS, clean.outcome)

        assertFalse(binDir.exists())
        assertFalse(objDir.exists())
    }

    @Test fun compileCreatesApk() {
        testConfigs.forEach { testConfig ->
            cleanConfig(testConfig)

            val taskName = ":app:${dotNetTaskNames.compile(testConfig)}"

            val build = {
                GradleRunner.create()
                    .withProjectDir(projectDir)
                    .withArguments(taskName)
                    .forwardOutput()
                    .build()
            }

            val result = build()

            assertEquals(TaskOutcome.SUCCESS, result.task(taskName)?.outcome)

            val apk = dotNetFiles.apkFile(testConfig, "com.roy.dotnetlibrary").get()
            assertTrue(apk.exists(), "Apk does not exist at: ${apk.absolutePath}")

            val resultTwo = build()

            assertEquals(TaskOutcome.UP_TO_DATE, resultTwo.task(taskName)?.outcome)
        }
    }

    @Test fun compileCreatesAndroidCallableWrappers() {
        testConfigs.forEach { testConfig ->
            val taskName = ":app:${dotNetTaskNames.compile(testConfig)}"

            val build = {
                GradleRunner.create()
                    .withProjectDir(projectDir)
                    .withArguments(taskName)
                    .forwardOutput()
                    .build()
            }

            val outcome = build().task(taskName)?.outcome

            assertTrue(completedOutcomes.contains(outcome), "Task considered incomplete with outcome: $outcome")

            val srcDir = dotNetFiles.javaSrcDir(testConfig).get()

            val files = listOf(
                srcDir,
                srcDir.resolve("com/roy/dotnetlibrary/Logger.java"),
                srcDir.resolve("mono/MonoRuntimeProvider.java"),
            )

            files.forEach { file ->
                assertTrue(file.exists(), "File does not exist: ${file.absolutePath}")
            }
        }
    }

    @Test fun unzippedDotNetApkContainsFiles() {
        testConfigs.forEach { testConfig ->
            val taskName = ":app:${dotNetTaskNames.unzipApk(testConfig)}"

            val build = {
                GradleRunner.create()
                    .withProjectDir(projectDir)
                    .withArguments(taskName)
                    .forwardOutput()
                    .build()
            }

            val outcome = build().task(taskName)?.outcome

            assertTrue(completedOutcomes.contains(outcome), "Task considered incomplete with outcome: $outcome")

            val assembliesDir = androidFiles.unzippedAssembliesDir(testConfig)
            val jniLibsDir = androidFiles.unzippedJniLibsDir(testConfig)

            val files = listOf(
                assembliesDir,
                assembliesDir.resolve("DotNetLibrary.dll"),
                assembliesDir.resolve("Mono.Android.dll"),
                assembliesDir.resolve("Mono.Android.Export.dll"),
                assembliesDir.resolve("Java.Interop.dll"),
                jniLibsDir.resolve("arm64-v8a/libmonodroid.so"),
                jniLibsDir.resolve("arm64-v8a/libxa-internal-api.so")
            )

            files.forEach { file ->
                assertTrue(file.exists(), "File does not exist: ${file.absolutePath}")
            }
        }
    }

    @Test fun appApkContainsFiles() {
        testConfigs.forEach { testConfig ->
            val taskName = ":app:assemble${testConfig.capitalize()}"
            val unzippedApkDirName = "app-$testConfig"

            val apkDir = projectDir.resolve("build/outputs/apk/$testConfig")
            val unzippedApkDir = apkDir.resolve(unzippedApkDirName)

            if (unzippedApkDir.exists()) {
                unzippedApkDir.deleteRecursively();
            }

            val build = {
                GradleRunner.create()
                    .withProjectDir(projectDir)
                    .withArguments(taskName)
                    .forwardOutput()
                    .build()
            }

            val outcome = build().task(taskName)?.outcome

            assertTrue(
                completedOutcomes.contains(outcome),
                "Task considered incomplete with outcome: $outcome"
            )

            val apks = apkDir.listFiles { file -> file.extension == "apk" }.toList()
            assertTrue(apks.isNotEmpty(), "APK not created in dir: ${apkDir.absolutePath}")
            assertEquals(apks.size, 1, "More than one APK in dir: ${apkDir.absolutePath}")

            ProcessBuilder("unzip", apks.single().name, "-d", unzippedApkDirName).apply {
                directory(apkDir)
                inheritIO()
                start().waitFor()
            }

            val paths = listOf(
                "assemblies",
                "assemblies/DotNetLibrary.dll",
                "assemblies/Mono.Android.dll",
                "assemblies/Mono.Android.Export.dll",
                "assemblies/Java.Interop.dll",
                "lib/arm64-v8a/libmonodroid.so",
                "lib/arm64-v8a/libmonosgen-2.0.so"
            )

            paths.forEach { path ->
                val file = unzippedApkDir.resolve(path)
                assertTrue(file.exists(), "File does not exist: ${file.absolutePath}")
            }

            val requiredClasses = mutableSetOf(
                "Lmono/MonoPackageManager_Resources;",
                "Lmono/MonoRuntimeProvider;",
                "Lcom/roy/dotnetlibrary/Logger;",
            )

            val outputFile = apkDir.resolve("classes.txt")
            if (outputFile.exists()) {
                outputFile.delete()
            }

            val outputWriter = FileWriter(outputFile, true)
            val classRegex = """(?<=Class descriptor  : ')[a-zA-Z0-9].+(?=')""".toRegex()

            unzippedApkDir.listFiles { it -> it.extension == "dex"}.forEach { dexFile ->
                val sdk = System.getenv("ANDROID_HOME")
                val process = ProcessBuilder("$sdk/build-tools/31.0.0/dexdump", dexFile.absolutePath).apply {
                    directory(projectDir.parentFile)
                }.start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                reader.lines().forEach { line ->
                    val match = classRegex.find(line)
                    if (match != null) {
                        requiredClasses.remove(match.value)
                        outputWriter.write(match.value)
                        outputWriter.write("\n")
                    }
                }
                process.waitFor()
                reader.close()
            }

            outputWriter.flush()
            outputWriter.close()

            val message = StringBuilder("APK is missing classes:\n")
            requiredClasses.forEach { className ->
                message.append("  $className\n")
            }

            assertTrue(requiredClasses.isEmpty(), message.toString())
        }
    }

    @Test fun unpacksDotNetProguardFile() {
        testConfigs.forEach { testConfig ->
            val dir = androidFiles.buildTasksDir(testConfig)
            if (dir.exists()) {
                dir.deleteRecursively()
            }

            val dllFile = File(DotNetLibraryPlugin.buildTasksDllFile)
            assertTrue(dllFile.exists(), "File does not exist: ${dllFile.absolutePath}")

            val taskName = ":app:${dotNetTaskNames.unpackProguardFile(testConfig)}"

            val build = {
                GradleRunner.create()
                    .withProjectDir(projectDir)
                    .withArguments(taskName)
                    .forwardOutput()
                    .build()
            }

            val result = build()

            assertEquals(TaskOutcome.SUCCESS, result.task(taskName)?.outcome)

            val proguardFile = androidFiles.proguardFile(testConfig)
            assertTrue(proguardFile.exists(), "File does not exist: ${proguardFile.absolutePath}")

            val resultTwo = build()

            assertEquals(TaskOutcome.UP_TO_DATE, resultTwo.task(taskName)?.outcome)
        }
    }
}
