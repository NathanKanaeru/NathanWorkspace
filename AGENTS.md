# Nathan Workspace — AGENTS.md

## Build & run

```powershell
./gradlew assembleDebug          # Build debug APK (only build target)
./gradlew assembleDebug --daemon  # With daemon (faster subsequent builds)
```

APK at `app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

- **Full Kotlin + Jetpack Compose + Material 3.** No XML layouts, no ViewBinding, no Fragments, no ViewPager2, no Navigation Component, no DI.
- **Two activities only:** `LoginActivity` (launcher + splash + PAT auth) and `MainActivity` (Compose host). Both are `ComponentActivity` with `setContent`.
- **`MainScreen`** (ui/MainScreen.kt): `Scaffold` + `NavigationBar` with 4 tabs (Workflow/Repo/Web/Profile), tab state via `rememberSaveable`, transitions via `AnimatedContent`. No navigation library.
- **Networking.** Raw `OkHttp 5.5.0` + `Gson` `JsonParser` (not Retrofit, not Moshi). Every API method is a `suspend` function returning `kotlin.Result<T>`, parsed with `isJsonObject` check before `asJsonObject`.
- **Gson quirk — never `?.asString` directly.** `JsonNull.asString()` throws `UnsupportedOperationException`. Always use the `safeString()` extension (checks `isJsonNull` first) defined in `GitHubApi.kt`.
- **State.** `WorkflowViewModel` + `RepoViewModel` (`AndroidViewModel`, activity-scoped via `viewModel()`), `Kotlin StateFlow` collected with `collectAsState()`. Persisted to `SharedPreferences("workflow")` as Gson JSON strings.
- **`WorkflowViewModel` uses manual `CoroutineScope(Dispatchers.Main)`**, not `viewModelScope`. Cancelled in `onCleared()`.
- **`RepoViewModel`** owns DownloadManager polling (1s loop), download progress/speed StateFlows, and `SharedPreferences("downloads_repo")` persistence of active download IDs.
- **Theming:** `ui/theme/` — `NathanWorkspaceTheme` (light/dark from DESIGN.md tokens), Inter fallback typography, M3 shape scale. XML themes (`values/themes.xml`) are framework-only for splash/window background.

## Key conventions

- **Indonesian** for user-facing error messages ("Token tidak valid", "Gagal terhubung").
- **Two SharedPreferences files:** `"app"` stores `github_token`, `github_login`, `github_name`; `"workflow"` stores `active_run` and `history` as Gson JSON; `"downloads_repo"` stores DownloadManager ID → asset ID.
- **Hardcoded API constants** in `GitHubApi.kt`: `OWNER = "BagasZkyn"`, `REPO = "studentcolab"`, `WORKFLOW = "student.yml"`. `getReleases` targets `NathanKanaeru/samptest` hardcoded. Changing target requires code change.
- **`UserInfo.avatarUrl` is fetched but never displayed** — UI always uses the generic `ic_github_mark` drawable.
- **Tab switching is state-based** (not swipeable). All 4 screens are composables; state survives tab switches via `rememberSaveable` and activity-scoped ViewModels.
- **WebView state preservation:** `WebViewHolder` (ui/webview/WebViewHolder.kt) holds the `WebView` instance + saved `Bundle`, hoisted at `MainScreen` level via `rememberSaveable` with a custom `Saver`. `captureState()` is called on `ON_STOP`, on dispose, and at save time. Tab switches reuse the same WebView instance.
- **Logout** clears both pref files, starts `LoginActivity` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`, calls `finishAffinity()`.
- **No ProGuard** (`proguard-rules.pro` referenced but missing).
- **Gradle version catalog** in `gradle/libs.versions.toml`. **Version lock (17 Aug 2026):** AGP **9.3.1** (built-in Kotlin — do NOT apply `org.jetbrains.kotlin.android`) + Kotlin **2.4.10** (via `org.jetbrains.kotlin.plugin.compose` for the Compose compiler) + Gradle **9.5.0** + Compose BOM **2026.08.00** (material3 1.4.0). compileSdk **37**, targetSdk **36**, minSdk 29, JDK 17. No `kotlinOptions`/`composeOptions` — compiler options live in `kotlin { compilerOptions {} }` (jvmTarget defaults to `compileOptions.targetCompatibility`).
- **`material-icons-extended`** is included — prefer Material icons; only 4 drawables remain (`ic_github_mark`, `ic_splash_logo`, launcher foreground/background).

## Design system

- **Dark scheme primary** (DESIGN.md): `primary=#a6c8ff`, `surface=#111318`, `background=#111318`. Light/dark via `isSystemInDarkTheme()` in `NathanWorkspaceTheme`. XML themes (DayNight) only set window background.
- **Inter font** via `res/font/inter.xml` (falls back to system sans-serif; drop Inter TTF files in `res/font/` to activate). Compose typography uses `FontFamily.SansSerif` (Inter fallback).
- **M3 tokens** defined explicitly in `ui/theme/Color.kt` from DESIGN.md (49-role palette, no dynamic color).
- **Rounded corners**: cards=12dp (`rounded.md`), buttons=9999dp (`FullPillShape`), inputs=4dp (`rounded.xs`), chips=8dp (`rounded.sm`).
- **Spacing**: page padding=24dp (`spacing.lg`), card inner padding=16dp (`spacing.md`), between cards=24dp (`spacing.lg`).
- **Tonal elevation**: no shadows on cards, hierarchy via surface container colors.
- **Input fields**: filled `TextField` with `surfaceContainerHighest` container color.

## Project structure

```
app/src/main/java/com/nathan/workspace/
├── LoginActivity.kt           # Launcher — splash + PAT auth (Compose host)
├── MainActivity.kt            # Compose host — setContent { MainScreen() }
├── api/GitHubApi.kt           # Singleton, 10 endpoints, UserInfo + WorkflowRunInfo + ReleaseInfo
├── viewmodel/
│   ├── WorkflowViewModel.kt   # StateFlows, polling, prefs persistence
│   └── RepoViewModel.kt       # Releases, DownloadManager polling, progress/speed StateFlows
└── ui/
    ├── MainScreen.kt          # Scaffold + NavigationBar + AnimatedContent (4 tabs)
    ├── theme/                 # Color.kt, Type.kt, Shape.kt, Theme.kt (M3 tokens DESIGN.md)
    ├── login/LoginScreen.kt   # PAT input + validate + save prefs
    ├── workflow/WorkflowScreen.kt  # Trigger, live logs dialog, history, cancel/delete
    ├── repo/RepoScreen.kt     # Releases monitoring, APK download & install
    ├── webview/
    │   ├── WebViewScreen.kt   # CRD WebView (AndroidView) + toolbar + shortcut bar
    │   └── WebViewHolder.kt   # WebView instance + saved Bundle (rememberSaveable Saver)
    └── profile/ProfileScreen.kt  # User info, stats, settings, sign out
```

## Important gotchas

- `studentcolab/` at project root is excluded via `.gitignore` — unrelated SA-MP scripts, not part of the Android app.
- `WorkflowScreen` reads user info directly from `SharedPreferences("app")`, not from the ViewModel.
- History entries don't show logs inline after completion — logs only via dialog (`LogsDialog`).
- `triggerWorkflow` escapes backslashes and double-quotes in CRD code to prevent JSON injection.
- Splash screen theme (`Theme.MyApp.Splash`) is applied to `LoginActivity` in the manifest. `installSplashScreen()` is called before `super.onCreate()`.
- `RepoScreen` monitors `NathanKanaeru/samptest` releases (hardcoded, different from the workflow OWNER/REPO).
- WebView URL whitelist: only `remotedesktop.google.com/access` + OAuth hosts (`accounts.google.com`, `accounts.youtube.com`, `consent.google.com`). Blocked navigation shows a Snackbar with "Buka di Browser" action.
- FileProvider configured in `file_paths.xml` under `<cache-path name="cache_downloads" path="downloads/" />` with authority `${applicationId}.fileprovider`.
- `REQUEST_INSTALL_PACKAGES` permission required for APK installation on Android 8+.
- `PullToRefreshBox` IS available since material3 1.3.0 (BOM 2026.08) — current refresh is still via header refresh button + `LinearProgressIndicator` (no migration done yet).
- **Version lock (17 Aug 2026):** AGP 9.3.1 (built-in Kotlin) + Gradle 9.5.0 + Kotlin 2.4.10 + BOM 2026.08.00. compileSdk 37 / targetSdk 36 / minSdk 29 / JDK 17. `android-37` platform + build-tools 37 installed in SDK. AGP 9 note: applying `org.jetbrains.kotlin.android` errors out — Kotlin is built-in; only `org.jetbrains.kotlin.plugin.compose` is applied.