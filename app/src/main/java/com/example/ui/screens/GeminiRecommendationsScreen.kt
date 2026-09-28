package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.remote.BookRecommendation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiRecommendationsScreen(
    userProfile: UserProfileEntity?,
    currentBooks: List<BookEntity>,
    recommendations: List<BookRecommendation>,
    isLoading: Boolean,
    onRefresh: (String?) -> Unit,
    onAddToShelf: (BookRecommendation) -> Unit,
    onUpdateGenres: (String) -> Unit,
    onOpenQuizRetake: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showInterestsDialog by remember { mutableStateOf(false) }
    var addedBooksState by remember { mutableStateOf(setOf<String>()) }

    val defaultInterests = listOf("Teologia", "Quadrinhos", "Terror", "Ficção", "Filosofia", "História")
    val userInterests = remember(userProfile?.preferredGenres) {
        userProfile?.preferredGenres?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
            ?: listOf("Teologia", "Quadrinhos", "Terror", "Ficção")
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sugestões Gemini AI",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showInterestsDialog = true },
                        modifier = Modifier.testTag("button_edit_interests")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Ajustar Interesses"
                        )
                    }
                    IconButton(
                        onClick = { onRefresh(null) },
                        enabled = !isLoading,
                        modifier = Modifier.testTag("button_refresh_gemini")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerar com Gemini"
                        )
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
            // Hero Banner Explaining the Gemini Analysis
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Curadoria Inteligente",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Gemini 3.5 Flash",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Análise cruzada dos livros na sua estante (${currentBooks.size} obras) combinada com seus interesses do cadastro:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Chips of Active Interests
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(userInterests) { interest ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "✨ $interest",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onOpenQuizRetake,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Evolução do Leitor • Refazer Quiz de Gêneros",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Loading state
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Consultando a inteligência do Gemini…",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cruzando gêneros: ${userInterests.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // List of Recommendations
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(recommendations) { rec ->
                        val isAdded = addedBooksState.contains(rec.title)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("rec_card_${rec.title}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Surface(
                                            color = Color(android.graphics.Color.parseColor(rec.coverColorHex)),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = rec.genre,
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = rec.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Text(
                                            text = "por ${rec.author} • ~${rec.estimatedPages} páginas",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Why Recommend Box
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Por que você vai amar:",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = rec.whyRecommend,
                                            style = MaterialTheme.typography.bodySmall,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = rec.synopsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        onAddToShelf(rec)
                                        addedBooksState = addedBooksState + rec.title
                                    },
                                    enabled = !isAdded,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isAdded) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Adicionado à Estante (Quero Ler)")
                                    } else {
                                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Adicionar à Estante (Quero Ler)")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal to customize Survey Interests
    if (showInterestsDialog) {
        var currentSelection by remember {
            mutableStateOf(userInterests.toSet())
        }

        AlertDialog(
            onDismissRequest = { showInterestsDialog = false },
            title = { Text("Seus Interesses de Leitura") },
            text = {
                Column {
                    Text(
                        text = "O Gemini utiliza estas tags para afunilar as recomendações literárias:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    defaultInterests.forEach { tag ->
                        val isSelected = currentSelection.contains(tag)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    currentSelection = if (checked) {
                                        currentSelection + tag
                                    } else {
                                        currentSelection - tag
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = tag, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newString = currentSelection.joinToString(", ")
                        onUpdateGenres(newString)
                        onRefresh(newString)
                        showInterestsDialog = false
                    }
                ) {
                    Text("Salvar & Recalcular")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInterestsDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
