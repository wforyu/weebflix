# Agents.md

## Build & Run
- **Build:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat installDebug`
- **Release Build:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleRelease`
- **Gradle:** 9.5.0, AGP 9.3.0, KSP 2.2.10-2.0.2 (for Glide)
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 34 (Android 14)
- **Language:** Kotlin
- **Package:** `com.weebflix.app`
- **Device:** `adb` at `C:\Users\pro021\AppData\Local\Android\Sdk\platform-tools\adb.exe`

## Project Structure
```
WeebFlix/app/src/main/
├── java/com/weebflix/app/
│   ├── WeebFlixApp.kt              # Application class, holds scraper singleton
│   ├── data/
│   │   ├── config/ProviderConfig.kt       # Mutable base URL via SharedPreferences
│   │   ├── model/
│   │   │   ├── Models.kt                 # Anime, Episode, VideoServer, AnimeDetail
│   │   │   └── WatchHistoryManager.kt    # Watch progress storage (SharedPreferences)
│   │   └── scraper/SamehadakuScraper.kt  # HTML parser (Jsoup) for v2.samehadaku.how
│   └── ui/
│       ├── splash/SplashActivity.kt      # Splash with animated N logo
│       ├── main/MainActivity.kt          # Bottom nav host (Home/Search/Ongoing/Settings)
│       ├── home/HomeFragment.kt          # Hero + continue watching + 3 horizontal rows
│       ├── search/SearchFragment.kt      # Real-time search with history
│       ├── ongoing/OngoingFragment.kt    # Grid with vertical infinite scroll
│       ├── settings/SettingsActivity.kt  # Domain configuration
│       ├── detail/AnimeDetailActivity.kt # Parallax detail + episode list
│       ├── player/PlayerActivity.kt      # ExoPlayer + WebView server resolution
│       └── adapter/
│           ├── LatestEpisodeAdapter.kt
│           ├── AnimeAdapter.kt
│           ├── EpisodeListAdapter.kt
│           ├── SearchGridAdapter.kt
│           ├── SearchHistoryAdapter.kt   # Search history chips
│           └── ContinueWatchingAdapter.kt # Continue watching with progress bar
└── res/
    ├── layout/        # XML layouts (Netflix dark theme)
    ├── drawable/      # Vector icons, backgrounds, gradients
    ├── values/        # colors.xml, strings.xml, themes.xml
    ├── anim/          # Splash animations
    └── menu/          # Bottom navigation menu
```

## Key Conventions
- **App Icon:** Netflix-style ribbon "N" (#E50914 + #B20710 fold shadows) on black background
- **Splash Screen:** Red "N" on black, Tudum-style zoom-in animation
- **Theme colors:** Background `#000000` (splash) / `#141414` (app), Red accent `#E50914`, Text primary `#FFFFFF`, Text secondary `#B3B3B3`
- **All networking** goes through `SamehadakuScraper` using coroutines (`withContext(Dispatchers.IO)`)
- **Image loading** uses Glide with `.placeholder(R.drawable.bg_card)` fallback
- **Navigation** is single-Activity with Fragment-based bottom tabs + separate Activities for detail/player/settings
- **Video playback** uses ExoPlayer (Media3) with OkHttp + SimpleCache

## Features
- **Home:** Hero banner + Continue Watching section + Latest Episode + Ongoing Anime + Popular Anime (all with infinite scroll pagination)
- **Search:** Real-time search with debounce (500ms) + Search history (SharedPreferences, max 20)
- **Ongoing:** Full paginated grid of all ongoing anime with vertical infinite scroll + footer loading
- **Detail:** Parallax banner, synopsis, info, episode list with spinner range selector (100 eps/chunk)
- **Player:** ExoPlayer, server picker (floating PopupWindow), gestures (brightness/volume/seek), skip opening/outro, auto-play next episode, PiP support, fullscreen toggle
- **Settings:** Configurable provider domain with validation
- **Continue Watching:** Saves watch progress per episode, shows progress bar on Home, auto-resumes from last position
- **Domain Switching:** Change scraper base URL from Settings — works with any Samehadaku mirror

## Modifying the Scraper
- Website: `https://v2.samehadaku.how`
- All selectors in `SamehadakuScraper.kt` are CSS selectors via Jsoup
- If website HTML structure changes, update selectors in the corresponding method
- Key methods: `getLatestEpisodes(page)`, `getOngoingAnime(page)`, `getPopularAnime(page)`, `searchAnime(query)`, `getAnimeDetail(url)`, `getEpisodeServers(url)`

## Common Tasks
- **Add new section to Home:** Add RecyclerView in `fragment_home.xml`, create adapter, load data in `HomeFragment.loadData()`
- **Change app icon:** Edit `drawable/ic_launcher_foreground.xml` (vector N) + `drawable/ic_launcher_background.xml` (black)
- **Add new screen:** Create Activity/Fragment, add to `AndroidManifest.xml`, wire navigation in `MainActivity`
- **Modify player behavior:** Edit `PlayerActivity.kt`, WebView settings are in `onCreate()`
- **Release APK:** Run `.\gradlew.bat assembleRelease` (unsigned by default, see keystore.md for signing)

## Video Server Resolution
### Blogspot Server (WORKING)
- Fast path: Scraper AJAX POST → get `blogger.com/video.g?token=` URL → WebView loads video.g with XHR interception
- XHR interception injects JS into `video.g` HTML that monkey-patches `XMLHttpRequest.prototype.send` to capture batchexecute responses containing `googlevideo.com` URLs
- URL cleaning: batchexecute responses have double-encoded escaping (`\\u003d`, `\\u0026`, `\\/`), handled by replace chain + final `replace(/\\/g, '')` to strip residual backslashes
- `interceptBloggerHtml()` in `PlayerActivity.kt` fetches video.g via OkHttp, injects XHR script, returns modified HTML
- `shouldInterceptRequest()` routes `blogger.com/video.g` to `interceptBloggerHtml()`
- `onUrlFound()` bridge receives clean URL → `initExoPlayer()`
- Server detection: `server.name.contains("Blogspot")` or `server.url.contains("blogger.com")` or `server.url.contains("bp.blogspot.com")`
- Reference implementation: `https://github.com/hexxt-git/anime-sdk` (`src/extractors/BloggerExtractor.ts`)

### Other Servers
- **Wibufile 720p**: AJAX iframe src IS a direct `.mp4` URL (`https://s0.wibufile.com/video01/...mp4`) — plays directly
- **filedon.co (VIP STREAMING)**: Embed loads via `https://filedon.co/embed/...` — needs further extraction (TODO)
- **Wibufile 480p**: `ERR_SSL_PROTOCOL_ERROR` — device/server incompatibility, cannot fix

## ExoPlayer Configuration
- Buffer: `minBufferMs=15s`, `maxBufferMs=60s`, `bufferForPlaybackMs=2.5s`, `bufferForPlaybackAfterRebufferMs=1.5s`
- Cache: `SimpleCache` with 250MB limit
- OkHttp: Adds `Referer`/`Origin` headers for `googlevideo.com` URLs
- Track selector: Max 1920x1080, preferred audio `id` (Indonesian)

## Bugs & Solutions
| Bug | Solution |
|-----|----------|
| about:blank WebView error | Use `loadDataWithBaseURL` with real URL instead of `loadUrl` |
| Server returns embed HTML not video | Detect failure, return embed URL for WebView resolution |
| Episode list not sorted numerically | Parse episode number from title, sort with natural ordering |
| 1000+ episodes causes OOM/slow scroll | Spinner with 100-episode range chunks |
| Fragment crash on tab switch | Tag-based fragment lookup via `supportFragmentManager.findFragmentByTag()` |
| Ongoing RecyclerView not filling height | `match_parent` + `layout_weight="1"` in parent LinearLayout |
| Fullscreen not toggling | `isSystemBarsHidden` flag with proper icon swap |
| Splash status bar gray | Set status bar color to `@color/black` in theme |
| Search active icon not colored | Use solid red fill vector instead of outline |
| WebView lazy initialization | `ensureWebView()` only called when needed (not on activity create) |
| wibuu.info dead domain | Scraper extracts inner blogspot URL from `url` query param |
| file.fm script-based embed | Scraper detects `<script src="file.fm/...">`, returns embed URL |
| Blogspot.com not detected | `resolveEmbedUrlViaWebView()` now recognizes `blogspot.com` as Blogger |
| Search crash (suspend in non-coroutine) | Wrap `performSearch` in `lifecycleScope.launch` |
| Episode order reversed | Fix scraper selector and sorting logic |

## TODO / Next Session
- **VIP Streaming (filedon.co)**: Extract video URL from `filedon.co/embed/...` pages
- **Auto play next episode**: Implement automatic playback of next episode when current finishes (partially done via auto-play overlay)
