# Keystore Info

## ⚠ 2026-08-16: Release APK kini di-sign dengan DEBUG keystore

Keystore release lama (`webflix-release.jks`) **hilang** (gitignored, tidak pernah di-commit, tidak ada backup). Supaya **auto-update (Check Update) bisa nimpa versi yang terinstall**, release APK sekarang di-sign dengan **debug keystore** (`~/.android/debug.keystore`, `CN=Android Debug`) — signature SAMA dengan build debug yang dipasang lewat `installDebug`/`adb install`. Ini berarti:

- APK rilis `assembleRelease` bisa di-install di atas build debug yang ada (tanpa uninstall, data aman)
- **Konsekuensi:** semua build (debug + release) share satu signature → cocok untuk distribusi pribadi
- Konfigurasi: `app/build.gradle.kts` → `buildTypes.release { signingConfig = signingConfigs.getByName("debug") }`
- **JANGAN pindah ke keystore lain** (mis. regenerasi `webflix-release.jks`) — itu bikin signature beda → auto-update gagal lagi. Kalau terpaksa ganti, semua device harus uninstall dulu (sekali), baru update berikutnya jalan.

## Release Keystore (histori — tidak dipakai lagi)

| Field | Value |
|-------|-------|
| **File** | `webflix-release.jks` (tidak ada — hilang) |
| **Alias** | `webflix` |
| **Store Password** | `webflix123` |
| **Key Password** | `webflix123` |
| **Algorithm** | RSA 2048-bit |
| **Validity** | 10,000 days (~27 years) |
| **Expires** | ~2053 |
| **DN** | CN=WebFlix, OU=Dev, O=WebFlix, L=Unknown, ST=Unknown, C=ID |

## Build Signed Release APK

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# Build signed release (debug keystore — lihat catatan di atas)
.\gradlew.bat assembleRelease

# APK output
# app/build/outputs/apk/release/app-release.apk (signed, CN=Android Debug)
```

## Verify Signature

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
& "$env:JAVA_HOME\bin\jarsigner.exe" -verify -verbose -certs WebFlix.apk
```

## Rebuild Keystore (if lost)

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v `
    -keystore webflix-release.jks `
    -keyalg RSA -keysize 2048 -validity 10000 `
    -alias webflix `
    -storepass webflix123 -keypass webflix123 `
    -dname "CN=WebFlix, OU=Dev, O=WebFlix, L=Unknown, ST=Unknown, C=ID"
```

## Notes

- Keystore file `webflix-release.jks` is in project root (gitignored)
- `app/build.gradle.kts` signing config references this keystore
- Debug builds use Android's default debug keystore (no password needed)
- **DO NOT** commit keystore or passwords to public repos
