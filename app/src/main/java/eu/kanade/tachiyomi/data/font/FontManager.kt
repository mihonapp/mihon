package eu.kanade.tachiyomi.data.font

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.Request
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Manages custom fonts for the novel reader.
 * Supports downloading from Google Fonts API and importing local fonts.
 */
class FontManager(
    private val context: Context,
    private val storageManager: StorageManager = Injekt.get(),
    private val networkHelper: NetworkHelper = Injekt.get(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    // Cache for loaded typefaces
    private val typefaceCache = mutableMapOf<String, Typeface>()
    
    /**
     * Get the fonts directory, creating it if necessary.
     */
    fun getFontsDirectory(): UniFile? {
        return storageManager.getFontsDirectory()
    }
    
    /**
     * Get list of installed custom fonts.
     */
    suspend fun getInstalledFonts(): List<FontInfo> = withContext(Dispatchers.IO) {
        val fontsDir = getFontsDirectory() ?: return@withContext emptyList()
        
        fontsDir.listFiles()
            ?.filter { it.isFile && it.name?.let { name -> 
                name.endsWith(".ttf", ignoreCase = true) || 
                name.endsWith(".otf", ignoreCase = true) 
            } == true }
            ?.mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                val displayName = name.substringBeforeLast(".").replace("_", " ").replace("-", " ")
                FontInfo(
                    name = displayName,
                    fileName = name,
                    path = file.uri.toString(),
                    isCustom = true,
                )
            }
            ?: emptyList()
    }
    
    /**
     * Get all available fonts (system + custom).
     */
    suspend fun getAllFonts(): List<FontInfo> {
        val systemFonts = getSystemFonts()
        val customFonts = getInstalledFonts()
        return systemFonts + customFonts
    }
    
    /**
     * Get system fonts.
     */
    fun getSystemFonts(): List<FontInfo> {
        return listOf(
            FontInfo("Sans Serif", "sans-serif", "sans-serif", false),
            FontInfo("Serif", "serif", "serif", false),
            FontInfo("Monospace", "monospace", "monospace", false),
            FontInfo("Georgia", "Georgia, serif", "Georgia, serif", false),
            FontInfo("Times New Roman", "Times New Roman, serif", "Times New Roman, serif", false),
            FontInfo("Arial", "Arial, sans-serif", "Arial, sans-serif", false),
        )
    }
    
    /**
     * Import a font file from a URI.
     */
    suspend fun importFont(uri: Uri): Result<FontInfo> = withContext(Dispatchers.IO) {
        try {
            val fontsDir = getFontsDirectory() 
                ?: return@withContext Result.failure(Exception("Cannot access fonts directory"))
            
            val sourceFile = UniFile.fromUri(context, uri)
                ?: return@withContext Result.failure(Exception("Cannot access source file"))
            
            val fileName = sourceFile.name 
                ?: return@withContext Result.failure(Exception("Cannot determine file name"))
            
            // Validate file extension
            if (!fileName.endsWith(".ttf", ignoreCase = true) && 
                !fileName.endsWith(".otf", ignoreCase = true)) {
                return@withContext Result.failure(Exception("Invalid font format. Only TTF and OTF are supported."))
            }
            
            // Check if font already exists
            val targetFile = fontsDir.createFile(fileName)
                ?: return@withContext Result.failure(Exception("Cannot create font file"))
            
            // Copy file
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.openOutputStream()?.use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Cannot read source file"))
            
            // Validate font file
            try {
                val testTypeface = Typeface.createFromFile(
                    File(targetFile.uri.path ?: targetFile.filePath ?: "")
                )
                if (testTypeface == null) throw Exception("Invalid font file")
            } catch (e: Exception) {
                targetFile.delete()
                return@withContext Result.failure(Exception("Invalid or corrupted font file"))
            }
            
            val displayName = fileName.substringBeforeLast(".").replace("_", " ").replace("-", " ")
            Result.success(FontInfo(
                name = displayName,
                fileName = fileName,
                path = targetFile.uri.toString(),
                isCustom = true,
            ))
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to import font" }
            Result.failure(e)
        }
    }
    
    /**
     * Download a font from Google Fonts.
     */
    fun downloadGoogleFont(fontFamily: String): Flow<FontDownloadState> = flow {
        emit(FontDownloadState.Downloading(0))
        
        try {
            // Google Fonts API for getting font file URL
            val apiUrl = "https://fonts.google.com/download?family=${fontFamily.replace(" ", "+")}"
            
            // Alternative: Use Google Fonts CSS API to get the font URL
            val cssUrl = "https://fonts.googleapis.com/css2?family=${fontFamily.replace(" ", "+")}:wght@400;700&display=swap"
            
            val request = Request.Builder()
                .url(cssUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            
            val response = networkHelper.client.newCall(request).execute()
            val css = response.body?.string() ?: throw Exception("Empty response")
            
            // Parse font URLs from CSS
            val urlRegex = """url\((https://fonts\.gstatic\.com/[^)]+\.(?:ttf|woff2?))\)""".toRegex()
            val fontUrls = urlRegex.findAll(css).map { it.groupValues[1] }.toList()
            
            if (fontUrls.isEmpty()) {
                throw Exception("No font files found for $fontFamily")
            }
            
            // Download the first TTF or WOFF2 file
            val fontUrl = fontUrls.firstOrNull { it.endsWith(".ttf") } 
                ?: fontUrls.first()
            
            emit(FontDownloadState.Downloading(25))
            
            val fontRequest = Request.Builder()
                .url(fontUrl)
                .build()
            
            val fontResponse = networkHelper.client.newCall(fontRequest).execute()
            val fontBytes = fontResponse.body?.bytes() 
                ?: throw Exception("Failed to download font")
            
            emit(FontDownloadState.Downloading(75))
            
            // Save to fonts directory
            val fontsDir = getFontsDirectory() 
                ?: throw Exception("Cannot access fonts directory")
            
            val extension = if (fontUrl.endsWith(".woff2")) "woff2" 
                else if (fontUrl.endsWith(".woff")) "woff" 
                else "ttf"
            val fileName = "${fontFamily.replace(" ", "_")}.$extension"
            
            val targetFile = fontsDir.createFile(fileName)
                ?: throw Exception("Cannot create font file")
            
            targetFile.openOutputStream()?.use { output ->
                output.write(fontBytes)
            } ?: throw Exception("Cannot write font file")
            
            emit(FontDownloadState.Downloading(100))
            
            val fontInfo = FontInfo(
                name = fontFamily,
                fileName = fileName,
                path = targetFile.uri.toString(),
                isCustom = true,
            )
            
            emit(FontDownloadState.Success(fontInfo))
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to download font: $fontFamily" }
            emit(FontDownloadState.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Search Google Fonts.
     */
    suspend fun searchGoogleFonts(query: String): List<GoogleFontInfo> = withContext(Dispatchers.IO) {
        try {
            // Using a simplified list of popular Google Fonts
            // In a full implementation, you'd use the Google Fonts API with an API key
            val popularFonts = listOf(
                GoogleFontInfo("Roboto", "sans-serif", listOf("100", "300", "400", "500", "700", "900")),
                GoogleFontInfo("Open Sans", "sans-serif", listOf("300", "400", "600", "700", "800")),
                GoogleFontInfo("Lato", "sans-serif", listOf("100", "300", "400", "700", "900")),
                GoogleFontInfo("Montserrat", "sans-serif", listOf("100", "200", "300", "400", "500", "600", "700", "800", "900")),
                GoogleFontInfo("Poppins", "sans-serif", listOf("100", "200", "300", "400", "500", "600", "700", "800", "900")),
                GoogleFontInfo("Source Sans Pro", "sans-serif", listOf("200", "300", "400", "600", "700", "900")),
                GoogleFontInfo("Noto Sans", "sans-serif", listOf("100", "200", "300", "400", "500", "600", "700", "800", "900")),
                GoogleFontInfo("Nunito", "sans-serif", listOf("200", "300", "400", "500", "600", "700", "800", "900")),
                GoogleFontInfo("Merriweather", "serif", listOf("300", "400", "700", "900")),
                GoogleFontInfo("Playfair Display", "serif", listOf("400", "500", "600", "700", "800", "900")),
                GoogleFontInfo("Lora", "serif", listOf("400", "500", "600", "700")),
                GoogleFontInfo("PT Serif", "serif", listOf("400", "700")),
                GoogleFontInfo("Source Serif Pro", "serif", listOf("200", "300", "400", "600", "700", "900")),
                GoogleFontInfo("Libre Baskerville", "serif", listOf("400", "700")),
                GoogleFontInfo("Crimson Text", "serif", listOf("400", "600", "700")),
                GoogleFontInfo("EB Garamond", "serif", listOf("400", "500", "600", "700", "800")),
                GoogleFontInfo("Fira Code", "monospace", listOf("300", "400", "500", "600", "700")),
                GoogleFontInfo("JetBrains Mono", "monospace", listOf("100", "200", "300", "400", "500", "600", "700", "800")),
                GoogleFontInfo("Source Code Pro", "monospace", listOf("200", "300", "400", "500", "600", "700", "900")),
                GoogleFontInfo("Inconsolata", "monospace", listOf("200", "300", "400", "500", "600", "700", "800", "900")),
                GoogleFontInfo("IBM Plex Mono", "monospace", listOf("100", "200", "300", "400", "500", "600", "700")),
                GoogleFontInfo("Dancing Script", "cursive", listOf("400", "500", "600", "700")),
                GoogleFontInfo("Pacifico", "cursive", listOf("400")),
                GoogleFontInfo("Caveat", "cursive", listOf("400", "500", "600", "700")),
                GoogleFontInfo("Indie Flower", "cursive", listOf("400")),
                GoogleFontInfo("Noto Serif JP", "serif", listOf("200", "300", "400", "500", "600", "700", "900")),
                GoogleFontInfo("Noto Sans JP", "sans-serif", listOf("100", "200", "300", "400", "500", "600", "700", "800", "900")),
                GoogleFontInfo("Noto Sans KR", "sans-serif", listOf("100", "200", "300", "400", "500", "600", "700", "800", "900")),
                GoogleFontInfo("Noto Sans SC", "sans-serif", listOf("100", "200", "300", "400", "500", "700", "900")),
            )
            
            if (query.isBlank()) {
                popularFonts
            } else {
                popularFonts.filter { it.family.contains(query, ignoreCase = true) }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to search Google Fonts" }
            emptyList()
        }
    }
    
    /**
     * Delete a custom font.
     */
    suspend fun deleteFont(fontInfo: FontInfo): Boolean = withContext(Dispatchers.IO) {
        if (!fontInfo.isCustom) return@withContext false
        
        try {
            val fontsDir = getFontsDirectory() ?: return@withContext false
            val file = fontsDir.findFile(fontInfo.fileName)
            file?.delete() ?: false
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to delete font: ${fontInfo.fileName}" }
            false
        }
    }
    
    /**
     * Get a Typeface for a font.
     */
    fun getTypeface(fontInfo: FontInfo): Typeface? {
        if (!fontInfo.isCustom) {
            return when (fontInfo.path) {
                "serif" -> Typeface.SERIF
                "monospace" -> Typeface.MONOSPACE
                else -> Typeface.SANS_SERIF
            }
        }
        
        return typefaceCache.getOrPut(fontInfo.path) {
            try {
                val uri = Uri.parse(fontInfo.path)
                val file = UniFile.fromUri(context, uri)
                val filePath = file?.filePath ?: file?.uri?.path
                if (filePath != null) {
                    Typeface.createFromFile(filePath)
                } else {
                    // Fallback: try to create from input stream
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val tempFile = File.createTempFile("font_", ".ttf", context.cacheDir)
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                        Typeface.createFromFile(tempFile).also {
                            tempFile.delete()
                        }
                    } ?: Typeface.DEFAULT
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load typeface: ${fontInfo.path}" }
                Typeface.DEFAULT
            }
        }
    }
    
    /**
     * Get the CSS font-face declaration for a custom font.
     */
    fun getFontFaceCss(fontInfo: FontInfo): String {
        if (!fontInfo.isCustom) return ""
        
        return """
            @font-face {
                font-family: '${fontInfo.name}';
                src: url('${fontInfo.path}');
            }
        """.trimIndent()
    }
    
    companion object {
        private const val FONTS_PATH = "fonts"
    }
}

/**
 * Information about a font.
 */
@Serializable
data class FontInfo(
    val name: String,
    val fileName: String,
    val path: String,
    val isCustom: Boolean,
)

/**
 * Information about a Google Font.
 */
@Serializable
data class GoogleFontInfo(
    val family: String,
    val category: String,
    val variants: List<String>,
)

/**
 * State of a font download.
 */
sealed class FontDownloadState {
    data class Downloading(val progress: Int) : FontDownloadState()
    data class Success(val fontInfo: FontInfo) : FontDownloadState()
    data class Error(val message: String) : FontDownloadState()
}
