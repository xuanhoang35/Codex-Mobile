package vn.intsoft.codexmobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vn.intsoft.codexmobile.data.AiEngine

/**
 * Official Brand Vector Logos for OpenAI Codex, Google Antigravity & Cline.
 * Self-contained Canvas implementations ensuring pixel-perfect scaling at any DP size.
 */

@Composable
fun CodexBrandIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f

        // Codex Emerald Badge Background
        drawCircle(
            color = Color(0xFF10A37F), // OpenAI Official Emerald Green
            radius = s * 0.48f,
            center = Offset(cx, cy)
        )

        // OpenAI 6-Segment Rosette Whirl
        val r = s * 0.28f
        val strokeW = (s * 0.08f).coerceAtLeast(1.5f)

        for (i in 0 until 6) {
            val angle = Math.toRadians((i * 60.0) + 15.0)
            val path = Path().apply {
                val startX = cx + (Math.cos(angle) * r * 0.35).toFloat()
                val startY = cy + (Math.sin(angle) * r * 0.35).toFloat()
                moveTo(startX, startY)

                val ctrlX = cx + (Math.cos(angle + 0.4) * r * 1.15).toFloat()
                val ctrlY = cy + (Math.sin(angle + 0.4) * r * 1.15).toFloat()

                val endX = cx + (Math.cos(angle + 0.8) * r * 0.85).toFloat()
                val endY = cy + (Math.sin(angle + 0.8) * r * 0.85).toFloat()

                quadraticTo(ctrlX, ctrlY, endX, endY)
            }
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }

        // Center hub dot
        drawCircle(
            color = Color.White,
            radius = s * 0.06f,
            center = Offset(cx, cy)
        )
    }
}

@Composable
fun AntigravityBrandIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f

        // Google DeepMind / Gemini Multi-color Gradient
        val gradient = Brush.linearGradient(
            colors = listOf(
                Color(0xFF4285F4), // Google Blue
                Color(0xFF8B5CF6), // DeepMind Purple
                Color(0xFFEC4899), // Magenta Spark
                Color(0xFFF59E0B)  // Amber Flare
            ),
            start = Offset(0f, 0f),
            end = Offset(this.size.width, this.size.height)
        )

        // Main 4-Point Concave Sparkle Star
        val r = s * 0.46f
        val starPath = Path().apply {
            moveTo(cx, cy - r)
            quadraticTo(cx, cy, cx + r, cy)
            quadraticTo(cx, cy, cx, cy + r)
            quadraticTo(cx, cy, cx - r, cy)
            quadraticTo(cx, cy, cx, cy - r)
            close()
        }
        drawPath(path = starPath, brush = gradient)

        // Antigravity Orbit Spark (Satellite diamond at top-right)
        val satR = s * 0.16f
        val satCx = cx + s * 0.28f
        val satCy = cy - s * 0.28f
        val satPath = Path().apply {
            moveTo(satCx, satCy - satR)
            quadraticTo(satCx, satCy, satCx + satR, satCy)
            quadraticTo(satCx, satCy, satCx, satCy + satR)
            quadraticTo(satCx, satCy, satCx - satR, satCy)
            quadraticTo(satCx, satCy, satCx, satCy - satR)
            close()
        }
        drawPath(
            path = satPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF38BDF8), Color(0xFFC084FC)),
                start = Offset(satCx - satR, satCy - satR),
                end = Offset(satCx + satR, satCy + satR)
            )
        )
    }
}

@Composable
fun ClineBrandIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f

        // 1. Antenna Stalk & Glowing Beacon
        val antW = s * 0.08f
        val antH = s * 0.20f
        drawRoundRect(
            color = Color(0xFF64748B),
            topLeft = Offset(cx - antW / 2f, cy - s * 0.48f),
            size = Size(antW, antH),
            cornerRadius = CornerRadius(antW / 2f, antW / 2f)
        )
        drawCircle(
            color = Color(0xFFF97316), // Cline Orange Beacon
            radius = s * 0.08f,
            center = Offset(cx, cy - s * 0.44f)
        )

        // 2. Ear Bolts (Left & Right)
        val earW = s * 0.10f
        val earH = s * 0.24f
        drawRoundRect(
            color = Color(0xFF475569),
            topLeft = Offset(cx - s * 0.46f, cy - earH / 2f + s * 0.03f),
            size = Size(earW, earH),
            cornerRadius = CornerRadius(earW / 2f, earW / 2f)
        )
        drawRoundRect(
            color = Color(0xFF475569),
            topLeft = Offset(cx + s * 0.46f - earW, cy - earH / 2f + s * 0.03f),
            size = Size(earW, earH),
            cornerRadius = CornerRadius(earW / 2f, earW / 2f)
        )

        // 3. Robot Head (Slate / Terminal Helmet)
        val headW = s * 0.74f
        val headH = s * 0.60f
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(cx - headW / 2f, cy - headH / 2f + s * 0.03f),
            size = Size(headW, headH),
            cornerRadius = CornerRadius(s * 0.14f, s * 0.14f)
        )

        // 4. Terminal Visor Screen
        val visorW = headW * 0.74f
        val visorH = headH * 0.42f
        drawRoundRect(
            color = Color(0xFF0F172A),
            topLeft = Offset(cx - visorW / 2f, cy - visorH / 2f + s * 0.03f),
            size = Size(visorW, visorH),
            cornerRadius = CornerRadius(s * 0.08f, s * 0.08f)
        )

        // 5. Friendly Glowing Green Robot Eyes / Phosphor Bars
        val eyeW = visorW * 0.22f
        val eyeH = visorH * 0.46f
        val eyeY = cy - eyeH / 2f + s * 0.03f

        // Left Eye
        drawRoundRect(
            color = Color(0xFF22C55E), // Cline Terminal Neon Green
            topLeft = Offset(cx - visorW * 0.30f, eyeY),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2f, eyeW / 2f)
        )
        // Right Eye
        drawRoundRect(
            color = Color(0xFF22C55E),
            topLeft = Offset(cx + visorW * 0.30f - eyeW, eyeY),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2f, eyeW / 2f)
        )
    }
}

@Composable
fun EngineBrandIcon(
    engine: AiEngine,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    when (engine) {
        AiEngine.CODEX -> CodexBrandIcon(modifier = modifier, size = size)
        AiEngine.ANTIGRAVITY -> AntigravityBrandIcon(modifier = modifier, size = size)
        AiEngine.CLINE -> ClineBrandIcon(modifier = modifier, size = size)
    }
}

