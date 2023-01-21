ALTER TABLE mangas
ADD COLUMN date_added INTEGER NOT NULL DEFAULT 0;

UPDATE mangas
SET date_added = (
    SELECT MIN(date_fetch)
    FROM mangas M
    INNER JOIN chapters C
    ON M._id = C.manga_id
    GROUP BY M._id
);