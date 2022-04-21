ALTER TABLE chapters
ADD COLUMN source_order INTEGER DEFAULT 0;

UPDATE mangas
SET thumbnail_url = replace(thumbnail_url, '93.174.95.110', 'kissmanga.com')
WHERE source = 4;