# WebFlix

Aplikasi Android untuk nonton streaming anime dari [Samehadaku](https://v2.samehadaku.how/), dengan UI ala Netflix. Tanpa login, tanpa daftar, langsung jalan.

## Fitur

| Fitur | Deskripsi |
|-------|-----------|
| **Splash Screen** | Logo "N" ribbon merah (#E50914) di background hitam, zoom-in Tudum-style animation |
| **Home** | Hero banner + Continue Watching (lanjutkan menonton) + Latest Episode + Ongoing + Populer (infinite scroll) |
| **Continue Watching** | Simpan progress tontonan otomatis, muncul di home dengan progress bar merah, tap untuk lanjut |
| **Search** | Pencarian real-time dengan debounce 500ms + Riwayat pencarian (max 20, chip UI) |
| **Ongoing** | Grid anime sedang tayang, fetch semua halaman via vertical infinite scroll |
| **Detail Anime** | Banner parallax, sinopsis, info lengkap, daftar episode dengan spinner range (100 eps/chunk) |
| **Video Player** | ExoPlayer (Media3), server picker floating, gesture (brightness/volume/seek), skip opening/outro, PiP, fullscreen |
| **Settings** | Konfigurasi domain provider, validasi URL, reset default |
| **Domain Switching** | Ganti base URL scraper dari Settings — kompatibel dengan mirror Samehadaku apapun |
| **Dark Theme** | Full Netflix dark mode (#141414) dengan accent merah (#E50914) |

## Screenshot

| Home | Detail | Player |
|------|--------|--------|
| Hero + Continue Watching + horizontal cards | Parallax + episode list | ExoPlayer + server picker |

## Tech Stack

| Library | Version | Purpose |
|---------|---------|---------|
| Kotlin | - | Primary language |
| Jsoup | 1.17.1 | HTML parsing dari website |
| OkHttp | 4.12.0 | HTTP client |
| Glide | 4.16.0 | Image loading & caching + KSP |
| Material Design | 1.11.0 | UI components |
| AndroidX Core | 1.12.0 | Core libraries |
| Media3 ExoPlayer | 1.2.1 | Video playback (HLS, DASH, RTSP) |
| Coroutines | 1.7.3 | Async operations |
| Lifecycle | 2.7.0 | ViewModel + LiveData |
| RecyclerView | 1.3.2 | List rendering |
| SwipeRefreshLayout | 1.1.0 | Pull-to-refresh |

## Build Info

| Parameter | Value |
|-----------|-------|
| Gradle | 9.5.0 |
| AGP | 9.3.0 |
| KSP | 2.2.10-2.0.2 |
| compileSdk | 34 |
| minSdk | 24 (Android 7.0) |
| targetSdk | 34 (Android 14) |
| Package | `com.weebflix.app` |

## Requirements

- Android Studio (2023.1.1) atau lebih baru
- JDK 17 (bundled dengan Android Studio)
- Android SDK 34
- Device/Emulator: Android 7.0+ (API 24)

## Cara Build & Install

### Debug
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat installDebug
```

### Release (Unsigned)
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleRelease
# APK: app/build/outputs/apk/release/app-release-unsigned.apk
```

### Release (Signed)
Lihat `keystore.md` untuk cara membuat keystore dan sign APK.

### Install via ADB
```powershell
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Struktur Project

```
WeebFlix/app/src/main/
├── AndroidManifest.xml
├── java/com/weebflix/app/
│   ├── WeebFlixApp.kt                  # Application singleton
│   ├── data/
│   │   ├── config/ProviderConfig.kt    # Mutable base URL via SharedPreferences
│   │   ├── model/
│   │   │   ├── Models.kt              # Data classes (Anime, Episode, VideoServer, etc.)
│   │   │   └── WatchHistoryManager.kt # Continue watching storage
│   │   └── scraper/SamehadakuScraper.kt # Web scraper (Jsoup)
│   └── ui/
│       ├── splash/SplashActivity.kt    # Splash screen
│       ├── main/MainActivity.kt        # Bottom navigation
│       ├── home/HomeFragment.kt        # Home tab
│       ├── search/SearchFragment.kt    # Search tab with history
│       ├── ongoing/OngoingFragment.kt  # Ongoing tab with pagination
│       ├── settings/SettingsActivity.kt # Domain settings
│       ├── detail/AnimeDetailActivity.kt # Anime detail
│       ├── player/PlayerActivity.kt    # Video player
│       └── adapter/                    # RecyclerView adapters
├── res/
│   ├── layout/        # XML layouts
│   ├── drawable/      # Vector icons, backgrounds, gradients
│   ├── values/        # colors, strings, themes
│   ├── anim/          # Splash animations
│   └── menu/          # Bottom nav menu
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Cara Kerja

1. **Scraper** (`SamehadakuScraper.kt`) fetch HTML dari `v2.samehadaku.how` pakai OkHttp
2. **Jsoup** parse HTML jadi data objects (`Anime`, `Episode`, `VideoServer`)
3. **UI** tampilkan data pakai RecyclerView + Glide untuk gambar
4. **Player** resolve server URL → ExoPlayer untuk playback, WebView untuk Blogspot/XHR extraction
5. **Watch History** simpan progress ke SharedPreferences, tampilkan di Home

## Bugs & Solutions

| Bug | Solusi |
|-----|--------|
| about:blank WebView error | Pakai `loadDataWithBaseURL` dengan URL asli |
| Server embed bukan video URL | Deteksi kegagalan, return embed URL untuk WebView |
| Episode list tidak terurut | Parse nomor episode dari title, sort numerik |
| 1000+ episode causes OOM | Spinner dengan range 100 episode per chunk |
| Fragment crash saat tab switch | Tag-based fragment lookup |
| Fullscreen tidak toggle | Flag `isSystemBarsHidden` dengan icon swap |
| Splash status bar abu-abu | Set status bar color ke `@color/black` di theme |
| WebView lazy init | `ensureWebView()` hanya dipanggil saat dibutuhkan |
| wibuu.info domain mati | Scraper extract inner blogspot URL dari query param |
| file.fm script embed | Scraper deteksi `<script src="file.fm/...">`, return embed URL |
| Search crash (suspend) | Wrap `performSearch` di `lifecycleScope.launch` |

## Konfigurasi

### Base URL
Default: `https://v2.samehadaku.how`

Bisa diganti dari menu Settings di aplikasi, atau edit `ProviderConfig.kt`:
```kotlin
private const val DEFAULT_BASE_URL = "https://v2.samehadaku.how"
```

### App Icon
Logo "N" ribbon ala Netflix (#E50914 + #B20710 fold shadows) di background hitam:
- Edit vector: `res/drawable/ic_launcher_foreground.xml`
- Background: `res/drawable/ic_launcher_background.xml`

### Tema Warna
```xml
<color name="netflix_red">#E50914</color>      <!-- Accent -->
<color name="netflix_dark_red">#B20710</color>   <!-- Fold shadow -->
<color name="netflix_black">#141414</color>      <!-- Background -->
<color name="netflix_surface">#1F1F1F</color>    <!-- Card surface -->
```

## License

Project ini untuk pembelajaran dan penggunaan pribadi.
