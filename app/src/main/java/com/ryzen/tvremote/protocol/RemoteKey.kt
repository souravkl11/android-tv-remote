package com.ryzen.tvremote.protocol

import com.ryzen.tvremote.protocol.remote.RemoteKeyCode

enum class RemoteKey(
    val displayName: String,
    val protoKeyCode: RemoteKeyCode,
    val androidKeyCode: Int
) {
    DPAD_UP("Up", RemoteKeyCode.KEYCODE_DPAD_UP, 19),
    DPAD_DOWN("Down", RemoteKeyCode.KEYCODE_DPAD_DOWN, 20),
    DPAD_LEFT("Left", RemoteKeyCode.KEYCODE_DPAD_LEFT, 21),
    DPAD_RIGHT("Right", RemoteKeyCode.KEYCODE_DPAD_RIGHT, 22),
    DPAD_CENTER("Select", RemoteKeyCode.KEYCODE_DPAD_CENTER, 23),
    BACK("Back", RemoteKeyCode.KEYCODE_BACK, 4),
    HOME("Home", RemoteKeyCode.KEYCODE_HOME, 3),
    POWER("Power", RemoteKeyCode.KEYCODE_POWER, 26),
    VOLUME_UP("Volume Up", RemoteKeyCode.KEYCODE_VOLUME_UP, 24),
    VOLUME_DOWN("Volume Down", RemoteKeyCode.KEYCODE_VOLUME_DOWN, 25),
    VOLUME_MUTE("Mute", RemoteKeyCode.KEYCODE_VOLUME_MUTE, 164),
    SEARCH("Assistant", RemoteKeyCode.KEYCODE_SEARCH, 84),
    TV_INPUT("Input", RemoteKeyCode.KEYCODE_TV_INPUT, 178),
    MENU("Menu", RemoteKeyCode.KEYCODE_MENU, 82),
    SETTINGS("Settings", RemoteKeyCode.KEYCODE_SETTINGS, 176),
    BACKSPACE("Backspace", RemoteKeyCode.KEYCODE_DEL, 67),
    ENTER("Enter", RemoteKeyCode.KEYCODE_ENTER, 66)
}
