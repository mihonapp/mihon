package mihon.presentation.widget.di

import tachiyomi.presentation.widget.BaseUpdatesGridGlanceWidget

interface PresentationWidgetGraph {
    fun inject(widget: BaseUpdatesGridGlanceWidget)
}
