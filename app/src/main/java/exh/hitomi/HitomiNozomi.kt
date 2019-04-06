package exh.hitomi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.asObservableSuccess
import exh.metadata.metadata.HitomiSearchMetadata.Companion.LTN_BASE_URL
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.vepta.vdm.ByteCursor
import rx.Observable
import rx.Single
import java.security.MessageDigest

private typealias HashedTerm = ByteArray

private data class DataPair(val offset: Long, val length: Int)
private data class Node(val keys: List<ByteArray>,
                        val datas: List<DataPair>,
                        val subnodeAddresses: List<Long>)

/**
 * Kotlin port of the hitomi.la search algorithm
 * @author NerdNumber9
 */
class HitomiNozomi(private val client: OkHttpClient,
                   private val tagIndexVersion: Long,
                   private val galleriesIndexVersion: Long) {
    fun getGalleryIdsForQuery(query: String): Single<List<Int>> {
        val replacedQuery = query.replace('_', ' ')

        if(':' in replacedQuery) {
            val sides = replacedQuery.split(':')
            val namespace = sides[0]
            var tag = sides[1]

            var area: String? = namespace
            var language = "all"
            if(namespace == "female" || namespace == "male") {
                area = "tag"
                tag = replacedQuery
            } else if(namespace == "language") {
                area = null
                language = tag
                tag = "index"
            }

            return getGalleryIdsFromNozomi(area, tag, language)
        }

        val key = hashTerm(query)
        val field = "galleries"

        return getNodeAtAddress(field, 0).flatMap { node ->
            if(node == null) {
                Single.just(null)
            } else {
                BSearch(field, key, node).flatMap { data ->
                    if (data == null) {
                        Single.just(null)
                    } else {
                        getGalleryIdsFromData(data)
                    }
                }
            }
        }
    }

    private fun getGalleryIdsFromData(data: DataPair?): Single<List<Int>> {
        if(data == null)
            return Single.just(emptyList())

        val url = "$LTN_BASE_URL/$GALLERIES_INDEX_DIR/galleries.$galleriesIndexVersion.data"
        val (offset, length) = data
        if(length > 100000000 || length <= 0)
            return Single.just(emptyList())

        return client.newCall(rangedGet(url, offset, offset + length - 1))
                .asObservable()
                .map {
                    it.body()?.bytes() ?: ByteArray(0)
                }
                .onErrorReturn { ByteArray(0) }
                .map { inbuf ->
                    if(inbuf.isEmpty())
                        return@map emptyList<Int>()

                    val view = ByteCursor(inbuf)
                    val numberOfGalleryIds = view.nextInt()

                    val expectedLength = numberOfGalleryIds * 4 + 4

                    if(numberOfGalleryIds > 10000000
                            || numberOfGalleryIds <= 0
                            || inbuf.size != expectedLength) {
                        return@map emptyList<Int>()
                    }

                    (1 .. numberOfGalleryIds).map {
                        view.nextInt()
                    }
                }.toSingle()
    }

    private fun BSearch(field: String, key: ByteArray, node: Node?): Single<DataPair?> {
        fun compareByteArrays(dv1: ByteArray, dv2: ByteArray): Int {
            val top = Math.min(dv1.size, dv2.size)
            for(i in 0 until top) {
                val dv1i = dv1[i].toInt() and 0xFF
                val dv2i = dv2[i].toInt() and 0xFF
                if(dv1i < dv2i)
                    return -1
                else if(dv1i > dv2i)
                    return 1
            }
            return 0
        }

        fun locateKey(key: ByteArray, node: Node): Pair<Boolean, Int> {
            var cmpResult = -1
            var lastI = 0
            for(nodeKey in node.keys) {
                cmpResult = compareByteArrays(key, nodeKey)
                if(cmpResult <= 0) break
                lastI++
            }
            return (cmpResult == 0) to lastI
        }

        fun isLeaf(node: Node): Boolean {
            return !node.subnodeAddresses.any {
                it != 0L
            }
        }

        if(node == null || node.keys.isEmpty()) {
            return Single.just(null)
        }

        val (there, where) = locateKey(key, node)
        if(there) {
            return Single.just(node.datas[where])
        } else if(isLeaf(node)) {
            return Single.just(null)
        }

        return getNodeAtAddress(field, node.subnodeAddresses[where]).flatMap { newNode ->
            BSearch(field, key, newNode)
        }
    }

    private fun decodeNode(data: ByteArray): Node {
        val view = ByteCursor(data)

        val numberOfKeys = view.nextInt()

        val keys = (1 .. numberOfKeys).map {
            val keySize = view.nextInt()
            view.next(keySize)
        }

        val numberOfDatas = view.nextInt()
        val datas = (1 .. numberOfDatas).map {
            val offset = view.nextLong()
            val length = view.nextInt()
            DataPair(offset, length)
        }

        val numberOfSubnodeAddresses = B + 1
        val subnodeAddresses = (1 .. numberOfSubnodeAddresses).map {
            view.nextLong()
        }

        return Node(keys, datas, subnodeAddresses)
    }

    private fun getNodeAtAddress(field: String, address: Long): Single<Node?> {
        var url = "$LTN_BASE_URL/$INDEX_DIR/$field.$tagIndexVersion.index"
        if(field == "galleries") {
            url = "$LTN_BASE_URL/$GALLERIES_INDEX_DIR/galleries.$galleriesIndexVersion.index"
        }

        return client.newCall(rangedGet(url, address, address + MAX_NODE_SIZE - 1))
                .asObservableSuccess()
                .map {
                    it.body()?.bytes() ?: ByteArray(0)
                }
                .onErrorReturn { ByteArray(0) }
                .map { nodedata ->
                    if(nodedata.isNotEmpty()) {
                        decodeNode(nodedata)
                    } else null
                }.toSingle()
    }

    fun getGalleryIdsFromNozomi(area: String?, tag: String, language: String): Single<List<Int>> {
        var nozomiAddress = "$LTN_BASE_URL/$COMPRESSED_NOZOMI_PREFIX/$tag-$language$NOZOMI_EXTENSION"
        if(area != null) {
            nozomiAddress = "$LTN_BASE_URL/$COMPRESSED_NOZOMI_PREFIX/$area/$tag-$language$NOZOMI_EXTENSION"
        }

        return client.newCall(Request.Builder()
                .url(nozomiAddress)
                .build())
                .asObservableSuccess()
                .map { resp ->
                    val body = resp.body()!!.bytes()
                    val cursor = ByteCursor(body)
                    (1 .. body.size / 4).map {
                        cursor.nextInt()
                    }
                }.toSingle()
    }

    private fun hashTerm(query: String): HashedTerm {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(query.toByteArray(HASH_CHARSET))
        return md.digest().copyOf(4)
    }

    companion object {
        private const val INDEX_DIR = "tagindex"
        private const val GALLERIES_INDEX_DIR = "galleriesindex"
        private const val COMPRESSED_NOZOMI_PREFIX = "n"
        private const val NOZOMI_EXTENSION = ".nozomi"
        private const val MAX_NODE_SIZE = 464
        private const val B = 16

        private val HASH_CHARSET = Charsets.UTF_8

        fun rangedGet(url: String, rangeBegin: Long, rangeEnd: Long?): Request {
            return GET(url, Headers.Builder()
                    .add("Range", "bytes=$rangeBegin-${rangeEnd ?: ""}")
                    .build())
        }


        fun getIndexVersion(httpClient: OkHttpClient, name: String): Observable<Long> {
            return httpClient.newCall(GET("$LTN_BASE_URL/$name/version?_=${System.currentTimeMillis()}"))
                    .asObservableSuccess()
                    .map { it.body()!!.string().toLong() }
        }
    }
}