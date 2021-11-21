# dot-net-embed-plugins
Gradle plugins for embedding C# in an Android app.

## Prerequisites
1. Android Studio
2. Visual Studio for Mac

Currently working for:
```
=== Visual Studio Community 2019 for Mac ===

Version 8.10.6 (build 10)

=== Mono Framework MDK ===

Runtime:
	Mono 6.12.0.140 (2020-02/51d876a041e) (64-bit)
	Package version: 612000140
  
=== Xamarin.Android ===

Version: 11.3.0.4
Commit: xamarin-android/d16-10/ae14caf
Android SDK:
	Supported Android versions:
		4.4 (API level 19)
		5.0 (API level 21)
		5.1 (API level 22)
		6.0 (API level 23)
		8.0 (API level 26)
		8.1 (API level 27)

SDK Tools Version: 26.1.1
SDK Platform Tools Version: 31.0.3
SDK Build Tools Version: 31.0.0

Build Information: 
Mono: b4a3858
Java.Interop: xamarin/java.interop/d16-10@f39db25
ProGuard: Guardsquare/proguard/v7.0.1@912d149
SQLite: xamarin/sqlite/3.35.4@85460d3
Xamarin.Android Tools: xamarin/xamarin-android-tools/d16-10@c5732a0

=== Operating System ===

Mac OS X 10.16.0
Darwin 20.6.0 Darwin Kernel Version 20.6.0
    Mon Aug 30 06:12:21 PDT 2021
    root:xnu-7195.141.6~3/RELEASE_X86_64 x86_64
```

## Getting Started
1. Clone this repo.
2. Open the [test Android project](https://github.com/royd/dot-net-embed-plugins/tree/main/test/android) in Android Studio.
3. Run the app.

## Building Your Own
1. Clone this repo.
2. Create an Android app in a sibling directory.
3. Create a Visual Studio Android library project in a sibling directory.
4. Configure the Visual Studio project. See [test library settings](https://github.com/royd/dot-net-embed-plugins/blob/main/test/dot-net/DotNetLibrary/DotNetLibrary.csproj).
5. Include the plugins project in the Android project `settings.gradle`. See [test app settings](https://github.com/royd/dot-net-embed-plugins/blob/main/test/android/settings.gradle).
```
includeBuild '../../'
```
4. Apply the app plugin in the app `build.gradle` and the library plugin in the `build.gradle` of the module that will use C# (perhaps the app `build.gradle` in a small app). See [test app configuration](https://github.com/royd/dot-net-embed-plugins/blob/main/test/android/app/build.gradle).

```
apply plugin: 'com.roy.dot-net-embed-app'
apply plugin: 'com.roy.dot-net-embed-library'
```

5. Configure the `dotNet` extension in the `build.gradle` where the library plugin is applied. See [test app configuration](https://github.com/royd/dot-net-embed-plugins/blob/main/test/android/app/build.gradle).

```
dotNet {
    pluginLogLevel LogLevel.LIFECYCLE
    projectFile "../../dot-net/DotNetLibrary/DotNetLibrary.csproj"
    solutionFile "../../dot-net/DotNet.sln"
    buildTypes {
        debug {
            msBuildConfig "Debug"
        }
        release {
            msBuildConfig "Release"
        }
    }
}
```
6. Add the Mono Runtime content provider to `AndroidManifest.xml` in the Android project `application` element to initialize Mono on startup. Get this from `obj/debug/android/AndroidManifest.xml` after building the C# project.
```
<provider
    android:name="mono.MonoRuntimeProvider"
    android:authorities="com.roy.dotnetlibrary.mono.MonoRuntimeProvider.__mono_init__"
    android:exported="false"
    android:initOrder="1999999999" />
```
8. Create C# classes extending `Java.Lang.Object`, applying the `Export` and `Register` attributes. See [an example](https://github.com/royd/dot-net-embed-plugins/blob/main/test/dot-net/DotNetLibrary/Logger.cs).
9. Run Gradle task `:app:compileDebugDotNet` to create the Android Callable Wrappers that will be visible in Android Studio.
10. Utilize the Android Callable Wrappers in Java or Kotlin.
11. Run the app!
