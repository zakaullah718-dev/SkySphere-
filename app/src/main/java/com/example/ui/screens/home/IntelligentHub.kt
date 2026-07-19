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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
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

    // Active screen section state (for sub-tab selector)
    var selectedSection by remember { mutableStateOf(0) } // 0: AI & Alerts, 1: Lifestyle, 2: Health & Travel, 3: Compare & Notify
    val sectionTitles = listOf("AI & Alerts", "Lifestyle", "Health & Travel", "Compare & Notify")

    // Smart processing values
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
    val travelPlannerSlots = remember(details, isCelsius) {
        IntelligentWeatherProcessor.processTravelPlanner(details, isCelsius)
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to main screen",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            // Manual Refresh of AI content
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
                            imageVector = Icons.Filled.Refresh,
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
        containerColor = Color(0xFF070913) // Central Premium Cosmic Theme color
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
                        // 1. Weather Timeline Natural Language Card
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("weather_timeline_card")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Timeline,
                                        contentDescription = null,
                                        tint = LuxuryCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "24H NATURAL TIMELINE",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = LuxuryCyan,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = naturalTimeline,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = 22.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                )
                            }
                        }

                        // 2. AI Weather Assistant
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("ai_assistant_card")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Psychology,
                                        contentDescription = null,
                                        tint = LuxurySkyBlue,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "AI COGNITIVE ASSISTANT",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = LuxurySkyBlue,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                if (isGeneratingSummary) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(100.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            SkySphereLoadingAnimation()
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "Synthesizing weather data...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    aiSummaryState?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                lineHeight = 20.sp,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                            ),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Divider(color = Color(0x1BFFFFFF))
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }

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
                                                imageVector = Icons.AutoMirrored.Filled.Send,
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
                                        unfocusedBorderColor = Color(0x3BFFFFFF),
                                        focusedContainerColor = Color(0x0AFFFFFF),
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("ai_assistant_input")
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Predefined quick question pills
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(aiQuestions) { q ->
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
                                                color = Color.White
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
                                                        imageVector = Icons.Filled.AutoAwesome,
                                                        contentDescription = null,
                                                        tint = LuxuryCyan,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "COGNITIVE INSIGHT",
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
                                                            imageVector = Icons.Filled.Close,
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
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Smart Weather Alerts
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("smart_alerts_card")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.NotificationImportant,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "SMART WEATHER ALERTS",
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
                                            imageVector = Icons.Filled.CheckCircle,
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
                                    smartAlerts.forEachIndexed { idx, alert ->
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
                                                    imageVector = Icons.Filled.Warning,
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
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "REC: ${alert.recommendation}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                ),
                                                color = Color.White.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> { // Lifestyle Recommendations Scorecard
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("lifestyle_scorecard_card")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FitnessCenter,
                                        contentDescription = null,
                                        tint = LuxuryCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "LIFESTYLE SCORECARD",
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

                                    val iconVector = when (rec.iconName) {
                                        "DirectionsRun" -> Icons.Filled.DirectionsRun
                                        "DirectionsBike" -> Icons.Filled.DirectionsBike
                                        "Terrain" -> Icons.Filled.Terrain
                                        "BeachAccess" -> Icons.Filled.BeachAccess
                                        "Grass" -> Icons.Filled.Grass
                                        "CameraAlt" -> Icons.Filled.CameraAlt
                                        "SetMeal" -> Icons.Filled.SetMeal
                                        else -> Icons.Filled.Sports
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
                                                    imageVector = iconVector,
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
                                                        color = Color.White
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
                                            trackColor = Color(0x1BFFFFFF),
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

                    2 -> { // Health Insights & Travel Planner
                        // 1. Health Insights
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("health_insights_card")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "HEALTH INSIGHTS",
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

                                    val insightIcon = when (insight.iconName) {
                                        "Spa" -> Icons.Filled.Spa
                                        "WbSunny" -> Icons.Filled.WbSunny
                                        "LocalDrink" -> Icons.Filled.LocalDrink
                                        "Air" -> Icons.Filled.Air
                                        else -> Icons.Filled.Favorite
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x0AFFFFFF))
                                            .border(1.dp, Color(0x11FFFFFF), RoundedCornerShape(8.dp))
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = insightIcon,
                                                contentDescription = null,
                                                tint = riskColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = insight.title,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White
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
                                            text = "Level: ${insight.value}",
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

                        // 2. Travel Planner (Outdoor & Travel Windows)
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("travel_planner_card")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DepartureBoard,
                                        contentDescription = null,
                                        tint = LuxurySkyBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "TRAVEL & OUTDOOR PLANNER",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = LuxurySkyBlue,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Ranked best hours of the day for outdoor activities, travel or commute:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                if (travelPlannerSlots.isEmpty()) {
                                    Text(
                                        "No future hours data available to plan.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    travelPlannerSlots.take(5).forEach { slot ->
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
                                                .background(Color(0x05FFFFFF))
                                                .border(1.dp, Color(0x0AFFFFFF), RoundedCornerShape(8.dp))
                                                .padding(10.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = slot.time,
                                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                        color = Color.White
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
                    }

                    3 -> { // Weather Comparison & Simulated Notification History
                        // 1. Weather Comparison Side-By-Side
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("weather_comparison_card")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CompareArrows,
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
                                        colors = CardDefaults.cardColors(containerColor = Color(0x0AFFFFFF)),
                                        modifier = Modifier.weight(1f).border(1.dp, Color(0x1BFFFFFF), RoundedCornerShape(8.dp))
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
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            WeatherConditionIcon(
                                                condition = details.condition,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Text(
                                                text = details.condition.displayName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "AQI: ${details.airQuality.aqi}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Wind: ${details.windSpeed.toInt()} km/h",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    // Compared City
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0x0AFFFFFF)),
                                        modifier = Modifier.weight(1f).border(1.dp, Color(0x1BFFFFFF), RoundedCornerShape(8.dp))
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
                                                    imageVector = Icons.Filled.Add,
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
                                                Text(
                                                    text = "Select a city to compare",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                                    color = Color.White
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                WeatherConditionIcon(
                                                    condition = cDetails.condition,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                                Text(
                                                    text = cDetails.condition.displayName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "AQI: ${cDetails.airQuality.aqi}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "Wind: ${cDetails.windSpeed.toInt()} km/h",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }

                                if (comparisonCity != null) {
                                    val comp = comparisonCity!!
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Divider(color = Color(0x1BFFFFFF))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Comparative summary text
                                    val tempDiff = (details.currentTemp - comp.weatherDetails.currentTemp)
                                    val warmerText = when {
                                        tempDiff > 0 -> "${cityWeather.cityName} is warmer by ${tempDiff.absoluteValue}°."
                                        tempDiff < 0 -> "${comp.cityName} is warmer by ${tempDiff.absoluteValue}°."
                                        else -> "Both cities are currently at the same temperature."
                                    }

                                    val aqiDiff = (details.airQuality.aqi - comp.weatherDetails.airQuality.aqi)
                                    val cleanText = when {
                                        aqiDiff > 0 -> "${comp.cityName} has cleaner air."
                                        aqiDiff < 0 -> "${cityWeather.cityName} has cleaner air."
                                        else -> "Both cities share identical air safety grades."
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x05FFFFFF))
                                            .padding(12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.AutoAwesome,
                                                contentDescription = null,
                                                tint = LuxurySkyBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "COMPARATIVE ANALYSIS",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = LuxurySkyBlue,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 1.sp
                                                )
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "• $warmerText\n• $cleanText",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = { showCitySelector = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LuxuryCyan),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("CHANGE COMPARISON CITY")
                                    }
                                }
                            }
                        }

                        // 2. Simulated Notification Settings & List
                        item {
                            SkySphereCard(
                                modifier = Modifier.fillMaxWidth().testTag("notification_center_card")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.NotificationsActive,
                                        contentDescription = null,
                                        tint = LuxurySkyBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "NOTIFICATION INTELLIGENCE SYSTEM",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = LuxurySkyBlue,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Send weather notifications only when important conditions change.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = notificationSettingsAlerts,
                                            onCheckedChange = { notificationSettingsAlerts = it },
                                            colors = CheckboxDefaults.colors(checkedColor = LuxurySkyBlue)
                                        )
                                        Text("Severe Weather Warnings", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = notificationSettingsShifts,
                                            onCheckedChange = { notificationSettingsShifts = it },
                                            colors = CheckboxDefaults.colors(checkedColor = LuxurySkyBlue)
                                        )
                                        Text("Significant Atmosphere Shifts", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = notificationSettingsSummary,
                                            onCheckedChange = { notificationSettingsSummary = it },
                                            colors = CheckboxDefaults.colors(checkedColor = LuxurySkyBlue)
                                        )
                                        Text("Morning Cognitive Summaries", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Divider(color = Color(0x1BFFFFFF))
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "HISTORICAL ALERTS LOG",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    TextButton(
                                        onClick = { notificationsList = emptyList() },
                                        contentPadding = PaddingValues()
                                    ) {
                                        Text("Clear All", style = MaterialTheme.typography.labelSmall, color = LuxurySkyBlue)
                                    }
                                }

                                if (notificationsList.isEmpty()) {
                                    Text(
                                        text = "Log empty. No notifications triggered.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                } else {
                                    notificationsList.forEach { note ->
                                        Row(
                                            verticalAlignment = Alignment.Top,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Info,
                                                contentDescription = null,
                                                tint = LuxurySkyBlue,
                                                modifier = Modifier.size(14.dp).padding(top = 2.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = note,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // City Selector Bottom Sheet for Comparison
    if (showCitySelector) {
        val selectableCities = allCities.filter { it.cityName != cityWeather.cityName }
        AlertDialog(
            onDismissRequest = { showCitySelector = false },
            title = {
                Text(
                    "CHOOSE CITY TO COMPARE",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (selectableCities.isEmpty()) {
                        Text(
                            "Please search and add cities to your catalog first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                        ) {
                            items(selectableCities) { city ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x05FFFFFF))
                                        .clickable {
                                            comparisonCity = city
                                            showCitySelector = false
                                        }
                                        .padding(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = city.cityName,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                        Text(
                                            text = city.country,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${city.weatherDetails.currentTemp}°",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold),
                                        color = LuxuryCyan
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCitySelector = false }) {
                    Text("CLOSE", color = LuxurySkyBlue)
                }
            },
            containerColor = Color(0xFF13172E),
            textContentColor = Color.White,
            titleContentColor = Color.White
        )
    }
}
