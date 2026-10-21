# Android TV Remote

An open-source Android application for controlling Android TV and Google TV devices over Wi-Fi. Built with Jetpack Compose and Kotlin Coroutines.

The app implements Google's Android TV Remote Protocol v2 (TLS pairing and encrypted remote control protocol) as well as an ADB fallback mode for devices without official remote service support.

## Features

- Native Android TV Protocol v2: Connects via TLS (ports 6467 for pairing, 6466 for remote commands) with standard on-screen PIN code verification.
- ADB Fallback Protocol: Alternative connection via ADB over Wi-Fi (port 5555) for developer-enabled devices and unsupported displays.
- Automatic TV Discovery: Discovers Android TVs on your local network using mDNS (Network Service Discovery / NSD).
- Draggable D-Pad Trackpad: Virtual thumbstick trackpad supporting directional drags, continuous repeat on hold, and center tap for selection.
- Real-Time Live Typing: Syncs keystrokes and backspaces directly to active TV input fields as you type on your phone.
- Multi-User Session Management: Automatically disconnects when the app is placed in the background so other phones or family members can control the TV without lockouts.
- Quick Settings Tile: Launch the remote directly from the Android Quick Settings shade.
- True AMOLED Mode: Pitch-black 0-nit theme designed for OLED displays, with subtle contrast borders and tactile feedback.

## Requirements

- Phone: Android 7.0 (API 24) or newer.
- TV: Any Android TV or Google TV connected to the same Wi-Fi network (Sony, TCL, Hisense, Philips, Chromecast with Google TV, Xiaomi, Nvidia Shield, etc.).

## Pairing Instructions

### Method 1: Android TV Remote v2 (Recommended)

1. Connect your phone and TV to the same Wi-Fi network.
2. Open the app. The app automatically scans for TVs on the network.
3. Select your TV from the list (or tap the dropdown in the top bar to choose a device).
4. When prompted, enter the 6-character alphanumeric pairing code displayed on your TV screen.
5. Once paired, your certificate is saved locally for future automatic connections.

### Method 2: ADB over Wi-Fi

1. On your Android TV, open Settings > Device Preferences > About.
2. Tap "Build" seven times to enable Developer Options.
3. Go to Developer Options and enable "Network Debugging" (or "Wireless Debugging").
4. In the app, open the device picker dialog, enter your TV IP address, select "ADB Wi-Fi (Port 5555)", and connect.

## Architecture

The project is structured around clean protocol separation:

- com.ryzen.tvremote.protocol.AndroidTvRemoteV2Client: Full implementation of Google's Android TV Remote Protocol v2. Handles RSA key pair and self-signed certificate generation using BouncyCastle, TLS handshake, mutual authentication, pairing messages, and protobuf message framing.
- com.ryzen.tvremote.protocol.AdbRemoteClient: Raw socket implementation of the ADB packet protocol for sending input keyevents directly to the Android TV shell.
- com.ryzen.tvremote.protocol.DiscoveryManager: Zero-configuration mDNS service discovery for `_androidtvremote2._tcp` and `_adb._tcp`.
- com.ryzen.tvremote.ui: Single-activity architecture using Jetpack Compose, Material 3, and Kotlin Coroutines/Flow for reactive state management.
- com.ryzen.tvremote.tile: Android Quick Settings Tile integration for quick access.

## Building from Source

### Prerequisites

- Android Studio Ladybug (2024.2) or newer
- JDK 17
- Android SDK Platform 35+

### Build Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/souravkl11/android-tv-remote.git
   cd android-tv-remote
   ```

2. Open the project in Android Studio, or build from the command line:
   ```bash
   ./gradlew assembleDebug
   ```

3. The generated APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

To build a release APK:
```bash
./gradlew assembleRelease
```

## Releases

Pre-built APKs are available on the GitHub Releases page:
https://github.com/souravkl11/android-tv-remote/releases

Download the latest `universal-release.apk` from the latest release and install it directly on your Android phone.

## Privacy & Security

- All communication occurs entirely over your local Wi-Fi network.
- No analytics, tracking, telemetry, or external internet connections are required or used.
- Keys and certificates are generated on-device and stored in private app storage.

## License

This project is licensed under the Apache License 2.0. See the LICENSE file for details.
