# Aegis Drive - Project Overview

Aegis Drive is an Android application designed to enhance driver safety through real-time monitoring and AI-driven drowsiness detection. It uses the device's front camera and a TensorFlow Lite model to analyze driver behavior and issue alerts when signs of fatigue are detected.

## Tech Stack

- **Platform:** Android (Min SDK 24, Target SDK 35)
- **Language:** Kotlin
- **Build System:** Gradle (Kotlin DSL)
- **UI Framework:** Material Design, XML Layouts
- **Navigation:** Android Navigation Component
- **Camera:** CameraX (Core, Camera2, Lifecycle, View)
- **AI/ML:** TensorFlow Lite (Interpreter, Support library)

## Core Architecture

### Activities
- `LoginActivity`: The entry point for users.
- `SignUpActivity`: Handles new user registration.
- `MainActivity`: The main dashboard containing a `BottomNavigationView` for navigating between fragments.
- `SettingsActivity`: Allows users to configure app preferences.

### Fragments
- `MonitorFragment`: The heart of the application. It manages the CameraX preview and `ImageAnalysis` pipeline. It runs real-time inference using a TFLite model to detect "Eyes Closed", "Normal", or "Yawning" states.
- `HomeFragment`: Likely provides a summary or dashboard view.
- `ChatFragment` & `NavigateFragment`: Additional features for user interaction and navigation.

### AI Engine
The app uses a custom TensorFlow Lite model (`aegis_drive_model.tflite`) located in `app/src/main/assets`. The monitoring logic includes:
- **Consecutive Frame Tracking:** To reduce false positives, alerts are triggered only after a threshold of consecutive "danger" or "warning" frames.
- **Safety Score:** A dynamic score (0-100%) that fluctuates based on detected driver behavior.
- **Audible Alerts:** Uses `RingtoneManager` to play alarms or notifications when high-risk states are detected.

## Building and Running

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 11 (as specified in `build.gradle.kts`)
- An Android device or emulator with a front-facing camera.

### Key Commands
- **Assemble Debug APK:** `./gradlew assembleDebug`
- **Install on Device:** `./gradlew installDebug`
- **Run Unit Tests:** `./gradlew test`
- **Run Instrumented Tests:** `./gradlew connectedAndroidTest`

## Development Conventions

- **State Management:** The `MonitorFragment` uses an `isMonitoring` flag to strictly control when AI analysis is active.
- **Resource Management:** Camera and TFLite resources are carefully bound to the fragment lifecycle (`onResume`, `onPause`, `onDestroyView`).
- **UI Feedback:** Uses Material Design components (Cards, ProgressBars, Buttons) with dynamic color tinting to provide immediate visual safety feedback.
- **Camera Optimization:** Uses a dual-stream architecture: HD for preview and a lower-resolution (480x640) stream for AI analysis to maintain high FPS.
