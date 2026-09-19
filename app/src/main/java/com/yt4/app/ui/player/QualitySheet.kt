package com.yt4.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yt4.app.data.model.QualitySelection
import com.yt4.app.data.model.StreamBundle

/**
 * Quality selector. Auto = adaptive-with-fallback (see AdaptiveFallback);
 * manual entries pin a DASH height; muxed rows are exposed for forced
 * low-bandwidth mode (single progressive file, no merge overhead).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(
    bundle: StreamBundle,
    selection: QualitySelection,
    onSelect: (QualitySelection) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Quality",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        LazyColumn(modifier = Modifier.padding(bottom = 24.dp)) {
            item(key = "auto", contentType = "q") {
                QualityRow(
                    label = "Auto (adaptive, falls back on stalls)",
                    selected = selection.isAuto,
                    onClick = {
                        onSelect(QualitySelection.Auto)
                        onDismiss()
                    },
                )
            }
            items(
                items = bundle.videoOnly,
                key = { "vo_${it.height}_${it.bitrate}" },
                contentType = { "q" },
            ) { option ->
                QualityRow(
                    label = "${option.label} · ${option.bitrate} kbps",
                    selected = selection.targetHeight == option.height,
                    onClick = {
                        onSelect(QualitySelection(option.height))
                        onDismiss()
                    },
                )
            }
            items(
                items = bundle.muxed,
                key = { "mx_${it.height}" },
                contentType = { "q" },
            ) { option ->
                QualityRow(
                    label = "${option.label} (compat · single stream)",
                    selected = selection.targetHeight == option.height,
                    onClick = {
                        onSelect(QualitySelection(option.height))
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun QualityRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
