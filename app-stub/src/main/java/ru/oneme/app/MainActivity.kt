package ru.oneme.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Убираем системный UI (заголовок и статус-бар)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Индикатор загрузки с градиентом и анимацией
                AnimatedGradientLoadingIndicator(
                    modifier = Modifier.size(60.dp)
                )
            }
        }
    }
}

@Composable
fun AnimatedGradientLoadingIndicator(modifier: Modifier = Modifier) {
    // Состояние для угла вращения
    var rotationAngle by remember { mutableStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }

    // Цвета градиента
    val gradientColors = listOf(
        Color(0xFF1f41f9), // Синий
        Color(0xFF7740db)  // Фиолетовый
    )

    // Анимация вращения
    val infiniteTransition = rememberInfiniteTransition()
    val animatedRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // Обновляем угол вращения, когда анимация не на паузе
    LaunchedEffect(animatedRotation, isPaused) {
        if (!isPaused) {
            rotationAngle = animatedRotation
        }
    }

    // Эффект для случайных пауз
    LaunchedEffect(Unit) {
        while (true) {
            // Случайная пауза между 2 и 5 секундами
            val pauseDelay = Random.nextLong(2000, 5000)
            delay(pauseDelay)

            // Активируем паузу
            isPaused = true

            // Случайная длительность паузы между 400 и 800 мс
            val pauseDuration = Random.nextLong(400, 800)
            delay(pauseDuration)

            // Возобновляем анимацию
            isPaused = false
        }
    }

    Canvas(modifier = modifier) {
        // Создаём градиент от нижнего левого к верхнему правому углу
        val brush = Brush.linearGradient(
            colors = gradientColors,
            start = Offset(0f, size.height),
            end = Offset(size.width, 0f)
        )

        // Рисуем круговой индикатор с градиентом
        rotate(rotationAngle) {
            drawArc(
                brush = brush,
                startAngle = 0f,
                sweepAngle = 270f, // Неполный круг для эффекта загрузки
                useCenter = false,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                size = Size(size.width, size.height)
            )
        }
    }
}

@Preview
@Composable
fun PreviewAnimatedGradientLoadingIndicator() {
    AnimatedGradientLoadingIndicator(
        modifier = Modifier.size(60.dp)
    )
}