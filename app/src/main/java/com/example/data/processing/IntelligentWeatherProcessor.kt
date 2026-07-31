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
    val iconName: String // Material icon identifier
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

data class TravelIntelligenceData(
    val bestTravelTimeToday: String,
    val rainDelayWarning: String?,
    val fogWarning: String?,
    val windWarning: String?,
    val stormAlert: String?,
    val visibilityAdvice: String,
    val hourlySlots: List<TravelHourSlot>
)

data class ClothingItem(
    val name: String,
    val iconName: String,
    val reason: String
)

data class ClothingAdvisorData(
    val summary: String,
    val items: List<ClothingItem>
)

data class SmartWeatherScores(
    val outdoorScore: Int,
    val runningScore: Int,
    val cyclingScore: Int,
    val travelScore: Int,
    val comfortScore: Int,
    val airQualityScore: Int
)

data class PrimaryAiInsight(
    val insightText: String,
    val category: String,
    val iconName: String
)

object IntelligentWeatherProcessor {

    /**
     * Requirement 1: Smart Weather Summary
     * Generates a short natural-language summary using current weather data locally.
     */
    fun processSmartWeatherSummary(details: WeatherDetails, isCelsius: Boolean, cityName: String = ""): String {
        val tempC = if (isCelsius) details.currentTemp.toDouble() else ((details.currentTemp - 32) * 5) / 9.0
        val tempUnit = "${details.currentTemp}°${if (isCelsius) "C" else "F"}"
        val condition = details.condition
        val maxPrecip = details.hourlyForecast.take(6).maxOfOrNull { it.precipitationChance } ?: 0

        val cityPrefix = if (cityName.isNotBlank()) "in $cityName " else ""

        return when {
            condition == WeatherCondition.STORM -> {
                "Thunderstorms are active or expected $cityPrefix. Carry an umbrella and avoid unnecessary travel during heavy rain and lightning."
            }
            condition == WeatherCondition.RAINY || maxPrecip >= 60 -> {
                "Rainy conditions detected $cityPrefix with up to $maxPrecip% chance of precipitation. Carry an umbrella and wear a waterproof jacket."
            }
            condition == WeatherCondition.SNOWY -> {
                "Freezing temperatures and active snowfall $cityPrefix. Wear insulated winter layers and watch for icy roads."
            }
            tempC >= 33.0 -> {
                "High temperatures of $tempUnit $cityPrefix creating intense heat strain. Stay hydrated, wear light clothing, and seek shade during midday."
            }
            tempC <= 2.0 -> {
                "Very cold weather at $tempUnit $cityPrefix. Bundle up with a heavy coat, gloves, and beanie before stepping out."
            }
            details.windSpeed > 30.0 -> {
                "Strong wind gusts up to ${details.windSpeed.toInt()} km/h $cityPrefix. Secure loose items and hold on to your umbrella."
            }
            details.uvIndex >= 7 -> {
                "Clear sunny skies at $tempUnit $cityPrefix with high UV exposure. Apply SPF 30+ sunscreen and wear sunglasses if spending time outdoors."
            }
            else -> {
                "Pleasant $tempUnit weather $cityPrefix with ${condition.displayName.lowercase()} skies. Excellent conditions for walking, outdoor sports, and daily travel."
            }
        }
    }

    /**
     * Requirement 7: Intelligent Alerts
     * Identifies active high-value alerts based on exact weather thresholds.
     * Only displays alerts when needed (no fake warnings).
     */
    fun processSmartAlerts(details: WeatherDetails, isCelsius: Boolean): List<WeatherAlert> {
        val alerts = mutableListOf<WeatherAlert>()
        val tempC = if (isCelsius) details.currentTemp.toDouble() else ((details.currentTemp - 32) * 5) / 9.0
        val windKmh = details.windSpeed

        // 1. Heavy Rain
        val maxPrecipChance = details.hourlyForecast.take(6).maxOfOrNull { it.precipitationChance } ?: 0
        if (details.condition == WeatherCondition.RAINY || maxPrecipChance >= 75) {
            alerts.add(
                WeatherAlert(
                    type = AlertType.HEAVY_RAIN,
                    title = "Heavy Rain Alert",
                    description = "Heavy rainfall detected or forecasted with up to $maxPrecipChance% probability.",
                    severity = AlertSeverity.WARNING,
                    recommendation = "Carry a sturdy umbrella and wear slip-resistant waterproof footwear."
                )
            )
        }

        // 2. Thunderstorm
        if (details.condition == WeatherCondition.STORM) {
            alerts.add(
                WeatherAlert(
                    type = AlertType.THUNDERSTORM,
                    title = "Thunderstorm Warning",
                    description = "Dangerous atmospheric instability and lightning storms are active.",
                    severity = AlertSeverity.CRITICAL,
                    recommendation = "Seek immediate indoor shelter and stay away from open tall structures."
                )
            )
        }

        // 3. Strong Wind
        if (windKmh > 35.0) {
            alerts.add(
                WeatherAlert(
                    type = AlertType.STRONG_WIND,
                    title = "Strong Wind Advisory",
                    description = "High wind gusts averaging ${windKmh.toInt()} km/h detected.",
                    severity = AlertSeverity.WARNING,
                    recommendation = "Secure outdoor belongings and exercise caution while driving."
                )
            )
        }

        // 4. Heatwave (Extreme Heat)
        if (tempC >= 34.0) {
            alerts.add(
                WeatherAlert(
                    type = AlertType.EXTREME_HEAT,
                    title = "Heatwave Warning",
                    description = "Elevated thermal stress with temperatures reaching ${details.currentTemp}°${if (isCelsius) "C" else "F"}.",
                    severity = AlertSeverity.CRITICAL,
                    recommendation = "Stay in air-conditioned spaces, avoid midday sun, and drink extra water."
                )
            )
        }

        // 5. Cold Wave (Extreme Cold)
        if (tempC <= 0.0) {
            alerts.add(
                WeatherAlert(
                    type = AlertType.EXTREME_COLD,
                    title = "Cold Wave Warning",
                    description = "Sub-zero temperatures of ${details.currentTemp}°${if (isCelsius) "C" else "F"} pose frostbite risks.",
                    severity = AlertSeverity.CRITICAL,
                    recommendation = "Wear thick thermal layers, protect exposed skin, and watch for icy patches."
                )
            )
        }

        // 6. Snow
        if (details.condition == WeatherCondition.SNOWY) {
            alerts.add(
                WeatherAlert(
                    type = AlertType.SNOW,
                    title = "Snowfall Warning",
                    description = "Winter snowfall is causing slick roads and reduced visibility.",
                    severity = AlertSeverity.WARNING,
                    recommendation = "Drive carefully at reduced speeds and wear insulated boots."
                )
            )
        }

        // 7. Dense Fog
        if (details.visibilityKm < 1.5) {
            alerts.add(
                WeatherAlert(
                    type = AlertType.DENSE_FOG,
                    title = "Dense Fog Warning",
                    description = "Horizontal visibility is reduced to ${details.visibilityKm} km.",
                    severity = AlertSeverity.CRITICAL,
                    recommendation = "Use low-beam fog lights and maintain safe braking distance."
                )
            )
        }

        // 8. High UV
        if (details.uvIndex >= 8) {
            alerts.add(
                WeatherAlert(
                    type = AlertType.HIGH_UV,
                    title = "High UV Exposure Alert",
                    description = "UV index of ${details.uvIndex} will cause fast skin damage without protection.",
                    severity = AlertSeverity.WARNING,
                    recommendation = "Apply SPF 50+ sunscreen, wear wide-brimmed hats, and seek shade."
                )
            )
        }

        // 9. Poor Air Quality
        if (details.airQuality.aqi >= 4) {
            alerts.add(
                WeatherAlert(
                    type = AlertType.POOR_AQI,
                    title = "Poor Air Quality Alert",
                    description = "High particulate pollution index (AQI ${details.airQuality.aqi}).",
                    severity = AlertSeverity.CRITICAL,
                    recommendation = "Limit outdoor strenuous activity and wear a protective mask."
                )
            )
        }

        return alerts
    }

    /**
     * Requirement 2: Lifestyle Intelligence
     * Provides dynamic recommendations for ALL 12 required activities based on real weather.
     */
    fun processLifestyleRecommendations(details: WeatherDetails, isCelsius: Boolean): List<LifestyleRecommendation> {
        val tempC = if (isCelsius) details.currentTemp.toDouble() else ((details.currentTemp - 32) * 5) / 9.0
        val windKmh = details.windSpeed
        val condition = details.condition
        val humidity = details.humidity
        val aqi = details.airQuality.aqi

        fun getStatus(score: Int): String = when {
            score >= 85 -> "Excellent"
            score >= 70 -> "Good"
            score >= 50 -> "Fair"
            else -> "Poor"
        }

        // 1. Walking
        val walkingScore = run {
            var s = 100
            s -= (tempC - 19.0).absoluteValue.toInt() * 2
            if (condition == WeatherCondition.RAINY) s -= 35
            if (condition == WeatherCondition.STORM) s -= 75
            if (condition == WeatherCondition.SNOWY) s -= 30
            s -= (aqi - 1) * 12
            s -= (windKmh / 6).toInt() * 3
            s.coerceIn(0, 100)
        }

        // 2. Running
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

        // 3. Cycling
        val cyclingScore = run {
            var s = 100
            s -= (tempC - 19.0).absoluteValue.toInt() * 2
            s -= (windKmh / 3).toInt() * 4
            if (condition == WeatherCondition.RAINY) s -= 40
            if (condition == WeatherCondition.STORM) s -= 80
            s -= (aqi - 1) * 15
            s.coerceIn(0, 100)
        }

        // 4. Hiking
        val hikingScore = run {
            var s = 100
            s -= (tempC - 15.0).absoluteValue.toInt() * 2
            if (condition == WeatherCondition.RAINY) s -= 45
            if (condition == WeatherCondition.STORM) s -= 90
            if (details.visibilityKm < 5.0) s -= 30
            s.coerceIn(0, 100)
        }

        // 5. Outdoor Sports
        val outdoorSportsScore = run {
            var s = 100
            s -= (tempC - 20.0).absoluteValue.toInt() * 2
            s -= (windKmh / 4).toInt() * 5
            if (condition == WeatherCondition.RAINY) s -= 50
            if (condition == WeatherCondition.STORM) s -= 85
            s -= (aqi - 1) * 15
            s.coerceIn(0, 100)
        }

        // 6. Photography
        val photoScore = run {
            var s = 50
            if (condition == WeatherCondition.PARTLY_CLOUDY) s += 40
            if (condition == WeatherCondition.SUNNY) s += 25
            if (details.visibilityKm > 10.0) s += 15
            if (details.visibilityKm < 3.0) s -= 30
            if (condition == WeatherCondition.STORM) s -= 40
            s.coerceIn(0, 100)
        }

        // 7. Gardening
        val gardeningScore = run {
            var s = 80
            s -= (tempC - 18.0).absoluteValue.toInt() * 2
            if (humidity < 30) s -= 15
            if (humidity > 85) s -= 10
            if (condition == WeatherCondition.STORM) s -= 60
            if (condition == WeatherCondition.SUNNY) s += 10
            s.coerceIn(0, 100)
        }

        // 8. Car Wash
        val carWashScore = run {
            var s = 90
            val rainNext2Days = details.dailyForecast.drop(1).take(2).any { it.precipitationChance > 35 }
            if (condition == WeatherCondition.RAINY || condition == WeatherCondition.STORM) s = 10
            else if (rainNext2Days) s = 30
            if (condition == WeatherCondition.SUNNY) s += 10
            s.coerceIn(0, 100)
        }

        // 9. Laundry Drying
        val laundryScore = run {
            var s = 70
            if (condition == WeatherCondition.SUNNY) s += 20
            if (condition == WeatherCondition.PARTLY_CLOUDY) s += 10
            if (windKmh in 10.0..25.0) s += 10
            if (humidity > 70) s -= 35
            if (condition == WeatherCondition.RAINY || condition == WeatherCondition.STORM) s = 5
            s.coerceIn(0, 100)
        }

        // 10. BBQ & Picnic
        val bbqScore = run {
            var s = 100
            s -= (tempC - 22.0).absoluteValue.toInt() * 3
            if (windKmh > 20.0) s -= 30
            if (condition == WeatherCondition.RAINY) s -= 60
            if (condition == WeatherCondition.STORM) s -= 90
            if (aqi >= 3) s -= 30
            s.coerceIn(0, 100)
        }

        // 11. Fishing
        val fishingScore = run {
            var s = 70
            if (condition == WeatherCondition.CLOUDY || condition == WeatherCondition.PARTLY_CLOUDY) s += 15
            if (windKmh in 8.0..18.0) s += 10
            if (tempC in 12.0..22.0) s += 5
            if (condition == WeatherCondition.STORM) s -= 50
            s.coerceIn(0, 100)
        }

        // 12. Camping
        val campingScore = run {
            var s = 100
            s -= (tempC - 18.0).absoluteValue.toInt() * 2
            if (condition == WeatherCondition.RAINY) s -= 50
            if (condition == WeatherCondition.STORM) s -= 90
            if (condition == WeatherCondition.SNOWY) s -= 40
            if (windKmh > 25.0) s -= 35
            s.coerceIn(0, 100)
        }

        return listOf(
            LifestyleRecommendation("Walking", walkingScore, getStatus(walkingScore), if (walkingScore >= 70) "Great conditions for a stroll in fresh air." else "Sub-optimal walking conditions.", "DirectionsWalk"),
            LifestyleRecommendation("Running", runningScore, getStatus(runningScore), if (runningScore >= 70) "Excellent thermal balance for jog workouts." else "High thermal or air strain for running.", "DirectionsRun"),
            LifestyleRecommendation("Cycling", cyclingScore, getStatus(cyclingScore), if (cyclingScore >= 70) "Low headwinds and clear roads for cycling." else "Wind resistance or moisture present.", "DirectionsBike"),
            LifestyleRecommendation("Hiking", hikingScore, getStatus(hikingScore), if (hikingScore >= 70) "Dry trails and crisp horizon views." else "Slippery paths or low visibility.", "Terrain"),
            LifestyleRecommendation("Outdoor Sports", outdoorSportsScore, getStatus(outdoorSportsScore), if (outdoorSportsScore >= 70) "Ideal weather for tennis, football, or basketball." else "Wind or rain impeding outdoor games.", "SportsSoccer"),
            LifestyleRecommendation("Photography", photoScore, getStatus(photoScore), if (photoScore >= 70) "Stunning sky dynamic range and cloud contrast." else "Flat sky or low visibility.", "CameraAlt"),
            LifestyleRecommendation("Gardening", gardeningScore, getStatus(gardeningScore), if (gardeningScore >= 70) "Favorable soil temperature and light." else "Risk of plant heat or frost stress.", "Grass"),
            LifestyleRecommendation("Car Wash", carWashScore, getStatus(carWashScore), if (carWashScore >= 70) "Dry weather ahead—perfect timing for a car wash." else "Showers forecasted; delay washing.", "DirectionsCar"),
            LifestyleRecommendation("Laundry Drying", laundryScore, getStatus(laundryScore), if (laundryScore >= 70) "Warm breeze allows fast outdoor line drying." else "High humidity or rain risk.", "Dry"),
            LifestyleRecommendation("BBQ & Picnic", bbqScore, getStatus(bbqScore), if (bbqScore >= 70) "Mild temperatures and calm wind for a picnic." else "Wind or rain makes outdoor dining uncomfortable.", "OutdoorGrill"),
            LifestyleRecommendation("Fishing", fishingScore, getStatus(fishingScore), if (fishingScore >= 70) "Overcast skies and light breeze favor fish biting." else "Turbulent weather reduces fish feeding.", "SetMeal"),
            LifestyleRecommendation("Camping", campingScore, getStatus(campingScore), if (campingScore >= 70) "Clear night ahead and comfortable sleep temps." else "Risk of rain, high wind, or cold ground.", "Cabin")
        )
    }

    /**
     * Requirement 3: Health Intelligence
     * Generates weather-based advice for all 8 required topics.
     */
    fun processHealthInsights(details: WeatherDetails, isCelsius: Boolean): List<HealthInsight> {
        val tempC = if (isCelsius) details.currentTemp.toDouble() else ((details.currentTemp - 32) * 5) / 9.0
        val humidity = details.humidity
        val windKmh = details.windSpeed
        val aqi = details.airQuality.aqi
        val uv = details.uvIndex

        val list = mutableListOf<HealthInsight>()

        // 1. UV Exposure
        val uvStatus = when {
            uv <= 2 -> "Low Exposure"
            uv in 3..5 -> "Moderate Risk"
            uv in 6..7 -> "High Risk"
            else -> "Extreme Risk"
        }
        val uvRec = when {
            uv <= 2 -> "Safe for extended outdoor time."
            uv in 3..5 -> "Wear sunglasses and apply SPF 15+ if out over 30 mins."
            uv in 6..7 -> "Apply SPF 30+ sunscreen, wear a hat, and seek shade midday."
            else -> "Avoid midday sun; apply SPF 50+ generously every 2 hours."
        }
        list.add(HealthInsight("UV Exposure", "Index: $uv", uvStatus, uvRec, "WbSunny", if (uv >= 6) AlertSeverity.WARNING else AlertSeverity.INFO))

        // 2. Heat Stress
        val heatIndexC = tempC + (0.555 * ((6.11 * Math.exp(5417.7530 * (1/273.16 - 1/(273.15 + tempC)))) * (humidity / 100.0) - 10))
        val heatStatus = when {
            heatIndexC >= 38 -> "Severe Heat Risk"
            heatIndexC >= 30 -> "Moderate Heat Stress"
            else -> "Thermal Comfort"
        }
        val heatRec = when {
            heatIndexC >= 38 -> "Danger of heat exhaustion. Stay in air-conditioned areas."
            heatIndexC >= 30 -> "Elevated heat strain. Take frequent breaks in shade."
            else -> "Body heat regulation operating normally."
        }
        list.add(HealthInsight("Heat Stress", "${tempC.toInt()}°C (${humidity}% RH)", heatStatus, heatRec, "Thermostat", if (heatIndexC >= 30) AlertSeverity.WARNING else AlertSeverity.INFO))

        // 3. Cold Stress
        val windChillC = 13.12 + (0.6215 * tempC) - (11.37 * Math.pow(windKmh.coerceAtLeast(1.0), 0.16)) + (0.3965 * tempC * Math.pow(windKmh.coerceAtLeast(1.0), 0.16))
        val coldStatus = when {
            windChillC <= 0 -> "High Cold Risk"
            windChillC <= 10 -> "Moderate Chilly"
            else -> "Mild Conditions"
        }
        val coldRec = when {
            windChillC <= 0 -> "Frostbite hazard on exposed skin. Wear thermal layers and gloves."
            windChillC <= 10 -> "Cool wind chill. Wear a jacket or wool sweater."
            else -> "No significant cold stress."
        }
        list.add(HealthInsight("Cold Stress", "Feels Like: ${windChillC.toInt()}°C", coldStatus, coldRec, "AcUnit", if (windChillC <= 0) AlertSeverity.WARNING else AlertSeverity.INFO))

        // 4. Air Quality
        val aqiStatus = details.airQuality.level
        val aqiRec = when (aqi) {
            1 -> "Pristine air quality. Perfect for deep breathing exercises."
            2 -> "Safe air quality for general outdoor activities."
            3 -> "Unhealthy for sensitive individuals. Limit heavy exertion."
            else -> "High particulate pollution. Wear N95 mask and close windows."
        }
        list.add(HealthInsight("Air Quality", "AQI Level $aqi", aqiStatus, aqiRec, "Air", if (aqi >= 3) AlertSeverity.WARNING else AlertSeverity.INFO))

        // 5. Allergy Risk
        val pollenVal = when {
            details.condition == WeatherCondition.RAINY -> "Low (Rain Washed)"
            tempC in 15.0..28.0 && windKmh > 15.0 -> "High (Aero-Pollen Bloom)"
            tempC in 10.0..30.0 -> "Moderate"
            else -> "Low"
        }
        val pollenRec = when {
            pollenVal.startsWith("High") -> "High tree/grass pollen count. Wear eyewear and rinse face post-commute."
            pollenVal.startsWith("Moderate") -> "Moderate pollen levels. Keep anti-allergy medication ready."
            else -> "Air is clean with minimal aero-allergens."
        }
        list.add(HealthInsight("Allergy Risk", pollenVal, if (pollenVal.startsWith("High")) "High Allergen Alert" else "Normal", pollenRec, "Spa", if (pollenVal.startsWith("High")) AlertSeverity.WARNING else AlertSeverity.INFO))

        // 6. Asthma Risk
        val asthmaTrigger = details.condition == WeatherCondition.STORM || aqi >= 3 || humidity > 85 || humidity < 25
        val asthmaStatus = if (asthmaTrigger) "Elevated Risk" else "Low Risk"
        val asthmaRec = if (asthmaTrigger) "Humidity extremes, AQI, or storm pressure may trigger asthma. Carry inhaler." else "Atmospheric stability is high. Low asthma trigger probability."
        list.add(HealthInsight("Asthma Risk", asthmaStatus, asthmaStatus, asthmaRec, "MedicalServices", if (asthmaTrigger) AlertSeverity.WARNING else AlertSeverity.INFO))

        // 7. Hydration Reminders
        val fluidNeeded = if (tempC >= 28.0 || humidity > 70) "Elevated (3.0L / day)" else "Standard (2.0L / day)"
        val hydRec = if (tempC >= 28.0) "High temperature accelerates fluid loss. Drink 250ml water every 45 mins." else "Maintain steady baseline hydration throughout the day."
        list.add(HealthInsight("Hydration Reminders", fluidNeeded, if (tempC >= 28.0) "High Fluid Need" else "Normal", hydRec, "LocalDrink", AlertSeverity.INFO))

        // 8. Sleep Comfort
        val nightTempC = tempC - 4.0
        val sleepQuality = if (nightTempC in 16.0..21.0 && humidity in 35..65) "Optimal Sleep Window" else "Sub-Optimal Sleep Temp"
        val sleepRec = if (nightTempC in 16.0..21.0) "Overnight thermal conditions ideal for deep REM sleep." else "Adjust room ventilation or AC to maintain ~19°C overnight."
        list.add(HealthInsight("Sleep Comfort", sleepQuality, sleepQuality, sleepRec, "Bedtime", AlertSeverity.INFO))

        return list
    }

    /**
     * Requirement 4: Travel Intelligence
     */
    fun processTravelIntelligence(details: WeatherDetails, isCelsius: Boolean): TravelIntelligenceData {
        val hourlySlots = processTravelPlanner(details, isCelsius)
        val bestSlot = hourlySlots.maxByOrNull { it.score }

        val bestTravelTimeToday = if (bestSlot != null && bestSlot.score >= 70) {
            "Optimal Travel Window: ${bestSlot.time} (${bestSlot.tempFormatted}, Score ${bestSlot.score}/100)"
        } else {
            "Travel Window: Standard conditions throughout the day"
        }

        val rainDelayWarning = if (details.condition == WeatherCondition.RAINY || details.hourlyForecast.take(6).any { it.precipitationChance > 60 }) {
            "Rain Delay Warning: Expect wet pavement and localized transit delays."
        } else null

        val fogWarning = if (details.visibilityKm < 2.0 || details.condition == WeatherCondition.FOGGY) {
            "Dense Fog Warning: Horizontal visibility is low (${details.visibilityKm} km). Drive slowly."
        } else null

        val windWarning = if (details.windSpeed > 30.0) {
            "Wind Warning: Crosswinds of ${details.windSpeed.toInt()} km/h may affect high-profile vehicles."
        } else null

        val stormAlert = if (details.condition == WeatherCondition.STORM) {
            "Storm Alert: Severe lightning and thunder. Delay non-essential road travel."
        } else null

        val visibilityAdvice = when {
            details.visibilityKm >= 10.0 -> "Clear visibility (${details.visibilityKm} km). Road conditions optimal."
            details.visibilityKm >= 5.0 -> "Fair visibility (${details.visibilityKm} km). Standard driving caution."
            else -> "Reduced visibility (${details.visibilityKm} km). Use fog headlights and increase distance."
        }

        return TravelIntelligenceData(
            bestTravelTimeToday = bestTravelTimeToday,
            rainDelayWarning = rainDelayWarning,
            fogWarning = fogWarning,
            windWarning = windWarning,
            stormAlert = stormAlert,
            visibilityAdvice = visibilityAdvice,
            hourlySlots = hourlySlots
        )
    }

    /**
     * Requirement 5: Clothing Advisor
     */
    fun processClothingAdvice(details: WeatherDetails, isCelsius: Boolean): ClothingAdvisorData {
        val tempC = if (isCelsius) details.currentTemp.toDouble() else ((details.currentTemp - 32) * 5) / 9.0
        val items = mutableListOf<ClothingItem>()
        val precipChance = details.hourlyForecast.take(6).maxOfOrNull { it.precipitationChance } ?: 0

        val tempSummary = when {
            tempC < 5.0 -> "Freezing Weather"
            tempC in 5.0..14.0 -> "Chilly Weather"
            tempC in 15.0..22.0 -> "Mild & Comfortable"
            tempC in 23.0..30.0 -> "Warm Weather"
            else -> "Hot Weather"
        }

        // Base outfit recommendation
        when {
            tempC < 5.0 -> {
                items.add(ClothingItem("Heavy Coat", "Check", "Insulation against freezing temperatures."))
                items.add(ClothingItem("Sweater", "Check", "Warm inner layering."))
                items.add(ClothingItem("Beanie & Gloves", "Check", "Protection for hands and ears."))
            }
            tempC in 5.0..14.0 -> {
                items.add(ClothingItem("Light Jacket", "Check", "Keeps off cool breezes."))
                items.add(ClothingItem("Sweater", "Check", "Comfortable layering."))
            }
            tempC in 15.0..22.0 -> {
                items.add(ClothingItem("T-shirt", "Check", "Breathable base layer."))
                items.add(ClothingItem("Light Jacket", "Check", "Good for evening temperature drop."))
            }
            else -> {
                items.add(ClothingItem("T-shirt", "Check", "Lightweight cotton apparel."))
                items.add(ClothingItem("Shorts / Linen Pants", "Check", "Breathable summer wear."))
            }
        }

        // Weather accessories
        if (details.condition == WeatherCondition.RAINY || precipChance >= 40) {
            items.add(ClothingItem("Umbrella", "Umbrella", "Precipitation expected today."))
            items.add(ClothingItem("Raincoat", "Check", "Waterproof outerwear."))
        }
        if (details.uvIndex >= 5 || details.condition == WeatherCondition.SUNNY) {
            items.add(ClothingItem("Sunglasses", "WbSunny", "Eye protection against UV rays."))
            items.add(ClothingItem("Hat", "Check", "Sun protection for head and face."))
        }

        return ClothingAdvisorData(
            summary = "$tempSummary outfit recommended.",
            items = items
        )
    }

    /**
     * Requirement 6: Smart Weather Score
     * Generates daily scores from 0-100.
     */
    fun processSmartWeatherScores(details: WeatherDetails, isCelsius: Boolean): SmartWeatherScores {
        val tempC = if (isCelsius) details.currentTemp.toDouble() else ((details.currentTemp - 32) * 5) / 9.0
        val wind = details.windSpeed
        val precip = details.hourlyForecast.take(6).maxOfOrNull { it.precipitationChance } ?: 0
        val aqi = details.airQuality.aqi

        // Running score
        val runningScore = run {
            var s = 100
            s -= (tempC - 14.0).absoluteValue.toInt() * 3
            if (details.condition == WeatherCondition.RAINY) s -= 35
            if (details.condition == WeatherCondition.STORM) s -= 75
            s -= (aqi - 1) * 20
            s.coerceIn(0, 100)
        }

        // Cycling score
        val cyclingScore = run {
            var s = 100
            s -= (tempC - 19.0).absoluteValue.toInt() * 2
            s -= (wind / 3).toInt() * 4
            if (details.condition == WeatherCondition.RAINY) s -= 40
            s.coerceIn(0, 100)
        }

        // Air quality score
        val airQualityScore = when (aqi) {
            1 -> 100
            2 -> 80
            3 -> 60
            4 -> 40
            else -> 20
        }

        // Comfort score
        val comfortScore = run {
            var s = 100
            s -= (tempC - 21.0).absoluteValue.toInt() * 3
            if (details.humidity > 75) s -= 15
            if (details.humidity < 25) s -= 15
            s.coerceIn(0, 100)
        }

        // Travel score
        val travelScore = run {
            var s = 100
            s -= (precip * 0.6).toInt()
            if (details.visibilityKm < 5.0) s -= 25
            if (details.condition == WeatherCondition.STORM) s -= 50
            s.coerceIn(0, 100)
        }

        // Outdoor score (average of key outdoor activities)
        val outdoorScore = ((runningScore + cyclingScore + comfortScore + travelScore) / 4).coerceIn(0, 100)

        return SmartWeatherScores(
            outdoorScore = outdoorScore,
            runningScore = runningScore,
            cyclingScore = cyclingScore,
            travelScore = travelScore,
            comfortScore = comfortScore,
            airQualityScore = airQualityScore
        )
    }

    /**
     * Requirement 8: AI Insight Card
     * Returns one primary useful recommendation based on current weather.
     */
    fun processPrimaryAiInsight(details: WeatherDetails, isCelsius: Boolean): PrimaryAiInsight {
        val tempC = if (isCelsius) details.currentTemp.toDouble() else ((details.currentTemp - 32) * 5) / 9.0
        val maxPrecip = details.hourlyForecast.take(6).maxOfOrNull { it.precipitationChance } ?: 0

        return when {
            details.condition == WeatherCondition.STORM -> {
                PrimaryAiInsight(
                    insightText = "Storm expected within two hours. Seek indoor shelter immediately.",
                    category = "STORM ALERT",
                    iconName = "Warning"
                )
            }
            details.condition == WeatherCondition.RAINY || maxPrecip >= 65 -> {
                PrimaryAiInsight(
                    insightText = "Precipitation probability is high today. Keep an umbrella handy.",
                    category = "RAIN WARNING",
                    iconName = "Umbrella"
                )
            }
            details.uvIndex >= 7 -> {
                PrimaryAiInsight(
                    insightText = "High UV expected after noon. Apply SPF 30+ sunscreen and wear a hat.",
                    category = "UV ALERT",
                    iconName = "WbSunny"
                )
            }
            details.airQuality.aqi == 1 -> {
                PrimaryAiInsight(
                    insightText = "Air quality is excellent today. Ideal for outdoor breathing and exercise.",
                    category = "AIR QUALITY",
                    iconName = "Air"
                )
            }
            tempC in 16.0..24.0 && details.windSpeed < 15.0 && details.condition == WeatherCondition.SUNNY -> {
                PrimaryAiInsight(
                    insightText = "Excellent evening for outdoor exercise and recreation.",
                    category = "LIFESTYLE",
                    iconName = "DirectionsRun"
                )
            }
            tempC >= 32.0 -> {
                PrimaryAiInsight(
                    insightText = "High thermal heat load today. Maintain high fluid intake.",
                    category = "HEALTH",
                    iconName = "Thermostat"
                )
            }
            else -> {
                PrimaryAiInsight(
                    insightText = "Stable meteorological conditions predicted throughout the day.",
                    category = "AI INSIGHT",
                    iconName = "AutoAwesome"
                )
            }
        }
    }

    /**
     * Interprets upcoming 24-hour weather trends into a natural-language description.
     */
    fun processWeatherTimelineSummary(details: WeatherDetails, isCelsius: Boolean): String {
        val hourly = details.hourlyForecast
        if (hourly.isEmpty()) return "Timeline data is unavailable."

        val minTemp = hourly.minOf { it.temperature }
        val maxTemp = hourly.maxOf { it.temperature }
        val rainChanceHours = hourly.filter { it.precipitationChance > 40 }
        val unit = if (isCelsius) "°C" else "°F"

        val sb = StringBuilder()
        sb.append("Over the next 24 hours, temperatures will range from $minTemp$unit to $maxTemp$unit. ")
        
        if (rainChanceHours.isNotEmpty()) {
            val earliestRain = rainChanceHours.first()
            sb.append("Elevated precipitation risk (${earliestRain.precipitationChance}%) expected around ${earliestRain.time}. ")
        } else {
            sb.append("Conditions will remain dry and clear. ")
        }

        return sb.toString()
    }

    /**
     * Grades all available hours to recommend best travel windows.
     */
    fun processTravelPlanner(details: WeatherDetails, isCelsius: Boolean): List<TravelHourSlot> {
        val hourly = details.hourlyForecast
        if (hourly.isEmpty()) return emptyList()

        return hourly.map { hour ->
            val tempC = if (isCelsius) hour.temperature.toDouble() else ((hour.temperature - 32) * 5) / 9.0
            var score = 100
            score -= (tempC - 19.0).absoluteValue.toInt() * 3
            score -= (hour.precipitationChance * 0.8).toInt()
            if (hour.condition == WeatherCondition.STORM) score -= 60
            if (hour.condition == WeatherCondition.RAINY) score -= 40

            val finalScore = score.coerceIn(0, 100)
            val suitability = when {
                finalScore >= 80 -> "Ideal"
                finalScore >= 55 -> "Moderate"
                else -> "Not Recommended"
            }
            val tip = when {
                finalScore >= 80 -> "Perfect travel window for commuting or outdoor activities."
                finalScore >= 55 -> "Acceptable conditions; keep lightweight outerwear handy."
                else -> "Unstable weather. Consider postponing non-essential travel."
            }

            TravelHourSlot(
                time = hour.time,
                tempFormatted = "${hour.temperature}°${if (isCelsius) "C" else "F"}",
                condition = hour.condition,
                score = finalScore,
                suitability = suitability,
                tip = tip
            )
        }.sortedByDescending { it.score }
    }
}
