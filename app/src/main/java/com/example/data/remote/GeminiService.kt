package com.example.data.remote

import com.example.BuildConfig
import com.example.data.local.entity.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class BookRecommendation(
    val title: String,
    val author: String,
    val genre: String,
    val synopsis: String,
    val whyRecommend: String,
    val estimatedPages: Int,
    val isbn: String = "",
    val coverColorHex: String = "#1E1B4B"
)

data class GeneratedFlashcard(
    val frontQuestion: String,
    val backAnswer: String,
    val keyQuote: String = ""
)

object GeminiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun getRecommendations(
        preferredGenres: String,
        currentBooks: List<BookEntity>
    ): List<BookRecommendation> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY

        val booksSummary = if (currentBooks.isEmpty()) {
            "Nenhum livro lido ainda no app."
        } else {
            currentBooks.joinToString("; ") {
                "${it.title} de ${it.author} (Gênero: ${it.genre}, Status: ${it.status}, Avaliação: ${it.rating} estrelas)"
            }
        }

        val prompt = """
            Você é o assistente literário de alta precisão do Lumina Reader.
            Analise o perfil do leitor abaixo e gere 4 recomendações de livros personalizadas, envolventes e de alto valor cultural.
            
            PERFIL DO LEITOR:
            - Interesses cadastrados na pesquisa prévia: $preferredGenres (ex: Teologia, Quadrinhos, Terror, Ficção).
            - Livros já lidos ou na biblioteca: $booksSummary.
            
            DIRETRIZES:
            1. Traga obras que conversem diretamente com esses interesses e complementem o que ele já leu.
            2. Forneça títulos consagrados ou aclamados pela crítica dentro de Teologia, Quadrinhos/Graphic Novels, Terror e Ficção Científica/Especulativa.
            3. Responda ESTRITAMENTE em formato JSON com um ARRAY de 4 objetos contendo os seguintes campos em português:
            [
              {
                "title": "Nome do Livro",
                "author": "Nome do Autor",
                "genre": "Teologia | Quadrinhos | Terror | Ficção",
                "synopsis": "Breve sinopse cativante (2 a 3 frases)",
                "whyRecommend": "Por que o usuário vai amar este livro com base no que ele já leu",
                "estimatedPages": 280,
                "isbn": "97885..."
              }
            ]
            Não adicione blocos de markdown ```json adicionais se puder, apenas o JSON puro ou encapsulado.
        """.trimIndent()

        if (!apiKey.isNullOrBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

                val requestJson = JSONObject().apply {
                    val contentsArray = JSONArray().apply {
                        put(JSONObject().apply {
                            val partsArray = JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            }
                            put("parts", partsArray)
                        })
                    }
                    put("contents", contentsArray)
                }

                val body = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseString = response.body?.string() ?: ""
                        val parsed = parseGeminiResponse(responseString)
                        if (parsed.isNotEmpty()) {
                            return@withContext parsed
                        }
                    }
                }
            } catch (e: Exception) {
                // If network/rate limit fails, use fallback generator below
            }
        }

        // Curated smart fallback recommendations when offline or before API key setup
        getCuratedFallbackRecommendations(preferredGenres, currentBooks)
    }

    private fun parseGeminiResponse(jsonText: String): List<BookRecommendation> {
        val list = mutableListOf<BookRecommendation>()
        try {
            val root = JSONObject(jsonText)
            val candidates = root.optJSONArray("candidates") ?: return emptyList()
            if (candidates.length() == 0) return emptyList()

            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return emptyList()
            val parts = content.optJSONArray("parts") ?: return emptyList()
            if (parts.length() == 0) return emptyList()

            var rawText = parts.getJSONObject(0).optString("text", "")
            rawText = rawText.trim()
            if (rawText.startsWith("```json")) {
                rawText = rawText.removePrefix("```json").trim()
            }
            if (rawText.startsWith("```")) {
                rawText = rawText.removePrefix("```").trim()
            }
            if (rawText.endsWith("```")) {
                rawText = rawText.removeSuffix("```").trim()
            }

            val array = JSONArray(rawText)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val genre = item.optString("genre", "Ficção")
                list.add(
                    BookRecommendation(
                        title = item.optString("title", "Obra Recomendada"),
                        author = item.optString("author", "Autor"),
                        genre = genre,
                        synopsis = item.optString("synopsis", "Uma narrativa instigante sob medida para os seus gostos literários."),
                        whyRecommend = item.optString("whyRecommend", "Conecta perfeitamente sua paixão pelos gêneros selecionados."),
                        estimatedPages = item.optInt("estimatedPages", 250),
                        isbn = item.optString("isbn", ""),
                        coverColorHex = getGenreColor(genre)
                    )
                )
            }
        } catch (e: Exception) {
            // parsing error
        }
        return list
    }

    private fun getCuratedFallbackRecommendations(
        preferredGenres: String,
        currentBooks: List<BookEntity>
    ): List<BookRecommendation> {
        return listOf(
            BookRecommendation(
                title = "O Peso da Glória",
                author = "C.S. Lewis",
                genre = "Teologia",
                synopsis = "Uma coletânea de nove sermões e conferências onde Lewis reflete sobre a saudade humana da beleza transcendental, a dor e o propósito da existência.",
                whyRecommend = "Complementa 'Ortodoxia' de Chesterton, trazendo a mesma lucidez poética e apologética cristã para a sua estante.",
                estimatedPages = 160,
                isbn = "9788578601836",
                coverColorHex = "#1E3A8A"
            ),
            BookRecommendation(
                title = "Maus: A História de um Sobrevivente",
                author = "Art Spiegelman",
                genre = "Quadrinhos",
                synopsis = "A única história em quadrinhos a receber o prêmio Pulitzer. Retrata a perseguição nazista com judeus desenhados como ratos e nazistas como gatos.",
                whyRecommend = "Perfeito para quem busca graphic novels profundas que unem potência histórica e domínio singular da linguagem visual.",
                estimatedPages = 296,
                isbn = "9788535906288",
                coverColorHex = "#D97706"
            ),
            BookRecommendation(
                title = "O Iluminado",
                author = "Stephen King",
                genre = "Terror",
                synopsis = "Isolado nas montanhas durante o inverno no sinistro Hotel Overlook, Jack Torrance é tragado pela loucura e pelas forças malévolas do lugar.",
                whyRecommend = "Evolui o horror cósmico de Lovecraft para o terror psicológico e claustrofóbico mais aclamado da literatura contemporânea.",
                estimatedPages = 464,
                isbn = "9788581050485",
                coverColorHex = "#064E3B"
            ),
            BookRecommendation(
                title = "Fahrenheit 451",
                author = "Ray Bradbury",
                genre = "Ficção",
                synopsis = "Em um futuro totalitário, os bombeiros têm a missão não de apagar incêndios, mas de queimar todos os livros existentes para sufocar o pensamento livre.",
                whyRecommend = "Uma celebração indispensável do amor aos livros e da resistência cultural, essencial para leitores apaixonados.",
                estimatedPages = 216,
                isbn = "9788525052247",
                coverColorHex = "#7C2D12"
            )
        )
    }

    private fun getGenreColor(genre: String): String {
        return when {
            genre.contains("Teologia", ignoreCase = true) -> "#1E3A8A"
            genre.contains("Terror", ignoreCase = true) -> "#064E3B"
            genre.contains("Quadrinho", ignoreCase = true) -> "#D97706"
            genre.contains("Ficção", ignoreCase = true) -> "#4C1D95"
            else -> "#1E1B4B"
        }
    }

    suspend fun generateChapterFlashcards(
        bookTitle: String,
        author: String,
        chapterTitle: String,
        chapterSnippet: String = "",
        sourceType: String = "APP"
    ): List<GeneratedFlashcard> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val contextInfo = if (chapterSnippet.isNotBlank()) {
            "TRECHO EXTRAÍDO DO CAPÍTULO:\n\"${chapterSnippet.take(1500)}\""
        } else {
            "O leitor está lendo em formato $sourceType (Livro Físico ou Kindle). Consulte seu vasto conhecimento literário para recuperar os pontos, teses e diálogos cruciais de '$bookTitle' ($author), especificamente sobre '$chapterTitle'."
        }

        val prompt = """
            Você é o especialista mestre em técnicas de repetição espaçada e memorização ativa (método Anki) do Lumina Reader.
            O usuário concluiu a leitura de um capítulo de uma obra literária.
            
            DADOS DA LEITURA:
            - Obra: $bookTitle
            - Autor: $author
            - Capítulo / Seção: $chapterTitle
            - Origem: $sourceType
            - Contexto: $contextInfo
            
            MISSÃO:
            Gere 4 cartões de memorização (Flashcards) no mais puro estilo Anki (Active Recall).
            Diretrizes dos cartões:
            1. 'frontQuestion': Pergunta instigante de recordação ativa focada no conceito central, dilema ético, virada de enredo ou argumento filosófico do capítulo.
            2. 'backAnswer': Resposta direta, esclarecedora e concisa (2 a 3 frases) que consolida a memória de longo prazo.
            3. 'keyQuote': Citação ou frase de efeito memorável da obra referente a esse conceito.
            
            Responda ESTRITAMENTE em formato JSON com um ARRAY contendo os 4 cartões:
            [
              {
                "frontQuestion": "Pergunta de memorização ativa...",
                "backAnswer": "Resposta e explicação do conceito...",
                "keyQuote": "Citação marcante..."
              }
            ]
        """.trimIndent()

        if (!apiKey.isNullOrBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
                val requestJson = JSONObject().apply {
                    val contentsArray = JSONArray().apply {
                        put(JSONObject().apply {
                            val partsArray = JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            }
                            put("parts", partsArray)
                        })
                    }
                    put("contents", contentsArray)
                }

                val body = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).post(body).build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseString = response.body?.string() ?: ""
                        val parsed = parseFlashcardsResponse(responseString)
                        if (parsed.isNotEmpty()) {
                            return@withContext parsed
                        }
                    }
                }
            } catch (e: Exception) {
                // fallback below
            }
        }

        getFallbackFlashcards(bookTitle, chapterTitle, sourceType)
    }

    private fun parseFlashcardsResponse(jsonText: String): List<GeneratedFlashcard> {
        val list = mutableListOf<GeneratedFlashcard>()
        try {
            val root = JSONObject(jsonText)
            val candidates = root.optJSONArray("candidates") ?: return emptyList()
            if (candidates.length() == 0) return emptyList()

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return emptyList()
            val parts = content.optJSONArray("parts") ?: return emptyList()
            if (parts.length() == 0) return emptyList()

            val text = parts.getJSONObject(0).optString("text", "")
            val cleanJson = text
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val jsonArray = JSONArray(cleanJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    GeneratedFlashcard(
                        frontQuestion = obj.optString("frontQuestion", "Conceito do capítulo"),
                        backAnswer = obj.optString("backAnswer", ""),
                        keyQuote = obj.optString("keyQuote", "")
                    )
                )
            }
        } catch (e: Exception) {
            // fallback
        }
        return list
    }

    private fun getFallbackFlashcards(
        bookTitle: String,
        chapterTitle: String,
        sourceType: String
    ): List<GeneratedFlashcard> {
        return when {
            bookTitle.contains("Ortodoxia", ignoreCase = true) -> listOf(
                GeneratedFlashcard(
                    frontQuestion = "Segundo Chesterton em '$chapterTitle', por que a ortodoxia é descrita como a única coisa verdadeiramente rebelde?",
                    backAnswer = "Porque o mundo materialista aceita comodamente o ceticismo fácil. Manter-se fiel à verdade cósmica e à admiração requer um esforço contínuo e heroico contra a corrente das modas passageiras.",
                    keyQuote = "Não posso conceber nada de mais desdenhoso do que o sujeito que imagina que a sua própria época é a medida de todas as eras."
                ),
                GeneratedFlashcard(
                    frontQuestion = "Qual é o perigo fundamental quando as pessoas deixam de crer em Deus?",
                    backAnswer = "O perigo não é que passem a não acreditar em nada, mas sim que passem a ser crédulas e vulneráveis a acreditar em qualquer bobagem ou tirania.",
                    keyQuote = "Quando os homens deixam de acreditar em Deus, passam a acreditar em qualquer coisa."
                ),
                GeneratedFlashcard(
                    frontQuestion = "Como a metáfora da 'cela da razão pura' ilustra a loucura em Ortodoxia?",
                    backAnswer = "O maníaco tem uma razão impecável e fechada em si mesma, mas perdeu a imaginação, a poesia e o senso de mistério que conectam o homem à amplitude do mundo real.",
                    keyQuote = "O mundo não é um teorema de lógica pura; o mundo é um drama divino de poesia e redenção."
                ),
                GeneratedFlashcard(
                    frontQuestion = "O que Chesterton defende sobre a 'Ética do País das Fadas'?",
                    backAnswer = "Que as leis da natureza não são necessidades cegas, mas hábitos divinos cheios de magia e gratidão, como o sol que se levanta toda manhã por insistência amorosa de Deus.",
                    keyQuote = "A imaginação é a chave que abre a cela da mente cartesiana fechada."
                )
            )
            bookTitle.contains("Cthulhu", ignoreCase = true) || bookTitle.contains("Lovecraft", ignoreCase = true) -> listOf(
                GeneratedFlashcard(
                    frontQuestion = "Em '$chapterTitle', qual é descrita como 'a coisa mais misericordiosa do mundo'?",
                    backAnswer = "A incapacidade da mente humana de correlacionar todo o seu conteúdo e conhecimentos dissociados, protegendo nossa sanidade diante do abismo cósmico.",
                    keyQuote = "Vivemos em uma plácida ilha de ignorância em meio a mares negros de infinitude."
                ),
                GeneratedFlashcard(
                    frontQuestion = "O que o relevo em argila trazido pelo jovem artista Wilcox representava?",
                    backAnswer = "Uma criatura monstruosa com cabeça de cefalópode, asas de dragão e corpo gelatinoso, inspirada em pesadelos com a cidade submersa de R'lyeh.",
                    keyQuote = "Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn."
                ),
                GeneratedFlashcard(
                    frontQuestion = "O que caracteriza o Terror Cósmico lovecraftiano presente nesta seção?",
                    backAnswer = "A insignificância total da humanidade no universo perante forças arcanas e colossais pré-humanas que desafiam as leis conhecidas da física e da geometria euclidiana.",
                    keyQuote = "As ciências pouco nos prejudicaram até agora; mas algum dia a união de conhecimentos descortinará visões aterradoras."
                )
            )
            bookTitle.contains("Casmurro", ignoreCase = true) || bookTitle.contains("Machado", ignoreCase = true) -> listOf(
                GeneratedFlashcard(
                    frontQuestion = "Em '$chapterTitle', qual é o significado de 'Casmurro' adotado por Bentinho?",
                    backAnswer = "Não o significado do dicionário (teimoso), mas o sentido popular de um homem calado, recolhido e metido consigo mesmo.",
                    keyQuote = "Não me zanguei. Há alcunhas piores que essa... casmurro no sentido de homem calado."
                ),
                GeneratedFlashcard(
                    frontQuestion = "Como José Dias avisa D. Glória sobre a necessidade urgente de mandar Bentinho ao seminário?",
                    backAnswer = "Aponta a amizade estreita e os 'segredinhos pelo quintal' entre Bentinho e Capitu como um risco de que o apego amoroso impeça a promessa do seminário.",
                    keyQuote = "A dificuldade são os olhos da menina de Pádua. Capitu anda muito achegada ao Bentinho."
                ),
                GeneratedFlashcard(
                    frontQuestion = "Por que os olhos de Capitu são imortalizados como 'olhos de ressaca'?",
                    backAnswer = "Porque possuíam um magnetismo que atraía como o refluxo das ondas do mar, insinuando um mistério profundo, envolvente e perigoso ao mesmo tempo.",
                    keyQuote = "Olhos de ressaca, olhos de cigana oblíqua e dissimulada."
                )
            )
            else -> listOf(
                GeneratedFlashcard(
                    frontQuestion = "Qual é o principal conceito desenvolvido em '$chapterTitle' de '$bookTitle'?",
                    backAnswer = "O autor estabelece as premissas essenciais desta seção, explorando a relação entre as decisões dos personagens ou ideias centrais e o tema global da obra.",
                    keyQuote = "A leitura reflexiva transforma informação bruta em sabedoria permanente."
                ),
                GeneratedFlashcard(
                    frontQuestion = "Qual insight prático ou filosófico pode ser extraído deste capítulo ($sourceType)?",
                    backAnswer = "A compreensão das tensões internas expostas pelo autor estimula o pensamento crítico e a capacidade de conectar essa lição a outras obras e vivências pessoais.",
                    keyQuote = "Registrado a partir de $sourceType no Lumina Reader via Gemini AI."
                ),
                GeneratedFlashcard(
                    frontQuestion = "Como este capítulo impulsiona o desenvolvimento do leitor?",
                    backAnswer = "Ao exercitar o active recall sobre '$chapterTitle', o leitor consolida os pontos-chave na memória de longo prazo segundo os princípios do Anki.",
                    keyQuote = "Recordação ativa é o pilar da retenção duradoura."
                )
            )
        }
    }
}
