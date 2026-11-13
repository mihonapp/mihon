package tachiyomi.translation.provider

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test

class ProviderPayloadTest {
    @Test
    fun `azure payload wraps each text in a Text object`() {
        buildAzurePayload(listOf("a", "b")) shouldBe """[{"Text":"a"},{"Text":"b"}]"""
    }

    @Test
    fun `registry exposes the five providers`() {
        ProviderRegistry.descriptors shouldHaveSize 5
        ProviderRegistry.descriptor(ProviderRegistry.EDGE)?.requiresKey shouldBe false
        ProviderRegistry.descriptor(ProviderRegistry.AZURE)?.needsRegion shouldBe true
        ProviderRegistry.descriptor(ProviderRegistry.LLM)?.needsModel shouldBe true
        ProviderRegistry.descriptor("unknown").shouldBeNull()
    }

    @Test
    fun `edge is the batch capable zero config path`() {
        val provider = ProviderRegistry.create(ProviderRegistry.EDGE, ProviderConfig(), OkHttpClient())

        provider?.requiresKey shouldBe false
        provider?.capabilities?.supportsBatch shouldBe true
        provider?.capabilities?.maxBatch shouldBe 25
    }

    @Test
    fun `unknown provider id creates nothing`() {
        ProviderRegistry.create("nope", ProviderConfig(), OkHttpClient()).shouldBeNull()
    }
}
