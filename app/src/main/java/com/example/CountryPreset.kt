package com.example

data class CountryPreset(
  val id: String,
  val name: String,
  val dialCode: String,
  val isoCountryCode: String,
  val locale: String,
  val timezone: String,
  val capitalCity: String,
  val latitude: Double,
  val longitude: Double,
  val flagEmoji: String
)

object PresetRepository {
  val presets = listOf(
    CountryPreset(
      id = "gn",
      name = "Guinea (গিনি)",
      dialCode = "+224",
      isoCountryCode = "GN",
      locale = "fr-GN",
      timezone = "Africa/Conakry",
      capitalCity = "Conakry",
      latitude = 9.6412,
      longitude = -13.5784,
      flagEmoji = "🇬🇳"
    ),
    CountryPreset(
      id = "us",
      name = "United States (আমেরিকা)",
      dialCode = "+1",
      isoCountryCode = "US",
      locale = "en-US",
      timezone = "America/New_York",
      capitalCity = "Washington D.C.",
      latitude = 38.9072,
      longitude = -77.0369,
      flagEmoji = "🇺🇸"
    ),
    CountryPreset(
      id = "gb",
      name = "United Kingdom (যুক্তরাজ্য)",
      dialCode = "+44",
      isoCountryCode = "GB",
      locale = "en-GB",
      timezone = "Europe/London",
      capitalCity = "London",
      latitude = 51.5074,
      longitude = -0.1278,
      flagEmoji = "🇬🇧"
    ),
    CountryPreset(
      id = "ae",
      name = "United Arab Emirates (দুবাই / UAE)",
      dialCode = "+971",
      isoCountryCode = "AE",
      locale = "ar-AE",
      timezone = "Asia/Dubai",
      capitalCity = "Abu Dhabi / Dubai",
      latitude = 25.2048,
      longitude = 55.2708,
      flagEmoji = "🇦🇪"
    ),
    CountryPreset(
      id = "sa",
      name = "Saudi Arabia (সৌদি আরব)",
      dialCode = "+966",
      isoCountryCode = "SA",
      locale = "ar-SA",
      timezone = "Asia/Riyadh",
      capitalCity = "Riyadh",
      latitude = 24.7136,
      longitude = 46.6753,
      flagEmoji = "🇸🇦"
    ),
    CountryPreset(
      id = "bd",
      name = "Bangladesh (বাংলাদেশ - মূল)",
      dialCode = "+880",
      isoCountryCode = "BD",
      locale = "bn-BD",
      timezone = "Asia/Dhaka",
      capitalCity = "Dhaka",
      latitude = 23.8103,
      longitude = 90.4125,
      flagEmoji = "🇧🇩"
    ),
    CountryPreset(
      id = "in",
      name = "India (ভারত)",
      dialCode = "+91",
      isoCountryCode = "IN",
      locale = "en-IN",
      timezone = "Asia/Kolkata",
      capitalCity = "New Delhi",
      latitude = 28.6139,
      longitude = 77.2090,
      flagEmoji = "🇮🇳"
    ),
    CountryPreset(
      id = "sg",
      name = "Singapore (সিঙ্গাপুর)",
      dialCode = "+65",
      isoCountryCode = "SG",
      locale = "en-SG",
      timezone = "Asia/Singapore",
      capitalCity = "Singapore",
      latitude = 1.3521,
      longitude = 103.8198,
      flagEmoji = "🇸🇬"
    ),
    CountryPreset(
      id = "jp",
      name = "Japan (জাপান)",
      dialCode = "+81",
      isoCountryCode = "JP",
      locale = "ja-JP",
      timezone = "Asia/Tokyo",
      capitalCity = "Tokyo",
      latitude = 35.6762,
      longitude = 139.6503,
      flagEmoji = "🇯🇵"
    ),
    CountryPreset(
      id = "de",
      name = "Germany (জার্মানি)",
      dialCode = "+49",
      isoCountryCode = "DE",
      locale = "de-DE",
      timezone = "Europe/Berlin",
      capitalCity = "Berlin",
      latitude = 52.5200,
      longitude = 13.4050,
      flagEmoji = "🇩🇪"
    ),
    CountryPreset(
      id = "ca",
      name = "Canada (কানাডা)",
      dialCode = "+1",
      isoCountryCode = "CA",
      locale = "en-CA",
      timezone = "America/Toronto",
      capitalCity = "Ottawa",
      latitude = 45.4215,
      longitude = -75.6972,
      flagEmoji = "🇨🇦"
    ),
    CountryPreset(
      id = "au",
      name = "Australia (অস্ট্রেলিয়া)",
      dialCode = "+61",
      isoCountryCode = "AU",
      locale = "en-AU",
      timezone = "Australia/Sydney",
      capitalCity = "Canberra",
      latitude = -33.8688,
      longitude = 151.2093,
      flagEmoji = "🇦🇺"
    )
  )

  val defaultPreset = presets.first() // Guinea (+224)
}
