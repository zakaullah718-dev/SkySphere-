package com.example.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * SkySphere Signature Icon Language
 *
 * A custom, unified vector icon suite designed specifically for SkySphere.
 * Characterized by precision geometric lines, rounded joint caps, fluid arcs,
 * and a cohesive 24x24dp celestial design aesthetic.
 */
object SkySphereIcons {

    // --- NAVIGATION ICONS ---

    val Info: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereInfo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            close()
            moveTo(12f, 16f)
            lineTo(12f, 11f)
            moveTo(12f, 8f)
            lineTo(12.01f, 8f)
        }.build()
    }

    val HomeActive: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereHomeActive",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.White),
            stroke = null
        ) {
            // Central Globe Core
            moveTo(12f, 4f)
            curveTo(7.58f, 4f, 4f, 7.58f, 4f, 12f)
            curveTo(4f, 16.42f, 7.58f, 20f, 12f, 20f)
            curveTo(16.42f, 20f, 20f, 16.42f, 20f, 12f)
            curveTo(20f, 7.58f, 16.42f, 4f, 12f, 4f)
            close()
        }.path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Tilted Atmospheric Orbit Ring
            moveTo(2f, 12f)
            curveTo(5f, 7.5f, 19f, 7.5f, 22f, 12f)
            curveTo(19f, 16.5f, 5f, 16.5f, 2f, 12f)
            close()
        }.build()
    }

    val Home: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereHome",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Central Globe Outline
            moveTo(12f, 4f)
            curveTo(7.58f, 4f, 4f, 7.58f, 4f, 12f)
            curveTo(4f, 16.42f, 7.58f, 20f, 12f, 20f)
            curveTo(16.42f, 20f, 20f, 16.42f, 20f, 12f)
            curveTo(20f, 7.58f, 16.42f, 4f, 12f, 4f)
            close()

            // Atmospheric Ring Arc
            moveTo(3f, 12f)
            curveTo(6f, 8.5f, 18f, 8.5f, 21f, 12f)
        }.build()
    }

    val SearchActive: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereSearchActive",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.White),
            stroke = null
        ) {
            // Radar Lens Circle
            moveTo(10.5f, 3f)
            curveTo(6.36f, 3f, 3f, 6.36f, 3f, 10.5f)
            curveTo(3f, 14.64f, 6.36f, 18f, 10.5f, 18f)
            curveTo(14.64f, 18f, 18f, 14.64f, 18f, 10.5f)
            curveTo(18f, 6.36f, 14.64f, 3f, 10.5f, 3f)
            close()
        }.path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            // Handle
            moveTo(16f, 16f)
            lineTo(21f, 21f)
        }.build()
    }

    val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereSearch",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Radar Lens Outline
            moveTo(11f, 4f)
            curveTo(7.13f, 4f, 4f, 7.13f, 4f, 11f)
            curveTo(4f, 14.87f, 7.13f, 18f, 11f, 18f)
            curveTo(14.87f, 18f, 18f, 14.87f, 18f, 11f)
            curveTo(18f, 7.13f, 14.87f, 4f, 11f, 4f)
            close()

            // Concentric Radar Crosshair
            moveTo(11f, 7f)
            lineTo(11f, 15f)
            moveTo(7f, 11f)
            lineTo(15f, 11f)

            // Lens Handle
            moveTo(16f, 16f)
            lineTo(21f, 21f)
        }.build()
    }

    val MapActive: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereMapActive",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.White),
            stroke = null
        ) {
            // Radar Map Sheet Fold 1
            moveTo(3f, 6f)
            lineTo(9f, 3f)
            lineTo(9f, 18f)
            lineTo(3f, 21f)
            close()

            // Fold 2
            moveTo(9f, 3f)
            lineTo(15f, 6f)
            lineTo(15f, 21f)
            lineTo(9f, 18f)
            close()

            // Fold 3
            moveTo(15f, 6f)
            lineTo(21f, 3f)
            lineTo(21f, 18f)
            lineTo(15f, 21f)
            close()
        }.build()
    }

    val Map: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereMap",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Radar Map Folded Grid Outline
            moveTo(3f, 6f)
            lineTo(9f, 3f)
            lineTo(15f, 6f)
            lineTo(21f, 3f)
            lineTo(21f, 18f)
            lineTo(15f, 21f)
            lineTo(9f, 18f)
            lineTo(3f, 21f)
            close()

            // Vertical Fold Seams
            moveTo(9f, 3f)
            lineTo(9f, 18f)
            moveTo(15f, 6f)
            lineTo(15f, 21f)
        }.build()
    }

    val VaultActive: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereVaultActive",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.White),
            stroke = null
        ) {
            // Starlight Diamond Vault Icon
            moveTo(12f, 2f)
            lineTo(15f, 9f)
            lineTo(22f, 12f)
            lineTo(15f, 15f)
            lineTo(12f, 22f)
            lineTo(9f, 15f)
            lineTo(2f, 12f)
            lineTo(9f, 9f)
            close()
        }.build()
    }

    val Vault: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereVault",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Starlight Diamond Vault Outline
            moveTo(12f, 2f)
            lineTo(15.2f, 8.8f)
            lineTo(22f, 12f)
            lineTo(15.2f, 15.2f)
            lineTo(12f, 22f)
            lineTo(8.8f, 15.2f)
            lineTo(2f, 12f)
            lineTo(8.8f, 8.8f)
            close()
        }.build()
    }

    val SettingsActive: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereSettingsActive",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.White),
            stroke = null
        ) {
            // Telemetry Control Knobs & Gear Outer Ring
            moveTo(12f, 8f)
            curveTo(9.79f, 8f, 8f, 9.79f, 8f, 12f)
            curveTo(8f, 14.21f, 9.79f, 16f, 12f, 16f)
            curveTo(14.21f, 16f, 16f, 14.21f, 16f, 12f)
            curveTo(16f, 9.79f, 14.21f, 8f, 12f, 8f)
            close()
        }.path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Slider Tracks
            moveTo(3f, 7f)
            lineTo(21f, 7f)
            moveTo(3f, 12f)
            lineTo(21f, 12f)
            moveTo(3f, 17f)
            lineTo(21f, 17f)
        }.build()
    }

    val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereSettings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Telemetry Slider Rails & Nodes
            moveTo(3f, 6f)
            lineTo(21f, 6f)
            moveTo(3f, 12f)
            lineTo(21f, 12f)
            moveTo(3f, 18f)
            lineTo(21f, 18f)

            // Slider Node 1
            moveTo(8f, 4f)
            lineTo(8f, 8f)
            // Slider Node 2
            moveTo(16f, 10f)
            lineTo(16f, 14f)
            // Slider Node 3
            moveTo(11f, 16f)
            lineTo(11f, 20f)
        }.build()
    }

    // --- WEATHER CONDITION ICONS ---

    val Sunny: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereSunny",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color(0xFFFFD54F)),
            stroke = null
        ) {
            // Central Solar Core
            moveTo(12f, 7f)
            curveTo(9.24f, 7f, 7f, 9.24f, 7f, 12f)
            curveTo(7f, 14.76f, 9.24f, 17f, 12f, 17f)
            curveTo(14.76f, 17f, 17f, 14.76f, 17f, 12f)
            curveTo(17f, 9.24f, 14.76f, 7f, 12f, 7f)
            close()
        }.path(
            fill = null,
            stroke = SolidColor(Color(0xFFFFD54F)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            // 8 Solar Rays
            moveTo(12f, 2f); lineTo(12f, 4f)
            moveTo(12f, 20f); lineTo(12f, 22f)
            moveTo(2f, 12f); lineTo(4f, 12f)
            moveTo(20f, 12f); lineTo(22f, 12f)
            moveTo(4.93f, 4.93f); lineTo(6.34f, 6.34f)
            moveTo(17.66f, 17.66f); lineTo(19.07f, 19.07f)
            moveTo(4.93f, 19.07f); lineTo(6.34f, 17.66f)
            moveTo(17.66f, 6.34f); lineTo(19.07f, 4.93f)
        }.build()
    }

    val SunPartlyCloudy: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereSunPartlyCloudy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color(0xFFFFD54F)),
            stroke = null
        ) {
            // Sun Core Behind Cloud
            moveTo(9f, 4f)
            curveTo(6.24f, 4f, 4f, 6.24f, 4f, 9f)
            curveTo(4f, 10.4f, 4.6f, 11.6f, 5.5f, 12.5f)
            curveTo(6.5f, 10.5f, 8.5f, 9f, 11f, 9f)
            curveTo(11.3f, 9f, 11.6f, 9.02f, 11.9f, 9.07f)
            curveTo(11.98f, 8.05f, 11.5f, 7f, 11f, 6.2f)
            curveTo(10.5f, 4.9f, 9.8f, 4f, 9f, 4f)
            close()
        }.path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Sculpted Foreground Cloud
            moveTo(6.5f, 18.5f)
            lineTo(17.5f, 18.5f)
            curveTo(19.43f, 18.5f, 21f, 16.93f, 21f, 15f)
            curveTo(21f, 13.07f, 19.43f, 11.5f, 17.5f, 11.5f)
            curveTo(17.3f, 11.5f, 17.1f, 11.52f, 16.9f, 11.55f)
            curveTo(16.3f, 9.5f, 14.4f, 8f, 12f, 8f)
            curveTo(9.6f, 8f, 7.7f, 9.5f, 7.1f, 11.55f)
            curveTo(4.8f, 11.8f, 3f, 13.7f, 3f, 16f)
            curveTo(3f, 17.38f, 4.57f, 18.5f, 6.5f, 18.5f)
            close()
        }.build()
    }

    val Cloud: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereCloud",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color(0xFF94A3B8)),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Sculpted Cloud Contour
            moveTo(6.5f, 18.5f)
            lineTo(17.5f, 18.5f)
            curveTo(19.43f, 18.5f, 21f, 16.93f, 21f, 15f)
            curveTo(21f, 13.07f, 19.43f, 11.5f, 17.5f, 11.5f)
            curveTo(17.3f, 11.5f, 17.1f, 11.52f, 16.9f, 11.55f)
            curveTo(16.3f, 9.5f, 14.4f, 8f, 12f, 8f)
            curveTo(9.6f, 8f, 7.7f, 9.5f, 7.1f, 11.55f)
            curveTo(4.8f, 11.8f, 3f, 13.7f, 3f, 16f)
            curveTo(3f, 17.38f, 4.57f, 18.5f, 6.5f, 18.5f)
            close()
        }.build()
    }

    val Rainy: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereRainy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Cloud Top
            moveTo(6.5f, 14f)
            lineTo(17.5f, 14f)
            curveTo(19.43f, 14f, 21f, 12.43f, 21f, 10.5f)
            curveTo(21f, 8.57f, 19.43f, 7f, 17.5f, 7f)
            curveTo(16.3f, 5f, 14.4f, 3.5f, 12f, 3.5f)
            curveTo(9.6f, 3.5f, 7.7f, 5f, 7.1f, 7.05f)
            curveTo(4.8f, 7.3f, 3f, 9.2f, 3f, 11.5f)
            curveTo(3f, 12.88f, 4.57f, 14f, 6.5f, 14f)
            close()
        }.path(
            fill = null,
            stroke = SolidColor(Color(0xFF38BDF8)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            // Rain Vectors
            moveTo(8f, 17f); lineTo(6.5f, 21f)
            moveTo(12f, 17f); lineTo(10.5f, 21f)
            moveTo(16f, 17f); lineTo(14.5f, 21f)
        }.build()
    }

    val Thunderstorm: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereThunderstorm",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Storm Cloud
            moveTo(6.5f, 13f)
            lineTo(17.5f, 13f)
            curveTo(19.43f, 13f, 21f, 11.43f, 21f, 9.5f)
            curveTo(21f, 7.57f, 19.43f, 6f, 17.5f, 6f)
            curveTo(16.3f, 4f, 14.4f, 2.5f, 12f, 2.5f)
            curveTo(9.6f, 2.5f, 7.7f, 4f, 7.1f, 6.05f)
            curveTo(4.8f, 6.3f, 3f, 8.2f, 3f, 10.5f)
            curveTo(3f, 11.88f, 4.57f, 13f, 6.5f, 13f)
            close()
        }.path(
            fill = SolidColor(Color(0xFFFACC15)),
            stroke = null
        ) {
            // Lightning Bolt
            moveTo(13f, 12f)
            lineTo(9.5f, 17f)
            lineTo(12f, 17f)
            lineTo(10.5f, 22f)
            lineTo(15.5f, 16f)
            lineTo(13f, 16f)
            close()
        }.build()
    }

    val Snowy: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereSnowy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color(0xFF38BDF8)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            // Hexagonal Snowflake Crystal
            moveTo(12f, 2f); lineTo(12f, 22f)
            moveTo(3.34f, 7f); lineTo(20.66f, 17f)
            moveTo(3.34f, 17f); lineTo(20.66f, 7f)

            // Snowflake V-Nodes
            moveTo(10f, 4f); lineTo(12f, 6f); lineTo(14f, 4f)
            moveTo(10f, 20f); lineTo(12f, 18f); lineTo(14f, 20f)
        }.build()
    }

    val Foggy: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereFoggy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color(0xFFCBD5E1)),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round
        ) {
            // Dynamic Fog Wave Bands
            moveTo(4f, 7f); lineTo(20f, 7f)
            moveTo(2f, 12f); lineTo(22f, 12f)
            moveTo(5f, 17f); lineTo(19f, 17f)
        }.build()
    }

    val Moon: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereMoon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color(0xFF38BDF8)),
            stroke = null
        ) {
            // Crescent Moon Vector
            moveTo(12.3f, 2f)
            curveTo(6.9f, 2f, 2.5f, 6.4f, 2.5f, 11.8f)
            curveTo(2.5f, 17.2f, 6.9f, 21.6f, 12.3f, 21.6f)
            curveTo(15.9f, 21.6f, 19f, 19.6f, 20.7f, 16.7f)
            curveTo(14.6f, 16.7f, 9.7f, 11.8f, 9.7f, 5.7f)
            curveTo(9.7f, 4.4f, 9.9f, 3.2f, 10.4f, 2.1f)
            curveTo(11f, 2f, 11.7f, 2f, 12.3f, 2f)
            close()
        }.build()
    }

    val MoonPartlyCloudy: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereMoonPartlyCloudy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color(0xFF38BDF8)),
            stroke = null
        ) {
            // Crescent Moon behind Night Cloud
            moveTo(9.5f, 3f)
            curveTo(7f, 3f, 5f, 5f, 5f, 7.5f)
            curveTo(5f, 9f, 5.7f, 10.3f, 6.8f, 11.1f)
            curveTo(7.8f, 9.5f, 9.5f, 8.5f, 11.5f, 8.5f)
            curveTo(11.8f, 8.5f, 12.1f, 8.52f, 12.4f, 8.57f)
            curveTo(12.5f, 7.5f, 12f, 6.5f, 11.5f, 5.7f)
            curveTo(11f, 4.5f, 10.2f, 3f, 9.5f, 3f)
            close()
        }.path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Night Cloud Contour
            moveTo(6.5f, 18.5f)
            lineTo(17.5f, 18.5f)
            curveTo(19.43f, 18.5f, 21f, 16.93f, 21f, 15f)
            curveTo(21f, 13.07f, 19.43f, 11.5f, 17.5f, 11.5f)
            curveTo(17.3f, 11.5f, 17.1f, 11.52f, 16.9f, 11.55f)
            curveTo(16.3f, 9.5f, 14.4f, 8f, 12f, 8f)
            curveTo(9.6f, 8f, 7.7f, 9.5f, 7.1f, 11.55f)
            curveTo(4.8f, 11.8f, 3f, 13.7f, 3f, 16f)
            curveTo(3f, 17.38f, 4.57f, 18.5f, 6.5f, 18.5f)
            close()
        }.build()
    }

    // --- TELEMETRY & METRIC ICONS ---

    val Thermostat: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereThermostat",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color(0xFFFFB74D)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Thermometer Glass Outline
            moveTo(14f, 14.76f)
            curveTo(15.24f, 13.71f, 16f, 12.18f, 16f, 10.5f)
            curveTo(16f, 7.46f, 13.54f, 5f, 10.5f, 5f)
            curveTo(7.46f, 5f, 5f, 7.46f, 5f, 10.5f)
            curveTo(5f, 12.18f, 5.76f, 13.71f, 7f, 14.76f)
            lineTo(7f, 18.5f)
            curveTo(7f, 19.88f, 8.12f, 21f, 9.5f, 21f)
            lineTo(11.5f, 21f)
            curveTo(12.88f, 21f, 14f, 19.88f, 14f, 18.5f)
            close()
        }.path(
            fill = SolidColor(Color(0xFFFFB74D)),
            stroke = null
        ) {
            // Fluid Core Bulb
            moveTo(10.5f, 8.5f)
            curveTo(9.4f, 8.5f, 8.5f, 9.4f, 8.5f, 10.5f)
            curveTo(8.5f, 11.6f, 9.4f, 12.5f, 10.5f, 12.5f)
            curveTo(11.6f, 12.5f, 12.5f, 11.6f, 12.5f, 10.5f)
            curveTo(12.5f, 9.4f, 11.6f, 8.5f, 10.5f, 8.5f)
            close()
        }.build()
    }

    val Humidity: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereHumidity",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color(0xFF38BDF8)),
            stroke = null
        ) {
            // Teardrop Droplet Core
            moveTo(12f, 2.5f)
            curveTo(12f, 2.5f, 5f, 10f, 5f, 15f)
            curveTo(5f, 18.87f, 8.13f, 22f, 12f, 22f)
            curveTo(15.87f, 22f, 19f, 18.87f, 19f, 15f)
            curveTo(19f, 10f, 12f, 2.5f, 12f, 2.5f)
            close()
        }.path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            // Fluid Reflection Curve
            moveTo(9f, 14f)
            curveTo(9f, 16.5f, 11f, 18.5f, 13.5f, 18.5f)
        }.build()
    }

    val Wind: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereWind",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color(0xFF38BDF8)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            // Aerodynamic Wind Wave 1
            moveTo(3f, 8f)
            lineTo(15f, 8f)
            curveTo(16.66f, 8f, 18f, 6.66f, 18f, 5f)
            curveTo(18f, 3.34f, 16.66f, 2f, 15f, 2f)

            // Aerodynamic Wind Wave 2
            moveTo(2f, 14f)
            lineTo(19f, 14f)
            curveTo(20.66f, 14f, 22f, 12.66f, 22f, 11f)
            curveTo(22f, 9.34f, 20.66f, 8f, 19f, 8f)

            // Aerodynamic Wind Wave 3
            moveTo(4f, 20f)
            lineTo(12f, 20f)
            curveTo(13.66f, 20f, 15f, 18.66f, 15f, 17f)
            curveTo(15f, 15.34f, 13.66f, 14f, 12f, 14f)
        }.build()
    }

    val Pressure: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySpherePressure",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color(0xFFB0BEC5)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Concentric Gauge Outer Ring
            moveTo(12f, 3f)
            curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
            curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f)
            curveTo(16.97f, 21f, 21f, 16.97f, 21f, 12f)
            curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f)
            close()

            // Gauge Needle
            moveTo(12f, 12f)
            lineTo(16f, 8f)

            // Pivot Point
            moveTo(12f, 12f)
            lineTo(12f, 12.01f)
        }.build()
    }

    val UVIndex: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereUVIndex",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color(0xFFFF7043)),
            stroke = null
        ) {
            // Solar Burst Center
            moveTo(12f, 6f)
            curveTo(8.69f, 6f, 6f, 8.69f, 6f, 12f)
            curveTo(6f, 15.31f, 8.69f, 18f, 12f, 18f)
            curveTo(15.31f, 18f, 18f, 15.31f, 18f, 12f)
            curveTo(18f, 8.69f, 15.31f, 6f, 12f, 6f)
            close()
        }.path(
            fill = null,
            stroke = SolidColor(Color(0xFFFF7043)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            // Solar Rays & Shield Base
            moveTo(12f, 2f); lineTo(12f, 4f)
            moveTo(2f, 12f); lineTo(4f, 12f)
            moveTo(20f, 12f); lineTo(22f, 12f)
            moveTo(5f, 5f); lineTo(6.5f, 6.5f)
            moveTo(19f, 5f); lineTo(17.5f, 6.5f)
        }.build()
    }

    val Visibility: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereVisibility",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color(0xFF38BDF8)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Celestial Eye Aperture
            moveTo(2f, 12f)
            curveTo(5f, 6.5f, 19f, 6.5f, 22f, 12f)
            curveTo(19f, 17.5f, 5f, 17.5f, 2f, 12f)
            close()

            // Iris Center
            moveTo(12f, 9.5f)
            curveTo(10.62f, 9.5f, 9.5f, 10.62f, 9.5f, 12f)
            curveTo(9.5f, 13.38f, 10.62f, 14.5f, 12f, 14.5f)
            curveTo(13.38f, 14.5f, 14.5f, 13.38f, 14.5f, 12f)
            curveTo(14.5f, 10.62f, 13.38f, 9.5f, 12f, 9.5f)
            close()
        }.build()
    }

    val Sunrise: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereSunrise",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color(0xFFFDE047)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            // Horizon Line
            moveTo(2f, 18f); lineTo(22f, 18f)

            // Rising Sun Arc
            moveTo(6f, 18f)
            curveTo(6f, 14.69f, 8.69f, 12f, 12f, 12f)
            curveTo(15.31f, 12f, 18f, 14.69f, 18f, 18f)

            // Ascending Vector Arrow
            moveTo(12f, 9f); lineTo(12f, 3f)
            moveTo(9f, 6f); lineTo(12f, 3f); lineTo(15f, 6f)
        }.build()
    }

    val Sunset: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereSunset",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color(0xFFFB923C)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            // Horizon Line
            moveTo(2f, 18f); lineTo(22f, 18f)

            // Setting Sun Arc
            moveTo(6f, 18f)
            curveTo(6f, 14.69f, 8.69f, 12f, 12f, 12f)
            curveTo(15.31f, 12f, 18f, 14.69f, 18f, 18f)

            // Descending Vector Arrow
            moveTo(12f, 3f); lineTo(12f, 9f)
            moveTo(9f, 6f); lineTo(12f, 9f); lineTo(15f, 6f)
        }.build()
    }

    val Umbrella: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereUmbrella",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color(0xFF38BDF8)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Umbrella Canopy
            moveTo(3f, 13f)
            curveTo(3f, 8f, 7f, 3f, 12f, 3f)
            curveTo(17f, 3f, 21f, 8f, 21f, 13f)
            lineTo(3f, 13f)
            close()

            // Umbrella Shaft & Handle
            moveTo(12f, 13f)
            lineTo(12f, 19f)
            curveTo(12f, 20.66f, 10.66f, 22f, 9f, 22f)
        }.build()
    }

    val Warning: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereWarning",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color(0xFFFF5252)),
            stroke = null
        ) {
            // Shielded Triangle Warning Body
            moveTo(12f, 3f)
            lineTo(2f, 21f)
            lineTo(22f, 21f)
            close()
        }.path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            // Exclamation Core
            moveTo(12f, 9f); lineTo(12f, 14f)
            moveTo(12f, 17f); lineTo(12f, 17.5f)
        }.build()
    }

    val Palette: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySpherePalette",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Swatch Palette Contour
            moveTo(12f, 3f)
            curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
            curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f)
            curveTo(13.38f, 21f, 14.5f, 19.88f, 14.5f, 18.5f)
            curveTo(14.5f, 17.81f, 14.22f, 17.18f, 13.78f, 16.72f)
            curveTo(13.34f, 16.26f, 13.07f, 15.63f, 13.07f, 14.94f)
            curveTo(13.07f, 13.56f, 14.19f, 12.44f, 15.57f, 12.44f)
            lineTo(21f, 12.44f)
            curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f)
            close()
        }.build()
    }

    val NotificationsActive: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereNotificationsActive",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color(0xFF2FA3FF)),
            stroke = null
        ) {
            // Chime Bell Base
            moveTo(12f, 22f)
            curveTo(13.1f, 22f, 14f, 21.1f, 14f, 20f)
            lineTo(10f, 20f)
            curveTo(10f, 21.1f, 10.9f, 22f, 12f, 22f)
            close()

            moveTo(18f, 16f)
            lineTo(18f, 11f)
            curveTo(18f, 7.93f, 16.37f, 5.36f, 13.5f, 4.68f)
            lineTo(13.5f, 4f)
            curveTo(13.5f, 3.17f, 12.83f, 2.5f, 12f, 2.5f)
            curveTo(11.17f, 2.5f, 10.5f, 3.17f, 10.5f, 4f)
            lineTo(10.5f, 4.68f)
            curveTo(7.64f, 5.36f, 6f, 7.92f, 6f, 11f)
            lineTo(6f, 16f)
            lineTo(4f, 18f)
            lineTo(4f, 19f)
            lineTo(20f, 19f)
            lineTo(20f, 18f)
            lineTo(18f, 16f)
            close()
        }.build()
    }

    val Person: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySpherePerson",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Celestial Profile Head Orb
            moveTo(12f, 4f)
            curveTo(9.79f, 4f, 8f, 5.79f, 8f, 8f)
            curveTo(8f, 10.21f, 9.79f, 12f, 12f, 12f)
            curveTo(14.21f, 12f, 16f, 10.21f, 16f, 8f)
            curveTo(16f, 5.79f, 14.21f, 4f, 12f, 4f)
            close()

            // Aura Shoulders Arc
            moveTo(4f, 20f)
            curveTo(4f, 16.69f, 7.58f, 14f, 12f, 14f)
            curveTo(16.42f, 14f, 20f, 16.69f, 20f, 20f)
        }.build()
    }

    val Edit: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereEdit",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Stylus Pen Vector
            moveTo(3f, 21f)
            lineTo(7.5f, 20.5f)
            lineTo(19.5f, 8.5f)
            curveTo(20.33f, 7.67f, 20.33f, 6.33f, 19.5f, 5.5f)
            lineTo(18.5f, 4.5f)
            curveTo(17.67f, 3.67f, 16.33f, 3.67f, 15.5f, 4.5f)
            lineTo(3.5f, 16.5f)
            lineTo(3f, 21f)
            close()
        }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereCheck",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color(0xFF10B981)),
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Crisp Checkmark
            moveTo(4f, 12f)
            lineTo(9f, 17f)
            lineTo(20f, 6f)
        }.build()
    }

    val MyLocation: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereMyLocation",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color(0xFF2FA3FF)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            // Crosshair Ring
            moveTo(12f, 5f)
            curveTo(8.13f, 5f, 5f, 8.13f, 5f, 12f)
            curveTo(5f, 15.87f, 8.13f, 19f, 12f, 19f)
            curveTo(15.87f, 19f, 19f, 15.87f, 19f, 12f)
            curveTo(19f, 8.13f, 15.87f, 5f, 12f, 5f)
            close()

            // Crosshair Ticks
            moveTo(12f, 2f); lineTo(12f, 5f)
            moveTo(12f, 19f); lineTo(12f, 22f)
            moveTo(2f, 12f); lineTo(5f, 12f)
            moveTo(19f, 12f); lineTo(22f, 12f)
        }.path(
            fill = SolidColor(Color(0xFF2FA3FF)),
            stroke = null
        ) {
            // Core Target Dot
            moveTo(12f, 9.5f)
            curveTo(10.62f, 9.5f, 9.5f, 10.62f, 9.5f, 12f)
            curveTo(9.5f, 13.38f, 10.62f, 14.5f, 12f, 14.5f)
            curveTo(13.38f, 14.5f, 14.5f, 13.38f, 14.5f, 12f)
            curveTo(14.5f, 10.62f, 13.38f, 9.5f, 12f, 9.5f)
            close()
        }.build()
    }

    val Refresh: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereRefresh",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color(0xFF2FA3FF)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Orbital Sync Arrow Arc 1
            moveTo(20f, 11f)
            curveTo(19.5f, 6.8f, 15.9f, 3.5f, 11.5f, 3.5f)
            curveTo(7.4f, 3.5f, 4f, 6.4f, 3.2f, 10.3f)

            // Arrow Tip 1
            moveTo(20f, 6f); lineTo(20f, 11f); lineTo(15f, 11f)

            // Orbital Sync Arrow Arc 2
            moveTo(4f, 13f)
            curveTo(4.5f, 17.2f, 8.1f, 20.5f, 12.5f, 20.5f)
            curveTo(16.6f, 20.5f, 20f, 17.6f, 20.8f, 13.7f)

            // Arrow Tip 2
            moveTo(4f, 18f); lineTo(4f, 13f); lineTo(9f, 13f)
        }.build()
    }

    val AutoAwesome: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereAutoAwesome",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color(0xFF38BDF8)),
            stroke = null
        ) {
            // Major Celestial Sparkle Star
            moveTo(19f, 9f)
            lineTo(20.25f, 6.25f)
            lineTo(23f, 5f)
            lineTo(20.25f, 3.75f)
            lineTo(19f, 1f)
            lineTo(17.75f, 3.75f)
            lineTo(15f, 5f)
            lineTo(17.75f, 6.25f)
            close()

            moveTo(11.5f, 9.5f)
            lineTo(9f, 4f)
            lineTo(6.5f, 9.5f)
            lineTo(1f, 12f)
            lineTo(6.5f, 14.5f)
            lineTo(9f, 20f)
            lineTo(11.5f, 14.5f)
            lineTo(17f, 12f)
            close()

            moveTo(19f, 15f)
            lineTo(17.75f, 17.75f)
            lineTo(15f, 19f)
            lineTo(17.75f, 20.25f)
            lineTo(19f, 23f)
            lineTo(20.25f, 20.25f)
            lineTo(23f, 19f)
            lineTo(20.25f, 17.75f)
            close()
        }.build()
    }

    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereChevronRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Tapered Arrow Right
            moveTo(9f, 18f)
            lineTo(15f, 12f)
            lineTo(9f, 6f)
        }.build()
    }

    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereClose",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            // Cross Lines
            moveTo(18f, 6f); lineTo(6f, 18f)
            moveTo(6f, 6f); lineTo(18f, 18f)
        }.build()
    }

    val Location: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereLocation",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color(0xFF38BDF8)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Drop Pin Outline
            moveTo(12f, 2f)
            curveTo(8.13f, 2f, 5f, 5.13f, 5f, 9f)
            curveTo(5f, 14.25f, 12f, 22f, 12f, 22f)
            curveTo(12f, 22f, 19f, 14.25f, 19f, 9f)
            curveTo(19f, 5.13f, 15.87f, 2f, 12f, 2f)
            close()

            // Pin Eye
            moveTo(12f, 11.5f)
            curveTo(10.62f, 11.5f, 9.5f, 10.38f, 9.5f, 9f)
            curveTo(9.5f, 7.62f, 10.62f, 6.5f, 12f, 6.5f)
            curveTo(13.38f, 6.5f, 14.5f, 7.62f, 14.5f, 9f)
            curveTo(14.5f, 10.38f, 13.38f, 11.5f, 12f, 11.5f)
            close()
        }.build()
    }

    val Back: ImageVector by lazy {
        ImageVector.Builder(name = "SkySphereBack", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(
            fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(19f, 12f); lineTo(5f, 12f)
            moveTo(12f, 19f); lineTo(5f, 12f); lineTo(12f, 5f)
        }.build()
    }

    val Send: ImageVector by lazy {
        ImageVector.Builder(name = "SkySphereSend", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(
            fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(22f, 2f); lineTo(11f, 13f)
            moveTo(22f, 2f); lineTo(15f, 22f); lineTo(11f, 13f); lineTo(2f, 9f); lineTo(22f, 2f)
        }.build()
    }

    val Timeline: ImageVector by lazy {
        ImageVector.Builder(name = "SkySphereTimeline", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(
            fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(23f, 6f); lineTo(13.5f, 15.5f); lineTo(8.5f, 10.5f); lineTo(1f, 18f)
            moveTo(17f, 6f); lineTo(23f, 6f); lineTo(23f, 12f)
        }.build()
    }

    val Psychology: ImageVector by lazy {
        ImageVector.Builder(name = "SkySpherePsychology", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(
            fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 2f); curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f); curveTo(2f, 15.5f, 3.8f, 18.6f, 6.5f, 20.3f); lineTo(6.5f, 22f); lineTo(10f, 22f)
            moveTo(12f, 6f); curveTo(10.34f, 6f, 9f, 7.34f, 9f, 9f); curveTo(9f, 10.66f, 10.34f, 12f, 12f, 12f); curveTo(13.66f, 12f, 15f, 10.66f, 15f, 9f)
        }.build()
    }

    val Sports: ImageVector by lazy {
        ImageVector.Builder(name = "SkySphereSports", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(
            fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 2f); curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f); curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f); curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f); curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f); close()
            moveTo(12f, 2f); lineTo(12f, 22f)
            moveTo(2f, 12f); lineTo(22f, 12f)
        }.build()
    }

    val Mountain: ImageVector by lazy {
        ImageVector.Builder(name = "SkySphereMountain", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(
            fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 20f); lineTo(10f, 6f); lineTo(14f, 13f); lineTo(17f, 9f); lineTo(21f, 20f); close()
        }.build()
    }

    val RainRadar: ImageVector by lazy {
        ImageVector.Builder(name = "SkySphereRainRadar", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(
            fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 12f); moveTo(12f, 2f); curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f); curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f); curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            moveTo(12f, 12f); lineTo(19f, 5f)
        }.build()
    }

    val Camera: ImageVector by lazy {
        ImageVector.Builder(name = "SkySphereCamera", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(
            fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(23f, 19f); curveTo(23f, 20.1f, 22.1f, 21f, 21f, 21f); lineTo(3f, 21f); curveTo(1.9f, 21f, 1f, 20.1f, 1f, 19f); lineTo(1f, 8f); curveTo(1f, 6.9f, 1.9f, 6f, 3f, 6f); lineTo(7f, 6f); lineTo(9f, 3f); lineTo(15f, 3f); lineTo(17f, 6f); lineTo(21f, 6f); curveTo(22.1f, 6f, 23f, 6.9f, 23f, 8f); close()
            moveTo(12f, 17f); curveTo(14.21f, 17f, 16f, 15.21f, 16f, 13f); curveTo(16f, 10.79f, 14.21f, 9f, 12f, 9f); curveTo(9.79f, 9f, 8f, 10.79f, 8f, 13f); curveTo(8f, 15.21f, 9.79f, 17f, 12f, 17f); close()
        }.build()
    }

    val Restaurant: ImageVector by lazy {
        ImageVector.Builder(name = "SkySphereRestaurant", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(
            fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(11f, 9f); lineTo(11f, 3f); moveTo(8f, 9f); lineTo(8f, 3f); moveTo(5f, 9f); lineTo(5f, 3f)
            moveTo(5f, 9f); curveTo(5f, 12f, 11f, 12f, 11f, 9f); moveTo(8f, 12f); lineTo(8f, 21f)
            moveTo(16f, 3f); lineTo(16f, 21f); moveTo(16f, 3f); curveTo(19f, 3f, 19f, 9f, 16f, 9f)
        }.build()
    }

    val Favorite: ImageVector by lazy {
        ImageVector.Builder(name = "SkySphereFavorite", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(
            fill = SolidColor(Color(0xFFFF5252)), stroke = null
        ) {
            moveTo(12f, 21.35f); lineTo(10.55f, 20.03f); curveTo(5.4f, 15.36f, 2f, 12.28f, 2f, 8.5f); curveTo(2f, 5.42f, 4.42f, 3f, 7.5f, 3f); curveTo(9.24f, 3f, 10.91f, 3.81f, 12f, 5.09f); curveTo(13.09f, 3.81f, 14.76f, 3f, 16.5f, 3f); curveTo(19.58f, 3f, 22f, 5.42f, 22f, 8.5f); curveTo(22f, 12.28f, 18.6f, 15.36f, 13.45f, 20.04f); close()
        }.build()
    }

    val Compare: ImageVector by lazy {
        ImageVector.Builder(name = "SkySphereCompare", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(
            fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9.01f, 14f); lineTo(2f, 14f); lineTo(5.5f, 17.5f); moveTo(14.99f, 10f); lineTo(22f, 10f); lineTo(18.5f, 6.5f)
        }.build()
    }

    val Plus: ImageVector by lazy {
        ImageVector.Builder(name = "SkySpherePlus", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(
            fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }.build()
    }

    val Notifications: ImageVector by lazy {
        ImageVector.Builder(name = "SkySphereNotifications", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(
            fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(18f, 8f); curveTo(18f, 4.69f, 15.31f, 2f, 12f, 2f); curveTo(8.69f, 2f, 6f, 4.69f, 6f, 8f); curveTo(6f, 15f, 3f, 17f, 3f, 17f); lineTo(21f, 17f); curveTo(21f, 17f, 18f, 15f, 18f, 8f); close()
            moveTo(13.73f, 21f); curveTo(13.25f, 21.6f, 12.65f, 22f, 12f, 22f); curveTo(11.35f, 22f, 10.75f, 21.6f, 10.27f, 21f)
        }.build()
    }

    val Pause: ImageVector by lazy {
        ImageVector.Builder(name = "SkySpherePause", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(
            fill = SolidColor(Color.White), stroke = null
        ) {
            moveTo(6f, 19f); lineTo(10f, 19f); lineTo(10f, 5f); lineTo(6f, 5f); close()
            moveTo(14f, 5f); lineTo(14f, 19f); lineTo(18f, 19f); lineTo(18f, 5f); close()
        }.build()
    }

    val Play: ImageVector by lazy {
        ImageVector.Builder(name = "SkySpherePlay", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(
            fill = SolidColor(Color.White), stroke = null
        ) {
            moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close()
        }.build()
    }

    val SkipPrevious: ImageVector by lazy {
        ImageVector.Builder(name = "SkySphereSkipPrevious", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(
            fill = SolidColor(Color.White), stroke = null
        ) {
            moveTo(6f, 6f); lineTo(8f, 6f); lineTo(8f, 18f); lineTo(6f, 18f); close()
            moveTo(9.5f, 12f); lineTo(18f, 6f); lineTo(18f, 18f); close()
        }.build()
    }

    val SkipNext: ImageVector by lazy {
        ImageVector.Builder(name = "SkySphereSkipNext", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).path(
            fill = SolidColor(Color.White), stroke = null
        ) {
            moveTo(6f, 18f); lineTo(14.5f, 12f); lineTo(6f, 6f); close()
            moveTo(16f, 6f); lineTo(18f, 6f); lineTo(18f, 18f); lineTo(16f, 18f); close()
        }.build()
    }

    val Compass: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereCompass",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 3f)
            curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
            curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f)
            curveTo(16.97f, 21f, 21f, 16.97f, 21f, 12f)
            curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f)
            close()
        }.path(
            fill = SolidColor(Color.White),
            stroke = null
        ) {
            moveTo(12f, 7f)
            lineTo(15f, 12f)
            lineTo(12f, 10.5f)
            lineTo(9f, 12f)
            close()
        }.path(
            fill = SolidColor(Color.White.copy(alpha = 0.5f)),
            stroke = null
        ) {
            moveTo(12f, 17f)
            lineTo(15f, 12f)
            lineTo(12f, 13.5f)
            lineTo(9f, 12f)
            close()
        }.build()
    }

    val Moonrise: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereMoonrise",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color(0xFF38BDF8)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            // Horizon Line
            moveTo(2f, 18f); lineTo(22f, 18f)

            // Crescent Moon
            moveTo(6f, 18f)
            curveTo(6f, 14.69f, 8.69f, 12f, 12f, 12f)
            curveTo(13.8f, 12f, 15.4f, 12.8f, 16.5f, 14.1f)
            curveTo(15.2f, 14.1f, 14f, 15.2f, 14f, 16.5f)
            curveTo(14f, 17f, 14.2f, 17.5f, 14.5f, 18f)

            // Ascending Arrow
            moveTo(18f, 10f); lineTo(18f, 3f)
            moveTo(15f, 6f); lineTo(18f, 3f); lineTo(21f, 6f)
        }.build()
    }

    val Moonset: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkySphereMoonset",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color(0xFF818CF8)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            // Horizon Line
            moveTo(2f, 18f); lineTo(22f, 18f)

            // Crescent Moon
            moveTo(6f, 18f)
            curveTo(6f, 14.69f, 8.69f, 12f, 12f, 12f)
            curveTo(13.8f, 12f, 15.4f, 12.8f, 16.5f, 14.1f)
            curveTo(15.2f, 14.1f, 14f, 15.2f, 14f, 16.5f)
            curveTo(14f, 17f, 14.2f, 17.5f, 14.5f, 18f)

            // Descending Arrow
            moveTo(18f, 3f); lineTo(18f, 10f)
            moveTo(15f, 7f); lineTo(18f, 10f); lineTo(21f, 7f)
        }.build()
    }
}
