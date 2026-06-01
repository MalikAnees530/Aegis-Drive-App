# 🛡️ Aegis Drive: Unified Safety & Navigation on the Edge

[![Android CI](https://github.com/MalikAnees530/Aegis-Drive-App/actions/workflows/android.yml/badge.svg)](https://github.com/MalikAnees530/Aegis-Drive-App/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/about)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-blue.svg)](https://kotlinlang.org/)
[![Security Policy](https://img.shields.io/badge/Security-Policy-brightgreen.svg)](SECURITY.md)
[![PRs Welcome](https://img.shields.io/badge/PRs-Welcome-brightgreen.svg)](CONTRIBUTING.md)

**Aegis Drive** is a professional-grade, smartphone-based Edge AI application designed to revolutionize road safety. By integrating high-performance driver monitoring with robust offline navigation, Aegis Drive provides a zero-latency safety shield.

---

## 🏛️ Executive Dissertation
Aegis Drive is engineered as a zero-latency safety ecosystem, adhering to the **Material Design 3 'Navy Bento'** visual philosophy. Unlike traditional cloud-dependent systems, Aegis Drive operates with **Strictly Localized Edge AI**, ensuring sub-100ms response times while maintaining absolute user privacy.

---

## 🔒 Security & Credential Hardening
Aegis Drive implements a multi-tier security architecture to ensure sensitive API keys and configuration data are never exposed:
*   **Zero-Tracking Policy**: Sensitive files (`local.properties`, `google-services.json`) and temporary build artifacts (`.gradle/`, `.idea/caches/`) are strictly excluded from version control.
*   **Compile-Time Injection**: Credentials (such as the Groq API key) are injected via the `BuildConfig` class at build time. This allows the codebase to remain free of hardcoded strings.
*   **Repository Integrity**: The project has undergone a full Git history scrub to remove all legacy traces of configuration files, ensuring a clean and secure public state.

---

## 🧠 Core AI Architecture

### 💬 Aegis AI: Intelligent Safety Assistant
The app features an integrated AI Chatbot powered by the **Llama-3.3-70b-versatile** model via the **Groq API**:
*   **Retrofit + Repository Pattern**: Implemented using a professional networking stack for robust error handling and clean code separation.
*   **Context-Aware Safety**: The AI receives real-time safety telemetry (Live Safety Score) to provide personalized, high-precision safety advice.
*   **Multimodal Interaction**: Supports hands-free operation using **Speech-to-Text (STT)** and **Text-to-Speech (TTS)** engines.

### ⚡ Zero-Copy Vision Pipeline
Utilizes **CameraX** with zero-copy buffers passed directly to **Google's MediaPipe 3D Spatial Extraction Engine**, eliminating memory overhead and minimizing latency.

---

## 🚀 Getting Started
To build the project locally:
1.  **Create local.properties**: In the root directory, create a file named `local.properties`.
2.  **Configure API Key**: Add your Groq API key:
    `GROK_API_KEY=gsk_your_key_here`
3.  **Sync Gradle**: Click "Sync Project with Gradle Files" in Android Studio to generate the `BuildConfig` required for AI features.

---

## 🛠️ Tech Stack
*   **Language**: Kotlin (JDK 11/17)
*   **Architecture**: MVVM + Repository Pattern
*   **AI/ML**: TensorFlow Lite, MediaPipe Vision Tasks
*   **Networking**: Retrofit 2.9.0 + Gson
*   **Maps**: MapLibre SDK, OSRM

---

## 👥 Project Team
* **Malik Anees Ahmed** (Team Lead)
* **Mudassir Mukhtar**
* **M. Niaz**

**Supervised By:** Mam Farnaz Akbar  
**Institution:** National University of Modern Languages (NUML), Islamabad
