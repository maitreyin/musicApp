package com.example.offlinemusic

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicTheme {
                SplashScreen(onSplashComplete = {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                })
            }
        }
    }
}

@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {
    var showContent by remember { mutableStateOf(false) }
    var showTagline by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(300)
        showContent = true
        delay(1500)
        showTagline = true
        delay(1500)
        onSplashComplete()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(AppColors.Midnight, AppColors.AccentStrong, AppColors.Accent),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            AnimatedVisibility(visible = showContent, enter = fadeIn(), exit = fadeOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MusicNote,
                        null,
                        modifier = Modifier.size(80.dp),
                        tint = AppColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "MUSED",
                        fontSize = 48.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = AppColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            AnimatedVisibility(visible = showTagline, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    "Your Music, Offline",
                    fontSize = 18.sp,
                    color = AppColors.TextPrimary.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
