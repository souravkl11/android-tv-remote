# Android TV Remote

A clean, open-source Android app to control your Android TV or Google TV over Wi-Fi.

No ads, no tracking, and no bloat.

## Features

- **Android TV Remote Protocol v2**: Connects directly using Google's official TLS protocol with standard on-screen PIN pairing.
- **ADB fallback**: Control devices over ADB (port 5555) if the official remote service is not available.
- **Network discovery**: Automatically finds TVs on your local network using mDNS.
- **D-pad and trackpad**: Smooth directional controls with haptic feedback and tap-to-navigate.
- **Keyboard input**: Type directly into TV search and input fields from your phone.
- **Quick Settings tile**: Jump into the remote right from your Android notification shade.
- **True AMOLED dark theme**: Pure black theme for OLED screens.

## How to Connect

### 1. Official Protocol (Recommended)

1. Make sure your phone and TV are on the same Wi-Fi network.
2. Open the app and select your TV from the discovered devices.
3. Enter the 6-character PIN code displayed on your TV screen.

### 2. ADB over Wi-Fi

If your TV does not support the v2 protocol:
1. Enable Developer Options on your TV (Settings > About > tap Build 7 times).
2. Enable Network Debugging (or Wireless Debugging).
3. In the app, enter your TV's IP address manually and connect via port 5555.

## Download

Download the latest APK from the [Releases](https://github.com/souravkl11/android-tv-remote/releases) page.

## Building

Prerequisites:
- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35

```bash
git clone https://github.com/souravkl11/android-tv-remote.git
cd android-tv-remote
./gradlew assembleDebug
```

The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.
