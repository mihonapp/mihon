package mihon.reader.layout

object NaturalPageComparator : Comparator<String> {
    override fun compare(left: String, right: String): Int {
        val normalizedLeft = left.replace('\\', '/')
        val normalizedRight = right.replace('\\', '/')
        var leftIndex = 0
        var rightIndex = 0

        while (leftIndex < normalizedLeft.length && rightIndex < normalizedRight.length) {
            val leftCharacter = normalizedLeft[leftIndex]
            val rightCharacter = normalizedRight[rightIndex]
            if (leftCharacter.isDigit() && rightCharacter.isDigit()) {
                val leftEnd = normalizedLeft.findNumericEnd(leftIndex)
                val rightEnd = normalizedRight.findNumericEnd(rightIndex)
                val numberComparison = compareNumericSegments(
                    normalizedLeft.substring(leftIndex, leftEnd),
                    normalizedRight.substring(rightIndex, rightEnd),
                )
                if (numberComparison != 0) return numberComparison
                leftIndex = leftEnd
                rightIndex = rightEnd
                continue
            }

            val foldedComparison = leftCharacter.lowercaseChar().compareTo(rightCharacter.lowercaseChar())
            if (foldedComparison != 0) return foldedComparison
            if (leftCharacter != rightCharacter) return leftCharacter.compareTo(rightCharacter)
            leftIndex += 1
            rightIndex += 1
        }

        val remainingComparison = normalizedLeft.length.compareTo(normalizedRight.length)
        return if (remainingComparison != 0) remainingComparison else left.compareTo(right)
    }
}

private fun String.findNumericEnd(startIndex: Int): Int {
    var index = startIndex
    while (index < length && this[index].isDigit()) index += 1
    return index
}

private fun compareNumericSegments(left: String, right: String): Int {
    val normalizedLeft = left.trimStart('0').ifEmpty { "0" }
    val normalizedRight = right.trimStart('0').ifEmpty { "0" }
    val significantComparison = normalizedLeft.length.compareTo(normalizedRight.length)
    if (significantComparison != 0) return significantComparison
    val valueComparison = normalizedLeft.compareTo(normalizedRight)
    if (valueComparison != 0) return valueComparison
    return left.length.compareTo(right.length)
}
