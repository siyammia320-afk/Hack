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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CodeViewerData(
  val fileName: String,
  val filePath: String,
  val initialContent: String,
  val isReadOnly: Boolean = false,
  val onSaveContent: ((String) -> Unit)? = null
)

enum class ActivePanel {
  LEFT, RIGHT
}

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
        MTFileManagerApp(
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

@Composable
fun MTFileManagerApp(
  isShizukuRunning: Boolean,
  hasShizukuPermission: Boolean,
  hasStoragePermission: Boolean,
  onRequestShizukuPermission: () -> Unit,
  onRequestStoragePermission: () -> Unit
) {
  var activeCodeViewer by remember { mutableStateOf<CodeViewerData?>(null) }
  var activeZipFile by remember { mutableStateOf<FileItem?>(null) }
  var zipCurrentSubDir by remember { mutableStateOf("") }

  // Back handler for Full Screen Code Viewer
  BackHandler(enabled = activeCodeViewer != null) {
    activeCodeViewer = null
  }

  // Back handler for Zip Browser
  BackHandler(enabled = activeCodeViewer == null && activeZipFile != null) {
    if (zipCurrentSubDir.isNotEmpty()) {
      val trimmed = zipCurrentSubDir.trimEnd('/')
      val parent = trimmed.substringBeforeLast('/', "")
      zipCurrentSubDir = if (parent.isEmpty()) "" else "$parent/"
    } else {
      activeZipFile = null
    }
  }

  if (activeCodeViewer != null) {
    FullScreenCodeViewer(
      data = activeCodeViewer!!,
      onClose = { activeCodeViewer = null }
    )
  } else if (activeZipFile != null) {
    ZipBrowserScreen(
      zipFile = activeZipFile!!,
      currentSubDir = zipCurrentSubDir,
      onSubDirChange = { zipCurrentSubDir = it },
      onOpenFileAsCode = { name, text ->
        activeCodeViewer = CodeViewerData(
          fileName = name,
          filePath = activeZipFile!!.path + "/" + name,
          initialContent = text,
          isReadOnly = true,
          onSaveContent = null
        )
      },
      onClose = { activeZipFile = null },
      onExtractSuccess = { activeZipFile = null }
    )
  } else {
    MTDualPaneScreen(
      isShizukuRunning = isShizukuRunning,
      hasShizukuPermission = hasShizukuPermission,
      hasStoragePermission = hasStoragePermission,
      onRequestShizukuPermission = onRequestShizukuPermission,
      onRequestStoragePermission = onRequestStoragePermission,
      onOpenZip = { zipItem ->
        activeZipFile = zipItem
        zipCurrentSubDir = ""
      },
      onOpenFileCode = { codeData ->
        activeCodeViewer = codeData
      }
    )
  }
}

/**
 * MT Manager Dual-Pane (Two Screen) File Manager
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MTDualPaneScreen(
  isShizukuRunning: Boolean,
  hasShizukuPermission: Boolean,
  hasStoragePermission: Boolean,
  onRequestShizukuPermission: () -> Unit,
  onRequestStoragePermission: () -> Unit,
  onOpenZip: (FileItem) -> Unit,
  onOpenFileCode: (CodeViewerData) -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  // Crisp MT Manager Dark Palette (No blur, no glow)
  val darkBg = Color(0xFF12171E)
  val topBarBg = Color(0xFF19202A)
  val panelBg = Color(0xFF141A22)
  val activePanelBg = Color(0xFF161E28)
  val activeHighlight = Color(0xFF00E676)
  val borderColor = Color(0xFF232E3A)
  val textColor = Color(0xFFECEFF1)
  val textSubColor = Color(0xFF90A4AE)
  val folderColor = Color(0xFFFFB300)

  var activePanel by remember { mutableStateOf(ActivePanel.LEFT) }

  // Left Panel State
  var leftPath by remember { mutableStateOf(FileManagerEngine.ROOT_STORAGE_PATH) }
  var leftItems by remember { mutableStateOf<List<FileItem>>(emptyList()) }
  var leftLoading by remember { mutableStateOf(false) }
  var leftHistory by remember { mutableStateOf(listOf(FileManagerEngine.ROOT_STORAGE_PATH)) }
  var leftHistoryIndex by remember { mutableIntStateOf(0) }

  // Right Panel State
  var rightPath by remember { mutableStateOf(FileManagerEngine.ROOT_STORAGE_PATH) }
  var rightItems by remember { mutableStateOf<List<FileItem>>(emptyList()) }
  var rightLoading by remember { mutableStateOf(false) }
  var rightHistory by remember { mutableStateOf(listOf(FileManagerEngine.ROOT_STORAGE_PATH)) }
  var rightHistoryIndex by remember { mutableIntStateOf(0) }

  // Search & Filter
  var searchQuery by remember { mutableStateOf("") }
  var isSearchOpen by remember { mutableStateOf(false) }

  // Context Actions Dialog (like Screenshot 1!)
  var actionTargetItem by remember { mutableStateOf<FileItem?>(null) }
  var actionTargetPanel by remember { mutableStateOf(ActivePanel.LEFT) }

  // CRUD Dialogs
  var showCreateDialog by remember { mutableStateOf(false) }
  var itemToRename by remember { mutableStateOf<FileItem?>(null) }
  var itemToDelete by remember { mutableStateOf<FileItem?>(null) }
  var itemDetails by remember { mutableStateOf<FileItem?>(null) }
  var showTopMenu by remember { mutableStateOf(false) }

  // Storage Stats
  var diskUsageText by remember { mutableStateOf("Disk: --") }

  fun refreshLeft() {
    leftLoading = true
    scope.launch(Dispatchers.IO) {
      val list = FileManagerEngine.listFiles(leftPath)
      withContext(Dispatchers.Main) {
        leftItems = list
        leftLoading = false
      }
    }
  }

  fun refreshRight() {
    rightLoading = true
    scope.launch(Dispatchers.IO) {
      val list = FileManagerEngine.listFiles(rightPath)
      withContext(Dispatchers.Main) {
        rightItems = list
        rightLoading = false
      }
    }
  }

  fun refreshActive() {
    if (activePanel == ActivePanel.LEFT) refreshLeft() else refreshRight()
  }

  fun refreshBoth() {
    refreshLeft()
    refreshRight()
    scope.launch(Dispatchers.IO) {
      val disk = FileManagerEngine.getDiskUsageInfo()
      withContext(Dispatchers.Main) {
        diskUsageText = disk
      }
    }
  }

  LaunchedEffect(leftPath) {
    refreshLeft()
  }

  LaunchedEffect(rightPath) {
    refreshRight()
  }

  LaunchedEffect(Unit) {
    scope.launch(Dispatchers.IO) {
      val disk = FileManagerEngine.getDiskUsageInfo()
      withContext(Dispatchers.Main) {
        diskUsageText = disk
      }
    }
  }

  // Navigation helpers for active panel
  fun navigateActiveTo(newPath: String) {
    if (activePanel == ActivePanel.LEFT) {
      val newHistory = leftHistory.subList(0, leftHistoryIndex + 1) + newPath
      leftHistory = newHistory
      leftHistoryIndex = newHistory.size - 1
      leftPath = newPath
    } else {
      val newHistory = rightHistory.subList(0, rightHistoryIndex + 1) + newPath
      rightHistory = newHistory
      rightHistoryIndex = newHistory.size - 1
      rightPath = newPath
    }
  }

  fun goActiveUp() {
    val currentP = if (activePanel == ActivePanel.LEFT) leftPath else rightPath
    val parent = File(currentP).parent
    if (parent != null && parent.isNotEmpty() && parent != "/") {
      navigateActiveTo(parent)
    } else if (currentP != FileManagerEngine.ROOT_STORAGE_PATH) {
      navigateActiveTo(FileManagerEngine.ROOT_STORAGE_PATH)
    }
  }

  fun goActiveBack() {
    if (activePanel == ActivePanel.LEFT) {
      if (leftHistoryIndex > 0) {
        leftHistoryIndex--
        leftPath = leftHistory[leftHistoryIndex]
      }
    } else {
      if (rightHistoryIndex > 0) {
        rightHistoryIndex--
        rightPath = rightHistory[rightHistoryIndex]
      }
    }
  }

  fun goActiveForward() {
    if (activePanel == ActivePanel.LEFT) {
      if (leftHistoryIndex < leftHistory.size - 1) {
        leftHistoryIndex++
        leftPath = leftHistory[leftHistoryIndex]
      }
    } else {
      if (rightHistoryIndex < rightHistory.size - 1) {
        rightHistoryIndex++
        rightPath = rightHistory[rightHistoryIndex]
      }
    }
  }

  // Back button handling: navigates back in active panel before exiting app
  BackHandler(enabled = true) {
    val canGoBack = if (activePanel == ActivePanel.LEFT) leftHistoryIndex > 0 else rightHistoryIndex > 0
    if (canGoBack) {
      goActiveBack()
    } else {
      goActiveUp()
    }
  }

  // Active path and statistics for Top Bar (matching Screenshot 1 & 2!)
  val activeCurrentPath = if (activePanel == ActivePanel.LEFT) leftPath else rightPath
  val activeItems = if (activePanel == ActivePanel.LEFT) leftItems else rightItems
  val folderCount = activeItems.count { it.isDirectory }
  val fileCount = activeItems.count { !it.isDirectory }

  Scaffold(
    containerColor = darkBg,
    topBar = {
      Column(modifier = Modifier.background(topBarBg)) {
        TopAppBar(
          navigationIcon = {
            IconButton(onClick = { showTopMenu = true }) {
              Icon(Icons.Default.Menu, contentDescription = "Menu", tint = textColor)
            }
          },
          title = {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
              Text(
                text = activeCurrentPath,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "Folders: $folderCount  Files: $fileCount  $diskUsageText",
                  color = textSubColor,
                  fontSize = 11.sp,
                  fontFamily = FontFamily.Monospace,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          },
          actions = {
            // Shizuku status indicator
            Surface(
              shape = CircleShape,
              color = if (hasShizukuPermission) activeHighlight else if (isShizukuRunning) Color(0xFFFFB300) else Color(0xFFEF5350),
              modifier = Modifier.size(8.dp)
            ) {}
            Spacer(modifier = Modifier.width(6.dp))

            IconButton(onClick = { isSearchOpen = !isSearchOpen }) {
              Icon(
                imageVector = if (isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                contentDescription = "Search",
                tint = textColor
              )
            }

            Box {
              IconButton(onClick = { showTopMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = textColor)
              }

              DropdownMenu(
                expanded = showTopMenu,
                onDismissRequest = { showTopMenu = false },
                modifier = Modifier.background(Color(0xFF1E2631))
              ) {
                if (!hasShizukuPermission && isShizukuRunning) {
                  DropdownMenuItem(
                    text = { Text("Grant Shizuku Permission", color = activeHighlight, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Android, contentDescription = null, tint = activeHighlight) },
                    onClick = {
                      showTopMenu = false
                      onRequestShizukuPermission()
                    }
                  )
                }
                if (!hasStoragePermission) {
                  DropdownMenuItem(
                    text = { Text("Grant Storage Access", color = Color(0xFFFFB300), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFFFB300)) },
                    onClick = {
                      showTopMenu = false
                      onRequestStoragePermission()
                    }
                  )
                }
                DropdownMenuItem(
                  text = { Text("Refresh Panels", color = textColor, fontSize = 13.sp) },
                  leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = textColor) },
                  onClick = {
                    showTopMenu = false
                    refreshBoth()
                  }
                )
                DropdownMenuItem(
                  text = { Text("Switch Side (<->)", color = textColor, fontSize = 13.sp) },
                  leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = activeHighlight) },
                  onClick = {
                    showTopMenu = false
                    activePanel = if (activePanel == ActivePanel.LEFT) ActivePanel.RIGHT else ActivePanel.LEFT
                  }
                )
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarBg)
        )

        // Storage Warning if not granted
        if (!hasStoragePermission) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color(0xFF372710))
              .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "Storage access needed for all folders",
              color = Color(0xFFFFCC80),
              fontSize = 11.sp,
              modifier = Modifier.weight(1f)
            )
            Button(
              onClick = onRequestStoragePermission,
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
              shape = RoundedCornerShape(4.dp),
              modifier = Modifier.height(28.dp)
            ) {
              Text("Grant", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
        }

        // Live search filter input
        if (isSearchOpen) {
          Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              placeholder = { Text("Filter items in active panel...", color = textSubColor, fontSize = 12.sp) },
              modifier = Modifier.fillMaxWidth().height(46.dp),
              singleLine = true,
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = activeHighlight,
                unfocusedBorderColor = borderColor,
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedContainerColor = panelBg,
                unfocusedContainerColor = panelBg
              )
            )
          }
        }

        // Quick path shortcuts
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          QuickJumpChip("Home", FileManagerEngine.ROOT_STORAGE_PATH) { navigateActiveTo(it) }
          QuickJumpChip("Android/data", FileManagerEngine.ANDROID_DATA_PATH) { navigateActiveTo(it) }
          QuickJumpChip("Android/obb", FileManagerEngine.ANDROID_OBB_PATH) { navigateActiveTo(it) }
          QuickJumpChip("Download", "${FileManagerEngine.ROOT_STORAGE_PATH}/Download") { navigateActiveTo(it) }
        }

        HorizontalDivider(color = borderColor, thickness = 1.dp)
      }
    },
    bottomBar = {
      // Bottom Toolbar matching MT Manager Screenshot!
      // Actions: <  >  +  <->  ↑
      Surface(
        color = topBarBg,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceAround,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // < (Back history)
          val canBack = if (activePanel == ActivePanel.LEFT) leftHistoryIndex > 0 else rightHistoryIndex > 0
          IconButton(
            onClick = { goActiveBack() },
            enabled = canBack,
            modifier = Modifier.testTag("toolbar_back_btn")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = if (canBack) textColor else textSubColor.copy(alpha = 0.3f),
              modifier = Modifier.size(22.dp)
            )
          }

          // > (Forward history)
          val canForward = if (activePanel == ActivePanel.LEFT) leftHistoryIndex < leftHistory.size - 1 else rightHistoryIndex < rightHistory.size - 1
          IconButton(
            onClick = { goActiveForward() },
            enabled = canForward,
            modifier = Modifier.testTag("toolbar_forward_btn")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = "Forward",
              tint = if (canForward) textColor else textSubColor.copy(alpha = 0.3f),
              modifier = Modifier.size(22.dp)
            )
          }

          // + (Create Folder / File)
          IconButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier.testTag("toolbar_create_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = "Create",
              tint = activeHighlight,
              modifier = Modifier.size(26.dp)
            )
          }

          // <-> (Switch active panel Left / Right)
          IconButton(
            onClick = {
              activePanel = if (activePanel == ActivePanel.LEFT) ActivePanel.RIGHT else ActivePanel.LEFT
            },
            modifier = Modifier.testTag("toolbar_switch_panel_btn")
          ) {
            Icon(
              imageVector = Icons.Default.SwapHoriz,
              contentDescription = "Switch Panel",
              tint = activeHighlight,
              modifier = Modifier.size(26.dp)
            )
          }

          // ↑ (Parent Directory)
          IconButton(
            onClick = { goActiveUp() },
            modifier = Modifier.testTag("toolbar_up_btn")
          ) {
            Icon(
              imageVector = Icons.Default.ArrowUpward,
              contentDescription = "Parent Directory",
              tint = textColor,
              modifier = Modifier.size(22.dp)
            )
          }
        }
      }
    }
  ) { innerPadding ->
    // Dual Pane Container (50% Left - 50% Right)
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(darkBg)
    ) {
      // LEFT PANEL
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .background(if (activePanel == ActivePanel.LEFT) activePanelBg else panelBg)
          .clickable { activePanel = ActivePanel.LEFT }
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          // Panel mini-tab header
          PanelTabHeader(
            title = "Left: " + (File(leftPath).name.ifEmpty { "Root" }),
            isActive = activePanel == ActivePanel.LEFT,
            onClick = { activePanel = ActivePanel.LEFT }
          )

          if (leftLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(color = activeHighlight, modifier = Modifier.size(28.dp))
            }
          } else {
            val displayedLeft = remember(leftItems, searchQuery, isSearchOpen, activePanel) {
              if (isSearchOpen && activePanel == ActivePanel.LEFT && searchQuery.isNotBlank()) {
                leftItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
              } else {
                leftItems
              }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
              // ".." parent folder item
              item {
                ParentDirRowItem(onClick = {
                  activePanel = ActivePanel.LEFT
                  val parent = File(leftPath).parent
                  if (parent != null && parent.isNotEmpty() && parent != "/") {
                    navigateActiveTo(parent)
                  } else if (leftPath != FileManagerEngine.ROOT_STORAGE_PATH) {
                    navigateActiveTo(FileManagerEngine.ROOT_STORAGE_PATH)
                  }
                })
                HorizontalDivider(color = borderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
              }

              items(displayedLeft, key = { "L_${it.path}" }) { item ->
                CompactFileRow(
                  item = item,
                  onClick = {
                    activePanel = ActivePanel.LEFT
                    if (item.isDirectory) {
                      navigateActiveTo(item.path)
                    } else if (FileManagerEngine.isArchiveFile(item.extension)) {
                      onOpenZip(item)
                    } else {
                      // Open in Full Screen Code Viewer
                      scope.launch(Dispatchers.IO) {
                        val content = FileManagerEngine.readFileText(item.path)
                        withContext(Dispatchers.Main) {
                          onOpenFileCode(
                            CodeViewerData(
                              fileName = item.name,
                              filePath = item.path,
                              initialContent = content,
                              isReadOnly = false,
                              onSaveContent = { newContent ->
                                scope.launch(Dispatchers.IO) {
                                  val res = FileManagerEngine.saveFileText(item.path, newContent)
                                  withContext(Dispatchers.Main) {
                                    Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                                    refreshLeft()
                                  }
                                }
                              }
                            )
                          )
                        }
                      }
                    }
                  },
                  onLongClick = {
                    activePanel = ActivePanel.LEFT
                    actionTargetItem = item
                    actionTargetPanel = ActivePanel.LEFT
                  },
                  onOptionsClick = {
                    activePanel = ActivePanel.LEFT
                    actionTargetItem = item
                    actionTargetPanel = ActivePanel.LEFT
                  }
                )
                HorizontalDivider(color = borderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
              }
            }
          }
        }
      }

      // Vertical Divider between panels
      Box(
        modifier = Modifier
          .width(1.5.dp)
          .fillMaxHeight()
          .background(borderColor)
      )

      // RIGHT PANEL
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .background(if (activePanel == ActivePanel.RIGHT) activePanelBg else panelBg)
          .clickable { activePanel = ActivePanel.RIGHT }
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          // Panel mini-tab header
          PanelTabHeader(
            title = "Right: " + (File(rightPath).name.ifEmpty { "Root" }),
            isActive = activePanel == ActivePanel.RIGHT,
            onClick = { activePanel = ActivePanel.RIGHT }
          )

          if (rightLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(color = activeHighlight, modifier = Modifier.size(28.dp))
            }
          } else {
            val displayedRight = remember(rightItems, searchQuery, isSearchOpen, activePanel) {
              if (isSearchOpen && activePanel == ActivePanel.RIGHT && searchQuery.isNotBlank()) {
                rightItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
              } else {
                rightItems
              }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
              // ".." parent folder item
              item {
                ParentDirRowItem(onClick = {
                  activePanel = ActivePanel.RIGHT
                  val parent = File(rightPath).parent
                  if (parent != null && parent.isNotEmpty() && parent != "/") {
                    navigateActiveTo(parent)
                  } else if (rightPath != FileManagerEngine.ROOT_STORAGE_PATH) {
                    navigateActiveTo(FileManagerEngine.ROOT_STORAGE_PATH)
                  }
                })
                HorizontalDivider(color = borderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
              }

              items(displayedRight, key = { "R_${it.path}" }) { item ->
                CompactFileRow(
                  item = item,
                  onClick = {
                    activePanel = ActivePanel.RIGHT
                    if (item.isDirectory) {
                      navigateActiveTo(item.path)
                    } else if (FileManagerEngine.isArchiveFile(item.extension)) {
                      onOpenZip(item)
                    } else {
                      // Open in Full Screen Code Viewer
                      scope.launch(Dispatchers.IO) {
                        val content = FileManagerEngine.readFileText(item.path)
                        withContext(Dispatchers.Main) {
                          onOpenFileCode(
                            CodeViewerData(
                              fileName = item.name,
                              filePath = item.path,
                              initialContent = content,
                              isReadOnly = false,
                              onSaveContent = { newContent ->
                                scope.launch(Dispatchers.IO) {
                                  val res = FileManagerEngine.saveFileText(item.path, newContent)
                                  withContext(Dispatchers.Main) {
                                    Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                                    refreshRight()
                                  }
                                }
                              }
                            )
                          )
                        }
                      }
                    }
                  },
                  onLongClick = {
                    activePanel = ActivePanel.RIGHT
                    actionTargetItem = item
                    actionTargetPanel = ActivePanel.RIGHT
                  },
                  onOptionsClick = {
                    activePanel = ActivePanel.RIGHT
                    actionTargetItem = item
                    actionTargetPanel = ActivePanel.RIGHT
                  }
                )
                HorizontalDivider(color = borderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
              }
            }
          }
        }
      }
    }
  }

  // --- MT Manager Action Dialog (like Screenshot 1!) ---
  actionTargetItem?.let { targetItem ->
    val isFromLeft = (actionTargetPanel == ActivePanel.LEFT)
    val destinationFolder = if (isFromLeft) rightPath else leftPath
    val copyActionLabel = if (isFromLeft) "-> Copy" else "<- Copy"
    val moveActionLabel = if (isFromLeft) "-> Move" else "<- Move"

    AlertDialog(
      onDismissRequest = { actionTargetItem = null },
      title = {
        Text(
          text = targetItem.name,
          color = textColor,
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
        ) {
          Text(
            text = "Target folder: ${File(destinationFolder).name.ifEmpty { "Root" }}",
            color = activeHighlight,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
          )

          // 1. Copy to opposite side
          ActionPopupRow(
            icon = Icons.Default.ContentCopy,
            title = copyActionLabel,
            tint = activeHighlight
          ) {
            actionTargetItem = null
            scope.launch(Dispatchers.IO) {
              val res = FileManagerEngine.pasteItem(
                ClipboardItem(ClipboardAction.COPY, targetItem),
                destinationFolder
              )
              withContext(Dispatchers.Main) {
                Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                refreshBoth()
              }
            }
          }

          // 2. Move to opposite side
          ActionPopupRow(
            icon = Icons.Default.ContentCut,
            title = moveActionLabel,
            tint = Color(0xFFFFB300)
          ) {
            actionTargetItem = null
            scope.launch(Dispatchers.IO) {
              val res = FileManagerEngine.pasteItem(
                ClipboardItem(ClipboardAction.CUT, targetItem),
                destinationFolder
              )
              withContext(Dispatchers.Main) {
                Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                refreshBoth()
              }
            }
          }

          // 3. Rename
          ActionPopupRow(
            icon = Icons.Default.Edit,
            title = "Rename",
            tint = Color(0xFF4FC3F7)
          ) {
            actionTargetItem = null
            itemToRename = targetItem
          }

          // 4. Delete
          ActionPopupRow(
            icon = Icons.Default.Delete,
            title = "Delete",
            tint = Color(0xFFFF5252)
          ) {
            actionTargetItem = null
            itemToDelete = targetItem
          }

          // 5. Compress to ZIP
          ActionPopupRow(
            icon = Icons.Default.Compress,
            title = "Compress",
            tint = Color(0xFFCE93D8)
          ) {
            actionTargetItem = null
            val zipOut = "${targetItem.path}.zip"
            scope.launch(Dispatchers.IO) {
              val res = FileManagerEngine.compressItem(targetItem, zipOut)
              withContext(Dispatchers.Main) {
                Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                refreshActive()
              }
            }
          }

          // 6. Properties
          ActionPopupRow(
            icon = Icons.Default.Info,
            title = "Properties",
            tint = Color(0xFFB0BEC5)
          ) {
            actionTargetItem = null
            itemDetails = targetItem
          }

          // 7. If text file, View/Edit Code
          if (!targetItem.isDirectory) {
            ActionPopupRow(
              icon = Icons.Default.Description,
              title = "Open as Code",
              tint = Color(0xFF80CBC4)
            ) {
              actionTargetItem = null
              scope.launch(Dispatchers.IO) {
                val content = FileManagerEngine.readFileText(targetItem.path)
                withContext(Dispatchers.Main) {
                  onOpenFileCode(
                    CodeViewerData(
                      fileName = targetItem.name,
                      filePath = targetItem.path,
                      initialContent = content,
                      isReadOnly = false,
                      onSaveContent = { newContent ->
                        scope.launch(Dispatchers.IO) {
                          val res = FileManagerEngine.saveFileText(targetItem.path, newContent)
                          withContext(Dispatchers.Main) {
                            Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                            refreshBoth()
                          }
                        }
                      }
                    )
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { actionTargetItem = null }) {
          Text("Cancel", color = textSubColor)
        }
      },
      containerColor = Color(0xFF1E2631)
    )
  }

  // --- Create Folder / File Dialog ---
  if (showCreateDialog) {
    var createMode by remember { mutableStateOf("folder") } // "folder" or "file"
    var nameInput by remember { mutableStateOf("") }

    AlertDialog(
      onDismissRequest = { showCreateDialog = false },
      title = {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "New Folder",
            color = if (createMode == "folder") activeHighlight else textSubColor,
            fontWeight = if (createMode == "folder") FontWeight.Bold else FontWeight.Normal,
            fontSize = 15.sp,
            modifier = Modifier.clickable { createMode = "folder" }
          )
          Text(
            text = "New File",
            color = if (createMode == "file") activeHighlight else textSubColor,
            fontWeight = if (createMode == "file") FontWeight.Bold else FontWeight.Normal,
            fontSize = 15.sp,
            modifier = Modifier.clickable { createMode = "file" }
          )
        }
      },
      text = {
        OutlinedTextField(
          value = nameInput,
          onValueChange = { nameInput = it },
          placeholder = {
            Text(
              if (createMode == "folder") "Folder name" else "File name (e.g. script.txt)",
              color = textSubColor
            )
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = activeHighlight,
            unfocusedBorderColor = borderColor,
            focusedTextColor = textColor,
            unfocusedTextColor = textColor
          )
        )
      },
      confirmButton = {
        Button(
          onClick = {
            if (nameInput.isNotBlank()) {
              val currentDir = if (activePanel == ActivePanel.LEFT) leftPath else rightPath
              scope.launch(Dispatchers.IO) {
                val res = if (createMode == "folder") {
                  FileManagerEngine.createFolder(currentDir, nameInput)
                } else {
                  FileManagerEngine.createFile(currentDir, nameInput)
                }
                withContext(Dispatchers.Main) {
                  showCreateDialog = false
                  Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                  refreshActive()
                }
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = activeHighlight)
        ) {
          Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateDialog = false }) {
          Text("Cancel", color = textSubColor)
        }
      },
      containerColor = Color(0xFF1E2631)
    )
  }

  // --- Rename Dialog ---
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
            focusedBorderColor = activeHighlight,
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
                refreshBoth()
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = activeHighlight)
        ) {
          Text("Rename", color = Color.Black, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { itemToRename = null }) {
          Text("Cancel", color = textSubColor)
        }
      },
      containerColor = Color(0xFF1E2631)
    )
  }

  // --- Delete Dialog ---
  itemToDelete?.let { item ->
    AlertDialog(
      onDismissRequest = { itemToDelete = null },
      title = { Text("Delete ${if (item.isDirectory) "Folder" else "File"}?", color = Color(0xFFFF5252), fontSize = 16.sp, fontWeight = FontWeight.Bold) },
      text = {
        Text(
          text = "Delete \"${item.name}\" permanently?",
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
                refreshBoth()
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
      containerColor = Color(0xFF1E2631)
    )
  }

  // --- Properties Dialog ---
  itemDetails?.let { item ->
    AlertDialog(
      onDismissRequest = { itemDetails = null },
      title = { Text("Properties", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          DetailRow("Name", item.name)
          DetailRow("Type", if (item.isDirectory) "Directory" else "File (${item.extension})")
          DetailRow("Size", item.formattedSize)
          if (item.formattedDate.isNotEmpty()) DetailRow("Modified", item.formattedDate)
          DetailRow("Full Path", item.path)
        }
      },
      confirmButton = {
        TextButton(onClick = { itemDetails = null }) {
          Text("OK", color = activeHighlight)
        }
      },
      containerColor = Color(0xFF1E2631)
    )
  }
}

/**
 * Panel Tab Header showing active status
 */
@Composable
fun PanelTabHeader(
  title: String,
  isActive: Boolean,
  onClick: () -> Unit
) {
  val activeColor = Color(0xFF00E676)
  val inactiveColor = Color(0xFF232E3A)

  Surface(
    color = if (isActive) Color(0xFF1B2430) else Color(0xFF131922),
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
  ) {
    Column {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          shape = CircleShape,
          color = if (isActive) activeColor else Color.Transparent,
          modifier = Modifier.size(6.dp)
        ) {}
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = title,
          color = if (isActive) Color.White else Color(0xFF90A4AE),
          fontSize = 12.sp,
          fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
          fontFamily = FontFamily.Monospace,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(2.dp)
          .background(if (isActive) activeColor else inactiveColor)
      )
    }
  }
}

/**
 * Compact File Row matching MT Manager aesthetic in Screenshots!
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CompactFileRow(
  item: FileItem,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onOptionsClick: () -> Unit
) {
  val textColor = Color(0xFFECEFF1)
  val textSubColor = Color(0xFF78909C)
  val folderColor = Color(0xFFFFB300)

  val icon = if (item.isDirectory) {
    Icons.Default.Folder
  } else if (FileManagerEngine.isArchiveFile(item.extension)) {
    Icons.Default.FolderZip
  } else {
    Icons.AutoMirrored.Filled.InsertDriveFile
  }

  val iconColor = if (item.isDirectory) {
    folderColor
  } else if (FileManagerEngine.isArchiveFile(item.extension)) {
    Color(0xFFCE93D8)
  } else {
    Color(0xFF4FC3F7)
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick
      )
      .padding(horizontal = 6.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = iconColor,
      modifier = Modifier.size(24.dp)
    )

    Spacer(modifier = Modifier.width(6.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = item.name,
        color = textColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = if (item.isDirectory) {
          item.formattedDate.substringAfter(" ").ifEmpty { item.formattedDate }
        } else {
          "${item.formattedSize} • ${item.formattedDate.substringAfter(" ")}"
        },
        color = textSubColor,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1
      )
    }

    IconButton(
      onClick = onOptionsClick,
      modifier = Modifier.size(26.dp)
    ) {
      Icon(
        imageVector = Icons.Default.MoreVert,
        contentDescription = "Options",
        tint = Color(0xFF546E7A),
        modifier = Modifier.size(16.dp)
      )
    }
  }
}

/**
 * Top ".." parent folder row
 */
@Composable
fun ParentDirRowItem(
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(horizontal = 6.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = Icons.Default.Folder,
      contentDescription = "Parent Directory",
      tint = Color(0xFFFFB300),
      modifier = Modifier.size(24.dp)
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = "..",
      color = Color.White,
      fontSize = 15.sp,
      fontWeight = FontWeight.Bold,
      fontFamily = FontFamily.Monospace
    )
  }
}

/**
 * Action row in popup menu (Screenshot 1: <- Copy, <- Move, Rename, Delete, Compress, etc.)
 */
@Composable
fun ActionPopupRow(
  icon: ImageVector,
  title: String,
  tint: Color,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(vertical = 9.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = icon,
      contentDescription = title,
      tint = tint,
      modifier = Modifier.size(20.dp)
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = title,
      color = Color(0xFFECEFF1),
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium
    )
  }
}

/**
 * Quick path jump chips
 */
@Composable
fun QuickJumpChip(
  label: String,
  path: String,
  onClick: (String) -> Unit
) {
  Surface(
    shape = RoundedCornerShape(4.dp),
    color = Color(0xFF1E2833),
    border = BorderStroke(0.5.dp, Color(0xFF2C3947)),
    modifier = Modifier.clickable { onClick(path) }
  ) {
    Text(
      text = label,
      color = Color(0xFFB0BEC5),
      fontSize = 10.sp,
      fontFamily = FontFamily.Monospace,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
    )
  }
}

/**
 * Full Screen Code Viewer & Editor with live code search, line numbers and zero glow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenCodeViewer(
  data: CodeViewerData,
  onClose: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val darkBg = Color(0xFF0F141A)
  val topBarBg = Color(0xFF161E27)
  val textColor = Color(0xFFE6EDF3)
  val textSubColor = Color(0xFF8B949E)
  val accentColor = Color(0xFF00E676)
  val borderColor = Color(0xFF263238)

  var textFieldValue by remember { mutableStateOf(TextFieldValue(data.initialContent)) }
  var isSearchOpen by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var currentMatchIndex by remember { mutableIntStateOf(0) }

  val verticalScrollState = rememberScrollState()
  val horizontalScrollState = rememberScrollState()

  // Find all matches in code
  val matches = remember(textFieldValue.text, searchQuery) {
    if (searchQuery.isBlank()) {
      emptyList<Pair<Int, Int>>()
    } else {
      val list = mutableListOf<Pair<Int, Int>>()
      val text = textFieldValue.text
      var startIndex = 0
      while (startIndex < text.length) {
        val found = text.indexOf(searchQuery, startIndex, ignoreCase = true)
        if (found == -1) break
        list.add(Pair(found, found + searchQuery.length))
        startIndex = found + searchQuery.length.coerceAtLeast(1)
      }
      list
    }
  }

  fun jumpToMatch(index: Int) {
    if (matches.isEmpty()) return
    val targetIndex = (index + matches.size) % matches.size
    currentMatchIndex = targetIndex
    val match = matches[targetIndex]
    textFieldValue = textFieldValue.copy(
      selection = TextRange(match.first, match.second)
    )
    val lineNum = textFieldValue.text.substring(0, match.first).count { it == '\n' }
    scope.launch {
      verticalScrollState.animateScrollTo((lineNum * 54 - 120).coerceAtLeast(0))
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
                text = data.fileName,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "${textFieldValue.text.lines().size} lines • ${if (data.isReadOnly) "Read Only" else "Editable"}",
                color = textSubColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
              )
            }
          },
          navigationIcon = {
            IconButton(onClick = onClose) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = accentColor
              )
            }
          },
          actions = {
            IconButton(onClick = {
              isSearchOpen = !isSearchOpen
              if (!isSearchOpen) searchQuery = ""
            }) {
              Icon(
                imageVector = if (isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                contentDescription = "Search Code",
                tint = textColor
              )
            }

            if (!data.isReadOnly && data.onSaveContent != null) {
              IconButton(onClick = {
                data.onSaveContent.invoke(textFieldValue.text)
              }) {
                Icon(
                  imageVector = Icons.Default.Save,
                  contentDescription = "Save",
                  tint = accentColor
                )
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarBg)
        )

        // Code Search Bar
        if (isSearchOpen) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color(0xFF19222C))
              .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedTextField(
              value = searchQuery,
              onValueChange = {
                searchQuery = it
                currentMatchIndex = 0
                if (it.isNotEmpty()) {
                  val first = textFieldValue.text.indexOf(it, ignoreCase = true)
                  if (first != -1) {
                    textFieldValue = textFieldValue.copy(
                      selection = TextRange(first, first + it.length)
                    )
                  }
                }
              },
              placeholder = { Text("Search code...", color = textSubColor, fontSize = 12.sp) },
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("code_search_input"),
              singleLine = true,
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = borderColor,
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedContainerColor = darkBg,
                unfocusedContainerColor = darkBg
              )
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
              text = if (matches.isEmpty()) "0" else "${currentMatchIndex + 1}/${matches.size}",
              color = if (matches.isEmpty() && searchQuery.isNotEmpty()) Color(0xFFFF5252) else accentColor,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace,
              modifier = Modifier.padding(horizontal = 4.dp)
            )

            IconButton(
              onClick = { jumpToMatch(currentMatchIndex - 1) },
              enabled = matches.isNotEmpty(),
              modifier = Modifier.size(36.dp)
            ) {
              Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Previous Match",
                tint = if (matches.isNotEmpty()) textColor else textSubColor.copy(alpha = 0.4f)
              )
            }

            IconButton(
              onClick = { jumpToMatch(currentMatchIndex + 1) },
              enabled = matches.isNotEmpty(),
              modifier = Modifier.size(36.dp)
            ) {
              Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Next Match",
                tint = if (matches.isNotEmpty()) textColor else textSubColor.copy(alpha = 0.4f)
              )
            }
          }
        }

        HorizontalDivider(color = borderColor, thickness = 1.dp)
      }
    }
  ) { paddingValues ->
    val lines = remember(textFieldValue.text) { textFieldValue.text.lines() }
    val lineCount = lines.size.coerceAtLeast(1)

    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .background(darkBg)
        .verticalScroll(verticalScrollState)
    ) {
      // Line numbers column
      Column(
        modifier = Modifier
          .background(Color(0xFF131922))
          .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.End
      ) {
        val lineNumbersText = remember(lineCount) {
          (1..lineCount).joinToString("\n")
        }
        Text(
          text = lineNumbersText,
          color = Color(0xFF546E7A),
          fontSize = 12.sp,
          fontFamily = FontFamily.Monospace,
          lineHeight = 22.sp
        )
      }

      Box(
        modifier = Modifier
          .width(1.dp)
          .height((lineCount * 22).dp + 20.dp)
          .background(borderColor)
      )

      // Code editor area
      Box(
        modifier = Modifier
          .weight(1f)
          .horizontalScroll(horizontalScrollState)
          .padding(horizontal = 10.dp, vertical = 10.dp)
      ) {
        BasicTextField(
          value = textFieldValue,
          onValueChange = {
            if (!data.isReadOnly) {
              textFieldValue = it
            }
          },
          readOnly = data.isReadOnly,
          textStyle = TextStyle(
            color = textColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 22.sp
          ),
          cursorBrush = SolidColor(accentColor),
          modifier = Modifier.fillMaxWidth().testTag("code_editor_field")
        )
      }
    }
  }
}

/**
 * MT Manager style Zip Archive Browser with folder navigation & code preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipBrowserScreen(
  zipFile: FileItem,
  currentSubDir: String,
  onSubDirChange: (String) -> Unit,
  onOpenFileAsCode: (fileName: String, content: String) -> Unit,
  onClose: () -> Unit,
  onExtractSuccess: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val darkBg = Color(0xFF0F141A)
  val topBarBg = Color(0xFF161E27)
  val cardBg = Color(0xFF19222C)
  val accentColor = Color(0xFF00E676)
  val textColor = Color(0xFFE6EDF3)
  val textSubColor = Color(0xFF8B949E)
  val borderColor = Color(0xFF263238)

  var zipEntries by remember { mutableStateOf<List<ZipEntryItem>>(emptyList()) }
  var isLoadingEntries by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var isSearchActive by remember { mutableStateOf(false) }

  fun loadEntries() {
    isLoadingEntries = true
    scope.launch(Dispatchers.IO) {
      val entries = FileManagerEngine.listZipEntries(zipFile.path, currentSubDir, context.cacheDir)
      withContext(Dispatchers.Main) {
        zipEntries = entries
        isLoadingEntries = false
      }
    }
  }

  LaunchedEffect(zipFile.path, currentSubDir) {
    loadEntries()
  }

  val displayedEntries = remember(zipEntries, searchQuery) {
    if (searchQuery.isBlank()) {
      zipEntries
    } else {
      zipEntries.filter { it.name.contains(searchQuery, ignoreCase = true) }
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
                text = zipFile.name,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = textColor,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "Archive: ${zipFile.formattedSize}",
                fontSize = 11.sp,
                color = textSubColor,
                fontFamily = FontFamily.Monospace
              )
            }
          },
          navigationIcon = {
            IconButton(onClick = {
              if (currentSubDir.isNotEmpty()) {
                val trimmed = currentSubDir.trimEnd('/')
                val parent = trimmed.substringBeforeLast('/', "")
                onSubDirChange(if (parent.isEmpty()) "" else "$parent/")
              } else {
                onClose()
              }
            }) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = accentColor)
            }
          },
          actions = {
            IconButton(onClick = {
              isSearchActive = !isSearchActive
              if (!isSearchActive) searchQuery = ""
            }) {
              Icon(
                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                contentDescription = "Search in archive",
                tint = textColor
              )
            }

            Button(
              onClick = {
                val parentDir = File(zipFile.path).parent ?: FileManagerEngine.ROOT_STORAGE_PATH
                scope.launch(Dispatchers.IO) {
                  val res = FileManagerEngine.extractZip(zipFile.path, parentDir, context.cacheDir)
                  withContext(Dispatchers.Main) {
                    Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                    if (res.first) onExtractSuccess()
                  }
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = accentColor),
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.padding(end = 6.dp).height(32.dp)
            ) {
              Icon(Icons.Default.Unarchive, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Extract All", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarBg)
        )

        if (isSearchActive) {
          Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              placeholder = { Text("Filter files in archive...", color = textSubColor, fontSize = 12.sp) },
              modifier = Modifier.fillMaxWidth().height(46.dp),
              singleLine = true,
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

        // Sub path breadcrumb
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF131A22))
            .padding(horizontal = 8.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = {
              if (currentSubDir.isNotEmpty()) {
                val trimmed = currentSubDir.trimEnd('/')
                val parent = trimmed.substringBeforeLast('/', "")
                onSubDirChange(if (parent.isEmpty()) "" else "$parent/")
              } else {
                onClose()
              }
            },
            modifier = Modifier.size(36.dp)
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up", tint = accentColor, modifier = Modifier.size(20.dp))
          }

          Text(
            text = "/${currentSubDir}",
            color = textColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        HorizontalDivider(color = borderColor, thickness = 1.dp)
      }
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .background(darkBg)
    ) {
      if (isLoadingEntries) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = accentColor, modifier = Modifier.size(36.dp))
        }
      } else if (displayedEntries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("No items in folder", color = textSubColor, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
      } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
          items(displayedEntries, key = { it.entryPath }) { entry ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  if (entry.isDirectory) {
                    onSubDirChange(entry.entryPath)
                  } else {
                    scope.launch(Dispatchers.IO) {
                      val text = FileManagerEngine.readZipEntryText(zipFile.path, entry.entryPath, context.cacheDir)
                      withContext(Dispatchers.Main) {
                        onOpenFileAsCode(entry.name, text)
                      }
                    }
                  }
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (entry.isDirectory) Color(0xFFFFB300) else Color(0xFF4FC3F7),
                modifier = Modifier.size(26.dp)
              )

              Spacer(modifier = Modifier.width(10.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = entry.name,
                  color = textColor,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Medium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = entry.formattedSize,
                  color = textSubColor,
                  fontSize = 11.sp,
                  fontFamily = FontFamily.Monospace
                )
              }

              if (!entry.isDirectory) {
                IconButton(
                  onClick = {
                    val parentDir = File(zipFile.path).parent ?: FileManagerEngine.ROOT_STORAGE_PATH
                    scope.launch(Dispatchers.IO) {
                      val res = FileManagerEngine.extractZipEntry(zipFile.path, entry.entryPath, parentDir, context.cacheDir)
                      withContext(Dispatchers.Main) {
                        Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                      }
                    }
                  },
                  modifier = Modifier.size(32.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Unarchive,
                    contentDescription = "Extract file",
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
            HorizontalDivider(color = borderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
          }
        }
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
