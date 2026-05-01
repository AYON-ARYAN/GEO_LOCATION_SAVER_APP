package com.techpuram.app.gpsmapcamera
import java.io.FileOutputStream
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import java.io.File

//fun createPdfFromImage(context: Context, bitmap: Bitmap): File? {
//    return try {
//        val pdfDocument = PdfDocument()
//        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
//        val page = pdfDocument.startPage(pageInfo)
//        val canvas = page.canvas
//        canvas.drawBitmap(bitmap, 0f, 0f, null)
//        pdfDocument.finishPage(page)
//
//        // Create a temp file in cache dir
//        val file = File(context.cacheDir, "shared_image_${System.currentTimeMillis()}.pdf")
//        val outputStream = FileOutputStream(file)
//        pdfDocument.writeTo(outputStream)
//        pdfDocument.close()
//        outputStream.close()
//
//        file
//    } catch (e: Exception) {
//        e.printStackTrace()
//        null
//    }
//}
