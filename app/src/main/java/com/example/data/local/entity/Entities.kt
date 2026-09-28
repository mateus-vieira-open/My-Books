package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String,
    val isbn: String = "",
    val genre: String, // e.g. Teologia, Quadrinhos, Terror, Ficção Científica, Filosofia
    val description: String = "",
    val coverUrl: String = "",
    val coverColorHex: String = "#312E81",
    val totalPages: Int = 100,
    val currentPage: Int = 0,
    val status: String = "READING", // READING, TO_READ, COMPLETED, FAVORITE
    val rating: Int = 0, // 1 to 5
    val content: String = "", // Full e-book readable content with chapters
    val chapters: String = "", // Comma-separated chapter titles
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val isSyncedWithCloud: Boolean = true
)

@Entity(tableName = "annotations")
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val bookTitle: String,
    val bookAuthor: String,
    val selectedText: String,
    val noteText: String = "",
    val highlightColorHex: String = "#FEF08A", // Yellow, Green, Blue, Pink, Orange
    val chapterTitle: String = "Capítulo 1",
    val pageNumber: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val exportedToNotion: Boolean = false
)

@Entity(tableName = "reading_goal")
data class ReadingGoalEntity(
    @PrimaryKey
    val id: Int = 1,
    val weeklyTargetPages: Int = 150,
    val weeklyPagesRead: Int = 92,
    val dailyStreak: Int = 7,
    val lastReadDateMillis: Long = System.currentTimeMillis(),
    val totalMinutesRead: Int = 380,
    val readerLevel: String = "Leitor Voraz",
    val averageWpm: Int = 220,
    val totalSessionsCount: Int = 8
)

@Entity(tableName = "reading_sessions")
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val bookTitle: String,
    val durationSeconds: Long,
    val pagesRead: Int,
    val wordsRead: Int,
    val speedWpm: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "flashcards")
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long = 0L,
    val bookTitle: String,
    val chapterTitle: String,
    val frontQuestion: String,
    val backAnswer: String,
    val keyQuote: String = "",
    val sourceType: String = "APP", // APP, FISICO, KINDLE
    val repetitionIntervalDays: Int = 1,
    val easeFactor: Float = 2.5f,
    val reviewCount: Int = 0,
    val masteryLevel: String = "NOVO", // NOVO, APRENDENDO, BOM, DOMINADO
    val lastReviewedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val id: Int = 1,
    val name: String = "Mateus",
    val email: String = "ma2001teus23@gmail.com",
    val preferredGenres: String = "Teologia, Quadrinhos, Terror, Ficção",
    val isCloudSyncEnabled: Boolean = true,
    val lastSyncTimestamp: Long = System.currentTimeMillis(),
    val dailyReminderTime: String = "20:00",
    val notionDatabaseUrl: String = "https://notion.so/my-reading-notes",
    val isNightModeForced: Boolean = false,
    val isOnboardingCompleted: Boolean = false,
    val quizRetakeCount: Int = 0,
    val readerEvolutionStage: String = "Leitor Explorador"
)
