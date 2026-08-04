# GupShup

![Kotlin Version](https://img.shields.io/badge/Kotlin-2.0.0-blue.svg)
![Min SDK](https://img.shields.io/badge/Min%20SDK-21-green.svg)
![Target SDK](https://img.shields.io/badge/Target%20SDK-34-orange.svg)
![Gradle](https://img.shields.io/badge/AGP-8.11.0-brightgreen.svg)
![License](https://img.shields.io/badge/License-MIT-purple.svg)

**GupShup** is a modern, feature-rich Android messaging application built with Kotlin, Traditional Android XML Views (Material Design 3), Firebase Firestore, Firebase Authentication, and Cloudinary Media SDK. It provides real-time 1-on-1 chat, photo media sharing, status stories with view tracking and comments, friend request management, and online presence tracking.

---

## Features

### Authentication & Onboarding
- **Animated Splash Screen**: Edge-to-edge window insets with logo overshoot animation, brand color gradient (`#00A884`), and automatic session checking.
- **Email & Password Authentication**: Complete sign-up, sign-in, and password reset email flows via Firebase Auth.
- **Google Sign-In**: Native Google Identity integration (`GoogleSignInClient` / `AuthCredential`).
- **Email Verification**: Dedicated verification check and resend screen (`EmailVerificationActivity`).

### Real-Time Messaging
- **1-on-1 Chat**: Low-latency message sync using Firebase Firestore real-time snapshot listeners.
- **Media Sharing**: Photo message attachments uploaded asynchronously to Cloudinary with Glide photo previews and full-screen lightbox viewing.
- **Message Reactions**: Interactive emoji reactions triggered via long-press on messages.
- **Read Receipts & Status**: Visual checkmarks (`ic_check_single` for sent, `ic_check_double` for read/seen).
- **Typing & Presence Indicators**: Real-time typing status (`typingTo`) and online presence indicators (`isOnline` / `lastSeen`).

### Status Stories
- **Multimedia Status Updates**: Post text or photo status updates stored in Firestore.
- **Segmented Story Viewer**: WhatsApp/Instagram-style story viewer (`StatusStoryActivity`) with animated progress segments, tap-to-skip navigation, and auto-advance.
- **Views & Comments**: Track story view counts (`viewers` collection) and interactive story comment replies.

### Social & Friends
- **User Search**: Query registered users by name or email.
- **Friend Request System**: Send, accept, or reject friend requests (`pending` / `accepted` status).
- **Unread & Request Badges**: Live numerical badge updates on bottom navigation items for unread chat messages and pending friend requests.

### Design & Connectivity
- **Material Design 3 (Material You)**: Full light and dark mode styling (`values-night/`), surface-toned toolbars, asymmetric chat bubbles, and clean typography (`Google Sans`).
- **Offline Banner**: Network connectivity flow monitor (`NetworkObserver`) with an animated top offline banner.
- **Shimmer Placeholders**: Loading animation placeholders powered by Facebook Shimmer.

---

## Tech Stack

| Category | Technology / Library | Version | Purpose |
|---|---|---|---|
| **Language** | Kotlin | `2.0.0` | Primary development language |
| **Build System** | Android Gradle Plugin (AGP) | `8.11.0` | Build configuration & task execution |
| **JDK Version** | Java Development Kit | `17` | Java compatibility level |
| **Architecture** | MVVM + Android View Binding | — | Separation of UI and data layers |
| **Authentication** | Firebase Auth | `22.3.0` | Email/password & token management |
| **Google Auth** | Play Services Auth | `21.0.0` | Google Sign-In integration |
| **Database** | Firebase Firestore | `24.10.0` | Real-time database for messages & users |
| **Media Cloud** | Cloudinary Android SDK | `2.5.0` | Unsigned cloud photo storage |
| **Image Loading** | Glide | `4.15.1` | Asynchronous image loading & caching |
| **UI Components** | Google Material Design 3 | `1.12.0` | Material You themes & components |
| **Shimmer Effect** | Facebook Shimmer | `0.5.0` | Loading placeholders |
| **Circle Image** | CircleImageView | `3.1.0` | Circular avatar rendering |
| **Async** | Kotlin Coroutines & Flow | `1.6.4` | Asynchronous operations & network state |

---

## Architecture

GupShup is structured around the **Model-View-ViewModel (MVVM)** architectural pattern:

```text
com.example.gupshup
 ├── SplashActivity.kt            # Launcher activity & splash animation
 ├── GupShupApp.kt                # Application subclass & Firestore settings
 ├── adapter                      # RecyclerView Adapters
 │    ├── ChatAdapter.kt          # Message bubbles & photo renderers
 │    ├── UsersAdapter.kt         # Friend list items & unread badges
 │    ├── StatusAdapter.kt        # Status feed items
 │    ├── StatusBubbleAdapter.kt  # Horizontal status story bubbles
 │    └── ...
 ├── di                           # Dependency Injection / Glide Modules
 │    └── GupShupGlideModule.kt
 ├── model                        # Data Models (Firestore DTOs)
 │    ├── ChatMessage.kt          # Message data class with reactions & seen state
 │    ├── User.kt                 # User profile & presence data
 │    ├── Status.kt               # Photo/text status story model
 │    └── ...
 ├── ui                           # UI Controllers (Activities & Fragments)
 │    ├── auth                    # LoginActivity, RegisterActivity, EmailVerificationActivity
 │    ├── chat                    # ChatActivity, StatusStoryActivity, StatusViewActivity
 │    └── main                    # MainNavigationActivity, HomeFragment, FriendsFragment,
 │                                # ProfileFragment, SearchFragment, StatusFragment
 ├── util                         # Utilities & Service Wrappers
 │    ├── CloudinaryManager.kt    # Cloudinary unsigned photo uploader
 │    ├── ImageUtils.kt           # Glide profile helper
 │    └── NetworkObserver.kt      # Flow-based connectivity observer
 └── viewmodel                    # ViewModels (State Holders)
      └── ChatViewModel.kt
```

---

## Prerequisites

Before building and running GupShup, ensure your development environment satisfies:

- **Android Studio**: Jellyfish (2024.1.1) or newer
- **JDK**: Java 17
- **Min SDK**: `21` (Android 5.0 Lollipop)
- **Target SDK**: `34` (Android 14)
- **Compile SDK**: `34`

---

## Installation & Setup

### 1. Clone Repository
```bash
git clone https://github.com/your-username/GupShup.git
cd GupShup
```

### 2. Configure Cloudinary Credentials
Create a file named `local.properties` in the root directory of the project (if it does not exist) and add your Cloudinary details:

```properties
cloudinary.cloud_name=YOUR_CLOUDINARY_CLOUD_NAME
cloudinary.upload_preset=YOUR_CLOUDINARY_UNSIGNED_UPLOAD_PRESET
```

> **Note**: `local.properties` is listed in `.gitignore` to prevent leaking credentials to public repositories.

### 3. Configure Firebase
1. Create a project in the [Firebase Console](https://console.firebase.google.com/).
2. Enable **Authentication** (Email/Password & Google Sign-In providers).
3. Enable **Cloud Firestore** database in production or test mode.
4. Add an Android app with the package name `com.example.gupshup`.
5. Register your development machine's SHA-1 debug key in Firebase Settings:
   ```powershell
   # Generate SHA-1 fingerprint on Windows PowerShell
   .\gradlew.bat signingReport
   ```
6. Download `google-services.json` and place it into the `app/` directory (`app/google-services.json`).

---

## How to Run

### Command Line
To build and assemble the debug APK:
```powershell
# Windows
.\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```
The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

### Android Studio
1. Open Android Studio and choose **Open an Existing Project**.
2. Select the `GupShup` directory.
3. Allow Gradle to sync dependencies.
4. Select a connected physical Android device or Emulator.
5. Click **Run 'app'** (`Shift + F10`).

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

- **Firestore `whereIn` Limit**: Firestore limits `whereIn` queries to a maximum of 10 items per chunk. `HomeFragment` handles this by splitting friend IDs into 10-item chunks.
- **Single File Pick**: Photo attachments support single-image selection per upload attempt.
- **Local Credentials Requirement**: Cloudinary credentials must be defined in `local.properties`; fallback default values are provided if missing.

---

## Screenshots

*(Add app screenshots or GIFs here)*

| Splash Screen | Login | Chat List | Real-time Chat | Dark Mode |
|:---:|:---:|:---:|:---:|:---:|
| *Splash* | *Login* | *Home* | *Chat* | *Dark Mode* |

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Contact & Author

- **Repository**: [GupShup GitHub Repository](https://github.com/your-username/GupShup)
- **Author**: GupShup Development Team
