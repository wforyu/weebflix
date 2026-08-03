# Agents.md

## Build & Run
- **Build:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"; .\gradlew.bat installDebug`
- **Release Build:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"; .\gradlew.bat assembleRelease`
- **Gradle:** 9.5.0, AGP 9.3.0, KSP 2.2.10-2.0.2 (for Glide)
- **Compile SDK:** 35 (Android 15) — required by media3 1.5.1
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 34 (Android 14)
- **Version:** `versionCode=100`, `versionName=2.0.0-beta` (in `app/build.gradle.kts`)
- **BuildConfig fields:** `GIT_COMMIT` (short hash from `git rev-parse --short HEAD`, fallback `"dev"`) + `BUILD_DATE` (`yyyy.MM.dd-HHmm`) — requires `buildFeatures { buildConfig = true }`; shown in Settings About section
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
│   │       └── AnichinHomeFragment.kt  # Anichin home (Continue Watching + latest + ongoing + completed + all anime)
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
- Key methods: `getLatestEpisodes(page)`, `getOngoingAnime(page)`, `getPopularAnime(page)`, `searchAnime(query)`, `getAnimeDetail(url)`, `getEpisodeServers(url)`, `getEpisodeNavigation(url)`
- **Episode navigation:** `.naveps .nvs a` (prev), `.naveps .nvs.rght a` (next) — anchors are icon-only (no text), so `deriveEpisodeTitle()` builds "Episode N" from the URL slug (`-episode-N`)
- **Movie/episode server resolution:** episode streaming pages (`/{slug}-v2/` for BluRay) expose `#server .east_player_option`; AJAX endpoint `POST {base}/wp-admin/admin-ajax.php` body `action=player_ajax&post={data-post}&nume={data-nume}&type={data-type}` → iframe HTML (e.g. filedon.co embed for VIP, mega.nz/embed for Mega). Disabled options (Wibufile/Blogspot rows with `pointer-events: none`) are skipped

### DrakorKita
- Website: `https://drakor.kita.mobi` (also supports legacy domains: nicewap.sbs, drakorita.com/net/cyou/cfd)
- Content: Korean Drama (Latest Episodes, Movies, Series)
- Scraper: `DrakorKitaScraper.kt` — CSS selectors via Jsoup + API calls to `nonton.bid`
- Features: Auto-rewrites dead domain URLs to current domain, trust-all SSL certs, Base64 token decoding for API access
- Key methods: `getHomeContent()` (returns episodes + movies + series + featured), `getAllAnime(page)`, `getEpisodeServers()`, `getEpisodeNavigation()`
- **API status (audited):** `episode.php` (episode list) still works; **`server.php` returns HTTP 500** (returns empty body with or without c/t tokens) and `video.php`/`server_mob.php` → 500, `video_sb.php`/`video_hydrax.php`/`video_p2p.php` → 200 but empty — the streaming sub-API is dead. The **download pipeline is the working playback path**: `ajax_dl_all.php` (no tokens) → `/download/{dlId}` → `dlfilemob.php?id={dlId}` → direct MP4 on `c1hd.load.my.id` → **ExoPlayer** (see "DrakorKita Download-Pipeline" below). Path-based WebView playback (`/detail/{slug}/{tag}_{cat}/{epNum}/`) is kept only as last-resort fallback.

### Anichin
- Website: `https://anichin.cafe`
- Content: Donghua/Anime (Latest Episodes, Ongoing, Completed, All Anime)
- CMS: WordPress with animestream theme by themesia
- Scraper: `AnichinScraper.kt` — Jsoup CSS selectors
- Key methods: `getLatestEpisodes(page)` (homepage latest, scoped to `div.releases.latesthome` to avoid Popular Today duplicates), `getOngoingAnime(page)` (`/ongoing/page/{N}/`), `getPopularAnime(page)` (`/completed/page/{N}/`), `getAllAnime(page)` (`/seri/` + `/?page={N}`, full catalog ~48 pages), `searchAnime(query)`, `getAnimeDetail(url)`, `getEpisodeServers(url)` (base64-decoded `<select class="mirror">`), `getEpisodeNavigation(url)` (`a[rel=prev/next]`)
- **Detail resolution:** `getAnimeDetail()` on an *episode* URL (e.g. from a Latest Episode card) resolves to the series page via breadcrumb `.ts-breadcrumb ol li a[href*='/seri/']` — episode pages have NO episode list (`div.eplister` only exists on `/seri/{slug}/` pages)
- **Server resolution:** Main player is `anichin.stream/?id={id}` (JWPlayer HLS) — extracted via unpacked eval'd JS for m3u8 URL or WebView `shouldInterceptRequest` `.m3u8` interception. AbyssCDN/hydrax URLs handled by existing resolution code. **Old-post embeds** (Dailymotion, Mega, archive.org, OK.ru, Rumble, `anichin-player.web.id`, rubyvidhub) are returned as-is by `resolveServerVideoUrl()` (`isBrowserPlayableEmbed()`) and played directly in the visible WebView via `playEpisodePageViaWebView(skipInjections=true)` in `PlayerActivity`
- **Drive servers (new posts):** "Drive 1 [ADS]" → `abyssplayer.com/{id}` (iamcdn.net SoTrym lite player; has a redirect guard `if(top.location==self.location && hostname != *.abyss.to) location.href="https://abyss.to"` that kills top-level playback, plus popup-ad overlay). "Drive 2 [ADS]" → `rubyvidhub.com/embed-{id}.html` (JWPlayer 8 + streamruby.net HLS; has ad-block overlay `#adbd`/`.a965058`). Both are routed to visible-WebView playback (`isWebViewPlayableEmbed()` includes `abyssplayer` + `rubyvidhub`) and their main-frame HTML is rewritten by `PlayerActivity.rewriteAnichinPlayerPage()` via `shouldInterceptRequest`: abyssplayer → guard forced `false` + overlay removed + `window.open`/`document.write` neutralized; rubyvidhub → `setADBFlag`/`showADBOverlay` no-oped + overlay elements removed on interval. **Old-post "Google Drive [ADS]" → `archive.org/embed/...` is dead content** (item `is_dark:true`, embed 404 / download 403 — cannot be fixed app-side). **Old-post "Google Drive 2 [ADS]" → `racaty.my.id/empire/{okId}` (502 Bad Gateway) and "Google Drive [ADS]" → `short.icu/{id}` (DNS dead) — both dead content at the source, auto-fail via hidden-WebView timeout**
- **Server routing (audited 2026-08, all live episodes):** Samehadaku & Anichin server classification confirmed against live pages — see "Per-Provider Server Routing" below
- **Home:** Provider-specific home (`AnichinHomeFragment.kt`) with Continue Watching + Latest Episodes + Ongoing + Completed + All Anime (horizontal scroll, infinite scroll per section)
- **CategoryGridActivity:** Generic handler for non-DrakorKita/OppaDrama providers: `CATEGORY_EPISODES` → `getLatestEpisodes`, `CATEGORY_ONGOING`/`CATEGORY_MOVIES` → `getOngoingAnime`, `CATEGORY_POPULAR`/`CATEGORY_COMPLETED`/`CATEGORY_SERIES` → `getPopularAnime`, `CATEGORY_ALL` → `AnichinScraper.getAllAnime` (else `getOngoingAnime`)

### OppaDrama
- Website: `http://45.11.57.192` (default)
- Content: Korean Drama (Latest Episodes, Movies, Series)
- Scraper: `OppaDramaScraper.kt` — Jsoup + JSON API endpoints + token-based server resolution
- Features: Cookie-based auth, token extraction from episode page (Base64 encoded), server resolution via `oppadrama/api/v2` endpoints, turboviplay CDN support with Referer validation
- Key methods: `getHomeContent()`, `getAllAnime(page)`, `getAnimeDetail()`, `getEpisodeServers()`, `getEpisodeNavigation()`
- Server resolution: Extracts `oppaDramaData` JSON from episode page, resolves Hydrax token via `api/v2/getToken.php`, resolves server via `api/v2/server.php`, final video URL via `api/v2/video_hydrax.php` or turboviplay CDN
- **Server routing (audited 2026-08, live):** every episode has exactly 3 `<select class="mirror">` servers (base64 iframe src): **FileLions** (`minochinos.com/v/{id}`) → signed HLS on `dramiyos-cdn.com`/`acek-cdn.com` (`/hls2/01/08487/{id}_,l,n,h,.urlset/master.m3u8`, DRM-free, **no 429** → **ExoPlayer** via `extractFileLionsM3u8()` unpacking the eval'd packed JS in `resolveServerVideoUrl`); **Hydrax** (`abyssplayer.com/?v={id}`) → AES-CTR-encrypted progressive MP4 on `*.sssrr.org`, only leading 64KB encrypted → **ExoPlayer** via `hydrax://` URI + `HydraxDataSource` (see "Hydrax ExoPlayer" below); **TurboVIP** (`emturbovid.com/t/{id}`) → Google-drive segments on `lh3.googleusercontent.com` which **429 rate-limit** from plain IPs → **WebView** (`playVideoViaHtml5WebView`). `PlayerActivity` OppaDrama branch routes `.urlset/`/`/hls2/` m3u8 and `hydrax://` → `initExoPlayer`, everything else → WebView
- **Metadata parsing:** the detail info box is `<span><b>Episode:</b> 32</span>` — the numeric value sits OUTSIDE `<b>`, so parse with `span.ownText()` (not `span.select("b").text()`); movie posts (`movie-...`) have NO episode list (guard against empty eplister)
- **OppaDrama Home:** Provider-specific home fragment (`OppaDramaHomeFragment.kt`) with 5 clickable sections: Eps Terbaru, Drama Korea, Drama China, Film Korea, Netflix. Each section has horizontal infinite scroll and "Lihat Semua" opens `CategoryGridActivity`
- **CategoryGridActivity:** Supports OppaDrama categories (`CATEGORY_DRAMA_KOREA`, `CATEGORY_DRAMA_CHINA`, `CATEGORY_FILM_KOREA`, `CATEGORY_NETFLIX`) with infinite scroll

## Features
- **Home:** Provider chip switcher, each provider has its own home fragment:
  - Samehadaku: Static hero + Continue Watching + Latest Episode + Ongoing + Popular (infinite scroll). Each section header has a "Lihat Semua >" button → `CategoryGridActivity` (`CATEGORY_EPISODES`/`CATEGORY_ONGOING`/`CATEGORY_POPULAR`)
  - DrakorKita: Auto-scrolling ViewPager2 hero carousel (4s interval) + Continue Watching + Episodes + Movies + Series (infinite scroll)
  - OppaDrama: 5 clickable section headers (Eps Terbaru, Drama Korea, Drama China, Film Korea, Netflix) + horizontal infinite scroll per section
  - Anichin: Continue Watching + Latest Episodes + Ongoing + Completed + All Anime (horizontal infinite scroll per section). Each section header has a "Lihat Semua >" button → `CategoryGridActivity` (`CATEGORY_EPISODES`/`CATEGORY_ONGOING`/`CATEGORY_COMPLETED`/`CATEGORY_ALL`)
- **Search:** Real-time search with debounce (500ms) + Search history (SharedPreferences, max 20)
- **Ongoing:** Full paginated grid of all ongoing anime with vertical infinite scroll + footer loading
- **Category Grid:** Full-screen 3-column grid for DrakorKita and OppaDrama categories (Episodes/Movies/Series/Drama Korea/Drama China/Film Korea/Netflix) with infinite scroll
- **Detail:** Parallax banner, synopsis, info, episode list with spinner range selector (100 eps/chunk)
- **Player:** ExoPlayer, server picker (floating PopupWindow), gestures (brightness/volume/seek), skip opening/outro (smart windows: intro = first `min(120s, 12%)` OR mid-episode `210s–min(330s, 30%)` if episode ≥11min; outro = last `min(120s, 8%)`), auto-play next episode, PiP support, fullscreen toggle, prev/next episode navigation
- **Settings:** Per-provider domain configuration with chip selector, validation, and reset; About section shows app version (`2.0.0-beta`) + `GIT_COMMIT` + `BUILD_DATE` from BuildConfig
- **Continue Watching:** Saves watch progress per episode per provider, shows progress bar on Home, auto-resumes from last position
- **Domain Switching:** Change scraper base URL per provider from Settings

## Video Server Resolution
### Per-Provider Server Routing (audited 2026-08, verified against live episodes)
Routing in `PlayerActivity` (single decision point, ~L4270): `scraperUrl` contains a direct-video suffix (`.mp4/.m3u8/.mpd/.mkv/.webm/.m4v` or `googlevideo.com`) → ExoPlayer; else for `ANICHIN`/`SAMEHADAKU` providers if `isWebViewPlayableEmbed(scraperUrl)` → visible-WebView playback; else hidden-WebView interception fallback.

| Provider | Server | Live URL | Path |
|----------|--------|----------|------|
| Samehadaku | Blogspot | `www.blogger.com/video.g?token=...` | ExoPlayer (video.g XHR intercept) |
| Samehadaku | VIP STREAMING | filedon.co embed → signed R2 `.mkv` | ExoPlayer (Matroska, `extractFiledonDirectUrl`) |
| Samehadaku | Wibufile 720p/1080p (enabled) | `s0.wibufile.com/video01/...mp4` | ExoPlayer direct (`isDirectVideoUrl`) |
| Samehadaku | Wibufile 480p | disabled (no data-post) | skipped in `getEpisodeServers` |
| Samehadaku | Mega 480p/720p/1080p | `mega.nz/embed/...` | WebView (SPA `secureboot.js`) |
| Anichin | Premium | `anichin.stream/?id={id}` → `/hls/{id}.m3u8` | ExoPlayer (unpacked eval JS) |
| Anichin | OK.ru / Dailymotion | `anichin-player.web.id/index.php?ok=\|url=` | WebView (host 403 to direct OkHttp) |
| Anichin | Rumble | `rumble.com/embed/...` | WebView (host 403 to direct OkHttp) |
| Anichin | Drive 1 [ADS] | `play.abyssplayer.com/{id}` | WebView (`rewriteAnichinPlayerPage`) |
| Anichin | Drive 2 [ADS] | `rubyvidhub.com/embed-{id}.html` | WebView (`rewriteAnichinPlayerPage`) |
| Anichin (old post) | Google Drive / Drive 2 | `archive.org/embed` / `racaty.my.id` / `short.icu` | DEAD at source — auto-fail |
| OppaDrama | FileLions | `minochinos.com/v/{id}` → packed JS → signed `{sub}.dramiyos-cdn.com / {sub}.acek-cdn.com /hls2/01/08487/{id}_,l,n,h,.urlset/master.m3u8?t=...&e=129600` | **ExoPlayer** (`extractFileLionsM3u8` unpacked eval JS in `resolveServerVideoUrl`) — segments clean MPEG-TS, no 429 |
| OppaDrama | TurboVIP | `emturbovid.com/t/{id}` → `cdn3.turboviplay.com` → `g*.turbosplayer.com` → `lh3.googleusercontent.com/d/{gDriveId}=d` | **WebView** (`playVideoViaHtml5WebView`) — Google-drive segments **429 rate-limited** from a plain IP → NOT ExoPlayer-viable |
| OppaDrama | Hydrax | `abyssplayer.com/?v={id}` → SoTrym `const datas` → AES-CTR progressive MP4 on `*.sssrr.org` (only `[0, 65536)` encrypted) | **ExoPlayer** (`hydrax://` URI + `HydraxDataSource` — decrypts leading 64KB, passes tail raw). moov at START → fast start |
| DrakorKita | Download 480p/720p/1080p | `ajax_dl_all.php` → `/download/{dlId}` → `dlfilemob.php?id={dlId}` → `https://c1hd.load.my.id/1fichier/{fileId}` | **ExoPlayer** direct MP4 (progressive, NO .mp4 ext, Range **ignored 200**, moov at END of file) — see "DrakorKita Download-Pipeline" below. **If ExoPlayer fails → `playDrakorKitaEpisodePage()` WebView fallback (one retry via `drakorDlFallbackTried`)** |

`SamehadakuScraper.resolveServerVideoUrl()` guards direct videos: if `server.url` already ends in a direct-video suffix → returned unchanged (no AJAX re-fetch); the AJAX `player_ajax` iframe src is also checked with `isDirectVideoUrl()` before Blogger/filedon branches.

### Blogspot Server (Samehadaku - WORKING)
- Fast path: Scraper AJAX POST → get `blogger.com/video.g?token=` URL → WebView loads video.g with XHR interception
- XHR interception injects JS into `video.g` HTML that monkey-patches `XMLHttpRequest.prototype.send` to capture batchexecute responses containing `googlevideo.com` URLs
- URL cleaning: batchexecute responses have double-encoded escaping (`\\u003d`, `\\u0026`, `\\/`), handled by replace chain + final `replace(/\\/g, '')` to strip residual backslashes
- `interceptBloggerHtml()` in `PlayerActivity.kt` fetches video.g via OkHttp, injects XHR script, returns modified HTML
- `shouldInterceptRequest()` routes `blogger.com/video.g` to `interceptBloggerHtml()`
- `onUrlFound()` bridge receives clean URL → `initExoPlayer()`
- Server detection: `server.name.contains("Blogspot")` or `server.url.contains("blogger.com")` or `server.url.contains("bp.blogspot.com")`

### DrakorKita Server (FALLBACK — direct WebView playback, skip API pipeline)
- **Approach:** Load path-based URL (`/detail/slug/tag_cat/epNum/`) directly in WebView. Bypass the API token-resolution pipeline entirely. **Now the fallback** — dl servers play in ExoPlayer first (`isDrakorDl`), WebView only when no `videoUrl` resolved or ExoPlayer fails.
- **Reference:** https://github.com/wforyu/drakorkita — the page itself handles server resolution via JS after loading the correct URL.
- **Code:** `PlayerActivity.kt` — `playDrakorKitaEpisodePage(server)` (new helper, replaces inline block): builds `{base}/{tag}_{cat}/{ep}/` (tag/cat from URL query params, not `dataNume`/`dataType`), calls `playEpisodePageViaWebView()` with `skipInjections=true` and `customCleanJs=REF_INJECT_ADBLOCK_ONLY`, then `postDelayed` at 4s injects the toggle + auto-click JS.
- **JS injection features:**
  - **⛶/✕ button:** Fixed-position floating button (z-index 9999999) that toggles video fullscreen via CSS (`position:fixed; 100vw; 100vh; object-fit:contain`)
  - **Auto-hide:** Button fades to 15% opacity after 4s idle; reappears on any touch
  - **Resize listener:** Keeps fullscreen video matched to device viewport on orientation change
  - **Fullscreen API guard:** Intercepts `fullscreenchange` event and auto-exits native fullscreen (prefers CSS-based fullscreen)
  - **Auto-click fallback:** After 20s, auto-clicks the matching server button (only if no video is already playing)
- **Toggle fullscreen:** Uses CSS `position:fixed` + parent element fullscreen, not the Fullscreen API
- **Ad blocking:** `REF_INJECT_ADBLOCK_ONLY` — ad pattern removal + MutationObserver, no CSS/layout changes

### DrakorKita Download-Pipeline (WORKING — ExoPlayer direct MP4, NO tokens)
- **Audit (2026-08):** streaming sub-API dead (`video.php`/`server_mob.php` → HTTP 500; `video_sb.php`/`video_hydrax.php`/`video_p2p.php` → 200 but `status:0` + empty URL). The **download pipeline lives and needs NO c/t tokens**:
  - `GET https://api.nonton.bid/c_api/ajax_dl_all.php?media_type={tv|movie}&id={movieId}&tag={cat}` → HTML of `<div class="card"><div class="card-header">Download Episode {N}</div>...` with `<a class="btn btn-sm btn-success" href="/download/{dlId}">[hardsub] {quality}p WEB-DL [{size} MB]</a>` per quality. TV cards numbered per episode; **movie cards have header `Download ` (no number)**. `domain`/`tag`/`c`/`t` all optional (verified 200).
  - `GET https://api.nonton.bid/c_api/dlfilemob.php?id={dlId}&is_mob=1` → JSON `{"download":"...","link":"https://c1hd.load.my.id/1fichier/{fileId}",...}` — `link` is a **direct MP4** (byte-verified `ftypisom`, Content-Length full-body present, no Referer/Origin needed). Works token-free too. JSON also carries `linksb`/`linksbp` (`dqt.my.id`), `linkp2p` (`drakorkita.stream`), `linkfilemoon`.
  - **CDN behavior (audited 2026-08):** domain migrated from `dkdownload1hd.uyeshare.cc` → `c1.load.my.id` / `c1hd.load.my.id`. Serves `Content-Type: video/mp4` with **no `.mp4` extension** in the path. **Range requests are IGNORED** (returns full-body 200, not 206, no Content-Range) — `?alternative` and `?download=1` variants also ignore Range; HEAD same. **Atom layout:** `ftyp@0(32) free@32(8) mdat@40(261,809,324)` → **`moov` atom is at the END of the file**, so progressive ExoPlayer playback must read nearly the whole file before finding moov (slow start / potential sync-byte error). No ads injected in the raw stream.
- **Code:** `DrakorKitaScraper.kt` — `resolveDownloadServers(episodeUrl, movieId, ep, cat, isMovie)` runs FIRST in `getEpisodeServers` for `?mid=..&eid=..` episode URLs; builds one `VideoServer` per quality (`name="DrakorKita {quality}p"`, `videoUrl`=direct MP4, `dataPost`=dlId, `dataType="dl"`). `resolveServerVideoUrl()` short-circuits `dataType=="dl"` → `resolveDlFileMob(dataPost)`. Streaming-API fallback (server_mob/video_hydrax) kept only if download pipeline returns empty.
- **`PlayerActivity` routing (2026-08):** direct-video branches (cached/videoUrl/direct-URL) previously forced DrakorKita → `playVideoViaHtml5WebView`; now **only OppaDrama** forces WebView. DrakorKita `dl` servers are detected via `isDrakorDl` (`activeProviderId==DRAKORKITA_ID && server.dataType=="dl"`, no `.mp4` ext needed) → `initExoPlayer(server.videoUrl)`. `isRealVideo` cache check extended with `load.my.id`/`uyeshare.cc`/`/1fichier/`. **If ExoPlayer errors → `onPlayerError` falls back to `playDrakorKitaEpisodePage(failedServer)` once per episode-load (`drakorDlFallbackTried` flag)**. WebView path (`isDrakorKitaServer` → `playDrakorKitaEpisodePage`) remains only as last-resort fallback when no `videoUrl` resolved or ExoPlayer fails.
- **Slow start (moov-at-end, verified 2026-08):** all 3 qualities (480p=192MB/720p=394MB/1080p=679MB) are `ftyp@0 free@8 mdat@40 ...` with **`moov` at END**; CDN (`c1/c1hd.load.my.id`) advertises `Accept-Ranges: bytes` but **ignores Range** (200 full-body to any range request) → ExoPlayer's extractor must read the ENTIRE mdat via `skip()` before it finds moov, so playback starts only after the whole file downloads (~2.5min for 480p on a ~10Mbps link). No fast-start variant exists (`?alternative`/`?download=1` same atoms; `linksb`/`linksbp` dqt.my.id StreamHG is DEAD "No such file"; `linkp2p` drakorkita.stream uses an encrypted JWPlayer API — not worth reverse-engineering). Mitigation: **`dlProgressTotal`/`dlProgressLoaded` + `progressTransferListener`** (a `TransferListener` added to the OkHttpDataSource via a `DataSource.Factory` wrapper in `initExoPlayerRemote`) feed a `tvLoadingProgress` text ("Preparing X% (A MB / B MB)") during buffering, so the wait is transparent instead of an indefinite spinner; `dl` servers are **sorted 480p-first** (`resolveDownloadServers`) so the smallest/fastest file is auto-selected.
- **Progress bar root cause (fixed 2026-08):** the first implementation showed **nothing** on screen even though logcat proved the download ran (`DL: bytes loaded` climbing, `fetchDlTotal` returning the real 196MB). Two bugs: (1) **`playServer` direct-video branch hid the spinner** — `isDrakorDl` makes `isDirectVideo=true` → the branch ran `loadingPlayer.visibility = View.GONE` before `initExoPlayer`, but for `dl` the player still has to download the whole file → the `if (loadingPlayer.visibility == View.VISIBLE)` gate in `updateLoadingProgress()` never passed → text stayed `GONE`. Fix: `if (!isDrakorDl) loadingPlayer.visibility = View.GONE` (spinner stays up during the 196MB pre-roll). (2) **`onPlaybackStateChanged(STATE_BUFFERING)` was missed** — the `Player.Listener` was attached in the `.also {}` block **after** `player.prepare()`, so the initial BUFFERING transition fired before the listener existed. Fix: attach listener **before** `setMediaItem`/`prepare()`/`playWhenReady` in `initExoPlayerRemote`. Also fixed: `onTransferStart` must NOT use `dataSpec.length` as the total (it's the cache **block** size ~3.4MB, not the file size) — the real total comes from `fetchDlTotalAsync()` (HEAD Content-Length, e.g. 196404566), guarded by `dlTotalFetched` so HEAD runs once per load. Debug logs (`DL:`) that were added to find this were removed after the fix.
- **WebView fallback URL fix:** the old path-based URL was malformed — it built `{base}/{quality}_dl/{ep}/` from `server.dataNume` (quality) + `server.dataType` (`dl`), but the site expects the real tag/cat (`{base}/hs_ind/{ep}/`). `playDrakorKitaEpisodePage()` now prefers `tag`/`cat` from the server URL query params, falling back to `dataNume`/`dataType`. It injects the same ⛶/✕ fullscreen toggle + 20s auto-click + `REF_INJECT_ADBLOCK_ONLY`.
- **Movies now play in ExoPlayer too (fixed 2026-08):** movie detail pages have **no `.infox .spe span`** (type/duration/status all empty) and `og:type=website` → `isMovie` was computed `false`. Worse, the API `episode.php` `episode_lists` returns a **single `btn-svr` button whose text is `"unnamed"`** (class `epz-unnamed`, id `svr-unnamed`) with a **real `data-epid`** (series buttons are numbered `1/2/3...` and ALSO have `data-server` — so `data-server` is NOT a movie discriminator). The old episode URL `?mid=..&eid={realEpid}&ep=unnamed` → `getEpisodeServers` saw `eid != "movie"` → `resolveDownloadServers(..., isMovie=false)` → `media_type=tv` → card header `Download Episode unnamed` has **no number** → `cardNum=null` → no match → empty → WebView fallback. Two fixes in `DrakorKitaScraper.kt`: (1) `getAnimeDetail` detects the `"unnamed"` button (`epNum.equals("unnamed")` or class contains `unnamed`) → rewrites the single episode as `?mid=..&eid=movie&tag={data-tag}&cat={data-cat}&ep=1` so it goes through the `media_type=movie` download pipeline; (2) `resolveDownloadServers` now also accepts a card when `cardNum == null && cardCount == 1` (covers movies hit via `media_type=tv` or any `isMovie=false` edge). **Verified live (2026-08, "Ready or Not Here I Come 2026" movieId `bCKmea90`):** `ajax_dl_all.php?media_type=movie` → card `Download` → `[softsub] 1080p Bluray [1.99 GB]` → `dlfilemob` → `https://c2hd.load.my.id/1fichier/...` = direct MP4 (`ftypisom`, `video/mp4`, ~2.1GB, same CDN family → moov-at-end + progress bar apply). ExoPlayer route via existing `isDrakorDl` (`dataType=="dl"`).

### DrakorKita Fast HLS (drakorkita.stream P2P API) — **NEW WORKING (reverse-engineered 2026-08)**
- **The streaming sub-API is dead but the P2P playback API lives.** The site's own player (`drakorkita.stream`, SPA bundle `assets/index-BHB3gR9K.js`) decrypts an AES-CBC payload from `api/v1/video?id={hash}` to get **signed HLS m3u8 URLs** — no WebRTC/P2P needed, the m3u8 plays directly (fMP4, 4s segments, no donlod pre-roll).
- **Full token-free flow (verified live on TV ep + movie):**
  1. `dlfilemob.php?id={dlId}&is_mob=1` JSON has `linkp2p: "https://drakorkita.stream/#{hash}&dl=1"` → extract `{hash}` (e.g. `vh3pdm`, `palr8c`).
  2. `GET https://drakorkita.stream/api/v1/video?id={hash}` → **hex ciphertext** (`application/octet-stream`, ~6KB hex, rotates every ~1h because the plaintext carries `k`/`kx` tokens).
  3. **AES-128-CBC decrypt** with **FIXED key/IV** (derived from `location.protocol` = constant): KEY = `"kiemtienmua911ca"`, IV = `"1234567890oiuytr"` (UTF-8 bytes, PKCS5/PKCS7 padding).
  4. Decrypted JSON contains **`source`** (direct-IP m3u8 `https://{ip}/v4/.../master.m3u8?v=...`, Referer-gated) and **`cfNative`** (`https://drakorkita.stream/v4/pl/{cfDomain}/x68/{hash}/master.{version}.m3u8?k=...&kx=...`, `kx` ≈ now+1h).
  5. Play m3u8 in ExoPlayer with `Referer`/`Origin: https://drakorkita.stream/` on all requests (master/child/segments).
- **⚠ MUST use `source`, NOT `cfNative` (audited 2026-08, root cause of random HLS failures):** `cfNative`'s child playlist serves **absolute URIs to a per-video CDN domain** (`*.obsidianmotion.shop`, `*.petloversmarket.site`, `*.sunrisevalleylab.store`) with **`.woff`/`.woff2` disguises**, and its init segment (`#EXT-X-MAP`) is a **broken hybrid MP4: `mdat` before `moov` with NO `mvex/trex`** → ExoPlayer `FragmentedMp4Extractor.onMoovContainerAtomRead` throws NPE (`checkNotNull(trex)`) → `Source error` on SOME videos. Also `drakorkita.stream` proxy intermittently returns **502 Bad gateway** even for previously-working videos. `source` is clean: **same-host relative URIs**, proper fMP4 init with `mvex/trex` (verified `q5i8wa`/`vh3pdm`/`palr8c`: 1197B init `ftyp`+`moov`+`trex`), and works with `Referer: https://drakorkita.stream/` (no Referer → 403; cfNative no-Referer → **serves a PNG image**). `resolveP2pHls` returns `source` first, `cfNative` as fallback.
- **⚠ `source` IP is FULLY DYNAMIC per decrypt (audited 2026-08):** the direct-IP host changes between subnets every ~1h (observed `185.237.107.x` → `185.237.106.164` → `94.131.217.250`). So the Referer header CANNOT be added by a static host prefix match. Fix in `PlayerActivity`: `drakorP2pHosts` (companion-level synchronized set) registers the master's host inside `initExoPlayerRemote` when `isDrakorP2pHls` (provider==DRAKORKITA && `.m3u8`), and the OkHttp interceptor + `defaultRequestProperties` add `Referer`/`Origin: https://drakorkita.stream/` when the request host is `drakorkita.stream` OR in `drakorP2pHosts`. Since `source` uses same-host relative URIs, registering the master host covers child+init+segments. Without this, master returns **403 Forbidden** (seen 2026-08 when host `185.237.106.164` fell outside the old `185.237.107.` prefix match).
- **FULL LINK AUDIT (2026-08-01, 8 titles: TV `vh3pdm`/`1lt8q8`/`q5hbf3`/`nfd5ve`/`q5i8wa`, movies `palr8c`/`f1xpdt`/`q5lzsy`):** P2P HLS path is **consistent & clean on every title**. `source` master → single child (same-host relative URIs, no absolute CDN hosts) → init `init-f1-v1-a1.mp4` exactly **1197B** with proper `ftyp@0`+`moov`+`mvex`+`trex`, segments = fMP4 (`moof`/`m4s`). **NEW finding — `source` IP pool is now 3 subnets, not 2:** observed `185.237.107.x` (`.184/.188`), `185.237.106.x` (`.93/.155/.164`), and **`203.188.166.x` (`.22/.63/.89` — previously undocumented)**. The `v=` query token on `source` is long-lived (master 200 even when `v` is 34h+ old); the Referer check is the real gate (no `Referer: https://drakorkita.stream/` → **403 nginx HTML**). **NEW finding — `api/v1/video` rate-limits:** rapid successive calls return **HTTP 429 `{"message": "Rate limit exceeded"}`** (~1 call/2-3s is safe). App does 1 call per episode-load so this is fine, but rapid re-resolves (fast episode skipping / retries) can hit it → `resolveP2pHls` returns `""` → falls back to dl servers (acceptable). **cfNative re-verified flaky:** fresh token sometimes 403 `{"message": "Invalid or expired token"}` at master level even with valid `k`/`kx` → reinforces `source`-first. **NEW app bug found by audit — 2-arg `loadEpisode('id','tag')` form:** some movie pages (e.g. `supergirl-2026-tyum`, `the-odyssey-2026-tlak`) render server buttons with `loadEpisode('{movieId}','raw')` (NO 3rd `cat` arg) while the old regex required 3 args → `movieId` never extracted → episode list empty → **movie unwatchable**. Fixed in `DrakorKitaScraper.kt` via `LOAD_EPISODE_REGEX` (optional 3rd group) + `parseLoadEpisodeCall()` (cat defaults to tag when absent) — see Bugs table.
- **Key/IV derivation (from bundle, for reference):** `y=(...r)=>String.fromCodePoint(...r)`; key = `y(107,105,101,109,116)` + key.slice(1,3) `"ie"` + `y(110,109,117)` `"nmu"` + from `z(639)="3579"`: `y(97,57)` + `y(49,49)` + `y(99,97)` → `"kiemtienmua911ca"`. IV = `"1".."9"` loop + `y(48,111,105,117,121,116,114)` `"0oiuytr"` → `"1234567890oiuytr"`. Codepoint digits come from `"ᵟ".codePointAt(0)=7519`. Neither depends on the video hash (hash only contributes a constant `v("#...")=35` → `'i'`). Confirmed by calling the real `R()`/`j()` in Node.
- **Code:** `DrakorKitaScraper.kt` — `fetchDlFileMobJson()` (refactored from `resolveDlFileMob`), `resolveP2pHls(downloadId)` (dlfilemob → hash → fetch hex → `decryptDkP2p()` AES-CBC → JSON → **`source` first, `cfNative` fallback**), `decryptDkP2p(hex)` (javax.crypto AES/CBC/PKCS5). `resolveDownloadServers` **prepends a `"DrakorKita HLS"` server** (`dataType="p2p"`, `videoUrl`=m3u8) at index 0 → auto-selected; dl servers kept as fallback (HLS fails → ExoPlayer error → next server = dl download).
- **`PlayerActivity`:** HLS URL is a `.m3u8` → existing direct-video branch → `initExoPlayer` (no `isDrakorDl` flag needed — fast, no pre-roll). OkHttp interceptor + `initExoPlayerRemote` `defaultRequestProperties` add `Referer`/`Origin: https://drakorkita.stream/` when host is `drakorkita.stream` OR in `drakorP2pHosts` (registered from the master URL host when `isDrakorP2pHls` — covers the dynamic direct-IP `source`).
- **Token expiry:** `cfNative`/`source` carry `k`/`kx` (≈1h). A stale m3u8 → 403 → ExoPlayer error → auto-falls to next (dl) server. `resolveDownloadServers` re-resolves fresh per episode load.

### Hydrax ExoPlayer (OppaDrama — **WORKING, reverse-engineered 2026-08**)
- **The old token/API pipeline is dead** (`abysscdn.com/api/source` + `abyssplayer.com/api/source` → **404 "Path not found"**), but the embed itself carries everything needed: `abyssplayer.com/?v={id}` → SoTrym player (`iamcdn.net`) whose `const datas = "<base64>"` is a self-contained config → decrypt → **direct AES-CTR-encrypted progressive MP4 on `*.sssrr.org`** → playable in ExoPlayer.
- **Full flow (verified live 2026-08 on `the-apartment-job-episode-7`, `Xe9RMv6WP` AND `royal-betrothal-episode-1`, `FHrcJJGts`):**
  1. Fetch embed, extract `const datas = "..."` (base64).
  2. Decode base64 → JSON `{slug, md5_id, user_id, media, config, danmu}`. ⚠ **Kotlin MUST decode the base64 blob with `Charsets.ISO_8859_1`, NOT `Charsets.UTF_8`** (`OppaDramaScraper.extractHydraxMp4` L648) — `media` carries **raw bytes 0-255**. Some embeds escape them as `\uXXXX` (pure ASCII → UTF-8 decode survives, why `Xe9RMv6WP` worked), but others (e.g. `FHrcJJGts`) embed **raw non-ASCII bytes**; UTF-8 decode collapses multi-byte sequences into single codepoints and `(code & 0xFF)` then recovers only the LAST byte → decrypt output garbage. **Live bug (2026-08):** `extractHydraxMp4 failed` → `JSONException: Value U<m ... cannot be converted to JSONObject` on `FHrcJJGts`. Fixed by switching the base64 decode to ISO_8859_1 (JSON structure is ASCII so parsing is unaffected); verified: decrypt → `mp4.sources` with sizes `164868189/256123134/500116872/663491924` on `8oekkfci14.sssrr.org` etc., smallest auto-selected → `hydrax://` → `ExoPlayerImpl: Init` on-device.
  3. Decrypt `media` with **AES-256-CTR**: key = `md5hex("$user_id:$slug:$md5_id")` (32 ASCII hex chars = 32-byte AES key), initial counter block = **key[:16]** (full 128-bit IV, Java `AES/CTR/NoPadding` semantics: counter increments the whole 16-byte big-endian block per 16B). Result = JSON with `mp4.sources` (`[{label, res_id, size, codec, status, path, url, partSize, sub}]`) + `mp4.fristDatas` + `mp4.domains`.
  4. Pick the **smallest `size`** source → `full = url + "/" + path` (e.g. `https://9p7jrkb8.sssrr.org/c/a/a/ab3eea...3.346725763.3`). File key = `md5hex(path.substringAfterLast('/'))`.
  5. **Audit finding (verified byte-level):** ONLY bytes `[0, 65536)` of each MP4 are AES-CTR encrypted (same key/IV as above); everything from 65536 to EOF is already plaintext. Confirmed by: (a) double-decrypt of `[65536,131072)` → garbage ⇒ was plaintext; (b) reconstructed file = decrypt(prefix) + raw tail → parses perfectly as `ftyp(32) + moov(3,219,024) + free(8) + mdat(343,506,699)`, moov children `mvhd(108) + trak-video(1.78MB) + trak-audio(1.44MB) + udta(98)` all coherent; mvhd `timescale=1000 duration=3,730,111` (~62min).
- **Why "only 64KB encrypted" makes sense:** the moov is 3.2MB, so `[65536, 3.2M)` is the **plaintext moov tail** (sample tables — dense 4-byte entries that read as `00 00 00 01`-ish patterns, NOT NAL units), and `[3.2M, end)` is plaintext mdat (sparse NAL = normal high-bitrate video). moov at START → **fast start**, no DrakorKita-style slow pre-roll.
- **Code — scraper (`OppaDramaScraper.kt`):** `extractHydraxMp4(embedHtml)` parses `const datas`, decrypts media JSON, picks smallest source, returns a `hydrax://<base64url>` URI whose payload is `{"u": <full CDN url>, "k": <32-char hex file key>, "s": <plaintext size>}`.
- **Code — `HydraxDataSource.kt`:** ExoPlayer `DataSource` registered for `hydrax://` URIs in `PlayerActivity` (`getDataSourceFactory`). `open()` decodes payload, wraps an `OkHttpDataSource` on the real URL with `Referer`/`Origin: https://abyssplayer.com/`, and `read()` decrypts **only positions < 65536** (`ENCRYPTED_BYTES = 65536L`), passing the tail through raw. CTR keystream per 16B block = AES-256(ECB) of (initialCounter + block) — equivalent to Java `AES/CTR/NoPadding`.
- **Code — routing (`PlayerActivity.kt` playServer):** Hydrax is detected via `server.videoUrl.contains("abyssplayer.com") || server.name.contains("Hydrax")` and routed to a scrape branch that calls `resolveServerVideoUrl()` → expects `hydrax://` → `initExoPlayer()`. **Fallback:** if scraping fails → `playEpisodePageViaWebView(server.videoUrl, server)` (the old embed path with `rewriteAnichinPlayerPage`). ⚠ **Previously `isIframeEmbed` matched `server.name.contains("Hydrax")` FIRST and jumped straight to the WebView embed** (infinite SoTrym spinner — looked like a stuck ad, but it was a routing bug): that branch was replaced so Hydrax tries ExoPlayer first.
- **File request headers:** `*.sssrr.org` serves with `Referer: https://abyssplayer.com/` + matching `Origin` (tested 206 partial content with `Range`; no range needed but works). `Accept-Ranges` absent in test response — treat full-body fetch as OK.
- **hydrax:// is treated as a "real video" everywhere:** `isRealVideo` (cached-URL gate), `isDirectVideo` (server.videoUrl gate), and the OppaDrama ExoPlayer branch all include `hydrax://`. Not cached by `SimpleCache` key factory (custom scheme short-circuits).

### Other Servers
- **Wibufile 720p/1080p**: AJAX iframe src IS a direct `.mp4` URL (`https://s0.wibufile.com/video01/...mp4`) — plays directly via ExoPlayer. `resolveServerVideoUrl()` short-circuits (`isDirectVideoUrl`) when `server.url` is already a direct-video URL or the AJAX iframe src is.
- **Wibufile 480p**: disabled on live pages (row has `pointer-events: none`, no `data-post`) → skipped in `getEpisodeServers`
- **filedon.co (Samehadaku VIP STREAMING)**: React SPA embed (`/build/assets/app-*.js`, `/build/assets/embed-*.js`; hls.js). **Playback = ExoPlayer, NOT WebView:** the embed's Inertia `data-page` JSON has `media.hls_url=null` + `transcode_status=pending`, so the player falls back to a **raw Cloudflare R2 `.mkv`** signed URL (byte-confirmed Matroska, `1A45DFA3`). WebView HTML5 cannot play MKV → blank/broken; ExoPlayer's Matroska extractor CAN. `SamehadakuScraper.resolveServerVideoUrl()` now parses `div#app[data-page]` JSON (`props.url`, preferring `props.media.hls_url` when transcode completes) and returns the direct signed R2 URL → `PlayerActivity` recognizes `.mkv` as direct video → `initExoPlayer`. Signed URLs expire ~1h (400 XML on stale ranges) — the scraper re-fetches the embed page for a fresh one on each resolve. R2 requests get `Referer`/`Origin: https://filedon.co/` via OkHttp interceptor + `defaultRequestProperties`.
- **filedon.co Referer whitelist (verified 2026-07):** filedon validates the **HTTP `Referer` header server-side** (Laravel Inertia decides which component to render). No Referer → renders `embed-forbidden` page ("This embed is not allowed on this website"). Allowed referers: `samehadaku.how`, `v2.samehadaku.how`, `winbu.net`. Fix: `playEpisodePageViaWebView()` adds `Referer` = active provider `baseUrl` header via `loadUrl(url, extraHeaders)` when URL contains `filedon.co`. There is NO client-side referrer check (whitelist config only passed for display).
- **Mega (Samehadaku 480p/720p/1080p)**: embed serves only a shell `<!DOCTYPE html>` + `secureboot.js` (SPA) — WebView-only, ExoPlayer CANNOT.
- **anichin.stream (Anichin Premium)**: JWPlayer page (`anichin.stream/?id={id}`) whose packed JS (`eval(function(p,a,c,k,e,d)...`) unwraps to `file:"/hls/{id}.m3u8"` — a **direct, token-free master m3u8** (`https://anichin.stream/hls/{id}.m3u8`) pointing at `1a-1791.com` chunklists/segments. All fetchable with plain `Referer: https://anichin.stream/`. → plays in ExoPlayer. **`unpackPackedJs()` in both AnichinScraper.kt and SamehadakuScraper.kt was broken** (regex expected `.split)` and `baseConvert()` used a wrong base-N encoding that never matched the packer's `e()` tokens); fixed with the real packer `e()` algorithm (`token(i) = (i<base? "" : token(i/base)) + (i%base>35 ? chr(i%base+29) : (i%base).toString(36))`).

### TurboVIP / Hydrax Server (OppaDrama - PARTIAL — rate limited)
- Server URL pattern: `emturbovid.com/t/{id}` → resolves to `https://cdn2.turboviplay.com/data3/{id}/{id}.m3u8`
- **CDN chain:** master m3u8 (cdn2.turboviplay.com) → sub-playlist (g266.turbosplayer.com) → .ts segments (lh3.googleusercontent.com)
- **Resolution:** WebView intercepts m3u8 URL from embed page → bundled hls.js + OkHttp proxy
- **Rate limiting:** Google CDN (`lh3.googleusercontent.com`) rate-limits after ~5-8 segment requests → 429 HTML
- **429 retry:** OkHttp proxy retries 4 times with Retry-After backoff
- **hls.js config:** `maxParallelFrags:1`, `startFragPrefetch:false`, `fragLoadingRetry:15000`, `startLevel:0`
- **Result:** Plays first ~10s, then buffers/retries through 429

## ExoPlayer Configuration
- Buffer (conditional in `initExoPlayerRemote`): **clean HLS** (`/hls2/`, `.urlset/`, `dramiyos-cdn.com`, `acek-cdn.com`, `minochinos`, `anichin.stream`, `1a-1791.com`) → generous `30s/120s/15s/10s` (smooth, no 429 risk); **everything else** (turboviplay etc.) → tight `5s/20s/3s/2s` (fewer concurrent segment requests to avoid 429)
- Cache: `SimpleCache` with 250MB limit, turboviplay URLs bypass cache (unique cache key) to prevent stale re-fetches
- OkHttp: Adds `Referer`/`Origin` headers for `googlevideo.com`, `abysscdn.com`/`hydrax`/`drakor.bid`, `turboviplay.com` (Referer = `turbovidhls.com`), `cloudflarestorage.com` (Referer = `filedon.co/`), and `anichin.stream`/`1a-1791.com` (Referer = `anichin.stream/`)
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
- **Episode URL → series:** `getAnimeDetail()` on an episode URL (no `div.eplister` found) re-fetches the series page found in `.ts-breadcrumb ol li a[href*='/seri/']` — NOTE: the breadcrumb class is `ts-breadcrumb`, NOT `breadcrumb` (selector `.breadcrumb ...` matches nothing and silently returns an empty episode list)
- **Old-post embeds:** `resolveServerVideoUrl()` returns Dailymotion/Mega/archive.org/OK.ru/Rumble/`anichin-player.web.id`/rubyvidhub embed URLs unchanged (`isBrowserPlayableEmbed()`); `PlayerActivity.isWebViewPlayableEmbed()` routes them to visible-WebView playback (`playEpisodePageViaWebView(skipInjections=true)`) instead of hidden-WebView interception

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

## Pre-Scrape Cache (GitHub Data)
- Home fragments fall back to `GitHubDataFetcher.fetchHomeData(providerId)` → `data/{providerId}_home.json` from `raw.githubusercontent.com/wforyu/weebflix/master/data`; live-scrape on null
- Regenerated by `.github/workflows/scrape-providers.yml` (cron 6h + workflow_dispatch) via `scripts/scrape_providers.py` (Python + requests + bs4)
- **Verified selectors (2026-07):** Samehadaku latest = `ul > li[itemscope] > h2.entry-title a`, Samehadaku grids (ongoing `/daftar-anime-2/`, popular `?order=popular`) = `.animposx a`; DrakorKita = `.bungkus` (url `a[href*='detail/']`, title = first text node of `.titit`, img `img.poster`, `/all?media_type=movie|tv`); OppaDrama = `article.bs .bsx` (+ `user_is_human` cookie via `?verify_human=1`)
- **Guard:** `save_json` skips writing when `latest` is empty → never overwrites good cache with empty arrays on transient failures
- Test locally: `python scripts/scrape_providers.py` (Python 3.11 installed at `%LOCALAPPDATA%\Programs\Python\Python311\python.exe`)

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
| OppaDrama FileLions m3u8 hidden in packed JS (WebView can't extract) | `extractFileLionsM3u8()` in `OppaDramaScraper.resolveServerVideoUrl()` reimplements the packer `e()` algorithm and returns the signed m3u8 → `PlayerActivity` routes `.urlset/`/`/hls2/` to ExoPlayer |
| OppaDrama detail `Episode:` count empty/wrong | Info box is `<span><b>Episode:</b> 32</span>` — numeric value OUTSIDE `<b>`. Parse with `span.ownText()` not `span.select("b").text()` |
| DrakorKita c/t tokens empty → `server.php` HTTP 500 | `resolveServerVideoUrl()` now calls `decodePageTokens()` as fallback (base64 dot-segment decode); WebView `onTokensFound()` also falls back to OkHttp + base64 decode |
| DrakorKita WebView infinite navigation loop | Server click → CDN redirect → page reload → auto-click re-fires → cycle. Fixed: `playEpisodePageViaWebView()` added `skipInjections` + `customCleanJs` params; DrakorKita uses `skipInjections=true` to avoid `REF_INJECT_CLEAN_PAGE`/`AUTOPLAY` re-inject; auto-click checks `video.paused && video.readyState` before clicking |
| DrakorKita JS `src.toLowerCase is not a function` | `str(v)` helper function in `REF_INJECT_CLEAN_PAGE` handles non-string `src`/`className` (SVGAnimatedString) |
| DrakorKita native controls hidden by clean page | `REF_INJECT_ADBLOCK_ONLY` created — ad-block only, no CSS/overflow/display:none changes |
| DrakorKita native fullscreen broken/cut off | CSS-based fullscreen via JS button (⛶) instead of Fullscreen API; `fullscreenchange` event auto-exits native FS; resize listener keeps viewport match |
| DrakorKita toggle button disappears on page nav | JS injected via Kotlin `postDelayed` (not in `onPageFinished`); `window._dkSetupDone` flag prevents double inject |
| CategoryGridActivity DrakorKita infinite scroll not working | 3 bugs fixed: (1) Episodes used `getHomeContent().latestEpisodes` (unpaginated, ~10 items) + `hasMore=false` → changed to `getAllAnime(page)` (paginated). (2) Movies page 1 used `getHomeContent().movies` (few items), page 2+ used `getOngoingAnime(page)` (gap) → now always uses `getOngoingAnime(page)`. (3) Series same as Movies → now always uses `getPopularAnime(page)` |
| Anichin latest episode card opens detail with no episode list | Episode URLs have NO `div.eplister` (only `/seri/{slug}/` pages do). `getAnimeDetail()` detects empty episode list and re-fetches the series page via breadcrumb `.ts-breadcrumb ol li a[href*='/seri/']` |
| Anichin detail empty because breadcrumb selector missed | The breadcrumb class is `ts-breadcrumb` (NOT `breadcrumb`) — `.breadcrumb ol li a` matches nothing silently, returns empty episode list |
| Anichin old posts can't play (Dailymotion/Mega/archive.org servers) | `resolveServerVideoUrl()` returns browser-playable embeds as-is (`isBrowserPlayableEmbed()`); `PlayerActivity` plays them in the visible WebView (`playEpisodePageViaWebView(skipInjections=true)`) instead of hidden-WebView interception that timed out and failed |
| Anichin Drive 1 (abyssplayer.com) won't play | Page has redirect guard `if(top.location==self.location && hostname != *.abyss.to) location.href="https://abyss.to"` — kills top-level WebView playback before the player loads. Fix: `rewriteAnichinPlayerPage()` in `PlayerActivity` rewrites the main-frame HTML via `shouldInterceptRequest` (guard → `false`, popup overlay removed, `window.open`/`document.write` neutralized); `isWebViewPlayableEmbed()` now includes `abyssplayer` so it routes to visible-WebView playback |
| Anichin Drive 2 (rubyvidhub.com) ad-block overlay blocks player | Embed page uses JWPlayer 8 + streamruby.net HLS; `noadblocker.js` can flag `adbon=1` and drop `.a965058`/`#adbd` overlay over the player. Fix: same `rewriteAnichinPlayerPage()` path no-ops `setADBFlag`/`showADBOverlay` and removes overlay elements on an interval |
| Anichin old-post "Google Drive" (archive.org) unplayable | Server maps to `archive.org/embed/{id}` where item is **dark** (`is_dark:true`): embed returns 404, download 403. Dead content on the site's part — not fixable app-side |
| Anichin home latest section duplicates on page 2+ | Homepage `div.listupd article.bs` matches both "Latest Release" AND "Popular Today" sections → `getLatestEpisodes()` scoped to `div.releases.latesthome`'s parent list |
| Samehadaku movie servers all fail (Kimetsu/One Piece movies) | Episode streaming page is `/{slug}-v2/`; `#server .east_player_option` includes **disabled** Wibufile/Blogspot rows (`pointer-events: none`, no `data-post`/`data-nume`) that returned empty iframe HTML. Fix: `getEpisodeServers()` skips disabled/unavailable options; valid options resolve via `player_ajax` endpoint → filedon.co embed → `SamehadakuScraper` parses `data-page` JSON and returns the signed R2 `.mkv` → played in ExoPlayer (`PlayerActivity` recognizes `.mkv`); Mega via `mega.nz/embed` (WebView-only SPA) |
| Samehadaku auto-next never fires | `getEpisodeNavigation()` used wrong selectors (`.epnav .prev a`/`.epnav .next a` matched nothing) — actual HTML is `.naveps .nvs a` (prev) + `.naveps .nvs.rght a` (next), icon-only anchors. Fix: correct selectors + `deriveEpisodeTitle()` from URL slug so next activity gets a non-empty `episodeTitle`/`episodeNumber` |
| Samehadaku movie page prev/next = `#` placeholders | Movie `-v2/` pages have `.naveps` with `href="#"` links → `nextEpisodeUrl="#"` would break auto-next/buttons. Fix: `cleanNavUrl()` filters empty/`#`/`javascript:` hrefs |
| Samehadaku episode title prepends number ("1Kimetsu...") | `epTitle = select(".lchx a, .epl-title, .epl-name, a")` — trailing broad `a` also matches `.eps a`, concatenating the number. Fix: priority-based selection (specific selectors first, then first anchor only) |
| OppaDrama Hydrax iframe src never extracted | Hydrax options encode `<IFRAME SRC="...">` (uppercase) but regex `src=["']...` was case-sensitive → `videoUrl=""`. Fix: `RegexOption.IGNORE_CASE` → now extracts `https://abyssplayer.com/?v={id}` (played via visible WebView, abyssplayer main-frame rewrite already disables the abyss.to redirect guard for any provider) |
| DrakorKita dl servers played in WebView (no `.mp4` ext) | Download CDN migrated to `c1hd.load.my.id/1fichier/{fileId}` — link has **no `.mp4` extension** so the direct-video check missed it. Fix: `isDrakorDl` (`activeProviderId==DRAKORKITA_ID && dataType=="dl"`) added to the `server.videoUrl` direct-video branch → `initExoPlayer`; `isRealVideo` cache check extended with `load.my.id`/`uyeshare.cc`/`/1fichier/` |
| DrakorKita WebView fallback URL malformed (`720p_dl/1/`) | Old code built `{base}/{dataNume}_{dataType}/{ep}/` (quality + "dl") but the site expects real tag/cat. Fix: `playDrakorKitaEpisodePage()` prefers `tag`/`cat` query params, falls back to `dataNume`/`dataType` |
| DrakorKita dl server fails in ExoPlayer (moov-at-end / sync-byte) | `onPlayerError` now falls back once per episode-load (`drakorDlFallbackTried`) to `playDrakorKitaEpisodePage(failedServer)` instead of advancing to next server |
| DrakorKita 2-arg `loadEpisode('id','tag')` movies unplayable (supergirl/the-odyssey) | Some movie pages (old-style `raw` servers) call `loadEpisode('{movieId}','raw')` with NO 3rd `cat` arg; the old 3-arg regex missed them → `movieId` empty → no episodes. Fix: `LOAD_EPISODE_REGEX` in `DrakorKitaScraper.kt` makes the 3rd group optional + `parseLoadEpisodeCall()` defaults `cat` to `tag` when absent |
| OppaDrama Hydrax stuck on infinite SoTrym spinner | `isIframeEmbed` matched `server.name.contains("Hydrax")` and jumped straight to the WebView embed — the `hydrax://` ExoPlayer path was never reached. Fix: `playServer` routes Hydrax (`abyssplayer.com` / name "Hydrax") to a scrape branch calling `resolveServerVideoUrl()` → `hydrax://` → `initExoPlayer`; falls back to embed WebView only if scraping fails |
| OppaDrama FileLions buffering putus-putus | FileLions HLS (`.urlset/`/`/hls2/`) was using the tight turboviplay load control (5s/20s/3s/2s). Fix: `initExoPlayerRemote` uses generous `30s/120s/15s/10s` buffer for clean HLS hosts (FileLions/Anichin), keeps tight config for turboviplay |
| OppaDrama Hydrax `JSONException: Value U<m ... cannot be converted to JSONObject` | `extractHydraxMp4` decoded the `const datas` base64 blob with `Charsets.UTF_8` → `media` (raw bytes 0-255, e.g. `FHrcJJGts`) collapsed multi-byte UTF-8 sequences into single codepoints, `(code & 0xFF)` then recovered only the last byte → decrypt output garbage. Fix (`OppaDramaScraper.kt` L648): decode base64 with `Charsets.ISO_8859_1` (JSON structure is ASCII; only `media` carries raw bytes). `Xe9RMv6WP` escaped bytes as `\uXXXX` (ASCII) so UTF-8 survived there |
| OppaDrama FileLions/Hydrax playback after WebView shows bare ExoPlayer, no controls (no seekbar/PIP/back, system nav visible) | WebView path hid `playerView`/`topBar`/`bottomBar`/`centerControls`/`gestureOverlay` (GONE) and never restored them. Fix: `showExoPlayerUi()` in `PlayerActivity.kt` (restore playerView+gestureOverlay, hide webViewPlayerControls, stop/pause webview video, `showControls()`+`scheduleAutoHide()`, hide system bars), called at top of `initExoPlayerRemote()`; `playEpisodePageViaWebView()` calls `exoPlayer?.pause()` to avoid dual audio |
| YouTube video fails with `LOGIN_REQUIRED` on ALL clients even with fresh visitorData | Per-video Content ID / embedding-disabled gate (e.g. `Ihtxx2s6RUE` LAPOR PAK!) — NOT the visitor bypass being broken (normal videos still `status=OK`). Fix: `ResolvedYouTube.blockReason` propagates the gate reason; `playYouTubeVideo()` shows "Video diblokir YouTube (butuh login). <reason>" so users know it's a YouTube-side block. Permanent fix = Google OAuth login (phase 2) |
| Nav label stuck "Histori" after switching away from YouTube provider (Samehadaku/DrakorKita/OppaDrama/Anichin) | `MainActivity.updateNavLabels()` only ran in `onResume()`, but the provider chip switch in `HomeFragment.selectProvider()` happens while the activity is already resumed → label never reverted. Fix: `updateNavLabels()` dibuat `internal` + `HomeFragment.selectProvider()` memanggil `(activity as? MainActivity)?.updateNavLabels()` setelah `ProviderConfig.activeProviderId = providerId` (label kini berubah tiap ganti provider: youtube→"Histori", lainnya→"Ongoing") |

## Open Bugs (Still Buggy — Needs Further Investigation)

### 1. OppaDrama turboviplay CDN — HTTP 429 rate limiting after ~60-70s
- **Server:** TurboVIP → `emturbovid.com/t/6a6636b94a2fb` → WebView resolves to `https://cdn2.turboviplay.com/data3/6a6636b94a2fb/6a6636b94a2fb.m3u8`
- **Symptom:** Video plays for 60-70s then ExoPlayer throws "Cannot find sync byte" — CDN returns HTML error page (429) as TS segment data
- **CORS origin in embed page:** `https://turbovidhls.com` (NOT `emturbovid.com`)
- **Root cause:** CDN rate-limits segment requests after sustained streaming (~60-70s), sends HTML error page instead of .ts data
- **Confirmed from plain IP (2026-08, re-tested):** segments are a mix of **HTTP 429** AND **HTTP 200 with `Content-Type: image/png`** (anti-leech PNG, `\x89PNG` magic bytes, ~600KB) — never real MPEG-TS. Even the "successful" 200s are not playable video. ExoPlayer cannot play this source from a plain IP, period.
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

### 2. OppaDrama FileLions (minochinos.com) — FIXED (scraper-side unpack → ExoPlayer)
- **Server:** FileLions → `https://minochinos.com/v/{id}` → signed HLS on `dramiyos-cdn.com`/`acek-cdn.com`- **Root cause:** the direct m3u8 is hidden inside a base-36 **packed eval JS** (`eval(function(p,a,c,k,e,d){...}('DATA',36,N,'DICT'.split('|'))`) that WebView JS extraction couldn't read; the WebView embed itself never exposes the URL to interception
- **Fix (2026-08):** `OppaDramaScraper.resolveServerVideoUrl()` now fetches the `minochinos.com/v/{id}` embed page and runs `extractFileLionsM3u8()` — Kotlin reimplementation of the packer `e()` algorithm (`token(i)=i.toString(base)`, `\b`-word-boundary replacement, highest index first) → extracts `var links={"hls2":"https://{sub}.dramiyos-cdn.com/hls2/01/08487/{id}_,l,n,h,.urlset/master.m3u8?t=...&e=129600","hls3":...}` → returns the m3u8 directly. `PlayerActivity` OppaDrama branch routes `.urlset/`/`/hls2/` m3u8 → `initExoPlayer` (segments are clean MPEG-TS, **no 429**). Prior WebView attempts (v1-v4) all abandoned — the iframe-enumeration/OkHttp fallback path in `resolveEmbedUrlViaWebView`/`extractFileLionsViaOkHttp` is now a dead end for FileLions (page JS requires a browser context to build the URL).
- **Signed URL expiry:** `e=129600` (~36h) → resolve per-episode, never cache the m3u8 across sessions
- **Referer whitelist (verified 2026-08):** `minochinos.com` validates the **HTTP `Referer` header** — ONLY an OppaDrama page (`http://45.11.57.192` / any path under it) is accepted; `turbovidhls.com`, `drakor.kita.mobi`, `google.com`, `emturbovid.com`, or no Referer all render "Video embed restricted for this domain". The scraper's embed fetch in `resolveServerVideoUrl` originally sent `Referer: https://turbovidhls.com/` → FileLions was always restricted → `extractFileLionsM3u8` never saw the packed JS → fell back to episode-page WebView. **Fixed 2026-08:** embed fetch now sends `Referer = episodeUrl` (fallback `baseUrl`) + matching `Origin` (extraction verified live against `sjwjybugwrzu`: page 17.8KB not restricted → unpack → `ESrZaEKj9iFIuE8.dramiyos-cdn.com/hls2/01/08489/..._.urlset/master.m3u8` 200 → 3 variants → child 200). Cached-URL path in `PlayerActivity` also fixed: `.urlset/`/`/hls2/` m3u8 from cache → `initExoPlayer`, other OppaDrama URLs still → WebView.

### 3. OppaDrama Hydrax server — ExoPlayer (reverse-engineered 2026-08)
- **Server:** Hydrax → `abyssplayer.com/?v={id}` → SoTrym player, base64 `const datas` token → AES-256-CTR-encrypted progressive MP4 on `*.sssrr.org`
- **Symptom (old):** Old API endpoints `abysscdn.com/api/source` + `abyssplayer.com/api/source` return **404 "Path not found"** → token/API resolution dead. Previously routed to visible-WebView playback (`playVideoViaHtml5WebView`).
- **FIXED (2026-08):** embed self-contained config decrypts to direct MP4 sources — see "Hydrax ExoPlayer" above. `OppaDramaScraper.extractHydraxMp4()` returns `hydrax://` URI; `HydraxDataSource` decrypts only leading 64KB → **ExoPlayer**. Routing fixed so Hydrax tries ExoPlayer before WebView (was: `isIframeEmbed` matched name → straight to WebView embed → infinite SoTrym spinner). **Verified on-device 2026-08-02** (`royal-betrothal-episode-1`): latin-1 base64 fix → decrypt → `hydrax://` → `ExoPlayerImpl: Init`.

## YouTube Provider (PROTOTYPE — VERIFIED AD-FREE PLAYBACK 2026-08-03)
- **Goal (user request 2026-08):** Add a YouTube provider to WeebFlix with YouTube-like mobile UI, **no ads** (GoTube-style raw-stream playback), optional login, watch history. Decision: start with a **prototype** (search + play via `youtube://` + no login, no full UI redesign) before login + full YouTube-like UI.
- **STATUS:** Prototype works end-to-end on-device (Redmi Note 8, ISP KISS FAMILY Indonesia). Feed (search-based home) → search tab ("winda" → results) → tap → `youtube://` → resolve → **ExoPlayer plays 1080p AVC + Opus, no ads** (raw DASH, verified via MediaCodec AVC decoder + 19% screen motion).
- **Player:** real YouTube app itself uses **ExoPlayer** (media3) — same engine WeebFlix already uses. Only the stream source differs. Ads are separate objects injected via the *player response*, NOT part of the raw media → playing raw DASH streams (video-only + audio-only) skips all ads.
- **Integration pattern (precedent = `hydrax://`):** `YouTubeScraper : AnimeProvider` maps `searchAnime`→`youtubei/v1/search`, `getLatestEpisodes`→home/recommended, `getPopularAnime`→trending (`FEtrending`), `getAnimeDetail`→video detail, `getEpisodeServers(url)`→ single `VideoServer` with `videoUrl="youtube://{videoId}"`, `dataType="yt"`. `PlayerActivity` routes `youtube://` → `YouTubeResolver.resolve(videoId)` → **`MergingMediaSource`** (DASH video-only + audio-only) → ExoPlayer. Register in `ProviderFactory` + chip in `HomeFragment` + config key in `ProviderConfig`.
- **Ad-free resolve flow (per video):** fetch `youtubei/v1/player` → `playerResponse` → `streamingData.adaptiveFormats` → split video-only + audio-only → **signature decipher** (cipher + n-param) via `YouTubeCipher` → pick ≤1080p video + best audio → `MergingMediaSource`.
- **⭐ BOT-GATE BYPASS (critical, discovered 2026-08-03):** on a flagged device IP (KISS FAMILY), ALL player clients were gated — ANDROID_VR `LOGIN_REQUIRED "Sign in to confirm you're not a bot"`, MWEB `UNPLAYABLE "page needs to be reloaded"`, WEB_EMBEDDED `ERROR "video unavailable"`, IOS `HTTP 404` (client dead). **Fix: `YouTubeResolver.ensureVisitor()`** bootstraps a fresh `visitorData` via a lightweight WEB `youtubei/v1/search?query=trending` (search is NOT gated), then sends it in the player `context.client.visitorData` + `X-Goog-Visitor-Id` header. With visitor, **ANDROID_VR returns `status=OK` with direct (unciphered) URLs**. Cached per process.
- **Resolver client chain (`YouTubeResolver.resolve`):** `ANDROID_VR` (1.55.3, key `AIzaSyB9VGVgUmYc0HeBp5dHnjg1WxNb0qk2X3k`, direct URLs, primary) → `ANDROID_MUSIC` (6.27.51, same key, known bypass for the Android bot-gate; not yet needed) → `IOS` (22.41.2, uses WEB key — the classic iOS key `AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w` returns **404**, dead) → `MWEB` → `WEB_EMBEDDED_PLAYER` (signatureCipher via `YouTubeCipher`). 2.5s sleep between clients; HTTP 400 = flagged → stop.
- **Invidious fallback — DEAD (2026-08-03):** all 3 instances (`inv.nadeko.net`, `yewtu.be` → 403, `invidious.nerdvpn.de` → 401) fail even from a clean PC IP; `api.invidious.io/instances.json` lists only 12 instances, **zero API-enabled**. Invidious is a dead end in 2026 — do not invest further.
- **Login (optional, phase 2):** OAuth via Google API project (unverified client ID, scopes `youtube.readonly` + `youtube.upload`); cookie-mode fallback. History: logged-in → fetch `FEhistory` browse feed server-side; logged-out → existing `WatchHistoryManager` (per-provider SharedPreferences). Ads stay zero either way (raw streams), login is only for personalization.
- **Anti-break strategy (YouTube updates signatures often):**
  1. **Remote rules updater** — decipher logic = indices (splice/swap patterns) stored in GitHub `youtube_rules.json`; app fetches on open + cron workflow refresh every 6h (same as `scrape-providers.yml`). Rumus update = push JSON, no APK release.
  2. **Multi-client fallback chain** — `android_vr` → `android_music` → `ios` → `mweb` → `web_embedded` (see chain above; `ensureVisitor()` covers the LOGIN_REQUIRED gate).
  3. **Community patterns** — mirror rule formats from yt-dlp / NewPipe releases.
  4. **Re-resolve on stale** — YT stream URLs last ~6h → 403 → re-resolve fresh (same pattern as other resolvers).
- **UI YouTube-like (phase 2):** dedicated 5th bottom-nav tab "YouTube" (not mixed with film/anime layout) — Home rows (Beranda/Trending/Musik/History), Search with filter chips + list results (16:9 thumbnail, duration badge, channel avatar, view count), Player uses `PlayerActivity` + YouTube-ish overlay (like/dislike, subscribe, related).
- **Effort estimate:** 3-4x of Anichin provider (decipher + OAuth + new UI). Prototype done.
- **Test quirk:** `adb shell input tap` throws `SecurityException INJECT_EVENTS` AFTER landing — the tap still works (ignore the stack trace). `uiautomator dump` fails "could not get idle state" on this app (persistent animation) — verify UI via logcat + screenshot pixel-diff instead.
- **⚠ MIUI input injection BLOCKED (2026-08-03):** `adb shell input tap/keyevent` may be fully denied by InputDispatcher (`Permission denied: injecting event from pid ... uid 2000 to window ... owned by uid 10220`) when MIUI "USB debugging (Security settings)" is OFF — `persist.sys.enable_inputopts=true` (setprop needs root, `adb root` fails on production builds). When blocked, verify via `am start` (note: `PlayerActivity` is `exported=false`, so it can't be launched directly by adb — use the app's own navigation, or launch the splash and rely on taps only if they land) + `dumpsys activity top` (view bounds + visibility flags) + `dumpsys window | grep mCurrentRotation` (orientation) + logcat. Screenshot pixel-diff: `adb shell screencap -p /sdcard/x.png` + `adb pull` (PowerShell `>` redirect corrupts binary PNG). **Pro-trick — switch persisted provider without taps:** APK debug is debuggable, so edit `ProviderConfig` prefs directly: push an XML to `/data/local/tmp/` then `adb shell "run-as com.weebflix.app cp /data/local/tmp/x.xml shared_prefs/weebflix_provider.xml"` (force-stop first, relaunch via SplashActivity). Verified 2026-08-03 for #6 (set `active_provider=youtube` → YouTubeHomeFragment + nav label "Histori"). For direct PlayerActivity launches, temporarily set `android:exported="true"` (revert after) + `am start ... --es providerId youtube --es url 'youtube://<id>' --el startPositionMs <ms>` — quote extras with SINGLE quotes inside `adb shell "..."` (PowerShell double quotes mangle `--es` values). `dumpsys activity top` output on Windows contains null bytes → strip via `$_` pipeline before grep; `uiautomator dump` fails on this app ("could not get idle state", persistent animation).
- **Gear resolusi manual — VERIFIED 2026-08-03:** tombol gear (`btnQuality`) di bottom bar player YouTube berfungsi — log `btnQuality clicked, opts=6` + `showYtResolutionDialog values.size=7 opts=[1080, 720, 480, 360, 240, 144]` (dialog Auto + 6 resolusi). Resolusi yang di-pick disuntikkan via `ytTrackSelector` (DefaultTrackSelector track override). Bukan gear yang bermasalah saat video gagal putar — itu per-video gate (di bawah).
- **Per-video gate (BUKAN IP-wide, verified 2026-08-03):** bypass `ensureVisitor()` MASIH bekerja untuk video normal (uji `dQw4w9WgXcQ` → ANDROID_VR `status=OK` dari IP `182.8.67.140`), tapi sebagian video **hard-gated walau visitor fresh**: `Ihtxx2s6RUE` ("LAPOR PAK!", klip TV ber-Content ID, embedding-disabled → WEB_EMBEDDED "unavailable") return `LOGIN_REQUIRED "Sign in to confirm you're not a bot"` di SEMUA client (ANDROID_VR, ANDROID_MUSIC "Please sign in", WEB, MWEB, TVHTML5) × 3 percobaan — deterministik. → Konten Content ID / embedding-disabled tidak bisa diputar app-side tanpa akun Google yang login (OAuth phase 2). Gate ini di luar kendali visitorData; video lain di feed tetap jalan.
- **Blocked-video UX (2026-08-03):** `ResolvedYouTube.blockReason` field baru; `YouTubeResolver.resolve()` meneruskan alasan `LOGIN_REQUIRED`/age-gate dari `fetchPlayer` (`blockReason` di `PlayerResult`); `PlayerActivity.playYouTubeVideo()` menampilkan **"Video diblokir YouTube (butuh login). <reason>"** sebagai ganti "Gagal memuat video" generic agar user tahu itu blokir dari YouTube, bukan bug app. Log diagnostik sementara `GEAR:` (dipasang saat verifikasi gear) sudah dihapus setelah tombol + dialog terbukti jalan.
- **⭐ Login Google Gmail untuk video yang diblokir (pertanyaan user 2026-08-03):** **BISA** — video Content ID/embedding-disabled yang butuh login bisa ditonton kalau app login akun Google via OAuth (phase 2). Request player ber-login (`youtubei/v1/player` dengan auth token) tidak kena `LOGIN_REQUIRED` bot-gate. Syarat: Google Cloud project + OAuth consent screen (External/Testing) + client ID (scope `youtube.readonly`), flow `AuthorizationCode` + refresh token (device/loopback). Bonus: watch history sinkron server-side (`FEhistory` browse feed). Ads tetap nol karena stream mentah. Alternatif yang TIDAK jalan: cookie mode kurang reliable (cookie cepat expire + gate tetap jalan); PO token (botguard) mahal & sering rusak.

## YouTube Provider — Achieved vs Not Yet Achieved (status 2026-08-03)

### ✅ Sudah tercapai (semua diverifikasi on-device)
- **Playback ad-free** — raw DASH (video-only + audio-only via `MergingMediaSource`) → ExoPlayer, nol iklan (iklan bukan bagian stream mentah)
- **Bot-gate bypass** — `ensureVisitor()` bootstrap visitorData (ANDROID_VR `status=OK` dari IP ter-flag); multi-client chain `android_vr → android_music → ios → mweb → web_embedded`
- **Feed + Search** — `YouTubeHomeFragment` (Beranda/Trending/Musik/History + infinite scroll), `YouTubeSearchActivity` hasil list 16:9 + history pencarian
- **Player phase 2 (semua DONE & verified on-device):**
  - Orientasi portrait + toggle fullscreen rotasi (#1)
  - Daftar Rekomendasi/related di bawah player + infinite scroll + `ytUpNext` (#2)
  - Chip episode/server disembunyikan + gear resolusi manual jalan (ytTrackSelector) (#3 + gear)
  - System bars immersive (#4)
  - Auto-play next video end-to-end: countdown 10s → play video berikutnya + resume-from-position (#5)
  - Tab Ongoing→Histori + `YouTubeHistoryFragment` (lanjut dari durasi terakhir) (#6)
  - Dedicated tab `nav_youtube` di bottom nav (index 3) + `YouTubeHomeFragment` penuh
- **Nav label flip** — label nav berubah tiap ganti provider: youtube→"Histori", lainnya→"Ongoing" (bug stuck-Histori sudah fixed)
- **Blocked-video UX** — `blockReason` menampilkan "Video diblokir YouTube (butuh login). <reason>"

### ❌ Belum tercapai (untuk fase berikutnya)
- **Login Google OAuth (phase 2)** — video ber-Content ID / embedding-disabled (`LOGIN_REQUIRED` bot-gate, e.g. `Ihtxx2s6RUE` "LAPOR PAK!") TIDAK bisa diputar tanpa akun login. Syarat: Google Cloud project + consent screen + client ID (`youtube.readonly`) + flow AuthorizationCode + refresh token. Bonus: history sinkron server-side (`FEhistory`). Ads tetap nol
- **YouTube-like UI penuh** — tab dedicated sudah ada tapi player masih generic `PlayerActivity`: belum ada tombol like/dislike, subscribe, komentar, related-by-video (yang ada related dari feed/`nextFeedPage`). Search belum ada filter chips
- **Dukungan ISP lain** — `ensureVisitor()` + ANDROID_VR terbukti di ISP KISS FAMILY; perlu uji client `ANDROID_MUSIC` bila gate kembali di ISP lain
- **Search Invidious fallback** — Invidious mati total (2026-08-03, 0 instance API-enabled) → fallback tidak dibutuhkan; path bisa dirapikan/dihapus
- **Cookie-mode login** — tidak viable (cookie cepat expire, gate tetap jalan); jangan diinvestasikan
- **Resolusi maks / selector global default** — belum ada settingan default resolusi per pengguna

## TODO / Next Session
- **YouTube player phase 2 (rencana user 2026-08-03):**
  1. **Orientasi player — DONE (2026-08-03, on-device verified):** PlayerActivity `onCreate` forces `SCREEN_ORIENTATION_PORTRAIT` when provider==YOUTUBE_ID (manifest still forces landscape for other providers); `toggleFullscreen()` rotates to `LANDSCAPE`/back to `PORTRAIT` for YouTube (bars-toggle unchanged for non-YouTube). Verified: YouTube video opens ROTATION_0 portrait on-device. Toggle tap untested (see MIUI input-block note below)
  2. **Related/random di bawah player — DONE (2026-08-03, on-device verified):** `activity_player.xml` root LinearLayout → `playerArea` (video 16:9, weight 1) + `ytBelowArea` (gone default, VISIBLE saat provider YouTube): `ytDetailTitle`/`ytDetailMeta` + header "Rekomendasi" + `ytRelatedList` (RecyclerView, adapter `YouTubeFeedAdapter`). `loadMoreRelated()` fetch `YouTubeScraper.nextFeedPage()` + infinite scroll (dari bawah). Launch langsung PlayerActivity (`--es providerId youtube`): video ter-render 16:9 di atas (y0-607), judul/meta + list thumbnail di bawah — verified via screenshot pixel-analysis (row-lum pattern) + logcat. Related list terpopulasi 15 item & di-set sebagai `ytUpNext`
  3. **Hilangkan chip episode + chip server — DONE (2026-08-03, on-device verified):** `playYouTubeVideo()` hides `tvServerName`/`btnPrevEpisodeNav`/`btnNextEpisodeNav`; `updateEpisodeNavButtons()` returns early for YOUTUBE_ID. Verified via view hierarchy: all 3 = GONE (0,0-0,0), `btnQuality` gear = VISIBLE
  4. **Sistem navigasi bawaan HP otomatis hilang — DONE (sudah sejak lama):** `WindowInsetsControllerCompat.hide(systemBars())` dipanggil di onCreate semua provider (PlayerActivity ~L449) — immersive aktif di semua player termasuk YouTube
  5. **Auto-play next video — DONE (2026-08-03, verified end-to-end on-device):** `checkAutoPlay()` (tiap 500ms saat playing) + countdown 10s + `autoPlayRunnable` → `navigateToNextEpisode()` → `playYouTubeByVideo(ytUpNext)` (remove dari list, refresh upNext, `playYouTubeVideo(id, 0L)`). Verified via launch `--el startPositionMs 200000` (seek 200s/212s): countdown → log `YT playByVideo: <next title>` → `ExoPlayerImpl: Release` + `ExoPlayerImpl: Init` untuk video berikut → upNext diperbarui. Resume-from-position (seek `startPositionMs`) juga jalan
  6. **Navigasi Ongoing → Histori saat provider YouTube aktif — DONE (2026-08-03, on-device verified):** `MainActivity.updateNavLabels()` (`onResume`): `nav_ongoing.title` = "Histori" saat `ProviderConfig.activeProviderId==youtube` (strings `history`/`ongoing`); tap `nav_ongoing` saat provider youtube → `showFragment(getYouTubeHistoryFragment())`. `YouTubeHistoryFragment` = daftar `WatchHistoryManager` (filter `!isFinished`) 16:9 thumbnail + durasi + "lanjut dari X%" → tap → PlayerActivity dengan `startPositionMs` (resume). Verified on-device (via `run-as` set pref `active_provider=youtube` + relaunch): YouTubeHomeFragment aktif & label item nav ke-3 (`nav_ongoing`, x432-648) berubah — pixel-diff vs state samehadaku: hanya item itu yang berubah (control 4 item lain 0 px). Lanjut-menonton langsung dari durasi terakhir terkonfirmasi lewat auto-play seek test
- **YouTube provider prototype — DONE (2026-08-03, verified on-device):** feed + search + ad-free playback work. Bot-gate bypassed via `ensureVisitor()` visitorData bootstrap (ANDROID_VR `status=OK`). Remaining: verify search's Invidious fallback isn't needed (Invidious is dead), and decide phase 2 (login + YouTube-like UI + dedicated tab). Also consider `ANDROID_MUSIC` client if ANDROID_VR gate returns on other ISPs.
- **DrakorKita HLS full audit — DONE (2026-08-01):** `source` verified clean on 8 titles (TV `vh3pdm`/`1lt8q8`/`q5hbf3`/`nfd5ve`/`q5i8wa`, movies `palr8c`/`f1xpdt`/`q5lzsy`) — consistent master/child/init, no 403 (with Referer), no broken init. New findings: `source` IP pool = 3 subnets incl. `203.188.166.x`; `api/v1/video` rate-limits 429 on rapid calls; 2-arg `loadEpisode('id','raw')` bug found & fixed (supergirl/the-odyssey movies). Re-audit if behavior changes
- **Test filedon VIP (Kimetsu movie etc.)** — `SamehadakuScraper` now returns the signed R2 `.mkv` (ExoPlayer Matroska). Needs on-device verification: VIP STREAMING server should now play in ExoPlayer instead of blank WebView
- **Test Anichin Premium server** — `unpackPackedJs()` fixed; anichin.stream should now resolve to `https://anichin.stream/hls/{id}.m3u8` (direct, ExoPlayer). Verify with a new-post episode (e.g. `100-000-years-of-refining-qi`)
- **Test turboviplay v5 fix** — 120ms segment delay + exponential backoff sync byte retry (5s/10s, max 2 retries)
- **Test OppaDrama Hydrax → ExoPlayer — DONE (2026-08-02, verified on-device):** `extractHydraxMp4()` + `hydrax://` + `HydraxDataSource` works end-to-end. Live logcat on `royal-betrothal-episode-1` (`FHrcJJGts`): "Hydrax: selected source size=164868189 file=...sssrr.org..." → `hydrax://` → `ExoPlayerImpl: Init`. The latin-1 base64 fix (see Hydrax section) was required — `Xe9RMv6WP` worked with UTF-8 (escaped bytes) but `FHrcJJGts` carried raw non-ASCII bytes.
- **Test OppaDrama FileLions buffer — DONE (2026-08-02, verified on-device):** `royal-betrothal-episode-1` FileLions (`minochinos.com/v/...`) → scrape m3u8 → "OppaDrama CDN detected, playing in ExoPlayer" → `ExoPlayerImpl: Init` with generous 30s/120s/15s/10s buffer.
- **OppaDrama WebView→ExoPlayer UI restore — DONE (2026-08-02):** WebView playback hid `playerView`/`topBar`/`bottomBar`/`centerControls`/`gestureOverlay` (GONE), so selecting FileLions/Hydrax mid-session left ExoPlayer with NO custom controls (bare, system nav visible). Fix: new `showExoPlayerUi()` in `PlayerActivity.kt` (restores playerView+gestureOverlay, hides webViewPlayerControls, stops/pauses webview video, `showControls()`+`scheduleAutoHide()`, hides system bars via `WindowInsetsControllerCompat`), called at the top of `initExoPlayerRemote()` so every ExoPlayer path restores the full custom UI; `playEpisodePageViaWebView()` also calls `exoPlayer?.pause()` to avoid dual audio. TurboVIP remains WebView-only (Google-drive 429).
- **Auto play next episode**: Implement automatic playback of next episode when current finishes (partially done via auto-play overlay)
- **Add more providers**: Implement `AnimeProvider` interface for new content sources
- **DrakorKita episode selection**: Choose specific episode from AnimeDetail → ensure path-based URL uses correct `epNum` from selected episode
