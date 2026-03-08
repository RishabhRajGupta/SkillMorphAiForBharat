
package com.example.skillmorph.presentation.auth

import android.R.attr.fontWeight
import android.R.color.white
import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.skillmorph.utils.GoogleAuthUiClient
import com.example.skillmorph.utils.glassEffect
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ModifierLocalBeyondBoundsLayout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavHostController
import com.example.skillmorph.R
import com.example.skillmorph.presentation.main.MainScreen
import com.google.common.io.Files.append
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random


@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    appNavController: NavHostController
) {
    val authState by viewModel.authState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Show a toast for any errors from the ViewModel
    LaunchedEffect(authState.error) {
        authState.error?.let {
            Toast.makeText(context, "Sign-in failed: $it", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Auth Logic
        if (authState.isLoading) {
            // Show a loading indicator
            CircularProgressIndicator()
        } else if (authState.user != null) {
            // If user is logged in, show a welcome message and sign-out button
//            Text("Welcome, ${authState.user?.displayName ?: "User"}!")
//            Spacer(modifier = Modifier.height(16.dp))
//            Button(onClick = { viewModel.signOut() }) {
//                Text("Sign Out")
//            }
            MainScreen(appNavController = appNavController)
        } else {
            // If user is not logged in, show the sign-in button
            Text(
                text = "SkillMorph",
                fontSize = 28.sp, // Use fontSize with sp for text scaling
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.6f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ){
                Text(
                    text = "O",
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    color = Color.Red)
                Text(
                    text = "S",
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    color = Color(0xFF9C27B0))
            }

            Spacer(modifier = Modifier.height(100.dp)) // Added space for better layout

            AgentRingFace(
                modifier = Modifier.size(300.dp)
            )

            Spacer(modifier = Modifier.height(100.dp))

            SignInButton(
                onClick = {
                    coroutineScope.launch {
                        try {
                            val googleIdOption = GoogleAuthUiClient.getGoogleIdOption()
                            val credentialRequest = GoogleAuthUiClient.getCredentialRequest(googleIdOption)
                            val credentialManager = GoogleAuthUiClient.getCredentialManager(context)

                            val result = credentialManager.getCredential(context as Activity, credentialRequest)
                            viewModel.signInWithGoogle(result)

                        } catch (e: GetCredentialException) {
                            // Show a more informative error message for debugging
                            Toast.makeText(context, "Sign-in failed: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "An unexpected error occurred: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(120.dp))

            TermsAndPrivacyText(
                onClickTerms = {  },
                onClickPrivacy = {  })
        }
    }
}

@Composable
fun SignInButton(onClick: () -> Unit) {
    GlowingButtonContainer(
        modifier = Modifier.size(width = 280.dp, height = 60.dp),
        glowColor = Color(0xFF25C0CB) // Google blue glow
    ) {

        Box(
            modifier = Modifier.fillMaxSize()
                .clip(RoundedCornerShape(30.dp))
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxSize()
                    .glassEffect(),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.google_logo),
                        contentDescription = "Google Logo",
                        modifier = Modifier.size(35.dp)
                    )
                    Text(
                        text = "Continue With Google",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

// Simple particle data
data class LoginParticle(
    val initialAngle: Float,
    val distanceFromCenter: Float,
    val size: Float,
    val opacity: Float
)

@Composable
fun AgentRingFace(
    modifier: Modifier = Modifier,
    circleColor: Color = Color.Cyan
) {
    // 1. Create the Particles (Once)
    val particles = remember {
        val list = mutableListOf<LoginParticle>()
        repeat(450) {
            list.add(
                LoginParticle(
                    initialAngle = Random.nextFloat() * 360f,
                    // 200f is the inner radius (How big the hole in the middle is)
                    // 80f is the thickness of the ring band
                    distanceFromCenter = 250f + Random.nextFloat() * 100f,
                    size = Random.nextFloat() * 10f + 2f,
                    opacity = Random.nextFloat()
                )
            )
        }
        list
    }

    // 2. Infinite Rotation Animation
    val infiniteTransition = rememberInfiniteTransition(label = "ring_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing), // 8 seconds for full circle
            repeatMode = RepeatMode.Restart
        ), label = "rotation"
    )

    // 3. Pulse Animation (Subtle breathing effect)
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    // 4. Drawing
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)

        particles.forEach { p ->
            // Add global rotation to the particle's fixed angle
            val currentAngle = (p.initialAngle + rotationAngle) % 360
            val rad = Math.toRadians(currentAngle.toDouble())

            // Apply Pulse to distance
            val currentDist = p.distanceFromCenter * pulseScale // This scales the ring size slightly

            // Polar to Cartesian
            val x = center.x + (cos(rad) * currentDist).toFloat()
            val y = center.y + (sin(rad) * currentDist).toFloat()

            drawCircle(
                color = circleColor.copy(alpha = p.opacity * 0.8f), // Varied opacity for depth
                radius = p.size,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun GlowingButtonContainer(
    modifier: Modifier = Modifier,
    glowColor: Color = Color(0xFF03E6FB),
    cornerRadius: Dp = 30.dp,
    glowRadius: Dp = 40.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val paint = Paint().asFrameworkPaint().apply {
                color = glowColor.copy(alpha = 0.8f).toArgb()
                maskFilter =
                    android.graphics.BlurMaskFilter(
                        glowRadius.toPx(),
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
            }

            drawIntoCanvas {
                it.nativeCanvas.drawRoundRect(
                    0f,
                    0f,
                    size.width,
                    size.height,
                    cornerRadius.toPx(),
                    cornerRadius.toPx(),
                    paint
                )
            }
        }

        content()
    }
}


@Composable
fun TermsAndPrivacyText(
    onClickTerms: () -> Unit = {},
    onClickPrivacy: () -> Unit = {}
) {
    // 1. Create text with specific parts styled (optional bolding)
    val text = buildAnnotatedString {
        append("By continuing, you agree to our\n")

        // If you want "Terms & Privacy Policy" to look slightly bolder/clickable
        withStyle(style = SpanStyle(
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.6f)
        )
        ) {
            append("Terms & Privacy Policy.")
        }
    }

    Text(
        text = text,
        // 2. Visual Styling
        color = Color.White.copy(alpha = 0.4f), // Muted/Greyish look
        fontSize = 13.sp, // Small, legal-text size
        lineHeight = 18.sp, // Good spacing between the two lines
        textAlign = TextAlign.Center, // Centered horizontally

        // 3. Make it clickable (Simple version)
        modifier = Modifier.clickable {
            // Handle click (usually opens a webview)
            onClickTerms()
        }
    )
}