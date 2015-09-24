package eu.kanade.mangafeed.data.tables;

import android.support.annotation.NonNull;

/**
 * Created by len on 23/09/2015.
 */
public class ChaptersTable {

    @NonNull
    public static final String TABLE = "chapters";

	@NonNull
	public static final String COLUMN_ID = "_id";

	@NonNull
	public static final String COLUMN_MANGA_ID = "manga_id";

	@NonNull
	public static final String COLUMN_URL = "url";

	@NonNull
	public static final String COLUMN_NAME = "name";

	@NonNull
	public static final String COLUMN_READ = "read";

	@NonNull
	public static final String COLUMN_DATE_FETCH = "date_fetch";
}
