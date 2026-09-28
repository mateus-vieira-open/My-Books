package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        BookEntity::class,
        AnnotationEntity::class,
        ReadingGoalEntity::class,
        UserProfileEntity::class,
        ReadingSessionEntity::class,
        FlashcardEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun flashcardDao(): FlashcardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lumina_reader_database"
                )
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }

            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        if (database.bookDao().getBookCount() == 0) {
                            populateInitialData(database)
                        }
                    }
                }
            }
        }

        suspend fun populateInitialData(database: AppDatabase) {
            val bookDao = database.bookDao()
            val annotationDao = database.annotationDao()
            val profileDao = database.userProfileDao()
            val sessionDao = database.readingSessionDao()
            val flashcardDao = database.flashcardDao()

            if (bookDao.getBookCount() > 0) return

            // 1. Initial User Profile
            profileDao.insertOrUpdateProfile(
                UserProfileEntity(
                    id = 1,
                    name = "Mateus",
                    email = "ma2001teus23@gmail.com",
                    preferredGenres = "Teologia, Quadrinhos, Terror, Ficção",
                    isCloudSyncEnabled = true,
                    lastSyncTimestamp = System.currentTimeMillis() - 1000 * 60 * 15,
                    dailyReminderTime = "20:00",
                    notionDatabaseUrl = "https://notion.so/workspace/Lumina-Reading-DB",
                    isNightModeForced = false
                )
            )

            // 2. Initial Reading Goal with speed & session metrics
            profileDao.insertOrUpdateGoal(
                ReadingGoalEntity(
                    id = 1,
                    weeklyTargetPages = 150,
                    weeklyPagesRead = 114,
                    dailyStreak = 7,
                    lastReadDateMillis = System.currentTimeMillis(),
                    totalMinutesRead = 410,
                    readerLevel = "Leitor Voraz 🌟",
                    averageWpm = 230,
                    totalSessionsCount = 8
                )
            )

            // 3. Pre-loaded high-value E-Books matching user's core genres
            val book1Id = bookDao.insertBook(
                BookEntity(
                    title = "Ortodoxia",
                    author = "G.K. Chesterton",
                    isbn = "9788563160270",
                    genre = "Teologia",
                    description = "Uma das maiores defesas da fé cristã e da racionalidade cósmica, escrita com humor brilhante, paradoxos vivos e imaginação teológica aguçada.",
                    coverUrl = "https://covers.openlibrary.org/b/isbn/9788563160270-M.jpg",
                    coverColorHex = "#1E3A8A", // Deep Navy
                    totalPages = 240,
                    currentPage = 68,
                    status = "READING",
                    rating = 5,
                    chapters = "I. Introdução em Defesa de Tudo, II. O Maníaco, III. O Suicídio do Pensamento, IV. A Ética do País das Fadas, V. A Bandeira do Mundo",
                    content = """
Capítulo I: Introdução em Defesa de Tudo

Não posso conceber nada de mais desdenhoso do que o sujeito que imagina que a sua própria época é a medida de todas as eras. O objetivo deste livro é expor como certas coisas simples e fantásticas me levaram a crer que a ortodoxia é a única coisa verdadeiramente rebelde e viva.

Quando os homens deixam de acreditar em Deus, o perigo não é que não acreditem em nada, mas que passem a acreditar em qualquer coisa. A razão humana necessita de um porto seguro para não naufragar na tempestade da sua própria presunção.

Capítulo II: O Maníaco

Se um homem disser que é uma lâmpada ou um camelo, pode estar enganado; mas se disser que não há mistério no cosmos, está verdadeiramente louco. O homem que não crê no mistério crê na fatalidade. O mundo não é um teorema de lógica pura; o mundo é um drama divino de poesia e redenção.

A imaginação é a chave que abre a cela da mente cartesiana fechada. É preciso ter humildade diante da luz para enxergar as cores da criação.
                    """.trimIndent(),
                    lastReadTimestamp = System.currentTimeMillis() - 1000 * 60 * 45,
                    isSyncedWithCloud = true
                )
            )

            val book2Id = bookDao.insertBook(
                BookEntity(
                    title = "O Chamado de Cthulhu & Outros Contos",
                    author = "H.P. Lovecraft",
                    isbn = "9788573264661",
                    genre = "Terror",
                    description = "O ápice do terror cósmico: o confronto da fragilidade humana contra as forças arcanas e colossais adormecidas sob os oceanos.",
                    coverUrl = "https://covers.openlibrary.org/b/isbn/9788573264661-M.jpg",
                    coverColorHex = "#064E3B", // Dark Emerald / Abyss
                    totalPages = 180,
                    currentPage = 142,
                    status = "READING",
                    rating = 5,
                    chapters = "I. O Horror em Argila, II. O Relato do Inspetor Legrasse, III. A Loucura Vinda do Mar",
                    content = """
Capítulo I: O Horror em Argila

A coisa mais misericordiosa do mundo, creio eu, é a incapacidade da mente humana de correlacionar todo o seu conteúdo. Vivemos em uma plácida ilha de ignorância em meio a mares negros de infinitude, e não fomos feitos para viajar para longe.

As ciências, cada uma se esforçando em sua própria direção, pouco nos prejudicaram até agora; mas algum dia a união de conhecimentos dissociados descortinará visões tão terríveis da realidade e de nossa assustadora posição nela que enlouqueceremos com a revelação, ou fugiremos da luz mortal para a paz e segurança de uma nova idade das trevas.

O manuscrito estava cercado de anotações sobre cultos bizarros espalhados pelo globo, com cânticos ininteligíveis que ecoavam: Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn.
                    """.trimIndent(),
                    lastReadTimestamp = System.currentTimeMillis() - 1000 * 60 * 180,
                    isSyncedWithCloud = true
                )
            )

            val book3Id = bookDao.insertBook(
                BookEntity(
                    title = "Crônicas do Multiverso Gráfico",
                    author = "Alan Moore & Neil Gaiman (Org.)",
                    isbn = "9788573516548",
                    genre = "Quadrinhos",
                    description = "Uma imersão na narrativa visual, explorando as técnicas revolucionárias das graphic novels, arcos de heróis obscuros e a arte do roteiro sequencial.",
                    coverUrl = "",
                    coverColorHex = "#7C2D12", // Warm Rust / Comics Sepia
                    totalPages = 210,
                    currentPage = 210,
                    status = "COMPLETED",
                    rating = 5,
                    chapters = "I. O Quadro Invisível, II. O Tempo Congelado em Tinta, III. A Anatomia dos Heróis Imperfeitos",
                    content = """
Capítulo I: O Quadro Invisível

Nos quadrinhos, a mágica não reside apenas no que está desenhado no painel, mas principalmente no espaço vazio entre eles — o que chamamos de sarjeta. É a mente do leitor que transforma duas imagens estáticas em um movimento vivo e palpável.

A nona arte combina o imediatismo do olhar com a cadência rítmica da literatura. Cada enquadramento é uma escolha política, poética e temporal. Quando o herói hesita, o quadro se estica; quando o perigo irrompe, a página se fragmenta em ângulos agudos.
                    """.trimIndent(),
                    lastReadTimestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 2,
                    isSyncedWithCloud = true
                )
            )

            bookDao.insertBook(
                BookEntity(
                    title = "A Máquina do Tempo",
                    author = "H.G. Wells",
                    isbn = "9788544001851",
                    genre = "Ficção",
                    description = "O clássico fundador da ficção científica moderna sobre o viajante que se projeta até o ano 802.701 e encontra a humanidade dividida entre Eloi e Morlocks.",
                    coverUrl = "https://covers.openlibrary.org/b/isbn/9788544001851-M.jpg",
                    coverColorHex = "#4C1D95", // Deep Violet
                    totalPages = 160,
                    currentPage = 0,
                    status = "TO_READ",
                    rating = 4,
                    chapters = "I. A Quarta Dimensão, II. O Mecanismo da Partida, III. O Pôr do Sol da Humanidade",
                    content = """
Capítulo I: A Quarta Dimensão

O Viajante do Tempo (pois é assim que convém chamá-lo) nos expunha uma matéria profunda. Seus olhos cinzentos brilhavam e faiscavam, e seu rosto habitualmente pálido estava ruborizado e animado. O fogo da lareira crepitava com alegria, e a suave luminosidade das lâmpadas incandescentes sobre os prismas de prata dava à sala uma atmosfera de agradável intimismo.

— Qualquer corpo real deve se estender em quatro direções: deve ter Comprimento, Largura, Espessura e Duração. Mas, por uma fraqueza natural dos nossos sentidos, tendemos a ignorar esse quarto fator.
                    """.trimIndent(),
                    lastReadTimestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 5,
                    isSyncedWithCloud = true
                )
            )

            val epubBookId = bookDao.insertBook(
                BookEntity(
                    title = "Dom Casmurro (Edição EPUB)",
                    author = "Machado de Assis",
                    isbn = "9788535911664",
                    genre = "EPUB",
                    description = "Edição digital completa em formato EPUB com navegação por capítulos, suporte ao modo noturno e anotações. A história imortal de Bentinho, Capitu e o enigma dos olhos de ressaca.",
                    coverUrl = "https://covers.openlibrary.org/b/isbn/9788535911664-M.jpg",
                    coverColorHex = "#0E7490", // Cyan / Teal Slate
                    totalPages = 256,
                    currentPage = 12,
                    status = "READING",
                    rating = 5,
                    chapters = "I. Do Título, II. Do Livro, III. A Denúncia, IV. Um Dever Severo, V. O Segredo de Capitu",
                    content = """
## Capítulo I: Do Título

Uma noite destas, vindo da cidade para o Engenho Novo, encontrei no trem da Central um rapaz aqui do bairro, que eu conheço de vista e de chapéu. Cumprimentou-me, sentou-se ao pé de mim, falou da lua e dos ministros, e acabou recitando-me versos. A viagem era curta, e os versos pode ser que não fossem inteiramente maus, porém o caso é que o sono foi mais forte do que a poesia.

Ao acordar, não achei mais o rapaz; e os passageiros que lá ficavam, rindo disfarçadamente, pareciam achar engraçada a minha soneca. No dia seguinte entraram a chamar-me de Dom Casmurro.

A alcunha pegou de tal jeito que os vizinhos esqueceram o meu nome de batismo. Não me zanguei. Há alcunhas piores que essa. Não consultem dicionários; casmurro não está aqui no sentido de teimoso ou obstinado, mas no de homem calado, metido consigo mesmo.

## Capítulo II: Do Livro

Agora que expliquei o título, passo a escrever o livro. Por que o faço? Porque não sei que melhor negócio dar a estes dias de retiro. A velhice tem destas coisas: traz a vontade de catar lembranças e de as reconstruir como eram na mocidade.

Meu fim evidente era atar as duas pontas da vida, e restaurar na velhice a adolescência. Pois, senhor, não consegui recompor o que foi nem o que fui. Em tudo, se o rosto é igual, a fisionomia é diferente. Se só me faltassem os outros, vá; um homem consola-se mais ou menos das pessoas que perde; mas falto eu mesmo, e esta lacuna é tudo.

Mandei pintar nesta casa do Engenho Novo uma reprodução exata daquela antiga de Matacavalos. As mesmas alcovas, os mesmos bustos de César e Augusto nas paredes, as mesmas persianas verdes. Mas falta a alma do tempo que se foi.

## Capítulo III: A Denúncia

Ia a entrar na sala de visitas, quando ouvi proferir o meu nome e escondi-me atrás da porta. A casa era a da Rua de Matacavalos, o mês novembro, o ano é que é um tanto remoto, mas eu não hei de trocar as datas da minha vida só para agradar aos que não amam histórias velhas; o ano era 1857.

— D. Glória, a senhora persiste na ideia de meter o nosso Bentinho no seminário? É mais que tempo, e já agora pode haver uma dificuldade.

— Que dificuldade?

— Uma grande dificuldade. José Dias suspendeu a frase, olhou para a minha mãe com aquele ar de gravidade que lhe era peculiar, e aproximou-se em passo medido. Não viu que o menino Bentinho escutava tudo através da fresta da porta de cedro.

## Capítulo IV: Um Dever Severo

Minha mãe ficou pálida. Prometera a Deus, antes de eu nascer, que se tivesse um varão iria dá-lo à Igreja. Essa promessa era para ela um peso de chumbo na consciência e uma flor de devoção no altar.

— Que dificuldade vê você, José Dias? — perguntou ela com voz trêmula.

— A dificuldade são os olhos da menina de Pádua. Capitu anda muito achegada ao Bentinho. Vivem aos segredinhos pelo quintal, colhendo pitangas e desenhando no muro. Se o menino for para o seminário agora, tudo se ajeita. Se demorar mais um ano, receio que o laço fique atado de vez.

## Capítulo V: O Segredo de Capitu

Capitu tinha então quatorze anos. Olhos de ressaca, olhos de cigana oblíqua e dissimulada. Quando nos encontramos no muro do fundo, contei-lhe o que ouvira sobre o seminário e o padre.

Ela mordeu os lábios, fitou-me com aquela profundidade serena que me assustava e fascinava ao mesmo tempo, e disse baixinho:

— Não chores, Bentinho. Eles querem te mandar para São Leopoldo, mas nós não deixaremos. Hás de ser doutor, e não padre. Confia em mim.
                    """.trimIndent(),
                    lastReadTimestamp = System.currentTimeMillis() - 1000 * 60 * 20,
                    isSyncedWithCloud = true
                )
            )

            // Initial Sample Annotations
            annotationDao.insertAnnotation(
                AnnotationEntity(
                    bookId = book1Id,
                    bookTitle = "Ortodoxia",
                    bookAuthor = "G.K. Chesterton",
                    selectedText = "Quando os homens deixam de acreditar em Deus, o perigo não é que não acreditem em nada, mas que passem a acreditar em qualquer coisa.",
                    noteText = "Citação clássica sobre o vazio ideológico e o ceticismo cego. Excelente para o Notion DB de Teologia.",
                    highlightColorHex = "#FEF08A",
                    chapterTitle = "Capítulo I",
                    pageNumber = 14,
                    createdAt = System.currentTimeMillis() - 1000 * 60 * 30,
                    exportedToNotion = true
                )
            )

            annotationDao.insertAnnotation(
                AnnotationEntity(
                    bookId = book2Id,
                    bookTitle = "O Chamado de Cthulhu & Outros Contos",
                    bookAuthor = "H.P. Lovecraft",
                    selectedText = "A coisa mais misericordiosa do mundo, creio eu, é a incapacidade da mente humana de correlacionar todo o seu conteúdo.",
                    noteText = "O conceito central do Horror Cósmico: a limitação protetora da consciência humana diante do infinito.",
                    highlightColorHex = "#BBF7D0",
                    chapterTitle = "Capítulo I",
                    pageNumber = 9,
                    createdAt = System.currentTimeMillis() - 1000 * 60 * 120,
                    exportedToNotion = false
                )
            )

            annotationDao.insertAnnotation(
                AnnotationEntity(
                    bookId = book3Id,
                    bookTitle = "Crônicas do Multiverso Gráfico",
                    bookAuthor = "Alan Moore & Neil Gaiman (Org.)",
                    selectedText = "Nos quadrinhos, a mágica não reside apenas no que está desenhado no painel, mas principalmente no espaço vazio entre eles — a sarjeta.",
                    noteText = "Fundamento da arte sequencial segundo Scott McCloud. A mente do leitor fecha o arco da ação.",
                    highlightColorHex = "#BFDBFE",
                    chapterTitle = "Capítulo I",
                    pageNumber = 35,
                    createdAt = System.currentTimeMillis() - 1000 * 60 * 60 * 20,
                    exportedToNotion = true
                )
            )

            annotationDao.insertAnnotation(
                AnnotationEntity(
                    bookId = epubBookId,
                    bookTitle = "Dom Casmurro (Edição EPUB)",
                    bookAuthor = "Machado de Assis",
                    selectedText = "Capitu tinha então quatorze anos. Olhos de ressaca, olhos de cigana oblíqua e dissimulada.",
                    noteText = "A célebre descrição psicológica e visual dos olhos de Capitu. Citação guardada para reflexão no Notion.",
                    highlightColorHex = "#FED7AA",
                    chapterTitle = "Capítulo V: O Segredo de Capitu",
                    pageNumber = 12,
                    createdAt = System.currentTimeMillis() - 1000 * 60 * 15,
                    exportedToNotion = true
                )
            )

            // Initial Sample Reading Sessions
            sessionDao.insertSession(
                ReadingSessionEntity(
                    bookId = book1Id,
                    bookTitle = "Ortodoxia",
                    durationSeconds = 1240L, // ~20 min
                    pagesRead = 12,
                    wordsRead = 3120,
                    speedWpm = 235,
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 3
                )
            )

            sessionDao.insertSession(
                ReadingSessionEntity(
                    bookId = epubBookId,
                    bookTitle = "Dom Casmurro (Edição EPUB)",
                    durationSeconds = 890L, // ~15 min
                    pagesRead = 8,
                    wordsRead = 2100,
                    speedWpm = 220,
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 24
                )
            )

            // Initial Sample Anki Flashcards for Chapters Read
            flashcardDao.insertFlashcards(
                listOf(
                    FlashcardEntity(
                        bookId = book1Id,
                        bookTitle = "Ortodoxia",
                        chapterTitle = "Capítulo I: Introdução em Defesa de Tudo",
                        frontQuestion = "Qual é o paradoxo central de Chesterton sobre a perda de fé em Deus?",
                        backAnswer = "Quando os homens deixam de acreditar em Deus, o perigo não é que não passem a acreditar em nada, mas sim que passem a acreditar em qualquer coisa.",
                        keyQuote = "O perigo não é que não acreditem em nada, mas que acreditem em qualquer coisa.",
                        sourceType = "APP",
                        masteryLevel = "BOM",
                        reviewCount = 2,
                        lastReviewedAt = System.currentTimeMillis() - 1000 * 60 * 60 * 12
                    ),
                    FlashcardEntity(
                        bookId = book1Id,
                        bookTitle = "Ortodoxia",
                        chapterTitle = "Capítulo II: O Maníaco",
                        frontQuestion = "Como Chesterton define a diferença entre o poeta e o maníaco/louco?",
                        backAnswer = "O louco não perdeu a razão; o louco perdeu tudo exceto a razão. O poeta busca a transcendência e a maravilha, enquanto o maníaco é prisioneiro de uma lógica circular e claustrofóbica.",
                        keyQuote = "O homem que não crê no mistério crê na fatalidade.",
                        sourceType = "APP",
                        masteryLevel = "APRENDENDO",
                        reviewCount = 1,
                        lastReviewedAt = System.currentTimeMillis() - 1000 * 60 * 60 * 20
                    ),
                    FlashcardEntity(
                        bookId = epubBookId,
                        bookTitle = "Dom Casmurro (Edição EPUB)",
                        chapterTitle = "Capítulo V: O Segredo de Capitu",
                        frontQuestion = "Como Bentinho descreve a expressão e o olhar de Capitu aos 14 anos?",
                        backAnswer = "Descreve como 'olhos de ressaca, olhos de cigana oblíqua e dissimulada', com uma profundidade serena que o assustava e fascinava como a vaga do mar.",
                        keyQuote = "Olhos de ressaca, olhos de cigana oblíqua e dissimulada.",
                        sourceType = "APP",
                        masteryLevel = "DOMINADO",
                        reviewCount = 4,
                        lastReviewedAt = System.currentTimeMillis() - 1000 * 60 * 60 * 6
                    ),
                    FlashcardEntity(
                        bookId = 0L,
                        bookTitle = "Sapiens: Uma Breve História da Humanidade",
                        chapterTitle = "Capítulo 1: Um Animal de Pouca Importância",
                        frontQuestion = "O que permitiu ao Homo sapiens cooperar em grupos de mais de 150 indivíduos?",
                        backAnswer = "A Revolução Cognitiva e a capacidade única de criar e acreditar em realidades imaginadas (mitos, nações, leis e religiões compartilhadas).",
                        keyQuote = "A ficção nos permitiu não apenas imaginar coisas, mas fazê-lo coletivamente.",
                        sourceType = "FISICO",
                        masteryLevel = "NOVO",
                        reviewCount = 0
                    ),
                    FlashcardEntity(
                        bookId = 0L,
                        bookTitle = "1984 (Edição Kindle)",
                        chapterTitle = "Parte 1 - Capítulo 1",
                        frontQuestion = "Quais são os três lemas do Partido estampados na fachada do Ministério da Verdade?",
                        backAnswer = "1. GUERRA É PAZ\n2. LIBERDADE É ESCRAVIDÃO\n3. IGNORÂNCIA É FORÇA",
                        keyQuote = "Quem controla o passado controla o futuro; quem controla o presente controla o passado.",
                        sourceType = "KINDLE",
                        masteryLevel = "NOVO",
                        reviewCount = 0
                    )
                )
            )
        }
    }
}
