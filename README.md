# GupShup

![Kotlin Version](https://img.shields.io/badge/Kotlin-2.0.0-blue.svg)
![Min SDK](https://img.shields.io/badge/Min%20SDK-21-green.svg)
![Target SDK](https://img.shields.io/badge/Target%20SDK-34-orange.svg)
![Gradle](https://img.shields.io/badge/AGP-8.11.0-brightgreen.svg)
![License](https://img.shields.io/badge/License-MIT-purple.svg)

**GupShup** is a modern, feature-rich Android messaging application built with Kotlin, Material Design 3 XML Views, Room local database, Firebase Firestore, Firebase Authentication, and Cloudinary Media SDK. It features an offline-first architecture, real-time 1-on-1 chat, photo media sharing, multimedia status stories, friend request management, online presence tracking, and a comprehensive Settings screen.

---

## Key Features

### 🎨 Material Design 3 UI & Navigation Architecture
- **Material Design 3 (Material You)**: Refined design system with curated brand green (`#00A884`), surface-toned toolbars, custom typography, asymmetric chat bubbles, and elevation layers.
- **State-Preserving Navigation**: Bottom navigation uses a `show()` / `hide()` fragment transaction strategy across `HomeFragment`, `StatusFragment`, `SearchFragment`, `FriendsFragment`, and `ProfileFragment`. Keeps fragment instances, scroll positions, and view states alive without re-creating views on tab switches.
- **New Settings Screen**: Full-featured settings Hub (`SettingsActivity`) organized into grouped Material 3 card sections:
  - **Account**: Profile management, Privacy toggles (Online status, Last seen, Profile photo), Blocked contacts count.
  - **Notifications**: Message notifications switch, custom notification sound picker, vibration toggle.
  - **Chat**: Theme selector (Light / Dark / System), Chat wallpaper picker, Clear chat history action.
  - **Data & Storage**: Network usage statistics, Auto-download media toggles.
  - **Help & About**: FAQ link, App version details (`v1.0.0`), Terms & Privacy policies.

### ⚡ Offline-First Architecture & Caching
- **Room Local Persistence**: Full Room cache layer (`AppDatabase` v1) storing local entities:
  - `UserEntity`: User profiles, bio, photo URL, online status, `updatedAt`, `cachedAt`.
  - `FriendRequestEntity`: Friend request status (`pending`/`accepted`), timestamps, `cachedAt`.
  - `MessageEntity`: Chat messages indexed by `chatId`, sender/receiver IDs, text, media, `timestamp`, `seen`.
  - `StatusEntity`: Active status updates, user details, media URL, `expiresAt` (24h cleanup).
- **Staleness Policy & Read-Quota Optimization**:
  - **5-Minute Freshness Check**: `HomeFragment` and `FriendsFragment` check the latest `cachedAt` timestamp. If cache is under 5 minutes old, the app serves Room Flow emissions directly and skips Firestore `.get()` calls entirely, saving bandwidth and read quota.
  - **Force Refresh**: Pull-to-refresh (`SwipeRefreshLayout`) bypasses staleness rules to fetch fresh network data.
- **Session Cache Maintenance**: `CacheCleanupManager` runs once per session on launch:
  - Prunes chat messages older than 7 days via `MessageDao.deleteOlderThanExcludingChat()` (safely preserving messages in any active chat).
  - Deletes expired status stories via `StatusDao.deleteExpired()`.

### 🖼️ Unified Image Loading System
- **Single `ImageLoaderUtil` Helper**: Consolidated Glide image loading strategy featuring disk caching (`DiskCacheStrategy.ALL`), memory caching, crossfade transitions, and fallback placeholders (`ic_profile_placeholder`, `rounded_image_bg`).
- **Automatic Cache Busting**: Profile avatars use `ObjectKey("${url}_${updatedAt}")` signatures. When a user updates their profile photo, `updatedAt` is updated in Firestore, immediately invalidating cached avatars across all screens.

### 💬 Real-Time Messaging & Status Stories
- **1-on-1 Chat**: Low-latency message synchronization using Firestore snapshot listeners backed by Room write-through caching.
- **Media Sharing**: Asynchronous Cloudinary photo uploads with full-screen lightbox viewing (`showImagePreviewDialog`).
- **Reactions & Read Receipts**: Interactive emoji reaction dialogs and visual checkmarks (`ic_check_single` / `ic_check_double`).
- **Presence & Typing**: Real-time typing status (`typingTo`) and online/last-seen tracking (`isOnline` / `lastSeen`).
- **Status Stories**: Story viewer (`StatusStoryActivity`) with animated progress segments, tap-to-skip navigation, view counters (`viewers` collection), and story replies.

### 👥 Social & Onboarding
- **Authentication**: Email/Password authentication, Google Sign-In (`GoogleSignInClient`), and Email Verification (`EmailVerificationActivity`).
- **Search & Friends**: Search registered users by name/email; send, accept, or reject friend requests with live unread and pending request badges on bottom navigation items.

---

## Tech Stack

| Category | Technology / Library | Version | Purpose |
|---|---|---|---|
| **Language** | Kotlin | `2.0.0` | Primary development language |
| **Build System** | Android Gradle Plugin (AGP) | `8.11.0` | Build configuration & task execution |
| **JDK Version** | Java Development Kit | `17` | Java compatibility level |
| **Architecture** | MVVM + Offline-First Repository | — | Layered UI, ViewModel, Local DB & Remote Sync |
| **Local Database** | Room Persistence Library | `2.6.1` | Local SQLite caching & offline-first persistence |
| **Remote Database** | Firebase Firestore | `24.10.0` | Real-time cloud database & listener sync |
| **Authentication** | Firebase Auth | `22.3.0` | Email/password & auth state management |
| **Google Auth** | Play Services Auth | `21.0.0` | Native Google Sign-In integration |
| **Media Cloud** | Cloudinary Android SDK | `2.5.0` | Unsigned cloud image storage |
| **Image Loading** | Glide | `4.15.1` | Unified image loading & signature cache-busting |
| **UI Components** | Google Material Design 3 | `1.12.0` | Material 3 themes, cards, switches & dialogs |
| **Shimmer Effect** | Facebook Shimmer | `0.5.0` | Loading placeholders |
| **Circle Image** | CircleImageView | `3.1.0` | Circular avatar rendering |
| **Async & State** | Kotlin Coroutines & Flow | `1.6.4` / `2.7.0` | Reactive DB streams & coroutine scope management |

---

## Architecture

GupShup follows an **Offline-First Layered MVVM Architecture**:

```text
UI Controllers (Activities / Fragments)
           │
           ▼
ViewModel / UI State Holders
           │
           ▼
Offline-First Repository Flow
 ├── 1. Read cached data instantly from Room Database (Flow<T>)
 ├── 2. Evaluate staleness policy (5-min threshold / force-refresh)
 └── 3. Fetch from Firestore / Cloudinary & write-through to Room
           │
 ┌─────────┴─────────┐
 ▼                   ▼
Room Local DB     Firebase / Cloudinary
(SQLite Cache)    (Remote Source of Truth)
```

### Package Structure

```text
com.example.gupshup
 ├── SplashActivity.kt            # Animated launcher & session validator
 ├── GupShupApp.kt                # Application class & Firestore settings
 ├── adapter                      # RecyclerView Adapters
 │    ├── ChatAdapter.kt          # Message bubbles & photo renderers
 │    ├── UsersAdapter.kt         # User items, avatars & unread badges
 │    ├── StatusAdapter.kt        # Vertical status feed items
 │    └── StatusBubbleAdapter.kt  # Horizontal status story bubbles
 ├── data                         # Data Layer (Room Local Database)
 │    └── local
 │         ├── AppDatabase.kt     # Room database singleton (version 1)
 │         ├── CacheConfig.kt     # Staleness thresholds & activeChatId tracker
 │         ├── CacheCleanupManager.kt # Session cache maintenance worker
 │         ├── dao                # Data Access Objects (UserDao, MessageDao, etc.)
 │         └── entity             # Room Entities (UserEntity, MessageEntity, etc.)
 ├── model                        # Domain & Firestore Data Models
 ├── ui                           # UI Controllers
 │    ├── auth                    # LoginActivity, RegisterActivity, EmailVerificationActivity
 │    ├── chat                    # ChatActivity, StatusStoryActivity
 │    └── main                    # MainNavigationActivity, HomeFragment, FriendsFragment,
 │                                # ProfileFragment, SearchFragment, StatusFragment, SettingsActivity
 └── util                         # Helper Utilities
      ├── CloudinaryManager.kt    # Photo upload utility
      ├── ImageLoaderUtil.kt      # Unified Glide loader & cache key buster
      └── NetworkObserver.kt      # Flow-based connectivity monitor
```

---

## Prerequisites

Ensure your development environment meets the following specifications:

- **Android Studio**: Jellyfish (2024.1.1) or newer
- **JDK**: Java 17
- **Min SDK**: `21` (Android 5.0 Lollipop)
- **Target SDK**: `34` (Android 14)
- **Compile SDK**: `34`

---

## Installation & Setup

### 1. Clone Repository
```bash
git clone https://github.com/chsaad-dev/GupShup.git
cd GupShup
```

### 2. Configure Cloudinary Credentials
Create or edit `local.properties` in the root project directory:

```properties
cloudinary.cloud_name=YOUR_CLOUDINARY_CLOUD_NAME
cloudinary.upload_preset=YOUR_CLOUDINARY_UNSIGNED_UPLOAD_PRESET
```

### 3. Configure Firebase
1. Create a project in the [Firebase Console](https://console.firebase.google.com/).
2. Enable **Authentication** (Email/Password & Google Sign-In).
3. Enable **Cloud Firestore** database.
4. Add an Android app with package name `com.example.gupshup`.
5. Generate and register your SHA-1 debug fingerprint:
   ```powershell
   .\gradlew.bat signingReport
   ```
6. Download `google-services.json` and place it in the `app/` directory (`app/google-services.json`).

---

## How to Build & Run

### Command Line
To compile and assemble the debug APK:

```powershell
# Windows PowerShell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

The output APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Permissions

Declared in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
```

---

## Known Limitations

- **Firestore `whereIn` Query Ceiling**: Firestore limits `whereIn` queries to 10 items per request. `HomeFragment` handles large friend lists by automatically splitting IDs into 10-item chunks.
- **Single Image Picker**: Media attachment selection currently supports 1 photo per upload action.
- **Local Credentials**: Cloudinary credentials must be set in `local.properties` (fallback default placeholders are used if absent).

---

## Screenshots

> *Note: UI screenshots will be updated to reflect the new Material Design 3 theme system and Settings screen.*

| Home Chat List | Real-time Chat | Status Stories | Settings Screen | Profile Screen |
|:---:|:---:|:---:|:---:|:---:|
| *Home* | *Chat* | *Status* | *Settings* | *Profile* |

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Author & Repository

- **Repository**: [GupShup GitHub Repository](https://github.com/chsaad-dev/GupShup)
- **Developer**: Saad (`chsaad-dev`)
