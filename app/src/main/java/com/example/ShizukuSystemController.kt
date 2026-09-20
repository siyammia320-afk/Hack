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
  val currentLanguage: String,
  val telephonySimCountry: String,
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

  suspend fun applyGuineaPreset(
    context: Context,
    preset: CountryPreset = PresetRepository.guineaPreset
  ): Result<List<String>> = withContext(Dispatchers.IO) {
    if (!isShizukuRunning()) {
      return@withContext Result.failure(Exception("Shizuku চালু নেই। Shizuku অ্যাপ ওপেন করে স্টার্ট করুন।"))
    }
    if (!hasPermission()) {
      return@withContext Result.failure(Exception("Shizuku পারমিশন দেওয়া হয়নি। Authorize বাটনে চাপুন।"))
    }

    val logs = mutableListOf<String>()
    val pkg = context.packageName

    // ১. প্রয়োজনীয় সব পারমিশন ADB দিয়ে নিশ্চিত করা
    grantRequiredPermissions(pkg)

    // ২. টাইম জোন সেট করা (Africa/Conakry - +224)
    logs.add("🕒 টাইম জোন গিনিতে সেট করা হচ্ছে: ${preset.timezone}...")
    runCommand("settings put global auto_time_zone 0")
    runCommand("service call alarm 3 s16 \"${preset.timezone}\"")
    runCommand("setprop persist.sys.timezone \"${preset.timezone}\"")
    runCommand("cmd time set-time-zone \"${preset.timezone}\"")
    logs.add("✓ টাইম জোন সেট হয়েছে: ${preset.timezone}")

    // ৩. রিজিওন ও কান্ট্রি কোড পরিবর্তন (GN, +224)
    logs.add("🌐 রিজিওন ও কান্ট্রি কোড পরিবর্তন: ${preset.isoCountryCode} (+224)...")
    runCommand("setprop persist.sys.country ${preset.isoCountryCode}")
    runCommand("setprop ro.csc.countryiso_code ${preset.isoCountryCode}")
    runCommand("setprop persist.sys.cact_country ${preset.isoCountryCode}")
    runCommand("setprop persist.sys.locale ${preset.locale}")
    runCommand("settings put system system_locales ${preset.locale}")
    runCommand("settings put global device_provisioned 1")
    logs.add("✓ কান্ট্রি ও রিজিওন কোড: ${preset.isoCountryCode} (GN)")

    // ৪. টেলিকম, সিম ও ওয়াইফাই কান্ট্রি স্পুফিং (Telephony & WiFi Country Code GN)
    logs.add("📶 টেলিকম ও সিম কান্ট্রি +224 (GN) তে স্পুফ করা হচ্ছে...")
    runCommand("setprop gsm.sim.operator.iso-country ${preset.isoCountryCode.lowercase()}")
    runCommand("setprop gsm.operator.iso-country ${preset.isoCountryCode.lowercase()}")
    runCommand("setprop gsm.sim.operator.numeric 61101") // 611 = Guinea Mobile Country Code (MCC)
    runCommand("setprop gsm.operator.numeric 61101")
    runCommand("cmd wifi set-country-code ${preset.isoCountryCode}")
    logs.add("✓ সিম MCC 611 ও নেটওয়ার্ক কান্ট্রি: GN (+224)")

    // ৫. সিস্টেমের ভাষা পরিবর্তন (+224 গিনির অফিশিয়াল ভাষা French fr-GN)
    logs.add("🗣️ ফোনের সিস্টেম ভাষা পরিবর্তন: ${preset.languageName} (${preset.locale})...")
    runCommand("setprop persist.sys.language ${preset.language}")
    runCommand("setprop persist.sys.locale ${preset.locale}")
    runCommand("setprop persist.sys.locales ${preset.locale}")
    runCommand("settings put system system_locales ${preset.locale}")
    runCommand("settings put system user_locale ${preset.locale}")
    runCommand("cmd activity update-configuration --locale ${preset.locale}")
    logs.add("✓ সিস্টেম ভাষা ও লোকেল সেট হয়েছে: ${preset.languageName}")

    // ৬. জিপিএস লোকেশন স্পুফিং (Conakry, Guinea)
    logs.add("📍 জিপিএস লোকেশন সেট করা হচ্ছে: ${preset.latitude}, ${preset.longitude} (Conakry)...")
    try {
      setMockGpsLocation(context, preset.latitude, preset.longitude)
      logs.add("✓ টেস্ট জিপিএস প্রোভাইডার এক্টিভ: ${preset.latitude}, ${preset.longitude}")
    } catch (e: Throwable) {
      val (cmdOk, cmdLog) = runCommand("cmd location set-location gps ${preset.latitude} ${preset.longitude}")
      if (cmdOk) {
        logs.add("✓ cmd location দিয়ে জিপিএস সেট হয়েছে")
      } else {
        logs.add("! জিপিএস নোট: ${e.message ?: cmdLog}")
      }
    }

    logs.add("🎉 ফোন সম্পূর্ণভাবে +224 (গিনি) তে কনফিগার সম্পন্ন হয়েছে!")
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
      return@withContext Result.failure(Exception("Shizuku চালু নেই"))
    }
    val logs = mutableListOf<String>()
    logs.add("অটো টাইম জোন ও ডিফল্ট সেটিংস রিস্টোর করা হচ্ছে...")

    runCommand("settings put global auto_time_zone 1")
    runCommand("settings put global auto_time 1")

    // Mock Provider বন্ধ করা
    try {
      val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
      listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach {
        try { locationManager.removeTestProvider(it) } catch (_: Throwable) {}
      }
      logs.add("✓ জিপিএস মক প্রোভাইডার বন্ধ করা হয়েছে")
    } catch (_: Throwable) {}

    logs.add("✓ ফোন স্বাভাবিক অটোমেটিক মোডে রিস্টোর হয়েছে")
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

    var simCountry = ""
    try {
      val (_, simOut) = runCommand("getprop gsm.sim.operator.iso-country")
      simCountry = simOut.trim().uppercase()
    } catch (_: Throwable) {}

    return SystemStatus(
      currentTimezone = tz.id,
      currentLocale = locale.toLanguageTag(),
      currentCountry = locale.country.ifEmpty { "Unknown" },
      currentLanguage = locale.displayLanguage,
      telephonySimCountry = simCountry.ifEmpty { "N/A" },
      isAutoTimezone = isAuto,
      formattedCurrentTime = sdf.format(Date())
    )
  }
}
