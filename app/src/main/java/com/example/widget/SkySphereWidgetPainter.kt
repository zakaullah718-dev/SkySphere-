package com.example.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.data.models.CityWeather
import com.example.data.models.WeatherCondition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object SkySphereWidgetPainter {

    data class WeatherTheme(
        val topColor: Int,
        val bottomColor: Int,
        val accentColor: Int,
        val cardBgColor: Int,
        val textColorPrimary: Int,
        val textColorSecondary: Int
    )

    fun getThemeForWeather(cityWeather: CityWeather): WeatherTheme {
        val condition = cityWeather.weatherDetails.condition
        val isNight = cityWeather.isNight

        if (isNight) {
            return WeatherTheme(
                topColor = Color.parseColor("#0F172A"),
                bottomColor = Color.parseColor("#020617"),
                accentColor = Color.parseColor("#38BDF8"),
                cardBgColor = Color.parseColor("#1E293B"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#94A3B8")
            )
        }

        return when (condition) {
            WeatherCondition.SUNNY -> WeatherTheme(
                topColor = Color.parseColor("#D97706"),
                bottomColor = Color.parseColor("#B45309"),
                accentColor = Color.parseColor("#FDE047"),
                cardBgColor = Color.parseColor("#78350F"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#FEF08A")
            )
            WeatherCondition.PARTLY_CLOUDY -> WeatherTheme(
                topColor = Color.parseColor("#0284C7"),
                bottomColor = Color.parseColor("#0369A1"),
                accentColor = Color.parseColor("#BAE6FD"),
                cardBgColor = Color.parseColor("#0C4A6E"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#E0F2FE")
            )
            WeatherCondition.CLOUDY -> WeatherTheme(
                topColor = Color.parseColor("#475569"),
                bottomColor = Color.parseColor("#334155"),
                accentColor = Color.parseColor("#CBD5E1"),
                cardBgColor = Color.parseColor("#1E293B"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#94A3B8")
            )
            WeatherCondition.RAINY -> WeatherTheme(
                topColor = Color.parseColor("#0F766E"),
                bottomColor = Color.parseColor("#115E59"),
                accentColor = Color.parseColor("#5EEAD4"),
                cardBgColor = Color.parseColor("#134E4A"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#CCFBF1")
            )
            WeatherCondition.STORM -> WeatherTheme(
                topColor = Color.parseColor("#581C87"),
                bottomColor = Color.parseColor("#3B0764"),
                accentColor = Color.parseColor("#E9D5FF"),
                cardBgColor = Color.parseColor("#2E1065"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#F3E8FF")
            )
            WeatherCondition.SNOWY -> WeatherTheme(
                topColor = Color.parseColor("#0891B2"),
                bottomColor = Color.parseColor("#0E7490"),
                accentColor = Color.parseColor("#CFFAFE"),
                cardBgColor = Color.parseColor("#164E63"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#ECFEFF")
            )
            WeatherCondition.FOGGY -> WeatherTheme(
                topColor = Color.parseColor("#64748B"),
                bottomColor = Color.parseColor("#475569"),
                accentColor = Color.parseColor("#E2E8F0"),
                cardBgColor = Color.parseColor("#334155"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#CBD5E1")
            )
        }
    }

    private fun formatTemp(temp: Int, isCelsius: Boolean): String {
        val t = if (isCelsius) temp else (temp * 9 / 5) + 32
        return "$t°"
    }

    private fun formatSpeed(kmh: Double, isCelsius: Boolean): String {
        return if (isCelsius) {
            "${kmh.toInt()} km/h"
        } else {
            "${(kmh * 0.621371).toInt()} mph"
        }
    }

    // --- DRAW WIDGET 1x1 ---
    fun drawWidget1x1(context: Context, cityWeather: CityWeather, isCelsius: Boolean): Bitmap {
        val width = 220
        val height = 220
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val theme = getThemeForWeather(cityWeather)
        drawBackgroundCard(canvas, width.toFloat(), height.toFloat(), theme)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        drawWeatherIcon(canvas, 110f, 75f, 45f, cityWeather.weatherDetails.condition, cityWeather.isNight, theme)

        // Temp
        paint.color = theme.textColorPrimary
        paint.textSize = 52f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(formatTemp(cityWeather.weatherDetails.currentTemp, isCelsius), 110f, 155f, paint)

        // City
        paint.color = theme.textColorSecondary
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(cityWeather.cityName.uppercase(Locale.getDefault()), 110f, 185f, paint)

        return bitmap
    }

    // --- DRAW WIDGET 2x2 ---
    fun drawWidget2x2(context: Context, cityWeather: CityWeather, isCelsius: Boolean): Bitmap {
        val width = 360
        val height = 360
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val theme = getThemeForWeather(cityWeather)
        drawBackgroundCard(canvas, width.toFloat(), height.toFloat(), theme)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Location
        paint.color = theme.textColorSecondary
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        val locationText = cityWeather.cityName.uppercase(Locale.getDefault())
        canvas.drawText(locationText, 30f, 50f, paint)

        // Weather Icon
        drawWeatherIcon(canvas, 270f, 130f, 60f, cityWeather.weatherDetails.condition, cityWeather.isNight, theme)

        // Temperature
        paint.color = theme.textColorPrimary
        paint.textSize = 84f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val tempStr = formatTemp(cityWeather.weatherDetails.currentTemp, isCelsius)
        canvas.drawText(tempStr, 30f, 150f, paint)

        // Condition Name
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(cityWeather.weatherDetails.condition.displayName, 30f, 210f, paint)

        // High / Low Card
        val highLowText = "H: ${formatTemp(cityWeather.weatherDetails.highTemp, isCelsius)}   L: ${formatTemp(cityWeather.weatherDetails.lowTemp, isCelsius)}"
        paint.color = theme.textColorSecondary
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(highLowText, 30f, 250f, paint)

        // Feels Like
        val feelsText = "Feels like ${formatTemp(cityWeather.weatherDetails.feelsLike, isCelsius)}"
        paint.textSize = 18f
        canvas.drawText(feelsText, 30f, 290f, paint)

        return bitmap
    }

    // --- DRAW WIDGET 4x2 ---
    fun drawWidget4x2(context: Context, cityWeather: CityWeather, isCelsius: Boolean): Bitmap {
        val width = 720
        val height = 360
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val theme = getThemeForWeather(cityWeather)
        drawBackgroundCard(canvas, width.toFloat(), height.toFloat(), theme)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Left Header: Location
        paint.color = theme.textColorPrimary
        paint.textSize = 30f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT

        val displayLoc = if (!cityWeather.region.isNullOrBlank()) {
            "${cityWeather.cityName.uppercase()}, ${cityWeather.region!!.uppercase()}"
        } else {
            "${cityWeather.cityName.uppercase()}, ${cityWeather.country.uppercase()}"
        }
        canvas.drawText(displayLoc, 40f, 65f, paint)

        // Condition text
        paint.color = theme.textColorSecondary
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(cityWeather.weatherDetails.condition.displayName, 40f, 110f, paint)

        // Updated time
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val updatedText = "Updated ${timeFmt.format(Date())}"
        paint.textSize = 18f
        canvas.drawText(updatedText, 40f, 150f, paint)

        // Left Bottom Pills (High/Low & Wind)
        drawMiniPill(canvas, 40f, 210f, 180f, 60f, "HIGH / LOW", "H:${formatTemp(cityWeather.weatherDetails.highTemp, isCelsius)} L:${formatTemp(cityWeather.weatherDetails.lowTemp, isCelsius)}", theme)
        drawMiniPill(canvas, 240f, 210f, 180f, 60f, "WIND", formatSpeed(cityWeather.weatherDetails.windSpeed, isCelsius), theme)

        // Right Hero: Large Weather Icon
        drawWeatherIcon(canvas, 580f, 120f, 75f, cityWeather.weatherDetails.condition, cityWeather.isNight, theme)

        // Right Temperature
        paint.color = theme.textColorPrimary
        paint.textSize = 96f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(formatTemp(cityWeather.weatherDetails.currentTemp, isCelsius), 480f, 140f, paint)

        // Feels like
        paint.color = theme.textColorSecondary
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Feels like ${formatTemp(cityWeather.weatherDetails.feelsLike, isCelsius)}", 480f, 180f, paint)

        return bitmap
    }

    // --- DRAW WIDGET 4x4 (PREMIUM WIDGET) ---
    fun drawWidget4x4(context: Context, cityWeather: CityWeather, isCelsius: Boolean): Bitmap {
        val width = 720
        val height = 720
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val theme = getThemeForWeather(cityWeather)
        drawBackgroundCard(canvas, width.toFloat(), height.toFloat(), theme)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Top Row: Location Name
        paint.color = theme.textColorPrimary
        paint.textSize = 32f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT

        val displayLoc = if (!cityWeather.region.isNullOrBlank()) {
            "${cityWeather.cityName.uppercase()}, ${cityWeather.region!!.uppercase()}"
        } else {
            "${cityWeather.cityName.uppercase()}, ${cityWeather.country.uppercase()}"
        }
        canvas.drawText(displayLoc, 40f, 65f, paint)

        // Last Updated
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val updatedText = "Updated ${timeFmt.format(Date())}"
        paint.color = theme.textColorSecondary
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(updatedText, 40f, 100f, paint)

        // Hero Weather Illustration (Center-Right)
        drawWeatherIcon(canvas, 560f, 180f, 95f, cityWeather.weatherDetails.condition, cityWeather.isNight, theme)

        // Hero Temperature
        paint.color = theme.textColorPrimary
        paint.textSize = 120f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(formatTemp(cityWeather.weatherDetails.currentTemp, isCelsius), 40f, 210f, paint)

        // Condition & High/Low
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val condText = cityWeather.weatherDetails.condition.displayName
        canvas.drawText(condText, 40f, 260f, paint)

        paint.color = theme.textColorSecondary
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val highLowText = "High: ${formatTemp(cityWeather.weatherDetails.highTemp, isCelsius)}   Low: ${formatTemp(cityWeather.weatherDetails.lowTemp, isCelsius)}   Feels: ${formatTemp(cityWeather.weatherDetails.feelsLike, isCelsius)}"
        canvas.drawText(highLowText, 40f, 300f, paint)

        // 4 WEATHER METRICS GRID CARDS (Y = 340 to 460)
        val cardWidth = 148f
        val cardHeight = 110f
        val startX = 40f
        val spacing = 16f

        // Metric 1: Humidity
        drawMetricCard(canvas, startX + 0 * (cardWidth + spacing), 340f, cardWidth, cardHeight, "HUMIDITY", "${cityWeather.weatherDetails.humidity}%", theme)
        // Metric 2: Wind
        drawMetricCard(canvas, startX + 1 * (cardWidth + spacing), 340f, cardWidth, cardHeight, "WIND", formatSpeed(cityWeather.weatherDetails.windSpeed, isCelsius), theme)
        // Metric 3: UV Index
        drawMetricCard(canvas, startX + 2 * (cardWidth + spacing), 340f, cardWidth, cardHeight, "UV INDEX", "${cityWeather.weatherDetails.uvIndex} Mod", theme)
        // Metric 4: Chance of Rain / Air Quality
        val precip = cityWeather.weatherDetails.hourlyForecast.firstOrNull()?.precipitationChance ?: 0
        drawMetricCard(canvas, startX + 3 * (cardWidth + spacing), 340f, cardWidth, cardHeight, "RAIN CHANCE", "$precip%", theme)

        // HOURLY FORECAST SECTION HEADER (Y = 490)
        paint.color = theme.textColorPrimary
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("HOURLY FORECAST", 40f, 495f, paint)

        // 5 HOURLY FORECAST PILLS (Y = 515 to 680)
        val hourlyList = cityWeather.weatherDetails.hourlyForecast.take(5)
        val pillWidth = 120f
        val pillHeight = 165f
        val pillSpacing = 10f

        hourlyList.forEachIndexed { idx, hour ->
            val px = 40f + idx * (pillWidth + pillSpacing)
            drawHourlyPill(canvas, px, 515f, pillWidth, pillHeight, hour, isCelsius, theme)
        }

        return bitmap
    }

    // --- DRAWING UTILITY HELPERS ---

    private fun drawBackgroundCard(canvas: Canvas, width: Float, height: Float, theme: WeatherTheme) {
        val rect = RectF(0f, 0f, width, height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Gradient
        val gradient = LinearGradient(
            0f, 0f, 0f, height,
            theme.topColor, theme.bottomColor,
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRoundRect(rect, 40f, 40f, paint)

        // Subtle Glass Border
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.WHITE
        paint.alpha = 40
        canvas.drawRoundRect(rect, 40f, 40f, paint)
    }

    private fun drawMetricCard(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        label: String,
        value: String,
        theme: WeatherTheme
    ) {
        val rect = RectF(x, y, x + w, y + h)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Card Fill
        paint.style = Paint.Style.FILL
        paint.color = theme.cardBgColor
        paint.alpha = 180
        canvas.drawRoundRect(rect, 24f, 24f, paint)

        // Card Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.WHITE
        paint.alpha = 30
        canvas.drawRoundRect(rect, 24f, 24f, paint)

        // Label
        paint.style = Paint.Style.FILL
        paint.color = theme.textColorSecondary
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, x + w / 2f, y + 36f, paint)

        // Value
        paint.color = theme.textColorPrimary
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(value, x + w / 2f, y + 80f, paint)
    }

    private fun drawMiniPill(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        label: String,
        value: String,
        theme: WeatherTheme
    ) {
        val rect = RectF(x, y, x + w, y + h)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.style = Paint.Style.FILL
        paint.color = theme.cardBgColor
        paint.alpha = 180
        canvas.drawRoundRect(rect, 20f, 20f, paint)

        paint.color = theme.textColorSecondary
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, x + 16f, y + 26f, paint)

        paint.color = theme.textColorPrimary
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(value, x + 16f, y + 50f, paint)
    }

    private fun drawHourlyPill(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        hour: com.example.data.models.ForecastHour,
        isCelsius: Boolean,
        theme: WeatherTheme
    ) {
        val rect = RectF(x, y, x + w, y + h)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.style = Paint.Style.FILL
        paint.color = theme.cardBgColor
        paint.alpha = 180
        canvas.drawRoundRect(rect, 24f, 24f, paint)

        // Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.WHITE
        paint.alpha = 25
        canvas.drawRoundRect(rect, 24f, 24f, paint)

        // Time
        paint.style = Paint.Style.FILL
        paint.color = theme.textColorSecondary
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(hour.time, x + w / 2f, y + 36f, paint)

        // Weather Icon
        drawWeatherIcon(canvas, x + w / 2f, y + 82f, 24f, hour.condition, hour.isNight, theme)

        // Temp
        paint.color = theme.textColorPrimary
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(formatTemp(hour.temperature, isCelsius), x + w / 2f, y + 140f, paint)
    }

    // --- VECTOR WEATHER ICON DRAWING ENGINE ---
    private fun drawWeatherIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        condition: WeatherCondition,
        isNight: Boolean,
        theme: WeatherTheme
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (isNight && (condition == WeatherCondition.SUNNY || condition == WeatherCondition.PARTLY_CLOUDY)) {
            // Draw Moon
            paint.color = Color.parseColor("#F1F5F9")
            canvas.drawCircle(cx, cy, radius, paint)

            // Moon shadow cutout
            paint.color = theme.topColor
            canvas.drawCircle(cx + radius * 0.45f, cy - radius * 0.25f, radius * 0.85f, paint)
            return
        }

        when (condition) {
            WeatherCondition.SUNNY -> {
                // Sun Disk
                paint.color = Color.parseColor("#FDE047")
                canvas.drawCircle(cx, cy, radius * 0.7f, paint)

                // Sun Rays
                paint.color = Color.parseColor("#FACC15")
                paint.strokeWidth = radius * 0.18f
                paint.style = Paint.Style.STROKE
                for (i in 0 until 8) {
                    val angle = Math.toRadians((i * 45).toDouble())
                    val x1 = cx + (radius * 0.82f) * cos(angle).toFloat()
                    val y1 = cy + (radius * 0.82f) * sin(angle).toFloat()
                    val x2 = cx + (radius * 1.15f) * cos(angle).toFloat()
                    val y2 = cy + (radius * 1.15f) * sin(angle).toFloat()
                    canvas.drawLine(x1, y1, x2, y2, paint)
                }
            }
            WeatherCondition.PARTLY_CLOUDY -> {
                // Sun Behind Cloud
                paint.color = Color.parseColor("#FDE047")
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx - radius * 0.3f, cy - radius * 0.3f, radius * 0.6f, paint)

                // Cloud
                paint.color = Color.WHITE
                canvas.drawCircle(cx - radius * 0.2f, cy + radius * 0.2f, radius * 0.45f, paint)
                canvas.drawCircle(cx + radius * 0.2f, cy + radius * 0.1f, radius * 0.55f, paint)
                val rect = RectF(cx - radius * 0.5f, cy + radius * 0.2f, cx + radius * 0.6f, cy + radius * 0.65f)
                canvas.drawRoundRect(rect, 15f, 15f, paint)
            }
            WeatherCondition.CLOUDY -> {
                // Overcast Clouds
                paint.color = Color.parseColor("#E2E8F0")
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx - radius * 0.3f, cy, radius * 0.5f, paint)
                canvas.drawCircle(cx + radius * 0.2f, cy - radius * 0.1f, radius * 0.65f, paint)
                val rect = RectF(cx - radius * 0.6f, cy + radius * 0.1f, cx + radius * 0.7f, cy + radius * 0.6f)
                canvas.drawRoundRect(rect, 15f, 15f, paint)
            }
            WeatherCondition.RAINY -> {
                // Cloud
                paint.color = Color.parseColor("#CBD5E1")
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx - radius * 0.25f, cy - radius * 0.2f, radius * 0.5f, paint)
                canvas.drawCircle(cx + radius * 0.25f, cy - radius * 0.3f, radius * 0.6f, paint)
                val rect = RectF(cx - radius * 0.55f, cy - radius * 0.1f, cx + radius * 0.65f, cy + radius * 0.3f)
                canvas.drawRoundRect(rect, 15f, 15f, paint)

                // Rain Drops
                paint.color = Color.parseColor("#38BDF8")
                paint.strokeWidth = radius * 0.15f
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(cx - radius * 0.3f, cy + radius * 0.4f, cx - radius * 0.4f, cy + radius * 0.8f, paint)
                canvas.drawLine(cx, cy + radius * 0.4f, cx - radius * 0.1f, cy + radius * 0.8f, paint)
                canvas.drawLine(cx + radius * 0.3f, cy + radius * 0.4f, cx + radius * 0.2f, cy + radius * 0.8f, paint)
            }
            WeatherCondition.STORM -> {
                // Dark Cloud
                paint.color = Color.parseColor("#94A3B8")
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy - radius * 0.2f, radius * 0.65f, paint)

                // Lightning Bolt
                paint.color = Color.parseColor("#FACC15")
                val path = Path()
                path.moveTo(cx + radius * 0.1f, cy + radius * 0.1f)
                path.lineTo(cx - radius * 0.2f, cy + radius * 0.6f)
                path.lineTo(cx, cy + radius * 0.6f)
                path.lineTo(cx - radius * 0.15f, cy + radius * 1.05f)
                path.lineTo(cx + radius * 0.3f, cy + radius * 0.45f)
                path.lineTo(cx + radius * 0.1f, cy + radius * 0.45f)
                path.close()
                canvas.drawPath(path, paint)
            }
            WeatherCondition.SNOWY -> {
                // Cloud
                paint.color = Color.parseColor("#E2E8F0")
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy - radius * 0.2f, radius * 0.6f, paint)

                // Snowflakes
                paint.color = Color.WHITE
                canvas.drawCircle(cx - radius * 0.35f, cy + radius * 0.55f, radius * 0.15f, paint)
                canvas.drawCircle(cx, cy + radius * 0.6f, radius * 0.15f, paint)
                canvas.drawCircle(cx + radius * 0.35f, cy + radius * 0.55f, radius * 0.15f, paint)
            }
            WeatherCondition.FOGGY -> {
                // Fog Bands
                paint.color = Color.parseColor("#E2E8F0")
                paint.strokeWidth = radius * 0.2f
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(cx - radius * 0.7f, cy - radius * 0.3f, cx + radius * 0.7f, cy - radius * 0.3f, paint)
                canvas.drawLine(cx - radius * 0.85f, cy, cx + radius * 0.85f, cy, paint)
                canvas.drawLine(cx - radius * 0.6f, cy + radius * 0.3f, cx + radius * 0.6f, cy + radius * 0.3f, paint)
            }
        }
    }
}
