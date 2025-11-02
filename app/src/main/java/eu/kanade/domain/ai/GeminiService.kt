package eu.kanade.domain.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import eu.kanade.domain.base.BasePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GeminiService(
    private val basePreferences: BasePreferences = Injekt.get(),
) {

    private val generativeModel by lazy {
        val apiKey = basePreferences.geminiAiApiKey().get()
        val modelName = basePreferences.geminiAiModel().get()

        if (apiKey.isBlank()) {
            throw IllegalStateException("Gemini API key is not configured")
        }

        GenerativeModel(
            modelName = modelName,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
            }
        )
    }

    suspend fun getRecommendations(libraryCsv: String): String {
        val prompt = """
            Analyze this manga library and provide recommendations.
            
            MY LIBRARY (CSV format):
            $libraryCsv
            
            STRICT REQUIREMENTS:
            1. Recommend exactly 15 manga/manhwa/manhua titles
            2. CRITICAL: Do NOT recommend any title that appears in my library above
            3. Analyze the genres, themes, and patterns in my library
            4. Provide diverse recommendations across different genres
            
            OUTPUT FORMAT - You MUST output ONLY a simple list, one title per line, like this:
            
            Title One
            Title Two
            Title Three
            
            RULES FOR OUTPUT:
            - NO numbers, bullets, or special characters
            - NO descriptions or explanations
            - NO markdown formatting (no **, *, or -)
            - ONLY the exact title name on each line
            - NO duplicate titles
            - NO titles from my library
            - Start output immediately with first title
            
            Example of correct output:
            Vinland Saga
            Monster
            Berserk
            Kingdom
            
            Begin recommendations now (titles only, one per line):
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(prompt)
            response.text ?: "No recommendations available at this time."
        } catch (e: Exception) {
            throw Exception("Failed to get recommendations: ${e.message}", e)
        }
    }
}