package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

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

  /**
   * APK-র ভেতর assets/file থেকে ফাইলগুলো বের করে Shizuku-অ্যাক্সেসযোগ্য ডিরেক্টরিতে প্রস্তুত রাখা
   */
  fun extractAssetsFileFolder(context: Context): File {
    val targetLocalDir = context.externalCacheDir?.let { File(it, "temp_file_transfer") }
      ?: File(context.cacheDir, "temp_file_transfer")

    if (!targetLocalDir.exists()) {
      targetLocalDir.mkdirs()
    }

    try {
      copyAssetFolderRecursively(context, "file", targetLocalDir)
      targetLocalDir.setReadable(true, false)
      targetLocalDir.setExecutable(true, false)
    } catch (_: Throwable) {}

    return targetLocalDir
  }

  private fun copyAssetFolderRecursively(context: Context, assetPath: String, targetDir: File) {
    val assetManager = context.assets
    val list = assetManager.list(assetPath) ?: return
    for (child in list) {
      if (child == "README.txt") continue
      val childAssetPath = "$assetPath/$child"
      val childTargetFile = File(targetDir, child)
      val subList = assetManager.list(childAssetPath)
      if (subList.isNullOrEmpty()) {
        assetManager.open(childAssetPath).use { input ->
          FileOutputStream(childTargetFile).use { output ->
            input.copyTo(output)
          }
        }
        childTargetFile.setReadable(true, false)
        childTargetFile.setWritable(true, false)
      } else {
        childTargetFile.mkdirs()
        childTargetFile.setReadable(true, false)
        childTargetFile.setExecutable(true, false)
        copyAssetFolderRecursively(context, childAssetPath, childTargetFile)
      }
    }
  }

  fun getSourceDirectory(context: Context): File {
    // 1. Storage-এ থাকা বাহ্যিক file ফোল্ডার (MT Manager বা ইউজার তৈরি ফোল্ডার)
    val candidates = listOf(
      File("/storage/emulated/0/Download/file"),
      File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "file"),
      File("/storage/emulated/0/file"),
      File(Environment.getExternalStorageDirectory(), "file"),
      File(context.getExternalFilesDir(null), "file"),
      File("/storage/emulated/0/Android/data/${context.packageName}/files/file"),
      File("/storage/emulated/0/Android/data/${context.packageName}/file")
    )

    for (dir in candidates) {
      if (dir.exists() && dir.isDirectory) {
        val items = dir.listFiles()?.filter { it.name != "README.txt" }
        if (!items.isNullOrEmpty()) {
          return dir
        }
      }
    }

    // 2. APK-র ভেতরের file ফোল্ডার (Assets)
    return extractAssetsFileFolder(context)
  }

  fun getSourceFiles(context: Context): List<File> {
    val dir = getSourceDirectory(context)
    return dir.listFiles()?.filter { it.name != "README.txt" } ?: emptyList()
  }

  private fun executeShizukuCmd(cmd: String): Boolean {
    return try {
      val method = Shizuku::class.java.getDeclaredMethod(
        "newProcess",
        Array<String>::class.java,
        Array<String>::class.java,
        String::class.java
      )
      method.isAccessible = true
      val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as java.lang.Process
      process.waitFor() == 0
    } catch (_: Throwable) {
      false
    }
  }

  /**
   * ফাইল বা ফোল্ডার রিকার্সিভলি Shizuku দিয়ে টার্গেটে কপি করা (আগে থেকে থাকলে রিপ্লেস হবে)
   */
  private fun transferItemRecursively(item: File, targetBasePath: String, relativePath: String = ""): Pair<Int, Boolean> {
    val currentRelPath = if (relativePath.isEmpty()) item.name else "$relativePath/${item.name}"
    val targetItemPath = "$targetBasePath/$currentRelPath"

    return if (item.isDirectory) {
      // টার্গেটে ফোল্ডার তৈরি
      executeShizukuCmd("mkdir -p \"$targetItemPath\" && chmod 777 \"$targetItemPath\"")
      val children: List<File> = item.listFiles()?.filter { it.name != "README.txt" } ?: emptyList()
      var totalCopied = 0
      var anySuccess = false
      for (child in children) {
        val (count, ok) = transferItemRecursively(child, targetBasePath, currentRelPath)
        totalCopied += count
        if (ok) anySuccess = true
      }
      Pair(totalCopied, anySuccess || children.isEmpty())
    } else {
      // ফাইল কপি: প্যারেন্ট ফোল্ডার নিশ্চিত করা, আগের ফাইল থাকলে রিমুভ করে নতুন রাইট (রিপ্লেস)
      val parentPath = File(targetItemPath).parent ?: targetBasePath
      val cmd = "mkdir -p \"$parentPath\" && rm -f \"$targetItemPath\" && cat > \"$targetItemPath\" && chmod 777 \"$targetItemPath\""

      val success = try {
        val method = Shizuku::class.java.getDeclaredMethod(
          "newProcess",
          Array<String>::class.java,
          Array<String>::class.java,
          String::class.java
        )
        method.isAccessible = true
        val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as java.lang.Process

        item.inputStream().use { input ->
          process.outputStream.use { output ->
            input.copyTo(output)
            output.flush()
          }
        }
        process.waitFor() == 0
      } catch (_: Throwable) {
        false
      }
      Pair(if (success) 1 else 0, success)
    }
  }

  fun copyFilesToTarget(context: Context): Pair<Boolean, String> {
    if (!isShizukuRunning()) {
      return Pair(false, "Shizuku is not running")
    }
    if (!hasPermission()) {
      return Pair(false, "Shizuku permission not granted")
    }

    val sourceDir = getSourceDirectory(context)
    val items = sourceDir.listFiles()?.filter { it.name != "README.txt" }
    if (items.isNullOrEmpty()) {
      return Pair(false, "No files or folders found in 'file' folder")
    }

    val targetPath = TARGET_PACKAGE_PATH

    return try {
      // 1. টার্গেট ডিরেক্টরি প্রস্তুত করা
      executeShizukuCmd("mkdir -p \"$targetPath\" && chmod 777 \"$targetPath\"")

      // 2. প্রতিটি ফাইল এবং ফোল্ডার রিকার্সিভলি কপি ও রিপ্লেস করা
      var totalFilesCopied = 0
      for (item in items) {
        val (count, _) = transferItemRecursively(item, targetPath)
        totalFilesCopied += count
      }

      // 3. নিশ্চিতকরণের জন্য ফলব্যাক শেল cp -rf রান করা (সব ফাইল ও ফোল্ডার রিপ্লেস হবে)
      val sourcePath = sourceDir.absolutePath
      val fallbackCmd = "cp -rf \"$sourcePath\"/* \"$targetPath/\" && chmod -R 777 \"$targetPath\""
      executeShizukuCmd(fallbackCmd)

      executeShizukuCmd("chmod -R 777 \"$targetPath\"")

      Pair(true, "Files & folders transferred to com.arafat.com")
    } catch (e: Throwable) {
      Pair(false, "Error: ${e.message}")
    }
  }

  fun deleteFilesFromTarget(context: Context): Pair<Boolean, String> {
    if (!isShizukuRunning()) {
      return Pair(false, "Shizuku is not running")
    }
    if (!hasPermission()) {
      return Pair(false, "Shizuku permission not granted")
    }

    val sourceDir = getSourceDirectory(context)
    val items = sourceDir.listFiles()?.filter { it.name != "README.txt" } ?: emptyList()
    val targetPath = TARGET_PACKAGE_PATH

    return try {
      if (items.isNotEmpty()) {
        for (item in items) {
          val itemPath = "$targetPath/${item.name}"
          executeShizukuCmd("rm -rf \"$itemPath\"")
        }
      } else {
        executeShizukuCmd("rm -rf \"$targetPath\"/*")
      }

      Pair(true, "Files & folders deleted successfully")
    } catch (e: Throwable) {
      Pair(false, "Error: ${e.message}")
    }
  }
}
