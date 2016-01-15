package eu.kanade.tachiyomi;

import android.app.Application;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import eu.kanade.tachiyomi.data.database.DatabaseHelper;
import eu.kanade.tachiyomi.data.database.models.Category;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.database.models.MangaCategory;

import static org.assertj.core.api.Assertions.*;

@Config(constants = BuildConfig.class, sdk = Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class CategoryTest {

    DatabaseHelper db;

    @Before
    public void setup() {
        Application app = RuntimeEnvironment.application;
        db = new DatabaseHelper(app);

        // Create 5 mangas
        createManga("a");
        createManga("b");
        createManga("c");
        createManga("d");
        createManga("e");
    }

    @Test
    public void testHasCategories() {
        // Create 2 categories
        createCategory("Reading");
        createCategory("Hold");

        List<Category> categories = db.getCategories().executeAsBlocking();
        assertThat(categories).hasSize(2);
    }

    @Test
    public void testHasLibraryMangas() {
        List<Manga> mangas = db.getLibraryMangas().executeAsBlocking();
        assertThat(mangas).hasSize(5);
    }

    @Test
    public void testHasCorrectFavorites() {
        Manga m = new Manga();
        m.title = "title";
        m.author = "";
        m.artist = "";
        m.thumbnail_url = "";
        m.genre = "a list of genres";
        m.description = "long description";
        m.url = "url to manga";
        m.favorite = false;
        db.insertManga(m).executeAsBlocking();
        List<Manga> mangas = db.getLibraryMangas().executeAsBlocking();
        assertThat(mangas).hasSize(5);
    }

    @Test
    public void testMangaInCategory() {
        // Create 2 categories
        createCategory("Reading");
        createCategory("Hold");

        // It should not have 0 as id
        Category c = db.getCategories().executeAsBlocking().get(0);
        assertThat(c.id).isNotZero();

        // Add a manga to a category
        Manga m = db.getMangas().executeAsBlocking().get(0);
        MangaCategory mc = MangaCategory.create(m, c);
        db.insertMangaCategory(mc).executeAsBlocking();

        // Get mangas from library and assert manga category is the same
        List<Manga> mangas = db.getLibraryMangas().executeAsBlocking();
        for (Manga manga : mangas) {
            if (manga.id.equals(m.id)) {
                assertThat(manga.category).isEqualTo(c.id);
            }
        }
    }

    private void createManga(String title) {
        Manga m = new Manga();
        m.title = title;
        m.author = "";
        m.artist = "";
        m.thumbnail_url = "";
        m.genre = "a list of genres";
        m.description = "long description";
        m.url = "url to manga";
        m.favorite = true;
        db.insertManga(m).executeAsBlocking();
    }

    private void createCategory(String name) {
        Category c = new Category();
        c.name = name;
        db.insertCategory(c).executeAsBlocking();
    }

}
