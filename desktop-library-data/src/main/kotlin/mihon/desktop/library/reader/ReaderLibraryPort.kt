package mihon.desktop.library.reader

import mihon.reader.session.ReaderProgressSink
import mihon.reader.source.ReaderChapterCatalog

interface ReaderLibraryPort : ReaderChapterCatalog, ReaderProgressSink, ReaderOnlineChapterCatalog
