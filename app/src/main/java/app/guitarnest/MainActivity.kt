package app.guitarnest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.guitarnest.ui.AppRoot
import app.guitarnest.ui.theme.GuitarNestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GuitarNestTheme {
                AppRoot()
            }
        }
    }
}
