package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.example.ui.theme.PurpleContainer
import com.example.ui.theme.TextWhite
import com.example.ui.theme.TextWhiteMuted
import com.example.ui.theme.TextWhiteVariant
import com.example.viewmodel.Screen

@Composable
fun LuminaBottomBar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val navItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Color.White,
        selectedTextColor = Color.White,
        unselectedIconColor = TextWhiteMuted,
        unselectedTextColor = TextWhiteVariant,
        indicatorColor = PurpleContainer
    )

    NavigationBar(modifier = modifier) {
        NavigationBarItem(
            selected = currentScreen is Screen.Library,
            onClick = { onNavigate(Screen.Library) },
            colors = navItemColors,
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = "Biblioteca"
                )
            },
            label = { Text("Estante") },
            modifier = Modifier.testTag("nav_item_library")
        )

        NavigationBarItem(
            selected = currentScreen is Screen.Flashcards,
            onClick = { onNavigate(Screen.Flashcards()) },
            colors = navItemColors,
            icon = {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "Cartões Anki"
                )
            },
            label = { Text("Anki") },
            modifier = Modifier.testTag("nav_item_flashcards")
        )

        NavigationBarItem(
            selected = currentScreen is Screen.DiscoverAi,
            onClick = { onNavigate(Screen.DiscoverAi) },
            colors = navItemColors,
            icon = {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Sugestões Gemini AI"
                )
            },
            label = { Text("Gemini AI") },
            modifier = Modifier.testTag("nav_item_discover")
        )

        NavigationBarItem(
            selected = currentScreen is Screen.Gamification,
            onClick = { onNavigate(Screen.Gamification) },
            colors = navItemColors,
            icon = {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = "Metas e Ritmo"
                )
            },
            label = { Text("Metas") },
            modifier = Modifier.testTag("nav_item_gamification")
        )

        NavigationBarItem(
            selected = currentScreen is Screen.SyncProfile,
            onClick = { onNavigate(Screen.SyncProfile) },
            colors = navItemColors,
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = "Sincronização & Conta"
                )
            },
            label = { Text("Sincronia") },
            modifier = Modifier.testTag("nav_item_sync")
        )
    }
}
