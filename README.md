# TV Remote for iOS

A native iPhone remote for Android TV and Google TV, written in SwiftUI with iOS 26 Liquid Glass. It is the iOS counterpart of the Android app on the `main` branch and speaks the same Android TV Remote Protocol v2.

No ads, no tracking, no third-party dependencies.

## Features

- **Android TV Remote Protocol v2**: TLS connection with on-screen PIN pairing (ports 6467 and 6466).
- **Network discovery**: finds TVs on your Wi-Fi with Bonjour (`_androidtvremote2._tcp`).
- **D-pad**: tap a direction, or drag the centre puck to scroll with auto-repeat. Tap it for OK, hold it for a long press.
- **Keys**: Back, Home, Power, Menu, Mute and Volume.
- **Keyboard input**: type into TV search and text fields from your phone.
- **Minimal dark design**: black background with Liquid Glass controls and haptics.

Not ported from the Android app: ADB fallback, Quick Settings tile, update checker, AMOLED toggle and tutorial.

## Requirements

- iPhone on iOS 26 or later
- A TV running Android TV or Google TV on the same Wi-Fi network
- Developer Mode enabled on the iPhone (Settings > Privacy & Security > Developer Mode)

## Building

The project is a Swift package set up for [xtool](https://xtool.sh), so it builds and installs from Linux, WSL or macOS without opening Xcode.

```bash
git clone -b ios https://github.com/souravkl11/android-tv-remote.git
cd android-tv-remote
xtool setup     # once: sign in with your Apple ID and install the iOS SDK
xtool dev       # build, sign and install on a connected iPhone
```

Before installing, change `bundleID` in `xtool.yml` to an identifier of your own, because the one in the repository is a placeholder.

With a free Apple ID the app expires after 7 days and has to be installed again. A paid Apple Developer account lasts a year and allows TestFlight.

## Project layout

```
Sources/TVRemote/
  Protocol/   protobuf codec, TLS channel, client certificate, pairing and remote messages
  Model/      connection state machine and Bonjour discovery
  UI/         D-pad, buttons and sheets
  ContentView.swift
  TVRemoteApp.swift
Package.swift  xtool.yml  Info.plist  Icon.png
```

## License

Apache License 2.0. See [LICENSE](LICENSE).
