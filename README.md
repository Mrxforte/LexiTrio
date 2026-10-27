# LexiTrio — Trilingual Learning & Dictionary Ecosystem

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-API_26+-green.svg)](https://developer.android.com/)
[![Jetpack Compose](https://img.shields.io/badge/Compose-Material_3-blue.svg)](https://developer.android.com/jetpack/compose)
[![Python](https://img.shields.io/badge/Python-3.11%20%7C%203.12-yellow.svg)](https://www.python.org/)
[![Django](https://img.shields.io/badge/Django-5.0+-092E20.svg)](https://www.djangoproject.com/)
[![Database](https://img.shields.io/badge/SQLite_FTS5-PostgreSQL_16-blue.svg)](https://www.postgresql.org/)

**LexiTrio** is a production-grade, offline-first mobile application and backend ecosystem designed for trilingual learning across **English (EN)**, **Russian (RU)**, and **Uzbek (UZ)**. It combines an offline neural Text-To-Speech (TTS) engine, a full-text search (FTS) trilingual dictionary with over 130,000 entries, and a comprehensive CEFR-aligned grammar curriculum with interactive exercises and progress tracking.

---

## 🌐 Language Summaries / Языковые сводки / Tillar bo'yicha qisqacha ma'lumot

<details>
<summary><b>🇬🇧 English Overview</b></summary>

LexiTrio is an offline-first learning system. The Android app uses Room SQLite with FTS5 for instant dictionary lookups and offline grammar practice, while the Django REST backend provides cloud synchronization, automated dictionary translation pipelines, and PostgreSQL full-text search.
</details>

<details>
<summary><b>🇷🇺 Описание на русском языке</b></summary>

LexiTrio — это экосистема для трехъязычного изучения английского, русского и узбекского языков. Приложение работает полностью оффлайн благодаря встроенной базе данных Room SQLite с полнотекстовым поиском FTS5 и нейросетевому синтезу речи (Sherpa-ONNX VITS). Серверная часть на Django REST API обеспечивает синхронизацию прогресса, управление контентом и поиск на PostgreSQL.
</details>

<details>
<summary><b>🇺🇿 O'zbek tilida tavsif</b></summary>

LexiTrio — bu ingliz, rus va o'zbek tillarini uch tilda kompleks o'rganish uchun mo'ljallangan mobil ilova va backend tizimi. Android ilovasi Room SQLite FTS5 va Sherpa-ONNX VITS neyron offline ovoz berish tizimi orqali internet bo'lmaganda ham to'liq ishlaydi. Django REST API backend esa ma'lumotlarni bulut bilan sinxronlash va lug'at qidiruvini ta'minlaydi.
</details>

---

## 🛠️ Technology Stack & Versions

### **Android Application**
* **Language & Runtime:** Kotlin `2.0.21`, JDK 17
* **UI Toolkit:** Jetpack Compose with Material 3 Design System
* **Architecture:** Clean Architecture + MVVM / MVI (ViewModel, StateFlow, Coroutines)
* **Local Persistence:** Room Database `2.6.1` with SQLite FTS5 full-text indexing
* **TTS Engines (Dual Architecture):**
  * **Sherpa-ONNX Engine:** Offline neural speech synthesis via VITS models (Piper ONNX)
  * **Android System TTS Engine:** Hardware/OS native speech engine fallback
* **Networking & Serialization:** Retrofit 2, OkHttp 4, KotlinX Serialization
* **Dependency Injection & Async:** Kotlin Coroutines & Flow

### **Django REST Backend**
* **Language & Framework:** Python `3.11` / `3.12`, Django `5.0+`, Django REST Framework
* **Databases:**
  * **Production:** PostgreSQL 16 with `pg_trgm` and `tsvector` full-text search
  * **Local Development:** SQLite with FTS5 extension
* **Task Queue & Caching:** Redis 7, Celery 5.x
* **WSGI & Reverse Proxy:** Gunicorn, Nginx
* **Deployment & Containerization:** Docker, Docker Compose, Linux systemd

---

## 📂 Project Architecture & Folder Structure

```
LexiTrio/
├── app/                                  # Android Application Module
│   ├── src/main/java/com/example/
│   │   ├── data/                         # Data Layer (Database, DAOs, Repositories, Cloud & TTS)
│   │   │   ├── cloud/                    # Retrofit Cloud API interfaces & Factory
│   │   │   ├── dao/                      # Room DAOs (CurriculumDao, DictionaryDao)
│   │   │   ├── database/                 # Room Database, Grammar Registry, Validators & Seed Data
│   │   │   │   ├── CurriculumCoverageReportGenerator.kt
│   │   │   │   ├── CurriculumValidator.kt
│   │   │   │   ├── GrammarCurriculumRegistry.kt   # 293 Topic Modules Generator
│   │   │   │   ├── LexiTrioDatabase.kt           # Room Database Definition & FTS Setup
│   │   │   │   └── PreloadCurriculumData.kt       # Pre-loaded database assets
│   │   │   ├── model/                    # Data Entities (Curriculum, Dictionary & Search Models)
│   │   │   ├── preferences/              # DataStore User Preferences Repository
│   │   │   ├── repository/               # Curriculum & Dictionary Repositories
│   │   │   └── tts/                      # Dual Text-To-Speech Engine Implementation
│   │   │       ├── AndroidSystemTtsEngine.kt # Fallback System TTS
│   │   │       ├── SherpaOnnxTtsEngine.kt    # Neural Offline VITS Engine
│   │   │       ├── TtsAudioCacheManager.kt   # Disk Audio WAV Caching
│   │   │       ├── TtsManager.kt             # TTS Manager Facade
│   │   │       └── VoiceModelManager.kt      # Neural Voice Download & Storage Manager
│   │   ├── domain/                       # Domain Layer (Interfaces, Models, Use Cases)
│   │   │   ├── model/                    # Domain Models & Search Wrappers
│   │   │   ├── repository/               # Repository Contracts
│   │   │   └── usecase/                  # Use Cases (SpeakDictionaryWordUseCase, StopSpeechUseCase)
│   │   ├── presentation/                 # Presentation Layer
│   │   │   └── AppViewModel.kt           # Main Application State & Business Logic Handler
│   │   └── ui/                           # UI Layer (Jetpack Compose)
│   │       ├── components/               # Reusable Composables (HighlightedText, Exercise Cards)
│   │       ├── navigation/               # NavHost Routes & Navigation Graphs
│   │       └── screens/                  # Feature Screens
│   │           ├── curriculum/           # Curriculum, Lesson Detail, Exercise & Dashboard Screens
│   │           ├── dictionary/           # Dictionary Search & Word Detail Screens
│   │           ├── home/                 # Main Dashboard Screen
│   │           └── settings/             # Voice Engine & App Settings Screens
│   └── src/test/java/com/example/        # Unit & Integration Tests (TTS, Search, Curriculum)
│
├── backend/                              # Django REST API Backend
│   ├── config/                           # Django Settings, URLs, Celery & WSGI Configuration
│   ├── learning/                         # Dictionary, Search, Curriculum, Sync & FTS App
│   │   ├── management/commands/          # CLI Commands (import_curriculum, rebuild_search_index, etc.)
│   │   ├── migrations/                   # PostgreSQL Migration Files
│   │   ├── models.py                     # Database Schema (Dictionary, Grammar, Progress)
│   │   ├── search.py                     # High-Performance FTS Search Services
│   │   └── views.py                      # REST Endpoints
│   ├── translation/                      # Dictionary Translation Pipeline & External Providers
│   └── load_testing/                     # Locust Performance Testing Scripts
│
├── nginx/                                # Production Nginx Reverse Proxy Config
├── scripts/                              # Database Backup & Restoration Utilities
├── docs/                                 # Architecture & Benchmark Documentation
├── docker-compose.yml                    # Multi-container Docker Setup (Web, DB, Redis, Celery)
└── DEPLOYMENT.md                         # Production Deployment Guide
```

---

## 📚 Grammar Curriculum & Practice Exercises Statistics

LexiTrio features a **100% complete CEFR-aligned grammar curriculum** supporting **English** and **Russian** core mastery courses, fully translated into **Uzbek**.

### **Curriculum Overview**

| Metric | English Core (`EN_CORE`) | Russian Core (`RU_CORE`) | Total Curriculum |
| :--- | :---: | :---: | :---: |
| **CEFR Levels Supported** | A1, A2, B1, B2, C1, C2 | A1, A2, B1, B2, C1, C2 | **A1 — C2** |
| **Grammar Topics** | **133 Topics** | **160 Topics** | **293 Topics** |
| **Practice Exercises (15 / topic)** | **1,995 Exercises** | **2,400 Exercises** | **4,395 Exercises** |
| **Test Bank Questions (5 / topic)** | **665 Questions** | **800 Questions** | **1,465 Questions** |
| **Grammar Summary Tables** | 133 Tables | 160 Tables | **293 Summary Tables** |
| **Instructional Languages** | EN, RU, UZ | EN, RU, UZ | **Trilingual** |

### **Level Breakdown**
* **English Core (`EN_CORE`):**
  * `A1 Beginner`: 23 topics
  * `A2 Elementary`: 31 topics
  * `B1 Intermediate`: 29 topics
  * `B2 Upper Intermediate`: 25 topics
  * `C1 Advanced`: 25 topics
* **Russian Core (`RU_CORE`):**
  * `A1 Beginner`: 35 topics
  * `A2 Elementary`: 35 topics
  * `B1 Intermediate`: 35 topics
  * `B2 Upper Intermediate`: 30 topics
  * `C1 Advanced`: 25 topics

### **Exercise Types**
Every topic includes 15 interactive practice exercises covering 5 distinct activity types:
1. `MULTIPLE_CHOICE`: Select the grammatically correct option.
2. `FILL_IN_BLANK`: Type or choose the missing word/form.
3. `SENTENCE_ORDERING`: Arrange jumbled words into correct word order.
4. `TRANSLATION`: Translate sentences between EN, RU, and UZ.
5. `ERROR_CORRECTION`: Identify and correct grammatical errors in context.

---

## 🔍 Trilingual Dictionary & Search Engine

### **Dictionary Database Stats**
* **Total Word Entries:** **130,000+ entries** across English, Russian, and Uzbek.
* **Phonetics:** Full IPA (International Phonetic Alphabet) support for English and Russian headwords.
* **Metadata:** CEFR level tags (A1-C2), part of speech, frequency ranking, plural forms, irregular verb forms, collocations, and example usage sentences.

### **How Search Works (Dual-Engine Ranking)**
LexiTrio uses a specialized search system designed for instant response (< 5ms on device):

1. **Exact Headword Match:** Matches exact target headword (Highest Priority).
2. **Headword Prefix Match:** Matches words starting with the user query.
3. **FTS Token Match (BM25 Ranking):** SQLite FTS5 / PostgreSQL `tsvector` token match against definitions, primary meanings, and target translations.
4. **Substring Fallback:** Substring search for partial query matching.
5. **UI Text Highlighting:** Matched query terms are dynamically highlighted in search results using `HighlightedSearchText`.

---

## 🔄 User Flows (Foydalanuvchi Boshidan Kechiradigan Bosqichlar)

### **Flow 1: Dictionary Search & Neural Voice Pronunciation**
```
[Search Bar] ──► Type "apple" / "olma"
      │
      ▼
[Dictionary Results] ──► Instant Ranked FTS Results with Highlighted Search Terms
      │
      ▼
[Word Detail Screen] ──► View IPA, Meanings (RU/UZ), Part of Speech, Examples
      │
      ▼
[Tap Speaker Icon] ──► SpeakDictionaryWordUseCase
                              │
                              ├──► Checks Local Audio WAV Cache
                              └──► If missing: Runs Sherpa-ONNX VITS Neural Model
                                   (Fallback: Android System TTS Engine)
```

### **Flow 2: Curriculum Grammar Lesson & Practice Session**
```
[Home Screen] ──► Select "Grammar Curriculum"
      │
      ▼
[Level Selector] ──► Choose "EN_CORE" ➔ "A1 Beginner"
      │
      ▼
[Topic List] ──► Select "EN_A1_004: Articles: A, An, The"
      │
      ▼
[Lesson Detail Screen]
   ├── 📖 Simple Explanation (EN / RU / UZ)
   ├── 📐 Main Rule Formula & Analogy
   ├── 📊 Interactive Grammar Table
   ├── 💬 Positive, Negative & Question Sentence Examples
   └── ⚠️ Common Mistakes & Exceptions
      │
      ▼
[Start Practice] ──► CurriculumExerciseScreen (15 Interactive Steps)
      │
      ├── Step 1: FILL_IN_BLANK ("I saw ___ apple.")
      ├── Step 2: SENTENCE_ORDERING ("an / is / Apple / fruit")
      └── ...
      │
      ▼
[Session Complete] ──► Score Summary (e.g. 14/15 Correct)
                       Progress persisted to Room DB (UserTopicProgressEntity)
```

---

## ⚡ Setup & Deployment

### **1. Running the Android Application**
1. Open the project folder in **Android Studio (2024.1+)**.
2. Sync Project with Gradle Files (`gradle_sync`).
3. Connect an Android device or start an Emulator (**Android 8.0+ / API 26+**).
4. Run/Deploy `:app` module.

### **2. Running the Django Backend (Docker)**
```bash
# Clone repository
git clone https://github.com/Mrxforte/LexiTrio.git
cd LexiTrio

# Start services using Docker Compose
docker-compose up -d --build

# Run migrations & import initial curriculum catalog
docker-compose exec web python manage.py migrate
docker-compose exec web python manage.py import_curriculum
```

---

## 📄 License & Attribution
* **License:** MIT License
* **TTS Models:** Uses Sherpa-ONNX / Piper VITS open voice models (See [TTS_LICENSES.md](TTS_LICENSES.md) for details).
