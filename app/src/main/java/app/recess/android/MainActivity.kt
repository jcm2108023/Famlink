package app.recess.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.recess.android.ui.FamilyViewModel
import app.recess.android.ui.RecessApp
import app.recess.android.ui.theme.RecessTheme

class MainActivity : ComponentActivity() {
    private val vm: FamilyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { RecessTheme { RecessApp(vm) } }
    }
}
