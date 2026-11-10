import SwiftUI

/// The navigation pad: tap a direction, drag the glass puck to scroll with
/// auto-repeat, tap the puck for OK, or hold it for a long-press.
struct DPadView: View {
    @EnvironmentObject private var remote: TVRemote
    let diameter: CGFloat

    @State private var offset: CGSize = .zero
    @State private var isActive = false
    @State private var isDragging = false
    @State private var startedOnCenter = false
    @State private var startKey: RemoteKey?
    @State private var repeatKey: RemoteKey?
    @State private var longPressing = false
    @State private var longPressTask: Task<Void, Never>?
    @State private var repeatTask: Task<Void, Never>?

    private var radius: CGFloat { diameter / 2 }
    private var puckSize: CGFloat { diameter * 0.34 }
    private var maxTravel: CGFloat { radius - puckSize / 2 - 8 }
    private var triggerDistance: CGFloat { maxTravel * 0.5 }
    private var centerHitRadius: CGFloat { puckSize * 0.65 }

    var body: some View {
        // No GlassEffectContainer here: it would blend the puck into the ring.
        Group {
            ZStack {
                Circle()
                    .fill(.clear)
                    .frame(width: diameter, height: diameter)
                    .glassEffect(.regular, in: Circle())
                    .overlay(Circle().strokeBorder(.white.opacity(0.14), lineWidth: 1))

                chevron("chevron.up", key: .dpadUp, x: 0, y: -1)
                chevron("chevron.down", key: .dpadDown, x: 0, y: 1)
                chevron("chevron.left", key: .dpadLeft, x: -1, y: 0)
                chevron("chevron.right", key: .dpadRight, x: 1, y: 0)

                Text("OK")
                    .font(.system(size: puckSize * 0.3, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .frame(width: puckSize, height: puckSize)
                    .glassEffect(.regular.tint(.white.opacity(0.3)), in: Circle())
                    .overlay(Circle().strokeBorder(.white.opacity(0.45), lineWidth: 1.5))
                    .scaleEffect(puckScale)
                    .offset(offset)
                    .animation(.spring(response: 0.25, dampingFraction: 0.7), value: puckScale)
            }
            .frame(width: diameter, height: diameter)
            .contentShape(Circle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged(handleChanged)
                    .onEnded { _ in handleEnded() }
            )
        }
        .frame(width: diameter, height: diameter)
        .onDisappear { reset(animated: false) }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Directional pad")
        .accessibilityAdjustableAction { direction in
            remote.send(direction == .increment ? .dpadRight : .dpadLeft)
        }
    }

    private var puckScale: CGFloat {
        if isDragging { return 1.08 }
        if isActive && startedOnCenter { return 0.93 }
        return 1
    }

    private func chevron(_ name: String, key: RemoteKey, x: CGFloat, y: CGFloat) -> some View {
        let highlighted = startKey == key || repeatKey == key
        // Halfway between the puck's edge and the ring's edge.
        let distance = (radius + puckSize / 2) / 2 + 2
        return Image(systemName: name)
            .font(.system(size: diameter * 0.09, weight: .semibold))
            .foregroundStyle(Color.white.opacity(highlighted ? 1 : 0.7))
            .scaleEffect(highlighted ? 1.3 : 1)
            .offset(x: x * distance, y: y * distance)
            .animation(.spring(response: 0.22, dampingFraction: 0.65), value: highlighted)
            .allowsHitTesting(false)
    }

    private static func direction(dx: CGFloat, dy: CGFloat) -> RemoteKey {
        if abs(dx) > abs(dy) {
            return dx > 0 ? .dpadRight : .dpadLeft
        }
        return dy > 0 ? .dpadDown : .dpadUp
    }

    private func handleChanged(_ value: DragGesture.Value) {
        if !isActive {
            isActive = true
            isDragging = false
            longPressing = false

            let dx = value.startLocation.x - radius
            let dy = value.startLocation.y - radius
            startedOnCenter = hypot(dx, dy) <= centerHitRadius

            if startedOnCenter {
                startKey = nil
                longPressTask = Task {
                    try? await Task.sleep(nanoseconds: 400_000_000)
                    if !Task.isCancelled && !isDragging {
                        longPressing = true
                        Haptics.heavy()
                        remote.send(.dpadCenter, .startLong)
                    }
                }
            } else {
                startKey = Self.direction(dx: dx, dy: dy)
            }
        }

        let t = value.translation
        let distance = hypot(t.width, t.height)

        if !isDragging && distance > 10 {
            isDragging = true
            startKey = nil
            longPressTask?.cancel()
            longPressTask = nil
            if longPressing {
                remote.send(.dpadCenter, .endLong)
                longPressing = false
            }
        }
        guard isDragging else { return }

        let scale = distance > maxTravel ? maxTravel / distance : 1
        let clamped = CGSize(width: t.width * scale, height: t.height * scale)
        offset = clamped

        let magnitude = hypot(clamped.width, clamped.height)
        if magnitude >= triggerDistance {
            let key = Self.direction(dx: clamped.width, dy: clamped.height)
            if repeatKey != key {
                repeatKey = key
                repeatTask?.cancel()
                repeatTask = Task {
                    Haptics.tick()
                    remote.send(key)
                    try? await Task.sleep(nanoseconds: 320_000_000)
                    while !Task.isCancelled {
                        Haptics.tick()
                        remote.send(key)
                        try? await Task.sleep(nanoseconds: 175_000_000)
                    }
                }
            }
        } else if magnitude < triggerDistance * 0.6 {
            repeatKey = nil
            repeatTask?.cancel()
            repeatTask = nil
        }
    }

    private func handleEnded() {
        longPressTask?.cancel()
        repeatTask?.cancel()

        if longPressing {
            remote.send(.dpadCenter, .endLong)
        } else if !isDragging {
            if startedOnCenter {
                Haptics.click()
                remote.send(.dpadCenter)
            } else if let key = startKey {
                Haptics.tick()
                remote.send(key)
            }
        }
        reset(animated: true)
    }

    private func reset(animated: Bool) {
        longPressTask?.cancel()
        longPressTask = nil
        repeatTask?.cancel()
        repeatTask = nil
        isActive = false
        isDragging = false
        longPressing = false
        startKey = nil
        repeatKey = nil
        if animated {
            withAnimation(.spring(response: 0.4, dampingFraction: 0.6)) { offset = .zero }
        } else {
            offset = .zero
        }
    }
}
