package com.resqedge.lite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.resqedge.lite.ui.responder.ResponderScreen
import com.resqedge.lite.ui.theme.ResQEdgeLiteTheme

enum class Screen { HOME, CITIZEN, RESPONDER }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ResQEdgeLiteTheme {
                var screen by remember { mutableStateOf(Screen.HOME) }

                BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    Box(modifier = Modifier.padding(inner).fillMaxSize()) {
                        when (screen) {
                            Screen.HOME -> HomeScreen(
                                onCitizen = { screen = Screen.CITIZEN },
                                onResponder = { screen = Screen.RESPONDER }
                            )
                            Screen.CITIZEN -> PlaceholderScreen(
                                title = "Citizen mode",
                                note = "SOS form goes here (next stage).",
                                onBack = { screen = Screen.HOME }
                            )
                            Screen.RESPONDER -> ResponderScreen(
                                onBack = { screen = Screen.HOME }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onCitizen: () -> Unit, onResponder: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ResQ-Mesh Lite", style = MaterialTheme.typography.headlineMedium)
        Text("Offline SOS for disaster zones", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(40.dp))
        Button(onClick = onCitizen, modifier = Modifier.fillMaxWidth()) {
            Text("I need help (Citizen)")
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onResponder, modifier = Modifier.fillMaxWidth()) {
            Text("I am a rescuer (Responder)")
        }
    }
}

@Composable
fun PlaceholderScreen(title: String, note: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(note)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}
