# Snake Game

Minimal offline Android game for Google Play review and store-review workflow testing.

## No Android Studio release flow

This project can be built for Google Play entirely in GitHub Actions. You do not need Android Studio locally.

1. Create one upload keystore with the JDK `keytool`.
2. Add the upload keystore to GitHub repository secrets.
3. Push to `main` or run the `Android Build` workflow manually.
4. Download `snake-release` from the workflow artifacts.
5. Upload `app-release.aab` to Google Play Console.

The GitHub runner installs the Android SDK packages needed for the build.

## Build

Debug build:

```sh
./gradlew :app:assembleDebug
```

Release Android App Bundle:

```sh
KEYSTORE_PATH=/absolute/path/upload-keystore.jks \
KEYSTORE_PASSWORD=... \
KEY_ALIAS=... \
KEY_PASSWORD=... \
./gradlew :app:bundleRelease
```

The release artifact is written to:

```text
app/build/outputs/bundle/release/app-release.aab
```

## Google Play signing

Do not generate a new keystore for every release. Google Play requires future uploads for the same app to be signed by the same upload key certificate, unless the upload key is reset in Play Console.

If this app has already uploaded an AAB to Google Play, use the same upload keystore that signed that first upload.

For a new app, create one upload keystore:

```sh
keytool -genkeypair \
  -v \
  -keystore upload-keystore.jks \
  -storetype PKCS12 \
  -alias snake \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

For GitHub Actions, store the upload keystore as this repository secret:

```text
KEYSTORE_BASE64
```

The workflow is compatible with the legacy keystore settings used by this repo:

```text
KEYSTORE_PASSWORD=android123
KEY_ALIAS=snake
KEY_PASSWORD=android123
```

You can override those defaults by adding `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` as repository secrets.

For a real production app, avoid the legacy defaults. If an old public workflow artifact included `keystore.jks`, treat that upload key as exposed. Delete the old artifacts and use a fresh upload key before the first Play upload, or reset the upload key in Play Console if the exposed key was already used.

On macOS, encode the keystore for `KEYSTORE_BASE64` with:

```sh
base64 -i upload-keystore.jks | pbcopy
```

On Linux:

```sh
base64 -w 0 upload-keystore.jks
```
