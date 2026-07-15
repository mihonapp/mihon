package eu.kanade.tachiyomi.data.track.anilist

import eu.kanade.tachiyomi.data.track.anilist.dto.ALRecommendationsResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnilistRecommendationsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `recommendation request targets exact manga id without title search`() {
        val payload = buildRecommendationsPayload(mediaId = 154587)
        val query = payload.getValue("query").jsonPrimitive.content
        val variables = payload.getValue("variables").jsonObject

        assertTrue("Media(id: \$mediaId, type: MANGA)" in query)
        assertTrue("recommendations(page: 1, perPage: 4, sort: RATING_DESC)" in query)
        assertFalse(Regex("""\bPage\s*\(""").containsMatchIn(query))
        assertFalse(query.contains("search", ignoreCase = true))
        assertEquals(154587L, variables.getValue("mediaId").jsonPrimitive.content.toLong())
        assertEquals(setOf("mediaId"), variables.keys)
    }

    @Test
    fun `recommendation response preserves strict mapping metadata and server order`() {
        val result = json.decodeFromString<ALRecommendationsResult>(responseWithFiveEdges)
            .recommendations()

        assertEquals(4, result.size)
        assertEquals(listOf(1001L, 1002L, 1003L, 1004L), result.map { it.media.id })
        assertEquals(listOf(42, 31, 20, 10), result.map { it.rating })
        assertEquals(
            listOf("Frieren", "Sousou no Frieren", "葬送のフリーレン", "Frieren at the Funeral"),
            result.first().media.title.variants(result.first().media.synonyms),
        )
        assertEquals(listOf("Adventure", "Fantasy"), result.first().media.genres)
        assertEquals("Magic", result.first().media.tags.single().name)
        assertEquals(92, result.first().media.tags.single().rank)
    }

    @Test
    fun `missing media and non manga recommendations are hidden`() {
        val missingMedia = json.decodeFromString<ALRecommendationsResult>(
            """{"data":{"Media":null}}""",
        )
        val mixedMedia = json.decodeFromString<ALRecommendationsResult>(
            """
            {
              "data": {
                "Media": {
                  "recommendations": {
                    "edges": [
                      {"node":{"rating":5,"mediaRecommendation":null}},
                      {"node":{"rating":4,"mediaRecommendation":{
                        "id":2,"type":"ANIME","title":{"romaji":"Anime"}
                      }}}
                    ]
                  }
                }
              }
            }
            """.trimIndent(),
        )

        assertTrue(missingMedia.recommendations().isEmpty())
        assertTrue(mixedMedia.recommendations().isEmpty())
    }

    private val responseWithFiveEdges =
        """
        {
          "data": {
            "Media": {
              "recommendations": {
                "edges": [
                  {"node":{"rating":42,"mediaRecommendation":{
                    "id":1001,"type":"MANGA",
                    "title":{"userPreferred":"Frieren","romaji":"Sousou no Frieren","native":"葬送のフリーレン"},
                    "synonyms":["Frieren at the Funeral"],
                    "genres":["Adventure","Fantasy"],
                    "tags":[{"name":"Magic","rank":92}],
                    "unused":"ignored"
                  }}},
                  {"node":{"rating":31,"mediaRecommendation":{
                    "id":1002,"type":"MANGA","title":{"romaji":"Second"}
                  }}},
                  {"node":{"rating":20,"mediaRecommendation":{
                    "id":1003,"type":"MANGA","title":{"english":"Third"}
                  }}},
                  {"node":{"rating":10,"mediaRecommendation":{
                    "id":1004,"type":"MANGA","title":{"native":"第四"}
                  }}},
                  {"node":{"rating":1,"mediaRecommendation":{
                    "id":1005,"type":"MANGA","title":{"romaji":"Fifth"}
                  }}}
                ]
              }
            }
          }
        }
        """.trimIndent()
}
