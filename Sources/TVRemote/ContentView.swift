import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var remote: TVRemote
    @EnvironmentObject private var discovery: TVDiscovery
    @Environment(\.scenePhase) private var scenePhase

    @State private var showDevices = false
    @State private var showKeyboard = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            GeometryReader { geo in
                let dpad = max(220, min(geo.size.width - 56, 330, geo.size.height * 0.4))

                VStack(spacing: 0) {
                    HeaderBar(showDevices: $showDevices, showKeyboard: $showKeyboard)
                        .padding(.top, 6)

                    ConnectionBanner(showDevices: $showDevices)
                        .padding(.top, 14)

                    Spacer(minLength: 12)

                    DPadView(diameter: dpad)
                        .opacity(remote.isConnected ? 1 : 0.5)
                        .animation(.smooth, value: remote.isConnected)

                    Spacer(minLength: 12)

                    NavigationButtons()

                    Spacer(minLength: 12)

                    VolumeBar()
                        .padding(.bottom, 10)
                }
                .padding(.horizontal, 20)
                .frame(width: geo.size.width, height: geo.size.height)
            }
        }
        .sheet(isPresented: $showDevices) { DevicePickerSheet() }
        .sheet(isPresented: $showKeyboard) { KeyboardSheet() }
        .sheet(isPresented: pairingBinding) { PairingSheet() }
        .task {
            discovery.start()
            remote.reconnectIfNeeded()
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active:
                discovery.start()
                remote.reconnectIfNeeded()
            case .background:
                if remote.status != .needsPairing { remote.disconnect() }
                discovery.stop()
            default:
                break
            }
        }
        .onChange(of: discovery.tvs) { _, tvs in
            autoConnect(to: tvs)
        }
    }

    private var pairingBinding: Binding<Bool> {
        Binding(
            get: { remote.status == .needsPairing },
            set: { presented in
                if !presented && remote.status == .needsPairing { remote.cancelPairing() }
            }
        )
    }

    /// Reconnects to the last TV when it shows up, or picks the only TV on the network.
    private func autoConnect(to tvs: [DiscoveredTV]) {
        guard remote.canReconnect, !tvs.isEmpty else { return }

        if let known = tvs.first(where: { $0.host == remote.host || $0.name == remote.deviceName }) {
            remote.connect(host: known.host, name: known.name)
        } else if remote.host.isEmpty {
            if tvs.count == 1, let only = tvs.first {
                remote.connect(host: only.host, name: only.name)
            } else if tvs.count > 1 {
                showDevices = true
            }
        }
    }
}

// MARK: - Header

private struct HeaderBar: View {
    @EnvironmentObject private var remote: TVRemote
    @Binding var showDevices: Bool
    @Binding var showKeyboard: Bool

    var body: some View {
        HStack(spacing: 10) {
            Button {
                Haptics.tap()
                showDevices = true
            } label: {
                HStack(spacing: 10) {
                    Circle()
                        .fill(statusColor)
                        .frame(width: 9, height: 9)
                        .shadow(color: statusColor.opacity(0.8), radius: 4)

                    VStack(alignment: .leading, spacing: 1) {
                        Text(remote.host.isEmpty ? "No TV" : remote.deviceName)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                        Text(statusText)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }

                    Image(systemName: "chevron.down")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 16)
                .frame(height: 48)
                .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .glassEffect(.regular.interactive(), in: Capsule())

            Spacer(minLength: 0)

            GlassEffectContainer(spacing: 10) {
                HStack(spacing: 10) {
                    GlassCircleButton(systemName: "keyboard", label: "Keyboard") {
                        showKeyboard = true
                    }
                    GlassCircleButton(systemName: "power", tint: .red.opacity(0.5), label: "Power") {
                        remote.send(.power)
                    }
                    moreMenu
                }
            }
        }
    }

    private var moreMenu: some View {
        Menu {
            Button { remote.send(.tvInput) } label: {
                Label("Input Source", systemImage: "rectangle.on.rectangle")
            }
            Button { remote.send(.settings) } label: {
                Label("TV Settings", systemImage: "gearshape")
            }
            Button { remote.send(.assistant) } label: {
                Label("Assistant", systemImage: "sparkles")
            }
            Button { remote.send(.menu) } label: {
                Label("Menu", systemImage: "line.3.horizontal")
            }
            Divider()
            Button { showDevices = true } label: {
                Label("Switch TV…", systemImage: "tv")
            }
            Button { remote.rePair() } label: {
                Label("Pair Again", systemImage: "link")
            }
            .disabled(remote.host.isEmpty)
        } label: {
            Image(systemName: "ellipsis")
                .font(.system(size: 19, weight: .semibold))
                .frame(width: 48, height: 48)
                .contentShape(Circle())
                .glassEffect(.regular.interactive(), in: Circle())
        }
        .accessibilityLabel("More")
    }

    private var statusColor: Color {
        switch remote.status {
        case .connected: return .green
        case .connecting, .needsPairing: return .orange
        case .error: return .red
        case .disconnected: return .gray
        }
    }

    private var statusText: String {
        switch remote.status {
        case .connected: return "Connected"
        case .connecting: return "Connecting…"
        case .needsPairing: return "Pairing"
        case .error: return "Offline"
        case .disconnected: return remote.host.isEmpty ? "Tap to choose" : "Disconnected"
        }
    }
}

// MARK: - Status banner

private struct ConnectionBanner: View {
    @EnvironmentObject private var remote: TVRemote
    @Binding var showDevices: Bool

    var body: some View {
        Group {
            switch remote.status {
            case .connected:
                EmptyView()
            case .connecting(let message):
                banner(icon: nil, text: message, action: nil)
            case .needsPairing:
                banner(icon: "number", text: "Enter the code shown on your TV.", action: nil)
            case .error(let message):
                banner(icon: "exclamationmark.triangle.fill", text: message, action: ("Retry", { remote.reconnectIfNeeded() }))
            case .disconnected:
                if remote.host.isEmpty {
                    banner(icon: "tv", text: "Pick your TV to get started.", action: ("Choose TV", { showDevices = true }))
                } else {
                    banner(icon: "wifi.slash", text: "Not connected to \(remote.deviceName).", action: ("Reconnect", { remote.reconnectIfNeeded() }))
                }
            }
        }
        .animation(.smooth, value: remote.status)
    }

    private func banner(icon: String?, text: String, action: (String, () -> Void)?) -> some View {
        HStack(spacing: 12) {
            if let icon {
                Image(systemName: icon)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.secondary)
            } else {
                ProgressView()
            }
            Text(text)
                .font(.footnote)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)

            if let action {
                Button(action.0) {
                    Haptics.tap()
                    action.1()
                }
                .buttonStyle(.glass)
                .controlSize(.small)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .transition(.scale(scale: 0.96).combined(with: .opacity))
    }
}

// MARK: - Back / Home

private struct NavigationButtons: View {
    @EnvironmentObject private var remote: TVRemote

    var body: some View {
        GlassEffectContainer(spacing: 40) {
            HStack(spacing: 44) {
                GlassCircleButton(systemName: "arrow.uturn.backward", size: 68, iconScale: 0.36, label: "Back") {
                    remote.send(.back)
                }
                GlassCircleButton(systemName: "house.fill", size: 68, iconScale: 0.38, label: "Home") {
                    remote.send(.home)
                }
            }
        }
        .opacity(remote.isConnected ? 1 : 0.5)
        .animation(.smooth, value: remote.isConnected)
    }
}

// MARK: - Volume

private struct VolumeBar: View {
    @EnvironmentObject private var remote: TVRemote

    var body: some View {
        HStack(spacing: 0) {
            HoldButton(action: { remote.send(.volumeMute) }) { pressed in
                Image(systemName: remote.volume.muted ? "speaker.slash.fill" : "speaker.fill")
                    .font(.system(size: 21, weight: .semibold))
                    .foregroundStyle(remote.volume.muted ? Color.red : Color.primary)
                    .scaleEffect(pressed ? 0.88 : 1)
                    .animation(.spring(response: 0.2, dampingFraction: 0.6), value: pressed)
            }
            .accessibilityLabel("Mute")

            divider

            HoldButton(repeatEvery: 0.14, action: { remote.send(.volumeDown) }) { pressed in
                Image(systemName: "speaker.wave.1.fill")
                    .font(.system(size: 21, weight: .semibold))
                    .scaleEffect(pressed ? 0.88 : 1)
                    .animation(.spring(response: 0.2, dampingFraction: 0.6), value: pressed)
            }
            .accessibilityLabel("Volume down")

            divider

            HoldButton(repeatEvery: 0.14, action: { remote.send(.volumeUp) }) { pressed in
                Image(systemName: "speaker.wave.3.fill")
                    .font(.system(size: 21, weight: .semibold))
                    .scaleEffect(pressed ? 0.88 : 1)
                    .animation(.spring(response: 0.2, dampingFraction: 0.6), value: pressed)
            }
            .accessibilityLabel("Volume up")
        }
        .frame(height: 68)
        .glassEffect(.regular.interactive(), in: Capsule())
        .opacity(remote.isConnected ? 1 : 0.5)
        .animation(.smooth, value: remote.isConnected)
    }

    private var divider: some View {
        Capsule()
            .fill(.secondary.opacity(0.25))
            .frame(width: 1, height: 26)
    }
}
