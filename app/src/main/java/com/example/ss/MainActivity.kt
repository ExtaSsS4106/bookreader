package com.example.ss

import PdfThumbnailGenerator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MainActivity : AppCompatActivity() {

    // Для хранения списка PDF файлов
    private val pdfItems = mutableListOf<PdfItem>()
    private lateinit var containerBooks: LinearLayout
    private lateinit var tvEmpty: TextView

    private val gson = Gson()
    private val STORAGE_FILE = "pdf_library.json"

    private val thumbnailGenerator by lazy { PdfThumbnailGenerator(this) }

    // Для выбора файла
    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                handleSelectedUri(uri, "file")
            }
        }
    }

    // Для выбора папки
    private val pickFolderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                handleSelectedUri(uri, "folder")
            }
        }
    }

    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        containerBooks = findViewById(R.id.containerBooks)
        tvEmpty = findViewById(R.id.tvEmpty)

        // Загружаем сохраненные книги
        loadSavedBooks()
        updateUI()

        val btnAddFile = findViewById<Button>(R.id.btnAddFile)
        val btnAddFolder = findViewById<Button>(R.id.btnAddFolder)

        btnAddFile.setOnClickListener {
            // Выбор одного PDF файла
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            }
            pickFileLauncher.launch(intent)
        }

        btnAddFolder.setOnClickListener {
            // Выбор папки (Android 5.0+)
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            }
            pickFolderLauncher.launch(intent)
        }

        // Кнопка очистки библиотеки
        findViewById<Button>(R.id.btnClear)?.setOnClickListener {
            clearLibrary()
        }
    }
    // Метод для показа/скрытия индикатора загрузки
    private fun showLoading(show: Boolean) {
        // Можно добавить ProgressBar в layout
        findViewById<ProgressBar>(R.id.progressBar)?.visibility =
            if (show) View.VISIBLE else View.GONE
    }
    private fun handleSelectedUri(uri: Uri, type: String) {
        showLoading(true)

        Thread {
            try {
                // Только получение списка файлов в фоне
                val items = when (type) {
                    "file" -> {
                        val pdfItem = createPdfItemFromFileUri(uri)
                        if (pdfItem != null) listOf(pdfItem) else emptyList()
                    }
                    "folder" -> getPdfsFromFolderUri(uri)
                    else -> emptyList()
                }

                runOnUiThread {
                    // Разрешение и добавление в основном потоке
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )

                        if (items.isNotEmpty()) {
                            items.forEach { pdfItem ->
                                addPdfItem(pdfItem)
                            }
                            saveBooks()
                            updateUI()

                            val message = when (type) {
                                "file" -> "Файл добавлен"
                                else -> "Добавлено ${items.size} PDF файлов"
                            }
                            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "В папке нет PDF файлов", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        showLoading(false)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                }
            }
        }.start()
    }

    private fun createPdfItemFromFileUri(uri: Uri): PdfItem? {
        return try {
            val displayName = getDisplayNameFromUri(uri) ?: "Неизвестный файл"

            PdfItem(
                id = System.currentTimeMillis().toString(),
                displayName = displayName,
                uriString = uri.toString(),
                type = "file",
                pageCount = 0  // Пока 0, обновим позже
            )
        } catch (e: Exception) {
            null
        }
    }



    @SuppressLint("Range")
    private fun getPdfsFromFolderUri(folderUri: Uri): List<PdfItem> {
        val pdfItems = mutableListOf<PdfItem>()

        try {
            // Получаем ID документа папки
            val folderDocId = DocumentsContract.getTreeDocumentId(folderUri)
            val folderUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, folderDocId)

            // Ищем все документы в папке
            contentResolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(folderUri,
                    DocumentsContract.getDocumentId(folderUri)),
                null, null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(
                        cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    )
                    val mimeType = cursor.getString(
                        cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    )
                    val docId = cursor.getString(
                        cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    )

                    // Проверяем, что это PDF файл
                    if (mimeType == "application/pdf" ||
                        (displayName?.endsWith(".pdf", ignoreCase = true) == true)) {

                        val documentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)

                        pdfItems.add(PdfItem(
                            id = docId,
                            displayName = displayName ?: "Unknown PDF",
                            uriString = documentUri.toString(),
                            type = "file"
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error reading folder: ${e.message}")
        }

        return pdfItems
    }

    @SuppressLint("Range")
    private fun getDisplayNameFromUri(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                } else {
                    null
                }
            } ?: uri.lastPathSegment
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }

    private fun addPdfItem(pdfItem: PdfItem) {
        if (!pdfItems.any { it.uriString == pdfItem.uriString }) {
            // Добавляем элемент (пока без миниатюры и без pageCount)
            pdfItems.add(pdfItem.copy(thumbnailPath = null, pageCount = 0))

            saveBooks()
            updateUI()
        }
    }

    // Сохранить текущую страницу
    private fun saveCurrentPage(pdfUri: String, pageNumber: Int) {
        val index = pdfItems.indexOfFirst { it.uriString == pdfUri }
        if (index != -1) {
            pdfItems[index] = pdfItems[index].copy(currentPage = pageNumber)
            saveBooks()
        }
    }

    // Найти текущую страницу по URL
    private fun getCurrentPage(pdfUri: String): Int {
        return pdfItems.find { it.uriString == pdfUri }?.currentPage ?: 0
    }




    @SuppressLint("SetTextI18n")
    private fun updateUI() {
        containerBooks.removeAllViews()

        if (pdfItems.isEmpty()) {
            tvEmpty.visibility = TextView.VISIBLE
            return
        }

        tvEmpty.visibility = TextView.GONE

        // Создаем карточки для каждого PDF
        pdfItems.forEachIndexed { index, pdfItem ->
            val cardView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    500
                ).apply {
                    setMargins(16, 8, 16, 8)
                }
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.card_background)
                setPadding(16, 16, 16, 16)
                elevation = 4f
            }

            // Иконка
            val thumbnailView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    300,  // Ширина миниатюры
                    470   // Высота миниатюры
                ).apply {
                    marginEnd = 16

                }
                scaleType = ImageView.ScaleType.CENTER_CROP

                // Загружаем миниатюру или показываем заглушку
                if (pdfItem.thumbnailPath != null) {
                    val bitmap = thumbnailGenerator.loadThumbnail(pdfItem.thumbnailPath!!)
                    if (bitmap != null) {
                        setImageBitmap(bitmap)
                    } else {
                        setImageResource(R.drawable.ic_pdf_placeholder)  // Заглушка
                    }
                } else {
                    // Пока миниатюра генерируется - показываем заглушку
                    setImageResource(R.drawable.ic_pdf_placeholder)

                    // Можно добавить индикатор загрузки
                    setBackgroundColor(Color.LTGRAY)
                }
            }

            // Информация о файле
            val textContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    weight = 1f
                }
            }

            val tvName = TextView(this).apply {
                text = pdfItem.displayName
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val tvType = TextView(this).apply {
                text = when {
                    pdfItem.pageCount > 0 -> "${pdfItem.pageCount} страниц"
                    else -> "PDF файл"
                }
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
                setPadding(0, 4, 0, 0)
            }

            // Кнопка удаления
            val btnDelete = Button(this).apply {
                text = "Удалить"
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
                background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.btn_default)
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    removePdfItem(index)
                }
            }



            // Добавляем элементы
            textContainer.addView(tvName)
            textContainer.addView(tvType)



            val buttonContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    200,
                    100
                ).apply {
                    leftMargin = 430
                    topMargin = 250
                }
            }
            textContainer.addView(buttonContainer)

            buttonContainer.addView(btnDelete)

            cardView.addView(thumbnailView)
            cardView.addView(textContainer)

            // Обработчик клика на всю карточку
            cardView.setOnClickListener {
                openPdf(pdfItem)
            }

            containerBooks.addView(cardView)
        }
    }

    private fun openPdf(pdfItem: PdfItem) {
        val intent = Intent(this, bookShelf::class.java).apply {
            putExtra("pdf_uri", pdfItem.uriString)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    private fun removePdfItem(index: Int) {
        if (index in pdfItems.indices) {
            pdfItems.removeAt(index)
            saveBooks()
            updateUI()
            Toast.makeText(this, "Файл удален", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearLibrary() {
        pdfItems.clear()
        saveBooks()
        updateUI()
        Toast.makeText(this, "Библиотека очищена", Toast.LENGTH_SHORT).show()
    }

    // Сохранение и загрузка списка книг
    private fun saveBooks() {
        try {
            val json = gson.toJson(pdfItems)
            val file = File(filesDir, STORAGE_FILE)
            FileOutputStream(file).use {
                it.write(json.toByteArray())
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving books: ${e.message}")
        }
    }

    private fun loadSavedBooks() {
        try {
            val file = File(filesDir, STORAGE_FILE)
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<List<PdfItem>>() {}.type
                val savedList = gson.fromJson<List<PdfItem>>(json, type)
                pdfItems.clear()
                pdfItems.addAll(savedList ?: emptyList())
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading books: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        saveBooks()
    }
}

