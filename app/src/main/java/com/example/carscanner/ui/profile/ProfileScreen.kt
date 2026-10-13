package com.example.carscanner.ui.profile

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.carscanner.CarScannerApp
import com.example.carscanner.data.CarEntity
import com.example.carscanner.data.ProfileStats
import com.example.carscanner.domain.Levels
import com.example.carscanner.domain.Rarity
import com.example.carscanner.ui.components.ConfirmDialog
import com.example.carscanner.ui.components.RarityChip
import com.example.carscanner.ui.components.StatTile
import com.example.carscanner.ui.components.formatCompactUsd
import com.example.carscanner.ui.theme.Gold
import com.example.carscanner.ui.theme.rarityBrush
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as CarScannerApp).container.repository
    private val userId = MutableStateFlow("")

    val stats: StateFlow<ProfileStats?> = userId
        .flatMapLatest { if (it.isBlank()) flowOf<ProfileStats?>(null) else repo.profileStats(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setUser(id: String) { userId.value = id }
}

@Composable
fun ProfileScreen(
    user: FirebaseUser,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    vm: ProfileViewModel = viewModel(),
) {
    LaunchedEffect(user.uid) { vm.setUser(user.uid) }
    val loaded by vm.stats.collectAsStateWithLifecycle()
    val stats = loaded ?: ProfileStats()
    var confirmLogout by remember { mutableStateOf(false) }

    val level = Levels.forPoints(stats.points)
    val number = NumberFormat.getIntegerInstance()

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Profile", style = MaterialTheme.typography.titleLarge)
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = user.photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.height(12.dp))
            Text(user.displayName ?: "Driver", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            user.email?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Level card
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface).padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Level ${level.level}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("${number.format(stats.points)} pts", style = MaterialTheme.typography.headlineMedium, color = Gold)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { level.progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = Gold,
                trackColor = Color.White.copy(alpha = 0.12f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${number.format(level.pointsToNext)} pts to level ${level.level + 1}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Stat tiles
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Cars collected", number.format(stats.cars), Modifier.weight(1f))
            StatTile("Unique models", number.format(stats.uniqueModels), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Garage value", formatCompactUsd(stats.totalValue), Modifier.weight(1f))
            StatTile("Scans this week", number.format(stats.scansThisWeek), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                "Avg points / car",
                if (stats.cars > 0) number.format(stats.points / stats.cars) else "-",
                Modifier.weight(1f),
            )
            StatTile(
                "Favorite brand",
                stats.topMake?.let { "$it x${stats.topMakeCount}" } ?: "-",
                Modifier.weight(1f),
            )
        }

        RarityBreakdown(stats)

        stats.bestCar?.let { Highlight("Highest scoring", it, "+${it.points} pts") }
        stats.priciestCar?.let { car ->
            Highlight("Most valuable", car, car.valueUsd?.let { formatCompactUsd(it) } ?: "")
        }

        stats.firstScan?.let {
            Text(
                "Collecting since ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sign out")
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "Sign out?",
            message = "Your garage stays saved on this phone. Sign back in with the same Google account to see it again.",
            confirmText = "Sign out",
            onConfirm = { confirmLogout = false; onSignOut() },
            onDismiss = { confirmLogout = false },
        )
    }
}

@Composable
private fun RarityBreakdown(stats: ProfileStats) {
    val maxCount = (stats.rarityCounts.values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Collection by rarity", style = MaterialTheme.typography.titleMedium)
        Rarity.entries.reversed().forEach { rarity ->
            val count = stats.rarityCounts[rarity] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rarity.label,
                    style = MaterialTheme.typography.labelLarge.copy(brush = rarityBrush(rarity)),
                    modifier = Modifier.width(84.dp),
                )
                Box(
                    Modifier.weight(1f).height(10.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(count.toFloat() / maxCount)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(rarityBrush(rarity)),
                    )
                }
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(36.dp),
                )
            }
        }
    }
}

@Composable
private fun Highlight(label: String, car: CarEntity, trailing: String) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = File(car.imagePath),
            contentDescription = car.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 84.dp, height = 62.dp).clip(MaterialTheme.shapes.medium),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(car.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            RarityChip(car.rarityEnum)
        }
        Spacer(Modifier.width(8.dp))
        Text(trailing, style = MaterialTheme.typography.titleMedium, color = Gold)
    }
}