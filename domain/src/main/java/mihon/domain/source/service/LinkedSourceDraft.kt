package mihon.domain.source.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import mihon.domain.source.model.LinkedSourceManga

/** An editing session does not modify persisted links until its snapshot is explicitly applied. */
class LinkedSourceDraft {
    data class State(
        val links: List<LinkedSourceManga>? = null,
        val saving: Boolean = false,
        val applied: Boolean = false,
    )

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    @Synchronized
    fun initialize(links: List<LinkedSourceManga>) {
        if (state.value.links == null) mutableState.value = State(links.toList())
    }

    @Synchronized
    fun select(link: LinkedSourceManga) {
        val current = state.value
        if (current.links == null || current.saving || current.applied) return
        mutableState.value = current.copy(links = current.links.filterNot { it.sourceId == link.sourceId } + link)
    }

    @Synchronized
    fun remove(sourceId: Long) {
        val current = state.value
        if (current.links == null || current.saving || current.applied) return
        mutableState.value = current.copy(links = current.links.filterNot { it.sourceId == sourceId })
    }

    @Synchronized
    fun clear() {
        val current = state.value
        if (current.links == null || current.saving || current.applied) return
        mutableState.value = current.copy(links = emptyList())
    }

    @Synchronized
    fun beginApply(): List<LinkedSourceManga>? {
        val current = state.value
        if (current.links == null || current.saving || current.applied) return null
        mutableState.value = current.copy(saving = true)
        return current.links.toList()
    }

    @Synchronized
    fun finishApply(success: Boolean) {
        mutableState.value = state.value.copy(saving = false, applied = success)
    }
}
