# CoolTaco: Automated Co-Regulation System for ADHD Families 🌮⌚

![Kotlin](https://img.shields.io/badge/Kotlin-100%25-blue?logo=kotlin)
![Android](https://img.shields.io/badge/Android-Jetpack%20Compose-3DDC84?logo=android)
![Wear OS](https://img.shields.io/badge/Wear%20OS-Supported-black?logo=wearos)
![Firebase](https://img.shields.io/badge/Firebase-Realtime%20Sync-FFCA28?logo=firebase)

## 📌 Project Overview
This project is a technical extension of the **CoolTaco (CHI '23)** research, which focuses on a co-regulation system for families with ADHD children. The primary innovation in this version is the transformation of the manual task verification process into an **Automated Sensor-Driven Verification System**.

The system leverages the **Android Health Connect API** and **WorkManager** to track the child's physical activities in the background. This architecture completely eliminates the manual verification burden for parents and ensures children receive immediate positive reinforcement.

## ✨ Key Features & Technical Innovation
* 🤖 **Automated Verification:** Utilizes physical sensor data to automatically verify chores and activities without requiring parental intervention.
* ⚡ **Real-Time Synchronization:** Achieves ultra-low latency updates (**< 500ms**) between the child's smartwatch and the parent's phone using Firebase Cloud Firestore Snapshot Listeners.
* 🔋 **Power Efficiency:** Highly optimized using Android WorkManager for battery-friendly background execution, maintaining an idle app CPU load of ~0%.
* 🧠 **Pre-Reward Reflection:** Integrates a psychological scaffolding mechanism that prompts children to self-evaluate their effort before claiming their earned points.

## 📸 System Interfaces

| Parent Dashboard (Mobile) | Child Interface (Wear OS) |
| :---: | :---: |
| <img src="phone.png" width="280"> | <img src="watch.png" width="280"> |
| *Automated verification tracking and progress monitoring on the parent's device.* | *Pre-reward reflection dialog and instant point awarding on the smartwatch.* |

## 🛠️ Tech Stack & Architecture
* **Programming Language:** Kotlin (1,600+ Lines of Code)
* **UI Framework:** Jetpack Compose & Compose for Wear OS
* **Database & Sync:** Firebase Cloud Firestore
* **Background Processing:** Android WorkManager API
* **Sensor Integration:** Android Health Connect API

## 📈 Performance Metrics (Emulator Validated)
* **Resource Efficiency:** Maintained **0% CPU usage (App load)** and stable memory allocation during continuous background monitoring.
* **Data Latency:** Confirmed real-time state synchronization across devices in under **500 milliseconds**.

## 🤝 Research Commitment & Dedication
This prototype was engineered entirely from scratch as an independent technical initiative to support ongoing research in HCI and accessible technology for ADHD:
* **Development Investment:** Dedicated ** 60+ hours** of independent, intensive engineering.
* **Future Availability:** Prepared to commit 15-20 hours/week as a Research Assistant to drive clinical trials, data integration, and upcoming lab publications.

## 🚀 Future Roadmap
- [ ] **Web-Based Family Dashboard:** Developing a scalable Laravel/PHP dashboard for large-screen "Joint Reflection" and long-term behavioral tracking.
- [ ] **Advanced Behavioral Analytics:** Translating smartwatch data into actionable heatmaps and task-initiation patterns for parents.
- [ ] **Personalized Micro-Scaffolding:** Implementing an interactive UI to break down complex household chores into manageable, step-by-step tasks.

---
*Developed by Nida Kamilia - Informatics Engineering, Universitas Darussalam Gontor.*
