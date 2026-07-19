package com.example.data.processing

import com.example.data.models.CityWeather
import com.example.data.models.WeatherCondition
import com.example.data.models.WeatherDetails
import com.example.data.models.ForecastHour
import kotlin.math.absoluteValue

data class WeatherAlert(
    val type: AlertType,
    val title: String,
    val description: String,
    val severity: AlertSeverity,
    val recommendation: String
)

enum class AlertType {
    HEAVY_RAIN,
    THUNDERSTORM,
    STRONG_WIND,
    EXTREME_HEAT,
    EXTREME_COLD,
    SNOW,
    DENSE_FOG,
    HIGH_UV,
    POOR_AQI
}

enum class AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}

data class LifestyleRecommendation(
    val name: String,
    val score: Int, // 0 to 100
    val status: String, // e.g., "Excellent", "Good", "Fair", "Poor"
    val description: String,
    val iconName: String // Material icon name or visual cue
)

data class HealthInsight(
    val title: String,
    val value: String,
    val status: String,
    val recommendation: String,
    val iconName: String,
    val severity: AlertSeverity
)

data class TravelHourSlot(
    val time: String,
    val tempFormatted: String,
    val condition: WeatherCondition,
    val score: Int, // 0 to 100
    val suitability: String, // "Ideal", "Moderate", "Not Recommended"
    val tip: String
)

object IntelligentWeatherProcessor {

    /**
     * Identifies active high-value alerts based on exact weather thresholds.
     */
    fun processSmartAlerts(details: WeatherDetails, isCelsius: Boolean): List<WeatherAlert> {
        val alerts = mutableListOf<WeatherAlert>()
        val tempC = if (isCelsius) details.currentTemp.toDouble() else ((details.currentTemp - 32) * 5) / 9.0
        val windKmh = details.windSpeed // repository stores wind speed

        // 1. Heavy Rain
        val maxPrecipChance = details.hourlyForecast.take(6).maxOfOrNull { it.precipitationChance } ?: 0
        if (details.condition == WeatherCondition.RAINY || maxPrecipChance >= 75) {
            alerts.add(
                WeatherAlert(
                    type = AlertType.HEAVY_RAIN,
                    title = "Heavy Precipitation Alert",
                    description = "Heavy rainfall is detected or forecasted with up to $maxPrecipChance% probability in the upcoming hours.",
                    severity = AlertSeverity.WARNING,
                    recommendation = "Carry a reliable umbrella, wear waterproof gear, and watch for localized road puddling."
                )
            )
        }

        // 2. Thunderstorm
        if (details.condition == WeatherCondition.STORM) {
            alerts.add(
                WeatherAlert(
                    type = AlertType.THUNDERSTORM,
                    title = "Severe Thunderstorm Warning",
                    description = "Dangerous atmospheric instability and lightning storms are currently active in your region.",
                    severity = AlertSeverity.CRITICAL,
                    recommendation = "Seek immediate indoor shelter. Avoid tall trees, open spaces, and unplug sensitive electrical devices."
                )
            )
        }

        // 3. Strong Wind
        if (windKmh > 35.0) {
            alerts.add(
                WeatherAlert(
                    type = AlertType.STRONG_WIND,
                    title = "High Wind Advisory",
                    description = "Strong winds averaging ${details.windSpeed.toInt()} km/h may cause flight delays and unstable high-profile driving.",
                    severity = AlertSeverity.WARNING,
                    recommendation = "Secure loose patio furniture, lightweight trash bins, and exercise caution when driving high-profile vehicles."
                )
            )
        }

        // 4. Extreme Heat
        if (tempC >= 35.0) {
            alerts.add(
                WeatherAlert(
                    type = AlertType.EXTREME_HEAT,
                    title = "Extreme Heat Warning",
                    description = "Unusually high thermal strain detected with temperatures reaching ${details.currentTemp}°${if (isCelsius) "C" else "F"}.",
                    severity = AlertSeverity.CRITICAL,
                    recommendation = "Stay indoors in air-conditioned environments, limit direct sun exposure, and drink plenty of water to prevent heat stroke."
                )
            )
        }

        // 5. Extreme Cold
        if (tempC <= 0.0) {
            alerts.add(
                WeatherAlert(
                    type = AlertType.EXTREME_COLD,
                    title = "Freezing Temperature Warning",
                    description = "Sub-zero temperatures of ${details.currentTemp}°${if (isCelsius) "C" else "F"} pose frostbite and icy road hazards.",
                    severity = AlertSeverity.CRITICAL,
                    recommendation = "Wear multiple insulating clothing layers, protect exposed skin, and check tire traction."
                )
            )
        }

        // 6. Snow
        if (details.condition == WeatherCondition.SNOWY) {
            alerts.add(
                WeatherAlert(
                    type = AlertType.SNOW,
                    title = "Active Snowfall Warning",
                    description = "Accumulating winter snowfall is causing sub-optimal visibility and slick pavement conditions.",
                    severity = AlertSeverity.WARNING,
                    recommendation = "Use appropriate footwear, drive at reduced speeds, and maintain safe braking distances."
                )
            )
        }

        // 7. Dense Fog
        if (details.visibilityKm < 1.5) {
            alerts.add(
                WeatherAlert(
                    type = AlertType.DENSE_FOG,
                    title = "Dense Fog Advisory",
                    description = "Extremely low horizontal visibility (${details.visibilityKm} km) is obstructing highway and transit routes.",
                    severity = AlertSeverity.CRITICAL,
                    recommendation = "Utilize low-beam fog lights, expand following distances, and minimize unnecessary travel."
                )
            )
        }

        // 8. High UV Index
        if (details.uvIndex >= 8) {
            alerts.add(
                WeatherAlert(
                    type = AlertType.HIGH_UV,
                    title = "Extreme UV Hazard",
                    description = "An elevated UV index of ${details.uvIndex} will trigger rapid, unprotected skin damage and sunburn.",
                    severity = AlertSeverity.WARNING,
                    recommendation = "Apply high-factor SPF 50+ sunscreen, wear protective sun apparel, and seek shade between 11 AM and 3 PM."
                )
            )
        }

        // 9. Poor Air Quality
        if (details.airQuality.aqi >= 4) {
            alerts.add(
                WeatherAlert(
                    type = AlertType.POOR_AQI,
                    title = "Unhealthy Air Quality Warning",
                    description = "Local atmospheric pollution indexes indicate hazardous particulate matter. dominant pollutant: ${details.airQuality.dominantPollutant}.",
                    severity = AlertSeverity.CRITICAL,
                    recommendation = "Avoid prolonged outdoor workouts, wear standard protective face masks, and utilize indoor air filtrations."
                )
            )
        }

        return alerts
    }

    /**
     * Generates a lifestyle index scorecard mapping current variables to activity scores.
     */
    fun processLifestyleRecommendations(details: WeatherDetails, isCelsius: Boolean): List<LifestyleRecommendation> {
        val tempC = if (isCelsius) details.currentTemp.toDouble() else ((details.currentTemp - 32) * 5) / 9.0
        val windKmh = details.windSpeed
        val condition = details.condition
        val humidity = details.humidity
        val aqi = details.airQuality.aqi

        // 1. Running Score
        val runningScore = run {
            var s = 100
            s -= (tempC - 14.0).absoluteValue.toInt() * 3
            if (condition == WeatherCondition.RAINY) s -= 35
            if (condition == WeatherCondition.STORM) s -= 75
            if (condition == WeatherCondition.SNOWY) s -= 45
            s -= (aqi - 1) * 20
            s -= (windKmh / 5).toInt() * 3
            s.coerceIn(0, 100)
        }

        // 2. Cycling Score
        val cyclingScore = run {
            var s = 100
            s -= (tempC - 19.0).absoluteValue.toInt() * 2
            s -= (windKmh / 3).toInt() * 4
            if (condition == WeatherCondition.RAINY) s -= 40
            if (condition == WeatherCondition.STORM) s -= 80
            s -= (aqi - 1) * 15
            s.coerceIn(0, 100)
        }

        // 3. Hiking Score
        val hikingScore = run {
            var s = 100
            s -= (tempC - 15.0).absoluteValue.toInt() * 2
            if (condition == WeatherCondition.RAINY) s -= 45
            if (condition == WeatherCondition.STORM) s -= 90
            if (details.visibilityKm < 5.0) s -= 30
            if (details.visibilityKm < 2.0) s -= 40
            s.coerceIn(0, 100)
        }

        // 4. Beach Score
        val beachScore = run {
            var s = 0
            if (condition == WeatherCondition.SUNNY) s += 60
            if (condition == WeatherCondition.PARTLY_CLOUDY) s += 40
            if (tempC >= 25.0) s += 30
            else if (tempC >= 20.0) s += 15
            s -= (windKmh / 5).toInt() * 3
            if (condition == WeatherCondition.RAINY || condition == WeatherCondition.STORM) s = 0
            s.coerceIn(0, 100)
        }

        // 5. Gardening Score
        val gardeningScore = run {
            var s = 80
            s -= (tempC - 18.0).absoluteValue.toInt() * 2
            if (humidity < 30) s -= 15
            if (humidity > 85) s -= 10
            if (condition == WeatherCondition.STORM) s -= 60
            if (condition == WeatherCondition.SUNNY) s += 10
            s.coerceIn(0, 100)
        }

        // 6. Photography Conditions
        val photoScore = run {
            var s = 50
            if (condition == WeatherCondition.PARTLY_CLOUDY) s += 40 // beautiful dramatic clouds
            if (condition == WeatherCondition.SUNNY) s += 25
            if (details.visibilityKm > 10.0) s += 15
            if (details.visibilityKm < 3.0) s -= 30
            if (condition == WeatherCondition.STORM) s -= 40
            s.coerceIn(0, 100)
        }

        // 7. Fishing Conditions
        val fishingScore = run {
            var s = 70
            // Fish bite more under overcast conditions or mild winds
            if (condition == WeatherCondition.CLOUDY || condition == WeatherCondition.PARTLY_CLOUDY) s += 15
            if (windKmh in 8.0..18.0) s += 10
            if (tempC in 12.0..22.0) s += 5
            if (condition == WeatherCondition.STORM) s -= 50
            s.coerceIn(0, 100)
        }

        fun getStatus(score: Int): String = when {
            score >= 85 -> "Excellent"
            score >= 70 -> "Good"
            score >= 50 -> "Fair"
            else -> "Poor"
        }

        return listOf(
            LifestyleRecommendation(
                name = "Running",
                score = runningScore,
                status = getStatus(runningScore),
                description = if (runningScore >= 70) "Excellent day for jogging! Enjoy clear paths and low thermal strain." else "Not ideal for running due to sub-optimal atmospheric factors.",
                iconName = "DirectionsRun"
            ),
            LifestyleRecommendation(
                name = "Cycling",
                score = cyclingScore,
                status = getStatus(cyclingScore),
                description = if (cyclingScore >= 70) "Low headwinds and highly comfortable temperatures for cycling routes." else "Moderate headwinds or moisture may impede cycling dynamics.",
                iconName = "DirectionsBike"
            ),
            LifestyleRecommendation(
                name = "Hiking",
                score = hikingScore,
                status = getStatus(hikingScore),
                description = if (hikingScore >= 70) "Clear visual ranges and dry ground make hiking trails highly inviting." else "Reduced visibility or trail slipperiness makes hiking risky.",
                iconName = "Terrain"
            ),
            LifestyleRecommendation(
                name = "Beach & Pool",
                score = beachScore,
                status = getStatus(beachScore),
                description = if (beachScore >= 70) "Warm radiant sunshine. Splendid conditions for beach lounging." else "Too cool or overcast for optimal beach outings.",
                iconName = "BeachAccess"
            ),
            LifestyleRecommendation(
                name = "Gardening",
                score = gardeningScore,
                status = getStatus(gardeningScore),
                description = if (gardeningScore >= 70) "Perfect soil hydration and ambient warmth to check on your plants." else "Harsh weather might cause leaf or soil distress.",
                iconName = "Grass"
            ),
            LifestyleRecommendation(
                name = "Photography",
                score = photoScore,
                status = getStatus(photoScore),
                description = if (photoScore >= 70) "Stunning cloud contrast and clean high-visibility horizon light." else "Muted sky or high light scattering reduces dynamic range.",
                iconName = "CameraAlt"
            ),
            LifestyleRecommendation(
                name = "Fishing",
                score = fishingScore,
                status = getStatus(fishingScore),
                description = if (fishingScore >= 70) "Favorable barometric transitions and breezes. Excellent fish activity." else "Atmospheric instability is suppressing aquatic feeding.",
                iconName = "SetMeal"
            )
        )
    }

    /**
     * Compiles detailed health insights regarding pollen, UV, hydration, and AQI.
     */
    fun processHealthInsights(details: WeatherDetails, isCelsius: Boolean): List<HealthInsight> {
        val tempC = if (isCelsius) details.currentTemp.toDouble() else ((details.currentTemp - 32) * 5) / 9.0
        val list = mutableListOf<HealthInsight>()

        // 1. Pollen Information (calculated dynamically when not explicitly in the API)
        val pollenVal = when {
            details.condition == WeatherCondition.RAINY -> "Low (Pollen Washed)"
            tempC in 15.0..28.0 && details.windSpeed > 15.0 -> "High (Aero-Allergen Bloom)"
            tempC in 10.0..30.0 -> "Moderate"
            else -> "Low"
        }
        val pollenStatus = when (pollenVal) {
            "High (Aero-Allergen Bloom)" -> "Elevated Allergen Alert"
            "Moderate" -> "Moderate Pollen"
            else -> "Clean Air Dome"
        }
        val pollenRec = when (pollenVal) {
            "High (Aero-Allergen Bloom)" -> "Sensitive allergy profiles should wear eyewear and rinse face post-commute."
            "Moderate" -> "Standard conditions, minor allergen triggers are possible."
            else -> "Ideal breathing air, extremely low grass/tree pollen present."
        }
        list.add(
            HealthInsight(
                title = "Allergen & Pollen",
                value = pollenVal,
                status = pollenStatus,
                recommendation = pollenRec,
                iconName = "Spa",
                severity = if (pollenVal.contains("High")) AlertSeverity.WARNING else AlertSeverity.INFO
            )
        )

        // 2. UV Protection Advice
        val uvVal = "UV Index: ${details.uvIndex}"
        val uvStatus = when {
            details.uvIndex <= 2 -> "Low Risk"
            details.uvIndex in 3..5 -> "Moderate Risk"
            details.uvIndex in 6..7 -> "High Risk"
            else -> "Extreme Risk"
        }
        val uvRec = when {
            details.uvIndex <= 2 -> "No special skin precautions are necessary for brief exposures."
            details.uvIndex in 3..5 -> "Apply standard SPF 15+ sunscreen and wear sunglasses under direct midday sun."
            details.uvIndex in 6..7 -> "Apply broad-spectrum SPF 30+ every 2 hours, wear wide hats, seek shade."
            else -> "Avoid peak sun, apply heavy-duty SPF 50+ generously, cover sensitive skin."
        }
        list.add(
            HealthInsight(
                title = "UV Radiation Shielding",
                value = uvVal,
                status = uvStatus,
                recommendation = uvRec,
                iconName = "WbSunny",
                severity = if (details.uvIndex >= 6) AlertSeverity.WARNING else AlertSeverity.INFO
            )
        )

        // 3. Hydration Reminders
        val hydrationVal = if (tempC >= 30.0) "Critical (Accelerated Sweating)" else if (tempC >= 22.0) "Recommended" else "Standard"
        val hydrationStatus = if (tempC >= 30.0) "Dehydration Hazard" else "Normal Hydration"
        val hydrationRec = when {
            tempC >= 30.0 -> "Ambient heat requires drinking at least 250-350ml of electrolyte-rich fluids every 30 minutes."
            tempC >= 22.0 -> "Drink water regularly (approx. 2 liters throughout the day) to sustain physical energy."
            else -> "Standard metabolic fluid requirements. Maintain a baseline intake."
        }
        list.add(
            HealthInsight(
                title = "Metabolic Hydration",
                value = hydrationVal,
                status = hydrationStatus,
                recommendation = hydrationRec,
                iconName = "LocalDrink",
                severity = if (tempC >= 30.0) AlertSeverity.CRITICAL else AlertSeverity.INFO
            )
        )

        // 4. Air Quality Health Recommendations
        val aqi = details.airQuality.aqi
        val aqiVal = "AQI Level: $aqi (${details.airQuality.level})"
        val aqiRec = when (aqi) {
            1 -> "Pristine air. Ideal for yoga, respiratory therapy, and deep-breathing outdoor exercise."
            2 -> "Acceptable environment. Fully safe to enjoy standard daily activities outside."
            3 -> "Unhealthy for hyper-sensitive cardiopulmonary systems. Limit intensive outdoor workouts."
            else -> "Hazardous particulate matter present. Restrict outdoor sessions and keep windows sealed."
        }
        list.add(
            HealthInsight(
                title = "Respiratory Safety",
                value = aqiVal,
                status = details.airQuality.level,
                recommendation = aqiRec,
                iconName = "Air",
                severity = if (aqi >= 3) AlertSeverity.CRITICAL else AlertSeverity.INFO
            )
        )

        return list
    }

    /**
     * Interprets upcoming 24-hour weather trends into a natural-language description.
     */
    fun processWeatherTimelineSummary(details: WeatherDetails, isCelsius: Boolean): String {
        val hourly = details.hourlyForecast
        if (hourly.isEmpty()) return "Timeline data is unavailable."

        val currentTemp = details.currentTemp
        val minTemp = hourly.minOf { it.temperature }
        val maxTemp = hourly.maxOf { it.temperature }
        val rainChanceHours = hourly.filter { it.precipitationChance > 40 }
        
        val unit = if (isCelsius) "°C" else "°F"
        val sb = StringBuilder()
        
        sb.append("Over the next 24 hours, temperatures will range from a low of $minTemp$unit to a high of $maxTemp$unit. ")
        
        if (rainChanceHours.isNotEmpty()) {
            val earliestRain = rainChanceHours.first()
            sb.append("Note that unstable conditions with an elevated precipitation risk of ${earliestRain.precipitationChance}% are anticipated starting around ${earliestRain.time}. ")
        } else {
            sb.append("Conditions will remain dry and stable with negligible moisture interference. ")
        }

        // Describe general condition transitions
        val distinctConditions = hourly.map { it.condition }.distinct()
        if (distinctConditions.size == 1) {
            sb.append("An unchanging, unified trend of ${distinctConditions.first().displayName.lowercase()} skies is predicted throughout the cycle.")
        } else {
            val firstCond = hourly.first().condition
            val finalCond = hourly.last().condition
            sb.append("Expect skies to transition from ${firstCond.displayName.lowercase()} in the morning hours toward ${finalCond.displayName.lowercase()} periods by tomorrow.")
        }

        return sb.toString()
    }

    /**
     * Grades all available hours to recommend the absolute best windows for travel & outdoor activities.
     */
    fun processTravelPlanner(details: WeatherDetails, isCelsius: Boolean): List<TravelHourSlot> {
        val hourly = details.hourlyForecast
        if (hourly.isEmpty()) return emptyList()

        val list = hourly.map { hour ->
            val tempC = if (isCelsius) hour.temperature.toDouble() else ((hour.temperature - 32) * 5) / 9.0
            val precip = hour.precipitationChance
            
            // Score hour out of 100
            var score = 100
            // temperature penalty: ideal 15-23C
            score -= (tempC - 19.0).absoluteValue.toInt() * 3
            // precipitation penalty
            score -= (precip * 0.8).toInt()
            // condition penalty
            if (hour.condition == WeatherCondition.STORM) score -= 60
            if (hour.condition == WeatherCondition.RAINY) score -= 40
            if (hour.condition == WeatherCondition.SNOWY) score -= 30
            
            val finalScore = score.coerceIn(0, 100)
            val suitability = when {
                finalScore >= 80 -> "Ideal"
                finalScore >= 55 -> "Moderate"
                else -> "Not Recommended"
            }
            val tip = when {
                finalScore >= 80 -> "Perfect weather window for commuting, outdoor events, or highway travel."
                finalScore >= 55 -> "Acceptable conditions. Keep lightweight outerwear handy for thermal comfort."
                else -> "Unstable or harsh conditions. Delay non-essential travel or secure shelter."
            }

            TravelHourSlot(
                time = hour.time,
                tempFormatted = "${hour.temperature}°${if (isCelsius) "C" else "F"}",
                condition = hour.condition,
                score = finalScore,
                suitability = suitability,
                tip = tip
            )
        }

        // Sort by score descending to present the best hours first
        return list.sortedByDescending { it.score }
    }
}
