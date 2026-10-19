# BidDrive Android - Kotlin & Jetpack Compose

Modern, high-performance Android application for the **BidDrive** real-time reverse-auction mobility platform. Built with **Kotlin 2.0**, **Jetpack Compose**, **Material 3**, **Coroutines / StateFlow**, and **Retrofit + STOMP WebSockets**.

---

## 🚀 Key Features

### 1. Dual Persona & Instant Role Switcher
- **Single Unified App Architecture:** Seamlessly switch between **Passenger Mode** and **Driver Mode** from the top app bar without re-logging or installing two separate APKs.
- **Persistent JWT Session:** Stores authenticated token, role, user ID, and email in Android `SharedPreferences`.
- **Demo Personas 1-Tap Fill:** Pre-configured buttons to instantly test as a **Passenger** (`passenger1@test.com`) or **Sedan Driver** (`driver1@test.com`).

### 2. Passenger Experience
- **Interactive Booking Sheet:** Pickup and destination entry with pre-calculated distance and estimated base fare (`GET /api/rides/estimate`).
- **Vehicle Category Selection:** Choose between **Sedan**, **Hatchback**, **SUV**, **Auto**, and **Bike**.
- **Live Reverse Auction View:**
  - Real-time **90-second countdown timer** synchronized with Redis TTL.
  - Dynamic incoming bid feed received over STOMP WebSocket (`/topic/auction/{rideId}`).
  - **Best Price Spotlight:** Automatically highlights the lowest driver bid and displays real-time savings (e.g., *"You save ₹18!"*).
  - 1-tap **"Accept Lowest Bid"** button.
- **Ride In-Progress & Security:**
  - **4-Digit OTP Display:** Prominent OTP card (e.g., `4821`) for passenger safety.
  - Live status tracking: `ACCEPTED` ➔ `ARRIVING` ➔ `ARRIVED` ➔ `IN_PROGRESS` ➔ `COMPLETED`.

### 3. Driver Experience
- **Driver Status Switch:** Toggle real-time driver state between `Available`, `BUSY`, and `Offline` (`PUT /api/drivers/status`).
- **Live Ride Feed:** Automatically receives and displays live auction requests matching the driver's vehicle type.
- **Quick Underbidding Panel:**
  - 1-tap decrement buttons: **-₹5**, **-₹10**, or custom counter-bid.
  - Enforces descending bid rules via atomic Redis Lua CAS (`POST /api/bids`).
- **Trip Execution Lifecycle:**
  - **"I Have Arrived"** notification (`PUT /api/rides/{id}/arrived`).
  - **Enter Passenger OTP & Verify** to start the trip (`POST /api/rides/{id}/start`).
  - **"Complete Trip"** to finalize the fare and credit wallet (`PUT /api/rides/{id}/complete`).

---

## 🛠️ Architecture & Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.0+ (JVM 21) |
| **UI Framework** | Jetpack Compose (Declarative UI) |
| **Design System** | Material 3 Dark Theme (Cyan `#00E5FF`, Emerald `#00E676`, Slate Backgrounds) |
| **State Management** | Kotlin Coroutines, `StateFlow`, `SharedFlow` |
| **REST Networking** | Retrofit 2 + OkHttp 4 + HttpLoggingInterceptor |
| **Real-time Comms** | STOMP protocol over OkHttp WebSocket (`/ws/websocket`) with polling fallback |
| **Serialization** | Google Gson |

---

## 📱 Project Structure

```
biddrive-android/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/biddrive/app/
│       │   ├── BidDriveApp.kt             # Application class & token storage
│       │   ├── MainActivity.kt            # Root Compose Activity & Role Switcher
│       │   ├── data/
│       │   │   ├── api/
│       │   │   │   ├── BidDriveApi.kt     # Retrofit REST interface
│       │   │   │   └── NetworkClient.kt   # Dynamic base URL & OkHttpClient
│       │   │   ├── models/
│       │   │   │   └── Models.kt          # DTOs (Auth, Ride, Bid, Driver)
│       │   │   ├── repository/
│       │   │   │   └── BidDriveRepository.kt # Central business logic repository
│       │   │   └── websocket/
│       │   │       └── BidDriveWebSocket.kt  # STOMP real-time auction client
│       │   └── ui/
│       │       ├── auth/
│       │       │   └── AuthScreen.kt      # Login, Register, Server Config
│       │       ├── components/
│       │       │   └── CommonComponents.kt# RoleSwitcher, CountdownTimer, Chips
│       │       ├── driver/
│       │       │   └── DriverScreen.kt    # Status, Live Auctions, Underbidding, Trip
│       │       ├── passenger/
│       │       │   └── PassengerScreen.kt # Booking, Reverse Auction, OTP Tracking
│       │       └── theme/
│       │           ├── Color.kt           # Custom BidDrive dark palette
│       │           └── Theme.kt           # Material 3 dark color scheme
│       └── res/
│           ├── values/
│           │   ├── colors.xml
│           │   ├── strings.xml
│           │   └── themes.xml
│           └── xml/
│               └── network_security_config.xml # Allows cleartext HTTP to local dev
├── gradle/
│   ├── libs.versions.toml                 # Version Catalog (AGP, Compose, Retrofit)
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
└── gradlew.bat
```

---

## 🔌 Connecting to Local Backend

By default, the Android app connects to:
- **Android Emulator:** `http://10.0.2.2:8080/` (maps directly to your Windows host machine `localhost:8080`).
- **Physical Device:** Open the app ➔ Tap the **Settings (⚙️)** icon in the top bar ➔ Enter your host machine's LAN IP (e.g. `http://192.168.1.5:8080/`).
