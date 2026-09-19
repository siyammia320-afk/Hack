package com.example

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File

class MainActivity : ComponentActivity() {

  private var isShizukuRunningState = mutableStateOf(false)
  private var hasShizukuPermissionState = mutableStateOf(false)
  private var hasStoragePermissionState = mutableStateOf(false)

  private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
    updateShizukuStatus()
  }

  private val binderDeadListener = Shizuku.OnBinderDeadListener {
    updateShizukuStatus()
  }

  private val requestPermissionResultListener =
    Shizuku.OnRequestPermissionResultListener { _, grantResult ->
      hasShizukuPermissionState.value = (grantResult == PackageManager.PERMISSION_GRANTED)
      if (hasShizukuPermissionState.value) {
        Toast.makeText(this, "Shizuku permission granted", Toast.LENGTH_SHORT).show()
      } else {
        Toast.makeText(this, "Shizuku permission denied", Toast.LENGTH_SHORT).show()
      }
      updateShizukuStatus()
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
    Shizuku.addBinderDeadListener(binderDeadListener)
    Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)

    updateShizukuStatus()
    checkStoragePermission()

    setContent {
      MyApplicationTheme {
        MTFileManagerScreen(
          isShizukuRunning = isShizukuRunningState.value,
          hasShizukuPermission = hasShizukuPermissionState.value,
          hasStoragePermission = hasStoragePermissionState.value,
          onRequestShizukuPermission = { ShizukuManager.requestPermission() },
          onRequestStoragePermission = { requestStorageManagerPermission() }
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    updateShizukuStatus()
    checkStoragePermission()
  }

  override fun onDestroy() {
    super.onDestroy()
    try {
      Shizuku.removeBinderReceivedListener(binderReceivedListener)
      Shizuku.removeBinderDeadListener(binderDeadListener)
      Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    } catch (_: Throwable) {}
  }

  private fun updateShizukuStatus() {
    isShizukuRunningState.value = ShizukuManager.isShizukuRunning()
    hasShizukuPermissionState.value = ShizukuManager.hasPermission()
  }

  private fun checkStoragePermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      hasStoragePermissionState.value = Environment.isExternalStorageManager()
    } else {
      hasStoragePermissionState.value = checkSelfPermission(
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
      ) == PackageManager.PERMISSION_GRANTED
    }
  }

  private fun requestStorageManagerPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      try {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
          data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
      } catch (_: Exception) {
        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        startActivity(intent)
      }
    } else {
      requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 101)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MTFileManagerScreen(
  isShizukuRunning: Boolean,
  hasShizukuPermission: Boolean,
  hasStoragePermission: Boolean,
  onRequestShizukuPermission: () -> Unit,
  onRequestStoragePermission: () -> Unit
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val scope = rememberCoroutineScope()

  // Primary Theme Colors (Solid, sleek MT dark theme without glowing shaders)
  val darkBg = Color(0xFF0F141A)
  val topBarBg = Color(0xFF161E27)
  val cardBg = Color(0xFF19222C)
  val accentColor = Color(0xFF00E676) // MT Green
  val textColor = Color(0xFFE6EDF3)
  val textSubColor = Color(0xFF8B949E)
  val folderColor = Color(0xFFFFB300) // MT Folder Amber
  val borderColor = Color(0xFF263238)

  var currentPath by remember { mutableStateOf(FileManagerEngine.ROOT_STORAGE_PATH) }
  var fileItems by remember { mutableStateOf<List<FileItem>>(emptyList()) }
  var isLoading by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var isSearchActive by remember { mutableStateOf(false) }

  // Clipboard (Copy / Cut)
  var clipboard by remember { mutableStateOf<ClipboardItem?>(null) }

  // Dialog states
  var showCreateFolderDialog by remember { mutableStateOf(false) }
  var showCreateFileDialog by remember { mutableStateOf(false) }
  var itemToRename by remember { mutableStateOf<FileItem?>(null) }
  var itemToDelete by remember { mutableStateOf<FileItem?>(null) }
  var itemDetails by remember { mutableStateOf<FileItem?>(null) }

  // Text editor modal
  var fileToEdit by remember { mutableStateOf<FileItem?>(null) }
  var editorContent by remember { mutableStateOf("") }
  var isEditorLoading by remember { mutableStateOf(false) }

  fun refreshList() {
    isLoading = true
    scope.launch(Dispatchers.IO) {
      val list = FileManagerEngine.listFiles(currentPath)
      withContext(Dispatchers.Main) {
        fileItems = list
        isLoading = false
      }
    }
  }

  // Load files when directory changes
  LaunchedEffect(currentPath) {
    refreshList()
  }

  val displayedItems = remember(fileItems, searchQuery) {
    if (searchQuery.isBlank()) {
      fileItems
    } else {
      fileItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
  }

  Scaffold(
    containerColor = darkBg,
    topBar = {
      Column(modifier = Modifier.background(topBarBg)) {
        TopAppBar(
          title = {
            Column {
              Text(
                text = "MT File Manager",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = textColor,
                fontFamily = FontFamily.Monospace
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                  shape = CircleShape,
                  color = if (hasShizukuPermission) accentColor else if (isShizukuRunning) Color(0xFFFFB300) else Color(0xFFEF5350),
                  modifier = Modifier.size(7.dp)
                ) {}
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (hasShizukuPermission) "Shizuku: Authorized" else if (isShizukuRunning) "Shizuku: Need Auth" else "Shizuku: Offline",
                  fontSize = 11.sp,
                  color = textSubColor,
                  fontFamily = FontFamily.Monospace
                )
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarBg),
          actions = {
            if (!hasShizukuPermission && isShizukuRunning) {
              OutlinedButton(
                onClick = onRequestShizukuPermission,
                border = BorderStroke(1.dp, accentColor),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(end = 4.dp).testTag("auth_shizuku_btn")
              ) {
                Text("Grant Shizuku", color = accentColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
              }
            }

            IconButton(
              onClick = {
                isSearchActive = !isSearchActive
                if (!isSearchActive) searchQuery = ""
              },
              modifier = Modifier.testTag("search_toggle_btn")
            ) {
              Icon(
                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                contentDescription = "Search",
                tint = textColor
              )
            }

            IconButton(
              onClick = { refreshList() },
              modifier = Modifier.testTag("refresh_btn")
            ) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = textColor
              )
            }
          }
        )

        // Storage Permission Warning Chip
        if (!hasStoragePermission) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color(0xFF372710))
              .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "Storage permission required to view all files",
              color = Color(0xFFFFCC80),
              fontSize = 11.sp,
              modifier = Modifier.weight(1f)
            )
            Button(
              onClick = onRequestStoragePermission,
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.height(30.dp)
            ) {
              Text("Grant", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
        }

        // Search Bar (if active)
        if (isSearchActive) {
          Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              placeholder = { Text("Filter files in current folder...", color = textSubColor, fontSize = 13.sp) },
              modifier = Modifier.fillMaxWidth().testTag("search_input"),
              singleLine = true,
              shape = RoundedCornerShape(8.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = borderColor,
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedContainerColor = cardBg,
                unfocusedContainerColor = cardBg
              )
            )
          }
        }

        // Current Path Breadcrumb & Navigation
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF131A22))
            .padding(horizontal = 8.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = {
              val parent = File(currentPath).parent
              if (parent != null && parent.isNotEmpty() && parent != "/") {
                currentPath = parent
              } else if (currentPath != FileManagerEngine.ROOT_STORAGE_PATH) {
                currentPath = FileManagerEngine.ROOT_STORAGE_PATH
              }
            },
            modifier = Modifier.size(36.dp).testTag("go_up_btn")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Up",
              tint = accentColor,
              modifier = Modifier.size(20.dp)
            )
          }

          Text(
            text = currentPath,
            color = textColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
          )
        }

        // Quick Jump Shortcuts (Internal, Android/data, Android/obb, Download)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 5.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          QuickPathChip("Home", FileManagerEngine.ROOT_STORAGE_PATH, currentPath) { currentPath = it }
          QuickPathChip("Android/data", FileManagerEngine.ANDROID_DATA_PATH, currentPath) { currentPath = it }
          QuickPathChip("Android/obb", FileManagerEngine.ANDROID_OBB_PATH, currentPath) { currentPath = it }
          QuickPathChip("Download", "${FileManagerEngine.ROOT_STORAGE_PATH}/Download", currentPath) { currentPath = it }
        }

        HorizontalDivider(color = borderColor, thickness = 1.dp)
      }
    },
    floatingActionButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        // Paste action if clipboard has content
        clipboard?.let { clip ->
          Button(
            onClick = {
              isLoading = true
              scope.launch(Dispatchers.IO) {
                val res = FileManagerEngine.pasteItem(clip, currentPath)
                withContext(Dispatchers.Main) {
                  isLoading = false
                  Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                  if (clip.action == ClipboardAction.CUT) {
                    clipboard = null
                  }
                  refreshList()
                }
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.height(48.dp).testTag("paste_button")
          ) {
            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste", tint = Color.Black)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (clip.action == ClipboardAction.CUT) "Move Here" else "Paste Here",
              color = Color.Black,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
          }

          IconButton(
            onClick = { clipboard = null },
            modifier = Modifier.background(Color(0xFF263238), CircleShape).size(42.dp)
          ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel Paste", tint = Color.White)
          }
        }

        // New Folder FAB
        FloatingActionButton(
          onClick = { showCreateFolderDialog = true },
          containerColor = cardBg,
          contentColor = folderColor,
          shape = CircleShape,
          modifier = Modifier.size(46.dp).testTag("new_folder_fab")
        ) {
          Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = "New Folder")
        }

        // New File FAB
        FloatingActionButton(
          onClick = { showCreateFileDialog = true },
          containerColor = cardBg,
          contentColor = Color(0xFF4FC3F7),
          shape = CircleShape,
          modifier = Modifier.size(46.dp).testTag("new_file_fab")
        ) {
          Icon(imageVector = Icons.Default.NoteAdd, contentDescription = "New File")
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(darkBg)
    ) {
      if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = accentColor, modifier = Modifier.size(40.dp))
        }
      } else if (displayedItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.Folder,
              contentDescription = null,
              tint = textSubColor.copy(alpha = 0.5f),
              modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = if (searchQuery.isNotEmpty()) "No matching files" else "Empty Folder",
              color = textSubColor,
              fontSize = 14.sp,
              fontFamily = FontFamily.Monospace
            )
            if (FileManagerEngine.isRestrictedPath(currentPath) && !hasShizukuPermission) {
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "Notice: Grant Shizuku permission to access Android/data",
                color = Color(0xFFFFB300),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
              )
            }
          }
        }
      } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
          items(displayedItems, key = { it.path }) { item ->
            FileRowItem(
              item = item,
              onClick = {
                if (item.isDirectory) {
                  currentPath = item.path
                } else {
                  // If text/code file, open in editor
                  val textExtensions = setOf("txt", "json", "xml", "lua", "cfg", "ini", "log", "sh", "py", "properties", "html", "js")
                  if (textExtensions.contains(item.extension) || item.size < 500_000) {
                    fileToEdit = item
                    isEditorLoading = true
                    scope.launch(Dispatchers.IO) {
                      val content = FileManagerEngine.readFileText(item.path)
                      withContext(Dispatchers.Main) {
                        editorContent = content
                        isEditorLoading = false
                      }
                    }
                  } else {
                    itemDetails = item
                  }
                }
              },
              onCopy = {
                clipboard = ClipboardItem(ClipboardAction.COPY, item)
              },
              onCut = {
                clipboard = ClipboardItem(ClipboardAction.CUT, item)
              },
              onRename = {
                itemToRename = item
              },
              onDelete = {
                itemToDelete = item
              },
              onDetails = {
                itemDetails = item
              }
            )
            HorizontalDivider(color = borderColor.copy(alpha = 0.4f), thickness = 0.5.dp)
          }
        }
      }
    }
  }

  // --- DIALOGS ---

  // 1. Create Folder Dialog
  if (showCreateFolderDialog) {
    var newFolderName by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showCreateFolderDialog = false },
      title = { Text("Create New Folder", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
      text = {
        OutlinedTextField(
          value = newFolderName,
          onValueChange = { newFolderName = it },
          placeholder = { Text("Folder name", color = textSubColor) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            unfocusedBorderColor = borderColor,
            focusedTextColor = textColor,
            unfocusedTextColor = textColor
          )
        )
      },
      confirmButton = {
        Button(
          onClick = {
            if (newFolderName.isNotBlank()) {
              scope.launch(Dispatchers.IO) {
                val res = FileManagerEngine.createFolder(currentPath, newFolderName)
                withContext(Dispatchers.Main) {
                  showCreateFolderDialog = false
                  Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                  refreshList()
                }
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = accentColor)
        ) {
          Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateFolderDialog = false }) {
          Text("Cancel", color = textSubColor)
        }
      },
      containerColor = cardBg
    )
  }

  // 2. Create File Dialog
  if (showCreateFileDialog) {
    var newFileName by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showCreateFileDialog = false },
      title = { Text("Create New File", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
      text = {
        OutlinedTextField(
          value = newFileName,
          onValueChange = { newFileName = it },
          placeholder = { Text("e.g. script.txt or file.json", color = textSubColor) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            unfocusedBorderColor = borderColor,
            focusedTextColor = textColor,
            unfocusedTextColor = textColor
          )
        )
      },
      confirmButton = {
        Button(
          onClick = {
            if (newFileName.isNotBlank()) {
              scope.launch(Dispatchers.IO) {
                val res = FileManagerEngine.createFile(currentPath, newFileName)
                withContext(Dispatchers.Main) {
                  showCreateFileDialog = false
                  Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                  refreshList()
                }
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = accentColor)
        ) {
          Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateFileDialog = false }) {
          Text("Cancel", color = textSubColor)
        }
      },
      containerColor = cardBg
    )
  }

  // 3. Rename Dialog
  itemToRename?.let { item ->
    var renameInput by remember { mutableStateOf(item.name) }
    AlertDialog(
      onDismissRequest = { itemToRename = null },
      title = { Text("Rename", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
      text = {
        OutlinedTextField(
          value = renameInput,
          onValueChange = { renameInput = it },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            unfocusedBorderColor = borderColor,
            focusedTextColor = textColor,
            unfocusedTextColor = textColor
          )
        )
      },
      confirmButton = {
        Button(
          onClick = {
            scope.launch(Dispatchers.IO) {
              val res = FileManagerEngine.renameItem(item, renameInput)
              withContext(Dispatchers.Main) {
                itemToRename = null
                Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                refreshList()
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = accentColor)
        ) {
          Text("Rename", color = Color.Black, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { itemToRename = null }) {
          Text("Cancel", color = textSubColor)
        }
      },
      containerColor = cardBg
    )
  }

  // 4. Delete Confirmation Dialog
  itemToDelete?.let { item ->
    AlertDialog(
      onDismissRequest = { itemToDelete = null },
      title = { Text("Delete ${if (item.isDirectory) "Folder" else "File"}?", color = Color(0xFFFF5252), fontSize = 16.sp, fontWeight = FontWeight.Bold) },
      text = {
        Text(
          text = "Are you sure you want to permanently delete \"${item.name}\"?",
          color = textColor,
          fontSize = 13.sp
        )
      },
      confirmButton = {
        Button(
          onClick = {
            scope.launch(Dispatchers.IO) {
              val res = FileManagerEngine.deleteItem(item)
              withContext(Dispatchers.Main) {
                itemToDelete = null
                Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                refreshList()
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
        ) {
          Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { itemToDelete = null }) {
          Text("Cancel", color = textSubColor)
        }
      },
      containerColor = cardBg
    )
  }

  // 5. File Details Dialog
  itemDetails?.let { item ->
    AlertDialog(
      onDismissRequest = { itemDetails = null },
      title = { Text("Properties", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          DetailRow("Name", item.name)
          DetailRow("Type", if (item.isDirectory) "Directory" else "File (${item.extension})")
          DetailRow("Size", item.formattedSize)
          if (item.formattedDate.isNotEmpty()) DetailRow("Modified", item.formattedDate)
          DetailRow("Full Path", item.path)
        }
      },
      confirmButton = {
        TextButton(onClick = { itemDetails = null }) {
          Text("OK", color = accentColor)
        }
      },
      containerColor = cardBg
    )
  }

  // 6. Text Editor / Viewer Modal
  fileToEdit?.let { item ->
    AlertDialog(
      onDismissRequest = { fileToEdit = null },
      title = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = item.name,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
          IconButton(onClick = { fileToEdit = null }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = textSubColor)
          }
        }
      },
      text = {
        Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
          if (isEditorLoading) {
            CircularProgressIndicator(color = accentColor, modifier = Modifier.align(Alignment.Center))
          } else {
            OutlinedTextField(
              value = editorContent,
              onValueChange = { editorContent = it },
              modifier = Modifier.fillMaxSize(),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = borderColor,
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedContainerColor = Color(0xFF101419),
                unfocusedContainerColor = Color(0xFF101419)
              )
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            scope.launch(Dispatchers.IO) {
              val res = FileManagerEngine.saveFileText(item.path, editorContent)
              withContext(Dispatchers.Main) {
                fileToEdit = null
                Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                refreshList()
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = accentColor)
        ) {
          Icon(Icons.Default.Save, contentDescription = "Save", tint = Color.Black, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { fileToEdit = null }) {
          Text("Close", color = textSubColor)
        }
      },
      containerColor = cardBg
    )
  }
}

@Composable
fun QuickPathChip(title: String, path: String, currentPath: String, onClick: (String) -> Unit) {
  val isSelected = currentPath == path
  val bg = if (isSelected) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFF1E2833)
  val border = if (isSelected) Color(0xFF00E676) else Color(0xFF2C3946)
  val textColor = if (isSelected) Color(0xFF00E676) else Color(0xFFB0BEC5)

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = bg,
    border = BorderStroke(1.dp, border),
    modifier = Modifier.clickable { onClick(path) }
  ) {
    Text(
      text = title,
      color = textColor,
      fontSize = 11.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
    )
  }
}

@Composable
fun FileRowItem(
  item: FileItem,
  onClick: () -> Unit,
  onCopy: () -> Unit,
  onCut: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  onDetails: () -> Unit
) {
  var menuExpanded by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Icon
    if (item.isDirectory) {
      Icon(
        imageVector = Icons.Default.Folder,
        contentDescription = "Folder",
        tint = Color(0xFFFFB300),
        modifier = Modifier.size(34.dp)
      )
    } else {
      val (icon, tint) = when (item.extension) {
        "apk" -> Pair(Icons.Default.Android, Color(0xFF66BB6A))
        "zip", "rar", "7z", "tar", "gz" -> Pair(Icons.Default.FolderZip, Color(0xFFAB47BC))
        "png", "jpg", "jpeg", "webp", "gif" -> Pair(Icons.Default.Image, Color(0xFF26C6DA))
        "txt", "json", "xml", "lua", "cfg", "ini", "log", "sh", "py" -> Pair(Icons.Default.Description, Color(0xFF42A5F5))
        else -> Pair(Icons.Default.InsertDriveFile, Color(0xFF90A4AE))
      }
      Icon(
        imageVector = icon,
        contentDescription = "File",
        tint = tint,
        modifier = Modifier.size(32.dp)
      )
    }

    Spacer(modifier = Modifier.width(12.dp))

    // Name & Info
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = item.name,
        color = Color(0xFFECEFF1),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = item.formattedSize,
          color = Color(0xFF78909C),
          fontSize = 11.sp,
          fontFamily = FontFamily.Monospace
        )
        if (item.formattedDate.isNotEmpty()) {
          Text(
            text = "•",
            color = Color(0xFF546E7A),
            fontSize = 11.sp
          )
          Text(
            text = item.formattedDate,
            color = Color(0xFF78909C),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }

    // 3-dots Menu
    Box {
      IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
        Icon(
          imageVector = Icons.Default.MoreVert,
          contentDescription = "Options",
          tint = Color(0xFF90A4AE),
          modifier = Modifier.size(18.dp)
        )
      }

      DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
        modifier = Modifier.background(Color(0xFF1E2631))
      ) {
        DropdownMenuItem(
          text = { Text("Copy", color = Color.White, fontSize = 13.sp) },
          leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(18.dp)) },
          onClick = {
            menuExpanded = false
            onCopy()
          }
        )
        DropdownMenuItem(
          text = { Text("Cut (Move)", color = Color.White, fontSize = 13.sp) },
          leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp)) },
          onClick = {
            menuExpanded = false
            onCut()
          }
        )
        DropdownMenuItem(
          text = { Text("Rename", color = Color.White, fontSize = 13.sp) },
          leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(18.dp)) },
          onClick = {
            menuExpanded = false
            onRename()
          }
        )
        DropdownMenuItem(
          text = { Text("Delete", color = Color(0xFFFF5252), fontSize = 13.sp) },
          leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp)) },
          onClick = {
            menuExpanded = false
            onDelete()
          }
        )
        DropdownMenuItem(
          text = { Text("Properties", color = Color(0xFFB0BEC5), fontSize = 13.sp) },
          leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFB0BEC5), modifier = Modifier.size(18.dp)) },
          onClick = {
            menuExpanded = false
            onDetails()
          }
        )
      }
    }
  }
}

@Composable
fun DetailRow(label: String, value: String) {
  Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
    Text(text = label, color = Color(0xFF78909C), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    Text(text = value, color = Color(0xFFECEFF1), fontSize = 13.sp, fontWeight = FontWeight.Medium)
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}
