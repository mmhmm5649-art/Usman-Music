# USMAN MUSIC — FIREBASE & GITHUB ACTIONS SETUP GUIDE

This repository contains **USMAN MUSIC** (`com.usman.musicplayer`), a native Android music player built with Kotlin and Jetpack Compose featuring Google Sign-In, Media3 playback, AMLL synchronized lyrics, and professional audio effects.

## Why GitHub Actions Fails Initially
By default, `app/google-services.json` is excluded from git version control (via `.gitignore`) to secure your Firebase credentials. When building on GitHub Actions, the file is missing unless restored securely from a repository secret.

---

## How to Configure GitHub Actions Secret (`GOOGLE_SERVICES_JSON_BASE64`)

To make your GitHub Actions workflow successfully build and export your APK, follow these steps:

### Step 1: Generate the Base64 String
On your local machine where your Firebase `google-services.json` file is located:

* **Linux / macOS (Terminal):**
  ```bash
  base64 -w 0 app/google-services.json
  ```

* **Windows (PowerShell):**
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("app/google-services.json"))
  ```

*Copy the entire single-line Base64 output string.*

---

### Step 2: Add the Secret to Your GitHub Repository
1. Open your repository on GitHub: **`mmhmm5649-art/Usman-Music`**
2. Click the **Settings** tab at the top.
3. In the left sidebar, expand **Secrets and variables** and click **Actions**.
4. Click the green **New repository secret** button.
5. Fill in the secret details:
   * **Name:** `GOOGLE_SERVICES_JSON_BASE64`
   * **Secret:** Paste the Base64-encoded string from Step 1.
6. Click **Add secret**.

---

### Step 3: Run the Workflow
1. Click the **Actions** tab in your repository.
2. Select **Android Build & APK Export**.
3. Click **Run workflow** (or push a commit to `main`).
4. GitHub Actions will automatically decode and validate `app/google-services.json`, compile Kotlin, run tests, assemble the debug APK, and publish the downloadable **`USMAN-MUSIC-PLAYER-debug-APK`** artifact.

---

## Simple Android Phone Instructions for Setup & Testing

If you want to build and test USMAN MUSIC locally on an Android device or emulator:

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/mmhmm5649-art/Usman-Music.git
   cd Usman-Music
   ```
2. **Add Firebase Configuration:**
   - Place your genuine `google-services.json` file inside the `app/` directory (`app/google-services.json`).
3. **Build the Debug APK Locally:**
   - **Using Terminal / CLI:**
     ```bash
     chmod +x gradlew
     ./gradlew :app:assembleDebug
     ```
   - **APK Output Location:**
     ```text
     app/build/outputs/apk/debug/app-debug.apk
     ```
4. **Install on Phone:**
   - Transfer `app-debug.apk` to your Android phone and install it, or run directly via Android Studio / ADB.
