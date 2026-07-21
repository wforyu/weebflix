# WeebFlix

Aplikasi Android untuk nonton streaming anime dari [Samehadaku](https://v2.samehadaku.how/), dengan UI ala Netflix. Tanpa login, tanpa daftar, langsung jalan.

## Fitur

- **Splash Screen** — Logo "W" merah (#E50914) di background hitam, zoom-in Tudum-style animation
- **Home** — Hero banner, Latest Episode, Ongoing, Populer (horizontal scroll Netflix-style)
- **Search** — Pencarian anime real-time dengan debounce
- **Ongoing** — Grid anime yang sedang tayang
- **Detail Anime** — Banner parallax, sinopsis, info lengkap, daftar episode
- **Video Player** — WebView-based streaming, server selection, navigasi episode
- **Dark Theme** — Full Netflix dark mode (#141414)

## Screenshot

| Home | Detail | Player |
|------|--------|--------|
| Hero banner + horizontal card rows | Parallax banner + episode list | Landscape WebView + server picker |

## Tech Stack

| Library | Purpose |
|---------|---------|
| Kotlin | Primary language |
| Jsoup | HTML parsing dari website |
| OkHttp | HTTP client |
| Glide | Image loading & caching |
| Material Design | UI components |
| AndroidX | Core libraries |

## Requirements

- Android Studio Hedgehog (2023.1.1) atau lebih baru
- JDK 17
- Android SDK 34
- Device/Emulator: Android 7.0+ (API 24)

## Cara Build & Install

### Android Studio
```
1. File > Open > pilih folder WeebFlix
2. Tunggu Gradle sync selesai
3. Pilih device/emulator
4. Click Run (▶)
```

### Command Line
```bash
cd WeebFlix
./gradlew assembleDebug
# APK ada di app/build/outputs/apk/debug/
```

### Install ke Device
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Struktur Project

```
WeebFlix/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/weebflix/app/
│   │   ├── WeebFlixApp.kt                 # Application singleton
│   │   ├── data/
│   │   │   ├── model/Models.kt            # Data classes
│   │   │   └── scraper/SamehadakuScraper.kt  # Web scraper
│   │   └── ui/
│   │       ├── splash/SplashActivity.kt   # Splash screen
│   │       ├── main/MainActivity.kt       # Bottom navigation
│   │       ├── home/HomeFragment.kt       # Home tab
│   │       ├── search/SearchFragment.kt   # Search tab
│   │       ├── ongoing/OngoingFragment.kt # Ongoing tab
│   │       ├── detail/AnimeDetailActivity.kt  # Anime detail
│   │       ├── player/PlayerActivity.kt   # Video player
│   │       └── adapter/                   # RecyclerView adapters
│   └── res/
│       ├── layout/        # 8 XML layouts
│       ├── drawable/      # 20+ vector icons & backgrounds
│       ├── values/        # colors, strings, themes
│       ├── anim/          # Splash animations
│       └── menu/          # Bottom nav menu
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Cara Kerja

1. **Scraper** (`SamehadakuScraper.kt`) fetch HTML dari `v2.samehadaku.how` pakai OkHttp
2. **Jsoup** parse HTML jadi data objects (`Anime`, `Episode`)
3. **UI** tampilkan data pakai RecyclerView + Glide untuk gambar
4. **Player** load video URL di WebView (landscape mode)

## Konfigurasi

### Base URL
Default: `https://v2.samehadaku.how`

Untuk ganti, edit di `SamehadakuScraper.kt`:
```kotlin
companion object {
    const val BASE_URL = "https://v2.samehadaku.how"
}
```

### App Icon
Huruf "W" warna merah (#E50914) di background hitam — persis seperti Netflix tapi N diganti W. Untuk ganti:
- Edit vector: `res/drawable/ic_launcher_foreground.xml` (huruf W) + `ic_launcher_background.xml` (background)
- Atau ganti PNG di folder `res/mipmap-*/`

### Tema Warna
Edit di `res/values/colors.xml`:
```xml
<color name="netflix_red">#E50914</color>     <!-- Accent -->
<color name="netflix_black">#141414</color>    <!-- Background -->
```

## Troubleshooting

| Masalah | Solusi |
|---------|--------|
| Gradle sync gagal | Pastikan JDK 17 terinstall, invalidate caches & restart |
| Tidak ada data | Website mungkin down atau HTML berubah, cek selector di scraper |
| Video tidak play | Coba ganti server di panel bawah player |
| Gambar tidak load | Cek koneksi internet, Glide handle placeholder otomatis |
| App crash | Cek log di Android Studio Logcat |

## License

Project ini untuk pembelajaran个人使用.
