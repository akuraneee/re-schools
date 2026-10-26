package io.github.akuraneee.reschools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.akuraneee.reschools.ui.AppRoot
import io.github.akuraneee.reschools.ui.theme.ReSchoolsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ReSchoolsTheme { AppRoot() } }
    }
}
