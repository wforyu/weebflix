# Keystore Info

## ⚠ 2026-08-16: Release APK di-sign dengan RELEASE keystore (tidak hilang)

Keystore release lama (`webflix-release.jks`) **masih ada** di project root — awalnya dikira hilang karena gitignored + tidak ada backup, tapi ternyata filenya ada. Fingerprint cert-nya (`fe27099a...`) **identik dengan semua rilis resmi** (v2.0.0-beta s/d v2.0.4-beta, `CN=WebFlix`), jadi **auto-update (Check Update) antar-versi rilis mulus tanpa uninstall**.

- Release APK `assembleRelease` di-sign dengan `webflix-release.jks` (alias `webflix`, password `webflix123`)
- ⚠ **Catatan:** build release TIDAK bisa nimpa build **debug** yang terpasang lewat `installDebug`/`adb install` (signature beda) — kalau device-nya masih build debug, uninstall dulu sekali, baru update berikutnya jalan.
- Konfigurasi: `app/build.gradle.kts` → `signingConfigs.release` + `buildTypes.release { signingConfig = signingConfigs.getByName("release") }`
- **JANGAN pindah ke keystore lain** — itu bikin signature beda → auto-update gagal. Kalau terpaksa ganti, semua device harus uninstall dulu (sekali).

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
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"

# Build signed release (release keystore — lihat catatan di atas)
.\gradlew.bat assembleRelease

# APK output
# app/build/outputs/apk/release/app-release.apk (signed, CN=WebFlix)
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
- **Backup** `webflix-release.jks` ke lokasi aman di luar repo — ini keystore satu-satunya untuk auto-update release
