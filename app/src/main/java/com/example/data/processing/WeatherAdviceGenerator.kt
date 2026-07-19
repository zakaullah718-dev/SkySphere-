package com.example.data.processing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.models.WeatherDetails
import com.example.data.models.WeatherCondition

/**
 * Priority levels for weather insights to allow color-coded visual rendering.
 */
enum class AdvicePriority {
    CRITICAL, // Urgent threat/alert (e.g., extreme UV, severe heat/cold, storm)
    WARNING,  // Moderate warning (e.g., high wind, rain expected, moderate AQI)
    INFO,     // General information (e.g., standard UV, wind, air quality details)
    COMFORT   // Comfort & lifestyle tips (e.g., apparel recommendation, outdoor activities)
}

/**
 * Categories of weather advice to classify different recommendations.
 */
enum class AdviceCategory {
    TEMPERATURE,
    PRECIPITATION,
    WIND,
    UV_SUN,
    AIR_QUALITY,
    LIFESTYLE
}

/**
 * Rich model containing precise, context-aware weather advice.
 */
data class WeatherAdvice(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val priority: AdvicePriority,
    val category: AdviceCategory
)

/**
 * Local processing module that performs real-time diagnostics on weather variables
 * to generate rich, contextual, human-friendly safety and comfort guidelines.
 */
object WeatherAdviceGenerator {

    /**
     * Generates a list of rich WeatherAdvice items based on the complete weather dataset.
     */
    fun generateAdvice(details: WeatherDetails, isCelsius: Boolean): List<WeatherAdvice> {
        val adviceList = mutableListOf<WeatherAdvice>()

        // 1. THERMAL COMFORT & CLOTHING INTERPRETATION (Temperature & Feels Like)
        val tempC = if (isCelsius) details.currentTemp else ((details.currentTemp - 32) * 5) / 9
        val feelsLikeC = if (isCelsius) details.feelsLike else ((details.feelsLike - 32) * 5) / 9
        val currentTempUnit = if (isCelsius) "${details.currentTemp}°C" else "${details.currentTemp}°F"
        val feelsLikeUnit = if (isCelsius) "${details.feelsLike}°C" else "${details.feelsLike}°F"

        // Analyze wind-chill or heat-index delta
        val tempDelta = feelsLikeC - tempC
        if (tempDelta <= -3 && tempC in 5..20) {
            adviceList.add(
                WeatherAdvice(
                    title = "Wind-Chill Notice",
                    description = "Wind speeds are amplifying the cold. It feels like $feelsLikeUnit but actual is $currentTempUnit. Add a windbreaker layer.",
                    icon = Icons.Filled.Thermostat,
                    priority = AdvicePriority.WARNING,
                    category = AdviceCategory.TEMPERATURE
                )
            )
        } else if (tempDelta >= 3 && tempC >= 25) {
            adviceList.add(
                WeatherAdvice(
                    title = "Humid Heat Index",
                    description = "High humidity is compounding heat strain. It feels like $feelsLikeUnit due to ${details.humidity}% humidity. Ensure high fluid intake.",
                    icon = Icons.Filled.Thermostat,
                    priority = AdvicePriority.WARNING,
                    category = AdviceCategory.TEMPERATURE
                )
            )
        }

        // Layering guide based on feelsLike temperature
        when {
            feelsLikeC < 0 -> {
                adviceList.add(
                    WeatherAdvice(
                        title = "Severe Cold Alert",
                        description = "Freezing temperatures ($feelsLikeUnit). Heavy winter coat, thermal inner-layers, gloves, and a beanie are essential to prevent heat loss.",
                        icon = Icons.Filled.SevereCold,
                        priority = AdvicePriority.CRITICAL,
                        category = AdviceCategory.TEMPERATURE
                    )
                )
            }
            feelsLikeC in 0..10 -> {
                adviceList.add(
                    WeatherAdvice(
                        title = "Winter Wear Recommended",
                        description = "Chilly day ($feelsLikeUnit). A warm coat or heavy sweater over lightweight layers, coupled with a scarf, is highly recommended.",
                        icon = Icons.Filled.Checkroom,
                        priority = AdvicePriority.WARNING,
                        category = AdviceCategory.TEMPERATURE
                    )
                )
            }
            feelsLikeC in 11..17 -> {
                adviceList.add(
                    WeatherAdvice(
                        title = "Cool Weather Comfort",
                        description = "Fresh air ($feelsLikeUnit). A light jacket, fleece pullover, or cardigan is perfect for transitioning through the day.",
                        icon = Icons.Filled.Checkroom,
                        priority = AdvicePriority.INFO,
                        category = AdviceCategory.TEMPERATURE
                    )
                )
            }
            feelsLikeC in 18..25 -> {
                adviceList.add(
                    WeatherAdvice(
                        title = "Optimal Thermal Comfort",
                        description = "Pleasant warmth ($feelsLikeUnit). Lightweight clothing (t-shirts, cotton shirts) will keep you perfectly comfortable outdoors.",
                        icon = Icons.Filled.Checkroom,
                        priority = AdvicePriority.COMFORT,
                        category = AdviceCategory.TEMPERATURE
                    )
                )
            }
            feelsLikeC in 26..32 -> {
                adviceList.add(
                    WeatherAdvice(
                        title = "Warm Dress & Hydration",
                        description = "Warm temperatures ($feelsLikeUnit). Breathable cotton or linen apparel is ideal. Carry a bottle of water if walking.",
                        icon = Icons.Filled.LocalDrink,
                        priority = AdvicePriority.INFO,
                        category = AdviceCategory.TEMPERATURE
                    )
                )
            }
            else -> {
                adviceList.add(
                    WeatherAdvice(
                        title = "Extreme Heat Advisory",
                        description = "Extreme warmth ($feelsLikeUnit). Wear loose, light-colored clothing, wide-brimmed hats, and limit strenuous activities during midday hours.",
                        icon = Icons.Filled.Warning,
                        priority = AdvicePriority.CRITICAL,
                        category = AdviceCategory.TEMPERATURE
                    )
                )
            }
        }

        // 2. PRECIPITATION INTEGRATION (Rain, Snow, Storms)
        val currentCondition = details.condition
        val isRainingOrStorming = currentCondition == WeatherCondition.RAINY || currentCondition == WeatherCondition.STORM
        val isSnowing = currentCondition == WeatherCondition.SNOWY

        if (isRainingOrStorming) {
            adviceList.add(
                WeatherAdvice(
                    title = "Rain Gear Required",
                    description = "Active rainfall. Heavy rain or storm detected nearby. Waterproof jacket, sturdy umbrella, and slip-resistant footwear are mandatory.",
                    icon = Icons.Filled.Umbrella,
                    priority = AdvicePriority.CRITICAL,
                    category = AdviceCategory.PRECIPITATION
                )
            )
        } else if (isSnowing) {
            adviceList.add(
                WeatherAdvice(
                    title = "Snow Gear & Traction",
                    description = "Active snowfall. Ground freezing likely. Wear insulated, water-resistant boots with aggressive treads to prevent slipping.",
                    icon = Icons.Filled.AcUnit,
                    priority = AdvicePriority.CRITICAL,
                    category = AdviceCategory.PRECIPITATION
                )
            )
        } else {
            // Check future hourly precipitation forecasts in the next 6 hours
            val upcomingRainHour = details.hourlyForecast.take(6).firstOrNull { it.precipitationChance > 35 }
            if (upcomingRainHour != null) {
                adviceList.add(
                    WeatherAdvice(
                        title = "Carry an Umbrella",
                        description = "Precipitation expected. Rain is likely around ${upcomingRainHour.time} (${upcomingRainHour.precipitationChance}% probability). Plan your commute accordingly.",
                        icon = Icons.Filled.Umbrella,
                        priority = AdvicePriority.WARNING,
                        category = AdviceCategory.PRECIPITATION
                    )
                )
            } else {
                // Check daily forecast for general instability
                val rainChanceToday = details.dailyForecast.firstOrNull()?.precipitationChance ?: 0
                if (rainChanceToday > 35) {
                    adviceList.add(
                        WeatherAdvice(
                            title = "Unstable Conditions",
                            description = "Scattered showers are possible today (${rainChanceToday}% chance). Keep an umbrella in your vehicle or bag.",
                            icon = Icons.Filled.Umbrella,
                            priority = AdvicePriority.INFO,
                            category = AdviceCategory.PRECIPITATION
                        )
                    )
                } else {
                    adviceList.add(
                        WeatherAdvice(
                            title = "Dry & Clear Skies",
                            description = "No precipitation expected today. Enjoy dry roads and clear walking paths.",
                            icon = Icons.Filled.WbSunny,
                            priority = AdvicePriority.COMFORT,
                            category = AdviceCategory.PRECIPITATION
                        )
                    )
                }
            }
        }

        // 3. UV INDEX & SUN PROTECTION
        when {
            details.uvIndex <= 2 -> {
                adviceList.add(
                    WeatherAdvice(
                        title = "Safe Sun Exposure",
                        description = "Minimal UV radiation. Safe to enjoy extended outdoor time without major sunburn risk.",
                        icon = Icons.Filled.WbSunny,
                        priority = AdvicePriority.COMFORT,
                        category = AdviceCategory.UV_SUN
                    )
                )
            }
            details.uvIndex in 3..5 -> {
                adviceList.add(
                    WeatherAdvice(
                        title = "Moderate UV Alert",
                        description = "Moderate sun radiation. Apply SPF 15+ sunscreen and wear sunglasses if outdoors for over 20 minutes.",
                        icon = Icons.Filled.WbSunny,
                        priority = AdvicePriority.INFO,
                        category = AdviceCategory.UV_SUN
                    )
                )
            }
            details.uvIndex in 6..7 -> {
                adviceList.add(
                    WeatherAdvice(
                        title = "High UV Index",
                        description = "High UV intensity. Protective sunscreen (SPF 30+), wide-brimmed hats, and sunglasses are highly recommended. Seek shade during peak midday hours.",
                        icon = Icons.Filled.WbSunny,
                        priority = AdvicePriority.WARNING,
                        category = AdviceCategory.UV_SUN
                    )
                )
            }
            else -> { // UV Index >= 8 (Very High to Extreme)
                adviceList.add(
                    WeatherAdvice(
                        title = "Extreme UV Hazard",
                        description = "Unprotected skin can burn rapidly. Avoid direct sun exposure between 10 AM and 4 PM. Apply SPF 50+ generously every 2 hours.",
                        icon = Icons.Filled.Warning,
                        priority = AdvicePriority.CRITICAL,
                        category = AdviceCategory.UV_SUN
                    )
                )
            }
        }

        // 4. WIND SPEED INTERPRETATION
        when {
            details.windSpeed < 12.0 -> {
                adviceList.add(
                    WeatherAdvice(
                        title = "Calm Air",
                        description = "Perfect conditions for outdoor sports like badminton, cycling, or drone photography.",
                        icon = Icons.Filled.Air,
                        priority = AdvicePriority.COMFORT,
                        category = AdviceCategory.WIND
                    )
                )
            }
            details.windSpeed in 12.0..25.0 -> {
                adviceList.add(
                    WeatherAdvice(
                        title = "Fresh Commute Winds",
                        description = "Moderate breezes. Great day for natural laundry drying or flying a kite. Holds minor resistance for cycling.",
                        icon = Icons.Filled.Air,
                        priority = AdvicePriority.INFO,
                        category = AdviceCategory.WIND
                    )
                )
            }
            details.windSpeed in 26.0..40.0 -> {
                adviceList.add(
                    WeatherAdvice(
                        title = "Strong Gale Alert",
                        description = "Strong gusty winds. Hold onto hats and umbrellas. Avoid placing light objects on open terraces or balconies.",
                        icon = Icons.Filled.Air,
                        priority = AdvicePriority.WARNING,
                        category = AdviceCategory.WIND
                    )
                )
            }
            else -> { // Wind speed > 40 km/h
                adviceList.add(
                    WeatherAdvice(
                        title = "Hazardous Wind Warning",
                        description = "Dangerous wind gusts! Secure loose outdoor patio furniture, trash bins, and tents. Drive high-profile vehicles with extreme care.",
                        icon = Icons.Filled.Warning,
                        priority = AdvicePriority.CRITICAL,
                        category = AdviceCategory.WIND
                    )
                )
            }
        }

        // 5. AIR QUALITY HEALTH ADVICE
        val aqi = details.airQuality.aqi
        when (aqi) {
            1 -> {
                adviceList.add(
                    WeatherAdvice(
                        title = "Pristine Air Quality",
                        description = "Air is beautifully clean. Ideal for breathing exercises, morning running, and outdoor aerobics.",
                        icon = Icons.Filled.Eco,
                        priority = AdvicePriority.COMFORT,
                        category = AdviceCategory.AIR_QUALITY
                    )
                )
            }
            2 -> {
                adviceList.add(
                    WeatherAdvice(
                        title = "Acceptable Air Quality",
                        description = "Moderate/Fair air. Completely safe for almost everyone. Extremely sensitive individuals should monitor throat irritation.",
                        icon = Icons.Filled.Eco,
                        priority = AdvicePriority.INFO,
                        category = AdviceCategory.AIR_QUALITY
                    )
                )
            }
            3 -> {
                adviceList.add(
                    WeatherAdvice(
                        title = "Sensitive Groups Warning",
                        description = "Unhealthy for sensitive individuals. People with asthma or cardiopulmonary conditions should limit intense outdoor exertion.",
                        icon = Icons.Filled.Masks,
                        priority = AdvicePriority.WARNING,
                        category = AdviceCategory.AIR_QUALITY
                    )
                )
            }
            else -> { // AQI >= 4
                adviceList.add(
                    WeatherAdvice(
                        title = "High Pollution Alert",
                        description = "Unhealthy air for all. Wear a high-efficiency mask (N95) outdoors, close windows, and activate indoor air purifiers.",
                        icon = Icons.Filled.Warning,
                        priority = AdvicePriority.CRITICAL,
                        category = AdviceCategory.AIR_QUALITY
                    )
                )
            }
        }

        // 6. JOINT SENSITIVITY & HEADACHE ADVISORY (Barometric Pressure)
        if (details.pressureHpa < 1008) {
            adviceList.add(
                WeatherAdvice(
                    title = "Low Pressure Advisory",
                    description = "Low barometric pressure (${details.pressureHpa} hPa) may induce minor headaches, joint swelling, or sleepiness in pressure-sensitive individuals.",
                    icon = Icons.Filled.Info,
                    priority = AdvicePriority.COMFORT,
                    category = AdviceCategory.LIFESTYLE
                )
            )
        }

        // 7. VEHICLE WASH & LIFESTYLE ADVICE
        val willRainFutureDays = details.dailyForecast.drop(1).take(2).any { it.precipitationChance > 35 }
        if (willRainFutureDays) {
            adviceList.add(
                WeatherAdvice(
                    title = "Delay Car Wash",
                    description = "Postpone washing your vehicle. Showers are forecasted in the next 48 hours.",
                    icon = Icons.Filled.DirectionsCar,
                    priority = AdvicePriority.COMFORT,
                    category = AdviceCategory.LIFESTYLE
                )
            )
        } else if (!isRainingOrStorming) {
            adviceList.add(
                WeatherAdvice(
                    title = "Perfect Car Wash Window",
                    description = "Clean skies and no upcoming rain forecasted. Perfect timing for washing your vehicle.",
                    icon = Icons.Filled.DirectionsCar,
                    priority = AdvicePriority.COMFORT,
                    category = AdviceCategory.LIFESTYLE
                )
            )
        }

        return adviceList
    }

    /**
     * Backward-compatible simple mapper that produces a list of strings matching the legacy list expectation,
     * but infused with high-value local intelligence advice.
     */
    fun generateSimpleInsights(details: WeatherDetails, isCelsius: Boolean): List<String> {
        return generateAdvice(details, isCelsius).map { advice ->
            "**${advice.title}**: ${advice.description}"
        }
    }
}
