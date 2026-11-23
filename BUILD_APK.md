# Build HEY OKA APK

The Android debug APK is built by GitHub Actions using `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --stacktrace`.

The workflow uploads the APK as the `hey-oka-debug-apk` artifact. The default application language for fresh installs is Indonesian.
