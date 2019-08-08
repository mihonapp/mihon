package exh.metadata.metadata.base

import com.google.gson.GsonBuilder
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.forEach
import exh.metadata.sql.models.SearchMetadata
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import exh.plusAssign
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class RaisedSearchMetadata {
    @Transient
    var mangaId: Long = -1

    @Transient
    var uploader: String? = null

    @Transient
    protected open var indexedExtra: String? = null

    @Transient
    val tags = mutableListOf<RaisedTag>()

    @Transient
    val titles = mutableListOf<RaisedTitle>()

    fun getTitleOfType(type: Int): String? = titles.find { it.type == type }?.title

    fun replaceTitleOfType(type: Int, newTitle: String?) {
        titles.removeAll { it.type == type }
        if(newTitle != null) titles += RaisedTitle(newTitle, type)
    }

    abstract fun copyTo(manga: SManga)

    fun tagsToGenreString()
            = tags.filter { it.type != TAG_TYPE_VIRTUAL }
            .joinToString { (if(it.namespace != null) "${it.namespace}: " else "") + it.name }

    fun tagsToDescription()
            = StringBuilder("Tags:\n").apply {
        //BiConsumer only available in Java 8, don't bother calling forEach directly on 'tags'
        val groupedTags = tags.filter { it.type != TAG_TYPE_VIRTUAL }.groupBy {
            it.namespace
        }.entries

        groupedTags.forEach { namespace, tags ->
            if (tags.isNotEmpty()) {
                val joinedTags = tags.joinToString(separator = " ", transform = { "<${it.name}>" })
                if(namespace != null) {
                    this += "▪ "
                    this += namespace
                    this += ": "
                }
                this += joinedTags
                this += "\n"
            }
        }
    }

    fun List<RaisedTag>.ofNamespace(ns: String): List<RaisedTag> {
        return filter { it.namespace == ns }
    }

    fun flatten(): FlatMetadata {
        require(mangaId != -1L)

        val extra = raiseFlattenGson.toJson(this)
        return FlatMetadata(
                SearchMetadata(
                        mangaId,
                        uploader,
                        extra,
                        indexedExtra,
                        0
                ),
                tags.map {
                    SearchTag(
                            null,
                            mangaId,
                            it.namespace,
                            it.name,
                            it.type
                    )
                },
                titles.map {
                    SearchTitle(
                            null,
                            mangaId,
                            it.title,
                            it.type
                    )
                }
        )
    }

    fun fillBaseFields(metadata: FlatMetadata) {
        mangaId = metadata.metadata.mangaId
        uploader = metadata.metadata.uploader
        indexedExtra = metadata.metadata.indexedExtra

        this.tags.clear()
        this.tags += metadata.tags.map {
            RaisedTag(it.namespace, it.name, it.type)
        }

        this.titles.clear()
        this.titles += metadata.titles.map {
            RaisedTitle(it.title, it.type)
        }
    }

    companion object {
        // Virtual tags allow searching of otherwise unindexed fields
        const val TAG_TYPE_VIRTUAL = -2

        val raiseFlattenGson = GsonBuilder().create()

        fun titleDelegate(type: Int) = object : ReadWriteProperty<RaisedSearchMetadata, String?> {
            /**
             * Returns the value of the property for the given object.
             * @param thisRef the object for which the value is requested.
             * @param property the metadata for the property.
             * @return the property value.
             */
            override fun getValue(thisRef: RaisedSearchMetadata, property: KProperty<*>)
                    = thisRef.getTitleOfType(type)

            /**
             * Sets the value of the property for the given object.
             * @param thisRef the object for which the value is requested.
             * @param property the metadata for the property.
             * @param value the value to set.
             */
            override fun setValue(thisRef: RaisedSearchMetadata, property: KProperty<*>, value: String?)
                    = thisRef.replaceTitleOfType(type, value)
        }
    }
}