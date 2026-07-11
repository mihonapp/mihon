package tachiyomi.domain.readinglist.normalization

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class IssueNumberNormalizerTest {

    @Test
    fun `normalizes leading zeroes labels and issue counts`() {
        val values = listOf("#001", "Issue 1", "No. #1", "Chapter 1 (of 6)")

        values.map(IssueNumberNormalizer::normalize).forEach { normalized ->
            normalized.canonical shouldBe "1"
            normalized.numericValue?.toPlainString() shouldBe "1"
            normalized.kind shouldBe IssueNumberKind.REGULAR
        }
    }

    @Test
    fun `normalizes decimal punctuation and trailing zeroes`() {
        IssueNumberNormalizer.normalize("01.500").canonical shouldBe "1.5"
        IssueNumberNormalizer.normalize("1,5").canonical shouldBe "1.5"
        IssueNumberNormalizer.normalize("1.0").canonical shouldBe "1"
    }

    @Test
    fun `normalizes alphabetic variants without losing the suffix`() {
        val compact = IssueNumberNormalizer.normalize("001A")
        val separated = IssueNumberNormalizer.normalize("1-a")

        compact.canonical shouldBe "1a"
        compact.suffix shouldBe "a"
        compact.isEquivalentTo(separated) shouldBe true
        compact.isEquivalentTo(IssueNumberNormalizer.normalize("1b")) shouldBe false
    }

    @Test
    fun `normalizes common fractional issue numbers`() {
        IssueNumberNormalizer.normalize("½").canonical shouldBe "0.5"
        IssueNumberNormalizer.normalize("1/2").canonical shouldBe "0.5"
        IssueNumberNormalizer.normalize("1 1/2").canonical shouldBe "1.5"
        IssueNumberNormalizer.normalize("1½").canonical shouldBe "1.5"
    }

    @Test
    fun `retains annual and special semantics`() {
        val annual = IssueNumberNormalizer.normalize("Annual #01")
        val annualSuffix = IssueNumberNormalizer.normalize("1 Annual")
        val special = IssueNumberNormalizer.normalize("Special 1")

        annual.canonical shouldBe "annual 1"
        annual.kind shouldBe IssueNumberKind.ANNUAL
        annual.isEquivalentTo(annualSuffix) shouldBe true
        annual.isEquivalentTo(IssueNumberNormalizer.normalize("1")) shouldBe false
        special.canonical shouldBe "special 1"
        special.kind shouldBe IssueNumberKind.SPECIAL
    }

    @Test
    fun `normalizes free comic book day and one shot labels`() {
        IssueNumberNormalizer.normalize("FCBD 2024").canonical shouldBe "fcbd 2024"
        IssueNumberNormalizer.normalize("Free Comic Book Day #2024").canonical shouldBe "fcbd 2024"
        IssueNumberNormalizer.normalize("One-Shot").canonical shouldBe "one shot"
        IssueNumberNormalizer.normalize("1 one shot").canonical shouldBe "one shot 1"
    }

    @Test
    fun `preserves opaque issue identifiers deterministically`() {
        val first = IssueNumberNormalizer.normalize("  Alpha – Beta  ")
        val second = IssueNumberNormalizer.normalize("alpha-beta")

        first.canonical shouldBe "alpha-beta"
        first.kind shouldBe IssueNumberKind.OPAQUE
        first.isEquivalentTo(second) shouldBe true
    }

    @Test
    fun `blank issue numbers are not usable or equivalent`() {
        val first = IssueNumberNormalizer.normalize(" ")
        val second = IssueNumberNormalizer.normalize("")

        first.isUsable shouldBe false
        first.isEquivalentTo(second) shouldBe false
    }
}
