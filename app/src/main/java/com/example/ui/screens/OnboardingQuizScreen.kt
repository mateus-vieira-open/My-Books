package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.UserProfileEntity

// Custom berry/magenta palette matching the reference image
val ChipSelectedBorder = Color(0xFFBE185D) // Vibrant deep magenta/berry
val ChipSelectedBg = Color(0xFFFDF2F8)     // Soft blush tint
val ChipSelectedText = Color(0xFF9D174D)   // High-contrast deep berry text
val ChipUnselectedBorder = Color(0xFFCBD5E1) // Subtle slate border
val ChipUnselectedBg = Color(0xFFFFFFFF)     // Clean white pill
val ChipUnselectedText = Color(0xFF475569)   // Slate gray text

val AvailableLiteraryGenres = listOf(
    "Teologia",
    "Quadrinhos",
    "Terror",
    "Ficção",
    "Filosofia",
    "Fantasia",
    "Suspense",
    "Romance",
    "História",
    "Biografias",
    "Poesia",
    "Distopia",
    "Psicologia",
    "Desenvolvimento Pessoal",
    "Clássicos",
    "Mangás",
    "Mistério Policial",
    "Mitologia",
    "Arte & Cinema",
    "Tecnologia & IA",
    "Espiritualidade",
    "Ensaios",
    "Crônicas",
    "Aventura"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingQuizScreen(
    userProfile: UserProfileEntity?,
    isRetake: Boolean,
    onComplete: (selectedGenres: List<String>, readingPaceWeeklyPages: Int) -> Unit,
    onDismissOrSkip: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Initialize with current profile genres if retaking, or popular defaults
    val initialSelected = remember(userProfile) {
        val current = userProfile?.preferredGenres
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        if (current.isNotEmpty()) current.toSet()
        else setOf("Teologia", "Quadrinhos", "Terror", "Ficção")
    }

    var selectedGenres by remember { mutableStateOf(initialSelected) }
    var selectedWeeklyPace by remember { mutableIntStateOf(150) } // Default 150 pages/week
    var customGenreInput by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }

    val minRequired = 2
    val isReady = selectedGenres.size >= minRequired

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Button(
                        onClick = {
                            if (isReady) {
                                onComplete(selectedGenres.toList(), selectedWeeklyPace)
                            }
                        },
                        enabled = isReady,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("button_complete_onboarding_quiz"),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChipSelectedBorder,
                            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRetake) "Atualizar Interesses com Gemini AI" else "Começar Leitura com Gemini AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (isRetake) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = onDismissOrSkip,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Voltar sem alterar",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        )
                    )
                )
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Badge
            Surface(
                color = ChipSelectedBg,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ChipSelectedBorder.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isRetake) Icons.AutoMirrored.Filled.TrendingUp else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = ChipSelectedBorder,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRetake) "EVOLUÇÃO DO LEITOR • QUIZ DE PREFERÊNCIAS" else "DESCOBERTA LITERÁRIA • PASSO INICIAL",
                        color = ChipSelectedText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Title
            Text(
                text = if (isRetake) "Como seus interesses evoluíram?" else "O que você mais gosta de ler?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = if (isRetake)
                    "Com o avançar das suas leituras, seus horizontes se expandem! Atualize seus assuntos favoritos para receber novas sugestões do Gemini AI que acompanham o seu desenvolvimento."
                else
                    "Toque nos gêneros literários abaixo para calibrar o assistente Gemini AI com o seu gosto pessoal. Você poderá refazer este quiz conforme ler novos livros!",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Counter Status Pill
            Surface(
                shape = CircleShape,
                color = if (isReady) ChipSelectedBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isReady) ChipSelectedBorder.copy(alpha = 0.5f) else Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isReady) ChipSelectedBorder else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${selectedGenres.size} assunto(s) selecionado(s) • mínimo de $minRequired",
                        color = if (isReady) ChipSelectedText else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isReady) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Organic Pill Cloud matching the user's reference image
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AvailableLiteraryGenres.forEach { genre ->
                    val isSelected = selectedGenres.contains(genre)
                    LiteraryInterestPill(
                        text = genre,
                        isSelected = isSelected,
                        onClick = {
                            selectedGenres = if (isSelected) {
                                selectedGenres - genre
                            } else {
                                selectedGenres + genre
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Add Custom Genre Button
            if (!showCustomInput) {
                OutlinedButton(
                    onClick = { showCustomInput = true },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Adicionar outro gênero", fontSize = 13.sp)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customGenreInput,
                        onValueChange = { customGenreInput = it },
                        placeholder = { Text("Ex: Ficção Épica, Poemas...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val clean = customGenreInput.trim()
                            if (clean.isNotBlank()) {
                                selectedGenres = selectedGenres + clean
                                customGenreInput = ""
                                showCustomInput = false
                            }
                        },
                        enabled = customGenreInput.isNotBlank(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Adicionar")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: Reader Evolution Pace
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Meta Semanal de Desenvolvimento",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Defina seu ritmo de leitura para incentivar o hábito diário:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val paces = listOf(
                        Triple(75, "Casual", "15 min/dia • 75 pág/sem"),
                        Triple(150, "Focado", "30 min/dia • 150 pág/sem"),
                        Triple(250, "Voraz", "1h+/dia • 250 pág/sem")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        paces.forEach { (pages, label, desc) ->
                            val isPaceSelected = selectedWeeklyPace == pages
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedWeeklyPace = pages },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isPaceSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = if (isPaceSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = label,
                                        fontWeight = if (isPaceSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isPaceSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$pages pág",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Organic, rounded capsule pill chip faithfully following the design in the user's reference image:
 * - Unselected: White pill, subtle border #CBD5E1, muted slate text #475569.
 * - Selected: Berry/magenta outline #BE185D, soft blush tint #FDF2F8, deep berry text #9D174D.
 * - Minimum touch target >= 48dp for accessibility.
 */
@Composable
fun LiteraryInterestPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) ChipSelectedBorder else ChipUnselectedBorder,
        label = "pill_border"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) ChipSelectedBg else ChipUnselectedBg,
        label = "pill_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) ChipSelectedText else ChipUnselectedText,
        label = "pill_text"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.03f else 1.0f,
        label = "pill_scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .defaultMinSize(minHeight = 44.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = CircleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = ChipSelectedBorder),
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("interest_pill_${text.lowercase().replace(" ", "_")}"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = ChipSelectedText,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = text,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.2.sp
            )
        }
    }
}
