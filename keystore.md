# Keystore Info

## Release Keystore

| Field | Value |
|-------|-------|
| **File** | `webflix-release.jks` |
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

# Build signed release
.\gradlew.bat assembleRelease

# APK output
# app/build/outputs/apk/release/app-release.apk (signed)
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
