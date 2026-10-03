package vn.lucbao.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

private const val LEAF = "M24 3C36 9 44 19 44 29c0 9-7.5 16-20 16S4 38 4 29C4 19 12 9 24 3z"
private const val PLAY = "M19 19.5v15l12.5-7.5z"

/** The Lục Bảo leaf with a play button. */
@Composable
fun LeafLogo(size: Dp, modifier: Modifier = Modifier) {
    val leaf = remember { PathParser().parsePathString(LEAF).toPath() }
    val play = remember { PathParser().parsePathString(PLAY).toPath() }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 48f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(
                leaf,
                Brush.linearGradient(
                    listOf(Color(0xFF7CF0BD), Color(0xFF2FBF80), Color(0xFF0F6B47)),
                    start = Offset(8f, 4f),
                    end = Offset(40f, 44f),
                )
            )
            drawLine(
                Color.White.copy(alpha = 0.25f), Offset(24f, 8f), Offset(24f, 42f),
                strokeWidth = 1.2f
            )
            drawPath(play, Color(0xE004140D))
        }
    }
}
