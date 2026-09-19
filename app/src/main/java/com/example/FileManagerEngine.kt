package com.example

import android.content.Context
import android.os.Environment
import rikka.shizuku.Shizuku
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileItem(
  val name: String,
  val path: String,
  val isDirectory: Boolean,
  val size: Long,
  val lastModified: Long,
  val isRestrictedOrShizuku: Boolean = false
) {
  val formattedSize: String
    get() {
      if (isDirectory) return "Folder"
      if (size < 1024) return "$size B"
      val kb = size / 1024.0
      if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
      val mb = kb / 1024.0
      if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
      val gb = mb / 1024.0
      return String.format(Locale.US, "%.2f GB", gb)
    }

  val formattedDate: String
    get() {
      if (lastModified <= 0) return ""
      val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
      return sdf.format(Date(lastModified))
    }

  val extension: String
    get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()
}

enum class ClipboardAction {
  COPY, CUT
}

data class ClipboardItem(
  val action: ClipboardAction,
  val item: FileItem
)

object FileManagerEngine {
  val ROOT_STORAGE_PATH: String = Environment.getExternalStorageDirectory().absolutePath
  const val ANDROID_DATA_PATH = "/storage/emulated/0/Android/data"
  const val ANDROID_OBB_PATH = "/storage/emulated/0/Android/obb"

  fun isRestrictedPath(path: String): Boolean {
    return path.startsWith(ANDROID_DATA_PATH) || path.startsWith(ANDROID_OBB_PATH) || path.startsWith("/data")
  }

  fun execShizuku(cmd: String): Pair<Int, String> {
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
      Pair(exitCode, if (exitCode == 0) output.trim() else error.trim())
    } catch (e: Throwable) {
      Pair(-1, e.message ?: "Shizuku execution failed")
    }
  }

  fun listFiles(dirPath: String, useShizukuAlwaysForRestricted: Boolean = true): List<FileItem> {
    val dir = File(dirPath)
    val isRestricted = isRestrictedPath(dirPath)

    // 1. Try standard Java File list if not strictly restricted or if accessible
    if (!isRestricted || !useShizukuAlwaysForRestricted) {
      try {
        val files = dir.listFiles()
        if (files != null) {
          return files.map { file ->
            FileItem(
              name = file.name,
              path = file.absolutePath,
              isDirectory = file.isDirectory,
              size = if (file.isDirectory) 0L else file.length(),
              lastModified = file.lastModified(),
              isRestrictedOrShizuku = false
            )
          }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }
      } catch (_: Throwable) {}
    }

    // 2. If standard list returned null / is restricted, fallback to Shizuku shell
    if (ShizukuManager.hasPermission()) {
      val (code, output) = execShizuku("cd \"$dirPath\" 2>/dev/null && ls -1ap")
      if (code == 0 && output.isNotEmpty()) {
        val lines = output.lines().filter { it.isNotBlank() && it != "./" && it != "../" && it != "." && it != ".." }
        val items = lines.map { line ->
          val isDir = line.endsWith("/")
          val rawName = if (isDir) line.dropLast(1) else line
          val fullPath = if (dirPath.endsWith("/")) "$dirPath$rawName" else "$dirPath/$rawName"
          
          FileItem(
            name = rawName,
            path = fullPath,
            isDirectory = isDir,
            size = 0L,
            lastModified = 0L,
            isRestrictedOrShizuku = true
          )
        }
        return items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
      }
    }

    return emptyList()
  }

  fun createFolder(parentPath: String, folderName: String): Pair<Boolean, String> {
    val cleanName = folderName.trim().replace("/", "")
    if (cleanName.isEmpty()) return Pair(false, "Folder name cannot be empty")
    val targetDir = File(parentPath, cleanName)

    // Try normal File API
    if (!isRestrictedPath(parentPath)) {
      try {
        if (targetDir.mkdirs()) {
          return Pair(true, "Folder created")
        }
      } catch (_: Throwable) {}
    }

    // Shizuku fallback
    if (ShizukuManager.hasPermission()) {
      val cmd = "mkdir -p \"${targetDir.absolutePath}\" && chmod 777 \"${targetDir.absolutePath}\""
      val (code, out) = execShizuku(cmd)
      return if (code == 0) {
        Pair(true, "Folder created (Shizuku)")
      } else {
        Pair(false, "Failed to create folder: $out")
      }
    }

    return Pair(false, "Permission denied. Please grant Shizuku permission.")
  }

  fun createFile(parentPath: String, fileName: String, content: String = ""): Pair<Boolean, String> {
    val cleanName = fileName.trim().replace("/", "")
    if (cleanName.isEmpty()) return Pair(false, "File name cannot be empty")
    val targetFile = File(parentPath, cleanName)

    // Try normal File API
    if (!isRestrictedPath(parentPath)) {
      try {
        if (targetFile.createNewFile()) {
          if (content.isNotEmpty()) {
            targetFile.writeText(content)
          }
          return Pair(true, "File created")
        }
      } catch (_: Throwable) {}
    }

    // Shizuku fallback
    if (ShizukuManager.hasPermission()) {
      val parentCmd = "mkdir -p \"$parentPath\""
      execShizuku(parentCmd)
      
      return try {
        val method = Shizuku::class.java.getDeclaredMethod(
          "newProcess",
          Array<String>::class.java,
          Array<String>::class.java,
          String::class.java
        )
        method.isAccessible = true
        val cmd = "cat > \"${targetFile.absolutePath}\" && chmod 777 \"${targetFile.absolutePath}\""
        val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as java.lang.Process
        process.outputStream.bufferedWriter().use { it.write(content) }
        val code = process.waitFor()
        if (code == 0) {
          Pair(true, "File created (Shizuku)")
        } else {
          Pair(false, "Failed to create file")
        }
      } catch (e: Throwable) {
        Pair(false, "Error: ${e.message}")
      }
    }

    return Pair(false, "Permission denied. Please grant Shizuku permission.")
  }

  fun deleteItem(item: FileItem): Pair<Boolean, String> {
    val target = File(item.path)

    // Try normal File API
    if (!isRestrictedPath(item.path)) {
      try {
        if (target.deleteRecursively()) {
          return Pair(true, "${item.name} deleted")
        }
      } catch (_: Throwable) {}
    }

    // Shizuku fallback
    if (ShizukuManager.hasPermission()) {
      val cmd = "rm -rf \"${item.path}\""
      val (code, out) = execShizuku(cmd)
      return if (code == 0) {
        Pair(true, "${item.name} deleted")
      } else {
        Pair(false, "Delete failed: $out")
      }
    }

    return Pair(false, "Permission denied. Please grant Shizuku permission.")
  }

  fun renameItem(item: FileItem, newName: String): Pair<Boolean, String> {
    val cleanName = newName.trim().replace("/", "")
    if (cleanName.isEmpty() || cleanName == item.name) return Pair(false, "Invalid new name")
    val parent = File(item.path).parent ?: return Pair(false, "Cannot determine parent folder")
    val destination = File(parent, cleanName)

    if (!isRestrictedPath(item.path)) {
      try {
        if (File(item.path).renameTo(destination)) {
          return Pair(true, "Renamed to $cleanName")
        }
      } catch (_: Throwable) {}
    }

    if (ShizukuManager.hasPermission()) {
      val cmd = "mv \"${item.path}\" \"${destination.absolutePath}\""
      val (code, out) = execShizuku(cmd)
      return if (code == 0) {
        Pair(true, "Renamed to $cleanName")
      } else {
        Pair(false, "Rename failed: $out")
      }
    }

    return Pair(false, "Permission denied")
  }

  fun pasteItem(clipboard: ClipboardItem, targetDirPath: String): Pair<Boolean, String> {
    val sourcePath = clipboard.item.path
    val itemName = clipboard.item.name
    val destinationPath = "$targetDirPath/$itemName"

    if (sourcePath == destinationPath) {
      return Pair(false, "Source and destination are the same")
    }

    val isMove = clipboard.action == ClipboardAction.CUT

    // Normal File API attempt if not restricted
    if (!isRestrictedPath(sourcePath) && !isRestrictedPath(targetDirPath)) {
      try {
        val src = File(sourcePath)
        val dst = File(destinationPath)
        if (isMove) {
          if (src.renameTo(dst)) return Pair(true, "Moved successfully")
        } else {
          if (src.isDirectory) {
            src.copyRecursively(dst, overwrite = true)
            return Pair(true, "Copied folder successfully")
          } else {
            src.copyTo(dst, overwrite = true)
            return Pair(true, "Copied file successfully")
          }
        }
      } catch (_: Throwable) {}
    }

    // Shizuku fallback
    if (ShizukuManager.hasPermission()) {
      val cmd = if (isMove) {
        "mkdir -p \"$targetDirPath\" && rm -rf \"$destinationPath\" && mv \"$sourcePath\" \"$targetDirPath/\" && chmod -R 777 \"$destinationPath\""
      } else {
        "mkdir -p \"$targetDirPath\" && rm -rf \"$destinationPath\" && cp -rf \"$sourcePath\" \"$targetDirPath/\" && chmod -R 777 \"$destinationPath\""
      }
      val (code, out) = execShizuku(cmd)
      return if (code == 0) {
        Pair(true, if (isMove) "Moved successfully" else "Copied successfully")
      } else {
        Pair(false, "Paste failed: $out")
      }
    }

    return Pair(false, "Permission denied. Shizuku required.")
  }

  fun readFileText(path: String): String {
    if (!isRestrictedPath(path)) {
      try {
        val f = File(path)
        if (f.exists() && f.canRead()) {
          return f.readText()
        }
      } catch (_: Throwable) {}
    }

    if (ShizukuManager.hasPermission()) {
      val (code, out) = execShizuku("cat \"$path\" 2>/dev/null")
      if (code == 0) return out
    }
    return ""
  }

  fun saveFileText(path: String, newContent: String): Pair<Boolean, String> {
    if (!isRestrictedPath(path)) {
      try {
        val f = File(path)
        f.writeText(newContent)
        return Pair(true, "File saved")
      } catch (_: Throwable) {}
    }

    if (ShizukuManager.hasPermission()) {
      return try {
        val method = Shizuku::class.java.getDeclaredMethod(
          "newProcess",
          Array<String>::class.java,
          Array<String>::class.java,
          String::class.java
        )
        method.isAccessible = true
        val cmd = "cat > \"$path\" && chmod 777 \"$path\""
        val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as java.lang.Process
        process.outputStream.bufferedWriter().use { it.write(newContent) }
        val code = process.waitFor()
        if (code == 0) Pair(true, "File saved (Shizuku)") else Pair(false, "Failed to save")
      } catch (e: Throwable) {
        Pair(false, "Error: ${e.message}")
      }
    }
    return Pair(false, "Permission denied")
  }
}
