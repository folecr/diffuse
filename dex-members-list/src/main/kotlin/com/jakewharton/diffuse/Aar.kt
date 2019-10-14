package com.jakewharton.diffuse

import com.jakewharton.diffuse.AndroidManifest.Companion.toAndroidManifest
import com.jakewharton.diffuse.ArchiveFile.Type.Companion.toAarFileType
import com.jakewharton.diffuse.ArchiveFiles.Companion.toArchiveFiles
import com.jakewharton.diffuse.Jar.Companion.toJar
import com.jakewharton.diffuse.io.Input

class Aar private constructor(
  override val filename: String?,
  val files: ArchiveFiles,
  val manifest: AndroidManifest,
  val classes: Jar,
  val libs: List<Jar>
) : Binary {
  companion object {
    internal val libsJarRegex = Regex("libs/[^/]\\.jar")

    @JvmStatic
    @JvmName("create")
    fun Input.toAar(): Aar {
      toZip().use { zip ->
        val files = zip.toArchiveFiles { it.toAarFileType() }
        val manifest = zip["AndroidManifest.xml"].input().source()
            .use { it.readUtf8().toAndroidManifest() }
        val classes = zip["classes.jar"].input().toJar()
        val libs = zip.entries
            .filter { it.path.matches(libsJarRegex) }
            .map { it.input().toJar() }
        return Aar(name, files, manifest, classes, libs)
      }
    }
  }
}