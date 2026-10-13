package com.example.carscanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.carscanner.data.CarEntity
import com.example.carscanner.domain.Rarity
import com.example.carscanner.ui.theme.rarityBrush
import java.io.File
import java.text.NumberFormat

@Composable
fun RarityDot(rarity: Rarity, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(rarityBrush(rarity)))
}

@Composable
fun RarityChip(rarity: Rarity, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(rarityBrush(rarity, alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RarityDot(rarity)
        Spacer(Modifier.width(6.dp))
        Text(
            rarity.label,
            style = MaterialTheme.typography.labelMedium.copy(
                brush = rarityBrush(rarity),
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Composable
fun CarDetailContent(car: CarEntity, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AsyncImage(
            model = File(car.imagePath),
            contentDescription = car.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(MaterialTheme.shapes.large),
        )
        Column {
            Text(car.title, style = MaterialTheme.typography.headlineMedium)
            val sub = listOf(car.yearText, car.bodyStyle).filter { it.isNotBlank() }.joinToString(", ")
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Value", car.valueUsd?.let { formatCompactUsd(it) } ?: "Unknown", Modifier.weight(1f))
            car.productionCount?.let { formatCompact(it, compactFrom = 100_000L) } ?: "Unknown"
            StatTile(
                "Built",
                car.productionCount?.let { NumberFormat.getIntegerInstance().format(it) } ?: "Unknown",
                Modifier.weight(1f),
            )
            StatTile("Certainty", "${(car.confidence * 100).toInt()}%", Modifier.weight(1f))
        }
        if (car.funFact.isNotBlank()) {
            Text(
                car.funFact,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
            )
        }
    }
}