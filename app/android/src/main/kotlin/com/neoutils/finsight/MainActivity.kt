package com.neoutils.finsight

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.neoutils.finsight.ui.App

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)

        // Before anything composes: whatever reads the clock must read the shifted one from the
        // start. A no-op outside a debug build.
        applyTimeTravel()

        setContent {
            App()
        }
    }

    /**
     * A launch that finds this activity alive delivers its extras here instead of to [onCreate] —
     * which is what happens whenever the app is relaunched without being stopped first. Reading
     * them in only one of the two places is how a test-only clock shift goes missing without
     * anything saying so.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyTimeTravel()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
