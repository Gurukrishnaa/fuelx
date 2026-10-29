package com.fuelx.util

import android.content.Context
import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.fuelx.ui.theme.FuelXColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts and caches the vibrant accent color from track artwork using Palette.
 * Normalized to ensure high contrast on dark OLED background (#0A0A0B).
 */
object ArtworkAccentHelper {
    private val cache = LruCache<String, Color>(200)

    suspend fun getAccentColor(context: Context, artworkUrl: String?): Color {
        if (artworkUrl.isNullOrBlank()) return FuelXColors.AccentDefault

        synchronized(cache) {
            cache.get(artworkUrl)?.let { return it }
        }

        return withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(Size(120, 120))
                    .allowHardware(false)
                    .build()

                val result = loader.execute(request)
                val bitmap: Bitmap? = result.drawable?.toBitmap()

                if (bitmap != null) {
                    val palette = withContext(Dispatchers.Default) {
                        Palette.from(bitmap).generate()
                    }
                    val rgb = palette.vibrantSwatch?.rgb
                        ?: palette.lightVibrantSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb

                    val extractedColor = if (rgb != null) {
                        normalizeColor(rgb)
                    } else {
                        FuelXColors.AccentDefault
                    }

                    synchronized(cache) {
                        cache.put(artworkUrl, extractedColor)
                    }
                    extractedColor
                } else {
                    FuelXColors.AccentDefault
                }
            } catch (_: Exception) {
                FuelXColors.AccentDefault
            }
        }
    }

    /**
     * Clamps saturation and lightness so extracted color always looks vivid and legible on dark surfaces.
     */
    private fun normalizeColor(rgb: Int): Color {
        val hsl = FloatArray(3)
        android.graphics.Color.colorToHSV(rgb, hsl)
        // Clamp saturation to 0.55..0.85 and value (brightness) to 0.60..0.85
        hsl[1] = hsl[1].coerceIn(0.55f, 0.85f)
        hsl[2] = hsl[2].coerceIn(0.60f, 0.85f)
        val normalizedRgb = android.graphics.Color.HSVToColor(hsl)
        return Color(normalizedRgb)
    }
}

