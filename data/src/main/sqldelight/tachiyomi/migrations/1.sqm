-- MangaUpdates score fixing --
UPDATE manga_sync
SET score = max(score, 0)
WHERE sync_id = 7;
