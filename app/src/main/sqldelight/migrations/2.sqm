CREATE TABLE history(
    history_id INTEGER NOT NULL PRIMARY KEY,
    history_chapter_id INTEGER NOT NULL UNIQUE,
    history_last_read INTEGER,
    history_time_read INTEGER,
    FOREIGN KEY(history_chapter_id) REFERENCES chapters (_id)
    ON DELETE CASCADE
);

CREATE INDEX history_history_chapter_id_index ON history(history_chapter_id);