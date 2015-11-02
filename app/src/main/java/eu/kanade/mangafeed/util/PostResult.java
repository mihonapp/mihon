package eu.kanade.mangafeed.util;

import android.support.annotation.Nullable;

public class PostResult {

    @Nullable
    private final Integer numberOfRowsUpdated;

    @Nullable
    private final Integer numberOfRowsInserted;

    @Nullable
    private final Integer numberOfRowsDeleted;

    public PostResult(Integer numberOfRowsUpdated, Integer numberOfRowsInserted, Integer numberOfRowsDeleted) {
        this.numberOfRowsUpdated = numberOfRowsUpdated;
        this.numberOfRowsInserted = numberOfRowsInserted;
        this.numberOfRowsDeleted = numberOfRowsDeleted;
    }

    public Integer getNumberOfRowsUpdated() {
        return numberOfRowsUpdated;
    }

    public Integer getNumberOfRowsInserted() {
        return numberOfRowsInserted;
    }

    public Integer getNumberOfRowsDeleted() {
        return numberOfRowsDeleted;
    }
}
