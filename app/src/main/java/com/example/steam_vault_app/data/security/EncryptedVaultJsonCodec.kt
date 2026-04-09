package com.example.steam_vault_app.data.security

import com.example.steam_vault_app.domain.security.EncryptedVault
import org.json.JSONObject

object EncryptedVaultJsonCodec {
    fun encode(encryptedVault: EncryptedVault): String {
        return JSONObject()
            .put("version", encryptedVault.version)
            .put("kdf_name", encryptedVault.kdfName)
            .put("cipher_name", encryptedVault.cipherName)
            .put("salt_base64", encryptedVault.saltBase64)
            .put("nonce_base64", encryptedVault.nonceBase64)
            .put("ciphertext_base64", encryptedVault.ciphertextBase64)
            .toString()
    }

    fun decode(rawPayload: String): EncryptedVault {
        return decode(JSONObject(rawPayload))
    }

    fun decode(json: JSONObject): EncryptedVault {
        return EncryptedVault(
            version = json.getInt("version"),
            kdfName = json.getString("kdf_name"),
            cipherName = json.getString("cipher_name"),
            saltBase64 = json.optString("salt_base64"),
            nonceBase64 = json.getString("nonce_base64"),
            ciphertextBase64 = json.getString("ciphertext_base64"),
        )
    }
}
