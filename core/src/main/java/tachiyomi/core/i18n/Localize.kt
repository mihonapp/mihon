package tachiyomi.core.i18n

import android.content.Context
import dev.icerock.moko.resources.PluralsResource
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.desc.Plural
import dev.icerock.moko.resources.desc.PluralFormatted
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.ResourceFormatted
import dev.icerock.moko.resources.desc.StringDesc

fun Context.localize(resource: StringResource): String {
    return StringDesc.Resource(resource).toString(this)
}

fun Context.localize(resource: StringResource, vararg args: Any): String {
    return StringDesc.ResourceFormatted(resource, *args).toString(this)
}

fun Context.localizePlural(resource: PluralsResource, count: Int): String {
    return StringDesc.Plural(resource, count).toString(this)
}

fun Context.localizePlural(resource: PluralsResource, count: Int, vararg args: Any): String {
    return StringDesc.PluralFormatted(resource, count, *args).toString(this)
}
