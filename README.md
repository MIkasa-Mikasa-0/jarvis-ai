# JARVIS AI (Android, Kotlin + Jetpack Compose)

A Jarvis-style AI assistant that runs entirely on your phone:

- **Gemini Boss**: every request is routed by the Boss to a specialist agent
  (Research / Coding / Web / Planning) and answered via the Gemini API with a
  key you provide. No key is hardcoded anywhere.
- **Voice + Text**: Hindi/Hinglish friendly (STT set to hi-IN), Text-to-Speech
  with a female-voice option when the device provides one, chat history saved
  on-device.
- **Dark futuristic dashboard**: Boss ON/OFF, Screen Watch controls with a
  5–300 s inactivity timeout slider, agent status, task history.
- **Screen Watch**: MediaProjection-based capture, started ONLY via Android's
  consent dialog, auto-stops on inactivity or manual stop. Tap the eye icon in
  chat to ask Gemini what is on your screen.
- **Android Actions**: an AccessibilityService performs taps, scrolls, typing
  and screen reading — only after you enable it in Android Settings and only
  after confirmation for sensitive actions (pay/send/delete-like keywords).
- **Background mode**: foreground service with a visible notification, START_STICKY,
  plus honest in-app notes about battery restrictions.
- **Reliability**: network errors, HTTP 429 rate limits (with Retry-After hint),
  invalid/missing API key, missing permissions — all produce clear user messages.

## Honest limitations (read this)
- **Free Gemini tier is rate-limited** (requests per minute/day). The app tells
  you to wait when it hits 429. The Web agent has NO live browsing on the free
  plan; it answers from model knowledge and says so.
- **Building on the phone alone is not realistic.** The Android SDK's build tools
  (aapt2) ship x86_64 binaries only, so Gradle will fail on an ARM phone.
  Recommended path below builds the APK for free in GitHub's cloud from your
  phone using Termux + the GitHub CLI.

## Build the APK from your phone (Termux, no PC)

### 1. Install Termux and tools
Install **Termux from F-Droid** (the Play Store build is outdated), then:

    pkg update && pkg upgrade -y
    pkg install -y git gh unzip wget

### 2. Get this project onto your phone
Copy the `jarvis-ai` folder into Termux storage (e.g. download the project zip
in your browser, then in Termux):

    cd /sdcard/Download      # or wherever the zip is
    unzip jarvis-ai.zip -d ~/jarvis-ai
    cd ~/jarvis-ai/jarvis-ai   # folder that contains settings.gradle.kts

### 3. Push to GitHub (needed so GitHub Actions can build it)
Create a free account at https://github.com in your browser first. Then:

    git init
    git add .
    git config user.email "you@example.com"
    git config user.name "Your Name"
    git commit -m "JARVIS AI initial"
    gh auth login          # choose GitHub.com / HTTPS / Login with a web browser
    gh repo create jarvis-ai --public --source=. --push

### 4. Build the APK in the cloud
1. In your phone browser open `https://github.com/<you>/jarvis-ai/actions`
2. Tap **"Build JARVIS APK"** → **Run workflow**.
3. Wait 5–15 minutes (first run downloads Gradle + Android SDK).
4. Open the finished run → **Artifacts** → download **jarvis-ai-apk** (a zip).
5. Unzip it (Files app or `unzip jarvis-ai-apk.zip` in Termux) to get
   `app-debug.apk`, tap it → **Install** (allow "install from this source").

Every future `git push` triggers a rebuild automatically.

### 5. Configure the app
1. Get a FREE key: https://aistudio.google.com → "Get API key".
2. Open JARVIS AI → **Settings** → paste key → **SAVE** → **TEST KEY**.
3. Phone Settings → Accessibility → **JARVIS AI** → ON (required for taps/typing).
4. Allow Microphone + Notifications when asked.

### 6. Test each feature
- **Chat/voice**: tap mic, say "mera din kaise jayega aaj plan banao" (Planning
  agent) or "kotlin mein recycler view ka code do" (Coding agent). Replies speak
  aloud via TTS.
- **Screen Watch**: Dashboard → START → accept Android's consent dialog → status
  ON. Wait quietly: it auto-stops after your timeout. In chat tap the eye icon to
  ask Gemini about the current screen.
- **Actions**: with accessibility ON, say "read the screen" (runs READ), "scroll
  down", "tap on <visible label>". Payment/send/delete-like requests pop a
  confirm dialog first.
- **Background**: toggle ON in Dashboard → a persistent notification appears.
  Some OEMs (Xiaomi/Oppo/Vivo) still kill apps; that is an Android limitation.

## Try a full on-device build anyway (advanced, likely to fail)
    pkg install -y openjdk-17
    wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
    mkdir -p ~/android-sdk/cmdline-tools && unzip commandlinetools*.zip -d ~/android-sdk/cmdline-tools
    cd ~/android-sdk/cmdline-tools && mv cmdline-tools latest
    yes | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
    ~/android-sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-34" "build-tools;34.0.0"
    wget https://services.gradle.org/distributions/gradle-8.7-bin.zip && unzip gradle-8.7-bin.zip
    cd ~/jarvis-ai/jarvis-ai
    ANDROID_HOME=~/android-sdk ./gradle-8.7/bin/gradle assembleDebug

`aapt2` (inside build-tools) is an x86_64 Linux binary and will not execute on
ARM — this is a Google platform limitation, not a project bug. Use the GitHub
Actions path above.

## Security notes
- API key lives in the app's private SharedPreferences — never in code, logs,
  or chat. All Gemini traffic is HTTPS.
- Screen frames are held in memory only, sent to Gemini solely when you ask,
  and never written to disk or chat.
- No permission is bypassed: MediaProjection consent, accessibility opt-in, and
  runtime permissions are all requested through official Android flows.
