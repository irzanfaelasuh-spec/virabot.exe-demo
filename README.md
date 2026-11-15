# virabot.exe — Android Spider Simulator

A red-spider, retro-computer themed **visual simulator** for Android. The current build keeps all effects inside the app.

## Included

- Smooth animated spider with an eight-leg procedural gait, gentle roaming motion, and subtle red aura.
- Idle scene, five-step retro warning sequence, and Safe Mode.
- Tap the spider once to start the five-popup warning sequence.
- Tap the spider four times within 850 ms to enter Safe Mode.
- Each unacknowledged in-app popup is split into two falling halves with gravity, rotation, fade-out, red particles, and `0` / `1` glyphs.
- Original synthesized retro-inspired warning and bite sounds.
- Settings for sound and particle effects.
- Red spider launcher icon.
- GitHub Actions workflow to build a debug APK and upload it as an artifact.

## Safety and scope

This app is intentionally a self-contained simulation. It does **not** request overlay, Accessibility, contacts, storage, package-list, or notification permissions. It does not block or alter other apps, show fake Android permission dialogs, delete anything, or run secretly in the background. Animations run while the app is open.

The retro popup is an in-app visual effect, not a real Android notification or Windows system dialog.

## Build from GitHub (phone-friendly)

1. Create a new GitHub repository named `virabot-exe`.
2. Upload the contents of this ZIP to the repository root (not the ZIP file itself).
3. Open the **Actions** tab and enable Actions if GitHub asks.
4. Run **Build virabot.exe APK** with **Run workflow**, or push to `main` / `master`.
5. Open the completed workflow run and download the `virabot-exe-debug-apk` artifact. It contains `virabot.exe.apk`.
6. Extract the artifact ZIP on your phone and install `app-debug.apk`. Android may ask you to approve installing an APK from your browser/files app.

## Build configuration

- Package: `com.virabot.exe`
- App label: `virabot.exe`
- Min SDK: 24 (Android 7.0)
- Compile SDK: 35 / target SDK: 34
- JDK: 17
- Gradle: 8.9
- Android Gradle Plugin: 8.7.3

## Project structure

```text
virabot-exe/
├── .github/workflows/android.yml
├── app/
│   ├── src/main/AndroidManifest.xml
│   ├── src/main/java/com/virabot/exe/MainActivity.kt
│   ├── src/main/res/...
│   └── build.gradle.kts
├── assets/virabot-spider-icon.png
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```
