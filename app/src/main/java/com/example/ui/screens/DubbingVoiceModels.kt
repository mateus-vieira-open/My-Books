package com.example.ui.screens

import androidx.compose.ui.graphics.Color

/**
 * Perfis de vozes e estilos de narração inspirados em dubladores profissionais,
 * projetados para dar vivacidade, ênfase dramática e expressividade à leitura guiada de e-books.
 */
data class DubbingVoiceProfile(
    val id: String,
    val name: String,
    val titleBadge: String,
    val actorInspiration: String,
    val archetype: String,
    val description: String,
    val emoji: String,
    val basePitch: Float,
    val baseSpeed: Float,
    val accentColor: Color,
    val previewSentence: String
)

object DubbingVoiceCatalog {
    val HEROIC_EPIC = DubbingVoiceProfile(
        id = "heroic_epic",
        name = "O Narrador Heroico & Épico",
        titleBadge = "Aventura & Épica",
        actorInspiration = "Inspiração: Dubladores de heróis e animação cinematográfica (estilo Guilherme Briggs)",
        archetype = "Aventura • Ficção Heroica • Fantasia",
        description = "Tom imponente, vibrante e enérgico. Confere vivacidade apaixonada em revelações e cenas de bravura.",
        emoji = "⚔️",
        basePitch = 0.92f,
        baseSpeed = 1.03f,
        accentColor = Color(0xFFE11D48), // Rose Vibrant
        previewSentence = "Ergam a cabeça e prestem atenção! O vento sussurra que o maior enigma deste mundo está prestes a se revelar!"
    )

    val ANIMATED_EXPRESSIVE = DubbingVoiceProfile(
        id = "animated_expressive",
        name = "A Narradora Expressiva & Carismática",
        titleBadge = "Vibrante & Nuances",
        actorInspiration = "Inspiração: Dubladoras de animação e cinema de aventura (estilo Miriam Ficher / Fernanda Bullara)",
        archetype = "Romance • Fantasia • Ficção Dinâmica",
        description = "Tom brilhante, afetuoso, cheio de carisma e agilidade emocional. Dá cor única aos diálogos e pensamentos.",
        emoji = "✨",
        basePitch = 1.25f,
        baseSpeed = 1.06f,
        accentColor = Color(0xFF9333EA), // Purple Vibrant
        previewSentence = "Com um sorriso travesso nos lábios, ela abriu o velho mapa e sussurrou: nada nesta jornada aconteceu por acaso!"
    )

    val WISE_STORYTELLER = DubbingVoiceProfile(
        id = "wise_storyteller",
        name = "O Mestre Contador de Histórias",
        titleBadge = "Fábulas & Mistério",
        actorInspiration = "Inspiração: Mestres anciãos e fábulas fantásticas (estilo Isaac Bardavid / Mestre da Sabedoria)",
        archetype = "Teologia • Filosofia • Fábulas Míticas",
        description = "Tom profundo, cadência envolvente, pausas de sabedoria e mistério teatral. Constrói atmosfera solene e viva.",
        emoji = "📜",
        basePitch = 0.78f,
        baseSpeed = 0.88f,
        accentColor = Color(0xFFD97706), // Amber Warm
        previewSentence = "Acomodem-se ao redor da chama, nobres viajantes... pois as antigas páginas guardam lições que o tempo jamais apagará."
    )

    val NOIR_SUSPENSE = DubbingVoiceProfile(
        id = "noir_suspense",
        name = "O Detetive Noir & Suspense",
        titleBadge = "Tensão & Mistério",
        actorInspiration = "Inspiração: Narradores de thriller policial e terror cósmico (estilo Márcio Seixas)",
        archetype = "Terror • Suspense • Romance Policial",
        description = "Voz aveludada, grave e atenta. Pausas calculadas e respiração dramática que colocam o ouvinte dentro da cena.",
        emoji = "🕵️",
        basePitch = 0.70f,
        baseSpeed = 0.85f,
        accentColor = Color(0xFF2563EB), // Blue Noir
        previewSentence = "A escuridão engolia a calçada vazia. Foi então que ele ouviu o rangido da porta... e percebeu que não estava sozinho."
    )

    val DYNAMIC_YOUNG = DubbingVoiceProfile(
        id = "dynamic_young",
        name = "O Jovem Rebelde & Espirituoso",
        titleBadge = "Ação & Alto Astral",
        actorInspiration = "Inspiração: Protagonistas de anime e quadrinhos de ritmo veloz",
        archetype = "Quadrinhos • Sci-Fi • Crônicas Urbanas",
        description = "Tom dinâmico, rápido, sagaz e empolgante. Perfeito para ritmo acelerado e humor afiado.",
        emoji = "⚡",
        basePitch = 1.36f,
        baseSpeed = 1.15f,
        accentColor = Color(0xFF059669), // Emerald
        previewSentence = "Ei, segura essa! Se você achava que tudo estava sob controle, prepare-se para ver o plano inteiro virar de cabeça pra baixo!"
    )

    val allProfiles = listOf(
        HEROIC_EPIC,
        ANIMATED_EXPRESSIVE,
        WISE_STORYTELLER,
        NOIR_SUSPENSE,
        DYNAMIC_YOUNG
    )
}

/**
 * Prepara o texto para narração de dublador, adicionando respiração,
 * ênfase nas pontuações dramáticas (diálogos, exclamações, reticências).
 */
fun formatTextForDubbedNarration(
    rawText: String,
    isEmphasisEnabled: Boolean
): String {
    if (!isEmphasisEnabled) return rawText.trim()

    var formatted = rawText
        // Diálogos com travessão ganham pausa de respiração
        .replace(Regex("(?m)^—\\s*"), "... ")
        .replace(Regex("—"), ", ")
        .replace(Regex("--"), ", ")
        // Reticências ganham suspense rítmico
        .replace("...", ", ... ")
        // Dois pontos ganham expectativa
        .replace(":", "... ")
        // Exclamações ganham destaque
        .replace("!", "! ")
        .replace("?", "? ")

    return formatted.trim()
}
