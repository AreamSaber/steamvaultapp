package com.example.steam_vault_app.ui.common

sealed interface AppUiState<out T> {
    data object Loading : AppUiState<Nothing>

    data object Empty : AppUiState<Nothing>

    data class Success<T>(val value: T) : AppUiState<T>

    data class Error(
        val message: String,
        val canRetry: Boolean = true,
    ) : AppUiState<Nothing>
}
