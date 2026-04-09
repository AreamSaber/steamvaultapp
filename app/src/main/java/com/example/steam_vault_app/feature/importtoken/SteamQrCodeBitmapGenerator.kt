package com.example.steam_vault_app.feature.importtoken

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

internal object SteamQrCodeBitmapGenerator {
    fun generate(content: String, sizePx: Int = DEFAULT_SIZE_PX): ImageBitmap {
        require(content.isNotBlank()) { "QR content cannot be blank." }
        val matrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
        )
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(
                    x,
                    y,
                    if (matrix[x, y]) Color.BLACK else Color.WHITE,
                )
            }
        }
        return bitmap.asImageBitmap()
    }

    private const val DEFAULT_SIZE_PX = 960
}
