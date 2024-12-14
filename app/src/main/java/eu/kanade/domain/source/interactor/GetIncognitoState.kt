package eu.kanade.domain.source.interactor

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences

class GetIncognitoState(
    private val basePreferences: BasePreferences,
    private val sourcePreferences: SourcePreferences,
) {
    fun isEnabled(sourceId: Long): Boolean {
        val globalIncognito = basePreferences.incognitoMode().get()
        val sourceIncognito = sourceId.toString() in sourcePreferences.incognitoSources().get()
        return globalIncognito || sourceIncognito
    }
}
