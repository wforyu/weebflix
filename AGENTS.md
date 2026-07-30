# Agents.md

## Build & Run
- **Build:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"; .\gradlew.bat installDebug`
- **Release Build:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"; .\gradlew.bat assembleRelease`
- **Gradle:** 9.5.0, AGP 9.3.0, KSP 2.2.10-2.0.2 (for Glide)
- **Compile SDK:** 35 (Android 15) — required by media3 1.5.1
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
│   │       └── OppaDramaScraper.kt    # Drakor scraper (Jsoup) — implements AnimeProvider
│   └── ui/
│       ├── splash/SplashActivity.kt      # Splash with animated N logo
│       ├── main/MainActivity.kt          # Bottom nav host (Home/Search/Ongoing/Settings)
│       ├── home/
│       │   ├── HomeFragment.kt           # Provider chip switcher + fragment container
│       │   ├── SamehadakuHomeFragment.kt # Samehadaku home (static hero + 3 rows)
│       │   ├── DrakorKitaHomeFragment.kt # DrakorKita home (auto-scroll hero + 3 rows)
│       │   └── OppaDramaHomeFragment.kt # DrakorKita home (5 clickable sections + h-scroll)
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
- **OppaDrama Home:** Provider-specific home fragment (`OppaDramaHomeFragment.kt`) with 5 clickable sections: Eps Terbaru, Drama Korea, Drama China, Film Korea, Netflix. Each section has horizontal infinite scroll and "Lihat Semua" opens `CategoryGridActivity`
- **CategoryGridActivity:** Supports OppaDrama categories (`CATEGORY_DRAMA_KOREA`, `CATEGORY_DRAMA_CHINA`, `CATEGORY_FILM_KOREA`, `CATEGORY_NETFLIX`) with infinite scroll

## Features
- **Home:** Provider chip switcher, each provider has its own home fragment:
  - Samehadaku: Static hero + Continue Watching + Latest Episode + Ongoing + Popular (infinite scroll)
  - DrakorKita: Auto-scrolling ViewPager2 hero carousel (4s interval) + Continue Watching + Episodes + Movies + Series (infinite scroll)
  - OppaDrama: 5 clickable section headers (Eps Terbaru, Drama Korea, Drama China, Film Korea, Netflix) + horizontal infinite scroll per section
- **Search:** Real-time search with debounce (500ms) + Search history (SharedPreferences, max 20)
- **Ongoing:** Full paginated grid of all ongoing anime with vertical infinite scroll + footer loading
- **Category Grid:** Full-screen 3-column grid for DrakorKita and OppaDrama categories (Episodes/Movies/Series/Drama Korea/Drama China/Film Korea/Netflix) with infinite scroll
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
- **Wibufile 480p**: `ERR_SSL_PROTOCOL_ERROR` — device/server incompatibility, cannot fix

### TurboVIP / Hydrax Server (OppaDrama - PARTIAL — rate limited)
- Server URL pattern: `emturbovid.com/t/{id}` → resolves to `https://cdn2.turboviplay.com/data3/{id}/{id}.m3u8`
- **CDN chain:** master m3u8 (cdn2.turboviplay.com) → sub-playlist (g266.turbosplayer.com) → .ts segments (lh3.googleusercontent.com)
- **Resolution:** WebView intercepts m3u8 URL from embed page → bundled hls.js + OkHttp proxy
- **Rate limiting:** Google CDN (`lh3.googleusercontent.com`) rate-limits after ~5-8 segment requests → 429 HTML
- **429 retry:** OkHttp proxy retries 4 times with Retry-After backoff
- **hls.js config:** `maxParallelFrags:1`, `startFragPrefetch:false`, `fragLoadingRetry:15000`, `startLevel:0`
- **Result:** Plays first ~10s, then buffers/retries through 429

## ExoPlayer Configuration
- Buffer: `minBufferMs=10s`, `maxBufferMs=45s`, `bufferForPlaybackMs=3s`, `bufferForPlaybackAfterRebufferMs=2s` (aggressive low buffer to avoid 429 rate limiting on turboviplay CDN)
- Cache: `SimpleCache` with 250MB limit, turboviplay URLs bypass cache (unique cache key) to prevent stale re-fetches
- OkHttp: Adds `Referer`/`Origin` headers for `googlevideo.com`, `abysscdn.com`/`hydrax`/`drakor.bid`, and `turboviplay.com` URLs (Referer = `turbovidhls.com`)
- TurboVI segment rate limiting: 80ms delay per `.ts`/`data3/` segment request to throttle CDN requests
- Retry: 429-specific retry with `Retry-After` header support (4 retries, 5s backoff for 429)
- Auto-retry on "Cannot find sync byte": Detects sync byte / Transport Stream errors (indicates 429 HTML response), retries same URL after 3s delay instead of auto-failing to next server
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

## Open Bugs (Still Buggy — Needs Further Investigation)

### 1. OppaDrama turboviplay CDN — HTTP 429 rate limiting (PERSISTENT)
- **Server:** TurboVIP → `emturbovid.com/t/{id}` → resolves to `https://cdn2.turboviplay.com/data3/{id}/{id}.m3u8`
- **CDN chain:** `cdn2.turboviplay.com` (master m3u8) → `g266.turbosplayer.com/file/{uuid}/master.m3u8` (sub-playlist) → `lh3.googleusercontent.com/d/{id}=d` (.ts segments)
- **Root cause:** `lh3.googleusercontent.com` is Google's CDN hosting the actual .ts video segments. It applies **per-IP rate limiting** — after ~5-8 segment requests (approx 10-15s of playback), ALL subsequent requests return **HTTP 429** with HTML error page (`<!DOCTYP...`). This is a server-side rate limit on free video hosting, NOT something that can be fully bypassed client-side.
- **CORS origin in embed page:** `https://turbovidhls.com` (NOT `emturbovid.com`)

#### Attempted fixes (ALL FAILED — rate limit is server-side, not client-side):

| # | Approach | Result | Why it failed |
|---|----------|--------|---------------|
| v1 | ExoPlayer + Referer/Origin `emturbovid.com` | 429 after ~80s | Rate limit is IP-based, not Referer-based |
| v2 | Changed Referer/Origin to `turbovidhls.com` | 429 after 30s-2min | Still IP-based rate limit |
| v3 | Reduced buffer 120s→60s + 429 retry w/ Retry-After (4 retries, 3s backoff) | 429 persists | CDN blocks for extended period, not just per-request |
| v4 | Buffer 10s/45s + 80ms segment delay + cache bypass + sync byte retry | 429 persists | Rate limit accumulates across sessions |
| v5 | Switched to WebView + bundled hls.js + OkHttp proxy | 429 after ~10s (same segments) | Proxy adds latency, CDN rate limit unchanged |
| v6 | OkHttp proxy with 429 retry (up to 4 retries w/ Retry-After) | 429 persists for ALL requests | Once rate-limited, CDN blocks entire IP for minutes |
| v7 | hls.js tuning (maxParallelFrags:1, no prefetch, startLevel:0) | 429 after ~10s | Single-threading helps slightly but CDN still rate-limits |
| v8 | Removed googleusercontent from proxy filter (direct WebView) | 429 after ~10s | CDN rate-limits regardless of fetch method |
| v9 | Added `googleusercontent` to CDN filter | 429 immediately | Proxy + rate-limited CDN = worse |

#### Key observations from logcat analysis:
- **Master m3u8** (cdn2.turboviplay.com): 363 bytes, always loads OK ✓
- **Sub-playlist** (g266.turbosplayer.com): 44056 bytes, always loads OK ✓ (after adding to filter)
- **.ts segments** (lh3.googleusercontent.com): 429 after ~5-8 requests ✗
- First 5-6 segments play fine, then 429 kicks in for ALL subsequent segments
- Even after waiting 30+ minutes, 429 persists for previously-requested URLs
- The 429 response is HTML (`hex=3c21444f43545950` = `<!DOCTYP`), which causes `fragParsingError` when hls.js tries to parse it as TS

#### Research findings (from web search):
1. **lh3.googleusercontent.com 429 is well-documented** — Google rate-limits this CDN for content hosted on Google Drive. See [GitHub issue #60](https://github.com/ArdiArtani/Google-Drive-Player-Script/issues/60): "429 - The rate limit has been exceeded"
2. **turboviplay.com** is a free video hosting platform that uses Google CDN for HLS segments — they don't control the rate limit
3. **Best practice for CDN 429**: Exponential backoff + jitter, respect Retry-After, reduce concurrency — but these only DELAY the 429, they don't prevent it
4. **HLS Proxy pattern** (node-HLS-Proxy, mediaflow-proxy): Segment prefetch + cache ahead of time distributes requests. But in an Android app without a server, we can't do true prefetch
5. **IP rotation** is the only real bypass for per-IP rate limits — not feasible on a single Android device

#### What WOULD work (but is complex/expensive):
- **External proxy server** with multiple IPs (residential proxies, Cloudflare Workers)
- **Segment prefetch**: Download first 30-60s of segments before playback starts (need to parse m3u8, extract segment URLs, download in background)
- **TurboVIPlay Premium**: Paid plan may have higher/zero rate limits
- **Accept limitation**: Play first ~10s, buffer/retry periodically, the video WILL eventually play through with enough retries

#### Current state (latest build):
- WebView + bundled hls.js + OkHttp proxy with ISO-8859-1 binary encoding
- 429 retry in proxy (4 retries with Retry-After)
- hls.js: `maxParallelFrags:1`, `startFragPrefetch:false`, `fragLoadingRetry:15000`, `startLevel:0`
- All CDN domains in filter: turboviplay, turbovid, turbosplayer, abysscdn, hydrax, googlevideo, googleusercontent, cdn2.
- **Result**: First ~10s plays, then 429 → `fragParsingError` → hls.js retries → eventually plays through with gaps

#### Code locations:
- OkHttp proxy with 429 retry: `PlayerActivity.kt` L826-845
- hls.js config: `PlayerActivity.kt` L928-936
- CDN filter: `PlayerActivity.kt` L807-810
- `playVideoViaHtml5WebView()`: L868+

### 2. OppaDrama FileLions (minochinos.com) — WebView loads embed, no video URL intercepted
- **Server:** FileLions → `https://minochinos.com/v/5k9cuh96zb25`
- **Symptom:** WebView loads embed page but no video URL intercepted — embed page JS doesn't expose video URL in a way our JS extraction can find it
- **Attempted fixes:**
  - v1: Added `minochinos.com`/`filelions` to `shouldInterceptRequest` isVideoUrl → BROKE IT (page URL itself intercepted, ExoPlayer got HTML not video → `UnrecognizedInputFormatException`)
  - v2: Removed `minochinos.com`/`filelions` from isVideoUrl + added `extractFileLionsVideoJs()` + increased timeout to 15s → no video found
  - v3: Fixed iframe bug (only send video URLs), timeout 15s→20s, grace period 5s→8s, added `scanObjectEmbed()`, `setAttribute` interception, `onProgressChanged` handles FileLions directly, added OkHttp HTML fallback (`extractFileLionsViaOkHttp()`) → **TESTING** (commit `e408bb6`)
- **Code locations:** `PlayerActivity.kt` `resolveEmbedUrlViaWebView()` (L2348-2354 timeout), `shouldInterceptRequest()` (L467-475 isVideoUrl — must NOT contain minochinos.com), `extractFileLionsVideoJs()` (L2714+), `extractFileLionsViaOkHttp()` (L2914+), `onProgressChanged` (L524+ FileLions branch)
- **Possible next steps if v3 still fails:**
  - Use Chrome DevTools Protocol (CDP) via WebView to inspect network requests after page load
  - Check if minochinos.com loads a sub-iframe (another domain) that contains the actual player — navigate WebView into iframe
  - FileLions may use blob URLs or WebM players that can't be intercepted via XHR
  - Try using a headless HTTP client to follow all redirects and parse the final page

### 3. OppaDrama Hydrax server — Same turboviplay CDN failure
- **Server:** Hydrax → loads episode page → WebView intercepts same `cdn2.turboviplay.com` URL
- **Symptom:** Same HTTP 429 rate limiting after ~10s (uses same CDN as bug #1)
- **Note:** Will be fixed if bug #1 is fixed, since both resolve to the same turboviplay CDN URL

## TODO / Next Session
- **Test turboviplay 429 proxy retry** — latest build has OkHttp 429 retry (4 retries w/ Retry-After) + hls.js `maxParallelFrags:1` + `startFragPrefetch:false` + all CDN domains in filter
- **Test FileLions v3 fix** (commit `e408bb6`) — fixed JS extraction + OkHttp fallback
- **VIP Streaming (filedon.co)**: Extract video URL from `filedon.co/embed/...` pages
- **Auto play next episode**: Implement automatic playback of next episode when current finishes (partially done via auto-play overlay)
- **Add more providers**: Implement `AnimeProvider` interface for new content sources
