package com.example.ui.screens.home

import kotlin.math.absoluteValue
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.example.ui.icons.SkySphereIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.AiAssistantService
import com.example.data.models.CityWeather
import com.example.data.models.WeatherCondition
import com.example.data.models.WeatherDetails
import com.example.data.processing.*
import com.example.ui.components.SkySphereButton
import com.example.ui.components.SkySphereCard
import com.example.ui.components.SkySphereIconButton
import com.example.ui.components.SkySphereLoadingAnimation
import com.example.ui.components.WeatherConditionIcon
import com.example.ui.theme.LuxuryCyan
import com.example.ui.theme.LuxurySkyBlue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntelligentHub(
    cityWeather: CityWeather,
    allCities: List<CityWeather>,
    isCelsius: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val details = cityWeather.weatherDetails
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // AI Assistant State
    var aiSummaryState by remember { mutableStateOf<String?>(null) }
    var isGeneratingSummary by remember { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }
    var chatOutput by remember { mutableStateOf<String?>(null) }
    var isGeneratingChat by remember { mutableStateOf(false) }

    // Predefined AI Questions
    val aiQuestions = listOf(
        "Should I carry an umbrella?",
        "Is today good for running?",
        "Is it safe to travel?",
        "What should I wear today?",
        "Is it a good day for outdoor activities?"
    )

    // Weather Comparison State
    var comparisonCity by remember { mutableStateOf<CityWeather?>(null) }
    var showCitySelector by remember { mutableStateOf(false) }

    // Adaptive contrast styling variables for high accessibility
    val textColor = Color.White
    val subCardBg = Color(0xFF1E1E2E)
    val subCardBgSubtle = Color(0xFF242435)
    val subCardBorder = Color(0xFF374151)
    val subCardBorderSubtle = Color(0xFF2D3748)
    val dividerColor = Color(0xFF2D3748)

    // Processed Phase 7 Intelligence Data
    val smartSummary = remember(details, isCelsius, cityWeather.cityName) {
        IntelligentWeatherProcessor.processSmartWeatherSummary(details, isCelsius, cityWeather.cityName)
    }
    val primaryAiInsight = remember(details, isCelsius) {
        IntelligentWeatherProcessor.processPrimaryAiInsight(details, isCelsius)
    }
    val smartScores = remember(details, isCelsius) {
        IntelligentWeatherProcessor.processSmartWeatherScores(details, isCelsius)
    }
    val clothingAdvice = remember(details, isCelsius) {
        IntelligentWeatherProcessor.processClothingAdvice(details, isCelsius)
    }
    val travelIntel = remember(details, isCelsius) {
        IntelligentWeatherProcessor.processTravelIntelligence(details, isCelsius)
    }
    val smartAlerts = remember(details, isCelsius) {
        IntelligentWeatherProcessor.processSmartAlerts(details, isCelsius)
    }
    val lifestyleRecs = remember(details, isCelsius) {
        IntelligentWeatherProcessor.processLifestyleRecommendations(details, isCelsius)
    }
    val healthInsights = remember(details, isCelsius) {
        IntelligentWeatherProcessor.processHealthInsights(details, isCelsius)
    }
    val naturalTimeline = remember(details, isCelsius) {
        IntelligentWeatherProcessor.processWeatherTimelineSummary(details, isCelsius)
    }

    // Dynamic stateful notifications list
    var notificationsList by remember {
        mutableStateOf(
            listOf(
                "Welcome to SkySphere Cognitive Hub! Real-time telemetry synchronized.",
                if (details.uvIndex >= 6) "Alert: UV exposure is high today. Sunwear protection advised." else "Notification: UV levels are currently safe.",
                if (details.currentTemp >= 30) "Hydration Reminder: Ensure elevated fluid intake in warm climates." else "Notification: Thermal comfort levels stable.",
                if (smartAlerts.isNotEmpty()) "Severe Alert: ${smartAlerts.first().title} is active!" else "Notification: No severe meteorological alerts active."
            ).filter { it.isNotBlank() }
        )
    }
    var notificationSettingsAlerts by remember { mutableStateOf(true) }
    var notificationSettingsShifts by remember { mutableStateOf(true) }
    var notificationSettingsSummary by remember { mutableStateOf(true) }

    // Auto-generate AI summary on startup if empty
    LaunchedEffect(cityWeather.cityName) {
        if (aiSummaryState == null) {
            isGeneratingSummary = true
            coroutineScope.launch {
                val tempFormatted = "${details.currentTemp}°${if (isCelsius) "C" else "F"}"
                aiSummaryState = AiAssistantService.generateWeatherSummary(
                    cityName = cityWeather.cityName,
                    tempFormatted = tempFormatted,
                    condition = details.condition.displayName,
                    humidity = details.humidity,
                    windSpeed = details.windSpeed,
                    aqi = details.airQuality.aqi
                )
                isGeneratingSummary = false
            }
        }
    }

    val sectionTitles = listOf("AI & Alerts", "Lifestyle", "Health & Travel", "Compare & Notify")
    var selectedSection by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "INTELLIGENT HUB",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("hub_back_button")
                    ) {
                        Icon(
                            imageVector = SkySphereIcons.Back,
                            contentDescription = "Back to main screen",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isGeneratingSummary = true
                            coroutineScope.launch {
                                val tempFormatted = "${details.currentTemp}°${if (isCelsius) "C" else "F"}"
                                aiSummaryState = AiAssistantService.generateWeatherSummary(
                                    cityName = cityWeather.cityName,
                                    tempFormatted = tempFormatted,
                                    condition = details.condition.displayName,
                                    humidity = details.humidity,
                                    windSpeed = details.windSpeed,
                                    aqi = details.airQuality.aqi
                                )
                                isGeneratingSummary = false
                            }
                        }
                    ) {
                        Icon(
                            imageVector = SkySphereIcons.Refresh,
                            contentDescription = "Regenerate summary",
                            tint = LuxurySkyBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Horizontal navigation bar
            ScrollableTabRow(
                selectedTabIndex = selectedSection,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedSection]),
                        color = LuxurySkyBlue
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                sectionTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedSection == index,
                        onClick = { selectedSection = index },
                        text = {
                            Text(
                                text = title.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                        },
                        selectedContentColor = LuxurySkyBlue,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Content Area
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                when (selectedSection) {
                    0 -> { // AI Weather Assistant & Smart Alerts
                        // 1. Requirement 8: AI Primary Insight Card
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("primary_ai_insight_card")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Brush.linearGradient(colors = listOf(Color(0xFF2FA3FF), Color(0xFF00C6FF))))
                                    ) {
                                        Icon(
                                            imageVector = SkySphereIcons.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = primaryAiInsight.category,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = LuxurySkyBlue,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.5.sp
                                            )
                                        )
                                        Text(
                                            text = "PRIMARY AI RECOMMENDATION",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 9.sp
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = primaryAiInsight.insightText,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 22.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                )
                            }
                        }

                        // 2. Requirement 1: Smart Weather Summary Card
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("smart_weather_summary_card")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = SkySphereIcons.Timeline,
                                        contentDescription = null,
                                        tint = LuxuryCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "SMART WEATHER SUMMARY",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = LuxuryCyan,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = smartSummary,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = 22.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                )
                            }
                        }

                        // 3. Requirement 6: Smart Weather Scores Card (0-100)
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("smart_weather_scores_card")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = SkySphereIcons.Sports,
                                        contentDescription = null,
                                        tint = LuxurySkyBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "DAILY SMART WEATHER SCORES",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = LuxurySkyBlue,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))

                                val scoresList = listOf(
                                    Triple("Outdoor Score", smartScores.outdoorScore, Color(0xFF69F0AE)),
                                    Triple("Running Score", smartScores.runningScore, Color(0xFF40C4FF)),
                                    Triple("Cycling Score", smartScores.cyclingScore, Color(0xFFFFD740)),
                                    Triple("Travel Score", smartScores.travelScore, Color(0xFFB388FF)),
                                    Triple("Comfort Score", smartScores.comfortScore, Color(0xFFFF8A80)),
                                    Triple("Air Quality Score", smartScores.airQualityScore, Color(0xFF00E676))
                                )

                                scoresList.forEach { (label, score, color) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            color = textColor,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "$score / 100",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = color
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { score / 100f },
                                        color = color,
                                        trackColor = subCardBorder,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }

                        // 4. Requirement 5: Clothing Advisor Card
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("clothing_advisor_card")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = SkySphereIcons.Check,
                                        contentDescription = null,
                                        tint = LuxuryCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "CLOTHING ADVISOR",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = LuxuryCyan,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = clothingAdvice.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(clothingAdvice.items) { item ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(subCardBgSubtle)
                                                .border(1.dp, subCardBorderSubtle, RoundedCornerShape(12.dp))
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = SkySphereIcons.Check,
                                                contentDescription = null,
                                                tint = LuxurySkyBlue,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = item.name,
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = textColor
                                                )
                                                Text(
                                                    text = item.reason,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Requirement 7: Intelligent Alerts Card (Only active when needed!)
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("smart_alerts_card")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = SkySphereIcons.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "INTELLIGENT ALERTS",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = Color(0xFFFF5252),
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                if (smartAlerts.isEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = SkySphereIcons.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF69F0AE),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Atmosphere stable. No hazardous alerts detected.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    smartAlerts.forEach { alert ->
                                        val priorityColor = when (alert.severity) {
                                            AlertSeverity.CRITICAL -> Color(0xFFFF3D00)
                                            AlertSeverity.WARNING -> Color(0xFFFFAB40)
                                            AlertSeverity.INFO -> Color(0xFF40C4FF)
                                        }
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(priorityColor.copy(alpha = 0.08f))
                                                .border(1.dp, priorityColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                                .padding(12.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = SkySphereIcons.Warning,
                                                    contentDescription = null,
                                                    tint = priorityColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = alert.title,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = priorityColor
                                                )
                                                Spacer(modifier = Modifier.weight(1f))
                                                Text(
                                                    text = alert.severity.name,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = priorityColor
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = alert.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = textColor
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "REC: ${alert.recommendation}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                ),
                                                color = textColor.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 6. AI Assistant Q&A Card
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("ai_assistant_card")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = SkySphereIcons.Psychology,
                                        contentDescription = null,
                                        tint = LuxurySkyBlue,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "AI COGNITIVE Q&A ASSISTANT",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = LuxurySkyBlue,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                // Interactive Q&A Input
                                Text(
                                    text = "Ask a customized weather question:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                OutlinedTextField(
                                    value = chatInput,
                                    onValueChange = { chatInput = it },
                                    placeholder = {
                                        Text(
                                            "e.g., Should I buy an umbrella?",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                if (chatInput.isNotBlank()) {
                                                    isGeneratingChat = true
                                                    keyboardController?.hide()
                                                    coroutineScope.launch {
                                                        val tempFormatted = "${details.currentTemp}°${if (isCelsius) "C" else "F"}"
                                                        chatOutput = AiAssistantService.answerQuestion(
                                                            question = chatInput,
                                                            cityName = cityWeather.cityName,
                                                            tempFormatted = tempFormatted,
                                                            condition = details.condition.displayName,
                                                            humidity = details.humidity,
                                                            windSpeed = details.windSpeed,
                                                            aqi = details.airQuality.aqi
                                                        )
                                                        isGeneratingChat = false
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = SkySphereIcons.Send,
                                                contentDescription = "Submit query",
                                                tint = LuxurySkyBlue
                                            )
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = {
                                        if (chatInput.isNotBlank()) {
                                            isGeneratingChat = true
                                            keyboardController?.hide()
                                            coroutineScope.launch {
                                                val tempFormatted = "${details.currentTemp}°${if (isCelsius) "C" else "F"}"
                                                chatOutput = AiAssistantService.answerQuestion(
                                                    question = chatInput,
                                                    cityName = cityWeather.cityName,
                                                    tempFormatted = tempFormatted,
                                                    condition = details.condition.displayName,
                                                    humidity = details.humidity,
                                                    windSpeed = details.windSpeed,
                                                    aqi = details.airQuality.aqi
                                                )
                                                isGeneratingChat = false
                                            }
                                        }
                                    }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = LuxurySkyBlue,
                                        unfocusedBorderColor = subCardBorder,
                                        focusedContainerColor = subCardBg,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedTextColor = textColor,
                                        unfocusedTextColor = textColor
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("ai_assistant_input")
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Quick question pills
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(aiQuestions, key = { index, q -> "ai_q_${index}_${q.hashCode()}" }) { _, q ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0x1BFFFFFF))
                                                .border(1.dp, Color(0x11FFFFFF), RoundedCornerShape(16.dp))
                                                .clickable {
                                                    chatInput = q
                                                    isGeneratingChat = true
                                                    coroutineScope.launch {
                                                        val tempFormatted = "${details.currentTemp}°${if (isCelsius) "C" else "F"}"
                                                        chatOutput = AiAssistantService.answerQuestion(
                                                            question = q,
                                                            cityName = cityWeather.cityName,
                                                            tempFormatted = tempFormatted,
                                                            condition = details.condition.displayName,
                                                            humidity = details.humidity,
                                                            windSpeed = details.windSpeed,
                                                            aqi = details.airQuality.aqi
                                                        )
                                                        isGeneratingChat = false
                                                    }
                                                }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = q,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = textColor
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Answer Output
                                AnimatedVisibility(
                                    visible = chatOutput != null || isGeneratingChat,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0x0A2FA3FF))
                                            .border(1.dp, Color(0x1A2FA3FF), RoundedCornerShape(12.dp))
                                            .padding(12.dp)
                                    ) {
                                        if (isGeneratingChat) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center,
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                            ) {
                                                SkySphereLoadingAnimation(size = 32.dp)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    "AI generating response...",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        } else {
                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        imageVector = SkySphereIcons.AutoAwesome,
                                                        contentDescription = null,
                                                        tint = LuxuryCyan,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "AI INSIGHT",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = LuxuryCyan,
                                                            fontWeight = FontWeight.Bold,
                                                            letterSpacing = 1.sp
                                                        )
                                                    )
                                                    Spacer(modifier = Modifier.weight(1f))
                                                    IconButton(
                                                        onClick = { chatOutput = null },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = SkySphereIcons.Close,
                                                            contentDescription = "Clear answer",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = chatOutput ?: "",
                                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                                    color = textColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> { // Requirement 2: Lifestyle Intelligence Scorecard (All 12 categories!)
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("lifestyle_scorecard_card")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = SkySphereIcons.Sports,
                                        contentDescription = null,
                                        tint = LuxuryCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "LIFESTYLE INTELLIGENCE (12 ACTIVITIES)",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = LuxuryCyan,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))

                                lifestyleRecs.forEach { rec ->
                                    val progressColor = when {
                                        rec.score >= 85 -> Color(0xFF69F0AE)
                                        rec.score >= 70 -> Color(0xFF40C4FF)
                                        rec.score >= 50 -> Color(0xFFFFD740)
                                        else -> Color(0xFFFF5252)
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(progressColor.copy(alpha = 0.12f))
                                            ) {
                                                Icon(
                                                    imageVector = SkySphereIcons.Sports,
                                                    contentDescription = null,
                                                    tint = progressColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.Bottom) {
                                                    Text(
                                                        text = rec.name,
                                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                        color = textColor
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = rec.status.uppercase(),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = progressColor
                                                    )
                                                }
                                                Text(
                                                    text = rec.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "${rec.score}",
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                                color = progressColor,
                                                modifier = Modifier.width(48.dp),
                                                textAlign = TextAlign.End
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { rec.score / 100f },
                                            color = progressColor,
                                            trackColor = subCardBorder,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    2 -> { // Requirements 3 & 4: Health & Travel Intelligence
                        // 1. Requirement 3: Health Intelligence
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("health_insights_card")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = SkySphereIcons.Favorite,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "HEALTH INTELLIGENCE (8 TOPICS)",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = Color(0xFFFF5252),
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))

                                healthInsights.forEach { insight ->
                                    val riskColor = when (insight.severity) {
                                        AlertSeverity.CRITICAL -> Color(0xFFFF5252)
                                        AlertSeverity.WARNING -> Color(0xFFFFAB40)
                                        AlertSeverity.INFO -> Color(0xFF69F0AE)
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(subCardBg)
                                            .border(1.dp, subCardBorderSubtle, RoundedCornerShape(8.dp))
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = SkySphereIcons.Favorite,
                                                contentDescription = null,
                                                tint = riskColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = insight.title,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                color = textColor
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text(
                                                text = insight.status,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = riskColor
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Metric: ${insight.value}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = riskColor
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = insight.recommendation,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Requirement 4: Travel Intelligence
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("travel_planner_card")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = SkySphereIcons.Timeline,
                                        contentDescription = null,
                                        tint = LuxurySkyBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "TRAVEL INTELLIGENCE & ADVICE",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = LuxurySkyBlue,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = travelIntel.bestTravelTimeToday,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = LuxuryCyan)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Visibility Advice: ${travelIntel.visibilityAdvice}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor
                                )

                                travelIntel.rainDelayWarning?.let {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = "⚠️ $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFD740))
                                }
                                travelIntel.fogWarning?.let {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = "🌫️ $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFD740))
                                }
                                travelIntel.windWarning?.let {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = "💨 $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFD740))
                                }
                                travelIntel.stormAlert?.let {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = "🌩️ $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF5252))
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Hourly Travel Suitability:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                travelIntel.hourlySlots.take(5).forEach { slot ->
                                    val suitColor = when (slot.suitability) {
                                        "Ideal" -> Color(0xFF69F0AE)
                                        "Moderate" -> Color(0xFFFFD740)
                                        else -> Color(0xFFFF5252)
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(subCardBgSubtle)
                                            .border(1.dp, subCardBorderSubtle, RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = slot.time,
                                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = textColor
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = slot.tempFormatted,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = slot.tip,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(horizontalAlignment = Alignment.End) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(suitColor.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = slot.suitability.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = suitColor
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Score: ${slot.score}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = suitColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    3 -> { // Comparison & Notification Center
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("weather_comparison_card")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = SkySphereIcons.Compare,
                                        contentDescription = null,
                                        tint = LuxuryCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "SIDE-BY-SIDE COMPARISON",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = LuxuryCyan,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Primary Active City
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = subCardBg),
                                        modifier = Modifier.weight(1f).border(1.dp, subCardBorder, RoundedCornerShape(8.dp))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = cityWeather.cityName.uppercase(),
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                color = LuxurySkyBlue,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "${details.currentTemp}°${if (isCelsius) "C" else "F"}",
                                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                color = textColor
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            WeatherConditionIcon(
                                                weatherDetails = details,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Text(
                                                text = details.condition.displayName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Compared City
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = subCardBg),
                                        modifier = Modifier.weight(1f).border(1.dp, subCardBorder, RoundedCornerShape(8.dp))
                                    ) {
                                        if (comparisonCity == null) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clickable { showCitySelector = true }
                                                    .padding(12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = SkySphereIcons.Plus,
                                                    contentDescription = "Select Comparison City",
                                                    tint = LuxuryCyan,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "CHOOSE CITY",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = LuxuryCyan,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        } else {
                                            val cCity = comparisonCity!!
                                            val cDetails = cCity.weatherDetails
                                            Column(
                                                modifier = Modifier
                                                    .clickable { showCitySelector = true }
                                                    .padding(12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = cCity.cityName.uppercase(),
                                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = LuxuryCyan,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "${cDetails.currentTemp}°${if (isCelsius) "C" else "F"}",
                                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                    color = textColor
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                WeatherConditionIcon(
                                                    cityWeather = cCity,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                                Text(
                                                    text = cDetails.condition.displayName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Notification Preferences
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("notification_settings_card")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = SkySphereIcons.Notifications,
                                        contentDescription = null,
                                        tint = LuxurySkyBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "NOTIFICATION PREFERENCES",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = LuxurySkyBlue,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Severe Weather Alerts", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = textColor)
                                        Text(text = "Instant alerts for rain, storms, winds, heat & cold", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = notificationSettingsAlerts, onCheckedChange = { notificationSettingsAlerts = it }, colors = SwitchDefaults.colors(checkedThumbColor = LuxurySkyBlue))
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Daily AI Weather Summary", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = textColor)
                                        Text(text = "Receive short natural-language summary daily", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = notificationSettingsSummary, onCheckedChange = { notificationSettingsSummary = it }, colors = SwitchDefaults.colors(checkedThumbColor = LuxurySkyBlue))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // City selector modal sheet for side-by-side comparison
    if (showCitySelector) {
        AlertDialog(
            onDismissRequest = { showCitySelector = false },
            title = {
                Text(
                    text = "SELECT COMPARISON CITY",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    allCities.filter { it.cityName != cityWeather.cityName }.forEach { city ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    comparisonCity = city
                                    showCitySelector = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = city.cityName,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${city.weatherDetails.currentTemp}°${if (isCelsius) "C" else "F"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LuxurySkyBlue
                            )
                        }
                        HorizontalDivider(color = subCardBorderSubtle)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCitySelector = false }) {
                    Text("Close", color = LuxurySkyBlue)
                }
            },
            containerColor = Color(0xFF1E1E2E),
            titleContentColor = textColor,
            textContentColor = textColor
        )
    }
}
