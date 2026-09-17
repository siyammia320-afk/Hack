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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

  private var isShizukuRunningState = mutableStateOf(false)
  private var hasPermissionState = mutableStateOf(false)
  private var isOverlayRunningState = mutableStateOf(false)
  private var sourcePathState = mutableStateOf("")
  private var fileCountState = mutableIntStateOf(0)
  private var statusMessageState = mutableStateOf("")

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
        Toast.makeText(this, "Shizuku পারমিশন অনুমোদিত হয়েছে", Toast.LENGTH_SHORT).show()
      } else {
        Toast.makeText(this, "Shizuku পারমিশন দেওয়া হয়নি", Toast.LENGTH_SHORT).show()
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
      MyApplicationTheme {
        MainScreen(
          isShizukuRunning = isShizukuRunningState.value,
          hasPermission = hasPermissionState.value,
          isOverlayRunning = isOverlayRunningState.value,
          sourcePath = sourcePathState.value,
          fileCount = fileCountState.value,
          statusMessage = statusMessageState.value,
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
          },
          onDirectTransfer = {
            executeTransfer()
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
      Toast.makeText(this, "ফ্লোটিং বাটনের জন্য Overlay পারমিশন চালু করুন", Toast.LENGTH_LONG).show()
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
    Toast.makeText(this, "ফ্লোটিং বাটন চালু হয়েছে", Toast.LENGTH_SHORT).show()
  }

  private fun stopOverlay() {
    val intent = Intent(this, OverlayService::class.java)
    stopService(intent)
    isOverlayRunningState.value = false
    Toast.makeText(this, "ফ্লোটিং বাটন বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show()
  }

  private fun executeTransfer() {
    statusMessageState.value = "কপি হচ্ছে..."
    val coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main)
    coroutineScope.launch {
      val result = withContext(Dispatchers.IO) {
        ShizukuManager.copyFilesToTarget(this@MainActivity)
      }
      statusMessageState.value = result.second
      Toast.makeText(this@MainActivity, result.second, Toast.LENGTH_LONG).show()
      updateStatus()
    }
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
  statusMessage: String,
  onRequestPermission: () -> Unit,
  onStartOverlay: () -> Unit,
  onStopOverlay: () -> Unit,
  onRefreshFiles: () -> Unit,
  onDirectTransfer: () -> Unit
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Data Transfer",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
          )
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        ),
        actions = {
          IconButton(
            onClick = onRefreshFiles,
            modifier = Modifier.testTag("refresh_button")
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Refresh Status"
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
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // 1. Shizuku Status Card
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("shizuku_status_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "Shizuku স্ট্যাটাস",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            Surface(
              shape = CircleShape,
              color = if (isShizukuRunning) Color(0xFF10B981) else Color(0xFFEF4444),
              modifier = Modifier.size(10.dp)
            ) {}
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (isShizukuRunning) "Shizuku কানেক্টেড" else "Shizuku ডিসকানেক্টেড (চালু নেই)",
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            Surface(
              shape = CircleShape,
              color = if (hasPermission) Color(0xFF10B981) else Color(0xFFF59E0B),
              modifier = Modifier.size(10.dp)
            ) {}
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (hasPermission) "পারমিশন অনুমোদিত" else "পারমিশন দেওয়া হয়নি",
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium
            )
          }

          if (isShizukuRunning && !hasPermission) {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
              onClick = onRequestPermission,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("request_permission_button"),
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00796B)
              )
            ) {
              Text("Shizuku পারমিশন দিন")
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
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
              tint = Color(0xFF00796B),
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "ফোল্ডার পাথ",
              fontSize = 15.sp,
              fontWeight = FontWeight.SemiBold
            )
          }

          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "সোর্স 'file' ফোল্ডার:",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = sourcePath,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              color = Color(0xFF0284C7)
            )
          }

          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "টার্গেট ফোল্ডার:",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = ShizukuManager.TARGET_PACKAGE_PATH,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              color = Color(0xFF10B981)
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "ফাইল সংখ্যা: $fileCount টি",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold
            )
            OutlinedButton(
              onClick = onRefreshFiles,
              modifier = Modifier.testTag("check_files_button")
            ) {
              Text("ফাইল চেক করুন", fontSize = 12.sp)
            }
          }
        }
      }

      // 3. Start and Stop Buttons (Primary Controls)
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("control_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = "ফ্লোটিং বাটন কন্ট্রোল",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
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
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF10B981)
              )
            ) {
              Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Start")
              Spacer(modifier = Modifier.width(6.dp))
              Text("Start", fontWeight = FontWeight.Bold)
            }

            Button(
              onClick = onStopOverlay,
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("stop_button"),
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFEF4444)
              )
            ) {
              Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop")
              Spacer(modifier = Modifier.width(6.dp))
              Text("Stop", fontWeight = FontWeight.Bold)
            }
          }

          if (isOverlayRunning) {
            Text(
              text = "ফ্লোটিং বাটন চালু আছে (স্ক্রিনে দেখা যাচ্ছে)",
              color = Color(0xFF10B981),
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }
      }

      // 4. Direct Transfer Option (Shortcut)
      Button(
        onClick = onDirectTransfer,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("direct_transfer_button"),
        colors = ButtonDefaults.buttonColors(
          containerColor = Color(0xFF00796B)
        )
      ) {
        Icon(imageVector = Icons.Default.Download, contentDescription = "Transfer")
        Spacer(modifier = Modifier.width(8.dp))
        Text("সরাসরি ডাউনলোড / রিপ্লেস করুন")
      }

      // Status message display
      if (statusMessage.isNotEmpty()) {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("status_message_card"),
          shape = RoundedCornerShape(8.dp),
          colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F172A)
          )
        ) {
          Text(
            text = statusMessage,
            color = Color(0xFF38BDF8),
            fontSize = 13.sp,
            modifier = Modifier.padding(12.dp)
          )
        }
      }
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}
