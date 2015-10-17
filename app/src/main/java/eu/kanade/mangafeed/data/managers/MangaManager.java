package eu.kanade.mangafeed.data.managers;

import com.pushtorefresh.storio.sqlite.operations.delete.DeleteResult;
import com.pushtorefresh.storio.sqlite.operations.delete.DeleteResults;
import com.pushtorefresh.storio.sqlite.operations.put.PutResult;
import com.pushtorefresh.storio.sqlite.operations.put.PutResults;

import java.util.List;

import eu.kanade.mangafeed.data.models.Manga;
import rx.Observable;

public interface MangaManager {

    Observable<List<Manga>> getMangas();

    Observable<List<Manga>> getMangasWithUnread();

    Observable<List<Manga>> getManga(String url);

    Observable<List<Manga>> getManga(long id);

    Manga getMangaBlock(String url);

    Observable<PutResult> insertManga(Manga manga);

    Observable<PutResults<Manga>> insertMangas(List<Manga> mangas);

    PutResult insertMangaBlock(Manga manga);

    Observable<DeleteResult> deleteManga(Manga manga);

    Observable<DeleteResults<Manga>> deleteMangas(List<Manga> mangas);

}
