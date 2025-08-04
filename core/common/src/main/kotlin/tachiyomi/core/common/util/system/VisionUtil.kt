package tachiyomi.core.common.util.system

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.round

object VisionUtil {

    init {
        OpenCVLoader.initLocal()
    }

    /** Converts a bitmap to an RGBA U8C4 Mat of the same size */
    private fun Bitmap.toMat(): Mat {
        val result = Mat(height, width, CvType.CV_8UC4)
        Utils.bitmapToMat(this, result, isPremultiplied)
        return result
    }

    private const val PROCESSING_RESOLUTION = 512

    /**
     * Detects whether two images form a horizontal spread using image analysis on the edges of the images.
     *
     */
    fun detectSpread(left: Bitmap, right: Bitmap): Boolean {
        // The images should have the same height
        if (left.height != right.height) {
            return false
        }

        // Already wide images cannot form a spread
        if (left.width > left.height || right.width > right.height) {
            return false
        }

        // Smaller images could form a spread in theory, but the detection algorithm has not been tested on smaller
        // images, and it may not be reliable if the image is not at least as large as the processing resolution.
        if (left.height < PROCESSING_RESOLUTION || right.height < PROCESSING_RESOLUTION) {
            return false
        }

        val img1 = left.toMat()
        val img2 = right.toMat()

        fun preprocess(img: Mat): Mat {
            return img
                .toResized(PROCESSING_RESOLUTION)
                .toGrayscale()
                .applyOperation { Imgproc.GaussianBlur(this, it, Size(5.0, 5.0), .0) }
                .apply { uniformQuantize() }
        }

        val processedImg1 = preprocess(img1)
        val processedImg2 = preprocess(img2)

        return isSpread(processedImg1, processedImg2)
    }


    private const val INTERESTINGNESS_REGION_COUNT = 8
    private const val INTERESTINGNESS_THRESHOLD = 0.5
    private const val INTERESTINGNESS_MAX_DIFFER = 0.2

    private const val CANNY_THRESHOLD_MIN = 15.0
    private const val CANNY_THRESHOLD_MAX = 50.0

    private const val SEAM_DISCONTINUITY_THRESHOLD = 0.13

    private fun isSpread(img1: Mat, img2: Mat): Boolean {
        // There are two main heuristics we'll use to determine if two adjacent pages comprise a spread:
        // 1. "Interestingness"
        //      A group of pixels is considered "interesting" when it has non-zero variance.
        //      Interestingness is tested in sets of pixels within a column close to the center edge.
        //      Note that quantization during pre-processing helped ensure that compression artifacts in
        //      the image wouldn't cause entirely non-interesting parts of the image to have non-zero variance.
        // 2. "Seam discontinuity"
        //      The purpose of this heuristic is to differentiate between a continuous image across the seam versus
        //      unrelated lines or textures that simply happen to run to the center of the page.
        //      To detect that, the two sides are fused, and then an edge map is created using Canny edge detection.
        //      When there's a discontinuity between the left and right sides of the seam, we expect the center of the
        //      edge map to contain continuous vertical lines. If, on the other hand, the center of the edge map is more
        //      noisy and discontinuous, those edges are likely part of texture in the image unrelated to the seam.
        //      A kernel is convolved on the center columns of the edge map to intensify vertical structures, then the
        //      result is subtracted from the original edge map. For highly vertical lines, this will be very similar to
        //      the original so the resulting image will be dark, whereas for more noisy edge maps, more pixels will
        //      be unaffected and so it will be brighter. The ratio of lit pixels between the original edge map and
        //      the difference is the value of this heuristic (a measure of how "discontinuous" the vertical seam is).

        // An image is predicted to be a spread if:
        //      1. Both sides have a number of interesting sub-regions above a certain threshold
        //      2. The regions of each side that are interesting line up, within a tolerance
        //      3. The seam discontinuity is above a certain threshold (i.e. there is not a clear seam)

        val minInterestingRegionCount = INTERESTINGNESS_REGION_COUNT * INTERESTINGNESS_THRESHOLD
        val leftInterestingMap = img1.col(img1.width() - 1).getInterestingnessMap()
        if (leftInterestingMap.count { it } < minInterestingRegionCount) {
            return false
        }

        val rightInterestingMap = img2.col(0).getInterestingnessMap()
        if (rightInterestingMap.count { it } < minInterestingRegionCount) {
            return false
        }

        val maxDifferingInterestingRegions = INTERESTINGNESS_REGION_COUNT * INTERESTINGNESS_MAX_DIFFER
        val regionsDiff = leftInterestingMap.zip(rightInterestingMap).count { it.first xor it.second }
        if (regionsDiff > maxDifferingInterestingRegions) {
            return false
        }

        if (getSeamDiscontinuity(img1, img2) < SEAM_DISCONTINUITY_THRESHOLD) {
            return false
        }

        return true
    }

    private fun Mat.getInterestingnessMap(): BooleanArray {
        val regionSize = height() / INTERESTINGNESS_REGION_COUNT
        val data = BooleanArray(regionSize)
        for (i in 0 until INTERESTINGNESS_REGION_COUNT) {
            val stddev = MatOfDouble()
            Core.meanStdDev(
                rowRange(regionSize * i, regionSize * (i + 1)),
                MatOfDouble(),
                stddev,
            )
            data[i] = stddev[0, 0][0] != .0
        }
        return data
    }

    private val verticalSmearKernel = run {
        val kernel = Mat(3, 3, CvType.CV_64FC1)
        kernel.put(
            0, 0,
            0.0, 0.5, 0.0,
            -1.0, 1.0, -1.0,
            0.0, 0.5, 0.0,
        )
        kernel
    }

    private fun getSeamDiscontinuity(img1: Mat, img2: Mat): Double {
        val halfBandSize = 8

        val fused = Mat(img1.rows(), img1.cols() + img2.cols(), img1.type())
        Core.hconcat(
            listOf(
                img1.colRange(img1.width() - halfBandSize, img1.width()),
                img2.colRange(0, halfBandSize),
            ),
            fused,
        )

        val edges = fused.applyOperation { Imgproc.Canny(this, it, CANNY_THRESHOLD_MIN, CANNY_THRESHOLD_MAX) }
        val centerEdges = edges.colRange(halfBandSize - 1, halfBandSize + 1)

        val edgesMean = Core.mean(centerEdges).`val`[0]
        // Shouldn't happen in practice, but there is a hypothetical divide-by-zero if this case is unchecked
        if (edgesMean == 0.0) {
            return 0.0
        }

        val convolved = centerEdges.applyOperation { Imgproc.filter2D(this, it, -1, verticalSmearKernel) }

        // Modify convolved in-place
        Core.subtract(centerEdges, convolved, convolved)
        val diffMean = Core.mean(convolved).`val`[0]

        return diffMean / edgesMean
    }

    private fun Mat.toGrayscale(): Mat {
        val target = Mat.zeros(size(), CvType.CV_8UC1)
        Imgproc.cvtColor(this, target, Imgproc.COLOR_RGBA2GRAY)
        return target
    }

    private const val QUANTIZATION_LEVELS = 16
    private val quantizationMap = run {
        val map = IntRange(0, 255).map {
            ((it * QUANTIZATION_LEVELS / 255) * 255 / (QUANTIZATION_LEVELS - 1)).toByte()
        }.toByteArray()
        map[255] = 255.toByte()
        map
    }

    private fun Mat.uniformQuantize() {
        val data = ByteArray(width() * height())
        get(0, 0, data)

        for (i in data.indices) {
            data[i] = quantizationMap[data[i].toUByte().toInt()]
        }

        put(0, 0, data)
    }

    private fun Mat.toResized(newHeight: Int): Mat {
        if (newHeight == height()) {
            return this.clone()
        }
        val scaleFactor = newHeight.toDouble() / height()
        val newWidth = round(width() * scaleFactor).toInt()
        val target = Mat(newHeight, newWidth, type())
        Imgproc.resize(this, target, target.size(), .0, .0, Imgproc.INTER_AREA)
        return target
    }

    private inline fun Mat.applyOperation(op: Mat.(newMat: Mat) -> Unit): Mat {
        val newMat = Mat(size(), type())
        op(newMat)
        return newMat
    }

}
