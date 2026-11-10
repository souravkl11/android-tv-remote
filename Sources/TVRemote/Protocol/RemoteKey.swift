import Foundation

/// Android key codes understood by the Android TV Remote v2 service.
enum RemoteKey: Int {
    case home = 3
    case back = 4
    case dpadUp = 19
    case dpadDown = 20
    case dpadLeft = 21
    case dpadRight = 22
    case dpadCenter = 23
    case volumeUp = 24
    case volumeDown = 25
    case power = 26
    case enter = 66
    case backspace = 67
    case menu = 82
    case assistant = 84
    case volumeMute = 164
    case settings = 176
    case tvInput = 178
}

enum KeyDirection: Int {
    case startLong = 1
    case endLong = 2
    case short = 3
}
