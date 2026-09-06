package com.unshoo.pixelmusic.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.worker.SyncProgress
import com.unshoo.pixelmusic.ui.theme.RoundedSans
import kotlin.math.roundToInt

/**
 * A professional progress indicator for library synchronization.
 * Shows current file count and progress percentage.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncProgressBar(
    syncProgress: SyncProgress,
    modifier: Modifier = Modifier,
    showCancelButton: Boolean = false,
    onCancel: () -> Unit = {}
) {
    val animatedProgress by animateFloatAsState(
        targetValue = syncProgress.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "progressAnimation"
    )

    val currentCount = syncProgress.currentCount
    val totalCount = syncProgress.totalCount
    val percentage = (animatedProgress * 100).roundToInt()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getPhaseText(syncProgress),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = RoundedSans,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = stringResource(R.string.presentation_batch_g_sync_percent, percentage),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = RoundedSans,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (totalCount > 0) {
                        stringResource(R.string.sync_files_progress, currentCount, totalCount)
                    } else {
                        stringResource(R.string.sync_scanning)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (syncProgress.isRunning) {
                    LoadingIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (syncProgress.isCompleted) {
                    Text(
                        text = stringResource(R.string.sync_complete),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            if (showCancelButton && syncProgress.isRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun getPhaseText(syncProgress: SyncProgress): String {
    return when {
        syncProgress.isCompleted -> stringResource(R.string.sync_complete)
        syncProgress.isRunning -> {
            when {
                syncProgress.totalCount == 0 -> stringResource(R.string.sync_scanning)
                syncProgress.hasProgress -> stringResource(R.string.sync_processing)
                else -> stringResource(R.string.sync_in_progress)
            }
        }
        else -> stringResource(R.string.sync_pending)
    }
}

/**
 * Compact progress indicator for use in smaller spaces.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CompactSyncProgressIndicator(
    syncProgress: SyncProgress,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = syncProgress.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "compactProgressAnimation"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LinearWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = stringResource(R.string.presentation_batch_g_sync_percent, (animatedProgress * 100).roundToInt()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
