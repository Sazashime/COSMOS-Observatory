# COSMOS Observatory Android 1.1

This is the Android Studio project for COSMOS. It wraps the actual offline web app in an Android WebView and serves it from a private `127.0.0.1` local web server. No home PC is needed after installation. GPS permission and observation-image picker are supported. Data remains on the phone in the app WebView storage. The app does not sync with desktop browser data.

## Build on Windows (recommended)

1. Install **Android Studio** (includes Android SDK and Gradle support).
2. Unzip this directory and open `COSMOS_Android` in Android Studio.
3. Allow Gradle sync, SDK 35 installation, and Android Gradle plugin download. Requires internet.
4. Choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. Find `app/build/outputs/apk/debug/app-debug.apk`. Copy it to your Honor and open it, allowing your file manager/browser to install unknown apps if Android asks.

## Build using GitHub Actions (without Android Studio)

1. Upload the contents of this folder to a GitHub repository.
2. Visit **Actions → Build COSMOS Android APK → Run workflow**.
3. When completed, download `COSMOS-Observatory-APK` in the workflow artifacts. Unzip it to obtain `app-debug.apk`.

Debug builds are automatically signed with a development key by Android build tools. Don't distribute them as a public release. The app runs without an external server. Blob-file calendar exports might not work inside Android WebView; use the browser version for .ics/JSON exports until a native export bridge is added.

This project has NOT been compiled or tested as an actual APK in the provided environment, which lacks the Android SDK/Gradle and has no network access to download them.
