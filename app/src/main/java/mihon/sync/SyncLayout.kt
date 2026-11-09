package mihon.sync

import java.security.MessageDigest

/**
 * Names and shapes of everything the sync keeps in the user's Drive.
 *
 * The library is sharded one file per entry rather than kept as a single archive. Rewriting the
 * whole library to record two pages read is wasteful, and two devices editing the same object is
 * exactly the conflict that sharding removes: phones reading different series never touch the same
 * file.
 */
object SyncLayout {

    const val ROOT_FOLDER = "Mihon Sync"
    const val LIBRARY_FOLDER = "library"

    const val README_FILE = "NE_PAS_SUPPRIMER_DO_NOT_DELETE.txt"
    const val DEVICES_FILE = "devices.json"
    const val HISTORY_FILE = "history.jsonl"
    const val CATEGORY_LIST_FILE = "categories.json"

    /**
     * Where the first version of the sync kept categories, as positions rather than stable ids. Read
     * once to seed [CATEGORY_LIST_FILE], never written again.
     */
    const val LEGACY_CATEGORIES_FILE = "categories.tachibk"
    const val SOURCES_FILE = "sources.tachibk"
    const val EXTENSIONS_FILE = "extensions.json"

    const val FOLDER_MIME = "application/vnd.google-apps.folder"

    /**
     * Stable file name for one library entry.
     *
     * Derived from source and URL because those are what identify an entry across devices — titles
     * change and ids are local. The URL is hashed so the name stays short and free of characters
     * Drive would mangle.
     */
    fun mangaFileName(sourceId: Long, url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        val hash = digest.take(HASH_BYTES).joinToString("") { "%02x".format(it) }
        return "${sourceId}_$hash.tachibk"
    }

    /**
     * Text dropped at the root of the folder, for whoever stumbles on it in their Drive months from
     * now. Multilingual because the folder is visible to the account owner whatever their language,
     * and an unexplained folder full of binary files is the kind of thing people delete.
     */
    val readmeContent: String = """
        Mihon — library synchronisation data
        ====================================

        [EN] This folder is used by the Mihon app to keep your manga library in sync between your
             devices: favourites, reading progress and categories. Deleting it will not harm your
             devices, but the synchronisation will start over from scratch and progress recorded
             only here would be lost. The files contain no personal data beyond your library.

        [FR] Ce dossier est utilisé par l'application Mihon pour synchroniser votre bibliothèque
             entre vos appareils : favoris, progression de lecture et catégories. Le supprimer
             n'abîmera pas vos appareils, mais la synchronisation repartira de zéro et la
             progression enregistrée seulement ici serait perdue. Les fichiers ne contiennent
             aucune donnée personnelle au-delà de votre bibliothèque.

        [ES] Esta carpeta la utiliza la aplicación Mihon para sincronizar tu biblioteca entre tus
             dispositivos: favoritos, progreso de lectura y categorías. Borrarla no dañará tus
             dispositivos, pero la sincronización empezará de cero.

        [DE] Dieser Ordner wird von der Mihon-App verwendet, um deine Bibliothek zwischen deinen
             Geräten zu synchronisieren: Favoriten, Lesefortschritt und Kategorien. Ein Löschen
             schadet deinen Geräten nicht, die Synchronisierung beginnt jedoch von vorn.

        [PT] Esta pasta é usada pelo aplicativo Mihon para sincronizar sua biblioteca entre seus
             dispositivos: favoritos, progresso de leitura e categorias. Excluí-la não prejudicará
             seus dispositivos, mas a sincronização recomeçará do zero.

        [IT] Questa cartella è utilizzata dall'app Mihon per sincronizzare la tua libreria tra i
             tuoi dispositivi: preferiti, avanzamento di lettura e categorie. Eliminarla non
             danneggerà i tuoi dispositivi, ma la sincronizzazione ripartirà da zero.

        [RU] Эта папка используется приложением Mihon для синхронизации вашей библиотеки между
             устройствами: избранное, прогресс чтения и категории. Её удаление не повредит
             устройствам, но синхронизация начнётся заново.

        [JA] このフォルダーは、Mihon アプリがデバイス間でライブラリ（お気に入り、読書の進捗、
             カテゴリ）を同期するために使用します。削除してもデバイスに影響はありませんが、
             同期は最初からやり直しになります。

        [ZH] 此文件夹由 Mihon 应用用于在您的设备之间同步书库：收藏、阅读进度和分类。
             删除它不会损坏您的设备，但同步将从头开始。

        Structure
        ---------
          $LIBRARY_FOLDER/      one file per library entry
          $CATEGORY_LIST_FILE         your categories
          $SOURCES_FILE         your extension repositories, and the names of the sources you use
          $EXTENSIONS_FILE      extensions your devices have installed, so a new one can offer them
          $DEVICES_FILE         devices taking part in the synchronisation
          $HISTORY_FILE         log of every synchronisation
    """.trimIndent()

    private const val HASH_BYTES = 8
}
