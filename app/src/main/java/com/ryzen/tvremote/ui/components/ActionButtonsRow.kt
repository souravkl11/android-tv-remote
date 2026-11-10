package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.protocol.RemoteKey

/** Back · Home · Keyboard · Input, evenly spaced and labelled. */
@Composable
fun ActionButtonsRow(
    onKey: (RemoteKey) -> Unit,
    onKeyboardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        RoundButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", { onKey(RemoteKey.BACK) }, label = "Back")
        RoundButton(Icons.Default.Home, "Home", { onKey(RemoteKey.HOME) }, label = "Home")
        RoundButton(Icons.Default.Keyboard, "Keyboard", onKeyboardClick, label = "Type")
        RoundButton(Icons.AutoMirrored.Filled.Input, "Input source", { onKey(RemoteKey.TV_INPUT) }, label = "Input")
    }
}
