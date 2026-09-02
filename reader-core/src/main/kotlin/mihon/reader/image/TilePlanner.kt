package mihon.reader.image

import mihon.reader.model.FrameId
import mihon.reader.model.PageId

object TilePlanner {
    const val TILE_SIZE: Int = 1024

    fun plan(
        pageId: PageId,
        frameId: FrameId?,
        imageWidth: Int,
        imageHeight: Int,
        visible: IntRect,
        sampleSize: Int = 1,
    ): List<TileRequest> {
        require(imageWidth > 0) { "imageWidth must be positive" }
        require(imageHeight > 0) { "imageHeight must be positive" }
        require(sampleSize > 0) { "sampleSize must be positive" }

        val clippedLeft = visible.left.coerceAtMost(imageWidth)
        val clippedTop = visible.top.coerceAtMost(imageHeight)
        val clippedRight = visible.right.coerceAtMost(imageWidth)
        val clippedBottom = visible.bottom.coerceAtMost(imageHeight)
        if (clippedLeft >= clippedRight || clippedTop >= clippedBottom) return emptyList()

        val expandedLeft = (clippedLeft - TILE_SIZE).coerceAtLeast(0) / TILE_SIZE * TILE_SIZE
        val expandedTop = (clippedTop - TILE_SIZE).coerceAtLeast(0) / TILE_SIZE * TILE_SIZE
        val expandedRight = clippedRight + TILE_SIZE
        val expandedBottom = clippedBottom + TILE_SIZE

        val requests = mutableListOf<TileRequest>()
        var top = expandedTop
        while (top < expandedBottom && top < imageHeight) {
            var left = expandedLeft
            while (left < expandedRight && left < imageWidth) {
                val bounds = IntRect(
                    left = left,
                    top = top,
                    right = (left + TILE_SIZE).coerceAtMost(imageWidth),
                    bottom = (top + TILE_SIZE).coerceAtMost(imageHeight),
                )
                requests += TileRequest(
                    key = TileKey(pageId, frameId, bounds, sampleSize),
                    targetWidth = (bounds.width + sampleSize - 1) / sampleSize,
                    targetHeight = (bounds.height + sampleSize - 1) / sampleSize,
                )
                left += TILE_SIZE
            }
            top += TILE_SIZE
        }
        return requests.distinctBy { it.key }
    }
}
