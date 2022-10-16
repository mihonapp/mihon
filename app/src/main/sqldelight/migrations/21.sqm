CREATE VIEW libraryView AS
SELECT
    M.*,
    coalesce(C.total - C.readCount, 0) AS unreadCount,
    coalesce(C.readCount, 0) AS readCount,
    coalesce(C.latestUpload, 0) AS latestUpload,
    coalesce(C.fetchedAt, 0) AS chapterFetchedAt,
    coalesce(C.lastRead, 0) AS lastRead,
    COALESCE(MC.category_id, 0) AS category
FROM mangas M
LEFT JOIN mangas_categories AS MC
ON MC.manga_id = M._id
LEFT JOIN(
    SELECT
        chapters.manga_id,
        count(*) AS total,
        sum(read) AS readCount,
        max(chapters.date_upload) AS latestUpload,
        max(history.last_read) AS lastRead,
        max(chapters.date_fetch) AS fetchedAt
    FROM chapters
    LEFT JOIN history
    ON chapters._id = history.chapter_id
    GROUP BY chapters.manga_id
) AS C
ON M._id = C.manga_id
WHERE M.favorite = 1;
