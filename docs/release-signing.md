# Release signing

CubeVPN's release APKs are signed by a single, fixed keystore stored **only** as an
encrypted GitHub Actions secret — never in this repository. This document explains how
that's wired up, how to verify a release's signature is consistent with previous ones,
and what to do (and not do) if the keystore is ever lost or rotated.

## Why the keystore never lives in the repo

Android requires an "update" (an APK installed over an existing app of the same
`applicationId`) to be signed with the **same certificate** as the version already on
the device, or the install is rejected. That means:

- Whoever holds the keystore can publish something Android will accept as a legitimate
  CubeVPN update on every device that has the app installed.
- A keystore committed to git — even in a private repo, even removed in a later commit —
  stays in the repo's history forever and is recoverable by anyone who ever gets read
  access (a compromised contributor account, an accidental `public` flip, a forked clone,
  a leaked backup, etc).

So the keystore is kept out of git entirely and only ever touches disk transiently, on
the GitHub Actions runner, for the duration of a single build.

## How it's wired

**Secrets** (set under repo Settings → Secrets and variables → Actions):

| Secret | What it is |
|---|---|
| `KEYSTORE_BASE64` | The `.jks` keystore file, base64-encoded (`base64 -w0 release.jks`) |
| `KEYSTORE_PASSWORD` | The keystore's store password |
| `KEY_ALIAS` | The key alias inside the keystore |
| `KEY_PASSWORD` | That key's password |

**`.github/workflows/release.yml`** decodes the keystore to a temp file on the runner,
passes the four values as environment variables to the Gradle invocation, and deletes
the temp keystore file in a final `if: always()` cleanup step so it never lingers on
the runner past the build:

```yaml
- name: Decode keystore
  run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > "$RUNNER_TEMP/release.jks"

- name: Build signed release
  env:
    KEYSTORE_FILE: ${{ runner.temp }}/release.jks
    KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
  run: ./gradlew assembleRelease --no-daemon ...

- name: Clean up secrets from runner
  if: always()
  run: rm -f "$RUNNER_TEMP/release.jks" local.properties secrets.properties
```

**`app/build.gradle.kts`** reads those same four environment variables and only applies
the release signing config when they're actually present:

```kotlin
signingConfigs {
    create("release") {
        val kf = System.getenv("KEYSTORE_FILE")
        if (kf != null) {
            storeFile = file(kf)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
}

buildTypes {
    release {
        if (System.getenv("KEYSTORE_FILE") != null) {
            signingConfig = signingConfigs.getByName("release")
        }
        ...
    }
}
```

The `release` build type never references `signingConfigs.debug` anywhere, so there is
no path by which a release build accidentally ends up debug-signed. The one real risk
with the "only sign if the env var is present" pattern is the opposite failure mode: if
a secret is missing or empty, `assembleRelease` still *succeeds* — it just produces an
**unsigned** APK, which the rest of the pipeline would happily publish as if it were a
normal release. The workflow now fails fast before building if any of the four secrets
are blank, specifically to close that gap (see "Fail fast on missing secrets" below).

Because `KEYSTORE_BASE64` is a stored secret rather than something generated per run,
every workflow run decodes the exact same bytes — so every release is signed with the
same key unless someone deliberately replaces the secret's value.

## Verifying a release's certificate

After any release, download its APK and run:

```bash
apksigner verify --verbose --print-certs CubeVPN-vX.Y.Z-arm64-v8a-release.apk
```

The output includes a `Signer #1 certificate SHA-256 digest`. Compare that digest across
two different releases — if they match, both were signed by the same key. `apksigner`
ships with the Android SDK build-tools (`$ANDROID_HOME/build-tools/<version>/apksigner`).

As of this change, the release workflow itself also runs this check automatically after
building and prints the SHA-256 digest to the job log for every release — so you can
check the "Build Release" workflow run's log for any tag and confirm it matches prior
runs without needing to download anything.

## If the keystore is ever lost

There is no recovery. A lost keystore means no future build can ever again be installed
as an "update" over an existing CubeVPN install — every user would need to uninstall the
old app and install the new one fresh, losing local-only app data (saved configs,
subscriptions, etc. — anything not tied to their account) in the process. Treat losing
this keystore as equivalent to losing the ability to update the app for its entire
existing install base.

**Keep an offline backup.** Store a copy of the original `.jks` file and its two
passwords in a secure password manager or vault outside of GitHub and outside of git —
never as a repo file, never in plain chat/email. Whoever controls that backup can
regenerate the `KEYSTORE_BASE64` secret (`base64 -w0 release.jks`) if it's ever rotated
out of GitHub Actions for any reason.

**Never regenerate the keystore casually.** Rotating to a brand-new keystore is only
appropriate if you're intentionally accepting that existing installs can no longer
update in place (e.g. a deliberate relaunch under a new signing identity). It is not a
fix for a forgotten password — if a password is lost but the `.jks` file itself is
still available, that's recoverable with `keytool` support; if the file itself is gone,
it is not.
