package com.example.steam_vault_app.data.security

import org.json.JSONObject

data class WrappedVaultKeyEnvelope(
    val version: Int,
    val passwordWrappedKey: WrappedKeyCopy,
    val keystoreWrappedKey: KeystoreEncryptedPayload?,
    val biometricWrappedKey: KeystoreEncryptedPayload?,
)

data class WrappedKeyCopy(
    val cipherName: String,
    val nonceBase64: String,
    val ciphertextBase64: String,
)

object WrappedVaultKeyEnvelopeCodec {
    fun encode(envelope: WrappedVaultKeyEnvelope): String {
        return JSONObject()
            .put("version", envelope.version)
            .put("password_wrapped_key", wrappedKeyCopyToJson(envelope.passwordWrappedKey))
            .put(
                "keystore_wrapped_key",
                envelope.keystoreWrappedKey?.let(::keystoreWrappedKeyToJson),
            )
            .put(
                "biometric_wrapped_key",
                envelope.biometricWrappedKey?.let(::keystoreWrappedKeyToJson),
            )
            .toString()
    }

    fun decode(serializedEnvelope: String): WrappedVaultKeyEnvelope {
        return decode(JSONObject(serializedEnvelope))
    }

    fun decode(json: JSONObject): WrappedVaultKeyEnvelope {
        return WrappedVaultKeyEnvelope(
            version = json.optInt("version", DEFAULT_VERSION),
            passwordWrappedKey = jsonToWrappedKeyCopy(
                json.getJSONObject("password_wrapped_key"),
            ),
            keystoreWrappedKey = json.optJSONObject("keystore_wrapped_key")
                ?.let(::jsonToKeystoreWrappedKey),
            biometricWrappedKey = json.optJSONObject("biometric_wrapped_key")
                ?.let(::jsonToKeystoreWrappedKey),
        )
    }

    private fun wrappedKeyCopyToJson(copy: WrappedKeyCopy): JSONObject {
        return JSONObject()
            .put("cipher_name", copy.cipherName)
            .put("nonce_base64", copy.nonceBase64)
            .put("ciphertext_base64", copy.ciphertextBase64)
    }

    private fun jsonToWrappedKeyCopy(json: JSONObject): WrappedKeyCopy {
        return WrappedKeyCopy(
            cipherName = json.optString("cipher_name", DEFAULT_CIPHER_NAME),
            nonceBase64 = json.getString("nonce_base64"),
            ciphertextBase64 = json.getString("ciphertext_base64"),
        )
    }

    private fun jsonToKeystoreWrappedKey(json: JSONObject): KeystoreEncryptedPayload {
        return KeystoreEncryptedPayload(
            cipherName = json.optString("cipher_name", DEFAULT_CIPHER_NAME),
            nonceBase64 = json.getString("nonce_base64"),
            ciphertextBase64 = json.getString("ciphertext_base64"),
        )
    }

    private fun keystoreWrappedKeyToJson(payload: KeystoreEncryptedPayload): JSONObject {
        return JSONObject()
            .put("cipher_name", payload.cipherName)
            .put("nonce_base64", payload.nonceBase64)
            .put("ciphertext_base64", payload.ciphertextBase64)
    }

    private const val DEFAULT_VERSION = 1
    private const val DEFAULT_CIPHER_NAME = "aes-256-gcm"
}
