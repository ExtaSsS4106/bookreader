package com.example.ss

// Модель данных для PDF файла
data class PdfItem(
    val id: String,
    val displayName: String,
    val uriString: String,
    val type: String,
    val thumbnailPath: String? = null,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
)