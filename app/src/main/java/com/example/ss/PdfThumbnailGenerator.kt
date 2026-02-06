import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.graphics.scale
import java.io.File
import java.io.FileOutputStream

class PdfThumbnailGenerator(private val context: Context) {

    companion object {
        private const val THUMBNAIL_WIDTH = 200
        private const val THUMBNAIL_HEIGHT = 300
        private const val THUMBNAIL_QUALITY = 90
    }

    /**
     * Генерирует миниатюру из первой страницы PDF
     * @param pdfUri URI PDF файла
     * @return Путь к сохраненной миниатюре или null
     */
    fun generateThumbnail(pdfUri: String): String? {
        var parcelFileDescriptor: ParcelFileDescriptor? = null

        return try {
            val uri = android.net.Uri.parse(pdfUri)
            parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")

            if (parcelFileDescriptor != null) {
                val pdfRenderer = android.graphics.pdf.PdfRenderer(parcelFileDescriptor)

                // Открываем первую страницу (индекс 0)
                val page = pdfRenderer.openPage(0)

                // Создаем Bitmap для миниатюры
                val bitmap = Bitmap.createBitmap(
                    THUMBNAIL_WIDTH,
                    THUMBNAIL_HEIGHT,
                    Bitmap.Config.ARGB_8888
                )

                // Рендерим страницу в Bitmap
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                // Закрываем ресурсы
                page.close()
                pdfRenderer.close()

                // Сохраняем миниатюру
                saveThumbnailToStorage(bitmap, getThumbnailFileName(pdfUri))
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("PdfThumbnail", "Error generating thumbnail: ${e.message}")
            null
        } finally {
            parcelFileDescriptor?.close()
        }
    }

    /**
     * Генерирует имя файла для миниатюры
     */
    private fun getThumbnailFileName(pdfUri: String): String {
        val hash = pdfUri.hashCode().toString().replace("-", "n")
        return "thumbnail_$hash.jpg"
    }

    /**
     * Сохраняет Bitmap во внутреннее хранилище
     */
    private fun saveThumbnailToStorage(bitmap: Bitmap, fileName: String): String {
        val file = File(context.filesDir, "thumbnails/$fileName")
        file.parentFile?.mkdirs()  // Создаем папку если не существует

        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, fos)
            fos.flush()
        }

        bitmap.recycle()
        return file.absolutePath
    }

    /**
     * Загружает сохраненную миниатюру
     */
    fun loadThumbnail(thumbnailPath: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(thumbnailPath)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Удаляет миниатюру
     */
    fun deleteThumbnail(thumbnailPath: String) {
        try {
            File(thumbnailPath).delete()
        } catch (e: Exception) {
            Log.e("PdfThumbnail", "Error deleting thumbnail: ${e.message}")
        }
    }
}