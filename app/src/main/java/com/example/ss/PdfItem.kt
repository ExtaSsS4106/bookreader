package com.example.ss

// Модель данных для PDF файла
data class PdfItem(
    val id: String,
    val displayName: String,
    val uriString: String,
    val type: String,
    val thumbnailPath: String? = null,  // ← Добавляем путь к миниатюре
    val pageCount: Int = 0,
    val currentPage: Int = 0,
)