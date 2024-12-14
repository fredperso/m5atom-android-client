package com.envmonitor.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GaugeComponent(
    value: Float,
    maxValue: Float,
    title: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$title",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
        
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(4.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val radius = minOf(canvasWidth / 2.5f, canvasHeight - 20.dp.toPx())
                
                val center = Offset(canvasWidth / 2f, canvasHeight - 10.dp.toPx())
                
                // Draw background arc (180 degrees)
                drawArc(
                    color = surfaceVariant,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(
                        width = 12.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
                
                // Draw value arc
                val sweepAngle = (value / maxValue * 180f).coerceIn(0f, 180f)
                drawArc(
                    color = primary,
                    startAngle = 180f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(
                        width = 12.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
                
                // Draw value text
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.1f", value)+" "+unit,
                    center.x,
                    center.y - radius / 2,
                    android.graphics.Paint().apply {
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 18.sp.toPx()
                        color = onSurface.toArgb()
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GaugeComponentPreview() {
    GaugeComponent(
        value = 25f,
        maxValue = 50f,
        title = "Temperature",
        unit = "°C"
    )
}

@Preview(showBackground = true)
@Composable
fun GaugeComponentEmptyPreview() {
    GaugeComponent(
        value = 0f,
        maxValue = 100f,
        title = "Empty Gauge",
        unit = "units"
    )
}
