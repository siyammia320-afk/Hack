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
    // context.filesDir (/data/user/0/...) Shizuku ADB শেল এক্সেস করতে পারে না (Permission denied)।
    // তাই externalCacheDir অথবা /sdcard/Android/data/... ব্যবহার করব যা ADB শেল সহজে পড়তে পারে।
    val targetLocalDir = context.externalCacheDir?.let { File(it, "temp_file_transfer") }
      ?: File(context.cacheDir, "temp_file_transfer")

    if (!targetLocalDir.exists()) {
      targetLocalDir.mkdirs()
    }

    try {
      val assetManager = context.assets
      val assetFiles = assetManager.list("file") ?: emptyArray()
      for (fileName in assetFiles) {
        if (fileName == "README.txt") continue
        val outFile = File(targetLocalDir, fileName)
        assetManager.open("file/$fileName").use { input ->
          FileOutputStream(outFile).use { output ->
            input.copyTo(output)
          }
        }
        outFile.setReadable(true, false)
        outFile.setWritable(true, false)
      }
      targetLocalDir.setReadable(true, false)
      targetLocalDir.setExecutable(true, false)
    } catch (_: Throwable) {}

    return targetLocalDir
  }

  fun getSourceDirectory(context: Context): File {
    // 1. APK-র ভেতরের file ফোল্ডার (Assets)
    val internalDir = extractAssetsFileFolder(context)
    val internalFiles = internalDir.listFiles()?.filter { it.isFile && it.name != "README.txt" }
    if (!internalFiles.isNullOrEmpty()) {
      return internalDir
    }

    // 2. MT Manager বা Storage-এ থাকা বাহ্যিক file ফোল্ডার
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
        val files = dir.listFiles()?.filter { it.isFile && it.name != "README.txt" }
        if (!files.isNullOrEmpty()) {
          return dir
        }
      }
    }

    return internalDir
  }

  fun getSourceFiles(context: Context): List<File> {
    val dir = getSourceDirectory(context)
    return dir.listFiles()?.filter { it.isFile && it.name != "README.txt" } ?: emptyList()
  }

  /**
   * একটি নির্দিষ্ট ফাইল Shizuku শেল ব্যবহার করে টার্গেট ফোল্ডারে নিখুঁতভাবে কপি করা
   */
  private fun transferFileWithShizuku(sourceFile: File, targetDir: String): Boolean {
    val targetFilePath = "$targetDir/${sourceFile.name}"
    val cmd = "cat > \"$targetFilePath\" && chmod 777 \"$targetFilePath\""

    return try {
      val method = Shizuku::class.java.getDeclaredMethod(
        "newProcess",
        Array<String>::class.java,
        Array<String>::class.java,
        String::class.java
      )
      method.isAccessible = true
      val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as java.lang.Process

      // Java Stream দিয়ে সরাসরি Shizuku প্রসেসের ইনপুট স্ট্রিমে বাইট রাইট করা
      // এতে অ্যান্ড্রয়েড পারমিশনের কোনো 'cp: bad /data/user/0... Permission denied' সমস্যা হবে না
      sourceFile.inputStream().use { input ->
        process.outputStream.use { output ->
          input.copyTo(output)
          output.flush()
        }
      }

      val exitCode = process.waitFor()
      exitCode == 0
    } catch (_: Throwable) {
      false
    }
  }

  fun copyFilesToTarget(context: Context): Pair<Boolean, String> {
    if (!isShizukuRunning()) {
      return Pair(false, "Shizuku চালু নেই")
    }
    if (!hasPermission()) {
      return Pair(false, "Shizuku পারমিশন নেই")
    }

    val sourceDir = getSourceDirectory(context)
    val files = sourceDir.listFiles()?.filter { it.isFile && it.name != "README.txt" }
    if (files.isNullOrEmpty()) {
      return Pair(false, "APK বা স্টোরেজের 'file' ফোল্ডারে কোনো ফাইল পাওয়া যায়নি")
    }

    val targetPath = TARGET_PACKAGE_PATH

    return try {
      val method = Shizuku::class.java.getDeclaredMethod(
        "newProcess",
        Array<String>::class.java,
        Array<String>::class.java,
        String::class.java
      )
      method.isAccessible = true

      // ১. টার্গেট ফোল্ডার তৈরি ও প্রিভিলেজ সেট করা
      val mkdirProcess = method.invoke(
        null,
        arrayOf("sh", "-c", "mkdir -p \"$targetPath\" && chmod 777 \"$targetPath\""),
        null,
        null
      ) as java.lang.Process
      mkdirProcess.waitFor()

      // ২. সরাসরি বাইট স্ট্রিমিং মেথডে ফাইল কপি করা (কোনো পারমিশন ব্লক হবে না)
      var copiedCount = 0
      for (file in files) {
        val success = transferFileWithShizuku(file, targetPath)
        if (success) {
          copiedCount++
        }
      }

      // ৩. ওভারঅল chmod দেওয়া
      val chmodProcess = method.invoke(
        null,
        arrayOf("sh", "-c", "chmod -R 777 \"$targetPath\""),
        null,
        null
      ) as java.lang.Process
      chmodProcess.waitFor()

      if (copiedCount > 0) {
        Pair(true, "$copiedCount টি ফাইল সফলভাবে com.arafat.com ফোল্ডারে ডাউনলোড হয়েছে")
      } else {
        // ফলব্যাক হিসেবে সাধারণ cp ট্রাই করা
        val sourcePath = sourceDir.absolutePath
        val fallbackCmd = "cp -rf \"$sourcePath\"/* \"$targetPath/\" && chmod -R 777 \"$targetPath\""
        val fallbackProcess = method.invoke(null, arrayOf("sh", "-c", fallbackCmd), null, null) as java.lang.Process
        val err = fallbackProcess.errorStream.bufferedReader().use { it.readText() }
        val code = fallbackProcess.waitFor()
        if (code == 0) {
          Pair(true, "${files.size} টি ফাইল সফলভাবে ডাউনলোড হয়েছে")
        } else {
          Pair(false, "ব্যর্থ হয়েছে: $err")
        }
      }
    } catch (e: Throwable) {
      Pair(false, "ত্রুটি: ${e.message}")
    }
  }
}
