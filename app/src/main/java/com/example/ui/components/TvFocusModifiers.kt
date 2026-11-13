package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Unified touch + Android TV / Fire TV 10-foot D-Pad focusable modifier.
 * Provides high-contrast focus ring, subtle spring scale on focus, D-Pad Center/Enter activation,
 * and M3 ripple touch target compliance (>= 48dp).
 */
fun Modifier.tvFocusableCard(
    shape: Shape,
    selected: Boolean = false,
    focusBorderColor: Color? = null,
    scaleOnFocus: Float = 1.025f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val highlightColor = focusBorderColor ?: MaterialTheme.colorScheme.primary
    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleOnFocus else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tvFocusScale"
    )

    this
        .minimumInteractiveComponentSize()
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (isFocused) {
                Modifier.border(2.5.dp, highlightColor, shape)
            } else if (selected) {
                Modifier.border(1.5.dp, highlightColor.copy(alpha = 0.75f), shape)
            } else {
                Modifier
            }
        )
        .clip(shape)
        .onKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp &&
                (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
            ) {
                onClick()
                true
            } else {
                false
            }
        }
        .focusable(interactionSource = interactionSource)
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material3.ripple(),
            role = Role.Button,
            onClick = onClick
        )
}

@Composable
fun posterFallbackGradient(seed: String): List<Color> {
    val hash = kotlin.math.abs(seed.hashCode())
    val palettes = listOf(
        listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
        listOf(Color(0xFF1A103C), Color(0xFF321D6E), Color(0xFF0099B8)),
        listOf(Color(0xFF23074D), Color(0xFF6B1D5C), Color(0xFFCC5333)),
        listOf(Color(0xFF091E3A), Color(0xFF1D4E89), Color(0xFF00B4DB)),
        listOf(Color(0xFF141E30), Color(0xFF243B55), Color(0xFF4B6CB7)),
        listOf(Color(0xFF1D2B24), Color(0xFF134E4A), Color(0xFF0D9488))
    )
    return palettes[hash % palettes.size]
}
