package com.example.carscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.carscanner.ui.garage.GarageScreen
import com.example.carscanner.ui.login.LoginScreen
import com.example.carscanner.ui.profile.ProfileScreen
import com.example.carscanner.ui.scan.ScanScreen
import com.example.carscanner.ui.theme.CarScannerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CarScannerApp).container
        setContent {
            CarScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    AppRoot(container)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(container: AppContainer) {
    val user by container.auth.user.collectAsStateWithLifecycle(initialValue = container.auth.currentUser)
    val signedIn = user

    if (signedIn == null) {
        LoginScreen()
    } else {
        val nav = rememberNavController()
        val scope = rememberCoroutineScope()
        NavHost(navController = nav, startDestination = "garage") {
            composable("garage") {
                GarageScreen(
                    user = signedIn,
                    onScan = { nav.navigate("scan") },
                    onProfile = { nav.navigate("profile") },
                )
            }
            composable("scan") {
                ScanScreen(userId = signedIn.uid, onClose = { nav.popBackStack() })
            }
            composable("profile") {
                ProfileScreen(
                    user = signedIn,
                    onBack = { nav.popBackStack() },
                    onSignOut = { scope.launch { container.auth.signOut() } },
                )
            }
        }
    }
}