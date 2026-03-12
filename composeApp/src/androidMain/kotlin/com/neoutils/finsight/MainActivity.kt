package com.neoutils.finsight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.neoutils.finsight.report.ActivityHolder
import com.neoutils.finsight.ui.theme.FinsightTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val activityHolder: ActivityHolder by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)

        setContent {
            FinsightTheme {
                App()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activityHolder.set(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        activityHolder.clear()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
