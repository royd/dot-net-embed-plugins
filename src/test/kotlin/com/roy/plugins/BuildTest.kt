package com.roy.plugins

import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuildTest {
    companion object {
        private val projectDir: File = Paths.get("test/android").toFile()
        private val dotNetProjectFile = File("test/dot-net/DotNetLibrary/DotNetLibrary.csproj")
        val dotNetFiles = DotNetLibraryPlugin.DotNetFiles(DefaultProvider { dotNetProjectFile.parentFile })
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
                srcDir.resolve("com/roy/Logger.java"),
                srcDir.resolve("mono/MonoRuntimeProvider.java"),
            )

            files.forEach { file ->
                assertTrue(file.exists(), "File does not exist: ${file.absolutePath}")
            }
        }
    }

    @Test fun unzippedApkContainsFiles() {
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

            val assembliesDir = dotNetFiles.unzippedAssembliesDir(testConfig).get()
            val jniLibsDir = dotNetFiles.unzippedJniLibsDir(testConfig).get()

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

    @Test fun unpacksDotNetProguardFile() {
        testConfigs.forEach { testConfig ->
            val dir = dotNetFiles.buildTasksDir(testConfig).get()
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

            val proguardFile = dotNetFiles.proguardFile(testConfig).get()
            assertTrue(proguardFile.exists(), "File does not exist: ${proguardFile.absolutePath}")

            val resultTwo = build()

            assertEquals(TaskOutcome.UP_TO_DATE, resultTwo.task(taskName)?.outcome)
        }
    }
}
