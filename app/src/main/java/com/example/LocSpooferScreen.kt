package com.example

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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

  // Matte Dark Palette (Zero Glow)
  val darkBg = Color(0xFF10151C)
  val cardBg = Color(0xFF161E28)
  val cardSubBg = Color(0xFF1B2532)
  val topBarBg = Color(0xFF141A22)
  val borderColor = Color(0xFF243140)
  val accentGreen = Color(0xFF4CAF50)
  val accentAmber = Color(0xFFD49C1E)
  val textColor = Color(0xFFECEFF1)
  val textSubColor = Color(0xFF90A4AE)

  var selectedPreset by remember { mutableStateOf(PresetRepository.defaultPreset) }
  var enableLocation by remember { mutableStateOf(true) }
  var enableTimezone by remember { mutableStateOf(true) }
  var enableRegion by remember { mutableStateOf(true) }

  var isApplying by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf("রেডি - পছন্দের দেশ সিলেক্ট করে প্রয়োগ করুন") }
  var actionLogs by remember { mutableStateOf<List<String>>(emptyList()) }

  var currentStatus by remember {
    mutableStateOf(ShizukuSystemController.readCurrentSystemStatus())
  }

  fun refreshStatus() {
    currentStatus = ShizukuSystemController.readCurrentSystemStatus()
  }

  LaunchedEffect(Unit) {
    refreshStatus()
  }

  fun applySelectedCountry() {
    if (!isShizukuRunning) {
      Toast.makeText(context, "Shizuku চালু নেই! আগে Shizuku স্টার্ট করুন।", Toast.LENGTH_SHORT).show()
      return
    }
    if (!hasShizukuPermission) {
      onRequestShizukuPermission()
      return
    }

    isApplying = true
    statusMessage = "${selectedPreset.name} সেটিংস প্রয়োগ করা হচ্ছে..."
    scope.launch {
      val result = ShizukuSystemController.applyCountryPreset(
        context = context,
        preset = selectedPreset,
        applyLocation = enableLocation,
        applyTimezone = enableTimezone,
        applyRegion = enableRegion
      )

      withContext(Dispatchers.Main) {
        isApplying = false
        if (result.isSuccess) {
          actionLogs = result.getOrDefault(emptyList())
          statusMessage = "সফল! ${selectedPreset.name} কান্ট্রি ও টাইমজোন সেট হয়েছে।"
          Toast.makeText(context, "${selectedPreset.name} এক্টিভ করা হয়েছে", Toast.LENGTH_SHORT).show()
          refreshStatus()
        } else {
          val err = result.exceptionOrNull()?.message ?: "ব্যর্থ হয়েছে"
          statusMessage = "ত্রুটি: $err"
          actionLogs = listOf("Error: $err")
          Toast.makeText(context, err, Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  fun resetSettings() {
    if (!isShizukuRunning || !hasShizukuPermission) {
      onRequestShizukuPermission()
      return
    }
    isApplying = true
    statusMessage = "ডিফল্ট সেটিংস রিস্টোর করা হচ্ছে..."
    scope.launch {
      val result = ShizukuSystemController.resetToAutoSettings(context)
      withContext(Dispatchers.Main) {
        isApplying = false
        if (result.isSuccess) {
          actionLogs = result.getOrDefault(emptyList())
          statusMessage = "ডিফল্ট রিস্টোর সম্পন্ন হয়েছে।"
          Toast.makeText(context, "স্বাভাবিক সেটিংসে রিস্টোর হয়েছে", Toast.LENGTH_SHORT).show()
          refreshStatus()
        } else {
          val err = result.exceptionOrNull()?.message ?: "রিস্টোর ব্যর্থ"
          statusMessage = "ত্রুটি: $err"
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
              text = "Location & Region Spoofer",
              color = textColor,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            Text(
              text = "Shizuku Powered Auto Changer",
              color = textSubColor,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace
            )
          }
        },
        actions = {
          IconButton(onClick = { refreshStatus() }) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = textColor)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarBg)
      )
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 14.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // ১. Shizuku Status Card
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .background(
                    if (isShizukuRunning && hasShizukuPermission) accentGreen else Color(0xFFFF5252),
                    CircleShape
                  )
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = if (isShizukuRunning && hasShizukuPermission) "Shizuku Active (Authorized)"
                  else if (isShizukuRunning) "Shizuku Running (Needs Permission)"
                  else "Shizuku Disconnected",
                  color = textColor,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace
                )
                Text(
                  text = if (hasShizukuPermission) "ADB সিস্টেম শেল কমান্ড রেডি" else "পারমিশন গ্রান্ট করতে চাপুন",
                  color = textSubColor,
                  fontSize = 11.sp
                )
              }
            }

            if (!hasShizukuPermission) {
              Button(
                onClick = onRequestShizukuPermission,
                colors = ButtonDefaults.buttonColors(containerColor = accentAmber),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(34.dp)
              ) {
                Text("Authorize", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      // ২. বর্তমান ফোনের অবস্থা (Current Live Status)
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = cardBg),
          border = BorderStroke(1.dp, borderColor),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "বর্তমান ফোনের সেটিংস",
                color = accentGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
              Surface(
                color = if (currentStatus.isAutoTimezone) Color(0xFF1E3A2F) else Color(0xFF3E2F1E),
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  text = if (currentStatus.isAutoTimezone) "Auto Timezone: ON" else "Auto Timezone: OFF",
                  color = if (currentStatus.isAutoTimezone) accentGreen else accentAmber,
                  fontSize = 10.sp,
                  fontFamily = FontFamily.Monospace,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
              text = "Timezone: ${currentStatus.currentTimezone}",
              color = textColor,
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace
            )
            Text(
              text = "Country & Locale: ${currentStatus.currentCountry} (${currentStatus.currentLocale})",
              color = textSubColor,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace
            )
            Text(
              text = "Local Time: ${currentStatus.formattedCurrentTime}",
              color = textSubColor,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace
            )
          }
        }
      }

      // ৩. টার্গেট কান্ট্রি সিলেক্টর (Preset Country List)
      item {
        Text(
          text = "সিলেক্টেড দেশ (এক ক্লিকে ফুল চেঞ্জ):",
          color = textColor,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace
        )
      }

      // নির্বাচিত দেশের বড় কার্ড (Special Spotlight on Guinea +224)
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
                Text(text = selectedPreset.flagEmoji, fontSize = 32.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = selectedPreset.name,
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "ডায়াল কোড: ${selectedPreset.dialCode} | ISO: ${selectedPreset.isoCountryCode}",
                    color = accentAmber,
                    fontSize = 12.sp,
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
                  text = "SELECTED",
                  color = accentGreen,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
              }
            }

            HorizontalDivider(color = borderColor, modifier = Modifier.padding(vertical = 8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Column {
                Text("রাজধানী / শহর:", color = textSubColor, fontSize = 10.sp)
                Text(selectedPreset.capitalCity, color = textColor, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
              }
              Column {
                Text("টাইম জোন:", color = textSubColor, fontSize = 10.sp)
                Text(selectedPreset.timezone, color = textColor, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
              }
              Column {
                Text("জিপিএস কোঅর্ডিনেট:", color = textSubColor, fontSize = 10.sp)
                Text("${selectedPreset.latitude}, ${selectedPreset.longitude}", color = textColor, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
              }
            }
          }
        }
      }

      // ৪. অপশন সুইচ (টগল কী কী চেঞ্জ হবে)
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = cardBg),
          border = BorderStroke(1.dp, borderColor),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = "কী কী পরিবর্তন করতে চান:",
              color = textColor,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Switch 1: GPS Location
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = accentGreen, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("GPS Mock Location (জিপিএস লোকেশন)", color = textColor, fontSize = 12.sp)
              }
              Switch(
                checked = enableLocation,
                onCheckedChange = { enableLocation = it },
                colors = SwitchDefaults.colors(checkedThumbColor = accentGreen, checkedTrackColor = Color(0xFF1E3A2F))
              )
            }

            // Switch 2: Timezone
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = accentAmber, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Time Zone (সিস্টেম টাইম জোন)", color = textColor, fontSize = 12.sp)
              }
              Switch(
                checked = enableTimezone,
                onCheckedChange = { enableTimezone = it },
                colors = SwitchDefaults.colors(checkedThumbColor = accentAmber, checkedTrackColor = Color(0xFF3E2F1E))
              )
            }

            // Switch 3: Region & Country
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Public, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Region & Country (কান্ট্রি কোড ও রিজিওন)", color = textColor, fontSize = 12.sp)
              }
              Switch(
                checked = enableRegion,
                onCheckedChange = { enableRegion = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF64B5F6), checkedTrackColor = Color(0xFF1E2D3E))
              )
            }
          }
        }
      }

      // ৫. অ্যাকশন বাটনসমূহ (এক ক্লিকে অটো চেঞ্জ এবং রিস্টোর)
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = { applySelectedCountry() },
            enabled = !isApplying,
            colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("apply_country_button")
          ) {
            if (isApplying) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
              Spacer(modifier = Modifier.width(8.dp))
              Text("প্রয়োগ হচ্ছে...", color = Color.Black, fontWeight = FontWeight.Bold)
            } else {
              Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                "এক ক্লিকে ${selectedPreset.name} পরিবর্তন করুন",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
            }
          }

          OutlinedButton(
            onClick = { resetSettings() },
            enabled = !isApplying,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier
              .fillMaxWidth()
              .height(42.dp)
              .testTag("reset_button")
          ) {
            Icon(Icons.Default.Restore, contentDescription = null, tint = textSubColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("স্বাভাবিক সেটিংসে রিস্টোর করুন (Reset Auto)", color = textSubColor, fontSize = 12.sp)
          }
        }
      }

      // ৬. অন্যান্য দেশের তালিকা নির্বাচন (Select other Country Presets)
      item {
        Text(
          text = "অন্যান্য দেশ নির্বাচন করুন:",
          color = textColor,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          modifier = Modifier.padding(top = 4.dp)
        )
      }

      items(PresetRepository.presets) { preset ->
        val isSelected = preset.id == selectedPreset.id
        Card(
          colors = CardDefaults.cardColors(
            containerColor = if (isSelected) cardSubBg else cardBg
          ),
          border = BorderStroke(
            if (isSelected) 1.5.dp else 0.5.dp,
            if (isSelected) accentGreen else borderColor
          ),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { selectedPreset = preset }
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(text = preset.flagEmoji, fontSize = 24.sp)
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = preset.name,
                  color = if (isSelected) accentGreen else textColor,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = "${preset.dialCode} • ${preset.timezone} • ${preset.capitalCity}",
                  color = textSubColor,
                  fontSize = 10.5.sp,
                  fontFamily = FontFamily.Monospace
                )
              }
            }

            if (isSelected) {
              Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accentGreen, modifier = Modifier.size(20.dp))
            }
          }
        }
      }

      // ৭. Shizuku লাইভ অ্যাকশন লগ (Logs Terminal)
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
                text = "> Shizuku Shell Output",
                color = accentAmber,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
              )
              Spacer(modifier = Modifier.height(4.dp))
              actionLogs.forEach { log ->
                Text(
                  text = log,
                  color = if (log.startsWith("✓")) accentGreen else textSubColor,
                  fontSize = 10.sp,
                  fontFamily = FontFamily.Monospace
                )
              }
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(20.dp))
      }
    }
  }
}
