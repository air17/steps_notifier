<h1 align="center">
  <br>
  StepsNotifier 🚶‍♂️
  <br>
</h1>

<h4 align="center">A custom motivational Android companion built with Kotlin Multiplatform and Jetpack Compose to ensure you never miss your daily step goals.</h4>

<p align="center">
  <a href="https://kotlinlang.org">
    <img src="https://img.shields.io/badge/Kotlin-Multiplatform-blue.svg?logo=kotlin" alt="Kotlin Multiplatform">
  </a>
  <a href="https://developer.android.com/jetpack/compose">
    <img src="https://img.shields.io/badge/Jetpack-Compose-4285F4.svg?logo=android" alt="Jetpack Compose">
  </a>
  <a href="https://developer.android.com/health-and-fitness/guides/health-connect">
    <img src="https://img.shields.io/badge/Health%20Connect-Integrated-green.svg" alt="Health Connect">
  </a>
</p>

<p align="center">
  <a href="#-the-motivation">Motivation</a> •
  <a href="#-key-features">Key Features</a> •
  <a href="#%EF%B8%8F-how-it-works">How It Works</a> •
  <a href="#-tech-stack">Tech Stack</a> •
  <a href="#-getting-started">Getting Started</a>
</p>

---

## 🎯 The Motivation

Ever realized it's 10 PM and you're 3,000 steps short of your daily goal? **StepsNotifier** is here to fix that. 

By integrating directly with **Android Health Connect**, StepsNotifier checks your progress at a target time you set (e.g., 6:00 PM). If you haven't hit your target yet, it sends you a motivational nudge. Still resting? It will persistently remind you every 30 minutes until you crush your goal, voluntarily tap "skip" for the day, or midnight strikes. 

---

## ✨ Key Features

* **⏰ Smart Goal Tracking:** Set your daily step goal and a target time for the first check-in seamlessly through the app interface.
* **🔁 Persistent Motivation:** If the goal isn't met, the app retries every 30 minutes with varied, randomized motivational notifications.
* **🌙 Auto-Correction Magic:** Automatically synchronizes and resets carry-over steps from third-party apps (like Samsung Health) every night seamlessly out of the box.
* **🏥 Deep Health Connect Integration:** Intelligently reads and deduplicates step records across multiple sensors for the most accurate daily step count.
* **🎨 Modern UI:** Built dynamically with Jetpack Compose featuring smooth Material 3 state management and persistent DataStore settings.

---

## 🛠 Tech Stack

StepsNotifier represents a modern, clean-architecture approach to Android development using foundational KMP guidelines:

* **Language:** Kotlin (JVM 11, KMP Android-only target)
* **UI toolkit:** Jetpack Compose + Material 3 (Compose Multiplatform)
* **Background Processing:** AndroidX WorkManager (`CoroutineWorker`)
* **Health Data API:** AndroidX Health Connect (`HealthConnectClient`)
* **Local Persistence:** AndroidX DataStore Preferences
* **State Management:** `StateFlow` + `AndroidViewModel`
* **Build System:** Gradle (Kotlin DSL) + Version Catalogs

---

## ⚙️ How It Works

### The Retry Loop
1. **Main Check (`MainStepWorker`):** Fires at your configured target time and checks your steps natively against the goal.
2. **The Nudge (`RetryStepWorker`):** If the goal isn't met by that time, it schedules a persistent retry 30 minutes out. 
3. **Completion:** The loop stops gracefully when the cumulative condition is met, the user taps "Skip" on the notification, or a new day cycle begins (midnight). 

### Health Connect Strategy
Handling step data can be notoriously tricky due to how different phone brands construct and write step data. StepNotifier dynamically checks for "daily summary" records (often written by Samsung Health spanning ≥ 20 hours). It handles the messy math by deciding whether to rely on aggregate intervals or specific long-tail records. 

Additionally, a silent background `CorrectionWorker` runs nightly at **3:30 AM** to calculate an offset that eliminates ghost "carry-over" steps from the previous day.

---

## 🚀 Getting Started

To build and run StepsNotifier locally:

### Prerequisites
* Android Studio (latest stable or preview version)
* A physical Android device or Emulator with the **Health Connect** app installed and configured.

### Build Instructions

Clone the repository and build via the included Gradle wrapper:
```bash
# Clone the repository
git clone https://github.com/air17/steps_notifier.git
cd steps_notifier

# Build debug APK
./gradlew :composeApp:assembleDebug

# Install on an actively connected device
./gradlew :composeApp:installDebug

# Run all unit tests
./gradlew :composeApp:test
```

> **Note:** Due to Android's background processing limits and API dependencies, Health Connect integrations and WorkManager schedules are best tested continuously on a real physical device.

---

## 🛡 Permissions

The app strictly requests only the minimal permissions it absolutely needs to keep you walking:

| Permission | Reason |
|---|---|
| `health.READ_STEPS` | Read your daily step progress via Health Connect. |
| `health.READ_HEALTH_DATA_IN_BACKGROUND` | Allows WorkManager to audit your steps seamlessly in the background. |
| `POST_NOTIFICATIONS` | Required to actually deliver periodic motivational pings. |
| `RECEIVE_BOOT_COMPLETED` | Reschedules your step workers silently if you reboot the device. |

---
<p align="center"><i>Keep walking, keep building.</i></p>
