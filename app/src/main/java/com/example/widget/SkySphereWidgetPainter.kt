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
        val themeType: ThemeType
    )

    fun getThemeForWeather(cityWeather: CityWeather): WeatherTheme {
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

        return when (themeType) {
            ThemeType.SUNNY -> WeatherTheme(
                topColor = Color.parseColor("#0284C7"),
                bottomColor = Color.parseColor("#0369A1"),
                accentColor = Color.parseColor("#FDE047"),
                cardBgColor = Color.parseColor("#0C4A6E"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#BAE6FD"),
                themeType = ThemeType.SUNNY
            )
            ThemeType.PARTLY_CLOUDY -> WeatherTheme(
                topColor = Color.parseColor("#0369A1"),
                bottomColor = Color.parseColor("#075985"),
                accentColor = Color.parseColor("#BAE6FD"),
                cardBgColor = Color.parseColor("#0C4A6E"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#E0F2FE"),
                themeType = ThemeType.PARTLY_CLOUDY
            )
            ThemeType.CLOUDY -> WeatherTheme(
                topColor = Color.parseColor("#475569"),
                bottomColor = Color.parseColor("#1E293B"),
                accentColor = Color.parseColor("#CBD5E1"),
                cardBgColor = Color.parseColor("#334155"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#94A3B8"),
                themeType = ThemeType.CLOUDY
            )
            ThemeType.RAIN -> WeatherTheme(
                topColor = Color.parseColor("#0F766E"),
                bottomColor = Color.parseColor("#042F2E"),
                accentColor = Color.parseColor("#5EEAD4"),
                cardBgColor = Color.parseColor("#134E4A"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#CCFBF1"),
                themeType = ThemeType.RAIN
            )
            ThemeType.THUNDERSTORM -> WeatherTheme(
                topColor = Color.parseColor("#4C1D95"),
                bottomColor = Color.parseColor("#1E1B4B"),
                accentColor = Color.parseColor("#FDE047"),
                cardBgColor = Color.parseColor("#2E1065"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#DDD6FE"),
                themeType = ThemeType.THUNDERSTORM
            )
            ThemeType.SNOW -> WeatherTheme(
                topColor = Color.parseColor("#0891B2"),
                bottomColor = Color.parseColor("#164E63"),
                accentColor = Color.parseColor("#CFFAFE"),
                cardBgColor = Color.parseColor("#0E7490"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#ECFEFF"),
                themeType = ThemeType.SNOW
            )
            ThemeType.FOG -> WeatherTheme(
                topColor = Color.parseColor("#64748B"),
                bottomColor = Color.parseColor("#334155"),
                accentColor = Color.parseColor("#F1F5F9"),
                cardBgColor = Color.parseColor("#475569"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#CBD5E1"),
                themeType = ThemeType.FOG
            )
            ThemeType.SUNRISE -> WeatherTheme(
                topColor = Color.parseColor("#EA580C"),
                bottomColor = Color.parseColor("#9D174D"),
                accentColor = Color.parseColor("#FDE047"),
                cardBgColor = Color.parseColor("#C2410C"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#FED7AA"),
                themeType = ThemeType.SUNRISE
            )
            ThemeType.SUNSET -> WeatherTheme(
                topColor = Color.parseColor("#7E22CE"),
                bottomColor = Color.parseColor("#EA580C"),
                accentColor = Color.parseColor("#FDE047"),
                cardBgColor = Color.parseColor("#581C87"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#F3E8FF"),
                themeType = ThemeType.SUNSET
            )
            ThemeType.NIGHT -> WeatherTheme(
                topColor = Color.parseColor("#0F172A"),
                bottomColor = Color.parseColor("#020617"),
                accentColor = Color.parseColor("#38BDF8"),
                cardBgColor = Color.parseColor("#1E293B"),
                textColorPrimary = Color.WHITE,
                textColorSecondary = Color.parseColor("#94A3B8"),
                themeType = ThemeType.NIGHT
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

    // --- DRAW WIDGET 1x1 (SMALL BADGE) ---
    fun drawWidget1x1(context: Context, cityWeather: CityWeather, isCelsius: Boolean): Bitmap {
        val width = 220
        val height = 220
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val theme = getThemeForWeather(cityWeather)
        drawBackgroundCard(canvas, width.toFloat(), height.toFloat(), theme)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // City Name (Top)
        paint.color = theme.textColorSecondary
        paint.textSize = 15f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(cityWeather.cityName.uppercase(Locale.getDefault()), 110f, 32f, paint)

        // Weather Icon (Center-Left)
        drawWeatherIcon(canvas, 62f, 95f, 32f, cityWeather.weatherDetails.condition, cityWeather.isNight, theme)

        // Temp (Center-Right)
        paint.color = theme.textColorPrimary
        paint.textSize = 46f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(formatTemp(cityWeather.weatherDetails.currentTemp, isCelsius), 112f, 110f, paint)

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

    // --- DRAW WIDGET 2x2 (MEDIUM SUMMARY) ---
    fun drawWidget2x2(context: Context, cityWeather: CityWeather, isCelsius: Boolean): Bitmap {
        val width = 360
        val height = 360
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val theme = getThemeForWeather(cityWeather)
        drawBackgroundCard(canvas, width.toFloat(), height.toFloat(), theme)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Header: Location
        paint.color = theme.textColorSecondary
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        val locationText = cityWeather.cityName.uppercase(Locale.getDefault())
        canvas.drawText(locationText, 30f, 48f, paint)

        // Weather Icon (Right side)
        drawWeatherIcon(canvas, 275f, 120f, 52f, cityWeather.weatherDetails.condition, cityWeather.isNight, theme)

        // Temperature
        paint.color = theme.textColorPrimary
        paint.textSize = 76f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val tempStr = formatTemp(cityWeather.weatherDetails.currentTemp, isCelsius)
        canvas.drawText(tempStr, 30f, 138f, paint)

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
        canvas.drawText(formatTemp(cityWeather.weatherDetails.currentTemp, isCelsius), 480f, 135f, paint)

        // Feels like
        paint.color = theme.textColorSecondary
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Feels like ${formatTemp(cityWeather.weatherDetails.feelsLike, isCelsius)}", 480f, 175f, paint)

        return bitmap
    }

    // --- DRAW WIDGET 4x4 (FLAGSHIP WIDGET) ---
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

        val region2 = cityWeather.region
        val country2 = cityWeather.country
        val displayLoc4x4 = when {
            !region2.isNullOrBlank() -> "${cityWeather.cityName.uppercase()}, ${region2.uppercase()}"
            !country2.isNullOrBlank() -> "${cityWeather.cityName.uppercase()}, ${country2.uppercase()}"
            else -> cityWeather.cityName.uppercase()
        }
        canvas.drawText(displayLoc4x4, 40f, 62f, paint)

        // Last Updated
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val updatedText = "Updated ${timeFmt.format(Date())}"
        paint.color = theme.textColorSecondary
        paint.textSize = 19f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(updatedText, 40f, 98f, paint)

        // Hero Weather Illustration (Center-Right)
        drawWeatherIcon(canvas, 560f, 175f, 90f, cityWeather.weatherDetails.condition, cityWeather.isNight, theme)

        // Hero Temperature
        paint.color = theme.textColorPrimary
        paint.textSize = 118f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(formatTemp(cityWeather.weatherDetails.currentTemp, isCelsius), 40f, 210f, paint)

        // Condition & High/Low
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val condText = cityWeather.weatherDetails.condition.displayName
        canvas.drawText(condText, 40f, 258f, paint)

        paint.color = theme.textColorSecondary
        paint.textSize = 21f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val highLowText = "High: ${formatTemp(cityWeather.weatherDetails.highTemp, isCelsius)}   Low: ${formatTemp(cityWeather.weatherDetails.lowTemp, isCelsius)}   Feels: ${formatTemp(cityWeather.weatherDetails.feelsLike, isCelsius)}"
        canvas.drawText(highLowText, 40f, 298f, paint)

        // 4 WEATHER METRICS GRID CARDS (Y = 335 to 455)
        val cardWidth = 148f
        val cardHeight = 110f
        val startX = 40f
        val spacing = 16f

        // Metric 1: Humidity
        drawMetricCard(canvas, startX + 0 * (cardWidth + spacing), 335f, cardWidth, cardHeight, "HUMIDITY", "${cityWeather.weatherDetails.humidity}%", theme)
        // Metric 2: Wind
        drawMetricCard(canvas, startX + 1 * (cardWidth + spacing), 335f, cardWidth, cardHeight, "WIND", formatSpeed(cityWeather.weatherDetails.windSpeed, isCelsius), theme)
        // Metric 3: UV Index
        drawMetricCard(canvas, startX + 2 * (cardWidth + spacing), 335f, cardWidth, cardHeight, "UV INDEX", "${cityWeather.weatherDetails.uvIndex} Mod", theme)
        // Metric 4: Rain Chance
        val precip = cityWeather.weatherDetails.hourlyForecast.firstOrNull()?.precipitationChance ?: 0
        drawMetricCard(canvas, startX + 3 * (cardWidth + spacing), 335f, cardWidth, cardHeight, "RAIN CHANCE", "$precip%", theme)

        // HOURLY FORECAST SECTION HEADER (Y = 485)
        paint.color = theme.textColorPrimary
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("HOURLY FORECAST", 40f, 490f, paint)

        // Divider Line
        paint.color = Color.WHITE
        paint.alpha = 30
        paint.strokeWidth = 2f
        canvas.drawLine(240f, 483f, 680f, 483f, paint)

        // 5 HOURLY FORECAST PILLS (Y = 510 to 680)
        val hourlyList = cityWeather.weatherDetails.hourlyForecast.take(5)
        val pillWidth = 120f
        val pillHeight = 170f
        val pillSpacing = 10f

        hourlyList.forEachIndexed { idx, hour ->
            val px = 40f + idx * (pillWidth + pillSpacing)
            drawHourlyPill(canvas, px, 510f, pillWidth, pillHeight, hour, isCelsius, theme)
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
        canvas.drawRoundRect(rect, 44f, 44f, paint)
        paint.shader = null

        // Render Dynamic Weather Background Effects
        drawBackgroundEffects(canvas, width, height, theme)

        // Subtle Glassmorphic Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.WHITE
        paint.alpha = 45
        canvas.drawRoundRect(rect, 44f, 44f, paint)
    }

    private fun drawBackgroundEffects(canvas: Canvas, width: Float, height: Float, theme: WeatherTheme) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (theme.themeType) {
            ThemeType.SUNNY -> {
                val radialGradient = RadialGradient(
                    width * 0.85f, height * 0.15f, width * 0.45f,
                    Color.argb(140, 255, 224, 130),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                paint.shader = radialGradient
                canvas.drawCircle(width * 0.85f, height * 0.15f, width * 0.45f, paint)
                paint.shader = null
            }
            ThemeType.SUNRISE -> {
                val radialGradient = RadialGradient(
                    width * 0.5f, height * 0.95f, width * 0.6f,
                    Color.argb(130, 253, 224, 71),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                paint.shader = radialGradient
                canvas.drawCircle(width * 0.5f, height * 0.95f, width * 0.6f, paint)
                paint.shader = null
            }
            ThemeType.SUNSET -> {
                val radialGradient = RadialGradient(
                    width * 0.5f, height * 0.9f, width * 0.65f,
                    Color.argb(140, 251, 146, 60),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                paint.shader = radialGradient
                canvas.drawCircle(width * 0.5f, height * 0.9f, width * 0.65f, paint)
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
                for (star in starCoords) {
                    paint.alpha = star[3].toInt()
                    canvas.drawCircle(width * star[0], height * star[1], star[2], paint)
                }
                val moonAura = RadialGradient(
                    width * 0.8f, height * 0.2f, width * 0.35f,
                    Color.argb(70, 56, 189, 248),
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
                for (drop in rainCoords) {
                    val sx = width * drop[0]
                    val sy = height * drop[1]
                    paint.alpha = 75
                    canvas.drawLine(sx, sy, sx - 15f, sy + 30f, paint)
                }
                paint.style = Paint.Style.FILL
            }
            ThemeType.SNOW -> {
                paint.color = Color.WHITE
                val snowCoords = arrayOf(
                    floatArrayOf(0.1f, 0.15f, 4f, 160f), floatArrayOf(0.28f, 0.1f, 5f, 200f),
                    floatArrayOf(0.48f, 0.22f, 3.5f, 140f), floatArrayOf(0.68f, 0.08f, 4.5f, 180f),
                    floatArrayOf(0.85f, 0.18f, 5.5f, 220f), floatArrayOf(0.18f, 0.45f, 4f, 150f),
                    floatArrayOf(0.38f, 0.52f, 3.5f, 130f), floatArrayOf(0.58f, 0.42f, 4.5f, 170f),
                    floatArrayOf(0.78f, 0.58f, 5f, 190f), floatArrayOf(0.12f, 0.78f, 4f, 160f),
                    floatArrayOf(0.52f, 0.75f, 4.5f, 180f), floatArrayOf(0.86f, 0.72f, 3.5f, 140f)
                )
                for (s in snowCoords) {
                    paint.alpha = s[3].toInt()
                    canvas.drawCircle(width * s[0], height * s[1], s[2], paint)
                }
            }
            ThemeType.THUNDERSTORM -> {
                val stormGlow = RadialGradient(
                    width * 0.75f, height * 0.25f, width * 0.45f,
                    Color.argb(80, 253, 224, 71),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                paint.shader = stormGlow
                canvas.drawCircle(width * 0.75f, height * 0.25f, width * 0.45f, paint)
                paint.shader = null
            }
            ThemeType.PARTLY_CLOUDY, ThemeType.CLOUDY, ThemeType.FOG -> {
                paint.color = Color.WHITE
                paint.alpha = 22
                canvas.drawCircle(width * 0.85f, height * 0.2f, width * 0.25f, paint)
                canvas.drawCircle(width * 0.7f, height * 0.15f, width * 0.2f, paint)
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
        paint.alpha = 185
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
        paint.alpha = 185
        canvas.drawRoundRect(rect, 20f, 20f, paint)

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
        paint.alpha = 185
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
            // Draw Moon with glow ring
            paint.color = Color.parseColor("#38BDF8")
            paint.alpha = 40
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
                // Outer glow
                paint.color = Color.parseColor("#FDE047")
                paint.alpha = 60
                canvas.drawCircle(cx, cy, radius * 1.2f, paint)

                // Sun Disk
                paint.color = Color.parseColor("#FDE047")
                paint.alpha = 255
                canvas.drawCircle(cx, cy, radius * 0.7f, paint)

                // Sun Rays
                paint.color = Color.parseColor("#FACC15")
                paint.strokeWidth = radius * 0.18f
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
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
