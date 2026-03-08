package com.example.skillmorph

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.skillmorph.presentation.main.viewModel.AgentViewModel
import com.example.skillmorph.presentation.main.viewModel.ChatMessage
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun HomeScreen(viewModel: AgentViewModel) {
    var isVoiceMode by remember { mutableStateOf(true) }

    // Use a Box to layer everything
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Or your main app background gradient
    ) {
        // --- LAYER 1: CONTENT ---
        // Crossfade makes the switch smooth instead of instant
        androidx.compose.animation.Crossfade(targetState = isVoiceMode, label = "mode") { voice ->
            if (voice) {
                // Your existing Particle Voice Screen
                AgentRing(viewModel)
            } else {
                // The new Chat Screen
                AgentChat(viewModel)
            }
        }

        // --- LAYER 2: TOGGLE CHIP ---
        // This floats on top of everything
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd) // Placing it at bottom-right
                .padding(end = 24.dp) // Adjust padding to sit above Send button/Mic
        ) {
            // Assuming you have the GlassInputModeChip from previous steps
            // If using the snippet you pasted, ensure InputModeChip is defined
            GlassInputModeChip(
                isVoiceMode = isVoiceMode,
                onToggle = { isVoiceMode = !isVoiceMode }
            )
        }
    }
}

// ... The contents of GlassInputModeChip and ChipOption composables remain unchanged ...
// NOTE: I've moved them here for completeness in a single file, you can keep them separate.

@Composable
fun GlassInputModeChip(
    isVoiceMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalWidth = 220.dp
    val chipHeight = 52.dp
    val indicatorWidth = totalWidth / 2
    val neonCyan = Color(0xFF00E5FF)

    val glassGradient = Brush.horizontalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.15f),
            Color.White.copy(alpha = 0.05f),
            Color.White.copy(alpha = 0.15f)
        )
    )

    val borderGradient = Brush.horizontalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.5f),
            Color.White.copy(alpha = 0.2f),
            Color.White.copy(alpha = 0.5f)
        )
    )

    val indicatorOffset by animateDpAsState(
        targetValue = if (isVoiceMode) 0.dp else indicatorWidth,
        animationSpec = tween(300),
        label = "offset"
    )

    Box(
        modifier = modifier
            .width(totalWidth)
            .height(chipHeight)
            .clip(CircleShape)
            .background(glassGradient)
            .border(1.dp, borderGradient, CircleShape)
            .clickable { onToggle() }
    ) {
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(indicatorWidth)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(CircleShape)
                .background(neonCyan.copy(alpha = 0.8f))
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChipOption(
                text = "Voice",
                icon = Icons.Rounded.Mic,
                isSelected = isVoiceMode,
                modifier = Modifier.width(indicatorWidth)
            )

            ChipOption(
                text = "Type",
                icon = Icons.Rounded.Keyboard,
                isSelected = !isVoiceMode,
                modifier = Modifier.width(indicatorWidth)
            )
        }
    }
}

@Composable
fun ChipOption(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.Black else Color.White.copy(alpha = 0.8f),
        animationSpec = tween(300),
        label = "color"
    )

    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

data class RingParticle(
    var angle: Float,
    var baseDistance: Float,
    var size: Float,
    var speed: Float
)

@Composable
fun AgentRing(viewModel: AgentViewModel) { // CORRECTED: Now takes a ViewModel
    val context = LocalContext.current

    // --- STATES ---
    var isAgentSpeaking by remember { mutableStateOf(false) }
    var isUserSpeaking by remember { mutableStateOf(false) } // Are we recording?
    var visualAmplitude by remember { mutableStateOf(0f) }

    val particles = remember { mutableStateListOf<RingParticle>() }

    // ADDED: Observe states from the ViewModel
    val isThinking by viewModel.isAgentThinking.collectAsState()
    val textToSpeak by viewModel.ttsText.collectAsState()

    // --- 1. TTS SETUP ---
    val tts = remember {
        TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) Log.e("TTS", "Init failed")
        }.apply {
            language = Locale.US
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { isAgentSpeaking = true }
                override fun onDone(id: String?) {
                    isAgentSpeaking = false
                    visualAmplitude = 0f
                }
                override fun onError(id: String?) { isAgentSpeaking = false }
            })
        }
    }

    // ADDED: Effect to trigger TTS when ViewModel provides text
    LaunchedEffect(textToSpeak) {
        textToSpeak?.let { text ->
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "aiResponse")
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "aiResponse")
            // Reset the trigger in the ViewModel
            viewModel.onTtsFinished()
        }
    }


    // --- 2. SPEECH RECOGNIZER SETUP ---
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
    }

    // CORRECTED: The speech result now sends data to the ViewModel
    val onSpeechResult = { text: String ->
        isUserSpeaking = false
        viewModel.sendMessage(text, isVoice = true)
    }

    // Set up the listener callbacks
    DisposableEffect(Unit) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isUserSpeaking = false }
            override fun onError(error: Int) {
                isUserSpeaking = false
                Log.e("Speech", "Error: $error")
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onSpeechResult(matches[0])
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer.setRecognitionListener(listener)
        onDispose {
            speechRecognizer.destroy()
            tts.shutdown()
        }
    }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            isUserSpeaking = true
            speechRecognizer.startListening(speechIntent)
        } else {
            Toast.makeText(context, "Microphone permission needed", Toast.LENGTH_SHORT).show()
        }
    }

    // --- INITIALIZATION: FORM THE RING ---
    LaunchedEffect(Unit) {
        if (particles.isEmpty()) {
            repeat(350) {
                particles.add(
                    RingParticle(
                        angle = Random.nextFloat() * 360,
                        baseDistance = 300f + Random.nextFloat() * 90f,
                        size = Random.nextFloat() * 6 + 3,
                        speed = Random.nextFloat() * 0.3f + 0.2f
                    )
                )
            }
        }
    }

    // --- ANIMATION LOOP ---
    LaunchedEffect(isAgentSpeaking, isUserSpeaking, isThinking) { // CORRECTED: Reacts to all state changes
        while (isActive) {
            withFrameNanos { time ->
                // CORRECTED: Amplitude logic now includes 'isThinking'
                visualAmplitude = when {
                    isAgentSpeaking -> {
                        val wave = sin(time / 90_000_000.0).toFloat()
                        (kotlin.math.abs(wave) * 0.6f) + 0.1f
                    }
                    isThinking -> {
                        // Fast ripples for "Processing"
                        0.4f + Random.nextFloat() * 0.2f
                    }
                    isUserSpeaking -> {
                        // Fast nervous vibration for "Listening"
                        0.3f + Random.nextFloat() * 0.1f
                    }
                    else -> {
                        // Slow breathing for "Idle"
                        sin(time / 500_000_000.0).toFloat() * 0.1f
                    }
                }
                // Update Particles
                particles.forEach { p -> p.angle += p.speed }
            }
        }
    }

    // --- UI LAYOUT ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {

        // 1. The Particle Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)

            particles.forEach { p ->
                val expansionFactor = 1f + visualAmplitude * 0.5f
                val currentDistance = p.baseDistance * expansionFactor

                // Jitter adds the "electric" look
                val jitter = if (isAgentSpeaking || isUserSpeaking || isThinking) Random.nextFloat() * 10f - 5f else 0f

                val rad = Math.toRadians(p.angle.toDouble())
                val x = center.x + (cos(rad) * currentDistance).toFloat() + jitter
                val y = center.y + (sin(rad) * currentDistance).toFloat() + jitter

                // CORRECTED: COLOR LOGIC now includes 'isThinking'
                val particleColor = when {
                    isAgentSpeaking -> Color(0xFF00FFFF).copy(alpha = 0.8f)      // Cyan (Agent Talking)
                    isThinking -> Color(0xFF8A2BE2).copy(alpha = 0.7f)           // BlueViolet (Thinking)
                    isUserSpeaking -> Color(0xFF00FF00).copy(alpha = 0.8f)       // Green (Listening to You)
                    else -> Color.Cyan.copy(0.7f)                 // Dim Blue (Idle)
                }

                drawCircle(
                    color = particleColor,
                    radius = p.size,
                    center = Offset(x, y)
                )
            }
        }

        // 2. The Mic Button (Bottom Center)
        FloatingActionButton(
            onClick = {
                if (isUserSpeaking) {
                    speechRecognizer.stopListening()
                    isUserSpeaking = false
                } else {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        isUserSpeaking = true
                        speechRecognizer.startListening(speechIntent)
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            containerColor = when {
                isUserSpeaking -> Color.Green
                isThinking -> Color.Gray // Indicate disabled while thinking
                else -> Color(0xFF00E5FF)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 50.dp)
                .size(70.dp),
            shape = CircleShape
        ) {
            Icon(
                imageVector = if (isUserSpeaking) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = "Mic",
                tint = Color.Black,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}


data class ChatMessage(val text: String, val isUser: Boolean)

@Composable
fun AgentChat(viewModel: AgentViewModel) {
    // OBSERVE REAL DATA
    val messages by viewModel.messages.collectAsState()
    val isThinking by viewModel.isAgentThinking.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()



    // Auto-scroll when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(top = 80.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        // --- CHAT LIST ---
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(messages) { msg ->
                MessageBubble(message = msg)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Show "Typing..." indicator if thinking
            if (isThinking) {
                item {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "SkillMorph is thinking...",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // --- INPUT AREA ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Type a message...", color = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color(0xFF00E5FF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
                enabled = !isThinking // CORRECTED: Disable input while thinking
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText, isVoice = false)
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF00E5FF), CircleShape),
                enabled = !isThinking && inputText.isNotBlank() // CORRECTED: Disable button appropriately
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (isThinking || inputText.isBlank()) Color.Gray else Color.Black
                )
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val bubbleColor = if (message.isUser) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color.DarkGray.copy(alpha = 0.3f)
    val align = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (message.isUser) RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp) else RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(align)
                .clip(shape)
                .background(bubbleColor)
                .padding(12.dp)
                .widthIn(max = 300.dp)
        ) {
            if (message.isUser) {
                Text(text = message.text, color = Color.White, fontSize = 16.sp)
            } else {
                MarkdownText(
                    markdown = message.text,
                    color = Color.White,
                    fontSize = 16.sp,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White) // Ensure markdown text color is white
                )
            }
        }
    }
}
