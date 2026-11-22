package com.usman.musicplayer.data.listentogether

import android.graphics.Bitmap
import android.graphics.Color
import java.net.URLDecoder
import java.net.URLEncoder
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

data class ParsedListenTogetherInvite(
    val roomCode: String,
    val serverUrl: String? = null
)

/**
 * Encodes and decodes Listen Together deep links and QR codes using ZXing Core.
 * Deep link format:
 *   usmanmusic://listen-together?code=ABC234
 *   (optionally includes &server=wss%3A%2F%2F... when sharing a custom server endpoint)
 */
object ListenTogetherQrCodec {

    const val DEEP_LINK_SCHEME = "usmanmusic"
    const val DEEP_LINK_HOST = "listen-together"
    private val roomCodePattern = Regex("^[A-Z0-9]{6}$")

    fun buildInviteUri(roomCode: String, serverUrl: String? = null): String {
        val cleanCode = normalizeRoomCode(roomCode)
        val encodedServer = serverUrl?.trim()?.takeIf { it.isNotBlank() }?.let {
            URLEncoder.encode(it, "UTF-8")
        }
        return if (!encodedServer.isNullOrBlank()) {
            "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST?code=$cleanCode&server=$encodedServer"
        } else {
            "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST?code=$cleanCode"
        }
    }

    fun buildShareMessage(roomCode: String, trackTitle: String?, serverUrl: String? = null): String {
        val cleanCode = normalizeRoomCode(roomCode)
        val inviteLink = buildInviteUri(cleanCode, serverUrl)
        val trackLine = trackTitle?.takeIf { it.isNotBlank() }
            ?.let { "\nNow Playing: $it" }
            ?: ""
        return "Join my USMAN MUSIC Listen Together room!\nRoom Code: $cleanCode$trackLine\nOpen in app: $inviteLink"
    }

    fun normalizeRoomCode(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.uppercase().replace(Regex("[^A-Z0-9]"), "").take(6)
    }

    fun isValidRoomCode(code: String?): Boolean {
        val clean = normalizeRoomCode(code)
        return roomCodePattern.matches(clean)
    }

    /**
     * Parses a raw QR payload, deep link URI, share text, or plain 6-character room code.
     */
    fun parseInvitePayload(raw: String?): ParsedListenTogetherInvite? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return null

        // 1. Try deep link / URL parsing
        if (trimmed.contains("://")) {
            val codeMatch = Regex("""[?&]code=([A-Za-z0-9]{6})""", RegexOption.IGNORE_CASE).find(trimmed)
            val code = codeMatch?.groupValues?.getOrNull(1)?.uppercase()
            if (!code.isNullOrBlank() && roomCodePattern.matches(code)) {
                val serverMatch = Regex("""[?&]server=([^&\s]+)""", RegexOption.IGNORE_CASE).find(trimmed)
                val serverUrl = serverMatch?.groupValues?.getOrNull(1)?.let {
                    runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull()
                }
                return ParsedListenTogetherInvite(roomCode = code, serverUrl = serverUrl)
            }
        }

        // 2. Try "Room Code: XXXXXX" pattern from shared message text
        val labelMatch = Regex("""(?:room\s*code\s*[:#-]\s*)([A-Za-z0-9]{6})""", RegexOption.IGNORE_CASE).find(trimmed)
        if (labelMatch != null) {
            val code = labelMatch.groupValues[1].uppercase()
            if (roomCodePattern.matches(code)) {
                return ParsedListenTogetherInvite(roomCode = code)
            }
        }

        // 3. Direct 6-character room code (must have exactly 6 alphanumeric characters, not truncated longer words)
        val stripped = trimmed.uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (stripped.length == 6 && roomCodePattern.matches(stripped) && trimmed.length <= 10) {
            return ParsedListenTogetherInvite(roomCode = stripped)
        }
        return null
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        if (content.isBlank() || sizePx <= 0) return null
        return runCatching {
            val hints = mapOf(
                com.google.zxing.EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                com.google.zxing.EncodeHintType.MARGIN to 1
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val pixels = IntArray(sizePx * sizePx)
            for (y in 0 until sizePx) {
                val rowOffset = y * sizePx
                for (x in 0 until sizePx) {
                    pixels[rowOffset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
            }
        }.getOrNull()
    }

    fun decodeFromBitmap(bitmap: Bitmap): String? {
        return runCatching {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            createQrReader().decodeWithState(binaryBitmap).text
        }.getOrNull()
    }

    fun decodeFromYuvPlane(
        yPlaneBytes: ByteArray,
        width: Int,
        height: Int
    ): String? {
        return runCatching {
            val source = PlanarYUVLuminanceSource(
                yPlaneBytes,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            createQrReader().decodeWithState(binaryBitmap).text
        }.getOrNull()
    }

    private fun createQrReader(): MultiFormatReader {
        return MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true
                )
            )
        }
    }
}
