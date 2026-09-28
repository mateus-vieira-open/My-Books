package com.example.data.repository

import com.example.data.local.dao.AnnotationDao
import com.example.data.local.dao.BookDao
import com.example.data.local.dao.FlashcardDao
import com.example.data.local.dao.ReadingSessionDao
import com.example.data.local.dao.UserProfileDao
import com.example.data.local.entity.AnnotationEntity
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.FlashcardEntity
import com.example.data.local.entity.ReadingGoalEntity
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

class BookRepository(
    private val bookDao: BookDao,
    private val annotationDao: AnnotationDao,
    private val userProfileDao: UserProfileDao,
    private val readingSessionDao: ReadingSessionDao,
    private val flashcardDao: FlashcardDao
) {
    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()
    val allAnnotations: Flow<List<AnnotationEntity>> = annotationDao.getAllAnnotations()
    val userProfile: Flow<UserProfileEntity?> = userProfileDao.getUserProfile()
    val readingGoal: Flow<ReadingGoalEntity?> = userProfileDao.getReadingGoal()
    val recentSessions: Flow<List<ReadingSessionEntity>> = readingSessionDao.getRecentSessions()
    val allFlashcards: Flow<List<FlashcardEntity>> = flashcardDao.getAllFlashcards()

    fun getBookById(id: Long): Flow<BookEntity?> = bookDao.getBookById(id)
    fun getAnnotationsForBook(bookId: Long): Flow<List<AnnotationEntity>> = annotationDao.getAnnotationsForBook(bookId)
    fun getFlashcardsForBook(bookId: Long): Flow<List<FlashcardEntity>> = flashcardDao.getFlashcardsForBook(bookId)
    fun getFlashcardsByBookTitle(title: String): Flow<List<FlashcardEntity>> = flashcardDao.getFlashcardsByBookTitle(title)

    suspend fun insertFlashcards(flashcards: List<FlashcardEntity>) = flashcardDao.insertFlashcards(flashcards)
    suspend fun updateFlashcard(flashcard: FlashcardEntity) = flashcardDao.updateFlashcard(flashcard)
    suspend fun deleteFlashcard(flashcard: FlashcardEntity) = flashcardDao.deleteFlashcard(flashcard)

    suspend fun insertBook(book: BookEntity): Long = bookDao.insertBook(book)
    suspend fun updateBook(book: BookEntity) = bookDao.updateBook(book)
    suspend fun deleteBook(book: BookEntity) = bookDao.deleteBook(book)
    suspend fun deleteBookById(id: Long) = bookDao.deleteBookById(id)

    suspend fun updateReadingProgress(id: Long, page: Int) {
        val now = System.currentTimeMillis()
        bookDao.updateReadingProgress(id, page, now)
    }

    suspend fun recordReadingSession(
        bookId: Long,
        bookTitle: String,
        durationSeconds: Long,
        pagesRead: Int,
        wordsRead: Int,
        speedWpm: Int
    ): ReadingSessionEntity {
        val session = ReadingSessionEntity(
            bookId = bookId,
            bookTitle = bookTitle,
            durationSeconds = durationSeconds,
            pagesRead = pagesRead,
            wordsRead = wordsRead,
            speedWpm = speedWpm,
            timestamp = System.currentTimeMillis()
        )
        readingSessionDao.insertSession(session)

        val minutes = (durationSeconds / 60).toInt().coerceAtLeast(if (durationSeconds >= 20) 1 else 0)
        val now = System.currentTimeMillis()
        val avgDbWpm = readingSessionDao.getAverageWpm()?.toInt() ?: speedWpm
        val finalWpm = if (avgDbWpm > 0) avgDbWpm else speedWpm

        userProfileDao.recordReadingSession(
            pages = pagesRead,
            minutes = minutes,
            wpm = finalWpm,
            timestamp = now
        )
        return session
    }

    suspend fun insertAnnotation(annotation: AnnotationEntity): Long = annotationDao.insertAnnotation(annotation)
    suspend fun deleteAnnotation(annotation: AnnotationEntity) = annotationDao.deleteAnnotation(annotation)
    suspend fun markAnnotationExported(id: Long, exported: Boolean) = annotationDao.setExportedToNotion(id, exported)
    suspend fun markAllAnnotationsExportedForBook(bookId: Long) = annotationDao.markAllAsExportedForBook(bookId)

    suspend fun updateGoal(goal: ReadingGoalEntity) = userProfileDao.insertOrUpdateGoal(goal)
    suspend fun updateProfile(profile: UserProfileEntity) = userProfileDao.insertOrUpdateProfile(profile)

    suspend fun triggerSync(): Long {
        val timestamp = System.currentTimeMillis()
        userProfileDao.updateSyncTimestamp(timestamp)
        return timestamp
    }
}
