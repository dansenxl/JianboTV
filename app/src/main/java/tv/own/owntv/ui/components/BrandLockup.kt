package tv.own.owntv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.own.owntv.R
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.core.brand.AppIconSwitcher
import tv.own.owntv.core.brand.PixelMark as CorePixelMark
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.stageText

/**
 * The icon colour the launcher shows right now. The in-app logo follows it, not the saved choice, so
 * logo and home-screen icon change together after the restart.
 */
@Composable
fun rememberAppliedIcon(): AppIcon {
    val context = LocalContext.current
    return remember(context) { AppIconSwitcher.applied(context) }
}

/**
 * The icon's flat mark (no shadow, no next card); at 32 dp and below the simpler small drawing. Pixel
 * above 32 dp is its dot matrix ([PixelMark]). With Accent-colored logo on, the play triangle takes the
 * accent: on a card a white triangle is drawn over the card's own and tinted (Pixel has none).
 * [followAccent] false draws the icon as the launcher shows it (the icon pickers).
 */
@Composable
fun BrandMark(icon: AppIcon, size: Dp, modifier: Modifier = Modifier, followAccent: Boolean = true) {
    JianboMark(size, modifier, if (followAccent) stageAccent.accent else Color(icon.accent))
}

/**
 * "Pixel": the dot-matrix TV set (core's [CorePixelMark], the same grid as `owntv_mark_pixel`), the dots
 * of the play triangle in [triangle].
 */
@Composable
fun PixelMark(modifier: Modifier = Modifier, triangle: Color = stageAccent.accent) {
    Canvas(modifier) {
        val u = size.minDimension / 100f
        CorePixelMark.dots.forEach { d ->
            val color = when (d.kind) {
                CorePixelMark.Kind.BODY -> PixelBody
                CorePixelMark.Kind.TRIANGLE -> triangle
                CorePixelMark.Kind.SIDE -> PixelSide
            }
            drawCircle(color, CorePixelMark.DOT_RADIUS * u, Offset(d.x * u, d.y * u))
        }
        drawCircle(PixelPower, CorePixelMark.POWER_RADIUS * u, Offset(CorePixelMark.POWER_X * u, CorePixelMark.POWER_Y * u))
    }
}

private val PixelBody = Color(CorePixelMark.BODY)
private val PixelSide = Color(CorePixelMark.SIDE)
private val PixelPower = Color(CorePixelMark.POWER)

/**
 * The Jianbo TV mark and product name, shown side by side or stacked.
 */
@Composable
fun BrandLockup(
    modifier: Modifier = Modifier,
    markSize: Int = 36,
    stacked: Boolean = false,
) {
    val parts = @Composable {
        JianboMark(markSize.dp)
        androidx.tv.material3.Text(
            stringResource(R.string.content_brand_name),
            style = stageText((markSize * .58f).toInt().coerceAtLeast(18), 800),
            color = StageColors.Text,
            maxLines = 1,
        )
    }
    if (stacked) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((markSize * 0.2f).dp),
        ) { parts() }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((markSize * 0.23f).dp),
        ) { parts() }
    }
}

@Composable
fun JianboMark(markSize: Dp, modifier: Modifier = Modifier, triangle: Color = stageAccent.accent) {
    Canvas(modifier.size(markSize)) {
        val side = this.size.minDimension
        val origin = Offset(side * .08f, side * .19f)
        val body = androidx.compose.ui.geometry.Size(side * .84f, side * .66f)
        drawRoundRect(Color(0xFF283B56), origin, body, androidx.compose.ui.geometry.CornerRadius(side * .12f))
        drawLine(triangle, Offset(side * .34f, side * .16f), Offset(side * .44f, side * .06f), side * .045f)
        drawLine(triangle, Offset(side * .66f, side * .16f), Offset(side * .56f, side * .06f), side * .045f)
        val play = androidx.compose.ui.graphics.Path().apply {
            moveTo(side * .43f, side * .34f)
            lineTo(side * .64f, side * .52f)
            lineTo(side * .43f, side * .70f)
            close()
        }
        drawPath(play, triangle)
    }
}
