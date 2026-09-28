package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entity.BookEntity
import com.example.data.remote.IsbnBookResult
import com.example.data.remote.NotionExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    books: List<BookEntity>,
    searchQuery: String,
    selectedGenre: String,
    selectedStatus: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectGenre: (String) -> Unit,
    onSelectStatus: (String) -> Unit,
    onOpenBook: (BookEntity) -> Unit,
    onOpenIsbnDialog: () -> Unit,
    onOpenAnnotations: () -> Unit,
    onUpdateRating: (BookEntity, Int) -> Unit,
    onImportEpub: (android.net.Uri) -> Unit = {},
    onImportSampleEpub: () -> Unit = {},
    onOpenAnki: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val epubPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        uri?.let { onImportEpub(it) }
    }

    val genres = listOf("Todos", "EPUB", "Teologia", "Quadrinhos", "Terror", "Ficção")
    val statuses = listOf("Todos", "Lendo", "Quero Ler", "Lidos", "Favoritos")

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Lumina Reader",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Nuvem Ativa",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { epubPickerLauncher.launch(arrayOf("application/epub+zip", "application/zip", "*/*")) },
                        modifier = Modifier.testTag("action_import_epub")
                    ) {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = "Importar EPUB")
                    }
                    IconButton(
                        onClick = onOpenAnnotations,
                        modifier = Modifier.testTag("action_view_annotations")
                    ) {
                        Icon(imageVector = Icons.Default.FormatQuote, contentDescription = "Ver Anotações & Notion")
                    }
                    IconButton(
                        onClick = onOpenIsbnDialog,
                        modifier = Modifier.testTag("action_scan_isbn")
                    ) {
                        Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = "Buscar ISBN")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenIsbnDialog,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Novo Livro / ISBN") },
                modifier = Modifier.testTag("fab_add_book")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("search_books_input"),
                placeholder = { Text("Buscar por título, autor ou gênero…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpar")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            // EPUB Reader Quick Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Leitor de EPUB",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Importe .epub ou leia os clássicos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = { epubPickerLauncher.launch(arrayOf("application/epub+zip", "application/zip", "*/*")) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("button_import_epub_banner")
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Abrir .epub", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = onImportSampleEpub,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("button_demo_epub")
                        ) {
                            Text("Demo", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Genre filter chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(genres) { genre ->
                    FilterChip(
                        selected = selectedGenre == genre,
                        onClick = { onSelectGenre(genre) },
                        label = { Text(genre) }
                    )
                }
            }

            // Shelf status tabs
            ScrollableTabRow(
                selectedTabIndex = statuses.indexOf(selectedStatus).coerceAtLeast(0),
                edgePadding = 16.dp,
                divider = {}
            ) {
                statuses.forEach { status ->
                    Tab(
                        selected = selectedStatus == status,
                        onClick = { onSelectStatus(status) },
                        text = {
                            Text(
                                text = status,
                                fontWeight = if (selectedStatus == status) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Book Count & Summary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${books.size} livro(s) no acervo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Book List
            if (books.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Nenhum livro encontrado nesta estante.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Adicione títulos pelo código ISBN ou explore as sugestões do Gemini AI.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onOpenIsbnDialog) {
                            Text("Buscar Livro por ISBN")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(books, key = { it.id }) { book ->
                        BookCardItem(
                            book = book,
                            onOpen = { onOpenBook(book) },
                            onExportNotion = {
                                NotionExporter.copyToClipboard(
                                    context,
                                    NotionExporter.formatBookAnnotationsForNotion(book, emptyList()),
                                    "Ficha do livro copiada para o Notion!"
                                )
                            },
                            onRatingChange = { rating -> onUpdateRating(book, rating) },
                            onOpenAnki = onOpenAnki?.let { { it(book.id) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BookCardItem(
    book: BookEntity,
    onOpen: () -> Unit,
    onExportNotion: () -> Unit,
    onRatingChange: (Int) -> Unit,
    onOpenAnki: (() -> Unit)? = null
) {
    val progressPct = if (book.totalPages > 0) {
        ((book.currentPage.toFloat() / book.totalPages.toFloat()) * 100).toInt()
    } else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("book_card_${book.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Book Cover
            if (book.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = book.title,
                    modifier = Modifier
                        .width(76.dp)
                        .height(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                )
            } else {
                // Stylized Fallback Cover Card
                Box(
                    modifier = Modifier
                        .width(76.dp)
                        .height(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(android.graphics.Color.parseColor(book.coverColorHex)),
                                    Color(android.graphics.Color.parseColor(book.coverColorHex)).copy(alpha = 0.7f)
                                )
                            )
                        )
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = book.title.take(14),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Info Column
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = book.genre,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (book.genre.contains("EPUB", ignoreCase = true) || book.chapters.contains(",")) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "EPUB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Rating Stars
                    Row {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (star <= book.rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$star estrelas",
                                tint = if (star <= book.rating) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onRatingChange(star) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (book.isbn.isNotBlank()) {
                    Text(
                        text = "ISBN: ${book.isbn}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Reading Progress Bar
                LinearProgressIndicator(
                    progress = { if (book.totalPages > 0) book.currentPage.toFloat() / book.totalPages else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$progressPct% lido (${book.currentPage}/${book.totalPages} págs)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        onOpenAnki?.let {
                            IconButton(
                                onClick = it,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = "Cartões Anki",
                                    modifier = Modifier.size(17.dp),
                                    tint = Color(0xFF6366F1)
                                )
                            }
                        }

                        IconButton(
                            onClick = onExportNotion,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Exportar para Notion",
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = onOpen,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Ler",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
