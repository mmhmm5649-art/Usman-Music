# USMAN Music Player — Liquid Glass Edition

An ultra-premium, professional Android music player inspired by Apple Music and the Liquid Glass design language. Featuring real-time audio visualization, genuine backdrop blur, and seamless local music discovery.

## 🌟 Key Features
- **Real Liquid Glass UI**: Authentic backdrop blur using `skydoves/Cloudy` for a sophisticated, translucent aesthetic.
- **Rainbow Audio Visualizer**: Frequency-reactive spectrum bars with mirrored reflections and smooth neon transitions.
- **Autoritative Playback**: Powered by AndroidX Media3 ExoPlayer for stable, high-fidelity audio.
- **Background Music**: Fully integrated `MediaSessionService` for background playback and system notification controls.
- **Local Discovery**: Dual-engine scanning (MediaStore + SAF) to find your music anywhere on your device.
- **Premium Design**: Dark AMOLED theme with cyan, purple, and magenta accents.

## 🛠 Technical Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Media Engine**: AndroidX Media3 (ExoPlayer)
- **Design System**: Material 3 + Liquid Glass
- **Library Management**: Gradle Version Catalog

## 🚀 Build Instructions
1. Clone this repository:
   ```bash
   git clone <repository-url>
   ```
2. Open in Android Studio (Ladybug or newer recommended).
3. Build the project using the Gradle wrapper:
   ```bash
   ./gradlew assembleDebug
   ```
4. Find your APK at `app/build/outputs/apk/debug/app-debug.apk`.

## 📦 Downloading the APK
If you are viewing this on GitHub, you can download the latest build:
1. Navigate to the **Actions** tab.
2. Select the latest **Android Build** workflow run.
3. Scroll down to the **Artifacts** section.
4. Download and extract **USMAN-MUSIC-PLAYER-debug-APK**.

## 📄 License
MIT License - see LICENSE for details.
