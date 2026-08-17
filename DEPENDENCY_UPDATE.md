# Daftar Update Dependency — Nathan Workspace

Diperiksa: **17 Agustus 2026** (langsung dari Maven metadata + tabel kompatibilitas resmi Google/JetBrains/Gradle).

> **Status: ✅ PATH A TELAH DIEKSEKUSI (17 Agu 2026)** — semua komponen di kolom "terbaru" sudah diterapkan, `./gradlew clean assembleDebug` hijau, APK `app-debug.apk` (~64 MB) terbit. Detail riwayat di bagian 5.

---

## 1. Tabel semua dependency

| # | Komponen | Versi saat ini | Versi terbaru (stable) | Update? | Catatan |
|---|----------|---------------|------------------------|---------|---------|
| 1 | AGP (`com.android.application`) | 8.2.2 | **9.3.1** | ✅ | Wajib Gradle 9.5.0. AGP 9.x = Kotlin built-in (plugin `kotlin.android` tak lagi dipakai). Max API 37. |
| 2 | Gradle (wrapper) | 8.5 | **9.5.0** | ✅ | Min untuk AGP 9.3. AGP 8.13 cukup dengan Gradle 8.13. |
| 3 | Kotlin (`org.jetbrains.kotlin.android`) | 1.9.22 | **2.4.10** | ✅ | KGP 2.4.x: Gradle 7.6.3–9.5.0, AGP **8.5.2–9.1.0** (dukungan penuh). |
| 4 | Compose compiler | 1.5.8 (`composeOptions`) | **bundled Kotlin 2.x** (`org.jetbrains.kotlin.plugin.compose`) | ✅ | Sejak Kotlin 2.0 compiler menyatu dengan Kotlin; `composeOptions.kotlinCompilerExtensionVersion` dihapus. |
| 5 | Compose BOM | 2024.02.01 | **2026.08.00** | ✅ | Menarik ui 1.9.x, material3 1.4.0, icons-extended 1.7.8. |
| 6 | material3 (via BOM) | 1.2.0 | **1.4.0** | ✅ | `PullToRefreshBox` tersedia (dulu dipaksa downgrade). |
| 7 | material-icons-extended (via BOM) | 1.6.2 | **1.7.8** | ✅ | APK tetap besar; alternatif: pindah ke ikon `material-icons-core` + drawable custom. |
| 8 | androidx.core:core-ktx | 1.12.0 | **1.19.0** | ✅ | Kemungkinan butuh compileSdk 36. |
| 9 | androidx.activity:activity-compose | 1.8.2 | **1.13.0** | ✅ | |
| 10 | androidx.lifecycle (viewmodel-compose, runtime-compose) | 2.7.0 | **2.11.0** | ✅ | `androidx.lifecycle.compose.LocalLifecycleOwner` tersedia (hapus import lama). |
| 11 | kotlinx-coroutines-android | 1.7.3 | **1.11.0** | ✅ | |
| 12 | okhttp | 4.12.0 | **5.5.0** | ✅ | Breaking API di 5.x (`okhttp3.internal` dihapus, dll). 4.12.0 masih dipelihara. |
| 13 | gson | 2.10.1 | **2.14.0** | ✅ | |
| 14 | androidx.core:core-splashscreen | 1.0.1 | **1.2.0** | ✅ | |
| 15 | compileSdk | 34 | **36** (Android 16) | ✅ | Dibutuhkan libs terbaru (core-ktx 1.19). AGP 8.2.2 max mendukung 34–35; butuh AGP ≥8.6 untuk API 35+. |
| 16 | targetSdk | 34 | **35+** | ✅ | **WAJIB** — Play Store menolak submission baru dengan targetSdk <35 sejak 31 Agu 2025. |
| 17 | Build Tools | default | 36.0.0 | ✅ | Ikut AGP. |
| 18 | JDK | 17 | 17 (min AGP) | ➖ | AGP 9.x tetap butuh JDK 17+; Studio terbaru bundel JDK 21. Tidak perlu diubah. |
| 19 | minSdk | 29 | 29 | ➖ | Tidak perlu dinaikkan. |

---

## 2. Rute upgrade yang disarankan

### Path A — Modern penuh (rekomendasi, tapi migrasi besar)
Koordinasi wajib: **Gradle 9.5.0 + AGP 9.3.1 + Kotlin 2.4.10 + compileSdk 36 + BOM 2026.08.00**

- AGP 9.x: Kotlin built-in → hapus plugin `org.jetbrains.kotlin.android`, hapus `kotlinOptions`/`composeOptions`, tambah plugin `org.jetbrains.kotlin.plugin.compose`.
- Versi aman: Kotlin 2.4.10 + AGP 9.3.1 (built-in Kotlin), atau KGP 2.4.10 + AGP maks **9.1.0**.
- Semua libs lain tinggal naik ke kolom "terbaru".
- Risiko: migrasi DSL (AGP 9 menghapus beberapa API lama), butuh Studio Panda/Quail.

### Path B — Upgrade ringan tanpa sentuh AGP/Gradle (minimal risiko)
Hanya 3 langkah, AGP 8.2.2 + Gradle 8.5 tetap:
1. **Kotlin 1.9.22 → 2.3.21** (kompatibel: Gradle 7.6.3–9.3.0, AGP 8.2.2–9.0.0)
2. Tambah plugin `org.jetbrains.kotlin.plugin.compose` 2.3.21, hapus `composeOptions.kotlinCompilerExtensionVersion`
3. **BOM 2024.02.01 → 2026.08.00** → material3 1.4.0, `PullToRefreshBox` bisa dipakai, core-ktx/lifecycle/activity naik sesuai tabel

> Catatan Path B: AGP 8.2.2 max compileSdk 34 → untuk compileSdk 36 tetap perlu AGP ≥8.6 (langkah terpisah).

---

## 3. Ketergantungan antar versi (hal penting)

- **AGP 9.3.1 ⟷ Gradle 9.5.0** (min) — lihat tabel resmi Google.
- **KGP 2.4.10 ⟷ Gradle maks 9.5.0, AGP maks 9.1.0** (dukungan penuh) — di luar itu hanya warning/deprecation.
- **Kotlin 2.x ⟷ Compose**: pakai plugin `org.jetbrains.kotlin.plugin.compose` versi = versi Kotlin. Compiler standalone 1.5.8 hanya untuk Kotlin 1.9.x.
- **BOM 2026.08.00** berisi ui 1.9.x/material3 1.4.0 yang dibangun dengan Kotlin 2.x — **tidak bisa** dipakai bersama compiler 1.5.8.
- **core-ktx 1.19.0 / activity 1.13.0** cenderung butuh compileSdk 36 → naikkan juga compileSdk + AGP yang mendukung.
- **targetSdk ≥35** adalah kewajiban Play Store (batas 31 Agu 2025) — terlepas dari update lain.
- **okhttp 5.x**: pecah API dibanding 4.x; jika tidak butuh fitur baru, 4.12.0 aman dibiarkan.

---

## 4. Status saat ini setelah migrasi Compose (AGENTS.md lock)

```
Kotlin 1.9.22 + AGP 8.2.2 + Gradle 8.5 + compose compiler 1.5.8 + BOM 2024.02.01
compileSdk/targetSdk 34, JDK 17, minSdk 29
```

Lock ini **sengaja** dibuat karena kenaikan Kotlin/BOM tanpa upgrade terkoordinasi akan mematahkan build.

---

## 5. Riwayat eksekusi Path A (17 Agu 2026)

### Sebelum → Sesudah

| Komponen | Sebelum | Sesudah |
|---|---|---|
| Gradle | 8.5 | 9.5.0 |
| AGP | 8.2.2 | 9.3.1 |
| Kotlin | 1.9.22 (plugin `kotlin.android`) | 2.4.10 (built-in AGP 9; hanya `kotlin.plugin.compose`) |
| Compose BOM | 2024.02.01 | 2026.08.00 (material3 1.4.0) |
| icons-extended | via BOM 1.6.2 | pin eksplisit **1.7.8** (tidak lagi di BOM) |
| core-ktx | 1.12.0 | 1.19.0 |
| activity-compose | 1.8.2 | 1.13.0 |
| lifecycle | 2.7.0 | 2.11.0 |
| coroutines | 1.7.3 | 1.11.0 |
| okhttp | 4.12.0 | 5.5.0 |
| gson | 2.10.1 | 2.14.0 |
| splashscreen | 1.0.1 | 1.2.0 |
| compileSdk | 34 | **37** (dipaksa okhttp-android 5.5.0 + core-ktx 1.19) |
| targetSdk | 34 | 36 |

### Perubahan build file
- `plugins {}`: hapus `org.jetbrains.kotlin.android` (AGP 9 error jika dipakai — Kotlin built-in), tambah `org.jetbrains.kotlin.plugin.compose`.
- Hapus blok `kotlinOptions {}` dan `composeOptions {}` (compiler menyatu Kotlin 2.x; jvmTarget default = `compileOptions.targetCompatibility` = 17).
- `material-icons-extended` di-pin ke 1.7.8 karena sudah tidak dikelola BOM 2026.08.00.
- Wrapper: `gradle-8.5-bin.zip` → `gradle-9.5.0-bin.zip`.
- SDK platform yang terpakai: `android-37.0` + build-tools 37 (sudah terpasang).

### Catatan
- OkHttp 5.5.0 butuh compileSdk ≥37 — ini alasan compileSdk 36 tidak cukup.
- Kode sumber **tidak berubah sama sekali** (tidak ada breaking API yang kena).
- APK membesar ~9 MB (55 → 64 MB) karena dependency lebih baru + icons-extended 1.7.8.