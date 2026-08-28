package com.apkupdater.util

import androidx.compose.material3.SnackbarVisuals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class SnackBar {

    private val snackBars = MutableSharedFlow<SnackbarVisuals>()

    fun flow(): SharedFlow<SnackbarVisuals> = snackBars

    fun snackBar(
        scope: CoroutineScope,
        message: SnackbarVisuals
    ) = scope.launch { snackBars.emit(message) }

}
