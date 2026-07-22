# Agents.md

## Build & Run
- **Build:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"; .\gradlew.bat installDebug`
- **Gradle:** 9.5.0, AGP 9.3.0, KSP 2.2.10-2.0.2 (for Glide)
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 34 (Android 14)
- **Language:** Kotlin
- **Package:** `com.weebflix.app`
- **Device:** `adb` at `C:\Users\ganyo\AppData\Local\Android\Sdk\platform-tools\adb.exe`

## Project Structure
```
WeebFlix/app/src/main/
├── java/com/weebflix/app/
│   ├── WeebFlixApp.kt              # Application class, holds scraper singleton
│   ├── data/
│   │   ├── model/Models.kt         # Anime, Episode, VideoServer, AnimeDetail
│   │   └── scraper/SamehadakuScraper.kt  # HTML parser (Jsoup) for v2.samehadaku.how
│   └── ui/
│       ├── splash/SplashActivity.kt    # Splash with animated W logo
│       ├── main/MainActivity.kt        # Bottom nav host (Home/Search/Ongoing)
│       ├── home/HomeFragment.kt        # Hero banner + horizontal card rows
│       ├── search/SearchFragment.kt    # Real-time search with debounce
│       ├── ongoing/OngoingFragment.kt  # Grid of ongoing anime
│       ├── detail/AnimeDetailActivity.kt  # Parallax detail + episode list
│       ├── player/PlayerActivity.kt    # ExoPlayer + WebView server resolution
│       └── adapter/                    # RecyclerView adapters
│           ├── LatestEpisodeAdapter.kt
│           ├── AnimeAdapter.kt
│           ├── EpisodeListAdapter.kt
│           └── SearchGridAdapter.kt
└── res/
    ├── layout/        # XML layouts (Netflix dark theme)
    ├── drawable/      # Vector icons, backgrounds, gradients
    ├── values/        # colors.xml, strings.xml, themes.xml
    ├── anim/          # Splash animations
    └── menu/          # Bottom navigation menu
```

## Key Conventions
- **App Icon:** Red "W" (#E50914) on black background — same style as Netflix's red "N" on black
- **Splash Screen:** Red "W" on black, Tudum-style zoom-in animation
- **Theme colors:** Background `#000000` (splash) / `#141414` (app), Red accent `#E50914`, Text primary `#FFFFFF`, Text secondary `#B3B3B3`
- **All networking** goes through `SamehadakuScraper` using coroutines (`withContext(Dispatchers.IO)`)
- **Image loading** uses Glide with `.placeholder(R.drawable.bg_card)` fallback
- **Navigation** is single-Activity with Fragment-based bottom tabs + separate Activities for detail/player
- **Video playback** uses ExoPlayer (Media3) with OkHttp + SimpleCache

## Modifying the Scraper
- Website: `https://v2.samehadaku.how`
- All selectors in `SamehadakuScraper.kt` are CSS selectors via Jsoup
- If website HTML structure changes, update selectors in the corresponding method
- Key methods: `getLatestEpisodes()`, `getOngoingAnime()`, `searchAnime()`, `getAnimeDetail()`, `getEpisodeServers()`

## Common Tasks
- **Add new section to Home:** Add RecyclerView in `fragment_home.xml`, create adapter, load data in `HomeFragment.loadData()`
- **Change app icon:** Red "W" vector in `drawable/ic_launcher_foreground.xml`, black bg in `ic_launcher_background.xml`, or add PNGs to `mipmap-*` folders
- **Add new screen:** Create Activity/Fragment, add to `AndroidManifest.xml`, wire navigation in `MainActivity`
- **Modify player behavior:** Edit `PlayerActivity.kt`, WebView settings are in `onCreate()`

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

## TODO / Next Session
- **VIP Streaming (filedon.co)**: Extract video URL from `filedon.co/embed/...` pages
- **Auto play next episode**: Implement automatic playback of next episode when current finishes
