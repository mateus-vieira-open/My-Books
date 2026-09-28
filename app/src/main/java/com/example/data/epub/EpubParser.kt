package com.example.data.epub

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class EpubChapter(
    val id: String,
    val title: String,
    val href: String,
    val plainText: String,
    val htmlContent: String
)

data class EpubBook(
    val title: String,
    val author: String,
    val language: String,
    val description: String,
    val coverImageBytes: ByteArray? = null,
    val chapters: List<EpubChapter>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EpubBook
        if (title != other.title) return false
        if (author != other.author) return false
        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + author.hashCode()
        return result
    }
}

object EpubParser {

    suspend fun parseFromUri(context: Context, uri: Uri): Result<EpubBook> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Não foi possível abrir o arquivo EPUB."))
            val result = parseFromInputStream(inputStream)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseFromInputStream(inputStream: InputStream): EpubBook {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    files[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        // 1. Locate OPF file path from META-INF/container.xml
        val opfPath = findOpfPath(files["META-INF/container.xml"]) ?: "content.opf"
        val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

        // 2. Parse OPF file
        val opfBytes = files[opfPath] ?: files.entries.firstOrNull { it.key.endsWith(".opf") }?.value
            ?: throw Exception("Arquivo descritor OPF não encontrado dentro do EPUB.")

        val (metadata, manifest, spine) = parseOpf(opfBytes)

        // 3. Extract Cover Image if available
        var coverBytes: ByteArray? = null
        val coverId = metadata["cover"]
        val coverHref = if (coverId != null) {
            manifest[coverId]?.href
        } else {
            manifest.values.firstOrNull { it.id.contains("cover", ignoreCase = true) || it.href.contains("cover", ignoreCase = true) }?.href
        }
        if (coverHref != null) {
            val fullCoverPath = normalizePath(opfDir + coverHref)
            coverBytes = files[fullCoverPath] ?: files[coverHref]
        }

        // 4. Parse Chapters according to Spine order
        val chapters = mutableListOf<EpubChapter>()
        var chapterIndex = 1

        for (itemRef in spine) {
            val item = manifest[itemRef] ?: continue
            val fullPath = normalizePath(opfDir + item.href)
            val chapterBytes = files[fullPath] ?: files[item.href] ?: continue
            val html = String(chapterBytes, Charsets.UTF_8)
            val cleanText = htmlToPlainText(html)

            if (cleanText.isNotBlank()) {
                val extractedTitle = extractTitleFromHtml(html) ?: "Capítulo $chapterIndex"
                chapters.add(
                    EpubChapter(
                        id = item.id,
                        title = extractedTitle,
                        href = item.href,
                        plainText = cleanText,
                        htmlContent = html
                    )
                )
                chapterIndex++
            }
        }

        if (chapters.isEmpty()) {
            throw Exception("O arquivo EPUB não possui capítulos de texto legíveis.")
        }

        return EpubBook(
            title = metadata["title"]?.ifBlank { "Livro EPUB Importado" } ?: "Livro EPUB Importado",
            author = metadata["creator"]?.ifBlank { "Autor Desconhecido" } ?: "Autor Desconhecido",
            language = metadata["language"] ?: "pt",
            description = metadata["description"] ?: "E-book em formato EPUB importado e organizado no Lumina Reader.",
            coverImageBytes = coverBytes,
            chapters = chapters
        )
    }

    private fun findOpfPath(containerXmlBytes: ByteArray?): String? {
        if (containerXmlBytes == null) return null
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(containerXmlBytes), "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name.equals("rootfile", ignoreCase = true)) {
                    val fullPath = parser.getAttributeValue(null, "full-path")
                    if (!fullPath.isNullOrBlank()) {
                        return fullPath
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // fallback
        }
        return null
    }

    private data class ManifestItem(val id: String, val href: String, val mediaType: String)

    private fun parseOpf(opfBytes: ByteArray): Triple<Map<String, String>, Map<String, ManifestItem>, List<String>> {
        val metadata = mutableMapOf<String, String>()
        val manifest = mutableMapOf<String, ManifestItem>()
        val spine = mutableListOf<String>()

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(opfBytes), "UTF-8")

            var eventType = parser.eventType
            var currentTag = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name.lowercase()
                        when {
                            currentTag == "item" -> {
                                val id = parser.getAttributeValue(null, "id") ?: ""
                                val href = parser.getAttributeValue(null, "href") ?: ""
                                val mediaType = parser.getAttributeValue(null, "media-type") ?: ""
                                if (id.isNotBlank() && href.isNotBlank()) {
                                    manifest[id] = ManifestItem(id, href, mediaType)
                                }
                            }
                            currentTag == "itemref" -> {
                                val idref = parser.getAttributeValue(null, "idref")
                                if (!idref.isNullOrBlank()) {
                                    spine.add(idref)
                                }
                            }
                            currentTag == "meta" -> {
                                val name = parser.getAttributeValue(null, "name")
                                val content = parser.getAttributeValue(null, "content")
                                if (name != null && content != null) {
                                    metadata[name] = content
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotBlank()) {
                            when (currentTag) {
                                "dc:title", "title" -> if (!metadata.containsKey("title")) metadata["title"] = text
                                "dc:creator", "creator" -> if (!metadata.containsKey("creator")) metadata["creator"] = text
                                "dc:language", "language" -> metadata["language"] = text
                                "dc:description", "description" -> metadata["description"] = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // parsing error fallback
        }

        return Triple(metadata, manifest, spine)
    }

    private fun normalizePath(path: String): String {
        val parts = path.split("/")
        val stack = mutableListOf<String>()
        for (part in parts) {
            if (part == "." || part.isEmpty()) continue
            if (part == "..") {
                if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
            } else {
                stack.add(part)
            }
        }
        return stack.joinToString("/")
    }

    private fun extractTitleFromHtml(html: String): String? {
        val h1Regex = Regex("<h[1-2][^>]*>(.*?)</h[1-2]>", RegexOption.IGNORE_CASE)
        val match = h1Regex.find(html)
        if (match != null) {
            val title = htmlToPlainText(match.groupValues[1]).trim()
            if (title.isNotBlank() && title.length < 60) return title
        }
        val titleTagRegex = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE)
        val titleMatch = titleTagRegex.find(html)
        if (titleMatch != null) {
            val title = htmlToPlainText(titleMatch.groupValues[1]).trim()
            if (title.isNotBlank() && title.length < 60) return title
        }
        return null
    }

    fun htmlToPlainText(html: String): String {
        return html
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<head[\\s\\S]*?</head>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n\n")
            .replace(Regex("(?i)</div>"), "\n")
            .replace(Regex("(?i)</h[1-6]>"), "\n\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
