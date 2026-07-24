# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

- **Build APK**: `./gradlew assembleDebug`
- **Install debug**: `./gradlew installDebug`
- **Lint**: `./gradlew lint`
- **Clean**: `./gradlew clean`
- **Full rebuild**: `./gradlew clean assembleDebug`
- **Project runs on**: Android SDK 34, minSdk 29, Gradle 8.5, AGP 8.2.2, Kotlin 1.9.22

## Project Architecture

### Overview
Single-activity Android app (`LoginActivity` → `MainActivity`) that authenticates with a GitHub Personal Access Token and triggers/displays GitHub Actions workflow runs. Uses ViewPager2 with BottomNavigationView for tab-based navigation. No navigation component — fragments are managed by `FragmentStateAdapter`.

### App Flow
1. **LoginActivity** — Splash screen → GitHub token input → validates via `GET /user` → saves token+user info to SharedPreferences → navigates to MainActivity
2. **MainActivity** — Hosts 3 fragments in ViewPager2 with BottomNavigationView:
   - **WorkflowFragment** (tab 0, default) — Enter a CRD auth code, trigger `student.yml` workflow on `BagasZkyn/studentcolab`, view live logs, cancel runs, see history
   - **WebViewFragment** (tab 1) — Loads `https://remotedesktop.google.com/access` for Chrome Remote Desktop
   - **ProfileFragment** (tab 2) — Shows user info, logout button (clears all prefs, returns to LoginActivity)

### Key Classes

| Class | Path | Role |
|---|---|---|
| `LoginActivity` | `app/.../LoginActivity.kt` | Token auth entry point, splash screen, SharedPreferences persistence |
| `MainActivity` | `app/.../MainActivity.kt` | ViewPager2 + BottomNavigationView host, page transformer animation |
| `GitHubApi` | `app/.../api/GitHubApi.kt` | Singleton OkHttp client, all GitHub API calls (validate, trigger, poll logs) |
| `WorkflowViewModel` | `app/.../viewmodel/WorkflowViewModel.kt` | AndroidViewModel, StateFlow-based, polls runs every 5s, persists to prefs |
| `WorkflowFragment` | `app/.../ui/WorkflowFragment.kt` | Workflow UI: input, logs, history list, cancel/delete actions |
| `WebViewFragment` | `app/.../ui/WebViewFragment.kt` | Chrome Remote Desktop WebView with state save/restore |
| `ProfileFragment` | `app/.../ui/ProfileFragment.kt` | User display, logout with FLAG_ACTIVITY_CLEAR_TASK |
| `ViewPagerAdapter` | `app/.../adapter/ViewPagerAdapter.kt` | FragmentStateAdapter for 3 tabs |

### GitHub API (OkHttp + Gson)

- All calls in `GitHubApi` object — raw OkHttp `Request.Builder`, manual JSON parsing with `JsonParser`
- Custom `safeString()` extension to handle `JsonNull` crashes from Gson
- Targets: repo `BagasZkyn/studentcolab`, workflow `student.yml`, branch `main`
- Auth: `Authorization: Bearer <token>` header
- Endpoints used: user, dispatch workflow, list/get runs, get logs, cancel run, delete logs

### State Management

- **Token/auth**: `SharedPreferences("app")` — github_token, github_login, github_name
- **Workflow state**: `SharedPreferences("workflow")` — active_run (serialized via Gson) + history (List<LogEntry>)
- **ViewModel**: `WorkflowViewModel` is `AndroidViewModel`, scoped to activity via `by activityViewModels()`
- **Polling**: Coroutine job polls `GET /run/{id}` every 5s while workflow is active, auto-stops on completion
- **Fragments**: Use `repeatOnLifecycle(STARTED)` with `lifecycleScope` for flow collection

### Theme & UI

- Material 3 (`Theme.Material3.DayNight.NoActionBar`) with custom colors (blue primary, teal accent)
- Custom bottom nav active indicator with full-rounded shape
- Edge-to-edge with transparent status/nav bars
- Dark theme support (`values-night/themes.xml`)
- Splash screen via `androidx.core:core-splashscreen`
- ViewBinding used throughout (no findViewById)

## Important Notes

- **No tests** — there are no test directories or test dependencies configured
- **UI language**: Indonesian (error messages, labels)
- **No CI/CD**: No GitHub Actions or CI config present
- **ProGuard**: Minimal rules file, minify disabled even for release
- **Offline-first**: No Room/DB — everything is in SharedPreferences
- **WebView**: JavaScript + DOM storage enabled, zoom controls (hidden +/- buttons), cache mode LOAD_DEFAULT
- **ViewPager2**: User input disabled (swipe), offscreenPageLimit=2, custom page transformer (alpha + scale + translation)