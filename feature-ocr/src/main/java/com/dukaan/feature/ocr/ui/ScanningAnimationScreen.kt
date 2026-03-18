package com.dukaan.feature.ocr.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.dukaan.core.ui.translation.LocalAppStrings

@Composable
fun ScanningAnimationScreen(progress: ScanProgress) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated bill icon with scanning line
            ScanningBillAnimation(
                modifier = Modifier.size(220.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Progress steps
            ScanProgressSteps(currentStep = progress)
        }
    }
}

@Composable
private fun ScanningBillAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")

    // Scan line moving top to bottom
    val scanLineProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanLine"
    )

    // Pulsing corners
    val cornerAlpha = infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cornerPulse"
    )

    // Glow effect on scan line
    val glowAlpha = infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val green = Color(0xFF4CAF50)
    val cyan = Color(0xFF00BCD4)

    Canvas(modifier = modifier) {
        val padding = 30.dp.toPx()
        val left = padding
        val top = padding
        val right = size.width - padding
        val bottom = size.height - padding
        val cornerLen = 35.dp.toPx()
        val strokeW = 3.dp.toPx()

        // Bill outline (faint)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.1f),
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )

        // Faint horizontal lines (bill rows)
        val lineCount = 8
        for (i in 1..lineCount) {
            val y = top + (bottom - top) * i / (lineCount + 1)
            drawLine(
                color = Color.White.copy(alpha = 0.06f),
                start = Offset(left + 20.dp.toPx(), y),
                end = Offset(right - 20.dp.toPx(), y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Pulsing corner brackets
        val cColor = green.copy(alpha = cornerAlpha.value)

        // Top left
        drawLine(cColor, Offset(left, top + cornerLen), Offset(left, top), strokeW, cap = StrokeCap.Round)
        drawLine(cColor, Offset(left, top), Offset(left + cornerLen, top), strokeW, cap = StrokeCap.Round)
        // Top right
        drawLine(cColor, Offset(right - cornerLen, top), Offset(right, top), strokeW, cap = StrokeCap.Round)
        drawLine(cColor, Offset(right, top), Offset(right, top + cornerLen), strokeW, cap = StrokeCap.Round)
        // Bottom left
        drawLine(cColor, Offset(left, bottom - cornerLen), Offset(left, bottom), strokeW, cap = StrokeCap.Round)
        drawLine(cColor, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeW, cap = StrokeCap.Round)
        // Bottom right
        drawLine(cColor, Offset(right - cornerLen, bottom), Offset(right, bottom), strokeW, cap = StrokeCap.Round)
        drawLine(cColor, Offset(right, bottom - cornerLen), Offset(right, bottom), strokeW, cap = StrokeCap.Round)

        // Scanning line
        val scanY = top + (bottom - top) * scanLineProgress.value
        val lineGradient = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                cyan.copy(alpha = 0.8f),
                green.copy(alpha = 0.9f),
                cyan.copy(alpha = 0.8f),
                Color.Transparent
            ),
            startX = left,
            endX = right
        )
        drawLine(
            brush = lineGradient,
            start = Offset(left + 8.dp.toPx(), scanY),
            end = Offset(right - 8.dp.toPx(), scanY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Glow above/below scan line
        val glowBrush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                green.copy(alpha = glowAlpha.value * 0.3f),
                green.copy(alpha = glowAlpha.value),
                green.copy(alpha = glowAlpha.value * 0.3f),
                Color.Transparent
            ),
            startY = scanY - 30.dp.toPx(),
            endY = scanY + 30.dp.toPx()
        )
        drawRect(
            brush = glowBrush,
            topLeft = Offset(left + 8.dp.toPx(), scanY - 30.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(right - left - 16.dp.toPx(), 60.dp.toPx())
        )
    }
}

@Composable
private fun ScanProgressSteps(currentStep: ScanProgress) {
    val strings = LocalAppStrings.current
    val steps = listOf(
        ScanProgress.READING_TEXT to strings.readingTextFromBill,
        ScanProgress.PARSING_ITEMS to strings.parsingItemsAndPrices
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.padding(horizontal = 48.dp)
    ) {
        steps.forEach { (step, label) ->
            val isCompleted = currentStep.ordinal > step.ordinal
            val isActive = currentStep == step

            StepRow(
                label = label,
                isActive = isActive,
                isCompleted = isCompleted
            )
        }
    }
}

@Composable
private fun StepRow(
    label: String,
    isActive: Boolean,
    isCompleted: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "step_$label")
    val pulseAlpha = infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val targetAlpha = when {
        isCompleted -> 1f
        isActive -> 1f
        else -> 0.35f
    }
    val alpha by animateFloatAsState(targetValue = targetAlpha, label = "stepAlpha")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.alpha(alpha)
    ) {
        // Status indicator
        when {
            isCompleted -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            isActive -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .alpha(pulseAlpha.value),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer ring
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50).copy(alpha = 0.2f))
                    )
                    // Inner dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = label,
            color = when {
                isActive -> Color.White
                isCompleted -> Color(0xFF4CAF50)
                else -> Color.White.copy(alpha = 0.4f)
            },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 15.sp
        )
    }
}
