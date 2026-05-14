package tachiyomi.source.local.filter

import android.content.Context
import eu.kanade.tachiyomi.source.model.Filter
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

class TagFilter(name: String) : Filter.CheckBox(name)

class TagGroupFilter(context: Context, tags: List<String>) : Filter.Group<TagFilter>(
    context.stringResource(MR.strings.local_filter_tags),
    tags.map(::TagFilter),
)
