# GupShup

GupShup is an Android messaging application built using Kotlin, Material Design 3, Room local database, Firebase Firestore, Firebase Authentication, Cloudinary Media SDK, and Cloudflare Workers. It includes offline caching, real-time messaging, media status stories, friend request handling, push notifications, and customizable application settings.

## Core Features

### Real-Time Chat List and Message History
* Live previews for last messages in conversations showing text content or media type indicators.
* Automatic message list sorting so conversations with the newest messages stay at the top.
* Formatted timestamps indicating time for today's messages, relative dates for recent messages, and calendar dates for older messages.
* Unread message counters and visual indicators linked with navigation badges.

### Push Notifications and Deep Linking
* Push notification processing built on Cloudflare Workers and Google Firebase Cloud Messaging (FCM) HTTP v1 API.
* Firebase service account authentication using Web Crypto JWT signing within Cloudflare Worker runtime.
* Direct deep-linking navigation when tapping system tray notifications:
  * Message notifications open ChatActivity directly with parent backstack navigation.
  * Friend request notifications launch MainNavigationActivity with the Friends tab preselected.
* Unique PendingIntent action identifiers and data URIs to prevent intent extra overwrite issues across Android versions.

### Dynamic Notification Channels for Android 8.0+
* NotificationChannelManager class managing dynamic channel versioning (messages_v1, messages_v2, and subsequent versions).
* Automatic channel deletion and recreation when users modify notification sounds or vibration settings, resolving the Android API 26+ notification channel immutability rule.
* Configurable sound options including Default, Chime, Classic Bell, Whistle, and Silent modes along with vibration control.

### Interactive Settings Hub
* Notification preferences covering message alert toggles, sound selection, and vibration controls synced locally and to Firestore.
* Account management supporting profile updates, privacy visibility settings (Online status and profile photo), blocked contact tracking, and User ID clipboard copying.
* Interface customization supporting theme selection (Light, Dark, System), chat wallpaper options, font size controls, and Enter key message sending behavior.
* Storage and data tools including cache calculation, auto-download media policies, and manual cache cleanup.
* Help and support utilities including an interactive FAQ bottom sheet, direct email support launcher, and bug reporting tools.

### Custom 60fps Animated Vector Empty States
* ModernAnimatedEmptyView component replacing static images across empty screens.
* Continuous vector canvas rendering featuring dual expanding pulse rings, ambient glowing background layers, floating vertical movement, and breathing scale transforms.
* Applied across Search, Status, Home Chats, and Friends empty state layouts.

### Offline Caching Architecture
* Room SQLite database layer caching local user profiles, friend requests, message histories, and status updates.
* Five-minute cache freshness policy skipping unnecessary network requests while maintaining manual pull-to-refresh capabilities.
* Automatic cleanup worker pruning message logs older than seven days and expired status stories on application startup.

### Security and Data Lifecycle
* Firestore security rules restricting access across user documents, friend requests, chats, status stories, and bug reports.
* Permanent account deletion feature removing remote user documents, status stories, friend requests, local Room tables, shared preferences, and the associated Firebase Auth account.

## Technical Specifications

* **Language**: Kotlin 2.0.0
* **Build System**: Android Gradle Plugin (AGP) 8.11.0
* **JDK Version**: Java 17 / 21
* **Architecture**: MVVM with Repository Pattern and Room Caching
* **Local Caching**: Room Persistence Library 2.6.1
* **Cloud Database**: Firebase Firestore 24.10.0
* **Authentication**: Firebase Authentication 22.3.0 and Google Play Services Auth 21.0.0
* **Notification Backend**: Cloudflare Worker with FCM HTTP v1 API integration
* **Media Storage**: Cloudinary Android SDK 2.5.0
* **Image Rendering**: Glide 4.15.1
* **UI Components**: Material Design 3 (1.12.0) and custom 60fps vector animation views
* **Shimmer Effects**: Facebook Shimmer 0.5.0

## Repository Structure

```text
com.example.gupshup
 ├── SplashActivity.kt                    # Launcher activity with intent payload routing
 ├── GupShupApp.kt                        # Application class and channel initialization
 ├── adapter                              # RecyclerView Adapters
 │    ├── ChatAdapter.kt                  # Message bubble and media item binding
 │    ├── UsersAdapter.kt                 # Chat item list binding and timestamp formatting
 │    ├── StatusAdapter.kt                # Vertical status feed list binding
 │    └── StatusBubbleAdapter.kt          # Horizontal status bubble list binding
 ├── data                                 # Local Data Layer
 │    └── local
 │         ├── AppDatabase.kt             # Room database configuration
 │         ├── CacheConfig.kt             # Cache expiration thresholds
 │         ├── CacheCleanupManager.kt     # Routine cache cleanup worker
 │         ├── dao                        # Room DAOs (UserDao, MessageDao, etc.)
 │         └── entity                     # Room Entity definitions
 ├── model                                # Domain models and Firestore document schemas
 ├── service
 │    └── GupShupMessagingService.kt      # FCM message receiver and notification handler
 ├── ui                                   # User Interface Controllers
 │    ├── auth                            # Authentication screens
 │    ├── chat                            # Chat details and status viewing screens
 │    ├── main                            # Main navigation tabs and settings screens
 │    └── view
 │         └── ModernAnimatedEmptyView.kt # Custom animated vector empty state view
 └── util                                 # Utility classes
      ├── CloudinaryManager.kt            # Media upload wrapper
      ├── ImageLoaderUtil.kt              # Glide image loading utilities
      ├── NetworkObserver.kt              # Connectivity monitoring utility
      └── NotificationChannelManager.kt   # Dynamic notification channel manager
```

## Cloudflare Worker Backend Structure

```text
cloudflare-worker
 ├── src
 │    └── index.ts                        # Hono router handling /notify/message and /notify/friend-request
 ├── package.json                         # Node dependencies and Wrangler scripts
 └── wrangler.toml                        # Worker environment configuration
```

## Building and Running

### Prerequisites
* Android Studio Jellyfish or newer
* JDK 17 or JDK 21 installed
* Node.js and Wrangler CLI (for Cloudflare Worker development)

### Android Project Build Command
Execute the Gradle build script from the project root directory:

```powershell
# Windows PowerShell
$env:JAVA_HOME="C:\Users\chsaa\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug

# Linux / macOS
./gradlew assembleDebug
```

The output APK will be available at `app/build/outputs/apk/debug/app-debug.apk`.

### Cloudflare Worker Deployment Command
To deploy the notification worker to Cloudflare Workers:

```bash
cd cloudflare-worker
npx wrangler deploy
```

## License

This project is licensed under the MIT License. Refer to the LICENSE file for more information.
