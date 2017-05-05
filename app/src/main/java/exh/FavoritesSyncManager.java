package exh;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AlertDialog;

import com.pushtorefresh.storio.sqlite.operations.put.PutResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.kanade.tachiyomi.data.database.DatabaseHelper;
import eu.kanade.tachiyomi.data.database.models.Category;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.database.models.MangaCategory;
import eu.kanade.tachiyomi.source.online.all.EHentai;
import kotlin.Pair;
//import eu.kanade.tachiyomi.data.source.online.english.EHentai;

public class FavoritesSyncManager {
    /*Context context;
    DatabaseHelper db;

    public FavoritesSyncManager(Context context, DatabaseHelper db) {
        this.context = context;
        this.db = db;
    }

    public void guiSyncFavorites(final Runnable onComplete) {
        if(!DialogLogin.isLoggedIn(context, false)) {
            new AlertDialog.Builder(context).setTitle("Error")
                    .setMessage("You are not logged in! Please log in and try again!")
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }).show();
            return;
        }
        final ProgressDialog dialog = ProgressDialog.show(context, "Downloading Favorites", "Please wait...", true, false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Handler mainLooper = new Handler(Looper.getMainLooper());
                try {
                    syncFavorites();
                } catch (Exception e) {
                    mainLooper.post(new Runnable() {
                        @Override
                        public void run() {
                            new AlertDialog.Builder(context)
                                    .setTitle("Error")
                                    .setMessage("There was an error downloading your favorites, please try again later!")
                                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    }).show();
                        }
                    });
                    e.printStackTrace();
                }
                dialog.dismiss();
                mainLooper.post(onComplete);
            }
        }).start();
    }*/
/*
    public void syncFavorites() throws IOException {
        Pair favResponse = EHentai.fetchFavorites(context);
        Map<String, List<Manga>> favorites = favResponse.favs;
        List<Category> ourCategories = new ArrayList<>(db.getCategories().executeAsBlocking());
        List<Manga> ourMangas = new ArrayList<>(db.getMangas().executeAsBlocking());
        //Add required categories (categories do not sync upwards)
        List<Category> categoriesToInsert = new ArrayList<>();
        for (String theirCategory : favorites.keySet()) {
            boolean haveCategory = false;
            for (Category category : ourCategories) {
                if (category.getName().endsWith(theirCategory)) {
                    haveCategory = true;
                }
            }
            if (!haveCategory) {
                Category category = Category.Companion.create(theirCategory);
                ourCategories.add(category);
                categoriesToInsert.add(category);
            }
        }
        if (!categoriesToInsert.isEmpty()) {
            for(Map.Entry<Category, PutResult> result : db.insertCategories(categoriesToInsert).executeAsBlocking().results().entrySet()) {
                if(result.getValue().wasInserted()) {
                    result.getKey().setId(result.getValue().insertedId().intValue());
                }
            }
        }
        //Build category map
        Map<String, Category> categoryMap = new HashMap<>();
        for (Category category : ourCategories) {
            categoryMap.put(category.getName(), category);
        }
        //Insert new mangas
        List<Manga> mangaToInsert = new ArrayList<>();
        Map<Manga, Category> mangaToSetCategories = new HashMap<>();
        for (Map.Entry<String, List<Manga>> entry : favorites.entrySet()) {
            Category category = categoryMap.get(entry.getKey());
            for (Manga manga : entry.getValue()) {
                boolean alreadyHaveManga = false;
                for (Manga ourManga : ourMangas) {
                    if (ourManga.getUrl().equals(manga.getUrl())) {
                        alreadyHaveManga = true;
                        manga = ourManga;
                        break;
                    }
                }
                if (!alreadyHaveManga) {
                    ourMangas.add(manga);
                    mangaToInsert.add(manga);
                }
                mangaToSetCategories.put(manga, category);
                manga.setFavorite(true);
            }
        }
        for (Map.Entry<Manga, PutResult> results : db.insertMangas(mangaToInsert).executeAsBlocking().results().entrySet()) {
            if(results.getValue().wasInserted()) {
                results.getKey().setId(results.getValue().insertedId());
            }
        }
        for(Map.Entry<Manga, Category> entry : mangaToSetCategories.entrySet()) {
            db.setMangaCategories(Collections.singletonList(MangaCategory.Companion.create(entry.getKey(), entry.getValue())),
                    Collections.singletonList(entry.getKey()));
        }*/
        //Determines what
        /*Map<Integer, List<Manga>> toUpload = new HashMap<>();
        for (Manga manga : ourMangas) {
            if(manga.getFavorite()) {
                boolean remoteHasManga = false;
                for (List<Manga> remoteMangas : favorites.values()) {
                    for (Manga remoteManga : remoteMangas) {
                        if (remoteManga.getUrl().equals(manga.getUrl())) {
                            remoteHasManga = true;
                            break;
                        }
                    }
                }
                if (!remoteHasManga) {
                    List<Category> mangaCategories = db.getCategoriesForManga(manga).executeAsBlocking();
                    for (Category category : mangaCategories) {
                        int categoryIndex = favResponse.favCategories.indexOf(category.getName());
                        if (categoryIndex >= 0) {
                            List<Manga> uploadMangas = toUpload.get(categoryIndex);
                            if (uploadMangas == null) {
                                uploadMangas = new ArrayList<>();
                                toUpload.put(categoryIndex, uploadMangas);
                            }
                            uploadMangas.add(manga);
                        }
                    }
                }
            }
        }*/
        /********** NON-FUNCTIONAL, modifygids[] CANNOT ADD NEW FAVORITES! (or as of my testing it can't, maybe I'll do more testing)**/
        /*PreferencesHelper helper = new PreferencesHelper(context);
        for(Map.Entry<Integer, List<Manga>> entry : toUpload.entrySet()) {
            FormBody.Builder formBody = new FormBody.Builder()
                    .add("ddact", "fav" + entry.getKey());
            for(Manga manga : entry.getValue()) {
                List<String> splitUrl = new ArrayList<>(Arrays.asList(manga.getUrl().split("/")));
                splitUrl.removeAll(Collections.singleton(""));
                if(splitUrl.size() < 2) {
                    continue;
                }
                formBody.add("modifygids[]", splitUrl.get(1).trim());
            }
            formBody.add("apply", "Apply");
            Request request = RequestsKt.POST(EHentai.buildFavoritesBase(context, helper.getPrefs()).favoritesBase,
                    EHentai.getHeadersBuilder(helper).build(),
                    formBody.build(),
                    RequestsKt.getDEFAULT_CACHE_CONTROL());
            Response response = NetworkManager.getInstance().getClient().newCall(request).execute();
            Util.d("EHentai", response.body().string());
        }*/
//    }
}
