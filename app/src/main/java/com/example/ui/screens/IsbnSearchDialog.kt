package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.remote.IsbnBookResult

@Composable
fun IsbnSearchDialog(
    isOpen: Boolean,
    isSearching: Boolean,
    searchResult: IsbnBookResult?,
    errorMessage: String?,
    onSearch: (String) -> Unit,
    onAddBook: (IsbnBookResult, String) -> Unit,
    onAddManual: (String, String, String, Int, String, String) -> Unit,
    onImportEpub: () -> Unit = {},
    onImportSampleEpub: () -> Unit = {},
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var query by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("TO_READ") }
    var showManualForm by remember { mutableStateOf(false) }

    // Manual form fields
    var manualTitle by remember { mutableStateOf("") }
    var manualAuthor by remember { mutableStateOf("") }
    var manualGenre by remember { mutableStateOf("Teologia") }
    var manualPages by remember { mutableStateOf("200") }
    var manualDescription by remember { mutableStateOf("") }

    val quickIsbns = listOf(
        "9788563160270" to "Ortodoxia (Teologia)",
        "9788573264661" to "Lovecraft (Terror)",
        "9788573515084" to "Watchmen (Quadrinhos)",
        "9788535914849" to "1984 (Ficção)"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showManualForm) "Cadastro Manual" else "Busca por ISBN",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!showManualForm) {
                    // EPUB Import Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Leitor de Arquivos EPUB",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Abra e leia e-books em formato .epub diretamente no Rocky Reader.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onImportEpub,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("button_import_local_epub")
                                ) {
                                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Abrir .epub", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = onImportSampleEpub,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("button_import_sample_epub")
                                ) {
                                    Text("EPUB Exemplo", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text = " OU POR CÓDIGO ISBN ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search Bar
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("isbn_input_field"),
                        label = { Text("Digite o ISBN (10 ou 13 dígitos)") },
                        placeholder = { Text("Ex: 9788563160270") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { onSearch(query) },
                                enabled = query.isNotBlank() && !isSearching
                            ) {
                                Icon(imageVector = Icons.Default.Search, contentDescription = "Buscar")
                            }
                        },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { onSearch(query) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_isbn_button"),
                        enabled = query.isNotBlank() && !isSearching
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Buscando no Open Library…")
                        } else {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Consultar ISBN")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Exemplos rápidos de ISBN:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickIsbns.take(2).forEach { (isbn, label) ->
                            SuggestionChip(
                                onClick = {
                                    query = isbn
                                    onSearch(isbn)
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickIsbns.drop(2).forEach { (isbn, label) ->
                            SuggestionChip(
                                onClick = {
                                    query = isbn
                                    onSearch(isbn)
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Result Card
                    if (searchResult != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Cover thumbnail
                                if (searchResult.coverUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = searchResult.coverUrl,
                                        contentDescription = searchResult.title,
                                        modifier = Modifier
                                            .width(60.dp)
                                            .height(88.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.DarkGray)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .width(60.dp)
                                            .height(88.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = searchResult.title.take(1),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = searchResult.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = searchResult.author,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = searchResult.genre,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = "${searchResult.totalPages} págs",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Status Selector
                        Text("Adicionar à estante como:", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedStatus == "TO_READ",
                                onClick = { selectedStatus = "TO_READ" },
                                label = { Text("Quero Ler") }
                            )
                            FilterChip(
                                selected = selectedStatus == "READING",
                                onClick = { selectedStatus = "READING" },
                                label = { Text("Lendo") }
                            )
                            FilterChip(
                                selected = selectedStatus == "COMPLETED",
                                onClick = { selectedStatus = "COMPLETED" },
                                label = { Text("Lido") }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { onAddBook(searchResult, selectedStatus) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_add_isbn_button")
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Adicionar à Biblioteca")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = { showManualForm = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Não tem ISBN? Cadastrar manualmente")
                    }
                } else {
                    // Manual Form
                    OutlinedTextField(
                        value = manualTitle,
                        onValueChange = { manualTitle = it },
                        label = { Text("Título da Obra") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = manualAuthor,
                        onValueChange = { manualAuthor = it },
                        label = { Text("Autor") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Gênero Literário:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Teologia", "Quadrinhos", "Terror", "Ficção").forEach { g ->
                            FilterChip(
                                selected = manualGenre == g,
                                onClick = { manualGenre = g },
                                label = { Text(g, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = manualPages,
                        onValueChange = { manualPages = it.filter { char -> char.isDigit() } },
                        label = { Text("Total de Páginas") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = manualDescription,
                        onValueChange = { manualDescription = it },
                        label = { Text("Sinopse ou Notas (Opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val pages = manualPages.toIntOrNull() ?: 150
                            onAddManual(manualTitle, manualAuthor, manualGenre, pages, manualDescription, selectedStatus)
                        },
                        enabled = manualTitle.isNotBlank() && manualAuthor.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirm_manual_book_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Salvar Livro")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { showManualForm = false },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Voltar para busca por ISBN")
                    }
                }
            }
        }
    }
}
