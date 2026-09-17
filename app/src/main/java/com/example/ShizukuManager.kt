package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import rikka.shizuku.Shizuku
import java.io.File

object ShizukuManager {
  const val SHIZUKU_REQUEST_CODE = 1001
  const val TARGET_PACKAGE_PATH = "/storage/emulated/0/Android/data/com.arafat.com"

  fun isShizukuRunning(): Boolean {
    return try {
      if (Shizuku.isPreV11()) {
        false
      } else {
        Shizuku.pingBinder()
      }
    } catch (_: Throwable) {
      false
    }
  }

  fun hasPermission(): Boolean {
    return try {
      if (isShizukuRunning()) {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
      } else {
        false
      }
    } catch (_: Throwable) {
      false
    }
  }

  fun requestPermission() {
    try {
      if (isShizukuRunning()) {
        Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
      }
    } catch (_: Throwable) {}
  }

  fun getSourceDirectory(context: Context): File {
    val candidates = listOf(
      File(context.getExternalFilesDir(null), "file"),
      File("/storage/emulated/0/Android/data/${context.packageName}/files/file"),
      File("/storage/emulated/0/Android/data/${context.packageName}/file"),
      File(Environment.getExternalStorageDirectory(), "file"),
      File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "file")
    )

    for (dir in candidates) {
      if (dir.exists() && dir.isDirectory) {
        val files = dir.listFiles()
        if (files != null && files.isNotEmpty()) {
          return dir
        }
      }
    }

    val defaultDir = File(context.getExternalFilesDir(null), "file")
    if (!defaultDir.exists()) {
      defaultDir.mkdirs()
    }
    return defaultDir
  }

  fun getSourceFiles(context: Context): List<File> {
    val dir = getSourceDirectory(context)
    return dir.listFiles()?.filter { it.isFile } ?: emptyList()
  }

  fun copyFilesToTarget(context: Context): Pair<Boolean, String> {
    if (!isShizukuRunning()) {
      return Pair(false, "Shizuku চালু নেই")
    }
    if (!hasPermission()) {
      return Pair(false, "Shizuku পারমিশন নেই")
    }

    val sourceDir = getSourceDirectory(context)
    if (!sourceDir.exists()) {
      return Pair(false, "Source 'file' ফোল্ডার পাওয়া যায়নি")
    }

    val files = sourceDir.listFiles()
    if (files.isNullOrEmpty()) {
      return Pair(false, "'file' ফোল্ডারে কোনো ফাইল নেই (${sourceDir.absolutePath})")
    }

    val sourcePath = sourceDir.absolutePath
    val targetPath = TARGET_PACKAGE_PATH

    val cmd = "mkdir -p \"$targetPath\" && cp -rf \"$sourcePath\"/. \"$targetPath/\" && chmod -R 777 \"$targetPath\""

    return try {
      val method = Shizuku::class.java.getDeclaredMethod(
        "newProcess",
        Array<String>::class.java,
        Array<String>::class.java,
        String::class.java
      )
      method.isAccessible = true
      val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as java.lang.Process
      val output = process.inputStream.bufferedReader().use { it.readText() }
      val error = process.errorStream.bufferedReader().use { it.readText() }
      val exitCode = process.waitFor()

      if (exitCode == 0) {
        Pair(true, "${files.size} টি ফাইল সফলভাবে ডাউনলোড ও রিপ্লেস হয়েছে")
      } else {
        Pair(false, "ব্যর্থ হয়েছে (Code: $exitCode): $error $output")
      }
    } catch (e: Throwable) {
      Pair(false, "ত্রুটি: ${e.message}")
    }
  }
}
