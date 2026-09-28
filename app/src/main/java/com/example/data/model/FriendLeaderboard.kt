package com.example.data.model

data class FriendRankingItem(
    val id: Int,
    val name: String,
    val avatarEmoji: String,
    val pagesThisWeek: Int,
    val streakDays: Int,
    val isCurrentUser: Boolean = false,
    val badge: String = "Leitor",
    val statusMessage: String = "Lendo ativamente"
)

data class AchievementBadge(
    val id: String,
    val title: String,
    val description: String,
    val iconEmoji: String,
    val isUnlocked: Boolean,
    val category: String
)

object GamificationData {
    fun getDefaultFriends(userPages: Int, userStreak: Int): List<FriendRankingItem> {
        val list = mutableListOf(
            FriendRankingItem(
                id = 1,
                name = "Lucas Ramos",
                avatarEmoji = "🦁",
                pagesThisWeek = 145,
                streakDays = 14,
                badge = "Filósofo 🧠",
                statusMessage = "Lendo Santo Agostinho"
            ),
            FriendRankingItem(
                id = 2,
                name = "Mateus (Você)",
                avatarEmoji = "🚀",
                pagesThisWeek = userPages,
                streakDays = userStreak,
                isCurrentUser = true,
                badge = "Leitor Voraz 🌟",
                statusMessage = "Focado nas metas da semana"
            ),
            FriendRankingItem(
                id = 3,
                name = "Beatriz Lima",
                avatarEmoji = "🦉",
                pagesThisWeek = 88,
                streakDays = 5,
                badge = "Noite Adentro 🌙",
                statusMessage = "Lendo Drácula"
            ),
            FriendRankingItem(
                id = 4,
                name = "Gabriel Santos",
                avatarEmoji = "⚡",
                pagesThisWeek = 65,
                streakDays = 3,
                badge = "Quadrinista 🎨",
                statusMessage = "Terminando Sandman"
            ),
            FriendRankingItem(
                id = 5,
                name = "Sofia Costa",
                avatarEmoji = "🌸",
                pagesThisWeek = 42,
                streakDays = 2,
                badge = "Exploradora 🗺️",
                statusMessage = "Começando Duna"
            )
        )
        return list.sortedByDescending { it.pagesThisWeek }
    }

    val defaultAchievements = listOf(
        AchievementBadge(
            id = "first_book",
            title = "Primeira Página",
            description = "Iniciou sua primeira leitura no Lumina Reader.",
            iconEmoji = "📖",
            isUnlocked = true,
            category = "Início"
        ),
        AchievementBadge(
            id = "streak_7",
            title = "Hábito em Chamas",
            description = "Manteve 7 dias consecutivos de leitura diária.",
            iconEmoji = "🔥",
            isUnlocked = true,
            category = "Constância"
        ),
        AchievementBadge(
            id = "notion_master",
            title = "Mente Organizada",
            description = "Exportou citações e anotações para o Notion.",
            iconEmoji = "💡",
            isUnlocked = true,
            category = "Produtividade"
        ),
        AchievementBadge(
            id = "theology_reader",
            title = "Buscador da Sabedoria",
            description = "Leu obras do acervo de Teologia e Filosofia.",
            iconEmoji = "🏛️",
            isUnlocked = true,
            category = "Gêneros"
        ),
        AchievementBadge(
            id = "night_owl",
            title = "Coruja da Madrugada",
            description = "Leu no Modo Noturno após as 22h.",
            iconEmoji = "🌙",
            isUnlocked = true,
            category = "Hábito"
        ),
        AchievementBadge(
            id = "century_pages",
            title = "Centenário",
            description = "Ultrapassou a marca de 100 páginas em uma única semana.",
            iconEmoji = "🏆",
            isUnlocked = true,
            category = "Metas"
        )
    )
}
