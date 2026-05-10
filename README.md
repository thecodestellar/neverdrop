# 🪂 NeverDrop

> *The AI-Powered Memory & Follow-Through Engine for Android.*

NeverDrop is **not** a reminder app. It's an Android-native **follow-through engine** that treats every commitment as a living entity with urgency, context, relationships, and consequences. Where Todoist, TickTick, Google Tasks and Microsoft To Do are passive digital lists 📋, NeverDrop actively tracks, nudges, escalates, and adapts until things actually get done. ✅

📖 See [`NeverDrop_Case_Study.md`](./NeverDrop_Case_Study.md) for the full product vision, competitive analysis, and PRD.

---

## 📦 What's in this repo

This is the **Phase 1 Android client** — a single-module Kotlin / Jetpack Compose app implementing the Smart Capture, Intelligence Engine, and Adaptive Notification modules from the case study.

```
app/
├── build.gradle.kts
├── google-services.json          # Firebase config
└── src/main/
    ├── AndroidManifest.xml
    ├── java/com/neverdrop/
    │   ├── NeverDropApp.kt        # 🚀 Application — wires DI, schedules workers
    │   ├── MainActivity.kt        # 🎨 Compose entry point
    │   ├── data/                  # 💾 Capture, Google sync, Room, notifications, prefs, workers
    │   ├── domain/                # 🧠 Task model + intelligence use cases
    │   ├── ui/                    # 📱 Compose screens, components, navigation, theme
    │   └── widget/                # ⚡ Home screen Quick Add widget
    └── res/                       # 🎨 Drawables, layouts, widget config, themes
```

---

## 🛠️ Tech stack

| Layer | Technology |
|---|---|
| 🟪 Language / build | Kotlin, Gradle Kotlin DSL, JVM target 17 |
| 🎨 UI | Jetpack Compose (Material 3, Material Icons Extended), Navigation Compose |
| 💾 Local storage | Room (KSP), `SharedPreferences` for user settings |
| ⚙️ Background work | WorkManager (periodic + one-shot) |
| 🔔 Push | Firebase Cloud Messaging + Firebase Analytics |
| 🔐 Auth & sync | Google Identity (Credential Manager), Gmail API, Google Calendar API |
| 👁️ OCR / capture | ML Kit Text Recognition (on-device) |
| 🎙️ Voice capture | `SpeechRecognizer` (`RECORD_AUDIO`) |
| 📡 Notification mining | `NotificationListenerService` (`BIND_NOTIFICATION_LISTENER_SERVICE`) |
| 🖼️ Image loading | Coil for Compose |

> 🎯 `compileSdk = 35` · `minSdk = 26` · `targetSdk = 35` · `applicationId = com.neverdrop`

---

## 🏗️ Architecture

A pragmatic Clean-Architecture split with one Gradle module:

- 🧠 **`domain/model`** — `Task`, `CommitmentType` (PROMISE_TO_SOMEONE, SELF_GOAL, RECURRING_DUTY, TIME_SENSITIVE_EVENT, RELATIONSHIP_MAINTENANCE), `TaskPriority`, `TaskStatus` (ACTIVE / COMPLETED / SNOOZED / DROPPED / ARCHIVED).
- 🧮 **`domain/usecase`** — pure-Kotlin analytics objects: `UrgencyCalculator`, `FollowThroughScoreCalculator`, `ForgettingProfileAnalyzer`, `RelationshipTracker`. No Android dependencies — easily unit-testable.
- 🗄️ **`data/local`** — Room `NeverDropDatabase` + `TaskDao` + `TaskEntity` mapper.
- 🔁 **`data/repository`** — `TaskRepository` exposes `Flow`s for the UI.
- 📸 **`data/capture`** — `ScreenshotAnalyzer` (ML Kit OCR + regex-based commitment / sender / deadline extraction).
- 🔐 **`data/google`** — `GoogleAuthManager`, `GmailSyncService`, `CalendarSyncService`.
- 🔄 **`data/sync`** — `SyncRepository` orchestrates Gmail + Calendar pulls and dedupes against existing tasks.
- 🔔 **`data/notification`** — FCM service, notification channel manager, action receiver, response tracker (anti-fatigue), `TaskNotificationBuilder`, `NotificationMinerService`.
- ⚙️ **`data/preferences`** — `UserPreferences` wraps `SharedPreferences` (briefing time, sync toggles, mining toggle, etc.).
- ⏰ **`data/worker`** — WorkManager workers: `DeadlineAlertWorker`, `EscalationWorker`, `MorningBriefingWorker`, `EveningReviewWorker`, `WeeklyReportWorker`, `SyncWorker`, plus `WorkManagerInitializer`.
- 📱 **`ui/`** — Compose screens (`home`, `capture`, `chat`, `screenshot`, `relationships`, `score`, `settings`), reusable components (`TaskCard`, `UrgencyIndicator`, `DeadlineCountdownCard`, `ReflectionPromptDialog`), `NeverDropNavGraph`.
- ⚡ **`widget/`** — `QuickAddWidgetProvider` + transparent `QuickAddActivity` for under-3-second capture.

> 💉 DI is intentionally hand-rolled in `NeverDropApp` — singletons via `lazy` (`database`, `taskRepository`, `userPreferences`, `googleAuthManager`, `syncRepository`, `notificationResponseTracker`).

---

## ✨ Implemented features

### 🎯 Smart Capture (input layer)
- 🎙️ **Voice capture** — `CaptureScreen` + `SpeechRecognizerHelper`.
- 📸 **Screenshot capture** — pick an image, ML Kit OCRs it, `ScreenshotAnalyzer` extracts commitments, deadlines (today / tomorrow / EOD / EOW / weekday names), sender, and urgency from regex-matched action patterns. Each extracted commitment is a one-tap add.
- 💬 **Chat-style capture** — natural-language entry via `ChatCaptureScreen`.
- 📡 **Notification mining** — `NotificationMinerService` listens for system notifications and auto-creates tasks for delivery 📦 / appointment 📅 / bill 💳 / travel ✈️ / event 🎟️ categories (opt-in in Settings).
- 📧 **Gmail + 📆 Google Calendar sync** — actionable emails and upcoming events become tracked tasks. Periodic via `SyncWorker` (every 6h) when signed in.
- ⚡ **Quick Add home-screen widget** — `QuickAddWidgetProvider` launches a transparent `QuickAddActivity` for instant entry.

### 🧠 Intelligence Engine
- 📈 **`UrgencyCalculator`** — dynamic 0–100 score per task. Combines a commitment-type base weight, an exponential deadline curve (slow early, spikes near deadline, capped overdue boost), an accelerating snooze penalty after 3+ snoozes, an age weight, and a person-related boost.
- 🏆 **`FollowThroughScoreCalculator`** — daily/weekly % of commitments fulfilled on time + zero-drop streak day count. 🔥
- 🔍 **`ForgettingProfileAnalyzer`** — surfaces personal patterns: drop rate by capture hour, late-night capture warnings, worst commitment type, average snoozes-before-drop, person-vs-self drop rate skew, positive-streak insights.
- 👥 **`RelationshipTracker`** — per-person view of pending / completed / dropped tasks, days since last activity, and a 0–100 health score (💚 Strong → 💛 Good → 🟠 Needs attention → 🔴 At risk → ⚫ Critical).
- 😴 **Snooze intelligence** — snooze count is tracked on `Task`; `EscalationWorker` intervenes after 3+ snoozes.

### 🔔 Adaptive Notifications
- 🪜 **Multi-tier channels** via `NotificationChannelManager` — channel selected by urgency score.
- 🛡️ **`NotificationResponseTracker`** — anti-fatigue: suppresses non-critical pings during ignored hours.
- ⏰ **`DeadlineAlertWorker`** (every 30 min) — proactive deadline countdown alerts.
- 🚨 **`EscalationWorker`** (hourly) — re-notifies overdue or repeatedly-snoozed items with escalated copy.
- 🌅 **Morning briefing** / 🌙 **evening review** / 📊 **weekly report** — periodic workers, scheduled at user-configured times.
- 🎛️ **Notification action buttons** — handled by `NotificationActionReceiver` (complete ✅ / snooze 😴 / drop ❌ without opening the app).
- ☁️ **FCM** — `NeverDropFirebaseMessagingService` for server-pushed nudges.

### 🏆 Score & accountability UI
- 📇 `FollowThroughScoreCard`, `StreakCard`, `ForgettingInsightsCard` on the home/score surface.
- 🪞 `ReflectionPromptDialog` after hard task completion.

---

## 🔑 Permissions

Declared in `AndroidManifest.xml`:

- 🌐 `INTERNET` — Google APIs, Firebase
- 🎙️ `RECORD_AUDIO` — voice capture
- 🔔 `POST_NOTIFICATIONS` — Android 13+ notification permission
- 📡 `BIND_NOTIFICATION_LISTENER_SERVICE` (granted via system Settings) — notification mining

---

## 🚀 Building

**Prerequisites:**
- 🛠️ Android Studio (Iguana or newer recommended)
- ☕ JDK 17
- 📦 Android SDK 35
- 🔥 A `google-services.json` for Firebase (a placeholder is checked in at `app/google-services.json`)
- 🔐 Google OAuth client secret JSON in `app/secret/` if you want to test Gmail/Calendar sync locally — this directory is **gitignored**.

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

> 💡 Or open the project in Android Studio and run the `app` configuration on a device with API 26+.

---

## 🗺️ Roadmap (from the case study)

| Phase | Status | Highlights |
|---|---|---|
| **1️⃣ Foundation** | ✅ In this repo | Smart capture (voice/text/widget/screenshot), basic AI classification, Gmail + Calendar sync, morning/evening/weekly briefings, follow-through score, urgency model, snooze intelligence, relationship graph v1, forgetting profile v1 |
| **2️⃣ Intelligence** | 🔜 Planned | Deeper LLM-backed classification, energy-aware scheduling, notification adaptation v2, public Play Store beta |
| **3️⃣ Integration** | 🔜 Planned | WhatsApp/SMS scanning, Wear OS, accountability partner, Pro tier |
| **4️⃣ Scale** | 🔜 Planned | Team features, Slack/Teams, advanced analytics, iOS via KMP |

---

## 📜 License

⚠️ Currently unlicensed / proprietary — see the case study for product positioning. Add a `LICENSE` file before any external distribution.

---

> *"The best products don't come from people who never had the problem.*
> *They come from people who lived with it every day."* 💭
