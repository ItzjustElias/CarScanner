package com.example.carscanner.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.carscanner.domain.Rarity
import com.example.carscanner.ui.components.CarDetailContent
import com.example.carscanner.ui.components.RarityChip
import com.example.carscanner.ui.theme.Gold
import com.example.carscanner.ui.theme.color
import com.example.carscanner.ui.theme.rarityBrush

@Composable
fun ScanScreen(userId: String, onClose: () -> Unit, vm: ScanViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCamera = it
    }
    LaunchedEffect(Unit) { if (!hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.analyze(userId, uri)
    }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    var captureFailed by remember { mutableStateOf(false) }

    BackHandler(enabled = state !is ScanUiState.Idle) { vm.reset() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val current = state
        if (current is ScanUiState.Success) {
            ResultView(current, onScanAnother = vm::reset, onDone = onClose)
        } else {
            if (hasCamera) {
                CameraPreview(imageCapture, Modifier.fillMaxSize())
                Reticle(Modifier.align(Alignment.Center).fillMaxWidth(0.82f).aspectRatio(1.3f))
            } else {
                PermissionPrompt(
                    onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }

            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Gallery
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                    ) { Icon(Icons.Rounded.PhotoLibrary, "Pick from gallery", tint = Color.White) }
                    Spacer(Modifier.height(4.dp))
                    Text("Gallery", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
                // Shutter
                Box(
                    Modifier.size(80.dp).border(4.dp, Color.White, CircleShape).padding(7.dp)
                        .clip(CircleShape).background(Color.White)
                        .clickable(enabled = hasCamera && state is ScanUiState.Idle) {
                            takePhoto(
                                context, imageCapture,
                                onPhoto = { vm.analyze(userId, it) },
                                onError = { captureFailed = true },
                            )
                        },
                )
                Spacer(Modifier.size(56.dp)) // keeps the shutter centred
            }
        }

        if (state is ScanUiState.Analyzing) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).clickable(enabled = false) {},
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = Gold)
                    Text("Identifying your car", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }

    when (val s = state) {
        ScanUiState.NoCar -> InfoDialog(
            "No car found",
            "Try a clearer photo with the whole car in frame.",
            onDismiss = vm::reset,
        )
        is ScanUiState.Error -> InfoDialog("Scan failed", s.message, onDismiss = vm::reset)
        else -> Unit
    }
    if (captureFailed) {
        InfoDialog("Camera error", "The photo couldn't be taken. Try again or use the gallery.") { captureFailed = false }
    }
}

@Composable
private fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Try again") } },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Camera access needed", style = MaterialTheme.typography.titleLarge)
        Text(
            "Allow the camera to scan cars live, or use the gallery button below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onGrant) { Text("Allow camera") }
    }
}

@Composable
private fun Reticle(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val len = 36.dp.toPx()
        val stroke = 4.dp.toPx()
        val color = Color.White.copy(alpha = 0.9f)
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(color, Offset(x, y), Offset(x + dx * len, y), stroke, StrokeCap.Round)
            drawLine(color, Offset(x, y), Offset(x, y + dy * len), stroke, StrokeCap.Round)
        }
        corner(0f, 0f, 1f, 1f)
        corner(size.width, 0f, -1f, 1f)
        corner(0f, size.height, 1f, -1f)
        corner(size.width, size.height, -1f, -1f)
    }
}

@Composable
private fun ResultView(state: ScanUiState.Success, onScanAnother: () -> Unit, onDone: () -> Unit) {
    val car = state.car
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val shown by animateIntAsState(if (started) car.points else 0, tween(1200), label = "points")

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PointsHero("+$shown", car.rarityEnum)
            Text("points", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            RarityChip(car.rarityEnum)
            if (state.duplicate) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "You've scanned this model before, so it scores 10%.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = car.rarityEnum.color(),
                    textAlign = TextAlign.Center,
                )
            }
        }
        CarDetailContent(car)
        Button(onClick = onScanAnother, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Scan another car") }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Back to garage") }
    }
}

@Composable
private fun PointsHero(text: String, rarity: Rarity) {
    val style = MaterialTheme.typography.displayLarge
    if (rarity >= Rarity.MYTHICAL) {
        Text(text, style = style.copy(brush = rarityBrush(rarity)))
    } else {
        Text(text, style = style, color = Gold)
    }
}