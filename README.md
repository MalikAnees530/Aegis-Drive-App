# 🛡️ Aegis Drive: Unified Safety & Navigation on the Edge

[![Android CI](https://github.com/MalikAnees530/Aegis-Drive-App/actions/workflows/android.yml/badge.svg)](https://github.com/MalikAnees530/Aegis-Drive-App/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/about)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-blue.svg)](https://kotlinlang.org/)

**Aegis Drive** is a professional-grade, smartphone-based Edge AI application designed to revolutionize road safety. By integrating high-performance driver monitoring with robust offline navigation, Aegis Drive provides a zero-latency safety shield that works even in the remotest areas.

---

## 🚀 Key Features

- **🧠 Edge AI Drowsiness Monitoring**: Real-time detection using a custom Hybrid Bi-LSTM network and MediaPipe 3D spatial extraction.
- **🗺️ Offline Navigation**: Powered by MapLibre and OSRM, ensuring reliable routing without an internet connection.
- **⚡ Zero Latency**: All processing happens locally on the device (Edge AI), ensuring instant alerts.
- **🔒 Privacy First**: No video data leaves the device; all monitoring is performed offline.
- **🛠️ Professional Dashboard**: Monitor driver states, security logs, and session history in a clean, intuitive interface.

## 🛠️ Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM (Model-View-ViewModel)
- **AI/ML**: TensorFlow Lite, MediaPipe
- **Maps**: MapLibre SDK, OSRM (Open Source Routing Machine)
- **Database**: Firebase (Online Sync), Local Persistence (Offline)
- **UI/UX**: Material Design 3

## 📂 Architecture Overview

Aegis Drive follows a clean architecture pattern to ensure maintainability and scalability:

- **Presentation Layer**: Fragments (Monitor, Navigate, Chat, Home, Settings) and ViewModels.
- **Domain Layer**: Models (DriveSession, UserProfile, SecurityLog).
- **Data Layer**: Repositories (FirestoreRepository) and Local Data Sources.

## 🏁 Getting Started

### Prerequisites

- Android Studio Flamingo or newer
- JDK 17
- Android Device with Camera and Android 8.0 (API 26) or higher

### Installation

1. **Clone the repository**:
   ```bash
   git clone https://github.com/MalikAnees530/Aegis-Drive-App.git
   ```
2. **Open in Android Studio**:
   Import the project and let Gradle sync.
3. **Build & Run**:
   Select your device and click the "Run" button.

## 🤝 Contributing

We welcome contributions! Please see our [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on how to get started.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👥 Project Team

* **Malik Anees Ahmed** (Team Lead)
* **Mudassir Mukhtar**
* **M. Niaz**

**Supervised By:** Mam Farnaz Akbar  
**Institution:** National University of Modern Languages (NUML), Islamabad
