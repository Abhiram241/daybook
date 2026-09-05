# Release signing

The `release` build type is signed with a real keystore instead of the Android debug key.

## How it works

- `keystore.properties` (repo root, **gitignored**) holds the credentials:

  ```
  storeFile=daybook-release.jks
  storePassword=...
  keyAlias=daybook
  keyPassword=...
  ```

- `app/build.gradle.kts` loads that file if present and creates a `release` signing config from it.
- If `keystore.properties` is **absent**, the release build falls back to the debug key so the
  project still assembles on a machine without the keystore.
- `app/daybook-release.jks` and `keystore.properties` are both in `.gitignore` — never commit them.

## Current keystore (development)

A throwaway keystore was generated so release builds are self-consistent:

| field | value |
|---|---|
| file | `app/daybook-release.jks` |
| alias | `daybook` |
| store/key password | `daybook-release` |
| dname | `CN=Daybook, O=abhiram, C=IN` |
| validity | 10000 days |

This is fine for sideloaded personal builds. **Do not** treat it as secret or as a
Play-upload key.

## Regenerating

```bash
"$JAVA_HOME/bin/keytool" -genkeypair -v \
  -keystore app/daybook-release.jks \
  -alias daybook \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass <STORE_PW> -keypass <KEY_PW> \
  -dname "CN=Daybook, O=abhiram, C=IN"
```

Then update `keystore.properties` to match.

## Swapping in a production key

1. Create/obtain the real keystore, put it somewhere outside the repo (or keep it here — it's
   gitignored).
2. Point `keystore.properties` at it (`storeFile` may be an absolute path).
3. Rebuild. The first install of a differently-signed APK over an existing one requires an
   uninstall (Android rejects a signature change), which wipes local data — do this swap
   before wider distribution.
