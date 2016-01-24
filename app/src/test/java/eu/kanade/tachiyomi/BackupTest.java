package eu.kanade.tachiyomi;

import android.app.Application;
import android.os.Build;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.tachiyomi.data.backup.BackupManager;
import eu.kanade.tachiyomi.data.database.DatabaseHelper;
import eu.kanade.tachiyomi.data.database.models.Category;
import eu.kanade.tachiyomi.data.database.models.Chapter;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.database.models.MangaCategory;
import eu.kanade.tachiyomi.data.database.models.MangaSync;

import static org.assertj.core.api.Assertions.assertThat;

@Config(constants = BuildConfig.class, sdk = Build.VERSION_CODES.LOLLIPOP)
@RunWith(CustomRobolectricGradleTestRunner.class)
public class BackupTest {

    DatabaseHelper db;
    BackupManager backupManager;
    Gson gson;
    JsonObject root;

    @Before
    public void setup() {
        Application app = RuntimeEnvironment.application;
        db = new DatabaseHelper(app);
        backupManager = new BackupManager(db);
        gson = new Gson();
        root = new JsonObject();
    }

    @Test
    public void testRestoreCategory() {
        String catName = "cat";
        root = createRootJson(null, toJson(createCategories(catName)));
        backupManager.restoreFromJson(root);

        List<Category> dbCats = db.getCategories().executeAsBlocking();
        assertThat(dbCats).hasSize(1);
        assertThat(dbCats.get(0).name).isEqualTo(catName);
    }

    @Test
    public void testRestoreEmptyCategory() {
        root = createRootJson(null, toJson(new ArrayList<>()));
        backupManager.restoreFromJson(root);
        List<Category> dbCats = db.getCategories().executeAsBlocking();
        assertThat(dbCats).isEmpty();
    }

    @Test
    public void testRestoreExistingCategory() {
        String catName = "cat";
        db.insertCategory(createCategory(catName)).executeAsBlocking();

        root = createRootJson(null, toJson(createCategories(catName)));
        backupManager.restoreFromJson(root);

        List<Category> dbCats = db.getCategories().executeAsBlocking();
        assertThat(dbCats).hasSize(1);
        assertThat(dbCats.get(0).name).isEqualTo(catName);
    }

    @Test
    public void testRestoreCategories() {
        root = createRootJson(null, toJson(createCategories("cat", "cat2", "cat3")));
        backupManager.restoreFromJson(root);

        List<Category> dbCats = db.getCategories().executeAsBlocking();
        assertThat(dbCats).hasSize(3);
    }

    @Test
    public void testRestoreExistingCategories() {
        db.insertCategories(createCategories("cat", "cat2")).executeAsBlocking();

        root = createRootJson(null, toJson(createCategories("cat", "cat2", "cat3")));
        backupManager.restoreFromJson(root);

        List<Category> dbCats = db.getCategories().executeAsBlocking();
        assertThat(dbCats).hasSize(3);
    }

    @Test
    public void testRestoreExistingCategoriesAlt() {
        db.insertCategories(createCategories("cat", "cat2", "cat3")).executeAsBlocking();

        root = createRootJson(null, toJson(createCategories("cat", "cat2")));
        backupManager.restoreFromJson(root);

        List<Category> dbCats = db.getCategories().executeAsBlocking();
        assertThat(dbCats).hasSize(3);
    }

    @Test
    public void testRestoreManga() {
        String mangaName = "title";
        List<Manga> mangas = createMangas(mangaName);
        List<JsonElement> elements = new ArrayList<>();
        for (Manga manga : mangas) {
            JsonObject entry = new JsonObject();
            entry.add("manga", toJson(manga));
            elements.add(entry);
        }
        root = createRootJson(toJson(elements), null);
        backupManager.restoreFromJson(root);

        List<Manga> dbMangas = db.getMangas().executeAsBlocking();
        assertThat(dbMangas).hasSize(1);
        assertThat(dbMangas.get(0).title).isEqualTo(mangaName);
    }

    @Test
    public void testRestoreExistingManga() {
        String mangaName = "title";
        Manga manga = createManga(mangaName);

        db.insertManga(manga).executeAsBlocking();

        List<JsonElement> elements = new ArrayList<>();
        JsonObject entry = new JsonObject();
        entry.add("manga", toJson(manga));
        elements.add(entry);

        root = createRootJson(toJson(elements), null);
        backupManager.restoreFromJson(root);

        List<Manga> dbMangas = db.getMangas().executeAsBlocking();
        assertThat(dbMangas).hasSize(1);
    }

    @Test
    public void testRestoreExistingMangaWithUpdatedFields() {
        // Store a manga in db
        String mangaName = "title";
        String updatedThumbnailUrl = "updated thumbnail url";
        Manga manga = createManga(mangaName);
        manga.chapter_flags = 1024;
        manga.thumbnail_url = updatedThumbnailUrl;
        db.insertManga(manga).executeAsBlocking();

        // Add an entry for a new manga with different attributes
        manga = createManga(mangaName);
        manga.chapter_flags = 512;
        JsonObject entry = new JsonObject();
        entry.add("manga", toJson(manga));

        // Append the entry to the backup list
        List<JsonElement> elements = new ArrayList<>();
        elements.add(entry);

        // Restore from json
        root = createRootJson(toJson(elements), null);
        backupManager.restoreFromJson(root);

        List<Manga> dbMangas = db.getMangas().executeAsBlocking();
        assertThat(dbMangas).hasSize(1);
        assertThat(dbMangas.get(0).thumbnail_url).isEqualTo(updatedThumbnailUrl);
        assertThat(dbMangas.get(0).chapter_flags).isEqualTo(512);
    }

    @Test
    public void testRestoreChaptersForManga() {
        // Create a manga and 3 chapters
        Manga manga = createManga("title");
        manga.id = 1L;
        List<Chapter> chapters = createChapters(manga, "1", "2", "3");

        // Add an entry for the manga
        JsonObject entry = new JsonObject();
        entry.add("manga", toJson(manga));
        entry.add("chapters", toJson(chapters));

        // Append the entry to the backup list
        List<JsonElement> mangas = new ArrayList<>();
        mangas.add(entry);

        // Restore from json
        root = createRootJson(toJson(mangas), null);
        backupManager.restoreFromJson(root);

        Manga dbManga = db.getManga(1).executeAsBlocking();
        assertThat(dbManga).isNotNull();

        List<Chapter> dbChapters = db.getChapters(dbManga).executeAsBlocking();
        assertThat(dbChapters).hasSize(3);
    }

    @Test
    public void testRestoreChaptersForExistingManga() {
        long mangaId = 3;
        // Create a manga and 3 chapters
        Manga manga = createManga("title");
        manga.id = mangaId;
        List<Chapter> chapters = createChapters(manga, "1", "2", "3");
        db.insertManga(manga).executeAsBlocking();

        // Add an entry for the manga
        JsonObject entry = new JsonObject();
        entry.add("manga", toJson(manga));
        entry.add("chapters", toJson(chapters));

        // Append the entry to the backup list
        List<JsonElement> mangas = new ArrayList<>();
        mangas.add(entry);

        // Restore from json
        root = createRootJson(toJson(mangas), null);
        backupManager.restoreFromJson(root);

        Manga dbManga = db.getManga(mangaId).executeAsBlocking();
        assertThat(dbManga).isNotNull();

        List<Chapter> dbChapters = db.getChapters(dbManga).executeAsBlocking();
        assertThat(dbChapters).hasSize(3);
    }

    @Test
    public void testRestoreExistingChaptersForExistingManga() {
        long mangaId = 5;
        // Store a manga and 3 chapters
        Manga manga = createManga("title");
        manga.id = mangaId;
        List<Chapter> chapters = createChapters(manga, "1", "2", "3");
        db.insertManga(manga).executeAsBlocking();
        db.insertChapters(chapters).executeAsBlocking();

        // The backup contains a existing chapter and a new one, so it should have 4 chapters
        chapters = createChapters(manga, "3", "4");

        // Add an entry for the manga
        JsonObject entry = new JsonObject();
        entry.add("manga", toJson(manga));
        entry.add("chapters", toJson(chapters));

        // Append the entry to the backup list
        List<JsonElement> mangas = new ArrayList<>();
        mangas.add(entry);

        // Restore from json
        root = createRootJson(toJson(mangas), null);
        backupManager.restoreFromJson(root);

        Manga dbManga = db.getManga(mangaId).executeAsBlocking();
        assertThat(dbManga).isNotNull();

        List<Chapter> dbChapters = db.getChapters(dbManga).executeAsBlocking();
        assertThat(dbChapters).hasSize(4);
    }

    @Test
    public void testRestoreCategoriesForManga() {
        // Create a manga
        Manga manga = createManga("title");

        // Create categories
        List<Category> categories = createCategories("cat1", "cat2", "cat3");

        // Add an entry for the manga
        JsonObject entry = new JsonObject();
        entry.add("manga", toJson(manga));
        entry.add("categories", toJson(createStringCategories("cat1")));

        // Append the entry to the backup list
        List<JsonElement> mangas = new ArrayList<>();
        mangas.add(entry);

        // Restore from json
        root = createRootJson(toJson(mangas), toJson(categories));
        backupManager.restoreFromJson(root);

        Manga dbManga = db.getManga(1).executeAsBlocking();
        assertThat(dbManga).isNotNull();

        assertThat(db.getCategoriesForManga(dbManga).executeAsBlocking())
                .hasSize(1)
                .contains(Category.create("cat1"))
                .doesNotContain(Category.create("cat2"));
    }

    @Test
    public void testRestoreCategoriesForExistingManga() {
        // Store a manga
        Manga manga = createManga("title");
        db.insertManga(manga).executeAsBlocking();

        // Create categories
        List<Category> categories = createCategories("cat1", "cat2", "cat3");

        // Add an entry for the manga
        JsonObject entry = new JsonObject();
        entry.add("manga", toJson(manga));
        entry.add("categories", toJson(createStringCategories("cat1")));

        // Append the entry to the backup list
        List<JsonElement> mangas = new ArrayList<>();
        mangas.add(entry);

        // Restore from json
        root = createRootJson(toJson(mangas), toJson(categories));
        backupManager.restoreFromJson(root);

        Manga dbManga = db.getManga(1).executeAsBlocking();
        assertThat(dbManga).isNotNull();

        assertThat(db.getCategoriesForManga(dbManga).executeAsBlocking())
                .hasSize(1)
                .contains(Category.create("cat1"))
                .doesNotContain(Category.create("cat2"));
    }

    @Test
    public void testRestoreMultipleCategoriesForManga() {
        // Create a manga
        Manga manga = createManga("title");

        // Create categories
        List<Category> categories = createCategories("cat1", "cat2", "cat3");

        // Add an entry for the manga
        JsonObject entry = new JsonObject();
        entry.add("manga", toJson(manga));
        entry.add("categories", toJson(createStringCategories("cat1", "cat3")));

        // Append the entry to the backup list
        List<JsonElement> mangas = new ArrayList<>();
        mangas.add(entry);

        // Restore from json
        root = createRootJson(toJson(mangas), toJson(categories));
        backupManager.restoreFromJson(root);

        Manga dbManga = db.getManga(1).executeAsBlocking();
        assertThat(dbManga).isNotNull();

        assertThat(db.getCategoriesForManga(dbManga).executeAsBlocking())
                .hasSize(2)
                .contains(Category.create("cat1"), Category.create("cat3"))
                .doesNotContain(Category.create("cat2"));
    }

    @Test
    public void testRestoreMultipleCategoriesForExistingMangaAndCategory() {
        // Store a manga and a category
        Manga manga = createManga("title");
        manga.id = 1L;
        db.insertManga(manga).executeAsBlocking();

        Category cat = createCategory("cat1");
        cat.id = 1;
        db.insertCategory(cat).executeAsBlocking();
        db.insertMangaCategory(MangaCategory.create(manga, cat)).executeAsBlocking();

        // Create categories
        List<Category> categories = createCategories("cat1", "cat2", "cat3");

        // Add an entry for the manga
        JsonObject entry = new JsonObject();
        entry.add("manga", toJson(manga));
        entry.add("categories", toJson(createStringCategories("cat1", "cat2")));

        // Append the entry to the backup list
        List<JsonElement> mangas = new ArrayList<>();
        mangas.add(entry);

        // Restore from json
        root = createRootJson(toJson(mangas), toJson(categories));
        backupManager.restoreFromJson(root);

        Manga dbManga = db.getManga(1).executeAsBlocking();
        assertThat(dbManga).isNotNull();

        assertThat(db.getCategoriesForManga(dbManga).executeAsBlocking())
                .hasSize(2)
                .contains(Category.create("cat1"), Category.create("cat2"))
                .doesNotContain(Category.create("cat3"));
    }

    @Test
    public void testRestoreSyncForManga() {
        // Create a manga and mangaSync
        Manga manga = createManga("title");
        manga.id = 1L;

        List<MangaSync> mangaSync = createMangaSync(manga, 1, 2, 3);

        // Add an entry for the manga
        JsonObject entry = new JsonObject();
        entry.add("manga", toJson(manga));
        entry.add("sync", toJson(mangaSync));

        // Append the entry to the backup list
        List<JsonElement> mangas = new ArrayList<>();
        mangas.add(entry);

        // Restore from json
        root = createRootJson(toJson(mangas), null);
        backupManager.restoreFromJson(root);

        Manga dbManga = db.getManga(1).executeAsBlocking();
        assertThat(dbManga).isNotNull();

        List<MangaSync> dbSync = db.getMangasSync(dbManga).executeAsBlocking();
        assertThat(dbSync).hasSize(3);
    }

    @Test
    public void testRestoreSyncForExistingManga() {
        long mangaId = 3;
        // Create a manga and 3 sync
        Manga manga = createManga("title");
        manga.id = mangaId;
        List<MangaSync> mangaSync = createMangaSync(manga, 1, 2, 3);
        db.insertManga(manga).executeAsBlocking();

        // Add an entry for the manga
        JsonObject entry = new JsonObject();
        entry.add("manga", toJson(manga));
        entry.add("sync", toJson(mangaSync));

        // Append the entry to the backup list
        List<JsonElement> mangas = new ArrayList<>();
        mangas.add(entry);

        // Restore from json
        root = createRootJson(toJson(mangas), null);
        backupManager.restoreFromJson(root);

        Manga dbManga = db.getManga(mangaId).executeAsBlocking();
        assertThat(dbManga).isNotNull();

        List<MangaSync> dbSync = db.getMangasSync(dbManga).executeAsBlocking();
        assertThat(dbSync).hasSize(3);
    }

    @Test
    public void testRestoreExistingSyncForExistingManga() {
        long mangaId = 5;
        // Store a manga and 3 sync
        Manga manga = createManga("title");
        manga.id = mangaId;
        List<MangaSync> mangaSync = createMangaSync(manga, 1, 2, 3);
        db.insertManga(manga).executeAsBlocking();
        db.insertMangasSync(mangaSync).executeAsBlocking();

        // The backup contains a existing sync and a new one, so it should have 4 sync
        mangaSync = createMangaSync(manga, 3, 4);

        // Add an entry for the manga
        JsonObject entry = new JsonObject();
        entry.add("manga", toJson(manga));
        entry.add("sync", toJson(mangaSync));

        // Append the entry to the backup list
        List<JsonElement> mangas = new ArrayList<>();
        mangas.add(entry);

        // Restore from json
        root = createRootJson(toJson(mangas), null);
        backupManager.restoreFromJson(root);

        Manga dbManga = db.getManga(mangaId).executeAsBlocking();
        assertThat(dbManga).isNotNull();

        List<MangaSync> dbSync = db.getMangasSync(dbManga).executeAsBlocking();
        assertThat(dbSync).hasSize(4);
    }

    private JsonObject createRootJson(JsonElement mangas, JsonElement categories) {
        JsonObject root = new JsonObject();
        if (mangas != null)
            root.add("mangas", mangas);
        if (categories != null)
            root.add("categories", categories);
        return root;
    }

    private Category createCategory(String name) {
        Category c = new Category();
        c.name = name;
        return c;
    }

    private List<Category> createCategories(String... names) {
        List<Category> cats = new ArrayList<>();
        for (String name : names) {
            cats.add(createCategory(name));
        }
        return cats;
    }

    private List<String> createStringCategories(String... names) {
        List<String> cats = new ArrayList<>();
        for (String name : names) {
            cats.add(name);
        }
        return cats;
    }

    private Manga createManga(String title) {
        Manga m = new Manga();
        m.title = title;
        m.author = "";
        m.artist = "";
        m.thumbnail_url = "";
        m.genre = "a list of genres";
        m.description = "long description";
        m.url = "url to manga";
        m.favorite = true;
        m.source = 1;
        return m;
    }

    private List<Manga> createMangas(String... titles) {
        List<Manga> mangas = new ArrayList<>();
        for (String title : titles) {
            mangas.add(createManga(title));
        }
        return mangas;
    }

    private Chapter createChapter(Manga manga, String url) {
        Chapter c = Chapter.create();
        c.url = url;
        c.name = url;
        c.manga_id = manga.id;
        return c;
    }

    private List<Chapter> createChapters(Manga manga, String... urls) {
        List<Chapter> chapters = new ArrayList<>();
        for (String url : urls) {
            chapters.add(createChapter(manga, url));
        }
        return chapters;
    }

    private MangaSync createMangaSync(Manga manga, int syncId) {
        MangaSync m = MangaSync.create();
        m.manga_id = manga.id;
        m.sync_id = syncId;
        m.title = "title";
        return m;
    }

    private List<MangaSync> createMangaSync(Manga manga, Integer... syncIds) {
        List<MangaSync> ms = new ArrayList<>();
        for (int title : syncIds) {
            ms.add(createMangaSync(manga, title));
        }
        return ms;
    }

    private JsonElement toJson(Object element) {
        return gson.toJsonTree(element);
    }

}