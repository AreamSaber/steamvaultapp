package com.example.steam_vault_app.platform.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.steam_vault_app.R
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher

data class BiometricAvailability(
    val available: Boolean,
    val message: String?,
)

class AndroidBiometricPromptController(
    private val activity: FragmentActivity,
) {
    fun checkAvailability(): BiometricAvailability {
        val biometricManager = BiometricManager.from(activity)
        return when (
            biometricManager.canAuthenticate(BIOMETRIC_AUTHENTICATORS)
        ) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability(
                available = true,
                message = string(R.string.biometric_availability_available),
            )

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability(
                available = false,
                message = string(R.string.biometric_availability_no_hardware),
            )

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability(
                available = false,
                message = string(R.string.biometric_availability_hardware_unavailable),
            )

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability(
                available = false,
                message = string(R.string.biometric_availability_none_enrolled),
            )

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAvailability(
                available = false,
                message = string(R.string.biometric_availability_security_update_required),
            )

            else -> BiometricAvailability(
                available = false,
                message = string(R.string.biometric_availability_unavailable),
            )
        }
    }

    suspend fun authenticateCipher(
        title: String,
        subtitle: String,
        cipher: Cipher,
    ): Cipher = suspendCancellableCoroutine { continuation ->
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    val authenticatedCipher = result.cryptoObject?.cipher
                    if (authenticatedCipher == null) {
                        continuation.resumeWithException(
                            IllegalStateException(string(R.string.biometric_auth_missing_cipher)),
                        )
                        return
                    }
                    continuation.resume(authenticatedCipher)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    val isCanceled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    continuation.resumeWithException(
                        BiometricAuthException(
                            message = if (isCanceled) {
                                string(R.string.biometric_auth_cancelled)
                            } else {
                                errString.toString()
                            },
                            isUserCanceled = isCanceled,
                        ),
                    )
                }
            },
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_AUTHENTICATORS)
            .setNegativeButtonText(string(R.string.common_action_cancel))
            .build()

        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    private fun string(resId: Int, vararg formatArgs: Any): String {
        return activity.getString(resId, *formatArgs)
    }

    companion object {
        private const val BIOMETRIC_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG
    }
}

class BiometricAuthException(
    override val message: String,
    val isUserCanceled: Boolean,
) : IllegalStateException(message)
