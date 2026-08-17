# Migrasi Jetpack Compose + Material3 — Nathan Workspace

Status: `⬜ pending` / `🔄 in progress` / `✅ done`

> Tujuan: migrasi total dari View system (XML + ViewBinding + Fragment + ViewPager2)
> ke **Kotlin + Jetpack Compose + Material3 penuh**, tetap mempertahankan
> fungsionalitas: login PAT, trigger workflow, live logs, history, releases
> APK download/install, CRD WebView, profil.

---

## Task 1 — Setup Gradle: version catalog + Compose dependencies
- [x] Buat `gradle/libs.versions.toml` (AGP, Kotlin, Compose BOM, lifecycle, okhttp, gson, coroutines, splashscreen)
- [x] Update root `build.gradle.kts` pakai version catalog
- [x] Update `settings.gradle.kts` → `rootProject.name = "NathanWorkspace"`
- [x] Update `app/build.gradle.kts`:
  - [x] `buildFeatures.compose = true`
  - [x] `composeOptions.kotlinCompilerExtensionVersion = 1.5.8`
  - [x] Tambah deps: compose-bom, ui, ui-graphics, ui-tooling, ui-tooling-preview, material3, material-icons-extended, animation, activity-compose, lifecycle-viewmodel-compose, lifecycle-runtime-compose
- [x] Verifikasi: `./gradlew assembleDebug` hijau

## Task 2 — Design system Material3 penuh (ui/theme)
- [x] `Color.kt`: DarkColorScheme + LightColorScheme persis token DESIGN.md / colors.xml
- [x] `Type.kt`: tipografi Inter fallback sans-serif, skala M3 lengkap (display/headline/title/body/label)
- [x] `Shape.kt`: rounded.xs=4, sm=8, md=12, lg=16, xl=24, full=9999 (pill hanya untuk button)
- [x] `Theme.kt`: `NathanWorkspaceTheme` dengan dark/light + dynamic color off (warna brand tetap)

## Task 3 — MainActivity jadi Compose host (Scaffold + NavigationBar)
- [x] Ubah `MainActivity` → `ComponentActivity` + `setContent`
- [x] Buat `MainScreen`: Scaffold + `NavigationBar` 4 tab (Workflow/Repo/Web/Profile)
- [x] Tab switching via state + `AnimatedContent` (bukan ViewPager2)
- [x] `WorkflowViewModel` via `viewModel()` activity-scoped, polling dimulai di MainScreen
- [x] `WebViewHolder` (rememberSaveable + Saver Bundle) di-hoist di MainScreen
- [x] Hapus fragment lama + ViewPagerAdapter (deps legacy sudah dicabut di Task 1 sehingga tidak bisa kompilasi)

## Task 4 — LoginScreen (Compose)
- [x] Buat `ui/login/LoginScreen.kt`: splash (core-splashscreen), input PAT filled M3, password toggle, validasi, loading, error
- [x] Simpan token ke SharedPreferences("app"), navigasi ke MainActivity
- [x] `LoginActivity` → `ComponentActivity` + `setContent`; hapus `activity_login.xml`

## Task 5 — WorkflowScreen (Compose)
- [x] Buat `ui/workflow/WorkflowScreen.kt`: header user, kartu trigger (input CRD code), daftar runs (LazyColumn), status chip, cancel/delete/logs dialog, snackbar error
- [x] Integrasi `WorkflowViewModel` (runs/isLoading/error + startRun/cancelRun/deleteRun/deleteRunLogs/fetchLogs)
- [x] Format waktu + status icon/badge sesuai WorkflowFragment lama
- [x] Catatan: pull-to-refresh diganti tombol refresh + `LinearProgressIndicator` (PullToRefreshBox butuh material3 1.3.0 → Compose 1.7 → Kotlin 2.0; BOM dijaga di 2024.02.01 agar kompatibel compiler 1.5.8)

## Task 6 — RepoScreen + RepoViewModel (Compose)
- [x] Buat `viewmodel/RepoViewModel.kt`: StateFlow releases, activeDownloads, progressMap, speedMap + polling DownloadManager
- [x] Buat `ui/repo/RepoScreen.kt`: daftar releases + assets, tombol Download/Install, progress bar, "View All Releases"
- [x] DownloadManager enqueue + FileProvider install APK (tetap)

## Task 7 — WebViewScreen (Compose)
- [x] Buat `ui/webview/WebViewScreen.kt`: `AndroidView` WebView + toolbar (back/forward/refresh/home/external) + shortcut bar (Esc/Tab/panah/Enter/Backspace/Ctrl/Alt)
- [x] URL whitelist CRD + OAuth Google, Snackbar blokir + aksi "Buka di Browser", key event injection (scancode fisik)
- [x] saveState/restoreState WebView via `WebViewHolder` (rememberSaveable Saver, hoisted di MainScreen)
- [x] Progress bar + domain URL + security icon

## Task 8 — ProfileScreen (Compose)
- [x] Buat `ui/profile/ProfileScreen.kt`: info user, statistik runs (total/success rate/failed/last run), menu settings, licenses, clear cache, clear all, logout
- [x] Dialog konfirmasi M3 (AlertDialog)

## Task 9 — Bersihkan legacy View system
- [x] Hapus semua `layout/*.xml` (fragment, item, activity) + fragments + adapter
- [x] Matikan `viewBinding`
- [x] Hapus drawable XML tak terpakai (shape chips lama, ripple, nav, dll) + menu + color selector — tersisa hanya 4 drawable (github_mark, splash_logo, launcher)
- [x] Pertahankan: `colors.xml` (splash/launcher), `themes.xml` (splash), `file_paths.xml`, mipmap launcher, `strings.xml`
- [x] `themes.xml` di-swap ke `android:Theme.Material(.Light).NoActionBar` murni; dep `material` (Material Components) dicabut; jitpack repo dihapus

## Task 10 — Build final & verifikasi
- [x] `./gradlew clean assembleDebug` hijau (APK 55MB, icons-extended)
- [x] Review akhir: tidak ada referensi View system tersisa (Fragment/ViewBinding/R.layout/LayoutInflater/ViewPager/findViewById = 0 match)
- [x] Update AGENTS.md sesuai arsitektur Compose baru

---

## Ringkasan hasil

- 100% Compose: semua screen (`Login`, `Workflow`, `Repo`, `WebView`, `Profile`) + design system M3 (`ui/theme/`).
- Version catalog (`gradle/libs.versions.toml`) menggantikan string hardcoded.
- `RepoViewModel` baru (StateFlow + DownloadManager polling) menggantikan state fragment.
- `WebViewHolder` (rememberSaveable Saver) menjaga state WebView lintas tab & rotasi.
- Dependency View system tersisa: **nol**. BOM Compose 2024.02.01 + compiler 1.5.8 (lock Kotlin 1.9.22).