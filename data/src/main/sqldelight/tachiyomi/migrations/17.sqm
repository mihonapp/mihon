DROP VIEW IF EXISTS historyView;

CREATE VIEW historyView AS
SELECT
    history._id AS id,
    mangas._id AS mangaId,
    chapters._id AS chapterId,
    mangas.title,
    mangas.thumbnail_url AS thumbnailUrl,
    mangas.source,
    mangas.favorite,
    mangas.cover_last_modified,
    chapters.chapter_number AS chapterNumber,
    history.last_read AS readAt,
    history.time_read AS readDuration,
    max_last_read.last_read AS maxReadAt,
    max_last_read.chapter_id AS maxReadAtChapterId
FROM mangas
JOIN chapters
ON mangas._id = chapters.manga_id
JOIN history
ON chapters._id = history.chapter_id
JOIN (
    SELECT chapters.manga_id,chapters._id AS chapter_id, MAX(history.last_read) AS last_read
    FROM chapters JOIN history
    ON chapters._id = history.chapter_id
    GROUP BY chapters.manga_id
) AS max_last_read
ON chapters.manga_id = max_last_read.manga_id;
