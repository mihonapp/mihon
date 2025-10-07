-keep class eu.kanade.tachiyomi.source.model.** { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.** { public protected *; }
-keep class eu.kanade.tachiyomi.source.** extends eu.kanade.tachiyomi.source.Source { public protected *; }

# Keep full chapter download constants and utilities
-keep class eu.kanade.tachiyomi.source.SourceKt { public protected *; }

-keep,allowoptimization class eu.kanade.tachiyomi.util.JsoupExtensionsKt { public protected *; }
