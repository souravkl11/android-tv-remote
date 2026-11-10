import SwiftUI
import UIKit

// MARK: - Haptics

@MainActor
enum Haptics {
    private static let light = UIImpactFeedbackGenerator(style: .light)
    private static let medium = UIImpactFeedbackGenerator(style: .medium)
    private static let rigid = UIImpactFeedbackGenerator(style: .rigid)
    private static let selection = UISelectionFeedbackGenerator()
    private static let notification = UINotificationFeedbackGenerator()

    static func tap() { light.impactOccurred() }
    static func click() { medium.impactOccurred() }
    static func heavy() { rigid.impactOccurred() }
    static func tick() { selection.selectionChanged() }
    static func success() { notification.notificationOccurred(.success) }
}

// MARK: - Buttons

/// A circular Liquid Glass button.
struct GlassCircleButton: View {
    let systemName: String
    var size: CGFloat = 48
    var iconScale: CGFloat = 0.4
    var tint: Color?
    var label: String
    let action: () -> Void

    var body: some View {
        Button {
            Haptics.tap()
            action()
        } label: {
            Image(systemName: systemName)
                .font(.system(size: size * iconScale, weight: .semibold))
                .frame(width: size, height: size)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.tint(tint).interactive(), in: Circle())
        .accessibilityLabel(label)
    }
}

/// A press-and-hold control that can auto-repeat while held (used for volume).
struct HoldButton<Content: View>: View {
    var repeatEvery: Double?
    let action: () -> Void
    @ViewBuilder let content: (Bool) -> Content

    @State private var pressed = false
    @State private var repeater: Task<Void, Never>?

    var body: some View {
        content(pressed)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in begin() }
                    .onEnded { _ in end() }
            )
            .onDisappear { end() }
    }

    private func begin() {
        guard !pressed else { return }
        pressed = true
        Haptics.tick()
        action()
        if let interval = repeatEvery {
            repeater = Task {
                try? await Task.sleep(nanoseconds: 350_000_000)
                while !Task.isCancelled {
                    Haptics.tick()
                    action()
                    try? await Task.sleep(nanoseconds: UInt64(interval * 1_000_000_000))
                }
            }
        }
    }

    private func end() {
        pressed = false
        repeater?.cancel()
        repeater = nil
    }
}
