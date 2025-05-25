package com.example.sensorread

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.rmtheis.tess-two.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PdfOcrService(private val context: Context) {
    private var tessBaseAPI: TessBaseAPI? = null
    
    init {
        initializeTesseract()
    }
    
    private fun initializeTesseract() {
        try {
            // Copy trained data to app's storage
            val dataPath = File(context.filesDir, "tesseract")
            if (!dataPath.exists()) {
                dataPath.mkdirs()
            }
            
            // Copy trained data files
            copyTrainedData(dataPath)
            
            // Initialize Tesseract
            tessBaseAPI = TessBaseAPI()
            val success = tessBaseAPI?.init(dataPath.absolutePath, "eng")
            if (success != true) {
                Log.e("PdfOcrService", "Tesseract initialization failed")
            }
        } catch (e: Exception) {
            Log.e("PdfOcrService", "Error initializing Tesseract", e)
        }
    }
    
    private fun copyTrainedData(dataPath: File) {
        try {
            val trainedDataFile = File(dataPath, "eng.traineddata")
            if (!trainedDataFile.exists()) {
                context.assets.open("eng.traineddata").use { input ->
                    FileOutputStream(trainedDataFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("PdfOcrService", "Error copying trained data", e)
        }
    }
    
    suspend fun extractTextFromPdf(pdfUri: String): String = withContext(Dispatchers.IO) {
        var extractedText = ""
        var fileDescriptor: ParcelFileDescriptor? = null
        
        try {
            // Open PDF file
            fileDescriptor = context.contentResolver.openFileDescriptor(pdfUri.toUri(), "r")
            val renderer = PdfRenderer(fileDescriptor!!)
            
            // Process each page
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(
                    page.width * 2,
                    page.height * 2,
                    Bitmap.Config.ARGB_8888
                )
                
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                
                // Perform OCR on the bitmap
                tessBaseAPI?.setImage(bitmap)
                val pageText = tessBaseAPI?.utF8Text ?: ""
                extractedText += pageText + "\n\n"
                
                // Clean up
                bitmap.recycle()
                page.close()
            }
            
            renderer.close()
        } catch (e: Exception) {
            Log.e("PdfOcrService", "Error processing PDF", e)
            throw e
        } finally {
            fileDescriptor?.close()
        }
        
        extractedText
    }
    
    fun release() {
        tessBaseAPI?.recycle()
        tessBaseAPI = null
    }
    
    private fun String.toUri() = android.net.Uri.parse(this)
} 