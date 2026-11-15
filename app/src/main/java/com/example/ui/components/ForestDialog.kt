package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DateUtils
import com.example.i18n.Strings
import com.example.i18n.formatDuration
import com.example.i18n.label
import com.example.model.AppLanguage
import com.example.model.PlantSpecies
import com.example.ui.theme.ForestEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SunlightGold
import com.example.viewmodel.MainUiState

@Composable
fun ForestDialog(
    uiState: MainUiState,
    s: Strings,
    onDismiss: () -> Unit,
    onUnlockOrSelectSpecies: (PlantSpecies) -> Unit
) {
    BackHandler { onDismiss() }

    val forest = uiState.data.forestState
    val isArabic = uiState.data.settings.language == AppLanguage.AR

    // 5 growth stages across 120 minutes cycle
    val growthProgress = (forest.currentGrowthMinutes / 120f).coerceIn(0f, 1f)
    val (stageIndex, stageLabel) = when {
        forest.currentGrowthMinutes < 15 -> 0 to s.stageSeed
        forest.currentGrowthMinutes < 35 -> 1 to s.stageSprout
        forest.currentGrowthMinutes < 65 -> 2 to s.stageSapling
        forest.currentGrowthMinutes < 95 -> 3 to s.stageBlooming
        else -> 4 to s.stageAncient
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Park, null, tint = ForestEmerald)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = s.forestTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Sunlight Points Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SunlightGold.copy(alpha = 0.16f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.WbSunny, null, tint = SunlightGold, modifier = Modifier.size(18.dp))
                        Text(
                            text = forest.sunlightPoints.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = SunlightGold
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_forest_button")) {
                    Icon(Icons.Default.Close, contentDescription = s.cancel)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Forest Banner + Active Growing Plant
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(145.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_forest_banner_1794743963672),
                                    contentDescription = s.forestTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                                            )
                                        )
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(14.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${s.currentTree}: ${forest.activeSpecies.label(s)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = stageLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFFD1FAE5)
                                        )
                                    }
                                    PlantIllustration(
                                        species = forest.activeSpecies,
                                        stage = stageIndex,
                                        modifier = Modifier.size(68.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(s.growthProgress, style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        text = "${forest.currentGrowthMinutes} / 120 ${s.minutesShort}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = ForestEmerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { growthProgress },
                                    color = ForestEmerald,
                                    trackColor = ForestEmerald.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Text(
                                    text = s.sunlightPoints + ": +10 / " + s.minutesShort,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Species Gallery
                item {
                    Text(
                        text = s.speciesGallery,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlantSpecies.entries.chunked(2).forEach { rowSpecies ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowSpecies.forEach { species ->
                                    val isUnlocked = forest.unlockedSpecies.contains(species)
                                    val isActive = forest.activeSpecies == species
                                    val canAfford = forest.sunlightPoints >= species.costPoints
                                    val speciesColor = Color(species.colorHex)

                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            if (isActive) 2.dp else 1.dp,
                                            if (isActive) ForestEmerald else MaterialTheme.colorScheme.outline
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(CircleShape)
                                                    .background(speciesColor.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                PlantIllustration(
                                                    species = species,
                                                    stage = 4,
                                                    modifier = Modifier.size(48.dp)
                                                )
                                            }
                                            Text(
                                                text = species.label(s),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Button(
                                                onClick = { onUnlockOrSelectSpecies(species) },
                                                enabled = isUnlocked || canAfford,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = when {
                                                        isActive -> MaterialTheme.colorScheme.primary
                                                        isUnlocked -> MaterialTheme.colorScheme.secondaryContainer
                                                        else -> SunlightGold
                                                    },
                                                    contentColor = when {
                                                        isActive -> Color.White
                                                        isUnlocked -> MaterialTheme.colorScheme.onSecondaryContainer
                                                        else -> Color.White
                                                    }
                                                ),
                                                shape = CircleShape,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                when {
                                                    isActive -> {
                                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(15.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(s.active, style = MaterialTheme.typography.labelMedium)
                                                    }
                                                    isUnlocked -> {
                                                        Text(s.select, style = MaterialTheme.typography.labelMedium)
                                                    }
                                                    else -> {
                                                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            "${species.costPoints} ☀",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontFamily = JetBrainsMonoFontFamily
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

                // Planted Forest History
                item {
                    Text(
                        text = "${s.plantedForest} (${forest.plantedHistory.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        forest.plantedHistory.forEach { planted ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PlantIllustration(
                                        species = planted.species,
                                        stage = 4,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = planted.species.label(s),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = DateUtils.formatDateTime(planted.plantedAtEpochMillis, isArabic),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = formatDuration(planted.durationMinutes, s),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = ForestEmerald,
                                        fontWeight = FontWeight.Bold
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

@Composable
fun PlantIllustration(
    species: PlantSpecies,
    stage: Int,
    modifier: Modifier = Modifier
) {
    val canopyColor = Color(species.colorHex)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Soil Mound
        drawOval(
            color = Color(0xFF78350F),
            topLeft = Offset(w * 0.22f, h * 0.80f),
            size = Size(w * 0.56f, h * 0.16f)
        )

        when (stage) {
            0 -> {
                // Seed
                drawOval(
                    color = Color(0xFFD97706),
                    topLeft = Offset(w * 0.40f, h * 0.68f),
                    size = Size(w * 0.20f, h * 0.16f)
                )
            }
            1 -> {
                // Sprout
                drawLine(
                    color = ForestEmerald,
                    start = Offset(w * 0.5f, h * 0.82f),
                    end = Offset(w * 0.5f, h * 0.52f),
                    strokeWidth = w * 0.06f
                )
                drawOval(
                    color = canopyColor,
                    topLeft = Offset(w * 0.30f, h * 0.44f),
                    size = Size(w * 0.20f, h * 0.13f)
                )
                drawOval(
                    color = canopyColor,
                    topLeft = Offset(w * 0.50f, h * 0.44f),
                    size = Size(w * 0.20f, h * 0.13f)
                )
            }
            2 -> {
                // Sapling
                drawLine(
                    color = Color(0xFF92400E),
                    start = Offset(w * 0.5f, h * 0.82f),
                    end = Offset(w * 0.5f, h * 0.40f),
                    strokeWidth = w * 0.08f
                )
                drawCircle(
                    color = canopyColor,
                    radius = w * 0.22f,
                    center = Offset(w * 0.5f, h * 0.38f)
                )
            }
            else -> {
                // Blooming / Ancient Tree tailored by species
                when (species) {
                    PlantSpecies.PINE -> {
                        drawRect(
                            color = Color(0xFF78350F),
                            topLeft = Offset(w * 0.44f, h * 0.62f),
                            size = Size(w * 0.12f, h * 0.22f)
                        )
                        val p1 = Path().apply {
                            moveTo(w * 0.5f, h * 0.12f)
                            lineTo(w * 0.22f, h * 0.52f)
                            lineTo(w * 0.78f, h * 0.52f)
                            close()
                        }
                        val p2 = Path().apply {
                            moveTo(w * 0.5f, h * 0.32f)
                            lineTo(w * 0.16f, h * 0.70f)
                            lineTo(w * 0.84f, h * 0.70f)
                            close()
                        }
                        drawPath(p2, canopyColor)
                        drawPath(p1, canopyColor.copy(alpha = 0.88f))
                    }
                    PlantSpecies.SUNFLOWER -> {
                        drawLine(
                            color = Color(0xFF15803D),
                            start = Offset(w * 0.5f, h * 0.82f),
                            end = Offset(w * 0.5f, h * 0.42f),
                            strokeWidth = w * 0.08f
                        )
                        drawCircle(color = SunlightGold, radius = w * 0.26f, center = Offset(w * 0.5f, h * 0.36f))
                        drawCircle(color = Color(0xFF78350F), radius = w * 0.13f, center = Offset(w * 0.5f, h * 0.36f))
                    }
                    PlantSpecies.CACTUS -> {
                        drawRoundRect(
                            color = canopyColor,
                            topLeft = Offset(w * 0.40f, h * 0.22f),
                            size = Size(w * 0.20f, h * 0.60f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
                        )
                        drawRoundRect(
                            color = canopyColor,
                            topLeft = Offset(w * 0.24f, h * 0.38f),
                            size = Size(w * 0.16f, h * 0.12f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                        )
                        drawRoundRect(
                            color = canopyColor,
                            topLeft = Offset(w * 0.60f, h * 0.32f),
                            size = Size(w * 0.16f, h * 0.12f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                        )
                    }
                    else -> {
                        drawRect(
                            color = Color(0xFF78350F),
                            topLeft = Offset(w * 0.43f, h * 0.55f),
                            size = Size(w * 0.14f, h * 0.28f)
                        )
                        drawCircle(color = canopyColor, radius = w * 0.24f, center = Offset(w * 0.5f, h * 0.36f))
                        drawCircle(color = canopyColor.copy(alpha = 0.85f), radius = w * 0.18f, center = Offset(w * 0.32f, h * 0.46f))
                        drawCircle(color = canopyColor.copy(alpha = 0.85f), radius = w * 0.18f, center = Offset(w * 0.68f, h * 0.46f))
                    }
                }
            }
        }
    }
}
