# Nathan Workspace — AGENTS.md

## Build & run

```powershell
./gradlew assembleDebug          # Build debug APK (only build target)
./gradlew assembleDebug --daemon  # With daemon (faster subsequent builds)
```

APK at `app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

- **Single-activity, multi-fragment.** `LoginActivity` → `MainActivity` (hosts ViewPager2 + BottomNavigationView). No Navigation Component.
- **No DI.** Manual singletons (`object GitHubApi`), `AndroidViewModel` with hardcoded `SharedPreferences`.
- **Networking.** Raw `OkHttp 4.12` + `Gson` `JsonParser` (not Retrofit, not Moshi, not data-class deserialization). Every API method is a `suspend` function returning `kotlin.Result<T>`, parsed with `isJsonObject` check before `asJsonObject`.
- **Gson quirk — never `?.asString` directly.** `JsonNull.asString()` throws `UnsupportedOperationException`. Always use the `safeString()` extension (checks `isJsonNull` first) defined in `GitHubApi.kt`.
- **State.** `Kotlin StateFlow` in `WorkflowViewModel` (activity-scoped via `by activityViewModels()`). Persisted to `SharedPreferences("workflow")` as Gson JSON strings.
- **ViewBinding.** Standard `_binding`/`binding` pattern, nulled in `onDestroyView`. All flow collection uses `viewLifecycleOwner.lifecycleScope` + `repeatOnLifecycle(STARTED)` + `_binding != null` guard.
- **`WorkflowViewModel` uses manual `CoroutineScope(Dispatchers.Main)`**, not `viewModelScope`. Cancelled in `onCleared()`.

## Key conventions

- **Indonesian** for user-facing error messages ("Token tidak valid", "Gagal terhubung").
- **Two SharedPreferences files:** `"app"` stores `github_token`, `github_login`, `github_name`; `"workflow"` stores `active_run` and `history` as Gson JSON.
- **Hardcoded API constants** in `GitHubApi.kt`: `OWNER = "BagasZkyn"`, `REPO = "studentcolab"`, `WORKFLOW = "student.yml"`. Changing target requires code change.
- **`UserInfo.avatarUrl` is fetched but never displayed** — UI always uses the generic `ic_github_mark` drawable.
- **ViewPager2 swipe disabled** (`isUserInputEnabled = false`), tab switching only via bottom nav. `offscreenPageLimit = 2` keeps all 3 fragments alive.
- **Logout** clears both pref files, starts `LoginActivity` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`, calls `finishAffinity()`.
- **No ProGuard** (`proguard-rules.pro` referenced but missing).

## Project structure

```
app/src/main/java/com/nathan/workspace/
├── LoginActivity.kt           # Launcher — splash + PAT auth
├── MainActivity.kt            # ViewPager2 + BottomNav host
├── adapter/ViewPagerAdapter.kt
├── api/GitHubApi.kt           # Singleton, 8 endpoints, UserInfo + WorkflowRunInfo
├── ui/
│   ├── WorkflowFragment.kt    # Trigger workflow, live logs, history
│   ├── WebViewFragment.kt     # CRD WebView (saveState/restoreState)
│   └── ProfileFragment.kt     # User info + sign out
└── viewmodel/WorkflowViewModel.kt  # StateFlows, polling, prefs persistence
```

## Important gotchas

- `studentcolab/` at project root is excluded via `.gitignore` — unrelated SA-MP scripts, not part of the Android app.
- `WorkflowFragment` reads user info directly from `SharedPreferences("app")`, not from the ViewModel.
- History entries don't show logs inline after completion — log card is hidden via `showIdleState()`.
- `triggerWorkflow` interpolates `$code` directly into JSON — special chars in CRD code will break the payload.
- Splash screen theme (`Theme.MyApp.Splash`) is applied to `LoginActivity` in the manifest. `installSplashScreen()` is called before `super.onCreate()`.
