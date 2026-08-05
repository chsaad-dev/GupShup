# GupShup

![Kotlin Version](https://img.shields.io/badge/Kotlin-2.0.0-blue.svg)
![Min SDK](https://img.shields.io/badge/Min%20SDK-21-green.svg)
![Target SDK](https://img.shields.io/badge/Target%20SDK-34-orange.svg)
![Gradle](https://img.shields.io/badge/AGP-8.11.0-brightgreen.svg)
![License](https://img.shields.io/badge/License-MIT-purple.svg)

**GupShup** is a modern, feature-rich Android messaging application built with Kotlin, Material Design 3 Views, Room local SQLite database, Firebase Firestore, Firebase Authentication, and Cloudinary Media SDK. It features an offline-first architecture, real-time 1-on-1 chat, WhatsApp-style last message previews with smart timestamps, multimedia status stories, friend request management, online presence tracking, strict security rules, and a comprehensive Settings hub with native support and reporting tools.

---

## Key Features

### Real-Time Chat List & Activity Sorting
- **Last Message Previews**: Home screen replaces email addresses with live message previews (text content or photo indicator).
- **Smart Formatted Timestamps**: Top-right timestamp formatting (`11:15 AM` for today, `Yesterday`, `dd/MM/yy` for older dates) with multi-type timestamp parsing and subcollection fallback.
- **Dynamic Reordering**: Conversations automatically re-order in real time so the contact with the latest message always rises to the top of the chat list.
- **Unread Badges & Highlighting**: Bold text formatting on unread message previews with dynamic bottom navigation badge count updates.

### Help Center & In-App Bug Reporting
- **Interactive FAQ Sheet**: Collapsible FAQ bottom sheet answering common questions regarding privacy settings, profile updates, blocking, chat themes, offline caching, and account deletion.
- **Direct Email Support**: Integrated email launcher targeting `saaddevlabs@gmail.com` with auto-filled device specs (Device Model, Android OS Version, App Version `1.0.0`, User ID).
- **Report a Problem Sheet**: Interactive reporting sheet allowing users to categorize issues (*Bug Report, Feature Request, Account & Privacy, Other*) and submit reports directly to the Firestore `reports` collection. Features smooth keyboard resize handling (`SOFT_INPUT_ADJUST_RESIZE`) and accessible high-contrast input fields.

### Production Security & Complete Data Purge
- **Strict Firestore Security Rules (`firestore.rules`)**: Production-grade rules enforcing document ownership across `users`, `friend_requests`, `chats`, `statuses`, `status`, and confidential `reports`.
- **Permanent Account Deletion**: Full account deletion workflow in `SettingsActivity` that purges user profiles, status updates, sent/received friend requests, clears Room SQLite tables and `SharedPreferences`, and deletes the Firebase Auth account.

### Material Design 3 UI & Navigation Architecture
- **Material Design 3 (Material You)**: Refined design system with curated brand green (`#00A884`), surface-toned toolbars, custom Google Sans typography, asymmetric chat bubbles, and elevation layers.
- **Smart Navigation & Backstack**: Bottom navigation uses a `show()` / `hide()` fragment transaction strategy across `HomeFragment`, `StatusFragment`, `SearchFragment`, `FriendsFragment`, and `ProfileFragment`. Pressing back on any non-Home tab returns to the **Home tab** before exiting the app.
- **Settings Hub (`SettingsActivity`)**:
  - **Account**: Profile management, Privacy toggles (Online status, Last seen, Profile photo), Blocked contacts count, and one-tap User ID copy icon.
  - **Notifications**: Message notifications switch, custom sound picker, vibration toggle.
  - **Chat**: App theme selector (Light / Dark / System), Chat wallpaper picker, Enter key send toggle, Font size dialog.
  - **Data & Storage**: Network usage statistics, Auto-download media toggles.
  - **Help & About**: Interactive FAQ Help Center, Bug Reporting sheet, App version details (`v1.0.0`).

### Offline-First Architecture & Caching
- **Room Local Persistence**: Full Room cache layer (`AppDatabase` v4) storing local entities (`UserEntity`, `FriendRequestEntity`, `MessageEntity`, `StatusEntity`).
- **Staleness Policy & Quota Optimization**:
  - **5-Minute Freshness Check**: Serves Room Flow emissions directly when cache is fresh, skipping redundant network calls.
  - **Force Refresh**: Pull-to-refresh (`SwipeRefreshLayout`) bypasses staleness rules to fetch fresh network data.
- **Session Cache Maintenance**: `CacheCleanupManager` prunes messages older than 7 days and expired 24-hour status stories on launch.

---

## Tech Stack

| Category | Technology / Library | Version | Purpose |
|---|---|---|---|
| **Language** | Kotlin | `2.0.0` | Primary development language |
| **Build System** | Android Gradle Plugin (AGP) | `8.11.0` | Build configuration & task execution |
| **JDK Version** | Java Development Kit | `17` / `21` | Java compatibility level |
| **Architecture** | MVVM + Offline-First Repository | — | Layered UI, ViewModel, Local DB & Remote Sync |
| **Local Database** | Room Persistence Library | `2.6.1` | Local SQLite caching & offline-first persistence |
| **Remote Database** | Firebase Firestore | `24.10.0` | Real-time cloud database & security rules |
| **Authentication** | Firebase Auth | `22.3.0` | Email/password & auth state management |
| **Google Auth** | Play Services Auth | `21.0.0` | Native Google Sign-In integration |
| **Media Cloud** | Cloudinary Android SDK | `2.5.0` | Unsigned cloud image storage |
| **Image Loading** | Glide | `4.15.1` | Unified image loading & signature cache-busting |
| **UI Components** | Google Material Design 3 | `1.12.0` | Material 3 themes, cards, switches & bottom sheets |
| **Shimmer Effect** | Facebook Shimmer | `0.5.0` | Loading placeholders |
| **Async & State** | Kotlin Coroutines & Flow | `1.6.4` / `2.7.0` | Reactive DB streams & coroutine scope management |

---

## Architecture

GupShup follows an **Offline-First Layered MVVM Architecture**:

```text
UI Controllers (Activities / Fragments / BottomSheets)
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

---

## Project Structure

```text
com.example.gupshup
 ├── SplashActivity.kt                    # Animated launcher & session validator
 ├── GupShupApp.kt                        # Application class & Firestore settings
 ├── adapter                              # RecyclerView Adapters
 │    ├── ChatAdapter.kt                  # Message bubbles & photo renderers
 │    ├── UsersAdapter.kt                 # User items, avatars, last message previews & timestamps
 │    ├── StatusAdapter.kt                # Vertical status feed items
 │    └── StatusBubbleAdapter.kt          # Horizontal status story bubbles
 ├── data                                 # Data Layer (Room Local Database)
 │    └── local
 │         ├── AppDatabase.kt             # Room database singleton (version 4)
 │         ├── CacheConfig.kt             # Staleness thresholds & activeChatId tracker
 │         ├── CacheCleanupManager.kt     # Session cache maintenance worker
 │         ├── dao                        # Data Access Objects (UserDao, MessageDao, etc.)
 │         └── entity                     # Room Entities (UserEntity, MessageEntity, etc.)
 ├── model                                # Domain & Firestore Data Models
 ├── ui                                   # UI Controllers
 │    ├── auth                            # LoginActivity, RegisterActivity, EmailVerificationActivity
 │    ├── chat                            # ChatActivity, StatusStoryActivity, StatusViewActivity
 │    └── main                            # MainNavigationActivity, HomeFragment, FriendsFragment,
 │                                        # ProfileFragment, SearchFragment, StatusFragment, SettingsActivity,
 │                                        # HelpCenterBottomSheetFragment, ReportProblemBottomSheetFragment
 └── util                                 # Helper Utilities
      ├── CloudinaryManager.kt            # Photo upload utility
      ├── ImageLoaderUtil.kt              # Unified Glide loader & cache key buster
      └── NetworkObserver.kt              # Flow-based connectivity monitor
```

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

### 3. Configure Firebase & Security Rules
1. Create a project in the [Firebase Console](https://console.firebase.google.com/).
2. Enable **Authentication** (Email/Password & Google Sign-In).
3. Enable **Cloud Firestore** database.
4. Copy the security rules from [`firestore.rules`](firestore.rules) into the Firebase Console Rules tab.
5. Download `google-services.json` and place it in `app/google-services.json`.

---

## How to Build & Run

### Command Line
To compile and assemble the debug APK:

```powershell
# Windows PowerShell
$env:JAVA_HOME="C:\Users\chsaa\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

The output APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Author & Contact

- **Repository**: [GupShup GitHub Repository](https://github.com/chsaad-dev/GupShup)
- **Developer**: Muhammad Saad (`chsaad-dev`)
- **Support Email**: `saaddevlabs@gmail.com`
