package com.example.carscanner.ui.scan

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.carscanner.CarScannerApp
import com.example.carscanner.data.CarEntity
import com.example.carscanner.data.SaveOutcome
import com.example.carscanner.domain.ScanException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object Analyzing : ScanUiState
    data class Success(val car: CarEntity, val duplicate: Boolean) : ScanUiState
    data object NoCar : ScanUiState
    data class AlreadyScanned(val car: CarEntity) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as CarScannerApp).container.repository
    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state = _state.asStateFlow()

    fun analyze(userId: String, uri: Uri) {
        if (_state.value is ScanUiState.Analyzing) return
        _state.value = ScanUiState.Analyzing
        viewModelScope.launch {
            _state.value = try {
                when (val result = repo.scan(userId, uri)) {
                    is SaveOutcome.Saved -> ScanUiState.Success(result.car, result.duplicate)
                    SaveOutcome.NoCar -> ScanUiState.NoCar
                    is SaveOutcome.AlreadyScanned -> ScanUiState.AlreadyScanned(result.car)
                }
            } catch (e: ScanException) {
                ScanUiState.Error(e.message ?: "Scan failed.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ScanUiState.Error("Something went wrong. Try again.")
            }
        }
    }

    fun reset() { _state.value = ScanUiState.Idle }
}
