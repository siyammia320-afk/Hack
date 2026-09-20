package com.example

data class CountryPreset(
  val id: String,
  val name: String,
  val dialCode: String,
  val isoCountryCode: String,
  val locale: String,
  val language: String,
  val languageName: String,
  val timezone: String,
  val capitalCity: String,
  val latitude: Double,
  val longitude: Double,
  val flagEmoji: String
)

object PresetRepository {
  // Only Guinea (+224) as requested by user
  val guineaPreset = CountryPreset(
    id = "gn",
    name = "Guinea (গিনি)",
    dialCode = "+224",
    isoCountryCode = "GN",
    locale = "fr-GN",
    language = "fr",
    languageName = "French (Français - Guinea)",
    timezone = "Africa/Conakry",
    capitalCity = "Conakry",
    latitude = 9.6412,
    longitude = -13.5784,
    flagEmoji = "🇬🇳"
  )

  val defaultPreset = guineaPreset
}
