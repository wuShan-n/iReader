package com.ireader.feature.reader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.DocumentCapabilities

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    capabilities: DocumentCapabilities?,
    config: RenderConfig?,
    onClose: () -> Unit,
    onApply: (RenderConfig, persist: Boolean) -> Unit
) {
    var persist by remember { mutableStateOf(true) }
    val fixedLayout = capabilities?.fixedLayout == true

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Reading settings")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(
                    checked = persist,
                    onCheckedChange = { persist = it }
                )
                Text("Save as default")
            }

            if (fixedLayout) {
                val current = (config as? RenderConfig.FixedPage) ?: RenderConfig.FixedPage()
                var zoom by remember(current.zoom) { mutableFloatStateOf(current.zoom) }
                var rotation by remember(current.rotationDegrees) { mutableIntStateOf(current.rotationDegrees) }
                var fitMode by remember(current.fitMode) { mutableStateOf(current.fitMode) }

                Text("Zoom: ${"%.2f".format(zoom)}")
                Slider(
                    value = zoom,
                    valueRange = 0.6f..4.0f,
                    onValueChange = {
                        zoom = it
                        onApply(current.copy(zoom = it, rotationDegrees = rotation, fitMode = fitMode), false)
                    }
                )

                Text("Rotation")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 90, 180, 270).forEach { degree ->
                        TextButton(onClick = {
                            rotation = degree
                            onApply(current.copy(zoom = zoom, rotationDegrees = degree, fitMode = fitMode), false)
                        }) {
                            Text("$degree")
                        }
                    }
                }

                Text("Fit mode")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RenderConfig.FitMode.entries.forEach { mode ->
                        TextButton(onClick = {
                            fitMode = mode
                            onApply(current.copy(zoom = zoom, rotationDegrees = rotation, fitMode = mode), false)
                        }) {
                            Text(mode.name)
                        }
                    }
                }

                TextButton(
                    onClick = {
                        onApply(
                            current.copy(zoom = zoom, rotationDegrees = rotation, fitMode = fitMode),
                            persist
                        )
                    }
                ) {
                    Text("Apply")
                }
            } else {
                val current = (config as? RenderConfig.ReflowText) ?: RenderConfig.ReflowText()
                var fontSize by remember(current.fontSizeSp) { mutableFloatStateOf(current.fontSizeSp) }
                var lineHeight by remember(current.lineHeightMult) { mutableFloatStateOf(current.lineHeightMult) }
                var pagePadding by remember(current.pagePaddingDp) { mutableFloatStateOf(current.pagePaddingDp) }

                Text("Font size: ${fontSize.toInt()}sp")
                Slider(
                    value = fontSize,
                    valueRange = 12f..30f,
                    onValueChange = {
                        fontSize = it
                        onApply(
                            current.copy(
                                fontSizeSp = it,
                                lineHeightMult = lineHeight,
                                pagePaddingDp = pagePadding
                            ),
                            false
                        )
                    }
                )

                Text("Line height: ${"%.2f".format(lineHeight)}")
                Slider(
                    value = lineHeight,
                    valueRange = 1.1f..2.2f,
                    onValueChange = {
                        lineHeight = it
                        onApply(
                            current.copy(
                                fontSizeSp = fontSize,
                                lineHeightMult = it,
                                pagePaddingDp = pagePadding
                            ),
                            false
                        )
                    }
                )

                Text("Page padding: ${pagePadding.toInt()}dp")
                Slider(
                    value = pagePadding,
                    valueRange = 8f..36f,
                    onValueChange = {
                        pagePadding = it
                        onApply(
                            current.copy(
                                fontSizeSp = fontSize,
                                lineHeightMult = lineHeight,
                                pagePaddingDp = it
                            ),
                            false
                        )
                    }
                )

                TextButton(
                    onClick = {
                        onApply(
                            current.copy(
                                fontSizeSp = fontSize,
                                lineHeightMult = lineHeight,
                                pagePaddingDp = pagePadding
                            ),
                            persist
                        )
                    }
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

