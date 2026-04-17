# AGENTS.md — StepsNotifier

## Project Overview

**StepsNotifier** is an Android application (package `steps.notifer.app`) built with
**Kotlin Multiplatform + Jetpack Compose**. Its purpose is to keep the user walking: at a
user-chosen target time (e.g. 18:00) it checks today's step count against a goal via the
**Android Health Connect API** and sends a notification if the goal has not been met. It
then retries every 30 minutes until the goal is reached, the user taps "Skip", or midnight
arrives.

A nightly **CorrectionWorker** automatically neutralises carry-over steps from the previous
day so that the displayed count starts from zero each morning.

---

## Repository Layout

```
StepsNotifier/
├── build.gradle.kts                  # Root project: plugin declarations only
├── settings.gradle.kts               # Root project name, single module :composeApp
├── gradle/libs.versions.toml         # Version catalog (all dependency/plugin versions)
└── composeApp/
    ├── build.gradle.kts              # Module config: KMP targets, dependencies
    └── src/
        └── androidMain/
            ├── AndroidManifest.xml   # Permissions, receivers, activities
            └── kotlin/steps/notifer/app/
                ├── App.kt                  # Compose UI root (@Composable App)
                ├── MainActivity.kt         # Entry point; requests HC permissions
                ├── StepGoalViewModel.kt    # UI state & business logic (AndroidViewModel)
                ├── WorkScheduler.kt        # WorkManager scheduling helpers
                ├── MainStepWorker.kt       # Fires at target time, checks goal
                ├── RetryStepWorker.kt      # Repeats every 30 min until goal met
                ├── CorrectionWorker.kt     # Nightly 2-phase midnight-correction worker
                ├── HealthConnectHelper.kt  # All Health Connect reads (steps, debug)
                ├── NotificationHelper.kt   # Creates channel, shows/cancels notifications
                ├── NotificationPhrases.kt  # Random motivational notification copy
                ├── DataStoreHelper.kt      # DataStore persistence (goal, correction)
                ├── SkipReceiver.kt         # BroadcastReceiver: user taps "Skip"
                └── BootReceiver.kt         # BroadcastReceiver: reschedules on reboot
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin (JVM 11, KMP Android-only target) |
| UI | Jetpack Compose + Material 3 (Compose Multiplatform) |
| Background work | AndroidX WorkManager (`CoroutineWorker`) |
| Health data | AndroidX Health Connect (`HealthConnectClient`) |
| Persistence | AndroidX DataStore Preferences |
| State management | `StateFlow` + `AndroidViewModel` |
| Build system | Gradle (Kotlin DSL) + Version Catalog |
| Min SDK | see `libs.versions.toml` → `android.minSdk` |
| Target/Compile SDK | see `libs.versions.toml` → `android.compileSdk` |

---

## Key Concepts and Invariants

### Step Count Correction
Health Connect (and especially Samsung Health) can carry steps from the tail of the
previous day into the new day's count. The app compensates with a **steps correction**
offset stored in DataStore:

```
effectiveSteps = rawHealthConnectSteps + correction
```

`correction` is usually a **negative** number. It is auto-set every night at **3:30 AM**
by `CorrectionWorker` phase 2 (`correction = -steps_at_3:30_AM`). The user can also edit
it manually from the UI.

### WorkManager Workers

| Worker | Tag / Unique Name | Fires When | Purpose |
|---|---|---|---|
| `MainStepWorker` | `main_worker` | User's target time (T) | First daily step-goal check |
| `RetryStepWorker` | `retry_worker` | T + 30 min, repeating | Re-checks until goal met or midnight |
| `CorrectionWorker` phase 1 | `correction_check` | 2:00 AM nightly | Schedules phase 2 |
| `CorrectionWorker` phase 2 | `correction_followup` | 3:30 AM nightly | Reads steps → saves negative correction |

**Scheduling rules:**
- `MainStepWorker` uses `ExistingWorkPolicy.REPLACE` — changing the goal time cancels any
  previously scheduled check.
- `CorrectionWorker` phase 1 uses `ExistingWorkPolicy.KEEP` — prevents double-scheduling
  on every app open.
- `BootReceiver` reschedules both `MainStepWorker` and `CorrectionWorker` after device
  reboot so the cycle resumes automatically.

### Health Connect Reading Strategy (`HealthConnectHelper.getTodaySteps`)
Some step sources (Samsung Health) write a single "daily summary" `StepsRecord` spanning
≥ 20 hours. Health Connect's `aggregate()` interpolates such records proportionally,
yielding a *lower* (incorrect) count. The helper:
1. Reads all records for today with `readRecords()`.
2. If any record spans ≥ 20 hours → uses `max(count)` of those records.
3. Otherwise → falls back to `aggregate()` (proper deduplication across granular sensors).

### Notifications
- Single notification, ID `1001`, channel `step_goal_channel`.
- Shows a **"Skip"** action button (except on the midnight-failure notification).
- Tapping "Skip" fires `SkipReceiver`, which cancels all retry workers and schedules the
  next day's main worker.

---

## Permissions Required

| Permission | Reason |
|---|---|
| `health.READ_STEPS` | Read daily step count from Health Connect |
| `health.READ_HEALTH_DATA_IN_BACKGROUND` | Workers read HC without the app in foreground |
| `POST_NOTIFICATIONS` | Show step-goal / retry notifications |
| `RECEIVE_BOOT_COMPLETED` | `BootReceiver` reschedules workers after reboot |

Health Connect also requires the app to declare the `HealthConnectRequestPermissionsActivity`
alias and the `<queries>` block for `com.google.android.apps.healthdata`.

---

## Coding Conventions

1. **Single-module KMP project** — all source lives under `composeApp/src/androidMain/`.
   There is no `commonMain` Kotlin source yet (only `commonMain.dependencies` in Gradle).
2. **`object` singletons** for stateless helpers (`WorkScheduler`, `HealthConnectHelper`,
   `NotificationHelper`, `PrefsKeys`).
3. **`CoroutineWorker`** for all background work — never plain `Worker` or `ListenableWorker`.
4. **`StateFlow` + `collectAsState()`** for every piece of UI-visible state in the ViewModel.
5. **DataStore** (not SharedPreferences) for all persistent settings.
6. **`ExistingWorkPolicy.REPLACE`** when the user changes settings; **`KEEP`** for
   idempotent background schedules that must not be duplicated.
7. Keep correction logic consistent: **always** apply `rawSteps + correction` before
   comparing against the goal — in `MainStepWorker`, `RetryStepWorker`, and the ViewModel.

---

## Building & Running

```bash
# Build debug APK
./gradlew :composeApp:assembleDebug

# Install on connected device / emulator
./gradlew :composeApp:installDebug

# Run all unit tests
./gradlew :composeApp:test
```

> Health Connect and WorkManager are difficult to test without a real or emulated Android
> device. Integration testing should be done on-device or via an Android emulator with
> Health Connect installed.

---

## Common Agent Tasks

- **Add a new notification phrase** → edit `NotificationPhrases.kt`; add strings to the
  `initial` or `retry` lists.
- **Change the correction schedule time** → edit `WorkScheduler.enqueueCorrectionCheckWorker()`
  (phase 1 time) and `WorkScheduler.enqueueCorrectionFollowup()` (phase 2 delay).
- **Add a new persisted setting** → add a key in `DataStoreHelper.kt` (`PrefsKeys`), a
  `save*` extension, and a `*Flow` extension; expose via `StepGoalViewModel`.
- **Add a new UI card** → edit `App.kt`; bind to a new `StateFlow` in `StepGoalViewModel`.
- **Handle a new step-source quirk** → update `HealthConnectHelper.getTodaySteps()` and
  document the detection logic there.
- **Add a platform target** (e.g. iOS) → add the target in `composeApp/build.gradle.kts`
  and create the corresponding `src/<target>Main/` source set.
