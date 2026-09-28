package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.local.AppDatabase
import com.example.data.repository.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LuminaApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val repository by lazy {
        BookRepository(
            database.bookDao(),
            database.annotationDao(),
            database.userProfileDao(),
            database.readingSessionDao(),
            database.flashcardDao()
        )
    }
    val authManager by lazy { com.example.data.auth.AuthManager(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Lembretes de Leitura Lumina"
            val descriptionText = "Notificações diárias para manter seu streak e suas metas de leitura."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_REMINDERS, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_REMINDERS = "lumina_reading_reminders_channel"
    }
}
