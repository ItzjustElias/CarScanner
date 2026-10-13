package com.example.carscanner.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import com.example.carscanner.domain.Rarity

private val Rainbow = listOf(
    Color(0xFFFF5A5A), Color(0xFFFFB347), Color(0xFFFFE45E), Color(0xFF5CE08A),
    Color(0xFF4FD1FF), Color(0xFF7C8CFF), Color(0xFFC77DFF), Color(0xFFFF5A5A),
)

@Composable
fun rarityBrush(rarity: Rarity, alpha: Float = 1f): Brush =
    if (rarity == Rarity.DIVINE) rainbowBrush(alpha) else SolidColor(rarity.color().copy(alpha = alpha))

@Composable
private fun rainbowBrush(alpha: Float): Brush {
    val transition = rememberInfiniteTransition(label = "rainbow")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "rainbowShift",
    )
    val width = 500f
    return Brush.linearGradient(
        colors = Rainbow.map { it.copy(alpha = alpha) },
        start = Offset(shift * width, 0f),
        end = Offset(shift * width + width, 0f),
        tileMode = TileMode.Repeated,
    )
}