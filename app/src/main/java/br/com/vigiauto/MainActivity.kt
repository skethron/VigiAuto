package br.com.vigiauto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.vigiauto.ui.AppRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as VigiAutoApp).container
        setContent {
            AppRoot(container)
        }
    }
}
