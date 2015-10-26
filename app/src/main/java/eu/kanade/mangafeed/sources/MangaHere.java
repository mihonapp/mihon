package eu.kanade.mangafeed.sources;

import android.content.Context;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import eu.kanade.mangafeed.data.helpers.SourceManager;
import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.sources.base.Source;
import rx.Observable;

public class MangaHere extends Source {

    public static final String NAME = "MangaHere (EN)";
    public static final String BASE_URL = "www.mangahere.co";

    private static final String INITIAL_UPDATE_URL = "http://www.mangahere.co/latest/";
    private static final String INITIAL_SEARCH_URL = "http://www.mangahere.co/search.php?";

    public MangaHere(Context context) {
        super(context);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getSourceId() {
        return SourceManager.MANGAHERE;
    }

    @Override
    protected String getUrlFromPageNumber(int page) {
        return INITIAL_UPDATE_URL + page + "/";
    }

    @Override
    protected String getSearchUrl(String query, int page) {
        return INITIAL_SEARCH_URL + "name=" + query + "&page=" + page;
    }


    @Override
    public boolean isLoginRequired() {
        return false;
    }

    public Observable<List<String>> getGenres() {
        List<String> genres = new ArrayList<>(30);

        genres.add("Action");
        genres.add("Adventure");
        genres.add("Comedy");
        genres.add("Drama");
        genres.add("Ecchi");
        genres.add("Fantasy");
        genres.add("Gender Bender");
        genres.add("Harem");
        genres.add("Historical");
        genres.add("Horror");
        genres.add("Josei");
        genres.add("Martial Arts");
        genres.add("Mature");
        genres.add("Mecha");
        genres.add("Mystery");
        genres.add("One Shot");
        genres.add("Psychological");
        genres.add("Romance");
        genres.add("School Life");
        genres.add("Sci-fi");
        genres.add("Seinen");
        genres.add("Shoujo");
        genres.add("Shoujo Ai");
        genres.add("Shounen");
        genres.add("Shounen Ai");
        genres.add("Slice of Life");
        genres.add("Sports");
        genres.add("Supernatural");
        genres.add("Tragedy");
        genres.add("Yaoi");
        genres.add("Yuri");

        return Observable.just(genres);
    }

    @Override
    public List<Manga> parsePopularMangasFromHtml(String unparsedHtml) {
        Document parsedDocument = Jsoup.parse(unparsedHtml);

        List<Manga> updatedMangaList = new ArrayList<>();

        Elements updatedHtmlBlocks = parsedDocument.select("div.manga_updates dl");
        for (Element currentHtmlBlock : updatedHtmlBlocks) {
            Manga currentlyUpdatedManga = constructMangaFromHtmlBlock(currentHtmlBlock);

            updatedMangaList.add(currentlyUpdatedManga);
        }

        return updatedMangaList;
    }

    @Override
    protected List<Manga> parseSearchFromHtml(String unparsedHtml) {
        return null;
    }

    private Manga constructMangaFromHtmlBlock(Element htmlBlock) {
        Manga mangaFromHtmlBlock = new Manga();
        mangaFromHtmlBlock.source = getSourceId();

        Element urlElement = htmlBlock.select("a.manga_info").first();
        Element nameElement = htmlBlock.select("a.manga_info").first();
        Element updateElement = htmlBlock.select("span.time").first();

        if (urlElement != null) {
            String fieldUrl = urlElement.attr("href");
            mangaFromHtmlBlock.url = fieldUrl;
        }
        if (nameElement != null) {
            String fieldName = nameElement.text();
            mangaFromHtmlBlock.title = fieldName;
        }
        if (updateElement != null) {
            long fieldUpdate = parseUpdateFromElement(updateElement);
            mangaFromHtmlBlock.last_update = fieldUpdate;
        }

        return mangaFromHtmlBlock;
    }

    private long parseUpdateFromElement(Element updateElement) {
        String updatedDateAsString = updateElement.text();

        if (updatedDateAsString.contains("Today")) {
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            try {
                Date withoutDay = new SimpleDateFormat("MMM d, yyyy h:mma", Locale.ENGLISH).parse(updatedDateAsString.replace("Today", ""));
                return today.getTimeInMillis() + withoutDay.getTime();
            } catch (ParseException e) {
                return today.getTimeInMillis();
            }
        } else if (updatedDateAsString.contains("Yesterday")) {
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DATE, -1);
            yesterday.set(Calendar.HOUR_OF_DAY, 0);
            yesterday.set(Calendar.MINUTE, 0);
            yesterday.set(Calendar.SECOND, 0);
            yesterday.set(Calendar.MILLISECOND, 0);

            try {
                Date withoutDay = new SimpleDateFormat("MMM d, yyyy h:mma", Locale.ENGLISH).parse(updatedDateAsString.replace("Yesterday", ""));
                return yesterday.getTimeInMillis() + withoutDay.getTime();
            } catch (ParseException e) {
                return yesterday.getTimeInMillis();
            }
        } else {
            try {
                Date specificDate = new SimpleDateFormat("MMM d, yyyy h:mma", Locale.ENGLISH).parse(updatedDateAsString);

                return specificDate.getTime();
            } catch (ParseException e) {
                // Do Nothing.
            }
        }

        return 0;
    }

    public Manga parseHtmlToManga(String mangaUrl, String unparsedHtml) {
        int beginIndex = unparsedHtml.indexOf("<ul class=\"detail_topText\">");
        int endIndex = unparsedHtml.indexOf("</ul>", beginIndex);
        String trimmedHtml = unparsedHtml.substring(beginIndex, endIndex);

        Document parsedDocument = Jsoup.parse(trimmedHtml);

        Elements detailElements = parsedDocument.select("ul.detail_topText li");

        Element artistElement = parsedDocument.select("a[href^=http://www.mangahere.co/artist/]").first();
        Element authorElement = parsedDocument.select("a[href^=http://www.mangahere.co/author/]").first();
        Element descriptionElement = detailElements.select("#show").first();
        Element genreElement = detailElements.get(3);
        Element statusElement = detailElements.get(6);

        Manga newManga = new Manga();
        newManga.url = mangaUrl;

        if (artistElement != null) {
            String fieldArtist = artistElement.text();
            newManga.artist = fieldArtist;
        }
        if (authorElement != null) {
            String fieldAuthor = authorElement.text();
            newManga.author = fieldAuthor;
        }
        if (descriptionElement != null) {
            String fieldDescription = descriptionElement.text().substring(0, descriptionElement.text().length() - "Show less".length());
            newManga.description = fieldDescription;
        }
        if (genreElement != null) {
            String fieldGenre = genreElement.text().substring("Genre(s):".length());
            newManga.genre = fieldGenre;
        }
        if (statusElement != null) {
            boolean fieldCompleted = statusElement.text().contains("Completed");
            newManga.status = fieldCompleted + "";
        }

        beginIndex = unparsedHtml.indexOf("<img");
        endIndex = unparsedHtml.indexOf("/>", beginIndex);
        trimmedHtml = unparsedHtml.substring(beginIndex, endIndex + 2);
        parsedDocument = Jsoup.parse(trimmedHtml);
        Element thumbnailUrlElement = parsedDocument.select("img").first();

        if (thumbnailUrlElement != null) {
            String fieldThumbnailUrl = thumbnailUrlElement.attr("src");
            newManga.thumbnail_url = fieldThumbnailUrl;
        }

        newManga.initialized = true;

        return newManga;
    }

    @Override
    public List<Chapter> parseHtmlToChapters(String unparsedHtml) {
        int beginIndex = unparsedHtml.indexOf("<ul>");
        int endIndex = unparsedHtml.indexOf("</ul>", beginIndex);
        String trimmedHtml = unparsedHtml.substring(beginIndex, endIndex);

        Document parsedDocument = Jsoup.parse(trimmedHtml);

        List<Chapter> chapterList = new ArrayList<Chapter>();

        Elements chapterElements = parsedDocument.getElementsByTag("li");
        for (Element chapterElement : chapterElements) {
            Chapter currentChapter = constructChapterFromHtmlBlock(chapterElement);

            chapterList.add(currentChapter);
        }

        return chapterList;
    }

    private Chapter constructChapterFromHtmlBlock(Element chapterElement) {
        Chapter newChapter = Chapter.newChapter();

        Element urlElement = chapterElement.select("a").first();
        Element nameElement = chapterElement.select("a").first();
        Element dateElement = chapterElement.select("span.right").first();

        if (urlElement != null) {
            String fieldUrl = urlElement.attr("href");
            newChapter.url = fieldUrl;
        }
        if (nameElement != null) {
            String fieldName = nameElement.text();
            newChapter.name = fieldName;
        }
        if (dateElement != null) {
            long fieldDate = parseDateFromElement(dateElement);
            newChapter.date_upload = fieldDate;
        }
        newChapter.date_fetch = new Date().getTime();

        return newChapter;
    }

    private long parseDateFromElement(Element dateElement) {
        String dateAsString = dateElement.text();

        if (dateAsString.contains("Today")) {
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            try {
                Date withoutDay = new SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).parse(dateAsString.replace("Today", ""));
                return today.getTimeInMillis() + withoutDay.getTime();
            } catch (ParseException e) {
                return today.getTimeInMillis();
            }
        } else if (dateAsString.contains("Yesterday")) {
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DATE, -1);
            yesterday.set(Calendar.HOUR_OF_DAY, 0);
            yesterday.set(Calendar.MINUTE, 0);
            yesterday.set(Calendar.SECOND, 0);
            yesterday.set(Calendar.MILLISECOND, 0);

            try {
                Date withoutDay = new SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).parse(dateAsString.replace("Yesterday", ""));
                return yesterday.getTimeInMillis() + withoutDay.getTime();
            } catch (ParseException e) {
                return yesterday.getTimeInMillis();
            }
        } else {
            try {
                Date date = new SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).parse(dateAsString);

                return date.getTime();
            } catch (ParseException e) {
                // Do Nothing.
            }
        }

        return 0;
    }

    @Override
    public List<String> parseHtmlToPageUrls(String unparsedHtml) {
        int beginIndex = unparsedHtml.indexOf("<div class=\"go_page clearfix\">");
        int endIndex = unparsedHtml.indexOf("</div>", beginIndex);
        String trimmedHtml = unparsedHtml.substring(beginIndex, endIndex);

        Document parsedDocument = Jsoup.parse(trimmedHtml);

        List<String> pageUrlList = new ArrayList<String>();

        Elements pageUrlElements = parsedDocument.select("select.wid60").first().getElementsByTag("option");
        for (Element pageUrlElement : pageUrlElements) {
            pageUrlList.add(pageUrlElement.attr("value"));
        }

        return pageUrlList;
    }

    @Override
    public String parseHtmlToImageUrl(String unparsedHtml) {
        int beginIndex = unparsedHtml.indexOf("<section class=\"read_img\" id=\"viewer\">");
        int endIndex = unparsedHtml.indexOf("</section>", beginIndex);
        String trimmedHtml = unparsedHtml.substring(beginIndex, endIndex);

        Document parsedDocument = Jsoup.parse(trimmedHtml);

        Element imageElement = parsedDocument.getElementById("image");

        return imageElement.attr("src");
    }

}
