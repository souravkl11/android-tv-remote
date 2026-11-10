package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.TvOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.protocol.ConnectionStatus

/**
 * Takes the pad's place whenever the remote can't control a TV: says what is going on in plain
 * words and puts the one useful next step under the thumb.
 */
@Composable
fun ConnectionStage(
    status: ConnectionStatus,
    deviceName: String,
    hasTv: Boolean,
    isScanning: Boolean,
    onRetry: () -> Unit,
    onChooseTv: () -> Unit,
    onRePair: () -> Unit,
    onEnterCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isError = status is ConnectionStatus.Error

    val title: String
    val body: String
    when (status) {
        is ConnectionStatus.Connecting -> {
            title = "Connecting to $deviceName"
            body = "Hang tight, this usually takes a second."
        }
        is ConnectionStatus.NeedsPairing -> {
            title = "Check your TV"
            body = "A 6-character code is on the TV screen. Enter it here to pair this phone."
        }
        is ConnectionStatus.Error -> {
            title = "Can't reach $deviceName"
            body = "It may be off, busy with another remote, or no longer paired with this phone."
        }
        else -> if (hasTv) {
            title = "Not connected"
            body = "$deviceName isn't connected right now."
        } else {
            title = "Let's find your TV"
            body = if (isScanning) "Looking for Android TVs on your Wi-Fi…"
            else "Put your phone and TV on the same Wi-Fi, then pick it from the list."
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(40.dp))
            .background(scheme.surfaceContainerLow)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Hero shape
        Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            when {
                status is ConnectionStatus.Connecting -> Searching()
                status is ConnectionStatus.NeedsPairing ->
                    ShapeBox(Polygons.Clover, scheme.tertiaryContainer, Modifier.fillMaxSize()) {
                        Icon(Icons.Rounded.Dialpad, null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(44.dp))
                    }
                isError ->
                    ShapeBox(Polygons.Burst, scheme.errorContainer, Modifier.fillMaxSize()) {
                        Icon(Icons.Rounded.TvOff, null, tint = scheme.onErrorContainer, modifier = Modifier.size(44.dp))
                    }
                !hasTv && isScanning -> Searching()
                else ->
                    ShapeBox(Polygons.Cookie, scheme.primaryContainer, Modifier.fillMaxSize()) {
                        Icon(Icons.Rounded.Tv, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(44.dp))
                    }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = scheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        when (status) {
            is ConnectionStatus.Connecting ->
                ExpressiveButton("Choose another TV", onChooseTv, tone = ButtonTone.Tonal)

            is ConnectionStatus.NeedsPairing -> {
                ExpressiveButton("Enter code", onEnterCode, icon = Icons.Rounded.Dialpad, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                ExpressiveButton("Choose another TV", onChooseTv, tone = ButtonTone.Plain, height = 48.dp)
            }

            is ConnectionStatus.Error -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ExpressiveButton("Try again", onRetry, icon = Icons.Rounded.Refresh, modifier = Modifier.weight(1.2f))
                    ExpressiveButton("Pair again", onRePair, tone = ButtonTone.Tonal, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                ExpressiveButton("Choose another TV", onChooseTv, tone = ButtonTone.Plain, height = 48.dp)
            }

            else -> if (hasTv) {
                ExpressiveButton("Connect", onRetry, icon = Icons.Rounded.Refresh, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                ExpressiveButton("Choose another TV", onChooseTv, tone = ButtonTone.Plain, height = 48.dp)
            } else {
                ExpressiveButton("Choose TV", onChooseTv, icon = Icons.Rounded.Search, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Searching() {
    val scheme = MaterialTheme.colorScheme
    LoadingMorph(color = scheme.primaryContainer, size = 132.dp)
    Icon(Icons.Rounded.Tv, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(44.dp))
}
