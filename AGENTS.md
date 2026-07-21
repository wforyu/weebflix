# Agents.md

## Build & Run
- **Build:** Open project in Android Studio, Gradle sync, then Run
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 34 (Android 14)
- **Language:** Kotlin
- **Package:** `com.weebflix.app`

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
│       ├── player/PlayerActivity.kt    # WebView video player + server picker
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
- **WebView player** handles landscape mode and server switching

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
