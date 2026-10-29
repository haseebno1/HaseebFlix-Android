# Haseeb-Flix 2.0

Native Android app (Kotlin, Jetpack Compose, Material 3) for browsing movies and TV shows with TMDB.
Built from the "Cinematic Obsidian" Stitch designs (`docs/DESIGN.md`): dark-first, crimson `#E11D2E`, Montserrat + Inter.

> **Build status:** this source was written without an Android SDK, so it has **not been compiled or run**.
> All files parse with the Kotlin 2.0.21 compiler, but types, Compose APIs and icon names were not resolved.
> Expect to fix a handful of small compile errors on first sync (most likely a Material icon name or a Compose API signature).

## Run it
1. Open the folder in **Android Studio** (JDK 17, Android SDK 35).
2. Create a free TMDB account and copy your **API Read Access Token** (https://www.themoviedb.org/settings/api).
3. Copy `local.properties.example` to `local.properties`, set `sdk.dir` and `TMDB_READ_TOKEN`.
4. Generate the wrapper once: `gradle wrapper --gradle-version 8.9` (or let Android Studio do it), then **Sync** and **Run**.

Without a token the app shows a clear "TMDB access isn't set up" state instead of crashing.

## What's implemented (screen → file)
| Design | File | Notes |
|---|---|---|
| Splash | `ui/screens/Splash.kt` | animated logo glow, progress, version |
| Onboarding (3 steps) | `Onboarding.kt` | notification permission, genre picker saved to DataStore |
| Home | `Home.kt` | auto-advancing hero, genre chips filter all rows, continue watching, pull-to-refresh, skeleton, offline/error states |
| Discover + Search | `Discover.kt` | browse by genre (live title counts), trending chips, coming soon with reminders, voice search, recents, Movies/TV tabs with counts, sort + min-rating filter, paging |
| Genre / See all grid | `Grid.kt` | paged poster grid, Movies/TV toggle, sort |
| Library | `Library.kt` | Watchlist / History / Following, swipe-to-remove with undo, edit mode, jump back in |
| Movie + TV details | `Details.kt` | chips scroll to sections, season picker, episode guide with progress, cast sheet, similar titles, two-pane on tablets |
| Notifications | `Notifications.kt` | grouped Today / This week / Earlier, filters, mark read |
| Settings | `Settings.kt` | theme (system/light/dark/AMOLED), accent, dynamic colour, resolution, audio, Wi-Fi only, language, region, adult filter, data saver, alerts, cache clear, privacy, about |
| Player | `Player.kt` | Media3 player, resume + progress saving every 10 s, landscape/immersive, picture-in-picture |

Adaptive: bottom bar on phones, navigation rail from 600 dp, details split into two panes on wide screens.
Background: WorkManager checks followed shows every 12 h and posts new-episode / release notifications.

## Architecture
MVVM + unidirectional state (`StateFlow`), Hilt, Retrofit + kotlinx.serialization, Room (single source of truth), DataStore, Coil 3, Navigation Compose with type-safe routes.
```
data/remote   TMDB API + DTOs        data/local    Room entities, DAOs
data/repo     MediaRepository (cache-first, TTLs), LibraryRepository
data/settings DataStore settings     domain        models
ui/theme      colour, type, shape    ui/components shared Compose components
ui/nav        routes + adaptive shell ui/screens   one file per screen (+ ViewModel)
playback      PlaybackSource seam    work          sync worker + notifications
```

## Two things you must decide
**1. Video playback.** TMDB provides metadata, artwork and trailer keys, not video streams.
`playback/PlaybackSource.kt` is the single interface that supplies a stream URL; the default `NoPlaybackSource` returns nothing,
so **Play** shows a "Playback isn't set up" screen with a *Watch trailer* fallback. Implement the interface with a source you are
licensed to use and bind it in `di/AppModule.kt`. Resume, progress, Continue Watching and PiP all work as soon as it returns a URL.

**2. Downloads.** The *Download* buttons from the design are present but show "Downloads aren't available yet".

## Deliberate differences from the mockups
- **No "4K UHD / HDR10+ / Dolby Atmos" badges, "AI Taste Engine" or "4K HDR Ready" copy.** TMDB has no stream-quality data and those claims would be false. Certification (PG-13, TV-14) is shown instead.
- Movie info card shows Director / Studio / Languages / Release (real TMDB fields) instead of "Audio Mix" and "Subtitles".
- TV stats show Episodes / Seasons / Status instead of a "#1 Talk" ranking.
- Settings profile tag reads **CREATOR** (the mockup said PRO, which implies a paid tier on a free app). Change it in `Settings.kt`.
- "Download over Wi-Fi only" became **Stream over Wi-Fi only** (there are no downloads yet).
- "Autoplay trailers" was dropped: trailers open in YouTube, so there is nothing to autoplay.
- Coming-soon bell adds the movie to your Watchlist and notifies you on release day.
- The first launch uses your TMDB region and language settings; changing them clears cached content.

## Notes
- Release builds use R8; serialization keep rules are in `app/proguard-rules.pro`.
- The Room database uses destructive migration while the schema is at version 1. Add real migrations before shipping an update that changes tables.
- TMDB attribution is shown in Settings and on detail pages as required by TMDB's terms.
