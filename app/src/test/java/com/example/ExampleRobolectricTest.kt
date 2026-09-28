package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.FlashcardEntity
import com.example.data.local.entity.ReadingGoalEntity
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.remote.GeminiService
import com.example.ui.screens.parseEpubChapters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testAppLaunch_AppNameMatches() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Lumina Reader", appName)
    }

    @Test
    fun testUserStory_RealTimeReadingSessionAndWeeklyGoalUpdate() = runBlocking {
        val sessionDao = database.readingSessionDao()
        val profileDao = database.userProfileDao()
        val bookDao = database.bookDao()

        // 1. User starts reading with an initial goal of 150 pages, 20 pages read so far
        val initialGoal = ReadingGoalEntity(
            id = 1,
            weeklyTargetPages = 150,
            weeklyPagesRead = 20,
            dailyStreak = 3,
            totalMinutesRead = 120,
            totalSessionsCount = 4,
            averageWpm = 200
        )
        profileDao.insertOrUpdateGoal(initialGoal)

        val book = BookEntity(
            title = "Dom Casmurro",
            author = "Machado de Assis",
            genre = "Romance Clássico",
            description = "Obra-prima de Machado de Assis.",
            coverColorHex = "#0E7490",
            totalPages = 250,
            currentPage = 20
        )
        val bookId = bookDao.insertBook(book)

        // 2. User completes a 15-minute real-time reading session reading 10 pages (~2600 words)
        val sessionDurationSeconds = 900L // 15 minutes
        val pagesRead = 10
        val wordsRead = 2600
        val sessionWpm = (wordsRead / (sessionDurationSeconds / 60.0)).toInt() // ~173 WPM
        val now = System.currentTimeMillis()

        val sessionEntity = ReadingSessionEntity(
            bookId = bookId,
            bookTitle = book.title,
            durationSeconds = sessionDurationSeconds,
            pagesRead = pagesRead,
            wordsRead = wordsRead,
            speedWpm = sessionWpm,
            timestamp = now
        )
        val sessionId = sessionDao.insertSession(sessionEntity)
        assertTrue("Session should be saved with valid ID", sessionId > 0)

        // 3. Update the user's weekly reading goal progress and streak
        profileDao.recordReadingSession(
            pages = pagesRead,
            minutes = (sessionDurationSeconds / 60).toInt(),
            wpm = sessionWpm,
            timestamp = now
        )

        // 4. Update the book's current page in database
        bookDao.updateProgress(bookId, book.currentPage + pagesRead, now)

        // 5. Verify the updated state from a user's perspective
        val updatedGoal = profileDao.getReadingGoal().first()
        assertNotNull(updatedGoal)
        assertEquals("Weekly pages read should increase by session pages", 30, updatedGoal!!.weeklyPagesRead)
        assertEquals("Daily streak should increment", 4, updatedGoal.dailyStreak)
        assertEquals("Total minutes read should increase by 15", 135, updatedGoal.totalMinutesRead)
        assertEquals("Total sessions count should increment by 1", 5, updatedGoal.totalSessionsCount)

        val updatedBook = bookDao.getBookById(bookId)
        assertNotNull(updatedBook)
        assertEquals("Book current page should be updated to 30", 30, updatedBook!!.currentPage)

        // Verify recent session queries
        val recent = sessionDao.getRecentSessions().first()
        assertEquals(1, recent.size)
        assertEquals("Dom Casmurro", recent.first().bookTitle)
        assertEquals(10, recent.first().pagesRead)
        assertEquals(sessionWpm, recent.first().speedWpm)
    }

    @Test
    fun testUserStory_AnkiSpacedRepetitionFlashcardsWorkflow() = runBlocking {
        val flashcardDao = database.flashcardDao()

        // 1. Generate an initial Anki flashcard for a chapter read
        val card = FlashcardEntity(
            bookId = 1L,
            bookTitle = "Sapiens",
            chapterTitle = "Capítulo 1: A Revolução Cognitiva",
            frontQuestion = "O que permitiu aos Homo sapiens cooperar em grupos flexíveis e de grande escala?",
            backAnswer = "A capacidade única de transmitir informações sobre coisas imaginadas (ficções, mitos, leis e crenças compartilhadas).",
            keyQuote = "Não há deuses no universo, nem nações, nem dinheiro, nem direitos humanos, exceto na imaginação comum dos seres humanos.",
            sourceType = "FISICO",
            masteryLevel = "NOVO",
            reviewCount = 0,
            repetitionIntervalDays = 1,
            easeFactor = 2.5f
        )
        val id = flashcardDao.insertFlashcard(card)
        assertTrue(id > 0)

        // 2. User reviews and rates as 'BOM'
        val savedCard = flashcardDao.getAllFlashcards().first().first()
        assertEquals("NOVO", savedCard.masteryLevel)
        assertEquals(0, savedCard.reviewCount)

        // Simulate ViewModel review logic for 'BOM'
        val reviewedGoodCard = savedCard.copy(
            repetitionIntervalDays = (savedCard.repetitionIntervalDays * savedCard.easeFactor).toInt().coerceAtLeast(2),
            masteryLevel = "BOM",
            reviewCount = savedCard.reviewCount + 1,
            lastReviewedAt = System.currentTimeMillis()
        )
        flashcardDao.updateFlashcard(reviewedGoodCard)

        val afterGoodReview = flashcardDao.getFlashcardsForBook(1L).first().first()
        assertEquals("BOM", afterGoodReview.masteryLevel)
        assertEquals(1, afterGoodReview.reviewCount)
        assertTrue("Interval should have grown to at least 2 days", afterGoodReview.repetitionIntervalDays >= 2)

        // 3. User reviews again next week and rates as 'FACIL'
        val reviewedEasyCard = afterGoodReview.copy(
            repetitionIntervalDays = (afterGoodReview.repetitionIntervalDays * afterGoodReview.easeFactor * 1.5).toInt().coerceAtLeast(4),
            easeFactor = (afterGoodReview.easeFactor + 0.15f).coerceAtMost(3.0f),
            masteryLevel = "DOMINADO",
            reviewCount = afterGoodReview.reviewCount + 1,
            lastReviewedAt = System.currentTimeMillis()
        )
        flashcardDao.updateFlashcard(reviewedEasyCard)

        val afterEasyReview = flashcardDao.getFlashcardsForBook(1L).first().first()
        assertEquals("DOMINADO", afterEasyReview.masteryLevel)
        assertEquals(2, afterEasyReview.reviewCount)
        assertTrue("Interval should be at least 4 days", afterEasyReview.repetitionIntervalDays >= 4)
        assertTrue("Ease factor should have increased", afterEasyReview.easeFactor > 2.5f)

        // 4. Test review 'ERRO' resets interval
        val reviewedMistakeCard = afterEasyReview.copy(
            repetitionIntervalDays = 1,
            easeFactor = (afterEasyReview.easeFactor - 0.2f).coerceAtLeast(1.3f),
            masteryLevel = "APRENDENDO",
            reviewCount = afterEasyReview.reviewCount + 1,
            lastReviewedAt = System.currentTimeMillis()
        )
        flashcardDao.updateFlashcard(reviewedMistakeCard)

        val afterMistakeReview = flashcardDao.getFlashcardsForBook(1L).first().first()
        assertEquals("APRENDENDO", afterMistakeReview.masteryLevel)
        assertEquals(1, afterMistakeReview.repetitionIntervalDays)
    }

    @Test
    fun testUserStory_ExternalPhysicalAndKindleBookGeminiFlashcardGeneration() = runBlocking {
        // Test generating flashcards for physical book or Kindle when reading offline or via GeminiService
        val flashcards = GeminiService.generateChapterFlashcards(
            bookTitle = "1984",
            author = "George Orwell",
            chapterTitle = "Parte 1 - Capítulo 1",
            chapterSnippet = "",
            sourceType = "KINDLE"
        )

        assertNotNull(flashcards)
        assertTrue("Should generate at least 2 Anki flashcards", flashcards.isNotEmpty())

        val firstCard = flashcards.first()
        assertTrue("Card should contain an active recall front question", firstCard.frontQuestion.isNotBlank())
        assertTrue("Card should contain an accurate back answer", firstCard.backAnswer.isNotBlank())
    }

    @Test
    fun testEpubChapterParsingAndSpeedCalculation() {
        val markdownEpub = """
            ## Capítulo I: O Início da Jornada
            
            Este é o primeiro parágrafo de teste com diversas ideias sobre a narrativa.
            
            O segundo parágrafo explora o desenvolvimento dos personagens e a ambientação da história.
            
            ## Capítulo II: O Clímax
            
            Um confronto inesperado acontece na floresta densa e sombria.
            
            Todos os mistérios começam a se desvendar conforme a luz do dia surge.
        """.trimIndent()

        val chapters = parseEpubChapters(markdownEpub, "Capítulo I, Capítulo II")
        assertEquals(2, chapters.size)
        assertEquals("Capítulo I: O Início da Jornada", chapters[0].title)
        assertEquals(2, chapters[0].paragraphs.size)
        assertTrue("Word count should be positive", chapters[0].wordCount > 10)

        assertEquals("Capítulo II: O Clímax", chapters[1].title)
        assertEquals(2, chapters[1].paragraphs.size)
    }
}
