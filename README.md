# 🛡️ Aegis Drive: Unified Safety & Navigation on the Edge

[![Android CI](https://github.com/MalikAnees530/Aegis-Drive-App/actions/workflows/android.yml/badge.svg)](https://github.com/MalikAnees530/Aegis-Drive-App/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/about)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-blue.svg)](https://kotlinlang.org/)
[![Security Policy](https://img.shields.io/badge/Security-Policy-brightgreen.svg)](SECURITY.md)
[![PRs Welcome](https://img.shields.io/badge/PRs-Welcome-brightgreen.svg)](CONTRIBUTING.md)

**Aegis Drive** is a professional-grade, smartphone-based Edge AI application designed to revolutionize road safety. By integrating high-performance driver monitoring with robust offline navigation, Aegis Drive provides a zero-latency safety shield that works even in the remotest areas.

---

## 🏛️ Executive Dissertation

Aegis Drive is engineered as a zero-latency safety ecosystem, adhering to the **Material Design 3 'Navy Bento'** visual philosophy. This aesthetic choice provides a high-density, card-based information display that minimizes driver distraction while maximizing data clarity. 

Unlike traditional cloud-dependent safety systems, Aegis Drive operates with **Strictly Localized Zero-Latency Edge AI**. All biometric and spatial processing occurs on-device, ensuring sub-100ms response times for critical alerts while maintaining absolute user privacy. The system is designed for **Pure Offline Operation**, functioning without any internet connectivity for its core safety monitoring and navigation features.

---

## 🧠 Exhaustive Core Features & Embedded Mathematics

### ⚡ Zero-Copy Vision Pipeline
The monitoring framework utilizes an optimized pipeline where **CameraX** captures real-time frames using the `STRATEGY_KEEP_ONLY_LATEST` backpressure strategy. These frames are passed as zero-copy buffers directly to **Google's MediaPipe 3D Spatial Extraction Engine**, eliminating memory overhead and minimizing latency.

### 📍 Precise Landmark Arrays
The system extracts specific coordinate indices from the **478-point 3D face mesh** to compute driver state:
*   **Right Eye**: `[33, 160, 158, 133, 153, 144]`
*   **Left Eye**: `[362, 385, 387, 263, 373, 380]`
*   **Lips (Mouth)**: `[78, 308, 13, 14]`

### 📐 Perspective Posture Geometry (Defense Foundation)
To successfully eliminate false positives caused by natural driving movements (e.g., checking mirrors), the system implements a precise **Head Posture Geometry** algorithm:
1.  **Pitch Computation**: Measures the vertical ratio by comparing the distance from the top of the head to the nose (`upperFace`) against the distance from the nose to the chin (`lowerFace`). 
    *   `pitchRatio = lowerFace / upperFace`
2.  **Yaw Computation**: Measures horizontal rotation by calculating the Euclidean distance between the nose and the inner corners of both eyes, then deriving the ratio between the maximum and minimum distances.
    *   `yawRatio = max(leftDist, rightDist) / min(leftDist, rightDist)`
3.  **Extreme Angle Deadzone Override**: A mathematical "freeze" is applied when `yawRatio > 2.0f`, `pitchRatio > 1.6f`, or `pitchRatio < 0.6f`. This logic suspends drowsiness counters when the driver is at extreme viewing angles, effectively preventing false alerts during side-mirror checks or shoulder checks.

### 📉 Dynamic EAR Thresholding
The system employs an aggressive multi-tiered threshold drop logic mapped to posture severity:
*   **Extreme Pitch (>1.35f)**: Threshold drops to **0.06f** (Strict deadzone).
*   **Moderate Pitch (>1.20f)**: Threshold drops to **0.09f**.
*   **Extreme Yaw (>1.6f)**: Threshold drops to **0.06f**.
*   **Moderate Yaw (>1.3f)**: Threshold drops to **0.10f**.
*   **Yawning Active (MAR > 0.40f)**: Threshold drops to **0.12f**.
*   **Standard Alertness**: Baseline threshold of **0.16f**.

### 🔗 Temporal Bi-LSTM Inference
The continuous eye and mouth metrics are packaged into an `ArrayDeque` of exactly **30 frames**. This temporal sequence is fed into a localized `aegis_drive_model.tflite` interpreter for classification. To eliminate UI classification stuttering, a smoothing logic is applied over a **5-frame prediction history buffer**:
*   `smoothedClassIdx = predictionHistory.groupBy { it }.maxByOrNull { it.value.size }`
This ensures the classification remains stable even during rapid frame transitions.

---

## 🔊 Sensory Intelligence & Hardware Alerting

The system implements a high-priority hardware feedback loop for instant driver notification. 
*   **Audio & Vibration**: Both the `MediaPlayer` and `Vibrator` engines explicitly inject `AudioAttributes.USAGE_ALARM`. This forces the Android OS to treat safety alerts as critical alarms, bypassing standard volume dampening or "Do Not Disturb" restrictions.
*   **State Recovery**: The UI implements **Instant Recovery Logic**, shifting the safety state back to 'Open' immediately upon detection of a single recovered frame (`openEyeFrames >= 1`), ensuring the driver is not distracted by persistent alerts after they have regained alertness.

---

## 🗺️ Offline Vector Navigation Architecture

Aegis Drive features a completely autonomous navigation stack:
*   **Local Vector Tiles**: MapLibre reads local vector packages directly from the device storage (e.g., `islamabad_local.mbtiles` in assets), ensuring high-fidelity map rendering without data usage.
*   **Offline Pathfinding**: Powered by a local **Open Source Routing Machine (OSRM)** implementation. The system computes pathfinding mathematics locally, drawing highly visible polyline overlays that are completely decoupled from internet dependencies.

---

## ☁️ Atomic Cloud Telemetry Sync

Upon session termination, the system executes an atomic synchronization logic:
1.  **Batch Writing**: Flat session maps (Duration, Score, Alert Count, Focus Level) are batch-written to the `DriveSessions` collection.
2.  **Atomic Increments**: Using `SetOptions.merge()`, the system atomically increments the user's aggregated lifetime statistics (Total Drives, Total Duration, Lifetime Score Sum) in the parent user profile document. This ensures data consistency even in poor network conditions.

---

## 🛠️ Tech Stack

*   **Language**: Kotlin (JDK 17)
*   **Architecture**: MVVM + Clean Architecture
*   **AI/ML**: TensorFlow Lite (LiteRT), MediaPipe Vision Tasks
*   **Maps**: MapLibre SDK, OSRM, TomTom Search
*   **Database**: Firebase Firestore (Sync), Local Persistence
*   **UI/UX**: Material Design 3 (Navy Bento Aesthetic)

---

## 👥 Project Team

* **Malik Anees Ahmed** (Team Lead)
* **Mudassir Mukhtar**
* **M. Niaz**

**Supervised By:** Mam Farnaz Akbar  
**Institution:** National University of Modern Languages (NUML), Islamabad

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
