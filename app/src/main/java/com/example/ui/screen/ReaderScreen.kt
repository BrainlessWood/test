package com.example.ui.screen

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.ReaderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    bookId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val book by viewModel.currentBook.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val pageBitmap by viewModel.pageBitmap.collectAsStateWithLifecycle()
    val isLoadingPage by viewModel.isLoadingPage.collectAsStateWithLifecycle()
    
    val isTtsPlaying by viewModel.isTtsPlaying.collectAsStateWithLifecycle()
    val currentTtsIndex by viewModel.currentTtsIndex.collectAsStateWithLifecycle()
    val isServiceBound by viewModel.isServiceBound.collectAsStateWithLifecycle()
    val pageSentences by viewModel.pageSentences.collectAsStateWithLifecycle()
    val turkishStatus by viewModel.turkishStatus.collectAsStateWithLifecycle()

    val speechSpeed by viewModel.settingsHelper.ttsSpeed.collectAsStateWithLifecycle()
    val speechPitch by viewModel.settingsHelper.ttsPitch.collectAsStateWithLifecycle()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showTurkishErrorDialog by remember { mutableStateOf(false) }

    // Navigation trigger
    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            book?.title ?: "Loading Book...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Page ${currentPage + 1} of ${book?.totalPages ?: 0}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("tts_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Voice Options"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (!isTtsPlaying) {
                FloatingActionButton(
                    onClick = {
                        val status = viewModel.checkTurkishTtsSupport()
                        if (status == "MISSING_DATA" || status == "NOT_SUPPORTED") {
                            showTurkishErrorDialog = true
                        } else {
                            viewModel.triggerReadAloud()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .testTag("read_aloud_fab")
                        .padding(bottom = if (isTtsPlaying || pageSentences.isNotEmpty()) 120.dp else 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Read Page Aloud"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Read Aloud", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Interactive Visual PDF view area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingPage) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Rendering digital page layout cleanly...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    } else if (pageBitmap != null) {
                        ZoomablePdfPage(bitmap = pageBitmap!!)
                    } else {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Failed to render visual layout.",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Interactive sentence marker indicator (if TTS is currently running)
                if (isTtsPlaying && pageSentences.isNotEmpty() && currentTtsIndex < pageSentences.size) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .background(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Hearing,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "SPEAKING SENTENCE ${currentTtsIndex + 1} OF ${pageSentences.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                pageSentences[currentTtsIndex],
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                // Visual Page Navigator Bar at the bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = { viewModel.prevPage() },
                        enabled = currentPage > 0,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                    }

                    Text(
                        text = "Page ${currentPage + 1} of ${book?.totalPages ?: 0}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    FilledIconButton(
                        onClick = { viewModel.nextPage() },
                        enabled = currentPage + 1 < (book?.totalPages ?: 0),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
                    }
                }
            }

            // Playback controls UI overlay at the bottom
            AnimatedVisibility(
                visible = isTtsPlaying || pageSentences.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                PlaybackControlsOverlay(
                    isPlaying = isTtsPlaying,
                    onPlay = { viewModel.resumeTts() },
                    onPause = { viewModel.pauseTts() },
                    onStop = { viewModel.stopTts() },
                    speechSpeed = speechSpeed,
                    speechPitch = speechPitch,
                    onSpeedChange = { viewModel.adjustSpeed(it) },
                    onPitchChange = { viewModel.adjustPitch(it) }
                )
            }
        }
    }

    // Dynamic Options and pitch slider configurations Setup Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SettingsVoice, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("TTS Diagnostics & Voice Pitch")
                }
            },
            text = {
                Column {
                    Text(
                        "Configure standard TextToSpeech rates and run offline diagnostic packages validation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Turkish Voice Check Status
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint = if (turkishStatus == "AVAILABLE") Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Turkish (tr_TR) Support", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                text = when (turkishStatus) {
                                    "AVAILABLE" -> "Fully Downloaded Offline Pack"
                                    "MISSING_DATA" -> "Missing Offline Data Pack"
                                    "NOT_SUPPORTED" -> "Language Pack Not Supported"
                                    else -> "Tuning engine parameters..."
                                },
                                fontSize = 11.sp,
                                color = if (turkishStatus == "AVAILABLE") Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Speech Speed: ${"%.1f".format(speechSpeed)}x", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = speechSpeed,
                        onValueChange = { viewModel.adjustSpeed(it) },
                        valueRange = 0.5f..2.5f,
                        steps = 4,
                        modifier = Modifier.testTag("settings_speed_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Voice Pitch: ${"%.1f".format(speechPitch)}x", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = speechPitch,
                        onValueChange = { viewModel.adjustPitch(it) },
                        valueRange = 0.5f..2.0f,
                        steps = 4,
                        modifier = Modifier.testTag("settings_pitch_slider")
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSettingsDialog = false }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val intent = Intent("com.android.settings.TTS_SETTINGS")
                        context.startActivity(intent)
                    }
                ) {
                    Text("Device TTS Setup")
                }
            }
        )
    }

    // Explicit Turkish language diagnostic setup dialog
    if (showTurkishErrorDialog) {
        AlertDialog(
            onDismissRequest = { showTurkishErrorDialog = false },
            icon = {
                Icon(
                    Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text("Turkish Speech Missing", textAlign = TextAlign.Center)
            },
            text = {
                Text(
                    "The offline standard Turkish language syntax profile was not detected on this system. " +
                    "To enable reading offline books seamlessly without memory dropouts or errors, " +
                    "install Turkish language assets in the system Text-To-Speech settings.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTurkishErrorDialog = false
                        val intent = Intent("com.android.settings.TTS_SETTINGS")
                        context.startActivity(intent)
                    }
                ) {
                    Text("Open System TTS Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTurkishErrorDialog = false }) {
                    Text("Dismiss Info")
                }
            }
        )
    }
}

@Composable
fun ZoomablePdfPage(
    bitmap: Bitmap,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetState by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        offsetState += offsetChange
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .transformable(state = transformState),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Visual Book Layout Page",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetState.x,
                    translationY = offsetState.y
                )
        )
    }
}

@Composable
fun PlaybackControlsOverlay(
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    speechSpeed: Float,
    speechPitch: Float,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("playback_panel"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Horizontal adjustment metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Pitch: ${"%.1f".format(speechPitch)}x",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Slider(
                        value = speechPitch,
                        onValueChange = onPitchChange,
                        valueRange = 0.7f..1.5f,
                        modifier = Modifier
                            .width(100.dp)
                            .testTag("playback_pitch_slider")
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Speed: ${"%.1f".format(speechSpeed)}x",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Slider(
                        value = speechSpeed,
                        onValueChange = onSpeedChange,
                        valueRange = 0.7f..2.0f,
                        modifier = Modifier
                            .width(100.dp)
                            .testTag("playback_speed_slider")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Playback controls row (Pause, Play/Resume, Stop)
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pause button
                FilledIconButton(
                    onClick = { if (isPlaying) onPause() else onPlay() },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("play_pause_tts_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play or Pause speech synthesizer",
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Close/Stop button
                Button(
                    onClick = onStop,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("stop_tts_button")
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop TTS")
                }
            }
        }
    }
}
