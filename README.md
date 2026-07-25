# WeebFlix

Aplikasi Android untuk nonton streaming anime dan drakor dari berbagai provider, dengan UI ala Netflix. Multi-provider, tanpa login, tanpa daftar, langsung jalan.

## Provider

| Provider | URL | Konten |
|----------|-----|--------|
| **Samehadaku** | `v2.samehadaku.how` | Anime (Latest, Ongoing, Popular) |
| **DrakorKita** | `drakor.kita.mobi` | Drakor (Episode, Movie, Serie) |

Bisa switch provider langsung dari tab Home, dan setiap provider punya domain yang bisa dikonfigurasi di Settings.

## Fitur

| Fitur | Deskripsi |
|-------|-----------|
| **Splash Screen** | Logo "N" ribbon merah (#E50914) di background hitam, zoom-in Tudum-style animation |
| **Multi-Provider Home** | Chip switcher untuk ganti antara Samehadaku dan DrakorKita, masing-masing dengan layout sendiri |
| **Hero Banner** | Auto-scrolling ViewPager2 carousel (DrakorKita) atau static hero (Samehadaku) dengan Play + Info buttons |
| **Continue Watching** | Simpan progress tontonan otomatis per provider, muncul di home dengan progress bar merah, tap untuk lanjut |
| **Search** | Pencarian real-time dengan debounce 500ms + Riwayat pencarian (max 20, chip UI) |
| **Ongoing** | Grid anime sedang tayang, fetch semua halaman via vertical infinite scroll |
| **Category Grid** | Full-screen 3-column grid untuk Semua Episode / Movie / Serie (DrakorKita) dengan infinite scroll |
| **Detail Anime** | Banner parallax, sinopsis, info lengkap, daftar episode dengan spinner range (100 eps/chunk) |
| **Video Player** | ExoPlayer (Media3), server picker floating, gesture (brightness/volume/seek), skip opening/outro, PiP, fullscreen, episode navigation (prev/next) |
| **Settings** | Konfigurasi domain per provider dengan validasi URL, reset default |
| **Domain Switching** | Ganti base URL scraper dari Settings — kompatibel dengan mirror apapun |
| **Dark Theme** | Full Netflix dark mode (#141414) dengan accent merah (#E50914) |

## Tech Stack

| Library | Version | Purpose |
|---------|---------|---------|
| Kotlin | - | Primary language |
| Jsoup | 1.17.1 | HTML parsing dari website |
| OkHttp | 4.12.0 | HTTP client |
| Glide | 4.16.0 | Image loading & caching + KSP |
| Material Design | 1.11.0 | UI components (Chips, CardView) |
| AndroidX Core | 1.12.0 | Core libraries |
| Media3 ExoPlayer | 1.2.1 | Video playback (HLS, DASH, RTSP) |
| ViewPager2 | - | Hero banner carousel |
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
│   ├── WeebFlixApp.kt                  # Application singleton, provider registry init
│   ├── data/
│   │   ├── config/ProviderConfig.kt    # Per-provider base URL + active provider via SharedPreferences
│   │   ├── model/
│   │   │   ├── Models.kt              # Data classes (Anime, Episode, VideoServer, EpisodeNavigation, etc.)
│   │   │   └── WatchHistoryManager.kt # Continue watching storage (per provider)
│   │   ├── provider/
│   │   │   ├── AnimeProvider.kt       # Provider interface (getLatestEpisodes, getAnimeDetail, etc.)
│   │   │   └── ProviderFactory.kt     # Singleton registry, getActiveProvider(), refreshBaseUrls()
│   │   └── scraper/
│   │       ├── SamehadakuScraper.kt   # Anime scraper (Jsoup) — implements AnimeProvider
│   │       └── DrakorKitaScraper.kt   # Drakor scraper (Jsoup) — implements AnimeProvider
│   └── ui/
│       ├── splash/SplashActivity.kt   # Splash screen
│       ├── main/MainActivity.kt       # Bottom nav host (Home/Search/Ongoing/Settings)
│       ├── home/
│       │   ├── HomeFragment.kt        # Provider chip switcher + fragment container
│       │   ├── SamehadakuHomeFragment.kt  # Samehadaku home (static hero + 3 rows)
│       │   └── DrakorKitaHomeFragment.kt  # DrakorKita home (auto-scroll hero + 3 rows)
│       ├── search/SearchFragment.kt   # Search tab with history
│       ├── ongoing/OngoingFragment.kt # Ongoing tab with pagination
│       ├── settings/SettingsFragment.kt   # Per-provider domain config (as Fragment)
│       ├── detail/
│       │   ├── AnimeDetailActivity.kt # Parallax detail + episode list
│       │   └── CategoryGridActivity.kt    # Full-screen 3-col grid (DrakorKita categories)
│       ├── player/PlayerActivity.kt   # ExoPlayer + WebView + multi-provider server resolution
│       └── adapter/
│           ├── LatestEpisodeAdapter.kt    # Samehadaku episode cards
│           ├── AnimeAdapter.kt            # Samehadaku anime cards
│           ├── NetflixCardAdapter.kt      # Netflix-style compact card (DrakorKita)
│           ├── DramaCardAdapter.kt        # Drama card with rating badge
│           ├── HeroPagerAdapter.kt        # ViewPager2 hero banner carousel
│           ├── EpisodeListAdapter.kt      # Episode list with spinner
│           ├── SearchGridAdapter.kt       # Search results grid
│           ├── SearchHistoryAdapter.kt    # Search history chips
│           └── ContinueWatchingAdapter.kt # Continue watching with progress bar
├── res/
│   ├── layout/        # XML layouts (Netflix dark theme)
│   ├── drawable/      # Vector icons, backgrounds, gradients
│   ├── values/        # colors, strings, themes
│   ├── anim/          # Splash animations
│   └── menu/          # Bottom navigation menu
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Cara Kerja

1. **ProviderFactory** registrasi semua provider (`SamehadakuScraper`, `DrakorKitaScraper`) saat app start
2. **HomeFragment** tampilkan chip switcher — user pilih provider, fragment container swap
3. **Scraper** fetch HTML dari website masing-masing pakai OkHttp (DrakorKita pakai trust-all SSL certs)
4. **Jsoup** parse HTML jadi data objects (`Anime`, `Episode`, `VideoServer`, `AnimeDetail`)
5. **UI** tampilkan data pakai RecyclerView + Glide untuk gambar
6. **Player** resolve server URL berdasarkan provider:
   - **Blogspot**: AJAX + XHR interception → ExoPlayer
   - **DrakorKita**: 3-step API (episode.php → server.php → video_hydrax.php) → Abyss CDN / direct MP4
   - **Wibufile**: Direct MP4 URL
7. **Watch History** simpan progress per provider ke SharedPreferences, tampilkan di Home

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
| Blogspot.com not detected | `resolveEmbedUrlViaWebView()` recognizes `blogspot.com` as Blogger |
| Search crash (suspend) | Wrap `performSearch` di `lifecycleScope.launch` |
| DrakorKita SSL errors | Trust-all SSL certificates on OkHttpClient |
| DrakorKita dead domains | Auto-rewrite old domain URLs to current domain |

## Konfigurasi

### Base URL per Provider
Default Samehadaku: `https://v2.samehadaku.how`
Default DrakorKita: `https://drakor.kita.mobi`

Bisa diganti dari menu Settings di aplikasi, atau edit `ProviderConfig.kt`:
```kotlin
private val DEFAULT_URLS = mapOf(
    "samehadaku" to "https://v2.samehadaku.how",
    "drakorkita" to "https://drakor.kita.mobi"
)
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
