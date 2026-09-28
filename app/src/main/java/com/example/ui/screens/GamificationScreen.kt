package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ReadingGoalEntity
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.model.GamificationData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamificationScreen(
    readingGoal: ReadingGoalEntity?,
    recentSessions: List<ReadingSessionEntity> = emptyList(),
    onUpdateTarget: (Int) -> Unit,
    onOpenQuizRetake: () -> Unit = {},
    onOpenAnki: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentPages = readingGoal?.weeklyPagesRead ?: 114
    val targetPages = readingGoal?.weeklyTargetPages ?: 150
    val streakDays = readingGoal?.dailyStreak ?: 7
    val progressPct = ((currentPages.toFloat() / targetPages.toFloat().coerceAtLeast(1f)) * 100).toInt()

    val friendsList = remember(currentPages, streakDays) {
        GamificationData.getDefaultFriends(currentPages, streakDays)
    }

    var showEditGoalDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Metas & Ranking",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showInviteDialog = true },
                        modifier = Modifier.testTag("button_invite_friend")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Convidar Amigos"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Weekly Reading Goal Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Meta Semanal de Leitura",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Renova todo domingo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }

                            TextButton(onClick = { showEditGoalDialog = true }) {
                                Text("Ajustar Meta", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Large Progress display
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "$currentPages",
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = " / $targetPages págs",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                                )
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "$progressPct%",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { (currentPages.toFloat() / targetPages.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val remaining = (targetPages - currentPages).coerceAtLeast(0)
                        Text(
                            text = if (remaining > 0) "Faltam apenas $remaining páginas para completar sua meta!" else "🎉 Parabéns! Meta semanal concluída com louvor!",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Real-Time Reading Speed & Active Session Stats Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⚡", fontSize = 18.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Ritmo & Velocidade de Leitura",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Calculado pelas sessões com cronômetro",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Stats Grid (Speed / Total Minutes / Sessions)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Velocidade Média", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${readingGoal?.averageWpm ?: 224}",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF047857)
                                    )
                                    Text("palavras/min", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Tempo Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val totalMin = readingGoal?.totalMinutesRead ?: 410
                                    Text(
                                        text = "${totalMin / 60}h ${totalMin % 60}m",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text("dedicados", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Sessões", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${readingGoal?.totalSessionsCount ?: 8}",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text("concluídas", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        // Recent Sessions Log
                        if (recentSessions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Últimas Sessões Registradas:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            recentSessions.take(3).forEach { session ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Text("📖", fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = session.bookTitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        text = "${session.durationSeconds / 60}m • +${session.pagesRead} págs • ${session.speedWpm} ppm",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Anki Memorization Shortcut Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF6366F1)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🗂️", fontSize = 22.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Cartões de Memorização Anki",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFF312E81)
                                )
                                Text(
                                    text = "Recordação ativa gerada pelo Gemini para e-books, livros físicos e Kindle.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF4338CA)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onOpenAnki,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Abrir Anki", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Reader Literary Evolution Quiz Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Evolução do Leitor",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "Suas leituras avançaram? Renove seus assuntos favoritos para calibrar o Gemini.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onOpenQuizRetake,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Refazer Quiz", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. Streak Fire Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFEF3C7) // Warm gold
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔥", fontSize = 28.sp)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$streakDays Dias Consecutivos!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF78350F)
                            )
                            Text(
                                text = "Você leu todos os dias nesta semana. Seu hábito diário está blindado!",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                }
            }

            // 3. Friends Ranking Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ranking Semanal de Amigos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(onClick = { showInviteDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adicionar Amigo")
                    }
                }
            }

            itemsIndexed(friendsList) { index, friend ->
                val isPodium = index < 3
                val podiumMedal = when (index) {
                    0 -> "🥇"
                    1 -> "🥈"
                    2 -> "🥉"
                    else -> "${index + 1}º"
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("friend_rank_$index"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (friend.isCurrentUser) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        }
                    ),
                    border = if (friend.isCurrentUser) CardDefaults.outlinedCardBorder().copy(width = 2.dp) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Position / Medal
                        Text(
                            text = podiumMedal,
                            fontSize = if (isPodium) 22.sp else 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(32.dp)
                        )

                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = friend.avatarEmoji, fontSize = 22.sp)
                        }

                        // Info
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = friend.name,
                                    fontWeight = if (friend.isCurrentUser) FontWeight.Bold else FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (friend.isCurrentUser) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "VOCÊ",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "${friend.badge} • ${friend.statusMessage}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Pages & Streak
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${friend.pagesThisWeek} págs",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔥", fontSize = 12.sp)
                                Text(
                                    text = "${friend.streakDays}d",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 4. Badges / Achievements
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Suas Conquistas",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GamificationData.defaultAchievements.forEach { badge ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(badge.iconEmoji, fontSize = 22.sp)
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = badge.title,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = badge.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Concluída",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal to Edit Weekly Goal
    if (showEditGoalDialog) {
        var tempTarget by remember { mutableStateOf(targetPages.toString()) }

        AlertDialog(
            onDismissRequest = { showEditGoalDialog = false },
            title = { Text("Definir Meta Semanal") },
            text = {
                Column {
                    Text("Quantas páginas você deseja ler por semana?")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempTarget,
                        onValueChange = { tempTarget = it.filter { c -> c.isDigit() } },
                        label = { Text("Páginas por semana") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(100, 150, 200, 300).forEach { p ->
                            SuggestionChip(
                                onClick = { tempTarget = p.toString() },
                                label = { Text("$p págs") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val num = tempTarget.toIntOrNull() ?: 150
                        onUpdateTarget(num)
                        showEditGoalDialog = false
                    }
                ) {
                    Text("Salvar Meta")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditGoalDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal to Invite Friend
    if (showInviteDialog) {
        var friendCode by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = { Text("Adicionar Amigo ao Ranking") },
            text = {
                Column {
                    Text("Insira o código de leitor ou e-mail do seu amigo para competir no ranking semanal:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = friendCode,
                        onValueChange = { friendCode = it },
                        label = { Text("Código ou E-mail") },
                        placeholder = { Text("Ex: gabriel@gmail.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Seu código de convite: LUMINA-MATEUS-77",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        Toast.makeText(context, "Convite enviado com sucesso!", Toast.LENGTH_SHORT).show()
                        showInviteDialog = false
                    },
                    enabled = friendCode.isNotBlank()
                ) {
                    Text("Conectar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}
