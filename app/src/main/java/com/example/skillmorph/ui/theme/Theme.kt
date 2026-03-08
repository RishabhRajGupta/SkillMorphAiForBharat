
package com.example.skillmorph.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Define the dark color scheme using the new palette
private val DarkColorScheme = darkColorScheme(
    primary = NeonBlue,
    secondary = CyberPurple,
    background = DarkPurple, // This will be the base, but our gradient will draw over it
    surface = NavyBlue,
    onPrimary = DarkPurple,       // Text/icons on top of primary color
    onSecondary = Color.White,    // Text/icons on top of secondary color
    onBackground = Color.White,   // Main text color
    onSurface = LightGray         // Secondary text/icons
)

@Composable
fun SkillMorphTheme(
    content: @Composable () -> Unit
) {


    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography
    ) {
        // A Box that provides the gradient background for the whole app.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush())
        ) {
            // The actual screen content is placed inside this Box.
            content()
        }
    }
}

fun gradientBrush(): Brush {
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF17BEBE),
            Color(0xFF248D8D),
            Color(0xFF205454),
            Color(0xFF163232),
            Color(0xFF0D1C1C),
            Color(0xFF1F0F2B),
            Color(0xFF36164B),
            Color(0xFF561B7B),
            Color(0xFF8619C3)
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )
}