package com.dukaan.feature.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.dukaan.core.ui.R as CoreUiR
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.dukaan.core.ui.translation.LocalAppStrings

@Composable
fun SplashScreen(
    onNavigateToDashboard: () -> Unit
) {
    val strings = LocalAppStrings.current
    var startAnimation by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1.25f else 0.75f,
        animationSpec = tween(durationMillis = 1800, easing = FastOutSlowInEasing)
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1500)
    )

    val permissions = arrayOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO
    )

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onNavigateToDashboard()
    }

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2500) // Branding display time
        permissionLauncher.launch(permissions)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF065F46),
                        Color(0xFF064E3B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Subtle background glow
        Surface(
            modifier = Modifier
                .size(300.dp)
                .scale(scale * 1.2f)
                .alpha(alpha * 0.15f),
            shape = CircleShape,
            color = Color.White
        ) {}

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(alpha)
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(scale),
                contentAlignment = Alignment.Center
            ) {
                // Outer soft glow ring
                Surface(
                    modifier = Modifier.size(130.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                ) {}
                // Inner brand icon box with premium emerald gradient
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF059669), Color(0xFF021B12)),
                                start = Offset(0f, 0f),
                                end = Offset(270f, 270f)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(CoreUiR.drawable.ic_dukaan_brand),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        tint = Color.Unspecified
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append("Dukaan") }
                    withStyle(SpanStyle(color = Color(0xFFFDE68A))) { append(" AI") }
                },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = strings.smartShopAssistant,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
        }
        
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp),
            color = Color(0xFFFDE68A), // GoldSoft
            strokeWidth = 4.dp
        )
    }
}
