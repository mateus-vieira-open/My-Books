package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.AnnotationEntity
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.FlashcardEntity
import com.example.data.local.entity.ReadingGoalEntity
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadTimestamp DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    fun getBookById(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE status = :status ORDER BY lastReadTimestamp DESC")
    fun getBooksByStatus(status: String): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("UPDATE books SET currentPage = :page, lastReadTimestamp = :timestamp, isSyncedWithCloud = 1 WHERE id = :id")
    suspend fun updateReadingProgress(id: Long, page: Int, timestamp: Long)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: Long)

    @Query("SELECT COUNT(*) FROM books")
    suspend fun getBookCount(): Int
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations ORDER BY createdAt DESC")
    fun getAllAnnotations(): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getAnnotationsForBook(bookId: Long): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity): Long

    @Delete
    suspend fun deleteAnnotation(annotation: AnnotationEntity)

    @Query("UPDATE annotations SET exportedToNotion = :exported WHERE id = :id")
    suspend fun setExportedToNotion(id: Long, exported: Boolean)

    @Query("UPDATE annotations SET exportedToNotion = 1 WHERE bookId = :bookId")
    suspend fun markAllAsExportedForBook(bookId: Long)
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM reading_goal WHERE id = 1 LIMIT 1")
    fun getReadingGoal(): Flow<ReadingGoalEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateGoal(goal: ReadingGoalEntity)

    @Query("UPDATE reading_goal SET weeklyPagesRead = weeklyPagesRead + :pages, dailyStreak = dailyStreak + 1, lastReadDateMillis = :timestamp WHERE id = 1")
    suspend fun recordPagesRead(pages: Int, timestamp: Long)

    @Query("UPDATE reading_goal SET weeklyPagesRead = weeklyPagesRead + :pages, totalMinutesRead = totalMinutesRead + :minutes, averageWpm = :wpm, totalSessionsCount = totalSessionsCount + 1, dailyStreak = dailyStreak + 1, lastReadDateMillis = :timestamp WHERE id = 1")
    suspend fun recordReadingSession(pages: Int, minutes: Int, wpm: Int, timestamp: Long)

    @Query("UPDATE user_profile SET lastSyncTimestamp = :timestamp, isCloudSyncEnabled = 1 WHERE id = 1")
    suspend fun updateSyncTimestamp(timestamp: Long)
}

@Dao
interface ReadingSessionDao {
    @Query("SELECT * FROM reading_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY timestamp DESC")
    fun getSessionsForBook(bookId: Long): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions ORDER BY timestamp DESC LIMIT 10")
    fun getRecentSessions(): Flow<List<ReadingSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadingSessionEntity): Long

    @Query("SELECT AVG(speedWpm) FROM reading_sessions WHERE speedWpm > 0")
    suspend fun getAverageWpm(): Double?

    @Query("SELECT SUM(durationSeconds) FROM reading_sessions")
    suspend fun getTotalDurationSeconds(): Long?
}

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards ORDER BY createdAt DESC")
    fun getAllFlashcards(): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE bookId = :bookId ORDER BY id ASC")
    fun getFlashcardsForBook(bookId: Long): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE bookTitle = :bookTitle ORDER BY id ASC")
    fun getFlashcardsByBookTitle(bookTitle: String): Flow<List<FlashcardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcard(flashcard: FlashcardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(flashcards: List<FlashcardEntity>)

    @Update
    suspend fun updateFlashcard(flashcard: FlashcardEntity)

    @Delete
    suspend fun deleteFlashcard(flashcard: FlashcardEntity)

    @Query("DELETE FROM flashcards WHERE bookTitle = :bookTitle AND chapterTitle = :chapterTitle")
    suspend fun deleteFlashcardsForChapter(bookTitle: String, chapterTitle: String)

    @Query("SELECT COUNT(*) FROM flashcards")
    suspend fun getFlashcardCount(): Int
}


