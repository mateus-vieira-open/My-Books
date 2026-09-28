package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.BookEntity
import com.example.ui.components.LuminaBottomBar
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.LuminaViewModel
import com.example.viewmodel.LuminaViewModelFactory
import com.example.viewmodel.Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as LuminaApp
        val viewModel: LuminaViewModel by viewModels {
            LuminaViewModelFactory(app.repository, app.authManager)
        }

        setContent {
            MyApplicationTheme {
                LuminaAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun LuminaAppContent(viewModel: LuminaViewModel) {
    val context = LocalContext.current

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val filteredBooks by viewModel.filteredBooks.collectAsStateWithLifecycle()
    val allBooks by viewModel.allBooks.collectAsStateWithLifecycle()
    val allAnnotations by viewModel.allAnnotations.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val readingGoal by viewModel.readingGoal.collectAsStateWithLifecycle()
    val activeBook by viewModel.activeBook.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()

    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val isLoadingAi by viewModel.isLoadingAi.collectAsStateWithLifecycle()

    val isSearchingIsbn by viewModel.isSearchingIsbn.collectAsStateWithLifecycle()
    val isbnSearchResult by viewModel.isbnSearchResult.collectAsStateWithLifecycle()
    val isbnSearchMessage by viewModel.isbnSearchMessage.collectAsStateWithLifecycle()

    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncSuccessMessage by viewModel.syncSuccessMessage.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    val recentSessions by viewModel.recentSessions.collectAsStateWithLifecycle()
    val allFlashcards by viewModel.allFlashcards.collectAsStateWithLifecycle()
    val isGeneratingFlashcards by viewModel.isGeneratingFlashcards.collectAsStateWithLifecycle()
    val flashcardMessage by viewModel.flashcardMessage.collectAsStateWithLifecycle()

    var showIsbnDialog by remember { mutableStateOf(false) }

    val epubPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importEpubFromUri(context, it) }
    }

    // Intercept back presses for nested navigation
    BackHandler(enabled = currentScreen !is Screen.Library && (currentScreen as? Screen.OnboardingQuiz)?.isRetake != false) {
        if (!viewModel.handleBack()) {
            viewModel.navigateTo(Screen.Library)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // Hide bottom bar inside Reader or Onboarding Quiz for full immersive experience
            if (currentScreen !is Screen.Reader && currentScreen !is Screen.OnboardingQuiz) {
                LuminaBottomBar(
                    currentScreen = currentScreen,
                    onNavigate = { target -> viewModel.navigateTo(target) }
                )
            }
        }
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)

        when (val screen = currentScreen) {
            is Screen.OnboardingQuiz -> {
                OnboardingQuizScreen(
                    userProfile = userProfile,
                    isRetake = screen.isRetake,
                    onComplete = { genres, pace ->
                        viewModel.completeOnboardingQuiz(genres, pace, context)
                    },
                    onDismissOrSkip = {
                        if (!viewModel.handleBack()) {
                            viewModel.navigateTo(Screen.Library)
                        }
                    },
                    modifier = modifier
                )
            }

            is Screen.Library -> {
                LibraryScreen(
                    books = filteredBooks,
                    searchQuery = searchQuery,
                    selectedGenre = selectedGenre,
                    selectedStatus = selectedStatus,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onSelectGenre = { viewModel.setGenreFilter(it) },
                    onSelectStatus = { viewModel.setStatusFilter(it) },
                    onOpenBook = { book -> viewModel.openReader(book) },
                    onOpenIsbnDialog = { showIsbnDialog = true },
                    onOpenAnnotations = { viewModel.navigateTo(Screen.Annotations) },
                    onUpdateRating = { book, rating -> viewModel.updateBookRating(book, rating) },
                    onImportEpub = { uri -> viewModel.importEpubFromUri(context, uri) },
                    onImportSampleEpub = { viewModel.importSampleEpub(context) },
                    onOpenAnki = { bookId -> viewModel.openFlashcards(bookId) },
                    modifier = modifier
                )
            }

            is Screen.Reader -> {
                val bookToRead = activeBook ?: allBooks.firstOrNull { it.id == screen.bookId } ?: allBooks.firstOrNull()
                val bookAnnotations = allAnnotations.filter { it.bookId == (bookToRead?.id ?: 0L) }

                ReaderScreen(
                    book = bookToRead,
                    annotations = bookAnnotations,
                    onBack = {
                        if (!viewModel.handleBack()) {
                            viewModel.navigateTo(Screen.Library)
                        }
                    },
                    onProgressChange = { page ->
                        bookToRead?.let { viewModel.updateReadingProgress(it.id, page) }
                    },
                    onAddAnnotation = { selectedText, note, color, page, chapter ->
                        bookToRead?.let {
                            viewModel.addAnnotation(it.id, selectedText, note, color, chapter, page)
                        }
                    },
                    onDeleteAnnotation = { note -> viewModel.deleteAnnotation(note) },
                    onFinishSession = { durationSeconds, pagesRead, wordsRead, speedWpm ->
                        bookToRead?.let {
                            viewModel.finishReadingSession(it.id, it.title, durationSeconds, pagesRead, wordsRead, speedWpm)
                        }
                    },
                    onGenerateAnkiForChapter = { bookId, bookTitle, author, chapterTitle, snippet ->
                        viewModel.generateChapterFlashcards(
                            bookId = bookId,
                            bookTitle = bookTitle,
                            author = author,
                            chapterTitle = chapterTitle,
                            chapterSnippet = snippet,
                            onComplete = { viewModel.openFlashcards(bookId, chapterTitle) }
                        )
                    }
                )
            }

            is Screen.Flashcards -> {
                FlashcardsScreen(
                    flashcards = allFlashcards,
                    isGenerating = isGeneratingFlashcards,
                    generationMessage = flashcardMessage,
                    initialBookId = screen.initialBookId,
                    initialChapter = screen.initialChapter,
                    onBack = {
                        if (!viewModel.handleBack()) {
                            viewModel.navigateTo(Screen.Library)
                        }
                    },
                    onReviewCard = { card, grade -> viewModel.reviewFlashcard(card, grade) },
                    onDeleteCard = { card -> viewModel.deleteFlashcard(card) },
                    onGenerateFromExternalBook = { title, author, chapter, source ->
                        viewModel.generateExternalBookFlashcards(title, author, chapter, source)
                    },
                    onClearMessage = { viewModel.clearFlashcardMessage() },
                    modifier = modifier
                )
            }

            is Screen.DiscoverAi -> {
                GeminiRecommendationsScreen(
                    userProfile = userProfile,
                    currentBooks = allBooks,
                    recommendations = recommendations,
                    isLoading = isLoadingAi,
                    onRefresh = { customGenres -> viewModel.refreshRecommendations(customGenres) },
                    onAddToShelf = { rec -> viewModel.addRecommendationToLibrary(rec) },
                    onUpdateGenres = { genres ->
                        userProfile?.let {
                            viewModel.updateProfile(it.name, it.email, genres, it.dailyReminderTime)
                        }
                    },
                    onOpenQuizRetake = { viewModel.openQuizRetake() },
                    modifier = modifier
                )
            }

            is Screen.Gamification -> {
                GamificationScreen(
                    readingGoal = readingGoal,
                    recentSessions = recentSessions,
                    onUpdateTarget = { target -> viewModel.updateGoal(target) },
                    onOpenQuizRetake = { viewModel.openQuizRetake() },
                    onOpenAnki = { viewModel.openFlashcards() },
                    modifier = modifier
                )
            }

            is Screen.SyncProfile -> {
                SyncAndProfileScreen(
                    userProfile = userProfile,
                    books = allBooks,
                    annotations = allAnnotations,
                    isSyncing = isSyncing,
                    syncSuccessMessage = syncSuccessMessage,
                    authState = authState,
                    onSignInWithGoogle = { viewModel.signInWithGoogle(context) },
                    onSignOut = { viewModel.signOut() },
                    onTriggerSync = { viewModel.triggerCloudSync() },
                    onSendGmailSummary = { viewModel.sendReadingSummaryToGmail(context) },
                    onUpdateProfile = { name, email, genres, reminder ->
                        viewModel.updateProfile(name, email, genres, reminder)
                    },
                    onOpenQuizRetake = { viewModel.openQuizRetake() },
                    modifier = modifier
                )
            }

            is Screen.Annotations -> {
                AnnotationsListScreen(
                    annotations = allAnnotations,
                    onBack = {
                        if (!viewModel.handleBack()) {
                            viewModel.navigateTo(Screen.Library)
                        }
                    },
                    onDeleteAnnotation = { note -> viewModel.deleteAnnotation(note) },
                    modifier = modifier
                )
            }
        }
    }

    // ISBN Search & Registration Dialog
    if (showIsbnDialog) {
        IsbnSearchDialog(
            isOpen = showIsbnDialog,
            isSearching = isSearchingIsbn,
            searchResult = isbnSearchResult,
            errorMessage = isbnSearchMessage,
            onSearch = { query -> viewModel.searchIsbn(query) },
            onAddBook = { result, status ->
                viewModel.addBookFromIsbn(result, status)
                showIsbnDialog = false
            },
            onAddManual = { title, author, genre, pages, desc, status ->
                viewModel.addManualBook(title, author, genre, pages, desc, status)
                showIsbnDialog = false
            },
            onImportEpub = {
                epubPickerLauncher.launch(arrayOf("application/epub+zip", "application/zip", "*/*"))
                showIsbnDialog = false
            },
            onImportSampleEpub = {
                viewModel.importSampleEpub(context)
                showIsbnDialog = false
            },
            onDismiss = {
                viewModel.clearIsbnResult()
                showIsbnDialog = false
            }
        )
    }
}
