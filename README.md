# WeebFlix

Aplikasi Android untuk nonton streaming anime, drakor, dan donghua dari berbagai provider, dengan UI ala Netflix. Multi-provider + YouTube (tanpa iklan), Android TV support, tanpa daftar, langsung jalan.

**Versi:** `2.0.2-beta` (versionCode 102) — pre-release.

## Provider

| Provider | URL | Konten |
|----------|-----|--------|
| **Samehadaku** | `v2.samehadaku.how` | Anime (Latest, Ongoing, Popular) |
| **DrakorKita** | `drakor.kita.mobi` | Drakor (Episode, Movie, Serie) — download-pipeline + Fast HLS |
| **OppaDrama** | `http://45.11.57.192` | Drakor (Episode, Movie, Serie) — Web API + token-based server resolution |
| **Anichin** | `anichin.cafe` | Donghua/Anime (Latest, Ongoing, Completed, All Anime) — WordPress + animestream theme |
| **Otakudesu** | `otakudesu.blog` | Anime (Latest, Ongoing, Complete) — WordPress, streaming via Blogspot + mirror download |
| **YouTube** | — | Feed/search/trending + playback **tanpa iklan** (raw DASH), OAuth login, komentar, like/subscribe, channel detail, account bottom sheet (mini player) |

Bisa switch provider langsung dari tab Home, dan setiap provider punya domain yang bisa dikonfigurasi di Settings.

## Fitur

| Fitur | Deskripsi |
|-------|-----------|
| **Splash Screen** | Logo "N" ribbon merah (#E50914) di background hitam, zoom-in Tudum-style animation |
| **Multi-Provider Home** | Chip switcher untuk ganti antar provider, masing-masing dengan layout sendiri |
| **Hero Banner** | Auto-scrolling ViewPager2 carousel (DrakorKita/OppaDrama) atau static hero (Samehadaku/Otakudesu) atau 5 clickable sections (OppaDrama) atau Continue Watching + sections (Anichin) |
| **Continue Watching** | Simpan progress tontonan otomatis per provider, muncul di home dengan progress bar merah, tap untuk lanjut |
| **Search** | Pencarian real-time dengan debounce 500ms + Riwayat pencarian (max 20, chip UI) |
| **Ongoing** | Grid anime sedang tayang, fetch semua halaman via vertical infinite scroll (label jadi "Histori" saat provider YouTube) |
| **Category Grid** | Full-screen 3-column grid untuk Semua Episode / Movie / Serie (DrakorKita) atau Drama Korea / China / Film Korea / Netflix (OppaDrama) atau Ongoing/Completed/All (Anichin) dengan infinite scroll |
| **Detail Anime** | Banner parallax, sinopsis, info lengkap, daftar episode dengan spinner range (100 eps/chunk). Di TV: layout two-pane (banner kiri + episode kanan) |
| **Video Player** | ExoPlayer (Media3), server picker floating, gesture (brightness/volume/seek), **pinch-to-zoom 1x–4x** (fullscreen, semua provider), skip opening/outro, PiP, fullscreen, episode navigation (prev/next), progress bar loading untuk download besar |
| **YouTube Player** | Mode portrait (rotate saat fullscreen), daftar Rekomendasi/related + infinite scroll, auto-play next video (countdown 10s), gear resolusi manual (Auto/1080p→144p), skip prev/next (`ytPlayHistory` + `ytUpNext`), like/dislike, subscribe, section Komentar (collapsible), default resolusi maks dari Settings |
| **YouTube OAuth** | Login akun Google via browser sistem + loopback server (PKCE, scope `youtube` penuh, built-in credentials), token tersimpan di EncryptedSharedPreferences |
| **Android TV** | LEANBACK_LAUNCHER + banner TV, D-pad focus/outline di semua card, hero carousel remote-friendly, player TV mode (auto-pilih server ExoPlayer-friendly + kontrol bawaan), layout-land two-pane, search pakai Leanback IME |
| **Check Update** | Tombol "Periksa Pembaruan" di Settings → bandingkan dengan rilis GitHub terbaru (termasuk pre-release) |
| **Settings** | Konfigurasi domain per provider dengan validasi URL, reset default, YouTube OAuth client config, default resolusi maks YouTube (Auto/144→2160), About (versi + GIT_COMMIT + BUILD_DATE + credit developer) |
| **Dark Theme** | Full Netflix dark mode (#141414) dengan accent merah (#E50914) |

## Tech Stack

| Library | Version | Purpose |
|---------|---------|---------|
| Kotlin | - | Primary language |
| Jsoup | 1.18.1 | HTML parsing dari website |
| OkHttp | 4.12.0 | HTTP client |
| Glide | 4.16.0 | Image loading & caching + KSP |
| Material Design | 1.12.0 | UI components (Chips, CardView) |
| AndroidX Core | 1.13.1 | Core libraries |
| Media3 ExoPlayer | 1.5.1 | Video playback (HLS, DASH, RTSP) + datasource-okhttp |
| Security Crypto | 1.1.0-alpha06 | EncryptedSharedPreferences (token OAuth) |
| ViewPager2 | 1.1.0 | Hero banner carousel |
| Coroutines | 1.9.0 | Async operations |
| Lifecycle | 2.8.7 | ViewModel + LiveData |
| RecyclerView | 1.3.2 | List rendering |
| SwipeRefreshLayout | 1.1.0 | Pull-to-refresh |

## Build Info

| Parameter | Value |
|-----------|-------|
| Versi | `2.0.2-beta` (versionCode 102) |
| Gradle | 9.5.0 |
| AGP | 9.3.0 |
| KSP | 2.2.10-2.0.2 (for Glide) |
| compileSdk | 35 (Android 15) |
| minSdk | 24 (Android 7.0) |
| targetSdk | 34 (Android 14) |
| Package | `com.weebflix.app` |
| BuildConfig | `GIT_COMMIT` + `BUILD_DATE` (tampil di Settings → About) |

## Requirements

- Android Studio (2023.1.1) atau lebih baru
- JDK 17 (bundled dengan Android Studio)
- Android SDK 35
- Device/Emulator: Android 7.0+ (API 24), HP atau Android TV (satu APK sama)

## Cara Build & Install

### Debug
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat installDebug
```

### Release (Signed)
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```
Release build memakai keystore `webflix-release.jks` (lihat `keystore.md`).

### Install via ADB
```powershell
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Pre-release (agar Check Update berfungsi)
Ikuti checklist di `AGENTS.md` → "Build & Release Pre-release": bump `versionCode`/`versionName`, commit, build release, push + tag `v{versionName}`, lalu `gh release create --prerelease` dengan APK.

## Struktur Project

```
WeebFlix/app/src/main/
├── AndroidManifest.xml            # + LEANBACK_LAUNCHER, banner TV, uses-feature touchscreen/leanback
├── java/com/weebflix/app/
│   ├── WeebFlixApp.kt             # Application, provider registry init
│   ├── WeebFlixGlideModule.kt     # Glide AppGlideModule
│   ├── data/
│   │   ├── auth/
│   │   │   ├── YouTubeAuthManager.kt   # OAuth PKCE + token store (EncryptedSharedPreferences)
│   │   │   └── LoopbackOAuthServer.kt  # ServerSocket localhost utk callback redirect
│   │   ├── config/ProviderConfig.kt    # Per-provider base URL + active provider + OAuth creds
│   │   ├── model/
│   │   │   ├── Models.kt               # Anime, Episode, VideoServer, AnimeDetail, EpisodeNavigation
│   │   │   ├── WatchHistoryManager.kt  # Continue watching storage (per provider)
│   │   │   ├── ProviderDataCache.kt    # Cache home data (memory + disk)
│   │   │   └── GitHubDataFetcher.kt    # Fallback pre-scrape dari GitHub
│   │   ├── provider/
│   │   │   ├── AnimeProvider.kt        # Provider interface (getLatestEpisodes, getAnimeDetail, dll)
│   │   │   └── ProviderFactory.kt      # Singleton registry, getActiveProvider(), refreshBaseUrls()
│   │   └── scraper/
│   │       ├── SamehadakuScraper.kt    # Anime scraper (Jsoup)
│   │       ├── DrakorKitaScraper.kt    # Drakor scraper (download pipeline + P2P HLS)
│   │       ├── OppaDramaScraper.kt     # Drakor scraper (token API + FileLions/Hydrax)
│   │       ├── AnichinScraper.kt       # Donghua/Anime scraper
│   │       ├── OtakudesuScraper.kt     # Anime scraper (Blogspot streaming)
│   │       └── YouTube*.kt             # YouTubeScraper + YouTubeResolver + YouTubeCipher + YouTubeDashManifest + YouTubeModels
│   └── ui/
│       ├── splash/SplashActivity.kt
│       ├── main/MainActivity.kt        # Bottom nav host (Home/Search/Ongoing/Settings/YouTube)
│       ├── home/                       # HomeFragment (chip switcher) + 1 fragment per provider
│       ├── search/SearchFragment.kt
│       ├── ongoing/OngoingFragment.kt
│       ├── settings/SettingsFragment.kt
│       ├── detail/                     # AnimeDetailActivity + CategoryGridActivity
│       ├── player/PlayerActivity.kt    # ExoPlayer + WebView + routing video + HydraxDataSource
│       ├── youtube/                    # YouTubeHomeFragment/HistoryFragment/SearchActivity/LoginActivity + adapter
│       ├── adapter/                    # Anime, Episode, Hero, ContinueWatching, Search, Netflix, Drama, dll.
│       └── util/TvUtils.kt             # Deteksi TV, force landscape, fokus D-pad
├── res/
│   ├── layout/          # XML layouts (Netflix dark theme)
│   ├── layout-land/     # Layout landscape khusus TV (two-pane detail + card besar)
│   ├── layout-sw600dp/  # Card variants TV/tablet (≥600dp: item_anime_card, item_youtube_feed, dll.)
│   ├── drawable/        # Vector icons, backgrounds, gradients, tv_banner
│   ├── animator/        # focus_scale.xml (animasi fokus D-pad TV)
│   ├── values/          # colors, strings, themes
│   ├── anim/            # Splash animations
│   ├── menu/            # Bottom navigation menu
│   └── raw/             # hls_min.js (bundled HLS player untuk turboviplay CDN)
```

## Cara Kerja

1. **ProviderFactory** registrasi semua provider (`SamehadakuScraper`, `DrakorKitaScraper`, `OppaDramaScraper`, `AnichinScraper`, `OtakudesuScraper`, `YouTubeScraper`) saat app start
2. **HomeFragment** tampilkan chip switcher — user pilih provider, fragment container swap
3. **Scraper** fetch HTML dari website masing-masing pakai OkHttp (DrakorKita/Anichin pakai trust-all SSL certs)
4. **Jsoup** parse HTML jadi data objects (`Anime`, `Episode`, `VideoServer`, `AnimeDetail`)
5. **UI** tampilkan data pakai RecyclerView + Glide untuk gambar
6. **Player** resolve server URL berdasarkan provider:
   - **Blogspot (Samehadaku)**: AJAX POST → `blogger.com/video.g?token=` → WebView XHR interception → batchexecute parsing → googlevideo.com URL
   - **filedon.co (Samehadaku VIP)**: parse `data-page` JSON embed → direct signed R2 `.mkv` → ExoPlayer (Matroska)
   - **Wibufile**: direct `.mp4` → ExoPlayer
   - **Mega**: embed SPA (`secureboot.js`) → WebView
   - **DrakorKita (Download)**: `ajax_dl_all.php` → `dlfilemob.php` → direct MP4 di `c1hd.load.my.id` → ExoPlayer (moov-at-end, progress bar loading)
   - **DrakorKita (Fast HLS)**: `drakorkita.stream` API → hex ciphertext → AES-128-CBC decrypt → signed m3u8 (`source`) → ExoPlayer dengan Referer dinamis
   - **OppaDrama (FileLions)**: `minochinos.com/v/{id}` → unpack packed JS → signed HLS (`.urlset/`) → ExoPlayer
   - **OppaDrama (Hydrax)**: `abyssplayer.com/?v={id}` → SoTrym `const datas` → AES-256-CTR MP4 di `*.sssrr.org` (hanya 64KB pertama terenkripsi) → `hydrax://` + `HydraxDataSource` → ExoPlayer
   - **OppaDrama (TurboVIP)**: WebView fallback (Google-drive 429 dari plain IP)
   - **Anichin (Premium)**: `anichin.stream/?id={id}` → unpack eval JS → direct m3u8 → ExoPlayer
   - **Anichin (old posts / Drive)**: Dailymotion/Mega/OK.ru/Rumble/abyssplayer/rubyvidhub embed → dimainkan langsung di WebView (`playEpisodePageViaWebView` + main-frame rewrite)
   - **Otakudesu (Blogspot)**: iframe `#lightsVideo` → `blogger.com/video.g?token=` → WebView XHR interception (pipeline Blogspot yang sama dengan Samehadaku) → googlevideo.com → ExoPlayer
   - **YouTube**: `youtubei/v1/player` → streamingData adaptiveFormats (video-only + audio-only, signature decipher + n-param) → `MergingMediaSource` → ExoPlayer tanpa iklan
7. **Watch History** simpan progress per provider ke SharedPreferences, tampilkan di Home
8. **Android TV** — deteksi via `TvUtils` (UiModeManager + FEATURE_LEANBACK): fokus D-pad + outline merah, player pakai kontrol ExoPlayer bawaan + auto-select server ExoPlayer-friendly, landscape `layout-land`

## Android TV Support (Phase 1-3)

Satu APK yang sama jalan di HP + Android TV (sideload):
- **Phase 1** — LEANBACK_LAUNCHER di MainActivity, banner TV (`tv_banner`), `uses-feature` touchscreen/leanback `required="false"` (installable di TV box)
- **Phase 2** — fokus D-pad (animasi scale + outline merah `#E50914`) di semua card/chip/YouTube items, hero carousel bisa dikontrol remote, player TV mode (`useController = isTvMode` + key handler MEDIA_*), orientation landscape di TV
- **Phase 3** — `layout-land/` two-pane detail (banner kiri + episode kanan) + card variants lebih besar, WebView player D-pad → auto-select server ExoPlayer-friendly saat TV, search pakai Leanback IME. `layout-sw600dp/` card variants (presisi TV/tablet class) + pinch-to-zoom 1x–4x (player)
- Terverifikasi di emulator TV API 36 (`weebflix_tv`, tv_1080p); belum diuji di TV box fisik

## Video Server Routing

Routing di `PlayerActivity` (satu titik keputusan): URL video langsung (`.mp4/.m3u8/.mpd/.mkv/.webm/.m4v`/`googlevideo`) → ExoPlayer; embed browser-playable → WebView visible; selain itu → WebView hidden interception. Rincian per-server di bagian "Cara Kerja" dan di `AGENTS.md` → "Video Server Resolution".

## Pre-Scrape Cache (GitHub Data)

Home fragments fall back ke `data/{providerId}_home.json` dari `raw.githubusercontent.com/wforyu/weebflix/master/data`; live-scrape kalau null. Dijalankan ulang otomatis oleh `.github/workflows/scrape-providers.yml` (cron 6h) via `scripts/scrape_providers.py`.

## Bugs & Solutions

Tabel bug-solusi lengkap + closed bugs (turboviplay 429, FileLions, Hydrax, filedon optString, DrakorKita HLS, dll.) ada di `AGENTS.md` → "Bugs & Solutions" dan "Closed Bugs". Tidak ada bug terbuka yang diketahui saat ini.

## Konfigurasi

### Base URL per Provider
Default:
- Samehadaku: `https://v2.samehadaku.how`
- DrakorKita: `https://drakor.kita.mobi`
- OppaDrama: `http://45.11.57.192`
- Anichin: `https://anichin.cafe`
- Otakudesu: `https://otakudesu.blog`

Bisa diganti dari menu Settings di aplikasi (per-provider, dengan validasi URL + reset).

### YouTube OAuth
Login Google untuk video yang diblokir/embedding-disabled:
- Client ID + Client Secret built-in (bisa diganti di Settings → YouTube Login)
- Flow: browser sistem (Google blokir embedded WebView) → redirect `http://localhost:8080/callback` ditangkap `LoopbackOAuthServer`
- ⚠ Saat ini YouTube memblokir semua request innertube ber-OAuth (HTTP 400 `INVALID_ARGUMENT`, open issue YouTube.js #916) — resolver pakai 2-pass fallback (auth → anonymous) sehingga login tidak merusak playback

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

## Lisensi

Project ini untuk pembelajaran dan penggunaan pribadi.
