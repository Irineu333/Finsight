package com.neoutils.finsight.window

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import java.awt.Rectangle
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WindowStatePersistenceTest {

    private val primaryScreen = listOf(Rectangle(0, 0, 1920, 1080))

    private fun persistence(settings: Settings = PropertiesSettings(Properties())) = WindowStatePersistence(settings)

    @Test
    fun `first use falls back to default centered floating`() {
        val state = persistence().load(primaryScreen)

        assertEquals(WindowDefaults.Size, state.size)
        assertIs<WindowPosition.Aligned>(state.position)
        assertEquals(WindowPlacement.Floating, state.placement)
    }

    @Test
    fun `round trips saved size position and placement`() {
        val settings = PropertiesSettings(Properties())
        persistence(settings).save(
            size = DpSize(1000.dp, 800.dp),
            position = WindowPosition.Absolute(100.dp, 120.dp),
            placement = WindowPlacement.Floating,
        )

        val state = persistence(settings).load(primaryScreen)

        assertEquals(DpSize(1000.dp, 800.dp), state.size)
        val position = assertIs<WindowPosition.Absolute>(state.position)
        assertEquals(100.dp, position.x)
        assertEquals(120.dp, position.y)
        assertEquals(WindowPlacement.Floating, state.placement)
    }

    @Test
    fun `size below minimum is clamped to minimum`() {
        val settings = PropertiesSettings(Properties())
        persistence(settings).save(
            size = DpSize(200.dp, 300.dp),
            position = WindowPosition.Absolute(10.dp, 10.dp),
            placement = WindowPlacement.Floating,
        )

        val state = persistence(settings).load(primaryScreen)

        assertEquals(WindowDefaults.MinSize, state.size)
    }

    @Test
    fun `off screen position falls back to centered keeping saved size`() {
        val settings = PropertiesSettings(Properties())
        persistence(settings).save(
            size = DpSize(1000.dp, 800.dp),
            position = WindowPosition.Absolute(5000.dp, 5000.dp),
            placement = WindowPlacement.Floating,
        )

        val state = persistence(settings).load(primaryScreen)

        assertIs<WindowPosition.Aligned>(state.position)
        assertEquals(DpSize(1000.dp, 800.dp), state.size)
    }

    @Test
    fun `position on screen with negative coordinates is restored`() {
        val settings = PropertiesSettings(Properties())
        val leftMonitor = Rectangle(-1920, 0, 1920, 1080)
        persistence(settings).save(
            size = DpSize(1000.dp, 800.dp),
            position = WindowPosition.Absolute((-1800).dp, 100.dp),
            placement = WindowPlacement.Floating,
        )

        val state = persistence(settings).load(primaryScreen + leftMonitor)

        val position = assertIs<WindowPosition.Absolute>(state.position)
        assertEquals((-1800).dp, position.x)
    }

    @Test
    fun `maximized placement is restored with valid restore bounds`() {
        val settings = PropertiesSettings(Properties())
        persistence(settings).save(
            size = DpSize(1000.dp, 800.dp),
            position = WindowPosition.Absolute(100.dp, 120.dp),
            placement = WindowPlacement.Maximized,
        )

        val state = persistence(settings).load(primaryScreen)

        assertEquals(WindowPlacement.Maximized, state.placement)
        assertEquals(DpSize(1000.dp, 800.dp), state.size)
        assertIs<WindowPosition.Absolute>(state.position)
    }

    @Test
    fun `maximized without saved size falls back to default restore bounds`() {
        val settings = PropertiesSettings(Properties()).apply { putString("window_placement", "Maximized") }

        val state = persistence(settings).load(primaryScreen)

        assertEquals(WindowPlacement.Maximized, state.placement)
        assertEquals(WindowDefaults.Size, state.size)
        assertIs<WindowPosition.Aligned>(state.position)
        assertTrue(state.size.width >= WindowDefaults.MinSize.width)
    }
}
