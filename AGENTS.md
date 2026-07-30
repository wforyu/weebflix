# Agents.md

## Build & Run
- **Build:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat installDebug`
- **Release Build:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleRelease`
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
│   │       ├── OppaDramaScraper.kt    # Drakor scraper (Jsoup) — implements AnimeProvider
│   │       └── AnichinScraper.kt     # Donghua/Anime scraper (Jsoup) — implements AnimeProvider
│   └── ui/
│       ├── splash/SplashActivity.kt      # Splash with animated N logo
│       ├── main/MainActivity.kt          # Bottom nav host (Home/Search/Ongoing/Settings)
│       ├── home/
│       │   ├── HomeFragment.kt           # Provider chip switcher + fragment container
│       │   ├── SamehadakuHomeFragment.kt # Samehadaku home (static hero + 3 rows)
│       │   ├── DrakorKitaHomeFragment.kt # DrakorKita home (auto-scroll hero + 3 rows)
│   │       ├── OppaDramaHomeFragment.kt # DrakorKita home (5 clickable sections + h-scroll)
│   │       └── AnichinHomeFragment.kt  # Anichin home (latest + ongoing)
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
- **`ProviderConfig`:** Stores per-provider base URLs (`base_url_samehadaku`, `base_url_drakorkita`, `base_url_anichin`) and active provider ID in SharedPreferences
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

### Anichin
- Website: `https://anichin.cafe`
- Content: Donghua/Anime (Latest Episodes, Ongoing, Completed)
- CMS: WordPress with animestream theme by themesia
- Scraper: `AnichinScraper.kt` — Jsoup CSS selectors
- Key methods: `getLatestEpisodes(page)` (homepage latest), `getOngoingAnime(page)` (`/ongoing/page/{N}/`), `getPopularAnime(page)` (`/completed/page/{N}/`), `searchAnime(query)`, `getAnimeDetail(url)`, `getEpisodeServers(url)` (base64-decoded `<select class="mirror">`), `getEpisodeNavigation(url)` (`a[rel=prev/next]`)
- **Server resolution:** Main player is `anichin.stream/?id={id}` (JWPlayer HLS) — extracted via unpacked eval'd JS for m3u8 URL or WebView `shouldInterceptRequest` `.m3u8` interception. AbyssCDN/hydrax URLs handled by existing resolution code. Other servers (OK.ru, Rumble) → WebView fallback
- **Home:** Provider-specific home (`AnichinHomeFragment.kt`) with Continue Watching + Latest Episodes + Ongoing (horizontal scroll, infinite scroll)
- **CategoryGridActivity:** Falls through to `activeProvider.getOngoingAnime(currentPage)` (generic handler)

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
  - Anichin: Continue Watching + Latest Episodes + Ongoing (horizontal infinite scroll)
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

### DrakorKita Server (WORKING — direct WebView playback, skip API pipeline)
- **Approach:** Load path-based URL (`/detail/slug/tag_cat/epNum/`) directly in WebView. Bypass the API token-resolution pipeline entirely.
- **Reference:** https://github.com/wforyu/drakorkita — the page itself handles server resolution via JS after loading the correct URL.
- **Code:** `PlayerActivity.kt` DrakorKita dispatch (L3945+):
  - `playEpisodePageViaWebView()` with `skipInjections=true` and `customCleanJs=REF_INJECT_ADBLOCK_ONLY`
  - Manual injection of toggle + auto-click JS via `webView?.postDelayed` at 4s
- **JS injection features:**
  - **⛶/✕ button:** Fixed-position floating button (z-index 9999999) that toggles video fullscreen via CSS (`position:fixed; 100vw; 100vh; object-fit:contain`)
  - **Auto-hide:** Button fades to 15% opacity after 4s idle; reappears on any touch
  - **Resize listener:** Keeps fullscreen video matched to device viewport on orientation change
  - **Fullscreen API guard:** Intercepts `fullscreenchange` event and auto-exits native fullscreen (prefers CSS-based fullscreen)
  - **Auto-click fallback:** After 20s, auto-clicks the matching server button (only if no video is already playing)
- **Toggle fullscreen:** Uses CSS `position:fixed` + parent element fullscreen, not the Fullscreen API
- **Ad blocking:** `REF_INJECT_ADBLOCK_ONLY` — ad pattern removal + MutationObserver, no CSS/layout changes

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

### Anichin
- Website: `https://anichin.cafe`
- Card selectors: `div.listupd > article.bs > div.bsx > a` (title via `attr("title")`, poster via `img.ts-post-image[src]`, episode/status via `span.epx`, type via `span.typez`)
- Detail page: `h1.entry-title`, `div.desc`, metadata via `.info-content .spe span` (Status/Tipe/Episode/Studio), genres via `.genxed a`, episode list via `.eplister > ul > li`
- Episode servers: `<select class="mirror">` `<option>` values are base64-encoded iframe HTML — decode, extract `src`, return as `VideoServer`
- Navigation: `a[rel=prev]` / `a[rel=next]` inside `div.naveps.bignav`
- Video resolution: JWPlayer at `anichin.stream/?id={id}` — `resolveServerVideoUrl()` fetches page, unpacks eval'd JS, extracts `.m3u8` URL via regex patterns. Falls back to WebView interception via `shouldInterceptRequest` (`.m3u8` pattern matches)

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
| DrakorKita c/t tokens empty → `server.php` HTTP 500 | `resolveServerVideoUrl()` now calls `decodePageTokens()` as fallback (base64 dot-segment decode); WebView `onTokensFound()` also falls back to OkHttp + base64 decode |
| DrakorKita WebView infinite navigation loop | Server click → CDN redirect → page reload → auto-click re-fires → cycle. Fixed: `playEpisodePageViaWebView()` added `skipInjections` + `customCleanJs` params; DrakorKita uses `skipInjections=true` to avoid `REF_INJECT_CLEAN_PAGE`/`AUTOPLAY` re-inject; auto-click checks `video.paused && video.readyState` before clicking |
| DrakorKita JS `src.toLowerCase is not a function` | `str(v)` helper function in `REF_INJECT_CLEAN_PAGE` handles non-string `src`/`className` (SVGAnimatedString) |
| DrakorKita native controls hidden by clean page | `REF_INJECT_ADBLOCK_ONLY` created — ad-block only, no CSS/overflow/display:none changes |
| DrakorKita native fullscreen broken/cut off | CSS-based fullscreen via JS button (⛶) instead of Fullscreen API; `fullscreenchange` event auto-exits native FS; resize listener keeps viewport match |
| DrakorKita toggle button disappears on page nav | JS injected via Kotlin `postDelayed` (not in `onPageFinished`); `window._dkSetupDone` flag prevents double inject |
| CategoryGridActivity DrakorKita infinite scroll not working | 3 bugs fixed: (1) Episodes used `getHomeContent().latestEpisodes` (unpaginated, ~10 items) + `hasMore=false` → changed to `getAllAnime(page)` (paginated). (2) Movies page 1 used `getHomeContent().movies` (few items), page 2+ used `getOngoingAnime(page)` (gap) → now always uses `getOngoingAnime(page)`. (3) Series same as Movies → now always uses `getPopularAnime(page)` |

## Open Bugs (Still Buggy — Needs Further Investigation)

### 1. OppaDrama turboviplay CDN — HTTP 429 rate limiting after ~60-70s
- **Server:** TurboVIP → `emturbovid.com/t/6a6636b94a2fb` → WebView resolves to `https://cdn2.turboviplay.com/data3/6a6636b94a2fb/6a6636b94a2fb.m3u8`
- **Symptom:** Video plays for 60-70s then ExoPlayer throws "Cannot find sync byte" — CDN returns HTML error page (429) as TS segment data
- **CORS origin in embed page:** `https://turbovidhls.com` (NOT `emturbovid.com`)
- **Root cause:** CDN rate-limits segment requests after sustained streaming (~60-70s), sends HTML error page instead of .ts data
- **Attempted fixes:**
  - v1-v3: Various Referer/Origin + buffer reductions → 429 persists
  - v4: 80ms segment delay + 10s/45s buffer + cache bypass + sync byte retry → 429 after 60-70s
  - **v5 (current):** 120ms segment delay + exponential backoff sync byte retry (5s/10s, max 2 retries) + reset retry counter on server switch/STATE_READY
- **Logcat findings:**
  - `setAudioAttributes(USAGE_MEDIA, AUDIO_CONTENT_TYPE_MOVIE)` fixed partial audio mute (was fmt 5/AAudio → now fmt 1/MediaCodec, audio [fine] 60s+ with brief 1-2s [mute])
  - Video renders frames but CDN rate limit kills playback after ~60-70s
  - Sync byte retry with 3s delay too fast → bumped to 5s*retryCount
- **Code locations:** `PlayerActivity.kt` OkHttp interceptor (L122-147, 120ms delay + 429 retry), `initExoPlayer` (L1362+, setAudioAttributes at L1329-1335), loadControl (L1403-1411), cache key factory (L1392-1401), onPlayerError sync byte retry (L1483-1508, exponential backoff + maxSyncByteRetries=2)
- **Possible next steps if v5 still fails:**
  - Try fetching m3u8 manually via OkHttp to check if rate limit is per-session or per-IP
  - Try completely bypassing ExoPlayer cache + using a dedicated non-shared OkHttpClient for turboviplay
  - CDN may have a fixed rate limit per IP per minute — need to measure how many segments/min it allows
  - Try switching to a different CDN or proxy approach
  - Try lower maxBufferMs (e.g. 30s) to reduce concurrent segment requests

### 2. OppaDrama FileLions (minochinos.com) — WebView loads embed, no video URL intercepted
- **Server:** FileLions → `https://minochinos.com/v/5k9cuh96zb25`
- **Symptom:** WebView loads embed page but no video URL intercepted — embed page JS doesn't expose video URL in a way our JS extraction can find it
- **Attempted fixes:**
  - v1: Added `minochinos.com`/`filelions` to `shouldInterceptRequest` isVideoUrl → BROKE IT (page URL itself intercepted, ExoPlayer got HTML not video → `UnrecognizedInputFormatException`)
  - v2: Removed `minochinos.com`/`filelions` from isVideoUrl + added `extractFileLionsVideoJs()` + increased timeout to 15s → no video found
  - v3: Fixed iframe bug (only send video URLs), timeout 15s→20s, grace period 5s→8s, added `scanObjectEmbed()`, `setAttribute` interception, `onProgressChanged` handles FileLions directly, added OkHttp HTML fallback (`extractFileLionsViaOkHttp()`) → **TESTING** (commit `e408bb6`)
  - v4 (current): trustAllCerts on OkHttp + iframe enumeration from page HTML + cookie passing from WebView + navigate into sub-iframe + retry all iframes via OkHttp
- **Logcat findings:**
  - OkHttp fallback fails in 9ms with `null` error → likely DNS resolution failure (minochinos.com unreachable from device) or Cloudflare blocking
  - trustAllCerts alone didn't fix it, confirms it's not SSL but DNS/connection
  - New flow: JS extraction → enumerate iframes → navigate to video host iframe → re-extract → OkHttp fallback with cookies
- **Code locations:** `PlayerActivity.kt` `resolveEmbedUrlViaWebView()` (L2447-2545 iframe enumeration), `shouldInterceptRequest()` (L469-486 isVideoUrl — must NOT contain minochinos.com), `extractFileLionsVideoJs()` (L2782+), `extractFileLionsViaOkHttp()` (L2934+, trustAllCerts + cookies + better headers), `onProgressChanged` (L529-581 FileLions branch)
- **Possible next steps if v4 still fails:**
  - Use Chrome DevTools Protocol (CDP) via WebView to inspect network requests after page load
  - Check if minochinos.com is completely down / domain not resolving
  - Try alternative embed URL sources for FileLions content
  - FileLions may use blob URLs or WebM players that can't be intercepted via XHR

### 3. OppaDrama Hydrax server — Same turboviplay CDN failure
- **Server:** Hydrax → loads episode page → WebView intercepts same `cdn2.turboviplay.com` URL
- **Symptom:** Same HTTP 429 rate limiting after ~10s (uses same CDN as bug #1)
- **Note:** Will be fixed if bug #1 is fixed, since both resolve to the same turboviplay CDN URL

## TODO / Next Session
- **Test Anichin provider** — test all methods (home, detail, search, player video resolution via anichin.stream m3u8)
- **Test turboviplay v5 fix** — 120ms segment delay + exponential backoff sync byte retry (5s/10s, max 2 retries)
- **Test FileLions v4 fix** — iframe enumeration + cookie passing + sub-iframe navigation
- **VIP Streaming (filedon.co)**: Extract video URL from `filedon.co/embed/...` pages
- **Auto play next episode**: Implement automatic playback of next episode when current finishes (partially done via auto-play overlay)
- **Add more providers**: Implement `AnimeProvider` interface for new content sources
- **DrakorKita episode selection**: Choose specific episode from AnimeDetail → ensure path-based URL uses correct `epNum` from selected episode
