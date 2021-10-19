package com.roy.plugins

import com.roy.plugins.xml.AndroidApiInfo
import org.gradle.api.GradleException
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AndroidApiInfoTest {
    @Test fun parsesInfo() {
        val file = File("src/test/data/AndroidApiInfo.xml")
        val info = AndroidApiInfo.parse(file)
        assertEquals(info.level, 30, "Api level does not match")
        assertEquals(info.version, "v11.0", "Api version does not match")
    }

    @Test fun findsInfo() {
        val dir = File("/Library/Frameworks/Xamarin.Android.framework/Versions/Current/lib/xbuild-frameworks/MonoAndroid")
        val apiLevel = 30
        val info = AndroidApiInfo.find(dir, apiLevel)
        assertEquals(info.level, apiLevel, "API $apiLevel not found")
        assertEquals(info.version, "v11.0", "API version does not match")
    }

    @Test fun notFoundThrows() {
        val dir = File("/Library/Frameworks/Xamarin.Android.framework/Versions/Current/lib/xbuild-frameworks/MonoAndroid")
        val apiLevel = 0
        assertFailsWith<GradleException> {
            AndroidApiInfo.find(dir, apiLevel)
        }
    }

    @Test fun invalidXmlThrows() {
        val file = File("src/test/data/InvalidAndroidApiInfo.xml")
        assertFailsWith<GradleException> {
            AndroidApiInfo.parse(file)
        }
    }
}
