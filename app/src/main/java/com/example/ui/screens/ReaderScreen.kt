package com.example.ui.screens

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.AnnotationEntity
import com.example.data.local.entity.BookEntity
import com.example.data.remote.NotionExporter
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

enum class ReaderTheme(
    val title: String,
    val backgroundColor: Color,
    val textColor: Color,
    val cardColor: Color
) {
    LIGHT("Claro", ReaderLightBg, ReaderLightText, Color.White),
    NIGHT("Noturno", ReaderNightBg, ReaderNightText, Color(0xFF1E293B)),
    SEPIA("Sépia", ReaderSepiaBg, ReaderSepiaText, Color(0xFFEFE6D7)),
    FOREST("Floresta", ReaderForestBg, ReaderForestText, Color(0xFF1B3529))
}

data class ParsedChapter(
    val title: String,
    val paragraphs: List<String>,
    val wordCount: Int
)

fun parseEpubChapters(content: String, declaredChapters: String): List<ParsedChapter> {
    if (content.isBlank()) {
        return listOf(
            ParsedChapter(
                title = "Capítulo 1",
                paragraphs = listOf("Este e-book não possui conteúdo legível no momento."),
                wordCount = 9
            )
        )
    }

    // 1. Check if content uses Markdown '## ' chapter delimiters
    if (content.contains(Regex("(?m)^##\\s+"))) {
        val parts = content.split(Regex("(?m)^##\\s+"))
        val list = mutableListOf<ParsedChapter>()
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isBlank()) continue
            val lines = trimmed.split("\n", limit = 2)
            val title = lines.firstOrNull()?.trim() ?: "Capítulo"
            val body = if (lines.size > 1) lines[1].trim() else ""
            val paragraphs = body.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
            val words = body.split(Regex("\\s+")).count { it.isNotBlank() }
            list.add(ParsedChapter(title = title, paragraphs = paragraphs, wordCount = words))
        }
        if (list.isNotEmpty()) return list
    }

    // 2. Check if content has 'Capítulo' markers at line beginnings
    if (content.contains(Regex("(?m)^Capítulo\\s+[IVXLCDM0-9]+", RegexOption.IGNORE_CASE))) {
        val parts = content.split(Regex("(?m)(?=^Capítulo\\s+[IVXLCDM0-9]+)", RegexOption.IGNORE_CASE))
        val list = mutableListOf<ParsedChapter>()
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isBlank()) continue
            val lines = trimmed.split("\n", limit = 2)
            val title = lines.firstOrNull()?.trim() ?: "Capítulo"
            val body = if (lines.size > 1) lines[1].trim() else ""
            val paragraphs = body.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
            val words = body.split(Regex("\\s+")).count { it.isNotBlank() }
            list.add(ParsedChapter(title = title, paragraphs = paragraphs, wordCount = words))
        }
        if (list.isNotEmpty()) return list
    }

    // 3. Fallback: single chapter with all paragraphs
    val paragraphs = content.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
    val defaultTitle = declaredChapters.split(",").firstOrNull()?.trim() ?: "Texto Completo"
    val words = content.split(Regex("\\s+")).count { it.isNotBlank() }
    return listOf(ParsedChapter(title = defaultTitle, paragraphs = paragraphs, wordCount = words))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: BookEntity?,
    annotations: List<AnnotationEntity>,
    onBack: () -> Unit,
    onProgressChange: (Int) -> Unit,
    onAddAnnotation: (selectedText: String, note: String, colorHex: String, page: Int, chapter: String) -> Unit,
    onDeleteAnnotation: (AnnotationEntity) -> Unit,
    onFinishSession: ((durationSeconds: Long, pagesRead: Int, wordsRead: Int, speedWpm: Int) -> Unit)? = null,
    onGenerateAnkiForChapter: ((bookId: Long, bookTitle: String, author: String, chapterTitle: String, contentSnippet: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // --- Real-time Reading Session Timer & Speed Tracking State ---
    var sessionSeconds by remember { mutableLongStateOf(0L) }
    var isTimerRunning by remember { mutableStateOf(true) }
    val initialPage = remember(book?.id) { book?.currentPage ?: 0 }
    var pagesReadInSession by remember { mutableIntStateOf(0) }
    var showSessionSummaryDialog by remember { mutableStateOf(false) }
    var showSessionDetailsSheet by remember { mutableStateOf(false) }

    // Ticking session timer
    LaunchedEffect(isTimerRunning) {
        while (isTimerRunning) {
            kotlinx.coroutines.delay(1000L)
            sessionSeconds++
        }
    }

    // Update pages read in this session
    LaunchedEffect(book?.currentPage) {
        val current = book?.currentPage ?: 0
        val delta = (current - initialPage).coerceAtLeast(0)
        if (delta > pagesReadInSession) {
            pagesReadInSession = delta
        }
    }

    fun handleExit() {
        if (sessionSeconds >= 8) {
            isTimerRunning = false
            showSessionSummaryDialog = true
        } else {
            onBack()
        }
    }

    BackHandler { handleExit() }

    if (book == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Selecione um livro na estante para iniciar a leitura.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack) {
                    Text("Voltar à Estante")
                }
            }
        }
        return
    }

    // Reader UI Themes & Typography
    var selectedTheme by remember { mutableStateOf(ReaderTheme.NIGHT) } // Starts in Night Mode
    var fontSizeSp by remember { mutableFloatStateOf(18f) }
    var lineSpacingMultiplier by remember { mutableFloatStateOf(1.5f) }
    var currentFontFamily by remember { mutableStateOf("Serif") }
    var isTextJustified by remember { mutableStateOf(true) }

    // Bottom Sheets & Dialogs
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showAddAnnotationSheet by remember { mutableStateOf(false) }
    var showNotesListSheet by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showDubbingStudioSheet by remember { mutableStateOf(false) }
    var showParagraphActionDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }

    // Search inside reader
    var isSearchActive by remember { mutableStateOf(false) }
    var inBookSearchQuery by remember { mutableStateOf("") }

    // Annotation drafting state
    var selectedHighlightText by remember { mutableStateOf("") }
    var personalNoteText by remember { mutableStateOf("") }
    var chosenColorHex by remember { mutableStateOf("#FEF08A") }

    // EPUB Chapters extraction
    val parsedChapters = remember(book.content, book.chapters) {
        parseEpubChapters(book.content, book.chapters)
    }

    var currentChapterIndex by remember { mutableIntStateOf(0) }
    val activeChapter = parsedChapters.getOrElse(currentChapterIndex) {
        parsedChapters.firstOrNull() ?: ParsedChapter("Capítulo", emptyList(), 0)
    }

    val scrollState = rememberScrollState()

    // --- Dubbing & Guided Voice Acting Engine ---
    var selectedDubbingProfile by remember { mutableStateOf(DubbingVoiceCatalog.HEROIC_EPIC) }
    var pitchMultiplier by remember { mutableFloatStateOf(1.0f) }
    var speedMultiplier by remember { mutableFloatStateOf(1.0f) }
    var isEmphasisEnabled by remember { mutableStateOf(true) }
    var isGuidedReadingEnabled by remember { mutableStateOf(true) }

    var currentSpeakingParagraphIndex by remember { mutableStateOf<Int?>(null) }
    var isTtsPlaying by remember { mutableStateOf(false) }
    var isTtsPaused by remember { mutableStateOf(false) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }

    // Forward declaration of speakParagraph
    var speakParagraphRef by remember { mutableStateOf<(Int) -> Unit>({}) }

    fun stopTts() {
        ttsEngine?.stop()
        isTtsPlaying = false
        isTtsPaused = false
        currentSpeakingParagraphIndex = null
    }

    fun pauseTts() {
        ttsEngine?.stop()
        isTtsPlaying = false
        isTtsPaused = true
    }

    val speakParagraph: (Int) -> Unit = { index ->
        val engine = ttsEngine
        if (engine == null) {
            Toast.makeText(context, "Sintetizador de voz ainda inicializando.", Toast.LENGTH_SHORT).show()
        } else {
            val paragraphs = activeChapter.paragraphs
            if (index !in paragraphs.indices) {
                stopTts()
                Toast.makeText(context, "Capítulo concluído com sucesso!", Toast.LENGTH_SHORT).show()
            } else {
                currentSpeakingParagraphIndex = index
                isTtsPlaying = true
                isTtsPaused = false

                val effectivePitch = (selectedDubbingProfile.basePitch * pitchMultiplier).coerceIn(0.5f, 2.0f)
                val effectiveSpeed = (selectedDubbingProfile.baseSpeed * speedMultiplier).coerceIn(0.5f, 2.0f)
                engine.setPitch(effectivePitch)
                engine.setSpeechRate(effectiveSpeed)

                val rawParagraph = paragraphs[index]
                val spokenText = formatTextForDubbedNarration(rawParagraph, isEmphasisEnabled)

                val utteranceId = "DUBBED_PARAGRAPH_$index"
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }
                engine.speak(spokenText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            }
        }
    }

    // Keep reference updated
    speakParagraphRef = speakParagraph

    fun togglePlayPause() {
        if (isTtsPlaying) {
            pauseTts()
        } else {
            val idx = currentSpeakingParagraphIndex ?: 0
            speakParagraph(idx)
        }
    }

    fun previewVoice(profile: DubbingVoiceProfile) {
        val engine = ttsEngine ?: return
        engine.stop()
        val effectivePitch = (profile.basePitch * pitchMultiplier).coerceIn(0.5f, 2.0f)
        val effectiveSpeed = (profile.baseSpeed * speedMultiplier).coerceIn(0.5f, 2.0f)
        engine.setPitch(effectivePitch)
        engine.setSpeechRate(effectiveSpeed)

        val previewText = formatTextForDubbedNarration(profile.previewSentence, isEmphasisEnabled)
        val utteranceId = "PREVIEW_${profile.id}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        engine.speak(previewText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        Toast.makeText(context, "Ouvindo tom: ${profile.name}", Toast.LENGTH_SHORT).show()
    }

    DisposableEffect(context) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("pt", "BR")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post {
                            if (utteranceId?.startsWith("DUBBED_PARAGRAPH_") == true) {
                                val idx = utteranceId.removePrefix("DUBBED_PARAGRAPH_").toIntOrNull()
                                if (idx != null) {
                                    currentSpeakingParagraphIndex = idx
                                    isTtsPlaying = true
                                    isTtsPaused = false
                                }
                            }
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            if (utteranceId?.startsWith("DUBBED_PARAGRAPH_") == true) {
                                val idx = utteranceId.removePrefix("DUBBED_PARAGRAPH_").toIntOrNull()
                                if (idx != null) {
                                    val nextIdx = idx + 1
                                    if (nextIdx < activeChapter.paragraphs.size) {
                                        speakParagraphRef(nextIdx)
                                    } else {
                                        stopTts()
                                        Toast.makeText(context, "Capítulo concluído!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            isTtsPlaying = false
                            isTtsPaused = false
                        }
                    }
                })
            }
        }
        ttsEngine = tts
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    // Auto-scroll when guided reading moves to next paragraph
    LaunchedEffect(currentSpeakingParagraphIndex) {
        val idx = currentSpeakingParagraphIndex
        if (idx != null && isGuidedReadingEnabled && activeChapter.paragraphs.isNotEmpty()) {
            val maxScroll = scrollState.maxValue
            if (maxScroll > 0) {
                val fraction = (idx.toFloat() / activeChapter.paragraphs.size).coerceIn(0f, 1f)
                scrollState.animateScrollTo((maxScroll * fraction).toInt())
            }
        }
    }

    // Reading time calculation: avg 200 words per minute
    val estimatedMinutes = (activeChapter.wordCount / 200).coerceAtLeast(1)

    // Real-Time Reading Speed Calculation (WPM)
    val currentWpm = remember(sessionSeconds, pagesReadInSession) {
        if (sessionSeconds < 4) 0
        else {
            val minutes = sessionSeconds / 60.0
            val words = if (pagesReadInSession > 0) pagesReadInSession * 260 else (activeChapter.wordCount.coerceIn(100, 380))
            (words / minutes).toInt().coerceIn(60, 600)
        }
    }

    fun formatSeconds(sec: Long): String {
        val m = sec / 60
        val s = sec % 60
        return "%02d:%02d".format(m, s)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = selectedTheme.textColor,
                                maxLines = 1
                            )
                            if (book.genre.contains("EPUB", ignoreCase = true) || parsedChapters.size > 1) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "EPUB",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${activeChapter.title} • ~$estimatedMinutes min • Pág. ${book.currentPage}/${book.totalPages}",
                            style = MaterialTheme.typography.labelSmall,
                            color = selectedTheme.textColor.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            stopTts()
                            handleExit()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = selectedTheme.textColor
                        )
                    }
                },
                actions = {
                    // Search in book text
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Buscar no texto",
                            tint = selectedTheme.textColor
                        )
                    }

                    // Estúdio de Dublagem & Vozes com Tons Diferentes
                    IconButton(
                        onClick = { showDubbingStudioSheet = true },
                        modifier = Modifier.testTag("action_dubbing_studio")
                    ) {
                        BadgedBox(
                            badge = {
                                if (isTtsPlaying) {
                                    Badge(containerColor = selectedDubbingProfile.accentColor) {
                                        Text(selectedDubbingProfile.emoji, fontSize = 8.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "Estúdio de Dubladores e Tons de Voz",
                                tint = if (isTtsPlaying) selectedDubbingProfile.accentColor else selectedTheme.textColor
                            )
                        }
                    }

                    // Text to Speech (Quick Play / Pause Narração)
                    IconButton(onClick = { togglePlayPause() }) {
                        Icon(
                            imageVector = if (isTtsPlaying) Icons.Default.PauseCircle else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isTtsPlaying) "Pausar Narração" else "Ouvir Capítulo com Dublador",
                            tint = if (isTtsPlaying) selectedDubbingProfile.accentColor else selectedTheme.textColor
                        )
                    }

                    // Night Mode Quick Toggle
                    IconButton(
                        onClick = {
                            selectedTheme = if (selectedTheme == ReaderTheme.NIGHT) ReaderTheme.SEPIA else ReaderTheme.NIGHT
                        }
                    ) {
                        Icon(
                            imageVector = if (selectedTheme == ReaderTheme.NIGHT) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Alternar Modo Noturno",
                            tint = selectedTheme.textColor
                        )
                    }

                    // EPUB Table of Contents (Índice de Capítulos)
                    IconButton(
                        onClick = { showTocSheet = true },
                        modifier = Modifier.testTag("action_reader_toc")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatListBulleted,
                            contentDescription = "Índice de Capítulos (EPUB TOC)",
                            tint = selectedTheme.textColor
                        )
                    }

                    // View Annotations
                    IconButton(onClick = { showNotesListSheet = true }) {
                        BadgedBox(
                            badge = {
                                if (annotations.isNotEmpty()) {
                                    Badge { Text("${annotations.size}") }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkBorder,
                                contentDescription = "Anotações do Livro",
                                tint = selectedTheme.textColor
                            )
                        }
                    }

                    // Settings (Typography, Margins, Theme)
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.FormatSize,
                            contentDescription = "Ajustes de Leitura",
                            tint = selectedTheme.textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = selectedTheme.backgroundColor
                )
            )
        },
        bottomBar = {
            Column {
                // Persistent Floating Guided Narration Bar
                AnimatedVisibility(visible = isTtsPlaying || isTtsPaused || currentSpeakingParagraphIndex != null) {
                    Surface(
                        color = selectedTheme.cardColor,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, selectedDubbingProfile.accentColor.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { showDubbingStudioSheet = true }
                                        .weight(1f)
                                ) {
                                    Surface(
                                        color = selectedDubbingProfile.accentColor.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, selectedDubbingProfile.accentColor)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(selectedDubbingProfile.emoji, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = selectedDubbingProfile.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = selectedDubbingProfile.accentColor,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "§ ${(currentSpeakingParagraphIndex ?: 0) + 1}/${activeChapter.paragraphs.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = selectedTheme.textColor.copy(alpha = 0.8f)
                                    )
                                }

                                TextButton(
                                    onClick = { showDubbingStudioSheet = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = selectedDubbingProfile.accentColor
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Trocar Tom", fontSize = 12.sp, color = selectedDubbingProfile.accentColor)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Transport Controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        val prev = (currentSpeakingParagraphIndex ?: 0) - 1
                                        if (prev >= 0) speakParagraph(prev)
                                    },
                                    enabled = (currentSpeakingParagraphIndex ?: 0) > 0
                                ) {
                                    Icon(Icons.Default.SkipPrevious, contentDescription = "Parágrafo anterior", tint = selectedTheme.textColor)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                FilledIconButton(
                                    onClick = { togglePlayPause() },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = selectedDubbingProfile.accentColor
                                    ),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isTtsPlaying) "Pausar" else "Retomar",
                                        tint = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = {
                                        val next = (currentSpeakingParagraphIndex ?: 0) + 1
                                        if (next < activeChapter.paragraphs.size) speakParagraph(next)
                                    },
                                    enabled = (currentSpeakingParagraphIndex ?: 0) < activeChapter.paragraphs.size - 1
                                ) {
                                    Icon(Icons.Default.SkipNext, contentDescription = "Próximo parágrafo", tint = selectedTheme.textColor)
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                IconButton(onClick = { stopTts() }) {
                                    Icon(Icons.Default.Stop, contentDescription = "Parar narração", tint = selectedTheme.textColor)
                                }
                            }
                        }
                    }
                }

                // Reader Bottom Bar with Pagination & Quick Highlight Action
                Surface(
                    color = selectedTheme.backgroundColor,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        // Progress Slider
                        Slider(
                            value = book.currentPage.toFloat(),
                            onValueChange = { onProgressChange(it.toInt()) },
                            valueRange = 0f..book.totalPages.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (book.currentPage > 1) {
                                        onProgressChange(book.currentPage - 1)
                                    }
                                },
                                enabled = book.currentPage > 0
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Página Anterior",
                                    tint = selectedTheme.textColor
                                )
                            }

                            Button(
                                onClick = {
                                    selectedHighlightText = activeChapter.paragraphs.firstOrNull()?.take(160)
                                        ?: "Trecho selecionado do livro ${book.title}"
                                    showAddAnnotationSheet = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.testTag("button_highlight_action")
                            ) {
                                Icon(Icons.Default.BorderColor, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Anotar / Citação", fontSize = 13.sp)
                            }

                            IconButton(
                                onClick = {
                                    if (book.currentPage < book.totalPages) {
                                        onProgressChange(book.currentPage + 1)
                                    }
                                },
                                enabled = book.currentPage < book.totalPages
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Próxima Página",
                                    tint = selectedTheme.textColor
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(selectedTheme.backgroundColor)
                .padding(innerPadding)
        ) {
            // Real-Time Reading Timer & Speed Tracking HUD Bar
            Surface(
                color = selectedTheme.cardColor,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reading_session_timer_hud")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Timer & Speed Status Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { showSessionDetailsSheet = true }
                            .weight(1f)
                    ) {
                        Surface(
                            color = if (isTimerRunning) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isTimerRunning) MaterialTheme.colorScheme.primary else Color.Gray)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(if (isTimerRunning) "⏱️" else "⏸️", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formatSeconds(sessionSeconds),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isTimerRunning) MaterialTheme.colorScheme.primary else selectedTheme.textColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Reading Speed Pill
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("⚡", fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (currentWpm > 0) "$currentWpm ppm" else "medindo...",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF047857)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "+$pagesReadInSession págs",
                            style = MaterialTheme.typography.labelSmall,
                            color = selectedTheme.textColor.copy(alpha = 0.8f)
                        )
                    }

                    // Quick Actions: Play/Pause Timer & Anki Generator for this Chapter
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { isTimerRunning = !isTimerRunning },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isTimerRunning) "Pausar Cronômetro" else "Retomar Cronômetro",
                                tint = selectedTheme.textColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        FilledTonalButton(
                            onClick = {
                                val snippet = activeChapter.paragraphs.take(3).joinToString("\n\n")
                                onGenerateAnkiForChapter?.invoke(
                                    book.id,
                                    book.title,
                                    book.author,
                                    activeChapter.title,
                                    snippet
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF6366F1).copy(alpha = 0.15f)),
                            modifier = Modifier.testTag("button_generate_chapter_anki")
                        ) {
                            Text("🗂️ Anki", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
                        }
                    }
                }
            }

            // In-Book Search Bar
            AnimatedVisibility(visible = isSearchActive) {
                Surface(
                    color = selectedTheme.cardColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inBookSearchQuery,
                            onValueChange = { inBookSearchQuery = it },
                            placeholder = { Text("Buscar palavra no capítulo...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (inBookSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { inBookSearchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Reading Text Canvas
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                // Chapter Header Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = selectedTheme.cardColor,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Capítulo ${currentChapterIndex + 1} de ${parsedChapters.size}",
                            color = selectedTheme.textColor.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "$estimatedMinutes min de leitura",
                        color = selectedTheme.textColor.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Chapter Title Banner
                Text(
                    text = activeChapter.title,
                    color = selectedTheme.textColor,
                    fontSize = (fontSizeSp + 6f).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = when (currentFontFamily) {
                        "Serif" -> FontFamily.Serif
                        "SansSerif" -> FontFamily.SansSerif
                        "Monospace" -> FontFamily.Monospace
                        else -> FontFamily.Default
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Chapter Paragraphs
                activeChapter.paragraphs.forEachIndexed { pIndex, paragraph ->
                    val isBeingSpoken = currentSpeakingParagraphIndex == pIndex
                    val matchingAnnotation = annotations.firstOrNull {
                        it.selectedText.isNotBlank() && paragraph.contains(it.selectedText)
                    }

                    // Highlight matches if search is active
                    val isQueryMatch = inBookSearchQuery.isNotBlank() &&
                            paragraph.contains(inBookSearchQuery, ignoreCase = true)

                    val animatedBgColor by animateColorAsState(
                        targetValue = when {
                            isBeingSpoken -> selectedDubbingProfile.accentColor.copy(alpha = 0.16f)
                            matchingAnnotation != null -> Color(android.graphics.Color.parseColor(matchingAnnotation.highlightColorHex)).copy(alpha = 0.28f)
                            isQueryMatch -> Color(0xFFFEF08A).copy(alpha = 0.35f)
                            else -> Color.Transparent
                        },
                        label = "paragraph_bg"
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                showParagraphActionDialog = Pair(pIndex, paragraph)
                            },
                        color = animatedBgColor,
                        shape = RoundedCornerShape(10.dp),
                        border = if (isBeingSpoken) BorderStroke(2.dp, selectedDubbingProfile.accentColor) else null
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            // Guided voice live banner when spoken
                            if (isBeingSpoken) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = selectedDubbingProfile.accentColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Leitura Guiada • ${selectedDubbingProfile.name}",
                                        color = selectedDubbingProfile.accentColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Surface(
                                        color = selectedDubbingProfile.accentColor,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (isTtsPlaying) "NARRANDO" else "PAUSADO",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (isQueryMatch && inBookSearchQuery.isNotBlank()) {
                                // Render with search highlight
                                val annotatedString = buildAnnotatedString {
                                    var startIndex = 0
                                    val queryLower = inBookSearchQuery.lowercase()
                                    val textLower = paragraph.lowercase()

                                    while (startIndex < paragraph.length) {
                                        val matchIndex = textLower.indexOf(queryLower, startIndex)
                                        if (matchIndex == -1) {
                                            append(paragraph.substring(startIndex))
                                            break
                                        }
                                        append(paragraph.substring(startIndex, matchIndex))
                                        withStyle(
                                            SpanStyle(
                                                background = Color(0xFFF59E0B),
                                                color = Color.Black,
                                                fontWeight = FontWeight.Bold
                                            )
                                        ) {
                                            append(paragraph.substring(matchIndex, matchIndex + queryLower.length))
                                        }
                                        startIndex = matchIndex + queryLower.length
                                    }
                                }

                                Text(
                                    text = annotatedString,
                                    color = selectedTheme.textColor,
                                    fontSize = fontSizeSp.sp,
                                    lineHeight = (fontSizeSp * lineSpacingMultiplier).sp,
                                    fontFamily = when (currentFontFamily) {
                                        "Serif" -> FontFamily.Serif
                                        "SansSerif" -> FontFamily.SansSerif
                                        "Monospace" -> FontFamily.Monospace
                                        else -> FontFamily.Default
                                    },
                                    textAlign = if (isTextJustified) TextAlign.Justify else TextAlign.Start
                                )
                            } else {
                                Text(
                                    text = paragraph,
                                    color = selectedTheme.textColor,
                                    fontSize = fontSizeSp.sp,
                                    lineHeight = (fontSizeSp * lineSpacingMultiplier).sp,
                                    fontFamily = when (currentFontFamily) {
                                        "Serif" -> FontFamily.Serif
                                        "SansSerif" -> FontFamily.SansSerif
                                        "Monospace" -> FontFamily.Monospace
                                        else -> FontFamily.Default
                                    },
                                    textAlign = if (isTextJustified) TextAlign.Justify else TextAlign.Start
                                )
                            }
                        }
                    }

                    // Inline annotation badge if marked
                    if (matchingAnnotation != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FormatQuote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Nota: \"${matchingAnnotation.noteText.ifBlank { "Destacado" }}\"",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = FontStyle.Italic
                                )
                            }

                            IconButton(
                                onClick = {
                                    NotionExporter.copyToClipboard(
                                        context,
                                        NotionExporter.formatSingleAnnotationForNotion(matchingAnnotation),
                                        "Citação enviada para o Notion!"
                                    )
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Copiar Notion",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Anki Chapter Completion Flashcard Banner
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .testTag("card_chapter_anki_cta"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF6366F1).copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF6366F1)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🗂️", fontSize = 20.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Fixar com Anki & Gemini",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = selectedTheme.textColor
                                )
                                Text(
                                    text = "Gere cartões de memorização para '${activeChapter.title.take(28)}'",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = selectedTheme.textColor.copy(alpha = 0.75f)
                                )
                            }
                        }
                        Button(
                            onClick = {
                                val snippet = activeChapter.paragraphs.take(3).joinToString("\n\n")
                                onGenerateAnkiForChapter?.invoke(
                                    book.id,
                                    book.title,
                                    book.author,
                                    activeChapter.title,
                                    snippet
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Gerar Anki", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Chapter Navigation Footer for EPUB Books
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = selectedTheme.textColor.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentChapterIndex > 0) {
                        OutlinedButton(
                            onClick = {
                                stopTts()
                                currentChapterIndex--
                                coroutineScope.launch { scrollState.scrollTo(0) }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Capítulo Anterior", fontSize = 12.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Surface(
                        color = selectedTheme.cardColor,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${currentChapterIndex + 1} / ${parsedChapters.size}",
                            color = selectedTheme.textColor.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (currentChapterIndex < parsedChapters.size - 1) {
                        val nextTitle = parsedChapters.getOrNull(currentChapterIndex + 1)?.title ?: "Próximo"
                        Button(
                            onClick = {
                                stopTts()
                                currentChapterIndex++
                                coroutineScope.launch { scrollState.scrollTo(0) }
                            }
                        ) {
                            Text(
                                text = "Próximo: ${nextTitle.take(16)}",
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Fim da Obra 🎉",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }

    // --- DIALOG: Paragraph Interactive Actions ---
    showParagraphActionDialog?.let { (pIndex, paragraphText) ->
        AlertDialog(
            onDismissRequest = { showParagraphActionDialog = null },
            icon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = selectedDubbingProfile.accentColor) },
            title = { Text("Parágrafo § ${pIndex + 1}") },
            text = {
                Column {
                    Text(
                        text = "\"${paragraphText.take(160)}...\"",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("O que deseja fazer com este trecho?", style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showParagraphActionDialog = null
                        speakParagraph(pIndex)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = selectedDubbingProfile.accentColor)
                ) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Narrar a Partir Daqui")
                }
            },
            dismissButton = {
                Row {
                    OutlinedButton(
                        onClick = {
                            selectedHighlightText = paragraphText
                            showParagraphActionDialog = null
                            showAddAnnotationSheet = true
                        }
                    ) {
                        Icon(Icons.Default.BorderColor, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Anotar")
                    }
                }
            }
        )
    }

    // --- 1. ESTÚDIO DE DUBLAGEM & VOZES (Dubbing Voice Studio) ---
    if (showDubbingStudioSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDubbingStudioSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = selectedDubbingProfile.accentColor.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🎙️", fontSize = 20.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Estúdio de Dublagem & Vozes",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Vozes interpretadas com vivacidade e ênfase dramática",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Voice Personas Cards
                Text(
                    text = "Selecione o Estilo de Narração / Dublador:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                DubbingVoiceCatalog.allProfiles.forEach { profile ->
                    val isSelected = selectedDubbingProfile.id == profile.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable {
                                selectedDubbingProfile = profile
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) profile.accentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = if (isSelected) BorderStroke(2.dp, profile.accentColor) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(profile.emoji, fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = profile.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isSelected) profile.accentColor else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = profile.archetype,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = profile.accentColor,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Surface(
                                        color = profile.accentColor,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Ativo",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = profile.actorInspiration,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = profile.description,
                                style = MaterialTheme.typography.bodySmall
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Action buttons: Preview Sample & Apply
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { previewVoice(profile) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ouvir Tom", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        selectedDubbingProfile = profile
                                        speakParagraph(currentSpeakingParagraphIndex ?: 0)
                                        showDubbingStudioSheet = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = profile.accentColor),
                                    modifier = Modifier.weight(1.3f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isSelected) "Narrar com Esta" else "Escolher e Narrar", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Custom Voice Finetuning (Pitch, Speed, Emphasis)
                Text(
                    text = "Ajuste de Entonação & Vivacidade:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Pitch Control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tom da Voz (Pitch):", style = MaterialTheme.typography.bodyMedium)
                    val pitchLabel = when {
                        pitchMultiplier < 0.9f -> "Mais Grave / Encorpado"
                        pitchMultiplier > 1.15f -> "Mais Agudo / Jovial"
                        else -> "Equilibrado (1.0x)"
                    }
                    Text(pitchLabel, fontWeight = FontWeight.Bold, color = selectedDubbingProfile.accentColor, fontSize = 13.sp)
                }

                Slider(
                    value = pitchMultiplier,
                    onValueChange = { pitchMultiplier = it },
                    valueRange = 0.7f..1.4f,
                    steps = 6,
                    colors = SliderDefaults.colors(
                        thumbColor = selectedDubbingProfile.accentColor,
                        activeTrackColor = selectedDubbingProfile.accentColor
                    )
                )

                // Speed Control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cadência & Ritmo:", style = MaterialTheme.typography.bodyMedium)
                    val speedLabel = when {
                        speedMultiplier < 0.9f -> "Solene & Suspense"
                        speedMultiplier > 1.15f -> "Rápido & Dinâmico"
                        else -> "Normal (1.0x)"
                    }
                    Text(speedLabel, fontWeight = FontWeight.Bold, color = selectedDubbingProfile.accentColor, fontSize = 13.sp)
                }

                Slider(
                    value = speedMultiplier,
                    onValueChange = { speedMultiplier = it },
                    valueRange = 0.75f..1.45f,
                    steps = 6,
                    colors = SliderDefaults.colors(
                        thumbColor = selectedDubbingProfile.accentColor,
                        activeTrackColor = selectedDubbingProfile.accentColor
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Modo Ênfase Teatral
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ênfase e Pausas Teatrais:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Injeta pausas expressivas em diálogos (—), expectativa em reticências (...) e destaque nas exclamações.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isEmphasisEnabled,
                        onCheckedChange = { isEmphasisEnabled = it }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Leitura Guiada Sincronizada
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Leitura Guiada em Tempo Real:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Destaca na tela o parágrafo exato que está sendo narrado e acompanha a leitura com rolagem suave.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isGuidedReadingEnabled,
                        onCheckedChange = { isGuidedReadingEnabled = it }
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    // 2. Settings & Night Mode Bottom Sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Ajustes de Leitura & Tema",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Tema do Leitor:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReaderTheme.values().forEach { theme ->
                        val isSelected = selectedTheme == theme
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedTheme = theme },
                            colors = CardDefaults.cardColors(containerColor = theme.backgroundColor),
                            border = if (isSelected) CardDefaults.outlinedCardBorder().copy(width = 2.dp) else null
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = theme.title,
                                    color = theme.textColor,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Font Size Control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tamanho da Fonte:", style = MaterialTheme.typography.labelLarge)
                    Text("${fontSizeSp.toInt()} sp", fontWeight = FontWeight.Bold)
                }

                Slider(
                    value = fontSizeSp,
                    onValueChange = { fontSizeSp = it },
                    valueRange = 14f..28f,
                    steps = 6
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Font Family
                Text("Família Tipográfica:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Serif", "SansSerif", "Monospace").forEach { font ->
                        FilterChip(
                            selected = currentFontFamily == font,
                            onClick = { currentFontFamily = font },
                            label = { Text(font) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Text Alignment Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Alinhamento Justificado:", style = MaterialTheme.typography.labelLarge)
                    Switch(
                        checked = isTextJustified,
                        onCheckedChange = { isTextJustified = it }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // 3. Add Annotation & Notion Export Bottom Sheet
    if (showAddAnnotationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddAnnotationSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Anotar Trecho & Citação",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Selected Quote preview
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Trecho selecionado:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\"${selectedHighlightText.take(200)}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Highlight Color Palette
                Text("Cor do Marcador:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(6.dp))

                val colors = listOf(
                    "#FEF08A" to "Amarelo",
                    "#BBF7D0" to "Verde",
                    "#BFDBFE" to "Azul",
                    "#FBCFE8" to "Rosa",
                    "#FED7AA" to "Laranja"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    colors.forEach { (hex, name) ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        val isChosen = chosenColorHex == hex
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { chosenColorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isChosen) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = name,
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Reflection Note Input
                OutlinedTextField(
                    value = personalNoteText,
                    onValueChange = { personalNoteText = it },
                    label = { Text("Comentário ou reflexão de margem") },
                    placeholder = { Text("Ex: Conceito chave para aplicar no meu projeto...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons: Save Local & Export to Notion
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onAddAnnotation(
                                selectedHighlightText,
                                personalNoteText,
                                chosenColorHex,
                                book.currentPage,
                                activeChapter.title
                            )
                            personalNoteText = ""
                            showAddAnnotationSheet = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("button_save_annotation")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salvar")
                    }

                    OutlinedButton(
                        onClick = {
                            val tempAnnotation = AnnotationEntity(
                                bookId = book.id,
                                bookTitle = book.title,
                                bookAuthor = book.author,
                                selectedText = selectedHighlightText,
                                noteText = personalNoteText,
                                highlightColorHex = chosenColorHex,
                                chapterTitle = activeChapter.title,
                                pageNumber = book.currentPage
                            )
                            onAddAnnotation(
                                selectedHighlightText,
                                personalNoteText,
                                chosenColorHex,
                                book.currentPage,
                                activeChapter.title
                            )
                            val markdown = NotionExporter.formatSingleAnnotationForNotion(tempAnnotation)
                            NotionExporter.copyToClipboard(context, markdown, "Citação copiada para colar no Notion!")
                            showAddAnnotationSheet = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salvar & Notion")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 4. Annotations List Bottom Sheet
    if (showNotesListSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNotesListSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Anotações (${annotations.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = {
                            val markdown = NotionExporter.formatBookAnnotationsForNotion(book, annotations)
                            NotionExporter.copyToClipboard(context, markdown, "Todas as anotações copiadas para o Notion!")
                        },
                        enabled = annotations.isNotEmpty()
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exportar Notion")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (annotations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhuma anotação neste livro ainda.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        annotations.forEach { note ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(android.graphics.Color.parseColor(note.highlightColorHex)).copy(alpha = 0.2f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${note.chapterTitle} • Pág. ${note.pageNumber}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Row {
                                            IconButton(
                                                onClick = {
                                                    val md = NotionExporter.formatSingleAnnotationForNotion(note)
                                                    NotionExporter.copyToClipboard(context, md, "Citação copiada para Notion!")
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = "Copiar Notion",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { onDeleteAnnotation(note) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Excluir",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "\"${note.selectedText}\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontStyle = FontStyle.Italic
                                    )

                                    if (note.noteText.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "💡 ${note.noteText}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // 5. EPUB Table of Contents (TOC) Bottom Sheet
    if (showTocSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTocSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Índice de Capítulos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${parsedChapters.size} capítulos disponíveis no EPUB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "EPUB TOC",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    parsedChapters.forEachIndexed { idx, ch ->
                        val isCurrent = idx == currentChapterIndex
                        val chMinutes = (ch.wordCount / 200).coerceAtLeast(1)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    stopTts()
                                    currentChapterIndex = idx
                                    showTocSheet = false
                                    coroutineScope.launch { scrollState.scrollTo(0) }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "${idx + 1}.",
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = ch.title,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "${ch.paragraphs.size} parágrafos • ~$chMinutes min",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                if (isCurrent) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Lendo agora",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // --- Live Session Details Sheet (Tapped on HUD) ---
    if (showSessionDetailsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSessionDetailsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Cronômetro & Ritmo de Leitura",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Digital Timer Clock Display
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = formatSeconds(sessionSeconds),
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isTimerRunning) "● Sessão em Andamento" else "⏸️ Cronômetro Pausado",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isTimerRunning) Color(0xFF10B981) else Color(0xFFF59E0B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Controls: Pause / Play
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { isTimerRunning = !isTimerRunning },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isTimerRunning) "Pausar" else "Retomar")
                    }

                    OutlinedButton(
                        onClick = {
                            sessionSeconds = 0L
                            pagesReadInSession = 0
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Zerar")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Metrics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Velocidade Média", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (currentWpm > 0) "$currentWpm" else "--",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("palavras / min", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Páginas Lidas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (pagesReadInSession > 0) pagesReadInSession-- },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = "$pagesReadInSession",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                                IconButton(
                                    onClick = { pagesReadInSession++ },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                            Text("nesta sessão", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        showSessionDetailsSheet = false
                        handleExit()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Concluir Sessão & Atualizar Meta", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // --- Session Summary Modal Dialog (On Back / Finish) ---
    if (showSessionSummaryDialog) {
        val calculatedWords = if (pagesReadInSession > 0) pagesReadInSession * 260 else (activeChapter.wordCount.coerceIn(100, 380))
        val finalWpm = if (currentWpm > 0) currentWpm else 220
        val sessionMin = (sessionSeconds / 60).coerceAtLeast(1)

        AlertDialog(
            onDismissRequest = {
                isTimerRunning = true
                showSessionSummaryDialog = false
            },
            icon = {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEF3C7)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎉", fontSize = 28.sp)
                }
            },
            title = {
                Text(
                    text = "Sessão de Leitura Concluída!",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Parabéns pelo foco em '${book.title}'! Veja suas métricas calculadas em tempo real:",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("⏱️ Duração:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("${sessionSeconds / 60}m ${sessionSeconds % 60}s", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("📖 Páginas Lidas:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("+$pagesReadInSession págs", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("⚡ Velocidade Média:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("$finalWpm PPM", fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("🎯 Meta Semanal:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Atualizada com sucesso!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onFinishSession?.invoke(
                            sessionSeconds,
                            pagesReadInSession,
                            calculatedWords,
                            finalWpm
                        )
                        showSessionSummaryDialog = false
                        onBack()
                    },
                    modifier = Modifier.testTag("button_save_reading_session")
                ) {
                    Text("Salvar & Concluir")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isTimerRunning = true
                        showSessionSummaryDialog = false
                    }
                ) {
                    Text("Continuar Lendo")
                }
            }
        )
    }
}
