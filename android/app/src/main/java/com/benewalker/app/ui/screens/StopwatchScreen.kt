package com.benewalker.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benewalker.app.ui.WalkViewModel

@Composable
fun StopwatchScreen(
    viewModel: WalkViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    // Pulse animation when running
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (uiState.stopwatchRunning) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val minutes = uiState.stopwatchElapsedSec / 60
    val seconds = uiState.stopwatchElapsedSec % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top: Target Selector & Sound Toggle
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.stopwatchTarget == "morning",
                        onClick = { viewModel.setStopwatchTarget("morning") },
                        label = { Text("1. Gehen", fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            if (uiState.stopwatchTarget == "morning") {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )

                    FilterChip(
                        selected = uiState.stopwatchTarget == "evening",
                        onClick = { viewModel.setStopwatchTarget("evening") },
                        label = { Text("2. Gehen", fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            if (uiState.stopwatchTarget == "evening") {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    )
                }
            }

            // Sound Toggle Chip (30s Piep & Minuten-Sprachansage)
            FilterChip(
                selected = uiState.stopwatchSoundEnabled,
                onClick = { viewModel.setStopwatchSoundEnabled(!uiState.stopwatchSoundEnabled) },
                label = {
                    Text(
                        text = if (uiState.stopwatchSoundEnabled) "Audio an (30s Piep & Min. Ansage)" else "Audio stumm",
                        fontSize = 12.sp,
                        fontWeight = if (uiState.stopwatchSoundEnabled) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (uiState.stopwatchSoundEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (uiState.stopwatchSoundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                },
                shape = RoundedCornerShape(10.dp)
            )
        }

        // Center: Animated Circular Display
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(270.dp)
        ) {
            // Pulsing background ring
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        if (uiState.stopwatchRunning) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
            )

            // Inner main circle
            Surface(
                modifier = Modifier.size(210.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = formattedTime,
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (uiState.stopwatchRunning) "Gehzeit läuft..." else "Bereit",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.stopwatchRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // Bottom: Controls
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset Button
                OutlinedIconButton(
                    onClick = { viewModel.resetStopwatch() },
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    enabled = uiState.stopwatchElapsedSec > 0
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Zurücksetzen")
                }

                // Play / Pause FAB
                FilledIconButton(
                    onClick = { viewModel.toggleStopwatch() },
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (uiState.stopwatchRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (uiState.stopwatchRunning) "Pause" else "Start",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                // Save Button
                FilledTonalIconButton(
                    onClick = { viewModel.saveStopwatchToToday() },
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    enabled = uiState.stopwatchElapsedSec > 0
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = "Speichern")
                }
            }

            if (uiState.stopwatchElapsedSec > 0 && !uiState.stopwatchRunning) {
                Button(
                    onClick = { viewModel.saveStopwatchToToday() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Für ${if (uiState.stopwatchTarget == "morning") "1. Gehen" else "2. Gehen"} heute speichern",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
