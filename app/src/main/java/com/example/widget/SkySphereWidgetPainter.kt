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

        // Synchronize App Theme colors with Weather Condition accents
        return when (appThemeId) {
            "OBSIDIAN_DARK" -> WeatherTheme(
                topColor = Color.parseColor("#0F172A"),
                bottomColor = Color.parseColor("#030712"),
                accentColor = Color.parseColor("#00E5FF"),
                cardBgColor = Color.parseColor("#121B2D"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#7DD3FC"),
                borderAlpha = 50,
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
                borderAlpha = 90,
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
                borderAlpha = 60,
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
                borderAlpha = 55,
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
                borderAlpha = 60,
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
                    borderAlpha = 55,
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

    // --- DRAW WIDGET 1x1 (COMPACT BADGE) ---
    fun drawWidget1x1(context: Context, cityWeather: CityWeather, isCelsius: Boolean): Bitmap {
        val width = 220
        val height = 220
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val theme = getThemeForWeather(context, cityWeather)
        drawBackgroundCard(canvas, width.toFloat(), height.toFloat(), theme)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // City Name (Top)
        paint.color = theme.textColorSecondary
        paint.textSize = 15f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(cityWeather.cityName.uppercase(Locale.getDefault()), 110f, 32f, paint)

        // Animated Weather Icon (Center-Left)
        drawWeatherIcon(canvas, 62f, 95f, 32f, cityWeather.weatherDetails.condition, cityWeather.isNight, theme)

        // Temp (Center-Right)
        paint.color = theme.textColorPrimary
        paint.textSize = 46f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        drawTextWithShadow(canvas, formatTemp(cityWeather.weatherDetails.currentTemp, isCelsius), 112f, 110f, paint, theme)

        // Condition Text (Center-Bottom)
        paint.color = theme.textColorPrimary
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(cityWeather.weatherDetails.condition.displayName, 110f, 158f, paint)

        // Last Updated Time (Bottom)
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        paint.color = theme.textColorSecondary
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Updated ${timeFmt.format(Date())}", 110f, 190f, paint)

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

        // Header: Location
        paint.color = theme.textColorSecondary
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        val locationText = cityWeather.cityName.uppercase(Locale.getDefault())
        canvas.drawText(locationText, 30f, 48f, paint)

        // Animated Weather Icon (Right side)
        drawWeatherIcon(canvas, 275f, 120f, 52f, cityWeather.weatherDetails.condition, cityWeather.isNight, theme)

        // Temperature
        paint.color = theme.textColorPrimary
        paint.textSize = 76f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val tempStr = formatTemp(cityWeather.weatherDetails.currentTemp, isCelsius)
        drawTextWithShadow(canvas, tempStr, 30f, 138f, paint, theme)

        // Condition Name
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(cityWeather.weatherDetails.condition.displayName, 30f, 185f, paint)

        // High / Low
        val highLowText = "H: ${formatTemp(cityWeather.weatherDetails.highTemp, isCelsius)}   L: ${formatTemp(cityWeather.weatherDetails.lowTemp, isCelsius)}"
        paint.color = theme.textColorSecondary
        paint.textSize = 17f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(highLowText, 30f, 222f, paint)

        // Feels Like & Humidity
        val feelsText = "Feels like ${formatTemp(cityWeather.weatherDetails.feelsLike, isCelsius)} • Hum ${cityWeather.weatherDetails.humidity}%"
        paint.textSize = 15f
        canvas.drawText(feelsText, 30f, 256f, paint)

        // Wind Speed
        val windText = "Wind: ${formatSpeed(cityWeather.weatherDetails.windSpeed, isCelsius)}"
        canvas.drawText(windText, 30f, 285f, paint)

        // Last Updated Time
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val updatedText = "Updated ${timeFmt.format(Date())}"
        paint.textSize = 14f
        paint.color = theme.textColorSecondary
        canvas.drawText(updatedText, 30f, 325f, paint)

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

        // Left Header: Location
        paint.color = theme.textColorPrimary
        paint.textSize = 30f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT

        val region1 = cityWeather.region
        val country1 = cityWeather.country
        val displayLoc = when {
            !region1.isNullOrBlank() -> "${cityWeather.cityName.uppercase()}, ${region1.uppercase()}"
            !country1.isNullOrBlank() -> "${cityWeather.cityName.uppercase()}, ${country1.uppercase()}"
            else -> cityWeather.cityName.uppercase()
        }
        canvas.drawText(displayLoc, 40f, 65f, paint)

        // Condition text
        paint.color = theme.textColorSecondary
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(cityWeather.weatherDetails.condition.displayName, 40f, 108f, paint)

        // Updated time
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val updatedText = "Updated ${timeFmt.format(Date())}"
        paint.textSize = 17f
        canvas.drawText(updatedText, 40f, 145f, paint)

        // Bottom Pills (High/Low, Wind, Humidity, Rain)
        drawMiniPill(canvas, 40f, 210f, 155f, 65f, "HIGH / LOW", "H:${formatTemp(cityWeather.weatherDetails.highTemp, isCelsius)} L:${formatTemp(cityWeather.weatherDetails.lowTemp, isCelsius)}", theme)
        drawMiniPill(canvas, 205f, 210f, 155f, 65f, "WIND", formatSpeed(cityWeather.weatherDetails.windSpeed, isCelsius), theme)
        drawMiniPill(canvas, 370f, 210f, 155f, 65f, "HUMIDITY", "${cityWeather.weatherDetails.humidity}%", theme)

        val rainChance = cityWeather.weatherDetails.hourlyForecast.firstOrNull()?.precipitationChance ?: 0
        drawMiniPill(canvas, 535f, 210f, 145f, 65f, "RAIN CHANCE", "$rainChance%", theme)

        // Right Hero: Large Weather Icon
        drawWeatherIcon(canvas, 580f, 115f, 70f, cityWeather.weatherDetails.condition, cityWeather.isNight, theme)

        // Right Temperature
        paint.color = theme.textColorPrimary
        paint.textSize = 96f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        drawTextWithShadow(canvas, formatTemp(cityWeather.weatherDetails.currentTemp, isCelsius), 480f, 135f, paint, theme)

        // Feels like
        paint.color = theme.textColorSecondary
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Feels like ${formatTemp(cityWeather.weatherDetails.feelsLike, isCelsius)}", 480f, 175f, paint)

        return bitmap
    }

    // --- DRAWING UTILITY HELPERS ---

    private fun drawTextWithShadow(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint, theme: WeatherTheme) {
        val origColor = paint.color
        // Subtle drop shadow behind text
        paint.color = Color.BLACK
        paint.alpha = 90
        canvas.drawText(text, x + 3f, y + 4f, paint)
        paint.color = origColor
        canvas.drawText(text, x, y, paint)
    }

    private fun drawBackgroundCard(canvas: Canvas, width: Float, height: Float, theme: WeatherTheme) {
        val rect = RectF(0f, 0f, width, height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Ambient Drop Shadow (Outer ambient shadow cast behind main card)
        val shadowRect = RectF(4f, 8f, width - 4f, height + 6f)
        paint.color = Color.BLACK
        paint.alpha = 75
        canvas.drawRoundRect(shadowRect, 48f, 48f, paint)

        // 2. Base Linear Gradient Fill (App Theme Color Palette + Glass Transparency)
        val gradient = LinearGradient(
            0f, 0f, 0f, height,
            theme.topColor, theme.bottomColor,
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRoundRect(rect, 44f, 44f, paint)
        paint.shader = null

        // 3. Render Dynamic Weather Background Effects & Atmosphere Glow
        drawBackgroundEffects(canvas, width, height, theme)

        // 4. Frosted Glass Diagonal Specular Light Sweep
        val glossPath = Path()
        glossPath.moveTo(0f, 0f)
        glossPath.lineTo(width * 0.7f, 0f)
        glossPath.lineTo(0f, height * 0.75f)
        glossPath.close()

        val glossGradient = LinearGradient(
            0f, 0f, width * 0.4f, height * 0.4f,
            Color.argb(45, 255, 255, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        paint.shader = glossGradient
        canvas.drawPath(glossPath, paint)
        paint.shader = null

        // 5. Top Edge Reflection Bar Highlight
        val topBarRect = RectF(20f, 0f, width - 20f, 14f)
        val topBarGradient = LinearGradient(
            0f, 0f, 0f, 14f,
            Color.argb(70, 255, 255, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        paint.shader = topBarGradient
        canvas.drawRoundRect(topBarRect, 8f, 8f, paint)
        paint.shader = null

        // 6. Translucent Glass Border Stroke
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.WHITE
        paint.alpha = theme.borderAlpha
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
        paint.alpha = 195
        canvas.drawRoundRect(rect, 24f, 24f, paint)

        // Card Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.WHITE
        paint.alpha = 35
        canvas.drawRoundRect(rect, 24f, 24f, paint)

        // Label
        paint.style = Paint.Style.FILL
        paint.color = theme.textColorSecondary
        paint.textSize = 15f
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
        paint.alpha = 195
        canvas.drawRoundRect(rect, 20f, 20f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.color = Color.WHITE
        paint.alpha = 30
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
        paint.alpha = 195
        canvas.drawRoundRect(rect, 24f, 24f, paint)

        // Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.WHITE
        paint.alpha = 30
        canvas.drawRoundRect(rect, 24f, 24f, paint)

        // Time
        paint.style = Paint.Style.FILL
        paint.color = theme.textColorSecondary
        paint.textSize = 17f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(hour.time, x + w / 2f, y + 32f, paint)

        // Weather Icon
        drawWeatherIcon(canvas, x + w / 2f, y + 76f, 22f, hour.condition, hour.isNight, theme)

        // Temp
        paint.color = theme.textColorPrimary
        paint.textSize = 21f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(formatTemp(hour.temperature, isCelsius), x + w / 2f, y + 124f, paint)

        // Rain chance %
        if (hour.precipitationChance > 0) {
            paint.color = Color.parseColor("#38BDF8")
            paint.textSize = 13f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("💧${hour.precipitationChance}%", x + w / 2f, y + 152f, paint)
        }
    }

    // --- ANIMATED VECTOR WEATHER ICON DRAWING ENGINE ---
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
        val timeMs = System.currentTimeMillis()

        if (isNight && (condition == WeatherCondition.SUNNY || condition == WeatherCondition.PARTLY_CLOUDY)) {
            // Draw Moon with glowing ring & twinkling star
            paint.color = Color.parseColor("#38BDF8")
            paint.alpha = 45
            canvas.drawCircle(cx, cy, radius * 1.25f, paint)

            paint.color = Color.parseColor("#F1F5F9")
            paint.alpha = 255
            canvas.drawCircle(cx, cy, radius, paint)

            // Moon shadow cutout
            paint.color = theme.topColor
            canvas.drawCircle(cx + radius * 0.45f, cy - radius * 0.25f, radius * 0.85f, paint)
            return
        }

        when (condition) {
            WeatherCondition.SUNNY -> {
                // Animated Pulsing Corona
                val pulseScale = 1.0f + (sin(timeMs / 400.0) * 0.08f).toFloat()
                paint.color = Color.parseColor("#FDE047")
                paint.alpha = 65
                canvas.drawCircle(cx, cy, radius * 1.25f * pulseScale, paint)

                // Sun Disk
                paint.color = Color.parseColor("#FDE047")
                paint.alpha = 255
                canvas.drawCircle(cx, cy, radius * 0.7f, paint)

                // Animated Rotating Sun Rays
                paint.color = Color.parseColor("#FACC15")
                paint.strokeWidth = radius * 0.18f
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND

                val angleOffset = (timeMs / 45L) % 360
                for (i in 0 until 8) {
                    val angle = Math.toRadians((i * 45 + angleOffset).toDouble())
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

                // Animated Cloud Drift
                val driftX = sin(timeMs / 700.0).toFloat() * 3f
                paint.color = Color.WHITE
                canvas.drawCircle(cx - radius * 0.2f + driftX, cy + radius * 0.2f, radius * 0.45f, paint)
                canvas.drawCircle(cx + radius * 0.2f + driftX, cy + radius * 0.1f, radius * 0.55f, paint)
                val rect = RectF(cx - radius * 0.5f + driftX, cy + radius * 0.2f, cx + radius * 0.6f + driftX, cy + radius * 0.65f)
                canvas.drawRoundRect(rect, 15f, 15f, paint)
            }
            WeatherCondition.CLOUDY -> {
                // Overcast Clouds with subtle animation
                val driftX = sin(timeMs / 800.0).toFloat() * 4f
                paint.color = Color.parseColor("#E2E8F0")
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx - radius * 0.3f + driftX, cy, radius * 0.5f, paint)
                canvas.drawCircle(cx + radius * 0.2f + driftX, cy - radius * 0.1f, radius * 0.65f, paint)
                val rect = RectF(cx - radius * 0.6f + driftX, cy + radius * 0.1f, cx + radius * 0.7f + driftX, cy + radius * 0.6f)
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

                // Animated Falling Rain Drops
                paint.color = Color.parseColor("#38BDF8")
                paint.strokeWidth = radius * 0.15f
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND

                val drop1Y = cy + radius * 0.4f + ((timeMs / 12L + 0) % (radius * 0.4f).toInt())
                val drop2Y = cy + radius * 0.4f + ((timeMs / 12L + 15) % (radius * 0.4f).toInt())
                val drop3Y = cy + radius * 0.4f + ((timeMs / 12L + 30) % (radius * 0.4f).toInt())

                canvas.drawLine(cx - radius * 0.3f, drop1Y, cx - radius * 0.4f, drop1Y + radius * 0.3f, paint)
                canvas.drawLine(cx, drop2Y, cx - radius * 0.1f, drop2Y + radius * 0.3f, paint)
                canvas.drawLine(cx + radius * 0.3f, drop3Y, cx + radius * 0.2f, drop3Y + radius * 0.3f, paint)
            }
            WeatherCondition.STORM -> {
                // Dark Cloud
                paint.color = Color.parseColor("#94A3B8")
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy - radius * 0.2f, radius * 0.65f, paint)

                // Animated Lightning Bolt Brightness Pulse
                val lightningAlpha = (180 + sin(timeMs / 140.0) * 75).toInt().coerceIn(80, 255)
                paint.color = Color.parseColor("#FACC15")
                paint.alpha = lightningAlpha
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

                // Animated Floating Snowflakes
                paint.color = Color.WHITE
                val snow1X = cx - radius * 0.35f + sin(timeMs / 500.0).toFloat() * 4f
                val snow2X = cx + sin(timeMs / 500.0 + 1).toFloat() * 4f
                val snow3X = cx + radius * 0.35f + sin(timeMs / 500.0 + 2).toFloat() * 4f

                val snow1Y = cy + radius * 0.45f + ((timeMs / 16L) % (radius * 0.3f).toInt())
                val snow2Y = cy + radius * 0.45f + ((timeMs / 16L + 10) % (radius * 0.3f).toInt())
                val snow3Y = cy + radius * 0.45f + ((timeMs / 16L + 20) % (radius * 0.3f).toInt())

                canvas.drawCircle(snow1X, snow1Y, radius * 0.13f, paint)
                canvas.drawCircle(snow2X, snow2Y, radius * 0.13f, paint)
                canvas.drawCircle(snow3X, snow3Y, radius * 0.13f, paint)
            }
            WeatherCondition.FOGGY -> {
                // Animated Fog Bands
                val shiftX = sin(timeMs / 900.0).toFloat() * 6f
                paint.color = Color.parseColor("#E2E8F0")
                paint.strokeWidth = radius * 0.2f
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(cx - radius * 0.7f + shiftX, cy - radius * 0.3f, cx + radius * 0.7f + shiftX, cy - radius * 0.3f, paint)
                canvas.drawLine(cx - radius * 0.85f - shiftX, cy, cx + radius * 0.85f - shiftX, cy, paint)
                canvas.drawLine(cx - radius * 0.6f + shiftX, cy + radius * 0.3f, cx + radius * 0.6f + shiftX, cy + radius * 0.3f, paint)
            }
        }
    }
}
