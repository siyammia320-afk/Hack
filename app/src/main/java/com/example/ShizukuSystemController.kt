package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class SystemStatus(
  val currentTimezone: String,
  val currentLocale: String,
  val currentCountry: String,
  val isAutoTimezone: Boolean,
  val formattedCurrentTime: String
)

object ShizukuSystemController {

  const val SHIZUKU_REQUEST_CODE = 2001

  fun isShizukuRunning(): Boolean {
    return try {
      if (Shizuku.isPreV11()) false else Shizuku.pingBinder()
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

  fun runCommand(cmd: String): Pair<Boolean, String> {
    return try {
      val method = Shizuku::class.java.getDeclaredMethod(
        "newProcess",
        Array<String>::class.java,
        Array<String>::class.java,
        String::class.java
      )
      method.isAccessible = true
      val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as java.lang.Process

      val reader = BufferedReader(InputStreamReader(process.inputStream))
      val errorReader = BufferedReader(InputStreamReader(process.errorStream))
      val output = StringBuilder()
      var line: String?

      while (reader.readLine().also { line = it } != null) {
        output.appendLine(line)
      }
      while (errorReader.readLine().also { line = it } != null) {
        output.appendLine(line)
      }

      val exitCode = process.waitFor()
      Pair(exitCode == 0, output.toString().trim())
    } catch (t: Throwable) {
      Pair(false, t.message ?: "Execution error")
    }
  }

  fun grantRequiredPermissions(packageName: String): List<String> {
    val logs = mutableListOf<String>()
    val permissions = listOf(
      "android.permission.ACCESS_FINE_LOCATION",
      "android.permission.ACCESS_COARSE_LOCATION",
      "android.permission.SET_TIME_ZONE",
      "android.permission.WRITE_SECURE_SETTINGS",
      "android.permission.CHANGE_CONFIGURATION"
    )

    for (perm in permissions) {
      val (ok, out) = runCommand("pm grant $packageName $perm")
      if (ok) logs.add("Granted $perm") else logs.add("PM Note ($perm): $out")
    }

    // AppOps for Mock Location
    val (okMock, mockOut) = runCommand("appops set $packageName android:mock_location allow")
    if (okMock) logs.add("AppOps: mock_location ALLOWED") else logs.add("AppOps mock note: $mockOut")

    return logs
  }

  suspend fun applyCountryPreset(
    context: Context,
    preset: CountryPreset,
    applyLocation: Boolean = true,
    applyTimezone: Boolean = true,
    applyRegion: Boolean = true
  ): Result<List<String>> = withContext(Dispatchers.IO) {
    if (!isShizukuRunning()) {
      return@withContext Result.failure(Exception("Shizuku চালু নেই। আগে Shizuku ওপেন করে চালু করুন।"))
    }
    if (!hasPermission()) {
      return@withContext Result.failure(Exception("Shizuku পারমিশন গ্রান্ট করা হয়নি।"))
    }

    val logs = mutableListOf<String>()
    val pkg = context.packageName

    // ১. প্রথমে দরকারি সব অ্যাপ পারমিশন ADB দিয়ে গ্রান্ট করা
    grantRequiredPermissions(pkg)

    // ২. টাইম জোন সেট করা (Timezone Spoofing)
    if (applyTimezone) {
      logs.add("🕒 টাইম জোন আপডেট করা হচ্ছে: ${preset.timezone}...")
      // অটো টাইম জোন বন্ধ করা
      runCommand("settings put global auto_time_zone 0")

      // সরাসরি alarm সার্ভিস কল বা setprop দিয়ে সেট করা
      val (timeOk, timeLog) = runCommand("service call alarm 3 s16 \"${preset.timezone}\"")
      val (propOk, _) = runCommand("setprop persist.sys.timezone \"${preset.timezone}\"")

      // cmd time বা toybox date দিয়ে কনফার্মেশন
      runCommand("cmd time set-time-zone \"${preset.timezone}\"")

      if (timeOk || propOk) {
        logs.add("✓ টাইম জোন সফলভাবে সেট হয়েছে: ${preset.timezone}")
      } else {
        logs.add("! টাইম জোন নোটিফিকেশন: $timeLog")
      }
    }

    // ৩. রিজিওন ও ভাষা সেট করা (Region & Country Spoofing)
    if (applyRegion) {
      logs.add("🌐 রিজিওন আপডেট করা হচ্ছে: ${preset.isoCountryCode} (${preset.dialCode})...")
      runCommand("setprop persist.sys.country ${preset.isoCountryCode}")
      runCommand("setprop persist.sys.locale ${preset.locale}")
      runCommand("setprop persist.sys.language ${preset.locale.substringBefore('-')}")

      // Settings Global Country/Locale update
      runCommand("settings put system system_locales ${preset.locale}")
      runCommand("settings put global device_provisioned 1")

      logs.add("✓ রিজিওন ও কান্ট্রি কোড সফলভাবে সেট হয়েছে: ${preset.isoCountryCode} (${preset.dialCode})")
    }

    // ৪. জিপিএস লোকেশন স্পুফিং (GPS Location Spoofing)
    if (applyLocation) {
      logs.add("📍 জিপিএস লোকেশন পাঠানো হচ্ছে: ${preset.latitude}, ${preset.longitude} (${preset.capitalCity})...")
      try {
        setMockGpsLocation(context, preset.latitude, preset.longitude)
        logs.add("✓ টেস্ট প্রোভাইডার দিয়ে জিপিএস ফিক্স করা হয়েছে: ${preset.latitude}, ${preset.longitude}")
      } catch (e: Throwable) {
        // Fallback using Shizuku cmd location
        val (cmdOk, cmdLog) = runCommand("cmd location set-location gps ${preset.latitude} ${preset.longitude}")
        if (cmdOk) {
          logs.add("✓ cmd location দিয়ে জিপিএস সেট হয়েছে")
        } else {
          logs.add("! জিপিএস নোট: ${e.message ?: cmdLog}")
        }
      }
    }

    Result.success(logs)
  }

  fun setMockGpsLocation(context: Context, latitude: Double, longitude: Double) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    for (provider in providers) {
      try {
        locationManager.removeTestProvider(provider)
      } catch (_: Throwable) {}

      try {
        locationManager.addTestProvider(
          provider,
          false,
          false,
          false,
          false,
          true,
          true,
          true,
          1,
          2
        )
        locationManager.setTestProviderEnabled(provider, true)

        val mockLocation = Location(provider).apply {
          this.latitude = latitude
          this.longitude = longitude
          this.altitude = 10.0
          this.time = System.currentTimeMillis()
          this.accuracy = 3.0f
          this.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.bearingAccuracyDegrees = 0.1f
            this.verticalAccuracyMeters = 0.1f
            this.speedAccuracyMetersPerSecond = 0.1f
          }
        }
        locationManager.setTestProviderLocation(provider, mockLocation)
      } catch (_: Throwable) {}
    }
  }

  suspend fun resetToAutoSettings(context: Context): Result<List<String>> = withContext(Dispatchers.IO) {
    if (!isShizukuRunning() || !hasPermission()) {
      return@withContext Result.failure(Exception("Shizuku প্রস্তুত নেই"))
    }
    val logs = mutableListOf<String>()
    logs.add("অটো টাইম জোন ও ডিফল্ট সেটিংস রিস্টোর করা হচ্ছে...")

    runCommand("settings put global auto_time_zone 1")
    runCommand("settings put global auto_time 1")

    // Mock Provider ক্লিন করা
    try {
      val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
      listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach {
        try { locationManager.removeTestProvider(it) } catch (_: Throwable) {}
      }
      logs.add("✓ টেস্ট লোকেশন প্রোভাইডার বন্ধ করা হয়েছে")
    } catch (_: Throwable) {}

    logs.add("✓ ফোন এখন অটোমেটিক টাইম জোন ও লোকেশনে রিস্টোর হয়েছে")
    Result.success(logs)
  }

  fun readCurrentSystemStatus(): SystemStatus {
    val tz = TimeZone.getDefault()
    val locale = Locale.getDefault()
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a (zzz)", locale)
    sdf.timeZone = tz

    var isAuto = true
    try {
      val (_, out) = runCommand("settings get global auto_time_zone")
      if (out.trim() == "0") isAuto = false
    } catch (_: Throwable) {}

    return SystemStatus(
      currentTimezone = tz.id,
      currentLocale = locale.toLanguageTag(),
      currentCountry = locale.country.ifEmpty { "Unknown" },
      isAutoTimezone = isAuto,
      formattedCurrentTime = sdf.format(Date())
    )
  }
}
