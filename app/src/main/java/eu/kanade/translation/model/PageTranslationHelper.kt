package eu.kanade.translation.model

import kotlin.math.abs

class PageTranslationHelper {

    companion object {
        /**
         * Merges Text block which overlap
         * this doesn't mutate the original blocks
         */
        fun mergeOverlap(blocks: ArrayList<TranslationBlock>): ArrayList<TranslationBlock> {
            val result = ArrayList<TranslationBlock>()
            val toProcess = blocks.toMutableList()
            while (toProcess.isNotEmpty()) {
                val current = toProcess.removeAt(0)
                val index = result.indexOfLast { shouldMerge(current, it) }
                if (index != -1) {
                    val mergedRectangle = mergeBlocks(result[index], current)
                    result[index] = mergedRectangle
                } else {
                    result.add(current)
                }
            }
            return result
        }

        private fun mergeBlocks(r1: TranslationBlock, r2: TranslationBlock): TranslationBlock {
            var height = maxOf(r1.height + r1.y, r2.height + r2.y)
            var width = maxOf(r1.width + r1.x, r2.width + r2.x)
            val x = minOf(r1.x, r2.x)
            val y = minOf(r1.y, r2.y)
            width = width - r1.x
            height = height - r1.y

            return TranslationBlock(
                text = "${r1.text}\n${r2.text}",
                translation = "${r1.translation}\n${r2.translation}",
                width = width,
                height = height,
                x = x,
                y = y,
                angle = (r1.angle + r2.angle) / 2,
                symWidth = minOf(r1.symWidth, r2.symWidth),
                symHeight = minOf(r1.symHeight, r2.symHeight),
            )
        }

        // Checks if two block overlap each other and are in same orientation
        private fun shouldMerge(r1: TranslationBlock, r2: TranslationBlock): Boolean {
            return abs(r1.angle - r2.angle) < 10 &&
                r1.x < (r2.x + r2.width) &&
                (r1.x + r1.width) > r2.x &&
                r1.y < (r2.y + r2.height) &&
                (r1.y + r1.height) > r2.y
        }
    }
}
