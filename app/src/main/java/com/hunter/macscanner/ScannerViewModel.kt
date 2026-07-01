package com.hunter.macscanner

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

class ScannerViewModel : ViewModel() {
    private val repository = ScannerRepository()
    val uiState: StateFlow<ScannerState> = repository.state

    fun startScan(url: String, speed: Int, startMac: String?) {
        repository.startScanning(url, speed, startMac)
    }

    fun stopScan() {
        repository.stopScanning()
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopScanning()
    }
}