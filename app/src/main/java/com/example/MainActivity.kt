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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
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
      MyApplicationTheme(darkTheme = true) {
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
          },
          onDeleteFiles = {
            executeDelete()
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

  private fun executeTransfer() {
    statusMessageState.value = "Copying..."
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

  private fun executeDelete() {
    statusMessageState.value = "Deleting..."
    val coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main)
    coroutineScope.launch {
      val result = withContext(Dispatchers.IO) {
        ShizukuManager.deleteFilesFromTarget(this@MainActivity)
      }
      statusMessageState.value = result.second
      Toast.makeText(this@MainActivity, result.second, Toast.LENGTH_LONG).show()
      updateStatus()

      if (OverlayService.isRunning) {
        kotlinx.coroutines.delay(5000L)
        stopOverlay()
      }
    }
  }
}

/**
 * প্রচুর পরিমাণে হ্যাকার লাইন ও অটো কালার চেঞ্জিং অ্যানিমেশন ক্যানভাস
 */
@Composable
fun HackerCyberLinesBackground() {
  val transition = rememberInfiniteTransition(label = "dense_cyber_lines")

  // কালার সাইকেল অ্যানিমেশন (সব লাইনের কালার স্বয়ংক্রিয়ভাবে চক্রাকারে বদলাবে)
  val colorPhase by transition.animateFloat(
    initialValue = 0f,
    targetValue = 6f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 6000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "color_phase"
  )

  // উপর থেকে নিচে চলমান স্ক্যানার বিম ১
  val scanY1 by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2400, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "scan_1"
  )

  // নিচ থেকে উপরে চলমান স্ক্যানার বিম ২
  val scanY2 by transition.animateFloat(
    initialValue = 1f,
    targetValue = 0f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 3600, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "scan_2"
  )

  // ডাটা লাইনগুলোর অনবরত নিচে পড়ার স্পিড অ্যানিমেশন
  val streamProgress by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1800, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "stream_progress"
  )

  // র্যান্ডম ও অটো-চেঞ্জিং সাইবার কালার প্যালেট
  val cyberPalette = listOf(
    Color(0xFF00FF66), // Neon Matrix Green
    Color(0xFF00F0FF), // Neon Cyan
    Color(0xFFFF007F), // Electric Pink / Magenta
    Color(0xFFB026FF), // Cyber Purple
    Color(0xFFFFE500), // Laser Gold / Yellow
    Color(0xFF0088FF), // Neon Blue
    Color(0xFFFF3355)  // Crimson Red
  )

  Canvas(modifier = Modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height

    val baseColorIndex = colorPhase.toInt() % cyberPalette.size
    val activeColor = cyberPalette[baseColorIndex]
    val secondaryColor = cyberPalette[(baseColorIndex + 2) % cyberPalette.size]

    // ১. ব্যাকগ্রাউন্ড সাইবার গ্রিড লাইন (অটোমেটিক কালার চেঞ্জিং সহ)
    val gridStep = 36.dp.toPx()
    var gx = 0f
    while (gx < w) {
      drawLine(
        color = activeColor.copy(alpha = 0.07f),
        start = Offset(gx, 0f),
        end = Offset(gx, h),
        strokeWidth = 1f
      )
      gx += gridStep
    }
    var gy = 0f
    while (gy < h) {
      drawLine(
        color = secondaryColor.copy(alpha = 0.07f),
        start = Offset(0f, gy),
        end = Offset(w, gy),
        strokeWidth = 1f
      )
      gy += gridStep
    }

    // ২. উজ্জ্বল ডুয়াল স্ক্যানার লেজার বিম
    val beamY1 = scanY1 * h
    drawLine(
      color = activeColor.copy(alpha = 0.85f),
      start = Offset(0f, beamY1),
      end = Offset(w, beamY1),
      strokeWidth = 3f
    )
    drawLine(
      color = activeColor.copy(alpha = 0.35f),
      start = Offset(0f, beamY1 - 5f),
      end = Offset(w, beamY1 - 5f),
      strokeWidth = 1.5f
    )

    val beamY2 = scanY2 * h
    drawLine(
      color = secondaryColor.copy(alpha = 0.70f),
      start = Offset(0f, beamY2),
      end = Offset(w, beamY2),
      strokeWidth = 2.5f
    )

    // ৩. প্রচুর পরিমাণে (২৮টি) উল্লম্ব বহু-রঙিন হ্যাকিং ডাটা লাইন (Dense Multi-Color Stream Rain)
    val totalLines = 28
    for (i in 0 until totalLines) {
      val colX = (w / (totalLines + 1)) * (i + 1)
      val speedFactor = 0.9f + ((i % 5) * 0.35f)
      val lineProgress = ((streamProgress * speedFactor + (i * 0.12f)) % 1f)
      val headY = lineProgress * (h + 300f) - 150f
      val streamLength = (80 + ((i * 17) % 80)).dp.toPx()

      // প্রতি লাইনের আলাদা র্যান্ডম কালার + অটো সাইকেল
      val lineColor = cyberPalette[(i + baseColorIndex) % cyberPalette.size]

      // পেছনের বডি লাইন
      drawLine(
        color = lineColor.copy(alpha = 0.65f),
        start = Offset(colX, (headY - streamLength).coerceAtLeast(0f)),
        end = Offset(colX, headY.coerceAtMost(h)),
        strokeWidth = 2.4f
      )

      // লাইনের উজ্জ্বল সাদা মাথা (Bright Glowing Tip)
      if (headY in 0f..h) {
        drawLine(
          color = Color.White,
          start = Offset(colX, (headY - 14f).coerceAtLeast(0f)),
          end = Offset(colX, headY),
          strokeWidth = 3.6f
        )
      }
    }
  }
}

/**
 * উপরে দৃশ্যমান লাইভ হ্যাকার স্ক্যানার বার (অটো কালার চেঞ্জিং সহ)
 */
@Composable
fun LiveHackerScanBar() {
  val transition = rememberInfiniteTransition(label = "scan_bar")
  val barOffset by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1500, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bar_offset"
  )

  val colorCycle by transition.animateFloat(
    initialValue = 0f,
    targetValue = 6f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 5000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "color_cycle"
  )

  val cyberPalette = listOf(
    Color(0xFF00FF66),
    Color(0xFF00F0FF),
    Color(0xFFFF007F),
    Color(0xFFB026FF),
    Color(0xFFFFE500),
    Color(0xFF0088FF)
  )
  val currentColor = cyberPalette[colorCycle.toInt() % cyberPalette.size]

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(28.dp)
      .background(Color(0xFF050E09))
      .padding(horizontal = 12.dp),
    contentAlignment = Alignment.CenterStart
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height

      // বেস লাইন
      drawLine(
        color = currentColor.copy(alpha = 0.35f),
        start = Offset(0f, h / 2),
        end = Offset(w, h / 2),
        strokeWidth = 1.5f
      )

      // নড়াচড়া করা উজ্জ্বল লেজার কার্সার
      val cursorX = barOffset * w
      val beamWidth = 70.dp.toPx()
      drawLine(
        color = currentColor,
        start = Offset((cursorX - beamWidth).coerceAtLeast(0f), h / 2),
        end = Offset((cursorX + beamWidth).coerceAtMost(w), h / 2),
        strokeWidth = 4f
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "● LIVE SYSTEM PROTOCOL",
        color = currentColor,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "[ SHIZUKU ACTIVE ]",
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace
      )
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
  onDirectTransfer: () -> Unit,
  onDeleteFiles: () -> Unit
) {
  val cyberBgColor = Color(0xFF030705)
  val cyberCardBg = Color(0xD808120B) // আংশিক স্বচ্ছ ডার্ক যাতে পেছনের মুভিং লাইন দেখা যায়
  val cyberBorderColor = Color(0xFF00FF66).copy(alpha = 0.45f)
  val cyberGreen = Color(0xFF00FF66)

  Scaffold(
    containerColor = cyberBgColor,
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
          containerColor = Color(0xFF050C08)
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
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(cyberBgColor)
    ) {
      // ১. পেছনের সম্পূর্ণ স্ক্রিন জুড়ে প্রচুর হ্যাকিং লাইন ও কালার চেঞ্জিং অ্যানিমেশন
      HackerCyberLinesBackground()

      Column(
        modifier = Modifier.fillMaxSize()
      ) {
        // ২. ওপরের লাইভ লেজার মুভিং স্ক্যানার বার
        LiveHackerScanBar()

        Column(
          modifier = Modifier
            .fillMaxSize()
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
            border = BorderStroke(1.2.dp, cyberBorderColor),
            colors = CardDefaults.cardColors(
              containerColor = cyberCardBg
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
            border = BorderStroke(1.2.dp, cyberBorderColor),
            colors = CardDefaults.cardColors(
              containerColor = cyberCardBg
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Files: $fileCount",
                  fontSize = 13.sp,
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
                )
                OutlinedButton(
                  onClick = onRefreshFiles,
                  shape = RoundedCornerShape(8.dp),
                  border = BorderStroke(1.dp, cyberGreen),
                  modifier = Modifier.testTag("check_files_button")
                ) {
                  Text("Check Files", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = cyberGreen)
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
            border = BorderStroke(1.2.dp, cyberBorderColor),
            colors = CardDefaults.cardColors(
              containerColor = cyberCardBg
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
                  text = "> Floating button & Aim dot active",
                  color = cyberGreen,
                  fontSize = 12.sp,
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Medium
                )
              }
            }
          }

          // 4. Direct Transfer Option
          Button(
            onClick = onDirectTransfer,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("direct_transfer_button"),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.2.dp, cyberGreen),
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF092216),
              contentColor = cyberGreen
            )
          ) {
            Icon(imageVector = Icons.Default.Download, contentDescription = "Transfer", tint = cyberGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Direct Download / Replace", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
          }

          // 5. Delete Files Option
          Button(
            onClick = onDeleteFiles,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("delete_files_button"),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.2.dp, Color(0xFFFF4444)),
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF220A0A),
              contentColor = Color(0xFFFF4444)
            )
          ) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF4444))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete Files", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
          }

          // Status message display
          if (statusMessage.isNotEmpty()) {
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("status_message_card"),
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.5f)),
              colors = CardDefaults.cardColors(
                containerColor = Color(0xFF07141E)
              )
            ) {
              Text(
                text = "> $statusMessage",
                color = Color(0xFF00F0FF),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(12.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}
