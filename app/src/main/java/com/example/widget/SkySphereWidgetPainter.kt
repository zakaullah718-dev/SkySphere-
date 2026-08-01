package com.example.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.data.models.CityWeather
import com.example.data.models.WeatherCondition
import com.example.utils.WeatherTimeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object SkySphereWidgetPainter {

    enum class ThemeType {
        SUNNY, PARTLY_CLOUDY, CLOUDY, RAIN, THUNDERSTORM, SNOW, FOG, SUNRISE, SUNSET, NIGHT
    }

    data class WeatherTheme(
        val topColor: Int,
        val bottomColor: Int,
        val accentColor: Int,
        val cardBgColor: Int,
        val textColorPrimary: Int,
        val textColorSecondary: Int,
        val borderAlpha: Int,
        val themeType: ThemeType,
        val appThemeId: String
    )

    fun getThemeForWeather(context: Context, cityWeather: CityWeather): WeatherTheme {
        val prefs = context.getSharedPreferences("skysphere_prefs", Context.MODE_PRIVATE)
        val appThemeId = prefs.getString("app_theme", "MIDNIGHT_BLUE") ?: "MIDNIGHT_BLUE"

        val condition = cityWeather.weatherDetails.condition
        val isNight = cityWeather.isNight

        val sunriseStr = cityWeather.weatherDetails.sunrise
        val sunsetStr = cityWeather.weatherDetails.sunset
        val sunriseMin = WeatherTimeUtils.parseTimeToMinutes(sunriseStr)
        val sunsetMin = WeatherTimeUtils.parseTimeToMinutes(sunsetStr)
        val currentMin = WeatherTimeUtils.getCurrentMinutesForLocation(
            cityWeather.localTime,
            cityWeather.timeZoneId ?: cityWeather.weatherDetails.timeZoneId,
            cityWeather.longitude
        )

        val themeType: ThemeType = when {
            isNight -> ThemeType.NIGHT
            abs(currentMin - sunriseMin) <= 40 -> ThemeType.SUNRISE
            abs(currentMin - sunsetMin) <= 40 -> ThemeType.SUNSET
            else -> when (condition) {
                WeatherCondition.SUNNY -> ThemeType.SUNNY
                WeatherCondition.PARTLY_CLOUDY -> ThemeType.PARTLY_CLOUDY
                WeatherCondition.CLOUDY -> ThemeType.CLOUDY
                WeatherCondition.RAINY -> ThemeType.RAIN
                WeatherCondition.STORM -> ThemeType.THUNDERSTORM
                WeatherCondition.SNOWY -> ThemeType.SNOW
                WeatherCondition.FOGGY -> ThemeType.FOG
            }
        }

        return when (appThemeId) {
            "OBSIDIAN_DARK" -> WeatherTheme(
                topColor = Color.parseColor("#0F172A"),
                bottomColor = Color.parseColor("#030712"),
                accentColor = Color.parseColor("#00E5FF"),
                cardBgColor = Color.parseColor("#121B2D"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#7DD3FC"),
                borderAlpha = 65,
                themeType = themeType,
                appThemeId = appThemeId
            )
            "SKY_LIGHT" -> WeatherTheme(
                topColor = Color.parseColor("#F8FAFC"),
                bottomColor = Color.parseColor("#E2E8F0"),
                accentColor = Color.parseColor("#0284C7"),
                cardBgColor = Color.parseColor("#FFFFFF"),
                textColorPrimary = Color.parseColor("#0F172A"),
                textColorSecondary = Color.parseColor("#334155"),
                borderAlpha = 110,
                themeType = themeType,
                appThemeId = appThemeId
            )
            "SUNSET_GOLD" -> WeatherTheme(
                topColor = Color.parseColor("#36170B"),
                bottomColor = Color.parseColor("#120703"),
                accentColor = Color.parseColor("#F59E0B"),
                cardBgColor = Color.parseColor("#421E0F"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#FDE68A"),
                borderAlpha = 70,
                themeType = themeType,
                appThemeId = appThemeId
            )
            "AURORA_TEAL" -> WeatherTheme(
                topColor = Color.parseColor("#07261F"),
                bottomColor = Color.parseColor("#020D0A"),
                accentColor = Color.parseColor("#10B981"),
                cardBgColor = Color.parseColor("#0D382E"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#A7F3D0"),
                borderAlpha = 65,
                themeType = themeType,
                appThemeId = appThemeId
            )
            "VIOLET_NIGHT" -> WeatherTheme(
                topColor = Color.parseColor("#220D3D"),
                bottomColor = Color.parseColor("#090314"),
                accentColor = Color.parseColor("#A855F7"),
                cardBgColor = Color.parseColor("#33155B"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#E9D5FF"),
                borderAlpha = 70,
                themeType = themeType,
                appThemeId = appThemeId
            )
            else -> { // MIDNIGHT_BLUE default
                WeatherTheme(
                    topColor = Color.parseColor("#0B1736"),
                    bottomColor = Color.parseColor("#030814"),
                    accentColor = Color.parseColor("#38BDF8"),
                    cardBgColor = Color.parseColor("#13234A"),
                    textColorPrimary = Color.WHITE,
                    textColorSecondary = Color.parseColor("#93C5FD"),
                    borderAlpha = 65,
                    themeType = themeType,
                    appThemeId = "MIDNIGHT_BLUE"
                )
            }
        }
    }

    private fun formatTemp(tempF: Int, isCelsius: Boolean): String {
        val t = if (isCelsius) (tempF - 32) * 5 / 9 else tempF
        return "$t°"
    }

    private fun formatSpeed(kmh: Double, isCelsius: Boolean): String {
        return if (isCelsius) {
            "${kmh.toInt()} km/h"
        } else {
            "${(kmh * 0.621371).toInt()} mph"
        }
    }

    private fun getCityFormattedDateTime(cityWeather: CityWeather): Pair<String, String> {
        val tz = WeatherTimeUtils.resolveTimeZone(
            cityWeather.timeZoneId ?: cityWeather.weatherDetails.timeZoneId,
            cityWeather.longitude
        )
        val now = Date()
        val timeFmt = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = tz }
        val dateFmt = SimpleDateFormat("EEE, d MMM yyyy", Locale.US).apply { timeZone = tz }
        return Pair(timeFmt.format(now), dateFmt.format(now))
    }

    // --- DRAW WIDGET 1x1 (COMPACT BADGE) ---
    fun drawWidget1x1(context: Context, cityWeather: CityWeather, isCelsius: Boolean): Bitmap {
        val width = 220
        val height = 220
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val theme = getThemeForWeather(context, cityWeather)
        drawBackgroundCard(canvas, width.toFloat(), height.toFloat(), theme)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val (timeStr, dateStr) = getCityFormattedDateTime(cityWeather)
        val clockTimeColor = if (cityWeather.isNight) Color.parseColor("#E0F2FE") else Color.WHITE
        val clockDateColor = if (cityWeather.isNight) Color.parseColor("#93C5FD") else Color.parseColor("#F1F5F9")

        // Upper-Left Digital Clock Time
        paint.color = clockTimeColor
        paint.textSize = 14.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        drawTextWithShadow(canvas, timeStr, 18f, 26f, paint, theme)

        // Directly below: Short Date
        val shortDateStr = dateStr.replace(" " + dateStr.takeLast(4), "").trim()
        paint.color = clockDateColor
        paint.textSize = 10.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        drawTextWithShadow(canvas, shortDateStr, 18f, 40f, paint, theme)

        // Upper-Right: City Name
        paint.color = theme.textColorSecondary
        paint.textSize = 12.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(cityWeather.cityName.uppercase(Locale.getDefault()), 202f, 26f, paint)

        // Center-Left: Weather Icon
        drawWeatherIcon(canvas, 58f, 100f, 32f, cityWeather.weatherDetails.condition, cityWeather.isNight, theme)

        // Center-Right: Temp
        paint.color = theme.textColorPrimary
        paint.textSize = 48f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        drawTextWithShadow(canvas, formatTemp(cityWeather.weatherDetails.currentTemp, isCelsius), 108f, 114f, paint, theme)

        // Condition Text (Center-Bottom)
        paint.color = theme.textColorPrimary
        paint.textSize = 15f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(cityWeather.weatherDetails.condition.displayName, 110f, 160f, paint)

        // Last Updated Time (Bottom)
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        paint.color = theme.textColorSecondary
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Updated ${timeFmt.format(Date())}", 110f, 192f, paint)

        return bitmap
    }

    // --- DRAW WIDGET 2x2 (SQUARE SUMMARY) ---
    fun drawWidget2x2(context: Context, cityWeather: CityWeather, isCelsius: Boolean): Bitmap {
        val targetWidth = 360
        val targetHeight = 360
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val theme = getThemeForWeather(context, cityWeather)
        drawBackgroundCard(canvas, targetWidth.toFloat(), targetHeight.toFloat(), theme)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val (timeStr, dateStr) = getCityFormattedDateTime(cityWeather)
        val clockTimeColor = if (cityWeather.isNight) Color.parseColor("#E0F2FE") else Color.WHITE
        val clockDateColor = if (cityWeather.isNight) Color.parseColor("#93C5FD") else Color.parseColor("#F1F5F9")

        // Upper-Left Digital Clock Time
        paint.color = clockTimeColor
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        drawTextWithShadow(canvas, timeStr, 30f, 44f, paint, theme)

        // Directly below: Day of week, Date, Month, Year
        paint.color = clockDateColor
        paint.textSize = 15.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        drawTextWithShadow(canvas, dateStr, 30f, 66f, paint, theme)

        // Directly below Date: City Location
        paint.color = theme.textColorSecondary
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val locationText = cityWeather.cityName.uppercase(Locale.getDefault())
        canvas.drawText(locationText, 30f, 94f, paint)

        // Upper-Right: Premium Animated Weather Icon
        drawWeatherIcon(canvas, 275f, 92f, 48f, cityWeather.weatherDetails.condition, cityWeather.isNight, theme)

        // Main Weather Temperature
        paint.color = theme.textColorPrimary
        paint.textSize = 80f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val tempStr = formatTemp(cityWeather.weatherDetails.currentTemp, isCelsius)
        drawTextWithShadow(canvas, tempStr, 30f, 172f, paint, theme)

        // Condition Name
        paint.textSize = 21f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(cityWeather.weatherDetails.condition.displayName, 30f, 208f, paint)

        // High / Low
        val highLowText = "H: ${formatTemp(cityWeather.weatherDetails.highTemp, isCelsius)}   L: ${formatTemp(cityWeather.weatherDetails.lowTemp, isCelsius)}"
        paint.color = theme.textColorSecondary
        paint.textSize = 16.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(highLowText, 30f, 240f, paint)

        // Feels Like & Humidity
        val feelsText = "Feels like ${formatTemp(cityWeather.weatherDetails.feelsLike, isCelsius)} • Hum ${cityWeather.weatherDetails.humidity}%"
        paint.textSize = 14.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(feelsText, 30f, 272f, paint)

        // Wind Speed
        val windText = "Wind: ${formatSpeed(cityWeather.weatherDetails.windSpeed, isCelsius)}"
        canvas.drawText(windText, 30f, 298f, paint)

        // Last Updated Time
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val updatedText = "Updated ${timeFmt.format(Date())}"
        paint.textSize = 12.5f
        paint.color = theme.textColorSecondary
        canvas.drawText(updatedText, 30f, 330f, paint)

        return bitmap
    }

    // --- DRAW WIDGET 4x2 (WIDE DASHBOARD) ---
    fun drawWidget4x2(context: Context, cityWeather: CityWeather, isCelsius: Boolean): Bitmap {
        val targetWidth = 480
        val targetHeight = 240
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(480f / 720f, 240f / 360f)

        val width = 720f
        val height = 360f

        val theme = getThemeForWeather(context, cityWeather)
        drawBackgroundCard(canvas, width, height, theme)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val (timeStr, dateStr) = getCityFormattedDateTime(cityWeather)
        val clockTimeColor = if (cityWeather.isNight) Color.parseColor("#E0F2FE") else Color.WHITE
        val clockDateColor = if (cityWeather.isNight) Color.parseColor("#93C5FD") else Color.parseColor("#F1F5F9")

        // Upper-Left Digital Clock Time
        paint.color = clockTimeColor
        paint.textSize = 34f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        drawTextWithShadow(canvas, timeStr, 40f, 50f, paint, theme)

        // Directly below: Day of week, Date, Month, Year
        paint.color = clockDateColor
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        drawTextWithShadow(canvas, dateStr, 40f, 78f, paint, theme)

        // Directly below Date: Location
        paint.color = theme.textColorPrimary
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val region1 = cityWeather.region
        val country1 = cityWeather.country
        val displayLoc = when {
            !region1.isNullOrBlank() -> "${cityWeather.cityName.uppercase()}, ${region1.uppercase()}"
            !country1.isNullOrBlank() -> "${cityWeather.cityName.uppercase()}, ${country1.uppercase()}"
            else -> cityWeather.cityName.uppercase()
        }
        canvas.drawText(displayLoc, 40f, 116f, paint)

        // Condition text
        paint.color = theme.textColorSecondary
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(cityWeather.weatherDetails.condition.displayName, 40f, 148f, paint)

        // Updated time
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val updatedText = "Updated ${timeFmt.format(Date())}"
        paint.textSize = 15f
        canvas.drawText(updatedText, 40f, 174f, paint)

        // Bottom Pills (High/Low, Wind, Humidity, Rain)
        drawMiniPill(canvas, 40f, 212f, 155f, 65f, "HIGH / LOW", "H:${formatTemp(cityWeather.weatherDetails.highTemp, isCelsius)} L:${formatTemp(cityWeather.weatherDetails.lowTemp, isCelsius)}", theme)
        drawMiniPill(canvas, 205f, 212f, 155f, 65f, "WIND", formatSpeed(cityWeather.weatherDetails.windSpeed, isCelsius), theme)
        drawMiniPill(canvas, 370f, 212f, 155f, 65f, "HUMIDITY", "${cityWeather.weatherDetails.humidity}%", theme)

        val rainChance = cityWeather.weatherDetails.hourlyForecast.firstOrNull()?.precipitationChance ?: 0
        drawMiniPill(canvas, 535f, 212f, 145f, 65f, "RAIN CHANCE", "$rainChance%", theme)

        // Right Hero: Large Weather Icon
        drawWeatherIcon(canvas, 490f, 112f, 68f, cityWeather.weatherDetails.condition, cityWeather.isNight, theme)

        // Right Temperature (aligned to right margin 680f)
        paint.color = theme.textColorPrimary
        paint.textSize = 104f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        drawTextWithShadow(canvas, formatTemp(cityWeather.weatherDetails.currentTemp, isCelsius), 680f, 130f, paint, theme)

        // Feels like
        paint.color = theme.textColorSecondary
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Feels like ${formatTemp(cityWeather.weatherDetails.feelsLike, isCelsius)}", 680f, 172f, paint)

        return bitmap
    }

    // --- DRAWING UTILITY HELPERS ---

    private fun drawTextWithShadow(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint, theme: WeatherTheme) {
        val origColor = paint.color
        paint.color = Color.BLACK
        paint.alpha = 80
        canvas.drawText(text, x + 3f, y + 4f, paint)
        paint.color = origColor
        canvas.drawText(text, x, y, paint)
    }

    private fun drawBackgroundCard(canvas: Canvas, width: Float, height: Float, theme: WeatherTheme) {
        val rect = RectF(0f, 0f, width, height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Ambient Drop Shadow (Outer shadow cast behind main card)
        val shadowRect = RectF(4f, 8f, width - 4f, height + 8f)
        paint.color = Color.BLACK
        paint.alpha = 65
        canvas.drawRoundRect(shadowRect, 44f, 44f, paint)

        // 2. Soft Night Outer Perimeter Edge Glow
        if (theme.themeType == ThemeType.NIGHT) {
            val glowRect = RectF(-6f, -6f, width + 6f, height + 6f)
            val nightGlow = RadialGradient(
                width * 0.5f, height * 0.5f, width * 0.75f,
                Color.argb(85, 56, 189, 248),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            paint.shader = nightGlow
            canvas.drawRoundRect(glowRect, 50f, 50f, paint)
            paint.shader = null
        }

        // 3. Glass Transparency Base Fill (Allows wallpaper visibility while maintaining high contrast)
        val topGlassColor = Color.argb(145, Color.red(theme.topColor), Color.green(theme.topColor), Color.blue(theme.topColor))
        val bottomGlassColor = Color.argb(185, Color.red(theme.bottomColor), Color.green(theme.bottomColor), Color.blue(theme.bottomColor))

        val gradient = LinearGradient(
            0f, 0f, 0f, height,
            topGlassColor, bottomGlassColor,
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRoundRect(rect, 44f, 44f, paint)
        paint.shader = null

        // 4. Render Dynamic Atmosphere Background Effects
        drawBackgroundEffects(canvas, width, height, theme)

        // 5. Specular Frosted Glass Highlight Diagonal Reflection
        val glossPath = Path()
        glossPath.moveTo(0f, 0f)
        glossPath.lineTo(width * 0.65f, 0f)
        glossPath.lineTo(0f, height * 0.7f)
        glossPath.close()

        val glossGradient = LinearGradient(
            0f, 0f, width * 0.35f, height * 0.35f,
            Color.argb(50, 255, 255, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        paint.shader = glossGradient
        canvas.drawPath(glossPath, paint)
        paint.shader = null

        // 6. Top Edge Reflection Highlight Bar
        val topBarRect = RectF(16f, 0f, width - 16f, 12f)
        val topBarGradient = LinearGradient(
            0f, 0f, 0f, 12f,
            Color.argb(75, 255, 255, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        paint.shader = topBarGradient
        canvas.drawRoundRect(topBarRect, 6f, 6f, paint)
        paint.shader = null

        // 7. Glass Bevel Perimeter Stroke
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.WHITE
        paint.alpha = theme.borderAlpha + 10
        canvas.drawRoundRect(rect, 44f, 44f, paint)
    }

    private fun drawBackgroundEffects(canvas: Canvas, width: Float, height: Float, theme: WeatherTheme) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val timeMs = System.currentTimeMillis()

        when (theme.themeType) {
            ThemeType.SUNNY -> {
                val radialGradient = RadialGradient(
                    width * 0.85f, height * 0.15f, width * 0.5f,
                    Color.argb(140, 255, 224, 130),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                paint.shader = radialGradient
                canvas.drawCircle(width * 0.85f, height * 0.15f, width * 0.5f, paint)
                paint.shader = null
            }
            ThemeType.SUNRISE -> {
                val radialGradient = RadialGradient(
                    width * 0.5f, height * 0.95f, width * 0.65f,
                    Color.argb(135, 253, 224, 71),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                paint.shader = radialGradient
                canvas.drawCircle(width * 0.5f, height * 0.95f, width * 0.65f, paint)
                paint.shader = null
            }
            ThemeType.SUNSET -> {
                val radialGradient = RadialGradient(
                    width * 0.5f, height * 0.9f, width * 0.7f,
                    Color.argb(145, 251, 146, 60),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                paint.shader = radialGradient
                canvas.drawCircle(width * 0.5f, height * 0.9f, width * 0.7f, paint)
                paint.shader = null
            }
            ThemeType.NIGHT -> {
                paint.color = Color.WHITE
                val starCoords = arrayOf(
                    floatArrayOf(0.1f, 0.15f, 2.5f, 180f),
                    floatArrayOf(0.25f, 0.08f, 2.0f, 140f),
                    floatArrayOf(0.45f, 0.2f, 3.0f, 220f),
                    floatArrayOf(0.6f, 0.12f, 2.0f, 160f),
                    floatArrayOf(0.75f, 0.25f, 2.5f, 200f),
                    floatArrayOf(0.88f, 0.09f, 3.2f, 240f),
                    floatArrayOf(0.15f, 0.45f, 1.8f, 120f),
                    floatArrayOf(0.35f, 0.38f, 2.2f, 150f),
                    floatArrayOf(0.55f, 0.48f, 2.0f, 130f),
                    floatArrayOf(0.82f, 0.42f, 2.6f, 190f),
                    floatArrayOf(0.08f, 0.7f, 2.0f, 160f),
                    floatArrayOf(0.92f, 0.75f, 2.2f, 170f)
                )
                starCoords.forEachIndexed { i, star ->
                    val twinkleAlpha = (130 + sin(timeMs / 250.0 + i) * 115).toInt().coerceIn(30, 255)
                    paint.alpha = twinkleAlpha
                    canvas.drawCircle(width * star[0], height * star[1], star[2], paint)
                }
                val moonAura = RadialGradient(
                    width * 0.8f, height * 0.2f, width * 0.35f,
                    Color.argb(80, 56, 189, 248),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                paint.shader = moonAura
                canvas.drawCircle(width * 0.8f, height * 0.2f, width * 0.35f, paint)
                paint.shader = null
            }
            ThemeType.RAIN -> {
                paint.color = Color.parseColor("#38BDF8")
                paint.strokeWidth = 2.5f
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                val rainCoords = arrayOf(
                    floatArrayOf(0.12f, 0.1f), floatArrayOf(0.28f, 0.15f), floatArrayOf(0.48f, 0.08f),
                    floatArrayOf(0.68f, 0.2f), floatArrayOf(0.84f, 0.12f), floatArrayOf(0.22f, 0.45f),
                    floatArrayOf(0.42f, 0.5f), floatArrayOf(0.62f, 0.4f), floatArrayOf(0.78f, 0.55f),
                    floatArrayOf(0.15f, 0.75f), floatArrayOf(0.52f, 0.78f), floatArrayOf(0.88f, 0.72f)
                )
                rainCoords.forEachIndexed { idx, drop ->
                    val dy = ((timeMs / 12L + idx * 25) % height.toInt()).toFloat()
                    val sx = width * drop[0]
                    val sy = (height * drop[1] + dy) % height
                    paint.alpha = 85
                    canvas.drawLine(sx, sy, sx - 15f, sy + 30f, paint)
                }
                paint.style = Paint.Style.FILL
            }
            ThemeType.SNOW -> {
                paint.color = Color.WHITE
                val snowCoords = arrayOf(
                    floatArrayOf(0.1f, 0.15f, 4f), floatArrayOf(0.28f, 0.1f, 5f),
                    floatArrayOf(0.48f, 0.22f, 3.5f), floatArrayOf(0.68f, 0.08f, 4.5f),
                    floatArrayOf(0.85f, 0.18f, 5.5f), floatArrayOf(0.18f, 0.45f, 4f),
                    floatArrayOf(0.38f, 0.52f, 3.5f), floatArrayOf(0.58f, 0.42f, 4.5f),
                    floatArrayOf(0.78f, 0.58f, 5f), floatArrayOf(0.12f, 0.78f, 4f),
                    floatArrayOf(0.52f, 0.75f, 4.5f), floatArrayOf(0.86f, 0.72f, 3.5f)
                )
                snowCoords.forEachIndexed { i, s ->
                    val xWobble = sin(timeMs / 550.0 + i).toFloat() * 6f
                    val yPos = ((height * s[1] + (timeMs / 16L + i * 20) % height.toInt())).toFloat() % height
                    paint.alpha = (160 + sin(timeMs / 400.0 + i) * 60).toInt().coerceIn(60, 240)
                    canvas.drawCircle(width * s[0] + xWobble, yPos, s[2], paint)
                }
            }
            ThemeType.THUNDERSTORM -> {
                val stormFlashAlpha = (90 + sin(timeMs / 140.0) * 70).toInt().coerceIn(20, 160)
                val stormGlow = RadialGradient(
                    width * 0.75f, height * 0.25f, width * 0.45f,
                    Color.argb(stormFlashAlpha, 253, 224, 71),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                paint.shader = stormGlow
                canvas.drawCircle(width * 0.75f, height * 0.25f, width * 0.45f, paint)
                paint.shader = null
            }
            ThemeType.PARTLY_CLOUDY, ThemeType.CLOUDY, ThemeType.FOG -> {
                val cloudDrift = sin(timeMs / 900.0).toFloat() * 8f
                paint.color = Color.WHITE
                paint.alpha = 26
                canvas.drawCircle(width * 0.85f + cloudDrift, height * 0.2f, width * 0.25f, paint)
                canvas.drawCircle(width * 0.7f + cloudDrift, height * 0.15f, width * 0.2f, paint)
            }
        }
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

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.color = Color.WHITE
        paint.alpha = 35
        canvas.drawRoundRect(rect, 20f, 20f, paint)

        paint.style = Paint.Style.FILL
        paint.color = theme.textColorSecondary
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, x + 14f, y + 26f, paint)

        paint.color = theme.textColorPrimary
        paint.textSize = 19f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(value, x + 14f, y + 52f, paint)
    }

    // --- FLAGSHIP CUSTOM VECTOR WEATHER ICON DRAWING ENGINE ---
    private fun drawWeatherIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        condition: WeatherCondition,
        isNight: Boolean,
        theme: WeatherTheme
    ) {
        val timeMs = System.currentTimeMillis()

        if (isNight && (condition == WeatherCondition.SUNNY || condition == WeatherCondition.PARTLY_CLOUDY)) {
            drawMoonIcon(canvas, cx, cy, radius, theme, timeMs)
            if (condition == WeatherCondition.PARTLY_CLOUDY) {
                drawCloudOverlay(canvas, cx + radius * 0.15f, cy + radius * 0.2f, radius * 0.9f, timeMs, darkTheme = true)
            }
            return
        }

        when (condition) {
            WeatherCondition.SUNNY -> drawSunIcon(canvas, cx, cy, radius, theme, timeMs)
            WeatherCondition.PARTLY_CLOUDY -> {
                drawSunIcon(canvas, cx - radius * 0.3f, cy - radius * 0.3f, radius * 0.7f, theme, timeMs)
                drawCloudOverlay(canvas, cx + radius * 0.15f, cy + radius * 0.2f, radius * 0.85f, timeMs, darkTheme = false)
            }
            WeatherCondition.CLOUDY -> drawCloudIcon(canvas, cx, cy, radius, theme, timeMs)
            WeatherCondition.RAINY -> drawRainIcon(canvas, cx, cy, radius, theme, timeMs)
            WeatherCondition.STORM -> drawThunderIcon(canvas, cx, cy, radius, theme, timeMs)
            WeatherCondition.SNOWY -> drawSnowIcon(canvas, cx, cy, radius, theme, timeMs)
            WeatherCondition.FOGGY -> drawFogIcon(canvas, cx, cy, radius, theme, timeMs)
        }
    }

    // 1. SUN ICON: Radiant gradient disk, glowing corona, rotating rays, breathing pulse
    private fun drawSunIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float, theme: WeatherTheme, timeMs: Long) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val pulseScale = 1.0f + (sin(timeMs / 500.0) * 0.06f).toFloat()
        val coronaRadius = radius * 1.35f * pulseScale

        // Outer Soft Radiant Glow Corona
        val coronaShader = RadialGradient(
            cx, cy, coronaRadius,
            intArrayOf(Color.argb(170, 255, 179, 0), Color.argb(80, 255, 224, 130), Color.TRANSPARENT),
            floatArrayOf(0.0f, 0.6f, 1.0f),
            Shader.TileMode.CLAMP
        )
        paint.shader = coronaShader
        canvas.drawCircle(cx, cy, coronaRadius, paint)
        paint.shader = null

        // Continuous Slow Rotating Modern Rays
        val angleOffset = (timeMs / 38.0) % 360
        paint.color = Color.parseColor("#FFA000")
        paint.strokeWidth = radius * 0.16f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        val rayInnerR = radius * 0.85f * pulseScale
        val rayOuterR = radius * 1.22f * pulseScale

        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45 + angleOffset).toDouble())
            val x1 = cx + rayInnerR * cos(angle).toFloat()
            val y1 = cy + rayInnerR * sin(angle).toFloat()
            val x2 = cx + rayOuterR * cos(angle).toFloat()
            val y2 = cy + rayOuterR * sin(angle).toFloat()
            canvas.drawLine(x1, y1, x2, y2, paint)
        }

        // Inner Core Sun Disk with High-Grade Gradient
        paint.style = Paint.Style.FILL
        val sunShader = RadialGradient(
            cx - radius * 0.2f, cy - radius * 0.2f, radius * 0.8f,
            intArrayOf(Color.parseColor("#FFF59D"), Color.parseColor("#FFB300"), Color.parseColor("#FF8F00")),
            floatArrayOf(0.0f, 0.65f, 1.0f),
            Shader.TileMode.CLAMP
        )
        paint.shader = sunShader
        canvas.drawCircle(cx, cy, radius * 0.72f * pulseScale, paint)
        paint.shader = null

        // Specular Top-Left Highlight
        paint.color = Color.WHITE
        paint.alpha = 180
        canvas.drawCircle(cx - radius * 0.28f, cy - radius * 0.28f, radius * 0.18f, paint)
    }

    // 2. MOON ICON: Radiant silver-cyan moon, crater depth, cyan aura, twinkling stars
    private fun drawMoonIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float, theme: WeatherTheme, timeMs: Long) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Soft Cyan Night Backlight
        val moonAura = RadialGradient(
            cx, cy, radius * 1.4f,
            intArrayOf(Color.argb(120, 56, 189, 248), Color.argb(40, 2, 132, 199), Color.TRANSPARENT),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = moonAura
        canvas.drawCircle(cx, cy, radius * 1.4f, paint)
        paint.shader = null

        // Crescent Moon Base Circle
        val moonShader = LinearGradient(
            cx - radius, cy - radius, cx + radius, cy + radius,
            Color.parseColor("#F0F9FF"), Color.parseColor("#38BDF8"),
            Shader.TileMode.CLAMP
        )
        paint.shader = moonShader
        canvas.drawCircle(cx, cy, radius * 0.95f, paint)
        paint.shader = null

        // Crescent Shadow Cutout (Matching Background)
        paint.color = theme.topColor
        canvas.drawCircle(cx + radius * 0.42f, cy - radius * 0.22f, radius * 0.82f, paint)

        // Soft Crater Textures on Moon
        paint.color = Color.parseColor("#0284C7")
        paint.alpha = 45
        canvas.drawCircle(cx - radius * 0.35f, cy + radius * 0.15f, radius * 0.18f, paint)
        canvas.drawCircle(cx - radius * 0.1f, cy + radius * 0.45f, radius * 0.12f, paint)

        // Twinkling 4-Point Sparkle Stars
        val starPos = arrayOf(
            floatArrayOf(cx + radius * 0.8f, cy - radius * 0.7f, 10f, 0f),
            floatArrayOf(cx + radius * 1.0f, cy + radius * 0.5f, 8f, 1.5f),
            floatArrayOf(cx - radius * 0.9f, cy - radius * 0.5f, 7f, 2.8f)
        )

        starPos.forEach { star ->
            val twAlpha = (140 + sin(timeMs / 300.0 + star[3]) * 115).toInt().coerceIn(30, 255)
            drawSparkleStar(canvas, star[0], star[1], star[2], twAlpha)
        }
    }

    private fun drawSparkleStar(canvas: Canvas, x: Float, y: Float, size: Float, alpha: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.alpha = alpha

        val path = Path().apply {
            moveTo(x, y - size)
            quadTo(x, y, x + size, y)
            quadTo(x, y, x, y + size)
            quadTo(x, y, x - size, y)
            quadTo(x, y, x, y - size)
            close()
        }
        canvas.drawPath(path, paint)
    }

    // 3. CLOUD ICON: Layered volumetric clouds, depth shadows, gentle drift animation
    private fun drawCloudIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float, theme: WeatherTheme, timeMs: Long) {
        val driftX = sin(timeMs / 900.0).toFloat() * 4f
        val driftY = cos(timeMs / 700.0).toFloat() * 2.5f

        // Back Shadow Cloud Layer (Offset Depth)
        drawSingleCloud(
            canvas = canvas,
            cx = cx + radius * 0.15f + driftX * 0.5f,
            cy = cy - radius * 0.1f + driftY * 0.5f,
            radius = radius * 0.85f,
            topColor = Color.parseColor("#64748B"),
            bottomColor = Color.parseColor("#334155"),
            alpha = 180
        )

        // Front Volumetric Cloud Layer
        drawSingleCloud(
            canvas = canvas,
            cx = cx + driftX,
            cy = cy + driftY,
            radius = radius,
            topColor = Color.WHITE,
            bottomColor = Color.parseColor("#CBD5E1"),
            alpha = 255
        )
    }

    private fun drawCloudOverlay(canvas: Canvas, cx: Float, cy: Float, radius: Float, timeMs: Long, darkTheme: Boolean) {
        val driftX = sin(timeMs / 850.0).toFloat() * 3f

        if (darkTheme) {
            drawSingleCloud(
                canvas = canvas,
                cx = cx + driftX,
                cy = cy,
                radius = radius,
                topColor = Color.parseColor("#94A3B8"),
                bottomColor = Color.parseColor("#475569"),
                alpha = 230
            )
        } else {
            drawSingleCloud(
                canvas = canvas,
                cx = cx + driftX,
                cy = cy,
                radius = radius,
                topColor = Color.WHITE,
                bottomColor = Color.parseColor("#E2E8F0"),
                alpha = 250
            )
        }
    }

    private fun drawSingleCloud(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        topColor: Int,
        bottomColor: Int,
        alpha: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Drop shadow under cloud base
        paint.color = Color.BLACK
        paint.alpha = (alpha * 0.22f).toInt()
        val shadowRect = RectF(cx - radius * 0.65f, cy + radius * 0.18f, cx + radius * 0.75f, cy + radius * 0.55f)
        canvas.drawRoundRect(shadowRect, 18f, 18f, paint)

        // Cloud Gradient Shader
        val shader = LinearGradient(
            cx, cy - radius * 0.5f, cx, cy + radius * 0.5f,
            topColor, bottomColor,
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
        paint.alpha = alpha

        // Volumetric Cloud Lobes
        canvas.drawCircle(cx - radius * 0.35f, cy + radius * 0.1f, radius * 0.42f, paint)
        canvas.drawCircle(cx + radius * 0.28f, cy, radius * 0.52f, paint)
        canvas.drawCircle(cx - radius * 0.05f, cy - radius * 0.2f, radius * 0.48f, paint)

        val baseRect = RectF(cx - radius * 0.65f, cy + radius * 0.1f, cx + radius * 0.75f, cy + radius * 0.5f)
        canvas.drawRoundRect(baseRect, 18f, 18f, paint)
        paint.shader = null

        // Specular Top Highlight Rim
        paint.color = Color.WHITE
        paint.alpha = (alpha * 0.45f).toInt()
        canvas.drawCircle(cx - radius * 0.05f, cy - radius * 0.22f, radius * 0.42f, paint)
    }

    // 4. RAIN ICON: Layered storm cloud, realistic falling raindrops with motion, splash ripples
    private fun drawRainIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float, theme: WeatherTheme, timeMs: Long) {
        // Dark Storm Cloud
        drawSingleCloud(
            canvas = canvas,
            cx = cx,
            cy = cy - radius * 0.2f,
            radius = radius * 0.95f,
            topColor = Color.parseColor("#94A3B8"),
            bottomColor = Color.parseColor("#475569"),
            alpha = 255
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.strokeWidth = radius * 0.1f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        // 4 Animated Falling Drops with Angled Wind Motion & Splash Ripples
        val dropOffsets = floatArrayOf(-0.45f, -0.15f, 0.15f, 0.45f)

        dropOffsets.forEachIndexed { i, xFactor ->
            val dropX = cx + radius * xFactor
            val cycleTime = 420L
            val progress = ((timeMs + i * 110L) % cycleTime) / cycleTime.toFloat()

            val startY = cy + radius * 0.25f
            val maxTravel = radius * 0.65f

            val currentDropY = startY + progress * maxTravel
            val dropLength = radius * 0.22f

            // Raindrop Shader Line
            val dropShader = LinearGradient(
                dropX - 6f, currentDropY, dropX - 12f, currentDropY + dropLength,
                Color.parseColor("#38BDF8"), Color.parseColor("#0284C7"),
                Shader.TileMode.CLAMP
            )
            paint.shader = dropShader
            paint.alpha = ((1f - progress * 0.3f) * 255).toInt().coerceIn(40, 255)

            canvas.drawLine(dropX, currentDropY, dropX - 8f, currentDropY + dropLength, paint)
            paint.shader = null

            // Splash Effect at bottom of raindrop path
            if (progress > 0.78f) {
                val splashProgress = (progress - 0.78f) / 0.22f
                val splashY = startY + maxTravel + dropLength * 0.5f
                val splashRadius = radius * 0.18f * splashProgress

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                paint.color = Color.parseColor("#38BDF8")
                paint.alpha = ((1f - splashProgress) * 200).toInt().coerceIn(0, 250)

                val splashBounds = RectF(
                    dropX - splashRadius - 4f, splashY - splashRadius * 0.4f,
                    dropX + splashRadius - 4f, splashY + splashRadius * 0.4f
                )
                canvas.drawOval(splashBounds, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = radius * 0.1f
            }
        }
    }

    // 5. THUNDER ICON: Ominous cloud, electric lightning bolt with pulse glow & smooth flash
    private fun drawThunderIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float, theme: WeatherTheme, timeMs: Long) {
        // Dark Ominous Cloud
        drawSingleCloud(
            canvas = canvas,
            cx = cx,
            cy = cy - radius * 0.22f,
            radius = radius * 0.95f,
            topColor = Color.parseColor("#475569"),
            bottomColor = Color.parseColor("#1E293B"),
            alpha = 255
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Smooth Pulsing Flash Glow
        val flashAlpha = (140 + sin(timeMs / 120.0) * 115).toInt().coerceIn(40, 255)
        val flashGlow = RadialGradient(
            cx, cy + radius * 0.4f, radius * 0.8f,
            intArrayOf(Color.argb(flashAlpha, 253, 224, 71), Color.argb((flashAlpha * 0.4f).toInt(), 56, 189, 248), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = flashGlow
        canvas.drawCircle(cx, cy + radius * 0.4f, radius * 0.8f, paint)
        paint.shader = null

        // Electric Lightning Bolt Path
        val boltPath = Path().apply {
            moveTo(cx + radius * 0.12f, cy + radius * 0.05f)
            lineTo(cx - radius * 0.22f, cy + radius * 0.52f)
            lineTo(cx + radius * 0.02f, cy + radius * 0.52f)
            lineTo(cx - radius * 0.18f, cy + radius * 1.02f)
            lineTo(cx + radius * 0.35f, cy + radius * 0.4f)
            lineTo(cx + radius * 0.08f, cy + radius * 0.4f)
            close()
        }

        // Outer Glowing Bolt Stroke
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.parseColor("#FDE047")
        paint.alpha = flashAlpha
        canvas.drawPath(boltPath, paint)

        // Inner Core Bolt Fill
        paint.style = Paint.Style.FILL
        val boltShader = LinearGradient(
            cx, cy, cx, cy + radius,
            Color.WHITE, Color.parseColor("#FACC15"),
            Shader.TileMode.CLAMP
        )
        paint.shader = boltShader
        paint.alpha = 255
        canvas.drawPath(boltPath, paint)
        paint.shader = null
    }

    // 6. SNOW ICON: Winter blue cloud, 6-arm delicate crystal snowflakes, floating sway
    private fun drawSnowIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float, theme: WeatherTheme, timeMs: Long) {
        // Winter Blue Cloud
        drawSingleCloud(
            canvas = canvas,
            cx = cx,
            cy = cy - radius * 0.2f,
            radius = radius * 0.95f,
            topColor = Color.parseColor("#F1F5F9"),
            bottomColor = Color.parseColor("#CBD5E1"),
            alpha = 255
        )

        // 3 Intricate Floating Snowflakes
        val snowData = arrayOf(
            floatArrayOf(-0.35f, 0.45f, 0.22f, 0f),
            floatArrayOf(0.0f, 0.58f, 0.26f, 1.8f),
            floatArrayOf(0.35f, 0.45f, 0.22f, 3.2f)
        )

        snowData.forEach { s ->
            val swayX = cx + radius * s[0] + sin(timeMs / 450.0 + s[3]).toFloat() * 4f
            val fallY = cy + radius * s[1] + ((timeMs / 18L + (s[3] * 20).toLong()) % (radius * 0.3f).toInt())
            val flakeR = radius * s[2]
            val rotAngle = (timeMs / 25.0 + s[3] * 50) % 360

            drawSnowflakeCrystal(canvas, swayX, fallY, flakeR, rotAngle.toFloat())
        }
    }

    private fun drawSnowflakeCrystal(canvas: Canvas, x: Float, y: Float, radius: Float, rotationDegrees: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.strokeWidth = 2.2f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        // Central Core Dot
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, radius * 0.25f, paint)
        paint.style = Paint.Style.STROKE

        // 6 Radial Arms with V-Barbs
        for (i in 0 until 6) {
            val angleRad = Math.toRadians((i * 60 + rotationDegrees).toDouble())
            val armEndX = x + radius * cos(angleRad).toFloat()
            val armEndY = y + radius * sin(angleRad).toFloat()

            canvas.drawLine(x, y, armEndX, armEndY, paint)

            // V-Barb Branch on each arm
            val barbMidX = x + (radius * 0.6f) * cos(angleRad).toFloat()
            val barbMidY = y + (radius * 0.6f) * sin(angleRad).toFloat()

            val barbLeftAngle = Math.toRadians((i * 60 + rotationDegrees + 40).toDouble())
            val barbRightAngle = Math.toRadians((i * 60 + rotationDegrees - 40).toDouble())

            val b1X = barbMidX + (radius * 0.35f) * cos(barbLeftAngle).toFloat()
            val b1Y = barbMidY + (radius * 0.35f) * sin(barbLeftAngle).toFloat()
            val b2X = barbMidX + (radius * 0.35f) * cos(barbRightAngle).toFloat()
            val b2Y = barbMidY + (radius * 0.35f) * sin(barbRightAngle).toFloat()

            canvas.drawLine(barbMidX, barbMidY, b1X, b1Y, paint)
            canvas.drawLine(barbMidX, barbMidY, b2X, b2Y, paint)
        }
    }

    // 7. FOG ICON: Semi-transparent drifting fog layers with soft rounded end caps
    private fun drawFogIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float, theme: WeatherTheme, timeMs: Long) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        val fogBands = arrayOf(
            floatArrayOf(-0.2f, 1.2f, 0.28f, 0f, 0.85f),
            floatArrayOf(0.1f, 0f, 0.3f, 1.5f, 1.0f),
            floatArrayOf(-0.15f, -0.2f, 0.28f, 3.0f, 0.75f)
        )

        fogBands.forEach { band ->
            val driftX = sin(timeMs / 800.0 + band[3]).toFloat() * 6f
            val bandY = cy + radius * band[1]
            val startX = cx - radius * band[4] + driftX
            val endX = cx + radius * band[4] + driftX

            val fogShader = LinearGradient(
                startX, bandY, endX, bandY,
                intArrayOf(Color.TRANSPARENT, Color.argb(220, 241, 245, 249), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            paint.shader = fogShader
            paint.strokeWidth = radius * band[2]

            canvas.drawLine(startX, bandY, endX, bandY, paint)
            paint.shader = null
        }
    }
}
