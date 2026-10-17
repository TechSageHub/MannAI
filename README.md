# MannAI

MannAI is a personal Android AI assistant being developed under the **TechSageHub** brand. It is an actively developed personal project — not a finished commercial product.

The goal is to explore what a genuinely useful personal AI assistant could look like when it is designed around one person's daily life rather than as a generic chatbot. MannAI emphasizes privacy, user approval, local data storage, and gradual development.

## Why MannAI Exists

MannAI is being built to explore what everyday assistance could look like when it is built around a single person's workflows: managing tasks and reminders, organizing information, assisting with conversations, and eventually providing a more natural voice-driven experience.

A major motivation is to reduce repetitive manual work without taking control away from the person using the assistant. In particular, communication assistance should help the user understand a message, prepare a suitable response, and review it before deciding whether to send it. Important actions — especially sending messages — require explicit user approval.

The project also explores how much of this experience can be implemented directly on Android, with local data storage and minimal infrastructure, instead of requiring a complicated backend or expensive services for every feature.

Privacy, user approval, practicality, and gradual development are important principles. The long-term goal is not simply to add AI everywhere, but to build an assistant that is useful, understandable, customizable, and trustworthy.

## Core Vision and Design Principles

- **Personal-first:** designed around the user's own workflows.
- **User-controlled:** important actions, especially sending messages, require explicit approval. Nothing is sent automatically.
- **Privacy-conscious:** keep personal data local wherever practical; disclose when external AI services may receive data.
- **Incremental development:** build and test features in clearly defined phases.
- **Practical usefulness:** prioritize features that solve real everyday problems.
- **Extensible architecture:** preserve a maintainable Android codebase that can grow over time.

## Current Features

The following features are implemented in the current codebase:

| Feature | Status | Description |
|---|---|---|
| Task management | Working | Create, edit, complete, and delete tasks with optional due dates, priority, and reminders. |
| Notes | Working | Create, search, edit, and delete notes stored locally in Room. |
| Reminders | Working | Schedule reminders and task due-date alerts via `AlarmManager`; persists across reboot via `BootReceiver`. |
| In-app assistant chat | Working | Conversation history screen with offline command parsing (`ChatMessageEntity`, `ChatRepository`). |
| Voice assistant | Working | Wake-phrase detection ("Hey <name>") and speech input via a foreground microphone service (`AssistantService`). |
| Text-to-speech | Working | Spoken responses through `TtsSpeaker`. |
| Offline command handling | Working | `CommandParser`/`CommandExecutor` handle tasks, notes, reminders without a network. |
| Reply drafting & editing | Working | Message Reply Assistant screen with offline tone templates and manual "Share" (no auto-send). |
| AI provider configuration | Working | OpenAI-compatible endpoint + API key + model configurable in Settings; stored in private SharedPreferences. |
| WhatsApp notification listening | Implemented (unverified on device) | `WhatsAppNotificationListener` reads WhatsApp notifications after the user grants Android Notification Access. |
| WhatsApp notification parsing | Implemented (unverified on device) | `WhatsAppNotificationParser` extracts sender/message defensively, filtered to `com.whatsapp`. |
| Selected-sender allowlisting | Implemented (unverified on device) | User-maintained allowlist of sender names; unlisted or unidentified senders are ignored. |
| WhatsApp chat-history import | Implemented (unverified on device) | SAF document picker for `.txt` exports, parser for multiple date/time formats, preview, sender attribution, local Room storage, delete support. |
| Settings & customization | Working | Assistant name, wake phrase, spoken responses, notifications, AI config, battery/autostart guidance. |
| Background operation | Working | `BootReceiver` reschedules reminders after reboot/clock changes; foreground service for voice listening. |

### WhatsApp Communication Assistance (Intended Workflow)

The WhatsApp features are being built in phases. The intended workflow is:

1. **Detect** supported incoming WhatsApp notifications after the user grants Android Notification Access.
2. **Filter** incoming messages using the user's selected sender allowlist.
3. (Planned) Use explicitly imported conversation history, where supported, to help understand context and communication style.
4. (Planned) Generate a conversational reply draft using a suitable AI provider.
5. Let the user **review, edit, approve, and then send** the reply using supported Android notification actions where available.

**Important:** The complete workflow is not yet implemented. WhatsApp notification listening and chat-history import are implemented as code but have **not been verified on a physical device**. No replies are generated or sent automatically in the current build.

Notification-based access and reply capabilities have Android and WhatsApp limitations. MannAI does not read every WhatsApp conversation, does not access all historical messages unless explicitly imported, and does not send replies in every situation.

## Technology Stack and Architecture

MannAI is a native Android application written in Kotlin.

**Verified technologies:**
- Kotlin (JVM 11)
- Jetpack Compose (Material 3) for UI
- Room persistence library (v2.8.4) with KSP
- Kotlin Coroutines and Flow
- Android Notification and foreground-service APIs
- `AlarmManager`, `NotificationListenerService`, `BroadcastReceiver`
- Storage Access Framework (document picker for chat imports)
- JUnit for unit tests; Compose UI Test + Espresso for instrumented tests
- Gradle (Kotlin DSL), Android SDK (compileSdk 37, minSdk 26, targetSdk 37)

**Main architectural components:**

| Package | Responsibility |
|---|---|
| `com.techsagehub.mannai` | Application class (`MannAIApp`) and main activity (`MainActivity`) with navigation |
| `com.techsagehub.mannai.data` | Repositories (`TaskRepository`, `NoteRepository`, `ReminderRepository`, `ChatRepository`, `WhatsAppImportRepository`) and `SettingsStore` |
| `com.techsagehub.mannai.data.db` | Room database (`MannAIDatabase`), entities, and DAOs |
| `com.techsagehub.mannai.service` | `AssistantService` (voice/wake-phrase) and `WhatsAppNotificationListener` |
| `com.techsagehub.mannai.notify` | Notification channels, `ReminderScheduler`, `ReminderReceiver`, `BootReceiver` |
| `com.techsagehub.mannai.ui` | Compose screens and ViewModels (`TasksScreen`, `ChatScreen`, `ReplyScreen`, `SettingsScreen`, `WhatsAppImportScreen`, etc.) |
| `com.techsagehub.mannai.assistant` | `CommandParser`, `CommandExecutor`, `ReplyDrafter`, `AiProvider`, `WhatsAppChatParser`, `WhatsAppNotificationParser` |
| `com.techsagehub.mannai.voice` | `SpeechInput` and `TtsSpeaker` |

**Database:** Room version 2 with additive migration from version 1 (`whatsapp_contacts` and `whatsapp_messages` tables). The existing `chat_messages` table for the in-app assistant is kept separate from imported WhatsApp history.

## Development Status

**Completed and unit-tested:**
- Task, note, reminder CRUD and scheduling
- In-app assistant chat with offline commands
- Voice wake-phrase service and TTS
- Reply drafting (offline templates) with manual share
- AI provider configuration (OpenAI-compatible)
- WhatsApp notification listener, parser, and selected-sender allowlist
- WhatsApp chat-history import parser, UI, preview, attribution, and local storage

**Implemented but not verified on a physical device:**
- WhatsApp notification listening (requires Android Notification Access granted manually)
- WhatsApp chat-history import via SAF document picker

**Planned (not yet implemented):**
- Complete and test WhatsApp chat-history import on a real device
- Integrate Gemini or another suitable AI provider for contextual reply generation
- Improve context selection and personalized writing style ("sound like me")
- Implement approval-based WhatsApp reply actions using Android RemoteInput where supported
- Test notification access, reminder delivery, and voice features on the target Android device
- Improve privacy, error handling, and secure API-key storage
- Continue improving the assistant's conversational and voice experiences

## Privacy and Security

**Local processing:**
- Tasks, notes, reminders, assistant chat history, settings, and imported WhatsApp history are stored locally in the Room database (`mannai.db`) and private SharedPreferences.
- No conversation content is logged or uploaded by the app.
- WhatsApp notification contents are parsed in memory and not persisted to disk in Phase 1; imported chat history is stored locally only when the user explicitly commits an import.
- No replies are generated or sent automatically.

**External AI requests:**
- MannAI includes an optional OpenAI-compatible AI provider. It is completely inert unless the user configures an endpoint, API key, and model in Settings.
- When configured, only the text the user explicitly sends (for chat or reply refinement) is transmitted to the provider. Imported WhatsApp history is **not** sent to any AI provider during import.
- API keys are stored in ordinary private SharedPreferences (`mannai_settings`) — **not encrypted**. Secure API-key storage is a planned improvement.

**Android Notification Access:**
- WhatsApp notification listening requires the user to manually grant Notification Access in Android Settings (Settings → Apps → Special app access → Notification access → MannAI). This is a sensitive permission; grant it only with an understanding of what it permits.
- MannAI filters notifications to the `com.whatsapp` package and applies a user-maintained sender allowlist, but the Android permission itself grants broad notification access at the OS level.

**No secrets in source:** No API keys, tokens, or credentials are stored in source control.

## Requirements and Setup

**Requirements:**
- JDK 17 (Android Gradle Plugin / Kotlin compilation targets Java 11)
- Android SDK (compileSdk 37, minSdk 26, targetSdk 37)
- Windows PowerShell or Command Prompt (commands below use PowerShell)

**Build and test from the command line:**

```powershell
git clone https://github.com/TechSageHub/MannAI.git
cd MannAI
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

The debug APK is generated at:

```
app/build/outputs/apk/debug/app-debug.apk
```

**Permission and setup notes:**
- Voice features require the `RECORD_AUDIO` permission (granted at runtime).
- Reminder notifications require `POST_NOTIFICATIONS` (runtime on Android 13+).
- WhatsApp notification listening requires manually granting Notification Access in Android Settings — it cannot be requested programmatically from the app.
- On some devices (e.g. Infinix/XOS), battery optimization and autostart settings may affect background service and reminder reliability.

The project is buildable from the command line; Android Studio is optional.

## Current Limitations

- Android Notification Access must be explicitly granted by the user in system Settings; the app cannot grant it programmatically.
- WhatsApp notification contents vary depending on WhatsApp notification settings and message type; parsing is defensive and fail-closed.
- Remote reply actions are only available when supported by the WhatsApp notification.
- WhatsApp notification listening and chat-history import have not been verified on a physical device.
- AI-generated replies require a configured and reachable provider; offline fallback drafting is available.
- Voice, wake-phrase, background-service, and reminder behavior still need physical-device verification on the target device.
- MannAI is not a replacement for the official WhatsApp application.
- API keys are stored in plaintext SharedPreferences; encryption is a planned improvement.

## Roadmap

- Complete and test WhatsApp chat-history import on a real device.
- Integrate Gemini or another suitable AI provider for contextual reply generation.
- Improve context selection and personalized writing style.
- Implement approval-based WhatsApp reply actions using Android RemoteInput where supported.
- Test notification access, reminder delivery, and voice features on the target Android device.
- Improve privacy, error handling, and secure API-key storage.

## Development

This is a personal, incrementally developed project. Features are built in phases with unit tests for pure logic (e.g. `WhatsAppChatParserTest`, `ReplyDrafterTest`) and instrumented tests for device integration (e.g. `DeviceIntegrationTest`, `ServiceLifecycleTest`).

No license has been applied to this repository. Do not assume redistribution rights beyond those granted by the repository host's terms.
