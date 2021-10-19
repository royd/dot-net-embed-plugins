package com.roy.plugins.xml

import org.gradle.api.GradleException
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory

class AndroidApiInfo(val level: Int, val version: String) {
  companion object {
    private const val fileName = "AndroidApiInfo.xml"

    fun find(searchDir: File, compileSdkVersion: Int): AndroidApiInfo {
      if (!searchDir.exists()) {
        throw GradleException("Directory does not exist: ${searchDir.absolutePath}")
      }

      val versions = mutableListOf<Int>()

      val dirs = searchDir.listFiles { _, name -> name.startsWith("v")} ?: arrayOf<File>()
      dirs.forEach { dir ->
        val file = dir.resolve(fileName)
        if (file.exists()) {
          val info = parse(file)
          if (info.level == compileSdkVersion) {
            return info
          }

          versions.add(info.level)
        }
      }

      throw GradleException("Did not find Xamarin version for compileSdkVersion $compileSdkVersion in $searchDir. Found $versions")
    }

    fun parse(xml: File): AndroidApiInfo {
      val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)
      val xPath = XPathFactory.newInstance().newXPath()
      val level = xPath.evaluate("/AndroidApiInfo/Level", document)
      val version = xPath.evaluate("/AndroidApiInfo/Version", document)

      if (level.isEmpty()) {
        throw GradleException("Failed to parse API level in file ${xml.absolutePath}")
      }

      if (version.isEmpty()) {
        throw GradleException("Failed to parse API version in file ${xml.absolutePath}")
      }

      return AndroidApiInfo(level.toInt(), version)
    }
  }
}