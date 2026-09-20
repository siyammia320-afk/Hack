package com.example

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocSpooferScreen(
  isShizukuRunning: Boolean,
  hasShizukuPermission: Boolean,
  onRequestShizukuPermission: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  // Flat Matte Dark Palette (Zero Glow, No Lag)
  val darkBg = Color(0xFF10151C)
  val cardBg = Color(0xFF161E28)
  val cardSubBg = Color(0xFF1B2532)
  val topBarBg = Color(0xFF141A22)
  val borderColor = Color(0xFF243140)
  val accentGreen = Color(0xFF4CAF50)
  val accentAmber = Color(0xFFD49C1E)
  val textColor = Color(0xFFECEFF1)
  val textSubColor = Color(0xFF90A4AE)

  val isConnected = isShizukuRunning && hasShizukuPermission

  val preset = PresetRepository.guineaPreset

  var isApplying by remember { mutableStateOf(false) }
  var isResetting by remember { mutableStateOf(false) }
  var actionLogs by remember { mutableStateOf<List<String>>(emptyList()) }

  var currentStatus by remember {
    mutableStateOf(ShizukuSystemController.readCurrentSystemStatus())
  }

  fun refreshStatus() {
    currentStatus = ShizukuSystemController.readCurrentSystemStatus()
  }

  LaunchedEffect(isConnected) {
    if (isConnected) {
      refreshStatus()
    }
  }

  fun startSetup() {
    if (!isConnected) {
      onRequestShizukuPermission()
      return
    }

    isApplying = true
    scope.launch {
      val result = ShizukuSystemController.applyGuineaPreset(
        context = context,
        preset = preset
      )

      withContext(Dispatchers.Main) {
        isApplying = false
        if (result.isSuccess) {
          actionLogs = result.getOrDefault(emptyList())
          Toast.makeText(context, "ফোন সম্পূর্ণ +224 গিনিতে পরিবর্তন সম্পন্ন হয়েছে", Toast.LENGTH_SHORT).show()
          refreshStatus()
        } else {
          val err = result.exceptionOrNull()?.message ?: "ব্যর্থ হয়েছে"
          actionLogs = listOf("Error: $err")
          Toast.makeText(context, err, Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  fun resetSettings() {
    if (!isConnected) {
      onRequestShizukuPermission()
      return
    }
    isResetting = true
    scope.launch {
      val result = ShizukuSystemController.resetToAutoSettings(context)
      withContext(Dispatchers.Main) {
        isResetting = false
        if (result.isSuccess) {
          actionLogs = result.getOrDefault(emptyList())
          Toast.makeText(context, "স্বাভাবিক সেটিংসে রিস্টোর হয়েছে", Toast.LENGTH_SHORT).show()
          refreshStatus()
        } else {
          val err = result.exceptionOrNull()?.message ?: "রিস্টোর ব্যর্থ"
          Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  Scaffold(
    containerColor = darkBg,
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = if (isConnected) "Full +224 Spoofer" else "Shizuku Connection Required",
              color = textColor,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            Text(
              text = if (isConnected) "Guinea (+224) System Active" else "কানেক্ট করে অ্যাপ আনলক করুন",
              color = if (isConnected) accentGreen else accentAmber,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace
            )
          }
        },
        actions = {
          if (isConnected) {
            IconButton(onClick = { refreshStatus() }) {
              Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = textColor)
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarBg)
      )
    }
  ) { paddingValues ->

    // Shizuku কানেক্ট না থাকলে শুধুমাত্র কানেক্ট স্ক্রিন দেখাবে (কোনো বাটন বা কন্টেন্ট থাকবে না)
    if (!isConnected) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(20.dp),
        contentAlignment = Alignment.Center
      ) {
        Card(
          colors = CardDefaults.cardColors(containerColor = cardBg),
          border = BorderStroke(1.dp, borderColor),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Box(
              modifier = Modifier
                .size(64.dp)
                .background(Color(0xFF1E2632), CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = accentAmber,
                modifier = Modifier.size(32.dp)
              )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
              text = "Shizuku সংযোগ প্রয়োজন",
              color = textColor,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = if (!isShizukuRunning)
                "Shizuku সার্ভিসটি ব্যাকগ্রাউন্ডে চালু নেই। অনুগ্রহ করে Shizuku অ্যাপ ওপেন করে Wireless Debugging বা পিসি দিয়ে Shizuku চালু করুন।"
              else
                "Shizuku চালু আছে কিন্তু অ্যাপে পারমিশন দেওয়া হয়নি। নিচে কানেক্ট বাটনে চাপ দিয়ে পারমিশন দিন।",
              color = textSubColor,
              fontSize = 13.sp,
              textAlign = TextAlign.Center,
              lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
              onClick = { onRequestShizukuPermission() },
              colors = ButtonDefaults.buttonColors(containerColor = accentAmber),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("connect_shizuku_button")
            ) {
              Icon(Icons.Default.Link, contentDescription = null, tint = Color.Black)
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (isShizukuRunning) "Connect Shizuku (Authorize)" else "Shizuku Connect করুন",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
              )
            }
          }
        }
      }
    } else {
      // Shizuku কানেক্ট হলে রিয়েল-টাইমে অ্যাপের সমস্ত ফিচার ও দুটি বাটন ওপেন হবে
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // ১. Shizuku Connected Status Bar
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, borderColor),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(10.dp)
                    .background(accentGreen, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(
                    text = "Shizuku Connected",
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                  )
                  Text(
                    text = "ADB রুট-লেস সিস্টেম প্রিভিলেজ সক্রিয়",
                    color = accentGreen,
                    fontSize = 11.sp
                  )
                }
              }

              Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF1E3A2F),
                border = BorderStroke(0.5.dp, accentGreen)
              ) {
                Text(
                  text = "AUTHORIZED",
                  color = accentGreen,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
              }
            }
          }
        }

        // ২. লক্ষ্যমাত্রা: ফুল +224 Guinea কার্ড
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = cardSubBg),
            border = BorderStroke(1.5.dp, accentGreen),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(text = preset.flagEmoji, fontSize = 34.sp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column {
                    Text(
                      text = preset.name,
                      color = textColor,
                      fontSize = 17.sp,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = "ডায়াল কোড: ${preset.dialCode} | ISO: ${preset.isoCountryCode}",
                      color = accentAmber,
                      fontSize = 13.sp,
                      fontWeight = FontWeight.SemiBold,
                      fontFamily = FontFamily.Monospace
                    )
                  }
                }

                Surface(
                  shape = RoundedCornerShape(4.dp),
                  color = Color(0xFF1E3A2F),
                  border = BorderStroke(0.5.dp, accentGreen)
                ) {
                  Text(
                    text = "FULL +224",
                    color = accentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                  )
                }
              }

              HorizontalDivider(color = borderColor, modifier = Modifier.padding(vertical = 10.dp))

              Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("📍 GPS লোকেশন:", color = textSubColor, fontSize = 12.sp)
                  Text("${preset.capitalCity} (${preset.latitude}, ${preset.longitude})", color = textColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("🕒 সিস্টেম টাইম জোন:", color = textSubColor, fontSize = 12.sp)
                  Text(preset.timezone, color = textColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("🌐 সিস্টেম কান্ট্রি ও রিজিওন:", color = textSubColor, fontSize = 12.sp)
                  Text("${preset.isoCountryCode} (${preset.dialCode})", color = textColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("📶 সিম / নেটওয়ার্ক কান্ট্রি:", color = textSubColor, fontSize = 12.sp)
                  Text("GN (MCC: 611 - Guinea)", color = textColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("🗣️ ফোনের সিস্টেম ভাষা:", color = textSubColor, fontSize = 12.sp)
                  Text(preset.languageName, color = accentGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
              }
            }
          }
        }

        // ৩. বর্তমান ফোনের স্ট্যাটাস
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, borderColor),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = "ফোনের বর্তমান লাইভ স্ট্যাটাস:",
                color = accentAmber,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Timezone: ${currentStatus.currentTimezone}",
                color = textColor,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace
              )
              Text(
                text = "Country & Region: ${currentStatus.currentCountry} (${currentStatus.currentLocale})",
                color = textSubColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
              )
              Text(
                text = "SIM/Net Country: ${currentStatus.telephonySimCountry}",
                color = textSubColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
              )
              Text(
                text = "Language: ${currentStatus.currentLanguage}",
                color = textSubColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
              )
              Text(
                text = "Current Time: ${currentStatus.formattedCurrentTime}",
                color = textSubColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
              )
            }
          }
        }

        // ৪. দুটি মূল বাটন: Setup Start & Reset
        item {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // বাটন ১: Setup Start
            Button(
              onClick = { startSetup() },
              enabled = !isApplying && !isResetting,
              colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("setup_start_button")
            ) {
              if (isApplying) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ফুল সেটআপ প্রয়োগ হচ্ছে...", color = Color.Black, fontWeight = FontWeight.Bold)
              } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  "Setup Start (Full +224 Guinea)",
                  color = Color.Black,
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp
                )
              }
            }

            // বাটন ২: Reset
            OutlinedButton(
              onClick = { resetSettings() },
              enabled = !isApplying && !isResetting,
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(1.dp, borderColor),
              colors = ButtonDefaults.outlinedButtonColors(containerColor = cardBg),
              modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("reset_button")
            ) {
              if (isResetting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = textSubColor, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("স্বাভাবিক অবস্থায় রিস্টোর হচ্ছে...", color = textSubColor)
              } else {
                Icon(Icons.Default.Restore, contentDescription = null, tint = textSubColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  "Reset (স্বাভাবিক অবস্থায় ফিরুন)",
                  color = textSubColor,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
          }
        }

        // ৫. Shizuku লাইভ শেল লগ
        if (actionLogs.isNotEmpty()) {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1015)),
              border = BorderStroke(1.dp, borderColor),
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text(
                  text = "> Shizuku Output",
                  color = accentAmber,
                  fontSize = 11.sp,
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                actionLogs.forEach { log ->
                  Text(
                    text = log,
                    color = if (log.startsWith("✓") || log.startsWith("🎉")) accentGreen else textSubColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                  )
                }
              }
            }
          }
        }

        item {
          Spacer(modifier = Modifier.height(16.dp))
        }
      }
    }
  }
}
