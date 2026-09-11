package mihon.desktop.ui.reader

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import mihon.reader.model.ScaleMode

val ReaderScaleModeKey = SemanticsPropertyKey<ScaleMode>("ReaderScaleMode")
val ReaderZoomKey = SemanticsPropertyKey<Float>("ReaderZoom")
val ReaderPanXKey = SemanticsPropertyKey<Float>("ReaderPanX")
val ReaderPanYKey = SemanticsPropertyKey<Float>("ReaderPanY")
val ReaderPageIndexKey = SemanticsPropertyKey<Int>("ReaderPageIndex")
val ReaderChromeVisibleKey = SemanticsPropertyKey<Boolean>("ReaderChromeVisible")
val ReaderBookmarkedKey = SemanticsPropertyKey<Boolean>("ReaderBookmarked")
val ReaderFrameIndexKey = SemanticsPropertyKey<Int>("ReaderFrameIndex")

var SemanticsPropertyReceiver.readerScaleMode by ReaderScaleModeKey
var SemanticsPropertyReceiver.readerZoom by ReaderZoomKey
var SemanticsPropertyReceiver.readerPanX by ReaderPanXKey
var SemanticsPropertyReceiver.readerPanY by ReaderPanYKey
var SemanticsPropertyReceiver.readerPageIndex by ReaderPageIndexKey
var SemanticsPropertyReceiver.readerChromeVisible by ReaderChromeVisibleKey
var SemanticsPropertyReceiver.readerBookmarked by ReaderBookmarkedKey
var SemanticsPropertyReceiver.readerFrameIndex by ReaderFrameIndexKey

fun pageTag(index: Int): String = "reader-page-$index"
