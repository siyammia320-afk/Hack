package com.example

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

  private var isShizukuRunningState = mutableStateOf(false)
  private var hasPermissionState = mutableStateOf(false)
  private var isOverlayRunningState = mutableStateOf(false)
  private var sourcePathState = mutableStateOf("")
  private var fileCountState = mutableIntStateOf(0)

  private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
    updateStatus()
  }

  private val binderDeadListener = Shizuku.OnBinderDeadListener {
    updateStatus()
  }

  private val requestPermissionResultListener =
    Shizuku.OnRequestPermissionResultListener { _, grantResult ->
      hasPermissionState.value = (grantResult == PackageManager.PERMISSION_GRANTED)
      if (hasPermissionState.value) {
        Toast.makeText(this, "Shizuku permission granted", Toast.LENGTH_SHORT).show()
      } else {
        Toast.makeText(this, "Shizuku permission denied", Toast.LENGTH_SHORT).show()
      }
      updateStatus()
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    try {
      Shizuku.addBinderReceivedListener(binderReceivedListener)
      Shizuku.addBinderDeadListener(binderDeadListener)
      Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
    } catch (_: Throwable) {}

    updateStatus()

    setContent {
      MyApplicationTheme(darkTheme = true) {
        MainScreen(
          isShizukuRunning = isShizukuRunningState.value,
          hasPermission = hasPermissionState.value,
          isOverlayRunning = isOverlayRunningState.value,
          sourcePath = sourcePathState.value,
          fileCount = fileCountState.value,
          onRequestPermission = {
            ShizukuManager.requestPermission()
          },
          onStartOverlay = {
            startOverlay()
          },
          onStopOverlay = {
            stopOverlay()
          },
          onRefreshFiles = {
            updateStatus()
          }
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    updateStatus()
  }

  override fun onDestroy() {
    super.onDestroy()
    try {
      Shizuku.removeBinderReceivedListener(binderReceivedListener)
      Shizuku.removeBinderDeadListener(binderDeadListener)
      Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    } catch (_: Throwable) {}
  }

  private fun updateStatus() {
    val running = ShizukuManager.isShizukuRunning()
    val permitted = ShizukuManager.hasPermission()
    isShizukuRunningState.value = running
    hasPermissionState.value = permitted
    isOverlayRunningState.value = OverlayService.isRunning

    val sourceDir = ShizukuManager.getSourceDirectory(this)
    sourcePathState.value = sourceDir.absolutePath
    val files = ShizukuManager.getSourceFiles(this)
    fileCountState.value = files.size
  }

  private fun startOverlay() {
    if (!Settings.canDrawOverlays(this)) {
      Toast.makeText(this, "Please enable Overlay permission for the floating button", Toast.LENGTH_LONG).show()
      val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:$packageName")
      )
      startActivity(intent)
      return
    }

    val intent = Intent(this, OverlayService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      startForegroundService(intent)
    } else {
      startService(intent)
    }
    isOverlayRunningState.value = true
    Toast.makeText(this, "Floating button started", Toast.LENGTH_SHORT).show()
  }

  private fun stopOverlay() {
    val intent = Intent(this, OverlayService::class.java)
    stopService(intent)
    isOverlayRunningState.value = false
    Toast.makeText(this, "Floating button stopped", Toast.LENGTH_SHORT).show()
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
  isShizukuRunning: Boolean,
  hasPermission: Boolean,
  isOverlayRunning: Boolean,
  sourcePath: String,
  fileCount: Int,
  onRequestPermission: () -> Unit,
  onStartOverlay: () -> Unit,
  onStopOverlay: () -> Unit,
  onRefreshFiles: () -> Unit
) {
  val bgColor = Color(0xFF0C130E)
  val cardBg = Color(0xFF142018)
  val borderColor = Color(0xFF233B2B)
  val cyberGreen = Color(0xFF00FF66)

  Scaffold(
    containerColor = bgColor,
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
              shape = CircleShape,
              color = cyberGreen,
              modifier = Modifier.size(8.dp)
            ) {}
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = ".BABA PNAL",
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              fontSize = 19.sp,
              color = cyberGreen
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Color(0xFF09100B)
        ),
        actions = {
          IconButton(
            onClick = onRefreshFiles,
            modifier = Modifier.testTag("refresh_button")
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Refresh Status",
              tint = cyberGreen
            )
          }
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(bgColor)
        .padding(horizontal = 16.dp, vertical = 14.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // 1. Shizuku Status Card
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("shizuku_status_card"),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.2.dp, borderColor),
        colors = CardDefaults.cardColors(
          containerColor = cardBg
        )
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "> Shizuku Status",
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = cyberGreen
          )

          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            Surface(
              shape = CircleShape,
              color = if (isShizukuRunning) cyberGreen else Color(0xFFEF4444),
              modifier = Modifier.size(10.dp)
            ) {}
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (isShizukuRunning) "Shizuku Connected" else "Shizuku Disconnected (Not Running)",
              fontSize = 14.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Medium,
              color = Color.White
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            Surface(
              shape = CircleShape,
              color = if (hasPermission) cyberGreen else Color(0xFFF59E0B),
              modifier = Modifier.size(10.dp)
            ) {}
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (hasPermission) "Permission Granted" else "Permission Not Granted",
              fontSize = 14.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Medium,
              color = Color.White
            )
          }

          if (isShizukuRunning && !hasPermission) {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
              onClick = onRequestPermission,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("request_permission_button"),
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = cyberGreen,
                contentColor = Color.Black
              )
            ) {
              Text("Grant Shizuku Permission", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
          }
        }
      }

      // 2. Folder Info Card
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("folder_info_card"),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.2.dp, borderColor),
        colors = CardDefaults.cardColors(
          containerColor = cardBg
        )
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Folder,
              contentDescription = "Folder",
              tint = cyberGreen,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "> Folder Paths",
              fontSize = 14.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.SemiBold,
              color = cyberGreen
            )
          }

          Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
              text = "Source 'file' folder:",
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace,
              color = Color(0xFF9CA3AF)
            )
            Text(
              text = sourcePath,
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Medium,
              color = Color(0xFF00F0FF)
            )
          }

          Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
              text = "Target folder:",
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace,
              color = Color(0xFF9CA3AF)
            )
            Text(
              text = ShizukuManager.TARGET_PACKAGE_PATH,
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Medium,
              color = cyberGreen
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Files / Folders: $fileCount",
              fontSize = 13.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }
        }
      }

      // 3. Start and Stop Buttons (Primary Controls)
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("control_card"),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.2.dp, borderColor),
        colors = CardDefaults.cardColors(
          containerColor = cardBg
        )
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = "> Floating Button Control",
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = cyberGreen
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Button(
              onClick = onStartOverlay,
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("start_button"),
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = cyberGreen,
                contentColor = Color.Black
              )
            ) {
              Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Start")
              Spacer(modifier = Modifier.width(6.dp))
              Text("Start", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            Button(
              onClick = onStopOverlay,
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("stop_button"),
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFEF4444),
                contentColor = Color.White
              )
            ) {
              Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop")
              Spacer(modifier = Modifier.width(6.dp))
              Text("Stop", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
          }

          if (isOverlayRunning) {
            Text(
              text = "> Floating button active",
              color = cyberGreen,
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Medium
            )
          }
        }
      }
    }
  }
}
