package com.example.carscanner.ui.login

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.carscanner.CarScannerApp
import com.example.carscanner.auth.SignInResult
import com.example.carscanner.ui.theme.Signal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(val loading: Boolean = false, val error: String? = null)

class LoginViewModel(app: Application) : AndroidViewModel(app) {
    private val auth = (app as CarScannerApp).container.auth
    val googleAvailable = auth.googleAvailable
    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    fun continueAsGuest() = auth.continueAsGuest()

    fun signIn(activityContext: android.content.Context) {
        if (_state.value.loading) return
        _state.value = LoginUiState(loading = true)
        viewModelScope.launch {
            _state.value = when (val result = auth.signIn(activityContext)) {
                SignInResult.Success, SignInResult.Cancelled -> LoginUiState()
                is SignInResult.Failure -> LoginUiState(error = result.message)
            }
        }
    }
}

@Composable
fun LoginScreen(vm: LoginViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1B2540), MaterialTheme.colorScheme.background),
                )
            )
            .systemBarsPadding(),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(Signal.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.DirectionsCar, null, Modifier.size(44.dp), tint = Signal)
            }
            Spacer(Modifier.height(28.dp))
            Text("Spot a car.\nScore its rarity.", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "Point your camera at any car and the AI tells you what it is and how many points it's worth.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            if (vm.googleAvailable) {
                Button(
                    onClick = { vm.signIn(context) },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Color(0xFF1F1F1F))
                    } else {
                        Text("Continue with Google", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = vm::continueAsGuest, enabled = !state.loading) {
                    Text("Continue as guest")
                }
            } else {
                Button(
                    onClick = vm::continueAsGuest,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text("Get started", style = MaterialTheme.typography.titleMedium)
                }
            }
            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
    }
}
