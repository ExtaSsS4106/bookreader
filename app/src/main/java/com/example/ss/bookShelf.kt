package com.example.ss



import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.PDFView
import java.io.File

class bookShelf : AppCompatActivity() {

    private lateinit var pdfView: PDFView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_shelf)

        // Убедитесь, что в layout есть элемент с id pdfView
        pdfView = findViewById(R.id.PDFView)

        val button = findViewById<Button>(R.id.btnBack)

        // Получаем URI из Intent
        val pdfUriString = intent.getStringExtra("pdf_uri")

        val savedPage = MainActivity.Companion.getPageForUri(currentPdfUri ?: "")
        if (!pdfUriString.isNullOrEmpty()) {
            val uri = Uri.parse(pdfUriString)
            println("DEBUG: Получен URI: $uri")
            openPdf(uri)
        } else {
            println("DEBUG: URI не получен или пустой")
            Toast.makeText(this, "Не удалось загрузить PDF. Файл не выбран.", Toast.LENGTH_LONG).show()
            finish()
        }

        button.setOnClickListener {
            // Возвращаемся на главную активность
            val intent = Intent(this, MainActivity::class.java)
            // Очищаем историю активности, чтобы нельзя было вернуться назад к просмотру PDF
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    // открытие пдф
    private fun openPdf(uri: Uri) {
        try {
            println("DEBUG: Начало открытия PDF")
            println("DEBUG: URI: $uri")

            // Способ 1: Попробуем открыть напрямую через URI
            try {
                // Даем разрешение на постоянный доступ к URI
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)

                pdfView.fromUri(uri)
                    .enableSwipe(true)
                    .enableDoubletap(true)
                    .defaultPage(0)
                    .enableAnnotationRendering(true)
                    .onLoad { totalPages ->
                        println("DEBUG: PDF загружен успешно, страниц: $totalPages")
                        Toast.makeText(this, "PDF загружен ($totalPages стр.)", Toast.LENGTH_SHORT).show()
                    }
                    .onError { error ->
                        println("DEBUG: Ошибка загрузки PDF через URI: $error")
                        // Если не получилось через URI, пробуем через временный файл
                        openPdfWithTempFile(uri)
                    }
                    .load()
            } catch (e: Exception) {
                println("DEBUG: Исключение при загрузке через URI: ${e.message}")
                openPdfWithTempFile(uri)
            }

        } catch (e: Exception) {
            println("DEBUG: Исключение при открытии PDF: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Ошибка при открытии файла", Toast.LENGTH_LONG).show()
        }
    }

    private fun openPdfWithTempFile(uri: Uri) {
        try {
            println("DEBUG: Пробуем открыть через временный файл")
            val tempFile = createTempFileFromUri(uri)

            if (tempFile != null && tempFile.exists()) {
                println("DEBUG: Временный файл создан: ${tempFile.path}")

                pdfView.fromFile(tempFile)
                    .enableSwipe(true)
                    .enableDoubletap(true)
                    .defaultPage(0)
                    .enableAnnotationRendering(true)
                    .onLoad { totalPages ->
                        println("DEBUG: PDF загружен через временный файл, страниц: $totalPages")
                        Toast.makeText(this, "PDF загружен ($totalPages стр.)", Toast.LENGTH_SHORT).show()
                    }
                    .onError { error ->
                        println("DEBUG: Ошибка загрузки PDF: $error")
                        Toast.makeText(this, "Ошибка загрузки файла", Toast.LENGTH_LONG).show()
                        tempFile.delete()
                    }
                    .onPageChange { page, pageCount ->
                        println("DEBUG: Текущая страница: $page из $pageCount")
                    }
                    .load()
            } else {
                println("DEBUG: Не удалось создать временный файл")
                Toast.makeText(this, "Не удалось прочитать файл", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            println("DEBUG: Исключение при создании временного файла: ${e.message}")
            Toast.makeText(this, "Не удалось открыть файл", Toast.LENGTH_LONG).show()
        }
    }

    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            val tempFile = File.createTempFile("temp_pdf_", ".pdf", cacheDir)

            contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                println("DEBUG: Файл скопирован, размер: ${tempFile.length()} байт")
            }

            tempFile
        } catch (e: Exception) {
            println("DEBUG: Ошибка создания временного файла: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}