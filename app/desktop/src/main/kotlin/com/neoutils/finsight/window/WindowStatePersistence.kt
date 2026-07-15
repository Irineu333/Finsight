package com.neoutils.finsight.window

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import com.russhwolf.settings.Settings
import java.awt.GraphicsEnvironment
import java.awt.Rectangle

/** Tamanhos de referência da janela principal do desktop, em dp. */
object WindowDefaults {
    // Acima do breakpoint Expanded (840dp), abre já mostrando o detail pane.
    val Size = DpSize(1100.dp, 760.dp)

    // Abaixo do breakpoint Medium (600dp), preservando o modo compacto (bottom bar).
    val MinSize = DpSize(480.dp, 600.dp)
}

/** Estado inicial resolvido (com clamp/fallback aplicados) para alimentar `rememberWindowState`. */
data class InitialWindowState(
    val size: DpSize,
    val position: WindowPosition,
    val placement: WindowPlacement,
)

/** Modelo serializável do estado da janela persistido em `Settings`. */
data class WindowStateData(
    val width: Float?,
    val height: Float?,
    val x: Float?,
    val y: Float?,
    val placement: WindowPlacement,
)

/**
 * Carrega e persiste o estado da janela principal do desktop via [Settings].
 * Concern 100% desktop; mantém `main.kt` apenas orquestrando.
 */
class WindowStatePersistence(private val settings: Settings) {

    fun save(size: DpSize, position: WindowPosition, placement: WindowPlacement) {
        settings.putFloat(KEY_WIDTH, size.width.value)
        settings.putFloat(KEY_HEIGHT, size.height.value)
        if (position is WindowPosition.Absolute) {
            settings.putFloat(KEY_X, position.x.value)
            settings.putFloat(KEY_Y, position.y.value)
        }
        settings.putString(KEY_PLACEMENT, placement.name)
    }

    /**
     * Resolve o estado inicial da janela:
     * - sem estado salvo → padrão, centralizado;
     * - tamanho salvo → ajustado ao mínimo ([WindowDefaults.MinSize]);
     * - posição salva off-screen (não intersecciona [screenBounds]) → descartada (centralizado);
     * - placement salvo (inclusive Maximized) preservado, sempre com bounds de restauração válidos.
     */
    fun load(screenBounds: List<Rectangle> = currentScreenBounds()): InitialWindowState {
        val data = read()
        val hasSize = data.width != null && data.height != null

        val size = if (data.width != null && data.height != null) {
            DpSize(
                width = data.width.coerceAtLeast(WindowDefaults.MinSize.width.value).dp,
                height = data.height.coerceAtLeast(WindowDefaults.MinSize.height.value).dp,
            )
        } else {
            WindowDefaults.Size
        }

        val hasOnScreenPosition = hasSize && data.x != null && data.y != null &&
            isOnScreen(data.x, data.y, size, screenBounds)

        val position = if (hasOnScreenPosition) {
            WindowPosition.Absolute(data.x.dp, data.y.dp)
        } else {
            WindowPosition.Aligned(Alignment.Center)
        }

        return InitialWindowState(size, position, data.placement)
    }

    private fun read(): WindowStateData {
        val placement = settings.getStringOrNull(KEY_PLACEMENT)
            ?.let { name -> WindowPlacement.entries.firstOrNull { it.name == name } }
            ?: WindowPlacement.Floating
        return WindowStateData(
            width = settings.getFloatOrNull(KEY_WIDTH),
            height = settings.getFloatOrNull(KEY_HEIGHT),
            x = settings.getFloatOrNull(KEY_X),
            y = settings.getFloatOrNull(KEY_Y),
            placement = placement,
        )
    }

    private fun isOnScreen(x: Float, y: Float, size: DpSize, screenBounds: List<Rectangle>): Boolean {
        val windowRect = Rectangle(
            x.toInt(),
            y.toInt(),
            size.width.value.toInt(),
            size.height.value.toInt(),
        )
        return screenBounds.any { it.intersects(windowRect) }
    }

    private fun currentScreenBounds(): List<Rectangle> =
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .screenDevices
            .map { it.defaultConfiguration.bounds }

    private companion object {
        const val KEY_WIDTH = "window_width"
        const val KEY_HEIGHT = "window_height"
        const val KEY_X = "window_x"
        const val KEY_Y = "window_y"
        const val KEY_PLACEMENT = "window_placement"
    }
}
