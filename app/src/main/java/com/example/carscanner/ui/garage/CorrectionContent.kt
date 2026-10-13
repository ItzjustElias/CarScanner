package com.example.carscanner.ui.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.carscanner.data.CarEntity
import com.example.carscanner.data.CorrectionPreview
import com.example.carscanner.ui.components.RarityChip
import com.example.carscanner.ui.components.StatTile
import com.example.carscanner.ui.theme.Gold
import java.text.NumberFormat
import com.example.carscanner.ui.components.formatCompact
import com.example.carscanner.ui.components.formatCompactUsd

sealed interface CorrectionState {
    data object Idle : CorrectionState
    data object Loading : CorrectionState
    data class Preview(val result: CorrectionPreview) : CorrectionState
    data class Error(val message: String) : CorrectionState
}

@Composable
fun CorrectionContent(
    car: CarEntity,
    state: CorrectionState,
    onSearch: (String) -> Unit,
    onConfirm: (CorrectionPreview) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val loading = state is CorrectionState.Loading

    fun submit() {
        if (text.isNotBlank() && !loading) {
            focus.clearFocus()
            onSearch(text)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Not the right car?", style = MaterialTheme.typography.headlineMedium)
        Text(
            "It was scanned as ${car.title}. Type the correct make, model and year and the AI will fill in the rest.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. Porsche 911 GT3 2019") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = Color.Transparent,
            ),
        )
        Button(
            onClick = { submit() },
            enabled = text.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Color.White)
            else Text("Find this car")
        }

        when (state) {
            is CorrectionState.Error -> Text(
                state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            is CorrectionState.Preview -> PreviewCard(car, state.result, onConfirm)
            else -> Unit
        }

        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun PreviewCard(car: CarEntity, preview: CorrectionPreview, onConfirm: (CorrectionPreview) -> Unit) {
    val scan = preview.scan
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Is this the one?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${scan.make} ${scan.model}".trim(), style = MaterialTheme.typography.titleLarge)
        val sub = listOf(scan.yearText, scan.bodyStyle).filter { it.isNotBlank() }.joinToString(", ")
        if (sub.isNotEmpty()) {
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RarityChip(scan.rarity)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Value", scan.valueUsd?.let { formatCompactUsd(it) } ?: "Unknown", Modifier.weight(1f))
            scan.productionCount?.let { formatCompact(it, compactFrom = 100_000L) } ?: "Unknown"
            StatTile(
                "Built",
                scan.productionCount?.let { NumberFormat.getIntegerInstance().format(it) } ?: "Unknown",
                Modifier.weight(1f),
            )
        }
        if (scan.funFact.isNotBlank()) {
            Text(scan.funFact, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "Score: ${car.points} -> ${preview.points} pts",
            style = MaterialTheme.typography.titleMedium,
            color = Gold,
        )
        Button(onClick = { onConfirm(preview) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Use this car")
        }
    }
}
