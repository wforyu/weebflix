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
│   ├── WeebFlixApp.kt                  # Application class, provider registry init
│   ├── data/
│   │   ├── config/ProviderConfig.kt    # Per-provider base URL + active provider via SharedPreferences
│   │   ├── model/
│   │   │   ├── Models.kt              # Anime, Episode, VideoServer, AnimeDetail, EpisodeNavigation
│   │   │   └── WatchHistoryManager.kt # Watch progress storage (per provider, SharedPreferences)
│   │   ├── provider/
│   │   │   ├── AnimeProvider.kt       # Provider interface (all scraper methods)
│   │   │   └── ProviderFactory.kt     # Singleton registry, getActiveProvider(), refreshBaseUrls()
│   │   └── scraper/
│   │       ├── SamehadakuScraper.kt   # Anime scraper (Jsoup) — implements AnimeProvider
│   │       └── DrakorKitaScraper.kt   # Drakor scraper (Jsoup) — implements AnimeProvider
│   └── ui/
│       ├── splash/SplashActivity.kt      # Splash with animated N logo
│       ├── main/MainActivity.kt          # Bottom nav host (Home/Search/Ongoing/Settings)
│       ├── home/
│       │   ├── HomeFragment.kt           # Provider chip switcher + fragment container
│       │   ├── SamehadakuHomeFragment.kt # Samehadaku home (static hero + 3 rows)
│       │   └── DrakorKitaHomeFragment.kt # DrakorKita home (auto-scroll hero + 3 rows)
│       ├── search/SearchFragment.kt      # Real-time search with history
│       ├── ongoing/OngoingFragment.kt    # Grid with vertical infinite scroll
│       ├── settings/SettingsFragment.kt  # Per-provider domain config (Fragment, not Activity)
│       ├── detail/
│       │   ├── AnimeDetailActivity.kt    # Parallax detail + episode list
│       │   └── CategoryGridActivity.kt   # Full-screen 3-col grid (DrakorKita categories)
│       ├── player/PlayerActivity.kt      # ExoPlayer + WebView + multi-provider server resolution
│       └── adapter/
│           ├── LatestEpisodeAdapter.kt   # Samehadaku episode cards
│           ├── AnimeAdapter.kt           # Samehadaku anime cards
│           ├── NetflixCardAdapter.kt     # Netflix-style compact card (DrakorKita)
│           ├── DramaCardAdapter.kt       # Drama card with rating badge
│           ├── HeroPagerAdapter.kt       # ViewPager2 hero banner carousel
│           ├── EpisodeListAdapter.kt     # Episode list with spinner
│           ├── SearchGridAdapter.kt      # Search results grid
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
- **Multi-provider architecture:** All scrapers implement `AnimeProvider` interface, registered via `ProviderFactory`
- **Networking:** Each scraper uses its own OkHttp client (DrakorKita uses trust-all SSL certs)
- **Image loading** uses Glide with `.placeholder(R.drawable.bg_card)` fallback
- **Navigation** is single-Activity with Fragment-based bottom tabs + separate Activities for detail/player/category grid
- **Video playback** uses ExoPlayer (Media3) with OkHttp + SimpleCache
- **Provider switching** happens in HomeFragment via ChipGroup, content fragment swaps dynamically

## Provider Architecture
- **`AnimeProvider` interface:** `id`, `name`, `baseUrl`, `getLatestEpisodes()`, `getOngoingAnime()`, `getPopularAnime()`, `searchAnime()`, `getAnimeDetail()`, `getEpisodeServers()`, `resolveServerVideoUrl()`, `getEpisodeNavigation()`
- **`ProviderFactory`:** Singleton registry, lazy-init all providers, `getActiveProvider()` reads from `ProviderConfig.activeProviderId`
- **`ProviderConfig`:** Stores per-provider base URLs (`base_url_samehadaku`, `base_url_drakorkita`) and active provider ID in SharedPreferences
- **Active provider** is persisted — app remembers last selected provider across restarts

## Providers
### Samehadaku
- Website: `https://v2.samehadaku.how`
- Content: Anime (Latest Episodes, Ongoing, Popular)
- Scraper: `SamehadakuScraper.kt` — CSS selectors via Jsoup
- Key methods: `getLatestEpisodes(page)`, `getOngoingAnime(page)`, `getPopularAnime(page)`, `searchAnime(query)`, `getAnimeDetail(url)`, `getEpisodeServers(url)`

### DrakorKita
- Website: `https://drakor.kita.mobi` (also supports legacy domains: nicewap.sbs, drakorita.com/net/cyou/cfd)
- Content: Korean Drama (Latest Episodes, Movies, Series)
- Scraper: `DrakorKitaScraper.kt` — CSS selectors via Jsoup + API calls to `nonton.bid`
- Features: Auto-rewrites dead domain URLs to current domain, trust-all SSL certs, Base64 token decoding for API access
- Key methods: `getHomeContent()` (returns episodes + movies + series + featured), `getAllAnime(page)`, `getEpisodeServers()`, `getEpisodeNavigation()`

### OppaDrama
- Website: `http://45.11.57.192` (default)
- Content: Korean Drama (Latest Episodes, Movies, Series)
- Scraper: `OppaDramaScraper.kt` — Jsoup + JSON API endpoints + token-based server resolution
- Features: Cookie-based auth, token extraction from episode page (Base64 encoded), server resolution via `oppadrama/api/v2` endpoints, turboviplay CDN support with Referer validation
- Key methods: `getHomeContent()`, `getAllAnime(page)`, `getAnimeDetail()`, `getEpisodeServers()`, `getEpisodeNavigation()`
- Server resolution: Extracts `oppaDramaData` JSON from episode page, resolves Hydrax token via `api/v2/getToken.php`, resolves server via `api/v2/server.php`, final video URL via `api/v2/video_hydrax.php` or turboviplay CDN

## Features
- **Home:** Provider chip switcher, each provider has its own home fragment:
  - Samehadaku: Static hero + Continue Watching + Latest Episode + Ongoing + Popular (infinite scroll)
  - DrakorKita: Auto-scrolling ViewPager2 hero carousel (4s interval) + Continue Watching + Episodes + Movies + Series (infinite scroll)
- **Search:** Real-time search with debounce (500ms) + Search history (SharedPreferences, max 20)
- **Ongoing:** Full paginated grid of all ongoing anime with vertical infinite scroll + footer loading
- **Category Grid:** Full-screen 3-column grid for DrakorKita categories (Episodes/Movies/Series/All) with infinite scroll
- **Detail:** Parallax banner, synopsis, info, episode list with spinner range selector (100 eps/chunk)
- **Player:** ExoPlayer, server picker (floating PopupWindow), gestures (brightness/volume/seek), skip opening/outro, auto-play next episode, PiP support, fullscreen toggle, prev/next episode navigation
- **Settings:** Per-provider domain configuration with chip selector, validation, and reset
- **Continue Watching:** Saves watch progress per episode per provider, shows progress bar on Home, auto-resumes from last position
- **Domain Switching:** Change scraper base URL per provider from Settings

## Video Server Resolution
### Blogspot Server (Samehadaku - WORKING)
- Fast path: Scraper AJAX POST → get `blogger.com/video.g?token=` URL → WebView loads video.g with XHR interception
- XHR interception injects JS into `video.g` HTML that monkey-patches `XMLHttpRequest.prototype.send` to capture batchexecute responses containing `googlevideo.com` URLs
- URL cleaning: batchexecute responses have double-encoded escaping (`\\u003d`, `\\u0026`, `\\/`), handled by replace chain + final `replace(/\\/g, '')` to strip residual backslashes
- `interceptBloggerHtml()` in `PlayerActivity.kt` fetches video.g via OkHttp, injects XHR script, returns modified HTML
- `shouldInterceptRequest()` routes `blogger.com/video.g` to `interceptBloggerHtml()`
- `onUrlFound()` bridge receives clean URL → `initExoPlayer()`
- Server detection: `server.name.contains("Blogspot")` or `server.url.contains("blogger.com")` or `server.url.contains("bp.blogspot.com")`

### DrakorKita Server (WORKING)
- 3-step API resolution pipeline:
  1. GET `episode.php` → get `server_xid` and `first_ep_id`
  2. POST `server.php` with episode_id, cat, tag, server_xid, c, t → extract direct URL or Abyss CDN ID
  3. POST `video_hydrax.php` → extract final video URL
- Tokens (`c`, `t`) obtained via WebView JS injection: reads global variables from episode page, sends back via `AndroidBridge.onTokensFound()`
- Abyss CDN: decodes `atob()` payload to extract direct `.mp4` URL
- `resolveDrakorKitaWithWebView()` orchestrates the full pipeline

### Other Servers
- **Wibufile 720p**: AJAX iframe src IS a direct `.mp4` URL (`https://s0.wibufile.com/video01/...mp4`) — plays directly
- **filedon.co (VIP STREAMING)**: Embed loads via `https://filedon.co/embed/...` — needs further extraction (TODO)
- **Wibufile 480p**: `ERR_SSL_PROTOCOL_ERROR` — device/server incompatibility, cannot fix
- **OppaDrama / turboviplay CDN**: After Hydrax token resolves to `cdn2.turboviplay.com/data3/.../....m3u8`, CDN requires `Referer: https://emturbovid.com/` and `Origin: https://emturbovid.com/` headers; without them TS segments fail with `Cannot find sync byte` after a few seconds

## ExoPlayer Configuration
- Buffer: `minBufferMs=15s`, `maxBufferMs=60s`, `bufferForPlaybackMs=2.5s`, `bufferForPlaybackAfterRebufferMs=1.5s`
- Cache: `SimpleCache` with 250MB limit
- OkHttp: Adds `Referer`/`Origin` headers for `googlevideo.com`, `abysscdn.com`/`hydrax`/`drakor.bid`, and `turboviplay.com` URLs
- Track selector: Max 1920x1080, preferred audio `id` (Indonesian)
- Episode navigation: `EpisodeNavigation` data class with prev/next URLs, auto-play chain pre-fetches next-next episode

## Modifying the Scraper
### Samehadaku
- Website: `https://v2.samehadaku.how`
- All selectors in `SamehadakuScraper.kt` are CSS selectors via Jsoup
- If website HTML structure changes, update selectors in the corresponding method
- Key methods: `getLatestEpisodes(page)`, `getOngoingAnime(page)`, `getPopularAnime(page)`, `searchAnime(query)`, `getAnimeDetail(url)`, `getEpisodeServers(url)`

### DrakorKita
- Website: `https://drakor.kita.mobi`
- CSS selectors in `DrakorKitaScraper.kt` (`.bungkus`, `.titit`, `img.poster`, `.rate`, etc.)
- Token decoding: `decodePageTokens()` — Base64 decode, digit extraction, character code parsing
- API endpoints: `api.nonton.bid/c_api/episode.php`, `server.php`, `video_hydrax.php`
- Domain migration: Old URLs auto-rewritten via `rewriteToCurrentDomain()`

## Common Tasks
- **Add new provider:** Implement `AnimeProvider` interface, register in `ProviderFactory`, add chip in `HomeFragment`, add config key in `ProviderConfig`
- **Add new section to Home:** Add RecyclerView in provider's home fragment layout, create adapter, load data in fragment
- **Change app icon:** Edit `drawable/ic_launcher_foreground.xml` (vector N) + `drawable/ic_launcher_background.xml` (black)
- **Add new screen:** Create Activity/Fragment, add to `AndroidManifest.xml`, wire navigation
- **Modify player behavior:** Edit `PlayerActivity.kt`, check `ResolveMode` enum for provider-specific paths
- **Release APK:** Run `.\gradlew.bat assembleRelease` (unsigned by default, see keystore.md for signing)

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
| DrakorKita SSL errors | Trust-all SSL certificates on OkHttpClient |
| DrakorKita dead domains | Auto-rewrite old domain URLs to current domain in scraper |
| Stale WebView callbacks | `resolveGeneration` counter prevents old callbacks from being processed |
| Video plays few seconds then disconnects (turboviplay CDN) | Added Referer/Origin headers for `turboviplay.com` domain in OkHttp interceptor and ExoPlayer `defaultRequestProperties` |
| HTML embed page played directly as video URL | Generic `server.videoUrl` check now requires `isDirectVideo` (`.mp4`/`.m3u8`/`.mpd`/`googlevideo.com`) before passing to ExoPlayer |
| OppaDrama servers fail to resolve | Token-based pipeline: extract `oppaDramaData` JSON → resolve Hydrax token → resolve server via API v2 |

## TODO / Next Session
- **VIP Streaming (filedon.co)**: Extract video URL from `filedon.co/embed/...` pages
- **Auto play next episode**: Implement automatic playback of next episode when current finishes (partially done via auto-play overlay)
- **Add more providers**: Implement `AnimeProvider` interface for new content sources
