DROP INDEX IF EXISTS mangas_favorite_index;

CREATE INDEX library_favorite_index
ON mangas(favorite)
WHERE favorite = 1;

CREATE INDEX chapters_unread_by_manga_index
ON chapters(manga_id, read)
WHERE read = 0;