package com.example.data.remote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.data.local.entity.AnnotationEntity
import com.example.data.local.entity.BookEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotionExporter {
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun formatSingleAnnotationForNotion(annotation: AnnotationEntity): String {
        val dateStr = dateFormat.format(Date(annotation.createdAt))
        return buildString {
            appendLine("### 📖 Citação de *${annotation.bookTitle}*")
            appendLine("**Autor:** ${annotation.bookAuthor} | **${annotation.chapterTitle}** (pág. ${annotation.pageNumber})")
            appendLine("**Data:** $dateStr")
            appendLine()
            appendLine("> \"${annotation.selectedText}\"")
            if (annotation.noteText.isNotBlank()) {
                appendLine()
                appendLine("> 💡 **Reflexão / Anotação Pessoal:**")
                appendLine("> ${annotation.noteText}")
            }
            appendLine()
            appendLine("🏷️ `#LuminaReader #Leitura #Citação`")
        }
    }

    fun formatBookAnnotationsForNotion(book: BookEntity, annotations: List<AnnotationEntity>): String {
        return buildString {
            appendLine("# 📚 ${book.title}")
            appendLine("- **Autor:** ${book.author}")
            appendLine("- **Gênero:** ${book.genre}")
            if (book.isbn.isNotBlank()) appendLine("- **ISBN:** ${book.isbn}")
            appendLine("- **Progresso:** ${book.currentPage} de ${book.totalPages} páginas (${if (book.totalPages > 0) (book.currentPage * 100 / book.totalPages) else 0}%)")
            appendLine("- **Avaliação:** ${"⭐".repeat(book.rating.coerceIn(1, 5))}")
            appendLine("- **Exportado de:** Lumina Reader em ${dateFormat.format(Date())}")
            appendLine()
            appendLine("---")
            appendLine("## 📝 Citações e Anotações (${annotations.size})")
            appendLine()

            if (annotations.isEmpty()) {
                appendLine("*Nenhuma anotação registrada ainda para este livro.*")
            } else {
                annotations.forEachIndexed { index, note ->
                    val date = dateFormat.format(Date(note.createdAt))
                    appendLine("### ${index + 1}. ${note.chapterTitle} — Página ${note.pageNumber}")
                    appendLine("> \"${note.selectedText}\"")
                    if (note.noteText.isNotBlank()) {
                        appendLine()
                        appendLine("> 💡 **Minha Anotação:**")
                        appendLine("> ${note.noteText}")
                    }
                    appendLine()
                    appendLine("📅 *$date*")
                    appendLine()
                }
            }
            appendLine("---")
            appendLine("🏷️ Tags: `#NotionBookTracker #LuminaReader #${book.genre}`")
        }
    }

    fun copyToClipboard(context: Context, text: String, message: String = "Formatado para o Notion copiado!") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Notion Export", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun shareExport(context: Context, text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Lumina Reader - Exportação para Notion: $title")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, "Exportar para o Notion ou Notas")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
