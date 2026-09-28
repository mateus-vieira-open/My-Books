package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthManager
import com.example.data.auth.AuthState
import com.example.data.epub.EpubParser
import com.example.data.local.entity.AnnotationEntity
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.FlashcardEntity
import com.example.data.local.entity.ReadingGoalEntity
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.remote.BookRecommendation
import com.example.data.remote.GeminiService
import com.example.data.remote.IsbnBookResult
import com.example.data.remote.IsbnService
import com.example.data.remote.NotionExporter
import com.example.data.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class Screen {
    data class OnboardingQuiz(val isRetake: Boolean = false) : Screen()
    object Library : Screen()
    data class Reader(val bookId: Long) : Screen()
    object DiscoverAi : Screen()
    object Gamification : Screen()
    object SyncProfile : Screen()
    object Annotations : Screen()
    data class Flashcards(val initialBookId: Long? = null, val initialChapter: String? = null) : Screen()
}

class LuminaViewModel(
    private val repository: BookRepository,
    private val authManager: AuthManager
) : ViewModel() {

    // Auth State via Firebase & Credential Manager
    val authState: StateFlow<AuthState> = authManager.authState

    // Navigation State
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Library)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Navigation History for back handling
    private val screenStack = mutableListOf<Screen>(Screen.Library)

    // Books and Filters
    val allBooks: StateFlow<List<BookEntity>> = repository.allBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAnnotations: StateFlow<List<AnnotationEntity>> = repository.allAnnotations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSessions: StateFlow<List<ReadingSessionEntity>> = repository.recentSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFlashcards: StateFlow<List<FlashcardEntity>> = repository.allFlashcards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfile: StateFlow<UserProfileEntity?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val readingGoal: StateFlow<ReadingGoalEntity?> = repository.readingGoal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGenre = MutableStateFlow("Todos")
    val selectedGenre: StateFlow<String> = _selectedGenre.asStateFlow()

    private val _selectedStatus = MutableStateFlow("Todos")
    val selectedStatus: StateFlow<String> = _selectedStatus.asStateFlow()

    // Filtered Books
    val filteredBooks: StateFlow<List<BookEntity>> = combine(
        allBooks,
        searchQuery,
        selectedGenre,
        selectedStatus
    ) { books, query, genre, status ->
        books.filter { book ->
            val matchesQuery = query.isBlank() ||
                    book.title.contains(query, ignoreCase = true) ||
                    book.author.contains(query, ignoreCase = true) ||
                    book.genre.contains(query, ignoreCase = true)

            val matchesGenre = genre == "Todos" || book.genre.equals(genre, ignoreCase = true)

            val matchesStatus = when (status) {
                "Todos" -> true
                "Lendo" -> book.status == "READING"
                "Quero Ler" -> book.status == "TO_READ"
                "Lidos" -> book.status == "COMPLETED"
                "Favoritos" -> book.status == "FAVORITE" || book.rating == 5
                else -> true
            }

            matchesQuery && matchesGenre && matchesStatus
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Reader State
    private val _activeBook = MutableStateFlow<BookEntity?>(null)
    val activeBook: StateFlow<BookEntity?> = _activeBook.asStateFlow()

    // ISBN Search State
    private val _isbnSearchResult = MutableStateFlow<IsbnBookResult?>(null)
    val isbnSearchResult: StateFlow<IsbnBookResult?> = _isbnSearchResult.asStateFlow()

    private val _isSearchingIsbn = MutableStateFlow(false)
    val isSearchingIsbn: StateFlow<Boolean> = _isSearchingIsbn.asStateFlow()

    private val _isbnSearchMessage = MutableStateFlow<String?>(null)
    val isbnSearchMessage: StateFlow<String?> = _isbnSearchMessage.asStateFlow()

    // AI Recommendations State
    private val _recommendations = MutableStateFlow<List<BookRecommendation>>(emptyList())
    val recommendations: StateFlow<List<BookRecommendation>> = _recommendations.asStateFlow()

    private val _isLoadingAi = MutableStateFlow(false)
    val isLoadingAi: StateFlow<Boolean> = _isLoadingAi.asStateFlow()

    // Sync State
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncSuccessMessage = MutableStateFlow<String?>(null)
    val syncSuccessMessage: StateFlow<String?> = _syncSuccessMessage.asStateFlow()

    init {
        // Pre-fetch AI recommendations on startup
        refreshRecommendations()

        // If user hasn't completed initial onboarding survey, display Onboarding Quiz
        viewModelScope.launch {
            repository.userProfile.collect { profile ->
                if (profile != null && !profile.isOnboardingCompleted && _currentScreen.value !is Screen.OnboardingQuiz) {
                    _currentScreen.value = Screen.OnboardingQuiz(isRetake = false)
                }
            }
        }
    }

    // Onboarding and Reader Evolution Quiz Actions
    fun openQuizRetake() {
        navigateTo(Screen.OnboardingQuiz(isRetake = true))
    }

    fun completeOnboardingQuiz(
        selectedGenres: List<String>,
        readingPaceWeeklyPages: Int,
        context: Context? = null
    ) {
        viewModelScope.launch {
            val current = userProfile.value ?: UserProfileEntity()
            val genresString = selectedGenres.joinToString(", ")
            val nextRetakeCount = current.quizRetakeCount + 1

            val newStage = when {
                nextRetakeCount >= 3 -> "Mestre Literário 📚"
                nextRetakeCount >= 2 -> "Leitor Connoisseur 🌟"
                nextRetakeCount >= 1 -> "Leitor Voraz 🔥"
                else -> "Leitor Explorador ✨"
            }

            repository.updateProfile(
                current.copy(
                    preferredGenres = genresString,
                    isOnboardingCompleted = true,
                    quizRetakeCount = nextRetakeCount,
                    readerEvolutionStage = newStage,
                    lastSyncTimestamp = System.currentTimeMillis()
                )
            )

            val currentGoal = readingGoal.value ?: ReadingGoalEntity()
            repository.updateGoal(
                currentGoal.copy(
                    weeklyTargetPages = readingPaceWeeklyPages,
                    readerLevel = newStage
                )
            )

            // Recalibrate Gemini AI recommendations immediately with updated genres
            refreshRecommendations(genresString)

            // Navigate to Discover AI so user immediately sees their tailored recommendations
            navigateTo(Screen.DiscoverAi)

            context?.let {
                Toast.makeText(
                    it,
                    "Interesses atualizados! O Gemini gerou recomendações personalizadas.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Navigation Controls
    fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            screenStack.add(screen)
            _currentScreen.value = screen
        }
    }

    fun handleBack(): Boolean {
        if (screenStack.size > 1) {
            screenStack.removeAt(screenStack.size - 1)
            val previous = screenStack.last()
            _currentScreen.value = previous
            return true
        }
        return false
    }

    // Filter Controls
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setGenreFilter(genre: String) {
        _selectedGenre.value = genre
    }

    fun setStatusFilter(status: String) {
        _selectedStatus.value = status
    }

    // Reader Actions
    fun openReader(book: BookEntity) {
        _activeBook.value = book
        navigateTo(Screen.Reader(book.id))
    }

    fun updateReadingProgress(bookId: Long, newPage: Int) {
        viewModelScope.launch {
            repository.updateReadingProgress(bookId, newPage)
            _activeBook.value = _activeBook.value?.copy(
                currentPage = newPage,
                lastReadTimestamp = System.currentTimeMillis()
            )
        }
    }

    // Real-Time Reading Timer Session Completion & Goal Progress
    fun finishReadingSession(
        bookId: Long,
        bookTitle: String,
        durationSeconds: Long,
        pagesRead: Int,
        wordsRead: Int,
        speedWpm: Int,
        onComplete: ((ReadingSessionEntity) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val session = repository.recordReadingSession(
                bookId = bookId,
                bookTitle = bookTitle,
                durationSeconds = durationSeconds,
                pagesRead = pagesRead,
                wordsRead = wordsRead,
                speedWpm = speedWpm
            )
            onComplete?.invoke(session)
        }
    }

    // --- Anki-Style Chapter Flashcards Powered by Gemini ---
    private val _isGeneratingFlashcards = MutableStateFlow(false)
    val isGeneratingFlashcards: StateFlow<Boolean> = _isGeneratingFlashcards.asStateFlow()

    private val _flashcardMessage = MutableStateFlow<String?>(null)
    val flashcardMessage: StateFlow<String?> = _flashcardMessage.asStateFlow()

    fun clearFlashcardMessage() {
        _flashcardMessage.value = null
    }

    fun openFlashcards(bookId: Long? = null, chapterTitle: String? = null) {
        navigateTo(Screen.Flashcards(initialBookId = bookId, initialChapter = chapterTitle))
    }

    fun generateChapterFlashcards(
        bookId: Long,
        bookTitle: String,
        author: String,
        chapterTitle: String,
        chapterSnippet: String,
        sourceType: String = "APP",
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isGeneratingFlashcards.value = true
            _flashcardMessage.value = null
            try {
                val generated = GeminiService.generateChapterFlashcards(
                    bookTitle = bookTitle,
                    author = author,
                    chapterTitle = chapterTitle,
                    chapterSnippet = chapterSnippet,
                    sourceType = sourceType
                )
                val entities = generated.map { item ->
                    FlashcardEntity(
                        bookId = bookId,
                        bookTitle = bookTitle,
                        chapterTitle = chapterTitle,
                        frontQuestion = item.frontQuestion,
                        backAnswer = item.backAnswer,
                        keyQuote = item.keyQuote,
                        sourceType = sourceType,
                        masteryLevel = "NOVO",
                        reviewCount = 0
                    )
                }
                repository.insertFlashcards(entities)
                _flashcardMessage.value = "${entities.size} cartões Anki gerados pelo Gemini para '$chapterTitle'!"
                onComplete?.invoke()
            } catch (e: Exception) {
                _flashcardMessage.value = "Erro ao gerar flashcards: ${e.message}"
            } finally {
                _isGeneratingFlashcards.value = false
            }
        }
    }

    fun generateExternalBookFlashcards(
        bookTitle: String,
        author: String,
        chapterTitle: String,
        sourceType: String, // "FISICO" or "KINDLE"
        onComplete: (() -> Unit)? = null
    ) {
        if (bookTitle.isBlank() || chapterTitle.isBlank()) return
        viewModelScope.launch {
            _isGeneratingFlashcards.value = true
            _flashcardMessage.value = null
            try {
                val generated = GeminiService.generateChapterFlashcards(
                    bookTitle = bookTitle,
                    author = author.ifBlank { "Autor da Obra" },
                    chapterTitle = chapterTitle,
                    chapterSnippet = "",
                    sourceType = sourceType
                )
                val entities = generated.map { item ->
                    FlashcardEntity(
                        bookId = 0L,
                        bookTitle = bookTitle,
                        chapterTitle = chapterTitle,
                        frontQuestion = item.frontQuestion,
                        backAnswer = item.backAnswer,
                        keyQuote = item.keyQuote,
                        sourceType = sourceType,
                        masteryLevel = "NOVO",
                        reviewCount = 0
                    )
                }
                repository.insertFlashcards(entities)
                _flashcardMessage.value = "${entities.size} cartões Anki gerados pelo Gemini para $sourceType!"
                onComplete?.invoke()
            } catch (e: Exception) {
                _flashcardMessage.value = "Falha ao consultar obra no Gemini: ${e.message}"
            } finally {
                _isGeneratingFlashcards.value = false
            }
        }
    }

    fun reviewFlashcard(card: FlashcardEntity, grade: String) {
        viewModelScope.launch {
            val (newInterval, newEase, newMastery) = when (grade) {
                "ERRO" -> Triple(1, (card.easeFactor - 0.2f).coerceAtLeast(1.3f), "APRENDENDO")
                "DIFICIL" -> Triple((card.repetitionIntervalDays * 1.2).toInt().coerceAtLeast(1), (card.easeFactor - 0.15f).coerceAtLeast(1.3f), "APRENDENDO")
                "BOM" -> Triple((card.repetitionIntervalDays * card.easeFactor).toInt().coerceAtLeast(2), card.easeFactor, "BOM")
                "FACIL" -> Triple((card.repetitionIntervalDays * card.easeFactor * 1.5).toInt().coerceAtLeast(4), (card.easeFactor + 0.15f).coerceAtMost(3.0f), "DOMINADO")
                else -> Triple(card.repetitionIntervalDays, card.easeFactor, card.masteryLevel)
            }
            repository.updateFlashcard(
                card.copy(
                    repetitionIntervalDays = newInterval,
                    easeFactor = newEase,
                    masteryLevel = newMastery,
                    reviewCount = card.reviewCount + 1,
                    lastReviewedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteFlashcard(card: FlashcardEntity) {
        viewModelScope.launch {
            repository.deleteFlashcard(card)
        }
    }

    fun addAnnotation(
        bookId: Long,
        selectedText: String,
        noteText: String,
        highlightColorHex: String,
        chapterTitle: String,
        pageNumber: Int
    ) {
        viewModelScope.launch {
            val book = allBooks.value.firstOrNull { it.id == bookId } ?: return@launch
            val annotation = AnnotationEntity(
                bookId = bookId,
                bookTitle = book.title,
                bookAuthor = book.author,
                selectedText = selectedText,
                noteText = noteText,
                highlightColorHex = highlightColorHex,
                chapterTitle = chapterTitle,
                pageNumber = pageNumber
            )
            repository.insertAnnotation(annotation)
        }
    }

    fun deleteAnnotation(annotation: AnnotationEntity) {
        viewModelScope.launch {
            repository.deleteAnnotation(annotation)
        }
    }

    // ISBN Search Actions
    fun searchIsbn(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isSearchingIsbn.value = true
            _isbnSearchMessage.value = null
            _isbnSearchResult.value = null

            val result = IsbnService.lookupBookByIsbn(query)
            result.onSuccess {
                _isbnSearchResult.value = it
            }.onFailure { error ->
                _isbnSearchMessage.value = error.message ?: "Livro não encontrado."
            }
            _isSearchingIsbn.value = false
        }
    }

    fun clearIsbnResult() {
        _isbnSearchResult.value = null
        _isbnSearchMessage.value = null
    }

    fun addBookFromIsbn(result: IsbnBookResult, status: String = "TO_READ") {
        viewModelScope.launch {
            val book = BookEntity(
                title = result.title,
                author = result.author,
                isbn = result.isbn,
                genre = result.genre,
                description = result.description,
                coverUrl = result.coverUrl,
                coverColorHex = result.coverColorHex,
                totalPages = result.totalPages,
                currentPage = 0,
                status = status,
                rating = 0,
                chapters = "Capítulo 1, Capítulo 2, Capítulo 3",
                content = """
Capítulo 1: Início da Obra

Este e-book foi catalogado no Lumina Reader através do código ISBN ${result.isbn}.
O conteúdo completo pode ser sincronizado com a sua conta ou lido diretamente neste leitor integrado.

Aproveite para destacar trechos, fazer anotações de margem e exportar citações para o seu Notion!
                """.trimIndent()
            )
            val newId = repository.insertBook(book)
            clearIsbnResult()
        }
    }

    fun addManualBook(
        title: String,
        author: String,
        genre: String,
        totalPages: Int,
        description: String,
        status: String
    ) {
        viewModelScope.launch {
            val book = BookEntity(
                title = title,
                author = author,
                genre = genre,
                description = description,
                totalPages = totalPages.coerceAtLeast(1),
                currentPage = 0,
                status = status,
                rating = 0,
                chapters = "Capítulo 1, Capítulo 2",
                content = """
Capítulo 1

Início da leitura de $title, por $author.
Utilize a ferramenta de seleção para salvar suas citações favoritas e sincronizar com seu Notion.
                """.trimIndent()
            )
            repository.insertBook(book)
        }
    }

    // AI Recommendations
    fun refreshRecommendations(customInterests: String? = null) {
        viewModelScope.launch {
            _isLoadingAi.value = true
            val profile = userProfile.value
            val genres = customInterests ?: profile?.preferredGenres ?: "Teologia, Quadrinhos, Terror, Ficção"
            val books = allBooks.value

            val recs = GeminiService.getRecommendations(genres, books)
            _recommendations.value = recs
            _isLoadingAi.value = false
        }
    }

    fun addRecommendationToLibrary(rec: BookRecommendation) {
        viewModelScope.launch {
            val book = BookEntity(
                title = rec.title,
                author = rec.author,
                isbn = rec.isbn,
                genre = rec.genre,
                description = rec.synopsis,
                coverColorHex = rec.coverColorHex,
                totalPages = rec.estimatedPages,
                currentPage = 0,
                status = "TO_READ",
                rating = 0,
                chapters = "Capítulo 1, Capítulo 2",
                content = """
Capítulo 1

${rec.synopsis}

Recomendação inteligente via Gemini AI:
"${rec.whyRecommend}"
                """.trimIndent()
            )
            repository.insertBook(book)
        }
    }

    // Notion Export Actions
    fun exportSingleAnnotationToNotion(context: Context, annotation: AnnotationEntity) {
        viewModelScope.launch {
            val markdown = NotionExporter.formatSingleAnnotationForNotion(annotation)
            NotionExporter.copyToClipboard(context, markdown, "Citação formatada para Notion copiada!")
            repository.markAnnotationExported(annotation.id, true)
        }
    }

    fun shareSingleAnnotation(context: Context, annotation: AnnotationEntity) {
        val markdown = NotionExporter.formatSingleAnnotationForNotion(annotation)
        NotionExporter.shareExport(context, markdown, annotation.bookTitle)
    }

    fun exportBookAnnotationsToNotion(context: Context, book: BookEntity) {
        viewModelScope.launch {
            val notes = allAnnotations.value.filter { it.bookId == book.id }
            val markdown = NotionExporter.formatBookAnnotationsForNotion(book, notes)
            NotionExporter.copyToClipboard(context, markdown, "Caderno do livro pronto para colar no Notion!")
            repository.markAllAnnotationsExportedForBook(book.id)
        }
    }

    fun shareBookAnnotations(context: Context, book: BookEntity) {
        val notes = allAnnotations.value.filter { it.bookId == book.id }
        val markdown = NotionExporter.formatBookAnnotationsForNotion(book, notes)
        NotionExporter.shareExport(context, markdown, book.title)
    }

    // Cloud Sync and Gmail
    fun triggerCloudSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncSuccessMessage.value = null
            kotlinx.coroutines.delay(1000) // Realistic smooth sync animation
            repository.triggerSync()
            _isSyncing.value = false
            _syncSuccessMessage.value = "Sincronizado com a nuvem com sucesso! Progresso e anotações atualizados."
        }
    }

    fun sendReadingSummaryToGmail(context: Context) {
        val profile = userProfile.value
        val goal = readingGoal.value
        val books = allBooks.value
        val notes = allAnnotations.value

        val readingBooks = books.filter { it.status == "READING" }
        val recipient = profile?.email ?: "ma2001teus23@gmail.com"

        val body = buildString {
            appendLine("Olá, ${profile?.name ?: "Mateus"}!")
            appendLine("Aqui está o seu Resumo Semanal de Leitura do Lumina Reader:")
            appendLine()
            appendLine("📊 DESEMPENHO DA SEMANA:")
            appendLine("- Páginas lidas: ${goal?.weeklyPagesRead ?: 0} / ${goal?.weeklyTargetPages ?: 150} páginas")
            appendLine("- Sequência ativa (Streak): ${goal?.dailyStreak ?: 0} dias consecutivos 🔥")
            appendLine("- Nível atual: ${goal?.readerLevel ?: "Leitor Voraz"}")
            appendLine()
            appendLine("📖 LIVROS EM ANDAMENTO:")
            readingBooks.forEach {
                val pct = if (it.totalPages > 0) (it.currentPage * 100 / it.totalPages) else 0
                appendLine("• ${it.title} (${it.author}) - $pct% concluído (pág. ${it.currentPage}/${it.totalPages})")
            }
            appendLine()
            appendLine("💡 ANOTAÇÕES RECENTES (${notes.size}):")
            notes.take(3).forEach {
                appendLine("• \"${it.selectedText.take(80)}...\" [${it.bookTitle}]")
            }
            appendLine()
            appendLine("Seus dados estão 100% sincronizados na nuvem do Lumina Reader.")
            appendLine("Continue firme para bater a meta semanal!")
        }

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$recipient")
            putExtra(Intent.EXTRA_SUBJECT, "Lumina Reader - Seu Resumo Semanal & Lembrete de Leitura")
            putExtra(Intent.EXTRA_TEXT, body)
        }

        try {
            val chooser = Intent.createChooser(intent, "Enviar Resumo para Gmail")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Resumo preparado para $recipient", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateProfile(name: String, email: String, preferredGenres: String, reminderTime: String) {
        viewModelScope.launch {
            val current = userProfile.value ?: UserProfileEntity()
            repository.updateProfile(
                current.copy(
                    name = name,
                    email = email,
                    preferredGenres = preferredGenres,
                    dailyReminderTime = reminderTime
                )
            )
        }
    }

    fun updateGoal(targetPages: Int) {
        viewModelScope.launch {
            val current = readingGoal.value ?: ReadingGoalEntity()
            repository.updateGoal(current.copy(weeklyTargetPages = targetPages))
        }
    }

    fun updateBookStatus(book: BookEntity, newStatus: String) {
        viewModelScope.launch {
            repository.updateBook(book.copy(status = newStatus))
        }
    }

    fun updateBookRating(book: BookEntity, rating: Int) {
        viewModelScope.launch {
            repository.updateBook(book.copy(rating = rating))
        }
    }

    // Google Sign-In with Credential Manager & Firebase Auth
    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            val result = authManager.signInWithGoogle(context)
            result.onSuccess { user ->
                val profile = userProfile.value
                if (profile != null) {
                    repository.updateProfile(
                        profile.copy(
                            name = user.displayName,
                            email = user.email
                        )
                    )
                }
                Toast.makeText(context, "Conectado como ${user.displayName}!", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(context, error.message ?: "Erro ao autenticar com Google", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
        }
    }

    // EPUB Digital Reader Import & Processing
    fun importEpubFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            Toast.makeText(context, "Processando arquivo EPUB...", Toast.LENGTH_SHORT).show()
            val result = EpubParser.parseFromUri(context, uri)
            result.onSuccess { epub ->
                val fullContent = epub.chapters.joinToString("\n\n") { chapter ->
                    "## ${chapter.title}\n\n${chapter.plainText}"
                }
                val chapterNames = epub.chapters.joinToString(", ") { it.title }
                val estimatedPages = (fullContent.length / 1000).coerceAtLeast(epub.chapters.size * 2)

                val book = BookEntity(
                    title = epub.title,
                    author = epub.author,
                    genre = "EPUB",
                    description = epub.description,
                    coverColorHex = "#0284C7",
                    totalPages = estimatedPages,
                    currentPage = 1,
                    status = "READING",
                    rating = 5,
                    chapters = chapterNames,
                    content = fullContent,
                    lastReadTimestamp = System.currentTimeMillis()
                )
                val id = repository.insertBook(book)
                val insertedBook = book.copy(id = id)
                openReader(insertedBook)
                Toast.makeText(context, "EPUB '${epub.title}' importado com sucesso!", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(context, "Falha ao importar EPUB: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun importSampleEpub(context: Context) {
        viewModelScope.launch {
            val book = BookEntity(
                title = "Dom Casmurro (EPUB)",
                author = "Machado de Assis",
                genre = "EPUB",
                description = "Edição digital completa em formato EPUB estruturada por capítulos com o enigma imortal de Bentinho e Capitu.",
                coverColorHex = "#0E7490",
                totalPages = 256,
                currentPage = 1,
                status = "READING",
                rating = 5,
                chapters = "I. Do Título, II. Do Livro, III. A Denúncia, IV. Um Dever Severo, V. O Segredo de Capitu",
                content = """
## Capítulo I: Do Título

Uma noite destas, vindo da cidade para o Engenho Novo, encontrei no trem da Central um rapaz aqui do bairro, que eu conheço de vista e de chapéu. Cumprimentou-me, sentou-se ao pé de mim, falou da lua e dos ministros, e acabou recitando-me versos. A viagem era curta, e os versos pode ser que não fossem inteiramente maus, porém o caso é que o sono foi mais forte do que a poesia.

Ao acordar, não achei mais o rapaz; e os passageiros que lá ficavam, rindo disfarçadamente, pareciam achar engraçada a minha soneca. No dia seguinte entraram a chamar-me de Dom Casmurro.

## Capítulo II: Do Livro

Agora que expliquei o título, passo a escrever o livro. Por que o faço? Porque não sei que melhor negócio dar a estes dias de retiro. A velhice tem destas coisas: traz a vontade de catar lembranças e de as reconstruir como eram na mocidade.

Meu fim evidente era atar as duas pontas da vida, e restaurar na velhice a adolescência. Pois, senhor, não consegui recompor o que foi nem o que fui. Em tudo, se o rosto é igual, a fisionomia é diferente. Se só me faltassem os outros, vá; um homem consola-se mais ou menos das pessoas que perde; mas falto eu mesmo, e esta lacuna é tudo.

## Capítulo III: A Denúncia

Ia a entrar na sala de visitas, quando ouvi proferir o meu nome e escondi-me atrás da porta. A casa era a da Rua de Matacavalos, o mês novembro, o ano é que é um tanto remoto, mas eu não hei de trocar as datas da minha vida só para agradar aos que não amam histórias velhas; o ano era 1857.

— D. Glória, a senhora persiste na ideia de meter o nosso Bentinho no seminário? É mais que tempo, e já agora pode haver uma dificuldade.

— Que dificuldade?

— Uma grande dificuldade. José Dias suspendeu a frase, olhou para a minha mãe com aquele ar de gravidade que lhe era peculiar.

## Capítulo IV: Um Dever Severo

Minha mãe ficou pálida. Prometera a Deus, antes de eu nascer, que se tivesse um varão iria dá-lo à Igreja. Essa promessa era para ela um peso de chumbo na consciência e uma flor de devoção no altar.

— A dificuldade são os olhos da menina de Pádua. Capitu anda muito achegada ao Bentinho. Vivem aos segredinhos pelo quintal, colhendo pitangas e desenhando no muro. Se o menino for para o seminário agora, tudo se ajeita. Se demorar mais um ano, receio que o laço fique atado de vez.

## Capítulo V: O Segredo de Capitu

Capitu tinha então quatorze anos. Olhos de ressaca, olhos de cigana oblíqua e dissimulada. Quando nos encontramos no muro do fundo, contei-lhe o que ouvira sobre o seminário e o padre.

Ela mordeu os lábios, fitou-me com aquela profundidade serena que me assustava e fascinava ao mesmo tempo, e disse baixinho:

— Não chores, Bentinho. Eles querem te mandar para São Leopoldo, mas nós não deixaremos. Hás de ser doutor, e não padre. Confia em mim.
                """.trimIndent(),
                lastReadTimestamp = System.currentTimeMillis()
            )
            val id = repository.insertBook(book)
            openReader(book.copy(id = id))
            Toast.makeText(context, "Livro EPUB clássico carregado no leitor!", Toast.LENGTH_SHORT).show()
        }
    }
}

class LuminaViewModelFactory(
    private val repository: BookRepository,
    private val authManager: AuthManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LuminaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LuminaViewModel(repository, authManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
