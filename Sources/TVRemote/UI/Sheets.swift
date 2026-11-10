import SwiftUI

// MARK: - Choose a TV

struct DevicePickerSheet: View {
    @EnvironmentObject private var remote: TVRemote
    @EnvironmentObject private var discovery: TVDiscovery
    @Environment(\.dismiss) private var dismiss

    @State private var manualHost = ""

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(discovery.tvs) { tv in
                        Button {
                            remote.connect(host: tv.host, name: tv.name)
                            dismiss()
                        } label: {
                            HStack(spacing: 14) {
                                Image(systemName: "tv")
                                    .font(.title3)
                                    .foregroundStyle(.secondary)
                                    .frame(width: 30)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(tv.name).foregroundStyle(.primary)
                                    Text(tv.host)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                if tv.host == remote.host {
                                    Image(systemName: "checkmark").foregroundStyle(.primary)
                                }
                            }
                        }
                    }

                    if discovery.tvs.isEmpty {
                        if let failure = discovery.failure {
                            Label(failure, systemImage: "exclamationmark.triangle")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        } else if discovery.isScanning {
                            HStack(spacing: 12) {
                                ProgressView()
                                Text("Searching your network…").foregroundStyle(.secondary)
                            }
                        }
                    }
                } header: {
                    Text("Nearby TVs")
                } footer: {
                    Text("Your iPhone and TV need to be on the same Wi-Fi network.")
                }

                Section("Connect by IP address") {
                    TextField("192.168.1.20", text: $manualHost)
                        .keyboardType(.numbersAndPunctuation)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Button("Connect") {
                        remote.connect(host: manualHost, name: "Android TV")
                        dismiss()
                    }
                    .disabled(manualHost.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .navigationTitle("Choose a TV")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        discovery.restart()
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                    .accessibilityLabel("Search again")
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

// MARK: - Pairing code

struct PairingSheet: View {
    @EnvironmentObject private var remote: TVRemote
    @State private var pin = ""
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "tv")
                .font(.system(size: 38, weight: .light))
                .foregroundStyle(.secondary)
                .padding(.top, 8)

            VStack(spacing: 6) {
                Text("Pair with \(remote.deviceName)")
                    .font(.title2.weight(.bold))
                Text("Enter the 6-character code shown on your TV.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            TextField("", text: $pin, prompt: Text("······"))
                .font(.system(size: 34, weight: .semibold, design: .monospaced))
                .multilineTextAlignment(.center)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .keyboardType(.asciiCapable)
                .focused($focused)
                .padding(.vertical, 14)
                .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                .onChange(of: pin) { _, newValue in
                    let cleaned = String(newValue.uppercased().filter(\.isHexDigit).prefix(6))
                    if cleaned != newValue { pin = cleaned }
                    remote.pinError = nil
                }
                .onSubmit(submit)

            Text(remote.pinError ?? " ")
                .font(.footnote)
                .foregroundStyle(.red)
                .multilineTextAlignment(.center)

            HStack(spacing: 12) {
                Button("Cancel") { remote.cancelPairing() }
                    .buttonStyle(.glass)
                Button("Pair", action: submit)
                    .buttonStyle(.glassProminent)
                    .disabled(pin.count != 6)
            }
            .controlSize(.large)
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 12)
        .presentationDetents([.height(440)])
        .onAppear { focused = true }
    }

    private func submit() {
        guard pin.count == 6 else { return }
        remote.submitPIN(pin)
    }
}

// MARK: - Type on TV

struct KeyboardSheet: View {
    @EnvironmentObject private var remote: TVRemote
    @Environment(\.dismiss) private var dismiss

    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text("Type on TV")
                    .font(.title3.weight(.bold))
                Spacer()
                HStack(spacing: 6) {
                    Circle().fill(.green).frame(width: 7, height: 7)
                    Text("Live")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
            }

            Text("What you type appears on the TV as you go.")
                .font(.footnote)
                .foregroundStyle(.secondary)

            TextField("Start typing…", text: $text)
                .focused($focused)
                .submitLabel(.search)
                .autocorrectionDisabled()
                .padding(.horizontal, 18)
                .frame(height: 52)
                .glassEffect(.regular, in: Capsule())
                .onChange(of: text) { old, new in sync(from: old, to: new) }
                .onSubmit {
                    remote.send(.enter)
                    dismiss()
                }

            HStack(spacing: 10) {
                Button {
                    if text.isEmpty { remote.send(.backspace) } else { text.removeLast() }
                } label: {
                    Label("Delete", systemImage: "delete.left")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.glass)

                Button {
                    text = ""
                } label: {
                    Text("Clear").frame(maxWidth: .infinity)
                }
                .buttonStyle(.glass)
                .disabled(text.isEmpty)

                Button {
                    remote.send(.enter)
                    dismiss()
                } label: {
                    Label("Search", systemImage: "return")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.glassProminent)
            }
            .controlSize(.large)
        }
        .padding(.horizontal, 24)
        .padding(.top, 8)
        .presentationDetents([.height(300)])
        .onAppear { focused = true }
    }

    /// Sends only what changed: backspaces for removed characters, then the new characters.
    private func sync(from old: String, to new: String) {
        let oldChars = Array(old)
        let newChars = Array(new)
        var common = 0
        while common < oldChars.count, common < newChars.count, oldChars[common] == newChars[common] {
            common += 1
        }
        for _ in 0..<(oldChars.count - common) {
            remote.send(.backspace)
        }
        let added = String(newChars[common...])
        if !added.isEmpty { remote.sendText(added) }
    }
}
