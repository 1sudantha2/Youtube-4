# YT4 — Ultra-Lightweight InnerTube Client for Android

A production-shaped, client-only YouTube experience in **100 % Kotlin + Jetpack
Compose**, speaking YouTube's **InnerTube** RPC surface directly. Zero backend,
zero tracking layers, engineered against hard resource budgets for budget
hardware.

> ⚠️ This project talks to an unofficial API surface with session cookies from
> your own account. It is intended for personal/educational use. Respect
> YouTube's Terms of Service and copyright law in your jurisdiction.

---

## Performance budgets (design targets)

| Metric                        | Budget                    | Enforced by |
| ----------------------------- | ------------------------- | ----------- |
| Frame pacing                  | 60/120 fps, zero drops    | SurfaceView HWC path, async MediaCodec queueing, draw-phase-only shimmer |
| RAM during active playback    | < 120 MB                  | `LowRamLoadControl` (25 s / 20 MB cap), HARDWARE bitmaps, 20 % heap image cache |
| CPU when idle / paused        | ~0 % (no wake loops)      | single player in `MediaSessionService`, no polling below 0.5 Hz |
| Battery                       | minimized radio wake-ups  | one shared HTTP/2 OkHttp pool, 3-min connection keep-alive, DNS cache |
| APK size (release, R8 full)   | small single-digit MB     | full-mode R8, no AppCompat, no DI framework, `-Xlambdas=indy` |
| Startup                       | < ~100 ms to first frame  | lazy DI graph, deferred NPE init, seeded baseline profile |

---

## Architecture

```
app/src/main/java/com/yt4/app/
├── YT4App.kt                     # bootstrap: DI wiring + Coil singleton
├── MainActivity.kt               # single-activity shell, player bind/unbind
├── core/
│   ├── di/ServiceLocator.kt      # hand-rolled DI (no Hilt → smaller dex, faster start)
│   ├── network/
│   │   ├── HttpClientFactory.kt  # ONE OkHttpClient: HTTP/2 pool, gzip, 8 MB cache
│   │   ├── CachingDns.kt         # 60 s TTL DNS cache (CDN rotation friendly)
│   │   ├── ImageLoaderFactory.kt # Coil: HARDWARE bitmaps, 20 % heap cache cap
│   │   ├── NewPipeInitializer.kt # deferred NPE init + shared-OkHttp Downloader
│   │   └── CallExt.kt            # suspendCancellableCoroutine bridge for Call
│   ├── auth/
│   │   ├── SapisidHash.kt        # SAPISIDHASH header generator (SHA-1, per-request ts)
│   │   ├── CookieHarvester.kt    # parses the 6-cookie session set
│   │   └── AuthRepository.kt     # session StateFlow, header derivation, sign-out
│   ├── persistence/
│   │   ├── SecureStore.kt        # EncryptedSharedPreferences (AES-256-GCM values)
│   │   └── SettingsStore.kt      # DataStore preferences
│   └── innertube/
│       ├── InnerTubeConfig.kt    # ANDROID client identity + browseIds
│       ├── InnerTubeApi.kt       # browse/next/like/subscribe/comment transport
│       ├── InnerTubeParser.kt    # indexed-key mapping → immutable domain models
│       └── JsonExt.kt            # allocation-lean JsonObject navigation
├── data/
│   ├── model/Models.kt           # @Immutable domain types (VideoItem, StreamBundle…)
│   └── repo/                     # FeedRepository, PlayerRepository (NPE, IO dispatcher)
├── player/
│   ├── LowRamLoadControl.kt      # 10s/25s buffers · 1.5s/3s start · 20 MB cap
│   ├── PlayerFactory.kt          # async codec queueing + decoder fallback + priorities
│   ├── PlayerService.kt          # MediaSessionService hosting the single ExoPlayer
│   ├── PlayerConnection.kt       # MediaController bind/release (start/stop pattern)
│   ├── BackgroundAudioManager.kt # background → audio-only source swap (decoder release)
│   ├── AdaptiveFallback.kt       # 3 s stall watchdog → downgrade quality tier
│   ├── PlaybackRegistry.kt       # what's currently loaded (for bg transitions)
│   └── compose/PlayerSurface.kt  # SurfaceView host attached via MediaController
└── ui/
    ├── theme/Theme.kt
    ├── nav/                      # NavHost + bottom bar
    ├── home/                     # FEwhat_to_watch screen + paginated ViewModel
    ├── feed/                     # FEsubscriptions / FElibrary / VLLM renderer
    ├── auth/SignInScreen.kt      # WebView sign-in + cookie capture + teardown
    ├── player/                   # watch page, quality sheet, comments sheet
    └── components/               # VideoFeedList, VideoCard, Shimmer, WebViewHostState
```

---

## The four critical engineering decisions

### 1. ExoPlayer RAM/CPU discipline (`player/LowRamLoadControl.kt`, `PlayerFactory.kt`)

* `DefaultLoadControl` capped at **10 s min / 25 s max buffer**, playback start
  at **1.5 s** (3 s after rebuffer), `targetBufferBytes = 20 MB`,
  `prioritizeTimeOverBandwidth = true` (the `C.PRIORITY_PLAYBACK` policy),
  3 s back-buffer not retained from keyframes.
* **Asynchronous MediaCodec queueing** via
  `DefaultRenderersFactory.forceEnableMediaCodecAsynchronousQueueing()` —
  input buffers are queued off the render loop, cutting frame-drop rate on
  weak SoCs. `setEnableDecoderFallback(true)` covers flaky HW decoders.
* **SurfaceView, never TextureView** — separate hardware-composited layer,
  ~40 % less GPU memory, no per-frame texture copy.
* Leaving the foreground mid-playback swaps the merged source for the
  **audio-only stream at the same position** (`BackgroundAudioManager`);
  ExoPlayer releases the now-unused video decoder immediately. Screen-off
  audio runs under `WAKE_MODE_LOCAL` in the `MediaSessionService`.
* DASH video-only (144p–1080p) + Opus/AAC audio are joined with
  `MergingMediaSource`; the quality selector can pin any tier, and Auto mode
  downgrades one tier after a 3 s stall, falling back to muxed progressive
  streams as the last resort.

### 2. Zero-recomposition Compose (`ui/components/*`, `ui/home/*`)

* Every domain/UI model is `@Immutable`; classes holding flows are `@Stable`.
* `LazyColumn` uses **`key = { it.id }`** and **`contentType`** on every item.
* The shimmer animation reads its phase **inside the draw lambda**
  (`Modifier.shimmer` / `drawWithContent`) → per-frame invalidation is
  draw-only, composition count stays 0.
* Pagination triggers go through `remember(keys) { derivedStateOf { … } }` so
  scroll offsets never invalidate composition.
* The seekbar's 500 ms position poll lives in its own leaf composable
  (`PlaybackControls`) — fast-changing state is isolated, never read in the
  composition phase of the surrounding screen.
* Verify anytime: `./gradlew assembleRelease -PcomposeMetrics` writes
  stability/restartability reports to `app/build/compose-reports`.

### 3. WebView auth without residue (`ui/auth/SignInScreen.kt`, `core/auth/*`)

* A bare `WebView` loads Google's sign-in; after each page load the cookie jar
  is scanned for the full session set (`SID, HSID, SSID, APISID, SAPISID,
  LOGIN_INFO`).
* On capture: cookies are persisted to **EncryptedSharedPreferences**
  (Keystore-resident AES-256-GCM master key, backup-excluded), then the
  WebView is fully destroyed — `stopLoading → detach → clearHistory → destroy`
  (`WebViewHostState`).
* Every InnerTube request computes a fresh
  `Authorization: SAPISIDHASH <ts>_<SHA1("<ts> <SAPISID> https://www.youtube.com")>`
  (`SapisidHash`) plus the `Cookie:` replay header. No cookie ever drives a
  WebView after sign-in.

### 4. Network & parsing efficiency (`core/network/*`, `core/innertube/*`)

* **One** `OkHttpClient` for feed, images, *and* media segments: HTTP/2
  multiplexing, 8-connection / 3-minute pool, 8 MB JSON disk cache, GZIP via
  OkHttp's bridge, 60 s DNS cache.
* InnerTube JSON is parsed in a single kotlinx.serialization streaming pass
  into a `JsonObject` tree, then mapped with **indexed key lookups only**
  (no per-response reflective deserializers) into immutable models —
  minimal GC churn.
* NewPipeExtractor runs through a `Downloader` adapter that reuses the same
  OkHttp client, and is initialized lazily on first playback (Rhino/jsoup
  classes never load at startup).

---

## Build

Requirements: JDK 17, Android Studio Ladybug+ (or Gradle 8.9 + Android SDK 35).

```sh
# one-time, if gradle-wrapper.jar isn't present:
sh tools/bootstrap-wrapper.sh

./gradlew assembleRelease          # R8 full mode, shrunk + obfuscated APK
./gradlew assembleRelease -PcomposeMetrics   # + Compose stability reports
```

Version catalog: `gradle/libs.versions.toml` · R8 rules: `app/proguard-rules.pro`.

### Regenerating the baseline profile

The seed in `app/src/main/baseline-prof.txt` covers the known hot paths. For a
measured profile add a `:baselineprofile` module with
`androidx.benchmark:benchmark-macro-junit4`, script app startup + home scroll
with `BaselineProfileRule`, and replace the seed with the generated rules.
`androidx.profileinstaller` already applies it on sideloaded installs.

---

## Known constraints / honest notes

* InnerTube and the Google login flow are unofficial surfaces; fields drift.
  Parsers are defensive (skip, never crash) but occasional breakage is a
  property of the problem space.
* `EncryptedSharedPreferences` is a `1.1.0-alpha` artifact — it is the only
  sane option for per-key hardware-backed file encryption from Compose-era
  code; alternatives (Keystore-wrapped DataStore) are a drop-in swap behind
  `SecureStore`.
* NewPipeExtractor v0.24.2 is pinned; upgrading is a catalog one-liner.
* No DRM/Widevine paths: this client plays non-DRM formats only.
