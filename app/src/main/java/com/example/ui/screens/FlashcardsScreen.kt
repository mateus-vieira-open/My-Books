package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.FlashcardEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(
    flashcards: List<FlashcardEntity>,
    isGenerating: Boolean,
    generationMessage: String?,
    initialBookId: Long? = null,
    initialChapter: String? = null,
    onBack: () -> Unit,
    onReviewCard: (FlashcardEntity, String) -> Unit,
    onDeleteCard: (FlashcardEntity) -> Unit,
    onGenerateFromExternalBook: (title: String, author: String, chapter: String, sourceType: String) -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Estudar (Anki), 1 = Gerenciar Cartões
    var showExternalSearchDialog by remember { mutableStateOf(false) }

    // Distinct books for filter
    val bookTitles = remember(flashcards) {
        listOf("Todos") + flashcards.map { it.bookTitle }.distinct()
    }
    var selectedBookFilter by remember { mutableStateOf("Todos") }

    val filteredCards = remember(flashcards, selectedBookFilter) {
        if (selectedBookFilter == "Todos") flashcards
        else flashcards.filter { it.bookTitle == selectedBookFilter }
    }

    var currentCardIndex by remember { mutableIntStateOf(0) }
    var isCardFlipped by remember { mutableStateOf(false) }

    // Clamp index
    LaunchedEffect(filteredCards.size) {
        if (currentCardIndex >= filteredCards.size && filteredCards.isNotEmpty()) {
            currentCardIndex = 0
            isCardFlipped = false
        }
    }

    // Show toast for generation message
    LaunchedEffect(generationMessage) {
        generationMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            onClearMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF6366F1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🗂️", fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Memorização Anki",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${flashcards.size} cartões • Repetição Espaçada",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showExternalSearchDialog = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("button_search_external_anki")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Físico / Kindle", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Mode Tabs (Estudo vs Biblioteca de Cartões)
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Estudo Ativo (${filteredCards.size})", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Psychology, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Ver Cartões") },
                    icon = { Icon(Icons.Default.ViewAgenda, contentDescription = null) }
                )
            }

            // Filter Chips by Book
            if (bookTitles.size > 2) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(bookTitles) { title ->
                        FilterChip(
                            selected = selectedBookFilter == title,
                            onClick = {
                                selectedBookFilter = title
                                currentCardIndex = 0
                                isCardFlipped = false
                            },
                            label = { Text(title, maxLines = 1) }
                        )
                    }
                }
            }

            if (isGenerating) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "O Gemini está pesquisando o capítulo e gerando cartões Anki...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (filteredCards.isEmpty() && !isGenerating) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("🗂️", fontSize = 64.sp)
                        Text(
                            text = "Nenhum cartão Anki disponível ainda",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Abra qualquer livro no leitor e clique em 'Gerar Anki do Capítulo', ou use o botão acima para pesquisar livros físicos e do seu Kindle no Gemini!",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showExternalSearchDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pesquisar Livro Físico / Kindle no Gemini")
                        }
                    }
                }
            } else if (selectedTab == 0 && filteredCards.isNotEmpty()) {
                // --- STUDY MODE (Anki Flip Card) ---
                val activeCard = filteredCards.getOrNull(currentCardIndex) ?: filteredCards.first()

                // Calculate Deck Mastery Counts
                val newCount = filteredCards.count { it.masteryLevel == "NOVO" }
                val learningCount = filteredCards.count { it.masteryLevel == "APRENDENDO" }
                val goodCount = filteredCards.count { it.masteryLevel == "BOM" }
                val masteredCount = filteredCards.count { it.masteryLevel == "DOMINADO" }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Header progress
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cartão ${currentCardIndex + 1} de ${filteredCards.size}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Source Badge
                            val sourceBadge = when (activeCard.sourceType) {
                                "FISICO" -> "📕 Livro Físico"
                                "KINDLE" -> "📱 Kindle"
                                else -> "📖 E-book no App"
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = sourceBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Deck Mastery Anki Stats Bar
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF3B82F6)))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Novos: $newCount", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Aprendendo: $learningCount", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Bons: $goodCount", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF6366F1)))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Dominados: $masteredCount", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // The Flippable Card
                    val rotation by animateFloatAsState(
                        targetValue = if (isCardFlipped) 180f else 0f,
                        animationSpec = tween(durationMillis = 400),
                        label = "cardFlip"
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .graphicsLayer {
                                rotationY = rotation
                                cameraDistance = 12f * density
                            }
                            .clickable { isCardFlipped = !isCardFlipped }
                            .testTag("anki_flashcard_view"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCardFlipped) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                .graphicsLayer {
                                    if (rotation > 90f) rotationY = 180f
                                }
                        ) {
                            if (rotation <= 90f) {
                                // FRONT: Question & Active Recall Prompt
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = activeCard.bookTitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = activeCard.chapterTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Text(
                                            text = activeCard.frontQuestion,
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 32.sp
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.TouchApp,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Toque no cartão para ver a resposta",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            } else {
                                // BACK: Answer & Key Quote
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "RESPOSTA & CONCEITO",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF10B981)
                                            )
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "Nível: ${activeCard.masteryLevel}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Text(
                                            text = activeCard.backAnswer,
                                            style = MaterialTheme.typography.bodyLarge,
                                            lineHeight = 26.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        if (activeCard.keyQuote.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                            ) {
                                                Row(modifier = Modifier.padding(12.dp)) {
                                                    Text("“", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = activeCard.keyQuote,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontStyle = FontStyle.Italic,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        text = "Como foi sua recordação? Avalie abaixo:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Anki Repetition Controls
                    if (isCardFlipped) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. ERRO
                            Button(
                                onClick = {
                                    onReviewCard(activeCard, "ERRO")
                                    isCardFlipped = false
                                    currentCardIndex = (currentCardIndex + 1) % filteredCards.size
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Errei", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("< 1 min", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                                }
                            }

                            // 2. DIFÍCIL
                            Button(
                                onClick = {
                                    onReviewCard(activeCard, "DIFICIL")
                                    isCardFlipped = false
                                    currentCardIndex = (currentCardIndex + 1) % filteredCards.size
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Difícil", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("1 dia", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                                }
                            }

                            // 3. BOM
                            Button(
                                onClick = {
                                    onReviewCard(activeCard, "BOM")
                                    isCardFlipped = false
                                    currentCardIndex = (currentCardIndex + 1) % filteredCards.size
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Bom", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("3 dias", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                                }
                            }

                            // 4. FÁCIL
                            Button(
                                onClick = {
                                    onReviewCard(activeCard, "FACIL")
                                    isCardFlipped = false
                                    currentCardIndex = (currentCardIndex + 1) % filteredCards.size
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Fácil", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("5 dias", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                                }
                            }
                        }
                    } else {
                        // Flip Button
                        Button(
                            onClick = { isCardFlipped = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("button_show_answer"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mostrar Resposta (Espaço / Toque)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // --- LIST & MANAGE MODE ---
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredCards) { card ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = card.bookTitle,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "${card.chapterTitle} • ${card.sourceType}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(onClick = { onDeleteCard(card) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "❓ ${card.frontQuestion}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "💡 ${card.backAnswer}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (card.keyQuote.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "“${card.keyQuote}”",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal to Search and Generate from Physical Book or Kindle using Gemini
    if (showExternalSearchDialog) {
        var externalTitle by remember { mutableStateOf("") }
        var externalAuthor by remember { mutableStateOf("") }
        var externalChapter by remember { mutableStateOf("") }
        var selectedSource by remember { mutableStateOf("FISICO") } // "FISICO" or "KINDLE"

        AlertDialog(
            onDismissRequest = { showExternalSearchDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (selectedSource == "FISICO") "📕" else "📱", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gerar Anki de Livro Externo", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Leu em livro físico ou Kindle? Informe os dados para o Gemini pesquisar a obra e gerar os cartões de memorização:",
                        style = MaterialTheme.typography.bodySmall
                    )

                    // Source Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedSource == "FISICO",
                            onClick = { selectedSource = "FISICO" },
                            label = { Text("📕 Livro Físico") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedSource == "KINDLE",
                            onClick = { selectedSource = "KINDLE" },
                            label = { Text("📱 Kindle") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Quick Preset Chips for Easy 1-Tap Search
                    Text(
                        text = "Sugestões populares para preencher rápido:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.clickable {
                                externalTitle = "Sapiens"
                                externalAuthor = "Yuval Noah Harari"
                                externalChapter = "Capítulo 1: A Revolução Cognitiva"
                            }
                        ) {
                            Text(
                                text = "⚡ Sapiens (Cap. 1)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.clickable {
                                externalTitle = "Hábitos Atômicos"
                                externalAuthor = "James Clear"
                                externalChapter = "Capítulo 1: O Poder dos Pequenos Hábitos"
                            }
                        ) {
                            Text(
                                text = "⚡ Hábitos Atômicos",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = externalTitle,
                        onValueChange = { externalTitle = it },
                        label = { Text("Título da Obra *") },
                        placeholder = { Text("Ex: Sapiens, 1984, Hábitos Atômicos") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_external_title")
                    )

                    OutlinedTextField(
                        value = externalAuthor,
                        onValueChange = { externalAuthor = it },
                        label = { Text("Autor (Opcional)") },
                        placeholder = { Text("Ex: Yuval Noah Harari, George Orwell") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_external_author")
                    )

                    OutlinedTextField(
                        value = externalChapter,
                        onValueChange = { externalChapter = it },
                        label = { Text("Capítulo ou Tema Lido *") },
                        placeholder = { Text("Ex: Capítulo 1, A Revolução Cognitiva") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_external_chapter")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (externalTitle.isNotBlank() && externalChapter.isNotBlank()) {
                            onGenerateFromExternalBook(
                                externalTitle.trim(),
                                externalAuthor.trim(),
                                externalChapter.trim(),
                                selectedSource
                            )
                            showExternalSearchDialog = false
                        } else {
                            Toast.makeText(context, "Preencha o título e o capítulo!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gerar no Gemini")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExternalSearchDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
